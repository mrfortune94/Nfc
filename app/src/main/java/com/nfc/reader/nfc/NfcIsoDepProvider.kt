package com.nfc.reader.nfc

import android.nfc.tech.IsoDep
import com.github.devnied.emvnfccard.exception.CommunicationException
import com.github.devnied.emvnfccard.parser.IProvider

/**
 * IProvider implementation for Android NFC IsoDep technology.
 * Used to interface with the EMV NFC Card parsing library for contactless payment cards.
 */
class NfcIsoDepProvider(private val isoDep: IsoDep) : IProvider {

    @Throws(CommunicationException::class)
    override fun transceive(command: ByteArray): ByteArray {
        return try {
            isoDep.transceive(command)
        } catch (e: Exception) {
            throw CommunicationException(e.message)
        }
    }

    override fun getAt(): ByteArray? {
        // Return historical bytes for NFC-A, or hiLayerResponse for NFC-B
        return isoDep.historicalBytes ?: isoDep.hiLayerResponse
    }
}
