package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.geometry.CheckStatus
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Export PDF de la note de calcul, construit a partir du meme modele de blocs que le DOCX.
 *
 * Le corps est compose en premier afin de connaitre la page de depart de chaque chapitre ;
 * le sommaire est ensuite genere avec les numeros de page reels, puis la page de garde, le
 * sommaire et le corps sont fusionnes. Les pieds de page « Page X / Y » sont apposes
 * apres assemblage, lorsque le nombre total de pages est definitif.
 */
class PdfReportExporter(
    private val pageWidth: Float = PDRectangle.A4.width,
    private val pageHeight: Float = PDRectangle.A4.height,
    private val marginLeft: Float = 48f,
    private val marginRight: Float = 48f,
    private val marginTop: Float = 54f,
    private val marginBottom: Float = 52f,
    private val accent: Color = Color(0x1F, 0x3A, 0x5F),
    private val accentSoft: Color = Color(0xDC, 0xE6, 0xF1),
    private val textColor: Color = Color(0x1F, 0x29, 0x37),
    private val muted: Color = Color(0x4B, 0x55, 0x63),
    private val grid: Color = Color(0xC7, 0xD2, 0xE0)
) {

    private val contentWidth: Float get() = pageWidth - marginLeft - marginRight

    fun write(document: ReportDocument, target: Path): Path {
        val absolute = target.toAbsolutePath()
        absolute.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(absolute).use { output -> write(document, output) }
        return absolute
    }

    fun bytes(document: ReportDocument): ByteArray {
        val buffer = ByteArrayOutputStream()
        write(document, buffer)
        return buffer.toByteArray()
    }

    fun write(document: ReportDocument, output: OutputStream) {
        val front = PDDocument()
        val body = PDDocument()
        val temps = mutableListOf<Path>()
        try {
            val frontFonts = Fonts(front)
            val toc = TocLayout(front, frontFonts, document)
            val tocPages = toc.pageCount()

            val bodyFonts = Fonts(body)
            val canvas = Canvas(body, bodyFonts, document)
            val startPages = linkedMapOf<Int, Int>()
            document.allChapters.forEach { chapter ->
                canvas.newPage()
                startPages[chapter.number] = body.numberOfPages - 1
                canvas.chapter(chapter)
            }
            canvas.finish()

            toc.cover(document.cover)
            toc.entries(document.allChapters, startPages, 1 + tocPages)
            toc.finish()

            val totalPages = 1 + tocPages + body.numberOfPages
            stampFooters(front, frontFonts, document, totalPages, skipFirstPage = true)
            stampFooters(
                body,
                bodyFonts,
                document,
                totalPages,
                skipFirstPage = false,
                pageOffset = 1 + tocPages
            )

            val frontFile = tempFile("ndc-front", temps)
            val bodyFile = tempFile("ndc-body", temps)
            front.save(frontFile.toFile())
            body.save(bodyFile.toFile())

            val merged = tempFile("ndc-merged", temps)
            val merger = PDFMergerUtility()
            merger.addSource(frontFile.toFile())
            merger.addSource(bodyFile.toFile())
            merger.setDestinationFileName(merged.toString())
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache())

            Loader.loadPDF(merged.toFile()).use { final ->
                final.documentInformation = information(document)
                final.save(output)
            }
        } finally {
            runCatching { front.close() }
            runCatching { body.close() }
            temps.forEach { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun tempFile(prefix: String, registry: MutableList<Path>): Path {
        val file = Files.createTempFile(prefix, ".pdf")
        registry.add(file)
        return file
    }

    private fun information(document: ReportDocument): PDDocumentInformation =
        PDDocumentInformation().apply {
            title = "Note de calcul — ${document.cover.jointName}"
            author = document.cover.engineer.ifBlank { "Archilab RPA 2024" }
            subject = document.cover.subtitle
            keywords = "RPA 2024, BAEL 91, CTC, note de calcul"
            creator = "Archilab RPA 2024"
        }

    private fun stampFooters(
        document: PDDocument,
        fonts: Fonts,
        model: ReportDocument,
        totalPages: Int,
        skipFirstPage: Boolean,
        pageOffset: Int = 0
    ) {
        document.pages.forEachIndexed { index, page ->
            if (skipFirstPage && index == 0) return@forEachIndexed
            val label = "Page ${pageOffset + index + 1} / $totalPages"
            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { stream ->
                stream.setStrokingColor(accentSoft)
                stream.setLineWidth(0.6f)
                stream.moveTo(marginLeft, marginBottom - 8f)
                stream.lineTo(pageWidth - marginRight, marginBottom - 8f)
                stream.stroke()
                val reference = "${model.cover.reference}  ·  Rév. ${model.cover.revision}"
                paint(stream, reference, marginLeft, marginBottom - 22f, 7.5f, fonts.regular, muted)
                val labelWidth = measure(label, fonts.regular, 7.5f)
                paint(stream, label, (pageWidth - labelWidth) / 2f, marginBottom - 22f, 7.5f, fonts.regular, muted)
                val dateWidth = measure(model.cover.dateLabel, fonts.regular, 7.5f)
                paint(
                    stream,
                    model.cover.dateLabel,
                    pageWidth - marginRight - dateWidth,
                    marginBottom - 22f,
                    7.5f,
                    fonts.regular,
                    muted
                )
            }
        }
    }

    // ------------------------------------------------------------------ polices

    private class Fonts(document: PDDocument) {
        val regular: PDFont = load(document, false)
        val bold: PDFont = load(document, true)

        private companion object {
            fun load(document: PDDocument, bold: Boolean): PDFont {
                val candidates = if (bold) {
                    listOf(
                        "C:/Windows/Fonts/arialbd.ttf",
                        "C:/Windows/Fonts/calibrib.ttf",
                        "C:/Windows/Fonts/segoeuib.ttf"
                    )
                } else {
                    listOf(
                        "C:/Windows/Fonts/arial.ttf",
                        "C:/Windows/Fonts/calibri.ttf",
                        "C:/Windows/Fonts/segoeui.ttf"
                    )
                }
                candidates.forEach { path ->
                    val file = File(path)
                    if (file.isFile) {
                        val loaded = runCatching { PDType0Font.load(document, file) }.getOrNull()
                        if (loaded != null) return loaded
                    }
                }
                val name = if (bold) Standard14Fonts.FontName.HELVETICA_BOLD else Standard14Fonts.FontName.HELVETICA
                return PDType1Font(name)
            }
        }
    }

    private fun paint(
        stream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        font: PDFont,
        color: Color
    ) {
        val value = sanitise(text)
        if (value.isBlank()) return
        stream.beginText()
        stream.setNonStrokingColor(color)
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        runCatching { stream.showText(value) }.onFailure {
            runCatching { stream.showText(asciiFallback(value)) }
        }
        stream.endText()
    }

    private fun measure(text: String, font: PDFont, size: Float): Float =
        runCatching { font.getStringWidth(sanitise(text)) / 1000f * size }
            .getOrElse { text.length * size * 0.52f }

    private fun sanitise(text: String): String = text
        .replace('\t', ' ')
        .replace('\n', ' ')
        .filter { it.code >= 32 }

    private fun asciiFallback(text: String): String = text
        .replace("≥", ">=")
        .replace("≤", "<=")
        .replace("≈", "~")
        .replace("→", "->")
        .replace("—", "-")
        .replace("–", "-")
        .replace("’", "'")
        .replace("“", "\"")
        .replace("”", "\"")
        .replace("·", "-")
        .replace("•", "-")
        .filter { it.code < 256 }

    // ------------------------------------------------------------------ corps

    /** Compose le corps de la note (chapitres et annexes). */
    private inner class Canvas(
        private val pdf: PDDocument,
        private val fonts: Fonts,
        private val model: ReportDocument
    ) {
        private var stream: PDPageContentStream? = null
        private var y: Float = pageHeight - marginTop

        fun newPage() {
            stream?.close()
            val page = PDPage(PDRectangle(pageWidth, pageHeight))
            pdf.addPage(page)
            stream = PDPageContentStream(pdf, page)
            y = pageHeight - marginTop
            drawHeader()
        }

        fun finish() {
            stream?.close()
            stream = null
        }

        private fun drawHeader() {
            val current = stream ?: return
            paint(current, model.cover.republicLine, marginLeft, pageHeight - 34f, 7.5f, fonts.bold, accent)
            val right = "${model.cover.projectTitle}  ·  Joint ${model.cover.jointName}"
            val rightWidth = measure(right, fonts.regular, 7.5f)
            paint(current, right, pageWidth - marginRight - rightWidth, pageHeight - 34f, 7.5f, fonts.regular, muted)
            current.setStrokingColor(accentSoft)
            current.setLineWidth(0.7f)
            current.moveTo(marginLeft, pageHeight - 40f)
            current.lineTo(pageWidth - marginRight, pageHeight - 40f)
            current.stroke()
        }

        private fun ensure(height: Float) {
            if (y - height < marginBottom) newPage()
        }

        private fun draw(text: String, x: Float, yValue: Float, size: Float, bold: Boolean, color: Color) {
            val current = stream ?: return
            paint(current, text, x, yValue, size, if (bold) fonts.bold else fonts.regular, color)
        }

        fun chapter(chapter: ReportChapter) {
            ensure(60f)
            heading(chapter.headingText, 1)
            paragraph(
                chapter.summary + "  Statut : " + ReportText.statusLabel(chapter.status) + ".",
                size = 8.5f,
                color = muted,
                after = 12f
            )
            chapter.blocks.forEach { block -> block(block) }
        }

        private fun block(block: ReportBlock) {
            when (block) {
                is ReportBlock.Heading -> heading(block.text, block.level)
                is ReportBlock.Paragraph -> paragraph(
                    block.text,
                    size = if (block.sizePt > 0) block.sizePt.toFloat() else 10f,
                    color = block.colorHex?.let { hex -> runCatching { Color.decode("#$hex") }.getOrDefault(textColor) } ?: textColor,
                    bold = block.bold,
                    align = block.align,
                    before = block.spaceBeforePt.toFloat(),
                    after = block.spaceAfterPt.toFloat()
                )
                is ReportBlock.Bullets -> block.items.forEach { item -> bullet(item, "\u2022") }
                is ReportBlock.Numbered -> block.items.forEachIndexed { index, item -> bullet(item, "${index + 1}.") }
                is ReportBlock.Equation -> equation(block)
                is ReportBlock.Table -> table(block)
                is ReportBlock.Figure -> image(block.png, block.widthCm, block.caption)
                is ReportBlock.Note -> note(block)
                is ReportBlock.Verdict -> verdict(block)
                is ReportBlock.ChecksTable -> checksTable(block)
                is ReportBlock.KeyValues -> keyValues(block)
                ReportBlock.PageBreak -> newPage()
            }
        }

        private fun heading(text: String, level: Int) {
            val size = when (level) {
                1 -> 15f
                2 -> 11.5f
                else -> 10.5f
            }
            val before = if (level == 1) 6f else 10f
            val lines = wrap(text, fonts.bold, size, contentWidth)
            ensure(before + lines.size * (size + 4f) + 8f)
            y -= before
            val color = if (level == 1) accent else textColor
            lines.forEach { line ->
                draw(line, marginLeft, y - size, size, true, color)
                y -= size + 4f
            }
            if (level == 1) {
                stream?.setStrokingColor(accent)
                stream?.setLineWidth(1.4f)
                stream?.moveTo(marginLeft, y - 2f)
                stream?.lineTo(pageWidth - marginRight, y - 2f)
                stream?.stroke()
                y -= 6f
            }
            y -= 4f
        }

        private fun paragraph(
            text: String,
            size: Float = 10f,
            color: Color = textColor,
            bold: Boolean = false,
            align: ReportAlign = ReportAlign.LEFT,
            before: Float = 0f,
            after: Float = 6f
        ) {
            val font = if (bold) fonts.bold else fonts.regular
            val lines = wrap(text, font, size, contentWidth - 4f)
            ensure(before + lines.size * (size + 2.5f) + after)
            y -= before
            lines.forEach { line ->
                val lineWidth = measure(line, font, size)
                val x = when (align) {
                    ReportAlign.CENTER -> marginLeft + (contentWidth - lineWidth) / 2f
                    ReportAlign.RIGHT -> pageWidth - marginRight - lineWidth
                    else -> marginLeft
                }
                draw(line, max(marginLeft, x), y - size, size, bold, color)
                y -= size + 2.5f
            }
            y -= after
        }

        private fun bullet(text: String, marker: String) {
            val size = 9.5f
            val indent = 16f
            val lines = wrap(text, fonts.regular, size, contentWidth - indent - 4f)
            ensure(lines.size * (size + 2.5f) + 5f)
            lines.forEachIndexed { index, line ->
                if (index == 0) draw(marker, marginLeft + 4f, y - size, size, false, accent)
                draw(line, marginLeft + indent, y - size, size, false, textColor)
                y -= size + 2.5f
            }
            y -= 2f
        }

        private fun equation(block: ReportBlock.Equation) {
            val plain = OmmlEquationBuilder.plainText(block.nodes)
            ensure(28f)
            paragraph(plain, size = 10.5f, align = ReportAlign.CENTER, before = 6f, after = 2f)
            block.caption?.let { caption(it) }
            y -= 4f
        }

        private fun caption(text: String) {
            val size = 8f
            val lines = wrap(text, fonts.regular, size, contentWidth - 24f)
            ensure(lines.size * (size + 2f) + 4f)
            lines.forEach { line ->
                val x = marginLeft + (contentWidth - measure(line, fonts.regular, size)) / 2f
                draw(line, x, y - size, size, false, muted)
                y -= size + 2f
            }
            y -= 4f
        }

        private fun image(png: ByteArray, widthCm: Double, captionText: String) {
            if (png.isEmpty()) return
            val image = runCatching { PDImageXObject.createFromByteArray(pdf, png, "figure") }.getOrNull() ?: return
            val ratio = runCatching {
                ImageIO.read(png.inputStream())?.let { it.height.toFloat() / it.width.toFloat() }
            }.getOrNull() ?: (image.height.toFloat() / max(1, image.width).toFloat())
            var width = min(contentWidth, (widthCm * 28.3465).toFloat())
            var height = width * ratio
            val available = pageHeight - marginTop - marginBottom - 30f
            if (height > available) {
                val scale = available / height
                width *= scale
                height = available
            }
            ensure(height + 18f)
            val x = marginLeft + (contentWidth - width) / 2f
            stream?.drawImage(image, x, y - height, width, height)
            y -= height + 4f
            caption(captionText)
        }

        private fun table(block: ReportBlock.Table) {
            block.caption?.let {
                paragraph(it, size = 8.5f, color = accent, bold = true, before = 6f, after = 3f)
            }
            if (block.headers.isEmpty()) return
            val columns = block.headers.size
            val widths = normalise(block.columnWidthsCm, columns)
            val size = block.fontSizePt.toFloat().coerceIn(7f, 9.5f)
            var remaining = block.rows
            var rowIndex = 0
            while (remaining.isNotEmpty()) {
                ensure(rowHeight(block.headers, widths, size, true) + 18f)
                drawRow(block.headers, widths, size, block.headerFillHex, true, emptyList())
                var drawn = 0
                for (values in remaining) {
                    val height = rowHeight(values, widths, size, false) + 6f
                    if (y - height < marginBottom) break
                    val fill = if (block.zebra && rowIndex % 2 == 1) "F3F6FA" else "FFFFFF"
                    drawRow(values, widths, size, fill, false, block.alignments, block.firstColumnBold)
                    rowIndex++
                    drawn++
                }
                if (drawn == 0) return
                remaining = remaining.drop(drawn)
                if (remaining.isNotEmpty()) newPage()
            }
            y -= 4f
        }

        private fun rowHeight(values: List<String>, widths: List<Float>, size: Float, bold: Boolean): Float {
            val font = if (bold) fonts.bold else fonts.regular
            val lines = values.mapIndexed { index, value ->
                wrap(value, font, size, max(20f, widths.getOrElse(index) { 60f } - 6f)).size
            }.maxOrNull() ?: 1
            return lines * (size + 2f)
        }

        private fun drawRow(
            values: List<String>,
            widths: List<Float>,
            size: Float,
            fillHex: String,
            header: Boolean,
            alignments: List<ReportAlign>,
            firstColumnBold: Boolean = false,
            fills: List<String>? = null
        ) {
            val height = rowHeight(values, widths, size, header) + 6f
            ensure(height)
            val current = stream ?: return
            var x = marginLeft
            val top = y
            values.forEachIndexed { index, value ->
                val width = widths.getOrElse(index) { 60f }
                val cellFill = fills?.getOrNull(index) ?: fillHex
                val fill = runCatching { Color.decode("#$cellFill") }.getOrDefault(Color.WHITE)
                current.setNonStrokingColor(fill)
                current.addRect(x, top - height, width, height)
                current.fill()
                current.setStrokingColor(grid)
                current.setLineWidth(0.4f)
                current.addRect(x, top - height, width, height)
                current.stroke()
                val bold = header || (firstColumnBold && index == 0)
                val font = if (bold) fonts.bold else fonts.regular
                val color = if (header) Color.WHITE else textColor
                val alignment = if (header) ReportAlign.CENTER else alignments.getOrNull(index) ?: ReportAlign.LEFT
                var textY = top - 4f - size
                wrap(value, font, size, width - 6f).forEach { line ->
                    val lineWidth = measure(line, font, size)
                    val textX = when (alignment) {
                        ReportAlign.CENTER -> x + (width - lineWidth) / 2f
                        ReportAlign.RIGHT -> x + width - lineWidth - 3f
                        else -> x + 3f
                    }
                    paint(current, line, max(x + 2f, textX), textY, size, font, color)
                    textY -= size + 2f
                }
                x += width
            }
            y = top - height
        }

        private fun keyValues(block: ReportBlock.KeyValues) {
            block.caption?.let {
                paragraph(it, size = 8.5f, color = accent, bold = true, before = 6f, after = 3f)
            }
            if (block.entries.isEmpty()) return
            val size = 8.5f
            val widths = listOf(contentWidth * 0.40f, contentWidth * 0.60f)
            block.entries.forEach { (label, value) ->
                ensure(rowHeight(listOf(label, value), widths, size, false) + 8f)
                drawRow(
                    listOf(label, value),
                    widths,
                    size,
                    "FFFFFF",
                    false,
                    listOf(ReportAlign.LEFT, ReportAlign.LEFT),
                    firstColumnBold = true,
                    fills = listOf("EEF3F9", "FFFFFF")
                )
            }
            y -= 4f
        }

        private fun checksTable(block: ReportBlock.ChecksTable) {
            val caption = block.caption ?: "Contrôles réglementaires"
            paragraph(
                "$caption (${block.checks.size} contrôle(s))",
                size = 8.5f,
                color = accent,
                bold = true,
                before = 6f,
                after = 3f
            )
            if (block.checks.isEmpty()) {
                paragraph("Aucun contrôle n'est applicable.", size = 8.5f, color = muted)
                return
            }
            val headers = listOf("Code", "Contrôle", "Référence", "Exigence", "Fourni", "État")
            val widths = proportions(listOf(2.4, 5.2, 3.4, 2.4, 2.4, 1.8))
            val size = 7.8f
            ensure(rowHeight(headers, widths, size, true) + 14f)
            drawRow(headers, widths, size, "1F3A5F", true, emptyList())
            block.checks.forEachIndexed { index, check ->
                val values = listOf(
                    check.code,
                    check.label,
                    check.reference,
                    check.required,
                    check.provided,
                    ReportText.statusLabel(check.status)
                )
                ensure(rowHeight(values, widths, size, false) + 6f)
                drawRow(
                    values,
                    widths,
                    size,
                    if (index % 2 == 1) "F3F6FA" else "FFFFFF",
                    false,
                    listOf(
                        ReportAlign.CENTER,
                        ReportAlign.LEFT,
                        ReportAlign.LEFT,
                        ReportAlign.LEFT,
                        ReportAlign.LEFT,
                        ReportAlign.CENTER
                    )
                )
            }
            y -= 4f
        }

        private fun note(block: ReportBlock.Note) {
            val size = 8.8f
            val text = "${block.title} — ${block.text}"
            val lines = wrap(text, fonts.regular, size, contentWidth - 20f)
            val height = lines.size * (size + 2.5f) + 12f
            ensure(height + 6f)
            val current = stream ?: return
            val top = y
            current.setNonStrokingColor(Color(0xFF, 0xF7, 0xE6))
            current.addRect(marginLeft, top - height, contentWidth, height)
            current.fill()
            current.setStrokingColor(Color(0xF5, 0x9E, 0x0B))
            current.setLineWidth(1.2f)
            current.addRect(marginLeft, top - height, contentWidth, height)
            current.stroke()
            var textY = top - 6f - size
            lines.forEach { line ->
                paint(current, line, marginLeft + 8f, textY, size, fonts.regular, Color(0x78, 0x35, 0x0F))
                textY -= size + 2.5f
            }
            y = top - height - 6f
        }

        private fun verdict(block: ReportBlock.Verdict) {
            val color = statusColor(block.status)
            val label = ReportText.statusLabel(block.status).uppercase() + " — "
            val size = 9.2f
            val lines = wrap(label + block.text, fonts.regular, size, contentWidth - 20f)
            val height = lines.size * (size + 2.5f) + 14f
            ensure(height + 6f)
            val current = stream ?: return
            val top = y
            current.setNonStrokingColor(softStatus(block.status))
            current.addRect(marginLeft, top - height, contentWidth, height)
            current.fill()
            current.setStrokingColor(color)
            current.setLineWidth(1.4f)
            current.addRect(marginLeft, top - height, contentWidth, height)
            current.stroke()
            var textY = top - 7f - size
            lines.forEach { line ->
                paint(current, line, marginLeft + 8f, textY, size, fonts.regular, textColor)
                textY -= size + 2.5f
            }
            y = top - height - 6f
        }

        // -------------------------------------------------------------- mesures

        private fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
            val clean = sanitise(text).trim()
            if (clean.isEmpty()) return listOf("")
            val lines = mutableListOf<String>()
            val current = StringBuilder()
            clean.split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (measure(candidate, font, size) <= maxWidth || current.isEmpty()) {
                    current.setLength(0)
                    current.append(candidate)
                } else {
                    lines += current.toString()
                    current.setLength(0)
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines.ifEmpty { listOf("") }
        }

        private fun normalise(widths: List<Double>?, columns: Int): List<Float> {
            val source = if (widths == null || widths.size != columns || widths.any { it <= 0.0 }) {
                List(columns) { 1.0 }
            } else {
                widths
            }
            return proportions(source)
        }

        private fun proportions(values: List<Double>): List<Float> {
            val total = values.sum()
            return values.map { (it / total * contentWidth).toFloat() }
        }

        private fun statusColor(status: CheckStatus): Color = when (status) {
            CheckStatus.OK -> Color(0x15, 0x80, 0x3D)
            CheckStatus.WARNING -> Color(0xB4, 0x53, 0x09)
            CheckStatus.FAILED -> Color(0xB9, 0x1C, 0x1C)
            CheckStatus.NOT_APPLICABLE -> Color(0x4B, 0x55, 0x63)
        }

        private fun softStatus(status: CheckStatus): Color = when (status) {
            CheckStatus.OK -> Color(0xEC, 0xFD, 0xF3)
            CheckStatus.WARNING -> Color(0xFF, 0xF7, 0xE6)
            CheckStatus.FAILED -> Color(0xFD, 0xEC, 0xEC)
            CheckStatus.NOT_APPLICABLE -> Color(0xF3, 0xF4, 0xF6)
        }
    }

    // ------------------------------------------------------------------ page de garde et sommaire

    /** Composition de la page de garde et du sommaire. */
    private inner class TocLayout(
        private val pdf: PDDocument,
        private val fonts: Fonts,
        private val model: ReportDocument
    ) {
        private var stream: PDPageContentStream? = null
        private var y: Float = 0f
        private var tocPages = 1

        fun pageCount(): Int {
            val lineHeight = 15.5f
            val linesPerPage = max(1, ((pageHeight - marginTop - marginBottom - 50f) / lineHeight).toInt())
            var lines = 0
            model.allChapters.forEach { chapter ->
                lines += wrap(chapter.headingText, fonts.regular, 10f, contentWidth - 70f).size
            }
            tocPages = max(1, ceil(lines.toDouble() / linesPerPage).toInt())
            return tocPages
        }

        fun cover(cover: ReportCover) {
            val page = PDPage(PDRectangle(pageWidth, pageHeight))
            pdf.addPage(page)
            val pageStream = PDPageContentStream(pdf, page)
            stream = pageStream
            y = pageHeight - 46f

            pageStream.setStrokingColor(accent)
            pageStream.setLineWidth(1.6f)
            pageStream.addRect(22f, 22f, pageWidth - 44f, pageHeight - 44f)
            pageStream.stroke()
            pageStream.setLineWidth(0.6f)
            pageStream.addRect(28f, 28f, pageWidth - 56f, pageHeight - 56f)
            pageStream.stroke()

            centered(cover.republicLine, 9.5f, true, accent)
            centered(cover.ministryLine, 8.5f, true, textColor)
            centered(cover.wilaya, 8.5f, false, muted)
            centered(cover.ctcLine, 8.5f, true, accent)
            separator()

            y -= 46f
            val bannerHeight = 96f
            pageStream.setNonStrokingColor(accent)
            pageStream.addRect(marginLeft, y - bannerHeight, contentWidth, bannerHeight)
            pageStream.fill()
            val titleSize = 21f
            val titleWidth = measure(cover.documentTitle, fonts.bold, titleSize)
            paint(
                pageStream,
                cover.documentTitle,
                marginLeft + (contentWidth - titleWidth) / 2f,
                y - 34f,
                titleSize,
                fonts.bold,
                Color.WHITE
            )
            var subtitleY = y - 54f
            wrap(cover.subtitle, fonts.regular, 8.5f, contentWidth - 24f).take(2).forEach { line ->
                val width = measure(line, fonts.regular, 8.5f)
                paint(
                    pageStream,
                    line,
                    marginLeft + (contentWidth - width) / 2f,
                    subtitleY,
                    8.5f,
                    fonts.regular,
                    accentSoft
                )
                subtitleY -= 11f
            }
            y -= bannerHeight + 26f

            cover.view3d?.let { png ->
                val image = runCatching { PDImageXObject.createFromByteArray(pdf, png, "couverture") }.getOrNull()
                if (image != null) {
                    val width = contentWidth * 0.60f
                    val height = width * image.height.toFloat() / max(1, image.width).toFloat()
                    val x = marginLeft + (contentWidth - width) / 2f
                    pageStream.drawImage(image, x, y - height, width, height)
                    y -= height + 10f
                    centered("Vue 3D isométrique du joint ${cover.jointName}", 7.5f, false, muted)
                }
            }

            y -= 8f
            drawCoverTable(
                listOf(
                    "Projet" to cover.projectTitle,
                    "Joint" to cover.jointName,
                    "Maître d'ouvrage" to cover.client,
                    "Bureau d'études" to cover.engineer,
                    "Référence" to cover.reference,
                    "Révision" to cover.revision,
                    "Date" to cover.dateLabel
                ),
                rowHeight = 14.5f
            )
            y -= 8f
            if (cover.summary.isNotEmpty()) {
                drawCoverTable(cover.summary, columns = 2, rowHeight = 14.5f)
            }

            y = 46f
            centered(
                "Document établi automatiquement — vérification et visa du CTC requis",
                7.5f,
                false,
                muted
            )
            pageStream.close()
            stream = null
        }

        private fun drawCoverTable(
            rows: List<Pair<String, String>>,
            columns: Int = 1,
            rowHeight: Float = 16f
        ) {
            val pageStream = stream ?: return
            val groupWidth = contentWidth / columns
            val labelWidth = groupWidth * if (columns == 1) 0.34f else 0.40f
            val valueWidth = groupWidth - labelWidth
            val fontSize = if (columns == 1) 8.0f else 7.3f
            rows.chunked(columns).forEach { chunk ->
                val texts = (0 until columns).map { index ->
                    chunk.getOrNull(index)?.let { pair ->
                        wrap(pair.second, fonts.regular, fontSize, valueWidth - 8f)
                    }.orEmpty()
                }
                val height = max(rowHeight, (texts.maxOfOrNull { it.size } ?: 1) * 8.2f + 4f)
                (0 until columns).forEach { index ->
                    val x0 = marginLeft + index * groupWidth
                    pageStream.setStrokingColor(grid)
                    pageStream.setLineWidth(0.4f)
                    pageStream.setNonStrokingColor(accentSoft)
                    pageStream.addRect(x0, y - height, labelWidth, height)
                    pageStream.fill()
                    pageStream.setNonStrokingColor(Color.WHITE)
                    pageStream.addRect(x0 + labelWidth, y - height, valueWidth, height)
                    pageStream.fill()
                    pageStream.addRect(x0, y - height, labelWidth, height)
                    pageStream.stroke()
                    pageStream.addRect(x0 + labelWidth, y - height, valueWidth, height)
                    pageStream.stroke()
                    val pair = chunk.getOrNull(index) ?: return@forEach
                    val labelLine = wrap(pair.first, fonts.bold, fontSize, labelWidth - 8f).firstOrNull() ?: ""
                    paint(pageStream, labelLine, x0 + 5f, y - height + 5f, fontSize, fonts.bold, textColor)
                    texts[index].forEachIndexed { lineIndex, line ->
                        val lineCount = texts[index].size
                        paint(
                            pageStream,
                            line,
                            x0 + labelWidth + 5f,
                            y - height + 5f + (lineCount - 1 - lineIndex) * 8.2f,
                            fontSize,
                            fonts.regular,
                            textColor
                        )
                    }
                }
                y -= height
            }
        }

        fun entries(chapters: List<ReportChapter>, startPages: Map<Int, Int>, offset: Int) {
            val lineHeight = 15.5f
            val linesPerPage = max(1, ((pageHeight - marginTop - marginBottom - 50f) / lineHeight).toInt())
            var index = 0
            repeat(tocPages) {
                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                pdf.addPage(page)
                val pageStream = PDPageContentStream(pdf, page)
                stream = pageStream
                y = pageHeight - marginTop
                drawHeader()
                y -= 26f
                paint(pageStream, "SOMMAIRE", marginLeft, y - 14f, 16f, fonts.bold, accent)
                pageStream.setStrokingColor(accent)
                pageStream.setLineWidth(1.2f)
                pageStream.moveTo(marginLeft, y - 20f)
                pageStream.lineTo(pageWidth - marginRight, y - 20f)
                pageStream.stroke()
                y -= 40f
                var linesOnPage = 0
                while (index < chapters.size && linesOnPage < linesPerPage) {
                    val chapter = chapters[index]
                    val lines = wrap(chapter.headingText, fonts.regular, 10f, contentWidth - 70f)
                    val firstLineY = y - 10f
                    lines.forEachIndexed { lineIndex, line ->
                        paint(
                            pageStream,
                            line,
                            marginLeft,
                            y - 10f,
                            10f,
                            if (lineIndex == 0) fonts.bold else fonts.regular,
                            textColor
                        )
                        y -= lineHeight
                        linesOnPage++
                    }
                    val pageNumber = (startPages[chapter.number] ?: 0) + offset + 1
                    val numberText = pageNumber.toString()
                    val numberWidth = measure(numberText, fonts.regular, 9f)
                    paint(
                        pageStream,
                        numberText,
                        pageWidth - marginRight - numberWidth,
                        firstLineY,
                        9f,
                        fonts.regular,
                        muted
                    )
                    val titleWidth = measure(lines.firstOrNull() ?: "", fonts.bold, 10f)
                    pageStream.setStrokingColor(grid)
                    pageStream.setLineWidth(0.4f)
                    pageStream.setLineDashPattern(floatArrayOf(1f, 2.2f), 0f)
                    pageStream.moveTo(marginLeft + titleWidth + 6f, firstLineY + 1f)
                    pageStream.lineTo(pageWidth - marginRight - numberWidth - 6f, firstLineY + 1f)
                    pageStream.stroke()
                    pageStream.setLineDashPattern(floatArrayOf(), 0f)
                    index++
                }
                pageStream.close()
                stream = null
            }
        }

        fun finish() {
            stream?.close()
            stream = null
        }

        private fun drawHeader() {
            val pageStream = stream ?: return
            paint(pageStream, model.cover.republicLine, marginLeft, pageHeight - 34f, 7.5f, fonts.bold, accent)
            val right = model.cover.reference
            val rightWidth = measure(right, fonts.regular, 7.5f)
            paint(pageStream, right, pageWidth - marginRight - rightWidth, pageHeight - 34f, 7.5f, fonts.regular, muted)
            pageStream.setStrokingColor(accentSoft)
            pageStream.setLineWidth(0.7f)
            pageStream.moveTo(marginLeft, pageHeight - 40f)
            pageStream.lineTo(pageWidth - marginRight, pageHeight - 40f)
            pageStream.stroke()
        }

        private fun separator() {
            val pageStream = stream ?: return
            pageStream.setStrokingColor(accent)
            pageStream.setLineWidth(1.0f)
            pageStream.moveTo(marginLeft, y)
            pageStream.lineTo(pageWidth - marginRight, y)
            pageStream.stroke()
            y -= 8f
        }

        private fun centered(text: String, size: Float, bold: Boolean, color: Color) {
            val pageStream = stream ?: return
            val font = if (bold) fonts.bold else fonts.regular
            wrap(text, font, size, contentWidth).forEach { line ->
                val width = measure(line, font, size)
                paint(pageStream, line, marginLeft + (contentWidth - width) / 2f, y - size, size, font, color)
                y -= size + 3f
            }
            y -= 2f
        }

        private fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
            val clean = sanitise(text).trim()
            if (clean.isEmpty()) return listOf("")
            val lines = mutableListOf<String>()
            val current = StringBuilder()
            clean.split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (measure(candidate, font, size) <= maxWidth || current.isEmpty()) {
                    current.setLength(0)
                    current.append(candidate)
                } else {
                    lines += current.toString()
                    current.setLength(0)
                    current.append(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines.ifEmpty { listOf("") }
        }
    }
}
