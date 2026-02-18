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
    
    @Query("SELECT * FROM card_backups WHERE id = :id")
    suspend fun getBackupById(id: Long): CardBackup?
    
    @Insert
    suspend fun insert(backup: CardBackup)
    
    @Delete
    suspend fun delete(backup: CardBackup)
}

@Dao
interface EmulationProfileDao {
    @Query("SELECT * FROM emulation_profiles ORDER BY timestamp DESC")
    fun getAllProfiles(): Flow<List<EmulationProfile>>
    
    @Query("SELECT * FROM emulation_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): EmulationProfile?
    
    @Query("SELECT * FROM emulation_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): EmulationProfile?
    
    @Query("UPDATE emulation_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()
    
    @Query("UPDATE emulation_profiles SET isActive = :isActive, lastEmulatedAt = :timestamp, emulationCount = emulationCount + 1 WHERE id = :id")
    suspend fun setActiveProfile(id: Long, isActive: Boolean, timestamp: Long = System.currentTimeMillis())
    
    @Insert
    suspend fun insert(profile: EmulationProfile): Long
    
    @Delete
    suspend fun delete(profile: EmulationProfile)
}
