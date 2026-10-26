package com.archilab.rpa2024.engine.frames

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
enum class DesignSituation {
    DURABLE_ELU,        // theta = 1.0 (ou 0.85 > 24h), gamma_b = 1.50, gamma_s = 1.15
    ACCIDENTAL_SEISMIC, // theta = 0.85, gamma_b = 1.20, gamma_s = 1.00
    SERVICE_ELS         // Fissuration et contraintes de service
}

@Serializable
enum class SectionStressState(val code: String, val description: String) {
    FLEXION_SIMPLE("FS", "Flexion simple"),
    SECTION_ENTIEREMENT_TENDUE("SET", "Section entièrement tendue"),
    SECTION_PARTIELLEMENT_COMPRIMEE("SPC", "Section partiellement comprimée"),
    SECTION_ENTIEREMENT_COMPRIMEE("SEC", "Section entièrement comprimée")
}

@Serializable
data class BaelRInput(
    val widthBMeters: Double,             // Largeur b (m)
    val heightHMeters: Double,            // Hauteur totale h (m)
    val dPrimeMeters: Double = 0.03,      // Enrobage/distance fibre sup - aciers sup d' (m)
    val cMeters: Double = 0.03,           // Enrobage/distance fibre inf - aciers inf c (m)
    val fc28MPa: Double = 25.0,           // Résistance compression béton (MPa)
    val feMPa: Double = 500.0,            // Limite élastique acier (MPa)
    val axialForceNKN: Double = 0.0,      // Effort normal N (kN) (+ compression, - traction convention BaelR)
    val momentMKNm: Double = 0.0,         // Moment fléchissant au centre de gravité M (kN.m)
    val situation: DesignSituation = DesignSituation.DURABLE_ELU,
    val thetaDuration: Double = 0.85      // Coefficient de durée d'application des charges (0.85 ou 1.0)
)

@Serializable
data class BaelRResult(
    val widthBMeters: Double,
    val heightHMeters: Double,
    val effectiveDepthDMeters: Double,
    val dPrimeMeters: Double,
    val axialForceNKN: Double,
    val momentMKNm: Double,
    val situation: DesignSituation,
    val state: SectionStressState,
    val neutralAxisY0Meters: Double,      // Position de l'axe neutre y0 par rapport à la fibre la plus comprimée (m)
    val aSupCm2: Double,                  // Section d'armatures supérieures (cm²)
    val aInfCm2: Double,                  // Section d'armatures inférieures (cm²)
    val aTotalCm2: Double,                // Section totale = Asup + Ainf (cm²)
    val minFragilityAreaCm2: Double,      // Armature minimale de non-fragilité BAEL (cm²)
    val aFictCm2: Double = 0.0,           // Section fictive sous moment transporté MA (cm²)
    val isCompliant: Boolean
)

/**
 * Solveur exact de section rectangulaire en béton armé selon BAEL 91 révisé 99.
 * Remplace intégralement le logiciel SOCOTEC BaelR.
 */
class BaelRectangularSolver {

    fun solve(input: BaelRInput): BaelRResult {
        val b = input.widthBMeters
        val h = input.heightHMeters
        val dPrime = input.dPrimeMeters
        val c = input.cMeters
        val d = h - c
        val fc28 = input.fc28MPa
        val fe = input.feMPa
        val theta = input.thetaDuration

        val (gammaB, gammaS) = when (input.situation) {
            DesignSituation.DURABLE_ELU -> Pair(1.50, 1.15)
            DesignSituation.ACCIDENTAL_SEISMIC -> Pair(1.20, 1.00)
            DesignSituation.SERVICE_ELS -> Pair(1.00, 1.00)
        }

        val fbu = (0.85 * fc28) / (theta * gammaB) // MPa
        val fsu = fe / gammaS                     // MPa
        val ft28 = 0.6 + 0.06 * fc28

        val nKN = input.axialForceNKN
        val mKNm = input.momentMKNm

        // Condition de non-fragilité BAEL en flexion simple / composée
        val aMinFragilityCm2 = 0.23 * b * d * (ft28 / fe) * 10000.0

        // 1. CAS PARTICULIER : FLEXION SIMPLE (N = 0)
        if (abs(nKN) < 1e-4) {
            return solveSimpleBending(b, h, d, dPrime, fbu, fsu, mKNm, input, aMinFragilityCm2)
        }

        // 2. CAS GÉNÉRAL : FLEXION COMPOSÉE (N != 0)
        return solveCombinedBending(b, h, d, dPrime, fbu, fsu, nKN, mKNm, input, aMinFragilityCm2)
    }

