package com.archilab.rpa2024.model

import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import kotlin.math.*

enum class SoilSiteClass(val s: Double, val t1: Double, val t2: Double, val t3: Double) {
    S1(1.00, 0.05, 0.25, 1.20),
    S2(1.30, 0.05, 0.30, 1.20),
    S3(1.55, 0.10, 0.40, 1.20), // Sol Meuble RPA 2024
    S4(1.80, 0.10, 0.50, 1.20)
}

data class Point3D(val id: String, val x: Double, val y: Double, val z: Double)

data class ConcreteProperties(
    val fc28Mpa: Double = 25.0,
    val ft28Mpa: Double = 2.10,
    val gammaBDurable: Double = 1.50,
    val gammaBAccidental: Double = 1.20,
    val densityKnM3: Double = 25.0,
    val thermalAlpha: Double = 1.00e-5
) {
    val eInstantMpa: Double = 11000.0 * fc28Mpa.pow(1.0 / 3.0) // 32164.20 MPa
    val eDeferredMpa: Double = 3700.0 * fc28Mpa.pow(1.0 / 3.0) // 10818.86 MPa
}

data class RebarProperties(
    val feMpa: Double = 500.0,
    val feMeshMpa: Double = 215.0,
    val gammaSDurable: Double = 1.15,
    val gammaSAccidental: Double = 1.00,
    val esMpa: Double = 200000.0
)

data class GeotechnicalProperties(
    val sigmaSolAdmBars: Double = 1.20, // 120 kPa
    val anchorageDepthMeters: Double = 1.50,
    val subgradeReactionKsKnM3: Double = 21848.0
) {
    val sigmaSolAdmKpa: Double = sigmaSolAdmBars * 100.0
}

data class ColumnElement(
    val id: String,
    val story: String,
    val sectionName: String,
    val bCm: Double,
    val hCm: Double,
    val heightMeters: Double,
    val clearHeightMeters: Double,
    val nodeBase: Point3D,
    val nodeTop: Point3D,
    val isSolidToShearWall: Boolean = false
) {
    val grossAreaCm2: Double = bCm * hCm
}

data class BeamElement(
    val id: String,
    val story: String,
    val sectionName: String,
    val isPrimary: Boolean,
    val bCm: Double,
    val hCm: Double,
    val lengthAxisMeters: Double,
    val lengthClearMeters: Double,
    val nodeStart: Point3D,
    val nodeEnd: Point3D
)

data class ShearWallPier(
    val pierName: String,
    val story: String,
    val lengthLwMeters: Double,
    val thicknessBwMeters: Double,
    val clearHeightHeMeters: Double,
    val elevationZkMeters: Double
) {
    val grossAreaM2: Double = lengthLwMeters * thicknessBwMeters
}

data class FoundationStrip(
    val lineId: String,
    val typeName: String, // SF120, SF160, SFJ200
    val isJoint: Boolean,
    val widthBMeters: Double,
    val webWidthB0Meters: Double,
    val totalHeightHMeters: Double,
    val slabHeightH0Meters: Double,
    val springKLineKnM: Double
)

data class StructuralJointModel(
    val projectName: String,
    val jointName: String,
    val wilaya: String,
    val zone: SeismicZone,
    val site: SoilSiteClass,
    val importance: ImportanceGroup,
    val behaviorR: Double,
    val qualityQ: Double,
    val dampingEta: Double,
    val concrete: ConcreteProperties,
    val rebar: RebarProperties,
    val geo: GeotechnicalProperties,
    val stories: MutableList<StoryLevel> = mutableListOf(),
    val points: MutableMap<String, Point3D> = mutableMapOf(),
    val columns: MutableList<ColumnElement> = mutableListOf(),
    val beams: MutableList<BeamElement> = mutableListOf(),
    val shearWalls: MutableList<ShearWallPier> = mutableListOf(),
    val foundations: MutableList<FoundationStrip> = mutableListOf()
)
