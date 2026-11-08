package com.eurowindow.cadviewer.ui.screens

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eurowindow.cadviewer.engine.CadLayer
import com.eurowindow.cadviewer.engine.MlightCadAdapter
import com.eurowindow.cadviewer.saf.SafManager
import com.eurowindow.cadviewer.webview.configureCadWebView
import kotlinx.coroutines.launch

private data class CadToolItem(
    val id: String,
    val icon: String,
    val label: String,
    val command: String,
    val clearsActiveTool: Boolean = false
)

private data class CadColorItem(
    val aci: Int,
    val name: String,
    val color: Color
)

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
    var layerSearchQuery by remember { mutableStateOf("") }
    var layersList by remember { mutableStateOf<List<CadLayer>>(emptyList()) }

    // DWG FastView active bottom category ("draw", "modify", "measure", "dimension", "color", or null)
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var activeToolLabel by remember { mutableStateOf<String?>(null) }
    var selectedAciColor by remember { mutableStateOf(2) } // Default Yellow (ACI 2)

    // OSNAP bitmask states (Pe enum in MLightCAD: EndPoint=1, MidPoint=2, Center=3->4, Quadrant=5->16, Intersection=6->32, Perpendicular=8->128, Nearest=10->512)
    var snapEndpoint by remember { mutableStateOf(true) }
    var snapMidpoint by remember { mutableStateOf(true) }
    var snapCenter by remember { mutableStateOf(true) }
    var snapIntersection by remember { mutableStateOf(true) }
    var snapPerpendicular by remember { mutableStateOf(true) }
    var snapQuadrant by remember { mutableStateOf(true) }
    var snapNearest by remember { mutableStateOf(true) }
    var orthoEnabled by remember { mutableStateOf(false) }

    val adapter = remember {
        MlightCadAdapter { js ->
            webViewRef?.post { webViewRef?.evaluateJavascript(js, null) }
        }
    }

    fun syncOsnapToEngine() {
        var mask = 0
        if (snapEndpoint) mask = mask or (1 shl 0)       // Pe.EndPoint = 1
        if (snapMidpoint) mask = mask or (1 shl 1)       // Pe.MidPoint = 2
        if (snapCenter) mask = mask or (1 shl 2)         // Pe.Center = 3
        if (snapQuadrant) mask = mask or (1 shl 4)       // Pe.Quadrant = 5
        if (snapIntersection) mask = mask or (1 shl 5)   // Pe.Intersection = 6
        if (snapPerpendicular) mask = mask or (1 shl 7)  // Pe.Perpendicular = 8
        if (snapNearest) mask = mask or (1 shl 9)        // Pe.Nearest = 10
        adapter.setOsnapModes(mask)
    }

    fun runCadTool(item: CadToolItem) {
        activeCategory = null
        if (item.clearsActiveTool) {
            activeToolLabel = null
        } else {
            activeToolLabel = item.label
        }
        syncOsnapToEngine()
        adapter.executeCommand(item.command)
    }

    // 1. Vẽ (Draw) — 13 tools matching DWG FastView Screenshot 1
    val drawTools = remember {
        listOf(
            CadToolItem("pline", "〰️", "Đa tuyến", "pline"),
            CadToolItem("line", "╱", "Dòng", "line"),
            CadToolItem("text", "𝐀", "Văn bản", "markuptext"),
            CadToolItem("circle", "◯", "Vòng tròn", "circle"),
            CadToolItem("arc", "⌒", "Vòng cung", "arc"),
            CadToolItem("rectang", "▭", "Hình chữ nhật", "rectang"),
            CadToolItem("ellipse", "⬭", "Hình elip", "ellipse"),
            CadToolItem("sketch", "✍️", "Phác thảo", "sketch"),
            CadToolItem("spline", "🖌️", "Bút thông minh", "spline"),
            CadToolItem("mleader", "💬", "Multileader", "markupcallout"),
            CadToolItem("revcloud", "☁️", "Tiết lộ", "revcloud"),
            CadToolItem("point", "➗", "Chia", "point"),
            CadToolItem("hatch", "▦", "Mặt cắt (Nở)", "-hatch")
        )
    }

    // 2. Chỉnh sửa (Modify) — Tools matching DWG FastView Screenshot 2 + Move/Copy/Rotate
    val modifyTools = remember {
        listOf(
            CadToolItem("trim_erase", "✂️", "Cắt / Xóa", "erase"),
            CadToolItem("extend_ray", "↗️", "Mở rộng", "ray"),
            CadToolItem("offset", "⧉", "Bù lại", "offset"),
            CadToolItem("fillet_arc", "◜", "Phi lê", "arc"),
            CadToolItem("chamfer_line", "◿", "Gọt cạnh xiên", "line"),
            CadToolItem("move", "✥", "Di chuyển", "move"),
            CadToolItem("copy", "❐", "Sao chép", "copy"),
            CadToolItem("rotate", "↻", "Xoay", "rotate"),
            CadToolItem("layiso", "🎯", "Cô lập lớp", "layiso"),
            CadToolItem("layuniso", "🔓", "Bỏ cô lập", "layuniso", clearsActiveTool = true),
            CadToolItem("undo", "↩️", "Hoàn tác", "undo", clearsActiveTool = true),
            CadToolItem("redo", "↪️", "Làm lại", "redo", clearsActiveTool = true)
        )
    }

    // 4. Đo lường (Measure) — Precision Auto-OSNAP Measurement Suite
    val measureTools = remember {
        listOf(
            CadToolItem("mdist", "📏", "Khoảng cách", "measuredistance"),
            CadToolItem("mcont", "⛓️", "Đo liên tiếp", "measurecontinuous"),
            CadToolItem("marea", "📐", "Diện tích", "measurearea"),
            CadToolItem("mangle", "∠", "Đo góc", "measureangle"),
            CadToolItem("marc", "⌒", "Dài cung", "measurearc"),
            CadToolItem("mpoint", "📍", "Tọa độ (X,Y)", "measurepoint"),
            CadToolItem("mclear", "🧹", "Xóa đo", "clearmeasurements", clearsActiveTool = true)
        )
    }

    // 5. Kích thước (Dimension) — 7 tools matching DWG FastView Screenshot 3
    val dimensionTools = remember {
        listOf(
            CadToolItem("dimaligned", "⤡", "Căn chỉnh", "dimaligned"),
            CadToolItem("dimlinear", "↦", "Tuyến tính", "dimlinear"),
            CadToolItem("dimangular", "∡", "Góc cạnh", "dimangular"),
            CadToolItem("dimradius", "◴", "Bán kính", "dimradius"),
            CadToolItem("dimdiameter", "⌀", "Đường kính", "dimdiameter"),
            CadToolItem("dimarc", "↷", "Chiều dài vòng cung", "dimarc"),
            CadToolItem("dimordinate", "⋕", "Cung ba điểm / Tọa độ", "dimordinate")
        )
    }

    // 6. Màu sắc (Color Palette)
    val colorItems = remember {
        listOf(
            CadColorItem(1, "Đỏ", Color(0xFFFF3B30)),
            CadColorItem(2, "Vàng", Color(0xFFFFCC00)),
            CadColorItem(3, "Xanh lá", Color(0xFF34C759)),
            CadColorItem(4, "Xanh lơ", Color(0xFF00E5FF)),
            CadColorItem(5, "Xanh lam", Color(0xFF2979FF)),
            CadColorItem(6, "Tím hồng", Color(0xFFFF2D55)),
            CadColorItem(7, "Trắng", Color(0xFFFFFFFF)),
            CadColorItem(30, "Cam", Color(0xFFFF9500))
        )
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
                syncOsnapToEngine()
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
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Full HD • Tự động bắt điểm OSNAP",
                            fontSize = 10.sp,
                            color = Color(0xFF26C6DA)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(text = "←", color = Color.White, fontSize = 20.sp)
                    }
                },
                actions = {
                    // Undo
                    IconButton(onClick = { adapter.executeCommand("undo") }) {
                        Text(text = "↩️", fontSize = 16.sp)
                    }
                    // Redo
                    IconButton(onClick = { adapter.executeCommand("redo") }) {
                        Text(text = "↪️", fontSize = 16.sp)
                    }
                    // Fit to Screen / Toggle Sheet vs Full Overview
                    IconButton(onClick = { coroutineScope.launch { adapter.zoomToFit() } }) {
                        Text(text = "🔍", fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1B1C1E))
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
                        wv.layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        wv.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val wPx = right - left
                            val hPx = bottom - top
                            if (wPx > 0 && hPx > 0 && (wPx != oldRight - oldLeft || hPx != oldBottom - oldTop)) {
                                val density = v.resources.displayMetrics.density.coerceAtLeast(1f)
                                val wCss = (wPx / density).toInt()
                                val hCss = (hPx / density).toInt()
                                wv.post {
                                    wv.evaluateJavascript(
                                        "if (window.EurowindowBridge && window.EurowindowBridge.onHostResize) { window.EurowindowBridge.onHostResize($wCss, $hCss); }",
                                        null
                                    )
                                }
                            }
                        }
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

            // Active Tool Floating Control Pill at Top of Canvas
            if (activeToolLabel != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFF26C6DA), RoundedCornerShape(24.dp)),
                    color = Color(0xE622252A),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🎯 $activeToolLabel (Chạm & di để bắt điểm)",
                            color = Color(0xFF80DEEA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    adapter.confirmCommand()
                                },
                            color = Color(0xFF00ACC1)
                        ) {
                            Text(
                                text = "✓ Xong",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    adapter.cancelCommand()
                                    activeToolLabel = null
                                },
                            color = Color(0xFF455A64)
                        ) {
                            Text(
                                text = "✕ Thoát",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // DWG FastView Floating Popover Panel + 6-Tab Bottom Dock Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Floating Cyan-Bordered Popover Card (matches DWG FastView screenshots 1, 2, 3)
                if (activeCategory != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, Color(0xFF26C6DA), RoundedCornerShape(18.dp)),
                        color = Color(0xF535383B),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            when (activeCategory) {
                                "draw" -> {
                                    DwgFastViewToolGrid(
                                        tools = drawTools,
                                        columns = 6,
                                        onSelectTool = { runCadTool(it) }
                                    )
                                }
                                "modify" -> {
                                    DwgFastViewToolGrid(
                                        tools = modifyTools,
                                        columns = 6,
                                        onSelectTool = { runCadTool(it) }
                                    )
                                }
                                "measure" -> {
                                    DwgFastViewToolGrid(
                                        tools = measureTools,
                                        columns = 4,
                                        onSelectTool = { runCadTool(it) }
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Color(0x33FFFFFF))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "🎯 Chế độ Tự động bắt điểm chính xác (Auto OSNAP):",
                                        color = Color(0xFF80DEEA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OsnapFilterChip("□ Điểm cuối", snapEndpoint) {
                                            snapEndpoint = !snapEndpoint
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("△ Trung điểm", snapMidpoint) {
                                            snapMidpoint = !snapMidpoint
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("○ Tâm", snapCenter) {
                                            snapCenter = !snapCenter
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("✕ Giao điểm", snapIntersection) {
                                            snapIntersection = !snapIntersection
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("⊥ Vuông góc", snapPerpendicular) {
                                            snapPerpendicular = !snapPerpendicular
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("◇ Góc 1/4", snapQuadrant) {
                                            snapQuadrant = !snapQuadrant
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("× Gần nhất", snapNearest) {
                                            snapNearest = !snapNearest
                                            syncOsnapToEngine()
                                        }
                                        OsnapFilterChip("📐 Trực giao 90°", orthoEnabled) {
                                            orthoEnabled = !orthoEnabled
                                            adapter.setOrthoMode(orthoEnabled)
                                        }
                                    }
                                }
                                "dimension" -> {
                                    DwgFastViewToolGrid(
                                        tools = dimensionTools,
                                        columns = 6,
                                        onSelectTool = { runCadTool(it) }
                                    )
                                }
                                "color" -> {
                                    Text(
                                        text = "Chọn màu nét vẽ, kích thước & đo lường:",
                                        color = Color(0xFFE0E0E0),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        colorItems.forEach { item ->
                                            val isSelected = selectedAciColor == item.aci
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable {
                                                    selectedAciColor = item.aci
                                                    adapter.setActiveColor(item.aci)
                                                    activeCategory = null
                                                }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(item.color)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) Color(0xFF00E5FF) else Color(0x66FFFFFF),
                                                            shape = CircleShape
                                                        )
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = item.name,
                                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFCCCCCC),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0x33FFFFFF))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        QuickActionChip("🌓 Đổi nền Đen/Trắng") {
                                            activeCategory = null
                                            adapter.executeCommand("switchbg")
                                        }
                                        QuickActionChip("📖 Chế độ Đọc") {
                                            activeCategory = null
                                            adapter.executeCommand("readingmode")
                                        }
                                        QuickActionChip("🏗️ Model / Layout") {
                                            activeCategory = null
                                            adapter.switchToModelSpace()
                                        }
                                        QuickActionChip("🧹 Xóa ghi chú") {
                                            activeCategory = null
                                            adapter.executeCommand("clearmarkups")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom 6-Tab Dock Bar (Vẽ | Chỉnh sửa | Lớp | Đo lường | Kích thước | Màu sắc)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1B1C1E),
                    tonalElevation = 10.dp,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DwgDockTab(
                            icon = "✏️",
                            label = "Vẽ",
                            isSelected = activeCategory == "draw",
                            onClick = {
                                activeCategory = if (activeCategory == "draw") null else "draw"
                            }
                        )
                        DwgDockTab(
                            icon = "📝",
                            label = "Chỉnh sửa",
                            isSelected = activeCategory == "modify",
                            onClick = {
                                activeCategory = if (activeCategory == "modify") null else "modify"
                            }
                        )
                        DwgDockTab(
                            icon = "📚",
                            label = "Lớp (${layersList.size})",
                            isSelected = showLayerDialog,
                            onClick = {
                                activeCategory = null
                                coroutineScope.launch {
                                    layersList = adapter.getLayers()
                                    showLayerDialog = true
                                }
                            }
                        )
                        DwgDockTab(
                            icon = "📏",
                            label = "Đo lường",
                            isSelected = activeCategory == "measure",
                            onClick = {
                                activeCategory = if (activeCategory == "measure") null else "measure"
                            }
                        )
                        DwgDockTab(
                            icon = "📐",
                            label = "Kích thước",
                            isSelected = activeCategory == "dimension",
                            onClick = {
                                activeCategory = if (activeCategory == "dimension") null else "dimension"
                            }
                        )
                        DwgDockTab(
                            icon = "🎨",
                            label = "Màu sắc",
                            isSelected = activeCategory == "color",
                            onClick = {
                                activeCategory = if (activeCategory == "color") null else "color"
                            }
                        )
                    }
                }
            }

            // Enhanced Layer Management Dialog
            if (showLayerDialog) {
                val filteredLayers = remember(layersList, layerSearchQuery) {
                    if (layerSearchQuery.isBlank()) layersList
                    else layersList.filter { it.name.contains(layerSearchQuery.trim(), ignoreCase = true) }
                }

                AlertDialog(
                    onDismissRequest = { showLayerDialog = false },
                    title = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Quản lý Lớp (Layers)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "${layersList.count { it.isVisible }}/${layersList.size} hiện",
                                    color = Color(0xFF26C6DA),
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickActionChip("👁 Hiện tất cả") {
                                    layersList = adapter.showAllLayers()
                                }
                                QuickActionChip("🎯 Cô lập lớp") {
                                    showLayerDialog = false
                                    activeToolLabel = "Cô lập lớp"
                                    adapter.executeCommand("layiso")
                                }
                                QuickActionChip("🔓 Bỏ cô lập") {
                                    showLayerDialog = false
                                    adapter.executeCommand("layuniso")
                                }
                                QuickActionChip("🚫 Tắt lớp chọn") {
                                    showLayerDialog = false
                                    activeToolLabel = "Tắt lớp chọn"
                                    adapter.executeCommand("layoff")
                                }
                            }
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = layerSearchQuery,
                                onValueChange = { layerSearchQuery = it },
                                placeholder = { Text("Tìm tên lớp (VD: Tpl_Bound, Dim, Text...)", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF26C6DA),
                                    unfocusedBorderColor = Color(0xFF555555)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (filteredLayers.isEmpty()) {
                                Text(
                                    text = "Không tìm thấy lớp phù hợp.",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 320.dp)
                                ) {
                                    items(filteredLayers) { layer ->
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
                                                    .background(if (layer.isVisible) Color(0xFF00E676) else Color(0xFF757575))
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
                                                color = if (layer.isVisible) Color(0xFF26C6DA) else Color(0xFFEF5350),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        HorizontalDivider(color = Color(0x22FFFFFF))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showLayerDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                        ) {
                            Text(text = "Đóng", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF262626)
                )
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
private fun DwgFastViewToolGrid(
    tools: List<CadToolItem>,
    columns: Int,
    onSelectTool: (CadToolItem) -> Unit
) {
    val rows = remember(tools, columns) { tools.chunked(columns) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectTool(item) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF282B2E))
                                .border(1.dp, Color(0x4426C6DA), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.icon,
                                fontSize = 18.sp,
                                color = Color(0xFF80DEEA)
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = item.label,
                            color = Color(0xFFEEEEEE),
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val emptySlots = columns - rowItems.size
                repeat(emptySlots) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DwgDockTab(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF26C6DA))
            )
            Spacer(modifier = Modifier.height(3.dp))
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            text = icon,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isSelected) Color(0xFF26C6DA) else Color(0xFFE0E0E0),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun OsnapFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF00E5FF) else Color(0xFF555555),
                shape = RoundedCornerShape(16.dp)
            ),
        color = if (selected) Color(0x3300E5FF) else Color(0xFF25282B)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF80DEEA) else Color(0xFF9E9E9E),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Color(0x5526C6DA), RoundedCornerShape(16.dp)),
        color = Color(0xFF25282B)
    ) {
        Text(
            text = label,
            color = Color(0xFFE0E0E0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
