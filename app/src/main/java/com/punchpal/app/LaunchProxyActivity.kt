package com.punchpal.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * 一个不可见的“跳板”页面：
 * 用户点击提醒通知 → 系统打开这里 → 标记今天该时段已完成 → 立刻打开打卡 App。
 * 用在“通知提醒模式”，保证“完成跳转后才不再提示”。
 */
class LaunchProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val slot = Slot.fromKey(intent.getStringExtra(EXTRA_SLOT))

        Notifications.cancelAction(this)

        if (packageName.isNullOrBlank()) {
            finish()
            return
        }

        val launchIntent = AppLauncher.resolveLaunchIntent(this, packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "找不到该应用，可能已被卸载或停用", Toast.LENGTH_LONG).show()
        } else {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            try {
                startActivity(launchIntent)
                if (slot != null) Prefs.markDoneToday(this, slot)
            } catch (e: Exception) {
                Toast.makeText(this, "打开失败：" + e.message, Toast.LENGTH_LONG).show()
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_SLOT = "extra_slot"
    }
}
