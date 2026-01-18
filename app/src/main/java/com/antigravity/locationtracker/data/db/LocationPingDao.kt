package com.antigravity.locationtracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for location pings.
 */
@Dao
interface LocationPingDao {
    
    /**
     * Insert a new location ping.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ping: LocationPingEntity): Long
    
    /**
     * Get all unsynced pings for upload to Google Sheets.
     * Ordered by timestamp for chronological upload.
     */
    @Query("SELECT * FROM location_pings WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<LocationPingEntity>
    
    /**
     * Get count of unsynced pings.
     */
    @Query("SELECT COUNT(*) FROM location_pings WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int
    
    /**
     * Observable count of unsynced pings for UI updates.
     */
    @Query("SELECT COUNT(*) FROM location_pings WHERE isSynced = 0")
    fun getUnsyncedCountFlow(): Flow<Int>
    
    /**
     * Mark specific pings as synced after successful upload.
     */
    @Query("UPDATE location_pings SET isSynced = 1, syncedAt = :syncedAt WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>, syncedAt: Long = System.currentTimeMillis())
    
    /**
     * Get a specific ping by ID.
     */
    @Query("SELECT * FROM location_pings WHERE id = :id")
    suspend fun getById(id: Long): LocationPingEntity?
    
    /**
     * Get the most recent location ping.
     */
    @Query("SELECT * FROM location_pings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): LocationPingEntity?
    
    /**
     * Observable for the most recent location (for UI).
     */
    @Query("SELECT * FROM location_pings ORDER BY timestamp DESC LIMIT 1")
    fun getLatestFlow(): Flow<LocationPingEntity?>
    
    /**
     * Get all pings (for debugging).
     */
    @Query("SELECT * FROM location_pings ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<LocationPingEntity>>
    
    /**
     * Delete old synced pings to manage storage.
     * Keeps only pings from the last 30 days.
     */
    @Query("DELETE FROM location_pings WHERE isSynced = 1 AND createdAt < :cutoffTime")
    suspend fun deleteOldSynced(cutoffTime: Long)
    
    /**
     * Get last synced timestamp for status display.
     */
    @Query("SELECT MAX(syncedAt) FROM location_pings WHERE isSynced = 1")
    suspend fun getLastSyncTime(): Long?
    
    /**
     * Observable for last sync time.
     */
    @Query("SELECT MAX(syncedAt) FROM location_pings WHERE isSynced = 1")
    fun getLastSyncTimeFlow(): Flow<Long?>
}
