package com.archilab.rpa2024.expert

import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingFamily
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.SectionCatalog
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.parser.EtabsSection
import com.archilab.rpa2024.parser.ParsedEtabsModel
import java.util.Locale

/** Décision de redimensionnement automatique d'une famille. */
data class ResizeDecision(
    val elementType: String,
    val elementName: String,
    val originalSection: String,
    val finalSection: String,
    val originalStatus: CheckStatus,
    val finalStatus: CheckStatus,
    val iterations: Int,
    val converged: Boolean,
    val updateHint: String
)

/** Résultat complet d'une passe d'auto-redimensionnement. */
data class AutoResizeReport(
    val snapshot: CrossModelSnapshot,
    val decisions: List<ResizeDecision>,
    val warnings: List<String> = emptyList()
) {
    val changedCount: Int get() = decisions.count { it.originalSection != it.finalSection }
    val convergedCount: Int get() = decisions.count { it.converged }
    val unresolved: List<ResizeDecision> get() = decisions.filter { !it.converged }
    val status: CheckStatus get() = if (unresolved.isEmpty()) CheckStatus.OK else CheckStatus.WARNING
}

data class AutoResizeOptions(
    val stepCm: Double = 5.0,
    val maximumIterations: Int = 8,
    val resizeColumns: Boolean = true,
    val resizeBeams: Boolean = true,
    val maximumColumnDimensionCm: Double = 150.0,
    val maximumBeamHeightCm: Double = 150.0,
    val resizeWalls: Boolean = true,
    val maximumWallThicknessMeters: Double = 0.60,
    val resizeFootings: Boolean = true,
    val maximumFootingWidthMeters: Double = 6.0
) {
    init {
        require(stepCm > 0.0) { "Le pas de redimensionnement doit être positif." }
        require(maximumIterations >= 1) { "Au moins une itération est nécessaire." }
        require(maximumColumnDimensionCm > 0.0 && maximumBeamHeightCm > 0.0)
    }
}

/**
 * Auto-résolveur de sections en échec.
 *
 * Le moteur part du modèle importé, augmente les familles par pas de 5 cm, met à jour
 * la section dans la copie du catalogue et réexécute les solveurs BAEL/RPA sur les
 * efforts importés. Une redistribution complète des efforts exige ensuite une
 * réanalyse ETABS/SAFE : le rapport le signale sans le masquer.
 */
