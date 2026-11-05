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
import java.text.SimpleDateFormat
import java.util.*

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Chưa có bản vẽ nào mở gần đây",
                    color = Color.Gray,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hãy chọn thẻ 'Bản vẽ' để mở tệp DXF/DWG từ thiết bị",
                    color = Color(0xFF666666),
                    fontSize = 12.sp
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
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            SafManager.takePersistablePermission(context, uri)
            val info = SafManager.queryFileInfo(context, uri)
            onOpenDrawing(uri, info.displayName)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Bộ sưu tập bản vẽ Eurowindow",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hỗ trợ định dạng DXF và DWG qua Storage Access Framework",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { launcher.launch(SafManager.createOpenDocumentIntent()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(50.dp)
            ) {
                Text(text = "📂 Chọn bản vẽ từ bộ nhớ máy", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                Text(text = "Phiên bản: P0.1 (Kiểm thử cục bộ)", color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Đơn vị đo mặc định: Milimét (mm)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Động cơ hiển thị: MLightCAD WebGL (100% Offline)", color = Color(0xFFCCCCCC), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Trạng thái DWG Parser: Khóa theo chính sách P0.1 (Yêu cầu cấp phép thương mại)", color = Color(0xFFFFA726), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Giấy phép bản phát hành: MIT / Apache-2.0 / 0BSD (Clean License)", color = Color(0xFF81C784), fontSize = 13.sp)
            }
        }
    }
}
