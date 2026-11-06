package com.punchpal.app

import android.content.Context
import android.content.Intent

object AppLauncher {

    /**
     * 优先用系统的 launch intent，失败时退回到“桌面图标”方式解析，
     * 兼容少数没有标准 LAUNCHER intent 的应用。
     */
    fun resolveLaunchIntent(ctx: Context, packageName: String): Intent? {
        val pm = ctx.packageManager

        pm.getLaunchIntentForPackage(packageName)?.let { return it }

        val query = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val info = pm.queryIntentActivities(query, 0).firstOrNull() ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(info.activityInfo.packageName, info.activityInfo.name)
        }
    }
}
