package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceImei
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCsvTest {

    @Test
    fun parsesCommaSeparatedLine() {
        assertEquals(
            DeviceImei("Galaxy S24", "Samsung", "356938035643809"),
            DeviceCsv.parseLine("Galaxy S24,Samsung,356938035643809"),
        )
    }

    @Test
    fun parsesSemicolonSeparatedLineWithCommaInName() {
        assertEquals(
            DeviceImei("Galaxy S24, 8/256", "Samsung", "356938035643809"),
            DeviceCsv.parseLine("Galaxy S24, 8/256;Samsung;356938035643809"),
        )
    }

    @Test
    fun parsesQuotedFields() {
        assertEquals(
            DeviceImei("Phone \"X\", 5G", "Acme", "356938035643809"),
            DeviceCsv.parseLine("\"Phone \"\"X\"\", 5G\", Acme , 356938035643809"),
        )
    }

    @Test
    fun skipsHeaderBlankAndBrokenLines() {
        assertNull(DeviceCsv.parseLine("name,company,imei"))
        assertNull(DeviceCsv.parseLine(""))
        assertNull(DeviceCsv.parseLine("Galaxy S24,Samsung"))
        assertNull(DeviceCsv.parseLine("Galaxy S24,Samsung,356938035643808"))
        assertNull(DeviceCsv.parseLine(",Samsung,356938035643809"))
    }
}
