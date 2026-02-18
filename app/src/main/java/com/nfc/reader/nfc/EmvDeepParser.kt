package com.nfc.reader.nfc

import android.nfc.tech.IsoDep
import android.util.Log
import java.nio.ByteBuffer

/**
 * Enhanced EMV Deep Parser for contactless payment card analysis
 * over ISO 14443-4 / ISO-DEP
 * 
 * Supports:
 * - PPSE (Proximity Payment System Environment) for contactless
 * - PSE (Payment System Environment) for contact fallback
 * - Multiple payment AIDs (Visa, Mastercard, AMEX, etc.)
 * - TLV parsing with recursive structure
 * - Track2 equivalent parsing
 * - Application data extraction
 * - PDOL (Processing Options DOL) parsing and construction
 * - EMV response chaining (SW 61XX / 6CXX)
 * - Application File Locator (AFL) based record reading
 * - CVM List parsing
 * - Application Interchange Profile (AIP) decoding
 * 
 * IMPORTANT: For educational/research purposes only.
 * Always use with your own cards.
 */
class EmvDeepParser(private val isoDep: IsoDep) {

    companion object {
        private val TAG = "EmvDeep"
        
        // Payment System Environment
        private val PPSE_AID = "325041592E5359532E4444463031".decodeHex() // 2PAY.SYS.DDF01
        private val PSE_AID = "315041592E5359532E4444463031".decodeHex()  // 1PAY.SYS.DDF01
        
        // Common Payment AIDs
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
            "Discover ZIP" to "A0000003241010",
            "JCB" to "A0000000651010",
            "JCB Debit" to "A0000000652010",
            "UnionPay Debit" to "A000000333010101",
            "UnionPay Credit" to "A000000333010102",
            "UnionPay QuasiCredit" to "A000000333010103",
            "MIR" to "A0000006581010",
            "RuPay" to "A0000005241010",
            "Interac" to "A0000002771010"
        )
        
        // EMV Tag Names for human-readable output
        val EMV_TAGS = mapOf(
            "4F" to "AID (Application Identifier)",
            "50" to "Application Label",
            "57" to "Track 2 Equivalent Data",
            "5A" to "Application PAN",
            "5F20" to "Cardholder Name",
            "5F24" to "Application Expiration Date",
            "5F25" to "Application Effective Date",
            "5F28" to "Issuer Country Code",
            "5F2A" to "Transaction Currency Code",
            "5F2D" to "Language Preference",
            "5F34" to "PAN Sequence Number",
            "6F" to "FCI Template",
            "70" to "EMV Proprietary Template",
            "77" to "Response Message Template Format 2",
            "80" to "Response Message Template Format 1",
            "82" to "Application Interchange Profile",
            "84" to "Dedicated File Name",
            "87" to "Application Priority Indicator",
            "88" to "Short File Identifier",
            "8C" to "Card Risk Management DOL1",
            "8D" to "Card Risk Management DOL2",
            "8E" to "CVM List",
            "8F" to "CA Public Key Index",
            "90" to "Issuer Public Key Certificate",
            "91" to "Issuer Authentication Data",
            "92" to "Issuer Public Key Remainder",
            "93" to "Signed Static Application Data",
            "94" to "Application File Locator",
            "95" to "Terminal Verification Results",
            "9A" to "Transaction Date",
            "9C" to "Transaction Type",
            "9F02" to "Amount, Authorized",
            "9F03" to "Amount, Other",
            "9F06" to "AID",
            "9F07" to "Application Usage Control",
            "9F08" to "Application Version Number",
            "9F09" to "Application Version Number (Terminal)",
            "9F0D" to "Issuer Action Code - Default",
            "9F0E" to "Issuer Action Code - Denial",
            "9F0F" to "Issuer Action Code - Online",
            "9F10" to "Issuer Application Data",
            "9F11" to "Issuer Code Table Index",
            "9F12" to "Application Preferred Name",
            "9F13" to "Last Online ATC Register",
            "9F14" to "Lower Consecutive Offline Limit",
            "9F17" to "PIN Try Counter",
            "9F1A" to "Terminal Country Code",
            "9F1E" to "Interface Device Serial Number",
            "9F1F" to "Track 1 Discretionary Data",
            "9F21" to "Transaction Time",
            "9F26" to "Application Cryptogram",
            "9F27" to "Cryptogram Information Data",
            "9F32" to "Issuer Public Key Exponent",
            "9F33" to "Terminal Capabilities",
            "9F34" to "CVM Results",
            "9F35" to "Terminal Type",
            "9F36" to "Application Transaction Counter",
            "9F37" to "Unpredictable Number",
            "9F38" to "PDOL",
            "9F39" to "POS Entry Mode",
            "9F40" to "Additional Terminal Capabilities",
            "9F42" to "Application Currency Code",
            "9F44" to "Application Currency Exponent",
            "9F45" to "Data Authentication Code",
            "9F46" to "ICC Public Key Certificate",
            "9F47" to "ICC Public Key Exponent",
            "9F48" to "ICC Public Key Remainder",
            "9F49" to "DDOL",
            "9F4A" to "Static Data Authentication Tag List",
            "9F4B" to "Signed Dynamic Application Data",
            "9F4C" to "ICC Dynamic Number",
            "9F4D" to "Log Entry",
            "9F4F" to "Log Format",
            "A5" to "FCI Proprietary Template",
            "BF0C" to "FCI Issuer Discretionary Data",
            "DF8101" to "Kernel ID",
            "DF8102" to "CTQ"
        )

