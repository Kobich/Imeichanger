package com.engboost.imeichanger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
