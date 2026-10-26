package com.archilab.rpa2024.normative

/** Une ponderation Pq du Tableau 3.18, avec sa justification issue de la topologie. */
data class PenaltyItem(
    val code: String,
    val label: String,
    val weight: Double,
    val applied: Boolean,
    val justification: String
)

data class QualityFactorResult(
    val category: PenaltyCategory,
    val qf: Double,
    val penalties: List<PenaltyItem> = emptyList(),
    val notes: List<String> = emptyList()
) {
    val appliedPenalties: List<PenaltyItem> get() = penalties.filter { it.applied }
    val totalPenalty: Double get() = qf - 1.0
    val isSatisfied: Boolean get() = appliedPenalties.isEmpty()
    val categoryLabel: String get() = category.label
}
