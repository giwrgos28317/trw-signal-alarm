package com.trw.signalalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun getCheck1Hour(context: Context): Int {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(Constants.PREF_CHECK_1_HOUR, Constants.DEFAULT_CHECK_1_HOUR)
    }

    fun getCheck1Minute(context: Context): Int {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(Constants.PREF_CHECK_1_MINUTE, Constants.DEFAULT_CHECK_1_MINUTE)
    }

    fun isCheck1Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_CHECK_1_ENABLED, true)
    }

    fun setCheck1Time(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(Constants.PREF_CHECK_1_HOUR, hour)
            .putInt(Constants.PREF_CHECK_1_MINUTE, minute)
            .apply()
    }

    fun setCheck1Enabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.PREF_CHECK_1_ENABLED, enabled).apply()
    }

    fun getCheck2Hour(context: Context): Int {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(Constants.PREF_CHECK_2_HOUR, Constants.DEFAULT_CHECK_2_HOUR)
    }

    fun getCheck2Minute(context: Context): Int {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(Constants.PREF_CHECK_2_MINUTE, Constants.DEFAULT_CHECK_2_MINUTE)
    }

    fun isCheck2Enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_CHECK_2_ENABLED, true)
    }

    fun setCheck2Time(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(Constants.PREF_CHECK_2_HOUR, hour)
            .putInt(Constants.PREF_CHECK_2_MINUTE, minute)
            .apply()
    }

    fun setCheck2Enabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.PREF_CHECK_2_ENABLED, enabled).apply()
    }

    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    /**
     * Cancels existing alarms and schedules exact alarms according to user preferences.
     * Returns a confirmation message string.
     */
    fun scheduleAlarms(context: Context): String {
        cancelAlarms(context)

        val check1On = isCheck1Enabled(context)
        val check2On = isCheck2Enabled(context)

        val h1 = getCheck1Hour(context)
        val m1 = getCheck1Minute(context)
        val h2 = getCheck2Hour(context)
        val m2 = getCheck2Minute(context)

        val t1 = formatTime(h1, m1)
        val t2 = formatTime(h2, m2)

        if (check1On) {
            scheduleAlarmAt(context, h1, m1, Constants.REQUEST_CODE_ALARM_1, "Check 1 ($t1)")
        }

        if (check2On) {
            scheduleAlarmAt(context, h2, m2, Constants.REQUEST_CODE_ALARM_2, "Check 2 ($t2)")
        }

        return when {
            check1On && check2On -> "Alarms set for $t1 & $t2"
            check1On -> "Alarm set for $t1 (Check 2 off)"
            check2On -> "Alarm set for $t2 (Check 1 off)"
            else -> "All night alarms disabled"
        }
    }

    /**
     * Backward-compatible alias for scheduleAlarms.
     */
    fun scheduleDailyAlarms(context: Context) {
        scheduleAlarms(context)
    }

    private fun scheduleAlarmAt(
        context: Context,
        hour: Int,
        minute: Int,
        requestCode: Int,
        label: String
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If this time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val triggerTime = calendar.timeInMillis
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.trw.signalalarm.ACTION_NIGHT_CHECK"
            putExtra("EXTRA_ALARM_HOUR", hour)
            putExtra("EXTRA_ALARM_MINUTE", minute)
            putExtra("EXTRA_REQUEST_CODE", requestCode)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d(TAG, "[$label] setAlarmClock set for ${calendar.time}")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d(TAG, "[$label] setExactAndAllowWhileIdle set for ${calendar.time}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "[$label] SecurityException on setAlarmClock: ${e.message}, falling back")
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (ex: Exception) {
                Log.e(TAG, "[$label] Failed to schedule exact alarm: ${ex.message}", ex)
            }
        }
    }

    /**
     * Cancels both scheduled alarms (Check 1 and Check 2).
     */
    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.trw.signalalarm.ACTION_NIGHT_CHECK"
        }

        val p1 = PendingIntent.getBroadcast(
            context,
            Constants.REQUEST_CODE_ALARM_1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(p1)

        val p2 = PendingIntent.getBroadcast(
            context,
            Constants.REQUEST_CODE_ALARM_2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(p2)

        Log.d(TAG, "Night alarms canceled.")
    }

    /**
     * Backward-compatible alias for cancelAlarms.
     */
    fun cancelDailyAlarms(context: Context) {
        cancelAlarms(context)
    }

    /**
     * Returns a human readable string describing the upcoming alarm.
     */
    fun getStatusSummary(context: Context): String {
        val check1On = isCheck1Enabled(context)
        val check2On = isCheck2Enabled(context)

        if (!check1On && !check2On) {
            return "Κατάσταση: Όλοι οι έλεγχοι είναι ανενεργοί"
        }

        val h1 = getCheck1Hour(context)
        val m1 = getCheck1Minute(context)
        val h2 = getCheck2Hour(context)
        val m2 = getCheck2Minute(context)

        val cal1 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h1)
            set(Calendar.MINUTE, m1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val cal2 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h2)
            set(Calendar.MINUTE, m2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val sdf = SimpleDateFormat("EEE, dd MMM - HH:mm", Locale.getDefault())

        return when {
            check1On && check2On -> {
                val nextCal = if (cal1.timeInMillis <= cal2.timeInMillis) cal1 else cal2
                val nextStr = sdf.format(nextCal.time)
                "Επόμενο: $nextStr (Ενεργά: ${formatTime(h1, m1)} & ${formatTime(h2, m2)})"
            }
            check1On -> {
                val nextStr = sdf.format(cal1.time)
                "Επόμενο: $nextStr (Check 1: ${formatTime(h1, m1)} | Check 2: Off)"
            }
            else -> {
                val nextStr = sdf.format(cal2.time)
                "Επόμενο: $nextStr (Check 2: ${formatTime(h2, m2)} | Check 1: Off)"
            }
        }
    }
}