        /**
         * Known PDOL tag lengths for constructing default PDOL data
         */
        val PDOL_TAG_LENGTHS = mapOf(
            "9F66" to 4,  // Terminal Transaction Qualifiers (TTQ)
            "9F02" to 6,  // Amount Authorized
            "9F03" to 6,  // Amount Other
            "9F1A" to 2,  // Terminal Country Code
            "5F2A" to 2,  // Transaction Currency Code
            "9A" to 3,    // Transaction Date
            "9C" to 1,    // Transaction Type
            "9F37" to 4,  // Unpredictable Number
            "9F35" to 1,  // Terminal Type
            "9F45" to 2,  // Data Authentication Code
            "9F4E" to 20, // Merchant Name and Location
            "9F34" to 3,  // CVM Results
            "9F21" to 3,  // Transaction Time
            "9F33" to 3,  // Terminal Capabilities
            "9F40" to 5,  // Additional Terminal Capabilities
            "9F09" to 2,  // Application Version Number (Terminal)
            "9F15" to 2,  // Merchant Category Code
            "9F16" to 15, // Merchant Identifier
            "9F1C" to 8,  // Terminal Identification
            "9F1E" to 8   // Interface Device Serial Number
        )

        /**
         * Decode Application Interchange Profile (AIP) bits
         */
        fun decodeAip(aip: ByteArray): List<String> {
            if (aip.size < 2) return listOf("Invalid AIP")
            val features = mutableListOf<String>()
            val byte1 = aip[0].toInt() and 0xFF
            val byte2 = aip[1].toInt() and 0xFF
            
            if (byte1 and 0x40 != 0) features.add("SDA supported")
            if (byte1 and 0x20 != 0) features.add("DDA supported")
            if (byte1 and 0x10 != 0) features.add("Cardholder verification supported")
            if (byte1 and 0x08 != 0) features.add("Terminal risk management required")
            if (byte1 and 0x04 != 0) features.add("Issuer authentication supported")
            if (byte1 and 0x01 != 0) features.add("CDA supported")
            if (byte2 and 0x80 != 0) features.add("MSD supported (magnetic stripe)")
            if (byte2 and 0x40 != 0) features.add("Relay resistance protocol supported")
            
            if (features.isEmpty()) features.add("No special capabilities")
            return features
        }

