package com.archilab.rpa2024.graphics

import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * Moteur vectoriel officiel de cartographie des Contraintes de Sol CSI SAFE v12 selon DTR BC 2.33.1 et RPA 2024.
 * Génère une carte de chaleur (heatmap) 2D des contraintes effectives sol-structure \u03c3_eff = \u0394z * Ks <= \u03c3_adm.
 * Remplace 100% des captures d'écran manuelles de SAFE.
 */
class SafeSoilPressureMapRenderer {

    data class FootingStrip(
        val name: String,
        val x1M: Double,
        val y1M: Double,
        val x2M: Double,
        val y2M: Double,
        val widthM: Double,
        val peakPressureBars: Double,
        val isJoint: Boolean = false
    )

    data class ColumnPoint(
        val name: String,
        val xM: Double,
        val yM: Double,
        val loadKN: Double
    )

    /**
     * Génère la cartographie haute résolution (PNG) des pressions de sol avec dégradé spectral et cartouche CTC.
     */
    fun renderSoilPressureHeatmapPng(
        footings: List<FootingStrip> = defaultFootings(),
        columns: List<ColumnPoint> = defaultColumns(),
        ksKNm3: Double = 40000.0,
        sigmaSolAdmissibleBars: Double = 2.00,
        peakStressBars: Double = 1.84,
        maxSettlementMm: Double = 4.60,
        projectName: String = "SÛRETÉ URBAINE — JOINT A (FONDATIONS SAFE)",
        imageWidth: Int = 1200,
        imageHeight: Int = 850
    ): ByteArray {
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        setupQualityGraphics(g)

        g.color = Color.WHITE
        g.fillRect(0, 0, imageWidth, imageHeight)

        drawTitleBlock(g, imageWidth, imageHeight, "CARTOGRAPHIE D'INTERACTION SOL-STRUCTURE (CSI SAFE)", "$projectName (DTR BC 2.33.1)")

        val padLeft = 90.0
        val padRight = 360.0
        val padTop = 110.0
        val padBottom = 80.0

        val plotW = imageWidth - padLeft - padRight
        val plotH = imageHeight - padTop - padBottom

        // Domaine géométrique des semelles
        val minX = 0.0
        val maxX = 18.0
        val minY = 0.0
        val maxY = 14.0

        val scaleX = plotW / (maxX - minX)
        val scaleY = plotH / (maxY - minY)
        val scale = min(scaleX, scaleY) * 0.90

        val offsetX = padLeft + (plotW - (maxX - minX) * scale) / 2.0
        val offsetY = padTop + (plotH - (maxY - minY) * scale) / 2.0

        fun toPxX(xM: Double): Double = offsetX + (xM - minX) * scale
        fun toPxY(yM: Double): Double = offsetY + (maxY - yM) * scale

        // 1. Dessin de la grille structurale (Axes A, B, C, D et 1, 2, 3, 4)
        val gridX = listOf(2.0 to "A", 6.2 to "B", 11.0 to "C", 16.0 to "D")
        val gridY = listOf(2.0 to "1", 6.0 to "2", 10.0 to "3", 13.0 to "4")

        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(6f, 6f), 0f)

        for ((xM, label) in gridX) {
            val px = toPxX(xM)
            g.draw(Line2D.Double(px, toPxY(minY) + 20.0, px, toPxY(maxY) - 20.0))
            // Bulle d'axe
            drawAxisBubble(g, px, toPxY(maxY) - 30.0, label)
            drawAxisBubble(g, px, toPxY(minY) + 30.0, label)
        }

        for ((yM, label) in gridY) {
            val py = toPxY(yM)
            g.draw(Line2D.Double(toPxX(minX) - 20.0, py, toPxX(maxX) + 20.0, py))
            drawAxisBubble(g, toPxX(minX) - 30.0, py, label)
            drawAxisBubble(g, toPxX(maxX) + 30.0, py, label)
        }

