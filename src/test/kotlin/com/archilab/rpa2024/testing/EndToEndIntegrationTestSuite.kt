package com.archilab.rpa2024.testing

import com.archilab.rpa2024.audit.*
import com.archilab.rpa2024.cad.*
import com.archilab.rpa2024.engine.foundations.BaelTInput
import com.archilab.rpa2024.engine.foundations.BaelTSectionSolver
import com.archilab.rpa2024.engine.frames.BaelRInput
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.DesignSituation
import com.archilab.rpa2024.engine.geometry.GeometryAndPreSizingEngine
import com.archilab.rpa2024.engine.seismic.*
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionCalculator
import com.archilab.rpa2024.engine.slabs.TBeamDeflectionInput
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.*
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import com.archilab.rpa2024.persistence.*
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.PdfReportExporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.abs

/**
 * Suite d'homologation finale de bout en bout (End-to-End Integration Test Suite).
 * Valide l'intégralité du pipeline de l'application Windows Standalone sur le cas réel
 * "Sûreté Urbaine 1500 LLV Bosquet - Joint A" :
 * 1. Ingestion topologique ETABS (.e2k)
 * 2. Moteur parasismique RPA 2024 (spectres, modes, calage base shear, drifts, nu <= 0.35)
 * 3. Solveurs structurels BAEL (BaelR flexion simple/composée, BaelT semelles, flèche Koka)
 * 4. Production de la Note de Calcul Word (.docx) et synthèse PDF
 * 5. Production des plans d'exécution CAO AutoCAD (.dxf) et nomenclature BBS
 * 6. Audit automatisé CTC et régularité inter-joints
 * 7. Persistance SQLite / Exposed multi-joints
 * 8. Calcul du Métré & DQE avec export Excel (.xlsx)
 */
class EndToEndIntegrationTestSuite {

