package com.punchpal.app

import android.content.Context
import java.util.Locale

/**
 * 一天里的两个提醒时段。每个时段从设定的时间开始生效，
 * 到“下一个已启用时段的开始时间”或“当天结束”为止。
 */
enum class Slot(val key: String, val title: String, val defaultMinutes: Int) {
    MORNING("morning", "早间", 8 * 60),
    EVENING("evening", "晚间", 18 * 60);

    companion object {

        fun fromKey(key: String?): Slot? = values().firstOrNull { it.key == key }

        fun formatTime(minutes: Int): String =
            String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

        /** 返回当前已经到点、并且仍在生效窗口内的时段；没有则返回 null。 */
        fun activeSlot(ctx: Context, nowMinutes: Int): Slot? {
            val windows = values()
                .filter { Prefs.isSlotEnabled(ctx, it) }
                .map { it to Prefs.slotMinutes(ctx, it) }
                .sortedBy { it.second }

            var active: Slot? = null
            for ((slot, start) in windows) {
                if (nowMinutes >= start) active = slot
            }
            return active
        }

        /** 今天还没到点的下一个时段，用于界面提示。 */
        fun nextSlot(ctx: Context, nowMinutes: Int): Slot? {
            val upcoming = values()
                .filter { Prefs.isSlotEnabled(ctx, it) && Prefs.slotMinutes(ctx, it) > nowMinutes }
                .minByOrNull { Prefs.slotMinutes(ctx, it) }
            return upcoming
        }
    }
}
