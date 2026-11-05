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
                val isDwgOrFormatNotice = errorMessage?.contains("DWG", ignoreCase = true) == true ||
                        errorMessage?.contains("hỗ trợ", ignoreCase = true) == true
                AlertDialog(
                    onDismissRequest = {
                        errorMessage = null
                        onBack()
                    },
                    title = {
                        Text(
                            text = if (isDwgOrFormatNotice) "Thông báo định dạng bản vẽ" else "Lỗi hiển thị CAD",
                            color = if (isDwgOrFormatNotice) Color(0xFFFFA726) else Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.White,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            if (isDwgOrFormatNotice) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "💡 Gợi ý:\n• Trong giai đoạn P0.1, vui lòng sử dụng định dạng DXF (.dxf) để xem 100% offline.\n• Từ AutoCAD/ZWCAD trên máy tính, chọn 'Save As' -> 'AutoCAD 2018 DXF (*.dxf)'.\n• Hoặc quay lại trang chính và mở 3 bản vẽ mẫu Eurowindow có sẵn để thử nghiệm ngay.",
                                    color = Color(0xFF90CAF9),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                errorMessage = null
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text(text = "Đã hiểu", color = Color.White)
                        }
                    }
                )
            }
        }
    }
}
