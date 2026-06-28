package com.oflix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.oflix.app.data.MediaItem
import com.oflix.app.data.MockData
import com.oflix.app.ui.components.CategoryTabs
import com.oflix.app.ui.components.HorizontalSection
import com.oflix.app.ui.theme.PrimaryRed

@Composable
fun HomeScreen(
    currentCategory: String,
    onCategorySelected: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp) // Padding for bottom nav
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OFLIX",
                color = PrimaryRed,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        // Category Tabs
        CategoryTabs(
            currentCategory = currentCategory,
            onCategorySelected = onCategorySelected
        )

        // Hero Banner (using the first trending item)
        val heroItem = MockData.trending.firstOrNull()
        if (heroItem != null) {
            HeroBanner(hero = heroItem, onClick = { onNavigateToDetail(heroItem.id) })
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Horizontal Sections
        HorizontalSection(
            title = "Trending Now",
            items = MockData.trending,
            onItemClick = { onNavigateToDetail(it.id) }
        )

        HorizontalSection(
            title = "Film Indonesia",
            items = MockData.indonesianMovies,
            onItemClick = { onNavigateToDetail(it.id) },
            onSeeMoreClick = { onCategorySelected("film") }
        )

        HorizontalSection(
            title = "Animasi Pilihan",
            items = MockData.anime,
            onItemClick = { onNavigateToDetail(it.id) },
            onSeeMoreClick = { onCategorySelected("donghua") }
        )

        HorizontalSection(
            title = "📚 Komik Populer",
            items = MockData.komik,
            onItemClick = { onNavigateToDetail(it.id) },
            onSeeMoreClick = { onCategorySelected("komik") }
        )
    }
}

@Composable
fun HeroBanner(hero: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = hero.posterUrl,
            contentDescription = hero.title,
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
                            Color(0x88000000),
                            Color(0xFF0A0A0A)
                        ),
                        startY = 300f
                    )
                )
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = hero.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tonton",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
