package com.archilab.rpa2024.engine.seismic

import com.archilab.rpa2024.model.SoilSiteClass
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Rpa2024SeismicEngineTest {

    private val projectConfig = SeismicProjectConfig(
        zone = SeismicZone.ZONE_III, // A = 0.15
        site = SoilSiteClass.S3,     // S = 1.55, T1=0.10, T2=0.40, T3=1.20
        importance = ImportanceGroup.GROUPE_1A, // I = 1.40, nuA = 0.39 / 0.46
        behaviorFactorR = 3.5,
        qualityFactorQ = 1.10,
        criticalDampingPercent = 7.0, // eta = 0.8819
        totalHeightMeters = 11.22,
        numberOfStories = 3,
        totalWeightKn = 10357.95,
        structureTypeCodeCt = 0.050
    )

    @Test
    fun testSpectrumCalculationAndLowerBound() {
        val generator = Rpa2024SpectrumGenerator(projectConfig)

        // 1. Contrôle du facteur d'amortissement eta = sqrt(7 / (2 + 7)) = 0.8819 >= 0.70
        assertEquals(0.8819, generator.dampingCorrectionEta, 0.001)

        // 2. Contrôle de la borne inférieure : 0.20 * A * I = 0.20 * 0.15 * 1.40 = 0.042 g
        assertEquals(0.042, generator.lowerBoundThreshold, 0.0001)

        // 3. Ordonnée spectrale sur le palier T1 <= T <= T2 (0.10s <= T <= 0.40s)
        // Sad/g = A * I * S * [2.5 * Q / R] = 0.15 * 1.4 * 1.55 * [2.5 * 1.1 / 3.5] = 0.2557 g
        val sadPlateau = generator.computeDesignHorizontalAcceleration(0.30)
        assertEquals(0.2557, sadPlateau, 0.001)

        // 4. Période très longue T = 4.0s : vérification que la valeur ne descend pas sous 0.042 g
        val sadLongPeriod = generator.computeDesignHorizontalAcceleration(4.0)
        assertTrue(sadLongPeriod >= 0.042)
    }

    @Test
    fun testModalMassParticipationAndBaseShearCalibration() {
        val spectrumEngine = Rpa2024SpectrumGenerator(projectConfig)
        val calibrator = ModalAndBaseShearCalibrator(projectConfig, spectrumEngine)

        // Données modales ETABS réelles (Tableau Modal Participating Mass Ratios p. 18)
        val modes = listOf(
            ModalModeResult(1, 0.399085, 73.1663, 0.6780, 0.0, 73.1663, 0.6780, 0.0, 1.0558),
            ModalModeResult(2, 0.294580, 1.1213, 70.3176, 0.0, 74.2876, 70.9957, 0.0, 3.4003),
            ModalModeResult(3, 0.265674, 0.0956, 3.1865, 0.0, 74.3832, 74.1822, 0.0, 60.9992),
            ModalModeResult(4, 0.166629, 4.8533, 0.4513, 0.0, 79.2365, 74.6335, 0.0, 4.5865),
            ModalModeResult(5, 0.150627, 0.0158, 2.8261, 0.0, 79.2523, 77.4596, 0.0, 0.8931),
            ModalModeResult(6, 0.135806, 0.6072, 1.9541, 0.0, 79.8594, 79.4136, 0.0, 5.0207),
            ModalModeResult(7, 0.094308, 13.3532, 0.1647, 0.0, 93.2126, 79.5784, 0.0, 1.1035),
            ModalModeResult(8, 0.073106, 0.7099, 14.3008, 0.0, 93.9226, 93.8792, 0.0, 0.5872)
        )

        // Validation de la complétude modale (Mode 8 : SumUx = 93.92% >= 90%, SumUy = 93.88% >= 90%)
        val modalSummary = calibrator.evaluateModalCompleteness(modes)
        assertTrue(modalSummary.isCompliant)
        assertTrue(modalSummary.isNinetyPercentAchieved)

        // Calage de la force sismique Sens X (Tx = 0.399s, V_MDS = 1884.51 kN)
        val calibX = calibrator.calibrateBaseShear("X-X", 0.399, 1884.51)
        assertEquals(0.85, calibX.lambdaCoeff, 0.001)
        assertEquals(2251.69, calibX.staticForceVmseKn, 5.0)
        assertTrue(calibX.isCalibrated) // 1884.51 kN >= 0.80 * 2251.69 kN (1801.35 kN)

        // Calage de la force sismique Sens Y (Ty = 0.294s, V_MDS = 1819.47 kN)
        val calibY = calibrator.calibrateBaseShear("Y-Y", 0.294, 1819.47)
        assertEquals(2251.69, calibY.staticForceVmseKn, 5.0)
        assertTrue(calibY.isCalibrated) // 1819.47 kN >= 1801.35 kN
    }

    @Test
    fun testReducedNormalStressCompliance() {
        val validator = ReducedNormalStressValidator(defaultFc28Mpa = 25.0)

        // Poteau 40x50 (C111) : Nd = 1495.43 kN -> nu = 0.299 <= 0.35
        val pot40x50 = validator.validateColumn("H RDC", "C111", "POT 40X50", 40.0, 50.0, -1495.43)
        assertTrue(pot40x50.isCompliant)
        assertEquals(0.299, pot40x50.reducedStressNu, 0.005)

        // Poteau 35x55 (C106) : Nd = 672.76 kN -> nu = 0.140 <= 0.35
        val pot35x55 = validator.validateColumn("H RDC", "C106", "POT 35X55", 35.0, 55.0, -672.76)
        assertTrue(pot35x55.isCompliant)
        assertEquals(0.140, pot35x55.reducedStressNu, 0.005)

        // Poteau 35x35 (C127) : Nd = 445.61 kN -> nu = 0.146 <= 0.35
        val pot35x35 = validator.validateColumn("H RDC", "C127", "POT 35X35", 35.0, 35.0, -445.61)
        assertTrue(pot35x35.isCompliant)
        assertEquals(0.146, pot35x35.reducedStressNu, 0.005)
    }

    @Test
    fun testKinematicDriftAndPDeltaStability() {
        val engine = KinematicAndStabilityEngine(
            behaviorR = 3.5,
            qualityQ = 1.10,
            nuAImportanceReduction = 0.46
        )

        // Test Déplacement H RDC Sens X (delta_ek = 0.0026 m, hk = 3.74 m)
        val driftRdc = engine.evaluateStoryDrift("H RDC", 3.74, 0.0026, 0.0)
        assertTrue(driftRdc.isDriftCompliant)
        assertEquals(0.0038, driftRdc.reducedDriftNuATimesBigDeltaKMeters, 0.001) // ~0.004 m <= 0.02805 m

        // Test Effet P-Delta Sens X au RDC (Pk = 10357.95 kN, Vk = 1884.51 kN, hk = 3.74 m, DeltaK = 0.0083 m)
        val pDeltaRdc = engine.evaluatePDeltaStability("H RDC", 3.74, 10357.95, 0.0083, 1884.51)
        assertTrue(pDeltaRdc.isSecondOrderNegligible)
        assertTrue(pDeltaRdc.thetaK <= 0.10)
        assertEquals(1.0, pDeltaRdc.amplificationFactor, 0.001)
    }
}
