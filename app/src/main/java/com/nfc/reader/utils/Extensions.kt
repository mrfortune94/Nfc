package com.nfc.reader.utils

/**
 * Convert byte array to hex string
 */
fun ByteArray.toHexString(): String {
    return joinToString("") { "%02X".format(it) }
}

/**
 * Convert hex string to byte array
 */
fun String.hexToByteArray(): ByteArray {
    val cleaned = this.replace(" ", "").replace(":", "")
    return cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * Format timestamp to readable string
 */
fun Long.toDateString(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(this))
}