class AutonomousResizeEngine(
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val footingEngine: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator()
) {

    fun resize(
        snapshot: CrossModelSnapshot,
        options: AutoResizeOptions = AutoResizeOptions()
    ): AutoResizeReport {
        var current = snapshot
        val decisions = mutableListOf<ResizeDecision>()

        if (options.resizeColumns) {
            columnEngine.design(current.model.joint, current.results)
                .columns
                .filter { it.status == CheckStatus.FAILED }
                .map { it.family.name }
                .distinct()
                .forEach { familyName ->
                    val result = resizeColumn(current, familyName, options)
                    current = result.first
                    decisions += result.second
                }
        }
        if (options.resizeBeams) {
            beamEngine.design(current.model.joint, current.results)
                .beams
                .filter { it.status == CheckStatus.FAILED }
                .map { it.family.name }
                .distinct()
                .forEach { familyName ->
                    val result = resizeBeam(current, familyName, options)
                    current = result.first
                    decisions += result.second
                }
        }
        if (options.resizeWalls) {
            wallEngine.design(current.model.joint, current.results)
                .walls
                .filter { it.status == CheckStatus.FAILED }
                .map { it.family.pierName }
                .distinct()
                .forEach { pierName ->
                    val result = resizeWall(current, pierName, options)
                    current = result.first
                    decisions += result.second
                }
        }
        if (options.resizeFootings && current.foundations != null) {
            footingEngine.design(current.foundations, current.model.joint.materials)
                .footings
                .filter { it.status == CheckStatus.FAILED }
                .map { it.family.name }
                .distinct()
                .forEach { familyName ->
                    val result = resizeFooting(current, familyName, options)
                    current = result.first
                    decisions += result.second
                }
        }

        val warnings = buildList {
            if (decisions.any { it.originalSection != it.finalSection }) {
                add("Les sections ont été modifiées dans le snapshot : réexécuter ETABS/SAFE pour redistribuer les efforts.")
            }
            decisions.filter { !it.converged }.forEach {
                add("${it.elementType} ${it.elementName} non résolu après ${it.iterations} itération(s).")
            }
        }
        return AutoResizeReport(current, decisions, warnings)
    }

    private fun resizeColumn(
        snapshot: CrossModelSnapshot,
        familyName: String,
        options: AutoResizeOptions
    ): Pair<CrossModelSnapshot, ResizeDecision> {
        val original = snapshot.model.joint.sectionCatalog.column(familyName)
            ?: return snapshot to ResizeDecision(
                "Poteau", familyName, "-", "-", CheckStatus.NOT_APPLICABLE,
                CheckStatus.NOT_APPLICABLE, 0, false, "Famille introuvable."
            )
        val originalSection = "${format(original.b1Cm)}x${format(original.h1Cm)}"
        var current = snapshot
        var family = original
        var status = designColumnStatus(current, familyName)
        var iterations = 0
        while (status == CheckStatus.FAILED && iterations < options.maximumIterations &&
            family.b1Cm < options.maximumColumnDimensionCm &&
            family.h1Cm < options.maximumColumnDimensionCm
        ) {
            val newWidth = (family.b1Cm + options.stepCm).coerceAtMost(options.maximumColumnDimensionCm)
            val newDepth = (family.h1Cm + options.stepCm).coerceAtMost(options.maximumColumnDimensionCm)
            val next = family.copy(
                b1Cm = newWidth,
                h1Cm = newDepth,
                diameterCm = if (family.shape == ColumnShape.CIRCULAR) newWidth else family.diameterCm
            )
            if (next.b1Cm == family.b1Cm && next.h1Cm == family.h1Cm) break
            family = next
            current = replaceColumn(current, family)
            status = designColumnStatus(current, familyName)
            iterations++
        }
        val finalSection = "${format(family.b1Cm)}x${format(family.h1Cm)}"
        return current to ResizeDecision(
            elementType = "Poteau",
            elementName = familyName,
            originalSection = originalSection,
            finalSection = finalSection,
            originalStatus = if (iterations == 0) status else CheckStatus.FAILED,
            finalStatus = status,
            iterations = iterations,
            converged = status != CheckStatus.FAILED,
            updateHint = "Passer la section ETABS $familyName de $originalSection à $finalSection cm."
        )
    }

    private fun resizeBeam(
        snapshot: CrossModelSnapshot,
        familyName: String,
        options: AutoResizeOptions
    ): Pair<CrossModelSnapshot, ResizeDecision> {
        val original = snapshot.model.joint.sectionCatalog.beam(familyName)
            ?: return snapshot to ResizeDecision(
                "Poutre", familyName, "-", "-", CheckStatus.NOT_APPLICABLE,
                CheckStatus.NOT_APPLICABLE, 0, false, "Famille introuvable."
            )
        val originalSection = "${format(original.bCm)}x${format(original.hCm)}"
        var current = snapshot
        var family = original
        var status = designBeamStatus(current, familyName)
        var iterations = 0
        while (status == CheckStatus.FAILED && iterations < options.maximumIterations &&
            family.hCm < options.maximumBeamHeightCm
        ) {
            val next = family.copy(
                bCm = family.bCm + options.stepCm,
                hCm = (family.hCm + options.stepCm).coerceAtMost(options.maximumBeamHeightCm)
            )
            family = next
            current = replaceBeam(current, family)
            status = designBeamStatus(current, familyName)
            iterations++
        }
        val finalSection = "${format(family.bCm)}x${format(family.hCm)}"
        return current to ResizeDecision(
            elementType = "Poutre",
            elementName = familyName,
            originalSection = originalSection,
            finalSection = finalSection,
            originalStatus = if (iterations == 0) status else CheckStatus.FAILED,
            finalStatus = status,
            iterations = iterations,
            converged = status != CheckStatus.FAILED,
            updateHint = "Passer la section ETABS $familyName de $originalSection à $finalSection cm."
        )
    }

    private fun resizeWall(
        snapshot: CrossModelSnapshot,
        pierName: String,
        options: AutoResizeOptions
    ): Pair<CrossModelSnapshot, ResizeDecision> {
        val original = snapshot.model.joint.sectionCatalog.shearWalls
            .firstOrNull { it.pierName == pierName }
            ?: return snapshot to ResizeDecision(
                "Voile", pierName, "-", "-", CheckStatus.NOT_APPLICABLE,
                CheckStatus.NOT_APPLICABLE, 0, false, "Famille de voile introuvable."
            )
        val originalSection = "${format(original.lengthLwMeters * 100.0)}x" +
            "${format(original.thicknessBwMeters * 100.0)} cm"
        var current = snapshot
        var family = original
        var status = designWallStatus(current, pierName)
        var iterations = 0
        while (status == CheckStatus.FAILED && iterations < options.maximumIterations &&
            family.thicknessBwMeters < options.maximumWallThicknessMeters
        ) {
            family = family.copy(
                thicknessBwMeters = (family.thicknessBwMeters + options.stepCm / 100.0)
                    .coerceAtMost(options.maximumWallThicknessMeters)
            )
            current = replaceWall(current, family)
            status = designWallStatus(current, pierName)
            iterations++
        }
        return current to ResizeDecision(
            elementType = "Voile",
            elementName = pierName,
            originalSection = originalSection,
            finalSection = "${format(family.lengthLwMeters * 100.0)}x" +
                "${format(family.thicknessBwMeters * 100.0)} cm",
            originalStatus = if (iterations == 0) status else CheckStatus.FAILED,
            finalStatus = status,
            iterations = iterations,
            converged = status != CheckStatus.FAILED,
            updateHint = "Augmenter l'épaisseur ETABS du voile $pierName à " +
                "${format(family.thicknessBwMeters * 100.0)} cm."
        )
    }

    private fun resizeFooting(
        snapshot: CrossModelSnapshot,
        familyName: String,
        options: AutoResizeOptions
    ): Pair<CrossModelSnapshot, ResizeDecision> {
        val foundation = snapshot.foundations ?: return snapshot to ResizeDecision(
            "Semelle", familyName, "-", "-", CheckStatus.NOT_APPLICABLE,
            CheckStatus.NOT_APPLICABLE, 0, false, "Modèle SAFE absent."
        )
        val original = foundation.families.firstOrNull { it.name == familyName }
            ?: return snapshot to ResizeDecision(
                "Semelle", familyName, "-", "-", CheckStatus.NOT_APPLICABLE,
                CheckStatus.NOT_APPLICABLE, 0, false, "Famille de semelle introuvable."
            )
        val originalSection = "${format(original.flangeWidthMeters)}x" +
            "${format(original.totalHeightMeters)} m"
        var current = snapshot
        var family = original
        var status = designFootingStatus(current, familyName)
        var iterations = 0
        while (status == CheckStatus.FAILED && iterations < options.maximumIterations &&
            family.flangeWidthMeters < options.maximumFootingWidthMeters
        ) {
            family = family.copy(
                flangeWidthMeters = (family.flangeWidthMeters + options.stepCm / 100.0)
                    .coerceAtMost(options.maximumFootingWidthMeters),
                webWidthMeters = family.webWidthMeters + options.stepCm / 100.0,
                totalHeightMeters = family.totalHeightMeters + options.stepCm / 100.0
            )
            current = replaceFooting(current, family)
            status = designFootingStatus(current, familyName)
            iterations++
        }
        return current to ResizeDecision(
            elementType = "Semelle",
            elementName = familyName,
            originalSection = originalSection,
            finalSection = "${format(family.flangeWidthMeters)}x" +
                "${format(family.totalHeightMeters)} m",
            originalStatus = if (iterations == 0) status else CheckStatus.FAILED,
            finalStatus = status,
            iterations = iterations,
            converged = status != CheckStatus.FAILED,
            updateHint = "Augmenter la largeur de semelle $familyName à " +
                "${format(family.flangeWidthMeters)} m."
        )
    }

    private fun designWallStatus(snapshot: CrossModelSnapshot, pierName: String): CheckStatus =
        wallEngine.design(snapshot.model.joint, snapshot.results)
            .walls.firstOrNull { it.family.pierName == pierName }?.status ?: CheckStatus.NOT_APPLICABLE

    private fun designFootingStatus(snapshot: CrossModelSnapshot, familyName: String): CheckStatus {
        val foundation = snapshot.foundations ?: return CheckStatus.NOT_APPLICABLE
        return footingEngine.design(foundation, snapshot.model.joint.materials)
            .footings.firstOrNull { it.family.name == familyName }?.status ?: CheckStatus.NOT_APPLICABLE
    }

    private fun replaceWall(snapshot: CrossModelSnapshot, family: ShearWallFamily): CrossModelSnapshot {
        val joint = snapshot.model.joint
        val catalog = joint.sectionCatalog
        val updated = catalog.copy(
            shearWalls = catalog.shearWalls.map { if (it.pierName == family.pierName) family else it }
        )
        val model = snapshot.model
        val sections = model.sections.map { section ->
            if (section.kind == com.archilab.rpa2024.parser.EtabsSectionKind.WALL &&
                section.name == family.pierName
            ) {
                section.copy(thicknessCm = family.thicknessBwMeters * 100.0)
            } else {
                section
            }
        }
        return snapshot.copy(etabs = snapshot.etabs.copy(
            model = model.copy(joint = joint.copy(sectionCatalog = updated), sections = sections)
        ))
    }

    private fun replaceFooting(snapshot: CrossModelSnapshot, family: StripFootingFamily): CrossModelSnapshot {
        val foundation = snapshot.foundations ?: return snapshot
        val updated = foundation.copy(
            families = foundation.families.map { if (it.name == family.name) family else it }
        )
        return snapshot.copy(foundations = updated)
    }

    private fun designColumnStatus(snapshot: CrossModelSnapshot, familyName: String): CheckStatus =
        columnEngine.design(snapshot.model.joint, snapshot.results)
            .columns.firstOrNull { it.family.name == familyName }?.status ?: CheckStatus.NOT_APPLICABLE

    private fun designBeamStatus(snapshot: CrossModelSnapshot, familyName: String): CheckStatus =
        beamEngine.design(snapshot.model.joint, snapshot.results)
            .beams.firstOrNull { it.family.name == familyName }?.status ?: CheckStatus.NOT_APPLICABLE

    private fun replaceColumn(snapshot: CrossModelSnapshot, family: ColumnFamily): CrossModelSnapshot {
        val joint = snapshot.model.joint
        val catalog = joint.sectionCatalog
        val updatedCatalog = catalog.copy(
            columns = catalog.columns.map { if (it.name == family.name) family else it }
        )
        return replaceJointAndSections(snapshot, joint.copy(sectionCatalog = updatedCatalog), family.name,
            family.b1Cm, family.h1Cm)
    }

    private fun replaceBeam(snapshot: CrossModelSnapshot, family: BeamFamily): CrossModelSnapshot {
        val joint = snapshot.model.joint
        val catalog = joint.sectionCatalog
        val updatedCatalog = catalog.copy(
            beams = catalog.beams.map { if (it.name == family.name) family else it }
        )
        return replaceJointAndSections(snapshot, joint.copy(sectionCatalog = updatedCatalog), family.name,
            family.bCm, family.hCm)
    }

    private fun replaceJointAndSections(
        snapshot: CrossModelSnapshot,
        joint: StructuralJoint,
        sectionName: String,
        widthCm: Double,
        depthCm: Double
    ): CrossModelSnapshot {
        val model = snapshot.model
        val updatedSections = model.sections.map { section ->
            if (section.name == sectionName && section.isFrame) {
                section.copy(
                    widthCm = widthCm,
                    depthCm = depthCm,
                    diameterCm = if (section.kind == com.archilab.rpa2024.parser.EtabsSectionKind.FRAME_CIRCULAR) {
                        widthCm
                    } else {
                        section.diameterCm
                    }
                )
            } else {
                section
            }
        }
        val updatedModel = model.copy(joint = joint, sections = updatedSections)
        return snapshot.copy(etabs = snapshot.etabs.copy(model = updatedModel))
    }

    private fun format(value: Double): String =
        if (kotlin.math.abs(value - value.toInt()) < 1e-6) value.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)
}
