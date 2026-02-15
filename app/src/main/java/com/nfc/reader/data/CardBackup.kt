package com.nfc.reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing card backups
 * Supports cloning and emulation of access cards
 */
@Entity(tableName = "card_backups")
data class CardBackup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long = System.currentTimeMillis(),
    
    // Card identification
    val uid: String,
    val cardName: String,
    val cardType: String,
    
    // Raw card data
    val sectorData: String, // JSON map of sector -> data
    val keys: String? = null, // Encrypted keys for Mifare Classic
    
    // ISO standard
    val isoStandard: String,
    
    // Technical details
    val technologies: String,
    val memorySize: Int,
    
    // Metadata
    val notes: String? = null,
    val canEmulate: Boolean = false
)
