package com.archilab.rpa2024.engine.seismic

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.ResolvedSeismicConfig
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.ModalRatioRow
import com.archilab.rpa2024.parser.StoryShearRow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Resultat de la participation modale (§ 4.3.3 du RPA 2024). */
data class ModalParticipationCheck(
    val modeCount: Int,
    val requiredModesFor90Percent: Int?,
    val sumUxPercent: Double,
    val sumUyPercent: Double,
    val checkASatisfied: Boolean,
    val minimumModesCheckB: Int,
    val lastModePeriodSeconds: Double?,
    val checkBSatisfied: Boolean,
    val fundamentalModeX: ModalRatioRow?,
    val fundamentalModeY: ModalRatioRow?,
    val torsionMode: ModalRatioRow?,
    val status: CheckStatus,
    val message: String
)

/** Comparaison de la force sismique statique equivalente et de la reaction dynamique ETABS. */
data class BaseShearCheck(
    val direction: AxisDirection,
    val periodAnalyticalSeconds: Double?,
    val periodEmpiricalSeconds: Double,
    val periodRetainedSeconds: Double,
    val periodWasCapped: Boolean,
    val lambda: Double,
    val accelerationOverG: Double,
    val spectralBranch: SpectralBranch,
    val seismicWeightKN: Double?,
    val staticBaseShearKN: Double?,
    val dynamicBaseShearKN: Double?,
    val dynamicToStaticRatio: Double?,
    val requiredDynamicBaseShearKN: Double?,
    val scaleFactor: Double?,
    val status: CheckStatus,
    val message: String
) {
    val directionLabel: String get() = if (direction == AxisDirection.X) "Sens X-X" else "Sens Y-Y"
}

/** Verification de l'effort normal reduit d'un poteau (§ 7.4.3, Eq. 7.5). */
data class ColumnReducedNormalForceCheck(
    val familyName: String,
    val sectionLabel: String,
    val element: String,
    val story: String,
    val stationMeters: Double,
    val loadCase: String,
    val ndKN: Double,
    val areaCm2: Double,
    val fc28MPa: Double,
    val nu: Double,
    val nuLimit: Double = 0.35,
    val status: CheckStatus,
    val message: String = ""
)

/** Verification du deplacement relatif inter-etages (§ 4.5.2, § 5.10 et Tableau 5.2). */
data class InterStoryDriftCheck(
    val direction: AxisDirection,
    val story: String,
    val storyHeightMeters: Double,
    val elasticDisplacementMeters: Double,
    val inelasticDisplacementMeters: Double,
    val relativeDriftMeters: Double,
    val nuA: Double,
    val reducedDriftMeters: Double,
    val fragileLimitMeters: Double,
    val ductileLimitMeters: Double,
    val governingLimitMeters: Double,
    val utilization: Double,
    val status: CheckStatus,
    val message: String = ""
)

/** Classement du coefficient de sensibilite P-Delta (§ 5.9, Eq. 5.10). */
enum class PDeltaClassification(val label: String) {
    NEGLIGIBLE("Effets P-Delta négligeables"),
    AMPLIFICATION_REQUIRED("Amplification requise des effets sismiques"),
    UNSTABLE_REDESIGN("Structure potentiellement instable - redimensionnement obligatoire")
}

/** Verification du second ordre a un niveau. */
data class PDeltaStoryCheck(
    val direction: AxisDirection,
    val story: String,
    val cumulativeWeightKN: Double,
    val storyShearKN: Double,
    val storyHeightMeters: Double,
    val driftMeters: Double,
    val theta: Double,
    val classification: PDeltaClassification,
    val amplificationFactor: Double?,
    val status: CheckStatus,
    val message: String
)

/** Options de calcul du moteur sismique. */
data class Rpa2024SeismicEngineOptions(
    val manualSeismicWeightKN: Double? = null,
    val nuAOverride: Double? = null,
    /** null : utilise l'option `useMeanNuA` de SeismicConfig. */
    val useMeanNuA: Boolean? = null,
    val includePenthouse: Boolean = true,
    val driftLimitFragile: Double = 0.0050,
    val driftLimitDuctile: Double = 0.0075,
    val pDeltaNegligibleLimit: Double = 0.10,
    val pDeltaUnstableLimit: Double = 0.20
)

