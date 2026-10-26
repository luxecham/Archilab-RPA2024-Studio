package com.archilab.rpa2024.engine.slabs

import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.model.StructuralJoint
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Point de discrétisation le long d'une travée de poutrelle (format table Koka de SOCTEC).
 */
@Serializable
data class SlabStationPoint(
    val xMeters: Double,
    val muKNm: Double,
    val mSerKNm: Double,
    val vuKN: Double,
    val sigmaBcMPa: Double,
    val sigmaStMPa: Double
)

/**
 * Choix de barres d'armatures commerciales sélectionnées avec section réelle et moment résistant.
 */
@Serializable
data class RebarSelection(
    val description: String,      // Ex: "3T12", "1T12 + 1T12chp"
    val areaCm2: Double,          // Section réelle adoptée en cm²
    val requiredAreaCm2: Double,  // Section théorique calculée en cm²
    val resistantMomentMrKNm: Double, // Moment résistant ultime Mr en kN.m
    val anchoringLengthCm: Double // Longueur d'ancrage recommandée
)

/**
 * Résultats complets de ferraillage et sollicitations pour une travée donnée.
 */
@Serializable
data class SpanDesignResult(
    val spanIndex: Int,
    val spanLengthMeters: Double,
    val momentSpanMaxMuKNm: Double,
    val momentSpanMaxMserKNm: Double,
    val momentLeftSupportMuKNm: Double,
    val momentRightSupportMuKNm: Double,
    val maxShearVuKN: Double,
    val spanRebars: RebarSelection,
    val leftSupportRebars: RebarSelection,
    val rightSupportRebars: RebarSelection,
    val transverseStirrupsDescription: String,
    val stirrupSpacingCm: Double,
    val shearStressTauUMPa: Double,
    val shearStressLimitMPa: Double,
    val discretizationPoints: List<SlabStationPoint>
)

/**
 * Rapport complet du solveur de plancher à corps creux.
 */
@Serializable
data class RibbedSlabSolutionReport(
    val slabName: String,
    val totalHeightHtCm: Double,
    val compressionSlabH0Cm: Double,
    val ribWidthB0Cm: Double,
    val flangeWidthBCm: Double,
    val maxSpanLengthMeters: Double,
    val minRequiredHeightHtCm: Double,
    val isHeightCompliant: Boolean,
    val uniformDeadLoadGKNm2: Double,
    val uniformLiveLoadQKNm2: Double,
    val stripDeadLoadGKNm: Double,
    val stripLiveLoadQKNm: Double,
    val designLoadPuKNm: Double,
    val serviceLoadPserKNm: Double,
    val spans: List<SpanDesignResult>,
    val deflectionReport: TBeamDeflectionReport
)

/**
 * Solveur complet de poutrelles continues à section en Té.
 * Remplace nativement le logiciel SOCOTEC Koka pour n'importe quelle configuration
 * de travées continues extraites du plancher ETABS.
 */
