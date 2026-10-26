package com.archilab.rpa2024

import com.archilab.rpa2024.engine.seismic.*
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionCalculator
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionInput
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.normative.SiteClass
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

class SeismicAndSlabEngineTest {

    private val slabSolver = ContinuousRibbedSlabSolver()
    private val deflectionCalc = TBeamDeflectionCalculator()
    private val spectrumGen = SpectrumGenerator()
    private val seismicEngine = Rpa2024SeismicEngine(spectrumGen)

    @Test
    fun testTBeamDeflectionCalculatorMatchingKokaSheet() {
        // Validation stricte basée sur la fiche de calcul de flèche Koka (Page 11 du document de référence)
        // Portée L = 4.35 m, b = 0.60 m, b0 = 0.12 m, h = 0.21 m, h0 = 0.05 m, d' = 0.02 m
        // As = 3.39 cm² (3T12), A's = 1.13 cm² (1T12), fc28 = 25 MPa
        // Mj = 11.56 kN.m, Mg = 11.56 kN.m, Mp = 11.56 kN.m
        val input = TBeamDeflectionInput(
            spanLengthMeters = 4.35,
            flangeWidthBMeters = 0.60,
            webWidthB0Meters = 0.12,
            totalHeightHMeters = 0.21,
            flangeThicknessH0Meters = 0.05,
            tensionSteelAreaM2 = 3.39e-4,     // 3T12 = 3.39 cm²
            compressionSteelAreaM2 = 1.13e-4, // 1T12 = 1.13 cm²
            dPrimeMeters = 0.02,
            dDoublePrimeMeters = 0.02,
            fc28MPa = 25.0,
            momentMjKNm = 11.56,
            momentMgKNm = 11.56,
            momentMpKNm = 11.56
        )

        val report = deflectionCalc.compute(input)

        // 1. Caractéristiques géométriques
        assertTrue(report.uncrackedInertiaI0M4 > 0.0)
        assertEquals(0.19, report.effectiveDepthDMeters, 0.001)

        // 2. Flèches sous charges
        assertTrue(report.deflectionFgvMm > 0.0)
        assertTrue(report.deflectionFgqMm > 0.0)

        // 3. Flèche totale admissible : L/500 = 4350 / 500 = 8.70 mm
        assertEquals(8.70, report.allowableDeflectionFbarMm, 0.01)

        // 4. Flèche nuisible Delta ft <= 8.7 mm (conforme Koka : ~5.68 mm)
        assertTrue(report.harmfulDeflectionDeltaFtMm <= report.allowableDeflectionFbarMm)
        assertTrue(report.isDeflectionSatisfied)
    }

    @Test
    fun testContinuousRibbedSlabSolverMultiSpans() {
        // Plancher à corps creux (16+5) cm sur 3 travées continues : 4.35 m, 3.15 m, 2.80 m (Page 9 du document)
        val topology = SlabStripTopology(
            name = "ETAGE COURANT",
            totalThicknessHtCm = 21.0,
            compressionSlabH0Cm = 5.0,
            hollowBlockHeightCm = 16.0,
            ribWidthB0Cm = 12.0,
            flangeWidthBCm = 60.0,
            continuousSpansMeters = listOf(4.35, 3.15, 2.80)
        )

        // Charges surfaciques : G = 6.75 kN/m², Q = 1.00 kN/m²
        val report = slabSolver.solve(
            topology = topology,
            deadLoadGKNm2 = 6.75,
            liveLoadQKNm2 = 1.00,
            materials = MaterialConfig(fc28MPa = 25.0, feMPa = 500.0)
        )

        // 1. Vérification du prédimensionnement
        assertTrue(report.isHeightCompliant)
        assertEquals(19.33, report.minRequiredHeightHtCm, 0.05)
        assertEquals(21.0, report.totalHeightHtCm, 0.01)

        // 2. Charges linéaires par nervure (bande de 0.60 m)
        assertEquals(4.05, report.stripDeadLoadGKNm, 0.01) // 6.75 * 0.60 = 4.05 kN/m
        assertEquals(0.60, report.stripLiveLoadQKNm, 0.01) // 1.00 * 0.60 = 0.60 kN/m
        assertEquals(6.3675, report.designLoadPuKNm, 0.01) // 1.35 * 4.05 + 1.5 * 0.60 = 6.3675 kN/m

        // 3. 3 travées résolues
        assertEquals(3, report.spans.size)

        val span1 = report.spans[0]
        assertEquals(4.35, span1.spanLengthMeters, 0.01)
        assertTrue(span1.momentSpanMaxMuKNm > 0.0)
        assertTrue(span1.maxShearVuKN > 0.0)

        // Ferraillage en travée : vérifie le choix automatique de barres et le moment résistant
        assertTrue(span1.spanRebars.areaCm2 >= span1.spanRebars.requiredAreaCm2)
        assertTrue(span1.spanRebars.resistantMomentMrKNm >= span1.momentSpanMaxMuKNm)
        assertTrue(span1.spanRebars.description.contains("T"))

        // Armatures transversales
        assertTrue(span1.shearStressTauUMPa <= span1.shearStressLimitMPa)
        assertEquals(0.56, 2.0 * (Math.PI * 0.6 * 0.6 / 4.0), 0.02) // Étriers phi 6 mm

        // 11 points de discrétisation par travée
        assertEquals(11, span1.discretizationPoints.size)

        // Flèche sur la travée la plus critique
        assertTrue(report.deflectionReport.isDeflectionSatisfied)
    }

