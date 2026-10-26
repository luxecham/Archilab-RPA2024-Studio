package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Coefficients partiels et resistances de calcul d'une section en Te.
 * f_bu = 0,85 f_c28 / (theta . gamma_b) ; f_su = f_e / gamma_s.
 */
data class BaelTCoefficients(
    val fc28MPa: Double,
    val feMPa: Double,
    val theta: Double,
    val gammaB: Double,
    val gammaS: Double,
    val equivalenceCoefficient: Double = 15.0,
    val esMPa: Double = 200_000.0
) {
    init {
        require(fc28MPa > 0.0) { "fc28 invalide." }
        require(feMPa > 0.0) { "fe invalide." }
        require(theta > 0.0 && gammaB > 0.0 && gammaS > 0.0) { "Coefficients partiels invalides." }
        require(equivalenceCoefficient > 0.0) { "Coefficient d'équivalence invalide." }
    }

    val fbuMPa: Double get() = 0.85 * fc28MPa / (theta * gammaB)
    val fsuMPa: Double get() = feMPa / gammaS
    val ft28MPa: Double get() = 0.6 + 0.06 * fc28MPa
    val epsilonSyPermille: Double get() = feMPa / esMPa * 1000.0
    val epsilonCuPermille: Double get() = 3.5
    val alphaLu: Double get() = epsilonCuPermille / (epsilonCuPermille + epsilonSyPermille)
    val muLu: Double get() = 0.8 * alphaLu * (1.0 - 0.4 * alphaLu)
}

/**
 * Donnees geometriques et sollicitations d'une section en Te (table comprimee, nervure tendue).
 * Convention : N > 0 en compression, M > 0 en flexion positive.
 */
data class BaelTInput(
    val bMeters: Double,
    val b0Meters: Double,
    val hMeters: Double,
    val h0Meters: Double,
    val dPrimeMeters: Double = 0.05,
    val cMeters: Double = 0.05,
    val nuKN: Double = 0.0,
    val muKNm: Double,
    val fc28MPa: Double = 25.0,
    val feMPa: Double = 500.0,
    val theta: Double = 0.85,
    val gammaB: Double = 1.20,
    val gammaS: Double = 1.00,
    val equivalenceCoefficient: Double = 15.0
) {
    init {
        require(bMeters > 0.0) { "Largeur de table invalide." }
        require(b0Meters > 0.0) { "Largeur de nervure invalide." }
        require(hMeters > 0.0) { "Hauteur totale invalide." }
        require(h0Meters > 0.0 && h0Meters < hMeters) { "Épaisseur de table invalide." }
        require(b0Meters <= bMeters + 1e-9) { "La nervure doit être plus étroite que la table." }
        require(cMeters > 0.0 && cMeters < hMeters) { "Enrobage inférieur invalide." }
        require(dPrimeMeters > 0.0 && dPrimeMeters < hMeters) { "Enrobage supérieur invalide." }
        require(muKNm >= 0.0) { "Le moment de calcul doit être positif." }
    }

    val dMeters: Double get() = hMeters - cMeters
    val plusMinus: Double get() = 1.0
    val designMomentKNm: Double get() = muKNm + nuKN * (dMeters - hMeters / 2.0)
}

/** Resultat complet du noyau BAELT (section en Te). */
data class BaelTResult(
    val input: BaelTInput,
    val coefficients: BaelTCoefficients,
    val momentTableKNm: Double,
    val designMomentKNm: Double,
    val caseLabel: String,
    val muBu: Double,
    val muLu: Double,
    val alpha: Double,
    val neutralAxisY0Meters: Double,
    val leverArmMeters: Double,
    val aInfCm2: Double,
    val aSupCm2: Double,
    val tensionSteelCm2: Double,
    val compressionSteelCm2: Double,
    val minimumSteelCm2: Double,
    val notes: List<String> = emptyList()
) {
    val requiredSteelCm2: Double get() = maxOf(tensionSteelCm2, minimumSteelCm2)
}

/** Verification ELS d'une section en Te fissuree (n = 15). */
data class BaelTServiceResult(
    val neutralAxisMeters: Double,
    val inertiaM4: Double,
    val sigmaConcreteMPa: Double,
    val sigmaSteelMPa: Double,
    val concreteLimitMPa: Double,
    val steelLimitMPa: Double,
    val checks: List<DesignCheck>
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })
}

/**
 * Solveur natif de section en Te aux ELU et ELS, remplacant le module SOCOTEC BaelT.
 * Le moment de reference M_tu separe l'axe neutre dans la table (cas 1) de l'axe neutre
 * dans la nervure (cas 2). Les debords de table sont traites par la methode des
 * armatures additionnelles, avec armatures de compression si mu_bu2 > mu_lu.
 */
class BaelTSectionSolver {

