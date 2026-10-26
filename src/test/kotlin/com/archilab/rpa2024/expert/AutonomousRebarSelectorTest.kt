package com.archilab.rpa2024.expert

import com.archilab.rpa2024.engine.geometry.CheckStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Cycle Copilot 1 : sélection commerciale minimale en poids et constructible. */
class AutonomousRebarSelectorTest {

    private val selector = AutonomousRebarSelector()

    @Test
    fun `un poteau selectionne une disposition symetrique et respecte les espacements`() {
        val selection = selector.selectColumn(
            widthCm = 40.0,
            depthCm = 40.0,
            coverCm = 4.0,
            requiredAsCm2 = 14.0,
            minimumRatio = 0.008,
            maximumRatio = 0.04,
            maximumSpacingCm = 25.0
        )
        assertEquals(CheckStatus.OK, selection.status)
        assertTrue(selection.areaCm2 >= 14.0)
        assertTrue(selection.ratioPercent >= 0.8)
        assertTrue(selection.totalBars >= 4)
        assertTrue(selection.spacingLongFaceCm <= 25.0 + 1e-9)
        assertTrue(selection.spacingShortFaceCm <= 25.0 + 1e-9)
        assertTrue(selection.tieSpacingCriticalCm <= 15.0 + 1e-9)
        assertTrue(selection.lapLengthCm > 0.0)
        assertTrue(selection.label.contains("T"))
    }

    @Test
    fun `le choix minimise l aire d acier parmi les alternatives`() {
        val selection = selector.selectColumn(
            widthCm = 35.0,
            depthCm = 45.0,
            coverCm = 4.0,
            requiredAsCm2 = 18.0,
            minimumRatio = 0.008,
            maximumRatio = 0.04,
            maximumSpacingCm = 20.0
        )
        assertEquals(CheckStatus.OK, selection.status)
        assertTrue(selection.alternatives.isNotEmpty())
        assertTrue(selection.alternatives.all { it.areaCm2 >= selection.areaCm2 - 1e-9 })
    }

    @Test
    fun `une poutre choisit filantes chapeaux et etriers`() {
        val selection = selector.selectBeam(
            widthCm = 30.0,
            depthCm = 50.0,
            coverCm = 4.0,
            requiredBottomAsCm2 = 8.0,
            requiredTopAsCm2 = 10.0,
            minimumRatio = 0.005,
            maximumRatio = 0.04,
            requiredStirrupAreaCm2PerM = 2.0
        )
        assertEquals(CheckStatus.OK, selection.status, selection.warnings.joinToString())
        assertTrue(selection.bottomAreaCm2 >= 8.0)
        assertTrue(selection.topAreaCm2 >= 10.0)
        assertTrue(selection.bottomLabel.contains("filantes"))
        assertTrue(selection.topLabel.contains("filantes"))
        assertTrue(selection.stirrupDiameterMm >= 8)
        assertTrue(selection.stirrupSpacingCriticalCm <= 20.0)
        assertTrue(selection.isConstructible)
    }

    @Test
    fun `une section impossible est declaree non constructible`() {
        val selection = selector.selectBeam(
            widthCm = 20.0,
            depthCm = 30.0,
            coverCm = 4.0,
            requiredBottomAsCm2 = 40.0,
            requiredTopAsCm2 = 40.0,
            minimumRatio = 0.005,
            maximumRatio = 0.04
        )
        assertEquals(CheckStatus.FAILED, selection.status)
        assertFalse(selection.isConstructible)
        assertNotNull(selection.warnings)
    }
}
