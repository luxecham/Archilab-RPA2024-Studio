package com.archilab.rpa2024.engine.slabs

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.SlabStripTopology
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Localisation d'un lit d'armatures dans la poutrelle. */
enum class SlabBarZone(val label: String) {
    SPAN_BOTTOM("Travée (armatures inférieures)"),
    SUPPORT_TOP("Appui (chapeaux supérieurs)"),
    CANTILEVER_TOP("Console (armatures supérieures)")
}

/** Lits de barres commerciales selectionnes automatiquement (ex. 3T12). */
data class CommercialBarGroup(val count: Int, val diameterMm: Int) {
    init {
        require(count >= 2) { "Un lit comporte au moins deux barres." }
        require(diameterMm > 0) { "Diametre de barre invalide." }
    }

    /** Section reelle du lit en cm2 : pi.D2/4 avec D en cm. */
    val areaCm2: Double
        get() = count * PI * (diameterMm / 10.0) * (diameterMm / 10.0) / 4.0

    val label: String get() = "${count}T$diameterMm"

    /** Largeur minimale du lit : diametres + espacements de 2,5 cm. */
    val minimumWidthMm: Double get() = count * diameterMm + (count - 1) * 25.0
}

/** Charges lineiques par bande de largeur b, en kN/m. */
data class SlabLoadSummary(
    val permanentLoadKNPerM2: Double,
    val variableLoadKNPerM2: Double,
    val permanentBeforeFinishesKNPerM2: Double,
    val bandWidthMeters: Double,
    val permanentLineLoadKNPerM: Double,
    val variableLineLoadKNPerM: Double,
    val permanentBeforeFinishesLineLoadKNPerM: Double,
    val ultimateLineLoadKNPerM: Double,
    val serviceLineLoadKNPerM: Double
)

/** Point de discretisation d'une travée (sortie type Koka). */
data class SlabAnalysisPoint(
    val xMeters: Double,
    val momentUltimateKNm: Double,
    val momentServiceKNm: Double,
    val shearUltimateKN: Double,
    val shearServiceKN: Double
)

/** Verification des contraintes ELS d'une section ferraillee. */
data class SlabServiceStressCheck(
    val location: String,
    val momentKNm: Double,
    val neutralAxisMeters: Double,
    val inertiaM4: Double,
    val sigmaConcreteMPa: Double,
    val sigmaSteelMPa: Double,
    val sigmaConcreteLimitMPa: Double,
    val sigmaSteelLimitMPa: Double,
    val status: CheckStatus,
    val message: String = ""
)

/** Ferraillage longitudinal d'une section (travée, appui ou console). */
data class SlabReinforcementDesign(
    val zone: SlabBarZone,
    val designMomentKNm: Double,
    val calculatedAsCm2: Double,
    val minAsCm2: Double,
    val requiredAsCm2: Double,
    val bars: CommercialBarGroup,
    val providedAsCm2: Double,
    val resistingMomentKNm: Double,
    val anchorageLengthMeters: Double,
    val utilization: Double,
    val status: CheckStatus,
    val message: String = ""
)

/** Resultat complet d'une travée continue. */
data class SlabSpanResult(
    val index: Int,
    val lengthMeters: Double,
    val leftMomentUltimateKNm: Double,
    val rightMomentUltimateKNm: Double,
    val leftMomentServiceKNm: Double,
    val rightMomentServiceKNm: Double,
    val maxSpanMomentUltimateKNm: Double,
    val maxSpanMomentServiceKNm: Double,
    val maxSpanMomentAbscissaMeters: Double,
    val maxShearUltimateKN: Double,
    val leftReactionUltimateKN: Double,
    val rightReactionUltimateKN: Double,
    val points: List<SlabAnalysisPoint>,
    val reinforcement: SlabReinforcementDesign,
    val serviceStress: SlabServiceStressCheck,
    val checks: List<DesignCheck>
)

/** Resultat d'un appui continu ou de rive. */
data class SlabSupportResult(
    val index: Int,
    val momentUltimateKNm: Double,
    val momentServiceKNm: Double,
    val reinforcement: SlabReinforcementDesign,
    val serviceStress: SlabServiceStressCheck,
    val checks: List<DesignCheck>
)

/** Resultat d'une console eventuelle. */
data class SlabCantileverResult(
    val side: String,
    val lengthMeters: Double,
    val momentUltimateKNm: Double,
    val momentServiceKNm: Double,
    val shearUltimateKN: Double,
    val points: List<SlabAnalysisPoint>,
    val reinforcement: SlabReinforcementDesign,
    val serviceStress: SlabServiceStressCheck,
    val checks: List<DesignCheck>
)

/** Verification des armatures transversales de nervure. */
data class SlabShearDesign(
    val spanIndex: Int,
    val shearUltimateKN: Double,
    val tauMPa: Double,
    val tauLimitMPa: Double,
    val stirrupDiameterMm: Int,
    val stirrupAreaCm2: Double,
    val spacingMeters: Double,
    val status: CheckStatus,
    val message: String = ""
)

/** Donnees d'entree du solveur de poutrelles continues. */
data class ContinuousSlabInput(
    val topology: SlabStripTopology,
    val permanentLoadKNPerM2: Double,
    val variableLoadKNPerM2: Double,
    val materials: MaterialConfig = MaterialConfig(),
    val concreteCoverCm: Double = 2.0,
    val permanentBeforeFinishesKNPerM2: Double = 0.0,
    val leftCantileverMeters: Double = topology.leftCantileverMeters,
    val rightCantileverMeters: Double = topology.rightCantileverMeters,
    val pointsPerSpan: Int = 21,
    val steelStressLimitMPa: Double? = null,
    val deflectionSteelStressLimitMPa: Double? = null
)

