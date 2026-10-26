package com.archilab.rpa2024

import com.archilab.rpa2024.engine.slabs.ShellElementMoments
import com.archilab.rpa2024.engine.slabs.WoodArmerBendingSolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WoodArmerBendingSolverTest {

    private val solver = WoodArmerBendingSolver()

    @Test
    fun testWoodArmerTransformationPureBendingAndTwisting() {
        // Cas classique Wood-Armer : Mx = 35 kN.m/m, My = 20 kN.m/m, Mxy = 15 kN.m/m
        val input = ShellElementMoments(
            elementId = "SHELL_01",
            mxKNmPerM = 35.0,
            myKNmPerM = 20.0,
            mxyKNmPerM = 15.0
        )

        val result = solver.computeWoodArmerMoments(input)

        // Mxd,bot = 35 + 15 = 50 kN.m/m
        assertEquals(50.0, result.bottomMxdKNmPerM, 0.1)
        // Myd,bot = 20 + 15 = 35 kN.m/m
        assertEquals(35.0, result.bottomMydKNmPerM, 0.1)
        // En nappe supérieure (moments négatifs), Mx - |Mxy| = 35 - 15 = 20 > 0, donc aucun acier supérieur
        assertEquals(0.0, result.topMxdKNmPerM)
        assertEquals(0.0, result.topMydKNmPerM)
    }

    @Test
    fun testWoodArmerSlabPanelCompleteDesign() {
        val grid = listOf(
            ShellElementMoments("E1", mxKNmPerM = 32.0, myKNmPerM = 22.0, mxyKNmPerM = 8.0),
            ShellElementMoments("E2", mxKNmPerM = -25.0, myKNmPerM = -18.0, mxyKNmPerM = 12.0),
            ShellElementMoments("E3", mxKNmPerM = 40.0, myKNmPerM = 28.0, mxyKNmPerM = 14.0),
            ShellElementMoments("E4", mxKNmPerM = -35.0, myKNmPerM = -26.0, mxyKNmPerM = 10.0)
        )

        val report = solver.designTwoWaySlabPanel(
            panelId = "PANEL_DALLE_TRANSFERT_01",
            thicknessHMeters = 0.25,
            spanXMeters = 5.40,
            spanYMeters = 4.80,
            momentsGrid = grid,
            concreteGrade = "C25/30",
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        assertNotNull(report)
        assertEquals("CONFORME WOOD-ARMER & BAEL 91", report.globalStatus)
        assertTrue(report.maxBottomMxdKNmPerM >= 40.0)
        assertTrue(report.maxTopMxdKNmPerM >= 35.0)

        // Nappe inférieure
        assertTrue(report.bottomReinforcement.directionXAreaCm2PerM > 0.0)
        assertTrue(report.bottomReinforcement.directionYAreaCm2PerM > 0.0)
        assertTrue(report.bottomReinforcement.isCompliant)

        // Nappe supérieure
        assertTrue(report.topReinforcement.directionXAreaCm2PerM > 0.0)
        assertTrue(report.topReinforcement.directionYAreaCm2PerM > 0.0)
        assertTrue(report.topReinforcement.isCompliant)

        assertTrue(report.totalSteelRatioKgPerM2 > 10.0)
    }
}
