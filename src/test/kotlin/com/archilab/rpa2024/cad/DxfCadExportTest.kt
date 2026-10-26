package com.archilab.rpa2024.cad

import com.archilab.rpa2024.bridge.EtabsToSafeFoundationBridge
import com.archilab.rpa2024.bridge.FoundationSoilInput
import com.archilab.rpa2024.bridge.SafeBridgeInput
import com.archilab.rpa2024.bridge.SafeFoundationGeneration
import com.archilab.rpa2024.engine.foundations.FoundationGeotechnicalParameters
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.parser.AreaElement
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.EtabsSection
import com.archilab.rpa2024.parser.EtabsSectionKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import com.archilab.rpa2024.parser.RawGeometry
import com.archilab.rpa2024.parser.RawMaterial
import com.archilab.rpa2024.parser.RawStory
import com.archilab.rpa2024.parser.UnitSystem
import com.archilab.rpa2024.sample.SampleJointA
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.hypot

/**
 * Validation de bout en bout de la Partie 8 : un seul fichier DXF contient les plans
 * de coffrage de tous les niveaux et des fondations, les planches de ferraillage de
 * toutes les familles, l'acrotere, l'escalier, les semelles et la nomenclature.
 */
class DxfCadExportTest {

    @Test
    fun `les longueurs developpees integrent les mandrins et les crochets`() {
        val unitWeight = BarDevelopedLengthCalculator.unitWeightKgPerMeter(12)
        assertEquals(12.0 * 12.0 / 162.0, unitWeight, 1e-12)

        val hook90 = BarDevelopedLengthCalculator.hookLengthMeters(12, 90.0)
        assertEquals(3.5 * 0.012 * Math.PI / 2.0 + 0.120, hook90, 1e-12)
        val hooked = BarDevelopedLengthCalculator.barWithEndHooks(1.50, 12, endCount = 2)
        assertEquals(1.50 + 2.0 * hook90, hooked, 1e-12)

        val stirrup = BarDevelopedLengthCalculator.closedStirrupLengthMeters(0.30, 0.40, 8)
        val expectedStirrup = 2.0 * (0.30 + 0.40) +
            2.0 * BarDevelopedLengthCalculator.hookLengthMeters(8, 135.0)
        assertEquals(expectedStirrup, stirrup, 1e-12)
        assertTrue(stirrup > 2.0 * (0.30 + 0.40))

        val count = BarDevelopedLengthCalculator.stirrupCount(
            totalLengthMeters = 6.0,
            firstStirrupFromSupportMeters = 0.05,
            nodalZoneLengthMeters = 1.10,
            nodalSpacingMeters = 0.10,
            currentSpacingMeters = 0.20
        )
        assertTrue(count > 20)
    }

