package com.antigravity.locationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.location.LocationForegroundService
import com.antigravity.locationtracker.location.TrackingScheduler
import com.antigravity.locationtracker.util.AppLogger

/**
 * AlarmManager wakeup receiver fired every tracking interval (default 4 hours)
 * even while the device is in Doze mode.
 */
class TrackingAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TrackingAlarmReceiver"
        const val ACTION_ALARM_PING = "com.antigravity.locationtracker.ACTION_ALARM_PING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_PING) return

        val appContext = context.applicationContext
        val prefs = SecurePreferences.getInstance(appContext)
        if (!prefs.isSetupComplete || !prefs.isServiceEnabled || prefs.userEmail == null) {
            AppLogger.d(TAG, "Alarm fired but tracking is disabled/unconfigured")
            return
        }

        AppLogger.i(TAG, "Scheduled alarm triggered — capturing fresh location")
        // Re-arm next alarm immediately so the 4-hour chain never breaks
        TrackingScheduler.scheduleNextPing(appContext, prefs.getIntervalMillis())

        try {
            LocationForegroundService.pingNow(appContext, "SCHEDULED")
        } catch (e: Exception) {
            // Android 12+/14+ may block background FGS start if battery/location exemptions are absent
            AppLogger.w(TAG, "FGS start blocked from alarm; falling back to WorkManager", e)
            TrackingScheduler.triggerImmediateWork(appContext, "SCHEDULED")
        }
    }
}
