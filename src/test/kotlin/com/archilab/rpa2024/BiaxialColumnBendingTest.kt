package com.archilab.rpa2024

import com.archilab.rpa2024.engine.frames.BiaxialColumnBendingSolver
import com.archilab.rpa2024.engine.frames.DesignSituation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BiaxialColumnBendingTest {

    private val biaxialSolver = BiaxialColumnBendingSolver()

    @Test
    @DisplayName("Poteau courant en flexion quasi-uniaxiale : M2 négligeable devant M3")
    fun testUniaxialDominance() {
        val res = biaxialSolver.evaluateBiaxialBending(
            widthBCm = 35.0,
            heightHCm = 45.0,
            axialForceNKN = 800.0,
            momentM2KNm = 3.0,
            momentM3KNm = 65.0,
            adoptedRebarAreaCm2 = 14.50,
            situation = DesignSituation.ACCIDENTAL_SEISMIC
        )

        assertNotNull(res)
        assertFalse(res.isBiaxialSignificant, "Le moment M2 < 10% M3, régime uniaxial prépondérant")
        assertEquals("Axe fort (Z-Z)", res.governingAxis)
        assertTrue(res.isBreslerCompliant)
        assertTrue(res.breslerInteractionRatio < 0.85)
    }

    @Test
    @DisplayName("Poteau d'angle en bi-flexion déviée sismique (N=1100 kN, M2=55 kNm, M3=80 kNm)")
    fun testCornerColumnBiaxialSeismic() {
        val res = biaxialSolver.evaluateBiaxialBending(
            widthBCm = 40.0,
            heightHCm = 50.0,
            axialForceNKN = 1100.0,
            momentM2KNm = 55.0,
            momentM3KNm = 80.0,
            adoptedRebarAreaCm2 = 24.63, // 16T14
            situation = DesignSituation.ACCIDENTAL_SEISMIC
        )

        assertNotNull(res)
        assertTrue(res.isBiaxialSignificant, "Les deux moments sont significatifs -> bi-flexion active")
        assertTrue(res.mEquivalentKNm > 80.0, "Le moment équivalent doit intégrer la composante M2")
        assertTrue(res.breslerAlphaExponent in 1.00..1.60, "L'exposant de Bresler doit être compris entre 1.0 et 1.6")
        assertTrue(res.resistantMomentM2RdKNm > 60.0, "Capacité résistante M2Rd")
        assertTrue(res.resistantMomentM3RdKNm > 90.0, "Capacité résistante M3Rd")
        assertTrue(res.isBreslerCompliant, "Section 16T14 suffisante pour reprendre la bi-flexion")
        assertTrue(res.safetyMarginPercent > 0.0)
    }

    @Test
    @DisplayName("Poteau sous-armé en bi-flexion extrême : détection de dépassement du critère de Bresler")
    fun testUnderReinforcedColumnBiaxialExceedance() {
        val res = biaxialSolver.evaluateBiaxialBending(
            widthBCm = 35.0,
            heightHCm = 35.0,
            axialForceNKN = 1500.0, // Très forte compression
            momentM2KNm = 95.0,     // Fort moment M2
            momentM3KNm = 110.0,    // Fort moment M3
            adoptedRebarAreaCm2 = 6.16, // Seulement 4T14 (gravement sous-armé)
            situation = DesignSituation.DURABLE_ELU
        )

        assertNotNull(res)
        assertTrue(res.isBiaxialSignificant)
        assertFalse(res.isBreslerCompliant, "Une section sous-armée de 4T14 sous bi-flexion extrême doit échouer au test de Bresler")
        assertTrue(res.breslerInteractionRatio > 1.00, "Le ratio d'interaction de Bresler doit dépasser 1.00")
        assertTrue(res.safetyMarginPercent < 0.0, "La marge de sécurité doit être négative")
    }
}