    fun solve(input: BaelTInput): BaelTResult {
        val coefficients = BaelTCoefficients(
            fc28MPa = input.fc28MPa,
            feMPa = input.feMPa,
            theta = input.theta,
            gammaB = input.gammaB,
            gammaS = input.gammaS,
            equivalenceCoefficient = input.equivalenceCoefficient
        )
        val b = input.bMeters
        val b0 = input.b0Meters
        val h0 = input.h0Meters
        val h = input.hMeters
        val d = input.dMeters
        val dPrime = input.dPrimeMeters
        val fbu = coefficients.fbuMPa
        val fsu = coefficients.fsuMPa
        val momentTable = b * h0 * fbu * (d - h0 / 2.0) * 1000.0
        val moment = input.designMomentKNm
        val axialReduction = if (input.nuKN > 0.0) input.nuKN * 10.0 / fsu else 0.0
        val notes = mutableListOf<String>()

        var aInf: Double
        var aSup = 0.0
        var muBu: Double
        var alpha: Double
        var y0: Double
        var leverArm: Double
        val caseLabel: String

        if (moment <= momentTable + 1e-9) {
            caseLabel = "Cas 1 - axe neutre dans la table de compression"
            muBu = moment / (b * d * d * fbu * 1000.0)
            val (resolvedAlpha, resolvedLever, resolvedSteel, resolvedSup) = solveRectangularWeb(
                moment = moment,
                width = b,
                d = d,
                dPrime = dPrime,
                fbu = fbu,
                fsu = fsu,
                muLu = coefficients.muLu,
                alphaLu = coefficients.alphaLu
            )
            alpha = resolvedAlpha
            leverArm = resolvedLever
            aInf = resolvedSteel
            aSup = resolvedSup
            y0 = alpha * d
            notes += "M_u = ${Checks.format(moment)} kN.m ≤ M_tu = " +
                "${Checks.format(momentTable)} kN.m : la table entière est comprimée."
        } else {
            caseLabel = "Cas 2 - axe neutre dans la nervure"
            val leverTable = d - h0 / 2.0
            val momentFlange = (b - b0) * h0 * fbu * leverTable * 1000.0
            val steelFlange = if (leverTable > 0.0) momentFlange * 10.0 / (leverTable * fsu) else 0.0
            val momentWeb = moment - momentFlange
            muBu = momentWeb / (b0 * d * d * fbu * 1000.0)
            val (resolvedAlpha, resolvedLever, resolvedSteel, resolvedSup) = solveRectangularWeb(
                moment = momentWeb,
                width = b0,
                d = d,
                dPrime = dPrime,
                fbu = fbu,
                fsu = fsu,
                muLu = coefficients.muLu,
                alphaLu = coefficients.alphaLu
            )
            alpha = resolvedAlpha
            leverArm = resolvedLever
            aInf = steelFlange + resolvedSteel
            aSup = resolvedSup
            y0 = alpha * d
            notes += "M_u > M_tu : débords de table repris par A_s1 = " +
                "${Checks.format(steelFlange)} cm², nervure par A_s2 = " +
                "${Checks.format(resolvedSteel)} cm²."
            if (resolvedSup > 0.0) {
                notes += "mu_bu2 > mu_lu : armatures de compression A_sup = " +
                    "${Checks.format(resolvedSup)} cm²."
            }
        }

        if (axialReduction > 0.0) {
            aInf = (aInf - axialReduction).coerceAtLeast(0.0)
            notes += "Effort normal de compression N_u = ${Checks.format(input.nuKN)} kN : " +
                "déduction de N_u/f_su = ${Checks.format(axialReduction)} cm²."
        } else if (input.nuKN < 0.0) {
            aInf += abs(input.nuKN) * 10.0 / fsu
            notes += "Effort normal de traction N_u = ${Checks.format(input.nuKN)} kN ajouté aux armatures tendues."
        }

        val minimumSteel = 0.23 * b0 * d * coefficients.ft28MPa / input.feMPa * 10_000.0
        if (aInf < minimumSteel) {
            notes += "La section calculée est inférieure au minimum de non-fragilité BAEL " +
                "(${Checks.format(minimumSteel)} cm²)."
        }

        return BaelTResult(
            input = input,
            coefficients = coefficients,
            momentTableKNm = round(momentTable, 2),
            designMomentKNm = round(moment, 2),
            caseLabel = caseLabel,
            muBu = muBu,
            muLu = coefficients.muLu,
            alpha = alpha,
            neutralAxisY0Meters = round(y0, 3),
            leverArmMeters = round(leverArm, 4),
            aInfCm2 = round(aInf, 2),
            aSupCm2 = round(aSup, 2),
            tensionSteelCm2 = round(aInf, 2),
            compressionSteelCm2 = round(aSup, 2),
            minimumSteelCm2 = round(minimumSteel, 2),
            notes = notes
        )
    }

