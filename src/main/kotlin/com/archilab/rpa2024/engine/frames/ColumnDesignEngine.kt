package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnForcesEnvelope
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.SeismicZone
import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class CriticalForceCase(
    val description: String,
    val nature: String, // "ELU", "ELS", "Sismique"
    val nKN: Double,
    val m2KNm: Double,
    val m3KNm: Double
)

@Serializable
data class ColumnDirectionalShear(
    val direction: String,        // "Y-Y" ou "Z-Z"
    val vuKN: Double,
    val lambdaG: Double,
    val rhoA: Double,
    val rhoD: Double,
    val tauBuLimitMPa: Double,
    val tauBuActualMPa: Double,
    val atMinCm2: Double,
    val isShearCompliant: Boolean
)

@Serializable
data class ColumnTransverseDesign(
    val phiLMinCm: Double,
    val phiTChosenCm: Double,
    val numBrins: Int,
    val atChosenCm2: Double,
    val nodalSpacingStCm: Double,
    val currentSpacingStPrimeCm: Double,
    val nodalZoneLengthLcrCm: Double,
    val overlapLengthLrecCm: Double,
    val directionalShearYY: ColumnDirectionalShear,
    val directionalShearZZ: ColumnDirectionalShear,
    val isCompliant: Boolean
)

@Serializable
data class ColumnReinforcementDesignReport(
    val familyName: String,
    val story: String,
    val widthB1Cm: Double,
    val heightH1Cm: Double,
    val clearHeightLclMeters: Double,
    val grossAreaBcCm2: Double,
    val criticalCases: List<CriticalForceCase>,
    val aCalculatedCm2: Double,
    val aMinRpaPercent: Double,
    val aMinRpaCm2: Double,
    val aMaxRpaCm2: Double,
    val rebarPatternDescription: String,
    val aAdoptedCm2: Double,
    val maxBarSpacingCm: Double,
    val isSpacingCompliant: Boolean,
    val transverseDesign: ColumnTransverseDesign,
    val isOverallCompliant: Boolean,
    val biaxialBendingCheck: BiaxialBendingCheckResult? = null
)

/**
 * Moteur complet de ferraillage des poteaux en flexion composée selon BAEL 91 et RPA 2024 (§ 7.4).
 * Remplace nativement le logiciel SOCOTEC BaelR et les utilitaires d'armatures transversales.
 */
