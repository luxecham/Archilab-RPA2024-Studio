package com.archilab.rpa2024

import com.archilab.rpa2024.engine.foundations.BaelTInput
import com.archilab.rpa2024.engine.foundations.BaelTSectionSolver
import com.archilab.rpa2024.engine.foundations.FoundationGeotechnicalParameters
import com.archilab.rpa2024.engine.foundations.FoundationSchematicRenderer
import com.archilab.rpa2024.engine.foundations.SafeFoundationExtractor
import com.archilab.rpa2024.engine.foundations.SoilBearingStressVerifier
import com.archilab.rpa2024.engine.foundations.SoilBearingVerificationInput
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingKind
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.BaelSectionInput
import com.archilab.rpa2024.engine.frames.BaelSituation
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.secondary.ParapetDesignInput
import com.archilab.rpa2024.engine.secondary.ParapetSchematicRenderer
import com.archilab.rpa2024.engine.secondary.ParapetWallDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseDesignInput
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.parser.TabularTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests de la partie 5 : éléments secondaires (acrotère, escalier) et fondations
 * (extracteur SAFE, portance, solveurs BaelR/BaelT, orchestrateur et rendus SVG).
 */
class SecondaryAndFoundationEngineTest {

    private val materials = MaterialConfig(fc28MPa = 25.0, feMPa = 500.0)

    // --------------------------------------------------------------- acrotère

    @Test
    fun `acrotère calcule la géométrie et les charges réglementaires`() {
        val result = ParapetWallDesigner().design(ParapetDesignInput(zoneAccelerationA = 0.07))

        assertEquals(18.43, result.geometry.slopeAngleDegrees, 0.02)
        assertEquals(1.658, result.geometry.developedPerimeterMeters, 0.005)
        assertEquals(0.07125, result.geometry.sectionAreaM2, 1e-6)
        assertEquals(2.4445, result.loads.totalWeightWpKNPerM, 0.01)
        assertEquals(0.5476, result.loads.seismicForceFpKNPerM, 0.002)
        assertEquals(1.00, result.loads.designHorizontalQKNPerM, 1e-9)
        assertTrue(result.mainSteel.providedAsCm2PerM + 1e-9 >= result.mainSteel.minimumAsCm2PerM)
        assertTrue(result.mainSteel.providedAsCm2PerM + 1e-9 >= result.mainSteel.requiredAsCm2PerM)
        assertEquals(CheckStatus.OK, result.checks.first { it.code == "PA11.3_MAINCOURANTE" }.status)
    }

    @Test
    fun `acrotère produit une coupe et un ferraillage SVG`() {
        val result = ParapetWallDesigner().design(ParapetDesignInput(zoneAccelerationA = 0.07))
        val renderer = ParapetSchematicRenderer()

        val geometry = renderer.renderGeometry(result)
        val reinforcement = renderer.renderReinforcement(result)
        val complete = renderer.render(result)

        assertTrue(geometry.startsWith("<?xml"))
        assertTrue(geometry.contains("<polygon"))
        assertTrue(geometry.contains("goutte d'eau"))
        assertTrue(reinforcement.contains("Nappe principale"))
        assertTrue(reinforcement.contains(result.mainSteel.label))
        assertTrue(complete.contains("note graphique"))
        assertEquals(11, result.toChapterReport().number)
    }

    // --------------------------------------------------------------- escalier

    @Test
    fun `escalier respecte Blondel et l'épaisseur de paillasse`() {
        val result = StaircaseDesigner().design(StaircaseDesignInput())
        val geometry = result.geometry

        assertTrue(geometry.blondelValueMeters in 0.60..0.64)
        assertTrue(geometry.waistThicknessMeters + 1e-9 >= geometry.minimumWaistThicknessMeters)
        assertTrue(geometry.waistThicknessMeters - 1e-9 <= geometry.maximumWaistThicknessMeters)
        assertTrue(result.spanMomentUltimateKNm > 0.0)
        assertTrue(result.supportMomentUltimateKNm > 0.0)
        assertTrue(result.shearUltimateKN > 0.0)
        assertTrue(result.spanReinforcement.providedAsCm2PerM + 1e-9 >= result.spanReinforcement.requiredAsCm2PerM)
        assertTrue(result.supportReinforcement.providedAsCm2PerM + 1e-9 >= result.supportReinforcement.requiredAsCm2PerM)
        assertEquals(CheckStatus.OK, result.checks.first { it.code == "PE12.1_BLONDEL" }.status)
        assertEquals(CheckStatus.OK, result.checks.first { it.code == "PE12.2_EPAISSEUR" }.status)
        assertEquals(CheckStatus.OK, result.checks.first { it.code == "PE12.6_CISAILLEMENT" }.status)
        assertEquals(12, result.toChapterReport().number)
    }

