package com.oflix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.oflix.app.PlayerActivity
import com.oflix.app.api.*
import com.oflix.app.ui.components.shimmerEffect
import com.oflix.app.ui.theme.PrimaryRed
import com.oflix.app.ui.theme.TextMuted

@Composable
fun DetailScreen(
    mediaId: String,
    onNavigateBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val streamState by viewModel.streamState.collectAsState()

    var currentSeason by remember { mutableIntStateOf(0) }

    LaunchedEffect(mediaId) {
        viewModel.loadDetail(mediaId)
    }

    // Launch player when stream is ready
    LaunchedEffect(streamState) {
        if (streamState is StreamUiState.Ready) {
            val ready = streamState as StreamUiState.Ready
            context.startActivity(PlayerActivity.createIntent(context, ready.videoUrl, ready.title))
            viewModel.resetStream()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                // Skeleton Detail View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .shimmerEffect()
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(32.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) {
                                Box(modifier = Modifier.width(50.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(24.dp))
                        repeat(3) {
                            Box(modifier = Modifier.fillMaxWidth(if (it == 2) 0.7f else 1f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        // Episode skeleton
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.width(100.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                            }
                        }
                    }
                }
            }

            is DetailUiState.Success -> {
                val detail = state.detail
                val optimizedCover = if (detail.coverUrl.isNotEmpty())
                    "https://funny-kitten-ad51d6.netlify.app/img?url=${java.net.URLEncoder.encode(detail.coverUrl, "UTF-8")}&w=600&q=80"
                else ""

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 24.dp)
                ) {
                    // Hero Image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                    ) {
                        AsyncImage(
                            model = optimizedCover,
                            contentDescription = detail.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0x66000000), Color(0xFF0A0A0A)),
                                        startY = 200f
                                    )
                                )
                        )
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .padding(16.dp)
                                .background(Color(0x88000000), RoundedCornerShape(50))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }

                    // Content Info
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = detail.title,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 34.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Meta Badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            if (detail.year.isNotEmpty()) MetaBadge(text = detail.year)
                            if (detail.rating.isNotEmpty()) MetaBadge(text = "⭐ ${detail.rating}")
                            if (detail.genre.isNotEmpty()) {
                                detail.genre.split(",").take(2).forEach { g ->
                                    MetaBadge(text = g.trim())
                                }
                            }
                            MetaBadge(
                                text = if (detail.isMovie) "Film" else "Series",
                                backgroundColor = Color(0x26E50914),
                                textColor = PrimaryRed
                            )
                        }

                        // Watch Button
                        val isStreamLoading = streamState is StreamUiState.Loading
                        val watchLabel = when {
                            detail.isMovie -> "▶  Tonton Film"
                            else -> "▶  Tonton Ep. 1"
                        }

                        Button(
                            onClick = {
                                if (detail.isMovie) {
                                    viewModel.loadStream(detail.subjectId, -1, -1, detail.title)
                                } else {
                                    val season = detail.seasons.getOrNull(currentSeason)
                                    val ep = season?.episodes?.firstOrNull()
                                    if (ep != null) {
                                        val episodeTitle = "${detail.title} · S${season.season} E${ep.episode}"
                                        viewModel.loadStream(detail.subjectId, currentSeason, 0, episodeTitle)
                                    }
                                }
                            },
                            enabled = !isStreamLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (isStreamLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menghubungkan...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            } else {
                                Text(watchLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
                            }
                        }

                        // Stream error
                        if (streamState is StreamUiState.Error) {
                            Text(
                                text = "⚠ ${(streamState as StreamUiState.Error).message}",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description
                        if (detail.description.isNotEmpty()) {
                            Text(
                                text = detail.description,
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Info chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                            if (detail.country.isNotEmpty()) {
                                MetaBadge(text = "🌍 ${detail.country}")
                            }
                            if (detail.duration.isNotEmpty()) {
                                MetaBadge(text = "⏱ ${detail.duration}")
                            }
                        }

                        // ═══════════════════════════════════════════════
                        //  EPISODE LIST (Series only)
                        // ═══════════════════════════════════════════════
                        if (!detail.isMovie && detail.seasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Section Title + Season Tabs
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Text(
                                    text = "Episode",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(16.dp))

                                if (detail.seasons.size > 1) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        detail.seasons.forEachIndexed { idx, season ->
                                            val isActive = idx == currentSeason
                                            Text(
                                                text = "Season ${season.season}",
                                                color = if (isActive) Color.White else Color(0xFF888888),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(
                                                        if (isActive) PrimaryRed else Color(0xFF1A1A1A)
                                                    )
                                                    .clickable { currentSeason = idx }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Episode Grid
                            val episodes = detail.seasons.getOrNull(currentSeason)?.episodes ?: emptyList()
                            val seasonNum = detail.seasons.getOrNull(currentSeason)?.season ?: 1

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Show episodes in rows of 3
                                episodes.chunked(3).forEach { rowEpisodes ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowEpisodes.forEach { ep ->
                                            val epIdx = ep.episode - 1
                                            EpisodeCard(
                                                episodeNumber = ep.episode,
                                                title = ep.title,
                                                isLoading = isStreamLoading,
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    val episodeTitle = "${detail.title} · S${seasonNum} E${ep.episode}"
                                                    viewModel.loadStream(
                                                        detail.subjectId,
                                                        currentSeason,
                                                        epIdx,
                                                        episodeTitle
                                                    )
                                                }
                                            )
                                        }
                                        // Fill remaining space if row isn't full
                                        repeat(3 - rowEpisodes.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is DetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ ${state.message}", color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("Kembali") }
                    }
                }
            }
        }

        // Top Progress Bar - always visible during loading
        if (uiState is DetailUiState.Loading || streamState is StreamUiState.Loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = PrimaryRed,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun EpisodeCard(
    episodeNumber: Int,
    title: String,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable(enabled = !isLoading) { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616))
    ) {
        Column {
            // Episode thumbnail placeholder with play icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0x99FFFFFF),
                    modifier = Modifier.size(28.dp)
                )
                // Episode number badge
                Text(
                    text = "$episodeNumber",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Episode title
            Text(
                text = title,
                color = Color(0xFFCCCCCC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun MetaBadge(
    text: String,
    backgroundColor: Color = Color(0xFF1E1E1E),
    textColor: Color = Color(0xFFCCCCCC)
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
