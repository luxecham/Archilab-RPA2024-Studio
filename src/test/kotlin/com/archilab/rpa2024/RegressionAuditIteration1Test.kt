package com.archilab.rpa2024

import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngineOptions
import com.archilab.rpa2024.parser.BeamForceRow
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.EtabsResultsReader
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import com.archilab.rpa2024.parser.TabularSource
import com.archilab.rpa2024.parser.TabularTable
import com.archilab.rpa2024.sample.SampleJointA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Itération d'audit 1 : classification des combinaisons, import des exports
 * tabulaires préfixés par TABLE:, et normalisation des participations modales.
 */
class RegressionAuditIteration1Test {

    @Test
    fun `COMB1 est ELU et COMB2 a COMB8 sont ELA`() {
        assertEquals(ComboCategory.ELU, ComboCategory.classify("COMB1"))
        (2..8).forEach { index ->
            assertEquals(ComboCategory.ELA, ComboCategory.classify("COMB$index"), "COMB$index")
        }
        assertEquals(ComboCategory.OTHER, ComboCategory.classify("COMB10"))
        assertEquals(ComboCategory.ELA, ComboCategory.classify("EX"))
        assertEquals(ComboCategory.ELA, ComboCategory.classify("E2"))
        assertEquals(ComboCategory.ELU, ComboCategory.classify("ELU"))
    }

    @Test
    fun `le poids sismique manuel prevaut sur le poids ETABS`() {
        val imported = SampleJointA.load()
        val engine = Rpa2024SeismicEngine()
        val automatic = engine.assess(imported.model.joint, imported.results)
        val manual = engine.assess(
            imported.model.joint,
            imported.results,
            options = Rpa2024SeismicEngineOptions(manualSeismicWeightKN = 12_345.0)
        )
        assertEquals(12_345.0, manual.baseShear.first().seismicWeightKN!!, 1e-9)
        assertTrue(
            manual.baseShear.first().staticBaseShearKN!! > automatic.baseShear.first().staticBaseShearKN!!,
            "Le poids manuel doit modifier la force sismique de calcul."
        )
    }

    @Test
    fun `un effort COMB2 alimente reellement le ferraillage de poutre`() {
        val imported = SampleJointA.load()
        val beamId = imported.model.joint.sectionCatalog.beams.first().elementIds.first()
        val results = EtabsResults(
            beamForces = listOf(
                BeamForceRow(
                    story = "N1",
                    element = beamId,
                    loadCase = "COMB2",
                    stationMeters = 2.0,
                    pKN = 0.0,
                    v2KN = 80.0,
                    v3KN = 0.0,
                    m2KNm = 0.0,
                    m3KNm = 90.0,
                    lengthMeters = 7.5
                )
            )
        )
        val design = BeamDesignEngine().design(imported.model.joint, results)
        val family = design.beams.firstOrNull { it.family.elementIds.contains(beamId) }
        assertNotNull(family)
        assertTrue(
            family!!.criticalSections.isNotEmpty(),
            "Une combinaison COMB2 doit produire une section critique BAEL, pas seulement le minimum."
        )
        assertNotNull(family.governingSection)
        assertTrue(family.governingSection!!.bael.requiredSteelCm2 >= 0.0)
    }

    @Test
    fun `un export tabulaire ETABS prefixe TABLE est lu comme tableaux et non comme e2k`(
        @TempDir tempDir: Path
    ) {
        val text = """
            TABLE:  Program Control
            CurrUnits	Units
            kN, m, C	kN, m, C

            TABLE:  Story Data
            Story	Height	Elevation
            N1	3.0	0.0
            N2	3.0	3.0

            TABLE:  Point Coordinates
            UniqueName	X	Y	Z
            P1	0.0	0.0	0.0
            P2	0.0	0.0	3.0
            P3	3.0	0.0	0.0
            P4	3.0	0.0	3.0

            TABLE:  Frame Section Properties
            SectionName	Material	Shape	t3	t2
            C40	B25	Rectangular	0.40	0.40

            TABLE:  Connectivity - Frame
            Frame	Joint1	Joint2	Type	Story
            C1	P1	P2	Column	N1

            TABLE:  Frame Section Assignments
            Frame	Section	Story
            C1	C40	N1
        """.trimIndent()
        val file = tempDir.resolve("EXPORT_ETABS.txt")
        Files.writeString(file, text)

        val tables = TabularSource.read(file)
        assertTrue(tables.any { it.name.contains("Story Data") }, tables.joinToString { it.name })

        val raw = EtabsUniversalIngestor().parseRaw(file)
        assertEquals(4, raw.points.size)
        assertEquals(1, raw.frames.size)
        assertEquals(3.0, raw.points.getValue("P2").zMeters, 1e-9)
        assertEquals("COLUMN", raw.frames.single().kind.name)
    }

    @Test
    fun `le modele e2k reste route vers le parseur e2k`(@TempDir tempDir: Path) {
        val e2k = SampleJointA.e2kText()
        val file = tempDir.resolve("JOINT_A.txt")
        Files.writeString(file, e2k)
        val raw = EtabsUniversalIngestor().parseRaw(file)
        assertFalse(raw.frames.isEmpty(), "Le modèle e2k doit conserver ses éléments.")
        assertTrue(raw.points.isNotEmpty())
    }

    @Test
    fun `les participations en pourcentage sans colonnes somme ne sont pas multipliees`() {
        val results = EtabsResultsReader().read(
            listOf(
                TabularTable("Program Control", listOf("CurrUnits"), listOf(listOf("kN, m, C"))),
                TabularTable(
                    "Modal Participating Mass Ratios",
                    listOf("Mode", "Period", "UX", "UY", "UZ", "RX", "RY", "RZ"),
                    listOf(listOf("1", "0.50", "60", "0", "0", "0", "0", "0"))
                )
            )
        )
        assertEquals(1, results.modalRatios.size)
        assertEquals(60.0, results.modalRatios.single().uxPercent, 1e-9)
    }

    @Test
    fun `les participations en fraction sans colonnes somme sont converties en pourcentage`() {
        val results = EtabsResultsReader().read(
            listOf(
                TabularTable("Program Control", listOf("CurrUnits"), listOf(listOf("kN, m, C"))),
                TabularTable(
                    "Modal Participating Mass Ratios",
                    listOf("Mode", "Period", "UX", "UY", "UZ", "RX", "RY", "RZ"),
                    listOf(listOf("1", "0.50", "0.60", "0.10", "0", "0", "0", "0"))
                )
            )
        )
        assertEquals(60.0, results.modalRatios.single().uxPercent, 1e-9)
        assertEquals(10.0, results.modalRatios.single().uyPercent, 1e-9)
    }
}
