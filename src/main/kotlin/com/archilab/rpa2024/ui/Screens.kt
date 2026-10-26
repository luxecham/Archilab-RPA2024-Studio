package com.archilab.rpa2024.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.model.InfillType
import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.OccupancyCase
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.normative.SiteClass
import com.archilab.rpa2024.report.OmmlEquationBuilder
import com.archilab.rpa2024.report.ReportAlign
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.report.ReportChapter
import com.archilab.rpa2024.report.ReportText
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.swing.JFileChooser

// ---------------------------------------------------------------------------- outils

private fun chooseFile(title: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name).toPath()
}

private fun chooseDirectory(title: String): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.toPath()
    } else {
        null
    }
}

private fun decimal(text: String): Double? =
    text.replace(',', '.').replace(" ", "").trim().toDoubleOrNull()

@Composable
private fun ScreenScaffold(
    title: String,
    subtitle: String,
    status: CheckStatus?,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CANVAS)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = NAVY)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 12.5.sp, color = MUTED)
            }
            status?.let { CheckStatusPill(it) }
        }
        content()
    }
}

@Composable
private fun EmptyAnalysis(text: String, onAnalyze: (() -> Unit)? = null) {
    SectionCard("Aucun résultat disponible", text) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoBanner("Renseignez les fichiers sources puis lancez l'analyse pour alimenter cet onglet.")
            if (onAnalyze != null) {
                Spacer(Modifier.width(12.dp))
                PrimaryButton("Analyser", onClick = onAnalyze)
            }
        }
    }
}

// ---------------------------------------------------------------- rendu des blocs

@Composable
private fun ChapterSection(chapter: ReportChapter, showBanner: Boolean = true) {
    if (showBanner) {
        ChapterBanner(
            chapter.number,
            chapter.title,
            chapter.summary,
            chapter.status,
            chapter.numbered
        )
    }
    ReportBlocksView(chapter.blocks)
}

@Composable
fun ReportBlocksView(blocks: List<ReportBlock>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            ReportBlockView(block)
        }
    }
}

@Composable
fun ReportBlockView(block: ReportBlock) {
    when (block) {
        is ReportBlock.Heading -> {
            val size = when (block.level) {
                1 -> 18.sp
                2 -> 14.5.sp
                else -> 12.5.sp
            }
            Text(
                block.text,
                fontSize = size,
                fontWeight = if (block.level <= 2) FontWeight.SemiBold else FontWeight.Medium,
                color = NAVY,
                modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 2.dp)
            )
        }

        is ReportBlock.Paragraph -> Text(
            block.text,
            fontSize = (block.sizePt + 1.5).sp,
            lineHeight = ((block.sizePt + 1.5) * 1.5).sp,
            color = if (block.colorHex != null) hexColor(block.colorHex) ?: TEXT else TEXT,
            fontWeight = if (block.bold) FontWeight.SemiBold else FontWeight.Normal,
            fontStyle = if (block.italic) androidx.compose.ui.text.font.FontStyle.Italic
            else androidx.compose.ui.text.font.FontStyle.Normal,
            textAlign = when (block.align) {
                ReportAlign.CENTER -> TextAlign.Center
                ReportAlign.RIGHT -> TextAlign.End
                else -> TextAlign.Start
            },
            modifier = Modifier.fillMaxWidth()
        )

        is ReportBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Text("•  $item", fontSize = 12.5.sp, lineHeight = 18.sp, color = TEXT)
            }
        }

        is ReportBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Text("${index + 1}.  $item", fontSize = 12.5.sp, lineHeight = 18.sp, color = TEXT)
            }
        }

        is ReportBlock.Equation -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MonoEquation(OmmlEquationBuilder.plainText(block.nodes))
            block.caption?.let {
                Text(it, fontSize = 10.5.sp, color = MUTED, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        is ReportBlock.Table -> ReportTableView(
            headers = block.headers,
            rows = block.rows,
            caption = block.caption,
            columnWidthsCm = block.columnWidthsCm,
            alignments = block.alignments,
            headerFillHex = block.headerFillHex,
            headerTextHex = block.headerTextHex,
            zebra = block.zebra,
            fontSizePt = block.fontSizePt,
            firstColumnBold = block.firstColumnBold
        )

        is ReportBlock.Figure -> FigureView(block)

        is ReportBlock.Note -> SurfaceNote(block.title, block.text, WARN_AMBER, WARN_BG)

        is ReportBlock.Verdict -> SectionCard("Conclusion du chapitre", null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckStatusPill(block.status)
                Spacer(Modifier.width(10.dp))
                Text(block.text, fontSize = 12.5.sp, color = TEXT, modifier = Modifier.weight(1f))
            }
        }

        is ReportBlock.ChecksTable -> ChecksTableView(block)

        is ReportBlock.KeyValues -> KeyValuesView(block)

        ReportBlock.PageBreak -> DividerLine(Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SurfaceNote(title: String, text: String, accent: Color, background: Color) {
    androidx.compose.material3.Surface(
        color = background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
            Spacer(Modifier.height(3.dp))
            Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = TEXT)
        }
    }
}

