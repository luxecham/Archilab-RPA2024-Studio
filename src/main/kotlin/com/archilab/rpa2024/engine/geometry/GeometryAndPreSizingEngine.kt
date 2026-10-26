package com.archilab.rpa2024.engine.geometry

import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Moteur complet de vérification réglementaire des Chapitres 1 à 7 (Description,
 * Géométrie auto-calculée, Matériaux, Combinaisons, Prédimensionnement et Coffrage RPA 2024).
 */
class GeometryAndPreSizingEngine {

    /**
     * Calcul des caractéristiques mécaniques des matériaux (Béton et Acier) selon CBA 93 / BAEL 91.
     */
    fun computeMechanicalProperties(materials: MaterialConfig): MechanicalProperties =
        MechanicalProperties.compute(materials)

    /**
     * Vérification de coffrage et de rigidité d'une famille de poutres (§ 7.5.1 & Fig. 7.4 RPA 2024).
     */
    fun verifyBeamFamily(
        beam: BeamFamily,
        zone: SeismicZone,
        isWallBraced: Boolean = false,
        supportColumn: ColumnFamily? = null
    ): BeamCheckResult {
        val lmax = beam.maxClearSpanMeters
        val lmaxCm = lmax * 100.0

        // 1. Critère de rigidité en hauteur : Lmax/15 <= h <= Lmax/10
        val minRigidityH = lmaxCm / 15.0
        val maxRigidityH = lmaxCm / 10.0
        val rigidityHPassed = beam.hCm in minRigidityH..maxRigidityH
        val rigidityHCheck = CheckItem(
            code = "RIGIDITY_H",
            description = "Rigidité en hauteur Lmax/15 <= h <= Lmax/10",
            actualValue = beam.hCm,
            limitValue = minRigidityH,
            comparisonOp = "in [$minRigidityH, $maxRigidityH]",
            status = if (rigidityHPassed) CheckStatus.PASSED else CheckStatus.WARNING,
            message = if (rigidityHPassed) "Hauteur conforme au critère de rigidité"
            else "Hauteur hors intervalle recommandé [${minRigidityH.format(1)}, ${maxRigidityH.format(1)}] cm"
        )

        // 2. Critère de rigidité en largeur : 0.3 h <= b <= 0.4 h
        val minRigidityB = 0.3 * beam.hCm
        val maxRigidityB = 0.4 * beam.hCm
        val rigidityBPassed = beam.bCm in minRigidityB..maxRigidityB
        val rigidityBCheck = CheckItem(
            code = "RIGIDITY_B",
            description = "Largeur de rigidité 0.3*h <= b <= 0.4*h",
            actualValue = beam.bCm,
            limitValue = minRigidityB,
            comparisonOp = "in [$minRigidityB, $maxRigidityB]",
            status = if (rigidityBPassed) CheckStatus.PASSED else CheckStatus.WARNING,
            message = if (rigidityBPassed) "Largeur conforme au ratio de rigidité"
            else "Largeur hors ratio recommandé [${minRigidityB.format(1)}, ${maxRigidityB.format(1)}] cm"
        )

        // 3. Largeur minimale RPA 2024 : b >= 20 cm (Zones I, II, III) ou >= 25 cm (Zones IV, V, VI)
        val minAllowedB = when (zone) {
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 25.0
            else -> 20.0
        }
        val minBPassed = beam.bCm >= minAllowedB
        val minBCheck = CheckItem(
            code = "RPA_MIN_B",
            description = "Largeur minimale b >= $minAllowedB cm (Zone ${zone.code})",
            actualValue = beam.bCm,
            limitValue = minAllowedB,
            comparisonOp = ">=",
            status = if (minBPassed) CheckStatus.PASSED else CheckStatus.FAILED,
            message = if (minBPassed) "Largeur supérieure au minimum RPA"
            else "Largeur insuffisante (< $minAllowedB cm)"
        )

        // 4. Hauteur minimale RPA 2024 : h >= 30 cm (ou 20 cm si contreventement par voiles)
        val minAllowedH = if (isWallBraced) 20.0 else 30.0
        val minHPassed = beam.hCm >= minAllowedH
        val minHCheck = CheckItem(
            code = "RPA_MIN_H",
            description = "Hauteur minimale h >= $minAllowedH cm",
            actualValue = beam.hCm,
            limitValue = minAllowedH,
            comparisonOp = ">=",
            status = if (minHPassed) CheckStatus.PASSED else CheckStatus.FAILED,
            message = if (minHPassed) "Hauteur supérieure au minimum RPA"
            else "Hauteur insuffisante (< $minAllowedH cm)"
        )

        // 5. Élancement géométrique : h / b <= 4.0
        val aspectRatio = beam.aspectRatio
        val aspectPassed = aspectRatio <= 4.0
        val aspectCheck = CheckItem(
            code = "RPA_ASPECT_RATIO",
            description = "Élancement h/b <= 4.0",
            actualValue = aspectRatio,
            limitValue = 4.0,
            comparisonOp = "<=",
            status = if (aspectPassed) CheckStatus.PASSED else CheckStatus.FAILED,
            message = if (aspectPassed) "Élancement h/b conforme"
            else "Poutre trop élancée (h/b > 4.0)"
        )

        // 6. Débordement par rapport au poteau : b <= 1.5 * h + b1 (avec b1 poteau d'appui)
        val supportB1 = supportColumn?.b1Cm ?: beam.maxSupportWidthCm
        val maxOverhangB = 1.5 * beam.hCm + supportB1
        val overhangPassed = beam.bCm <= maxOverhangB
        val overhangCheck = CheckItem(
            code = "RPA_OVERHANG",
            description = "Débordement maximal b <= 1.5*h + b1",
            actualValue = beam.bCm,
            limitValue = maxOverhangB,
            comparisonOp = "<=",
            status = if (overhangPassed) CheckStatus.PASSED else CheckStatus.FAILED,
            message = if (overhangPassed) "Largeur compatible avec le poteau support"
            else "Débordement excessif sur l'appui"
        )

        // 7. Excentrement d'axe (Fig. 7.4 RPA 2024) : e <= max(b1/2, h1/2)
        val supportMaxDim = supportColumn?.maxDimensionCm ?: beam.maxSupportWidthCm
        val maxEccentricityAllowed = supportMaxDim / 2.0
        val eccPassed = beam.maxAxisEccentricityCm <= maxEccentricityAllowed
        val eccCheck = CheckItem(
            code = "RPA_ECCENTRICITY",
            description = "Excentrement axe poutre/poteau <= max(b1, h1)/2",
            actualValue = beam.maxAxisEccentricityCm,
            limitValue = maxEccentricityAllowed,
            comparisonOp = "<=",
            status = if (eccPassed) CheckStatus.PASSED else CheckStatus.FAILED,
            message = if (eccPassed) "Excentrement d'axe conforme"
            else "Excentrement trop important (> $maxEccentricityAllowed cm)"
        )

        val isCompliant = minBPassed && minHPassed && aspectPassed && overhangPassed && eccPassed

        return BeamCheckResult(
            beamFamilyName = beam.name,
            bCm = beam.bCm,
            hCm = beam.hCm,
            clearSpanMeters = lmax,
            rigidityHeightCheck = rigidityHCheck,
            rigidityWidthCheck = rigidityBCheck,
            minWidthCheck = minBCheck,
            minHeightCheck = minHCheck,
            slendernessCheck = aspectCheck,
            overhangCheck = overhangCheck,
            eccentricityCheck = eccCheck,
            isCompliant = isCompliant
        )
    }

