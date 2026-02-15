package com.nfc.reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing an NFC tag scan log
 * Supports ISO/IEC 14443, ISO/IEC 15693, and other standards
 */
@Entity(tableName = "nfc_logs")
data class NfcLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val timestamp: Long = Date().time,
    
    // Tag identification
    val uid: String,
    val tagType: String,
    val technologies: String, // Comma-separated list
    
    // ISO/IEC Standard information
    val isoStandard: String, // e.g., "ISO 14443-A", "ISO 15693"
    
    // NDEF data
    val hasNdef: Boolean = false,
    val ndefMessage: String? = null,
    val ndefRecords: String? = null, // JSON array of records
    
    // Technical details
    val atqa: String? = null, // Answer to Request Type A (ISO 14443-A)
    val sak: ByteArray? = null, // Select Acknowledge (ISO 14443-A)
    val applicationData: String? = null, // ISO 15693
    val dsfId: String? = null, // Data Storage Format ID (ISO 15693)
    val maxTransceiveLength: Int? = null,
    
    // Memory info
    val memorySize: Int? = null,
    val isWritable: Boolean = false,
    
    // EMV/ISO 7816 APDU logs
    val apduCommands: String? = null, // JSON array of APDU exchanges
    
    // Operation type
    val operation: String // READ, WRITE, CLONE, EMULATE, APDU
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NfcLog

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (uid != other.uid) return false
        if (tagType != other.tagType) return false
        if (technologies != other.technologies) return false
        if (isoStandard != other.isoStandard) return false
        if (hasNdef != other.hasNdef) return false
        if (ndefMessage != other.ndefMessage) return false
        if (ndefRecords != other.ndefRecords) return false
        if (atqa != other.atqa) return false
        if (sak != null) {
            if (other.sak == null) return false
            if (!sak.contentEquals(other.sak)) return false
        } else if (other.sak != null) return false
        if (applicationData != other.applicationData) return false
        if (dsfId != other.dsfId) return false
        if (maxTransceiveLength != other.maxTransceiveLength) return false
        if (memorySize != other.memorySize) return false
        if (isWritable != other.isWritable) return false
        if (apduCommands != other.apduCommands) return false
        if (operation != other.operation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + uid.hashCode()
        result = 31 * result + tagType.hashCode()
        result = 31 * result + technologies.hashCode()
        result = 31 * result + isoStandard.hashCode()
        result = 31 * result + hasNdef.hashCode()
        result = 31 * result + (ndefMessage?.hashCode() ?: 0)
        result = 31 * result + (ndefRecords?.hashCode() ?: 0)
        result = 31 * result + (atqa?.hashCode() ?: 0)
        result = 31 * result + (sak?.contentHashCode() ?: 0)
        result = 31 * result + (applicationData?.hashCode() ?: 0)
        result = 31 * result + (dsfId?.hashCode() ?: 0)
        result = 31 * result + (maxTransceiveLength ?: 0)
        result = 31 * result + (memorySize ?: 0)
        result = 31 * result + isWritable.hashCode()
        result = 31 * result + (apduCommands?.hashCode() ?: 0)
        result = 31 * result + operation.hashCode()
        return result
    }
}
