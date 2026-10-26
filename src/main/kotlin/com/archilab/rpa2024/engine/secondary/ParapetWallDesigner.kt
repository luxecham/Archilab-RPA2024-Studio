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
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.normative.SeismicZone
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.sin

/**
 * Geometrie parametrique d'un acrotere en beton arme assimile a une console verticale
 * encastree dans le plancher terrasse. Toutes les valeurs sont exprimees en metres.
 */
data class ParapetGeometry(
    val heightMeters: Double = 0.60,
    val stemThicknessMeters: Double = 0.10,
    val corniceProjectionMeters: Double = 0.15,
    val corniceStraightHeightMeters: Double = 0.05,
    val corniceSlopedHeightMeters: Double = 0.05,
    val renderThicknessMeters: Double = 0.02
) {
    init {
        require(heightMeters > 0.0) { "La hauteur de l'acrotère doit être positive." }
        require(stemThicknessMeters > 0.0) { "L'épaisseur du fût doit être positive." }
        require(corniceProjectionMeters > 0.0) { "La saillie du becquet doit être positive." }
        require(corniceStraightHeightMeters >= 0.0) { "La hauteur droite du becquet doit être positive ou nulle." }
        require(corniceSlopedHeightMeters >= 0.0) { "La hauteur inclinée du becquet doit être positive ou nulle." }
        require(corniceStraightHeightMeters + corniceSlopedHeightMeters < heightMeters) {
            "La hauteur cumulée du becquet doit rester inférieure à la hauteur totale de l'acrotère."
        }
        require(renderThicknessMeters >= 0.0) { "L'épaisseur d'enduit doit être positive ou nulle." }
    }

    /** Angle d'inclinaison superieur : tan(alpha) = h_b2 / b_bec. */
    val slopeAngleRadians: Double
        get() = atan(corniceSlopedHeightMeters / corniceProjectionMeters)

    val slopeAngleDegrees: Double get() = Math.toDegrees(slopeAngleRadians)

    private val slopedFaceLengthMeters: Double
        get() = if (slopeAngleRadians <= 0.0) 0.0 else corniceSlopedHeightMeters / sin(slopeAngleRadians)

    /** Hauteur de la face interieure du fut, sous le becquet. */
    val stemInnerHeightMeters: Double
        get() = heightMeters - corniceStraightHeightMeters - corniceSlopedHeightMeters

    /**
     * Perimetre developpe du contour ferme de la section (deux faces du fut, about superieur,
     * sous-face du becquet, face droite et face inclinee). L'enduit enveloppe tout le contour.
     */
    val developedPerimeterMeters: Double
        get() = stemInnerHeightMeters +
            heightMeters +
            2.0 * stemThicknessMeters +
            corniceProjectionMeters +
            corniceStraightHeightMeters +
            slopedFaceLengthMeters

    /** Aire de la section transversale : fut + becquet rectangulaire + becquet triangulaire. */
    val sectionAreaM2: Double
        get() = heightMeters * stemThicknessMeters +
            corniceStraightHeightMeters * corniceProjectionMeters +
            corniceProjectionMeters * corniceSlopedHeightMeters / 2.0

    val topWidthMeters: Double get() = stemThicknessMeters + corniceProjectionMeters

    val geometryLabel: String
        get() = "H=${Checks.format(heightMeters, 2)} m, e=${Checks.format(stemThicknessMeters, 2)} m, " +
            "becquet ${Checks.format(corniceProjectionMeters, 2)}x" +
            "${Checks.format(corniceStraightHeightMeters + corniceSlopedHeightMeters, 2)} m"
}

/** Charges lineaires de l'acrotere pour une bande de 1,00 ml. */
data class ParapetLoadSummary(
    val concreteWeightKNPerM: Double,
    val renderWeightKNPerM: Double,
    val totalWeightWpKNPerM: Double,
    val zoneAccelerationA: Double,
    val behaviorFactorCp: Double,
    val seismicAmplification: Double,
    val seismicForceFpKNPerM: Double,
    val handrailLoadKNPerM: Double,
    val designHorizontalQKNPerM: Double
) {
    val isSeismicGoverning: Boolean get() = seismicForceFpKNPerM > handrailLoadKNPerM
}

