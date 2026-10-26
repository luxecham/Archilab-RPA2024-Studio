package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.engine.frames.BaelRectangularResult
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.BaelSectionInput
import com.archilab.rpa2024.engine.frames.BaelSituation
import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.parser.ComboCategory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Options de ferraillage et de verification des semelles filantes. */
data class StripFootingDesignOptions(
    val longitudinalDiametersMm: List<Int> = listOf(12, 14, 16, 20, 25, 32),
    val transverseDiametersMm: List<Int> = listOf(8, 10, 12, 14, 16),
    val minimumLongitudinalBars: Int = 4,
    val maximumLongitudinalBars: Int = 40,
    val maximumLongitudinalSpacingMeters: Double = 0.25,
    val minimumTransverseSpacingMeters: Double = 0.10,
    val maximumTransverseSpacingMeters: Double = 0.25,
    val transverseCoverMeters: Double = 0.05,
    val shearLimitFactor: Double = 0.20,
    val absoluteShearLimitMPa: Double = 4.0
) {
    init {
        require(longitudinalDiametersMm.isNotEmpty()) { "Aucun diamètre longitudinal." }
        require(transverseDiametersMm.isNotEmpty()) { "Aucun diamètre transversal." }
        require(minimumLongitudinalBars >= 2) { "Nombre minimal de barres invalide." }
        require(maximumLongitudinalSpacingMeters > 0.0) { "Espacement longitudinal maximal invalide." }
        require(maximumTransverseSpacingMeters >= minimumTransverseSpacingMeters) {
            "Plage d'espacement transversal invalide."
        }
    }
}

/** Nappe d'armatures longitudinales repartie sur la largeur de la semelle. */
data class StripFootingRebarLayout(
    val zone: String,
    val face: String,
    val requiredAreaCm2: Double,
    val barDiameterMm: Int,
    val barCount: Int,
    val providedAreaCm2: Double,
    val spacingMeters: Double,
    val label: String,
    val check: DesignCheck
) {
    val isSatisfied: Boolean get() = check.isSatisfied
    val status: CheckStatus get() = check.status
}

/** Verification de portance rattachee a une famille et a un ressort. */
data class StripFootingSoilCheck(
    val familyName: String,
    val springName: String,
    val result: SoilBearingVerificationResult
)

/** Dimensionnement complet d'une famille de semelle filante. */
data class StripFootingDesign(
    val family: StripFootingFamily,
    val forces: StripFootingForceEnvelope?,
    val spring: LineSpringFamily?,
    val soil: SoilBearingVerificationResult?,
    val situation: BaelSituation,
    val supportDesign: BaelRectangularResult,
    val spanTeeDesign: BaelTResult?,
    val spanRectangularDesign: BaelRectangularResult,
    val adoptedSpanModel: String,
    val supportSteel: StripFootingRebarLayout,
    val spanSteel: StripFootingRebarLayout,
    val transverseSteel: StripFootingRebarLayout,
    val transverseRequiredCm2PerM: Double,
    val shearStressMPa: Double,
    val shearLimitMPa: Double,
    val minimumWidthMeters: Double,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })

    fun toElementReport(): ElementCheckReport = ElementCheckReport(
        elementType = "Semelle filante",
        name = "${family.name} (${family.kind.label})",
        geometry = buildString {
            append(family.sectionLabel)
            append(" - longueur ")
            append(Checks.format(forces?.ultimate?.stationLengthMeters ?: family.footingLengthMeters ?: 0.0, 2))
            append(" m")
            if (soil != null) {
                append(" - σ_eff = ")
                append(Checks.format(soil.effectiveStressKNM2, 2))
                append(" kN/m2")
            }
        },
        checks = checks
    )
}