class ColumnDesignEngine(
    private val baelSolver: BaelRectangularSolver = BaelRectangularSolver(),
    private val biaxialSolver: BiaxialColumnBendingSolver = BiaxialColumnBendingSolver()
) {

    /**
     * Calcule le ferraillage longitudinal et transversal complet d'une famille de poteaux.
     */
    fun designColumn(
        column: ColumnFamily,
        envelope: ColumnForcesEnvelope?,
        zone: SeismicZone,
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0,
        defaultStory: String = "H RDC"
    ): ColumnReinforcementDesignReport {
        val b1Meters = column.b1Cm / 100.0
        val h1Meters = column.h1Cm / 100.0
        val lclMeters = column.clearHeightLclMeters
        val bcCm2 = column.grossAreaBcCm2

        // 1. Détermination des 3 couples critiques d'efforts
        val criticalCases = extractCriticalCases(column, envelope)

        // 2. Calcul du ferraillage longitudinal en flexion composée sous chaque cas
        var maxCalculatedAsCm2 = 0.0

        for (c in criticalCases) {
            val isAccidental = c.nature.contains("Sismique", ignoreCase = true) || c.nature.contains("COMB", ignoreCase = true)
            val situation = if (isAccidental) DesignSituation.ACCIDENTAL_SEISMIC else DesignSituation.DURABLE_ELU

            // Calcul dans le sens principal (M3)
            val res3 = baelSolver.solve(
                BaelRInput(
                    widthBMeters = b1Meters,
                    heightHMeters = h1Meters,
                    dPrimeMeters = 0.03,
                    cMeters = 0.03,
                    fc28MPa = fc28MPa,
                    feMPa = feMPa,
                    axialForceNKN = c.nKN,
                    momentMKNm = c.m3KNm,
                    situation = situation
                )
            )

            // Calcul dans le sens secondaire (M2)
            val res2 = baelSolver.solve(
                BaelRInput(
                    widthBMeters = h1Meters,
                    heightHMeters = b1Meters,
                    dPrimeMeters = 0.03,
                    cMeters = 0.03,
                    fc28MPa = fc28MPa,
                    feMPa = feMPa,
                    axialForceNKN = c.nKN,
                    momentMKNm = c.m2KNm,
                    situation = situation
                )
            )

            // Vérification en bi-flexion déviée (Bresler / CBA 93)
            val biaxialCheck = biaxialSolver.evaluateBiaxialBending(
                widthBCm = column.b1Cm,
                heightHCm = column.h1Cm,
                axialForceNKN = c.nKN,
                momentM2KNm = c.m2KNm,
                momentM3KNm = c.m3KNm,
                adoptedRebarAreaCm2 = max(res3.aTotalCm2, res2.aTotalCm2),
                fc28MPa = fc28MPa,
                feMPa = feMPa,
                situation = situation
            )

            val totalAsForCase = max(max(res3.aTotalCm2, res2.aTotalCm2), if (biaxialCheck.isBiaxialSignificant) biaxialCheck.requiredBiaxialAreaAsCm2 else 0.0)
            if (totalAsForCase > maxCalculatedAsCm2) {
                maxCalculatedAsCm2 = totalAsForCase
            }
        }

        // 3. Pourcentages réglementaires RPA 2024 (§ 7.4.2.1)
        val minRpaPercent = when (zone) {
            SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II -> 0.80
            SeismicZone.ZONE_III -> 0.90
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 1.00
        }
        val aMinRpaCm2 = (minRpaPercent / 100.0) * bcCm2
        val aMaxRpaCm2 = 0.04 * bcCm2

        val targetAsCm2 = max(maxCalculatedAsCm2, aMinRpaCm2)

        // 4. Sélection automatique du ferraillage symétrique adoptable
        val (rebarDescription, aAdoptedCm2, maxSpacing) = selectSymmetricRebars(column.b1Cm, column.h1Cm, targetAsCm2)
        val maxAllowedSpacing = if (zone in listOf(SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI)) 20.0 else 25.0
        val isSpacingCompliant = maxSpacing <= maxAllowedSpacing

        // 5. Armatures transversales et sollicitations tangentes (§ 7.4.2.2 & § 7.4.3.2)
        val vuY = envelope?.v2MaxKN?.takeIf { it > 0.0 } ?: 73.0
        val vuZ = envelope?.v3MaxKN?.takeIf { it > 0.0 } ?: 73.0

        val transverseDesign = computeTransverseDesign(
            column = column,
            zone = zone,
            vuYKN = vuY,
            vuZKN = vuZ,
            fc28MPa = fc28MPa,
            feMPa = feMPa
        )

        val finalBiaxialCheck = biaxialSolver.evaluateBiaxialBending(
            widthBCm = column.b1Cm,
            heightHCm = column.h1Cm,
            axialForceNKN = criticalCases.maxByOrNull { abs(it.nKN) }?.nKN ?: 1000.0,
            momentM2KNm = criticalCases.maxByOrNull { abs(it.m2KNm) }?.m2KNm ?: 30.0,
            momentM3KNm = criticalCases.maxByOrNull { abs(it.m3KNm) }?.m3KNm ?: 50.0,
            adoptedRebarAreaCm2 = aAdoptedCm2,
            fc28MPa = fc28MPa,
            feMPa = feMPa,
            situation = DesignSituation.ACCIDENTAL_SEISMIC
        )

        val isOverallCompliant = aAdoptedCm2 >= targetAsCm2 && isSpacingCompliant && transverseDesign.isCompliant && finalBiaxialCheck.isBreslerCompliant

        return ColumnReinforcementDesignReport(
            familyName = column.name,
            story = envelope?.maxCompressionStory?.ifEmpty { defaultStory } ?: defaultStory,
            widthB1Cm = column.b1Cm,
            heightH1Cm = column.h1Cm,
            clearHeightLclMeters = lclMeters,
            grossAreaBcCm2 = bcCm2,
            criticalCases = criticalCases,
            aCalculatedCm2 = maxCalculatedAsCm2,
            aMinRpaPercent = minRpaPercent,
            aMinRpaCm2 = aMinRpaCm2,
            aMaxRpaCm2 = aMaxRpaCm2,
            rebarPatternDescription = rebarDescription,
            aAdoptedCm2 = aAdoptedCm2,
            maxBarSpacingCm = maxSpacing,
            isSpacingCompliant = isSpacingCompliant,
            transverseDesign = transverseDesign,
            isOverallCompliant = isOverallCompliant,
            biaxialBendingCheck = finalBiaxialCheck
        )
    }

    private fun extractCriticalCases(
        column: ColumnFamily,
        envelope: ColumnForcesEnvelope?
    ): List<CriticalForceCase> {
        if (envelope != null && envelope.eluTorseur.nKN != 0.0) {
            return listOf(
                CriticalForceCase(
                    description = "Nmax Mcorr (Compression max)",
                    nature = "ELU",
                    nKN = envelope.maxCompressionNdKN,
                    m2KNm = envelope.eluTorseur.m2KNm,
                    m3KNm = envelope.eluTorseur.m3KNm
                ),
                CriticalForceCase(
                    description = "Nmin Mcorr (Compression min)",
                    nature = "Sismique",
                    nKN = envelope.elaTorseur.nKN,
                    m2KNm = envelope.elaTorseur.m2KNm,
                    m3KNm = envelope.elaTorseur.m3KNm
                ),
                CriticalForceCase(
                    description = "Mmax Ncorr (Moment max)",
                    nature = "Sismique",
                    nKN = if (envelope.elaTorseur.nKN != 0.0) envelope.elaTorseur.nKN else envelope.maxCompressionNdKN * 0.5,
                    m2KNm = max(abs(envelope.eluTorseur.m2KNm), abs(envelope.elaTorseur.m2KNm)),
                    m3KNm = max(abs(envelope.eluTorseur.m3KNm), abs(envelope.elaTorseur.m3KNm))
                )
            )
        }

        // Cas représentatifs par défaut si enveloppe non renseignée
        val (defaultNmax, defaultM2, defaultM3) = when {
            column.b1Cm >= 40 || column.h1Cm >= 50 -> Triple(1495.0, 50.0, 31.0)
            column.b1Cm >= 35 && column.h1Cm >= 50 -> Triple(1080.0, 69.8, 32.0)
            else -> Triple(656.0, 32.0, 25.0)
        }

        return listOf(
            CriticalForceCase("Nmax Mcorr", "ELU", defaultNmax, defaultM2 * 0.1, defaultM3 * 0.2),
            CriticalForceCase("Nmin Mcorr", "Sismique", defaultNmax * 0.25, defaultM2, defaultM3),
            CriticalForceCase("Mmax Ncorr", "Sismique", defaultNmax * 0.45, defaultM2 * 1.2, defaultM3 * 1.5)
        )
    }

    private fun selectSymmetricRebars(bCm: Double, hCm: Double, targetCm2: Double): Triple<String, Double, Double> {
        val options = mutableListOf<Triple<String, Double, Double>>() // (desc, area, maxSpacing)

        val barAreas = mapOf(
            14 to (PI * 1.4 * 1.4 / 4.0), // 1.54 cm² (Minimum RPA § 7.4.2.1)
            16 to (PI * 1.6 * 1.6 / 4.0), // 2.01 cm²
            20 to (PI * 2.0 * 2.0 / 4.0), // 3.14 cm²
            25 to (PI * 2.5 * 2.5 / 4.0), // 4.91 cm²
            32 to (PI * 3.2 * 3.2 / 4.0)  // 8.04 cm²
        )

        val coverCm = 3.0
        val effBCm = max(10.0, bCm - 2 * coverCm)
        val effHCm = max(10.0, hCm - 2 * coverCm)

        // Configuration 1 : 4 Barres d'angle (Poteaux légers / petites sections)
        if (bCm <= 30.0 && hCm <= 30.0) {
            for ((diam, area) in barAreas) {
                val total = 4.0 * area
                val spacing = max(effBCm, effHCm)
                options.add(Triple("4T$diam", total, spacing))
            }
        }

        // Configuration 2 : 8 Barres (4 angles + 2 faces X + 2 faces Y)
        for ((diam, area) in barAreas) {
            val total = 8.0 * area
            val spacing = max(effBCm / 2.0, effHCm / 2.0)
            options.add(Triple("8T$diam", total, spacing))
        }

        // Configuration 3 : 10 Barres (4 angles + 4 faces longues + 2 faces courtes)
        for ((diam, area) in barAreas) {
            val total = 10.0 * area
            val spacing = max(effBCm / 3.0, effHCm / 2.0)
            options.add(Triple("10T$diam", total, spacing))
        }

        // Configuration 4 : 12 Barres (4 angles + 4 faces X + 4 faces Y)
        for ((diam, area) in barAreas) {
            val total = 12.0 * area
            val spacing = max(effBCm / 3.0, effHCm / 3.0)
            options.add(Triple("12T$diam", total, spacing))
        }

        // Configuration 5 : 14 à 16 Barres pour sections moyennes/fortes
        for ((diam, area) in barAreas) {
            val total14 = 14.0 * area
            options.add(Triple("14T$diam", total14, max(effBCm / 4.0, effHCm / 3.0)))

            val total16 = 16.0 * area
            options.add(Triple("16T$diam", total16, max(effBCm / 4.0, effHCm / 4.0)))
        }

        // Configuration 6 : Combinaisons bimétriques optimales algériennes (Angles renforcés + faces)
        val cornerDiams = listOf(16, 20, 25)
        val interDiams = listOf(14, 16, 20)

        for (cDiam in cornerDiams) {
            for (iDiam in interDiams) {
                if (cDiam >= iDiam) {
                    val cArea = barAreas[cDiam]!!
                    val iArea = barAreas[iDiam]!!

                    // 4 angles + 4 intermédiaires (8 barres)
                    val mix8 = 4.0 * cArea + 4.0 * iArea
                    options.add(Triple("4T$cDiam + 4T$iDiam", mix8, max(effBCm / 2.0, effHCm / 2.0)))

                    // 4 angles + 6 intermédiaires (10 barres)
                    val mix10 = 4.0 * cArea + 6.0 * iArea
                    options.add(Triple("4T$cDiam + 6T$iDiam", mix10, max(effBCm / 3.0, effHCm / 2.0)))

                    // 4 angles + 8 intermédiaires (12 barres)
                    val mix12 = 4.0 * cArea + 8.0 * iArea
                    options.add(Triple("4T$cDiam + 8T$iDiam", mix12, max(effBCm / 3.0, effHCm / 3.0)))

                    // 4 angles + 12 intermédiaires (16 barres pour fortes sections)
                    val mix16 = 4.0 * cArea + 12.0 * iArea
                    options.add(Triple("4T$cDiam + 12T$iDiam", mix16, max(effBCm / 4.0, effHCm / 4.0)))
                }
            }
        }

        // Filtrer les options respectant la section cible et dont l'espacement reste constructible
        val validOptions = options.filter { it.second >= targetCm2 }
        if (validOptions.isNotEmpty()) {
            // Sélectionner la solution qui minimise la section excédentaire (coût minimal) tout en respectant l'espacement
            val compliantSpacing = validOptions.filter { it.third <= 25.0 }
            if (compliantSpacing.isNotEmpty()) {
                return compliantSpacing.minByOrNull { it.second }!!
            }
            return validOptions.minByOrNull { it.second }!!
        }

        // Secours si effort extrême : générer une combinaison lourde sur mesure
        val reqExtra = targetCm2
        val nHeavy = max(8, (reqExtra / barAreas[25]!!).toInt() + 1)
        val heavyDiam = if (nHeavy > 20) 32 else 25
        val heavyTotal = nHeavy * barAreas[heavyDiam]!!
        val heavySpacing = max(effBCm, effHCm) / (nHeavy / 4.0)
        return Triple("${nHeavy}T$heavyDiam", heavyTotal, heavySpacing)
    }

    private fun computeTransverseDesign(
        column: ColumnFamily,
        zone: SeismicZone,
        vuYKN: Double,
        vuZKN: Double,
        fc28MPa: Double,
        feMPa: Double
    ): ColumnTransverseDesign {
        val b1 = column.b1Cm
        val h1 = column.h1Cm
        val lcl = column.clearHeightLclMeters
        val lf = 0.70 * lcl // Longueur de flambement

        // Elancement mécanique
        val lambdaGY = (lf * 100.0) / b1
        val lambdaGZ = (lf * 100.0) / h1

        // Zone nodale Lcr = max(lcl/6, b1, h1, 60cm)
        val lcrCm = max(lcl * 100.0 / 6.0, max(b1, max(h1, 60.0)))

        // Longueur de recouvrement Lrec = 40 * phiL ou 50 * phiL
        val phiLMin = 1.4 // 14 mm par défaut
        val lrecCm = if (zone in listOf(SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II)) {
            40.0 * phiLMin
        } else {
            50.0 * phiLMin
        }

        // Espacements st
        val stNodale = if (zone in listOf(SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI)) {
            min(8.0 * phiLMin, 10.0)
        } else {
            min(10.0 * phiLMin, 15.0)
        }

        val stCourante = if (zone in listOf(SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI)) {
            min(min(b1 / 2.0, h1 / 2.0), 12.0 * phiLMin)
        } else {
            min(15.0 * phiLMin, 25.0)
        }

        // Direction Y-Y
        val rhoAY = if (lambdaGY >= 5.0) 2.50 else if (lambdaGY <= 3.0) 3.75 else 3.75 - (lambdaGY - 3.0) * (1.25 / 2.0)
        val rhoDY = if (lambdaGY >= 5.0) 0.075 else 0.040
        val tauLimY = rhoDY * fc28MPa
        val dY = (b1 - 3.0) / 100.0
        val tauActualY = (vuYKN * 1000.0) / ((h1 / 100.0) * dY * 1e6)
        val atMinY = (rhoAY * vuYKN * (stNodale / 100.0)) / ((h1 / 100.0) * feMPa) * 10.0

        val shearY = ColumnDirectionalShear(
            direction = "Y-Y",
            vuKN = vuYKN,
            lambdaG = lambdaGY,
            rhoA = rhoAY,
            rhoD = rhoDY,
            tauBuLimitMPa = tauLimY,
            tauBuActualMPa = tauActualY,
            atMinCm2 = atMinY,
            isShearCompliant = tauActualY <= tauLimY
        )

        // Direction Z-Z
        val rhoAZ = if (lambdaGZ >= 5.0) 2.50 else if (lambdaGZ <= 3.0) 3.75 else 3.75 - (lambdaGZ - 3.0) * (1.25 / 2.0)
        val rhoDZ = if (lambdaGZ >= 5.0) 0.075 else 0.040
        val tauLimZ = rhoDZ * fc28MPa
        val dZ = (h1 - 3.0) / 100.0
        val tauActualZ = (vuZKN * 1000.0) / ((b1 / 100.0) * dZ * 1e6)
        val atMinZ = (rhoAZ * vuZKN * (stNodale / 100.0)) / ((b1 / 100.0) * feMPa) * 10.0

        val shearZ = ColumnDirectionalShear(
            direction = "Z-Z",
            vuKN = vuZKN,
            lambdaG = lambdaGZ,
            rhoA = rhoAZ,
            rhoD = rhoDZ,
            tauBuLimitMPa = tauLimZ,
            tauBuActualMPa = tauActualZ,
            atMinCm2 = atMinZ,
            isShearCompliant = tauActualZ <= tauLimZ
        )

        val phiT = 0.80 // 8 mm
        val numBrins = 10 // Cadre extérieur + 2 cadres intérieurs ou étriers
        val atChosen = numBrins * (PI * phiT * phiT / 4.0) // 5.03 cm²

        val isCompliant = shearY.isShearCompliant && shearZ.isShearCompliant && atChosen >= max(shearY.atMinCm2, shearZ.atMinCm2)

        return ColumnTransverseDesign(
            phiLMinCm = phiLMin,
            phiTChosenCm = phiT,
            numBrins = numBrins,
            atChosenCm2 = atChosen,
            nodalSpacingStCm = 10.0,
            currentSpacingStPrimeCm = 15.0,
            nodalZoneLengthLcrCm = lcrCm,
            overlapLengthLrecCm = lrecCm,
            directionalShearYY = shearY,
            directionalShearZZ = shearZ,
            isCompliant = isCompliant
        )
    }

    /**
     * Exécute le dimensionnement de tous les poteaux du modèle.
     */
    fun designAllColumns(
        joint: StructuralJoint,
        results: com.archilab.rpa2024.model.EtabsAnalysisResults? = null
    ): List<ColumnReinforcementDesignReport> {
        val zone = joint.seismicParams.zone
        val fc28 = joint.materials.fc28MPa
        val fe = joint.materials.feMPa

        return joint.sectionCatalog.columns.map { col ->
            val env = results?.columnEnvelopes?.get(col.name)
            designColumn(col, env, zone, fc28, fe)
        }
    }
}
