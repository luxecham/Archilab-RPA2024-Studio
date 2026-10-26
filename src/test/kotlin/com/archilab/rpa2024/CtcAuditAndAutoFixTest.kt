package com.archilab.rpa2024

import com.archilab.rpa2024.audit.*
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.normative.SiteClass
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Suite de tests unitaires pour la Partie 9 :
 * Moteur d'audit pré-CTC, régularité RPA 2024 § 3.7, joint sismique § 5.11,
 * hiérarchie poteau fort / poutre faible § 7.6, et auto-redimensionnement optimal.
 */
class CtcAuditAndAutoFixTest {

    private val regularityAnalyzer = RegularityAnalyzerRpa2024()
    private val jointCalculator = SeismicJointWidthCalculator()
    private val weakColumnStrongBeamVerifier = WeakColumnStrongBeamVerifier()
    private val auditorAndFixer = CtcComplianceAuditorAndAutoFixer()

    private fun createSampleJoint(
        name: String = "Joint A",
        bColCm: Double = 35.0,
        hColCm: Double = 35.0,
        bBeamCm: Double = 30.0,
        hBeamCm: Double = 50.0
    ): StructuralJoint {
        val gridSys = GridSystem(
            xGrids = listOf(GridLine("1", 0.0, AxisDirection.X), GridLine("2", 15.0, AxisDirection.X)),
            yGrids = listOf(GridLine("A", 0.0, AxisDirection.Y), GridLine("B", 20.65, AxisDirection.Y))
        )
        val storySys = StorySystem(
            stories = listOf(
                StoryLevel("H RDC", 3.74, 3.74),
                StoryLevel("H 1ER", 3.74, 7.48),
                StoryLevel("H 2EME", 3.74, 11.22),
                StoryLevel("TERRASSE", 3.74, 14.96)
            )
        )
        val catalog = SectionCatalog(
            columns = listOf(
                ColumnFamily("POT ${bColCm.toInt()}X${hColCm.toInt()}", ColumnShape.RECTANGULAR, bColCm, hColCm, clearHeightLclMeters = 3.19)
            ),
            beams = listOf(
                BeamFamily("P.Prin ${bBeamCm.toInt()}x${hBeamCm.toInt()}", BeamRole.MAIN_BEAM, bBeamCm, hBeamCm, maxClearSpanMeters = 5.0)
            ),
            shearWalls = listOf(
                ShearWallFamily("V1", AxisDirection.X, 3.0, 0.20, clearHeightHeMeters = 3.19, totalHeightHwMeters = 14.96)
            ),
            slabStrips = listOf(
                SlabStripTopology("Plancher", 21.0, 5.0, 16.0, 12.0, 60.0, listOf(4.35, 3.15))
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
        return StructuralJoint(name, gridSys, storySys, catalog, MaterialConfig(), seismicConfig)
    }

    @Test
    fun testPlanAndElevationRegularityAnalysisRegularBuilding() {
        val joint = createSampleJoint("Bloc Regulier")

        // Bâtiment régulier : retraits nuls, trémies minimes (6%), excentricités < 15%
        val report = regularityAnalyzer.analyzeRegularity(
            joint = joint,
            reentrantCornerRatio = 0.08,
            openingsAreaRatio = 0.06
        )

        assertTrue(report.planResult.isGloballyPlanRegular, "Le bâtiment devrait être régulier en plan")
        assertTrue(report.elevationResult.isGloballyElevationRegular, "Le bâtiment devrait être régulier en élévation")
        assertTrue(report.isFullyRegular, "Le bâtiment devrait être globalement régulier")

        // Facteur de qualité Qf = 1.0 car 0 pénalité
        assertEquals(1.00, report.qualityFactorQf, 0.001)
        assertEquals(0.0, report.totalPenaltySum, 0.001)
    }

    @Test
    fun testPlanAndElevationRegularityAnalysisIrregularBuilding() {
        val joint = createSampleJoint("Bloc Irregulier")

        // Bâtiment irrégulier : retraits en plan de 35% (> 25%), trémies de 22% (> 15%)
        val report = regularityAnalyzer.analyzeRegularity(
            joint = joint,
            reentrantCornerRatio = 0.35,
            openingsAreaRatio = 0.22
        )

        assertFalse(report.planResult.isGloballyPlanRegular, "Le bâtiment avec décrochements majeurs doit être irrégulier en plan")
        assertFalse(report.isFullyRegular, "Le bâtiment ne doit pas être globalement régulier")

        // Qf doit incorporer les pénalités Pq (Qf > 1.0)
        assertTrue(report.qualityFactorQf > 1.0, "Le facteur de qualité Qf doit être pénalisé (Qf > 1.0)")
        assertTrue(report.totalPenaltySum > 0.0, "Des pénalités Pq doivent être enregistrées")
    }

    @Test
    fun testSeismicJointWidthCalculationBetweenAdjacentBlocks() {
        val jointA = createSampleJoint("Joint A")
        val jointB = createSampleJoint("Joint B")

        // Déplacements inélastiques au sommet : delta1 = 18 mm, delta2 = 14 mm
        val report = jointCalculator.calculateSeismicJointWidth(
            jointA = jointA,
            jointB = jointB,
            deltaMaxAInelasticMm = 18.0,
            deltaMaxBInelasticMm = 14.0
        )

        // d_min = 15mm + 18mm + 14mm = 47 mm (4.7 cm) >= 40 mm
        assertEquals(47.0, report.dMinLinearMm, 0.01)
        assertTrue(report.adoptedJointWidthMm >= 47.0)
        // Arrondi au centimètre supérieur : 5.0 cm (50 mm)
        assertEquals(5.0, report.adoptedJointWidthCm, 0.01)
        assertTrue(report.isCompliantWith40mmMin)
    }

    @Test
    fun testCapacityDesignWeakColumnStrongBeamHierarchy() {
        val joint = createSampleJoint("Portique Node")

        // Poutres 30x50, Poteaux 35x35
        val report = weakColumnStrongBeamVerifier.verifyHierarchy(
            joint = joint,
            columnRebarAreaCm2 = 18.0,
            beamRebarAreaCm2 = 10.0,
            columnNdKN = 600.0
        )

        assertNotNull(report)
        assertEquals("Portique Node", report.jointName)
        assertTrue(report.nodesEvaluated.isNotEmpty())

        for (node in report.nodesEvaluated) {
            assertTrue(node.sumMRcKNm > 0.0)
            assertTrue(node.sumMRbKNm > 0.0)
            assertTrue(node.ratioLambda > 0.0)
        }
    }

    @Test
    fun testCtcComplianceAuditAndAutoFixOverloadedColumn() {
        // Poteau 35x35 cm soumis à un effort normal élevé Nd = 1400 kN
        // Pour fc28 = 25 MPa, Bc = 0.35 * 0.35 = 0.1225 m²
        // nu = 1400 / (0.1225 * 25000) = 0.457 > 0.35 (Non conforme au RPA 2024 § 7.4.3.1)
        val joint = createSampleJoint("Bloc Poteau Surchargé", bColCm = 35.0, hColCm = 35.0)

        val report = auditorAndFixer.performCtcAudit(
            joint = joint,
            forcedColumnNdMaxKN = 1400.0
        )

        assertNotNull(report)
        assertEquals(15, report.checks.size, "L'audit doit comporter exactement 15 points de contrôle CTC")

        // Le point #8 (nu <= 0.35) doit être en RÉSERVE BLOQUANTE
        val colCheck = report.checks.find { it.checkNumber == 8 }
        assertNotNull(colCheck)
        assertEquals(CtcComplianceStatus.RESERVE_BLOQUANTE, colCheck?.status)
        assertFalse(report.isCtcVisaGranted, "Le visa CTC ne doit pas être accordé en présence de réserves bloquantes")

        // Une proposition d'auto-optimisation doit avoir été émise
        assertTrue(report.autoFixProposals.isNotEmpty())
        val colFix = report.autoFixProposals.find { it.elementType == "Poteau" }
        assertNotNull(colFix)

        // Test direct de l'algorithme d'optimisation
        val optimizedCol = auditorAndFixer.optimizeColumnSection(ndKN = 1400.0, fc28MPa = 25.0)
        // Vérification de la nouvelle section : nu <= 0.35
        val optBcM2 = (optimizedCol.b1Cm / 100.0) * (optimizedCol.h1Cm / 100.0)
        val optNu = 1400.0 / (optBcM2 * 25000.0)
        assertTrue(optNu <= 0.35, "La section optimisée (${optimizedCol.b1Cm}x${optimizedCol.h1Cm}) doit satisfaire nu <= 0.35 (nu = $optNu)")
        // Vérification du pas multiple de 5 cm
        assertEquals(0.0, optimizedCol.b1Cm % 5.0, 1e-4)
        assertEquals(0.0, optimizedCol.h1Cm % 5.0, 1e-4)
    }

    @Test
    fun testCtcComplianceAuditFullyCompliantJoint() {
        // Section normale : poteau 45x45 cm, poutre 30x50 cm, Nd = 700 kN
        // Bc = 0.45 * 0.45 = 0.2025 m² -> nu = 700 / (0.2025 * 25000) = 0.138 <= 0.35
        val joint = createSampleJoint("Bloc Conforme", bColCm = 45.0, hColCm = 45.0)

        val report = auditorAndFixer.performCtcAudit(
            joint = joint,
            forcedColumnNdMaxKN = 700.0
        )

        val colCheck = report.checks.find { it.checkNumber == 8 }
        assertEquals(CtcComplianceStatus.CONFORME, colCheck?.status)
        assertTrue(report.isCtcVisaGranted, "Le visa CTC doit être favorable lorsque tous les critères sont respectés")
        assertEquals(0, report.blockingReserveCount)
    }
}
