package com.archilab.rpa2024.engine.foundations

data class SafeFootingInput(
    val lineId: String,
    val beamType: String, // SF120, SF160, SFJ200
    val isJoint: Boolean,
    val widthBMeters: Double,
    val webWidthB0Meters: Double,
    val totalHeightHMeters: Double,
    val slabHeightH0Meters: Double,
    val subgradeKsKnM3: Double = 21848.0,
    val soilCapacityBars: Double = 1.20,
    val maxSettlementDeltaMeters: Double,
    val supportMomentMuKnM: Double,
    val spanMomentMuKnM: Double,
    val shearVuKn: Double,
    val axialNuKn: Double = 0.0
)

data class SafeFootingOutput(
    val lineId: String,
    val beamType: String,
    val effectiveSoilStressKpa: Double,
    val allowableSoilStressKpa: Double,
    val isSoilStressCompliant: Boolean,
    val supportSteelAreaProvidedCm2: Double,
    val supportRebarDescription: String,
    val supportNeutralAxisY0Meters: Double,
    val spanSteelAreaProvidedCm2: Double,
    val spanRebarDescription: String,
    val spanNeutralAxisY0Meters: Double,
    val transverseSlabSteelCm2PerM: Double,
    val transverseSlabRebarDescription: String,
    val isOverallCompliant: Boolean
)
