package com.nfc.reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing card emulation profiles
 * Used for HCE (Host Card Emulation) replay functionality
 * 
 * IMPORTANT: Only for use with cards owned by the user
 * Educational and research purposes only
 */
@Entity(tableName = "emulation_profiles")
data class EmulationProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long = System.currentTimeMillis(),
    
    // Profile identification
    val profileName: String,
    val cardBackupId: Long? = null, // Reference to source CardBackup
    
    // Card identifiers
    val uid: String,
    val aid: String? = null, // Application Identifier for ISO-DEP
    
    // Emulation data (stored as JSON)
    val apduResponses: String? = null, // JSON map of command -> response pairs
    val customData: String? = null, // Additional card-specific data
    
    // Card type info
    val cardType: String,
    val isoStandard: String,
    
    // Emulation status
    val isActive: Boolean = false,
    val lastEmulatedAt: Long? = null,
    val emulationCount: Int = 0,
    
    // Notes
    val notes: String? = null
)
