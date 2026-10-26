package com.archilab.rpa2024

import com.archilab.rpa2024.engine.slabs.AdvancedPunchingShearEngine
import com.archilab.rpa2024.engine.slabs.ColumnSlabPosition
import com.archilab.rpa2024.engine.slabs.PunchingReinforcementType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdvancedPunchingShearEngineTest {

    private val engine = AdvancedPunchingShearEngine()

    @Test
    fun testInteriorColumnPunchingSufficientConcreteAlone() {
        // Poteau intérieur 40x40 cm sur dalle d'épaisseur 30 cm modérément chargé (Nu = 450 kN)
        val report = engine.evaluatePunching(
            nodeId = "COL_INT_01",
            position = ColumnSlabPosition.INTERIOR,
            columnAxialLoadVEdKN = 450.0,
            columnMomentMEdKNm = 25.0,
            c1Meters = 0.40,
            c2Meters = 0.40,
            slabThicknessHMeters = 0.30,
            concreteCoverMeters = 0.04,
            longitudinalRhoXPercent = 0.80,
            longitudinalRhoYPercent = 0.80,
            fc28MPa = 25.0
        )

        assertNotNull(report)
        assertTrue(report.isFaceStressCompliant, "La contrainte au nu ne doit pas dépasser vRd,max")
        assertTrue(report.isConcreteAloneSufficient, "Pour 450 kN sur dalle 30 cm, le béton seul doit suffire")
        assertFalse(report.reinforcementRequired)
        assertNull(report.shearReinforcementDetail)
        assertTrue(report.overallStatus.contains("Béton seul suffisant"))
    }

    @Test
    fun testHeavyColumnPunchingRequiresShearStudsRails() {
        // Poteau fortement chargé (Nu = 1350 kN) sur dalle de 25 cm : nécessite des armatures de poinçonnement
        val report = engine.evaluatePunching(
            nodeId = "COL_INT_HEAVY",
            position = ColumnSlabPosition.INTERIOR,
            columnAxialLoadVEdKN = 1350.0,
            columnMomentMEdKNm = 40.0,
            c1Meters = 0.45,
            c2Meters = 0.45,
            slabThicknessHMeters = 0.25,
            concreteCoverMeters = 0.035,
            longitudinalRhoXPercent = 1.0,
            longitudinalRhoYPercent = 1.0,
            fc28MPa = 25.0,
            preferShearStuds = true
        )

        assertNotNull(report)
        assertTrue(report.isFaceStressCompliant)
        assertFalse(report.isConcreteAloneSufficient, "Un effort de 1350 kN sur 25 cm dépasse la résistance du béton seul")
        assertTrue(report.reinforcementRequired)

        val detail = report.shearReinforcementDetail
        assertNotNull(detail)
        assertEquals(PunchingReinforcementType.SHEAR_STUDS_RAILS, detail?.type)
        assertTrue((detail?.numberOfRadialRails ?: 0) >= 6)
        assertTrue((detail?.studsPerRail ?: 0) >= 3)
        assertTrue((detail?.providedAreaAswCm2 ?: 0.0) >= (detail?.totalRequiredAreaAswCm2 ?: 0.0))
        assertTrue(detail?.commercialDescription?.contains("rails de goujons") == true)
        assertTrue(report.overallStatus.contains("CONFORME AVEC ARMATURES DE POINÇONNEMENT"))
    }

    @Test
    fun testCornerColumnPunchingWithHighEccentricity() {
        // Poteau d'angle avec fort facteur beta = 1.50
        val report = engine.evaluatePunching(
            nodeId = "COL_CORNER_01",
            position = ColumnSlabPosition.CORNER,
            columnAxialLoadVEdKN = 320.0,
            columnMomentMEdKNm = 35.0,
            c1Meters = 0.35,
            c2Meters = 0.35,
            slabThicknessHMeters = 0.22,
            longitudinalRhoXPercent = 0.70,
            longitudinalRhoYPercent = 0.70
        )

        assertNotNull(report)
        assertTrue(report.eccentricityFactorBeta >= 1.50)
        assertTrue(report.perimeterAtColumnFaceU0Meters < 1.0)
    }
}
