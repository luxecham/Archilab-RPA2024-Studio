package com.archilab.rpa2024.engine.secondary

data class ParapetDesignResult(
    val heightMeters: Double,
    val thicknessMeters: Double,
    val developedPerimeterMeters: Double,
    val crossSectionAreaM2: Double,
    val deadLoadWpKnPerM: Double,
    val seismicForceFpKnPerM: Double,
    val designForceQKnPerM: Double,
    val momentEluKnM: Double,
    val axialForceEluKn: Double,
    val verticalSteelAreaCm2PerM: Double,
    val verticalRebarDescription: String,
    val distributionRebarDescription: String
)

data class JoistSpanData(
    val spanIndex: Int,
    val lengthMeters: Double,
    val deadLoadGKnPerM: Double,
    val liveLoadQKnPerM: Double
)

data class JoistDesignResult(
    val spanIndex: Int,
    val lengthMeters: Double,
    val maxSpanMomentMuKnM: Double,
    val leftSupportMomentMuKnM: Double,
    val rightSupportMomentMuKnM: Double,
    val maxShearVuKn: Double,
    val spanSteelAreaProvidedCm2: Double,
    val spanRebarDescription: String,
    val leftSupportSteelAreaProvidedCm2: Double,
    val leftSupportRebarDescription: String,
    val transverseSteelDescription: String,
    val calculatedDeflectionMm: Double,
    val allowableDeflectionMm: Double,
    val isDeflectionCompliant: Boolean
)

data class StaircaseDesignResult(
    val stepHeightCm: Double,
    val treadWidthCm: Double,
    val blondelValueCm: Double,
    val isBlondelCompliant: Boolean,
    val slopeAngleDeg: Double,
    val waistThicknessCm: Double,
    val deadLoadGKnM2: Double,
    val liveLoadQKnM2: Double,
    val maxMomentEluKnM: Double,
    val mainSteelAreaCm2PerM: Double,
    val mainRebarDescription: String,
    val distributionSteelAreaCm2PerM: Double,
    val distributionRebarDescription: String
)