/** Synthese complete de l'etude d'un plancher a corps creux (chapitre 8). */
data class ContinuousSlabResult(
    val topology: SlabStripTopology,
    val loads: SlabLoadSummary,
    val effectiveDepthMeters: Double,
    val predimensionCheck: DesignCheck,
    val spans: List<SlabSpanResult>,
    val supports: List<SlabSupportResult>,
    val cantilevers: List<SlabCantileverResult>,
    val shearDesigns: List<SlabShearDesign>,
    val deflection: TBeamDeflectionResult,
    val checks: List<DesignCheck>,
    val warnings: List<String> = emptyList()
) {
    val spanCount: Int get() = spans.size

    val status: CheckStatus
        get() = CheckStatus.worst(checks.map { it.status })

    val maxUltimateMomentKNm: Double
        get() = spans.maxOfOrNull { it.maxSpanMomentUltimateKNm } ?: 0.0

    val maxSupportMomentKNm: Double
        get() = supports.minOfOrNull { it.momentUltimateKNm }?.let { abs(it) } ?: 0.0

    /** Rapport de chapitre directement injectable dans la synthese du joint. */
    fun toChapterReport(): ChapterReport {
        val elements = spans.map { span ->
            ElementCheckReport(
                elementType = "Poutrelle continue",
                name = "Travée ${span.index + 1}",
                geometry = "L=${Checks.format(span.lengthMeters)} m, " +
                    "${Checks.format(topology.flangeWidthBCm)}x${Checks.format(topology.totalThicknessHtCm)} cm",
                checks = span.checks
            )
        } + supports.map { support ->
            ElementCheckReport(
                elementType = "Appui",
                name = "Appui ${support.index}",
                geometry = "M=${Checks.format(support.momentUltimateKNm)} kN.m",
                checks = support.checks
            )
        } + cantilevers.map { cantilever ->
            ElementCheckReport(
                elementType = "Console",
                name = cantilever.side,
                geometry = "L=${Checks.format(cantilever.lengthMeters)} m",
                checks = cantilever.checks
            )
        }

        val lines = buildList {
            add(
                "Plancher ${topology.geometryLabel} - ${spanCount} travée(s) continue(s) : " +
                    spans.joinToString(" + ") { Checks.format(it.lengthMeters) } + " m."
            )
            spans.forEach { span ->
                add(
                    "Travée ${span.index + 1} : Mu,max = ${Checks.format(span.maxSpanMomentUltimateKNm)} kN.m, " +
                        "armatures ${span.reinforcement.bars.label} (${Checks.format(span.reinforcement.providedAsCm2)} cm²), " +
                        "Mr = ${Checks.format(span.reinforcement.resistingMomentKNm)} kN.m."
                )
            }
            supports.forEach { support ->
                add(
                    "Appui ${support.index} : Mu = ${Checks.format(support.momentUltimateKNm)} kN.m, " +
                        "armatures ${support.reinforcement.bars.label} (${Checks.format(support.reinforcement.providedAsCm2)} cm²), " +
                        "Mr = ${Checks.format(support.reinforcement.resistingMomentKNm)} kN.m."
                )
            }
            add(
                "Flèche nuisible Δft = ${Checks.format(deflection.deltaFtMeters * 1000.0, 2)} mm " +
                    "pour une flèche admissible de ${Checks.format(deflection.allowableMeters * 1000.0, 2)} mm."
            )
        }

        return ChapterReport(
            number = 8,
            title = "Étude des planchers à corps creux - poutrelles continues",
            summary = "Calcul hyperstatique par la méthode des trois moments, ferraillage BAEL 91 révisé 99 " +
                "des sections en Té et vérification de la flèche.",
            checks = checks,
            elements = elements,
            lines = lines
        )
    }
}

/**
 * Solveur generique de poutrelles continues en section en Te, remplacant nativement
 * le logiciel SOCOTEC Koka. Le nombre de travées, leurs portees et les consoles sont
 * lus dans la topologie ETABS : aucun parametre geometrique n'est code en dur.
 *
 * Etapes :
 * 1. predimensionnement ht >= Lmax,nu / 22.5 ;
 * 2. efforts par la methode des trois moments sous ELU (1.35G + 1.5Q) et ELS (G + Q) ;
 * 3. ferraillage longitudinal en Te (table comprimee / nervure) et sur appuis (nervure) ;
 * 4. verification ELS des contraintes avec n = 15 ;
 * 5. armatures transversales de nervure ;
 * 6. fiche de fleche BAEL 91 rev. 99 de la travée la plus longue.
 */
class ContinuousRibbedSlabSolver {

