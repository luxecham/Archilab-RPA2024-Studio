package com.archilab.rpa2024.engine.frames

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Résultat complet de la vérification en bi-flexion déviée d'un poteau (Bresler / CBA 93 / BAEL 91).
 */
@Serializable
data class BiaxialBendingCheckResult(
    val axialForceNKN: Double,
    val momentM2KNm: Double,
    val momentM3KNm: Double,
    val isBiaxialSignificant: Boolean,         // True si les deux moments agissent simultanément
    val mEquivalentKNm: Double,                // Moment équivalent uniaxial selon CBA 93 / BAEL 91
    val governingAxis: String,                 // "Axe fort (Z-Z)" ou "Axe faible (Y-Y)"
    val requiredBiaxialAreaAsCm2: Double,      // Section d'armatures requise pour reprendre la bi-flexion
    val resistantMomentM2RdKNm: Double,        // Capacité résistante en flexion pure/composée sens 2
    val resistantMomentM3RdKNm: Double,        // Capacité résistante en flexion pure/composée sens 3
    val breslerAlphaExponent: Double,          // Exposant de l'ellipse de Bresler (1.00 à 1.60)
    val breslerInteractionRatio: Double,       // (M2/M2Rd)^alpha + (M3/M3Rd)^alpha <= 1.00
    val isBreslerCompliant: Boolean,           // Ratio Bresler <= 1.00
    val safetyMarginPercent: Double            // Marge de sécurité = (1.0 - ratio) * 100%
) {
    val breslerRatio: Double get() = breslerInteractionRatio
    val nKN: Double get() = axialForceNKN
    val m2KNm: Double get() = momentM2KNm
    val m3KNm: Double get() = momentM3KNm
    val m2RdKNm: Double get() = resistantMomentM2RdKNm
    val m3RdKNm: Double get() = resistantMomentM3RdKNm
    val breslerAlpha: Double get() = breslerAlphaExponent
    val equivalentMomentMeqKNm: Double get() = mEquivalentKNm
}

/**
 * Solveur avancé de bi-flexion déviée (Biaxial Bending) pour poteaux d'angles et de rives.
 * Conforme au CBA 93, au BAEL 91 (Fascicule 62 Titre 1er) et aux recommandations RPA 2024.
 * Combine la méthode du moment équivalent CBA 93 et le critère d'interaction de Bresler (EC2 § 5.8.9).
 */
