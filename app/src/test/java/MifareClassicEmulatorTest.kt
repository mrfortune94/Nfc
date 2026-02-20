package com.nfc.reader.hce

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for MifareClassicEmulator
 * Tests UID validation, response generation, and command handling.
 */
class MifareClassicEmulatorTest {

    // === UID Validation Tests ===

    @Test
    fun testValidUid_4Bytes() {
        assertTrue(MifareClassicEmulator.isValidMifareUid("AABBCCDD"))
    }

    @Test
    fun testValidUid_7Bytes() {
        assertTrue(MifareClassicEmulator.isValidMifareUid("01020304050607"))
    }

    @Test
    fun testValidUid_10Bytes() {
        assertTrue(MifareClassicEmulator.isValidMifareUid("0102030405060708090A"))
    }

    @Test
    fun testValidUid_withSpaces() {
        assertTrue(MifareClassicEmulator.isValidMifareUid("AA BB CC DD"))
    }

    @Test
    fun testValidUid_withColons() {
        assertTrue(MifareClassicEmulator.isValidMifareUid("AA:BB:CC:DD"))
    }

    @Test
    fun testValidUid_lowercaseHex() {
        assertTrue(MifareClassicEmulator.isValidMifareUid("aabbccdd"))
    }

    @Test
    fun testInvalidUid_empty() {
        assertFalse(MifareClassicEmulator.isValidMifareUid(""))
    }

    @Test
    fun testInvalidUid_oddLength() {
        assertFalse(MifareClassicEmulator.isValidMifareUid("AABBCCD"))
    }

    @Test
    fun testInvalidUid_wrongByteCount() {
        // 5 bytes is not a valid Mifare UID length
        assertFalse(MifareClassicEmulator.isValidMifareUid("AABBCCDDEE"))
    }

    @Test
    fun testInvalidUid_nonHexChars() {
        assertFalse(MifareClassicEmulator.isValidMifareUid("GGHHIIJJ"))
    }

    // === Card Variant Detection Tests ===

    @Test
    fun testDetectCardVariant_1kBySak() {
        val variant = MifareClassicEmulator.detectCardVariant(null, "08", null)
        assertEquals("Mifare Classic 1K", variant)
    }

    @Test
    fun testDetectCardVariant_4kBySak() {
        val variant = MifareClassicEmulator.detectCardVariant(null, "18", null)
        assertEquals("Mifare Classic 4K", variant)
    }

    @Test
    fun testDetectCardVariant_1kByMemorySize() {
        val variant = MifareClassicEmulator.detectCardVariant(null, null, 1024)
        assertEquals("Mifare Classic 1K", variant)
    }

    @Test
    fun testDetectCardVariant_4kByMemorySize() {
        val variant = MifareClassicEmulator.detectCardVariant(null, null, 4096)
        assertEquals("Mifare Classic 4K", variant)
    }

    @Test
    fun testDetectCardVariant_unknown() {
        val variant = MifareClassicEmulator.detectCardVariant(null, null, null)
        assertEquals("Mifare Classic", variant)
    }

    // === Response Generation Tests ===

    @Test
    fun testGetUidResponse_contains_uid_and_status() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        val response = emulator.getUidResponse()

