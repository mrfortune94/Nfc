package com.nfc.reader.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.nfc.reader.utils.toHexString

/**
 * Host-based Card Emulation (HCE) Service
 * Emulates NFC card credentials for access control
 * Supports ISO/IEC 14443-4 and ISO/IEC 7816-4
 */
class CardEmulationService : HostApduService() {
    
    companion object {
        private const val TAG = "CardEmulationService"
        
        // Standard AIDs (Application Identifiers)
        private val SELECT_APDU_HEADER = "00A40400".hexToByteArray()
        private const val STATUS_SUCCESS = "9000"
        private const val STATUS_FAILED = "6F00"
        private const val UNKNOWN_CMD_SW = "0000"
        
        // Sample AID for emulation
        private val SAMPLE_AID = "F0010203040506".hexToByteArray()
    }
    
    private var selectedAid: ByteArray? = null
    
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            return UNKNOWN_CMD_SW.hexToByteArray()
        }
        
        Log.d(TAG, "Received APDU: ${commandApdu.toHexString()}")
        
        // Check if it's a SELECT command
        if (isSelectCommand(commandApdu)) {
            val aid = extractAid(commandApdu)
            Log.d(TAG, "SELECT AID: ${aid?.toHexString()}")
            
            return if (aid != null && aid.contentEquals(SAMPLE_AID)) {
                selectedAid = aid
                // Return success with some application data
                buildResponse("Emulated Card", STATUS_SUCCESS)
            } else {
                buildResponse("", STATUS_FAILED)
            }
        }
        
        // Handle other commands when AID is selected
        if (selectedAid != null) {
            return when {
                // GET DATA command
                commandApdu[1] == 0xCA.toByte() -> {
                    handleGetData(commandApdu)
                }
                // READ BINARY command
                commandApdu[1] == 0xB0.toByte() -> {
                    handleReadBinary(commandApdu)
                }
                else -> {
                    buildResponse("", STATUS_FAILED)
                }
            }
        }
        
        return UNKNOWN_CMD_SW.hexToByteArray()
    }
    
    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Service deactivated: $reason")
        selectedAid = null
    }
    
    private fun isSelectCommand(apdu: ByteArray): Boolean {
        return apdu.size >= 4 &&
                apdu[0] == 0x00.toByte() &&
                apdu[1] == 0xA4.toByte() &&
                apdu[2] == 0x04.toByte()
    }
    
    private fun extractAid(apdu: ByteArray): ByteArray? {
        if (apdu.size < 5) return null
        val aidLength = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + aidLength) return null
        return apdu.copyOfRange(5, 5 + aidLength)
    }
    
    private fun handleGetData(apdu: ByteArray): ByteArray {
        // Return card UID or other data
        val p1 = apdu[2]
        val p2 = apdu[3]
        
        return when {
            p1 == 0x00.toByte() && p2 == 0x00.toByte() -> {
                // Return simulated UID
                buildResponse("EMULATED_UID_12345678", STATUS_SUCCESS)
            }
            else -> buildResponse("", STATUS_FAILED)
        }
    }
    
    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        // Simulate reading binary data from card
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val length = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
        
        // Return simulated data
        val data = ByteArray(length) { (it + offset).toByte() }
        return data + STATUS_SUCCESS.hexToByteArray()
    }
    
    private fun buildResponse(data: String, status: String): ByteArray {
        val dataBytes = data.toByteArray()
        val statusBytes = status.hexToByteArray()
        return dataBytes + statusBytes
    }
    
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
