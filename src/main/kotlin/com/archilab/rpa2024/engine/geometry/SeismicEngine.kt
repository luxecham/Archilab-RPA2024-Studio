package com.archilab.rpa2024.engine.geometry

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.ResolvedSeismicConfig
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.DiaphragmDisplacementRow
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.ModalRatioRow
import kotlin.math.abs

/** Force sismique repartie par niveau selon Eq. 4.5 du RPA 2024. */
data class SeismicStoryForce(
    val story: String,
    val weightKN: Double,
    val heightMeters: Double,
    val lateralForceKN: Double,
    val shearKN: Double
)

/** Verification du deplacement inter-etages (RPA 2024 § 5.10, Eq. 5.12). */
data class DriftCheck(
    val story: String,
    val storyHeightMeters: Double,
    val elasticDisplacementMeters: Double,
    val designDisplacementMeters: Double,
    val driftMeters: Double,
    val allowedDriftMeters: Double,
    val reducedDriftMeters: Double,
    val status: CheckStatus,
    val message: String = ""
)

/** Verification de l'effet P-Delta (RPA 2024 § 5.9, Eq. 5.10). */
data class PDeltaCheck(
    val story: String,
    val cumulativeWeightKN: Double,
    val storyShearKN: Double,
    val driftMeters: Double,
    val storyHeightMeters: Double,
    val theta: Double,
    val status: CheckStatus
)

/** Synthese de l'etude sismique d'un joint (chapitre 7 de la note de calcul). */
data class SeismicAssessment(
    val resolved: ResolvedSeismicConfig,
    val seismicWeightKN: Double?,
    val periodXSeconds: Double?,
    val periodYSeconds: Double?,
    val staticBaseShearXKN: Double?,
    val staticBaseShearYKN: Double?,
    val dynamicBaseShearXKN: Double?,
    val dynamicBaseShearYKN: Double?,
    val storyForcesX: List<SeismicStoryForce>,
    val storyForcesY: List<SeismicStoryForce>,
    val driftChecksX: List<DriftCheck>,
    val driftChecksY: List<DriftCheck>,
    val pDeltaChecksX: List<PDeltaCheck>,
    val pDeltaChecksY: List<PDeltaCheck>,
    val modalRatios: List<ModalRatioRow>,
    val notes: List<String> = emptyList()
) {
    val driftStatus: CheckStatus
        get() = CheckStatus.worst((driftChecksX + driftChecksY).map { it.status })

    val pDeltaStatus: CheckStatus
        get() = CheckStatus.worst((pDeltaChecksX + pDeltaChecksY).map { it.status })

    val staticDynamicRatioX: Double?
        get() = ratio(staticBaseShearXKN, dynamicBaseShearXKN)

    val staticDynamicRatioY: Double?
        get() = ratio(staticBaseShearYKN, dynamicBaseShearYKN)

    private fun ratio(static: Double?, dynamic: Double?): Double? =
        if (static == null || dynamic == null || dynamic == 0.0) null else static / dynamic
}

/**
 * Moteur de l'etude sismique : force statique equivalente (Eq. 4.1), repartition par niveau,
 * comparaison a la reaction de base ETABS, deplacements inter-etages et effet P-Delta.
 */
class SeismicEngine {

