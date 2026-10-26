package com.archilab.rpa2024.engine.secondary

import com.archilab.rpa2024.engine.frames.BaelRInput
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.DesignSituation
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Paramètres géométriques d'une volée d'escalier avec palier.
 */
@Serializable
data class StaircaseGeometry(
    val stepHeightHmMeters: Double = 0.17,      // Hauteur de marche h (m)
    val treadWidthGMeters: Double = 0.30,       // Giron g (m)
    val flightSpanMeters: Double = 2.40,        // Longueur projetée de la volée (m)
    val landingSpanMeters: Double = 1.40,       // Longueur du palier de repos (m)
    val slabThicknessEpMeters: Double = 0.16,   // Épaisseur de la paillasse ep (m)
    val flightWidthMeters: Double = 1.00        // Largeur unitaire de calcul (1 ml)
) {
    val totalLengthMeters: Double
        get() = flightSpanMeters + landingSpanMeters

    /**
     * Loi de Blondel : 60 cm <= 2*h + g <= 64 cm
     */
    val blondelValueCm: Double
        get() = (2.0 * stepHeightHmMeters + treadWidthGMeters) * 100.0

    val isBlondelCompliant: Boolean
        get() = blondelValueCm in 59.9..64.1

    /**
     * Pente de la paillasse alpha = arctan(h/g)
     */
    val slopeAngleRad: Double
        get() = atan(stepHeightHmMeters / treadWidthGMeters)

    val slopeAngleDeg: Double
        get() = Math.toDegrees(slopeAngleRad)

    val minThicknessRecommendedMeters: Double
        get() = totalLengthMeters / 30.0

    val maxThicknessRecommendedMeters: Double
        get() = totalLengthMeters / 20.0

    val isThicknessCompliant: Boolean
        get() = slabThicknessEpMeters >= (minThicknessRecommendedMeters - 0.01)
}

/**
 * Rapport complet de calcul d'une volée d'escalier.
 */
@Serializable
data class StaircaseDesignReport(
    val geometry: StaircaseGeometry,
    val deadLoadFlightGKNPerM2: Double,
    val deadLoadLandingGKNPerM2: Double,
    val liveLoadQKNPerM2: Double,
    val equivalentEluLoadKNPerM: Double,
    val equivalentElsLoadKNPerM: Double,
    val isostaticMomentM0KNm: Double,
    val designSpanMomentMtKNm: Double,          // 0.85 * M0
    val designSupportMomentMaKNm: Double,       // 0.50 * M0
    val maxShearVuKN: Double,
    val calculatedSpanSteelAreaCm2: Double,
    val adoptedSpanSteelAreaCm2: Double,
    val spanSteelDescription: String,
    val calculatedSupportSteelAreaCm2: Double,
    val adoptedSupportSteelAreaCm2: Double,
    val supportSteelDescription: String,
    val distributionSteelAreaCm2: Double,
    val distributionSteelDescription: String,
    val shearStressTauUMPa: Double,
    val shearStressLimitMPa: Double,
    val isShearCompliant: Boolean,
    val isGloballyCompliant: Boolean
)

/**
 * Moteur de calcul des escaliers en béton armé selon CBA 93 et BAEL 91.
 */
