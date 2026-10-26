package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.AxisDirection
import kotlin.math.abs

enum class ComboCategory {
    ELU,
    ELS,
    ELA,
    OTHER;

    companion object {
        fun classify(loadCase: String): ComboCategory {
            val compact = loadCase.uppercase().replace(" ", "")
            // Convention de nommage Archilab / note CTC : COMB1 = ELU, COMB2..COMB8 = ELA.
            // Les noms explicites (ELU/ELS/ELA, EX/EY, E1/E2, SISM, RPA) restent prioritaires.
            val numberedCombo = Regex("^COMB([0-9]+)$").find(compact)
                ?.groupValues?.get(1)?.toIntOrNull()
            val isExOrEy = compact.startsWith("EX") || compact.startsWith("EY")
            val isSeismicAlias = compact.length in 2..3 &&
                compact.startsWith("E") &&
                compact.drop(1).all { it.isDigit() }
            return when {
                compact.contains("ELA") ||
                    compact.contains("ACC") ||
                    compact.contains("SISM") ||
                    compact.contains("SEIS") ||
                    compact.contains("RPA") ||
                    compact.contains("SPECTR") ||
                    isExOrEy ||
                    isSeismicAlias ||
                    (numberedCombo != null && numberedCombo in 2..8) -> ELA
                numberedCombo == 1 -> ELU
                compact.contains("ELS") -> ELS
                compact.contains("ELU") -> ELU
                else -> OTHER
            }
        }
    }
}

data class ModalRatioRow(
    val mode: Int,
    val periodSeconds: Double,
    val uxPercent: Double,
    val uyPercent: Double,
    val uzPercent: Double,
    val sumUxPercent: Double,
    val sumUyPercent: Double,
    val sumUzPercent: Double,
    val rxPercent: Double = 0.0,
    val ryPercent: Double = 0.0,
    val rzPercent: Double = 0.0
)

data class BaseReactionRow(
    val loadCase: String,
    val fxKN: Double,
    val fyKN: Double,
    val fzKN: Double,
    val story: String? = null
) {
    val isSpectrumCase: Boolean
        get() = ComboCategory.classify(loadCase) == ComboCategory.ELA ||
            loadCase.uppercase().startsWith("EX") ||
            loadCase.uppercase().startsWith("EY")
}

data class StoryShearRow(
    val story: String,
    val location: String,
    val loadCase: String,
    val vxKN: Double,
    val vyKN: Double,
    val pKN: Double = 0.0
) {
    val isBottom: Boolean get() = location.equals("Bottom", ignoreCase = true) || location.isBlank()
    val isSeismicWeightCase: Boolean
        get() {
            val normalized = loadCase.uppercase().replace(" ", "")
            return normalized == "WI" || normalized.startsWith("WI") || normalized.contains("POIDS")
        }
}

data class DiaphragmDisplacementRow(
    val story: String,
    val loadCase: String,
    val uxMeters: Double,
    val uyMeters: Double,
    val uzMeters: Double = 0.0,
    val diaphragm: String? = null
)

/**
 * Ligne de la table ETABS « Centers Of Mass And Rigidity ». Les coordonnees des centres
 * de masse et de rigidite sont converties en metres ; les masses restent dans l'unite
 * declaree par ETABS car elles ne servent qu'a pondérer plusieurs diaphragmes d'un niveau.
 */
data class MassRigidityCenterRow(
    val story: String,
    val diaphragm: String = "",
    val massX: Double = 0.0,
    val massY: Double = 0.0,
    val xcmMeters: Double,
    val ycmMeters: Double,
    val xcrMeters: Double,
    val ycrMeters: Double
) {
    val totalMass: Double get() = massX + massY
}

data class ColumnForceRow(
    val story: String,
    val element: String,
    val loadCase: String,
    val stationMeters: Double,
    val pKN: Double,
    val v2KN: Double,
    val v3KN: Double,
    val m2KNm: Double,
    val m3KNm: Double,
    val lengthMeters: Double? = null
) {
    val category: ComboCategory get() = ComboCategory.classify(loadCase)
}

data class BeamForceRow(
    val story: String,
    val element: String,
    val loadCase: String,
    val stationMeters: Double,
    val pKN: Double,
    val v2KN: Double,
    val v3KN: Double,
    val m2KNm: Double,
    val m3KNm: Double,
    val lengthMeters: Double? = null
) {
    val category: ComboCategory get() = ComboCategory.classify(loadCase)
}

data class PierForceRow(
    val story: String,
    val pier: String,
    val loadCase: String,
    val location: String,
    val stationMeters: Double,
    val pKN: Double,
    val v2KN: Double,
    val v3KN: Double,
    val m2KNm: Double,
    val m3KNm: Double,
    val lengthMeters: Double? = null
) {
    val category: ComboCategory get() = ComboCategory.classify(loadCase)
}

