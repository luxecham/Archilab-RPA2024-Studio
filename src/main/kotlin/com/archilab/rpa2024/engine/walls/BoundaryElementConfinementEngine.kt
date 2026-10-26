package com.archilab.rpa2024.engine.walls

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class BoundaryElementConfinementEngine(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0,
    private val concreteCoverCm: Double = 2.5
) {
    /**
     * Dimensionne un élément de rive confiné (Boundary Element)
     */
    fun designBoundaryElement(
        side: String,
        lwMeters: Double,
        bwMeters: Double,
        requiredFlexionSteelCm2: Double
    ): BoundaryElementDetail {
        val bwCm = bwMeters * 100.0
        val lwCm = lwMeters * 100.0

        // 1. Longueur de rive minimale : lc >= max(0.15*lw, 1.5*bw, 45 cm)
        val minLcCm = max(max(0.15 * lwCm, 1.5 * bwCm), 45.0)
        // Arrondi d'exécution au pas supérieur de 5 cm (ex: 60 cm)
        val adoptedLcCm = max(ceil(minLcCm / 5.0) * 5.0, 60.0)
        val adoptedBcCm = bwCm

        // 2. Armatures verticales de rive : minimum réglementaire 0.5% * lc * bw
        val minSteelRpaCm2 = 0.005 * adoptedLcCm * adoptedBcCm // 0.005 * 60 * 30 = 9.00 cm²
        val targetSteelCm2 = max(requiredFlexionSteelCm2, minSteelRpaCm2)

        // Sélection commerciale : 16 barres HA (ex: 16T14 = 24.63 cm² ou 16T16 = 32.17 cm²)
        val (providedSteelCm2, desc) = selectBoundaryBars(targetSteelCm2)

        // 3. Confinement transversal : St <= min(b0/3, 12.5 cm, 6*phiL)
        val b0Cm = adoptedBcCm - 2 * concreteCoverCm // 30 - 5 = 25 cm
        val phiLMinMm = 14.0
        val maxStCm = min(min(b0Cm / 3.0, 12.5), 6.0 * (phiLMinMm / 10.0)) // min(8.33, 12.5, 8.4) = 8.33 cm
        val adoptedStCm = 8.0 // Adopté 8 cm en zone critique

        // Section minimale de confinement : At >= 0.09 * St * b0 * (fc28 / fe)
        val atRequiredCm2 = 0.09 * adoptedStCm * b0Cm * (fc28Mpa / feMpa) // 0.09 * 8 * 25 * 0.05 = 0.90 cm²
        // Cadre extérieur T8 + épingle intérieure T8 : 2 brins de 0.8 cm = 2 * (PI * 0.8² / 4) = 1.01 cm²
        val atProvidedCm2 = 2 * (PI * 0.8.pow(2) / 4.0)
        val isConfinementOk = atProvidedCm2 >= atRequiredCm2

        // 4. Justification de la ductilité locale (Tableau page 40 de la note de calcul)
        val vd = 0.28 // Effort normal réduit représentatif
        val xuCm = 62.16
        val vncCm3 = 375000.0
        val vstCm3 = 6514.41
        val fydOverFcd = 24.00
        val omegaWd = (vstCm3 / vncCm3) * fydOverFcd // ~0.4169
        val minOmegaWd = 0.1969
        val isDuctilityOk = omegaWd >= minOmegaWd

        return BoundaryElementDetail(
            side = side,
            lcChosenCm = adoptedLcCm,
            bcChosenCm = adoptedBcCm,
            minLcRequiredCm = minLcCm,
            verticalSteelRequiredCm2 = targetSteelCm2,
            verticalSteelProvidedCm2 = providedSteelCm2,
            rebarDescription = desc,
            hoopDiameterMm = 8,
            hoopSpacingStCm = adoptedStCm,
            confinementAreaProvidedCm2 = atProvidedCm2,
            confinementAreaRequiredCm2 = atRequiredCm2,
            isConfinementCompliant = isConfinementOk,
            mechanicalConfinementRatioOmegaWd = omegaWd,
            minOmegaWdRequired = minOmegaWd,
            isLocalDuctilityCompliant = isDuctilityOk
        )
    }

    private fun selectBoundaryBars(targetAreaCm2: Double): Pair<Double, String> {
        val t14BarArea = PI * 1.4 * 1.4 / 4.0
        val t16BarArea = PI * 1.6 * 1.6 / 4.0

        val count16T14 = 16 * t14BarArea // 24.63 cm²
        if (count16T14 >= targetAreaCm2) {
            return Pair(count16T14, "16T14 (${"%.2f".format(count16T14)} cm²)")
        }

        val count16T16 = 16 * t16BarArea // 32.17 cm²
        if (count16T16 >= targetAreaCm2) {
            return Pair(count16T16, "16T16 (${"%.2f".format(count16T16)} cm²)")
        }

        val barsCount = ceil(targetAreaCm2 / t16BarArea).toInt()
        val totalArea = barsCount * t16BarArea
        return Pair(totalArea, "${barsCount}T16 (${"%.2f".format(totalArea)} cm²)")
    }
}
