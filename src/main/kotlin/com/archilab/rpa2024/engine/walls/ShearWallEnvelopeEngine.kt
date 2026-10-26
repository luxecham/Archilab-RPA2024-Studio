package com.archilab.rpa2024.engine.walls

import kotlin.math.max
import kotlin.math.min

class ShearWallEnvelopeEngine {

    /**
     * Calcule la hauteur critique hcr selon RPA 2024 § 7.7.4 (Formules 7.22 & 7.23)
     */
    fun computeCriticalZoneHeight(
        lwMeters: Double,
        hwMeters: Double,
        clearHeightHeMeters: Double,
        totalStoriesN: Int
    ): Double {
        val baseHcr = max(lwMeters, hwMeters / 6.0)
        val upperLimit = if (totalStoriesN <= 6) {
            min(2.0 * lwMeters, clearHeightHeMeters)
        } else {
            min(2.0 * lwMeters, 2.0 * clearHeightHeMeters)
        }
        return min(baseHcr, upperLimit)
    }

    /**
     * Génère l'enveloppe linéaire de moment fléchissant décalée verticalement de hcr
     */
    fun buildShiftedMomentEnvelope(
        storyForces: List<StoryPierForces>,
        hcrMeters: Double
    ): List<ShiftedMomentEnvelopePoint> {
        val sorted = storyForces.sortedBy { it.elevationHeightMeters }
        val mBase = sorted.firstOrNull()?.muBendingKnM ?: 0.0
        val mTop = sorted.lastOrNull()?.muBendingKnM ?: 0.0
        val hw = sorted.lastOrNull()?.elevationHeightMeters ?: 11.22

        return sorted.map { sp ->
            val z = sp.elevationHeightMeters
            // Enveloppe linéaire entre M_base et M_top
            val linearM = mBase - (z / hw) * (mBase - mTop)
            // Décalage vertical de traction d'une hauteur hcr
            val shiftedZ = max(0.0, z - hcrMeters)
            val shiftedM = mBase - (shiftedZ / hw) * (mBase - mTop)

            ShiftedMomentEnvelopePoint(
                elevationZ = z,
                rawAnalysisMomentKnM = sp.muBendingKnM,
                linearEnvelopeMomentKnM = linearM,
                shiftedDesignMomentKnM = max(linearM, shiftedM)
            )
        }
    }

    /**
     * Génère l'enveloppe d'effort tranchant amplifié (V = 1.40 * Vu)
     */
    fun buildAmplifiedShearEnvelope(
        storyForces: List<StoryPierForces>,
        amplificationFactor: Double = 1.40
    ): List<AmplifiedShearEnvelopePoint> {
        return storyForces.sortedBy { it.elevationHeightMeters }.map { sp ->
            AmplifiedShearEnvelopePoint(
                elevationZ = sp.elevationHeightMeters,
                rawAnalysisShearKn = sp.vuShearKn,
                amplifiedDesignShearKn = sp.vuShearKn * amplificationFactor
            )
        }
    }
}
