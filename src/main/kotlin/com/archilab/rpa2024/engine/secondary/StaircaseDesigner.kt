package com.archilab.rpa2024.engine.secondary

import com.archilab.rpa2024.engine.frames.BaelRectangularResult
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.BaelSectionInput
import com.archilab.rpa2024.engine.frames.BaelSituation
import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionCalculator
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionInput
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionResult
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Geometrie parametrique d'une volee d'escalier en beton arme.
 * Le nombre de contremarches est le nombre total de l'escalier, reparti sur les volees.
 */
data class StaircaseGeometry(
    val name: String = "Escalier",
    val riserHeightMeters: Double = 0.175,
    val treadDepthMeters: Double = 0.28,
    val numberOfRisers: Int = 16,
    val numberOfFlights: Int = 2,
    val flightWidthMeters: Double = 1.20,
    val landingLengthMeters: Double = 1.20,
    val waistThicknessMeters: Double = 0.15,
    val landingThicknessMeters: Double = 0.15
) {
    init {
        require(riserHeightMeters > 0.0) { "La hauteur de marche doit être positive." }
        require(treadDepthMeters > 0.0) { "Le giron doit être positif." }
        require(numberOfRisers >= 3) { "Un escalier comporte au moins trois contremarches." }
        require(numberOfFlights >= 1) { "Le nombre de volées doit être au moins égal à 1." }
        require(flightWidthMeters > 0.0) { "La largeur de volée doit être positive." }
        require(landingLengthMeters >= 0.0) { "La longueur de palier doit être positive ou nulle." }
        require(waistThicknessMeters > 0.0) { "L'épaisseur de paillasse doit être positive." }
        require(landingThicknessMeters > 0.0) { "L'épaisseur de palier doit être positive." }
    }

    val risersPerFlight: Int get() = ceil(numberOfRisers.toDouble() / numberOfFlights).toInt()

    val totalRiseMeters: Double get() = riserHeightMeters * numberOfRisers

    val flightRiseMeters: Double get() = riserHeightMeters * risersPerFlight

    val flightRunMeters: Double get() = treadDepthMeters * (risersPerFlight - 1)

    val slopeAngleRadians: Double get() = atan(riserHeightMeters / treadDepthMeters)

    val slopeAngleDegrees: Double get() = Math.toDegrees(slopeAngleRadians)

    val developedFlightLengthMeters: Double
        get() = hypot(flightRunMeters, flightRiseMeters)

    /** Longueur de calcul de la poutre-dalle equivalente : volée + palier. */
    val designSpanMeters: Double get() = flightRunMeters + landingLengthMeters

    val minimumWaistThicknessMeters: Double get() = designSpanMeters / 30.0

    val maximumWaistThicknessMeters: Double get() = designSpanMeters / 20.0

    val blondelValueMeters: Double get() = 2.0 * riserHeightMeters + treadDepthMeters

    val geometryLabel: String
        get() = "$numberOfRisers contremarches de ${Checks.format(riserHeightMeters * 100.0, 1)} cm, " +
            "giron ${Checks.format(treadDepthMeters * 100.0, 1)} cm, largeur " +
            "${Checks.format(flightWidthMeters, 2)} m"
}