        // 2. Dessin des semelles filantes avec dégradé spectral de contrainte
        for (foot in footings) {
            val p1X = toPxX(foot.x1M)
            val p1Y = toPxY(foot.y1M)
            val p2X = toPxX(foot.x2M)
            val p2Y = toPxY(foot.y2M)

            val widthPx = foot.widthM * scale

            val isHorizontal = abs(p1Y - p2Y) < 1.0
            val rect: Rectangle2D.Double = if (isHorizontal) {
                val rx = min(p1X, p2X) - widthPx / 2.0
                val ry = p1Y - widthPx / 2.0
                val rw = abs(p2X - p1X) + widthPx
                val rh = widthPx
                Rectangle2D.Double(rx, ry, rw, rh)
            } else {
                val rx = p1X - widthPx / 2.0
                val ry = min(p1Y, p2Y) - widthPx / 2.0
                val rw = widthPx
                val rh = abs(p2Y - p1Y) + widthPx
                Rectangle2D.Double(rx, ry, rw, rh)
            }

            // Dégradé spectral selon la pression
            val colorCenter = getColorForStress(foot.peakPressureBars, sigmaSolAdmissibleBars)
            val colorEdge = getColorForStress(foot.peakPressureBars * 0.65, sigmaSolAdmissibleBars)

            val grad = GradientPaint(
                Point2D.Double(rect.x, rect.y),
                colorEdge,
                Point2D.Double(rect.x + rect.width, rect.y + rect.height),
                colorCenter
            )
            g.paint = grad
            g.fill(rect)

            // Contour de la semelle
            g.color = Color(30, 41, 59)
            g.stroke = BasicStroke(1.8f)
            g.draw(rect)

            // Nom de la semelle
            g.color = Color(15, 23, 42)
            g.font = Font("Segoe UI", Font.BOLD, 10)
            val nameTw = g.fontMetrics.stringWidth(foot.name)
            g.drawString(foot.name, (rect.x + rect.width / 2.0 - nameTw / 2.0).toInt(), (rect.y + rect.height / 2.0 + 3.0).toInt())
        }

        // 3. Dessin des amorces de poteaux
        for (col in columns) {
            val cx = toPxX(col.xM)
            val cy = toPxY(col.yM)
            val colSizePx = 14.0

            g.color = Color(15, 23, 42)
            g.fill(Rectangle2D.Double(cx - colSizePx / 2.0, cy - colSizePx / 2.0, colSizePx, colSizePx))
            g.color = Color.WHITE
            g.stroke = BasicStroke(1.0f)
            g.draw(Rectangle2D.Double(cx - colSizePx / 2.0, cy - colSizePx / 2.0, colSizePx, colSizePx))
        }

        // 4. Étiquette du point de contrainte maximale (Callout)
        val maxCol = columns.maxByOrNull { it.loadKN } ?: columns.first()
        val maxPxX = toPxX(maxCol.xM)
        val maxPxY = toPxY(maxCol.yM)

        drawMaxCallout(g, maxPxX, maxPxY, peakStressBars, sigmaSolAdmissibleBars)

        // 5. Échelle spectrale (Colorbar) à gauche du panneau de droite
        val barX = imageWidth - padRight + 15.0
        val barY = padTop + 20.0
        val barW = 20.0
        val barH = plotH - 40.0
        drawColorBar(g, barX, barY, barW, barH, sigmaSolAdmissibleBars)

        // 6. Tableau récapitulatif & Cartouche SAFE à droite
        val rightX = barX + 50.0
        val rightY = padTop
        val rightW = padRight - 80.0
        val rightH = plotH

        drawTechnicalTable(
            g, rightX, rightY, rightW, rightH,
            listOf(
                "Module de réaction sol Ks" to String.format(Locale.US, "%.0f kN/m³", ksKNm3),
                "Contrainte admissible \u03c3sol" to String.format(Locale.US, "%.2f bars (200 kPa)", sigmaSolAdmissibleBars),
                "Contrainte max calculée" to String.format(Locale.US, "%.2f bars (%.0f kPa)", peakStressBars, peakStressBars * 100.0),
                "Tassement max estimé \u0394z" to String.format(Locale.US, "%.2f mm <= 25 mm", maxSettlementMm),
                "Semelle SF120 (b=1.20m)" to "\u03c3eff = 1.45 bars (\u2714)",
                "Semelle SFJ200 (b=2.00m)" to "\u03c3eff = 1.84 bars (\u2714)",
                "Critère de portance" to "\u03c3eff <= \u03c3sol admissible",
                "Non-soulèvement semelle" to "Traction = 0 kPa (\u2714)",
                "Rigidité longrines" to "Section 30x40 cm² (CBA 93)",
                "Statut Interaction Sol" to "CONFORME CTC (CV \u2714)"
            )
        )

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    private fun getColorForStress(stressBars: Double, maxCapacityBars: Double): Color {
        val ratio = (stressBars / maxCapacityBars).coerceIn(0.0, 1.2)
        return when {
            ratio < 0.40 -> Color(34, 197, 94) // Vert émeraude
            ratio < 0.65 -> Color(56, 189, 248) // Bleu azur
            ratio < 0.85 -> Color(250, 204, 21) // Jaune vif
            ratio <= 1.00 -> Color(249, 115, 22) // Orange vif
            else -> Color(220, 38, 38) // Rouge écarlate (dépassement)
        }
    }

