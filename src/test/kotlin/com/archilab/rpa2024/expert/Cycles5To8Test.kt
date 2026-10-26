package com.archilab.rpa2024.expert

import com.archilab.rpa2024.cad.CadProjectInfo
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Cycles 5 à 8 : fondations, CAO DXF, note CTC et orchestrateur one-click. */
class Cycles5To8Test {

    private fun snapshot(): CrossModelSnapshot = CrossModelIngestor().link(
        etabs = SampleJointA.load(),
        foundations = SampleSafeFoundation.load(),
        seismicConfig = SampleJointA.defaultConfig
    )

    private fun project(): CadProjectInfo = CadProjectInfo(
        projectTitle = "1500 LLV BOSQUET",
        jointName = "JOINT A",
        wilaya = "Sidi Bel Abbès",
        client = "Direction du Logement",
        engineer = "Archilab",
        reference = "NDC-JOINT-A-RPA2024"
    )

    @Test
    fun `cycle 5 optimise les semelles et calcule la portance Winkler`() {
        val report = AutonomousFoundationOptimizer().optimize(snapshot())
        assertNotNull(report.designSet)
        assertTrue(report.designSet!!.footings.isNotEmpty())
        assertTrue(report.maximumEffectiveStressKNM2 != null)
        assertTrue(report.allowableStressKNM2!! > 0.0)
        assertTrue(report.maximumSettlementMeters != null)
    }

    @Test
    fun `cycle 6 produit coffrage ferraillage nomenclature et DQE`(@TempDir temp: Path) {
        val report = AutonomousCadDxfEngine().export(snapshot(), project(), temp)
        assertNotNull(report.formworkPath)
        assertNotNull(report.detailingPath)
        assertNotNull(report.bbsDxfPath)
        assertNotNull(report.bbsXlsxPath)
        report.files.forEach { assertTrue(Files.exists(it), it.toString()) }
        assertTrue(report.sheetCount > 0)
        val dxf = Files.readString(report.formworkPath!!, Charsets.ISO_8859_1)
        assertTrue(dxf.contains("SECTION"))
        assertTrue(dxf.contains("EOF"))
    }

    @Test
    fun `cycle 7 produit la note CTC 13 chapitres en Word et PDF`(@TempDir temp: Path) {
        val metadata = ExpertNoteMetadata(
            projectTitle = "1500 LLV BOSQUET",
            wilaya = "Sidi Bel Abbès",
            client = "Direction du Logement",
            engineer = "Archilab",
            reference = "NDC-JOINT-A-RPA2024"
        )
        val result = AutonomousCtcReportEngine().generate(snapshot(), metadata, temp)
        assertEquals(13, result.chapterCount)
        assertTrue(Files.size(result.wordPath) > 20_000)
        assertTrue(Files.size(result.pdfPath) > 20_000)
    }

    @Test
    fun `cycle 8 execute le workflow one click complet`() {
        val temp = Path.of("build", "oneclick-demo")
        Files.createDirectories(temp)
        val request = OneClickRequest(
            etabsModel = Path.of("NON_UTILISE.e2k"),
            jointName = "JOINT A",
            seismicConfig = SampleJointA.defaultConfig,
            noteMetadata = ExpertNoteMetadata(
                projectTitle = "1500 LLV BOSQUET",
                wilaya = "Sidi Bel Abbès",
                client = "Direction du Logement",
                engineer = "Archilab",
                reference = "NDC-JOINT-A-RPA2024"
            ),
            cadProject = project(),
            outputDirectory = temp
        )
        val result = ArchilabOneClickStudio().runFromSnapshot(snapshot(), request)
        assertEquals(100, result.dashboard.automationIndex)
        assertTrue(result.dashboard.reportFiles.size >= 2)
        assertTrue(result.dashboard.cadFiles.isNotEmpty())
        result.dashboard.allFiles.forEach { assertTrue(Files.exists(it), it.toString()) }
        assertTrue(result.dashboard.durationMs >= 0)
        assertTrue(result.dashboard.proposalCount > 0)
    }
}
