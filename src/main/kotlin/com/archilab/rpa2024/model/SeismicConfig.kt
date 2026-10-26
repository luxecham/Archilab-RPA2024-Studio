package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable

/**
 * Parametres geotechniques disponibles pour classer le site (Tableau 3.1 du RPA 2024).
 * Vs30 reste le parametre le plus fiable ; les autres essais servent de repli.
 */
@Serializable
data class SiteParameters(
    val vs30MetersPerSecond: Double? = null,
    val n30: Double? = null,
    val qc30MPa: Double? = null,
    val cu30KPa: Double? = null,
    val rc30MPa: Double? = null,
    val pl30MPa: Double? = null,
    val ep30MPa: Double? = null,
    val softClayThicknessMeters: Double = 0.0,
    val requiresSpecificStudy: Boolean = false
) {
    val hasAnyMeasurement: Boolean
        get() = vs30MetersPerSecond != null || n30 != null || qc30MPa != null || cu30KPa != null ||
            rc30MPa != null || pl30MPa != null || ep30MPa != null
}

/**
 * Configuration sismique saisie par l'utilisateur ou deduite du projet, volontairement
 * independante du package normative : les codes sont resolus par la base RPA 2024.
 */
@Serializable
data class SeismicConfig(
    val zoneCode: String = "III",
    val siteClassCode: String = "S3",
    val siteParameters: SiteParameters? = null,
    val importanceGroupCode: String = "2",
    val occupancyCaseCode: String = "HABITATION_BUREAUX",
    val bracingSystemId: Int = 3,
    val infillType: InfillType = InfillType.DENSE,
    val specificStudySite: Boolean = false,
    val ctCoefficientOverride: Double? = null,
    val behaviorFactorOverride: Double? = null,
    val qualityFactorOverride: Double? = null,
    val dampingPercentOverride: Double? = null,
    val psiOverride: Double? = null,
    val lambdaOverride: Double? = null,
    val useMeanNuA: Boolean = false,
    val planRegular: Boolean = true,
    val elevationRegular: Boolean = true,
    val redundantWallsInPlan: Boolean = false
) {
    val usesOverrides: Boolean
        get() = ctCoefficientOverride != null || behaviorFactorOverride != null ||
            qualityFactorOverride != null || dampingPercentOverride != null ||
            psiOverride != null || lambdaOverride != null
}
