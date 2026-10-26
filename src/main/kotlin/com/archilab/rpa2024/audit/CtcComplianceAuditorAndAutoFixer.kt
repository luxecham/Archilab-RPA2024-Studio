package com.archilab.rpa2024.audit

import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.engine.foundations.StripFootingDesign
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingDesignSet
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignSet
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignSet
import com.archilab.rpa2024.engine.geometry.BeamChecker
import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.ColumnChecker
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.WallChecker
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicAssessment
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngineOptions
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.ContinuousSlabInput
import com.archilab.rpa2024.engine.slabs.ContinuousSlabResult
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignSet
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.normative.ResolvedSeismicConfig
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.MassRigidityCenterRow
import com.archilab.rpa2024.parser.ParsedEtabsModel
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

enum class CtcFindingStatus(val label: String, val severity: Int) {
    CONFORME("Conforme", 0),
    AVERTISSEMENT("Avertissement", 1),
    RESERVE_BLOQUANTE("Réserve bloquante", 2);

    companion object {
        fun fromCheck(status: CheckStatus): CtcFindingStatus = when (status) {
            CheckStatus.OK -> CONFORME
            CheckStatus.WARNING, CheckStatus.NOT_APPLICABLE -> AVERTISSEMENT
            CheckStatus.FAILED -> RESERVE_BLOQUANTE
        }
    }
}

enum class CtcVerdict(val label: String) {
    CONFORME("Avis favorable"),
    CONFORME_AVEC_AVERTISSEMENTS("Avis favorable avec réserves non bloquantes"),
    NON_CONFORME("Avis défavorable - réserves bloquantes")
}

data class CtcControlResult(
    val index: Int,
    val code: String,
    val title: String,
    val reference: String,
    val status: CtcFindingStatus,
    val requirement: String,
    val observation: String,
    val conclusion: String,
    val sourceChecks: List<DesignCheck> = emptyList(),
    val affectedElements: List<String> = emptyList()
) {
    fun toDesignCheck(): DesignCheck = DesignCheck(
        code = code,
        label = title,
        reference = reference,
        status = when (status) {
            CtcFindingStatus.CONFORME -> CheckStatus.OK
            CtcFindingStatus.AVERTISSEMENT -> CheckStatus.WARNING
            CtcFindingStatus.RESERVE_BLOQUANTE -> CheckStatus.FAILED
        },
        required = requirement,
        provided = observation,
        message = conclusion
    )
}

data class CtcAuditOptions(
    val slabLiveLoadKNM2: Double = 1.5,
    val slabFinishingLoadKNM2: Double = 1.2,
    val slabPermanentSuperLoadKNM2: Double = 0.0,
    val includePenthouseInSeismic: Boolean = true,
    val regularityOptions: RegularityCriteriaOptions = RegularityCriteriaOptions(),
    val seismicJointOptions: SeismicJointOptions = SeismicJointOptions(),
    val strongColumnOptions: WeakColumnStrongBeamOptions = WeakColumnStrongBeamOptions()
) {
    init {
        require(slabLiveLoadKNM2 >= 0.0) { "La surcharge de plancher doit être positive ou nulle." }
        require(slabFinishingLoadKNM2 >= 0.0) { "La charge de revêtement doit être positive ou nulle." }
        require(slabPermanentSuperLoadKNM2 >= 0.0) { "La charge permanente additionnelle doit être positive ou nulle." }
    }
}

data class CtcAuditRequest(
    val model: ParsedEtabsModel,
    val results: EtabsResults = EtabsResults(),
    val foundationModel: SafeFoundationModel? = null,
    val adjacentBlock: SeismicBlock? = null,
    val floors: List<FloorGeometry> = DiaphragmGeometryExtractor.fromModel(model),
    val centers: List<MassRigidityCenterRow> = results.massRigidityCenters,
    val options: CtcAuditOptions = CtcAuditOptions()
)

