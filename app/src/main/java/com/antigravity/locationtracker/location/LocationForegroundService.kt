package com.antigravity.locationtracker.location

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.antigravity.locationtracker.AntigravityApp
import com.antigravity.locationtracker.MainActivity
import com.antigravity.locationtracker.R
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.sync.SyncManager
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.DeviceControlHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground service for continuous & Doze-proof location tracking.
 *
 * Key invariants:
 * - captureMutex prevents concurrent / duplicate captures
 * - MANUAL pings are debounced (10 s min gap) to prevent double-tap duplicates
 * - SyncManager handles connectivity enable + state restore internally
 * - If securePrefs.lockAfterSync is true and Device Admin is active, screen locks post-sync
 */
class LocationForegroundService : Service() {

    companion object {
        private const val TAG                   = "LocationService"
        private const val NOTIFICATION_ID       = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val WAKELOCK_TIMEOUT_MS   = 60_000L
        private const val MANUAL_DEBOUNCE_MS    = 10_000L   // prevent double-tap duplicates

        const val ACTION_START           = "com.antigravity.locationtracker.action.START"
        const val ACTION_UPDATE_INTERVAL = "com.antigravity.locationtracker.action.UPDATE_INTERVAL"
        const val ACTION_PING_NOW        = "com.antigravity.locationtracker.action.PING_NOW"
        const val ACTION_STOP            = "com.antigravity.locationtracker.action.STOP"
        const val EXTRA_EVENT_TYPE       = "extra_event_type"

        @Volatile private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationForegroundService::class.java).apply { action = ACTION_START }
            )
        }

        fun updateInterval(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_INTERVAL
            }
            if (isRunning) context.startService(intent)
            else ContextCompat.startForegroundService(context, intent)
        }

        fun pingNow(context: Context, eventType: String = "MANUAL") {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationForegroundService::class.java).apply {
                    action = ACTION_PING_NOW
                    putExtra(EXTRA_EVENT_TYPE, eventType)
                }
            )
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            if (isRunning) context.startService(intent)
            else {
                SecurePreferences.getInstance(context).isServiceEnabled = false
                TrackingScheduler.cancelAll(context)
            }
        }
    }

    private val serviceScope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captureMutex  = Mutex()

    private lateinit var fusedLocationClient : FusedLocationProviderClient
    private lateinit var locationCallback    : LocationCallback
    private lateinit var securePrefs         : SecurePreferences
    private lateinit var syncManager         : SyncManager

    private var connectivityManager  : ConnectivityManager?                     = null
    private var networkCallback      : ConnectivityManager.NetworkCallback?     = null
    private var locationUpdatesActive: Boolean                                   = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Service created")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        securePrefs         = SecurePreferences.getInstance(this)
        syncManager         = SyncManager(this, securePrefs)
        setupLocationCallback()
        registerNetworkFlushCallback()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        AppLogger.i(TAG, "onStartCommand action=$action")

        // Always call startForeground immediately — required on Android 12+
        ensureForeground("Tracking: ${securePrefs.formatIntervalDisplay()}")

        if (action == ACTION_STOP) {
            securePrefs.isServiceEnabled = false
            TrackingScheduler.cancelAll(this)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!DeviceControlHelper.hasFineLocationPermission(this)) {
            AppLogger.e(TAG, "Location permission missing — stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        securePrefs.isServiceEnabled = true
        DeviceControlHelper.applyAntiTheftPolicies(this)

        when (action) {
            ACTION_UPDATE_INTERVAL -> {
                restartLocationUpdates()
                TrackingScheduler.scheduleAll(this)
                updateNotification("Interval: ${securePrefs.formatIntervalDisplay()}")
            }

            ACTION_PING_NOW -> {
                val eventType = intent?.getStringExtra(EXTRA_EVENT_TYPE) ?: "MANUAL"

                // Debounce MANUAL pings to prevent double-tap duplicate rows
                if (eventType == "MANUAL") {
                    val since = System.currentTimeMillis() - securePrefs.lastPingAttemptTime
                    if (since < MANUAL_DEBOUNCE_MS && securePrefs.lastPingAttemptTime > 0) {
                        AppLogger.d(TAG, "Debouncing duplicate MANUAL ping (${since}ms < ${MANUAL_DEBOUNCE_MS}ms)")
                        return START_STICKY
                    }
                }
                securePrefs.lastPingAttemptTime = System.currentTimeMillis()

                if (!locationUpdatesActive) restartLocationUpdates()
                TrackingScheduler.scheduleAll(this)
                triggerActiveCaptureAndSync(eventType)
            }

            else -> { // ACTION_START
                restartLocationUpdates()
                TrackingScheduler.scheduleAll(this)
                // Capture an initial fix if no recent one
                val ageMs = System.currentTimeMillis() - securePrefs.lastLocationTime
                if (securePrefs.lastLocationTime == 0L || ageMs >= securePrefs.getIntervalMillis()) {
                    triggerActiveCaptureAndSync("STARTUP")
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(TAG, "Service destroyed")
        // Do NOT clear isServiceEnabled — OS kills must auto-resume!
        if (locationUpdatesActive) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
            locationUpdatesActive = false
        }
        networkCallback?.let { cb ->
            runCatching { connectivityManager?.unregisterNetworkCallback(cb) }
        }
        serviceScope.cancel()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Core capture + sync ───────────────────────────────────────────────────────────

    private fun triggerActiveCaptureAndSync(eventType: String) {
        serviceScope.launch {
            val pm       = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:PingWakeLock")
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
            try {
                captureMutex.withLock {
                    // Capture
                    val ping = LocationHelper.captureAndSaveLocation(this@LocationForegroundService, eventType)

                    if (ping != null) {
                        clearAlertNotification()
                        updateNotification(
                            "%.4f, %.4f • %s • 🔋%d%%".format(
                                ping.latitude, ping.longitude, eventType, ping.batteryLevel
                            )
                        )
                        // Sync — SyncManager handles connectivity enable/restore internally
                        syncManager.syncNow()

                        // Lock screen if requested (Device Admin must be active)
                        if (securePrefs.lockAfterSync) {
                            DeviceControlHelper.lockScreenNow(this@LocationForegroundService)
                        }
                    } else {
                        checkAndAlertDisabledServices()
                    }
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    // ── Location callback (passive / continuous updates) ──────────────────────────────

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                // Rate-limit passive updates to avoid flooding between scheduled pings
                val minGapMs = (securePrefs.getIntervalMillis() * 0.75)
                    .toLong().coerceAtMost(60_000L)
                val since = System.currentTimeMillis() - securePrefs.lastLocationTime
                if (since < minGapMs) return

                serviceScope.launch {
                    captureMutex.withLock {
                        val ping = LocationHelper.saveRawLocation(
                            this@LocationForegroundService, location, "AUTO"
                        )
                        updateNotification(
                            "%.4f, %.4f • 🔋%d%%".format(
                                ping.latitude, ping.longitude, ping.batteryLevel
                            )
                        )
                        // Auto-sync the new pin when network is available
                        if (syncManager.isNetworkAvailable()) {
                            syncManager.syncNow()
                        } else {
                            TrackingScheduler.scheduleNetworkFlush(this@LocationForegroundService)
                        }
                    }
                }
            }
        }
    }

    private fun restartLocationUpdates() {
        if (locationUpdatesActive) {
            runCatching { fusedLocationClient.removeLocationUpdates(locationCallback) }
            locationUpdatesActive = false
        }

        val intervalMs  = securePrefs.getIntervalMillis()
        val minUpdateMs = (intervalMs * 0.8).toLong().coerceAtLeast(1_000L)
        val priority    = if (intervalMs <= 5 * 60 * 1_000L)
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minUpdateMs)
            .setMaxUpdateDelayMillis(intervalMs)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
            locationUpdatesActive = true
            AppLogger.i(TAG, "Location updates registered (${securePrefs.formatIntervalDisplay()})")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Location updates registration failed", e)
            stopSelf()
        }
    }

    // ── Network flush callback ────────────────────────────────────────────────────────

    private fun registerNetworkFlushCallback() {
        try {
            val cm       = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager = cm
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    AppLogger.i(TAG, "Network available — flushing pending pings")
                    serviceScope.launch { syncManager.syncNow() }
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
            networkCallback = callback
        } catch (e: Exception) {
            AppLogger.w(TAG, "NetworkCallback registration failed", e)
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────────────

    private fun ensureForeground(text: String) {
        val notification = createNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AntigravityApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun showAlertNotification(message: String) {
        val pi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, AntigravityApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("wya Protection Alert")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun clearAlertNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ALERT_NOTIFICATION_ID)
    }

    private fun checkAndAlertDisabledServices() {
        val status = DeviceControlHelper.getProtectionStatus(this)
        if (!status.locationServiceEnabled) {
            showAlertNotification("Location is OFF — tap to fix and re-enable tracking")
        }
    }
}
