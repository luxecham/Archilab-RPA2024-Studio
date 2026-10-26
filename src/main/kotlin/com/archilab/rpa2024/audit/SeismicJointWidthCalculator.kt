package com.archilab.rpa2024.audit

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

enum class SeismicJointCombination(val label: String) {
    ADDITIVE("15 mm + δmax,1 + δmax,2 (addition vectorielle enveloppe)"),
    QUADRATIC("15 mm + √(δmax,1² + δmax,2²)")
}

data class SeismicJointOptions(
    val minimumWidthMm: Double = 40.0,
    val additionalWidthMm: Double = 15.0,
    val combination: SeismicJointCombination = SeismicJointCombination.ADDITIVE,
    val roundingStepMm: Double = 10.0,
    val includePenthouse: Boolean = true
) {
    init {
        require(minimumWidthMm >= 0.0) { "La largeur minimale du joint doit être positive ou nulle." }
        require(additionalWidthMm >= 0.0) { "La marge additionnelle doit être positive ou nulle." }
        require(roundingStepMm > 0.0) { "Le pas d'arrondi doit être positif." }
    }
}

/** Bloc sismique autonome, avec ses paramètres RPA et ses résultats ETABS. */
data class SeismicBlock(
    val name: String,
    val joint: StructuralJoint,
    val results: EtabsResults,
    val behaviorFactorOverride: Double? = null,
    val qualityFactorOverride: Double? = null
) {
    init {
        require(name.isNotBlank()) { "Le nom du bloc sismique est obligatoire." }
    }
}

data class SeismicStoryDemand(
    val story: String,
    val elasticDisplacementMeters: Double,
    val inelasticDisplacementMeters: Double
)

data class SeismicBlockDemand(
    val blockName: String,
    val behaviorFactorR: Double,
    val qualityFactor: Double,
    val amplificationFactor: Double,
    val maxElasticMeters: Double,
    val maxInelasticMeters: Double,
    val governingStory: String?,
    val byStory: Map<String, SeismicStoryDemand>,
    val available: Boolean,
    val warnings: List<String>
)

data class SeismicJointLevelDemand(
    val story: String,
    val inelasticBlockAMeters: Double,
    val inelasticBlockBMeters: Double,
    val additiveWidthMm: Double,
    val quadraticWidthMm: Double,
    val requiredWidthMm: Double
)

data class SeismicJointAssessment(
    val blockAName: String,
    val blockBName: String,
    val demandA: SeismicBlockDemand,
    val demandB: SeismicBlockDemand,
    val levels: List<SeismicJointLevelDemand>,
    val governingStory: String?,
    val additiveWidthMm: Double?,
    val quadraticWidthMm: Double?,
    val requiredWidthMm: Double?,
    val recommendedWidthMm: Double?,
    val combination: SeismicJointCombination,
    val check: DesignCheck,
    val warnings: List<String> = emptyList()
) {
    val isAvailable: Boolean
        get() = requiredWidthMm != null && recommendedWidthMm != null && check.status != CheckStatus.NOT_APPLICABLE

    val status: CheckStatus get() = check.status

    fun toChapterReport(number: Int = 9): ChapterReport {
        val lines = mutableListOf(
            "Bloc ${demandA.blockName} : R = ${Checks.format(demandA.behaviorFactorR, 2)}, " +
                "QF = ${Checks.format(demandA.qualityFactor, 2)}, R/QF = " +
                "${Checks.format(demandA.amplificationFactor, 2)}, δmax,inélastique = " +
                "${formatMm(demandA.maxInelasticMeters)} mm (${demandA.governingStory ?: "n/d"}).",
            "Bloc ${demandB.blockName} : R = ${Checks.format(demandB.behaviorFactorR, 2)}, " +
                "QF = ${Checks.format(demandB.qualityFactor, 2)}, R/QF = " +
                "${Checks.format(demandB.amplificationFactor, 2)}, δmax,inélastique = " +
                "${formatMm(demandB.maxInelasticMeters)} mm (${demandB.governingStory ?: "n/d"}).",
            "Formule retenue : ${combination.label}.",
            additiveWidthMm?.let { "Ouverture additive = ${Checks.format(it, 1)} mm." }
                ?: "Ouverture additive non calculable.",
            quadraticWidthMm?.let { "Ouverture quadratique = ${Checks.format(it, 1)} mm." }
                ?: "Ouverture quadratique non calculable.",
            requiredWidthMm?.let {
                "Largeur minimale requise = ${Checks.format(it, 1)} mm ; largeur arrondie recommandée = " +
                    "${Checks.format(recommendedWidthMm ?: it, 0)} mm."
            } ?: "Largeur minimale non calculable."
        )
        lines += levels.map {
            "Niveau ${it.story} : δA = ${formatMm(it.inelasticBlockAMeters)} mm, " +
                "δB = ${formatMm(it.inelasticBlockBMeters)} mm, d_min = ${Checks.format(it.requiredWidthMm, 1)} mm."
        }
        lines += warnings.map { "Avertissement : $it" }
        return ChapterReport(
            number = number,
            title = "Joint sismique ${blockAName} / ${blockBName}",
            summary = "Calcul réglementaire de l'ouverture minimale entre deux blocs à partir des " +
                "déplacements inélastiques R/QF·δek.",
            checks = listOf(check),
            lines = lines
        )
    }

    private fun formatMm(meters: Double): String = Checks.format(meters * 1000.0, 1)
}