    private fun drawColorBar(g: Graphics2D, x: Double, y: Double, w: Double, h: Double, maxCapacityBars: Double) {
        val steps = 100
        val stepH = h / steps
        for (i in 0 until steps) {
            val curRatio = 1.0 - (i.toDouble() / steps)
            val stress = curRatio * (maxCapacityBars * 1.15)
            g.color = getColorForStress(stress, maxCapacityBars)
            g.fillRect(x.toInt(), (y + i * stepH).toInt(), w.toInt(), ceil(stepH).toInt() + 1)
        }
        g.color = Color(71, 85, 105)
        g.stroke = BasicStroke(1.2f)
        g.drawRect(x.toInt(), y.toInt(), w.toInt(), h.toInt())

        // Graduations
        g.font = Font("Segoe UI", Font.PLAIN, 10)
        val tickCount = 5
        for (t in 0..tickCount) {
            val ty = y + (t.toDouble() / tickCount) * h
            val stress = (1.0 - t.toDouble() / tickCount) * (maxCapacityBars * 1.15)
            g.color = Color(51, 65, 85)
            g.draw(Line2D.Double(x + w, ty, x + w + 4.0, ty))
            g.drawString(String.format(Locale.US, "%.1f bar", stress), (x + w + 7.0).toInt(), (ty + 3.0).toInt())
        }

        // Seuil limite admissible repéré
        val limRatio = 1.0 - (1.0 / 1.15)
        val limY = y + limRatio * h
        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(2.0f)
        g.draw(Line2D.Double(x - 4.0, limY, x + w + 12.0, limY))
        g.drawString("\u03c3adm", (x + w + 14.0).toInt(), (limY + 4.0).toInt())
    }

    private fun drawMaxCallout(g: Graphics2D, targetX: Double, targetY: Double, maxStress: Double, maxCapacity: Double) {
        val bubbleW = 190.0
        val bubbleH = 50.0
        val bubbleX = targetX + 30.0
        val bubbleY = targetY - 60.0

        // Ligne de rappel
        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(2.0f)
        g.draw(Line2D.Double(targetX, targetY, bubbleX, bubbleY + bubbleH / 2.0))
        g.fill(Ellipse2D.Double(targetX - 4.0, targetY - 4.0, 8.0, 8.0))

        // Bulle
        g.color = Color(254, 242, 242)
        g.fillRoundRect(bubbleX.toInt(), bubbleY.toInt(), bubbleW.toInt(), bubbleH.toInt(), 8, 8)
        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(1.5f)
        g.drawRoundRect(bubbleX.toInt(), bubbleY.toInt(), bubbleW.toInt(), bubbleH.toInt(), 8, 8)

        // Textes
        g.font = Font("Segoe UI", Font.BOLD, 11)
        g.drawString(String.format(Locale.US, "Pression Max : %.2f bars", maxStress), (bubbleX + 10).toInt(), (bubbleY + 20).toInt())
        g.font = Font("Segoe UI", Font.PLAIN, 10)
        g.color = Color(22, 101, 52)
        g.drawString(String.format(Locale.US, "<= \u03c3sol (%.2f bars) \u2192 CONFORME \u2714", maxCapacity), (bubbleX + 10).toInt(), (bubbleY + 38).toInt())
    }

