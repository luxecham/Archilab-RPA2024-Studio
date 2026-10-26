package com.archilab.rpa2024.engine.seismic

class KinematicAndStabilityEngine(
    private val behaviorR: Double,
    private val qualityQ: Double,
    private val nuAImportanceReduction: Double // Ex: 0.46 ou 0.39 selon Groupe d'usage
) {
    /**
     * Justification vis-à-vis des déplacements inter-étages (RPA 2024 § 4.5.2 & § 5.10)
     */
    fun evaluateStoryDrift(
        storyName: String,
        storyHeightHkMeters: Double,
        elasticDisplacementCurrentMeters: Double,
        elasticDisplacementPreviousMeters: Double,
        isDuctileNonStructural: Boolean = true
    ): StoryDriftCheck {
        // δk = (R / QF) * δek
        val ampFactor = behaviorR / qualityQ
        val deltaK = ampFactor * elasticDisplacementCurrentMeters
        val deltaKPrev = ampFactor * elasticDisplacementPreviousMeters

        // Δk = δk - δk-1
        val bigDeltaK = deltaK - deltaKPrev

        // Justification : νA * Δk ≤ Δk_adm
        val reducedDrift = nuAImportanceReduction * bigDeltaK
        val allowableLimit = if (isDuctileNonStructural) 0.0075 * storyHeightHkMeters else 0.0050 * storyHeightHkMeters
        val isCompliant = reducedDrift <= allowableLimit

        return StoryDriftCheck(
            storyName = storyName,
            elevationHeightHkMeters = storyHeightHkMeters,
            elasticDisplacementDeltaEkMeters = elasticDisplacementCurrentMeters,
            inelasticDisplacementDeltaKMeters = deltaK,
            interStoryDriftBigDeltaKMeters = bigDeltaK,
            reducedDriftNuATimesBigDeltaKMeters = reducedDrift,
            allowableDriftLimitMeters = allowableLimit,
            isDriftCompliant = isCompliant
        )
    }

    /**
     * Justification de l'effet P-Delta / Second ordre (RPA 2024 § 5.9)
     */
    fun evaluatePDeltaStability(
        storyName: String,
        storyHeightHkMeters: Double,
        cumulativeWeightPkKn: Double,
        interStoryDriftBigDeltaKMeters: Double,
        storyShearVkKn: Double
    ): PDeltaStoryEvaluation {
        // θk = (Pk * Δk) / (Vk * hk)
        val denominator = storyShearVkKn * storyHeightHkMeters
        val theta = if (denominator > 0.0) (cumulativeWeightPkKn * interStoryDriftBigDeltaKMeters) / denominator else 0.0

        val isNegligible = theta <= 0.10
        val requiresAmp = theta > 0.10 && theta <= 0.20
        val isUnstable = theta > 0.20
        val factor = if (requiresAmp) 1.0 / (1.0 - theta) else 1.0

        return PDeltaStoryEvaluation(
            storyName = storyName,
            storyHeightHkMeters = storyHeightHkMeters,
            cumulativeWeightPkKn = cumulativeWeightPkKn,
            interStoryDriftDeltaKMeters = interStoryDriftBigDeltaKMeters,
            cumulativeShearVkKn = storyShearVkKn,
            thetaK = theta,
            isSecondOrderNegligible = isNegligible,
            requiresAmplification = requiresAmp,
            isPotentiallyUnstable = isUnstable,
            amplificationFactor = factor
        )
    }
}