data class SingleBlockSeismicJointRecommendation(
    val blockName: String,
    val demand: SeismicBlockDemand,
    val estimatedMinimumWidthMm: Double?,
    val recommendedWidthMm: Double?,
    val check: DesignCheck,
    val warnings: List<String> = emptyList()
) {
    fun toChapterReport(number: Int = 9): ChapterReport {
        val lines = listOf(
            "Bloc unique ${demand.blockName} : δmax,inélastique = ${Checks.format(demand.maxInelasticMeters * 1000.0, 1)} mm.",
            estimatedMinimumWidthMm?.let {
                "Largeur estimée au droit du joint = ${Checks.format(it, 1)} mm ; " +
                    "largeur recommandée = ${Checks.format(recommendedWidthMm ?: it, 0)} mm."
            } ?: "Déplacements sismiques indisponibles : largeur non estimée."
        ) + warnings.map { "Avertissement : $it" }
        return ChapterReport(
            number = number,
            title = "Joint sismique - bloc ${demand.blockName}",
            summary = "Estimation de la largeur du joint à partir du déplacement inélastique du bloc importé.",
            checks = listOf(check),
            lines = lines
        )
    }
}

/**
 * Calcul de la largeur minimale du joint sismique entre deux blocs. Les déplacements
 * élastiques ETABS sont amplifiés par R/QF, puis combinés selon la formule additive
 * réglementaire (ou quadratique en option) avec un minimum absolu de 40 mm.
 */
class SeismicJointWidthCalculator {

