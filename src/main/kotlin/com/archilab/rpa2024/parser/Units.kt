package com.archilab.rpa2024.parser

/**
 * Conversions vers le systeme interne unique : forces en kN, longueurs en m, contraintes en MPa.
 * Les fichiers ETABS v9 a v21 expriment leurs unites dans l'entete ($ UNITS) ou dans les tables.
 */
data class UnitSystem(
    val code: String,
    val forceFactorToKN: Double,
    val lengthFactorToMeters: Double
) {
    val momentFactorToKNm: Double get() = forceFactorToKN * lengthFactorToMeters
    val stressFactorToMPa: Double get() = forceFactorToKN / (lengthFactorToMeters * lengthFactorToMeters) / 1000.0

    fun toKN(value: Double): Double = value * forceFactorToKN
    fun toMeters(value: Double): Double = value * lengthFactorToMeters
    fun toCentimeters(value: Double): Double = value * lengthFactorToMeters * 100.0
    fun toKNm(value: Double): Double = value * momentFactorToKNm
    fun toMPa(value: Double): Double = value * stressFactorToMPa
    fun toSquareMeters(value: Double): Double = value * lengthFactorToMeters * lengthFactorToMeters

    companion object {
        val KN_M = UnitSystem("KN-M", 1.0, 1.0)
        val KN_MM = UnitSystem("KN-MM", 1.0, 0.001)
        val KGF_M = UnitSystem("KGF-M", 0.00980665, 1.0)
        val KGF_MM = UnitSystem("KGF-MM", 0.00980665, 0.001)
        val TONF_M = UnitSystem("TONF-M", 9.80665, 1.0)
        val TONF_MM = UnitSystem("TONF-MM", 9.80665, 0.001)
        val N_MM = UnitSystem("N-MM", 0.001, 0.001)
        val KIP_FT = UnitSystem("KIP-FT", 4.4482216, 0.3048)
        val LB_IN = UnitSystem("LB-IN", 0.0044482216, 0.0254)

        private val knownCodes: Map<String, UnitSystem> = linkedMapOf(
            "KN-M" to KN_M,
            "KNM" to KN_M,
            "KN-MM" to KN_MM,
            "KNMM" to KN_MM,
            "KGF-M" to KGF_M,
            "KGFM" to KGF_M,
            "KGF-MM" to KGF_MM,
            "KGFMM" to KGF_MM,
            "TON-M" to TONF_M,
            "TONF-M" to TONF_M,
            "TON-MM" to TONF_MM,
            "TONF-MM" to TONF_MM,
            "N-MM" to N_MM,
            "NMM" to N_MM,
            "KIP-FT" to KIP_FT,
            "LB-IN" to LB_IN
        )

        private val forceFactors: Map<String, Double> = mapOf(
            "KN" to 1.0,
            "N" to 0.001,
            "KGF" to 0.00980665,
            "TONF" to 9.80665,
            "TON" to 9.80665,
            "KIP" to 4.4482216,
            "LB" to 0.0044482216,
            "LBF" to 0.0044482216
        )

        private val lengthFactors: Map<String, Double> = mapOf(
            "M" to 1.0,
            "CM" to 0.01,
            "MM" to 0.001,
            "FT" to 0.3048,
            "IN" to 0.0254
        )

        fun fromEtabsUnits(raw: String): UnitSystem {
            val upper = raw.trim().uppercase()
            knownCodes[upper.replace(" ", "")]?.let { return it }

            val tokens = upper.split(Regex("[\\s,;\\-/]+")).filter { it.isNotBlank() }
            val forceToken = tokens.firstOrNull { it in forceFactors }
            val lengthToken = tokens.firstOrNull { it in lengthFactors }
            if (forceToken != null && lengthToken != null) {
                return UnitSystem(
                    code = "$forceToken-$lengthToken",
                    forceFactorToKN = forceFactors.getValue(forceToken),
                    lengthFactorToMeters = lengthFactors.getValue(lengthToken)
                )
            }
            return KN_M
        }

        fun fromForceAndLength(forceToken: String, lengthToken: String): UnitSystem {
            val force = forceFactors[forceToken.trim().uppercase()] ?: 1.0
            val length = lengthFactors[lengthToken.trim().uppercase()] ?: 1.0
            return UnitSystem("$forceToken-$lengthToken", force, length)
        }

        fun stressFactorToMPa(forceToken: String, lengthToken: String): Double =
            fromForceAndLength(forceToken, lengthToken).stressFactorToMPa
    }
}
