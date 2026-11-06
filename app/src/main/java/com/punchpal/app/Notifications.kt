package com.punchpal.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {

    const val CHANNEL_SERVICE = "punchpal_service"
    const val CHANNEL_ACTION = "punchpal_action"

    const val SERVICE_NOTIFICATION_ID = 1001
    const val ACTION_NOTIFICATION_ID = 1002

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            ctx.getString(R.string.channel_service),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "保持打卡助手在后台运行"
            setShowBadge(false)
        }

        val actionChannel = NotificationChannel(
            CHANNEL_ACTION,
            ctx.getString(R.string.channel_action),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到点解锁手机后提醒你去打卡"
            enableVibration(true)
            setShowBadge(true)
        }

        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(actionChannel)
    }

    fun serviceNotification(
        ctx: Context,
        text: String = "守护中：解锁手机后按设定跳转"
    ): Notification =
        NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_punch)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(mainPendingIntent(ctx))
            .build()

    fun updateServiceNotification(ctx: Context, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SERVICE_NOTIFICATION_ID, serviceNotification(ctx, text))
    }

    fun actionNotification(ctx: Context, slot: Slot, label: String, packageName: String): Notification {
        val intent = proxyPendingIntent(ctx, slot, packageName)
        return NotificationCompat.Builder(ctx, CHANNEL_ACTION)
            .setSmallIcon(R.drawable.ic_stat_punch)
            .setContentTitle("该打卡了：" + label)
            .setContentText("点一下打开 " + label + "，完成后今天不再提醒")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .setFullScreenIntent(intent, true)
            .build()
    }

    fun showAction(ctx: Context, slot: Slot, label: String, packageName: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ACTION_NOTIFICATION_ID, actionNotification(ctx, slot, label, packageName))
    }

    fun cancelAction(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ACTION_NOTIFICATION_ID)
    }

    fun mainPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun proxyPendingIntent(ctx: Context, slot: Slot, packageName: String): PendingIntent {
        val intent = Intent(ctx, LaunchProxyActivity::class.java).apply {
            putExtra(LaunchProxyActivity.EXTRA_PACKAGE, packageName)
            putExtra(LaunchProxyActivity.EXTRA_SLOT, slot.key)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        return PendingIntent.getActivity(
            ctx,
            400 + slot.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
