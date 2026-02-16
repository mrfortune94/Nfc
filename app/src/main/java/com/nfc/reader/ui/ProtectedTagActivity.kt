package com.nfc.reader.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nfc.reader.R
import com.nfc.reader.databinding.ActivityProtectedTagBinding
import com.nfc.reader.nfc.ProtectedTagHandler
import com.nfc.reader.utils.toHexString

/**
 * Activity for working with security-protected NFC tags
 * Supports authentication, key management, and read/write operations
 * 
 * IMPORTANT: Only for use with tags that you own
 * Educational and research purposes only
 */
class ProtectedTagActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProtectedTagBinding
    private var nfcAdapter: NfcAdapter? = null
    private val protectedTagHandler = ProtectedTagHandler()
    
    private var currentTag: Tag? = null
    private var selectedOperation = Operation.READ
    private var customKey: ByteArray? = null
    private var selectedKeyType = ProtectedTagHandler.KeyType.KEY_A
    private var selectedSector = 0

    enum class Operation {
        READ, WRITE, AUTH_TEST, DUMP_ALL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProtectedTagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.protected_tag_title)
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupUI()
    }

    private fun setupUI() {
        // Operation selection
        val operations = arrayOf(
            getString(R.string.op_read_sector),
            getString(R.string.op_write_block),
            getString(R.string.op_test_auth),
            getString(R.string.op_dump_all)
        )
        val operationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, operations)
        binding.operationSpinner.adapter = operationAdapter
        binding.operationSpinner.setSelection(0)

        binding.operationSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedOperation = Operation.entries[position]
                updateUIForOperation()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Key type selection
        binding.keyTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedKeyType = when (checkedId) {
                R.id.keyARadio -> ProtectedTagHandler.KeyType.KEY_A
                R.id.keyBRadio -> ProtectedTagHandler.KeyType.KEY_B
                else -> ProtectedTagHandler.KeyType.KEY_A
            }
        }

        // Sector selection
        binding.sectorSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                selectedSector = progress
                binding.sectorValueText.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Default key buttons
        binding.useDefaultKeyButton.setOnClickListener {
            binding.keyInput.setText("FFFFFFFFFFFF")
            customKey = ProtectedTagHandler.KEY_DEFAULT
        }

        binding.useMadKeyButton.setOnClickListener {
            binding.keyInput.setText("A0A1A2A3A4A5")
            customKey = ProtectedTagHandler.KEY_MAD
        }

        binding.useNfcForumKeyButton.setOnClickListener {
            binding.keyInput.setText("D3F7D3F7D3F7")
            customKey = ProtectedTagHandler.KEY_NFC_FORUM
        }

        // Execute button
        binding.executeButton.setOnClickListener {
            if (currentTag == null) {
                Toast.makeText(this, R.string.scan_tag_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val keyHex = binding.keyInput.text.toString()
            if (keyHex.isBlank() || keyHex.length != 12) {
                Toast.makeText(this, R.string.invalid_key, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            try {
                customKey = ProtectedTagHandler.hexStringToByteArray(keyHex)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.invalid_key_format, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            executeOperation()
        }

        // Write data input
        binding.writeDataInput.visibility = View.GONE
    }

    private fun updateUIForOperation() {
        when (selectedOperation) {
            Operation.READ -> {
                binding.sectorSelectionLayout.visibility = View.VISIBLE
                binding.writeDataLayout.visibility = View.GONE
                binding.executeButton.text = getString(R.string.read_sector)
            }
            Operation.WRITE -> {
                binding.sectorSelectionLayout.visibility = View.VISIBLE
                binding.writeDataLayout.visibility = View.VISIBLE
                binding.executeButton.text = getString(R.string.write_block)
            }
            Operation.AUTH_TEST -> {
                binding.sectorSelectionLayout.visibility = View.VISIBLE
                binding.writeDataLayout.visibility = View.GONE
                binding.executeButton.text = getString(R.string.test_authentication)
            }
            Operation.DUMP_ALL -> {
                binding.sectorSelectionLayout.visibility = View.GONE
                binding.writeDataLayout.visibility = View.GONE
                binding.executeButton.text = getString(R.string.dump_all_sectors)
            }
        }
    }

    private fun executeOperation() {
        val tag = currentTag ?: return
        val key = customKey ?: return

        when (selectedOperation) {
            Operation.READ -> readSector(tag, key)
            Operation.WRITE -> writeBlock(tag, key)
            Operation.AUTH_TEST -> testAuthentication(tag, key)
            Operation.DUMP_ALL -> dumpAllSectors(tag, key)
        }
    }

    private fun readSector(tag: Tag, key: ByteArray) {
        val result = protectedTagHandler.readMifareClassicSector(
            tag, selectedSector, key, selectedKeyType
        )
        
        when (result) {
            is ProtectedTagHandler.AuthResult.Success -> {
                val blocks = result.data["blocks"] as? List<*>
                val output = buildString {
                    appendLine("Sector $selectedSector read successfully:")
                    appendLine()
                    blocks?.forEachIndexed { index, block ->
                        appendLine("Block ${index}: $block")
                    }
                }
                showResult(result.message, output)
            }
            is ProtectedTagHandler.AuthResult.Error -> {
                showResult("Error", result.message)
            }
        }
    }

    private fun writeBlock(tag: Tag, key: ByteArray) {
        val dataHex = binding.writeDataInput.text.toString()
        if (dataHex.isBlank() || dataHex.length != 32) {
            Toast.makeText(this, R.string.invalid_block_data, Toast.LENGTH_SHORT).show()
            return
        }

        val blockNum = binding.blockNumberInput.text.toString().toIntOrNull()
        if (blockNum == null || blockNum < 0) {
            Toast.makeText(this, R.string.invalid_block_number, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val data = ProtectedTagHandler.hexStringToByteArray(dataHex)
            val result = protectedTagHandler.writeMifareClassicBlock(
                tag, blockNum, key, selectedKeyType, data
            )
            
            when (result) {
                is ProtectedTagHandler.AuthResult.Success -> {
                    showResult("Success", result.message)
                }
                is ProtectedTagHandler.AuthResult.Error -> {
                    showResult("Error", result.message)
                }
            }
        } catch (e: Exception) {
            showResult("Error", "Failed to parse data: ${e.message}")
        }
    }

    private fun testAuthentication(tag: Tag, key: ByteArray) {
        val result = protectedTagHandler.authenticateMifareClassic(
            tag, selectedSector, key, selectedKeyType
        )
        
        when (result) {
            is ProtectedTagHandler.AuthResult.Success -> {
                showResult("Authentication Success", 
                    "Key ${selectedKeyType.name} works for sector $selectedSector")
            }
            is ProtectedTagHandler.AuthResult.Error -> {
                showResult("Authentication Failed", result.message)
            }
        }
    }

    private fun dumpAllSectors(tag: Tag, key: ByteArray) {
        // Create key map with same key for all sectors
        val keyMap = (0..15).associate { sector ->
            sector to Pair(key, selectedKeyType)
        }
        
        val result = protectedTagHandler.readAllMifareClassicSectors(tag, keyMap)
        
        when (result) {
            is ProtectedTagHandler.AuthResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val sectors = result.data["sectors"] as? Map<Int, List<String>>
                val output = buildString {
                    appendLine("Card Dump Results:")
                    appendLine(result.message)
                    appendLine()
                    sectors?.forEach { (sector, blocks) ->
                        appendLine("=== Sector $sector ===")
                        blocks.forEachIndexed { index, block ->
                            appendLine("  Block ${index}: $block")
                        }
                        appendLine()
                    }
                }
                showResult("Dump Complete", output)
            }
            is ProtectedTagHandler.AuthResult.Error -> {
                showResult("Error", result.message)
            }
        }
    }

    private fun showResult(title: String, message: String) {
        binding.resultText.text = message
        binding.resultText.visibility = View.VISIBLE
        
        if (message.length > 500) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { 
                currentTag = it
                updateTagInfo(it)
            }
        }
    }

    private fun updateTagInfo(tag: Tag) {
        val uid = tag.id.toHexString()
        val techList = tag.techList.joinToString("\n") { it.substringAfterLast('.') }
        
        binding.tagInfoCard.visibility = View.VISIBLE
        binding.tagUidText.text = getString(R.string.tag_uid_format, uid)
        binding.tagTechText.text = techList
        
        // Check if it's a Mifare Classic card
        if (tag.techList.contains("android.nfc.tech.MifareClassic")) {
            binding.tagTypeText.text = getString(R.string.mifare_classic_detected)
            binding.executeButton.isEnabled = true
        } else if (tag.techList.contains("android.nfc.tech.IsoDep")) {
            binding.tagTypeText.text = getString(R.string.iso_dep_detected)
            binding.executeButton.isEnabled = true
        } else {
            binding.tagTypeText.text = getString(R.string.unsupported_tag_type)
            binding.executeButton.isEnabled = false
        }
        
        Toast.makeText(this, R.string.tag_detected_ready, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
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
