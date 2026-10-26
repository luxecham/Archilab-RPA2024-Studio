package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.model.MaterialConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Situation de calcul BAEL : durable (ELU) ou accidentelle sismique (ELA). */
enum class BaelSituation(val label: String) {
    DURABLE("Situation durable (ELU)"),
    ACCIDENTAL("Situation accidentelle sismique (ELA)")
}

/**
 * Coefficients partiels et resistances de calcul d'une situation BAEL.
 * f_bu = 0,85 f_c28 / (theta . gamma_b) ; f_su = f_e / gamma_s.
 */
data class BaelCoefficients(
    val situation: BaelSituation,
    val theta: Double,
    val gammaB: Double,
    val gammaS: Double,
    val fc28MPa: Double,
    val feMPa: Double,
    val esMPa: Double = 200_000.0
) {
    val fbuMPa: Double get() = 0.85 * fc28MPa / (theta * gammaB)
    val fsuMPa: Double get() = feMPa / gammaS
    val ft28MPa: Double get() = 0.6 + 0.06 * fc28MPa
    val epsilonSyPermille: Double get() = feMPa / esMPa * 1000.0
    val epsilonCuPermille: Double get() = 3.5
    val epsilonSuPermille: Double get() = 10.0

    /** Contrainte de calcul de l'acier pour une deformation imposee (en pour mille). */
    fun steelStressMPa(strainPermille: Double): Double =
        min(strainPermille / 1000.0 * esMPa, fsuMPa).coerceAtLeast(0.0)
}

/** Etat de la section a l'ELU. */
enum class BaelSectionState(val label: String) {
    FLEXION_SIMPLE("Flexion simple"),
    ENTIEREMENT_TENDUE("Section entièrement tendue (SET)"),
    PARTIELLEMENT_COMPRIMEE("Section partiellement comprimée (SPC)"),
    ENTIEREMENT_COMPRIMEE("Section entièrement comprimée (SEC)")
}

/**
 * Donnees geometriques et sollicitations d'une section rectangulaire.
 * Convention des efforts : N > 0 en compression, N < 0 en traction ; M > 0 en
 * flexion positive (fibre superieure comprimee). c = enrobage de l'acier tendu
 * (distance face tendue / centre du lit), d' = enrobage de l'acier comprime.
 */
data class BaelSectionInput(
    val bCm: Double,
    val hCm: Double,
    val tensionCoverCm: Double = 5.0,
    val compressionCoverCm: Double = 5.0,
    val nuKN: Double = 0.0,
    val muGKNm: Double = 0.0,
    val situation: BaelSituation = BaelSituation.DURABLE
) {
    init {
        require(bCm > 0.0) { "Largeur b invalide." }
        require(hCm > 0.0) { "Hauteur h invalide." }
        require(tensionCoverCm > 0.0 && compressionCoverCm > 0.0) { "Enrobages invalides." }
        require(tensionCoverCm < hCm) { "Enrobage c incompatible avec la hauteur h." }
        require(compressionCoverCm < hCm) { "Enrobage d' incompatible avec la hauteur h." }
    }

    val bMeters: Double get() = bCm / 100.0
    val hMeters: Double get() = hCm / 100.0
    val dMeters: Double get() = hMeters - tensionCoverCm / 100.0
    val dPrimeMeters: Double get() = compressionCoverCm / 100.0
    val isSimpleBending: Boolean get() = abs(nuKN) < 1e-9
}

/**
 * Resultat complet du noyau BAELR : etat de section, axe neutre, bras de levier
 * et sections d'acier superieure / inferieure en cm2.
 */
