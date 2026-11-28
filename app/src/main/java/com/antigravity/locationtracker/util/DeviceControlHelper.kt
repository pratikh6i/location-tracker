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
import android.net.wifi.WifiManager
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
 * Inspects and enforces anti-theft device protections.
 *
 * WiFi toggle (enableWifi / disableWifi) silently fails on Android 10+ for
 * non-system apps that are NOT Device Owner. All other reads work without privilege.
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
            get() = fineLocationGranted && backgroundLocationGranted &&
                    locationServiceEnabled && batteryOptimizationIgnored

        val canAutoEnableLocation: Boolean
            get() = isDeviceOwner || hasWriteSecureSettings
    }

    // -------------------------------------------------------------------------------------
    // Admin / privilege checks
    // -------------------------------------------------------------------------------------

    fun getAdminComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, TrackerDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isDeviceOwnerApp(context.packageName)
    } catch (e: Exception) { AppLogger.w(TAG, "Device Owner check failed", e); false }

    fun isDeviceAdmin(context: Context): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(getAdminComponent(context))
    } catch (_: Exception) { false }

    fun hasWriteSecureSettings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

    // -------------------------------------------------------------------------------------
    // Location
    // -------------------------------------------------------------------------------------

    fun isLocationEnabled(context: Context): Boolean = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        LocationManagerCompat.isLocationEnabled(lm)
    } catch (e: Exception) { AppLogger.w(TAG, "Location enabled check failed", e); false }

    fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    /**
     * Attempts to enable location via Device Owner (Android 11+) or WRITE_SECURE_SETTINGS.
     * Returns true if location is enabled after the call.
     */
    @Suppress("DEPRECATION")
    fun ensureLocationEnabled(context: Context): Boolean {
        if (isLocationEnabled(context)) return true

        if (isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.setLocationEnabled(getAdminComponent(context), true)
                AppLogger.i(TAG, "Location enabled via DevicePolicyManager")
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
                AppLogger.i(TAG, "Location enabled via WRITE_SECURE_SETTINGS")
            } catch (e: Exception) {
                AppLogger.w(TAG, "WRITE_SECURE_SETTINGS location enable failed", e)
            }
        }

        return isLocationEnabled(context)
    }

    /**
     * Disables location service. Requires Device Owner or WRITE_SECURE_SETTINGS.
     */
    @Suppress("DEPRECATION")
    fun disableLocation(context: Context): Boolean {
        if (isDeviceOwner(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                dpm.setLocationEnabled(getAdminComponent(context), false)
                AppLogger.i(TAG, "Location disabled via DevicePolicyManager")
                true
            } catch (e: Exception) {
                AppLogger.e(TAG, "DevicePolicyManager.setLocationEnabled(false) failed", e)
                false
            }
        }
        if (hasWriteSecureSettings(context)) {
            return try {
                Settings.Secure.putInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF
                )
                AppLogger.i(TAG, "Location disabled via WRITE_SECURE_SETTINGS")
                true
            } catch (e: Exception) {
                AppLogger.w(TAG, "WRITE_SECURE_SETTINGS location disable failed", e)
                false
            }
        }
        AppLogger.w(TAG, "Cannot disable location: no Device Owner or WRITE_SECURE_SETTINGS")
        return false
    }

    // -------------------------------------------------------------------------------------
    // WiFi  (Device Owner only on API 29+)
    // -------------------------------------------------------------------------------------

    fun isWifiEnabled(context: Context): Boolean = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.isWifiEnabled
    } catch (_: Exception) { false }

    /**
     * Enables WiFi. Requires Device Owner on Android 10+; silently fails otherwise.
     */
    @Suppress("DEPRECATION")
    fun enableWifi(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val result = wm.setWifiEnabled(true)
            AppLogger.i(TAG, "WiFi enable result: $result (Device Owner: ${isDeviceOwner(context)})")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "WiFi enable failed", e)
            false
        }
    }

    /**
     * Disables WiFi. Requires Device Owner on Android 10+; silently fails otherwise.
     */
    @Suppress("DEPRECATION")
    fun disableWifi(context: Context): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val result = wm.setWifiEnabled(false)
            AppLogger.i(TAG, "WiFi disable result: $result")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "WiFi disable failed", e)
            false
        }
    }

    // -------------------------------------------------------------------------------------
    // Network / connectivity
    // -------------------------------------------------------------------------------------

    fun isMobileDataEnabled(context: Context): Boolean = try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tm.isDataEnabled
        } else {
            @Suppress("DEPRECATION")
            Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
        }
    } catch (_: SecurityException) {
        try {
            @Suppress("DEPRECATION")
            Settings.Global.getInt(context.contentResolver, "mobile_data", 1) == 1
        } catch (_: Exception) { false }
    } catch (_: Exception) { false }

    fun isInternetAvailable(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
             caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     ||
             caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
             caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    } catch (e: Exception) { AppLogger.w(TAG, "Internet check failed", e); false }

    // -------------------------------------------------------------------------------------
    // Battery / alarm
    // -------------------------------------------------------------------------------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true

    // -------------------------------------------------------------------------------------
    // Combined status
    // -------------------------------------------------------------------------------------

    fun getProtectionStatus(context: Context): ProtectionStatus = ProtectionStatus(
        fineLocationGranted        = hasFineLocationPermission(context),
        backgroundLocationGranted  = hasBackgroundLocationPermission(context),
        locationServiceEnabled     = isLocationEnabled(context),
        mobileDataEnabled          = isMobileDataEnabled(context),
        internetAvailable          = isInternetAvailable(context),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
        exactAlarmAllowed          = canScheduleExactAlarms(context),
        isDeviceOwner              = isDeviceOwner(context),
        hasWriteSecureSettings     = hasWriteSecureSettings(context)
    )

    // -------------------------------------------------------------------------------------
    // Anti-theft policies (Device Owner only)
    // -------------------------------------------------------------------------------------

    fun applyAntiTheftPolicies(context: Context): Boolean {
        if (!isDeviceOwner(context)) return false
        return try {
            val dpm   = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
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
            AppLogger.i(TAG, "Anti-theft Device Owner policies applied")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Anti-theft policy apply failed", e)
            false
        }
    }

    /** Locks screen immediately. Requires Device Admin to be active. */
    fun lockScreenNow(context: Context) {
        if (!isDeviceAdmin(context)) return
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.lockNow()
            AppLogger.i(TAG, "Screen locked via DevicePolicyManager")
        } catch (e: Exception) {
            AppLogger.w(TAG, "lockNow failed", e)
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
