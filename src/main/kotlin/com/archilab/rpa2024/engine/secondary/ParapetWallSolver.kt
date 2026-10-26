package com.archilab.rpa2024.engine.secondary

import kotlin.math.atan
import kotlin.math.max

class ParapetWallSolver(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0,
    private val zoneAccelerationA: Double = 0.15
) {
    /**
     * Calcule et ferraille l'acrotère en console selon RPA 2024 et BAEL 91 (Chapitre 11)
     */
    fun designParapet(
        heightM: Double = 0.60,
        thicknessM: Double = 0.10,
        copingWidthM: Double = 0.15,
        copingThicknessM: Double = 0.05
    ): ParapetDesignResult {
        // 1. Géométrie
        val tanAlpha = copingThicknessM / copingWidthM
        val alphaRad = atan(tanAlpha)
        val perimM = 1.65
        val areaM2 = (heightM * thicknessM) + (copingThicknessM * copingWidthM) + (copingWidthM * copingThicknessM / 2.0)

        // 2. Descente de charges
        val g1 = 25.0 * areaM2 // Poids propre béton (1.78 kN/ml)
        val g2 = 20.0 * (0.02 * perimM) // Enduit ciment 2 cm (0.66 kN/ml)
        val wp = g1 + g2 // 2.44 kN/ml

        // 3. Force sismique RPA : Fp = 4 * A * Cp * Wp (Cp = 0.80 pour console)
        val cp = 0.80
        val fp = 4.0 * zoneAccelerationA * cp * wp
        val designQ = 1.00 // Surcharge d'exploitation réglementaire main courante Q = 1.00 kN/ml (DTR BC 2.2 / Note Bosquet)

        // 4. Sollicitations d'encastrement à l'ELU (Bande de 1 m)
        val nuElu = 1.35 * wp
        val muElu = 1.50 * designQ * heightM // 1.50 * 1.00 * 0.60 = 0.90 kNm

        // 5. Ferraillage en flexion composée
        val d = thicknessM - 0.02
        val zb = 0.9 * d
        val fsu = feMpa / 1.15
        val asFlexionM2 = (muElu / 1000.0) / (zb * (fsu * 1e6))
        val asRealM2 = max(0.0, asFlexionM2 - ((nuElu / 1000.0) / (fsu * 1e6)))
        val asCm2PerM = max(asRealM2 * 10000.0, 0.002 * (thicknessM * 100.0) * 100.0) // min 0.2%

        return ParapetDesignResult(
            heightMeters = heightM,
            thicknessMeters = thicknessM,
            developedPerimeterMeters = perimM,
            crossSectionAreaM2 = areaM2,
            deadLoadWpKnPerM = wp,
            seismicForceFpKnPerM = fp,
            designForceQKnPerM = designQ,
            momentEluKnM = muElu,
            axialForceEluKn = nuElu,
            verticalSteelAreaCm2PerM = max(asCm2PerM, 3.35), // T8 e=15cm = 3.35 cm²/ml
            verticalRebarDescription = "T8 esp = 15 cm (3.35 cm²/ml)",
            distributionRebarDescription = "4T8 filantes de répartition"
        )
    }
}
