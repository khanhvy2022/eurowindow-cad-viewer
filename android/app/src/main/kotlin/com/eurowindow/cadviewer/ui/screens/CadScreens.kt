package com.eurowindow.cadviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eurowindow.cadviewer.data.entity.DrawingMetadata
import com.eurowindow.cadviewer.saf.SafManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class SampleDrawingItem(
    val assetPath: String,
    val title: String,
    val fileName: String,
    val specs: String,
    val description: String,
    val badgeColor: Color = Color(0xFF1565C0)
)

val SAMPLE_DRAWINGS = listOf(
    SampleDrawingItem(
        assetPath = "sample-drawings/sample-door.dwg",
        title = "Cửa đi nhôm kính Eurowindow (DWG)",
        fileName = "sample-door.dwg",
        specs = "Chuẩn AutoCAD DWG • 35 KB",
        description = "Bản vẽ định dạng DWG kiểm tra bộ giải mã LibreDWG WASM",
        badgeColor = Color(0xFFE65100)
    ),
    SampleDrawingItem(
        assetPath = "sample-drawings/sheet_0.dxf",
        title = "Tấm cắt kỹ thuật Eurowindow",
        fileName = "sheet_0.dxf",
        specs = "11.858 chi tiết • 4 Layer • 2.2 MB",
        description = "Bản vẽ kết cấu nhôm kính phức tạp, kiểm tra WebGL 2.0"
    ),
    SampleDrawingItem(
        assetPath = "sample-drawings/block-color.dxf",
        title = "Khối chi tiết màu sắc nhôm kính",
        fileName = "block-color.dxf",
        specs = "6 Layer màu • 621 KB",
        description = "Bản vẽ kiểm tra bảng màu RGB/Index và hiển thị lớp CAD"
    ),
    SampleDrawingItem(
        assetPath = "sample-drawings/find-blocks.dxf",
        title = "Khung tên bản vẽ tiêu chuẩn",
        fileName = "find-blocks.dxf",
        specs = "Khung chuẩn • 4 Layer • 7.4 KB",
        description = "Khung tên bản vẽ và khối thuộc tính kỹ thuật Eurowindow"
    )
)

@Composable
fun DrawingCard(
    drawing: DrawingMetadata,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onOpen() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252526))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CAD Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (drawing.extension == "dwg") Color(0xFFE65100) else Color(0xFF1565C0),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = drawing.extension.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drawing.displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(Date(drawing.lastOpenedTimestamp))
                val sizeKb = (drawing.fileSize / 1024).coerceAtLeast(1)
                Text(
                    text = "$sizeKb KB • $dateStr",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Text(
                    text = if (drawing.isFavorite) "★" else "☆",
                    color = if (drawing.isFavorite) Color(0xFFFFD700) else Color.Gray,
                    fontSize = 20.sp
                )
            }

            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Text(text = "✕", color = Color(0xFF888888), fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun RecentScreen(
    drawings: List<DrawingMetadata>,
    onOpenDrawing: (Uri, String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    if (drawings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Chưa có bản vẽ nào mở gần đây",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hãy chọn thẻ 'Bản vẽ' bên dưới để mở bản vẽ mẫu có sẵn hoặc chọn tệp từ máy",
                    color = Color(0xFF888888),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(drawings, key = { it.uriString }) { drawing ->
                DrawingCard(
                    drawing = drawing,
                    onOpen = { onOpenDrawing(Uri.parse(drawing.uriString), drawing.displayName) },
                    onToggleFavorite = { onToggleFavorite(drawing.uriString, drawing.isFavorite) },
                    onRemove = { onRemove(drawing.uriString) }
                )
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    drawings: List<DrawingMetadata>,
    onOpenDrawing: (Uri, String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit
) {
    val favorites = drawings.filter { it.isFavorite }
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Chưa có bản vẽ yêu thích\nNhấn biểu tượng ★ để ghim bản vẽ",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(favorites, key = { it.uriString }) { drawing ->
                DrawingCard(
                    drawing = drawing,
                    onOpen = { onOpenDrawing(Uri.parse(drawing.uriString), drawing.displayName) },
                    onToggleFavorite = { onToggleFavorite(drawing.uriString, drawing.isFavorite) }
                )
            }
        }
    }
}

@Composable
fun DrawingsScreen(
    onOpenDrawing: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            SafManager.takePersistablePermission(context, uri)
            scope.launch {
                val info = withContext(Dispatchers.IO) {
                    SafManager.cacheUriToSandbox(context, uri)
                }
                onOpenDrawing(info.uri, info.displayName)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            // Header
            Text(
                text = "Bộ sưu tập bản vẽ CAD",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hỗ trợ 100% Offline: AutoCAD DWG (.dwg) và DXF (.dxf)",
                color = Color(0xFF90CAF9),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Main Open File Button Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2638)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mở tệp từ thiết bị",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chọn tệp .dwg / .dxf từ Bộ nhớ trong, Thư mục Tải về hoặc Zalo",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { launcher.launch(SafManager.createOpenDocumentIntent()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "📂 Chọn tệp từ thiết bị",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sample Drawings Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bản vẽ mẫu Eurowindow",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color(0xFF2E7D32),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Thử ngay",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nhấn để xem ngay bản vẽ kỹ thuật không cần chọn tệp ngoài:",
                color = Color(0xFF888888),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(SAMPLE_DRAWINGS) { sample ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable {
                        val uri = Uri.parse("asset://${sample.assetPath}")
                        onOpenDrawing(uri, sample.fileName)
                    },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF252526))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(sample.badgeColor, shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DXF",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sample.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sample.specs,
                            color = Color(0xFF90CAF9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sample.description,
                            color = Color(0xFF999999),
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }

                    Text(
                        text = "▶",
                        color = Color(0xFF90CAF9),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ℹ️ Hướng dẫn định dạng tệp CAD",
                        color = Color(0xFFFFA726),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Hỗ trợ xem 100% Offline cả 2 định dạng AutoCAD DWG (.dwg) & DXF (.dxf).\n• Tích hợp thanh công cụ đo đạc khoảng cách, diện tích và quản lý lớp layer tương tự DWG FastView.",
                        color = Color(0xFFB0BEC5),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Cài đặt & Pháp lý",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF252526)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Eurowindow CAD Viewer", color = Color.White, fontWeight = FontWeight.Bold)
                Text(text = "Phiên bản: v1.0.15 (Zoom Mượt Mà • Bắt Điểm Auto-OSNAP)", color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Đơn vị đo mặc định: Milimét (mm)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Động cơ hiển thị: MLightCAD WebGL 2.0 (100% Offline)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Trạng thái DWG Parser: Đã kích hoạt (Hỗ trợ DWG & DXF 2D/3D)", color = Color(0xFF81C784), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Giấy phép bản phát hành: MIT / Apache-2.0 / 0BSD (Clean License)", color = Color(0xFF81C784), fontSize = 13.sp)
            }
        }
    }
}
