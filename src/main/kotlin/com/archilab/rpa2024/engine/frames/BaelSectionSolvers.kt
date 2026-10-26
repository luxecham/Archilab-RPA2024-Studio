package com.archilab.rpa2024.engine.frames

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class BaelSectionSolvers(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0,
    private val theta: Double = 0.85
) {
    /**
     * Solveur de Flexion Composée Rectangulaire à l'ELU (Émulation SOCOTEC BaelR)
     */
    fun solveFlexionComposee(
        bMeters: Double,
        hMeters: Double,
        cMeters: Double,
        nuKn: Double,       // > 0 en compression, < 0 en traction
        muKnM: Double,      // Moment fléchissant par rapport au centre de gravité du béton seul
        gammaB: Double = 1.20,
        gammaS: Double = 1.00
    ): FlexionComposeeResult {
        val d = hMeters - cMeters
        val dPrime = cMeters
        val fbu = (0.85 * fc28Mpa) / (theta * gammaB) // MPa
        val fsu = feMpa / gammaS                      // MPa

        val nuMpaM2 = nuKn / 1000.0
        val muMpaM3 = muKnM / 1000.0

        // 1. Excentricité de la charge
        val e0 = if (abs(nuKn) > 1e-4) muKnM / nuKn else 0.0

        // 2. Moment fictif au CDG des aciers tendus A_inf
        val muA = muKnM + nuKn * (d - hMeters / 2.0)
        val muAMpaM3 = muA / 1000.0

        // 3. Cas de la Section Entièrement Tendue (SET)
        if (nuKn < 0 && abs(e0) <= (hMeters / 2.0 - dPrime)) {
            val aInfM2 = abs(nuMpaM2) * (d - hMeters / 2.0 + e0) / ((d - dPrime) * fsu)
            val aSupM2 = abs(nuMpaM2) * (d - hMeters / 2.0 - e0) / ((d - dPrime) * fsu)
            return FlexionComposeeResult(
                aSupCm2 = aSupM2 * 10000.0,
                aInfCm2 = aInfM2 * 10000.0,
                neutralAxisY0Meters = 0.0,
                muBu = 0.0,
                stateDescription = "Section Entièrement Tendue (SET)"
            )
        }

        // 4. Section Partiellement Comprimée (SPC)
        val denom = bMeters * d * d * fbu
        val muBu = if (denom > 0.0) abs(muAMpaM3) / denom else 0.0

        return if (muBu <= 0.371) {
            val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muBu)))
            val y0 = alpha * d
            val zb = d * (1.0 - 0.4 * alpha)
            val aFlexionSimpleM2 = abs(muAMpaM3) / (zb * fsu)
            val aInfM2 = max(0.0, aFlexionSimpleM2 - (nuMpaM2 / fsu))
            FlexionComposeeResult(
                aSupCm2 = 0.0,
                aInfCm2 = aInfM2 * 10000.0,
                neutralAxisY0Meters = y0,
                muBu = muBu,
                stateDescription = "Section Partiellement Comprimée sans aciers comprimés (Pivot B)"
            )
        } else {
            // Aciers comprimés requis
            val alphaLu = 0.668
            val y0 = alphaLu * d
            val zbLu = d * (1.0 - 0.4 * alphaLu)
            val muLu = 0.371
            val muResMpaM3 = muLu * bMeters * d * d * fbu
            val deltaMuMpaM3 = abs(muAMpaM3) - muResMpaM3
            val aSupM2 = deltaMuMpaM3 / ((d - dPrime) * fsu)
            val aInfM2 = (muResMpaM3 / (zbLu * fsu)) + aSupM2 - (nuMpaM2 / fsu)
            FlexionComposeeResult(
                aSupCm2 = aSupM2 * 10000.0,
                aInfCm2 = aInfM2 * 10000.0,
                neutralAxisY0Meters = y0,
                muBu = muBu,
                stateDescription = "Section avec aciers comprimés (Pivot C)"
            )
        }
    }

    /**
     * Solveur de Flexion Simple Rectangulaire à l'ELU (Poutres)
     */
    fun solveFlexionSimple(
        bMeters: Double,
        hMeters: Double,
        cMeters: Double,
        muKnM: Double,
        gammaB: Double = 1.50,
        gammaS: Double = 1.15
    ): FlexionSimpleResult {
        val d = hMeters - cMeters
        val fbu = (0.85 * fc28Mpa) / (theta * gammaB)
        val fsu = feMpa / gammaS

        val muMpaM3 = abs(muKnM) / 1000.0
        val muBu = muMpaM3 / (bMeters * d * d * fbu)
        val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muBu)))
        val y0 = alpha * d
        val zb = d * (1.0 - 0.4 * alpha)
        val aInfM2 = muMpaM3 / (zb * fsu)

        return FlexionSimpleResult(
            aInfCm2 = aInfM2 * 10000.0,
            neutralAxisY0Meters = y0,
            muBu = muBu,
            zMeters = zb
        )
    }
}
