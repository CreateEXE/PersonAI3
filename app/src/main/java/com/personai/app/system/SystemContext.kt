package com.personai.app.system

import com.google.gson.annotations.SerializedName
import java.util.Calendar

data class SystemContext(
    @SerializedName("foreground_app")        val foregroundApp: String = "",
    @SerializedName("foreground_app_label")  val foregroundAppLabel: String = "",
    @SerializedName("screen_text_summary")   val screenTextSummary: String = "",
    @SerializedName("recent_apps")           val recentApps: List<String> = emptyList(),
    @SerializedName("last_app_switch_ms")    val lastAppSwitchMs: Long = 0L,
    @SerializedName("active_notifications")  val activeNotifications: List<NotificationInfo> = emptyList(),
    @SerializedName("unread_count")          val unreadCount: Int = 0,
    @SerializedName("battery_level")         val batteryLevel: Int = -1,
    @SerializedName("is_charging")           val isCharging: Boolean = false,
    @SerializedName("network_connected")     val networkConnected: Boolean = false,
    @SerializedName("network_type")          val networkType: String = "unknown",
    @SerializedName("screen_on")             val screenOn: Boolean = true,
    @SerializedName("time_of_day")           val timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,
    @SerializedName("hour_of_day")           val hourOfDay: Int = -1,
    @SerializedName("is_weekend")            val isWeekend: Boolean = false,
    @SerializedName("screen_time_today_min") val screenTimeTodayMin: Int = 0,
    @SerializedName("top_app_today")         val topAppToday: String = "",
    @SerializedName("last_updated")          val lastUpdated: Long = 0L
) {
    companion object {
        fun empty() = SystemContext()
        fun currentTimeOfDay(): TimeOfDay {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (h) {
                in 5..6   -> TimeOfDay.DAWN;   in 7..11  -> TimeOfDay.MORNING
                in 12..16 -> TimeOfDay.AFTERNOON; in 17..19 -> TimeOfDay.EVENING
                in 20..22 -> TimeOfDay.NIGHT;  23, 0, 1  -> TimeOfDay.LATE_NIGHT
                in 2..4   -> TimeOfDay.EARLY_MORNING;  else -> TimeOfDay.UNKNOWN
            }
        }
        fun isWeekendNow(): Boolean {
            val d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            return d == Calendar.SATURDAY || d == Calendar.SUNDAY
        }
    }

    fun toPromptContext(): String = buildString {
        if (timeOfDay != TimeOfDay.UNKNOWN) append("Time: ${timeOfDay.display}")
        if (isWeekend) append(" (weekend)"); append("\n")
        if (foregroundAppLabel.isNotEmpty()) append("User is in: $foregroundAppLabel\n")
        if (batteryLevel >= 0) { append("Battery: ${batteryLevel}%"); if (isCharging) append(" (charging)"); append("\n") }
        if (!networkConnected) append("Device is offline\n")
        if (activeNotifications.isNotEmpty()) append("Pending notifications: ${activeNotifications.size}\n")
        if (screenTextSummary.isNotEmpty()) append("On screen: ${screenTextSummary.take(200)}\n")
    }.trim()
}

data class NotificationInfo(
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_label")    val appLabel: String,
    @SerializedName("title")        val title: String,
    @SerializedName("text")         val text: String,
    @SerializedName("timestamp")    val timestamp: Long,
    @SerializedName("is_messaging") val isMessaging: Boolean = false
)

enum class TimeOfDay(val display: String) {
    DAWN("dawn"), MORNING("morning"), AFTERNOON("afternoon"), EVENING("evening"),
    NIGHT("night"), LATE_NIGHT("late night"), EARLY_MORNING("early morning"), UNKNOWN("unknown")
}
