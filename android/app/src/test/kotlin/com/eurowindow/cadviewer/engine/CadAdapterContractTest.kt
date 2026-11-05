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
    fun unsupportedFileExtension_failsWithUnsupportedFormatException() = runTest {
        val adapter = MlightCadAdapter { }
        val result = adapter.openDrawing("document.pdf", byteArrayOf(1, 2, 3))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CadEngineException.UnsupportedFormatException)
    }
}
