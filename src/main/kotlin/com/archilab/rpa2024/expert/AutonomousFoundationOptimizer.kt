package com.archilab.rpa2024.expert

import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingDesignSet
import com.archilab.rpa2024.engine.geometry.CheckStatus

/** Rapport du cycle fondations : dimensionnement SAFE, BaelT/BaelR et auto-resize. */
data class FoundationOptimizationReport(
    val snapshot: CrossModelSnapshot,
    val designSet: StripFootingDesignSet?,
    val resizeDecisions: List<ResizeDecision>,
    val maximumEffectiveStressKNM2: Double?,
    val allowableStressKNM2: Double?,
    val maximumSettlementMeters: Double?,
    val warnings: List<String> = emptyList()
) {
    val status: CheckStatus
        get() = when {
            designSet == null -> CheckStatus.NOT_APPLICABLE
            resizeDecisions.any { !it.converged } -> CheckStatus.WARNING
            else -> designSet.status
        }

    val soilUtilization: Double?
        get() = if (maximumEffectiveStressKNM2 != null && (allowableStressKNM2 ?: 0.0) > 0.0) {
            maximumEffectiveStressKNM2!! / allowableStressKNM2!!
        } else {
            null
        }
}

/**
 * Cycle 5 — optimiseur autonome des fondations SAFE.
 *
 * Il s'appuie sur `StripFootingDesignOrchestrator` pour les semelles filantes
 * (BaelR en appui, BaelT en travée, bielles transversales), puis sur
 * `AutonomousResizeEngine` pour élargir les semelles dont la portance ou le
 * ferraillage est en échec.
 */
class AutonomousFoundationOptimizer(
    private val designEngine: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator(),
    private val resizeEngine: AutonomousResizeEngine = AutonomousResizeEngine()
) {

    fun optimize(
        snapshot: CrossModelSnapshot,
        options: AutoResizeOptions = AutoResizeOptions(
            resizeColumns = false,
            resizeBeams = false,
            resizeWalls = false,
            resizeFootings = true
        )
    ): FoundationOptimizationReport {
        val foundations = snapshot.foundations
            ?: return FoundationOptimizationReport(
                snapshot = snapshot,
                designSet = null,
                resizeDecisions = emptyList(),
                maximumEffectiveStressKNM2 = null,
                allowableStressKNM2 = null,
                maximumSettlementMeters = null,
                warnings = listOf("Aucun modèle SAFE : cycle fondations sans objet.")
            )
        val resized = resizeEngine.resize(snapshot, options.copy(
            resizeColumns = false,
            resizeBeams = false,
            resizeWalls = false,
            resizeFootings = true
        ))
        val design = designEngine.design(resized.snapshot.foundations ?: foundations, snapshot.model.joint.materials)
        val soilChecks = design.soilChecks.map { it.result }
        val critical = soilChecks.maxByOrNull { it.effectiveStressKNM2 }
        val settlement = resized.snapshot.foundations?.displacementEnvelope?.settlementMeters
        val warnings = (design.warnings + resized.warnings).distinct()
        return FoundationOptimizationReport(
            snapshot = resized.snapshot,
            designSet = design,
            resizeDecisions = resized.decisions.filter { it.elementType == "Semelle" },
            maximumEffectiveStressKNM2 = critical?.effectiveStressKNM2,
            allowableStressKNM2 = critical?.allowableStressKNM2 ?: foundations.geotechnical.allowableStressKNM2,
            maximumSettlementMeters = settlement,
            warnings = warnings
        )
    }
}
