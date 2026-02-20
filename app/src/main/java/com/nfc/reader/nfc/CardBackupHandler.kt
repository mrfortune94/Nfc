package com.nfc.reader.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcV
import com.google.gson.Gson
import com.nfc.reader.data.CardBackup
import com.nfc.reader.utils.toHexString
import java.io.IOException

/**
 * Card Backup and Clone Handler
 * Supports backing up and cloning NFC access cards
 */
class CardBackupHandler {
    
    private val gson = Gson()
    
    data class BackupResult(
        val success: Boolean,
        val message: String,
        val backup: CardBackup? = null
    )
    
    /**
     * Backup card data
     */
    fun backupCard(tag: Tag, cardName: String): BackupResult {
        val uid = tag.id.toHexString()
        val techList = tag.techList.toList()
        
        return try {
            when {
                techList.contains("android.nfc.tech.MifareClassic") -> {
                    backupMifareClassic(tag, cardName)
                }
                techList.contains("android.nfc.tech.NfcA") -> {
                    backupNfcA(tag, cardName)
                }
                techList.contains("android.nfc.tech.NfcB") -> {
                    backupNfcB(tag, cardName)
                }
                techList.contains("android.nfc.tech.NfcV") -> {
                    backupNfcV(tag, cardName)
                }
                else -> {
                    BackupResult(
                        success = false,
                        message = "Unsupported card type for backup"
                    )
                }
            }
        } catch (e: Exception) {
            BackupResult(
                success = false,
                message = "Backup failed: ${e.message}"
            )
        }
    }
    
    private fun backupMifareClassic(tag: Tag, cardName: String): BackupResult {
        val mifare = MifareClassic.get(tag) ?: return BackupResult(
            success = false,
            message = "Not a Mifare Classic card"
        )
        
        try {
            mifare.connect()
            
            val sectorCount = mifare.sectorCount
            val sectorData = mutableMapOf<Int, List<String>>()
            
            // Read all sectors
            for (sector in 0 until sectorCount) {
                val blocks = mutableListOf<String>()
                val blockCount = mifare.getBlockCountInSector(sector)
                
                for (block in 0 until blockCount) {
                    val blockIndex = mifare.sectorToBlock(sector) + block
                    
                    try {
                        // Try to authenticate with default keys
                        val authenticated = authenticateSector(mifare, sector)
                        
                        if (authenticated) {
                            val data = mifare.readBlock(blockIndex)
                            blocks.add(data.toHexString())
                        } else {
                            blocks.add("LOCKED")
                        }
                    } catch (e: IOException) {
                        blocks.add("ERROR")
                    }
                }
                
                sectorData[sector] = blocks
            }
            
            mifare.close()
            
            val backup = CardBackup(
                uid = tag.id.toHexString(),
                cardName = cardName,
                cardType = getMifareType(mifare.type),
                sectorData = gson.toJson(sectorData),
                isoStandard = "ISO/IEC 14443-A",
                technologies = "MifareClassic,NfcA",
                memorySize = mifare.size,
                canEmulate = true // UID-level emulation via HCE
            )
            
            return BackupResult(
                success = true,
                message = "Card backed up successfully",
                backup = backup
            )
            
        } catch (e: Exception) {
            return BackupResult(
                success = false,
                message = "Error reading card: ${e.message}"
            )
        } finally {
            if (mifare.isConnected) {
                mifare.close()
            }
        }
    }
    
    private fun backupNfcA(tag: Tag, cardName: String): BackupResult {
        val nfcA = NfcA.get(tag) ?: return BackupResult(
            success = false,
            message = "Not an NFC-A card"
        )
        
        try {
            nfcA.connect()
            
            // Store basic card info
            val data = mapOf(
                "atqa" to nfcA.atqa.toHexString(),
                "sak" to nfcA.sak.toString(),
                "maxTransceive" to nfcA.maxTransceiveLength.toString()
            )
            
            nfcA.close()
            
            val backup = CardBackup(
                uid = tag.id.toHexString(),
                cardName = cardName,
                cardType = "NFC-A Generic",
                sectorData = gson.toJson(data),
                isoStandard = "ISO/IEC 14443-A",
                technologies = "NfcA",
                memorySize = 0,
                canEmulate = true
            )
            
            return BackupResult(
                success = true,
                message = "NFC-A card info saved",
                backup = backup
            )
            
        } catch (e: Exception) {
            return BackupResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    private fun backupNfcB(tag: Tag, cardName: String): BackupResult {
        val nfcB = NfcB.get(tag) ?: return BackupResult(
            success = false,
            message = "Not an NFC-B card"
        )
        
        try {
            nfcB.connect()
            
            val data = mapOf(
                "applicationData" to (nfcB.applicationData?.toHexString() ?: ""),
                "protocolInfo" to (nfcB.protocolInfo?.toHexString() ?: "")
            )
            
            nfcB.close()
            
            val backup = CardBackup(
                uid = tag.id.toHexString(),
                cardName = cardName,
                cardType = "NFC-B",
                sectorData = gson.toJson(data),
                isoStandard = "ISO/IEC 14443-B",
                technologies = "NfcB",
                memorySize = 0,
                canEmulate = true
            )
            
            return BackupResult(
                success = true,
                message = "NFC-B card info saved",
                backup = backup
            )
            
        } catch (e: Exception) {
            return BackupResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    private fun backupNfcV(tag: Tag, cardName: String): BackupResult {
        val nfcV = NfcV.get(tag) ?: return BackupResult(
            success = false,
            message = "Not an NFC-V card"
        )
        
        try {
            nfcV.connect()
            
            val data = mapOf(
                "dsfId" to nfcV.dsfId.toString(),
                "responseFlags" to nfcV.responseFlags.toString()
            )
            
            nfcV.close()
            
            val backup = CardBackup(
                uid = tag.id.toHexString(),
                cardName = cardName,
                cardType = "NFC-V (ISO 15693)",
                sectorData = gson.toJson(data),
                isoStandard = "ISO/IEC 15693",
                technologies = "NfcV",
                memorySize = 0,
                canEmulate = false
            )
            
            return BackupResult(
                success = true,
                message = "NFC-V card info saved",
                backup = backup
            )
            
        } catch (e: Exception) {
            return BackupResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    private fun authenticateSector(mifare: MifareClassic, sector: Int): Boolean {
        // Try common default keys
        val defaultKeys = arrayOf(
            MifareClassic.KEY_DEFAULT,
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
            MifareClassic.KEY_NFC_FORUM
        )
        
        for (key in defaultKeys) {
            if (mifare.authenticateSectorWithKeyA(sector, key) ||
                mifare.authenticateSectorWithKeyB(sector, key)) {
                return true
            }
        }
        
        return false
    }
    
    private fun getMifareType(type: Int): String {
        return when (type) {
            MifareClassic.TYPE_CLASSIC -> "Mifare Classic"
            MifareClassic.TYPE_PLUS -> "Mifare Plus"
            MifareClassic.TYPE_PRO -> "Mifare Pro"
            else -> "Mifare Unknown"
        }
    }
}
