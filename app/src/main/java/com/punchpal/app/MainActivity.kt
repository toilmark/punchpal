package com.punchpal.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var textServiceStatus: TextView
    private lateinit var textTodayStatus: TextView

    private lateinit var switchMorning: MaterialSwitch
    private lateinit var textMorningTime: TextView
    private lateinit var textMorningApp: TextView
    private lateinit var btnMorningTime: Button
    private lateinit var btnMorningApp: Button

    private lateinit var switchEvening: MaterialSwitch
    private lateinit var textEveningTime: TextView
    private lateinit var textEveningApp: TextView
    private lateinit var btnEveningTime: Button
    private lateinit var btnEveningApp: Button

    private lateinit var chipMon: Chip
    private lateinit var chipTue: Chip
    private lateinit var chipWed: Chip
    private lateinit var chipThu: Chip
    private lateinit var chipFri: Chip
    private lateinit var chipSat: Chip
    private lateinit var chipSun: Chip

    private lateinit var textNotifyStatus: TextView
    private lateinit var textOverlayStatus: TextView
    private lateinit var textBatteryStatus: TextView

    private lateinit var btnNotify: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnBattery: Button
    private lateinit var btnAutostart: Button
    private lateinit var btnTest: Button
    private lateinit var btnReset: Button

    private lateinit var switchNotifyOnly: MaterialSwitch

    private var bindingUi = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updateUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupListeners()
        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun bindViews() {
        switchEnabled = findViewById(R.id.switchEnabled)
        textServiceStatus = findViewById(R.id.textServiceStatus)
        textTodayStatus = findViewById(R.id.textTodayStatus)

        switchMorning = findViewById(R.id.switchMorning)
        textMorningTime = findViewById(R.id.textMorningTime)
        textMorningApp = findViewById(R.id.textMorningApp)
        btnMorningTime = findViewById(R.id.btnMorningTime)
        btnMorningApp = findViewById(R.id.btnMorningApp)

        switchEvening = findViewById(R.id.switchEvening)
        textEveningTime = findViewById(R.id.textEveningTime)
        textEveningApp = findViewById(R.id.textEveningApp)
        btnEveningTime = findViewById(R.id.btnEveningTime)
        btnEveningApp = findViewById(R.id.btnEveningApp)

        chipMon = findViewById(R.id.chipMon)
        chipTue = findViewById(R.id.chipTue)
        chipWed = findViewById(R.id.chipWed)
        chipThu = findViewById(R.id.chipThu)
        chipFri = findViewById(R.id.chipFri)
        chipSat = findViewById(R.id.chipSat)
        chipSun = findViewById(R.id.chipSun)

        textNotifyStatus = findViewById(R.id.textNotifyStatus)
        textOverlayStatus = findViewById(R.id.textOverlayStatus)
        textBatteryStatus = findViewById(R.id.textBatteryStatus)

        btnNotify = findViewById(R.id.btnNotify)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnBattery = findViewById(R.id.btnBattery)
        btnAutostart = findViewById(R.id.btnAutostart)
        btnTest = findViewById(R.id.btnTest)
        btnReset = findViewById(R.id.btnReset)

        switchNotifyOnly = findViewById(R.id.switchNotifyOnly)
    }

    private fun setupListeners() {
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            if (bindingUi) return@setOnCheckedChangeListener
            Prefs.setEnabled(this, checked)
            if (checked) {
                requestNotificationIfNeeded()
                try {
                    TriggerService.start(this)
                } catch (e: Exception) {
                    toast("启动失败：" + e.message)
                }
                if (!Settings.canDrawOverlays(this)) {
                    toast("已开启：记得在下方打开“显示在其他应用上层”，自动跳转才生效")
                } else {
                    toast("已开启")
                }
            } else {
                TriggerService.stop(this)
                Notifications.cancelAction(this)
            }
            updateUi()
        }

        switchMorning.setOnCheckedChangeListener { _, checked ->
            if (bindingUi) return@setOnCheckedChangeListener
            Prefs.setSlotEnabled(this, Slot.MORNING, checked)
            afterSettingsChanged()
        }

        switchEvening.setOnCheckedChangeListener { _, checked ->
            if (bindingUi) return@setOnCheckedChangeListener
            Prefs.setSlotEnabled(this, Slot.EVENING, checked)
            afterSettingsChanged()
        }

        btnMorningTime.setOnClickListener { pickTime(Slot.MORNING) }
        btnEveningTime.setOnClickListener { pickTime(Slot.EVENING) }

        btnMorningApp.setOnClickListener { pickApp(Slot.MORNING) }
        btnEveningApp.setOnClickListener { pickApp(Slot.EVENING) }

        for ((chip, day) in dayChips()) {
            chip.setOnCheckedChangeListener { _, checked ->
                if (bindingUi) return@setOnCheckedChangeListener
                Prefs.setWeekdayEnabled(this, day, checked)
                afterSettingsChanged()
            }
        }

        switchNotifyOnly.setOnCheckedChangeListener { _, checked ->
            if (bindingUi) return@setOnCheckedChangeListener
            Prefs.setNotifyOnly(this, checked)
            afterSettingsChanged()
        }

        btnNotify.setOnClickListener { requestNotificationIfNeeded() }
        btnOverlay.setOnClickListener { openOverlaySettings() }
        btnBattery.setOnClickListener { openBatterySettings() }
        btnAutostart.setOnClickListener { showAutostartHelp() }

        btnTest.setOnClickListener {
            if (!Prefs.isEnabled(this)) {
                toast("请先打开最上面的“启用打卡助手”")
                return@setOnClickListener
            }
            if (!Prefs.hasAnyTarget(this)) {
                toast("请先选择一个要跳转的打卡 App")
                return@setOnClickListener
            }
            TriggerService.test(this)
        }

        btnReset.setOnClickListener {
            Prefs.resetToday(this)
            Notifications.cancelAction(this)
            updateUi()
            toast("已重置今天两个时段的完成状态")
        }
    }

    private fun dayChips(): List<Pair<Chip, Int>> = listOf(
        chipMon to Calendar.MONDAY,
        chipTue to Calendar.TUESDAY,
        chipWed to Calendar.WEDNESDAY,
        chipThu to Calendar.THURSDAY,
        chipFri to Calendar.FRIDAY,
        chipSat to Calendar.SATURDAY,
        chipSun to Calendar.SUNDAY
    )

    private fun afterSettingsChanged() {
        updateUi()
        TriggerService.refresh(this)
    }

    private fun updateUi() {
        bindingUi = true
        try {
            val enabled = Prefs.isEnabled(this)
            switchEnabled.isChecked = enabled
            switchMorning.isChecked = Prefs.isSlotEnabled(this, Slot.MORNING)
            switchEvening.isChecked = Prefs.isSlotEnabled(this, Slot.EVENING)
            switchNotifyOnly.isChecked = Prefs.isNotifyOnly(this)

            textMorningTime.text = Slot.formatTime(Prefs.slotMinutes(this, Slot.MORNING))
            textEveningTime.text = Slot.formatTime(Prefs.slotMinutes(this, Slot.EVENING))
            textMorningApp.text = appText(Slot.MORNING)
            textEveningApp.text = appText(Slot.EVENING)

            for ((chip, day) in dayChips()) {
                chip.isChecked = Prefs.isWeekdayEnabled(this, day)
            }

            val serviceState = when {
                !enabled -> "未启用"
                TriggerService.running -> "守护中"
                else -> "已启用，服务未运行"
            }
            textServiceStatus.text = "状态：$serviceState"

            val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            textTodayStatus.text = if (!Prefs.isWeekdayEnabled(this, today)) {
                "今天：不在生效的星期里，不会提醒"
            } else {
                "今天：" + buildTodayStatus()
            }

            setPermissionStatus(textNotifyStatus, hasNotificationPermission())
            setPermissionStatus(textOverlayStatus, Settings.canDrawOverlays(this))
            setPermissionStatus(textBatteryStatus, isIgnoringBatteryOptimizations())
        } finally {
            bindingUi = false
        }
    }

    private fun appText(slot: Slot): String {
        val packageName = Prefs.targetPackage(this, slot)
            ?: return "未选择打卡 App（点右侧选择）"
        val label = Prefs.targetLabel(this, slot) ?: packageName
        return "打卡 App：$label"
    }

    private fun buildTodayStatus(): String {
        val parts = ArrayList<String>()
        for (slot in Slot.values()) {
            val state = when {
                !Prefs.isSlotEnabled(this, slot) -> "已关闭"
                Prefs.isDoneToday(this, slot) -> "已完成"
                Prefs.targetPackage(this, slot).isNullOrEmpty() -> "未选 App"
                else -> Slot.formatTime(Prefs.slotMinutes(this, slot)) + " 后提醒"
            }
            parts.add(slot.title + " " + state)
        }
        return parts.joinToString(" · ")
    }

    private fun setPermissionStatus(view: TextView, granted: Boolean) {
        view.text = if (granted) "已开启" else "未开启"
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (granted) R.color.status_ok else R.color.status_bad
            )
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            toast("“显示在其他应用上层”已开启")
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (ignored: Exception) {
                toast("请到系统设置里手动允许“显示在其他应用上层”")
            }
        }
    }

    private fun openBatterySettings() {
        if (isIgnoringBatteryOptimizations()) {
            toast("已忽略电池优化")
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ignored: Exception) {
                toast("请到系统设置 → 电池 → 电池优化里把打卡助手设为“不优化”")
            }
        }
    }

    private fun showAutostartHelp() {
        val brand = Build.MANUFACTURER.lowercase()
        val tip = when {
            brand.contains("xiaomi") || brand.contains("redmi") ->
                "小米 / Redmi：设置 → 应用设置 → 应用管理 → 打卡助手 → 打开“自启动”，并把“省电策略”改成“无限制”。"
            brand.contains("huawei") || brand.contains("honor") ->
                "华为 / 荣耀：设置 → 应用 → 应用启动管理 → 打卡助手，关闭“自动管理”，勾选“自启动、关联启动、后台活动”。"
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") ->
                "OPPO / 一加 / realme：设置 → 电池 → 更多设置，关闭“睡眠待机优化”；应用管理 → 打卡助手 → 允许自启动、允许后台活动。"
            brand.contains("vivo") || brand.contains("iqoo") ->
                "vivo / iQOO：i 管家 → 应用管理 → 权限管理 → 自启动，允许打卡助手；电池 → 后台高耗电，允许。"
            brand.contains("samsung") ->
                "三星：设置 → 电池 → 后台使用限制，把打卡助手加入“永不休眠的应用”。"
            else ->
                "在系统设置中允许打卡助手“自启动 / 后台活动 / 后台弹出界面”，并把电池策略设为“不限制”。"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("让它在后台活下来")
            .setMessage(
                tip + "\n\n另外：安卓 10 以上默认禁止后台启动界面，所以“自动跳转”依赖“显示在其他应用上层（悬浮窗）”权限；" +
                    "如果仍然被系统拦截，请打开“仅用通知提醒”，改成点通知跳转。"
            )
            .setPositiveButton("去应用详情页") { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    toast("请手动打开系统设置 → 应用 → 打卡助手")
                }
            }
            .setNegativeButton("知道了", null)
            .show()
    }

    private fun pickTime(slot: Slot) {
        val minutes = Prefs.slotMinutes(this, slot)
        TimePickerDialog(
            this,
            { _, hour, minute ->
                Prefs.setSlotMinutes(this, slot, hour * 60 + minute)
                afterSettingsChanged()
            },
            minutes / 60,
            minutes % 60,
            true
        ).show()
    }

    private fun pickApp(slot: Slot) {
        AppPickerDialog(this).show { entry ->
            Prefs.setTarget(this, slot, entry.packageName, entry.label)
            afterSettingsChanged()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
