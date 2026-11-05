package com.eurowindow.cadviewer.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class SafFileInfo(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val extension: String
)

object SafManager {

    val SUPPORTED_MIME_TYPES = arrayOf(
        "application/dxf",
        "image/vnd.dxf",
        "model/vnd.dwg",
        "image/vnd.dwg",
        "application/acad",
        "application/x-acad",
        "application/autocad_dwg",
        "application/dwg",
        "application/octet-stream",
        "*/*"
    )

    fun createOpenDocumentIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIME_TYPES)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    fun takePersistablePermission(context: Context, uri: Uri) {
        if (uri.scheme == "asset") return
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // Some providers do not support persistable permissions
        }
    }

    fun getAssetPath(uri: Uri): String {
        val s = uri.toString()
        return s.removePrefix("asset://").removePrefix("asset:/").removePrefix("/")
    }

    private fun detectFormatFromBytes(context: Context, uri: Uri): String? {
        return try {
            val stream: InputStream? = if (uri.scheme == "asset") {
                val assetPath = getAssetPath(uri)
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val header = ByteArray(64)
                val read = input.read(header)
                if (read >= 6) {
                    val headerStr = String(header, 0, read, Charsets.US_ASCII)
                    when {
                        headerStr.startsWith("AC10") -> "dwg"
                        headerStr.contains("SECTION", ignoreCase = true) ||
                        headerStr.trimStart().startsWith("0") ||
                        headerStr.contains("AutoCAD", ignoreCase = true) -> "dxf"
                        else -> null
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun queryFileInfo(context: Context, uri: Uri): SafFileInfo {
        if (uri.scheme == "asset") {
            val assetPath = getAssetPath(uri)
            val name = assetPath.substringAfterLast('/')
            val ext = name.substringAfterLast('.', "").lowercase()
            val size = try {
                context.assets.open(assetPath).use { it.available().toLong() }
            } catch (e: Exception) {
                0L
            }
            return SafFileInfo(
                uri = uri,
                displayName = name,
                size = size,
                extension = ext
            )
        }

        var name = "unknown_drawing"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // ContentProvider query failed, fallback to lastPathSegment
        }

        if (name == "unknown_drawing" && uri.lastPathSegment != null) {
            name = uri.lastPathSegment ?: name
        }

        var extension = name.substringAfterLast('.', "").lowercase()

        // If extension is missing or whole name has no dot, infer from MIME or magic bytes
        if (extension.isEmpty() || extension == name.lowercase()) {
            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            val inferredExt = when {
                mimeType?.contains("dxf", ignoreCase = true) == true -> "dxf"
                mimeType?.contains("dwg", ignoreCase = true) == true -> "dwg"
                else -> detectFormatFromBytes(context, uri)
            }
            if (inferredExt != null) {
                extension = inferredExt
                if (!name.endsWith(".$extension", ignoreCase = true)) {
                    name = "$name.$extension"
                }
            }
        }

        return SafFileInfo(
            uri = uri,
            displayName = name,
            size = size,
            extension = extension
        )
    }

    suspend fun readFileBytes(
        context: Context,
        uri: Uri,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val fileInfo = queryFileInfo(context, uri)
            val inputStream: InputStream = if (uri.scheme == "asset") {
                val assetPath = getAssetPath(uri)
                context.assets.open(assetPath)
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Không thể mở luồng đọc tệp từ Content Provider"))
            }

            inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val outputStream = java.io.ByteArrayOutputStream()
                var bytesRead: Int
                var totalRead = 0L

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress?.invoke(totalRead, fileInfo.size)
                }
                Result.success(outputStream.toByteArray())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isUriAccessible(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "asset") return true
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
