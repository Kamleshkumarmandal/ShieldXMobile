package com.example.shieldxmobile

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class PinActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "shieldx_prefs"
        private const val LEGACY_PIN_KEY = "admin_pin"
        private const val PIN_HASH_KEY = "admin_pin_hash"
        private const val PIN_SALT_KEY = "admin_pin_salt"
        private const val FAILED_ATTEMPTS_KEY = "pin_failed_attempts"
        private const val MAX_FAILED_ATTEMPTS = 3
        const val EXTRA_ALLOW_PIN_RESET = "com.example.shieldxmobile.ALLOW_PIN_RESET"
    }

    private lateinit var etPin: EditText
    private lateinit var btnPinAction: Button
    private lateinit var btnChangePinFingerprint: Button
    private lateinit var tvPinMessage: TextView
    private lateinit var tvPinSubtitle: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var executor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContentView(R.layout.activity_pin)

        etPin = findViewById(R.id.etPin)
        btnPinAction = findViewById(R.id.btnPinAction)
        btnChangePinFingerprint = findViewById(R.id.btnChangePinFingerprint)
        tvPinMessage = findViewById(R.id.tvPinMessage)
        tvPinSubtitle = findViewById(R.id.tvPinSubtitle)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        executor = ContextCompat.getMainExecutor(this)

        // MainActivity sets this only after successful device/biometric authentication.
        if (intent.getBooleanExtra(EXTRA_ALLOW_PIN_RESET, false)) {
            prefs.edit()
                .remove(PIN_HASH_KEY)
                .remove(PIN_SALT_KEY)
                .remove(LEGACY_PIN_KEY)
                .remove(FAILED_ATTEMPTS_KEY)
                .apply()
        }

        updatePinScreen()

        btnPinAction.setOnClickListener {
            val enteredPin = etPin.text.toString().trim()
            if (!enteredPin.matches(Regex("^[0-9]{4}$"))) {
                showMessage("PIN must contain exactly 4 digits", error = true)
                return@setOnClickListener
            }

            if (failedAttempts() >= MAX_FAILED_ATTEMPTS) {
                showMessage("PIN locked after three attempts. Use device authentication to reset it.", error = true)
                updatePinScreen()
                return@setOnClickListener
            }

            if (!isPinConfigured()) {
                savePin(enteredPin)
                clearFailedAttempts()
                showMessage("PIN set successfully", error = false)
                openMainApp()
                return@setOnClickListener
            }

            if (verifyStoredPin(enteredPin)) {
                clearFailedAttempts()
                showMessage("PIN verified", error = false)
                openMainApp()
            } else {
                val attempts = failedAttempts() + 1
                prefs.edit().putInt(FAILED_ATTEMPTS_KEY, attempts).apply()
                etPin.text.clear()
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    showMessage(
                        "Too many wrong attempts. Use fingerprint or device screen lock to reset your PIN.",
                        error = true
                    )
                } else {
                    showMessage("Wrong PIN ($attempts/$MAX_FAILED_ATTEMPTS)", error = true)
                }
                updatePinScreen()
            }
        }

        btnChangePinFingerprint.setOnClickListener {
            authenticateForPinReset()
        }
    }

    private fun isPinConfigured(): Boolean =
        prefs.contains(PIN_HASH_KEY) || prefs.contains(LEGACY_PIN_KEY)

    private fun verifyStoredPin(enteredPin: String): Boolean {
        val savedHash = prefs.getString(PIN_HASH_KEY, null)
        val savedSalt = prefs.getString(PIN_SALT_KEY, null)

        if (savedHash != null && savedSalt != null) {
            return PinSecurity.verifyPin(enteredPin, savedSalt, savedHash)
        }

        // One-time migration for users upgrading from the original plaintext PIN.
        val legacyPin = prefs.getString(LEGACY_PIN_KEY, null) ?: return false
        if (enteredPin != legacyPin) return false

        savePin(enteredPin)
        return true
    }

    private fun savePin(pin: String) {
        val salt = PinSecurity.createSalt()
        val hash = PinSecurity.hashPin(pin, salt)
        prefs.edit()
            .putString(PIN_SALT_KEY, salt)
            .putString(PIN_HASH_KEY, hash)
            .remove(LEGACY_PIN_KEY)
            .apply()
    }

    private fun failedAttempts(): Int = prefs.getInt(FAILED_ATTEMPTS_KEY, 0)

    private fun clearFailedAttempts() {
        prefs.edit().remove(FAILED_ATTEMPTS_KEY).apply()
    }

    private fun updatePinScreen() {
        val configured = isPinConfigured()
        val locked = failedAttempts() >= MAX_FAILED_ATTEMPTS

        tvPinSubtitle.text = when {
            locked -> "PIN is locked. Authenticate to reset it."
            configured -> "Enter your 4-digit admin PIN"
            else -> "Set your 4-digit admin PIN"
        }
        btnPinAction.text = if (configured) "Unlock" else "Set PIN"
        btnPinAction.isEnabled = !locked
        etPin.isEnabled = !locked
        btnChangePinFingerprint.visibility =
            if (locked && configured) View.VISIBLE else View.GONE
    }

    private fun showMessage(message: String, error: Boolean) {
        tvPinMessage.text = message
        tvPinMessage.setTextColor(
            if (error) Color.RED else Color.parseColor("#2E7D32")
        )
    }

    private fun authenticateForPinReset() {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(this)

        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(
                this,
                "Set up a fingerprint, face unlock or device screen lock to reset the PIN.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    prefs.edit()
                        .remove(PIN_HASH_KEY)
                        .remove(PIN_SALT_KEY)
                        .remove(LEGACY_PIN_KEY)
                        .remove(FAILED_ATTEMPTS_KEY)
                        .apply()

                    etPin.text.clear()
                    showMessage("Device authentication passed. Set a new PIN.", error = false)
                    updatePinScreen()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    showMessage(errString.toString(), error = true)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Reset ShieldX PIN")
            .setSubtitle("Verify with biometrics or your device screen lock")
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
