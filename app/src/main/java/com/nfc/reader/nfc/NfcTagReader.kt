package com.nfc.reader.nfc

import android.nfc.Tag
import android.nfc.tech.*
import com.nfc.reader.utils.toHexString

/**
 * NFC Tag Reader supporting multiple ISO standards:
 * - ISO/IEC 14443-A (Type A)
 * - ISO/IEC 14443-B (Type B)
 * - ISO/IEC 15693
 * - ISO/IEC 18092 (NFC-F)
 */
class NfcTagReader {
    
    data class TagInfo(
        val uid: String,
        val technologies: List<String>,
        val tagType: String,
        val isoStandard: String,
        val atqa: String? = null,
        val sak: ByteArray? = null,
        val applicationData: String? = null,
        val dsfId: String? = null,
        val maxTransceiveLength: Int? = null,
        val memorySize: Int? = null,
        val isWritable: Boolean = false,
        val ndefInfo: NdefInfo? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as TagInfo
            
            if (uid != other.uid) return false
            if (technologies != other.technologies) return false
            if (tagType != other.tagType) return false
            if (isoStandard != other.isoStandard) return false
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
            if (ndefInfo != other.ndefInfo) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = uid.hashCode()
            result = 31 * result + technologies.hashCode()
            result = 31 * result + tagType.hashCode()
            result = 31 * result + isoStandard.hashCode()
            result = 31 * result + (atqa?.hashCode() ?: 0)
            result = 31 * result + (sak?.contentHashCode() ?: 0)
            result = 31 * result + (applicationData?.hashCode() ?: 0)
            result = 31 * result + (dsfId?.hashCode() ?: 0)
            result = 31 * result + (maxTransceiveLength ?: 0)
            result = 31 * result + (memorySize ?: 0)
            result = 31 * result + isWritable.hashCode()
            result = 31 * result + (ndefInfo?.hashCode() ?: 0)
            return result
        }
    }
    
    data class NdefInfo(
        val message: String,
        val records: List<NdefRecordData>
    )
    
    data class NdefRecordData(
        val type: String,
        val payload: String,
        val tnf: Short
    )
    
    fun readTag(tag: Tag): TagInfo {
        val uid = tag.id.toHexString()
        val techList = tag.techList.toList()
        
        // Determine ISO standard and tag type
        val (isoStandard, tagType) = determineIsoStandard(techList)
        
        // Read technology-specific information
        var atqa: String? = null
        var sak: ByteArray? = null
        var applicationData: String? = null
        var dsfId: String? = null
        var maxTransceiveLength: Int? = null
        var memorySize: Int? = null
        var isWritable = false
        var ndefInfo: NdefInfo? = null
        
        // ISO 14443-A specific
        if (techList.contains("android.nfc.tech.NfcA")) {
            val nfcA = NfcA.get(tag)
            nfcA?.use {
                atqa = it.atqa.toHexString()
                sak = byteArrayOf(it.sak.toByte())
                maxTransceiveLength = it.maxTransceiveLength
            }
        }
        
        // ISO 14443-B specific
        if (techList.contains("android.nfc.tech.NfcB")) {
            val nfcB = NfcB.get(tag)
            nfcB?.use {
                applicationData = it.applicationData?.toHexString()
                maxTransceiveLength = it.maxTransceiveLength
            }
        }
        
        // ISO 15693 specific
        if (techList.contains("android.nfc.tech.NfcV")) {
            val nfcV = NfcV.get(tag)
            nfcV?.use {
                dsfId = it.dsfId.toString()
                maxTransceiveLength = it.maxTransceiveLength
            }
        }
        
        // NDEF reading
        if (techList.contains("android.nfc.tech.Ndef")) {
            val ndef = Ndef.get(tag)
            ndef?.use {
                memorySize = it.maxSize
                isWritable = it.isWritable
                it.cachedNdefMessage?.let { msg ->
                    ndefInfo = parseNdefMessage(msg)
                }
            }
        }
        
        // MifareClassic specific
        if (techList.contains("android.nfc.tech.MifareClassic")) {
            val mifare = MifareClassic.get(tag)
            mifare?.use {
                memorySize = it.size
            }
        }
        
        // MifareUltralight specific
        if (techList.contains("android.nfc.tech.MifareUltralight")) {
            val ultralight = MifareUltralight.get(tag)
            ultralight?.use {
                when (it.type) {
                    MifareUltralight.TYPE_ULTRALIGHT -> memorySize = 64
                    MifareUltralight.TYPE_ULTRALIGHT_C -> memorySize = 192
                    else -> memorySize = null
                }
            }
        }
        
        return TagInfo(
            uid = uid,
            technologies = techList,
            tagType = tagType,
            isoStandard = isoStandard,
            atqa = atqa,
            sak = sak,
            applicationData = applicationData,
            dsfId = dsfId,
            maxTransceiveLength = maxTransceiveLength,
            memorySize = memorySize,
            isWritable = isWritable,
            ndefInfo = ndefInfo
        )
    }
    
    private fun determineIsoStandard(techList: List<String>): Pair<String, String> {
        return when {
            techList.contains("android.nfc.tech.NfcA") -> "ISO/IEC 14443-A" to "Type A"
            techList.contains("android.nfc.tech.NfcB") -> "ISO/IEC 14443-B" to "Type B"
            techList.contains("android.nfc.tech.NfcF") -> "ISO/IEC 18092" to "Type F (FeliCa)"
            techList.contains("android.nfc.tech.NfcV") -> "ISO/IEC 15693" to "Type V (Vicinity)"
            techList.contains("android.nfc.tech.IsoDep") -> "ISO/IEC 14443-4 / ISO 7816" to "ISO-DEP"
            techList.contains("android.nfc.tech.MifareClassic") -> "ISO/IEC 14443-A" to "Mifare Classic"
            techList.contains("android.nfc.tech.MifareUltralight") -> "ISO/IEC 14443-A" to "Mifare Ultralight"
            else -> "Unknown" to "Unknown"
        }
    }
    
    private fun parseNdefMessage(msg: android.nfc.NdefMessage): NdefInfo {
        val records = msg.records.map { record ->
            val type = String(record.type)
            val payload = String(record.payload)
            NdefRecordData(type, payload, record.tnf)
        }
        return NdefInfo(msg.toString(), records)
    }
}
