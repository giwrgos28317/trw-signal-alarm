package com.trw.signalalarm

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.trw.signalalarm.databinding.ActivityNightCheckBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NightCheckActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NightCheckActivity"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var binding: ActivityNightCheckBinding
    private lateinit var prefs: SharedPreferences

    private val checkHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private var checkStartTime: Long = 0L
    private var isAlarmTriggered = false
    private var checkCount = 0
    private var triggerSlot: String = "02:13"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        triggerSlot = intent.getStringExtra("EXTRA_TRIGGER_TIME") ?: "02:13"

        // 1. Configure Window Flags to Turn Screen On and Show Over Lockscreen
        setupLockscreenWakeFlags()

        // 2. Set Minimal Brightness (0.01) to keep room dark during automated check
        setWindowBrightness(0.01f)

        // 3. Acquire Screen Dim WakeLock to guarantee WebView keeps running
        acquireWakeLock()

        binding = ActivityNightCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        checkStartTime = System.currentTimeMillis()

        // 4. Setup STOP ALARM button click listener
        binding.btnStopNightAlarm.setOnClickListener {
            stopAlarmAndDismiss()
        }

        // 5. Initialize WebView for TRW
        setupNightWebView()

        // 6. Start the checking cycle (10-minute window)
        startCheckCycle()
    }

    private fun setupLockscreenWakeFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setWindowBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
        window.attributes = lp
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "TRW:NightCheckActivityWakeLock"
        )?.apply {
            acquire(12 * 60 * 1000L) // 12 minutes max safeguard
        }
    }

    private fun setupNightWebView() {
        val webView = binding.nightWebView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = USER_AGENT
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Night WebView page finished: $url")
            }
        }
    }

    /**
     * Repeats check cycle every 45 seconds for a 10-minute window.
     */
    private fun startCheckCycle() {
        if (isAlarmTriggered || isFinishing || isDestroyed) return

        val now = System.currentTimeMillis()
        val elapsed = now - checkStartTime

        // Check if 10-minute window has expired
        if (elapsed >= Constants.MAX_CHECK_DURATION_MS) {
            Log.d(TAG, "Check duration exceeded 10 minutes for $triggerSlot slot. No new signal. Finishing silently.")
            finishSilentlyAndReschedule()
            return
        }

        checkCount++
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedTime = sdf.format(Date(now))
        binding.tvNightTimeElapsed.text = "Έλεγχος slot $triggerSlot (#$checkCount στις $formattedTime)"

        Log.d(TAG, "Starting Check #$checkCount for slot $triggerSlot at $formattedTime (Elapsed: ${elapsed / 1000}s)")

        // 1. Reload the target chat page
        binding.nightWebView.loadUrl(Constants.TARGET_URL)

        // 2. Wait 15 seconds for dynamic DOM and chat messages to fully load
        checkHandler.postDelayed({
            if (!isAlarmTriggered && !isFinishing && !isDestroyed) {
                executeSignalCheck()
            }
        }, Constants.PAGE_LOAD_WAIT_MS)
    }

    /**
     * Extracts signal using JavaScript and evaluates if alarm should be triggered.
     */
    private fun executeSignalCheck() {
        SignalExtractor.extractLatestSignal(binding.nightWebView) { latestSignal ->
            if (isAlarmTriggered || isFinishing || isDestroyed) return@extractLatestSignal

            val savedSignal = prefs.getString(Constants.PREF_SAVED_SIGNAL, "") ?: ""
            Log.d(TAG, "Extracted signal length=${latestSignal.length}")
            Log.d(TAG, "Saved signal length=${savedSignal.length}")

            if (latestSignal.isNotBlank()) {
                // If savedSignal is empty, or latestSignal differs from savedSignal
                if (savedSignal.isEmpty() || latestSignal != savedSignal) {
                    Log.i(TAG, "🚨 NEW SIGNAL DETECTED at $triggerSlot! Triggering Alarm! 🚨")
                    onNewSignalDetected(latestSignal)
                    return@extractLatestSignal
                } else {
                    Log.d(TAG, "Signal is IDENTICAL to saved_signal. Continuing watch.")
                }
            } else {
                Log.w(TAG, "No signal extracted (empty result). Will retry in next cycle.")
            }

            // Schedule next check cycle at 45 seconds from now
            val remainingInCycle = Constants.CHECK_INTERVAL_MS - Constants.PAGE_LOAD_WAIT_MS
            checkHandler.postDelayed({
                startCheckCycle()
            }, remainingInCycle.coerceAtLeast(5000L))
        }
    }

    /**
     * Called when a valid signal DIFFERENT from saved_signal is found.
     */
    private fun onNewSignalDetected(newSignal: String) {
        isAlarmTriggered = true
        checkHandler.removeCallbacksAndMessages(null)

        // 1. Immediately save the new signal and timestamp
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        prefs.edit()
            .putString(Constants.PREF_SAVED_SIGNAL, newSignal)
            .putString(Constants.PREF_LAST_CHECK_TIME, sdf.format(Date(now)))
            .apply()

        // 2. Restore 100% full screen brightness
        setWindowBrightness(1.0f)

        // 3. Switch UI to high-visibility Alarm Screen
        binding.layoutWebContainer.visibility = View.GONE
        binding.layoutAlarmTriggered.visibility = View.VISIBLE
        binding.tvNewSignalContent.text = newSignal

        // 4. Raise STREAM_ALARM to maximum and start continuous looping alarm + vibration
        AlarmSoundManager.startAlarm(this)
    }

    /**
     * User clicked the giant red STOP ALARM button.
     */
    private fun stopAlarmAndDismiss() {
        Log.d(TAG, "User clicked STOP ALARM")
        AlarmSoundManager.stopAlarm(this)

        // Reschedule upcoming daily alarms
        AlarmScheduler.scheduleDailyAlarms(this)

        // Dismiss notification
        NotificationManagerCompat.from(this).cancel(Constants.NOTIFICATION_ID)

        // Close Activity and allow screen to turn off
        finishAndRemoveTask()
    }

    /**
     * Silently finish activity when 10 minutes pass without a new signal.
     */
    private fun finishSilentlyAndReschedule() {
        checkHandler.removeCallbacksAndMessages(null)

        // Reschedule upcoming daily alarms
        AlarmScheduler.scheduleDailyAlarms(this)

        // Dismiss notification
        NotificationManagerCompat.from(this).cancel(Constants.NOTIFICATION_ID)

        // Silently close Activity so screen turns off
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        checkHandler.removeCallbacksAndMessages(null)
        if (!isAlarmTriggered) {
            AlarmSoundManager.stopAlarm(this)
        }
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakeLock: ${e.message}")
        }
    }
}
