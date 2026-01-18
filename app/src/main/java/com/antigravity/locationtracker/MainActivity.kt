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
import com.antigravity.locationtracker.ui.screens.ActiveScreen
import com.antigravity.locationtracker.ui.screens.AuthScreen
import com.antigravity.locationtracker.ui.screens.SetupScreen
import com.antigravity.locationtracker.ui.theme.AntigravityTheme
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.util.AppLogger
import kotlinx.coroutines.launch

/**
 * Main activity - single entry point for the app.
 * Manages navigation between Auth, Setup, and Active screens.
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
        
        AppLogger.i(TAG, "=== App Started ===")
        AppLogger.i(TAG, "onCreate called")
        
        // Initialize dependencies
        securePrefs = SecurePreferences(this)
        authManager = GoogleAuthManager(this, securePrefs)
        sheetsRepository = SheetsRepository(this, securePrefs)
        database = AppDatabase.getInstance(this)
        syncManager = SyncManager(this, securePrefs)
        
        AppLogger.d(TAG, "Dependencies initialized")
        
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
                    
                    // UI state
                    var isCreatingSheet by remember { mutableStateOf(false) }
                    var sheetCreated by remember { mutableStateOf(securePrefs.hasSpreadsheet()) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var isSyncing by remember { mutableStateOf(false) }
                    var setupCompleted by remember { mutableStateOf(false) }
                    
                    // Location data for Active screen
                    val unsyncedCount by database.locationPingDao().getUnsyncedCountFlow().collectAsState(initial = 0)
                    val lastSyncTime by database.locationPingDao().getLastSyncTimeFlow().collectAsState(initial = null)
                    val latestPing by database.locationPingDao().getLatestFlow().collectAsState(initial = null)
                    
                    // Download logs function
                    val downloadLogs = {
                        AppLogger.i(TAG, "User requested to download logs")
                        AppLogger.saveAndNotify(this@MainActivity)
                    }
                    
                    // Sync now handler
                    val onSyncNow: () -> Unit = {
                        scope.launch {
                            AppLogger.i(TAG, "User triggered manual sync")
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
                    
                    // Refresh handler (for pull-to-refresh)
                    val onRefresh: () -> Unit = {
                        scope.launch {
                            isSyncing = true
                            syncManager.syncNow()
                            isSyncing = false
                        }
                    }
                    
                    // Interval change handler
                    val onIntervalChanged: (Int, Boolean) -> Unit = { value, isDevMode ->
                        AppLogger.i(TAG, "Interval changed to: $value ${if (isDevMode) "seconds (dev)" else "minutes"}")
                        
                        if (isDevMode) {
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
                    
                    // Dev mode change handler
                    val onDevModeChanged: (Boolean) -> Unit = { enabled ->
                        AppLogger.i(TAG, "Dev mode ${if (enabled) "enabled" else "disabled"}")
                        securePrefs.isDevMode = enabled
                    }
                    
                    // Main content with FAB overlay
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Determine which screen to show
                        when (val state = authState) {
                            is AuthState.Loading -> {
                                AppLogger.d(TAG, "Showing Loading state")
                                AuthScreen(
                                    isLoading = true,
                                    errorMessage = null,
                                    onSignInClick = {},
                                    onShareLogsClick = downloadLogs
                                )
                            }
                            
                            is AuthState.SignedOut -> {
                                AppLogger.d(TAG, "Showing SignedOut state")
                                AuthScreen(
                                    isLoading = false,
                                    errorMessage = errorMessage,
                                    onSignInClick = {
                                        AppLogger.i(TAG, "User clicked Sign In")
                                        errorMessage = null
                                        signInLauncher.launch(authManager.getSignInIntent())
                                    },
                                    onShareLogsClick = downloadLogs
                                )
                            }
                            
                            is AuthState.Error -> {
                                AppLogger.w(TAG, "Showing Error state: ${state.message}")
                                AuthScreen(
                                    isLoading = false,
                                    errorMessage = state.message,
                                    onSignInClick = {
                                        AppLogger.i(TAG, "User clicked Sign In (after error)")
                                        errorMessage = null
                                        signInLauncher.launch(authManager.getSignInIntent())
                                    },
                                    onShareLogsClick = downloadLogs
                                )
                            }
                            
                            is AuthState.SignedIn -> {
                                AppLogger.d(TAG, "Showing SignedIn state for: ${state.email}")
                                if (!securePrefs.isSetupComplete) {
                                    // Need to complete setup
                                    SetupScreen(
                                        isCreatingSheet = isCreatingSheet,
                                        sheetCreated = sheetCreated,
                                        onSetupComplete = {
                                            if (!setupCompleted) {
                                                setupCompleted = true
                                                scope.launch {
                                                    AppLogger.i(TAG, "Setup completing...")
                                                    // Mark setup complete
                                                    securePrefs.isSetupComplete = true
                                                    
                                                    // Start location service
                                                    AppLogger.i(TAG, "Starting location service...")
                                                    LocationForegroundService.start(this@MainActivity)
                                                }
                                            }
                                        }
                                    )
                                    
                                    // Trigger sheet creation when entering setup
                                    if (!sheetCreated && !isCreatingSheet) {
                                        scope.launch {
                                            AppLogger.i(TAG, "Creating sheet on setup enter...")
                                            isCreatingSheet = true
                                            val result = sheetsRepository.findOrCreateSheet()
                                            isCreatingSheet = false
                                            sheetCreated = result.isSuccess
                                            if (result.isFailure) {
                                                AppLogger.e(TAG, "Sheet creation failed", result.exceptionOrNull())
                                            }
                                        }
                                    }
                                } else {
                                    // Setup complete - show active screen
                                    ActiveScreen(
                                        lastLocationTime = latestPing?.timestamp ?: securePrefs.lastLocationTime,
                                        lastLatitude = latestPing?.latitude ?: securePrefs.lastLatitude,
                                        lastLongitude = latestPing?.longitude ?: securePrefs.lastLongitude,
                                        batteryLevel = latestPing?.batteryLevel ?: getBatteryLevel(),
                                        pendingSyncCount = unsyncedCount,
                                        lastSyncTime = lastSyncTime,
                                        userName = state.displayName,
                                        spreadsheetUrl = securePrefs.getSpreadsheetUrl(),
                                        currentIntervalDisplay = securePrefs.formatIntervalDisplay(),
                                        currentIntervalMinutes = securePrefs.trackingIntervalMinutes,
                                        currentIntervalSeconds = securePrefs.trackingIntervalSeconds,
                                        isDevMode = securePrefs.isDevMode,
                                        isSyncing = isSyncing,
                                        onRefresh = onRefresh,
                                        onSyncNowClick = onSyncNow,
                                        onIntervalChanged = onIntervalChanged,
                                        onDevModeChanged = onDevModeChanged
                                    )
                                    
                                    // Ensure service is running
                                    if (!LocationForegroundService.isServiceRunning()) {
                                        AppLogger.i(TAG, "Service not running, starting...")
                                        LocationForegroundService.start(this@MainActivity)
                                    }
                                }
                            }
                        }
                        
                        // Floating Action Button for logs - ALWAYS VISIBLE
                        DebugLogsFab(
                            onClick = downloadLogs,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
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
