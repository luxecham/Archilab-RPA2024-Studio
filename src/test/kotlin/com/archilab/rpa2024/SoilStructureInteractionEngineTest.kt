package com.archilab.rpa2024

import com.archilab.rpa2024.engine.foundations.SoilStructureInteractionEngine
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SoilStructureInteractionEngineTest {

    private val engine = SoilStructureInteractionEngine()

    @Test
    fun testSoilStructureInteractionAndUpliftVerification() {
        val joint = createDefaultJointA()

        val report = engine.evaluateInteractionAndStability(
            joint = joint,
            foundationLengthX = 24.50,
            foundationWidthY = 14.80,
            foundationEmbedmentD = 1.50
        )

        assertNotNull(report)
        assertEquals("CONFORME RPA 2024 § 10.1 & DTR BC 2.33.1", report.globalStatus)

        // Vérification des paramètres dynamiques de Pasternak et Pais-Kausel
        val imp = report.impedance
        assertTrue(imp.shearWaveVelocityVsMs >= 150.0, "La vitesse Vs doit correspondre à la classe de sol")
        assertTrue(imp.pasternakWinklerKsKNm3 > 0.0)
        assertTrue(imp.pasternakShearLayerGpKNm > 0.0, "Le paramètre de couche de cisaillement Pasternak doit être positif")
        assertTrue(imp.horizontalStiffnessKxMNm > 0.0)
        assertTrue(imp.rockingStiffnessKThetaMNmRad > 0.0)
        assertTrue(imp.periodLengtheningRatio >= 1.0, "L'interaction sol-structure allonge toujours la période fondamentale")
        assertTrue(imp.flexibleBasePeriodTISSecond >= imp.fixedBasePeriodTFSecond)
        assertTrue(imp.effectiveRadiationDampingPercent >= 5.0)

        // Vérification des 4 combinaisons de stabilité
        assertEquals(4, report.stabilityReports.size)

        for (stab in report.stabilityReports) {
            assertTrue(stab.isOverturningSafe, "Le facteur de sécurité au renversement Fs doit être >= 1.50 sous ${stab.combinationName}")
            assertTrue(stab.isUpliftCompliant, "La zone comprimée doit satisfaire aux exigences du CTC sous ${stab.combinationName}")
            assertTrue(stab.isSoilBearingCompliant, "La contrainte de pointe doit rester admissible sous ${stab.combinationName}")
            assertTrue(stab.compressedAreaRatioPercent >= stab.minRequiredCompressedAreaPercent)
            assertTrue(stab.observations.isNotEmpty())
        }

        // Cas ELS : 100% de contact
        val elsReport = report.stabilityReports.first { it.combinationName.contains("ELS") }
        assertEquals(100.0, elsReport.compressedAreaRatioPercent)
        assertEquals(0.0, elsReport.totalOverturningMomentMKNm)

        // Cas Sismique : Décollement partiel contrôlé
        val sismReport = report.stabilityReports.first { it.combinationName.contains("0.8G - Ex") }
        assertTrue(sismReport.totalOverturningMomentMKNm > 0.0)
        assertTrue(sismReport.compressedAreaRatioPercent >= 50.0)
    }
}
