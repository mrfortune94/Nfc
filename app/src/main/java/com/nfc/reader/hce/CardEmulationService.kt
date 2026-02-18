package com.nfc.reader.hce

import android.content.Context
import android.content.SharedPreferences
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nfc.reader.utils.toHexString

/**
 * Host-based Card Emulation (HCE) Service
 * Emulates NFC card credentials for access control
 * Supports ISO/IEC 14443-4 and ISO/IEC 7816-4
 * 
 * Enhanced for replay/emulation functionality with stored profiles
 * 
 * IMPORTANT: Only use with your own cards for educational purposes
 */
class CardEmulationService : HostApduService() {
    
    companion object {
        private const val TAG = "CardEmulationService"
        
        // Status words
        private const val STATUS_SUCCESS = "9000"
        private const val STATUS_FAILED = "6F00"
        private const val STATUS_FILE_NOT_FOUND = "6A82"
        private const val STATUS_WRONG_LENGTH = "6700"
        private const val STATUS_CONDITIONS_NOT_SATISFIED = "6985"
        private const val UNKNOWN_CMD_SW = "0000"
        
        // Shared preferences keys
        private const val PREFS_NAME = "hce_emulation_prefs"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_CUSTOM_UID = "custom_uid"
        private const val KEY_CUSTOM_RESPONSES = "custom_responses"
        private const val KEY_EMULATION_ENABLED = "emulation_enabled"
        
        // Known AIDs for various card types
        val COMMON_AIDS = mapOf(
            "Visa Credit/Debit" to "A0000000031010",
            "Visa Electron" to "A0000000032010",
            "Visa Interlink" to "A0000000033010",
            "Visa Plus" to "A0000000038010",
            "Visa V Pay" to "A000000003101001",
            "Mastercard" to "A0000000041010",
            "Mastercard Maestro" to "A0000000042203",
            "Mastercard Maestro UK" to "A0000000046000",
            "Mastercard Debit" to "A0000000043060",
            "AMEX" to "A00000002501",
            "Discover" to "A0000001523010",
            "JCB" to "A0000000651010",
            "UnionPay Debit" to "A000000333010101",
            "UnionPay Credit" to "A000000333010102",
            "MIR" to "A0000006581010",
            "Custom HCE" to "F0010203040506"
        )
        
        private fun hexStringToByteArray(hex: String): ByteArray {
            val cleaned = hex.replace(" ", "").replace(":", "")
            return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        
        fun setEmulationData(context: Context, uid: String?, responses: Map<String, String>?, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            editor.putBoolean(KEY_EMULATION_ENABLED, enabled)
            
            if (uid != null) {
                editor.putString(KEY_CUSTOM_UID, uid)
            }
            
            if (responses != null) {
                val gson = Gson()
                editor.putString(KEY_CUSTOM_RESPONSES, gson.toJson(responses))
            }
            
            editor.apply()
        }
        
        fun clearEmulationData(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
        
        fun isEmulationEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_EMULATION_ENABLED, false)
        }
    }
    
