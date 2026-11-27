package com.antigravity.locationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.location.LocationForegroundService
import com.antigravity.locationtracker.location.TrackingScheduler
import com.antigravity.locationtracker.util.AppLogger

/**
 * Broadcast receiver that restores location tracking after device reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appContext = context.applicationContext
        val securePrefs = SecurePreferences.getInstance(appContext)
        AppLogger.i(TAG, "Boot/update event ($action): setup=${securePrefs.isSetupComplete}, enabled=${securePrefs.isServiceEnabled}")

        if (securePrefs.isSetupComplete && securePrefs.isServiceEnabled) {
            TrackingScheduler.scheduleAll(appContext)
            try {
                LocationForegroundService.pingNow(appContext, "BOOT")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Background FGS start blocked on boot; scheduling WorkManager fallback", e)
                TrackingScheduler.triggerImmediateWork(appContext, "BOOT")
            }
        }
    }
}
