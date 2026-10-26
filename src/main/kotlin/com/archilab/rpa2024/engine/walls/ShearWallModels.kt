package com.archilab.rpa2024.engine.walls

data class StoryPierForces(
    val storyName: String,
    val elevationHeightMeters: Double,
    val clearHeightHeMeters: Double,
    val ndCompressiveKn: Double,
    val vuShearKn: Double,
    val muBendingKnM: Double
)

data class ShiftedMomentEnvelopePoint(
    val elevationZ: Double,
    val rawAnalysisMomentKnM: Double,
    val linearEnvelopeMomentKnM: Double,
    val shiftedDesignMomentKnM: Double
)

data class AmplifiedShearEnvelopePoint(
    val elevationZ: Double,
    val rawAnalysisShearKn: Double,
    val amplifiedDesignShearKn: Double
)

data class BoundaryElementDetail(
    val side: String, // "GAUCHE" ou "DROITE"
    val lcChosenCm: Double,
    val bcChosenCm: Double,
    val minLcRequiredCm: Double,
    val verticalSteelRequiredCm2: Double,
    val verticalSteelProvidedCm2: Double,
    val rebarDescription: String,
    val hoopDiameterMm: Int,
    val hoopSpacingStCm: Double,
    val confinementAreaProvidedCm2: Double,
    val confinementAreaRequiredCm2: Double,
    val isConfinementCompliant: Boolean,
    val mechanicalConfinementRatioOmegaWd: Double,
    val minOmegaWdRequired: Double,
    val isLocalDuctilityCompliant: Boolean
)

data class WallWebReinforcement(
    val verticalBarsDescription: String,
    val horizontalBarsDescription: String,
    val spacingVerticalCm: Double,
    val spacingHorizontalCm: Double,
    val webShearStressTauBMpa: Double,
    val shearStressLimitMpa: Double,
    val isShearCompliant: Boolean
)

data class CompleteShearWallReport(
    val pierName: String,
    val lwMeters: Double,
    val bwMeters: Double,
    val totalHeightHwMeters: Double,
    val isSlenderWall: Boolean,
    val maxCompressiveNdKn: Double,
    val reducedNormalStressNuD: Double,
    val isNuDCompliant: Boolean,
    val maxShearVuKn: Double,
    val shearStressTauBMpa: Double,
    val shearStressLimitMpa: Double,
    val isShearCompliant: Boolean,
    val criticalZoneHeightHcrMeters: Double,
    val momentEnvelopes: List<ShiftedMomentEnvelopePoint>,
    val shearEnvelopes: List<AmplifiedShearEnvelopePoint>,
    val leftBoundary: BoundaryElementDetail,
    val rightBoundary: BoundaryElementDetail,
    val webReinforcement: WallWebReinforcement,
    val isOverallCompliant: Boolean
)
