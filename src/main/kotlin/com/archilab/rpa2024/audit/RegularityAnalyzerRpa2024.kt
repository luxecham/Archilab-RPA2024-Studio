package com.archilab.rpa2024.audit

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.model.BoundingBox
import com.archilab.rpa2024.normative.QualityFactorResult
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.AreaElement
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.FrameElement
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.MassRigidityCenterRow
import com.archilab.rpa2024.parser.ParsedEtabsModel
import com.archilab.rpa2024.parser.Point3D
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class PlanVertex(val xMeters: Double, val yMeters: Double)

/** Contour d'un plancher : regions pleines et ouvertures, tous deux maillees en plan. */
data class FloorGeometry(
    val storyName: String,
    val solidRegions: List<List<PlanVertex>>,
    val openings: List<List<PlanVertex>> = emptyList()
) {
    init {
        require(storyName.isNotBlank()) { "Le nom du niveau est obligatoire." }
    }

    private val allVertices: List<PlanVertex>
        get() = solidRegions.flatten() + openings.flatten()

    val bounds: BoundingBox?
        get() {
            val points = allVertices.filter { it.xMeters.isFinite() && it.yMeters.isFinite() }
            if (points.isEmpty()) return null
            return BoundingBox(
                points.minOf { it.xMeters },
                points.maxOf { it.xMeters },
                points.minOf { it.yMeters },
                points.maxOf { it.yMeters }
            )
        }

    val widthMeters: Double get() = bounds?.widthX ?: 0.0
    val lengthMeters: Double get() = bounds?.lengthY ?: 0.0

    fun coverage(): FloorCoverage = FloorCoverage.compute(this)

    companion object {
        fun rectangle(
            storyName: String,
            minX: Double,
            maxX: Double,
            minY: Double,
            maxY: Double,
            openings: List<List<PlanVertex>> = emptyList()
        ): FloorGeometry = FloorGeometry(
            storyName = storyName,
            solidRegions = listOf(
                listOf(
                    PlanVertex(minX, minY),
                    PlanVertex(maxX, minY),
                    PlanVertex(maxX, maxY),
                    PlanVertex(minX, maxY)
                )
            ),
            openings = openings
        )
    }
}

/** Mesures de couverture obtenues par decomposition orthogonale du contour. */
data class FloorCoverage(
    val bounds: BoundingBox,
    val grossAreaM2: Double,
    val solidAreaM2: Double,
    val openingAreaM2: Double,
    val retraitAreaM2: Double,
    val notches: List<FloorNotch>
) {
    val openingRatio: Double get() = if (grossAreaM2 > 0.0) openingAreaM2 / grossAreaM2 else 0.0
    val retraitRatio: Double get() = if (grossAreaM2 > 0.0) retraitAreaM2 / grossAreaM2 else 0.0

    internal companion object {
        internal fun compute(floor: FloorGeometry): FloorCoverage {
            val bounds = floor.bounds ?: BoundingBox(0.0, 0.0, 0.0, 0.0)
            val xs = coordinates(floor, horizontal = true)
            val ys = coordinates(floor, horizontal = false)
            if (xs.size < 2 || ys.size < 2) {
                return FloorCoverage(bounds, 0.0, 0.0, 0.0, 0.0, emptyList())
            }

            val nx = xs.size - 1
            val ny = ys.size - 1
            var solidArea = 0.0
            var openingArea = 0.0
            var retraitArea = 0.0
            val emptyCells = BooleanArray(nx * ny)
            for (ix in 0 until nx) {
                val cx = (xs[ix] + xs[ix + 1]) / 2.0
                for (iy in 0 until ny) {
                    val cy = (ys[iy] + ys[iy + 1]) / 2.0
                    val cellArea = (xs[ix + 1] - xs[ix]) * (ys[iy + 1] - ys[iy])
                    val opening = floor.openings.any { polygon ->
                        pointInPolygon(PlanVertex(cx, cy), polygon)
                    }
                    val solid = !opening && floor.solidRegions.any { polygon ->
                        pointInPolygon(PlanVertex(cx, cy), polygon)
                    }
                    when {
                        solid -> solidArea += cellArea
                        opening -> openingArea += cellArea
                        else -> {
                            retraitArea += cellArea
                            emptyCells[ix * ny + iy] = true
                        }
                    }
                }
            }
            val gross = solidArea + openingArea + retraitArea
            val notches = connectedNotches(xs, ys, emptyCells, nx, ny)
            return FloorCoverage(bounds, gross, solidArea, openingArea, retraitArea, notches)
        }

        private fun coordinates(floor: FloorGeometry, horizontal: Boolean): List<Double> {
            val bounds = floor.bounds ?: return emptyList()
            val values = floor.solidRegions.flatten().filter { it.xMeters.isFinite() && it.yMeters.isFinite() } +
                floor.openings.flatten()
            val raw = if (horizontal) values.map { it.xMeters } else values.map { it.yMeters }
            val merged = mutableListOf<Double>()
            raw.filter { it.isFinite() }.sorted().forEach { value ->
                if (merged.isEmpty() || abs(value - merged.last()) > 1e-6) merged += value
            }
            if (merged.isEmpty()) {
                merged += if (horizontal) bounds.minX else bounds.minY
                merged += if (horizontal) bounds.maxX else bounds.maxY
            } else {
                if (merged.first() > (if (horizontal) bounds.minX else bounds.minY) + 1e-9) {
                    merged.add(0, if (horizontal) bounds.minX else bounds.minY)
                }
                if (merged.last() < (if (horizontal) bounds.maxX else bounds.maxY) - 1e-9) {
                    merged += if (horizontal) bounds.maxX else bounds.maxY
                }
            }
            return merged
        }

        private fun connectedNotches(
            xs: List<Double>,
            ys: List<Double>,
            empty: BooleanArray,
            nx: Int,
            ny: Int
        ): List<FloorNotch> {
            val visited = BooleanArray(empty.size)
            val notches = mutableListOf<FloorNotch>()
            val queue = ArrayDeque<Int>()
            for (start in empty.indices) {
                if (!empty[start] || visited[start]) continue
                visited[start] = true
                queue.addLast(start)
                var minIx = Int.MAX_VALUE
                var maxIx = Int.MIN_VALUE
                var minIy = Int.MAX_VALUE
                var maxIy = Int.MIN_VALUE
                var area = 0.0
                while (queue.isNotEmpty()) {
                    val index = queue.removeFirst()
                    val ix = index / ny
                    val iy = index % ny
                    minIx = min(minIx, ix)
                    maxIx = max(maxIx, ix)
                    minIy = min(minIy, iy)
                    maxIy = max(maxIy, iy)
                    area += (xs[ix + 1] - xs[ix]) * (ys[iy + 1] - ys[iy])
                    listOf(ix - 1 to iy, ix + 1 to iy, ix to iy - 1, ix to iy + 1).forEach { (jX, jY) ->
                        if (jX in 0 until nx && jY in 0 until ny) {
                            val neighbour = jX * ny + jY
                            if (empty[neighbour] && !visited[neighbour]) {
                                visited[neighbour] = true
                                queue.addLast(neighbour)
                            }
                        }
                    }
                }
                if (maxIx >= minIx && maxIy >= minIy) {
                    notches += FloorNotch(
                        widthMeters = xs[maxIx + 1] - xs[minIx],
                        lengthMeters = ys[maxIy + 1] - ys[minIy],
                        areaM2 = area
                    )
                }
            }
            return notches.sortedByDescending { it.areaM2 }
        }

        private fun pointInPolygon(point: PlanVertex, polygon: List<PlanVertex>): Boolean {
            if (polygon.size < 3) return false
            var inside = false
            var previous = polygon.last()
            for (current in polygon) {
                val intersects = (current.yMeters > point.yMeters) != (previous.yMeters > point.yMeters) &&
                    point.xMeters < (previous.xMeters - current.xMeters) *
                    (point.yMeters - current.yMeters) / (previous.yMeters - current.yMeters) + current.xMeters
                if (intersects) inside = !inside
                previous = current
            }
            return inside
        }
    }
}

