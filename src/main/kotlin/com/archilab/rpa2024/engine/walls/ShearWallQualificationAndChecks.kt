package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.WallSlenderness
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.round

/**
 * Rapport de qualification géométrique et de coffrage d'un voile de contreventement (§ 7.7.1 RPA 2024).
 */
@Serializable
data class WallGeometryQualification(
    val pierName: String,
    val direction: AxisDirection,
    val clearHeightHeMeters: Double,
    val thicknessBwMeters: Double,
    val lengthLwMeters: Double,
    val totalHeightHwMeters: Double,
    val minLengthHeOver3: Double,
    val minLength4Bw: Double,
    val minLength1m: Double,
    val minLengthRequiredMeters: Double,
    val isWallConditionSatisfied: Boolean,
    val minThicknessRequiredMeters: Double,
    val isThicknessSatisfied: Boolean,
    val slendernessRatioHwOverLw: Double,
    val classification: WallSlenderness,
    val reclassifiedAsColumn: Boolean = false
)

/**
 * Rapport de vérification de l'effort normal réduit d'un voile (§ 7.7.2 - Éq. 7.11 & 7.22 RPA 2024).
 */
@Serializable
data class WallReducedAxialForceCheck(
    val pierName: String,
    val lengthLwMeters: Double,
    val thicknessBwMeters: Double,
    val grossAreaBcM2: Double,
    val fc28MPa: Double,
    val maxCompressionNdKN: Double,
    val nuD: Double,
    val nuDLimit: Double = 0.40,
    val isCompliant: Boolean,
    val story: String = "",
    val loadCombo: String = ""
)

/**
 * Rapport de vérification de la contrainte limite de cisaillement dans le béton d'un voile (§ 7.7.3 RPA 2024).
 */
@Serializable
data class WallShearStressCheck(
    val pierName: String,
    val lengthLwMeters: Double,
    val effectiveDepthDMeters: Double,
    val thicknessBwMeters: Double,
    val vuKN: Double,
    val vBarKN: Double,                 // V_bar = 1.40 * Vu
    val tauBKPa: Double,
    val tauBMPa: Double,
    val tauBLimitMPa: Double,            // 0.20 * fc28
    val isCompliant: Boolean,
    val observation: String
)

/**
 * Bilan global de vérification préliminaire d'une famille de voiles.
 */
@Serializable
data class ShearWallPreliminaryCheckReport(
    val geometry: WallGeometryQualification,
    val axialForceCheck: WallReducedAxialForceCheck,
    val shearStressCheck: WallShearStressCheck,
    val isGloballyCompliant: Boolean
)

/**
 * Moteur de qualification géométrique, de coffrage et des vérifications limites des voiles selon RPA 2024 § 7.7.
 */
class ShearWallQualificationAndChecks {

    /**
     * 1. Qualification Géométrique de Coffrage (§ 7.7.1 - Éq. 7.9 & 7.10)
     */
    fun qualifyGeometry(wall: ShearWallFamily): WallGeometryQualification {
        val he = wall.clearHeightHeMeters
        val bw = wall.thicknessBwMeters
        val lw = wall.lengthLwMeters
        val hw = wall.totalHeightHwMeters

        val minHeOver3 = he / 3.0
        val min4Bw = 4.0 * bw
        val min1m = 1.00

        val minLwRequired = max(minHeOver3, max(min4Bw, min1m))
        val isWallCondition = lw >= (minLwRequired - 1e-4)

        val minBwRequired = max(0.15, he / 20.0)
        val isBwCondition = bw >= (minBwRequired - 1e-4)

        val slendernessRatio = if (lw > 0.0) hw / lw else 0.0
        val classification = when {
            slendernessRatio > 2.0 -> WallSlenderness.SLENDER_WALL
            slendernessRatio > (2.0 / 3.0) -> WallSlenderness.SHORT_WALL
            else -> WallSlenderness.LARGE_DIMENSION_WALL
        }

        return WallGeometryQualification(
            pierName = wall.pierName,
            direction = wall.direction,
            clearHeightHeMeters = roundTo3Decimals(he),
            thicknessBwMeters = roundTo3Decimals(bw),
            lengthLwMeters = roundTo3Decimals(lw),
            totalHeightHwMeters = roundTo3Decimals(hw),
            minLengthHeOver3 = roundTo3Decimals(minHeOver3),
            minLength4Bw = roundTo3Decimals(min4Bw),
            minLength1m = min1m,
            minLengthRequiredMeters = roundTo3Decimals(minLwRequired),
            isWallConditionSatisfied = isWallCondition,
            minThicknessRequiredMeters = roundTo3Decimals(minBwRequired),
            isThicknessSatisfied = isBwCondition,
            slendernessRatioHwOverLw = roundTo3Decimals(slendernessRatio),
            classification = classification,
            reclassifiedAsColumn = !isWallCondition
        )
    }

