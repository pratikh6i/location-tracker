package com.antigravity.locationtracker.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.antigravity.locationtracker.ui.theme.AppTypography
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.SoftWhite
import com.antigravity.locationtracker.ui.theme.SuccessGreen
import com.antigravity.locationtracker.ui.theme.WarmGray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Map Dashboard screen using OpenStreetMap (osmdroid) - completely FREE.
 * Features floating action buttons for Settings, SOS, Profile, and sharing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDashboardScreen(
    currentLatitude: Double?,
    currentLongitude: Double?,
    lastLocationTime: Long?,
    batteryLevel: Int,
    pendingSyncCount: Int,
    userName: String?,
    spreadsheetUrl: String?,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSosClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showShareSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()
    
    // Default to Pune, India if no location
    val defaultLat = 18.5204
    val defaultLng = 73.8567
    val lat = if (currentLatitude != null && currentLatitude != 0.0) currentLatitude else defaultLat
    val lng = if (currentLongitude != null && currentLongitude != 0.0) currentLongitude else defaultLng
    
    // Configure osmdroid
    Configuration.getInstance().userAgentValue = context.packageName
    
    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap View
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(lat, lng))
                    
                    // Add marker for current location
                    val marker = Marker(this)
                    marker.position = GeoPoint(lat, lng)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = userName ?: "You"
                    marker.snippet = "Battery: $batteryLevel%"
                    overlays.add(marker)
                }
            },
            update = { mapView ->
                mapView.controller.setCenter(GeoPoint(lat, lng))
                
                // Update marker position
                mapView.overlays.filterIsInstance<Marker>().firstOrNull()?.let { marker ->
                    marker.position = GeoPoint(lat, lng)
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Floating Action Buttons - Left side
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .padding(top = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Settings button
            FloatingActionButton(
                onClick = onSettingsClick,
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text("⚙️", fontSize = 20.sp)
            }
            
            // Profile button - Opens Settings/Profile
            FloatingActionButton(
                onClick = onProfileClick,
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text("👤", fontSize = 20.sp)
            }
            
            // SOS button - RED
            FloatingActionButton(
                onClick = {
                    onSosClick()
                    Toast.makeText(context, "🆘 SOS Sent!", Toast.LENGTH_LONG).show()
                },
                containerColor = Color(0xFFE53935),
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Text("SOS", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            
            // Notification bell - Shows sync status
            FloatingActionButton(
                onClick = {
                    val message = if (pendingSyncCount == 0) {
                        "✅ All locations synced!"
                    } else {
                        "📤 $pendingSyncCount locations pending sync"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(48.dp)
            ) {
                Text("🔔", fontSize = 20.sp)
            }
        }
        
        // Right side buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Refresh/Sync button
            FloatingActionButton(
                onClick = onRefreshClick,
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(44.dp)
            ) {
                Text("🔄", fontSize = 18.sp)
            }
            
            // Center on me
            FloatingActionButton(
                onClick = {
                    Toast.makeText(context, "📍 Centering on your location", Toast.LENGTH_SHORT).show()
                },
                containerColor = SoftWhite,
                contentColor = DeepCharcoal,
                modifier = Modifier.size(44.dp)
            ) {
                Text("📍", fontSize = 18.sp)
            }
        }
        
        // Bottom Panel - Share Sheet Link
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
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(WarmGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Share Location",
                            style = AppTypography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepCharcoal
                        )
                        Text(
                            text = if (pendingSyncCount == 0) "All synced ✓" else "$pendingSyncCount pending",
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
    
    // Share bottom sheet
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
                    text = "Share Your Location",
                    style = AppTypography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = DeepCharcoal
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Share this link with family or friends to let them see your location.",
                    style = AppTypography.bodyMedium,
                    color = WarmGray,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Copy Link button
                Button(
                    onClick = {
                        spreadsheetUrl?.let { url ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Location Sheet Link", url)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "📋 Link copied!", Toast.LENGTH_SHORT).show()
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
                
                // Open Sheet button
                Button(
                    onClick = {
                        spreadsheetUrl?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
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
