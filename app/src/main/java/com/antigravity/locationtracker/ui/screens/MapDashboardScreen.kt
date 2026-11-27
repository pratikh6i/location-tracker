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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.MutedRedLight
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.WarningAmberLight
import com.antigravity.locationtracker.ui.theme.WarmGray
import com.antigravity.locationtracker.util.DeviceControlHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Map Dashboard screen using OpenStreetMap (osmdroid).
 * Properly manages MapView lifecycle, centers on demand without fighting pan/zoom,
 * and surfaces real-time anti-theft protection warnings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDashboardScreen(
    currentLatitude: Double?,
    currentLongitude: Double?,
    lastLocationTime: Long?,
    lastAccuracy: Float?,
    batteryLevel: Int,
    pendingSyncCount: Int,
    userName: String?,
    spreadsheetUrl: String?,
    intervalDisplay: String,
    protectionStatus: DeviceControlHelper.ProtectionStatus,
    isSyncing: Boolean,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSosClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onFixLocationClick: () -> Unit,
    onFixNetworkClick: () -> Unit,
    onFixBatteryClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showShareSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()

    val hasValidFix = currentLatitude != null && currentLongitude != null &&
        (currentLatitude != 0.0 || currentLongitude != 0.0)
    val defaultLat = 18.5204
    val defaultLng = 73.8567
    val lat = if (hasValidFix) currentLatitude!! else defaultLat
    val lng = if (hasValidFix) currentLongitude!! else defaultLng

    Configuration.getInstance().userAgentValue = context.packageName

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mv = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mv?.onResume()
                Lifecycle.Event.ON_PAUSE -> mv?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mv?.onPause()
            mv?.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(if (hasValidFix) 16.5 else 12.0)
                    controller.setCenter(GeoPoint(lat, lng))
                    if (hasValidFix) centeredOnce = true

                    val marker = Marker(this).apply {
                        position = GeoPoint(lat, lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = userName ?: "Device"
                        snippet = buildSnippet(batteryLevel, lastAccuracy, lastLocationTime)
                    }
                    overlays.add(marker)
                    onResume()
                    mapViewRef = this
                }
            },
            update = { mapView ->
                val point = GeoPoint(lat, lng)
                if (hasValidFix && !centeredOnce) {
                    mapView.controller.setZoom(16.5)
                    mapView.controller.animateTo(point)
                    centeredOnce = true
                }
                mapView.overlays.filterIsInstance<Marker>().firstOrNull()?.let { marker ->
                    marker.position = point
                    marker.title = userName ?: "Device"
                    marker.snippet = buildSnippet(batteryLevel, lastAccuracy, lastLocationTime)
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Protection Alert Banner at Top if any critical setting is OFF
        if (!protectionStatus.locationServiceEnabled || !protectionStatus.internetAvailable || !protectionStatus.batteryOptimizationIgnored) {
            val (alertText, onFix) = when {
                !protectionStatus.locationServiceEnabled ->
                    "GPS Location is OFF — tap to turn ON" to onFixLocationClick
                !protectionStatus.internetAvailable ->
                    "No Internet / Mobile Data — pings queued offline" to onFixNetworkClick
                else ->
                    "Battery optimization active — tap to allow background" to onFixBatteryClick
            }

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp)
                    .clickable(onClick = onFix),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!protectionStatus.locationServiceEnabled) MutedRedLight else WarningAmberLight
                )
            ) {
                Text(
                    text = "⚠️ $alertText",
                    style = AppTypography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = DeepCharcoal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        // Left floating action buttons
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onSettingsClick,
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text("⚙️", fontSize = 20.sp)
            }

            FloatingActionButton(
                onClick = onProfileClick,
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text("🛡️", fontSize = 20.sp)
            }

            FloatingActionButton(
                onClick = onSosClick,
                containerColor = Color(0xFFE53935),
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Text("SOS", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            FloatingActionButton(
                onClick = {
                    val message = if (pendingSyncCount == 0) {
                        "All locations synced!"
                    } else {
                        "$pendingSyncCount locations pending sync"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text(if (pendingSyncCount == 0) "✅" else "📤", fontSize = 20.sp)
            }
        }

        // Right floating action buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onRefreshClick,
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = DeepCharcoal
                    )
                } else {
                    Text("🔄", fontSize = 18.sp)
                }
            }

            FloatingActionButton(
                onClick = {
                    mapViewRef?.let { mv ->
                        mv.controller.setZoom(17.0)
                        mv.controller.animateTo(GeoPoint(lat, lng))
                    }
                    onRefreshClick()
                },
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text("📍", fontSize = 18.sp)
            }
        }

        // Bottom Panel
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SoftWhite)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(WarmGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Every $intervalDisplay • Bat $batteryLevel%",
                            style = AppTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepCharcoal
                        )
                        val timeText = formatDashboardTime(lastLocationTime)
                        val syncText = if (pendingSyncCount == 0) "All synced ✓" else "$pendingSyncCount pending"
                        Text(
                            text = if (hasValidFix) "$timeText • $syncText" else "Waiting for first GPS fix • $syncText",
                            style = AppTypography.labelMedium,
                            color = if (pendingSyncCount == 0) SuccessGreen else WarmGray
                        )
                    }

                    Button(
                        onClick = { showShareSheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MintGreen,
                            contentColor = DeepCharcoal
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("🔗 Share", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState = bottomSheetState,
            containerColor = SoftWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share Your Location Sheet",
                    style = AppTypography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DeepCharcoal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Share this Google Sheet link so trusted people can always see your latest coordinates.",
                    style = AppTypography.bodyMedium,
                    color = WarmGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (spreadsheetUrl != null) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Location Sheet Link", spreadsheetUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Sheet not ready yet", Toast.LENGTH_SHORT).show()
                        }
                        showShareSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintGreen,
                        contentColor = DeepCharcoal
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("📋 Copy Link", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (spreadsheetUrl != null) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spreadsheetUrl))
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Sheet not ready yet", Toast.LENGTH_SHORT).show()
                        }
                        showShareSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("📊 Open Google Sheet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(8.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun buildSnippet(battery: Int, accuracy: Float?, timestamp: Long?): String {
    val accPart = accuracy?.let { "±${it.toInt()}m" } ?: ""
    val timePart = formatDashboardTime(timestamp)
    return listOfNotNull(
        "Bat: $battery%",
        accPart.takeIf { it.isNotEmpty() },
        timePart.takeIf { it != "—" }
    ).joinToString(" • ")
}

private fun formatDashboardTime(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return "—"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date(timestamp))
    }
}