@Composable
fun ReportTableView(
    headers: List<String>,
    rows: List<List<String>>,
    caption: String? = null,
    columnWidthsCm: List<Double>? = null,
    alignments: List<ReportAlign> = emptyList(),
    headerFillHex: String = "12304F",
    headerTextHex: String = "FFFFFF",
    zebra: Boolean = true,
    fontSizePt: Int = 9,
    firstColumnBold: Boolean = false
) {
    val columns = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val widths = (0 until columns).map { index ->
        columnWidthsCm?.getOrNull(index)?.let { (it * 37.8).dp } ?: 150.dp
    }
    val totalWidth = widths.fold(0.dp) { acc, width -> acc + width }
    val headerBackground = hexColor(headerFillHex) ?: NAVY
    val headerText = hexColor(headerTextHex) ?: Color.White
    val bodySize = (fontSizePt + 1.2).sp

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        caption?.let {
            Text(it, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = NAVY)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .border(1.dp, BORDER, RoundedCornerShape(8.dp))
                .background(Color.White, RoundedCornerShape(8.dp))
        ) {
            Row(
                modifier = Modifier
                    .width(totalWidth)
                    .background(headerBackground)
                    .padding(vertical = 8.dp)
            ) {
                headers.forEachIndexed { index, header ->
                    Text(
                        header,
                        color = headerText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(widths[index]).padding(horizontal = 7.dp)
                    )
                }
                repeat(columns - headers.size) { index ->
                    Spacer(Modifier.width(widths[headers.size + index]))
                }
            }
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .width(totalWidth)
                        .background(
                            if (zebra && rowIndex % 2 == 1) Color(0xFFF6F8FB) else Color.White
                        )
                        .padding(vertical = 6.dp)
                ) {
                    repeat(columns) { columnIndex ->
                        val value = row.getOrNull(columnIndex).orEmpty()
                        val align = alignments.getOrNull(columnIndex) ?: ReportAlign.LEFT
                        Text(
                            value,
                            fontSize = bodySize,
                            color = TEXT,
                            fontWeight = if ((firstColumnBold && columnIndex == 0) || columnIndex == 0) {
                                FontWeight.Medium
                            } else {
                                FontWeight.Normal
                            },
                            textAlign = when (align) {
                                ReportAlign.CENTER -> TextAlign.Center
                                ReportAlign.RIGHT -> TextAlign.End
                                else -> TextAlign.Start
                            },
                            modifier = Modifier.width(widths[columnIndex]).padding(horizontal = 7.dp)
                        )
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    DividerLine(Modifier.width(totalWidth))
                }
            }
        }
    }
}

@Composable
private fun ChecksTableView(block: ReportBlock.ChecksTable) {
    val visible = block.checks.take(40)
    SectionCard(
        block.caption ?: "Vérifications",
        "${block.checks.size} contrôle(s) — ${block.checks.count { it.status == CheckStatus.OK }} vérifié(s), " +
            "${block.checks.count { it.status == CheckStatus.FAILED }} non vérifié(s)"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEachIndexed { index, check ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) Color(0xFFF8FAFC) else Color.White,
                            RoundedCornerShape(7.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${check.code} — ${check.label}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TEXT
                        )
                        Text(check.reference, fontSize = 10.sp, color = MUTED)
                        if (check.message.isNotBlank()) {
                            Text(check.message, fontSize = 10.5.sp, color = MUTED)
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.width(210.dp)
                    ) {
                        CheckStatusPill(check.status)
                        if (check.required.isNotBlank() || check.provided.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Requis : ${check.required.ifBlank { "—" }}",
                                fontSize = 10.sp,
                                color = MUTED,
                                textAlign = TextAlign.End
                            )
                            Text(
                                "Fourni : ${check.provided.ifBlank { "—" }}",
                                fontSize = 10.sp,
                                color = TEXT,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
            if (block.checks.size > visible.size) {
                Text(
                    "… et ${block.checks.size - visible.size} contrôle(s) supplémentaire(s) dans la note de calcul.",
                    fontSize = 10.5.sp,
                    color = MUTED
                )
            }
        }
    }
}

@Composable
private fun KeyValuesView(block: ReportBlock.KeyValues) {
    SectionCard(block.caption ?: "Synthèse", null) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            block.entries.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(7.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontSize = 11.sp, color = MUTED, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                value,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = TEXT,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FigureView(figure: ReportBlock.Figure) {
    var expanded by remember(figure.key) { mutableStateOf(false) }
    val bitmap: ImageBitmap? = remember(figure.key, figure.png.size) {
        runCatching {
            SkiaImage.makeFromEncoded(figure.png).toComposeImageBitmap()
        }.getOrNull()
    }
    val maxHeight = if (expanded) 1500.dp else 420.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, BORDER, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = maxHeight)
                .background(Color(0xFFFAFBFC), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = figure.caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(6.dp)
                )
            } else {
                Text("Image non disponible", fontSize = 11.sp, color = MUTED)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                figure.caption,
                fontSize = 10.5.sp,
                color = MUTED,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Réduire" else "Agrandir", fontSize = 11.sp, color = ACCENT)
            }
        }
    }
}