/** Maillage d'acier retenu pour une nappe de l'acrotere (bande de 1,00 ml). */
data class ParapetMeshDesign(
    val zone: String,
    val designMomentKNm: Double,
    val axialKN: Double,
    val calculatedAsCm2PerM: Double,
    val minimumAsCm2PerM: Double,
    val requiredAsCm2PerM: Double,
    val barDiameterMm: Int,
    val spacingMeters: Double,
    val providedAsCm2PerM: Double,
    val label: String,
    val status: CheckStatus,
    val check: DesignCheck
)

/** Donnees d'entree du calcul d'acrotere. */
data class ParapetDesignInput(
    val geometry: ParapetGeometry = ParapetGeometry(),
    val materials: MaterialConfig = MaterialConfig(),
    val zoneAccelerationA: Double = SeismicZone.ZONE_I.coeffA,
    val behaviorFactorCp: Double = 0.80,
    val seismicAmplification: Double = 4.0,
    val handrailLoadKNPerM: Double = 1.00,
    val concreteUnitWeightKNM3: Double = 25.0,
    val renderUnitWeightKNM3: Double = 20.0,
    val concreteCoverCm: Double = 2.0,
    val barDiameterMm: Int = 8,
    val maximumSpacingMeters: Double = 0.20,
    val distributionRatio: Double = 0.25
) {
    init {
        require(zoneAccelerationA >= 0.0) { "Le coefficient sismique A doit être positif ou nul." }
        require(behaviorFactorCp > 0.0) { "Le coefficient Cp doit être positif." }
        require(seismicAmplification > 0.0) { "Le coefficient d'amplification sismique doit être positif." }
        require(handrailLoadKNPerM >= 0.0) { "La surcharge de main courante doit être positive ou nulle." }
        require(concreteUnitWeightKNM3 > 0.0) { "Le poids volumique du béton doit être positif." }
        require(renderUnitWeightKNM3 >= 0.0) { "Le poids volumique de l'enduit doit être positif ou nul." }
        require(
            concreteCoverCm > 0.0 &&
                concreteCoverCm + barDiameterMm / 20.0 < geometry.stemThicknessMeters * 100.0
        ) {
            "L'enrobage est incompatible avec l'épaisseur du fût."
        }
        require(barDiameterMm >= 6) { "Le diamètre des barres doit être au moins 6 mm." }
        require(maximumSpacingMeters > 0.0) { "L'espacement maximal doit être positif." }
        require(distributionRatio in 0.0..1.0) { "Le ratio d'armatures de répartition doit être compris entre 0 et 1." }
    }

    companion object {
        fun fromSeismicZone(zone: SeismicZone, geometry: ParapetGeometry = ParapetGeometry()): ParapetDesignInput =
            ParapetDesignInput(geometry = geometry, zoneAccelerationA = zone.coeffA)
    }
}

