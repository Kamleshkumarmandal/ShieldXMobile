package com.example.shieldxmobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamMessageHeuristicTest {
    private val detector = ScamMessageHeuristic()

    @Test
    fun blankMessageHasNoRisk() {
        val result = detector.analyze("   ")
        assertEquals(0, result.riskScore)
        assertTrue(result.matchedSignals.isEmpty())
    }

    @Test
    fun ordinaryTransactionDoesNotMatchScamPatterns() {
        val result = detector.analyze("Your account was credited with INR 5000. Transaction successful.")
        assertEquals(0, result.riskScore)
        assertEquals("No strong scam pattern detected", result.label)
    }

    @Test
    fun credentialRequestAndSuspiciousLinkRaiseRisk() {
        val result = detector.analyze(
            "Urgent! Share OTP now and click here https://claim-prize.xyz"
        )
        assertTrue(result.riskScore >= 60)
        assertEquals("High-risk pattern", result.label)
        assertTrue(result.matchedSignals.isNotEmpty())
    }

    @Test
    fun linkWithoutOtherSignalsIsNotCalledSafe() {
        val result = detector.analyze("Visit https://example.xyz for details.")
        assertTrue(result.riskScore > 0)
        assertTrue(result.label != "No strong scam pattern detected")
    }
}