/** Synthese complete du chapitre 9 de la note de calcul. */
data class Rpa2024SeismicAssessment(
    val resolved: ResolvedSeismicConfig,
    val spectrumGenerator: SpectrumGenerator?,
    val modalParticipation: ModalParticipationCheck,
    val baseShear: List<BaseShearCheck>,
    val columnReducedNormalForce: List<ColumnReducedNormalForceCheck>,
    val driftX: List<InterStoryDriftCheck>,
    val driftY: List<InterStoryDriftCheck>,
    val pDeltaX: List<PDeltaStoryCheck>,
    val pDeltaY: List<PDeltaStoryCheck>,
    val checks: List<DesignCheck>,
    val warnings: List<String> = emptyList()
) {
    val driftChecks: List<InterStoryDriftCheck> get() = driftX + driftY
    val pDeltaChecks: List<PDeltaStoryCheck> get() = pDeltaX + pDeltaY

    val overallStatus: CheckStatus
        get() = CheckStatus.worst(
            checks.map { it.status } +
                columnReducedNormalForce.map { it.status } +
                driftChecks.map { it.status } +
                pDeltaChecks.map { it.status }
        )

    /** Rapport de chapitre 9 directement injectable dans la synthese du joint. */
    fun toChapterReport(): ChapterReport {
        val elements = mutableListOf<ElementCheckReport>()

        columnReducedNormalForce.forEach { column ->
            elements += ElementCheckReport(
                elementType = "Poteau",
                name = "${column.element} - ${column.story}",
                geometry = "${column.sectionLabel}, Bc=${Checks.format(column.areaCm2, 0)} cm²",
                checks = listOf(columnCheck(column))
            )
        }
        driftChecks.forEach { drift ->
            elements += ElementCheckReport(
                elementType = if (drift.direction == AxisDirection.X) "Déplacement X" else "Déplacement Y",
                name = drift.story,
                geometry = "hk=${Checks.format(drift.storyHeightMeters)} m",
                checks = listOf(driftCheck(drift))
            )
        }
        pDeltaChecks.forEach { pDelta ->
            elements += ElementCheckReport(
                elementType = if (pDelta.direction == AxisDirection.X) "P-Delta X" else "P-Delta Y",
                name = pDelta.story,
                geometry = "hk=${Checks.format(pDelta.storyHeightMeters)} m",
                checks = listOf(pDeltaCheck(pDelta))
            )
        }

        val lines = mutableListOf<String>()
        lines += "Participation modale : ${modalParticipation.message}"
        baseShear.forEach { shear ->
            lines += "${shear.directionLabel} : T0 = ${Checks.format(shear.periodRetainedSeconds, 3)} s, " +
                "Sad/g = ${Checks.format(shear.accelerationOverG, 4)}, λ = ${Checks.format(shear.lambda, 2)}, " +
                "V_MSE = ${shear.staticBaseShearKN?.let { Checks.format(it, 2) } ?: "-"} kN, " +
                "V_MDS = ${shear.dynamicBaseShearKN?.let { Checks.format(it, 2) } ?: "-"} kN."
        }
        if (driftChecks.isNotEmpty()) {
            val maxDrift = driftChecks.maxBy { it.utilization }
            lines += "Déplacement inter-étages le plus défavorable : ${maxDrift.story} " +
                "(νA.|Δ| = ${Checks.format(maxDrift.reducedDriftMeters * 1000.0, 2)} mm pour " +
                "${Checks.format(maxDrift.governingLimitMeters * 1000.0, 2)} mm admissibles)."
        }
        if (pDeltaChecks.isNotEmpty()) {
            val maxTheta = pDeltaChecks.maxBy { it.theta }
            lines += "Effet P-Delta le plus défavorable : ${maxTheta.story}, θ = ${Checks.format(maxTheta.theta, 3)}."
        }

        return ChapterReport(
            number = 9,
            title = "Analyse sismique globale RPA 2024",
            summary = "Participation modale, force sismique statique équivalente comparée à la réaction dynamique, " +
                "effort normal réduit des poteaux, déplacements inter-étages et effet du second ordre.",
            checks = checks,
            elements = elements,
            lines = lines
        )
    }

    private fun columnCheck(column: ColumnReducedNormalForceCheck): DesignCheck = if (column.status == CheckStatus.NOT_APPLICABLE) {
        Checks.notApplicable(
            code = "NU_${column.familyName}",
            label = "Effort normal réduit ${column.familyName}",
            reference = "RPA 2024 § 7.4.3, Éq. 7.5",
            message = column.message
        )
    } else {
        Checks.max(
            code = "NU_${column.familyName}",
            label = "Effort normal réduit ${column.familyName}",
            reference = "RPA 2024 § 7.4.3, Éq. 7.5",
            provided = column.nu,
            maximum = column.nuLimit,
            unit = "",
            decimals = 3,
            failureMessage = column.message
        )
    }

    private fun driftCheck(drift: InterStoryDriftCheck): DesignCheck = if (drift.status == CheckStatus.NOT_APPLICABLE) {
        Checks.notApplicable(
            code = "DRIFT_${drift.direction.name}_${drift.story}",
            label = "Déplacement inter-étages ${drift.story}",
            reference = "RPA 2024 § 5.10",
            message = drift.message
        )
    } else {
        Checks.max(
            code = "DRIFT_${drift.direction.name}_${drift.story}",
            label = "Déplacement inter-étages ${drift.story}",
            reference = "RPA 2024 § 5.10, Éq. 5.12/5.13",
            provided = drift.reducedDriftMeters,
            maximum = drift.governingLimitMeters,
            unit = "m",
            decimals = 4,
            failureMessage = drift.message
        )
    }

    private fun pDeltaCheck(pDelta: PDeltaStoryCheck): DesignCheck = if (pDelta.status == CheckStatus.NOT_APPLICABLE) {
        Checks.notApplicable(
            code = "PDELTA_${pDelta.direction.name}_${pDelta.story}",
            label = "Effet P-Delta ${pDelta.story}",
            reference = "RPA 2024 § 5.9, Éq. 5.10",
            message = pDelta.message
        )
    } else {
        DesignCheck(
            code = "PDELTA_${pDelta.direction.name}_${pDelta.story}",
            label = "Effet P-Delta ${pDelta.story}",
            reference = "RPA 2024 § 5.9, Éq. 5.10",
            status = pDelta.status,
            required = "θ ≤ 0,10",
            provided = "θ = ${Checks.format(pDelta.theta, 3)}",
            message = pDelta.message
        )
    }
}