class ContinuousRibbedSlabSolver(
    private val deflectionCalculator: TBeamDeflectionCalculator = TBeamDeflectionCalculator()
) {

    /**
     * Résout un plancher courant typique d'un Joint/Bloc structural.
     */
    fun solveTypicalFloorSlab(joint: StructuralJoint): RibbedSlabSolutionReport {
        val spans = joint.sectionCatalog.slabStrips.firstOrNull()?.continuousSpansMeters
            ?.takeIf { it.isNotEmpty() }
            ?: joint.gridSystem.spansX.ifEmpty { listOf(4.20, 3.80, 4.50) }

        val topology = joint.sectionCatalog.slabStrips.firstOrNull() ?: SlabStripTopology(
            name = "PL_CORPS_CREUX_16+5",
            totalThicknessHtCm = 21.0,
            compressionSlabH0Cm = 5.0,
            hollowBlockHeightCm = 16.0,
            ribWidthB0Cm = 12.0,
            flangeWidthBCm = 65.0,
            continuousSpansMeters = spans
        )

        return solve(
            topology = topology,
            deadLoadGKNm2 = 6.20,
            liveLoadQKNm2 = 1.50,
            materials = joint.materials
        )
    }

    /**
     * Résout intégralement la poutrelle continue sous charges permanentes G et d'exploitation Q.
     */
    fun solve(
        topology: SlabStripTopology,
        deadLoadGKNm2: Double,
        liveLoadQKNm2: Double,
        materials: MaterialConfig = MaterialConfig(),
        cantileverLeftLengthMeters: Double = 0.0,
        cantileverRightLengthMeters: Double = 0.0
    ): RibbedSlabSolutionReport {
        val bMeters = topology.flangeWidthBCm / 100.0
        val b0Meters = topology.ribWidthB0Cm / 100.0
        val htMeters = topology.totalThicknessHtCm / 100.0
        val h0Meters = topology.compressionSlabH0Cm / 100.0
        val dMeters = htMeters - 0.02 // Enrobage 2 cm

        val spans = topology.continuousSpansMeters
        require(spans.isNotEmpty()) { "Le plancher doit comporter au moins une travée." }

        // 1. Charges par bande de largeur b
        val gLin = deadLoadGKNm2 * bMeters
        val qLin = liveLoadQKNm2 * bMeters
        val puLin = 1.35 * gLin + 1.50 * qLin
        val pserLin = gLin + qLin

        // 2. Vérification de la hauteur minimale ht >= Lmax / 22.5
        val maxSpan = spans.maxOrNull() ?: 0.0
        val minHtCm = (maxSpan * 100.0) / 22.5
        val isHtCompliant = topology.totalThicknessHtCm >= minHtCm

        // 3. Calcul hyperstatique des moments d'appuis (Méthode des Trois Moments / Clapeyron)
        val supportMomentsMu = solveContinuousMoments(spans, puLin, cantileverLeftLengthMeters, cantileverRightLengthMeters)
        val supportMomentsMser = solveContinuousMoments(spans, pserLin, cantileverLeftLengthMeters, cantileverRightLengthMeters)

        // Résistance de calcul des matériaux
        val fc28 = materials.fc28MPa
        val ft28 = 0.6 + 0.06 * fc28
        val fe = materials.feMPa
        val theta = 0.85
        val gammaB = 1.50
        val gammaS = 1.15
        val fbu = (0.85 * fc28) / (theta * gammaB)
        val fsu = fe / gammaS

        val spanResults = mutableListOf<SpanDesignResult>()

        for (i in spans.indices) {
            val l = spans[i]
            val mwMu = supportMomentsMu[i]
            val meMu = supportMomentsMu[i + 1]
            val mwMser = supportMomentsMser[i]
            val meMser = supportMomentsMser[i + 1]

            // Moment maximal en travée sous ELU
            // x_max = L/2 + (Me - Mw)/(p*L)
            val xMax = max(0.0, min(l, l / 2.0 + (meMu - mwMu) / (puLin * l)))
            val isoMax = (puLin * l * l) / 8.0
            val rawSpanMu = (puLin * xMax * (l - xMax)) / 2.0 + mwMu * (1.0 - xMax / l) + meMu * (xMax / l)
            // Majoration forfaitaire BAEL si nécessaire : Mt >= 1.05 * M0 - (Mw + Me)/2
            val minBaelMt = max(0.50 * isoMax, 1.05 * isoMax - abs((mwMu + meMu) / 2.0))
            val maxSpanMu = max(rawSpanMu, minBaelMt)

            val rawSpanMser = (pserLin * xMax * (l - xMax)) / 2.0 + mwMser * (1.0 - xMax / l) + meMser * (xMax / l)
            val maxSpanMser = max(0.0, rawSpanMser)

            // Efforts tranchants aux appuis
            val vuLeft = (puLin * l) / 2.0 + (mwMu - meMu) / l
            val vuRight = -(puLin * l) / 2.0 + (mwMu - meMu) / l
            val maxVu = max(abs(vuLeft), abs(vuRight))

            // Ferraillage en travée (Section en Té)
            val spanRebar = designTBeamSpanRebar(
                muKNm = maxSpanMu,
                bMeters = bMeters,
                b0Meters = b0Meters,
                hMeters = htMeters,
                h0Meters = h0Meters,
                dMeters = dMeters,
                fbuMPa = fbu,
                fsuMPa = fsu,
                feMPa = fe,
                ft28MPa = ft28
            )

            // Ferraillage sur appui gauche et droit (Section rectangulaire b0 x h)
            val leftRebar = designSupportRebar(abs(mwMu), b0Meters, htMeters, dMeters, fbu, fsu, fe, ft28)
            val rightRebar = designSupportRebar(abs(meMu), b0Meters, htMeters, dMeters, fbu, fsu, fe, ft28)

            // Armatures transversales
            val tauU = (maxVu * 1000.0) / (b0Meters * dMeters * 1e6) // MPa
            val tauULimit = min(0.20 * fc28 / gammaB, 5.0)
            val phiT = min(6.0, (htMeters * 1000.0) / 35.0)
            val atCm2 = 2.0 * (PI * (phiT / 10.0) * (phiT / 10.0) / 4.0)
            val spacingStCm = min(20.0, min(0.9 * dMeters * 100.0, 40.0))

            // Discrétisation de la travée en 11 points (0 à L)
            val discPoints = mutableListOf<SlabStationPoint>()
            val steps = 10
            for (s in 0..steps) {
                val x = (s.toDouble() / steps) * l
                val muX = (puLin * x * (l - x)) / 2.0 + mwMu * (1.0 - x / l) + meMu * (x / l)
                val mserX = (pserLin * x * (l - x)) / 2.0 + mwMser * (1.0 - x / l) + meMser * (x / l)
                val vuX = (puLin * (l / 2.0 - x)) + (mwMu - meMu) / l

                // Contraintes élastiques sous ELS
                val (sbc, sst) = evaluateElasticStresses(
                    momentMserKNm = mserX,
                    bMeters = bMeters,
                    b0Meters = b0Meters,
                    hMeters = htMeters,
                    h0Meters = h0Meters,
                    dMeters = dMeters,
                    asSteelM2 = spanRebar.areaCm2 / 10000.0
                )

                discPoints.add(
                    SlabStationPoint(
                        xMeters = x,
                        muKNm = muX,
                        mSerKNm = mserX,
                        vuKN = vuX,
                        sigmaBcMPa = sbc,
                        sigmaStMPa = sst
                    )
                )
            }

            spanResults.add(
                SpanDesignResult(
                    spanIndex = i + 1,
                    spanLengthMeters = l,
                    momentSpanMaxMuKNm = maxSpanMu,
                    momentSpanMaxMserKNm = maxSpanMser,
                    momentLeftSupportMuKNm = mwMu,
                    momentRightSupportMuKNm = meMu,
                    maxShearVuKN = maxVu,
                    spanRebars = spanRebar,
                    leftSupportRebars = leftRebar,
                    rightSupportRebars = rightRebar,
                    transverseStirrupsDescription = "Étriers T${phiT.toInt()} (At = ${atCm2.format(2)} cm²)",
                    stirrupSpacingCm = spacingStCm,
                    shearStressTauUMPa = tauU,
                    shearStressLimitMPa = tauULimit,
                    discretizationPoints = discPoints
                )
            )
        }

        // 4. Calcul de flèche BAEL 91 sur la travée la plus longue
        val maxSpanIdx = spans.indices.maxByOrNull { spans[it] } ?: 0
        val longestSpan = spans[maxSpanIdx]
        val longestSpanRes = spanResults[maxSpanIdx]

        val deflectionInput = TBeamDeflectionInput(
            spanLengthMeters = longestSpan,
            flangeWidthBMeters = bMeters,
            webWidthB0Meters = b0Meters,
            totalHeightHMeters = htMeters,
            flangeThicknessH0Meters = h0Meters,
            tensionSteelAreaM2 = longestSpanRes.spanRebars.areaCm2 / 10000.0,
            compressionSteelAreaM2 = 1.13e-4, // 1T12 compression
            fc28MPa = fc28,
            momentMjKNm = (gLin * longestSpan * longestSpan) / 8.0 * 0.70, // Moment avant cloisons
            momentMgKNm = (gLin * longestSpan * longestSpan) / 8.0 * 0.85, // Moment permanent total
            momentMpKNm = longestSpanRes.momentSpanMaxMserKNm.coerceAtLeast(0.1) // Moment total G+Q
        )
        val deflectionReport = deflectionCalculator.compute(deflectionInput)

        return RibbedSlabSolutionReport(
            slabName = topology.name,
            totalHeightHtCm = topology.totalThicknessHtCm,
            compressionSlabH0Cm = topology.compressionSlabH0Cm,
            ribWidthB0Cm = topology.ribWidthB0Cm,
            flangeWidthBCm = topology.flangeWidthBCm,
            maxSpanLengthMeters = maxSpan,
            minRequiredHeightHtCm = minHtCm,
            isHeightCompliant = isHtCompliant,
            uniformDeadLoadGKNm2 = deadLoadGKNm2,
            uniformLiveLoadQKNm2 = liveLoadQKNm2,
            stripDeadLoadGKNm = gLin,
            stripLiveLoadQKNm = qLin,
            designLoadPuKNm = puLin,
            serviceLoadPserKNm = pserLin,
            spans = spanResults,
            deflectionReport = deflectionReport
        )
    }

    /**
     * Résolution du système tridiagonal des Trois Moments (Équation de Clapeyron).
     */
    private fun solveContinuousMoments(
        spans: List<Double>,
        pLin: Double,
        cantileverLeft: Double = 0.0,
        cantileverRight: Double = 0.0
    ): DoubleArray {
        val numSupports = spans.size + 1
        val m = DoubleArray(numSupports)

        // Moments aux extrémités
        m[0] = if (cantileverLeft > 0.0) -(pLin * cantileverLeft * cantileverLeft) / 2.0 else 0.0
        m[numSupports - 1] = if (cantileverRight > 0.0) -(pLin * cantileverRight * cantileverRight) / 2.0 else 0.0

        if (spans.size == 1) {
            // Poutre sur 2 appuis simples
            return m
        }

        val numInterior = spans.size - 1
        val a = DoubleArray(numInterior) // sous-diagonale
        val b = DoubleArray(numInterior) // diagonale principale
        val c = DoubleArray(numInterior) // sur-diagonale
        val d = DoubleArray(numInterior) // second membre

        for (i in 0 until numInterior) {
            val l1 = spans[i]
            val l2 = spans[i + 1]

            a[i] = l1
            b[i] = 2.0 * (l1 + l2)
            c[i] = l2
            d[i] = -(pLin * l1 * l1 * l1 + pLin * l2 * l2 * l2) / 4.0

            if (i == 0) d[i] -= l1 * m[0]
            if (i == numInterior - 1) d[i] -= l2 * m[numSupports - 1]
        }

        // Algorithme de Thomas (TDMA)
        val cPrime = DoubleArray(numInterior)
        val dPrime = DoubleArray(numInterior)

        cPrime[0] = c[0] / b[0]
        dPrime[0] = d[0] / b[0]

        for (i in 1 until numInterior) {
            val denom = b[i] - a[i] * cPrime[i - 1]
            cPrime[i] = if (i < numInterior - 1) c[i] / denom else 0.0
            dPrime[i] = (d[i] - a[i] * dPrime[i - 1]) / denom
        }

        val x = DoubleArray(numInterior)
        x[numInterior - 1] = dPrime[numInterior - 1]
        for (i in numInterior - 2 downTo 0) {
            x[i] = dPrime[i] - cPrime[i] * x[i + 1]
        }

        for (i in 0 until numInterior) {
            // Forfait BAEL : vérifie un minimum sur appui intermédiaire (-0.40 * M0 à -0.50 * M0)
            val l1 = spans[i]
            val l2 = spans[i + 1]
            val maxM0 = (pLin * max(l1, l2).pow(2)) / 8.0
            val minSupportM = -0.40 * maxM0
            m[i + 1] = min(x[i], minSupportM)
        }

        return m
    }

    /**
     * Dimensionnement des armatures longitudinales en travée pour section en Té.
     */
    private fun designTBeamSpanRebar(
        muKNm: Double,
        bMeters: Double,
        b0Meters: Double,
        hMeters: Double,
        h0Meters: Double,
        dMeters: Double,
        fbuMPa: Double,
        fsuMPa: Double,
        feMPa: Double,
        ft28MPa: Double
    ): RebarSelection {
        val muNm = max(muKNm, 0.1) * 1000.0
        val fbuPa = fbuMPa * 1e6
        val fsuPa = fsuMPa * 1e6

        // Moment équilibré par la table de compression seule
        val mtuNm = bMeters * h0Meters * fbuPa * (dMeters - h0Meters / 2.0)

        val asM2 = if (muNm <= mtuNm) {
            // L'axe neutre est dans la table -> Calcul comme une section rectangulaire b x h
            val muRed = muNm / (bMeters * dMeters * dMeters * fbuPa)
            val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muRed)))
            val z = dMeters * (1.0 - 0.4 * alpha)
            muNm / (z * fsuPa)
        } else {
            // L'axe neutre est dans la nervure -> Décomposition
            val muFlanges = (bMeters - b0Meters) * h0Meters * fbuPa * (dMeters - h0Meters / 2.0)
            val asFlanges = muFlanges / ((dMeters - h0Meters / 2.0) * fsuPa)

            val muWeb = muNm - muFlanges
            val muRedWeb = muWeb / (b0Meters * dMeters * dMeters * fbuPa)
            val alphaWeb = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * muRedWeb)))
            val zWeb = dMeters * (1.0 - 0.4 * alphaWeb)
            val asWeb = muWeb / (zWeb * fsuPa)

            asFlanges + asWeb
        }

        // Condition de non-fragilité BAEL
        val asMinM2 = 0.23 * b0Meters * dMeters * (ft28MPa / feMPa)
        val finalAsM2 = max(asM2, asMinM2)
        val finalAsCm2 = finalAsM2 * 10000.0

        // Sélection commerciale automatique
        val commercialBars = selectBestRebarPattern(finalAsCm2, isSpan = true)
        val actualAreaM2 = commercialBars.areaCm2 / 10000.0
        val zEst = 0.90 * dMeters
        val mrKNm = (actualAreaM2 * fsuPa * zEst) / 1000.0

        return commercialBars.copy(resistantMomentMrKNm = mrKNm)
    }

    /**
     * Dimensionnement des armatures longitudinales sur appui (section rectangulaire b0 x h).
     */
    private fun designSupportRebar(
        muKNm: Double,
        b0Meters: Double,
        hMeters: Double,
        dMeters: Double,
        fbuMPa: Double,
        fsuMPa: Double,
        feMPa: Double,
        ft28MPa: Double
    ): RebarSelection {
        val muNm = max(muKNm, 0.1) * 1000.0
        val fbuPa = fbuMPa * 1e6
        val fsuPa = fsuMPa * 1e6

        val muRed = muNm / (b0Meters * dMeters * dMeters * fbuPa)
        val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * min(0.35, muRed))))
        val z = dMeters * (1.0 - 0.4 * alpha)
        val asM2 = muNm / (z * fsuPa)

        val asMinM2 = 0.23 * b0Meters * dMeters * (ft28MPa / feMPa)
        val finalAsCm2 = max(asM2, asMinM2) * 10000.0

        val commercialBars = selectBestRebarPattern(finalAsCm2, isSpan = false)
        val actualAreaM2 = commercialBars.areaCm2 / 10000.0
        val mrKNm = (actualAreaM2 * fsuPa * z) / 1000.0

        return commercialBars.copy(resistantMomentMrKNm = mrKNm)
    }

    /**
     * Catalogue de sélection des armatures commerciales standard pour poutrelles.
     */
    private fun selectBestRebarPattern(requiredCm2: Double, isSpan: Boolean): RebarSelection {
        // Barres usuelles pour poutrelles : T10, T12, T14
        val patterns = if (isSpan) {
            listOf(
                Pair("3T10", 3.0 * (PI * 1.0 * 1.0 / 4.0)), // 2.36 cm²
                Pair("3T12", 3.0 * (PI * 1.2 * 1.2 / 4.0)), // 3.39 cm²
                Pair("3T14", 3.0 * (PI * 1.4 * 1.4 / 4.0)), // 4.62 cm²
                Pair("2T14 + 1T16", 2.0 * 1.54 + 2.01),     // 5.09 cm²
                Pair("3T16", 3.0 * (PI * 1.6 * 1.6 / 4.0))  // 6.03 cm²
            )
        } else {
            listOf(
                Pair("1T10 + 1T10chp", 2.0 * (PI * 1.0 * 1.0 / 4.0)), // 1.57 cm²
                Pair("1T12 + 1T12chp", 2.0 * (PI * 1.2 * 1.2 / 4.0)), // 2.26 cm²
                Pair("1T14 + 1T14chp", 2.0 * (PI * 1.4 * 1.4 / 4.0)), // 3.08 cm²
                Pair("2T12 + 1T14chp", 2.0 * 1.13 + 1.54),            // 3.80 cm²
                Pair("2T14 + 2T12chp", 2.0 * 1.54 + 2.0 * 1.13)       // 5.34 cm²
            )
        }

        val chosen = patterns.firstOrNull { it.second >= requiredCm2 } ?: patterns.last()
        val anchorLengthCm = if (chosen.first.contains("14")) 50.0 else 40.0

        return RebarSelection(
            description = chosen.first,
            areaCm2 = chosen.second,
            requiredAreaCm2 = requiredCm2,
            resistantMomentMrKNm = 0.0,
            anchoringLengthCm = anchorLengthCm
        )
    }

    private fun evaluateElasticStresses(
        momentMserKNm: Double,
        bMeters: Double,
        b0Meters: Double,
        hMeters: Double,
        h0Meters: Double,
        dMeters: Double,
        asSteelM2: Double
    ): Pair<Double, Double> {
        if (momentMserKNm <= 0.0 || asSteelM2 <= 0.0) return Pair(0.0, 0.0)
        val n = 15.0

        val a1 = 0.5 * bMeters
        val b1 = n * asSteelM2
        val c1 = -(n * asSteelM2 * dMeters)
        val delta1 = b1 * b1 - 4.0 * a1 * c1
        val y1Candidate = (-b1 + sqrt(max(0.0, delta1))) / (2.0 * a1)

        val y1 = if (y1Candidate <= h0Meters) y1Candidate else {
            val a2 = 0.5 * b0Meters
            val b2 = (bMeters - b0Meters) * h0Meters + n * asSteelM2
            val c2 = -((bMeters - b0Meters) * h0Meters * (h0Meters / 2.0) + n * asSteelM2 * dMeters)
            val delta2 = b2 * b2 - 4.0 * a2 * c2
            (-b2 + sqrt(max(0.0, delta2))) / (2.0 * a2)
        }

        val iCr = if (y1 <= h0Meters) {
            (bMeters * y1.pow(3)) / 3.0 + n * asSteelM2 * (dMeters - y1).pow(2)
        } else {
            (b0Meters * y1.pow(3)) / 3.0 +
                    (bMeters - b0Meters) * h0Meters * (h0Meters.pow(2) / 12.0 + (y1 - h0Meters / 2.0).pow(2)) +
                    n * asSteelM2 * (dMeters - y1).pow(2)
        }

        val momNm = momentMserKNm * 1000.0
        val sbcMPa = ((momNm * y1) / iCr) / 1e6
        val sstMPa = (n * (momNm * (dMeters - y1)) / iCr) / 1e6

        return Pair(max(0.0, sbcMPa), max(0.0, sstMPa))
    }
}

private fun Double.format(digits: Int): String = String.format(java.util.Locale.US, "%.${digits}f", this)
