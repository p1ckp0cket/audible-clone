package com.audibleclone.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.audibleclone.R
import com.audibleclone.ui.discover.DiscoverScreen
import com.audibleclone.ui.library.LibraryScreen
import com.audibleclone.ui.player.PlayerScreen
import com.audibleclone.ui.settings.SettingsScreen
import com.audibleclone.ui.theme.AudibleCloneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudibleCloneTheme {
                MainNavHost()
            }
        }
    }
}

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    val startDestination = "library"
    
    NavHost(navController, startDestination) {
        composable("library") {
            LibraryScreen(onBookClick = { book ->
                navController.navigate("player/$book.id")
            })
        }
        composable(
            route = "player/{bookId}",
            arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.IntType })
        ) { backStackEntry ->
            val bookId = backStackEntry.getInt("bookId")
            PlayerScreen(bookId = bookId)
        }
        composable("discover") {
            DiscoverScreen()
        }
        composable("settings") {
            SettingsScreen()
        }
    }
}

@Composable
fun MainBottomNavBar(navController: androidx.navigation.NavController) {
    val items = listOf(
        NavItem("library", "Library", androidx.compose.material.icons.Icons.Filled.LibraryBooks),
        NavItem("discover", "Discover", androidx.compose.material.icons.Icons.Filled.Explore),
        NavItem("player", "Player", androidx.compose.material.icons.Icons.Filled.PlayCircleFilled),
        NavItem("settings", "Settings", androidx.compose.material.icons.Icons.Filled.Settings),
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ""
    
    NavigationBar {
        items.forEach { item ->
            val isSelected = currentRoute.startsWith(item.route)
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(text = item.label, fontSize = 12.sp) },
                selected = isSelected,
                onClick = { 
                    if (!isSelected) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                selectedContentColor = com.audibleclone.ui.theme.MaterialTheme.colorScheme.onPrimary,
                unselectedContentColor = com.audibleclone.ui.theme.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)