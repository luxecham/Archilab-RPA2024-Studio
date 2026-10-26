package com.archilab.rpa2024

import com.archilab.rpa2024.engine.foundations.*
import com.archilab.rpa2024.engine.frames.BaelRInput
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.DesignSituation
import com.archilab.rpa2024.engine.secondary.ParapetGeometry
import com.archilab.rpa2024.engine.secondary.ParapetWallDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class SecondaryAndFoundationEngineTest {

    private val parapetDesigner = ParapetWallDesigner()
    private val staircaseDesigner = StaircaseDesigner()
    private val soilVerifier = SoilBearingStressVerifier()
    private val baelTSolver = BaelTSectionSolver()
    private val baelRSolver = BaelRectangularSolver()
    private val footingOrchestrator = StripFootingDesignOrchestrator(baelRSolver, baelTSolver, soilVerifier)
    private val schematicRenderer = FoundationSchematicRenderer()

    @Test
    fun testParapetWallDesignMatchingChapter11() {
        // Validation exacte des résultats de l'Acrotère (Chapitre 11) :
        // H = 0.60 m, e = 0.10 m, b_bec = 0.15 m, hb1 = 0.05 m, hb2 = 0.05 m, e_end = 0.02 m
        val geom = ParapetGeometry(
            heightHMeters = 0.60,
            stemThicknessEMeters = 0.10,
            overhangBbecMeters = 0.15,
            capStraightHeightHb1Meters = 0.05,
            capSlopeHeightHb2Meters = 0.05,
            plasterThicknessEendMeters = 0.02
        )

        // 1. Géométrie
        assertEquals(18.43, geom.slopeAngleDeg, 0.05)
        assertEquals(1.65, geom.perimeterMeters, 0.02)
        assertEquals(0.071, geom.crossSectionAreaM2, 0.005)

        // 2. Descente de charges et force sismique
        val report = parapetDesigner.designParapet(
            geometry = geom,
            seismicCoefficientA = 0.10, // Zone II (Fp = 0.78 kN/ml < Qmc = 1.00 kN/ml -> Qcalc = 1.00 kN/ml)
            forceFactorCp = 0.80,       // Console
            handrailLiveLoadQmc = 1.00  // kN/ml
        )

        assertEquals(1.78, report.concreteWeightG1KNPerMl, 0.02)
        assertEquals(0.66, report.plasterWeightG2KNPerMl, 0.02)
        assertEquals(2.44, report.totalDeadWeightWpKNPerMl, 0.02)
        assertEquals(1.00, report.designLateralLoadQCalcKNPerMl, 0.01) // max(Fp=1.17, Qmc=1.00)

        // 3. Sollicitations à la base
        assertEquals(3.30, report.eluAxialForceNuKN, 0.05)   // 1.35 * 2.44
        assertEquals(0.90, report.eluBendingMomentMuKNm, 0.01) // 1.50 * 1.00 * 0.60 = 0.90 kN.m
        assertEquals(2.44, report.elsAxialForceNserKN, 0.02)
        assertEquals(0.60, report.elsBendingMomentMserKNm, 0.01)

        // 4. Ferraillage
        assertTrue(report.adoptedMainSteelAreaCm2 >= report.calculatedMainSteelAreaCm2)
        assertTrue(report.isCompliant)

        // 5. Rendu SVG
        val svg = parapetDesigner.renderParapetSvg(report)
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("Ferraillage de l'Acrotère"))
        assertTrue(svg.contains("Q = 1.0 kN/ml"))
    }

    @Test
    fun testStaircaseDesignMatchingChapter12() {
        val geom = StaircaseGeometry(
            stepHeightHmMeters = 0.17,
            treadWidthGMeters = 0.30,
            flightSpanMeters = 2.40,
            landingSpanMeters = 1.40,
            slabThicknessEpMeters = 0.16,
            flightWidthMeters = 1.00
        )

        // 1. Loi de Blondel : 2 * 17 + 30 = 64 cm (conforme)
        assertEquals(64.0, geom.blondelValueCm, 0.01)
        assertTrue(geom.isBlondelCompliant)
        assertTrue(geom.isThicknessCompliant)

        // 2. Calcul et ferraillage
        val report = staircaseDesigner.designStaircase(
            geometry = geom,
            liveLoadQ = 2.50,
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        assertTrue(report.equivalentEluLoadKNPerM > 10.0)
        assertTrue(report.designSpanMomentMtKNm > 0.0)
        assertTrue(report.calculatedSpanSteelAreaCm2 > 0.0)
        assertTrue(report.isShearCompliant)
        assertTrue(report.isGloballyCompliant)
    }

    @Test
    fun testSoilBearingStressUnderSafeModel() {
        // Validation exacte de la contrainte sol sous SAFE (Page 43) :
        // Déplacement maximal sous ELS = 0.005 m, Ks = 21848 kN/m³
        // sigma = Delta_max * Ks = 0.005 * 21848 = 109.24 kN/m² < 120 kN/m² (1.20 bars)
        val soilConfig = FoundationSoilConfig(
            allowableBearingCapacityBars = 1.20,
            subgradeModulusKsKNM3 = 21848.0
        )

        val report = soilVerifier.verifySoilBearing(
            maxDisplacementDeltaMaxMeters = 0.005,
            soilConfig = soilConfig
        )

        assertEquals(109.24, report.effectiveBearingStressKNM2, 0.01)
        assertEquals(120.0, report.allowableBearingCapacityKNM2, 0.01)
        assertEquals(8.97, report.safetyMarginPercent, 0.05)
        assertTrue(report.isCompliant)
        assertTrue(report.observation.contains("Contrainte du sol vérifiée"))
    }

    @Test
    fun testBaelRAndBaelTExactMatchingFoundationCalculations() {
        // 1. Semelle de Joint en Appui (BaelR) :
        // Mu = 1335 kN.m sur b = 0.90 m, h = 1.05 m (d = 1.00 m)
        // BaelR donne As = 27.57 cm², y0 = 0.09 m
        val inputJointSupport = BaelRInput(
            widthBMeters = 0.90,
            heightHMeters = 1.05,
            dPrimeMeters = 0.05,
            cMeters = 0.05,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = 0.0,
            momentMKNm = 1335.0,
            situation = DesignSituation.ACCIDENTAL_SEISMIC,
            thetaDuration = 0.85
        )
        val resJointSupport = baelRSolver.solve(inputJointSupport)
        assertEquals(0.09, resJointSupport.neutralAxisY0Meters, 0.01)
        assertEquals(27.57, resJointSupport.aInfCm2, 0.25)

        // 2. Semelle de Joint en Travée (BaelT - Section en Té) :
        // Mu = 126 kN.m, Té B = 2.00 m, b0 = 0.50 m, h = 0.90 m, h0 = 0.40 m (d = 0.85 m)
        // BaelT donne As = 2.97 cm²
        val inputJointSpanT = BaelTInput(
            flangeWidthBMeters = 2.00,
            webWidthB0Meters = 0.50,
            totalHeightHMeters = 0.90,
            flangeThicknessH0Meters = 0.40,
            dPrimeMeters = 0.05,
            cMeters = 0.05,
            fc28MPa = 25.0,
            feMPa = 500.0,
            momentMUKNm = 126.0,
            thetaDuration = 0.85
        )
        val resJointSpanT = baelTSolver.solveBaelT(inputJointSpanT)
        assertTrue(resJointSpanT.isNeutralAxisInFlange) // Axe neutre dans la table
        assertEquals(2.97, resJointSpanT.aInfCm2, 0.05)

        // 3. Semelle Courante en Appui (BaelR) :
        // Mu = 313 kN.m sur b = 0.40 m, h = 0.90 m (d = 0.85 m)
        // BaelR donne As = 7.57 cm², y0 = 0.06 m
        val inputRegularSupport = BaelRInput(
            widthBMeters = 0.40,
            heightHMeters = 0.90,
            dPrimeMeters = 0.05,
            cMeters = 0.05,
            fc28MPa = 25.0,
            feMPa = 500.0,
            axialForceNKN = 0.0,
            momentMKNm = 313.0,
            situation = DesignSituation.ACCIDENTAL_SEISMIC,
            thetaDuration = 0.85
        )
        val resRegularSupport = baelRSolver.solve(inputRegularSupport)
        assertEquals(0.06, resRegularSupport.neutralAxisY0Meters, 0.01)
        assertEquals(7.57, resRegularSupport.aInfCm2, 0.05)
    }

    @Test
    fun testStripFootingDesignOrchestratorAndSvgRendering() {
        val section = StripFootingSection(
            sectionName = "SFJ200",
            footingType = FootingType.SEISMIC_JOINT_FOOTING,
            footingWidthBMeters = 2.00,
            slabThicknessH0Meters = 0.40,
            ribWidthB0Meters = 0.50,
            totalHeightHMeters = 0.90
        )

        val forces = StripFootingForcesEnvelope(
            footingName = "SFJ200",
            jointSupportMomentMuKNm = 1335.0,
            jointSpanMomentMuKNm = 126.0,
            maxShearVuKN = 450.0,
            axialForceNuKN = 1800.0,
            axialForceNserKN = 1350.0
        )

        val soilConfig = FoundationSoilConfig(
            allowableBearingCapacityBars = 1.20,
            subgradeModulusKsKNM3 = 21848.0
        )

        val report = footingOrchestrator.designFooting(
            section = section,
            forces = forces,
            soilConfig = soilConfig,
            maxDisplacementDeltaMaxMeters = 0.005
        )

        // 1. Sol
        assertEquals(109.24, report.soilReport.effectiveBearingStressKNM2, 0.01)
        assertTrue(report.soilReport.isCompliant)

        // 2. Appui BaelR
        assertEquals(27.57, report.supportDesignBaelR.aInfCm2, 0.25)

        // 3. Travée BaelT
        assertEquals(2.97, report.spanDesignBaelT.aInfCm2, 0.05)

        // 4. Table de semelle (Méthode des bielles)
        val trans = report.slabTransverseDesign
        assertEquals(0.75, trans.overhangMeters, 0.01) // (2.00 - 0.50) / 2 = 0.75 m
        assertTrue(trans.requiredAreaCm2PerMl > 0.0)
        assertTrue(trans.isCompliant)
        assertTrue(report.isGloballyCompliant)

        // 5. Rendu SVG de la section en Té
        val svgT = schematicRenderer.renderBaelTCrossSectionSvg(
            flangeWidthBMeters = section.footingWidthBMeters,
            webWidthB0Meters = section.ribWidthB0Meters,
            totalHeightHMeters = section.totalHeightHMeters,
            flangeThicknessH0Meters = section.slabThicknessH0Meters,
            neutralAxisY0Meters = report.spanDesignBaelT.neutralAxisY0Meters,
            aInfCm2 = report.spanDesignBaelT.aInfCm2,
            momentMUKNm = 126.0
        )
        assertTrue(svgT.startsWith("<svg"))
        assertTrue(svgT.contains("Section en Té (SOCOTEC BaelT)"))
        assertTrue(svgT.contains("Ainf = 2.97 cm²"))
    }

    @Test
    fun testRaftFoundationDesignerCompleteWorkflow() {
        val raftDesigner = RaftFoundationDesigner(baelRSolver)

        val input = RaftInput(
            totalBuildingLoadNserKN = 12500.0,
            totalBuildingLoadNuKN = 16875.0,
            footprintAreaM2 = 280.0,
            maxSpanLxMeters = 4.50,
            maxSpanLyMeters = 5.20,
            maxColumnLoadNuKN = 1500.0,
            criticalColumnB1Cm = 40.0,
            criticalColumnH1Cm = 50.0,
            soilAllowableCapacityBars = 2.0,
            subgradeModulusKsKNM3 = 40000.0,
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        val report = raftDesigner.designRaft(input)

        // 1. Géométrie
        assertTrue(report.geometry.slabThicknessHMeters >= 0.50)
        assertEquals(0.55, report.geometry.slabThicknessHMeters, 0.05) // 5.20 / 10 = 0.52 -> 0.55m
        assertTrue(report.geometry.overhangMeters >= 0.50)
        assertTrue(report.geometry.totalRaftAreaM2 > 280.0)
        assertTrue(report.geometry.elasticLengthLeMeters > 2.0)
        // Lmax = 5.20m > pi/2 * Le (~4.06m) : détection correcte de la nécessité de nervures
        assertFalse(report.geometry.isRigidityVerified)
        assertTrue(report.geometry.rigidityObservation.contains("nervures requise"))

        // 2. Non-poinçonnement
        assertTrue(report.punchingCheck.controlPerimeterUcMeters > 3.0)
        assertTrue(report.punchingCheck.punchingLimitKN > 1500.0)
        assertTrue(report.punchingCheck.isPunchingCompliant)

        // 3. Contrainte au sol
        assertTrue(report.soilCheck.meanSoilStressBars <= 2.0)
        assertTrue(report.soilCheck.isSoilPressureCompliant)
        assertTrue(report.soilCheck.safetyMarginPercent > 0.0)

        // 4. Ferraillage
        assertTrue(report.reinforcement.bottomRebarXAreaCm2PerM > 0.0)
        assertTrue(report.reinforcement.topRebarXAreaCm2PerM > 0.0)
        assertTrue(report.reinforcement.bottomRebarXDesc.contains("cm²/ml"))
        assertTrue(report.reinforcement.topRebarXDesc.contains("cm²/ml"))
        assertTrue(report.isGloballyCompliant)

        // 5. Rendu vectoriel SVG
        val svg = raftDesigner.renderRaftCrossSectionSvg(report)
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("Radier Général en Béton Armé"))
        assertTrue(svg.contains("Nappe Supérieure"))
        assertTrue(svg.contains("Nappe Inférieure"))
        assertTrue(svg.contains("RADIER CONFORME CTC"))
    }
}
