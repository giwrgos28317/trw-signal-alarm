package com.trw.signalalarm

object Constants {
    const val TARGET_URL = "https://app.jointherealworld.com/chat/01GGDHGV32QWPG7FJ3N39K4FME/01H83QAX979K9R7QTMH74ATR8C"

    // SharedPreferences Keys
    const val PREFS_NAME = "trw_alarm_prefs"
    const val PREF_SAVED_SIGNAL = "saved_signal"
    const val PREF_LAST_CHECK_TIME = "last_check_time"
    const val PREF_WATCH_ENABLED = "is_watch_enabled"

    // Dual Exact Alarms Configuration
    // 1. Primary Check at 02:13 AM
    const val ALARM_1_HOUR = 2
    const val ALARM_1_MINUTE = 13
    const val REQUEST_CODE_ALARM_1 = 2013

    // 2. Backup Check at 03:30 AM
    const val ALARM_2_HOUR = 3
    const val ALARM_2_MINUTE = 30
    const val REQUEST_CODE_ALARM_2 = 2030

    // Notification IDs
    const val NOTIFICATION_CHANNEL_ID = "trw_night_alarm_channel"
    const val NOTIFICATION_ID = 9001

    // Night Check Timing Constants
    const val PAGE_LOAD_WAIT_MS = 15_000L      // 15 seconds wait after load for dynamic DOM
    const val CHECK_INTERVAL_MS = 45_000L      // 45 seconds repeat interval
    const val MAX_CHECK_DURATION_MS = 600_000L // 10 minutes max window per check
}
