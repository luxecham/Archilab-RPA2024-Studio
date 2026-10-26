package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.model.PierLevelForce
import com.archilab.rpa2024.model.ShearWallFamily
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Point de coordonnées (z, valeur) sur une courbe enveloppe d'effort vertical.
 */
@Serializable
data class EnvelopePoint(
    val zMeters: Double,
    val value: Double
)

/**
 * Enveloppes complètes des moments fléchissants M(z) selon RPA 2024 § 7.7.4 (Fig. 7.13).
 */
@Serializable
data class WallMomentEnvelope(
    val pierName: String,
    val totalHeightHwMeters: Double,
    val criticalZoneHeightHcrMeters: Double,
    val baseMomentKNm: Double,
    val topMomentKNm: Double,
    val analysisCurve: List<EnvelopePoint>,         // Courbe 1 : Issue de l'analyse structurale ETABS
    val linearEnvelopeCurve: List<EnvelopePoint>,    // Courbe 2 : Enveloppe linéaire joignant base et sommet
    val shiftedDesignEnvelopeCurve: List<EnvelopePoint> // Courbe 3 : Enveloppe de calcul décalée de hcr
)

/**
 * Enveloppes complètes des efforts tranchants T(z) selon RPA 2024 § 7.7.4 (Fig. 7.15).
 */
@Serializable
data class WallShearEnvelope(
    val pierName: String,
    val totalHeightHwMeters: Double,
    val baseShearVuKN: Double,
    val amplifiedBaseShearVBarKN: Double,           // 1.40 * Vu
    val topShearLimitKN: Double,                    // V_bar / 2
    val analysisCurve: List<EnvelopePoint>,         // Courbe 1 : Issue de l'analyse structurale ETABS
    val amplifiedCurve: List<EnvelopePoint>,        // Courbe 2 : Amplifiée de 40% (1.4 * T(z))
    val piecewiseDesignEnvelopeCurve: List<EnvelopePoint> // Courbe 3 : Palier sur hw/3 puis décroissance linéaire vers V_bar/2
)

/**
 * Rapport complet des enveloppes de dimensionnement d'un voile élancé.
 */
@Serializable
data class WallEnvelopeReport(
    val pierName: String,
    val totalHeightHwMeters: Double,
    val lengthLwMeters: Double,
    val clearStoryHeightHeMeters: Double,
    val numberOfStories: Int,
    val criticalZoneHeightHcrBaseMeters: Double,
    val criticalZoneHeightHcrMaxMeters: Double,
    val criticalZoneHeightHcrAdoptedMeters: Double,
    val criticalZoneStories: List<String>,
    val momentEnvelope: WallMomentEnvelope,
    val shearEnvelope: WallShearEnvelope
)

/**
 * Générateur mathématique des enveloppes de moments et d'efforts tranchants RPA 2024.
 */
class WallEnvelopeGenerator {

    /**
     * Calcule la hauteur réglementaire de la zone critique hcr à la base du voile (Éq. 7.20 & 7.21).
     */
    fun calculateCriticalZoneHeight(
        lengthLwMeters: Double,
        totalHeightHwMeters: Double,
        clearStoryHeightHeMeters: Double,
        numberOfStories: Int
    ): Pair<Double, Double> {
        val hcrBase = max(lengthLwMeters, totalHeightHwMeters / 6.0)

        val hcrMax = if (numberOfStories <= 6) {
            min(2.0 * lengthLwMeters, clearStoryHeightHeMeters)
        } else {
            min(2.0 * lengthLwMeters, 2.0 * clearStoryHeightHeMeters)
        }

        val hcrAdopted = min(hcrBase, hcrMax)
        return Pair(hcrBase, hcrAdopted)
    }