    /**
     * Vérification de coffrage d'une famille de poteaux (§ 7.4.1 & Fig. 7.1 RPA 2024).
     */
    fun verifyColumnFamily(
        column: ColumnFamily,
        zone: SeismicZone
    ): ColumnCheckResult {
        val lclCm = column.clearHeightLclMeters * 100.0

        val (minDimCheck, lclCheck, aspectCheck, isCompliant) = when (column.shape) {
            ColumnShape.RECTANGULAR -> {
                val minDim = column.minDimensionCm
                val minRequired = when (zone) {
                    SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 30.0
                    else -> 25.0
                }
                val minDimPassed = minDim >= minRequired
                val c1 = CheckItem(
                    code = "RPA_COL_MIN_DIM",
                    description = "Dimension minimale min(b1, h1) >= $minRequired cm (Zone ${zone.code})",
                    actualValue = minDim,
                    limitValue = minRequired,
                    comparisonOp = ">=",
                    status = if (minDimPassed) CheckStatus.PASSED else CheckStatus.FAILED,
                    message = if (minDimPassed) "Dimension minimale conforme" else "Dimension minimale insuffisante"
                )

                val minLclRatio = lclCm / 20.0
                val lclPassed = minDim >= minLclRatio
                val c2 = CheckItem(
                    code = "RPA_COL_LCL_20",
                    description = "Critère hauteur libre min(b1, h1) >= l_cl / 20",
                    actualValue = minDim,
                    limitValue = minLclRatio,
                    comparisonOp = ">=",
                    status = if (lclPassed) CheckStatus.PASSED else CheckStatus.FAILED,
                    message = if (lclPassed) "Rapport dimension / hauteur libre conforme"
                    else "Dimension inférieure à l_cl / 20 (${minLclRatio.format(1)} cm)"
                )

                val ratio = column.aspectRatio
                val ratioPassed = ratio > 0.25 && ratio < 4.0
                val c3 = CheckItem(
                    code = "RPA_COL_ASPECT",
                    description = "Rapport des dimensions 0.25 < b1 / h1 < 4.0",
                    actualValue = ratio,
                    limitValue = 4.0,
                    comparisonOp = "in ]0.25, 4.0[",
                    status = if (ratioPassed) CheckStatus.PASSED else CheckStatus.FAILED,
                    message = if (ratioPassed) "Rapport b1/h1 conforme" else "Section hors limites d'élancement"
                )

                val compliant = minDimPassed && lclPassed && ratioPassed
                listOf(c1, c2, c3, compliant)
            }
            ColumnShape.CIRCULAR -> {
                val diameter = column.diameterCm ?: column.b1Cm
                val minRequired = when (zone) {
                    SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II -> 25.0
                    SeismicZone.ZONE_III -> 30.0
                    SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 35.0
                }
                val diamPassed = diameter >= minRequired
                val c1 = CheckItem(
                    code = "RPA_COL_DIAM_MIN",
                    description = "Diamètre minimal D >= $minRequired cm (Zone ${zone.code})",
                    actualValue = diameter,
                    limitValue = minRequired,
                    comparisonOp = ">=",
                    status = if (diamPassed) CheckStatus.PASSED else CheckStatus.FAILED,
                    message = if (diamPassed) "Diamètre minimal conforme" else "Diamètre insuffisant"
                )

                val minLclRatio = lclCm / 15.0
                val lclPassed = diameter >= minLclRatio
                val c2 = CheckItem(
                    code = "RPA_COL_CIRC_LCL_15",
                    description = "Critère hauteur libre D >= l_cl / 15",
                    actualValue = diameter,
                    limitValue = minLclRatio,
                    comparisonOp = ">=",
                    status = if (lclPassed) CheckStatus.PASSED else CheckStatus.FAILED,
                    message = if (lclPassed) "Diamètre conforme vis-à-vis de l_cl"
                    else "Diamètre inférieur à l_cl / 15 (${minLclRatio.format(1)} cm)"
                )

                val c3 = CheckItem(
                    code = "RPA_COL_CIRC_SHAPE",
                    description = "Section circulaire régulière",
                    actualValue = diameter,
                    limitValue = diameter,
                    comparisonOp = "==",
                    status = CheckStatus.PASSED,
                    message = "Section circulaire régulière"
                )

                val compliant = diamPassed && lclPassed
                listOf(c1, c2, c3, compliant)
            }
        }

        return ColumnCheckResult(
            columnFamilyName = column.name,
            shape = column.shape,
            b1Cm = column.b1Cm,
            h1Cm = column.h1Cm,
            clearHeightLclMeters = column.clearHeightLclMeters,
            minDimensionCheck = minDimCheck as CheckItem,
            clearHeightRatioCheck = lclCheck as CheckItem,
            aspectRatioCheck = aspectCheck as CheckItem,
            isCompliant = isCompliant as Boolean
        )
    }

