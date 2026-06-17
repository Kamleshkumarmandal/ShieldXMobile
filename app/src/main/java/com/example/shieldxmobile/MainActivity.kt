package com.example.shieldxmobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerHistory: RecyclerView
    private val historyList = mutableListOf<HistoryItem>()
    private lateinit var historyAdapter: HistoryAdapter

    private lateinit var spinnerLanguage: Spinner
    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var switchAutoProtection: SwitchCompat
    private lateinit var tvProtectionStatus: TextView
    private lateinit var etSender: EditText
    private lateinit var etMessage: EditText

    private lateinit var btnClearAllHistory: Button
    private lateinit var btnScan: Button

    private lateinit var tvRiskPercent: TextView
    private lateinit var progressRisk: ProgressBar
    private lateinit var tvResult: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvSummary: TextView

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private var smsReceiverBridge: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Toast.makeText(this, "MainActivity Started", Toast.LENGTH_LONG).show()
        setContentView(R.layout.activity_main)

        // 🎯 Prefs and Toolbar Setup
        prefs = getSharedPreferences("shieldx_prefs", MODE_PRIVATE)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 🎯 MENU ICON CLICK: Ultimate 5-Section Menu Setup
        toolbar.setNavigationOnClickListener { view ->
            val popupMenu = androidx.appcompat.widget.PopupMenu(this, view)

            // Saare Menu Items list mein add ho rahe hain
            popupMenu.menu.add(0, 1, 0, "🔑 Change PIN")
            popupMenu.menu.add(0, 2, 1, "🚨 Block Scam Number")
            popupMenu.menu.add(0, 3, 2, "📋 Scam History Log")
            popupMenu.menu.add(0, 4, 3, "📢 Report New Scam")
            popupMenu.menu.add(0, 5, 4, "✉️ Help & Feedback") // 💡 NAYA HELP SECTION
            popupMenu.menu.add(0, 6, 5, "ℹ️ About App")

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        // 🔒 1. CHANGE PIN (Fingerprint Protected)
                        val biometricManager = androidx.biometric.BiometricManager.from(this)
                        if (biometricManager.canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                            == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {

                            val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
                            val biometricPrompt = androidx.biometric.BiometricPrompt(this, executor,
                                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                        super.onAuthenticationSucceeded(result)
                                        val intent = android.content.Intent(this@MainActivity, PinActivity::class.java)
                                        startActivity(intent)
                                    }
                                    override fun onAuthenticationFailed() {
                                        super.onAuthenticationFailed()
                                        android.widget.Toast.makeText(this@MainActivity, "Authentication failed!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                })

                            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                .setTitle("Security Verification")
                                .setSubtitle("Scan your fingerprint or enter device lock to change PIN")
                                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                .build()

                            biometricPrompt.authenticate(promptInfo)
                        } else {
                            android.widget.Toast.makeText(this, "Biometric sensor not available.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    2 -> {
                        // 🚨 2. BLOCK SCAM NUMBER
                        val etSender = findViewById<android.widget.EditText>(R.id.etSender)
                        val suspiciousNumber = etSender.text.toString().trim()
                        if (suspiciousNumber.isNotEmpty()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_INSERT_OR_EDIT).apply {
                                type = android.provider.ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, suspiciousNumber)
                                putExtra("finishActivityOnSaveCompleted", true)
                            }
                            startActivity(intent)
                        } else {
                            android.widget.Toast.makeText(this, "Please enter or scan a number first!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    3 -> {
                        // 📋 3. SCAM HISTORY LOG (Scroll ke liye alert message)
                        android.widget.Toast.makeText(this, "Displaying main dashboard scan records below.", android.widget.Toast.LENGTH_SHORT).show()
                        true
                    }
                    4 -> {
                        // 📢 4. REPORT NEW SCAM
                        val etSender = findViewById<android.widget.EditText>(R.id.etSender)
                        val reportNum = etSender.text.toString().trim()
                        if (reportNum.isNotEmpty()) {
                            android.widget.Toast.makeText(this, "Number $reportNum marked as Spam. Sending to Global Database!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(this, "Enter a number in Sender box to report!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    5 -> {
                        // ✉️ 5. HELP & FEEDBACK (Direct Email Handler)
                        val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:") // Sirf email apps open karega
                            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("kkm524421@gmail.com")) // 👈 Yahaan apni correct Gmail ID daal dena bhai
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "ShieldX Mobile - User Feedback")
                            putExtra(android.content.Intent.EXTRA_TEXT, "Hello Developer,\n\nI am facing an issue / want to suggest a feature:\n")
                        }
                        try {
                            startActivity(android.content.Intent.createChooser(emailIntent, "Send Feedback Via..."))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this, "No email client found on device.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    6 -> {
                        // ℹ️ 6. ABOUT APP
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("About ShieldX Mobile")
                            .setMessage("Developer: Kamlesh Kumar Mandal\nVersion: v1.0.2\n\n© 2026 ShieldX Mobile. All Rights Reserved.\nProprietary software. Unauthorized copying or distribution of this source code is strictly prohibited.")
                            .setPositiveButton("Close", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

// 🎯 YEH LINE ADD KARO: Isse default title hat jayega aur duplicate nahi hoga!
        supportActionBar?.setDisplayShowTitleEnabled(false)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        switchDarkMode = findViewById(R.id.switchDarkMode)
        switchAutoProtection = findViewById(R.id.switchAutoProtection)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        etSender = findViewById(R.id.etSender)
        etMessage = findViewById(R.id.etMessage)

        btnScan = findViewById(R.id.btnScan)
        btnClearAllHistory = findViewById(R.id.btnClearAllHistory)

        tvRiskPercent = findViewById(R.id.tvRiskPercent)
        progressRisk = findViewById(R.id.progressRisk)
        tvResult = findViewById(R.id.tvResult)
        tvCategory = findViewById(R.id.tvCategory)
        tvSummary = findViewById(R.id.tvSummary)

        recyclerHistory = findViewById(R.id.recyclerHistory)
        recyclerHistory.layoutManager = LinearLayoutManager(this)

        historyAdapter = HistoryAdapter(historyList)
        recyclerHistory.adapter = historyAdapter

        loadHistory()
        setupLanguageSpinner()
        setupDarkMode()
        setupAutoProtection()

        // 🚀 1. SCAN BUTTON CLICK LOGIC (Moved inside onCreate)
        btnScan.setOnClickListener {
            val sender = etSender.text.toString().trim()
            val message = etMessage.text.toString().trim()

            if (sender.isEmpty() || message.isEmpty()) {
                tvResult.text = getString(R.string.enter_details)
                return@setOnClickListener
            }

            val detector = AiScamDetector()
            val prediction = detector.predict(message)

            tvRiskPercent.text = "Risk: ${prediction.aiRisk}%"
            progressRisk.progress = prediction.aiRisk
            tvResult.text = prediction.aiLabel
            tvCategory.text = "Category: SMS Scam"
            tvSummary.text = "Confidence Level: ${prediction.confidence}%"

            historyList.add(0, HistoryItem(sender, message, prediction.aiLabel, "SMS Scam", prediction.aiRisk))
            historyAdapter.notifyItemInserted(0)
            recyclerHistory.scrollToPosition(0)

            if (prediction.aiRisk >= 50) {
                showScamAlertNotification(this@MainActivity, sender, prediction.aiRisk)
            }

            saveHistory()
        }

        // 🎯 2. CLEAR ALL HISTORY BUTTON CLICK LOGIC (Moved inside onCreate)
        btnClearAllHistory.setOnClickListener {
            if (historyList.isNotEmpty()) {
                historyList.clear()
                historyAdapter.notifyDataSetChanged()
                saveHistory()
                Toast.makeText(this, "History cleared successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "History is already empty!", Toast.LENGTH_SHORT).show()
            }
        }

        // 📡 3. LIVE BACKGROUND SCANNER (Moved inside onCreate)
        smsReceiverBridge = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val sender = intent.getStringExtra("sender") ?: "Unknown"
                val message = intent.getStringExtra("message") ?: ""
                val riskScore = intent.getIntExtra("riskScore", 0)
                val category = intent.getStringExtra("category") ?: "SMS Scam"
                val label = intent.getStringExtra("label") ?: "Safe"

                try {
                    if (::tvRiskPercent.isInitialized && tvRiskPercent != null) {
                        tvRiskPercent.text = "Risk: $riskScore%"
                        progressRisk.progress = riskScore
                        tvResult.text = label
                        tvCategory.text = "Category: $category"
                        tvSummary.text = "Live Scanned Background Monitor"
                    }
                } catch (e: Exception) {}

                val backgroundPrefs = context.getSharedPreferences("shieldx_prefs", Context.MODE_PRIVATE)
                val localGson = Gson()
                val json = backgroundPrefs.getString("history", null)
                val type = object : TypeToken<MutableList<HistoryItem>>() {}.type

                val currentList = mutableListOf<HistoryItem>()
                if (json != null) {
                    val savedList: MutableList<HistoryItem> = localGson.fromJson(json, type)
                    currentList.addAll(savedList)
                }

                val newItem = HistoryItem(sender, message, label, category, riskScore)
                if (currentList.isEmpty() || currentList[0].message != message) {
                    currentList.add(0, newItem)
                }

                backgroundPrefs.edit().putString("history", localGson.toJson(currentList)).apply()

                try {
                    if (::historyAdapter.isInitialized) {
                        historyList.clear()
                        historyList.addAll(currentList)
                        historyAdapter.notifyDataSetChanged()
                        recyclerHistory.scrollToPosition(0)
                    }
                } catch (e: Exception) {}

                if (riskScore >= 50) {
                    showScamAlertNotification(context, sender, riskScore)
                }
            }
        }

        // 📡 4. LIVE BACKGROUND SCANNER REGISTRATION (Moved inside onCreate)
        @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                smsReceiverBridge,
                IntentFilter("com.example.shieldxmobile.SMS_UPDATED"),
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                smsReceiverBridge,
                IntentFilter("com.example.shieldxmobile.SMS_UPDATED")
            )
        }
    } // Perfectly closes the onCreate function

    // 🔔 SCAM ALERT NOTIFICATION FUNCTION DEFINITION
    private fun showScamAlertNotification(context: Context, sender: String, riskScore: Int) {
        val channelId = "scam_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Scam Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for suspicious SMS detection"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ HIGH RISK SCAM DETECTED!")
            .setContentText("Suspicious SMS from: $sender (Risk: $riskScore%)")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun setupLanguageSpinner() {
        val languages = arrayOf("English", "Hindi", "Tamil")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        val currentLang = prefs.getString("selected_lang", "en")
        when (currentLang) {
            "hi" -> spinnerLanguage.setSelection(1)
            "ta" -> spinnerLanguage.setSelection(2)
            else -> spinnerLanguage.setSelection(0)
        }

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val langCode = when (position) {
                    1 -> "hi"
                    2 -> "ta"
                    else -> "en"
                }

                if (langCode != prefs.getString("selected_lang", "en")) {
                    prefs.edit().putString("selected_lang", langCode).apply()
                    setAppLocale(langCode)
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setAppLocale(langCode: String) {
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }

    private fun setupDarkMode() {
        val enabled = prefs.getBoolean("dark_mode", false)
        switchDarkMode.isChecked = enabled
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            recreate()
        }
    }

    private fun loadTheme() {
        val enabled = prefs.getBoolean("dark_mode", false)
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun setupAutoProtection() {
        val enabled = prefs.getBoolean("auto_protection", false)
        switchAutoProtection.isChecked = enabled

        // 🎯 FIXED: Accessing the resource strings properly via getString()
        tvProtectionStatus.text = if (enabled) getString(R.string.auto_protection_on) else getString(R.string.auto_protection_off)

        switchAutoProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_protection", isChecked).apply()

            // 🎯 FIXED: Accessing the resource strings properly here too
            tvProtectionStatus.text = if (isChecked) getString(R.string.auto_protection_on) else getString(R.string.auto_protection_off)

            if (isChecked) {
                val permissions = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(android.Manifest.permission.RECEIVE_SMS)
                }
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(android.Manifest.permission.READ_SMS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (permissions.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
                }
            }
        }
    }

    private fun saveHistory() {
        val json = gson.toJson(historyList)
        prefs.edit().putString("history", json).apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("history", null) ?: return
        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
        val savedList: MutableList<HistoryItem> = gson.fromJson(json, type)
        historyList.clear()
        historyList.addAll(savedList)
        if (::historyAdapter.isInitialized) {
            historyAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (smsReceiverBridge != null) {
            unregisterReceiver(smsReceiverBridge)
        }
    }

    // 🎯 Isse saare menu icons aur "CHANGE PIN" screen se hat jayenge
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        return false
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }
} // 👈 Ye last bracket pure MainActivity class ko close karta hai, iske niche kuch nahi hona chahiye!