        /**
         * Parse CVM (Cardholder Verification Method) List
         */
        fun parseCvmList(data: ByteArray): List<String> {
            val methods = mutableListOf<String>()
            if (data.size < 8) return listOf("Invalid CVM List")
            
            // First 4 bytes: Amount X, next 4 bytes: Amount Y
            val amountX = ((data[0].toInt() and 0xFF) shl 24) or 
                         ((data[1].toInt() and 0xFF) shl 16) or 
                         ((data[2].toInt() and 0xFF) shl 8) or 
                         (data[3].toInt() and 0xFF)
            val amountY = ((data[4].toInt() and 0xFF) shl 24) or 
                         ((data[5].toInt() and 0xFF) shl 16) or 
                         ((data[6].toInt() and 0xFF) shl 8) or 
                         (data[7].toInt() and 0xFF)
            
            methods.add("Amount X: $amountX, Amount Y: $amountY")
            
            // CVM rules: pairs of bytes
            var i = 8
            while (i + 1 < data.size) {
                val cvmCode = data[i].toInt() and 0xFF
                val conditionCode = data[i + 1].toInt() and 0xFF
                
                val method = when (cvmCode and 0x3F) {
                    0x00 -> "Fail CVM processing"
                    0x01 -> "Plaintext PIN verification by ICC"
                    0x02 -> "Enciphered PIN verified online"
                    0x03 -> "Plaintext PIN by ICC + signature"
                    0x04 -> "Enciphered PIN by ICC"
                    0x05 -> "Enciphered PIN by ICC + signature"
                    0x1E -> "Signature (paper)"
                    0x1F -> "No CVM required"
                    0x20 -> "No CVM required (amount confirmed)"
                    else -> "RFU/Unknown (${String.format("%02X", cvmCode and 0x3F)})"
                }
                
                val failAction = if (cvmCode and 0x40 != 0) "apply next if unsuccessful" else "fail on unsuccessful"
                
                val condition = when (conditionCode) {
                    0x00 -> "Always"
                    0x01 -> "If unattended cash"
                    0x02 -> "If not (unattended cash/manual cash/purchase with cashback)"
                    0x03 -> "If terminal supports CVM"
                    0x04 -> "If manual cash"
                    0x05 -> "If purchase with cashback"
                    0x06 -> "If transaction is in application currency and under X"
                    0x07 -> "If transaction is in application currency and over X"
                    0x08 -> "If transaction is in application currency and under Y"
                    0x09 -> "If transaction is in application currency and over Y"
                    else -> "RFU/Unknown (${String.format("%02X", conditionCode)})"
                }
                
                methods.add("  $method ($failAction) - $condition")
                i += 2
            }
            
            return methods
        }
    }

    data class EmvResult(
        val success: Boolean,
        val cardBrand: String?,
        val pan: String?,
        val expiry: String?,
        val cardholderName: String?,
        val rawData: String,
        val parsedTags: Map<String, String>
    )

    fun deepRead(): StringBuilder {
        val sb = StringBuilder()
        try {
            isoDep.timeout = 15000
            if (!isoDep.isConnected) isoDep.connect()

            sb.append("=== EMV Deep Read (ISO 14443-4 / ISO-DEP) ===\n\n")
            
            // Show connection info
            val histBytes = isoDep.historicalBytes
            val hiLayer = isoDep.hiLayerResponse
            if (histBytes != null) {
                sb.append("Historical Bytes (NFC-A): ${histBytes.toHex()}\n")
            }
            if (hiLayer != null) {
                sb.append("HI-Layer Response (NFC-B): ${hiLayer.toHex()}\n")
            }
            sb.append("Max Transceive Length: ${isoDep.maxTransceiveLength}\n\n")

            // Try PPSE first (contactless)
            val ppseResp = transceive(buildSelectCommand(PPSE_AID), sb)
            if (isSuccessOrMoreData(ppseResp)) {
                sb.append("✓ PPSE Selected (contactless mode)\n")
                parseTlv(getResponseData(ppseResp), sb, "  ")
            } else {
                // Try PSE (contact)
                val pseResp = transceive(buildSelectCommand(PSE_AID), sb)
                if (isSuccessOrMoreData(pseResp)) {
                    sb.append("✓ PSE Selected (contact mode)\n")
                    parseTlv(getResponseData(pseResp), sb, "  ")
                }
            }

            sb.append("\n=== Trying Known AIDs ===\n")
            
            var selectedAidName: String? = null
            
            // Try each known AID
            for ((name, aidHex) in COMMON_AIDS) {
                val aid = aidHex.decodeHex()
                val resp = transceive(buildSelectCommand(aid), sb)
                if (isSuccessOrMoreData(resp)) {
                    selectedAidName = name
                    sb.append("\n✓ $name ($aidHex) SELECTED\n")
                    
                    val fciData = getResponseData(resp)
                    val fciTags = mutableMapOf<String, String>()
                    extractTags(fciData, fciTags)
                    parseTlv(fciData, sb, "  ")
                    
                    // Decode AIP if present
                    fciTags["82"]?.let { aipHex ->
                        val aipBytes = aipHex.decodeHex()
                        sb.append("\n  AIP Capabilities:\n")
                        decodeAip(aipBytes).forEach { sb.append("    • $it\n") }
                    }
                    
                    // Parse PDOL if present and use it for GPO
                    val pdolData = fciTags["9F38"]?.let { pdolHex ->
                        sb.append("\n  PDOL requested by card:\n")
                        val pdolBytes = pdolHex.decodeHex()
                        parsePdol(pdolBytes, sb)
                        buildDefaultPdolData(pdolBytes)
                    } ?: byteArrayOf()
                    
                    // Get Processing Options with PDOL data
                    readGpo(sb, pdolData)
                    
                    // Read records using AFL from GPO or brute-force
                    dumpRecords(sb)
                    
                    // Try to get specific data
                    readCardData(sb)
                    
                    break // Found a working AID
                }
            }

            if (selectedAidName == null) {
                sb.append("\n✗ No known AID selected\n")
            }

        } catch (e: Exception) {
            sb.append("\nError: ${e.message}\n")
            Log.e(TAG, "Deep read error", e)
        }
        return sb
    }

    fun readEmvCard(): EmvResult {
        val parsedTags = mutableMapOf<String, String>()
        var cardBrand: String? = null
        var pan: String? = null
        var expiry: String? = null
        var cardholderName: String? = null
        val rawData = StringBuilder()

        try {
            if (!isoDep.isConnected) isoDep.connect()
            isoDep.timeout = 15000

            // Select PPSE
            val ppseResp = transceiveRaw(buildSelectCommand(PPSE_AID))
            if (isSuccessOrMoreData(ppseResp)) {
                rawData.append("PPSE: ${ppseResp.toHex()}\n")
                extractTags(getResponseData(ppseResp), parsedTags)
            }

            // Try AIDs
            for ((name, aidHex) in COMMON_AIDS) {
                val aid = aidHex.decodeHex()
                val resp = transceiveRaw(buildSelectCommand(aid))
                if (isSuccessOrMoreData(resp)) {
                    cardBrand = name
                    rawData.append("AID $name: ${resp.toHex()}\n")
                    
                    val fciTags = mutableMapOf<String, String>()
                    extractTags(getResponseData(resp), fciTags)
                    parsedTags.putAll(fciTags)

                    // Parse PDOL and build GPO data
                    val pdolData = fciTags["9F38"]?.let { pdolHex ->
                        buildDefaultPdolData(pdolHex.decodeHex())
                    } ?: byteArrayOf()

                    // GPO with PDOL
                    val gpoCommandData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
                    val gpoCmd = byteArrayOf(
                        0x80.toByte(), 0xA8.toByte(), 0x00, 0x00,
                        gpoCommandData.size.toByte()
                    ) + gpoCommandData + byteArrayOf(0x00)
                    
                    val gpoResp = transceiveRaw(gpoCmd)
                    if (isSuccessOrMoreData(gpoResp)) {
                        rawData.append("GPO: ${gpoResp.toHex()}\n")
                        val gpoData = getResponseData(gpoResp)
                        extractTags(gpoData, parsedTags)
                        
                        // Parse AFL from GPO response for targeted record reading
                        val aflEntries = parseAflFromGpo(gpoData)
                        if (aflEntries.isNotEmpty()) {
                            for ((sfi, firstRec, lastRec) in aflEntries) {
                                for (rec in firstRec..lastRec) {
                                    val p2 = (sfi shl 3) or 0x04
                                    val readCmd = byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), p2.toByte(), 0x00)
                                    val recResp = transceiveRaw(readCmd)
                                    if (isSuccessOrMoreData(recResp)) {
                                        extractTags(getResponseData(recResp), parsedTags)
                                    }
                                }
                            }
                        } else {
                            // Fallback: brute-force read records
                            for (sfi in 1..10) {
                                for (rec in 1..5) {
                                    val p2 = (sfi shl 3) or 0x04
                                    val readCmd = byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), p2.toByte(), 0x00)
                                    val recResp = transceiveRaw(readCmd)
                                    if (isSuccessOrMoreData(recResp)) {
                                        extractTags(getResponseData(recResp), parsedTags)
                                    }
                                }
                            }
                        }
                    }

                    break
                }
            }

            // Extract key fields (use case-insensitive F removal for BCD padding)
            pan = parsedTags["5A"]?.replace(Regex("[Ff]"), "")
            expiry = parsedTags["5F24"]?.let { formatExpiry(it) }
            cardholderName = parsedTags["5F20"]?.let { hexToAscii(it).trim() }

            // Try Track2 for additional PAN/expiry
            parsedTags["57"]?.let { track2 ->
                val t2 = parseTrack2(track2)
                if (pan == null) pan = t2["pan"]
                if (expiry == null) expiry = t2["expiry"]
            }

            return EmvResult(
                success = cardBrand != null,
                cardBrand = cardBrand,
                pan = pan?.chunked(4)?.joinToString(" "),
                expiry = expiry,
                cardholderName = cardholderName,
                rawData = rawData.toString(),
                parsedTags = parsedTags
            )

        } catch (e: Exception) {
            Log.e(TAG, "EMV read error", e)
            return EmvResult(
                success = false,
                cardBrand = null,
                pan = null,
                expiry = null,
                cardholderName = null,
                rawData = "Error: ${e.message}",
                parsedTags = parsedTags
            )
        }
    }

    private fun buildSelectCommand(aid: ByteArray): ByteArray {
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
    }

    private fun readGpo(sb: StringBuilder, pdolData: ByteArray = byteArrayOf()) {
        sb.append("\n=== Get Processing Options ===\n")
        
        // GPO with PDOL data wrapped in tag 83
        val commandData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
        val gpoCmd = byteArrayOf(
            0x80.toByte(), 0xA8.toByte(), 0x00, 0x00,
            commandData.size.toByte()
        ) + commandData + byteArrayOf(0x00)
        
        val gpoResp = transceive(gpoCmd, sb)
        if (isSuccessOrMoreData(gpoResp)) {
            sb.append("GPO Response:\n")
            val gpoData = getResponseData(gpoResp)
            parseTlv(gpoData, sb, "  ")
            
            // Parse AIP from GPO response
            val gpoTags = mutableMapOf<String, String>()
            extractTags(gpoData, gpoTags)
            
            gpoTags["82"]?.let { aipHex ->
                val aipBytes = aipHex.decodeHex()
                sb.append("\n  AIP Capabilities:\n")
                decodeAip(aipBytes).forEach { sb.append("    • $it\n") }
            }
            
            // Parse AFL and show targeted records
            val aflEntries = parseAflFromGpo(gpoData)
            if (aflEntries.isNotEmpty()) {
                sb.append("\n  AFL Entries (targeted records):\n")
                for ((sfi, firstRec, lastRec) in aflEntries) {
                    sb.append("    SFI $sfi: records $firstRec to $lastRec\n")
                }
            }
        }
    }

    /**
     * Parse Application File Locator (AFL) entries from GPO response.
     * Returns list of (sfi, firstRecord, lastRecord) triples.
     */
    private fun parseAflFromGpo(gpoData: ByteArray): List<Triple<Int, Int, Int>> {
        val entries = mutableListOf<Triple<Int, Int, Int>>()
        val tags = mutableMapOf<String, String>()
        extractTags(gpoData, tags)
        
        // AFL is in tag 94
        val aflHex = tags["94"] ?: return entries
        val afl = aflHex.decodeHex()
        
        // AFL is 4 bytes per entry: SFI(1) | FirstRecord(1) | LastRecord(1) | ODARecords(1)
        var i = 0
        while (i + 3 < afl.size) {
            val sfi = (afl[i].toInt() and 0xF8) shr 3
            val firstRec = afl[i + 1].toInt() and 0xFF
            val lastRec = afl[i + 2].toInt() and 0xFF
            if (sfi in 1..30 && firstRec in 1..255 && lastRec >= firstRec) {
                entries.add(Triple(sfi, firstRec, lastRec))
            }
            i += 4
        }
        
        return entries
    }

    /**
     * Parse PDOL (Processing Options Data Object List) and display requested tags.
     */
    private fun parsePdol(pdolBytes: ByteArray, sb: StringBuilder) {
        var pos = 0
        while (pos < pdolBytes.size) {
            val (tag, tLen) = readTag(pdolBytes, pos)
            pos += tLen
            if (pos >= pdolBytes.size) break
            
            val length = pdolBytes[pos].toInt() and 0xFF
            pos++
            
            val tagHex = tag.toHex()
            val tagName = EMV_TAGS[tagHex] ?: "Unknown"
            sb.append("    $tagHex ($tagName) - $length bytes\n")
        }
    }

    /**
     * Build default PDOL data based on the PDOL from the card's FCI.
     * Fills in zero/default values for requested terminal data objects.
     */
    private fun buildDefaultPdolData(pdolBytes: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        var pos = 0
        
        while (pos < pdolBytes.size) {
            val (tag, tLen) = readTag(pdolBytes, pos)
            pos += tLen
            if (pos >= pdolBytes.size) break
            
            val length = pdolBytes[pos].toInt() and 0xFF
            pos++
            
            // Fill with zeros (safe default for educational/testing use)
            repeat(length) { result.add(0x00) }
        }
        
        return result.toByteArray()
    }

    private fun dumpRecords(sb: StringBuilder) {
        sb.append("\n=== SFI/Record Dump ===\n")
        val sfis = listOf(1, 2, 3, 4, 10, 11, 17, 18, 20, 21)
        for (sfi in sfis) {
            for (rec in 1..10) {
                val p2 = (sfi shl 3) or 0x04
                val cmd = byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), p2.toByte(), 0x00)
                val data = transceive(cmd, sb)
                if (isSuccessOrMoreData(data)) {
                    sb.append("SFI $sfi REC $rec:\n")
                    val recData = getResponseData(data)
                    parseTlv(recData, sb, "  ")
                    
                    // Check for CVM List in this record
                    val recTags = mutableMapOf<String, String>()
                    extractTags(recData, recTags)
                    recTags["8E"]?.let { cvmHex ->
                        sb.append("  CVM List:\n")
                        parseCvmList(cvmHex.decodeHex()).forEach { sb.append("    $it\n") }
                    }
                }
            }
        }
    }

    private fun readCardData(sb: StringBuilder) {
        sb.append("\n=== GET DATA Commands ===\n")
        
        val tags = listOf(
            "5A" to "PAN",
            "5F24" to "Expiry",
            "5F20" to "Cardholder Name",
            "57" to "Track2",
            "9F17" to "PIN Try Counter",
            "9F36" to "ATC",
            "9F13" to "Last Online ATC",
            "9F4F" to "Log Format",
            "9F4D" to "Log Entry",
            "9F08" to "Application Version Number",
            "9F42" to "Application Currency Code",
            "9F44" to "Application Currency Exponent"
        )
        
        for ((tag, name) in tags) {
            val tagBytes = tag.decodeHex()
            val p1 = if (tagBytes.size > 1) tagBytes[0] else 0x00.toByte()
            val p2 = tagBytes.last()
            val cmd = byteArrayOf(0x80.toByte(), 0xCA.toByte(), p1, p2, 0x00)
            val resp = transceive(cmd, sb)
            if (isSuccessOrMoreData(resp)) {
                val value = getResponseData(resp)
                sb.append("$name ($tag): ${value.toHex()}\n")
                
                // Parse specific values (use case-insensitive F removal for BCD padding)
                when (tag) {
                    "5A" -> sb.append("  → PAN: ${value.toHex().replace(Regex("[Ff]"), "")}\n")
                    "5F24" -> sb.append("  → Expiry: ${formatExpiry(value.toHex())}\n")
                    "5F20" -> sb.append("  → Name: ${hexToAscii(value.toHex())}\n")
                    "57" -> parseTrack2Enhanced(value, sb)
                }
            }
        }
    }

    private fun parseTlv(data: ByteArray, sb: StringBuilder, indent: String = "") {
        var pos = 0
        while (pos < data.size) {
            try {
                // Read tag
                val (tag, tLen) = readTag(data, pos)
                pos += tLen
                if (pos >= data.size) break

                // Read length
                val (len, lLen) = readLength(data, pos)
                pos += lLen
                if (pos + len > data.size) break

                // Read value
                val value = data.copyOfRange(pos, pos + len)
                val tagHex = tag.toHex()
                val tagName = EMV_TAGS[tagHex] ?: "Unknown"
                
                sb.append("$indent$tagHex ($tagName): ${value.toHex()}\n")

                // If constructed, parse recursively
                if ((tag[0].toInt() and 0x20) != 0) {
                    parseTlv(value, sb, "$indent  ")
                } else {
                    // Try to decode known values (use case-insensitive F removal for BCD padding)
                    when (tagHex) {
                        "50", "5F20", "9F12" -> sb.append("$indent  → ${hexToAscii(value.toHex())}\n")
                        "5A" -> sb.append("$indent  → PAN: ${value.toHex().replace(Regex("[Ff]"), "")}\n")
                        "5F24" -> sb.append("$indent  → Expiry: ${formatExpiry(value.toHex())}\n")
                        "57" -> parseTrack2Enhanced(value, StringBuilder().also { sb.append(it) })
                        "82" -> {
                            sb.append("$indent  AIP:\n")
                            decodeAip(value).forEach { sb.append("$indent    • $it\n") }
                        }
                        "8E" -> {
                            sb.append("$indent  CVM List:\n")
                            parseCvmList(value).forEach { sb.append("$indent    $it\n") }
                        }
                    }
                }

                pos += len
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun extractTags(data: ByteArray, tags: MutableMap<String, String>) {
        var pos = 0
        while (pos < data.size) {
            try {
                val (tag, tLen) = readTag(data, pos)
                pos += tLen
                if (pos >= data.size) break

                val (len, lLen) = readLength(data, pos)
                pos += lLen
                if (pos + len > data.size) break

                val value = data.copyOfRange(pos, pos + len)
                val tagHex = tag.toHex()
                tags[tagHex] = value.toHex()

                // Parse constructed tags recursively
                if ((tag[0].toInt() and 0x20) != 0) {
                    extractTags(value, tags)
                }

                pos += len
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun parseTrack2Enhanced(raw: ByteArray, sb: StringBuilder) {
        val t2 = parseTrack2(raw.toHex())
        sb.append("  Track2 Data:\n")
        sb.append("    PAN: ${t2["pan"]}\n")
        sb.append("    Expiry: ${t2["expiry"]}\n")
        sb.append("    Service Code: ${t2["serviceCode"]}\n")
    }

    private fun parseTrack2(hexData: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val digits = hexData.uppercase()
        val sep = digits.indexOf('D')
        
        if (sep > 0) {
            // Use case-insensitive F removal for BCD padding
            result["pan"] = digits.substring(0, sep).replace(Regex("[Ff]"), "")
            val rest = digits.substring(sep + 1)
            if (rest.length >= 4) {
                result["expiry"] = rest.substring(0, 4).let { "${it.substring(2, 4)}/${it.substring(0, 2)}" }
            }
            if (rest.length >= 7) {
                result["serviceCode"] = rest.substring(4, 7)
            }
        }
        return result
    }

    /**
     * Transceive with logging and EMV response chaining support
     */
    private fun transceive(cmd: ByteArray, sb: StringBuilder): ByteArray {
        Log.d(TAG, "→ ${cmd.toHex()}")
        return try {
            var r = isoDep.transceive(cmd)
            Log.d(TAG, "← ${r.toHex()}")
            
            // Handle response chaining
            while (r.size >= 2) {
                val sw1 = r[r.size - 2].toInt() and 0xFF
                val sw2 = r[r.size - 1].toInt() and 0xFF
                
                if (sw1 == 0x61) {
                    // GET RESPONSE for remaining data
                    val getResp = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
                    val next = isoDep.transceive(getResp)
                    r = r.copyOfRange(0, r.size - 2) + next
                } else if (sw1 == 0x6C) {
                    // Wrong Le, re-send with correct Le
                    val corrected = cmd.copyOf()
                    corrected[corrected.size - 1] = sw2.toByte()
                    r = isoDep.transceive(corrected)
                } else {
                    break
                }
            }
            
            r
        } catch (e: Exception) {
            Log.e(TAG, "transceive fail", e)
            byteArrayOf(0x6F.toByte(), 0x00)
        }
    }

    /**
     * Raw transceive with EMV response chaining (no logging StringBuilder)
     */
    private fun transceiveRaw(cmd: ByteArray): ByteArray {
        return try {
            var r = isoDep.transceive(cmd)
            
            while (r.size >= 2) {
                val sw1 = r[r.size - 2].toInt() and 0xFF
                val sw2 = r[r.size - 1].toInt() and 0xFF
                
                if (sw1 == 0x61) {
                    val getResp = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
                    val next = isoDep.transceive(getResp)
                    r = r.copyOfRange(0, r.size - 2) + next
                } else if (sw1 == 0x6C) {
                    val corrected = cmd.copyOf()
                    corrected[corrected.size - 1] = sw2.toByte()
                    r = isoDep.transceive(corrected)
                } else {
                    break
                }
            }
            
            r
        } catch (e: Exception) {
            Log.e(TAG, "transceive fail", e)
            byteArrayOf(0x6F.toByte(), 0x00)
        }
    }

    /**
     * Check if response indicates success (9000) or more data available (61XX)
     */
    private fun isSuccessOrMoreData(resp: ByteArray): Boolean {
        if (resp.size < 2) return false
        val sw1 = resp[resp.size - 2].toInt() and 0xFF
        val sw2 = resp.last().toInt() and 0xFF
        return (sw1 == 0x90 && sw2 == 0x00) || sw1 == 0x61
    }

    /**
     * Get the data portion of a response (without status word bytes)
     */
    private fun getResponseData(resp: ByteArray): ByteArray {
        return if (resp.size > 2) resp.copyOfRange(0, resp.size - 2) else byteArrayOf()
    }

    private fun readTag(data: ByteArray, offset: Int): Pair<ByteArray, Int> {
        var pos = offset
        val first = data[pos++]
        
        return if ((first.toInt() and 0x1F) == 0x1F) {
            // Multi-byte tag
            var tagLen = 1
            while (pos < data.size && (data[pos].toInt() and 0x80) != 0) {
                pos++
                tagLen++
            }
            if (pos < data.size) {
                pos++
                tagLen++
            }
            data.copyOfRange(offset, offset + tagLen) to tagLen
        } else {
            byteArrayOf(first) to 1
        }
    }

    private fun readLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        var pos = offset
        val first = data[pos++].toInt() and 0xFF
        
        return if (first < 0x80) {
            first to 1
        } else {
            val numBytes = first and 0x7F
            var len = 0
            repeat(numBytes) {
                if (pos < data.size) {
                    len = (len shl 8) or (data[pos++].toInt() and 0xFF)
                }
            }
            len to (1 + numBytes)
        }
    }

    private fun formatExpiry(hex: String): String {
        if (hex.length >= 4) {
            return "${hex.substring(2, 4)}/${hex.substring(0, 2)}"
        }
        return hex
    }

    private fun hexToAscii(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < hex.length - 1) {
            val byte = hex.substring(i, i + 2).toIntOrNull(16) ?: 0
            if (byte in 32..126) {
                sb.append(byte.toChar())
            }
            i += 2
        }
        return sb.toString()
    }
}

// Extension helpers
fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
fun String.decodeHex() = chunked(2).map { it.toInt(16).toUByte().toByte() }.toByteArray()
