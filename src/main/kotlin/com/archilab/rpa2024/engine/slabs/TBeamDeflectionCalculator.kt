package com.archilab.rpa2024.engine.slabs

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Entrée des paramètres pour le calcul de la flèche d'une poutrelle en Té selon BAEL 91 révisé 99.
 */
@Serializable
data class TBeamDeflectionInput(
    val spanLengthMeters: Double,          // Portée L de la travée
    val flangeWidthBMeters: Double,        // Largeur de la table b (ex: 0.60 m)
    val webWidthB0Meters: Double,          // Largeur de la nervure b0 (ex: 0.12 m)
    val totalHeightHMeters: Double,        // Hauteur totale h (ex: 0.21 m)
    val flangeThicknessH0Meters: Double,   // Épaisseur de la table h0 (ex: 0.05 m)
    val tensionSteelAreaM2: Double,        // Section d'aciers tendus As en m² (ex: 3.39e-4 m² pour 3T12)
    val compressionSteelAreaM2: Double = 0.0, // Section d'aciers comprimés A's en m²
    val dPrimeMeters: Double = 0.02,       // Enrobage/distance aux armatures tendues d' (ex: 0.02 m)
    val dDoublePrimeMeters: Double = 0.02, // Distance aux armatures comprimées d'' (ex: 0.02 m)
    val fc28MPa: Double = 25.0,            // Résistance béton à 28 jours (MPa)
    val ft28MPa: Double = 0.6 + 0.06 * 25.0, // Résistance traction (MPa)
    val eiInstantaneousMPa: Double = 32164.2, // Module instantané Ei (MPa)
    val evDelayedMPa: Double = 10818.86,     // Module différé Ev (MPa)
    val momentMjKNm: Double,               // Moment dû aux charges permanentes avant cloisons (Mj)
    val momentMgKNm: Double,               // Moment dû à la totalité des charges permanentes (Mg)
    val momentMpKNm: Double                // Moment total G + Q (Mp)
)

/**
 * Rapport complet et décomposé du calcul de flèche BAEL 91 révisé 99 (reproduction exacte de Koka).
 */
@Serializable
data class TBeamDeflectionReport(
    val spanLengthMeters: Double,
    val neutralAxisV1Meters: Double,       // Distance fibre supérieure au centre de gravité
    val neutralAxisV2Meters: Double,       // Distance fibre inférieure au centre de gravité
    val uncrackedInertiaI0M4: Double,      // Inertie I0 de la section non fissurée (m⁴)
    val effectiveDepthDMeters: Double,     // Hauteur utile d (m)
    val reinforcementRatioRho: Double,     // Taux d'armature rho = As / (b0 * d)
    val lambdaI: Double,                   // Coefficient lambda_i
    val lambdaV: Double,                   // Coefficient lambda_v
    val stressSjMPa: Double,               // Contrainte acier sous Mj
    val stressSgMPa: Double,               // Contrainte acier sous Mg
    val stressSpMPa: Double,               // Contrainte acier sous Mp
    val muJ: Double,                       // Coefficient mu_j
    val muG: Double,                       // Coefficient mu_g
    val muP: Double,                       // Coefficient mu_p
    val ifjiM4: Double,                    // Inertie fissurée instantanée sous Mj
    val ifgiM4: Double,                    // Inertie fissurée instantanée sous Mg
    val ifgvM4: Double,                    // Inertie fissurée différée sous Mg
    val ifpiM4: Double,                    // Inertie fissurée instantanée sous Mp
    val deflectionFjiMm: Double,           // Flèche f_ji (mm)
    val deflectionFgiMm: Double,           // Flèche f_gi (mm)
    val deflectionFgvMm: Double,           // Flèche f_gv (mm)
    val deflectionFpiMm: Double,           // Flèche f_pi (mm)
    val deflectionFgqMm: Double,           // Flèche totale f_gq = f_pi - f_gi + f_gv (mm)
    val harmfulDeflectionDeltaFtMm: Double,// Flèche totale nuisible Delta f_t = f_gq - f_ji (mm)
    val allowableDeflectionFbarMm: Double, // Flèche admissible f_bar = L/500 ou 0.5cm + L/1000 (mm)
    val isDeflectionSatisfied: Boolean     // Vérification Delta f_t <= f_bar
)

/**
 * Calculateur de flèche d'une poutre ou poutrelle en Té selon BAEL 91 révisé 99 (§ B.6.5.2).
 */
