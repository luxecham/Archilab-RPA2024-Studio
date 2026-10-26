package com.archilab.rpa2024.engine.slabs

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Typologie de positionnement du poteau ou de l'appui sur la dalle / radier.
 */
@Serializable
enum class ColumnSlabPosition(val defaultBeta: Double, val label: String) {
    INTERIOR(1.15, "Poteau Intérieur"),
    EDGE_X(1.40, "Poteau de Rive (Bord X)"),
    EDGE_Y(1.40, "Poteau de Rive (Bord Y)"),
    CORNER(1.50, "Poteau d'Angle"),
    CIRCULAR_INTERNAL(1.10, "Poteau Circulaire Intérieur")
}

/**
 * Type d'armatures de poinçonnement adoptées.
 */
@Serializable
enum class PunchingReinforcementType(val label: String, val efficiencyFactor: Double) {
    NONE("Aucune armature transversale (Béton seul)", 0.0),
    VERTICAL_STIRRUPS("Étriers verticaux fermés en cage", 1.0),
    INCLINED_BARS_45("Barres pliées ou chevalets inclinés à 45°", 1.414),
    SHEAR_STUDS_RAILS("Rails de goujons à double tête (Style Peikko PSB / Halfen)", 1.25)
}

/**
 * Détail des rails et chapelets d'armatures de poinçonnement.
 */
@Serializable
data class PunchingShearReinforcementDetail(
    val type: PunchingReinforcementType,
    val totalRequiredAreaAswCm2: Double,
    val providedAreaAswCm2: Double,
    val numberOfRadialRails: Int,
    val studsPerRail: Int,
    val studDiameterMm: Double,
    val radialSpacingSrCm: Double,
    val outerPerimeterUoutMeters: Double,
    val outerPerimeterDistanceKdMeters: Double,
    val commercialDescription: String
)

/**
 * Rapport complet de vérification et de dimensionnement au poinçonnement selon Eurocode 2 / BAEL 91 / DTR BC 2.33.1.
 */
@Serializable
data class PunchingShearAssessmentReport(
    val nodeIdentifier: String,
    val columnPosition: ColumnSlabPosition,
    val axialLoadVEdKN: Double,
    val bendingMomentMEdKNm: Double = 0.0,
    val eccentricityFactorBeta: Double,
    val columnDimC1Meters: Double,
    val columnDimC2Meters: Double,
    val slabThicknessHMeters: Double,
    val effectiveDepthDMeters: Double,
    val longitudinalRebarRatioRhoPercent: Double,
    val concreteCompressiveStrengthFc28MPa: Double,
    val perimeterAtColumnFaceU0Meters: Double,
    val basicControlPerimeterU1Meters: Double,
    val maxShearStressAtFaceVEd0MPa: Double,
    val maxAllowableStressVRdMaxMPa: Double,
    val isFaceStressCompliant: Boolean,
    val shearStressAtControlPerimeterVEd1MPa: Double,
    val concreteShearResistanceWithoutRebarVRdcMPa: Double,
    val isConcreteAloneSufficient: Boolean,
    val reinforcementRequired: Boolean,
    val shearReinforcementDetail: PunchingShearReinforcementDetail?,
    val overallStatus: String,
    val diagnosticSummary: String
)

/**
 * Moteur Avancé de Poinçonnement des Dalles Pleines, Planchers-Dalles et Radiers Généraux.
 * Équivalent natif aux modules de poinçonnement de CYPECAD et Robot Structural Analysis.
 * Conforme aux normes Eurocode 2 (§ 6.4), BAEL 91 (§ A.5.2) et DTR BC 2.33.1.
 */
class AdvancedPunchingShearEngine {

