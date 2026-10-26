package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

private const val COORDINATE_TOLERANCE = 1e-3

@Serializable
data class GridLine(
    val label: String,
    val coordinateMeters: Double,
    val isPrimary: Boolean = true
)

@Serializable
data class BoundingBox(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
) {
    init {
        require(maxX >= minX) { "maxX ($maxX) doit etre superieur ou egal a minX ($minX)" }
        require(maxY >= minY) { "maxY ($maxY) doit etre superieur ou egal a minY ($minY)" }
    }

    val widthX: Double get() = maxX - minX
    val lengthY: Double get() = maxY - minY
}

/**
 * Systeme d'axes entierement deduit du modele ETABS : aucune file, portee ou longueur n'est figee.
 * Les longueurs totales utilisent l'enveloppe des noeuds reelle quand elle est fournie par le parseur,
 * sinon l'enveloppe des files.
 */
@Serializable
data class GridSystem(
    val xGrids: List<GridLine> = emptyList(),
    val yGrids: List<GridLine> = emptyList(),
    val nodesBounds: BoundingBox? = null
) {
    private val xCoordinates: List<Double> get() = mergedCoordinates(xGrids)
    private val yCoordinates: List<Double> get() = mergedCoordinates(yGrids)

    val sortedXGrids: List<GridLine> get() = xGrids.sortedBy { it.coordinateMeters }
    val sortedYGrids: List<GridLine> get() = yGrids.sortedBy { it.coordinateMeters }

    val spansX: List<Double>
        get() = xCoordinates.zipWithNext { left, right -> right - left }

    val spansY: List<Double>
        get() = yCoordinates.zipWithNext { bottom, top -> top - bottom }

    val numBaysX: Int get() = spansX.size
    val numBaysY: Int get() = spansY.size

    val axisCountX: Int get() = xCoordinates.size
    val axisCountY: Int get() = yCoordinates.size

    val totalLengthLx: Double
        get() = nodesBounds?.widthX ?: extent(xCoordinates)

    val totalLengthLy: Double
        get() = nodesBounds?.lengthY ?: extent(yCoordinates)

    val maxSpanX: Double get() = spansX.maxOrNull() ?: 0.0
    val maxSpanY: Double get() = spansY.maxOrNull() ?: 0.0
    val minSpanX: Double get() = spansX.minOrNull() ?: 0.0
    val minSpanY: Double get() = spansY.minOrNull() ?: 0.0

    val hasMinimumThreeBays: Boolean get() = numBaysX >= 3 && numBaysY >= 3
    val isTrulyDynamic: Boolean get() = xGrids.isNotEmpty() || yGrids.isNotEmpty() || nodesBounds != null

    fun labelNearX(coordinate: Double): String? = nearestLabel(xGrids, coordinate)
    fun labelNearY(coordinate: Double): String? = nearestLabel(yGrids, coordinate)

    fun labelForSpanX(startMeters: Double, endMeters: Double): String {
        val from = labelNearX(startMeters) ?: "?"
        val to = labelNearX(endMeters) ?: "?"
        return "$from-$to"
    }

    fun labelForSpanY(startMeters: Double, endMeters: Double): String {
        val from = labelNearY(startMeters) ?: "?"
        val to = labelNearY(endMeters) ?: "?"
        return "$from-$to"
    }

    companion object {
        fun fromNodeCoordinates(
            xs: Collection<Double>,
            ys: Collection<Double>,
            xLabels: (Int) -> String = { ('A'.code + it).toChar().toString() },
            yLabels: (Int) -> String = { (it + 1).toString() }
        ): GridSystem {
            val uniqueX = distinctSorted(xs)
            val uniqueY = distinctSorted(ys)
            val bounds = if (uniqueX.isNotEmpty() && uniqueY.isNotEmpty()) {
                BoundingBox(uniqueX.first(), uniqueX.last(), uniqueY.first(), uniqueY.last())
            } else {
                null
            }
            return GridSystem(
                xGrids = uniqueX.mapIndexed { index, value -> GridLine(xLabels(index), value) },
                yGrids = uniqueY.mapIndexed { index, value -> GridLine(yLabels(index), value) },
                nodesBounds = bounds
            )
        }

        private fun distinctSorted(values: Collection<Double>): List<Double> {
            val sorted = values.sorted()
            val merged = mutableListOf<Double>()
            for (value in sorted) {
                if (merged.isEmpty() || abs(value - merged.last()) > COORDINATE_TOLERANCE) {
                    merged.add(value)
                }
            }
            return merged
        }

        private fun nearestLabel(lines: List<GridLine>, coordinate: Double): String? =
            lines.minByOrNull { abs(it.coordinateMeters - coordinate) }
                ?.takeIf { abs(it.coordinateMeters - coordinate) <= 0.05 }
                ?.label

        private fun extent(coordinates: List<Double>): Double =
            if (coordinates.size < 2) 0.0 else coordinates.last() - coordinates.first()

        private fun mergedCoordinates(lines: List<GridLine>): List<Double> =
            distinctSorted(lines.map { it.coordinateMeters }.filter { it.isFinite() })
    }
}
