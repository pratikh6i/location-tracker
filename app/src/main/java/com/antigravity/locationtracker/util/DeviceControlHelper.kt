package com.antigravity.locationtracker.util

import android.Manifest
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.antigravity.locationtracker.admin.TrackerDeviceAdminReceiver
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/**
 * Inspects and enforces anti-theft device protections (Location, Mobile Data,
 * Battery Optimization, Exact Alarms, Device Owner / WRITE_SECURE_SETTINGS).
 *
 * Note on Android OS security model:
 * - Standard apps cannot silently toggle System Location or Mobile Data ON from
 *   ordinary runtime permissions.
 * - If provisioned as Device Owner (via `adb shell dpm set-device-owner ...`),
 *   this app can silently enable Location (Android 11+) and lock Location,
 *   Mobile Data, Airplane Mode, Safe Boot, Factory Reset, and Uninstall.
 * - If granted `WRITE_SECURE_SETTINGS` via ADB, it can attempt to re-enable
 *   Location Services automatically on supported Android builds.
 */
object DeviceControlHelper {

    private const val TAG = "DeviceControlHelper"

    data class ProtectionStatus(
        val fineLocationGranted: Boolean,
        val backgroundLocationGranted: Boolean,
        val locationServiceEnabled: Boolean,
        val mobileDataEnabled: Boolean,
        val internetAvailable: Boolean,
        val batteryOptimizationIgnored: Boolean,
        val exactAlarmAllowed: Boolean,
        val isDeviceOwner: Boolean,
        val hasWriteSecureSettings: Boolean
    ) {
        val isReadyForReliableTracking: Boolean
            get() = fineLocationGranted &&
                backgroundLocationGranted &&
                locationServiceEnabled &&
                batteryOptimizationIgnored

        val canAutoEnableLocation: Boolean
            get() = isDeviceOwner || hasWriteSecureSettings
    }

    fun getAdminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, TrackerDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed checking Device Owner status", e)
            false
        }
    }

    fun isDeviceAdmin(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(getAdminComponent(context))
        } catch (e: Exception) {
            false
        }
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            LocationManagerCompat.isLocationEnabled(lm)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed checking location enabled", e)
            false
        }
    }

    fun isMobileDataEnabled(context: Context): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.isDataEnabled
            } else {
                Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
            }
        } catch (_: SecurityException) {
            try {
                Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
            } catch (_: Exception) {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed checking internet availability", e)
            false
        }
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun getProtectionStatus(context: Context): ProtectionStatus {
        return ProtectionStatus(
            fineLocationGranted = hasFineLocationPermission(context),
            backgroundLocationGranted = hasBackgroundLocationPermission(context),
            locationServiceEnabled = isLocationEnabled(context),
            mobileDataEnabled = isMobileDataEnabled(context),
            internetAvailable = isInternetAvailable(context),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
            exactAlarmAllowed = canScheduleExactAlarms(context),
            isDeviceOwner = isDeviceOwner(context),
            hasWriteSecureSettings = hasWriteSecureSettings(context)
        )
    }

    /**
     * Attempts to enable system Location Services automatically using privileged
     * APIs when available (Device Owner on Android 11+ or WRITE_SECURE_SETTINGS).
     * Returns true if location services are enabled afterwards.
     */
    @Suppress("DEPRECATION")
    fun ensureLocationEnabled(context: Context): Boolean {
        if (isLocationEnabled(context)) return true

        if (isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.setLocationEnabled(getAdminComponent(context), true)
                AppLogger.i(TAG, "Enabled Location Services via DevicePolicyManager")
            } catch (e: Exception) {
                AppLogger.e(TAG, "DevicePolicyManager.setLocationEnabled failed", e)
            }
        }

        if (!isLocationEnabled(context) && hasWriteSecureSettings(context)) {
            try {
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                )
                AppLogger.i(TAG, "Attempted enabling Location Services via WRITE_SECURE_SETTINGS")
            } catch (e: Exception) {
                AppLogger.w(TAG, "WRITE_SECURE_SETTINGS location toggle failed", e)
            }
        }

        return isLocationEnabled(context)
    }

    /**
     * Applies anti-theft restrictions when this app is provisioned as Device Owner.
     * Locks Location ON, prevents disabling Mobile Data / Airplane Mode / Safe Boot /
     * Factory Reset, and blocks uninstalling the tracker.
     */
    fun applyAntiTheftPolicies(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getAdminComponent(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setLocationEnabled(admin, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_LOCATION)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_AIRPLANE_MODE)
            }
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.setUninstallBlocked(admin, context.packageName, true)
            AppLogger.i(TAG, "Anti-theft Device Owner policies applied successfully")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to apply Device Owner anti-theft policies", e)
            false
        }
    }

    fun buildLocationSettingsRequest(): LocationSettingsRequest {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
        return LocationSettingsRequest.Builder()
            .addLocationRequest(req)
            .setAlwaysShow(true)
            .build()
    }
}