// ------------------------------------------------------------------- onglet 1

@Composable
fun ImportTopologyScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val document = joint.artifacts?.document
    ScreenScaffold(
        "Import ETABS & topologie 3D",
        "Modèle, résultats d'analyse et export SAFE du joint ${joint.name}",
        chapterStatus(joint, 1, 2)
    ) {
        SectionCard(
            "Fichiers sources",
            "Formats acceptés : .e2k, .xlsx, .mdb, .txt pour ETABS ; export tabulaire SAFE pour les fondations.",
            actions = {
                PrimaryButton(
                    "Analyser le joint",
                    enabled = joint.status != JointStatus.EN_COURS,
                    onClick = { controller.analyze(project, joint) }
                )
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Field(
                    "Nom du joint",
                    joint.name,
                    { joint.name = it },
                    modifier = Modifier.width(320.dp)
                )
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ToggleField(
                        "Données de démonstration",
                        "Utilise le joint exemple ETABS + l'export SAFE intégrés",
                        joint.sample
                    ) { joint.sample = it }
                }
                SecondaryButton("Charger l'exemple NDC") { controller.loadSample(project, joint) }
            }
            Spacer(Modifier.height(10.dp))
            FileRow(
                "Modèle ETABS",
                joint.modelPath,
                if (joint.sample) "Joint exemple intégré" else "Aucun fichier sélectionné",
                onBrowse = { chooseFile("Sélectionner le modèle ETABS")?.let { controller.importFile(joint, it) } },
                onClear = { joint.modelPath = null }
            )
            FileRow(
                "Résultats d'analyse",
                joint.resultsPath,
                "Tableaux de résultats (facultatif si inclus dans le modèle)",
                onBrowse = { chooseFile("Sélectionner les résultats ETABS")?.let { controller.importFile(joint, it) } },
                onClear = { joint.resultsPath = null }
            )
            FileRow(
                "Export SAFE — fondations",
                joint.safePath,
                if (joint.sample) "Export SAFE exemple intégré" else "Aucun export SAFE sélectionné",
                onBrowse = { chooseFile("Sélectionner l'export SAFE")?.let { controller.importFile(joint, it) } },
                onClear = { joint.safePath = null }
            )
            if (joint.sample) {
                Spacer(Modifier.height(8.dp))
                InfoBanner("Mode démonstration : le joint ${com.archilab.rpa2024.sample.SampleJointA.JOINT_NAME} et l'export SAFE exemple sont utilisés. Désactivez l'option pour analyser vos propres fichiers.")
            }
            if (joint.status == JointStatus.EN_COURS) {
                Spacer(Modifier.height(8.dp))
                BusyRow("Analyse du modèle et composition de la note de calcul…")
            }
            joint.error?.let {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(it)
            }
            WarningBanner(joint.warnings)
        }

        val imported = joint.imported
        if (imported != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile("Barres", "${imported.model.frames.size}", "Éléments frame", Modifier.weight(1f))
                MetricTile("Surfaces", "${imported.model.areas.size}", "Voiles et planchers", Modifier.weight(1f))
                MetricTile("Nœuds", "${imported.model.points.size}", "Points du modèle", Modifier.weight(1f))
                MetricTile("Niveaux", "${imported.model.joint.storySystem.orderedStories.size}", "Étages reconstruits", Modifier.weight(1f))
                MetricTile("Sections", "${imported.model.sections.size}", "Familles ETABS", Modifier.weight(1f))
                MetricTile(
                    "Fondations",
                    "${joint.foundations?.families?.size ?: 0}",
                    "Familles SAFE",
                    Modifier.weight(1f)
                )
            }
            if (document != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    MetricTile("Chapitres", "${document.chapters.size}", "Note de calcul CTC", Modifier.weight(1f))
                    MetricTile("Figures", "${document.allChapters.sumOf { chapter -> chapter.blocks.count { it is ReportBlock.Figure } }}", "PNG 300 DPI", Modifier.weight(1f))
                    MetricTile("Équations", "${document.allChapters.sumOf { chapter -> chapter.blocks.count { it is ReportBlock.Equation } }}", "OMML natif Word", Modifier.weight(1f))
                    MetricTile("Vérifications", "${document.allChapters.sumOf { chapter -> chapter.blocks.count { it is ReportBlock.ChecksTable || it is ReportBlock.Verdict } }}", "Contrôles réglementaires", Modifier.weight(1f))
                    MetricTile(
                        "Statut global",
                        statusLabel(document.status),
                        "${document.failedCount} chapitre(s) en échec",
                        Modifier.weight(1f)
                    )
                }
            }
        }

        val chapter1 = chapterOf(joint, 1)
        val chapter2 = chapterOf(joint, 2)
        if (chapter1 == null || chapter2 == null) {
            EmptyAnalysis(
                "La topologie, les plans de coffrage et la vue 3D seront affichés après l'analyse.",
                onAnalyze = { controller.analyze(project, joint) }
            )
        } else {
            ChapterSection(chapter1)
            ChapterSection(chapter2)
        }
    }
}

