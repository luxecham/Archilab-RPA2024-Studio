package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs

@Serializable
enum class AxisDirection(val label: String) {
    X("X-X"),
    Y("Y-Y")
}

@Serializable
enum class ColumnShape {
    RECTANGULAR,
    CIRCULAR
}

@Serializable
enum class BeamRole {
    MAIN_BEAM,
    SECONDARY_TIE
}

@Serializable
enum class WallSlenderness {
    SLENDER_WALL,
    SHORT_WALL,
    LARGE_DIMENSION_WALL
}

@Serializable
enum class InfillType {
    LIGHT,
    DENSE
}

@Serializable
data class ColumnFamily(
    val name: String,
    val shape: ColumnShape,
    val b1Cm: Double,
    val h1Cm: Double,
    val clearHeightLclMeters: Double = 0.0,
    val diameterCm: Double? = null,
    val isWallBoundary: Boolean = false,
    val supportingBeamHeightCm: Double? = null,
    val stories: List<String> = emptyList(),
    val elementIds: List<String> = emptyList()
) {
    val effectiveDiameterCm: Double get() = if (shape == ColumnShape.CIRCULAR) (diameterCm ?: b1Cm) else 0.0
    val areaCm2: Double
        get() = if (shape == ColumnShape.CIRCULAR) {
            PI * effectiveDiameterCm * effectiveDiameterCm / 4.0
        } else {
            b1Cm * h1Cm
        }
    val areaM2: Double get() = areaCm2 / 10_000.0
    val minDimensionCm: Double
        get() = if (shape == ColumnShape.CIRCULAR) effectiveDiameterCm else minOf(b1Cm, h1Cm)
    val maxDimensionCm: Double
        get() = if (shape == ColumnShape.CIRCULAR) effectiveDiameterCm else maxOf(b1Cm, h1Cm)
    val aspectRatioB1OverH1: Double get() = if (h1Cm == 0.0) 0.0 else b1Cm / h1Cm
    val slendernessMinDimensionOverLcl: Double
        get() = if (clearHeightLclMeters <= 0.0) 0.0 else minDimensionCm / (clearHeightLclMeters * 100.0)
    val geometryLabel: String
        get() = if (shape == ColumnShape.CIRCULAR) {
            "D=${formatCm(effectiveDiameterCm)} cm"
        } else {
            "${formatCm(b1Cm)}x${formatCm(h1Cm)} cm"
        }
}

@Serializable
data class BeamFamily(
    val name: String,
    val role: BeamRole,
    val bCm: Double,
    val hCm: Double,
    val maxClearSpanMeters: Double,
    val minClearSpanMeters: Double = 0.0,
    val maxSupportWidthCm: Double = 0.0,
    val maxSupportDepthCm: Double? = null,
    val maxAxisEccentricityCm: Double = 0.0,
    val continuousSpansMeters: List<Double> = emptyList(),
    val elementIds: List<String> = emptyList()
) {
    val supportWidthCm: Double get() = maxSupportWidthCm
    val supportDepthCm: Double get() = maxSupportDepthCm ?: maxSupportWidthCm
    val minRecommendedHeightCm: Double get() = maxClearSpanMeters * 100.0 / 15.0
    val maxRecommendedHeightCm: Double get() = maxClearSpanMeters * 100.0 / 10.0
    val aspectRatioHeightOverWidth: Double get() = if (bCm == 0.0) 0.0 else hCm / bCm
    val geometryLabel: String get() = "${formatCm(bCm)}x${formatCm(hCm)} cm"
    val isContinuous: Boolean get() = continuousSpansMeters.size > 1
}

