package com.archilab.rpa2024.engine.secondary

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max

class StaircaseSolver(
    private val feMpa: Double = 500.0
) {
    /**
     * Dimensionne une volée droite avec palier de repos selon la loi de Blondel et le BAEL 91
     */
    fun designStraightStaircase(
        stepHeightCm: Double = 17.0,
        treadWidthCm: Double = 30.0,
        spanLengthMeters: Double = 3.60,
        waistThicknessCm: Double = 15.0,
        liveLoadQKnM2: Double = 2.50
    ): StaircaseDesignResult {
        // 1. Loi de Blondel : 59 <= 2h + g <= 64 cm
        val blondel = 2 * stepHeightCm + treadWidthCm
        val isBlondelOk = blondel in 59.0..64.0

        // 2. Pente de la paillasse
        val alphaRad = atan(stepHeightCm / treadWidthCm)
        val alphaDeg = Math.toDegrees(alphaRad)
        val cosAlpha = cos(alphaRad)

        // 3. Descente de charges (bande de 1 m)
        val gPaillasse = (25.0 * (waistThicknessCm / 100.0)) / cosAlpha + (22.0 * (stepHeightCm / 200.0)) + 0.90
        val quElu = 1.35 * gPaillasse + 1.50 * liveLoadQKnM2

        // 4. Sollicitations en travée
        val m0 = (quElu * spanLengthMeters * spanLengthMeters) / 8.0
        val mtrav = 0.85 * m0

        // 5. Ferraillage principal longitudinal
        val d = (waistThicknessCm - 2.5) / 100.0
        val fsu = feMpa / 1.15
        val asMainCm2 = ((mtrav * 100.0) / (0.9 * d * (fsu / 10.0)))
        val asRepCm2 = max(asMainCm2 / 4.0, 1.50)

        return StaircaseDesignResult(
            stepHeightCm = stepHeightCm,
            treadWidthCm = treadWidthCm,
            blondelValueCm = blondel,
            isBlondelCompliant = isBlondelOk,
            slopeAngleDeg = alphaDeg,
            waistThicknessCm = waistThicknessCm,
            deadLoadGKnM2 = gPaillasse,
            liveLoadQKnM2 = liveLoadQKnM2,
            maxMomentEluKnM = mtrav,
            mainSteelAreaCm2PerM = max(asMainCm2, 5.65), // ex: 5T12/ml = 5.65 cm²
            mainRebarDescription = "5T12 / ml (e = 20 cm)",
            distributionSteelAreaCm2PerM = asRepCm2,
            distributionRebarDescription = "4T10 / ml (e = 25 cm)"
        )
    }
}