// ------------------------------------------------------------------- onglet 2

@Composable
fun MaterialsRpaScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    ScreenScaffold(
        "Matériaux & paramètres RPA 2024",
        "Configuration sismique du joint, combinaisons d'actions et descente de charges",
        chapterStatus(joint, 3, 4, 5)
    ) {
        SectionCard(
            "Paramètres sismiques",
            "Zone, site, groupe d'importance et système de contreventement (RPA 2024 — DTR BC 2.48).",
            actions = {
                PrimaryButton(
                    "Appliquer et recalculer",
                    enabled = joint.status != JointStatus.EN_COURS,
                    onClick = { controller.analyze(project, joint) }
                )
            }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownField(
                    "Zone sismique",
                    joint.zoneCode,
                    SeismicZone.entries.map { it.code to "${it.code} — ${it.level} (A = ${ReportText.plain(it.coeffA, 2)})" },
                    { joint.zoneCode = it },
                    Modifier.weight(1f)
                )
                DropdownField(
                    "Classe de site",
                    joint.siteCode,
                    SiteClass.entries.map { it.code to "${it.code} — ${it.description}" },
                    { joint.siteCode = it },
                    Modifier.weight(1f)
                )
                DropdownField(
                    "Groupe d'importance",
                    joint.importanceCode,
                    ImportanceGroup.entries.map { it.code to "${it.code} — ${it.description} (I = ${ReportText.plain(it.coeffI, 2)})" },
                    { joint.importanceCode = it },
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownField(
                    "Système de contreventement",
                    joint.bracingId.toString(),
                    BracingSystem.entries.map { it.id.toString() to "${it.id} — ${it.description} (R = ${ReportText.plain(it.coeffR, 1)})" },
                    { joint.bracingId = it.toIntOrNull() ?: joint.bracingId },
                    Modifier.weight(1f)
                )
                DropdownField(
                    "Catégorie d'occupation",
                    joint.occupancyCode,
                    OccupancyCase.entries.map { it.name to "${it.code} — ${it.description} (ψ = ${ReportText.plain(it.psi, 2)})" },
                    { joint.occupancyCode = it },
                    Modifier.weight(1f)
                )
                DropdownField(
                    "Remplissage",
                    joint.infill.name,
                    listOf(
                        InfillType.DENSE.name to "Dense — maçonnerie rigide",
                        InfillType.LIGHT.name to "Léger — cloisons non structurelles"
                    ),
                    { code -> runCatching { InfillType.valueOf(code) }.getOrNull()?.let { joint.infill = it } },
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            DividerLine()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    ToggleField(
                        "Régularité en plan",
                        "Absence de dissymétrie et de discontinuité",
                        joint.planRegular
                    ) { joint.planRegular = it }
                }
                Box(Modifier.weight(1f)) {
                    ToggleField(
                        "Régularité en élévation",
                        "Distribution homogène des raideurs sur la hauteur",
                        joint.elevationRegular
                    ) { joint.elevationRegular = it }
                }
                Box(Modifier.weight(1f)) {
                    ToggleField(
                        "Voiles redondants",
                        "Système redondant en plan (double contreventement)",
                        joint.redundantWalls
                    ) { joint.redundantWalls = it }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    "Poids sismique manuel W (kN) — facultatif",
                    joint.manualWeightText,
                    { joint.manualWeightText = it },
                    placeholder = "Calculé depuis ETABS si vide",
                    numeric = true,
                    modifier = Modifier.width(420.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    "Laisser vide pour utiliser la descente de charges issue du modèle.",
                    fontSize = 11.sp,
                    color = MUTED
                )
            }
            joint.error?.let {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(it)
            }
            WarningBanner(joint.warnings)
        }

        val chapters = listOfNotNull(chapterOf(joint, 3), chapterOf(joint, 4), chapterOf(joint, 5))
        if (chapters.isEmpty()) {
            EmptyAnalysis(
                "Les matériaux, combinaisons et la descente de charges seront affichés après l'analyse.",
                onAnalyze = { controller.analyze(project, joint) }
            )
        } else {
            chapters.forEach { ChapterSection(it) }
        }
    }
}

