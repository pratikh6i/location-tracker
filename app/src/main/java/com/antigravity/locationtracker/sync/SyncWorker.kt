package com.antigravity.locationtracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.location.LocationHelper
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.SmsFallbackManager

/**
 * WorkManager watchdog and offline-flush worker.
 *
 * - Captures a fresh location if explicitly requested OR if the configured interval
 *   has elapsed without a recent ping (OEM killed the foreground service).
 * - Flushes all pending pings via SyncManager (which handles connectivity + state restore).
 * - Runs SMS fallback check after every sync attempt.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG               = "SyncWorker"
        const val KEY_CAPTURE_EVENT         = "capture_event"
    }

    override suspend fun doWork(): Result {
        val appContext  = applicationContext
        val securePrefs = SecurePreferences.getInstance(appContext)
        AppLogger.i(TAG, "SyncWorker started")

        if (securePrefs.userEmail == null || !securePrefs.isSetupComplete) {
            AppLogger.d(TAG, "Setup incomplete or not signed in — skipping")
            return Result.success()
        }

        // ── 1. Capture location if needed ─────────────────────────────────────────────
        val explicitEvent = inputData.getString(KEY_CAPTURE_EVENT)
        val intervalMs    = securePrefs.getIntervalMillis()
        val elapsedSince  = System.currentTimeMillis() - securePrefs.lastLocationTime
        val isOverdue     = securePrefs.isServiceEnabled &&
            (securePrefs.lastLocationTime == 0L || elapsedSince >= intervalMs)

        if (explicitEvent != null || isOverdue) {
            val event = explicitEvent ?: "WATCHDOG"
            AppLogger.i(TAG, "Capturing location ($event, elapsed ${elapsedSince / 1000}s)")
            LocationHelper.captureAndSaveLocation(appContext, event)
        }

        // ── 2. Sync (SyncManager handles connectivity + state restore internally) ─────
        val syncManager = SyncManager(appContext, securePrefs)
        val synced      = syncManager.syncNow()

        if (synced < 0) {
            AppLogger.w(TAG, "Sync failed — SyncManager already attempted SMS fallback")
            return Result.retry()
        }

        return Result.success()
    }
}