    @Test
    fun testCompleteEndToEndPipelineOnBosquetJointA(@TempDir tempDir: File) {
        // =========================================================================
        // ÉTAPE 1 : INGESTION DU MODÈLE ETABS & DÉCOUVERTE GÉOMÉTRIQUE DYNAMIQUE
        // =========================================================================
        val e2kContent = """
            ; ETABS MODEL - SURETE URBAINE 1500 LLV BOSQUET - JOINT A
            UNITS "KN-M"
            
            $ STORIES - STORY DATA
              STORY "H RDC"     HEIGHT 3.74  ELEV 3.74
              STORY "H 1ER"     HEIGHT 3.74  ELEV 7.48
              STORY "H 2EME"    HEIGHT 3.74  ELEV 11.22
              STORY "TERRASSE"  HEIGHT 3.74  ELEV 14.96
              
            $ GRIDS
              GRID "1" DIR "X" ORD 0.0
              GRID "2" DIR "X" ORD 4.65
              GRID "3" DIR "X" ORD 9.75
              GRID "4" DIR "X" ORD 15.00
              GRID "A" DIR "Y" ORD 0.0
              GRID "B" DIR "Y" ORD 6.35
              GRID "C" DIR "Y" ORD 13.50
              GRID "D" DIR "Y" ORD 20.65
              
            $ POINT COORDINATES
              POINT "1"  X 0.0   Y 0.0   Z 0.0
              POINT "2"  X 0.0   Y 0.0   Z 3.74
              POINT "3"  X 4.65  Y 0.0   Z 3.74
              POINT "4"  X 15.00 Y 0.0   Z 3.74
              POINT "5"  X 15.00 Y 20.65 Z 3.74
              POINT "6"  X 0.0   Y 20.65 Z 3.74
              
            $ FRAME SECTIONS
              FRAME "POT 35X50"      SHAPE "RECTANGULAR" T3 0.50 T2 0.35
              FRAME "POT 40X50"      SHAPE "RECTANGULAR" T3 0.50 T2 0.40
              FRAME "POT 35X35"      SHAPE "RECTANGULAR" T3 0.35 T2 0.35
              FRAME "P.Prin 30x55"   SHAPE "RECTANGULAR" T3 0.55 T2 0.30
              FRAME "P.Second 30x35" SHAPE "RECTANGULAR" T3 0.35 T2 0.30
              
            $ WALL/SLAB/DECK PROPERTIES
              WALL "WALL20" THICKNESS 0.20
              
            $ LINE CONNECTIVITIES
              LINE "C1" I "1" J "2"
              LINE "B1" I "2" J "3"
              
            $ LINE ASSIGNS
              LINE "C1" SECTION "POT 35X50"
              LINE "B1" SECTION "P.Prin 30x55"
              
            $ END OF FILE
        """.trimIndent()

        val ingestor = EtabsUniversalIngestor()
        val joint = ingestor.parseModelE2k(e2kContent, "Joint A - Bosquet")

        assertNotNull(joint)
        assertEquals("Joint A - Bosquet", joint.jointName)
        assertEquals(4, joint.storySystem.stories.size)
        assertEquals(14.96, joint.storySystem.totalHeightHn, 0.01)
        assertEquals(15.00, joint.gridSystem.totalLengthLx, 0.01)
        assertEquals(20.65, joint.gridSystem.totalLengthLy, 0.01)

        // =========================================================================
        // ÉTAPE 2 : EXÉCUTION DU MOTEUR SISMIQUE RPA 2024
        // =========================================================================
        val spectrumGen = SpectrumGenerator()
        val seismicEngine = Rpa2024SeismicEngine(spectrumGen)

        val seismicReport = seismicEngine.performCompleteSeismicAnalysis(joint)
        assertNotNull(seismicReport)
        assertTrue(seismicReport.modalAnalysis.sumUxPercent >= 90.0)
        assertTrue(seismicReport.modalAnalysis.sumUyPercent >= 90.0)
        assertTrue(seismicReport.baseShearComparison.overallCompliant)

        // Vérification de l'effort normal réduit
        for (colCheck in seismicReport.columnReducedNormalForces) {
            assertTrue(colCheck.nuComputed <= 0.35, "L'effort normal réduit du poteau doit respecter nu <= 0.35")
        }

        // =========================================================================
        // ÉTAPE 3 : SOLVEURS STRUCTURELS BAELR & BAELT
        // =========================================================================
        // 3.1 Poutrelles continues Koka & Flèche BAEL 91
        val slabSolver = ContinuousRibbedSlabSolver()
        val slabDeflectionCalc = TBeamDeflectionCalculator()

        val slabTopology = SlabStripTopology(
            name = "ETAGE COURANT",
            totalThicknessHtCm = 21.0,
            compressionSlabH0Cm = 5.0,
            hollowBlockHeightCm = 16.0,
            ribWidthB0Cm = 12.0,
            flangeWidthBCm = 60.0,
            continuousSpansMeters = listOf(4.35, 3.15, 2.80)
        )
        val slabReport = slabSolver.solve(slabTopology, deadLoadGKNm2 = 6.75, liveLoadQKNm2 = 1.00)
        assertTrue(slabReport.spans.isNotEmpty())

        val deflectionInput = TBeamDeflectionInput(
            spanLengthMeters = 4.35,
            flangeWidthBMeters = 0.60,
            webWidthB0Meters = 0.12,
            totalHeightHMeters = 0.21,
            flangeThicknessH0Meters = 0.05,
            tensionSteelAreaM2 = 3.39e-4,     // 3T12
            compressionSteelAreaM2 = 1.13e-4, // 1T12
            dPrimeMeters = 0.02,
            dDoublePrimeMeters = 0.02,
            fc28MPa = 25.0,
            momentMjKNm = 11.56,
            momentMgKNm = 11.56,
            momentMpKNm = 11.56
        )
        val deflectionResult = slabDeflectionCalc.compute(deflectionInput)
        assertTrue(deflectionResult.isDeflectionSatisfied)
        assertTrue(deflectionResult.harmfulDeflectionDeltaFtMm <= deflectionResult.allowableDeflectionFbarMm)

        // 3.2 Poteaux en flexion composée BaelR
        val baelRectSolver = BaelRectangularSolver()
        val colBaelRInput = BaelRInput(
            widthBMeters = 0.35,
            heightHMeters = 0.50,
            dPrimeMeters = 0.035,
            cMeters = 0.035,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = 850.0,
            momentMKNm = 75.0,
            situation = DesignSituation.ACCIDENTAL_SEISMIC
        )
        val colBaelRResult = baelRectSolver.solve(colBaelRInput)
        assertTrue(colBaelRResult.aTotalCm2 >= 0.0)

        // 3.3 Semelles filantes BaelT (Section en Té renversé)
        val baelTSolver = BaelTSectionSolver()
        val footingTInput = BaelTInput(
            flangeWidthBMeters = 2.00,
            webWidthB0Meters = 0.50,
            totalHeightHMeters = 0.90,
            flangeThicknessH0Meters = 0.40,
            dPrimeMeters = 0.05,
            cMeters = 0.05,
            fc28MPa = 25.0,
            feMPa = 500.0,
            momentMUKNm = 126.0,
            thetaDuration = 0.85
        )
        val footingTResult = baelTSolver.solveBaelT(footingTInput)
        assertTrue(footingTResult.isNeutralAxisInFlange)
        assertTrue(footingTResult.aInfCm2 > 0.0)

        // =========================================================================
        // ÉTAPE 4 : PRODUCTION DES DOCUMENTS OFFICIELS (WORD .DOCX & PDF)
        // =========================================================================
        val wordGen = CtcWordReportGenerator()
        val wordFile = File(tempDir, "SURETE_URBAINE_BOSQUET_JOINT_A_NDC.docx")
        wordGen.generateWordReport(joint, wordFile)
        assertTrue(wordFile.exists(), "Le fichier Word .docx doit être généré")
        assertTrue(wordFile.length() > 5000, "Le fichier Word doit contenir l'ensemble des 13 chapitres")

        val pdfExporter = PdfReportExporter()
        val pdfFile = File(tempDir, "SURETE_URBAINE_BOSQUET_JOINT_A_SYNTHESE.pdf")
        pdfExporter.exportExecutivePdf(joint, pdfFile)
        assertTrue(pdfFile.exists(), "Le rapport exécutif PDF doit être généré")
        assertTrue(pdfFile.length() > 1000)

        // =========================================================================
        // ÉTAPE 5 : EXPORT CAO AUTOCAD (.DXF) & NOMENCLATURE DES ACIERS (BBS)
        // =========================================================================
        val masterDxf = DxfCoreEngine()
        val fwExp = FormworkPlanDxfExporter()
        val detExp = StructuralDetailingDxfExporter()
        val bbsGen = BarBendingScheduleDxfGenerator()

        fwExp.drawFloorFormworkPlan(masterDxf, joint, 0.0, 0.0, 1, "Sûreté Urbaine Bosquet", "Alger")
        fwExp.drawFoundationFormworkPlan(masterDxf, joint, 35.0, 0.0, "Sûreté Urbaine Bosquet", "Alger")
        detExp.drawColumnDetailingSheet(masterDxf, joint, 0.0, -25.0, "Sûreté Urbaine Bosquet", "Alger")
        detExp.drawBeamDetailingSheet(masterDxf, joint, 26.0, -25.0, "Sûreté Urbaine Bosquet", "Alger")
        detExp.drawShearWallDetailingSheet(masterDxf, joint, 56.0, -25.0, "Sûreté Urbaine Bosquet", "Alger")
        detExp.drawSecondaryAndFootingDetailingSheet(masterDxf, joint, 84.0, -25.0, "Sûreté Urbaine Bosquet", "Alger")

        val bbsReport = bbsGen.computeBendingSchedule(joint)
        bbsGen.drawBendingScheduleTable(masterDxf, 110.0, -25.0, bbsReport, joint, "Sûreté Urbaine Bosquet", "Alger")

        val dxfFile = File(tempDir, "PLANS_EXECUTION_BOSQUET_JOINT_A.dxf")
        masterDxf.saveToFile(dxfFile)
        assertTrue(dxfFile.exists(), "Le fichier DXF maître doit être créé")
        assertTrue(dxfFile.length() > 5000)

        // =========================================================================
        // ÉTAPE 6 : AUDIT PRÉ-CTC, RÉGULARITÉ § 3.7 & JOINT SISMIQUE § 5.11
        // =========================================================================
        val ctcAuditor = CtcComplianceAuditorAndAutoFixer()
        val ctcReport = ctcAuditor.performCtcAudit(joint)

        assertEquals(15, ctcReport.checks.size, "L'audit CTC doit comporter 15 points réglementaires")
        assertTrue(ctcReport.isCtcVisaGranted, "Le visa CTC doit être favorable pour le modèle conforme")
        assertEquals(0, ctcReport.blockingReserveCount)

        val regAnalyzer = RegularityAnalyzerRpa2024()
        val regReport = regAnalyzer.analyzeRegularity(joint)
        assertTrue(regReport.planResult.isGloballyPlanRegular)
        assertTrue(regReport.elevationResult.isGloballyElevationRegular)
        assertEquals(1.00, regReport.qualityFactorQf, 0.01)

        val jointCalc = SeismicJointWidthCalculator()
        val jointReport = jointCalc.calculateSeismicJointWidth(joint, deltaMaxAInelasticMm = 18.0)
        assertTrue(jointReport.adoptedJointWidthCm >= 4.0)

        // =========================================================================
        // ÉTAPE 7 : PERSISTANCE SQLITE / EXPOSED MULTI-JOINTS
        // =========================================================================
        val dbFile = File(tempDir, "test_archilab_e2e.db")
        val dbManager = ProjectDatabaseManager(dbFile)

        val projectData = ProjectEntityData(
            id = "PROJ_BOSQUET_001",
            title = "Projet Sûreté Urbaine 1500 LLV Bosquet",
            wilaya = "Alger",
            engineerName = "Ing. Cherif ArchiLab",
            joints = listOf(joint),
            manualOverrides = mapOf("POT 35X50_rebar" to "8T16", "P.Prin 30x55_sup" to "3T16")
        )

        dbManager.saveProject(projectData)
        val loadedProject = dbManager.loadProject("PROJ_BOSQUET_001")

        assertNotNull(loadedProject)
        assertEquals("Projet Sûreté Urbaine 1500 LLV Bosquet", loadedProject?.title)
        assertEquals(1, loadedProject?.joints?.size)
        assertEquals("Joint A - Bosquet", loadedProject?.joints?.first()?.jointName)
        assertEquals(4, loadedProject?.joints?.first()?.storySystem?.stories?.size)
        assertEquals("8T16", loadedProject?.manualOverrides?.get("POT 35X50_rebar"))

        // Test persistance des fondations (Semelles & Radier)
        val jId = "PROJ_BOSQUET_001_Joint A - Bosquet"
        val ftgId = dbManager.saveFoundationDesign(
            jointId = jId,
            foundationType = "RAFT_FOUNDATION",
            dimensionsSummary = "Radier Général h = 50 cm, Débord = 50 cm",
            soilStress = 0.59,
            allowableStress = 2.00,
            compliant = true,
            rebar = "2 nappes T14 e=15cm"
        )
        assertNotNull(ftgId)
        val ftgRecords = dbManager.getFoundationDesigns(jId)
        assertEquals(1, ftgRecords.size)
        assertEquals("RAFT_FOUNDATION", ftgRecords[0].foundationType)
        assertTrue(ftgRecords[0].isCompliant)

        // =========================================================================
        // ÉTAPE 8 : MÉTRÉ & DEVIS QUANTITATIF ESTIMATIF (DQE) AVEC EXCEL (.XLSX)
        // =========================================================================
        val boqEngine = BillOfQuantitiesEngine()
        val boqReport = boqEngine.computeBoq(
            joint = joint,
            projectTitle = "Sûreté Urbaine Bosquet",
            wilaya = "Alger"
        )

        assertNotNull(boqReport)
        assertTrue(boqReport.totalStructuralConcreteM3 > 50.0, "Le volume de béton armé doit être significatif")
        assertTrue(boqReport.totalRebarWeightKg > 5000.0, "Le poids des aciers HA doit être significatif")
        // Le ratio global d'acier doit être conforme aux ratios algériens standards (100 - 135 kg/m³)
        assertTrue(
            boqReport.globalSteelRatioKgPerM3 in 95.0..140.0,
            "Le ratio moyen d'acier (${boqReport.globalSteelRatioKgPerM3} kg/m³) doit être dans la fourchette normative 95-140 kg/m³"
        )
        assertTrue(boqReport.totalEstimatedCostDzd > 1_000_000.0, "Le devis estimatif doit refléter les prix du marché")

        val excelFile = File(tempDir, "DQE_SURETE_URBAINE_BOSQUET_JOINT_A.xlsx")
        boqEngine.exportToExcel(boqReport, excelFile)
        assertTrue(excelFile.exists(), "Le fichier Excel DQE .xlsx doit être créé")
        assertTrue(excelFile.length() > 4000, "Le classeur Excel doit contenir les deux feuilles de métré et devis")
    }
}