// ------------------------------------------------------------------- onglet 3

@Composable
fun SeismicScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val chapter = chapterOf(joint, 9)
    ScreenScaffold(
        "Vérifications sismiques globales",
        "Spectre de calcul, participation modale, recalage MSE/MDS, déplacements et P-Δ",
        chapter?.status
    ) {
        if (chapter == null) {
            EmptyAnalysis(
                "L'étude sismique complète sera disponible après l'analyse.",
                onAnalyze = { controller.analyze(project, joint) }
            )
            return@ScreenScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Zone / Site", "${joint.zoneCode} / ${joint.siteCode}", "RPA 2024", Modifier.weight(1f))
            MetricTile("I / R", importanceR(joint), "Coefficients retenus", Modifier.weight(1f))
            MetricTile("Chapitre 9", ReportText.statusLabel(chapter.status), "${chapter.blocks.size} blocs", Modifier.weight(1f))
            MetricTile(
                "Vérifications",
                "${chapter.blocks.filterIsInstance<ReportBlock.ChecksTable>().sumOf { it.checks.size }}",
                "Contrôles sismiques",
                Modifier.weight(1f)
            )
            MetricTile(
                "Spectre",
                if (joint.siteCode == SiteClass.SS.code) "Étude spécifique" else "Tabulé",
                "Site ${joint.siteCode}",
                Modifier.weight(1f)
            )
        }
        ChapterSection(chapter)
    }
}

private fun importanceR(joint: JointWorkspace): String {
    val importance = runCatching { ImportanceGroup.entries.first { it.code == joint.importanceCode } }.getOrNull()
    val bracing = runCatching { BracingSystem.fromId(joint.bracingId) }.getOrNull()
    return "${ReportText.plain(importance?.coeffI ?: 1.0, 2)} / ${ReportText.plain(bracing?.coeffR ?: 1.0, 1)}"
}

// ------------------------------------------------------------------- onglet 4

@Composable
fun FloorsScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val chapter = chapterOf(joint, 8)
    ScreenScaffold(
        "Planchers & poutrelles",
        "Méthode forfaitaire Koka, ferraillage des poutrelles, flèche et nappe BAELT",
        chapter?.status
    ) {
        if (chapter == null) {
            EmptyAnalysis(
                "L'étude des poutrelles continues sera disponible après l'analyse.",
                onAnalyze = { controller.analyze(project, joint) }
            )
            return@ScreenScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                "Planchers",
                "${chapter.blocks.count { it is ReportBlock.Table }}",
                "Tableaux de résultats",
                Modifier.weight(1f)
            )
            MetricTile(
                "Fiches techniques",
                "${chapter.blocks.count { it is ReportBlock.Figure }}",
                "Koka / BAELT + schémas",
                Modifier.weight(1f)
            )
            MetricTile("Charges", "Q = ${ReportText.plain(joint.options.liveLoadKNPerM2, 2)} kN/m²", "Exploitation", Modifier.weight(1f))
            MetricTile("Superstructures", "+ ${ReportText.plain(joint.options.finishingLoadKNPerM2, 2)} kN/m²", "Revêtements", Modifier.weight(1f))
            MetricTile("Chapitre 8", ReportText.statusLabel(chapter.status), "${chapter.blocks.size} blocs", Modifier.weight(1f))
        }
        ChapterSection(chapter)
    }
}

// ------------------------------------------------------------------- onglet 5

