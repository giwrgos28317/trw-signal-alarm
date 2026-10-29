package com.trw.signalalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log

object AlarmSoundManager {
    private const val TAG = "AlarmSoundManager"

    private var mediaPlayer: MediaPlayer? = null
    private var isAlarmActive = false
    private val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500)

    @Synchronized
    fun startAlarm(context: Context) {
        if (isAlarmActive) {
            Log.d(TAG, "Alarm is already playing")
            return
        }
        isAlarmActive = true

        try {
            // 1. Maximize STREAM_ALARM Volume
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                val maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                it.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
                Log.d(TAG, "Set STREAM_ALARM to maximum: $maxVolume")
            }

            // 2. Resolve Alarm Audio URI
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI

            // 3. Initialize & Start MediaPlayer
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "MediaPlayer started looping alarm sound")

            // 4. Start Vibration Loop
            startVibration(context)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm: ${e.message}", e)
        }
    }

    @Synchronized
    fun stopAlarm(context: Context? = null) {
        isAlarmActive = false

        // Stop Audio
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player: ${e.message}", e)
        } finally {
            mediaPlayer = null
        }

        // Stop Vibration
        context?.let { stopVibration(it) }
        Log.d(TAG, "Alarm stopped")
    }

    fun isPlaying(): Boolean = isAlarmActive

    private fun startVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(vibrationPattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(vibrationPattern, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration: ${e.message}", e)
        }
    }

    private fun stopVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration: ${e.message}", e)
        }
    }
}
