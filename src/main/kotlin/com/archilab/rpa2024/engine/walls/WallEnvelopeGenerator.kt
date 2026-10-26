package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.StorySystem
import com.archilab.rpa2024.parser.PierForceRow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Point d'une courbe d'enveloppe (cote cumulee depuis la base, valeur). */
data class WallEnvelopePoint(
    val elevationMeters: Double,
    val value: Double,
    val label: String = ""
)

/** Hauteur de la zone critique h_cr (RPA 2024 § 7.7.4, Eq. 7.22 et 7.23). */
data class WallCriticalHeightResult(
    val storiesN: Int,
    val baseMeters: Double,
    val capMeters: Double,
    val retainedMeters: Double,
    val coveredStories: List<String>,
    val formula: String
) {
    val isCapped: Boolean get() = retainedMeters + 1e-9 < baseMeters
}

/** Les trois courbes de moments flechissants sur la hauteur du voile. */
data class WallMomentEnvelope(
    val analysis: List<WallEnvelopePoint>,
    val linear: List<WallEnvelopePoint>,
    val shifted: List<WallEnvelopePoint>,
    val baseMomentKNm: Double,
    val topMomentKNm: Double,
    val criticalHeightMeters: Double
) {
    /** Interpolation lineaire de l'enveloppe decalee (valeur constante sur h_cr). */
    fun shiftedAt(elevationMeters: Double): Double =
        interpolateEnvelope(shifted, elevationMeters)

    fun linearAt(elevationMeters: Double): Double =
        interpolateEnvelope(linear, elevationMeters)
}

/** Les trois courbes d'efforts tranchants sur la hauteur du voile. */
data class WallShearEnvelope(
    val analysis: List<WallEnvelopePoint>,
    val amplified: List<WallEnvelopePoint>,
    val design: List<WallEnvelopePoint>,
    val baseShearKN: Double,
    val amplifiedBaseKN: Double,
    val topPlateauKN: Double
) {
    fun designAt(elevationMeters: Double): Double =
        interpolateEnvelope(design, elevationMeters)

    fun amplifiedAt(elevationMeters: Double): Double =
        interpolateEnvelope(amplified, elevationMeters)
}

/** Enveloppes completes d'un voile (moments et efforts tranchants). */
data class WallEnvelopeResult(
    val family: ShearWallFamily,
    val forces: WallDesignForces,
    val criticalHeight: WallCriticalHeightResult,
    val moment: WallMomentEnvelope,
    val shear: WallShearEnvelope,
    val notes: List<String> = emptyList()
) {
    val heightMeters: Double get() = family.totalHeightHwMeters
    fun momentAt(elevationMeters: Double): Double = moment.shiftedAt(elevationMeters)
    fun shearAt(elevationMeters: Double): Double = shear.designAt(elevationMeters)
}

/**
 * Generateur des enveloppes de calcul des voiles. Toutes les cotes et tous les efforts
 * proviennent du modele ETABS ; aucune hauteur, portee ou nom de voile n'est fige.
 *
 * Moments :
 *  1. diagramme ETABS aux cotes cumulees (Pier Forces) ;
 *  2. enveloppe lineaire base -> sommet ;
 *  3. enveloppe de calcul decalee de h_cr (palier M_base sur [0 ; h_cr]).
 *
 * Efforts tranchants :
 *  1. diagramme ETABS ;
 *  2. diagramme amplifie 1,40 V_u ;
 *  3. enveloppe de calcul : palier 1,40 V_u sur le tiers inferieur puis decroissance
 *     lineaire jusqu'a (1,40 V_u)/2 au sommet.
 */
class WallEnvelopeGenerator {

