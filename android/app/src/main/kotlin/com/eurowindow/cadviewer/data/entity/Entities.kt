package com.eurowindow.cadviewer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drawing_metadata")
data class DrawingMetadata(
    @PrimaryKey
    val uriString: String,
    val displayName: String,
    val extension: String,
    val fileSize: Long,
    val lastOpenedTimestamp: Long,
    val isFavorite: Boolean = false,
    val thumbnailPath: String? = null
)

@Entity(tableName = "measurement_sidecars")
data class MeasurementSidecar(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentUriString: String,
    val measurementType: String,
    val measurementValue: Double,
    val unit: String,
    val pointsJson: String,
    val createdAtTimestamp: Long = System.currentTimeMillis()
)
