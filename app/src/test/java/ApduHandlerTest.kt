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
}
