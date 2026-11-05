package com.eurowindow.cadviewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.eurowindow.cadviewer.saf.SafManager
import com.eurowindow.cadviewer.ui.theme.EurowindowCADViewerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var initialUri: String? = null
        var initialDisplayName: String? = null

        val uri = intent?.data
        if (uri != null && (intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_EDIT)) {
            SafManager.takePersistablePermission(this, uri)
            val info = SafManager.queryFileInfo(this, uri)
            initialUri = uri.toString()
            initialDisplayName = info.displayName
        }

        setContent {
            EurowindowCADViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        initialUri = initialUri,
                        initialDisplayName = initialDisplayName
                    )
                }
            }
        }
    }
}