    fun solve(input: ContinuousSlabInput): ContinuousSlabResult {
        validate(input)
        val topology = input.topology
        val warnings = mutableListOf<String>()

        val spans = topology.continuousSpansMeters.toDoubleArray()
        val spanCount = spans.size
        val bandWidth = topology.flangeWidthBCm / 100.0
        val bFlange = topology.flangeWidthBCm / 100.0
        val solid = topology.isSolidSlab
        val bWeb = if (solid) bFlange else topology.ribWidthB0Cm / 100.0
        val totalHeight = topology.totalThicknessHtCm / 100.0
        val slabThickness = if (solid) totalHeight else topology.compressionSlabH0Cm / 100.0
        val effectiveDepth = (topology.totalThicknessHtCm - input.concreteCoverCm) / 100.0
        require(effectiveDepth > 0.0) { "Hauteur utile négative : vérifier l'enrobage." }

        val materials = input.materials
        val fbuMPa = materials.fbuDurableMPa
        val fsuMPa = materials.sigmaSteelEluMPa
        val ft28MPa = materials.ft28MPa
        val feMPa = materials.feMPa
        val steelLimit = input.steelStressLimitMPa ?: (0.8 * feMPa)
        val deflectionSteelLimit = input.deflectionSteelStressLimitMPa ?: steelLimit

        val ultimateLineLoad = 1.35 * input.permanentLoadKNPerM2 * bandWidth +
            1.5 * input.variableLoadKNPerM2 * bandWidth
        val serviceLineLoad = (input.permanentLoadKNPerM2 + input.variableLoadKNPerM2) * bandWidth
        val permanentLineLoad = input.permanentLoadKNPerM2 * bandWidth
        val permanentBeforeFinishesLineLoad = input.permanentBeforeFinishesKNPerM2 * bandWidth
        val variableLineLoad = input.variableLoadKNPerM2 * bandWidth

        val loads = SlabLoadSummary(
            permanentLoadKNPerM2 = input.permanentLoadKNPerM2,
            variableLoadKNPerM2 = input.variableLoadKNPerM2,
            permanentBeforeFinishesKNPerM2 = input.permanentBeforeFinishesKNPerM2,
            bandWidthMeters = bandWidth,
            permanentLineLoadKNPerM = permanentLineLoad,
            variableLineLoadKNPerM = variableLineLoad,
            permanentBeforeFinishesLineLoadKNPerM = permanentBeforeFinishesLineLoad,
            ultimateLineLoadKNPerM = ultimateLineLoad,
            serviceLineLoadKNPerM = serviceLineLoad
        )

        val predimension = predimensionCheck(topology, spans.max())

        val leftCantilever = input.leftCantileverMeters.coerceAtLeast(0.0)
        val rightCantilever = input.rightCantileverMeters.coerceAtLeast(0.0)
        val leftMomentUltimate = if (leftCantilever > 0.0) -ultimateLineLoad * leftCantilever * leftCantilever / 2.0 else 0.0
        val rightMomentUltimate = if (rightCantilever > 0.0) -ultimateLineLoad * rightCantilever * rightCantilever / 2.0 else 0.0
        val leftMomentService = if (leftCantilever > 0.0) -serviceLineLoad * leftCantilever * leftCantilever / 2.0 else 0.0
        val rightMomentService = if (rightCantilever > 0.0) -serviceLineLoad * rightCantilever * rightCantilever / 2.0 else 0.0

        val supportMomentsUltimate = solveSupportMoments(
            spans,
            ultimateLineLoad,
            leftMomentUltimate,
            rightMomentUltimate
        )
        val supportMomentsService = solveSupportMoments(
            spans,
            serviceLineLoad,
            leftMomentService,
            rightMomentService
        )

        // --- Travées : sollicitations, ferraillage et ELS --------------------------------
        val spanResults = (0 until spanCount).map { index ->
            val length = spans[index]
            val mwU = supportMomentsUltimate[index]
            val meU = supportMomentsUltimate[index + 1]
            val mwS = supportMomentsService[index]
            val meS = supportMomentsService[index + 1]
            val points = discretize(
                length = length,
                leftMomentUltimate = mwU,
                rightMomentUltimate = meU,
                leftMomentService = mwS,
                rightMomentService = meS,
                ultimateLineLoad = ultimateLineLoad,
                serviceLineLoad = serviceLineLoad,
                pointCount = input.pointsPerSpan
            )
            val maxPointU = points.maxBy { it.momentUltimateKNm }
            val maxPointS = points.maxBy { it.momentServiceKNm }
            val maxSpanMomentU = max(0.0, maxPointU.momentUltimateKNm)
            val maxSpanMomentS = max(0.0, maxPointS.momentServiceKNm)
            val shearU = max(abs(points.first().shearUltimateKN), abs(points.last().shearUltimateKN))

            val rawDesign = designUltimate(
                momentKNm = maxSpanMomentU,
                isTee = !solid,
                bMeters = bFlange,
                b0Meters = bWeb,
                h0Meters = slabThickness,
                totalHeightMeters = totalHeight,
                effectiveDepthMeters = effectiveDepth,
                fbuMPa = fbuMPa,
                fsuMPa = fsuMPa,
                ft28MPa = ft28MPa,
                feMPa = feMPa
            )
            val selected = selectBars(
                requiredAsCm2 = rawDesign.requiredAsCm2,
                webWidthCm = topology.ribWidthB0Cm,
                zone = SlabBarZone.SPAN_BOTTOM
            )
            val resistingMoment = resistingMoment(
                steelAreaCm2 = selected.areaCm2,
                isTee = !solid,
                bMeters = bFlange,
                b0Meters = bWeb,
                h0Meters = slabThickness,
                effectiveDepthMeters = effectiveDepth,
                fbuMPa = fbuMPa,
                fsuMPa = fsuMPa
            )
            val reinforcement = buildReinforcement(
                zone = SlabBarZone.SPAN_BOTTOM,
                designMomentKNm = maxSpanMomentU,
                rawDesign = rawDesign,
                selected = selected,
                resistingMomentKNm = resistingMoment,
                effectiveDepthMeters = effectiveDepth,
                fsuMPa = fsuMPa,
                ft28MPa = ft28MPa,
                feMPa = feMPa
            )
            val serviceStress = serviceStressCheck(
                location = "Travée ${index + 1}",
                momentServiceKNm = maxSpanMomentS,
                isTee = !solid,
                bMeters = bFlange,
                b0Meters = bWeb,
                h0Meters = slabThickness,
                effectiveDepthMeters = effectiveDepth,
                steelAreaCm2 = selected.areaCm2,
                concreteLimitMPa = 0.6 * materials.fc28MPa,
                steelLimitMPa = steelLimit
            )
            val checks = listOf(
                bendingCheck("ELU_TR$index", "Flexion ELU travée ${index + 1}", resistingMoment, maxSpanMomentU),
                Checks.boolean(
                    code = "ELS_TR$index",
                    label = "Contraintes ELS travée ${index + 1}",
                    reference = "BAEL 91 révisé 99, A.4.5.2",
                    condition = serviceStress.status != CheckStatus.FAILED,
                    required = "σbc ≤ ${Checks.format(0.6 * materials.fc28MPa, 2)} MPa et σst ≤ ${Checks.format(steelLimit, 1)} MPa",
                    provided = "σbc = ${Checks.format(serviceStress.sigmaConcreteMPa, 2)} MPa, " +
                        "σst = ${Checks.format(serviceStress.sigmaSteelMPa, 1)} MPa",
                    message = serviceStress.message
                )
            )
            SlabSpanResult(
                index = index,
                lengthMeters = length,
                leftMomentUltimateKNm = mwU,
                rightMomentUltimateKNm = meU,
                leftMomentServiceKNm = mwS,
                rightMomentServiceKNm = meS,
                maxSpanMomentUltimateKNm = maxSpanMomentU,
                maxSpanMomentServiceKNm = maxSpanMomentS,
                maxSpanMomentAbscissaMeters = maxPointU.xMeters,
                maxShearUltimateKN = shearU,
                leftReactionUltimateKN = points.first().shearUltimateKN,
                rightReactionUltimateKN = -points.last().shearUltimateKN,
                points = points,
                reinforcement = reinforcement,
                serviceStress = serviceStress,
                checks = checks
            )
        }

        // --- Appuis : chapeaux superieurs en section rectangulaire b0 -------------------
        val supportResults = (0..spanCount).map { index ->
            val momentU = supportMomentsUltimate[index]
            val momentS = supportMomentsService[index]
            val magnitudeU = abs(momentU)
            val rawDesign = designUltimate(
                momentKNm = magnitudeU,
                isTee = false,
                bMeters = bWeb,
                b0Meters = bWeb,
                h0Meters = 0.0,
                totalHeightMeters = totalHeight,
                effectiveDepthMeters = effectiveDepth,
                fbuMPa = fbuMPa,
                fsuMPa = fsuMPa,
                ft28MPa = ft28MPa,
                feMPa = feMPa
            )
            val selected = selectBars(
                requiredAsCm2 = rawDesign.requiredAsCm2,
                webWidthCm = topology.ribWidthB0Cm,
                zone = SlabBarZone.SUPPORT_TOP
            )
            val resistingMoment = resistingMoment(
                steelAreaCm2 = selected.areaCm2,
                isTee = false,
                bMeters = bWeb,
                b0Meters = bWeb,
                h0Meters = 0.0,
                effectiveDepthMeters = effectiveDepth,
                fbuMPa = fbuMPa,
                fsuMPa = fsuMPa
            )
            val reinforcement = buildReinforcement(
                zone = SlabBarZone.SUPPORT_TOP,
                designMomentKNm = magnitudeU,
                rawDesign = rawDesign,
                selected = selected,
                resistingMomentKNm = resistingMoment,
                effectiveDepthMeters = effectiveDepth,
                fsuMPa = fsuMPa,
                ft28MPa = ft28MPa,
                feMPa = feMPa
            )
            val serviceStress = serviceStressCheck(
                location = "Appui $index",
                momentServiceKNm = abs(momentS),
                isTee = false,
                bMeters = bWeb,
                b0Meters = bWeb,
                h0Meters = 0.0,
                effectiveDepthMeters = effectiveDepth,
                steelAreaCm2 = selected.areaCm2,
                concreteLimitMPa = 0.6 * materials.fc28MPa,
                steelLimitMPa = steelLimit
            )
            val checks = listOf(
                bendingCheck("ELU_AP$index", "Flexion ELU appui $index", resistingMoment, magnitudeU),
                Checks.boolean(
                    code = "ELS_AP$index",
                    label = "Contraintes ELS appui $index",
                    reference = "BAEL 91 révisé 99, A.4.5.2",
                    condition = serviceStress.status != CheckStatus.FAILED,
                    required = "σbc ≤ ${Checks.format(0.6 * materials.fc28MPa, 2)} MPa et σst ≤ ${Checks.format(steelLimit, 1)} MPa",
                    provided = "σbc = ${Checks.format(serviceStress.sigmaConcreteMPa, 2)} MPa, " +
                        "σst = ${Checks.format(serviceStress.sigmaSteelMPa, 1)} MPa",
                    message = serviceStress.message
                )
            )
            SlabSupportResult(
                index = index,
                momentUltimateKNm = momentU,
                momentServiceKNm = momentS,
                reinforcement = reinforcement,
                serviceStress = serviceStress,
                checks = checks
            )
        }

        // --- Consoles eventuelles --------------------------------------------------------
        val cantilevers = buildList {
            if (leftCantilever > 0.0) {
                add(
                    solveCantilever(
                        side = "Console gauche",
                        length = leftCantilever,
                        ultimateLineLoad = ultimateLineLoad,
                        serviceLineLoad = serviceLineLoad,
                        solid = solid,
                        bFlange = bFlange,
                        bWeb = bWeb,
                        slabThickness = slabThickness,
                        totalHeight = totalHeight,
                        effectiveDepth = effectiveDepth,
                        fbuMPa = fbuMPa,
                        fsuMPa = fsuMPa,
                        ft28MPa = ft28MPa,
                        feMPa = feMPa,
                        fc28MPa = materials.fc28MPa,
                        steelLimitMPa = steelLimit,
                        ribWidthCm = topology.ribWidthB0Cm
                    )
                )
            }
            if (rightCantilever > 0.0) {
                add(
                    solveCantilever(
                        side = "Console droite",
                        length = rightCantilever,
                        ultimateLineLoad = ultimateLineLoad,
                        serviceLineLoad = serviceLineLoad,
                        solid = solid,
                        bFlange = bFlange,
                        bWeb = bWeb,
                        slabThickness = slabThickness,
                        totalHeight = totalHeight,
                        effectiveDepth = effectiveDepth,
                        fbuMPa = fbuMPa,
                        fsuMPa = fsuMPa,
                        ft28MPa = ft28MPa,
                        feMPa = feMPa,
                        fc28MPa = materials.fc28MPa,
                        steelLimitMPa = steelLimit,
                        ribWidthCm = topology.ribWidthB0Cm
                    )
                )
            }
        }

        // --- Armatures transversales de nervure -----------------------------------------
        val shearDesigns = spanResults.map { span ->
            val adjacentTop = spanResults.getOrNull(span.index + 1)?.reinforcement?.bars?.diameterMm ?: 12
            val adjacentBottom = spanResults.getOrNull(span.index - 1)?.reinforcement?.bars?.diameterMm ?: 12
            val longitudinalDiameter = maxOf(span.reinforcement.bars.diameterMm, adjacentTop, adjacentBottom)
            shearDesign(
                spanIndex = span.index,
                shearUltimateKN = span.maxShearUltimateKN,
                bWebMeters = bWeb,
                totalHeightMeters = totalHeight,
                effectiveDepthMeters = effectiveDepth,
                fc28MPa = materials.fc28MPa,
                ft28MPa = ft28MPa,
                feMPa = feMPa,
                gammaB = materials.gammaBElu,
                longitudinalDiameterMm = longitudinalDiameter
            )
        }

        // --- Fleche BAEL de la travée la plus longue -----------------------------------
        val longestIndex = spans.indices.maxBy { spans[it] }
        val longestSpan = spans[longestIndex]
        val permanentMoments = supportMomentsWithCantilevers(
            spans, permanentBeforeFinishesLineLoad, leftCantilever, rightCantilever
        )
        val totalPermanentMoments = supportMomentsWithCantilevers(
            spans, permanentLineLoad, leftCantilever, rightCantilever
        )
        val totalServiceMoments = supportMomentsWithCantilevers(
            spans, serviceLineLoad, leftCantilever, rightCantilever
        )
        val momentBeforeFinishes = maxSpanMoment(
            longestSpan,
            permanentMoments[longestIndex],
            permanentMoments[longestIndex + 1],
            permanentBeforeFinishesLineLoad
        )
        val momentPermanent = maxSpanMoment(
            longestSpan,
            totalPermanentMoments[longestIndex],
            totalPermanentMoments[longestIndex + 1],
            permanentLineLoad
        )
        val momentTotal = maxSpanMoment(
            longestSpan,
            totalServiceMoments[longestIndex],
            totalServiceMoments[longestIndex + 1],
            serviceLineLoad
        )
        val deflection = TBeamDeflectionCalculator().calculate(
            TBeamDeflectionInput(
                flangeWidthMeters = bFlange,
                webWidthMeters = bWeb,
                totalHeightMeters = totalHeight,
                slabThicknessMeters = slabThickness,
                effectiveDepthMeters = effectiveDepth,
                steelAreaCm2 = spanResults[longestIndex].reinforcement.providedAsCm2,
                spanMeters = longestSpan,
                momentBeforeFinishesKNm = momentBeforeFinishes,
                momentPermanentKNm = max(momentPermanent, momentBeforeFinishes),
                momentTotalServiceKNm = momentTotal,
                fc28MPa = materials.fc28MPa,
                ft28MPa = ft28MPa,
                eijMPa = materials.eijMPa,
                evjMPa = materials.evjMPa,
                feMPa = feMPa,
                concreteStressLimitMPa = 0.6 * materials.fc28MPa,
                steelStressLimitMPa = deflectionSteelLimit
            )
        )

        // --- Synthese -------------------------------------------------------------------
        val checks = buildList {
            add(predimension)
            spanResults.forEach { addAll(it.checks) }
            supportResults.forEach { addAll(it.checks) }
            cantilevers.forEach { addAll(it.checks) }
            shearDesigns.forEach { shear ->
                add(
                    Checks.boolean(
                        code = "TRANSO_S${shear.spanIndex}",
                        label = "Armatures transversales travée ${shear.spanIndex + 1}",
                        reference = "BAEL 91 révisé 99, A.5.1",
                        condition = shear.status == CheckStatus.OK,
                        required = "τu ≤ ${Checks.format(shear.tauLimitMPa, 2)} MPa et St ≥ 5 cm",
                        provided = "τu = ${Checks.format(shear.tauMPa, 2)} MPa, " +
                            "φt = ${shear.stirrupDiameterMm} mm, St = ${Checks.format(shear.spacingMeters * 100.0, 0)} cm",
                        failureStatus = shear.status,
                        message = shear.message
                    )
                )
            }
            addAll(deflection.checks)
        }

        if (topology.continuousSpansMeters.any { it <= 0.0 }) {
            warnings += "Une portée nulle ou négative a été ignorée dans la topologie."
        }
        if (!topology.isThicknessConsistent) {
            warnings += "L'épaisseur totale ${Checks.format(topology.totalThicknessHtCm)} cm ne correspond pas à " +
                "h0 + hauteur de corps creux."
        }
        if (deflection.messages.isNotEmpty()) warnings += deflection.messages

        return ContinuousSlabResult(
            topology = topology,
            loads = loads,
            effectiveDepthMeters = effectiveDepth,
            predimensionCheck = predimension,
            spans = spanResults,
            supports = supportResults,
            cantilevers = cantilevers,
            shearDesigns = shearDesigns,
            deflection = deflection,
            checks = checks,
            warnings = warnings
        )
    }

