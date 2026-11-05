package com.eurowindow.cadviewer.engine

data class CadPoint(
    val x: Double,
    val y: Double,
    val z: Double = 0.0
)

data class CadLayer(
    val id: String,
    val name: String,
    val isVisible: Boolean,
    val colorHex: Int = 0xFFFFFFFF.toInt()
)

data class DrawingInfo(
    val fileName: String,
    val fileExtension: String,
    val title: String,
    val layerCount: Int,
    val boundsMin: CadPoint? = null,
    val boundsMax: CadPoint? = null
)

enum class MeasurementType {
    DISTANCE,
    ANGLE,
    AREA
}

data class MeasurementResult(
    val type: MeasurementType,
    val value: Double,
    val unit: String,
    val points: List<CadPoint>,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class CadEngineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class FileNotFoundException(path: String) : CadEngineException("Bản vẽ không tồn tại hoặc đã bị xóa: $path")
    class UnsupportedFormatException(ext: String) : CadEngineException("Định dạng không được hỗ trợ: $ext")
    class DwgFeatureDisabledException : CadEngineException("Định dạng DWG hiện đang bị khóa trong P0.1 (Yêu cầu cấp phép bản quyền commercial parser). Vui lòng dùng định dạng DXF.")
    class ParseFailureException(reason: String) : CadEngineException("Lỗi phân tích bản vẽ: $reason")
    class RendererCrashException : CadEngineException("Tiến trình render CAD bị dừng do quá tải bộ nhớ.")
}

interface CadDocument {
    val info: DrawingInfo
    val layers: List<CadLayer>
}

interface CadEngine {
    suspend fun initialize(): Boolean
    suspend fun openDrawing(fileName: String, bytes: ByteArray): Result<DrawingInfo>
    suspend fun closeDrawing(): Result<Unit>
    suspend fun getLayers(): List<CadLayer>
    suspend fun setLayerVisibility(layerName: String, visible: Boolean): Result<Unit>
    suspend fun zoomToFit(): Result<Unit>
    suspend fun getDrawingInfo(): DrawingInfo?
}

object CadEngineFactory {
    fun createDefaultAdapter(webViewExecutor: (String) -> Unit): CadEngine {
        return MlightCadAdapter(webViewExecutor)
    }
}
