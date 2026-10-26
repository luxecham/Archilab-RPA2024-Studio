package com.archilab.rpa2024

import com.archilab.rpa2024.cad.*
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.normative.SiteClass
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DxfCadExportTest {

    private fun createJointA(): StructuralJoint {
        val xGrids = listOf(
            GridLine("A", 0.0, AxisDirection.X),
            GridLine("B", 4.20, AxisDirection.X),
            GridLine("C", 8.40, AxisDirection.X),
            GridLine("D", 12.60, AxisDirection.X)
        )
        val yGrids = listOf(
            GridLine("1", 0.0, AxisDirection.Y),
            GridLine("2", 3.80, AxisDirection.Y),
            GridLine("3", 7.60, AxisDirection.Y),
            GridLine("4", 11.40, AxisDirection.Y)
        )
        val stories = listOf(
            StoryLevel("RDC", 3.20, 3.20),
            StoryLevel("Étage 1", 3.06, 6.26),
            StoryLevel("Étage 2", 3.06, 9.32),
            StoryLevel("Étage 3", 3.06, 12.38),
            StoryLevel("Étage 4", 3.06, 15.44)
        )
        val columns = listOf(
            ColumnFamily("P35X35", ColumnShape.RECTANGULAR, 35.0, 35.0, clearHeightLclMeters = 2.65),
            ColumnFamily("P40X40", ColumnShape.RECTANGULAR, 40.0, 40.0, clearHeightLclMeters = 2.65)
        )
        val beams = listOf(
            BeamFamily("PR30X55", BeamRole.MAIN_BEAM, 30.0, 55.0, maxClearSpanMeters = 4.20),
            BeamFamily("CH30X35", BeamRole.SECONDARY_TIE, 30.0, 35.0, maxClearSpanMeters = 3.80)
        )
        val walls = listOf(
            ShearWallFamily("V1", AxisDirection.X, 3.00, 0.20, 2.65, 15.44),
            ShearWallFamily("V2", AxisDirection.Y, 3.20, 0.20, 2.65, 15.44)
        )

        return StructuralJoint(
            jointName = "Joint A",
            gridSystem = GridSystem(xGrids, yGrids),
            storySystem = StorySystem(stories),
            sectionCatalog = SectionCatalog(columns, beams, walls),
            materials = MaterialConfig(
                concreteGrade = "C25/30",
                fc28MPa = 25.0,
                feMPa = 500.0,
                soilBearingCapacityBars = 1.50,
                soilSubgradeModulusKNm3 = 21848.0
            ),
            seismicParams = SeismicConfig(
                zone = SeismicZone.ZONE_III,
                site = SiteClass.S3,
                importanceGroup = ImportanceGroup.GROUP_1B,
                bracingSystem = BracingSystem.CAT_4
            )
        )
    }

    @Test
    fun testDxfCoreEngineSectionsAndSyntax() {
        val dxf = DxfCoreEngine()

        // Ajout d'entités représentatives
        dxf.addLine(0.0, 0.0, 10.0, 10.0, CadLayer.AXES)
        dxf.addRectangle(1.0, 1.0, 4.0, 5.0, CadLayer.COFFRAGE_POTEAUX)
        dxf.addCircle(5.0, 5.0, 0.40, CadLayer.BULLES_AXES)
        dxf.addArc(5.0, 5.0, 1.0, 0.0, 90.0, CadLayer.COTATIONS)
        dxf.addText(5.0, 5.0, 0.25, "TEST TEXT", CadLayer.TEXTES_ET_REPERES)
        dxf.addSolidHatch(
            listOf(CadPoint(0.0, 0.0), CadPoint(1.0, 0.0), CadPoint(1.0, 1.0), CadPoint(0.0, 1.0)),
            CadLayer.COFFRAGE_POTEAUX
        )
        dxf.addLinearDimension(0.0, 0.0, 4.0, 0.0, offset = -1.0, textOverride = "4.00 m")
        dxf.addLeaderWithBubble(0.0, 0.0, 1.0, 1.0, 0.30, "1", "4T14")

        val dxfContent = dxf.buildDxfString()
        assertNotNull(dxfContent)
        assertTrue(dxfContent.isNotEmpty())

        // 1. Vérification de la section HEADER
        assertTrue(dxfContent.contains("  0\nSECTION\n  2\nHEADER"))
        assertTrue(dxfContent.contains("\$ACADVER\n  1\nAC1024"))
        assertTrue(dxfContent.contains("\$INSUNITS\n 70\n6"))

        // 2. Vérification de la section TABLES et des calques
        assertTrue(dxfContent.contains("  0\nSECTION\n  2\nTABLES"))
        assertTrue(dxfContent.contains("  0\nTABLE\n  2\nLTYPE"))
        assertTrue(dxfContent.contains("  2\nCENTER"))
        assertTrue(dxfContent.contains("  2\nHIDDEN"))
        assertTrue(dxfContent.contains("  0\nTABLE\n  2\nLAYER"))

        // Tous les 11 calques normalisés doivent être présents
        for (layer in CadLayer.entries) {
            assertTrue(dxfContent.contains("  2\n${layer.layerName}"))
        }

        // 3. Vérification de la section BLOCKS
        assertTrue(dxfContent.contains("  0\nSECTION\n  2\nBLOCKS"))

        // 4. Vérification de la section ENTITIES
        assertTrue(dxfContent.contains("  0\nSECTION\n  2\nENTITIES"))
        assertTrue(dxfContent.contains("  0\nLINE"))
        assertTrue(dxfContent.contains("  0\nLWPOLYLINE"))
        assertTrue(dxfContent.contains("  0\nCIRCLE"))
        assertTrue(dxfContent.contains("  0\nARC"))
        assertTrue(dxfContent.contains("  0\nTEXT"))
        assertTrue(dxfContent.contains("  0\nHATCH"))
        assertTrue(dxfContent.contains("TEST TEXT"))

        // 5. Vérification du terminateur EOF
        assertTrue(dxfContent.endsWith("  0\nEOF\n"))
    }

    @Test
    fun testFormworkPlanDxfExporter(@TempDir tempDir: File) {
        val joint = createJointA()
        val exporter = FormworkPlanDxfExporter()
        val targetFile = File(tempDir, "PLAN_COFFRAGE_JOINT_A.dxf")

        val exportedFile = exporter.exportFormworkDxf(joint, targetFile)
        assertTrue(exportedFile.exists())
        assertTrue(exportedFile.length() > 2000)

        val content = exportedFile.readText()

        // 1. Présence des bulles d'axes et labels
        assertTrue(content.contains("A"))
        assertTrue(content.contains("B"))
        assertTrue(content.contains("1"))
        assertTrue(content.contains("4"))

        // 2. Présence des cotations périmétriques et hors-tout
        assertTrue(content.contains("Lx = 12.60 m"))
        assertTrue(content.contains("Ly = 11.40 m"))

        // 3. Présence des éléments structuraux
        assertTrue(content.contains("P1 (35x35)"))
        assertTrue(content.contains("PR30X55"))
        assertTrue(content.contains("CH30X35"))
        assertTrue(content.contains("V1"))
        assertTrue(content.contains("CC 16+5"))

        // 4. Présence du plan de fondations
        assertTrue(content.contains("SF120"))
        assertTrue(content.contains("SFJ200"))
        assertTrue(content.contains("PLAN DE FONDATIONS - SEMELLES FILANTES & NERVURES"))
        assertTrue(content.contains("PLAN DE COFFRAGE - Étage 1"))
    }

    @Test
    fun testStructuralDetailingDxfExporter(@TempDir tempDir: File) {
        val joint = createJointA()
        val exporter = StructuralDetailingDxfExporter()
        val targetFile = File(tempDir, "PLANS_FERRAILLAGE_EXECUTION.dxf")

        val exportedFile = exporter.exportAllDetailingDxf(joint, targetFile)
        assertTrue(exportedFile.exists())
        assertTrue(exportedFile.length() > 5000)

        val content = exportedFile.readText()

        // 1. Détails Poteaux RPA 2024 § 7.4.2
        assertTrue(content.contains("FERRAILLAGE POTEAUX"))
        assertTrue(content.contains("ZN Lcr="))
        assertTrue(content.contains("St=10"))
        assertTrue(content.contains("ZC="))
        assertTrue(content.contains("St'=15"))
        assertTrue(content.contains("Lrec=70cm"))
        assertTrue(content.contains("COUPE A-A (ECH. 1/20)"))
        assertTrue(content.contains("4T16 (Angles)"))

        // 2. Détails Poutres RPA 2024 § 7.5.2
        assertTrue(content.contains("FERRAILLAGE POUTRES & CHAÎNAGES"))
        assertTrue(content.contains("ZN: S=8cm"))
        assertTrue(content.contains("ZC: S'=15cm"))
        assertTrue(content.contains("COUPE EN TRAVÉE"))
        assertTrue(content.contains("COUPE SUR APPUI"))

        // 3. Détails Voiles RPA 2024 § 7.7.4
        assertTrue(content.contains("FERRAILLAGE VOILES DE CONTREVENTEMENT"))
        assertTrue(content.contains("COUPE HORIZONTALE VOILE"))
        assertTrue(content.contains("lc ="))
        assertTrue(content.contains("Âme ="))
        assertTrue(content.contains("Élément de rive : 6T14 + Cadres T8 e=8cm"))
        assertTrue(content.contains("Double nappe T12 e=15cm"))

        // 4. Détails Secondaires et Fondations
        assertTrue(content.contains("ACROTÈRE (H=60cm, T8 e=15cm)"))
        assertTrue(content.contains("POUTRELLE 16+5"))
        assertTrue(content.contains("SEMELLE FILANTE SF160"))
    }

    @Test
    fun testBarBendingScheduleCalculationAndDxf(@TempDir tempDir: File) {
        val joint = createJointA()
        val bbsGen = BarBendingScheduleDxfGenerator()

        // 1. Calcul de la nomenclature
        val report = bbsGen.computeBendingSchedule(joint)
        assertNotNull(report)
        assertTrue(report.items.size >= 12)
        assertTrue(report.totalSteelWeightKg > 1000.0)
        assertTrue(report.totalConcreteVolumeM3 > 50.0)
        assertTrue(report.steelRatioKgPerM3 in 60.0..140.0) // Ratio réaliste bâtiment génie civil algérien (~70 - 100 kg/m³)

        // Vérification des poids par diamètre
        assertTrue(report.weightsByDiameterKg.containsKey(8))
        assertTrue(report.weightsByDiameterKg.containsKey(12))
        assertTrue(report.weightsByDiameterKg.containsKey(14))
        assertTrue(report.weightsByDiameterKg.containsKey(16))

        // 2. Export DXF du tableau BBS
        val bbsFile = File(tempDir, "NOMENCLATURE_ACIERS_HA.dxf")
        bbsGen.exportBbsDxfFile(joint, bbsFile)
        assertTrue(bbsFile.exists())
        assertTrue(bbsFile.length() > 2000)

        val content = bbsFile.readText()
        assertTrue(content.contains("NOMENCLATURE GÉNÉRALE & MÉTRÉ DES ACIERS HA"))
        assertTrue(content.contains("POIDS TOTAL D'ACIER HA"))
        assertTrue(content.contains("RATIO MOYEN D'ARMATURES"))
        assertTrue(content.contains("SCHÉMA DE FAÇONNAGE"))
    }

    @Test
    fun testCompleteMasterExecutionPlanGeneration(@TempDir tempDir: File) {
        val joint = createJointA()
        val masterDxf = DxfCoreEngine()

        // A. Plan de coffrage à l'origine (0, 0)
        val formworkExporter = FormworkPlanDxfExporter()
        formworkExporter.drawFloorFormworkPlan(masterDxf, joint, 0.0, 0.0, 1, "Projet R+4 Test", "Alger")
        formworkExporter.drawFoundationFormworkPlan(masterDxf, joint, 35.0, 0.0, "Projet R+4 Test", "Alger")

        // B. Détails d'exécution ferraillage décalés en Y
        val detailingExporter = StructuralDetailingDxfExporter()
        detailingExporter.drawColumnDetailingSheet(masterDxf, joint, 0.0, -25.0, "Projet R+4 Test", "Alger")
        detailingExporter.drawBeamDetailingSheet(masterDxf, joint, 26.0, -25.0, "Projet R+4 Test", "Alger")
        detailingExporter.drawShearWallDetailingSheet(masterDxf, joint, 56.0, -25.0, "Projet R+4 Test", "Alger")
        detailingExporter.drawSecondaryAndFootingDetailingSheet(masterDxf, joint, 84.0, -25.0, "Projet R+4 Test", "Alger")

        // C. Tableau de Nomenclature & Métré d'aciers HA
        val bbsGen = BarBendingScheduleDxfGenerator()
        val bbsReport = bbsGen.computeBendingSchedule(joint)
        bbsGen.drawBendingScheduleTable(masterDxf, 110.0, -25.0, bbsReport, joint, "Projet R+4 Test", "Alger")

        val masterFile = File(tempDir, "PLANS_EXECUTION_JOINT_A.dxf")
        masterDxf.saveToFile(masterFile)

        assertTrue(masterFile.exists())
        assertTrue(masterFile.length() > 15000)

        val fullText = masterFile.readText()

        // Validation syntaxique stricte de toutes les sections
        val headerIdx = fullText.indexOf("  2\nHEADER")
        val tablesIdx = fullText.indexOf("  2\nTABLES")
        val blocksIdx = fullText.indexOf("  2\nBLOCKS")
        val entitiesIdx = fullText.indexOf("  2\nENTITIES")
        val eofIdx = fullText.indexOf("  0\nEOF")

        assertTrue(headerIdx >= 0)
        assertTrue(tablesIdx > headerIdx)
        assertTrue(blocksIdx > tablesIdx)
        assertTrue(entitiesIdx > blocksIdx)
        assertTrue(eofIdx > entitiesIdx)

        // Nombre de lignes de cote et de textes
        val numDimensions = Regex("01_AXES|03_COTATIONS").findAll(fullText).count()
        assertTrue(numDimensions > 50)
    }
}
