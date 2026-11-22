package com.example.shieldxmobile

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SmsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_UPDATED = "com.example.shieldxmobile.SMS_UPDATED"
        private const val PREFS_NAME = "shieldx_prefs"
        private const val HISTORY_KEY = "history"
        private const val MAX_HISTORY_ITEMS = 100
        private const val NOTIFICATION_CHANNEL_ID = "shieldx_alert_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Auto Protection is opt-in. Manual message scanning remains available in MainActivity.
        if (!prefs.getBoolean("auto_protection", false)) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // One SMS may arrive in several PDU segments. Analyze it as one message.
            val sender = messages.firstNotNullOfOrNull { it.originatingAddress } ?: "Unknown Sender"
            val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
            if (body.isBlank()) return

            val heuristic = ScamMessageHeuristic().analyze(body)
            val mlPrediction = ScamMessageMlClassifier.analyze(context, body)
            val riskScore = mlPrediction?.riskScore ?: heuristic.riskScore
            val label = mlPrediction?.label ?: when {
                heuristic.riskScore >= 60 -> "High Risk Scam Pattern"
                heuristic.riskScore >= 30 -> "Suspicious Message Pattern"
                else -> "No Strong Scam Pattern Detected"
            }
            val sourceCategory = if (mlPrediction != null) {
                "On-device ML + heuristic screening"
            } else {
                "Rule-based SMS screening (ML model unavailable)"
            }
            val ruleSignals = heuristic.matchedSignals.joinToString(" · ")
                .ifBlank { "No high-risk keyword patterns matched" }
            val category = listOfNotNull(mlPrediction?.summary, "Rule signals: $ruleSignals")
                .joinToString(" | ")

            saveHistory(context, HistoryItem(sender, body, label, sourceCategory, riskScore))

            // Package-scoped broadcast plus a NOT_EXPORTED dynamic receiver in MainActivity.
            val updateIntent = Intent(ACTION_SMS_UPDATED).apply {
                setPackage(context.packageName)
                putExtra("sender", sender)
                putExtra("message", body)
                putExtra("riskScore", riskScore)
                putExtra("category", category)
                putExtra("label", label)
            }
            context.sendBroadcast(updateIntent)

            if (riskScore >= 50) {
                showNotification(context, label, sender, riskScore)
            }
        } catch (exception: Exception) {
            Log.e("ShieldXMobile", "Could not process incoming SMS", exception)
        }
    }

    private fun saveHistory(context: Context, item: HistoryItem) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type
        val existingJson = prefs.getString(HISTORY_KEY, null)
            ?: prefs.getString("scan_history", null)

        val history = try {
            if (existingJson.isNullOrBlank()) mutableListOf()
            else gson.fromJson<MutableList<HistoryItem>>(existingJson, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }

        history.add(0, item)
        val boundedHistory = history.take(MAX_HISTORY_ITEMS)
        prefs.edit()
            .putString(HISTORY_KEY, gson.toJson(boundedHistory))
            .remove("scan_history")
            .apply()
    }

    private fun showNotification(
        context: Context,
        result: String,
        sender: String,
        riskScore: Int
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "ShieldX Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for SMS messages matching suspicious patterns"
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, PinActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (result.contains("High Risk")) {
            "ShieldX: high-risk SMS pattern"
        } else {
            "ShieldX: suspicious SMS pattern"
        }
        // Do not expose the SMS body or OTP content on the lock screen.
        val text = "A message from $sender matched screening rules (score $riskScore/100)."

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
