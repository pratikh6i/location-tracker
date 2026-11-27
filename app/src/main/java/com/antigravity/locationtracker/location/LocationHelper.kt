package com.antigravity.locationtracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.db.LocationPingEntity
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.DeviceControlHelper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared high-accuracy location capture helper used by the Foreground Service,
 * AlarmManager wakeup, and WorkManager watchdog.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    private const val FRESH_FIX_TIMEOUT_MS = 14_000L

    /**
     * Attempts to ensure location is enabled (if privileged), acquires a fresh
     * high-accuracy location with multi-provider fallbacks, persists it to Room,
     * and updates SecurePreferences.
     */
    @SuppressLint("MissingPermission")
    suspend fun captureAndSaveLocation(
        context: Context,
        eventType: String
    ): LocationPingEntity? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val securePrefs = SecurePreferences.getInstance(appContext)
        securePrefs.lastPingAttemptTime = System.currentTimeMillis()

        if (!DeviceControlHelper.hasFineLocationPermission(appContext)) {
            AppLogger.e(TAG, "Cannot capture location ($eventType): location permission not granted")
            return@withContext null
        }

        // Attempt auto-enable via Device Owner or WRITE_SECURE_SETTINGS if available
        val locationEnabled = DeviceControlHelper.ensureLocationEnabled(appContext)
        if (!locationEnabled) {
            AppLogger.w(TAG, "System location is OFF and auto-enable is unavailable ($eventType)")
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        var bestLocation: Location? = null

        // 1. Fast fresh high-accuracy fix
        val cts = CancellationTokenSource()
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(12_000L)
                .setMaxUpdateAgeMillis(30_000L)
                .build()

            bestLocation = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
                fusedClient.getCurrentLocation(request, cts.token).await()
            }
            if (bestLocation != null) {
                AppLogger.i(
                    TAG,
                    "Fresh Fused fix ($eventType): ${bestLocation.latitude}, ${bestLocation.longitude} (±${bestLocation.accuracy}m)"
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "getCurrentLocation failed ($eventType)", e)
        } finally {
            cts.cancel()
        }

        // 2. Fallback to Fused lastLocation
        if (bestLocation == null) {
            try {
                bestLocation = withTimeoutOrNull(3_000L) {
                    fusedClient.lastLocation.await()
                }
                if (bestLocation != null) {
                    AppLogger.w(TAG, "Using Fused lastLocation fallback ($eventType)")
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Fused lastLocation failed", e)
            }
        }

        // 3. Fallback to Android LocationManager providers (pick most recent)
        if (bestLocation == null) {
            bestLocation = getBestLastKnownFromLocationManager(appContext)
            if (bestLocation != null) {
                AppLogger.w(TAG, "Using LocationManager fallback ($eventType): ${bestLocation.provider}")
            }
        }

        val location = bestLocation ?: run {
            AppLogger.e(TAG, "No location fix available from any provider ($eventType)")
            return@withContext null
        }

        return@withContext saveLocationEntity(appContext, location, eventType)
    }

    @SuppressLint("MissingPermission")
    suspend fun saveRawLocation(
        context: Context,
        location: Location,
        eventType: String
    ): LocationPingEntity = withContext(Dispatchers.IO) {
        saveLocationEntity(context.applicationContext, location, eventType)
    }

    private suspend fun saveLocationEntity(
        context: Context,
        location: Location,
        eventType: String
    ): LocationPingEntity {
        val securePrefs = SecurePreferences.getInstance(context)
        val database = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        val battery = getBatteryLevel(context)

        val ping = LocationPingEntity(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed else null,
            timestamp = now,
            batteryLevel = battery,
            eventType = eventType
        )

        val id = database.locationPingDao().insert(ping)
        securePrefs.lastLocationTime = now
        securePrefs.lastLatitude = location.latitude
        securePrefs.lastLongitude = location.longitude

        AppLogger.i(TAG, "Saved ping id=$id ($eventType) lat=${location.latitude}, lng=${location.longitude}, bat=$battery%")
        return ping.copy(id = id)
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownFromLocationManager(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers
                .mapNotNull { provider ->
                    runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        } catch (e: Exception) {
            AppLogger.w(TAG, "LocationManager fallback error", e)
            null
        }
    }

    fun getBatteryLevel(context: Context): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        } catch (_: Exception) {
            -1
        }
    }
}
