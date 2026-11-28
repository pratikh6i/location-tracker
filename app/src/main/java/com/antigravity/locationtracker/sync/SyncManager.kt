package com.antigravity.locationtracker.sync

import android.content.Context
import android.net.ConnectivityManager
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.ConnectivityStateManager
import com.antigravity.locationtracker.util.DeviceControlHelper
import com.antigravity.locationtracker.util.SmsFallbackManager
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Mutex-protected synchronisation manager.
 *
 * Before each sync attempt:
 *   1. Snapshots WiFi/location state
 *   2. Tries to ensure internet (WiFi → cellular fallback)
 *   3. Runs the Sheets upload
 *   4. Always restores state in finally (if stateRestoreEnabled)
 *   5. Checks SMS fallback if sync fails and 24 h threshold exceeded
 */
class SyncManager(
    private val context: Context,
    private val securePrefs: SecurePreferences
) {
    companion object {
        private const val TAG        = "SyncManager"
        private const val BATCH_SIZE = 50
        private val syncMutex        = Mutex()
    }

    private val database         = AppDatabase.getInstance(context.applicationContext)
    private val sheetsRepository = SheetsRepository(context.applicationContext, securePrefs)

    fun isNetworkAvailable(): Boolean = DeviceControlHelper.isInternetAvailable(context)

    /**
     * Syncs all pending locations to Sheets under a process-wide mutex.
     * Returns the number of rows uploaded, or -1 on failure.
     */
    suspend fun syncNow(): Int = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            if (securePrefs.userEmail == null) {
                AppLogger.w(TAG, "Not signed in — skipping sync")
                return@withLock -1
            }

            // ── 1. Connectivity snapshot + enable ──────────────────────────────────────
            val (hasInternet, snapshot) = ConnectivityStateManager.enableAndSnapshot(context)

            if (!hasInternet) {
                AppLogger.w(TAG, "No internet after all enable attempts — trying SMS fallback")
                SmsFallbackManager.checkAndSendIfNeeded(context)
                return@withLock -1
            }

            // ── 2. Upload ──────────────────────────────────────────────────────────────
            return@withLock try {
                if (!securePrefs.hasSpreadsheet()) {
                    AppLogger.w(TAG, "No sheet configured — discovering/creating")
                    val result = sheetsRepository.findOrCreateSheet()
                    if (result.isFailure) {
                        val msg = result.exceptionOrNull()?.message ?: "Sheet creation failed"
                        securePrefs.lastSyncError = msg
                        AppLogger.e(TAG, "Sheet create failed: $msg", result.exceptionOrNull())
                        return@withLock -1
                    }
                }

                val totalSynced = syncPendingLocations()
                if (totalSynced >= 0) {
                    securePrefs.lastSyncError     = null
                    securePrefs.lastSheetSyncTime = System.currentTimeMillis()
                    AppLogger.i(TAG, "Sync complete: $totalSynced rows")
                } else {
                    SmsFallbackManager.checkAndSendIfNeeded(context)
                }
                totalSynced
            } catch (e: Exception) {
                securePrefs.lastSyncError = e.message ?: "Sync failed"
                AppLogger.e(TAG, "syncNow exception", e)
                SmsFallbackManager.checkAndSendIfNeeded(context)
                -1
            } finally {
                // ── 3. Always restore state ────────────────────────────────────────────
                if (securePrefs.stateRestoreEnabled) {
                    ConnectivityStateManager.restoreState(context, snapshot)
                } else {
                    // Still unbind cellular to restore normal routing
                    if (snapshot.boundToCellular) {
                        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                            as android.net.ConnectivityManager
                        cm.bindProcessToNetwork(null)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------------------

    private suspend fun syncPendingLocations(): Int {
        val dao          = database.locationPingDao()
        val unsyncedList = dao.getUnsynced()
        if (unsyncedList.isEmpty()) {
            AppLogger.d(TAG, "No pending locations")
            return 0
        }

        AppLogger.i(TAG, "Uploading ${unsyncedList.size} pending location(s)")
        var totalSynced       = 0
        var sheetRecoveredOnce = false

        for (batch in unsyncedList.chunked(BATCH_SIZE)) {
            var result = sheetsRepository.appendLocationData(batch)

            // Auto-recover if sheet was deleted from Drive
            val err = result.exceptionOrNull()
            if (!result.isSuccess && !sheetRecoveredOnce &&
                err is GoogleJsonResponseException && err.statusCode == 404
            ) {
                sheetRecoveredOnce = true
                AppLogger.w(TAG, "Sheet 404 — re-creating")
                securePrefs.spreadsheetId = null
                if (sheetsRepository.findOrCreateSheet().isSuccess) {
                    result = sheetsRepository.appendLocationData(batch)
                }
            }

            if (result.isSuccess) {
                dao.markSynced(batch.map { it.id })
                totalSynced += batch.size
            } else {
                val failure = result.exceptionOrNull()
                securePrefs.lastSyncError = when (failure) {
                    is UserRecoverableAuthIOException ->
                        "Google auth expired — open app to re-sign in"
                    is GoogleJsonResponseException   ->
                        "Sheets API error (${failure.statusCode})"
                    is HttpResponseException         ->
                        "HTTP ${failure.statusCode}: ${failure.statusMessage}"
                    else -> failure?.message ?: "Upload failed"
                }
                AppLogger.e(TAG, "Batch upload failed: ${securePrefs.lastSyncError}", failure)
                return if (totalSynced > 0) totalSynced else -1
            }
        }

        // Clean up rows older than 30 days that are already synced
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.deleteOldSynced(cutoff)

        return totalSynced
    }
}
