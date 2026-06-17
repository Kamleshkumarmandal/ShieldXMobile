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

    private lateinit var etPin: EditText
    private lateinit var btnPinAction: Button
    private lateinit var btnChangePinFingerprint: Button
    private lateinit var tvPinMessage: TextView
    private lateinit var tvPinSubtitle: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var executor: Executor

    private var wrongAttempts = 0

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

        prefs = getSharedPreferences("shieldx_prefs", MODE_PRIVATE)
        executor = ContextCompat.getMainExecutor(this)

        val savedPin = prefs.getString("admin_pin", null)

        if (savedPin == null) {
            tvPinSubtitle.text = "Set your 4-digit admin PIN"
            btnPinAction.text = "Set PIN"
            btnChangePinFingerprint.visibility = View.GONE
        } else {
            tvPinSubtitle.text = "Enter your 4-digit admin PIN"
            btnPinAction.text = "Unlock"
            btnChangePinFingerprint.visibility = View.GONE
        }

        btnPinAction.setOnClickListener {
            val enteredPin = etPin.text.toString().trim()
            val currentSavedPin = prefs.getString("admin_pin", null)

            if (enteredPin.length != 4) {
                tvPinMessage.text = "PIN must be exactly 4 digits"
                tvPinMessage.setTextColor(Color.RED)
                return@setOnClickListener
            }

            if (currentSavedPin == null) {
                prefs.edit().putString("admin_pin", enteredPin).apply()
                tvPinMessage.text = "PIN set successfully"
                tvPinMessage.setTextColor(Color.parseColor("#2E7D32"))
                openMainApp()
            } else {
                if (enteredPin == currentSavedPin) {
                    wrongAttempts = 0
                    tvPinMessage.text = "PIN verified"
                    tvPinMessage.setTextColor(Color.parseColor("#2E7D32"))
                    openMainApp()
                } else {
                    wrongAttempts++
                    tvPinMessage.text = "Wrong PIN ($wrongAttempts/3)"
                    tvPinMessage.setTextColor(Color.RED)

                    if (wrongAttempts >= 3) {
                        tvPinMessage.text =
                            "Too many wrong attempts. Use fingerprint to change PIN."
                        btnChangePinFingerprint.visibility = View.VISIBLE
                    }
                }
            }
        }

        btnChangePinFingerprint.setOnClickListener {
            authenticateFingerprintForPinChange()
        }
    }

    private fun authenticateFingerprintForPinChange() {
        val biometricManager = BiometricManager.from(this)

        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Fingerprint not available or not enrolled", Toast.LENGTH_LONG)
                .show()
            return
        }

        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)

                    prefs.edit().remove("admin_pin").apply()
                    wrongAttempts = 0

                    etPin.text.clear()
                    tvPinSubtitle.text = "Set your new 4-digit admin PIN"
                    btnPinAction.text = "Set New PIN"
                    btnChangePinFingerprint.visibility = View.GONE

                    tvPinMessage.text = "Fingerprint verified. Set a new PIN."
                    tvPinMessage.setTextColor(Color.parseColor("#2E7D32"))
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Fingerprint")
            .setSubtitle("Fingerprint required to change PIN")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun openMainApp() {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        startActivity(intent)

        finish()
    }
}