data class BaelRectangularResult(
    val section: BaelSectionInput,
    val coefficients: BaelCoefficients,
    val state: BaelSectionState,
    val axialKN: Double,
    val momentKNm: Double,
    val designMomentAtTensionSteelKNm: Double,
    val eccentricityMeters: Double,
    val muBu: Double,
    val muLu: Double,
    val alpha: Double,
    val neutralAxisMeters: Double,
    val leverArmMeters: Double,
    val topSteelCm2: Double,
    val bottomSteelCm2: Double,
    val tensionSteelCm2: Double,
    val compressionSteelCm2: Double,
    val minimumSteelCm2: Double,
    val notes: List<String> = emptyList()
) {
    val totalSteelCm2: Double get() = topSteelCm2 + bottomSteelCm2
    val requiredSteelCm2: Double get() = max(totalSteelCm2, minimumSteelCm2)
    val neutralAxisLabel: String
        get() = when (state) {
            BaelSectionState.ENTIEREMENT_TENDUE -> "y0 = 0 (section entièrement tendue)"
            BaelSectionState.ENTIEREMENT_COMPRIMEE ->
                "y0 ≥ h = ${com.archilab.rpa2024.engine.geometry.Checks.format(section.hCm, 1)} cm (pivot C)"
            else -> "y0 = ${com.archilab.rpa2024.engine.geometry.Checks.format(neutralAxisMeters * 100.0, 1)} cm"
        }
}

/**
 * Noyau de calcul BAEL 91 remplacant SOCOTEC BaelR pour les sections rectangulaires.
 * Toutes les unites d'entree sont le cm pour les longueurs, le kN et le kN.m pour les
 * efforts, le MPa pour les resistances ; les aciers sont retournes en cm2.
 */
class BaelRectangularSolver {

    fun coefficients(materials: MaterialConfig, situation: BaelSituation): BaelCoefficients =
        when (situation) {
            BaelSituation.DURABLE -> BaelCoefficients(
                situation = situation,
                theta = 1.0,
                gammaB = materials.gammaBElu,
                gammaS = materials.gammaSElu,
                fc28MPa = materials.fc28MPa,
                feMPa = materials.feMPa
            )
            BaelSituation.ACCIDENTAL -> BaelCoefficients(
                situation = situation,
                theta = 0.85,
                gammaB = materials.gammaBAccidental,
                gammaS = materials.gammaSAccidental,
                fc28MPa = materials.fc28MPa,
                feMPa = materials.feMPa
            )
        }

    fun solve(input: BaelSectionInput, materials: MaterialConfig): BaelRectangularResult {
        val coefficients = coefficients(materials, input.situation)
        return if (input.isSimpleBending) {
            solveSimpleBending(input, coefficients)
        } else {
            solveCombinedBending(input, coefficients)
        }
    }

    /** Moment reduit limite correspondant a l'axe neutre ou l'acier tendu atteint sa limite elastique. */
    fun alphaLimit(coefficients: BaelCoefficients): Double =
        coefficients.epsilonCuPermille / (coefficients.epsilonCuPermille + coefficients.epsilonSyPermille)

    fun muLimit(coefficients: BaelCoefficients): Double {
        val alpha = alphaLimit(coefficients)
        return 0.8 * alpha * (1.0 - 0.4 * alpha)
    }

    /** Section minimale de non-fragilite BAEL : A_min = 0,23 b d f_t28 / f_e. */
    fun baelMinimumSteelCm2(
        bCm: Double,
        hCm: Double,
        tensionCoverCm: Double,
        coefficients: BaelCoefficients
    ): Double {
        val b = bCm / 100.0
        val d = (hCm - tensionCoverCm) / 100.0
        return 0.23 * b * d * coefficients.ft28MPa / coefficients.feMPa * 10_000.0
    }