/**
 * Moteur de verification sismique globale RPA 2024 (DTR BC 2.48), chapitre 9 de la note :
 * - spectres et force sismique totale MSE vs MDS (§ 4.2.3, § 4.2.4, § 4.3.5) ;
 * - participation modale (§ 4.3.3) ;
 * - effort normal reduit des poteaux (§ 7.4.3) ;
 * - deplacements inter-etages et limitation des dommages (§ 4.5.2, § 5.10) ;
 * - justification du second ordre P-Delta (§ 5.9).
 *
 * Toutes les listes (modes, niveaux, poteaux) sont construites dynamiquement depuis
 * le [StructuralJoint] et les tableaux ETABS : aucun niveau ni poteau n'est code en dur.
 */
class Rpa2024SeismicEngine {

    fun assess(
        joint: StructuralJoint,
        results: EtabsResults,
        config: SeismicConfig = joint.seismicParams,
        options: Rpa2024SeismicEngineOptions = Rpa2024SeismicEngineOptions()
    ): Rpa2024SeismicAssessment {
        val resolved = Rpa2024NormativeDb.resolveSeismic(config, joint)
        val warnings = resolved.warnings.toMutableList()
        val modal = evaluateModalParticipation(results.modalRatios, joint.storySystem.numStoriesN)

        if (resolved.zone == SeismicZone.ZONE_0 || resolved.spectralParams == null) {
            if (resolved.zone == SeismicZone.ZONE_0) {
                warnings += "Zone 0 : aucune vérification sismique de calcul n'est applicable."
            }
            val checks = listOf(
                Checks.notApplicable(
                    code = "ANALYSE_SISMIQUE",
                    label = "Analyse sismique globale",
                    reference = "RPA 2024",
                    message = "Aucune action sismique tabulée pour cette configuration."
                )
            )
            return Rpa2024SeismicAssessment(
                resolved = resolved,
                spectrumGenerator = null,
                modalParticipation = modal,
                baseShear = emptyList(),
                columnReducedNormalForce = emptyList(),
                driftX = emptyList(),
                driftY = emptyList(),
                pDeltaX = emptyList(),
                pDeltaY = emptyList(),
                checks = checks,
                warnings = warnings
            )
        }

        val spectrum = SpectrumGenerator.from(resolved)
        val baseShear = listOf(AxisDirection.X, AxisDirection.Y).map { direction ->
            baseShearCheck(direction, joint, resolved, spectrum, results, config, options, warnings)
        }
        val columns = columnChecks(joint, results, warnings)
        val driftX = driftChecks(AxisDirection.X, joint, resolved, results, config, options, warnings)
        val driftY = driftChecks(AxisDirection.Y, joint, resolved, results, config, options, warnings)
        val pDeltaX = pDeltaChecks(AxisDirection.X, joint, results, driftX, options, warnings)
        val pDeltaY = pDeltaChecks(AxisDirection.Y, joint, results, driftY, options, warnings)

        val checks = buildList {
            add(modalCheck(modal))
            baseShear.forEach { add(baseShearSummaryCheck(it)) }
            add(columnSummaryCheck(columns))
            add(driftSummaryCheck("X", driftX))
            add(driftSummaryCheck("Y", driftY))
            add(pDeltaSummaryCheck("X", pDeltaX, options))
            add(pDeltaSummaryCheck("Y", pDeltaY, options))
        }

        return Rpa2024SeismicAssessment(
            resolved = resolved,
            spectrumGenerator = spectrum,
            modalParticipation = modal,
            baseShear = baseShear,
            columnReducedNormalForce = columns,
            driftX = driftX,
            driftY = driftY,
            pDeltaX = pDeltaX,
            pDeltaY = pDeltaY,
            checks = checks,
            warnings = warnings
        )
    }

