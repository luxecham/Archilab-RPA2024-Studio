package com.archilab.rpa2024.report

import org.apache.poi.xwpf.usermodel.*

object CtcStyleCatalog {

    const val COLOR_PRIMARY_NAVY = "1B365D"
    const val COLOR_SUCCESS_GREEN = "1E7E34"
    const val COLOR_TEXT_DARK = "2B2B2B"
    const val COLOR_HEADER_BG = "EBF1F5"
    const val COLOR_BORDER_LIGHT = "D0D7DE"
    const val FONT_FAMILY = "Calibri"

    fun styleHeading1(paragraph: XWPFParagraph, title: String) {
        paragraph.style = "Heading1"
        paragraph.spacingBefore = 360
        paragraph.spacingAfter = 180
        val run = paragraph.createRun()
        run.fontFamily = FONT_FAMILY
        run.fontSize = 15
        run.isBold = true
        run.color = COLOR_PRIMARY_NAVY
        run.setText(title)
    }

    fun styleHeading2(paragraph: XWPFParagraph, title: String) {
        paragraph.style = "Heading2"
        paragraph.spacingBefore = 240
        paragraph.spacingAfter = 120
        val run = paragraph.createRun()
        run.fontFamily = FONT_FAMILY
        run.fontSize = 13
        run.isBold = true
        run.color = COLOR_PRIMARY_NAVY
        run.setText(title)
    }

    fun addParagraphText(paragraph: XWPFParagraph, text: String, isItalic: Boolean = false) {
        paragraph.spacingAfter = 100
        val run = paragraph.createRun()
        run.fontFamily = FONT_FAMILY
        run.fontSize = 11
        run.isItalic = isItalic
        run.color = COLOR_TEXT_DARK
        run.setText(text)
    }

    fun addVerificationBadge(paragraph: XWPFParagraph, checkDescription: String, isCompliant: Boolean) {
        paragraph.spacingBefore = 100
        paragraph.spacingAfter = 140
        val rText = paragraph.createRun()
        rText.fontFamily = FONT_FAMILY
        rText.fontSize = 11
        rText.setText("$checkDescription : ")

        val rBadge = paragraph.createRun()
        rBadge.fontFamily = FONT_FAMILY
        rBadge.fontSize = 11
        rBadge.isBold = true
        if (isCompliant) {
            rBadge.color = COLOR_SUCCESS_GREEN
            rBadge.setText("[ Condition Vérifiée (CV) ]")
        } else {
            rBadge.color = "D9381E"
            rBadge.setText("[ NON VÉRIFIÉE / CRITIQUE ]")
        }
    }

    fun formatTable(table: XWPFTable, colWidths: List<Int>) {
        table.width = 9500 // dxas
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, COLOR_BORDER_LIGHT)
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, COLOR_BORDER_LIGHT)
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, null)
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, null)
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, COLOR_BORDER_LIGHT)
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, null)

        for (row in table.rows) {
            for (i in row.tableCells.indices) {
                val cell = row.tableCells[i]
                val width = if (i < colWidths.size) colWidths[i] else 2000
                cell.setWidth(width.toString())
                cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
            }
        }
    }

    fun formatHeaderRow(row: XWPFTableRow) {
        for (cell in row.tableCells) {
            cell.setColor(COLOR_HEADER_BG)
            val p = if (cell.paragraphs.isNotEmpty()) cell.paragraphs[0] else cell.addParagraph()
            p.alignment = ParagraphAlignment.CENTER
            p.spacingBefore = 80
            p.spacingAfter = 80
            for (r in p.runs) {
                r.fontFamily = FONT_FAMILY
                r.fontSize = 10
                r.isBold = true
                r.color = COLOR_PRIMARY_NAVY
            }
        }
    }

    fun formatDataRow(row: XWPFTableRow) {
        for (cell in row.tableCells) {
            val p = if (cell.paragraphs.isNotEmpty()) cell.paragraphs[0] else cell.addParagraph()
            p.spacingBefore = 60
            p.spacingAfter = 60
            for (r in p.runs) {
                r.fontFamily = FONT_FAMILY
                r.fontSize = 9
                r.color = COLOR_TEXT_DARK
            }
        }
    }
}
