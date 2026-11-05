package com.eurowindow.cadviewer.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class MlightCadAdapter(
    private val jsExecutor: (String) -> Unit
) : CadEngine {

    companion object {
        const val ENABLE_COMMERCIAL_DWG_PARSER = true
        private const val JS_TIMEOUT_MS = 60000L
    }

    private var isEngineReady = false
    private var currentDrawingInfo: DrawingInfo? = null
    private var pendingLoadDeferred: CompletableDeferred<DrawingInfo>? = null
    private var cachedLayers: List<CadLayer> = emptyList()

    @Volatile
    var currentDrawingBytes: ByteArray? = null
        private set

    override suspend fun initialize(): Boolean {
        isEngineReady = true
        return true
    }

    override suspend fun openDrawing(fileName: String, bytes: ByteArray): Result<DrawingInfo> {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext != "dxf" && ext != "dwg") {
            return Result.failure(CadEngineException.UnsupportedFormatException(ext))
        }

        val deferred = CompletableDeferred<DrawingInfo>()
        pendingLoadDeferred = deferred
        currentDrawingBytes = bytes

        // Trigger native stream loading via https://cad.eurowindow.local/current-drawing-stream
        val script = "if (window.loadCurrentDrawing) { window.loadCurrentDrawing('$fileName'); } else if (window.EurowindowBridge && window.EurowindowBridge.loadDrawingFromArrayBuffer) { fetch('https://cad.eurowindow.local/current-drawing-stream').then(r => r.arrayBuffer()).then(b => window.EurowindowBridge.loadDrawingFromArrayBuffer('$fileName', b)); } else { window.EurowindowBridge.loadDrawingFromBase64('$fileName', ''); }"
        jsExecutor(script)

        return try {
            val info = withTimeout(JS_TIMEOUT_MS) {
                deferred.await()
            }
            currentDrawingInfo = info
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(CadEngineException.ParseFailureException(e.message ?: "Hết thời gian nạp bản vẽ"))
        } finally {
            pendingLoadDeferred = null
        }
    }

    override suspend fun closeDrawing(): Result<Unit> {
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.closeDrawing) window.EurowindowBridge.closeDrawing();")
        currentDrawingInfo = null
        currentDrawingBytes = null
        cachedLayers = emptyList()
        return Result.success(Unit)
    }

    override suspend fun getLayers(): List<CadLayer> {
        return cachedLayers
    }

    override suspend fun setLayerVisibility(layerName: String, visible: Boolean): Result<Unit> {
        cachedLayers = cachedLayers.map {
            if (it.name == layerName) it.copy(isVisible = visible) else it
        }
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.setLayerVisibility) window.EurowindowBridge.setLayerVisibility('$layerName', $visible);")
        return Result.success(Unit)
    }

    override suspend fun zoomToFit(): Result<Unit> {
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.zoomToFit) window.EurowindowBridge.zoomToFit();")
        return Result.success(Unit)
    }

    fun switchToModelSpace() {
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.switchToModelSpace) window.EurowindowBridge.switchToModelSpace();")
    }

    fun startMeasureDistance() {
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.startMeasureDistance) window.EurowindowBridge.startMeasureDistance();")
    }

    fun startMeasureArea() {
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.startMeasureArea) window.EurowindowBridge.startMeasureArea();")
    }

    fun clearMeasurements() {
        jsExecutor("if (window.EurowindowBridge && window.EurowindowBridge.clearMeasurements) window.EurowindowBridge.clearMeasurements();")
    }

    override suspend fun getDrawingInfo(): DrawingInfo? {
        return currentDrawingInfo
    }

    // Called by AndroidCadBridge from JavaScript
    fun onDocumentLoadedFromJs(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val fileName = json.optString("fileName", "drawing.dwg")
            val layersArray = json.optJSONArray("layers")
            val list = mutableListOf<CadLayer>()
            if (layersArray != null) {
                for (i in 0 until layersArray.length()) {
                    val lObj = layersArray.getJSONObject(i)
                    list.add(
                        CadLayer(
                            id = lObj.optString("id", "$i"),
                            name = lObj.optString("name", "Layer $i"),
                            isVisible = !lObj.optBoolean("isOff", false),
                            colorHex = lObj.optInt("color", 7)
                        )
                    )
                }
            }
            cachedLayers = list

            val info = DrawingInfo(
                fileName = fileName,
                fileExtension = fileName.substringAfterLast('.', "dwg"),
                title = json.optString("title", fileName),
                layerCount = json.optInt("layerCount", list.size)
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
