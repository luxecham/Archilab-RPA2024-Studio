package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * Famille géométrique de poteaux regroupant tous les éléments ayant la même section et typologie.
 */
@Serializable
data class ColumnFamily(
    val name: String,
    val shape: ColumnShape,
    val b1Cm: Double,
    val h1Cm: Double,
    val diameterCm: Double? = null,
    val clearHeightLclMeters: Double,
    val isWallBoundary: Boolean = false,
    val elementIds: List<String> = emptyList()
) {
    /**
     * Aire de la section brute de béton B_c en cm².
     */
    val grossAreaBcCm2: Double
        get() = when (shape) {
            ColumnShape.RECTANGULAR -> b1Cm * h1Cm
            ColumnShape.CIRCULAR -> {
                val d = diameterCm ?: b1Cm
                PI * d * d / 4.0
            }
        }

    /**
     * Dimension transversale minimale en cm (min(b1, h1) ou diamètre D).
     */
    val minDimensionCm: Double
        get() = when (shape) {
            ColumnShape.RECTANGULAR -> min(b1Cm, h1Cm)
            ColumnShape.CIRCULAR -> diameterCm ?: b1Cm
        }

    /**
     * Dimension transversale maximale en cm (max(b1, h1) ou diamètre D).
     */
    val maxDimensionCm: Double
        get() = when (shape) {
            ColumnShape.RECTANGULAR -> max(b1Cm, h1Cm)
            ColumnShape.CIRCULAR -> diameterCm ?: b1Cm
        }

    /**
     * Rapport d'élancement de coffrage b1 / h1.
     */
    val aspectRatio: Double
        get() = if (h1Cm > 0.0) b1Cm / h1Cm else 1.0
}

/**
 * Famille géométrique de poutres regroupant tous les éléments de même section et rôle.
 */
@Serializable
data class BeamFamily(
    val name: String,
    val role: BeamRole,
    val bCm: Double,
    val hCm: Double,
    val maxClearSpanMeters: Double,
    val minClearSpanMeters: Double = maxClearSpanMeters,
    val maxSupportWidthCm: Double = 35.0,
    val maxAxisEccentricityCm: Double = 0.0,
    val elementIds: List<String> = emptyList()
) {
    /**
     * Rapport d'élancement de la section h / b.
     */
    val aspectRatio: Double
        get() = if (bCm > 0.0) hCm / bCm else 0.0

    /**
     * Aire brute de la section en cm².
     */
    val grossAreaCm2: Double
        get() = bCm * hCm
}

/**
 * Famille de voiles de contreventement (Shear Wall / Pier).
 */
@Serializable
data class ShearWallFamily(
    val pierName: String,
    val direction: AxisDirection,
    val lengthLwMeters: Double,
    val thicknessBwMeters: Double,
    val clearHeightHeMeters: Double,
    val totalHeightHwMeters: Double,
    val stories: List<String> = emptyList()
) {
    /**
     * Aire de la section transversale brute en m².
     */
    val grossAreaM2: Double
        get() = lengthLwMeters * thicknessBwMeters

    /**
     * Aire de la section transversale brute en cm².
     */
    val grossAreaCm2: Double
        get() = grossAreaM2 * 10000.0

    /**
     * Rapport d'élancement hw / lw (§ 7.7.4 RPA 2024).
     */
    val slendernessRatio: Double
        get() = if (lengthLwMeters > 0.0) totalHeightHwMeters / lengthLwMeters else 0.0

    /**
     * Qualification du voile selon son élancement.
     */
    val slendernessType: WallSlenderness
        get() = when {
            slendernessRatio > 2.0 -> WallSlenderness.SLENDER_WALL
            slendernessRatio > (2.0 / 3.0) -> WallSlenderness.SHORT_WALL
            else -> WallSlenderness.LARGE_DIMENSION_WALL
        }
}

/**
 * Modèle de bande de plancher (poutrelles continues en corps creux).
 */
@Serializable
data class SlabStripTopology(
    val name: String,
    val totalThicknessHtCm: Double,
    val compressionSlabH0Cm: Double = 5.0,
    val hollowBlockHeightCm: Double = 16.0,
    val ribWidthB0Cm: Double = 12.0,
    val flangeWidthBCm: Double = 60.0,
    val continuousSpansMeters: List<Double> = emptyList()
) {
    /**
     * Portée maximale de calcul de la travée continue la plus défavorable.
     */
    val maxSpanLmaxMeters: Double
        get() = continuousSpansMeters.maxOrNull() ?: 0.0
}

/**
 * Catalogue complet des sections d'un Joint/Bloc découvert depuis le modèle ETABS.
 */
@Serializable
data class SectionCatalog(
    val columns: List<ColumnFamily> = emptyList(),
    val beams: List<BeamFamily> = emptyList(),
    val shearWalls: List<ShearWallFamily> = emptyList(),
    val slabStrips: List<SlabStripTopology> = emptyList()
) {
    fun findColumn(name: String): ColumnFamily? =
        columns.find { it.name.equals(name, ignoreCase = true) }

    fun findBeam(name: String): BeamFamily? =
        beams.find { it.name.equals(name, ignoreCase = true) }

    fun findShearWall(name: String): ShearWallFamily? =
        shearWalls.find { it.pierName.equals(name, ignoreCase = true) }
}
