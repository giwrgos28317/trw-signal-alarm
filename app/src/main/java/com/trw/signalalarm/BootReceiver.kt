package com.trw.signalalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d(TAG, "Device booted with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val isEnabled = AlarmScheduler.isWatchEnabled(context)
            Log.d(TAG, "Watch is enabled in preferences: $isEnabled")

            if (isEnabled) {
                AlarmScheduler.scheduleDailyAlarms(context)
                Log.d(TAG, "Rescheduled daily 02:13 AM & 03:30 AM alarms after reboot successfully.")
            }
        }
    }
}
