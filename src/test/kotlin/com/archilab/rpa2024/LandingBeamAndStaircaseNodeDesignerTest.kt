package com.archilab.rpa2024

import com.archilab.rpa2024.engine.secondary.LandingBeamAndStaircaseNodeDesigner
import com.archilab.rpa2024.engine.secondary.LandingBeamGeometry
import com.archilab.rpa2024.engine.secondary.StaircaseDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LandingBeamAndStaircaseNodeDesignerTest {

    private val staircaseDesigner = StaircaseDesigner()
    private val landingBeamDesigner = LandingBeamAndStaircaseNodeDesigner()

    @Test
    fun testLandingBeamCombinedBendingAndTorsionDesign() {
        val stairReport = staircaseDesigner.designStaircase(
            geometry = StaircaseGeometry(
                stepHeightHmMeters = 0.17,
                treadWidthGMeters = 0.30,
                flightSpanMeters = 2.40,
                landingSpanMeters = 1.40,
                slabThicknessEpMeters = 0.16
            ),
            liveLoadQ = 2.50
        )

        val beamReport = landingBeamDesigner.designLandingBeam(
            beamGeometry = LandingBeamGeometry(
                beamName = "PP_30x40",
                widthBMeters = 0.30,
                heightHMeters = 0.40,
                spanLengthLMeters = 3.60
            ),
            staircaseReport = stairReport,
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        assertNotNull(beamReport)
        assertEquals("CONFORME BAEL 91 & CTC (FLEXION + TORSION)", beamReport.globalStatus)
        assertTrue(beamReport.isCombinedStressCompliant)

        // Vérification des sollicitations
        assertTrue(beamReport.designBendingMomentMuKNm > 0.0)
        assertTrue(beamReport.designShearForceVuKN > 0.0)
        assertTrue(beamReport.designTorsionMomentTuKNm > 0.0, "La torsion induite par l'excentricité de la paillasse doit être calculée")

        // Vérification des contraintes BAEL A.5.4
        assertTrue(beamReport.shearStressTauVMPa > 0.0)
        assertTrue(beamReport.torsionStressTauTMPa > 0.0)
        assertTrue(beamReport.combinedStressTauTotalMPa <= beamReport.allowableStressLimitMPa)

        // Armatures de flexion et de torsion
        assertTrue(beamReport.bendingSteelAreaAsBendingCm2 > 0.0)
        assertTrue(beamReport.torsionLongitudinalSteelAreaAlCm2 > 0.0, "Des armatures de peau pour la torsion doivent être générées")
        assertTrue(beamReport.totalLongitudinalSteelAreaCm2 >= beamReport.bendingSteelAreaAsBendingCm2)

        // Dispositions de nœuds CTC
        assertTrue(beamReport.reentrantCornerDetailingAdvice.contains("angles rentrants"))
        assertTrue(beamReport.adoptedTransverseRebar.contains("Cadres fermés"))
    }
}