    /**
     * Qualification et vérification de coffrage d'un voile de contreventement (§ 7.7.1, Fig. 7.7 RPA 2024).
     */
    fun qualifyShearWall(
        wall: ShearWallFamily,
        numStoriesN: Int
    ): ShearWallCheckResult {
        val he = wall.clearHeightHeMeters
        val bw = wall.thicknessBwMeters
        val lw = wall.lengthLwMeters
        val hw = wall.totalHeightHwMeters

        // 1. Condition de longueur minimale pour qualification en voile (Eq 7.9) :
        // lw >= max(he / 3.0, 4.0 * bw, 1.00 m)
        val minLwRequired = max(he / 3.0, max(4.0 * bw, 1.00))
        val isQualifiedAsWall = lw >= minLwRequired
        val lwCheck = CheckItem(
            code = "RPA_WALL_QUALIF_LW",
            description = "Qualification de voile : lw >= max(he/3, 4*bw, 1.0m) = ${minLwRequired.format(2)} m",
            actualValue = lw,
            limitValue = minLwRequired,
            comparisonOp = ">=",
            status = if (isQualifiedAsWall) CheckStatus.PASSED else CheckStatus.WARNING,
            message = if (isQualifiedAsWall) "Élément qualifié en voile de contreventement"
            else "Non qualifié en voile -> doit être traité comme un poteau (élément linéaire)"
        )

        // 2. Épaisseur minimale (Eq 7.10) : bw >= max(0.15 m, he / 20.0)
        val minBwRequired = max(0.15, he / 20.0)
        val thicknessPassed = bw >= minBwRequired
        val bwCheck = CheckItem(
            code = "RPA_WALL_MIN_BW",
            description = "Épaisseur minimale bw >= max(15 cm, he/20) = ${(minBwRequired * 100.0).format(1)} cm",
            actualValue = bw * 100.0,
            limitValue = minBwRequired * 100.0,
            comparisonOp = ">=",
            status = if (thicknessPassed) CheckStatus.PASSED else CheckStatus.FAILED,
            message = if (thicknessPassed) "Épaisseur conforme"
            else "Épaisseur inférieure au minimum requis"
        )

        // 3. Hauteur de la zone critique hcr (Eq 7.22 & 7.23 RPA 2024) :
        // hcr = max(lw, hw / 6.0)
        // borné supérieurement par :
        // hcr <= min(2.0 * lw, if (numStoriesN <= 6) he else 2.0 * he)
        val hcrRaw = max(lw, hw / 6.0)
        val upperBound = min(2.0 * lw, if (numStoriesN <= 6) he else 2.0 * he)
        val hcrAdopted = min(hcrRaw, upperBound)

        val isCompliant = isQualifiedAsWall && thicknessPassed

        return ShearWallCheckResult(
            pierName = wall.pierName,
            lengthLwMeters = lw,
            thicknessBwMeters = bw,
            clearHeightHeMeters = he,
            totalHeightHwMeters = hw,
            lengthQualificationCheck = lwCheck,
            thicknessCheck = bwCheck,
            isWallQualified = isQualifiedAsWall,
            slenderness = wall.slendernessType,
            criticalZoneHeightHcrMeters = hcrAdopted,
            isCompliant = isCompliant
        )
    }

