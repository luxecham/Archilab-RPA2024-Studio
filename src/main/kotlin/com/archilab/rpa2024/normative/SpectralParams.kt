package com.archilab.rpa2024.normative

/**
 * Parametres S, T1, T2 et T3 des Tableaux 3.3 (spectre type 1) et 3.4 (spectre type 2).
 */
data class SpectralParams(
    val spectrumType: SpectrumType,
    val siteClass: SiteClass,
    val s: Double,
    val t1: Double,
    val t2: Double,
    val t3: Double,
    val reference: String
) {
    val siteCoefficient: Double get() = s

    /**
     * Spectre elastique horizontal normalise Sae/g, Eqn. (3.8) du RPA 2024.
     * A est le coefficient d'acceleration de zone, I le coefficient d'importance,
     * eta le coefficient de correction d'amortissement (Eqn. 3.9).
     */
    fun elasticAccelerationOverG(
        periodSeconds: Double,
        zoneAcceleration: Double,
        importanceFactor: Double,
        eta: Double
    ): Double {
        if (periodSeconds <= 0.0) return zoneAcceleration * importanceFactor * s
        val base = zoneAcceleration * importanceFactor * s
        val plateau = 2.5 * eta
        // Eq. (3.8) : décroissance linéaire en T2/T puis en T2.T3/T² (RPA 2024, p. 50).
        return when {
            periodSeconds <= t1 -> base * (1.0 + periodSeconds / t1 * (plateau - 1.0))
            periodSeconds <= t2 -> base * plateau
            periodSeconds <= t3 -> base * plateau * (t2 / periodSeconds)
            periodSeconds <= 4.0 -> base * plateau * (t2 * t3 / (periodSeconds * periodSeconds))
            else -> 0.0
        }
    }

    /**
     * Spectre de calcul horizontal normalise Sad/g, Eqn. (3.15) du RPA 2024.
     * La reduction ductile s'applique via R et QF ; le terme d'amortissement eta
     * n'intervient pas dans le spectre de calcul (il appartient au spectre elastique).
     */
    fun designAccelerationOverG(
        periodSeconds: Double,
        zoneAcceleration: Double,
        importanceFactor: Double,
        behaviorFactor: Double,
        qualityFactor: Double
    ): Double {
        require(behaviorFactor > 0.0) { "Le coefficient de comportement R doit etre positif." }
        require(qualityFactor > 0.0) { "Le facteur de qualite QF doit etre positif." }
        val base = zoneAcceleration * importanceFactor * s
        val plateau = 2.5 * qualityFactor / behaviorFactor
        val value = when {
            periodSeconds <= 0.0 -> zoneAcceleration * importanceFactor * s * 2.0 / 3.0
            periodSeconds <= t1 -> base * (2.0 / 3.0 + periodSeconds / t1 * (plateau - 2.0 / 3.0))
            periodSeconds <= t2 -> base * plateau
            periodSeconds <= t3 -> base * plateau * (t2 / periodSeconds)
            periodSeconds <= 4.0 -> base * plateau * (t2 * t3 / (periodSeconds * periodSeconds))
            else -> 0.0
        }
        return maxOf(value, 0.2 * zoneAcceleration * importanceFactor)
    }
}
