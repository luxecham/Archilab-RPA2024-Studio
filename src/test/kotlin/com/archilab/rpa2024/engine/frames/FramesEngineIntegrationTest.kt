package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.normative.SeismicZone
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FramesEngineIntegrationTest {

    private val columnDesigner = ColumnFramesDesigner(
        zone = SeismicZone.ZONE_III,
        fc28Mpa = 25.0,
        feMpa = 500.0
    )

    private val beamDesigner = BeamFramesDesigner(
        fc28Mpa = 25.0,
        feMpa = 500.0,
        columnSupportDimCm = 50.0
    )

    @Test
    fun testColumn40x50CompleteDesign() {
        // Poteau 40x50 (C111) : Nd = 1495.43 kN, Mcorr = 52.0 kNm, Vu = 220.0 kN
        val report = columnDesigner.designColumn(
            columnId = "C111",
            story = "H RDC",
            sectionName = "POT 40X50",
            bCm = 40.0,
            hCm = 50.0,
            clearHeightMeters = 3.24,
            ndMaxKn = 1495.43,
            muCorrKnM = 52.0,
            vuMaxKn = 220.0
        )

        // Minimum RPA : 0.9% * 2000 = 18.0 cm²
        assertEquals(18.0, report.aMinRpaCm2, 0.01)

        // Disposition adoptée (12T14 = 18.48 cm²)
        assertTrue(report.adoptedLongitudinalBars.totalAreaCm2 >= 18.0)
        assertEquals("12T14", report.adoptedLongitudinalBars.description)

        // Zone nodale Lcr = max(324/6 = 54, 40, 50, 60) = 60 cm
        assertEquals(60.0, report.lcrNodalZoneCm, 0.1)
        assertEquals(10.0, report.spacingNodalCm, 0.1)
        assertEquals(15.0, report.spacingCurrentCm, 0.1)

        // Cisaillement : tau_bu <= 1.88 MPa
        assertTrue(report.isShearCompliant)
        assertTrue(report.isOverallCompliant)
    }

    @Test
    fun testColumn35x55CompleteDesign() {
        // Poteau 35x55 (C106) : Nd = 672.76 kN, Mcorr = 70.0 kNm, Vu = 73.0 kN
        val report = columnDesigner.designColumn(
            columnId = "C106",
            story = "H RDC",
            sectionName = "POT 35X55",
            bCm = 35.0,
            hCm = 55.0,
            clearHeightMeters = 3.24,
            ndMaxKn = 672.76,
            muCorrKnM = 70.0,
            vuMaxKn = 73.0
        )

        // Minimum RPA : 0.9% * 1925 = 17.325 cm²
        assertEquals(17.325, report.aMinRpaCm2, 0.01)

        // Zone nodale Lcr = max(54, 35, 55, 60) = 60 cm (ou 82.5 cm selon h=55)
        assertTrue(report.lcrNodalZoneCm >= 60.0)
        assertTrue(report.isOverallCompliant)
    }

    @Test
    fun testPrimaryBeam30x55FourShearChecks() {
        // Poutre Principale 30x55 : Mt = 136.63 kNm, Ma = -161.70 kNm, Vu = 178.0 kN
        val report = beamDesigner.designBeam(
            beamId = "B1",
            story = "H RDC",
            sectionName = "PR 30X55",
            isPrimary = true,
            bCm = 30.0,
            hCm = 55.0,
            lengthMeters = 4.35,
            muSpanKnM = 136.63,
            muSupportKnM = 161.70,
            vuMaxKn = 178.0
        )

        // Minimum RPA : 0.5% * 30 * 55 = 8.25 cm²
        assertEquals(8.25, report.aMinRpaCm2, 0.01)

        // Section en appui adoptée : 3T14 + 3T14 = 9.24 cm²
        assertEquals("3T14 filantes + 3T14 chapeaux", report.adoptedSupportBars.description)
        assertEquals(9.24, report.adoptedSupportBars.totalAreaCm2, 0.05)

        // Vérification des 4 contrôles d'effort tranchant
        val checks = report.shearChecks
        // 1. tau_u <= 3.33 MPa
        assertTrue(checks.isTauUCompliant)
        assertEquals(1.14, checks.tauUMpa, 0.10) // ~1.20 MPa dans le rapport

        // 2. Bielle de béton : Vu <= 911.14 kN (0.267 * 300 * 500 * 25 / 1000 = 1001 kN)
        assertTrue(checks.isBielleCompliant)

        // 3. Aciers inférieurs d'appui : As >= Vu / (fe/gammaS) = 1780 / 434.78 = 4.09 cm²
        assertTrue(checks.isAnchorageCompliant)
        assertEquals(4.09, checks.requiredAnchorageSteelCm2, 0.05)

        // 4. Contrainte moyenne : sigma_mb <= 21.67 MPa
        assertTrue(checks.isSigmaMbCompliant)
        assertTrue(checks.allChecksCompliant)
    }

    @Test
    fun testPrimaryBeam30x35Design() {
        // Poutre Principale 30x35 : Mt = 62.00 kNm, Ma = 103.68 kNm, Vu = 144.0 kN
        val report = beamDesigner.designBeam(
            beamId = "B2",
            story = "H RDC",
            sectionName = "PR 30X35",
            isPrimary = true,
            bCm = 30.0,
            hCm = 35.0,
            lengthMeters = 3.15,
            muSpanKnM = 62.00,
            muSupportKnM = 103.68,
            vuMaxKn = 144.0
        )

        assertEquals(5.25, report.aMinRpaCm2, 0.01)
        assertTrue(report.shearChecks.allChecksCompliant)
        assertEquals(1.50, report.shearChecks.tauUMpa, 0.10) // ~1.52 MPa dans le rapport
    }
}
