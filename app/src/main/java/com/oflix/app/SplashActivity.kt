package com.oflix.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen()
        }
    }

    @Composable
    fun SplashScreen() {
        val scale = remember { Animatable(0.5f) }
        val alpha = remember { Animatable(0f) }

        // Start animation and navigate to MainActivity
        LaunchedEffect(key1 = true) {
            // Animate scale and alpha concurrently
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = { t ->
                        // Custom Overshoot Easing for premium feel
                        val overshoot = 1.2f
                        val tMinusOne = t - 1f
                        tMinusOne * tMinusOne * ((overshoot + 1f) * tMinusOne + overshoot) + 1f
                    }
                )
            )
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800)
            )
            
            // Hold the screen for loading representation
            delay(1500)

            // Navigate to MainActivity and destroy SplashActivity
            val intent = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Layout with premium dark gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F0F), // Sleek pitch black
                            Color(0xFF231F20)  // Deep charcoal tone
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Logo
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Oflix Logo",
                    modifier = Modifier
                        .size(180.dp)
                        .scale(scale.value)
                        .alpha(alpha.value)
                )
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Red Accent Loading Indicator
                CircularProgressIndicator(
                    color = Color(0xFFE50914), // Netflix red
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .alpha(alpha.value)
                )
            }
        }
    }
}
