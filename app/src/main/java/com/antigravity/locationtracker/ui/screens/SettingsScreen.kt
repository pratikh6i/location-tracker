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
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.PaleBlue
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.WarmGray

/**
 * Settings screen with profile, frequency, and app options.
 * Now includes inline frequency dialog.
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
    onBackClick: () -> Unit,
    onIntervalChanged: (Int, Boolean) -> Unit, // (value, isDevMode)
    onDevModeChanged: (Boolean) -> Unit,
    onSignOutClick: () -> Unit
) {
    val context = LocalContext.current
    var showFrequencyDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaleBlue.copy(alpha = 0.3f))
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Text("←", fontSize = 24.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = SoftWhite
            )
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
                    // Avatar
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Update Frequency - Opens dialog
            SettingsItem(
                icon = "⏱️",
                title = "Update Frequency",
                subtitle = currentIntervalDisplay,
                onClick = { showFrequencyDialog = true }
            )
            
            SettingsItem(
                icon = "📊",
                title = "Google Sheet",
                subtitle = "Open your location data",
                onClick = {
                    spreadsheetUrl?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                }
            )
            
            SettingsItem(
                icon = "🔗",
                title = "Copy Sheet Link",
                subtitle = "Share with family",
                onClick = {
                    spreadsheetUrl?.let { url ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Location Sheet Link", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "📋 Link copied!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
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
                            text = "Fast tracking for testing",
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
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
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App version
            Text(
                text = "wya v1.0.0",
                style = AppTypography.labelSmall,
                color = WarmGray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Frequency Dialog
    if (showFrequencyDialog) {
        FrequencySelectionDialog(
            currentMinutes = currentIntervalMinutes,
            currentSeconds = currentIntervalSeconds,
            isDevMode = isDevMode,
            onDismiss = { showFrequencyDialog = false },
            onIntervalSelected = { value, devMode ->
                onIntervalChanged(value, devMode)
                showFrequencyDialog = false
                Toast.makeText(
                    context,
                    if (devMode) "⚡ Interval: ${value}s" else "⏱️ Interval: ${value} min",
                    Toast.LENGTH_SHORT
                ).show()
            }
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

/**
 * Frequency selection dialog with minute and second options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencySelectionDialog(
    currentMinutes: Int,
    currentSeconds: Int,
    isDevMode: Boolean,
    onDismiss: () -> Unit,
    onIntervalSelected: (Int, Boolean) -> Unit
) {
    val minuteOptions = listOf(5, 10, 15, 30, 60)
    val secondOptions = listOf(1, 5, 10, 30)
    
    var selectedMinutes by remember { mutableIntStateOf(if (isDevMode) 15 else currentMinutes) }
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
                // Standard intervals
                Text(
                    "📍 Standard Mode",
                    fontWeight = FontWeight.SemiBold,
                    color = DeepCharcoal,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(minuteOptions) { minutes ->
                        FilterChip(
                            selected = !useDevMode && selectedMinutes == minutes,
                            onClick = {
                                useDevMode = false
                                selectedMinutes = minutes
                            },
                            label = { Text("$minutes min") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SuccessGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Dev mode intervals
                Text(
                    "⚡ Dev Mode (seconds)",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    text = if (useDevMode) "⚠️ Fast tracking uses more battery" else "✓ Battery optimized",
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
