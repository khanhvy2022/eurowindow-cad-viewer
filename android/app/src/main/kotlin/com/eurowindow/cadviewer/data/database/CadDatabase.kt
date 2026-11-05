package com.eurowindow.cadviewer.data.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.eurowindow.cadviewer.data.dao.DrawingDao
import com.eurowindow.cadviewer.data.dao.MeasurementDao
import com.eurowindow.cadviewer.data.entity.DrawingMetadata
import com.eurowindow.cadviewer.data.entity.MeasurementSidecar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

abstract class CadDatabase {
    abstract fun drawingDao(): DrawingDao
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var INSTANCE: CadDatabase? = null

        fun getInstance(context: Context): CadDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CadDatabaseImpl(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

class CadDatabase_Impl(context: Context) : CadDatabaseImpl(context)

open class CadDatabaseImpl(context: Context) : CadDatabase() {
    private val dbHelper = DatabaseHelper(context)
    private val drawingDao = SQLiteDrawingDao(dbHelper)
    private val measurementDao = SQLiteMeasurementDao(dbHelper)

    override fun drawingDao(): DrawingDao = drawingDao
    override fun measurementDao(): MeasurementDao = measurementDao
}

private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "eurowindow_cad_viewer.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS drawing_metadata (
                uriString TEXT PRIMARY KEY NOT NULL,
                displayName TEXT NOT NULL,
                extension TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                lastOpenedTimestamp INTEGER NOT NULL,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                thumbnailPath TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS measurement_sidecars (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentUriString TEXT NOT NULL,
                measurementType TEXT NOT NULL,
                measurementValue REAL NOT NULL,
                unit TEXT NOT NULL,
                pointsJson TEXT NOT NULL,
                createdAtTimestamp INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS drawing_metadata")
        db.execSQL("DROP TABLE IF EXISTS measurement_sidecars")
        onCreate(db)
    }
}

private class SQLiteDrawingDao(private val dbHelper: DatabaseHelper) : DrawingDao {
    private val recentFlow = MutableStateFlow<List<DrawingMetadata>>(emptyList())
    private val favoriteFlow = MutableStateFlow<List<DrawingMetadata>>(emptyList())

    init {
        refreshFlows()
    }

    private fun refreshFlows() {
        try {
            val db = dbHelper.readableDatabase
            val recentList = mutableListOf<DrawingMetadata>()
            db.rawQuery("SELECT * FROM drawing_metadata ORDER BY lastOpenedTimestamp DESC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    recentList.add(cursorToMetadata(cursor))
                }
            }
            recentFlow.value = recentList
            favoriteFlow.value = recentList.filter { it.isFavorite }
        } catch (e: Exception) {
            // Safety guard against DB access errors
        }
    }

    private fun cursorToMetadata(cursor: Cursor): DrawingMetadata {
        val uri = cursor.getString(cursor.getColumnIndexOrThrow("uriString"))
        val name = cursor.getString(cursor.getColumnIndexOrThrow("displayName"))
        val ext = cursor.getString(cursor.getColumnIndexOrThrow("extension"))
        val size = cursor.getLong(cursor.getColumnIndexOrThrow("fileSize"))
        val time = cursor.getLong(cursor.getColumnIndexOrThrow("lastOpenedTimestamp"))
        val fav = cursor.getInt(cursor.getColumnIndexOrThrow("isFavorite")) == 1
        val thumbIndex = cursor.getColumnIndex("thumbnailPath")
        val thumb = if (thumbIndex != -1 && !cursor.isNull(thumbIndex)) cursor.getString(thumbIndex) else null
        return DrawingMetadata(uri, name, ext, size, time, fav, thumb)
    }

    override fun getAllRecentDrawings(): Flow<List<DrawingMetadata>> = recentFlow.asStateFlow()

    override fun getFavoriteDrawings(): Flow<List<DrawingMetadata>> = favoriteFlow.asStateFlow()

    override suspend fun getDrawingByUri(uriString: String): DrawingMetadata? = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT * FROM drawing_metadata WHERE uriString = ? LIMIT 1", arrayOf(uriString)).use { cursor ->
                if (cursor.moveToFirst()) cursorToMetadata(cursor) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun insertOrUpdate(drawing: DrawingMetadata): Unit = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("uriString", drawing.uriString)
                put("displayName", drawing.displayName)
                put("extension", drawing.extension)
                put("fileSize", drawing.fileSize)
                put("lastOpenedTimestamp", drawing.lastOpenedTimestamp)
                put("isFavorite", if (drawing.isFavorite) 1 else 0)
                put("thumbnailPath", drawing.thumbnailPath)
            }
            db.insertWithOnConflict("drawing_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            refreshFlows()
        } catch (e: Exception) {
        }
    }

    override suspend fun update(drawing: DrawingMetadata): Unit = insertOrUpdate(drawing)

    override suspend fun setFavorite(uriString: String, isFavorite: Boolean): Unit = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("isFavorite", if (isFavorite) 1 else 0)
            }
            db.update("drawing_metadata", values, "uriString = ?", arrayOf(uriString))
            refreshFlows()
        } catch (e: Exception) {
        }
    }

    override suspend fun delete(drawing: DrawingMetadata): Unit = deleteByUri(drawing.uriString)

    override suspend fun deleteByUri(uriString: String): Unit = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.delete("drawing_metadata", "uriString = ?", arrayOf(uriString))
            refreshFlows()
        } catch (e: Exception) {
        }
    }
}

private class SQLiteMeasurementDao(private val dbHelper: DatabaseHelper) : MeasurementDao {
    override fun getMeasurementsForDocument(uriString: String): Flow<List<MeasurementSidecar>> {
        val flow = MutableStateFlow<List<MeasurementSidecar>>(emptyList())
        try {
            val db = dbHelper.readableDatabase
            val list = mutableListOf<MeasurementSidecar>()
            db.rawQuery("SELECT * FROM measurement_sidecars WHERE documentUriString = ? ORDER BY createdAtTimestamp ASC", arrayOf(uriString)).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        MeasurementSidecar(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            documentUriString = cursor.getString(cursor.getColumnIndexOrThrow("documentUriString")),
                            measurementType = cursor.getString(cursor.getColumnIndexOrThrow("measurementType")),
                            measurementValue = cursor.getDouble(cursor.getColumnIndexOrThrow("measurementValue")),
                            unit = cursor.getString(cursor.getColumnIndexOrThrow("unit")),
                            pointsJson = cursor.getString(cursor.getColumnIndexOrThrow("pointsJson")),
                            createdAtTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("createdAtTimestamp"))
                        )
                    )
                }
            }
            flow.value = list
        } catch (e: Exception) {
        }
        return flow.asStateFlow()
    }

    override suspend fun insertMeasurement(measurement: MeasurementSidecar): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val values = ContentValues().apply {
                put("documentUriString", measurement.documentUriString)
                put("measurementType", measurement.measurementType)
                put("measurementValue", measurement.measurementValue)
                put("unit", measurement.unit)
                put("pointsJson", measurement.pointsJson)
                put("createdAtTimestamp", measurement.createdAtTimestamp)
            }
            db.insert("measurement_sidecars", null, values)
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun clearMeasurementsForDocument(uriString: String): Unit = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            db.delete("measurement_sidecars", "documentUriString = ?", arrayOf(uriString))
        } catch (e: Exception) {
        }
    }
}