data class ColumnForceEnvelope(
    val story: String,
    val element: String,
    val category: ComboCategory,
    val maxCompressionKN: Double,
    val maxCompressionCombo: String,
    val m2AtMaxCompressionKNm: Double,
    val m3AtMaxCompressionKNm: Double,
    val minAxialKN: Double,
    val minAxialCombo: String,
    val m2AtMinAxialKNm: Double,
    val m3AtMinAxialKNm: Double,
    val maxM2KNm: Double,
    val maxM2Combo: String,
    val axialAtMaxM2KN: Double,
    val m3AtMaxM2KNm: Double,
    val maxM3KNm: Double,
    val maxM3Combo: String,
    val axialAtMaxM3KN: Double,
    val m2AtMaxM3KNm: Double,
    val maxV2KN: Double,
    val maxV2Combo: String,
    val maxV3KN: Double,
    val maxV3Combo: String
)

data class BeamForceEnvelope(
    val story: String,
    val element: String,
    val category: ComboCategory,
    val maxSpanMomentKNm: Double,
    val maxSpanMomentCombo: String,
    val maxSpanMomentStationMeters: Double,
    val maxSupportMomentKNm: Double,
    val maxSupportMomentCombo: String,
    val maxSupportMomentStationMeters: Double,
    val maxShearKN: Double,
    val maxShearCombo: String
)

data class PierLevelForce(
    val story: String,
    val stationMeters: Double,
    val pKN: Double,
    val m3KNm: Double,
    val v2KN: Double
)

data class PierForceProfile(
    val pier: String,
    val category: ComboCategory,
    val baseAxialKN: Double,
    val baseShearV2KN: Double,
    val baseShearV3KN: Double,
    val baseMomentM3KNm: Double,
    val baseCombo: String,
    val levels: List<PierLevelForce> = emptyList()
)

data class EtabsResults(
    val modalRatios: List<ModalRatioRow> = emptyList(),
    val baseReactions: List<BaseReactionRow> = emptyList(),
    val storyShears: List<StoryShearRow> = emptyList(),
    val diaphragmDisplacements: List<DiaphragmDisplacementRow> = emptyList(),
    val columnForces: List<ColumnForceRow> = emptyList(),
    val beamForces: List<BeamForceRow> = emptyList(),
    val pierForces: List<PierForceRow> = emptyList(),
    val massRigidityCenters: List<MassRigidityCenterRow> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val firstModePeriodSeconds: Double?
        get() = modalRatios.minByOrNull { it.mode }?.periodSeconds

    val seismicWeightKN: Double?
        get() = storyShears
            .filter { it.isSeismicWeightCase && it.isBottom }
            .maxOfOrNull { abs(it.pKN) }
            ?: storyShears.filter { it.isSeismicWeightCase }.maxOfOrNull { abs(it.pKN) }

    fun cumulativeWeightByStoryKN(): Map<String, Double> {
        val weightRows = storyShears.filter { it.isSeismicWeightCase }
        return weightRows.groupBy { it.story }.mapValues { (_, rows) ->
            val bottom = rows.filter { it.isBottom }
            (bottom.ifEmpty { rows }).maxOf { abs(it.pKN) }
        }
    }

    fun baseSeismicShearKN(direction: AxisDirection): Double? = baseReactions
        .filter { it.isSpectrumCase }
        .maxOfOrNull { if (direction == AxisDirection.X) abs(it.fxKN) else abs(it.fyKN) }

    fun topDisplacementMeters(direction: AxisDirection): Double? {
        if (diaphragmDisplacements.isEmpty()) return null
        val row = diaphragmDisplacements.maxByOrNull { abs(if (direction == AxisDirection.X) it.uxMeters else it.uyMeters) }
            ?: return null
        return if (direction == AxisDirection.X) abs(row.uxMeters) else abs(row.uyMeters)
    }

    fun columnEnvelopes(category: ComboCategory? = null): List<ColumnForceEnvelope> =
        ColumnEnvelopeBuilder.build(if (category == null) columnForces else columnForces.filter { it.category == category })

    fun beamEnvelopes(category: ComboCategory? = null): List<BeamForceEnvelope> =
        BeamEnvelopeBuilder.build(if (category == null) beamForces else beamForces.filter { it.category == category })

    fun pierProfiles(category: ComboCategory? = null): List<PierForceProfile> =
        PierProfileBuilder.build(if (category == null) pierForces else pierForces.filter { it.category == category })
}

object ColumnEnvelopeBuilder {

