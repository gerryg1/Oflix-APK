package com.oflix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.oflix.app.data.MockData
import com.oflix.app.ui.theme.PrimaryRed
import com.oflix.app.ui.theme.TextMuted
import androidx.compose.ui.draw.clip

import androidx.compose.animation.Crossfade
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.oflix.app.PlayerActivity
import com.oflix.app.api.DetailUiState
import com.oflix.app.api.DetailViewModel
import com.oflix.app.ui.components.shimmerEffect

@Composable
fun DetailScreen(
    mediaId: String,
    onNavigateBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(mediaId) {
        viewModel.loadDetail(mediaId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(targetState = uiState, label = "detail_fade") { state ->
            when (state) {
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
                                Box(modifier = Modifier.width(40.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                                Box(modifier = Modifier.width(60.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                                Box(modifier = Modifier.width(50.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(24.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth(0.8f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        }
                    }
                }
                is DetailUiState.Success -> {
                    val data = state.data
                    val subject = if (data.has("subject")) data.getAsJsonObject("subject") else null
                    val metadata = if (data.has("metadata")) data.getAsJsonObject("metadata") else null

                    val title = subject?.get("title")?.asString ?: metadata?.get("title")?.asString ?: "Unknown"
                    val year = subject?.get("releaseDate")?.asString?.take(4) ?: ""
                    val rating = subject?.get("imdbRatingValue")?.asString ?: ""
                    val desc = subject?.get("brief")?.asString ?: metadata?.get("description")?.asString ?: ""
                    
                    var coverUrl = ""
                    if (subject?.has("cover") == true) {
                        coverUrl = subject.getAsJsonObject("cover")?.get("url")?.asString ?: ""
                    } else if (metadata?.has("image") == true) {
                        coverUrl = metadata.get("image").asString
                    }

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
                                model = "https://funny-kitten-ad51d6.netlify.app/img?url=${java.net.URLEncoder.encode(coverUrl, "UTF-8")}&w=600&q=80",
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Gradient Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0x66000000),
                                                Color(0xFF0A0A0A)
                                            ),
                                            startY = 200f
                                        )
                                    )
                            )
                            
                            // Back Button
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .background(Color(0x88000000), RoundedCornerShape(50))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                        
                        // Content Info
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = title,
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
                                if (year.isNotEmpty()) {
                                    MetaBadge(text = year)
                                }
                                if (rating.isNotEmpty()) {
                                    MetaBadge(text = "⭐ $rating")
                                }
                                MetaBadge(
                                    text = "HD",
                                    backgroundColor = Color(0x26E50914), // 0.15 alpha
                                    textColor = PrimaryRed
                                )
                            }
                            
                            Button(
                                onClick = {
                                    // Dummy video URL since we haven't implemented the play/ HLS decryption proxy endpoint yet
                                    val dummyVideo = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                                    context.startActivity(PlayerActivity.createIntent(context, dummyVideo))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .padding(bottom = 16.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tonton Sekarang",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            
                            Text(
                                text = desc,
                                color = TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                        }
                    }
                }
                is DetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gagal memuat: ${state.message}", color = Color.White)
                            Button(onClick = onNavigateBack, modifier = Modifier.padding(top = 16.dp)) {
                                Text("Kembali")
                            }
                        }
                    }
                }
            }
        }
        
        if (uiState is DetailUiState.Loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = PrimaryRed,
                trackColor = Color.Transparent
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
