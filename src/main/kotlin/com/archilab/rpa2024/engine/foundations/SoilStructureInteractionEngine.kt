package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.SiteClass
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Paramètres du modèle de sol biparamétrique de Pasternak et d'impédance dynamique.
 */
@Serializable
data class SoilDynamicImpedance(
    val shearWaveVelocityVsMs: Double,
    val dynamicShearModulusGMPa: Double,
    val pasternakWinklerKsKNm3: Double,
    val pasternakShearLayerGpKNm: Double,
    val horizontalStiffnessKxMNm: Double,
    val rockingStiffnessKThetaMNmRad: Double,
    val fixedBasePeriodTFSecond: Double,
    val flexibleBasePeriodTISSecond: Double,
    val periodLengtheningRatio: Double,
    val effectiveRadiationDampingPercent: Double
)

/**
 * Diagnostic de stabilité au renversement et au décollement (Uplift) selon RPA 2024 § 10.1.5 et CTC.
 */
@Serializable
data class BaseOverturningAndUpliftReport(
    val direction: AxisDirection,
    val combinationName: String,
    val totalVerticalLoadNKN: Double,
    val totalOverturningMomentMKNm: Double,
    val totalStabilizingMomentMKNm: Double,
    val overturningSafetyFactorFs: Double,
    val isOverturningSafe: Boolean,
    val eccentricityMeters: Double,
    val baseDimensionMeters: Double,
    val eccentricityRatioEL: Double,
    val compressedBaseLengthMeters: Double,
    val compressedAreaRatioPercent: Double,
    val minRequiredCompressedAreaPercent: Double,
    val isUpliftCompliant: Boolean,
    val maxSoilStressUnderUpliftBars: Double,
    val allowableSoilStressBars: Double,
    val isSoilBearingCompliant: Boolean,
    val observations: List<String>
)

/**
 * Rapport complet d'Interaction Sol-Structure (ISS) et de Stabilité Globale.
 */
@Serializable
data class SoilStructureInteractionReport(
    val buildingName: String,
    val siteClass: SiteClass,
    val foundationType: String,
    val foundationLengthXMeters: Double,
    val foundationWidthYMeters: Double,
    val foundationAreaM2: Double,
    val impedance: SoilDynamicImpedance,
    val stabilityReports: List<BaseOverturningAndUpliftReport>,
    val globalStatus: String
)

/**
 * Moteur Expert d'Interaction Sol-Structure (ISS) et de Contrôle Non-Linéaire du Soulèvement (Uplift / Renversement).
 * Conforme au DTR BC 2.48 (RPA 2024 § 10.1.5), au DTR BC 2.33.1 (Règles de calcul des fondations superficielles),
 * et aux formulations analytiques de Pais-Kausel et Gazetas.
 */
class SoilStructureInteractionEngine {

