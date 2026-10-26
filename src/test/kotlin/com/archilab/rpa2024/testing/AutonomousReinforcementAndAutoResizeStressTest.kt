package com.archilab.rpa2024.testing

import com.archilab.rpa2024.audit.CtcComplianceAuditorAndAutoFixer
import com.archilab.rpa2024.audit.CtcComplianceStatus
import com.archilab.rpa2024.engine.foundations.RaftFoundationDesigner
import com.archilab.rpa2024.engine.foundations.RaftInput
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.max

class AutonomousReinforcementAndAutoResizeStressTest {

    private val colEngine = ColumnDesignEngine()
    private val beamEngine = BeamDesignEngine()
    private val wallDesigner = WallCriticalZoneAndDuctilityDesigner()
    private val auditor = CtcComplianceAuditorAndAutoFixer()
    private val raftDesigner = RaftFoundationDesigner()

    @Test
    @DisplayName("Crash-Test 1 : Bâtiment irrégulier avec voiles dissymétriques et auto-correction")
    fun testIrregularBuildingWithAsymmetricWalls() {
        val baseJoint = createDefaultJointA()
        val irregularJoint = baseJoint.copy(
            seismicParams = baseJoint.seismicParams.copy(
                planRegular = false,
                elevationRegular = false
            )
        )

        val (fixedJoint, audit) = auditor.autoCorrectAndReDesignProject(irregularJoint)
        assertNotNull(fixedJoint)
        assertNotNull(audit)
        assertTrue(audit.checks.isNotEmpty())
    }

    @Test
    @DisplayName("Crash-Test 2 : Poteau hyper-chargé nu > 0.35 avec auto-redimensionnement par pas de 5 cm")
    fun testOverloadedColumnNuAutoResize() {
        // Poteau initial 35x35 cm sous charge axiale Nd = 2100 kN -> nu = 2100 / (0.35*0.35*25000) = 0.686 > 0.35
        val baseJoint = createDefaultJointA()
        val col35 = ColumnFamily(name = "P35X35_TEST", shape = ColumnShape.RECTANGULAR, b1Cm = 35.0, h1Cm = 35.0, clearHeightLclMeters = 3.0)

        // Optimization
        val optCol = auditor.optimizeColumnSection(ndKN = 2100.0, fc28MPa = 25.0)
        assertTrue(optCol.b1Cm >= 50.0, "La section doit passer au moins à 50 cm pour satisfaire nu <= 0.35")
        assertEquals(0.0, optCol.b1Cm % 5.0, "Le redimensionnement doit être un multiple strict de 5 cm")

        val nuOpt = 2100.0 / ((optCol.b1Cm / 100.0) * (optCol.h1Cm / 100.0) * 25000.0)
        assertTrue(nuOpt <= 0.35 + 1e-4, "L'effort normal réduit optimisé doit être <= 0.35")

        // Ferraillage réel généré pour le poteau optimisé
        val report = colEngine.designColumn(
            column = optCol,
            envelope = ColumnForcesEnvelope(
                columnFamilyName = optCol.name,
                maxCompressionNdKN = 2100.0,
                maxCompressionStory = "H RDC",
                eluTorseur = ForceTorseur(nKN = 2100.0, m2KNm = 45.0, m3KNm = 65.0),
                elaTorseur = ForceTorseur(nKN = 1200.0, m2KNm = 85.0, m3KNm = 110.0),
                v2MaxKN = 95.0,
                v3MaxKN = 105.0
            ),
            zone = SeismicZone.ZONE_III
        )

        assertTrue(report.isOverallCompliant)
        assertTrue(report.aAdoptedCm2 >= report.aCalculatedCm2)
        assertTrue(report.maxBarSpacingCm <= 25.0)
    }

