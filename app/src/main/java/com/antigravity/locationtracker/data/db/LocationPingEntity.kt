package com.antigravity.locationtracker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a single location ping.
 * Stored locally in Room database for offline-first approach.
 */
@Entity(tableName = "location_pings")
data class LocationPingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val speed: Float?,
    
    val timestamp: Long, // Unix timestamp in milliseconds
    val batteryLevel: Int, // 0-100
    
    val isSynced: Boolean = false,
    val syncedAt: Long? = null, // When it was uploaded to Sheets
    
    val createdAt: Long = System.currentTimeMillis()
)
