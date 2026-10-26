package com.archilab.rpa2024.engine.slabs

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Caracteristiques geometriques de la section en T homogene non fissuree. */
data class TSectionProperties(
    val areaM2: Double,
    val centroidFromTopMeters: Double,
    val centroidFromBottomMeters: Double,
    val inertiaM4: Double
) {
    /** Module d'inertie I/v' utilise pour la condition de non-fragilite des sections en T. */
    val tensionSectionModulusM3: Double
        get() = if (centroidFromBottomMeters <= 0.0) 0.0 else inertiaM4 / centroidFromBottomMeters
}

/** Section fissuree elastique : axe neutre et inertie fissuree. */
data class CrackedSectionProperties(
    val neutralAxisMeters: Double,
    val inertiaM4: Double,
    val equivalenceCoefficient: Double
) {
    /** Contrainte de beton en MPa pour un moment en kN.m. */
    fun concreteStressMPa(momentKNm: Double): Double =
        if (inertiaM4 <= 0.0) 0.0 else abs(momentKNm) * neutralAxisMeters / inertiaM4 / 1000.0

    /** Contrainte d'acier en MPa pour un moment en kN.m. */
    fun steelStressMPa(momentKNm: Double, effectiveDepthMeters: Double): Double =
        if (inertiaM4 <= 0.0) 0.0 else {
            equivalenceCoefficient * abs(momentKNm) * (effectiveDepthMeters - neutralAxisMeters) / inertiaM4 / 1000.0
        }
}

/**
 * Mecanique des sections en T en beton arme :
 * - caracteristiques de la section homogene non fissuree (Annexe B.6.5 BAEL 91) ;
 * - axe neutre et inertie de la section fissuree avec le coefficient d'equivalence n = 15.
 */
internal object ConcreteSectionMechanics {

    fun teeProperties(
        flangeWidthMeters: Double,
        webWidthMeters: Double,
        totalHeightMeters: Double,
        slabThicknessMeters: Double
    ): TSectionProperties {
        require(flangeWidthMeters > 0.0 && webWidthMeters > 0.0) { "Largeurs de section invalides." }
        require(totalHeightMeters > 0.0) { "Hauteur de section invalide." }
        val h0 = slabThicknessMeters.coerceIn(0.0, totalHeightMeters)
        val webHeight = totalHeightMeters - h0

        val tableArea = flangeWidthMeters * h0
        val tableCentroid = h0 / 2.0
        val webArea = webWidthMeters * webHeight
        val webCentroid = h0 + webHeight / 2.0
        val totalArea = tableArea + webArea
        require(totalArea > 0.0) { "Aire de section nulle." }

        val centroid = (tableArea * tableCentroid + webArea * webCentroid) / totalArea
        val inertia = flangeWidthMeters * h0 * h0 * h0 / 12.0 +
            tableArea * (tableCentroid - centroid) * (tableCentroid - centroid) +
            webWidthMeters * webHeight * webHeight * webHeight / 12.0 +
            webArea * (webCentroid - centroid) * (webCentroid - centroid)

        return TSectionProperties(
            areaM2 = totalArea,
            centroidFromTopMeters = centroid,
            centroidFromBottomMeters = totalHeightMeters - centroid,
            inertiaM4 = inertia
        )
    }

    /**
     * Section totale rendue homogene avec n = 15 : beton de la table et de la nervure
     * plus acier tendu transforme place a la hauteur utile. C'est le I0 de l'annexe B.6.5.
     */
    fun homogenizedTeeProperties(
        flangeWidthMeters: Double,
        webWidthMeters: Double,
        totalHeightMeters: Double,
        slabThicknessMeters: Double,
        steelAreaM2: Double,
        effectiveDepthMeters: Double,
        equivalenceCoefficient: Double = 15.0
    ): TSectionProperties {
        val concrete = teeProperties(
            flangeWidthMeters,
            webWidthMeters,
            totalHeightMeters,
            slabThicknessMeters
        )
        require(steelAreaM2 > 0.0) { "Section d'acier nulle : section homogene impossible." }
        val transformedSteelArea = equivalenceCoefficient * steelAreaM2
        val totalArea = concrete.areaM2 + transformedSteelArea
        val centroid = (
            concrete.areaM2 * concrete.centroidFromTopMeters +
                transformedSteelArea * effectiveDepthMeters
            ) / totalArea
        val concreteOffset = centroid - concrete.centroidFromTopMeters
        val steelOffset = effectiveDepthMeters - centroid
        val inertia = concrete.inertiaM4 +
            concrete.areaM2 * concreteOffset * concreteOffset +
            transformedSteelArea * steelOffset * steelOffset
        return TSectionProperties(
            areaM2 = totalArea,
            centroidFromTopMeters = centroid,
            centroidFromBottomMeters = totalHeightMeters - centroid,
            inertiaM4 = inertia
        )
    }

