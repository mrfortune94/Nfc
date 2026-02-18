package com.nfc.reader.nfc

import android.nfc.tech.IsoDep
import com.github.devnied.emvnfccard.exception.CommunicationException
import com.github.devnied.emvnfccard.parser.IProvider

/**
 * IProvider implementation for Android NFC IsoDep technology.
 * Used to interface with the EMV NFC Card parsing library for contactless payment cards.
 * 
 * Supports ISO/IEC 14443-4 (ISO-DEP) communication with:
 * - Connection lifecycle management (connect/disconnect/close)
 * - EMV response chaining (SW 61XX - more data available)
 * - Wrong Le correction (SW 6CXX - retry with correct length)
 * - Historical bytes extraction for NFC-A and hi-layer response for NFC-B
 */
class NfcIsoDepProvider(private val isoDep: IsoDep) : IProvider {

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15000
    }

    /**
     * Connect to the ISO-DEP tag with default timeout.
     * Safe to call multiple times; no-op if already connected.
     */
    fun connect(timeoutMs: Int = DEFAULT_TIMEOUT_MS) {
        if (!isoDep.isConnected) {
            isoDep.connect()
        }
        isoDep.timeout = timeoutMs
    }

    /**
     * Close the ISO-DEP connection.
     * Safe to call multiple times; no-op if already disconnected.
     */
    fun close() {
        try {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        } catch (_: Exception) {
            // Ignore close errors
        }
    }

    /**
     * Check if the ISO-DEP tag is currently connected.
     */
    val isConnected: Boolean
        get() = isoDep.isConnected

    @Throws(CommunicationException::class)
    override fun transceive(command: ByteArray): ByteArray {
        return try {
            var response = isoDep.transceive(command)

            // Handle EMV response chaining: SW 61XX means XX more bytes available
            while (response.size >= 2) {
                val sw1 = response[response.size - 2].toInt() and 0xFF
                val sw2 = response[response.size - 1].toInt() and 0xFF

                if (sw1 == 0x61) {
                    // GET RESPONSE to fetch remaining data
                    val getResponse = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
                    val next = isoDep.transceive(getResponse)
                    // Concatenate: previous data (without SW) + next response
                    response = response.copyOfRange(0, response.size - 2) + next
                } else if (sw1 == 0x6C) {
                    // Wrong Le: re-send command with correct Le from SW2
                    val corrected = command.copyOf()
                    corrected[corrected.size - 1] = sw2.toByte()
                    response = isoDep.transceive(corrected)
                } else {
                    break
                }
            }

            response
        } catch (e: Exception) {
            throw CommunicationException(e.message ?: "Communication failed")
        }
    }

    override fun getAt(): ByteArray? {
        // Return historical bytes for NFC-A, or hiLayerResponse for NFC-B
        return isoDep.historicalBytes ?: isoDep.hiLayerResponse
    }
}
