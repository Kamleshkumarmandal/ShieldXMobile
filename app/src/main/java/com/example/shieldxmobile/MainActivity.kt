package com.example.shieldxmobile

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etSender: EditText
    private lateinit var etMessage: EditText
    private lateinit var btnScan: Button
    private lateinit var btnClear: Button
    private lateinit var tvResult: TextView
    private lateinit var tvRiskPercent: TextView
    private lateinit var progressRisk: ProgressBar
    private lateinit var tvHistory: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvSummary: TextView
    private lateinit var switchAutoProtection: SwitchCompat
    private lateinit var tvProtectionStatus: TextView
    private lateinit var spinnerLanguage: Spinner

    private lateinit var prefs: SharedPreferences
    private val historyList = mutableListOf<String>()
    private var isFirstSpinnerLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("shieldx_prefs", MODE_PRIVATE)
        val savedLang = prefs.getString("app_language", "en") ?: "en"
        setLocale(savedLang)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSender = findViewById(R.id.etSender)
        etMessage = findViewById(R.id.etMessage)
        btnScan = findViewById(R.id.btnScan)
        btnClear = findViewById(R.id.btnClear)
        tvResult = findViewById(R.id.tvResult)
        tvRiskPercent = findViewById(R.id.tvRiskPercent)
        progressRisk = findViewById(R.id.progressRisk)
        tvHistory = findViewById(R.id.tvHistory)
        tvCategory = findViewById(R.id.tvCategory)
        tvSummary = findViewById(R.id.tvSummary)
        switchAutoProtection = findViewById(R.id.switchAutoProtection)
        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)

        setupLanguageSpinner(savedLang)

        val savedState = prefs.getBoolean("auto_sms_enabled", false)
        switchAutoProtection.isChecked = savedState
        updateProtectionStatus(savedState)

        switchAutoProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_sms_enabled", isChecked).apply()

            if (isChecked) {
                requestSmsPermissions()
                requestNotificationPermission()
                updateProtectionStatus(true)
            } else {
                updateProtectionStatus(false)
            }
        }

        btnScan.setOnClickListener {
            val sender = etSender.text.toString().trim()
            val message = etMessage.text.toString().trim()

            if (message.isEmpty()) {
                tvResult.text = getString(R.string.enter_message_first)
                tvResult.setTextColor(Color.parseColor("#E65100"))
                tvRiskPercent.text = getString(R.string.risk_default)
                progressRisk.progress = 0
                tvCategory.text = getString(R.string.category_default)
                tvSummary.text = getString(R.string.enter_details)
            } else {
                val result = analyzeMessage(sender, message)
                showManualScanResult(sender, message, result)
            }
        }

        btnClear.setOnClickListener {
            etSender.text.clear()
            etMessage.text.clear()
            tvResult.text = getString(R.string.result_default)
            tvResult.setTextColor(Color.BLACK)
            tvRiskPercent.text = getString(R.string.risk_default)
            progressRisk.progress = 0
            tvCategory.text = getString(R.string.category_default)
            tvSummary.text = getString(R.string.summary_default)
        }
    }

    override fun onResume() {
        super.onResume()
        loadLatestIncomingSmsIfAny()
    }

    private fun setupLanguageSpinner(savedLang: String) {
        val languageOptions = arrayOf("English", "Hindi")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter

        if (savedLang == "hi") {
            spinnerLanguage.setSelection(1)
        } else {
            spinnerLanguage.setSelection(0)
        }

        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLang = if (position == 1) "hi" else "en"
                val currentLang = prefs.getString("app_language", "en") ?: "en"

                if (isFirstSpinnerLoad) {
                    isFirstSpinnerLoad = false
                    return
                }

                if (selectedLang != currentLang) {
                    prefs.edit().putString("app_language", selectedLang).apply()
                    recreate()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadLatestIncomingSmsIfAny() {
        val hasNewSms = prefs.getBoolean("has_new_sms", false)
        if (!hasNewSms) return

        val sender = prefs.getString("latest_sender", "") ?: ""
        val message = prefs.getString("latest_message", "") ?: ""
        val result = prefs.getString("latest_result", getString(R.string.result_default))
            ?: getString(R.string.result_default)
        val risk = prefs.getInt("latest_risk", 0)
        val category = prefs.getString("latest_category", "Not scanned") ?: "Not scanned"
        val summary = prefs.getString("latest_summary", getString(R.string.summary_default))
            ?: getString(R.string.summary_default)

        etSender.setText(sender)
        etMessage.setText(message)
        tvResult.text = result
        tvRiskPercent.text = "Risk: $risk%"
        progressRisk.progress = risk
        tvCategory.text = "Category: $category"
        tvSummary.text = "Summary: $summary"

        when {
            result.contains("High Risk") -> tvResult.setTextColor(Color.RED)
            result.contains("Suspicious") -> tvResult.setTextColor(Color.parseColor("#E65100"))
            else -> tvResult.setTextColor(Color.parseColor("#2E7D32"))
        }

        val shortMessage = if (message.length > 35) {
            message.take(35) + "..."
        } else {
            message
        }

        val historyItem = "• $sender\n  $shortMessage\n  $result | $category | $risk%"
        historyList.add(0, historyItem)
        tvHistory.text = historyList.joinToString("\n\n")

        prefs.edit().putBoolean("has_new_sms", false).apply()
    }

    private fun updateProtectionStatus(enabled: Boolean) {
        if (enabled) {
            tvProtectionStatus.text = getString(R.string.auto_protection_on)
            tvProtectionStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            tvProtectionStatus.text = getString(R.string.auto_protection_off)
            tvProtectionStatus.setTextColor(Color.parseColor("#C62828"))
        }
    }

    private fun requestSmsPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                1
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    2
                )
            }
        }
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)
    }

    data class ScanResult(
        val message: String,
        val color: Int,
        val riskPercent: Int,
        val label: String,
        val category: String,
        val summary: String
    )

    private fun analyzeMessage(sender: String, message: String): ScanResult {
        val text = message.lowercase()
        val senderText = sender.lowercase()

        var score = 0
        val reasons = mutableListOf<String>()
        var category = "General"

        val suspiciousWords = listOf(
            "urgent", "win", "winner", "prize", "lottery",
            "bank", "account", "verify", "click", "link",
            "password", "otp", "free", "offer", "limited time",
            "payment", "update", "suspended", "claim", "kyc",
            "blocked", "reward", "gift", "loan", "refund",
            "cashback", "upi", "aadhaar", "pan", "login",
            "expired", "security alert"
        )

        for (word in suspiciousWords) {
            if (text.contains(word)) {
                score += 1
                reasons.add(word)
            }
        }

        if (text.contains("http://") || text.contains("https://") || text.contains("www.")) {
            score += 2
            reasons.add("suspicious link")
        }

        if (senderText.matches(Regex("^[0-9+]+$")) && senderText.length >= 10) {
            score += 1
            reasons.add("unknown number sender")
        }

        if (text.contains("bank") || text.contains("account") || text.contains("kyc") || text.contains("upi")) {
            category = "Banking Scam"
        } else if (text.contains("otp") || text.contains("password") || text.contains("verify") || text.contains("login")) {
            category = "Credential Theft"
        } else if (text.contains("prize") || text.contains("winner") || text.contains("gift") || text.contains("reward")) {
            category = "Lottery / Reward Scam"
        } else if (text.contains("loan") || text.contains("payment") || text.contains("refund") || text.contains("cashback")) {
            category = "Financial Fraud"
        }

        val riskPercent = when {
            score >= 7 -> 95
            score >= 6 -> 85
            score >= 5 -> 75
            score >= 4 -> 60
            score >= 3 -> 45
            score >= 2 -> 30
            score >= 1 -> 15
            else -> 5
        }

        return when {
            score >= 5 -> ScanResult(
                message = "⚠ High Risk Scam Message\n\nDetected words/signs: ${reasons.joinToString(", ")}",
                color = Color.RED,
                riskPercent = riskPercent,
                label = "High Risk",
                category = category,
                summary = "This message contains multiple scam indicators. Do not click links or share details."
            )

            score >= 3 -> ScanResult(
                message = "⚠ Suspicious Message\n\nDetected words/signs: ${reasons.joinToString(", ")}",
                color = Color.parseColor("#E65100"),
                riskPercent = riskPercent,
                label = "Suspicious",
                category = category,
                summary = "This message may be unsafe. Verify sender details before taking any action."
            )

            else -> ScanResult(
                message = "✅ Looks Safe\n\nNo major scam signs found.",
                color = Color.parseColor("#2E7D32"),
                riskPercent = riskPercent,
                label = "Safe",
                category = "Normal",
                summary = "No strong scam pattern detected in this message."
            )
        }
    }

    private fun showManualScanResult(sender: String, message: String, result: ScanResult) {
        tvResult.text = result.message
        tvResult.setTextColor(result.color)
        tvRiskPercent.text = "Risk: ${result.riskPercent}%"
        progressRisk.progress = result.riskPercent
        tvCategory.text = "Category: ${result.category}"
        tvSummary.text = "Summary: ${result.summary}"

        val shortMessage = if (message.length > 35) {
            message.take(35) + "..."
        } else {
            message
        }

        val senderText = if (sender.isEmpty()) getString(R.string.unknown_sender) else sender

        val historyItem =
            "• $senderText\n  $shortMessage\n  ${result.label} | ${result.category} | ${result.riskPercent}%"

        historyList.add(0, historyItem)
        tvHistory.text = historyList.joinToString("\n\n")
    }
}