    fun assess(
        joint: StructuralJoint,
        resolved: ResolvedSeismicConfig,
        results: EtabsResults,
        manualWeightKN: Double? = null
    ): SeismicAssessment {
        val notes = mutableListOf<String>()
        val weight = manualWeightKN ?: results.seismicWeightKN
        if (weight == null) {
            notes += "Poids sismique W indisponible : renseigner un poids manuel ou importer la table Story Shears (cas WI)."
        } else if (results.seismicWeightKN == null) {
            notes += "Poids sismique W = ${Checks.format(weight)} kN fourni manuellement."
        }

        val analyticalPeriod = resolved.empiricalPeriodSeconds.takeIf { it > 0.0 }
        val modalPeriodX = fundamentalPeriod(results.modalRatios, AxisDirection.X) ?: analyticalPeriod
        val modalPeriodY = fundamentalPeriod(results.modalRatios, AxisDirection.Y) ?: analyticalPeriod

        val staticX = staticBaseShear(resolved, weight, modalPeriodX)
        val staticY = staticBaseShear(resolved, weight, modalPeriodY)
        val dynamicX = results.baseSeismicShearKN(AxisDirection.X) ?: fallbackShear(results, AxisDirection.X)
        val dynamicY = results.baseSeismicShearKN(AxisDirection.Y) ?: fallbackShear(results, AxisDirection.Y)

        if (dynamicX != null && staticX != null && dynamicX + 1e-6 < staticX) {
            notes += "Vx dynamique (${Checks.format(dynamicX)} kN) < Vx statique (${Checks.format(staticX)} kN) : " +
                "la valeur dynamique doit être corrigée par le rapport Vstatique/Vdynamique (RPA 2024 § 4.3.5)."
        }
        if (dynamicY != null && staticY != null && dynamicY + 1e-6 < staticY) {
            notes += "Vy dynamique (${Checks.format(dynamicY)} kN) < Vy statique (${Checks.format(staticY)} kN) : " +
                "correction dynamique requise (RPA 2024 § 4.3.5)."
        }

        val cumulativeWeights = results.cumulativeWeightByStoryKN()
        val storyWeights = storyWeights(joint, cumulativeWeights)
        val forcesX = distribute(staticX, joint, storyWeights)
        val forcesY = distribute(staticY, joint, storyWeights)

        val driftX = driftChecks(joint, results, AxisDirection.X, resolved)
        val driftY = driftChecks(joint, results, AxisDirection.Y, resolved)
        val pDeltaX = pDeltaChecks(joint, results, AxisDirection.X, driftX, cumulativeWeights)
        val pDeltaY = pDeltaChecks(joint, results, AxisDirection.Y, driftY, cumulativeWeights)

        if (driftX.isEmpty() && driftY.isEmpty()) {
            notes += "Déplacements non extraits : importer la table 'Diaphragm CM Displacements' sous les cas EX et EY."
        }
        if (results.modalRatios.isNotEmpty()) {
            val sumUx = results.modalRatios.maxOf { it.sumUxPercent }
            val sumUy = results.modalRatios.maxOf { it.sumUyPercent }
            if (sumUx < 90.0 || sumUy < 90.0) {
                notes += "Participation massique cumulée insuffisante : ${Checks.format(sumUx)} % en X, " +
                    "${Checks.format(sumUy)} % en Y (90 % exigés par le RPA 2024 § 4.3.2)."
            }
        }

        return SeismicAssessment(
            resolved = resolved,
            seismicWeightKN = weight,
            periodXSeconds = modalPeriodX,
            periodYSeconds = modalPeriodY,
            staticBaseShearXKN = staticX,
            staticBaseShearYKN = staticY,
            dynamicBaseShearXKN = dynamicX,
            dynamicBaseShearYKN = dynamicY,
            storyForcesX = forcesX,
            storyForcesY = forcesY,
            driftChecksX = driftX,
            driftChecksY = driftY,
            pDeltaChecksX = pDeltaX,
            pDeltaChecksY = pDeltaY,
            modalRatios = results.modalRatios,
            notes = notes
        )
    }

    private fun staticBaseShear(
        resolved: ResolvedSeismicConfig,
        weightKN: Double?,
        periodSeconds: Double?
    ): Double? {
        if (weightKN == null || periodSeconds == null) return null
        val spectrum = resolved.designAccelerationOverG(periodSeconds) ?: return null
        return spectrum * resolved.lambda * weightKN
    }

    private fun fundamentalPeriod(rows: List<ModalRatioRow>, direction: AxisDirection): Double? {
        if (rows.isEmpty()) return null
        val reference = rows.maxByOrNull {
            if (direction == AxisDirection.X) it.uxPercent else it.uyPercent
        } ?: return null
        val participation = if (direction == AxisDirection.X) reference.uxPercent else reference.uyPercent
        return reference.periodSeconds.takeIf { participation > 1e-9 }
    }

    private fun fallbackShear(results: EtabsResults, direction: AxisDirection): Double? {
        val row = results.storyShears
            .filter { it.isBottom && isSeismicCase(it.loadCase, direction) }
            .maxByOrNull { if (direction == AxisDirection.X) abs(it.vxKN) else abs(it.vyKN) }
            ?: return null
        return if (direction == AxisDirection.X) abs(row.vxKN) else abs(row.vyKN)
    }

    private fun storyWeights(joint: StructuralJoint, cumulative: Map<String, Double>): Map<String, Double> {
        if (cumulative.isEmpty()) return emptyMap()
        val ordered = joint.storySystem.orderedStories
        val weights = linkedMapOf<String, Double>()
        for (index in ordered.indices) {
            val story = ordered[index]
            val current = cumulative[story.storyName] ?: continue
            val above = ordered.getOrNull(index + 1)?.let { cumulative[it.storyName] } ?: 0.0
            weights[story.storyName] = (current - above).coerceAtLeast(0.0)
        }
        return weights
    }