    private fun validate(input: ContinuousSlabInput) {
        val topology = input.topology
        require(topology.continuousSpansMeters.isNotEmpty()) { "La poutrelle doit comporter au moins une travée." }
        require(topology.continuousSpansMeters.all { it > 0.0 }) { "Toutes les portées doivent être positives." }
        require(topology.totalThicknessHtCm > 0.0) { "Hauteur totale du plancher invalide." }
        require(topology.flangeWidthBCm > 0.0) { "Largeur de table invalide." }
        require(topology.ribWidthB0Cm > 0.0) { "Largeur de nervure invalide." }
        require(!topology.isSolidSlab || topology.ribWidthB0Cm > 0.0) { "Largeur de nervure invalide." }
        require(topology.compressionSlabH0Cm >= 0.0) { "Épaisseur de table invalide." }
        require(topology.compressionSlabH0Cm < topology.totalThicknessHtCm) {
            "L'épaisseur de table doit être inférieure à la hauteur totale."
        }
        require(input.permanentLoadKNPerM2 >= 0.0 && input.variableLoadKNPerM2 >= 0.0) {
            "Les charges permanentes et d'exploitation doivent être positives."
        }
        require(input.pointsPerSpan >= 5) { "La discrétisation doit comporter au moins 5 points par travée." }
        require(input.concreteCoverCm > 0.0 && input.concreteCoverCm < topology.totalThicknessHtCm) {
            "Enrobage invalide."
        }
    }

