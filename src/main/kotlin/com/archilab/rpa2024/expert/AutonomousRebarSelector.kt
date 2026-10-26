package com.archilab.rpa2024.expert

import com.archilab.rpa2024.engine.geometry.CheckStatus
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Barres commerciales regroupées sur une face ou une nappe. */
data class BarGroup(
    val count: Int,
    val diameterMm: Int
) {
    init {
        require(count >= 1) { "Un groupe comporte au moins une barre." }
        require(diameterMm >= 6) { "Diamètre de barre invalide." }
    }

    val areaCm2: Double
        get() {
            val radiusCm = diameterMm / 10.0
            return count * PI * radiusCm * radiusCm / 4.0
        }
    val label: String get() = "${count}T$diameterMm"
}

/** Résultat d'une sélection de ferraillage de poteau. */
data class ColumnRebarSelection(
    val cornerBars: BarGroup,
    val faceBars: BarGroup?,
    val totalBars: Int,
    val areaCm2: Double,
    val ratioPercent: Double,
    val minimumRatioPercent: Double,
    val maximumRatioPercent: Double,
    val spacingLongFaceCm: Double,
    val spacingShortFaceCm: Double,
    val tieDiameterMm: Int,
    val tieSpacingCriticalCm: Double,
    val tieSpacingCurrentCm: Double,
    val lapLengthCm: Double,
    val status: CheckStatus,
    val warnings: List<String> = emptyList(),
    val alternatives: List<ColumnRebarSelection> = emptyList()
) {
    val label: String
        get() = when {
            faceBars == null || faceBars.count == 0 -> cornerBars.label
            cornerBars.diameterMm == faceBars.diameterMm ->
                "${cornerBars.count + faceBars.count}T${cornerBars.diameterMm}"
            else -> "${cornerBars.label} + ${faceBars.label}"
        }

    val isConstructible: Boolean
        get() = status != CheckStatus.FAILED && spacingLongFaceCm > 0.0 && spacingShortFaceCm > 0.0

    /** Masse d'acier longitudinale par mètre de poteau (7850 kg/m³). */
    val steelMassPerMeterKg: Double get() = areaCm2 / 10_000.0 * 7850.0
}

/** Résultat d'une sélection de ferraillage de poutre. */
data class BeamRebarSelection(
    val bottomContinuous: BarGroup,
    val bottomAdditional: BarGroup?,
    val topContinuous: BarGroup,
    val topAdditional: BarGroup?,
    val bottomAreaCm2: Double,
    val topAreaCm2: Double,
    val bottomRatioPercent: Double,
    val topRatioPercent: Double,
    val stirrupDiameterMm: Int,
    val stirrupLegs: Int,
    val stirrupSpacingCriticalCm: Double,
    val stirrupSpacingCurrentCm: Double,
    val status: CheckStatus,
    val warnings: List<String> = emptyList(),
    val alternatives: List<BeamRebarSelection> = emptyList()
) {
    val bottomLabel: String
        get() = bottomContinuous.label + " filantes" +
            (bottomAdditional?.let { " + ${it.label} renforts" } ?: "")

    val topLabel: String
        get() = topContinuous.label + " filantes" +
            (topAdditional?.let { " + ${it.label} chapeaux" } ?: "")

    val bottomTotalBars: Int get() = bottomContinuous.count + (bottomAdditional?.count ?: 0)
    val topTotalBars: Int get() = topContinuous.count + (topAdditional?.count ?: 0)
    val stirrupLabel: String get() = "$stirrupLegs brins T$stirrupDiameterMm"
    val isConstructible: Boolean get() = status != CheckStatus.FAILED

    /** Masse longitudinale inférieure + supérieure par mètre de poutre. */
    val steelMassPerMeterKg: Double get() = (bottomAreaCm2 + topAreaCm2) / 10_000.0 * 7850.0
}

/** Paramètres communs de sélection. */
data class RebarSelectionOptions(
    val diametersMm: List<Int> = listOf(12, 14, 16, 20, 25, 32),
    val minimumBarDiameterMm: Int = 12,
    val maximumBarDiameterMm: Int = 32,
    val maximumBarSpacingCm: Double = 25.0,
    val tieDiameterMm: Int = 8,
    val lapFactor: Double = 50.0,
    val maximumAlternatives: Int = 5
) {
    init {
        require(diametersMm.isNotEmpty())
        require(maximumBarSpacingCm > 0.0)
        require(maximumAlternatives >= 0)
    }
}

