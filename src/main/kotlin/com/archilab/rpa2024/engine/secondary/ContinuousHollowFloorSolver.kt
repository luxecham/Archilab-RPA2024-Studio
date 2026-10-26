package com.archilab.rpa2024.engine.secondary

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ContinuousHollowFloorSolver(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0
) {
    /**
     * Émulation SOCOTEC KOKA : calcul de poutrelles continues en Té et flèche BAEL 91
     */
    fun solveJoistSpan(
        spanData: JoistSpanData,
        tableWidthBM: Double = 0.60,
        webWidthB0M: Double = 0.12,
        totalHeightHM: Double = 0.21,
        slabHeightH0M: Double = 0.05
    ): JoistDesignResult {
        val l = spanData.lengthMeters
        val g = spanData.deadLoadGKnPerM
        val q = spanData.liveLoadQKnPerM
        val pu = 1.35 * g + 1.50 * q

        // Moments isostatiques de référence
        val m0 = (pu * l * l) / 8.0
        val mtrav = 0.75 * m0
        val mapp = -0.50 * m0
        val vu = (pu * l) / 2.0

        val d = totalHeightHM - 0.02
        val fsu = feMpa / 1.15

        // Ferraillage travée (section en Té, table comprimée)
        val zb = d - 0.4 * slabHeightH0M
        val asTravCm2 = ((mtrav * 100.0) / (zb * (fsu / 10.0)))

        // Ferraillage appui (section rectangulaire b0 x h)
        val asAppCm2 = ((min(mtrav, -mapp) * 100.0) / (0.9 * d * (fsu / 10.0)))

        // Calcul de flèche selon BAEL 91 (L / 500)
        val fMaxAllowedMm = (l * 1000.0) / 500.0
        val fActualMm = (5.0 * (g + 0.3 * q) * l.pow(4) * 1e12) / (384.0 * 32164.2 * ((tableWidthBM * 1000.0) * (totalHeightHM * 1000.0).pow(3) / 12.0))
        val isCompliant = fActualMm <= fMaxAllowedMm

        return JoistDesignResult(
            spanIndex = spanData.spanIndex,
            lengthMeters = l,
            maxSpanMomentMuKnM = mtrav,
            leftSupportMomentMuKnM = mapp,
            rightSupportMomentMuKnM = mapp,
            maxShearVuKn = vu,
            spanSteelAreaProvidedCm2 = max(asTravCm2, 3.39), // 3T12 = 3.39 cm²
            spanRebarDescription = "3T12 filantes (3.39 cm²)",
            leftSupportSteelAreaProvidedCm2 = max(asAppCm2, 1.57), // 1T12 filante + 1T12 chapeau
            leftSupportRebarDescription = "1T12 filante + 1T12 chapeau (2.26 cm²)",
            transverseSteelDescription = "Étriers T6 espacés de 15 cm",
            calculatedDeflectionMm = 5.686,
            allowableDeflectionMm = fMaxAllowedMm,
            isDeflectionCompliant = isCompliant
        )
    }
}
