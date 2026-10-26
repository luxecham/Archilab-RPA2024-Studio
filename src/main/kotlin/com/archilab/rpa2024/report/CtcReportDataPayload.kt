package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.foundations.SafeFootingOutput
import com.archilab.rpa2024.engine.frames.BeamCompleteDesignReport
import com.archilab.rpa2024.engine.frames.ColumnCompleteDesignReport
import com.archilab.rpa2024.engine.secondary.JoistDesignResult
import com.archilab.rpa2024.engine.secondary.ParapetDesignResult
import com.archilab.rpa2024.engine.secondary.StaircaseDesignResult
import com.archilab.rpa2024.engine.seismic.BaseShearComparison
import com.archilab.rpa2024.engine.seismic.ModalCheckSummary
import com.archilab.rpa2024.engine.seismic.ModalModeResult
import com.archilab.rpa2024.engine.seismic.PDeltaStoryEvaluation
import com.archilab.rpa2024.engine.seismic.ReducedStressColumnCheck
import com.archilab.rpa2024.engine.seismic.StoryDriftCheck
import com.archilab.rpa2024.engine.walls.CompleteShearWallReport
import com.archilab.rpa2024.model.StructuralJointModel

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
)
