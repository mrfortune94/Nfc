package com.nfc.reader.nfc

import android.nfc.tech.IsoDep
import android.util.Log
import java.nio.ByteBuffer

/**
 * Enhanced EMV Deep Parser for contactless payment card analysis
 * 
 * Supports:
 * - PPSE (Proximity Payment System Environment)
 * - Multiple payment AIDs (Visa, Mastercard, AMEX, etc.)
 * - TLV parsing with recursive structure
 * - Track2 equivalent parsing
 * - Application data extraction
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

            sb.append("=== EMV Deep Read ===\n\n")

            // Try PPSE first (contactless)
            val ppseResp = transceive(buildSelectCommand(PPSE_AID), sb)
            if (isSuccess(ppseResp)) {
                sb.append("✓ PPSE Selected\n")
                parseTlv(ppseResp.dropLast(2).toByteArray(), sb, "  ")
            } else {
                // Try PSE (contact)
                val pseResp = transceive(buildSelectCommand(PSE_AID), sb)
                if (isSuccess(pseResp)) {
                    sb.append("✓ PSE Selected\n")
                    parseTlv(pseResp.dropLast(2).toByteArray(), sb, "  ")
                }
            }

            sb.append("\n=== Trying Known AIDs ===\n")
            
            var selectedAidName: String? = null
            
            // Try each known AID
            for ((name, aidHex) in COMMON_AIDS) {
                val aid = aidHex.decodeHex()
                val resp = transceive(buildSelectCommand(aid), sb)
                if (isSuccess(resp)) {
                    selectedAidName = name
                    sb.append("\n✓ $name ($aidHex) SELECTED\n")
                    parseTlv(resp.dropLast(2).toByteArray(), sb, "  ")
                    
                    // Get Processing Options
                    readGpo(sb)
                    
                    // Read records
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
            val ppseResp = isoDep.transceive(buildSelectCommand(PPSE_AID))
            if (isSuccess(ppseResp)) {
                rawData.append("PPSE: ${ppseResp.toHex()}\n")
                extractTags(ppseResp.dropLast(2).toByteArray(), parsedTags)
            }

            // Try AIDs
            for ((name, aidHex) in COMMON_AIDS) {
                val aid = aidHex.decodeHex()
                val resp = isoDep.transceive(buildSelectCommand(aid))
                if (isSuccess(resp)) {
                    cardBrand = name
                    rawData.append("AID $name: ${resp.toHex()}\n")
                    extractTags(resp.dropLast(2).toByteArray(), parsedTags)

                    // GPO
                    val gpoResp = isoDep.transceive("80A8000002830000".decodeHex())
                    if (isSuccess(gpoResp)) {
                        rawData.append("GPO: ${gpoResp.toHex()}\n")
                        extractTags(gpoResp.dropLast(2).toByteArray(), parsedTags)
                    }

                    // Read records
                    for (sfi in 1..10) {
                        for (rec in 1..5) {
                            val p2 = (sfi shl 3) or 0x04
                            val readCmd = byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), p2.toByte(), 0x00)
                            val recResp = isoDep.transceive(readCmd)
                            if (isSuccess(recResp)) {
                                extractTags(recResp.dropLast(2).toByteArray(), parsedTags)
                            }
                        }
                    }

                    break
                }
            }

            // Extract key fields
            pan = parsedTags["5A"]?.replace("F", "")
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

    private fun readGpo(sb: StringBuilder) {
        sb.append("\n=== Get Processing Options ===\n")
        
        // Simple GPO with empty PDOL
        val gpoCmd = "80A8000002830000".decodeHex()
        val gpoResp = transceive(gpoCmd, sb)
        if (isSuccess(gpoResp)) {
            sb.append("GPO Response:\n")
            parseTlv(gpoResp.dropLast(2).toByteArray(), sb, "  ")
        }
    }

    private fun dumpRecords(sb: StringBuilder) {
        sb.append("\n=== SFI/Record Dump ===\n")
        val sfis = listOf(1, 2, 3, 4, 10, 11, 17, 18, 20, 21)
        for (sfi in sfis) {
            for (rec in 1..10) {
                val p2 = (sfi shl 3) or 0x04
                val cmd = byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), p2.toByte(), 0x00)
                val data = transceive(cmd, sb)
                if (isSuccess(data)) {
                    sb.append("SFI $sfi REC $rec:\n")
                    parseTlv(data.dropLast(2).toByteArray(), sb, "  ")
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
            "9F4F" to "Log Format"
        )
        
        for ((tag, name) in tags) {
            val tagBytes = tag.decodeHex()
            val p1 = if (tagBytes.size > 1) tagBytes[0] else 0x00.toByte()
            val p2 = tagBytes.last()
            val cmd = byteArrayOf(0x80.toByte(), 0xCA.toByte(), p1, p2, 0x00)
            val resp = transceive(cmd, sb)
            if (isSuccess(resp)) {
                val value = resp.dropLast(2).toByteArray()
                sb.append("$name ($tag): ${value.toHex()}\n")
                
                // Parse specific values
                when (tag) {
                    "5A" -> sb.append("  → PAN: ${value.toHex().replace("F", "")}\n")
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
                    // Try to decode known values
                    when (tagHex) {
                        "50", "5F20", "9F12" -> sb.append("$indent  → ${hexToAscii(value.toHex())}\n")
                        "5A" -> sb.append("$indent  → PAN: ${value.toHex().replace("F", "")}\n")
                        "5F24" -> sb.append("$indent  → Expiry: ${formatExpiry(value.toHex())}\n")
                        "57" -> parseTrack2Enhanced(value, StringBuilder().also { sb.append(it) })
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
            result["pan"] = digits.substring(0, sep).replace("F", "")
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

    private fun transceive(cmd: ByteArray, sb: StringBuilder): ByteArray {
        Log.d(TAG, "→ ${cmd.toHex()}")
        return try {
            val r = isoDep.transceive(cmd)
            Log.d(TAG, "← ${r.toHex()}")
            r
        } catch (e: Exception) {
            Log.e(TAG, "transceive fail", e)
            byteArrayOf(0x6F.toByte(), 0x00)
        }
    }

    private fun isSuccess(resp: ByteArray): Boolean {
        return resp.size >= 2 && 
               resp[resp.size - 2].toInt() and 0xFF == 0x90 && 
               resp.last().toInt() and 0xFF == 0x00
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
