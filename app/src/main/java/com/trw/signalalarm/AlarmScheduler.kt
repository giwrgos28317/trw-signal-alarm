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

    /**
     * Schedules both daily exact alarms:
     * 1. Primary check at 02:13:00 AM
     * 2. Backup check at 03:30:00 AM
     */
    fun scheduleDailyAlarms(context: Context) {
        scheduleAlarmAt(
            context = context,
            hour = Constants.ALARM_1_HOUR,
            minute = Constants.ALARM_1_MINUTE,
            requestCode = Constants.REQUEST_CODE_ALARM_1,
            label = "Primary 02:13 AM"
        )

        scheduleAlarmAt(
            context = context,
            hour = Constants.ALARM_2_HOUR,
            minute = Constants.ALARM_2_MINUTE,
            requestCode = Constants.REQUEST_CODE_ALARM_2,
            label = "Backup 03:30 AM"
        )

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.PREF_WATCH_ENABLED, true).apply()
        Log.d(TAG, "Both alarms (02:13 AM and 03:30 AM) successfully scheduled.")
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
     * Cancels both scheduled alarms (02:13 AM and 03:30 AM).
     */
    fun cancelDailyAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.trw.signalalarm.ACTION_NIGHT_CHECK"
        }

        // Cancel 02:13 AM
        val p1 = PendingIntent.getBroadcast(
            context,
            Constants.REQUEST_CODE_ALARM_1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(p1)

        // Cancel 03:30 AM
        val p2 = PendingIntent.getBroadcast(
            context,
            Constants.REQUEST_CODE_ALARM_2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(p2)

        Log.d(TAG, "Both alarms canceled.")
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(Constants.PREF_WATCH_ENABLED, false).apply()
    }

    /**
     * Checks if the watch switch is enabled.
     */
    fun isWatchEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_WATCH_ENABLED, false)
    }

    /**
     * Calculates which alarm is coming next and formats a human readable string.
     */
    fun getNextAlarmFormatted(): String {
        val cal1 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Constants.ALARM_1_HOUR)
            set(Calendar.MINUTE, Constants.ALARM_1_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val cal2 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Constants.ALARM_2_HOUR)
            set(Calendar.MINUTE, Constants.ALARM_2_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val nextCal = if (cal1.timeInMillis <= cal2.timeInMillis) cal1 else cal2
        val sdf = SimpleDateFormat("EEE, dd MMM - HH:mm", Locale.getDefault())
        val nextTimeStr = sdf.format(nextCal.time)
        return "Επόμενο: $nextTimeStr (Πρόγραμμα: 02:13 & 03:30 π.μ.)"
    }
}