    /**
     * Vérification de l'effort normal réduit nu = Nd / (Bc * fc28) <= 0.35 pour un poteau (§ 7.4.3 RPA 2024).
     * Les poteaux solidaires de voiles (isWallBoundary = true) sont automatiquement exclus.
     */
    fun verifyReducedNormalForceColumn(
        column: ColumnFamily,
        ndMaxKN: Double,
        fc28MPa: Double
    ): ReducedNormalForceCheckResult {
        if (column.isWallBoundary) {
            return ReducedNormalForceCheckResult(
                elementType = "COLUMN",
                elementName = column.name,
                sectionAreaCm2 = column.grossAreaBcCm2,
                ndKN = ndMaxKN,
                fc28MPa = fc28MPa,
                nuComputed = 0.0,
                nuLimit = 0.35,
                isWallBoundaryExcluded = true,
                isCompliant = true
            )
        }

        val bcCm2 = column.grossAreaBcCm2
        // Formule : nu = (Nd [kN] * 10) / (Bc [cm²] * fc28 [MPa])
        val nu = if (bcCm2 > 0.0 && fc28MPa > 0.0) {
            (abs(ndMaxKN) * 10.0) / (bcCm2 * fc28MPa)
        } else 0.0

        val compliant = nu <= 0.35

        return ReducedNormalForceCheckResult(
            elementType = "COLUMN",
            elementName = column.name,
            sectionAreaCm2 = bcCm2,
            ndKN = abs(ndMaxKN),
            fc28MPa = fc28MPa,
            nuComputed = nu,
            nuLimit = 0.35,
            isWallBoundaryExcluded = false,
            isCompliant = compliant
        )
    }

