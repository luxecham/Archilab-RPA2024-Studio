package com.archilab.rpa2024

import com.archilab.rpa2024.engine.foundations.FootingType
import com.archilab.rpa2024.engine.foundations.FoundationSoilConfig
import com.archilab.rpa2024.engine.foundations.SafeFoundationExtractor
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingForcesEnvelope
import com.archilab.rpa2024.engine.foundations.StripFootingSection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SafeFoundationExtractorTest {

    private val extractor = SafeFoundationExtractor()
    private val orchestrator = StripFootingDesignOrchestrator()

    @Test
    fun testParseSafeS2kTextExtraction() {
        val s2kText = """
            $ CSI SAFE S2K EXPORT FILE
            $ POINT DISPLACEMENTS
            TABLE: "POINT DISPLACEMENTS"
               Story=BASE  Point="J1"  Case="ELS"  UZ=-0.0048  RX=0.0001
               Story=BASE  Point="J2"  Case="ELS"  UZ=-0.0075  RX=0.0002
               Story=BASE  Point="J3"  Case="ELS"  UZ=-0.0032  RX=0.0001
               
            $ JOINT REACTIONS
            TABLE: "JOINT REACTIONS"
               Story=BASE  Joint="J1"  Case="ELU"  FZ=1850.5  MX=35.0  MY=55.0
               Story=BASE  Joint="J2"  Case="ELU"  FZ=1420.0  MX=25.0  MY=40.0
               
            $ STRIP FORCES
            TABLE: "STRIP FORCES"
               Strip="SFJ200"  Case="ELU"  Moment=1335.0  Shear=450.0
               Strip="SF120"   Case="ELU"  Moment=313.0   Shear=280.0
        """.trimIndent()

        val model = extractor.parseSafeS2kText(s2kText, FoundationSoilConfig(allowableBearingCapacityBars = 1.20))

        assertNotNull(model)
        assertEquals(0.0075, model.displacements.maxSettlementDeltaMaxMeters, 1e-4)
        assertTrue(model.forces.containsKey("SFJ200"))
        assertTrue(model.forces.containsKey("SF120"))

        val sfjForces = model.forces["SFJ200"]!!
        assertEquals(1335.0, sfjForces.jointSupportMomentMuKNm, 0.1)
        assertEquals(450.0, sfjForces.maxShearVuKN, 0.1)
        assertEquals(1850.5, sfjForces.axialForceNuKN, 0.1)
    }

    @Test
    fun testPunchingShearVerificationOnFooting() {
        val section = StripFootingSection(
            sectionName = "SFJ200",
            footingType = FootingType.SEISMIC_JOINT_FOOTING,
            footingWidthBMeters = 2.00,
            slabThicknessH0Meters = 0.50,
            ribWidthB0Meters = 0.90,
            totalHeightHMeters = 1.05,
            coverDMeters = 0.05
        )

        val forces = StripFootingForcesEnvelope(
            footingName = "SFJ200",
            jointSupportMomentMuKNm = 1335.0,
            jointSpanMomentMuKNm = 126.0,
            maxShearVuKN = 320.0,
            axialForceNuKN = 1800.0,
            axialForceNserKN = 1350.0
        )

        val report = orchestrator.designFooting(
            section = section,
            forces = forces,
            soilConfig = FoundationSoilConfig(allowableBearingCapacityBars = 1.20),
            fc28MPa = 25.0,
            feMPa = 500.0
        )

        assertNotNull(report.punchingShearCheck, "La vérification au non-poinçonnement doit être calculée")
        val punch = report.punchingShearCheck!!

        assertEquals(1800.0, punch.columnLoadNuKN, 0.1)
        assertTrue(punch.criticalPerimeterUCm > 0.0, "Le périmètre critique doit être strictement positif")
        assertTrue(punch.shearStressLimitTauLimMPa > 0.0, "La contrainte limite doit être positive")
        assertTrue(punch.isCompliant, "La condition de non-poinçonnement doit être satisfaite")
        assertTrue(report.isGloballyCompliant, "Le rapport global de semelle doit être conforme")
    }
}
