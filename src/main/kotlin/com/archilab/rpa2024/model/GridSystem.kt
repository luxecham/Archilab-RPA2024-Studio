package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Ligne de trame (axe de file) dans le système de coordonnées global.
 */
@Serializable
data class GridLine(
    val id: String,
    val coordinate: Double,
    val direction: AxisDirection
) : Comparable<GridLine> {
    override fun compareTo(other: GridLine): Int = coordinate.compareTo(other.coordinate)
}

/**
 * Système dynamique de trames / axes découvert automatiquement depuis ETABS.
 * Garantit qu'aucune cote n'est codée en dur.
 */
@Serializable
data class GridSystem(
    val xGrids: List<GridLine>,
    val yGrids: List<GridLine>
) {
    init {
        require(xGrids.isNotEmpty()) { "Le système d'axes doit comporter au moins un axe X." }
        require(yGrids.isNotEmpty()) { "Le système d'axes doit comporter au moins un axe Y." }
    }

    /** Axes X triés par coordonnée croissante */
    val sortedXGrids: List<GridLine> = xGrids.sorted()

    /** Axes Y triés par coordonnée croissante */
    val sortedYGrids: List<GridLine> = yGrids.sorted()

    /**
     * Portées / entraxes successifs selon l'axe X (Δx_i = x_{i+1} - x_i).
     */
    val spansX: List<Double> = if (sortedXGrids.size >= 2) {
        sortedXGrids.zipWithNext { a, b -> b.coordinate - a.coordinate }
    } else emptyList()

    /**
     * Portées / entraxes successifs selon l'axe Y (Δy_j = y_{j+1} - y_j).
     */
    val spansY: List<Double> = if (sortedYGrids.size >= 2) {
        sortedYGrids.zipWithNext { a, b -> b.coordinate - a.coordinate }
    } else emptyList()

    /**
     * Longueur totale d'emprise selon X : Lx = max(X) - min(X).
     */
    val totalLengthLx: Double = if (sortedXGrids.isNotEmpty()) {
        sortedXGrids.last().coordinate - sortedXGrids.first().coordinate
    } else 0.0

    /**
     * Longueur totale d'emprise selon Y : Ly = max(Y) - min(Y).
     */
    val totalLengthLy: Double = if (sortedYGrids.isNotEmpty()) {
        sortedYGrids.last().coordinate - sortedYGrids.first().coordinate
    } else 0.0

    /**
     * Nombre de travées selon X (utilisé pour la vérification du critère minimal >= 3 travées).
     */
    val numBaysX: Int = spansX.size

    /**
     * Nombre de travées selon Y (utilisé pour la vérification du critère minimal >= 3 travées).
     */
    val numBaysY: Int = spansY.size

    /**
     * Vérifie si le critère de redondance minimale de travées (Tableau 3.18 RPA 2024, Cat a) est satisfait.
     */
    val satisfiesMinBaysCondition: Boolean = (numBaysX >= 3 && numBaysY >= 3)

    /**
     * Recherche la ligne de grille la plus proche d'une coordonnée donnée avec une tolérance.
     */
    fun findNearestGrid(coord: Double, direction: AxisDirection, tolerance: Double = 0.05): GridLine? {
        val list = if (direction == AxisDirection.X) sortedXGrids else sortedYGrids
        return list.minByOrNull { abs(it.coordinate - coord) }?.takeIf { abs(it.coordinate - coord) <= tolerance }
    }
}