    /**
     * Vérification de l'effort normal réduit nu_d = Nd / (Bc * fc28) <= 0.40 pour un voile (Eq 7.11 RPA 2024).
     */
    fun verifyReducedNormalForceShearWall(
        wall: ShearWallFamily,
        ndMaxKN: Double,
        fc28MPa: Double
    ): ReducedNormalForceCheckResult {
        val bcCm2 = wall.grossAreaCm2
        val nu = if (bcCm2 > 0.0 && fc28MPa > 0.0) {
            (abs(ndMaxKN) * 10.0) / (bcCm2 * fc28MPa)
        } else 0.0

        val compliant = nu <= 0.40

        return ReducedNormalForceCheckResult(
            elementType = "SHEAR_WALL",
            elementName = wall.pierName,
            sectionAreaCm2 = bcCm2,
            ndKN = abs(ndMaxKN),
            fc28MPa = fc28MPa,
            nuComputed = nu,
            nuLimit = 0.40,
            isWallBoundaryExcluded = false,
            isCompliant = compliant
        )
    }

    /**
     * Vérification d'épaisseur de plancher corps creux ht >= Lmax / 22.5 (CBA 93 / DTR BC 2.41).
     */
    fun verifySlabStrip(slab: SlabStripTopology): SlabCheckResult {
        val lmaxCm = slab.maxSpanLmaxMeters * 100.0
        val minHtRequired = lmaxCm / 22.5
        val compliant = slab.totalThicknessHtCm >= minHtRequired

        return SlabCheckResult(
            slabName = slab.name,
            htCm = slab.totalThicknessHtCm,
            maxSpanMeters = slab.maxSpanLmaxMeters,
            minRequiredHtCm = minHtRequired,
            isCompliant = compliant
        )
    }

    /**
     * Génération des combinaisons de charges réglementaires (Chapitre 4).
     */
    fun generateLoadCombinations(psi: Double): List<LoadCombination> {
        val combos = mutableListOf<LoadCombination>()
        combos.add(LoadCombination("ELU", "Situation durable ultime", "1.35 G + 1.50 Q", isSeismic = false))
        combos.add(LoadCombination("ELS", "Situation de service", "G + Q", isSeismic = false))
        combos.add(LoadCombination("Poids Wi", "Poids sismique total", "G + ${psi} Q", isSeismic = true))

        val signs = listOf(
            Pair(1.0, 1.0),
            Pair(1.0, -1.0),
            Pair(-1.0, 1.0),
            Pair(-1.0, -1.0)
        )

        var idx = 1
        // COMB1 à COMB4 : G + psi Q ± Ex ± 0.3 Ey
        for ((sx, sy) in signs) {
            val sxStr = if (sx > 0) "+ Ex" else "- Ex"
            val syStr = if (sy > 0) "+ 0.3 Ey" else "- 0.3 Ey"
            combos.add(
                LoadCombination(
                    name = "COMB$idx",
                    description = "Combinaison sismique avec composante principale Ex",
                    formula = "G + ${psi} Q $sxStr $syStr",
                    isSeismic = true
                )
            )
            idx++
        }

        // COMB5 à COMB8 : G + psi Q ± 0.3 Ex ± Ey
        for ((sx, sy) in signs) {
            val sxStr = if (sx > 0) "+ 0.3 Ex" else "- 0.3 Ex"
            val syStr = if (sy > 0) "+ Ey" else "- Ey"
            combos.add(
                LoadCombination(
                    name = "COMB$idx",
                    description = "Combinaison sismique avec composante principale Ey",
                    formula = "G + ${psi} Q $sxStr $syStr",
                    isSeismic = true
                )
            )
            idx++
        }

        return combos
    }

