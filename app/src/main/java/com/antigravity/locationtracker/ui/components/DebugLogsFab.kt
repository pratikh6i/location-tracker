package com.antigravity.locationtracker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        contentColor = DeepCharcoal
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download Logs",
            modifier = Modifier.size(24.dp)
        )
    }
}
