package com.nfc.reader.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for utility extensions
 */
class ExtensionsTest {
    
    @Test
    fun testByteArrayToHexString() {
        val bytes = byteArrayOf(0x12, 0x34, 0xAB.toByte(), 0xCD.toByte())
        val hex = bytes.toHexString()
        
        assertEquals("1234ABCD", hex)
    }
    
    @Test
    fun testEmptyByteArrayToHexString() {
        val bytes = byteArrayOf()
        val hex = bytes.toHexString()
        
        assertEquals("", hex)
    }
    
    @Test
    fun testHexStringToByteArray() {
        val hex = "1234ABCD"
        val bytes = hex.hexToByteArray()
        
        assertEquals(4, bytes.size)
        assertEquals(0x12.toByte(), bytes[0])
        assertEquals(0x34.toByte(), bytes[1])
        assertEquals(0xAB.toByte(), bytes[2])
        assertEquals(0xCD.toByte(), bytes[3])
    }
    
    @Test
    fun testHexStringWithSpacesToByteArray() {
        val hex = "12 34 AB CD"
        val bytes = hex.hexToByteArray()
        
        assertEquals(4, bytes.size)
    }
    
    @Test
    fun testTimestampToDateString() {
        val timestamp = 1700000000000L // Fixed timestamp
        val dateString = timestamp.toDateString()
        
        // Just verify it returns a non-empty string
        assertTrue(dateString.isNotEmpty())
        assertTrue(dateString.contains("-"))
        assertTrue(dateString.contains(":"))
    }
}
