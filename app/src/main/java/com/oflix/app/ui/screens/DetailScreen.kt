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

@Composable
fun DetailScreen(
    mediaId: String,
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    // Find the item in mock data
    val mediaItem = MockData.trending.find { it.id == mediaId }
        ?: MockData.indonesianMovies.find { it.id == mediaId }
        ?: MockData.anime.find { it.id == mediaId }
        ?: MockData.komik.find { it.id == mediaId }
    
    if (mediaItem == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Konten tidak ditemukan", color = Color.White)
            Button(onClick = onNavigateBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("Kembali")
            }
        }
        return
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
                model = mediaItem.posterUrl,
                contentDescription = mediaItem.title,
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
                text = mediaItem.title,
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
                if (mediaItem.year != null) {
                    MetaBadge(text = mediaItem.year)
                }
                if (mediaItem.rating != null) {
                    MetaBadge(text = "⭐ ${mediaItem.rating}")
                }
                if (mediaItem.duration != null) {
                    MetaBadge(text = mediaItem.duration)
                }
                MetaBadge(
                    text = mediaItem.category,
                    backgroundColor = Color(0x26E50914), // 0.15 alpha
                    textColor = PrimaryRed
                )
            }
            
            // Watch Button
            val isKomik = mediaItem.category == "Komik"
            val actionLabel = if (isKomik) "Baca Bab 1" else "Tonton Sekarang"
            
            Button(
                onClick = { /* TODO: Play Video / Read Manga */ },
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
                    text = actionLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                )
            }
            
            Text(
                text = "Sinopsis sementara. Aplikasi ini adalah porting dari versi web React Vite. Tampilan sudah diadaptasi menggunakan Jetpack Compose untuk memberikan pengalaman native Android yang halus dan responsif.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 24.dp)
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