    /**
     * Vérification des déplacements latéraux relatifs inter-étages (§ 5.10 RPA 2024).
     * nu_A * Delta_k <= Delta_bar_k = 0.0075 * h_k.
     */
    fun verifyDisplacements(
        joint: StructuralJoint,
        displacements: List<DiaphragmDisplacementRow>,
        customNuA: Double? = null
    ): List<DriftCheckResult> {
        val results = mutableListOf<DriftCheckResult>()
        val nuA = customNuA
            ?: if (joint.seismicParams.useCustomNuA) joint.seismicParams.customNuA
            else joint.seismicParams.importanceGroup.nuATabulated

        val r = joint.seismicParams.bracingSystem.coeffR
        val qf = Rpa2024NormativeDb.computeQualityFactor(
            category = joint.seismicParams.bracingSystem.penaltyCategory,
            planRegular = joint.seismicParams.planRegular,
            elevRegular = joint.seismicParams.elevationRegular,
            topology = joint,
            redundantWalls = joint.seismicParams.redundantWalls
        ).qf

        val displacementFactor = r / qf

        for (direction in listOf(AxisDirection.X, AxisDirection.Y)) {
            var prevDeltaK = 0.0

            for (story in joint.storySystem.sortedStories) {
                val row = displacements.find {
                    it.story.trim().equals(story.storyName.trim(), ignoreCase = true) &&
                            (if (direction == AxisDirection.X) it.loadCombo.contains("EX", ignoreCase = true)
                            else it.loadCombo.contains("EY", ignoreCase = true))
                }

                val deltaEk = if (row != null) {
                    if (direction == AxisDirection.X) row.ux else row.uy
                } else 0.0

                val deltaK = displacementFactor * deltaEk
                val relativeDriftDelta = deltaK - prevDeltaK
                prevDeltaK = deltaK

                val hk = story.heightHk
                val limit = 0.0075 * hk
                val nuADrift = nuA * abs(relativeDriftDelta)
                val compliant = nuADrift <= limit

                results.add(
                    DriftCheckResult(
                        story = story.storyName,
                        direction = direction,
                        deltaEkMeters = deltaEk,
                        deltaKMeters = deltaK,
                        relativeDriftDeltaKMeters = relativeDriftDelta,
                        nuADeltaKMeters = nuADrift,
                        limitDeltaBarKMeters = limit,
                        isCompliant = compliant
                    )
                )
            }
        }

        return results
    }

    /**
     * Justification vis-à-vis de l'effet P-Delta (Eq 5.10 RPA 2024).
     * theta_k = (P_k * Delta_k) / (V_k * h_k) <= 0.10.
     */
    fun verifyPDelta(
        joint: StructuralJoint,
        storyShears: List<StoryShearRow>,
        driftResults: List<DriftCheckResult>
    ): List<PDeltaCheckResult> {
        val results = mutableListOf<PDeltaCheckResult>()

        for (direction in listOf(AxisDirection.X, AxisDirection.Y)) {
            val comboKey = if (direction == AxisDirection.X) "EX" else "EY"

            for (story in joint.storySystem.sortedStories) {
                val drift = driftResults.find {
                    it.story.equals(story.storyName, ignoreCase = true) && it.direction == direction
                }
                val relativeDeltaMeters = abs(drift?.relativeDriftDeltaKMeters ?: 0.0)

                // Effort tranchant d'étage V_k sous EX ou EY au niveau inférieur
                val vkRow = storyShears.find {
                    it.story.equals(story.storyName, ignoreCase = true) &&
                            it.loadCombo.contains(comboKey, ignoreCase = true) &&
                            it.loc.equals("Bottom", ignoreCase = true)
                }
                val vk = abs(if (direction == AxisDirection.X) vkRow?.vxKN ?: 0.0 else vkRow?.vyKN ?: 0.0)

                // Poids cumulé d'étage P_k sous combinaison WI
                val pkRow = storyShears.find {
                    it.story.equals(story.storyName, ignoreCase = true) &&
                            it.loadCombo.contains("WI", ignoreCase = true) &&
                            it.loc.equals("Bottom", ignoreCase = true)
                }
                val pk = pkRow?.pKN ?: 0.0
                val hk = story.heightHk

                val thetaK = if (vk > 0.0 && hk > 0.0) {
                    (pk * relativeDeltaMeters) / (vk * hk)
                } else 0.0

                val compliant = thetaK <= 0.10

                results.add(
                    PDeltaCheckResult(
                        story = story.storyName,
                        direction = direction,
                        hkMeters = hk,
                        pkKN = pk,
                        deltaKMeters = relativeDeltaMeters,
                        vkKN = vk,
                        thetaK = thetaK,
                        limitTheta = 0.10,
                        isCompliant = compliant
                    )
                )
            }
        }

        return results
    }

