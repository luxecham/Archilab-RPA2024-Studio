package com.archilab.rpa2024.expert

import com.archilab.rpa2024.cad.CadProjectInfo
import com.archilab.rpa2024.engine.foundations.FoundationGeotechnicalParameters
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.model.SeismicConfig
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Demande complète du workflow « zéro-clic ». */
data class OneClickRequest(
    val etabsModel: Path,
    val jointName: String,
    val seismicConfig: SeismicConfig,
    val etabsResults: Path? = null,
    val safeModel: Path? = null,
    val geotechnical: FoundationGeotechnicalParameters = FoundationGeotechnicalParameters(),
    val manualSeismicWeightKN: Double? = null,
    val noteMetadata: ExpertNoteMetadata = ExpertNoteMetadata(),
    val cadProject: CadProjectInfo = CadProjectInfo(),
    val outputDirectory: Path,
    val cadDirectoryName: String = "PLANS_EXECUTION",
    val noteBasename: String = "NOTE_CALCUL_CTC",
    val captures: Map<String, Path> = emptyMap(),
    val resizeOptions: AutoResizeOptions = AutoResizeOptions(),
    val cadOptions: CadExportOptions = CadExportOptions()
)

/** Tableau de bord de synthèse du workflow. */
data class OneClickDashboard(
    val ctcStatus: CheckStatus,
    val automationIndex: Int,
    val integrityVerdict: IntegrityVerdict,
    val integrityScore: Int,
    val foundationStatus: CheckStatus,
    val reinforcementStatus: CheckStatus,
    val resizedElementCount: Int,
    val proposalCount: Int,
    val criticalProposalCount: Int,
    val reportFiles: List<Path>,
    val cadFiles: List<Path>,
    val warnings: List<String>,
    val durationMs: Long = 0L,
    val generatedAt: Instant = Instant.now()
) {
    val isAutomationComplete: Boolean get() = reportFiles.size >= 2 && cadFiles.isNotEmpty()
    val allFiles: List<Path> get() = reportFiles + cadFiles
}

/** Résultat complet et reproductible du one-click. */
data class OneClickResult(
    val snapshot: CrossModelSnapshot,
    val foundation: FoundationOptimizationReport,
    val resize: AutoResizeReport,
    val proposals: ReinforcementProposalSet,
    val note: CtcReportResult,
    val cad: CadExportReport,
    val dashboard: OneClickDashboard
)

/**
 * Cycle 8 — orchestrateur « One Click ».
 *
 * Le seul fichier fourni par l'ingénieur est le modèle ETABS/SAFE. Le studio exécute
 * ensuite : ingestion, audit, fondations, ferraillage, auto-resize, note Word/PDF,
 * plans DXF et DQE acier. Chaque étape est conservée dans le résultat pour l'audit.
 */
class ArchilabOneClickStudio(
    private val ingestor: CrossModelIngestor = CrossModelIngestor(),
    private val integrityAuditor: StructuralDataIntegrityAuditor = StructuralDataIntegrityAuditor(),
    private val foundationOptimizer: AutonomousFoundationOptimizer = AutonomousFoundationOptimizer(),
    private val designer: AutonomousReinforcementDesigner = AutonomousReinforcementDesigner(),
    private val reportEngine: AutonomousCtcReportEngine = AutonomousCtcReportEngine(),
    private val cadEngine: AutonomousCadDxfEngine = AutonomousCadDxfEngine()
) {

    fun run(request: OneClickRequest): OneClickResult {
        val snapshot = ingestor.ingest(
            CrossModelRequest(
                etabsModel = request.etabsModel,
                jointName = request.jointName,
                seismicConfig = request.seismicConfig,
                etabsResults = request.etabsResults,
                safeModel = request.safeModel,
                geotechnical = request.geotechnical,
                manualSeismicWeightKN = request.manualSeismicWeightKN
            )
        )
        return runFromSnapshot(snapshot, request)
    }

    /**
     * Variante utilisée par l'interface Compose et les tests : le modèle a déjà été
     * importé par `CrossModelIngestor`. Le chemin `etabsModel` de la requête n'est
     * alors pas relu.
     */
    fun runFromSnapshot(snapshot: CrossModelSnapshot, request: OneClickRequest): OneClickResult {
        val started = System.nanoTime()
        Files.createDirectories(request.outputDirectory)

        val integrity = integrityAuditor.audit(snapshot)
        val foundation = foundationOptimizer.optimize(snapshot)
        val resized = designer.designWithAutoResize(
            snapshot = foundation.snapshot,
            resizeOptions = request.resizeOptions
        )
        val proposals = resized.second
        val effectiveSnapshot = resized.first.snapshot
        val report = reportEngine.generate(
            snapshot = effectiveSnapshot,
            metadata = request.noteMetadata,
            outputDirectory = request.outputDirectory,
            basename = request.noteBasename,
            captures = request.captures,
            integrityOverride = integrity,
            proposalsOverride = proposals
        )
        val cad = cadEngine.export(
            snapshot = effectiveSnapshot,
            project = request.cadProject,
            outputDirectory = request.outputDirectory.resolve(request.cadDirectoryName),
            options = request.cadOptions
        )

        val resizeReport = resized.first
        val ctcStatus = when {
            integrity.blocking.isNotEmpty() -> CheckStatus.FAILED
            else -> CheckStatus.worst(listOf(report.note.document.status, proposals.status))
        }
        val warnings = (
            snapshot.warnings +
                integrity.warnings.map { "${it.code} : ${it.observation}" } +
                foundation.warnings +
                resizeReport.warnings +
                proposals.warnings +
                report.note.document.warnings +
                cad.warnings
            ).distinct()
        val dashboard = OneClickDashboard(
            ctcStatus = ctcStatus,
            automationIndex = if (report.files.isNotEmpty() && cad.files.isNotEmpty()) 100 else 50,
            integrityVerdict = integrity.verdict,
            integrityScore = integrity.score,
            foundationStatus = foundation.status,
            reinforcementStatus = proposals.status,
            resizedElementCount = resizeReport.changedCount,
            proposalCount = proposals.proposals.size,
            criticalProposalCount = proposals.criticalCount,
            reportFiles = report.files,
            cadFiles = cad.files,
            warnings = warnings,
            durationMs = (System.nanoTime() - started) / 1_000_000L,
            generatedAt = Instant.now()
        )
        return OneClickResult(
            snapshot = effectiveSnapshot,
            foundation = foundation,
            resize = resizeReport,
            proposals = proposals,
            note = report,
            cad = cad,
            dashboard = dashboard
        )
    }
}