/** Donnees d'entree du calcul d'escalier. */
data class StaircaseDesignInput(
    val geometry: StaircaseGeometry = StaircaseGeometry(),
    val materials: MaterialConfig = MaterialConfig(),
    val permanentSuperLoadKNM2: Double = 1.50,
    val liveLoadKNM2: Double = 2.50,
    val handrailLoadKNPerM: Double = 0.50,
    val finishesLoadKNM2: Double = 1.20,
    val stepUnitWeightKNM3: Double = 22.0,
    val concreteUnitWeightKNM3: Double = 25.0,
    val concreteCoverCm: Double = 2.0,
    val spanMomentRatio: Double = 0.85,
    val supportMomentRatio: Double = 0.50,
    val barDiameterMm: Int = 10,
    val maximumSpacingMeters: Double = 0.20,
    val distributionRatio: Double = 0.25
) {
    init {
        require(permanentSuperLoadKNM2 >= 0.0) { "La charge permanente doit être positive ou nulle." }
        require(liveLoadKNM2 >= 0.0) { "La surcharge d'exploitation doit être positive ou nulle." }
        require(handrailLoadKNPerM >= 0.0) { "La charge de garde-corps doit être positive ou nulle." }
        require(finishesLoadKNM2 >= 0.0) { "La charge de revêtement doit être positive ou nulle." }
        require(stepUnitWeightKNM3 >= 0.0) { "Le poids volumique des marches doit être positif ou nul." }
        require(concreteUnitWeightKNM3 > 0.0) { "Le poids volumique du béton doit être positif." }
        require(concreteCoverCm > 0.0) { "L'enrobage doit être positif." }
        require(spanMomentRatio in 0.0..1.0) { "Le ratio de moment en travée doit être compris entre 0 et 1." }
        require(supportMomentRatio in 0.0..1.0) { "Le ratio de moment sur appui doit être compris entre 0 et 1." }
        require(barDiameterMm >= 6) { "Le diamètre des barres doit être au moins 6 mm." }
        require(maximumSpacingMeters > 0.0) { "L'espacement maximal doit être positif." }
        require(distributionRatio in 0.0..1.0) { "Le ratio d'armatures de répartition doit être compris entre 0 et 1." }
    }
}

/** Charges ramenees a la poutre-dalle equivalente (par metre de largeur). */
data class StaircaseLoadSummary(
    val waistPermanentKNM2: Double,
    val landingPermanentKNM2: Double,
    val permanentKNM2: Double,
    val variableKNM2: Double,
    val ultimateKNM2: Double,
    val serviceKNM2: Double,
    val ultimateLineLoadKNPerM: Double,
    val serviceLineLoadKNPerM: Double
)

/** Ferraillage d'une zone d'escalier (travée, appui ou répartition). */
data class StaircaseReinforcement(
    val zone: String,
    val designMomentKNm: Double,
    val bael: BaelRectangularResult,
    val requiredAsCm2PerM: Double,
    val barDiameterMm: Int,
    val spacingMeters: Double,
    val providedAsCm2PerM: Double,
    val label: String,
    val check: DesignCheck
) {
    val status: CheckStatus get() = check.status
}

/** Resultat complet de l'etude d'un escalier (chapitre 12). */
data class StaircaseDesignResult(
    val geometry: StaircaseGeometry,
    val loads: StaircaseLoadSummary,
    val spanMomentUltimateKNm: Double,
    val supportMomentUltimateKNm: Double,
    val shearUltimateKN: Double,
    val spanReinforcement: StaircaseReinforcement,
    val supportReinforcement: StaircaseReinforcement,
    val distributionReinforcement: StaircaseReinforcement,
    val deflection: TBeamDeflectionResult,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })

    fun toChapterReport(): ChapterReport {
        val elements = listOf(
            ElementCheckReport(
                elementType = "Escalier - travée",
                name = geometry.name,
                geometry = "${spanReinforcement.label} sur b = 1,00 m",
                checks = listOf(spanReinforcement.check)
            ),
            ElementCheckReport(
                elementType = "Escalier - appui",
                name = geometry.name,
                geometry = "${supportReinforcement.label} sur b = 1,00 m",
                checks = listOf(supportReinforcement.check)
            ),
            ElementCheckReport(
                elementType = "Escalier - répartition",
                name = geometry.name,
                geometry = "${distributionReinforcement.label} sur b = 1,00 m",
                checks = listOf(distributionReinforcement.check)
            )
        )
        val reinforcementCodes = setOf("PE12.4_TRAVEE", "PE12.5_APPUI", "PE12.7_REPARTITION")
        val chapterChecks = checks.filter { it.code !in reinforcementCodes }
        val lines = buildList {
            add("${geometry.name} : ${geometry.geometryLabel}.")
            add(
                "Blondel : 2h + g = ${Checks.format(geometry.blondelValueMeters * 100.0, 1)} cm ; " +
                    "inclinaison alpha = ${Checks.format(geometry.slopeAngleDegrees, 2)}°."
            )
            add(
                "Portée de calcul L = ${Checks.format(geometry.designSpanMeters)} m ; " +
                    "épaisseur de paillasse ${Checks.format(geometry.waistThicknessMeters * 100.0, 0)} cm " +
                    "(plage ${Checks.format(geometry.minimumWaistThicknessMeters * 100.0, 1)} à " +
                    "${Checks.format(geometry.maximumWaistThicknessMeters * 100.0, 1)} cm)."
            )
            add(
                "Charges : G = ${Checks.format(loads.permanentKNM2)} kN/m², Q = " +
                    "${Checks.format(loads.variableKNM2)} kN/m², qu = " +
                    "${Checks.format(loads.ultimateKNM2)} kN/m²."
            )
            add(
                "Moments : M0 = ${Checks.format(spanMomentUltimateKNm / 0.85)} kN.m, " +
                    "Mt = ${Checks.format(spanMomentUltimateKNm)} kN.m, " +
                    "Ma = ${Checks.format(supportMomentUltimateKNm)} kN.m, " +
                    "Vu = ${Checks.format(shearUltimateKN)} kN."
            )
            add(
                "Ferraillage : travée ${spanReinforcement.label} " +
                    "(${Checks.format(spanReinforcement.providedAsCm2PerM)} cm²/m), appui " +
                    "${supportReinforcement.label} (${Checks.format(supportReinforcement.providedAsCm2PerM)} cm²/m), " +
                    "répartition ${distributionReinforcement.label}."
            )
            add(
                "Flèche nuisible Δft = ${Checks.format(deflection.deltaFtMeters * 1000.0, 2)} mm " +
                    "≤ ${Checks.format(deflection.allowableMeters * 1000.0, 2)} mm."
            )
        }
        return ChapterReport(
            number = 12,
            title = "12.1 Étude des escaliers",
            summary = "Escalier en paillasse pleine calculé comme poutre-dalle continue, charges DTR BC 2.2, " +
                "ferraillage BAEL 91 et vérification de la flèche.",
            checks = chapterChecks,
            elements = elements,
            lines = lines
        )
    }
}

