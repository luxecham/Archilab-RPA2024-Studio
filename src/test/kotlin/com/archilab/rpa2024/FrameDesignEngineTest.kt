package com.archilab.rpa2024

import com.archilab.rpa2024.engine.frames.*
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.SeismicZone
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

class FrameDesignEngineTest {

    private val baelSolver = BaelRectangularSolver()
    private val columnEngine = ColumnDesignEngine(baelSolver)
    private val beamEngine = BeamDesignEngine(baelSolver)
    private val schematicRenderer = FrameSchematicRenderer()

    @Test
    fun testBaelRectangularSolverSimpleBending() {
        // Poutre P.Prin 30x55 sous moment de travée Mu = 136.63 kN.m (Page 31)
        val input = BaelRInput(
            widthBMeters = 0.30,
            heightHMeters = 0.55,
            dPrimeMeters = 0.025,
            cMeters = 0.025,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = 0.0,
            momentMKNm = 136.63,
            situation = DesignSituation.DURABLE_ELU
        )

        val res = baelSolver.solve(input)

        assertEquals(SectionStressState.FLEXION_SIMPLE, res.state)
        assertTrue(res.neutralAxisY0Meters > 0.0)
        assertEquals(0.0, res.aSupCm2, 0.001)
        // Section théorique calculée ~6.49 cm²
        assertEquals(6.49, res.aInfCm2, 0.40)
        assertTrue(res.isCompliant)
    }

    @Test
    fun testBaelRectangularSolverCombinedBendingMatchingBaelRSheet() {
        // Validation exacte basée sur la capture BaelR (Page 28 du document) :
        // Section b = 0.35 m, h = 0.55 m, d' = 0.03 m, c = 0.03 m, fc28 = 25 MPa, fe = 500 MPa
        // Situation accidentelle : theta = 0.85, gamma_b = 1.20, gamma_s = 1.00
        // N = 249 kN (compression), M = 70 kN.m
        val input = BaelRInput(
            widthBMeters = 0.35,
            heightHMeters = 0.55,
            dPrimeMeters = 0.03,
            cMeters = 0.03,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = 249.0, // Compression
            momentMKNm = 70.0,
            situation = DesignSituation.ACCIDENTAL_SEISMIC,
            thetaDuration = 0.85
        )

        val res = baelSolver.solve(input)

        // BaelR calcul en flexion fictive : Afict ~ 5.22 cm²
        // Armatures réelles après soustraction de l'effort normal : Ainf = Afict - N/fsu = 0.24 cm², Asup = 0 cm²
        assertEquals(0.0, res.aSupCm2, 0.001)
        assertEquals(5.22, res.aFictCm2, 0.20)
        assertEquals(0.24, res.aInfCm2, 0.05)
        assertTrue(res.isCompliant)

        // Cas Section Entièrement Tendue (SET) sous traction axiale pure ou forte traction
        val inputSET = BaelRInput(
            widthBMeters = 0.35,
            heightHMeters = 0.55,
            dPrimeMeters = 0.03,
            cMeters = 0.03,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = -300.0, // Traction
            momentMKNm = 10.0,
            situation = DesignSituation.DURABLE_ELU
        )
        val resSET = baelSolver.solve(inputSET)
        assertEquals(SectionStressState.SECTION_ENTIEREMENT_TENDUE, resSET.state)
        assertEquals(0.0, resSET.neutralAxisY0Meters, 0.001)
        assertTrue(resSET.aSupCm2 > 0.0)
        assertTrue(resSET.aInfCm2 > 0.0)

        // Cas Flexion composée avec moment négatif (Inversion des nappes de ferraillage)
        val inputNegM = BaelRInput(
            widthBMeters = 0.35,
            heightHMeters = 0.55,
            dPrimeMeters = 0.03,
            cMeters = 0.03,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = 249.0, // Compression
            momentMKNm = -70.0,   // Moment négatif (traction en fibre supérieure)
            situation = DesignSituation.ACCIDENTAL_SEISMIC,
            thetaDuration = 0.85
        )
        val resNegM = baelSolver.solve(inputNegM)
        assertEquals(SectionStressState.SECTION_PARTIELLEMENT_COMPRIMEE, resNegM.state)
        assertEquals(0.24, resNegM.aSupCm2, 0.05) // Armature tendue en haut
        assertEquals(0.0, resNegM.aInfCm2, 0.001) // Armature comprimée en bas
        assertTrue(resNegM.isCompliant)
    }

