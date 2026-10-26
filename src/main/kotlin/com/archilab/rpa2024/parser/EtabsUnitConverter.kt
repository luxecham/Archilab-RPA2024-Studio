package com.archilab.rpa2024.parser

/**
 * Convertisseur universel d'unités ETABS (v9.7.4 à v21).
 * Convertit automatiquement les valeurs vers le standard interne :
 * - Longueur : mètre (m) ou centimètre (cm) pour les sections
 * - Force : kilonewton (kN)
 * - Contrainte / Pression : mégapascal (MPa = N/mm² = 1000 kN/m²)
 */
class EtabsUnitConverter(
    val lengthUnit: LengthUnit = LengthUnit.METER,
    val forceUnit: ForceUnit = ForceUnit.KN
) {
    enum class LengthUnit(val toMeters: Double) {
        METER(1.0),
        MILLIMETER(0.001),
        CENTIMETER(0.01),
        INCH(0.0254),
        FOOT(0.3048)
    }

    enum class ForceUnit(val toKN: Double) {
        KN(1.0),
        N(0.001),
        TON(9.80665),
        KGF(0.00980665),
        KIP(4.44822),
        LB(0.00444822)
    }

    fun toMeters(value: Double): Double = value * lengthUnit.toMeters

    fun toCm(value: Double): Double = toMeters(value) * 100.0

    fun toKN(value: Double): Double = value * forceUnit.toKN

    fun toKNm(moment: Double): Double = toKN(moment) * lengthUnit.toMeters

    fun toMPa(stress: Double): Double {
        // 1 Pa = 1 N/m² = 1e-3 kN / m²
        // 1 MPa = 10^6 Pa = 1000 kN/m²
        val forceInKN = toKN(stress)
        val areaInM2 = lengthUnit.toMeters * lengthUnit.toMeters
        val knPerM2 = forceInKN / areaInM2
        return knPerM2 / 1000.0
    }

    companion object {
        fun fromEtabsString(unitStr: String): EtabsUnitConverter {
            val upper = unitStr.uppercase().replace("\"", "").trim()
            val parts = upper.split("-", "_", "/")
            val fStr = parts.getOrNull(0) ?: "KN"
            val lStr = parts.getOrNull(1) ?: "M"

            val force = when (fStr) {
                "KN", "KNEWTON" -> ForceUnit.KN
                "N", "NEWTON" -> ForceUnit.N
                "TON", "TONNE", "TONF" -> ForceUnit.TON
                "KGF", "KG" -> ForceUnit.KGF
                "KIP" -> ForceUnit.KIP
                "LB" -> ForceUnit.LB
                else -> ForceUnit.KN
            }

            val length = when (lStr) {
                "M", "METER", "METERS" -> LengthUnit.METER
                "MM", "MILLIMETER" -> LengthUnit.MILLIMETER
                "CM", "CENTIMETER" -> LengthUnit.CENTIMETER
                "IN", "INCH" -> LengthUnit.INCH
                "FT", "FOOT", "FEET" -> LengthUnit.FOOT
                else -> LengthUnit.METER
            }

            return EtabsUnitConverter(length, force)
        }
    }
}
