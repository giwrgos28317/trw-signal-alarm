package com.trw.signalalarm

object Constants {
    const val TARGET_URL = "https://app.jointherealworld.com/chat/01GGDHGV32QWPG7FJ3N39K4FME/01H83QAX979K9R7QTMH74ATR8C"

    // SharedPreferences Keys
    const val PREFS_NAME = "trw_alarm_prefs"
    const val PREF_SAVED_SIGNAL = "saved_signal"
    const val PREF_SAVED_REACTION = "saved_reaction"
    const val PREF_LAST_CHECK_TIME = "last_check_time"

    // Dynamic Check 1 Settings (Default: 02:13 AM)
    const val PREF_CHECK_1_ENABLED = "check_1_enabled"
    const val PREF_CHECK_1_HOUR = "check_1_hour"
    const val PREF_CHECK_1_MINUTE = "check_1_minute"
    const val DEFAULT_CHECK_1_HOUR = 2
    const val DEFAULT_CHECK_1_MINUTE = 13
    const val REQUEST_CODE_ALARM_1 = 2013

    // Dynamic Check 2 Settings (Default: 03:30 AM backup)
    const val PREF_CHECK_2_ENABLED = "check_2_enabled"
    const val PREF_CHECK_2_HOUR = "check_2_hour"
    const val PREF_CHECK_2_MINUTE = "check_2_minute"
    const val DEFAULT_CHECK_2_HOUR = 3
    const val DEFAULT_CHECK_2_MINUTE = 30
    const val REQUEST_CODE_ALARM_2 = 2030

    // Notification IDs
    const val NOTIFICATION_CHANNEL_ID = "trw_night_alarm_channel"
    const val NOTIFICATION_ID = 9001

    // Night Check Timing Constants
    const val PAGE_LOAD_WAIT_MS = 15_000L      // 15 seconds wait after load for dynamic DOM
    const val CHECK_INTERVAL_MS = 45_000L      // 45 seconds repeat interval
    const val MAX_CHECK_ATTEMPTS = 8           // Up to 8 attempts per scheduled check
}
