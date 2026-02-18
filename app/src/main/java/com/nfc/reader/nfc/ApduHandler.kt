package com.nfc.reader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.nfc.reader.utils.toHexString
import java.io.IOException

/**
 * APDU (Application Protocol Data Unit) Handler
 * Supports ISO/IEC 7816-4 and EMV contactless communication over ISO 14443-4 / ISO-DEP
 * 
 * Provides:
 * - Standard APDU command construction and parsing
 * - EMV contactless: PPSE/PSE selection, GPO, READ RECORD
 * - Response chaining (SW 61XX / 6CXX handling)
 * - Human-readable EMV status word descriptions
 */
class ApduHandler {
    
    companion object {
        /**
         * EMV status word descriptions for educational/debugging purposes
         */
        val STATUS_DESCRIPTIONS = mapOf(
            0x9000 to "Success",
            0x6100 to "More data available (use GET RESPONSE)",
            0x6283 to "Selected file invalidated/blocked",
            0x6300 to "Authentication failed",
            0x6400 to "Execution error",
            0x6581 to "Memory failure",
            0x6700 to "Wrong length",
            0x6882 to "Secure messaging not supported",
            0x6981 to "Command incompatible with file structure",
            0x6982 to "Security status not satisfied",
            0x6983 to "Authentication method blocked",
            0x6984 to "Referenced data invalidated",
            0x6985 to "Conditions of use not satisfied",
            0x6986 to "Command not allowed (no current EF)",
            0x6A80 to "Incorrect parameters in data field",
            0x6A81 to "Function not supported",
            0x6A82 to "File or application not found",
            0x6A83 to "Record not found",
            0x6A86 to "Incorrect parameters P1-P2",
            0x6A88 to "Referenced data not found",
            0x6B00 to "Wrong parameters P1-P2",
            0x6C00 to "Wrong Le field (SW2 indicates correct Le)",
            0x6D00 to "Instruction code not supported/invalid",
            0x6E00 to "Class not supported",
            0x6F00 to "No precise diagnosis"
        )

        /**
         * Get human-readable description for an EMV status word
         */
        fun describeStatusWord(sw: Int): String {
            STATUS_DESCRIPTIONS[sw]?.let { return it }
            // Check SW1-based ranges
            val sw1 = (sw shr 8) and 0xFF
            return when (sw1) {
                0x61 -> "More data available (${sw and 0xFF} bytes)"
                0x62 -> "Warning: state of non-volatile memory unchanged"
                0x63 -> "Warning: state of non-volatile memory changed"
                0x64 -> "Execution error: state of non-volatile memory unchanged"
                0x65 -> "Execution error: state of non-volatile memory changed"
                0x67 -> "Wrong length"
                0x68 -> "Functions in CLA not supported"
                0x69 -> "Command not allowed"
                0x6A -> "Wrong parameters P1-P2"
                0x6C -> "Wrong Le (correct Le = ${sw and 0xFF})"
                0x90 -> "Success"
                else -> "Unknown status: ${String.format("%04X", sw)}"
            }
        }
    }
    
    data class ApduCommand(
        val cla: Byte,  // Class byte
        val ins: Byte,  // Instruction byte
        val p1: Byte,   // Parameter 1
        val p2: Byte,   // Parameter 2
        val data: ByteArray? = null,
        val le: Int? = null  // Expected response length
    ) {
        fun toByteArray(): ByteArray {
            val hasData = data != null && data.isNotEmpty()
            val hasLe = le != null
            
            val size = 4 + (if (hasData) 1 + data!!.size else 0) + (if (hasLe) 1 else 0)
            val command = ByteArray(size)
            
            var index = 0
            command[index++] = cla
            command[index++] = ins
            command[index++] = p1
            command[index++] = p2
            
            if (hasData) {
                command[index++] = data!!.size.toByte()
                data.copyInto(command, index)
                index += data.size
            }
            
            if (hasLe) {
                command[index] = le!!.toByte()
            }
            
            return command
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ApduCommand
            
            if (cla != other.cla) return false
            if (ins != other.ins) return false
            if (p1 != other.p1) return false
            if (p2 != other.p2) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false
            if (le != other.le) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = cla.toInt()
            result = 31 * result + ins
            result = 31 * result + p1
            result = 31 * result + p2
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + (le ?: 0)
            return result
        }
    }
    