/**
 * Extracteur de la geometrie des escaliers a partir des dalles inclinees du modele ETABS.
 * Chaque surface dont les noeuds presentent une pente significative est convertie en volee.
 */
class StaircaseExtractor {

    data class Settings(
        val targetRiserHeightMeters: Double = 0.17,
        val defaultLandingLengthMeters: Double = 1.20,
        val defaultWaistThicknessMeters: Double = 0.15,
        val minimumSlopeMeters: Double = 0.10
    )

    fun fromModel(
        model: ParsedEtabsModel,
        settings: Settings = Settings()
    ): List<StaircaseGeometry> {
        val points = model.points
        val inclined = model.areas.filter { it.kind == AreaKind.SLAB && it.nodeIds.size >= 3 }
        return inclined.mapNotNull { area ->
            val nodes = area.nodeIds.mapNotNull { points[it] }
            if (nodes.size < 3) return@mapNotNull null
            val zValues = nodes.map { it.zMeters }
            val rise = (zValues.maxOrNull() ?: 0.0) - (zValues.minOrNull() ?: 0.0)
            if (rise < settings.minimumSlopeMeters) return@mapNotNull null

            val run = nodes.maxOf { a -> nodes.maxOf { b -> a.planDistanceTo(b) } }
            if (run <= 0.0) return@mapNotNull null

            val xs = nodes.map { it.xMeters }
            val ys = nodes.map { it.yMeters }
            val width = minOf(xs.max() - xs.min(), ys.max() - ys.min())
                .takeIf { it > 0.5 } ?: (run / 2.0).coerceAtLeast(0.6)

            val risers = max(3, (rise / settings.targetRiserHeightMeters).roundToInt())
            val riser = rise / risers
            val tread = run / max(1, risers)
            val section = model.section(area.propertyName)
            val thickness = section?.totalThicknessCm?.div(100.0)
                ?: section?.thicknessCm?.div(100.0)
                ?: settings.defaultWaistThicknessMeters

            StaircaseGeometry(
                name = "Escalier ${area.id}",
                riserHeightMeters = riser,
                treadDepthMeters = tread,
                numberOfRisers = risers,
                numberOfFlights = 1,
                flightWidthMeters = width,
                landingLengthMeters = settings.defaultLandingLengthMeters,
                waistThicknessMeters = thickness,
                landingThicknessMeters = thickness
            )
        }.distinctBy { "${Checks.format(it.riserHeightMeters, 3)}-${Checks.format(it.treadDepthMeters, 3)}-${Checks.format(it.flightWidthMeters, 2)}" }
    }
}

