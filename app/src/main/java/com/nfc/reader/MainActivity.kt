package com.nfc.reader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.devnied.emvnfccard.model.EmvCard
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.google.gson.Gson
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.data.NfcLog
import com.nfc.reader.databinding.ActivityMainBinding
import com.nfc.reader.nfc.NfcIsoDepProvider
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
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        database = NfcDatabase.getDatabase(this)
        
        // Show disclaimer on first launch
        val prefs = getSharedPreferences("nfc_pro_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("disclaimer_accepted", false)) {
            showDisclaimerDialog(prefs)
        }
        
        // Check NFC availability
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            binding.statusText.text = getString(R.string.nfc_not_supported)
            disableButtons()
            return
        }
        
        if (!nfcAdapter!!.isEnabled) {
            binding.statusText.text = getString(R.string.nfc_disabled)
        }
        
        setupUI()
        
        // Check if launched by NFC
        handleIncomingNfcIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // This is CRITICAL for when app is already open
        setIntent(intent)
        handleIncomingNfcIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        
        // Quick NFC status check
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            Toast.makeText(this, getString(R.string.nfc_hardware_not_found), Toast.LENGTH_LONG).show()
            return
        } else if (!adapter.isEnabled) {
            Toast.makeText(this, getString(R.string.nfc_is_off), Toast.LENGTH_LONG).show()
        }
        
        // Enable Reader Mode for all common NFC techs (more reliable than foreground dispatch)
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }

        nfcAdapter?.enableReaderMode(this, this, flags, extras)
        Log.d(TAG, "Reader mode ENABLED")
    }
    
    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        Log.d(TAG, "Reader mode DISABLED")
    }
    
    /**
     * Called when a tag is discovered via Reader Mode.
     * This fires EVERY time a tag enters the NFC field (more reliable than intent-based).
     * Supports EMV/PayPass/payWave contactless cards, NDEF, MifareClassic, and other NFC techs.
     */
    override fun onTagDiscovered(tag: Tag?) {
        tag?.let {
            val uidHex = it.id?.joinToString(":") { byte -> "%02X".format(byte) } ?: "No UID"
            val techs = it.techList?.joinToString(", ") ?: "No techs"

            var info = "TAG DETECTED 🔥\nUID: $uidHex\nTechs: $techs"

            // Try EMV parsing for IsoDep cards (contactless payment cards)
            val isoDep = IsoDep.get(it)
            if (isoDep != null) {
                info += parseEmvCard(isoDep)
            } else {
                // Not an EMV card, try other technologies
                
                // Read NDEF if available (text/URL/AAR)
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
                                        // NDEF text record: first byte is status (bit 7 = encoding, bits 5-0 = lang code length)
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

                // Read from MifareClassic if available (access cards, keyfobs)
                MifareClassic.get(it)?.let { mfc ->
                    try {
                        mfc.connect()
                        info += "\n\nMIFARE Classic (${mfc.sectorCount} sectors, ${mfc.size} bytes)"
                        // Try default key on sector 0 for reading
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

            // Log and update UI
            Log.d(TAG, info)
            runOnUiThread {
                Toast.makeText(this, info.take(MAX_TOAST_LENGTH) + if (info.length > MAX_TOAST_LENGTH) "..." else "", Toast.LENGTH_LONG).show()
                findViewById<TextView>(R.id.nfc_status_text)?.text = info
                
                // Also process through the existing tag reader for full display
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
    
    /**
     * Parse EMV contactless payment card using the EMV NFC Card library.
     * Supports Visa payWave, Mastercard PayPass, Amex ExpressPay, etc.
     */
    private fun parseEmvCard(isoDep: IsoDep): String {
        val sb = StringBuilder()
        
        try {
            // Connect to IsoDep
            if (!isoDep.isConnected) {
                isoDep.connect()
            }
            isoDep.timeout = 5000
            
            sb.append("\n\n=== EMV Contactless Card ===")
            sb.append("\nHistorical bytes: ${isoDep.historicalBytes?.toHexString() ?: "N/A"}")
            sb.append("\nMax transceive: ${isoDep.maxTransceiveLength} bytes")

            val provider = NfcIsoDepProvider(isoDep)
            
            val config = EmvTemplate.Config()
                .setContactLess(true)
                .setReadAllAids(true)
                .setReadTransactions(false)
                .setRemoveDefaultParsers(false)

            val parser = EmvTemplate.Builder()
                .setProvider(provider)
                .setConfig(config)
                .build()

            val card: EmvCard? = parser.readEmvCard()

            if (card != null) {
                sb.append("\n\n--- Card Details ---")
                
                // Card scheme/type (VISA, MASTERCARD, AMEX, etc.)
                sb.append("\nCard Type: ${card.type?.name ?: "Unknown"}")
                
                // Application label (from applications list)
                card.applications?.firstOrNull()?.applicationLabel?.let { label ->
                    sb.append("\nApplication: $label")
                }

                // PAN (Primary Account Number / Card Number)
                card.cardNumber?.let { pan ->
                    sb.append("\nPAN: $pan")
                } ?: sb.append("\nPAN: Not available (masked)")

                // Expiry date
                card.expireDate?.let { exp ->
                    val sdf = SimpleDateFormat("MM/yy", Locale.getDefault())
                    sb.append("\nExpiry: ${sdf.format(exp)}")
                } ?: sb.append("\nExpiry: Not found")

                // Cardholder name (if present)
                card.holderLastname?.let { lastName ->
                    val firstName = card.holderFirstname ?: ""
                    sb.append("\nCardholder: $firstName $lastName".trim())
                }

                // AID used (from applications list)
                card.applications?.firstOrNull()?.aid?.let { aid ->
                    sb.append("\nAID: ${aid.toHexString()}")
                }

                // Track 2 equivalent data
                card.track2?.let { track2 ->
                    sb.append("\nTrack 2: ${track2.raw?.toHexString() ?: "N/A"}")
                }

                Log.d(TAG, "EMV Card parsed successfully: ${card.type?.name}")
            } else {
                sb.append("\n\nFailed to parse EMV card data")
                sb.append("\n(Card may not support contactless read)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "EMV parsing error: ${e.message}", e)
            sb.append("\n\nEMV Parse Error: ${e.message}")
        } finally {
            try {
                isoDep.close()
            } catch (_: Exception) {}
        }
        
        return sb.toString()
    }
    
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
            // Backup functionality would be triggered here
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
                val uidBytes = tag.id
                val uidHex = uidBytes?.joinToString(":") { "%02X".format(it) } ?: "No UID"
                val techList = tag.techList?.joinToString(", ") ?: "No techs"

                val message = "TAG DETECTED 🔥\nUID: $uidHex\nTechs: $techList"

                // Show it big and obvious
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }

                Log.d(TAG, message)

                // Update the NFC status text
                findViewById<TextView>(R.id.nfc_status_text)?.text = message
                
                // Process the tag
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
            
            tagInfo.atqa?.let {
                appendLine("ATQA (ISO 14443-A): $it")
            }
            tagInfo.sak?.let {
                appendLine("SAK (ISO 14443-A): ${it.toHexString()}")
            }
            tagInfo.applicationData?.let {
                appendLine("App Data (ISO 14443-B): $it")
            }
            tagInfo.dsfId?.let {
                appendLine("DSFID (ISO 15693): $it")
            }
            tagInfo.maxTransceiveLength?.let {
                appendLine("Max Transceive: $it bytes")
            }
            appendLine()
            
            tagInfo.memorySize?.let {
                appendLine("${getString(R.string.tag_size)} $it bytes")
            }
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
