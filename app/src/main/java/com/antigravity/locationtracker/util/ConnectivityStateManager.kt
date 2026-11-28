package com.antigravity.locationtracker.util

import android.content.Context
import android.net.ConnectivityManager
import com.antigravity.locationtracker.location.LocationForegroundService
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Manages connectivity state for sync:
 * 1. Snapshots current WiFi / location state
 * 2. Enables connectivity (WiFi via Device Owner, or cellular via requestNetwork)
 * 3. After sync completes, restores ONLY what WE changed — leaves the rest untouched
 *
 * Mobile data toggle is NOT possible without Device Owner + system privileges,
 * so we rely on ConnectivityManager.requestNetwork(TRANSPORT_CELLULAR) to bind
 * our process to SIM data even when WiFi is preferred (but offline).
 */
object ConnectivityStateManager {

    private const val TAG = "ConnectivityState"
    private const val CELLULAR_WAIT_MS = 12_000L
    private const val WIFI_CONNECT_WAIT_MS = 8_000L

    data class ConnectivitySnapshot(
        val wifiWasEnabled: Boolean,
        val locationWasEnabled: Boolean,
        val mobileDataWasEnabled: Boolean,
        val weEnabledWifi: Boolean = false,
        val weEnabledLocation: Boolean = false,
        val weEnabledMobileData: Boolean = false,
        val boundToCellular: Boolean = false
    )

    /**
     * Ensures internet and location are available.
     * Returns (hasInternet, snapshot-of-what-we-changed).
     * Always call restoreState(snapshot) in a finally block after sync.
     */
    suspend fun enableAndSnapshot(context: Context): Pair<Boolean, ConnectivitySnapshot> {
        val wifiWas    = DeviceControlHelper.isWifiEnabled(context)
        val locWas     = DeviceControlHelper.isLocationEnabled(context)
        val dataWas    = DeviceControlHelper.isMobileDataEnabled(context)
        var snapshot   = ConnectivitySnapshot(
            wifiWasEnabled       = wifiWas,
            locationWasEnabled   = locWas,
            mobileDataWasEnabled = dataWas
        )

        // 1. Enable location if needed (Device Owner / WRITE_SECURE_SETTINGS only)
        val locEnabled = DeviceControlHelper.ensureLocationEnabled(context)
        if (locEnabled && !locWas) {
            snapshot = snapshot.copy(weEnabledLocation = true)
            AppLogger.i(TAG, "Location enabled by us")
        }

        // 2. If internet already available → done
        if (DeviceControlHelper.isInternetAvailable(context)) {
            AppLogger.d(TAG, "Internet already available")
            return Pair(true, snapshot)
        }

        // 3. If Device Owner and WiFi is off → try enabling WiFi
        if (!wifiWas && DeviceControlHelper.isDeviceOwner(context)) {
            val wifiOn = DeviceControlHelper.enableWifi(context)
            if (wifiOn) {
                snapshot = snapshot.copy(weEnabledWifi = true)
                AppLogger.i(TAG, "WiFi enabled by us — waiting for connection")
                val connected = waitForInternet(context, WIFI_CONNECT_WAIT_MS)
                if (connected) return Pair(true, snapshot)
            }
        }

        // 4. Try enabling mobile data (requires MODIFY_PHONE_STATE or WRITE_SECURE_SETTINGS via ADB)
        if (!dataWas && (DeviceControlHelper.hasMobileDataPermission(context) ||
                         DeviceControlHelper.hasWriteSecureSettings(context))) {
            AppLogger.i(TAG, "Attempting to enable mobile data")
            val enabled = DeviceControlHelper.enableMobileData(context)
            if (enabled) {
                snapshot = snapshot.copy(weEnabledMobileData = true)
                // Give the modem time to register data connection
                delay(3_500L)
            }
        }

        // 5. Try binding process to cellular network (works if SIM data plan exists / just enabled)
        AppLogger.i(TAG, "Trying cellular network binding")
        val cellularOk = requestCellularNetwork(context)
        if (cellularOk) {
            snapshot = snapshot.copy(boundToCellular = true)
            return Pair(true, snapshot)
        }

        AppLogger.w(TAG, "No internet available via any path")
        return Pair(false, snapshot)
    }

    /**
     * Reverts ONLY what we enabled. Must be called in a finally block.
     * Does NOT turn off location if the foreground service is currently running
     * (service needs location for passive fixes).
     */
    fun restoreState(context: Context, snapshot: ConnectivitySnapshot) {
        try {
            if (snapshot.boundToCellular) {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.bindProcessToNetwork(null)
                AppLogger.i(TAG, "Unbound from cellular network")
            }
            if (snapshot.weEnabledWifi && DeviceControlHelper.isDeviceOwner(context)) {
                DeviceControlHelper.disableWifi(context)
                AppLogger.i(TAG, "WiFi restored to OFF")
            }
            if (snapshot.weEnabledMobileData) {
                DeviceControlHelper.disableMobileData(context)
                AppLogger.i(TAG, "Mobile data restored to OFF")
            }
            if (snapshot.weEnabledLocation) {
                // Only turn location back off if the foreground service isn't using it
                val serviceRunning = LocationForegroundService.isServiceRunning()

                if (!serviceRunning) {
                    DeviceControlHelper.disableLocation(context)
                    AppLogger.i(TAG, "Location restored to OFF")
                } else {
                    AppLogger.d(TAG, "Service running — leaving location ON")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error restoring connectivity state", e)
        }
    }

    // -------------------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------------------

    private suspend fun waitForInternet(context: Context, timeoutMs: Long): Boolean {
        val step = 500L
        val iterations = (timeoutMs / step).toInt()
        repeat(iterations) {
            if (DeviceControlHelper.isInternetAvailable(context)) return true
            delay(step)
        }
        return false
    }

    /**
     * Requests a dedicated cellular network and binds process traffic to it.
     * This works even when WiFi is enabled but offline.
     * Returns true if SIM data is reachable.
     */
    private suspend fun requestCellularNetwork(context: Context): Boolean {
        // Check if cellular is already up without binding
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val existingCellular = cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        if (existingCellular != null) {
            cm.bindProcessToNetwork(existingCellular)
            AppLogger.i(TAG, "Bound to already-connected cellular network")
            return true
        }

        return withTimeoutOrNull(CELLULAR_WAIT_MS) {
            suspendCancellableCoroutine { cont ->
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        cm.bindProcessToNetwork(network)
                        AppLogger.i(TAG, "Cellular became available, process bound")
                        safeResume(cont, true)
                        try { cm.unregisterNetworkCallback(this) } catch (_: Exception) {}
                    }
                    override fun onUnavailable() {
                        AppLogger.w(TAG, "Cellular unavailable (no SIM data?)")
                        safeResume(cont, false)
                    }
                    override fun onLost(network: Network) {
                        // Not cancelling continuation here; onAvailable already resolved it
                    }
                }

                try {
                    cm.requestNetwork(request, callback, CELLULAR_WAIT_MS.toInt())
                    cont.invokeOnCancellation {
                        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "requestNetwork failed", e)
                    safeResume(cont, false)
                }
            }
        } ?: run {
            AppLogger.w(TAG, "Cellular request timed out")
            false
        }
    }

    private fun <T> safeResume(cont: kotlinx.coroutines.CancellableContinuation<T>, value: T) {
        if (cont.isActive) cont.resume(value)
    }
}
