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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {

            val prefs = context.getSharedPreferences("shieldx_prefs", Context.MODE_PRIVATE)
            val autoEnabled = prefs.getBoolean("auto_protection", false)

            val bundle: Bundle? = intent.extras

            try {
                if (bundle != null) {
                    val pdus = bundle.get("pdus") as Array<*>
                    val format = bundle.getString("format")

                    for (pdu in pdus) {
                        val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val sender = sms.originatingAddress ?: "Unknown Sender"
                        val message = sms.messageBody ?: ""

                        val result = analyzeIncomingMessage(sender, message)

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
                            "ShieldX AI scanned: ${result.resultText}",
                            Toast.LENGTH_LONG
                        ).show()

                        val updateIntent = Intent("com.example.shieldxmobile.SMS_UPDATED").apply {
                            putExtra("sender", sender)
                            putExtra("message", message)
                            putExtra("riskScore", result.riskPercent)
                            putExtra("category", result.category)
                            putExtra("label", result.resultText)
                        }
                        context.sendBroadcast(updateIntent)

                        if (autoEnabled && (result.resultText.contains("High Risk") || result.resultText.contains("Suspicious"))) {
                            showNotification(context, result.resultText, sender, message)
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

    private fun analyzeIncomingMessage(sender: String, message: String): IncomingScanResult {
        val text = message.lowercase()
        val senderText = sender.lowercase()

        var score = 0

        val aiDetector = AiScamDetector()
        val aiPrediction = aiDetector.predict(message)

        val safeTransactionWords = listOf(
            "debited", "credited", "paid", "payment successful", "transaction successful",
            "txn", "upi ref", "available balance", "a/c", "account balance",
            "rs.", "inr", "spent", "received"
        )

        val englishBankingWords = listOf(
            "bank", "account", "kyc", "upi", "blocked", "suspended",
            "verify", "login", "debit", "credit", "card", "netbanking"
        )

        val hindiBankingWords = listOf(
            "bank", "khata", "account", "khata band", "band ho jayega",
            "kyc", "upi", "verify karo", "login karo", "blocked", "block"
        )

        val otpWords = listOf(
            "otp", "password", "pin", "credential", "verification code",
            "security code", "login code"
        )

        val hindiOtpWords = listOf(
            "otp bhejo", "otp share", "pin bhejo", "password bhejo",
            "code bhejo", "otp do", "pin do"
        )

        val rewardWords = listOf(
            "winner", "prize", "gift", "reward", "lottery", "cashback",
            "free", "claim", "congratulations"
        )

        val hindiRewardWords = listOf(
            "inaam", "jeet", "jeeta", "gift", "lottery", "cashback",
            "muft", "claim karo", "badhai"
        )

        val fraudActionWords = listOf(
            "click", "click here", "click now", "verify now", "update kyc",
            "complete kyc", "unblock", "claim now", "receive refund",
            "share otp", "enter otp", "share pin", "enter pin",
            "install app", "download app"
        )

        val hindiFraudActionWords = listOf(
            "click karo", "link kholo", "verify karo", "turant karo",
            "jaldi karo", "otp bhejo", "otp share karo", "pin share karo",
            "kyc update karo", "refund pane ke liye", "paisa pane ke liye",
            "app install karo"
        )

        val urgencyWords = listOf(
            "urgent", "immediately", "limited time", "now", "today",
            "expire", "expired", "last chance", "warning", "final notice"
        )

        val hindiUrgencyWords = listOf(
            "turant", "jaldi", "abhi", "aaj", "last chance",
            "antim mauka", "warning", "band ho jayega"
        )

        fun containsAny(words: List<String>): Boolean {
            return words.any { text.contains(it) }
        }

        fun addScore(words: List<String>, weight: Int) {
            for (word in words) {
                if (text.contains(word)) {
                    score += weight
                }
            }
        }

        addScore(englishBankingWords, 2)
        addScore(hindiBankingWords, 2)
        addScore(otpWords, 2)
        addScore(hindiOtpWords, 3)
        addScore(rewardWords, 1)
        addScore(hindiRewardWords, 1)
        addScore(fraudActionWords, 3)
        addScore(hindiFraudActionWords, 3)
        addScore(urgencyWords, 1)
        addScore(hindiUrgencyWords, 1)

        val hasLink =
            text.contains("http://") ||
                    text.contains("https://") ||
                    text.contains("www.") ||
                    text.contains(".com") ||
                    text.contains(".in") ||
                    text.contains(".xyz") ||
                    text.contains(".top") ||
                    text.contains(".click")

        val hasShortLink =
            text.contains("bit.ly") ||
                    text.contains("tinyurl") ||
                    text.contains("shorturl") ||
                    text.contains("t.co") ||
                    text.contains("goo.gl")

        if (hasLink) score += 3
        if (hasShortLink) score += 5

        val dangerousDomains = listOf(
            ".xyz",
            ".top",
            ".click",
            ".live",
            ".gq",
            ".tk",
            ".buzz",
            ".win",
            ".loan"
        )

        val phishingKeywords = listOf(
            "verify",
            "reward",
            "free",
            "gift",
            "claim",
            "kyc",
            "update",
            "refund",
            "bank",
            "upi"
        )

        var fakeLinkScore = 0

        for (domain in dangerousDomains) {
            if (text.contains(domain)) {
                fakeLinkScore += 5
            }
        }

        for (keyword in phishingKeywords) {
            if (text.contains(keyword) && hasLink) {
                fakeLinkScore += 3
            }
        }

        if (fakeLinkScore >= 5) {
            score += fakeLinkScore
        }

        val unknownNumericSender =
            senderText.matches(Regex("^[0-9+]+$")) && senderText.length >= 10

        if (unknownNumericSender) score += 1

        val hasOtp = containsAny(otpWords) || containsAny(hindiOtpWords)
        val hasBanking = containsAny(englishBankingWords) || containsAny(hindiBankingWords)
        val hasReward = containsAny(rewardWords) || containsAny(hindiRewardWords)
        val hasFraudAction = containsAny(fraudActionWords) || containsAny(hindiFraudActionWords)
        val hasUrgency = containsAny(urgencyWords) || containsAny(hindiUrgencyWords)

        val looksLikeSafeTransaction =
            containsAny(safeTransactionWords) &&
                    !hasLink &&
                    !hasShortLink &&
                    !hasFraudAction &&
                    !hasOtp &&
                    !text.contains("share") &&
                    !text.contains("bhejo")

        if (looksLikeSafeTransaction && aiPrediction.aiRisk <= 30) {
            return IncomingScanResult(
                resultText = "✅ Looks Safe",
                riskPercent = 5,
                category = "Normal Transaction",
                summary = "This looks like a normal bank or UPI transaction SMS. AI also marked it safe."
            )
        }

        if (hasOtp && (hasFraudAction || hasReward || text.contains("refund") || text.contains("paisa"))) {
            score += 5
        }

        if (hasBanking && hasLink && hasUrgency) {
            score += 5
        }

        if (hasReward && hasLink) {
            score += 4
        }

        val category = when {
            aiPrediction.aiLabel.contains("Scam") -> "AI Scam Detection"
            aiPrediction.aiLabel.contains("Suspicious") -> "AI Suspicious Pattern"
            hasBanking && hasOtp -> "Banking / OTP Scam"
            hasOtp && hasFraudAction -> "OTP / Credential Theft"
            hasBanking -> "Banking Scam"
            hasReward -> "Lottery / Reward Scam"
            text.contains("refund") || text.contains("loan") || text.contains("paisa") -> "Financial Fraud"
            hasLink || hasShortLink -> "Suspicious Link"
            else -> "General"
        }

        val ruleRiskPercent = when {
            score >= 15 -> 98
            score >= 12 -> 90
            score >= 9 -> 80
            score >= 7 -> 65
            score >= 5 -> 50
            score >= 3 -> 35
            score >= 1 -> 15
            else -> 5
        }

        val finalRiskPercent = maxOf(ruleRiskPercent, aiPrediction.aiRisk)

        return when {
            finalRiskPercent >= 80 -> IncomingScanResult(
                resultText = "⚠ High Risk Scam Message",
                riskPercent = finalRiskPercent,
                category = category,
                summary = "AI + rules detected strong scam pattern. Do not click links or share OTP, PIN, password, or banking details."
            )

            finalRiskPercent >= 40 -> IncomingScanResult(
                resultText = "⚠ Suspicious Message",
                riskPercent = finalRiskPercent,
                category = category,
                summary = "AI found this SMS suspicious. Verify sender identity before taking any action."
            )

            else -> IncomingScanResult(
                resultText = "✅ Looks Safe",
                riskPercent = finalRiskPercent,
                category = "Normal",
                summary = "No strong scam pattern detected by rules or AI."
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

        val prefs =
            context.getSharedPreferences("shieldx_prefs", Context.MODE_PRIVATE)

        val gson = Gson()

        val historyJson =
            prefs.getString("scan_history", "[]")

        val type = object : TypeToken<MutableList<HistoryItem>>() {}.type

        val historyList: MutableList<HistoryItem> =
            gson.fromJson(historyJson, type)

        historyList.add(
            0,
            HistoryItem(
                sender,
                message,
                result,
                category,
                riskPercent
            )
        )

        prefs.edit()
            .putString(
                "scan_history",
                gson.toJson(historyList)
            )
            .apply()
    }

    private fun showNotification(
        context: Context,
        result: String,
        sender: String,
        message: String
    ) {
        val channelId = "shieldx_alert_channel"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ShieldX Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications for scam SMS alerts"
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
            "🚨 ShieldX AI High Risk SMS"
        } else {
            "⚠️ ShieldX AI Suspicious SMS"
        }

        val text = "AI detected possible scam. Tap to view details."

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
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