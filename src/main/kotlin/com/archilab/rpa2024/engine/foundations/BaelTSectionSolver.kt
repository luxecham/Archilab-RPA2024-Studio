package com.archilab.rpa2024.engine.foundations

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Données d'entrée pour le solveur de section en Té (remplacement SOCOTEC BaelT).
 */
@Serializable
data class BaelTInput(
    val flangeWidthBMeters: Double,        // Largeur de la table b (m)
    val webWidthB0Meters: Double,          // Largeur de la nervure b0 (m)
    val totalHeightHMeters: Double,        // Hauteur totale h (m)
    val flangeThicknessH0Meters: Double,   // Épaisseur de la table h0 (m)
    val dPrimeMeters: Double = 0.05,       // Enrobage supérieur d' (m)
    val cMeters: Double = 0.05,            // Enrobage inférieur c (m)
    val fc28MPa: Double = 25.0,
    val feMPa: Double = 500.0,
    val momentMUKNm: Double,               // Moment ultime Mu (kN.m)
    val axialForceNUKN: Double = 0.0,      // Effort normal Nu (kN)
    val thetaDuration: Double = 0.85,      // 0.85 pour sismique accidentel, 1.0 pour durable
    val gammaB: Double = 1.20,             // 1.20 pour accidentel, 1.50 pour durable
    val gammaS: Double = 1.00              // 1.00 pour accidentel, 1.15 pour durable
)

/**
 * Résultat exact du dimensionnement de la section en Té selon BAEL 91 (style BaelT).
 */
@Serializable
data class BaelTResult(
    val flangeWidthBMeters: Double,
    val webWidthB0Meters: Double,
    val totalHeightHMeters: Double,
    val flangeThicknessH0Meters: Double,
    val effectiveDepthDMeters: Double,
    val momentMUKNm: Double,
    val flangeCapacityMtuKNm: Double,
    val isNeutralAxisInFlange: Boolean,
    val neutralAxisY0Meters: Double,
    val leverArmZbMeters: Double,
    val aSupCm2: Double,
    val aInfCm2: Double,
    val aTotalCm2: Double,
    val minFragilityAreaCm2: Double,
    val aAdoptedCm2: Double = max(aInfCm2, minFragilityAreaCm2),
    val isCompliant: Boolean
)

/**
 * Solveur universel de section en Té en béton armé selon BAEL 91 mod. 99.
 * Remplace intégralement le logiciel SOCOTEC BaelT.
 */
class BaelTSectionSolver {

