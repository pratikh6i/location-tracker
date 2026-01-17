package com.antigravity.locationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.location.LocationForegroundService

/**
 * Broadcast receiver that starts the location service after device boot.
 * Enables "set-and-forget" operation for elderly users.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.i(TAG, "Boot completed, checking if service should start")
                
                val securePrefs = SecurePreferences(context)
                
                // Only start if setup is complete and service was previously enabled
                if (securePrefs.isSetupComplete && securePrefs.isServiceEnabled) {
                    Log.i(TAG, "Starting location service after boot")
                    LocationForegroundService.start(context)
                } else {
                    Log.i(TAG, "Setup not complete or service not enabled, skipping auto-start")
                }
            }
        }
    }
}
