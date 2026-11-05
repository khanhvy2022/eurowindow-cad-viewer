package com.eurowindow.cadviewer.engine

import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class MlightCadAdapter(
    private val jsExecutor: (String) -> Unit
) : CadEngine {

    companion object {
        const val ENABLE_COMMERCIAL_DWG_PARSER = false
        private const val JS_TIMEOUT_MS = 25000L
    }

    private var isEngineReady = false
    private var currentDrawingInfo: DrawingInfo? = null
    private var pendingLoadDeferred: CompletableDeferred<DrawingInfo>? = null

    @Volatile
    var currentDrawingBytes: ByteArray? = null
        private set

    override suspend fun initialize(): Boolean {
        isEngineReady = true
        return true
    }

    override suspend fun openDrawing(fileName: String, bytes: ByteArray): Result<DrawingInfo> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "dwg" && !ENABLE_COMMERCIAL_DWG_PARSER) {
            return Result.failure(CadEngineException.DwgFeatureDisabledException())
        }

        if (ext != "dxf" && ext != "dwg") {
            return Result.failure(CadEngineException.UnsupportedFormatException(ext))
        }

        val deferred = CompletableDeferred<DrawingInfo>()
        pendingLoadDeferred = deferred
        currentDrawingBytes = bytes

        // We trigger native stream loading via https://cad.eurowindow.local/current-drawing-stream
        // which avoids Android WebView IPC Binder 1MB transaction limits on multi-megabyte CAD files,
        // while maintaining loadDrawingFromBase64 fallback signature.
        val script = "if (window.loadCurrentDrawing) { window.loadCurrentDrawing('$fileName'); } else if (window.EurowindowBridge && window.EurowindowBridge.loadDrawingFromArrayBuffer) { fetch('https://cad.eurowindow.local/current-drawing-stream').then(r => r.arrayBuffer()).then(b => window.EurowindowBridge.loadDrawingFromArrayBuffer('$fileName', b)); } else { window.EurowindowBridge.loadDrawingFromBase64('$fileName', ''); }"
        jsExecutor(script)

        return try {
            val info = withTimeout(JS_TIMEOUT_MS) {
                deferred.await()
            }
            currentDrawingInfo = info
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(CadEngineException.ParseFailureException(e.message ?: "Timeout loading drawing"))
        } finally {
            pendingLoadDeferred = null
        }
    }

    override suspend fun closeDrawing(): Result<Unit> {
        jsExecutor("window.EurowindowBridge.closeDrawing();")
        currentDrawingInfo = null
        currentDrawingBytes = null
        return Result.success(Unit)
    }

    override suspend fun getLayers(): List<CadLayer> {
        return emptyList()
    }

    override suspend fun setLayerVisibility(layerName: String, visible: Boolean): Result<Unit> {
        jsExecutor("window.EurowindowBridge.setLayerVisibility('$layerName', $visible);")
        return Result.success(Unit)
    }

    override suspend fun zoomToFit(): Result<Unit> {
        jsExecutor("window.EurowindowBridge.zoomToFit();")
        return Result.success(Unit)
    }

    override suspend fun getDrawingInfo(): DrawingInfo? {
        return currentDrawingInfo
    }

    // Called by AndroidCadBridge from JavaScript
    fun onDocumentLoadedFromJs(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val info = DrawingInfo(
                fileName = json.optString("fileName", "drawing.dxf"),
                fileExtension = json.optString("fileName", "drawing.dxf").substringAfterLast('.', "dxf"),
                title = json.optString("title", "Untitled Drawing"),
                layerCount = json.optInt("layerCount", 0)
            )
            currentDrawingInfo = info
            pendingLoadDeferred?.complete(info)
        } catch (e: Exception) {
            pendingLoadDeferred?.completeExceptionally(e)
        }
    }

    fun onDocumentLoadErrorFromJs(errorMsg: String) {
        pendingLoadDeferred?.completeExceptionally(CadEngineException.ParseFailureException(errorMsg))
    }
}