data class CtcAuditReport(
    val jointName: String,
    val regularity: RegularityAssessment,
    val seismicJoint: SeismicJointAssessment?,
    val singleBlockJointRecommendation: SingleBlockSeismicJointRecommendation?,
    val weakColumnStrongBeam: WeakColumnStrongBeamAssessment,
    val controls: List<CtcControlResult>,
    val autoFixes: List<AutoFixProposal>,
    val warnings: List<String>,
    val generatedOn: LocalDate = LocalDate.now()
) {
    init {
        require(controls.size == 15) { "La fiche CTC doit comporter exactement 15 contrôles." }
    }

    val blockingControls: List<CtcControlResult>
        get() = controls.filter { it.status == CtcFindingStatus.RESERVE_BLOQUANTE }
    val warningControls: List<CtcControlResult>
        get() = controls.filter { it.status == CtcFindingStatus.AVERTISSEMENT }

    val verdict: CtcVerdict
        get() = when {
            blockingControls.isNotEmpty() || weakColumnStrongBeam.failedChecks.isNotEmpty() -> CtcVerdict.NON_CONFORME
            warningControls.isNotEmpty() || weakColumnStrongBeam.warningChecks.isNotEmpty() ||
                !regularity.planRegular || !regularity.elevationRegular || warnings.isNotEmpty() ->
                CtcVerdict.CONFORME_AVEC_AVERTISSEMENTS
            else -> CtcVerdict.CONFORME
        }

    val etabsUpdateCommands: List<EtabsUpdateCommand> get() = autoFixes.map { it.updateCommand }

    fun e2kUpdateSnippet(): String = autoFixes
        .map { it.updateCommand.e2kManagedBlock.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

    fun toChapterReport(number: Int = 14): ChapterReport {
        val checks = controls.map { it.toDesignCheck() } +
            regularity.toChapterReport(number).checks +
            weakColumnStrongBeam.toChapterReport(number).checks
        val lines = buildList {
            add(
                "Audit CTC du joint $jointName : ${controls.size} contrôles, " +
                    "${blockingControls.size} réserve(s) bloquante(s), ${warningControls.size} avertissement(s)."
            )
            add(
                "Régularité : plan ${if (regularity.planRegular) "régulière" else "irrégulière"} " +
                    "(Pq1=${Checks.format(regularity.planPenalty, 2)}), élévation " +
                    "${if (regularity.elevationRegular) "régulière" else "irrégulière"} " +
                    "(Pq2=${Checks.format(regularity.elevationPenalty, 2)}), QF=${Checks.format(regularity.qf, 2)}."
            )
            seismicJoint?.let { joint ->
                add(
                    "Joint sismique ${joint.blockAName}/${joint.blockBName} : d_min = " +
                        "${joint.requiredWidthMm?.let { Checks.format(it, 1) } ?: "n/d"} mm, " +
                        "largeur recommandée = ${joint.recommendedWidthMm?.let { Checks.format(it, 0) } ?: "n/d"} mm."
                )
            }
            singleBlockJointRecommendation?.let { single ->
                add(
                    "Bloc unique ${single.blockName} : largeur de joint recommandée = " +
                        "${single.recommendedWidthMm?.let { Checks.format(it, 0) } ?: "n/d"} mm."
                )
            }
            add(
                "Hiérarchie poteaux-poutres : ${weakColumnStrongBeam.checks.size} direction(s) contrôlée(s), " +
                    "${weakColumnStrongBeam.failedChecks.size} non conforme(s)."
            )
            controls.forEach { control ->
                add("CTC-${control.index.toString().padStart(2, '0')} ${control.title} : " +
                    "${control.status.label} - ${control.observation}")
            }
            autoFixes.forEach { fix ->
                add("Auto-correction ${fix.type.label} ${fix.elementName} : ${fix.summary}.")
            }
            warnings.forEach { add("Avertissement : $it") }
        }
        return ChapterReport(
            number = number,
            title = "Audit CTC automatisé et conformité réglementaire",
            summary = "${verdict.label} - ${blockingControls.size} réserve(s) bloquante(s), " +
                "${warningControls.size} avertissement(s), ${autoFixes.size} correction(s) proposée(s).",
            checks = checks,
            lines = lines
        )
    }
}

data class MultiJointCtcAuditReport(
    val jointReports: List<CtcAuditReport>,
    val generatedOn: LocalDate = LocalDate.now()
) {
    val blockingCount: Int get() = jointReports.sumOf { it.blockingControls.size }
    val warningCount: Int get() = jointReports.sumOf { it.warningControls.size }
    val verdict: CtcVerdict
        get() = when {
            jointReports.any { it.verdict == CtcVerdict.NON_CONFORME } -> CtcVerdict.NON_CONFORME
            jointReports.any { it.verdict == CtcVerdict.CONFORME_AVEC_AVERTISSEMENTS } ->
                CtcVerdict.CONFORME_AVEC_AVERTISSEMENTS
            else -> CtcVerdict.CONFORME
        }

    val autoFixes: List<AutoFixProposal> get() = jointReports.flatMap { it.autoFixes }

    fun toChapterReport(number: Int = 14): ChapterReport {
        val checks = jointReports.map { report ->
            DesignCheck(
                code = "CTC_PROJET_${report.jointName.uppercase().replace(Regex("[^A-Z0-9]"), "_")}",
                label = "Audit CTC du joint ${report.jointName}",
                reference = "RPA 2024 / BAEL 91 / CBA 93",
                status = when (report.verdict) {
                    CtcVerdict.CONFORME -> CheckStatus.OK
                    CtcVerdict.CONFORME_AVEC_AVERTISSEMENTS -> CheckStatus.WARNING
                    CtcVerdict.NON_CONFORME -> CheckStatus.FAILED
                },
                required = "Aucune réserve bloquante",
                provided = report.verdict.label,
                message = "${report.blockingControls.size} réserve(s), ${report.warningControls.size} avertissement(s)."
            )
        }
        val lines = jointReports.flatMap { report ->
            listOf(
                "${report.jointName} : ${report.verdict.label} ; QF = ${Checks.format(report.regularity.qf, 2)} ; " +
                    "${report.blockingControls.size} réserve(s) bloquante(s), ${report.warningControls.size} avertissement(s).",
                "  Corrections proposées : ${report.autoFixes.size} ; hiérarchie poteaux-poutres : " +
                    "${report.weakColumnStrongBeam.failedChecks.size} nœud(s) en défaut."
            )
        }
        return ChapterReport(
            number = number,
            title = "Synthèse CTC multi-joints",
            summary = "${jointReports.size} joint(s) audité(s) : ${blockingCount} réserve(s) bloquante(s), " +
                "$warningCount avertissement(s), ${autoFixes.size} correction(s) proposée(s).",
            checks = checks,
            lines = lines
        )
    }

    fun e2kUpdateSnippet(): String = autoFixes
        .map { it.updateCommand.e2kManagedBlock.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n\n")
}

enum class AutoFixType(val label: String) {
    COLUMN_SECTION("Poteau"),
    WALL_SECTION("Voile"),
    FOOTING_WIDTH("Semelle")
}

enum class CtcUpdateKind(val label: String) {
    ETABS_FRAME_SECTION("Section de trame ETABS"),
    ETABS_AREA_SECTION("Section de coque ETABS"),
    SAFE_LINE_SPRING("Ressort linéaire SAFE")
}

data class EtabsUpdateCommand(
    val kind: CtcUpdateKind,
    val targetName: String,
    val proposedName: String,
    val elementIds: List<String>,
    val parameters: Map<String, Double>,
    val oapiCalls: List<String>,
    val e2kManagedBlock: String
)

sealed interface AutoFixProposal {
    val type: AutoFixType
    val elementName: String
    val criterion: String
    val affectedElementIds: List<String>
    val updateCommand: EtabsUpdateCommand
    val summary: String
}

data class ColumnResizeFix(
    override val elementName: String,
    val previousWidthCm: Double,
    val previousDepthCm: Double,
    val proposedWidthCm: Double,
    val proposedDepthCm: Double,
    val previousNu: Double,
    val proposedNu: Double,
    val nuLimit: Double,
    val circular: Boolean,
    override val affectedElementIds: List<String>,
    override val updateCommand: EtabsUpdateCommand
) : AutoFixProposal {
    override val type: AutoFixType = AutoFixType.COLUMN_SECTION
    override val criterion: String = "RPA 2024 § 7.4.3 - effort normal réduit ν ≤ ${formatNu(nuLimit)}"
    override val summary: String
        get() = "${formatSection(previousWidthCm, previousDepthCm, circular)} → " +
            "${formatSection(proposedWidthCm, proposedDepthCm, circular)}, " +
            "ν ${formatNu(previousNu)} → ${formatNu(proposedNu)}"

    private fun formatSection(width: Double, depth: Double, circular: Boolean): String =
        if (circular) "D${formatDimension(width)} cm" else "${formatDimension(width)}x${formatDimension(depth)} cm"

    private fun formatDimension(value: Double): String =
        if (kotlin.math.abs(value - value.toInt()) < 1e-6) value.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)

    private fun formatNu(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
}

data class WallResizeFix(
    override val elementName: String,
    val previousThicknessMeters: Double,
    val proposedThicknessMeters: Double,
    val previousNu: Double,
    val proposedNu: Double,
    val previousTauMPa: Double,
    val proposedTauMPa: Double,
    val nuLimit: Double,
    val tauLimitMPa: Double,
    override val affectedElementIds: List<String>,
    override val updateCommand: EtabsUpdateCommand
) : AutoFixProposal {
    override val type: AutoFixType = AutoFixType.WALL_SECTION
    override val criterion: String = "RPA 2024 § 7.7.2/§ 7.7.3 - νd ≤ ${format(nuLimit)} et τb ≤ ${format(tauLimitMPa)} MPa"
    override val summary: String
        get() = "épaisseur ${format(previousThicknessMeters)} m → ${format(proposedThicknessMeters)} m ; " +
            "νd ${format(previousNu, 3)} → ${format(proposedNu, 3)} ; " +
            "τb ${format(previousTauMPa, 3)} → ${format(proposedTauMPa, 3)} MPa"

    private fun format(value: Double, decimals: Int = 3): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)
}

data class FootingWidthFix(
    override val elementName: String,
    val previousWidthMeters: Double,
    val proposedWidthMeters: Double,
    val previousStressKNM2: Double,
    val proposedStressKNM2: Double,
    val allowableStressKNM2: Double,
    val previousLineStiffnessKNM2: Double?,
    val proposedLineStiffnessKNM2: Double?,
    override val affectedElementIds: List<String>,
    override val updateCommand: EtabsUpdateCommand
) : AutoFixProposal {
    override val type: AutoFixType = AutoFixType.FOOTING_WIDTH
    override val criterion: String = "Portance - σeff ≤ ${format(allowableStressKNM2)} kN/m²"
    override val summary: String
        get() = "largeur ${format(previousWidthMeters)} m → ${format(proposedWidthMeters)} m ; " +
            "σ ${format(previousStressKNM2)} → ${format(proposedStressKNM2)} kN/m² ; " +
            "Kline ${previousLineStiffnessKNM2?.let(::format) ?: "n/d"} → " +
            "${proposedLineStiffnessKNM2?.let(::format) ?: "n/d"} kN/m²"

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}

/**
 * Optimiseur deterministe des sections en echec. Les dimensions sont arrondies au
 * multiple de 5 cm superieur et chaque proposition est verifiee avec la meme formule
 * que le moteur de calcul avant d'etre transmise au pont ETABS/SAFE de la Partie 7.
 */
class AutoSectionOptimizer {

    fun optimizeColumn(
        sectionName: String,
        widthCm: Double,
        depthCm: Double,
        axialKN: Double,
        fc28MPa: Double,
        nuLimit: Double = 0.35,
        incrementCm: Double = 5.0,
        elementIds: List<String> = emptyList(),
        materialName: String? = null,
        circular: Boolean = false
    ): ColumnResizeFix? {
        require(widthCm > 0.0 && depthCm > 0.0) { "Dimensions de poteau invalides." }
        require(axialKN > 0.0) { "L'effort normal doit être une compression positive." }
        require(fc28MPa > 0.0) { "fc28 doit être positif." }
        require(nuLimit > 0.0) { "La limite de ν doit être positive." }
        require(incrementCm > 0.0) { "Le pas d'arrondi doit être positif." }

        val currentArea = if (circular) PI * widthCm * widthCm / 4.0 else widthCm * depthCm
        val previousNu = reducedNormal(axialKN, currentArea, fc28MPa)
        if (previousNu <= nuLimit + 1e-9) return null
        val requiredArea = axialKN * 10.0 / (nuLimit * fc28MPa)

        val proposedWidth: Double
        var proposedDepth: Double
        if (circular) {
            val diameter = ceilToIncrement(sqrt(4.0 * requiredArea / PI), incrementCm)
            proposedWidth = diameter
            proposedDepth = diameter
        } else {
            val ratio = sqrt(requiredArea / currentArea)
            proposedWidth = max(widthCm, ceilToIncrement(widthCm * ratio, incrementCm))
            proposedDepth = max(depthCm, ceilToIncrement(depthCm * ratio, incrementCm))
            if (proposedWidth * proposedDepth + 1e-9 < requiredArea) {
                val extra = requiredArea / proposedWidth
                proposedDepth = ceilToIncrement(max(proposedDepth, extra), incrementCm)
            }
        }
        val proposedArea = if (circular) PI * proposedWidth * proposedWidth / 4.0 else proposedWidth * proposedDepth
        val proposedNu = reducedNormal(axialKN, proposedArea, fc28MPa)
        val proposedName = "${sanitize(sectionName)}_AUTO_" +
            (if (circular) "D${formatDimension(proposedWidth)}" else
                "${formatDimension(proposedWidth)}x${formatDimension(proposedDepth)}")
        val material = materialName ?: "MATERIAU_EXISTANT"
        val oapi = buildList {
            if (circular) {
                add("SapModel.PropFrame.SetCircle(\"$proposedName\", \"$material\", ${meters(proposedWidth)}, \"\")")
            } else {
                add(
                    "SapModel.PropFrame.SetRectangle(\"$proposedName\", \"$material\", " +
                        "${meters(proposedDepth)}, ${meters(proposedWidth)}, \"\")"
                )
            }
            elementIds.forEach { id ->
                add("SapModel.FrameObj.SetSection(\"$id\", \"$proposedName\", 0)")
            }
        }
        val parameters = linkedMapOf(
            "largeur_m" to meters(proposedWidth),
            "hauteur_m" to meters(proposedDepth),
            "aire_cm2" to proposedArea,
            "nu_limite" to nuLimit
        )
        val e2k = managedBlock(
            kind = "FRAME_SECTION",
            target = sectionName,
            lines = listOf(
                "ETABS_ACTION=SET_OR_CREATE_SECTION",
                "NEW_SECTION=\"$proposedName\"",
                "SHAPE=${if (circular) "CIRCULAR" else "RECTANGULAR"}",
                "MATERIAL=\"$material\"",
                "B=${meters(proposedWidth)}",
                "H=${meters(proposedDepth)}",
                "ASSIGN_ELEMENTS=${elementIds.joinToString(",")}"
            )
        )
        return ColumnResizeFix(
            elementName = sectionName,
            previousWidthCm = widthCm,
            previousDepthCm = depthCm,
            proposedWidthCm = proposedWidth,
            proposedDepthCm = proposedDepth,
            previousNu = previousNu,
            proposedNu = proposedNu,
            nuLimit = nuLimit,
            circular = circular,
            affectedElementIds = elementIds,
            updateCommand = EtabsUpdateCommand(
                kind = CtcUpdateKind.ETABS_FRAME_SECTION,
                targetName = sectionName,
                proposedName = proposedName,
                elementIds = elementIds,
                parameters = parameters,
                oapiCalls = oapi,
                e2kManagedBlock = e2k
            )
        )
    }

    fun optimizeWall(
        sectionName: String,
        lengthMeters: Double,
        thicknessMeters: Double,
        axialKN: Double,
        shearKN: Double,
        fc28MPa: Double,
        nuLimit: Double = 0.40,
        tauLimitMPa: Double = 0.20 * fc28MPa,
        incrementMeters: Double = 0.05,
        elementIds: List<String> = emptyList(),
        materialName: String? = null
    ): WallResizeFix? {
        require(lengthMeters > 0.0 && thicknessMeters > 0.0) { "Dimensions de voile invalides." }
        require(fc28MPa > 0.0) { "fc28 doit être positif." }
        require(nuLimit > 0.0 && tauLimitMPa > 0.0) { "Les limites de voile doivent être positives." }
        val previousNu = if (lengthMeters * thicknessMeters * fc28MPa * 1000.0 > 0.0) {
            axialKN / (lengthMeters * thicknessMeters * fc28MPa * 1000.0)
        } else {
            0.0
        }
        val previousTau = 1.4 * shearKN / (thicknessMeters * 0.90 * lengthMeters * 1000.0)
        if (previousNu <= nuLimit + 1e-9 && previousTau <= tauLimitMPa + 1e-9) return null

        val requiredNuThickness = if (axialKN > 0.0) {
            axialKN / (nuLimit * fc28MPa * 1000.0 * lengthMeters)
        } else {
            0.0
        }
        val requiredShearThickness = 1.4 * shearKN / (tauLimitMPa * 0.90 * lengthMeters * 1000.0)
        val required = max(
            thicknessMeters,
            max(
                if (previousNu > nuLimit) requiredNuThickness else 0.0,
                if (previousTau > tauLimitMPa) requiredShearThickness else 0.0
            )
        )
        val proposedThickness = ceilToIncrement(required, incrementMeters)
        val proposedNu = axialKN / (lengthMeters * proposedThickness * fc28MPa * 1000.0)
        val proposedTau = 1.4 * shearKN / (proposedThickness * 0.90 * lengthMeters * 1000.0)
        val proposedName = "${sanitize(sectionName)}_AUTO_EP${formatDimension(proposedThickness * 100.0)}"
        val material = materialName ?: "MATERIAU_EXISTANT"
        val oapi = buildList {
            add(
                "SapModel.PropArea.SetWall(\"$proposedName\", \"$material\", " +
                    "${String.format(Locale.ROOT, "%.3f", proposedThickness)}, 0.0, \"\")"
            )
            elementIds.forEach { id ->
                add("SapModel.AreaObj.SetProperty(\"$id\", \"$proposedName\", 0)")
            }
        }
        val command = EtabsUpdateCommand(
            kind = CtcUpdateKind.ETABS_AREA_SECTION,
            targetName = sectionName,
            proposedName = proposedName,
            elementIds = elementIds,
            parameters = linkedMapOf(
                "epaisseur_m" to proposedThickness,
                "nu_limite" to nuLimit,
                "tau_limite_MPa" to tauLimitMPa
            ),
            oapiCalls = oapi,
            e2kManagedBlock = managedBlock(
                kind = "AREA_SECTION",
                target = sectionName,
                lines = listOf(
                    "ETABS_ACTION=SET_OR_CREATE_AREA_SECTION",
                    "NEW_SECTION=\"$proposedName\"",
                    "MATERIAL=\"$material\"",
                    "THICKNESS=${String.format(Locale.ROOT, "%.3f", proposedThickness)}",
                    "ASSIGN_ELEMENTS=${elementIds.joinToString(",")}"
                )
            )
        )
        return WallResizeFix(
            elementName = sectionName,
            previousThicknessMeters = thicknessMeters,
            proposedThicknessMeters = proposedThickness,
            previousNu = previousNu,
            proposedNu = proposedNu,
            previousTauMPa = previousTau,
            proposedTauMPa = proposedTau,
            nuLimit = nuLimit,
            tauLimitMPa = tauLimitMPa,
            affectedElementIds = elementIds,
            updateCommand = command
        )
    }

    fun optimizeFooting(
        design: StripFootingDesign,
        incrementMeters: Double = 0.05
    ): FootingWidthFix? {
        val soil = design.soil ?: return null
        val currentWidth = design.family.flangeWidthMeters
        val stress = soil.effectiveStressKNM2
        val allowable = soil.allowableStressKNM2
        if (stress <= allowable + 1e-9 || currentWidth <= 0.0) return null
        val required = currentWidth * stress / allowable
        val proposedWidth = ceilToIncrement(max(required, currentWidth + incrementMeters), incrementMeters)
        val proposedStress = stress * currentWidth / proposedWidth
        val spring = design.spring
        val proposedKline = spring?.let { it.subgradeModulusKNM3 * proposedWidth }
        val proposedSpringName = spring?.let { "${it.name}_AUTO" } ?: "${design.family.name}_K_AUTO"
        val oapi = buildList {
            add("SapModel.PropLineSpring.SetLineSpringProp(\"$proposedSpringName\", " +
                "${spring?.subgradeModulusKNM3 ?: "KS"}, $proposedWidth, 0)")
            add("SapModel.FrameObj.SetLineSpring(\"${design.family.name}\", \"$proposedSpringName\", 0)")
        }
        val command = EtabsUpdateCommand(
            kind = CtcUpdateKind.SAFE_LINE_SPRING,
            targetName = spring?.name ?: design.family.name,
            proposedName = proposedSpringName,
            elementIds = design.family.elementIds,
            parameters = linkedMapOf(
                "largeur_m" to proposedWidth,
                "ks_kN_m3" to (spring?.subgradeModulusKNM3 ?: 0.0),
                "kline_kN_m2" to (proposedKline ?: 0.0),
                "contrainte_limite_kN_m2" to allowable
            ),
            oapiCalls = oapi,
            e2kManagedBlock = managedBlock(
                kind = "LINE_SPRING",
                target = spring?.name ?: design.family.name,
                lines = listOf(
                    "SAFE_ACTION=SET_LINE_SPRING",
                    "NEW_SPRING=\"$proposedSpringName\"",
                    "WIDTH=${String.format(Locale.ROOT, "%.3f", proposedWidth)}",
                    "KS=${String.format(Locale.ROOT, "%.1f", spring?.subgradeModulusKNM3 ?: 0.0)}",
                    "KLINE=${String.format(Locale.ROOT, "%.1f", proposedKline ?: 0.0)}",
                    "ASSIGN_ELEMENTS=${design.family.elementIds.joinToString(",")}"
                )
            )
        )
        return FootingWidthFix(
            elementName = design.family.name,
            previousWidthMeters = currentWidth,
            proposedWidthMeters = proposedWidth,
            previousStressKNM2 = stress,
            proposedStressKNM2 = proposedStress,
            allowableStressKNM2 = allowable,
            previousLineStiffnessKNM2 = design.spring?.lineStiffnessKNM2,
            proposedLineStiffnessKNM2 = proposedKline,
            affectedElementIds = design.family.elementIds,
            updateCommand = command
        )
    }

    private fun reducedNormal(axialKN: Double, areaCm2: Double, fc28MPa: Double): Double =
        if (areaCm2 <= 0.0 || fc28MPa <= 0.0) 0.0 else axialKN * 10.0 / (areaCm2 * fc28MPa)

    private fun ceilToIncrement(value: Double, step: Double): Double =
        ceil(value / step - 1e-9) * step

    private fun meters(valueCm: Double): Double = valueCm / 100.0

    private fun formatDimension(value: Double): String =
        if (kotlin.math.abs(value - value.toInt()) < 1e-6) value.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)

    private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun sanitize(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "SECTION" }

    private fun managedBlock(kind: String, target: String, lines: List<String>): String = buildString {
        appendLine("\$ RPA2024:AUTOFIX:BEGIN:$kind:$target")
        lines.forEach { appendLine("\$ $it") }
        append("\$ RPA2024:AUTOFIX:END:$kind:$target")
    }
}