    /**
     * 2. Vérification de l'Effort Normal Réduit (§ 7.7.2 - Éq. 7.11 & 7.22)
     * nu_d = Nd / (Bc * fc28) <= 0.40
     */
    fun checkReducedAxialForce(
        pierName: String,
        lengthLwMeters: Double,
        thicknessBwMeters: Double,
        maxCompressionNdKN: Double,
        fc28MPa: Double = 25.0,
        story: String = "",
        loadCombo: String = ""
    ): WallReducedAxialForceCheck {
        val bc = lengthLwMeters * thicknessBwMeters
        val fc28KPa = fc28MPa * 1000.0
        val capacityKN = bc * fc28KPa

        val nuD = if (capacityKN > 0.0) maxCompressionNdKN / capacityKN else 0.0
        val isCompliant = nuD <= (0.40 + 1e-4)

        return WallReducedAxialForceCheck(
            pierName = pierName,
            lengthLwMeters = roundTo3Decimals(lengthLwMeters),
            thicknessBwMeters = roundTo3Decimals(thicknessBwMeters),
            grossAreaBcM2 = roundTo3Decimals(bc),
            fc28MPa = fc28MPa,
            maxCompressionNdKN = roundTo2Decimals(maxCompressionNdKN),
            nuD = roundTo3Decimals(nuD),
            nuDLimit = 0.40,
            isCompliant = isCompliant,
            story = story,
            loadCombo = loadCombo
        )
    }

    /**
     * 3. Vérification de la Contrainte Limite de Cisaillement (§ 7.7.3 - Éq. 7.12 & 7.13)
     * V_bar = 1.40 * Vu
     * d = 0.90 * lw
     * tau_b = V_bar / (bw * d) <= 0.20 * fc28
     */
    fun checkShearStress(
        pierName: String,
        lengthLwMeters: Double,
        thicknessBwMeters: Double,
        vuMaxKN: Double,
        fc28MPa: Double = 25.0
    ): WallShearStressCheck {
        val vBarKN = 1.40 * vuMaxKN
        val dMeters = 0.90 * lengthLwMeters

        val areaM2 = thicknessBwMeters * dMeters
        val tauBKPa = if (areaM2 > 0.0) vBarKN / areaM2 else 0.0
        val tauBMPa = tauBKPa / 1000.0
        val tauBLimitMPa = 0.20 * fc28MPa

        val isCompliant = tauBMPa <= (tauBLimitMPa + 1e-4)
        val observation = if (isCompliant) {
            "Contrainte admissible vérifiée (tau_b = ${roundTo3Decimals(tauBMPa)} MPa <= ${roundTo2Decimals(tauBLimitMPa)} MPa)"
        } else {
            "Cisaillement excessif (tau_b = ${roundTo3Decimals(tauBMPa)} MPa > ${roundTo2Decimals(tauBLimitMPa)} MPa) - Augmenter l'épaisseur bw ou allonger le voile"
        }

        return WallShearStressCheck(
            pierName = pierName,
            lengthLwMeters = roundTo3Decimals(lengthLwMeters),
            effectiveDepthDMeters = roundTo3Decimals(dMeters),
            thicknessBwMeters = roundTo3Decimals(thicknessBwMeters),
            vuKN = roundTo2Decimals(vuMaxKN),
            vBarKN = roundTo2Decimals(vBarKN),
            tauBKPa = roundTo2Decimals(tauBKPa),
            tauBMPa = roundTo3Decimals(tauBMPa),
            tauBLimitMPa = roundTo2Decimals(tauBLimitMPa),
            isCompliant = isCompliant,
            observation = observation
        )
    }

    /**
     * Exécute les 3 vérifications combinées pour un voile complet.
     */
    fun performAllPreliminaryChecks(
        wall: ShearWallFamily,
        maxCompressionNdKN: Double,
        vuMaxKN: Double,
        fc28MPa: Double = 25.0,
        story: String = "",
        loadCombo: String = ""
    ): ShearWallPreliminaryCheckReport {
        val geom = qualifyGeometry(wall)
        val axial = checkReducedAxialForce(
            pierName = wall.pierName,
            lengthLwMeters = wall.lengthLwMeters,
            thicknessBwMeters = wall.thicknessBwMeters,
            maxCompressionNdKN = maxCompressionNdKN,
            fc28MPa = fc28MPa,
            story = story,
            loadCombo = loadCombo
        )
        val shear = checkShearStress(
            pierName = wall.pierName,
            lengthLwMeters = wall.lengthLwMeters,
            thicknessBwMeters = wall.thicknessBwMeters,
            vuMaxKN = vuMaxKN,
            fc28MPa = fc28MPa
        )

        val globallyCompliant = geom.isWallConditionSatisfied &&
                geom.isThicknessSatisfied &&
                axial.isCompliant &&
                shear.isCompliant

        return ShearWallPreliminaryCheckReport(
            geometry = geom,
            axialForceCheck = axial,
            shearStressCheck = shear,
            isGloballyCompliant = globallyCompliant
        )
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundTo3Decimals(value: Double): Double = round(value * 1000.0) / 1000.0
}
