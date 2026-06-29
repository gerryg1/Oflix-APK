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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    companion object {
        private const val EXTRA_VIDEO_URL = "video_url"
        private const val EXTRA_VIDEO_TITLE = "video_title"

        fun createIntent(context: Context, videoUrl: String, title: String = ""): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""

        setContent {
            val context = LocalContext.current
            val player = remember {
                // Custom DataSource with proper headers for video streaming
                // This is critical: without these headers, origin servers reject the request
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .setDefaultRequestProperties(mapOf(
                        "Referer" to "https://netnaija.film/",
                        "Origin" to "https://netnaija.film",
                        "Accept" to "*/*",
                        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
                    ))
                    .setConnectTimeoutMs(30_000)
                    .setReadTimeoutMs(30_000)
                    .setAllowCrossProtocolRedirects(true)

                val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(videoUrl))
                        prepare()
                        playWhenReady = true
                        exoPlayer = this

                        addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                            override fun onAudioSessionIdChanged(
                                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                                audioSessionId: Int
                            ) {
                                applyAudioProcessing(audioSessionId)
                            }
                        })
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

                // Title overlay at top
                if (videoTitle.isNotEmpty()) {
                    Text(
                        text = videoTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 56.dp, top = 16.dp, end = 16.dp)
                            .background(Color(0x66000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    private fun applyAudioProcessing(sessionId: Int) {
        if (sessionId == 0) return
        try {
            // 1. Equalizer for bass warmth and voice clarity
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bands = numberOfBands
                if (bands >= 5) {
                    setBandLevel(0, 200)   // +2dB at ~60Hz (bass warmth)
                    setBandLevel(3, 300)   // +3dB at ~3.6kHz (voice clarity)
                    setBandLevel(4, 150)   // +1.5dB at ~14kHz (air/sparkle)
                }
            }

            // 2. Dynamics Processing (Compressor) - Android 9.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, 0, true, 0, true, 0, true
                )
                val config = builder.build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, config).apply {
                    enabled = true
                }
            }

            // 3. LoudnessEnhancer (Makeup gain +1.4dB)
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(1400)
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
