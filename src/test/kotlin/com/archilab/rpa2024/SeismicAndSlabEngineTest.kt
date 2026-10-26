package com.archilab.rpa2024

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.seismic.PDeltaClassification
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.seismic.SpectralBranch
import com.archilab.rpa2024.engine.seismic.SpectrumGenerator
import com.archilab.rpa2024.engine.seismic.SpectrumKind
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.ContinuousSlabInput
import com.archilab.rpa2024.engine.slabs.SlabBarZone
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.ModalRatioRow
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.pow

/**
 * Validation de bout en bout de la partie 2 :
 * - spectres RPA 2024 et export ETABS ;
 * - solveur de poutrelles continues en Té avec fiche de flèche BAEL ;
 * - moteur sismique global (participation modale, MSE/MDS, ν, déplacements, P-Delta).
 */
class SeismicAndSlabEngineTest {

    private val imported = SampleJointA.load()
    private val joint = imported.model.joint
    private val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)
    private val generator = SpectrumGenerator.from(resolved)

    // ---------------------------------------------------------------------------------
    // Spectres RPA 2024
    // ---------------------------------------------------------------------------------

    @Test
    fun `les spectres horizontaux respectent les equations 3-8 et 3-15`() {
        // A = 0,15 ; I = 1,40 ; S = 1,55 ; type 2 ; site S3 ; R = 3,5 ; QF = 1,10.
        val base = 0.15 * 1.40 * 1.55
        assertEquals(2.0 / 3.0 * base, generator.horizontalDesign(0.0), 1e-12)
        assertEquals(2.5 * 1.10 / 3.5 * base, generator.horizontalDesign(0.40), 1e-12)
        assertEquals(0.127875, generator.horizontalDesign(0.80), 1e-12)
        assertEquals(0.042, generator.horizontalDesign(2.0), 1e-12, "plancher 0,20.A.I")

        assertEquals(base, generator.horizontalElastic(0.0), 1e-12)
        assertEquals(base * 2.5 * generator.eta, generator.horizontalElastic(0.40), 1e-12)
        assertEquals(base * 2.5 * generator.eta * 0.5, generator.horizontalElastic(0.80), 1e-12)
        assertEquals(base * 2.5 * generator.eta * 0.48 / 4.0, generator.horizontalElastic(2.0), 1e-12)
        assertEquals(0.0, generator.horizontalElastic(4.20), 1e-12)

        assertEquals(SpectralBranch.CASE_1, generator.branchAt(SpectrumKind.HORIZONTAL_DESIGN, 0.05))
        assertEquals(SpectralBranch.CASE_2, generator.branchAt(SpectrumKind.HORIZONTAL_DESIGN, 0.20))
        assertEquals(SpectralBranch.CASE_3, generator.branchAt(SpectrumKind.HORIZONTAL_DESIGN, 0.80))
        assertEquals(SpectralBranch.CASE_4, generator.branchAt(SpectrumKind.HORIZONTAL_DESIGN, 2.0))
        assertEquals(SpectralBranch.OUT_OF_RANGE, generator.branchAt(SpectrumKind.HORIZONTAL_DESIGN, 4.5))
    }

    @Test
    fun `le spectre vertical applique les coefficients du tableau 3-8`() {
        val vertical = generator.verticalParameters
        assertEquals(0.55, vertical.avOverA, 1e-12)
        assertEquals(0.0825, vertical.av, 1e-12)
        assertEquals(0.05, vertical.t1Seconds, 1e-12)
        assertEquals(0.25, vertical.t2Seconds, 1e-12)
        assertEquals(1.0, vertical.t3Seconds, 1e-12)
        assertEquals(0.8, vertical.alpha, 1e-12)
        assertEquals("Tableau 3.8", vertical.reference)

        val a = 0.0825 * 1.40
        assertEquals(2.0 / 3.0 * a, generator.verticalDesign(0.0), 1e-12)
        assertEquals(2.5 / 3.5 * a, generator.verticalDesign(0.25), 1e-12)
        assertEquals(2.5 / 3.5 * a * (0.25 / 0.50).pow(0.8), generator.verticalDesign(0.50), 1e-12)
        assertEquals(0.0, generator.verticalElastic(4.10), 1e-12)
    }

    @Test
    fun `l'export ETABS produit deux fichiers de 201 points`() {
        val directory = Files.createTempDirectory("rpa2024-spectres")
        try {
            val export = generator.exportSpectrumToEtabsFile(directory, step = 0.02)
            assertEquals(201, export.horizontalPointCount)
            assertEquals(201, export.verticalPointCount)
            assertEquals(0.02, export.stepSeconds, 1e-12)
            assertTrue(Files.exists(export.horizontalPath))
            assertTrue(Files.exists(export.verticalPath))

            val horizontalLines = Files.readAllLines(export.horizontalPath)
            val verticalLines = Files.readAllLines(export.verticalPath)
            assertEquals(201, horizontalLines.size)
            assertEquals(201, verticalLines.size)

            val first = horizontalLines.first().split(',')
            assertEquals(0.0, first[0].trim().toDouble(), 1e-9)
            assertEquals(0.217, first[1].trim().toDouble(), 1e-6)

            val last = horizontalLines.last().split(',')
            assertEquals(4.0, last[0].trim().toDouble(), 1e-9)
            assertEquals(0.042, last[1].trim().toDouble(), 1e-6)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------
    // Poutrelles continues en Té
    // ---------------------------------------------------------------------------------

    @Test
    fun `la poutrelle continue a trois travees est entierement verifiee`() {
        val result = ContinuousRibbedSlabSolver().solve(
            ContinuousSlabInput(
                topology = ribbedTopology(),
                permanentLoadKNPerM2 = 6.75,
                variableLoadKNPerM2 = 1.00,
                // Cloisons posees apres l'ensemble des charges permanentes.
                permanentBeforeFinishesKNPerM2 = 6.75
            )
        )

        assertEquals(3, result.spanCount)
        assertEquals(4, result.supports.size)
        assertEquals(6.3675, result.loads.ultimateLineLoadKNPerM, 1e-9)
        assertEquals(4.65, result.loads.serviceLineLoadKNPerM, 1e-9)

        // Trois travées égales : M_appui = -u.L²/10 et M_travée centrale = u.L²/40.
        val ultimateMoment = result.loads.ultimateLineLoadKNPerM * 4.35 * 4.35
        assertEquals(-ultimateMoment / 10.0, result.supports[1].momentUltimateKNm, 1e-6)
        assertEquals(ultimateMoment / 40.0, result.spans[1].maxSpanMomentUltimateKNm, 1e-6)
        assertEquals(0.08 * ultimateMoment, result.spans[0].maxSpanMomentUltimateKNm, 1e-6)

        result.spans.forEach { span ->
            assertEquals(SlabBarZone.SPAN_BOTTOM, span.reinforcement.zone)
            assertTrue(
                span.reinforcement.resistingMomentKNm + 1e-6 >= span.maxSpanMomentUltimateKNm,
                "Mr < Mu pour la travée ${span.index + 1}"
            )
            assertEquals(CheckStatus.OK, span.reinforcement.status)
            assertEquals(CheckStatus.OK, span.serviceStress.status)
        }
        result.supports.forEach { support ->
            assertEquals(SlabBarZone.SUPPORT_TOP, support.reinforcement.zone)
            assertTrue(
                support.reinforcement.resistingMomentKNm + 1e-6 >= abs(support.momentUltimateKNm),
                "Mr < Mu pour l'appui ${support.index}"
            )
            assertEquals(CheckStatus.OK, support.reinforcement.status)
            assertEquals(CheckStatus.OK, support.serviceStress.status)
        }

        assertEquals("2T12", result.supports[1].reinforcement.bars.label)
        assertEquals("2T10", result.spans[0].reinforcement.bars.label)
        assertEquals("2T10", result.spans[1].reinforcement.bars.label)

        assertEquals(3, result.shearDesigns.size)
        result.shearDesigns.forEach { shear ->
            assertEquals(CheckStatus.OK, shear.status)
            assertEquals(6, shear.stirrupDiameterMm)
            assertTrue(shear.tauMPa <= shear.tauLimitMPa)
            assertTrue(shear.spacingMeters >= 0.05)
        }

        val deflection = result.deflection
        assertTrue(deflection.deltaFtMeters >= 0.0)
        assertTrue(
            deflection.deltaFtMeters <= deflection.allowableMeters + 1e-12,
            "Δft = ${deflection.deltaFtMeters} m > admissible ${deflection.allowableMeters} m"
        )
        assertEquals(4.35 / 500.0, deflection.allowableMeters, 1e-12)
        assertEquals(CheckStatus.OK, deflection.status)
        assertTrue(deflection.checks.any { it.code == "FLECHE_BRUYANTE" && it.status == CheckStatus.OK })

        assertEquals(CheckStatus.OK, result.predimensionCheck.status)
        assertEquals(CheckStatus.OK, result.status)
        assertTrue(result.cantilevers.isEmpty())

        val report = result.toChapterReport()
        assertEquals(8, report.number)
        assertEquals(CheckStatus.OK, report.status)
        assertEquals(3 + 4, report.elements.size)
        assertTrue(report.lines.any { it.contains("Flèche nuisible") })
    }

    @Test
    fun `une travee isolee reproduit le fonctionnement isostatique`() {
        val result = ContinuousRibbedSlabSolver().solve(
            ContinuousSlabInput(
                topology = ribbedTopology(spans = listOf(4.20)),
                permanentLoadKNPerM2 = 6.75,
                variableLoadKNPerM2 = 1.00,
                permanentBeforeFinishesKNPerM2 = 6.75
            )
        )

        assertEquals(1, result.spanCount)
        assertEquals(0.0, result.supports.first().momentUltimateKNm, 1e-9)
        assertEquals(0.0, result.supports.last().momentUltimateKNm, 1e-9)
        val expected = result.loads.ultimateLineLoadKNPerM * 4.20 * 4.20 / 8.0
        assertEquals(expected, result.spans.single().maxSpanMomentUltimateKNm, 1e-6)
        assertEquals(CheckStatus.OK, result.spans.single().reinforcement.status)
        assertTrue(result.deflection.deltaFtMeters <= result.deflection.allowableMeters + 1e-12)
        assertTrue(result.deflection.homogenizedSection.inertiaM4 > result.deflection.crackedSection.inertiaM4)
        assertEquals(CheckStatus.OK, result.deflection.status)
        assertEquals(CheckStatus.OK, result.status)
    }

    @Test
    fun `une console est ferraillee en chapeau superieur`() {
        val result = ContinuousRibbedSlabSolver().solve(
            ContinuousSlabInput(
                topology = ribbedTopology(spans = listOf(4.35, 4.35)),
                permanentLoadKNPerM2 = 6.75,
                variableLoadKNPerM2 = 1.00,
                rightCantileverMeters = 1.20
            )
        )

        assertEquals(1, result.cantilevers.size)
        val cantilever = result.cantilevers.single()
        assertEquals("Console droite", cantilever.side)
        assertEquals(1.20, cantilever.lengthMeters, 1e-12)
        assertEquals(SlabBarZone.CANTILEVER_TOP, cantilever.reinforcement.zone)
        assertEquals(
            -result.loads.ultimateLineLoadKNPerM * 1.20 * 1.20 / 2.0,
            cantilever.momentUltimateKNm,
            1e-9
        )
        assertTrue(cantilever.reinforcement.resistingMomentKNm + 1e-6 >= abs(cantilever.momentUltimateKNm))
        assertEquals(CheckStatus.OK, cantilever.reinforcement.status)
        assertEquals(CheckStatus.OK, cantilever.serviceStress.status)
    }

    // ---------------------------------------------------------------------------------
    // Moteur sismique global RPA 2024
    // ---------------------------------------------------------------------------------

    @Test
    fun `le moteur sismique reproduit la NDC Joint A`() {
        val assessment = Rpa2024SeismicEngine().assess(joint, imported.results)

        val modal = assessment.modalParticipation
        assertEquals(3, modal.modeCount)
        assertTrue(modal.checkASatisfied)
        assertFalse(modal.checkBSatisfied)
        assertEquals(3, modal.requiredModesFor90Percent)
        assertEquals(90.0, modal.sumUxPercent, 1e-9)
        assertEquals(90.0, modal.sumUyPercent, 1e-9)
        assertEquals(CheckStatus.OK, modal.status)
        assertEquals(0.399, modal.fundamentalModeX?.periodSeconds ?: 0.0, 1e-9)
        assertEquals(0.294, modal.fundamentalModeY?.periodSeconds ?: 0.0, 1e-9)

        val shearX = assessment.baseShear.first { it.direction == AxisDirection.X }
        val shearY = assessment.baseShear.first { it.direction == AxisDirection.Y }
        assertEquals(10357.95, requireNotNull(shearX.seismicWeightKN), 1e-6)
        assertEquals(2251.69, requireNotNull(shearX.staticBaseShearKN), 0.05)
        assertEquals(2251.69, requireNotNull(shearY.staticBaseShearKN), 0.05)
        assertEquals(1884.51, requireNotNull(shearX.dynamicBaseShearKN), 1e-6)
        assertEquals(1819.47, requireNotNull(shearY.dynamicBaseShearKN), 1e-6)
        assertEquals(0.85, shearX.lambda, 1e-12)
        assertEquals(0.25575, shearX.accelerationOverG, 1e-9)
        assertNull(shearX.scaleFactor)
        assertNull(shearY.scaleFactor)
        assertEquals(CheckStatus.OK, shearX.status)
        assertEquals(CheckStatus.OK, shearY.status)
        assertTrue(requireNotNull(shearX.dynamicToStaticRatio) >= 0.80)
        assertTrue(requireNotNull(shearY.dynamicToStaticRatio) >= 0.80)

        val columns = assessment.columnReducedNormalForce.associateBy { it.familyName }
        assertEquals(3, columns.size)
        assertEquals(0.140, columns.getValue("POT35x55").nu, 5e-4)
        assertEquals(0.299, columns.getValue("POT40x50").nu, 5e-4)
        assertEquals(0.146, columns.getValue("POT35x35").nu, 5e-4)
        columns.values.forEach { column ->
            assertTrue(column.nu <= column.nuLimit)
            assertEquals(CheckStatus.OK, column.status)
        }

        assertEquals(4, assessment.driftX.size)
        assertEquals(4, assessment.driftY.size)
        assertEquals(0.39, assessment.driftX.first().nuA, 1e-12)
        assessment.driftChecks.forEach { drift ->
            assertEquals(CheckStatus.OK, drift.status)
            assertEquals(0.0050 * drift.storyHeightMeters, drift.fragileLimitMeters, 1e-12)
            assertEquals(0.0075 * drift.storyHeightMeters, drift.ductileLimitMeters, 1e-12)
            assertTrue(drift.reducedDriftMeters <= drift.governingLimitMeters)
        }
        val topX = assessment.driftX.first { it.story == "CAGE" }
        assertEquals(0.014, topX.elasticDisplacementMeters, 1e-12)
        assertEquals(3.5 / 1.10, topX.inelasticDisplacementMeters / topX.elasticDisplacementMeters, 1e-9)

        assertEquals(4, assessment.pDeltaX.size)
        assertEquals(4, assessment.pDeltaY.size)
        assessment.pDeltaChecks.forEach { pDelta ->
            assertEquals(PDeltaClassification.NEGLIGIBLE, pDelta.classification)
            assertEquals(CheckStatus.OK, pDelta.status)
            assertTrue(pDelta.theta <= 0.10)
            assertNull(pDelta.amplificationFactor)
        }

        assertEquals(CheckStatus.OK, assessment.overallStatus)
        val report = assessment.toChapterReport()
        assertEquals(9, report.number)
        assertEquals(CheckStatus.OK, report.status)
        assertEquals(19, report.elements.size)
    }

    @Test
    fun `le critere 4-3-3 b exige 3 racine de N modes et Tk inferieure a 0,20 s`() {
        val engine = Rpa2024SeismicEngine()
        val fastModes = sixModes(lastPeriod = 0.15)
        val fast = engine.evaluateModalParticipation(fastModes, storyCount = 3)
        assertEquals(6, fast.minimumModesCheckB)
        assertFalse(fast.checkASatisfied)
        assertTrue(fast.checkBSatisfied)
        assertEquals(CheckStatus.OK, fast.status)

        val slow = engine.evaluateModalParticipation(sixModes(lastPeriod = 0.30), storyCount = 3)
        assertFalse(slow.checkBSatisfied)
        assertEquals(CheckStatus.FAILED, slow.status)

        val tooFew = engine.evaluateModalParticipation(sixModes(lastPeriod = 0.15).take(4), storyCount = 3)
        assertFalse(tooFew.checkBSatisfied)
        assertEquals(CheckStatus.FAILED, tooFew.status)
    }

    @Test
    fun `la zone 0 ne declenche aucune verification sismique de calcul`() {
        val zone0Config = SampleJointA.defaultConfig.copy(zoneCode = "0")
        val zone0Joint = joint.copy(seismicParams = zone0Config)
        val assessment = Rpa2024SeismicEngine().assess(zone0Joint, imported.results, zone0Config)

        assertNull(assessment.spectrumGenerator)
        assertTrue(assessment.baseShear.isEmpty())
        assertEquals(CheckStatus.NOT_APPLICABLE, assessment.checks.single().status)
    }

    // ---------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------

    private fun ribbedTopology(spans: List<Double> = listOf(4.35, 4.35, 4.35)): SlabStripTopology =
        SlabStripTopology(
            name = "16+5",
            totalThicknessHtCm = 21.0,
            compressionSlabH0Cm = 5.0,
            hollowBlockHeightCm = 16.0,
            ribWidthB0Cm = 10.0,
            flangeWidthBCm = 60.0,
            ribSpacingCm = 60.0,
            continuousSpansMeters = spans
        )

    private fun sixModes(lastPeriod: Double): List<ModalRatioRow> =
        (1..6).map { mode ->
            val period = lastPeriod + (6 - mode) * 0.05
            ModalRatioRow(
                mode = mode,
                periodSeconds = period,
                uxPercent = 10.0,
                uyPercent = 10.0,
                uzPercent = 0.0,
                sumUxPercent = 10.0 * mode,
                sumUyPercent = 10.0 * mode,
                sumUzPercent = 0.0
            )
        }

    @Test
    fun `les spectres et le moteur produisent des rapports coherents`() {
        val assessment = Rpa2024SeismicEngine().assess(joint, imported.results)
        val generator = requireNotNull(assessment.spectrumGenerator)
        assertNotNull(generator)
        assertEquals(0.217, generator.horizontalDesign(0.0), 1e-9)
        assertEquals(0.0825, generator.verticalDesign(0.25), 1e-12)

        val curve = generator.sample(SpectrumKind.HORIZONTAL_DESIGN, 0.02)
        assertEquals(201, curve.points.size)
        assertEquals(4.0, curve.maxPeriodSeconds, 1e-12)
        assertEquals(0.217, curve.valueAt(0.0), 1e-9)
        assertEquals(curve.valueAt(0.50), generator.horizontalDesign(0.50), 1e-6)
    }
}
