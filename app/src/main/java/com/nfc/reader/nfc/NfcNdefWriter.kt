package com.nfc.reader.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import java.io.IOException
import java.nio.charset.Charset

/**
 * NFC NDEF Writer for automation tags
 * Supports writing:
 * - Text records
 * - URL records (URI)
 * - Android Application Records (AAR) for app launch
 */
class NfcNdefWriter {
    
    sealed class WriteResult {
        object Success : WriteResult()
        data class Error(val message: String) : WriteResult()
    }
    
    /**
     * Write a plain text NDEF record to tag
     */
    fun writeText(tag: Tag, text: String, languageCode: String = "en"): WriteResult {
        val textRecord = createTextRecord(text, languageCode)
        val message = NdefMessage(arrayOf(textRecord))
        return writeNdefMessage(tag, message)
    }
    
    /**
     * Write a URL NDEF record to tag
     */
    fun writeUrl(tag: Tag, url: String): WriteResult {
        val uriRecord = NdefRecord.createUri(url)
        val message = NdefMessage(arrayOf(uriRecord))
        return writeNdefMessage(tag, message)
    }
    
    /**
     * Write an Android Application Record (AAR) to launch an app
     * @param packageName The package name of the app to launch
     */
    fun writeAppLaunch(tag: Tag, packageName: String): WriteResult {
        val aarRecord = NdefRecord.createApplicationRecord(packageName)
        val message = NdefMessage(arrayOf(aarRecord))
        return writeNdefMessage(tag, message)
    }
    
    /**
     * Write a text record with AAR for app launch
     */
    fun writeTextWithApp(tag: Tag, text: String, packageName: String): WriteResult {
        val textRecord = createTextRecord(text)
        val aarRecord = NdefRecord.createApplicationRecord(packageName)
        val message = NdefMessage(arrayOf(textRecord, aarRecord))
        return writeNdefMessage(tag, message)
    }
    
    /**
     * Write a URL with AAR for app launch
     */
    fun writeUrlWithApp(tag: Tag, url: String, packageName: String): WriteResult {
        val uriRecord = NdefRecord.createUri(url)
        val aarRecord = NdefRecord.createApplicationRecord(packageName)
        val message = NdefMessage(arrayOf(uriRecord, aarRecord))
        return writeNdefMessage(tag, message)
    }
    
    private fun writeNdefMessage(tag: Tag, message: NdefMessage): WriteResult {
        try {
            // Try to get existing Ndef tag
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                return writeToNdef(ndef, message)
            }
            
            // Try to format as NDEF if not already formatted
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                return formatAndWrite(ndefFormatable, message)
            }
            
            return WriteResult.Error("Tag is not NDEF compatible")
        } catch (e: Exception) {
            return WriteResult.Error("Error writing to tag: ${e.message}")
        }
    }
    
    private fun writeToNdef(ndef: Ndef, message: NdefMessage): WriteResult {
        return try {
            ndef.connect()
            
            if (!ndef.isWritable) {
                ndef.close()
                return WriteResult.Error("Tag is read-only")
            }
            
            val size = message.toByteArray().size
            if (size > ndef.maxSize) {
                ndef.close()
                return WriteResult.Error("Message is too large for tag (${size} > ${ndef.maxSize})")
            }
            
            ndef.writeNdefMessage(message)
            ndef.close()
            WriteResult.Success
        } catch (e: IOException) {
            WriteResult.Error("I/O error: ${e.message}")
        } catch (e: Exception) {
            WriteResult.Error("Error: ${e.message}")
        }
    }
    
    private fun formatAndWrite(formatable: NdefFormatable, message: NdefMessage): WriteResult {
        return try {
            formatable.connect()
            formatable.format(message)
            formatable.close()
            WriteResult.Success
        } catch (e: IOException) {
            WriteResult.Error("I/O error during format: ${e.message}")
        } catch (e: Exception) {
            WriteResult.Error("Format error: ${e.message}")
        }
    }
    
    private fun createTextRecord(text: String, languageCode: String = "en"): NdefRecord {
        val langBytes = languageCode.toByteArray(Charset.forName("US-ASCII"))
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        
        // First byte: status byte (0x02 = UTF-8, length of language code)
        payload[0] = langBytes.size.toByte()
        
        // Copy language code
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        
        // Copy text
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)
        
        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }
}