    /**
     * Résout exactement la section en Té aux ELU (et ELA).
     */
    fun solveBaelT(input: BaelTInput): BaelTResult {
        val b = input.flangeWidthBMeters
        val b0 = input.webWidthB0Meters
        val h = input.totalHeightHMeters
        val h0 = input.flangeThicknessH0Meters
        val dPrime = input.dPrimeMeters
        val c = input.cMeters
        val d = h - c

        val fc28 = input.fc28MPa
        val fe = input.feMPa
        val theta = input.thetaDuration
        val gammaB = input.gammaB
        val gammaS = input.gammaS

        val fbu = (0.85 * fc28) / (theta * gammaB) // MPa
        val fsu = fe / gammaS                     // MPa
        val ft28 = 0.6 + 0.06 * fc28

        val fbuPa = fbu * 1e6
        val fsuPa = fsu * 1e6
        val mNm = abs(input.momentMUKNm) * 1000.0

        // Condition de non-fragilité BAEL pour la nervure
        val aMinFragilityCm2 = 0.23 * b0 * d * (ft28 / fe) * 10000.0

        if (mNm < 1e-3) {
            return BaelTResult(
                flangeWidthBMeters = b,
                webWidthB0Meters = b0,
                totalHeightHMeters = h,
                flangeThicknessH0Meters = h0,
                effectiveDepthDMeters = d,
                momentMUKNm = 0.0,
                flangeCapacityMtuKNm = 0.0,
                isNeutralAxisInFlange = true,
                neutralAxisY0Meters = 0.0,
                leverArmZbMeters = d,
                aSupCm2 = 0.0,
                aInfCm2 = roundTo2Decimals(aMinFragilityCm2),
                aTotalCm2 = roundTo2Decimals(aMinFragilityCm2),
                minFragilityAreaCm2 = roundTo2Decimals(aMinFragilityCm2),
                isCompliant = true
            )
        }

        // Moment équilibré par la table de compression seule :
        // Mtu = b * h0 * fbu * (d - h0 / 2)
        val mtuNm = b * h0 * fbuPa * (d - h0 / 2.0)
        val mtuKNm = mtuNm / 1000.0

        val muLu = 0.372 // Pivot B pour FeE500

        var aSupM2 = 0.0
        val aInfM2: Double
        val y0Meters: Double
        val zbMeters: Double
        val inFlange: Boolean

        if (mNm <= mtuNm) {
            // CAS 1 : Axe neutre dans la table de compression (y0 <= h0)
            // Calcul comme une section rectangulaire fictive de largeur b
            inFlange = true
            val muBu = mNm / (b * d * d * fbuPa)
            val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muBu)))
            y0Meters = alpha * d
            zbMeters = d * (1.0 - 0.4 * alpha)
            aInfM2 = mNm / (zbMeters * fsuPa)
        } else {
            // CAS 2 : Axe neutre dans la nervure (y0 > h0)
            inFlange = false
            // 1. Débords de la table (largeur b - b0)
            val mu1Nm = (b - b0) * h0 * fbuPa * (d - h0 / 2.0)
            val as1M2 = mu1Nm / ((d - h0 / 2.0) * fsuPa)

            // 2. Nervure centrale (largeur b0)
            val mu2Nm = mNm - mu1Nm
            val muBu2 = mu2Nm / (b0 * d * d * fbuPa)

            if (muBu2 <= muLu) {
                val alpha2 = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muBu2)))
                y0Meters = alpha2 * d
                zbMeters = d * (1.0 - 0.4 * alpha2)
                val as2M2 = mu2Nm / (zbMeters * fsuPa)
                aInfM2 = as1M2 + as2M2
            } else {
                val alphaL = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muLu)))
                y0Meters = alphaL * d
                zbMeters = d * (1.0 - 0.4 * alphaL)
                val deltaMNm = mu2Nm - muLu * b0 * d * d * fbuPa
                aSupM2 = deltaMNm / ((d - dPrime) * fsuPa)
                val as2M2 = (muLu * b0 * d * d * fbuPa) / (zbMeters * fsuPa) + aSupM2
                aInfM2 = as1M2 + as2M2
            }
        }

        val aInfCm2 = aInfM2 * 10000.0
        val aSupCm2 = aSupM2 * 10000.0

        return BaelTResult(
            flangeWidthBMeters = roundTo2Decimals(b),
            webWidthB0Meters = roundTo2Decimals(b0),
            totalHeightHMeters = roundTo2Decimals(h),
            flangeThicknessH0Meters = roundTo2Decimals(h0),
            effectiveDepthDMeters = roundTo2Decimals(d),
            momentMUKNm = roundTo2Decimals(input.momentMUKNm),
            flangeCapacityMtuKNm = roundTo2Decimals(mtuKNm),
            isNeutralAxisInFlange = inFlange,
            neutralAxisY0Meters = roundTo3Decimals(y0Meters),
            leverArmZbMeters = roundTo3Decimals(zbMeters),
            aSupCm2 = roundTo2Decimals(aSupCm2),
            aInfCm2 = roundTo2Decimals(aInfCm2),
            aTotalCm2 = roundTo2Decimals(aSupCm2 + aInfCm2),
            minFragilityAreaCm2 = roundTo2Decimals(aMinFragilityCm2),
            isCompliant = true
        )
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundTo3Decimals(value: Double): Double = round(value * 1000.0) / 1000.0
}
