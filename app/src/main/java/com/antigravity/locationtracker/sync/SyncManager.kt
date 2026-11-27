package com.antigravity.locationtracker.sync

import android.content.Context
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.DeviceControlHelper
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Mutex-protected manager for instant, deduplicated synchronization of location data.
 */
class SyncManager(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val BATCH_SIZE = 50
        private val syncMutex = Mutex()
    }

    private val database = AppDatabase.getInstance(context.applicationContext)
    private val sheetsRepository = SheetsRepository(context.applicationContext, securePrefs)

    fun isNetworkAvailable(): Boolean = DeviceControlHelper.isInternetAvailable(context)

    /**
     * Sync all pending locations chronologically under a process-wide mutex
     * so concurrent callers never upload duplicate rows.
     * Returns number of synced locations, or -1 on failure.
     */
    suspend fun syncNow(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            if (!isNetworkAvailable()) {
                AppLogger.w(TAG, "No network available, skipping sync")
                return@withLock -1
            }

            if (securePrefs.userEmail == null) {
                AppLogger.w(TAG, "User not signed in, skipping sync")
                return@withLock -1
            }

            if (!securePrefs.hasSpreadsheet()) {
                AppLogger.w(TAG, "No spreadsheet configured, discovering/creating...")
                val result = sheetsRepository.findOrCreateSheet()
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Sheet creation failed"
                    securePrefs.lastSyncError = msg
                    AppLogger.e(TAG, "Failed to create spreadsheet: $msg", result.exceptionOrNull())
                    return@withLock -1
                }
            }

            return@withLock try {
                val totalSynced = syncPendingLocations()
                if (totalSynced >= 0) {
                    securePrefs.lastSyncError = null
                }
                totalSynced
            } catch (e: Exception) {
                securePrefs.lastSyncError = e.message ?: "Sync failed"
                AppLogger.e(TAG, "Instant sync failed", e)
                -1
            }
        }
    }

    private suspend fun syncPendingLocations(): Int {
        val dao = database.locationPingDao()
        val unsyncedPings = dao.getUnsynced()

        if (unsyncedPings.isEmpty()) {
            AppLogger.d(TAG, "No pending locations to sync")
            return 0
        }

        AppLogger.i(TAG, "Syncing ${unsyncedPings.size} pending locations...")

        var totalSynced = 0
        var sheetRecoveredOnce = false
        val batches = unsyncedPings.chunked(BATCH_SIZE)

        for (batch in batches) {
            var result = sheetsRepository.appendLocationData(batch)

            // Recover automatically if user deleted the spreadsheet from Google Drive
            val err = result.exceptionOrNull()
            if (result.isFailure && !sheetRecoveredOnce && err is GoogleJsonResponseException && err.statusCode == 404) {
                sheetRecoveredOnce = true
                AppLogger.w(TAG, "Spreadsheet returned 404; re-creating spreadsheet and retrying")
                securePrefs.spreadsheetId = null
                if (sheetsRepository.findOrCreateSheet().isSuccess) {
                    result = sheetsRepository.appendLocationData(batch)
                }
            }

            if (result.isSuccess) {
                val ids = batch.map { it.id }
                dao.markSynced(ids)
                totalSynced += batch.size
                AppLogger.i(TAG, "Synced batch: ${batch.size} locations")
            } else {
                val failure = result.exceptionOrNull()
                securePrefs.lastSyncError = when (failure) {
                    is UserRecoverableAuthIOException -> "Google authorization expired — reopen app to sign in"
                    is GoogleJsonResponseException -> "Sheets API error (${failure.statusCode})"
                    else -> failure?.message ?: "Upload failed"
                }
                AppLogger.e(TAG, "Batch sync failed: ${securePrefs.lastSyncError}", failure)
                return if (totalSynced > 0) totalSynced else -1
            }
        }

        return totalSynced
    }
}
