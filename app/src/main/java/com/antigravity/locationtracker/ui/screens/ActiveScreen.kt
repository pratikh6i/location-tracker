package com.antigravity.locationtracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.antigravity.locationtracker.data.prefs.SecurePreferences
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.LightMint
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.PaleBlue
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.SuccessGreenLight
import com.antigravity.locationtracker.ui.theme.WarmGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Active screen shown when tracking is running.
 * Enhanced dashboard with frequency settings, sheet link, and sync controls.
 */
@Composable
fun ActiveScreen(
    lastLocationTime: Long?,
    lastLatitude: Double?,
    lastLongitude: Double?,
    batteryLevel: Int,
    pendingSyncCount: Int,
    lastSyncTime: Long?,
    userName: String?,
    spreadsheetUrl: String?,
    currentIntervalDisplay: String,
    currentIntervalMinutes: Int,
    currentIntervalSeconds: Int,
    isDevMode: Boolean,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onSyncNowClick: () -> Unit,
    onIntervalChanged: (Int, Boolean) -> Unit, // (value, isDevMode)
    onDevModeChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var showFrequencyDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SoftWhite, LightMint)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Greeting
            userName?.let { name ->
                Text(
                    text = "Hello, ${name.split(" ").firstOrNull() ?: name}",
                    style = AppTypography.titleLarge,
                    color = WarmGray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Large "Active" status indicator
            ActiveStatusIndicator()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Status cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Frequency card - clickable
                StatusCard(
                    icon = "⏱️",
                    label = "Update Frequency",
                    value = currentIntervalDisplay,
                    subtitle = if (isDevMode) "⚡ Dev Mode Active" else "Tap to change",
                    backgroundColor = if (isDevMode) Color(0xFFFFF3E0) else MintGreen.copy(alpha = 0.3f),
                    onClick = { showFrequencyDialog = true }
                )
                
                // Google Sheet link card - opens in browser
                spreadsheetUrl?.let { url ->
                    StatusCard(
                        icon = "📊",
                        label = "Your Location Sheet",
                        value = "Open in Google Sheets",
                        subtitle = "Tap to view your data",
                        backgroundColor = SuccessGreenLight,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    )
                    
                    // Share link card - copies to clipboard
                    StatusCard(
                        icon = "🔗",
                        label = "Share Sheet Link",
                        value = "Copy link to clipboard",
                        subtitle = "Share with family or others",
                        backgroundColor = PaleBlue.copy(alpha = 0.4f),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Location Sheet Link", url)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "📋 Link copied! Share it with anyone.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                
                // Last location card with coordinates
                StatusCard(
                    icon = "📍",
                    label = "Last Location",
                    value = formatTime(lastLocationTime),
                    subtitle = if (lastLatitude != null && lastLongitude != null && lastLatitude != 0.0) {
                        "%.4f, %.4f".format(lastLatitude, lastLongitude)
                    } else null,
                    backgroundColor = PaleBlue.copy(alpha = 0.3f)
                )
                
                // Battery card
                StatusCard(
                    icon = "🔋",
                    label = "Battery",
                    value = "$batteryLevel%",
                    backgroundColor = when {
                        batteryLevel > 50 -> SuccessGreenLight
                        batteryLevel > 20 -> PaleBlue.copy(alpha = 0.3f)
                        else -> Color(0xFFFEE2E2)
                    }
                )
                
                // Sync status card with Sync Now button
                SyncStatusCard(
                    pendingSyncCount = pendingSyncCount,
                    lastSyncTime = lastSyncTime,
                    isSyncing = isSyncing,
                    onSyncNowClick = onSyncNowClick
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Refresh button
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSyncing) "Syncing..." else "🔄 Refresh",
                    style = AppTypography.labelLarge
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer message
            Text(
                text = "Syncs instantly when online",
                style = AppTypography.bodyMedium,
                color = WarmGray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }
    
    // Frequency selection dialog
    if (showFrequencyDialog) {
        FrequencyDialog(
            currentMinutes = currentIntervalMinutes,
            currentSeconds = currentIntervalSeconds,
            isDevMode = isDevMode,
            onDismiss = { showFrequencyDialog = false },
            onIntervalSelected = { value, devMode ->
                onIntervalChanged(value, devMode)
                if (devMode != isDevMode) {
                    onDevModeChanged(devMode)
                }
                showFrequencyDialog = false
            }
        )
    }
}

@Composable
private fun ActiveStatusIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pulsing green circle
        Box(
            modifier = Modifier
                .scale(scale)
                .size(100.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SuccessGreen,
                            SuccessGreen.copy(alpha = 0.7f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✓",
                style = AppTypography.displayLarge,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Active",
            style = AppTypography.headlineMedium,
            color = SuccessGreen,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusCard(
    icon: String,
    label: String,
    value: String,
    subtitle: String? = null,
    backgroundColor: Color,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = AppTypography.titleLarge
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = AppTypography.labelMedium,
                    color = WarmGray
                )
                Text(
                    text = value,
                    style = AppTypography.titleMedium,
                    color = DeepCharcoal,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = AppTypography.labelSmall,
                        color = WarmGray
                    )
                }
            }
            
            // Arrow indicator if clickable
            if (onClick != null) {
                Text(
                    text = "→",
                    style = AppTypography.titleLarge,
                    color = WarmGray
                )
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    pendingSyncCount: Int,
    lastSyncTime: Long?,
    isSyncing: Boolean,
    onSyncNowClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LightMint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☁️",
                    style = AppTypography.titleLarge
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Sync Status",
                    style = AppTypography.labelMedium,
                    color = WarmGray
                )
                Text(
                    text = if (pendingSyncCount == 0) "All synced ✓" else "$pendingSyncCount pending",
                    style = AppTypography.titleMedium,
                    color = if (pendingSyncCount == 0) SuccessGreen else DeepCharcoal,
                    fontWeight = FontWeight.SemiBold
                )
                lastSyncTime?.let {
                    Text(
                        text = "Last: ${formatTime(it)}",
                        style = AppTypography.labelSmall,
                        color = WarmGray
                    )
                }
            }
            
            // Sync Now button
            if (pendingSyncCount > 0) {
                Button(
                    onClick = onSyncNowClick,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintGreen,
                        contentColor = DeepCharcoal
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = if (isSyncing) "..." else "Sync",
                        style = AppTypography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrequencyDialog(
    currentMinutes: Int,
    currentSeconds: Int,
    isDevMode: Boolean,
    onDismiss: () -> Unit,
    onIntervalSelected: (Int, Boolean) -> Unit // (value, isDevMode)
) {
    var devModeEnabled by remember { mutableStateOf(isDevMode) }
    var selectedMinutes by remember { mutableIntStateOf(currentMinutes) }
    var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }
    var customValue by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    
    val presetMinutes = SecurePreferences.PRESET_INTERVALS_MINUTES
    val presetSeconds = SecurePreferences.DEV_INTERVALS_SECONDS
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SoftWhite
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Update Frequency",
                    style = AppTypography.headlineSmall,
                    color = DeepCharcoal,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dev mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (devModeEnabled) Color(0xFFFFF3E0) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "⚡ Dev Mode",
                            style = AppTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepCharcoal
                        )
                        Text(
                            text = "Fast tracking (timing varies by GPS)",
                            style = AppTypography.labelSmall,
                            color = WarmGray
                        )
                    }
                    Switch(
                        checked = devModeEnabled,
                        onCheckedChange = { devModeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF9800)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Interval chips
                Text(
                    text = if (devModeEnabled) "Seconds" else "Minutes",
                    style = AppTypography.labelMedium,
                    color = WarmGray,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = if (devModeEnabled) presetSeconds else presetMinutes
                    val selectedValue = if (devModeEnabled) selectedSeconds else selectedMinutes
                    
                    presets.forEach { value ->
                        FilterChip(
                            selected = selectedValue == value && !showCustomInput,
                            onClick = {
                                if (devModeEnabled) {
                                    selectedSeconds = value
                                } else {
                                    selectedMinutes = value
                                }
                                showCustomInput = false
                            },
                            label = {
                                Text(
                                    text = if (devModeEnabled) "${value}s" else formatInterval(value),
                                    style = AppTypography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (devModeEnabled) Color(0xFFFF9800) else MintGreen,
                                selectedLabelColor = if (devModeEnabled) Color.White else DeepCharcoal
                            )
                        )
                    }
                    
                    // Custom chip
                    FilterChip(
                        selected = showCustomInput,
                        onClick = { showCustomInput = true },
                        label = {
                            Text(
                                text = "Custom",
                                style = AppTypography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (devModeEnabled) Color(0xFFFF9800) else MintGreen,
                            selectedLabelColor = if (devModeEnabled) Color.White else DeepCharcoal
                        )
                    )
                }
                
                // Custom input field
                AnimatedVisibility(
                    visible = showCustomInput,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = customValue,
                            onValueChange = { 
                                customValue = it.filter { char -> char.isDigit() }
                            },
                            label = { Text(if (devModeEnabled) "Seconds" else "Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (devModeEnabled) Color(0xFFFF9800) else MintGreen,
                                cursorColor = if (devModeEnabled) Color(0xFFFF9800) else MintGreen
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            val value = if (showCustomInput && customValue.isNotEmpty()) {
                                val maxValue = if (devModeEnabled) 60 else 1440
                                customValue.toIntOrNull()?.coerceIn(1, maxValue) ?: 
                                    if (devModeEnabled) selectedSeconds else selectedMinutes
                            } else {
                                if (devModeEnabled) selectedSeconds else selectedMinutes
                            }
                            onIntervalSelected(value, devModeEnabled)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (devModeEnabled) Color(0xFFFF9800) else MintGreen,
                            contentColor = if (devModeEnabled) Color.White else DeepCharcoal
                        )
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun formatInterval(minutes: Int): String {
    return when {
        minutes < 60 -> "$minutes min"
        minutes == 60 -> "1 hour"
        minutes == 120 -> "2 hours"
        minutes == 240 -> "4 hours"
        minutes % 60 == 0 -> "${minutes / 60} hours"
        else -> "$minutes min"
    }
}

private fun formatTime(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "—"
    
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
