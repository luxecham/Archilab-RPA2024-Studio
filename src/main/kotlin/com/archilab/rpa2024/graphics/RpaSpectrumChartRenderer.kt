package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.model.SoilSiteClass
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import java.awt.*
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * Moteur vectoriel officiel de tracé des Spectres Sismiques Réglementaires selon le DTR BC 2.48 (RPA 2024 § 3.3).
 * Trace le repère orthonormé coté, les spectres élastique Sae/g et de calcul Sad/g, ainsi que le seuil 0.20·A·I.
 * Remplace 100% des captures d'écran de logiciels externes.
 */
class RpaSpectrumChartRenderer {

    data class SpectrumPoint(val tSeconds: Double, val saeOverG: Double, val sadOverG: Double)

    fun computeSpectrumCurve(
        zone: SeismicZone,
        site: SoilSiteClass,
        importance: ImportanceGroup,
        r: Double = 3.5,
        q: Double = 1.10,
        dampingPercent: Double = 7.0,
        maxT: Double = 4.0,
        steps: Int = 200
    ): Pair<List<SpectrumPoint>, Double> {
        val a = zone.coeffA
        val i = importance.coeffI
        val s = site.s
        val t1 = site.t1
        val t2 = site.t2
        val t3 = site.t3
        val eta = sqrt(7.0 / (2.0 + dampingPercent))
        val minThreshold = 0.20 * a * i

        val points = mutableListOf<SpectrumPoint>()
        for (idx in 0..steps) {
            val t = (idx.toDouble() / steps) * maxT
            if (t <= 0.0) {
                points.add(SpectrumPoint(0.0, a * i, max(a * i * (q / r), minThreshold)))
                continue
            }
            val sae: Double = when {
                t < t1 -> a * i * (1.0 + (t / t1) * (2.5 * eta * s - 1.0))
                t <= t2 -> 2.5 * a * i * eta * s
                t <= t3 -> 2.5 * a * i * eta * s * (t2 / t)
                else -> 2.5 * a * i * eta * s * (t2 / t3) * (t3 / t).pow(2.0)
            }
            val sadRaw = sae * (q / r)
            val sad = max(sadRaw, minThreshold)
            points.add(SpectrumPoint(t, sae, sad))
        }
        return Pair(points, minThreshold)
    }

    /**
     * Génère l'image haute définition (PNG 300 DPI) pour injection Word ou affichage Desktop.
     */
    fun renderToPng(
        zone: SeismicZone = SeismicZone.ZONE_III,
        site: SoilSiteClass = SoilSiteClass.S3,
        importance: ImportanceGroup = ImportanceGroup.GROUPE_1A,
        r: Double = 3.5,
        q: Double = 1.10,
        t0Operating: Double = 0.399,
        width: Int = 1200,
        height: Int = 750
    ): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        // Fond blanc
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        val (points, minThreshold) = computeSpectrumCurve(zone, site, importance, r, q)

        val padLeft = 110.0
        val padRight = 60.0
        val padTop = 110.0
        val padBottom = 90.0
        val plotW = width - padLeft - padRight
        val plotH = height - padTop - padBottom

        val maxT = 4.0
        val maxSa = max(0.35, max(points.maxOf { it.saeOverG }, points.maxOf { it.sadOverG }) * 1.15)

        fun toPxX(t: Double): Double = padLeft + (t / maxT) * plotW
        fun toPxY(sa: Double): Double = padTop + plotH - (sa / maxSa) * plotH

        // Titre et bandeau réglementaire
        g.color = Color(0x1B, 0x36, 0x5D)
        g.font = Font("Arial", Font.BOLD, 22)
        g.drawString("SPECTRES SISMIQUES RÉGLEMENTAIRES DTR BC 2.48 (RPA 2024)", padLeft.toInt(), 45)

        g.color = Color(0x64, 0x74, 0x8B)
        g.font = Font("Arial", Font.PLAIN, 14)
        val subtitle = "Wilaya : Sidi Bel Abbès (Zone ${zone.code}, A=${zone.coeffA}) | Site ${site.name} (S=${site.s}) | Usage ${importance.code} (I=${importance.coeffI}) | R=$r | Q=$q"
        g.drawString(subtitle, padLeft.toInt(), 72)

