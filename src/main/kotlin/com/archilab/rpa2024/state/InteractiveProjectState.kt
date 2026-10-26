package com.archilab.rpa2024.state

import com.archilab.rpa2024.model.SoilSiteClass
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone

/**
 * État immuable du projet interactif Archilab RPA 2024 Studio.
 * Regroupe tous les paramètres d'entrée modifiables et les résultats analytiques recalculés en temps réel.
 */
data class InteractiveProjectState(
    // Identification projet
    val projectName: String = "Sûreté Urbaine Joint A (Sidi Bel Abbès)",
    val commune: String = "Sidi Bel Abbès",
    val wilaya: String = "Sidi Bel Abbès",

    // Paramètres Sismiques RPA 2024
    val seismicZone: SeismicZone = SeismicZone.ZONE_III,
    val siteClass: SoilSiteClass = SoilSiteClass.S3,
    val importanceGroup: ImportanceGroup = ImportanceGroup.GROUPE_1A,
    val behaviorFactorR: Double = 3.5,
    val qualityFactorQ: Double = 1.10,
    val dampingPercent: Double = 7.0,
    val fundamentalPeriodT0: Double = 0.399,

    // Paramètres Poteau
    val columnWidthBCm: Double = 45.0,
    val columnHeightHCm: Double = 45.0,
    val columnStoryHeightM: Double = 3.06,
    val columnFc28MPa: Double = 25.0,
    val columnFeMPa: Double = 400.0,
    val columnNedKN: Double = 1480.0,
    val columnMedKNm: Double = 125.0,
    val columnVedKN: Double = 68.0,

    // Paramètres Poutre
    val beamWidthBCm: Double = 30.0,
    val beamHeightHCm: Double = 45.0,
    val beamSpanM: Double = 5.20,
    val beamMuKNm: Double = 115.0,
    val beamVuKN: Double = 95.0,

    // Paramètres Voile de contreventement
    val wallLengthLwM: Double = 3.20,
    val wallThicknessEM: Double = 0.20,
    val wallTotalHeightHM: Double = 9.18,
    val wallFc28MPa: Double = 25.0,
    val wallNedKN: Double = 2100.0,
    val wallMedKNm: Double = 1180.0,
    val wallVedKN: Double = 310.0,

    // Paramètres Interaction Sol-Fondation SAFE
    val soilKsKNm3: Double = 40000.0,
    val soilSigmaSolAdmBars: Double = 2.00,
    val stripWidthSF120M: Double = 1.20,
    val stripWidthSFJ200M: Double = 2.00,
    val peakColumnLoadKN: Double = 1480.0,

    // Résultats calculés instantanément
    val spectrumOutput: SpectrumOutput = SpectrumOutput(),
    val columnOutput: ColumnOutput = ColumnOutput(),
    val beamOutput: BeamOutput = BeamOutput(),
    val wallOutput: WallOutput = WallOutput(),
    val foundationOutput: FoundationOutput = FoundationOutput(),

    // Indicateurs globaux
    val isAllCompliant: Boolean = true,
    val computationTimeMs: Long = 0,
    val lastUpdateTimestamp: Long = System.currentTimeMillis()
)

data class SpectrumOutput(
    val aCoeff: Double = 0.25,
    val iCoeff: Double = 1.25,
    val sFactor: Double = 1.25,
    val minThreshold020AI: Double = 0.0625,
    val saeAtT0: Double = 0.697,
    val sadAtT0: Double = 0.219,
    val t1: Double = 0.15,
    val t2: Double = 0.40,
    val t3: Double = 1.50
)

data class ColumnOutput(
    val concreteAreaCm2: Double = 2025.0,
    val reducedAxialStressNu: Double = 0.292, // Ned / (B * fc28) <= 0.35
    val isNuValid: Boolean = true,
    val minAsRpaCm2: Double = 16.20, // 0.8% * B
    val calculatedAsCm2: Double = 17.50,
    val selectedRebarDesc: String = "12 HA 14",
    val realAsCm2: Double = 18.47,
    val rebarRatioPercent: Double = 0.912,
    val isAsConforme: Boolean = true,
    val lcrM: Double = 0.60,
    val tieSpacingLcrCm: Double = 10.0,
    val tieSpacingCouranteCm: Double = 15.0,
    val lrecM: Double = 0.70
)

data class BeamOutput(
    val reducedMomentMu: Double = 0.153,
    val calculatedAsTraveeCm2: Double = 7.85,
    val selectedRebarTravee: String = "3 HA 14 + 3 HA 14 (9.24 cm²)",
    val selectedRebarChapeaux: String = "3 HA 14 (4.62 cm²)",
    val rebarRatioPercent: Double = 1.37,
    val isBeamConforme: Boolean = true,
    val tieSpacingNodaleCm: Double = 10.0,
    val tieSpacingCouranteCm: Double = 20.0
)

data class WallOutput(
    val hcrM: Double = 3.20,
    val reducedAxialStressNu: Double = 0.131, // Nu <= 0.40
    val isNuValid: Boolean = true,
    val amplifiedVuKN: Double = 434.0, // 1.40 * Vu
    val tauBMPa: Double = 0.75,
    val tauLimMPa: Double = 5.00, // 0.20 * fc28
    val isShearConforme: Boolean = true,
    val requiredEdgeAsCm2: Double = 14.80,
    val selectedEdgeRebarDesc: String = "2 x 6 HA 14 (18.47 cm²)"
)

data class FoundationOutput(
    val calculatedMaxPressureBars: Double = 1.84,
    val isBearingConforme: Boolean = true,
    val estimatedMaxSettlementMm: Double = 4.60,
    val isSettlementConforme: Boolean = true
)