    @Test
    fun testColumnDesignEngineMatchingNoteDeCalcul() {
        // Validation des 3 familles de poteaux de la Note de Calcul (Page 27 à 30)
        // Zone III -> Amin RPA = 0.90% * b * h

        // 1. Poteau POT 35x55 (RDC)
        val col35x55 = ColumnFamily(
            name = "POT 35X55",
            shape = ColumnShape.RECTANGULAR,
            b1Cm = 35.0,
            h1Cm = 55.0,
            clearHeightLclMeters = 3.24
        )
        val env35x55 = ColumnForcesEnvelope(
            columnFamilyName = "POT 35X55",
            maxCompressionNdKN = 1080.39,
            maxCompressionStory = "H RDC",
            maxCompressionColumnId = "C106",
            eluTorseur = ForceTorseur(-1080.39, 2.48, 7.14),
            elaTorseur = ForceTorseur(248.30, 69.86, 32.02),
            v2MaxKN = 73.0,
            v3MaxKN = 73.0
        )
        val rep35x55 = columnEngine.designColumn(col35x55, env35x55, SeismicZone.ZONE_III)

        assertEquals(0.90, rep35x55.aMinRpaPercent, 0.001)
        assertEquals(17.325, rep35x55.aMinRpaCm2, 0.01)
        // Ferraillage adopté >= 17.28 cm² (Page 27 : 4T16 + 6T14 = 17.28 cm² ou 12T14 = 18.48 cm²)
        assertTrue(rep35x55.aAdoptedCm2 >= 17.20)
        assertTrue(rep35x55.isSpacingCompliant)

        // Armatures transversales POT 35x55 (Page 29)
        val trans35x55 = rep35x55.transverseDesign
        assertEquals(10, trans35x55.numBrins)
        assertEquals(0.80, trans35x55.phiTChosenCm, 0.01)
        assertEquals(5.03, trans35x55.atChosenCm2, 0.05)
        assertEquals(10.0, trans35x55.nodalSpacingStCm, 0.01)
        assertEquals(15.0, trans35x55.currentSpacingStPrimeCm, 0.01)
        assertEquals(70.0, trans35x55.overlapLengthLrecCm, 0.01) // 50 * phiL = 50 * 1.4 = 70 cm
        assertTrue(trans35x55.directionalShearYY.isShearCompliant)
        assertTrue(trans35x55.directionalShearZZ.isShearCompliant)
        assertTrue(trans35x55.isCompliant)

        // 2. Poteau POT 40x50 (RDC)
        val col40x50 = ColumnFamily(
            name = "POT 40X50",
            shape = ColumnShape.RECTANGULAR,
            b1Cm = 40.0,
            h1Cm = 50.0,
            clearHeightLclMeters = 3.24
        )
        val env40x50 = ColumnForcesEnvelope(
            columnFamilyName = "POT 40X50",
            maxCompressionNdKN = 1495.43,
            maxCompressionStory = "H RDC",
            maxCompressionColumnId = "C111",
            eluTorseur = ForceTorseur(-1495.43, -50.14, -31.41),
            elaTorseur = ForceTorseur(903.84, 51.23, 20.98),
            v2MaxKN = 49.0,
            v3MaxKN = 220.0
        )
        val rep40x50 = columnEngine.designColumn(col40x50, env40x50, SeismicZone.ZONE_III)
        assertEquals(18.00, rep40x50.aMinRpaCm2, 0.01)
        assertTrue(rep40x50.aAdoptedCm2 >= 18.00) // 12T14 = 18.48 cm²

        // 3. Poteau POT 35x35 (RDC)
        val col35x35 = ColumnFamily(
            name = "POT 35X35",
            shape = ColumnShape.RECTANGULAR,
            b1Cm = 35.0,
            h1Cm = 35.0,
            clearHeightLclMeters = 3.24
        )
        val env35x35 = ColumnForcesEnvelope(
            columnFamilyName = "POT 35X35",
            maxCompressionNdKN = 446.0,
            maxCompressionStory = "H RDC",
            maxCompressionColumnId = "C127",
            eluTorseur = ForceTorseur(-656.20, 5.57, 12.89),
            elaTorseur = ForceTorseur(199.56, 9.89, 31.82),
            v2MaxKN = 36.0,
            v3MaxKN = 25.0
        )
        val rep35x35 = columnEngine.designColumn(col35x35, env35x35, SeismicZone.ZONE_III)
        assertEquals(11.025, rep35x35.aMinRpaCm2, 0.01)
        assertTrue(rep35x35.aAdoptedCm2 >= 11.025) // 8T14 = 12.32 cm²
    }

