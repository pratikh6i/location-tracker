package com.antigravity.locationtracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.locationtracker.ui.theme.MintGreen
import com.antigravity.locationtracker.ui.theme.DeepCharcoal

/**
 * Floating action button for downloading debug logs.
 * Should be visible on all screens for debugging.
 */
@Composable
fun DebugLogsFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(16.dp),
        containerColor = MintGreen,
        contentColor = DeepCharcoal,
        shape = CircleShape
    ) {
        Text(
            text = "📋",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