    /**
     * Resout l'axe neutre elastique de la section fissuree :
     * moment statique de la zone comprimee = n.As.(d - y1).
     * L'equation est resolus par dichotomie, que l'axe soit dans la table ou dans la nervure.
     */
    fun crackedSection(
        flangeWidthMeters: Double,
        webWidthMeters: Double,
        slabThicknessMeters: Double,
        effectiveDepthMeters: Double,
        steelAreaM2: Double,
        equivalenceCoefficient: Double = 15.0
    ): CrackedSectionProperties {
        require(effectiveDepthMeters > 0.0) { "Hauteur utile invalide." }
        require(steelAreaM2 > 0.0) { "Section d'acier nulle : calcul ELS impossible." }

        val b = flangeWidthMeters
        val b0 = webWidthMeters
        val h0 = slabThicknessMeters.coerceAtLeast(0.0)
        val d = effectiveDepthMeters
        val nAs = equivalenceCoefficient * steelAreaM2

        fun compressionFirstMoment(y: Double): Double =
            if (h0 > 0.0 && y <= h0) {
                b * y * y / 2.0
            } else {
                b * h0 * (y - h0 / 2.0) + b0 * (y - h0) * (y - h0) / 2.0
            }

        fun equilibrium(y: Double): Double = compressionFirstMoment(y) - nAs * (d - y)

        var low = 0.0
        var high = d
        var fLow = equilibrium(low)
        var fHigh = equilibrium(high)
        if (fHigh < 0.0) {
            // Section sur-armee : l'axe neutre est rejete a la fibre la plus comprimee.
            high = d
        }
        repeat(120) {
            val mid = (low + high) / 2.0
            val fMid = equilibrium(mid)
            if (fMid > 0.0) {
                high = mid
                fHigh = fMid
            } else {
                low = mid
                fLow = fMid
            }
            if (abs(high - low) < 1e-10) return@repeat
        }
        val y = (low + high) / 2.0
        val concreteInertia = if (h0 > 0.0 && y <= h0) {
            b * y * y * y / 3.0
        } else {
            b * h0 * h0 * h0 / 12.0 +
                b * h0 * (y - h0 / 2.0) * (y - h0 / 2.0) +
                b0 * (y - h0) * (y - h0) * (y - h0) / 3.0
        }
        val inertia = concreteInertia + nAs * (d - y) * (d - y)

        // fLow / fHigh sont conserves pour la lisibilite de la dichotomie.
        check(!fLow.isNaN() && !fHigh.isNaN()) { "Dichotomie non convergente." }
        return CrackedSectionProperties(y, inertia, equivalenceCoefficient)
    }
}

/**
 * Donnees de la fiche de fleche d'une poutrelle en T.
 * Les moments sont ceux de la travée la plus longue, en kN.m et en valeur positive.
 */
data class TBeamDeflectionInput(
    val flangeWidthMeters: Double,
    val webWidthMeters: Double,
    val totalHeightMeters: Double,
    val slabThicknessMeters: Double,
    val effectiveDepthMeters: Double,
    val steelAreaCm2: Double,
    val spanMeters: Double,
    val momentBeforeFinishesKNm: Double,
    val momentPermanentKNm: Double,
    val momentTotalServiceKNm: Double,
    val fc28MPa: Double,
    val ft28MPa: Double,
    val eijMPa: Double,
    val evjMPa: Double,
    val feMPa: Double,
    val equivalenceCoefficient: Double = 15.0,
    val concreteStressLimitMPa: Double = 0.6 * fc28MPa,
    val steelStressLimitMPa: Double = 0.8 * feMPa
) {
    init {
        require(flangeWidthMeters > 0.0 && webWidthMeters > 0.0) { "Largeurs de poutrelle invalides." }
        require(totalHeightMeters > 0.0) { "Hauteur de poutrelle invalide." }
        require(effectiveDepthMeters > 0.0) { "Hauteur utile invalide." }
        require(steelAreaCm2 > 0.0) { "Section d'acier nulle." }
        require(spanMeters > 0.0) { "Portee invalide." }
        require(eijMPa > 0.0 && evjMPa > 0.0) { "Modules de deformation invalides." }
    }
}

