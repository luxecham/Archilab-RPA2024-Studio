package com.archilab.rpa2024.normative

import com.archilab.rpa2024.model.PenaltyCategory
import kotlinx.serialization.Serializable

/**
 * Zones sismiques algériennes et coefficients d'accélération de zone A (Tableau 3.2 RPA 2024).
 * Période de retour Tr = 475 ans, rocher S1.
 */
@Serializable
enum class SeismicZone(
    val code: String,
    val level: String,
    val coeffA: Double
) {
    ZONE_0("0", "Très faible", 0.00),
    ZONE_I("I", "Faible", 0.07),
    ZONE_II("II", "Faible à moyenne", 0.10),
    ZONE_III("III", "Moyenne", 0.15),
    ZONE_IV("IV", "Moyenne à élevée", 0.20),
    ZONE_V("V", "Élevée", 0.25),
    ZONE_VI("VI", "Élevée", 0.30);

    companion object {
        fun fromCode(code: String): SeismicZone =
            entries.find { it.code.equals(code.trim(), ignoreCase = true) }
                ?: entries.find { it.name.endsWith(code.trim().uppercase()) }
                ?: ZONE_III
    }
}

/**
 * Classes de sites géotechniques (Tableau 3.1 RPA 2024).
 */
@Serializable
enum class SiteClass(
    val code: String,
    val description: String
) {
    S1("S1", "Rocheux ou assimilé (Vs > 800 m/s)"),
    S2("S2", "Ferme (360 < Vs <= 800 m/s, N30 > 50, qc > 15 MPa)"),
    S3("S3", "Meuble (180 <= Vs <= 360 m/s, 15 <= N30 <= 50)"),
    S4("S4", "Très meuble (Vs < 180 m/s, N30 < 15, qc < 1.5 MPa)"),
    S5("S5", "Spécifique (nécessitant investigations approfondies)");

    companion object {
        fun fromCode(code: String): SiteClass =
            entries.find { it.code.equals(code.trim(), ignoreCase = true) } ?: S3
    }
}

/**
 * Groupes d'importance des ouvrages, coefficient I et période de retour Tr (Tableaux 3.10 & 1.1 RPA 2024).
 */
@Serializable
enum class ImportanceGroup(
    val code: String,
    val description: String,
    val coeffI: Double,
    val returnPeriodTr: Int,
    val nuATabulated: Double
) {
    GROUP_1A("1A", "Bâtiments d'importance vitale (casernes, police, hôpitaux, PC de crise)", 1.40, 1178, 0.39),
    GROUP_1B("1B", "Bâtiments de grande importance (écoles, grands rassemblements)", 1.20, 777, 0.46),
    GROUP_2("2", "Bâtiments d'usage courant (habitations, bureaux courants)", 1.00, 475, 0.55),
    GROUP_3("3", "Bâtiments de faible importance (hangars agricoles, constructions temporaires)", 0.80, 260, 0.69);

    companion object {
        fun fromCode(code: String): ImportanceGroup =
            entries.find { it.code.equals(code.trim(), ignoreCase = true) } ?: GROUP_1A
    }
}

/**
 * Systèmes de contreventement en béton armé, coefficient de comportement R (Tableau 3.6),
 * catégorie de pénalité Qf (Tableau 3.18), coefficient Ct (Tableau 4.3)
 * et limitations en hauteur et nombre de niveaux (Tableau 3.14 RPA 2024).
 */
