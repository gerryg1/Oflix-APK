package com.oflix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oflix.app.ui.components.BottomNav
import com.oflix.app.ui.screens.DetailScreen
import com.oflix.app.ui.screens.HomeScreen
import com.oflix.app.ui.screens.RankingScreen
import com.oflix.app.ui.theme.BackgroundDark
import com.oflix.app.ui.theme.OflixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OflixTheme {
                OflixApp()
            }
        }
    }
}

@Composable
fun OflixApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"

    // Only show BottomNav on main tabs
    val showBottomNav = currentRoute in listOf("home", "search", "profile")

    var currentCategory by remember { mutableStateOf("home") }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                BottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = BackgroundDark
        ) {
            NavHost(
                navController = navController,
                startDestination = "home"
            ) {
                composable("home") {
                    HomeScreen(
                        currentCategory = currentCategory,
                        onCategorySelected = { category ->
                            currentCategory = category
                        },
                        onNavigateToDetail = { mediaId ->
                            navController.navigate("detail/${java.net.URLEncoder.encode(mediaId, "UTF-8")}")
                        },
                        onNavigateToRanking = { rankingId, title ->
                            navController.navigate("ranking/$rankingId?title=${java.net.URLEncoder.encode(title, "UTF-8")}")
                        }
                    )
                }
                
                composable("search") {
                    // SearchScreen placeholder
                }
                
                composable("profile") {
                    // ProfileScreen placeholder
                }
                
                composable("detail/{mediaId}") { backStackEntry ->
                    val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                    DetailScreen(
                        mediaId = mediaId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("ranking/{rankingId}?title={title}") { backStackEntry ->
                    val rankingId = backStackEntry.arguments?.getString("rankingId") ?: ""
                    val title = backStackEntry.arguments?.getString("title") ?: "Peringkat"
                    RankingScreen(
                        rankingId = rankingId,
                        rankingTitle = title,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToDetail = { mediaId ->
                            navController.navigate("detail/${java.net.URLEncoder.encode(mediaId, "UTF-8")}")
                        }
                    )
                }
            }
        }
    }
}
