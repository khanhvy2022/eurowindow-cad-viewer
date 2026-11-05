package com.eurowindow.cadviewer.saf

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class SafFileInfoTest {

    @Test
    fun supportedMimeTypes_containsDxfAndDwg() {
        val types = SafManager.SUPPORTED_MIME_TYPES.toList()
        assertTrue("Must support application/dxf", types.contains("application/dxf"))
        assertTrue("Must support image/vnd.dxf", types.contains("image/vnd.dxf"))
        assertTrue("Must support model/vnd.dwg", types.contains("model/vnd.dwg"))
    }

    @Test
    fun safFileInfo_correctlyExtractsExtension() {
        val dummyUri = Mockito.mock(Uri::class.java)
        val infoDxf = SafFileInfo(dummyUri, "CuaSoEA55.DXF", 102400L, "DXF".lowercase())
        assertEquals("dxf", infoDxf.extension)

        val infoDwg = SafFileInfo(dummyUri, "BanVeKienTruc.DWG", 204800L, "DWG".lowercase())
        assertEquals("dwg", infoDwg.extension)
    }
}
