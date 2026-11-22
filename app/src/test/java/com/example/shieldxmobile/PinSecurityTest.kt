package com.example.shieldxmobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {

    @Test
    fun hashIsStableForSameSaltAndVerifiesCorrectPin() {
        val pin = "0427"
        val salt = PinSecurity.createSalt()
        val firstHash = PinSecurity.hashPin(pin, salt)
        val secondHash = PinSecurity.hashPin(pin, salt)

        assertTrue(firstHash == secondHash)
        assertTrue(PinSecurity.verifyPin(pin, salt, firstHash))
        assertFalse(PinSecurity.verifyPin("0428", salt, firstHash))
    }

    @Test
    fun differentSaltsProduceDifferentHashes() {
        val pin = "7319"
        val firstSalt = PinSecurity.createSalt()
        val secondSalt = PinSecurity.createSalt()

        assertNotEquals(
            PinSecurity.hashPin(pin, firstSalt),
            PinSecurity.hashPin(pin, secondSalt)
        )
    }

    @Test
    fun invalidPinsAreRejected() {
        val salt = PinSecurity.createSalt()
        assertFalse(PinSecurity.verifyPin("123", salt, PinSecurity.hashPin("1234", salt)))
        assertFalse(PinSecurity.verifyPin("12a4", salt, PinSecurity.hashPin("1234", salt)))
    }
}
