package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.geometry.CheckStatus
import org.apache.poi.common.usermodel.PictureType
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.Borders
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFStyle
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageBorderOffset
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Generateur natif de la note de calcul CTC au format Word (.docx).
 *
 * Le document produit comprend une page de garde encadree, un en-tete et un pied de page
 * recurrents avec pagination dynamique, un sommaire automatique (champ `TOC`) relie aux
 * titres 1 a 3 par des signets valides, des equations OMML natives et l'integralite des
 * tableaux et figures de la note.
 */
class CtcWordReportGenerator(
    private val bodyFont: String = "Calibri",
    private val accentHex: String = "1F3A5F",
    private val accentSoftHex: String = "DCE6F1",
    private val textHex: String = "1F2937",
    private val mutedHex: String = "4B5563"
) {

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
        XWPFDocument().use { word ->
            Writer(word, document).compose()
            word.write(output)
        }
    }

    /** Etat de composition d'un document Word. */
    private inner class Writer(
        private val word: XWPFDocument,
        private val model: ReportDocument
    ) {
        private var bookmarkId = 1
        private val usedBookmarks = linkedSetOf<String>()

        fun compose() {
            configurePage()
            configureStyles()
            configureHeaderAndFooter()
            cover()
            tableOfContents()
            model.allChapters.forEach { chapter -> chapter(chapter) }
            word.settings?.setUpdateFields()
        }

        // ------------------------------------------------------------ mise en page

        private fun configurePage() {
            val body = word.document.body
            val section = body.sectPr ?: body.addNewSectPr()
            section.addNewTitlePg()
            val pageSize = section.pgSz ?: section.addNewPgSz()
            pageSize.setW(BigInteger.valueOf(11_906))
            pageSize.setH(BigInteger.valueOf(16_838))
            val margins = section.pgMar ?: section.addNewPgMar()
            margins.setTop(BigInteger.valueOf(1_417))
            margins.setBottom(BigInteger.valueOf(1_417))
            margins.setLeft(BigInteger.valueOf(1_417))
            margins.setRight(BigInteger.valueOf(1_134))
            margins.setHeader(BigInteger.valueOf(709))
            margins.setFooter(BigInteger.valueOf(709))
            val borders = (if (section.isSetPgBorders) section.pgBorders else null)
                ?: section.addNewPgBorders()
            borders.setOffsetFrom(STPageBorderOffset.PAGE)
            pageBorder(borders.addNewTop())
            pageBorder(borders.addNewBottom())
            pageBorder(borders.addNewLeft())
            pageBorder(borders.addNewRight())
        }

        private fun pageBorder(border: CTPageBorder) {
            border.setVal(STBorder.SINGLE)
            border.setSz(BigInteger.valueOf(12))
            border.setSpace(BigInteger.valueOf(24))
            border.setColor(accentHex)
        }

        private fun configureStyles() {
            val styles = word.styles ?: word.createStyles()
            if (styles.styleExist("Heading1") && styles.styleExist("Heading2")) return
            if (!styles.styleExist("Normal")) {
                val normal = CTStyle.Factory.newInstance()
                normal.setType(STStyleType.PARAGRAPH)
                normal.setStyleId("Normal")
                normal.addNewName().setVal("Normal")
                normal.addNewQFormat()
                val run = normal.addNewRPr()
                run.addNewRFonts().apply {
                    setAscii(bodyFont)
                    setHAnsi(bodyFont)
                    setEastAsia(bodyFont)
                    setCs(bodyFont)
                }
                run.addNewSz().setVal(BigInteger.valueOf(22))
                run.addNewLang().setVal("fr-FR")
                styles.addStyle(XWPFStyle(normal))
            }
            listOf(
                Triple("Heading1", "heading 1", 32),
                Triple("Heading2", "heading 2", 26),
                Triple("Heading3", "heading 3", 24)
            ).forEach { (id, name, size) ->
                if (styles.styleExist(id)) return@forEach
                val style = CTStyle.Factory.newInstance()
                style.setType(STStyleType.PARAGRAPH)
                style.setStyleId(id)
                style.addNewName().setVal(name)
                style.addNewBasedOn().setVal("Normal")
                style.addNewNext().setVal("Normal")
                style.addNewQFormat()
                val paragraph = style.addNewPPr()
                paragraph.addNewKeepNext()
                paragraph.addNewKeepLines()
                paragraph.addNewOutlineLvl().setVal(
                    BigInteger.valueOf(
                        when (id) {
                            "Heading1" -> 0L
                            "Heading2" -> 1L
                            else -> 2L
                        }
                    )
                )
                paragraph.addNewSpacing().apply {
                    setBefore(BigInteger.valueOf(if (id == "Heading1") 240L else 180L))
                    setAfter(BigInteger.valueOf(120))
                }
                val run = style.addNewRPr()
                run.addNewRFonts().apply {
                    setAscii(bodyFont)
                    setHAnsi(bodyFont)
                }
                run.addNewB().setVal(true)
                run.addNewColor().setVal(if (id == "Heading1") accentHex else textHex)
                run.addNewSz().setVal(BigInteger.valueOf(size.toLong()))
                styles.addStyle(XWPFStyle(style))
            }
        }

        private fun configureHeaderAndFooter() {
            val policy = XWPFHeaderFooterPolicy(word)
            val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
            val firstLine = header.paragraphs.firstOrNull() ?: header.createParagraph()
            firstLine.setAlignment(ParagraphAlignment.LEFT)
            firstLine.setSpacingAfter(0)
            paragraphBorder(firstLine, bottom = true, color = accentHex, size = 6)
            val republic = firstLine.createRun()
            republic.setText(model.cover.republicLine)
            republic.setFontFamily(bodyFont)
            republic.setFontSize(8.0)
            republic.setBold(true)
            republic.setColor(accentHex)

            val secondLine = header.createParagraph()
            secondLine.setAlignment(ParagraphAlignment.RIGHT)
            secondLine.setSpacingAfter(40)
            val identification = secondLine.createRun()
            identification.setText(
                "${model.cover.projectTitle}  ·  Joint ${model.cover.jointName}  ·  ${model.cover.reference}"
            )
            identification.setFontFamily(bodyFont)
            identification.setFontSize(7.5)
            identification.setColor(mutedHex)

            val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
            val firstFooterLine = footer.paragraphs.firstOrNull() ?: footer.createParagraph()
            firstFooterLine.setAlignment(ParagraphAlignment.LEFT)
            firstFooterLine.setSpacingAfter(0)
            paragraphBorder(firstFooterLine, bottom = false, color = accentSoftHex, size = 6)
            val reference = firstFooterLine.createRun()
            reference.setText(
                "${model.cover.reference}  ·  Rév. ${model.cover.revision}  ·  ${model.cover.dateLabel}"
            )
            reference.setFontFamily(bodyFont)
            reference.setFontSize(7.5)
            reference.setColor(mutedHex)

            val secondFooterLine = footer.createParagraph()
            secondFooterLine.setAlignment(ParagraphAlignment.CENTER)
            secondFooterLine.setSpacingBefore(0)
            val pageLabel = secondFooterLine.createRun()
            pageLabel.setText("Page ")
            pageLabel.setFontFamily(bodyFont)
            pageLabel.setFontSize(8.0)
            pageLabel.setColor(mutedHex)
            appendField(secondFooterLine, "PAGE", "1")
            val of = secondFooterLine.createRun()
            of.setText(" / ")
            of.setFontFamily(bodyFont)
            of.setFontSize(8.0)
            of.setColor(mutedHex)
            appendField(secondFooterLine, "NUMPAGES", "1")
        }

        private fun appendField(paragraph: XWPFParagraph, instruction: String, placeholder: String) {
            paragraph.createRun().ctr.addNewFldChar().setFldCharType(STFldCharType.BEGIN)
            paragraph.createRun().ctr.addNewInstrText().setStringValue(" $instruction ")
            paragraph.createRun().ctr.addNewFldChar().setFldCharType(STFldCharType.SEPARATE)
            val display = paragraph.createRun()
            display.setText(placeholder)
            display.setFontFamily(bodyFont)
            display.setFontSize(8.0)
            display.setColor(mutedHex)
            paragraph.createRun().ctr.addNewFldChar().setFldCharType(STFldCharType.END)
        }

        // ------------------------------------------------------------ page de garde

        private fun cover() {
            val cover = model.cover
            paragraph(cover.republicLine, size = 10.0, bold = true, align = ParagraphAlignment.CENTER, color = accentHex, after = 2)
            paragraph(cover.ministryLine, size = 9.0, bold = true, align = ParagraphAlignment.CENTER, color = textHex, after = 2)
            paragraph(cover.wilaya, size = 9.0, align = ParagraphAlignment.CENTER, color = mutedHex, after = 8)
            paragraph(cover.ctcLine, size = 9.0, bold = true, align = ParagraphAlignment.CENTER, color = accentHex, after = 8)
            horizontalRule()

            spacer(80)
            val banner = word.createTable(1, 1)
            configureTableWidths(banner, listOf(17.0))
            val cell = banner.getRow(0).getCell(0)
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
            shade(cell, accentHex)
            cellMargins(cell, top = 200, bottom = 200)
            val titleParagraph = cell.paragraphs.first()
            titleParagraph.setAlignment(ParagraphAlignment.CENTER)
            titleParagraph.setSpacingAfter(40)
            val title = titleParagraph.createRun()
            title.setText(cover.documentTitle)
            title.setFontFamily(bodyFont)
            title.setFontSize(22.0)
            title.setBold(true)
            title.setColor("FFFFFF")
            val subtitleParagraph = cell.addParagraph()
            subtitleParagraph.setAlignment(ParagraphAlignment.CENTER)
            val subtitle = subtitleParagraph.createRun()
            subtitle.setText(cover.subtitle)
            subtitle.setFontFamily(bodyFont)
            subtitle.setFontSize(9.5)
            subtitle.setColor(accentSoftHex)
            cellBorder(banner, accentHex, 16)
            spacer(70)

            cover.view3d?.let { png ->
                image(png, 10.0, "Vue 3D isométrique du joint ${cover.jointName}", compact = true)
            }

            spacer(70)
            val identification = word.createTable(5, 4)
            configureTableWidths(identification, listOf(3.8, 4.7, 3.8, 4.7))
            val rows = listOf(
                listOf("Projet", cover.projectTitle, "Joint", cover.jointName),
                listOf("Maître d'ouvrage", cover.client, "Bureau d'études", cover.engineer),
                listOf("Référence", cover.reference, "Révision", cover.revision),
                listOf("Date", cover.dateLabel, "Contrôle", cover.ctcLine),
                listOf("Document", cover.documentTitle, "Bases", "RPA 2024 · BAEL 91 · CBA 93")
            )
            rows.forEachIndexed { rowIndex, values ->
                values.forEachIndexed { columnIndex, value ->
                    val target = identification.getRow(rowIndex).getCell(columnIndex)
                    val label = columnIndex % 2 == 0
                    fillCell(
                        target,
                        value,
                        bold = label,
                        size = 8.5,
                        shadeHex = if (label) accentSoftHex else null
                    )
                    cellMargins(target, top = 20, bottom = 20, left = 80, right = 80)
                }
            }
            cellBorder(identification, "9CA3AF", 4)

            if (cover.summary.isNotEmpty()) {
                spacer(60)
                val pairs = cover.summary.chunked(2)
                val summary = word.createTable(pairs.size, 4)
                configureTableWidths(summary, listOf(4.2, 4.6, 4.2, 4.0))
                pairs.forEachIndexed { index, pair ->
                    val row = summary.getRow(index)
                    pair.forEachIndexed { column, (label, value) ->
                        val labelCell = row.getCell(column * 2)
                        val valueCell = row.getCell(column * 2 + 1)
                        fillCell(labelCell, label, bold = true, size = 7.8, shadeHex = accentSoftHex)
                        fillCell(valueCell, value, size = 7.8)
                        cellMargins(labelCell, top = 20, bottom = 20, left = 70, right = 70)
                        cellMargins(valueCell, top = 20, bottom = 20, left = 70, right = 70)
                    }
                    if (pair.size == 1) {
                        fillCell(row.getCell(2), "", size = 7.8, shadeHex = accentSoftHex)
                        fillCell(row.getCell(3), "", size = 7.8)
                    }
                }
                cellBorder(summary, "9CA3AF", 4)
            }
            spacer(20)
            paragraph(
                "Document établi par calcul automatique à partir de l'export ETABS/SAFE. " +
                    "Les résultats restent soumis à la vérification du bureau d'études et au visa du CTC.",
                size = 8.0,
                italic = true,
                align = ParagraphAlignment.CENTER,
                color = mutedHex
            )
            pageBreak()
        }

        // ------------------------------------------------------------ sommaire

        private fun tableOfContents() {
            val heading = word.createParagraph()
            heading.setStyle("Normal")
            heading.setSpacingBefore(0)
            heading.setSpacingAfter(240)
            heading.setAlignment(ParagraphAlignment.LEFT)
            val run = heading.createRun()
            run.setText("SOMMAIRE")
            run.setFontFamily(bodyFont)
            run.setFontSize(16.0)
            run.setBold(true)
            run.setColor(accentHex)

            val instruction = word.createParagraph()
            instruction.setSpacingAfter(120)
            instruction.createRun().ctr.addNewFldChar().setFldCharType(STFldCharType.BEGIN)
            instruction.createRun().ctr.addNewInstrText()
                .setStringValue(" TOC \\o \"1-3\" \\h \\z \\u ")
            instruction.createRun().ctr.addNewFldChar().setFldCharType(STFldCharType.SEPARATE)
            val placeholder = instruction.createRun()
            placeholder.setText("Sommaire automatique — appuyer sur F9 pour actualiser la pagination.")
            placeholder.setFontFamily(bodyFont)
            placeholder.setFontSize(9.0)
            placeholder.setItalic(true)
            placeholder.setColor(mutedHex)
            instruction.createRun().ctr.addNewFldChar().setFldCharType(STFldCharType.END)

            spacer(120)
            paragraph(
                "Le sommaire est un champ Word automatique : un clic droit puis « Mettre à jour les champs » " +
                    "(ou F9) régénère la table des matières et les numéros de page.",
                size = 8.5,
                italic = true,
                color = mutedHex
            )
            pageBreak()
        }

        // ------------------------------------------------------------ chapitres

        private fun chapter(chapter: ReportChapter) {
            val heading = word.createParagraph()
            heading.setStyle("Heading1")
            heading.setSpacingBefore(0)
            heading.setSpacingAfter(160)
            heading.setKeepNext(true)
            val bookmarkName = uniqueBookmark(chapter.bookmark)
            addBookmarkStart(heading, bookmarkName)
            heading.ctpPr.addNewOutlineLvl().setVal(BigInteger.ZERO)
            val run = heading.createRun()
            run.setText(chapter.headingText)
            run.setFontFamily(bodyFont)
            run.setFontSize(16.0)
            run.setBold(true)
            run.setColor(accentHex)
            addBookmarkEnd(heading)
            paragraph(
                chapter.summary + "  Statut du chapitre : " + ReportText.statusLabel(chapter.status) + ".",
                size = 9.0,
                italic = true,
                color = mutedHex,
                after = 140
            )
            chapter.blocks.forEach { block -> block(block) }
            pageBreak()
        }

        private fun block(block: ReportBlock) {
            when (block) {
                is ReportBlock.Heading -> heading(block)
                is ReportBlock.Paragraph -> paragraph(
                    block.text,
                    size = block.sizePt.toDouble(),
                    bold = block.bold,
                    italic = block.italic,
                    align = alignment(block.align),
                    color = block.colorHex ?: textHex,
                    before = block.spaceBeforePt,
                    after = block.spaceAfterPt,
                    keepWithNext = block.keepWithNext
                )
                is ReportBlock.Bullets -> block.items.forEach { item -> listItem(item, "\u2022", 340) }
                is ReportBlock.Numbered -> block.items.forEachIndexed { index, item ->
                    listItem(item, "${index + 1}.", 340)
                }
                is ReportBlock.Equation -> equation(block)
                is ReportBlock.Table -> table(block)
                is ReportBlock.Figure -> image(block.png, block.widthCm, block.caption)
                is ReportBlock.Note -> note(block)
                is ReportBlock.Verdict -> verdict(block)
                is ReportBlock.ChecksTable -> checksTable(block)
                is ReportBlock.KeyValues -> keyValues(block)
                ReportBlock.PageBreak -> pageBreak()
            }
        }

        private fun heading(block: ReportBlock.Heading) {
            val level = block.level.coerceIn(1, 3)
            val paragraph = word.createParagraph()
            paragraph.setStyle("Heading$level")
            paragraph.setSpacingBefore(if (level == 1) 200 else 160)
            paragraph.setSpacingAfter(90)
            paragraph.setKeepNext(true)
            val name = uniqueBookmark(block.bookmark)
            addBookmarkStart(paragraph, name)
            paragraph.ctpPr.addNewOutlineLvl().setVal(BigInteger.valueOf((level - 1).toLong()))
            val run = paragraph.createRun()
            run.setText(block.text)
            run.setFontFamily(bodyFont)
            run.setFontSize(
                when (level) {
                    1 -> 15.0
                    2 -> 12.5
                    else -> 11.5
                }
            )
            run.setBold(true)
            run.setColor(if (level == 1) accentHex else textHex)
            addBookmarkEnd(paragraph)
        }

        private fun equation(block: ReportBlock.Equation) {
            val paragraph = word.createParagraph()
            paragraph.setAlignment(ParagraphAlignment.CENTER)
            paragraph.setSpacingBefore(100)
            paragraph.setSpacingAfter(40)
            OmmlEquationBuilder.appendDisplay(paragraph, block.nodes)
            block.caption?.let {
                paragraph(
                    it,
                    size = 8.5,
                    italic = true,
                    align = ParagraphAlignment.CENTER,
                    color = mutedHex,
                    after = 120
                )
            }
        }

        private fun table(block: ReportBlock.Table) {
            block.caption?.let {
                paragraph(
                    it,
                    size = 9.0,
                    bold = true,
                    color = accentHex,
                    before = 120,
                    after = 40,
                    keepWithNext = true
                )
            }
            if (block.headers.isEmpty()) return
            val columns = block.headers.size
            val widths = normaliseWidths(block.columnWidthsCm, columns)
            val table = word.createTable(block.rows.size + 1, columns)
            configureTableWidths(table, widths)
            table.setRowBandSize(1)
            table.setTableAlignment(TableRowAlign.CENTER)
            tableBorders(table, "C7D2E0", 4)

            val headerRow = table.getRow(0)
            headerRow.setRepeatHeader(true)
            block.headers.forEachIndexed { index, text ->
                val cell = headerRow.getCell(index)
                shade(cell, block.headerFillHex)
                fillCell(
                    cell,
                    text,
                    bold = true,
                    colorHex = block.headerTextHex,
                    size = block.fontSizePt.toDouble(),
                    align = ParagraphAlignment.CENTER
                )
            }
            block.rows.forEachIndexed { rowIndex, values ->
                val row = table.getRow(rowIndex + 1)
                values.forEachIndexed { columnIndex, value ->
                    if (columnIndex >= columns) return@forEachIndexed
                    val cell = row.getCell(columnIndex)
                    if (block.zebra && rowIndex % 2 == 1) shade(cell, "F3F6FA")
                    val align = block.alignments.getOrNull(columnIndex) ?: ReportAlign.LEFT
                    fillCell(
                        cell,
                        value,
                        bold = block.firstColumnBold && columnIndex == 0,
                        size = block.fontSizePt.toDouble(),
                        align = alignment(align)
                    )
                }
            }
            spacer(100)
        }

        private fun checksTable(block: ReportBlock.ChecksTable) {
            val caption = block.caption ?: "Contrôles réglementaires"
            paragraph(
                "$caption (${block.checks.size} contrôle(s))",
                size = 9.0,
                bold = true,
                color = accentHex,
                before = 120,
                after = 40,
                keepWithNext = true
            )
            if (block.checks.isEmpty()) {
                paragraph("Aucun contrôle n'est applicable.", size = 9.0, italic = true, color = mutedHex)
                return
            }
            val table = word.createTable(block.checks.size + 1, 6)
            configureTableWidths(table, listOf(2.4, 5.2, 3.4, 2.4, 2.4, 1.8))
            tableBorders(table, "C7D2E0", 4)
            table.getRow(0).setRepeatHeader(true)
            listOf("Code", "Contrôle", "Référence", "Exigence", "Fourni", "État").forEachIndexed { index, title ->
                val cell = table.getRow(0).getCell(index)
                shade(cell, accentHex)
                fillCell(cell, title, bold = true, colorHex = "FFFFFF", size = 8.0, align = ParagraphAlignment.CENTER)
            }
            block.checks.forEachIndexed { index, check ->
                val row = table.getRow(index + 1)
                val values = listOf(
                    check.code,
                    check.label,
                    check.reference,
                    check.required,
                    check.provided,
                    ReportText.statusLabel(check.status)
                )
                values.forEachIndexed { columnIndex, value ->
                    val cell = row.getCell(columnIndex)
                    if (index % 2 == 1) shade(cell, "F3F6FA")
                    if (columnIndex == values.lastIndex) {
                        fillCell(
                            cell,
                            value,
                            bold = true,
                            colorHex = ReportText.statusColor(check.status),
                            size = 8.0,
                            align = ParagraphAlignment.CENTER
                        )
                    } else {
                        fillCell(
                            cell,
                            value,
                            size = 8.0,
                            align = if (columnIndex == 0) ParagraphAlignment.CENTER else ParagraphAlignment.LEFT
                        )
                    }
                }
            }
            spacer(100)
        }

        private fun keyValues(block: ReportBlock.KeyValues) {
            block.caption?.let {
                paragraph(
                    it,
                    size = 9.0,
                    bold = true,
                    color = accentHex,
                    before = 120,
                    after = 40,
                    keepWithNext = true
                )
            }
            if (block.entries.isEmpty()) return
            val table = word.createTable(block.entries.size, 2)
            configureTableWidths(table, listOf(7.0, 10.0))
            tableBorders(table, "C7D2E0", 4)
            block.entries.forEachIndexed { index, (label, value) ->
                val row = table.getRow(index)
                fillCell(row.getCell(0), label, bold = true, size = 9.0, shadeHex = "EEF3F9")
                fillCell(row.getCell(1), value, size = 9.0)
            }
            spacer(100)
        }

        private fun note(block: ReportBlock.Note) {
            val table = word.createTable(1, 1)
            configureTableWidths(table, listOf(17.0))
            val cell = table.getRow(0).getCell(0)
            shade(cell, "FFF7E6")
            cellMargins(cell, top = 100, bottom = 100)
            val paragraph = cell.paragraphs.first()
            val title = paragraph.createRun()
            title.setText("${block.title} — ")
            title.setFontFamily(bodyFont)
            title.setFontSize(9.0)
            title.setBold(true)
            title.setColor("92400E")
            val text = paragraph.createRun()
            text.setText(block.text)
            text.setFontFamily(bodyFont)
            text.setFontSize(9.0)
            text.setColor("78350F")
            cellBorder(table, "F59E0B", 6)
            spacer(100)
        }

        private fun verdict(block: ReportBlock.Verdict) {
            val table = word.createTable(1, 1)
            configureTableWidths(table, listOf(17.0))
            val cell = table.getRow(0).getCell(0)
            val color = ReportText.statusColor(block.status)
            shade(cell, softStatusColor(block.status))
            cellMargins(cell, top = 120, bottom = 120)
            val paragraph = cell.paragraphs.first()
            val label = paragraph.createRun()
            label.setText("${ReportText.statusLabel(block.status).uppercase()} — ")
            label.setFontFamily(bodyFont)
            label.setFontSize(9.5)
            label.setBold(true)
            label.setColor(color)
            val text = paragraph.createRun()
            text.setText(block.text)
            text.setFontFamily(bodyFont)
            text.setFontSize(9.5)
            text.setColor(textHex)
            cellBorder(table, color, 8)
            spacer(100)
        }

        private fun softStatusColor(status: CheckStatus): String = when (status) {
            CheckStatus.OK -> "ECFDF3"
            CheckStatus.WARNING -> "FFF7E6"
            CheckStatus.FAILED -> "FDECEC"
            CheckStatus.NOT_APPLICABLE -> "F3F4F6"
        }

        // ------------------------------------------------------------ primitives

        private fun paragraph(
            text: String,
            size: Double = 10.0,
            bold: Boolean = false,
            italic: Boolean = false,
            align: ParagraphAlignment = ParagraphAlignment.BOTH,
            color: String = textHex,
            before: Int = 0,
            after: Int = 80,
            keepWithNext: Boolean = false
        ): XWPFParagraph {
            val paragraph = word.createParagraph()
            paragraph.setAlignment(align)
            paragraph.setSpacingBefore(before)
            paragraph.setSpacingAfter(after)
            paragraph.setKeepNext(keepWithNext)
            val run = paragraph.createRun()
            run.setFontFamily(bodyFont)
            run.setFontSize(size)
            run.setBold(bold)
            run.setItalic(italic)
            run.setColor(color)
            run.setText(text.replace("\n", " "))
            return paragraph
        }

        private fun listItem(text: String, marker: String, indent: Int) {
            val paragraph = word.createParagraph()
            paragraph.setAlignment(ParagraphAlignment.BOTH)
            paragraph.setSpacingAfter(40)
            paragraph.setIndentationLeft(indent)
            paragraph.setIndentationHanging(260)
            val run = paragraph.createRun()
            run.setFontFamily(bodyFont)
            run.setFontSize(10.0)
            run.setColor(textHex)
            run.setText("$marker  $text")
        }

        private fun spacer(twips: Int) {
            val paragraph = word.createParagraph()
            paragraph.setSpacingBefore(0)
            paragraph.setSpacingAfter(twips)
            paragraph.setSpacingBetween(1.0)
            paragraph.createRun().setFontSize(1.0)
        }

        private fun paragraphBorder(paragraph: XWPFParagraph, bottom: Boolean, color: String, size: Int) {
            val properties = paragraph.ctpPr
            val borders = if (properties.isSetPBdr) properties.pBdr else properties.addNewPBdr()
            val border = if (bottom) {
                if (borders.isSetBottom) borders.bottom else borders.addNewBottom()
            } else {
                if (borders.isSetTop) borders.top else borders.addNewTop()
            }
            border.setVal(STBorder.SINGLE)
            border.setSz(BigInteger.valueOf(size.toLong()))
            border.setSpace(BigInteger.ZERO)
            border.setColor(color)
        }

        private fun horizontalRule() {
            val paragraph = word.createParagraph()
            paragraph.setSpacingBefore(40)
            paragraph.setSpacingAfter(60)
            paragraphBorder(paragraph, bottom = true, color = accentHex, size = 12)
        }

        private fun pageBreak() {
            word.createParagraph().setPageBreak(true)
        }

        private fun image(png: ByteArray, widthCm: Double, caption: String, compact: Boolean = false) {
            val dimensions = pngDimensions(png) ?: return
            val widthPoints = widthCm * 28.3465
            val heightPoints = widthPoints * dimensions.second.toDouble() / dimensions.first.toDouble()
            val paragraph = word.createParagraph()
            paragraph.setAlignment(ParagraphAlignment.CENTER)
            paragraph.setSpacingBefore(if (compact) 0 else 100)
            paragraph.setSpacingAfter(if (compact) 0 else 20)
            paragraph.setKeepNext(true)
            paragraph.createRun().addPicture(
                ByteArrayInputStream(png),
                PictureType.PNG,
                "figure.png",
                Units.toEMU(widthPoints),
                Units.toEMU(heightPoints)
            )
            paragraph(
                caption,
                size = if (compact) 8.0 else 8.5,
                italic = true,
                align = ParagraphAlignment.CENTER,
                color = mutedHex,
                after = if (compact) 60 else 140
            )
        }

        private fun pngDimensions(png: ByteArray): Pair<Int, Int>? {
            if (png.isEmpty()) return null
            val image: BufferedImage = runCatching { ImageIO.read(ByteArrayInputStream(png)) }.getOrNull() ?: return null
            if (image.width <= 0 || image.height <= 0) return null
            return image.width to image.height
        }

        private fun alignment(align: ReportAlign): ParagraphAlignment = when (align) {
            ReportAlign.LEFT -> ParagraphAlignment.LEFT
            ReportAlign.CENTER -> ParagraphAlignment.CENTER
            ReportAlign.RIGHT -> ParagraphAlignment.RIGHT
            ReportAlign.JUSTIFY -> ParagraphAlignment.BOTH
        }

        private fun normaliseWidths(widths: List<Double>?, columns: Int): List<Double> {
            val source = if (widths == null || widths.size != columns || widths.any { it <= 0.0 }) {
                List(columns) { 17.0 / columns }
            } else {
                widths
            }
            val factor = 17.0 / source.sum()
            return source.map { it * factor }
        }

        private fun configureTableWidths(table: XWPFTable, widthsCm: List<Double>) {
            table.setWidth("100%")
            table.setCellMargins(40, 80, 40, 80)
            val properties = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
            if (!properties.isSetTblLayout) {
                properties.addNewTblLayout().setType(STTblLayoutType.FIXED)
            }
            widthsCm.forEachIndexed { index, width ->
                val twips = (width * 567.0).toInt().toString()
                table.rows.forEach { row -> row.tableCells.getOrNull(index)?.setWidth(twips) }
            }
        }

        private fun tableBorders(table: XWPFTable, color: String, size: Int) {
            val properties = table.ctTbl.tblPr ?: table.ctTbl.addNewTblPr()
            val borders = properties.addNewTblBorders()
            listOf(
                borders.addNewTop(),
                borders.addNewBottom(),
                borders.addNewLeft(),
                borders.addNewRight(),
                borders.addNewInsideH(),
                borders.addNewInsideV()
            ).forEach { border ->
                border.setVal(STBorder.SINGLE)
                border.setSz(BigInteger.valueOf(size.toLong()))
                border.setSpace(BigInteger.ZERO)
                border.setColor(color)
            }
        }

        private fun cellBorder(table: XWPFTable, color: String, size: Int) {
            table.rows.forEach { row ->
                row.tableCells.forEach { cell ->
                    val properties = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
                    val borders = properties.addNewTcBorders()
                    listOf(
                        borders.addNewTop(),
                        borders.addNewBottom(),
                        borders.addNewLeft(),
                        borders.addNewRight()
                    ).forEach { border ->
                        border.setVal(STBorder.SINGLE)
                        border.setSz(BigInteger.valueOf(size.toLong()))
                        border.setColor(color)
                    }
                }
            }
        }

        private fun cellMargins(cell: XWPFTableCell, top: Int, bottom: Int, left: Int = 120, right: Int = 120) {
            val properties = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
            val margins = properties.addNewTcMar()
            margins.addNewTop().setW(BigInteger.valueOf(top.toLong()))
            margins.addNewBottom().setW(BigInteger.valueOf(bottom.toLong()))
            margins.addNewLeft().setW(BigInteger.valueOf(left.toLong()))
            margins.addNewRight().setW(BigInteger.valueOf(right.toLong()))
        }

        private fun shade(cell: XWPFTableCell, hex: String) {
            val properties = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
            val shading = properties.addNewShd()
            shading.setVal(STShd.CLEAR)
            shading.setFill(hex)
            shading.setColor("auto")
        }

        private fun fillCell(
            cell: XWPFTableCell,
            text: String,
            bold: Boolean = false,
            italic: Boolean = false,
            colorHex: String = textHex,
            shadeHex: String? = null,
            size: Double = 9.0,
            align: ParagraphAlignment = ParagraphAlignment.LEFT
        ) {
            shadeHex?.let { shade(cell, it) }
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER)
            val paragraph = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
            paragraph.setAlignment(align)
            paragraph.setSpacingBefore(0)
            paragraph.setSpacingAfter(0)
            val run = paragraph.createRun()
            run.setFontFamily(bodyFont)
            run.setFontSize(size)
            run.setBold(bold)
            run.setItalic(italic)
            run.setColor(colorHex)
            run.setText(text)
        }

        // ------------------------------------------------------------ signets

        private fun uniqueBookmark(name: String): String {
            val trimmed = name.trim().ifBlank { "signet" }.replace(Regex("[^A-Za-z0-9_]"), "_")
            val candidate = trimmed.take(38)
            var value = candidate
            var suffix = 1
            while (!usedBookmarks.add(value)) {
                value = "${candidate.take(35)}_${suffix++}"
            }
            return value
        }

        private fun addBookmarkStart(paragraph: XWPFParagraph, name: String) {
            val start = paragraph.ctp.addNewBookmarkStart()
            start.setId(BigInteger.valueOf(bookmarkId.toLong()))
            start.setName(name)
            bookmarkId++
        }

        private fun addBookmarkEnd(paragraph: XWPFParagraph) {
            paragraph.ctp.addNewBookmarkEnd().setId(BigInteger.valueOf((bookmarkId - 1).toLong()))
        }
    }
}