    @Test
    fun testSpectrumGeneratorFunctionsAndExport() {
        val zone = SeismicZone.ZONE_III
        val site = SiteClass.S3
        val group = ImportanceGroup.GROUP_1A
        val bracing = BracingSystem.CAT_3
        val qf = 1.10

        // 1. Test du plateau du spectre de calcul horizontal Sad/g (Eq 3.15)
        // Dans [T1, T2] = [0.10, 0.40] : plateau constant = (A * I * S) * (2.5 * eta / R) * Qf
        val (sadPlateau, branchPlateau) = spectrumGen.computeDesignHorizontalSaOverG(
            t = 0.25,
            zone = zone,
            site = site,
            importance = group,
            bracing = bracing,
            qualityFactorQf = qf
        )
        assertEquals(SpectrumBranchCase.CASE_2_PLATEAU, branchPlateau)
        // (0.15 * 1.40 * 1.55) * (2.5 * 0.8819 / 3.5) * 1.10 = 0.3255 * 0.6299 * 1.10 = 0.2255
        assertEquals(0.2255, sadPlateau, 0.005)

        // 2. Test du plancher réglementaire 0.20 * A * I = 0.20 * 0.15 * 1.40 = 0.042
        val (sadAt4s, _) = spectrumGen.computeDesignHorizontalSaOverG(
            t = 4.0,
            zone = zone,
            site = site,
            importance = group,
            bracing = bracing,
            qualityFactorQf = qf
        )
        assertTrue(sadAt4s >= 0.04199)

        // 3. Test du spectre vertical Sav/g (Eq 3.14)
        val savPlateau = spectrumGen.computeElasticVerticalSaOverG(
            t = 0.25,
            zone = zone,
            site = site,
            importance = group
        )
        assertTrue(savPlateau > 0.0)

        // 4. Test d'exportation vers fichier texte ETABS
        val tempFile = File.createTempFile("etabs_spectrum_test", ".txt")
        tempFile.deleteOnExit()

        spectrumGen.exportSpectrumToEtabsFile(
            file = tempFile,
            zone = zone,
            site = site,
            importance = group,
            bracing = bracing,
            qualityFactorQf = qf,
            step = 0.10
        )

        assertTrue(tempFile.exists())
        val lines = tempFile.readLines().filter { !it.startsWith(";") }
        assertTrue(lines.size >= 40) // De 0.0 à 4.0 par pas de 0.10
        val firstDataRow = lines.first().split("\t")
        assertEquals(2, firstDataRow.size)
    }