    private fun solveSimpleBending(
        input: BaelSectionInput,
        coefficients: BaelCoefficients
    ): BaelRectangularResult {
        val notes = mutableListOf<String>()
        val b = input.bMeters
        val d = input.dMeters
        val dPrime = input.dPrimeMeters
        val fbu = coefficients.fbuMPa
        val fsu = coefficients.fsuMPa
        val muLu = muLimit(coefficients)
        val alphaLu = alphaLimit(coefficients)

        val moment = abs(input.muGKNm)
        val muBu = moment / (b * d * d * fbu * 1000.0)
        val alpha = if (muBu <= muLu && muBu < 0.5) {
            1.25 * (1.0 - sqrt((1.0 - 2.0 * muBu).coerceAtLeast(0.0)))
        } else {
            alphaLu
        }
        val leverArm = d * (1.0 - 0.4 * alpha)
        val mluKNm = muLu * b * d * d * fbu * 1000.0
        val zLu = d * (1.0 - 0.4 * alphaLu)

        var compressionSteel = 0.0
        var compressionStress = 0.0
        var tensionSteel = if (leverArm > 0.0) moment / (leverArm * fsu) * 10.0 else 0.0

        if (muBu > muLu) {
            val deltaM = moment - mluKNm
            val strainPrime = coefficients.epsilonCuPermille * (1.0 - dPrime / (alphaLu * d))
            compressionStress = if (strainPrime >= coefficients.epsilonSyPermille) {
                fsu
            } else {
                coefficients.steelStressMPa(strainPrime)
            }
            compressionSteel = if (deltaM > 0.0 && compressionStress > 0.0) {
                deltaM / ((d - dPrime) * compressionStress) * 10.0
            } else {
                0.0
            }
            val baseTension = if (zLu > 0.0) mluKNm / (zLu * fsu) * 10.0 else 0.0
            tensionSteel = baseTension + compressionSteel * compressionStress / fsu
            notes += "Moment réduit supérieur à mu_lu = ${format(muLu, 3)} : section doublement armée."
            if (strainPrime < coefficients.epsilonSyPermille) {
                notes += "L'acier comprimé n'atteint pas sa limite élastique : sigma_s' = " +
                    "${format(compressionStress, 1)} MPa."
            }
        }

        val minimumSteel = baelMinimumSteelCm2(input.bCm, input.hCm, input.tensionCoverCm, coefficients)
        if (tensionSteel + compressionSteel < minimumSteel) {
            notes += "La section calculée est inférieure au minimum de non-fragilité BAEL."
        }

        return BaelRectangularResult(
            section = input,
            coefficients = coefficients,
            state = BaelSectionState.FLEXION_SIMPLE,
            axialKN = 0.0,
            momentKNm = input.muGKNm,
            designMomentAtTensionSteelKNm = input.muGKNm,
            eccentricityMeters = 0.0,
            muBu = muBu,
            muLu = muLu,
            alpha = alpha,
            neutralAxisMeters = alpha * d,
            leverArmMeters = leverArm,
            topSteelCm2 = compressionSteel,
            bottomSteelCm2 = tensionSteel,
            tensionSteelCm2 = tensionSteel,
            compressionSteelCm2 = compressionSteel,
            minimumSteelCm2 = minimumSteel,
            notes = notes
        )
    }

