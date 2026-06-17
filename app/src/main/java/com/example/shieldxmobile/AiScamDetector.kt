package com.example.shieldxmobile

class AiScamDetector {

    data class AiPrediction(
        val aiRisk: Int,
        val aiLabel: String,
        val confidence: Double
    )

    fun predict(message: String): AiPrediction {

        val text = message.lowercase()

        var aiScore = 0

        val dangerousPatterns = listOf(
            "otp",
            "refund",
            "verify now",
            "click link",
            "bank blocked",
            "kyc",
            "share otp",
            "pin",
            "urgent",
            "reward",
            "lottery",
            "claim now",
            "click here",
            "account suspended",
            "upi blocked",
            "password"

        )

        val hindiPatterns = listOf(
            "otp bhejo",
            "otp share",
            "pin bhejo",
            "bank account band",
            "kyc update",
            "jaldi karo",
            "turant",
            "inaam",
            "jeet gaye",
            "refund pane ke liye"
        )

        for (pattern in dangerousPatterns) {
            if (text.contains(pattern)) {
                aiScore += 8
            }
        }

        for (pattern in hindiPatterns) {
            if (text.contains(pattern)) {
                aiScore += 10
            }
        }

        if (
            text.contains("http://") ||
            text.contains("https://") ||
            text.contains("bit.ly") ||
            text.contains(".xyz")
        ) {
            aiScore += 15
        }

        return when {

            aiScore >= 40 -> AiPrediction(
                aiRisk = 95,
                aiLabel = "AI Detected Scam",
                confidence = 95.0
            )

            aiScore >= 25 -> AiPrediction(
                aiRisk = 75,
                aiLabel = "AI Suspicious",
                confidence = 80.0
            )

            else -> AiPrediction(
                aiRisk = 10,
                aiLabel = "AI Safe",
                confidence = 90.0
            )
        }
    }
}