    private fun predimensionCheck(topology: SlabStripTopology, maxSpanMeters: Double): DesignCheck {
        val minimumHeight = maxSpanMeters * 100.0 / 22.5
        val adopted = topology.totalThicknessHtCm
        val suggestions = listOf(16 to 4, 16 to 5, 20 to 4, 20 to 5, 25 to 5, 30 to 5)
            .filter { it.first + it.second >= minimumHeight }
            .minByOrNull { it.first + it.second }
            ?.let { "${it.first}+${it.second} cm" }
            ?: "étude spécifique"
        val message = if (adopted + 1e-6 < minimumHeight) {
            "Hauteur insuffisante : adopter au minimum $suggestions pour la portée " +
                "${Checks.format(maxSpanMeters)} m."
        } else {
            ""
        }
        return Checks.min(
            code = "PREDIM_PLANCHER",
            label = "Prédimensionnement de la hauteur totale",
            reference = "Pratique BAEL - plancher à corps creux, ht ≥ Lmax,nu/22.5",
            provided = adopted,
            minimum = minimumHeight,
            unit = "cm",
            decimals = 1,
            failureMessage = message
        )
    }

    /**
     * Methode des trois moments pour une poutre continue a n travées de longueurs quelconques.
     * Les moments de console eventuels fixent les rotations aux appuis de rive.
     */
    private fun solveSupportMoments(
        spans: DoubleArray,
        lineLoad: Double,
        leftEndMoment: Double,
        rightEndMoment: Double
    ): DoubleArray {
        val n = spans.size
        val moments = DoubleArray(n + 1)
        moments[0] = leftEndMoment
        moments[n] = rightEndMoment
        if (n == 1) return moments

        val count = n - 1
        val subDiagonal = DoubleArray(count)
        val diagonal = DoubleArray(count)
        val superDiagonal = DoubleArray(count)
        val rhs = DoubleArray(count)

        for (i in 1 until n) {
            val index = i - 1
            val leftLength = spans[i - 1]
            val rightLength = spans[i]
            subDiagonal[index] = if (i == 1) 0.0 else leftLength
            diagonal[index] = 2.0 * (leftLength + rightLength)
            superDiagonal[index] = if (i == n - 1) 0.0 else rightLength
            rhs[index] = -(lineLoad * leftLength * leftLength * leftLength +
                lineLoad * rightLength * rightLength * rightLength) / 4.0
            if (i == 1) rhs[index] -= moments[0] * leftLength
            if (i == n - 1) rhs[index] -= moments[n] * rightLength
        }

        // Elimination de Thomas.
        for (i in 1 until count) {
            if (abs(diagonal[i - 1]) < 1e-12) {
                throw IllegalStateException("Système des trois moments singulier : vérifier les portées.")
            }
            val factor = subDiagonal[i] / diagonal[i - 1]
            diagonal[i] -= factor * superDiagonal[i - 1]
            rhs[i] -= factor * rhs[i - 1]
        }
        val solution = DoubleArray(count)
        solution[count - 1] = rhs[count - 1] / diagonal[count - 1]
        for (i in count - 2 downTo 0) {
            solution[i] = (rhs[i] - superDiagonal[i] * solution[i + 1]) / diagonal[i]
        }
        for (i in 0 until count) moments[i + 1] = solution[i]
        return moments
    }

    private fun supportMomentsWithCantilevers(
        spans: DoubleArray,
        lineLoad: Double,
        leftCantileverMeters: Double,
        rightCantileverMeters: Double
    ): DoubleArray = solveSupportMoments(
        spans = spans,
        lineLoad = lineLoad,
        leftEndMoment = if (leftCantileverMeters > 0.0) {
            -lineLoad * leftCantileverMeters * leftCantileverMeters / 2.0
        } else {
            0.0
        },
        rightEndMoment = if (rightCantileverMeters > 0.0) {
            -lineLoad * rightCantileverMeters * rightCantileverMeters / 2.0
        } else {
            0.0
        }
    )