/**
 * Moteur de calcul des escaliers en paillasse pleine : verification de Blondel,
 * descente de charges DTR BC 2.2, poutre-dalle equivalente, ferraillage BAEL 91
 * et fiche de fleche. Aucune dimension n'est codee en dur : tout provient de [StaircaseGeometry].
 */
class StaircaseDesigner(
    private val solver: BaelRectangularSolver = BaelRectangularSolver(),
    private val deflectionCalculator: TBeamDeflectionCalculator = TBeamDeflectionCalculator()
) {

    fun design(input: StaircaseDesignInput): StaircaseDesignResult {
        val geometry = input.geometry
        val materials = input.materials
        val alpha = geometry.slopeAngleRadians
        val cosAlpha = cos(alpha).coerceAtLeast(0.2)

        val waistSelfWeight = input.concreteUnitWeightKNM3 * geometry.waistThicknessMeters / cosAlpha
        val stepWeight = input.stepUnitWeightKNM3 * geometry.riserHeightMeters / 2.0
        val handrailPerM2 = if (geometry.flightWidthMeters > 0.0) {
            input.handrailLoadKNPerM / geometry.flightWidthMeters
        } else {
            0.0
        }
        val waistPermanent = waistSelfWeight + stepWeight + input.finishesLoadKNM2 + handrailPerM2
        val landingPermanent = input.concreteUnitWeightKNM3 * geometry.landingThicknessMeters +
            input.finishesLoadKNM2 + handrailPerM2
        val permanent = (waistPermanent * geometry.flightRunMeters +
            landingPermanent * geometry.landingLengthMeters) /
            geometry.designSpanMeters.coerceAtLeast(1e-9)
        val variable = input.liveLoadKNM2
        val ultimate = 1.35 * (permanent + input.permanentSuperLoadKNM2) + 1.50 * variable
        val service = permanent + input.permanentSuperLoadKNM2 + variable
        val width = geometry.flightWidthMeters

        val loads = StaircaseLoadSummary(
            waistPermanentKNM2 = waistPermanent,
            landingPermanentKNM2 = landingPermanent,
            permanentKNM2 = permanent,
            variableKNM2 = variable,
            ultimateKNM2 = ultimate,
            serviceKNM2 = service,
            ultimateLineLoadKNPerM = ultimate * width,
            serviceLineLoadKNPerM = service * width
        )

        val span = geometry.designSpanMeters
        val ultimateLineLoad = loads.ultimateLineLoadKNPerM
        val serviceLineLoad = loads.serviceLineLoadKNPerM
        val isostaticMoment = ultimateLineLoad * span * span / 8.0
        val spanMoment = input.spanMomentRatio * isostaticMoment
        val supportMoment = input.supportMomentRatio * isostaticMoment
        val shear = ultimateLineLoad * span / 2.0

        val widthCm = 100.0
        val spanDepthCm = geometry.waistThicknessMeters * 100.0
        val supportDepthCm = geometry.landingThicknessMeters * 100.0
        val spanBael = solveSection(
            widthCm = widthCm,
            depthCm = spanDepthCm,
            momentKNm = spanMoment,
            materials = materials,
            coverCm = input.concreteCoverCm
        )
        val supportBael = solveSection(
            widthCm = widthCm,
            depthCm = supportDepthCm,
            momentKNm = supportMoment,
            materials = materials,
            coverCm = input.concreteCoverCm
        )

        val spanReinforcement = reinforcement(
            zone = "Travée",
            bael = spanBael,
            input = input,
            requiredAsCm2PerM = spanBael.requiredSteelCm2
        )
        val supportReinforcement = reinforcement(
            zone = "Appui",
            bael = supportBael,
            input = input,
            requiredAsCm2PerM = supportBael.requiredSteelCm2
        )
        val distributionRequired = max(
            input.distributionRatio * max(spanReinforcement.providedAsCm2PerM, supportReinforcement.providedAsCm2PerM),
            0.20
        )
        val distributionReinforcement = reinforcement(
            zone = "Répartition",
            bael = spanBael,
            input = input,
            requiredAsCm2PerM = distributionRequired
        )

        val effectiveDepth = (spanDepthCm - input.concreteCoverCm) / 100.0
        val tau = shear / (1.0 * effectiveDepth.coerceAtLeast(0.01)) / 1000.0
        val tauLimit = minOf(0.20 * materials.fc28MPa / materials.gammaBElu, 4.0)
        val shearCheck = Checks.max(
            code = "PE12.6_CISAILLEMENT",
            label = "Cisaillement de la paillasse",
            reference = "BAEL 91 révisé 99, A.5.1",
            provided = tau,
            maximum = tauLimit,
            unit = "MPa",
            decimals = 2,
            failureMessage = "τu = ${Checks.format(tau, 2)} MPa > ${Checks.format(tauLimit, 2)} MPa : " +
                "augmenter l'épaisseur de paillasse ou la classe de béton."
        )

        val blondelCheck = Checks.between(
            code = "PE12.1_BLONDEL",
            label = "Loi de Blondel 2h + g",
            reference = "DTR BC 2.2 / pratique des escaliers",
            provided = geometry.blondelValueMeters * 100.0,
            minimum = 60.0,
            maximum = 64.0,
            unit = "cm",
            decimals = 1,
            outOfRangeStatus = CheckStatus.FAILED,
            message = "2h + g = ${Checks.format(geometry.blondelValueMeters * 100.0, 1)} cm " +
                "en dehors de la plage ergonomique 60-64 cm."
        )
        val thicknessCheck = Checks.between(
            code = "PE12.2_EPAISSEUR",
            label = "Épaisseur de paillasse",
            reference = "Pratique BAEL - L/30 ≤ ep ≤ L/20",
            provided = geometry.waistThicknessMeters * 100.0,
            minimum = geometry.minimumWaistThicknessMeters * 100.0,
            maximum = geometry.maximumWaistThicknessMeters * 100.0,
            unit = "cm",
            decimals = 1,
            outOfRangeStatus = CheckStatus.FAILED,
            message = "L'épaisseur de paillasse doit rester comprise entre " +
                "${Checks.format(geometry.minimumWaistThicknessMeters * 100.0, 1)} et " +
                "${Checks.format(geometry.maximumWaistThicknessMeters * 100.0, 1)} cm."
        )

        val deflection = computeDeflection(
            input = input,
            geometry = geometry,
            loads = loads,
            spanMomentService = (permanent + input.permanentSuperLoadKNM2 + variable) * width * span * span / 8.0,
            providedSteelCm2PerM = spanReinforcement.providedAsCm2PerM,
            effectiveDepthMeters = effectiveDepth
        )

        val checks = listOf(blondelCheck, thicknessCheck, shearCheck) +
            spanReinforcement.check + supportReinforcement.check + distributionReinforcement.check +
            deflection.checks

        val notes = mutableListOf<String>()
        notes += "Poutre-dalle équivalente de ${Checks.format(span)} m de portée et 1,00 m de largeur."
        notes += "Moment isostatique M0 = ${Checks.format(isostaticMoment)} kN.m ; " +
            "Mt = ${Checks.format(input.spanMomentRatio, 2)} M0, Ma = ${Checks.format(input.supportMomentRatio, 2)} M0."
        notes += spanBael.notes
        notes += supportBael.notes

        return StaircaseDesignResult(
            geometry = geometry,
            loads = loads,
            spanMomentUltimateKNm = spanMoment,
            supportMomentUltimateKNm = supportMoment,
            shearUltimateKN = shear,
            spanReinforcement = spanReinforcement,
            supportReinforcement = supportReinforcement,
            distributionReinforcement = distributionReinforcement,
            deflection = deflection,
            checks = checks,
            notes = notes.distinct()
        )
    }

    private fun solveSection(
        widthCm: Double,
        depthCm: Double,
        momentKNm: Double,
        materials: MaterialConfig,
        coverCm: Double
    ): BaelRectangularResult = solver.solve(
        BaelSectionInput(
            bCm = widthCm,
            hCm = depthCm,
            tensionCoverCm = coverCm.coerceAtMost(depthCm - 1.0),
            compressionCoverCm = coverCm.coerceAtMost(depthCm - 1.0),
            nuKN = 0.0,
            muGKNm = momentKNm,
            situation = BaelSituation.DURABLE
        ),
        materials
    )

    private fun reinforcement(
        zone: String,
        bael: BaelRectangularResult,
        input: StaircaseDesignInput,
        requiredAsCm2PerM: Double
    ): StaircaseReinforcement {
        val mesh = selectMesh(
            requiredAsCm2PerM = requiredAsCm2PerM,
            preferredDiameterMm = input.barDiameterMm,
            maximumSpacingMeters = input.maximumSpacingMeters
        )
        val sufficient = mesh.areaCm2PerM + 1e-9 >= requiredAsCm2PerM
        val code = when (zone) {
            "Travée" -> "PE12.4_TRAVEE"
            "Appui" -> "PE12.5_APPUI"
            else -> "PE12.7_REPARTITION"
        }
        val check = Checks.boolean(
            code = code,
            label = "Ferraillage $zone ${mesh.label}",
            reference = "BAEL 91 révisé 99, A.4.2",
            condition = sufficient,
            required = "As ≥ ${Checks.format(requiredAsCm2PerM)} cm²/m",
            provided = "As = ${Checks.format(mesh.areaCm2PerM)} cm²/m",
            message = "Le maillage ${mesh.label} est insuffisant pour la zone $zone."
        )
        return StaircaseReinforcement(
            zone = zone,
            designMomentKNm = bael.momentKNm,
            bael = bael,
            requiredAsCm2PerM = requiredAsCm2PerM,
            barDiameterMm = mesh.diameterMm,
            spacingMeters = mesh.spacingMeters,
            providedAsCm2PerM = mesh.areaCm2PerM,
            label = mesh.label,
            check = check
        )
    }

    private fun computeDeflection(
        input: StaircaseDesignInput,
        geometry: StaircaseGeometry,
        loads: StaircaseLoadSummary,
        spanMomentService: Double,
        providedSteelCm2PerM: Double,
        effectiveDepthMeters: Double
    ): TBeamDeflectionResult {
        val materials = input.materials
        val span = geometry.designSpanMeters
        val width = geometry.flightWidthMeters
        val structuralMoment = loads.waistPermanentKNM2 * width * span * span / 8.0
        val permanentMoment = loads.permanentKNM2 * width * span * span / 8.0
        return deflectionCalculator.calculate(
            TBeamDeflectionInput(
                flangeWidthMeters = 1.0,
                webWidthMeters = 1.0,
                totalHeightMeters = geometry.waistThicknessMeters,
                slabThicknessMeters = 0.0,
                effectiveDepthMeters = effectiveDepthMeters.coerceAtLeast(0.02),
                steelAreaCm2 = providedSteelCm2PerM,
                spanMeters = span,
                momentBeforeFinishesKNm = structuralMoment,
                momentPermanentKNm = max(permanentMoment, structuralMoment),
                momentTotalServiceKNm = spanMomentService,
                fc28MPa = materials.fc28MPa,
                ft28MPa = materials.ft28MPa,
                eijMPa = materials.eijMPa,
                evjMPa = materials.evjMPa,
                feMPa = materials.feMPa,
                concreteStressLimitMPa = 0.6 * materials.fc28MPa,
                steelStressLimitMPa = 0.8 * materials.feMPa
            )
        )
    }

    private data class MeshCandidate(
        val diameterMm: Int,
        val spacingMeters: Double,
        val areaCm2PerM: Double
    ) {
        val label: String get() = "T$diameterMm e=${Checks.format(spacingMeters * 100.0, 0)} cm"
    }

    private fun selectMesh(
        requiredAsCm2PerM: Double,
        preferredDiameterMm: Int,
        maximumSpacingMeters: Double
    ): MeshCandidate {
        val diameters = listOf(preferredDiameterMm, 10, 12, 14, 16).filter { it >= 6 }.distinct().sorted()
        val spacings = generateSequence(0.10) { if (it + 0.05 <= maximumSpacingMeters + 1e-9) it + 0.05 else null }
            .toList()
            .ifEmpty { listOf(maximumSpacingMeters) }
        val candidates = diameters.flatMap { diameter ->
            spacings.map { spacing ->
                val barArea = PI * (diameter / 10.0) * (diameter / 10.0) / 4.0
                MeshCandidate(diameter, spacing, barArea / spacing)
            }
        }
        return candidates.firstOrNull { it.areaCm2PerM + 1e-9 >= requiredAsCm2PerM }
            ?: candidates.maxBy { it.areaCm2PerM }
    }
}