/** Ensemble des semelles dimensionnees, avec les verifications de sol globales. */
data class StripFootingDesignSet(
    val footings: List<StripFootingDesign>,
    val soilChecks: List<StripFootingSoilCheck>,
    val warnings: List<String> = emptyList()
) {
    val status: CheckStatus
        get() = CheckStatus.worst(
            footings.map { it.status } + soilChecks.map { it.result.status }
        )

    fun toChapterReport(): ChapterReport {
        val soilElements = soilChecks.map { entry ->
            ElementCheckReport(
                elementType = "Sol",
                name = "Portance ${entry.springName} (${entry.familyName})",
                geometry = "Ks = ${Checks.format(entry.result.input.subgradeModulusKNM3, 0)} kN/m3, " +
                    "σ̄ = ${Checks.format(entry.result.allowableStressKNM2, 1)} kN/m2",
                checks = entry.result.checks
            )
        }
        val allElements = soilElements + footings.map { it.toElementReport() }
        val synthesis = Checks.boolean(
            code = "SF13.0_SYNTHESE",
            label = "Synthèse des fondations",
            reference = "DTR BC 2.33.1 / BAEL 91 révisé 99",
            condition = allElements.all { it.status != CheckStatus.FAILED },
            required = "Aucune vérification en échec",
            provided = "${footings.size} semelle(s), ${soilChecks.size} vérification(s) de sol"
        )
        return ChapterReport(
            number = 13,
            title = "13.1 Dimensionnement des semelles filantes",
            summary = buildString {
                append("${footings.size} famille(s) de semelle dimensionnée(s)")
                if (soilChecks.isNotEmpty()) {
                    append(", portance du sol vérifiée pour ${soilChecks.size} ressort(s)")
                }
                append(".")
            },
            checks = listOf(synthesis),
            elements = allElements,
            lines = buildList {
                footings.forEach { footing ->
                    add(
                        "${footing.family.name} : B × h = " +
                            "${Checks.format(footing.family.flangeWidthMeters, 2)} × " +
                            "${Checks.format(footing.family.totalHeightMeters, 2)} m, " +
                            "appui ${footing.supportSteel.label}, travée ${footing.spanSteel.label}, " +
                            "transversal ${footing.transverseSteel.label}."
                    )
                }
                soilChecks.forEach { entry ->
                    add(
                        "${entry.familyName} / ${entry.springName} : " +
                            "σ_eff = ${Checks.format(entry.result.effectiveStressKNM2, 2)} kN/m2 " +
                            "≤ ${Checks.format(entry.result.allowableStressKNM2, 2)} kN/m2."
                    )
                }
                warnings.forEach { add("Attention : $it") }
            }
        )
    }
}

/**
 * Orchestrateur de dimensionnement des semelles filantes :
 * - appui : BaelR sur l'ame (section en Te) ou sur la section complete (rectangulaire) ;
 * - travée : BaelT sur la section en Te et BaelR sur l'ame, enveloppe retenue ;
 * - acier transversal : As/ml = Nu . (B - b0) / (8 . d_table . fsu) ;
 * - cisaillement, non-fragilite, portance et largeur minimale.
 */