data class FloorNotch(val widthMeters: Double, val lengthMeters: Double, val areaM2: Double)

/**
 * Extraction des contours de plancher depuis Area Connectivity. Les surfaces dont le
 * nom contient OPENING/TRÉMIE/TROU/VOID sont traitées comme des trémies et affectées
 * au niveau du plancher porteur.
 */
object DiaphragmGeometryExtractor {

    private val openingPattern = Regex(
        "(?i)(OPEN|OUVERT|TR[EÉ]MIE|TROU|VOID|VIDE|RESERVE|SHAFT|HOLE)"
    )

    fun fromModel(model: ParsedEtabsModel): List<FloorGeometry> {
        val stories = model.joint.storySystem.orderedStories
        data class Group(val solid: MutableList<List<PlanVertex>>, val openings: MutableList<List<PlanVertex>>)

        val groups = linkedMapOf<String, Group>()
        model.areas.filter { it.kind == AreaKind.SLAB }.forEach { area ->
            val points = uniquePoints(model, area)
            if (points.size < 3) return@forEach
            val storyName = storyName(model, area, stories) ?: return@forEach
            val key = storyName.trim().uppercase()
            val group = groups.getOrPut(key) { Group(mutableListOf(), mutableListOf()) }
            val target = if (isOpening(area)) group.openings else group.solid
            target += points
        }
        return groups.map { (key, group) ->
            val story = stories.firstOrNull { it.storyName.trim().uppercase() == key }?.storyName ?: key
            FloorGeometry(story, group.solid.toList(), group.openings.toList())
        }.sortedBy { floor ->
            stories.indexOfFirst { it.storyName.equals(floor.storyName, ignoreCase = true) }
        }
    }

    fun openingArea(floor: FloorGeometry): Double = floor.coverage().openingAreaM2

    private fun uniquePoints(model: ParsedEtabsModel, area: AreaElement): List<PlanVertex> =
        area.nodeIds.mapNotNull { model.points[it] }
            .distinctBy { "${round(it.xMeters)}:${round(it.yMeters)}" }
            .map { PlanVertex(it.xMeters, it.yMeters) }

    private fun storyName(
        model: ParsedEtabsModel,
        area: AreaElement,
        stories: List<com.archilab.rpa2024.model.StoryLevel>
    ): String? {
        if (!area.storyName.isNullOrBlank()) {
            return stories.firstOrNull { it.storyName.equals(area.storyName, ignoreCase = true) }?.storyName
                ?: area.storyName
        }
        val elevation = area.nodeIds.mapNotNull { model.points[it]?.zMeters }.minOrNull() ?: return null
        return stories.minByOrNull { abs(it.elevationZk - elevation) }?.storyName
    }

    private fun isOpening(area: AreaElement): Boolean =
        openingPattern.containsMatchIn(area.propertyName) ||
            openingPattern.containsMatchIn(area.pierName.orEmpty())

    private fun round(value: Double): Long = Math.round(value * 1000.0)
}

