package com.archilab.rpa2024.engine.foundations

import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * Rapport de vérification géotechnique des contraintes et tassements du sol sous SAFE.
 */
@Serializable
data class SoilBearingVerificationReport(
    val maxDisplacementDeltaMaxMeters: Double,
    val subgradeModulusKsKNM3: Double,
    val effectiveBearingStressKNM2: Double,     // sigma = Delta_max * Ks
    val allowableBearingCapacityBars: Double,
    val allowableBearingCapacityKNM2: Double,  // sigma_bar = bars * 100
    val safetyMarginPercent: Double,
    val isCompliant: Boolean,
    val observation: String
)

/**
 * Moteur de justification géotechnique des contraintes transmises au sol de fondation.
 */
class SoilBearingStressVerifier {

    /**
     * Vérifie la contrainte transmise au sol par couplage élastique (Winkler/Westergaard).
     */
    fun verifySoilBearing(
        maxDisplacementDeltaMaxMeters: Double,
        soilConfig: FoundationSoilConfig
    ): SoilBearingVerificationReport {
        val ks = soilConfig.subgradeModulusKsKNM3
        val sigmaEff = maxDisplacementDeltaMaxMeters * ks
        val sigmaBar = soilConfig.allowableBearingCapacityKNM2

        val isCompliant = sigmaEff <= (sigmaBar + 1e-3)
        val margin = if (sigmaBar > 0.0) ((sigmaBar - sigmaEff) / sigmaBar) * 100.0 else 0.0

        val observation = if (isCompliant) {
            "Contrainte du sol vérifiée : sigma = ${roundTo2Decimals(sigmaEff)} kN/m² < sigma_adm = ${roundTo2Decimals(sigmaBar)} kN/m² (Marge = ${roundTo1Decimal(margin)}%)"
        } else {
            "Dépassement de la capacité portante admissible : sigma = ${roundTo2Decimals(sigmaEff)} kN/m² > sigma_adm = ${roundTo2Decimals(sigmaBar)} kN/m² - Augmenter la largeur des semelles"
        }

        return SoilBearingVerificationReport(
            maxDisplacementDeltaMaxMeters = maxDisplacementDeltaMaxMeters,
            subgradeModulusKsKNM3 = ks,
            effectiveBearingStressKNM2 = roundTo2Decimals(sigmaEff),
            allowableBearingCapacityBars = soilConfig.allowableBearingCapacityBars,
            allowableBearingCapacityKNM2 = roundTo2Decimals(sigmaBar),
            safetyMarginPercent = roundTo1Decimal(margin),
            isCompliant = isCompliant,
            observation = observation
        )
    }

    /**
     * Calcule la largeur minimale préliminaire d'une semelle filante : B_min = N_ser / (L * sigma_adm).
     */
    fun preSizeStripFootingWidth(
        totalServiceLoadNserKN: Double,
        footingLengthMeters: Double,
        allowableCapacityKNM2: Double
    ): Double {
        if (footingLengthMeters <= 0.0 || allowableCapacityKNM2 <= 0.0) return 1.00
        val rawWidth = totalServiceLoadNserKN / (footingLengthMeters * allowableCapacityKNM2)
        // Arrondi au multiple supérieur de 10 cm (ex: 1.14 m -> 1.20 m)
        return kotlin.math.ceil(rawWidth * 10.0) / 10.0
    }

    private fun roundTo1Decimal(value: Double): Double = round(value * 10.0) / 10.0
    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0
}
