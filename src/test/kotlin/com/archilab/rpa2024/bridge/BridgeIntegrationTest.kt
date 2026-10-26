package com.archilab.rpa2024.bridge

import com.archilab.rpa2024.engine.seismic.SpectrumGenerator
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.E2kModelParser
import com.archilab.rpa2024.parser.UnitSystem
import com.archilab.rpa2024.report.NoteDeCalculBundle
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.sample.SampleJointA
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.math.cbrt

class BridgeIntegrationTest {
    private val imported = SampleJointA.load()
    private val resolved = Rpa2024NormativeDb.resolveSeismic(imported.model.joint.seismicParams, imported.model.joint)
    private val spectrum = SpectrumGenerator.from(resolved)
    private val injectionConfig = EtabsInjectionConfig(spectrum, resolved.psi)

    @Test
    fun `l'injection ETABS respecte les unites les masses et les combinaisons`() {
        val directory = Files.createTempDirectory("rpa2024-bridge-etabs")
        try {
            val injector = EtabsLiveApiAndE2kInjector()
            val result = injector.injectText(SampleJointA.e2kText(), injectionConfig, directory, UnitSystem.KN_MM)
            val concrete = result.materials.first()
            assertEquals(11_000_000.0 * cbrt(25.0), concrete.elasticModulusKNM2, 1e-6)
            assertEquals(concrete.elasticModulusKNM2 / 2.4, concrete.shearModulusKNM2, 1e-6)
            assertEquals(25_000.0, concrete.compressiveStrengthKNM2!!, 0.0)
            assertEquals(500_000.0, result.materials[1].yieldStrengthKNM2!!, 0.0)
            assertEquals(32.164195, CsiUnits.stress(concrete.elasticModulusKNM2, UnitSystem.KN_MM), 1e-5)
            assertEquals(1.0, result.massSource.permanentMultiplier, 0.0)
            assertEquals(resolved.psi, result.massSource.liveMultiplier, 0.0)
            assertTrue(result.massSource.lateralMassOnly)
            assertTrue(result.massSource.lumpAtStories)
            assertEquals(201, result.spectrumExport.horizontalPointCount)
            assertEquals(201, result.spectrumExport.verticalPointCount)
            assertEquals(listOf("U1", "U2"), result.cases.map { it.direction })
            assertTrue(result.cases.all { it.modalCombination == "CQC" && it.eccentricity == 0.05 })
            assertEquals(17, result.combinations.size)
            assertEquals(listOf(1.0, 0.3), result.combinations.first { it.name == "COMB1" }.terms.takeLast(2).map { it.factor })
            assertEquals(listOf(0.8, -1.0), result.combinations.first { it.name == "PORT_E2_N" }.terms.map { it.factor })
            assertTrue(result.text.contains("MATERIAL PROPERTIES 02 - MECHANICAL"))
            assertTrue(result.text.contains("LateralMassOnly=YES"))
            assertTrue(result.text.contains("ModalComb=CQC"))
            assertEquals(listOf(3, 4, 9), result.reportChapters.map { it.number })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `le bloc ETABS est idempotent et les planches s'integrent au rapport`() {
        val directory = Files.createTempDirectory("rpa2024-bridge-idempotent")
        try {
            val injector = EtabsLiveApiAndE2kInjector()
            val first = injector.injectText(SampleJointA.e2kText(), injectionConfig, directory)
            val second = injector.injectText(first.text, injectionConfig, directory)
            assertEquals(first.text, second.text)
            assertEquals(1, Regex("RPA2024:BEGIN:ETABS").findAll(second.text).count())

            val artifacts = NoteDeCalculBundle().build(NoteDeCalculRequest(imported.model, imported.results))
            val integrated = first.integrateInto(first.integrateInto(artifacts.document))
            assertEquals(13, integrated.chapters.size)
            assertEquals(3, integrated.chapters.map { it.number }.toSet().intersect(setOf(3, 4, 9)).size)
            assertTrue(integrated.chapters.filter { it.number in setOf(3, 4, 9) }
                .flatMap { it.blocks }.filterIsInstance<com.archilab.rpa2024.report.ReportBlock.Figure>()
                .map { it.key }.containsAll(listOf("bridge-materials", "bridge-combinations", "bridge-spectrum")))
            assertEquals(1, integrated.chapters.flatMap { it.blocks }
                .filterIsInstance<com.archilab.rpa2024.report.ReportBlock.Figure>().count { it.key == "bridge-spectrum" })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `le pont SAFE parcourt toutes les grilles et detecte les rives de joint`() {
        val raw = E2kModelParser().parseText(SampleJointA.e2kText())
        val baseNode = raw.points.values.filter { it.zMeters == 0.0 }.first { it.xMeters == 0.0 }
        val input = SafeBridgeInput(
            geometry = raw,
            reactions = raw.points.values.filter { it.zMeters == 0.0 }.map {
                SupportReaction(it.id, "ELS", fxKN = 5.0, fyKN = 4.0, fzKN = 800.0, mxKNm = 20.0)
            },
            soil = FoundationSoilInput(allowableStressKNM2 = 150.0),
            seismicJointGridLabels = setOf("A"),
            seismicJointNodeIds = setOf(baseNode.id)
        )
        val bridge = EtabsToSafeFoundationBridge()
        val generated = bridge.build(input)
        assertEquals(raw.gridsX.size + raw.gridsY.size, generated.footings.size)
        assertEquals(2, generated.footings.count { it.seismicJoint })
        assertEquals(2, generated.footings.count { it.family.name.startsWith("SFJ_") })
        assertTrue(generated.footings.filterNot { it.seismicJoint }.all { it.eccentricityMeters == 0.0 })
        assertTrue(generated.footings.filter { it.seismicJoint }.all { it.eccentricityMeters > 0.0 })
        assertEquals(18_000.0, generated.subgradeModulusKNM3, 0.0)
        assertTrue(generated.footings.all { it.spring.lineStiffnessKNM2 == 18_000.0 * it.family.flangeWidthMeters })
        assertEquals(3_700_000.0 * cbrt(25.0), generated.materials.first().elasticModulusKNM2, 1e-6)
        assertEquals(550_000.0, generated.materials.first { it.name == "FE500" }.tensileStrengthKNM2!!, 0.0)
        assertEquals(410_000.0, generated.materials.first { it.name == "FE235" }.tensileStrengthKNM2!!, 0.0)
        assertTrue(generated.text.contains("CompressionOnly=YES"))
        assertTrue(generated.text.contains("JOINT LOADS"))
        assertEquals(generated.text, bridge.build(input, generated.text).text)
    }

    @Test
    fun `l'optimisation SAFE propose le palier suivant et met a jour le ressort`() {
        val raw = E2kModelParser().parseText(SampleJointA.e2kText())
        val input = SafeBridgeInput(raw, soil = FoundationSoilInput(150.0), initialWidthMeters = 0.80)
        val bridge = EtabsToSafeFoundationBridge()
        val initial = bridge.build(input)
        val target = initial.footings.first().family.name
        val optimized = bridge.optimize(input, measuredSettlementsMeters = mapOf(target to 0.020))
        val result = optimized.optimizations.first { it.footingName == target }
        assertEquals(360.0, result.previousStressKNM2, 1e-9)
        assertEquals(2.0, result.proposedWidthMeters, 1e-9)
        assertEquals(144.0, result.proposedStressKNM2, 1e-9)
        assertEquals(36_000.0, result.proposedLineStiffnessKNM2, 1e-9)
        assertTrue(result.converged)
        val updated = optimized.footings.first { it.family.name == target }
        assertEquals(2.0, updated.family.flangeWidthMeters, 0.0)
        assertEquals(36_000.0, updated.spring.lineStiffnessKNM2, 0.0)
        assertNotEquals(initial.text, optimized.text)
    }

    @Test
    fun `le runner bascule entre legacy texte et OAPI XLSX et ferme les sessions`() {
        val directory = Files.createTempDirectory("rpa2024-bridge-runner")
        try {
            val model = directory.resolve("model.e2k")
            Files.writeString(model, SampleJointA.e2kText())
            val legacy = FakeFactory("9.7.4")
            val legacyResult = runBlocking {
                AutomatedComRunner(legacy).run(AutomatedRunRequest(
                    CsiProduct.ETABS, model, directory.resolve("tmp"), tables = listOf("Joint Reactions", "Pier Forces")
                ))
            }
            assertEquals(CsiExecutionMode.LEGACY_TEXT, legacyResult.mode)
            assertTrue(legacyResult.isComplete)
            assertTrue(legacyResult.exportedTables.values.all { it.extension == "txt" })
            assertEquals(listOf("open", "analyze", "save", "close"), legacy.session.events)

            val modern = FakeFactory("21.0.0")
            val modernResult = runBlocking {
                AutomatedComRunner(modern).run(AutomatedRunRequest(
                    CsiProduct.ETABS, model, directory.resolve("tmp"), runAnalysis = false,
                    tables = listOf("Modal Participating Mass Ratios")
                ))
            }
            assertEquals(CsiExecutionMode.OAPI_XLSX, modernResult.mode)
            assertTrue(modernResult.exportedTables.values.single().extension == "xlsx")
            assertEquals(listOf("open", "save", "close"), modern.session.events)
            assertEquals(CsiExecutionMode.OAPI_XLSX,
                AutomatedComRunner.executionMode(CsiProduct.SAFE, CsiVersion.parse("12.0.0")))
            assertFalse(AutomatedComRunner.executionMode(CsiProduct.ETABS, CsiVersion.parse("9.7.4")) == CsiExecutionMode.OAPI_XLSX)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class FakeFactory(version: String) : CsiSessionFactory {
        val session = FakeSession(CsiVersion.parse(version))
        override fun create(request: AutomatedRunRequest): CsiSession = session
    }

    private class FakeSession(override val version: CsiVersion) : CsiSession {
        val events = mutableListOf<String>()
        override fun openModel(path: Path) { events += "open" }
        override fun analyzeAll() { events += "analyze" }
        override fun saveModel() { events += "save" }
        override fun exportTable(tableName: String, destination: Path) {
            Files.writeString(destination, "Table,$tableName\nValue,1\n")
        }
        override fun close() { events += "close" }
    }
}
