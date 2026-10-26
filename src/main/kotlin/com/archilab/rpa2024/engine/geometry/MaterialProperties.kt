package com.archilab.rpa2024.engine.geometry

import com.archilab.rpa2024.model.MaterialConfig
import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Propriétés mécaniques dérivées du béton et des armatures selon CBA 93 et BAEL 91 révisé 99.
 */
@Serializable
data class MechanicalProperties(
    val fc28MPa: Double,
    val ft28MPa: Double,
    val eijInstantaneousMPa: Double,
    val evjDelayedMPa: Double,
    val feLongitudinalMPa: Double,
    val feMeshMPa: Double,
    val gammaBElu: Double = 1.50,
    val gammaSElu: Double = 1.15,
    val gammaBAccidental: Double = 1.20,
    val gammaSAccidental: Double = 1.00,
    val fbuEluMPa: Double,
    val fbuAccidentalMPa: Double,
    val fsuEluMPa: Double,
    val fsuAccidentalMPa: Double
) {
    companion object {
        fun compute(config: MaterialConfig, thetaDuration: Double = 0.85): MechanicalProperties {
            val fc28 = config.fc28MPa
            val ft28 = 0.6 + 0.06 * fc28
            val fc28Cbrt = fc28.pow(1.0 / 3.0)
            val eij = 11000.0 * fc28Cbrt
            val evj = 3700.0 * fc28Cbrt

            val gammaBElu = 1.50
            val gammaSElu = 1.15
            val gammaBAcc = 1.20
            val gammaSAcc = 1.00

            val fbuElu = (0.85 * fc28) / (thetaDuration * gammaBElu)
            val fbuAcc = (0.85 * fc28) / (thetaDuration * gammaBAcc)
            val fsuElu = config.feMPa / gammaSElu
            val fsuAcc = config.feMPa / gammaSAcc

            return MechanicalProperties(
                fc28MPa = fc28,
                ft28MPa = ft28,
                eijInstantaneousMPa = eij,
                evjDelayedMPa = evj,
                feLongitudinalMPa = config.feMPa,
                feMeshMPa = config.meshFeMPa,
                gammaBElu = gammaBElu,
                gammaSElu = gammaSElu,
                gammaBAccidental = gammaBAcc,
                gammaSAccidental = gammaSAcc,
                fbuEluMPa = fbuElu,
                fbuAccidentalMPa = fbuAcc,
                fsuEluMPa = fsuElu,
                fsuAccidentalMPa = fsuAcc
            )
        }
    }
}