    /**
     * Participation modale (§ 4.3.3) :
     * a) nombre minimal de modes k >= 3 tel que Som(UX) >= 90 % et Som(UY) >= 90 % ;
     * b) sinon k >= 3.sqrt(N) et T_k <= 0,20 s.
     */
    fun evaluateModalParticipation(rows: List<ModalRatioRow>, storyCount: Int): ModalParticipationCheck {
        val minimumModesB = ceil(3.0 * sqrt(max(1, storyCount).toDouble())).toInt()
        if (rows.isEmpty()) {
            return ModalParticipationCheck(
                modeCount = 0,
                requiredModesFor90Percent = null,
                sumUxPercent = 0.0,
                sumUyPercent = 0.0,
                checkASatisfied = false,
                minimumModesCheckB = minimumModesB,
                lastModePeriodSeconds = null,
                checkBSatisfied = false,
                fundamentalModeX = null,
                fundamentalModeY = null,
                torsionMode = null,
                status = CheckStatus.NOT_APPLICABLE,
                message = "Tableau de participation modale absent : vérification non automatisée."
            )
        }

        val ordered = rows.sortedBy { it.mode }
        val declaredSums = ordered.any { it.sumUxPercent > 0.0 || it.sumUyPercent > 0.0 }
        val individualSum = ordered.sumOf { it.uxPercent } + ordered.sumOf { it.uyPercent }
        val useDeclared = individualSum < 1e-9 && declaredSums

        val cumulativeUx = DoubleArray(ordered.size)
        val cumulativeUy = DoubleArray(ordered.size)
        var runningUx = 0.0
        var runningUy = 0.0
        ordered.forEachIndexed { index, row ->
            runningUx += row.uxPercent
            runningUy += row.uyPercent
            cumulativeUx[index] = if (useDeclared) row.sumUxPercent else runningUx
            cumulativeUy[index] = if (useDeclared) row.sumUyPercent else runningUy
        }

        val index90 = (2 until ordered.size).firstOrNull { index ->
            cumulativeUx[index] >= 90.0 - 1e-9 && cumulativeUy[index] >= 90.0 - 1e-9
        }
        val checkA = index90 != null
        val lastPeriod = ordered.last().periodSeconds
        val checkB = ordered.size >= minimumModesB && lastPeriod <= 0.20 + 1e-9 && ordered.size >= 3

        val fundamentalX = ordered.maxByOrNull { it.uxPercent }?.takeIf { it.uxPercent > 0.0 }
        val fundamentalY = ordered.maxByOrNull { it.uyPercent }?.takeIf { it.uyPercent > 0.0 }
        val torsion = ordered.maxByOrNull { it.rzPercent }?.takeIf { it.rzPercent > 0.0 }

        val sumUx = index90?.let { cumulativeUx[it] } ?: cumulativeUx.last()
        val sumUy = index90?.let { cumulativeUy[it] } ?: cumulativeUy.last()

        val status = if (checkA || checkB) CheckStatus.OK else CheckStatus.FAILED
        val message = when {
            checkA -> "Participation modale suffisante : ${index90!! + 1} modes atteignent " +
                "${Checks.format(sumUx, 1)} % en X et ${Checks.format(sumUy, 1)} % en Y (§ 4.3.3.a)."
            checkB -> "Critère § 4.3.3.b satisfait : ${ordered.size} modes ≥ 3√N = $minimumModesB et " +
                "Tk = ${Checks.format(lastPeriod, 3)} s ≤ 0,20 s."
            else -> "Participation modale insuffisante : ${ordered.size} modes, " +
                "ΣUX = ${Checks.format(sumUx, 1)} %, ΣUY = ${Checks.format(sumUy, 1)} %, " +
                "Tk = ${Checks.format(lastPeriod, 3)} s (minimum 3√N = $minimumModesB et Tk ≤ 0,20 s)."
        }

        return ModalParticipationCheck(
            modeCount = ordered.size,
            requiredModesFor90Percent = index90?.plus(1),
            sumUxPercent = sumUx,
            sumUyPercent = sumUy,
            checkASatisfied = checkA,
            minimumModesCheckB = minimumModesB,
            lastModePeriodSeconds = lastPeriod,
            checkBSatisfied = checkB,
            fundamentalModeX = fundamentalX,
            fundamentalModeY = fundamentalY,
            torsionMode = torsion,
            status = status,
            message = message
        )
    }

    private fun baseShearCheck(
        direction: AxisDirection,
        joint: StructuralJoint,
        resolved: ResolvedSeismicConfig,
        spectrum: SpectrumGenerator,
        results: EtabsResults,
        config: SeismicConfig,
        options: Rpa2024SeismicEngineOptions,
        warnings: MutableList<String>
    ): BaseShearCheck {
        val fundamental = fundamentalMode(results.modalRatios, direction)
        val empirical = resolved.empiricalPeriodSeconds
        val analytical = fundamental?.periodSeconds
        val cappedPeriod = 1.30 * empirical
        val retained = when {
            analytical == null -> empirical
            empirical <= 0.0 -> analytical
            else -> min(analytical, cappedPeriod)
        }
        val wasCapped = analytical != null && empirical > 0.0 && analytical > cappedPeriod + 1e-9
        if (wasCapped) {
            warnings += "Période ${if (direction == AxisDirection.X) "Tx" else "Ty"} analytique " +
                "${Checks.format(analytical!!, 3)} s plafonnée à 1,30.Temp = " +
                "${Checks.format(cappedPeriod, 3)} s (§ 4.2.4)."
        }
        val t2 = resolved.spectralParams?.t2 ?: 0.0
        val lambda = config.lambdaOverride ?: if (joint.storySystem.numStoriesN > 2 && t2 > 0.0 &&
            retained <= 2.0 * t2 + 1e-9
        ) {
            0.85
        } else {
            1.0
        }
        val acceleration = spectrum.valueAt(SpectrumKind.HORIZONTAL_DESIGN, retained)
        val branch = spectrum.branchAt(SpectrumKind.HORIZONTAL_DESIGN, retained)
        // Une valeur saisie par l'ingenieur prévaut sur le poids extrait d'ETABS.
        val weight = options.manualSeismicWeightKN ?: results.seismicWeightKN
        val staticShear = weight?.let { lambda * acceleration * it }
        val dynamicShear = results.baseSeismicShearKN(direction)
            ?: fallbackBaseShear(results, direction)
        val required = staticShear?.times(0.80)
        val ratio = if (staticShear != null && staticShear > 1e-9 && dynamicShear != null) {
            dynamicShear / staticShear
        } else {
            null
        }
        val scaleFactor = if (dynamicShear != null && dynamicShear > 1e-9 && required != null &&
            dynamicShear < required - 1e-9
        ) {
            required / dynamicShear
        } else {
            null
        }

        val status = when {
            weight == null || staticShear == null -> CheckStatus.NOT_APPLICABLE
            dynamicShear == null -> CheckStatus.NOT_APPLICABLE
            scaleFactor != null -> CheckStatus.WARNING
            else -> CheckStatus.OK
        }
        val message = when (status) {
            CheckStatus.NOT_APPLICABLE -> when {
                weight == null -> "Poids sismique W indisponible : importer Story Shears (cas WI) ou fournir W manuellement."
                else -> "Réaction dynamique ETABS indisponible : comparaison MSE/MDS non automatisée."
            }
            CheckStatus.WARNING -> "Analyse dynamique à recalibrer : V_MDS = " +
                "${Checks.format(dynamicShear ?: 0.0, 2)} kN < 0,80.V_MSE = " +
                "${Checks.format(required ?: 0.0, 2)} kN. Facteur d'échelle = " +
                "${Checks.format(scaleFactor ?: 0.0, 3)} (§ 4.3.5)."
            else -> "Calage vérifié : V_MDS/V_MSE = ${Checks.format((ratio ?: 0.0) * 100.0, 1)} % " +
                "≥ 80 % (branche ${branch.label})."
        }

        return BaseShearCheck(
            direction = direction,
            periodAnalyticalSeconds = analytical,
            periodEmpiricalSeconds = empirical,
            periodRetainedSeconds = retained,
            periodWasCapped = wasCapped,
            lambda = lambda,
            accelerationOverG = acceleration,
            spectralBranch = branch,
            seismicWeightKN = weight,
            staticBaseShearKN = staticShear,
            dynamicBaseShearKN = dynamicShear,
            dynamicToStaticRatio = ratio,
            requiredDynamicBaseShearKN = required,
            scaleFactor = scaleFactor,
            status = status,
            message = message
        )
    }

