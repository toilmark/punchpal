package com.punchpal.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 所有设置都存在 SharedPreferences 里，不引入数据库，进程被杀也不会丢。
 */
object Prefs {

    private const val FILE = "punchpal_settings"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_NOTIFY_ONLY = "notify_only"
    private const val KEY_WEEKDAY_PREFIX = "weekday_"
    private const val KEY_DONE_PREFIX = "done_date_"
    private const val KEY_SLOT_ENABLED_PREFIX = "slot_enabled_"
    private const val KEY_SLOT_MINUTES_PREFIX = "slot_minutes_"
    private const val KEY_TARGET_PKG_PREFIX = "target_pkg_"
    private const val KEY_TARGET_LABEL_PREFIX = "target_label_"

    fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- 总开关 ----------

    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_ENABLED, value).apply()

    // ---------- 提醒方式 ----------

    /** true = 只发通知，不尝试自动跳转。 */
    fun isNotifyOnly(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_NOTIFY_ONLY, false)

    fun setNotifyOnly(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_NOTIFY_ONLY, value).apply()

    // ---------- 时段 ----------

    fun isSlotEnabled(ctx: Context, slot: Slot): Boolean =
        sp(ctx).getBoolean(KEY_SLOT_ENABLED_PREFIX + slot.key, true)

    fun setSlotEnabled(ctx: Context, slot: Slot, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_SLOT_ENABLED_PREFIX + slot.key, value).apply()

    fun slotMinutes(ctx: Context, slot: Slot): Int =
        sp(ctx).getInt(KEY_SLOT_MINUTES_PREFIX + slot.key, slot.defaultMinutes)

    fun setSlotMinutes(ctx: Context, slot: Slot, minutes: Int) =
        sp(ctx).edit().putInt(KEY_SLOT_MINUTES_PREFIX + slot.key, minutes).apply()

    // ---------- 目标 App ----------

    fun targetPackage(ctx: Context, slot: Slot): String? =
        sp(ctx).getString(KEY_TARGET_PKG_PREFIX + slot.key, null)

    fun targetLabel(ctx: Context, slot: Slot): String? =
        sp(ctx).getString(KEY_TARGET_LABEL_PREFIX + slot.key, null)

    fun setTarget(ctx: Context, slot: Slot, packageName: String, label: String) {
        sp(ctx).edit()
            .putString(KEY_TARGET_PKG_PREFIX + slot.key, packageName)
            .putString(KEY_TARGET_LABEL_PREFIX + slot.key, label)
            .apply()
    }

    // ---------- 星期 ----------

    /** calendarDay 使用 Calendar.MONDAY..Calendar.SUNDAY（1..7）。 */
    fun isWeekdayEnabled(ctx: Context, calendarDay: Int): Boolean =
        sp(ctx).getBoolean(KEY_WEEKDAY_PREFIX + calendarDay, true)

    fun setWeekdayEnabled(ctx: Context, calendarDay: Int, value: Boolean) =
        sp(ctx).edit().putBoolean(KEY_WEEKDAY_PREFIX + calendarDay, value).apply()

    // ---------- 当天完成状态 ----------

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isDoneToday(ctx: Context, slot: Slot): Boolean =
        sp(ctx).getString(KEY_DONE_PREFIX + slot.key, null) == todayKey()

    fun markDoneToday(ctx: Context, slot: Slot) =
        sp(ctx).edit().putString(KEY_DONE_PREFIX + slot.key, todayKey()).apply()

    fun resetToday(ctx: Context) {
        val editor = sp(ctx).edit()
        for (slot in Slot.values()) editor.remove(KEY_DONE_PREFIX + slot.key)
        editor.apply()
    }

    // ---------- 配置完整性 ----------

    fun hasAnyTarget(ctx: Context): Boolean =
        Slot.values().any { !targetPackage(ctx, it).isNullOrEmpty() }

    fun isConfigured(ctx: Context): Boolean =
        Slot.values()
            .filter { isSlotEnabled(ctx, it) }
            .all { !targetPackage(ctx, it).isNullOrEmpty() }
}
