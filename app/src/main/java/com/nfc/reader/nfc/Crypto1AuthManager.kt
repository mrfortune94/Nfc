package com.nfc.reader.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import com.nfc.reader.utils.toHexString
import java.io.IOException

/**
 * Full sector authentication manager using CRYPTO1 for Mifare Classic tags.
 *
 * Provides comprehensive CRYPTO1 authentication capabilities including:
 * - Per-sector Key A and Key B authentication
 * - Access bits parsing and interpretation
 * - Sector trailer analysis
 * - Full card authentication mapping
 * - Detailed authentication result reporting
 *
 * IMPORTANT: This class is designed for educational/research purposes
 * and should only be used with tags that the user owns.
 */
class Crypto1AuthManager {

    /**
     * Result of authenticating a single sector with both Key A and Key B.
     */
    data class SectorAuthResult(
        val sector: Int,
        val keyASuccess: Boolean,
        val keyBSuccess: Boolean,
        val keyAUsed: ByteArray? = null,
        val keyBUsed: ByteArray? = null,
        val blocks: List<String> = emptyList(),
        val accessBits: AccessBits? = null,
        val error: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as SectorAuthResult
            if (sector != other.sector) return false
            if (keyASuccess != other.keyASuccess) return false
            if (keyBSuccess != other.keyBSuccess) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sector
            result = 31 * result + keyASuccess.hashCode()
            result = 31 * result + keyBSuccess.hashCode()
            return result
        }
    }

    /**
     * Parsed access bits from a Mifare Classic sector trailer.
     * The sector trailer (last block of each sector) contains access conditions
     * encoded in bytes 6–9 of the 16-byte block.
     */
    data class AccessBits(
        val rawBytes: ByteArray,
        val blockAccessConditions: List<BlockAccess>,
        val isValid: Boolean,
        val validationError: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AccessBits
            return rawBytes.contentEquals(other.rawBytes)
        }

        override fun hashCode(): Int = rawBytes.contentHashCode()
    }

    /**
     * Access conditions for a single block within a sector.
     */
    data class BlockAccess(
        val blockIndex: Int,
        val c1: Int,
        val c2: Int,
        val c3: Int,
        val description: String
    )

    /**
     * Result of a full card authentication map operation.
     */
    data class CardAuthMap(
        val cardType: String,
        val uid: String,
        val sectorCount: Int,
        val totalSize: Int,
        val sectorResults: List<SectorAuthResult>,
        val accessibleSectors: Int,
        val totalKeysFound: Int
    )

    /**
     * Authenticate a single sector with both Key A and Key B, trying all provided keys.
     * Returns detailed result including which keys worked and the sector data.
     */
    fun authenticateSectorFull(
        tag: Tag,
        sector: Int,
        keys: List<ByteArray>
    ): SectorAuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return SectorAuthResult(
                sector = sector,
                keyASuccess = false,
                keyBSuccess = false,
                error = "Not a Mifare Classic tag"
            )

        return try {
            mifare.connect()
            authenticateSectorInternal(mifare, sector, keys)
        } catch (e: IOException) {
            SectorAuthResult(
                sector = sector,
                keyASuccess = false,
                keyBSuccess = false,
                error = "I/O Error: ${e.message}"
            )
        } catch (e: Exception) {
            SectorAuthResult(
                sector = sector,
                keyASuccess = false,
                keyBSuccess = false,
                error = "Error: ${e.message}"
            )
        } finally {
            try {
                if (mifare.isConnected) mifare.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Perform full card authentication mapping.
     * Tries all provided keys on every sector with both Key A and Key B.
     * Returns a comprehensive map of the card's authentication state.
     */
    fun performFullCardAuth(
        tag: Tag,
        keys: List<ByteArray> = ALL_KNOWN_KEYS
    ): ProtectedTagHandler.AuthResult {
        val mifare = MifareClassic.get(tag)
            ?: return ProtectedTagHandler.AuthResult.Error("Not a Mifare Classic tag")

        return try {
            mifare.connect()

            val uid = tag.id.toHexString()
            val sectorCount = mifare.sectorCount
            val cardType = getCardTypeName(mifare)
            val sectorResults = mutableListOf<SectorAuthResult>()
            var totalKeysFound = 0

            for (sector in 0 until sectorCount) {
                val result = authenticateSectorInternal(mifare, sector, keys)
                sectorResults.add(result)
                if (result.keyASuccess) totalKeysFound++
                if (result.keyBSuccess) totalKeysFound++
            }

            val accessibleSectors = sectorResults.count { it.keyASuccess || it.keyBSuccess }

            val cardMap = CardAuthMap(
                cardType = cardType,
                uid = uid,
                sectorCount = sectorCount,
                totalSize = mifare.size,
                sectorResults = sectorResults,
                accessibleSectors = accessibleSectors,
                totalKeysFound = totalKeysFound
            )

            val report = buildFullAuthReport(cardMap)

            val data = mapOf(
                "uid" to uid,
                "cardType" to cardType,
                "sectorCount" to sectorCount,
                "size" to mifare.size,
                "accessibleSectors" to accessibleSectors,
                "totalKeysFound" to totalKeysFound,
                "sectorResults" to sectorResults.map { sr ->
                    mapOf(
                        "sector" to sr.sector,
                        "keyASuccess" to sr.keyASuccess,
                        "keyBSuccess" to sr.keyBSuccess,
                        "keyA" to (sr.keyAUsed?.toHexString() ?: ""),
                        "keyB" to (sr.keyBUsed?.toHexString() ?: ""),
                        "blocks" to sr.blocks,
                        "accessBits" to (sr.accessBits?.let { ab ->
                            mapOf(
                                "valid" to ab.isValid,
                                "raw" to ab.rawBytes.toHexString(),
                                "conditions" to ab.blockAccessConditions.map { ba ->
                                    mapOf(
                                        "block" to ba.blockIndex,
                                        "c1" to ba.c1,
                                        "c2" to ba.c2,
                                        "c3" to ba.c3,
                                        "description" to ba.description
                                    )
                                }
                            )
                        } ?: emptyMap<String, Any>())
                    )
                },
                "report" to report
            )

            ProtectedTagHandler.AuthResult.Success(
                "Full CRYPTO1 auth: $accessibleSectors/$sectorCount sectors accessible, $totalKeysFound keys found",
                data
            )
        } catch (e: Exception) {
            ProtectedTagHandler.AuthResult.Error("Error: ${e.message}")
        } finally {
            try {
                if (mifare.isConnected) mifare.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Authenticate sector and read its data, including access bits from the sector trailer.
     * Uses an already-connected MifareClassic instance.
     */
    private fun authenticateSectorInternal(
        mifare: MifareClassic,
        sector: Int,
        keys: List<ByteArray>
    ): SectorAuthResult {
        var keyAUsed: ByteArray? = null
        var keyBUsed: ByteArray? = null
        var keyASuccess = false
        var keyBSuccess = false
        val blocks = mutableListOf<String>()
        var accessBits: AccessBits? = null

        // Try Key A with all keys
        for (key in keys) {
            try {
                if (mifare.authenticateSectorWithKeyA(sector, key)) {
                    keyASuccess = true
                    keyAUsed = key.copyOf()
                    break
                }
            } catch (_: Exception) {}
        }

        // Try Key B with all keys
        for (key in keys) {
            try {
                if (mifare.authenticateSectorWithKeyB(sector, key)) {
                    keyBSuccess = true
                    keyBUsed = key.copyOf()
                    break
                }
            } catch (_: Exception) {}
        }

        // Read block data if at least one key worked
        if (keyASuccess || keyBSuccess) {
            // Re-authenticate with working key to read
            val reAuth = if (keyASuccess) {
                try {
                    mifare.authenticateSectorWithKeyA(sector, keyAUsed!!)
                } catch (_: Exception) { false }
            } else {
                try {
                    mifare.authenticateSectorWithKeyB(sector, keyBUsed!!)
                } catch (_: Exception) { false }
            }

            if (reAuth) {
                val blockCount = mifare.getBlockCountInSector(sector)
                val firstBlock = mifare.sectorToBlock(sector)

                for (block in 0 until blockCount) {
                    try {
                        val data = mifare.readBlock(firstBlock + block)
                        blocks.add(data.toHexString())
                    } catch (_: IOException) {
                        blocks.add("READ_ERROR")
                    }
                }

                // Parse access bits from the sector trailer (last block)
                if (blocks.isNotEmpty() && blocks.last() != "READ_ERROR") {
                    val trailerHex = blocks.last()
                    if (trailerHex.length >= 24) {
                        val trailerBytes = ProtectedTagHandler.hexStringToByteArray(trailerHex)
                        accessBits = parseAccessBits(trailerBytes, mifare.getBlockCountInSector(sector))
                    }
                }
            }
        }

        return SectorAuthResult(
            sector = sector,
            keyASuccess = keyASuccess,
            keyBSuccess = keyBSuccess,
            keyAUsed = keyAUsed,
            keyBUsed = keyBUsed,
            blocks = blocks,
            accessBits = accessBits
        )
    }

    /**
     * Build a detailed human-readable report from a full card authentication map.
     */
    private fun buildFullAuthReport(cardMap: CardAuthMap): String = buildString {
        appendLine("╔══════════════════════════════════════════╗")
        appendLine("║   CRYPTO1 Full Sector Authentication     ║")
        appendLine("╚══════════════════════════════════════════╝")
        appendLine()
        appendLine("Card: ${cardMap.cardType}")
        appendLine("UID: ${cardMap.uid}")
        appendLine("Size: ${cardMap.totalSize} bytes (${cardMap.sectorCount} sectors)")
        appendLine("Accessible: ${cardMap.accessibleSectors}/${cardMap.sectorCount} sectors")
        appendLine("Keys Found: ${cardMap.totalKeysFound}")
        appendLine()

        for (sr in cardMap.sectorResults) {
            appendLine("━━━ Sector ${sr.sector} ━━━")

            val keyAStatus = if (sr.keyASuccess)
                "✓ Key A: ${sr.keyAUsed?.toHexString()}" else "✗ Key A: not found"
            val keyBStatus = if (sr.keyBSuccess)
                "✓ Key B: ${sr.keyBUsed?.toHexString()}" else "✗ Key B: not found"
            appendLine("  $keyAStatus")
            appendLine("  $keyBStatus")

            if (sr.blocks.isNotEmpty()) {
                sr.blocks.forEachIndexed { index, block ->
                    val label = if (index == sr.blocks.size - 1) "Trailer" else "Data   "
                    appendLine("  Block $index [$label]: $block")
                }
            }

            sr.accessBits?.let { ab ->
                if (ab.isValid) {
                    appendLine("  Access Bits (${ab.rawBytes.toHexString()}):")
                    ab.blockAccessConditions.forEach { ba ->
                        appendLine("    Block ${ba.blockIndex}: C1=${ba.c1} C2=${ba.c2} C3=${ba.c3} → ${ba.description}")
                    }
                } else {
                    appendLine("  Access Bits: INVALID (${ab.validationError})")
                }
            }
            appendLine()
        }

        appendLine("━━━ CRYPTO1 Security Analysis ━━━")
        appendLine("Mifare Classic uses the CRYPTO1 stream cipher for authentication.")
        appendLine("CRYPTO1 operates with a 48-bit key and has known cryptographic weaknesses.")
        appendLine("Each sector has independent Key A and Key B, with access bits controlling permissions.")
        appendLine("The sector trailer (last block) stores Key A (write-only), access bits, and Key B.")
    }

    companion object {
        /**
         * Parse access bits from a sector trailer block (16 bytes).
         * Access conditions are stored in bytes 6–9 of the sector trailer.
         *
         * Byte 6: ~C2B3 ~C2B2 ~C2B1 ~C2B0 | ~C1B3 ~C1B2 ~C1B1 ~C1B0
         * Byte 7:  C1B3  C1B2  C1B1  C1B0 | ~C3B3 ~C3B2 ~C3B1 ~C3B0
         * Byte 8:  C3B3  C3B2  C3B1  C3B0 |  C2B3  C2B2  C2B1  C2B0
         * Byte 9: User data byte
         *
         * @param trailerBlock the full 16-byte sector trailer
         * @param blockCount number of blocks in the sector (4 for standard, 16 for large sectors)
         */
        fun parseAccessBits(trailerBlock: ByteArray, blockCount: Int = 4): AccessBits {
            if (trailerBlock.size < 16) {
                return AccessBits(
                    rawBytes = ByteArray(0),
                    blockAccessConditions = emptyList(),
                    isValid = false,
                    validationError = "Trailer block too short (${trailerBlock.size} bytes, need 16)"
                )
            }

            val byte6 = trailerBlock[6].toInt() and 0xFF
            val byte7 = trailerBlock[7].toInt() and 0xFF
            val byte8 = trailerBlock[8].toInt() and 0xFF

            // Validate: inverted bits must be complement of non-inverted bits
            val c1Inv = byte6 and 0x0F         // low nibble of byte6 = ~C1
            val c1 = (byte7 shr 4) and 0x0F    // high nibble of byte7 = C1
            val c2Inv = (byte6 shr 4) and 0x0F // high nibble of byte6 = ~C2
            val c2 = byte8 and 0x0F             // low nibble of byte8 = C2
            val c3Inv = byte7 and 0x0F          // low nibble of byte7 = ~C3
            val c3 = (byte8 shr 4) and 0x0F    // high nibble of byte8 = C3

            val valid = (c1Inv == c1.inv() and 0x0F) &&
                    (c2Inv == c2.inv() and 0x0F) &&
                    (c3Inv == c3.inv() and 0x0F)

            val conditions = mutableListOf<BlockAccess>()

            // For standard 4-block sectors, bits 0-2 are for data blocks, bit 3 is for trailer
            // For 16-block sectors, the mapping is different (groups of 5 blocks)
            val numEntries = if (blockCount <= 4) 4 else 4

            for (i in 0 until numEntries) {
                val c1Bit = (c1 shr i) and 0x01
                val c2Bit = (c2 shr i) and 0x01
                val c3Bit = (c3 shr i) and 0x01

                val description = if (i < numEntries - 1) {
                    describeDataBlockAccess(c1Bit, c2Bit, c3Bit)
                } else {
                    describeSectorTrailerAccess(c1Bit, c2Bit, c3Bit)
                }

                conditions.add(
                    BlockAccess(
                        blockIndex = i,
                        c1 = c1Bit,
                        c2 = c2Bit,
                        c3 = c3Bit,
                        description = description
                    )
                )
            }

            return AccessBits(
                rawBytes = byteArrayOf(trailerBlock[6], trailerBlock[7], trailerBlock[8], trailerBlock[9]),
                blockAccessConditions = conditions,
                isValid = valid,
                validationError = if (!valid) "Access bit parity check failed" else null
            )
        }

        /**
         * Describe access conditions for a data block based on C1, C2, C3 bits.
         * Reference: NXP MIFARE Classic EV1 data sheet, Table 7.
         */
        fun describeDataBlockAccess(c1: Int, c2: Int, c3: Int): String {
            return when {
                c1 == 0 && c2 == 0 && c3 == 0 -> "Read/Write: Key A|B; Increment: Key A|B; Decrement/Transfer/Restore: Key A|B"
                c1 == 0 && c2 == 1 && c3 == 0 -> "Read: Key A|B; Write: never; Increment: never; Decrement/Transfer/Restore: never"
                c1 == 1 && c2 == 0 && c3 == 0 -> "Read: Key A|B; Write: Key B; Increment: never; Decrement/Transfer/Restore: never"
                c1 == 1 && c2 == 1 && c3 == 0 -> "Read: Key A|B; Write: Key B; Increment: Key B; Decrement/Transfer/Restore: Key A|B"
                c1 == 0 && c2 == 0 && c3 == 1 -> "Read: Key A|B; Write: never; Increment: never; Decrement/Transfer/Restore: Key A|B"
                c1 == 0 && c2 == 1 && c3 == 1 -> "Read: Key B; Write: Key B; Increment: never; Decrement/Transfer/Restore: never"
                c1 == 1 && c2 == 0 && c3 == 1 -> "Read: Key B; Write: never; Increment: never; Decrement/Transfer/Restore: never"
                c1 == 1 && c2 == 1 && c3 == 1 -> "Read: never; Write: never; Increment: never; Decrement/Transfer/Restore: never"
                else -> "Unknown access condition"
            }
        }

        /**
         * Describe access conditions for the sector trailer block based on C1, C2, C3 bits.
         * Reference: NXP MIFARE Classic EV1 data sheet, Table 8.
         */
        fun describeSectorTrailerAccess(c1: Int, c2: Int, c3: Int): String {
            return when {
                c1 == 0 && c2 == 0 && c3 == 0 -> "KeyA: write Key A; Access: read Key A, write never; KeyB: read/write Key A"
                c1 == 0 && c2 == 1 && c3 == 0 -> "KeyA: write never; Access: read Key A, write never; KeyB: read/write never (transport)"
                c1 == 1 && c2 == 0 && c3 == 0 -> "KeyA: write Key B; Access: read Key A|B, write never; KeyB: write never"
                c1 == 1 && c2 == 1 && c3 == 0 -> "KeyA: write never; Access: read Key A|B, write never; KeyB: write never"
                c1 == 0 && c2 == 0 && c3 == 1 -> "KeyA: write Key A; Access: read Key A, write Key A; KeyB: read/write Key A"
                c1 == 0 && c2 == 1 && c3 == 1 -> "KeyA: write Key B; Access: read Key A|B, write Key B; KeyB: write never"
                c1 == 1 && c2 == 0 && c3 == 1 -> "KeyA: write never; Access: read Key A|B, write Key B; KeyB: write never"
                c1 == 1 && c2 == 1 && c3 == 1 -> "KeyA: write never; Access: read Key A|B, write never; KeyB: write never"
                else -> "Unknown trailer access condition"
            }
        }

        /**
         * Get a human-readable card type name from MifareClassic.
         */
        fun getCardTypeName(mifare: MifareClassic): String {
            return when (mifare.type) {
                MifareClassic.TYPE_CLASSIC -> when (mifare.size) {
                    320 -> "Mifare Classic Mini (320 bytes, 5 sectors)"
                    1024 -> "Mifare Classic 1K (1024 bytes, 16 sectors)"
                    2048 -> "Mifare Classic 2K (2048 bytes, 32 sectors)"
                    4096 -> "Mifare Classic 4K (4096 bytes, 40 sectors)"
                    else -> "Mifare Classic (${mifare.size} bytes)"
                }
                MifareClassic.TYPE_PLUS -> "Mifare Plus (AES security)"
                MifareClassic.TYPE_PRO -> "Mifare Pro"
                else -> "Unknown Mifare type"
            }
        }

        /**
         * Comprehensive list of known Mifare Classic keys for research.
         * Includes factory defaults, well-known application keys, and common deployment keys.
         */
        val ALL_KNOWN_KEYS: List<ByteArray> = listOf(
            // Factory defaults
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
            // MAD key
            byteArrayOf(0xA0.toByte(), 0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte(), 0xA4.toByte(), 0xA5.toByte()),
            // NFC Forum key
            byteArrayOf(0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte(), 0xD3.toByte(), 0xF7.toByte()),
            // Common deployment keys
            byteArrayOf(0xB0.toByte(), 0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0xB4.toByte(), 0xB5.toByte()),
            byteArrayOf(0xA0.toByte(), 0xB0.toByte(), 0xC0.toByte(), 0xD0.toByte(), 0xE0.toByte(), 0xF0.toByte()),
            byteArrayOf(0x4D.toByte(), 0x3A.toByte(), 0x99.toByte(), 0xC3.toByte(), 0x51.toByte(), 0xDD.toByte()),
            byteArrayOf(0x1A.toByte(), 0x98.toByte(), 0x2C.toByte(), 0x7E.toByte(), 0x45.toByte(), 0x9A.toByte()),
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()),
            byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55),
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06),
            byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte()),
            byteArrayOf(0x71, 0x4C.toByte(), 0x5C.toByte(), 0x88.toByte(), 0x6E.toByte(), 0x97.toByte()),
            byteArrayOf(0x58.toByte(), 0x7E.toByte(), 0xE5.toByte(), 0xF9.toByte(), 0x35.toByte(), 0x0F.toByte()),
            byteArrayOf(0xA6.toByte(), 0x22.toByte(), 0x6C.toByte(), 0xE8.toByte(), 0xFC.toByte(), 0xAD.toByte()),
            // Additional common keys
            byteArrayOf(0x01, 0x01, 0x01, 0x01, 0x01, 0x01),
            byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x12, 0x34, 0x56),
            byteArrayOf(0xB1.toByte(), 0x27.toByte(), 0xC6.toByte(), 0xF8.toByte(), 0xA1.toByte(), 0xD0.toByte()),
            byteArrayOf(0x48.toByte(), 0xFF.toByte(), 0xE7.toByte(), 0x14.toByte(), 0x9B.toByte(), 0x81.toByte()),
            byteArrayOf(0xA6.toByte(), 0x4E.toByte(), 0x94.toByte(), 0x21.toByte(), 0x16.toByte(), 0xE5.toByte())
        )
    }
}