    private fun solveCombinedBending(
        input: BaelSectionInput,
        coefficients: BaelCoefficients
    ): BaelRectangularResult {
        val notes = mutableListOf<String>()
        val b = input.bMeters
        val h = input.hMeters
        val d = input.dMeters
        val dPrime = input.dPrimeMeters
        val nu = input.nuKN
        val muG = input.muGKNm
        val fbu = coefficients.fbuMPa
        val fsu = coefficients.fsuMPa
        val muLu = muLimit(coefficients)
        val alphaLu = alphaLimit(coefficients)
        val halfHeight = h / 2.0
        val eccentricity = muG / nu
        val muA = muG + nu * (d - halfHeight)
        val minimumSteel = baelMinimumSteelCm2(input.bCm, input.hCm, input.tensionCoverCm, coefficients)

        // Cas A : section entierement tendue (SET).
        if (nu < 0.0 && abs(eccentricity) <= halfHeight - dPrime + 1e-9) {
            val lever = (d - dPrime) * fsu
            val bottomRaw = if (lever > 0.0) abs(nu) * (d - halfHeight + eccentricity) / lever * 10.0 else 0.0
            val topRaw = if (lever > 0.0) abs(nu) * (d - halfHeight - eccentricity) / lever * 10.0 else 0.0
            val topArea = topRaw.coerceAtLeast(0.0)
            val bottomArea = bottomRaw.coerceAtLeast(0.0)
            notes += "Axe neutre hors section : les deux nappes reprennent la traction."
            if (topRaw < -1e-9 || bottomRaw < -1e-9) {
                notes += "Une nappe calculée est négative : elle est ramenée à zéro."
            }
            return BaelRectangularResult(
                section = input,
                coefficients = coefficients,
                state = BaelSectionState.ENTIEREMENT_TENDUE,
                axialKN = nu,
                momentKNm = muG,
                designMomentAtTensionSteelKNm = muA,
                eccentricityMeters = eccentricity,
                muBu = 0.0,
                muLu = muLu,
                alpha = 0.0,
                neutralAxisMeters = 0.0,
                leverArmMeters = d - dPrime,
                topSteelCm2 = topArea,
                bottomSteelCm2 = bottomArea,
                tensionSteelCm2 = topArea + bottomArea,
                compressionSteelCm2 = 0.0,
                minimumSteelCm2 = minimumSteel,
                notes = notes
            )
        }

        val psi1 = if (b * h * fbu * 1000.0 > 0.0) nu / (b * h * fbu * 1000.0) else 0.0
        if (nu > 0.0 && psi1 > 0.81) {
            notes += "psi_1 = N_u/(b h f_bu) = ${format(psi1, 3)} > 0,81 : pivot C."
            return solveFullyCompressed(input, coefficients, muA, minimumSteel, notes)
        }

        val muBu = abs(muA) / (b * d * d * fbu * 1000.0)
        val alpha = if (muBu <= muLu && muBu < 0.5) {
            1.25 * (1.0 - sqrt((1.0 - 2.0 * muBu).coerceAtLeast(0.0)))
        } else {
            alphaLu
        }
        if (nu > 0.0 && alpha * d > h + 1e-9) {
            notes += "Axe neutre au-delà de la section : pivot C."
            return solveFullyCompressed(input, coefficients, muA, minimumSteel, notes)
        }

        // Cas B : section partiellement comprimee (SPC).
        val fictitious = solveSimpleBending(
            input.copy(nuKN = 0.0, muGKNm = abs(muA)),
            coefficients
        )
        val axialReduction = nu / fsu * 10.0
        val realTension = max(0.0, fictitious.tensionSteelCm2 - axialReduction)
        if (realTension <= 1e-9 && nu > 0.0) {
            notes += "A_tendu fictive (${format(fictitious.tensionSteelCm2, 2)} cm²) inférieure à " +
                "N_u/f_su (${format(axialReduction, 2)} cm²) : section entièrement comprimée."
            return solveFullyCompressed(input, coefficients, muA, minimumSteel, notes)
        }

        val swapped = muA < 0.0
        val compression = fictitious.compressionSteelCm2
        val top = if (swapped) realTension else compression
        val bottom = if (swapped) compression else realTension
        notes += "Section partiellement comprimée : ferraillage fictif en flexion simple sous " +
            "M_uA = ${format(abs(muA), 2)} kN.m, puis déduction de N_u/f_su."
        if (swapped) {
            notes += "Moment à l'axe des aciers négatif : la nappe tendue est la nappe supérieure."
        }

        return BaelRectangularResult(
            section = input,
            coefficients = coefficients,
            state = BaelSectionState.PARTIELLEMENT_COMPRIMEE,
            axialKN = nu,
            momentKNm = muG,
            designMomentAtTensionSteelKNm = muA,
            eccentricityMeters = eccentricity,
            muBu = muBu,
            muLu = muLu,
            alpha = alpha,
            neutralAxisMeters = alpha * d,
            leverArmMeters = d * (1.0 - 0.4 * alpha),
            topSteelCm2 = top,
            bottomSteelCm2 = bottom,
            tensionSteelCm2 = realTension,
            compressionSteelCm2 = compression,
            minimumSteelCm2 = minimumSteel,
            notes = notes
        )
    }

