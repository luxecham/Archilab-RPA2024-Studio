package com.archilab.rpa2024.engine.frames

import kotlin.math.PI
import kotlin.math.pow

data class CommercialBar(val diameterMm: Int) {
    val sectionAreaCm2: Double = (PI * (diameterMm / 10.0).pow(2)) / 4.0
    val weightKgPerMeter: Double = (diameterMm * diameterMm) / 162.0
}

data class FlexionComposeeResult(
    val aSupCm2: Double,
    val aInfCm2: Double,
    val neutralAxisY0Meters: Double,
    val muBu: Double,
    val stateDescription: String
)

data class FlexionSimpleResult(
    val aInfCm2: Double,
    val neutralAxisY0Meters: Double,
    val muBu: Double,
    val zMeters: Double
)

data class RebarArrangement(
    val description: String,
    val totalAreaCm2: Double,
    val barCount: Int,
    val details: Map<String, String> = emptyMap()
)

data class ColumnCompleteDesignReport(
    val columnId: String,
    val story: String,
    val sectionName: String,
    val bCm: Double,
    val hCm: Double,
    val clearHeightMeters: Double,
    val ndMaxKn: Double,
    val muCorrKnM: Double,
    val vuMaxKn: Double,
    val aCalculatedCm2: Double,
    val aMinRpaCm2: Double,
    val adoptedLongitudinalBars: RebarArrangement,
    val transverseDiameterMm: Int,
    val numberOfLegs: Int,
    val lcrNodalZoneCm: Double,
    val spacingNodalCm: Double,
    val spacingCurrentCm: Double,
    val lapLengthLrecCm: Double,
    val tauBuMpa: Double,
    val tauBuLimitMpa: Double,
    val isShearCompliant: Boolean,
    val isOverallCompliant: Boolean
)

data class BeamShearChecks(
    val vuKn: Double,
    val tauUMpa: Double,
    val tauLimitMpa: Double,
    val isTauUCompliant: Boolean,
    val bielleCapacityKn: Double,
    val isBielleCompliant: Boolean,
    val requiredAnchorageSteelCm2: Double,
    val providedSupportSteelCm2: Double,
    val isAnchorageCompliant: Boolean,
    val sigmaMbMpa: Double,
    val sigmaMbLimitMpa: Double,
    val isSigmaMbCompliant: Boolean,
    val allChecksCompliant: Boolean
)

data class BeamCompleteDesignReport(
    val beamId: String,
    val story: String,
    val sectionName: String,
    val isPrimary: Boolean,
    val bCm: Double,
    val hCm: Double,
    val lengthMeters: Double,
    val muSpanKnM: Double,
    val muSupportKnM: Double,
    val vuMaxKn: Double,
    val aMinRpaCm2: Double,
    val spanCalculatedSteelCm2: Double,
    val supportCalculatedSteelCm2: Double,
    val adoptedSpanBars: RebarArrangement,
    val adoptedSupportBars: RebarArrangement,
    val transverseDiameterMm: Int,
    val numberOfLegs: Int,
    val lPrimeNodalZoneCm: Double,
    val spacingNodalCm: Double,
    val spacingCurrentCm: Double,
    val lapLengthLrecCm: Double,
    val shearChecks: BeamShearChecks
)
