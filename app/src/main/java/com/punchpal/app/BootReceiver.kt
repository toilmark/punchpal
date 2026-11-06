package com.punchpal.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        if (!Prefs.isEnabled(context)) return
        try {
            TriggerService.start(context)
        } catch (_: Exception) {
            // 系统限制后台启动前台服务时静默失败，用户下次打开 App 会自动恢复。
        }
    }
}