    @Test
    fun `la planche execution du Joint A est syntaxiquement complete`() {
        val imported = SampleJointA.load()
        val model = enrichWithShearWall(imported.model)
        val project = CadProjectInfo(
            projectTitle = "1500 LLV BOSQUET",
            jointName = model.jointName,
            wilaya = "Sidi Bel Abbes",
            client = "OPGI Sidi Bel Abbes",
            engineer = "Bureau d'etudes Archilab",
            reference = "PLANS-EXECUTION-${model.jointName.replace(' ', '-')}",
            revision = "A"
        )
        val generation = buildFoundationGeneration(model)
        val foundationModel = toFoundationModel(generation)
        val document = DxfCoreEngine("PLANS_EXECUTION_JOINT_A")

        val formwork = FormworkPlanDxfExporter().draw(
            document,
            FormworkPlanRequest(
                model = model,
                foundationGeneration = generation,
                project = project
            )
        )
        assertEquals(4, formwork.storySheets.size, "Les quatre niveaux doivent avoir un plan de coffrage.")
        assertNotNull(formwork.foundationSheet)
        assertTrue(formwork.foundationCount > 0)
        assertTrue(formwork.columnCount >= 15)
        assertTrue(formwork.beamCount >= 20)
        assertTrue(formwork.wallCount >= 1)
        assertTrue(formwork.slabCount >= 4)

        val detailingOriginY = -(120.0 + CadSheetFormat.A1.heightMm)
        val detailing = StructuralDetailingDxfExporter().draw(
            document,
            DetailingPlanRequest(
                model = model,
                results = imported.results,
                foundationModel = foundationModel,
                staircaseGeometries = listOf(
                    StaircaseGeometry(
                        name = "Escalier principal",
                        numberOfRisers = 22,
                        numberOfFlights = 2,
                        flightWidthMeters = 1.20,
                        landingLengthMeters = 1.20
                    )
                ),
                project = project,
                sheetFormat = CadSheetFormat.A1,
                startingSheetNumber = formwork.sheetCount + 1
            ),
            originX = 0.0,
            originY = detailingOriginY
        )
        assertEquals(3, detailing.columnCount)
        assertEquals(2, detailing.beamCount)
        assertEquals(1, detailing.wallCount)
        assertTrue(detailing.slabCount >= 1)
        assertEquals(1, detailing.staircaseCount)
        assertTrue(detailing.footingCount > 0)
        assertTrue(detailing.schedule.items.size >= 25)
        assertTrue(detailing.schedule.totalSteelWeightKg > 0.0)
        assertTrue(detailing.schedule.concreteVolumeM3 > 0.0)
        assertTrue(detailing.schedule.steelRatioKgM3 > 0.0)
        assertEquals(
            detailing.schedule.items.size,
            detailing.schedule.items.map { it.position }.distinct().size,
            "Chaque position de nomenclature doit etre unique."
        )
        assertEquals(
            detailing.schedule.items.size,
            detailing.schedule.items.map { it.mark }.distinct().size,
            "Chaque repere d'acier doit etre unique."
        )
        assertEquals(
            detailing.schedule.items.map { it.diameterMm }.distinct().sorted(),
            detailing.schedule.byDiameter().map { it.diameterMm }
        )
        val elementTypes = detailing.schedule.items.map { it.elementType }.toSet()
        listOf("Poteau", "Poutre", "Voile", "Poutrelle", "Acrotère", "Escalier", "Semelle").forEach {
            assertTrue(it in elementTypes, "Nomenclature incomplete pour $it.")
        }
        val titles = detailing.sheetTitles.joinToString(" | ")
        listOf("POTEAU", "POUTRE", "VOILE", "POUTRELLES", "ACROTERE", "ESCALIER", "SEMELLE").forEach {
            assertTrue(titles.contains(it), "La planche $it doit etre generee.")
        }

        val bbs = BarBendingScheduleDxfGenerator()
        val bbsOriginY = detailingOriginY - 120.0 - CadSheetFormat.A0.heightMm
        val bbsResult = bbs.draw(
            document = document,
            schedule = detailing.schedule,
            project = project,
            originY = bbsOriginY
        )
        assertTrue(bbsResult.sheetCount >= 1)
        assertEquals(detailing.schedule.items.size, bbsResult.rowCount)

        val directory = Path.of("build", "cad")
        val dxfPath = directory.resolve("PLANS_EXECUTION_JOINT_A.dxf")
        document.write(dxfPath)
        val xlsxPath = bbs.writeXlsx(detailing.schedule, project, directory.resolve("NOMENCLATURE_JOINT_A.xlsx"))

        assertTrue(Files.exists(dxfPath))
        assertTrue(Files.size(dxfPath) > 250_000L)
        val dxf = Files.readString(dxfPath)
        assertDxfSyntax(dxf)
        CadLayerStandard.all.forEach { layer ->
            assertTrue(dxf.contains(layer.name), "Calque manquant : ${layer.name}")
        }
        listOf("LINE", "LWPOLYLINE", "CIRCLE", "ARC", "HATCH", "TEXT", "MTEXT", "DIMENSION").forEach { type ->
            assertTrue(dxf.contains("\n$type\n"), "Entite DXF manquante : $type")
        }
        listOf(
            "PLAN DE COFFRAGE - NIVEAU N1",
            "PLAN DE COFFRAGE - FONDATIONS",
            "FERRAILLAGE POTEAU",
            "FERRAILLAGE POUTRE",
            "FERRAILLAGE VOILE",
            "FERRAILLAGE POUTRELLES",
            "FERRAILLAGE ACROTERE",
            "FERRAILLAGE ESCALIER",
            "FERRAILLAGE SEMELLE",
            "NOMENCLATURE ET FAÇONNAGE DES ACIERS",
            "Longueurs developpees calculees"
        ).forEach { marker ->
            assertTrue(dxf.contains(marker), "Marqueur de planche absent : $marker")
        }

        assertTrue(Files.exists(xlsxPath))
        XSSFWorkbook(Files.newInputStream(xlsxPath)).use { workbook ->
            assertEquals("Nomenclature", workbook.getSheetAt(0).sheetName)
            assertTrue(workbook.numberOfSheets >= 3)
            val positionsSheet = workbook.getSheet("Nomenclature")
            assertTrue(positionsSheet.lastRowNum >= detailing.schedule.items.size)
            val diametersSheet = workbook.getSheet("Aciers par diamètre")
            assertTrue(diametersSheet.lastRowNum >= detailing.schedule.byDiameter().size)
            val concreteSheet = workbook.getSheet("Béton et ratios")
            assertTrue(concreteSheet.lastRowNum >= detailing.schedule.concreteElements.size)
        }
    }

