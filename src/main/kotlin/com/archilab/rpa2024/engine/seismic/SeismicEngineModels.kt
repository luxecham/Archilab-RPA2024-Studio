package com.archilab.rpa2024.engine.seismic

import com.archilab.rpa2024.model.SoilSiteClass
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone

data class SeismicProjectConfig(
    val zone: SeismicZone,
    val site: SoilSiteClass,
    val importance: ImportanceGroup,
    val behaviorFactorR: Double,
    val qualityFactorQ: Double,
    val criticalDampingPercent: Double = 7.0,
    val totalHeightMeters: Double,
    val numberOfStories: Int,
    val totalWeightKn: Double,
    val structureTypeCodeCt: Double = 0.050 // Tableau 4.3 RPA 2024
)

data class SpectrumPoint(
    val periodSeconds: Double,
    val spectralAccelerationG: Double
)

data class ModalModeResult(
    val modeNumber: Int,
    val periodSeconds: Double,
    val uxPercent: Double,
    val uyPercent: Double,
    val uzPercent: Double,
    val sumUxPercent: Double,
    val sumUyPercent: Double,
    val sumUzPercent: Double,
    val rzPercent: Double
)

data class ModalCheckSummary(
    val modesCount: Int,
    val sumUxFinal: Double,
    val sumUyFinal: Double,
    val isNinetyPercentAchieved: Boolean,
    val isTorsionDerogationSatisfied: Boolean,
    val isCompliant: Boolean,
    val diagnostic: String
)

data class BaseShearComparison(
    val direction: String,
    val fundamentalPeriodT0: Double,
    val empiricalPeriodTemp: Double,
    val designSpectralAccelSadOverG: Double,
    val lambdaCoeff: Double,
    val staticForceVmseKn: Double,
    val dynamicForceVmdsKn: Double,
    val ratioVmdsOverVmse: Double,
    val isCalibrated: Boolean,
    val amplificationFactorNeeded: Double
)

data class ReducedNormalStressColumnCheck(
    val story: String,
    val columnId: String,
    val sectionName: String,
    val bCm: Double,
    val hCm: Double,
    val grossAreaCm2: Double,
    val axialForceNdKn: Double,
    val fc28Mpa: Double,
    val reducedStressNu: Double,
    val isCompliant: Boolean,
    val diagnostic: String
)

typealias ReducedStressColumnCheck = ReducedNormalStressColumnCheck

data class StoryDriftCheck(
    val storyName: String,
    val elevationHeightHkMeters: Double,
    val elasticDisplacementDeltaEkMeters: Double,
    val inelasticDisplacementDeltaKMeters: Double,
    val interStoryDriftBigDeltaKMeters: Double,
    val reducedDriftNuATimesBigDeltaKMeters: Double,
    val allowableDriftLimitMeters: Double,
    val isDriftCompliant: Boolean
)

data class PDeltaStoryEvaluation(
    val storyName: String,
    val storyHeightHkMeters: Double,
    val cumulativeWeightPkKn: Double,
    val interStoryDriftDeltaKMeters: Double,
    val cumulativeShearVkKn: Double,
    val thetaK: Double,
    val isSecondOrderNegligible: Boolean,
    val requiresAmplification: Boolean,
    val isPotentiallyUnstable: Boolean,
    val amplificationFactor: Double
)
