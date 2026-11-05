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
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Some providers do not support persistable permissions
        }
    }

    fun queryFileInfo(context: Context, uri: Uri): SafFileInfo {
        var name = "unknown_drawing"
        var size = 0L

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

        val extension = name.substringAfterLast('.', "").lowercase()
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
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Không thể mở luồng đọc tệp từ Content Provider"))

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
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
