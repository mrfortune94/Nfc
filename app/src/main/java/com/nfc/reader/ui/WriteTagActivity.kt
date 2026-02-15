package com.nfc.reader.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.nfc.reader.R
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.data.NfcLog
import com.nfc.reader.databinding.ActivityWriteTagBinding
import com.nfc.reader.nfc.NfcNdefWriter
import kotlinx.coroutines.launch

/**
 * Activity for writing NDEF data to NFC tags
 * Supports: Text, URL, and Android Application Records
 */
class WriteTagActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityWriteTagBinding
    private var nfcAdapter: NfcAdapter? = null
    private val ndefWriter = NfcNdefWriter()
    private lateinit var database: NfcDatabase
    
    private var writeMode = WriteMode.TEXT
    private var pendingWriteData: String? = null
    private var pendingPackageName: String? = null
    
    enum class WriteMode {
        TEXT, URL, APP
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWriteTagBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        database = NfcDatabase.getDatabase(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        setupUI()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { writeToTag(it) }
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
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> selectTextMode()
                    1 -> selectUrlMode()
                    2 -> selectAppMode()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        binding.writeButton.setOnClickListener {
            prepareWrite()
        }
    }
    
    private fun selectTextMode() {
        writeMode = WriteMode.TEXT
        binding.inputLayout.hint = getString(R.string.hint_text)
        binding.packageLayout.visibility = View.GONE
    }
    
    private fun selectUrlMode() {
        writeMode = WriteMode.URL
        binding.inputLayout.hint = getString(R.string.hint_url)
        binding.packageLayout.visibility = View.GONE
    }
    
    private fun selectAppMode() {
        writeMode = WriteMode.APP
        binding.inputLayout.hint = getString(R.string.hint_text)
        binding.packageLayout.visibility = View.VISIBLE
    }
    
    private fun prepareWrite() {
        val inputText = binding.inputText.text?.toString()
        
        if (inputText.isNullOrBlank()) {
            Toast.makeText(this, "Please enter data to write", Toast.LENGTH_SHORT).show()
            return
        }
        
        pendingWriteData = inputText
        
        if (writeMode == WriteMode.APP) {
            val packageName = binding.packageText.text?.toString()
            if (packageName.isNullOrBlank()) {
                Toast.makeText(this, "Please enter package name", Toast.LENGTH_SHORT).show()
                return
            }
            pendingPackageName = packageName
        }
        
        binding.statusText.text = getString(R.string.write_instruction)
        Toast.makeText(this, getString(R.string.write_instruction), Toast.LENGTH_LONG).show()
    }
    
    private fun writeToTag(tag: Tag) {
        val data = pendingWriteData ?: return
        
        binding.statusText.text = "Writing..."
        
        val result = when (writeMode) {
            WriteMode.TEXT -> {
                val packageName = pendingPackageName
                if (packageName != null) {
                    ndefWriter.writeTextWithApp(tag, data, packageName)
                } else {
                    ndefWriter.writeText(tag, data)
                }
            }
            WriteMode.URL -> {
                val packageName = pendingPackageName
                if (packageName != null) {
                    ndefWriter.writeUrlWithApp(tag, data, packageName)
                } else {
                    ndefWriter.writeUrl(tag, data)
                }
            }
            WriteMode.APP -> {
                ndefWriter.writeAppLaunch(tag, pendingPackageName!!)
            }
        }
        
        when (result) {
            is NfcNdefWriter.WriteResult.Success -> {
                binding.statusText.text = getString(R.string.write_success)
                Toast.makeText(this, R.string.write_success, Toast.LENGTH_SHORT).show()
                logWrite(tag, data)
                pendingWriteData = null
                pendingPackageName = null
            }
            is NfcNdefWriter.WriteResult.Error -> {
                binding.statusText.text = result.message
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun logWrite(tag: Tag, data: String) {
        lifecycleScope.launch {
            val log = NfcLog(
                uid = tag.id.joinToString("") { "%02X".format(it) },
                tagType = "Written",
                technologies = tag.techList.joinToString(","),
                isoStandard = "Unknown",
                hasNdef = true,
                ndefMessage = data,
                operation = "WRITE"
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
