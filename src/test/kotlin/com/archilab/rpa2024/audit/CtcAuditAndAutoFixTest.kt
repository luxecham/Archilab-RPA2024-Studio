package com.archilab.rpa2024.audit

import com.archilab.rpa2024.parser.ColumnForceRow
import com.archilab.rpa2024.parser.DiaphragmDisplacementRow
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.EtabsResultsReader
import com.archilab.rpa2024.parser.MassRigidityCenterRow
import com.archilab.rpa2024.parser.TabularTable
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ceil
import kotlin.math.max

/**
 * Validation de la Partie 9 : régularité mathématique et QF, joint sismique
 * inter-blocs, audit CTC et redimensionnement automatique des poteaux.
 */
class CtcAuditAndAutoFixTest {

    private val imported = SampleJointA.load()
    private val regularityOptions = RegularityCriteriaOptions(includePenthouse = false)

    @Test
    fun `la regularite en plan et en elevation pilote le facteur de qualite`() {
        val centers = listOf(
            center("N1", xcm = 7.5),
            center("N2", xcm = 7.5),
            center("N3", xcm = 7.5)
        )
        val floors = listOf(
            fullFloor("N1"),
            fullFloor("N2"),
            fullFloor("N3")
        )
        val regularResults = regularResults(centers)
        val analyzer = RegularityAnalyzerRpa2024()
        val regular = analyzer.analyze(
            RegularityAnalysisRequest(
                model = imported.model,
                results = regularResults,
                floors = floors,
                centers = centers,
                options = regularityOptions
            )
        )
        assertTrue(
            regular.planRegular,
            "Le plan rectangulaire et symétrique doit être régulier. " +
                regular.planCriteria.joinToString(" | ") { "${it.code}:${it.observed}" }
        )
        assertTrue(
            regular.elevationRegular,
            "La trame verticale continue doit être régulière. " +
                regular.elevationCriteria.joinToString(" | ") { "${it.code}:${it.observed}" }
        )
        assertEquals(1.10, regular.qf, 1e-9, "QF = 1 + pénalité de 0,10 liée aux 2 travées X du modèle d'essai.")
        assertEquals(0.0, regular.planPenalty, 1e-9)
        assertEquals(0.0, regular.elevationPenalty, 1e-9)

        val irregularFloors = listOf(
            fullFloor("N1", openings = listOf(
                listOf(
                    PlanVertex(3.0, 3.0), PlanVertex(11.0, 3.0),
                    PlanVertex(11.0, 10.0), PlanVertex(3.0, 10.0)
                )
            )),
            fullFloor("N2"),
            FloorGeometry.rectangle("N3", 0.0, 10.0, 0.0, 20.65)
        )
        val irregularCenters = listOf(
            center("N1", xcm = 7.5),
            center("N2", xcm = 7.5),
            center("N3", xcm = 5.0)
        )
        val irregular = analyzer.analyze(
            RegularityAnalysisRequest(
                model = imported.model,
                results = regularResults(irregularCenters),
                floors = irregularFloors,
                centers = irregularCenters,
                options = regularityOptions
            )
        )
        assertFalse(irregular.planRegular, "La trémie de 18 % doit rendre le plan irrégulier.")
        assertFalse(irregular.elevationRegular, "Le retrait de 33 % au niveau N3 doit rendre l'élévation irrégulière.")
        assertEquals(0.05, irregular.planPenalty, 1e-9)
        assertEquals(0.20, irregular.elevationPenalty, 1e-9)
        assertEquals(1.35, irregular.qf, 1e-9)
        assertTrue(irregular.planCriteria.first { it.code == "a5" }.status == com.archilab.rpa2024.engine.geometry.CheckStatus.FAILED)
        assertTrue(irregular.elevationCriteria.first { it.code == "b3" }.status == com.archilab.rpa2024.engine.geometry.CheckStatus.FAILED)
    }

