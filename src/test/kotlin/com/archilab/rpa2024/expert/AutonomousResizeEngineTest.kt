package com.archilab.rpa2024.expert

import com.archilab.rpa2024.parser.BeamForceRow
import com.archilab.rpa2024.parser.ColumnForceRow
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Itération Copilot 1 : auto-resize par pas de 5 cm et réévaluation BAEL. */
class AutonomousResizeEngineTest {

    private fun snapshot(results: EtabsResults): CrossModelSnapshot = CrossModelIngestor().link(
        etabs = SampleJointA.load().copy(results = results),
        foundations = SampleSafeFoundation.load(),
        seismicConfig = SampleJointA.defaultConfig
    )

    @Test
    fun `un poteau surcharge est redimensionne et revalide`() {
        val base = SampleJointA.load()
        val family = base.model.joint.sectionCatalog.columns.first()
        val overload = ColumnForceRow(
            story = base.model.joint.storySystem.mainStories.first().storyName,
            element = family.elementIds.first(),
            loadCase = "ELA",
            stationMeters = 0.0,
            pKN = -10_000.0,
            v2KN = 0.0,
            v3KN = 0.0,
            m2KNm = 300.0,
            m3KNm = 450.0
        )
        val report = AutonomousResizeEngine().resize(
            snapshot(base.results.copy(columnForces = base.results.columnForces + overload))
        )
        val decision = report.decisions.firstOrNull { it.elementName == family.name }
        assertTrue(decision != null, report.decisions.joinToString { it.elementName })
        assertTrue(decision!!.originalSection != decision.finalSection)
        assertTrue(decision.iterations > 0)
        assertTrue(report.warnings.any { it.contains("réexécuter ETABS", true) })
    }

    @Test
    fun `une poutre en flexion excessive est redimensionnee`() {
        val base = SampleJointA.load()
        val family = base.model.joint.sectionCatalog.beams.first()
        val overload = BeamForceRow(
            story = base.model.joint.storySystem.mainStories.first().storyName,
            element = family.elementIds.first(),
            loadCase = "ELA",
            stationMeters = 2.0,
            pKN = 0.0,
            v2KN = 450.0,
            v3KN = 0.0,
            m2KNm = 0.0,
            m3KNm = 500.0,
            lengthMeters = 7.5
        )
        val report = AutonomousResizeEngine().resize(
            snapshot(base.results.copy(beamForces = base.results.beamForces + overload))
        )
        val decision = report.decisions.firstOrNull { it.elementName == family.name }
        assertTrue(decision != null, report.decisions.joinToString { it.elementName })
        assertTrue(decision!!.iterations > 0)
        assertFalse(decision.finalSection == decision.originalSection)
    }

    @Test
    fun `aucune modification inutile sur un modele sain`() {
        val report = AutonomousResizeEngine().resize(snapshot(SampleJointA.load().results))
        assertTrue(report.decisions.isEmpty() || report.decisions.all { it.iterations == 0 })
    }
}
