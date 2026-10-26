package com.archilab.rpa2024.expert

import com.archilab.rpa2024.audit.AutoSectionOptimizer
import com.archilab.rpa2024.engine.foundations.StripFootingDesign
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingDesignSet
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignResult
import com.archilab.rpa2024.engine.frames.BeamDesignSet
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignResult
import com.archilab.rpa2024.engine.frames.ColumnDesignSet
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignResult
import com.archilab.rpa2024.engine.walls.WallDesignSet
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.normative.SeismicZone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/** Options du moteur autonome de proposition de ferraillage. */
data class AutonomousDesignOptions(
    val minimumColumnRatioZoneI_II: Double = 0.008,
    val minimumColumnRatioZoneIII: Double = 0.009,
    val minimumColumnRatioZoneIV_V_VI: Double = 0.010,
    val minimumBeamRatio: Double = 0.005,
    val maximumLongitudinalRatio: Double = 0.04,
    val columnBarSpacingLimitCm: Double = 25.0,
    val columnBarSpacingLimitHighZoneCm: Double = 20.0,
    val includeFoundations: Boolean = true
) {
    init {
        require(minimumBeamRatio > 0.0)
        require(maximumLongitudinalRatio > minimumBeamRatio)
        require(columnBarSpacingLimitCm > 0.0)
    }
}

/**
 * Proposition d'exécution d'une famille d'éléments : armatures longitudinales,
 * transversales, statut normatif et éventuelle optimisation de section.
 */
data class ReinforcementProposal(
    val elementType: String,
    val name: String,
    val section: String,
    val longitudinal: String,
    val transverse: String,
    val requiredSteelCm2: Double? = null,
    val providedSteelCm2: Double? = null,
    val ratioPercent: Double? = null,
    val status: CheckStatus,
    val checks: List<DesignCheck> = emptyList(),
    val optimization: String? = null,
    val notes: List<String> = emptyList()
) {
    val isCritical: Boolean get() = status == CheckStatus.FAILED
    val hasOptimization: Boolean get() = !optimization.isNullOrBlank()
}

/** Ensemble des propositions, classé par type d'élément. */
data class ReinforcementProposalSet(
    val proposals: List<ReinforcementProposal>,
    val columns: List<ReinforcementProposal>,
    val beams: List<ReinforcementProposal>,
    val walls: List<ReinforcementProposal>,
    val footings: List<ReinforcementProposal>,
    val warnings: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(proposals.map { it.status })
    val criticalCount: Int get() = proposals.count { it.isCritical }
    val optimizedCount: Int get() = proposals.count { it.hasOptimization }
    val allChecks: List<DesignCheck> get() = proposals.flatMap { it.checks }
}

/**
 * Solveur de proposition de ferraillage à la CYPECAD : il ne se contente pas de
 * ratios, il retourne des barres commerciales, des espacements, des zones nodales
 * et, en cas d'échec, une proposition de section optimisée.
 *
 * Les moteurs BAEL/RPA existants restent la source normative unique ; ce composant
 * les orchestre et expose une vue d'exécution homogène.
 */