class TBeamDeflectionCalculator {

    fun compute(input: TBeamDeflectionInput): TBeamDeflectionReport {
        val b = input.flangeWidthBMeters
        val b0 = input.webWidthB0Meters
        val h = input.totalHeightHMeters
        val h0 = input.flangeThicknessH0Meters
        val hw = h - h0
        val l = input.spanLengthMeters

        // 1. Caractéristiques de la section brute non fissurée en Té
        val areaFlange = b * h0
        val areaWeb = b0 * hw
        val totalAreaB = areaFlange + areaWeb

        val staticMomTop = areaFlange * (h0 / 2.0) + areaWeb * (h0 + hw / 2.0)
        val v1 = staticMomTop / totalAreaB
        val v2 = h - v1

        val iFlange = (b * h0 * h0 * h0) / 12.0 + areaFlange * (v1 - h0 / 2.0) * (v1 - h0 / 2.0)
        val iWeb = (b0 * hw * hw * hw) / 12.0 + areaWeb * (v1 - (h0 + hw / 2.0)) * (v1 - (h0 + hw / 2.0))
        val i0 = iFlange + iWeb

        val d = h - input.dPrimeMeters
        val dPrimePrime = input.dDoublePrimeMeters
        val asSteel = input.tensionSteelAreaM2
        val asPrimeSteel = input.compressionSteelAreaM2

        val rho = if (b0 * d > 0.0) asSteel / (b0 * d) else 0.0

        val ft28 = input.ft28MPa
        val lambdaI = if (rho > 0.0) {
            (0.05 * ft28) / ((2.0 + 3.0 * (b0 / b)) * rho)
        } else 0.0
        val lambdaV = 0.40 * lambdaI

        // 2. Position de l'axe neutre fissuré y1 et inertie fissurée I_cr
        val (y1, iCr) = computeCrackedSectionProperties(b, b0, h0, d, dPrimePrime, asSteel, asPrimeSteel)

        // 3. Calcul des contraintes d'acier sous Mj, Mg, Mp (en MPa)
        // sigma_s = 15 * (M * (d - y1) / I_cr) * 1e-6 (si M en N.m, I_cr en m⁴)
        val stressSj = computeSteelStress(input.momentMjKNm, d, y1, iCr)
        val stressSg = computeSteelStress(input.momentMgKNm, d, y1, iCr)
        val stressSp = computeSteelStress(input.momentMpKNm, d, y1, iCr)

        // 4. Coefficients mu
        val muJ = computeMu(stressSj, rho, ft28)
        val muG = computeMu(stressSg, rho, ft28)
        val muP = computeMu(stressSp, rho, ft28)

        // 5. Inerties fissurées effectives I_f (m⁴)
        val ifji = (1.1 * i0) / (1.0 + lambdaI * muJ)
        val ifgi = (1.1 * i0) / (1.0 + lambdaI * muG)
        val ifgv = (1.1 * i0) / (1.0 + lambdaV * muG)
        val ifpi = (1.1 * i0) / (1.0 + lambdaI * muP)

        // 6. Flèches partielles (directement en millimètres)
        // f [m] = (M [kN.m] * 1e3 * L^2) / (10 * E [MPa] * 1e6 * I [m⁴]) = (M * L^2) / (10000 * E * I)
        // f [mm] = f [m] * 1000 = (M * L^2) / (10 * E * I)
        val ei = input.eiInstantaneousMPa
        val ev = input.evDelayedMPa

        val fjiMm = (input.momentMjKNm * l * l) / (10.0 * ei * ifji)
        val fgiMm = (input.momentMgKNm * l * l) / (10.0 * ei * ifgi)
        val fgvMm = (input.momentMgKNm * l * l) / (10.0 * ev * ifgv)
        val fpiMm = (input.momentMpKNm * l * l) / (10.0 * ei * ifpi)

        val fgqMm = fpiMm - fgiMm + fgvMm
        val deltaFtMm = fgqMm - fjiMm

        // 7. Flèche admissible selon la portée L
        val allowableFbarMm = if (l <= 5.0) {
            (l * 1000.0) / 500.0
        } else {
            5.0 + (l * 1000.0) / 1000.0
        }

        val isSatisfied = deltaFtMm <= allowableFbarMm

        return TBeamDeflectionReport(
            spanLengthMeters = l,
            neutralAxisV1Meters = v1,
            neutralAxisV2Meters = v2,
            uncrackedInertiaI0M4 = i0,
            effectiveDepthDMeters = d,
            reinforcementRatioRho = rho,
            lambdaI = lambdaI,
            lambdaV = lambdaV,
            stressSjMPa = stressSj,
            stressSgMPa = stressSg,
            stressSpMPa = stressSp,
            muJ = muJ,
            muG = muG,
            muP = muP,
            ifjiM4 = ifji,
            ifgiM4 = ifgi,
            ifgvM4 = ifgv,
            ifpiM4 = ifpi,
            deflectionFjiMm = fjiMm,
            deflectionFgiMm = fgiMm,
            deflectionFgvMm = fgvMm,
            deflectionFpiMm = fpiMm,
            deflectionFgqMm = fgqMm,
            harmfulDeflectionDeltaFtMm = deltaFtMm,
            allowableDeflectionFbarMm = allowableFbarMm,
            isDeflectionSatisfied = isSatisfied
        )
    }