    private fun columnChecks(
        joint: StructuralJoint,
        results: EtabsResults,
        warnings: MutableList<String>
    ): List<ColumnReducedNormalForceCheck> {
        val families = joint.sectionCatalog.columns.filter { !it.isWallBoundary }
        val excluded = joint.sectionCatalog.columns.count { it.isWallBoundary }
        if (excluded > 0) {
            warnings += "$excluded famille(s) de poteaux solidaire(s) de voiles exclue(s) du contrôle ν (§ 7.4.3)."
        }
        if (families.isEmpty()) {
            warnings += "Aucun poteau non solidaire de voile : contrôle de l'effort normal réduit non applicable."
            return emptyList()
        }

        val checks = families.map { family ->
            val rows = results.columnForces.filter { row ->
                isSeismicCombo(row.loadCase) && matchesFamily(row.element, family)
            }
            if (rows.isEmpty()) {
                warnings += "Aucun effort normal sismique trouvé pour la famille ${family.name}."
                return@map ColumnReducedNormalForceCheck(
                    familyName = family.name,
                    sectionLabel = family.geometryLabel,
                    element = family.elementIds.firstOrNull() ?: family.name,
                    story = "",
                    stationMeters = 0.0,
                    loadCase = "",
                    ndKN = 0.0,
                    areaCm2 = family.areaCm2,
                    fc28MPa = joint.materials.fc28MPa,
                    nu = 0.0,
                    status = CheckStatus.NOT_APPLICABLE,
                    message = "Aucun effort normal sismique trouvé pour cette famille."
                )
            }
            val governing = rows.minByOrNull { it.pKN }!!
            val nd = max(0.0, -governing.pKN)
            val area = family.areaCm2
            val nu = if (area <= 0.0) 0.0 else nd * 1000.0 / (area * 100.0 * joint.materials.fc28MPa)
            val status = when {
                nd <= 0.0 -> CheckStatus.NOT_APPLICABLE
                nu <= 0.35 + 1e-9 -> CheckStatus.OK
                else -> CheckStatus.FAILED
            }
            val message = when (status) {
                CheckStatus.NOT_APPLICABLE -> "Aucun effort de compression sismique pour cette famille."
                CheckStatus.FAILED -> "Effort normal réduit excessif : ν = ${Checks.format(nu, 3)} > 0,35 " +
                    "(Nd = ${Checks.format(nd, 1)} kN, Bc = ${Checks.format(area, 0)} cm²)."
                else -> "Vérifié : ν = ${Checks.format(nu, 3)} ≤ 0,35."
            }
            ColumnReducedNormalForceCheck(
                familyName = family.name,
                sectionLabel = family.geometryLabel,
                element = governing.element,
                story = governing.story,
                stationMeters = governing.stationMeters,
                loadCase = governing.loadCase,
                ndKN = nd,
                areaCm2 = area,
                fc28MPa = joint.materials.fc28MPa,
                nu = nu,
                status = status,
                message = message
            )
        }
        return checks
    }

