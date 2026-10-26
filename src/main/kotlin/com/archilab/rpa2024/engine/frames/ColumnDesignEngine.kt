package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.parser.ColumnForceRow
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val AXIAL_COMPRESSION_LIMIT = 0.35

/** Demande de confinement d'une direction de poteau (RPA 2024 § 7.4.2.2). */
private data class ColumnConfinementDemand(
    val slenderness: Double,
    val rhoA: Double,
    val minimumRatio: Double,
    val requiredAtNodalCm2: Double,
    val requiredAtCurrentCm2: Double
)

/** Parametres communs aux moteurs de ferraillage des poteaux et des poutres. */
data class FrameDesignSettings(
    val steelCoverCm: Double = 5.0,
    val concreteCoverCm: Double = 3.0,
    val longitudinalMinDiameterMm: Int = 12,
    val stirrupDiameterMm: Int = 8,
    val stirrupLegs: Int = 4,
    val columnMaxSteelRatio: Double = 0.04,
    val beamMinSteelRatio: Double = 0.005,
    val beamMaxSteelRatio: Double = 0.04,
    val elsSteelStressLimitMPa: Double? = null
)

/** Sens de flexion d'un poteau : 2-2 (mineur) ou 3-3 (majeur). */
enum class FrameBendingAxis(val label: String, val shortLabel: String) {
    MINOR_2_2("2-2 (mineur)", "M2"),
    MAJOR_3_3("3-3 (majeur)", "M3")
}

/** Couple critique extrait des tables Column Forces. */
data class ColumnCriticalCase(
    val label: String,
    val combo: String,
    val category: ComboCategory,
    val axis: FrameBendingAxis,
    val axialKN: Double,
    val momentKNm: Double,
    val designWidthCm: Double,
    val designDepthCm: Double,
    val bael: BaelRectangularResult
) {
    val isCompression: Boolean get() = axialKN >= 0.0
    val isTension: Boolean get() = axialKN < 0.0
}

/** Verification de cisaillement dans une direction de poteau. */
data class ColumnShearCheck(
    val axis: FrameBendingAxis,
    val shearKN: Double,
    val widthCm: Double,
    val effectiveDepthCm: Double,
    val slenderness: Double,
    val tauMPa: Double,
    val tauLimitMPa: Double,
    val status: CheckStatus
)

/** Armatures transversales et zones de confinement d'un poteau. */
data class ColumnTransverseDesign(
    val clearHeightMeters: Double,
    val nodalZoneLengthCm: Double,
    val lapLengthCm: Double,
    val nodalSpacingLimitCm: Double,
    val currentSpacingLimitCm: Double,
    val nodalSpacingCm: Double,
    val currentSpacingCm: Double,
    val stirrupLegs: Int,
    val stirrupDiameterMm: Int,
    val stirrupAreaCm2: Double,
    val requiredStirrupAreaCm2: Double,
    val slendernessLambda: Double,
    val rhoA: Double,
    val minimumTransverseRatio: Double,
    val anchorageLengthCm: Double,
    val shearChecks: List<ColumnShearCheck>
) {
    val stirrupLabel: String get() = "$stirrupLegs brins φ$stirrupDiameterMm"
}

/** Resultat complet du ferraillage d'une famille de poteaux. */
data class ColumnDesignResult(
    val family: ColumnFamily,
    val sectionWidthCm: Double,
    val sectionDepthCm: Double,
    val effectiveDepthCm: Double,
    val criticalCases: List<ColumnCriticalCase>,
    val governingCase: ColumnCriticalCase?,
    val requiredAsCm2: Double,
    val minimumAsCm2: Double,
    val maximumAsCm2: Double,
    val layout: ColumnRebarLayout?,
    val adoptedAsCm2: Double,
    val maximumLapAsCm2: Double,
    val axialCompressionKN: Double,
    val normalizedAxial: Double,
    val normalizedAxialLimit: Double,
    val transverse: ColumnTransverseDesign,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })

    fun toElementReport(): ElementCheckReport = ElementCheckReport(
        elementType = "Poteau",
        name = family.name,
        geometry = "${Checks.format(sectionWidthCm)}x${Checks.format(sectionDepthCm)} cm ; lcl = " +
            "${Checks.format(transverse.clearHeightMeters, 2)} m",
        checks = checks
    )
}

