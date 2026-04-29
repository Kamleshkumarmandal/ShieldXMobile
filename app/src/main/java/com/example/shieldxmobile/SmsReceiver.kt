package com.example.shieldxmobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {

            Toast.makeText(context, "SMS Receiver Triggered", Toast.LENGTH_LONG).show()

            val bundle: Bundle? = intent.extras

            try {
                if (bundle != null) {
                    val pdus = bundle.get("pdus") as Array<*>
                    val format = bundle.getString("format")

                    for (pdu in pdus) {
                        val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val sender = sms.originatingAddress ?: "Unknown Sender"
                        val message = sms.messageBody ?: ""

                        val result = analyzeIncomingMessage(message)

                        saveLatestSms(
                            context = context,
                            sender = sender,
                            message = message,
                            result = result.resultText,
                            riskPercent = result.riskPercent,
                            category = result.category,
                            summary = result.summary
                        )

                        Toast.makeText(
                            context,
                            "Auto Scan Result: ${result.resultText}",
                            Toast.LENGTH_LONG
                        ).show()

                        if (result.resultText.contains("High Risk") ||
                            result.resultText.contains("Suspicious")
                        ) {
                            showNotification(context, result.resultText)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    data class IncomingScanResult(
        val resultText: String,
        val riskPercent: Int,
        val category: String,
        val summary: String
    )

    private fun analyzeIncomingMessage(message: String): IncomingScanResult {
        val text = message.lowercase()
        var score = 0
        var category = "General"

        val suspiciousWords = listOf(
            "urgent", "bank", "account", "verify", "click",
            "otp", "password", "winner", "prize", "claim",
            "suspended", "kyc", "link", "gift", "loan",
            "free", "limited time", "update", "blocked", "reward",
            "refund", "cashback", "upi", "aadhaar", "pan", "login"
        )

        for (word in suspiciousWords) {
            if (text.contains(word)) {
                score += 1
            }
        }

        if (text.contains("http://") || text.contains("https://") || text.contains("www.")) {
            score += 2
        }

        if (text.contains("bank") || text.contains("account") || text.contains("kyc") || text.contains("upi")) {
            category = "Banking Scam"
        } else if (text.contains("otp") || text.contains("password") || text.contains("verify") || text.contains("login")) {
            category = "Credential Theft"
        } else if (text.contains("prize") || text.contains("winner") || text.contains("gift") || text.contains("reward")) {
            category = "Lottery / Reward Scam"
        } else if (text.contains("loan") || text.contains("refund") || text.contains("cashback")) {
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
            score >= 5 -> IncomingScanResult(
                resultText = "⚠ High Risk Scam Message",
                riskPercent = riskPercent,
                category = category,
                summary = "This incoming SMS looks dangerous. Do not click links or share details."
            )

            score >= 3 -> IncomingScanResult(
                resultText = "⚠ Suspicious Message",
                riskPercent = riskPercent,
                category = category,
                summary = "This incoming SMS may be unsafe. Verify sender before taking action."
            )

            else -> IncomingScanResult(
                resultText = "✅ Looks Safe",
                riskPercent = riskPercent,
                category = "Normal",
                summary = "No strong scam pattern detected in this incoming SMS."
            )
        }
    }

    private fun saveLatestSms(
        context: Context,
        sender: String,
        message: String,
        result: String,
        riskPercent: Int,
        category: String,
        summary: String
    ) {
        val prefs: SharedPreferences =
            context.getSharedPreferences("shieldx_prefs", Context.MODE_PRIVATE)

        prefs.edit()
            .putString("latest_sender", sender)
            .putString("latest_message", message)
            .putString("latest_result", result)
            .putInt("latest_risk", riskPercent)
            .putString("latest_category", category)
            .putString("latest_summary", summary)
            .putBoolean("has_new_sms", true)
            .apply()
    }

    private fun showNotification(context: Context, result: String) {
        val channelId = "shieldx_alert_channel"
        val channelName = "ShieldX Alerts"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications for scam SMS alerts"
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, PinActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (result.contains("High Risk")) {
            "🚨 High Risk SMS Detected"
        } else {
            "⚠️ Suspicious SMS Detected"
        }

        val text = "Suspicious SMS detected. Tap to open ShieldX."

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}