/**
 * Auditeur global CTC. Il rejoue les moteurs des Parties 2 a 8 sur le joint importe,
 * agrège les quinze contrôles critiques, contrôle la regularite et les joints, puis
 * propose des corrections dimensionnelles verifiees.
 */
class CtcComplianceAuditorAndAutoFixer(
    private val beamChecker: BeamChecker = BeamChecker(),
    private val columnChecker: ColumnChecker = ColumnChecker(),
    private val wallChecker: WallChecker = WallChecker(),
    private val slabSolver: ContinuousRibbedSlabSolver = ContinuousRibbedSlabSolver(),
    private val seismicEngine: Rpa2024SeismicEngine = Rpa2024SeismicEngine(),
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val foundationEngine: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator(),
    private val regularityAnalyzer: RegularityAnalyzerRpa2024 = RegularityAnalyzerRpa2024(),
    private val jointCalculator: SeismicJointWidthCalculator = SeismicJointWidthCalculator(),
    private val weakColumnVerifier: WeakColumnStrongBeamVerifier = WeakColumnStrongBeamVerifier(),
    private val optimizer: AutoSectionOptimizer = AutoSectionOptimizer()
) {

    fun audit(request: CtcAuditRequest): CtcAuditReport {        val model = request.model
        val joint = model.joint
        val materials = joint.materials
        val warnings = mutableListOf<String>()

        val regularity = regularityAnalyzer.analyze(
            RegularityAnalysisRequest(
                model = model,
                results = request.results,
                floors = request.floors,
                centers = request.centers,
                options = request.options.regularityOptions
            )
        )
        warnings += regularity.warnings
        val auditedJoint = joint.copy(seismicParams = regularity.toSeismicConfig(joint.seismicParams))
        val resolved = Rpa2024NormativeDb.resolveSeismic(auditedJoint.seismicParams, auditedJoint)

        val seismic = seismicEngine.assess(
            auditedJoint,
            request.results,
            config = auditedJoint.seismicParams,
            options = Rpa2024SeismicEngineOptions(includePenthouse = request.options.includePenthouseInSeismic)
        )
        val slabs = solveSlabs(request, materials, warnings)
        val columns = columnEngine.design(auditedJoint, request.results)
        val beams = beamEngine.design(auditedJoint, request.results)
        val walls = wallEngine.design(auditedJoint, request.results)
        val footings = request.foundationModel?.let { foundationEngine.design(it, materials) }
        val weakBeam = weakColumnVerifier.assess(model, columns, beams, materials, request.options.strongColumnOptions)
        warnings += weakBeam.warnings

        val pair = request.adjacentBlock?.let { adjacent ->
            jointCalculator.assess(
                SeismicBlock(joint.jointName, auditedJoint, request.results),
                adjacent,
                request.options.seismicJointOptions
            )
        }
        val single = if (pair == null) {
            jointCalculator.recommendSingleBlock(
                SeismicBlock(joint.jointName, auditedJoint, request.results),
                request.options.seismicJointOptions
            )
        } else {
            null
        }
        pair?.let { warnings += it.warnings }
        single?.let { warnings += it.warnings }

        val autoFixes = mutableListOf<AutoFixProposal>()
        autoFixes += columnFixes(model, columns, materials)
        autoFixes += wallFixes(model, walls, materials)
        autoFixes += footingFixes(footings)

        val controls = buildControls(
            request = request,
            resolved = resolved,
            seismic = seismic,
            slabs = slabs,
            columns = columns,
            beams = beams,
            walls = walls,
            footings = footings,
            pair = pair,
            single = single
        )
        warnings += controls.filter { it.status != CtcFindingStatus.CONFORME }.map { "${it.code} : ${it.conclusion}" }
        if (request.adjacentBlock == null && single != null) {
            warnings += "Bloc adjacent non importé : la largeur de joint est une recommandation à confirmer."
        }
        if (weakBeam.failedChecks.isNotEmpty()) {
            warnings += "${weakBeam.failedChecks.size} nœud(s) ne satisfont pas la hiérarchie poteau fort / poutre faible."
        }

        return CtcAuditReport(
            jointName = joint.jointName,
            regularity = regularity,
            seismicJoint = pair,
            singleBlockJointRecommendation = single,
            weakColumnStrongBeam = weakBeam,
            controls = controls,
            autoFixes = autoFixes,
            warnings = warnings.distinct()
        )
    }

    fun auditAll(requests: List<CtcAuditRequest>): MultiJointCtcAuditReport {
        require(requests.isNotEmpty()) { "Au moins un joint doit être fourni pour l'audit multi-joints." }
        return MultiJointCtcAuditReport(requests.map(::audit))
    }

    private fun buildControls(
        request: CtcAuditRequest,
        resolved: ResolvedSeismicConfig,
        seismic: Rpa2024SeismicAssessment,
        slabs: List<ContinuousSlabResult>,
        columns: ColumnDesignSet,
        beams: BeamDesignSet,
        walls: WallDesignSet,
        footings: StripFootingDesignSet?,
        pair: SeismicJointAssessment?,
        single: SingleBlockSeismicJointRecommendation?
    ): List<CtcControlResult> {
        val joint = request.model.joint
        val beamReports = joint.sectionCatalog.beams.map {
            beamChecker.check(it, resolved.zone, resolved.bracingSystem)
        }
        val columnReports = joint.sectionCatalog.columns.map {
            columnChecker.check(it, resolved.zone, joint.materials.fc28MPa)
        }
        val wallReports = joint.sectionCatalog.shearWalls.map { wallChecker.check(it) }

        val control1 = aggregate(
            index = 1,
            code = "CTC01",
            title = "Coffrage minimal des poutres",
            reference = "RPA 2024 § 7.5.1 / Fig. 7.4",
            checks = beamReports.flatMap { it.checks },
            affected = beamReports.filter { it.status != CheckStatus.OK }.map { it.name },
            noData = "Aucune famille de poutres exploitable."
        )
        val control2 = aggregate(
            index = 2,
            code = "CTC02",
            title = "Coffrage minimal des poteaux",
            reference = "RPA 2024 § 7.4.1 / Fig. 7.1",
            checks = columnReports.flatMap { it.checks },
            affected = columnReports.filter { it.status != CheckStatus.OK }.map { it.name },
            noData = "Aucune famille de poteaux exploitable."
        )
        val control3 = aggregate(
            index = 3,
            code = "CTC03",
            title = "Coffrage minimal des voiles",
            reference = "RPA 2024 § 7.7.1 / Fig. 7.7",
            checks = wallReports.flatMap { it.checks },
            affected = wallReports.filter { it.status != CheckStatus.OK }.map { it.name },
            noData = "Aucune famille de voiles exploitable."
        )
        val deflectionChecks = slabs.flatMap { it.deflection.checks }
        val control4 = aggregate(
            index = 4,
            code = "CTC04",
            title = "Flèche des poutrelles de plancher",
            reference = "BAEL 91 révisé 99, annexe B.6.5",
            checks = deflectionChecks,
            affected = slabs.filter { it.deflection.status != CheckStatus.OK }.map { it.topology.name },
            noData = "Aucun plancher à poutrelles calculé."
        )
        val modal = seismic.modalParticipation
        val modalCheck = DesignCheck(
            code = "CTC05_MODAL",
            label = "Participation massique modale",
            reference = "RPA 2024 § 4.3.3",
            status = modal.status,
            required = "ΣMx ≥ 90 % et ΣMy ≥ 90 %, ou k ≥ 3√N et Tk ≤ 0,20 s",
            provided = "${modal.modeCount} mode(s), ΣMx = ${Checks.format(modal.sumUxPercent, 1)} %, " +
                "ΣMy = ${Checks.format(modal.sumUyPercent, 1)} %",
            message = modal.message
        )
        val control5 = aggregate(
            5, "CTC05", "Participation massique modale", "RPA 2024 § 4.3.3",
            listOf(modalCheck), emptyList(), "Participation modale indisponible."
        )
        val periodChecks = seismic.baseShear.map { shear ->
            val limit = 1.30 * shear.periodEmpiricalSeconds
            val status = when {
                shear.periodAnalyticalSeconds == null -> CheckStatus.NOT_APPLICABLE
                shear.periodRetainedSeconds <= limit + 1e-9 -> CheckStatus.OK
                else -> CheckStatus.FAILED
            }
            DesignCheck(
                code = "CTC06_PERIODE_${shear.direction.name}",
                label = "Plafonnement de la période ${shear.directionLabel}",
                reference = "RPA 2024 § 4.2.4",
                status = status,
                required = "T0 ≤ 1,30 Temp = ${Checks.format(limit, 3)} s",
                provided = "T0 = ${Checks.format(shear.periodRetainedSeconds, 3)} s",
                message = if (shear.periodWasCapped) {
                    "Période analytique ${Checks.format(shear.periodAnalyticalSeconds ?: 0.0, 3)} s " +
                        "plafonnée à ${Checks.format(shear.periodRetainedSeconds, 3)} s."
                } else {
                    "Période retenue ${Checks.format(shear.periodRetainedSeconds, 3)} s."
                }
            )
        }
        val control6 = aggregate(
            6, "CTC06", "Plafonnement de la période", "RPA 2024 § 4.2.4",
            periodChecks, seismic.baseShear.map { it.directionLabel }, "Périodes sismiques indisponibles."
        )
        val baseShearChecks = seismic.baseShear.map { shear ->
            val status = when {
                shear.staticBaseShearKN == null || shear.dynamicBaseShearKN == null -> CheckStatus.NOT_APPLICABLE
                shear.scaleFactor != null -> CheckStatus.WARNING
                else -> CheckStatus.OK
            }
            DesignCheck(
                code = "CTC07_BASE_${shear.direction.name}",
                label = "Calage de l'effort tranchant à la base ${shear.directionLabel}",
                reference = "RPA 2024 § 4.3.5",
                status = status,
                required = "V_MDS ≥ 0,80 V_MSE",
                provided = "V_MSE = ${shear.staticBaseShearKN?.let { Checks.format(it, 1) } ?: "n/d"} kN ; " +
                    "V_MDS = ${shear.dynamicBaseShearKN?.let { Checks.format(it, 1) } ?: "n/d"} kN",
                message = shear.message
            )
        }
        val control7 = aggregate(
            7, "CTC07", "Calage de l'effort tranchant à la base", "RPA 2024 § 4.3.5",
            baseShearChecks, seismic.baseShear.map { it.directionLabel }, "Réactions sismiques indisponibles."
        )
        val nuChecks = columns.columns.flatMap { it.checks.filter { check -> check.code == "PP10.3_NU" } }
        val nuFailures = columns.columns.filter { it.normalizedAxial > it.normalizedAxialLimit + 1e-9 }
        val control8 = aggregate(
            8, "CTC08", "Effort normal réduit des poteaux", "RPA 2024 § 7.4.3, Éq. 7.5",
            nuChecks, nuFailures.map { it.family.name }, "Efforts normaux de poteaux indisponibles."
        )
        val driftChecks = seismic.driftChecks.map { drift ->
            DesignCheck(
                code = "CTC09_DEPL_${drift.direction.name}_${drift.story}",
                label = "Déplacement inter-étages ${drift.story}",
                reference = "RPA 2024 § 5.10",
                status = drift.status,
                required = "νA·Δk ≤ ${Checks.format(drift.governingLimitMeters * 1000.0, 2)} mm",
                provided = "νA·Δk = ${Checks.format(drift.reducedDriftMeters * 1000.0, 2)} mm",
                message = drift.message
            )
        }
        val control9 = aggregate(
            9, "CTC09", "Déplacements inter-étages", "RPA 2024 § 5.10",
            driftChecks, seismic.driftChecks.filter { it.status != CheckStatus.OK }.map { it.story },
            "Déplacements inter-étages indisponibles."
        )
        val pDeltaChecks = seismic.pDeltaChecks.map { pDelta ->
            DesignCheck(
                code = "CTC10_PDELTA_${pDelta.direction.name}_${pDelta.story}",
                label = "Effet P-Delta ${pDelta.story}",
                reference = "RPA 2024 § 5.9",
                status = pDelta.status,
                required = "θ ≤ 0,10",
                provided = "θ = ${Checks.format(pDelta.theta, 3)}",
                message = pDelta.message
            )
        }
        val control10 = aggregate(
            10, "CTC10", "Stabilité au second ordre P-Delta", "RPA 2024 § 5.9",
            pDeltaChecks, seismic.pDeltaChecks.filter { it.status != CheckStatus.OK }.map { it.story },
            "Données P-Delta indisponibles."
        )
        val columnRebarChecks = columns.columns.flatMap { design ->
            design.checks.filter { check ->
                check.code.startsWith("PP10.1_") || check.code.startsWith("PP10.3_TAU_")
            }
        }
        val control11 = aggregate(
            11, "CTC11", "Armatures et cisaillement des poteaux", "RPA 2024 § 7.4.2/§ 7.4.3",
            columnRebarChecks, columns.columns.filter { it.status != CheckStatus.OK }.map { it.family.name },
            "Ferraillage de poteaux indisponible."
        )
        val control12 = aggregate(
            12, "CTC12", "Armatures et cisaillement des poutres", "RPA 2024 § 7.5.2",
            beams.beams.flatMap { it.checks }, beams.beams.filter { it.status != CheckStatus.OK }.map { it.family.name },
            "Ferraillage de poutres indisponible."
        )
        val wallChecks = walls.walls.flatMap { it.qualification.checks + it.checks }
            .distinctBy { listOf(it.code, it.status.name, it.provided) }
        val control13 = aggregate(
            13, "CTC13", "Voiles : effort normal, cisaillement et ductilité", "RPA 2024 § 7.7",
            wallChecks, walls.walls.filter { it.status != CheckStatus.OK }.map { it.family.pierName },
            "Calcul de voiles indisponible."
        )
        val soilChecks = footings?.soilChecks?.flatMap { it.result.checks }.orEmpty()
        val soilFailures = footings?.soilChecks?.filter { it.result.status != CheckStatus.OK }
            ?.map { it.familyName }.orEmpty()
        val control14 = aggregate(
            14, "CTC14", "Contrainte effective du sol", "DTR BC 2.33.1 / BAEL 91",
            soilChecks, soilFailures,
            "Aucune vérification de portance SAFE disponible."
        )
        val jointCheck = pair?.check ?: single?.check
        val jointStatusCheck = when {
            jointCheck == null -> Checks.notApplicable(
                "CTC15_JOINT", "Largeur minimale du joint sismique", "RPA 2024 § 5.11",
                "Aucune donnée de bloc ou de déplacement sismique."
            )
            single != null && pair == null -> jointCheck.copy(
                status = CheckStatus.OK,
                message = jointCheck.message + " Bloc adjacent non importé : recommandation à confirmer."
            )
            else -> jointCheck
        }
        val control15 = aggregate(
            15, "CTC15", "Largeur minimale du joint sismique", "RPA 2024 § 5.11",
            listOf(jointStatusCheck),
            listOfNotNull(pair?.blockAName, pair?.blockBName, single?.blockName),
            "Largeur de joint non calculable."
        )
        return listOf(
            control1, control2, control3, control4, control5, control6, control7, control8,
            control9, control10, control11, control12, control13, control14, control15
        )
    }

    private fun aggregate(
        index: Int,
        code: String,
        title: String,
        reference: String,
        checks: List<DesignCheck>,
        affected: List<String>,
        noData: String
    ): CtcControlResult {
        val applicable = checks.filter { it.status != CheckStatus.NOT_APPLICABLE }
        if (applicable.isEmpty()) {
            return CtcControlResult(
                index = index,
                code = code,
                title = title,
                reference = reference,
                status = CtcFindingStatus.AVERTISSEMENT,
                requirement = "Vérification disponible",
                observation = noData,
                conclusion = noData,
                sourceChecks = checks,
                affectedElements = affected.distinct()
            )
        }
        val governing = applicable.maxWithOrNull(
            compareBy<DesignCheck> { it.status.severity }.thenBy { it.message.length }
        ) ?: applicable.first()
        val status = CtcFindingStatus.fromCheck(governing.status)
        val observation = buildString {
            append(governing.provided.ifBlank { governing.message })
            if (governing.message.isNotBlank() && governing.message != governing.provided) {
                if (isNotEmpty()) append(" - ")
                append(governing.message)
            }
        }
        return CtcControlResult(
            index = index,
            code = code,
            title = title,
            reference = reference,
            status = status,
            requirement = governing.required,
            observation = observation.ifBlank { "Vérifié" },
            conclusion = when (status) {
                CtcFindingStatus.CONFORME -> "Aucune réserve sur ce point."
                CtcFindingStatus.AVERTISSEMENT -> "Vérification à lever ou à compléter avant avis définitif."
                CtcFindingStatus.RESERVE_BLOQUANTE -> "Réserve bloquante : la conception ou le ferraillage doit être corrigé."
            },
            sourceChecks = checks,
            affectedElements = (affected.distinct().ifEmpty { listOf(governing.label) }).take(40)
        )
    }

    private fun solveSlabs(
        request: CtcAuditRequest,
        materials: MaterialConfig,
        warnings: MutableList<String>
    ): List<ContinuousSlabResult> = request.model.joint.sectionCatalog.slabs
        .filter { it.continuousSpansMeters.isNotEmpty() }
        .mapNotNull { topology ->
            val selfWeight = topology.htMeters * materials.gammaConcreteKNm3
            runCatching {
                slabSolver.solve(
                    ContinuousSlabInput(
                        topology = topology,
                        permanentLoadKNPerM2 = selfWeight + request.options.slabFinishingLoadKNM2 +
                            request.options.slabPermanentSuperLoadKNM2,
                        variableLoadKNPerM2 = request.options.slabLiveLoadKNM2,
                        materials = materials,
                        permanentBeforeFinishesKNPerM2 = selfWeight,
                        leftCantileverMeters = topology.leftCantileverMeters,
                        rightCantileverMeters = topology.rightCantileverMeters
                    )
                )
            }.onFailure {
                warnings += "Flèche/flexion du plancher '${topology.name}' non calculable : ${it.message.orEmpty()}."
            }.getOrNull()
        }

    private fun columnFixes(
        model: ParsedEtabsModel,
        columns: ColumnDesignSet,
        materials: MaterialConfig
    ): List<ColumnResizeFix> = columns.columns.mapNotNull { design ->
        if (design.family.isWallBoundary || design.axialCompressionKN <= 0.0) return@mapNotNull null
        if (design.normalizedAxial <= design.normalizedAxialLimit + 1e-9) return@mapNotNull null
        val family = design.family
        val material = model.section(family.name)?.materialName
        optimizer.optimizeColumn(
            sectionName = family.name,
            widthCm = design.sectionWidthCm,
            depthCm = design.sectionDepthCm,
            axialKN = design.axialCompressionKN,
            fc28MPa = materials.fc28MPa,
            nuLimit = design.normalizedAxialLimit,
            elementIds = family.elementIds,
            materialName = material,
            circular = family.shape == ColumnShape.CIRCULAR
        )
    }

    private fun wallFixes(
        model: ParsedEtabsModel,
        walls: WallDesignSet,
        materials: MaterialConfig
    ): List<WallResizeFix> = walls.walls.mapNotNull { design ->
        val qualification = design.qualification
        if (!qualification.forces.hasForces) return@mapNotNull null
        val failing = qualification.normalizedAxial > 0.40 + 1e-9 ||
            qualification.tauMPa > qualification.tauLimitMPa + 1e-9
        if (!failing) return@mapNotNull null
        val area = model.areas.firstOrNull { area ->
            area.kind == AreaKind.WALL &&
                (area.pierName.equals(design.family.pierName, ignoreCase = true) || area.id in design.family.elementIds)
        }
        val sourceSection = area?.propertyName ?: design.family.pierName
        val material = area?.let { model.section(it.propertyName)?.materialName }
            ?: model.section(design.family.pierName)?.materialName
        optimizer.optimizeWall(
            sectionName = sourceSection,
            lengthMeters = design.family.lengthLwMeters,
            thicknessMeters = design.family.thicknessBwMeters,
            axialKN = qualification.forces.axialCompressionKN,
            shearKN = qualification.forces.shearKN,
            fc28MPa = materials.fc28MPa,
            nuLimit = 0.40,
            tauLimitMPa = qualification.tauLimitMPa,
            elementIds = design.family.elementIds,
            materialName = material
        )
    }

    private fun footingFixes(footings: StripFootingDesignSet?): List<FootingWidthFix> =
        footings?.footings?.mapNotNull { design ->
            optimizer.optimizeFooting(design)
        }.orEmpty()
}
