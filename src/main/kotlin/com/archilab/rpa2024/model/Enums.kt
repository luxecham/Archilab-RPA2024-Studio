package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable

/**
 * Direction axiale orthogonale dans le repère global du bâtiment.
 */
@Serializable
enum class AxisDirection {
    X,
    Y
}

/**
 * Forme géométrique de la section transversale du poteau.
 */
@Serializable
enum class ColumnShape {
    RECTANGULAR,
    CIRCULAR
}

/**
 * Rôle structurel d'une poutre dans la descente de charges et le contreventement.
 */
@Serializable
enum class BeamRole {
    MAIN_BEAM,      // Poutre porteuse principale (supportant les poutrelles du plancher)
    SECONDARY_TIE   // Poutre secondaire / Chaînage de liaison
}

/**
 * Classification de l'élancement géométrique des voiles de contreventement (§ 7.7.4 RPA 2024).
 */
@Serializable
enum class WallSlenderness {
    SLENDER_WALL,          // hw / lw > 2.0 (Voile élancé avec zone critique à la base)
    SHORT_WALL,            // 2/3 < hw / lw <= 2.0 (Voile court)
    LARGE_DIMENSION_WALL   // hw / lw <= 2/3 (Voile de grandes dimensions)
}

/**
 * Catégorie de pénalité pour le calcul du facteur de qualité Q_F (Tableau 3.18 RPA 2024).
 */
@Serializable
enum class PenaltyCategory {
    CATEGORY_A, // Systèmes à ossature ou mixtes équivalents à une ossature
    CATEGORY_B  // Systèmes à voiles ou mixtes équivalents à des voiles
}

/**
 * Catégories de charges d'exploitation et coefficient d'accompagnement psi (Tableau 4.2 RPA 2024).
 */
@Serializable
enum class LiveLoadCategory(
    val caseNumber: Int,
    val description: String,
    val psi: Double
) {
    HABITATION_BUREAUX(1, "Bâtiments d'habitation, bureaux ou assimilés", 0.30),
    PUBLIC_TEMPORAIRE(2, "Bâtiments recevant du public temporairement (salles de sport, culte, classes...)", 0.40),
    ENTREPOTS_HANGARS(3, "Entrepôts, hangars", 0.50),
    ARCHIVES_CRITIQUES(4, "Archives, bibliothèques, réservoirs, équipements médicaux critiques", 1.00),
    AUTRES_LOCAUX(5, "Autres locaux non visés ci-dessus", 0.60)
}