@Serializable
data class ShearWallFamily(
    val pierName: String,
    val direction: AxisDirection,
    val lengthLwMeters: Double,
    val thicknessBwMeters: Double,
    val clearHeightHeMeters: Double,
    val totalHeightHwMeters: Double,
    val stories: List<String> = emptyList(),
    val elementIds: List<String> = emptyList()
) {
    val slendernessHwOverLw: Double get() = if (lengthLwMeters <= 0.0) Double.MAX_VALUE else totalHeightHwMeters / lengthLwMeters
    val classification: WallSlenderness
        get() = when {
            slendernessHwOverLw > 2.0 -> WallSlenderness.SLENDER_WALL
            slendernessHwOverLw > 2.0 / 3.0 -> WallSlenderness.SHORT_WALL
            else -> WallSlenderness.LARGE_DIMENSION_WALL
        }
    val requiredMinimumLengthMeters: Double
        get() = maxOf(clearHeightHeMeters / 3.0, 4.0 * thicknessBwMeters, 1.0)
    val requiredMinimumThicknessMeters: Double
        get() = maxOf(0.15, clearHeightHeMeters / 20.0)
    val geometryLabel: String get() = "lw=${formatMeters(lengthLwMeters)} m, bw=${formatMeters(thicknessBwMeters)} m"
}

@Serializable
data class SlabStripTopology(
    val name: String,
    val totalThicknessHtCm: Double,
    val compressionSlabH0Cm: Double,
    val hollowBlockHeightCm: Double,
    val ribWidthB0Cm: Double,
    val flangeWidthBCm: Double,
    val continuousSpansMeters: List<Double> = emptyList(),
    val ribSpacingCm: Double = 60.0,
    val isSolidSlab: Boolean = false,
    val leftCantileverMeters: Double = 0.0,
    val rightCantileverMeters: Double = 0.0,
    val elementIds: List<String> = emptyList()
) {
    val htMeters: Double get() = totalThicknessHtCm / 100.0
    val maxSpanMeters: Double get() = continuousSpansMeters.maxOrNull() ?: 0.0
    val minSpanMeters: Double get() = continuousSpansMeters.minOrNull() ?: 0.0
    val isThicknessConsistent: Boolean
        get() = isSolidSlab || abs(totalThicknessHtCm - (compressionSlabH0Cm + hollowBlockHeightCm)) < 0.5
    val isContinuous: Boolean get() = continuousSpansMeters.size > 1
    val geometryLabel: String
        get() = if (isSolidSlab) {
            "dalle pleine ${formatCm(totalThicknessHtCm)} cm"
        } else {
            "corps creux ${formatCm(totalThicknessHtCm)} cm (${formatCm(compressionSlabH0Cm)}+${formatCm(hollowBlockHeightCm)})"
        }
}

@Serializable
data class SectionCatalog(
    val columns: List<ColumnFamily> = emptyList(),
    val beams: List<BeamFamily> = emptyList(),
    val shearWalls: List<ShearWallFamily> = emptyList(),
    val slabs: List<SlabStripTopology> = emptyList()
) {
    val totalElementCount: Int
        get() = columns.sumOf { it.elementIds.size } +
            beams.sumOf { it.elementIds.size } +
            shearWalls.sumOf { it.elementIds.size } +
            slabs.sumOf { it.elementIds.size }

    val allFamilyNames: List<String>
        get() = columns.map { it.name } + beams.map { it.name } + shearWalls.map { it.pierName } + slabs.map { it.name }

    fun column(name: String): ColumnFamily? =
        columns.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun beam(name: String): BeamFamily? =
        beams.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun wall(pierName: String): ShearWallFamily? =
        shearWalls.firstOrNull { it.pierName.equals(pierName.trim(), ignoreCase = true) }

    fun slab(name: String): SlabStripTopology? =
        slabs.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun hasShearWalls(): Boolean = shearWalls.isNotEmpty()
    fun isEmpty(): Boolean = columns.isEmpty() && beams.isEmpty() && shearWalls.isEmpty() && slabs.isEmpty()
}

internal fun formatCm(value: Double): String = if (abs(value - value.toInt()) < 1e-6) value.toInt().toString() else String.format("%.1f", value)

internal fun formatMeters(value: Double): String = String.format("%.2f", value)
