package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.foundations.SafeFootingInput
import com.archilab.rpa2024.engine.foundations.SafeFootingOutput
import com.archilab.rpa2024.engine.foundations.SafeFoundationEngine
import com.archilab.rpa2024.engine.frames.BeamCompleteDesignReport
import com.archilab.rpa2024.engine.frames.BeamFramesDesigner
import com.archilab.rpa2024.engine.frames.ColumnCompleteDesignReport
import com.archilab.rpa2024.engine.frames.ColumnFramesDesigner
import com.archilab.rpa2024.engine.secondary.ContinuousHollowFloorSolver
import com.archilab.rpa2024.engine.secondary.JoistDesignResult
import com.archilab.rpa2024.engine.secondary.JoistSpanData
import com.archilab.rpa2024.engine.secondary.ParapetDesignResult
import com.archilab.rpa2024.engine.secondary.ParapetWallSolver
import com.archilab.rpa2024.engine.secondary.StaircaseDesignResult
import com.archilab.rpa2024.engine.secondary.StaircaseSolver
import com.archilab.rpa2024.engine.seismic.*
import com.archilab.rpa2024.engine.walls.AutonomousShearWallDesigner
import com.archilab.rpa2024.engine.walls.CompleteShearWallReport
import com.archilab.rpa2024.engine.walls.StoryPierForces
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.model.SoilSiteClass

/**
 * Conteneur universel regroupant toutes les données calculées nécessaires pour alimenter les 13 chapitres.
 */
data class CtcReportDataPayload(
    val model: StructuralJointModel,
    val modalModes: List<ModalModeResult>,
    val modalSummary: ModalCheckSummary,
    val baseShearX: BaseShearComparison,
    val baseShearY: BaseShearComparison,
    val columnReducedStressChecks: List<ReducedStressColumnCheck>,
    val storyDriftsX: List<StoryDriftCheck>,
    val storyDriftsY: List<StoryDriftCheck>,
    val pDeltaChecksX: List<PDeltaStoryEvaluation>,
    val pDeltaChecksY: List<PDeltaStoryEvaluation>,
    val designedColumns: List<ColumnCompleteDesignReport>,
    val designedBeams: List<BeamCompleteDesignReport>,
    val designedWalls: List<CompleteShearWallReport>,
    val parapet: ParapetDesignResult,
    val joist: JoistDesignResult,
    val staircase: StaircaseDesignResult,
    val foundations: List<SafeFootingOutput>
) {
    companion object {
        fun createJointASample(): CtcReportDataPayload {
            val projectModel = StructuralJointModel(
                projectName = "RÉALISATION D'UNE SÛRETÉ URBAINE AU NIVEAU DU SITE DES 1500 LLV BOSQUET",
                jointName = "JOINT A",
                wilaya = "Sidi Bel Abbès",
                zone = SeismicZone.ZONE_III,
                site = SoilSiteClass.S3,
                importance = ImportanceGroup.GROUPE_1A,
                behaviorR = 3.5,
                qualityQ = 1.10,
                dampingEta = 0.88,
                concrete = ConcreteProperties(fc28Mpa = 25.0, ft28Mpa = 2.10),
                rebar = RebarProperties(feMpa = 500.0),
                geo = GeotechnicalProperties(sigmaSolAdmBars = 1.20, anchorageDepthMeters = 1.50, subgradeReactionKsKnM3 = 21848.0)
            )

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

            val baseShearX = calibrator.calibrateBaseShear("X-X", 0.399, 1884.51)
            val baseShearY = calibrator.calibrateBaseShear("Y-Y", 0.294, 1819.47)

            val nuValidator = ReducedNormalStressValidator(defaultFc28Mpa = 25.0)
            val columnNuChecks = listOf(
                nuValidator.validateColumn("H RDC", "C111", "POT 40X50", 40.0, 50.0, -1495.43),
                nuValidator.validateColumn("H RDC", "C106", "POT 35X55", 35.0, 55.0, -672.76),
                nuValidator.validateColumn("H RDC", "C127", "POT 35X35", 35.0, 35.0, -445.61)
            )

            val stabilityEngine = KinematicAndStabilityEngine(behaviorR = 3.5, qualityQ = 1.10, nuAImportanceReduction = 0.46)
            val storyDriftRdcX = stabilityEngine.evaluateStoryDrift("H RDC", 3.74, 0.0026, 0.0)
            val pDeltaRdcX = stabilityEngine.evaluatePDeltaStability("H RDC", 3.74, 10357.95, 0.0083, 1884.51)

            val colDesigner = ColumnFramesDesigner(zone = projectModel.zone, fc28Mpa = 25.0, feMpa = 500.0)
            val beamDesigner = BeamFramesDesigner(fc28Mpa = 25.0, feMpa = 500.0, columnSupportDimCm = 50.0)

            val designedCols = listOf(
                colDesigner.designColumn("C111", "H RDC", "POT 40X50", 40.0, 50.0, 3.24, 1495.43, 52.0, 220.0),
                colDesigner.designColumn("C106", "H RDC", "POT 35X55", 35.0, 55.0, 3.24, 672.76, 70.0, 73.0),
                colDesigner.designColumn("C127", "H RDC", "POT 35X35", 35.0, 35.0, 3.24, 445.61, 32.0, 36.0)
            )

            val designedBeams = listOf(
                beamDesigner.designBeam("B1", "H RDC", "PR 30X55", true, 30.0, 55.0, 4.35, 136.63, 161.70, 178.0),
                beamDesigner.designBeam("B2", "H RDC", "PR 30X35", true, 30.0, 35.0, 3.15, 62.00, 103.68, 144.0),
                beamDesigner.designBeam("B3", "H RDC", "CH 30X35", false, 30.0, 35.0, 3.50, 17.06, 30.21, 64.0)
            )

            val wallDesigner = AutonomousShearWallDesigner(fc28Mpa = 25.0, feMpa = 500.0)
            val forcesV6 = listOf(
                StoryPierForces("H RDC", 0.00, 3.39, 2675.81, 750.37, 2317.83),
                StoryPierForces("H 1ER", 3.74, 3.39, 868.25, 507.72, 1145.75),
                StoryPierForces("TERRASSE", 7.48, 3.39, 226.83, 140.32, 274.26)
            )
            val designedWallV6 = wallDesigner.designShearWall("V6", 1.40, 0.30, 11.22, forcesV6, totalStoriesN = 3)

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

            return CtcReportDataPayload(
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
        }
    }
}