        // UID (4 bytes) + status word 9000 (2 bytes) = 6 bytes
        assertEquals(6, response.size)
        // Check UID bytes
        assertEquals(0xAA.toByte(), response[0])
        assertEquals(0xBB.toByte(), response[1])
        assertEquals(0xCC.toByte(), response[2])
        assertEquals(0xDD.toByte(), response[3])
        // Check status word
        assertEquals(0x90.toByte(), response[4])
        assertEquals(0x00.toByte(), response[5])
    }

    @Test
    fun testGetAtqaResponse_default() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        val response = emulator.getAtqaResponse()

        // ATQA (2 bytes) + status word (2 bytes) = 4 bytes
        assertEquals(4, response.size)
        // Default ATQA for 1K: 0004
        assertEquals(0x00.toByte(), response[0])
        assertEquals(0x04.toByte(), response[1])
        // Status word
        assertEquals(0x90.toByte(), response[2])
        assertEquals(0x00.toByte(), response[3])
    }

    @Test
    fun testGetAtqaResponse_custom() {
        val emulator = MifareClassicEmulator("AABBCCDD", atqa = "0002")
        val response = emulator.getAtqaResponse()

        assertEquals(4, response.size)
        assertEquals(0x00.toByte(), response[0])
        assertEquals(0x02.toByte(), response[1])
    }

    @Test
    fun testGetSakResponse_default() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        val response = emulator.getSakResponse()

        // SAK (1 byte) + status word (2 bytes) = 3 bytes
        assertEquals(3, response.size)
        // Default SAK for 1K: 08
        assertEquals(0x08.toByte(), response[0])
        // Status word
        assertEquals(0x90.toByte(), response[1])
        assertEquals(0x00.toByte(), response[2])
    }

    @Test
    fun testGetSakResponse_custom() {
        val emulator = MifareClassicEmulator("AABBCCDD", sak = "18")
        val response = emulator.getSakResponse()

        assertEquals(3, response.size)
        assertEquals(0x18.toByte(), response[0])
    }

    @Test
    fun testGetCardIdentityResponse_containsTlvStructure() {
        val emulator = MifareClassicEmulator("AABBCCDD", atqa = "0004", sak = "08")
        val response = emulator.getCardIdentityResponse()

        // TLV: C0 04 AABBCCDD + C1 02 0004 + C2 01 08 + 9000
        // = 2+4 + 2+2 + 2+1 + 2 = 15 bytes
        assertEquals(15, response.size)

        // Tag C0 (UID)
        assertEquals(0xC0.toByte(), response[0])
        assertEquals(0x04.toByte(), response[1])

        // Tag C1 (ATQA)
        assertEquals(0xC1.toByte(), response[6])
        assertEquals(0x02.toByte(), response[7])

        // Tag C2 (SAK)
        assertEquals(0xC2.toByte(), response[10])
        assertEquals(0x01.toByte(), response[11])

        // Status word at end
        assertEquals(0x90.toByte(), response[13])
        assertEquals(0x00.toByte(), response[14])
    }

    // === Command Handling Tests ===

    @Test
    fun testHandleCommand_getUid() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // GET DATA INS=CA P1=00 P2=00
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x00, 0x00)
        val response = emulator.handleCommand(command)

        assertNotNull(response)
        assertEquals(6, response!!.size) // 4 UID + 2 SW
    }

    @Test
    fun testHandleCommand_getAtqa() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // GET DATA INS=CA P1=00 P2=01
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x00, 0x01)
        val response = emulator.handleCommand(command)

        assertNotNull(response)
        assertEquals(4, response!!.size) // 2 ATQA + 2 SW
    }

    @Test
    fun testHandleCommand_getSak() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // GET DATA INS=CA P1=00 P2=02
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x00, 0x02)
        val response = emulator.handleCommand(command)

        assertNotNull(response)
        assertEquals(3, response!!.size) // 1 SAK + 2 SW
    }

    @Test
    fun testHandleCommand_getCardIdentity() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // GET DATA INS=CA P1=00 P2=03
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x00, 0x03)
        val response = emulator.handleCommand(command)

        assertNotNull(response)
        assertTrue(response!!.size > 4)
    }

    @Test
    fun testHandleCommand_unknownP2_returnsNull() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // GET DATA INS=CA P1=00 P2=FF
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x00, 0xFF.toByte())
        val response = emulator.handleCommand(command)

        assertNull(response)
    }

    @Test
    fun testHandleCommand_nonGetData_returnsNull() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // SELECT command INS=A4
        val command = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
        val response = emulator.handleCommand(command)

        assertNull(response)
    }

    @Test
    fun testHandleCommand_wrongP1_returnsNull() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        // GET DATA INS=CA P1=01 P2=00
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x01, 0x00)
        val response = emulator.handleCommand(command)

        assertNull(response)
    }

    @Test
    fun testHandleCommand_tooShort_returnsNull() {
        val emulator = MifareClassicEmulator("AABBCCDD")
        val command = byteArrayOf(0x00, 0xCA.toByte(), 0x00)
        val response = emulator.handleCommand(command)

        assertNull(response)
    }

    // === 7-byte UID Tests ===

    @Test
    fun testGetUidResponse_7byteUid() {
        val emulator = MifareClassicEmulator("01020304050607")
        val response = emulator.getUidResponse()

        // 7 UID bytes + 2 status bytes = 9
        assertEquals(9, response.size)
        assertEquals(0x01.toByte(), response[0])
        assertEquals(0x07.toByte(), response[6])
        assertEquals(0x90.toByte(), response[7])
        assertEquals(0x00.toByte(), response[8])
    }
}