    private fun enrichWithShearWall(model: ParsedEtabsModel): ParsedEtabsModel {
        val lowerPoints = model.frames
            .filter { it.kind == com.archilab.rpa2024.parser.FrameKind.COLUMN }
            .mapNotNull { model.points[it.startNodeId] }
            .filter { kotlin.math.abs(it.zMeters) <= 1e-6 }
            .sortedWith(compareBy({ kotlin.math.abs(it.yMeters) }, { it.xMeters }))
        val first = lowerPoints.firstOrNull() ?: error("Aucun noeud de base pour l'acroche du voile.")
        val second = lowerPoints.firstOrNull { it.id != first.id && kotlin.math.abs(it.yMeters - first.yMeters) <= 1e-6 }
            ?: error("Aucun second noeud pour definir le voile.")
        val wallLength = hypot(second.xMeters - first.xMeters, second.yMeters - first.yMeters)
        val section = EtabsSection(
            name = "VOILE20",
            kind = EtabsSectionKind.WALL,
            materialName = "B25",
            thicknessCm = 20.0
        )
        val area = AreaElement(
            id = "WALL_N1_A_B",
            kind = AreaKind.WALL,
            nodeIds = listOf(first.id, second.id),
            propertyName = section.name,
            pierName = "V1",
            storyName = "N1"
        )
        val wall = ShearWallFamily(
            pierName = "V1",
            direction = AxisDirection.X,
            lengthLwMeters = wallLength,
            thicknessBwMeters = section.thicknessCm!! / 100.0,
            clearHeightHeMeters = 3.74,
            totalHeightHwMeters = 14.96,
            stories = listOf("N1", "N2", "N3"),
            elementIds = listOf(area.id)
        )
        val catalog = model.joint.sectionCatalog.copy(
            shearWalls = model.joint.sectionCatalog.shearWalls + wall
        )
        return model.copy(
            joint = model.joint.copy(sectionCatalog = catalog),
            areas = model.areas + area,
            sections = model.sections + section
        )
    }

    private fun buildFoundationGeneration(model: ParsedEtabsModel): SafeFoundationGeneration {
        val raw = RawGeometry(
            points = model.points,
            frames = model.frames,
            areas = model.areas,
            sections = model.sections,
            materials = listOf(
                RawMaterial(name = "B25", type = "Concrete", fc28MPa = 25.0),
                RawMaterial(name = "A500", type = "Rebar", fyMPa = 500.0)
            ),
            stories = model.joint.storySystem.orderedStories.map {
                RawStory(name = it.storyName, heightMeters = it.heightHk, elevationMeters = it.elevationZk)
            },
            gridsX = model.joint.gridSystem.xGrids,
            gridsY = model.joint.gridSystem.yGrids,
            pierNames = model.pierNames,
            units = UnitSystem.KN_M
        )
        return EtabsToSafeFoundationBridge().build(
            SafeBridgeInput(
                geometry = raw,
                soil = FoundationSoilInput(allowableStressKNM2 = 120.0, directSubgradeModulusKNM3 = 21_848.0),
                baseStory = "N1",
                concreteName = "B25",
                initialWidthMeters = 0.80,
                widthTiersMeters = listOf(0.80, 1.00, 1.20, 1.50, 1.80, 2.00),
                footingThicknessMeters = 0.40,
                supportedWallWidthMeters = 0.30
            )
        )
    }

    private fun toFoundationModel(generation: SafeFoundationGeneration): SafeFoundationModel {
        val families = generation.footings.map { it.family }.distinctBy { it.name }
        val springs = generation.footings.map { it.spring }.distinctBy { it.name }
        return SafeFoundationModel(
            families = families,
            springs = springs,
            displacements = emptyList(),
            displacementEnvelope = null,
            forceEnvelopes = emptyList(),
            materials = listOf(
                RawMaterial(name = "B25", type = "Concrete", fc28MPa = 25.0),
                RawMaterial(name = "A500", type = "Rebar", fyMPa = 500.0)
            ),
            geotechnical = FoundationGeotechnicalParameters(
                allowableStressKNM2 = 120.0,
                subgradeModulusKNM3 = generation.subgradeModulusKNM3
            ),
            units = UnitSystem.KN_M,
            sourceName = "SAFE_JOINT_A"
        )
    }

    private fun assertDxfSyntax(dxf: String) {
        val lines = dxf.lines().dropLastWhile { it.isEmpty() }
        assertTrue(lines.size % 2 == 0, "Chaque code DXF doit etre suivi d'une valeur.")
        val pairs = lines.chunked(2)
        assertTrue(pairs.all { it[0].trim().toIntOrNull() != null }, "Code de groupe DXF non numerique.")
        assertEquals("0", pairs.first()[0].trim())
        assertEquals("SECTION", pairs.first()[1].trim())
        listOf("HEADER", "TABLES", "BLOCKS", "ENTITIES").forEach { section ->
            val occurrences = pairs.count { pair ->
                pair[0].trim() == "2" && pair[1].trim() == section &&
                    pairs.indexOf(pair).let { index ->
                        index > 0 && pairs[index - 1][0].trim() == "0" && pairs[index - 1][1].trim() == "SECTION"
                    }
            }
            assertEquals(1, occurrences, "La section $section doit apparaitre une seule fois.")
        }
        assertEquals("0", pairs.last()[0].trim())
        assertEquals("EOF", pairs.last()[1].trim())
        assertTrue(pairs.count { it[0].trim() == "8" } > 100, "Les entites doivent etre affectees a des calques.")
    }
}
