package com.archilab.rpa2024.normative

/** Regroupement des zones des tableaux de limitation de hauteur (I et II, III, IV, V et VI). */
enum class ZoneGroup(val label: String) {
    I_II("I et II"),
    III("III"),
    IV("IV"),
    V_VI("V et VI");

    companion object {
        fun of(zone: SeismicZone): ZoneGroup? = when (zone) {
            SeismicZone.ZONE_I, SeismicZone.ZONE_II -> I_II
            SeismicZone.ZONE_III -> III
            SeismicZone.ZONE_IV -> IV
            SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> V_VI
            SeismicZone.ZONE_0 -> null
        }
    }
}

/**
 * Limitation en nombre de niveaux et en hauteur. Une valeur nulle signifie que le DTR
 * ne fixe pas de limite pour ce critere (et non une limite nulle).
 */
data class HeightLimit(
    val maxStories: Int?,
    val maxHeightMeters: Double?,
    val reference: String
) {
    val hasAnyLimit: Boolean get() = maxStories != null || maxHeightMeters != null
}

data class HeightCompliance(
    val limit: HeightLimit,
    val storiesConsidered: Int,
    val heightConsideredMeters: Double,
    val storiesSatisfied: Boolean?,
    val heightSatisfied: Boolean?,
    val mainStructureSatisfied: Boolean
) {
    val isSatisfied: Boolean
        get() = storiesSatisfied != false && heightSatisfied != false
}

/**
 * Tableau 3.17 : systemes de contreventement en beton arme (1 a 9), coefficient R et
 * categorie de penalite (a), (b) ou (c) du Tableau 3.18.
 * Les limites de hauteur proviennent des Tableaux 3.11, 3.12, 3.13 et du paragraphe 3.5 (systeme 5).
 */
enum class BracingSystem(
    val id: Int,
    val description: String,
    val coeffR: Double,
    val penaltyCategory: PenaltyCategory,
    val defaultCt: Double,
    val hasWallBracing: Boolean = false,
    private val limitsByGroup: Map<ZoneGroup, HeightLimit> = emptyMap()
) {
    OSSATURE(
        1, "Système à ossature", 5.5, PenaltyCategory.A, 0.075, false,
        mapOf(
            ZoneGroup.I_II to HeightLimit(5, 17.0, "Tableau 3.11"),
            ZoneGroup.III to HeightLimit(4, 14.0, "Tableau 3.11"),
            ZoneGroup.IV to HeightLimit(3, 11.0, "Tableau 3.11"),
            ZoneGroup.V_VI to HeightLimit(3, 11.0, "Tableau 3.11")
        )
    ),
    MIXTE_EQUIVALENT_OSSATURE(
        2, "Système à contreventement mixte, équivalent à une ossature", 5.5, PenaltyCategory.A, 0.050, false,
        mapOf(
            ZoneGroup.I_II to HeightLimit(7, 23.0, "Tableau 3.12"),
            ZoneGroup.III to HeightLimit(6, 20.0, "Tableau 3.12"),
            ZoneGroup.IV to HeightLimit(5, 17.0, "Tableau 3.12"),
            ZoneGroup.V_VI to HeightLimit(5, 17.0, "Tableau 3.12")
        )
    ),
    OSSATURE_AVEC_REMPLISSAGE_RIGIDE(
        3, "Système à ossature ou mixte équivalent à ossature avec remplissage en maçonnerie rigide",
        3.5, PenaltyCategory.A, 0.050, false,
        mapOf(
            ZoneGroup.I_II to HeightLimit(5, 17.0, "Tableau 3.13"),
            ZoneGroup.III to HeightLimit(4, 14.0, "Tableau 3.13"),
            ZoneGroup.IV to HeightLimit(3, 11.0, "Tableau 3.13"),
            ZoneGroup.V_VI to HeightLimit(2, 8.0, "Tableau 3.13")
        )
    ),
    MIXTE_EQUIVALENT_VOILES(
        4, "Système à contreventement mixte, équivalent à des voiles", 4.5, PenaltyCategory.B, 0.050, true
    ),
    VOILES(
        5, "Système de contreventement constitué par des voiles", 4.5, PenaltyCategory.B, 0.050, true,
        mapOf(
            ZoneGroup.I_II to HeightLimit(null, 48.0, "§ 3.5 système 5"),
            ZoneGroup.III to HeightLimit(null, 48.0, "§ 3.5 système 5"),
            ZoneGroup.IV to HeightLimit(null, 48.0, "§ 3.5 système 5"),
            ZoneGroup.V_VI to HeightLimit(null, 48.0, "§ 3.5 système 5")
        )
    ),
    OSSATURE_NOYAU(
        6, "Système à ossature à noyau ou à effet noyau", 3.0, PenaltyCategory.B, 0.050, true
    ),
    CONSOLE_VERTICALE(
        7, "Système fonctionnant en console verticale à masses réparties prédominantes",
        3.0, PenaltyCategory.B, 0.050, false
    ),
    PENDULE_INVERSE(
        8, "Système en pendule inversé", 2.0, PenaltyCategory.C, 0.050, false
    ),
    VOILES_GRANDES_DIMENSIONS(
        9, "Système de voiles de grandes dimensions en béton peu armé",
        1.5, PenaltyCategory.C, 0.050, true
    );

    fun heightLimit(zone: SeismicZone): HeightLimit? = ZoneGroup.of(zone)?.let { limitsByGroup[it] }

    fun heightCompliance(
        zone: SeismicZone,
        storiesIncludingPenthouse: Int,
        heightIncludingPenthouseMeters: Double,
        mainStories: Int,
        mainHeightMeters: Double
    ): HeightCompliance? {
        val limit = heightLimit(zone) ?: return null
        val storiesOk = limit.maxStories?.let { storiesIncludingPenthouse <= it }
        val heightOk = limit.maxHeightMeters?.let { heightIncludingPenthouseMeters <= it + 1e-6 }
        val mainOk = (limit.maxStories?.let { mainStories <= it } ?: true) &&
            (limit.maxHeightMeters?.let { mainHeightMeters <= it + 1e-6 } ?: true)
        return HeightCompliance(
            limit = limit,
            storiesConsidered = storiesIncludingPenthouse,
            heightConsideredMeters = heightIncludingPenthouseMeters,
            storiesSatisfied = storiesOk,
            heightSatisfied = heightOk,
            mainStructureSatisfied = mainOk
        )
    }

    companion object {
        fun fromId(id: Int): BracingSystem =
            entries.firstOrNull { it.id == id }
                ?: throw NormativeDataException(
                    "Systeme de contreventement id=$id inconnu. Valeurs admises : 1 a 9 (Tableau 3.17, beton arme)."
                )
    }
}
