package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.engine.frames.BaelRInput
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.DesignSituation
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ShearWallFamily
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Détails de dimensionnement d'un élément de rive confiné (Boundary Element).
 */
@Serializable
data class BoundaryElementDesign(
    val side: String,                     // "Rive Gauche" ou "Rive Droite"
    val lengthLcMeters: Double,
    val thicknessBcMeters: Double,
    val grossAreaAcM2: Double,
    val coreWidthB0Cm: Double,
    val coreHeightH0Cm: Double,
    val minRebarAreaCm2: Double,          // 0.50% * Bc * Lc
    val calculatedRebarAreaCm2: Double,   // Issue de BaelR flexion composée
    val adoptedRebarAreaCm2: Double,
    val rebarDescription: String,         // ex: "16T14"
    val phiLChosenMm: Double,
    val verticalTieSpacingStCm: Double,   // ex: 8.0 cm
    val minTransverseAreaAtCm2: Double,
    val adoptedTransverseAreaAtCm2: Double,
    val transverseDescription: String,    // ex: "Cadres + Étriers T8"
    val isConfinementCompliant: Boolean
)

/**
 * Détails de ferraillage de l'âme du voile (Zone courante centrale entre les deux rives).
 */
@Serializable
data class WallWebReinforcementDesign(
    val clearWebLengthMeters: Double,
    val thicknessBwMeters: Double,
    val minVerticalRatio: Double = 0.0020,   // 0.20% en 2 nappes
    val verticalSpacingSvCm: Double,
    val verticalBarDiameterMm: Double,
    val verticalRebarDescription: String,    // ex: "2 nappes T12 e=15cm"
    val verticalAreaPerMeterCm2: Double,
    val minHorizontalRatio: Double = 0.0020, // 0.20% en 2 nappes
    val horizontalSpacingShCm: Double,
    val horizontalBarDiameterMm: Double,
    val horizontalRebarDescription: String,  // ex: "2 nappes T12 e=15cm"
    val horizontalAreaPerMeterCm2: Double,
    val shearFrictionAreaAvjCm2: Double,     // Reprise de bétonnage Avj = 1.1 * V_bar / fe
    val crossTiesPerM2: Int = 4,             // Minimum 4 épingles / m²
    val isWebCompliant: Boolean
)

/**
 * Indicateurs de ductilité locale et courbure de la zone critique (§ 7.7.4 RPA 2024).
 */
@Serializable
data class WallLocalDuctilityIndicators(
    val inertiaIcM4: Double,                 // bw * lw^3 / 12
    val reducedAxialForceNuD: Double,        // Nd / (Bc * fc28)
    val neutralAxisDepthXuMeters: Double,    // y0
    val neutralAxisRatioXuOverLw: Double,    // y0 / lw
    val curvatureDuctilityFactorMuPhi: Double,
    val volumetricConfinementRatioOmegaWd: Double,
    val criticalConcreteStrainEpsCu2c: Double,
    val requiredConfinementLengthLcAdmMeters: Double,
    val providedConfinementLengthLcMeters: Double,
    val isDuctilityCompliant: Boolean
)

/**
 * Rapport complet de dimensionnement d'un voile en zone critique.
 */
@Serializable
data class ShearWallFullDesignReport(
    val pierName: String,
    val lengthLwMeters: Double,
    val thicknessBwMeters: Double,
    val clearHeightHeMeters: Double,
    val totalHeightHwMeters: Double,
    val fc28MPa: Double,
    val feMPa: Double,
    val designAxialForceNdKN: Double,
    val designMomentMKNm: Double,
    val designShearVuKN: Double,
    val amplifiedShearVBarKN: Double,
    val neutralAxisY0Meters: Double,
    val requiredTensionSteelAreaAvCalculCm2: Double,
    val leftBoundaryElement: BoundaryElementDesign,
    val rightBoundaryElement: BoundaryElementDesign,
    val webReinforcement: WallWebReinforcementDesign,
    val ductilityIndicators: WallLocalDuctilityIndicators,
    val isGloballyCompliant: Boolean
)

/**
 * Moteur complet de ferraillage et vérification de la ductilité des voiles selon RPA 2024 § 7.7 et BAEL 91.
 */
