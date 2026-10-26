package com.archilab.rpa2024

import com.archilab.rpa2024.engine.frames.BaelSectionState
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.walls.ShearWallQualificationAndChecks
import com.archilab.rpa2024.engine.walls.WallChartAndSchematicRenderer
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignResult
import com.archilab.rpa2024.engine.walls.WallEnvelopeGenerator
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.WallSlenderness
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.PierForceRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Validation du moteur des voiles (RPA 2024 § 7.7 + BAEL 91) sur un voile
 * lw = 1,40 m ; bw = 0,30 m ; he = 3,39 m ; Hw = 11,22 m ;
 * Nd = 2944 kN ; Vu = 1043 kN ; Mbase = 2317,83 kN.m.
 */
class ShearWallEngineTest {

    private val materials = MaterialConfig(fc28MPa = 25.0, feMPa = 500.0)

    private val wall = ShearWallFamily(
        pierName = "PV1",
        direction = AxisDirection.X,
        lengthLwMeters = 1.40,
        thicknessBwMeters = 0.30,
        clearHeightHeMeters = 3.39,
        totalHeightHwMeters = 11.22,
        stories = listOf("RDC", "E1", "E2"),
        elementIds = listOf("W1")
    )

    private val rows = listOf(
        row("RDC", "Bottom", 0.0, -2944.0, 1043.0, 2317.83),
        row("RDC", "Top", 3.74, -2500.0, 900.0, 1900.0),
        row("E1", "Top", 7.48, -1800.0, 700.0, 1200.0),
        row("E2", "Top", 11.22, -900.0, 400.0, 300.0)
    )

    private val results = EtabsResults(pierForces = rows)
    private val qualificationEngine = ShearWallQualificationAndChecks()
    private val envelopeGenerator = WallEnvelopeGenerator()
    private val designer = WallCriticalZoneAndDuctilityDesigner()

    private val qualification = qualificationEngine.assess(listOf(wall), results, materials, storyCount = 3)
    private val qualified = qualification.walls.single()
    private val envelope = envelopeGenerator.generate(wall, qualified.forces, rows, storyCount = 3)
    private val designSet = designer.design(listOf(wall), results, materials)
    private val design: WallDesignResult = designSet.walls.single()

    @Test
    @DisplayName("Qualification geometrique, effort normal reduit et classification RPA 2024")
    fun qualificationGeometriqueEtEffortNormal() {
        assertEquals(1.20, qualified.requiredLengthMeters, 1e-9)
        assertEquals(0.1695, qualified.requiredThicknessMeters, 1e-9)
        assertTrue(wall.lengthLwMeters >= qualified.requiredLengthMeters)
        assertTrue(wall.thicknessBwMeters >= qualified.requiredThicknessMeters)
        assertEquals(WallSlenderness.SLENDER_WALL, qualified.classification)

        assertEquals(0.2804, qualified.normalizedAxial, 1e-4)
        assertEquals(1.40 * 0.30, qualified.concreteAreaM2, 1e-9)
        assertEquals(CheckStatus.OK, checkStatus("PSW1_LONGUEUR"))
        assertEquals(CheckStatus.OK, checkStatus("PSW2_EPAISSEUR"))
        assertEquals(CheckStatus.OK, checkStatus("PSW4_NU"))
        assertTrue(qualified.forces.hasSeismicForces)
        assertEquals(2944.0, qualified.forces.axialCompressionKN, 1e-9)
    }

    @Test
    @DisplayName("Cisaillement de calcul Vbarre = 1,40 Vu et tau_b <= 0,20 fc28")
    fun cisaillementDeCalcul() {
        assertEquals(1460.2, qualified.shearDesignKN, 1e-6)
        assertEquals(1.26, qualified.effectiveDepthMeters, 1e-9)
        assertEquals(3.863, qualified.tauMPa, 1e-3)
        assertEquals(5.0, qualified.tauLimitMPa, 1e-9)
        assertEquals(CheckStatus.OK, checkStatus("PSW5_CISAILLEMENT"))
        assertTrue(qualified.tauMPa < qualified.tauLimitMPa)
    }

