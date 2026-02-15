package com.nfc.reader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.nfc.reader.utils.toHexString
import java.io.IOException

/**
 * APDU (Application Protocol Data Unit) Handler
 * Supports ISO/IEC 7816-4 and EMV contactless communication
 */
class ApduHandler {
    
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
            return "Data: ${data.toHexString()}, SW: ${String.format("%02X%02X", sw1, sw2)}"
        }
    }
    
    /**
     * Send APDU command to ISO-DEP tag (ISO 14443-4)
     */
    fun sendApdu(tag: Tag, command: ApduCommand): Result<ApduResponse> {
        val isoDep = IsoDep.get(tag) ?: return Result.failure(
            IllegalArgumentException("Tag does not support ISO-DEP")
        )
        
        return try {
            isoDep.connect()
            val commandBytes = command.toByteArray()
            val response = isoDep.transceive(commandBytes)
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
     * Send raw APDU bytes
     */
    fun sendRawApdu(tag: Tag, commandBytes: ByteArray): Result<ApduResponse> {
        val isoDep = IsoDep.get(tag) ?: return Result.failure(
            IllegalArgumentException("Tag does not support ISO-DEP")
        )
        
        return try {
            isoDep.connect()
            val response = isoDep.transceive(commandBytes)
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
     * EMV: Read Payment System Environment (PSE)
     */
    fun readPSE(tag: Tag): Result<ApduResponse> {
        // PSE directory "1PAY.SYS.DDF01" or "2PAY.SYS.DDF01"
        val pseAid = "1PAY.SYS.DDF01".toByteArray()
        return selectApplication(tag, pseAid)
    }
    
    /**
     * Parse hex string to byte array for APDU commands
     */
    fun parseHexCommand(hexString: String): ByteArray {
        val cleaned = hexString.replace(" ", "").replace(":", "")
        return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
