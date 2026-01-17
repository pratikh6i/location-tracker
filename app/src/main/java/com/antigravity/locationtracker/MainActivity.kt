package com.antigravity.locationtracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.antigravity.locationtracker.auth.AuthState
import com.antigravity.locationtracker.auth.GoogleAuthManager
import com.antigravity.locationtracker.data.db.AppDatabase
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.data.sheets.SheetsRepository
import com.antigravity.locationtracker.location.LocationForegroundService
import com.antigravity.locationtracker.ui.screens.ActiveScreen
import com.antigravity.locationtracker.ui.screens.AuthScreen
import com.antigravity.locationtracker.ui.screens.SetupScreen
import com.antigravity.locationtracker.ui.theme.AntigravityTheme
import com.antigravity.locationtracker.ui.theme.SoftWhite
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main activity - single entry point for the app.
 * Manages navigation between Auth, Setup, and Active screens.
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var authManager: GoogleAuthManager
    private lateinit var securePrefs: SecurePreferences
    private lateinit var sheetsRepository: SheetsRepository
    private lateinit var database: AppDatabase
    
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager.handleSignInResult(result.data)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize dependencies
        securePrefs = SecurePreferences(this)
        authManager = GoogleAuthManager(this, securePrefs)
        sheetsRepository = SheetsRepository(this, securePrefs)
        database = AppDatabase.getInstance(this)
        
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
                    
                    // Location data for Active screen
                    val unsyncedCount by database.locationPingDao().getUnsyncedCountFlow().collectAsState(initial = 0)
                    val lastSyncTime by database.locationPingDao().getLastSyncTimeFlow().collectAsState(initial = null)
                    val latestPing by database.locationPingDao().getLatestFlow().collectAsState(initial = null)
                    
                    // Determine which screen to show
                    when (val state = authState) {
                        is AuthState.Loading -> {
                            // Show loading in AuthScreen
                            AuthScreen(
                                isLoading = true,
                                errorMessage = null,
                                onSignInClick = {}
                            )
                        }
                        
                        is AuthState.SignedOut -> {
                            AuthScreen(
                                isLoading = false,
                                errorMessage = errorMessage,
                                onSignInClick = {
                                    errorMessage = null
                                    signInLauncher.launch(authManager.getSignInIntent())
                                }
                            )
                        }
                        
                        is AuthState.Error -> {
                            AuthScreen(
                                isLoading = false,
                                errorMessage = state.message,
                                onSignInClick = {
                                    errorMessage = null
                                    signInLauncher.launch(authManager.getSignInIntent())
                                }
                            )
                        }
                        
                        is AuthState.SignedIn -> {
                            if (!securePrefs.isSetupComplete) {
                                // Need to complete setup
                                SetupScreen(
                                    isCreatingSheet = isCreatingSheet,
                                    sheetCreated = sheetCreated,
                                    onSetupComplete = {
                                        scope.launch {
                                            // Create or find sheet if not done
                                            if (!sheetCreated) {
                                                isCreatingSheet = true
                                                val result = sheetsRepository.findOrCreateSheet()
                                                isCreatingSheet = false
                                                sheetCreated = result.isSuccess
                                            }
                                            
                                            if (sheetCreated) {
                                                // Mark setup complete
                                                securePrefs.isSetupComplete = true
                                                
                                                // Start location service
                                                LocationForegroundService.start(this@MainActivity)
                                            }
                                        }
                                    }
                                )
                                
                                // Trigger sheet creation when entering setup
                                if (!sheetCreated && !isCreatingSheet) {
                                    scope.launch {
                                        isCreatingSheet = true
                                        val result = sheetsRepository.findOrCreateSheet()
                                        isCreatingSheet = false
                                        sheetCreated = result.isSuccess
                                    }
                                }
                            } else {
                                // Setup complete - show active screen
                                ActiveScreen(
                                    lastLocationTime = latestPing?.timestamp,
                                    batteryLevel = latestPing?.batteryLevel ?: getBatteryLevel(),
                                    pendingSyncCount = unsyncedCount,
                                    lastSyncTime = lastSyncTime,
                                    userName = state.displayName
                                )
                                
                                // Ensure service is running
                                if (!LocationForegroundService.isServiceRunning()) {
                                    LocationForegroundService.start(this@MainActivity)
                                }
                            }
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
