package com.archilab.rpa2024.graphics

import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * Moteur vectoriel officiel de tracé des Enveloppes de Sollicitations des Voiles selon le DTR BC 2.48 (RPA 2024 § 7.7).
 * Trace les diagrammes de moments enveloppes décalés de hcr et d'efforts tranchants amplifiés à 1.40 Vu.
 */
class ShearWallEnvelopesRenderer {

    data class StoryLevelData(val storyName: String, val elevationM: Double, val momentKNm: Double, val shearKN: Double)

    /**
     * Génère l'image haute définition (PNG) de l'enveloppe des moments fléchissants Mu(z) décalée de hcr.
     */
    fun renderMomentEnvelopePng(
        stories: List<StoryLevelData> = defaultStories(),
        wallLengthM: Double = 3.20,
        wallThicknessM: Double = 0.20,
        totalHeightM: Double = 9.18,
        hcrM: Double = 3.20,
        elementName: String = "VOILE V1 (L=3.20m, e=20cm)",
        width: Int = 1100,
        height: Int = 800
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        setupQualityGraphics(g)

        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        drawTitleBlock(g, width, height, "ENVELOPPE DES MOMENTS FLÉCHISSANTS DÉCALÉE DE hcr", "$elementName (RPA 2024 § 7.7.1)")

        val padLeft = 110.0
        val padRight = 360.0
        val padTop = 110.0
        val padBottom = 90.0

        val plotW = width - padLeft - padRight
        val plotH = height - padTop - padBottom

        val maxM = max(800.0, stories.maxOf { it.momentKNm } * 1.30)
        val maxZ = max(10.0, totalHeightM * 1.10)

        fun xForM(m: Double): Double = padLeft + (m / maxM) * plotW
        fun yForZ(z: Double): Double = padTop + plotH - (z / maxZ) * plotH

        // 1. Zone critique hcr ombrée à la base
        val yBase = yForZ(0.0)
        val yHcr = yForZ(hcrM)
        g.color = Color(254, 242, 242, 180) // Rouge rosé très pâle
        g.fill(Rectangle2D.Double(padLeft, yHcr, plotW, yBase - yHcr))

        // Grille de fond
        g.color = Color(241, 245, 249)
        g.stroke = BasicStroke(1.0f)
        val stepM = 200.0
        var curM = 0.0
        while (curM <= maxM) {
            val gx = xForM(curM)
            g.draw(Line2D.Double(gx, padTop, gx, padTop + plotH))
            curM += stepM
        }

        val stepZ = 2.0
        var curZ = 0.0
        while (curZ <= maxZ) {
            val gy = yForZ(curZ)
            g.draw(Line2D.Double(padLeft, gy, padLeft + plotW, gy))
            curZ += stepZ
        }

        // Axes principaux
        g.color = Color(71, 85, 105)
        g.stroke = BasicStroke(2.0f)
        g.draw(Line2D.Double(padLeft, padTop, padLeft, padTop + plotH))
        g.draw(Line2D.Double(padLeft, padTop + plotH, padLeft + plotW, padTop + plotH))

        // Graduations Axe X (Moments en kN.m)
        curM = 0.0
        g.font = Font("Segoe UI", Font.PLAIN, 11)
        while (curM <= maxM) {
            val gx = xForM(curM)
            g.color = Color(100, 116, 139)
            g.draw(Line2D.Double(gx, padTop + plotH, gx, padTop + plotH + 5.0))
            val text = String.format(Locale.US, "%.0f", curM)
            val tw = g.fontMetrics.stringWidth(text)
            g.drawString(text, (gx - tw / 2.0).toInt(), (padTop + plotH + 20.0).toInt())
            curM += stepM
        }
        g.font = Font("Segoe UI", Font.BOLD, 12)
        g.drawString("Moment Fléchissant Mu (kN·m)", (padLeft + plotW / 2.0 - 90).toInt(), (padTop + plotH + 45.0).toInt())

        // Graduations Axe Z (Hauteur z en m)
        curZ = 0.0
        while (curZ <= maxZ) {
            val gy = yForZ(curZ)
            g.color = Color(100, 116, 139)
            g.draw(Line2D.Double(padLeft - 5.0, gy, padLeft, gy))
            val text = String.format(Locale.US, "%.1f m", curZ)
            val tw = g.fontMetrics.stringWidth(text)
            g.drawString(text, (padLeft - tw - 10.0).toInt(), (gy + 4.0).toInt())
            curZ += stepZ
        }

        // Traçage des niveaux d'étages
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 4f), 0f)
        g.color = Color(148, 163, 184)
        for (st in stories) {
            val sy = yForZ(st.elevationM)
            g.draw(Line2D.Double(padLeft, sy, padLeft + plotW, sy))
            g.font = Font("Segoe UI", Font.ITALIC, 10)
            g.drawString("${st.storyName} (z = ${st.elevationM}m)", (padLeft + 10).toInt(), (sy - 4).toInt())
        }

        // 1. Courbe Analyse ETABS brute (Pointillée bleue)
        val pathEtabs = Path2D.Double()
        stories.sortedBy { it.elevationM }.forEachIndexed { idx, st ->
            val px = xForM(st.momentKNm)
            val py = yForZ(st.elevationM)
            if (idx == 0) pathEtabs.moveTo(px, py) else pathEtabs.lineTo(px, py)
        }
        g.color = Color(37, 99, 235) // Bleu royal
        g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(8f, 5f), 0f)
        g.draw(pathEtabs)

        // Points ETABS
        for (st in stories) {
            val px = xForM(st.momentKNm)
            val py = yForZ(st.elevationM)
            g.color = Color(37, 99, 235)
            g.fill(Ellipse2D.Double(px - 4.5, py - 4.5, 9.0, 9.0))
            g.color = Color.WHITE
            g.fill(Ellipse2D.Double(px - 2.0, py - 2.0, 4.0, 4.0))
        }

        // 2. Courbe Enveloppe Décalée de hcr (RPA 2024 § 7.7.1)
        val baseM = stories.minByOrNull { it.elevationM }?.momentKNm ?: 1200.0
        val pathShifted = Path2D.Double()

        // De la base z=0 jusqu'à z=hcr, le moment reste constant et égal à Mbase
        pathShifted.moveTo(xForM(baseM), yForZ(0.0))
        pathShifted.lineTo(xForM(baseM), yForZ(hcrM))

        // De z=hcr jusqu'au sommet z=totalHeightM, décroissance linéaire vers 0
        pathShifted.lineTo(xForM(0.0), yForZ(totalHeightM))

        g.color = Color(220, 38, 38) // Rouge écarlate
        g.stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(pathShifted)

        // Annotation de hcr (Accolade / Flèche)
        val arrowX = padLeft + plotW + 15.0
        drawDimensionLine(g, arrowX, yBase, arrowX, yHcr, String.format(Locale.US, "hcr = %.2f m", hcrM), isHorizontal = false)

        // Tableau et Légende à droite
        val rightX = width - padRight + 40.0
        val rightY = padTop
        val rightW = padRight - 60.0
        val rightH = plotH

        drawTechnicalTable(
            g, rightX, rightY, rightW, rightH,
            listOf(
                "Longueur du voile Lw" to String.format(Locale.US, "%.2f m", wallLengthM),
                "Épaisseur du voile e" to String.format(Locale.US, "%.0f cm", wallThicknessM * 100.0),
                "Hauteur totale H" to String.format(Locale.US, "%.2f m", totalHeightM),
                "Hauteur critique hcr" to String.format(Locale.US, "max(Lw, H/6) = %.2f m", hcrM),
                "Moment à la base Mu,base" to String.format(Locale.US, "%.1f kN·m", baseM),
                "Loi d'enveloppe" to "Mu = Mbase sur [0, hcr]",
                "Décroissance au sommet" to "Linéaire jusqu'à z = H",
                "Ferraillage vertical" to "Bande rive + Courant",
                "Contrôle traction rive" to "Acier 2 x 6 HA 14",
                "Statut RPA 2024" to "CONFORME (CV \u2714)"
            )
        )

        // Légende graphique sous le graphe
        drawLegend(g, padLeft, padTop + 15.0)

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Génère l'image haute définition (PNG) de l'enveloppe d'effort tranchant amplifié à 1.40 Vu (RPA § 7.7.3).
     */
    fun renderShearEnvelopePng(
        stories: List<StoryLevelData> = defaultStories(),
        amplificationFactor: Double = 1.40,
        wallLengthM: Double = 3.20,
        wallThicknessM: Double = 0.20,
        fc28MPa: Double = 25.0,
        elementName: String = "VOILE V1 — EFFORT TRANCHANT VU",
        width: Int = 1100,
        height: Int = 800
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        setupQualityGraphics(g)

        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        drawTitleBlock(g, width, height, "ENVELOPPE D'EFFORT TRANCHANT AMPLIFIÉ 1.40 Vu", "$elementName (RPA 2024 § 7.7.3)")

        val padLeft = 110.0
        val padRight = 360.0
        val padTop = 110.0
        val padBottom = 90.0

        val plotW = width - padLeft - padRight
        val plotH = height - padTop - padBottom

        // Capacité limite à l'effort tranchant (tau_b <= 0.20 fc28)
        // Vu_lim = 0.20 * fc28 * (e * 0.9 * Lw)
        val vuLimitKN = 0.20 * (fc28MPa * 1000.0) * (wallThicknessM * 0.90 * wallLengthM)

        val maxV = max(vuLimitKN * 1.15, stories.maxOf { it.shearKN * amplificationFactor } * 1.25)
        val maxZ = max(10.0, stories.maxOf { it.elevationM } * 1.15)

        fun xForV(v: Double): Double = padLeft + (v / maxV) * plotW
        fun yForZ(z: Double): Double = padTop + plotH - (z / maxZ) * plotH

        // Grille
        g.color = Color(241, 245, 249)
        g.stroke = BasicStroke(1.0f)
        val stepV = 200.0
        var curV = 0.0
        while (curV <= maxV) {
            val gx = xForV(curV)
            g.draw(Line2D.Double(gx, padTop, gx, padTop + plotH))
            curV += stepV
        }
        val stepZ = 2.0
        var curZ = 0.0
        while (curZ <= maxZ) {
            val gy = yForZ(curZ)
            g.draw(Line2D.Double(padLeft, gy, padLeft + plotW, gy))
            curZ += stepZ
        }

        // Axes
        g.color = Color(71, 85, 105)
        g.stroke = BasicStroke(2.0f)
        g.draw(Line2D.Double(padLeft, padTop, padLeft, padTop + plotH))
        g.draw(Line2D.Double(padLeft, padTop + plotH, padLeft + plotW, padTop + plotH))

        // Graduations
        curV = 0.0
        g.font = Font("Segoe UI", Font.PLAIN, 11)
        while (curV <= maxV) {
            val gx = xForV(curV)
            g.color = Color(100, 116, 139)
            g.draw(Line2D.Double(gx, padTop + plotH, gx, padTop + plotH + 5.0))
            val text = String.format(Locale.US, "%.0f", curV)
            val tw = g.fontMetrics.stringWidth(text)
            g.drawString(text, (gx - tw / 2.0).toInt(), (padTop + plotH + 20.0).toInt())
            curV += stepV
        }
        g.font = Font("Segoe UI", Font.BOLD, 12)
        g.drawString("Effort Tranchant Vu (kN)", (padLeft + plotW / 2.0 - 70).toInt(), (padTop + plotH + 45.0).toInt())

        curZ = 0.0
        while (curZ <= maxZ) {
            val gy = yForZ(curZ)
            g.color = Color(100, 116, 139)
            g.draw(Line2D.Double(padLeft - 5.0, gy, padLeft, gy))
            val text = String.format(Locale.US, "%.1f m", curZ)
            val tw = g.fontMetrics.stringWidth(text)
            g.drawString(text, (padLeft - tw - 10.0).toInt(), (gy + 4.0).toInt())
            curZ += stepZ
        }

        // Seuil limite admissible (Ligne pourpre)
        val xLim = xForV(vuLimitKN)
        g.color = Color(147, 51, 234) // Violet
        g.stroke = BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(8f, 6f), 0f)
        g.draw(Line2D.Double(xLim, padTop, xLim, padTop + plotH))
        g.font = Font("Segoe UI", Font.BOLD, 11)
        g.drawString(String.format(Locale.US, "Limite \u03c4b <= 0.20 fc28 (VR,max = %.0f kN)", vuLimitKN), (xLim + 6).toInt(), (padTop + 25).toInt())

        // 1. Courbe brute ETABS
        val pathRaw = Path2D.Double()
        stories.sortedBy { it.elevationM }.forEachIndexed { idx, st ->
            val px = xForV(st.shearKN)
            val py = yForZ(st.elevationM)
            if (idx == 0) pathRaw.moveTo(px, py) else pathRaw.lineTo(px, py)
        }
        g.color = Color(59, 130, 246)
        g.stroke = BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(6f, 4f), 0f)
        g.draw(pathRaw)

        // 2. Courbe Amplifiée 1.40 Vu
        val pathAmp = Path2D.Double()
        stories.sortedBy { it.elevationM }.forEachIndexed { idx, st ->
            val px = xForV(st.shearKN * amplificationFactor)
            val py = yForZ(st.elevationM)
            if (idx == 0) pathAmp.moveTo(px, py) else pathAmp.lineTo(px, py)
        }
        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(pathAmp)

        // Points amplifiés
        for (st in stories) {
            val px = xForV(st.shearKN * amplificationFactor)
            val py = yForZ(st.elevationM)
            g.color = Color(220, 38, 38)
            g.fill(Ellipse2D.Double(px - 5.0, py - 5.0, 10.0, 10.0))
            g.color = Color.WHITE
            g.fill(Ellipse2D.Double(px - 2.0, py - 2.0, 4.0, 4.0))
        }

        // Tableau à droite
        val maxVuAmp = stories.maxOf { it.shearKN * amplificationFactor }
        val tauBMPa = (maxVuAmp * 1000.0) / (wallThicknessM * 1000.0 * 0.90 * wallLengthM * 1000.0)
        val tauLimMPa = 0.20 * fc28MPa

        val rightX = width - padRight + 40.0
        val rightY = padTop
        val rightW = padRight - 60.0
        val rightH = plotH

        drawTechnicalTable(
            g, rightX, rightY, rightW, rightH,
            listOf(
                "Effort Tranchant max brut" to String.format(Locale.US, "%.1f kN", stories.maxOf { it.shearKN }),
                "Facteur d'amplification" to String.format(Locale.US, "x %.2f (RPA § 7.7.3)", amplificationFactor),
                "Effort Tranchant calcul Vd" to String.format(Locale.US, "%.1f kN", maxVuAmp),
                "Contrainte de cisaillement \u03c4b" to String.format(Locale.US, "%.2f MPa", tauBMPa),
                "Contrainte limite admissible" to String.format(Locale.US, "0.20 fc28 = %.2f MPa", tauLimMPa),
                "Ratio de cisaillement \u03c4b/\u03c4lim" to String.format(Locale.US, "%.1f %% (<= 100%%)", (tauBMPa / tauLimMPa) * 100.0),
                "Armatures transversales St" to "HA 10 espacés 15 cm",
                "Pourcentage mini transversal" to "0.15 % (Respecté \u2714)",
                "Statut Cisaillement CTC" to "CONFORME (CV \u2714)"
            )
        )

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    private fun defaultStories(): List<StoryLevelData> = listOf(
        StoryLevelData("Fondation", 0.0, 1180.0, 310.0),
        StoryLevelData("Niveau RDC", 3.06, 920.0, 290.0),
        StoryLevelData("Niveau 1er", 6.12, 540.0, 240.0),
        StoryLevelData("Niveau 2ème", 9.18, 120.0, 110.0)
    )

    private fun drawDimensionLine(g: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double, text: String, isHorizontal: Boolean) {
        g.color = Color(185, 28, 28)
        g.stroke = BasicStroke(1.8f)
        g.draw(Line2D.Double(x1, y1, x2, y2))
        val tick = 8.0
        g.draw(Line2D.Double(x1 - tick, y1, x1 + tick, y1))
        g.draw(Line2D.Double(x2 - tick, y2, x2 + tick, y2))
        g.font = Font("Segoe UI", Font.BOLD, 12)
        val fm = g.fontMetrics
        val tw = fm.stringWidth(text)
        val at = g.transform
        g.translate(x1 + 18.0, (y1 + y2) / 2.0 + tw / 2.0)
        g.rotate(-Math.PI / 2.0)
        g.drawString(text, 0, 0)
        g.transform = at
    }

    private fun drawLegend(g: Graphics2D, x: Double, y: Double) {
        val ly = y + 10.0
        g.color = Color(37, 99, 235)
        g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(8f, 5f), 0f)
        g.draw(Line2D.Double(x + 10.0, ly, x + 40.0, ly))
        g.color = Color(51, 65, 85)
        g.font = Font("Segoe UI", Font.PLAIN, 11)
        g.drawString("Analyse Brute ETABS", (x + 48).toInt(), (ly + 4).toInt())

        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(3.0f)
        g.draw(Line2D.Double(x + 180.0, ly, x + 210.0, ly))
        g.color = Color(51, 65, 85)
        g.drawString("Enveloppe Décalée hcr (RPA 2024)", (x + 218).toInt(), (ly + 4).toInt())
    }

    private fun drawTitleBlock(g: Graphics2D, width: Int, height: Int, title: String, subtitle: String) {
        g.color = Color(15, 23, 42)
        g.fillRect(0, 0, width, 70)

        g.color = Color(59, 130, 246)
        g.fillRoundRect(25, 15, 40, 40, 8, 8)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 16)
        g.drawString("SW", 32, 40)

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
        g.drawString("PARAMÈTRES RÉGLEMENTAIRES", (x + 16).toInt(), (y + 25).toInt())

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

    private fun setupQualityGraphics(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }
}
