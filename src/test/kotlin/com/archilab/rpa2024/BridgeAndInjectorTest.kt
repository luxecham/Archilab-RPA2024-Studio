package com.archilab.rpa2024

import com.archilab.rpa2024.bridge.*
import com.archilab.rpa2024.engine.foundations.FootingType
import com.archilab.rpa2024.engine.foundations.FoundationSoilConfig
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.normative.SiteClass
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.abs

class BridgeAndInjectorTest {

    private fun createTestJoint(): StructuralJoint {
        val xGrids = listOf(
            GridLine("A", 0.0, AxisDirection.X),
            GridLine("B", 4.50, AxisDirection.X),
            GridLine("C", 9.00, AxisDirection.X)
        )
        val yGrids = listOf(
            GridLine("1", 0.0, AxisDirection.Y),
            GridLine("2", 4.00, AxisDirection.Y),
            GridLine("3", 8.00, AxisDirection.Y)
        )
        val stories = listOf(
            StoryLevel("RDC", 3.00, 3.00),
            StoryLevel("Etage 1", 3.00, 6.00),
            StoryLevel("Etage 2", 3.00, 9.00)
        )
        val columns = listOf(
            ColumnFamily("P35X35", ColumnShape.RECTANGULAR, 35.0, 35.0, clearHeightLclMeters = 2.60)
        )
        val beams = listOf(
            BeamFamily("PR30X50", BeamRole.MAIN_BEAM, 30.0, 50.0, maxClearSpanMeters = 4.50)
        )
        val walls = listOf(
            ShearWallFamily("V1", AxisDirection.X, 3.00, 0.20, 2.60, 9.00)
        )

        return StructuralJoint(
            jointName = "Joint Test Bridge",
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
                bracingSystem = BracingSystem.CAT_4,
                liveLoadCategory = LiveLoadCategory.HABITATION_BUREAUX
            )
        )
    }

    @Test
    fun testEtabsMaterialPropertiesGeneration() {
        val injector = EtabsLiveApiAndE2kInjector()
        val matBlock = injector.buildMaterialPropertiesBlock()

        // Vérification BETON25
        assertTrue(matBlock.contains("MATERIAL \"BETON25\"  TYPE \"CONCRETE\""))
        assertTrue(matBlock.contains("E 32164195"))
        assertTrue(matBlock.contains("U 0.20"))
        assertTrue(matBlock.contains("G 13401748"))
        assertTrue(matBlock.contains("UNITWT 25.00"))
        assertTrue(matBlock.contains("FC 25000"))

        // Vérification FE500 et FE235
        assertTrue(matBlock.contains("MATERIAL \"FE500\"  TYPE \"REBAR\""))
        assertTrue(matBlock.contains("FY 500000"))
        assertTrue(matBlock.contains("MATERIAL \"FE235\"  TYPE \"REBAR\""))
        assertTrue(matBlock.contains("FY 235000"))
    }

    @Test
    fun testEtabsMassSourceAndSpectrumGeneration() {
        val injector = EtabsLiveApiAndE2kInjector()
        val joint = createTestJoint()

        // 1. Source de masse WI = 1.00G + 0.20Q
        val massBlock = injector.buildMassSourceBlock(psi = 0.20)
        assertTrue(massBlock.contains("MASSSOURCE \"WI\""))
        assertTrue(massBlock.contains("LATERALMASS ONLY=YES"))
        assertTrue(massBlock.contains("LUMPMASS STORIES=YES"))
        assertTrue(massBlock.contains("MASSCOMBO \"DEAD\"  SF 1.00"))
        assertTrue(massBlock.contains("MASSCOMBO \"LIVE\"  SF 0.20"))

        // 2. Fonction spectrale RPA2024_SPEC
        val (specBlock, pointsCount) = injector.buildSpectrumFunctionBlock(joint)
        assertTrue(specBlock.contains("FUNCTION \"RPA2024_SPEC\"  TYPE \"SPECTRUM\""))
        assertTrue(specBlock.contains("DAMPING 0.07"))
        assertTrue(pointsCount > 50)
        assertTrue(specBlock.contains("POINT 0.0000"))

        // 3. Cas spectraux dynamiques EX et EY
        val casesBlock = injector.buildResponseSpectrumLoadCasesBlock(scaleFactorG = 9.81)
        assertTrue(casesBlock.contains("CASE \"EX\"  TYPE \"RESPONSE SPECTRUM\""))
        assertTrue(casesBlock.contains("DIR \"U1\"  FUNC \"RPA2024_SPEC\"  SF 9.8100"))
        assertTrue(casesBlock.contains("MODALCOMBO \"CQC\"  DIRCOMBO \"SRSS\"  ECCEN 0.05"))
        assertTrue(casesBlock.contains("CASE \"EY\"  TYPE \"RESPONSE SPECTRUM\""))
        assertTrue(casesBlock.contains("DIR \"U2\"  FUNC \"RPA2024_SPEC\"  SF 9.8100"))
    }

    @Test
    fun testEtabsRegulatoryCombinationsGeneration() {
        val injector = EtabsLiveApiAndE2kInjector()
        val comboBlock = injector.buildLoadCombinationsBlock(psi = 0.20)

        // Combinaisons de base
        assertTrue(comboBlock.contains("COMBO \"ELU\""))
        assertTrue(comboBlock.contains("CASE \"DEAD\"  SF 1.35"))
        assertTrue(comboBlock.contains("CASE \"LIVE\"  SF 1.50"))
        assertTrue(comboBlock.contains("COMBO \"ELS\""))
        assertTrue(comboBlock.contains("COMBO \"WI\""))

        // Combinaisons sismiques RPA 2024
        assertTrue(comboBlock.contains("COMBO \"COMB1\""))
        assertTrue(comboBlock.contains("COMBO \"COMB2\""))
        assertTrue(comboBlock.contains("COMBO \"COMB3\""))
        assertTrue(comboBlock.contains("COMBO \"COMB4\""))
        assertTrue(comboBlock.contains("COMBO \"COMB5\""))
        assertTrue(comboBlock.contains("COMBO \"COMB6\""))
        assertTrue(comboBlock.contains("COMBO \"COMB7\""))
        assertTrue(comboBlock.contains("COMBO \"COMB8\""))

        // Combinaisons orthogonales 100% + 30%
        assertTrue(comboBlock.contains("COMBO \"COMB_EX_EY_P\""))
        assertTrue(comboBlock.contains("CASE \"EX\"  SF 1.00"))
        assertTrue(comboBlock.contains("CASE \"EY\"  SF 0.30"))
    }

    @Test
    fun testCompleteE2kInjectionAndSpectrumExport(@TempDir tempDir: File) {
        val injector = EtabsLiveApiAndE2kInjector()
        val joint = createTestJoint()

        val sampleE2k = """
            ; ETABS MODEL FILE
            $ PROGRAM INFORMATION
              PROGRAM "ETABS"  VERSION "20.0"
            $ POINT COORDINATES
              POINT "1"  X 0.0  Y 0.0  Z 0.0
              POINT "2"  X 4.5  Y 0.0  Z 0.0
            $ STORIES
              STORY "RDC"  HEIGHT 3.0  ELEV 3.0
            $ END
        """.trimIndent()

        val result = injector.injectRpa2024ToE2k(sampleE2k, joint)
        assertTrue(result.success)
        assertEquals(3, result.injectedMaterialsCount)
        assertTrue(result.injectedMassSource)
        assertTrue(result.injectedSpectrumPointsCount > 50)
        assertEquals(2, result.injectedLoadCasesCount)
        assertTrue(result.injectedCombinationsCount >= 15)

        // Vérification de la persistance de la géométrie d'origine
        assertTrue(result.outputContent.contains("POINT \"1\"  X 0.0  Y 0.0  Z 0.0"))
        assertTrue(result.outputContent.contains("STORY \"RDC\"  HEIGHT 3.0  ELEV 3.0"))

        // Vérification de l'injection des blocs RPA 2024
        assertTrue(result.outputContent.contains("\$ MATERIAL PROPERTIES"))
        assertTrue(result.outputContent.contains("MATERIAL \"BETON25\""))
        assertTrue(result.outputContent.contains("\$ MASS SOURCE"))
        assertTrue(result.outputContent.contains("MASSSOURCE \"WI\""))
        assertTrue(result.outputContent.contains("\$ FUNCTIONS"))
        assertTrue(result.outputContent.contains("FUNCTION \"RPA2024_SPEC\""))
        assertTrue(result.outputContent.contains("\$ LOAD CASES"))
        assertTrue(result.outputContent.contains("CASE \"EX\""))
        assertTrue(result.outputContent.contains("\$ LOAD COMBINATIONS"))
        assertTrue(result.outputContent.contains("COMBO \"ELU\""))

        // Test d'export du spectre au format texte (.txt)
        val specFile = File(tempDir, "Spectrum_RPA2024.txt")
        injector.exportSpectrumFile(specFile, joint)
        assertTrue(specFile.exists())
        val lines = specFile.readLines()
        assertTrue(lines.size > 20)
        assertTrue(lines.any { it.contains("RPA 2024 SPECTRUM") })
    }

    @Test
    fun testLiveComGracefulFallback() {
        val injector = EtabsLiveApiAndE2kInjector()
        val joint = createTestJoint()

        // L'appel ne doit jamais lever d'exception non gérée, même sans ETABS en mémoire
        val comResult = injector.injectRpa2024ViaLiveApi(joint)
        assertNotNull(comResult)
        assertNotNull(comResult.message)
    }

    @Test
    fun testEtabsToSafeFoundationBridgeOptimization() {
        val bridge = EtabsToSafeFoundationBridge()
        val joint = createTestJoint()

        // 1. Dérivation des réactions de base
        val reactions = bridge.deriveBaseReactionsFromTopology(joint)
        assertEquals(9, reactions.size) // 3 axes X * 3 axes Y = 9 points de base
        assertTrue(reactions.all { it.fzDeadKN < 0.0 && it.fzLiveKN < 0.0 })

        // 2. Modélisation et optimisation des semelles avec sol admissible de 150 kPa (1.5 bar)
        val soilConfig = FoundationSoilConfig(
            allowableBearingCapacityBars = 1.50,
            subgradeModulusKsKNM3 = 21848.0
        )
        val report = bridge.buildAndOptimizeSafeModel(joint, soilConfig, reactions)

        assertTrue(report.success)
        assertTrue(report.finalMaxStressKNm2 <= report.allowableStressKNm2 + 1e-3)
        assertEquals(150.0, report.allowableStressKNm2)
        assertTrue(report.footingWidths.isNotEmpty())
        assertTrue(report.lineSprings.isNotEmpty())

        // Vérification des raideurs de ressort linéique K_line = Ks * B
        for ((lineId, width) in report.footingWidths) {
            val expectedK = soilConfig.subgradeModulusKsKNM3 * width
            val actualK = report.lineSprings[lineId] ?: 0.0
            assertTrue(abs(actualK - expectedK) < 1e-2)
        }

        // 3. Vérification du format CSI SAFE .s2k généré
        val s2k = report.s2kContent
        assertTrue(s2k.contains("\$ PROGRAM INFORMATION"))
        assertTrue(s2k.contains("PROGRAM \"SAFE\""))
        assertTrue(s2k.contains("\$ UNITS"))
        assertTrue(s2k.contains("UNITS \"KN-M-C\""))
        assertTrue(s2k.contains("\$ GRID LINES"))
        assertTrue(s2k.contains("\$ MATERIAL PROPERTIES"))
        assertTrue(s2k.contains("MATERIAL \"BETON25\"  TYPE \"CONCRETE\""))
        // Module différé longue durée Evj = 10 818 000
        assertTrue(s2k.contains("E 10818000"))
        assertTrue(s2k.contains("G 4507500"))
        assertTrue(s2k.contains("\$ BEAM PROPERTIES"))
        assertTrue(s2k.contains("\$ LINE SPRINGS"))
        assertTrue(s2k.contains("NONLINEAR TENSION=NO COMPRESSION=YES"))
        assertTrue(s2k.contains("\$ POINT OBJECTS"))
        assertTrue(s2k.contains("\$ LINE OBJECTS"))
        assertTrue(s2k.contains("\$ LINE ASSIGNS"))
        assertTrue(s2k.contains("\$ POINT LOADS"))
        assertTrue(s2k.contains("LOADCASE \"DEAD\""))
        assertTrue(s2k.contains("LOADCASE \"LIVE\""))
        assertTrue(s2k.contains("\$ LOAD COMBINATIONS"))
        assertTrue(s2k.contains("COMBO \"ELU\""))
        assertTrue(s2k.contains("COMBO \"ELS\""))
    }

    @Test
    fun testSafeSoilStressWideningIteration() {
        val bridge = EtabsToSafeFoundationBridge()
        val joint = createTestJoint()

        // Création de charges très lourdes pour forcer l'optimisation et l'élargissement itératif
        val heavyReactions = bridge.deriveBaseReactionsFromTopology(joint).map {
            it.copy(fzDeadKN = it.fzDeadKN * 3.5, fzLiveKN = it.fzLiveKN * 3.5)
        }

        // Capacité portante faible de 1.0 bar (100 kPa)
        val soilConfig = FoundationSoilConfig(
            allowableBearingCapacityBars = 1.00,
            subgradeModulusKsKNM3 = 20000.0
        )

        val report = bridge.buildAndOptimizeSafeModel(joint, soilConfig, heavyReactions)
        // Les itérations ont dû élargir les semelles au-delà de 1.20m
        assertTrue(report.iterationsCount >= 1)
        assertTrue(report.footingWidths.values.any { it > 1.20 })
    }

    @Test
    fun testAutomatedComRunnerEnvironmentAndExecution(@TempDir tempDir: File) = runBlocking {
        val runner = AutomatedComRunner()
        val status = runner.detectEnvironment()

        assertNotNull(status.operatingSystem)
        assertNotNull(status.recommendedWorkflow)
        assertNotNull(status.detectedEtabsVersion)

        // Exécution en tâche de fond async d'un modèle factice en mode autonome
        val dummyE2k = File(tempDir, "test_structure.e2k")
        dummyE2k.writeText("\$ PROGRAM INFORMATION\n  PROGRAM \"ETABS\"\n")

        var lastPercent = 0.0
        val runResult = runner.runAnalysisAsync(dummyE2k, CsiSoftwareVersion.STANDALONE_FALLBACK) { _, pct ->
            lastPercent = pct
        }

        assertTrue(runResult.success)
        assertTrue(runResult.executionTimeMs >= 0)
        assertEquals(1.00, lastPercent, 1e-4)

        // Génération d'un script batch d'exécution externe
        val batFile = File(tempDir, "run_etabs.bat")
        runner.generateRunBatchScript(batFile, dummyE2k)
        assertTrue(batFile.exists())
        val batContent = batFile.readText()
        assertTrue(batContent.contains("@echo off"))
        assertTrue(batContent.contains("/r"))
    }
}
