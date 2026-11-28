package com.antigravity.locationtracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.PaleBlue
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.WarmGray
import com.antigravity.locationtracker.util.DeviceControlHelper

/**
 * Settings screen with profile, 4-hour frequency selector, anti-theft diagnostics,
 * and Device Owner / ADB auto-enable setup instructions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String?,
    userEmail: String?,
    spreadsheetUrl: String?,
    currentIntervalDisplay: String,
    currentIntervalMinutes: Int,
    currentIntervalSeconds: Int,
    isDevMode: Boolean,
    protectionStatus: DeviceControlHelper.ProtectionStatus,
    isSyncing: Boolean,
    onBackClick: () -> Unit,
    onIntervalChanged: (Int, Boolean) -> Unit,
    onDevModeChanged: (Boolean) -> Unit,
    onTestPingSyncClick: () -> Unit,
    onEnableLocationClick: () -> Unit,
    onOpenNetworkSettingsClick: () -> Unit,
    onOpenBatterySettingsClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showAdbLockDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaleBlue.copy(alpha = 0.3f))
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Text("←", fontSize = 24.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftWhite)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MintGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName?.firstOrNull()?.uppercase() ?: "?",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepCharcoal
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName ?: "User",
                            style = AppTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepCharcoal
                        )
                        userEmail?.let {
                            Text(
                                text = it,
                                style = AppTypography.labelMedium,
                                color = WarmGray
                            )
                        }
                    }
                }
            }

            // Instant Test Ping & Sync
            SettingsItem(
                icon = "📍",
                title = if (isSyncing) "Capturing & Syncing..." else "Test Ping & Sync Now",
                subtitle = "Capture live GPS fix and upload to Google Sheet immediately",
                onClick = { if (!isSyncing) onTestPingSyncClick() }
            )

            // Update Frequency
            SettingsItem(
                icon = "⏱️",
                title = "Update Frequency",
                subtitle = currentIntervalDisplay,
                onClick = { showFrequencyDialog = true }
            )

            // Google Sheet
            SettingsItem(
                icon = "📊",
                title = "Google Sheet",
                subtitle = "Open your location history",
                onClick = {
                    if (spreadsheetUrl != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spreadsheetUrl)))
                    } else {
                        Toast.makeText(context, "Sheet not created yet", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            SettingsItem(
                icon = "🔗",
                title = "Copy Sheet Link",
                subtitle = "Share live tracking sheet",
                onClick = {
                    if (spreadsheetUrl != null) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Location Sheet Link", spreadsheetUrl))
                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Anti-Theft Protection Diagnostics
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftWhite)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🛡️ Anti-Theft Protection Status",
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepCharcoal
                    )

                    StatusRow(
                        label = "GPS Location Service",
                        ok = protectionStatus.locationServiceEnabled,
                        actionText = if (protectionStatus.locationServiceEnabled) "ON" else "Turn ON",
                        onClick = onEnableLocationClick
                    )
                    StatusRow(
                        label = "Mobile Data / Internet",
                        ok = protectionStatus.internetAvailable,
                        actionText = if (protectionStatus.internetAvailable) "Online" else "Settings",
                        onClick = onOpenNetworkSettingsClick
                    )
                    StatusRow(
                        label = "Unrestricted Battery",
                        ok = protectionStatus.batteryOptimizationIgnored,
                        actionText = if (protectionStatus.batteryOptimizationIgnored) "OK" else "Fix",
                        onClick = onOpenBatterySettingsClick
                    )
                    StatusRow(
                        label = "Auto-Enable & Lock (Device Owner)",
                        ok = protectionStatus.isDeviceOwner || protectionStatus.hasWriteSecureSettings,
                        actionText = if (protectionStatus.isDeviceOwner) "Locked" else "Setup Guide",
                        onClick = { showAdbLockDialog = true }
                    )
                }
            }

            // Dev Mode toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDevMode) Color(0xFFFFF3E0) else SoftWhite
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 24.sp)

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Mode",
                            style = AppTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepCharcoal
                        )
                        Text(
                            text = "Fast second-level tracking for testing",
                            style = AppTypography.labelMedium,
                            color = WarmGray
                        )
                    }

                    Switch(
                        checked = isDevMode,
                        onCheckedChange = onDevModeChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF9800)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── SMS Fallback & State Restore ─────────────────────────────────────────
            SmsAndRestoreSection(context = context)

            Spacer(modifier = Modifier.height(16.dp))

            // Sign Out
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOutClick() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🚪 Sign Out",
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE53935)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "wya v1.1.0",
                style = AppTypography.labelSmall,
                color = WarmGray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showFrequencyDialog) {
        FrequencySelectionDialog(
            currentMinutes = currentIntervalMinutes,
            currentSeconds = currentIntervalSeconds,
            isDevMode = isDevMode,
            onDismiss = { showFrequencyDialog = false },
            onIntervalSelected = { value, devMode ->
                onIntervalChanged(value, devMode)
                showFrequencyDialog = false
                val msg = if (devMode) "Interval: ${value}s" else "Interval: ${SecurePreferences.formatInterval(value)}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAdbLockDialog) {
        val dpmCommand = "adb shell dpm set-device-owner com.antigravity.locationtracker/.admin.TrackerDeviceAdminReceiver"
        val secureSettingsCmd = "adb shell pm grant com.antigravity.locationtracker android.permission.WRITE_SECURE_SETTINGS"
        val mobileDataCmd = "adb shell pm grant com.antigravity.locationtracker android.permission.MODIFY_PHONE_STATE"

        AlertDialog(
            onDismissRequest = { showAdbLockDialog = false },
            title = { Text("Anti-Theft Auto-Enable Setup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Android blocks normal apps from silently toggling Mobile Data or Location from phone settings. To allow auto-enabling Location & locking Mobile Data/Location against thieves:",
                        style = AppTypography.bodyMedium,
                        color = DeepCharcoal
                    )
                    Text(
                        text = "1. Full Anti-Theft Lock (Device Owner on clean/un-signed-in phone):\n$dpmCommand",
                        style = AppTypography.labelSmall,
                        color = WarmGray
                    )
                    Text(
                        text = "2. Auto-Enable Location only (works on existing phone):\n$secureSettingsCmd",
                        style = AppTypography.labelSmall,
                        color = WarmGray
                    )
                    Text(
                        text = "3. Auto-Enable Mobile Data (run once with USB debugging):\n$mobileDataCmd",
                        style = AppTypography.labelSmall,
                        color = WarmGray
                    )
                    Text(
                        text = "After step 3 app will silently turn mobile data ON before each sync and OFF after — restoring your original state.",
                        style = AppTypography.labelSmall,
                        color = SuccessGreen
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB Setup", "$dpmCommand\n$secureSettingsCmd\n$mobileDataCmd"))
                        Toast.makeText(context, "ADB commands copied!", Toast.LENGTH_SHORT).show()
                        showAdbLockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = DeepCharcoal)
                ) {
                    Text("Copy Commands")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdbLockDialog = false }) {
                    Text("Close")
                }
            },
            containerColor = SoftWhite
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${if (ok) "✅" else "⚠️"} $label",
            style = AppTypography.labelMedium,
            color = DeepCharcoal
        )
        Text(
            text = actionText,
            style = AppTypography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) SuccessGreen else Color(0xFFE53935)
        )
    }
}

@Composable
private fun SettingsItem(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 24.sp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepCharcoal
                )
                Text(
                    text = subtitle,
                    style = AppTypography.labelMedium,
                    color = WarmGray
                )
            }

            Text("→", fontSize = 20.sp, color = WarmGray)
        }
    }
}

// ── SMS Fallback + State Restore card (self-contained, reads/writes prefs directly) ──

@Composable
private fun SmsAndRestoreSection(context: Context) {
    val prefs = remember { SecurePreferences.getInstance(context) }

    var phone1          by remember { mutableStateOf(prefs.smsPhone1 ?: "") }
    var phone2          by remember { mutableStateOf(prefs.smsPhone2 ?: "") }
    var stateRestore    by remember { mutableStateOf(prefs.stateRestoreEnabled) }
    var lockAfterSync   by remember { mutableStateOf(prefs.lockAfterSync) }
    var smsGranted      by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> smsGranted = granted }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SoftWhite)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📱 SMS Fallback",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = DeepCharcoal
            )
            Text(
                "If location isn't uploaded for 24 h, sends last known location via SMS.",
                style = AppTypography.labelSmall,
                color = WarmGray,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            if (!smsGranted) {
                Button(
                    onClick = { smsPermLauncher.launch(Manifest.permission.SEND_SMS) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Grant SMS Permission", color = Color.White) }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = phone1,
                onValueChange = { phone1 = it },
                label = { Text("Emergency Number 1") },
                placeholder = { Text("+91xxxxxxxxxx") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MintGreen,
                    unfocusedBorderColor = WarmGray
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = phone2,
                onValueChange = { phone2 = it },
                label = { Text("Emergency Number 2 (optional)") },
                placeholder = { Text("+91xxxxxxxxxx") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MintGreen,
                    unfocusedBorderColor = WarmGray
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    prefs.smsPhone1 = phone1.trim().ifBlank { null }
                    prefs.smsPhone2 = phone2.trim().ifBlank { null }
                    if (!smsGranted) {
                        smsPermLauncher.launch(Manifest.permission.SEND_SMS)
                    } else {
                        Toast.makeText(context, "SMS numbers saved", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MintGreen)
            ) { Text("Save SMS Numbers", color = DeepCharcoal, fontWeight = FontWeight.SemiBold) }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "🔄 Auto State Restore",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = DeepCharcoal
            )
            Text(
                "Reverts location / WiFi to their previous state after each sync cycle.",
                style = AppTypography.labelSmall,
                color = WarmGray,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable state restore", color = DeepCharcoal)
                Switch(
                    checked = stateRestore,
                    onCheckedChange = { stateRestore = it; prefs.stateRestoreEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MintGreen)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lock screen after sync", color = DeepCharcoal)
                    Text(
                        "Requires Device Admin via ADB",
                        style = AppTypography.labelSmall,
                        color = WarmGray
                    )
                }
                Switch(
                    checked = lockAfterSync,
                    onCheckedChange = { lockAfterSync = it; prefs.lockAfterSync = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MintGreen)
                )
            }
        }
    }
}

// ── Frequency dialog ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FrequencySelectionDialog(
    currentMinutes: Int,
    currentSeconds: Int,
    isDevMode: Boolean,
    onDismiss: () -> Unit,
    onIntervalSelected: (Int, Boolean) -> Unit
) {
    val minuteOptions = SecurePreferences.PRESET_INTERVALS_MINUTES
    val secondOptions = SecurePreferences.DEV_INTERVALS_SECONDS

    var selectedMinutes by remember { mutableIntStateOf(if (isDevMode) SecurePreferences.DEFAULT_INTERVAL_MINUTES else currentMinutes) }
    var selectedSeconds by remember { mutableIntStateOf(if (isDevMode) currentSeconds else 30) }
    var useDevMode by remember { mutableStateOf(isDevMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Update Frequency",
                fontWeight = FontWeight.Bold,
                color = DeepCharcoal
            )
        },
        text = {
            Column {
                Text(
                    "📍 Standard Mode",
                    fontWeight = FontWeight.SemiBold,
                    color = DeepCharcoal,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    minuteOptions.forEach { minutes ->
                        FilterChip(
                            selected = !useDevMode && selectedMinutes == minutes,
                            onClick = {
                                useDevMode = false
                                selectedMinutes = minutes
                            },
                            label = { Text(SecurePreferences.formatInterval(minutes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SuccessGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "⚡ Dev Mode (seconds)",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(secondOptions) { seconds ->
                        FilterChip(
                            selected = useDevMode && selectedSeconds == seconds,
                            onClick = {
                                useDevMode = true
                                selectedSeconds = seconds
                            },
                            label = { Text("${seconds}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF9800),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (useDevMode) "Fast tracking uses more battery" else "Doze-safe alarm + watchdog enabled",
                    style = AppTypography.labelSmall,
                    color = if (useDevMode) Color(0xFFFF9800) else SuccessGreen,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (useDevMode) {
                        onIntervalSelected(selectedSeconds, true)
                    } else {
                        onIntervalSelected(selectedMinutes, false)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MintGreen,
                    contentColor = DeepCharcoal
                )
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WarmGray)
            }
        },
        containerColor = SoftWhite
    )
}