/** Resultat complet de l'etude de l'acrotere (chapitre 11). */
data class ParapetDesignResult(
    val geometry: ParapetGeometry,
    val loads: ParapetLoadSummary,
    val durableResult: BaelRectangularResult,
    val accidentalResult: BaelRectangularResult,
    val governingSituation: BaelSituation,
    val mainSteel: ParapetMeshDesign,
    val distributionSteel: ParapetMeshDesign,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })

    fun toChapterReport(): ChapterReport {
        val mainCheck = ElementCheckReport(
            elementType = "Acrotère",
            name = "Nappe principale",
            geometry = "${mainSteel.label} sur b = 1,00 ml",
            checks = listOf(mainSteel.check)
        )
        val distributionCheck = ElementCheckReport(
            elementType = "Acrotère",
            name = "Nappe de répartition",
            geometry = "${distributionSteel.label} sur b = 1,00 ml",
            checks = listOf(distributionSteel.check)
        )
        val lines = buildList {
            add(
                "Acrotère ${geometry.geometryLabel} : alpha = " +
                    "${Checks.format(geometry.slopeAngleDegrees, 2)}°, " +
                    "P = ${Checks.format(geometry.developedPerimeterMeters)} m, " +
                    "S = ${Checks.format(geometry.sectionAreaM2 * 1000.0, 2)} dm²."
            )
            add(
                "Poids propre G1 = ${Checks.format(loads.concreteWeightKNPerM)} kN/ml ; " +
                    "enduit G2 = ${Checks.format(loads.renderWeightKNPerM)} kN/ml ; " +
                    "Wp = ${Checks.format(loads.totalWeightWpKNPerM)} kN/ml."
            )
            add(
                "Force sismique Fp = ${Checks.format(loads.seismicAmplification, 1)} A Cp Wp = " +
                    "${Checks.format(loads.seismicForceFpKNPerM)} kN/ml " +
                    "(A = ${Checks.format(loads.zoneAccelerationA, 2)}, Cp = " +
                    "${Checks.format(loads.behaviorFactorCp, 2)})."
            )
            add(
                "Q de calcul = max(Fp ; ${Checks.format(loads.handrailLoadKNPerM)} kN/ml) = " +
                    "${Checks.format(loads.designHorizontalQKNPerM)} kN/ml " +
                    if (loads.isSeismicGoverning) "(effort sismique dimensionnant)" else "(main courante dimensionnante)."
            )
            add(
                "Moment d'encastrement : Mu = ${Checks.format(durableResult.momentKNm)} kN.m, " +
                    "Nu = ${Checks.format(durableResult.axialKN)} kN ; situation retenue : " +
                    "${governingSituation.label}."
            )
            add(
                "Armatures principales : ${mainSteel.label} = ${Checks.format(mainSteel.providedAsCm2PerM)} cm²/ml " +
                    "(requis ${Checks.format(mainSteel.requiredAsCm2PerM)} cm²/ml)."
            )
            add(
                "Armatures de répartition : ${distributionSteel.label} = " +
                    "${Checks.format(distributionSteel.providedAsCm2PerM)} cm²/ml."
            )
        }
        return ChapterReport(
            number = 11,
            title = "11.1 Étude de l'acrotère",
            summary = "Console verticale de 1,00 ml soumise au poids propre, à l'enduit, à la main courante " +
                "et à la force sismique RPA 2024 des éléments secondaires ; ferraillage BAEL 91 en flexion composée.",
            checks = checks,
            elements = listOf(mainCheck, distributionCheck),
            lines = lines
        )
    }
}

/**
 * Dimensionnement complet d'un acrotere en console. Le moteur balaie la geometrie du bloc,
 * calcule les charges lineiques, la force sismique Fp = 4 A Cp Wp, puis ferraille la section
 * rectangulaire de 1,00 ml de largeur avec le noyau BAELR.
 */
