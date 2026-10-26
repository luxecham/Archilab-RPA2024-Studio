package com.archilab.rpa2024.engine.seismic

import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class ModalAndBaseShearCalibrator(
    private val config: SeismicProjectConfig,
    private val spectrumEngine: Rpa2024SpectrumGenerator
) {
    fun evaluateModalCompleteness(modes: List<ModalModeResult>): ModalCheckSummary {
        val lastMode = modes.maxByOrNull { it.modeNumber }
            ?: throw IllegalArgumentException("La liste des modes est vide.")

        val sumUx = lastMode.sumUxPercent
        val sumUy = lastMode.sumUyPercent
        val is90Achieved = sumUx >= 90.0 && sumUy >= 90.0

        val requiredK = (3.0 * sqrt(config.numberOfStories.toDouble())).toInt()
        val modesCount = modes.size
        val kMode = modes.find { it.modeNumber == requiredK }
        val isTorsionSatisfied = modesCount >= requiredK && (kMode != null && kMode.periodSeconds <= 0.20)

        val isCompliant = is90Achieved || isTorsionSatisfied
        val diag = buildString {
            append("Modes analysés : $modesCount. ΣUx = ${"%.2f".format(sumUx)}%, ΣUy = ${"%.2f".format(sumUy)}%. ")
            if (is90Achieved) {
                append("Condition ΣMasses ≥ 90% satisfaite (RPA 2024 § 4.3.3a).")
            } else if (isTorsionSatisfied) {
                append("Clause dérogatoire vérifiée : k ≥ $requiredK modes et Tk = ${kMode?.periodSeconds}s ≤ 0.20s (§ 4.3.3b).")
            } else {
                append("NON CONFORME : Augmenter le nombre de modes calculés dans ETABS.")
            }
        }

        return ModalCheckSummary(
            modesCount = modesCount,
            sumUxFinal = sumUx,
            sumUyFinal = sumUy,
            isNinetyPercentAchieved = is90Achieved,
            isTorsionDerogationSatisfied = isTorsionSatisfied,
            isCompliant = isCompliant,
            diagnostic = diag
        )
    }

    fun calibrateBaseShear(
        direction: String,
        tCalculatedSeconds: Double,
        dynamicShearVmdsKn: Double
    ): BaseShearComparison {
        // Temp = CT * (Hn)^(3/4)
        val temp = config.structureTypeCodeCt * config.totalHeightMeters.pow(0.75)
        val tMaxLimit = 1.30 * temp

        val t0 = min(tCalculatedSeconds, tMaxLimit)

        val lambda = if (t0 <= (2.0 * config.site.t2) && config.numberOfStories > 2) 0.85 else 1.00

        val sadOverG = spectrumEngine.computeDesignHorizontalAcceleration(t0)

        // VMSE = λ * (Sad / g) * W
        val vmse = lambda * sadOverG * config.totalWeightKn

        val minAllowedVmds = 0.80 * vmse
        val isCalibrated = dynamicShearVmdsKn >= minAllowedVmds
        val ratio = if (vmse > 0.0) dynamicShearVmdsKn / vmse else 1.0
        val factor = if (!isCalibrated && dynamicShearVmdsKn > 0.0) minAllowedVmds / dynamicShearVmdsKn else 1.0

        return BaseShearComparison(
            direction = direction,
            fundamentalPeriodT0 = t0,
            empiricalPeriodTemp = temp,
            designSpectralAccelSadOverG = sadOverG,
            lambdaCoeff = lambda,
            staticForceVmseKn = vmse,
            dynamicForceVmdsKn = dynamicShearVmdsKn,
            ratioVmdsOverVmse = ratio,
            isCalibrated = isCalibrated,
            amplificationFactorNeeded = factor
        )
    }
}
