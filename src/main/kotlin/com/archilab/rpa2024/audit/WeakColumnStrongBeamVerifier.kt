package com.archilab.rpa2024.audit

import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignResult
import com.archilab.rpa2024.engine.frames.BeamDesignSet
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignResult
import com.archilab.rpa2024.engine.frames.ColumnDesignSet
import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.FrameElement
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import com.archilab.rpa2024.parser.Point3D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class JointNodeCategory(val label: String) {
    INTERIOR("Nœud intérieur"),
    EDGE("Nœud de rive"),
    CORNER("Nœud d'angle")
}

data class ColumnNodeCapacity(
    val elementId: String,
    val story: String,
    val sectionName: String,
    val axialKN: Double,
    val resistingMomentKNm: Double
)

data class BeamNodeCapacity(
    val elementId: String,
    val story: String,
    val sectionName: String,
    val positiveMomentKNm: Double,
    val negativeMomentKNm: Double
) {
    val resistingMomentKNm: Double get() = max(positiveMomentKNm, negativeMomentKNm)
}

data class StrongColumnNodeCheck(
    val nodeId: String,
    val story: String,
    val category: JointNodeCategory,
    val direction: AxisDirection,
    val columnCapacities: List<ColumnNodeCapacity>,
    val beamCapacities: List<BeamNodeCapacity>,
    val columnMomentKNm: Double,
    val beamMomentKNm: Double,
    val requiredColumnMomentKNm: Double,
    val ratio: Double,
    val status: CheckStatus,
    val message: String
) {
    val code: String get() = "SCWB_${direction.name}_$nodeId"
    val label: String get() = "${category.label} $nodeId - ${if (direction == AxisDirection.X) "X" else "Y"}"
}

data class WeakColumnStrongBeamOptions(
    val overStrengthFactor: Double = 1.25,
    val concreteCoverMeters: Double = 0.05,
    val useAccidentalStrengths: Boolean = true
) {
    init {
        require(overStrengthFactor >= 1.0) { "Le coefficient de hiérarchie doit être au moins égal à 1,0." }
        require(concreteCoverMeters > 0.0) { "L'enrobage doit être positif." }
    }
}

data class WeakColumnStrongBeamAssessment(
    val checks: List<StrongColumnNodeCheck>,
    val warnings: List<String> = emptyList()
) {
    val nodeCount: Int get() = checks.map { it.nodeId }.distinct().size
    val failedChecks: List<StrongColumnNodeCheck> get() = checks.filter { it.status == CheckStatus.FAILED }
    val warningChecks: List<StrongColumnNodeCheck> get() = checks.filter { it.status == CheckStatus.WARNING }
    val status: CheckStatus
        get() = if (checks.isEmpty()) CheckStatus.NOT_APPLICABLE else CheckStatus.worst(checks.map { it.status })

    fun toChapterReport(number: Int = 10): ChapterReport {
        val synthesis = Checks.boolean(
            code = "SCWB_SYNTHESE",
            label = "Hiérarchie poteaux forts / poutres faibles",
            reference = "RPA 2024 § 7.6",
            condition = checks.isNotEmpty() && failedChecks.isEmpty(),
            required = "ΣMRc ≥ 1,25 ΣMRb à tous les nœuds",
            provided = if (checks.isEmpty()) "aucun nœud évalué" else {
                "${nodeCount} nœud(s), ${checks.size} direction(s), ${failedChecks.size} non conforme(s)"
            },
            failureStatus = CheckStatus.FAILED,
            message = if (checks.isEmpty()) {
                "Aucun nœud poteau-poutre exploitable : vérifier la topologie et les affectations de sections."
            } else {
                "Au moins une direction de nœud ne satisfait pas la hiérarchie des résistances."
            }
        )
        val elementReports = checks.map { check ->
            ElementCheckReport(
                elementType = "Nœud ${check.category.label}",
                name = "${check.nodeId} - ${check.direction.name}",
                geometry = "ΣMRc = ${Checks.format(check.columnMomentKNm)} kN.m ; " +
                    "ΣMRb = ${Checks.format(check.beamMomentKNm)} kN.m",
                checks = listOf(check.toDesignCheck())
            )
        }
        val lines = buildList {
            checks.forEach { check ->
                add(
                    "${check.nodeId} (${check.story}, ${check.category.label}) ${if (check.direction == AxisDirection.X) "X" else "Y"} : " +
                        "ΣMRc = ${Checks.format(check.columnMomentKNm)} kN.m, ΣMRb = " +
                        "${Checks.format(check.beamMomentKNm)} kN.m, requis = " +
                        "${Checks.format(check.requiredColumnMomentKNm)} kN.m, ratio = " +
                        "${Checks.format(check.ratio, 2)}."
                )
            }
            warnings.forEach { add("Avertissement : $it") }
        }
        return ChapterReport(
            number = number,
            title = "Hiérarchie des résistances poteaux-poutres",
            summary = "Vérification de la rotule plastique dans les poutres (RPA 2024 § 7.6) : " +
                "${checks.size} direction(s) de nœud contrôlée(s), ${failedChecks.size} en défaut.",
            checks = listOf(synthesis),
            elements = elementReports,
            lines = lines
        )
    }

    private fun StrongColumnNodeCheck.toDesignCheck(): DesignCheck = DesignCheck(
        code = code,
        label = label,
        reference = "RPA 2024 § 7.6",
        status = status,
        required = "ΣMRc ≥ ${Checks.format(requiredColumnMomentKNm)} kN.m",
        provided = "ΣMRc = ${Checks.format(columnMomentKNm)} kN.m",
        message = message
    )
}

