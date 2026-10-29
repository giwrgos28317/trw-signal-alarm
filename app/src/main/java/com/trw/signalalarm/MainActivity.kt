package com.trw.signalalarm

import android.Manifest
import android.app.AlarmManager
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

        // Initial check for permissions if enabled
        if (AlarmScheduler.isWatchEnabled(this)) {
            checkAndRequestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusDisplay()
    }

    private fun setupUI() {
        // 1. Saved Signal Display
        updateStatusDisplay()

        // 2. Read & Save Current Signal Button
        binding.btnReadSignal.setOnClickListener {
            readAndSaveCurrentSignal()
        }

        // 3. Daily 02:00 AM Watch Switch
        val isWatchEnabled = AlarmScheduler.isWatchEnabled(this)
        binding.switchDailyAlarm.isChecked = isWatchEnabled
        updateAlarmStatusText(isWatchEnabled)

        binding.switchDailyAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndRequestPermissions()
                AlarmScheduler.scheduleDailyAlarm(this)
                updateAlarmStatusText(true)
                Toast.makeText(
                    this,
                    "Ενεργοποιήθηκε! Έλεγχος στις 02:00:00 π.μ.\n${AlarmScheduler.getNextAlarmFormatted()}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                AlarmScheduler.cancelDailyAlarm(this)
                updateAlarmStatusText(false)
                Toast.makeText(this, "Ο νυχτερινός έλεγχος απενεργοποιήθηκε.", Toast.LENGTH_SHORT).show()
            }
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

        // 6. Floating Reload Button
        binding.fabReloadWeb.setOnClickListener {
            binding.webView.reload()
            Toast.makeText(this, "Ανανέωση chat...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusDisplay() {
        val saved = prefs.getString(Constants.PREF_SAVED_SIGNAL, "") ?: ""
        if (saved.isNotBlank()) {
            binding.tvSavedSignal.text = saved
        } else {
            binding.tvSavedSignal.text = getString(R.string.no_signal_saved)
        }

        val lastCheck = prefs.getString(Constants.PREF_LAST_CHECK_TIME, "") ?: ""
        if (lastCheck.isNotBlank()) {
            binding.tvLastCheckTime.text = "Τελευταία ενημέρωση: $lastCheck"
        } else {
            binding.tvLastCheckTime.text = "Τελευταία ενημέρωση: Καμία"
        }
    }

    private fun updateAlarmStatusText(isEnabled: Boolean) {
        if (isEnabled) {
            binding.tvAlarmStatus.text = "Ενεργό: ${AlarmScheduler.getNextAlarmFormatted()}"
            binding.tvAlarmStatus.setTextColor(ContextCompat.getColor(this, R.color.alarm_green))
        } else {
            binding.tvAlarmStatus.text = getString(R.string.alarm_status_off)
            binding.tvAlarmStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    /**
     * Reads the current rendered DOM of the WebView, extracts the signal,
     * saves it to SharedPreferences, and updates the UI.
     */
    private fun readAndSaveCurrentSignal() {
        Toast.makeText(this, "Ανάγνωση σήματος από τη σελίδα...", Toast.LENGTH_SHORT).show()

        SignalExtractor.extractLatestSignal(binding.webView) { latestSignal ->
            if (latestSignal.isNotBlank()) {
                val now = System.currentTimeMillis()
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val formattedDate = sdf.format(Date(now))

                prefs.edit()
                    .putString(Constants.PREF_SAVED_SIGNAL, latestSignal)
                    .putString(Constants.PREF_LAST_CHECK_TIME, formattedDate)
                    .apply()

                updateStatusDisplay()

                AlertDialog.Builder(this)
                    .setTitle("✅ Signal Αποθηκεύτηκε!")
                    .setMessage("Το τελευταίο signal αποθηκεύτηκε επιτυχώς:\n\n$latestSignal")
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

    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = USER_AGENT
        settings.cacheMode = WebSettings.LOAD_DEFAULT

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
                    .setMessage("Για να μπορεί η εφαρμογή να εκτελεί τον ακριβή έλεγχο στις 02:00:00 π.μ., παρακαλούμε ενεργοποιήστε την άδεια ακριβούς ξυπνητηριού.")
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
