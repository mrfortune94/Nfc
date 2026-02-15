package com.nfc.reader.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.nfc.reader.R
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.data.NfcLog
import com.nfc.reader.databinding.ActivityApduConsoleBinding
import com.nfc.reader.nfc.ApduHandler
import com.nfc.reader.utils.toHexString
import kotlinx.coroutines.launch

/**
 * APDU Console Activity
 * Advanced interface for sending ISO 7816 APDU commands
 */
class ApduConsoleActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityApduConsoleBinding
    private var nfcAdapter: NfcAdapter? = null
    private val apduHandler = ApduHandler()
    private lateinit var database: NfcDatabase
    private val gson = Gson()
    
    private var currentTag: Tag? = null
    private val apduHistory = mutableListOf<Pair<String, String>>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApduConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.apdu_console)
        
        database = NfcDatabase.getDatabase(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        setupUI()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                currentTag = it
                binding.responseText.text = "Tag detected: ${it.id.toHexString()}\nReady to send commands."
            }
        }
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
        binding.sendButton.setOnClickListener {
            sendApduCommand()
        }
        
        // Quick command buttons
        binding.selectBtn.setOnClickListener {
            binding.commandInput.setText("00A4040007A0000000031010")
        }
        
        binding.readBtn.setOnClickListener {
            binding.commandInput.setText("00B0000000")
        }
        
        binding.getDataBtn.setOnClickListener {
            binding.commandInput.setText("00CA000000")
        }
    }
    
    private fun sendApduCommand() {
        val tag = currentTag
        if (tag == null) {
            Toast.makeText(this, "No tag detected. Please scan a tag first.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val commandText = binding.commandInput.text?.toString()
        if (commandText.isNullOrBlank()) {
            Toast.makeText(this, "Please enter an APDU command", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val commandBytes = apduHandler.parseHexCommand(commandText)
            val result = apduHandler.sendRawApdu(tag, commandBytes)
            
            result.fold(
                onSuccess = { response ->
                    val responseText = buildString {
                        appendLine("Command: $commandText")
                        appendLine("Data: ${response.data.toHexString()}")
                        appendLine("SW: ${String.format("%02X%02X", response.sw1, response.sw2)}")
                        appendLine("Status: ${if (response.isSuccess) "Success" else "Error"}")
                        appendLine()
                    }
                    
                    apduHistory.add(commandText to response.toString())
                    displayResponse(responseText)
                    logApduCommand(tag, commandText, response.toString())
                },
                onFailure = { error ->
                    val errorText = "Error: ${error.message}"
                    displayResponse(errorText)
                    Toast.makeText(this, errorText, Toast.LENGTH_LONG).show()
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid APDU format: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun displayResponse(text: String) {
        val currentText = binding.responseText.text.toString()
        binding.responseText.text = if (currentText.isEmpty()) {
            text
        } else {
            "$currentText\n$text"
        }
    }
    
    private fun logApduCommand(tag: Tag, command: String, response: String) {
        lifecycleScope.launch {
            val apduLog = mapOf(
                "command" to command,
                "response" to response,
                "timestamp" to System.currentTimeMillis()
            )
            
            val log = NfcLog(
                uid = tag.id.toHexString(),
                tagType = "ISO-DEP",
                technologies = tag.techList.joinToString(","),
                isoStandard = "ISO/IEC 7816",
                apduCommands = gson.toJson(listOf(apduLog)),
                operation = "APDU"
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
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }
    
    private fun disableForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