class StripFootingDesignOrchestrator(
    private val rectangularSolver: BaelRectangularSolver = BaelRectangularSolver(),
    private val teeSolver: BaelTSectionSolver = BaelTSectionSolver(),
    private val soilVerifier: SoilBearingStressVerifier = SoilBearingStressVerifier()
) {

    fun design(
        model: SafeFoundationModel,
        materials: MaterialConfig = MaterialConfig(),
        options: StripFootingDesignOptions = StripFootingDesignOptions()
    ): StripFootingDesignSet {
        if (model.families.isEmpty()) {
            return StripFootingDesignSet(
                footings = emptyList(),
                soilChecks = emptyList(),
                warnings = model.warnings + "Aucune semelle à dimensionner."
            )
        }

        val warnings = model.warnings.toMutableList()
        val soilChecks = mutableListOf<StripFootingSoilCheck>()
        val footings = model.families.map { family ->
            designFamily(model, family, materials, options, warnings, soilChecks)
        }
        return StripFootingDesignSet(
            footings = footings,
            soilChecks = soilChecks.distinctBy { it.springName to it.familyName },
            warnings = warnings.distinct()
        )
    }

    private fun designFamily(
        model: SafeFoundationModel,
        family: StripFootingFamily,
        materials: MaterialConfig,
        options: StripFootingDesignOptions,
        warnings: MutableList<String>,
        soilChecks: MutableList<StripFootingSoilCheck>
    ): StripFootingDesign {
        val familyMaterials = model.materialFor(family, materials)
        val forces = model.forcesFor(family.name)
        val spring = model.springFor(family)
        val notes = mutableListOf<String>()
        if (forces == null) {
            notes += "Aucun effort lu pour '${family.name}' : dimensionnement au minimum réglementaire."
            warnings += "Semelle '${family.name}' sans efforts SAFE : minimum BAEL appliqué."
        } else {
            notes += forces.notes
        }

        val situation = situationFor(family, forces)
        val coefficients = rectangularSolver.coefficients(familyMaterials, situation)
        notes += "Situation retenue : ${situation.label} " +
            "(θ = ${Checks.format(coefficients.theta, 2)}, " +
            "γb = ${Checks.format(coefficients.gammaB, 2)}, " +
            "γs = ${Checks.format(coefficients.gammaS, 2)})."

        val designSupportMoment = forces?.designSupportMomentKNm ?: 0.0
        val designSpanMoment = forces?.designSpanMomentKNm ?: 0.0
        val designShear = forces?.designShearKN ?: 0.0
        val designAxial = forces?.designAxialKN ?: 0.0
        val length = forces?.ultimate?.stationLengthMeters
            ?: forces?.accidental?.stationLengthMeters
            ?: forces?.service?.stationLengthMeters
            ?: family.footingLengthMeters
            ?: 1.0
        val serviceAxial = forces?.serviceAxialKN?.takeIf { it > 0.0 }
            ?: (abs(designAxial) / 1.35)

        val soil = if (spring != null && model.displacementEnvelope != null) {
            soilVerifier.verify(
                envelope = model.displacementEnvelope,
                spring = spring,
                geotechnical = model.geotechnical,
                axialServiceKN = serviceAxial,
                footingLengthMeters = length,
                providedWidthMeters = family.flangeWidthMeters
            )
        } else {
            null
        }
        if (soil == null) {
            notes += if (spring == null) {
                "Aucun ressort de sol associé : portance non vérifiée."
            } else {
                "Aucun déplacement vertical SAFE : portance non vérifiée."
            }
        } else {
            soilChecks += StripFootingSoilCheck(family.name, spring?.name ?: "Sol", soil)
            notes += soil.warnings
        }

        val support = rectangularSolver.solve(
            BaelSectionInput(
                bCm = family.webWidthMeters * 100.0,
                hCm = family.totalHeightMeters * 100.0,
                tensionCoverCm = family.bottomCoverMeters * 100.0,
                compressionCoverCm = family.topCoverMeters * 100.0,
                nuKN = designAxial,
                muGKNm = -abs(designSupportMoment),
                situation = situation
            ),
            familyMaterials
        )
        notes += support.notes

        val spanRectangular = rectangularSolver.solve(
            BaelSectionInput(
                bCm = family.webWidthMeters * 100.0,
                hCm = family.totalHeightMeters * 100.0,
                tensionCoverCm = family.bottomCoverMeters * 100.0,
                compressionCoverCm = family.topCoverMeters * 100.0,
                nuKN = 0.0,
                muGKNm = abs(designSpanMoment),
                situation = situation
            ),
            familyMaterials
        )
        notes += spanRectangular.notes

        val spanTee = if (family.isTee) {
            teeSolver.solve(
                BaelTInput(
                    bMeters = family.flangeWidthMeters,
                    b0Meters = family.webWidthMeters,
                    hMeters = family.totalHeightMeters,
                    h0Meters = family.tableThicknessMeters,
                    dPrimeMeters = family.topCoverMeters,
                    cMeters = family.bottomCoverMeters,
                    nuKN = 0.0,
                    muKNm = abs(designSpanMoment),
                    fc28MPa = familyMaterials.fc28MPa,
                    feMPa = familyMaterials.feMPa,
                    theta = coefficients.theta,
                    gammaB = coefficients.gammaB,
                    gammaS = coefficients.gammaS
                )
            ).also { notes += it.notes }
        } else {
            null
        }

        val rectangularSpanTotal = max(
            spanRectangular.tensionSteelCm2 + spanRectangular.compressionSteelCm2,
            spanRectangular.minimumSteelCm2
        )
        val teeSpanTotal = spanTee?.let {
            max(it.tensionSteelCm2 + it.compressionSteelCm2, it.minimumSteelCm2)
        }
        val adoptedSpanSteel = if (teeSpanTotal != null && teeSpanTotal <= rectangularSpanTotal) {
            teeSpanTotal
        } else {
            rectangularSpanTotal
        }
        val adoptedSpanModel = when {
            spanTee == null -> "BaelR (section rectangulaire)"
            teeSpanTotal != null && teeSpanTotal <= rectangularSpanTotal -> "BaelT (table comprimée)"
            else -> "BaelR (nervure, enveloppe)"
        }
        notes += "Travée : BaelT = ${Checks.format(teeSpanTotal ?: 0.0, 2)} cm², " +
            "BaelR = ${Checks.format(rectangularSpanTotal, 2)} cm², " +
            "modèle retenu = $adoptedSpanModel."

        val supportRequired = max(support.tensionSteelCm2, support.minimumSteelCm2)
        val supportFace = if (designSupportMoment > 1e-9) "nappe inférieure" else "nappe supérieure"
        val supportSteel = selectLongitudinal(
            zone = "appui",
            face = supportFace,
            requiredAreaCm2 = supportRequired,
            availableWidthMeters = family.webWidthMeters,
            coverMeters = max(family.topCoverMeters, family.bottomCoverMeters),
            options = options,
            code = "SF13.3_APPUI",
            reference = "BAEL 91 révisé 99, flexion simple"
        )
        val spanWidth = if (adoptedSpanModel.startsWith("BaelT")) {
            family.flangeWidthMeters
        } else {
            family.webWidthMeters
        }
        val spanSteel = selectLongitudinal(
            zone = "travée",
            face = "nappe inférieure",
            requiredAreaCm2 = adoptedSpanSteel,
            availableWidthMeters = spanWidth,
            coverMeters = family.bottomCoverMeters,
            options = options,
            code = "SF13.4_TRAVEE",
            reference = "BAEL 91 révisé 99, flexion simple"
        )

        val transverseDepth = transverseEffectiveDepth(family, options)
        val transverseRequired = transverseSteelRequired(
            axialKN = abs(designAxial),
            flangeWidthMeters = family.flangeWidthMeters,
            webWidthMeters = family.webWidthMeters,
            effectiveDepthMeters = transverseDepth,
            fsuMPa = coefficients.fsuMPa,
            ft28MPa = coefficients.ft28MPa,
            feMPa = familyMaterials.feMPa
        )
        val transverseSteel = selectMesh(
            zone = "transversal",
            requiredAreaCm2PerM = transverseRequired,
            options = options,
            code = "SF13.6_TRANSVERSAL",
            reference = "BAEL 91 révisé 99, flexion transversale"
        )

        val d = family.totalHeightMeters - family.bottomCoverMeters
        val shearStress = if (family.webWidthMeters * d > 0.0) {
            designShear / (family.webWidthMeters * d * 1000.0)
        } else {
            0.0
        }
        val shearLimit = min(
            options.shearLimitFactor * familyMaterials.fc28MPa / coefficients.gammaB,
            options.absoluteShearLimitMPa
        )
        val minimumWidth = soil?.minimumWidthMeters
            ?: soilVerifier.requiredWidthMeters(
                axialServiceKN = serviceAxial,
                footingLengthMeters = length,
                allowableStressKNM2 = model.geotechnical.allowableStressKNM2
            )

        val checks = mutableListOf<DesignCheck>()
        if (forces == null || !forces.hasDesignForces) {
            checks += Checks.boolean(
                code = "SF13.10_EFFORTS",
                label = "Efforts de calcul disponibles",
                reference = "Export SAFE, Beam Forces",
                condition = false,
                required = "Efforts ELU/ELA non nuls",
                provided = "Efforts absents ou nuls",
                failureStatus = CheckStatus.WARNING,
                message = "Le ferraillage est réduit au minimum réglementaire faute d'efforts exploitables."
            )
        } else {
            checks += Checks.info(
                code = "SF13.10_EFFORTS",
                label = "Efforts de calcul",
                reference = "Export SAFE, Beam Forces",
                value = "M_appui = ${Checks.format(designSupportMoment, 1)} kN.m, " +
                    "M_travée = ${Checks.format(designSpanMoment, 1)} kN.m, " +
                    "V_u = ${Checks.format(designShear, 1)} kN, " +
                    "N_u = ${Checks.format(designAxial, 1)} kN"
            )
        }
        checks += Checks.min(
            code = "SF13.2_LARGEUR_MIN",
            label = "Largeur minimale de semelle",
            reference = "DTR BC 2.33.1, portance",
            provided = family.flangeWidthMeters,
            minimum = minimumWidth,
            unit = "m",
            decimals = 3,
            failureMessage = "B = ${Checks.format(family.flangeWidthMeters, 2)} m < " +
                "B_min = ${Checks.format(minimumWidth, 2)} m : augmenter la largeur ou la longueur."
        )
        checks += supportSteel.check
        checks += spanSteel.check
        checks += Checks.max(
            code = "SF13.5_CISAILLEMENT",
            label = "Cisaillement dans l'âme",
            reference = "BAEL 91 révisé 99, A.5.1.2",
            provided = shearStress,
            maximum = shearLimit,
            unit = "MPa",
            decimals = 2,
            failureMessage = "τ_u = ${Checks.format(shearStress, 2)} MPa > " +
                "τ̄ = ${Checks.format(shearLimit, 2)} MPa : augmenter l'épaisseur ou les armatures d'effort tranchant."
        )
        checks += transverseSteel.check
        checks += Checks.min(
            code = "SF13.7_NON_FRAGILITE",
            label = "Pourcentage minimal d'armatures",
            reference = "BAEL 91 révisé 99, B.6.4",
            provided = min(supportSteel.providedAreaCm2, spanSteel.providedAreaCm2),
            minimum = max(support.minimumSteelCm2, spanRectangular.minimumSteelCm2),
            unit = "cm2",
            decimals = 2
        )
        checks += Checks.boolean(
            code = "SF13.8_ENROBAGE",
            label = "Enrobages",
            reference = "BAEL 91 révisé 99, A.7.1",
            condition = family.topCoverMeters in 0.02..(family.totalHeightMeters / 2.0) &&
                family.bottomCoverMeters in 0.02..(family.totalHeightMeters / 2.0),
            required = "c et d' compris entre 0,02 m et h/2",
            provided = "d' = ${Checks.format(family.topCoverMeters, 3)} m, " +
                "c = ${Checks.format(family.bottomCoverMeters, 3)} m"
        )
        if (family.isTee) {
            checks += Checks.boolean(
                code = "SF13.9_GEOMETRIE",
                label = "Géométrie de la section en Te",
                reference = "BAEL 91 révisé 99, section en Te",
                condition = family.flangeWidthMeters > family.webWidthMeters &&
                    family.tableThicknessMeters < family.totalHeightMeters,
                required = "B > b0 et h0 < h",
                provided = "B = ${Checks.format(family.flangeWidthMeters, 2)} m, " +
                    "b0 = ${Checks.format(family.webWidthMeters, 2)} m, " +
                    "h0 = ${Checks.format(family.tableThicknessMeters, 2)} m, " +
                    "h = ${Checks.format(family.totalHeightMeters, 2)} m"
            )
        } else {
            checks += Checks.info(
                code = "SF13.9_GEOMETRIE",
                label = "Géométrie rectangulaire",
                reference = "Export SAFE",
                value = "${Checks.format(family.webWidthMeters, 2)} × " +
                    "${Checks.format(family.totalHeightMeters, 2)} m"
            )
        }
        checks += Checks.info(
            code = "SF13.11_SITUATION",
            label = "Situation BAEL",
            reference = "BAEL 91 révisé 99",
            value = situation.label
        )

        if (transverseRequired > 1e-9 && transverseDepth < family.tableThicknessMeters) {
            notes += "Hauteur utile transversale retenue : ${Checks.format(transverseDepth, 3)} m."
        }
        if (spring == null) {
            notes += "Ressort de sol absent : σ_eff non calculée."
        }

        return StripFootingDesign(
            family = family,
            forces = forces,
            spring = spring,
            soil = soil,
            situation = situation,
            supportDesign = support,
            spanTeeDesign = spanTee,
            spanRectangularDesign = spanRectangular,
            adoptedSpanModel = adoptedSpanModel,
            supportSteel = supportSteel,
            spanSteel = spanSteel,
            transverseSteel = transverseSteel,
            transverseRequiredCm2PerM = transverseRequired,
            shearStressMPa = shearStress,
            shearLimitMPa = shearLimit,
            minimumWidthMeters = minimumWidth,
            checks = checks,
            notes = notes.distinct()
        )
    }

    private fun situationFor(
        family: StripFootingFamily,
        forces: StripFootingForceEnvelope?
    ): BaelSituation {
        val category = forces?.governingCategory
        return when {
            category == ComboCategory.ELA -> BaelSituation.ACCIDENTAL
            category == ComboCategory.ELU -> BaelSituation.DURABLE
            family.kind == StripFootingKind.SEISMIC_JOINT -> BaelSituation.ACCIDENTAL
            else -> BaelSituation.DURABLE
        }
    }

    /**
     * Hauteur utile de la table pour la flexion transversale. Si l'épaisseur de
     * table est trop faible, la hauteur utile de la nervure est utilisee.
     */
    private fun transverseEffectiveDepth(
        family: StripFootingFamily,
        options: StripFootingDesignOptions
    ): Double {
        val totalDepth = family.totalHeightMeters - options.transverseCoverMeters
        if (!family.isTee) return totalDepth.coerceAtLeast(0.01)
        val tableDepth = family.tableThicknessMeters - options.transverseCoverMeters
        return if (tableDepth > 0.0) tableDepth else totalDepth.coerceAtLeast(0.01)
    }

    /**
     * As,trans/ml = Nu . (B - b0) / (8 . d_table . fsu), converti en cm2/ml.
     * Le minimum de non-fragilite est applique sur 1,00 m de largeur.
     */
    private fun transverseSteelRequired(
        axialKN: Double,
        flangeWidthMeters: Double,
        webWidthMeters: Double,
        effectiveDepthMeters: Double,
        fsuMPa: Double,
        ft28MPa: Double,
        feMPa: Double
    ): Double {
        val projection = flangeWidthMeters - webWidthMeters
        if (projection <= 1e-9 || effectiveDepthMeters <= 1e-9 || fsuMPa <= 0.0) return 0.0
        val calculated = axialKN * projection / (8.0 * effectiveDepthMeters * fsuMPa) * 10.0
        val minimum = 0.23 * 1.0 * effectiveDepthMeters * ft28MPa / feMPa * 10_000.0
        return max(calculated, minimum)
    }

    private fun selectLongitudinal(
        zone: String,
        face: String,
        requiredAreaCm2: Double,
        availableWidthMeters: Double,
        coverMeters: Double,
        options: StripFootingDesignOptions,
        code: String,
        reference: String
    ): StripFootingRebarLayout {
        val usableWidth = (availableWidthMeters - 2.0 * coverMeters).coerceAtLeast(0.05)
        val minimumCount = max(
            options.minimumLongitudinalBars,
            ceil(usableWidth / options.maximumLongitudinalSpacingMeters).toInt() + 1
        )
        data class Candidate(
            val diameter: Int,
            val count: Int,
            val area: Double,
            val spacing: Double
        )

        val candidates = mutableListOf<Candidate>()
        for (diameter in options.longitudinalDiametersMm.sorted()) {
            val areaPerBar = barAreaCm2(diameter)
            for (count in minimumCount..options.maximumLongitudinalBars) {
                val spacing = usableWidth / (count - 1).coerceAtLeast(1)
                if (spacing > options.maximumLongitudinalSpacingMeters + 1e-9) continue
                val area = count * areaPerBar
                if (area + 1e-9 < requiredAreaCm2) continue
                candidates += Candidate(diameter, count, area, spacing)
            }
        }
        val best = candidates.minWithOrNull(
            compareBy<Candidate> { it.area }
                .thenBy { it.count }
                .thenBy { it.diameter }
        ) ?: Candidate(
            diameter = options.longitudinalDiametersMm.max(),
            count = options.maximumLongitudinalBars,
            area = options.maximumLongitudinalBars * barAreaCm2(options.longitudinalDiametersMm.max()),
            spacing = usableWidth / (options.maximumLongitudinalBars - 1).coerceAtLeast(1)
        )
        val provided = best.count * barAreaCm2(best.diameter)
        val label = "${best.count}T${best.diameter}"
        val check = Checks.min(
            code = code,
            label = "Armatures longitudinales en $zone ($face)",
            reference = reference,
            provided = provided,
            minimum = requiredAreaCm2,
            unit = "cm2",
            decimals = 2,
            failureMessage = "$label en $zone (${Checks.format(provided, 2)} cm²) < " +
                "As requis ${Checks.format(requiredAreaCm2, 2)} cm²."
        )
        return StripFootingRebarLayout(
            zone = zone,
            face = face,
            requiredAreaCm2 = requiredAreaCm2,
            barDiameterMm = best.diameter,
            barCount = best.count,
            providedAreaCm2 = provided,
            spacingMeters = best.spacing,
            label = label,
            check = check
        )
    }

    private fun selectMesh(
        zone: String,
        requiredAreaCm2PerM: Double,
        options: StripFootingDesignOptions,
        code: String,
        reference: String
    ): StripFootingRebarLayout {
        val minimumSpacingCm = (options.minimumTransverseSpacingMeters * 100.0).toInt().coerceAtLeast(1)
        val maximumSpacingCm = (options.maximumTransverseSpacingMeters * 100.0).toInt()
        data class Candidate(
            val diameter: Int,
            val spacingCm: Int,
            val area: Double
        )

        val candidates = mutableListOf<Candidate>()
        for (diameter in options.transverseDiametersMm.sorted()) {
            val areaPerBar = barAreaCm2(diameter)
            for (spacingCm in minimumSpacingCm..maximumSpacingCm) {
                val barsPerMeter = 100.0 / spacingCm
                val area = barsPerMeter * areaPerBar
                if (area + 1e-9 < requiredAreaCm2PerM) continue
                candidates += Candidate(diameter, spacingCm, area)
            }
        }
        val best = candidates.minWithOrNull(
            compareBy<Candidate> { it.area }
                .thenByDescending { it.spacingCm }
                .thenBy { it.diameter }
        ) ?: Candidate(
            diameter = options.transverseDiametersMm.max(),
            spacingCm = minimumSpacingCm,
            area = (100.0 / minimumSpacingCm) * barAreaCm2(options.transverseDiametersMm.max())
        )
        val provided = (100.0 / best.spacingCm) * barAreaCm2(best.diameter)
        val barsPerMeter = floor(100.0 / best.spacingCm).toInt().coerceAtLeast(1)
        val label = "T${best.diameter} e=${best.spacingCm} cm"
        val check = Checks.min(
            code = code,
            label = "Armatures transversales ($zone)",
            reference = reference,
            provided = provided,
            minimum = requiredAreaCm2PerM,
            unit = "cm2/ml",
            decimals = 2,
            failureMessage = "$label (${Checks.format(provided, 2)} cm²/ml) < " +
                "As requis ${Checks.format(requiredAreaCm2PerM, 2)} cm²/ml."
        )
        return StripFootingRebarLayout(
            zone = zone,
            face = "nappe transversale",
            requiredAreaCm2 = requiredAreaCm2PerM,
            barDiameterMm = best.diameter,
            barCount = barsPerMeter,
            providedAreaCm2 = provided,
            spacingMeters = best.spacingCm / 100.0,
            label = label,
            check = check
        )
    }

    private fun barAreaCm2(diameterMm: Int): Double =
        PI * (diameterMm / 10.0).pow(2) / 4.0
}
