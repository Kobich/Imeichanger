package com.engboost.imeichanger.data

import com.engboost.imeichanger.domain.DeviceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCsvTest {

    @Test
    fun parsesCatalogLine() {
        assertEquals(
            DeviceModel(brand = "10.OR", model = "10.OR D", tac = "91161200"),
            DeviceCsv.parseLine("10.OR,91161200,10.OR,10.OR D"),
        )
    }

    @Test
    fun restoresLeadingZerosStrippedByExcel() {
        assertEquals("00440207", DeviceCsv.parseLine("2D,440207,2D,2D TEST")?.tac)
        assertEquals("01326100", DeviceCsv.parseLine("2GIG,1326100,2GIG,2GIG CP1")?.tac)
    }

    @Test
    fun fallsBackToSpecsWhenModelIsMissing() {
        assertEquals("10.OR", DeviceCsv.parseLine("10.OR,91161200,10.OR")?.model)
        assertEquals("10.OR", DeviceCsv.parseLine("10.OR,91161200,10.OR,")?.model)
    }

    @Test
    fun parsesSemicolonSeparatedAndQuotedFields() {
        assertEquals(
            DeviceModel(brand = "Acme", model = "Phone \"X\", 5G", tac = "35123456"),
            DeviceCsv.parseLine("Acme;35123456;Acme;\"Phone \"\"X\"\", 5G\""),
        )
    }

    @Test
    fun skipsHeaderBlankAndBrokenLines() {
        assertNull(DeviceCsv.parseLine("brand,tac,specs,model"))
        assertNull(DeviceCsv.parseLine(""))
        assertNull(DeviceCsv.parseLine("10.OR"))
        assertNull(DeviceCsv.parseLine("10.OR,911612001,10.OR,10.OR D"))
        assertNull(DeviceCsv.parseLine("10.OR,9116A200,10.OR,10.OR D"))
    }
}