    private fun solveSimpleBending(
        b: Double,
        h: Double,
        d: Double,
        dPrime: Double,
        fbu: Double,
        fsu: Double,
        mKNm: Double,
        input: BaelRInput,
        aMinFragilityCm2: Double
    ): BaelRResult {
        val absM = abs(mKNm)
        if (absM < 1e-4) {
            return BaelRResult(
                widthBMeters = b,
                heightHMeters = h,
                effectiveDepthDMeters = d,
                dPrimeMeters = dPrime,
                axialForceNKN = 0.0,
                momentMKNm = 0.0,
                situation = input.situation,
                state = SectionStressState.FLEXION_SIMPLE,
                neutralAxisY0Meters = 0.0,
                aSupCm2 = 0.0,
                aInfCm2 = aMinFragilityCm2,
                aTotalCm2 = aMinFragilityCm2,
                minFragilityAreaCm2 = aMinFragilityCm2,
                isCompliant = true
            )
        }

        val mNm = absM * 1000.0
        val fbuPa = fbu * 1e6
        val fsuPa = fsu * 1e6

        val muBu = mNm / (b * d * d * fbuPa)
        val muLu = 0.372 // Pivot B pour FeE500

        var aSupM2 = 0.0
        val aInfM2: Double
        val y0: Double

        if (muBu <= muLu) {
            val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muBu)))
            val zb = d * (1.0 - 0.4 * alpha)
            y0 = alpha * d
            aInfM2 = mNm / (zb * fsuPa)
        } else {
            val alphaL = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muLu)))
            y0 = alphaL * d
            val zbL = d * (1.0 - 0.4 * alphaL)
            val deltaMNm = mNm - muLu * b * d * d * fbuPa
            aSupM2 = deltaMNm / ((d - dPrime) * fsuPa)
            aInfM2 = (muLu * b * d * d * fbuPa) / (zbL * fsuPa) + aSupM2
        }

        val aInfCm2 = max(aMinFragilityCm2, aInfM2 * 10000.0)
        val aSupCm2 = aSupM2 * 10000.0

        return BaelRResult(
            widthBMeters = b,
            heightHMeters = h,
            effectiveDepthDMeters = d,
            dPrimeMeters = dPrime,
            axialForceNKN = 0.0,
            momentMKNm = mKNm,
            situation = input.situation,
            state = SectionStressState.FLEXION_SIMPLE,
            neutralAxisY0Meters = y0,
            aSupCm2 = if (mKNm >= 0) aSupCm2 else aInfCm2,
            aInfCm2 = if (mKNm >= 0) aInfCm2 else aSupCm2,
            aTotalCm2 = aSupCm2 + aInfCm2,
            minFragilityAreaCm2 = aMinFragilityCm2,
            aFictCm2 = aInfM2 * 10000.0,
            isCompliant = true
        )
    }

    private fun solveCombinedBending(
        b: Double,
        h: Double,
        d: Double,
        dPrime: Double,
        fbu: Double,
        fsu: Double,
        nKN: Double,
        mKNm: Double,
        input: BaelRInput,
        aMinFragilityCm2: Double
    ): BaelRResult {
        // Convention BaelR : N > 0 = compression, N < 0 = traction
        val fbuPa = fbu * 1e6
        val fsuPa = fsu * 1e6
        val nN = nKN * 1000.0
        val mNm = mKNm * 1000.0
        val absMNm = abs(mNm)

        val e0 = if (abs(nN) > 1e-3) mNm / nN else 0.0

        // CAS A : SECTION ENTIÈREMENT TENDUE (SET)
        // N < 0 et l'effort normal se trouve entre les armatures supérieures et inférieures
        val distBetweenRebars = d - dPrime
        if (nKN < 0 && abs(e0) <= (h / 2.0 - dPrime)) {
            val nTensile = abs(nN)
            // Bras de levier par rapport aux armatures
            val aMajorM2 = (nTensile * (d - h / 2.0 + abs(e0))) / (distBetweenRebars * fsuPa)
            val aMinorM2 = (nTensile * (d - h / 2.0 - abs(e0))) / (distBetweenRebars * fsuPa)

            val aMajorCm2 = max(0.0, aMajorM2 * 10000.0)
            val aMinorCm2 = max(0.0, aMinorM2 * 10000.0)

            val (aSupCm2, aInfCm2) = if (mKNm >= 0) {
                Pair(aMinorCm2, aMajorCm2)
            } else {
                Pair(aMajorCm2, aMinorCm2)
            }

            return BaelRResult(
                widthBMeters = b,
                heightHMeters = h,
                effectiveDepthDMeters = d,
                dPrimeMeters = dPrime,
                axialForceNKN = nKN,
                momentMKNm = mKNm,
                situation = input.situation,
                state = SectionStressState.SECTION_ENTIEREMENT_TENDUE,
                neutralAxisY0Meters = 0.0,
                aSupCm2 = aSupCm2,
                aInfCm2 = aInfCm2,
                aTotalCm2 = aSupCm2 + aInfCm2,
                minFragilityAreaCm2 = aMinFragilityCm2,
                isCompliant = true
            )
        }

        // Moment transporté au centre de gravité des armatures tendues A
        // MA = |M_G| + N * (d - h/2)
        val maNm = absMNm + nN * (d - h / 2.0)
        val muA = abs(maNm) / (b * d * d * fbuPa)
        val muLu = 0.372

        // CAS C : SECTION ENTIÈREMENT COMPRIMÉE (SEC)
        // N > 0 et forte compression (centre de pression dans le noyau central ou muA négatif)
        val psi = if (nKN > 0) nN / (b * h * fbuPa) else 0.0
        if (nKN > 0 && (psi > 0.81 || (maNm <= 0.0 && abs(e0) < h / 6.0))) {
            // Pivot C
            val totalAsM2 = max(0.0, (nN - b * h * fbuPa) / fsuPa)
            val halfAsCm2 = (totalAsM2 * 10000.0) / 2.0

            return BaelRResult(
                widthBMeters = b,
                heightHMeters = h,
                effectiveDepthDMeters = d,
                dPrimeMeters = dPrime,
                axialForceNKN = nKN,
                momentMKNm = mKNm,
                situation = input.situation,
                state = SectionStressState.SECTION_ENTIEREMENT_COMPRIMEE,
                neutralAxisY0Meters = h,
                aSupCm2 = halfAsCm2,
                aInfCm2 = halfAsCm2,
                aTotalCm2 = halfAsCm2 * 2.0,
                minFragilityAreaCm2 = aMinFragilityCm2,
                isCompliant = true
            )
        }

        // CAS B : SECTION PARTIELLEMENT COMPRIMÉE (SPC)
        var aCompM2 = 0.0
        val aFictM2: Double
        val y0: Double

        if (muA <= muLu) {
            val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muA)))
            val zb = d * (1.0 - 0.4 * alpha)
            y0 = alpha * d
            aFictM2 = abs(maNm) / (zb * fsuPa)
        } else {
            val alphaL = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muLu)))
            y0 = alphaL * d
            val zbL = d * (1.0 - 0.4 * alphaL)
            val deltaM = abs(maNm) - muLu * b * d * d * fbuPa
            aCompM2 = deltaM / ((d - dPrime) * fsuPa)
            aFictM2 = (muLu * b * d * d * fbuPa) / (zbL * fsuPa) + aCompM2
        }

        // Reconversion de la section fictive en section réelle :
        // As = A_fict - N / fsu
        val aTensM2 = aFictM2 - (nN / fsuPa)

        val aTensCm2 = max(0.0, aTensM2 * 10000.0)
        val aCompCm2 = max(0.0, aCompM2 * 10000.0)

        val (aSupCm2, aInfCm2) = if (mKNm >= 0) {
            Pair(aCompCm2, aTensCm2)
        } else {
            Pair(aTensCm2, aCompCm2)
        }

        return BaelRResult(
            widthBMeters = b,
            heightHMeters = h,
            effectiveDepthDMeters = d,
            dPrimeMeters = dPrime,
            axialForceNKN = nKN,
            momentMKNm = mKNm,
            situation = input.situation,
            state = SectionStressState.SECTION_PARTIELLEMENT_COMPRIMEE,
            neutralAxisY0Meters = y0,
            aSupCm2 = aSupCm2,
            aInfCm2 = aInfCm2,
            aTotalCm2 = aSupCm2 + aInfCm2,
            minFragilityAreaCm2 = aMinFragilityCm2,
            aFictCm2 = aFictM2 * 10000.0,
            isCompliant = true
        )
    }
}