    private data class RectangularSolution(
        val alpha: Double,
        val leverArmMeters: Double,
        val tensionSteelCm2: Double,
        val compressionSteelCm2: Double
    )

    private fun solveRectangularWeb(
        moment: Double,
        width: Double,
        d: Double,
        dPrime: Double,
        fbu: Double,
        fsu: Double,
        muLu: Double,
        alphaLu: Double
    ): RectangularSolution {
        val denominator = width * d * d * fbu * 1000.0
        val muBu = if (denominator > 0.0) moment / denominator else 0.0
        if (muBu <= muLu + 1e-9) {
            val alpha = 1.25 * (1.0 - sqrt((1.0 - 2.0 * muBu).coerceAtLeast(0.0)))
            val lever = d * (1.0 - 0.4 * alpha)
            val steel = if (lever > 0.0) moment * 10.0 / (lever * fsu) else 0.0
            return RectangularSolution(alpha, lever, steel, 0.0)
        }
        val momentLimit = muLu * denominator
        val leverLimit = d * (1.0 - 0.4 * alphaLu)
        val baseSteel = if (leverLimit > 0.0) momentLimit * 10.0 / (leverLimit * fsu) else 0.0
        val deltaMoment = moment - momentLimit
        val compressionSteel = if (d > dPrime) deltaMoment * 10.0 / ((d - dPrime) * fsu) else 0.0
        return RectangularSolution(
            alpha = alphaLu,
            leverArmMeters = leverLimit,
            tensionSteelCm2 = baseSteel + compressionSteel,
            compressionSteelCm2 = compressionSteel
        )
    }

    /**
     * Verification ELS : section en Te fissuree, axe neutre par equilibre des moments
     * statiques, contraintes deduites de la section homogene (n = 15).
     */
    fun checkService(
        input: BaelTInput,
        steelAreaCm2: Double,
        momentServiceKNm: Double
    ): BaelTServiceResult {
        require(steelAreaCm2 > 0.0) { "La section d'acier doit être positive pour le calcul ELS." }
        val coefficients = BaelTCoefficients(
            fc28MPa = input.fc28MPa,
            feMPa = input.feMPa,
            theta = input.theta,
            gammaB = input.gammaB,
            gammaS = input.gammaS,
            equivalenceCoefficient = input.equivalenceCoefficient
        )
        val b = input.bMeters
        val b0 = input.b0Meters
        val h0 = input.h0Meters
        val d = input.dMeters
        val nAs = coefficients.equivalenceCoefficient * steelAreaCm2 / 10_000.0
        val moment = abs(momentServiceKNm)

        fun firstMoment(y: Double): Double =
            if (y <= h0) {
                b * y * y / 2.0 - nAs * (d - y)
            } else {
                b * h0 * (y - h0 / 2.0) + b0 * (y - h0) * (y - h0) / 2.0 - nAs * (d - y)
            }

        var low = 0.0
        var high = d
        if (firstMoment(high) < 0.0) {
            high = d
        }
        repeat(120) {
            val mid = (low + high) / 2.0
            if (firstMoment(mid) > 0.0) {
                high = mid
            } else {
                low = mid
            }
            if (high - low < 1e-10) return@repeat
        }
        val y = (low + high) / 2.0
        val concreteInertia = if (y <= h0) {
            b * y * y * y / 3.0
        } else {
            b * h0 * h0 * h0 / 12.0 +
                b * h0 * (y - h0 / 2.0) * (y - h0 / 2.0) +
                b0 * (y - h0) * (y - h0) * (y - h0) / 3.0
        }
        val inertia = concreteInertia + nAs * (d - y) * (d - y)
        val sigmaConcrete = if (inertia > 0.0) moment * y / inertia / 1000.0 else 0.0
        val sigmaSteel = if (inertia > 0.0) {
            coefficients.equivalenceCoefficient * moment * (d - y) / inertia / 1000.0
        } else {
            0.0
        }
        val concreteLimit = 0.6 * input.fc28MPa
        val steelLimit = 0.8 * input.feMPa
        val checks = listOf(
            Checks.max(
                code = "BAELT_ELS_BC",
                label = "Contrainte béton ELS (section en Té)",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                provided = sigmaConcrete,
                maximum = concreteLimit,
                unit = "MPa",
                decimals = 2
            ),
            Checks.max(
                code = "BAELT_ELS_ST",
                label = "Contrainte acier ELS (section en Té)",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                provided = sigmaSteel,
                maximum = steelLimit,
                unit = "MPa",
                decimals = 1
            )
        )
        return BaelTServiceResult(
            neutralAxisMeters = y,
            inertiaM4 = inertia,
            sigmaConcreteMPa = sigmaConcrete,
            sigmaSteelMPa = sigmaSteel,
            concreteLimitMPa = concreteLimit,
            steelLimitMPa = steelLimit,
            checks = checks
        )
    }

    private fun round(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return (value * factor).roundToLong() / factor
    }
}
