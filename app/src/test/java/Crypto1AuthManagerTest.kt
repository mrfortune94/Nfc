package com.nfc.reader.nfc

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Crypto1AuthManager
 * Tests the pure-logic components that don't require Android framework.
 */
class Crypto1AuthManagerTest {

    @Test
    fun testParseAccessBits_defaultTransportConfig() {
        // Default transport configuration: FF 07 80 69
        // Key A = FFFFFFFFFFFF, Access bits = FF0780, Key B = FFFFFFFFFFFF
        // Byte 6=0xFF, Byte 7=0x07, Byte 8=0x80, Byte 9=0x69
        // This means C1=0x0, C2=0x0, C3=0x0 for all blocks -> full read/write access
        val trailerBlock = ByteArray(16)
        // Key A (bytes 0-5)
        for (i in 0..5) trailerBlock[i] = 0xFF.toByte()
        // Access bits
        trailerBlock[6] = 0xFF.toByte() // ~C2(high nibble) | ~C1(low nibble)
        trailerBlock[7] = 0x07.toByte() // C1(high nibble) | ~C3(low nibble)
        trailerBlock[8] = 0x80.toByte() // C3(high nibble) | C2(low nibble)
        trailerBlock[9] = 0x69.toByte() // user data byte
        // Key B (bytes 10-15)
        for (i in 10..15) trailerBlock[i] = 0xFF.toByte()

        val result = Crypto1AuthManager.parseAccessBits(trailerBlock, 4)

        assertTrue("Access bits should be valid", result.isValid)
        assertEquals(4, result.blockAccessConditions.size)

        // All data blocks should have C1=0, C2=0, C3=0 (full access)
        for (i in 0..2) {
            val block = result.blockAccessConditions[i]
            assertEquals(0, block.c1)
            assertEquals(0, block.c2)
            assertEquals(0, block.c3)
        }

        // Sector trailer (block 3) should be C1=0, C2=0, C3=1 (transport config)
        val trailer = result.blockAccessConditions[3]
        assertEquals(0, trailer.c1)
        assertEquals(0, trailer.c2)
        assertEquals(1, trailer.c3)
    }

    @Test
    fun testParseAccessBits_invalidShortTrailer() {
        val shortBlock = ByteArray(8) // Too short
        val result = Crypto1AuthManager.parseAccessBits(shortBlock, 4)

        assertFalse("Should be invalid for short trailer", result.isValid)
        assertNotNull(result.validationError)
        assertTrue(result.blockAccessConditions.isEmpty())
    }

    @Test
    fun testParseAccessBits_customConfig() {
        // Custom config where block 0 has C1=1, C2=0, C3=0 -> Read Key A|B, Write Key B
        val trailerBlock = ByteArray(16)
        for (i in 0..5) trailerBlock[i] = 0xFF.toByte()
        // Set C1 bit 0 = 1, all others = 0
        // C1 = 0x1 (bit 0 set), C2 = 0x0, C3 = 0x0
        // Byte 6: ~C2(high) | ~C1(low) = 0xF | 0xE = 0xFE
        // Byte 7: C1(high) | ~C3(low) = 0x1 shl 4 | 0xF = 0x1F
        // Byte 8: C3(high) | C2(low) = 0x0 | 0x0 = 0x00
        trailerBlock[6] = 0xFE.toByte()
        trailerBlock[7] = 0x1F.toByte()
        trailerBlock[8] = 0x00.toByte()
        trailerBlock[9] = 0x00.toByte()
        for (i in 10..15) trailerBlock[i] = 0xFF.toByte()

        val result = Crypto1AuthManager.parseAccessBits(trailerBlock, 4)

        assertTrue("Access bits should be valid", result.isValid)
        assertEquals(4, result.blockAccessConditions.size)

        // Block 0 should have C1=1, C2=0, C3=0
        val block0 = result.blockAccessConditions[0]
        assertEquals(1, block0.c1)
        assertEquals(0, block0.c2)
        assertEquals(0, block0.c3)
    }

    @Test
    fun testDescribeDataBlockAccess_fullAccess() {
        val desc = Crypto1AuthManager.describeDataBlockAccess(0, 0, 0)
        assertTrue(desc.contains("Read/Write"))
        assertTrue(desc.contains("Increment"))
    }

    @Test
    fun testDescribeDataBlockAccess_readOnly() {
        val desc = Crypto1AuthManager.describeDataBlockAccess(0, 1, 0)
        assertTrue(desc.contains("Read"))
        assertTrue(desc.contains("never"))
    }