        // Grille millimétrique
        g.color = Color(0xED, 0xF2, 0xF7)
        g.stroke = BasicStroke(1.0f)
        for (i in 1..8) {
            val t = i * 0.5
            val x = toPxX(t)
            g.draw(Line2D.Double(x, padTop, x, padTop + plotH))
        }
        val saStep = 0.05
        var saVal = saStep
        while (saVal <= maxSa) {
            val y = toPxY(saVal)
            g.draw(Line2D.Double(padLeft, y, padLeft + plotW, y))
            saVal += saStep
        }

        // Axes de coordonnées
        g.color = Color(0x2D, 0x37, 0x48)
        g.stroke = BasicStroke(2.0f)
        g.draw(Line2D.Double(padLeft, padTop, padLeft, padTop + plotH)) // Axe Y
        g.draw(Line2D.Double(padLeft, padTop + plotH, padLeft + plotW, padTop + plotH)) // Axe X

        // Graduations Axe X (Période T en secondes)
        g.font = Font("Arial", Font.PLAIN, 12)
        g.color = Color(0x4A, 0x55, 0x68)
        for (i in 0..8) {
            val t = i * 0.5
            val x = toPxX(t)
            g.draw(Line2D.Double(x, padTop + plotH, x, padTop + plotH + 5))
            val lbl = String.format(Locale.US, "%.1fs", t)
            g.drawString(lbl, (x - 12).toFloat(), (padTop + plotH + 22).toFloat())
        }
        g.font = Font("Arial", Font.BOLD, 14)
        g.drawString("Période fondamentale T (s)", (padLeft + plotW / 2 - 80).toFloat(), (padTop + plotH + 48).toFloat())

        // Graduations Axe Y (Sa / g)
        saVal = 0.0
        g.font = Font("Arial", Font.PLAIN, 12)
        while (saVal <= maxSa) {
            val y = toPxY(saVal)
            g.draw(Line2D.Double(padLeft - 5, y, padLeft, y))
            val lbl = String.format(Locale.US, "%.2f", saVal)
            g.drawString(lbl, (padLeft - 55).toFloat(), (y + 5).toFloat())
            saVal += saStep
        }
        // Libellé vertical Sa/g
        val oldTransform = g.transform
        g.translate(35.0, padTop + plotH / 2 + 50)
        g.rotate(-Math.PI / 2)
        g.drawString("Accélération spectrale normalisée Sa / g", 0f, 0f)
        g.transform = oldTransform

