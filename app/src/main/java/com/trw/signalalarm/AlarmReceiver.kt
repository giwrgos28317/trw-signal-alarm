package com.trw.signalalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val alarmHour = intent?.getIntExtra("EXTRA_ALARM_HOUR", 2) ?: 2
        val alarmMinute = intent?.getIntExtra("EXTRA_ALARM_MINUTE", 13) ?: 13
        val requestCode = intent?.getIntExtra("EXTRA_REQUEST_CODE", Constants.REQUEST_CODE_ALARM_1) ?: Constants.REQUEST_CODE_ALARM_1
        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", alarmHour, alarmMinute)

        Log.d(TAG, "AlarmReceiver triggered at $formattedTime! Action=${intent?.action}")

        // 1. Acquire temporary WakeLock to ensure CPU stays awake while launching Activity
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TRW:AlarmReceiverWakeLock"
        )
        wakeLock?.acquire(30_000L) // 30 seconds max safety timeout

        // 2. Create Notification Channel (Android O+)
        createNotificationChannel(context)

        // 3. Prepare Intent for NightCheckActivity
        val nightCheckIntent = Intent(context, NightCheckActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_TRIGGER_TIME", formattedTime)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            nightCheckIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. Build Full Screen Alarm Notification (guarantees waking screen on locked device)
        val notification = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("TRW Portfolio Watcher ($formattedTime)")
            .setContentText("Νυχτερινός έλεγχος για σημερινό Signal...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(Constants.NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing: ${e.message}")
        }

        // 5. Directly start NightCheckActivity
        try {
            context.startActivity(nightCheckIntent)
            Log.d(TAG, "Successfully started NightCheckActivity for $formattedTime check")
        } catch (e: Exception) {
            Log.e(TAG, "Could not directly start activity: ${e.message}", e)
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock release error: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "TRW Night Alarm & Check"
            val descriptionText = "Executes automated night checks and triggers portfolio signal alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
