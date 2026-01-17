package com.antigravity.locationtracker.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.LightMint
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.WarmGray

/**
 * Setup screen for one-time configuration.
 * Guides user through permissions and battery optimization.
 */
@Composable
fun SetupScreen(
    isCreatingSheet: Boolean,
    sheetCreated: Boolean,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    
    // Track current step
    var currentStep by remember { mutableIntStateOf(1) }
    var locationGranted by remember { mutableStateOf(false) }
    var backgroundLocationGranted by remember { mutableStateOf(false) }
    var batteryOptimized by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    
    // Permission launchers
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            currentStep = 2
        }
    }
    
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        backgroundLocationGranted = granted
        if (granted) {
            currentStep = 3
        }
    }
    
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
        currentStep = 4
    }
    
    // Check battery optimization status
    fun checkBatteryOptimization(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    
    LaunchedEffect(Unit) {
        batteryOptimized = checkBatteryOptimization()
    }
    
    // Auto-complete when sheet is created and all permissions granted
    LaunchedEffect(sheetCreated, locationGranted, backgroundLocationGranted, batteryOptimized, notificationGranted) {
        if (sheetCreated && locationGranted && backgroundLocationGranted && batteryOptimized && notificationGranted) {
            onSetupComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SoftWhite, LightMint)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Quick Setup",
                style = AppTypography.headlineLarge,
                color = DeepCharcoal
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Just a few steps to get started",
                style = AppTypography.bodyLarge,
                color = WarmGray
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Step indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                StepIndicator(number = 1, isActive = currentStep >= 1, isComplete = locationGranted)
                StepLine(isComplete = locationGranted)
                StepIndicator(number = 2, isActive = currentStep >= 2, isComplete = backgroundLocationGranted)
                StepLine(isComplete = backgroundLocationGranted)
                StepIndicator(number = 3, isActive = currentStep >= 3, isComplete = batteryOptimized)
                StepLine(isComplete = batteryOptimized)
                StepIndicator(number = 4, isActive = currentStep >= 4, isComplete = sheetCreated)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Current step content
            when (currentStep) {
                1 -> {
                    SetupStepContent(
                        title = "Location Access",
                        description = "Allow location access so we can track your whereabouts.",
                        buttonText = "Allow Location",
                        isLoading = false,
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
                2 -> {
                    SetupStepContent(
                        title = "Background Location",
                        description = "Allow location access in the background for continuous tracking.",
                        buttonText = "Allow Background",
                        isLoading = false,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            } else {
                                backgroundLocationGranted = true
                                currentStep = 3
                            }
                        }
                    )
                }
                3 -> {
                    SetupStepContent(
                        title = "Battery Settings",
                        description = "Disable battery optimization to ensure reliable tracking.",
                        buttonText = if (batteryOptimized) "✓ Configured" else "Open Settings",
                        isLoading = false,
                        onClick = {
                            if (!batteryOptimized) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                            // Check again after returning
                            batteryOptimized = checkBatteryOptimization()
                            if (batteryOptimized || true) { // Allow proceeding anyway
                                currentStep = 4
                            }
                        }
                    )
                }
                4 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                        SetupStepContent(
                            title = "Notifications",
                            description = "Allow notifications to show tracking status.",
                            buttonText = "Allow Notifications",
                            isLoading = false,
                            onClick = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        )
                    } else {
                        SetupStepContent(
                            title = "Creating Your Sheet",
                            description = "Setting up your personal location history...",
                            buttonText = "",
                            isLoading = isCreatingSheet || !sheetCreated,
                            onClick = {}
                        )
                        
                        if (sheetCreated) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "✓ Sheet created successfully!",
                                style = AppTypography.bodyLarge,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    number: Int,
    isActive: Boolean,
    isComplete: Boolean
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = when {
                    isComplete -> SuccessGreen
                    isActive -> MintGreen
                    else -> LightMint
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isComplete) "✓" else number.toString(),
            style = AppTypography.labelLarge,
            color = if (isComplete || isActive) DeepCharcoal else WarmGray,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StepLine(isComplete: Boolean) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(4.dp)
            .background(
                color = if (isComplete) SuccessGreen else LightMint,
                shape = RoundedCornerShape(2.dp)
            )
    )
}

@Composable
private fun SetupStepContent(
    title: String,
    description: String,
    buttonText: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = AppTypography.headlineMedium,
            color = DeepCharcoal,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = description,
            style = AppTypography.bodyLarge,
            color = WarmGray,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MintGreen,
                strokeWidth = 4.dp
            )
        } else if (buttonText.isNotEmpty()) {
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintGreen,
                    contentColor = DeepCharcoal
                )
            ) {
                Text(
                    text = buttonText,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
