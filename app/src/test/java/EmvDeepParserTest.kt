package com.nfc.reader.nfc

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for EmvDeepParser companion object methods
 * Tests pure-logic components that don't require Android framework.
 */
class EmvDeepParserTest {

    @Test
    fun testDecodeAip_sdaSupported() {
        // Byte1 bit 6 set = SDA supported
        val aip = byteArrayOf(0x40, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("SDA supported"))
    }

    @Test
    fun testDecodeAip_ddaSupported() {
        // Byte1 bit 5 set = DDA supported
        val aip = byteArrayOf(0x20, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("DDA supported"))
    }

    @Test
    fun testDecodeAip_cardholderVerification() {
        // Byte1 bit 4 set = Cardholder verification
        val aip = byteArrayOf(0x10, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("Cardholder verification supported"))
    }

    @Test
    fun testDecodeAip_terminalRiskManagement() {
        // Byte1 bit 3 set = Terminal risk management
        val aip = byteArrayOf(0x08, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("Terminal risk management required"))
    }

    @Test
    fun testDecodeAip_cdaSupported() {
        // Byte1 bit 0 set = CDA supported
        val aip = byteArrayOf(0x01, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("CDA supported"))
    }

    @Test
    fun testDecodeAip_msdSupported() {
        // Byte2 bit 7 set = MSD supported
        val aip = byteArrayOf(0x00, 0x80.toByte())
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("MSD supported (magnetic stripe)"))
    }

    @Test
    fun testDecodeAip_noCapabilities() {
        val aip = byteArrayOf(0x00, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("No special capabilities"))
    }

    @Test
    fun testDecodeAip_multipleCapabilities() {
        // SDA + DDA + Cardholder verification
        val aip = byteArrayOf(0x70, 0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertTrue(features.contains("SDA supported"))
        assertTrue(features.contains("DDA supported"))
        assertTrue(features.contains("Cardholder verification supported"))
    }

    @Test
    fun testDecodeAip_invalidShortInput() {
        val aip = byteArrayOf(0x00)
        val features = EmvDeepParser.decodeAip(aip)
        assertEquals(1, features.size)
        assertTrue(features[0].contains("Invalid"))
    }

    @Test
    fun testParseCvmList_basic() {
        // Amount X = 0, Amount Y = 0, followed by CVM rules
        val data = byteArrayOf(
            0x00, 0x00, 0x00, 0x00,  // Amount X
            0x00, 0x00, 0x00, 0x00,  // Amount Y
            0x42, 0x03,              // Enciphered PIN online + apply next + terminal supports CVM
            0x1F.toByte(), 0x00      // No CVM required + Always
        )
        val methods = EmvDeepParser.parseCvmList(data)
        assertTrue(methods.size >= 3) // Amount line + 2 CVM rules
        assertTrue(methods[0].contains("Amount X"))
    }

    @Test
    fun testParseCvmList_invalidShortInput() {
        val data = byteArrayOf(0x00, 0x01, 0x02)
        val methods = EmvDeepParser.parseCvmList(data)
        assertEquals(1, methods.size)
        assertTrue(methods[0].contains("Invalid"))
    }

    @Test
    fun testCommonAids_notEmpty() {
        assertTrue(EmvDeepParser.COMMON_AIDS.isNotEmpty())
    }

    @Test
    fun testCommonAids_containsVisa() {
        assertTrue(EmvDeepParser.COMMON_AIDS.containsKey("Visa Credit/Debit"))
        assertEquals("A0000000031010", EmvDeepParser.COMMON_AIDS["Visa Credit/Debit"])
    }

    @Test
    fun testCommonAids_containsMastercard() {
        assertTrue(EmvDeepParser.COMMON_AIDS.containsKey("Mastercard"))
        assertEquals("A0000000041010", EmvDeepParser.COMMON_AIDS["Mastercard"])
    }

    @Test
    fun testCommonAids_containsAmex() {
        assertTrue(EmvDeepParser.COMMON_AIDS.containsKey("AMEX"))
        assertEquals("A00000002501", EmvDeepParser.COMMON_AIDS["AMEX"])
    }

    @Test
    fun testEmvTags_containsKnownTags() {
        assertEquals("Application PAN", EmvDeepParser.EMV_TAGS["5A"])
        assertEquals("Application Expiration Date", EmvDeepParser.EMV_TAGS["5F24"])
        assertEquals("Cardholder Name", EmvDeepParser.EMV_TAGS["5F20"])
        assertEquals("Track 2 Equivalent Data", EmvDeepParser.EMV_TAGS["57"])
        assertEquals("PDOL", EmvDeepParser.EMV_TAGS["9F38"])
        assertEquals("Application File Locator", EmvDeepParser.EMV_TAGS["94"])
        assertEquals("Application Interchange Profile", EmvDeepParser.EMV_TAGS["82"])
        assertEquals("CVM List", EmvDeepParser.EMV_TAGS["8E"])
    }

    @Test
    fun testPdolTagLengths_containsKnownTags() {
        assertEquals(4, EmvDeepParser.PDOL_TAG_LENGTHS["9F66"])
        assertEquals(6, EmvDeepParser.PDOL_TAG_LENGTHS["9F02"])
        assertEquals(2, EmvDeepParser.PDOL_TAG_LENGTHS["9F1A"])
        assertEquals(2, EmvDeepParser.PDOL_TAG_LENGTHS["5F2A"])
        assertEquals(3, EmvDeepParser.PDOL_TAG_LENGTHS["9A"])
        assertEquals(1, EmvDeepParser.PDOL_TAG_LENGTHS["9C"])
        assertEquals(4, EmvDeepParser.PDOL_TAG_LENGTHS["9F37"])
    }

    @Test
    fun testExtensionToHex() {
        val bytes = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10)
        assertEquals("A0000000031010", bytes.toHex())
    }

    @Test
    fun testExtensionDecodeHex() {
        val hex = "A0000000031010"
        val bytes = hex.decodeHex()
        assertEquals(7, bytes.size)
        assertEquals(0xA0.toByte(), bytes[0])
        assertEquals(0x10.toByte(), bytes[6])
    }

    @Test
    fun testExtensionRoundTrip() {
        val original = "A0000000041010"
        assertEquals(original, original.decodeHex().toHex())
    }
}
