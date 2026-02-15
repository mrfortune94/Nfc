package com.nfc.reader.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.nfc.reader.R
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.databinding.ActivityDiagnosticsBinding
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Diagnostics Activity
 * Shows offline logs and provides export functionality
 */
class DiagnosticsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDiagnosticsBinding
    private lateinit var database: NfcDatabase
    private val gson = Gson()
    private lateinit var adapter: LogsAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_diagnostics)
        
        database = NfcDatabase.getDatabase(this)
        
        setupRecyclerView()
        setupButtons()
        loadLogs()
    }
    
    private fun setupRecyclerView() {
        adapter = LogsAdapter()
        binding.logsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.logsRecyclerView.adapter = adapter
    }
    
    private fun setupButtons() {
        binding.exportButton.setOnClickListener {
            exportLogs()
        }
        
        binding.clearButton.setOnClickListener {
            showClearConfirmation()
        }
    }
    
    private fun loadLogs() {
        lifecycleScope.launch {
            database.nfcLogDao().getAllLogs().collect { logs ->
                if (logs.isEmpty()) {
                    binding.emptyText.visibility = View.VISIBLE
                    binding.logsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyText.visibility = View.GONE
                    binding.logsRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(logs)
                }
            }
        }
    }
    
    private fun exportLogs() {
        lifecycleScope.launch {
            try {
                val logs = mutableListOf<com.nfc.reader.data.NfcLog>()
                database.nfcLogDao().getAllLogs().collect { logList ->
                    logs.clear()
                    logs.addAll(logList)
                }
                
                if (logs.isEmpty()) {
                    Toast.makeText(this@DiagnosticsActivity, R.string.no_logs, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Export as JSON
                val json = gson.toJson(logs)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "nfc_logs_$timestamp.json"
                
                val exportDir = File(getExternalFilesDir(null), "exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                
                val file = File(exportDir, fileName)
                file.writeText(json)
                
                Toast.makeText(
                    this@DiagnosticsActivity,
                    "${getString(R.string.log_exported)} ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
                
                // Optionally share the file
                shareFile(file)
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@DiagnosticsActivity,
                    "Export failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share logs"))
        } catch (e: Exception) {
            // File provider not configured, skip sharing
        }
    }
    
    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Logs")
            .setMessage("Are you sure you want to delete all logs?")
            .setPositiveButton("Clear") { _, _ ->
                clearLogs()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun clearLogs() {
        lifecycleScope.launch {
            database.nfcLogDao().deleteAll()
            Toast.makeText(this@DiagnosticsActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
