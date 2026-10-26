package com.archilab.rpa2024

import com.archilab.rpa2024.engine.walls.*
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.PierLevelForce
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.WallSlenderness
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ShearWallEngineTest {

    private val qualificationEngine = ShearWallQualificationAndChecks()
    private val envelopeGenerator = WallEnvelopeGenerator()
    private val ductilityDesigner = WallCriticalZoneAndDuctilityDesigner()
    private val schematicRenderer = WallChartAndSchematicRenderer()

    @Test
    fun testShearWallGeometricQualificationAndPreChecks() {
        // Validation sur le voile modèle extrait d'ETABS (Chapitre 10.3) :
        // lw = 1.40 m, bw = 0.30 m, he = 3.39 m, Hw = 11.22 m, fc28 = 25 MPa
        val wall = ShearWallFamily(
            pierName = "V1",
            direction = AxisDirection.X,
            lengthLwMeters = 1.40,
            thicknessBwMeters = 0.30,
            clearHeightHeMeters = 3.39,
            totalHeightHwMeters = 11.22,
            stories = listOf("H RDC", "H ETAGE 1", "H ETAGE 2")
        )

        // 1. Qualification géométrique
        val geom = qualificationEngine.qualifyGeometry(wall)
        assertEquals(1.13, geom.minLengthHeOver3, 0.01) // he / 3 = 3.39 / 3 = 1.13 m
        assertEquals(1.20, geom.minLength4Bw, 0.01)     // 4 * bw = 4 * 0.30 = 1.20 m
        assertEquals(1.20, geom.minLengthRequiredMeters, 0.01)
        assertTrue(geom.isWallConditionSatisfied)        // lw = 1.40 >= 1.20 m
        assertEquals(0.17, geom.minThicknessRequiredMeters, 0.01) // he / 20 = 3.39 / 20 = 0.17 m
        assertTrue(geom.isThicknessSatisfied)           // bw = 0.30 >= 0.17 m
        assertEquals(WallSlenderness.SLENDER_WALL, geom.classification)
        assertFalse(geom.reclassifiedAsColumn)

        // 2. Effort normal réduit (§ 7.7.2 - Éq. 7.11 & 7.22)
        // Nd = 2944 kN, Bc = 1.40 * 0.30 = 0.42 m², fc28 = 25000 kPa -> nu_d = 0.280 <= 0.40
        val axial = qualificationEngine.checkReducedAxialForce(
            pierName = "V1",
            lengthLwMeters = wall.lengthLwMeters,
            thicknessBwMeters = wall.thicknessBwMeters,
            maxCompressionNdKN = 2944.0,
            fc28MPa = 25.0
        )
        assertEquals(0.42, axial.grossAreaBcM2, 0.001)
        assertEquals(0.280, axial.nuD, 0.005)
        assertEquals(0.40, axial.nuDLimit, 0.001)
        assertTrue(axial.isCompliant)

        // 3. Contrainte limite de cisaillement (§ 7.7.3)
        // Vu = 1043 kN -> V_bar = 1.4 * 1043 = 1460.2 kN
        // d = 0.9 * 1.40 = 1.26 m, tau_b = 1460.2 / (0.30 * 1.26) = 3863 kPa = 3.863 MPa <= 5.00 MPa
        val shear = qualificationEngine.checkShearStress(
            pierName = "V1",
            lengthLwMeters = wall.lengthLwMeters,
            thicknessBwMeters = wall.thicknessBwMeters,
            vuMaxKN = 1043.0,
            fc28MPa = 25.0
        )
        assertEquals(1.26, shear.effectiveDepthDMeters, 0.01)
        assertEquals(1460.2, shear.vBarKN, 0.1)
        assertEquals(3.863, shear.tauBMPa, 0.01)
        assertEquals(5.00, shear.tauBLimitMPa, 0.01)
        assertTrue(shear.isCompliant)
    }

    @Test
    fun testReclassificationAsColumnWhenWallConditionFails() {
        // Cas d'un faux voile de longueur trop courte (ex: 0.80 m < 1.00 m)
        val shortElement = ShearWallFamily(
            pierName = "V_SHORT",
            direction = AxisDirection.Y,
            lengthLwMeters = 0.80,
            thicknessBwMeters = 0.30,
            clearHeightHeMeters = 3.00,
            totalHeightHwMeters = 9.00
        )
        val geom = qualificationEngine.qualifyGeometry(shortElement)
        assertFalse(geom.isWallConditionSatisfied)
        assertTrue(geom.reclassifiedAsColumn)
    }

    @Test
    fun testWallEnvelopeGeneratorMomentsAndShears() {
        val wall = ShearWallFamily(
            pierName = "V1",
            direction = AxisDirection.X,
            lengthLwMeters = 1.40,
            thicknessBwMeters = 0.30,
            clearHeightHeMeters = 3.39,
            totalHeightHwMeters = 11.22,
            stories = listOf("H RDC", "H ETAGE 1", "H ETAGE 2")
        )

        val verticalForces = listOf(
            PierLevelForce("H RDC", 0.0, 2675.81, 2317.83, 1043.0),
            PierLevelForce("H ETAGE 1", 3.74, 1800.0, 1500.0, 850.0),
            PierLevelForce("H ETAGE 2", 7.48, 900.0, 700.0, 500.0),
            PierLevelForce("TERRASSE", 11.22, 150.0, 120.0, 150.0)
        )

        val rep = envelopeGenerator.generateFullEnvelopeReport(
            wall = wall,
            numberOfStories = 3,
            verticalForces = verticalForces,
            storyHeights = mapOf("H RDC" to 3.74, "H ETAGE 1" to 3.74, "H ETAGE 2" to 3.74)
        )

        // Hauteur de zone critique hcr
        // hcr_base = max(1.40, 11.22 / 6 = 1.87) = 1.87 m
        // hcr_max = min(2 * 1.40 = 2.80, 3.39) = 2.80 m
        // hcr_adopted = 1.87 m
        assertEquals(1.87, rep.criticalZoneHeightHcrAdoptedMeters, 0.01)
        assertTrue(rep.criticalZoneStories.contains("H RDC"))

        // Enveloppe de moments fléchissants M(z)
        val momentEnv = rep.momentEnvelope
        assertEquals(2317.83, momentEnv.baseMomentKNm, 0.1)
        assertEquals(120.0, momentEnv.topMomentKNm, 0.1)

        // Sur le palier [0, hcr], le moment de calcul décalé est égal à M_base
        val shiftedBase = momentEnv.shiftedDesignEnvelopeCurve.first { it.zMeters == 0.0 }
        assertEquals(2317.83, shiftedBase.value, 0.1)

        // Enveloppe d'efforts tranchants T(z)
        val shearEnv = rep.shearEnvelope
        assertEquals(1043.0, shearEnv.baseShearVuKN, 0.1)
        assertEquals(1460.2, shearEnv.amplifiedBaseShearVBarKN, 0.1)
        assertEquals(730.1, shearEnv.topShearLimitKN, 0.1) // 1460.2 / 2 = 730.1 kN

        // Sur le premier tiers [0, Hw/3 = 3.74 m], le palier est maintenu à V_bar
        val plateauPoint = shearEnv.piecewiseDesignEnvelopeCurve.first { it.zMeters == 0.0 }
        assertEquals(1460.2, plateauPoint.value, 0.1)
    }

    @Test
    fun testWallCriticalZoneAndBoundaryDuctilityDesign() {
        val wall = ShearWallFamily(
            pierName = "V1",
            direction = AxisDirection.X,
            lengthLwMeters = 1.40,
            thicknessBwMeters = 0.30,
            clearHeightHeMeters = 3.39,
            totalHeightHwMeters = 11.22,
            stories = listOf("H RDC", "H ETAGE 1", "H ETAGE 2")
        )

        // Cas de charge sismique à la base (Pages 38 et 40 de la note) :
        // Nd = 2675.81 kN, M_base = 2317.83 kN.m, Vu = 1043 kN
        val rep = ductilityDesigner.designShearWall(
            wall = wall,
            designAxialForceNdKN = 2675.81,
            designMomentMKNm = 2317.83,
            designShearVuKN = 1043.0,
            fc28MPa = 25.0,
            feMPa = 500.0,
            targetLcMeters = 0.60
        )

        // 1. Flexion composée à la base (BaelR)
        // Position de l'axe neutre y0 ~ 0.77 - 0.78 m
        assertEquals(0.776, rep.neutralAxisY0Meters, 0.03)
        // Armatures de flexion composée calculées Av ~ 24 - 25 cm²
        assertTrue(rep.requiredTensionSteelAreaAvCalculCm2 >= 23.5)
        assertTrue(rep.requiredTensionSteelAreaAvCalculCm2 <= 25.5)

        // 2. Éléments de rive confinés (Boundary Elements)
        val left = rep.leftBoundaryElement
        assertEquals(0.60, left.lengthLcMeters, 0.01)
        assertEquals(0.30, left.thicknessBcMeters, 0.01)
        assertEquals(9.00, left.minRebarAreaCm2, 0.01) // 0.5% * 30 * 60 = 9.00 cm²

        // Armatures adoptées en rive : 16T14 = 24.63 cm² ou 16T16
        assertTrue(left.adoptedRebarAreaCm2 >= 24.0)
        assertEquals(14.0, left.phiLChosenMm, 0.01)

        // Confinement et espacement St = 8 cm
        assertEquals(8.0, left.verticalTieSpacingStCm, 0.01)
        assertTrue(left.adoptedTransverseAreaAtCm2 >= left.minTransverseAreaAtCm2)
        assertTrue(left.isConfinementCompliant)

        // 3. Ferraillage de l'âme du voile
        val web = rep.webReinforcement
        assertEquals(0.20, web.clearWebLengthMeters, 0.01) // 1.40 - 2 * 0.60 = 0.20 m
        assertEquals(15.0, web.verticalSpacingSvCm, 0.01)
        assertEquals(15.0, web.horizontalSpacingShCm, 0.01)
        assertTrue(web.shearFrictionAreaAvjCm2 > 0.0) // Avj = 1.1 * V_bar / fe
        assertTrue(web.isWebCompliant)

        // 4. Indicateurs de ductilité locale
        val duct = rep.ductilityIndicators
        assertEquals(0.28, duct.reducedAxialForceNuD, 0.04)
        assertTrue(duct.isDuctilityCompliant)
        assertTrue(rep.isGloballyCompliant)
    }

    @Test
    fun testWallChartAndSchematicSvgRendering() {
        val wall = ShearWallFamily(
            pierName = "V1",
            direction = AxisDirection.X,
            lengthLwMeters = 1.40,
            thicknessBwMeters = 0.30,
            clearHeightHeMeters = 3.39,
            totalHeightHwMeters = 11.22,
            stories = listOf("H RDC", "H ETAGE 1", "H ETAGE 2")
        )

        val verticalForces = listOf(
            PierLevelForce("H RDC", 0.0, 2675.81, 2317.83, 1043.0),
            PierLevelForce("H ETAGE 1", 3.74, 1800.0, 1500.0, 850.0),
            PierLevelForce("H ETAGE 2", 7.48, 900.0, 700.0, 500.0),
            PierLevelForce("TERRASSE", 11.22, 150.0, 120.0, 150.0)
        )

        val envReport = envelopeGenerator.generateFullEnvelopeReport(
            wall = wall,
            numberOfStories = 3,
            verticalForces = verticalForces
        )

        // 1. Rendu SVG de l'enveloppe des moments M(z)
        val momentSvg = schematicRenderer.renderMomentEnvelopeSvg(envReport.momentEnvelope)
        assertTrue(momentSvg.startsWith("<svg"))
        assertTrue(momentSvg.contains("Enveloppe des Moments Fléchissants M(z) - Voile V1"))
        assertTrue(momentSvg.contains("Zone Critique hcr"))
        assertTrue(momentSvg.contains("3. Enveloppe Décalée (hcr)"))

        // 2. Rendu SVG de l'enveloppe des efforts tranchants T(z)
        val shearSvg = schematicRenderer.renderShearEnvelopeSvg(envReport.shearEnvelope)
        assertTrue(shearSvg.startsWith("<svg"))
        assertTrue(shearSvg.contains("Enveloppe des Efforts Tranchants T(z) - Voile V1"))
        assertTrue(shearSvg.contains("hw / 3"))

        // 3. Rendu SVG de la coupe transversale
        val crossSectionSvg = schematicRenderer.renderWallCrossSectionSvg(
            pierName = "V1",
            lengthLwMeters = 1.40,
            thicknessBwMeters = 0.30,
            boundaryLengthLcMeters = 0.60,
            boundaryRebarDesc = "16T14",
            webRebarDesc = "2 nappes T12 e=15cm",
            tieSpacingStCm = 8.0
        )
        assertTrue(crossSectionSvg.startsWith("<svg"))
        assertTrue(crossSectionSvg.contains("Coupe Transversale de Ferraillage du Voile : V1"))
        assertTrue(crossSectionSvg.contains("lc = 0.6m"))
        assertTrue(crossSectionSvg.contains("16T14"))
    }
}