    @Test
    fun `la largeur du joint sismique entre deux blocs est calculee selon R sur QF`() {
        val stories = imported.model.joint.storySystem.mainStories.map { it.storyName }
        val resultsA = displacementResults(stories, maxElasticMeters = 0.005)
        val resultsB = displacementResults(stories, maxElasticMeters = 0.004)
        val jointA = imported.model.joint.copy(jointName = "Bloc A")
        val jointB = imported.model.joint.copy(jointName = "Bloc B")
        val calculator = SeismicJointWidthCalculator()
        val assessment = calculator.assess(
            SeismicBlock("Bloc A", jointA, resultsA),
            SeismicBlock("Bloc B", jointB, resultsB)
        )
        assertTrue(assessment.isAvailable)
        assertNotNull(assessment.requiredWidthMm)
        assertNotNull(assessment.recommendedWidthMm)
        val expectedAdditive = 15.0 +
            assessment.demandA.maxInelasticMeters * 1000.0 +
            assessment.demandB.maxInelasticMeters * 1000.0
        assertEquals(expectedAdditive, assessment.additiveWidthMm ?: 0.0, 1e-9)
        assertEquals(max(40.0, expectedAdditive), assessment.requiredWidthMm ?: 0.0, 1e-9)
        assertEquals(ceil(expectedAdditive / 10.0 - 1e-9) * 10.0, assessment.recommendedWidthMm ?: 0.0, 1e-9)
        assertEquals(0.005 * assessment.demandA.amplificationFactor, assessment.demandA.maxInelasticMeters, 1e-12)
        assertEquals(0.004 * assessment.demandB.amplificationFactor, assessment.demandB.maxInelasticMeters, 1e-12)
        assertEquals(com.archilab.rpa2024.engine.geometry.CheckStatus.OK, assessment.check.status)

        val quadratic = calculator.assess(
            SeismicBlock("Bloc A", jointA, resultsA),
            SeismicBlock("Bloc B", jointB, resultsB),
            SeismicJointOptions(combination = SeismicJointCombination.QUADRATIC)
        )
        assertTrue((quadratic.quadraticWidthMm ?: 0.0) < (quadratic.additiveWidthMm ?: 0.0))
        assertEquals(max(40.0, quadratic.quadraticWidthMm ?: 0.0), quadratic.requiredWidthMm ?: 0.0, 1e-9)
    }

    @Test
    fun `un poteau surcharge a nu 0 42 est redimensionne au multiple de 5 cm`() {
        val axialKN = 1653.75
        val fc28MPa = 25.0
        val optimizer = AutoSectionOptimizer()
        val fix = optimizer.optimizeColumn(
            sectionName = "POT35x45",
            widthCm = 35.0,
            depthCm = 45.0,
            axialKN = axialKN,
            fc28MPa = fc28MPa,
            nuLimit = 0.35,
            incrementCm = 5.0,
            elementIds = listOf("C_TEST"),
            materialName = "B25"
        )
        assertNotNull(fix)
        fix!!
        assertEquals(0.42, fix.previousNu, 1e-9)
        assertTrue(fix.previousNu > fix.nuLimit)
        assertEquals(40.0, fix.proposedWidthCm, 1e-9)
        assertEquals(50.0, fix.proposedDepthCm, 1e-9)
        assertTrue(fix.proposedNu <= 0.35 + 1e-9)
        assertEquals(0.0, fix.proposedWidthCm % 5.0, 1e-9)
        assertEquals(0.0, fix.proposedDepthCm % 5.0, 1e-9)
        assertEquals(0.40, fix.updateCommand.parameters.getValue("largeur_m"), 1e-9)
        assertEquals(0.50, fix.updateCommand.parameters.getValue("hauteur_m"), 1e-9)
        assertTrue(fix.updateCommand.oapiCalls.any { it.contains("SetRectangle") })
        assertTrue(fix.updateCommand.e2kManagedBlock.contains("RPA2024:AUTOFIX:BEGIN"))

        val noFix = optimizer.optimizeColumn(
            sectionName = "POT35x45",
            widthCm = 35.0,
            depthCm = 45.0,
            axialKN = 1000.0,
            fc28MPa = fc28MPa
        )
        assertNull(noFix, "Un poteau correctement dimensionné ne doit pas être modifié.")
    }

