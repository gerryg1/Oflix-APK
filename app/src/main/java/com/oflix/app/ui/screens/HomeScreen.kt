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

import com.oflix.app.ui.components.CategoryTabs
import com.oflix.app.ui.components.HorizontalSection
import com.oflix.app.ui.theme.PrimaryRed

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oflix.app.api.HomeUiState
import com.oflix.app.api.HomeViewModel
import com.oflix.app.ui.components.shimmerEffect

@Composable
fun HomeScreen(
    currentCategory: String,
    onCategorySelected: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val handleCategoryClick: (String) -> Unit = { category ->
        if (category == "donghua" || category == "komik") {
            Toast.makeText(context, "Kategori ini Segera Hadir!", Toast.LENGTH_SHORT).show()
        } else {
            onCategorySelected(category)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                onCategorySelected = handleCategoryClick
            )

            Crossfade(targetState = uiState, label = "home_content") { state ->
                when (state) {
                    is HomeUiState.Loading -> {
                        // Skeleton Layout
                        Column {
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4f)
                                .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            val dummyItems = List(5) { MediaItem(it.toString(), "", "", "") }
                            repeat(4) {
                                HorizontalSection(
                                    title = "Memuat...",
                                    items = dummyItems,
                                    onItemClick = {},
                                    isLoading = true
                                )
                            }
                        }
                    }
                    is HomeUiState.Success -> {
                        Column {
                            val heroItem = state.trending.firstOrNull()
                            if (heroItem != null) {
                                HeroBanner(hero = heroItem, onClick = { onNavigateToDetail(heroItem.id) })
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            HorizontalSection(
                                title = "Trending Now 🔥",
                                items = state.trending,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            HorizontalSection(
                                title = "Indonesian Movies",
                                items = state.indonesianMovies,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            HorizontalSection(
                                title = "Indonesian Drama",
                                items = state.indonesianDrama,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            HorizontalSection(
                                title = "K-Drama Populer",
                                items = state.kdrama,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            HorizontalSection(
                                title = "Anime (Donghua)",
                                items = state.anime,
                                onItemClick = { onNavigateToDetail(it.id) },
                                onSeeMoreClick = { handleCategoryClick("donghua") }
                            )
                            HorizontalSection(
                                title = "Western TV",
                                items = state.westernTv,
                                onItemClick = { onNavigateToDetail(it.id) },
                                onSeeMoreClick = { handleCategoryClick("series") }
                            )
                            HorizontalSection(
                                title = "Short TV",
                                items = state.shortTv,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            HorizontalSection(
                                title = "Horror Pilihan",
                                items = state.horror,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            HorizontalSection(
                                title = "Thailand Drama",
                                items = state.thailandDrama,
                                onItemClick = { onNavigateToDetail(it.id) }
                            )
                            // Komik section - Coming Soon
                            // HorizontalSection for Komik is intentionally omitted as it's still in development
                        }
                    }
                    is HomeUiState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Gagal memuat: ${state.message}", color = Color.Red)
                        }
                    }
                }
            }
        }
        
        // Top Progress Bar — rendered after Column so it's on top
        if (uiState is HomeUiState.Loading) {
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