    fun assess(
        blockA: SeismicBlock,
        blockB: SeismicBlock,
        options: SeismicJointOptions = SeismicJointOptions()
    ): SeismicJointAssessment {
        val demandA = evaluateBlock(blockA, options)
        val demandB = evaluateBlock(blockB, options)
        val warnings = (demandA.warnings + demandB.warnings).distinct().toMutableList()
        if (!demandA.available || !demandB.available) {
            warnings += "Largeur de joint non calculable : déplacements sismiques manquants pour au moins un bloc."
            return SeismicJointAssessment(
                blockAName = blockA.name,
                blockBName = blockB.name,
                demandA = demandA,
                demandB = demandB,
                levels = emptyList(),
                governingStory = null,
                additiveWidthMm = null,
                quadraticWidthMm = null,
                requiredWidthMm = null,
                recommendedWidthMm = null,
                combination = options.combination,
                check = Checks.notApplicable(
                    code = "JS15_JOINT",
                    label = "Largeur minimale du joint sismique",
                    reference = "RPA 2024 § 5.11",
                    message = "Déplacements sismiques indisponibles : calcul d_min non automatisé."
                ),
                warnings = warnings
            )
        }

        val storyOrder = blockA.joint.storySystem.orderedStories.map { it.storyName }
            .ifEmpty { blockB.joint.storySystem.orderedStories.map { it.storyName } }
        val commonStories = demandA.byStory.keys.intersect(demandB.byStory.keys)
            .sortedBy { story -> storyOrder.indexOfFirst { normalize(it) == story }.let { if (it < 0) Int.MAX_VALUE else it } }
        val levels = if (commonStories.isNotEmpty()) {
            commonStories.map { story ->
                val a = demandA.byStory.getValue(story)
                val b = demandB.byStory.getValue(story)
                levelDemand(story, a.inelasticDisplacementMeters, b.inelasticDisplacementMeters, options)
            }
        } else {
            warnings += "Aucun niveau commun entre les deux blocs : l'enveloppe globale des déplacements est utilisée."
            listOf(
                levelDemand(
                    "enveloppe",
                    demandA.maxInelasticMeters,
                    demandB.maxInelasticMeters,
                    options
                )
            )
        }
        val governing = levels.maxByOrNull { it.requiredWidthMm } ?: return unavailable(blockA, blockB, demandA, demandB, warnings, options)
        val additive = governing.additiveWidthMm
        val quadratic = governing.quadraticWidthMm
        val retained = when (options.combination) {
            SeismicJointCombination.ADDITIVE -> additive
            SeismicJointCombination.QUADRATIC -> quadratic
        }
        val required = max(options.minimumWidthMm, retained)
        val recommended = roundUp(required, options.roundingStepMm)
        val check = Checks.min(
            code = "JS15_JOINT",
            label = "Largeur minimale du joint sismique ${blockA.name} / ${blockB.name}",
            reference = "RPA 2024 § 5.11",
            provided = required,
            minimum = options.minimumWidthMm,
            unit = "mm",
            decimals = 1,
            failureMessage = "La largeur calculée est inférieure au minimum réglementaire de " +
                "${Checks.format(options.minimumWidthMm, 0)} mm."
        ).copy(
            required = "≥ ${Checks.format(options.minimumWidthMm, 0)} mm (recommandé " +
                "${Checks.format(recommended, 0)} mm)",
            provided = "${Checks.format(required, 1)} mm",
            message = "Combinaison ${options.combination.name} au niveau ${governing.story} : " +
                "d_min = ${Checks.format(required, 1)} mm."
        )
        return SeismicJointAssessment(
            blockAName = blockA.name,
            blockBName = blockB.name,
            demandA = demandA,
            demandB = demandB,
            levels = levels,
            governingStory = governing.story,
            additiveWidthMm = additive,
            quadraticWidthMm = quadratic,
            requiredWidthMm = required,
            recommendedWidthMm = recommended,
            combination = options.combination,
            check = check,
            warnings = warnings.distinct()
        )
    }

    fun assess(
        jointA: StructuralJoint,
        resultsA: EtabsResults,
        jointB: StructuralJoint,
        resultsB: EtabsResults,
        options: SeismicJointOptions = SeismicJointOptions()
    ): SeismicJointAssessment = assess(
        SeismicBlock(jointA.jointName, jointA, resultsA),
        SeismicBlock(jointB.jointName, jointB, resultsB),
        options
    )

    fun recommendSingleBlock(
        block: SeismicBlock,
        options: SeismicJointOptions = SeismicJointOptions()
    ): SingleBlockSeismicJointRecommendation {
        val demand = evaluateBlock(block, options)
        if (!demand.available) {
            return SingleBlockSeismicJointRecommendation(
                blockName = block.name,
                demand = demand,
                estimatedMinimumWidthMm = null,
                recommendedWidthMm = null,
                check = Checks.notApplicable(
                    code = "JS15_JOINT",
                    label = "Largeur du joint sismique",
                    reference = "RPA 2024 § 5.11",
                    message = "Aucun déplacement sismique exploitable pour le bloc ${block.name}."
                ),
                warnings = demand.warnings
            )
        }
        val estimated = max(options.minimumWidthMm, options.additionalWidthMm + demand.maxInelasticMeters * 1000.0)
        val recommended = roundUp(estimated, options.roundingStepMm)
        val check = DesignCheck(
            code = "JS15_JOINT",
            label = "Largeur du joint sismique du bloc ${block.name}",
            reference = "RPA 2024 § 5.11",
            status = CheckStatus.OK,
            required = "≥ ${Checks.format(options.minimumWidthMm, 0)} mm",
            provided = "${Checks.format(recommended, 0)} mm recommandés",
            message = "Bloc unique : estimation fondée sur δmax du bloc ${block.name} et une marge de 15 mm."
        )
        return SingleBlockSeismicJointRecommendation(
            blockName = block.name,
            demand = demand,
            estimatedMinimumWidthMm = estimated,
            recommendedWidthMm = recommended,
            check = check,
            warnings = demand.warnings + "Un seul bloc importé : la largeur devra être confirmée avec le bloc adjacent."
        )
    }