    /**
     * Exécute l'analyse complète d'interaction sol-structure et de stabilité au décollement.
     */
    fun evaluateInteractionAndStability(
        joint: StructuralJoint,
        foundationLengthX: Double = 24.50,
        foundationWidthY: Double = 14.80,
        foundationEmbedmentD: Double = 1.50
    ): SoilStructureInteractionReport {
        val siteClass = joint.seismicParams.site
        val soilBearing = joint.materials.soilBearingCapacityBars

        // 1. Estimation de la vitesse des ondes de cisaillement Vs selon la classe de sol RPA 2024
        val vs = when (siteClass) {
            SiteClass.S1 -> 850.0  // Rocheux
            SiteClass.S2 -> 450.0  // Ferme
            SiteClass.S3 -> 250.0  // Meuble
            SiteClass.S4 -> 150.0  // Très meuble
            SiteClass.S5 -> 120.0  // Spécifique
        }

        val rhoSoil = 1.95 // t/m³ = 1950 kg/m³
        val gSoilPa = rhoSoil * 1000.0 * vs * vs
        val gSoilMPa = gSoilPa / 1e6
        val nuSoil = 0.35 // Coefficient de Poisson moyen du sol

        val lx = max(foundationLengthX, foundationWidthY)
        val ly = min(foundationLengthX, foundationWidthY)
        val area = lx * ly
        val r0 = sqrt(area / PI) // Rayon équivalent

        // 2. Paramètres du modèle biparamétrique de Pasternak
        val ks = joint.materials.soilSubgradeModulusKNm3
        val hs = 2.50 * r0 // Profondeur active du milieu stratifié
        val gp = (gSoilMPa * 1000.0 * hs) / 3.0 // Paramètre de cisaillement de Pasternak en kN/m

        // 3. Raideurs dynamiques selon Pais-Kausel & Gazetas
        val kxMNm = (gSoilMPa * lx / (2.0 - nuSoil)) * (2.0 + 2.50 * (ly / lx).pow(0.85))
        val ibaseX = (ly * lx.pow(3)) / 12.0
        val kThetaMNmRad = (gSoilMPa / (1.0 - nuSoil)) * ibaseX.pow(0.75) * (lx / ly).pow(0.25) * (2.40 + 0.50 * (ly / lx))

        // Période empirique fixe (RPA 2024 formule C_T * H_n^0.75)
        val hBuilding = joint.storySystem.totalHeightHn.takeIf { it > 1.0 } ?: 18.0
        val tf: Double = 0.05 * hBuilding.pow(0.75)
        val hEff = 0.70 * hBuilding // Hauteur équivalente du mode fondamental

        // Période assouplie par ISS : T~ = T_f * sqrt(1 + K_str/K_x + K_str*h_eff^2 / K_theta)
        // Estimation de K_struc = M_eff * (2*pi/T_f)^2
        val totalWeightKN = joint.storySystem.stories.size * 3200.0 // Estimation masse participante
        val mEffTonnes = (totalWeightKN / 9.81) * 0.85
        val omegaFixed = 2.0 * PI / tf
        val kStrucMNm = (mEffTonnes * 1000.0 * omegaFixed * omegaFixed) / 1e6

        val flexibilityTranslation = (kStrucMNm / max(1.0, kxMNm))
        val flexibilityRocking = (kStrucMNm * hEff * hEff / max(1.0, kThetaMNmRad))
        val lengtheningRatio = sqrt(1.0 + flexibilityTranslation + flexibilityRocking)
        val tis = tf * lengtheningRatio

        // Amortissement radiatif additionnel
        val a0 = omegaFixed * r0 / vs
        val radDamping = min(15.0, 5.0 + 10.0 * a0.coerceIn(0.1, 1.0))

        val impedance = SoilDynamicImpedance(
            shearWaveVelocityVsMs = vs,
            dynamicShearModulusGMPa = round(gSoilMPa * 10.0) / 10.0,
            pasternakWinklerKsKNm3 = round(ks),
            pasternakShearLayerGpKNm = round(gp),
            horizontalStiffnessKxMNm = round(kxMNm * 10.0) / 10.0,
            rockingStiffnessKThetaMNmRad = round(kThetaMNmRad * 10.0) / 10.0,
            fixedBasePeriodTFSecond = round(tf * 1000.0) / 1000.0,
            flexibleBasePeriodTISSecond = round(tis * 1000.0) / 1000.0,
            periodLengtheningRatio = round(lengtheningRatio * 100.0) / 100.0,
            effectiveRadiationDampingPercent = round(radDamping * 10.0) / 10.0
        )

        // 4. Analyse de stabilité au décollement et au renversement sous combinaisons CTC
        val baseShearX = 1850.0 // kN
        val baseShearY = 1620.0 // kN
        val momentRenvX = baseShearX * hEff // kN.m
        val momentRenvY = baseShearY * hEff // kN.m

        val nG = totalWeightKN * 0.70
        val nQ = totalWeightKN * 0.30

        val stabilityReports = mutableListOf<BaseOverturningAndUpliftReport>()

        // Combinaison 1 : G + Q (ELS de service, sans séisme)
        stabilityReports.add(
            checkUpliftAndOverturning(
                direction = AxisDirection.X,
                comboName = "G + Q (ELS)",
                nKN = nG + nQ,
                mRenvKNm = 0.0,
                baseLength = lx,
                baseWidth = ly,
                allowableStressBars = soilBearing,
                minAreaRatioReq = 100.0
            )
        )

        // Combinaison 2 : G + Q + Ex (Sismique majeure X)
        stabilityReports.add(
            checkUpliftAndOverturning(
                direction = AxisDirection.X,
                comboName = "G + Q + Ex (Sismique)",
                nKN = nG + 0.8 * nQ,
                mRenvKNm = momentRenvX,
                baseLength = lx,
                baseWidth = ly,
                allowableStressBars = soilBearing * 1.30, // Majoration 30% RPA sous séisme
                minAreaRatioReq = 75.0
            )
        )

        // Combinaison 3 : 0.8G - Ex (Soulèvement minimal défavorable X)
        stabilityReports.add(
            checkUpliftAndOverturning(
                direction = AxisDirection.X,
                comboName = "0.8G - Ex (Soulèvement)",
                nKN = 0.80 * nG,
                mRenvKNm = momentRenvX,
                baseLength = lx,
                baseWidth = ly,
                allowableStressBars = soilBearing * 1.30,
                minAreaRatioReq = 50.0
            )
        )

        // Combinaison 4 : 0.8G - Ey (Soulèvement minimal défavorable Y)
        stabilityReports.add(
            checkUpliftAndOverturning(
                direction = AxisDirection.Y,
                comboName = "0.8G - Ey (Soulèvement)",
                nKN = 0.80 * nG,
                mRenvKNm = momentRenvY,
                baseLength = ly,
                baseWidth = lx,
                allowableStressBars = soilBearing * 1.30,
                minAreaRatioReq = 50.0
            )
        )

        val allOk = stabilityReports.all { it.isOverturningSafe && it.isUpliftCompliant && it.isSoilBearingCompliant }

        return SoilStructureInteractionReport(
            buildingName = joint.jointName,
            siteClass = siteClass,
            foundationType = "Radier Général Épais avec Nervures Bidirectionnelles",
            foundationLengthXMeters = lx,
            foundationWidthYMeters = ly,
            foundationAreaM2 = area,
            impedance = impedance,
            stabilityReports = stabilityReports,
            globalStatus = if (allOk) "CONFORME RPA 2024 § 10.1 & DTR BC 2.33.1" else "NON-CONFORME AU SOULÈVEMENT"
        )
    }