    private fun discretize(
        length: Double,
        leftMomentUltimate: Double,
        rightMomentUltimate: Double,
        leftMomentService: Double,
        rightMomentService: Double,
        ultimateLineLoad: Double,
        serviceLineLoad: Double,
        pointCount: Int
    ): List<SlabAnalysisPoint> {
        val steps = max(4, pointCount - 1)
        return (0..steps).map { index ->
            val x = length * index / steps
            SlabAnalysisPoint(
                xMeters = x,
                momentUltimateKNm = spanMoment(x, length, leftMomentUltimate, rightMomentUltimate, ultimateLineLoad),
                momentServiceKNm = spanMoment(x, length, leftMomentService, rightMomentService, serviceLineLoad),
                shearUltimateKN = spanShear(x, length, leftMomentUltimate, rightMomentUltimate, ultimateLineLoad),
                shearServiceKN = spanShear(x, length, leftMomentService, rightMomentService, serviceLineLoad)
            )
        }
    }

    private fun spanMoment(
        x: Double,
        length: Double,
        leftMoment: Double,
        rightMoment: Double,
        lineLoad: Double
    ): Double = leftMoment * (1.0 - x / length) + rightMoment * x / length +
        lineLoad * x * (length - x) / 2.0

    private fun spanShear(
        x: Double,
        length: Double,
        leftMoment: Double,
        rightMoment: Double,
        lineLoad: Double
    ): Double = (rightMoment - leftMoment) / length + lineLoad * (length / 2.0 - x)

    private fun maxSpanMoment(
        length: Double,
        leftMoment: Double,
        rightMoment: Double,
        lineLoad: Double
    ): Double {
        if (lineLoad <= 1e-12) return 0.0
        val abscissa = length / 2.0 + (rightMoment - leftMoment) / (lineLoad * length)
        return if (abscissa in 0.0..length) {
            max(0.0, spanMoment(abscissa, length, leftMoment, rightMoment, lineLoad))
        } else {
            maxOf(
                0.0,
                spanMoment(0.0, length, leftMoment, rightMoment, lineLoad),
                spanMoment(length, length, leftMoment, rightMoment, lineLoad),
                spanMoment(length / 2.0, length, leftMoment, rightMoment, lineLoad)
            )
        }
    }

    private data class RawSectionDesign(
        val calculatedAsCm2: Double,
        val minAsCm2: Double,
        val requiredAsCm2: Double,
        val mu: Double,
        val alpha: Double,
        val leverArmMeters: Double,
        val compressionSteelRequired: Boolean,
        val message: String
    )

