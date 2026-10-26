package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.normative.SeismicZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ColumnFramesDesigner(
    private val zone: SeismicZone = SeismicZone.ZONE_III,
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0
) {
    private val solvers = BaelSectionSolvers(fc28Mpa, feMpa)
    private val selector = AutonomousCommercialRebarSelector()

    fun designColumn(
        columnId: String,
        story: String,
        sectionName: String,
        bCm: Double,
        hCm: Double,
        clearHeightMeters: Double,
        ndMaxKn: Double,
        muCorrKnM: Double,
        vuMaxKn: Double,
        isSolidToShearWall: Boolean = false
    ): ColumnCompleteDesignReport {
        val grossAreaCm2 = bCm * hCm
        val cMeters = 0.03
        val bMeters = bCm / 100.0
        val hMeters = hCm / 100.0

        // 1. Résolution de la flexion composée sous (Nd, Mcorr) avec Nd > 0 en compression (BAEL 91)
        val solverRes = solvers.solveFlexionComposee(
            bMeters = bMeters,
            hMeters = hMeters,
            cMeters = cMeters,
            nuKn = abs(ndMaxKn), // compression > 0
            muKnM = muCorrKnM,
            gammaB = 1.20,
            gammaS = 1.00
        )
        val aCalculated = solverRes.aInfCm2 + solverRes.aSupCm2

        // 2. Minimum réglementaire RPA 2024 (§ 7.4.2.1)
        val minRpaPercent = zone.rpaMinColumnRatio
        val aMinRpa = grossAreaCm2 * minRpaPercent
        val targetSteel = max(aCalculated, aMinRpa)

        // 3. Choix des barres commerciales réelles
        val adoptedBars = selector.selectColumnBars(bCm, hCm, targetSteel)

        // 4. Armatures transversales et zone nodale (§ 7.4.2.2)
        val lclCm = clearHeightMeters * 100.0
        val lcr = max(max(lclCm / 6.0, max(bCm, hCm)), 60.0)
        val tNodal = 10.0 // Zone III : min(10 phiL, 15cm)
        val tCurrent = 15.0 // Zone III : 15 phiL
        val lrec = 50.0 * 1.4 // 50 * phiL = 70 cm

        // 5. Sollicitation tangente conventionnelle
        val dUsefulM = min(bMeters, hMeters) - cMeters
        val tauBu = (vuMaxKn * 10.0) / (min(bCm, hCm) * (dUsefulM * 100.0)) // MPa
        val lambdaG = (clearHeightMeters * 0.7) / (min(bMeters, hMeters))
        val rhoD = if (lambdaG >= 5.0) 0.075 else 0.040
        val tauBuLimit = rhoD * fc28Mpa
        val isShearValid = tauBu <= tauBuLimit

        return ColumnCompleteDesignReport(
            columnId = columnId,
            story = story,
            sectionName = sectionName,
            bCm = bCm,
            hCm = hCm,
            clearHeightMeters = clearHeightMeters,
            ndMaxKn = ndMaxKn,
            muCorrKnM = muCorrKnM,
            vuMaxKn = vuMaxKn,
            aCalculatedCm2 = aCalculated,
            aMinRpaCm2 = aMinRpa,
            adoptedLongitudinalBars = adoptedBars,
            transverseDiameterMm = if (max(bCm, hCm) >= 45.0) 10 else 8,
            numberOfLegs = 4,
            lcrNodalZoneCm = lcr,
            spacingNodalCm = tNodal,
            spacingCurrentCm = tCurrent,
            lapLengthLrecCm = lrec,
            tauBuMpa = tauBu,
            tauBuLimitMpa = tauBuLimit,
            isShearCompliant = isShearValid,
            isOverallCompliant = isShearValid && (adoptedBars.totalAreaCm2 >= aMinRpa)
        )
    }

    private val SeismicZone.rpaMinColumnRatio: Double
        get() = when (this) {
            SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II -> 0.008
            SeismicZone.ZONE_III -> 0.009
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 0.010
        }
}
