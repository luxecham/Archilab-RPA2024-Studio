package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.foundations.SafeFootingOutput
import com.archilab.rpa2024.engine.frames.BeamCompleteDesignReport
import com.archilab.rpa2024.engine.frames.BeamShearChecks
import com.archilab.rpa2024.engine.frames.ColumnCompleteDesignReport
import com.archilab.rpa2024.engine.frames.RebarArrangement
import com.archilab.rpa2024.engine.secondary.JoistDesignResult
import com.archilab.rpa2024.engine.secondary.ParapetDesignResult
import com.archilab.rpa2024.engine.secondary.StaircaseDesignResult
import com.archilab.rpa2024.engine.seismic.BaseShearComparison
import com.archilab.rpa2024.engine.seismic.ModalCheckSummary
import com.archilab.rpa2024.engine.seismic.ModalModeResult
import com.archilab.rpa2024.engine.seismic.ReducedStressColumnCheck
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

class CtcWordGenerationIntegrationTest {

    @Test
    fun testGenerateFullCtcDocxDocument() {
        val tempFile = File.createTempFile("NOTE_DE_CALCUL_TEST_JOINT_A", ".docx")
        tempFile.deleteOnExit()

        val dummyModel = StructuralJointModel(
            projectName = "RÉALISATION D'UNE SÛRETÉ URBAINE AU NIVEAU DU SITE DES 1500 LLV BOSQUET",
            jointName = "JOINT A",
            wilaya = "Sidi Bel Abbès",
            zone = com.archilab.rpa2024.normative.SeismicZone.ZONE_III,
            site = SoilSiteClass.S3,
            importance = ImportanceGroup.GROUPE_1A,
            behaviorR = 3.5,
            qualityQ = 1.10,
            dampingEta = 0.88,
            concrete = ConcreteProperties(),
            rebar = RebarProperties(),
            geo = GeotechnicalProperties()
        )

        val payload = CtcReportDataPayload(
            model = dummyModel,
            modalModes = listOf(
                ModalModeResult(1, 0.399, 73.17, 0.68, 0.0, 73.17, 0.68, 0.0, 1.06),
                ModalModeResult(2, 0.295, 1.12, 70.32, 0.0, 74.29, 71.00, 0.0, 3.40),
                ModalModeResult(8, 0.073, 0.71, 14.30, 0.0, 93.92, 93.88, 0.0, 0.59)
            ),
            modalSummary = ModalCheckSummary(8, 93.92, 93.88, true, true, true, "Condition ΣMasses ≥ 90% vérifiée"),
            baseShearX = BaseShearComparison("X-X", 0.398, 0.307, 0.256, 0.85, 2251.69, 1884.51, 0.837, true, 1.0),
            baseShearY = BaseShearComparison("Y-Y", 0.294, 0.307, 0.256, 0.85, 2251.69, 1819.47, 0.808, true, 1.0),
            columnReducedStressChecks = listOf(
                ReducedStressColumnCheck("H RDC", "C111", "POT 40X50", 40.0, 50.0, 2000.0, 1495.43, 25.0, 0.299, true, "CV"),
                ReducedStressColumnCheck("H RDC", "C106", "POT 35X55", 35.0, 55.0, 1925.0, 672.76, 25.0, 0.140, true, "CV"),
                ReducedStressColumnCheck("H RDC", "C127", "POT 35X35", 35.0, 35.0, 1225.0, 445.61, 25.0, 0.146, true, "CV")
            ),
            storyDriftsX = emptyList(),
            storyDriftsY = emptyList(),
            pDeltaChecksX = emptyList(),
            pDeltaChecksY = emptyList(),
            designedColumns = listOf(
                ColumnCompleteDesignReport("C111", "H RDC", "POT 40X50", 40.0, 50.0, 3.24, 1495.43, 52.0, 220.0, 11.40, 18.00, RebarArrangement("12T14", 18.48, 12), 10, 4, 60.0, 10.0, 15.0, 70.0, 1.22, 1.88, true, true)
            ),
            designedBeams = listOf(
                BeamCompleteDesignReport(
                    "B1", "H RDC", "PR 30X55", true, 30.0, 55.0, 4.35, 136.63, 161.70, 178.0, 8.25, 6.49, 7.78,
                    RebarArrangement("3T14 + 2T12", 6.88, 5), RebarArrangement("3T14 + 3T14", 9.24, 6), 8, 4, 82.5, 8.0, 15.0, 84.0,
                    BeamShearChecks(178.0, 1.20, 3.33, true, 911.14, true, 4.09, 9.24, true, 2.61, 21.67, true, true)
                )
            ),
            designedWalls = emptyList(),
            parapet = ParapetDesignResult(0.60, 0.10, 1.65, 0.071, 2.44, 1.17, 1.00, 0.90, 3.29, 3.35, "T8 esp=15cm", "4T8"),
            joist = JoistDesignResult(4, 4.35, 11.23, -11.56, -11.56, 16.39, 3.39, "3T12", 2.26, "1T12+1T12", "T6", 5.686, 8.70, true),
            staircase = StaircaseDesignResult(17.0, 30.0, 64.0, true, 29.54, 15.0, 8.02, 2.50, 14.50, 5.65, "5T12/ml", 1.50, "4T10/ml"),
            foundations = listOf(
                SafeFootingOutput("Line_SFJ200", "SFJ200", 109.24, 120.00, true, 27.97, "27.97 cm² (BaelR)", 0.09, 2.97, "2.97 cm² (BaelT)", 0.00, 3.50, "T12 e=15cm", true)
            )
        )

        val generator = CtcNoteDeCalculWordGenerator()
        generator.generateFullCtcDocx(payload, tempFile)

        // Vérification de la création et lecture du document généré
        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() > 2000)

        FileInputStream(tempFile).use { fis ->
            val doc = XWPFDocument(fis)
            assertTrue(doc.paragraphs.size > 20)
            assertTrue(doc.tables.size >= 4)

            // Contrôle textuel du contenu des chapitres
            val textContent = doc.paragraphs.joinToString("\n") { it.text }
            assertTrue(textContent.contains("NOTE DE CALCUL STRUCTURALE"))
            assertTrue(textContent.contains("RPA 2024"))
            assertTrue(textContent.contains("SIDI BEL ABBÈS"))
            assertTrue(textContent.contains("Condition Vérifiée (CV)"))
        }
    }
}