    @Test
    fun testDescribeDataBlockAccess_noAccess() {
        val desc = Crypto1AuthManager.describeDataBlockAccess(1, 1, 1)
        assertTrue(desc.contains("never"))
    }

    @Test
    fun testDescribeSectorTrailerAccess_transportConfig() {
        val desc = Crypto1AuthManager.describeSectorTrailerAccess(0, 1, 0)
        assertTrue(desc.contains("transport"))
    }

    @Test
    fun testDescribeSectorTrailerAccess_fullLock() {
        val desc = Crypto1AuthManager.describeSectorTrailerAccess(1, 1, 1)
        assertTrue(desc.contains("never"))
    }

    @Test
    fun testAllKnownKeysNotEmpty() {
        assertTrue(Crypto1AuthManager.ALL_KNOWN_KEYS.isNotEmpty())
        // All keys should be 6 bytes
        for (key in Crypto1AuthManager.ALL_KNOWN_KEYS) {
            assertEquals("Each key should be 6 bytes", 6, key.size)
        }
    }

    @Test
    fun testAllKnownKeysContainsDefaults() {
        val defaultKey = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val allZeros = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val madKey = byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte())

        assertTrue("Should contain default key",
            Crypto1AuthManager.ALL_KNOWN_KEYS.any { it.contentEquals(defaultKey) })
        assertTrue("Should contain all-zeros key",
            Crypto1AuthManager.ALL_KNOWN_KEYS.any { it.contentEquals(allZeros) })
        assertTrue("Should contain MAD key",
            Crypto1AuthManager.ALL_KNOWN_KEYS.any { it.contentEquals(madKey) })
    }

    @Test
    fun testSectorAuthResult_equality() {
        val result1 = Crypto1AuthManager.SectorAuthResult(
            sector = 0,
            keyASuccess = true,
            keyBSuccess = false
        )
        val result2 = Crypto1AuthManager.SectorAuthResult(
            sector = 0,
            keyASuccess = true,
            keyBSuccess = false
        )

        assertEquals(result1, result2)
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun testAccessBits_equality() {
        val bytes1 = byteArrayOf(0xFF.toByte(), 0x07, 0x80.toByte(), 0x69)
        val bytes2 = byteArrayOf(0xFF.toByte(), 0x07, 0x80.toByte(), 0x69)
        val ab1 = Crypto1AuthManager.AccessBits(bytes1, emptyList(), true)
        val ab2 = Crypto1AuthManager.AccessBits(bytes2, emptyList(), true)

        assertEquals(ab1, ab2)
        assertEquals(ab1.hashCode(), ab2.hashCode())
    }

    @Test
    fun testParseAccessBits_returnsRawBytes() {
        val trailerBlock = ByteArray(16)
        for (i in 0..5) trailerBlock[i] = 0xFF.toByte()
        trailerBlock[6] = 0xFF.toByte()
        trailerBlock[7] = 0x07.toByte()
        trailerBlock[8] = 0x80.toByte()
        trailerBlock[9] = 0x69.toByte()
        for (i in 10..15) trailerBlock[i] = 0xFF.toByte()

        val result = Crypto1AuthManager.parseAccessBits(trailerBlock, 4)

        assertEquals(4, result.rawBytes.size)
        assertEquals(0xFF.toByte(), result.rawBytes[0])
        assertEquals(0x07.toByte(), result.rawBytes[1])
        assertEquals(0x80.toByte(), result.rawBytes[2])
        assertEquals(0x69.toByte(), result.rawBytes[3])
    }

    @Test
    fun testDescribeDataBlockAccess_allCombinations() {
        // Test all 8 combinations of C1, C2, C3
        for (c1 in 0..1) {
            for (c2 in 0..1) {
                for (c3 in 0..1) {
                    val desc = Crypto1AuthManager.describeDataBlockAccess(c1, c2, c3)
                    assertTrue("Description should not be empty for c1=$c1 c2=$c2 c3=$c3",
                        desc.isNotEmpty())
                    assertFalse("Should not be 'Unknown' for valid combination",
                        desc.contains("Unknown"))
                }
            }
        }
    }

    @Test
    fun testDescribeSectorTrailerAccess_allCombinations() {
        // Test all 8 combinations
        for (c1 in 0..1) {
            for (c2 in 0..1) {
                for (c3 in 0..1) {
                    val desc = Crypto1AuthManager.describeSectorTrailerAccess(c1, c2, c3)
                    assertTrue("Description should not be empty for c1=$c1 c2=$c2 c3=$c3",
                        desc.isNotEmpty())
                    assertFalse("Should not be 'Unknown' for valid combination",
                        desc.contains("Unknown"))
                }
            }
        }
    }
}
