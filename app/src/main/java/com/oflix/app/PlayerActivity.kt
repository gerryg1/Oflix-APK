package com.oflix.app

import android.content.Context
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    companion object {
        private const val EXTRA_VIDEO_URL = "video_url"
        fun createIntent(context: Context, videoUrl: String): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen and hide system bars
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""

        setContent {
            val context = LocalContext.current
            val player = remember {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    prepare()
                    playWhenReady = true
                    exoPlayer = this
                    
                    // Apply AudioFX matching JS Web Audio API
                    applyAudioProcessing(this.audioSessionId)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    releaseAudioEffects()
                    player.release()
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun applyAudioProcessing(sessionId: Int) {
        if (sessionId == 0) return
        try {
            // 1. Equalizer for HighPass (90Hz), LowShelf (80Hz), Peaking (3kHz), HighShelf (8kHz)
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                // Android EQ bands are typically: 60Hz, 230Hz, 910Hz, 3600Hz, 14000Hz.
                // We'll boost the 60Hz (warmth) and 3600Hz (voice clarity).
                val bands = numberOfBands
                if (bands >= 5) {
                    setBandLevel(0, 200)   // +2dB at ~60Hz
                    setBandLevel(3, 300)   // +3dB at ~3.6kHz
                    setBandLevel(4, 150)   // +1.5dB at ~14kHz
                }
            }

            // 2. Dynamics Processing (Compressor) - Android 9.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // channels
                    true, // enable mbc
                    0, // mbc bands
                    true, // enable preEQ
                    0, // preEq bands
                    true, // enable postEq
                    0, // postEq bands
                    true // enable limiter
                )
                val config = builder.build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                    enabled = true
                }
            }

            // 3. LoudnessEnhancer (Makeup gain +1.4)
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(1400) // +1.4 dB (1400 mB)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseAudioEffects() {
        equalizer?.release()
        dynamicsProcessing?.release()
        loudnessEnhancer?.release()
    }
}
