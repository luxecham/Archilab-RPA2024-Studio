package com.archilab.rpa2024.testing

import com.archilab.rpa2024.cad.CadProjectInfo
import com.archilab.rpa2024.cad.DxfCoreEngine
import com.archilab.rpa2024.cad.FormworkPlanDxfExporter
import com.archilab.rpa2024.cad.FormworkPlanRequest
import com.archilab.rpa2024.cad.StructuralDetailingDxfExporter
import com.archilab.rpa2024.cad.DetailingPlanRequest
import com.archilab.rpa2024.engine.foundations.BaelTSectionSolver
import com.archilab.rpa2024.engine.foundations.BaelTInput
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.BaelSectionInput
import com.archilab.rpa2024.engine.frames.BaelSituation
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.persistence.BillOfQuantitiesEngine
import com.archilab.rpa2024.persistence.BillOfQuantitiesRequest
import com.archilab.rpa2024.persistence.CalculationRecord
import com.archilab.rpa2024.persistence.JointArchive
import com.archilab.rpa2024.persistence.ManualRebarOverride
import com.archilab.rpa2024.persistence.ProjectDatabaseManager
import com.archilab.rpa2024.persistence.QuantityElementType
import com.archilab.rpa2024.persistence.RebarElementType
import com.archilab.rpa2024.persistence.StoredProject
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.NoteDeCalculBundle
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Suite d'homologation finale : ingere le joint A, execute les noyaux RPA/BAEL,
 * reconstruit la note Word, exporte les plans DXF, calcule le DQE, puis verifie la
 * persistance SQLite multi-joints avec reprise des aciers manuels.
 */
class EndToEndIntegrationTestSuite {

    private val outputDirectory: Path = Path.of("build", "homologation")

    @Test
    fun `ingestion ETABS et chaine de calcul RPA BAEL`() {
        val imported = SampleJointA.load()
        val joint = imported.model.joint
        assertTrue(imported.model.points.isNotEmpty())
        assertTrue(imported.model.frames.isNotEmpty())
        assertTrue(imported.model.areas.isNotEmpty())
        assertEquals(SampleJointA.JOINT_NAME, joint.jointName)
        assertTrue(joint.storySystem.numStoriesN >= 3)
        assertTrue(joint.gridSystem.numBaysX >= 1)
        assertTrue(joint.gridSystem.numBaysY >= 1)

        val seismic = Rpa2024SeismicEngine().assess(joint, imported.results)
        assertEquals(2, seismic.baseShear.size, "Les deux directions doivent être contrôlées.")
        assertTrue(seismic.modalParticipation.modeCount >= 3)
        seismic.baseShear.forEach { shear ->
            assertNotNull(shear.staticBaseShearKN)
            assertNotNull(shear.dynamicBaseShearKN)
            assertTrue((shear.staticBaseShearKN ?: 0.0) > 0.0)
            assertTrue((shear.dynamicBaseShearKN ?: 0.0) > 0.0)
            assertTrue(shear.status != CheckStatus.FAILED)
        }
        assertTrue(seismic.columnReducedNormalForce.isNotEmpty())
        assertTrue(seismic.columnReducedNormalForce.all { it.nu <= 0.35 + 1e-9 })

        val bealR = BaelRectangularSolver().solve(
            BaelSectionInput(
                bCm = 40.0,
                hCm = 90.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                muGKNm = 313.0,
                situation = BaelSituation.ACCIDENTAL
            ),
            joint.materials
        )
        assertEquals(7.57, bealR.bottomSteelCm2, 0.05)
        assertEquals(0.057, bealR.neutralAxisMeters, 0.005)

        val bealT = BaelTSectionSolver().solve(
            BaelTInput(
                bMeters = 2.0,
                b0Meters = 0.5,
                hMeters = 0.9,
                h0Meters = 0.4,
                muKNm = 126.0
            )
        )
        assertTrue(bealT.caseLabel.contains("Cas 1"))
        assertEquals(2.97, bealT.aInfCm2, 0.02)
        assertTrue(bealT.momentTableKNm > bealT.designMomentKNm)
    }