    private fun evaluateBlock(block: SeismicBlock, options: SeismicJointOptions): SeismicBlockDemand {
        val resolved = Rpa2024NormativeDb.resolveSeismic(block.joint.seismicParams, block.joint)
        val behavior = block.behaviorFactorOverride ?: resolved.behaviorFactorR
        val quality = block.qualityFactorOverride ?: resolved.qualityFactor.qf
        val amplification = if (quality > 1e-9) behavior / quality else behavior
        val warnings = mutableListOf<String>()
        val excludedStories = if (!options.includePenthouse) {
            block.joint.storySystem.penthouseStories.map { normalize(it.storyName) }.toSet()
        } else {
            emptySet()
        }
        val allRows = block.results.diaphragmDisplacements
        var seismicRows = allRows.filter { isSeismicCase(it.loadCase) }
        if (seismicRows.isEmpty() && allRows.isNotEmpty()) {
            warnings += "Aucun cas EX/EY/ELA identifiable : tous les déplacements de ${block.name} sont pris en enveloppe."
            seismicRows = allRows
        }
        val filtered = seismicRows.filter { normalize(it.story) !in excludedStories }
        if (filtered.isEmpty()) {
            warnings += "Déplacements sismiques absents pour le bloc ${block.name}."
            return SeismicBlockDemand(
                blockName = block.name,
                behaviorFactorR = behavior,
                qualityFactor = quality,
                amplificationFactor = amplification,
                maxElasticMeters = 0.0,
                maxInelasticMeters = 0.0,
                governingStory = null,
                byStory = emptyMap(),
                available = false,
                warnings = warnings
            )
        }
        val byStory = filtered.groupBy { normalize(it.story) }.mapValues { (_, rows) ->
            val elastic = rows.maxOf { hypot(it.uxMeters, it.uyMeters) }
            val story = rows.first().story
            SeismicStoryDemand(
                story = story,
                elasticDisplacementMeters = elastic,
                inelasticDisplacementMeters = elastic * amplification
            )
        }
        val governing = byStory.values.maxByOrNull { it.inelasticDisplacementMeters }
        return SeismicBlockDemand(
            blockName = block.name,
            behaviorFactorR = behavior,
            qualityFactor = quality,
            amplificationFactor = amplification,
            maxElasticMeters = governing?.elasticDisplacementMeters ?: 0.0,
            maxInelasticMeters = governing?.inelasticDisplacementMeters ?: 0.0,
            governingStory = governing?.story,
            byStory = byStory,
            available = governing != null,
            warnings = warnings
        )
    }

    private fun levelDemand(
        story: String,
        inelasticA: Double,
        inelasticB: Double,
        options: SeismicJointOptions
    ): SeismicJointLevelDemand {
        val additive = options.additionalWidthMm + inelasticA * 1000.0 + inelasticB * 1000.0
        val quadratic = options.additionalWidthMm + hypot(inelasticA * 1000.0, inelasticB * 1000.0)
        val retained = when (options.combination) {
            SeismicJointCombination.ADDITIVE -> additive
            SeismicJointCombination.QUADRATIC -> quadratic
        }
        return SeismicJointLevelDemand(
            story = story,
            inelasticBlockAMeters = inelasticA,
            inelasticBlockBMeters = inelasticB,
            additiveWidthMm = additive,
            quadraticWidthMm = quadratic,
            requiredWidthMm = max(options.minimumWidthMm, retained)
        )
    }

    private fun unavailable(
        blockA: SeismicBlock,
        blockB: SeismicBlock,
        demandA: SeismicBlockDemand,
        demandB: SeismicBlockDemand,
        warnings: List<String>,
        options: SeismicJointOptions
    ): SeismicJointAssessment = SeismicJointAssessment(
        blockAName = blockA.name,
        blockBName = blockB.name,
        demandA = demandA,
        demandB = demandB,
        levels = emptyList(),
        governingStory = null,
        additiveWidthMm = null,
        quadraticWidthMm = null,
        requiredWidthMm = null,
        recommendedWidthMm = null,
        combination = options.combination,
        check = Checks.notApplicable(
            code = "JS15_JOINT",
            label = "Largeur minimale du joint sismique",
            reference = "RPA 2024 § 5.11",
            message = "Données de déplacement insuffisantes."
        ),
        warnings = warnings
    )

    private fun roundUp(value: Double, step: Double): Double = ceil(value / step - 1e-9) * step

    private fun isSeismicCase(loadCase: String): Boolean =
        ComboCategory.classify(loadCase) == ComboCategory.ELA

    private fun normalize(value: String): String = value.trim().uppercase()
}