/**
 * Sélecteur autonome d'armatures commerciales.
 *
 * Il ne calcule pas les sections d'acier BAEL : il reçoit les As requis des solveurs
 * et choisit la disposition la plus légère qui respecte les minima RPA, les enrobages,
 * la largeur de coffrage et les espacements maximaux. Le critère de poids est équivalent
 * au critère d'aire d'acier pour une nuance constante.
 */
class AutonomousRebarSelector(
    private val options: RebarSelectionOptions = RebarSelectionOptions()
) {

    fun selectColumn(
        widthCm: Double,
        depthCm: Double,
        coverCm: Double,
        requiredAsCm2: Double,
        minimumRatio: Double,
        maximumRatio: Double = 0.04,
        maximumSpacingCm: Double = options.maximumBarSpacingCm
    ): ColumnRebarSelection {
        require(widthCm > 2 * coverCm && depthCm > 2 * coverCm)
        require(requiredAsCm2 >= 0.0)
        require(minimumRatio > 0.0 && maximumRatio >= minimumRatio)
        require(maximumSpacingCm > 0.0)

        val sectionArea = widthCm * depthCm
        val lowerBound = max(requiredAsCm2, minimumRatio * sectionArea)
        val upperBound = maximumRatio * sectionArea
        val longFace = max(widthCm, depthCm)
        val shortFace = min(widthCm, depthCm)
        val usableLong = longFace - 2.0 * coverCm
        val usableShort = shortFace - 2.0 * coverCm
        val minBarsLong = max(2, ceil(usableLong / maximumSpacingCm).toInt() + 1)
        val minBarsShort = max(2, ceil(usableShort / maximumSpacingCm).toInt() + 1)
        val candidates = mutableListOf<ColumnRebarSelection>()

        for (barsLong in minBarsLong..(minBarsLong + 4)) {
            for (barsShort in minBarsShort..(minBarsShort + 4)) {
                val spacingLong = usableLong / (barsLong - 1)
                val spacingShort = usableShort / (barsShort - 1)
                if (spacingLong > maximumSpacingCm + 1e-6 ||
                    spacingShort > maximumSpacingCm + 1e-6
                ) continue
                val faceCount = 2 * (barsLong - 2) + 2 * (barsShort - 2)
                val totalBars = 4 + faceCount
                if (totalBars > 32) continue
                for (cornerDiameter in options.diametersMm.filter {
                    it in options.minimumBarDiameterMm..options.maximumBarDiameterMm
                }) {
                    val corner = BarGroup(4, cornerDiameter)
                    if (faceCount == 0) {
                        val area = corner.areaCm2
                        if (area + 1e-9 < lowerBound || area > upperBound + 1e-9) continue
                        candidates += columnSelection(
                            corner, null, totalBars, area, sectionArea, minimumRatio,
                            maximumRatio, spacingLong, spacingShort
                        )
                    } else {
                        for (faceDiameter in options.diametersMm.filter {
                            it in options.minimumBarDiameterMm..cornerDiameter
                        }) {
                            val face = BarGroup(faceCount, faceDiameter)
                            val area = corner.areaCm2 + face.areaCm2
                            if (area + 1e-9 < lowerBound || area > upperBound + 1e-9) continue
                            candidates += columnSelection(
                                corner, face, totalBars, area, sectionArea, minimumRatio,
                                maximumRatio, spacingLong, spacingShort
                            )
                        }
                    }
                }
            }
        }

        val ordered = candidates.sortedWith(
            compareBy<ColumnRebarSelection> { it.areaCm2 }
                .thenBy { it.totalBars }
                .thenBy { it.cornerBars.diameterMm }
                .thenBy { it.faceBars?.diameterMm ?: 0 }
        )
        if (ordered.isEmpty()) {
            val fallback = BarGroup(4, options.maximumBarDiameterMm)
            val failed = columnSelection(
                fallback, null, 4, fallback.areaCm2, sectionArea, minimumRatio,
                maximumRatio, usableLong, usableShort,
                status = CheckStatus.FAILED,
                warnings = listOf(
                    "Aucune disposition constructible pour As requis = ${format(requiredAsCm2)} cm² " +
                        "(limite ${format(lowerBound)}–${format(upperBound)} cm²)."
                )
            )
            return failed
        }
        val best = ordered.first()
        return best.copy(alternatives = ordered.drop(1).take(options.maximumAlternatives))
    }

    fun selectBeam(
        widthCm: Double,
        depthCm: Double,
        coverCm: Double,
        requiredBottomAsCm2: Double,
        requiredTopAsCm2: Double,
        minimumRatio: Double = 0.005,
        maximumRatio: Double = 0.04,
        requiredStirrupAreaCm2PerM: Double = 0.0
    ): BeamRebarSelection {
        require(widthCm > 2 * coverCm && depthCm > 2 * coverCm)
        require(minimumRatio > 0.0 && maximumRatio >= minimumRatio)
        val sectionArea = widthCm * depthCm
        val minAs = minimumRatio * sectionArea
        val maxAs = maximumRatio * sectionArea
        val bottomRequired = max(requiredBottomAsCm2, minAs)
        val topRequired = max(requiredTopAsCm2, minAs)
        val bottomCandidates = beamGroups(widthCm, coverCm, bottomRequired, maxAs)
        val topCandidates = beamGroups(widthCm, coverCm, topRequired, maxAs)
        val stirrup = selectStirrup(
            depthCm = depthCm,
            requiredAreaCm2PerM = requiredStirrupAreaCm2PerM,
            criticalZone = true
        )
        if (bottomCandidates.isEmpty() || topCandidates.isEmpty()) {
            val fallback = BarGroup(3, options.maximumBarDiameterMm)
            return BeamRebarSelection(
                bottomContinuous = fallback,
                bottomAdditional = null,
                topContinuous = fallback,
                topAdditional = null,
                bottomAreaCm2 = fallback.areaCm2,
                topAreaCm2 = fallback.areaCm2,
                bottomRatioPercent = fallback.areaCm2 / sectionArea * 100.0,
                topRatioPercent = fallback.areaCm2 / sectionArea * 100.0,
                stirrupDiameterMm = stirrup.first,
                stirrupLegs = 2,
                stirrupSpacingCriticalCm = stirrup.second,
                stirrupSpacingCurrentCm = min(20.0, stirrup.second * 1.5),
                status = CheckStatus.FAILED,
                warnings = listOf(
                    "Aucune disposition de poutre constructible dans la largeur de coffrage."
                )
            )
        }
        val all = bottomCandidates.flatMap { bottomChoice ->
            topCandidates.map { topChoice -> bottomChoice to topChoice }
        }.sortedWith(
            compareBy<Pair<BeamChoice, BeamChoice>> { it.first.area + it.second.area }
                .thenBy { it.first.totalBars + it.second.totalBars }
        )
        val bestPair = all.first()
        return beamSelection(bestPair.first, bestPair.second, sectionArea, stirrup)
            .copy(
                alternatives = all.drop(1).take(options.maximumAlternatives).map { pair ->
                    beamSelection(pair.first, pair.second, sectionArea, stirrup)
                }
            )
    }

    private fun beamSelection(
        bottomChoice: BeamChoice,
        topChoice: BeamChoice,
        sectionArea: Double,
        stirrup: Triple<Int, Double, Boolean>
    ): BeamRebarSelection = BeamRebarSelection(
        bottomContinuous = bottomChoice.continuous,
        bottomAdditional = bottomChoice.additional,
        topContinuous = topChoice.continuous,
        topAdditional = topChoice.additional,
        bottomAreaCm2 = bottomChoice.area,
        topAreaCm2 = topChoice.area,
        bottomRatioPercent = bottomChoice.area / sectionArea * 100.0,
        topRatioPercent = topChoice.area / sectionArea * 100.0,
        stirrupDiameterMm = stirrup.first,
        stirrupLegs = 2,
        stirrupSpacingCriticalCm = stirrup.second,
        stirrupSpacingCurrentCm = min(20.0, stirrup.second * 1.5),
        status = if (stirrup.third) CheckStatus.OK else CheckStatus.WARNING,
        warnings = if (stirrup.third) emptyList() else listOf(
            "Aucune combinaison d'étriers ne satisfait At/s requis ; vérifier l'effort tranchant."
        )
    )

    private fun columnSelection(
        corner: BarGroup,
        face: BarGroup?,
        totalBars: Int,
        area: Double,
        sectionArea: Double,
        minimumRatio: Double,
        maximumRatio: Double,
        spacingLong: Double,
        spacingShort: Double,
        status: CheckStatus = CheckStatus.OK,
        warnings: List<String> = emptyList()
    ): ColumnRebarSelection {
        val minDiameter = min(corner.diameterMm, face?.diameterMm ?: corner.diameterMm)
        val tieCritical = min(10.0 * minDiameter / 10.0, 15.0)
        val tieCurrent = min(15.0 * minDiameter / 10.0, 20.0)
        return ColumnRebarSelection(
            cornerBars = corner,
            faceBars = face,
            totalBars = totalBars,
            areaCm2 = area,
            ratioPercent = area / sectionArea * 100.0,
            minimumRatioPercent = minimumRatio * 100.0,
            maximumRatioPercent = maximumRatio * 100.0,
            spacingLongFaceCm = spacingLong,
            spacingShortFaceCm = spacingShort,
            tieDiameterMm = options.tieDiameterMm,
            tieSpacingCriticalCm = tieCritical,
            tieSpacingCurrentCm = tieCurrent,
            lapLengthCm = options.lapFactor * minDiameter / 10.0,
            status = status,
            warnings = warnings
        )
    }

    private fun beamGroups(
        widthCm: Double,
        coverCm: Double,
        requiredAsCm2: Double,
        maximumAsCm2: Double
    ): List<BeamChoice> {
        val usable = widthCm - 2.0 * coverCm
        val candidates = mutableListOf<BeamChoice>()
        for (continuousCount in listOf(2, 3)) {
            for (continuousDiameter in options.diametersMm.filter {
                it in options.minimumBarDiameterMm..options.maximumBarDiameterMm
            }) {
                val continuous = BarGroup(continuousCount, continuousDiameter)
                for (additionalCount in 0..6) {
                    val additionalDiameters = if (additionalCount == 0) listOf(0) else options.diametersMm
                    for (additionalDiameter in additionalDiameters) {
                        val additional = if (additionalCount > 0) {
                            BarGroup(additionalCount, additionalDiameter)
                        } else {
                            null
                        }
                        val totalBars = continuousCount + additionalCount
                        val maximumDiameter = max(continuousDiameter, additionalDiameter)
                        if (totalBars * maximumDiameter / 10.0 + (totalBars - 1) * 2.5 > usable + 1e-6) continue
                        val area = continuous.areaCm2 + (additional?.areaCm2 ?: 0.0)
                        if (area + 1e-9 < requiredAsCm2 || area > maximumAsCm2 + 1e-9) continue
                        candidates += BeamChoice(
                            continuous = continuous,
                            additional = additional,
                            area = area
                        )
                    }
                }
            }
        }
        return candidates.sortedWith(
            compareBy<BeamChoice> { it.area }
                .thenBy { it.totalBars }
                .thenBy { max(it.continuous.diameterMm, it.additional?.diameterMm ?: 0) }
        )
    }

    private fun selectStirrup(
        depthCm: Double,
        requiredAreaCm2PerM: Double,
        criticalZone: Boolean
    ): Triple<Int, Double, Boolean> {
        val maximumSpacing = if (criticalZone) min(20.0, depthCm / 4.0) else min(25.0, depthCm / 2.0)
        var best: Triple<Int, Double, Boolean>? = null
        for (diameter in listOf(8, 10, 12)) {
            for (spacing in 8..max(8, maximumSpacing.toInt())) {
                val areaPerMeter = 2 * BarGroup(1, diameter).areaCm2 * 100.0 / spacing
                if (areaPerMeter + 1e-9 < requiredAreaCm2PerM) continue
                val candidate = Triple(diameter, spacing.toDouble(), true)
                if (best == null || areaPerMeter < 2 * BarGroup(1, best.first).areaCm2 * 100.0 / best.second) {
                    best = candidate
                }
            }
        }
        return best ?: Triple(8, maximumSpacing, false)
    }

    private data class BeamChoice(
        val continuous: BarGroup,
        val additional: BarGroup?,
        val area: Double
    ) {
        val totalBars: Int get() = continuous.count + (additional?.count ?: 0)
    }

    private fun format(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value)
}
