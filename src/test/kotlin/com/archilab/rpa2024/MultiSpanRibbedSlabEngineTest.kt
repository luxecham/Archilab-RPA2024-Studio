package com.archilab.rpa2024

import com.archilab.rpa2024.engine.slabs.MultiSpanRibbedSlabEngine
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MultiSpanRibbedSlabEngineTest {

    private val engine = MultiSpanRibbedSlabEngine()

    @Test
    fun testMultiStoryRibbedSlabAnalysisAndCurtailment() {
        val joint = createDefaultJointA()
        val report = engine.evaluateMultiStoryRibbedSlabs(joint)

        assertNotNull(report)
        assertTrue(report.spansCount > 0)
        assertTrue(report.isDeflectionCompliant, "La flèche calculée doit être inférieure à la flèche admissible")
        assertEquals(report.spansCount, report.curtailmentDetails.size)

        for (detail in report.curtailmentDetails) {
            assertTrue(detail.bottomCutoffDistanceMeters > 0.0)
            assertTrue(detail.leftChapeauxLengthMeters > 0.0)
            assertTrue(detail.rightChapeauxLengthMeters > 0.0)
            assertTrue(detail.antiShrinkageMesh.contains("Treillis"))
        }

        assertTrue(report.maxMomentSpanMuKNm > 0.0)
        assertTrue(report.maxMomentSupportMuKNm > 0.0)
        assertEquals("CONFORME CBA 93 & BAEL 91", report.globalStatus)
    }
}