/**
 * Verificateur de la condition poteau fort / poutre faible. Les moments resistants sont
 * recalcules a partir des aciers reellement adoptes (chapitres 10.1 et 10.2), et non des
 * efforts issus de l'analyse. La geometrie des noeuds est deduite des connectivites ETABS.
 */
class WeakColumnStrongBeamVerifier(
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine()
) {

    fun assess(
        model: ParsedEtabsModel,
        results: EtabsResults,
        options: WeakColumnStrongBeamOptions = WeakColumnStrongBeamOptions()
    ): WeakColumnStrongBeamAssessment {
        val columns = columnEngine.design(model.joint, results)
        val beams = beamEngine.design(model.joint, results)
        return assess(model, columns, beams, model.joint.materials, options)
    }

    fun assess(
        model: ParsedEtabsModel,
        columnSet: ColumnDesignSet,
        beamSet: BeamDesignSet,
        materials: MaterialConfig,
        options: WeakColumnStrongBeamOptions = WeakColumnStrongBeamOptions()
    ): WeakColumnStrongBeamAssessment {
        val warnings = mutableListOf<String>()
        val columnFrames = model.frames.filter { it.kind == FrameKind.COLUMN }
        val beamFrames = model.frames.filter { it.kind == FrameKind.BEAM }
        if (columnFrames.isEmpty() || beamFrames.isEmpty()) {
            warnings += "Aucune trame poteau/poutre : hiérarchie des nœuds non vérifiable."
            return WeakColumnStrongBeamAssessment(emptyList(), warnings)
        }

        val columnByElement = columnSet.columns.flatMap { design ->
            design.family.elementIds.map { it to design }
        }.toMap()
        val beamByElement = beamSet.beams.flatMap { design ->
            design.family.elementIds.map { it to design }
        }.toMap()
        val mismatch = columnFrames.count { it.id !in columnByElement } + beamFrames.count { it.id !in beamByElement }
        if (mismatch > 0) {
            warnings += "$mismatch élément(s) sans résultat de ferraillage associé : nœuds concernés ignorés."
        }

        val fcd = if (options.useAccidentalStrengths) materials.fbuAccidentalMPa else materials.fbuDurableMPa
        val fyd = if (options.useAccidentalStrengths) materials.sigmaSteelAccidentalMPa else materials.sigmaSteelEluMPa

        val nodes = columnFrames.flatMap { listOf(it.startNodeId, it.endNodeId) }.toSet()
        val checks = mutableListOf<StrongColumnNodeCheck>()
        nodes.sorted().forEach { nodeId ->
            val point = model.points[nodeId] ?: return@forEach
            val lowerFrame = columnFrames.filter { it.endNodeId == nodeId }
            val upperFrame = columnFrames.filter { it.startNodeId == nodeId }
            if (lowerFrame.isEmpty() && upperFrame.isEmpty()) return@forEach
            val attached = beamFrames.filter { it.startNodeId == nodeId || it.endNodeId == nodeId }
            if (attached.isEmpty()) return@forEach
            val story = storyFor(model, nodeId, lowerFrame.firstOrNull() ?: upperFrame.first())
            val category = nodeCategory(model, point, attached)

            AxisDirection.entries.forEach { direction ->
                val directionBeams = attached.filter { beamDirection(model, it) == direction }
                if (directionBeams.isEmpty()) return@forEach
                val columnCapacities = (lowerFrame + upperFrame).mapNotNull { frame ->
                    val design = columnByElement[frame.id] ?: return@mapNotNull null
                    columnCapacity(frame, story, design, direction, fcd, fyd, options)
                }
                val beamCapacities = directionBeams.mapNotNull { frame ->
                    val design = beamByElement[frame.id] ?: return@mapNotNull null
                    beamCapacity(frame, story, design, fcd, fyd, options)
                }
                if (beamCapacities.isEmpty()) return@forEach
                val columnMoment = columnCapacities.sumOf { it.resistingMomentKNm }
                val beamMoment = beamCapacities.sumOf { it.resistingMomentKNm }
                val required = options.overStrengthFactor * beamMoment
                val ratio = if (required > 1e-9) columnMoment / required else Double.POSITIVE_INFINITY
                val status = when {
                    beamMoment <= 1e-9 -> CheckStatus.NOT_APPLICABLE
                    columnMoment + 1e-6 >= required -> CheckStatus.OK
                    ratio >= 0.95 -> CheckStatus.WARNING
                    else -> CheckStatus.FAILED
                }
                val message = when (status) {
                    CheckStatus.NOT_APPLICABLE -> "Aucun moment resistant de poutre exploitable."
                    CheckStatus.OK -> "Hiérarchie satisfaite : ΣMRc/ΣMRb = ${format(ratio)} ≥ 1,25."
                    CheckStatus.WARNING -> "Hiérarchie légèrement insuffisante : ΣMRc/ΣMRb = ${format(ratio)} < 1,25."
                    CheckStatus.FAILED -> "Rotule plastique susceptible de se former dans les poteaux : " +
                        "ΣMRc/ΣMRb = ${format(ratio)} < 1,25."
                }
                checks += StrongColumnNodeCheck(
                    nodeId = nodeId,
                    story = story,
                    category = category,
                    direction = direction,
                    columnCapacities = columnCapacities,
                    beamCapacities = beamCapacities,
                    columnMomentKNm = columnMoment,
                    beamMomentKNm = beamMoment,
                    requiredColumnMomentKNm = required,
                    ratio = ratio,
                    status = status,
                    message = message
                )
            }
        }
        if (checks.isEmpty()) {
            warnings += "Aucun nœud n'a pu être évalué : vérifier les connectivités et les familles de ferraillage."
        }
        return WeakColumnStrongBeamAssessment(checks, warnings.distinct())
    }

    private fun columnCapacity(
        frame: FrameElement,
        story: String,
        design: ColumnDesignResult,
        direction: AxisDirection,
        fcdMPa: Double,
        fydMPa: Double,
        options: WeakColumnStrongBeamOptions
    ): ColumnNodeCapacity {
        // La largeur de section est perpendiculaire au sens de flexion ; la hauteur
        // est la dimension dans le sens du moment. Pour un poteau rectangulaire,
        // la flexion X mobilise h1 comme bras et b1 comme largeur, et inversement
        // pour la flexion Y. Utiliser les dimensions du resultat de ferraillage
        // garantit la coherence avec la section effectivement calculee.
        val family = design.family
        val (breadthCm, depthCm) = orientedColumnSection(
            shape = family.shape,
            widthCm = design.sectionWidthCm,
            depthCm = design.sectionDepthCm,
            direction = direction
        )
        val axial = design.criticalCases.minOfOrNull { it.axialKN } ?: 0.0
        val steel = design.adoptedAsCm2 * 100.0
        val moment = NodeSectionCapacity.flexuralCapacityKNm(
            breadthCm = breadthCm,
            depthCm = depthCm,
            totalSteelMm2 = steel,
            steelAtCompressionFaceMm2 = steel / 2.0,
            steelAtTensionFaceMm2 = steel / 2.0,
            coverMeters = options.concreteCoverMeters,
            fcdMPa = fcdMPa,
            fydMPa = fydMPa,
            axialKN = axial
        )
        return ColumnNodeCapacity(
            elementId = frame.id,
            story = story,
            sectionName = family.name,
            axialKN = axial,
            resistingMomentKNm = moment
        )
    }

    private fun beamCapacity(
        frame: FrameElement,
        story: String,
        design: BeamDesignResult,
        fcdMPa: Double,
        fydMPa: Double,
        options: WeakColumnStrongBeamOptions
    ): BeamNodeCapacity {
        val bottom = (design.spanLayout?.areaCm2 ?: 0.0) * 100.0
        val top = (design.supportLayout?.areaCm2 ?: 0.0) * 100.0
        val bottomContinuous = (design.spanLayout?.continuousAreaCm2 ?: 0.0) * 100.0
        val topContinuous = (design.supportLayout?.continuousAreaCm2 ?: 0.0) * 100.0
        val positive = NodeSectionCapacity.flexuralCapacityKNm(
            breadthCm = design.widthCm,
            depthCm = design.heightCm,
            totalSteelMm2 = bottom + topContinuous,
            steelAtCompressionFaceMm2 = topContinuous,
            steelAtTensionFaceMm2 = bottom,
            coverMeters = 0.025,
            fcdMPa = fcdMPa,
            fydMPa = fydMPa,
            axialKN = 0.0
        )
        val negative = NodeSectionCapacity.flexuralCapacityKNm(
            breadthCm = design.widthCm,
            depthCm = design.heightCm,
            totalSteelMm2 = top + bottomContinuous,
            steelAtCompressionFaceMm2 = bottomContinuous,
            steelAtTensionFaceMm2 = top,
            coverMeters = 0.025,
            fcdMPa = fcdMPa,
            fydMPa = fydMPa,
            axialKN = 0.0
        )
        return BeamNodeCapacity(
            elementId = frame.id,
            story = story,
            sectionName = design.family.name,
            positiveMomentKNm = positive,
            negativeMomentKNm = negative
        )
    }

    private fun nodeCategory(
        model: ParsedEtabsModel,
        node: Point3D,
        beams: List<FrameElement>
    ): JointNodeCategory {
        val axes = beams.map { beamDirection(model, it) }.toSet()
        val sides = beams.mapNotNull { frame ->
            val otherId = if (frame.startNodeId == node.id) frame.endNodeId else frame.startNodeId
            val other = model.points[otherId] ?: return@mapNotNull null
            val dx = other.xMeters - node.xMeters
            val dy = other.yMeters - node.yMeters
            if (abs(dx) >= abs(dy)) (if (dx >= 0) "X+" else "X-") else (if (dy >= 0) "Y+" else "Y-")
        }.toSet()
        return when {
            axes.size >= 2 && sides.size >= 3 -> JointNodeCategory.INTERIOR
            sides.size <= 1 -> JointNodeCategory.CORNER
            else -> JointNodeCategory.EDGE
        }
    }

    private fun beamDirection(model: ParsedEtabsModel, frame: FrameElement): AxisDirection {
        val start = model.points[frame.startNodeId]
        val end = model.points[frame.endNodeId]
        if (start == null || end == null) return AxisDirection.X
        return if (abs(end.xMeters - start.xMeters) >= abs(end.yMeters - start.yMeters)) {
            AxisDirection.X
        } else {
            AxisDirection.Y
        }
    }

    private fun storyFor(model: ParsedEtabsModel, nodeId: String, fallback: FrameElement): String {
        fallback.storyName?.takeIf { it.isNotBlank() }?.let { return it }
        val z = model.points[nodeId]?.zMeters ?: return fallback.storyName.orEmpty()
        return model.joint.storySystem.orderedStories
            .minByOrNull { abs(it.elevationZk - z) }
            ?.storyName.orEmpty()
    }

    private fun format(value: Double): String =
        if (value.isFinite()) String.format(Locale.ROOT, "%.2f", value) else "∞"
}

