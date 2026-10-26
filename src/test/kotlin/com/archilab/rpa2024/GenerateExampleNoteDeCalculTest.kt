package com.archilab.rpa2024

import com.archilab.rpa2024.audit.CtcComplianceAuditorAndAutoFixer
import com.archilab.rpa2024.cad.BarBendingScheduleDxfGenerator
import com.archilab.rpa2024.cad.DxfCoreEngine
import com.archilab.rpa2024.cad.FormworkPlanDxfExporter
import com.archilab.rpa2024.cad.StructuralDetailingDxfExporter
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.*
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import com.archilab.rpa2024.persistence.BillOfQuantitiesEngine
import com.archilab.rpa2024.persistence.ProjectDatabaseManager
import com.archilab.rpa2024.persistence.ProjectEntityData
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.PdfReportExporter
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Test exécutable pour générer directement la Note de Calcul officielle complète (.docx),
 * la synthèse PDF (.pdf), le Devis DQE Excel (.xlsx) et les plans CAO (.dxf)
 * dans le dossier Téléchargements de l'utilisateur, conformément au modèle de référence
 * "Sûreté Urbaine 1500 LLV Bosquet - Joint A".
 */
class GenerateExampleNoteDeCalculTest {

    @Test
    fun generateFullDeliverablesForUser() {
        val userHome = System.getProperty("user.home")
        val downloadsDir = File(userHome, "Downloads")
        downloadsDir.mkdirs()

        // 1. Modèle ETABS complet du Joint A (Projet Sûreté Urbaine Bosquet)
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
              WALL "V1" THICKNESS 0.20
              WALL "V2" THICKNESS 0.20
              
            $ LINE CONNECTIVITIES
              LINE "C1" I "1" J "2"
              LINE "B1" I "2" J "3"
              
            $ LINE ASSIGNS
              LINE "C1" SECTION "POT 35X50"
              LINE "B1" SECTION "P.Prin 30x55"
              
            $ END OF FILE
        """.trimIndent()

        val ingestor = EtabsUniversalIngestor()
        val joint = ingestor.parseModelE2k(e2kContent, "Joint A")

        // 2. Génération de la Note de Calcul Word (.docx) officielle avec 13 chapitres, tableaux et captures graphiques
        val wordFile = File(downloadsDir, "NOTE_DE_CALCUL_JOINT_A_RPA2024.docx")
        val wordGen = CtcWordReportGenerator()
        wordGen.generateWordReport(
            joint = joint,
            outputFile = wordFile,
            projectName = "CONSTRUCTION DU SIÈGE DE LA SÛRETÉ URBAINE (1500 LLV BOSQUET)",
            clientName = "DIRECTION DU LOGEMENT ET DES ÉQUIPEMENTS PUBLICS (DLEP)",
            engineerName = "Ing. Structure Expert ArchiLab"
        )
        assertTrue(wordFile.exists(), "Le fichier Word (.docx) doit être généré")
        println("✔ Note de Calcul Word générée : ${wordFile.absolutePath} (${wordFile.length() / 1024} Ko)")

        // 3. Génération du Rapport Exécutif PDF (.pdf) conforme CTC
        val pdfFile = File(downloadsDir, "SYNTHESE_SISMIQUE_JOINT_A_CTC.pdf")
        val pdfGen = PdfReportExporter()
        pdfGen.exportExecutivePdf(
            joint = joint,
            outputPdfFile = pdfFile,
            projectName = "SÛRETÉ URBAINE 1500 LLV BOSQUET - JOINT A"
        )
        assertTrue(pdfFile.exists(), "Le fichier PDF doit être généré")
        println("✔ Rapport PDF exécutif généré : ${pdfFile.absolutePath} (${pdfFile.length() / 1024} Ko)")

        // 4. Génération du Devis Quantitatif et Estimatif (DQE) Excel (.xlsx) avec métré détaillé et ventilation des aciers
        val excelFile = File(downloadsDir, "DEVIS_DQE_ET_METRE_JOINT_A.xlsx")
        val boqEngine = BillOfQuantitiesEngine()
        val boqReport = boqEngine.computeBoq(
            joint = joint,
            projectTitle = "Sûreté Urbaine 1500 LLV Bosquet - Joint A",
            wilaya = "Mostaganem"
        )
        boqEngine.exportToExcel(boqReport, excelFile)
        assertTrue(excelFile.exists(), "Le classeur Excel DQE doit être généré")
        println("✔ Devis DQE Excel généré : ${excelFile.absolutePath} (${excelFile.length() / 1024} Ko)")

        // 5. Génération des Plans d'Exécution CAO AutoCAD (.dxf) avec coffrage, ferraillage et nomenclature BBS
        val dxfFile = File(downloadsDir, "PLANS_EXECUTION_AUTOCAD_JOINT_A.dxf")
        val masterDxf = DxfCoreEngine()
        val fwExp = FormworkPlanDxfExporter()
        val detExp = StructuralDetailingDxfExporter()
        val bbsGen = BarBendingScheduleDxfGenerator()

        fwExp.drawFloorFormworkPlan(masterDxf, joint, 0.0, 0.0, 1, "Sûreté Urbaine Bosquet", "Mostaganem")
        fwExp.drawFoundationFormworkPlan(masterDxf, joint, 35.0, 0.0, "Sûreté Urbaine Bosquet", "Mostaganem")
        detExp.drawColumnDetailingSheet(masterDxf, joint, 0.0, -25.0, "Sûreté Urbaine Bosquet", "Mostaganem")
        detExp.drawBeamDetailingSheet(masterDxf, joint, 26.0, -25.0, "Sûreté Urbaine Bosquet", "Mostaganem")
        detExp.drawShearWallDetailingSheet(masterDxf, joint, 56.0, -25.0, "Sûreté Urbaine Bosquet", "Mostaganem")
        detExp.drawSecondaryAndFootingDetailingSheet(masterDxf, joint, 84.0, -25.0, "Sûreté Urbaine Bosquet", "Mostaganem")

        detExp.drawBendingScheduleSheet(masterDxf, joint, 112.0, -25.0, "Sûreté Urbaine Bosquet", "Mostaganem")
        masterDxf.saveToFile(dxfFile)
        assertTrue(dxfFile.exists(), "Le fichier DXF doit être généré")
        println("✔ Plans CAO AutoCAD (.dxf) générés : ${dxfFile.absolutePath} (${dxfFile.length() / 1024} Ko)")

        // 6. Persistance SQLite du projet complet
        val dbFile = File(downloadsDir, "SURETE_URBAINE_BOSQUET.db")
        val dbManager = ProjectDatabaseManager(dbFile)
        val proj = ProjectEntityData(
            id = "PROJ_BOSQUET_JOINT_A",
            title = "Sûreté Urbaine 1500 LLV Bosquet",
            wilaya = "Mostaganem",
            engineerName = "Ing. Structure Expert ArchiLab",
            joints = listOf(joint)
        )
        dbManager.saveProject(proj)
        println("✔ Base de données SQLite persistée : ${dbFile.absolutePath}")

        // 7. Validation finale par le Super-Orchestrateur Zero-Click Studio
        val studio = com.archilab.rpa2024.engine.ArchilabOneClickStudio()
        val oneClickResult = studio.executeZeroClickPipeline(
            inputModel = joint,
            outputDirectory = downloadsDir,
            projectName = "CONSTRUCTION DU SIÈGE DE LA SÛRETÉ URBAINE (1500 LLV BOSQUET)",
            wilaya = "Mostaganem"
        )
        assertTrue(oneClickResult.success, "Le Super-Orchestrateur Zéro-Clic doit valider 100% de la chaîne de production")
        println("🚀 SUPER-ORCHESTRATEUR ZÉRO-CLIC ARCHILAB VALIDÉ EN ${oneClickResult.executionTimeMs} ms.")
    }
}