/** Resultat complet de la verification de fleche BAEL 91 revise 99. */
data class TBeamDeflectionResult(
    val section: TSectionProperties,
    val homogenizedSection: TSectionProperties,
    val crackedSection: CrackedSectionProperties,
    val rho: Double,
    val sigmaSjMPa: Double,
    val sigmaSgMPa: Double,
    val sigmaSpMPa: Double,
    val muJ: Double,
    val muG: Double,
    val muP: Double,
    val lambdaInstant: Double,
    val lambdaDeferred: Double,
    val inertiaFjiM4: Double,
    val inertiaFgiM4: Double,
    val inertiaFgvM4: Double,
    val inertiaFpiM4: Double,
    val fjiMeters: Double,
    val fgiMeters: Double,
    val fgvMeters: Double,
    val fpiMeters: Double,
    val fgqMeters: Double,
    val deltaFtMeters: Double,
    val allowableMeters: Double,
    val utilization: Double,
    val status: CheckStatus,
    val checks: List<DesignCheck>,
    val messages: List<String> = emptyList()
)

/**
 * Calcul de la fleche d'une poutrelle en T selon la methode BAEL 91 revise 99
 * (Annexe B.6.5) et reproduction de la fiche de fleche du logiciel Koka :
 * inerties fissurees instantanees et differees, fleches partielles fji, fgi, fgv, fpi, fgq,
 * puis fleche totale nuisible Delta ft = fgq - fji.
 */
class TBeamDeflectionCalculator {

