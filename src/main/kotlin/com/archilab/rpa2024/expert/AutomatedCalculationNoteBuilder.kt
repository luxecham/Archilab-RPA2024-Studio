package com.archilab.rpa2024.expert

import com.archilab.rpa2024.report.CaptureLibrary
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.NoteDeCalculOptions
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.report.PdfReportExporter
import com.archilab.rpa2024.report.ReferenceNoteDeCalculComposer
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.report.ReportDocument
import com.archilab.rpa2024.report.ReportText
import com.archilab.rpa2024.report.ReportChapter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Métadonnées de la note d'exécution. */
data class ExpertNoteMetadata(
    val projectTitle: String = "Projet",
    val wilaya: String = "",
    val client: String = "",
    val engineer: String = "",
    val reference: String = "NDC-RPA2024",
    val revision: String = "A",
    val options: NoteDeCalculOptions = NoteDeCalculOptions()
)

/** Sortie complète du composeur de note expert. */
data class AutomatedNoteArtifacts(
    val document: ReportDocument,
    val figures: Map<String, ByteArray>,
    val capturePlaceholders: List<String>,
    val integrity: StructuralIntegrityReport,
    val proposals: ReinforcementProposalSet
) {
    val chapterCount: Int get() = document.chapters.size
}

/**
 * Compose la note CTC de 13 chapitres à partir du snapshot croisé, de l'audit de
 * vérifiabilité et des propositions de ferraillage. Les chapitres 10 et 13 sont
 * enrichis sans changer la structure réglementaire du document.
 */
class AutomatedCalculationNoteBuilder(
    private val noteComposer: ReferenceNoteDeCalculComposer = ReferenceNoteDeCalculComposer(),
    private val integrityAuditor: StructuralDataIntegrityAuditor = StructuralDataIntegrityAuditor(),
    private val reinforcementDesigner: AutonomousReinforcementDesigner = AutonomousReinforcementDesigner(),
    private val wordExporter: CtcWordReportGenerator = CtcWordReportGenerator(),
    private val pdfExporter: PdfReportExporter = PdfReportExporter()
) {

    fun build(
        snapshot: CrossModelSnapshot,
        metadata: ExpertNoteMetadata = ExpertNoteMetadata(),
        integrity: StructuralIntegrityReport? = null,
        proposals: ReinforcementProposalSet? = null,
        captures: Map<String, Path> = emptyMap()
    ): AutomatedNoteArtifacts {
        val audit = integrity ?: integrityAuditor.audit(snapshot)
        val design = proposals ?: reinforcementDesigner.design(snapshot)
        val request = NoteDeCalculRequest(
            model = snapshot.model,
            results = snapshot.results,
            foundations = snapshot.foundations,
            projectTitle = metadata.projectTitle,
            wilaya = metadata.wilaya,
            client = metadata.client,
            engineer = metadata.engineer,
            reference = metadata.reference,
            revision = metadata.revision,
            options = metadata.options.copy(
                manualSeismicWeightKN = metadata.options.manualSeismicWeightKN
                    ?: snapshot.manualSeismicWeightKN
            )
        )
        val reference = noteComposer.compose(request)
        val enriched = injectExpertSections(reference.document, audit, design)
        val applied = CaptureLibrary.apply(enriched, captures)
        return AutomatedNoteArtifacts(
            document = applied.document,
            figures = reference.base.figures,
            capturePlaceholders = reference.capturePlaceholders,
            integrity = audit,
            proposals = design
        )
    }

    fun writeWord(artifacts: AutomatedNoteArtifacts, target: Path): Path {
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        return wordExporter.write(artifacts.document, target)
    }

    fun writePdf(artifacts: AutomatedNoteArtifacts, target: Path): Path {
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        return pdfExporter.write(artifacts.document, target)
    }

    private fun injectExpertSections(
        document: ReportDocument,
        integrity: StructuralIntegrityReport,
        proposals: ReinforcementProposalSet
    ): ReportDocument {
        val chapters = document.chapters.map { chapter ->
            when (chapter.number) {
                10 -> chapter.withBlocks(
                    ReportBlock.Heading(2, "10.8 Propositions autonomes de ferraillage", "s108"),
                    ReportBlock.Paragraph(
                        "Les dispositions ci-dessous sont des propositions constructibles issues du calcul " +
                            "BAEL 91 / RPA 2024. Les barres sont commerciales et les zones nodales sont explicitées.",
                        italic = true,
                        sizePt = 9
                    ),
                    proposalTable(proposals),
                    *proposalNotes(proposals)
                )
                13 -> chapter.withBlocks(
                    ReportBlock.Heading(2, "13.10 Contrôle de vérifiabilité des données", "s1310"),
                    integrityTable(integrity),
                    ReportBlock.Note(
                        "Verdict de vérifiabilité : ${integrity.verdict.label} (indice ${integrity.score}/100). " +
                            "Les anomalies bloquantes doivent être levées avant validation CTC.",
                        "Synthèse de l'audit d'intégrité"
                    ),
                    *integrityNotes(integrity)
                )
                else -> chapter
            }
        }
        return document.copy(chapters = chapters)
    }

    private fun proposalTable(proposals: ReinforcementProposalSet): ReportBlock.Table = ReportBlock.Table(
        caption = "Fiches d'armatures proposées",
        headers = listOf(
            "Type", "Élément", "Section", "Longitudinal", "Transversal",
            "As requis", "As fourni", "Statut", "Optimisation"
        ),
        rows = proposals.proposals.map { proposal ->
            listOf(
                proposal.elementType,
                proposal.name,
                proposal.section,
                proposal.longitudinal,
                proposal.transverse,
                proposal.requiredSteelCm2?.let { format(it) } ?: "—",
                proposal.providedSteelCm2?.let { format(it) } ?: "—",
                ReportText.statusLabel(proposal.status),
                proposal.optimization ?: "—"
            )
        },
        columnWidthsCm = listOf(1.8, 2.4, 2.2, 3.6, 3.2, 1.4, 1.4, 1.7, 4.0),
        fontSizePt = 7,
        firstColumnBold = true
    )

    private fun proposalNotes(proposals: ReinforcementProposalSet): Array<ReportBlock> =
        proposals.proposals.filter { it.isCritical }
            .map { proposal ->
                ReportBlock.Note(
                    proposal.optimization
                        ?: "Élément ${proposal.name} non vérifié : reprendre la section ou le ferraillage.",
                    "Action corrective — ${proposal.elementType} ${proposal.name}"
                )
            }.toTypedArray()

    private fun integrityTable(report: StructuralIntegrityReport): ReportBlock.Table = ReportBlock.Table(
        caption = "Contrôle de cohérence et de vérifiabilité",
        headers = listOf("Code", "Constat", "Sévérité", "Référence", "Observation"),
        rows = report.findings.map {
            listOf(it.code, it.title, it.severity.label, it.reference, it.observation)
        },
        columnWidthsCm = listOf(2.0, 4.0, 2.4, 3.2, 6.0),
        fontSizePt = 7,
        firstColumnBold = true
    )

    private fun integrityNotes(report: StructuralIntegrityReport): Array<ReportBlock> =
        report.blocking.take(6).map { finding ->
            ReportBlock.Note(
                finding.remediation.ifBlank { finding.observation },
                "Blocage ${finding.code} — ${finding.title}"
            )
        }.toTypedArray()

    private fun ReportChapter.withBlocks(vararg blocks: ReportBlock): ReportChapter =
        copy(blocks = this.blocks + blocks.toList())

    private fun format(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value)
}
