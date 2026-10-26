package com.archilab.rpa2024

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.ColumnChecker
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.GeometryAndPreSizingEngine
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.normative.SiteClass
import com.archilab.rpa2024.normative.SpectrumType
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsImport
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import com.archilab.rpa2024.sample.SampleJointA
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileOutputStream
import java.nio.file.Files
import kotlin.math.pow

class DynamicTopologyAndNormativeTest {

    private val imported: EtabsImport = SampleJointA.load()
    private val joint = imported.model.joint

    @Test
    fun `la topologie est reconstruite depuis les seuls noeuds du modele`() {
        val grid = joint.gridSystem
        assertEquals(15.0, grid.totalLengthLx, 1e-9)
        assertEquals(20.65, grid.totalLengthLy, 1e-9)
        assertArrayEquals(doubleArrayOf(7.5, 7.5), grid.spansX.toDoubleArray(), 1e-9)
        assertArrayEquals(doubleArrayOf(4.65, 5.0, 5.5, 5.5), grid.spansY.toDoubleArray(), 1e-9)
        assertEquals(2, grid.numBaysX)
        assertEquals(4, grid.numBaysY)
        assertEquals(listOf("A", "B", "C"), grid.sortedXGrids.map { it.label })
        assertEquals(listOf("1", "2", "3", "4", "5"), grid.sortedYGrids.map { it.label })

        val stories = joint.storySystem
        assertEquals(4, stories.orderedStories.size)
        assertEquals(3, stories.numStoriesN)
        assertEquals(4, stories.numStoriesIncludingPenthouse)
        assertEquals(11.22, stories.totalHeightHn, 1e-9)
        assertEquals(14.96, stories.totalHeightIncludingPenthouse, 1e-9)
        assertEquals("CAGE", stories.penthouseStories.single().storyName)
        assertEquals(listOf("N1", "N2", "N3", "CAGE"), stories.storyNames)

        val catalog = joint.sectionCatalog
        assertEquals(listOf("POT35x35", "POT35x55", "POT40x50"), catalog.columns.map { it.name })
        assertEquals(listOf("B30x35", "B30x40"), catalog.beams.map { it.name })
        assertEquals(listOf("16+5"), catalog.slabs.map { it.name })
        assertEquals(60, catalog.columns.sumOf { it.elementIds.size })
        assertEquals(88, catalog.beams.sumOf { it.elementIds.size })
        assertEquals(4, catalog.slabs.sumOf { it.elementIds.size })
        assertTrue(catalog.shearWalls.isEmpty())

        val beam40 = catalog.beam("B30x40")!!
        assertEquals(7.15, beam40.maxClearSpanMeters, 0.02)
        val column55 = catalog.column("POT35x55")!!
        assertEquals(55.0, column55.h1Cm, 1e-9)
        assertEquals(35.0, column55.b1Cm, 1e-9)
        assertTrue(column55.clearHeightLclMeters in 3.30..3.36, "lcl déduit: ${column55.clearHeightLclMeters}")

        val slab = catalog.slab("16+5")!!
        assertEquals(21.0, slab.totalThicknessHtCm, 1e-9)
        assertEquals(5.0, slab.compressionSlabH0Cm, 1e-9)
        assertEquals(16.0, slab.hollowBlockHeightCm, 1e-9)
        assertEquals(10.0, slab.ribWidthB0Cm, 1e-9)
        assertEquals(5.5, slab.maxSpanMeters, 1e-9)
    }

    @Test
    fun `les tables RPA 2024 resolvent la configuration de la NDC`() {
        val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)

        assertEquals("III", resolved.zone.code)
        assertEquals(0.15, resolved.zone.coeffA, 1e-12)
        assertEquals(SiteClass.S3, resolved.siteClass)
        assertEquals(SpectrumType.TYPE_2, resolved.spectrumType)

        val spectral = requireNotNull(resolved.spectralParams)
        assertEquals(1.55, spectral.s, 1e-12)
        assertEquals(0.10, spectral.t1, 1e-12)
        assertEquals(0.40, spectral.t2, 1e-12)
        assertEquals(1.20, spectral.t3, 1e-12)
        assertEquals("Tableau 3.4", spectral.reference)

        assertEquals(1.40, resolved.importance.coeffI, 1e-12)
        assertEquals(0.39, resolved.importance.nuA, 1e-12)
        assertEquals(3.5, resolved.behaviorFactorR, 1e-12)
        assertEquals(1.10, resolved.qualityFactor.qf, 1e-9)
        assertTrue(resolved.qualityFactor.appliedPenalties.any { it.code == "QA4_MIN_BAYS" })

        assertEquals(0.050, resolved.ctCoefficient, 1e-12)
        val expectedPeriod = 0.05 * 11.22.pow(0.75)
        assertEquals(expectedPeriod, resolved.empiricalPeriodSeconds, 1e-9)
        assertEquals(0.85, resolved.lambda, 1e-12)
        assertEquals(0.20, resolved.psi, 1e-12)
        assertEquals(7.0, resolved.damping.percent, 1e-12)