    @Test
    @DisplayName("Hauteur critique h_cr et enveloppes decalees des moments et efforts tranchants")
    fun hauteurCritiqueEtEnveloppes() {
        val critical = envelope.criticalHeight
        assertEquals(1.87, critical.baseMeters, 1e-9)
        assertEquals(2.80, critical.capMeters, 1e-9)
        assertEquals(1.87, critical.retainedMeters, 1e-9)
        assertFalse(critical.isCapped)
        assertEquals(listOf("RDC"), critical.coveredStories)

        assertEquals(2317.83, envelope.moment.shiftedAt(0.0), 1e-6)
        assertEquals(2317.83, envelope.moment.shiftedAt(1.87), 1e-6)
        assertEquals(300.0, envelope.moment.shiftedAt(11.22), 1e-6)
        assertEquals(2317.83, envelope.moment.linearAt(0.0), 1e-6)
        assertEquals(300.0, envelope.moment.linearAt(11.22), 1e-6)

        assertEquals(1460.2, envelope.shear.designAt(0.0), 1e-6)
        assertEquals(1460.2, envelope.shear.designAt(3.74), 1e-6)
        assertEquals(730.1, envelope.shear.designAt(11.22), 1e-6)
        assertEquals(1460.2, envelope.shear.amplifiedAt(0.0), 1e-6)
        assertEquals(1460.2, envelope.shear.amplifiedBaseKN, 1e-6)
    }

    @Test
    @DisplayName("Flexion composee accidentelle a la base (BaelRectangularSolver)")
    fun flexionComposeeAlaBase() {
        assertEquals(2944.0, design.designAxialKN, 1e-9)
        assertEquals(2317.83, design.designMomentKNm, 1e-9)
        assertEquals(BaelSectionState.PARTIELLEMENT_COMPRIMEE, design.bael.state)
        // Reference de la fiche de calcul : A_inf environ 25 cm2 et y0 environ 0,78 m.
        assertEquals(25.03, design.bael.tensionSteelCm2, 3.0)
        assertEquals(0.78, design.bael.neutralAxisMeters, 0.03)
        assertEquals(3.0, design.bael.section.tensionCoverCm, 1e-9)
        assertEquals(30.0, design.bael.section.bCm, 1e-9)
        assertEquals(140.0, design.bael.section.hCm, 1e-9)
    }

    @Test
    @DisplayName("Elements de rive confines, cadres et ductilite locale")
    fun elementsDeRiveEtDuctilite() {
        val boundary = design.boundaryLeft
        assertEquals(45.0, boundary.lengthLcCm, 1e-9)
        assertEquals(30.0, boundary.thicknessBcCm, 1e-9)
        assertEquals(1350.0, boundary.areaAcCm2, 1e-9)
        assertEquals(25.0, boundary.coreWidthB0Cm, 1e-9)
        assertEquals(40.0, boundary.coreHeightH0Cm, 1e-9)
        assertEquals(6.75, boundary.minimumAsCm2, 1e-9)
        assertEquals(0, boundary.bars.count % 2)
        assertTrue(boundary.bars.diameterMm >= 12)
        assertTrue(boundary.adoptedAsCm2 + 1e-9 >= boundary.requiredAsCm2)
        assertEquals(8.0, boundary.stirrupSpacingCm, 1e-9)
        assertEquals(0.9, boundary.requiredTransverseCm2, 1e-9)
        assertTrue(boundary.providedTransverseCm2 + 1e-9 >= boundary.requiredTransverseCm2)
        assertEquals(2, boundary.stirrupLegs)
        assertEquals(8, boundary.stirrupDiameterMm)

        assertEquals(boundary.adoptedAsCm2, design.boundaryRight.adoptedAsCm2, 1e-9)
        assertEquals(CheckStatus.OK, checkStatus("PSW9_RIVE_LONGUEUR"))
        assertEquals(CheckStatus.OK, checkStatus("PSW10_RIVE_ARMATURES"))
        assertEquals(CheckStatus.OK, checkStatus("PSW12_RIVE_ESPACEMENT"))
        assertEquals(CheckStatus.OK, checkStatus("PSW13_RIVE_CONFINEMENT"))
        assertEquals(CheckStatus.OK, checkStatus("PSW14_LONGUEUR_CONFINEMENT"))

        assertEquals(6.0, design.ductility.requiredCurvatureDuctility, 1e-9)
        assertEquals(0.4907, design.ductility.ultimateCompressedHeightMeters, 1e-3)
        assertEquals(0.1005, design.ductility.confinementMechanicalRatio, 1e-3)
        assertTrue(design.ductility.isConfinementLengthSatisfied)
        assertTrue(design.ductility.availableCurvatureDuctility > 0.0)
    }

