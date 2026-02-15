package com.nfc.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [NfcLog::class, CardBackup::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NfcDatabase : RoomDatabase() {
    abstract fun nfcLogDao(): NfcLogDao
    abstract fun cardBackupDao(): CardBackupDao
    
    companion object {
        @Volatile
        private var INSTANCE: NfcDatabase? = null
        
        fun getDatabase(context: Context): NfcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NfcDatabase::class.java,
                    "nfc_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
