package com.engboost.imeichanger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ImeiTest {

    @Test
    fun validImeiPasses() {
        assertNull(Imei.validate("356938035643809"))
        assertNull(Imei.validate("490154203237518"))
    }

    @Test
    fun invalidImeiIsRejected() {
        assertEquals(ImeiError.WRONG_LENGTH, Imei.validate(""))
        assertEquals(ImeiError.WRONG_LENGTH, Imei.validate("35693803564380"))
        assertEquals(ImeiError.NOT_DIGITS, Imei.validate("35693803564380a"))
        assertEquals(ImeiError.INVALID_CHECKSUM, Imei.validate("356938035643808"))
    }

    @Test
    fun generatedImeiIsValidAndStartsWithTac() {
        val random = Random(42)
        repeat(10_000) {
            val imei = Imei.generate(tac = "91161200", random = random)
            assertNull(Imei.validate(imei))
            assertTrue(imei.startsWith("91161200"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateRejectsShortTac() {
        Imei.generate(tac = "440207")
    }
}
