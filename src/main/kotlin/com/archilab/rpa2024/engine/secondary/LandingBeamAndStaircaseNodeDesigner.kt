package com.archilab.rpa2024.engine.secondary

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Sollicitations et géométrie de la poutre palière supportant la volée d'escalier.
 */
@Serializable
data class LandingBeamGeometry(
    val beamName: String = "PP30x40",
    val widthBMeters: Double = 0.30,
    val heightHMeters: Double = 0.40,
    val spanLengthLMeters: Double = 3.60,
    val concreteCoverMeters: Double = 0.03,
    val selfWeightLinearKNPerM: Double = 3.0, // 25.0 * 0.30 * 0.40 = 3.0 kN/ml
    val wallWeightLinearKNPerM: Double = 2.80 // Mur de séparation / garde-corps
)

/**
 * Rapport complet de dimensionnement d'une poutre palière sous flexion, cisaillement et torsion (BAEL 91 § A.5.4).
 */
@Serializable
data class LandingBeamDesignReport(
    val beamName: String,
    val widthBMeters: Double,
    val heightHMeters: Double,
    val spanLengthMeters: Double,
    val designBendingMomentMuKNm: Double,
    val designShearForceVuKN: Double,
    val designTorsionMomentTuKNm: Double,
    val shearStressTauVMPa: Double,
    val torsionStressTauTMPa: Double,
    val combinedStressTauTotalMPa: Double,
    val allowableStressLimitMPa: Double,
    val isCombinedStressCompliant: Boolean,
    val bendingSteelAreaAsBendingCm2: Double,
    val torsionLongitudinalSteelAreaAlCm2: Double,
    val totalLongitudinalSteelAreaCm2: Double,
    val adoptedLongitudinalRebar: String,
    val transverseStirrupsAreaAtCm2: Double,
    val adoptedStirrupSpacingCm: Double,
    val adoptedTransverseRebar: String,
    val reentrantCornerDetailingAdvice: String,
    val globalStatus: String
)

/**
 * Moteur Avancé de Poutres Palières et Dispositions Constructives des Nœuds d'Escalier.
 * Conforme au BAEL 91 révisé 99 (§ A.5.4 & B.6.7), au CBA 93 et aux exigences CTC
 * relatives à la reprise de torsion et au non-arrachement des aciers aux angles rentrants.
 */
class LandingBeamAndStaircaseNodeDesigner {