    // ------------------------------------------------------------ portance sol

    @Test
    fun `portance du sol vérifie sigma égale delta fois Ks`() {
        val result = SoilBearingStressVerifier().verify(
            SoilBearingVerificationInput(
                settlementMeters = 0.005,
                subgradeModulusKNM3 = 21_848.0,
                allowableStressKNM2 = 120.0,
                allowableStressBars = 1.2,
                axialServiceKN = 100.0,
                footingLengthMeters = 1.0,
                providedWidthMeters = 1.2
            )
        )

        assertEquals(109.24, result.effectiveStressKNM2, 0.01)
        assertEquals(1.0924, result.effectiveStressBars, 0.0001)
        assertTrue(result.isBearingSatisfied)
        assertEquals(CheckStatus.OK, result.checks.first { it.code == "SOL13.1_PORTANCE" }.status)
        assertEquals(0.833333, result.minimumWidthMeters, 1e-5)
        assertEquals(CheckStatus.OK, result.checks.first { it.code == "SOL13.3_LARGEUR" }.status)
        assertEquals(13, result.toChapterReport().number)
    }

    @Test
    fun `portance du sol signale un dépassement et un soulèvement`() {
        val result = SoilBearingStressVerifier().verify(
            SoilBearingVerificationInput(
                settlementMeters = 0.008,
                subgradeModulusKNM3 = 21_848.0,
                allowableStressKNM2 = 120.0,
                upliftMeters = 0.002,
                providedWidthMeters = 0.8
            )
        )

        assertEquals(174.784, result.effectiveStressKNM2, 0.01)
        assertFalse(result.isBearingSatisfied)
        assertEquals(CheckStatus.FAILED, result.checks.first { it.code == "SOL13.1_PORTANCE" }.status)
        assertEquals(CheckStatus.WARNING, result.checks.first { it.code == "SOL13.2_SOULEVEMENT" }.status)
        assertTrue(result.warnings.any { it.contains("Soulèvement") })
    }

    // ------------------------------------------------------------ solveur BaelT

    @Test
    fun `BaelT cas 1 restitue la section d'acier de la fiche BAELT`() {
        val result = BaelTSectionSolver().solve(
            BaelTInput(
                bMeters = 2.0,
                b0Meters = 0.5,
                hMeters = 0.9,
                h0Meters = 0.4,
                muKNm = 126.0
            )
        )

        assertTrue(result.caseLabel.contains("Cas 1"))
        assertEquals(2.97, result.aInfCm2, 0.02)
        assertTrue(result.neutralAxisY0Meters < 0.4)
        assertTrue(result.momentTableKNm > result.designMomentKNm)
    }

    @Test
    fun `BaelT cas 2 traite la nervure et les armatures de compression`() {
        val result = BaelTSectionSolver().solve(
            BaelTInput(
                bMeters = 2.0,
                b0Meters = 0.5,
                hMeters = 0.9,
                h0Meters = 0.4,
                muKNm = 10_900.0
            )
        )

        assertTrue(result.caseLabel.contains("Cas 2"))
        assertTrue(result.aInfCm2 > 250.0)
        assertTrue(result.aSupCm2 > 0.0)
        assertTrue(result.neutralAxisY0Meters > 0.4)
        assertTrue(result.notes.any { it.contains("débords") })
    }

    // ------------------------------------------------------------ solveur BaelR

    @Test
    fun `BaelR restitue la fiche 0_40 x 0_90`() {
        val result = BaelRectangularSolver().solve(
            BaelSectionInput(
                bCm = 40.0,
                hCm = 90.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                muGKNm = 313.0,
                situation = BaelSituation.ACCIDENTAL
            ),
            materials
        )

        assertEquals(7.57, result.bottomSteelCm2, 0.05)
        assertEquals(0.057, result.neutralAxisMeters, 0.005)
        assertEquals(0.0, result.topSteelCm2, 1e-9)
    }

    /**
     * La fiche du prompt annonce 27,57 cm2 et y0 = 0,09 m pour M = 1335 kN.m sur
     * 0,90 x 0,50 : ces valeurs sont incompatibles avec fsu = 500 MPa (z > d).
     * Le solveur restitue la solution BAEL exacte, contrôlée ici.
     */
    @Test
    fun `BaelR traite la fiche 0_90 x 0_50 avec la solution BAEL exacte`() {
        val result = BaelRectangularSolver().solve(
            BaelSectionInput(
                bCm = 90.0,
                hCm = 50.0,
                tensionCoverCm = 5.0,
                compressionCoverCm = 5.0,
                muGKNm = 1335.0,
                situation = BaelSituation.ACCIDENTAL
            ),
            materials
        )

        assertEquals(76.8, result.bottomSteelCm2, 0.3)
        assertEquals(0.256, result.neutralAxisMeters, 0.005)
        assertTrue(result.leverArmMeters < result.section.dMeters)
    }

