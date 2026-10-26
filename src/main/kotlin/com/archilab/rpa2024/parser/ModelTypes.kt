package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.model.StructuralJoint
import kotlin.math.hypot

class EtabsParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

enum class FrameKind { COLUMN, BEAM, BRACE, OTHER }

enum class AreaKind { WALL, SLAB, OTHER }

enum class EtabsSectionKind {
    FRAME_RECTANGULAR,
    FRAME_CIRCULAR,
    FRAME_OTHER,
    WALL,
    SLAB,
    DECK
}

data class Point3D(
    val id: String,
    val xMeters: Double,
    val yMeters: Double,
    val zMeters: Double
) {
    fun planDistanceTo(other: Point3D): Double = hypot(other.xMeters - xMeters, other.yMeters - yMeters)
    fun distanceTo(other: Point3D): Double =
        hypot(planDistanceTo(other), other.zMeters - zMeters)
}

data class FrameElement(
    val id: String,
    val kind: FrameKind,
    val startNodeId: String,
    val endNodeId: String,
    val sectionName: String,
    val storyName: String? = null
)

data class AreaElement(
    val id: String,
    val kind: AreaKind,
    val nodeIds: List<String>,
    val propertyName: String,
    val pierName: String? = null,
    val storyName: String? = null
)

data class EtabsSection(
    val name: String,
    val kind: EtabsSectionKind,
    val materialName: String? = null,
    val widthCm: Double? = null,
    val depthCm: Double? = null,
    val diameterCm: Double? = null,
    val thicknessCm: Double? = null,
    val totalThicknessCm: Double? = null,
    val compressionSlabThicknessCm: Double? = null,
    val hollowBlockHeightCm: Double? = null,
    val ribWidthCm: Double? = null,
    val ribSpacingCm: Double? = null
) {
    val isFrame: Boolean
        get() = kind == EtabsSectionKind.FRAME_RECTANGULAR ||
            kind == EtabsSectionKind.FRAME_CIRCULAR ||
            kind == EtabsSectionKind.FRAME_OTHER

    val isWall: Boolean get() = kind == EtabsSectionKind.WALL
    val isSlab: Boolean get() = kind == EtabsSectionKind.SLAB || kind == EtabsSectionKind.DECK

    val maxFrameDimensionCm: Double
        get() = maxOf(widthCm ?: 0.0, depthCm ?: 0.0, diameterCm ?: 0.0)

    val frameDepthCm: Double
        get() = depthCm ?: diameterCm ?: 0.0
}

data class RawMaterial(
    val name: String,
    val type: String = "CONCRETE",
    val fc28MPa: Double? = null,
    val fyMPa: Double? = null,
    val eMPa: Double? = null
) {
    val isConcrete: Boolean get() = type.uppercase().contains("CONC")
    val isSteel: Boolean get() = type.uppercase().contains("STEEL") || type.uppercase().contains("REBAR")
}

data class RawStory(
    val name: String,
    val heightMeters: Double,
    val elevationMeters: Double? = null
)

/**
 * Geometrie brute produite par n'importe quelle source ETABS (texte e2k, Excel, Access).
 * Toutes les dimensions sont deja converties en m, kN et MPa.
 */
class RawGeometry(
    val points: Map<String, Point3D>,
    val frames: List<FrameElement>,
    val areas: List<AreaElement>,
    val sections: List<EtabsSection>,
    val materials: List<RawMaterial> = emptyList(),
    val stories: List<RawStory> = emptyList(),
    val gridsX: List<GridLine> = emptyList(),
    val gridsY: List<GridLine> = emptyList(),
    val pierNames: List<String> = emptyList(),
    val units: UnitSystem? = null,
    val warnings: MutableList<String> = mutableListOf()
)

data class ParsedEtabsModel(
    val joint: StructuralJoint,
    val points: Map<String, Point3D>,
    val frames: List<FrameElement>,
    val areas: List<AreaElement>,
    val sections: List<EtabsSection>,
    val pierNames: List<String>,
    val units: UnitSystem? = null,
    val warnings: List<String> = emptyList()
) {
    val jointName: String get() = joint.jointName

    fun section(name: String): EtabsSection? =
        sections.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
