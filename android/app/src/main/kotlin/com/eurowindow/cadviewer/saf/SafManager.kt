package com.eurowindow.cadviewer.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class SafFileInfo(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val extension: String,
    val localFile: File? = null
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
        if (uri.scheme == "asset" || uri.scheme == "file") return
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            // Providers like DownloadStorageProvider do not support persistable permissions on Android 14/15/16
        }
    }

    fun getAssetPath(uri: Uri): String {
        val s = uri.toString()
        return s.removePrefix("asset://").removePrefix("asset:/").removePrefix("/")
    }

    private fun detectFormatFromBytes(context: Context, uri: Uri): String? {
        return try {
            val stream: InputStream? = when (uri.scheme) {
                "asset" -> context.assets.open(getAssetPath(uri))
                "file" -> {
                    val path = uri.path ?: uri.schemeSpecificPart
                    FileInputStream(File(path))
                }
                else -> context.contentResolver.openInputStream(uri)
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

    /**
     * Resolves and copies an external URI (such as DownloadStorageProvider from Downloads,
     * Zalo, or Files app on Android 14/15/16) into the app's internal sandbox.
     * This eliminates "Permission Denial" errors permanently.
     */
    fun cacheUriToSandbox(context: Context, uri: Uri): SafFileInfo {
        if (uri.scheme == "asset") {
            return queryFileInfo(context, uri)
        }

        // If it's already a local file inside internal storage, return directly
        if (uri.scheme == "file") {
            val path = uri.path ?: uri.schemeSpecificPart
            val file = File(path)
            if (file.exists()) {
                val name = file.name
                val ext = name.substringAfterLast('.', "").lowercase()
                return SafFileInfo(
                    uri = uri,
                    displayName = name,
                    size = file.length(),
                    extension = ext,
                    localFile = file
                )
            }
        }

        // 1. Safely query name and size without crashing on Android 14/15/16 SecurityException
        var displayName = "drawing_${System.currentTimeMillis()}"
        var reportedSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        val n = cursor.getString(nameIndex)
                        if (!n.isNullOrBlank()) displayName = n
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        reportedSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // DownloadStorageProvider or restrictive provider denied query
            uri.lastPathSegment?.let { seg ->
                val clean = seg.substringAfterLast('/')
                if (clean.isNotBlank()) displayName = clean
            }
        }

        // Sanitize file name for local storage
        var safeName = displayName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        var ext = safeName.substringAfterLast('.', "").lowercase()

        if (ext != "dwg" && ext != "dxf") {
            val mimeType = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
            val inferredExt = when {
                mimeType?.contains("dxf", ignoreCase = true) == true -> "dxf"
                mimeType?.contains("dwg", ignoreCase = true) == true -> "dwg"
                else -> detectFormatFromBytes(context, uri) ?: "dwg"
            }
            ext = inferredExt
            if (!safeName.endsWith(".$ext", ignoreCase = true)) {
                safeName = "$safeName.$ext"
            }
        }

        val drawingsDir = File(context.filesDir, "drawings").apply { mkdirs() }
        val destFile = File(drawingsDir, safeName)

        // Fast-path: if the sandbox already holds the exact same file size, skip re-copying 68MB+
        if (destFile.exists() && reportedSize > 0L && destFile.length() == reportedSize) {
            return SafFileInfo(
                uri = Uri.fromFile(destFile),
                displayName = safeName,
                size = destFile.length(),
                extension = ext,
                localFile = destFile
            )
        }

        // 2. Stream into sandbox file immediately while permission is fresh
        var copySuccess = false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 262144)
                }
                copySuccess = true
            }
        } catch (e: Exception) {
            // Try file descriptor fallback
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output, bufferSize = 262144)
                        }
                    }
                    copySuccess = true
                }
            } catch (e2: Exception) {
                // If direct copy fails, destFile might be incomplete
            }
        }

        val finalFile = if (copySuccess && destFile.exists() && destFile.length() > 0) destFile else null
        val finalUri = if (finalFile != null) Uri.fromFile(finalFile) else uri
        val finalSize = finalFile?.length() ?: reportedSize

        return SafFileInfo(
            uri = finalUri,
            displayName = safeName,
            size = finalSize,
            extension = ext,
            localFile = finalFile
        )
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
                extension = ext,
                localFile = null
            )
        }

        if (uri.scheme == "file") {
            val path = uri.path ?: uri.schemeSpecificPart
            val file = File(path)
            val name = file.name
            val ext = name.substringAfterLast('.', "").lowercase()
            return SafFileInfo(
                uri = uri,
                displayName = name,
                size = if (file.exists()) file.length() else 0L,
                extension = ext,
                localFile = if (file.exists()) file else null
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
            // Fallback to lastPathSegment if ContentProvider query fails
        }

        if (name == "unknown_drawing" && uri.lastPathSegment != null) {
            name = uri.lastPathSegment ?: name
        }

        var extension = name.substringAfterLast('.', "").lowercase()

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
            extension = extension,
            localFile = null
        )
    }

    suspend fun readFileBytes(
        context: Context,
        uri: Uri,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // Fast-path: local file read directly without buffer-doubling thrashing
            if (uri.scheme == "file") {
                val path = uri.path ?: uri.schemeSpecificPart
                val file = File(path)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
                    return@withContext Result.success(bytes)
                }
            }

            val fileInfo = queryFileInfo(context, uri)
            val inputStream: InputStream = when (uri.scheme) {
                "asset" -> context.assets.open(getAssetPath(uri))
                "file" -> FileInputStream(File(uri.path ?: uri.schemeSpecificPart))
                else -> context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Không thể mở luồng đọc tệp từ Content Provider"))
            }

            inputStream.use { stream ->
                val buffer = ByteArray(65536)
                val initialCapacity = if (fileInfo.size in 1..200_000_000L) fileInfo.size.toInt() else 32
                val outputStream = ByteArrayOutputStream(initialCapacity)
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
        if (uri.scheme == "file") {
            val path = uri.path ?: uri.schemeSpecificPart
            return File(path).exists()
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
