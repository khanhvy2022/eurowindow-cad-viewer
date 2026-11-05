package com.eurowindow.cadviewer.data.repository

import android.content.Context
import android.net.Uri
import com.eurowindow.cadviewer.data.database.CadDatabase
import com.eurowindow.cadviewer.data.entity.DrawingMetadata
import com.eurowindow.cadviewer.saf.SafManager
import kotlinx.coroutines.flow.Flow

class DrawingRepository(private val context: Context) {
    private val database = CadDatabase.getInstance(context)
    private val drawingDao = database.drawingDao()

    fun getRecentDrawings(): Flow<List<DrawingMetadata>> {
        return drawingDao.getAllRecentDrawings()
    }

    fun getFavoriteDrawings(): Flow<List<DrawingMetadata>> {
        return drawingDao.getFavoriteDrawings()
    }

    suspend fun recordDrawingOpened(uri: Uri): DrawingMetadata {
        SafManager.takePersistablePermission(context, uri)
        val fileInfo = SafManager.queryFileInfo(context, uri)

        val metadata = DrawingMetadata(
            uriString = uri.toString(),
            displayName = fileInfo.displayName,
            extension = fileInfo.extension,
            fileSize = fileInfo.size,
            lastOpenedTimestamp = System.currentTimeMillis()
        )
        drawingDao.insertOrUpdate(metadata)
        return metadata
    }

    suspend fun toggleFavorite(uriString: String, currentStatus: Boolean) {
        drawingDao.setFavorite(uriString, !currentStatus)
    }

    suspend fun removeFromRecent(uriString: String) {
        drawingDao.deleteByUri(uriString)
    }
}
