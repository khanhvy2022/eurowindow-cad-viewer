package com.eurowindow.cadviewer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.eurowindow.cadviewer.saf.SafManager
import com.eurowindow.cadviewer.ui.theme.EurowindowCADViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IncomingDrawingRequest(
    val uriString: String,
    val displayName: String
)

class MainActivity : ComponentActivity() {

    private val incomingDrawingFlow = MutableStateFlow<IncomingDrawingRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            EurowindowCADViewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        incomingDrawingFlow = incomingDrawingFlow
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val targetUri = extractUri(intent) ?: return

        SafManager.takePersistablePermission(this, targetUri)
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                SafManager.cacheUriToSandbox(this@MainActivity, targetUri)
            }
            incomingDrawingFlow.value = IncomingDrawingRequest(
                uriString = info.uri.toString(),
                displayName = info.displayName
            )
        }
    }

    private fun extractUri(intent: Intent): Uri? {
        val action = intent.action ?: return intent.data
        return when (action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.data
            }
            else -> intent.data
        }
    }
}