@Composable
fun FramesScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val chapter6 = chapterOf(joint, 6)
    val chapter7 = chapterOf(joint, 7)
    val chapter10 = chapterOf(joint, 10)
    val columns = sectionOf(chapter10, "10.1", "10.2")
    val beams = sectionOf(chapter10, "10.2", "10.3")
    ScreenScaffold(
        "Poteaux & poutres",
        "Prédimensionnement, ferraillage BAEL 91 et dispositions constructives RPA 2024",
        CheckStatus.worst(listOfNotNull(chapter6?.status, chapter7?.status, columns?.status, beams?.status))
    ) {
        if (chapter6 == null || chapter7 == null || columns == null || beams == null) {
            EmptyAnalysis(
                "Le prédimensionnement et le ferraillage des poteaux et poutres seront affichés après l'analyse.",
                onAnalyze = { controller.analyze(project, joint) }
            )
            return@ScreenScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("Poteaux", "${joint.imported?.model?.joint?.sectionCatalog?.columns?.size ?: 0}", "Familles", Modifier.weight(1f))
            MetricTile("Poutres", "${joint.imported?.model?.joint?.sectionCatalog?.beams?.size ?: 0}", "Familles", Modifier.weight(1f))
            MetricTile("Chapitre 6", ReportText.statusLabel(chapter6.status), "Prédimensionnement poutres", Modifier.weight(1f))
            MetricTile("Chapitre 7", ReportText.statusLabel(chapter7.status), "Prédimensionnement poteaux", Modifier.weight(1f))
            MetricTile("Ferraillage", ReportText.statusLabel(CheckStatus.worst(listOf(columns.status, beams.status))), "Chapitre 10.1 / 10.2", Modifier.weight(1f))
        }
        ChapterSection(chapter6)
        ChapterSection(chapter7)
        ChapterSection(columns)
        ChapterSection(beams)
    }
}

// ------------------------------------------------------------------- onglet 6

@Composable
fun WallsScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val walls = sectionOf(chapterOf(joint, 10), "10.3", null)
    ScreenScaffold(
        "Voiles de contreventement",
        "Zones critiques, effort tranchant amplifié, ferraillage et schémas d'exécution",
        walls?.status
    ) {
        if (walls == null) {
            EmptyAnalysis(
                "L'étude des voiles sera disponible après l'analyse du joint.",
                onAnalyze = { controller.analyze(project, joint) }
            )
            return@ScreenScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                "Voiles",
                "${walls.blocks.count { it is ReportBlock.Table }}",
                "Tableaux de ferraillage",
                Modifier.weight(1f)
            )
            MetricTile(
                "Schémas",
                "${walls.blocks.count { it is ReportBlock.Figure }}",
                "Coupes et élévations",
                Modifier.weight(1f)
            )
            MetricTile(
                "Vérifications",
                "${walls.blocks.filterIsInstance<ReportBlock.ChecksTable>().sumOf { it.checks.size }}",
                "Contrôles RPA",
                Modifier.weight(1f)
            )
            MetricTile("Section 10.3", ReportText.statusLabel(walls.status), "${walls.blocks.size} blocs", Modifier.weight(1f))
        }
        ChapterSection(walls)
    }
}

// ------------------------------------------------------------------- onglet 7

@Composable
fun SecondaryScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val chapter11 = chapterOf(joint, 11)
    val chapter12 = chapterOf(joint, 12)
    val chapter13 = chapterOf(joint, 13)
    ScreenScaffold(
        "Acrotère, escaliers & fondations",
        "Éléments secondaires, poutre-dalle d'escalier et semelles filantes issues de SAFE",
        CheckStatus.worst(listOfNotNull(chapter11?.status, chapter12?.status, chapter13?.status))
    ) {
        val foundations = joint.foundations
        if (foundations != null) {
            SectionCard(
                "Export SAFE — familles de semelles",
                "${foundations.sourceName} · ${foundations.families.size} famille(s) · Ks = ${ReportText.plain(foundations.geotechnical.subgradeModulusKNM3, 0)} kN/m³ · σadm = ${ReportText.plain(foundations.geotechnical.allowableStressBars, 2)} bar"
            ) {
                ReportTableView(
                    listOf("Famille", "Type", "Largeur (m)", "Hauteur (m)", "Longueur (m)", "Éléments"),
                    foundations.families.map { family ->
                        listOf(
                            family.name,
                            family.kind.toString(),
                            ReportText.plain(family.flangeWidthMeters, 2),
                            ReportText.plain(family.totalHeightMeters, 2),
                            family.footingLengthMeters?.let { ReportText.plain(it, 2) } ?: "filante",
                            "${family.elementIds.size}"
                        )
                    },
                    caption = "Semelles filantes lues depuis l'export SAFE"
                )
                WarningBanner(foundations.warnings)
            }
        }
        val chapters = listOfNotNull(chapter11, chapter12, chapter13)
        if (chapters.isEmpty()) {
            EmptyAnalysis(
                "Acrotère, escaliers et fondations seront étudiés après l'analyse.",
                onAnalyze = { controller.analyze(project, joint) }
            )
        } else {
            chapters.forEach { ChapterSection(it) }
        }
    }
}

// ------------------------------------------------------------------- onglet 8

