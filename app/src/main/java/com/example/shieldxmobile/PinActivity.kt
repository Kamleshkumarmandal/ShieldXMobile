package com.example.shieldxmobile

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PinActivity : AppCompatActivity() {

    private lateinit var etPin: EditText
    private lateinit var btnPinAction: Button
    private lateinit var tvPinMessage: TextView
    private lateinit var tvPinSubtitle: TextView

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_pin)

        etPin = findViewById(R.id.etPin)
        btnPinAction = findViewById(R.id.btnPinAction)
        tvPinMessage = findViewById(R.id.tvPinMessage)
        tvPinSubtitle = findViewById(R.id.tvPinSubtitle)

        prefs = getSharedPreferences("shieldx_prefs", MODE_PRIVATE)

        val savedPin = prefs.getString("admin_pin", null)

        if (savedPin == null) {
            tvPinSubtitle.text = "Set your 4-digit admin PIN"
            btnPinAction.text = "Set PIN"
        } else {
            tvPinSubtitle.text = "Enter your 4-digit admin PIN"
            btnPinAction.text = "Unlock"
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
                    tvPinMessage.text = "PIN verified"
                    tvPinMessage.setTextColor(Color.parseColor("#2E7D32"))
                    openMainApp()
                } else {
                    tvPinMessage.text = "Wrong PIN"
                    tvPinMessage.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}