    /**
     * Dimensionne la poutre palière recevant la réaction de la paillasse d'escalier.
     */
    fun designLandingBeam(
        beamGeometry: LandingBeamGeometry = LandingBeamGeometry(),
        staircaseReport: StaircaseDesignReport,
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0
    ): LandingBeamDesignReport {
        val b = beamGeometry.widthBMeters
        val h = beamGeometry.heightHMeters
        val l = beamGeometry.spanLengthLMeters
        val d = h - beamGeometry.concreteCoverMeters
        val gammaB = 1.50
        val gammaS = 1.15
        val fbu = (0.85 * fc28MPa) / gammaB
        val fsu = feMPa / gammaS

        // 1. Réaction d'escalier transmise à la poutre palière
        // Vu de l'escalier est en kN/ml pour une largeur unitaire
        val rStaircaseElu = staircaseReport.maxShearVuKN
        val excentricityM = (b / 2.0) - 0.05 // Encorbellement d'appui de la paillasse sur le nez de poutre

        // Charge totale linéaire sur la poutre
        val qTotLinearElu = 1.35 * (beamGeometry.selfWeightLinearKNPerM + beamGeometry.wallWeightLinearKNPerM) + rStaircaseElu

        // Sollicitations ELU
        val muElu = (qTotLinearElu * l * l) / 8.0
        val vuElu = (qTotLinearElu * l) / 2.0
        // Moment de torsion engendré par l'excentricité de la réaction et l'encastrement partiel
        val tuElu = (rStaircaseElu * max(0.05, excentricityM) * l) / 2.0

        // 2. Caractéristiques de la section creuse équivalente de torsion (BAEL § A.5.4)
        val tEquiv = min(b, h) / 6.0 // Épaisseur de paroi fictive
        val omegaArea = (b - tEquiv) * (h - tEquiv) // Aire intérieure
        val perimeterU = 2.0 * ((b - tEquiv) + (h - tEquiv)) // Périmètre moyen

        // 3. Contraintes tangentielles
        val tauV = (vuElu * 1000.0) / (b * d * 1e6)
        val tauT = (tuElu * 1000.0) / (2.0 * omegaArea * tEquiv * 1e6)
        val tauComb = sqrt(tauV * tauV + tauT * tauT)
        val tauLim = min(0.20 * fc28MPa / gammaB, 5.0) // Fissuration peu préjudiciable

        val isStressOk = tauComb <= tauLim

        // 4. Armatures de flexion pure
        val muRed = (muElu * 1000.0) / (b * d * d * fbu * 1e6)
        val alphaB = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muRed)))
        val zB = d * (1.0 - 0.40 * alphaB)
        val asBendingCm2 = ((muElu * 1000.0) / (zB * fsu * 1e6)) * 10000.0

        // 5. Armatures longitudinales de torsion réparties sur le périmètre (BAEL § A.5.4.3)
        val alTorsionM2 = (tuElu * 1000.0 * perimeterU) / (2.0 * omegaArea * fsu * 1e6)
        val alTorsionCm2 = alTorsionM2 * 10000.0

        val totalLongitudinalSteelCm2 = asBendingCm2 + alTorsionCm2

        // Sélection commerciale des aciers filants (Lit inférieur + barres de peau)
        val adoptedLong = when {
            totalLongitudinalSteelCm2 <= 6.0 -> "3T14 lit inférieur + 2T12 barres de peau (6.88 cm²)"
            totalLongitudinalSteelCm2 <= 8.5 -> "3T16 lit inférieur + 2T12 barres de peau (8.29 cm²)"
            totalLongitudinalSteelCm2 <= 11.0 -> "3T20 lit inférieur + 2T14 barres de peau (12.50 cm²)"
            else -> "4T20 lit inférieur + 4T14 barres de peau (18.72 cm²)"
        }

        // 6. Armatures transversales combinées (Étriers fermés à 90° avec crochets à 135°)
        val atOverStTorsion = (tuElu * 1000.0) / (2.0 * omegaArea * fsu * 1e6)
        val atOverStShear = (vuElu * 1000.0) / (0.90 * d * fsu * 1e6)
        val atOverStTotalM = atOverStTorsion + atOverStShear
        val atOverStTotalCm2PerM = atOverStTotalM * 100.0

        // Espacement maximal st <= min(0.9d, 40cm, perimeterU / 8)
        val maxStCm = min(min(0.90 * d * 100.0, 30.0), (perimeterU / 8.0) * 100.0)
        val adoptedSpacingCm = if (maxStCm >= 15.0) 15.0 else 10.0
        val atRequiredPerStirrupCm2 = atOverStTotalCm2PerM * (adoptedSpacingCm / 100.0)

        val adoptedTrans = "Cadres fermés T10 + Étriers T8 espacés de ${adoptedSpacingCm.toInt()} cm (crochets 135°)"

        val nodeAdvice = "Dispositions Parasismiques CTC : Aux angles rentrants de la paillasse, prolonger les barres inférieures " +
                "en chapeaux au-delà de l'intersection avec une longueur d'ancrage La >= 40 phi (1.50 m) pour éviter l'éclatement du béton sous poussée au vide."

        return LandingBeamDesignReport(
            beamName = beamGeometry.beamName,
            widthBMeters = b,
            heightHMeters = h,
            spanLengthMeters = l,
            designBendingMomentMuKNm = round(muElu * 100.0) / 100.0,
            designShearForceVuKN = round(vuElu * 100.0) / 100.0,
            designTorsionMomentTuKNm = round(tuElu * 100.0) / 100.0,
            shearStressTauVMPa = round(tauV * 1000.0) / 1000.0,
            torsionStressTauTMPa = round(tauT * 1000.0) / 1000.0,
            combinedStressTauTotalMPa = round(tauComb * 1000.0) / 1000.0,
            allowableStressLimitMPa = round(tauLim * 1000.0) / 1000.0,
            isCombinedStressCompliant = isStressOk,
            bendingSteelAreaAsBendingCm2 = round(asBendingCm2 * 100.0) / 100.0,
            torsionLongitudinalSteelAreaAlCm2 = round(alTorsionCm2 * 100.0) / 100.0,
            totalLongitudinalSteelAreaCm2 = round(totalLongitudinalSteelCm2 * 100.0) / 100.0,
            adoptedLongitudinalRebar = adoptedLong,
            transverseStirrupsAreaAtCm2 = round(atRequiredPerStirrupCm2 * 100.0) / 100.0,
            adoptedStirrupSpacingCm = adoptedSpacingCm,
            adoptedTransverseRebar = adoptedTrans,
            reentrantCornerDetailingAdvice = nodeAdvice,
            globalStatus = if (isStressOk) "CONFORME BAEL 91 & CTC (FLEXION + TORSION)" else "SECTION INSUFFISANTE EN TORSION"
        )
    }
}