@Serializable
enum class BracingSystem(
    val id: Int,
    val description: String,
    val coeffR: Double,
    val penaltyCategory: PenaltyCategory,
    val defaultCt: Double,
    val maxStoriesZ1Z2: Int = 999,
    val maxHeightZ1Z2Meters: Double = 999.0,
    val maxStoriesZ3: Int = 999,
    val maxHeightZ3Meters: Double = 999.0,
    val maxStoriesZ4: Int = 999,
    val maxHeightZ4Meters: Double = 999.0,
    val maxStoriesZ5Z6: Int = 999,
    val maxHeightZ5Z6Meters: Double = 999.0
) {
    CAT_1(
        1,
        "Système à ossature (portiques auto-stables sans maçonnerie rigide)",
        5.5,
        PenaltyCategory.CATEGORY_A,
        0.075
    ),
    CAT_2(
        2,
        "Système à contreventement mixte, équivalent à une ossature",
        5.5,
        PenaltyCategory.CATEGORY_A,
        0.050
    ),
    CAT_3(
        3,
        "Système à ossature ou mixte équivalent à ossature avec remplissage en maçonnerie rigide",
        3.5,
        PenaltyCategory.CATEGORY_A,
        0.050,
        maxStoriesZ1Z2 = 5,
        maxHeightZ1Z2Meters = 17.0,
        maxStoriesZ3 = 4,
        maxHeightZ3Meters = 14.0,
        maxStoriesZ4 = 3,
        maxHeightZ4Meters = 11.0,
        maxStoriesZ5Z6 = 2,
        maxHeightZ5Z6Meters = 8.0
    ),
    CAT_4(
        4,
        "Système à contreventement mixte, équivalent à des voiles",
        4.5,
        PenaltyCategory.CATEGORY_B,
        0.050
    ),
    CAT_5(
        5,
        "Système de contreventement constitué par des voiles",
        4.5,
        PenaltyCategory.CATEGORY_B,
        0.050
    ),
    CAT_6(
        6,
        "Système à ossature à noyau ou à effet noyau",
        3.0,
        PenaltyCategory.CATEGORY_B,
        0.050
    ),
    CAT_7(
        7,
        "Système fonctionnant en console verticale à masses réparties prédominantes",
        3.0,
        PenaltyCategory.CATEGORY_B,
        0.050
    ),
    CAT_8(
        8,
        "Système en pendule inversé",
        2.0,
        PenaltyCategory.CATEGORY_B,
        0.050
    ),
    CAT_9(
        9,
        "Système de voiles de grandes dimensions en béton peu armé",
        1.5,
        PenaltyCategory.CATEGORY_B,
        0.050
    );

    fun getMaxAllowedStories(zone: SeismicZone): Int = when (zone) {
        SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II -> maxStoriesZ1Z2
        SeismicZone.ZONE_III -> maxStoriesZ3
        SeismicZone.ZONE_IV -> maxStoriesZ4
        SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> maxStoriesZ5Z6
    }

    fun getMaxAllowedHeight(zone: SeismicZone): Double = when (zone) {
        SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II -> maxHeightZ1Z2Meters
        SeismicZone.ZONE_III -> maxHeightZ3Meters
        SeismicZone.ZONE_IV -> maxHeightZ4Meters
        SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> maxHeightZ5Z6Meters
    }
}

/**
 * Paramètres spectraux d'accélération (Tableaux 3.3 et 3.4 RPA 2024).
 */
@Serializable
data class SpectralParams(
    val s: Double,
    val t1: Double,
    val t2: Double,
    val t3: Double,
    val spectrumType: Int
)

/**
 * Résultat du calcul et décomposition du facteur de qualité Q_F (Tableau 3.18 RPA 2024).
 */
@Serializable
data class QualityFactorResult(
    val qf: Double,
    val category: PenaltyCategory,
    val penalties: Map<String, Double>,
    val isPlanRegular: Boolean,
    val isElevRegular: Boolean,
    val hasMinStories: Boolean,
    val hasMinBays: Boolean,
    val hasRedundantWalls: Boolean
)

/**
 * Paramètres complets d'analyse spectrale selon RPA 2024.
 */
@Serializable
data class RpaSpectrumParams(
    val zone: SeismicZone,
    val site: SiteClass,
    val importance: ImportanceGroup,
    val bracing: BracingSystem,
    val qualityFactorQf: Double = 1.0,
    val dampingXiPercent: Double = 7.0
) {
    val zoneAccelerationA: Double get() = zone.coeffA
    val siteClass: String get() = site.code
    val siteFactorS: Double get() = Rpa2024NormativeDb.getSpectralParameters(zone, site).s
    val importanceFactorI: Double get() = importance.coeffI
    val behaviorFactorR: Double get() = bracing.coeffR
    val t1: Double get() = Rpa2024NormativeDb.getSpectralParameters(zone, site).t1
    val t2: Double get() = Rpa2024NormativeDb.getSpectralParameters(zone, site).t2
    val t3: Double get() = Rpa2024NormativeDb.getSpectralParameters(zone, site).t3
}
