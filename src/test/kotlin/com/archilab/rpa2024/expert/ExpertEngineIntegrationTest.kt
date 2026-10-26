package com.archilab.rpa2024.expert

import com.archilab.rpa2024.parser.BaseReactionRow
import com.archilab.rpa2024.parser.ColumnForceRow
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Scénario d'intégration expert : ingestion ETABS + SAFE, audit de vérifiabilité,
 * proposition de ferraillage réelle et note Word/PDF enrichie.
 */
class ExpertEngineIntegrationTest {

    private fun snapshot(overrides: EtabsResults? = null): CrossModelSnapshot {
        val etabs = SampleJointA.load()
        val safe = SampleSafeFoundation.load()
        val effective = if (overrides == null) etabs else etabs.copy(results = overrides)
        return CrossModelIngestor().link(
            etabs = effective,
            foundations = safe,
            seismicConfig = SampleJointA.defaultConfig,
            sources = listOf(
                CrossModelSource("ETABS", null),
                CrossModelSource("SAFE", null)
            )
        )
    }

    @Test
    fun `l ingestion fichier e2k est tracee par le snapshot croise`(@TempDir temp: Path) {
        val e2k = temp.resolve("JOINT_A.e2k")
        SampleJointA.writeE2k(e2k)
        val snapshot = CrossModelIngestor().ingest(
            CrossModelRequest(
                etabsModel = e2k,
                jointName = "JOINT A",
                seismicConfig = SampleJointA.defaultConfig
            )
        )
        assertTrue(snapshot.model.frames.isNotEmpty())
        assertEquals(3, snapshot.sources.size)
        assertTrue(snapshot.sources.first().available)
        assertFalse(snapshot.isCrossModel)
    }

    @Test
    fun `le snapshot croise et l audit sont exploitables`() {
        val snapshot = snapshot()
        assertTrue(snapshot.isCrossModel)
        assertTrue(snapshot.model.frames.isNotEmpty())
        assertNotNull(snapshot.foundations)
        val report = StructuralDataIntegrityAuditor().audit(snapshot)
        assertTrue(report.findings.isNotEmpty())
        assertTrue(report.score in 0..100)
    }

    @Test
    fun `une participation modale absente est bloquante`() {
        val base = snapshot()
        val broken = base.results.copy(modalRatios = emptyList())
        val report = StructuralDataIntegrityAuditor().audit(snapshot(broken))
        val finding = report.findings.firstOrNull { it.code == "RES-01" }
        assertNotNull(finding, report.findings.joinToString { it.code })
        assertEquals(IntegritySeverity.BLOCKING, finding!!.severity)
        assertEquals(IntegrityVerdict.NOT_VERIFIABLE, report.verdict)
    }

    @Test
    fun `un calage MSE MDS insuffisant est signale avec un facteur d echelle`() {
        val base = snapshot()
        val lowDynamic = base.results.copy(
            baseReactions = listOf(
                BaseReactionRow("EX", 10.0, 0.0, 0.0),
                BaseReactionRow("EY", 10.0, 0.0, 0.0)
            )
        )
        val report = StructuralDataIntegrityAuditor().audit(snapshot(lowDynamic))
        val mseFinding = report.findings.firstOrNull { it.code.startsWith("MSE-MDS") }
        assertNotNull(mseFinding, report.findings.joinToString { it.code })
        assertEquals(IntegritySeverity.BLOCKING, mseFinding!!.severity)
        assertTrue(mseFinding.remediation.contains("facteur"))
    }

    @Test
    fun `le designer propose des barres commerciales et une optimisation sur anomalie`() {
        val base = snapshot()
        val family = base.model.joint.sectionCatalog.columns.first()
        val overload = ColumnForceRow(
            story = base.model.joint.storySystem.mainStories.first().storyName,
            element = family.elementIds.first(),
            loadCase = "ELA",
            stationMeters = 0.0,
            pKN = -8_000.0,
            v2KN = 0.0,
            v3KN = 0.0,
            m2KNm = 250.0,
            m3KNm = 300.0
        )
        val overloaded = base.results.copy(columnForces = base.results.columnForces + overload)
        val proposals = AutonomousReinforcementDesigner().design(snapshot(overloaded))
        assertTrue(proposals.columns.isNotEmpty())
        assertTrue(proposals.proposals.any { it.longitudinal.contains("T") })
        assertTrue(proposals.criticalCount > 0 || proposals.optimizedCount > 0)
    }

    @Test
    fun `la note expert contient les 13 chapitres et les sections 10 8 et 13 10`(@TempDir temp: Path) {
        val snapshot = snapshot()
        val integrity = StructuralDataIntegrityAuditor().audit(snapshot)
        val proposals = AutonomousReinforcementDesigner().design(snapshot)
        val builder = AutomatedCalculationNoteBuilder()
        val note = builder.build(
            snapshot = snapshot,
            metadata = ExpertNoteMetadata(
                projectTitle = "1500 LLV BOSQUET",
                wilaya = "Sidi Bel Abbès",
                client = "Direction du Logement",
                engineer = "Archilab",
                reference = "NDC-JOINT-A-RPA2024"
            ),
            integrity = integrity,
            proposals = proposals
        )
        assertEquals(13, note.chapterCount)
        assertTrue(note.document.chapters.first { it.number == 10 }.blocks.any {
            it is com.archilab.rpa2024.report.ReportBlock.Heading && it.text.contains("10.8")
        })
        assertTrue(note.document.chapters.first { it.number == 13 }.blocks.any {
            it is com.archilab.rpa2024.report.ReportBlock.Heading && it.text.contains("13.10")
        })
        assertEquals(20, note.capturePlaceholders.size)
        val docx = builder.writeWord(note, temp.resolve("NOTE_EXPERT.docx"))
        val pdf = builder.writePdf(note, temp.resolve("NOTE_EXPERT.pdf"))
        assertTrue(Files.size(docx) > 20_000)
        assertTrue(Files.size(pdf) > 20_000)
        assertFalse(note.proposals.proposals.isEmpty())
    }
}
