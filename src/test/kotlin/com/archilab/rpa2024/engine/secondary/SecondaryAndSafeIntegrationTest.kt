package com.archilab.rpa2024.engine.secondary

import com.archilab.rpa2024.engine.foundations.SafeFootingInput
import com.archilab.rpa2024.engine.foundations.SafeFoundationEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SecondaryAndSafeIntegrationTest {

    @Test
    fun testAcrotereDesignCompliance() {
        val solver = ParapetWallSolver(fc28Mpa = 25.0, feMpa = 500.0, zoneAccelerationA = 0.15)
        val result = solver.designParapet(heightM = 0.60, thicknessM = 0.10)

        // Poids Wp = 2.44 kN/ml, Q = 1.00 kN/ml, Moment = 0.90 kNm
        assertEquals(2.44, result.deadLoadWpKnPerM, 0.05)
        assertEquals(1.00, result.designForceQKnPerM, 0.01)
        assertEquals(0.90, result.momentEluKnM, 0.05)
        assertTrue(result.verticalSteelAreaCm2PerM >= 3.35) // T8 e=15cm
    }

    @Test
    fun testPoutrelleContinuousJoistKokaCompliance() {
        val joistSolver = ContinuousHollowFloorSolver(fc28Mpa = 25.0, feMpa = 500.0)
        val span = JoistSpanData(
            spanIndex = 4,
            lengthMeters = 4.35,
            deadLoadGKnPerM = 3.40,
            liveLoadQKnPerM = 1.50
        )
        val result = joistSolver.solveJoistSpan(span)

        // Vérification des aciers KOKA (3T12 travée = 3.39 cm², 1T12+1T12 appui)
        assertTrue(result.spanSteelAreaProvidedCm2 >= 3.39)
        assertTrue(result.leftSupportSteelAreaProvidedCm2 >= 1.57)

        // Vérification flèche BAEL 91 (5.686 mm <= 8.70 mm)
        assertEquals(5.686, result.calculatedDeflectionMm, 0.01)
        assertEquals(8.70, result.allowableDeflectionMm, 0.05)
        assertTrue(result.isDeflectionCompliant)
    }

    @Test
    fun testEscalierBlondelAndWaistCompliance() {
        val staircaseSolver = StaircaseSolver(feMpa = 500.0)
        val result = staircaseSolver.designStraightStaircase(
            stepHeightCm = 17.0,
            treadWidthCm = 30.0,
            spanLengthMeters = 3.60
        )

        // Loi de Blondel : 2*17 + 30 = 64 cm (Vérifié)
        assertEquals(64.0, result.blondelValueCm, 0.1)
        assertTrue(result.isBlondelCompliant)
        assertTrue(result.mainSteelAreaCm2PerM >= 5.65) // 5T12/ml
    }

    @Test
    fun testSafeFootingSoilStressAndBaelRTSolvers() {
        val foundationEngine = SafeFoundationEngine(fc28Mpa = 25.0, feMpa = 500.0, coverCMeters = 0.05)

        // 1. Contrôle contrainte de sol sous SAFE (p. 43) : Delta = 0.005 m, Ks = 21848 kN/m3
        // sigma = 0.005 * 21848 = 109.24 kPa < 120 kPa
        val inputJoint = SafeFootingInput(
            lineId = "Joint_SFJ200",
            beamType = "SFJ200",
            isJoint = true,
            widthBMeters = 2.00,
            webWidthB0Meters = 0.90,
            totalHeightHMeters = 0.90,
            slabHeightH0Meters = 0.40,
            subgradeKsKnM3 = 21848.0,
            soilCapacityBars = 1.20,
            maxSettlementDeltaMeters = 0.005,
            supportMomentMuKnM = 1135.0, // Mu appui joint (p. 44)
            spanMomentMuKnM = 126.0,     // Mu travée joint (p. 44)
            shearVuKn = 350.0
        )
        val outputJoint = foundationEngine.designFootingBeam(inputJoint)

        // Vérification géotechnique
        assertTrue(outputJoint.isSoilStressCompliant)
        assertEquals(109.24, outputJoint.effectiveSoilStressKpa, 0.01)

        // 2. Vérification BaelR en appui joint (Mu = 1135 kNm -> Ainf = 27.97 cm², y0 = 0.09 m)
        assertEquals(27.97, outputJoint.supportSteelAreaProvidedCm2, 0.50)
        assertEquals(0.09, outputJoint.supportNeutralAxisY0Meters, 0.02)

        // 3. Vérification BaelT en travée joint (Mu = 126 kNm -> Ainf = 2.97 cm², y0 = 0.00 m)
        assertEquals(2.97, outputJoint.spanSteelAreaProvidedCm2, 0.20)
        assertEquals(0.00, outputJoint.spanNeutralAxisY0Meters, 0.02)
    }

    @Test
    fun testSafeFootingCurrentAppuiBaelR() {
        val foundationEngine = SafeFoundationEngine(fc28Mpa = 25.0, feMpa = 500.0, coverCMeters = 0.05)

        // En appui courant (p. 45) : b = 0.40 m, h = 0.90 m, Mu = 313 kNm -> Ainf = 7.57 cm², y0 = 0.06 m
        val inputCourant = SafeFootingInput(
            lineId = "Courant_SF120",
            beamType = "SF120",
            isJoint = false,
            widthBMeters = 1.20,
            webWidthB0Meters = 0.40,
            totalHeightHMeters = 0.90,
            slabHeightH0Meters = 0.40,
            subgradeKsKnM3 = 21848.0,
            soilCapacityBars = 1.20,
            maxSettlementDeltaMeters = 0.004,
            supportMomentMuKnM = 313.0,
            spanMomentMuKnM = 126.0,
            shearVuKn = 200.0
        )
        val outputCourant = foundationEngine.designFootingBeam(inputCourant)

        // Aciers appui courant
        assertEquals(7.57, outputCourant.supportSteelAreaProvidedCm2, 0.30)
        assertEquals(0.06, outputCourant.supportNeutralAxisY0Meters, 0.02)

        // Aciers travée courante (Mu = 126 kNm -> Ainf = 3.00 cm²)
        assertEquals(3.00, outputCourant.spanSteelAreaProvidedCm2, 0.20)
    }
}
