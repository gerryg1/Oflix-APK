package com.oflix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainDashboard()
        }
    }

    @Composable
    fun MainDashboard() {
        Scaffold(
            bottomBar = { BottomNavBar() },
            containerColor = Color(0xFF141414) // Classic streaming dark theme
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Custom App Bar Header
                HeaderSection()

                // Hero/Featured Content Card
                FeaturedBanner()

                Spacer(modifier = Modifier.height(24.dp))

                // Horizontal Movie Row: Trending Now
                MovieRowSection(
                    title = "Trending Now",
                    items = listOf(
                        MovieMock("Wednesday", "Mystery • Season 1", Color(0xFF2C3E50)),
                        MovieMock("Stranger Things", "Sci-Fi • Season 4", Color(0xFF8E44AD)),
                        MovieMock("All of Us Are Dead", "Thriller • Season 1", Color(0xFF27AE60)),
                        MovieMock("The Crown", "Drama • Season 5", Color(0xFFD35400))
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Horizontal Movie Row: Popular on Oflix
                MovieRowSection(
                    title = "Popular on Oflix",
                    items = listOf(
                        MovieMock("Squid Game", "Thriller • Season 2", Color(0xFFC0392B)),
                        MovieMock("Money Heist", "Crime • Part 5", Color(0xFF7F8C8D)),
                        MovieMock("Black Mirror", "Sci-Fi • Season 6", Color(0xFF2980B9)),
                        MovieMock("Narcos", "Crime • Season 3", Color(0xFF16A085))
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    fun HeaderSection() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "OFLIX",
                color = Color(0xFFE50914), // Red Netflix color
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Row {
                IconButton(onClick = { /* Handle Search */ }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { /* Handle Profile */ }) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = Color.White
                    )
                }
            }
        }
    }

    @Composable
    fun FeaturedBanner() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF3A0007), Color(0xFF141414))
                    )
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "FEATURED MOVIE",
                    color = Color(0xFFE50914),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Avatar: The Way of Water",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Jake Sully lives with his newfound family formed on the extrasolar moon Pandora. Once a familiar threat returns, Jake must work with Neytiri...",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(
                        onClick = { /* Play Video */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Play", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { /* Info */ },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) {
                        Text(text = "My List", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    data class MovieMock(val title: String, val category: String, val color: Color)

    @Composable
    fun MovieRowSection(title: String, items: List<MovieMock>) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { movie ->
                    MovieCard(movie = movie)
                }
            }
        }
    }

    @Composable
    fun MovieCard(movie: MovieMock) {
        Card(
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .width(140.dp)
                .height(200.dp),
            colors = CardDefaults.cardColors(containerColor = movie.color)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = movie.category,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    fun BottomNavBar() {
        NavigationBar(
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color.White
        ) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                label = { Text("Home", color = Color.White) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFE50914),
                    unselectedIconColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = false,
                onClick = {},
                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                label = { Text("Search", color = Color.Gray) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFE50914),
                    unselectedIconColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
            NavigationBarItem(
                selected = false,
                onClick = {},
                icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                label = { Text("Profile", color = Color.Gray) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFE50914),
                    unselectedIconColor = Color.Gray,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