class BiaxialColumnBendingSolver(
    private val baelSolver: BaelRectangularSolver = BaelRectangularSolver()
) {

    /**
     * Analyse et vérifie la bi-flexion d'une section rectangulaire sous sollicitations (N, M2, M3).
     *
     * @param widthBCm Largeur b (axe 2 / Y) en cm
     * @param heightHCm Hauteur h (axe 3 / Z) en cm
     * @param axialForceNKN Effort normal N en kN (positif = compression)
     * @param momentM2KNm Moment fléchissant M2 en kN.m
     * @param momentM3KNm Moment fléchissant M3 en kN.m
     * @param adoptedRebarAreaCm2 Section totale d'armatures réelles adoptées dans le poteau (cm²)
     * @param fc28MPa Résistance caractéristique du béton (MPa)
     * @param feMPa Limite d'élasticité de l'acier (MPa)
     * @param situation Situation de calcul (ELU ou Sismique ELA)
     */
    fun evaluateBiaxialBending(
        widthBCm: Double,
        heightHCm: Double,
        axialForceNKN: Double,
        momentM2KNm: Double,
        momentM3KNm: Double,
        adoptedRebarAreaCm2: Double,
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0,
        situation: DesignSituation = DesignSituation.ACCIDENTAL_SEISMIC
    ): BiaxialBendingCheckResult {
        val bMeters = widthBCm / 100.0
        val hMeters = heightHCm / 100.0
        val absN = abs(axialForceNKN)
        val absM2 = abs(momentM2KNm)
        val absM3 = abs(momentM3KNm)

        val (gammaB, gammaS) = when (situation) {
            DesignSituation.DURABLE_ELU -> Pair(1.50, 1.15)
            DesignSituation.ACCIDENTAL_SEISMIC -> Pair(1.20, 1.00)
            DesignSituation.SERVICE_ELS -> Pair(1.00, 1.00)
        }

        val fcd = (0.85 * fc28MPa) / gammaB
        val fyd = feMPa / gammaS

        // Détection du régime bi-axial significatif (les deux moments dépassent 10% de leur max)
        val maxM = max(absM2, absM3)
        val minM = min(absM2, absM3)
        val isBiaxial = maxM > 5.0 && (minM / maxM) >= 0.10

        // 1. MÉTHODE DU MOMENT ÉQUIVALENT UNIAXIAL SELON CBA 93 / BAEL 91
        // Coefficient d'interaction beta = 0.65
        val beta = 0.65
        val ratio3 = if (hMeters > 0) absM3 / hMeters else 0.0
        val ratio2 = if (bMeters > 0) absM2 / bMeters else 0.0

        val (mEq, governingAxis, bEq, hEq) = if (ratio3 >= ratio2) {
            // L'axe 3 (Z-Z) est prépondérant
            val meq = absM3 + beta * (hMeters / bMeters) * absM2
            Tuple4(meq, "Axe fort (Z-Z)", bMeters, hMeters)
        } else {
            // L'axe 2 (Y-Y) est prépondérant
            val meq = absM2 + beta * (bMeters / hMeters) * absM3
            Tuple4(meq, "Axe faible (Y-Y)", hMeters, bMeters)
        }

        // Résolution de la section équivalente avec le solveur BAEL 91
        val resEq = baelSolver.solve(
            BaelRInput(
                widthBMeters = bEq,
                heightHMeters = hEq,
                dPrimeMeters = 0.03,
                cMeters = 0.03,
                fc28MPa = fc28MPa,
                feMPa = feMPa,
                axialForceNKN = axialForceNKN,
                momentMKNm = mEq,
                situation = situation
            )
        )
        val requiredAsBiaxial = resEq.aTotalCm2

        // 2. CRITÈRE D'INTERACTION DE BRESLER (ELLIPSE DE RUPTURE)
        // Capacité axiale pure de la section NRd
        val acM2 = bMeters * hMeters
        val asM2 = (adoptedRebarAreaCm2 / 10000.0)
        val nRdKN = (0.85 * acM2 * fcd * 1000.0) + (asM2 * fyd * 1000.0)
        val nu = if (nRdKN > 0) min(1.0, absN / nRdKN) else 0.20

        // Exposant alpha de Bresler selon Eurocode 2 § 5.8.9 :
        // Pour section rectangulaire : alpha dépend de nu = N_Ed / N_Rd
        // nu <= 0.10 => alpha = 1.00
        // nu >= 0.70 => alpha = 1.50
        // interpolation linéaire entre 0.10 et 0.70
        val alpha = when {
            nu <= 0.10 -> 1.00
            nu >= 0.70 -> 1.50
            else -> 1.00 + (1.50 - 1.00) * ((nu - 0.10) / 0.60)
        }

        // Estimation analytique des moments résistants MRd2 et MRd3 sous l'effort N appliqué
        // Pour une section avec aciers adoptés As symétriques
        val m3Rd = computeSectionResistantMoment(bMeters, hMeters, absN, adoptedRebarAreaCm2, fcd, fyd)
        val m2Rd = computeSectionResistantMoment(hMeters, bMeters, absN, adoptedRebarAreaCm2, fcd, fyd)

        // Ratio de Bresler : (M2 / M2Rd)^alpha + (M3 / M3Rd)^alpha
        val term2 = if (m2Rd > 0) (absM2 / m2Rd).pow(alpha) else 0.0
        val term3 = if (m3Rd > 0) (absM3 / m3Rd).pow(alpha) else 0.0
        val breslerRatio = term2 + term3
        val isBreslerOk = breslerRatio <= 1.00
        val margin = (1.00 - breslerRatio) * 100.0

        return BiaxialBendingCheckResult(
            axialForceNKN = roundTo2Decimals(axialForceNKN),
            momentM2KNm = roundTo2Decimals(momentM2KNm),
            momentM3KNm = roundTo2Decimals(momentM3KNm),
            isBiaxialSignificant = isBiaxial,
            mEquivalentKNm = roundTo2Decimals(mEq),
            governingAxis = governingAxis,
            requiredBiaxialAreaAsCm2 = roundTo2Decimals(requiredAsBiaxial),
            resistantMomentM2RdKNm = roundTo2Decimals(m2Rd),
            resistantMomentM3RdKNm = roundTo2Decimals(m3Rd),
            breslerAlphaExponent = roundTo2Decimals(alpha),
            breslerInteractionRatio = roundTo2Decimals(breslerRatio),
            isBreslerCompliant = isBreslerOk,
            safetyMarginPercent = roundTo2Decimals(margin)
        )
    }

    /**
     * Calcule le moment résistant de flexion composée d'une section rectangulaire
     * armée symétriquement de As sous effort axial N (Modèle bloc rectangulaire BAEL/EC2).
     */
    private fun computeSectionResistantMoment(
        b: Double,
        h: Double,
        nKN: Double,
        asCm2: Double,
        fcdMPa: Double,
        fydMPa: Double
    ): Double {
        val dPrime = 0.035
        val d = h - dPrime
        val asHalfM2 = (asCm2 / 2.0) / 10000.0 // Moitié des aciers par face principale
        val fcdKNm2 = fcdMPa * 1000.0
        val fydKNm2 = fydMPa * 1000.0

        // Hauteur de la zone comprimée de béton yu : équilibre N = 0.8 * yu * b * fcd + As2*sigma_s2 - As1*sigma_s1
        // Approximation robuste de premier ordre du bras de levier z
        val z = d - dPrime
        val nConcreteMax = 0.80 * h * b * fcdKNm2

        val effectiveN = min(nKN, nConcreteMax * 0.85)
        val yu = if (b * fcdKNm2 > 0) max(0.04, min(h * 0.85, (effectiveN + asHalfM2 * fydKNm2) / (0.80 * b * fcdKNm2))) else h * 0.40

        // Moment par rapport au centre de gravité
        val concreteForce = 0.80 * yu * b * fcdKNm2
        val concreteLever = (h / 2.0) - (0.40 * yu)
        val concreteMoment = concreteForce * concreteLever

        val steelForce = asHalfM2 * fydKNm2
        val steelLever = (h / 2.0) - dPrime
        val steelMoment = 2.0 * steelForce * steelLever

        val totalMRd = max(10.0, concreteMoment + steelMoment)
        return totalMRd
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