    /**
     * Construit les enveloppes de moments fléchissants M(z) (§ 7.7.4 - Fig. 7.13).
     */
    fun generateMomentEnvelope(
        pierName: String,
        totalHeightHwMeters: Double,
        hcrAdoptedMeters: Double,
        verticalForces: List<PierLevelForce>,
        baseMomentKNmOverride: Double? = null
    ): WallMomentEnvelope {
        val sortedLevels = verticalForces.sortedBy { it.zkMeters }

        val mBase = baseMomentKNmOverride ?: sortedLevels.firstOrNull()?.m3KNm ?: 0.0
        val mTop = sortedLevels.lastOrNull()?.m3KNm ?: 0.0

        // 1. Courbe d'analyse
        val analysisPoints = sortedLevels.map {
            EnvelopePoint(roundTo3Decimals(it.zkMeters), roundTo2Decimals(it.m3KNm))
        }

        // 2. Enveloppe linéaire (du moment max de base au moment de tête)
        val numSamplePoints = 20
        val dz = totalHeightHwMeters / numSamplePoints
        val linearPoints = mutableListOf<EnvelopePoint>()

        for (step in 0..numSamplePoints) {
            val z = step * dz
            val ratio = if (totalHeightHwMeters > 0.0) z / totalHeightHwMeters else 0.0
            val mVal = mBase - ratio * (mBase - mTop)
            linearPoints.add(EnvelopePoint(roundTo3Decimals(z), roundTo2Decimals(mVal)))
        }

        // 3. Enveloppe de calcul décalée de hcr (Décalage des tractions)
        // De z = 0 à z = hcr : M(z) = M_base
        // De z = hcr à z = Hw : droite parallèle à l'enveloppe linéaire (décalée de hcr)
        val shiftedPoints = mutableListOf<EnvelopePoint>()

        // Point de base
        shiftedPoints.add(EnvelopePoint(0.0, roundTo2Decimals(mBase)))
        if (hcrAdoptedMeters > 0.0 && hcrAdoptedMeters < totalHeightHwMeters) {
            shiftedPoints.add(EnvelopePoint(roundTo3Decimals(hcrAdoptedMeters), roundTo2Decimals(mBase)))
        }

        for (step in 1..numSamplePoints) {
            val z = step * dz
            if (z > hcrAdoptedMeters) {
                // Décalage de traction : valeur correspondant à z - hcr sur la droite initiale
                val zShifted = max(0.0, z - hcrAdoptedMeters)
                val ratio = if (totalHeightHwMeters > 0.0) zShifted / totalHeightHwMeters else 0.0
                val mVal = mBase - ratio * (mBase - mTop)
                shiftedPoints.add(EnvelopePoint(roundTo3Decimals(z), roundTo2Decimals(mVal)))
            }
        }
        if (shiftedPoints.none { it.zMeters == roundTo3Decimals(totalHeightHwMeters) }) {
            val zShifted = max(0.0, totalHeightHwMeters - hcrAdoptedMeters)
            val ratio = if (totalHeightHwMeters > 0.0) zShifted / totalHeightHwMeters else 0.0
            val mVal = mBase - ratio * (mBase - mTop)
            shiftedPoints.add(EnvelopePoint(roundTo3Decimals(totalHeightHwMeters), roundTo2Decimals(mVal)))
        }

        return WallMomentEnvelope(
            pierName = pierName,
            totalHeightHwMeters = roundTo3Decimals(totalHeightHwMeters),
            criticalZoneHeightHcrMeters = roundTo3Decimals(hcrAdoptedMeters),
            baseMomentKNm = roundTo2Decimals(mBase),
            topMomentKNm = roundTo2Decimals(mTop),
            analysisCurve = analysisPoints,
            linearEnvelopeCurve = linearPoints,
            shiftedDesignEnvelopeCurve = shiftedPoints
        )
    }