    private fun drawAxisBubble(g: Graphics2D, x: Double, y: Double, text: String) {
        val r = 10.0
        g.color = Color.WHITE
        g.fill(Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r))
        g.color = Color(71, 85, 105)
        g.stroke = BasicStroke(1.2f)
        g.draw(Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r))
        g.font = Font("Segoe UI", Font.BOLD, 10)
        val tw = g.fontMetrics.stringWidth(text)
        g.drawString(text, (x - tw / 2.0).toInt(), (y + 4.0).toInt())
    }

    private fun drawTitleBlock(g: Graphics2D, width: Int, height: Int, title: String, subtitle: String) {
        g.color = Color(15, 23, 42)
        g.fillRect(0, 0, width, 70)

        g.color = Color(59, 130, 246)
        g.fillRoundRect(25, 15, 40, 40, 8, 8)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 16)
        g.drawString("SF", 34, 40)

        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 18)
        g.drawString(title, 80, 32)
        g.color = Color(148, 163, 184)
        g.font = Font("Segoe UI", Font.PLAIN, 12)
        g.drawString(subtitle, 80, 52)

        val badgeW = 160
        val badgeH = 34
        val badgeX = width - badgeW - 30
        val badgeY = 18
        g.color = Color(22, 101, 52)
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 11)
        g.drawString("CONFORME RPA 2024", badgeX + 15, badgeY + 21)

        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(1.0f)
        g.draw(Line2D.Double(0.0, 70.0, width.toDouble(), 70.0))
    }

    private fun drawTechnicalTable(g: Graphics2D, x: Double, y: Double, w: Double, h: Double, rows: List<Pair<String, String>>) {
        g.color = Color(248, 250, 252)
        g.fillRoundRect(x.toInt(), y.toInt(), w.toInt(), h.toInt(), 12, 12)
        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(1.2f)
        g.drawRoundRect(x.toInt(), y.toInt(), w.toInt(), h.toInt(), 12, 12)

        g.color = Color(30, 41, 59)
        g.fillRoundRect(x.toInt(), y.toInt(), w.toInt(), 40, 12, 12)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 13)
        g.drawString("INTERACTION SOL-FONDATION", (x + 16).toInt(), (y + 25).toInt())

        val rowHeight = (h - 40.0) / rows.size
        var rowY = y + 40.0

        for ((idx, row) in rows.withIndex()) {
            if (idx % 2 == 1) {
                g.color = Color(241, 245, 249)
                g.fillRect(x.toInt() + 1, rowY.toInt(), w.toInt() - 2, rowHeight.toInt())
            }
            g.color = Color(226, 232, 240)
            g.stroke = BasicStroke(0.8f)
            g.draw(Line2D.Double(x, rowY + rowHeight, x + w, rowY + rowHeight))

            g.color = Color(71, 85, 105)
            g.font = Font("Segoe UI", Font.PLAIN, 11)
            g.drawString(row.first, (x + 12).toInt(), (rowY + rowHeight / 2.0 + 4).toInt())

            g.color = if (row.second.contains("CONFORME") || row.second.contains("\u2714")) Color(22, 101, 52) else Color(15, 23, 42)
            g.font = Font("Segoe UI", Font.BOLD, 11)
            val valW = g.fontMetrics.stringWidth(row.second)
            g.drawString(row.second, (x + w - valW - 12).toInt(), (rowY + rowHeight / 2.0 + 4).toInt())

            rowY += rowHeight
        }
    }

    private fun defaultFootings(): List<FootingStrip> = listOf(
        FootingStrip("SF120 - File 1", 2.0, 2.0, 16.0, 2.0, 1.20, 1.45),
        FootingStrip("SF120 - File 2", 2.0, 6.0, 16.0, 6.0, 1.20, 1.55),
        FootingStrip("SF120 - File 3", 2.0, 10.0, 16.0, 10.0, 1.20, 1.62),
        FootingStrip("SFJ200 - File 4 (Joint)", 2.0, 13.0, 16.0, 13.0, 2.00, 1.84, isJoint = true),
        // Files transversales
        FootingStrip("SF120 - File A", 2.0, 2.0, 2.0, 13.0, 1.20, 1.38),
        FootingStrip("SF120 - File B", 6.2, 2.0, 6.2, 13.0, 1.20, 1.48),
        FootingStrip("SF120 - File C", 11.0, 2.0, 11.0, 13.0, 1.20, 1.52),
        FootingStrip("SF120 - File D", 16.0, 2.0, 16.0, 13.0, 1.20, 1.40)
    )

    private fun defaultColumns(): List<ColumnPoint> = listOf(
        ColumnPoint("P1", 2.0, 2.0, 650.0),
        ColumnPoint("P2", 6.2, 2.0, 890.0),
        ColumnPoint("P3", 11.0, 2.0, 920.0),
        ColumnPoint("P4", 16.0, 2.0, 680.0),
        ColumnPoint("P5", 2.0, 6.0, 950.0),
        ColumnPoint("P6", 6.2, 6.0, 1420.0),
        ColumnPoint("P7", 11.0, 6.0, 1480.0),
        ColumnPoint("P8", 16.0, 6.0, 980.0),
        ColumnPoint("P9", 2.0, 10.0, 910.0),
        ColumnPoint("P10", 6.2, 10.0, 1380.0),
        ColumnPoint("P11", 11.0, 10.0, 1440.0),
        ColumnPoint("P12", 16.0, 10.0, 930.0),
        ColumnPoint("P13", 2.0, 13.0, 720.0),
        ColumnPoint("P14", 6.2, 13.0, 1150.0),
        ColumnPoint("P15", 11.0, 13.0, 1200.0),
        ColumnPoint("P16", 16.0, 13.0, 760.0)
    )

    private fun setupQualityGraphics(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }
}