        // Périodes caractéristiques verticales T1, T2, T3
        val charT = listOf("T1" to site.t1, "T2" to site.t2, "T3" to site.t3)
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 4f), 0f)
        g.color = Color(0x94, 0xA3, 0xB8)
        g.font = Font("Arial", Font.BOLD, 12)
        for ((name, tVal) in charT) {
            val px = toPxX(tVal)
            g.draw(Line2D.Double(px, padTop, px, padTop + plotH))
            g.drawString("$name=${tVal}s", (px - 15).toFloat(), (padTop - 10).toFloat())
        }

        // Seuil Plancher Minimal 0.20·A·I (Ligne pointillée verte)
        val yMin = toPxY(minThreshold)
        g.stroke = BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(6f, 6f), 0f)
        g.color = Color(0x10, 0xB9, 0x81)
        g.draw(Line2D.Double(padLeft, yMin, padLeft + plotW, yMin))
        g.drawString(String.format(Locale.US, "Plancher minimal 0.20·A·I = %.3fg (RPA § 3.3.3)", minThreshold), (padLeft + 15).toFloat(), (yMin - 8).toFloat())

        // Tracé Courbe 1 : Spectre élastique Sae / g (Bleu royal)
        val pathSae = Path2D.Double()
        points.forEachIndexed { idx, pt ->
            val px = toPxX(pt.tSeconds)
            val py = toPxY(pt.saeOverG)
            if (idx == 0) pathSae.moveTo(px, py) else pathSae.lineTo(px, py)
        }
        g.color = Color(0x25, 0x63, 0xEB)
        g.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(pathSae)

        // Tracé Courbe 2 : Spectre de calcul Sad / g (Rouge cramoisi)
        val pathSad = Path2D.Double()
        points.forEachIndexed { idx, pt ->
            val px = toPxX(pt.tSeconds)
            val py = toPxY(pt.sadOverG)
            if (idx == 0) pathSad.moveTo(px, py) else pathSad.lineTo(px, py)
        }
        g.color = Color(0xDC, 0x26, 0x26)
        g.stroke = BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(pathSad)

        // Point de fonctionnement T0 du bâtiment
        val opPt = points.minByOrNull { abs(it.tSeconds - t0Operating) }
        if (opPt != null) {
            val px = toPxX(opPt.tSeconds)
            val pySad = toPxY(opPt.sadOverG)

            g.color = Color(0x7C, 0x3A, 0xED)
            g.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(3f, 3f), 0f)
            g.draw(Line2D.Double(px, padTop + plotH, px, pySad))
            g.draw(Line2D.Double(padLeft, pySad, px, pySad))

            // Marqueur circulaire
            g.color = Color(0x7C, 0x3A, 0xED)
            g.fill(Ellipse2D.Double(px - 6, pySad - 6, 12.0, 12.0))
            g.color = Color.WHITE
            g.fill(Ellipse2D.Double(px - 3, pySad - 3, 6.0, 6.0))

            // Bulle d'annotation
            g.color = Color(0x1E, 0x29, 0x3B)
            g.font = Font("Arial", Font.BOLD, 12)
            val tag = String.format(Locale.US, "Point de calcul : T0=%.3fs, Sad/g=%.3f", opPt.tSeconds, opPt.sadOverG)
            g.drawString(tag, (px + 12).toFloat(), (pySad - 8).toFloat())
        }

        // Légende officielle
        val legX = width - 360.0
        val legY = padTop + 20.0
        val legW = 320.0
        val legH = 110.0

        g.color = Color(0xF8, 0xFA, 0xFC)
        g.fill(Rectangle2D.Double(legX, legY, legW, legH))
        g.color = Color(0xCB, 0xD5, 0xE1)
        g.stroke = BasicStroke(1.0f)
        g.draw(Rectangle2D.Double(legX, legY, legW, legH))

        // Éléments de légende
        var cy = legY + 25.0
        g.font = Font("Arial", Font.PLAIN, 12)
        g.color = Color(0x1E, 0x29, 0x3B)

        // 1. Sae
        g.color = Color(0x25, 0x63, 0xEB)
        g.stroke = BasicStroke(2.5f)
        g.draw(Line2D.Double(legX + 15, cy, legX + 45, cy))
        g.color = Color(0x1E, 0x29, 0x3B)
        g.drawString("Spectre élastique Sae / g (Éq. 3.8)", (legX + 55).toFloat(), (cy + 4).toFloat())

        // 2. Sad
        cy += 28.0
        g.color = Color(0xDC, 0x26, 0x26)
        g.stroke = BasicStroke(3.0f)
        g.draw(Line2D.Double(legX + 15, cy, legX + 45, cy))
        g.color = Color(0x1E, 0x29, 0x3B)
        g.drawString("Spectre de calcul Sad / g (Éq. 3.15, R=$r)", (legX + 55).toFloat(), (cy + 4).toFloat())

        // 3. Min limit
        cy += 28.0
        g.color = Color(0x10, 0xB9, 0x81)
        g.stroke = BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(5f, 5f), 0f)
        g.draw(Line2D.Double(legX + 15, cy, legX + 45, cy))
        g.color = Color(0x1E, 0x29, 0x3B)
        g.drawString("Seuil plancher 0.20·A·I (min = " + String.format(Locale.US, "%.3fg)", minThreshold), (legX + 55).toFloat(), (cy + 4).toFloat())

        g.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }
}
