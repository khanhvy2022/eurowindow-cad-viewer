package com.eurowindow.cadviewer

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eurowindow.cadviewer.ui.main.MainScreen
import com.eurowindow.cadviewer.ui.screens.ViewerScreen
import kotlinx.coroutines.flow.StateFlow

sealed interface CadScreen {
    object Main : CadScreen
    data class Viewer(val uriString: String, val displayName: String) : CadScreen
}

@Composable
fun MainNavigation(
    incomingDrawingFlow: StateFlow<IncomingDrawingRequest?>
) {
    val incomingDrawing by incomingDrawingFlow.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf<CadScreen>(CadScreen.Main) }

    LaunchedEffect(incomingDrawing) {
        val req = incomingDrawing
        if (req != null) {
            currentScreen = CadScreen.Viewer(req.uriString, req.displayName)
        }
    }

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