    data class ApduResponse(
        val data: ByteArray,
        val sw1: Byte,
        val sw2: Byte
    ) {
        val statusWord: Int get() = ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)
        val isSuccess: Boolean get() = sw1 == 0x90.toByte() && sw2 == 0x00.toByte()
        val hasMoreData: Boolean get() = (sw1.toInt() and 0xFF) == 0x61
        val remainingBytes: Int get() = if (hasMoreData) sw2.toInt() and 0xFF else 0
        val isWrongLe: Boolean get() = (sw1.toInt() and 0xFF) == 0x6C
        val correctLe: Int get() = if (isWrongLe) sw2.toInt() and 0xFF else 0
        val statusDescription: String get() = describeStatusWord(statusWord)
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ApduResponse
            
            if (!data.contentEquals(other.data)) return false
            if (sw1 != other.sw1) return false
            if (sw2 != other.sw2) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + sw1
            result = 31 * result + sw2
            return result
        }
        
        override fun toString(): String {
            return "Data: ${data.toHexString()}, SW: ${String.format("%02X%02X", sw1, sw2)} ($statusDescription)"
        }
    }
    
    /**
     * Send APDU command to ISO-DEP tag (ISO 14443-4) with response chaining support
     */
    fun sendApdu(tag: Tag, command: ApduCommand): Result<ApduResponse> {
        val isoDep = IsoDep.get(tag) ?: return Result.failure(
            IllegalArgumentException("Tag does not support ISO-DEP")
        )
        
        return try {
            isoDep.connect()
            val response = transceiveWithChaining(isoDep, command.toByteArray())
            isoDep.close()
            
            if (response.size < 2) {
                return Result.failure(IOException("Invalid response length: ${response.size}"))
            }
            
            val data = response.copyOfRange(0, response.size - 2)
            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]
            
            Result.success(ApduResponse(data, sw1, sw2))
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        }
    }
    
    /**
     * Send raw APDU bytes with response chaining support
     */
    fun sendRawApdu(tag: Tag, commandBytes: ByteArray): Result<ApduResponse> {
        val isoDep = IsoDep.get(tag) ?: return Result.failure(
            IllegalArgumentException("Tag does not support ISO-DEP")
        )
        
        return try {
            isoDep.connect()
            val response = transceiveWithChaining(isoDep, commandBytes)
            isoDep.close()
            
            if (response.size < 2) {
                return Result.failure(IOException("Invalid response length"))
            }
            
            val data = response.copyOfRange(0, response.size - 2)
            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]
            
            Result.success(ApduResponse(data, sw1, sw2))
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        }
    }
    
    /**
     * Transceive with EMV response chaining:
     * - SW 61XX: issue GET RESPONSE to retrieve remaining data
     * - SW 6CXX: re-send with corrected Le from SW2
     * Note: Le correction assumes Le is the last byte, which holds for
     * all EMV commands constructed by this handler (Case 2/4 APDUs).
     */
    private fun transceiveWithChaining(isoDep: IsoDep, command: ByteArray): ByteArray {
        var response = isoDep.transceive(command)
        
        while (response.size >= 2) {
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            
            if (sw1 == 0x61) {
                // GET RESPONSE to fetch remaining data
                val getResponse = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
                val next = isoDep.transceive(getResponse)
                response = response.copyOfRange(0, response.size - 2) + next
            } else if (sw1 == 0x6C) {
                // Wrong Le: re-send with correct Le
                val corrected = command.copyOf()
                corrected[corrected.size - 1] = sw2.toByte()
                response = isoDep.transceive(corrected)
            } else {
                break
            }
        }
        
        return response
    }
    
    /**
     * SELECT command (ISO 7816-4)
     */
    fun selectApplication(tag: Tag, aid: ByteArray): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0xA4.toByte(),  // SELECT
            p1 = 0x04,             // Select by DF name
            p2 = 0x00,
            data = aid,
            le = 256
        )
        return sendApdu(tag, command)
    }
    
    /**
     * READ BINARY command (ISO 7816-4)
     */
    fun readBinary(tag: Tag, offset: Int, length: Int): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0xB0.toByte(),  // READ BINARY
            p1 = (offset shr 8).toByte(),
            p2 = (offset and 0xFF).toByte(),
            le = length
        )
        return sendApdu(tag, command)
    }
    
    /**
     * GET DATA command (ISO 7816-4)
     */
    fun getData(tag: Tag, p1: Byte, p2: Byte): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0xCA.toByte(),  // GET DATA
            p1 = p1,
            p2 = p2,
            le = 256
        )
        return sendApdu(tag, command)
    }
    
    /**
     * EMV: Read Proximity Payment System Environment (PPSE) for contactless cards
     * Selects "2PAY.SYS.DDF01" per EMV Contactless Book B
     */
    fun readPPSE(tag: Tag): Result<ApduResponse> {
        val ppseAid = "2PAY.SYS.DDF01".toByteArray()
        return selectApplication(tag, ppseAid)
    }
    
    /**
     * EMV: Read Payment System Environment (PSE) for contact cards
     * Selects "1PAY.SYS.DDF01"
     */
    fun readPSE(tag: Tag): Result<ApduResponse> {
        val pseAid = "1PAY.SYS.DDF01".toByteArray()
        return selectApplication(tag, pseAid)
    }
    
    /**
     * EMV: READ RECORD command
     * Reads a record from the specified SFI (Short File Identifier)
     * @param sfi Short File Identifier (1-30)
     * @param record Record number (1-255)
     */
    fun readRecord(tag: Tag, sfi: Int, record: Int): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0xB2.toByte(),  // READ RECORD
            p1 = record.toByte(),
            p2 = ((sfi shl 3) or 0x04).toByte(), // SFI in upper 5 bits, P2 mode = 0x04
            le = 256
        )
        return sendApdu(tag, command)
    }
    
    /**
     * EMV: GET PROCESSING OPTIONS (GPO) command
     * Initiates an EMV transaction. The PDOL data is terminal-provided data
     * requested by the card's Processing Options Data Object List.
     * @param pdolData PDOL-encoded terminal data, or empty for default GPO
     */
    fun getProcessingOptions(tag: Tag, pdolData: ByteArray = byteArrayOf()): Result<ApduResponse> {
        // GPO data is wrapped in tag 83
        val commandData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
        val command = ApduCommand(
            cla = 0x80.toByte(),
            ins = 0xA8.toByte(),  // GET PROCESSING OPTIONS
            p1 = 0x00,
            p2 = 0x00,
            data = commandData,
            le = 256
        )
        return sendApdu(tag, command)
    }
    
    /**
     * EMV: GENERATE APPLICATION CRYPTOGRAM (GENERATE AC) command
     * Used to request the card to generate an Application Cryptogram.
     * @param referenceControl 0x00=AAC, 0x40=TC, 0x80=ARQC
     * @param cdolData Card Risk Management DOL data
     */
    fun generateAC(tag: Tag, referenceControl: Byte, cdolData: ByteArray): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x80.toByte(),
            ins = 0xAE.toByte(),  // GENERATE AC
            p1 = referenceControl,
            p2 = 0x00,
            data = cdolData,
            le = 256
        )
        return sendApdu(tag, command)
    }
    
    /**
     * EMV: GET RESPONSE command
     * Used to retrieve remaining data when SW 61XX is returned.
     */
    fun getResponse(tag: Tag, length: Int): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0xC0.toByte(),  // GET RESPONSE
            p1 = 0x00,
            p2 = 0x00,
            le = length
        )
        return sendApdu(tag, command)
    }
    
    /**
     * EMV: INTERNAL AUTHENTICATE command
     * @param authData Authentication-related data (DDOL data)
     */
    fun internalAuthenticate(tag: Tag, authData: ByteArray): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0x88.toByte(),  // INTERNAL AUTHENTICATE
            p1 = 0x00,
            p2 = 0x00,
            data = authData,
            le = 256
        )
        return sendApdu(tag, command)
    }
    
    /**
     * EMV: GET CHALLENGE command
     * Returns a random number from the card for authentication
     */
    fun getChallenge(tag: Tag): Result<ApduResponse> {
        val command = ApduCommand(
            cla = 0x00,
            ins = 0x84.toByte(),  // GET CHALLENGE
            p1 = 0x00,
            p2 = 0x00,
            le = 8
        )
        return sendApdu(tag, command)
    }
    
    /**
     * Parse hex string to byte array for APDU commands
     */
    fun parseHexCommand(hexString: String): ByteArray {
        val cleaned = hexString.replace(" ", "").replace(":", "")
        return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