    @Test
    fun `note Word OMML avec signets complets et sans reference cassee`() {
        Files.createDirectories(outputDirectory)
        val document = buildNoteDeCalcul().document
        val docx = CtcWordReportGenerator().write(
            document,
            outputDirectory.resolve("NDC_JOINT_A_HOMOLOGATION.docx")
        )
        assertTrue(Files.exists(docx))
        assertTrue(Files.size(docx) > 200_000L)

        XWPFDocument(FileInputStream(docx.toFile())).use { word ->
            val xml = word.document.xmlText()
            assertTrue(xml.contains("TOC \\o"), "Le champ de table des matières doit être présent.")
            assertTrue(xml.contains("m:oMath"), "Les équations doivent être natives OMML.")
            assertTrue(word.paragraphs.count { it.styleID == "Heading1" } >= 13)
            assertTrue(word.allPictures.isNotEmpty())
        }

        ZipFile(docx.toFile()).use { zip ->
            val entry = zip.getEntry("word/document.xml")
            assertNotNull(entry)
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val xmlDocument = factory.newDocumentBuilder().parse(zip.getInputStream(entry))
            val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            val starts = xmlDocument.getElementsByTagNameNS(wNs, "bookmarkStart")
            val ends = xmlDocument.getElementsByTagNameNS(wNs, "bookmarkEnd")
            val startIds = (0 until starts.length).map {
                (starts.item(it) as Element).getAttributeNS(wNs, "id")
            }
            val endIds = (0 until ends.length).map {
                (ends.item(it) as Element).getAttributeNS(wNs, "id")
            }
            val names = (0 until starts.length).map {
                (starts.item(it) as Element).getAttributeNS(wNs, "name")
            }
            assertTrue(startIds.isNotEmpty())
            assertEquals(startIds.size, startIds.distinct().size, "Les identifiants de signets doivent être uniques.")
            assertEquals(names.size, names.distinct().size, "Les noms de signets doivent être uniques.")
            assertEquals(startIds.size, endIds.size, "Chaque signet doit posséder une fin.")
            assertEquals(startIds.toSet(), endIds.toSet(), "Aucun signet ne doit être orphelin.")

            val links = xmlDocument.getElementsByTagNameNS(wNs, "hyperlink")
            val anchors = (0 until links.length).mapNotNull {
                (links.item(it) as Element).getAttributeNS(wNs, "anchor").takeIf { anchor -> anchor.isNotBlank() }
            }
            anchors.forEach { anchor ->
                assertTrue(names.contains(anchor), "Lien interne cassé vers le signet $anchor.")
            }
        }

        val settingsXml = ZipFile(docx.toFile()).use { zip ->
            val settings = zip.getEntry("word/settings.xml")
            assertNotNull(settings)
            zip.getInputStream(settings).readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(settingsXml.contains("updateFields"))
    }

    @Test
    fun `plans DXF mette DQE et annexe de note de calcul`() {
        Files.createDirectories(outputDirectory)
        val imported = SampleJointA.load()
        val foundations = SampleSafeFoundation.load()
        val jointWithWall = enrichWithWall(imported.model.joint)
        val staircases = listOf(
            StaircaseGeometry(
                name = "Escalier principal",
                numberOfRisers = 22,
                numberOfFlights = 2,
                flightWidthMeters = 1.20,
                landingLengthMeters = 1.20
            )
        )
        val boq = BillOfQuantitiesEngine().calculate(
            BillOfQuantitiesRequest(
                joint = jointWithWall,
                projectTitle = "1500 LLV BOSQUET",
                results = imported.results,
                model = imported.model,
                foundationModel = foundations,
                staircaseGeometries = staircases
            )
        )
        assertTrue(boq.lines.isNotEmpty())
        assertTrue(boq.concreteTotalM3 > 0.0)
        assertTrue(boq.formworkTotalM2 > 0.0)
        assertTrue(boq.steelTotalKg > 0.0)
        assertTrue(boq.floorAreaM2 > 0.0)
        assertTrue(boq.steelRatioKgM3 > 0.0)
        assertTrue(boq.steelByDiameterKg.isNotEmpty())
        assertTrue(boq.totals.meshSteelKg > 0.0, "Le treillis soudé de table doit être métré.")
        val types = boq.totalsByType().keys
        listOf(
            QuantityElementType.COLUMN,
            QuantityElementType.BEAM,
            QuantityElementType.WALL,
            QuantityElementType.SLAB,
            QuantityElementType.PARAPET,
            QuantityElementType.STAIRCASE,
            QuantityElementType.FOUNDATION,
            QuantityElementType.LEAN_CONCRETE
        ).forEach { type ->
            assertTrue(type in types, "Poste DQE manquant : ${type.label}.")
        }
        assertEquals(
            boq.steelTotalKg,
            boq.totals.steelByDiameterKg.values.sum() + boq.totals.meshSteelKg,
            1e-6
        )
        assertEquals(
            boq.concreteTotalM3,
            boq.totalsByType().values.sumOf { it.concreteVolumeM3 },
            1e-6
        )

        val xlsx = boq.writeXlsx(outputDirectory.resolve("DQE_JOINT_A_HOMOLOGATION.xlsx"))
        assertTrue(Files.exists(xlsx))
        XSSFWorkbook(Files.newInputStream(xlsx)).use { workbook ->
            assertEquals("DQE", workbook.getSheetAt(0).sheetName)
            assertNotNull(workbook.getSheet("Récapitulatif"))
            assertNotNull(workbook.getSheet("Aciers par diamètre"))
            assertTrue(workbook.getSheet("DQE").lastRowNum > boq.lines.size)
        }

        val note = buildNoteDeCalcul()
        val withDqe = boq.appendTo(note.document, 15)
        assertTrue(withDqe.annexes.any { it.number == 15 })
        val dqeChapter = withDqe.annexes.first { it.number == 15 }
        assertTrue(dqeChapter.blocks.any { it is ReportBlock.Table })
        assertEquals(CheckStatus.OK, dqeChapter.status)

        val projectInfo = CadProjectInfo(
            projectTitle = "1500 LLV BOSQUET",
            jointName = imported.model.jointName,
            wilaya = "Sidi Bel Abbès",
            client = "Direction du Logement",
            engineer = "Archilab",
            reference = "PLANS-JOINT-A-RPA2024",
            revision = "A"
        )
        val dxf = DxfCoreEngine("HOMOLOGATION_JOINT_A")
        val formwork = FormworkPlanDxfExporter().draw(
            dxf,
            FormworkPlanRequest(
                model = imported.model,
                project = projectInfo,
                includeFoundationPlan = false
            )
        )
        assertTrue(formwork.sheetCount >= 4)
        val detailing = StructuralDetailingDxfExporter().draw(
            dxf,
            DetailingPlanRequest(
                model = imported.model,
                results = imported.results,
                foundationModel = foundations,
                staircaseGeometries = staircases,
                project = projectInfo
            ),
            originY = -(formwork.heightMm + 120.0)
        )
        assertTrue(detailing.sheetCount >= 8)
        assertTrue(detailing.schedule.items.isNotEmpty())
        val dxfPath = outputDirectory.resolve("PLANS_EXECUTION_JOINT_A_HOMOLOGATION.dxf")
        dxf.write(dxfPath)
        val dxfText = Files.readString(dxfPath)
        listOf("HEADER", "TABLES", "BLOCKS", "ENTITIES", "EOF").forEach { section ->
            assertTrue(dxfText.contains("\n$section\n"), "Section DXF manquante : $section.")
        }
        assertTrue(dxfText.contains("FERRAILLAGE POTEAU"))
        assertTrue(dxfText.contains("DIMENSION"))
        assertTrue(dxfText.contains("HATCH"))
        assertTrue(Files.size(dxfPath) > 250_000L)
    }

    @Test
    fun `persistance SQLite multi-joints et reprise des aciers manuels`() {
        Files.createDirectories(outputDirectory)
        val databaseFile = outputDirectory.resolve("PROJETS_ARCHILAB.sqlite")
        Files.deleteIfExists(databaseFile)
        val imported = SampleJointA.load()
        val jointA = imported.model.joint
        val jointB = jointA.copy(jointName = "JOINT B")
        val columnOverride = ManualRebarOverride(
            elementType = RebarElementType.COLUMN,
            familyName = "POT35x55",
            adoptedRebar = "4T16",
            axialKN = 900.0,
            utilization = 0.281,
            notes = "Choix manuel de l'ingénieur"
        )
        val beamOverride = ManualRebarOverride(
            elementType = RebarElementType.BEAM,
            familyName = "B30x40",
            adoptedRebar = "3T16",
            secondaryRebar = "3T14",
            notes = "Chapeaux et nappe inférieure"
        )
        val initialCalculation = CalculationRecord(
            chapterId = 9,
            title = "Analyse sismique",
            jsonPayload = """{"V_MSE_X":1884.51,"V_MDS_X":1950.0,"nu_max":0.299}"""
        )
        val archive = StoredProject(
            title = "1500 LLV BOSQUET",
            wilaya = "Sidi Bel Abbès",
            engineerName = "Archilab — Bureau d'études structures",
            clientName = "Direction du Logement",
            reference = "PRJ-1500LLV-RPA2024",
            joints = listOf(
                JointArchive(
                    joint = jointA,
                    loads = mapOf("G" to "1.35", "Q" to "1.50", "psi" to "0.20"),
                    manualRebars = listOf(columnOverride, beamOverride),
                    calculations = listOf(initialCalculation)
                ),
                JointArchive(joint = jointB)
            )
        )

        var manager: ProjectDatabaseManager? = ProjectDatabaseManager.open(databaseFile)
        val projectId: Long
        try {
            projectId = manager!!.saveProject(archive)
            assertTrue(projectId > 0)
            val summaries = manager!!.listProjects()
            assertEquals(1, summaries.size)
            assertEquals(2, summaries.single().jointCount)
            manager!!.appendCalculation(
                projectId = projectId,
                jointName = "JOINT A",
                chapterId = 15,
                title = "Métré DQE",
                jsonPayload = """{"beton_m3":128.5,"acier_kg":14520.0}"""
            )
            manager!!.close()
            manager = null

            DriverManager.getConnection("jdbc:sqlite:${databaseFile.toAbsolutePath()}").use { connection ->
                connection.createStatement().use { statement ->
                    val tables = mutableSetOf<String>()
                    statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rows ->
                        while (rows.next()) tables += rows.getString(1)
                    }
                    listOf(
                        "Projects", "StructuralJoints", "StoriesTable", "ColumnFamiliesTable",
                        "BeamFamiliesTable", "ShearWallsTable", "CalculationResultsTable"
                    ).forEach { required -> assertTrue(required in tables, "Table SQLite absente : $required.") }
                    statement.executeQuery(
                        "SELECT rebar_adopted FROM ColumnFamiliesTable WHERE family_name='POT35x55'"
                    ).use { rows ->
                        assertTrue(rows.next())
                        assertEquals("4T16", rows.getString(1))
                    }
                    statement.executeQuery("SELECT COUNT(*) FROM CalculationResultsTable").use { rows ->
                        assertTrue(rows.next())
                        assertEquals(2, rows.getInt(1))
                    }
                }
            }

            manager = ProjectDatabaseManager.open(databaseFile)
            val loaded = manager!!.loadProject(projectId)
            assertNotNull(loaded)
            loaded!!
            assertEquals("1500 LLV BOSQUET", loaded.title)
            assertEquals("Sidi Bel Abbès", loaded.wilaya)
            assertEquals("Archilab — Bureau d'études structures", loaded.engineerName)
            assertEquals(2, loaded.joints.size)
            assertEquals("JOINT A", loaded.joints[0].joint.jointName)
            assertEquals("JOINT B", loaded.joints[1].joint.jointName)
            assertEquals(jointA.gridSystem.xGrids.size, loaded.joints[0].joint.gridSystem.xGrids.size)
            assertEquals(jointA.gridSystem.yGrids.size, loaded.joints[0].joint.gridSystem.yGrids.size)
            assertEquals(jointA.storySystem.stories.size, loaded.joints[0].joint.storySystem.stories.size)
            assertEquals(jointA.sectionCatalog.columns.size, loaded.joints[0].joint.sectionCatalog.columns.size)
            assertEquals(jointA.sectionCatalog.beams.size, loaded.joints[0].joint.sectionCatalog.beams.size)
            assertEquals(jointA.materials, loaded.joints[0].joint.materials)
            assertEquals(jointA.seismicParams, loaded.joints[0].joint.seismicParams)
            assertEquals(3, loaded.joints[0].loads.size)
            assertEquals(2, loaded.joints[0].manualRebars.size)
            val restoredColumn = loaded.joints[0].manualRebars.first {
                it.elementType == RebarElementType.COLUMN && it.familyName == "POT35x55"
            }
            assertEquals("4T16", restoredColumn.adoptedRebar)
            assertEquals(900.0, restoredColumn.axialKN ?: 0.0, 1e-9)
            assertEquals(0.281, restoredColumn.utilization ?: 0.0, 1e-9)
            val restoredBeam = loaded.joints[0].manualRebars.first {
                it.elementType == RebarElementType.BEAM && it.familyName == "B30x40"
            }
            assertEquals("3T16", restoredBeam.adoptedRebar)
            assertEquals("3T14", restoredBeam.secondaryRebar)
            assertEquals(2, loaded.joints[0].calculations.size)
            assertTrue(loaded.joints[0].calculations.any { it.chapterId == 9 })
            assertTrue(loaded.joints[0].calculations.any { it.chapterId == 15 })

            val renamed = loaded.copy(title = "1500 LLV BOSQUET — Révision B")
            manager!!.saveProject(renamed)
            val reloaded = manager!!.loadProject(projectId)
            assertNotNull(reloaded)
            assertEquals("1500 LLV BOSQUET — Révision B", reloaded!!.title)
            assertEquals(2, reloaded.joints.size)
            assertTrue(manager!!.deleteProject(projectId))
            assertNull(manager!!.loadProject(projectId))
        } finally {
            manager?.close()
        }
    }