    fun generate(
        wall: ShearWallFamily,
        forces: WallDesignForces,
        rows: List<PierForceRow>,
        storyCount: Int? = null,
        storySystem: StorySystem? = null
    ): WallEnvelopeResult {
        val notes = mutableListOf<String>()
        val storiesN = storyCountFor(wall, storyCount)
        val criticalHeight = WallCriticalHeightCalculator.compute(wall, storiesN, storySystem)
        if (criticalHeight.isCapped) {
            notes += "h_cr plafonnee par l'Eq. (7.23) : ${Checks.format(criticalHeight.baseMeters, 2)} m " +
                "ramene a ${Checks.format(criticalHeight.retainedMeters, 2)} m."
        }

        val height = wall.totalHeightHwMeters.coerceAtLeast(wall.clearHeightHeMeters).coerceAtLeast(0.0)
        val pierRows = WallForceExtractor.rowsFor(wall, rows)
        val momentAnalysis = analysisCurve(
            wall = wall,
            forces = forces,
            rows = pierRows,
            height = height,
            storySystem = storySystem,
            valueOf = { abs(it.m3KNm).takeIf { value -> value > 1e-9 } ?: abs(it.m2KNm) },
            baseValue = forces.momentKNm
        )
        val shearAnalysis = analysisCurve(
            wall = wall,
            forces = forces,
            rows = pierRows,
            height = height,
            storySystem = storySystem,
            valueOf = { abs(it.v2KN).takeIf { value -> value > 1e-9 } ?: abs(it.v3KN) },
            baseValue = forces.shearKN
        )

        val baseMoment = forces.momentKNm
        val topMoment = momentAnalysis.lastOrNull()?.value ?: 0.0
        val linear = listOf(
            WallEnvelopePoint(0.0, baseMoment, "M_base"),
            WallEnvelopePoint(height, topMoment, "M_sommet")
        )
        val hcr = criticalHeight.retainedMeters.coerceIn(0.0, height)
        val shifted = if (height <= 1e-9) {
            listOf(WallEnvelopePoint(0.0, baseMoment, "M_base"))
        } else if (hcr >= height - 1e-9) {
            listOf(
                WallEnvelopePoint(0.0, baseMoment, "M_base"),
                WallEnvelopePoint(height, baseMoment.coerceAtLeast(topMoment), "M_sommet")
            )
        } else {
            listOf(
                WallEnvelopePoint(0.0, baseMoment, "M_base"),
                WallEnvelopePoint(hcr, baseMoment, "M_base (h_cr)"),
                WallEnvelopePoint(height, topMoment, "M_sommet")
            )
        }

        val baseShear = forces.shearKN
        val amplifiedBase = 1.40 * baseShear
        val amplified = shearAnalysis.map { it.copy(value = 1.40 * it.value) }
        val design = shearEnvelope(height, amplifiedBase)
        val topPlateau = 0.50 * amplifiedBase

        return WallEnvelopeResult(
            family = wall,
            forces = forces,
            criticalHeight = criticalHeight,
            moment = WallMomentEnvelope(
                analysis = momentAnalysis,
                linear = linear,
                shifted = shifted,
                baseMomentKNm = baseMoment,
                topMomentKNm = topMoment,
                criticalHeightMeters = hcr
            ),
            shear = WallShearEnvelope(
                analysis = shearAnalysis,
                amplified = amplified,
                design = design,
                baseShearKN = baseShear,
                amplifiedBaseKN = amplifiedBase,
                topPlateauKN = topPlateau
            ),
            notes = notes
        )
    }

    private fun shearEnvelope(height: Double, baseShear: Double): List<WallEnvelopePoint> {
        if (height <= 1e-9) return listOf(WallEnvelopePoint(0.0, baseShear, "V_base"))
        val third = height / 3.0
        val top = 0.50 * baseShear
        return listOf(
            WallEnvelopePoint(0.0, baseShear, "V_base"),
            WallEnvelopePoint(third, baseShear, "V (hw/3)"),
            WallEnvelopePoint(height, top, "V_sommet")
        )
    }

    private fun analysisCurve(
        wall: ShearWallFamily,
        forces: WallDesignForces,
        rows: List<PierForceRow>,
        height: Double,
        storySystem: StorySystem?,
        valueOf: (PierForceRow) -> Double,
        baseValue: Double
    ): List<WallEnvelopePoint> {
        if (height <= 1e-9) return listOf(WallEnvelopePoint(0.0, baseValue, "Base"))

        val groups = rows.groupBy { it.story.trim() }
        val orderedStories = storySystem?.orderedStories?.map { it.storyName } ?: emptyList()
        val fallbackSpacing = if (wall.clearHeightHeMeters > 0.0) {
            wall.clearHeightHeMeters
        } else {
            height / wall.stories.distinct().size.coerceAtLeast(1)
        }

        val points = mutableListOf<WallEnvelopePoint>()
        groups.entries.forEach { (story, storyRows) ->
            val governing = storyRows.maxByOrNull(valueOf) ?: return@forEach
            val value = valueOf(governing)
            val elevation = elevationOf(
                wall = wall,
                story = story,
                row = governing,
                storySystem = storySystem,
                orderedStories = orderedStories,
                fallbackSpacing = fallbackSpacing,
                height = height
            )
            points += WallEnvelopePoint(elevation.coerceIn(0.0, height), value, story)
        }

        if (points.none { it.elevationMeters <= 1e-6 }) {
            points += WallEnvelopePoint(0.0, baseValue, "Base")
        }
        if (points.none { it.elevationMeters >= height - 1e-6 }) {
            points += WallEnvelopePoint(height, 0.0, "Sommet")
        }

        // Un seul point par cote : on conserve la valeur maximale (enveloppe).
        return points
            .groupBy { String.format(java.util.Locale.ROOT, "%.3f", it.elevationMeters) }
            .map { (_, sameElevation) -> sameElevation.maxBy { it.value } }
            .sortedBy { it.elevationMeters }
    }