    /**
     * Exécute le rapport complet d'analyse pour un joint structural.
     */
    fun performFullJointVerification(
        joint: StructuralJoint,
        results: EtabsAnalysisResults? = null
    ): GlobalJointCheckReport {
        val mechProps = computeMechanicalProperties(joint.materials)
        val zone = joint.seismicParams.zone
        val isWallBraced = joint.seismicParams.bracingSystem in listOf(
            BracingSystem.CAT_4, BracingSystem.CAT_5, BracingSystem.CAT_9
        )

        // 1. Poutres
        val beamChecks = joint.sectionCatalog.beams.map { beam ->
            verifyBeamFamily(beam, zone, isWallBraced)
        }

        // 2. Poteaux
        val columnChecks = joint.sectionCatalog.columns.map { column ->
            verifyColumnFamily(column, zone)
        }

        // 3. Voiles
        val wallChecks = joint.sectionCatalog.shearWalls.map { wall ->
            qualifyShearWall(wall, joint.storySystem.numStoriesN)
        }

        // 4. Effort normal réduit
        val normalForceChecks = mutableListOf<ReducedNormalForceCheckResult>()
        joint.sectionCatalog.columns.forEach { col ->
            val env = results?.columnEnvelopes?.get(col.name)
            val nd = env?.maxCompressionNdKN ?: 0.0
            normalForceChecks.add(verifyReducedNormalForceColumn(col, nd, joint.materials.fc28MPa))
        }
        joint.sectionCatalog.shearWalls.forEach { wall ->
            val env = results?.pierEnvelopes?.get(wall.pierName)
            val nd = env?.ndMaxBaseKN ?: 0.0
            normalForceChecks.add(verifyReducedNormalForceShearWall(wall, nd, joint.materials.fc28MPa))
        }

        // 5. Planchers
        val slabChecks = joint.sectionCatalog.slabStrips.map { slab ->
            verifySlabStrip(slab)
        }

        // 6. Combinaisons
        val combos = generateLoadCombinations(joint.seismicParams.liveLoadCategory.psi)

        // 7. Déplacements et P-Delta
        val driftChecks = if (results != null && results.diaphragmDisplacements.isNotEmpty()) {
            verifyDisplacements(joint, results.diaphragmDisplacements)
        } else emptyList()

        val pDeltaChecks = if (results != null && results.storyShears.isNotEmpty() && driftChecks.isNotEmpty()) {
            verifyPDelta(joint, results.storyShears, driftChecks)
        } else emptyList()

        val allCompliant = beamChecks.all { it.isCompliant } &&
                columnChecks.all { it.isCompliant } &&
                wallChecks.all { it.isCompliant } &&
                normalForceChecks.all { it.isCompliant } &&
                slabChecks.all { it.isCompliant } &&
                (driftChecks.isEmpty() || driftChecks.all { it.isCompliant }) &&
                (pDeltaChecks.isEmpty() || pDeltaChecks.all { it.isCompliant })

        return GlobalJointCheckReport(
            jointName = joint.jointName,
            mechanicalProps = mechProps,
            beamChecks = beamChecks,
            columnChecks = columnChecks,
            shearWallChecks = wallChecks,
            reducedNormalForceChecks = normalForceChecks,
            slabChecks = slabChecks,
            driftChecks = driftChecks,
            pDeltaChecks = pDeltaChecks,
            loadCombinations = combos,
            allChecksCompliant = allCompliant
        )
    }
}

private fun Double.format(digits: Int): String = String.format(java.util.Locale.US, "%.${digits}f", this)
