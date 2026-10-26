package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.model.SoilSiteClass
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.report.CtcNoteDeCalculWordGenerator
import com.archilab.rpa2024.report.CtcReportDataPayload
import com.archilab.rpa2024.state.ProjectStateManager
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

class InteractiveStudioAndGraphicsTest {

    private fun isPngHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() && // 'P'
                bytes[2] == 0x4E.toByte() && // 'N'
                bytes[3] == 0x47.toByte() && // 'G'
                bytes[4] == 0x0D.toByte() &&
                bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0x0A.toByte()
    }

    @Test
    fun testRpaSpectrumChartRenderer() {
        val renderer = RpaSpectrumChartRenderer()
        val (points, minThreshold) = renderer.computeSpectrumCurve(
            zone = SeismicZone.ZONE_III,
            site = SoilSiteClass.S3,
            importance = ImportanceGroup.GROUPE_1A
        )

        assertTrue(points.size >= 100, "La courbe spectrale doit comporter au moins 100 points.")
        assertTrue(minThreshold > 0.03, "Le seuil minimal 0.20·A·I doit être strictement positif.")

        for (pt in points) {
            assertTrue(pt.sadOverG >= minThreshold - 1e-6, "Sad/g (${pt.sadOverG}) doit respecter le plancher 0.20·A·I ($minThreshold).")
        }

        val pngBytes = renderer.renderToPng(
            zone = SeismicZone.ZONE_III,
            site = SoilSiteClass.S3,
            importance = ImportanceGroup.GROUPE_1A,
            t0Operating = 0.399
        )
        assertTrue(pngBytes.size > 20_000, "L'image PNG du spectre doit faire plus de 20 Ko.")
        assertTrue(isPngHeader(pngBytes), "L'en-tête de l'image doit être un PNG valide.")
    }

    @Test
    fun testRebarSectionDetailRenderer() {
        val renderer = RebarSectionDetailRenderer()

        // 1. Coupe transversale poteau
        val colSection = renderer.renderColumnSectionPng(
            widthCm = 45.0,
            heightCm = 45.0,
            barDiameterMm = 14
        )
        assertTrue(colSection.size > 20_000, "La coupe du poteau doit faire plus de 20 Ko.")
        assertTrue(isPngHeader(colSection), "L'en-tête doit correspondre à un fichier PNG.")

        // 2. Élévation poteau avec zones nodales
        val colElev = renderer.renderColumnElevationPng(
            heightTotalM = 3.06,
            sectionDimCm = 45.0,
            lcrM = 0.60,
            spacingLcrCm = 10.0,
            spacingCouranteCm = 15.0
        )
        assertTrue(colElev.size > 20_000, "L'élévation du poteau doit faire plus de 20 Ko.")
        assertTrue(isPngHeader(colElev), "L'en-tête doit correspondre à un fichier PNG.")

        // 3. Coupe transversale poutre
        val beamSection = renderer.renderBeamSectionPng(
            widthBCm = 30.0,
            heightHCm = 45.0
        )
        assertTrue(beamSection.size > 20_000, "La coupe de la poutre doit faire plus de 20 Ko.")
        assertTrue(isPngHeader(beamSection), "L'en-tête doit correspondre à un fichier PNG.")
    }

    @Test
    fun testShearWallEnvelopesRenderer() {
        val renderer = ShearWallEnvelopesRenderer()

        // 1. Enveloppe des moments décalée de hcr
        val momentPng = renderer.renderMomentEnvelopePng(
            wallLengthM = 3.20,
            wallThicknessM = 0.20,
            totalHeightM = 9.18,
            hcrM = 3.20
        )
        assertTrue(momentPng.size > 20_000, "L'enveloppe des moments du voile doit faire plus de 20 Ko.")
        assertTrue(isPngHeader(momentPng), "L'en-tête doit être un PNG valide.")

        // 2. Enveloppe d'effort tranchant amplifiée 1.40 Vu
        val shearPng = renderer.renderShearEnvelopePng(
            wallLengthM = 3.20,
            wallThicknessM = 0.20
        )
        assertTrue(shearPng.size > 20_000, "L'enveloppe de cisaillement du voile doit faire plus de 20 Ko.")
        assertTrue(isPngHeader(shearPng), "L'en-tête doit être un PNG valide.")
    }

    @Test
    fun testSafeSoilPressureMapRenderer() {
        val renderer = SafeSoilPressureMapRenderer()

        val soilHeatmap = renderer.renderSoilPressureHeatmapPng(
            ksKNm3 = 40000.0,
            sigmaSolAdmissibleBars = 2.00,
            peakStressBars = 1.84
        )
        assertTrue(soilHeatmap.size > 25_000, "La heatmap SAFE du sol doit faire plus de 25 Ko.")
        assertTrue(isPngHeader(soilHeatmap), "L'en-tête doit être un PNG valide.")
    }

    @Test
    fun testProjectStateManagerReactiveRecalculation() {
        val manager = ProjectStateManager()
        val initial = manager.state.value

        // 1. État initial conforme
        assertTrue(initial.isAllCompliant, "L'état initial doit être conforme.")
        assertTrue(initial.columnOutput.isNuValid, "L'effort normal réduit initial ν <= 0.30 doit être valide.")
        assertTrue(initial.columnOutput.reducedAxialStressNu <= 0.30)
        assertTrue(initial.computationTimeMs < 50, "Le temps de calcul initial doit être < 50 ms.")

        // 2. Mutation sismique (changement de zone)
        manager.updateSeismicParams(zone = SeismicZone.ZONE_II)
        val afterZone = manager.state.value
        assertEquals(0.10, afterZone.spectrumOutput.aCoeff, 1e-4)
        assertEquals(SeismicZone.ZONE_II, afterZone.seismicZone)

        // 3. Mutation poteau : surcharge excessive entraînant le dépassement de ν <= 0.30
        manager.updateColumnParams(nedKN = 5000.0) // Surcharge extrême
        val overloaded = manager.state.value
        assertFalse(overloaded.columnOutput.isNuValid, "Avec 5000 kN, l'effort normal réduit ν doit dépasser 0.30.")
        assertFalse(overloaded.isAllCompliant, "Le projet ne doit plus être compliant à 100%.")

        // 4. Correction de coffrage : augmentation de la section à 65x65 cm
        manager.updateColumnParams(widthCm = 65.0, heightCm = 65.0, nedKN = 2000.0)
        val fixed = manager.state.value
        assertTrue(fixed.columnOutput.isNuValid, "Avec 65x65 cm et 2000 kN, ν doit redevenir conforme.")

        // 5. Réinitialisation aux valeurs par défaut
        manager.resetToDefaults()
        val resetState = manager.state.value
        assertEquals(45.0, resetState.columnWidthBCm)
        assertEquals(45.0, resetState.columnHeightHCm)
        assertEquals(1480.0, resetState.columnNedKN)
        assertTrue(resetState.isAllCompliant)
    }

    @Test
    fun testReactiveWordPayloadTracksCurrentState() {
        val manager = ProjectStateManager()
        manager.updateColumnParams(widthCm = 65.0, heightCm = 65.0, nedKN = 2000.0)
        val payload = CtcReportDataPayload.fromInteractiveState(manager.state.value)
        val column = payload.designedColumns.first()
        assertEquals(65.0, column.bCm, 1e-9)
        assertEquals(65.0, column.hCm, 1e-9)
        assertEquals(2000.0, column.ndMaxKn, 1e-9)
        assertEquals(65.0, payload.columnReducedStressChecks.first().bCm, 1e-9)
        assertTrue(payload.foundations.isNotEmpty())
        assertEquals(manager.state.value.seismicZone, payload.model.zone)

        manager.updateSeismicParams(zone = SeismicZone.ZONE_II)
        val updated = CtcReportDataPayload.fromInteractiveState(manager.state.value)
        assertEquals(SeismicZone.ZONE_II, updated.model.zone)
        assertEquals(0.10, updated.baseShearX.designSpectralAccelSadOverG, 0.20)
    }

    @Test
    fun testWordDocumentIncludesAllRenderedGraphics() {
        val outDocx = File("build/test-results/TEST_NOTE_DE_CALCUL_WITH_VECTOR_GRAPHICS.docx")
        outDocx.parentFile?.mkdirs()

        val generator = CtcNoteDeCalculWordGenerator()
        val payload = CtcReportDataPayload.createJointASample()
        generator.generateOfficialCtcWordReport(outDocx, payload)

        assertTrue(outDocx.exists(), "Le document Word officiel doit être généré.")
        assertTrue(outDocx.length() > 50_000, "Le document avec images doit dépasser 50 Ko (taille réelle: ${outDocx.length()} octets).")

        FileInputStream(outDocx).use { fis ->
            val doc = XWPFDocument(fis)
            val pictures = doc.allPictures
            // Doit contenir au moins 6 images vectorielles haute résolution insérées :
            // 1. Spectre RPA 2024
            // 2. Coupe Poteau C111
            // 3. Élévation Poteau Lcr
            // 4. Coupe Poutre PR30x55
            // 5. Enveloppe Moments Voile
            // 6. Enveloppe Cisaillement Voile
            // 7. Cartographie Sol SAFE
            assertTrue(pictures.size >= 6, "Le document Word doit contenir au minimum 6 images vectorielles haute résolution (trouvé: ${pictures.size}).")
        }
    }
}
