package com.antigravity.locationtracker.location

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.antigravity.locationtracker.AntigravityApp
import com.antigravity.locationtracker.MainActivity
import com.antigravity.locationtracker.R
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.db.LocationPingEntity
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.sync.SyncManager
import com.antigravity.locationtracker.sync.SyncWorker
import com.antigravity.locationtracker.util.AppLogger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service for continuous location tracking.
 * Uses FusedLocationProvider for efficient, battery-optimized location updates.
 * Syncs immediately when network is available.
 */
class LocationForegroundService : Service() {
    
    companion object {
        private const val TAG = "LocationService"
        private const val NOTIFICATION_ID = 1001
        
        // Work names
        private const val SYNC_WORK_NAME = "location_sync_work"
        
        @Volatile
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
        
        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var securePrefs: SecurePreferences
    private lateinit var database: AppDatabase
    private lateinit var syncManager: SyncManager
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Service created")
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        securePrefs = SecurePreferences(this)
        database = AppDatabase.getInstance(this)
        syncManager = SyncManager(this, securePrefs)
        
        setupLocationCallback()
        isRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i(TAG, "Service started")
        
        // Start as foreground service
        val notification = createNotification("Tracking active")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // Start location updates
        startLocationUpdates()
        
        // Schedule sync worker as fallback for when network is unavailable
        scheduleSyncWorker()
        
        // Mark service as enabled
        securePrefs.isServiceEnabled = true
        
        return START_STICKY
    }
    
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    AppLogger.i(TAG, "Location received: ${location.latitude}, ${location.longitude}")
                    
                    serviceScope.launch {
                        val pingId = saveLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            speed = if (location.hasSpeed()) location.speed else null
                        )
                        
                        // Immediately sync if network available
                        if (syncManager.isNetworkAvailable()) {
                            AppLogger.i(TAG, "Network available, syncing immediately...")
                            syncManager.syncSingleLocation(pingId)
                        } else {
                            AppLogger.d(TAG, "No network, will sync later via WorkManager")
                        }
                    }
                    
                    // Update notification with latest location
                    val notificationText = String.format(
                        "%.4f, %.4f • Battery: %d%%",
                        location.latitude,
                        location.longitude,
                        getBatteryLevel()
                    )
                    updateNotification(notificationText)
                }
            }
        }
    }
    
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.e(TAG, "Location permission not granted")
            stopSelf()
            return
        }
        
        // Get interval from preferences
        val intervalMs = securePrefs.getIntervalMillis()
        val fastestIntervalMs = (intervalMs * 0.8).toLong().coerceAtLeast(1000L)
        
        // Use balanced power for longer intervals (>= 5 min), high accuracy for shorter
        val priority = if (intervalMs >= 5 * 60 * 1000L) {
            AppLogger.i(TAG, "Using BALANCED_POWER_ACCURACY for battery optimization")
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            AppLogger.i(TAG, "Using HIGH_ACCURACY for precise tracking")
            Priority.PRIORITY_HIGH_ACCURACY
        }
        
        val displayInterval = if (securePrefs.isDevMode) {
            "${securePrefs.trackingIntervalSeconds}s (Dev)"
        } else {
            "${securePrefs.trackingIntervalMinutes} min"
        }
        
        AppLogger.i(TAG, "Configuring location updates - interval: $displayInterval ($intervalMs ms)")
        
        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .setWaitForAccurateLocation(false) // Don't wait, sync what we have
            .build()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            AppLogger.i(TAG, "Location updates started - interval: $displayInterval")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Failed to start location updates", e)
            stopSelf()
        }
    }
    
    private suspend fun saveLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        altitude: Double?,
        speed: Float?
    ): Long {
        val ping = LocationPingEntity(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            altitude = altitude,
            speed = speed,
            timestamp = System.currentTimeMillis(),
            batteryLevel = getBatteryLevel()
        )
        
        val id = database.locationPingDao().insert(ping)
        
        // Save last location info
        securePrefs.lastLocationTime = System.currentTimeMillis()
        securePrefs.lastLatitude = latitude
        securePrefs.lastLongitude = longitude
        
        AppLogger.i(TAG, "Location saved to database (id: $id)")
        return id
    }
    
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    
    private fun scheduleSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Fallback sync every 15 min in case instant sync missed some
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        
        AppLogger.i(TAG, "Sync worker scheduled as fallback")
    }
    
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, AntigravityApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(TAG, "Service destroyed")
        
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isRunning = false
        securePrefs.isServiceEnabled = false
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
