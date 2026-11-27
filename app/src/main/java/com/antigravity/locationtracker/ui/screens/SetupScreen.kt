package com.antigravity.locationtracker.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.LightMint
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.WarmGray
import com.antigravity.locationtracker.util.DeviceControlHelper

/**
 * One-time setup wizard verifying permissions, background location, battery exemption,
 * notifications, and Google Sheet provisioning.
 */
@Composable
fun SetupScreen(
    isCreatingSheet: Boolean,
    sheetCreated: Boolean,
    sheetError: String?,
    onRetrySheet: () -> Unit,
    onRequestEnableLocation: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var locationGranted by remember { mutableStateOf(DeviceControlHelper.hasFineLocationPermission(context)) }
    var backgroundLocationGranted by remember { mutableStateOf(DeviceControlHelper.hasBackgroundLocationPermission(context)) }
    var batteryOptimized by remember { mutableStateOf(DeviceControlHelper.isIgnoringBatteryOptimizations(context)) }
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    var locationServiceOn by remember { mutableStateOf(DeviceControlHelper.isLocationEnabled(context)) }

    var currentStep by remember {
        mutableIntStateOf(
            when {
                !locationGranted -> 1
                !backgroundLocationGranted -> 2
                !batteryOptimized || !locationServiceOn -> 3
                else -> 4
            }
        )
    }

    fun refreshState() {
        locationGranted = DeviceControlHelper.hasFineLocationPermission(context)
        backgroundLocationGranted = DeviceControlHelper.hasBackgroundLocationPermission(context)
        batteryOptimized = DeviceControlHelper.isIgnoringBatteryOptimizations(context)
        locationServiceOn = DeviceControlHelper.isLocationEnabled(context)

        if (currentStep == 3 && batteryOptimized && locationServiceOn) {
            currentStep = 4
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            currentStep = if (backgroundLocationGranted) 3 else 2
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
            Text(
                text = "Quick Setup",
                style = AppTypography.headlineLarge,
                color = DeepCharcoal
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Configure reliable 4-hour anti-theft tracking",
                style = AppTypography.bodyLarge,
                color = WarmGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepIndicator(number = 1, isActive = currentStep >= 1, isComplete = locationGranted)
                StepLine(isComplete = locationGranted)
                StepIndicator(number = 2, isActive = currentStep >= 2, isComplete = backgroundLocationGranted)
                StepLine(isComplete = backgroundLocationGranted)
                StepIndicator(number = 3, isActive = currentStep >= 3, isComplete = batteryOptimized && locationServiceOn)
                StepLine(isComplete = batteryOptimized && locationServiceOn)
                StepIndicator(number = 4, isActive = currentStep >= 4, isComplete = sheetCreated)
            }

            Spacer(modifier = Modifier.height(40.dp))

            when (currentStep) {
                1 -> {
                    SetupStepContent(
                        title = "Location Access",
                        description = "Allow precise GPS access so the tracker can locate your device.",
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
                        description = "Select \"Allow all the time\" so 4-hour tracking keeps working even when the screen is off.",
                        buttonText = "Allow All The Time",
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Reliability & GPS",
                            style = AppTypography.headlineMedium,
                            color = DeepCharcoal,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Disable battery optimization and turn on GPS so Android doesn't pause 4-hour wakeups.",
                            style = AppTypography.bodyLarge,
                            color = WarmGray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (!batteryOptimized) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                        }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintGreen,
                                    contentColor = DeepCharcoal
                                )
                            ) {
                                Text("Allow Unrestricted Battery", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (!locationServiceOn) {
                            Button(
                                onClick = {
                                    onRequestEnableLocation()
                                    refreshState()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintGreen,
                                    contentColor = DeepCharcoal
                                )
                            ) {
                                Text("Turn On Location Service", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedButton(
                            onClick = { currentStep = 4 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (batteryOptimized && locationServiceOn) "Continue →" else "Skip for now →",
                                color = DeepCharcoal
                            )
                        }
                    }
                }

                4 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                        SetupStepContent(
                            title = "Notifications",
                            description = "Allow notifications so the foreground tracking service stays alive reliably.",
                            buttonText = "Allow Notifications",
                            isLoading = false,
                            onClick = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (sheetCreated) "Ready to Track" else "Creating Your Sheet",
                                style = AppTypography.headlineMedium,
                                color = DeepCharcoal,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = when {
                                    sheetCreated -> "Your Google Sheet is connected and 4-hour tracking is ready."
                                    sheetError != null -> sheetError
                                    else -> "Setting up your personal location history in Google Drive..."
                                },
                                style = AppTypography.bodyLarge,
                                color = if (sheetError != null && !sheetCreated) MaterialTheme.colorScheme.error else WarmGray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            if (isCreatingSheet && !sheetCreated) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MintGreen,
                                    strokeWidth = 4.dp
                                )
                            } else if (sheetError != null && !sheetCreated) {
                                Button(
                                    onClick = onRetrySheet,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MintGreen,
                                        contentColor = DeepCharcoal
                                    )
                                ) {
                                    Text("Retry Sheet Setup", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            if (sheetCreated) {
                                Text(
                                    text = "✓ Sheet ready!",
                                    style = AppTypography.bodyLarge,
                                    color = SuccessGreen
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = onSetupComplete,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SuccessGreen,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "Start Tracking →",
                                        style = AppTypography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