    private fun driftChecks(
        direction: AxisDirection,
        joint: StructuralJoint,
        resolved: ResolvedSeismicConfig,
        results: EtabsResults,
        config: SeismicConfig,
        options: Rpa2024SeismicEngineOptions,
        warnings: MutableList<String>
    ): List<InterStoryDriftCheck> {
        val caseName = if (direction == AxisDirection.X) "EX" else "EY"
        val rows = results.diaphragmDisplacements.filter { matchesCase(it.loadCase, caseName) }
        if (rows.isEmpty()) {
            warnings += "Déplacements $caseName absents : vérification des déplacements inter-étages non automatisée."
            return emptyList()
        }
        val byStory = rows.groupBy { normalizeStory(it.story) }
            .mapValues { (_, values) ->
                values.maxOf { row -> abs(if (direction == AxisDirection.X) row.uxMeters else row.uyMeters) }
            }
        val stories = if (options.includePenthouse) {
            joint.storySystem.orderedStories
        } else {
            joint.storySystem.mainStories
        }
        val nuA = options.nuAOverride ?: if (options.useMeanNuA ?: config.useMeanNuA) {
            0.50
        } else {
            resolved.importance.nuA
        }
        val amplification = resolved.behaviorFactorR / resolved.qualityFactor.qf

        var previousInelastic = 0.0
        var missingReported = false
        return stories.map { story ->
            val elastic = byStory[normalizeStory(story.storyName)]
            if (elastic == null) {
                if (!missingReported) {
                    warnings += "Certains niveaux n'ont pas de déplacement $caseName : les lignes correspondantes " +
                        "sont marquées non applicables."
                    missingReported = true
                }
                InterStoryDriftCheck(
                    direction = direction,
                    story = story.storyName,
                    storyHeightMeters = story.heightHk,
                    elasticDisplacementMeters = 0.0,
                    inelasticDisplacementMeters = 0.0,
                    relativeDriftMeters = 0.0,
                    nuA = nuA,
                    reducedDriftMeters = 0.0,
                    fragileLimitMeters = options.driftLimitFragile * story.heightHk,
                    ductileLimitMeters = options.driftLimitDuctile * story.heightHk,
                    governingLimitMeters = min(
                        options.driftLimitFragile * story.heightHk,
                        options.driftLimitDuctile * story.heightHk
                    ),
                    utilization = 0.0,
                    status = CheckStatus.NOT_APPLICABLE,
                    message = "Déplacement $caseName absent pour le niveau ${story.storyName}."
                )
            } else {
                val inelastic = amplification * elastic
                val drift = inelastic - previousInelastic
                previousInelastic = inelastic
                val reduced = nuA * abs(drift)
                val fragileLimit = options.driftLimitFragile * story.heightHk
                val ductileLimit = options.driftLimitDuctile * story.heightHk
                val governing = min(fragileLimit, ductileLimit)
                val utilization = if (governing <= 0.0) 0.0 else reduced / governing
                val status = if (reduced <= governing + 1e-9) CheckStatus.OK else CheckStatus.FAILED
                val message = if (status == CheckStatus.OK) {
                    "Vérifiée : νA.|Δk| = ${Checks.format(reduced * 1000.0, 2)} mm ≤ " +
                        "${Checks.format(governing * 1000.0, 2)} mm " +
                        "(fragile ${Checks.format(fragileLimit * 1000.0, 2)} mm, " +
                        "ductile ${Checks.format(ductileLimit * 1000.0, 2)} mm)."
                } else {
                    "Déplacement excessif : νA.|Δk| = ${Checks.format(reduced * 1000.0, 2)} mm > " +
                        "${Checks.format(governing * 1000.0, 2)} mm."
                }
                InterStoryDriftCheck(
                    direction = direction,
                    story = story.storyName,
                    storyHeightMeters = story.heightHk,
                    elasticDisplacementMeters = elastic,
                    inelasticDisplacementMeters = inelastic,
                    relativeDriftMeters = drift,
                    nuA = nuA,
                    reducedDriftMeters = reduced,
                    fragileLimitMeters = fragileLimit,
                    ductileLimitMeters = ductileLimit,
                    governingLimitMeters = governing,
                    utilization = utilization,
                    status = status,
                    message = message
                )
            }
        }
    }

