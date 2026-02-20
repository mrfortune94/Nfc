package com.nfc.reader.hce

/**
 * Mifare Classic UID Emulator
 * 
 * Handles emulation of Mifare Classic card UID via Host Card Emulation (HCE).
 * Since Android HCE operates at the ISO 14443-4 (ISO-DEP) layer, full Mifare Classic
 * emulation (including CRYPTO1 authentication) is not possible without dedicated hardware.
 * 
 * This emulator provides UID-level emulation by responding to APDU commands that
 * query card identity information (UID, ATQA, SAK), allowing the device to present
 * itself with a Mifare Classic card's UID to compatible readers.
 * 
 * IMPORTANT: Only use with your own cards for educational purposes.
 */
class MifareClassicEmulator(
    private val uid: String,
    private val atqa: String? = null,
    private val sak: String? = null
) {
    companion object {
        // Mifare Classic default ATQA values
        const val MIFARE_CLASSIC_1K_ATQA = "0004"
        const val MIFARE_CLASSIC_4K_ATQA = "0002"

        // Mifare Classic SAK values
        const val MIFARE_CLASSIC_1K_SAK = "08"
        const val MIFARE_CLASSIC_4K_SAK = "18"

        // Status words
        private const val STATUS_SUCCESS = "9000"

        // Valid Mifare Classic UID byte lengths
        private val VALID_UID_LENGTHS = setOf(4, 7, 10)

        /**
         * Validate that a UID string is a valid Mifare Classic UID.
         * Mifare Classic supports 4-byte (single size), 7-byte (double size),
         * or 10-byte (triple size) UIDs.
         */
        fun isValidMifareUid(uid: String): Boolean {
            val cleaned = uid.replace(" ", "").replace(":", "")
            if (cleaned.isEmpty() || cleaned.length % 2 != 0) return false
            val byteLength = cleaned.length / 2
            if (byteLength !in VALID_UID_LENGTHS) return false
            return cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        }

        /**
         * Determine the Mifare Classic card type from ATQA/SAK or memory size.
         */
        fun detectCardVariant(atqa: String?, sak: String?, memorySize: Int?): String {
            return when {
                sak?.equals(MIFARE_CLASSIC_4K_SAK, ignoreCase = true) == true -> "Mifare Classic 4K"
                sak?.equals(MIFARE_CLASSIC_1K_SAK, ignoreCase = true) == true -> "Mifare Classic 1K"
                memorySize != null && memorySize > 1024 -> "Mifare Classic 4K"
                memorySize != null && memorySize > 0 -> "Mifare Classic 1K"
                else -> "Mifare Classic"
            }
        }

        private fun hexToByteArray(hex: String): ByteArray {
            val cleaned = hex.replace(" ", "").replace(":", "")
            return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    /**
     * Build the UID response bytes with success status word.
     */
    fun getUidResponse(): ByteArray {
        return hexToByteArray(uid) + hexToByteArray(STATUS_SUCCESS)
    }

    /**
     * Build the ATQA response bytes with success status word.
     */
    fun getAtqaResponse(): ByteArray {
        val atqaValue = atqa ?: MIFARE_CLASSIC_1K_ATQA
        return hexToByteArray(atqaValue) + hexToByteArray(STATUS_SUCCESS)
    }

    /**
     * Build the SAK response bytes with success status word.
     */
    fun getSakResponse(): ByteArray {
        val sakValue = sak ?: MIFARE_CLASSIC_1K_SAK
        return hexToByteArray(sakValue) + hexToByteArray(STATUS_SUCCESS)
    }

    /**
     * Build a complete card identity response containing UID, ATQA, and SAK
     * encoded as a TLV structure with success status word.
     * 
     * TLV structure:
     * - Tag C0: UID bytes
     * - Tag C1: ATQA bytes
     * - Tag C2: SAK byte
     */
    fun getCardIdentityResponse(): ByteArray {
        val uidBytes = hexToByteArray(uid)
        val atqaBytes = hexToByteArray(atqa ?: MIFARE_CLASSIC_1K_ATQA)
        val sakBytes = hexToByteArray(sak ?: MIFARE_CLASSIC_1K_SAK)

        // Build TLV: C0 [len] [uid] C1 [len] [atqa] C2 [len] [sak]
        val tlv = byteArrayOf(0xC0.toByte(), uidBytes.size.toByte()) + uidBytes +
                  byteArrayOf(0xC1.toByte(), atqaBytes.size.toByte()) + atqaBytes +
                  byteArrayOf(0xC2.toByte(), sakBytes.size.toByte()) + sakBytes

        return tlv + hexToByteArray(STATUS_SUCCESS)
    }

    /**
     * Handle a Mifare Classic-specific APDU command.
     * Returns the appropriate response or null if the command is not recognized.
     *
     * Custom command mapping for UID emulation:
     * - GET DATA (INS=CA) P1=00, P2=00 → UID
     * - GET DATA (INS=CA) P1=00, P2=01 → ATQA
     * - GET DATA (INS=CA) P1=00, P2=02 → SAK
     * - GET DATA (INS=CA) P1=00, P2=03 → Full card identity (UID + ATQA + SAK)
     */
    fun handleCommand(commandApdu: ByteArray): ByteArray? {
        if (commandApdu.size < 4) return null

        val ins = commandApdu[1]
        val p1 = commandApdu[2]
        val p2 = commandApdu[3]

        // Only handle GET DATA commands
        if (ins != 0xCA.toByte() || p1 != 0x00.toByte()) return null

        return when (p2) {
            0x00.toByte() -> getUidResponse()
            0x01.toByte() -> getAtqaResponse()
            0x02.toByte() -> getSakResponse()
            0x03.toByte() -> getCardIdentityResponse()
            else -> null
        }
    }
}
