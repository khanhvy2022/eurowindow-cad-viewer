package com.eurowindow.cadviewer

import androidx.compose.runtime.*
import com.eurowindow.cadviewer.ui.main.MainScreen
import com.eurowindow.cadviewer.ui.screens.ViewerScreen

sealed interface CadScreen {
    object Main : CadScreen
    data class Viewer(val uriString: String, val displayName: String) : CadScreen
}

@Composable
fun MainNavigation() {
    var currentScreen by remember { mutableStateOf<CadScreen>(CadScreen.Main) }

    when (val screen = currentScreen) {
        is CadScreen.Main -> {
            MainScreen(
                onItemClick = { navKey ->
                    if (navKey is ViewerNavKey) {
                        currentScreen = CadScreen.Viewer(navKey.uriString, navKey.displayName)
                    }
                }
            )
        }
        is CadScreen.Viewer -> {
            ViewerScreen(
                uriString = screen.uriString,
                displayName = screen.displayName,
                onBack = { currentScreen = CadScreen.Main }
            )
        }
    }
}