    /**
     * Construit les enveloppes d'efforts tranchants T(z) (§ 7.7.4 - Fig. 7.15).
     */
    fun generateShearEnvelope(
        pierName: String,
        totalHeightHwMeters: Double,
        verticalForces: List<PierLevelForce>,
        baseShearVuKNOverride: Double? = null
    ): WallShearEnvelope {
        val sortedLevels = verticalForces.sortedBy { it.zkMeters }

        val vuBase = baseShearVuKNOverride ?: sortedLevels.firstOrNull()?.v2KN ?: 0.0
        val vBarBase = 1.40 * vuBase
        val topLimit = vBarBase / 2.0

        // 1. Courbe d'analyse
        val analysisPoints = sortedLevels.map {
            EnvelopePoint(roundTo3Decimals(it.zkMeters), roundTo2Decimals(it.v2KN))
        }

        // 2. Courbe amplifiée de 40%
        val amplifiedPoints = sortedLevels.map {
            EnvelopePoint(roundTo3Decimals(it.zkMeters), roundTo2Decimals(1.40 * it.v2KN))
        }

        // 3. Enveloppe de calcul par morceaux (Fig. 7.15)
        // - De z = 0 à z = hw / 3 : palier constant V_bar
        // - De z = hw / 3 à z = Hw : décroissance linéaire vers V_bar / 2
        val hThird = totalHeightHwMeters / 3.0
        val piecewisePoints = mutableListOf<EnvelopePoint>()

        piecewisePoints.add(EnvelopePoint(0.0, roundTo2Decimals(vBarBase)))
        piecewisePoints.add(EnvelopePoint(roundTo3Decimals(hThird), roundTo2Decimals(vBarBase)))

        val numStepsUpper = 10
        val dzUpper = (totalHeightHwMeters - hThird) / numStepsUpper
        for (step in 1..numStepsUpper) {
            val z = hThird + step * dzUpper
            val ratio = if (totalHeightHwMeters - hThird > 0.0) (z - hThird) / (totalHeightHwMeters - hThird) else 0.0
            val tVal = vBarBase - ratio * (vBarBase - topLimit)
            piecewisePoints.add(EnvelopePoint(roundTo3Decimals(z), roundTo2Decimals(tVal)))
        }

        return WallShearEnvelope(
            pierName = pierName,
            totalHeightHwMeters = roundTo3Decimals(totalHeightHwMeters),
            baseShearVuKN = roundTo2Decimals(vuBase),
            amplifiedBaseShearVBarKN = roundTo2Decimals(vBarBase),
            topShearLimitKN = roundTo2Decimals(topLimit),
            analysisCurve = analysisPoints,
            amplifiedCurve = amplifiedPoints,
            piecewiseDesignEnvelopeCurve = piecewisePoints
        )
    }

    /**
     * Génère le rapport complet des enveloppes pour un voile élancé.
     */
    fun generateFullEnvelopeReport(
        wall: ShearWallFamily,
        numberOfStories: Int,
        verticalForces: List<PierLevelForce>,
        storyHeights: Map<String, Double> = emptyMap(),
        baseMomentOverrideKNm: Double? = null,
        baseShearOverrideKN: Double? = null
    ): WallEnvelopeReport {
        val (hcrBase, hcrAdopted) = calculateCriticalZoneHeight(
            lengthLwMeters = wall.lengthLwMeters,
            totalHeightHwMeters = wall.totalHeightHwMeters,
            clearStoryHeightHeMeters = wall.clearHeightHeMeters,
            numberOfStories = numberOfStories
        )

        // Identifier les étages couverts par la zone critique
        var cumulativeZ = 0.0
        val criticalStories = mutableListOf<String>()
        for (storyName in wall.stories) {
            val hStory = storyHeights[storyName] ?: wall.clearHeightHeMeters
            criticalStories.add(storyName)
            cumulativeZ += hStory
            if (cumulativeZ >= hcrAdopted) {
                break
            }
        }
        if (criticalStories.isEmpty() && wall.stories.isNotEmpty()) {
            criticalStories.add(wall.stories.first())
        }

        val momentEnv = generateMomentEnvelope(
            pierName = wall.pierName,
            totalHeightHwMeters = wall.totalHeightHwMeters,
            hcrAdoptedMeters = hcrAdopted,
            verticalForces = verticalForces,
            baseMomentKNmOverride = baseMomentOverrideKNm
        )

        val shearEnv = generateShearEnvelope(
            pierName = wall.pierName,
            totalHeightHwMeters = wall.totalHeightHwMeters,
            verticalForces = verticalForces,
            baseShearVuKNOverride = baseShearOverrideKN
        )

        val hcrMax = if (numberOfStories <= 6) {
            min(2.0 * wall.lengthLwMeters, wall.clearHeightHeMeters)
        } else {
            min(2.0 * wall.lengthLwMeters, 2.0 * wall.clearHeightHeMeters)
        }

        return WallEnvelopeReport(
            pierName = wall.pierName,
            totalHeightHwMeters = roundTo3Decimals(wall.totalHeightHwMeters),
            lengthLwMeters = roundTo3Decimals(wall.lengthLwMeters),
            clearStoryHeightHeMeters = roundTo3Decimals(wall.clearHeightHeMeters),
            numberOfStories = numberOfStories,
            criticalZoneHeightHcrBaseMeters = roundTo3Decimals(hcrBase),
            criticalZoneHeightHcrMaxMeters = roundTo3Decimals(hcrMax),
            criticalZoneHeightHcrAdoptedMeters = roundTo3Decimals(hcrAdopted),
            criticalZoneStories = criticalStories,
            momentEnvelope = momentEnv,
            shearEnvelope = shearEnv
        )
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundTo3Decimals(value: Double): Double = round(value * 1000.0) / 1000.0
}