@Composable
fun ReportExportScreen(project: ProjectState, joint: JointWorkspace, controller: ProjectController) {
    val document = joint.artifacts?.document
    ScreenScaffold(
        "Génération Note de Calcul CTC",
        "Document Word éditable avec équations OMML, sommaire automatique et export PDF paginé",
        document?.status ?: if (joint.status == JointStatus.PRET) CheckStatus.OK else null
    ) {
        SectionCard("Identification du projet", "Repris sur la page de garde encadrée du document CTC") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Field("Intitulé du projet", joint.projectTitle, { joint.projectTitle = it }, modifier = Modifier.weight(2f))
                Field("Wilaya", joint.wilaya, { joint.wilaya = it }, modifier = Modifier.weight(1f))
                Field("Joint / bloc", joint.name, { joint.name = it }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Field("Maître d'ouvrage", joint.client, { joint.client = it }, modifier = Modifier.weight(2f))
                Field("Bureau d'études", joint.engineer, { joint.engineer = it }, modifier = Modifier.weight(2f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Field("Référence", joint.reference, { joint.reference = it }, modifier = Modifier.weight(2f))
                Field("Révision", joint.revision, { joint.revision = it }, modifier = Modifier.weight(1f))
                Text(
                    "Date du jour : ${UiText.today()}",
                    fontSize = 11.5.sp,
                    color = MUTED,
                    modifier = Modifier.weight(2f).padding(top = 18.dp)
                )
            }
        }

        SectionCard("Hypothèses de charges et rendu", "Valeurs injectées dans les chapitres 5, 8 et 11 à 13") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Field(
                    "Exploitation Q (kN/m²)",
                    ReportText.plain(joint.options.liveLoadKNPerM2, 2),
                    { value -> decimal(value)?.let { joint.options = joint.options.copy(liveLoadKNPerM2 = it) } },
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                Field(
                    "Revêtements (kN/m²)",
                    ReportText.plain(joint.options.finishingLoadKNPerM2, 2),
                    { value -> decimal(value)?.let { joint.options = joint.options.copy(finishingLoadKNPerM2 = it) } },
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                Field(
                    "Surcharges permanentes (kN/m²)",
                    ReportText.plain(joint.options.permanentSuperLoadKNPerM2, 2),
                    { value -> decimal(value)?.let { joint.options = joint.options.copy(permanentSuperLoadKNPerM2 = it) } },
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                Field(
                    "Résolution des figures (DPI)",
                    ReportText.plain(joint.options.renderDpi, 0),
                    { value -> decimal(value)?.takeIf { it >= 96 }?.let { joint.options = joint.options.copy(renderDpi = it) } },
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Figures PNG ${ReportText.plain(joint.options.renderDpi, 0)} DPI, ${joint.options.maxPlanFigures} plans de coffrage maximum, ${joint.options.maxModeRows} modes affichés.",
                fontSize = 10.5.sp,
                color = MUTED
            )
        }

        SectionCard(
            "Contrôle du dossier",
            "Les treize chapitres réglementaires sont vérifiés avant l'export.",
            actions = {
                SecondaryButton(
                    "Relancer l'analyse",
                    enabled = joint.status != JointStatus.EN_COURS
                ) { controller.analyze(project, joint) }
            }
        ) {
            if (document == null) {
                InfoBanner("Lancez l'analyse du joint pour composer et contrôler la note de calcul.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    document.chapters.forEach { chapter ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(7.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "${chapter.number}".padStart(2, '0'),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ACCENT,
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                chapter.title,
                                fontSize = 11.5.sp,
                                color = TEXT,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${chapter.blocks.size} blocs",
                                fontSize = 10.sp,
                                color = MUTED,
                                modifier = Modifier.width(70.dp),
                                textAlign = TextAlign.End
                            )
                            Spacer(Modifier.width(12.dp))
                            CheckStatusPill(chapter.status)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${document.chapters.size} chapitres · ${document.cover.summary.size} rubriques de synthèse · " +
                        "sommaire automatique TOC \\o \"1-3\" · en-tête et pied de page « Page X / Y » · équations Office Math natives.",
                    fontSize = 10.5.sp,
                    color = MUTED
                )
            }
        }

        SectionCard(
            "Captures ETABS / SAFE",
            "Associez une image PNG/JPG à chaque repère de la note. L'image remplace le repère dans Word et PDF ; les repères non associés restent visibles pour contrôle."
        ) {
            if (document == null || joint.capturePlaceholders.isEmpty()) {
                InfoBanner("Aucun repère de capture disponible : lancez l'analyse pour composer la note de référence.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${joint.captureFiles.size} / ${joint.capturePlaceholders.size} capture(s) associée(s)",
                        fontSize = 11.5.sp,
                        color = TEXT,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("Importer un dossier d'images") {
                        chooseDirectory("Dossier des captures ETABS / SAFE")?.let { controller.importCaptureFolder(joint, it) }
                    }
                    SecondaryButton("Extraire d'un DOCX de référence") {
                        chooseFile("Sélectionner le DOCX de référence")?.let { controller.importReferenceDocx(joint, it) }
                    }
                    TextButton(onClick = { controller.clearCaptures(joint) }) {
                        Text("Tout retirer", fontSize = 11.sp, color = MUTED)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    joint.capturePlaceholders.forEachIndexed { index, tag ->
                        val file = joint.captureFiles[tag]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (file == null) Color(0xFFFFF7E6) else Color(0xFFF1F8F4),
                                    RoundedCornerShape(7.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                (index + 1).toString().padStart(2, '0'),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ACCENT,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tag,
                                    fontSize = 10.5.sp,
                                    color = TEXT,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    file?.fileName?.toString() ?: "Aucune image associée",
                                    fontSize = 9.5.sp,
                                    color = if (file == null) WARN_AMBER else OK_GREEN
                                )
                            }
                            SecondaryButton(if (file == null) "Choisir" else "Remplacer") {
                                chooseFile("Sélectionner la capture ${index + 1}")?.let {
                                    controller.setCapture(joint, tag, it)
                                }
                            }
                            if (file != null) {
                                TextButton(onClick = { controller.setCapture(joint, tag, null) }) {
                                    Text("Retirer", fontSize = 10.5.sp, color = MUTED)
                                }
                            }
                        }
                    }
                }
                joint.captureMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    SurfaceNote("Captures", it, ACCENT, Color(0xFFEAF2F8))
                }
            }
        }

        SectionCard(
            "Export du document",
            "Le fichier Word conserve les équations éditables ; le PDF applique la pagination CTC."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    "Exporter en Word (.docx)",
                    enabled = joint.imported != null && !joint.exportBusy
                ) { controller.export(project, joint, ExportFormat.WORD) }
                PrimaryButton(
                    "Exporter en PDF (.pdf)",
                    enabled = joint.imported != null && !joint.exportBusy
                ) { controller.export(project, joint, ExportFormat.PDF) }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (joint.imported == null) {
                        "Analyse requise avant l'export."
                    } else {
                        "Nom proposé : ${joint.exportBaseName}.docx / .pdf"
                    },
                    fontSize = 11.sp,
                    color = MUTED
                )
            }
            if (joint.exportBusy) {
                Spacer(Modifier.height(10.dp))
                BusyRow("Génération du document en cours (figures 300 DPI, équations, pagination)…")
            }
            joint.exportMessage?.let {
                Spacer(Modifier.height(10.dp))
                SurfaceNote("Export terminé", it, OK_GREEN, OK_BG)
            }
            joint.exportError?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(it)
            }
            joint.lastExport?.let { path ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        "Ouvrir le fichier",
                        enabled = Files.exists(path)
                    ) { openFile(path) }
                    Spacer(Modifier.width(10.dp))
                    Text(path.toAbsolutePath().toString(), fontSize = 10.5.sp, color = MUTED)
                }
            }
        }
    }
}