    private val gson = Gson()
    private var selectedAid: ByteArray? = null
    private var prefs: SharedPreferences? = null
    private var customResponses: Map<String, String>? = null
    private var customUid: String? = null
    private var emulationEnabled: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadEmulationProfile()
        Log.d(TAG, "CardEmulationService created, emulation enabled: $emulationEnabled")
    }
    
    private fun loadEmulationProfile() {
        emulationEnabled = prefs?.getBoolean(KEY_EMULATION_ENABLED, false) ?: false
        customUid = prefs?.getString(KEY_CUSTOM_UID, null)
        
        val responsesJson = prefs?.getString(KEY_CUSTOM_RESPONSES, null)
        if (responsesJson != null) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                customResponses = gson.fromJson(responsesJson, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load custom responses: ${e.message}")
            }
        }
    }
    
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.isEmpty()) {
            Log.w(TAG, "Received null or empty APDU")
            return UNKNOWN_CMD_SW.hexToByteArray()
        }
        
        val commandHex = commandApdu.toHexString()
        Log.d(TAG, "Received APDU: $commandHex (len=${commandApdu.size})")
        
        // Check for custom response first (for replay)
        customResponses?.get(commandHex)?.let { response ->
            Log.d(TAG, "Returning custom response: $response")
            return response.hexToByteArray()
        }
        
        // Handle SELECT command (ISO 7816-4)
        if (isSelectCommand(commandApdu)) {
            return handleSelectCommand(commandApdu)
        }
        
        // Handle commands when AID is selected
        if (selectedAid != null) {
            return handleSelectedAidCommand(commandApdu)
        }
        
        // Unknown command
        Log.d(TAG, "Unknown command, returning 6F00")
        return STATUS_FAILED.hexToByteArray()
    }
    
    private fun handleSelectCommand(commandApdu: ByteArray): ByteArray {
        val aid = extractAid(commandApdu)
        val aidHex = aid?.toHexString() ?: "null"
        Log.d(TAG, "SELECT command for AID: $aidHex")
        
        if (aid == null) {
            return STATUS_WRONG_LENGTH.hexToByteArray()
        }
        
        // Check if this is a supported AID
        val isKnownAid = COMMON_AIDS.values.any { it.equals(aidHex, ignoreCase = true) }
        
        if (isKnownAid || emulationEnabled) {
            selectedAid = aid
            Log.d(TAG, "AID selected: $aidHex")
            
            // Build FCI (File Control Information) response
            val fci = buildFciTemplate(aid)
            return fci + STATUS_SUCCESS.hexToByteArray()
        }
        
        Log.d(TAG, "AID not supported: $aidHex")
        return STATUS_FILE_NOT_FOUND.hexToByteArray()
    }
    
    private fun handleSelectedAidCommand(commandApdu: ByteArray): ByteArray {
        val ins = commandApdu[1]
        
        return when (ins) {
            0xCA.toByte() -> handleGetData(commandApdu)      // GET DATA
            0xB0.toByte() -> handleReadBinary(commandApdu)   // READ BINARY
            0xB2.toByte() -> handleReadRecord(commandApdu)   // READ RECORD
            0xA8.toByte() -> handleGetProcessingOptions(commandApdu) // GPO
            0x88.toByte() -> handleGetChallenge(commandApdu) // GET CHALLENGE
            0x82.toByte() -> handleExternalAuth(commandApdu) // EXTERNAL AUTHENTICATE
            0x84.toByte() -> handleGetChallengeAlt(commandApdu) // GET CHALLENGE (alt)
            else -> {
                Log.d(TAG, "Unhandled INS: ${String.format("%02X", ins)}")
                STATUS_CONDITIONS_NOT_SATISFIED.hexToByteArray()
            }
        }
    }
    
    private fun handleGetData(apdu: ByteArray): ByteArray {
        val p1 = apdu[2]
        val p2 = apdu[3]
        val tag = String.format("%02X%02X", p1, p2)
        
        Log.d(TAG, "GET DATA for tag: $tag")
        
        // Return custom UID if configured
        if (p1 == 0x00.toByte() && p2 == 0x00.toByte() && customUid != null) {
            return customUid!!.hexToByteArray() + STATUS_SUCCESS.hexToByteArray()
        }
        
        // Standard responses for common EMV tags
        return when (tag) {
            "5A00" -> buildResponse("0000000000000000", STATUS_SUCCESS) // PAN (masked)
            "5F24" -> buildResponse("991231", STATUS_SUCCESS) // Expiry
            "9F17" -> buildResponse("03", STATUS_SUCCESS) // PIN Try Counter
            else -> STATUS_FILE_NOT_FOUND.hexToByteArray()
        }
    }
    
    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val length = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
        
        Log.d(TAG, "READ BINARY offset=$offset, length=$length")
        
        // Return simulated data
        val data = ByteArray(minOf(length, 256)) { (it + offset).toByte() }
        return data + STATUS_SUCCESS.hexToByteArray()
    }
    
    private fun handleReadRecord(apdu: ByteArray): ByteArray {
        val recordNum = apdu[2].toInt() and 0xFF
        val sfiAndMode = apdu[3].toInt() and 0xFF
        val sfi = sfiAndMode shr 3
        
        Log.d(TAG, "READ RECORD SFI=$sfi, Record=$recordNum")
        
        // Return empty record with success for testing
        return buildTlv("70", byteArrayOf()) + STATUS_SUCCESS.hexToByteArray()
    }
    
    private fun handleGetProcessingOptions(apdu: ByteArray): ByteArray {
        Log.d(TAG, "GET PROCESSING OPTIONS")
        
        // Return minimal GPO response
        // AIP (Application Interchange Profile) + AFL (Application File Locator)
        val aip = byteArrayOf(0x00, 0x00) // No special capabilities
        val afl = byteArrayOf() // No records
        
        val response = buildTlv("77", buildTlv("82", aip) + buildTlv("94", afl))
        return response + STATUS_SUCCESS.hexToByteArray()
    }
    
    private fun handleGetChallenge(apdu: ByteArray): ByteArray {
        Log.d(TAG, "GET CHALLENGE")
        
        // Generate random challenge
        val challenge = ByteArray(8) { (Math.random() * 256).toInt().toByte() }
        return challenge + STATUS_SUCCESS.hexToByteArray()
    }
    
    private fun handleGetChallengeAlt(apdu: ByteArray): ByteArray {
        return handleGetChallenge(apdu)
    }
    
    private fun handleExternalAuth(apdu: ByteArray): ByteArray {
        Log.d(TAG, "EXTERNAL AUTHENTICATE")
        
        // For testing, accept any authentication
        return STATUS_SUCCESS.hexToByteArray()
    }
    
    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "Link Loss"
            DEACTIVATION_DESELECTED -> "Deselected"
            else -> "Unknown"
        }
        Log.d(TAG, "Service deactivated: $reasonStr")
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
    
    private fun buildFciTemplate(aid: ByteArray): ByteArray {
        // Build FCI (File Control Information) as per ISO 7816-4
        // 6F = FCI Template
        //   84 = DF Name (AID)
        //   A5 = FCI Proprietary Template
        //     50 = Application Label
        //     9F38 = PDOL (Processing Options Data Object List)
        
        val aidTag = buildTlv("84", aid)
        val label = buildTlv("50", "NFC PRO".toByteArray())
        val propTemplate = buildTlv("A5", label)
        
        return buildTlv("6F", aidTag + propTemplate)
    }
    
    private fun buildTlv(tagHex: String, value: ByteArray): ByteArray {
        val tag = tagHex.hexToByteArray()
        val length = when {
            value.size < 128 -> byteArrayOf(value.size.toByte())
            value.size < 256 -> byteArrayOf(0x81.toByte(), value.size.toByte())
            else -> byteArrayOf(0x82.toByte(), (value.size shr 8).toByte(), (value.size and 0xFF).toByte())
        }
        return tag + length + value
    }
    
    private fun buildResponse(dataHex: String, statusHex: String): ByteArray {
        return dataHex.hexToByteArray() + statusHex.hexToByteArray()
    }
    
    private fun String.hexToByteArray(): ByteArray {
        if (this.isEmpty()) return byteArrayOf()
        val cleaned = this.replace(" ", "").replace(":", "")
        return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
