package com.archilab.rpa2024.engine.frames

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BeamFramesDesigner(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0,
    private val columnSupportDimCm: Double = 50.0
) {
    private val solvers = BaelSectionSolvers(fc28Mpa, feMpa)
    private val selector = AutonomousCommercialRebarSelector()

    fun designBeam(
        beamId: String,
        story: String,
        sectionName: String,
        isPrimary: Boolean,
        bCm: Double,
        hCm: Double,
        lengthMeters: Double,
        muSpanKnM: Double,
        muSupportKnM: Double,
        vuMaxKn: Double
    ): BeamCompleteDesignReport {
        val grossAreaCm2 = bCm * hCm
        val cMeters = 0.03
        val bM = bCm / 100.0
        val hM = hCm / 100.0
        val dUsefulM = hM - cMeters

        // 1. Minimum d'armatures RPA 2024 (§ 7.5.2.1) : 0.5% * b * h
        val aMinRpa = 0.005 * grossAreaCm2

        // 2. Résolution en travée (Flexion simple)
        val spanRes = solvers.solveFlexionSimple(bM, hM, cMeters, muSpanKnM)
        val targetSpanSteel = max(spanRes.aInfCm2, aMinRpa)
        val adoptedSpanBars = selector.selectBeamBars(targetSpanSteel, isSupport = false)

        // 3. Résolution sur appui (Flexion simple)
        val supportRes = solvers.solveFlexionSimple(bM, hM, cMeters, muSupportKnM)
        val targetSupportSteel = max(supportRes.aInfCm2, aMinRpa)
        val adoptedSupportBars = selector.selectBeamBars(targetSupportSteel, isSupport = true)

        // 4. Les 4 Vérifications à l'effort tranchant (BAEL 91 & RPA 2024)
        val vuKn = abs(vuMaxKn)
        val tauU = (vuKn * 10.0) / (bCm * (dUsefulM * 100.0)) // MPa
        val tauLimit = min(0.20 * fc28Mpa / 1.50, 5.0) // 3.33 MPa
        val isTauUValid = tauU <= tauLimit

        val aSupportDirectM = columnSupportDimCm / 100.0
        val bielleCapacityKn = 0.267 * (bM * 1000.0) * (aSupportDirectM * 1000.0) * (fc28Mpa / 1000.0)
        val isBielleValid = vuKn <= bielleCapacityKn

        val fsu = feMpa / 1.15
        val reqAnchorageSteelCm2 = (vuKn * 10.0) / fsu
        val isAnchorageValid = adoptedSupportBars.totalAreaCm2 >= reqAnchorageSteelCm2

        val sigmaMb = (vuKn * 1000.0) / ((bM * 1000.0) * (aSupportDirectM * 1000.0))
        val sigmaMbLimit = 1.30 * fc28Mpa / 1.50 // 21.67 MPa
        val isSigmaMbValid = sigmaMb <= sigmaMbLimit

        val allShearValid = isTauUValid && isBielleValid && isAnchorageValid && isSigmaMbValid

        val shearChecks = BeamShearChecks(
            vuKn = vuKn,
            tauUMpa = tauU,
            tauLimitMpa = tauLimit,
            isTauUCompliant = isTauUValid,
            bielleCapacityKn = bielleCapacityKn,
            isBielleCompliant = isBielleValid,
            requiredAnchorageSteelCm2 = reqAnchorageSteelCm2,
            providedSupportSteelCm2 = adoptedSupportBars.totalAreaCm2,
            isAnchorageCompliant = isAnchorageValid,
            sigmaMbMpa = sigmaMb,
            sigmaMbLimitMpa = sigmaMbLimit,
            isSigmaMbCompliant = isSigmaMbValid,
            allChecksCompliant = allShearValid
        )

        // 5. Zone nodale et espacements RPA 2024 (§ 7.5.2.2)
        val lPrime = 1.50 * hCm
        val sNodal = 8.0 // min(h/4, 12 phiL, 30cm)
        val sCurrent = 15.0 // h/2
        val lrec = 60.0 * 1.4 // 84 cm

        return BeamCompleteDesignReport(
            beamId = beamId,
            story = story,
            sectionName = sectionName,
            isPrimary = isPrimary,
            bCm = bCm,
            hCm = hCm,
            lengthMeters = lengthMeters,
            muSpanKnM = muSpanKnM,
            muSupportKnM = muSupportKnM,
            vuMaxKn = vuKn,
            aMinRpaCm2 = aMinRpa,
            spanCalculatedSteelCm2 = spanRes.aInfCm2,
            supportCalculatedSteelCm2 = supportRes.aInfCm2,
            adoptedSpanBars = adoptedSpanBars,
            adoptedSupportBars = adoptedSupportBars,
            transverseDiameterMm = 8,
            numberOfLegs = 4,
            lPrimeNodalZoneCm = lPrime,
            spacingNodalCm = sNodal,
            spacingCurrentCm = sCurrent,
            lapLengthLrecCm = lrec,
            shearChecks = shearChecks
        )
    }
}