        val height = requireNotNull(resolved.heightCompliance)
        assertEquals(4, height.storiesConsidered)
        assertEquals(14.96, height.heightConsideredMeters, 1e-9)
        assertFalse(height.isSatisfied)
        assertTrue(height.mainStructureSatisfied)
    }

    @Test
    fun `l'etude sismique reproduit les valeurs de la NDC Joint A`() {
        val assessment = GeometryAndPreSizingEngine().assess(imported.model, imported.results)
        val seismic = requireNotNull(assessment.seismic)

        assertEquals(10357.95, requireNotNull(seismic.seismicWeightKN), 1e-6)
        assertEquals(0.399, requireNotNull(seismic.periodXSeconds), 1e-9)
        assertEquals(0.294, requireNotNull(seismic.periodYSeconds), 1e-9)

        assertEquals(2251.69, requireNotNull(seismic.staticBaseShearXKN), 0.02)
        assertEquals(2251.69, requireNotNull(seismic.staticBaseShearYKN), 0.02)
        assertEquals(1884.51, requireNotNull(seismic.dynamicBaseShearXKN), 1e-6)
        assertEquals(1819.47, requireNotNull(seismic.dynamicBaseShearYKN), 1e-6)

        assertEquals(4, seismic.storyForcesX.size)
        assertEquals(4, seismic.driftChecksX.size)
        assertEquals(4, seismic.driftChecksY.size)
        assertEquals(CheckStatus.OK, seismic.driftStatus)
        assertEquals(CheckStatus.OK, seismic.pDeltaStatus)

        assertEquals(1884.51, imported.results.baseSeismicShearKN(AxisDirection.X)!!, 1e-6)
        assertEquals(1819.47, imported.results.baseSeismicShearKN(AxisDirection.Y)!!, 1e-6)
        assertEquals(0.399, imported.results.firstModePeriodSeconds!!, 1e-9)
    }

    @Test
    fun `les efforts normaux reduits des poteaux correspondent a la NDC`() {
        val fc28 = joint.materials.fc28MPa
        val envelopes = imported.results.columnEnvelopes(ComboCategory.ELA)

        val check55 = axialCheck("POT35x55", fc28, envelopes)
        val check40 = axialCheck("POT40x50", fc28, envelopes)
        val check35 = axialCheck("POT35x35", fc28, envelopes)

        assertEquals("PP5_NU", check55.code)
        assertEquals(0.140, check55.provided.toDouble(), 5e-4)
        assertEquals(0.299, check40.provided.toDouble(), 5e-4)
        assertEquals(0.146, check35.provided.toDouble(), 5e-4)
        listOf(check55, check40, check35).forEach { assertEquals(CheckStatus.OK, it.status) }
    }

    private fun axialCheck(
        section: String,
        fc28: Double,
        envelopes: List<com.archilab.rpa2024.parser.ColumnForceEnvelope>
    ): DesignCheck {
        val column = requireNotNull(joint.sectionCatalog.column(section))
        return ColumnChecker().axialCheck(column, fc28, envelopes)
    }

    @Test
    fun `le lecteur xlsx reconstitue les resultats ETABS`() {
        val file = Files.createTempFile("resultats-etabs", ".xlsx")
        try {
            XSSFWorkbook().use { workbook ->
                workbook.createSheet("Modal Participating Mass Ratios").apply {
                    val header = listOf("Mode", "Period", "UX", "UY", "UZ", "SumUX", "SumUY", "SumUZ")
                    val headerRow = createRow(0)
                    header.forEachIndexed { index, title -> headerRow.createCell(index).setCellValue(title) }
                    listOf(
                        listOf(1.0, 0.399, 0.68, 0.005, 0.0, 0.68, 0.005, 0.0),
                        listOf(2.0, 0.294, 0.02, 0.70, 0.0, 0.70, 0.705, 0.0),
                        listOf(3.0, 0.213, 0.20, 0.195, 0.0, 0.90, 0.90, 0.0)
                    ).forEachIndexed { rowIndex, values ->
                        val row = createRow(rowIndex + 1)
                        values.forEachIndexed { cellIndex, value ->
                            row.createCell(cellIndex).setCellValue(value)
                        }
                    }
                }
                workbook.createSheet("Base Reactions").apply {
                    val headerRow = createRow(0)
                    listOf("Load Case", "FX", "FY", "FZ").forEachIndexed { index, title ->
                        headerRow.createCell(index).setCellValue(title)
                    }
                    listOf(
                        listOf("EX", 1884.51, 0.0, 0.0),
                        listOf("EY", 0.0, 1819.47, 0.0)
                    ).forEachIndexed { rowIndex, values ->
                        val row = createRow(rowIndex + 1)
                        row.createCell(0).setCellValue(values[0] as String)
                        row.createCell(1).setCellValue(values[1] as Double)
                        row.createCell(2).setCellValue(values[2] as Double)
                        row.createCell(3).setCellValue(values[3] as Double)
                    }
                }
                FileOutputStream(file.toFile()).use { workbook.write(it) }
            }

            val results = EtabsUniversalIngestor().readResults(file)
            assertEquals(0.399, results.modalRatios.first().periodSeconds, 1e-9)
            assertEquals(68.0, results.modalRatios.first().uxPercent, 1e-9)
            assertEquals(70.0, results.modalRatios[1].uyPercent, 1e-9)
            assertEquals(1884.51, results.baseSeismicShearKN(AxisDirection.X)!!, 1e-6)
            assertEquals(1819.47, results.baseSeismicShearKN(AxisDirection.Y)!!, 1e-6)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `un fichier e2k sur disque est importe par le lecteur universel`() {
        val directory = Files.createTempDirectory("rpa2024-joint-a")
        try {
            val file = SampleJointA.writeE2k(directory.resolve("joint-a.e2k"))
            val diskImport = EtabsUniversalIngestor().importJoint(
                file,
                SampleJointA.JOINT_NAME,
                SampleJointA.defaultConfig
            )
            assertEquals(15.0, diskImport.model.joint.gridSystem.totalLengthLx, 1e-9)
            assertEquals(4, diskImport.model.joint.storySystem.orderedStories.size)
            assertEquals(3, diskImport.model.joint.sectionCatalog.columns.size)
            assertNull(diskImport.results.seismicWeightKN)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