    private fun distribute(
        baseShearKN: Double?,
        joint: StructuralJoint,
        storyWeights: Map<String, Double>
    ): List<SeismicStoryForce> {
        if (baseShearKN == null || storyWeights.isEmpty()) return emptyList()
        val base = joint.storySystem.baseElevationZ
        val levels = joint.storySystem.orderedStories.mapNotNull { story ->
            val weight = storyWeights[story.storyName] ?: return@mapNotNull null
            val height = (story.topElevationZk - base).coerceAtLeast(0.0)
            Triple(story.storyName, weight, height)
        }
        val denominator = levels.sumOf { it.second * it.third }
        if (denominator <= 1e-9) return emptyList()

        val forces = mutableListOf<Double>()
        val result = mutableListOf<SeismicStoryForce>()
        for ((name, weight, height) in levels) {
            val force = baseShearKN * weight * height / denominator
            forces += force
            result += SeismicStoryForce(name, weight, height, force, 0.0)
        }
        var shear = 0.0
        for (index in result.indices.reversed()) {
            shear += forces[index]
            result[index] = result[index].copy(shearKN = shear)
        }
        return result
    }

    private fun driftChecks(
        joint: StructuralJoint,
        results: EtabsResults,
        direction: AxisDirection,
        resolved: ResolvedSeismicConfig
    ): List<DriftCheck> {
        if (results.diaphragmDisplacements.isEmpty()) return emptyList()
        val base = joint.storySystem.baseElevationZ
        val amplification = resolved.behaviorFactorR / resolved.qualityFactor.qf
        val nuA = resolved.importance.nuA
        val limitRatio = 0.0075

        var previousDesign = 0.0
        val checks = mutableListOf<DriftCheck>()
        for (story in joint.storySystem.orderedStories) {
            val elastic = results.diaphragmDisplacements
                .filter { it.story.equals(story.storyName, ignoreCase = true) && isSeismicCase(it.loadCase, direction) }
                .maxOfOrNull { abs(if (direction == AxisDirection.X) it.uxMeters else it.uyMeters) }
                ?: continue
            val design = amplification * elastic
            val drift = (design - previousDesign).coerceAtLeast(0.0)
            previousDesign = design
            val allowed = limitRatio * story.heightHk
            val reduced = nuA * drift
            val status = if (reduced <= allowed + 1e-9) CheckStatus.OK else CheckStatus.FAILED
            checks += DriftCheck(
                story = story.storyName,
                storyHeightMeters = story.heightHk,
                elasticDisplacementMeters = elastic,
                designDisplacementMeters = design,
                driftMeters = drift,
                allowedDriftMeters = allowed,
                reducedDriftMeters = reduced,
                status = status,
                message = if (status == CheckStatus.FAILED) {
                    "νA·Δk = ${Checks.format(reduced * 100.0, 2)} cm > ${Checks.format(allowed * 100.0, 2)} cm " +
                        "(0,0075 hk) au niveau '${story.storyName}'."
                } else ""
            )
        }
        return checks
    }

    private fun pDeltaChecks(
        joint: StructuralJoint,
        results: EtabsResults,
        direction: AxisDirection,
        drifts: List<DriftCheck>,
        cumulativeWeights: Map<String, Double>
    ): List<PDeltaCheck> {
        if (drifts.isEmpty() || cumulativeWeights.isEmpty()) return emptyList()
        val checks = mutableListOf<PDeltaCheck>()
        for (drift in drifts) {
            val p = cumulativeWeights[drift.story] ?: continue
            val shear = results.storyShears
                .filter {
                    it.story.equals(drift.story, ignoreCase = true) &&
                        isSeismicCase(it.loadCase, direction) &&
                        (it.isBottom || it.location.isBlank())
                }
                .maxOfOrNull { abs(if (direction == AxisDirection.X) it.vxKN else it.vyKN) }
                ?: continue
            if (shear <= 1e-9 || drift.storyHeightMeters <= 1e-9) continue
            val theta = p * drift.driftMeters / (shear * drift.storyHeightMeters)
            val status = when {
                theta <= 0.10 -> CheckStatus.OK
                theta <= 0.20 -> CheckStatus.WARNING
                else -> CheckStatus.FAILED
            }
            checks += PDeltaCheck(
                story = drift.story,
                cumulativeWeightKN = p,
                storyShearKN = shear,
                driftMeters = drift.driftMeters,
                storyHeightMeters = drift.storyHeightMeters,
                theta = theta,
                status = status
            )
        }
        return checks
    }

    private fun isSeismicCase(loadCase: String, direction: AxisDirection): Boolean {
        val normalized = loadCase.uppercase()
        val directional = when (direction) {
            AxisDirection.X ->
                normalized.startsWith("EX") || normalized.startsWith("RSX") || normalized.startsWith("SPX") ||
                    normalized.contains("SISMIQUE X") || normalized.contains("SISMIQUE-X")
            AxisDirection.Y ->
                normalized.startsWith("EY") || normalized.startsWith("RSY") || normalized.startsWith("SPY") ||
                    normalized.contains("SISMIQUE Y") || normalized.contains("SISMIQUE-Y")
        }
        return directional || (ComboCategory.classify(loadCase) == ComboCategory.ELA && direction == AxisDirection.X)
    }
}