    /**
     * Pivot C : section entierement comprimee. Le diagramme de deformation est pris a la
     * limite du domaine (3,5 pour mille a la fibre la plus comprimee, 2 pour mille a l'autre).
     * Les aciers sont supposes symetriques ; on retient l'enveloppe des equations de force
     * et de moment, ce qui conduit a une section d'acier constructible et securitaire.
     */
    private fun solveFullyCompressed(
        input: BaelSectionInput,
        coefficients: BaelCoefficients,
        muAKNm: Double,
        minimumSteel: Double,
        notes: MutableList<String>
    ): BaelRectangularResult {
        val b = input.bMeters
        val h = input.hMeters
        val d = input.dMeters
        val dPrime = input.dPrimeMeters
        val nu = input.nuKN
        val fbu = coefficients.fbuMPa
        val fsu = coefficients.fsuMPa
        val fbuKNm2 = fbu * 1000.0

        var topSteel: Double
        var bottomSteel: Double

        if (abs(input.muGKNm) < 1e-6) {
            val uniformStrain = min(
                coefficients.epsilonCuPermille,
                3.5 * nu / (b * h * fbuKNm2)
            ).coerceAtLeast(0.0)
            val concreteForce = b * h * fbuKNm2 * (uniformStrain / 3.5)
            val sigma = coefficients.steelStressMPa(uniformStrain)
            val total = if (sigma > 0.0) (nu - concreteForce).coerceAtLeast(0.0) / sigma * 10.0 else 0.0
            topSteel = total / 2.0
            bottomSteel = total / 2.0
        } else {
            val epsilonTop = coefficients.epsilonCuPermille
            val epsilonBottom = 2.0
            val concreteForce = b * h * fbuKNm2 *
                ((epsilonTop + epsilonBottom) / (2.0 * coefficients.epsilonCuPermille))
            val concreteCentroid = h * (epsilonTop + 2.0 * epsilonBottom) /
                (3.0 * (epsilonTop + epsilonBottom))
            val strainTopSteel = epsilonTop + (epsilonBottom - epsilonTop) * (dPrime / h)
            val strainBottomSteel = epsilonTop + (epsilonBottom - epsilonTop) * (d / h)
            val sigmaTop = coefficients.steelStressMPa(strainTopSteel)
            val sigmaBottom = coefficients.steelStressMPa(strainBottomSteel)
            val byForce = if (sigmaTop + sigmaBottom > 0.0) {
                2.0 * (nu - concreteForce) / (sigmaTop + sigmaBottom) * 10.0
            } else {
                0.0
            }
            val byMoment = if (sigmaTop > 0.0 && d > dPrime) {
                2.0 * (abs(muAKNm) - concreteForce * (d - concreteCentroid)) /
                    (sigmaTop * (d - dPrime)) * 10.0
            } else {
                0.0
            }
            val total = maxOf(byForce, byMoment, 0.0)
            topSteel = total / 2.0
            bottomSteel = total / 2.0
            notes += "Pivot C : epsilon_bc = 3,5‰ / 2,0‰, béton comprimé sur toute la hauteur, " +
                "aciers symétriques."
        }

        if (topSteel + bottomSteel < minimumSteel) {
            notes += "Le ferraillage de pivot C est inférieur au minimum BAEL : le minimum réglementaire gouverne."
        }

        return BaelRectangularResult(
            section = input,
            coefficients = coefficients,
            state = BaelSectionState.ENTIEREMENT_COMPRIMEE,
            axialKN = nu,
            momentKNm = input.muGKNm,
            designMomentAtTensionSteelKNm = muAKNm,
            eccentricityMeters = if (abs(nu) > 1e-9) input.muGKNm / nu else 0.0,
            muBu = if (b * d * d * fbu * 1000.0 > 0.0) abs(muAKNm) / (b * d * d * fbu * 1000.0) else 0.0,
            muLu = muLimit(coefficients),
            alpha = if (d > 0.0) h / d else 1.0,
            neutralAxisMeters = h,
            leverArmMeters = d - dPrime,
            topSteelCm2 = topSteel,
            bottomSteelCm2 = bottomSteel,
            tensionSteelCm2 = 0.0,
            compressionSteelCm2 = topSteel + bottomSteel,
            minimumSteelCm2 = minimumSteel,
            notes = notes
        )
    }

