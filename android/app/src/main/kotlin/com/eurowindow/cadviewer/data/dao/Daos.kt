package com.eurowindow.cadviewer.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eurowindow.cadviewer.data.entity.DrawingMetadata
import com.eurowindow.cadviewer.data.entity.MeasurementSidecar
import kotlinx.coroutines.flow.Flow

@Dao
interface DrawingDao {
    @Query("SELECT * FROM drawing_metadata ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecentDrawings(): Flow<List<DrawingMetadata>>

    @Query("SELECT * FROM drawing_metadata WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavoriteDrawings(): Flow<List<DrawingMetadata>>

    @Query("SELECT * FROM drawing_metadata WHERE uriString = :uriString LIMIT 1")
    suspend fun getDrawingByUri(uriString: String): DrawingMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(drawing: DrawingMetadata)

    @Update
    suspend fun update(drawing: DrawingMetadata)

    @Query("UPDATE drawing_metadata SET isFavorite = :isFavorite WHERE uriString = :uriString")
    suspend fun setFavorite(uriString: String, isFavorite: Boolean)

    @Delete
    suspend fun delete(drawing: DrawingMetadata)

    @Query("DELETE FROM drawing_metadata WHERE uriString = :uriString")
    suspend fun deleteByUri(uriString: String)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement_sidecars WHERE documentUriString = :uriString ORDER BY createdAtTimestamp ASC")
    fun getMeasurementsForDocument(uriString: String): Flow<List<MeasurementSidecar>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: MeasurementSidecar): Long

    @Query("DELETE FROM measurement_sidecars WHERE documentUriString = :uriString")
    suspend fun clearMeasurementsForDocument(uriString: String)
}