    fun calculate(input: TBeamDeflectionInput): TBeamDeflectionResult {
        val messages = mutableListOf<String>()
        val section = ConcreteSectionMechanics.teeProperties(
            input.flangeWidthMeters,
            input.webWidthMeters,
            input.totalHeightMeters,
            input.slabThicknessMeters
        )
        val steelAreaM2 = input.steelAreaCm2 / 10_000.0
        val homogenized = ConcreteSectionMechanics.homogenizedTeeProperties(
            flangeWidthMeters = input.flangeWidthMeters,
            webWidthMeters = input.webWidthMeters,
            totalHeightMeters = input.totalHeightMeters,
            slabThicknessMeters = input.slabThicknessMeters,
            steelAreaM2 = steelAreaM2,
            effectiveDepthMeters = input.effectiveDepthMeters,
            equivalenceCoefficient = input.equivalenceCoefficient
        )
        val cracked = ConcreteSectionMechanics.crackedSection(
            flangeWidthMeters = input.flangeWidthMeters,
            webWidthMeters = input.webWidthMeters,
            slabThicknessMeters = input.slabThicknessMeters,
            effectiveDepthMeters = input.effectiveDepthMeters,
            steelAreaM2 = steelAreaM2,
            equivalenceCoefficient = input.equivalenceCoefficient
        )

        val rho = steelAreaM2 / (input.webWidthMeters * input.effectiveDepthMeters)
        if (rho <= 1e-9) {
            messages += "Le pourcentage d'armatures tendu est nul : la fleche n'est pas calculable."
        }

        val sigmaSj = cracked.steelStressMPa(input.momentBeforeFinishesKNm, input.effectiveDepthMeters)
        val sigmaSg = cracked.steelStressMPa(input.momentPermanentKNm, input.effectiveDepthMeters)
        val sigmaSp = cracked.steelStressMPa(input.momentTotalServiceKNm, input.effectiveDepthMeters)

        fun mu(sigmaS: Double): Double {
            if (rho <= 1e-9) return 0.0
            val denominator = 4.0 * rho * sigmaS + input.ft28MPa
            if (denominator <= 0.0) return 0.0
            return max(0.0, 1.0 - 1.75 * input.ft28MPa / denominator)
        }

        val muJ = mu(sigmaSj)
        val muG = mu(sigmaSg)
        val muP = mu(sigmaSp)

        val lambdaInstant = if (rho <= 1e-9) {
            0.0
        } else {
            0.05 * input.ft28MPa / ((2.0 + 3.0 * input.webWidthMeters / input.flangeWidthMeters) * rho)
        }
        val lambdaDeferred = 0.4 * lambdaInstant

        fun fissuredInertia(base: Double, lambda: Double, coefficient: Double): Double {
            val factor = 1.0 + lambda * coefficient
            return if (factor <= 1e-9) base else base / factor
        }

        // BAEL B.6.5 : If = I0 / (1 + lambda.mu), I0 etant la section totale rendue homogene (n = 15).
        val baseInertia = homogenized.inertiaM4
        val inertiaFji = fissuredInertia(baseInertia, lambdaInstant, muJ)
        val inertiaFgi = fissuredInertia(baseInertia, lambdaInstant, muG)
        val inertiaFgv = fissuredInertia(baseInertia, lambdaDeferred, muG)
        val inertiaFpi = fissuredInertia(baseInertia, lambdaInstant, muP)

        fun deflection(momentKNm: Double, modulusMPa: Double, inertiaM4: Double): Double {
            if (inertiaM4 <= 0.0 || modulusMPa <= 0.0) return 0.0
            // E en MPa = 1000 kN/m2 ; M en kN.m, L en m et I en m4 : resultat en metres.
            return abs(momentKNm) * input.spanMeters * input.spanMeters /
                (10.0 * modulusMPa * 1000.0 * inertiaM4)
        }

        val fji = deflection(input.momentBeforeFinishesKNm, input.eijMPa, inertiaFji)
        val fgi = deflection(input.momentPermanentKNm, input.eijMPa, inertiaFgi)
        val fgv = deflection(input.momentPermanentKNm, input.evjMPa, inertiaFgv)
        val fpi = deflection(input.momentTotalServiceKNm, input.eijMPa, inertiaFpi)
        val fgq = fpi - fgi + fgv
        val deltaFt = fgq - fji

        val allowable = if (input.spanMeters <= 5.0) {
            input.spanMeters / 500.0
        } else {
            0.005 + input.spanMeters / 1000.0
        }

        if (input.momentBeforeFinishesKNm > input.momentPermanentKNm + 1e-6) {
            messages += "Le moment avant cloisons dépasse le moment permanent total : vérifier la décomposition des charges."
        }

        val checks = listOf(
            Checks.max(
                code = "FLECHE_BRUYANTE",
                label = "Flèche totale nuisible",
                reference = "BAEL 91 révisé 99, Annexe B.6.5",
                provided = deltaFt,
                maximum = allowable,
                unit = "m",
                decimals = 4,
                failureMessage = "Flèche nuisible ${Checks.format(deltaFt * 1000.0, 2)} mm > " +
                    "flèche admissible ${Checks.format(allowable * 1000.0, 2)} mm."
            ),
            Checks.max(
                code = "SIGMA_BC_ELS",
                label = "Contrainte béton ELS",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                provided = cracked.concreteStressMPa(input.momentTotalServiceKNm),
                maximum = input.concreteStressLimitMPa,
                unit = "MPa",
                decimals = 2
            ),
            Checks.max(
                code = "SIGMA_ST_ELS",
                label = "Contrainte acier ELS",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                provided = sigmaSp,
                maximum = input.steelStressLimitMPa,
                unit = "MPa",
                decimals = 1
            )
        )

        val status = CheckStatus.worst(checks.map { it.status })
        val utilization = if (allowable <= 0.0) 0.0 else deltaFt / allowable

        return TBeamDeflectionResult(
            section = section,
            homogenizedSection = homogenized,
            crackedSection = cracked,
            rho = rho,
            sigmaSjMPa = sigmaSj,
            sigmaSgMPa = sigmaSg,
            sigmaSpMPa = sigmaSp,
            muJ = muJ,
            muG = muG,
            muP = muP,
            lambdaInstant = lambdaInstant,
            lambdaDeferred = lambdaDeferred,
            inertiaFjiM4 = inertiaFji,
            inertiaFgiM4 = inertiaFgi,
            inertiaFgvM4 = inertiaFgv,
            inertiaFpiM4 = inertiaFpi,
            fjiMeters = fji,
            fgiMeters = fgi,
            fgvMeters = fgv,
            fpiMeters = fpi,
            fgqMeters = fgq,
            deltaFtMeters = deltaFt,
            allowableMeters = allowable,
            utilization = utilization,
            status = status,
            checks = checks,
            messages = messages
        )
    }
}