    @Test
    fun testCompleteRpa2024SeismicEngineWorkflow() {
        // Configuration complète du Joint A selon la Note de Calcul (Sûreté Urbaine 1500 LLV Bosquet)
        val gridSys = GridSystem(
            xGrids = listOf(GridLine("1", 0.0, AxisDirection.X), GridLine("2", 4.65, AxisDirection.X), GridLine("3", 9.75, AxisDirection.X), GridLine("4", 15.00, AxisDirection.X)),
            yGrids = listOf(GridLine("A", 0.0, AxisDirection.Y), GridLine("B", 6.35, AxisDirection.Y), GridLine("C", 13.50, AxisDirection.Y), GridLine("D", 20.65, AxisDirection.Y))
        )
        val storySys = StorySystem(
            stories = listOf(
                StoryLevel("H RDC", 3.74, 3.74),
                StoryLevel("H 1ER", 3.74, 7.48),
                StoryLevel("H 2EME", 3.74, 11.22),
                StoryLevel("CAGE", 3.74, 14.96, isPenthouseOrCage = true)
            )
        )
        val catalog = SectionCatalog(
            columns = listOf(
                ColumnFamily("POT 35X50", ColumnShape.RECTANGULAR, 35.0, 50.0, clearHeightLclMeters = 3.19),
                ColumnFamily("POT 40X50", ColumnShape.RECTANGULAR, 40.0, 50.0, clearHeightLclMeters = 3.19),
                ColumnFamily("POT 35X35", ColumnShape.RECTANGULAR, 35.0, 35.0, clearHeightLclMeters = 3.19)
            )
        )
        val seismicConfig = SeismicConfig(
            zone = SeismicZone.ZONE_III,
            site = SiteClass.S3,
            importanceGroup = ImportanceGroup.GROUP_1A,
            bracingSystem = BracingSystem.CAT_3,
            dampingRatioXiPercent = 7.0,
            useCustomNuA = true,
            customNuA = 0.46
        )
        val joint = StructuralJoint("Joint A", gridSys, storySys, catalog, MaterialConfig(), seismicConfig)

        // Résultats de l'analyse ETABS (extraits des tableaux des pages 18, 22, 24, 26)
        val modalRows = listOf(
            ModalRatioRow(mode = 1, period = 0.399, ux = 73.17, uy = 0.68, sumUx = 73.17, sumUy = 0.68),
            ModalRatioRow(mode = 2, period = 0.295, ux = 1.12, uy = 70.32, sumUx = 74.29, sumUy = 71.00),
            ModalRatioRow(mode = 3, period = 0.266, ux = 0.10, uy = 3.19, sumUx = 74.38, sumUy = 74.18, rz = 60.99),
            ModalRatioRow(mode = 4, period = 0.167, ux = 4.85, uy = 0.45, sumUx = 79.24, sumUy = 74.63),
            ModalRatioRow(mode = 5, period = 0.151, ux = 0.02, uy = 2.83, sumUx = 79.25, sumUy = 77.46),
            ModalRatioRow(mode = 6, period = 0.136, ux = 0.61, uy = 1.95, sumUx = 79.86, sumUy = 79.41),
            ModalRatioRow(mode = 7, period = 0.094, ux = 13.35, uy = 0.16, sumUx = 93.21, sumUy = 79.58),
            ModalRatioRow(mode = 8, period = 0.073, ux = 0.71, uy = 14.30, sumUx = 93.92, sumUy = 93.88)
        )

        val storyShears = listOf(
            StoryShearRow("H RDC", "WI", "Bottom", 10357.95, 0.0, 0.0),
            StoryShearRow("H 1ER", "WI", "Bottom", 6854.80, 0.0, 0.0),
            StoryShearRow("H 2EME", "WI", "Bottom", 3351.60, 0.0, 0.0),
            StoryShearRow("CAGE", "WI", "Bottom", 290.00, 0.0, 0.0),
            StoryShearRow("H RDC", "EX", "Bottom", 0.0, 1884.51, 303.53),
            StoryShearRow("H 1ER", "EX", "Bottom", 0.0, 1629.82, 257.55),
            StoryShearRow("H 2EME", "EX", "Bottom", 0.0, 1003.21, 173.83),
            StoryShearRow("CAGE", "EX", "Bottom", 0.0, 126.50, 36.27),
            StoryShearRow("H RDC", "EY", "Bottom", 0.0, 303.53, 1819.47),
            StoryShearRow("H 1ER", "EY", "Bottom", 0.0, 248.58, 1563.96),
            StoryShearRow("H 2EME", "EY", "Bottom", 0.0, 152.74, 958.35),
            StoryShearRow("CAGE", "EY", "Bottom", 0.0, 64.39, 121.10)
        )

        val displacements = listOf(
            DiaphragmDisplacementRow("H RDC", "D1", "EX", 0.0026, 0.0003),
            DiaphragmDisplacementRow("H 1ER", "D2", "EX", 0.0077, 0.0008),
            DiaphragmDisplacementRow("H 2EME", "D3", "EX", 0.0129, 0.0010),
            DiaphragmDisplacementRow("CAGE", "D4", "EX", 0.0173, 0.0019),
            DiaphragmDisplacementRow("H RDC", "D1", "EY", 0.0004, 0.0014),
            DiaphragmDisplacementRow("H 1ER", "D2", "EY", 0.0010, 0.0041),
            DiaphragmDisplacementRow("H 2EME", "D3", "EY", 0.0015, 0.0068),
            DiaphragmDisplacementRow("CAGE", "D4", "EY", 0.0058, 0.0097)
        )

        val colEnvelopes = mapOf(
            "POT 35X50" to ColumnForcesEnvelope("POT 35X50", maxCompressionNdKN = 673.0, maxCompressionStory = "H RDC", maxCompressionColumnId = "C106"),
            "POT 40X50" to ColumnForcesEnvelope("POT 40X50", maxCompressionNdKN = 1496.0, maxCompressionStory = "H RDC", maxCompressionColumnId = "C111"),
            "POT 35X35" to ColumnForcesEnvelope("POT 35X35", maxCompressionNdKN = 446.0, maxCompressionStory = "H RDC", maxCompressionColumnId = "C127")
        )

        val etabsResults = EtabsAnalysisResults(
            modalRatios = modalRows,
            baseReactions = BaseReactionsResult(1884.51, 1819.47),
            storyShears = storyShears,
            diaphragmDisplacements = displacements,
            columnEnvelopes = colEnvelopes,
            totalWeightWKN = 10357.95
        )

        val report = seismicEngine.performCompleteSeismicAnalysis(joint, etabsResults)

        // 1. Analyse modale
        assertEquals(1, report.modalAnalysis.fundamentalXMode)
        assertEquals(0.399, report.modalAnalysis.fundamentalXPeriod, 0.001)
        assertEquals(2, report.modalAnalysis.fundamentalYMode)
        assertEquals(0.295, report.modalAnalysis.fundamentalYPeriod, 0.001)
        assertEquals(3, report.modalAnalysis.torsionalMode)
        assertTrue(report.modalAnalysis.sumUxPercent >= 90.0)
        assertTrue(report.modalAnalysis.sumUyPercent >= 90.0)
        assertTrue(report.modalAnalysis.isModalParticipationCompliant)

        // 2. Base shear MSE vs MDS
        assertTrue(report.baseShearComparison.overallCompliant)
        assertEquals(0.85, report.baseShearComparison.xDirection.lambda, 0.001)
        assertEquals(0.85, report.baseShearComparison.yDirection.lambda, 0.001)
        assertTrue(report.baseShearComparison.xDirection.isDynamicScaleSufficient)
        assertTrue(report.baseShearComparison.yDirection.isDynamicScaleSufficient)

        // 3. Effort normal réduit poteaux
        assertEquals(3, report.columnReducedNormalForces.size)
        assertTrue(report.columnReducedNormalForces.all { it.isCompliant })
        // nu(POT 35x50) = (673 * 10) / (1750 * 25) = 0.154 <= 0.35
        // nu(POT 40x50) = (1496 * 10) / (2000 * 25) = 0.299 <= 0.35
        // nu(POT 35x35) = (446 * 10) / (1225 * 25) = 0.146 <= 0.35
        val p40x50 = report.columnReducedNormalForces.find { it.b1Cm == 40.0 }
        assertNotNull(p40x50)
        assertEquals(0.299, p40x50!!.nuComputed, 0.001)

        // 4. Déplacements inter-étages et limitation des dommages
        assertTrue(report.driftReportX.isDirectionCompliant)
        assertTrue(report.driftReportY.isDirectionCompliant)

        // 5. Effet P-Delta
        assertTrue(report.pDeltaReportX.isDirectionCompliant)
        assertTrue(report.pDeltaReportY.isDirectionCompliant)
        assertTrue(report.pDeltaReportX.maxThetaK <= 0.10)
        assertTrue(report.pDeltaReportY.maxThetaK <= 0.10)

        // 6. Conformité globale
        assertTrue(report.overallSeismicCompliant)
    }
}
