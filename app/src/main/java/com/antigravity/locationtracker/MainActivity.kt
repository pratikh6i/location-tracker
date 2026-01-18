package com.antigravity.locationtracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.antigravity.locationtracker.auth.AuthState
import com.antigravity.locationtracker.auth.GoogleAuthManager
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.location.LocationForegroundService
import com.antigravity.locationtracker.sync.SyncManager
import com.antigravity.locationtracker.ui.components.DebugLogsFab
import com.antigravity.locationtracker.ui.screens.AuthScreen
import com.antigravity.locationtracker.ui.screens.MapDashboardScreen
import com.antigravity.locationtracker.ui.screens.SetupScreen
import com.antigravity.locationtracker.ui.screens.SettingsScreen
import com.antigravity.locationtracker.ui.screens.SplashScreen
import com.antigravity.locationtracker.ui.theme.AntigravityTheme
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.util.AppLogger
import kotlinx.coroutines.launch

/**
 * Main activity - single entry point for the app.
 * Navigation flow: Splash → Auth → Setup → MapDashboard (with Settings)
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        AppLogger.i(TAG, "=== wya App Started ===")
        
        // Initialize dependencies
        securePrefs = SecurePreferences(this)
        authManager = GoogleAuthManager(this, securePrefs)
        sheetsRepository = SheetsRepository(this, securePrefs)
        database = AppDatabase.getInstance(this)
        syncManager = SyncManager(this, securePrefs)
        
        // Check existing sign-in
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
                    
                    // Navigation state
                    var showSplash by remember { mutableStateOf(true) }
                    var showSettings by remember { mutableStateOf(false) }
                    
                    // UI state
                    var isCreatingSheet by remember { mutableStateOf(false) }
                    var sheetCreated by remember { mutableStateOf(securePrefs.hasSpreadsheet()) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var isSyncing by remember { mutableStateOf(false) }
                    var setupCompleted by remember { mutableStateOf(false) }
                    
                    // Location data
                    val unsyncedCount by database.locationPingDao().getUnsyncedCountFlow().collectAsState(initial = 0)
                    val lastSyncTime by database.locationPingDao().getLastSyncTimeFlow().collectAsState(initial = null)
                    val latestPing by database.locationPingDao().getLatestFlow().collectAsState(initial = null)
                    
                    // Handlers
                    val downloadLogs = {
                        AppLogger.saveAndNotify(this@MainActivity)
                    }
                    
                    val onSyncNow: () -> Unit = {
                        scope.launch {
                            isSyncing = true
                            val result = syncManager.syncNow()
                            isSyncing = false
                            val message = when {
                                result > 0 -> "Synced $result locations ✓"
                                result == 0 -> "Already up to date ✓"
                                else -> "Sync failed - check connection"
                            }
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    val onDevModeChanged: (Boolean) -> Unit = { enabled ->
                        securePrefs.isDevMode = enabled
                        if (LocationForegroundService.isServiceRunning()) {
                            LocationForegroundService.stop(this@MainActivity)
                            LocationForegroundService.start(this@MainActivity)
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
                        // Restart service with new interval
                        if (LocationForegroundService.isServiceRunning()) {
                            LocationForegroundService.stop(this@MainActivity)
                            LocationForegroundService.start(this@MainActivity)
                        }
                    }
                    
                    val onSosClick: () -> Unit = {
                        scope.launch {
                            AppLogger.i(TAG, "🆘 SOS TRIGGERED!")
                            // Post SOS to sheet
                            syncManager.syncNow()
                            Toast.makeText(this@MainActivity, "🆘 SOS location sent!", Toast.LENGTH_LONG).show()
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            // Splash screen
                            showSplash -> {
                                SplashScreen(
                                    onSplashComplete = { showSplash = false }
                                )
                            }
                            
                            // Settings screen
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
                                    onBackClick = { showSettings = false },
                                    onIntervalChanged = onIntervalChanged,
                                    onDevModeChanged = onDevModeChanged,
                                    onSignOutClick = {
                                        scope.launch {
                                            authManager.signOut()
                                            securePrefs.isSetupComplete = false
                                            showSettings = false
                                        }
                                    }
                                )
                            }
                            
                            // Auth flow
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
                                
                                if (!securePrefs.isSetupComplete) {
                                    // Setup screen
                                    SetupScreen(
                                        isCreatingSheet = isCreatingSheet,
                                        sheetCreated = sheetCreated,
                                        onSetupComplete = {
                                            if (!setupCompleted) {
                                                setupCompleted = true
                                                scope.launch {
                                                    securePrefs.isSetupComplete = true
                                                    LocationForegroundService.start(this@MainActivity)
                                                }
                                            }
                                        }
                                    )
                                    
                                    // Create sheet on enter
                                    if (!sheetCreated && !isCreatingSheet) {
                                        scope.launch {
                                            isCreatingSheet = true
                                            val result = sheetsRepository.findOrCreateSheet()
                                            isCreatingSheet = false
                                            sheetCreated = result.isSuccess
                                        }
                                    }
                                } else {
                                    // Main Map Dashboard
                                    MapDashboardScreen(
                                        currentLatitude = latestPing?.latitude ?: securePrefs.lastLatitude,
                                        currentLongitude = latestPing?.longitude ?: securePrefs.lastLongitude,
                                        lastLocationTime = latestPing?.timestamp ?: securePrefs.lastLocationTime,
                                        batteryLevel = latestPing?.batteryLevel ?: getBatteryLevel(),
                                        pendingSyncCount = unsyncedCount,
                                        userName = state.displayName,
                                        spreadsheetUrl = securePrefs.getSpreadsheetUrl(),
                                        onSettingsClick = { showSettings = true },
                                        onProfileClick = { showSettings = true }, // Opens settings for profile
                                        onSosClick = onSosClick,
                                        onRefreshClick = onSyncNow
                                    )
                                    
                                    // Ensure service is running
                                    if (!LocationForegroundService.isServiceRunning()) {
                                        LocationForegroundService.start(this@MainActivity)
                                    }
                                }
                            }
                        }
                        
                        // Debug FAB (only when not on splash)
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
    
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
        return batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
