package com.punchpal.app

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * 常驻前台服务：
 * - 监听“解锁完成 / 亮屏 / 熄屏”
 * - 每 20 秒兜底检查一次
 * - 判定是否到了某个时段、今天该时段是否已完成、今天星期几是否启用
 * - 满足条件就打开目标打卡 App（或退化为通知）
 */
class TriggerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var keyguard: KeyguardManager? = null

    /** 最近一次解锁的时间，用于保证“解锁后才跳”，而不是用户正在用手机时突然跳走。 */
    private var lastUnlockAt = 0L

    /** 触发后的冷却时间，避免亮屏/定时检查重复打开。 */
    private var suppressUntil = 0L

    private var lastNotifyAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            evaluate(false)
            handler.postDelayed(this, 20_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Notifications.ensureChannels(this)
        startForeground(
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.serviceNotification(this)
        )
        registerUnlockReceiver()
        running = true
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST -> runTest()
            ACTION_REFRESH -> handler.post { evaluate(false) }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        receiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // 已经注销过，忽略
            }
        }
        receiver = null
        super.onDestroy()
    }

    private fun registerUnlockReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> {
                        lastUnlockAt = System.currentTimeMillis()
                        handler.postDelayed({ evaluate(true) }, 350L)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        handler.postDelayed({
                            // 没有锁屏密码 / 锁屏的设备：亮屏即视为解锁。
                            if (keyguard?.isKeyguardLocked == false) {
                                lastUnlockAt = System.currentTimeMillis()
                            }
                            evaluate(false)
                        }, 900L)
                    }
                    else -> Unit
                }
            }
        }

        ContextCompat.registerReceiver(
            this,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun evaluate(fromUnlock: Boolean) {
        if (!Prefs.isEnabled(this)) return

        val now = System.currentTimeMillis()
        if (now < suppressUntil) return

        if (keyguard?.isKeyguardLocked == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) return

        // 定时兜底只认“刚刚解锁过”，避免用户正在使用时被突兀地切走。
        if (!fromUnlock && now - lastUnlockAt > 90_000L) return

        val calendar = Calendar.getInstance()
        if (!Prefs.isWeekdayEnabled(this, calendar.get(Calendar.DAY_OF_WEEK))) return

        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val slot = Slot.activeSlot(this, nowMinutes) ?: return
        if (Prefs.isDoneToday(this, slot)) return

        // 必须是“这个时段开始之后”才发生的解锁，避免 7:59 解锁、8:00 突然被切走。
        val slotMinutes = Prefs.slotMinutes(this, slot)
        val slotStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, slotMinutes / 60)
            set(Calendar.MINUTE, slotMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (lastUnlockAt < slotStart) return

        val packageName = Prefs.targetPackage(this, slot) ?: return
        val label = Prefs.targetLabel(this, slot) ?: packageName

        openTarget(slot, packageName, label, markDone = true, fromTest = false)
    }

    private fun openTarget(
        slot: Slot,
        packageName: String,
        label: String,
        markDone: Boolean,
        fromTest: Boolean
    ) {
        suppressUntil = System.currentTimeMillis() + 15_000L

        val launchIntent = AppLauncher.resolveLaunchIntent(this, packageName)
        if (launchIntent == null) {
            notifyUser(slot, label, packageName)
            return
        }

        val autoMode = !Prefs.isNotifyOnly(this) && Settings.canDrawOverlays(this)
        if (autoMode) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            try {
                startActivity(launchIntent)
                if (markDone) {
                    Prefs.markDoneToday(this, slot)
                    Notifications.cancelAction(this)
                    Notifications.updateServiceNotification(
                        this,
                        Slot.formatTime(Prefs.slotMinutes(this, slot)) + " 已打开 " + label
                    )
                } else {
                    toast("已尝试打开 " + label)
                }
                return
            } catch (e: Exception) {
                // 安卓 10+ 后台启动限制，或被 ROM 拦截：退化成通知提醒。
            }
        }

        notifyUser(slot, label, packageName)
        if (fromTest) toast("自动跳转不可用，已改为通知提醒")
    }

    private fun notifyUser(slot: Slot, label: String, packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 5 * 60_000L) return
        lastNotifyAt = now
        Notifications.showAction(this, slot, label, packageName)
    }

    private fun runTest() {
        val slot = Slot.values().firstOrNull {
            Prefs.isSlotEnabled(this, it) && !Prefs.targetPackage(this, it).isNullOrEmpty()
        } ?: Slot.values().firstOrNull { !Prefs.targetPackage(this, it).isNullOrEmpty() }

        if (slot == null) {
            toast("请先选择要跳转的打卡 App")
            return
        }

        val packageName = Prefs.targetPackage(this, slot) ?: return
        val label = Prefs.targetLabel(this, slot) ?: packageName
        openTarget(slot, packageName, label, markDone = false, fromTest = true)
    }

    private fun toast(message: String) {
        handler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_TEST = "com.punchpal.app.action.TEST"
        const val ACTION_REFRESH = "com.punchpal.app.action.REFRESH"

        @Volatile
        var running: Boolean = false
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TriggerService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TriggerService::class.java))
        }

        /** 设置变更后让服务立刻重新判定一次（只在已启用时）。 */
        fun refresh(ctx: Context) {
            if (!Prefs.isEnabled(ctx)) return
            try {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, TriggerService::class.java).setAction(ACTION_REFRESH)
                )
            } catch (_: Exception) {
                // 后台启动前台服务受限时忽略，服务本身仍在运行。
            }
        }

        /** 调试用：无视时间与完成状态，立即尝试打开一次。 */
        fun test(ctx: Context) {
            try {
                ContextCompat.startForegroundService(
                    ctx,
                    Intent(ctx, TriggerService::class.java).setAction(ACTION_TEST)
                )
            } catch (_: Exception) {
                Toast.makeText(ctx, "无法启动服务，请先打开上面的开关", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
