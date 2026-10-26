package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable

/**
 * Définition d'un niveau d'étage du bâtiment.
 */
@Serializable
data class StoryLevel(
    val storyName: String,
    val heightHk: Double,
    val elevationZk: Double,
    val isPenthouseOrCage: Boolean = false
) : Comparable<StoryLevel> {
    override fun compareTo(other: StoryLevel): Int = elevationZk.compareTo(other.elevationZk)
}

/**
 * Système d'étages dynamique découvert automatiquement depuis ETABS.
 */
@Serializable
data class StorySystem(
    val stories: List<StoryLevel>
) {
    init {
        require(stories.isNotEmpty()) { "Le modèle doit comporter au moins un niveau d'étage." }
    }

    /** Étages triés par élévation croissante (du bas vers le haut) */
    val sortedStories: List<StoryLevel> = stories.sorted()

    /** Niveaux principaux excluant les édicules/cages d'escalier/machineries d'ascenseur */
    val mainStories: List<StoryLevel> = sortedStories.filter { !it.isPenthouseOrCage }

    /**
     * Hauteur totale Hn mesurée depuis la base jusqu'au dernier niveau de plancher courant
     * (utilisée dans la formule empirique T_empirique = C_T * H_n^(3/4)).
     */
    val totalHeightHn: Double = mainStories.sumOf { it.heightHk }

    /**
     * Élévation maximale absolue du bâtiment (incluant cage/édicule le cas échéant).
     */
    val totalBuildingElevation: Double = sortedStories.maxOfOrNull { it.elevationZk } ?: 0.0

    /**
     * Nombre de niveaux N au-dessus du sol (niveaux principaux).
     * Utilisé pour vérifier :
     * 1. N >= 2 (critère Tableau 3.18)
     * 2. Limites en nombre de niveaux (Tableau 3.14)
     * 3. Coefficient lambda : lambda = 0.85 si T0 <= 2*T2 et N > 2, sinon 1.00.
     */
    val numStoriesN: Int = mainStories.size

    /**
     * Recherche un étage par son nom (insensible à la casse et aux espaces).
     */
    fun findStory(name: String): StoryLevel? =
        sortedStories.find { it.storyName.trim().equals(name.trim(), ignoreCase = true) }

    /**
     * Retourne le niveau d'étage immédiatement inférieur (pour les déplacements relatifs Delta_k = delta_k - delta_{k-1}).
     */
    fun getLowerStory(story: StoryLevel): StoryLevel? {
        val idx = sortedStories.indexOf(story)
        return if (idx > 0) sortedStories[idx - 1] else null
    }
}