class StaircaseDesigner(
    private val baelSolver: BaelRectangularSolver = BaelRectangularSolver()
) {

    fun designStaircase(
        geometry: StaircaseGeometry = StaircaseGeometry(),
        liveLoadQ: Double = 2.50, // 2.50 kN/m² pour habitation, 4.00 kN/m² pour public
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0,
        concreteCoverMeters: Double = 0.025
    ): StaircaseDesignReport {
        val cosAlpha = cos(geometry.slopeAngleRad)
        val ep = geometry.slabThicknessEpMeters
        val hm = geometry.stepHeightHmMeters

        // 1. Descente de charges DTR BC 2.2
        // Paillasse inclinée :
        val gDalle = (25.0 * ep) / cosAlpha
        val gMarches = (22.0 * hm) / 2.0
        val gRevetement = 0.50 + 0.20 + 0.10 // Mortier/carrelage + enduit plâtre + garde-corps
        val gFlight = gDalle + gMarches + gRevetement

        // Palier horizontal :
        val gPalier = 25.0 * ep + 0.50 + 0.20

        // 2. Charge équivalente sur projection horizontale (pour b = 1.00 m)
        val lFlight = geometry.flightSpanMeters
        val lLanding = geometry.landingSpanMeters
        val lTot = geometry.totalLengthMeters

        val pFlightElu = 1.35 * gFlight + 1.50 * liveLoadQ
        val pLandingElu = 1.35 * gPalier + 1.50 * liveLoadQ
        val qEqElu = (pFlightElu * lFlight + pLandingElu * lLanding) / lTot

        val pFlightEls = gFlight + liveLoadQ
        val pLandingEls = gPalier + liveLoadQ
        val qEqEls = (pFlightEls * lFlight + pLandingEls * lLanding) / lTot

        // 3. Sollicitations de flexion (Poutre sur deux appuis)
        val m0Elu = (qEqElu * lTot * lTot) / 8.0
        val mtElu = 0.85 * m0Elu
        val maElu = 0.50 * m0Elu
        val vuElu = (qEqElu * lTot) / 2.0

        // 4. Ferraillage en flexion simple (BaelRectangularSolver)
        val d = ep - concreteCoverMeters
        val spanInput = BaelRInput(
            widthBMeters = geometry.flightWidthMeters,
            heightHMeters = ep,
            dPrimeMeters = concreteCoverMeters,
            cMeters = concreteCoverMeters,
            fc28MPa = fc28MPa,
            feMPa = feMPa,
            axialForceNKN = 0.0,
            momentMKNm = mtElu,
            situation = DesignSituation.DURABLE_ELU
        )
        val spanRes = baelSolver.solve(spanInput)
        val asSpanCalculated = spanRes.aInfCm2

        val supportInput = BaelRInput(
            widthBMeters = geometry.flightWidthMeters,
            heightHMeters = ep,
            dPrimeMeters = concreteCoverMeters,
            cMeters = concreteCoverMeters,
            fc28MPa = fc28MPa,
            feMPa = feMPa,
            axialForceNKN = 0.0,
            momentMKNm = maElu,
            situation = DesignSituation.DURABLE_ELU
        )
        val supportRes = baelSolver.solve(supportInput)
        val asSupportCalculated = supportRes.aInfCm2

        // Choix des aciers (ex: 5 T12 = 5.65 cm² ou 5 T14 = 7.70 cm²)
        val (spanDesc, asSpanAdopted) = selectSlabBars(asSpanCalculated)
        val (suppDesc, asSuppAdopted) = selectSlabBars(asSupportCalculated)

        // Aciers de répartition transversaux : Ar = As / 4
        val arRequired = asSpanCalculated / 4.0
        val arAdopted = max(1.50, arRequired)
        val arDesc = "4 T10/ml (3.14 cm²/ml)"

        // 5. Vérification du cisaillement (BAEL 91 mod. 99)
        val tauU = (vuElu * 1000.0) / (geometry.flightWidthMeters * d * 1e6)
        val tauULimit = min(0.05 * fc28MPa, 4.0) // Fissuration non préjudiciable pour dalle
        val isShearOk = tauU <= tauULimit

        val isCompliant = geometry.isBlondelCompliant &&
                geometry.isThicknessCompliant &&
                isShearOk &&
                asSpanAdopted >= asSpanCalculated

        return StaircaseDesignReport(
            geometry = geometry,
            deadLoadFlightGKNPerM2 = roundTo2Decimals(gFlight),
            deadLoadLandingGKNPerM2 = roundTo2Decimals(gPalier),
            liveLoadQKNPerM2 = liveLoadQ,
            equivalentEluLoadKNPerM = roundTo2Decimals(qEqElu),
            equivalentElsLoadKNPerM = roundTo2Decimals(qEqEls),
            isostaticMomentM0KNm = roundTo2Decimals(m0Elu),
            designSpanMomentMtKNm = roundTo2Decimals(mtElu),
            designSupportMomentMaKNm = roundTo2Decimals(maElu),
            maxShearVuKN = roundTo2Decimals(vuElu),
            calculatedSpanSteelAreaCm2 = roundTo2Decimals(asSpanCalculated),
            adoptedSpanSteelAreaCm2 = roundTo2Decimals(asSpanAdopted),
            spanSteelDescription = spanDesc,
            calculatedSupportSteelAreaCm2 = roundTo2Decimals(asSupportCalculated),
            adoptedSupportSteelAreaCm2 = roundTo2Decimals(asSuppAdopted),
            supportSteelDescription = suppDesc,
            distributionSteelAreaCm2 = roundTo2Decimals(arAdopted),
            distributionSteelDescription = arDesc,
            shearStressTauUMPa = roundTo3Decimals(tauU),
            shearStressLimitMPa = roundTo2Decimals(tauULimit),
            isShearCompliant = isShearOk,
            isGloballyCompliant = isCompliant
        )
    }

    private fun selectSlabBars(asRequiredCm2: Double): Pair<String, Double> {
        val options = listOf(
            Pair("5 T10/ml (3.93 cm²/ml)", 3.93),
            Pair("5 T12/ml (5.65 cm²/ml)", 5.65),
            Pair("6 T12/ml (6.79 cm²/ml)", 6.79),
            Pair("5 T14/ml (7.70 cm²/ml)", 7.70),
            Pair("6 T14/ml (9.24 cm²/ml)", 9.24)
        )
        for (opt in options) {
            if (opt.second >= asRequiredCm2) {
                return opt
            }
        }
        return Pair("7 T14/ml (10.78 cm²/ml)", 10.78)
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundTo3Decimals(value: Double): Double = round(value * 1000.0) / 1000.0
}