/** Synthese du chapitre 10.1 (poteaux). */
data class ColumnDesignSet(
    val columns: List<ColumnDesignResult>,
    val chapter: ChapterReport
) {
    val status: CheckStatus get() = CheckStatus.worst(columns.map { it.status })
    val failedCount: Int get() = columns.count { it.status == CheckStatus.FAILED }
    val warningCount: Int get() = columns.count { it.status == CheckStatus.WARNING }
}

/**
 * Moteur de ferraillage des poteaux (chapitre 10.1) : extraction des couples critiques
 * depuis ETABS, flexion composee BAEL 91, pourcentages RPA 2024 § 7.4.2.1, zones nodales,
 * cadres et verification des contraintes tangentes § 7.4.2.2 et § 7.4.3.2.
 */
class ColumnDesignEngine(
    private val solver: BaelRectangularSolver = BaelRectangularSolver(),
    private val optimizer: RebarOptimizer = RebarOptimizer(),
    private val settings: FrameDesignSettings = FrameDesignSettings()
) {

    fun design(joint: StructuralJoint, results: EtabsResults): ColumnDesignSet =
        design(
            columns = joint.sectionCatalog.columns,
            results = results,
            materials = joint.materials,
            zone = SeismicZone.fromCode(joint.seismicParams.zoneCode)
        )

    fun design(
        columns: List<ColumnFamily>,
        results: EtabsResults,
        materials: MaterialConfig,
        zone: SeismicZone
    ): ColumnDesignSet {
        val designs = columns.map { designColumn(it, results, materials, zone) }
        val checks = mutableListOf<DesignCheck>()
        checks += Checks.boolean(
            code = "PP10.0_FAMILLES",
            label = "Familles de poteaux traitées",
            reference = "RPA 2024 § 7.4.2",
            condition = columns.isNotEmpty(),
            required = "Au moins une famille",
            provided = "${columns.size} famille(s)",
            failureStatus = CheckStatus.FAILED,
            message = "Aucune famille de poteaux à ferrailler : vérifier les affectations ETABS."
        )
        checks += Checks.boolean(
            code = "PP10.0_EFFORTS",
            label = "Efforts de poteaux importés",
            reference = "ETABS - Column Forces",
            condition = designs.any { it.criticalCases.isNotEmpty() },
            required = "Au moins un couple critique",
            provided = "${designs.sumOf { it.criticalCases.size }} couple(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucune table Column Forces exploitable : le ferraillage minimal RPA est appliqué."
        )
        checks += Checks.boolean(
            code = "PP10.0_CONFORMITE",
            label = "Conformité du ferraillage des poteaux",
            reference = "BAEL 91 + RPA 2024 § 7.4",
            condition = designs.none { it.status == CheckStatus.FAILED },
            required = "Aucune famille en défaut",
            provided = "${designs.count { it.status == CheckStatus.FAILED }} famille(s) en défaut",
            failureStatus = CheckStatus.FAILED,
            message = "Au moins une famille de poteaux ne satisfait pas les vérifications de ferraillage."
        )

        val summary = "${designs.size} famille(s) de poteaux ferraillée(s) : " +
            "${designs.count { it.status == CheckStatus.OK }} conforme(s), " +
            "${designs.count { it.status == CheckStatus.WARNING }} à vérifier, " +
            "${designs.count { it.status == CheckStatus.FAILED }} non conforme(s)."
        val lines = designs.flatMap { design ->
            val layout = design.layout
            buildList {
                add(
                    "Poteau ${design.family.name} (${Checks.format(design.sectionWidthCm)}x" +
                        "${Checks.format(design.sectionDepthCm)} cm) : As,calcul = " +
                        "${Checks.format(design.requiredAsCm2)} cm², As,min RPA = " +
                        "${Checks.format(design.minimumAsCm2)} cm², As,adopté = " +
                        "${Checks.format(design.adoptedAsCm2)} cm² (${layout?.label ?: "non disponible"})."
                )
                design.criticalCases.maxByOrNull { it.bael.requiredSteelCm2 }?.let { case ->
                    add(
                        "  Couple dimensionnant : ${case.label} sous ${case.combo} (${case.category.name}), " +
                            "N = ${Checks.format(case.axialKN)} kN, ${case.axis.shortLabel} = " +
                            "${Checks.format(case.momentKNm)} kN.m, état = ${case.bael.state.label}, " +
                            "y0 = ${Checks.format(case.bael.neutralAxisMeters * 100.0)} cm."
                    )
                }
                val transverse = design.transverse
                add(
                    "  Zone nodale Lcr = ${Checks.format(transverse.nodalZoneLengthCm)} cm ; recouvrement " +
                        "Lrec = ${Checks.format(transverse.lapLengthCm)} cm ; cadres ${transverse.stirrupLabel} " +
                        "tous les ${Checks.format(transverse.nodalSpacingCm)} cm en zone nodale et " +
                        "${Checks.format(transverse.currentSpacingCm)} cm en zone courante."
                )
                add(
                    "  Effort normal : Nd = ${Checks.format(design.axialCompressionKN)} kN, " +
                        "ν = ${Checks.format(design.normalizedAxial, 3)} ≤ " +
                        "${Checks.format(design.normalizedAxialLimit, 2)}."
                )
                transverse.shearChecks.forEach { shear ->
                    add(
                        "  Cisaillement ${shear.axis.label} : Vu = ${Checks.format(shear.shearKN)} kN, " +
                            "tau_bu = ${Checks.format(shear.tauMPa, 2)} MPa ≤ " +
                            "${Checks.format(shear.tauLimitMPa, 2)} MPa (${shear.status.label})."
                    )
                }
            }
        }

        return ColumnDesignSet(
            columns = designs,
            chapter = ChapterReport(
                number = 10,
                title = "10.1 Ferraillage des poteaux (flexion composée)",
                summary = summary,
                checks = checks,
                elements = designs.map { it.toElementReport() },
                lines = lines
            )
        )
    }

    private fun designColumn(
        family: ColumnFamily,
        results: EtabsResults,
        materials: MaterialConfig,
        zone: SeismicZone
    ): ColumnDesignResult {
        val notes = mutableListOf<String>()
        val isCircular = family.shape == ColumnShape.CIRCULAR
        val (widthCm, depthCm) = if (isCircular) {
            val diameter = family.effectiveDiameterCm
            val side = diameter * sqrt(PI) / 2.0
            notes += "Poteau circulaire : section carrée équivalente de ${Checks.format(side)} cm de côté " +
                "(même aire que le cercle de diamètre ${Checks.format(diameter)} cm)."
            side to side
        } else {
            family.b1Cm to family.h1Cm
        }

        val ids = family.elementIds.toSet()
        val rows = results.columnForces.filter { it.element in ids }
        val cases = extractCriticalCases(rows, widthCm, depthCm, materials)
        val governing = cases.maxByOrNull { it.bael.requiredSteelCm2 }
        val calculated = governing?.bael?.requiredSteelCm2 ?: 0.0

        val zoneHigh = isHighZone(zone)
        val minRatio = columnMinimumRatio(zone)
        if (zone == SeismicZone.ZONE_0) {
            notes += "Zone sismique 0 : aucune exigence RPA de pourcentage minimal ; " +
                "0,50 % est retenu par cohérence avec le ferraillage BAEL."
        }
        val concreteAreaCm2 = if (isCircular) PI * family.effectiveDiameterCm * family.effectiveDiameterCm / 4.0 else widthCm * depthCm
        val minimumAs = minRatio * concreteAreaCm2
        val maximumAs = settings.columnMaxSteelRatio * concreteAreaCm2
        val maximumLapAs = 0.08 * concreteAreaCm2
        val requiredAs = max(calculated, minimumAs)
        val spacingLimit = if (zoneHigh) 15.0 else 20.0

        val axialCase = cases.filter { it.category == ComboCategory.ELA }.maxByOrNull { it.axialKN }
            ?: cases.maxByOrNull { it.axialKN }
        val axialCompression = axialCase?.axialKN?.coerceAtLeast(0.0) ?: 0.0
        val normalizedAxial = if (concreteAreaCm2 > 0.0) {
            10.0 * axialCompression / (concreteAreaCm2 * materials.fc28MPa)
        } else {
            0.0
        }

        val layout = optimizer.selectColumnLayout(
            bCm = widthCm,
            hCm = depthCm,
            steelCoverCm = settings.steelCoverCm,
            requiredAsCm2 = requiredAs,
            maximumAsCm2 = maximumAs,
            spacingLimitCm = spacingLimit
        )
        val adopted = layout?.areaCm2 ?: 0.0
        if (layout != null && adopted > maximumAs + 1e-6) {
            notes += "La section d'acier adoptée (${Checks.format(adopted)} cm²) dépasse 4 % de la " +
                "section (${Checks.format(maximumAs)} cm²) : vérifier le ferraillage ou les efforts."
        }
        if (layout == null) {
            notes += "Aucune disposition de barres n'a pu être générée pour la section."
        }

        val transverse = designTransverse(
            family = family,
            widthCm = widthCm,
            depthCm = depthCm,
            rows = rows,
            layout = layout,
            materials = materials,
            zone = zone
        )
        val checks = buildChecks(
            layout = layout,
            adopted = adopted,
            required = requiredAs,
            minimum = minimumAs,
            maximum = maximumAs,
            maximumLap = maximumLapAs,
            spacingLimit = spacingLimit,
            normalizedAxial = normalizedAxial,
            wallBoundary = family.isWallBoundary,
            transverse = transverse,
            hasForces = rows.isNotEmpty()
        )

        if (rows.isEmpty()) {
            notes += "Aucun effort ETABS pour la famille : ferraillage minimal RPA appliqué."
        }

        return ColumnDesignResult(
            family = family,
            sectionWidthCm = widthCm,
            sectionDepthCm = depthCm,
            effectiveDepthCm = depthCm - settings.steelCoverCm,
            criticalCases = cases,
            governingCase = governing,
            requiredAsCm2 = requiredAs,
            minimumAsCm2 = minimumAs,
            maximumAsCm2 = maximumAs,
            layout = layout,
            adoptedAsCm2 = adopted,
            maximumLapAsCm2 = maximumLapAs,
            axialCompressionKN = axialCompression,
            normalizedAxial = normalizedAxial,
            normalizedAxialLimit = AXIAL_COMPRESSION_LIMIT,
            transverse = transverse,
            checks = checks,
            notes = notes
        )
    }

    private fun extractCriticalCases(
        rows: List<ColumnForceRow>,
        widthCm: Double,
        depthCm: Double,
        materials: MaterialConfig
    ): List<ColumnCriticalCase> {
        if (rows.isEmpty()) return emptyList()
        val cases = mutableListOf<ColumnCriticalCase>()

        rows.groupBy { it.category }
            .filterKeys { it != ComboCategory.OTHER }
            .forEach { (category, group) ->
                if (group.isEmpty()) return@forEach
                val compressionRow = group.minBy { it.pKN }
                val tensionRow = group.maxBy { it.pKN }
                val maxM2Row = group.maxBy { abs(it.m2KNm) }
                val maxM3Row = group.maxBy { abs(it.m3KNm) }
                val situation = if (category == ComboCategory.ELA) {
                    BaelSituation.ACCIDENTAL
                } else {
                    BaelSituation.DURABLE
                }

                fun addCase(
                    label: String,
                    row: ColumnForceRow,
                    axis: FrameBendingAxis,
                    moment: Double
                ) {
                    val designWidth = if (axis == FrameBendingAxis.MAJOR_3_3) widthCm else depthCm
                    val designDepth = if (axis == FrameBendingAxis.MAJOR_3_3) depthCm else widthCm
                    val section = BaelSectionInput(
                        bCm = designWidth,
                        hCm = designDepth,
                        tensionCoverCm = settings.steelCoverCm,
                        compressionCoverCm = settings.steelCoverCm,
                        nuKN = -row.pKN,
                        muGKNm = moment,
                        situation = situation
                    )
                    cases += ColumnCriticalCase(
                        label = label,
                        combo = row.loadCase,
                        category = category,
                        axis = axis,
                        axialKN = -row.pKN,
                        momentKNm = moment,
                        designWidthCm = designWidth,
                        designDepthCm = designDepth,
                        bael = solver.solve(section, materials)
                    )
                }

                FrameBendingAxis.entries.forEach { axis ->
                    addCase(
                        label = "Compression maximale",
                        row = compressionRow,
                        axis = axis,
                        moment = if (axis == FrameBendingAxis.MAJOR_3_3) compressionRow.m3KNm else compressionRow.m2KNm
                    )
                    addCase(
                        label = "Traction maximale / compression minimale",
                        row = tensionRow,
                        axis = axis,
                        moment = if (axis == FrameBendingAxis.MAJOR_3_3) tensionRow.m3KNm else tensionRow.m2KNm
                    )
                }
                addCase(
                    label = "Moment M2 maximal",
                    row = maxM2Row,
                    axis = FrameBendingAxis.MINOR_2_2,
                    moment = maxM2Row.m2KNm
                )
                addCase(
                    label = "Moment M3 maximal",
                    row = maxM3Row,
                    axis = FrameBendingAxis.MAJOR_3_3,
                    moment = maxM3Row.m3KNm
                )
            }

        return cases.sortedByDescending { it.bael.requiredSteelCm2 }
    }

    private fun designTransverse(
        family: ColumnFamily,
        widthCm: Double,
        depthCm: Double,
        rows: List<ColumnForceRow>,
        layout: ColumnRebarLayout?,
        materials: MaterialConfig,
        zone: SeismicZone
    ): ColumnTransverseDesign {
        val designWidthCm = widthCm.coerceAtLeast(1.0)
        val designDepthCm = depthCm.coerceAtLeast(1.0)
        val lclMeters = family.clearHeightLclMeters
        val lclCm = lclMeters * 100.0
        val highZone = isHighZone(zone)
        val maximumDiameterCm = (layout?.maxDiameterMm ?: settings.longitudinalMinDiameterMm) / 10.0
        val minimumDiameterCm = (layout?.minDiameterMm ?: settings.longitudinalMinDiameterMm) / 10.0

        val largestDimensionCm = maxOf(designWidthCm, designDepthCm)
        val nodalZoneLength = when {
            lclMeters <= 0.0 -> maxOf(largestDimensionCm, 60.0)
            lclCm / largestDimensionCm < 3.0 -> lclCm
            else -> maxOf(1.5 * largestDimensionCm, lclCm / 6.0, 60.0)
        }
        val lapFactor = when (zone) {
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 60.0
            SeismicZone.ZONE_0 -> 40.0
            else -> 50.0
        }
        val lapLength = lapFactor * maximumDiameterCm

        val coreWidthCm = (min(designWidthCm, designDepthCm) - 2.0 * settings.concreteCoverCm)
            .coerceAtLeast(1.0)
        val nodalSpacingLimit = if (highZone) {
            minOf(coreWidthCm / 3.0, 10.0, 60.0 * minimumDiameterCm)
        } else {
            minOf(10.0 * minimumDiameterCm, 12.5)
        }
        val currentSpacingLimit = if (highZone) {
            minOf(designWidthCm / 2.0, designDepthCm / 2.0, 10.0 * minimumDiameterCm)
        } else {
            15.0 * minimumDiameterCm
        }
        val nodalSpacing = floor(nodalSpacingLimit).coerceAtLeast(5.0)
        val currentSpacing = floor(currentSpacingLimit).coerceAtLeast(5.0)

        val effectiveForce = if (lclMeters > 0.0) 0.7 * lclCm else 0.0

        fun confinementDemand(sectionCm: Double, shearKN: Double): ColumnConfinementDemand {
            val slenderness = if (sectionCm > 0.0) effectiveForce / sectionCm else 0.0
            val rho = when {
                slenderness >= 5.0 -> 2.50
                slenderness <= 3.0 -> 3.75
                else -> 3.75 - (slenderness - 3.0) * 0.625
            }
            val ratio = when {
                slenderness >= 5.0 -> 0.003
                slenderness <= 3.0 -> 0.008
                else -> 0.008 - (slenderness - 3.0) * 0.0025
            }
            fun requiredAt(spacingCm: Double): Double = maxOf(
                rho * shearKN * spacingCm / (sectionCm * materials.feMPa) * 10.0,
                ratio * spacingCm * sectionCm
            )
            return ColumnConfinementDemand(
                slenderness = slenderness,
                rhoA = rho,
                minimumRatio = ratio,
                requiredAtNodalCm2 = requiredAt(nodalSpacing),
                requiredAtCurrentCm2 = requiredAt(currentSpacing)
            )
        }

        val demands = listOf(
            confinementDemand(designWidthCm, rows.maxOfOrNull { abs(it.v3KN) } ?: 0.0),
            confinementDemand(designDepthCm, rows.maxOfOrNull { abs(it.v2KN) } ?: 0.0)
        )
        val governing = demands.maxBy { max(it.requiredAtNodalCm2, it.requiredAtCurrentCm2) }
        val slenderness = governing.slenderness
        val rhoA = governing.rhoA
        val minimumTransverseRatio = governing.minimumRatio
        val requiredAt = max(governing.requiredAtNodalCm2, governing.requiredAtCurrentCm2)

        val stirrupOptions = listOf(
            2 to 8, 2 to 10, 4 to 8, 2 to 12, 4 to 10, 2 to 14, 4 to 12, 4 to 14,
            6 to 10, 4 to 16, 6 to 12, 4 to 20, 6 to 14, 6 to 16
        )
        val chosen = stirrupOptions
            .map { (legs, diameter) -> RebarBar(legs, diameter) }
            .firstOrNull { it.areaCm2 + 1e-9 >= requiredAt }
            ?: RebarBar(4, 12)

        val shearChecks = listOf(
            shearCheck(
                axis = FrameBendingAxis.MAJOR_3_3,
                shear = rows.maxOfOrNull { abs(it.v2KN) } ?: 0.0,
                widthCm = designWidthCm,
                depthCm = designDepthCm,
                effectiveForceCm = effectiveForce,
                materials = materials
            ),
            shearCheck(
                axis = FrameBendingAxis.MINOR_2_2,
                shear = rows.maxOfOrNull { abs(it.v3KN) } ?: 0.0,
                widthCm = designDepthCm,
                depthCm = designWidthCm,
                effectiveForceCm = effectiveForce,
                materials = materials
            )
        )

        val anchorage = 50.0 * maximumDiameterCm

        return ColumnTransverseDesign(
            clearHeightMeters = lclMeters,
            nodalZoneLengthCm = nodalZoneLength,
            lapLengthCm = lapLength,
            nodalSpacingLimitCm = nodalSpacingLimit,
            currentSpacingLimitCm = currentSpacingLimit,
            nodalSpacingCm = nodalSpacing,
            currentSpacingCm = currentSpacing,
            stirrupLegs = chosen.count,
            stirrupDiameterMm = chosen.diameterMm,
            stirrupAreaCm2 = chosen.areaCm2,
            requiredStirrupAreaCm2 = requiredAt,
            slendernessLambda = slenderness,
            rhoA = rhoA,
            minimumTransverseRatio = minimumTransverseRatio,
            anchorageLengthCm = anchorage,
            shearChecks = shearChecks
        )
    }

    private fun shearCheck(
        axis: FrameBendingAxis,
        shear: Double,
        widthCm: Double,
        depthCm: Double,
        effectiveForceCm: Double,
        materials: MaterialConfig
    ): ColumnShearCheck {
        val effectiveDepth = (depthCm - settings.steelCoverCm).coerceAtLeast(1.0)
        val slenderness = if (widthCm > 0.0) effectiveForceCm / widthCm else 0.0
        val rhoD = if (slenderness >= 5.0) 0.075 else 0.040
        val tau = 10.0 * shear / (widthCm * effectiveDepth)
        val tauLimit = rhoD * materials.fc28MPa
        val status = when {
            tau > tauLimit + 1e-9 -> CheckStatus.FAILED
            tau > 0.9 * tauLimit -> CheckStatus.WARNING
            else -> CheckStatus.OK
        }
        return ColumnShearCheck(
            axis = axis,
            shearKN = shear,
            widthCm = widthCm,
            effectiveDepthCm = effectiveDepth,
            slenderness = slenderness,
            tauMPa = tau,
            tauLimitMPa = tauLimit,
            status = status
        )
    }

    private fun buildChecks(
        layout: ColumnRebarLayout?,
        adopted: Double,
        required: Double,
        minimum: Double,
        maximum: Double,
        maximumLap: Double,
        spacingLimit: Double,
        normalizedAxial: Double,
        wallBoundary: Boolean,
        transverse: ColumnTransverseDesign,
        hasForces: Boolean
    ): List<DesignCheck> {
        val checks = mutableListOf<DesignCheck>()

        checks += if (layout == null) {
            Checks.boolean(
                code = "PP10.1_AS",
                label = "Section d'acier longitudinale",
                reference = "BAEL 91 + RPA 2024 § 7.4.2.1",
                condition = false,
                required = "≥ ${Checks.format(required)} cm²",
                provided = "aucune disposition",
                failureStatus = CheckStatus.FAILED,
                message = "Impossible de générer une disposition de barres pour cette section."
            )
        } else {
            Checks.min(
                code = "PP10.1_AS",
                label = "Section d'acier longitudinale calculée",
                reference = "BAEL 91 + RPA 2024 § 7.4.2.1",
                provided = adopted,
                minimum = required,
                unit = "cm²",
                failureMessage = "As,adopté = ${Checks.format(adopted)} cm² < As,requis = " +
                    "${Checks.format(required)} cm²."
            )
        }

        checks += Checks.min(
            code = "PP10.1_AS_MIN",
            label = "Pourcentage minimal RPA d'armatures longitudinales",
            reference = "RPA 2024 § 7.4.2.1",
            provided = adopted,
            minimum = minimum,
            unit = "cm²",
            failureMessage = "As,adopté = ${Checks.format(adopted)} cm² < As,min RPA = " +
                "${Checks.format(minimum)} cm²."
        )
        checks += Checks.max(
            code = "PP10.1_AS_MAX",
            label = "Pourcentage maximal d'armatures longitudinales",
            reference = "RPA 2024 § 7.4.2.1 (4 % en zone courante, 8 % au recouvrement)",
            provided = adopted,
            maximum = maximum,
            unit = "cm²",
            failureMessage = "As,adopté = ${Checks.format(adopted)} cm² > 4 % b h = " +
                "${Checks.format(maximum)} cm²."
        )
        checks += Checks.max(
            code = "PP10.1_AS_RECOUVREMENT_MAX",
            label = "Pourcentage maximal d'armatures au recouvrement",
            reference = "RPA 2024 § 7.4.2.1 (8 % en zone de recouvrement)",
            provided = adopted,
            maximum = maximumLap,
            unit = "cm²",
            failureMessage = "As,adopté = ${Checks.format(adopted)} cm² > 8 % b h = " +
                "${Checks.format(maximumLap)} cm²."
        )
        checks += Checks.min(
            code = "PP10.1_DIAMETRE",
            label = "Diamètre minimal des barres longitudinales",
            reference = "RPA 2024 § 7.4.2.1",
            provided = (layout?.minDiameterMm ?: 0).toDouble(),
            minimum = settings.longitudinalMinDiameterMm.toDouble(),
            unit = "mm",
            decimals = 0,
            failureMessage = "Les barres doivent avoir un diamètre ≥ ${settings.longitudinalMinDiameterMm} mm."
        )
        checks += Checks.max(
            code = "PP10.2_ESPACEMENT_FACE",
            label = "Espacement des barres sur une face",
            reference = if (spacingLimit <= 15.0) "RPA 2024 § 7.4.2.1 (≤ 15 cm, zones IV-VI)" else
                "RPA 2024 § 7.4.2.1 (≤ 20 cm, zones I-III)",
            provided = layout?.let { max(it.spacingLongFaceCm, it.spacingShortFaceCm) } ?: 0.0,
            maximum = spacingLimit,
            unit = "cm",
            failureMessage = "Espacement des barres supérieur à ${Checks.format(spacingLimit)} cm."
        )
        checks += Checks.info(
            code = "PP10.2_ZONE_NODALE",
            label = "Longueur de la zone critique",
            reference = "RPA 2024 § 7.4.2.2, Eq. (7.2)",
            value = "Lcr = max(1,5 hc ; lcl/6 ; 60 cm) = ${Checks.format(transverse.nodalZoneLengthCm)} cm"
        )
        checks += Checks.info(
            code = "PP10.2_RECOUVREMENT",
            label = "Longueur de recouvrement",
            reference = "RPA 2024 § 7.4.2.2",
            value = "Lrec = ${Checks.format(transverse.lapLengthCm)} cm ; ancrage à 90° ≥ " +
                "${Checks.format(transverse.anchorageLengthCm)} cm"
        )
        checks += Checks.min(
            code = "PP10.2_AT",
            label = "Section des armatures transversales",
            reference = "RPA 2024 § 7.4.2.2",
            provided = transverse.stirrupAreaCm2,
            minimum = transverse.requiredStirrupAreaCm2,
            unit = "cm²",
            failureMessage = "At = ${Checks.format(transverse.stirrupAreaCm2)} cm² < At,min = " +
                "${Checks.format(transverse.requiredStirrupAreaCm2)} cm²."
        )
        checks += Checks.max(
            code = "PP10.2_ESPACEMENT_CADRES",
            label = "Espacement des cadres en zone nodale",
            reference = "RPA 2024 § 7.4.2.2",
            provided = transverse.nodalSpacingCm,
            maximum = transverse.nodalSpacingLimitCm,
            unit = "cm",
            failureMessage = "Espacement nodal ${Checks.format(transverse.nodalSpacingCm)} cm > " +
                "${Checks.format(transverse.nodalSpacingLimitCm)} cm."
        )
        checks += Checks.max(
            code = "PP10.2_ESPACEMENT_CADRES_COURANT",
            label = "Espacement des cadres en zone courante",
            reference = "RPA 2024 § 7.4.2.2",
            provided = transverse.currentSpacingCm,
            maximum = transverse.currentSpacingLimitCm,
            unit = "cm",
            failureMessage = "Espacement courant ${Checks.format(transverse.currentSpacingCm)} cm > " +
                "${Checks.format(transverse.currentSpacingLimitCm)} cm."
        )
        transverse.shearChecks.forEach { shear ->
            checks += Checks.max(
                code = "PP10.3_TAU_${shear.axis.shortLabel}",
                label = "Contrainte tangente conventionnelle ${shear.axis.label}",
                reference = "RPA 2024 § 7.4.3.2",
                provided = shear.tauMPa,
                maximum = shear.tauLimitMPa,
                unit = "MPa",
                decimals = 2,
                failureMessage = "tau_bu = ${Checks.format(shear.tauMPa, 2)} MPa > " +
                    "${Checks.format(shear.tauLimitMPa, 2)} MPa."
            )
        }
        if (wallBoundary) {
            checks += Checks.notApplicable(
                code = "PP10.3_NU",
                label = "Effort normal de compression réduit",
                reference = "RPA 2024 § 7.4.3.1",
                message = "Poteau frontière de voile : ν est vérifié avec le voile de contreventement, " +
                    "hors du champ du poteau isolé."
            )
        } else {
            checks += Checks.max(
                code = "PP10.3_NU",
                label = "Effort normal de compression réduit",
                reference = "RPA 2024 § 7.4.3.1, Eq. (7.5) (ν ≤ 0,35)",
                provided = normalizedAxial,
                maximum = AXIAL_COMPRESSION_LIMIT,
                unit = "",
                decimals = 3,
                failureMessage = "ν = ${Checks.format(normalizedAxial, 3)} > 0,35 : risque de rupture fragile " +
                    "du poteau, augmenter la section ou réduire l'effort normal.",
                warningAtRatio = 0.30 / AXIAL_COMPRESSION_LIMIT
            )
        }
        if (!hasForces) {
            checks += Checks.notApplicable(
                code = "PP10.0_EFFORTS",
                label = "Couples critiques de poteau",
                reference = "ETABS - Column Forces",
                message = "Aucun effort importé : seul le ferraillage minimal RPA est vérifié."
            )
        }
        return checks
    }

    private fun isHighZone(zone: SeismicZone): Boolean =
        zone == SeismicZone.ZONE_IV || zone == SeismicZone.ZONE_V || zone == SeismicZone.ZONE_VI

    private fun columnMinimumRatio(zone: SeismicZone): Double = when (zone) {
        SeismicZone.ZONE_I, SeismicZone.ZONE_II -> 0.008
        SeismicZone.ZONE_III -> 0.009
        SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 0.010
        SeismicZone.ZONE_0 -> 0.005
    }
}