    /**
     * Analyse et dimensionne la résistance au poinçonnement au droit d'un appui ponctuel.
     */
    fun evaluatePunching(
        nodeId: String,
        position: ColumnSlabPosition,
        columnAxialLoadVEdKN: Double,
        columnMomentMEdKNm: Double = 0.0,
        c1Meters: Double,
        c2Meters: Double,
        slabThicknessHMeters: Double,
        concreteCoverMeters: Double = 0.04,
        longitudinalRhoXPercent: Double = 0.85,
        longitudinalRhoYPercent: Double = 0.85,
        fc28MPa: Double = 25.0,
        fykSteelMPa: Double = 500.0,
        preferShearStuds: Boolean = true
    ): PunchingShearAssessmentReport {
        val dMeters = slabThicknessHMeters - concreteCoverMeters
        require(dMeters > 0.05) { "La hauteur utile de la dalle d doit être supérieure à 5 cm." }

        val dMm = dMeters * 1000.0
        val gammaC = 1.50
        val fcd = fc28MPa / gammaC

        // 1. Facteur d'excentrement beta
        val beta = if (columnMomentMEdKNm > 1.0 && columnAxialLoadVEdKN > 10.0) {
            val approxBeta = 1.0 + 1.25 * (columnMomentMEdKNm / columnAxialLoadVEdKN) / max(0.20, c1Meters)
            approxBeta.coerceIn(position.defaultBeta, 2.0)
        } else {
            position.defaultBeta
        }

        // 2. Périmètre de contrôle u0 au nu du poteau
        val u0 = when (position) {
            ColumnSlabPosition.INTERIOR -> 2.0 * (c1Meters + c2Meters)
            ColumnSlabPosition.EDGE_X -> c2Meters + 2.0 * c1Meters
            ColumnSlabPosition.EDGE_Y -> c1Meters + 2.0 * c2Meters
            ColumnSlabPosition.CORNER -> c1Meters + c2Meters
            ColumnSlabPosition.CIRCULAR_INTERNAL -> PI * c1Meters
        }

        // 3. Périmètre de référence de base u1 à 2d du nu du poteau
        val dist2d = 2.0 * dMeters
        val u1 = when (position) {
            ColumnSlabPosition.INTERIOR -> 2.0 * (c1Meters + c2Meters) + 2.0 * PI * dist2d
            ColumnSlabPosition.EDGE_X -> c2Meters + 2.0 * c1Meters + PI * dist2d
            ColumnSlabPosition.EDGE_Y -> c1Meters + 2.0 * c2Meters + PI * dist2d
            ColumnSlabPosition.CORNER -> c1Meters + c2Meters + 0.50 * PI * dist2d
            ColumnSlabPosition.CIRCULAR_INTERNAL -> PI * (c1Meters + 2.0 * dist2d)
        }

        // 4. Contrainte maximale au nu du poteau v_Ed,0 vs v_Rd,max
        val vEd0 = (beta * columnAxialLoadVEdKN * 1000.0) / (u0 * dMeters * 1e6)
        val nuFactor = 0.60 * (1.0 - fc28MPa / 250.0)
        val vRdMax = 0.50 * nuFactor * fcd
        val isFaceCompliant = vEd0 <= vRdMax

        // 5. Contrainte sur le périmètre de base u1 vs résistance du béton v_Rd,c
        val vEd1 = (beta * columnAxialLoadVEdKN * 1000.0) / (u1 * dMeters * 1e6)

        // Résistance au cisaillement sans armatures (EC2 formule 6.47)
        val kScale = min(2.0, 1.0 + sqrt(200.0 / dMm))
        val rhoL = sqrt((longitudinalRhoXPercent / 100.0) * (longitudinalRhoYPercent / 100.0)).coerceAtMost(0.02)
        val cRdc = 0.18 / gammaC
        val vMin = 0.035 * kScale.pow(1.5) * sqrt(fc28MPa)
        val vRdcCalculated = (cRdc * kScale * (100.0 * rhoL * fc28MPa).pow(1.0 / 3.0)).coerceAtLeast(vMin)

        val isConcreteAloneOk = vEd1 <= vRdcCalculated
        val needsReinforcement = !isConcreteAloneOk

        // 6. Dimensionnement des armatures transversales de poinçonnement si vEd1 > vRdc
        var rebarDetail: PunchingShearReinforcementDetail? = null

        if (needsReinforcement && isFaceCompliant) {
            val fywdEff = min(435.0, 250.0 + 0.25 * dMm) // Résistance efficace de calcul des goujons/étriers
            val rebarType = if (preferShearStuds) PunchingReinforcementType.SHEAR_STUDS_RAILS else PunchingReinforcementType.VERTICAL_STIRRUPS

            // Section requise Asw selon EC2 eq. 6.52 : (vEd - 0.75 vRdc) * u1 * d / (1.5 * fywd)
            val excessStressMPa = max(0.05, vEd1 - 0.75 * vRdcCalculated)
            val requiredAswM2 = (excessStressMPa * 1e6 * u1 * dMeters) / (1.50 * rebarType.efficiencyFactor * fywdEff * 1e6)
            val requiredAswCm2 = requiredAswM2 * 10000.0

            // Configuration géométrique en rails radiaux
            val numRails = when (position) {
                ColumnSlabPosition.INTERIOR -> 8
                ColumnSlabPosition.EDGE_X, ColumnSlabPosition.EDGE_Y -> 6
                ColumnSlabPosition.CORNER -> 4
                ColumnSlabPosition.CIRCULAR_INTERNAL -> 8
            }

            // Choix du diamètre de goujon ou d'étrier (10, 12, 14, 16 mm)
            val candidateDiameters = listOf(10.0, 12.0, 14.0, 16.0, 20.0)
            var chosenDiaMm = 12.0
            var studsPerRail = 3
            var providedAreaCm2 = 0.0

            for (dia in candidateDiameters) {
                val singleAreaCm2 = (PI * dia * dia / 4.0) / 100.0
                val totalStudsNeeded = ceil(requiredAswCm2 / singleAreaCm2).toInt()
                val perRail = ceil(totalStudsNeeded.toDouble() / numRails).toInt().coerceAtLeast(3)
                val totalProvided = perRail * numRails * singleAreaCm2
                if (totalProvided >= requiredAswCm2) {
                    chosenDiaMm = dia
                    studsPerRail = perRail
                    providedAreaCm2 = totalProvided
                    break
                }
            }

            // Périmètre extérieur u_out où vEd <= vRdc
            val uOutMeters = (beta * columnAxialLoadVEdKN * 1000.0) / (vRdcCalculated * dMeters * 1e6)
            val rOutMeters = max(dMeters, (uOutMeters - 2.0 * (c1Meters + c2Meters)) / (2.0 * PI))
            val distKd = round((rOutMeters / dMeters) * 10.0) / 10.0
            val radialSpacingSr = round((0.75 * dMeters * 100.0) * 10.0) / 10.0

            val desc = if (rebarType == PunchingReinforcementType.SHEAR_STUDS_RAILS) {
                "$numRails rails de goujons PSB/HDB $\\phi$${chosenDiaMm.toInt()} mm ($studsPerRail goujons/rail, As = ${round(providedAreaCm2 * 10.0) / 10.0} cm²)"
            } else {
                "$numRails files d'étriers verticaux fermés $\\phi$${chosenDiaMm.toInt()} mm espacés de $radialSpacingSr cm"
            }

            rebarDetail = PunchingShearReinforcementDetail(
                type = rebarType,
                totalRequiredAreaAswCm2 = round(requiredAswCm2 * 100.0) / 100.0,
                providedAreaAswCm2 = round(providedAreaCm2 * 100.0) / 100.0,
                numberOfRadialRails = numRails,
                studsPerRail = studsPerRail,
                studDiameterMm = chosenDiaMm,
                radialSpacingSrCm = radialSpacingSr,
                outerPerimeterUoutMeters = round(uOutMeters * 100.0) / 100.0,
                outerPerimeterDistanceKdMeters = distKd,
                commercialDescription = desc
            )
        }

        val status = when {
            !isFaceCompliant -> "ÉCHEC AU NU DU POTEAU (vEd0 > vRd,max : Augmenter l'épaisseur de dalle ou créer un chapiteau)"
            isConcreteAloneOk -> "CONFORME SANS ARMATURES (Béton seul suffisant vEd1 <= vRdc)"
            needsReinforcement && rebarDetail != null -> "CONFORME AVEC ARMATURES DE POINÇONNEMENT (Goujons PSB dimensionnés)"
            else -> "NON CONFORME"
        }

        val diag = buildString {
            append("Poinçonnement sous ${position.label} (${c1Meters * 100}x${c2Meters * 100} cm) : ")
            append("VEd = ${columnAxialLoadVEdKN.toInt()} kN, beta = $beta, d = ${round(dMeters * 100)} cm. ")
            if (!isFaceCompliant) {
                append("Dépassement de la bielle de compression au nu : vEd0 = ${round(vEd0 * 100) / 100} MPa > vRd,max = ${round(vRdMax * 100) / 100} MPa.")
            } else if (isConcreteAloneOk) {
                append("Cisaillement critique vEd1 = ${round(vEd1 * 100) / 100} MPa <= vRdc = ${round(vRdcCalculated * 100) / 100} MPa [CV].")
            } else {
                append("Cisaillement vEd1 = ${round(vEd1 * 100) / 100} MPa > vRdc = ${round(vRdcCalculated * 100) / 100} MPa. ")
                append("Armatures requises Asw = ${rebarDetail?.totalRequiredAreaAswCm2} cm² (${rebarDetail?.commercialDescription}).")
            }
        }

        return PunchingShearAssessmentReport(
            nodeIdentifier = nodeId,
            columnPosition = position,
            axialLoadVEdKN = columnAxialLoadVEdKN,
            bendingMomentMEdKNm = columnMomentMEdKNm,
            eccentricityFactorBeta = round(beta * 100.0) / 100.0,
            columnDimC1Meters = c1Meters,
            columnDimC2Meters = c2Meters,
            slabThicknessHMeters = slabThicknessHMeters,
            effectiveDepthDMeters = round(dMeters * 1000.0) / 1000.0,
            longitudinalRebarRatioRhoPercent = round(rhoL * 10000.0) / 100.0,
            concreteCompressiveStrengthFc28MPa = fc28MPa,
            perimeterAtColumnFaceU0Meters = round(u0 * 100.0) / 100.0,
            basicControlPerimeterU1Meters = round(u1 * 100.0) / 100.0,
            maxShearStressAtFaceVEd0MPa = round(vEd0 * 1000.0) / 1000.0,
            maxAllowableStressVRdMaxMPa = round(vRdMax * 1000.0) / 1000.0,
            isFaceStressCompliant = isFaceCompliant,
            shearStressAtControlPerimeterVEd1MPa = round(vEd1 * 1000.0) / 1000.0,
            concreteShearResistanceWithoutRebarVRdcMPa = round(vRdcCalculated * 1000.0) / 1000.0,
            isConcreteAloneSufficient = isConcreteAloneOk,
            reinforcementRequired = needsReinforcement,
            shearReinforcementDetail = rebarDetail,
            overallStatus = status,
            diagnosticSummary = diag
        )
    }
}
