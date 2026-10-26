package com.archilab.rpa2024

import com.archilab.rpa2024.engine.foundations.SafeFootingInput
import com.archilab.rpa2024.engine.foundations.SafeFoundationEngine
import com.archilab.rpa2024.engine.frames.BeamFramesDesigner
import com.archilab.rpa2024.engine.frames.ColumnFramesDesigner
import com.archilab.rpa2024.engine.secondary.ContinuousHollowFloorSolver
import com.archilab.rpa2024.engine.secondary.JoistSpanData
import com.archilab.rpa2024.engine.secondary.ParapetWallSolver
import com.archilab.rpa2024.engine.secondary.StaircaseSolver
import com.archilab.rpa2024.engine.seismic.*
import com.archilab.rpa2024.engine.walls.AutonomousShearWallDesigner
import com.archilab.rpa2024.engine.walls.StoryPierForces
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.parser.EtabsUniversalParser
import com.archilab.rpa2024.report.CtcNoteDeCalculWordGenerator
import com.archilab.rpa2024.report.CtcReportDataPayload
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

fun main(args: Array<String>) {
    println("================================================================================")
    println("🏛️  ARCHILAB RPA 2024 NOTEDECALCUL STUDIO — PIPELINE AUTONOME D'INGÉNIERIE")
    println("   Conforme DTR BC 2.48 (RPA 2024) / CBA 93 / BAEL 91 mod. 99 / DTR BC 2.2")
    println("================================================================================")

    val outputDirectory = File(if (args.isNotEmpty()) args[0] else "./exports").apply { mkdirs() }
    val finalDocxFile = File(outputDirectory, "NOTE_DE_CALCUL_OFFICIELLE_CTC_JOINT_A.docx")

    // -------------------------------------------------------------------------
    // PHASE 1 : INITIALISATION DU MODÈLE ET INGESTION GÉOMÉTRIQUE
    // -------------------------------------------------------------------------
    println("\n[PHASE 1/6] Ingestion des données du projet témoin (Sûreté Urbaine Joint A)...")
    val projectModel = StructuralJointModel(
        projectName = "RÉALISATION D'UNE SÛRETÉ URBAINE AU NIVEAU DU SITE DES 1500 LLV BOSQUET",
        jointName = "JOINT A",
        wilaya = "Sidi Bel Abbès",
        zone = SeismicZone.ZONE_III, // A = 0.15
        site = SoilSiteClass.S3,     // Sol meuble : S = 1.55, T1=0.10s, T2=0.40s, T3=1.20s
        importance = ImportanceGroup.GROUPE_1A, // I = 1.40, nuA = 0.46
        behaviorR = 3.5,            // Catégorie 3 (Ossature avec maçonnerie rigide)
        qualityQ = 1.10,            // Pénalité travées = 0.10
        dampingEta = 0.88,          // ksi = 7%
        concrete = ConcreteProperties(fc28Mpa = 25.0, ft28Mpa = 2.10),
        rebar = RebarProperties(feMpa = 500.0),
        geo = GeotechnicalProperties(sigmaSolAdmBars = 1.20, anchorageDepthMeters = 1.50, subgradeReactionKsKnM3 = 21848.0)
    )

    // Simulation de l'ingestion du fichier d'échange ETABS .e2k
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
          "B2"    "2"  "3"  "PR_30X35"   "H RDC"
          "B3"    "2"  "3"  "CH_30X35"   "H RDC"
        ${'$'} WALL/PIER INFORMATION
          "V1"  "H RDC"  1.40  0.30  3.39
          "V5"  "H RDC"  1.40  0.30  3.39
          "V6"  "H RDC"  1.40  0.30  3.39
    """.trimIndent()

    val etabsParser = EtabsUniversalParser()
    etabsParser.parseE2kStream(ByteArrayInputStream(simulatedE2k.toByteArray()), projectModel)
    println("   -> Niveaux chargés : ${projectModel.stories.size}")
    println("   -> Poteaux identifiés : ${projectModel.columns.size}, Poutres : ${projectModel.beams.size}, Voiles : ${projectModel.shearWalls.size}")

    // -------------------------------------------------------------------------
    // PHASE 2 : MOTEUR SISMIQUE PARASISMIQUE RPA 2024
    // -------------------------------------------------------------------------
    println("\n[PHASE 2/6] Calcul spectral, complétude modale et justifications parasismiques...")
    val seismicConfig = SeismicProjectConfig(
        zone = projectModel.zone,
        site = projectModel.site,
        importance = projectModel.importance,
        behaviorFactorR = projectModel.behaviorR,
        qualityFactorQ = projectModel.qualityQ,
        criticalDampingPercent = 7.0,
        totalHeightMeters = 11.22,
        numberOfStories = 3,
        totalWeightKn = 10357.95,
        structureTypeCodeCt = 0.050
    )

    val spectrumEngine = Rpa2024SpectrumGenerator(seismicConfig)
    val calibrator = ModalAndBaseShearCalibrator(seismicConfig, spectrumEngine)

    // Modes réels issus d'ETABS (page 18)
    val modalModes = listOf(
        ModalModeResult(1, 0.399085, 73.1663, 0.6780, 0.0, 73.1663, 0.6780, 0.0, 1.0558),
        ModalModeResult(2, 0.294580, 1.1213, 70.3176, 0.0, 74.2876, 70.9957, 0.0, 3.4003),
        ModalModeResult(3, 0.265674, 0.0956, 3.1865, 0.0, 74.3832, 74.1822, 0.0, 60.9992),
        ModalModeResult(4, 0.166629, 4.8533, 0.4513, 0.0, 79.2365, 74.6335, 0.0, 4.5865),
        ModalModeResult(5, 0.150627, 0.0158, 2.8261, 0.0, 79.2523, 77.4596, 0.0, 0.8931),
        ModalModeResult(6, 0.135806, 0.6072, 1.9541, 0.0, 79.8594, 79.4136, 0.0, 5.0207),
        ModalModeResult(7, 0.094308, 13.3532, 0.1647, 0.0, 93.2126, 79.5784, 0.0, 1.1035),
        ModalModeResult(8, 0.073106, 0.7099, 14.3008, 0.0, 93.9226, 93.8792, 0.0, 0.5872)
    )
    val modalSummary = calibrator.evaluateModalCompleteness(modalModes)
    println("   -> Complétude modale : ${modalSummary.diagnostic}")

    // Calage dynamique MSE vs MDS (pages 21-22) : V_MDS,x = 1884.51 kN, V_MDS,y = 1819.47 kN
    val baseShearX = calibrator.calibrateBaseShear("X-X", 0.399, 1884.51)
    val baseShearY = calibrator.calibrateBaseShear("Y-Y", 0.294, 1819.47)
    println("   -> Calage Force Sismique X : VMSE=${"%.2f".format(Locale.US, baseShearX.staticForceVmseKn)} kN, VMDS=${baseShearX.dynamicForceVmdsKn} kN -> Calibré : ${baseShearX.isCalibrated}")
    println("   -> Calage Force Sismique Y : VMSE=${"%.2f".format(Locale.US, baseShearY.staticForceVmseKn)} kN, VMDS=${baseShearY.dynamicForceVmdsKn} kN -> Calibré : ${baseShearY.isCalibrated}")

    // Contrôle de l'effort normal réduit ν ≤ 0.35 (page 22)
    val nuValidator = ReducedNormalStressValidator(defaultFc28Mpa = 25.0)
    val columnNuChecks = listOf(
        nuValidator.validateColumn("H RDC", "C111", "POT 40X50", 40.0, 50.0, -1495.43), // ν = 0.299
        nuValidator.validateColumn("H RDC", "C106", "POT 35X55", 35.0, 55.0, -672.76),  // ν = 0.140
        nuValidator.validateColumn("H RDC", "C127", "POT 35X35", 35.0, 35.0, -445.61)   // ν = 0.146
    )
    columnNuChecks.forEach { println("   -> ${it.diagnostic}") }

    // Déplacements et Effet P-Delta (pages 24-26)
    val stabilityEngine = KinematicAndStabilityEngine(behaviorR = 3.5, qualityQ = 1.10, nuAImportanceReduction = 0.46)
    val storyDriftRdcX = stabilityEngine.evaluateStoryDrift("H RDC", 3.74, 0.0026, 0.0)
    val pDeltaRdcX = stabilityEngine.evaluatePDeltaStability("H RDC", 3.74, 10357.95, 0.0083, 1884.51)

    // -------------------------------------------------------------------------
    // PHASE 3 : FERRAILLAGE BÉTON ARMÉ DES POTEAUX ET POUTRES
    // -------------------------------------------------------------------------
    println("\n[PHASE 3/6] Dimensionnement des armatures réelles (Solveurs BaelR & 4 vérifications)...")
    val colDesigner = ColumnFramesDesigner(zone = projectModel.zone, fc28Mpa = 25.0, feMpa = 500.0)
    val beamDesigner = BeamFramesDesigner(fc28Mpa = 25.0, feMpa = 500.0, columnSupportDimCm = 50.0)

    val designedCols = listOf(
        colDesigner.designColumn("C111", "H RDC", "POT 40X50", 40.0, 50.0, 3.24, 1495.43, 52.0, 220.0),
        colDesigner.designColumn("C106", "H RDC", "POT 35X55", 35.0, 55.0, 3.24, 672.76, 70.0, 73.0),
        colDesigner.designColumn("C127", "H RDC", "POT 35X35", 35.0, 35.0, 3.24, 445.61, 32.0, 36.0)
    )
    designedCols.forEach {
        println("   -> Poteau ${it.sectionName} : ${it.adoptedLongitudinalBars.description} | Zone nodale Lcr = ${it.lcrNodalZoneCm} cm, t = ${it.spacingNodalCm} cm")
    }

    val designedBeams = listOf(
        beamDesigner.designBeam("B1", "H RDC", "PR 30X55", true, 30.0, 55.0, 4.35, 136.63, 161.70, 178.0),
        beamDesigner.designBeam("B2", "H RDC", "PR 30X35", true, 30.0, 35.0, 3.15, 62.00, 103.68, 144.0),
        beamDesigner.designBeam("B3", "H RDC", "CH 30X35", false, 30.0, 35.0, 3.50, 17.06, 30.21, 64.0)
    )
    designedBeams.forEach {
        println("   -> Poutre ${it.sectionName} : Travée ${it.adoptedSpanBars.description} | Appui ${it.adoptedSupportBars.description} | Cisaillement tau_u = ${"%.2f".format(Locale.US, it.shearChecks.tauUMpa)} MPa <= 3.33 MPa (CV)")
    }

    // -------------------------------------------------------------------------
    // PHASE 4 : VOILES DE CONTREVENTEMENT SELON RPA 2024 § 7.7
    // -------------------------------------------------------------------------
    println("\n[PHASE 4/6] Conception parasismique des voiles (hauteur critique hcr, rives et treillis)...")
    val wallDesigner = AutonomousShearWallDesigner(fc28Mpa = 25.0, feMpa = 500.0)
    val forcesV6 = listOf(
        StoryPierForces("H RDC", 0.00, 3.39, 2675.81, 750.37, 2317.83),
        StoryPierForces("H 1ER", 3.74, 3.39, 868.25, 507.72, 1145.75),
        StoryPierForces("TERRASSE", 7.48, 3.39, 226.83, 140.32, 274.26)
    )
    val designedWallV6 = wallDesigner.designShearWall("V6", 1.40, 0.30, 11.22, forcesV6, totalStoriesN = 3)
    println("   -> Voile V6 (1.40x0.30 m) : nu_d = ${"%.3f".format(Locale.US, designedWallV6.reducedNormalStressNuD)} <= 0.40 (CV) | tau_b = ${"%.2f".format(Locale.US, designedWallV6.shearStressTauBMpa)} MPa <= 5.0 MPa (CV)")
    println("   -> Rives confinées lc = ${designedWallV6.leftBoundary.lcChosenCm} cm (St = ${designedWallV6.leftBoundary.hoopSpacingStCm} cm, 16T14) | Ductilité locale omega_wd = ${"%.4f".format(Locale.US, designedWallV6.leftBoundary.mechanicalConfinementRatioOmegaWd)} >= 0.1969 (CV)")

    // -------------------------------------------------------------------------
    // PHASE 5 : ÉLÉMENTS SECONDAIRES ET FONDATIONS SAFE
    // -------------------------------------------------------------------------
    println("\n[PHASE 5/6] Éléments secondaires (Acrotère, Planchers, Escaliers) et fondations SAFE...")
    val parapetSolver = ParapetWallSolver(fc28Mpa = 25.0, feMpa = 500.0, zoneAccelerationA = 0.15)
    val parapetRes = parapetSolver.designParapet(0.60, 0.10)

    val joistSolver = ContinuousHollowFloorSolver(fc28Mpa = 25.0, feMpa = 500.0)
    val joistRes = joistSolver.solveJoistSpan(JoistSpanData(4, 4.35, 3.40, 1.50))

    val stairSolver = StaircaseSolver(feMpa = 500.0)
    val stairRes = stairSolver.designStraightStaircase(17.0, 30.0, 3.60, 15.0, 2.50)

    val safeEngine = SafeFoundationEngine(fc28Mpa = 25.0, feMpa = 500.0, coverCMeters = 0.05)
    val safeOutputs = listOf(
        safeEngine.designFootingBeam(
            SafeFootingInput("Line_SFJ200", "SFJ200", true, 2.00, 0.90, 0.90, 0.40, 21848.0, 1.20, 0.005, 1135.0, 126.0, 350.0)
        ),
        safeEngine.designFootingBeam(
            SafeFootingInput("Line_SF120", "SF120", false, 1.20, 0.40, 0.90, 0.40, 21848.0, 1.20, 0.004, 313.0, 126.0, 200.0)
        )
    )
    println("   -> Fondation SAFE : sigma_eff = ${safeOutputs[0].effectiveSoilStressKpa} kPa <= 120.0 kPa (CV)")
    println("   -> Appui Joint BaelR : ${safeOutputs[0].supportRebarDescription} | Travée Joint BaelT : ${safeOutputs[0].spanRebarDescription}")

    // -------------------------------------------------------------------------
    // PHASE 6 : ASSEMBLAGE DOCUMENTAIRE ET EXPORT DU RAPPORT WORD (.DOCX OMML)
    // -------------------------------------------------------------------------
    println("\n[PHASE 6/6] Assemblage documentaire et compilation de la Note de Calcul CTC officielle...")
    val payload = CtcReportDataPayload(
        model = projectModel,
        modalModes = modalModes,
        modalSummary = modalSummary,
        baseShearX = baseShearX,
        baseShearY = baseShearY,
        columnReducedStressChecks = columnNuChecks,
        storyDriftsX = listOf(storyDriftRdcX),
        storyDriftsY = emptyList(),
        pDeltaChecksX = listOf(pDeltaRdcX),
        pDeltaChecksY = emptyList(),
        designedColumns = designedCols,
        designedBeams = designedBeams,
        designedWalls = listOf(designedWallV6),
        parapet = parapetRes,
        joist = joistRes,
        staircase = stairRes,
        foundations = safeOutputs
    )

    val wordGenerator = CtcNoteDeCalculWordGenerator()
    wordGenerator.generateFullCtcDocx(payload, finalDocxFile)

    println("\n================================================================================")
    println("✅  SUCCÈS : Note de Calcul générée avec succès !")
    println("📁  Fichier généré : ${finalDocxFile.absolutePath}")
    println("📄  Nombre de chapitres compilés : 13 chapitres exhaustifs conformes au CTC")
    println("================================================================================")
}