    private fun checkUpliftAndOverturning(
        direction: AxisDirection,
        comboName: String,
        nKN: Double,
        mRenvKNm: Double,
        baseLength: Double,
        baseWidth: Double,
        allowableStressBars: Double,
        minAreaRatioReq: Double
    ): BaseOverturningAndUpliftReport {
        val mStabKNm = nKN * (baseLength / 2.0)
        val fs = if (mRenvKNm > 0.0) mStabKNm / mRenvKNm else 99.99
        val isFsSafe = fs >= 1.50

        val e = if (nKN > 0.0) mRenvKNm / nKN else baseLength
        val eRatio = e / baseLength

        // Calcul de la zone comprimée (Section trapézoïdale ou triangulaire décollée)
        val isFullContact = e <= baseLength / 6.0
        val compressedLength = if (isFullContact) {
            baseLength
        } else {
            3.0 * (baseLength / 2.0 - e)
        }
        val clampedCompressedLength = compressedLength.coerceIn(0.0, baseLength)
        val areaRatioPercent = (clampedCompressedLength / baseLength) * 100.0
        val isUpliftSafe = areaRatioPercent >= minAreaRatioReq

        // Contrainte maximale du sol sous la semelle / radier
        val sigmaMaxKPa = if (isFullContact) {
            (nKN / (baseLength * baseWidth)) * (1.0 + 6.0 * e / baseLength)
        } else {
            if (clampedCompressedLength > 0.0) {
                (2.0 * nKN) / (3.0 * baseWidth * (baseLength / 2.0 - e))
            } else 9999.0
        }
        val sigmaMaxBars = sigmaMaxKPa / 100.0
        val isBearingSafe = sigmaMaxBars <= allowableStressBars

        val obs = mutableListOf<String>()
        if (isFsSafe) {
            obs.add("Facteur de sécurité au renversement Fs = ${round(fs * 100.0) / 100.0} >= 1.50 (Vérifié).")
        } else {
            obs.add("Attention : Facteur au renversement Fs = ${round(fs * 100.0) / 100.0} < 1.50 (Risque de basculement).")
        }

        if (isUpliftSafe) {
            obs.add("Surface comprimée ${round(areaRatioPercent * 10.0) / 10.0}% >= ${minAreaRatioReq.toInt()}% prescrit par le CTC.")
        } else {
            obs.add("Décollement excessif : Zone comprimée (${round(areaRatioPercent * 10.0) / 10.0}%) insuffisante.")
        }

        if (isBearingSafe) {
            obs.add("Pointe de contrainte sol sigma = ${round(sigmaMaxBars * 100.0) / 100.0} bars <= ${round(allowableStressBars * 100.0) / 100.0} bars.")
        } else {
            obs.add("Dépassement de la capacité portante admissible sous séisme.")
        }

        return BaseOverturningAndUpliftReport(
            direction = direction,
            combinationName = comboName,
            totalVerticalLoadNKN = round(nKN * 10.0) / 10.0,
            totalOverturningMomentMKNm = round(mRenvKNm * 10.0) / 10.0,
            totalStabilizingMomentMKNm = round(mStabKNm * 10.0) / 10.0,
            overturningSafetyFactorFs = round(fs * 100.0) / 100.0,
            isOverturningSafe = isFsSafe,
            eccentricityMeters = round(e * 1000.0) / 1000.0,
            baseDimensionMeters = baseLength,
            eccentricityRatioEL = round(eRatio * 1000.0) / 1000.0,
            compressedBaseLengthMeters = round(clampedCompressedLength * 100.0) / 100.0,
            compressedAreaRatioPercent = round(areaRatioPercent * 10.0) / 10.0,
            minRequiredCompressedAreaPercent = minAreaRatioReq,
            isUpliftCompliant = isUpliftSafe,
            maxSoilStressUnderUpliftBars = round(sigmaMaxBars * 100.0) / 100.0,
            allowableSoilStressBars = round(allowableStressBars * 100.0) / 100.0,
            isSoilBearingCompliant = isBearingSafe,
            observations = obs
        )
    }
}
