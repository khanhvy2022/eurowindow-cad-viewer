package com.eurowindow.cadviewer.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadAdapterContractTest {

    @Test
    fun dwgLoading_triggersJsBridgeCall() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        val dummyDwgBytes = "dummy_dwg_content".toByteArray()
        val job = launch {
            adapter.openDrawing("window.dwg", dummyDwgBytes)
        }

        delay(50)
        assertTrue("JavaScript execution should be triggered for DWG", executedScripts.isNotEmpty())

        adapter.onDocumentLoadedFromJs("""{"fileName":"window.dwg","title":"Window","layerCount":8,"layers":[{"id":"1","name":"0","isOff":false,"color":7}]}""")
        job.join()

        val info = adapter.getDrawingInfo()
        assertEquals("window.dwg", info?.fileName)
        assertEquals(8, info?.layerCount)
        assertEquals(1, adapter.getLayers().size)
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

        adapter.onDocumentLoadedFromJs("""{"fileName":"plan.dxf","title":"Plan","layerCount":5}""")
        job.join()

        val info = adapter.getDrawingInfo()
        assertEquals("plan.dxf", info?.fileName)
        assertEquals(5, info?.layerCount)
    }

    @Test
    fun unsupportedFormat_failsEarlyWithoutExecutingJs() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        val result = adapter.openDrawing("model.pdf", ByteArray(10))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CadEngineException.UnsupportedFormatException)
        assertEquals(0, executedScripts.size)
    }

    @Test
    fun zoomToFit_triggersCorrectJs() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        adapter.zoomToFit()

        assertEquals(1, executedScripts.size)
        assertTrue(executedScripts[0].contains("zoomToFit"))
    }

    @Test
    fun setLayerVisibility_triggersCorrectJs() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        adapter.setLayerVisibility("Walls", false)

        assertEquals(1, executedScripts.size)
        assertTrue(executedScripts[0].contains("Walls"))
        assertTrue(executedScripts[0].contains("false"))
    }

    @Test
    fun measurementCommands_triggerCorrectJs() = runTest {
        val executedScripts = mutableListOf<String>()
        val adapter = MlightCadAdapter { script -> executedScripts.add(script) }

        adapter.startMeasureDistance()
        adapter.startMeasureArea()
        adapter.clearMeasurements()

        assertEquals(3, executedScripts.size)
        assertTrue(executedScripts[0].contains("startMeasureDistance"))
        assertTrue(executedScripts[1].contains("startMeasureArea"))
        assertTrue(executedScripts[2].contains("clearMeasurements"))
    }
}
