package com.archilab.rpa2024.engine.seismic

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class Rpa2024SpectrumGenerator(
    private val config: SeismicProjectConfig
) {
    val dampingCorrectionEta: Double = run {
        val etaCalc = sqrt(7.0 / (2.0 + config.criticalDampingPercent))
        max(etaCalc, 0.70)
    }

    val lowerBoundThreshold: Double = kotlin.math.round(0.20 * config.zone.accelA * config.importance.factorI * 1e6) / 1e6

    fun computeElasticHorizontalAcceleration(periodT: Double): Double {
        val a = config.zone.accelA
        val i = config.importance.factorI
        val s = config.site.s
        val t1 = config.site.t1
        val t2 = config.site.t2
        val t3 = config.site.t3
        val eta = dampingCorrectionEta

        val baseFactor = a * i * s
        val constantPlateau = 2.5 * eta

        val sae = when {
            periodT < t1 -> baseFactor * (1.0 + (periodT / t1) * (constantPlateau - 1.0))
            periodT < t2 -> baseFactor * constantPlateau
            periodT < t3 -> baseFactor * constantPlateau * (t2 / periodT)
            else -> baseFactor * constantPlateau * ((t2 * t3) / periodT.pow(2.0))
        }
        return max(sae, lowerBoundThreshold)
    }

    fun computeDesignHorizontalAcceleration(periodT: Double): Double {
        val a = config.zone.accelA
        val i = config.importance.factorI
        val s = config.site.s
        val t1 = config.site.t1
        val t2 = config.site.t2
        val t3 = config.site.t3
        val qf = config.qualityFactorQ
        val r = config.behaviorFactorR

        val baseFactor = a * i * s
        val plateauRatio = 2.5 * (qf / r)

        val sad = when {
            periodT < t1 -> baseFactor * ((2.0 / 3.0) + (periodT / t1) * (plateauRatio - (2.0 / 3.0)))
            periodT < t2 -> baseFactor * plateauRatio
            periodT < t3 -> baseFactor * plateauRatio * (t2 / periodT)
            else -> baseFactor * plateauRatio * ((t2 * t3) / periodT.pow(2.0))
        }
        return max(sad, lowerBoundThreshold)
    }

    fun generateDesignSpectrumCurve(tMaxSeconds: Double = 4.0, stepSeconds: Double = 0.02): List<SpectrumPoint> {
        val curve = mutableListOf<SpectrumPoint>()
        var t = 0.0
        while (t <= tMaxSeconds + 1e-5) {
            curve.add(SpectrumPoint(t, computeDesignHorizontalAcceleration(t)))
            t += stepSeconds
        }
        return curve
    }
}
