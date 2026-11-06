package com.eurowindow.cadviewer.ui.main

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eurowindow.cadviewer.NavKey
import com.eurowindow.cadviewer.ViewerNavKey
import com.eurowindow.cadviewer.data.repository.DrawingRepository
import com.eurowindow.cadviewer.saf.SafManager
import com.eurowindow.cadviewer.ui.screens.DrawingsScreen
import com.eurowindow.cadviewer.ui.screens.FavoritesScreen
import com.eurowindow.cadviewer.ui.screens.RecentScreen
import com.eurowindow.cadviewer.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

enum class MainTab(val title: String, val icon: String) {
    RECENT("Gần đây", "🕒"),
    FAVORITES("Yêu thích", "⭐"),
    DRAWINGS("Bản vẽ", "📐"),
    SETTINGS("Cài đặt", "⚙️")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { DrawingRepository(context) }
    val recentDrawings by repository.getRecentDrawings().collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedTab by remember { mutableStateOf(MainTab.RECENT) }

    fun openDrawing(uri: Uri, displayName: String) {
        coroutineScope.launch {
            val safeInfo = if (uri.scheme == "content") {
                SafManager.cacheUriToSandbox(context, uri)
            } else {
                SafManager.queryFileInfo(context, uri)
            }
            repository.recordDrawingOpened(safeInfo.uri)
            onItemClick(ViewerNavKey(safeInfo.uri.toString(), safeInfo.displayName))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Eurowindow CAD Viewer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E1E1E),
                tonalElevation = 8.dp
            ) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(text = tab.icon, fontSize = 18.sp) },
                        label = { Text(text = tab.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF90CAF9),
                            selectedTextColor = Color(0xFF90CAF9),
                            unselectedIconColor = Color(0xFF888888),
                            unselectedTextColor = Color(0xFF888888),
                            indicatorColor = Color(0xFF2C3E50)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212))
        ) {
            when (selectedTab) {
                MainTab.RECENT -> RecentScreen(
                    drawings = recentDrawings,
                    onOpenDrawing = ::openDrawing,
                    onToggleFavorite = { uri, status ->
                        coroutineScope.launch { repository.toggleFavorite(uri, status) }
                    },
                    onRemove = { uri ->
                        coroutineScope.launch { repository.removeFromRecent(uri) }
                    }
                )
                MainTab.FAVORITES -> FavoritesScreen(
                    drawings = recentDrawings,
                    onOpenDrawing = ::openDrawing,
                    onToggleFavorite = { uri, status ->
                        coroutineScope.launch { repository.toggleFavorite(uri, status) }
                    }
                )
                MainTab.DRAWINGS -> DrawingsScreen(
                    onOpenDrawing = ::openDrawing
                )
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
