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

    private var isAlarmTriggered = false
    private var checkAttempt = 0
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

        // 4. Setup STOP ALARM button click listener
        binding.btnStopNightAlarm.setOnClickListener {
            stopAlarmAndDismiss()
        }

        // 5. Initialize WebView for TRW
        setupNightWebView()

        // 6. Start the checking cycle (up to MAX_CHECK_ATTEMPTS attempts)
        startCheckAttempt()
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
            acquire(15 * 60 * 1000L) // 15 minutes max safeguard
        }
    }

    private fun setupNightWebView() {
        val webView = binding.nightWebView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = USER_AGENT

        // Cache-busting mode: do not load cached stale page
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

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
     * Executes one check attempt, re-checking every 45s up to MAX_CHECK_ATTEMPTS.
     */
    private fun startCheckAttempt() {
        if (isAlarmTriggered || isFinishing || isDestroyed) return

        if (checkAttempt >= Constants.MAX_CHECK_ATTEMPTS) {
            Log.d(TAG, "Reached max attempts (${Constants.MAX_CHECK_ATTEMPTS}) for $triggerSlot slot. Finishing silently.")
            finishSilentlyAndReschedule()
            return
        }

        checkAttempt++
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val formattedTime = sdf.format(Date(now))

        binding.tvNightCheckStatus.text = "Έλεγχος slot $triggerSlot (#$checkAttempt/${Constants.MAX_CHECK_ATTEMPTS})..."
        binding.tvNightTimeElapsed.text = "Ώρα: $formattedTime"

        Log.d(TAG, "Starting Attempt #$checkAttempt of ${Constants.MAX_CHECK_ATTEMPTS} for slot $triggerSlot at $formattedTime")

        // 1. Clear cache & reload with timestamp parameter
        binding.nightWebView.clearCache(true)
        val separator = if (Constants.TARGET_URL.contains("?")) "&" else "?"
        val cacheBustUrl = "${Constants.TARGET_URL}${separator}_ts=${System.currentTimeMillis()}"
        binding.nightWebView.loadUrl(cacheBustUrl)

        // 2. Wait 15 seconds for dynamic DOM and chat messages to load
        checkHandler.postDelayed({
            if (!isAlarmTriggered && !isFinishing && !isDestroyed) {
                // Auto-scroll to bottom of chat
                SignalExtractor.scrollToBottom(binding.nightWebView) {
                    // Small delay to allow any lazy rendering after scroll
                    checkHandler.postDelayed({
                        if (!isAlarmTriggered && !isFinishing && !isDestroyed) {
                            evaluateExtractedSignal()
                        }
                    }, 1500L)
                }
            }
        }, Constants.PAGE_LOAD_WAIT_MS)
    }

    /**
     * Evaluates signals and reaction counts according to the reaction-drop mathematical criteria:
     * - isNewPost = (latestReaction < saved_reaction) || (prevReaction > 0 && latestReaction < prevReaction * 0.65 && prevSignal == saved_signal)
     * - If isNewPost == false: no alarm, silently update saved_reaction if increased, retry in 45s.
     * - If isNewPost == true: update saved_signal & saved_reaction.
     *   - If latestSignal == saved_signal: silent finish without alarm.
     *   - If latestSignal != saved_signal: TRIGGER LOUD ALARM!
     */
    private fun evaluateExtractedSignal() {
        SignalExtractor.extractLatestSignal(binding.nightWebView) { result ->
            if (isAlarmTriggered || isFinishing || isDestroyed) return@extractLatestSignal

            val savedSignal = prefs.getString(Constants.PREF_SAVED_SIGNAL, "") ?: ""
            val savedReaction = prefs.getInt(Constants.PREF_SAVED_REACTION, -1)

            val latestSignal = result.latestSignal
            val latestReaction = result.latestReaction
            val prevSignal = result.prevSignal
            val prevReaction = result.prevReaction

            Log.d(TAG, "Attempt #$checkAttempt Evaluation:")
            Log.d(TAG, "latestReaction=$latestReaction, savedReaction=$savedReaction, prevReaction=$prevReaction")
            Log.d(TAG, "latestSignalLength=${latestSignal.length}, savedSignalLength=${savedSignal.length}, totalFound=${result.totalMessagesFound}")

            if (latestSignal.isBlank()) {
                Log.w(TAG, "latestSignal is blank (chat page not yet loaded). Retrying in 45s...")
                retryNextAttemptOrFinish()
                return@extractLatestSignal
            }

            // Criterion for a Fresh Post:
            // 1. latestReaction dropped below saved_reaction (e.g. 45 < 671)
            // 2. Or: latestReaction is significantly lower than prevReaction (< 65%) and prevSignal equals saved_signal
            val condition1 = (savedReaction > 0 && latestReaction < savedReaction)
            val condition2 = (prevReaction > 0 && latestReaction < (prevReaction * 0.65) && prevSignal.isNotBlank() && prevSignal == savedSignal)
            val isNewPost = condition1 || condition2

            Log.d(TAG, "isNewPost=$isNewPost (cond1=$condition1, cond2=$condition2)")

            if (!isNewPost) {
                // No new post detected yet (chat not updated or same post)
                Log.d(TAG, "No new post detected yet. Skipping alarm.")

                // If reactions for the same post increased, silently update saved_reaction
                if (latestReaction > savedReaction && latestReaction > 0) {
                    Log.d(TAG, "Silently updating saved_reaction: $savedReaction -> $latestReaction")
                    prefs.edit().putInt(Constants.PREF_SAVED_REACTION, latestReaction).apply()
                }

                binding.tvNightCheckStatus.text = "Αναμονή για νέο post (Reactions: $latestReaction vs $savedReaction)... Επανάληψη σε 45s (#$checkAttempt/${Constants.MAX_CHECK_ATTEMPTS})"
                retryNextAttemptOrFinish()
                return@extractLatestSignal
            }

            // isNewPost == true: A brand new signal post was published!
            Log.i(TAG, "🌟 NEW POST DETECTED! latestReaction=$latestReaction, savedReaction=$savedReaction, prevReaction=$prevReaction")

            // Update saved_signal, saved_reaction and timestamp
            val now = System.currentTimeMillis()
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val formattedDate = sdf.format(Date(now))
            prefs.edit()
                .putString(Constants.PREF_SAVED_SIGNAL, latestSignal)
                .putInt(Constants.PREF_SAVED_REACTION, latestReaction)
                .putString(Constants.PREF_LAST_CHECK_TIME, formattedDate)
                .apply()

            // Compare latestSignal with old savedSignal
            if (savedSignal.isNotBlank() && latestSignal == savedSignal) {
                Log.i(TAG, "New post published, but signal text is IDENTICAL to previous signal (e.g. still 100% CASH). Silent finish.")
                finishSilentlyAndReschedule()
            } else {
                Log.i(TAG, "🚨 NEW SIGNAL DIFFERS FROM PREVIOUS! TRIGGERING MAXIMUM ALARM! 🚨")
                onNewSignalDetected(latestSignal)
            }
        }
    }

    private fun retryNextAttemptOrFinish() {
        if (checkAttempt >= Constants.MAX_CHECK_ATTEMPTS) {
            Log.d(TAG, "Max attempts reached without new post. Finishing silently.")
            finishSilentlyAndReschedule()
        } else {
            val remainingWait = (Constants.CHECK_INTERVAL_MS - Constants.PAGE_LOAD_WAIT_MS - 1500L).coerceAtLeast(10_000L)
            checkHandler.postDelayed({
                startCheckAttempt()
            }, remainingWait)
        }
    }

    /**
     * Called when a valid new post with a DIFFERENT signal is found.
     */
    private fun onNewSignalDetected(newSignal: String) {
        isAlarmTriggered = true
        checkHandler.removeCallbacksAndMessages(null)

        // 1. Restore 100% full screen brightness
        setWindowBrightness(1.0f)

        // 2. Switch UI to high-visibility Alarm Screen
        binding.layoutWebContainer.visibility = View.GONE
        binding.layoutAlarmTriggered.visibility = View.VISIBLE
        binding.tvNewSignalContent.text = newSignal

        // 3. Raise STREAM_ALARM to maximum and start continuous looping alarm + vibration
        AlarmSoundManager.startAlarm(this)
    }

    /**
     * User clicked the giant red STOP ALARM button.
     */
    private fun stopAlarmAndDismiss() {
        Log.d(TAG, "User clicked STOP ALARM")
        AlarmSoundManager.stopAlarm(this)

        // Reschedule upcoming daily alarms
        AlarmScheduler.scheduleAlarms(this)

        // Dismiss notification
        NotificationManagerCompat.from(this).cancel(Constants.NOTIFICATION_ID)

        // Close Activity and allow screen to turn off
        finishAndRemoveTask()
    }

    /**
     * Silently finish activity when max attempts pass without a new post or signal was identical.
     */
    private fun finishSilentlyAndReschedule() {
        checkHandler.removeCallbacksAndMessages(null)

        // Reschedule upcoming daily alarms
        AlarmScheduler.scheduleAlarms(this)

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
