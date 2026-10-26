package com.archilab.rpa2024

import com.archilab.rpa2024.engine.geometry.*
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.*
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DynamicTopologyAndNormativeTest {

    private val ingestor = EtabsUniversalIngestor()
    private val engine = GeometryAndPreSizingEngine()

    @Test
    fun testDynamicTopologyDiscoveryFromE2k() {
        // Modèle ETABS .e2k synthétique multi-travées, multi-étages avec poteaux, poutres et voiles
        val e2kContent = """
            ; ETABS MODEL FILE GENERATED FOR TESTING DYNAMIC DISCOVERY
            UNITS "KN-M"
            
            $ STORIES - STORY DATA
              STORY "H RDC"     HEIGHT 3.74  ELEV 3.74
              STORY "H 1ER"     HEIGHT 3.74  ELEV 7.48
              STORY "TERRASSE"  HEIGHT 3.74  ELEV 11.22
              STORY "H CAGE"    HEIGHT 3.74  ELEV 14.96
              
            $ GRIDS
              GRID "1" DIR "X" ORD 0.0
              GRID "2" DIR "X" ORD 4.65
              GRID "3" DIR "X" ORD 9.75
              GRID "4" DIR "X" ORD 15.00
              GRID "A" DIR "Y" ORD 0.0
              GRID "B" DIR "Y" ORD 6.35
              GRID "C" DIR "Y" ORD 13.50
              GRID "D" DIR "Y" ORD 20.65
              
            $ POINT COORDINATES
              POINT "1"  X 0.0   Y 0.0   Z 0.0
              POINT "2"  X 0.0   Y 0.0   Z 3.74
              POINT "3"  X 4.65  Y 0.0   Z 3.74
              POINT "4"  X 15.00 Y 0.0   Z 3.74
              POINT "5"  X 15.00 Y 20.65 Z 3.74
              POINT "6"  X 0.0   Y 20.65 Z 3.74
              POINT "7"  X 0.0   Y 0.0   Z 11.22
              POINT "8"  X 1.40  Y 0.0   Z 0.0
              POINT "9"  X 1.40  Y 0.0   Z 3.74
              
            $ FRAME SECTIONS
              FRAME "POT 35X50"     SHAPE "RECTANGULAR" T3 0.50 T2 0.35
              FRAME "POT 40X50"     SHAPE "RECTANGULAR" T3 0.50 T2 0.40
              FRAME "POT 35X35"     SHAPE "RECTANGULAR" T3 0.35 T2 0.35
              FRAME "P.Prin 30x55"  SHAPE "RECTANGULAR" T3 0.55 T2 0.30
              FRAME "P.Second 30x35" SHAPE "RECTANGULAR" T3 0.35 T2 0.30
              
            $ WALL/SLAB/DECK PROPERTIES
              WALL "WALL30" THICKNESS 0.30
              
            $ LINE CONNECTIVITIES
              LINE "C1" I "1" J "2"
              LINE "B1" I "2" J "3"
              
            $ LINE ASSIGNS
              LINE "C1" SECTION "POT 35X50"
              LINE "B1" SECTION "P.Prin 30x55"
              
            $ AREA CONNECTIVITIES
              AREA "W1" POINTS "1" "8" "9" "2"
              
            $ AREA ASSIGNS
              AREA "W1" PIER "V1"
        """.trimIndent()

        val joint = ingestor.parseModelE2k(e2kContent, "Joint A")

        // 1. Vérification du système d'axes dynamique
        assertEquals(15.00, joint.gridSystem.totalLengthLx, 0.01)
        assertEquals(20.65, joint.gridSystem.totalLengthLy, 0.01)
        assertEquals(3, joint.gridSystem.numBaysX)
        assertEquals(3, joint.gridSystem.numBaysY)
        assertTrue(joint.gridSystem.satisfiesMinBaysCondition)
        assertEquals(listOf(4.65, 5.10, 5.25), joint.gridSystem.spansX.map { kotlin.math.round(it * 100.0) / 100.0 })

        // 2. Vérification du système d'étages dynamique
        assertEquals(3, joint.storySystem.numStoriesN)
        assertEquals(11.22, joint.storySystem.totalHeightHn, 0.01)
        assertEquals(14.96, joint.storySystem.totalBuildingElevation, 0.01)
        assertTrue(joint.storySystem.findStory("H CAGE")?.isPenthouseOrCage == true)

        // 3. Vérification des familles d'éléments découvertes
        val col = joint.sectionCatalog.findColumn("POT 35X50")
        assertNotNull(col)
        assertEquals(35.0, col!!.b1Cm, 0.01)
        assertEquals(50.0, col.h1Cm, 0.01)
        // l_cl = hk - max(h_poutre) = 3.74 - 0.55 = 3.19 m
        assertEquals(3.19, col.clearHeightLclMeters, 0.05)

        val beam = joint.sectionCatalog.findBeam("P.Prin 30x55")
        assertNotNull(beam)
        assertEquals(30.0, beam!!.bCm, 0.01)
        assertEquals(55.0, beam.hCm, 0.01)
        assertEquals(BeamRole.MAIN_BEAM, beam.role)

        val wall = joint.sectionCatalog.findShearWall("V1")
        assertNotNull(wall)
        assertEquals(1.40, wall!!.lengthLwMeters, 0.01)
        assertEquals(0.30, wall.thicknessBwMeters, 0.01)
    }

    @Test
    fun testE2kParserWithEqualsAndScientificNotation() {
        val modernE2k = """
            $ PROGRAM INFORMATION
              PROGRAM "ETABS" VERSION "20.0.0"
              
            $ STORIES - STORY DATA
              STORY = "BASE" HEIGHT = 0.0 ELEV = 0.0
              STORY = "RDC"  HEIGHT = 3.24E+00 ELEV = 3.24E+00
              STORY = "ETAGE1" HEIGHT = 3.06E+00 ELEV = 6.30E+00
              
            $ GRIDS
              GRID = "A" DIR = "X" ORD = 0.0
              GRID = "B" DIR = "X" ORD = 5.50E+00
              GRID = "1" DIR = "Y" ORD = 0.0
              GRID = "2" DIR = "Y" ORD = 6.00E+00
              
            $ FRAME SECTIONS
              FRAME = "COL40X40" SHAPE = "RECTANGULAR" T3 = 4.0E-01 T2 = 4.0E-01
              FRAME = "BEAM30X50" SHAPE = "RECTANGULAR" T3 = 5.0E-01 T2 = 3.0E-01
              
            $ POINT COORDINATES
              POINT = "P1" X = 0.0 Y = 0.0 Z = 0.0
              POINT = "P2" X = 0.0 Y = 0.0 Z = 3.24E+00
              
            $ LINE CONNECTIVITIES
              LINE = "L1" I = "P1" J = "P2"
              
            $ LINE ASSIGNS
              LINE = "L1" SECTION = "COL40X40"
        """.trimIndent()

        val joint = ingestor.parseModelE2k(modernE2k, "Modern Joint")

        assertEquals(2, joint.storySystem.numStoriesN)
        assertEquals(3.24, joint.storySystem.stories[0].heightHk, 0.01)
        assertEquals(3.06, joint.storySystem.stories[1].heightHk, 0.01)

        assertEquals(5.50, joint.gridSystem.totalLengthLx, 0.01)
        assertEquals(6.00, joint.gridSystem.totalLengthLy, 0.01)

        val col = joint.sectionCatalog.findColumn("COL40X40")
        assertNotNull(col)
        assertEquals(40.0, col!!.b1Cm, 0.01)
        assertEquals(40.0, col.h1Cm, 0.01)
    }

    @Test
    fun testRpa2024NormativeCalculationsMatchingDesignNote() {
        // Données du projet de référence : Sûreté Urbaine 1500 LLV Bosquet (Joint A)
        // Zone III, Site S3, Groupe 1A, Système Cat 3 (R = 3.5), Hn = 11.22 m (R+2)
        val zone = SeismicZone.ZONE_III
        val site = SiteClass.S3
        val group = ImportanceGroup.GROUP_1A
        val bracing = BracingSystem.CAT_3
        val xi = 7.0 // Portiques BA remplissage dense

        // 1. Paramètres spectraux Type 2 (Tableau 3.4)
        val spec = Rpa2024NormativeDb.getSpectralParameters(zone, site)
        assertEquals(2, spec.spectrumType)
        assertEquals(1.55, spec.s, 0.001)
        assertEquals(0.10, spec.t1, 0.001)
        assertEquals(0.40, spec.t2, 0.001)
        assertEquals(1.20, spec.t3, 0.001)

        // 2. Facteur d'amortissement eta
        val eta = Rpa2024NormativeDb.computeDampingCorrectionEta(xi)
        assertTrue(eta >= 0.70)
        assertEquals(0.8819, eta, 0.001)

        // 3. Période fondamentale empirique (Formule 4.4)
        // T_emp = Ct * Hn^(3/4) = 0.05 * (11.22)^0.75
        val tEmp = Rpa2024NormativeDb.computeEmpiricalPeriod(bracing.defaultCt, 11.22)
        assertEquals(0.307, tEmp, 0.002)
        val tEmp13 = 1.3 * tEmp
        assertEquals(0.399, tEmp13, 0.002)

        // 4. Coefficient correcteur lambda (Eq 4.2)
        val lambda = Rpa2024NormativeDb.computeLambda(periodT0 = 0.398, t2 = spec.t2, numStoriesN = 3)
        assertEquals(0.85, lambda, 0.001)

        // 5. Facteur de réduction nu_A pour Groupe 1A
        assertEquals(0.39, group.nuATabulated, 0.001)
        val effectiveNuA = Rpa2024NormativeDb.getEffectiveReductionFactorNuA(group, useCustom = true, customValue = 0.46)
        assertEquals(0.46, effectiveNuA, 0.001)

        // 6. Limite en hauteur et niveaux (Tableau 3.14)
        assertEquals(4, bracing.getMaxAllowedStories(zone))
        assertEquals(14.0, bracing.getMaxAllowedHeight(zone), 0.01)
        // Pour R+2 (3 niveaux, 11.22m) : 3 <= 4 et 11.22 <= 14.0 -> Conforme
        assertTrue(3 <= bracing.getMaxAllowedStories(zone))
        assertTrue(11.22 <= bracing.getMaxAllowedHeight(zone))

        // 7. Facteur de qualité Q_F pour le cas réel (pénalité travées non respectées dans une direction -> Pq = 0.10)
        val dummyGrid = GridSystem(
            xGrids = listOf(GridLine("1", 0.0, AxisDirection.X), GridLine("2", 15.0, AxisDirection.X)), // 1 travée seulement
            yGrids = listOf(GridLine("A", 0.0, AxisDirection.Y), GridLine("B", 10.0, AxisDirection.Y))
        )
        val dummyStory = StorySystem(
            stories = listOf(
                StoryLevel("RDC", 3.74, 3.74),
                StoryLevel("1ER", 3.74, 7.48),
                StoryLevel("TERRASSE", 3.74, 11.22)
            )
        )
        val jointWithPenalty = StructuralJoint("TestJoint", dummyGrid, dummyStory, SectionCatalog())
        val qfResult = Rpa2024NormativeDb.computeQualityFactor(
            category = PenaltyCategory.CATEGORY_A,
            planRegular = true,
            elevRegular = true,
            topology = jointWithPenalty
        )
        assertEquals(0.10, qfResult.penalties["4. Conditions minimales sur les travées"] ?: 0.0, 0.001)
        assertEquals(1.10, qfResult.qf, 0.001)

        // 8. Spectre de dimensionnement Sad/g et force sismique à la base
        val sadOverG = Rpa2024NormativeDb.computeDesignResponseSpectrumSadOverG(
            periodT = 0.398,
            zone = zone,
            site = site,
            importance = group,
            bracing = bracing,
            qualityFactorQf = 1.10,
            dampingXiPercent = xi
        )
        assertTrue(sadOverG > 0.0)

        val totalW = 10357.95 // Poids total Wi du bloc (kN)
        val vStat = Rpa2024NormativeDb.computeEquivalentStaticBaseShear(
            totalWeightWKN = totalW,
            periodT0 = 0.398,
            zone = zone,
            site = site,
            importance = group,
            bracing = bracing,
            qualityFactorQf = 1.10,
            numStoriesN = 3,
            dampingXiPercent = xi
        )
        assertTrue(vStat > 0.0)
    }

    @Test
    fun testMaterialsAndGeometryChecksEngine() {
        val matConfig = MaterialConfig(concreteGrade = "C25/30", fc28MPa = 25.0, feMPa = 500.0)
        val mech = engine.computeMechanicalProperties(matConfig)

        // 1. Caractéristiques matériaux
        assertEquals(2.10, mech.ft28MPa, 0.01)
        assertEquals(32164.2, mech.eijInstantaneousMPa, 1.0)
        assertEquals(10818.86, mech.evjDelayedMPa, 1.0)

        // 2. Vérification des Poutres (P.Prin 30x55 & P.Prin 30x35)
        val beam30x55 = BeamFamily(
            name = "P.Prin 30x55",
            role = BeamRole.MAIN_BEAM,
            bCm = 30.0,
            hCm = 55.0,
            maxClearSpanMeters = 4.35,
            maxSupportWidthCm = 35.0,
            maxAxisEccentricityCm = 0.0
        )
        val beamCheck = engine.verifyBeamFamily(beam30x55, SeismicZone.ZONE_III)
        assertTrue(beamCheck.isCompliant)
        assertEquals(CheckStatus.PASSED, beamCheck.minWidthCheck.status)
        assertEquals(CheckStatus.PASSED, beamCheck.minHeightCheck.status)
        assertEquals(CheckStatus.PASSED, beamCheck.slendernessCheck.status)

        // 3. Vérification des Poteaux (POT 35x50 & POT 35x35)
        val col35x50 = ColumnFamily(
            name = "POT 35X50",
            shape = ColumnShape.RECTANGULAR,
            b1Cm = 35.0,
            h1Cm = 50.0,
            clearHeightLclMeters = 3.19
        )
        val colCheck = engine.verifyColumnFamily(col35x50, SeismicZone.ZONE_III)
        assertTrue(colCheck.isCompliant)

        // 4. Vérification de l'effort normal réduit poteaux (nu <= 0.35)
        // Cas 1 : POT 35x55 (aire = 1925 cm², Nd = 673 kN)
        val nuCheck1 = engine.verifyReducedNormalForceColumn(
            column = ColumnFamily("POT 35X55", ColumnShape.RECTANGULAR, 35.0, 55.0, clearHeightLclMeters = 3.24),
            ndMaxKN = 673.0,
            fc28MPa = 25.0
        )
        assertEquals(0.140, nuCheck1.nuComputed, 0.001)
        assertTrue(nuCheck1.isCompliant)

        // Cas 2 : POT 40x50 (aire = 2000 cm², Nd = 1496 kN)
        val nuCheck2 = engine.verifyReducedNormalForceColumn(
            column = ColumnFamily("POT 40X50", ColumnShape.RECTANGULAR, 40.0, 50.0, clearHeightLclMeters = 3.24),
            ndMaxKN = 1496.0,
            fc28MPa = 25.0
        )
        assertEquals(0.299, nuCheck2.nuComputed, 0.001)
        assertTrue(nuCheck2.isCompliant)

        // 5. Qualification et vérification d'un voile (Pier V5 : lw = 1.40 m, bw = 0.30 m, he = 3.39 m, hw = 11.22 m)
        val wallV5 = ShearWallFamily(
            pierName = "V5",
            direction = AxisDirection.X,
            lengthLwMeters = 1.40,
            thicknessBwMeters = 0.30,
            clearHeightHeMeters = 3.39,
            totalHeightHwMeters = 11.22
        )
        val wallCheck = engine.qualifyShearWall(wallV5, numStoriesN = 3)
        assertTrue(wallCheck.isWallQualified)
        assertTrue(wallCheck.isCompliant)
        assertEquals(WallSlenderness.SLENDER_WALL, wallCheck.slenderness)
        // Hauteur de zone critique hcr = max(lw, hw/6) = max(1.40, 11.22/6 = 1.87) = 1.87 m
        assertEquals(1.87, wallCheck.criticalZoneHeightHcrMeters, 0.01)

        // Vérification effort normal réduit voile (nu_d <= 0.40)
        // Nd = 2944 kN, aire = 140 * 30 = 4200 cm²
        val nuWall = engine.verifyReducedNormalForceShearWall(wallV5, ndMaxKN = 2944.0, fc28MPa = 25.0)
        assertEquals(0.280, nuWall.nuComputed, 0.001)
        assertTrue(nuWall.isCompliant)

        // 6. Vérification du plancher corps creux (Lmax = 4.35 m -> ht >= 435 / 22.5 = 19.33 cm -> 21 cm)
        val slab = SlabStripTopology(name = "Plancher RDC", totalThicknessHtCm = 21.0, continuousSpansMeters = listOf(4.35, 3.15))
        val slabCheck = engine.verifySlabStrip(slab)
        assertTrue(slabCheck.isCompliant)
        assertEquals(19.33, slabCheck.minRequiredHtCm, 0.01)

        // 7. Combinaisons de charges générées
        val combos = engine.generateLoadCombinations(psi = 0.30)
        assertEquals(11, combos.size) // ELU + ELS + Wi + 8 combinaisons sismiques
        assertTrue(combos.any { it.name == "COMB1" && it.formula.contains("0.3") })
    }

    @Test
    fun testResultsTextTableParsingAndPDelta() {
        val tablesText = """
            TABLE: "Story Shears"
               Story   Load    Loc        P         VX        VY
               H RDC   WI      Bottom   10357.95   0.0       0.0
               H 1ER   WI      Bottom    6854.80   0.0       0.0
               H 2EME  WI      Bottom    3351.60   0.0       0.0
               CAGE    WI      Bottom     290.00   0.0       0.0
               H RDC   EX      Bottom       0.0   1884.51   303.53
               H 1ER   EX      Bottom       0.0   1629.82   257.55
               H 2EME  EX      Bottom       0.0   1003.21   173.83
               CAGE    EX      Bottom       0.0    126.50    36.27
               H RDC   EY      Bottom       0.0    303.53  1819.47
               H 1ER   EY      Bottom       0.0    248.58  1563.96
               H 2EME  EY      Bottom       0.0    152.74   958.35
               CAGE    EY      Bottom       0.0     64.39   121.10
               
            TABLE: "Diaphragm CM Displacements"
               Story   Diaphragm   Load   UX        UY
               H RDC   D1          EX     0.0026    0.0003
               H 1ER   D2          EX     0.0077    0.0008
               H 2EME  D3          EX     0.0129    0.0010
               CAGE    D4          EX     0.0173    0.0019
               H RDC   D1          EY     0.0004    0.0014
               H 1ER   D2          EY     0.0010    0.0041
               H 2EME  D3          EY     0.0015    0.0068
               CAGE    D4          EY     0.0058    0.0097
        """.trimIndent()

        val results = ingestor.parseResultsFromText(tablesText)
        assertEquals(10357.95, results.totalWeightWKN, 0.01)
        assertEquals(12, results.storyShears.size)
        assertEquals(8, results.diaphragmDisplacements.size)

        val storySys = StorySystem(
            listOf(
                StoryLevel("H RDC", 3.74, 3.74),
                StoryLevel("H 1ER", 3.74, 7.48),
                StoryLevel("H 2EME", 3.74, 11.22),
                StoryLevel("CAGE", 3.74, 14.96, isPenthouseOrCage = true)
            )
        )
        val gridSys = GridSystem(
            listOf(GridLine("1", 0.0, AxisDirection.X), GridLine("2", 5.0, AxisDirection.X), GridLine("3", 10.0, AxisDirection.X), GridLine("4", 15.0, AxisDirection.X)),
            listOf(GridLine("A", 0.0, AxisDirection.Y), GridLine("B", 7.0, AxisDirection.Y), GridLine("C", 14.0, AxisDirection.Y), GridLine("D", 20.65, AxisDirection.Y))
        )
        val joint = StructuralJoint("Joint A", gridSys, storySys, SectionCatalog())

        // Vérification des déplacements inter-étages
        val driftChecks = engine.verifyDisplacements(joint, results.diaphragmDisplacements, customNuA = 0.46)
        assertTrue(driftChecks.isNotEmpty())
        assertTrue(driftChecks.all { it.isCompliant })

        // Vérification de l'effet P-Delta
        val pDeltaChecks = engine.verifyPDelta(joint, results.storyShears, driftChecks)
        assertTrue(pDeltaChecks.isNotEmpty())
        assertTrue(pDeltaChecks.all { it.isCompliant })
        assertTrue(pDeltaChecks.all { it.thetaK <= 0.10 })
    }
}
