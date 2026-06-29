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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.oflix.app.api.SeasonData
import com.oflix.app.api.StreamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    companion object {
        private const val EXTRA_VIDEO_URL = "video_url"
        private const val EXTRA_VIDEO_TITLE = "video_title"
        private const val EXTRA_SUBTITLES = "subtitles"
        private const val EXTRA_DOWNLOADS = "downloads"
        private const val EXTRA_SUBJECT_ID = "subject_id"
        private const val EXTRA_DETAIL_PATH = "detail_path"
        private const val EXTRA_SEASON_IDX = "season_idx"
        private const val EXTRA_EPISODE_IDX = "episode_idx"
        private const val EXTRA_SEASONS_JSON = "seasons_json"

        fun createIntent(
            context: Context, videoUrl: String, title: String = "", subtitles: String = "",
            downloads: String = "", subjectId: String = "", detailPath: String = "",
            seasonIdx: Int = 0, episodeIdx: Int = 0, seasonsJson: String = "[]"
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_VIDEO_TITLE, title)
                putExtra(EXTRA_SUBTITLES, subtitles)
                putExtra(EXTRA_DOWNLOADS, downloads)
                putExtra(EXTRA_SUBJECT_ID, subjectId)
                putExtra(EXTRA_DETAIL_PATH, detailPath)
                putExtra(EXTRA_SEASON_IDX, seasonIdx)
                putExtra(EXTRA_EPISODE_IDX, episodeIdx)
                putExtra(EXTRA_SEASONS_JSON, seasonsJson)
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

        val initialVideoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        val initialVideoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        val initialSubtitlesRaw = intent.getStringExtra(EXTRA_SUBTITLES) ?: ""
        val initialDownloadsRaw = intent.getStringExtra(EXTRA_DOWNLOADS) ?: ""
        
        val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: ""
        val detailPath = intent.getStringExtra(EXTRA_DETAIL_PATH) ?: ""
        val initialSeasonIdx = intent.getIntExtra(EXTRA_SEASON_IDX, 0)
        val initialEpisodeIdx = intent.getIntExtra(EXTRA_EPISODE_IDX, 0)
        val seasonsJson = intent.getStringExtra(EXTRA_SEASONS_JSON) ?: "[]"

        // Parse Seasons
        val seasons: List<SeasonData> = try {
            val type = object : TypeToken<List<SeasonData>>() {}.type
            Gson().fromJson(seasonsJson, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        setContent {
            val context = LocalContext.current
            val activity = context as? Activity
            val coroutineScope = rememberCoroutineScope()

            // Main playback states
            var videoTitle by remember { mutableStateOf(initialVideoTitle) }
            var subtitlesRaw by remember { mutableStateOf(initialSubtitlesRaw) }
            var downloadsRaw by remember { mutableStateOf(initialDownloadsRaw) }
            
            // Parse download entries for Quality: "url;;resolution;;label"
            data class DownloadTrack(val url: String, val resolution: Int, val label: String)
            val downloadTracks = remember(downloadsRaw) {
                if (downloadsRaw.isNotEmpty()) {
                    downloadsRaw.split("|").mapNotNull { entry ->
                        val parts = entry.split(";;")
                        if (parts.size >= 3 && parts[0].isNotEmpty()) {
                            DownloadTrack(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
                        } else null
                    }
                } else emptyList()
            }

            // Default to initialVideoUrl to avoid playback bugs with expired download links
            var videoUrl by remember { mutableStateOf(initialVideoUrl) }
            
            var currentSeasonIdx by remember { mutableIntStateOf(initialSeasonIdx) }
            var currentEpisodeIdx by remember { mutableIntStateOf(initialEpisodeIdx) }

            // Player logic states
            var isPlaying by remember { mutableStateOf(true) }
            var currentPosition by remember { mutableLongStateOf(0L) }
            var bufferedPosition by remember { mutableLongStateOf(0L) }
            var totalDuration by remember { mutableLongStateOf(0L) }
            var showControls by remember { mutableStateOf(true) }
            var buffering by remember { mutableStateOf(true) }
            var isLocked by remember { mutableStateOf(false) }

            // Brightness logic
            var brightness by remember { mutableFloatStateOf(
                try {
                    android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
                } catch (e: Exception) { 0.5f }
            ) }
            var showBrightnessOverlay by remember { mutableStateOf(false) }

            // Apply brightness
            LaunchedEffect(brightness) {
                activity?.window?.let { w ->
                    val attrs = w.attributes
                    attrs.screenBrightness = brightness
                    w.attributes = attrs
                }
                showBrightnessOverlay = true
                delay(1500)
                showBrightnessOverlay = false
            }

            var playbackSpeed by remember { mutableFloatStateOf(1f) }
            var videoScale by remember { mutableFloatStateOf(1f) }
            
            // Menus
            var showSpeedMenu by remember { mutableStateOf(false) }
            var showSubtitleMenu by remember { mutableStateOf(false) }
            var showQualityMenu by remember { mutableStateOf(false) }
            var showEpisodesMenu by remember { mutableStateOf(false) }

            // Parse subtitle entries: "url;;langCode;;langName"
            data class SubtitleTrack(val url: String, val code: String, val name: String)
            val subtitleTracks = remember(subtitlesRaw) {
                if (subtitlesRaw.isNotEmpty()) {
                    subtitlesRaw.split("|").mapNotNull { entry ->
                        val parts = entry.split(";;")
                        if (parts.size >= 3 && parts[0].isNotEmpty()) {
                            SubtitleTrack(parts[0], parts[1], parts[2])
                        } else null
                    }
                } else emptyList()
            }
            
            // Default subtitle
            val defaultSubIdx = remember(subtitleTracks) {
                if (subtitleTracks.isEmpty()) -1
                else {
                    val idIdx = subtitleTracks.indexOfFirst {
                        it.code.lowercase() in listOf("id", "ind", "indonesia") ||
                        it.name.lowercase().contains("indonesia")
                    }
                    if (idIdx != -1) idIdx else 0
                }
            }
            var selectedSubIdx by remember(subtitleTracks) { mutableIntStateOf(defaultSubIdx) }

            // Handle Back Button (Block if locked)
            BackHandler(enabled = true) {
                if (!isLocked) {
                    activity?.finish()
                }
            }

            // Auto-hide controls after 4 seconds (only when playing)
            LaunchedEffect(showControls, isLocked, isPlaying) {
                if (showControls && !isLocked && isPlaying) {
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

            // ExoPlayer Initialization & Updates
            val player = remember {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .setDefaultRequestProperties(mapOf("Referer" to "https://netnaija.film/", "Origin" to "https://netnaija.film"))
                    .setAllowCrossProtocolRedirects(true)
                val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
                ExoPlayer.Builder(context)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
            }

            // Update Media Item when videoUrl changes
            LaunchedEffect(videoUrl) {
                if (videoUrl.isEmpty()) return@LaunchedEffect
                
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

                player.setMediaItem(mediaItemBuilder.build())
                player.prepare()
                player.playWhenReady = true
                exoPlayer = player

                // Initial Subtitle selection
                if (defaultSubIdx != -1 && subtitleTracks.isNotEmpty()) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setPreferredTextLanguage(subtitleTracks[defaultSubIdx].code.ifEmpty { null })
                        .build()
                }

                player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onAudioSessionIdChanged(
                        eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                        audioSessionId: Int
                    ) {
                        applyAudioProcessing(audioSessionId)
                    }
                })
            }

            DisposableEffect(Unit) {
                onDispose {
                    releaseAudioEffects()
                    player.release()
                }
            }
            
            // Function to fetch a new stream (change episode)
            fun playEpisode(sIdx: Int, eIdx: Int) {
                if (sIdx < 0 || sIdx >= seasons.size) return
                val season = seasons[sIdx]
                if (eIdx < 0 || eIdx >= season.episodes.size) return
                val episode = season.episodes[eIdx]
                
                buffering = true
                showEpisodesMenu = false
                currentSeasonIdx = sIdx
                currentEpisodeIdx = eIdx
                videoTitle = if (episode.title.contains(initialVideoTitle, ignoreCase = true)) episode.title else "$initialVideoTitle - ${episode.title}"
                
                coroutineScope.launch {
                    val se = (sIdx + 1).toString()
                    val ep = (eIdx + 1).toString()
                    var result: StreamRepository.StreamResult? = null
                    
                    // Retry up to 3 times
                    for (i in 1..3) {
                        result = StreamRepository.fetchStream(subjectId, se, ep, detailPath)
                        if (result.success && result.videoUrl.isNotEmpty()) break
                        delay(500)
                    }
                    
                    if (result != null && result.success && result.videoUrl.isNotEmpty()) {
                        subtitlesRaw = result.captions.joinToString("|") { "${it.url};;${it.languageCode};;${it.language}" }
                        downloadsRaw = result.downloads.joinToString("|") { "${it.url};;${it.resolution};;${it.label}" }
                        videoUrl = result.videoUrl
                    } else {
                        // Error loading stream
                        buffering = false
                    }
                }
            }

            fun playNextEpisode() {
                if (seasons.isEmpty()) return
                val currentSeason = seasons.getOrNull(currentSeasonIdx) ?: return
                if (currentEpisodeIdx + 1 < currentSeason.episodes.size) {
                    playEpisode(currentSeasonIdx, currentEpisodeIdx + 1)
                } else if (currentSeasonIdx + 1 < seasons.size) {
                    playEpisode(currentSeasonIdx + 1, 0)
                }
            }

            val hasNextEpisode = seasons.isNotEmpty() && (
                (seasons.getOrNull(currentSeasonIdx)?.episodes?.size ?: 0) > currentEpisodeIdx + 1 ||
                currentSeasonIdx + 1 < seasons.size
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Video View
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false  
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) 

                            // Subtitle styling matching globals.css
                            subtitleView?.apply {
                                setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f)
                                setStyle(
                                    androidx.media3.ui.CaptionStyleCompat(
                                        android.graphics.Color.WHITE,
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT,
                                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                        android.graphics.Color.BLACK,
                                        try {
                                            android.graphics.Typeface.createFromAsset(ctx.assets, "fonts/NetflixSans-Bold.otf")
                                        } catch (e: Exception) { null }
                                    )
                                )
                                setBottomPaddingFraction(0.15f)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(scaleX = videoScale, scaleY = videoScale)
                        .pointerInput(isLocked) {
                            detectTapGestures(
                                onTap = { 
                                    if (!isLocked) showControls = !showControls 
                                    else showControls = true // Wake up just to show Lock icon
                                },
                                onDoubleTap = { offset ->
                                    if (!isLocked) {
                                        val w = size.width
                                        if (offset.x < w / 3) player.seekTo(player.currentPosition - 10_000)
                                        else if (offset.x > w * 2 / 3) player.seekTo(player.currentPosition + 10_000)
                                    }
                                },
                                onLongPress = {
                                    if (!isLocked) showControls = false
                                }
                            )
                        }
                        .pointerInput(isLocked) {
                            if (!isLocked) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    videoScale = (videoScale * zoom).coerceIn(1f, 3f)
                                }
                            }
                        }
                )

                // Invisible overlay on the left 30% for Brightness gesture (only when not locked)
                if (!isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.3f)
                            .align(Alignment.CenterStart)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    // dragAmount is positive when dragging down (reduce brightness)
                                    val sensitivity = 0.002f
                                    brightness = (brightness - (dragAmount * sensitivity)).coerceIn(0.05f, 1f)
                                }
                            }
                    )
                }

                // Buffering indicator
                if (buffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }

                // Lock Icon Button (visible when showControls)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
                ) {
                    IconButton(
                        onClick = { isLocked = !isLocked; showControls = true },
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0x66000000))
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "Lock",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Brightness Overlay (visible when controls are shown or when dragged)
                AnimatedVisibility(
                    visible = (showControls || showBrightnessOverlay) && !isLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp)).padding(12.dp)
                    ) {
                        Text("☀", fontSize = 24.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.width(6.dp).height(100.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x66FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier.width(6.dp).fillMaxHeight(brightness).align(Alignment.BottomCenter)
                                    .clip(RoundedCornerShape(3.dp)).background(Color(0xFFE50914))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(brightness * 100).toInt()}%", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // ═══════════════════════════════════════════════
                //  CUSTOM CONTROLS OVERLAY (HIDDEN IF LOCKED)
                // ═══════════════════════════════════════════════
                AnimatedVisibility(
                    visible = showControls && !isLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color(0xAA000000),
                                    0.2f to Color.Transparent,
                                    0.7f to Color.Transparent,
                                    1.0f to Color(0xDD000000)
                                )
                            )
                    ) {
                        // ── TOP BAR: Title + Close ──
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                                .align(Alignment.TopCenter)
                        ) {
                            Text(
                                text = videoTitle,
                                color = Color.White,
                                fontSize = 16.sp,
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
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // ── CENTER: Rewind / Play-Pause / Forward ──
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(50.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = { player.seekTo(player.currentPosition - 10_000) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(52.dp).graphicsLayer { scaleX = -1f }
                                    )
                                    Text("10", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                }
                            }

                            // Play/Pause
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                    isPlaying = player.isPlaying
                                },
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x44FFFFFF))
                            ) {
                                if (isPlaying) {
                                    // Pause icon (two vertical bars)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(modifier = Modifier.width(8.dp).height(28.dp).background(Color.White))
                                        Box(modifier = Modifier.width(8.dp).height(28.dp).background(Color.White))
                                    }
                                } else {
                                    // Play icon
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { player.seekTo(player.currentPosition + 10_000) },
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Text("10", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                        }

                        // ── BOTTOM: Progress bar + Action buttons ──
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            // Progress Slider with Buffering Track
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                val progress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
                                val bufferProgress = if (totalDuration > 0) bufferedPosition.toFloat() / totalDuration.toFloat() else 0f

                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    // Buffering indicator (secondary track)
                                    LinearProgressIndicator(
                                        progress = bufferProgress,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(0x88FFFFFF), // Buffered progress (white)
                                        trackColor = Color(0x33FFFFFF) // Unbuffered progress (dark grey)
                                    )
                                    
                                    // Primary Slider
                                    Slider(
                                        value = progress,
                                        onValueChange = { newVal ->
                                            player.seekTo((newVal * totalDuration).toLong())
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFE50914),
                                            activeTrackColor = Color(0xFFE50914), // Red for played amount
                                            inactiveTrackColor = Color.Transparent // Show buffering behind
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = formatTime(currentPosition) + " / " + formatTime(totalDuration),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bottom action bar
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                BottomAction(icon = Icons.Default.Speed, label = "Speed (${playbackSpeed}x)", onClick = { showSpeedMenu = true })
                                Spacer(modifier = Modifier.width(20.dp))
                                if (seasons.isNotEmpty()) {
                                    BottomAction(icon = Icons.Default.List, label = "Episodes", onClick = { 
                                        showEpisodesMenu = true
                                        player.pause()
                                        isPlaying = false
                                    })
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                                if (subtitleTracks.isNotEmpty()) {
                                    BottomAction(icon = Icons.Default.Subtitles, label = "Audio & Subtitles", onClick = { showSubtitleMenu = true })
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                                if (downloadTracks.isNotEmpty()) {
                                    BottomAction(icon = Icons.Default.Settings, label = "Auto : 1080p", onClick = { showQualityMenu = true })
                                    Spacer(modifier = Modifier.width(20.dp))
                                }
                                if (hasNextEpisode) {
                                    BottomAction(icon = Icons.Default.SkipNext, label = "Next Episode", onClick = { playNextEpisode() })
                                }
                            }
                        }
                    }
                }

                // ── Quality Menu Dropdown ──
                if (showQualityMenu) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showQualityMenu = false }, contentAlignment = Alignment.Center) {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                            Column(modifier = Modifier.padding(8.dp).widthIn(min = 200.dp)) {
                                Text("Quality", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                                // Auto HLS Option
                                Text(
                                    text = "Auto (HLS)",
                                    color = if (videoUrl.contains(".m3u8")) Color(0xFFE50914) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (videoUrl.contains(".m3u8")) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth().clickable { showQualityMenu = false }.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                                // Downloads (MP4 options)
                                downloadTracks.forEach { track ->
                                    val isSelected = videoUrl == track.url
                                    Text(
                                        text = "${track.resolution}p ${track.label}",
                                        color = if (isSelected) Color(0xFFE50914) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            videoUrl = track.url
                                            showQualityMenu = false
                                        }.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Speed Menu Dropdown ──
                if (showSpeedMenu) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showSpeedMenu = false }, contentAlignment = Alignment.Center) {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    val isSelected = playbackSpeed == spd
                                    Text(
                                        text = "${spd}x" + if (spd == 1.0f) " (Normal)" else "",
                                        color = if (isSelected) Color(0xFFE50914) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth().clickable { playbackSpeed = spd; player.setPlaybackSpeed(spd); showSpeedMenu = false }.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Subtitle Menu Dropdown ──
                if (showSubtitleMenu) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showSubtitleMenu = false }, contentAlignment = Alignment.Center) {
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                            Column(modifier = Modifier.padding(8.dp).widthIn(min = 200.dp)) {
                                Text("Audio & Subtitles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                                Text(
                                    text = "Off",
                                    color = if (selectedSubIdx == -1) Color(0xFFE50914) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedSubIdx == -1) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedSubIdx = -1
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                                        showSubtitleMenu = false
                                    }.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                                subtitleTracks.forEachIndexed { idx, track ->
                                    val isSelected = selectedSubIdx == idx
                                    Text(
                                        text = track.name.ifEmpty { "Subtitle ${idx + 1}" },
                                        color = if (isSelected) Color(0xFFE50914) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedSubIdx = idx
                                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(track.code.ifEmpty { null }).build()
                                            showSubtitleMenu = false
                                        }.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // ── Episodes Slide-Up Panel ──
                AnimatedVisibility(
                    visible = showEpisodesMenu,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xAA000000))) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                                .background(Color(0xFF141414))
                        ) {
                            // Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text(
                                    text = "Episodes",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { 
                                    showEpisodesMenu = false 
                                    player.play()
                                    isPlaying = true
                                }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Episodes", tint = Color.White)
                                }
                            }
                            
                            // List of Episodes
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                seasons.forEachIndexed { sIdx, season ->
                                    item {
                                        Text(
                                            text = "Season ${season.season}",
                                            color = Color(0xFF888888),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                        )
                                    }
                                    itemsIndexed(season.episodes) { eIdx, ep ->
                                        val isCurrent = sIdx == currentSeasonIdx && eIdx == currentEpisodeIdx
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { playEpisode(sIdx, eIdx) }
                                                .background(if (isCurrent) Color(0x33E50914) else Color.Transparent)
                                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${ep.episode}. ${ep.title}",
                                                color = if (isCurrent) Color.White else Color(0xFFCCCCCC),
                                                fontSize = 14.sp,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
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
private fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
