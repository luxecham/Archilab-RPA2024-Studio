package com.archilab.rpa2024.normative

import com.archilab.rpa2024.model.InfillType
import kotlin.math.max
import kotlin.math.sqrt

data class DampingValue(
    val percent: Double,
    val description: String,
    val reference: String = "Tableau 3.5"
)

/**
 * Tableau 3.5 (amortissement) et Eqn. (3.9) (coefficient de correction eta).
 */
object DampingTable {

    fun dampingFor(system: BracingSystem, infill: InfillType): DampingValue = when (system) {
        BracingSystem.MIXTE_EQUIVALENT_VOILES,
        BracingSystem.VOILES,
        BracingSystem.OSSATURE_NOYAU,
        BracingSystem.VOILES_GRANDES_DIMENSIONS ->
            DampingValue(10.0, "Voiles en béton armé / maçonnerie")

        BracingSystem.OSSATURE,
        BracingSystem.MIXTE_EQUIVALENT_OSSATURE,
        BracingSystem.OSSATURE_AVEC_REMPLISSAGE_RIGIDE -> when (infill) {
            InfillType.LIGHT -> DampingValue(6.0, "Portiques béton armé, remplissage léger")
            InfillType.DENSE -> DampingValue(7.0, "Portiques béton armé, remplissage dense")
        }

        BracingSystem.CONSOLE_VERTICALE,
        BracingSystem.PENDULE_INVERSE ->
            DampingValue(5.0, "Système non visé par le Tableau 3.5 : valeur par défaut")
    }

    /** Eqn. (3.9) : eta = sqrt(7 / (2 + xi)), borne a 0.70. */
    fun correctionFactor(percent: Double): Double = max(0.70, sqrt(7.0 / (2.0 + percent)))
}
