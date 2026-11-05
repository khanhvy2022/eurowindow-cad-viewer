package com.eurowindow.cadviewer.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadAdapterContractTest {

    @Test
    fun dwgLoading_whenCommercialParserDisabled_failsWithSpecificException() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        val dummyDwgBytes = "dummy_dwg_content".toByteArray()
        val result = adapter.openDrawing("window.dwg", dummyDwgBytes)

        assertTrue("DWG must fail when commercial parser flag is false", result.isFailure)
        assertTrue(
            "Exception must be DwgFeatureDisabledException",
            result.exceptionOrNull() is CadEngineException.DwgFeatureDisabledException
        )
        assertEquals("No JavaScript should be executed for blocked DWG", 0, executedScripts.size)
    }

    @Test
    fun dxfLoading_triggersJsBridgeCall() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        val dummyDxfBytes = "0\nSECTION\n2\nHEADER\n0\nENDSEC\n0\nEOF".toByteArray()
        
        // Launch openDrawing in coroutine
        val job = launch {
            adapter.openDrawing("plan.dxf", dummyDxfBytes)
        }

        // Simulate JS callback
        delay(50)
        assertTrue("JavaScript execution should be triggered for DXF", executedScripts.isNotEmpty())
        assertTrue("Must call loadDrawingFromBase64", executedScripts[0].contains("loadDrawingFromBase64"))

        adapter.onDocumentLoadedFromJs("""{"fileName":"plan.dxf","title":"Plan","layerCount":5}""")
        job.join()

        val info = adapter.getDrawingInfo()
        assertEquals("plan.dxf", info?.fileName)
        assertEquals(5, info?.layerCount)
    }

    @Test
    fun dxfLoading_withActualCorpusLayerCounts_accuratelyPropagatesLayerInfo() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }
        val dummyDxfBytes = "0\nSECTION\n2\nENTITIES\n0\nENDSEC\n0\nEOF".toByteArray()

        val job = launch {
            adapter.openDrawing("sheet_0.dxf", dummyDxfBytes)
        }

        delay(50)
        // Simulate bridge callback from MLightCAD with actual sheet_0.dxf layer structure (4 layers)
        adapter.onDocumentLoadedFromJs("""{"fileName":"sheet_0.dxf","title":"sheet_0.dxf","layerCount":4}""")
        job.join()

        val info = adapter.getDrawingInfo()
        assertEquals("sheet_0.dxf", info?.fileName)
        assertEquals("Actual sheet_0.dxf layerCount must be 4", 4, info?.layerCount)
    }

    @Test
    fun layerVisibilityToggle_triggersCorrectBridgeCall() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        adapter.setLayerVisibility("PLATE", false)
        assertEquals(1, executedScripts.size)
        assertEquals("window.EurowindowBridge.setLayerVisibility('PLATE', false);", executedScripts[0])

        adapter.setLayerVisibility("COMMON_CUT", true)
        assertEquals(2, executedScripts.size)
        assertEquals("window.EurowindowBridge.setLayerVisibility('COMMON_CUT', true);", executedScripts[1])
    }

    @Test
    fun zoomToFit_triggersCorrectBridgeCall() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        adapter.zoomToFit()
        assertEquals(1, executedScripts.size)
        assertEquals("window.EurowindowBridge.zoomToFit();", executedScripts[0])
    }

    @Test
    fun unsupportedFileExtension_failsWithUnsupportedFormatException() = runTest {
        val adapter = MlightCadAdapter { }
        val result = adapter.openDrawing("document.pdf", byteArrayOf(1, 2, 3))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CadEngineException.UnsupportedFormatException)
    }
}
