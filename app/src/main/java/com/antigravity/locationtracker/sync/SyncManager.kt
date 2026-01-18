package com.antigravity.locationtracker.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for instant synchronization of location data.
 * Provides immediate sync capability when network is available.
 */
class SyncManager(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val BATCH_SIZE = 50
        
        @Volatile
        private var isSyncing = false
    }
    
    private val database = AppDatabase.getInstance(context)
    private val sheetsRepository = SheetsRepository(context, securePrefs)
    
    /**
     * Check if device has active internet connection
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Sync all pending locations immediately.
     * Returns number of synced locations, or -1 on failure.
     */
    suspend fun syncNow(): Int = withContext(Dispatchers.IO) {
        if (isSyncing) {
            AppLogger.w(TAG, "Sync already in progress, skipping")
            return@withContext 0
        }
        
        if (!isNetworkAvailable()) {
            AppLogger.w(TAG, "No network available, skipping sync")
            return@withContext -1
        }
        
        if (securePrefs.userEmail == null) {
            AppLogger.w(TAG, "User not signed in, skipping sync")
            return@withContext -1
        }
        
        if (!securePrefs.hasSpreadsheet()) {
            AppLogger.w(TAG, "No spreadsheet configured, creating...")
            val result = sheetsRepository.findOrCreateSheet()
            if (result.isFailure) {
                AppLogger.e(TAG, "Failed to create spreadsheet", result.exceptionOrNull())
                return@withContext -1
            }
        }
        
        isSyncing = true
        try {
            val totalSynced = syncPendingLocations()
            AppLogger.i(TAG, "Instant sync complete: $totalSynced locations")
            return@withContext totalSynced
        } catch (e: Exception) {
            AppLogger.e(TAG, "Instant sync failed", e)
            return@withContext -1
        } finally {
            isSyncing = false
        }
    }
    
    /**
     * Sync a single location immediately after capture.
     * Optimized for low latency.
     */
    suspend fun syncSingleLocation(locationId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            AppLogger.d(TAG, "No network for single sync, will batch later")
            return@withContext false
        }
        
        if (!securePrefs.hasSpreadsheet()) {
            return@withContext false
        }
        
        try {
            val dao = database.locationPingDao()
            val location = dao.getById(locationId) ?: return@withContext false
            
            if (location.isSynced) {
                return@withContext true
            }
            
            val result = sheetsRepository.appendLocationData(listOf(location))
            if (result.isSuccess) {
                dao.markSynced(listOf(locationId))
                AppLogger.i(TAG, "Single location synced instantly")
                return@withContext true
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Single location sync failed", e)
        }
        return@withContext false
    }
    
    private suspend fun syncPendingLocations(): Int {
        val dao = database.locationPingDao()
        val unsyncedPings = dao.getUnsynced()
        
        if (unsyncedPings.isEmpty()) {
            AppLogger.i(TAG, "No pending locations to sync")
            return 0
        }
        
        AppLogger.i(TAG, "Syncing ${unsyncedPings.size} pending locations...")
        
        var totalSynced = 0
        val batches = unsyncedPings.chunked(BATCH_SIZE)
        
        for (batch in batches) {
            val result = sheetsRepository.appendLocationData(batch)
            
            if (result.isSuccess) {
                val ids = batch.map { it.id }
                dao.markSynced(ids)
                totalSynced += batch.size
                AppLogger.i(TAG, "Synced batch: ${batch.size} locations")
            } else {
                AppLogger.e(TAG, "Batch sync failed", result.exceptionOrNull())
                break
            }
        }
        
        return totalSynced
    }
}