    private fun pDeltaChecks(
        direction: AxisDirection,
        joint: StructuralJoint,
        results: EtabsResults,
        drifts: List<InterStoryDriftCheck>,
        options: Rpa2024SeismicEngineOptions,
        warnings: MutableList<String>
    ): List<PDeltaStoryCheck> {
        if (drifts.isEmpty()) return emptyList()
        val cumulativeWeights = results.cumulativeWeightByStoryKN()
            .entries.associate { (story, value) -> normalizeStory(story) to value }
        val caseName = if (direction == AxisDirection.X) "EX" else "EY"
        val shearRows = results.storyShears.filter { matchesCase(it.loadCase, caseName) && it.isBottom }
        val shearByStory = shearRows.groupBy { normalizeStory(it.story) }
            .mapValues { (_, values) ->
                values.maxOf { row -> abs(if (direction == AxisDirection.X) row.vxKN else row.vyKN) }
            }

        if (shearRows.isEmpty()) {
            warnings += "Efforts tranchants d'étage $caseName absents : effet P-Delta non automatisé."
        }
        if (cumulativeWeights.isEmpty()) {
            warnings += "Poids cumulés (cas WI) absents : effet P-Delta non automatisé."
        }

        return drifts.map { driftCheck ->
            val story = driftCheck.story
            val weight = cumulativeWeights[normalizeStory(story)]
            val shear = shearByStory[normalizeStory(story)]
            if (driftCheck.status == CheckStatus.NOT_APPLICABLE || weight == null || shear == null ||
                shear <= 1e-9 || driftCheck.storyHeightMeters <= 0.0
            ) {
                PDeltaStoryCheck(
                    direction = direction,
                    story = story,
                    cumulativeWeightKN = weight ?: 0.0,
                    storyShearKN = shear ?: 0.0,
                    storyHeightMeters = driftCheck.storyHeightMeters,
                    driftMeters = abs(driftCheck.relativeDriftMeters),
                    theta = 0.0,
                    classification = PDeltaClassification.NEGLIGIBLE,
                    amplificationFactor = null,
                    status = CheckStatus.NOT_APPLICABLE,
                    message = "Données insuffisantes pour évaluer P-Delta au niveau $story."
                )
            } else {
                val drift = abs(driftCheck.relativeDriftMeters)
                val theta = weight * drift / (shear * driftCheck.storyHeightMeters)
                val classification = when {
                    theta <= options.pDeltaNegligibleLimit + 1e-9 -> PDeltaClassification.NEGLIGIBLE
                    theta <= options.pDeltaUnstableLimit + 1e-9 -> PDeltaClassification.AMPLIFICATION_REQUIRED
                    else -> PDeltaClassification.UNSTABLE_REDESIGN
                }
                val status = when (classification) {
                    PDeltaClassification.NEGLIGIBLE -> CheckStatus.OK
                    PDeltaClassification.AMPLIFICATION_REQUIRED -> CheckStatus.WARNING
                    PDeltaClassification.UNSTABLE_REDESIGN -> CheckStatus.FAILED
                }
                val amplification = if (classification == PDeltaClassification.AMPLIFICATION_REQUIRED && theta < 1.0) {
                    1.0 / (1.0 - theta)
                } else {
                    null
                }
                val message = when (classification) {
                    PDeltaClassification.NEGLIGIBLE ->
                        "Vérifiée : effets P-Delta négligés (θ = ${Checks.format(theta, 3)} ≤ 0,10)."
                    PDeltaClassification.AMPLIFICATION_REQUIRED ->
                        "Amplification requise des effets sismiques par 1/(1-θ) = " +
                            "${Checks.format(amplification ?: 0.0, 3)} (θ = ${Checks.format(theta, 3)})."
                    PDeltaClassification.UNSTABLE_REDESIGN ->
                        "Non vérifiée : structure potentiellement instable (θ = ${Checks.format(theta, 3)} > 0,20), " +
                            "redimensionnement obligatoire."
                }
                PDeltaStoryCheck(
                    direction = direction,
                    story = story,
                    cumulativeWeightKN = weight,
                    storyShearKN = shear,
                    storyHeightMeters = driftCheck.storyHeightMeters,
                    driftMeters = drift,
                    theta = theta,
                    classification = classification,
                    amplificationFactor = amplification,
                    status = status,
                    message = message
                )
            }
        }
    }

    private fun modalCheck(modal: ModalParticipationCheck): DesignCheck = if (modal.status == CheckStatus.NOT_APPLICABLE) {
        Checks.notApplicable(
            code = "PARTICIPATION_MODALE",
            label = "Participation modale",
            reference = "RPA 2024 § 4.3.3",
            message = modal.message
        )
    } else {
        Checks.boolean(
            code = "PARTICIPATION_MODALE",
            label = "Participation modale",
            reference = "RPA 2024 § 4.3.3, Éq. 4.10",
            condition = modal.checkASatisfied || modal.checkBSatisfied,
            required = "ΣUX et ΣUY ≥ 90 % (k ≥ 3) ou k ≥ 3√N et Tk ≤ 0,20 s",
            provided = "${modal.modeCount} modes, ΣUX = ${Checks.format(modal.sumUxPercent, 1)} %, " +
                "ΣUY = ${Checks.format(modal.sumUyPercent, 1)} %",
            message = modal.message
        )
    }

    private fun baseShearSummaryCheck(shear: BaseShearCheck): DesignCheck {
        if (shear.status == CheckStatus.NOT_APPLICABLE) {
            return Checks.notApplicable(
                code = "MSE_MDS_${shear.direction.name}",
                label = "Calage MSE/MDS ${shear.directionLabel}",
                reference = "RPA 2024 § 4.3.5",
                message = shear.message
            )
        }
        val ratioPercent = (shear.dynamicToStaticRatio ?: 0.0) * 100.0
        val condition = ratioPercent >= 80.0 - 1e-6
        return Checks.boolean(
            code = "MSE_MDS_${shear.direction.name}",
            label = "Calage MSE/MDS ${shear.directionLabel}",
            reference = "RPA 2024 § 4.3.5",
            condition = condition,
            required = "V_MDS ≥ 0,80.V_MSE",
            provided = "V_MSE = ${shear.staticBaseShearKN?.let { Checks.format(it, 2) } ?: "-"} kN, " +
                "V_MDS = ${shear.dynamicBaseShearKN?.let { Checks.format(it, 2) } ?: "-"} kN, " +
                "rapport = ${Checks.format(ratioPercent, 1)} %",
            failureStatus = CheckStatus.WARNING,
            message = shear.message
        )
    }

