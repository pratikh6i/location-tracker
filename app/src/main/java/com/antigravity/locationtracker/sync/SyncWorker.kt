package com.antigravity.locationtracker.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository

/**
 * WorkManager worker for syncing location data to Google Sheets.
 * Runs periodically when network is available.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "SyncWorker"
        private const val BATCH_SIZE = 50 // Max rows to sync at once
    }
    
    private val database = AppDatabase.getInstance(context)
    private val securePrefs = SecurePreferences(context)
    private val sheetsRepository = SheetsRepository(context, securePrefs)
    
    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting sync work")
        
        // Check if user is signed in
        if (securePrefs.userEmail == null) {
            Log.w(TAG, "User not signed in, skipping sync")
            return Result.success()
        }
        
        // Check if spreadsheet is configured
        if (!securePrefs.hasSpreadsheet()) {
            Log.w(TAG, "Spreadsheet not configured, attempting to create")
            val sheetResult = sheetsRepository.findOrCreateSheet()
            if (sheetResult.isFailure) {
                Log.e(TAG, "Failed to create spreadsheet: ${sheetResult.exceptionOrNull()}")
                return Result.retry()
            }
        }
        
        return try {
            syncPendingLocations()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            Result.retry()
        }
    }
    
    private suspend fun syncPendingLocations(): Result {
        val dao = database.locationPingDao()
        
        // Get unsynced locations
        val unsyncedPings = dao.getUnsynced()
        
        if (unsyncedPings.isEmpty()) {
            Log.i(TAG, "No pending locations to sync")
            return Result.success()
        }
        
        Log.i(TAG, "Found ${unsyncedPings.size} locations to sync")
        
        // Sync in batches
        val batches = unsyncedPings.chunked(BATCH_SIZE)
        var totalSynced = 0
        
        for (batch in batches) {
            val result = sheetsRepository.appendLocationData(batch)
            
            if (result.isSuccess) {
                // Mark batch as synced
                val ids = batch.map { it.id }
                dao.markSynced(ids)
                totalSynced += batch.size
                Log.i(TAG, "Synced batch of ${batch.size} locations")
            } else {
                Log.e(TAG, "Failed to sync batch: ${result.exceptionOrNull()}")
                // Continue with next batch, or return retry if it's a persistent error
                if (totalSynced == 0) {
                    return Result.retry()
                }
                break
            }
        }
        
        Log.i(TAG, "Sync complete: $totalSynced locations uploaded")
        
        // Clean up old synced entries (older than 30 days)
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.deleteOldSynced(thirtyDaysAgo)
        
        return Result.success()
    }
}
