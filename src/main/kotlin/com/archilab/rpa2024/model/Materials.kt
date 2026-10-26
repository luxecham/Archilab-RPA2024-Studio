package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable
import kotlin.math.cbrt

/**
 * Donnees mecaniques saisies ou lues dans ETABS. Les grandeurs derivees reprennent les
 * expressions du CBA 93 (chapitre 3) : ft28 = 0.6 + 0.06 fc28, Eij = 11000 fc28^(1/3),
 * Evj = 3700 fc28^(1/3), fbu = 0.85 fc28 / (theta . gamma_b).
 */
@Serializable
data class MaterialConfig(
    val concreteClass: String = "C25/30",
    val fc28MPa: Double = 25.0,
    val steelGrade: String = "FeE500",
    val feMPa: Double = 500.0,
    val gammaConcreteKNm3: Double = 25.0,
    val cementDosageKgM3: Double = 350.0,
    val gammaBElu: Double = 1.5,
    val gammaBAccidental: Double = 1.2,
    val gammaSElu: Double = 1.15,
    val gammaSAccidental: Double = 1.0,
    val theta: Double = 1.0,
    val soilAllowableStressBars: Double = 1.2,
    val anchorageLengthMeters: Double = 1.5
) {
    val ft28MPa: Double get() = 0.6 + 0.06 * fc28MPa
    val eijMPa: Double get() = 11000.0 * cbrt(fc28MPa)
    val evjMPa: Double get() = 3700.0 * cbrt(fc28MPa)
    val eijOverEvj: Double get() = if (evjMPa == 0.0) 0.0 else eijMPa / evjMPa
    val fbuDurableMPa: Double get() = 0.85 * fc28MPa / (theta * gammaBElu)
    val fbuAccidentalMPa: Double get() = 0.85 * fc28MPa / (theta * gammaBAccidental)
    val sigmaSteelEluMPa: Double get() = feMPa / gammaSElu
    val sigmaSteelAccidentalMPa: Double get() = feMPa / gammaSAccidental
    val ftjMPa: Double get() = 0.6 + 0.06 * fc28MPa
}
