package com.nfc.reader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.data.NfcLog
import com.nfc.reader.databinding.ActivityMainBinding
import com.nfc.reader.nfc.NfcTagReader
import com.nfc.reader.ui.DiagnosticsActivity
import com.nfc.reader.ui.WriteTagActivity
import com.nfc.reader.ui.EmulateCardActivity
import com.nfc.reader.ui.BackupManagerActivity
import com.nfc.reader.ui.ProtectedTagActivity
import com.nfc.reader.utils.toHexString
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Main Activity for NFC Tag Reading
 * Supports ISO/IEC 14443-A/B, ISO/IEC 15693, ISO/IEC 18092, and other standards
 * Implements NfcAdapter.ReaderCallback for reliable tag detection
 */
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private val tagReader = NfcTagReader()
    private val gson = Gson()

    private lateinit var database: NfcDatabase

    companion object {
        private const val TAG = "NFC_DEBUG"
        private const val MAX_TOAST_LENGTH = 300

        // ────────────────────────────────────────────────
        // RESEARCH / DEBUG FLAGS (set to true for max output)
        // ────────────────────────────────────────────────
        const val AGGRESSIVE_LOGGING = true           // logs every APDU in detail
        const val DUMP_ALL_RECORDS = true             // tries many more SFI/records
        const val ENABLE_FALLBACK_RAW_READS = true    // extra raw commands when AID fails
        const val SHOW_RAW_BYTES_IN_UI = true         // shows hex in UI (warning: can be huge)
        const val AUTO_BACKUP_ON_READ = false         // auto-save every read to DB (set true if wanted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = NfcDatabase.getDatabase(this)

        val prefs = getSharedPreferences("nfc_pro_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("disclaimer_accepted", false)) {
            showDisclaimerDialog(prefs)
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            binding.statusText.text = getString(R.string.nfc_not_supported)
            disableButtons()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            binding.statusText.text = getString(R.string.nfc_disabled_tap_to_enable)
            binding.statusText.setOnClickListener {
                openNfcSettings()
            }
        }

        setupUI()

        handleIncomingNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            Toast.makeText(this, getString(R.string.nfc_hardware_not_found), Toast.LENGTH_LONG).show()
            return
        } else if (!adapter.isEnabled) {
            binding.statusText.text = getString(R.string.nfc_disabled_tap_to_enable)
            binding.statusText.setOnClickListener { openNfcSettings() }
            Toast.makeText(this, getString(R.string.nfc_is_off), Toast.LENGTH_LONG).show()
            return
        } else {
            binding.statusText.text = getString(R.string.scan_prompt)
            binding.statusText.setOnClickListener(null)
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 400) // more stable polling
        }

        nfcAdapter?.enableReaderMode(this, this, flags, extras)
        Log.d(TAG, "Reader mode ENABLED")
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        Log.d(TAG, "Reader mode DISABLED")
    }

    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val uidHex = it.id?.joinToString(":") { byte -> "%02X".format(byte) } ?: "No UID"
            val techs = it.techList?.joinToString(", ") ?: "No techs"

            var info = "TAG DETECTED 🔥\nUID: $uidHex\nTechs: $techs"

            val isoDep = IsoDep.get(it)
            if (isoDep != null) {
                info += deepEmvRead(isoDep)
            } else {
                Ndef.get(it)?.let { ndef ->
                    try {
                        ndef.connect()
                        if (ndef.isConnected && ndef.ndefMessage != null) {
                            val records = ndef.ndefMessage.records
                            info += "\n\nNDEF (${records.size} records):"
                            records.forEachIndexed { idx, rec ->
                                val payload = rec.payload
                                if (payload != null && payload.isNotEmpty()) {
                                    val text = try {
                                        val statusByte = payload[0].toInt() and 0xFF
                                        val langCodeLength = statusByte and 0x3F
                                        val textOffset = 1 + langCodeLength
                                        if (textOffset < payload.size) {
                                            String(payload.copyOfRange(textOffset, payload.size), Charset.forName("UTF-8"))
                                        } else {
                                            payload.toHexString()
                                        }
                                    } catch (_: Exception) {
                                        "Binary data"
                                    }
                                    info += "\n  Record $idx: $text"
                                }
                            }
                        }
                    } catch (e: IOException) {
                        info += "\nNDEF connect failed: ${e.message}"
                    } finally {
                        try { ndef.close() } catch (_: Exception) {}
                    }
                }

                MifareClassic.get(it)?.let { mfc ->
                    try {
                        mfc.connect()
                        info += "\n\nMIFARE Classic (${mfc.sectorCount} sectors, ${mfc.size} bytes)"
                        if (mfc.authenticateSectorWithKeyA(0, MifareClassic.KEY_DEFAULT)) {
                            val blockData = mfc.readBlock(mfc.sectorToBlock(0))
                            info += "\nSector 0 Block 0: ${blockData.joinToString(" ") { "%02X".format(it) }}"
                        } else {
                            info += "\nAuth failed (needs correct key)"
                        }
                    } catch (e: Exception) {
                        info += "\nMFC error: ${e.message}"
                    } finally {
                        try { mfc.close() } catch (_: Exception) {}
                    }
                }
            }

            Log.d(TAG, info)

            runOnUiThread {
                Toast.makeText(this, info.take(MAX_TOAST_LENGTH) + if (info.length > MAX_TOAST_LENGTH) "..." else "", Toast.LENGTH_LONG).show()
                findViewById<TextView>(R.id.nfc_status_text)?.text = info

                try {
                    val tagInfo = tagReader.readTag(it)
                    displayTagInfo(tagInfo)
                    logTagRead(tagInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading tag: ${e.message}")
                }
            }
        } ?: Log.w(TAG, "Null tag discovered")
    }

    private fun deepEmvRead(isoDep: IsoDep): String {
        val sb = StringBuilder("\n\n=== Deep EMV / Contactless Read ===\n")

        try {
            if (!isoDep.isConnected) isoDep.connect()
            isoDep.timeout = 15000

            sb.append("Connected (timeout 15s)\n")

            val ppse = "00A404000E325041592E5359532E444446303100".decodeHex()
            val ppseResp = safeTransceive(isoDep, ppse, sb)
            sb.append("PPSE: ${ppseResp.toHexString()}\n")

            val commonAids = listOf(
                "A0000000031010", "A0000000041010", "A00000002501",
                "A0000001523010", "A0000000651010", "A0000000032010",
                "A0000000042203", "A0000000038010", "A0000000043060"
            )

            var aidSuccess = false

            commonAids.forEach { aidStr ->
                val aid = aidStr.decodeHex()
                val select = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid
                val resp = safeTransceive(isoDep, select, sb)
                if (isSuccess(resp)) {
                    aidSuccess = true
                    sb.append("\nAID OK: $aidStr\n")
                    parseFciRecursive(resp.dropLast(2).toByteArray(), sb)
                }
            }

            if (!aidSuccess) sb.append("\nNo AID selected\n")

            val tags = listOf("5A", "5F24", "57", "9F0B", "9F17", "9F7F", "9F38")
            tags.forEach { tag ->
                val cmd = "80CA${tag}00".decodeHex()
                val resp = safeTransceive(isoDep, cmd, sb)
                if (isSuccess(resp)) {
                    val valBytes = resp.dropLast(2).toByteArray()
                    sb.append("GET $tag → ${valBytes.toHexString()}\n")
                    when (tag) {
                        "5A" -> sb.append("  PAN: ${valBytes.toHexString().replace("F", "")}\n")
                        "5F24" -> if (valBytes.size >= 3) sb.append("  Expiry: ${"%02X%02X".format(valBytes[0], valBytes[1])}\n")
                        "57" -> parseTrack2(valBytes, sb)
                    }
                }
            }

            dumpCommonRecords(isoDep, sb)

        } catch (e: Exception) {
            sb.append("\nDeep read failed: ${e.message}\n")
        } finally {
            try { isoDep.close() } catch (_: Throwable) {}
        }

        return sb.toString()
    }

    private fun safeTransceive(isoDep: IsoDep, cmd: ByteArray, sb: StringBuilder): ByteArray {
        Log.d(TAG, "APDU → ${cmd.toHexString()}")
        sb.append("→ ${cmd.toHexString()}\n")
        return try {
            val resp = isoDep.transceive(cmd)
            Log.d(TAG, "APDU ← ${resp.toHexString()}")
            sb.append("← ${resp.toHexString()}\n")
            resp
        } catch (e: Exception) {
            Log.e(TAG, "transceive fail", e)
            sb.append("ERROR: ${e.message}\n")
            byteArrayOf(0x6F.toByte(), 0x00)
        }
    }

    private fun isSuccess(resp: ByteArray): Boolean =
        resp.size >= 2 && resp[resp.size - 2].toInt() == 0x90 && resp.last().toInt() == 0x00

    private fun parseFciRecursive(data: ByteArray, sb: StringBuilder, indent: String = "  ") {
        var pos = 0
        while (pos < data.size) {
            val (tag, tLen) = readTag(data, pos)
            pos += tLen

            val (len, lLen) = readLength(data, pos)
            pos += lLen

            if (pos + len > data.size) break

            val value = data.copyOfRange(pos, pos + len)
            sb.append("${indent}Tag ${tag.toHexString()} len=$len: ${value.toHexString()}\n")

            if ((tag[0].toInt() and 0x20) != 0) {
                parseFciRecursive(value, sb, "$indent  ")
            }

            pos += len
        }
    }

    private fun dumpCommonRecords(isoDep: IsoDep, sb: StringBuilder) {
        sb.append("\nSFI/Record dump:\n")
        val sfis = listOf(1, 2, 3, 10, 11, 17, 18)
        sfis.forEach { sfi ->
            (1..5).forEach { rec ->
                val p1 = rec.toByte()
                val p2 = ((sfi shl 3) or 0x04).toByte()
                val cmd = byteArrayOf(0x00, 0xB2.toByte(), p1, p2, 0x00)
                val resp = safeTransceive(isoDep, cmd, sb)
                if (isSuccess(resp)) {
                    sb.append("  SFI $sfi REC $rec → ${resp.dropLast(2).toHexString()}\n")
                }
            }
        }
    }

    private fun parseTrack2(raw: ByteArray, sb: StringBuilder) {
        val digits = buildString {
            raw.forEach { b ->
                append(((b.toInt() shr 4) and 0x0F).toString(16).uppercase())
                append((b.toInt() and 0x0F).toString(16).uppercase())
            }
        }
        val sep = digits.indexOf('D')
        if (sep > 0) {
            val pan = digits.substring(0, sep)
            val rest = digits.substring(sep + 1)
            val exp = if (rest.length >= 4) rest.substring(0, 4) else "?"
            val svc = if (rest.length >= 7) rest.substring(4, 7) else "?"
            sb.append("  Track2:\n    PAN: $pan\n    Expiry: $exp\n    Service: $svc\n")
        } else {
            sb.append("  Track2 raw: $digits\n")
        }
    }

    private fun readTag(data: ByteArray, offset: Int): Pair<ByteArray, Int> {
        var pos = offset
        val first = data[pos++]
        return if ((first.toInt() and 0x1F) == 0x1F && pos < data.size) {
            byteArrayOf(first, data[pos++]) to 2
        } else {
            byteArrayOf(first) to 1
        }
    }

    private fun readLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        var pos = offset
        var lb = data[pos++].toInt() and 0xFF
        if (lb < 0x80) return lb to 1
        val nb = lb and 0x7F
        var len = 0
        repeat(nb) { len = (len shl 8) or (data[pos++].toInt() and 0xFF) }
        return len to (1 + nb)
    }

    // ────────────────────────────────────────────────
    // Original methods (unchanged)
    // ────────────────────────────────────────────────

    private fun showDisclaimerDialog(prefs: android.content.SharedPreferences) {
        AlertDialog.Builder(this)
            .setTitle(R.string.disclaimer_title)
            .setMessage(R.string.disclaimer_message)
            .setCancelable(false)
            .setPositiveButton(R.string.disclaimer_accept) { dialog, _ ->
                prefs.edit().putBoolean("disclaimer_accepted", true).apply()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.disclaimer_decline) { _, _ ->
                finish()
            }
            .show()
    }

    private fun setupUI() {
        binding.writeTagButton.setOnClickListener {
            startActivity(Intent(this, WriteTagActivity::class.java))
        }

        binding.emulateButton.setOnClickListener {
            startActivity(Intent(this, EmulateCardActivity::class.java))
        }

        binding.backupManagerButton.setOnClickListener {
            startActivity(Intent(this, BackupManagerActivity::class.java))
        }

        binding.protectedTagButton.setOnClickListener {
            startActivity(Intent(this, ProtectedTagActivity::class.java))
        }

        binding.diagnosticsButton.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        binding.backupButton.setOnClickListener {
            Toast.makeText(this, "Touch a card to backup", Toast.LENGTH_SHORT).show()
        }

        binding.apduConsoleButton.setOnClickListener {
            startActivity(Intent(this, com.nfc.reader.ui.ApduConsoleActivity::class.java))
        }
    }

    private fun handleIncomingNfcIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action

        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val uidHex = tag.id?.joinToString(":") { "%02X".format(it) } ?: "No UID"
                val techList = tag.techList?.joinToString(", ") ?: "No techs"

                val message = "TAG DETECTED 🔥\nUID: $uidHex\nTechs: $techList"

                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }

                Log.d(TAG, message)

                findViewById<TextView>(R.id.nfc_status_text)?.text = message

                readTag(tag)
            } else {
                Toast.makeText(this, "Intent had no Tag extra!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readTag(tag: Tag) {
        try {
            val tagInfo = tagReader.readTag(tag)
            displayTagInfo(tagInfo)
            logTagRead(tagInfo)
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading tag: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun displayTagInfo(tagInfo: NfcTagReader.TagInfo) {
        binding.statusText.text = getString(R.string.tag_detected)
        binding.tagInfoScrollView.visibility = View.VISIBLE
        binding.backupButton.visibility = View.VISIBLE

        val info = buildString {
            appendLine("${getString(R.string.tag_uid)} ${tagInfo.uid}")
            appendLine()
            appendLine("${getString(R.string.tag_type)} ${tagInfo.tagType}")
            appendLine("ISO Standard: ${tagInfo.isoStandard}")
            appendLine()

            appendLine("${getString(R.string.tag_tech)}")
            tagInfo.technologies.forEach { tech ->
                appendLine("  • ${tech.substringAfterLast('.')}")
            }
            appendLine()

            tagInfo.atqa?.let { appendLine("ATQA (ISO 14443-A): $it") }
            tagInfo.sak?.let { appendLine("SAK (ISO 14443-A): ${it.toHexString()}") }
            tagInfo.applicationData?.let { appendLine("App Data (ISO 14443-B): $it") }
            tagInfo.dsfId?.let { appendLine("DSFID (ISO 15693): $it") }
            tagInfo.maxTransceiveLength?.let { appendLine("Max Transceive: $it bytes") }
            appendLine()

            tagInfo.memorySize?.let { appendLine("${getString(R.string.tag_size)} $it bytes") }
            appendLine("Writable: ${if (tagInfo.isWritable) "Yes" else "No"}")
            appendLine()

            tagInfo.ndefInfo?.let { ndef ->
                appendLine(getString(R.string.ndef_message))
                appendLine("Records: ${ndef.records.size}")
                ndef.records.forEachIndexed { index, record ->
                    appendLine("  Record $index:")
                    appendLine("    Type: ${record.type}")
                    appendLine("    Payload: ${record.payload}")
                }
            } ?: appendLine(getString(R.string.no_ndef))
        }

        binding.tagInfoText.text = info
    }

    private fun logTagRead(tagInfo: NfcTagReader.TagInfo) {
        lifecycleScope.launch {
            val log = NfcLog(
                uid = tagInfo.uid,
                tagType = tagInfo.tagType,
                technologies = tagInfo.technologies.joinToString(","),
                isoStandard = tagInfo.isoStandard,
                hasNdef = tagInfo.ndefInfo != null,
                ndefMessage = tagInfo.ndefInfo?.message,
                ndefRecords = tagInfo.ndefInfo?.let { gson.toJson(it.records) },
                atqa = tagInfo.atqa,
                sak = tagInfo.sak,
                applicationData = tagInfo.applicationData,
                dsfId = tagInfo.dsfId,
                maxTransceiveLength = tagInfo.maxTransceiveLength,
                memorySize = tagInfo.memorySize,
                isWritable = tagInfo.isWritable,
                operation = "READ"
            )
            database.nfcLogDao().insert(log)
        }
    }

    private fun openNfcSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (ex: Exception) {
                Toast.makeText(
                    this,
                    "Unable to open settings. Enable NFC manually.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun disableButtons() {
        binding.writeTagButton.isEnabled = false
        binding.emulateButton.isEnabled = false
        binding.backupManagerButton.isEnabled = false
        binding.protectedTagButton.isEnabled = false
        binding.diagnosticsButton.isEnabled = false
        binding.backupButton.isEnabled = false
        binding.apduConsoleButton.isEnabled = false
    }
}

// ────────────────────────────────────────────────
// Extensions (add here or in utils file)
fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