    @Test
    @DisplayName("Nappes verticales et horizontales de l'ame et verification du cisaillement")
    fun ferraillageDeLame() {
        val web = design.web
        assertEquals(50.0, web.lengthCm, 1e-9)
        assertEquals(2, web.layers)
        assertEquals(30.0, web.verticalSpacingCm, 1e-9)
        assertEquals(30.0, web.horizontalSpacingCm, 1e-9)
        assertEquals(0.0025, web.verticalRatio, 1e-3)
        assertEquals(0.0025, web.horizontalRatio, 1e-3)
        assertTrue(web.verticalRatio + 1e-9 >= 0.0020)
        assertTrue(web.horizontalRatio + 1e-9 >= 0.0020)
        assertEquals(5.0, web.tauLimitMPa, 1e-9)
        assertEquals(CheckStatus.OK, checkStatus("PSW16_AME_VERTICAL"))
        assertEquals(CheckStatus.OK, checkStatus("PSW17_AME_HORIZONTAL"))
        assertEquals(CheckStatus.OK, checkStatus("PSW18_AME_ESPACEMENT"))
        assertEquals(CheckStatus.OK, checkStatus("PSW19_AME_CISAILLEMENT"))
    }

    @Test
    @DisplayName("Rendu SVG des deux diagrammes et de la coupe horizontale")
    fun renduGraphique() {
        val renderer = WallChartAndSchematicRenderer()
        val moment = renderer.renderMomentChart(design)
        val shear = renderer.renderShearChart(design)
        val section = renderer.renderSection(design)
        val combined = renderer.render(design)

        assertTrue(moment.startsWith("<?xml"))
        assertTrue(moment.contains("<svg"))
        assertTrue(moment.contains("Moments fléchissants"))
        assertTrue(moment.contains("h_cr"))
        assertTrue(moment.contains("Enveloppe décalée"))
        assertTrue(shear.contains("Efforts tranchants"))
        assertTrue(shear.contains("1,40 Vu"))
        assertTrue(shear.contains("Enveloppe de calcul"))
        assertTrue(section.contains("Coupe horizontale"))
        assertTrue(section.contains("Rive gauche"))
        assertTrue(section.contains("lw = 140"))
        assertTrue(section.contains("bw = 30"))
        assertTrue(section.contains("Sv = 30"))
        assertTrue(section.contains("Sh = 30"))
        assertTrue(combined.contains("Moments fléchissants"))
        assertTrue(combined.contains("Efforts tranchants"))
        assertTrue(combined.contains("Coupe horizontale"))
        assertTrue(combined.contains("<pattern id=\"wallHatch\""))
    }

    @Test
    @DisplayName("Le moteur parcourt dynamiquement tous les voiles et toutes les geometries")
    fun traitementDynamiqueDeTousLesVoiles() {
        val second = ShearWallFamily(
            pierName = "PV2",
            direction = AxisDirection.Y,
            lengthLwMeters = 2.40,
            thicknessBwMeters = 0.25,
            clearHeightHeMeters = 3.00,
            totalHeightHwMeters = 9.00,
            stories = listOf("RDC", "E1", "E2")
        )
        val secondRows = listOf(
            row("RDC", "Bottom", 0.0, -1200.0, 600.0, 900.0, pier = "PV2"),
            row("E1", "Top", 6.00, -700.0, 350.0, 500.0, pier = "PV2"),
            row("E2", "Top", 9.00, -300.0, 150.0, 200.0, pier = "PV2")
        )
        val set = designer.design(
            walls = listOf(wall, second),
            results = EtabsResults(pierForces = rows + secondRows),
            materials = materials
        )

        assertEquals(2, set.walls.size)
        assertEquals(listOf("PV1", "PV2"), set.walls.map { it.family.pierName })
        val secondDesign = set.walls.first { it.family.pierName == "PV2" }
        assertEquals(9.00, secondDesign.envelope.heightMeters, 1e-9)
        assertEquals(2.40, secondDesign.envelope.criticalHeight.baseMeters, 1e-9)
        assertEquals(45.0, secondDesign.boundaryLeft.lengthLcCm, 1e-9)
        assertTrue(secondDesign.boundaryLeft.lengthLcCm >= 1.5 * 25.0)
        assertTrue(set.chapter.elements.size == 2)
    }

    private fun checkStatus(code: String): CheckStatus =
        design.checks.first { it.code == code }.status

    private fun row(
        story: String,
        location: String,
        station: Double,
        p: Double,
        v2: Double,
        m3: Double,
        pier: String = "PV1"
    ): PierForceRow = PierForceRow(
        story = story,
        pier = pier,
        loadCase = "E1",
        location = location,
        stationMeters = station,
        pKN = p,
        v2KN = v2,
        v3KN = 0.0,
        m2KNm = 0.0,
        m3KNm = m3
    )
}