    private fun buildNoteDeCalcul(): com.archilab.rpa2024.report.NoteDeCalculArtifacts {
        val imported = SampleJointA.load()
        val foundations = SampleSafeFoundation.load()
        return NoteDeCalculBundle().build(
            NoteDeCalculRequest(
                model = imported.model,
                results = imported.results,
                foundations = foundations,
                projectTitle = "1500 LLV BOSQUET",
                wilaya = "Sidi Bel Abbès",
                client = "Direction du Logement — Sidi Bel Abbès",
                engineer = "Archilab — Bureau d'études structures",
                reference = "NDC-JOINT-A-RPA2024",
                revision = "A"
            )
        )
    }

    private fun enrichWithWall(joint: StructuralJoint): StructuralJoint {
        val wall = ShearWallFamily(
            pierName = "V1",
            direction = AxisDirection.X,
            lengthLwMeters = 7.50,
            thicknessBwMeters = 0.20,
            clearHeightHeMeters = 3.74,
            totalHeightHwMeters = 14.96,
            stories = listOf("N1", "N2", "N3"),
            elementIds = listOf("WALL_JOINT_A")
        )
        return joint.copy(
            sectionCatalog = joint.sectionCatalog.copy(
                shearWalls = joint.sectionCatalog.shearWalls + wall
            )
        )
    }
}