class ParapetWallDesigner(
    private val solver: BaelRectangularSolver = BaelRectangularSolver()
) {

    fun design(input: ParapetDesignInput): ParapetDesignResult {
        val geometry = input.geometry
        val materials = input.materials

        val concreteWeight = input.concreteUnitWeightKNM3 * geometry.sectionAreaM2
        val renderWeight = input.renderUnitWeightKNM3 * geometry.renderThicknessMeters *
            geometry.developedPerimeterMeters
        val totalWeight = concreteWeight + renderWeight
        val seismicForce = input.seismicAmplification * input.zoneAccelerationA *
            input.behaviorFactorCp * totalWeight
        val designHorizontal = max(seismicForce, input.handrailLoadKNPerM)
        val loads = ParapetLoadSummary(
            concreteWeightKNPerM = concreteWeight,
            renderWeightKNPerM = renderWeight,
            totalWeightWpKNPerM = totalWeight,
            zoneAccelerationA = input.zoneAccelerationA,
            behaviorFactorCp = input.behaviorFactorCp,
            seismicAmplification = input.seismicAmplification,
            seismicForceFpKNPerM = seismicForce,
            handrailLoadKNPerM = input.handrailLoadKNPerM,
            designHorizontalQKNPerM = designHorizontal
        )

        val widthCm = 100.0
        val heightCm = geometry.stemThicknessMeters * 100.0
        val baseMomentService = designHorizontal * geometry.heightMeters
        val durableMoment = 1.50 * baseMomentService
        val durableAxial = 1.35 * totalWeight
        val durable = solver.solve(
            BaelSectionInput(
                bCm = widthCm,
                hCm = heightCm,
                tensionCoverCm = input.concreteCoverCm,
                compressionCoverCm = input.concreteCoverCm,
                nuKN = durableAxial,
                muGKNm = durableMoment,
                situation = BaelSituation.DURABLE
            ),
            materials
        )
        val accidental = solver.solve(
            BaelSectionInput(
                bCm = widthCm,
                hCm = heightCm,
                tensionCoverCm = input.concreteCoverCm,
                compressionCoverCm = input.concreteCoverCm,
                nuKN = totalWeight,
                muGKNm = baseMomentService,
                situation = BaelSituation.ACCIDENTAL
            ),
            materials
        )

        val governing = if (accidental.requiredSteelCm2 > durable.requiredSteelCm2) {
            BaelSituation.ACCIDENTAL
        } else {
            BaelSituation.DURABLE
        }
        val governingResult = if (governing == BaelSituation.ACCIDENTAL) accidental else durable
        val minimumSteel = max(durable.minimumSteelCm2, accidental.minimumSteelCm2)
        val requiredSteel = max(governingResult.requiredSteelCm2, minimumSteel)

        val mainSteel = designMesh(
            zone = "Nappe principale (verticale)",
            requiredAsCm2PerM = requiredSteel,
            calculatedAsCm2PerM = governingResult.tensionSteelCm2,
            minimumAsCm2PerM = minimumSteel,
            momentKNm = governingResult.momentKNm,
            axialKN = governingResult.axialKN,
            input = input
        )
        val distributionRequired = max(input.distributionRatio * mainSteel.requiredAsCm2PerM, 0.20)
        val distributionSteel = designMesh(
            zone = "Nappe de répartition (horizontale)",
            requiredAsCm2PerM = distributionRequired,
            calculatedAsCm2PerM = distributionRequired,
            minimumAsCm2PerM = 0.20,
            momentKNm = 0.0,
            axialKN = 0.0,
            input = input
        )

        val checks = mutableListOf<DesignCheck>()
        checks += Checks.boolean(
            code = "PA11.1_GEOMETRIE",
            label = "Géométrie de l'acrotère",
            reference = "DTR BC 2.2 / plan de coffrage",
            condition = geometry.heightMeters > 0.0 && geometry.sectionAreaM2 > 0.0,
            required = "Hauteur et section positives",
            provided = geometry.geometryLabel,
            message = "La géométrie de l'acrotère est incomplète."
        )
        checks += Checks.boolean(
            code = "PA11.2_FP",
            label = "Force sismique de l'élément secondaire",
            reference = "RPA 2024 § 6.3 (Fp = 4 A Cp Wp)",
            condition = loads.seismicForceFpKNPerM >= 0.0,
            required = "Fp calculée",
            provided = "${Checks.format(loads.seismicForceFpKNPerM)} kN/ml",
            message = "La force sismique n'a pas pu être évaluée."
        )
        checks += Checks.boolean(
            code = "PA11.3_MAINCOURANTE",
            label = "Surcharge horizontale de main courante",
            reference = "DTR BC 2.2",
            condition = loads.designHorizontalQKNPerM >= input.handrailLoadKNPerM - 1e-9,
            required = "Q ≥ ${Checks.format(input.handrailLoadKNPerM)} kN/ml",
            provided = "Q = ${Checks.format(loads.designHorizontalQKNPerM)} kN/ml",
            message = "La surcharge de main courante n'est pas couverte."
        )
        checks += Checks.info(
            code = "PA11.4_FLEXION",
            label = "Flexion composée à l'encastrement",
            reference = "BAEL 91 révisé 99, A.4.4",
            value = "${governingResult.state.label} : M = ${Checks.format(governingResult.momentKNm)} kN.m, " +
                "N = ${Checks.format(governingResult.axialKN)} kN, As,calcul = " +
                "${Checks.format(governingResult.tensionSteelCm2)} cm²/ml."
        )
        checks += Checks.boolean(
            code = "PA11.5_NONFRAGILITE",
            label = "Condition de non-fragilité",
            reference = "BAEL 91 révisé 99, A.4.2.1",
            condition = mainSteel.providedAsCm2PerM + 1e-9 >= minimumSteel,
            required = "As ≥ ${Checks.format(minimumSteel)} cm²/ml",
            provided = "As = ${Checks.format(mainSteel.providedAsCm2PerM)} cm²/ml",
            message = "La section d'acier adoptée est inférieure au minimum de non-fragilité."
        )
        checks += mainSteel.check
        checks += distributionSteel.check

        val notes = mutableListOf<String>()
        notes += "Acrotère calculé pour une bande linéaire de 1,00 ml, encastrée à la base du plancher terrasse."
        notes += "Le contour développé inclut les deux faces du fût et la sous-face du becquet."
        notes += "La situation accidentelle sismique est comparée à la situation durable ELU ; " +
            "le ferraillage le plus défavorable est retenu."
        notes += durable.notes
        notes += accidental.notes

        return ParapetDesignResult(
            geometry = geometry,
            loads = loads,
            durableResult = durable,
            accidentalResult = accidental,
            governingSituation = governing,
            mainSteel = mainSteel,
            distributionSteel = distributionSteel,
            checks = checks,
            notes = notes.distinct()
        )
    }

    private fun designMesh(
        zone: String,
        requiredAsCm2PerM: Double,
        calculatedAsCm2PerM: Double,
        minimumAsCm2PerM: Double,
        momentKNm: Double,
        axialKN: Double,
        input: ParapetDesignInput
    ): ParapetMeshDesign {
        val candidate = selectMesh(
            requiredAsCm2PerM = requiredAsCm2PerM,
            preferredDiameterMm = input.barDiameterMm,
            maximumSpacingMeters = input.maximumSpacingMeters
        )
        val provided = candidate.areaCm2PerM
        val sufficient = provided + 1e-9 >= requiredAsCm2PerM
        val check = Checks.boolean(
            code = if (zone.startsWith("Nappe principale")) "PA11.6_PRINCIPAL" else "PA11.7_REPARTITION",
            label = "$zone ${candidate.label}",
            reference = "BAEL 91 révisé 99, A.4.2",
            condition = sufficient,
            required = "As ≥ ${Checks.format(requiredAsCm2PerM)} cm²/ml",
            provided = "As = ${Checks.format(provided)} cm²/ml",
            message = "Le maillage ${candidate.label} ne couvre pas la section requise."
        )
        return ParapetMeshDesign(
            zone = zone,
            designMomentKNm = momentKNm,
            axialKN = axialKN,
            calculatedAsCm2PerM = calculatedAsCm2PerM,
            minimumAsCm2PerM = minimumAsCm2PerM,
            requiredAsCm2PerM = requiredAsCm2PerM,
            barDiameterMm = candidate.diameterMm,
            spacingMeters = candidate.spacingMeters,
            providedAsCm2PerM = provided,
            label = candidate.label,
            status = if (sufficient) CheckStatus.OK else CheckStatus.FAILED,
            check = check
        )
    }

    private data class MeshCandidate(
        val diameterMm: Int,
        val spacingMeters: Double,
        val areaCm2PerM: Double
    ) {
        val label: String
            get() = "T$diameterMm e=${Checks.format(spacingMeters * 100.0, 0)} cm"
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
