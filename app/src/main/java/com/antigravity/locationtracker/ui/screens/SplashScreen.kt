package com.antigravity.locationtracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.locationtracker.R
import com.antigravity.locationtracker.ui.theme.DeepCharcoal
import com.antigravity.locationtracker.ui.theme.SoftWhite
import kotlinx.coroutines.delay

/**
 * Splash screen with animated logo.
 * Auto-navigates after 2 seconds.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Animate in
        scale.animateTo(1f, animationSpec = tween(800))
        alpha.animateTo(1f, animationSpec = tween(600))
        
        // Wait
        delay(1500)
        
        // Navigate
        onSplashComplete()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.wya_logo),
                contentDescription = "wya Logo",
                modifier = Modifier.size(150.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App name
            Text(
                text = "wya",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = DeepCharcoal
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Where You At",
                fontSize = 16.sp,
                color = DeepCharcoal.copy(alpha = 0.6f)
            )
        }
    }
}
