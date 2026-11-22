package com.example.shieldxmobile

import java.util.Locale

/**
 * Transparent keyword/rule-based SMS screening.
 *
 * This is not a trained AI/ML model. Scores are heuristic risk indicators,
 * not probabilities or guarantees that a message is safe or fraudulent.
 */
class ScamMessageHeuristic {

    data class Prediction(
        val riskScore: Int,
        val label: String,
        val matchedSignals: List<String>
    )

    fun analyze(message: String): Prediction {
        val text = message.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) {
            return Prediction(0, "No message to analyze", emptyList())
        }

        val signals = linkedSetOf<String>()
        var score = 0

        val credentialRequests = listOf(
            "share otp", "send otp", "provide otp", "enter otp", "otp bhejo",
            "otp share karo", "pin bhejo", "share your pin", "share pin",
            "send your password", "password bhejo", "tell me the otp",
            "verification code bhejo", "security code share"
        )
        val urgentActions = listOf(
            "verify now", "click here", "click the link", "unblock now",
            "update kyc now", "account suspended", "bank account blocked",
            "upi blocked", "account will be blocked", "claim now",
            "refund pane ke liye", "link kholo", "kyc update karo", "turant karo"
        )
        val rewardBait = listOf(
            "you won a prize", "you are a winner", "lottery winner",
            "claim your reward", "free gift", "inaam jeet gaye",
            "lucky winner", "cash prize"
        )
        val urgency = listOf(
            "immediately", "within 24 hours", "final warning",
            "last chance", "urgent action", "account expires today",
            "jaldi karo", "antim mauka"
        )

        fun addMatches(patterns: List<String>, points: Int, description: String) {
            val matches = patterns.filter { text.contains(it) }
            if (matches.isNotEmpty()) {
                score += points * matches.size.coerceAtMost(2)
                signals += description
            }
        }

        addMatches(credentialRequests, 28, "Requests an OTP, PIN, password or security code")
        addMatches(urgentActions, 16, "Uses an urgent account or verification action")
        addMatches(rewardBait, 12, "Uses a prize, reward or lottery claim")
        addMatches(urgency, 6, "Uses urgency or deadline language")

        val hasUrl = Regex(
            "(https?://|www\\.)[^\\s]+",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        val suspiciousTld = listOf(".xyz", ".top", ".click", ".gq", ".tk", ".buzz", ".loan")
            .any { text.contains(it) }
        val shortener = listOf("bit.ly", "tinyurl.com", "t.co/", "goo.gl/")
            .any { text.contains(it) }

        if (hasUrl) {
            score += 12
            signals += "Contains a web link"
        }
        if (suspiciousTld) {
            score += 18
            signals += "Contains a higher-risk link suffix"
        }
        if (shortener) {
            score += 12
            signals += "Contains a shortened link"
        }

        val boundedScore = score.coerceIn(0, 100)
        val label = when {
            boundedScore >= 60 -> "High-risk pattern"
            boundedScore >= 30 -> "Suspicious pattern"
            else -> "No strong scam pattern detected"
        }
        return Prediction(boundedScore, label, signals.toList())
    }
}