    fun build(rows: List<ColumnForceRow>): List<ColumnForceEnvelope> =
        rows.groupBy { Triple(it.story, it.element, it.category) }
            .map { (key, group) ->
                val (story, element, category) = key
                val maxCompression = group.maxBy { -it.pKN }
                val minAxial = group.minBy { it.pKN }
                val maxM2 = group.maxBy { abs(it.m2KNm) }
                val maxM3 = group.maxBy { abs(it.m3KNm) }
                val maxV2 = group.maxBy { abs(it.v2KN) }
                val maxV3 = group.maxBy { abs(it.v3KN) }
                ColumnForceEnvelope(
                    story = story,
                    element = element,
                    category = category,
                    maxCompressionKN = -maxCompression.pKN,
                    maxCompressionCombo = maxCompression.loadCase,
                    m2AtMaxCompressionKNm = maxCompression.m2KNm,
                    m3AtMaxCompressionKNm = maxCompression.m3KNm,
                    minAxialKN = minAxial.pKN,
                    minAxialCombo = minAxial.loadCase,
                    m2AtMinAxialKNm = minAxial.m2KNm,
                    m3AtMinAxialKNm = minAxial.m3KNm,
                    maxM2KNm = maxM2.m2KNm,
                    maxM2Combo = maxM2.loadCase,
                    axialAtMaxM2KN = maxM2.pKN,
                    m3AtMaxM2KNm = maxM2.m3KNm,
                    maxM3KNm = maxM3.m3KNm,
                    maxM3Combo = maxM3.loadCase,
                    axialAtMaxM3KN = maxM3.pKN,
                    m2AtMaxM3KNm = maxM3.m2KNm,
                    maxV2KN = abs(maxV2.v2KN),
                    maxV2Combo = maxV2.loadCase,
                    maxV3KN = abs(maxV3.v3KN),
                    maxV3Combo = maxV3.loadCase
                )
            }
            .sortedWith(compareBy({ it.story }, { it.element }, { it.category.name }))
}

object BeamEnvelopeBuilder {

    fun build(rows: List<BeamForceRow>): List<BeamForceEnvelope> =
        rows.groupBy { Triple(it.story, it.element, it.category) }
            .map { (key, group) ->
                val (story, element, category) = key
                val length = group.mapNotNull { it.lengthMeters }.maxOrNull()
                    ?: group.maxOf { abs(it.stationMeters) }.takeIf { it > 0.0 }
                    ?: 1.0

                val supports = group.filter { it.stationMeters <= 0.02 * length || it.stationMeters >= 0.98 * length }
                val span = group.filter { it.stationMeters >= 0.20 * length && it.stationMeters <= 0.80 * length }
                val supportPool = supports.ifEmpty { group }
                val spanPool = span.ifEmpty { group }

                val maxSpanMoment = spanPool.maxBy { abs(it.m3KNm) }
                val maxSupportMoment = supportPool.maxBy { abs(it.m3KNm) }
                val maxShear = group.maxBy { abs(it.v2KN) }

                BeamForceEnvelope(
                    story = story,
                    element = element,
                    category = category,
                    maxSpanMomentKNm = abs(maxSpanMoment.m3KNm),
                    maxSpanMomentCombo = maxSpanMoment.loadCase,
                    maxSpanMomentStationMeters = maxSpanMoment.stationMeters,
                    maxSupportMomentKNm = abs(maxSupportMoment.m3KNm),
                    maxSupportMomentCombo = maxSupportMoment.loadCase,
                    maxSupportMomentStationMeters = maxSupportMoment.stationMeters,
                    maxShearKN = abs(maxShear.v2KN),
                    maxShearCombo = maxShear.loadCase
                )
            }
            .sortedWith(compareBy({ it.story }, { it.element }, { it.category.name }))
}

object PierProfileBuilder {

    fun build(rows: List<PierForceRow>): List<PierForceProfile> =
        rows.groupBy { it.pier to it.category }
            .map { (key, group) ->
                val (pier, category) = key
                val base = group
                    .filter { it.location.equals("Bottom", ignoreCase = true) }
                    .ifEmpty { group.sortedBy { it.stationMeters }.take(1) }
                    .maxBy { abs(it.pKN) }
                val levels = group
                    .groupBy { it.story }
                    .map { (story, storyRows) ->
                        val governing = storyRows.maxBy { abs(it.pKN) }
                        PierLevelForce(
                            story = story,
                            stationMeters = governing.stationMeters,
                            pKN = governing.pKN,
                            m3KNm = governing.m3KNm,
                            v2KN = governing.v2KN
                        )
                    }
                    .sortedBy { it.stationMeters }

                PierForceProfile(
                    pier = pier,
                    category = category,
                    baseAxialKN = base.pKN,
                    baseShearV2KN = base.v2KN,
                    baseShearV3KN = base.v3KN,
                    baseMomentM3KNm = base.m3KNm,
                    baseCombo = base.loadCase,
                    levels = levels
                )
            }
            .sortedWith(compareBy({ it.pier }, { it.category.name }))
}