    @Test
    fun `un decrochement en L est detecte par le critere a2`() {
        val centers = listOf(center("N1", 5.0), center("N2", 5.0), center("N3", 5.0))
        val lShape = listOf(
            PlanVertex(0.0, 0.0), PlanVertex(10.0, 0.0),
            PlanVertex(10.0, 5.0), PlanVertex(5.0, 5.0),
            PlanVertex(5.0, 10.0), PlanVertex(0.0, 10.0)
        )
        val floors = listOf(
            FloorGeometry("N1", listOf(lShape)),
            FloorGeometry("N2", listOf(lShape)),
            FloorGeometry("N3", listOf(lShape))
        )
        val irregularCenters = centers.map {
            it.copy(ycmMeters = 5.0, ycrMeters = 5.0)
        }
        val assessment = RegularityAnalyzerRpa2024().analyze(
            RegularityAnalysisRequest(
                model = imported.model,
                results = regularResults(irregularCenters),
                floors = floors,
                centers = irregularCenters,
                options = regularityOptions
            )
        )
        val a2 = assessment.planCriteria.first { it.code == "a2" }
        assertFalse(assessment.planRegular)
        assertEquals(com.archilab.rpa2024.engine.geometry.CheckStatus.FAILED, a2.status)
        assertTrue(a2.observed.contains("décrochement"))
    }

    @Test
    fun `la table ETABS des centres de masse et de rigidite est lue`() {
        val tables = listOf(
            TabularTable("Program Control", listOf("CurrUnits"), listOf(listOf("KN-M"))),
            TabularTable(
                "Centers Of Mass And Rigidity",
                listOf("Story", "Diaphragm", "MassX", "MassY", "XCM", "YCM", "XCR", "YCR"),
                listOf(listOf("N1", "D1", "1800", "1800", "7.50", "10.30", "7.30", "10.10"))
            )
        )
        val results = EtabsResultsReader().read(tables)
        assertEquals(1, results.massRigidityCenters.size)
        val center = results.massRigidityCenters.single()
        assertEquals("N1", center.story)
        assertEquals(7.50, center.xcmMeters, 1e-12)
        assertEquals(10.30, center.ycmMeters, 1e-12)
        assertEquals(7.30, center.xcrMeters, 1e-12)
        assertEquals(10.10, center.ycrMeters, 1e-12)
        assertEquals(1800.0, center.massX, 1e-12)
    }

    @Test
    fun `l'audit CTC detecte le poteau surcharge et joint la correction automatique`() {
        val centers = listOf(center("N1", 7.5), center("N2", 7.5), center("N3", 7.5))
        val floors = listOf(fullFloor("N1"), fullFloor("N2"), fullFloor("N3"))
        val overload = ColumnForceRow(
            story = "N1",
            element = "C_1_0_N1",
            loadCase = "ELA2",
            stationMeters = 0.0,
            pKN = -2100.0,
            v2KN = 0.0,
            v3KN = 0.0,
            m2KNm = 120.0,
            m3KNm = 150.0
        )
        val results = regularResults(centers).copy(
            columnForces = regularResults(centers).columnForces + overload
        )
        val report = CtcComplianceAuditorAndAutoFixer().audit(
            CtcAuditRequest(
                model = imported.model,
                results = results,
                floors = floors,
                centers = centers,
                options = CtcAuditOptions(regularityOptions = regularityOptions)
            )
        )
        val fix = report.autoFixes.filterIsInstance<ColumnResizeFix>()
            .firstOrNull { it.elementName == "POT40x50" }
        assertNotNull(fix, "L'audit doit proposer le redimensionnement du poteau POT40x50.")
        fix!!
        assertEquals(0.42, fix.previousNu, 1e-9)
        assertTrue(fix.proposedNu <= 0.35 + 1e-9)
        assertEquals(45.0, fix.proposedWidthCm, 1e-9)
        assertEquals(55.0, fix.proposedDepthCm, 1e-9)
        assertEquals(CtcFindingStatus.RESERVE_BLOQUANTE, report.controls.first { it.index == 8 }.status)
    }

