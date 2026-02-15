package com.nfc.reader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.data.NfcLog
import com.nfc.reader.databinding.ActivityMainBinding
import com.nfc.reader.nfc.NfcTagReader
import com.nfc.reader.ui.DiagnosticsActivity
import com.nfc.reader.ui.WriteTagActivity
import com.nfc.reader.utils.toHexString
import kotlinx.coroutines.launch

/**
 * Main Activity for NFC Tag Reading
 * Supports ISO/IEC 14443-A/B, ISO/IEC 15693, ISO/IEC 18092, and other standards
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private val tagReader = NfcTagReader()
    private val gson = Gson()
    
    private lateinit var database: NfcDatabase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        database = NfcDatabase.getDatabase(this)
        
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
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }
    
    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }
    
    private fun setupUI() {
        binding.writeTagButton.setOnClickListener {
            startActivity(Intent(this, WriteTagActivity::class.java))
        }
        
        binding.diagnosticsButton.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        
        binding.backupButton.setOnClickListener {
            // Backup functionality would be triggered here
            Toast.makeText(this, "Touch a card to backup", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleIntent(intent: Intent) {
        val action = intent.action
        
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { readTag(it) }
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
    
    private fun enableForegroundDispatch() {
        nfcAdapter?.let { adapter ->
            val intent = Intent(this, javaClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_MUTABLE
            )
            
            val filters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            )
            
            adapter.enableForegroundDispatch(this, pendingIntent, filters, null)
        }
    }
    
    private fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    private fun disableButtons() {
        binding.writeTagButton.isEnabled = false
        binding.diagnosticsButton.isEnabled = false
        binding.backupButton.isEnabled = false
    }
}