data class RegularityCriteriaOptions(
    val symmetryToleranceRatio: Double = 0.10,
    val eccentricityLimitRatio: Double = 0.15,
    val minimumLengthOverWidth: Double = 0.25,
    val maximumLengthOverWidth: Double = 4.0,
    val maximumOpeningRatio: Double = 0.15,
    val maximumRetraitRatio: Double = 0.25,
    val maximumNotchRatio: Double = 0.25,
    val maximumStiffnessVariation: Double = 0.30,
    val maximumMassVariation: Double = 0.30,
    val maximumSetbackRatio: Double = 0.20,
    val continuityToleranceMeters: Double = 0.30,
    val minimumSupportCoverage: Double = 0.50,
    val includePenthouse: Boolean = true
) {
    init {
        require(symmetryToleranceRatio >= 0.0) { "La tolérance de symétrie doit être positive ou nulle." }
        require(eccentricityLimitRatio > 0.0) { "La limite d'excentricité doit être positive." }
        require(minimumLengthOverWidth > 0.0 && maximumLengthOverWidth > minimumLengthOverWidth) {
            "Les bornes du rapport Lx/Ly sont invalides."
        }
        require(maximumOpeningRatio in 0.0..1.0) { "Le ratio d'ouverture doit appartenir à [0 ; 1]." }
        require(maximumRetraitRatio in 0.0..1.0) { "Le ratio de retrait doit appartenir à [0 ; 1]." }
        require(maximumNotchRatio in 0.0..1.0) { "Le ratio de décrochement doit appartenir à [0 ; 1]." }
        require(maximumStiffnessVariation in 0.0..1.0) { "La variation de rigidité doit appartenir à [0 ; 1]." }
        require(maximumMassVariation in 0.0..1.0) { "La variation de masse doit appartenir à [0 ; 1]." }
        require(maximumSetbackRatio in 0.0..1.0) { "Le retrait en élévation doit appartenir à [0 ; 1]." }
        require(continuityToleranceMeters > 0.0) { "La tolérance de continuité doit être positive." }
        require(minimumSupportCoverage in 0.0..1.0) { "La couverture minimale doit appartenir à [0 ; 1]." }
    }
}

data class RegularityCriterionResult(
    val code: String,
    val label: String,
    val reference: String,
    val verified: Boolean,
    val satisfied: Boolean,
    val observed: String,
    val requirement: String,
    val justification: String
) {
    val status: CheckStatus
        get() = when {
            !verified -> CheckStatus.WARNING
            satisfied -> CheckStatus.OK
            else -> CheckStatus.FAILED
        }
}

data class RegularityAnalysisRequest(
    val model: ParsedEtabsModel,
    val results: EtabsResults = EtabsResults(),
    val floors: List<FloorGeometry> = DiaphragmGeometryExtractor.fromModel(model),
    val centers: List<MassRigidityCenterRow> = results.massRigidityCenters,
    val options: RegularityCriteriaOptions = RegularityCriteriaOptions()
)

data class RegularityAssessment(
    val planCriteria: List<RegularityCriterionResult>,
    val elevationCriteria: List<RegularityCriterionResult>,
    val planRegular: Boolean,
    val elevationRegular: Boolean,
    val qualityFactor: QualityFactorResult,
    val planPenalty: Double,
    val elevationPenalty: Double,
    val warnings: List<String> = emptyList()
) {
    val qf: Double get() = qualityFactor.qf

    fun toSeismicConfig(config: com.archilab.rpa2024.model.SeismicConfig): com.archilab.rpa2024.model.SeismicConfig =
        config.copy(planRegular = planRegular, elevationRegular = elevationRegular)

    fun toChapterReport(number: Int = 9): ChapterReport {
        val checks = (planCriteria + elevationCriteria).map { criterion ->
            if (!criterion.verified) {
                Checks.notApplicable(
                    code = "REG_${criterion.code}",
                    label = criterion.label,
                    reference = criterion.reference,
                    message = criterion.observed
                )
            } else {
                Checks.boolean(
                    code = "REG_${criterion.code}",
                    label = criterion.label,
                    reference = criterion.reference,
                    condition = criterion.satisfied,
                    required = criterion.requirement,
                    provided = criterion.observed,
                    failureStatus = CheckStatus.FAILED,
                    message = criterion.justification
                )
            }
        }.toMutableList()
        checks += Checks.info(
            code = "REG_QF",
            label = "Facteur de qualité après audit de régularité",
            reference = "RPA 2024 Tableau 3.18",
            value = "QF = ${Checks.format(qf, 2)} ; Pq1 = ${Checks.format(planPenalty, 2)} ; " +
                "Pq2 = ${Checks.format(elevationPenalty, 2)}"
        )
        val lines = planCriteria.map { "Plan ${it.code} : ${if (it.satisfied) "satisfait" else "non satisfait"} - ${it.observed}" } +
            elevationCriteria.map { "Élévation ${it.code} : ${if (it.satisfied) "satisfait" else "non satisfait"} - ${it.observed}" } +
            listOf(
                "Conclusion plan : ${if (planRegular) "régulière" else "irrégulière"} (Pq1 = ${Checks.format(planPenalty, 2)}).",
                "Conclusion élévation : ${if (elevationRegular) "régulière" else "irrégulière"} " +
                    "(Pq2 = ${Checks.format(elevationPenalty, 2)}).",
                "Facteur de qualité recalculé : QF = ${Checks.format(qf, 2)}."
            ) + warnings.map { "Avertissement : $it" }
        return ChapterReport(
            number = number,
            title = "Audit mathématique de la régularité RPA 2024",
            summary = "Vérification automatique des critères § 3.7.1a et § 3.7.1b ; " +
                "QF = ${Checks.format(qf, 2)}.",
            checks = checks,
            lines = lines
        )
    }
}

