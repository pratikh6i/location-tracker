package com.antigravity.locationtracker.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * "Set-and-forget" - no interactive controls, just status display.
 */
@Composable
fun ActiveScreen(
    lastLocationTime: Long?,
    batteryLevel: Int,
    pendingSyncCount: Int,
    lastSyncTime: Long?,
    userName: String?
) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
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
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Status cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Last location card
                StatusCard(
                    icon = "📍",
                    label = "Last Location",
                    value = formatTime(lastLocationTime),
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
                
                // Sync status card
                StatusCard(
                    icon = "☁️",
                    label = "Sync Status",
                    value = if (pendingSyncCount == 0) {
                        "All synced"
                    } else {
                        "$pendingSyncCount pending"
                    },
                    subtitle = lastSyncTime?.let { "Last sync: ${formatTime(it)}" },
                    backgroundColor = LightMint
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Footer message
            Text(
                text = "Tracking is running in the background.\nNo action required.",
                style = AppTypography.bodyMedium,
                color = WarmGray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
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
        // Pulsing green circle with "Active" text
        Box(
            modifier = Modifier
                .scale(scale)
                .size(160.dp)
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✓",
                    style = AppTypography.displayLarge,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Active",
            style = AppTypography.displayMedium,
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
    backgroundColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = AppTypography.headlineMedium
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = AppTypography.labelLarge,
                    color = WarmGray
                )
                Text(
                    text = value,
                    style = AppTypography.titleLarge,
                    color = DeepCharcoal,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = AppTypography.labelMedium,
                        color = WarmGray
                    )
                }
            }
        }
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
