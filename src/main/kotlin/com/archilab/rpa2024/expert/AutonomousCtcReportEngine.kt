package com.archilab.rpa2024.expert

import java.nio.file.Files
import java.nio.file.Path

/** Livrables documentaires du cycle CTC. */
data class CtcReportResult(
    val note: AutomatedNoteArtifacts,
    val wordPath: Path,
    val pdfPath: Path
) {
    val files: List<Path> get() = listOf(wordPath, pdfPath)
    val chapterCount: Int get() = note.chapterCount
}

/**
 * Cycle 7 — générateur documentaire CTC.
 *
 * Orchestre le composeur de référence, l'audit d'intégrité, le designer autonome et
 * les exports Word/PDF. Le document reste structuré en 13 chapitres et reçoit les
 * sections expertes 10.8 (ferraillage) et 13.10 (vérifiabilité).
 */
class AutonomousCtcReportEngine(
    private val noteBuilder: AutomatedCalculationNoteBuilder = AutomatedCalculationNoteBuilder(),
    private val auditor: StructuralDataIntegrityAuditor = StructuralDataIntegrityAuditor(),
    private val designer: AutonomousReinforcementDesigner = AutonomousReinforcementDesigner()
) {

    fun generate(
        snapshot: CrossModelSnapshot,
        metadata: ExpertNoteMetadata,
        outputDirectory: Path,
        basename: String = "NOTE_CALCUL_CTC",
        captures: Map<String, Path> = emptyMap(),
        integrityOverride: StructuralIntegrityReport? = null,
        proposalsOverride: ReinforcementProposalSet? = null
    ): CtcReportResult {
        Files.createDirectories(outputDirectory)
        val integrity = integrityOverride ?: auditor.audit(snapshot)
        val proposals = proposalsOverride ?: designer.design(snapshot)
        val note = noteBuilder.build(
            snapshot = snapshot,
            metadata = metadata,
            integrity = integrity,
            proposals = proposals,
            captures = captures
        )
        val root = sanitize(basename)
        val word = noteBuilder.writeWord(note, outputDirectory.resolve("$root.docx"))
        val pdf = noteBuilder.writePdf(note, outputDirectory.resolve("$root.pdf"))
        return CtcReportResult(note, word, pdf)
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "NOTE_CALCUL_CTC" }
}
