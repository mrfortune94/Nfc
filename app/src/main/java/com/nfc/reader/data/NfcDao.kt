package com.nfc.reader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface NfcLogDao {
    @Query("SELECT * FROM nfc_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NfcLog>>
    
    @Query("SELECT * FROM nfc_logs WHERE operation = :operation ORDER BY timestamp DESC")
    fun getLogsByOperation(operation: String): Flow<List<NfcLog>>
    
    @Insert
    suspend fun insert(log: NfcLog)
    
    @Query("DELETE FROM nfc_logs")
    suspend fun deleteAll()
    
    @Delete
    suspend fun delete(log: NfcLog)
}

@Dao
interface CardBackupDao {
    @Query("SELECT * FROM card_backups ORDER BY timestamp DESC")
    fun getAllBackups(): Flow<List<CardBackup>>
    
    @Query("SELECT * FROM card_backups WHERE uid = :uid")
    suspend fun getBackupByUid(uid: String): CardBackup?
    
    @Insert
    suspend fun insert(backup: CardBackup)
    
    @Delete
    suspend fun delete(backup: CardBackup)
}
