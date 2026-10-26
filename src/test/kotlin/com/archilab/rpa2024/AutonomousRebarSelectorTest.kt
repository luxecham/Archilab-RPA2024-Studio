package com.archilab.rpa2024

import com.archilab.rpa2024.engine.frames.AutonomousRebarSelector
import com.archilab.rpa2024.normative.SeismicZone
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AutonomousRebarSelectorTest {

    private val selector = AutonomousRebarSelector()

    @Test
    @DisplayName("Poteau 35x35 cm en Zone III : respect de 0.9% Bc et espacement <= 20 cm")
    fun testColumn35x35ZoneIII() {
        // Section Bc = 35x35 = 1225 cm²
        // Min RPA Zone III = 0.9% * 1225 = 11.025 cm²
        val res = selector.selectOptimalColumnRebars(
            widthBCm = 35.0,
            heightHCm = 35.0,
            requiredCalculatedAreaCm2 = 8.50,
            zone = SeismicZone.ZONE_III
        )

        assertNotNull(res)
        assertTrue(res.totalAreaCm2 >= 11.025, "La section adoptée doit dépasser le min RPA (11.025 cm²)")
        assertEquals(0.90, res.minRpaPercentage, 0.01)
        assertTrue(res.isSpacingCompliant, "L'espacement doit respecter <= 20 cm")
        assertTrue(res.isRatioCompliant, "Le ratio d'armatures doit être conforme")
        assertTrue(res.minClearSpacingCm >= 2.5, "L'espacement libre doit permettre le passage du granulat")
        assertEquals(res.totalBarCount, res.rebarCoordinates.size, "Chaque barre doit avoir ses coordonnées CAO")
    }

    @Test
    @DisplayName("Poteau 40x50 cm en Zone II : panachage bimétrique ou régulier avec As >= 0.8% Bc")
    fun testColumn40x50ZoneII() {
        // Section Bc = 40x50 = 2000 cm²
        // Min RPA Zone II = 0.8% * 2000 = 16.00 cm²
        val res = selector.selectOptimalColumnRebars(
            widthBCm = 40.0,
            heightHCm = 50.0,
            requiredCalculatedAreaCm2 = 14.50,
            zone = SeismicZone.ZONE_II
        )

        assertNotNull(res)
        assertTrue(res.totalAreaCm2 >= 16.00)
        assertEquals(0.80, res.minRpaPercentage, 0.01)
        assertTrue(res.isSpacingCompliant)
        assertTrue(res.maxAxisSpacingCm <= 20.0, "En Zone II, espacement <= 20 cm")
        assertTrue(res.totalBarCount in listOf(8, 10, 12, 14, 16))
    }

    @Test
    @DisplayName("Poteau fortement sollicité As_cal = 28.5 cm² : adaptation automatique de barres lourdes")
    fun testHeavyColumn50x50() {
        val res = selector.selectOptimalColumnRebars(
            widthBCm = 50.0,
            heightHCm = 50.0,
            requiredCalculatedAreaCm2 = 28.50,
            zone = SeismicZone.ZONE_III
        )

        assertNotNull(res)
        assertTrue(res.totalAreaCm2 >= 28.50)
        assertTrue(res.isSpacingCompliant)
        assertTrue(res.isRatioCompliant)
        assertTrue(res.suggestedTiesDescription.contains("4 brins"))
    }

    @Test
    @DisplayName("Poutre Principale 30x55 cm : répartition travée et appui avec vérification A_inf >= 0.50 A_sup")
    fun testMainBeam30x55() {
        val res = selector.selectOptimalBeamRebars(
            beamName = "P.Prin 30x55",
            widthBCm = 30.0,
            heightHCm = 55.0,
            spanLnMeters = 4.35,
            requiredSpanAreaCm2 = 6.50,
            requiredSupportAreaCm2 = 9.10
        )

        assertNotNull(res)
        assertEquals(8.25, res.minRpaAreaCm2, 0.01) // 0.5% * 30 * 55 = 8.25 cm²
        assertEquals("3T14", res.spanReinforcement.filantesDescription)
        assertEquals("2T12", res.spanReinforcement.chapeauxDescription)
        assertEquals("3T14", res.supportReinforcement.chapeauxDescription)

        // Règle RPA § 7.5.2.1 : A_inf >= 0.50 * A_sup sur appui
        assertTrue(res.isSeismicInversionCompliant, "La condition A_inf >= 0.50 A_sup doit être vérifiée")
        assertTrue(res.ratioInfOverSupAtSupport >= 0.49)

        // Zone nodale : St <= min(h/4, 12*phi, 10) = min(13.75, 14.4, 10) = 8 ou 10 cm
        assertTrue(res.nodalSpacingStCm in listOf(8.0, 10.0))
        assertEquals(5.0, res.firstStirrupOffsetCm)
    }

    @Test
    @DisplayName("Poutre longue portée 35x70 cm à forte charge : chapeaux renforcés et 2 lits")
    fun testLongSpanHeavyBeam35x70() {
        val res = selector.selectOptimalBeamRebars(
            beamName = "P.Prin 35x70",
            widthBCm = 35.0,
            heightHCm = 70.0,
            spanLnMeters = 6.80,
            requiredSpanAreaCm2 = 15.50,
            requiredSupportAreaCm2 = 21.00
        )

        assertNotNull(res)
        assertTrue(res.spanReinforcement.totalAdoptedAreaCm2 >= 15.50)
        assertTrue(res.supportReinforcement.totalAdoptedAreaCm2 >= 21.00)
        assertTrue(res.spanReinforcement.isClearSpacingCompliant)
        assertTrue(res.supportReinforcement.isClearSpacingCompliant)
        assertTrue(res.nodalZoneLengthLPrimeMeters >= 1.40) // 2h = 1.40 m
    }
}
