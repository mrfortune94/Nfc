package com.nfc.reader.nfc

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for APDU Handler
 */
class ApduHandlerTest {
    
    private val apduHandler = ApduHandler()
    
    @Test
    fun testApduCommandConstruction() {
        val command = ApduHandler.ApduCommand(
            cla = 0x00,
            ins = 0xA4.toByte(),
            p1 = 0x04,
            p2 = 0x00,
            data = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10),
            le = 256
        )
        
        val bytes = command.toByteArray()
        
        // Check command structure
        assertEquals(0x00.toByte(), bytes[0]) // CLA
        assertEquals(0xA4.toByte(), bytes[1]) // INS (SELECT)
        assertEquals(0x04.toByte(), bytes[2]) // P1
        assertEquals(0x00.toByte(), bytes[3]) // P2
        assertEquals(7, bytes[4].toInt()) // Lc (data length)
    }
    
    @Test
    fun testParseHexCommand() {
        val hexString = "00A4040007A0000000031010"
        val bytes = apduHandler.parseHexCommand(hexString)
        
        assertEquals(12, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0xA4.toByte(), bytes[1])
    }
    
    @Test
    fun testParseHexCommandWithSpaces() {
        val hexString = "00 A4 04 00 07 A0 00 00 00 03 10 10"
        val bytes = apduHandler.parseHexCommand(hexString)
        
        assertEquals(12, bytes.size)
    }
    
    @Test
    fun testApduResponse() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(0x12, 0x34),
            sw1 = 0x90.toByte(),
            sw2 = 0x00.toByte()
        )
        
        assertTrue(response.isSuccess)
        assertEquals(0x9000, response.statusWord)
    }
    
    @Test
    fun testApduResponseError() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(),
            sw1 = 0x6A.toByte(),
            sw2 = 0x82.toByte()
        )
        
        assertFalse(response.isSuccess)
        assertEquals(0x6A82, response.statusWord)
    }
    
    // === New tests for EMV contactless features ===
    
    @Test
    fun testApduResponseHasMoreData() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(0x01, 0x02),
            sw1 = 0x61.toByte(),
            sw2 = 0x20.toByte()
        )
        
        assertTrue(response.hasMoreData)
        assertEquals(32, response.remainingBytes)
        assertFalse(response.isSuccess)
        assertFalse(response.isWrongLe)
    }
    
    @Test
    fun testApduResponseWrongLe() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(),
            sw1 = 0x6C.toByte(),
            sw2 = 0x10.toByte()
        )
        
        assertTrue(response.isWrongLe)
        assertEquals(16, response.correctLe)
        assertFalse(response.isSuccess)
        assertFalse(response.hasMoreData)
    }
    
    @Test
    fun testApduResponseSuccessNoMoreData() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(0x12, 0x34),
            sw1 = 0x90.toByte(),
            sw2 = 0x00.toByte()
        )
        
        assertTrue(response.isSuccess)
        assertFalse(response.hasMoreData)
        assertFalse(response.isWrongLe)
        assertEquals(0, response.remainingBytes)
        assertEquals(0, response.correctLe)
    }
    
    @Test
    fun testStatusDescriptionSuccess() {
        val desc = ApduHandler.describeStatusWord(0x9000)
        assertEquals("Success", desc)
    }
    
    @Test
    fun testStatusDescriptionFileNotFound() {
        val desc = ApduHandler.describeStatusWord(0x6A82)
        assertEquals("File or application not found", desc)
    }
    
    @Test
    fun testStatusDescriptionMoreData() {
        val desc = ApduHandler.describeStatusWord(0x6120)
        assertTrue(desc.contains("More data available"))
        assertTrue(desc.contains("32"))
    }
    
    @Test
    fun testStatusDescriptionWrongLe() {
        val desc = ApduHandler.describeStatusWord(0x6C10)
        assertTrue(desc.contains("Wrong Le"))
        assertTrue(desc.contains("16"))
    }
    
    @Test
    fun testStatusDescriptionConditionsNotSatisfied() {
        val desc = ApduHandler.describeStatusWord(0x6985)
        assertEquals("Conditions of use not satisfied", desc)
    }
    
    @Test
    fun testStatusDescriptionWrongLength() {
        val desc = ApduHandler.describeStatusWord(0x6700)
        assertEquals("Wrong length", desc)
    }
    
    @Test
    fun testStatusDescriptionUnknown() {
        val desc = ApduHandler.describeStatusWord(0x1234)
        assertTrue(desc.contains("Unknown"))
    }
    
    @Test
    fun testApduResponseStatusDescription() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(),
            sw1 = 0x90.toByte(),
            sw2 = 0x00.toByte()
        )
        assertEquals("Success", response.statusDescription)
    }
    
    @Test
    fun testApduResponseToStringIncludesDescription() {
        val response = ApduHandler.ApduResponse(
            data = byteArrayOf(),
            sw1 = 0x90.toByte(),
            sw2 = 0x00.toByte()
        )
        val str = response.toString()
        assertTrue(str.contains("9000"))
        assertTrue(str.contains("Success"))
    }
    
    @Test
    fun testGpoCommandConstruction() {
        // GPO with empty PDOL should produce: 80 A8 00 00 02 83 00 00
        val pdolData = byteArrayOf()
        val commandData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
        val command = ApduHandler.ApduCommand(
            cla = 0x80.toByte(),
            ins = 0xA8.toByte(),
            p1 = 0x00,
            p2 = 0x00,
            data = commandData,
            le = 256
        )
        val bytes = command.toByteArray()
        
        assertEquals(0x80.toByte(), bytes[0]) // CLA
        assertEquals(0xA8.toByte(), bytes[1]) // INS (GPO)
        assertEquals(0x00.toByte(), bytes[2]) // P1
        assertEquals(0x00.toByte(), bytes[3]) // P2
        assertEquals(2, bytes[4].toInt())      // Lc = 2 (tag 83 + length 0)
        assertEquals(0x83.toByte(), bytes[5]) // Tag 83
        assertEquals(0x00.toByte(), bytes[6]) // PDOL length 0
    }
    
    @Test
    fun testReadRecordCommandConstruction() {
        // READ RECORD SFI=1, Record=1: 00 B2 01 0C 00
        val sfi = 1
        val record = 1
        val command = ApduHandler.ApduCommand(
            cla = 0x00,
            ins = 0xB2.toByte(),
            p1 = record.toByte(),
            p2 = ((sfi shl 3) or 0x04).toByte(),
            le = 256
        )
        val bytes = command.toByteArray()
        
        assertEquals(0x00.toByte(), bytes[0]) // CLA
        assertEquals(0xB2.toByte(), bytes[1]) // INS (READ RECORD)
        assertEquals(0x01.toByte(), bytes[2]) // P1 (record number)
        assertEquals(0x0C.toByte(), bytes[3]) // P2 (SFI 1 << 3 | 0x04 = 0x0C)
    }
    
    @Test
    fun testGenerateACCommandConstruction() {
        // GENERATE AC with ARQC reference control
        val referenceControl: Byte = 0x80.toByte() // ARQC
        val cdolData = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00) // Amount
        val command = ApduHandler.ApduCommand(
            cla = 0x80.toByte(),
            ins = 0xAE.toByte(),
            p1 = referenceControl,
            p2 = 0x00,
            data = cdolData,
            le = 256
        )
        val bytes = command.toByteArray()
        
        assertEquals(0x80.toByte(), bytes[0]) // CLA
        assertEquals(0xAE.toByte(), bytes[1]) // INS (GENERATE AC)
        assertEquals(0x80.toByte(), bytes[2]) // P1 (ARQC)
        assertEquals(0x00.toByte(), bytes[3]) // P2
        assertEquals(6, bytes[4].toInt())      // Lc
    }

    @Test
    fun testStatusDescriptions_containsKeyEntries() {
        assertTrue(ApduHandler.STATUS_DESCRIPTIONS.containsKey(0x9000))
        assertTrue(ApduHandler.STATUS_DESCRIPTIONS.containsKey(0x6A82))
        assertTrue(ApduHandler.STATUS_DESCRIPTIONS.containsKey(0x6985))
        assertTrue(ApduHandler.STATUS_DESCRIPTIONS.containsKey(0x6F00))
        assertTrue(ApduHandler.STATUS_DESCRIPTIONS.containsKey(0x6700))
    }
}
