package com.antigravity.locationtracker

import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.antigravity.locationtracker.auth.AuthState
import com.antigravity.locationtracker.auth.GoogleAuthManager
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.location.LocationForegroundService
import com.antigravity.locationtracker.location.LocationHelper
import com.antigravity.locationtracker.sync.SyncManager
import com.antigravity.locationtracker.ui.components.DebugLogsFab
import com.antigravity.locationtracker.ui.screens.AuthScreen
import com.antigravity.locationtracker.ui.screens.MapDashboardScreen
import com.antigravity.locationtracker.ui.screens.SettingsScreen
import com.antigravity.locationtracker.ui.screens.SetupScreen
import com.antigravity.locationtracker.ui.screens.SplashScreen
import com.antigravity.locationtracker.ui.theme.AntigravityTheme
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.util.AppLogger
import com.antigravity.locationtracker.util.DeviceControlHelper
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

/**
 * Main activity - single entry point for the app.
 * Navigation flow: Splash -> Auth -> Setup -> MapDashboard (with Settings)
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var authManager: GoogleAuthManager
    private lateinit var securePrefs: SecurePreferences
    private lateinit var sheetsRepository: SheetsRepository
    private lateinit var database: AppDatabase
    private lateinit var syncManager: SyncManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLogger.i(TAG, "Sign-in result received: resultCode=${result.resultCode}")
        authManager.handleSignInResult(result.data)
    }

    private val locationResolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        AppLogger.i(TAG, "Location settings resolution result: ${result.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppLogger.i(TAG, "=== wya App Started ===")

        securePrefs = SecurePreferences.getInstance(this)
        authManager = GoogleAuthManager(this, securePrefs)
        sheetsRepository = SheetsRepository(this, securePrefs)
        database = AppDatabase.getInstance(this)
        syncManager = SyncManager(this, securePrefs)

        // Apply Device Owner policies automatically if provisioned
        DeviceControlHelper.applyAntiTheftPolicies(this)

        lifecycleScope.launch {
            authManager.checkExistingSignIn()
        }

        setContent {
            AntigravityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SoftWhite
                ) {
                    val authState by authManager.authState.collectAsState()
                    val scope = rememberCoroutineScope()
                    val lifecycleOwner = LocalLifecycleOwner.current

                    var showSplash by remember { mutableStateOf(true) }
                    var showSettings by remember { mutableStateOf(false) }

                    var isCreatingSheet by remember { mutableStateOf(false) }
                    var sheetCreated by remember { mutableStateOf(securePrefs.hasSpreadsheet()) }
                    var sheetError by remember { mutableStateOf<String?>(null) }
                    var sheetRetryTick by remember { mutableIntStateOf(0) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var isSyncing by remember { mutableStateOf(false) }
                    var setupCompleted by remember { mutableStateOf(securePrefs.isSetupComplete) }
                    var protectionStatus by remember {
                        mutableStateOf(DeviceControlHelper.getProtectionStatus(this@MainActivity))
                    }

                    // Refresh protection status when returning to foreground
                    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                protectionStatus = DeviceControlHelper.getProtectionStatus(this@MainActivity)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    val unsyncedCount by database.locationPingDao().getUnsyncedCountFlow().collectAsState(initial = 0)
                    val latestPing by database.locationPingDao().getLatestFlow().collectAsState(initial = null)

                    val downloadLogs = {
                        AppLogger.saveAndNotify(this@MainActivity)
                    }

                    val requestEnableLocation: () -> Unit = {
                        if (!DeviceControlHelper.ensureLocationEnabled(this@MainActivity)) {
                            promptLocationSettingsResolution()
                        }
                        protectionStatus = DeviceControlHelper.getProtectionStatus(this@MainActivity)
                    }

                    val openNetworkSettings: () -> Unit = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                        }.onFailure {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }

                    val openBatterySettings: () -> Unit = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        runCatching { startActivity(intent) }
                            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }

                    val onPingAndSyncNow: () -> Unit = {
                        if (!isSyncing) {
                            scope.launch {
                                isSyncing = true
                                val ping = LocationHelper.captureAndSaveLocation(this@MainActivity, "MANUAL")
                                val result = syncManager.syncNow()
                                isSyncing = false
                                protectionStatus = DeviceControlHelper.getProtectionStatus(this@MainActivity)
                                val message = when {
                                    ping == null && !protectionStatus.locationServiceEnabled ->
                                        "Turn ON Location to capture GPS"
                                    result > 0 -> "Captured & synced $result ping(s) ✓"
                                    result == 0 -> "Location captured & up to date ✓"
                                    else -> "Saved offline — will sync when online"
                                }
                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    val onDevModeChanged: (Boolean) -> Unit = { enabled ->
                        securePrefs.isDevMode = enabled
                        if (securePrefs.isSetupComplete) {
                            LocationForegroundService.updateInterval(this@MainActivity)
                        }
                    }

                    val onIntervalChanged: (Int, Boolean) -> Unit = { value, devMode ->
                        if (devMode) {
                            securePrefs.isDevMode = true
                            securePrefs.trackingIntervalSeconds = value
                        } else {
                            securePrefs.isDevMode = false
                            securePrefs.trackingIntervalMinutes = value
                        }
                        if (securePrefs.isSetupComplete) {
                            LocationForegroundService.updateInterval(this@MainActivity)
                        }
                    }

                    val onSosClick: () -> Unit = {
                        scope.launch {
                            AppLogger.i(TAG, "SOS TRIGGERED!")
                            val ping = LocationHelper.captureAndSaveLocation(this@MainActivity, "SOS")
                            val synced = syncManager.syncNow()
                            val msg = when {
                                ping != null && synced > 0 -> "SOS location captured & synced!"
                                ping != null -> "SOS saved offline — syncing when online"
                                else -> "Enable Location to send SOS coordinates"
                            }
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }

                    // Side-effect safe Google Sheet discovery/creation
                    LaunchedEffect(authState, sheetCreated, sheetRetryTick) {
                        if (authState is AuthState.SignedIn && !sheetCreated && !isCreatingSheet) {
                            isCreatingSheet = true
                            sheetError = null
                            val result = sheetsRepository.findOrCreateSheet()
                            isCreatingSheet = false
                            if (result.isSuccess) {
                                sheetCreated = true
                            } else {
                                sheetError = result.exceptionOrNull()?.message ?: "Failed to create Google Sheet"
                            }
                        }
                    }

                    // Side-effect safe ForegroundService startup once setup completes
                    LaunchedEffect(authState, setupCompleted) {
                        if (authState is AuthState.SignedIn && setupCompleted) {
                            if (DeviceControlHelper.hasFineLocationPermission(this@MainActivity)) {
                                if (!LocationForegroundService.isServiceRunning()) {
                                    LocationForegroundService.start(this@MainActivity)
                                }
                                if (!DeviceControlHelper.isLocationEnabled(this@MainActivity)) {
                                    requestEnableLocation()
                                }
                            } else {
                                AppLogger.w(TAG, "Location permission revoked; returning to setup")
                                securePrefs.isSetupComplete = false
                                setupCompleted = false
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            showSplash -> {
                                SplashScreen(
                                    onSplashComplete = { showSplash = false }
                                )
                            }

                            showSettings -> {
                                val state = authState
                                SettingsScreen(
                                    userName = if (state is AuthState.SignedIn) state.displayName else null,
                                    userEmail = if (state is AuthState.SignedIn) state.email else null,
                                    spreadsheetUrl = securePrefs.getSpreadsheetUrl(),
                                    currentIntervalDisplay = securePrefs.formatIntervalDisplay(),
                                    currentIntervalMinutes = securePrefs.trackingIntervalMinutes,
                                    currentIntervalSeconds = securePrefs.trackingIntervalSeconds,
                                    isDevMode = securePrefs.isDevMode,
                                    protectionStatus = protectionStatus,
                                    isSyncing = isSyncing,
                                    onBackClick = { showSettings = false },
                                    onIntervalChanged = onIntervalChanged,
                                    onDevModeChanged = onDevModeChanged,
                                    onTestPingSyncClick = onPingAndSyncNow,
                                    onEnableLocationClick = requestEnableLocation,
                                    onOpenNetworkSettingsClick = openNetworkSettings,
                                    onOpenBatterySettingsClick = openBatterySettings,
                                    onSignOutClick = {
                                        scope.launch {
                                            LocationForegroundService.stop(this@MainActivity)
                                            authManager.signOut()
                                            setupCompleted = false
                                            sheetCreated = false
                                            showSettings = false
                                        }
                                    }
                                )
                            }

                            authState is AuthState.Loading -> {
                                AuthScreen(
                                    isLoading = true,
                                    errorMessage = null,
                                    onSignInClick = {},
                                    onShareLogsClick = downloadLogs
                                )
                            }

                            authState is AuthState.SignedOut -> {
                                AuthScreen(
                                    isLoading = false,
                                    errorMessage = errorMessage,
                                    onSignInClick = {
                                        errorMessage = null
                                        signInLauncher.launch(authManager.getSignInIntent())
                                    },
                                    onShareLogsClick = downloadLogs
                                )
                            }

                            authState is AuthState.Error -> {
                                val state = authState as AuthState.Error
                                AuthScreen(
                                    isLoading = false,
                                    errorMessage = state.message,
                                    onSignInClick = {
                                        errorMessage = null
                                        signInLauncher.launch(authManager.getSignInIntent())
                                    },
                                    onShareLogsClick = downloadLogs
                                )
                            }

                            authState is AuthState.SignedIn -> {
                                val state = authState as AuthState.SignedIn

                                if (!setupCompleted) {
                                    SetupScreen(
                                        isCreatingSheet = isCreatingSheet,
                                        sheetCreated = sheetCreated,
                                        sheetError = sheetError,
                                        onRetrySheet = { sheetRetryTick++ },
                                        onRequestEnableLocation = requestEnableLocation,
                                        onSetupComplete = {
                                            if (!setupCompleted) {
                                                securePrefs.isSetupComplete = true
                                                setupCompleted = true
                                            }
                                        }
                                    )
                                } else {
                                    MapDashboardScreen(
                                        currentLatitude = latestPing?.latitude ?: securePrefs.lastLatitude,
                                        currentLongitude = latestPing?.longitude ?: securePrefs.lastLongitude,
                                        lastLocationTime = latestPing?.timestamp ?: securePrefs.lastLocationTime,
                                        lastAccuracy = latestPing?.accuracy,
                                        batteryLevel = latestPing?.batteryLevel ?: getBatteryLevel(),
                                        pendingSyncCount = unsyncedCount,
                                        userName = state.displayName,
                                        spreadsheetUrl = securePrefs.getSpreadsheetUrl(),
                                        intervalDisplay = securePrefs.formatIntervalDisplay(),
                                        protectionStatus = protectionStatus,
                                        isSyncing = isSyncing,
                                        onSettingsClick = { showSettings = true },
                                        onProfileClick = { showSettings = true },
                                        onSosClick = onSosClick,
                                        onRefreshClick = onPingAndSyncNow,
                                        onFixLocationClick = requestEnableLocation,
                                        onFixNetworkClick = openNetworkSettings,
                                        onFixBatteryClick = openBatterySettings
                                    )
                                }
                            }
                        }

                        if (!showSplash) {
                            DebugLogsFab(
                                onClick = downloadLogs,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun promptLocationSettingsResolution() {
        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(DeviceControlHelper.buildLocationSettingsRequest())
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    runCatching {
                        val req = IntentSenderRequest.Builder(exception.resolution).build()
                        locationResolutionLauncher.launch(req)
                    }.onFailure {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                } else {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
    }

    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
