package com.antigravity.locationtracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.location.LocationHelper
import com.antigravity.locationtracker.util.AppLogger

/**
 * WorkManager watchdog and offline-flush worker.
 * - Captures a fresh location if explicitly requested OR if the configured interval
 *   has elapsed without a recent ping (e.g., if the OEM killed the service).
 * - Flushes all unsynced pings chronologically via SyncManager.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val KEY_CAPTURE_EVENT = "capture_event"
    }

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val securePrefs = SecurePreferences.getInstance(appContext)
        AppLogger.i(TAG, "SyncWorker started")

        if (securePrefs.userEmail == null || !securePrefs.isSetupComplete) {
            AppLogger.d(TAG, "Setup incomplete or signed out; skipping")
            return Result.success()
        }

        val explicitEvent = inputData.getString(KEY_CAPTURE_EVENT)
        val intervalMs = securePrefs.getIntervalMillis()
        val elapsedSinceLast = System.currentTimeMillis() - securePrefs.lastLocationTime
        val isOverdue = securePrefs.isServiceEnabled &&
            (securePrefs.lastLocationTime == 0L || elapsedSinceLast >= intervalMs)

        if (explicitEvent != null || isOverdue) {
            val eventType = explicitEvent ?: "WATCHDOG"
            AppLogger.i(TAG, "Capturing location in SyncWorker ($eventType, elapsed=${elapsedSinceLast / 1000}s)")
            LocationHelper.captureAndSaveLocation(appContext, eventType)
        }

        val syncManager = SyncManager(appContext, securePrefs)
        if (!syncManager.isNetworkAvailable()) {
            AppLogger.d(TAG, "No network yet; will retry when online")
            return Result.retry()
        }

        val synced = syncManager.syncNow()
        if (synced < 0) {
            AppLogger.w(TAG, "Sync failed; scheduling retry")
            return Result.retry()
        }

        // Clean up synced entries older than 30 days
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        AppDatabase.getInstance(appContext).locationPingDao().deleteOldSynced(thirtyDaysAgo)

        return Result.success()
    }
}