    private fun elevationOf(
        wall: ShearWallFamily,
        story: String,
        row: PierForceRow,
        storySystem: StorySystem?,
        orderedStories: List<String>,
        fallbackSpacing: Double,
        height: Double
    ): Double {
        if (storySystem != null) {
            val level = storySystem.level(story)
            if (level != null) {
                return (level.topElevationZk - storySystem.baseElevationZ).coerceIn(0.0, height)
            }
        }
        if (row.stationMeters > 1e-6) return row.stationMeters
        val index = orderedStories.indexOfFirst { it.equals(story, ignoreCase = true) }
        if (index >= 0) return (index + 1) * fallbackSpacing
        val distinct = wall.stories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val localIndex = distinct.indexOfFirst { it.equals(story, ignoreCase = true) }
        return if (localIndex >= 0) (localIndex + 1) * fallbackSpacing else height
    }

}

internal fun interpolateEnvelope(points: List<WallEnvelopePoint>, elevation: Double): Double {
    if (points.isEmpty()) return 0.0
    if (elevation <= points.first().elevationMeters) return points.first().value
    if (elevation >= points.last().elevationMeters) return points.last().value
    for (index in 0 until points.size - 1) {
        val start = points[index]
        val end = points[index + 1]
        if (elevation >= start.elevationMeters && elevation <= end.elevationMeters) {
            val span = end.elevationMeters - start.elevationMeters
            if (span <= 1e-12) return max(start.value, end.value)
            val ratio = (elevation - start.elevationMeters) / span
            return start.value + (end.value - start.value) * ratio
        }
    }
    return points.last().value
}

/** Calcul de h_cr par les Eq. (7.22) et (7.23) du RPA 2024. */
object WallCriticalHeightCalculator {

    fun compute(
        wall: ShearWallFamily,
        storiesN: Int,
        storySystem: StorySystem? = null
    ): WallCriticalHeightResult {
        val lw = wall.lengthLwMeters.coerceAtLeast(0.0)
        val hw = wall.totalHeightHwMeters.coerceAtLeast(0.0)
        val he = wall.clearHeightHeMeters.coerceAtLeast(0.0)
        val base = max(lw, hw / 6.0)
        val cap = if (storiesN <= 6) {
            min(2.0 * lw, he.takeIf { it > 0.0 } ?: base)
        } else {
            min(2.0 * lw, 2.0 * he)
        }
        val retained = min(base, cap).coerceIn(0.0, if (hw > 0.0) hw else base)
        val covered = when {
            storySystem != null -> {
                val baseZ = storySystem.baseElevationZ
                storySystem.orderedStories
                    .filter { (it.topElevationZk - baseZ) <= retained + 1e-6 }
                    .map { it.storyName }
            }
            wall.clearHeightHeMeters > 0.0 -> {
                val count = ceil(retained / wall.clearHeightHeMeters).toInt().coerceAtLeast(0)
                wall.stories.distinct().take(count)
            }
            else -> emptyList()
        }
        val formula = "h_cr,base = max(lw ; hw/6) = ${Checks.format(base, 2)} m ; " +
            "h_cr,max = ${if (storiesN <= 6) "min(2 lw ; he)" else "min(2 lw ; 2 he)"} = " +
            "${Checks.format(cap, 2)} m (N = $storiesN niveaux)."
        return WallCriticalHeightResult(
            storiesN = storiesN,
            baseMeters = base,
            capMeters = cap,
            retainedMeters = retained,
            coveredStories = covered,
            formula = formula
        )
    }
}
