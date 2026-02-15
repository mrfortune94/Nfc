package com.nfc.reader.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.joinToString(",") { it.toString() }
    }
    
    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.split(",")?.map { it.toByte() }?.toByteArray()
    }
}
