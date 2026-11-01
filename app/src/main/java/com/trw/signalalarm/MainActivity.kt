package com.trw.signalalarm

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.trw.signalalarm.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var isPanelCollapsed = false

    // Permission launcher for Android 13+ Notifications
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
        } else {
            Toast.makeText(this, "Απαιτείται άδεια ειδοποιήσεων για το ξυπνητήρι!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        setupWebView()
        setupUI()
        setupBackNavigation()

        // Initial check for permissions if any alarm is enabled
        if (AlarmScheduler.isCheck1Enabled(this) || AlarmScheduler.isCheck2Enabled(this)) {
            checkAndRequestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusDisplay()
        updateAlarmUI()
    }

    private fun setupUI() {
        // 1. Saved Signal Display
        updateStatusDisplay()

        // 2. Read & Save Current Signal Button
        binding.btnReadSignal.setOnClickListener {
            readAndSaveCurrentSignal()
        }

        // 3. Initialize Check 1 & Check 2 UI
        updateAlarmUI()

        // Click on Check 1 Time button -> open TimePickerDialog (24-hour format)
        binding.btnCheck1Time.setOnClickListener {
            val currentH = AlarmScheduler.getCheck1Hour(this)
            val currentM = AlarmScheduler.getCheck1Minute(this)
            val dialog = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    AlarmScheduler.setCheck1Time(this, hourOfDay, minute)
                    val confirmMsg = AlarmScheduler.scheduleAlarms(this)
                    updateAlarmUI()
                    Toast.makeText(this, confirmMsg, Toast.LENGTH_LONG).show()
                },
                currentH,
                currentM,
                true // 24-hour mode
            )
            dialog.setTitle("Επιλογή Ώρας Check 1")
            dialog.show()
        }

        // Switch for Check 1
        binding.switchCheck1.setOnCheckedChangeListener { _, isChecked ->
            AlarmScheduler.setCheck1Enabled(this, isChecked)
            if (isChecked) {
                checkAndRequestPermissions()
            }
            val confirmMsg = AlarmScheduler.scheduleAlarms(this)
            updateAlarmUI()
            Toast.makeText(this, confirmMsg, Toast.LENGTH_SHORT).show()
        }

        // Click on Check 2 Time button -> open TimePickerDialog (24-hour format)
        binding.btnCheck2Time.setOnClickListener {
            val currentH = AlarmScheduler.getCheck2Hour(this)
            val currentM = AlarmScheduler.getCheck2Minute(this)
            val dialog = TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    AlarmScheduler.setCheck2Time(this, hourOfDay, minute)
                    val confirmMsg = AlarmScheduler.scheduleAlarms(this)
                    updateAlarmUI()
                    Toast.makeText(this, confirmMsg, Toast.LENGTH_LONG).show()
                },
                currentH,
                currentM,
                true // 24-hour mode
            )
            dialog.setTitle("Επιλογή Ώρας Check 2 (Backup)")
            dialog.show()
        }

        // Switch for Check 2
        binding.switchCheck2.setOnCheckedChangeListener { _, isChecked ->
            AlarmScheduler.setCheck2Enabled(this, isChecked)
            if (isChecked) {
                checkAndRequestPermissions()
            }
            val confirmMsg = AlarmScheduler.scheduleAlarms(this)
            updateAlarmUI()
            Toast.makeText(this, confirmMsg, Toast.LENGTH_SHORT).show()
        }

        // 4. Test Alarm Sound & Stop Buttons
        binding.btnTestAlarm.setOnClickListener {
            Toast.makeText(this, "Δοκιμή ήχου ξυπνητηριού & δόνησης...", Toast.LENGTH_SHORT).show()
            AlarmSoundManager.startAlarm(this)
        }

        binding.btnStopTestAlarm.setOnClickListener {
            AlarmSoundManager.stopAlarm(this)
            Toast.makeText(this, "Το ξυπνητήρι σταμάτησε.", Toast.LENGTH_SHORT).show()
        }

        // 5. Toggle Panel Collapse / Expand for better chat viewing
        binding.btnTogglePanel.setOnClickListener {
            isPanelCollapsed = !isPanelCollapsed
            if (isPanelCollapsed) {
                binding.collapsiblePanelContent.visibility = View.GONE
                binding.btnTogglePanel.text = "Show Panel"
            } else {
                binding.collapsiblePanelContent.visibility = View.VISIBLE
                binding.btnTogglePanel.text = "Hide Panel"
            }
        }

        // 6. Floating Reload Button with cache-clearing
        binding.fabReloadWeb.setOnClickListener {
            binding.webView.clearCache(true)
            binding.webView.reload()
            Toast.makeText(this, "Ανανέωση chat (χωρίς cache)...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAlarmUI() {
        val h1 = AlarmScheduler.getCheck1Hour(this)
        val m1 = AlarmScheduler.getCheck1Minute(this)
        val h2 = AlarmScheduler.getCheck2Hour(this)
        val m2 = AlarmScheduler.getCheck2Minute(this)

        binding.btnCheck1Time.text = AlarmScheduler.formatTime(h1, m1)
        binding.btnCheck2Time.text = AlarmScheduler.formatTime(h2, m2)

        binding.switchCheck1.isChecked = AlarmScheduler.isCheck1Enabled(this)
        binding.switchCheck2.isChecked = AlarmScheduler.isCheck2Enabled(this)

        val summary = AlarmScheduler.getStatusSummary(this)
        binding.tvAlarmStatus.text = summary
        val anyActive = AlarmScheduler.isCheck1Enabled(this) || AlarmScheduler.isCheck2Enabled(this)
        binding.tvAlarmStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (anyActive) R.color.alarm_green else R.color.text_secondary
            )
        )
    }

    private fun updateStatusDisplay(latestReaction: Int? = null, prevReaction: Int? = null) {
        val saved = prefs.getString(Constants.PREF_SAVED_SIGNAL, "") ?: ""
        if (saved.isNotBlank()) {
            binding.tvSavedSignal.text = saved
        } else {
            binding.tvSavedSignal.text = getString(R.string.no_signal_saved)
        }

        val savedReaction = prefs.getInt(Constants.PREF_SAVED_REACTION, -1)
        if (latestReaction != null && prevReaction != null) {
            binding.tvSavedReactions.text = "Last Reaction Count: $latestReaction (Prev msg: $prevReaction)"
        } else if (savedReaction >= 0) {
            binding.tvSavedReactions.text = "Saved First Reaction: $savedReaction"
        } else {
            binding.tvSavedReactions.text = "Last Reaction Count: - (Prev msg: -)"
        }

        val lastCheck = prefs.getString(Constants.PREF_LAST_CHECK_TIME, "") ?: ""
        if (lastCheck.isNotBlank()) {
            binding.tvLastCheckTime.text = "Τελευταία ενημέρωση: $lastCheck"
        } else {
            binding.tvLastCheckTime.text = "Τελευταία ενημέρωση: Καμία"
        }
    }

    /**
     * Reads the current rendered DOM of the WebView, extracts latest & previous signals and reactions,
     * saves them to SharedPreferences, and updates the UI.
     */
    private fun readAndSaveCurrentSignal() {
        Toast.makeText(this, "Ανάγνωση σημάτων & reactions...", Toast.LENGTH_SHORT).show()

        // 1. First trigger auto-scroll
        SignalExtractor.scrollToBottom(binding.webView) {
            // 2. Extract signals and reactions
            SignalExtractor.extractLatestSignal(binding.webView) { result ->
                if (result.latestSignal.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    val formattedDate = sdf.format(Date(now))

                    prefs.edit()
                        .putString(Constants.PREF_SAVED_SIGNAL, result.latestSignal)
                        .putInt(Constants.PREF_SAVED_REACTION, result.latestReaction)
                        .putString(Constants.PREF_LAST_CHECK_TIME, formattedDate)
                        .apply()

                    updateStatusDisplay(result.latestReaction, result.prevReaction)

                    AlertDialog.Builder(this)
                        .setTitle("✅ Signal & Reactions Αποθηκεύτηκαν!")
                        .setMessage("Τελευταίο Signal:\n${result.latestSignal}\n\nReactions:\n• Last Reaction Count: ${result.latestReaction}\n• Prev msg Reaction: ${result.prevReaction}\n(Βρέθηκαν ${result.totalMessagesFound} μηνύματα στο chat)")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Δεν βρέθηκε Signal")
                        .setMessage("Δεν εντοπίστηκε κείμενο με τίτλο 'Portfolio Signal Update' και '@I Understand The Signals'.\n\nΒεβαιωθείτε ότι είστε συνδεδεμένοι στο The Real World και ότι το κανάλι έχει φορτώσει πλήρως.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = USER_AGENT

        // Cache-busting mode: do not use stale cache
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.webViewProgressBar.visibility = View.VISIBLE
                    binding.webViewProgressBar.progress = newProgress
                } else {
                    binding.webViewProgressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false // Keep navigation inside webview
            }
        }

        // Load TRW Target Chat Channel
        webView.loadUrl(Constants.TARGET_URL)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    /**
     * Checks and requests:
     * 1. Notification Permission (Android 13+)
     * 2. Schedule Exact Alarm Permission (Android 12+)
     * 3. Battery Optimization Exemption
     */
    private fun checkAndRequestPermissions() {
        // 1. Android 13+ Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 2. Android 12+ Exact Alarm Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == false) {
                AlertDialog.Builder(this)
                    .setTitle("Απαραίτητη Άδεια Exact Alarm")
                    .setMessage("Για να εκτελούνται αυτόματα οι ακριβείς νυχτερινοί έλεγχοι, παρακαλούμε ενεργοποιήστε την άδεια ακριβούς ξυπνητηριού.")
                    .setPositiveButton("Ρυθμίσεις") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening exact alarm settings: ${e.message}")
                        }
                    }
                    .setNegativeButton("Άκυρο", null)
                    .show()
            }
        }

        // 3. Ignore Battery Optimizations (Doze mode exemption)
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Εξαίρεση Βελτιστοποίησης Μπαταρίας")
                .setMessage("Για να μην «παγώνει» το σύστημα την εφαρμογή τη νύχτα, απαιτείται εξαίρεση από τη βελτιστοποίηση μπαταρίας.")
                .setPositiveButton("Εξαίρεση") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening battery optimization settings: ${e.message}")
                    }
                }
                .setNegativeButton("Αργότερα", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundManager.stopAlarm(this)
    }
}