    // --------------------------------------------------- extracteur SAFE + orchestre

    @Test
    fun `extracteur SAFE lit les familles, ressorts, déplacements et efforts`() {
        val model = SafeFoundationExtractor().extract(
            tables = safeTables(),
            geotechnical = FoundationGeotechnicalParameters()
        )

        assertEquals(2, model.families.size)
        val current = requireNotNull(model.family("SF120"))
        assertEquals(StripFootingKind.CURRENT, current.kind)
        assertTrue(current.isTee)
        assertEquals(1.20, current.flangeWidthMeters, 1e-9)
        assertEquals(0.40, current.webWidthMeters, 1e-9)
        assertEquals(0.30, current.tableThicknessMeters, 1e-9)
        assertEquals(0.60, current.totalHeightMeters, 1e-9)

        val joint = requireNotNull(model.family("SFJ200"))
        assertEquals(StripFootingKind.SEISMIC_JOINT, joint.kind)

        val spring = requireNotNull(model.springFor(current))
        assertEquals("K120", spring.name)
        assertEquals(1.20, spring.widthMeters, 1e-9)
        assertEquals(21_848.0, spring.subgradeModulusKNM3, 1e-6)
        assertEquals(21_848.0 * 1.2, spring.lineStiffnessKNM2, 0.1)

        val envelope = requireNotNull(model.displacementEnvelope)
        assertEquals(0.005, envelope.settlementMeters, 1e-9)
        assertEquals("ELS1", envelope.governingLoadCase)

        val forces = requireNotNull(model.forcesFor("SF120"))
        assertEquals(400.0, forces.designSupportMomentKNm, 0.01)
        assertEquals(150.0, forces.designSpanMomentKNm, 0.01)
        assertEquals(250.0, forces.designShearKN, 0.01)
        assertEquals(400.0, forces.designAxialKN, 0.01)
        assertEquals(370.0, forces.serviceAxialKN, 0.01)
        assertEquals("C25/30", model.materialFor(current).concreteClass)
        assertEquals(500.0, model.materialFor(current).feMPa, 1e-9)
    }

    @Test
    fun `orchestrateur dimensionne les semelles et produit le rapport chapitre 13`() {
        val model = SafeFoundationExtractor().extract(
            tables = safeTables(),
            geotechnical = FoundationGeotechnicalParameters()
        )
        val set = StripFootingDesignOrchestrator().design(model)

        assertEquals(2, set.footings.size)
        val current = set.footings.first { it.family.name == "SF120" }
        val soil = requireNotNull(current.soil)
        assertTrue(soil.isBearingSatisfied)
        assertTrue(current.supportSteel.isSatisfied)
        assertTrue(current.spanSteel.isSatisfied)
        assertTrue(current.transverseSteel.isSatisfied)
        assertEquals(CheckStatus.OK, current.checks.first { it.code == "SF13.5_CISAILLEMENT" }.status)
        assertTrue(current.transverseRequiredCm2PerM > 0.0)
        assertEquals("BaelT (table comprimée)", current.adoptedSpanModel)
        assertTrue(current.spanTeeDesign != null)

        val joint = set.footings.first { it.family.name == "SFJ200" }
        assertEquals(StripFootingKind.SEISMIC_JOINT, joint.family.kind)
        assertTrue(joint.supportSteel.isSatisfied)
        assertTrue(joint.spanSteel.isSatisfied)

        val report = set.toChapterReport()
        assertEquals(13, report.number)
        assertTrue(report.elements.size >= 2)
        assertFalse(report.lines.isEmpty())
        assertTrue(report.lines.any { it.contains("SF120") })
        assertTrue(report.status != CheckStatus.NOT_APPLICABLE)
    }

