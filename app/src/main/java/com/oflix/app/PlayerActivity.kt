package com.oflix.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    companion object {
        private const val EXTRA_VIDEO_URL = "video_url"
        private const val EXTRA_VIDEO_TITLE = "video_title"
        private const val EXTRA_SUBTITLES = "subtitles"

        fun createIntent(context: Context, videoUrl: String, title: String = "", subtitles: String = ""): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
                putExtra(EXTRA_SUBTITLES, subtitles)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive + keep screen on
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        val subtitlesRaw = intent.getStringExtra(EXTRA_SUBTITLES) ?: ""

        // Parse subtitle entries: "url;;langCode;;langName|url;;langCode;;langName"
        data class SubtitleTrack(val url: String, val code: String, val name: String)
        val subtitleTracks = if (subtitlesRaw.isNotEmpty()) {
            subtitlesRaw.split("|").mapNotNull { entry ->
                val parts = entry.split(";;")
                if (parts.size >= 3 && parts[0].isNotEmpty()) {
                    SubtitleTrack(parts[0], parts[1], parts[2])
                } else null
            }
        } else emptyList()

        setContent {
            val context = LocalContext.current
            val activity = context as? Activity

            // Player state
            var isPlaying by remember { mutableStateOf(true) }
            var currentPosition by remember { mutableLongStateOf(0L) }
            var bufferedPosition by remember { mutableLongStateOf(0L) }
            var totalDuration by remember { mutableLongStateOf(0L) }
            var showControls by remember { mutableStateOf(true) }
            var buffering by remember { mutableStateOf(true) }

            // Default to device brightness
            var brightness by remember { mutableFloatStateOf(
                try {
                    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
                } catch (e: Exception) { 0.5f }
            ) }
            var playbackSpeed by remember { mutableFloatStateOf(1f) }
            var showSpeedMenu by remember { mutableStateOf(false) }
            var showSubtitleMenu by remember { mutableStateOf(false) }

            // Default to Indonesian if available, else first subtitle, else -1 (off)
            val defaultSubIdx = remember {
                if (subtitleTracks.isEmpty()) -1
                else {
                    val idIdx = subtitleTracks.indexOfFirst {
                        it.code.lowercase() in listOf("id", "ind", "indonesia") ||
                        it.name.lowercase().contains("indonesia")
                    }
                    if (idIdx != -1) idIdx else 0
                }
            }
            var selectedSubIdx by remember { mutableIntStateOf(defaultSubIdx) }

            // Auto-hide controls after 4 seconds
            LaunchedEffect(showControls) {
                if (showControls) {
                    delay(4000)
                    showControls = false
                }
            }

            // Position update timer
            LaunchedEffect(Unit) {
                while (true) {
                    exoPlayer?.let { p ->
                        currentPosition = p.currentPosition
                        bufferedPosition = p.bufferedPosition
                        totalDuration = p.duration.coerceAtLeast(0)
                        isPlaying = p.isPlaying
                        buffering = p.playbackState == Player.STATE_BUFFERING
                    }
                    delay(500)
                }
            }

            val player = remember {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .setDefaultRequestProperties(mapOf(
                        "Referer" to "https://netnaija.film/",
                        "Origin" to "https://netnaija.film",
                        "Accept" to "*/*"
                    ))
                    .setConnectTimeoutMs(30_000)
                    .setReadTimeoutMs(30_000)
                    .setAllowCrossProtocolRedirects(true)

                val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        // Build MediaItem with subtitle tracks
                        val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)

                        if (subtitleTracks.isNotEmpty()) {
                            val subtitleConfigs = subtitleTracks.mapIndexed { idx, track ->
                                MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                                    .setMimeType(
                                        when {
                                            track.url.contains(".vtt") -> MimeTypes.TEXT_VTT
                                            track.url.contains(".srt") -> MimeTypes.APPLICATION_SUBRIP
                                            track.url.contains(".ass") || track.url.contains(".ssa") -> MimeTypes.TEXT_SSA
                                            else -> MimeTypes.TEXT_VTT
                                        }
                                    )
                                    .setLanguage(track.code.ifEmpty { "und" })
                                    .setLabel(track.name.ifEmpty { "Subtitle ${idx + 1}" })
                                    .setSelectionFlags(if (idx == defaultSubIdx) C.SELECTION_FLAG_DEFAULT else 0)
                                    .build()
                            }
                            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
                        }

                        setMediaItem(mediaItemBuilder.build())
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

                        // Apply initial track selection for subtitle
                        if (defaultSubIdx != -1 && subtitleTracks.isNotEmpty()) {
                            trackSelectionParameters = trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setPreferredTextLanguage(subtitleTracks[defaultSubIdx].code.ifEmpty { null })
                                .build()
                        }
                    }
            }

            DisposableEffect(Unit) {
                onDispose {
                    releaseAudioEffects()
                    player.release()
                }
            }

            // Apply brightness
            LaunchedEffect(brightness) {
                activity?.window?.let { w ->
                    val attrs = w.attributes
                    attrs.screenBrightness = brightness
                    w.attributes = attrs
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                val w = size.width
                                if (offset.x < w / 3) {
                                    player.seekTo(player.currentPosition - 10_000)
                                } else if (offset.x > w * 2 / 3) {
                                    player.seekTo(player.currentPosition + 10_000)
                                }
                            }
                        )
                    }
            ) {
                // Video View — hide built-in controls since we have custom ones
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false  // We build our own controls
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) // Using custom spinner

                            // Subtitle styling matching globals.css
                            subtitleView?.apply {
                                setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.2f)
                                setStyle(
                                    androidx.media3.ui.CaptionStyleCompat(
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT,
                                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                        android.graphics.Color.BLACK,
                                        try {
                                            android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/NetflixSans-Bold.otf")
                                        } catch (e: Exception) {
                                            null
                                        }
                                    )
                                )
                                setBottomPaddingFraction(0.15f)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Buffering indicator
                if (buffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }

                // ═══════════════════════════════════════════════
                //  CUSTOM CONTROLS OVERLAY
                // ═══════════════════════════════════════════════
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000))
                    ) {
                        // ── TOP BAR: Title + Close ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            Text(
                                text = videoTitle,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { activity?.finish() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // ── LEFT: Brightness Slider (vertical) ──
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 16.dp)
                                .width(32.dp)
                        ) {
                            Text("☀", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Vertical slider simulated with a Box
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x66FFFFFF))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight(brightness)
                                        .align(Alignment.BottomCenter)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFE50914))
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Brightness up/down
                            Text(
                                text = "+",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { brightness = (brightness + 0.1f).coerceAtMost(1f) }
                                    .padding(4.dp)
                            )
                            Text(
                                text = "−",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { brightness = (brightness - 0.1f).coerceAtLeast(0.05f) }
                                    .padding(4.dp)
                            )
                        }

                        // ── CENTER: Rewind / Play-Pause / Forward ──
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = { player.seekTo(player.currentPosition - 10_000) },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp).androidx.compose.ui.graphics.graphicsLayer(scaleX = -1f)
                                    )
                                    Text("10", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                }
                            }

                            // Play/Pause
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                    isPlaying = player.isPlaying
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FFFFFF))
                            ) {
                                if (isPlaying) {
                                    // Pause icon (two vertical bars)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(modifier = Modifier.width(6.dp).height(24.dp).background(Color.White))
                                        Box(modifier = Modifier.width(6.dp).height(24.dp).background(Color.White))
                                    }
                                } else {
                                    // Play icon
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { player.seekTo(player.currentPosition + 10_000) },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text("10", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }

                        // ── BOTTOM: Progress bar + Action buttons ──
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xCC000000))
                                    )
                                )
                                .padding(bottom = 8.dp)
                        ) {
                            // Progress Slider with Buffering Track
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                val progress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
                                val bufferProgress = if (totalDuration > 0) bufferedPosition.toFloat() / totalDuration.toFloat() else 0f

                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    // Buffering indicator (secondary track)
                                    LinearProgressIndicator(
                                        progress = { bufferProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp)),
                                        color = Color(0x99FFFFFF), // Buffered white color
                                        trackColor = Color(0x33FFFFFF) // Base track color
                                    )
                                    
                                    // Primary Slider
                                    Slider(
                                        value = progress,
                                        onValueChange = { newVal ->
                                            player.seekTo((newVal * totalDuration).toLong())
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFE50914),
                                            activeTrackColor = Color(0xFFE50914),
                                            inactiveTrackColor = Color.Transparent // Hide so we see the buffering track
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = formatTime(currentPosition) + " / " + formatTime(totalDuration),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom action bar: Speed | Episodes | Subtitles | Quality | Next
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                BottomAction(icon = "⚡", label = "Speed (${playbackSpeed}x)", onClick = { showSpeedMenu = true })
                                BottomAction(icon = "📑", label = "Episodes", onClick = { /* TODO */ })
                                BottomAction(icon = "💬", label = "Audio & Subtitles", onClick = { showSubtitleMenu = true })
                                BottomAction(icon = "⚙", label = "Auto : 1080p", onClick = { /* TODO */ })
                                BottomAction(icon = "⏭", label = "Next Episode", onClick = { /* TODO */ })
                            }
                        }
                    }
                }

                // ── Speed Menu Dropdown ──
                if (showSpeedMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xAA000000))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showSpeedMenu = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "Playback Speed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    val isSelected = playbackSpeed == spd
                                    Text(
                                        text = "${spd}x" + if (spd == 1.0f) " (Normal)" else "",
                                        color = if (isSelected) Color(0xFFE50914) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                playbackSpeed = spd
                                                player.setPlaybackSpeed(spd)
                                                showSpeedMenu = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Subtitle Menu Dropdown ──
                if (showSubtitleMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xAA000000))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showSubtitleMenu = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Column(modifier = Modifier.padding(8.dp).widthIn(min = 200.dp)) {
                                Text(
                                    "Audio & Subtitles",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                                // Off option
                                Text(
                                    text = "Off",
                                    color = if (selectedSubIdx == -1) Color(0xFFE50914) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedSubIdx == -1) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedSubIdx = -1
                                            // Disable all text tracks
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            showSubtitleMenu = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                                if (subtitleTracks.isEmpty()) {
                                    Text(
                                        text = "No subtitles available",
                                        color = Color(0xFF666666),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                subtitleTracks.forEachIndexed { idx, track ->
                                    val isSelected = selectedSubIdx == idx
                                    Text(
                                        text = track.name.ifEmpty { "Subtitle ${idx + 1}" },
                                        color = if (isSelected) Color(0xFFE50914) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedSubIdx = idx
                                                // Enable text tracks
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setPreferredTextLanguage(track.code.ifEmpty { null })
                                                    .build()
                                                showSubtitleMenu = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%d:%02d", minutes, seconds)
    }

    private fun applyAudioProcessing(sessionId: Int) {
        if (sessionId == 0) return
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                val bands = numberOfBands
                if (bands >= 5) {
                    setBandLevel(0, 200)   // +2dB bass warmth
                    setBandLevel(3, 300)   // +3dB voice clarity
                    setBandLevel(4, 150)   // +1.5dB air/sparkle
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, 0, true, 0, true, 0, true
                )
                dynamicsProcessing = DynamicsProcessing(0, sessionId, builder.build()).apply { enabled = true }
            }
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(1400)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun releaseAudioEffects() {
        equalizer?.release()
        dynamicsProcessing?.release()
        loudnessEnhancer?.release()
    }
}

@Composable
private fun BottomAction(icon: String, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
