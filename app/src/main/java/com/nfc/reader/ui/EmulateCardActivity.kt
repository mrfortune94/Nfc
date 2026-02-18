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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nfc.reader.R
import com.nfc.reader.data.CardBackup
import com.nfc.reader.data.EmulationProfile
import com.nfc.reader.data.NfcDatabase
import com.nfc.reader.databinding.ActivityEmulateCardBinding
import com.nfc.reader.hce.CardEmulationService
import com.nfc.reader.nfc.CardBackupHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Activity for emulating and replaying NFC cards
 * Supports Host Card Emulation (HCE) for compatible tags
 * 
 * IMPORTANT: Only use with your own cards for educational purposes
 */
class EmulateCardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmulateCardBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var database: NfcDatabase
    private val cardBackupHandler = CardBackupHandler()
    
    private var availableBackups = listOf<CardBackup>()
    private var selectedBackup: CardBackup? = null
    private var isEmulationActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmulateCardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.emulate_card_title)
        }

        database = NfcDatabase.getDatabase(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupUI()
        loadBackups()
    }

    private fun setupUI() {
        binding.selectCardButton.setOnClickListener {
            showCardSelectionDialog()
        }

        binding.startEmulationButton.setOnClickListener {
            if (selectedBackup == null) {
                Toast.makeText(this, R.string.select_card_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedBackup?.canEmulate == true) {
                // Check if this is a MIFARE Classic card and show warning
                if (selectedBackup?.technologies?.contains("MifareClassic") == true) {
                    showMifareClassicWarningDialog()
                } else {
                    startEmulation()
                }
            } else {
                showEmulationNotSupportedDialog()
            }
        }

        binding.stopEmulationButton.setOnClickListener {
            stopEmulation()
        }

        binding.backupNewCardButton.setOnClickListener {
            showBackupPrompt()
        }

        updateEmulationUI()
    }

    private fun loadBackups() {
        lifecycleScope.launch {
            database.cardBackupDao().getAllBackups().collect { backups ->
                availableBackups = backups
                updateBackupsList()
            }
        }
    }

    private fun updateBackupsList() {
        if (availableBackups.isEmpty()) {
            binding.noBackupsText.visibility = View.VISIBLE
            binding.cardListText.visibility = View.GONE
        } else {
            binding.noBackupsText.visibility = View.GONE
            binding.cardListText.visibility = View.VISIBLE
            binding.cardListText.text = getString(R.string.available_cards_count, availableBackups.size)
        }
    }

    private fun showCardSelectionDialog() {
        if (availableBackups.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_cards_title)
                .setMessage(R.string.no_cards_message)
                .setPositiveButton(R.string.backup_now) { _, _ -> showBackupPrompt() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val cardNames = availableBackups.map { 
            "${it.cardName} (${it.cardType})\nUID: ${it.uid}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.select_card_to_emulate)
            .setItems(cardNames) { _, which ->
                selectedBackup = availableBackups[which]
                updateSelectedCardUI()
            }
            .show()
    }

    private fun updateSelectedCardUI() {
        selectedBackup?.let { backup ->
            binding.selectedCardContainer.visibility = View.VISIBLE
            binding.selectedCardName.text = backup.cardName
            binding.selectedCardUid.text = getString(R.string.card_uid_format, backup.uid)
            binding.selectedCardType.text = getString(R.string.card_type_format, backup.cardType)
            binding.selectedCardIso.text = getString(R.string.card_iso_format, backup.isoStandard)
            
            binding.emulationSupportedIcon.setImageResource(
                if (backup.canEmulate) R.drawable.ic_check_circle else R.drawable.ic_warning
            )
            binding.emulationSupportedText.text = getString(
                if (backup.canEmulate) R.string.emulation_supported else R.string.emulation_not_supported
            )
        }
    }

    private fun startEmulation() {
        selectedBackup?.let { backup ->
            // Configure the HCE service with the backup data
            val responses = mutableMapOf<String, String>()
            
            // Store basic card info for emulation
            CardEmulationService.setEmulationData(
                context = this,
                uid = backup.uid,
                responses = responses,
                enabled = true
            )
            
            isEmulationActive = true
            updateEmulationUI()
            
            Toast.makeText(this, R.string.emulation_started, Toast.LENGTH_SHORT).show()
            
            // Log the emulation start
            lifecycleScope.launch {
                val profile = EmulationProfile(
                    profileName = backup.cardName,
                    cardBackupId = backup.id,
                    uid = backup.uid,
                    cardType = backup.cardType,
                    isoStandard = backup.isoStandard,
                    isActive = true,
                    lastEmulatedAt = System.currentTimeMillis()
                )
                database.emulationProfileDao().deactivateAllProfiles()
                database.emulationProfileDao().insert(profile)
            }
        }
    }

    private fun stopEmulation() {
        // Disable HCE emulation
        CardEmulationService.clearEmulationData(this)
        
        isEmulationActive = false
        updateEmulationUI()
        
        // Update profile status
        lifecycleScope.launch {
            database.emulationProfileDao().deactivateAllProfiles()
        }
        
        Toast.makeText(this, R.string.emulation_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun updateEmulationUI() {
        if (isEmulationActive) {
            binding.startEmulationButton.visibility = View.GONE
            binding.stopEmulationButton.visibility = View.VISIBLE
            binding.emulationStatusText.text = getString(R.string.emulation_active)
            binding.emulationStatusText.setTextColor(ContextCompat.getColor(this, R.color.nfc_green))
        } else {
            binding.startEmulationButton.visibility = View.VISIBLE
            binding.stopEmulationButton.visibility = View.GONE
            binding.emulationStatusText.text = getString(R.string.emulation_inactive)
            binding.emulationStatusText.setTextColor(ContextCompat.getColor(this, R.color.premium_primary_dark))
        }
    }

    private fun showEmulationNotSupportedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.emulation_not_supported)
            .setMessage(R.string.emulation_not_supported_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showMifareClassicWarningDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.emulation_limited)
            .setMessage(R.string.emulation_limited_message)
            .setPositiveButton(R.string.start_emulation) { _, _ ->
                startEmulation()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private var pendingBackupName: String? = null

    private fun showBackupPrompt() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.enter_card_name)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.backup_new_card)
            .setMessage(R.string.backup_prompt_message)
            .setView(input)
            .setPositiveButton(R.string.scan_card) { _, _ ->
                val name = input.text.toString().ifBlank { "My Card" }
                pendingBackupName = name
                binding.backupInstructionText.visibility = View.VISIBLE
                binding.backupInstructionText.text = getString(R.string.hold_card_to_backup)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let { 
                pendingBackupName?.let { name ->
                    backupCard(it, name)
                    pendingBackupName = null
                    binding.backupInstructionText.visibility = View.GONE
                }
            }
        }
    }

    private fun backupCard(tag: Tag, cardName: String) {
        val result = cardBackupHandler.backupCard(tag, cardName)
        
        if (result.success && result.backup != null) {
            lifecycleScope.launch {
                database.cardBackupDao().insert(result.backup)
                Toast.makeText(this@EmulateCardActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
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
}