    @Test
    fun testBeamDesignEngineMatchingNoteDeCalcul() {
        // Validation Poutre Principale P.Prin 30x55 (Page 31 et 33)
        val beam30x55 = BeamFamily(
            name = "P.Prin 30x55",
            role = BeamRole.MAIN_BEAM,
            bCm = 30.0,
            hCm = 55.0,
            maxClearSpanMeters = 4.35
        )
        val env30x55 = BeamForcesEnvelope(
            beamFamilyName = "P.Prin 30x55",
            mSpanMaxKNm = 136.633,
            mSupportMaxKNm = -161.697,
            vuMaxKN = 178.00,
            eluSpanM = 136.633,
            eluSupportM = -161.697
        )

        val rep30x55 = beamEngine.designBeam(beam30x55, env30x55, SeismicZone.ZONE_III)

        // 1. Armatures minimales RPA 2024 : 0.5% * b * h = 0.005 * 30 * 55 = 8.25 cm²
        assertEquals(8.25, rep30x55.spanRebars.minRpaAreaCm2, 0.01)

        // 2. Ferraillage adopté conforme page 31 :
        // Travée : 3T14 filantes + 2T12 chapeaux = 6.88 cm²
        assertEquals("3T14", rep30x55.spanRebars.filantesDescription)
        assertEquals("2T12", rep30x55.spanRebars.chapeauxDescription)
        assertEquals(6.88, rep30x55.spanRebars.totalAdoptedAreaCm2, 0.10)

        // Appui : 3T14 filantes + 3T14 chapeaux = 9.24 cm²
        assertEquals("3T14", rep30x55.supportRebars.filantesDescription)
        assertEquals("3T14", rep30x55.supportRebars.chapeauxDescription)
        assertEquals(9.24, rep30x55.supportRebars.totalAdoptedAreaCm2, 0.10)

        // 3. Les 4 vérifications à l'effort tranchant (Page 33)
        val shear = rep30x55.shearChecks
        // 1. Contrainte tangente tau_u <= 3.33 MPa (valeur page 33: 1.20 MPa)
        assertEquals(1.13, shear.tauUMPa, 0.10)
        assertEquals(3.33, shear.tauUAdmissibleMPa, 0.01)
        assertTrue(shear.isTauUCompliant)

        // 2. Compression de la bielle (limite 911.14 kN >= 178 kN)
        assertTrue(shear.vuBielleLimitKN >= shear.vuKN)
        assertTrue(shear.isBielleCompliant)

        // 3. Armatures inférieures d'appui (Vu / (fe/gamma_s) = 4.09 cm² <= As_inf)
        assertEquals(4.09, shear.asInfRequiredCm2, 0.10)
        assertTrue(shear.isAsInfCompliant)

        // 4. Contrainte moyenne de compression (sigma_mb <= 21.67 MPa)
        assertEquals(21.67, shear.sigmaMbLimitMPa, 0.01)
        assertTrue(shear.isSigmaMbCompliant)
        assertTrue(shear.allShearChecksCompliant)

        // 4. Armatures transversales (Page 33) :
        // l' = 82.5 cm, S = 8 cm (nodale), S' = 15 cm (courante), Lrec = 84 cm (60 phiL)
        val trans = rep30x55.transverseDetails
        assertEquals(82.5, trans.nodalZoneLengthLPrimeCm, 0.1)
        assertEquals(8.0, trans.nodalSpacingSCm, 0.01)
        assertEquals(15.0, trans.currentSpacingSPrimeCm, 0.01)
        assertEquals(84.0, trans.overlapLengthLrecCm, 0.01)
        assertEquals(2.01, trans.atChosenCm2, 0.05) // 4 brins phi 8 mm
    }

    @Test
    fun testFrameSchematicRendererSvgOutput() {
        // 1. Coupe transversale BaelR
        val baelSvg = schematicRenderer.renderBaelRCrossSectionSvg(
            widthBCm = 35.0,
            heightHCm = 55.0,
            y0Meters = 0.15,
            aSupDescription = "3T14",
            aInfDescription = "4T16"
        )
        assertTrue(baelSvg.startsWith("<svg"))
        assertTrue(baelSvg.contains("Section BaelR : 35 x 55 cm"))
        assertTrue(baelSvg.contains("Axe neutre y0"))

        // 2. Elévation de ferraillage poteau
        val colSvg = schematicRenderer.renderColumnElevationSvg(
            colName = "POT 35X55",
            bCm = 35.0,
            hCm = 55.0,
            lclMeters = 3.24,
            lcrCm = 82.5,
            stNodalCm = 10.0,
            stCurrentCm = 15.0,
            lrecCm = 70.0,
            rebarDesc = "4T16 + 6T14"
        )
        assertTrue(colSvg.startsWith("<svg"))
        assertTrue(colSvg.contains("Ferraillage Poteau : POT 35X55"))
        assertTrue(colSvg.contains("Zone nodale Lcr = 82 cm"))

        // 3. Elévation de ferraillage poutre
        val beamSvg = schematicRenderer.renderBeamElevationSvg(
            beamName = "P.Prin 30x55",
            bCm = 30.0,
            hCm = 55.0,
            lPrimeCm = 82.5,
            stNodalCm = 8.0,
            stCurrentCm = 15.0,
            spanRebarDesc = "3T14 + 2T12",
            supportRebarDesc = "3T14 + 3T14",
            lrecCm = 84.0
        )
        assertTrue(beamSvg.startsWith("<svg"))
        assertTrue(beamSvg.contains("Ferraillage Poutre : P.Prin 30x55"))
        assertTrue(beamSvg.contains("5cm")) // 1er cadre à 5 cm
    }
}
