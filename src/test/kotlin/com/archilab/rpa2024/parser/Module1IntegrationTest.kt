package com.archilab.rpa2024.parser

import com.archilab.rpa2024.engine.precheck.DimensionsValidator
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class Module1IntegrationTest {

    @Test
    fun testRealBosquetJointADataIngestionAndValidation() {
        // 1. Initialisation du projet d'après le cas d'étude réel
        val model = StructuralJointModel(
            projectName = "RÉALISATION D'UNE SÛRETÉ URBAINE AU NIVEAU DU SITE DES 1500 LLV BOSQUET",
            jointName = "JOINT A",
            wilaya = "Sidi Bel Abbès",
            zone = SeismicZone.ZONE_III,
            site = SoilSiteClass.S3,
            importance = ImportanceGroup.GROUPE_1A,
            behaviorR = 3.5,
            qualityQ = 1.10,
            dampingEta = 0.88,
            concrete = ConcreteProperties(),
            rebar = RebarProperties(),
            geo = GeotechnicalProperties()
        )

        // 2. Simulation d'un flux d'export ETABS .e2k
        val simulatedE2k = """
            ${'$'} STORIES
              "H CAGE"     3.74   11.22
              "TERRASSE"   3.74    7.48
              "H 1ER"      3.74    3.74
              "H RDC"      3.74    0.00
            ${'$'} POINT COORDINATES
              "1"   0.00   0.00   0.00
              "2"   0.00   0.00   3.74
              "3"   4.35   0.00   3.74
            ${'$'} FRAME ASSIGNMENTS
              "C111"  "1"  "2"  "POT_40X50"  "H RDC"
              "C106"  "1"  "2"  "POT_35X55"  "H RDC"
              "C127"  "1"  "2"  "POT_35X35"  "H RDC"
              "B1"    "2"  "3"  "PR_30X55"   "H RDC"
              "B2"    "2"  "3"  "CH_30X35"   "H RDC"
            ${'$'} WALL/PIER INFORMATION
              "V1"  "H RDC"  1.40  0.30  3.39
              "V5"  "H RDC"  1.40  0.30  3.39
        """.trimIndent()

        val parser = EtabsUniversalParser()
        parser.parseE2kStream(ByteArrayInputStream(simulatedE2k.toByteArray()), model)

        // 3. Assertions sur le chargement topologique
        assertEquals(4, model.stories.size)
        assertEquals(3, model.columns.size)
        assertEquals(2, model.beams.size)
        assertEquals(2, model.shearWalls.size)

        // 4. Validation des critères géométriques du RPA 2024
        val validator = DimensionsValidator(model.zone)

        // Poteau 40x50
        val col40x50 = model.columns.first { it.id == "C111" }
        val reportCol40x50 = validator.validateColumn(col40x50)
        assertTrue(reportCol40x50.isOverallValid)
        assertEquals(2000.0, col40x50.grossAreaCm2)

        // Poutre Principale 30x55
        val beam30x55 = model.beams.first { it.id == "B1" }
        val reportBeam30x55 = validator.validateBeam(beam30x55, colSupportDimCm = 50.0)
        assertTrue(reportBeam30x55.isOverallValid)
        assertTrue(reportBeam30x55.isOverhangValid)

        // Poutre Chaînage 30x35
        val beam30x35 = model.beams.first { it.id == "B2" }
        val reportBeam30x35 = validator.validateBeam(beam30x35, colSupportDimCm = 35.0)
        assertTrue(reportBeam30x35.isOverallValid)

        // Voile V1
        val wallV1 = model.shearWalls.first { it.pierName == "V1" }
        assertEquals(1.40, wallV1.lengthLwMeters)
        assertEquals(0.30, wallV1.thicknessBwMeters)
    }
}