    private fun columnSummaryCheck(columns: List<ColumnReducedNormalForceCheck>): DesignCheck {
        val applicable = columns.filter { it.status != CheckStatus.NOT_APPLICABLE }
        if (applicable.isEmpty()) {
            return Checks.notApplicable(
                code = "NU_POTEAUX",
                label = "Effort normal réduit des poteaux",
                reference = "RPA 2024 § 7.4.3, Éq. 7.5",
                message = "Aucun effort normal de compression sismique exploitable."
            )
        }
        val governing = applicable.maxBy { it.nu }
        return Checks.max(
            code = "NU_POTEAUX",
            label = "Effort normal réduit des poteaux",
            reference = "RPA 2024 § 7.4.3, Éq. 7.5",
            provided = governing.nu,
            maximum = governing.nuLimit,
            unit = "",
            decimals = 3,
            failureMessage = "Effort normal réduit maximal ν = ${Checks.format(governing.nu, 3)} > " +
                "${Checks.format(governing.nuLimit, 2)} pour ${governing.element} (${governing.familyName})."
        )
    }

    private fun driftSummaryCheck(direction: String, checks: List<InterStoryDriftCheck>): DesignCheck {
        val applicable = checks.filter { it.status != CheckStatus.NOT_APPLICABLE }
        if (applicable.isEmpty()) {
            return Checks.notApplicable(
                code = "DEPLACEMENTS_$direction",
                label = "Déplacements inter-étages $direction",
                reference = "RPA 2024 § 5.10",
                message = "Déplacements indisponibles pour la direction $direction."
            )
        }
        val governing = applicable.maxBy { it.utilization }
        return Checks.max(
            code = "DEPLACEMENTS_$direction",
            label = "Déplacements inter-étages $direction",
            reference = "RPA 2024 § 5.10, Éq. 5.12/5.13",
            provided = governing.utilization * 100.0,
            maximum = 100.0,
            unit = "%",
            decimals = 1,
            failureMessage = "Déplacement inter-étages excessif au niveau ${governing.story} : " +
                "νA.|Δ| = ${Checks.format(governing.reducedDriftMeters * 1000.0, 2)} mm > " +
                "${Checks.format(governing.governingLimitMeters * 1000.0, 2)} mm."
        )
    }

    private fun pDeltaSummaryCheck(
        direction: String,
        checks: List<PDeltaStoryCheck>,
        options: Rpa2024SeismicEngineOptions
    ): DesignCheck {
        val applicable = checks.filter { it.status != CheckStatus.NOT_APPLICABLE }
        if (applicable.isEmpty()) {
            return Checks.notApplicable(
                code = "PDELTA_$direction",
                label = "Effet P-Delta $direction",
                reference = "RPA 2024 § 5.9, Éq. 5.10",
                message = "Données insuffisantes pour évaluer l'effet P-Delta en direction $direction."
            )
        }
        val governing = applicable.maxBy { it.theta }
        val status = when {
            governing.theta <= options.pDeltaNegligibleLimit + 1e-9 -> CheckStatus.OK
            governing.theta <= options.pDeltaUnstableLimit + 1e-9 -> CheckStatus.WARNING
            else -> CheckStatus.FAILED
        }
        val required = if (status == CheckStatus.WARNING) {
            "θ ≤ 0,10 ou amplification 1/(1−θ)"
        } else {
            "θ ≤ 0,10"
        }
        return DesignCheck(
            code = "PDELTA_$direction",
            label = "Effet P-Delta $direction",
            reference = "RPA 2024 § 5.9, Éq. 5.10",
            status = status,
            required = required,
            provided = "θmax = ${Checks.format(governing.theta, 3)} au niveau ${governing.story}",
            message = governing.message
        )
    }

    private fun fundamentalMode(rows: List<ModalRatioRow>, direction: AxisDirection): ModalRatioRow? =
        rows.maxByOrNull { if (direction == AxisDirection.X) it.uxPercent else it.uyPercent }
            ?.takeIf { row -> (if (direction == AxisDirection.X) row.uxPercent else row.uyPercent) > 0.0 }

    private fun fallbackBaseShear(results: EtabsResults, direction: AxisDirection): Double? {
        val caseName = if (direction == AxisDirection.X) "EX" else "EY"
        val seismicRows = results.storyShears.filter { matchesCase(it.loadCase, caseName) }
        val bottomRows = seismicRows.filter { it.isBottom }
        val pool = bottomRows.ifEmpty { seismicRows }
        return pool.maxOfOrNull { row ->
            abs(if (direction == AxisDirection.X) row.vxKN else row.vyKN)
        }
    }

    private fun matchesFamily(element: String, family: ColumnFamily): Boolean {
        val identifier = normalizeIdentifier(element)
        val familyIds = family.elementIds.map { normalizeIdentifier(it) }
        if (familyIds.isNotEmpty()) {
            if (familyIds.any { it == identifier }) return true
            if (familyIds.any { identifier.startsWith(it) || it.startsWith(identifier) }) return true
        }
        val familyName = normalizeIdentifier(family.name)
        return identifier == familyName || identifier.contains(familyName) ||
            element.contains(family.name, ignoreCase = true)
    }

    private fun isSeismicCombo(loadCase: String): Boolean =
        ComboCategory.classify(loadCase) == ComboCategory.ELA

    private fun matchesCase(loadCase: String, base: String): Boolean {
        val normalized = loadCase.uppercase().replace(" ", "")
        return normalized == base || normalized.startsWith(base) ||
            normalized.contains("-$base") || normalized.contains("_$base")
    }

    private fun normalizeStory(story: String): String = story.trim().uppercase()

    private fun normalizeIdentifier(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9]"), "")
}
