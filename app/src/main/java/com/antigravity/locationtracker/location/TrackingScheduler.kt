package com.antigravity.locationtracker.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.receiver.TrackingAlarmReceiver
import com.antigravity.locationtracker.sync.SyncWorker
import com.antigravity.locationtracker.util.AppLogger
import java.util.concurrent.TimeUnit

/**
 * Dual-redundant scheduler combining Doze-proof AlarmManager wakeups with
 * WorkManager periodic watchdogs so 4-hour pings reliably fire and sync.
 */
object TrackingScheduler {

    private const val TAG = "TrackingScheduler"
    private const val ALARM_REQUEST_CODE = 4001
    private const val PERIODIC_WATCHDOG_WORK = "location_sync_work"
    private const val IMMEDIATE_CAPTURE_WORK = "location_immediate_work"
    private const val NETWORK_FLUSH_WORK = "location_network_flush_work"

    fun scheduleAll(context: Context) {
        val appContext = context.applicationContext
        val prefs = SecurePreferences.getInstance(appContext)
        scheduleNextPing(appContext, prefs.getIntervalMillis())
        scheduleWatchdogWork(appContext)
    }

    fun scheduleNextPing(context: Context, intervalMs: Long) {
        val appContext = context.applicationContext
        val safeIntervalMs = intervalMs.coerceAtLeast(1_000L)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildAlarmPendingIntent(appContext)
        val triggerAtElapsed = SystemClock.elapsedRealtime() + safeIntervalMs

        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    pendingIntent
                )
                AppLogger.i(TAG, "Scheduled exact Doze-safe alarm in ${safeIntervalMs / 1000}s")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsed,
                    pendingIntent
                )
                AppLogger.i(TAG, "Scheduled inexact Doze-safe alarm in ${safeIntervalMs / 1000}s")
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsed,
                pendingIntent
            )
            AppLogger.w(TAG, "Exact alarm denied, fell back to setAndAllowWhileIdle", e)
        }
    }

    /**
     * Watchdog runs every 15 minutes regardless of network so it can capture
     * overdue offline GPS fixes if the OEM killed the service, and sync when online.
     */
    fun scheduleWatchdogWork(context: Context) {
        val watchdogRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WATCHDOG_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            watchdogRequest
        )
    }

    /**
     * Enqueues a network-constrained one-time flush so WorkManager wakes up
     * immediately once connectivity is restored after an offline period.
     */
    fun scheduleNetworkFlush(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val flushRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            NETWORK_FLUSH_WORK,
            ExistingWorkPolicy.KEEP,
            flushRequest
        )
    }

    /**
     * Fallback immediate capture + sync via expedited WorkManager job when
     * Android 14+ blocks starting a ForegroundService from background.
     */
    fun triggerImmediateWork(context: Context, eventType: String) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_CAPTURE_EVENT to eventType))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_CAPTURE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
        AppLogger.i(TAG, "Triggered expedited WorkManager capture ($eventType)")
    }

    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildAlarmPendingIntent(appContext))
        val wm = WorkManager.getInstance(appContext)
        wm.cancelUniqueWork(PERIODIC_WATCHDOG_WORK)
        wm.cancelUniqueWork(IMMEDIATE_CAPTURE_WORK)
        wm.cancelUniqueWork(NETWORK_FLUSH_WORK)
        AppLogger.i(TAG, "Cancelled all scheduled tracking alarms and workers")
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TrackingAlarmReceiver::class.java).apply {
            action = TrackingAlarmReceiver.ACTION_ALARM_PING
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
