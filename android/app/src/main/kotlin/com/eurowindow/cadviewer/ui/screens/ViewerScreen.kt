package com.eurowindow.cadviewer.ui.screens

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eurowindow.cadviewer.engine.CadEngineException
import com.eurowindow.cadviewer.engine.MlightCadAdapter
import com.eurowindow.cadviewer.saf.SafManager
import com.eurowindow.cadviewer.webview.configureCadWebView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    uriString: String,
    displayName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isViewerReady by remember { mutableStateOf(false) }
    var isLoadingDrawing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val adapter = remember {
        MlightCadAdapter { js ->
            webViewRef?.post { webViewRef?.evaluateJavascript(js, null) }
        }
    }

    fun loadSelectedDrawing() {
        val uri = Uri.parse(uriString)
        isLoadingDrawing = true
        coroutineScope.launch {
            val bytesResult = SafManager.readFileBytes(context, uri)
            bytesResult.fold(
                onSuccess = { bytes ->
                    val openResult = adapter.openDrawing(displayName, bytes)
                    isLoadingDrawing = false
                    openResult.onFailure { err ->
                        errorMessage = err.message ?: "Lỗi khi mở bản vẽ"
                    }
                },
                onFailure = { err ->
                    isLoadingDrawing = false
                    errorMessage = "Không thể đọc tệp: ${err.message}"
                }
            )
        }
    }

    LaunchedEffect(isViewerReady) {
        if (isViewerReady) {
            loadSelectedDrawing()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", color = Color.White, fontSize = 22.sp)
                    }
                },
                actions = {
                    // Zoom to fit button
                    IconButton(onClick = { coroutineScope.launch { adapter.zoomToFit() } }) {
                        Text(text = "⛶", color = Color(0xFF90CAF9), fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).also { wv ->
                        webViewRef = wv
                        configureCadWebView(
                            webView = wv,
                            adapter = adapter,
                            onReady = { isViewerReady = true },
                            onError = { err -> errorMessage = err },
                            onCrash = { errorMessage = "Tiến trình render CAD bị dừng do quá tải." }
                        )
                    }
                }
            )

            if (isLoadingDrawing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF90CAF9))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "Đang nạp dữ liệu hình học CAD...", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            if (errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { errorMessage = null },
                    title = { Text(text = "Thông báo CAD") },
                    text = { Text(text = errorMessage ?: "") },
                    confirmButton = {
                        TextButton(onClick = {
                            errorMessage = null
                            onBack()
                        }) {
                            Text("Đóng")
                        }
                    }
                )
            }
        }
    }
}