/**
 * Analyseur de régularité en plan et en élévation fondé sur les tables ETABS
 * « Centers Of Mass And Rigidity », la géométrie des planchers, les déplacements,
 * les efforts tranchants et la topologie verticale. Chaque critère est daté, mesuré
 * et rattaché à sa disposition réglementaire.
 */
class RegularityAnalyzerRpa2024 {

    fun analyze(
        model: ParsedEtabsModel,
        results: EtabsResults = EtabsResults(),
        floors: List<FloorGeometry> = DiaphragmGeometryExtractor.fromModel(model),
        centers: List<MassRigidityCenterRow> = results.massRigidityCenters,
        options: RegularityCriteriaOptions = RegularityCriteriaOptions()
    ): RegularityAssessment = analyze(RegularityAnalysisRequest(model, results, floors, centers, options))

    fun analyze(request: RegularityAnalysisRequest): RegularityAssessment {
        val model = request.model
        val joint = model.joint
        val options = request.options
        val stories = if (options.includePenthouse) joint.storySystem.orderedStories else joint.storySystem.mainStories
        val floorByStory = request.floors.associateBy { normalizeStory(it.storyName) }
        val centerByStory = representativeCenters(request.centers)
        val warnings = mutableListOf<String>()
        if (request.centers.isEmpty()) {
            warnings += "Table Centers Of Mass And Rigidity absente : la symétrie et l'excentricité ne peuvent pas être vérifiées."
        }
        if (request.floors.isEmpty()) {
            warnings += "Aucun contour de plancher exploitable : les critères de retrait, d'élancement et d'ouverture ne sont pas vérifiables."
        }

        val planCriteria = listOf(
            symmetryCriterion(stories, floorByStory, centerByStory, options),
            notchCriterion(stories, floorByStory, options),
            eccentricityCriterion(stories, floorByStory, centerByStory, options),
            slendernessCriterion(stories, floorByStory, options),
            openingCriterion(stories, floorByStory, options)
        )
        val elevationCriteria = listOf(
            continuityCriterion(model, stories, options),
            massStiffnessVariationCriterion(model, request.results, stories, centerByStory, options),
            setbackCriterion(stories, floorByStory, options)
        )
        val planRegular = planCriteria.all { it.satisfied }
        val elevationRegular = elevationCriteria.all { it.satisfied }
        val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)
        val quality = Rpa2024NormativeDb.computeQualityFactor(
            category = resolved.bracingSystem.penaltyCategory,
            planRegular = planRegular,
            elevationRegular = elevationRegular,
            topology = joint,
            redundantWalls = joint.seismicParams.redundantWallsInPlan
        )
        val planPenalty = quality.penalties.firstOrNull { it.code.endsWith("PLAN") }?.takeIf { it.applied }?.weight ?: 0.0
        val elevationPenalty = quality.penalties.firstOrNull { it.code.endsWith("ELEVATION") }?.takeIf { it.applied }?.weight ?: 0.0
        return RegularityAssessment(
            planCriteria = planCriteria,
            elevationCriteria = elevationCriteria,
            planRegular = planRegular,
            elevationRegular = elevationRegular,
            qualityFactor = quality,
            planPenalty = planPenalty,
            elevationPenalty = elevationPenalty,
            warnings = warnings.distinct()
        )
    }

    private fun symmetryCriterion(
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        floors: Map<String, FloorGeometry>,
        centers: Map<String, MassRigidityCenterRow>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.isEmpty()) return unverified("a1", "Symétrie masse/rigidité", "RPA 2024 § 3.7.1a", "Aucun niveau.")
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        for (story in stories) {
            val floor = floors[normalizeStory(story.storyName)]
            val center = centers[normalizeStory(story.storyName)]
            if (floor?.bounds == null || center == null) {
                verified = false
                details += "${story.storyName} : données centre/contour indisponibles."
                continue
            }
            val gx = (floor.bounds!!.minX + floor.bounds!!.maxX) / 2.0
            val gy = (floor.bounds!!.minY + floor.bounds!!.maxY) / 2.0
            val limitX = options.symmetryToleranceRatio * floor.widthMeters
            val limitY = options.symmetryToleranceRatio * floor.lengthMeters
            val mx = abs(center.xcmMeters - gx)
            val my = abs(center.ycmMeters - gy)
            val rx = abs(center.xcrMeters - gx)
            val ry = abs(center.ycrMeters - gy)
            val ok = mx <= limitX + 1e-9 && my <= limitY + 1e-9 &&
                rx <= limitX + 1e-9 && ry <= limitY + 1e-9
            if (!ok) satisfied = false
            details += "${story.storyName} : |CM-Cg|x=${Checks.format(mx)} m, |CR-Cg|x=${Checks.format(rx)} m, " +
                "|CM-Cg|y=${Checks.format(my)} m, |CR-Cg|y=${Checks.format(ry)} m " +
                "(limites ${Checks.format(limitX)} / ${Checks.format(limitY)} m)."
        }
        return RegularityCriterionResult(
            code = "a1",
            label = "Symétrie approximative des masses et rigidités",
            reference = "RPA 2024 § 3.7.1a",
            verified = verified,
            satisfied = satisfied && verified,
            observed = details.joinToString(" "),
            requirement = "Écart des centres ≤ ${Checks.format(options.symmetryToleranceRatio * 100.0)} % " +
                "des dimensions respectives.",
            justification = if (satisfied) "Distribution des centres approximativement symétrique." else
                "La distribution des masses ou rigidités est dissymétrique sur au moins un niveau."
        )
    }

    private fun notchCriterion(
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        floors: Map<String, FloorGeometry>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.isEmpty()) return unverified("a2", "Retraits en plan", "RPA 2024 § 3.7.1a", "Aucun niveau.")
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        for (story in stories) {
            val floor = floors[normalizeStory(story.storyName)]
            if (floor == null) {
                verified = false
                details += "${story.storyName} : contour indisponible."
                continue
            }
            val coverage = floor.coverage()
            if (coverage.grossAreaM2 <= 0.0) {
                verified = false
                continue
            }
            val maxNotchX = coverage.notches.maxOfOrNull { it.widthMeters / max(coverage.bounds.widthX, 1e-9) } ?: 0.0
            val maxNotchY = coverage.notches.maxOfOrNull { it.lengthMeters / max(coverage.bounds.lengthY, 1e-9) } ?: 0.0
            val ok = coverage.retraitRatio <= options.maximumRetraitRatio + 1e-9 &&
                maxNotchX <= options.maximumNotchRatio + 1e-9 &&
                maxNotchY <= options.maximumNotchRatio + 1e-9
            if (!ok) satisfied = false
            details += "${story.storyName} : retrait ${Checks.format(coverage.retraitRatio * 100.0, 1)} % ; " +
                "décrochement max ${Checks.format(maxNotchX * 100.0, 1)} % Lx / " +
                "${Checks.format(maxNotchY * 100.0, 1)} % Ly."
        }
        return RegularityCriterionResult(
            code = "a2",
            label = "Limitation des décrochements en plan",
            reference = "RPA 2024 § 3.7.1a (décrochements ≤ 25 %)",
            verified = verified,
            satisfied = satisfied && verified,
            observed = details.ifEmpty { listOf("Aucun contour de plancher exploitable.") }.joinToString(" "),
            requirement = "Retrait total ≤ ${Checks.format(options.maximumRetraitRatio * 100.0)} % ; " +
                "l1/Lx et l2/Ly ≤ ${Checks.format(options.maximumNotchRatio * 100.0)} %.",
            justification = if (satisfied) "Décrochements compatibles avec la forme compacte." else
                "Au moins un décrochement dépasse les seuils du § 3.7.1a."
        )
    }

    private fun eccentricityCriterion(
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        floors: Map<String, FloorGeometry>,
        centers: Map<String, MassRigidityCenterRow>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.isEmpty()) return unverified("a3", "Excentricité masse/rigidité", "RPA 2024 § 3.7.1a", "Aucun niveau.")
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        for (story in stories) {
            val floor = floors[normalizeStory(story.storyName)]
            val center = centers[normalizeStory(story.storyName)]
            if (floor == null || center == null) {
                verified = false
                details += "${story.storyName} : centre ou contour indisponible."
                continue
            }
            val dimensions = floor.bounds ?: continue
            val ex = abs(center.xcmMeters - center.xcrMeters)
            val ey = abs(center.ycmMeters - center.ycrMeters)
            val limitX = options.eccentricityLimitRatio * dimensions.widthX
            val limitY = options.eccentricityLimitRatio * dimensions.lengthY
            val ok = ex <= limitX + 1e-9 && ey <= limitY + 1e-9
            if (!ok) satisfied = false
            details += "${story.storyName} : eox=${Checks.format(ex)} m ≤ ${Checks.format(limitX)} m, " +
                "eoy=${Checks.format(ey)} m ≤ ${Checks.format(limitY)} m."
        }
        return RegularityCriterionResult(
            code = "a3",
            label = "Excentricité entre centre de masse et centre de rigidité",
            reference = "RPA 2024 § 3.7.1a",
            verified = verified,
            satisfied = satisfied && verified,
            observed = details.ifEmpty { listOf("Centres de masse/rigidité indisponibles.") }.joinToString(" "),
            requirement = "eox ≤ ${Checks.format(options.eccentricityLimitRatio * 100.0)} % Lx et " +
                "eoy ≤ ${Checks.format(options.eccentricityLimitRatio * 100.0)} % Ly.",
            justification = if (satisfied) "Excentricités structurales admissibles." else
                "Excentricité excessive à au moins un niveau : risque de torsion."
        )
    }

    private fun slendernessCriterion(
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        floors: Map<String, FloorGeometry>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.isEmpty()) return unverified("a4", "Élancement en plan", "RPA 2024 § 3.7.1a", "Aucun niveau.")
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        for (story in stories) {
            val floor = floors[normalizeStory(story.storyName)]
            if (floor == null || floor.widthMeters <= 0.0 || floor.lengthMeters <= 0.0) {
                verified = false
                details += "${story.storyName} : dimensions indisponibles."
                continue
            }
            val ratio = floor.widthMeters / floor.lengthMeters
            val ok = ratio >= options.minimumLengthOverWidth - 1e-9 &&
                ratio <= options.maximumLengthOverWidth + 1e-9
            if (!ok) satisfied = false
            details += "${story.storyName} : Lx/Ly = ${Checks.format(ratio, 3)}."
        }
        return RegularityCriterionResult(
            code = "a4",
            label = "Élancement en plan du bloc",
            reference = "RPA 2024 § 3.7.1a",
            verified = verified,
            satisfied = satisfied && verified,
            observed = details.ifEmpty { listOf("Dimensions en plan indisponibles.") }.joinToString(" "),
            requirement = "${Checks.format(options.minimumLengthOverWidth, 2)} ≤ Lx/Ly ≤ " +
                "${Checks.format(options.maximumLengthOverWidth, 2)}.",
            justification = if (satisfied) "Proportions en plan admissibles." else
                "Rapport Lx/Ly hors plage : bloc trop allongé."
        )
    }

    private fun openingCriterion(
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        floors: Map<String, FloorGeometry>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.isEmpty()) return unverified("a5", "Ouvertures de plancher", "RPA 2024 § 3.7.1a", "Aucun niveau.")
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        for (story in stories) {
            val floor = floors[normalizeStory(story.storyName)]
            if (floor == null) {
                verified = false
                details += "${story.storyName} : plancher indisponible."
                continue
            }
            val coverage = floor.coverage()
            if (coverage.grossAreaM2 <= 0.0) {
                verified = false
                continue
            }
            val ok = coverage.openingRatio <= options.maximumOpeningRatio + 1e-9
            if (!ok) satisfied = false
            details += "${story.storyName} : trémies ${Checks.format(coverage.openingRatio * 100.0, 1)} % " +
                "de ${Checks.format(coverage.grossAreaM2, 1)} m²."
        }
        return RegularityCriterionResult(
            code = "a5",
            label = "Rigidité de diaphragme - limitation des trémies",
            reference = "RPA 2024 § 3.7.1a",
            verified = verified,
            satisfied = satisfied && verified,
            observed = details.ifEmpty { listOf("Surfaces de plancher indisponibles.") }.joinToString(" "),
            requirement = "Surface des ouvertures ≤ ${Checks.format(options.maximumOpeningRatio * 100.0)} % " +
                "de la surface du plancher.",
            justification = if (satisfied) "Le diaphragme reste assimilable à un plancher rigide." else
                "Surface de trémie excessive : justifier un modèle de diaphragme souple."
        )
    }

    private fun continuityCriterion(
        model: ParsedEtabsModel,
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.size < 2) {
            return unverified("b1", "Continuité verticale", "RPA 2024 § 3.7.1b", "Moins de deux niveaux.")
        }
        val framesByStory = model.frames.filter { it.kind == FrameKind.COLUMN }
            .groupBy { normalizeStory(it.storyName.orEmpty()) }
        val wallsByStory = model.areas.filter { it.kind == AreaKind.WALL }
            .groupBy { normalizeStory(it.storyName.orEmpty()) }
        var verified = false
        var satisfied = true
        val issues = mutableListOf<String>()
        var checkedPairs = 0
        for (index in 1 until stories.size) {
            val lower = stories[index - 1]
            val upper = stories[index]
            val lowerKey = normalizeStory(lower.storyName)
            val upperKey = normalizeStory(upper.storyName)
            val lowerColumns = framesByStory[lowerKey].orEmpty()
            val upperColumns = framesByStory[upperKey].orEmpty()
            val lowerWalls = wallsByStory[lowerKey].orEmpty()
            val upperWalls = wallsByStory[upperKey].orEmpty()
            if (upperColumns.isEmpty() && upperWalls.isEmpty()) continue
            checkedPairs++
            verified = true
            val lowerColumnPoints = lowerColumns.mapNotNull { model.points[it.startNodeId] }
            val lowerWallSegments = lowerWalls.mapNotNull { wallSegment(model, it) }
            var missingColumns = 0
            upperColumns.forEach { frame ->
                val point = model.points[frame.startNodeId] ?: return@forEach
                if (!supported(point, lowerColumnPoints, lowerWallSegments, options.continuityToleranceMeters)) {
                    missingColumns++
                }
            }
            var unsupportedWalls = 0
            upperWalls.forEach { wall ->
                val segment = wallSegment(model, wall) ?: return@forEach
                val sampleCount = 5
                val supportedCount = (0..sampleCount).count { step ->
                    val fraction = step.toDouble() / sampleCount
                    val point = Point3D(
                        "sample",
                        segment.first.xMeters + (segment.second.xMeters - segment.first.xMeters) * fraction,
                        segment.first.yMeters + (segment.second.yMeters - segment.first.yMeters) * fraction,
                        segment.first.zMeters
                    )
                    supported(point, lowerColumnPoints, lowerWallSegments, options.continuityToleranceMeters)
                }
                val coverage = supportedCount.toDouble() / (sampleCount + 1)
                if (coverage + 1e-9 < options.minimumSupportCoverage) unsupportedWalls++
            }
            if (missingColumns > 0) {
                satisfied = false
                issues += "${upper.storyName} : $missingColumns poteau(x) sans appui vertical continu (transfert)."
            }
            if (unsupportedWalls > 0) {
                satisfied = false
                issues += "${upper.storyName} : $unsupportedWalls voile(s) sans reprise verticale suffisante."
            }
        }
        if (checkedPairs == 0) verified = false
        return RegularityCriterionResult(
            code = "b1",
            label = "Continuité verticale des éléments porteurs",
            reference = "RPA 2024 § 3.7.1b",
            verified = verified,
            satisfied = satisfied && verified,
            observed = if (issues.isEmpty()) {
                if (verified) "Aucun élément vertical n'est interrompu au-dessus d'une poutre de transfert."
                else "Aucune trame verticale exploitable."
            } else issues.joinToString(" "),
            requirement = "Chaque poteau/voile doit trouver un appui vertical continu sur le niveau inférieur.",
            justification = if (satisfied) "Continuité verticale assurée." else
                "Au moins un élément repose sur une poutre de transfert ou une console non justifiée."
        )
    }

    private fun massStiffnessVariationCriterion(
        model: ParsedEtabsModel,
        results: EtabsResults,
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        centers: Map<String, MassRigidityCenterRow>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.size < 2) {
            return unverified("b2", "Variation masse/rigidité", "RPA 2024 § 3.7.1b", "Moins de deux niveaux.")
        }
        val masses = storyMassMap(model, results, centers)
        val stiffnessX = storyStiffnessMap(model, results, stories, com.archilab.rpa2024.model.AxisDirection.X)
        val stiffnessY = storyStiffnessMap(model, results, stories, com.archilab.rpa2024.model.AxisDirection.Y)
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        var maxMassVariation = 0.0
        var maxStiffnessX = 0.0
        var maxStiffnessY = 0.0
        for (index in 1 until stories.size) {
            val lower = normalizeStory(stories[index - 1].storyName)
            val upper = normalizeStory(stories[index].storyName)
            val lowerMass = masses[lower]
            val upperMass = masses[upper]
            if (lowerMass != null && upperMass != null && lowerMass > 0.0 && upperMass > 0.0) {
                val variation = abs(lowerMass - upperMass) / max(lowerMass, upperMass)
                maxMassVariation = max(maxMassVariation, variation)
                if (variation > options.maximumMassVariation + 1e-9) {
                    satisfied = false
                    val direction = if (upperMass > lowerMass) "augmentation" else "baisse"
                    details += "${stories[index].storyName} : masse en $direction de " +
                        "${Checks.format(variation * 100.0, 1)} % entre deux niveaux consecutifs."
                }
            } else {
                verified = false
            }
            val lowerKx = stiffnessX[lower]
            val upperKx = stiffnessX[upper]
            if (lowerKx != null && upperKx != null && lowerKx > 0.0 && upperKx > 0.0) {
                val variation = abs(lowerKx - upperKx) / max(lowerKx, upperKx)
                maxStiffnessX = max(maxStiffnessX, variation)
                if (variation > options.maximumStiffnessVariation + 1e-9) {
                    satisfied = false
                    val direction = if (upperKx > lowerKx) "augmentation" else "baisse"
                    details += "${stories[index].storyName} : raideur X en $direction de " +
                        "${Checks.format(variation * 100.0, 1)} % entre deux niveaux consecutifs."
                }
            } else {
                verified = false
            }
            val lowerKy = stiffnessY[lower]
            val upperKy = stiffnessY[upper]
            if (lowerKy != null && upperKy != null && lowerKy > 0.0 && upperKy > 0.0) {
                val variation = abs(lowerKy - upperKy) / max(lowerKy, upperKy)
                maxStiffnessY = max(maxStiffnessY, variation)
                if (variation > options.maximumStiffnessVariation + 1e-9) {
                    satisfied = false
                    val direction = if (upperKy > lowerKy) "augmentation" else "baisse"
                    details += "${stories[index].storyName} : raideur Y en $direction de " +
                        "${Checks.format(variation * 100.0, 1)} % entre deux niveaux consecutifs."
                }
            } else {
                verified = false
            }
        }
        return RegularityCriterionResult(
            code = "b2",
            label = "Variation progressive de masse et de rigidité",
            reference = "RPA 2024 § 3.7.1b",
            verified = verified,
            satisfied = satisfied && verified,
            observed = buildString {
                append("Variation max : masse ${Checks.format(maxMassVariation * 100.0, 1)} %, ")
                append("raideur X ${Checks.format(maxStiffnessX * 100.0, 1)} %, ")
                append("raideur Y ${Checks.format(maxStiffnessY * 100.0, 1)} %.")
                if (details.isNotEmpty()) append(" ").append(details.joinToString(" "))
            },
            requirement = "Variation de masse et de raideur ≤ " +
                "${Checks.format(options.maximumMassVariation * 100.0)} % entre niveaux consécutifs.",
            justification = if (satisfied) "Absence d'étage souple ou de saut de masse brutal." else
                "Chute brutale de masse ou de raideur : étage souple à justifier."
        )
    }

    private fun setbackCriterion(
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        floors: Map<String, FloorGeometry>,
        options: RegularityCriteriaOptions
    ): RegularityCriterionResult {
        if (stories.size < 2) {
            return unverified("b3", "Retraits en élévation", "RPA 2024 § 3.7.1b", "Moins de deux niveaux.")
        }
        val details = mutableListOf<String>()
        var verified = true
        var satisfied = true
        for (index in 1 until stories.size) {
            val lower = floors[normalizeStory(stories[index - 1].storyName)]
            val upper = floors[normalizeStory(stories[index].storyName)]
            if (lower == null || upper == null || lower.widthMeters <= 0.0 || lower.lengthMeters <= 0.0) {
                verified = false
                continue
            }
            val ratioX = upper.widthMeters / lower.widthMeters
            val ratioY = upper.lengthMeters / lower.lengthMeters
            val dropX = 1.0 - ratioX
            val dropY = 1.0 - ratioY
            val ok = dropX <= options.maximumSetbackRatio + 1e-9 && dropY <= options.maximumSetbackRatio + 1e-9
            if (!ok) satisfied = false
            details += "${stories[index].storyName} : ΔLx=${Checks.format(dropX * 100.0, 1)} %, " +
                "ΔLy=${Checks.format(dropY * 100.0, 1)} %."
        }
        return RegularityCriterionResult(
            code = "b3",
            label = "Retraits et variations de dimensions en élévation",
            reference = "RPA 2024 § 3.7.1b",
            verified = verified,
            satisfied = satisfied && verified,
            observed = details.ifEmpty { listOf("Dimensions de plancher indisponibles.") }.joinToString(" "),
            requirement = "Réduction de Lx et Ly ≤ ${Checks.format(options.maximumSetbackRatio * 100.0)} % par niveau.",
            justification = if (satisfied) "Retraits en élévation admissibles." else
                "Retrait de plus de 20 % entre deux niveaux consécutifs."
        )
    }

    private fun representativeCenters(rows: List<MassRigidityCenterRow>): Map<String, MassRigidityCenterRow> =
        rows.groupBy { normalizeStory(it.story) }.mapValues { (_, group) ->
            group.maxByOrNull { it.totalMass } ?: group.first()
        }

    private fun storyMassMap(
        model: ParsedEtabsModel,
        results: EtabsResults,
        centers: Map<String, MassRigidityCenterRow>
    ): Map<String, Double> {
        val fromCenters = centers.mapValues { (_, center) ->
            when {
                center.massX > 0.0 && center.massY > 0.0 -> (center.massX + center.massY) / 2.0
                center.massX > 0.0 -> center.massX
                else -> center.massY
            }
        }.filterValues { it > 0.0 }
        if (fromCenters.isNotEmpty()) return fromCenters
        val cumulative = results.cumulativeWeightByStoryKN()
        if (cumulative.isEmpty()) return emptyMap()
        val levels = model.joint.storySystem.orderedStories.sortedBy { it.elevationZk }
        return levels.mapIndexedNotNull { index, level ->
            val current = cumulative.entries.firstOrNull { normalizeStory(it.key) == normalizeStory(level.storyName) }?.value
                ?: return@mapIndexedNotNull null
            val above = levels.getOrNull(index + 1)?.let { next ->
                cumulative.entries.firstOrNull { normalizeStory(it.key) == normalizeStory(next.storyName) }?.value
            }
            normalizeStory(level.storyName) to max(0.0, current - (above ?: 0.0))
        }.toMap()
    }

    private fun storyStiffnessMap(
        model: ParsedEtabsModel,
        results: EtabsResults,
        stories: List<com.archilab.rpa2024.model.StoryLevel>,
        direction: com.archilab.rpa2024.model.AxisDirection
    ): Map<String, Double> {
        val caseName = if (direction == com.archilab.rpa2024.model.AxisDirection.X) "EX" else "EY"
        val shears = results.storyShears
            .filter { normCase(it.loadCase).startsWith(caseName) || (ComboCategory.classify(it.loadCase) == ComboCategory.ELA) }
            .groupBy { normalizeStory(it.story) }
            .mapValues { (_, rows) ->
                rows.maxOfOrNull { row ->
                    abs(if (direction == com.archilab.rpa2024.model.AxisDirection.X) row.vxKN else row.vyKN)
                } ?: 0.0
            }
        val displacements = results.diaphragmDisplacements
            .filter { normCase(it.loadCase).startsWith(caseName) || ComboCategory.classify(it.loadCase) == ComboCategory.ELA }
            .groupBy { normalizeStory(it.story) }
            .mapValues { (_, rows) ->
                rows.maxOfOrNull { row ->
                    abs(if (direction == com.archilab.rpa2024.model.AxisDirection.X) row.uxMeters else row.uyMeters)
                } ?: 0.0
            }
        val ordered = stories.sortedBy { it.elevationZk }
        val result = mutableMapOf<String, Double>()
        var previousDisplacement = 0.0
        ordered.forEach { story ->
            val key = normalizeStory(story.storyName)
            val current = displacements[key]
            val shear = shears[key]
            if (current != null && shear != null && shear > 1e-9) {
                val drift = abs(current - previousDisplacement)
                if (drift > 1e-9) result[key] = shear / drift
                previousDisplacement = current
            }
        }
        return result
    }

    private fun supported(
        point: Point3D,
        columnPoints: List<Point3D>,
        wallSegments: List<Pair<Point3D, Point3D>>,
        tolerance: Double
    ): Boolean {
        if (columnPoints.any { it.planDistanceTo(point) <= tolerance + 1e-9 }) return true
        return wallSegments.any { (start, end) -> distanceToSegment(point, start, end) <= tolerance + 1e-9 }
    }

    private fun distanceToSegment(point: Point3D, start: Point3D, end: Point3D): Double {
        val dx = end.xMeters - start.xMeters
        val dy = end.yMeters - start.yMeters
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared < 1e-12) return point.planDistanceTo(start)
        val t = (((point.xMeters - start.xMeters) * dx + (point.yMeters - start.yMeters) * dy) / lengthSquared)
            .coerceIn(0.0, 1.0)
        val x = start.xMeters + t * dx
        val y = start.yMeters + t * dy
        return hypot(point.xMeters - x, point.yMeters - y)
    }

    private fun wallSegment(model: ParsedEtabsModel, area: AreaElement): Pair<Point3D, Point3D>? {
        val points = area.nodeIds.mapNotNull { model.points[it] }
            .distinctBy { "${Math.round(it.xMeters * 1000)}:${Math.round(it.yMeters * 1000)}" }
        if (points.size < 2) return null
        var best: Pair<Point3D, Point3D>? = null
        var bestDistance = -1.0
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val distance = points[i].planDistanceTo(points[j])
                if (distance > bestDistance) {
                    bestDistance = distance
                    best = points[i] to points[j]
                }
            }
        }
        return best
    }

    private fun unverified(code: String, label: String, reference: String, reason: String): RegularityCriterionResult =
        RegularityCriterionResult(
            code = code,
            label = label,
            reference = reference,
            verified = false,
            satisfied = false,
            observed = reason,
            requirement = "Données nécessaires à la vérification.",
            justification = reason
        )

    private fun normalizeStory(value: String): String = value.trim().uppercase()
    private fun normCase(value: String): String = value.trim().uppercase().replace(" ", "")
}
