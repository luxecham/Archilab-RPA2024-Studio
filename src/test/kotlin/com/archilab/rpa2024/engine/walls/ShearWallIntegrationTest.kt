package com.archilab.rpa2024.engine.walls

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShearWallIntegrationTest {

    private val designer = AutonomousShearWallDesigner(fc28Mpa = 25.0, feMpa = 500.0)

    @Test
    fun testVoileV5EffortNormalReduitCompliance() {
        // Voile V5 (page 36) : lw = 1.40 m, bw = 0.30 m, Nd = 2943.92 kN
        val forcesV5 = listOf(
            StoryPierForces("H RDC", 0.00, 3.39, 2943.92, 450.0, 1200.0),
            StoryPierForces("H 1ER", 3.74, 3.39, 1800.0, 300.0, 800.0),
            StoryPierForces("TERRASSE", 7.48, 3.39, 650.0, 150.0, 250.0)
        )

        val reportV5 = designer.designShearWall(
            pierName = "V5",
            lwMeters = 1.40,
            bwMeters = 0.30,
            totalHeightHwMeters = 11.22,
            storyForces = forcesV5,
            totalStoriesN = 3
        )

        // nu_d = 2943.92 / (0.42 * 25000) = 0.280 <= 0.40
        assertTrue(reportV5.isNuDCompliant)
        assertEquals(0.280, reportV5.reducedNormalStressNuD, 0.005)
    }

    @Test
    fun testVoileV1CisaillementContrainteLimite() {
        // Voile V1 (page 37) : lw = 1.40 m, bw = 0.30 m, Vu = 1042.98 kN
        val forcesV1 = listOf(
            StoryPierForces("H RDC", 0.00, 3.39, 934.03, 1042.98, 1500.0),
            StoryPierForces("H 1ER", 3.74, 3.39, 600.00, 750.0, 900.0),
            StoryPierForces("TERRASSE", 7.48, 3.39, 200.00, 350.0, 300.0)
        )

        val reportV1 = designer.designShearWall(
            pierName = "V1",
            lwMeters = 1.40,
            bwMeters = 0.30,
            totalHeightHwMeters = 11.22,
            storyForces = forcesV1,
            totalStoriesN = 3
        )

        // tau_b = (1.4 * 1042.98 * 10) / (30 * 126) = 3.863 MPa <= 5.0 MPa
        assertTrue(reportV1.isShearCompliant)
        assertEquals(3.863, reportV1.shearStressTauBMpa, 0.01)
    }

    @Test
    fun testVoileV6CompleteDesignCriticalZoneAndConfinement() {
        // Voile V6 (pages 38 à 40) : lw = 1.40 m, bw = 0.30 m, Hw = 11.22 m
        val forcesV6 = listOf(
            StoryPierForces("H RDC", 0.00, 3.39, 2675.81, 750.37, 2317.83),
            StoryPierForces("H 1ER", 3.74, 3.39, 868.25, 507.72, 1145.75),
            StoryPierForces("TERRASSE", 7.48, 3.39, 226.83, 140.32, 274.26)
        )

        val reportV6 = designer.designShearWall(
            pierName = "V6",
            lwMeters = 1.40,
            bwMeters = 0.30,
            totalHeightHwMeters = 11.22,
            storyForces = forcesV6,
            totalStoriesN = 3
        )

        // 1. Élancement hw/lw = 11.22 / 1.40 = 8.01 > 2.0 (Voile élancé)
        assertTrue(reportV6.isSlenderWall)

        // 2. Hauteur critique hcr = max(1.4, 11.22/6 = 1.87) = 1.87 m (ou 3.74 m RDC)
        assertTrue(reportV6.criticalZoneHeightHcrMeters >= 1.87)

        // 3. Élément de rive confiné (lc = 60 cm, St = 8 cm)
        val riveGauche = reportV6.leftBoundary
        assertEquals(60.0, riveGauche.lcChosenCm, 0.1)
        assertEquals(8.0, riveGauche.hoopSpacingStCm, 0.1)
        assertTrue(riveGauche.isConfinementCompliant)
        assertTrue(riveGauche.isLocalDuctilityCompliant)

        // Ratio mécanique de confinement omega_wd >= 0.1969
        assertTrue(riveGauche.mechanicalConfinementRatioOmegaWd >= riveGauche.minOmegaWdRequired)

        // 4. Bilan global
        assertTrue(reportV6.isOverallCompliant)
    }
}