class AutonomousReinforcementDesigner(
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val footingEngine: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator(),
    private val optimizer: AutoSectionOptimizer = AutoSectionOptimizer()
) {

    /**
     * Pipeline complet : redimensionnement des éléments en échec puis nouvelle
     * proposition de ferraillage sur le snapshot corrigé. Les efforts importés sont
     * conservés ; une réanalyse ETABS/SAFE reste nécessaire pour la redistribution.
     */
    fun designWithAutoResize(
        snapshot: CrossModelSnapshot,
        resizeOptions: AutoResizeOptions = AutoResizeOptions(),
        designOptions: AutonomousDesignOptions = AutonomousDesignOptions()
    ): Pair<AutoResizeReport, ReinforcementProposalSet> {
        val resized = AutonomousResizeEngine().resize(snapshot, resizeOptions)
        return resized to design(resized.snapshot, designOptions)
    }

    fun design(
        snapshot: CrossModelSnapshot,
        options: AutonomousDesignOptions = AutonomousDesignOptions()
    ): ReinforcementProposalSet {
        val joint = snapshot.model.joint
        val results = snapshot.results
        val zone = SeismicZone.fromCode(joint.seismicParams.zoneCode)
        val columns = columnEngine.design(joint, results)
        val beams = beamEngine.design(joint, results)
        val walls = wallEngine.design(joint, results)
        val footings = if (options.includeFoundations && snapshot.foundations != null) {
            footingEngine.design(snapshot.foundations, joint.materials)
        } else {
            null
        }

        val columnProposals = columns.columns.map { proposal(it, zone, options, joint.materials.fc28MPa) }
        val beamProposals = beams.beams.map { proposal(it, options) }
        val wallProposals = walls.walls.map { proposal(it) }
        val footingProposals = footings?.footings.orEmpty().map { proposal(it) }

        val warnings = buildList {
            addAll(columns.chapter.lines.filter { it.contains("échec", true) || it.contains("Attention", true) })
            addAll(beams.chapter.lines.filter { it.contains("échec", true) || it.contains("Attention", true) })
            addAll(walls.chapter.lines.filter { it.contains("échec", true) || it.contains("Attention", true) })
            footings?.warnings?.let(::addAll)
            if (footings == null) add("Aucun modèle SAFE : les semelles ne sont pas proposées.")
        }.distinct().take(20)

        val all = columnProposals + beamProposals + wallProposals + footingProposals
        return ReinforcementProposalSet(
            proposals = all,
            columns = columnProposals,
            beams = beamProposals,
            walls = wallProposals,
            footings = footingProposals,
            warnings = warnings
        )
    }

    private fun proposal(
        design: ColumnDesignResult,
        zone: SeismicZone,
        options: AutonomousDesignOptions,
        fc28MPa: Double
    ): ReinforcementProposal {
        val section = "${format(design.sectionWidthCm)}x${format(design.sectionDepthCm)} cm"
        val layout = design.layout?.label ?: "disposition indisponible"
        val transverse = design.transverse.let {
            "${it.stirrupLabel}, zone nodale ${format(it.nodalSpacingCm)} cm, " +
                "zone courante ${format(it.currentSpacingCm)} cm, Lrec=${format(it.lapLengthCm)} cm"
        }
        val ratio = if (design.sectionWidthCm * design.sectionDepthCm > 0) {
            design.adoptedAsCm2 / (design.sectionWidthCm * design.sectionDepthCm) * 100.0
        } else {
            null
        }
        val minimumRatio = when (zone) {
            SeismicZone.ZONE_I, SeismicZone.ZONE_II -> options.minimumColumnRatioZoneI_II
            SeismicZone.ZONE_III -> options.minimumColumnRatioZoneIII
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> options.minimumColumnRatioZoneIV_V_VI
            SeismicZone.ZONE_0 -> 0.005
        }
        val optimization = if (design.status == CheckStatus.FAILED) {
            val fix = runCatching {
                optimizer.optimizeColumn(
                    sectionName = design.family.name,
                    widthCm = design.sectionWidthCm,
                    depthCm = design.sectionDepthCm,
                    axialKN = max(0.0, design.axialCompressionKN),
                    fc28MPa = fc28MPa
                )
            }.getOrNull()
            when {
                fix != null -> "Augmenter ${section} vers ${format(fix.proposedWidthCm)}x" +
                    "${format(fix.proposedDepthCm)} cm (ν ${format(fix.previousNu)} → ${format(fix.proposedNu)})."
                else -> "Vérifier le ferraillage et envisager une augmentation de section."
            }
        } else {
            null
        }
        return ReinforcementProposal(
            elementType = "Poteau",
            name = design.family.name,
            section = section,
            longitudinal = layout,
            transverse = transverse,
            requiredSteelCm2 = design.requiredAsCm2,
            providedSteelCm2 = design.adoptedAsCm2,
            ratioPercent = ratio,
            status = design.status,
            checks = design.checks,
            optimization = optimization,
            notes = buildList {
                addAll(design.notes)
                add("Ratio RPA minimal de zone : ${format(minimumRatio * 100.0)} %.")
            }
        )
    }

    private fun proposal(design: BeamDesignResult, options: AutonomousDesignOptions): ReinforcementProposal {
        val section = "${format(design.widthCm)}x${format(design.heightCm)} cm"
        val longitudinal = buildString {
            append("Travée : ${design.spanLayout?.label ?: "indisponible"}")
            append(" | Appui : ${design.supportLayout?.label ?: "indisponible"}")
        }
        val transverse = design.transverse.let {
            "${it.stirrupLabel}, zone nodale ${format(it.nodalSpacingCm)} cm, " +
                "zone courante ${format(it.currentSpacingCm)} cm, l'=${format(it.nodalZoneLengthCm)} cm"
        }
        val required = design.requiredBottomAsCm2 + design.requiredTopAsCm2
        val provided = design.bottomAdoptedAsCm2 + design.topAdoptedAsCm2
        val ratio = if (design.widthCm * design.heightCm > 0) {
            provided / (design.widthCm * design.heightCm) * 100.0
        } else {
            null
        }
        val optimization = if (design.status == CheckStatus.FAILED) {
            "Augmenter la hauteur ou la largeur de poutre et revérifier " +
                "les quatre conditions d'effort tranchant."
        } else {
            null
        }
        return ReinforcementProposal(
            elementType = "Poutre",
            name = design.family.name,
            section = section,
            longitudinal = longitudinal,
            transverse = transverse,
            requiredSteelCm2 = required,
            providedSteelCm2 = provided,
            ratioPercent = ratio,
            status = design.status,
            checks = design.checks,
            optimization = optimization,
            notes = design.notes + "Ratio minimal BAEL/RPA : ${format(options.minimumBeamRatio * 100.0)} %."
        )
    }

    private fun proposal(design: WallDesignResult): ReinforcementProposal {
        val family = design.family
        val section = "${format(family.lengthLwMeters * 100.0)}x${format(family.thicknessBwMeters * 100.0)} cm"
        val boundary = "Rive G : ${design.boundaryLeft.barLabel} ; Rive D : ${design.boundaryRight.barLabel}"
        val transverse = "Cadres rive ${design.boundaryLeft.stirrupLabel}, e=${format(design.boundaryLeft.stirrupSpacingCm)} cm ; " +
            "âme ${design.web.barLabel} e=${format(design.web.verticalSpacingCm)}/${format(design.web.horizontalSpacingCm)} cm"
        val optimization = if (design.status == CheckStatus.FAILED) {
            "Augmenter l'épaisseur du voile ou la longueur de rive, puis revérifier νd et τb."
        } else {
            null
        }
        return ReinforcementProposal(
            elementType = "Voile",
            name = family.pierName,
            section = section,
            longitudinal = boundary,
            transverse = transverse,
            requiredSteelCm2 = design.boundaryLeft.requiredAsCm2 + design.boundaryRight.requiredAsCm2,
            providedSteelCm2 = design.boundaryLeft.adoptedAsCm2 + design.boundaryRight.adoptedAsCm2,
            ratioPercent = design.web.verticalRatio * 100.0,
            status = design.status,
            checks = design.checks,
            optimization = optimization,
            notes = design.notes + boundary
        )
    }

    private fun proposal(design: StripFootingDesign): ReinforcementProposal {
        val family = design.family
        val section = family.sectionLabel
        val longitudinal = "Appui : ${design.supportSteel.label} ; travée : ${design.spanSteel.label} " +
            "(${design.adoptedSpanModel})"
        val transverse = "${design.transverseSteel.label} ; τ=${format(design.shearStressMPa)} / " +
            "${format(design.shearLimitMPa)} MPa"
        val required = design.supportSteel.requiredAreaCm2 + design.spanSteel.requiredAreaCm2
        val provided = design.supportSteel.providedAreaCm2 + design.spanSteel.providedAreaCm2
        val optimization = if (design.status == CheckStatus.FAILED) {
            runCatching { optimizer.optimizeFooting(design) }.getOrNull()?.summary
                ?: "Augmenter la largeur de semelle et/ou la hauteur, puis revérifier la portance."
        } else {
            null
        }
        return ReinforcementProposal(
            elementType = "Semelle filante",
            name = family.name,
            section = section,
            longitudinal = longitudinal,
            transverse = transverse,
            requiredSteelCm2 = required,
            providedSteelCm2 = provided,
            ratioPercent = null,
            status = design.status,
            checks = design.checks,
            optimization = optimization,
            notes = design.notes
        )
    }

    private fun format(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value)
}