    private fun format(value: Double, decimals: Int): String =
        String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
}

/** Lit de barres commerciales (ex. 4T16). */
data class RebarBar(val count: Int, val diameterMm: Int) {
    init {
        require(count >= 1) { "Un lit comporte au moins une barre." }
        require(diameterMm >= 6) { "Diamètre de barre invalide." }
    }

    val areaCm2: Double get() = count * PI * (diameterMm / 10.0).pow(2) / 4.0
    val label: String get() = "${count}T$diameterMm"
}

/** Disposition symetrique des armatures longitudinales d'un poteau. */
data class ColumnRebarLayout(
    val cornerBar: RebarBar,
    val faceBar: RebarBar?,
    val barsPerLongFace: Int,
    val barsPerShortFace: Int,
    val totalBars: Int,
    val areaCm2: Double,
    val spacingLongFaceCm: Double,
    val spacingShortFaceCm: Double,
    val minDiameterMm: Int,
    val maxDiameterMm: Int
) {
    val label: String
        get() = when {
            faceBar == null || faceBar.count == 0 -> cornerBar.label
            cornerBar.diameterMm == faceBar.diameterMm ->
                "${cornerBar.count + faceBar.count}T${cornerBar.diameterMm}"
            else -> "${cornerBar.label} + ${faceBar.label}"
        }
}

/** Repartition en barres filantes et renforts / chapeaux d'une poutre. */
data class BeamRebarLayout(
    val continuousBar: RebarBar,
    val additionalBar: RebarBar?,
    val totalBars: Int,
    val areaCm2: Double,
    val continuousAreaCm2: Double,
    val minDiameterMm: Int,
    val maxDiameterMm: Int,
    val additionalRole: String
) {
    val label: String
        get() = buildString {
            append(continuousBar.label)
            append(" filantes")
            if (additionalBar != null && additionalBar.count > 0) {
                append(" + ")
                append(additionalBar.label)
                append(' ')
                append(additionalRole)
            }
        }
}

/**
 * Selecteur automatique de barres commerciales. Les dispositions de poteaux sont
 * symetriques (4 coins + barres de face) et respectent l'espacement maximal sur chaque
 * face ; les poutres sont reparties en barres filantes et renforts/chapeaux.
 */