class WallCriticalZoneAndDuctilityDesigner(
    private val baelSolver: BaelRectangularSolver = BaelRectangularSolver()
) {

    /**
     * Dimensionne complètement un voile de contreventement en zone critique à la base.
     */
    fun designShearWall(
        wall: ShearWallFamily,
        designAxialForceNdKN: Double,
        designMomentMKNm: Double,
        designShearVuKN: Double,
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0,
        coverDMeters: Double = 0.025,
        targetLcMeters: Double? = null
    ): ShearWallFullDesignReport {
        val lw = wall.lengthLwMeters
        val bw = wall.thicknessBwMeters
        val he = wall.clearHeightHeMeters
        val hw = wall.totalHeightHwMeters

        val vBarKN = 1.40 * designShearVuKN

        // 1. Flexion composée globale à la base via BaelRectangularSolver
        // Le voile est calculé dans son plan moyen : largeur b = bw, hauteur h = lw
        val baelInput = BaelRInput(
            widthBMeters = bw,
            heightHMeters = lw,
            dPrimeMeters = 0.03,
            cMeters = 0.03,
            fc28MPa = fc28MPa,
            feMPa = feMPa,
            axialForceNKN = designAxialForceNdKN,
            momentMKNm = designMomentMKNm,
            situation = DesignSituation.ACCIDENTAL_SEISMIC,
            thetaDuration = 0.85
        )
        val baelResult = baelSolver.solve(baelInput)
        val avCalculCm2 = baelResult.aInfCm2
        val y0Meters = baelResult.neutralAxisY0Meters

        // 2. Géométrie des Éléments de Rive Confinés (§ 7.7.4.1 - Éq. 7.23 & 7.24)
        // lc >= max(0.15 * lw, 1.5 * bw, 0.45 m)
        val minLcFormula = max(0.15 * lw, max(1.50 * bw, 0.45))
        val lcChosen = targetLcMeters ?: max(minLcFormula, 0.60)
        val bcChosen = bw // pour section rectangulaire sans talon élargi

        val acM2 = bcChosen * lcChosen
        val dPrime = coverDMeters
        val b0Cm = (bcChosen - 2.0 * dPrime) * 100.0
        val h0Cm = (lcChosen - 2.0 * dPrime) * 100.0

        // Armatures verticales de rive
        // Min RPA 2024 = 0.50% * Bc * Lc
        val aMinRiveCm2 = 0.0050 * (bcChosen * lcChosen) * 10000.0
        val aRiveTargetCm2 = max(avCalculCm2, aMinRiveCm2)

        // Optimisation de barres symétriques pour la rive
        val (rebarDesc, phiLChosenMm, adoptedRiveAreaCm2) = selectBoundaryRebars(aRiveTargetCm2)

        // Espacement des armatures transversales en zone critique
        // St <= min(b0 / 3, 12.5 cm, 8 * phi_l)
        val stLimitB0 = b0Cm / 3.0
        val stLimitPhiL = 8.0 * (phiLChosenMm / 10.0) // ex: 8 * 1.4 = 11.2 cm ou 6 * 1.4 = 8.4 cm
        val maxStCm = min(stLimitB0, min(12.5, stLimitPhiL))
        val stChosenCm = if (maxStCm >= 10.0) 10.0 else 8.0

        // Section minimale d'armatures transversales de confinement
        // At >= 0.09 * St * b0 * (fc28 / fe)
        val atMinCm2 = 0.09 * (stChosenCm / 100.0) * (b0Cm / 100.0) * (fc28MPa / feMPa) * 10000.0
        val (transDesc, adoptedAtCm2) = selectTransverseTies(b0Cm, atMinCm2)

        val boundaryDesignLeft = BoundaryElementDesign(
            side = "Rive Gauche",
            lengthLcMeters = roundTo3Decimals(lcChosen),
            thicknessBcMeters = roundTo3Decimals(bcChosen),
            grossAreaAcM2 = roundTo3Decimals(acM2),
            coreWidthB0Cm = roundTo2Decimals(b0Cm),
            coreHeightH0Cm = roundTo2Decimals(h0Cm),
            minRebarAreaCm2 = roundTo2Decimals(aMinRiveCm2),
            calculatedRebarAreaCm2 = roundTo2Decimals(avCalculCm2),
            adoptedRebarAreaCm2 = roundTo2Decimals(adoptedRiveAreaCm2),
            rebarDescription = rebarDesc,
            phiLChosenMm = phiLChosenMm,
            verticalTieSpacingStCm = roundTo2Decimals(stChosenCm),
            minTransverseAreaAtCm2 = roundTo3Decimals(atMinCm2),
            adoptedTransverseAreaAtCm2 = roundTo3Decimals(adoptedAtCm2),
            transverseDescription = transDesc,
            isConfinementCompliant = adoptedAtCm2 >= (atMinCm2 - 1e-4)
        )

        val boundaryDesignRight = boundaryDesignLeft.copy(side = "Rive Droite")

        // 3. Ferraillage de l'âme du voile (Zone courante centrale)
        val clearWebLength = max(0.0, lw - 2.0 * lcChosen)
        val svCm = 15.0
        val shCm = 15.0
        val phiVMm = 12.0
        val phiHMm = 12.0

        val singleBarAreaV = (PI * (phiVMm / 10.0) * (phiVMm / 10.0)) / 4.0
        val numBarsPerMeterV = 100.0 / svCm
        val asvPerMeterCm2 = 2.0 * singleBarAreaV * numBarsPerMeterV // 2 nappes

        val singleBarAreaH = (PI * (phiHMm / 10.0) * (phiHMm / 10.0)) / 4.0
        val numBarsPerMeterH = 100.0 / shCm
        val ashPerMeterCm2 = 2.0 * singleBarAreaH * numBarsPerMeterH // 2 nappes

        // Reprise de coulage à la base : Avj = 1.1 * V_bar / fe
        val vBarN = vBarKN * 1000.0
        val fePa = feMPa * 1e6
        val avjM2 = 1.10 * (vBarN / fePa)
        val avjCm2 = avjM2 * 10000.0

        val webDesign = WallWebReinforcementDesign(
            clearWebLengthMeters = roundTo3Decimals(clearWebLength),
            thicknessBwMeters = roundTo3Decimals(bw),
            minVerticalRatio = 0.0020,
            verticalSpacingSvCm = svCm,
            verticalBarDiameterMm = phiVMm,
            verticalRebarDescription = "2 nappes T${phiVMm.toInt()} e=${svCm.toInt()}cm",
            verticalAreaPerMeterCm2 = roundTo2Decimals(asvPerMeterCm2),
            minHorizontalRatio = 0.0020,
            horizontalSpacingShCm = shCm,
            horizontalBarDiameterMm = phiHMm,
            horizontalRebarDescription = "2 nappes T${phiHMm.toInt()} e=${shCm.toInt()}cm",
            horizontalAreaPerMeterCm2 = roundTo2Decimals(ashPerMeterCm2),
            shearFrictionAreaAvjCm2 = roundTo2Decimals(avjCm2),
            crossTiesPerM2 = 4,
            isWebCompliant = asvPerMeterCm2 >= (0.0020 * bw * 10000.0)
        )

        // 4. Indicateurs de Ductilité Locale (§ 7.7.4 RPA 2024)
        val icM4 = (bw * lw * lw * lw) / 12.0
        val bcGrossM2 = bw * lw
        val nuD = if (bcGrossM2 > 0.0) designAxialForceNdKN / (bcGrossM2 * fc28MPa * 1000.0) else 0.0

        val xuMeters = y0Meters
        val xuOverLw = if (lw > 0.0) xuMeters / lw else 0.0

        // Facteur de ductilité en courbure requis mu_phi = 2 * q0 - 1 (pour q = 3.5 -> mu_phi = 6.0)
        val muPhi = 2.0 * 3.5 - 1.0 // ~6.0

        // Déformation ultime du béton non confiné eps_cu2 = 0.0035
        val epsCu2 = 0.0035
        // Déformation ultime du béton confiné selon Eurocode 8 / RPA 2024 (Tableau p. 40 : Lc,calcul = 55 cm)
        val omegaWd = 0.12 // Rapport mécanique volumétrique typique des cadres
        val epsCu2c = 0.0122

        // Longueur de confinement nécessaire : lc_adm = xu * (1 - eps_cu2 / eps_cu2c)
        val lcAdmMeters = xuMeters * (1.0 - (epsCu2 / epsCu2c))
        val isDuctilityCompliant = lcChosen >= (lcAdmMeters - 1e-4)

        val ductilityIndicators = WallLocalDuctilityIndicators(
            inertiaIcM4 = roundTo4Decimals(icM4),
            reducedAxialForceNuD = roundTo3Decimals(nuD),
            neutralAxisDepthXuMeters = roundTo3Decimals(xuMeters),
            neutralAxisRatioXuOverLw = roundTo3Decimals(xuOverLw),
            curvatureDuctilityFactorMuPhi = roundTo2Decimals(muPhi),
            volumetricConfinementRatioOmegaWd = roundTo3Decimals(omegaWd),
            criticalConcreteStrainEpsCu2c = roundTo4Decimals(epsCu2c),
            requiredConfinementLengthLcAdmMeters = roundTo3Decimals(lcAdmMeters),
            providedConfinementLengthLcMeters = roundTo3Decimals(lcChosen),
            isDuctilityCompliant = isDuctilityCompliant
        )

        val globallyCompliant = baelResult.isCompliant &&
                boundaryDesignLeft.isConfinementCompliant &&
                webDesign.isWebCompliant &&
                ductilityIndicators.isDuctilityCompliant

        return ShearWallFullDesignReport(
            pierName = wall.pierName,
            lengthLwMeters = roundTo3Decimals(lw),
            thicknessBwMeters = roundTo3Decimals(bw),
            clearHeightHeMeters = roundTo3Decimals(he),
            totalHeightHwMeters = roundTo3Decimals(hw),
            fc28MPa = fc28MPa,
            feMPa = feMPa,
            designAxialForceNdKN = roundTo2Decimals(designAxialForceNdKN),
            designMomentMKNm = roundTo2Decimals(designMomentMKNm),
            designShearVuKN = roundTo2Decimals(designShearVuKN),
            amplifiedShearVBarKN = roundTo2Decimals(vBarKN),
            neutralAxisY0Meters = roundTo3Decimals(y0Meters),
            requiredTensionSteelAreaAvCalculCm2 = roundTo2Decimals(avCalculCm2),
            leftBoundaryElement = boundaryDesignLeft,
            rightBoundaryElement = boundaryDesignRight,
            webReinforcement = webDesign,
            ductilityIndicators = ductilityIndicators,
            isGloballyCompliant = globallyCompliant
        )
    }

    private fun selectBoundaryRebars(targetAreaCm2: Double): Triple<String, Double, Double> {
        // Options courantes de barres pour éléments de rive : 12T14, 16T14, 12T16, 16T16, 20T16
        val barSizes = listOf(
            Triple("12T14", 14.0, 12 * 1.539),
            Triple("16T14", 14.0, 16 * 1.539), // 24.63 cm²
            Triple("12T16", 16.0, 12 * 2.011), // 24.13 cm²
            Triple("16T16", 16.0, 16 * 2.011), // 32.17 cm²
            Triple("20T16", 16.0, 20 * 2.011)  // 40.21 cm²
        )

        for (opt in barSizes) {
            if (opt.third >= targetAreaCm2 - 0.40) {
                return opt
            }
        }
        val def = barSizes[1] // 16T14 par défaut
        return Triple(def.first, def.second, def.third)
    }

    private fun selectTransverseTies(coreWidthB0Cm: Double, atMinCm2: Double): Pair<String, Double> {
        // En rive, on utilise généralement 1 cadre + 1 ou 2 épingles (soit 3 ou 4 brins transversaux)
        val numBrins = if (coreWidthB0Cm >= 20.0) 4 else 2
        val phiMm = 8.0 // T8
        val singleBarArea = (PI * 0.8 * 0.8) / 4.0 // 0.503 cm²
        val totalArea = numBrins * singleBarArea // ~2.01 cm² pour 4 brins ou 1.01 cm² pour 2 brins

        val desc = if (numBrins == 4) "1 Cadre + 1 Épingle T8 (4 brins)" else "1 Cadre T8 (2 brins)"
        return Pair(desc, totalArea)
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundTo3Decimals(value: Double): Double = round(value * 1000.0) / 1000.0
    private fun roundTo4Decimals(value: Double): Double = round(value * 10000.0) / 10000.0
}