    /**
     * Flexion simple a l'ELU avec diagramme rectangulaire simplifie.
     * En travée, si Mu <= Mtu l'axe neutre reste dans la table (section rectangulaire b) ;
     * sinon on resout la profondeur du bloc comprime dans la nervure (débords b - b0 compris).
     */
    private fun designUltimate(
        momentKNm: Double,
        isTee: Boolean,
        bMeters: Double,
        b0Meters: Double,
        h0Meters: Double,
        totalHeightMeters: Double,
        effectiveDepthMeters: Double,
        fbuMPa: Double,
        fsuMPa: Double,
        ft28MPa: Double,
        feMPa: Double
    ): RawSectionDesign {
        val fbu = fbuMPa * 1000.0
        val fsu = fsuMPa * 1000.0
        val d = effectiveDepthMeters
        val muLimit = 0.372
        val momentTable = if (isTee) bMeters * h0Meters * fbu * (d - h0Meters / 2.0) else 0.0

        val calculatedAsM2: Double
        val mu: Double
        val alpha: Double
        val leverArm: Double
        var compressionSteel = false
        var message = ""

        if (!isTee || momentKNm <= momentTable) {
            val reducedMoment = momentKNm / (bMeters * d * d * fbu)
            mu = reducedMoment
            val clamped = min(reducedMoment, muLimit)
            compressionSteel = reducedMoment > muLimit + 1e-9
            alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * clamped)))
            leverArm = d * (1.0 - 0.4 * alpha)
            calculatedAsM2 = momentKNm / (leverArm * fsu)
        } else {
            val flangeCapacity = (bMeters - b0Meters) * h0Meters * fbu
            val flangeTerm = flangeCapacity * (d - h0Meters / 2.0)
            val discriminant = d * d - 2.0 * (momentKNm - flangeTerm) / (b0Meters * fbu)
            if (discriminant <= 0.0) {
                compressionSteel = true
                message = "Section en Té sur-armée : armatures de compression nécessaires."
                val y = d
                alpha = y / (0.8 * d)
                leverArm = d - y / 2.0
                calculatedAsM2 = (b0Meters * y * fbu + flangeCapacity) / fsu
                mu = momentKNm / (b0Meters * d * d * fbu)
            } else {
                // y est la profondeur du bloc rectangulaire comprime dans la nervure.
                val y = d - sqrt(discriminant)
                alpha = (y / 0.8) / d
                calculatedAsM2 = (b0Meters * y * fbu + flangeCapacity) / fsu
                leverArm = if (calculatedAsM2 <= 0.0) 0.9 * d else momentKNm / (calculatedAsM2 * fsu)
                mu = momentKNm / (b0Meters * d * d * fbu)
                if (y > d) compressionSteel = true
            }
        }

        val asMinWeb = 0.23 * b0Meters * d * ft28MPa / feMPa
        val teeSection = ConcreteSectionMechanics.teeProperties(
            flangeWidthMeters = bMeters,
            webWidthMeters = b0Meters,
            totalHeightMeters = totalHeightMeters,
            slabThicknessMeters = if (isTee) h0Meters else totalHeightMeters
        )
        // Variante en Té : module I/v' rapporte a la hauteur utile (condition de non-fragilite).
        val asMinTee = 0.23 * teeSection.tensionSectionModulusM3 * ft28MPa / (feMPa * d)
        val asMin = max(asMinWeb, asMinTee)
        val calculated = max(0.0, calculatedAsM2)
        val required = max(calculated, asMin)

        return RawSectionDesign(
            calculatedAsCm2 = calculated * 10_000.0,
            minAsCm2 = asMin * 10_000.0,
            requiredAsCm2 = required * 10_000.0,
            mu = mu,
            alpha = alpha,
            leverArmMeters = leverArm,
            compressionSteelRequired = compressionSteel,
            message = message
        )
    }

    private fun selectBars(requiredAsCm2: Double, webWidthCm: Double, zone: SlabBarZone): CommercialBarGroup {
        val diameters = listOf(8, 10, 12, 14, 16, 20, 25)
        val maxCount = when {
            webWidthCm >= 18.0 -> 5
            webWidthCm >= 12.0 -> 4
            webWidthCm >= 8.0 -> 3
            else -> 2
        }
        val clearanceMm = webWidthCm * 10.0 - 30.0
        val preferredMinimum = if (zone == SlabBarZone.SPAN_BOTTOM) 10 else 8

        val candidates = buildList {
            for (count in 2..maxCount) {
                for (diameter in diameters) {
                    if (diameter < preferredMinimum) continue
                    val bars = CommercialBarGroup(count, diameter)
                    if (bars.minimumWidthMm <= clearanceMm) add(bars)
                }
            }
        }
        val fallback = buildList {
            for (count in 2..maxCount) {
                for (diameter in diameters) add(CommercialBarGroup(count, diameter))
            }
        }
        val pool = candidates.ifEmpty { fallback }
        val above = pool.filter { it.areaCm2 >= requiredAsCm2 - 1e-9 }
        return (above.ifEmpty { pool })
            .minWith(compareBy({ it.areaCm2 }, { it.count }, { it.diameterMm }))
    }

    /** Moment resistant de la section reellement ferraillee. */
    private fun resistingMoment(
        steelAreaCm2: Double,
        isTee: Boolean,
        bMeters: Double,
        b0Meters: Double,
        h0Meters: Double,
        effectiveDepthMeters: Double,
        fbuMPa: Double,
        fsuMPa: Double
    ): Double {
        val fbu = fbuMPa * 1000.0
        val fsu = fsuMPa * 1000.0
        val tension = steelAreaCm2 / 10_000.0 * fsu
        val d = effectiveDepthMeters
        if (!isTee) {
            val blockDepth = min(tension / (bMeters * fbu), d)
            return tension * (d - blockDepth / 2.0)
        }
        val tableCapacity = bMeters * h0Meters * fbu
        if (tension <= tableCapacity) {
            val blockDepth = tension / (bMeters * fbu)
            return tension * (d - blockDepth / 2.0)
        }
        val flangeCapacity = (bMeters - b0Meters) * h0Meters * fbu
        val webBlock = min(max(0.0, (tension - flangeCapacity) / (b0Meters * fbu)), d)
        return flangeCapacity * (d - h0Meters / 2.0) + b0Meters * webBlock * fbu * (d - webBlock / 2.0)
    }

    private fun buildReinforcement(
        zone: SlabBarZone,
        designMomentKNm: Double,
        rawDesign: RawSectionDesign,
        selected: CommercialBarGroup,
        resistingMomentKNm: Double,
        effectiveDepthMeters: Double,
        fsuMPa: Double,
        ft28MPa: Double,
        feMPa: Double
    ): SlabReinforcementDesign {
        val anchorage = (selected.diameterMm / 1000.0) / 4.0 * fsuMPa / (1.5 * ft28MPa)
        val utilization = if (resistingMomentKNm <= 0.0) Double.MAX_VALUE else designMomentKNm / resistingMomentKNm
        val momentOk = resistingMomentKNm + 1e-6 >= designMomentKNm
        val status = when {
            rawDesign.compressionSteelRequired -> CheckStatus.FAILED
            !momentOk -> CheckStatus.FAILED
            else -> CheckStatus.OK
        }
        val message = when {
            rawDesign.compressionSteelRequired -> rawDesign.message
            !momentOk -> "Moment résistant ${Checks.format(resistingMomentKNm)} kN.m < " +
                "moment sollicitant ${Checks.format(designMomentKNm)} kN.m."
            else -> ""
        }
        return SlabReinforcementDesign(
            zone = zone,
            designMomentKNm = designMomentKNm,
            calculatedAsCm2 = rawDesign.calculatedAsCm2,
            minAsCm2 = rawDesign.minAsCm2,
            requiredAsCm2 = rawDesign.requiredAsCm2,
            bars = selected,
            providedAsCm2 = selected.areaCm2,
            resistingMomentKNm = resistingMomentKNm,
            anchorageLengthMeters = anchorage,
            utilization = utilization,
            status = status,
            message = message
        )
    }

    private fun serviceStressCheck(
        location: String,
        momentServiceKNm: Double,
        isTee: Boolean,
        bMeters: Double,
        b0Meters: Double,
        h0Meters: Double,
        effectiveDepthMeters: Double,
        steelAreaCm2: Double,
        concreteLimitMPa: Double,
        steelLimitMPa: Double
    ): SlabServiceStressCheck {
        val cracked = ConcreteSectionMechanics.crackedSection(
            flangeWidthMeters = if (isTee) bMeters else b0Meters,
            webWidthMeters = b0Meters,
            slabThicknessMeters = if (isTee) h0Meters else 0.0,
            effectiveDepthMeters = effectiveDepthMeters,
            steelAreaM2 = steelAreaCm2 / 10_000.0
        )
        val sigmaConcrete = cracked.concreteStressMPa(momentServiceKNm)
        val sigmaSteel = cracked.steelStressMPa(momentServiceKNm, effectiveDepthMeters)
        val concreteOk = sigmaConcrete <= concreteLimitMPa + 1e-9
        val steelOk = sigmaSteel <= steelLimitMPa + 1e-9
        val status = if (concreteOk && steelOk) CheckStatus.OK else CheckStatus.FAILED
        val message = when {
            !concreteOk && !steelOk -> "Contraintes ELS dépassées en béton et en acier."
            !concreteOk -> "Contrainte béton ELS ${Checks.format(sigmaConcrete, 2)} MPa > " +
                "${Checks.format(concreteLimitMPa, 2)} MPa."
            !steelOk -> "Contrainte acier ELS ${Checks.format(sigmaSteel, 1)} MPa > " +
                "${Checks.format(steelLimitMPa, 1)} MPa."
            else -> ""
        }
        return SlabServiceStressCheck(
            location = location,
            momentKNm = momentServiceKNm,
            neutralAxisMeters = cracked.neutralAxisMeters,
            inertiaM4 = cracked.inertiaM4,
            sigmaConcreteMPa = sigmaConcrete,
            sigmaSteelMPa = sigmaSteel,
            sigmaConcreteLimitMPa = concreteLimitMPa,
            sigmaSteelLimitMPa = steelLimitMPa,
            status = status,
            message = message
        )
    }

    private fun bendingCheck(code: String, label: String, resistingMomentKNm: Double, designMomentKNm: Double): DesignCheck =
        Checks.boolean(
            code = code,
            label = label,
            reference = "BAEL 91 révisé 99, A.4.3",
            condition = resistingMomentKNm + 1e-6 >= designMomentKNm,
            required = "Mr ≥ Mu = ${Checks.format(designMomentKNm)} kN.m",
            provided = "Mr = ${Checks.format(resistingMomentKNm)} kN.m",
            message = "$label non vérifiée : Mr = ${Checks.format(resistingMomentKNm)} kN.m < " +
                "Mu = ${Checks.format(designMomentKNm)} kN.m."
        )

    private fun shearDesign(
        spanIndex: Int,
        shearUltimateKN: Double,
        bWebMeters: Double,
        totalHeightMeters: Double,
        effectiveDepthMeters: Double,
        fc28MPa: Double,
        ft28MPa: Double,
        feMPa: Double,
        gammaB: Double,
        longitudinalDiameterMm: Int
    ): SlabShearDesign {
        val b0Mm = bWebMeters * 1000.0
        val heightMm = totalHeightMeters * 1000.0
        val limitDiameter = minOf(heightMm / 35.0, b0Mm / 10.0, longitudinalDiameterMm.toDouble())
        val stirrupDiameter = listOf(6, 8, 10, 12).lastOrNull { it <= limitDiameter } ?: 6
        val stirrupAreaCm2 = 2.0 * PI * (stirrupDiameter / 10.0) * (stirrupDiameter / 10.0) / 4.0
        val tauMPa = shearUltimateKN / (bWebMeters * effectiveDepthMeters) / 1000.0
        val tauLimitMPa = min(0.2 * fc28MPa / gammaB, 4.0)
        val z = 0.9 * effectiveDepthMeters
        val concreteShearKN = 0.3 * ft28MPa * 1000.0 * bWebMeters * effectiveDepthMeters
        val fsuMPa = feMPa / 1.15
        val atM2 = stirrupAreaCm2 / 10_000.0
        val calculatedSpacing = if (shearUltimateKN > concreteShearKN + 1e-9) {
            atM2 * fsuMPa * 1000.0 * z / (shearUltimateKN - concreteShearKN)
        } else {
            Double.MAX_VALUE
        }
        val maximumSpacing = min(0.9 * effectiveDepthMeters, 0.40)
        val rawSpacing = min(maximumSpacing, calculatedSpacing)
        val spacing = if (rawSpacing == Double.MAX_VALUE) {
            floor(maximumSpacing / 0.05) * 0.05
        } else {
            max(0.05, floor(rawSpacing / 0.05) * 0.05)
        }
        val shearOk = tauMPa <= tauLimitMPa + 1e-9
        val spacingOk = spacing >= 0.05 - 1e-9 && calculatedSpacing >= 0.05
        val status = if (shearOk && spacingOk) CheckStatus.OK else CheckStatus.FAILED
        val message = when {
            !shearOk -> "Cisaillement excessif dans la nervure : τu = ${Checks.format(tauMPa, 2)} MPa > " +
                "${Checks.format(tauLimitMPa, 2)} MPa."
            !spacingOk -> "Espacement d'étriers insuffisant : augmenter le diamètre ou la section des étriers."
            else -> ""
        }
        return SlabShearDesign(
            spanIndex = spanIndex,
            shearUltimateKN = shearUltimateKN,
            tauMPa = tauMPa,
            tauLimitMPa = tauLimitMPa,
            stirrupDiameterMm = stirrupDiameter,
            stirrupAreaCm2 = stirrupAreaCm2,
            spacingMeters = spacing,
            status = status,
            message = message
        )
    }

    private fun solveCantilever(
        side: String,
        length: Double,
        ultimateLineLoad: Double,
        serviceLineLoad: Double,
        solid: Boolean,
        bFlange: Double,
        bWeb: Double,
        slabThickness: Double,
        totalHeight: Double,
        effectiveDepth: Double,
        fbuMPa: Double,
        fsuMPa: Double,
        ft28MPa: Double,
        feMPa: Double,
        fc28MPa: Double,
        steelLimitMPa: Double,
        ribWidthCm: Double
    ): SlabCantileverResult {
        val momentUltimate = ultimateLineLoad * length * length / 2.0
        val momentService = serviceLineLoad * length * length / 2.0
        val shear = ultimateLineLoad * length
        val pointCount = 11
        val points = (0..pointCount).map { index ->
            val x = length * index / pointCount
            val lever = length - x
            SlabAnalysisPoint(
                xMeters = x,
                momentUltimateKNm = -ultimateLineLoad * lever * lever / 2.0,
                momentServiceKNm = -serviceLineLoad * lever * lever / 2.0,
                shearUltimateKN = ultimateLineLoad * lever,
                shearServiceKN = serviceLineLoad * lever
            )
        }
        val rawDesign = designUltimate(
            momentKNm = momentUltimate,
            isTee = false,
            bMeters = bWeb,
            b0Meters = bWeb,
            h0Meters = 0.0,
            totalHeightMeters = totalHeight,
            effectiveDepthMeters = effectiveDepth,
            fbuMPa = fbuMPa,
            fsuMPa = fsuMPa,
            ft28MPa = ft28MPa,
            feMPa = feMPa
        )
        val selected = selectBars(rawDesign.requiredAsCm2, ribWidthCm, SlabBarZone.CANTILEVER_TOP)
        val resistingMoment = resistingMoment(
            steelAreaCm2 = selected.areaCm2,
            isTee = false,
            bMeters = bWeb,
            b0Meters = bWeb,
            h0Meters = 0.0,
            effectiveDepthMeters = effectiveDepth,
            fbuMPa = fbuMPa,
            fsuMPa = fsuMPa
        )
        val reinforcement = buildReinforcement(
            zone = SlabBarZone.CANTILEVER_TOP,
            designMomentKNm = momentUltimate,
            rawDesign = rawDesign,
            selected = selected,
            resistingMomentKNm = resistingMoment,
            effectiveDepthMeters = effectiveDepth,
            fsuMPa = fsuMPa,
            ft28MPa = ft28MPa,
            feMPa = feMPa
        )
        val serviceStress = serviceStressCheck(
            location = side,
            momentServiceKNm = momentService,
            isTee = false,
            bMeters = bWeb,
            b0Meters = bWeb,
            h0Meters = 0.0,
            effectiveDepthMeters = effectiveDepth,
            steelAreaCm2 = selected.areaCm2,
            concreteLimitMPa = 0.6 * fc28MPa,
            steelLimitMPa = steelLimitMPa
        )
        val checks = listOf(
            bendingCheck("ELU_CONS", "Flexion ELU $side", resistingMoment, momentUltimate),
            Checks.boolean(
                code = "ELS_CONS",
                label = "Contraintes ELS $side",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                condition = serviceStress.status != CheckStatus.FAILED,
                required = "σbc ≤ ${Checks.format(0.6 * fc28MPa, 2)} MPa et σst ≤ ${Checks.format(steelLimitMPa, 1)} MPa",
                provided = "σbc = ${Checks.format(serviceStress.sigmaConcreteMPa, 2)} MPa, " +
                    "σst = ${Checks.format(serviceStress.sigmaSteelMPa, 1)} MPa",
                message = serviceStress.message
            )
        )
        return SlabCantileverResult(
            side = side,
            lengthMeters = length,
            momentUltimateKNm = -momentUltimate,
            momentServiceKNm = -momentService,
            shearUltimateKN = shear,
            points = points,
            reinforcement = reinforcement,
            serviceStress = serviceStress,
            checks = checks
        )
    }
}