class RebarOptimizer(
    private val diametersMm: List<Int> = listOf(12, 14, 16, 20, 25, 32)
) {

    fun selectColumnLayout(
        bCm: Double,
        hCm: Double,
        steelCoverCm: Double,
        requiredAsCm2: Double,
        maximumAsCm2: Double,
        spacingLimitCm: Double
    ): ColumnRebarLayout? {
        if (bCm <= 0.0 || hCm <= 0.0) return null
        val longFace = max(bCm, hCm)
        val shortFace = min(bCm, hCm)
        val longSpacingLimit = spacingLimitCm.coerceAtLeast(5.0)
        val minBarsLong = barsForSpacing(longFace, steelCoverCm, longSpacingLimit)
        val minBarsShort = barsForSpacing(shortFace, steelCoverCm, longSpacingLimit)

        data class Candidate(
            val barsLong: Int,
            val barsShort: Int,
            val corner: RebarBar,
            val face: RebarBar?,
            val totalBars: Int,
            val area: Double,
            val spacingLong: Double,
            val spacingShort: Double
        )

        val candidates = mutableListOf<Candidate>()
        for (barsLong in minBarsLong..(minBarsLong + 4)) {
            for (barsShort in minBarsShort..(minBarsShort + 4)) {
                val faceCount = 2 * (barsLong - 2) + 2 * (barsShort - 2)
                val totalBars = 4 + faceCount
                if (totalBars > 28) continue
                val spacingLong = (longFace - 2.0 * steelCoverCm) / (barsLong - 1)
                val spacingShort = (shortFace - 2.0 * steelCoverCm) / (barsShort - 1)
                if (spacingLong > longSpacingLimit + 1e-6 || spacingShort > longSpacingLimit + 1e-6) continue

                for (cornerDiameter in diametersMm.filter { it >= 12 }) {
                    val corner = RebarBar(4, cornerDiameter)
                    val faceOptions = if (faceCount > 0) {
                        diametersMm.filter { it >= 12 && it <= cornerDiameter }
                    } else {
                        listOf(cornerDiameter)
                    }
                    for (faceDiameter in faceOptions) {
                        val face = if (faceCount > 0) RebarBar(faceCount, faceDiameter) else null
                        val area = corner.areaCm2 + (face?.areaCm2 ?: 0.0)
                        if (area + 1e-9 < requiredAsCm2) continue
                        if (area > maximumAsCm2 + 1e-9) continue
                        candidates += Candidate(
                            barsLong = barsLong,
                            barsShort = barsShort,
                            corner = corner,
                            face = face,
                            totalBars = totalBars,
                            area = area,
                            spacingLong = spacingLong,
                            spacingShort = spacingShort
                        )
                    }
                }
            }
        }

        val best = candidates.minWithOrNull(
            compareBy<Candidate> { it.area }
                .thenBy { it.totalBars }
                .thenBy { it.corner.diameterMm }
                .thenBy { it.face?.diameterMm ?: 0 }
        )
        if (best != null) {
            val minDiameter = min(best.corner.diameterMm, best.face?.diameterMm ?: best.corner.diameterMm)
            val maxDiameter = max(best.corner.diameterMm, best.face?.diameterMm ?: best.corner.diameterMm)
            return ColumnRebarLayout(
                cornerBar = best.corner,
                faceBar = best.face,
                barsPerLongFace = best.barsLong,
                barsPerShortFace = best.barsShort,
                totalBars = best.totalBars,
                areaCm2 = best.area,
                spacingLongFaceCm = best.spacingLong,
                spacingShortFaceCm = best.spacingShort,
                minDiameterMm = minDiameter,
                maxDiameterMm = maxDiameter
            )
        }

        // Repli : la section exige plus que le pourcentage maximal. On retourne la
        // disposition la plus ferraillee possible pour documenter le depassement.
        val fallbackCorner = RebarBar(4, diametersMm.max())
        val fallbackFace = RebarBar(24, diametersMm.max())
        return ColumnRebarLayout(
            cornerBar = fallbackCorner,
            faceBar = fallbackFace,
            barsPerLongFace = 4,
            barsPerShortFace = 4,
            totalBars = 28,
            areaCm2 = fallbackCorner.areaCm2 + fallbackFace.areaCm2,
            spacingLongFaceCm = (longFace - 2.0 * steelCoverCm) / 3.0,
            spacingShortFaceCm = (shortFace - 2.0 * steelCoverCm) / 3.0,
            minDiameterMm = diametersMm.max(),
            maxDiameterMm = diametersMm.max()
        )
    }

    fun selectBeamLayout(
        bCm: Double,
        hCm: Double,
        steelCoverCm: Double,
        requiredAsCm2: Double,
        minimumContinuousAsCm2: Double = 0.0,
        additionalRole: String = "renforts",
        maximumAsCm2: Double = 0.04 * bCm * hCm,
        minimumDiameterMm: Int = 12
    ): BeamRebarLayout? {
        if (bCm <= 0.0 || hCm <= 0.0) return null
        val barDiameters = diametersMm.filter { it >= minimumDiameterMm }

        data class Candidate(
            val continuousCount: Int,
            val continuousDiameter: Int,
            val additionalCount: Int,
            val additionalDiameter: Int,
            val totalBars: Int,
            val area: Double,
            val continuousArea: Double
        )

        val candidates = mutableListOf<Candidate>()
        for (continuousCount in listOf(2, 3)) {
            for (continuousDiameter in barDiameters) {
                val continuous = RebarBar(continuousCount, continuousDiameter)
                if (continuous.areaCm2 + 1e-9 < minimumContinuousAsCm2) continue
                for (additionalCount in 0..6) {
                    val additionalOptions = if (additionalCount == 0) listOf(0) else barDiameters
                    for (additionalDiameter in additionalOptions) {
                        val additional = if (additionalCount > 0) {
                            RebarBar(additionalCount, additionalDiameter)
                        } else {
                            null
                        }
                        val totalBars = continuousCount + additionalCount
                        val area = continuous.areaCm2 + (additional?.areaCm2 ?: 0.0)
                        if (area + 1e-9 < requiredAsCm2) continue
                        if (area > maximumAsCm2 + 1e-9) continue
                        val maxDiameterCm = max(continuousDiameter, additionalDiameter) / 10.0
                        val minimumWidth = totalBars * maxDiameterCm + (totalBars - 1) * 2.5
                        if (minimumWidth > bCm - 2.0 * steelCoverCm + 1e-6) continue
                        candidates += Candidate(
                            continuousCount = continuousCount,
                            continuousDiameter = continuousDiameter,
                            additionalCount = additionalCount,
                            additionalDiameter = additionalDiameter,
                            totalBars = totalBars,
                            area = area,
                            continuousArea = continuous.areaCm2
                        )
                    }
                }
            }
        }

        val best = candidates.minWithOrNull(
            compareBy<Candidate> { it.area }
                .thenBy { it.totalBars }
                .thenBy { max(it.continuousDiameter, it.additionalDiameter) }
        )
        if (best != null) {
            val continuous = RebarBar(best.continuousCount, best.continuousDiameter)
            val additional = if (best.additionalCount > 0) {
                RebarBar(best.additionalCount, best.additionalDiameter)
            } else {
                null
            }
            val minDiameter = min(
                best.continuousDiameter,
                if (best.additionalCount > 0) best.additionalDiameter else best.continuousDiameter
            )
            val maxDiameter = max(
                best.continuousDiameter,
                if (best.additionalCount > 0) best.additionalDiameter else best.continuousDiameter
            )
            return BeamRebarLayout(
                continuousBar = continuous,
                additionalBar = additional,
                totalBars = best.totalBars,
                areaCm2 = best.area,
                continuousAreaCm2 = best.continuousArea,
                minDiameterMm = minDiameter,
                maxDiameterMm = maxDiameter,
                additionalRole = additionalRole
            )
        }

        // Repli : la largeur ne permet pas de loger le ferraillage requis sur un lit.
        val continuous = RebarBar(3, diametersMm.max())
        val additional = RebarBar(6, diametersMm.max())
        return BeamRebarLayout(
            continuousBar = continuous,
            additionalBar = additional,
            totalBars = 9,
            areaCm2 = continuous.areaCm2 + additional.areaCm2,
            continuousAreaCm2 = continuous.areaCm2,
            minDiameterMm = diametersMm.max(),
            maxDiameterMm = diametersMm.max(),
            additionalRole = additionalRole
        )
    }

    private fun barsForSpacing(faceCm: Double, coverCm: Double, spacingLimitCm: Double): Int {
        val available = (faceCm - 2.0 * coverCm).coerceAtLeast(0.0)
        if (available <= 0.0) return 2
        return max(2, ceil(available / spacingLimitCm).toInt() + 1)
    }
}
