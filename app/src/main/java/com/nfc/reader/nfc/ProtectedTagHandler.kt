package com.nfc.reader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import com.nfc.reader.utils.toHexString
import java.io.IOException

/**
 * Handler for security-protected NFC tags
 * Supports authentication and key management for user-owned tags only
 * 
 * IMPORTANT: This class is designed for educational/research purposes
 * and should only be used with tags that the user owns.
 */
class ProtectedTagHandler {

    sealed class AuthResult {
        data class Success(val message: String, val data: Map<String, Any> = emptyMap()) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    /**
     * Authenticate with Mifare Classic sector using provided key
     * Only for tags owned by the user
     */
    fun authenticateMifareClassic(
        tag: Tag,
        sector: Int,
        key: ByteArray,
        keyType: KeyType
    ): AuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()
            
            val authenticated = when (keyType) {
                KeyType.KEY_A -> mifare.authenticateSectorWithKeyA(sector, key)
                KeyType.KEY_B -> mifare.authenticateSectorWithKeyB(sector, key)
            }

            if (authenticated) {
                AuthResult.Success("Authentication successful for sector $sector")
            } else {
                AuthResult.Error("Authentication failed - invalid key")
            }
        } catch (e: IOException) {
            AuthResult.Error("I/O Error: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }

    /**
     * Read Mifare Classic sector after authentication
     */
    fun readMifareClassicSector(
        tag: Tag,
        sector: Int,
        key: ByteArray,
        keyType: KeyType
    ): AuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()
            
            val authenticated = when (keyType) {
                KeyType.KEY_A -> mifare.authenticateSectorWithKeyA(sector, key)
                KeyType.KEY_B -> mifare.authenticateSectorWithKeyB(sector, key)
            }

            if (!authenticated) {
                return AuthResult.Error("Authentication failed - cannot read sector")
            }

            val blockCount = mifare.getBlockCountInSector(sector)
            val firstBlock = mifare.sectorToBlock(sector)
            val blocks = mutableListOf<String>()

            for (block in 0 until blockCount) {
                try {
                    val data = mifare.readBlock(firstBlock + block)
                    blocks.add(data.toHexString())
                } catch (e: IOException) {
                    blocks.add("READ_ERROR")
                }
            }

            val data = mapOf(
                "sector" to sector,
                "blocks" to blocks,
                "blockCount" to blockCount
            )

            AuthResult.Success("Sector $sector read successfully", data)
        } catch (e: IOException) {
            AuthResult.Error("I/O Error: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }

    /**
     * Write to Mifare Classic sector after authentication
     * Only for user-owned tags
     */
    fun writeMifareClassicBlock(
        tag: Tag,
        block: Int,
        key: ByteArray,
        keyType: KeyType,
        data: ByteArray
    ): AuthResult {
        if (data.size != 16) {
            return AuthResult.Error("Block data must be exactly 16 bytes")
        }

        val mifare = MifareClassic.get(tag)
            ?: return AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()
            
            val sector = mifare.blockToSector(block)
            val authenticated = when (keyType) {
                KeyType.KEY_A -> mifare.authenticateSectorWithKeyA(sector, key)
                KeyType.KEY_B -> mifare.authenticateSectorWithKeyB(sector, key)
            }

            if (!authenticated) {
                return AuthResult.Error("Authentication failed - cannot write block")
            }

            // Safety check: don't write to sector trailer
            val sectorTrailer = mifare.sectorToBlock(sector) + mifare.getBlockCountInSector(sector) - 1
            if (block == sectorTrailer) {
                return AuthResult.Error("Cannot write to sector trailer - this would lock the card")
            }

            mifare.writeBlock(block, data)
            AuthResult.Success("Block $block written successfully")
        } catch (e: IOException) {
            AuthResult.Error("I/O Error: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }

    /**
     * Read all Mifare Classic sectors using provided keys
     */
    fun readAllMifareClassicSectors(
        tag: Tag,
        keyMap: Map<Int, Pair<ByteArray, KeyType>>
    ): AuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()
            
            val sectorCount = mifare.sectorCount
            val allData = mutableMapOf<Int, List<String>>()
            var successCount = 0

            for (sector in 0 until sectorCount) {
                val keyInfo = keyMap[sector]
                if (keyInfo == null) {
                    // Try default keys
                    val defaultAuthenticated = tryDefaultKeys(mifare, sector)
                    if (!defaultAuthenticated) {
                        allData[sector] = listOf("LOCKED")
                        continue
                    }
                } else {
                    val (key, keyType) = keyInfo
                    val authenticated = when (keyType) {
                        KeyType.KEY_A -> mifare.authenticateSectorWithKeyA(sector, key)
                        KeyType.KEY_B -> mifare.authenticateSectorWithKeyB(sector, key)
                    }
                    if (!authenticated) {
                        allData[sector] = listOf("AUTH_FAILED")
                        continue
                    }
                }

                val blockCount = mifare.getBlockCountInSector(sector)
                val firstBlock = mifare.sectorToBlock(sector)
                val blocks = mutableListOf<String>()

                for (block in 0 until blockCount) {
                    try {
                        val data = mifare.readBlock(firstBlock + block)
                        blocks.add(data.toHexString())
                    } catch (e: IOException) {
                        blocks.add("READ_ERROR")
                    }
                }

                allData[sector] = blocks
                successCount++
            }

            val data = mapOf(
                "sectors" to allData,
                "totalSectors" to sectorCount,
                "readSectors" to successCount
            )

            AuthResult.Success("Read $successCount/$sectorCount sectors", data)
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }

    /**
     * Get Mifare Ultralight password protection status
     */
    fun getMifareUltralightInfo(tag: Tag): AuthResult {
        val ultralight = MifareUltralight.get(tag)
            ?: return AuthResult.Error("Not a Mifare Ultralight tag")

        return try {
            ultralight.connect()
            
            val type = when (ultralight.type) {
                MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight (48 bytes)"
                MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C (192 bytes, 3DES auth)"
                else -> "Unknown type"
            }

            // Read first few pages
            val pages = mutableListOf<String>()
            for (pageIndex in 0 until 4) {
                try {
                    val data = ultralight.readPages(pageIndex * 4)
                    pages.add(data.toHexString())
                } catch (e: IOException) {
                    pages.add("PROTECTED")
                }
            }

            val data = mapOf(
                "type" to type,
                "maxTransceive" to ultralight.maxTransceiveLength,
                "pages" to pages
            )

            AuthResult.Success("Mifare Ultralight detected: $type", data)
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (ultralight.isConnected) {
                ultralight.close()
            }
        }
    }

    /**
     * Authenticate with ISO-DEP card using APDU
     * Used for ISO 7816-4 compliant cards (DESFire, etc.)
     */
    fun authenticateIsoDep(
        tag: Tag,
        aid: ByteArray,
        authCommand: ByteArray? = null
    ): AuthResult {
        val isoDep = IsoDep.get(tag)
            ?: return AuthResult.Error("Tag does not support ISO-DEP")

        return try {
            isoDep.connect()
            isoDep.timeout = 5000

            // SELECT AID command
            val selectCommand = byteArrayOf(
                0x00.toByte(), // CLA
                0xA4.toByte(), // INS - SELECT
                0x04.toByte(), // P1 - Select by DF name
                0x00.toByte(), // P2
                aid.size.toByte() // Lc
            ) + aid

            val selectResponse = isoDep.transceive(selectCommand)
            
            if (selectResponse.size < 2) {
                return AuthResult.Error("Invalid response from card")
            }

            val sw1 = selectResponse[selectResponse.size - 2]
            val sw2 = selectResponse[selectResponse.size - 1]

            if (sw1 != 0x90.toByte() || sw2 != 0x00.toByte()) {
                return AuthResult.Error("AID selection failed: ${String.format("%02X%02X", sw1, sw2)}")
            }

            // Send auth command if provided
            if (authCommand != null && authCommand.isNotEmpty()) {
                val authResponse = isoDep.transceive(authCommand)
                val data = mapOf(
                    "selectResponse" to selectResponse.toHexString(),
                    "authResponse" to authResponse.toHexString()
                )
                AuthResult.Success("Authentication complete", data)
            } else {
                val data = mapOf(
                    "selectResponse" to selectResponse.toHexString()
                )
                AuthResult.Success("AID selected successfully", data)
            }
        } catch (e: IOException) {
            AuthResult.Error("I/O Error: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        }
    }

    /**
     * Send raw APDU to ISO-DEP card
     */
    fun sendApduCommand(tag: Tag, command: ByteArray): AuthResult {
        val isoDep = IsoDep.get(tag)
            ?: return AuthResult.Error("Tag does not support ISO-DEP")

        return try {
            isoDep.connect()
            isoDep.timeout = 5000

            val response = isoDep.transceive(command)
            
            if (response.size < 2) {
                return AuthResult.Error("Invalid response")
            }

            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]
            val responseData = if (response.size > 2) {
                response.copyOfRange(0, response.size - 2)
            } else {
                ByteArray(0)
            }

            val data = mapOf(
                "response" to response.toHexString(),
                "data" to responseData.toHexString(),
                "sw1" to String.format("%02X", sw1),
                "sw2" to String.format("%02X", sw2)
            )

            if (sw1 == 0x90.toByte() && sw2 == 0x00.toByte()) {
                AuthResult.Success("Command successful", data)
            } else {
                AuthResult.Success("Command returned: ${String.format("%02X%02X", sw1, sw2)}", data)
            }
        } catch (e: IOException) {
            AuthResult.Error("I/O Error: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        }
    }

    private fun tryDefaultKeys(mifare: MifareClassic, sector: Int): Boolean {
        for (key in COMMON_KEYS) {
            try {
                if (mifare.authenticateSectorWithKeyA(sector, key)) return true
                if (mifare.authenticateSectorWithKeyB(sector, key)) return true
            } catch (_: Exception) {
                // Continue trying other keys
            }
        }

        return false
    }

    /**
     * Discover keys for all sectors using common key dictionary
     * Returns a map of sector -> working key pairs
     */
    fun discoverKeys(tag: Tag): AuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()
            
            val sectorCount = mifare.sectorCount
            val discoveredKeys = mutableMapOf<Int, MutableMap<String, String>>()
            var successCount = 0

            for (sector in 0 until sectorCount) {
                discoveredKeys[sector] = mutableMapOf()
                
                for ((keyName, key) in NAMED_KEYS) {
                    try {
                        if (mifare.authenticateSectorWithKeyA(sector, key)) {
                            discoveredKeys[sector]!!["KeyA"] = keyName
                            successCount++
                            break
                        }
                    } catch (_: Exception) {
                        // Continue
                    }
                }
                
                for ((keyName, key) in NAMED_KEYS) {
                    try {
                        if (mifare.authenticateSectorWithKeyB(sector, key)) {
                            discoveredKeys[sector]!!["KeyB"] = keyName
                            break
                        }
                    } catch (_: Exception) {
                        // Continue
                    }
                }
            }

            val data = mapOf(
                "totalSectors" to sectorCount,
                "successCount" to successCount,
                "discoveredKeys" to discoveredKeys
            )

            AuthResult.Success("Key discovery complete: $successCount/$sectorCount sectors accessible", data)
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }

    /**
     * Perform nested authentication attack simulation
     * This demonstrates the concept - actual implementation requires
     * low-level radio control not available on standard Android
     */
    fun nestedAuthenticationInfo(tag: Tag): AuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()
            
            val cardType = when (mifare.type) {
                MifareClassic.TYPE_CLASSIC -> "Mifare Classic (potentially vulnerable)"
                MifareClassic.TYPE_PLUS -> "Mifare Plus (improved security)"
                MifareClassic.TYPE_PRO -> "Mifare Pro"
                else -> "Unknown type"
            }

            val info = buildString {
                appendLine("Card Type: $cardType")
                appendLine("Sector Count: ${mifare.sectorCount}")
                appendLine("Size: ${mifare.size} bytes")
                appendLine()
                appendLine("Security Note:")
                appendLine("Nested authentication attacks require specialized hardware")
                appendLine("that can capture timing information from the PRNG.")
                appendLine("Standard Android NFC hardware cannot perform this.")
                appendLine()
                appendLine("Mifare Classic uses CRYPTO1 which has known weaknesses.")
                appendLine("Mifare Plus and DESFire use AES encryption.")
            }

            val data = mapOf(
                "cardType" to cardType,
                "sectorCount" to mifare.sectorCount,
                "size" to mifare.size,
                "info" to info
            )

            AuthResult.Success("Card analyzed", data)
        } catch (e: Exception) {
            AuthResult.Error("Error: ${e.message}")
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }

    enum class KeyType {
        KEY_A, KEY_B
    }

    companion object {
        // Common Mifare Classic keys for user's own cards
        val KEY_DEFAULT = MifareClassic.KEY_DEFAULT
        val KEY_MAD = MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY
        val KEY_NFC_FORUM = MifareClassic.KEY_NFC_FORUM

        // Extended list of common keys
        val COMMON_KEYS = arrayOf(
            MifareClassic.KEY_DEFAULT, // FFFFFFFFFFFF
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY, // A0A1A2A3A4A5
            MifareClassic.KEY_NFC_FORUM, // D3F7D3F7D3F7
            byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
            byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
            byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
            // Common transit/access card keys
            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00), // All zeros
            byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
            byteArrayOf(0x4D.toByte(), 0x3A.toByte(), 0x99.toByte(), 0xC3.toByte(), 0x51.toByte(), 0xDD.toByte()),
            byteArrayOf(0x1A.toByte(), 0x98.toByte(), 0x2C.toByte(), 0x7E.toByte(), 0x45.toByte(), 0x9A.toByte()),
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
            byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55),
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
            byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte()),
            byteArrayOf(0x71, 0x4C.toByte(), 0x5C.toByte(), 0x88.toByte(), 0x6E.toByte(), 0x97.toByte()),
            byteArrayOf(0x58.toByte(), 0x7E.toByte(), 0xE5.toByte(), 0xF9.toByte(), 0x35.toByte(), 0x0F.toByte()),
            byteArrayOf(0xA6.toByte(), 0x22.toByte(), 0x6C.toByte(), 0xE8.toByte(), 0xFC.toByte(), 0xAD.toByte())
        )

        // Named keys for reporting
        val NAMED_KEYS = mapOf(
            "Default (FFFFFFFFFFFF)" to MifareClassic.KEY_DEFAULT,
            "MAD (A0A1A2A3A4A5)" to MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
            "NFC Forum (D3F7D3F7D3F7)" to MifareClassic.KEY_NFC_FORUM,
            "All Zeros" to byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            "Sequential (010203040506)" to byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
            "Common 1" to byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
            "Common 2" to byteArrayOf(0x4D.toByte(), 0x3A.toByte(), 0x99.toByte(), 0xC3.toByte(), 0x51.toByte(), 0xDD.toByte())
        )

        fun hexStringToByteArray(hex: String): ByteArray {
            val cleaned = hex.replace(" ", "").replace(":", "")
            return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
