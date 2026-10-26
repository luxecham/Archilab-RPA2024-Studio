package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable

@Serializable
data class StoryLevel(
    val storyName: String,
    val heightHk: Double,
    val elevationZk: Double,
    val isPenthouseOrCage: Boolean = false
) {
    init {
        require(heightHk > 0.0) { "La hauteur du niveau '${storyName}' doit etre positive (h=${heightHk})" }
    }

    val topElevationZk: Double get() = elevationZk + heightHk
}

/**
 * Niveaux reconstruits depuis ETABS. Hauteur Hn et nombre de niveaux N excluent les cages
 * d'escalier / édicules (isPenthouseOrCage) conformement a l'usage de la methode statique equivalente.
 */
@Serializable
data class StorySystem(val stories: List<StoryLevel> = emptyList()) {

    val orderedStories: List<StoryLevel> get() = stories.sortedBy { it.elevationZk }
    val mainStories: List<StoryLevel> get() = orderedStories.filter { !it.isPenthouseOrCage }
    val penthouseStories: List<StoryLevel> get() = orderedStories.filter { it.isPenthouseOrCage }

    val numStoriesN: Int get() = mainStories.size
    val numStoriesIncludingPenthouse: Int get() = orderedStories.size

    val totalHeightHn: Double get() = mainStories.sumOf { it.heightHk }

    val totalHeightIncludingPenthouse: Double
        get() {
            val levels = orderedStories
            if (levels.isEmpty()) return 0.0
            return levels.maxOf { it.topElevationZk } - levels.minOf { it.elevationZk }
        }

    val baseElevationZ: Double get() = orderedStories.minOfOrNull { it.elevationZk } ?: 0.0
    val topElevationZ: Double get() = orderedStories.maxOfOrNull { it.topElevationZk } ?: 0.0
    val maxStoryHeightMeters: Double get() = orderedStories.maxOfOrNull { it.heightHk } ?: 0.0
    val storeyHeightList: List<Double> get() = orderedStories.map { it.heightHk }
    val storyNames: List<String> get() = orderedStories.map { it.storyName }

    fun level(storyName: String): StoryLevel? =
        orderedStories.firstOrNull { it.storyName.equals(storyName.trim(), ignoreCase = true) }

    fun levelAtElevation(elevation: Double, tolerance: Double = 0.01): StoryLevel? =
        orderedStories.firstOrNull { kotlin.math.abs(it.elevationZk - elevation) <= tolerance }

    fun hasAtLeastTwoStories(): Boolean = numStoriesN >= 2
}