    @Test
    fun `rendus SVG des sections rectangulaire et en T`() {
        val model = SafeFoundationExtractor().extract(safeTables())
        val set = StripFootingDesignOrchestrator().design(model)
        val current = set.footings.first { it.family.name == "SF120" }
        val renderer = FoundationSchematicRenderer()

        val rectangular = renderer.renderRectangular(current)
        val tee = renderer.renderTee(current)
        val complete = renderer.render(set)

        assertTrue(rectangular.contains("Section rectangulaire"))
        assertTrue(rectangular.contains("url(#hatchCompression)"))
        assertTrue(rectangular.contains("y0 ="))
        assertTrue(tee.contains("Section en Té"))
        assertTrue(tee.contains("h0 ="))
        assertTrue(tee.contains("url(#hatchCompression)"))
        assertTrue(complete.contains("SFJ200"))
        assertTrue(complete.startsWith("<?xml"))
    }

    private fun safeTables(): List<TabularTable> = listOf(
        TabularTable("Units", listOf("Units"), listOf(listOf("KN-M"))),
        TabularTable(
            name = "Beam Properties",
            headers = listOf("Name", "Material", "B", "h0", "b0", "h", "d'", "c", "Length"),
            rows = listOf(
                listOf("SF120", "C25/30", "1.20", "0.30", "0.40", "0.60", "0.05", "0.05", "1.00"),
                listOf("SFJ200", "C25/30", "2.00", "0.35", "0.60", "0.80", "0.05", "0.05", "1.00")
            )
        ),
        TabularTable(
            name = "Line Spring Properties",
            headers = listOf("Name", "Width", "Ks"),
            rows = listOf(
                listOf("K120", "1.20", "21848"),
                listOf("K200", "2.00", "21848")
            )
        ),
        TabularTable(
            name = "Joint Coordinates",
            headers = listOf("Joint", "X", "Y"),
            rows = listOf(
                listOf("J1", "0", "0"),
                listOf("J2", "5", "0")
            )
        ),
        TabularTable(
            name = "Joint Displacements",
            headers = listOf("Joint", "OutputCase", "U3"),
            rows = listOf(
                listOf("J1", "ELS1", "-0.005"),
                listOf("J2", "ELS1", "-0.003")
            )
        ),
        TabularTable(
            name = "Beam Forces",
            headers = listOf("Beam", "Section", "Station", "OutputCase", "P", "V2", "M3"),
            rows = beamForceRows("SF120", axialUltimate = 500.0, axialService = 370.0, axialAccidental = 400.0) +
                beamForceRows("SFJ200", axialUltimate = 300.0, axialService = 250.0, axialAccidental = 280.0)
        ),
        TabularTable(
            name = "Material Properties",
            headers = listOf("Name", "Type", "Fc", "Fy"),
            rows = listOf(
                listOf("C25/30", "Concrete", "25", "0"),
                listOf("FeE500", "Rebar", "0", "500")
            )
        )
    )

    private fun beamForceRows(
        section: String,
        axialUltimate: Double,
        axialService: Double,
        axialAccidental: Double
    ): List<List<String>> {
        val supportUltimate = if (section.startsWith("SFJ")) 600.0 else 300.0
        val spanUltimate = if (section.startsWith("SFJ")) 250.0 else 120.0
        val shearUltimate = if (section.startsWith("SFJ")) 350.0 else 200.0
        val supportService = if (section.startsWith("SFJ")) 350.0 else 220.0
        val spanService = if (section.startsWith("SFJ")) 150.0 else 90.0
        val shearService = if (section.startsWith("SFJ")) 220.0 else 150.0
        val supportAccidental = if (section.startsWith("SFJ")) 700.0 else 400.0
        val spanAccidental = if (section.startsWith("SFJ")) 300.0 else 150.0
        val shearAccidental = if (section.startsWith("SFJ")) 400.0 else 250.0

        return listOf(
            listOf(section, section, "0", "ELU1", axialUltimate.toString(), shearUltimate.toString(), (-supportUltimate).toString()),
            listOf(section, section, "2.5", "ELU1", axialUltimate.toString(), "10", spanUltimate.toString()),
            listOf(section, section, "5", "ELU1", axialUltimate.toString(), (-shearUltimate).toString(), (-supportUltimate).toString()),
            listOf(section, section, "0", "ELS1", axialService.toString(), shearService.toString(), (-supportService).toString()),
            listOf(section, section, "2.5", "ELS1", axialService.toString(), "0", spanService.toString()),
            listOf(section, section, "5", "ELS1", axialService.toString(), (-shearService).toString(), (-supportService).toString()),
            listOf(section, section, "0", "ELA1", axialAccidental.toString(), shearAccidental.toString(), (-supportAccidental).toString()),
            listOf(section, section, "2.5", "ELA1", axialAccidental.toString(), "0", spanAccidental.toString()),
            listOf(section, section, "5", "ELA1", axialAccidental.toString(), (-shearAccidental).toString(), (-supportAccidental).toString())
        )
    }
}