    @Test
    @DisplayName("Crash-Test 3 : Voile élancé avec forte concentration de cisaillement tau_b ~ 0.20 fc28")
    fun testSlenderShearWallHighShearStress() {
        val wall = ShearWallFamily(
            pierName = "V_CRITICAL",
            direction = AxisDirection.X,
            lengthLwMeters = 3.50,
            thicknessBwMeters = 0.20,
            clearHeightHeMeters = 3.06,
            totalHeightHwMeters = 18.00
        )

        // Forte sollicitation d'effort tranchant Vu = 1450 kN
        val optWall = auditor.optimizeShearWallThickness(
            lengthLwMeters = wall.lengthLwMeters,
            shearVuKN = 1450.0,
            fc28MPa = 25.0,
            direction = AxisDirection.X
        )

        assertTrue(optWall.thicknessBwMeters >= 0.20)
        assertEquals(0.0, (optWall.thicknessBwMeters * 100.0) % 5.0, "L'épaisseur du voile doit être un multiple de 5 cm")

        val wallReport = wallDesigner.designShearWall(
            wall = optWall,
            designAxialForceNdKN = 2800.0,
            designMomentMKNm = 4200.0,
            designShearVuKN = 1450.0,
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        assertTrue(wallReport.leftBoundaryElement.isConfinementCompliant)
        assertTrue(wallReport.webReinforcement.isWebCompliant)
    }

    @Test
    @DisplayName("Crash-Test 4 : Poutre longue portée à moments asymétriques et optimisation des chapeaux")
    fun testLongSpanBeamAsymmetricMoments() {
        val beam7m = BeamFamily(
            name = "PP_LONG_7M",
            role = BeamRole.MAIN_BEAM,
            bCm = 35.0,
            hCm = 65.0,
            maxClearSpanMeters = 7.20
        )

        val envHeavy = BeamForcesEnvelope(
            beamFamilyName = beam7m.name,
            mSpanMaxKNm = 245.0,
            mSupportMaxKNm = -310.0,
            vuMaxKN = 265.0,
            eluSpanM = 245.0,
            eluSupportM = -310.0,
            elsSpanM = 175.0,
            elsSupportM = -220.0
        )

        val report = beamEngine.designBeam(
            beam = beam7m,
            envelope = envHeavy,
            zone = SeismicZone.ZONE_III,
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        assertTrue(report.isOverallCompliant)
        assertTrue(report.spanRebars.totalAdoptedAreaCm2 >= report.spanRebars.requiredCalculatedAreaCm2)
        assertTrue(report.supportRebars.totalAdoptedAreaCm2 >= report.supportRebars.requiredCalculatedAreaCm2)
        assertTrue(report.shearChecks.allShearChecksCompliant, "Toutes les 4 vérifications d'effort tranchant doivent être satisfaites")
    }

    @Test
    @DisplayName("Crash-Test 5 : Sol compressible et basculement automatique vers radier général")
    fun testCompressibleSoilAutomaticRaftConversion() {
        val joint = createDefaultJointA()

        // Sol compressible avec faible module Ks = 12000 kN/m3 et sigma_adm = 0.8 bar (80 kN/m2)
        val report = auditor.performCtcAudit(
            joint = joint,
            soilConfig = com.archilab.rpa2024.engine.foundations.FoundationSoilConfig(
                allowableBearingCapacityBars = 0.80,
                subgradeModulusKsKNM3 = 12000.0
            ),
            forcedMaxDisplacementMeters = 0.015 // Tassement 1.5 cm -> contrainte 180 kPa > 80 kPa
        )

        assertEquals(CtcComplianceStatus.RESERVE_BLOQUANTE, report.checks.first { it.checkNumber == 14 }.status)
        assertTrue(report.autoFixProposals.any { it.elementType.contains("Radier") || it.elementType.contains("Fondation") })

        // Dimensionnement automatique du radier général
        val raftReport = raftDesigner.designRaft(
            RaftInput(
                totalBuildingLoadNserKN = 10357.95,
                totalBuildingLoadNuKN = 14200.0,
                footprintAreaM2 = 250.0,
                maxSpanLxMeters = 5.0,
                maxSpanLyMeters = 5.5,
                maxColumnLoadNuKN = 1495.0,
                soilAllowableCapacityBars = 0.80,
                subgradeModulusKsKNM3 = 12000.0
            )
        )

        assertNotNull(raftReport)
        assertTrue(raftReport.geometry.slabThicknessHMeters >= 0.40)
        assertTrue(raftReport.punchingCheck.isPunchingCompliant)
    }
}