    private fun computeCrackedSectionProperties(
        b: Double,
        b0: Double,
        h0: Double,
        d: Double,
        dPrimePrime: Double,
        asSteel: Double,
        asPrimeSteel: Double
    ): Pair<Double, Double> {
        val n = 15.0
        // Hypothèse 1 : l'axe neutre est dans la table (y1 <= h0)
        // 0.5 * b * y1^2 + n*A's*(y1 - d'') - n*As*(d - y1) = 0
        // (0.5*b)*y1^2 + (n*A's + n*As)*y1 - (n*As*d + n*A's*d'') = 0
        val a1 = 0.5 * b
        val b1 = n * (asSteel + asPrimeSteel)
        val c1 = -(n * asSteel * d + n * asPrimeSteel * dPrimePrime)

        val delta1 = b1 * b1 - 4.0 * a1 * c1
        val y1Candidate = (-b1 + sqrt(max(0.0, delta1))) / (2.0 * a1)

        val y1 = if (y1Candidate <= h0) {
            y1Candidate
        } else {
            // Hypothèse 2 : l'axe neutre est dans la nervure (y1 > h0)
            // 0.5 * b0 * y1^2 + (b - b0)*h0*(y1 - h0/2) + n*A's*(y1 - d'') - n*As*(d - y1) = 0
            val a2 = 0.5 * b0
            val b2 = (b - b0) * h0 + n * (asSteel + asPrimeSteel)
            val c2 = -((b - b0) * h0 * (h0 / 2.0) + n * asSteel * d + n * asPrimeSteel * dPrimePrime)
            val delta2 = b2 * b2 - 4.0 * a2 * c2
            (-b2 + sqrt(max(0.0, delta2))) / (2.0 * a2)
        }

        // Calcul de l'inertie fissurée I_cr par rapport à l'axe neutre y1
        val iCr = if (y1 <= h0) {
            (b * y1 * y1 * y1) / 3.0 +
                    n * asSteel * (d - y1) * (d - y1) +
                    n * asPrimeSteel * (y1 - dPrimePrime) * (y1 - dPrimePrime)
        } else {
            (b0 * y1 * y1 * y1) / 3.0 +
                    (b - b0) * h0 * ((h0 * h0) / 12.0 + (y1 - h0 / 2.0) * (y1 - h0 / 2.0)) +
                    n * asSteel * (d - y1) * (d - y1) +
                    n * asPrimeSteel * (y1 - dPrimePrime) * (y1 - dPrimePrime)
        }

        return Pair(y1, max(1e-8, iCr))
    }

    private fun computeSteelStress(momentKNm: Double, d: Double, y1: Double, iCr: Double): Double {
        if (momentKNm <= 0.0 || iCr <= 0.0) return 0.0
        val momentNm = momentKNm * 1000.0
        val sigmaSPa = 15.0 * (momentNm * (d - y1)) / iCr
        return sigmaSPa / 1e6 // Convertir en MPa
    }

    private fun computeMu(sigmaSMPa: Double, rho: Double, ft28MPa: Double): Double {
        if (sigmaSMPa <= 0.0 || rho <= 0.0) return 0.0
        val denom = 4.0 * rho * sigmaSMPa + ft28MPa
        if (denom <= 0.0) return 0.0
        val raw = 1.0 - (1.75 * ft28MPa) / denom
        return max(0.0, raw)
    }
}
