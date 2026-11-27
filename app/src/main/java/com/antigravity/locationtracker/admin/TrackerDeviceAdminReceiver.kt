package com.antigravity.locationtracker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.DeviceControlHelper

/**
 * Device Admin / Device Owner receiver for anti-theft protection.
 * When provisioned as Device Owner via ADB on a dedicated device, this allows
 * silently enabling Location Services and locking Location, Mobile Data,
 * Airplane Mode, Safe Boot, and Factory Reset controls.
 */
class TrackerDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "TrackerDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AppLogger.i(TAG, "Device admin enabled; applying anti-theft policies if Device Owner")
        DeviceControlHelper.applyAntiTheftPolicies(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        AppLogger.w(TAG, "Device admin disabled")
    }
}
