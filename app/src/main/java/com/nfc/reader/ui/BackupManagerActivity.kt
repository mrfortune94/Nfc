package com.nfc.reader.ui

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nfc.reader.R
import com.nfc.reader.data.CardBackup
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.databinding.ActivityBackupManagerBinding
import com.nfc.reader.nfc.CardBackupHandler
import kotlinx.coroutines.launch

/**
 * Activity for managing NFC card backups
 * Allows viewing, creating, and deleting card backups
 * 
 * IMPORTANT: Only for use with cards owned by the user
 */
class BackupManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupManagerBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var database: NfcDatabase
    private val cardBackupHandler = CardBackupHandler()
    
    private lateinit var backupAdapter: BackupAdapter
    private var pendingBackupName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.backup_manager_title)
        }

        database = NfcDatabase.getDatabase(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupRecyclerView()
        setupUI()
        loadBackups()
    }

    private fun setupRecyclerView() {
        backupAdapter = BackupAdapter(
            onViewClick = { backup -> showBackupDetails(backup) },
            onDeleteClick = { backup -> confirmDeleteBackup(backup) },
            onEmulateClick = { backup -> startEmulateActivity(backup) }
        )
        
        binding.backupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BackupManagerActivity)
            adapter = backupAdapter
        }
    }

    private fun setupUI() {
        binding.createBackupButton.setOnClickListener {
            showCreateBackupDialog()
        }

        binding.exportAllButton.setOnClickListener {
            exportAllBackups()
        }
    }

    private fun loadBackups() {
        lifecycleScope.launch {
            database.cardBackupDao().getAllBackups().collect { backups ->
                if (backups.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.backupsRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.backupsRecyclerView.visibility = View.VISIBLE
                    backupAdapter.submitList(backups)
                }
                binding.backupCountText.text = getString(R.string.backup_count_format, backups.size)
            }
        }
    }

    private fun showCreateBackupDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.enter_card_name)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.create_backup)
            .setMessage(R.string.create_backup_message)
            .setView(input)
            .setPositiveButton(R.string.scan_card) { _, _ ->
                val name = input.text.toString().ifBlank { "Card Backup" }
                pendingBackupName = name
                binding.scanInstructionText.visibility = View.VISIBLE
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBackupDetails(backup: CardBackup) {
        val details = buildString {
            appendLine("Card Name: ${backup.cardName}")
            appendLine("UID: ${backup.uid}")
            appendLine("Type: ${backup.cardType}")
            appendLine("ISO Standard: ${backup.isoStandard}")
            appendLine("Memory Size: ${backup.memorySize} bytes")
            appendLine("Technologies: ${backup.technologies}")
            appendLine("Can Emulate: ${if (backup.canEmulate) "Yes" else "No"}")
            appendLine()
            appendLine("Backed up: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(backup.timestamp))}")
        }

        AlertDialog.Builder(this)
            .setTitle(backup.cardName)
            .setMessage(details)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.view_raw_data) { _, _ ->
                showRawData(backup)
            }
            .show()
    }

    private fun showRawData(backup: CardBackup) {
        AlertDialog.Builder(this)
            .setTitle(R.string.raw_data)
            .setMessage(backup.sectorData)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmDeleteBackup(backup: CardBackup) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_backup)
            .setMessage(getString(R.string.delete_backup_confirm, backup.cardName))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteBackup(backup)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteBackup(backup: CardBackup) {
        lifecycleScope.launch {
            database.cardBackupDao().delete(backup)
            Toast.makeText(this@BackupManagerActivity, R.string.backup_deleted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startEmulateActivity(backup: CardBackup) {
        val intent = Intent(this, EmulateCardActivity::class.java)
        // In a real implementation, pass the backup ID
        startActivity(intent)
    }

    private fun exportAllBackups() {
        lifecycleScope.launch {
            val backups = backupAdapter.currentList
            if (backups.isEmpty()) {
                Toast.makeText(this@BackupManagerActivity, R.string.no_backups_to_export, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val gson = com.google.gson.Gson()
            val json = gson.toJson(backups)
            
            // Export to file
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "nfc_backups_${System.currentTimeMillis()}.json")
            }
            startActivityForResult(intent, EXPORT_REQUEST_CODE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { 
                pendingBackupName?.let { name ->
                    createBackup(it, name)
                    pendingBackupName = null
                    binding.scanInstructionText.visibility = View.GONE
                }
            }
        }
    }

    private fun createBackup(tag: Tag, cardName: String) {
        val result = cardBackupHandler.backupCard(tag, cardName)
        
        if (result.success && result.backup != null) {
            lifecycleScope.launch {
                database.cardBackupDao().insert(result.backup)
                Toast.makeText(this@BackupManagerActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
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

    companion object {
        private const val EXPORT_REQUEST_CODE = 1001
    }
}
