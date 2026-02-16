package com.nfc.reader.nfc

import android.nfc.tech.IsoDep
import android.util.Log
import java.nio.ByteBuffer

class EmvDeepParser(private val isoDep: IsoDep) {

    companion object {
        private val TAG = "EmvDeep"
        private val COMMON_AIDS = listOf(
            "A0000000031010", // Visa Debit/Credit
            "A0000000041010", // Mastercard
            "A00000002501",   // Amex
            "A0000001523010", // Discover
            "A0000000651010", // JCB
            "A0000000032010", // Visa Electron
            "A0000000042203"  // Mastercard Maestro
        ).map { it.decodeHex() }
    }

    fun deepRead(): StringBuilder {
        val sb = StringBuilder()
        try {
            isoDep.timeout = 15000
            if (!isoDep.isConnected) isoDep.connect()

            // PPSE select
            val ppseResp = transceive("00A404000E325041592E5359532E444446303100".decodeHex())
            sb.append("PPSE: ${ppseResp.toHex()}\n")

            // Try multiple AIDs
            COMMON_AIDS.forEach { aid ->
                val select = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), aid.size.toByte()) + aid
                val resp = transceive(select)
                if (isSuccess(resp)) {
                    sb.append("Selected AID ${aid.toHex()}: ${resp.toHex()}\n")
                    parseFci(resp, sb)
                    dumpRecords(sb)  // try SFI/records
                }
            }

            // Track2 equivalent (common tag 57)
            val track2 = getData("57".decodeHex(), sb)
            if (track2 != null) {
                sb.append("Track2 eq: ${track2.toHex()}\n")
                parseTrack2Enhanced(track2, sb)
            }

        } catch (e: Exception) {
            sb.append("Deep read error: ${e.message}\n")
        }
        return sb
    }

    private fun parseFci(fci: ByteArray, sb: StringBuilder) {
        // Your recursive TLV here – parse 6F template, extract 84 (DF name), A5 (prop template), etc.
        // Implement recursiveDecodeTlv(fci.drop(2), sb, 0) or similar
    }

    private fun dumpRecords(sb: StringBuilder) {
        for (sfi in 1..30) {
            for (rec in 1..10) {
                val cmd = byteArrayOf(0x00.toByte(), 0xB2.toByte(), rec.toByte(), (sfi shl 3 or 4).toByte(), 0x00.toByte())
                val data = transceive(cmd)
                if (isSuccess(data)) {
                    sb.append("SFI $sfi Rec $rec: ${data.dropLast(2).toHex()}\n")
                }
            }
        }
    }

    private fun getData(tag: ByteArray, sb: StringBuilder): ByteArray? {
        val cmd = byteArrayOf(0x80.toByte(), 0xCA.toByte()) + tag + byteArrayOf(0x00.toByte())
        val resp = transceive(cmd)
        return if (isSuccess(resp)) resp.dropLast(2).toByteArray() else null
    }

    private fun transceive(cmd: ByteArray): ByteArray {
        Log.d(TAG, "→ ${cmd.toHex()}")
        val r = isoDep.transceive(cmd)
        Log.d(TAG, "← ${r.toHex()}")
        return r
    }

    private fun isSuccess(resp: ByteArray) = resp.size >= 2 && resp.takeLast(2).contentEquals(byteArrayOf(0x90.toByte(), 0x00))

    private fun parseTrack2Enhanced(raw: ByteArray, sb: StringBuilder) {
        // Enhanced: handle BCD compressed, separator D, discretionary for issuer data
        // Output full possible PAN, expiry YYMM, service code, discretionary
        // Your existing fixed parser + extras
    }
}

// Extension helpers
fun ByteArray.toHex() = joinToString("") { "%02X".format(it) }
fun String.decodeHex() = chunked(2).map { it.toInt(16).toUByte().toByte() }.toByteArray()
