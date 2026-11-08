package com.eurowindow.cadviewer.ui.screens

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eurowindow.cadviewer.engine.CadLayer
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
    var showLayerDialog by remember { mutableStateOf(false) }
    var layersList by remember { mutableStateOf<List<CadLayer>>(emptyList()) }
    var activeTool by remember { mutableStateOf<String?>(null) }

    val adapter = remember {
        MlightCadAdapter { js ->
            webViewRef?.post { webViewRef?.evaluateJavascript(js, null) }
        }
    }

    fun loadSelectedDrawing() {
        val uri = Uri.parse(uriString)
        isLoadingDrawing = true
        coroutineScope.launch {
            val openResult = if (uri.scheme == "file") {
                val path = uri.path ?: uri.schemeSpecificPart
                val file = java.io.File(path)
                if (file.exists()) {
                    adapter.openDrawingFile(displayName, file)
                } else {
                    val bytesResult = SafManager.readFileBytes(context, uri)
                    if (bytesResult.isSuccess) {
                        adapter.openDrawing(displayName, bytesResult.getOrThrow())
                    } else {
                        Result.failure(bytesResult.exceptionOrNull() ?: Exception("Tệp không tồn tại"))
                    }
                }
            } else {
                val bytesResult = SafManager.readFileBytes(context, uri)
                if (bytesResult.isSuccess) {
                    adapter.openDrawing(displayName, bytesResult.getOrThrow())
                } else {
                    Result.failure(bytesResult.exceptionOrNull() ?: Exception("Không thể đọc tệp"))
                }
            }

            isLoadingDrawing = false
            if (openResult.isSuccess) {
                layersList = adapter.getLayers()
                adapter.zoomToFit()
            } else {
                val exMsg = openResult.exceptionOrNull()?.message ?: "Lỗi khi mở bản vẽ"
                errorMessage = if (exMsg.contains("Permission Denial", ignoreCase = true)) {
                    "Không thể đọc tệp: Quyền truy cập bị hệ điều hành từ chối. Vui lòng mở lại từ mục 'Bản vẽ'."
                } else {
                    exMsg
                }
            }
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
                    Column {
                        Text(
                            text = displayName,
                            maxLines = 1,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Eurowindow CAD Viewer • DWG FastView Mode",
                            fontSize = 11.sp,
                            color = Color(0xFF90CAF9)
                        )
                    }
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

            // DWG FastView Style Bottom Quick Toolbar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(32.dp)),
                color = Color(0xEB212121),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Khoảng cách (Distance)
                    CadToolbarButton(
                        icon = "📏",
                        label = "Đo dài",
                        isSelected = activeTool == "dist",
                        onClick = {
                            activeTool = "dist"
                            adapter.startMeasureDistance()
                        }
                    )

                    // 2. Diện tích (Area)
                    CadToolbarButton(
                        icon = "📐",
                        label = "Diện tích",
                        isSelected = activeTool == "area",
                        onClick = {
                            activeTool = "area"
                            adapter.startMeasureArea()
                        }
                    )

                    // 3. Xóa đo (Clear)
                    CadToolbarButton(
                        icon = "🧹",
                        label = "Xóa đo",
                        isSelected = false,
                        onClick = {
                            activeTool = null
                            adapter.clearMeasurements()
                        }
                    )

                    // 4. Lớp (Layers)
                    CadToolbarButton(
                        icon = "📑",
                        label = "Lớp (${layersList.size})",
                        isSelected = showLayerDialog,
                        onClick = {
                            coroutineScope.launch {
                                layersList = adapter.getLayers()
                                showLayerDialog = true
                            }
                        }
                    )

                    // 5. Fit màn hình (Smart Extents Fit)
                    CadToolbarButton(
                        icon = "⛶",
                        label = "Vừa khung",
                        isSelected = false,
                        onClick = {
                            coroutineScope.launch { adapter.zoomToFit() }
                        }
                    )

                    // 6. Không gian Mô hình (Model Space)
                    CadToolbarButton(
                        icon = "🏗️",
                        label = "Mô hình",
                        isSelected = false,
                        onClick = {
                            adapter.switchToModelSpace()
                        }
                    )
                }
            }

            // Layer Management Dialog
            if (showLayerDialog) {
                AlertDialog(
                    onDismissRequest = { showLayerDialog = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Danh sách Lớp (Layers)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "${layersList.size} lớp",
                                color = Color(0xFF90CAF9),
                                fontSize = 13.sp
                            )
                        }
                    },
                    text = {
                        if (layersList.isEmpty()) {
                            Text(
                                text = "Bản vẽ không có layer hoặc chưa tải xong.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 350.dp)
                            ) {
                                items(layersList) { layer ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    adapter.setLayerVisibility(layer.name, !layer.isVisible)
                                                    layersList = adapter.getLayers()
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(if (layer.isVisible) Color(0xFF4CAF50) else Color(0xFF757575))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = layer.name,
                                            color = if (layer.isVisible) Color.White else Color.Gray,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (layer.isVisible) "👁 Hiện" else "🚫 Ẩn",
                                            color = if (layer.isVisible) Color(0xFF90CAF9) else Color(0xFFEF5350),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    HorizontalDivider(color = Color(0x22FFFFFF))
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showLayerDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text(text = "Đóng", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF262626)
                )
            }

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
                        Text(
                            text = "Đang nạp và giải mã bản vẽ CAD (DWG / DXF)...",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (errorMessage != null) {
                AlertDialog(
                    onDismissRequest = {
                        errorMessage = null
                        onBack()
                    },
                    title = {
                        Text(
                            text = "Thông báo",
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Text(
                            text = (errorMessage ?: "") + "\n\n— Thông tin kỹ thuật —\n" +
                                com.eurowindow.cadviewer.webview.CadDiagnostics.report(context),
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                errorMessage = null
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Text(text = "Đóng", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF262626)
                )
            }
        }
    }
}

@Composable
private fun CadToolbarButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) Color(0xFF1976D2) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isSelected) Color.White else Color(0xFFB0BEC5),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
