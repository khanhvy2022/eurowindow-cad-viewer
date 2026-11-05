package com.eurowindow.cadviewer.data

import com.eurowindow.cadviewer.data.entity.DrawingMetadata
import com.eurowindow.cadviewer.data.entity.MeasurementSidecar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomMetadataContractTest {

    @Test
    fun drawingMetadata_hasCorrectDefaultsAndProperties() {
        val now = System.currentTimeMillis()
        val metadata = DrawingMetadata(
            uriString = "content://com.android.providers.media.documents/document/101",
            displayName = "CuaDiEA55.dxf",
            extension = "dxf",
            fileSize = 1048576L,
            lastOpenedTimestamp = now
        )

        assertEquals("content://com.android.providers.media.documents/document/101", metadata.uriString)
        assertEquals("CuaDiEA55.dxf", metadata.displayName)
        assertEquals("dxf", metadata.extension)
        assertEquals(1048576L, metadata.fileSize)
        assertEquals(now, metadata.lastOpenedTimestamp)
        assertFalse("Default favorite status must be false", metadata.isFavorite)
        assertNull("Default thumbnail must be null", metadata.thumbnailPath)
    }

    @Test
    fun measurementSidecar_preservesIsolationFromCadFile() {
        val now = System.currentTimeMillis()
        val sidecar = MeasurementSidecar(
            id = 1L,
            documentUriString = "content://com.android.providers.media.documents/document/101",
            measurementType = "DISTANCE",
            measurementValue = 1250.5,
            unit = "mm",
            pointsJson = """[{"x": 10.0, "y": 20.0}, {"x": 1260.5, "y": 20.0}]""",
            createdAtTimestamp = now
        )

        assertEquals(1L, sidecar.id)
        assertEquals("content://com.android.providers.media.documents/document/101", sidecar.documentUriString)
        assertEquals("DISTANCE", sidecar.measurementType)
        assertEquals(1250.5, sidecar.measurementValue, 0.001)
        assertEquals("mm", sidecar.unit)
        assertTrue(sidecar.pointsJson.contains("1260.5"))
    }

    @Test
    fun drawingMetadataList_sortsByLastOpenedTimestampDescending() {
        val item1 = DrawingMetadata(
            uriString = "content://test/1",
            displayName = "Doc1.dxf",
            extension = "dxf",
            fileSize = 100L,
            lastOpenedTimestamp = 1000L
        )
        val item2 = DrawingMetadata(
            uriString = "content://test/2",
            displayName = "Doc2.dxf",
            extension = "dxf",
            fileSize = 200L,
            lastOpenedTimestamp = 3000L
        )
        val item3 = DrawingMetadata(
            uriString = "content://test/3",
            displayName = "Doc3.dxf",
            extension = "dxf",
            fileSize = 300L,
            lastOpenedTimestamp = 2000L
        )

        val list = listOf(item1, item2, item3)
        val sorted = list.sortedByDescending { it.lastOpenedTimestamp }

        assertEquals("Doc2.dxf", sorted[0].displayName)
        assertEquals("Doc3.dxf", sorted[1].displayName)
        assertEquals("Doc1.dxf", sorted[2].displayName)
    }
}