/**
 * Dimensions (largeur ; hauteur) d'un poteau pour le sens de flexion demande.
 * La hauteur est la dimension dans le plan du moment ; la largeur lui est
 * perpendiculaire. Cette fonction est partagee par le calcul de capacite et
 * ses tests de non-regression.
 */
internal fun orientedColumnSection(
    shape: ColumnShape,
    widthCm: Double,
    depthCm: Double,
    direction: AxisDirection
): Pair<Double, Double> = when (shape) {
    ColumnShape.CIRCULAR -> widthCm to widthCm
    ColumnShape.RECTANGULAR -> if (direction == AxisDirection.X) {
        widthCm to depthCm
    } else {
        depthCm to widthCm
    }
}

/**
 * Capacite en flexion d'une section rectangulaire a armatures symetriques, par
 * equilibre d'une section fissuree BAEL (bloc rectangulaire 0,8 x) et acier
 * elastoplastique parfait. L'effort normal est signe positif en compression.
 */
internal object NodeSectionCapacity {

    fun flexuralCapacityKNm(
        breadthCm: Double,
        depthCm: Double,
        totalSteelMm2: Double,
        steelAtCompressionFaceMm2: Double,
        steelAtTensionFaceMm2: Double,
        coverMeters: Double,
        fcdMPa: Double,
        fydMPa: Double,
        axialKN: Double
    ): Double {
        val b = breadthCm * 10.0
        val h = depthCm * 10.0
        val cover = (coverMeters * 1000.0).coerceIn(10.0, h / 3.0)
        if (b <= 0.0 || h <= 0.0 || fcdMPa <= 0.0 || fydMPa <= 0.0) return 0.0
        val topSteel = steelAtCompressionFaceMm2.coerceAtLeast(0.0)
        val bottomSteel = steelAtTensionFaceMm2.coerceAtLeast(0.0)
        val duplicates = totalSteelMm2.coerceAtLeast(0.0)
        val effectiveTop = if (topSteel + bottomSteel <= 1e-9) duplicates / 2.0 else topSteel
        val effectiveBottom = if (topSteel + bottomSteel <= 1e-9) duplicates / 2.0 else bottomSteel
        val targetN = axialKN * 1000.0

        fun axialAt(x: Double): Double {
            val blockDepth = min(0.8 * x, h)
            val concrete = fcdMPa * b * blockDepth
            fun steelForce(area: Double, depth: Double): Double {
                if (area <= 0.0) return 0.0
                val strain = if (x <= 1e-9) -0.01 else 0.0035 * (x - depth) / x
                val stress = (200_000.0 * strain).coerceIn(-fydMPa, fydMPa)
                return area * stress
            }
            return concrete + steelForce(effectiveTop, cover) + steelForce(effectiveBottom, h - cover)
        }

        var low = 1e-6
        var high = max(h * 2.0, 100.0)
        var guard = 0
        while (axialAt(high) < targetN && guard < 30) {
            high *= 1.7
            guard++
        }
        repeat(140) {
            val mid = (low + high) / 2.0
            if (axialAt(mid) < targetN) low = mid else high = mid
        }
        val x = (low + high) / 2.0
        val blockDepth = min(0.8 * x, h)
        val concreteForce = fcdMPa * b * blockDepth
        val concreteLever = h / 2.0 - blockDepth / 2.0
        var momentNmm = concreteForce * concreteLever
        fun addSteel(area: Double, depth: Double) {
            if (area <= 0.0) return
            val strain = if (x <= 1e-9) -0.01 else 0.0035 * (x - depth) / x
            val stress = (200_000.0 * strain).coerceIn(-fydMPa, fydMPa)
            momentNmm += area * stress * (h / 2.0 - depth)
        }
        addSteel(effectiveTop, cover)
        addSteel(effectiveBottom, h - cover)
        return abs(momentNmm) / 1_000_000.0
    }
}
