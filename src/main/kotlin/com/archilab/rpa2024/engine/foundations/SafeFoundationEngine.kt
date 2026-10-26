package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.engine.frames.BaelSectionSolvers
import kotlin.math.abs
import kotlin.math.max

class SafeFoundationEngine(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0,
    private val coverCMeters: Double = 0.05
) {
    private val rectSolver = BaelSectionSolvers(fc28Mpa, feMpa)
    private val tSolver = BaelTSectionSolver(fc28Mpa, feMpa)

    fun designFootingBeam(input: SafeFootingInput): SafeFootingOutput {
        // 1. Contrôle géotechnique sous modèle de Winkler : σeff = Δmax · Ks ≤ σsol_adm
        val sigmaEffKpa = abs(input.maxSettlementDeltaMeters) * input.subgradeKsKnM3
        val allowableKpa = input.soilCapacityBars * 100.0 // 1.20 bars = 120 kPa
        val isSoilCompliant = sigmaEffKpa <= allowableKpa

        // 2. Calcul en Appui (Nappe Supérieure tendue -> Section rectangulaire b0 x h via BaelR)
        val supportRes = rectSolver.solveFlexionComposee(
            bMeters = input.webWidthB0Meters,
            hMeters = input.totalHeightHMeters,
            cMeters = coverCMeters,
            nuKn = input.axialNuKn,
            muKnM = input.supportMomentMuKnM,
            gammaB = 1.20,
            gammaS = 1.00
        )
        val supportSteel = max(supportRes.aInfCm2, 2.0)

        // 3. Calcul en Travée (Nappe Inférieure tendue -> Section en Té BaelT)
        val spanRes = tSolver.solveTSection(
            tableWidthBM = input.widthBMeters,
            webWidthB0M = input.webWidthB0Meters,
            totalHeightHM = input.totalHeightHMeters,
            flangeHeightH0M = input.slabHeightH0Meters,
            coverCM = coverCMeters,
            muKnM = input.spanMomentMuKnM,
            gammaB = 1.20,
            gammaS = 1.00
        )
        val spanSteel = max(spanRes.aInfCm2, 2.0)

        // 4. Ferraillage transversal du patin de semelle (Méthode des bielles)
        val dSlab = input.slabHeightH0Meters - coverCMeters
        val cantileverM = (input.widthBMeters - input.webWidthB0Meters) / 2.0
        val nuLineKn = max(abs(input.axialNuKn), 450.0) // Valeur d'effort normal linéique représentatif
        val astM2 = (nuLineKn * 1000.0 * cantileverM) / (4.0 * dSlab * (feMpa * 1e6))
        val astCm2PerM = max(astM2 * 10000.0, 0.002 * (input.slabHeightH0Meters * 100.0) * 100.0)

        return SafeFootingOutput(
            lineId = input.lineId,
            beamType = input.beamType,
            effectiveSoilStressKpa = sigmaEffKpa,
            allowableSoilStressKpa = allowableKpa,
            isSoilStressCompliant = isSoilCompliant,
            supportSteelAreaProvidedCm2 = supportSteel,
            supportRebarDescription = "${"%.2f".format(supportSteel)} cm² (Section BaelR)",
            supportNeutralAxisY0Meters = supportRes.neutralAxisY0Meters,
            spanSteelAreaProvidedCm2 = spanSteel,
            spanRebarDescription = "${"%.2f".format(spanSteel)} cm² (Section BaelT)",
            spanNeutralAxisY0Meters = spanRes.neutralAxisY0Meters,
            transverseSlabSteelCm2PerM = astCm2PerM,
            transverseSlabRebarDescription = "T12 esp = 15 cm (${"%.2f".format(astCm2PerM)} cm²/ml)",
            isOverallCompliant = isSoilCompliant
        )
    }
}
