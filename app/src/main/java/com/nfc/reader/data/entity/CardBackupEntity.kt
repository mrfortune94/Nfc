package com.nfc.reader.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_backups")
data class CardBackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val backupName: String,               // user given name or auto "Card-2026-02-17"
    val timestamp: Long = System.currentTimeMillis(),
    val tagType: String,
    val technologies: String,
    val fullDumpText: String,             // human readable deep parse result
    val rawApduLog: String,               // all commands + responses JSON or concatenated
    val track2Hex: String? = null,
    val pan: String? = null,
    val expiryYYMM: String? = null,
    val isEmv: Boolean = false,
    val memoryDump: String? = null        // MIFARE sectors or other binary hex if applicable
)