private fun openFile(path: Path) {
    runCatching {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(path.toFile())
        }
    }
}

// ------------------------------------------------------------------- helpers

private fun chapterOf(joint: JointWorkspace, number: Int): ReportChapter? =
    joint.artifacts?.document?.chapters?.firstOrNull { it.number == number }

private fun chapterStatus(joint: JointWorkspace, vararg numbers: Int): CheckStatus? {
    val statuses = numbers.map { chapterOf(joint, it)?.status }.filterNotNull()
    return if (statuses.isEmpty()) null else CheckStatus.worst(statuses)
}

private fun sectionOf(chapter: ReportChapter?, start: String, stop: String?): ReportChapter? {
    if (chapter == null) return null
    val blocks = mutableListOf<ReportBlock>()
    var active = false
    chapter.blocks.forEach { block ->
        if (block is ReportBlock.Heading && block.level == 2) {
            if (block.text.startsWith(start)) {
                active = true
                blocks += block
                return@forEach
            }
            if (stop != null && block.text.startsWith(stop)) {
                active = false
                return@forEach
            }
        }
        if (active) blocks += block
    }
    if (blocks.isEmpty()) return null
    val heading = blocks.first() as ReportBlock.Heading
    val statuses = blocks.filterIsInstance<ReportBlock.ChecksTable>()
        .flatMap { it.checks }
        .map { it.status } + blocks.filterIsInstance<ReportBlock.Verdict>().map { it.status }
    val hasChecks = blocks.any { it is ReportBlock.ChecksTable }
    return chapter.copy(
        blocks = blocks,
        title = heading.text.removePrefix("$start ").ifBlank { heading.text },
        summary = "Section $start — ${chapter.title}",
        status = when {
            statuses.isNotEmpty() -> CheckStatus.worst(statuses)
            hasChecks -> chapter.status
            else -> CheckStatus.NOT_APPLICABLE
        },
        numbered = false
    )
}