    @Test
    fun `l'auditeur CTC produit les quinze controles et une synthese de rapport`() {
        val centers = listOf(center("N1", 7.5), center("N2", 7.5), center("N3", 7.5))
        val floors = listOf(fullFloor("N1"), fullFloor("N2"), fullFloor("N3"))
        val results = regularResults(centers)
        val report = CtcComplianceAuditorAndAutoFixer().audit(
            CtcAuditRequest(
                model = imported.model,
                results = results,
                floors = floors,
                centers = centers,
                options = CtcAuditOptions(regularityOptions = regularityOptions)
            )
        )
        assertEquals(15, report.controls.size)
        assertEquals((1..15).toList(), report.controls.map { it.index })
        assertTrue(report.controls.map { it.code }.toSet().containsAll((1..15).map { "CTC" + it.toString().padStart(2, '0') }))
        assertTrue(
            report.regularity.planRegular,
            report.regularity.planCriteria.joinToString(" | ") { "${it.code}:${it.observed}" }
        )
        assertTrue(
            report.regularity.elevationRegular,
            report.regularity.elevationCriteria.joinToString(" | ") { "${it.code}:${it.observed}" }
        )
        assertNotNull(report.singleBlockJointRecommendation)
        assertTrue(
            report.weakColumnStrongBeam.checks.isNotEmpty(),
            "La vérification poteau fort / poutre faible doit produire des nœuds. " +
                report.weakColumnStrongBeam.warnings.joinToString(" | ")
        )
        assertTrue(report.weakColumnStrongBeam.checks.all { it.columnMomentKNm > 0.0 && it.beamMomentKNm > 0.0 })
        val chapter = report.toChapterReport()
        assertTrue(chapter.checks.isNotEmpty())
        assertTrue(chapter.lines.any { it.contains("Audit CTC") })
        assertTrue(report.verdict != CtcVerdict.CONFORME || report.warnings.isEmpty())
    }

    private fun center(story: String, xcm: Double): MassRigidityCenterRow = MassRigidityCenterRow(
        story = story,
        diaphragm = "D1",
        massX = 1000.0,
        massY = 1000.0,
        xcmMeters = xcm,
        ycmMeters = 10.325,
        xcrMeters = xcm,
        ycrMeters = 10.325
    )

    private fun fullFloor(
        story: String,
        openings: List<List<PlanVertex>> = emptyList()
    ): FloorGeometry = FloorGeometry.rectangle(
        storyName = story,
        minX = 0.0,
        maxX = 15.0,
        minY = 0.0,
        maxY = 20.65,
        openings = openings
    )

    private fun displacementResults(stories: List<String>, maxElasticMeters: Double): EtabsResults {
        val rows = stories.mapIndexed { index, story ->
            val fraction = (index + 1).toDouble() / stories.size
            DiaphragmDisplacementRow(
                story = story,
                loadCase = "EX",
                uxMeters = maxElasticMeters * fraction,
                uyMeters = 0.0
            )
        }
        return EtabsResults(diaphragmDisplacements = rows)
    }

    private fun regularResults(centers: List<MassRigidityCenterRow>): EtabsResults {
        val base = imported.results
        val weights = base.storyShears.filter { it.isSeismicWeightCase }
        val shears = listOf(
            com.archilab.rpa2024.parser.StoryShearRow("N1", "Bottom", "EX", 1800.0, 0.0),
            com.archilab.rpa2024.parser.StoryShearRow("N2", "Bottom", "EX", 1500.0, 0.0),
            com.archilab.rpa2024.parser.StoryShearRow("N3", "Bottom", "EX", 1000.0, 0.0),
            com.archilab.rpa2024.parser.StoryShearRow("N1", "Bottom", "EY", 0.0, 1800.0),
            com.archilab.rpa2024.parser.StoryShearRow("N2", "Bottom", "EY", 0.0, 1400.0),
            com.archilab.rpa2024.parser.StoryShearRow("N3", "Bottom", "EY", 0.0, 900.0)
        )
        val displacements = listOf(
            DiaphragmDisplacementRow("N1", "EX", 0.005, 0.0),
            DiaphragmDisplacementRow("N2", "EX", 0.009, 0.0),
            DiaphragmDisplacementRow("N3", "EX", 0.012, 0.0),
            DiaphragmDisplacementRow("N1", "EY", 0.0, 0.004),
            DiaphragmDisplacementRow("N2", "EY", 0.0, 0.0075),
            DiaphragmDisplacementRow("N3", "EY", 0.0, 0.0105)
        )
        return base.copy(
            storyShears = weights + shears,
            diaphragmDisplacements = displacements,
            massRigidityCenters = centers
        )
    }
}
