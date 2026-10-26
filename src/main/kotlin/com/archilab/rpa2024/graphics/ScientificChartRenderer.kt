package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.normative.RpaSpectrumParams
import com.archilab.rpa2024.engine.seismic.SpectrumGenerator
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * Moteur de rendu graphique des courbes scientifiques réglementaires RPA 2024 (Spectre de calcul, limites 0.2*A*I).
 */
class ScientificChartRenderer(
    private val spectrumGenerator: SpectrumGenerator = SpectrumGenerator()
) {

    /**
     * Génère la courbe vectorielle SVG officielle du spectre de calcul RPA 2024 (§ 3.3.3 - Éq. 3.15).
     */
    fun renderRpa2024SpectrumChartSvg(
        params: RpaSpectrumParams,
        svgWidth: Int = 800,
        svgHeight: Int = 500
    ): String {
        val points = spectrumGenerator.generateDesignSpectrumPoints(params, maxPeriodSeconds = 4.0, numPoints = 120)
        val minLimit = spectrumGenerator.calculateMinSpectrumThreshold(params) // 0.2 * A * I

        val marginLeft = 80.0
        val marginRight = 50.0
        val marginTop = 60.0
        val marginBottom = 70.0

        val plotWidth = svgWidth - marginLeft - marginRight
        val plotHeight = svgHeight - marginTop - marginBottom

        val maxT = 4.0
        val maxSa = max(0.50, (points.maxOfOrNull { it.saOverG } ?: 0.5) * 1.15)

        fun xForT(t: Double): Double = marginLeft + (t / maxT) * plotWidth
        fun yForSa(sa: Double): Double = marginTop + plotHeight - (sa / maxSa) * plotHeight

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"30\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#0f172a\">")
        sb.appendLine("    Spectre de Calcul RPA 2024 (DTR BC 2.48 § 3.3.3)")
        sb.appendLine("  </text>")
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"48\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Zone ${params.zone} (A=${params.zoneAccelerationA}) | Site ${params.siteClass} (S=${params.siteFactorS}) | I=${params.importanceFactorI} | R=${params.behaviorFactorR} | QF=${params.qualityFactorQf}")
        sb.appendLine("  </text>")

        // Grille horizontale
        val numYSteps = 5
        for (i in 0..numYSteps) {
            val sa = (i.toDouble() / numYSteps) * maxSa
            val y = yForSa(sa)
            sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$y\" x2=\"${marginLeft + plotWidth}\" y2=\"$y\" stroke=\"#f1f5f9\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"${marginLeft - 10}\" y=\"${y + 4}\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">${round(sa * 100) / 100}g</text>")
        }

        // Grille verticale
        val numXSteps = 8
        for (i in 0..numXSteps) {
            val t = (i.toDouble() / numXSteps) * maxT
            val x = xForT(t)
            sb.appendLine("  <line x1=\"$x\" y1=\"$marginTop\" x2=\"$x\" y2=\"${marginTop + plotHeight}\" stroke=\"#f1f5f9\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"$x\" y=\"${marginTop + plotHeight + 20}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">${round(t * 10) / 10}s</text>")
        }

        // Périodes caractéristiques T1, T2, T3 en pointillés gris
        val markers = listOf(Pair("T1", params.t1), Pair("T2", params.t2), Pair("T3", params.t3))
        for (marker in markers) {
            val label = marker.first
            val tVal = marker.second
            val tx = xForT(tVal)
            sb.appendLine("  <line x1=\"$tx\" y1=\"$marginTop\" x2=\"$tx\" y2=\"${marginTop + plotHeight}\" stroke=\"#cbd5e1\" stroke-width=\"1\" stroke-dasharray=\"3,3\"/>")
            sb.appendLine("  <text x=\"$tx\" y=\"${marginTop - 6}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#475569\">$label=${tVal}s</text>")
        }

        // Ligne de limite minimale rouge (0.2 * A * I)
        val yMin = yForSa(minLimit)
        sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$yMin\" x2=\"${marginLeft + plotWidth}\" y2=\"$yMin\" stroke=\"#dc2626\" stroke-width=\"2\" stroke-dasharray=\"6,4\"/>")
        sb.appendLine("  <text x=\"${marginLeft + plotWidth - 10}\" y=\"${yMin - 6}\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#dc2626\">Limite Min = 0.2*A*I = ${round(minLimit * 1000) / 1000}g</text>")

        // Courbe du spectre de calcul (Bleu plein)
        if (points.isNotEmpty()) {
            val sbPath = StringBuilder()
            sbPath.append("M ${xForT(points[0].periodT)} ${yForSa(points[0].saOverG)} ")
            for (i in 1 until points.size) {
                sbPath.append("L ${xForT(points[i].periodT)} ${yForSa(points[i].saOverG)} ")
            }
            sb.appendLine("  <path d=\"${sbPath.toString().trim()}\" fill=\"none\" stroke=\"#2563eb\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
        }

        // Légendes
        val legY = svgHeight - 20
        sb.appendLine("  <line x1=\"${marginLeft + 30}\" y1=\"$legY\" x2=\"${marginLeft + 60}\" y2=\"$legY\" stroke=\"#2563eb\" stroke-width=\"3\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 70}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#1e293b\">Spectre de calcul Sad(T)/g</text>")

        sb.appendLine("  <line x1=\"${marginLeft + 280}\" y1=\"$legY\" x2=\"${marginLeft + 310}\" y2=\"$legY\" stroke=\"#dc2626\" stroke-width=\"2\" stroke-dasharray=\"6,4\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 320}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#dc2626\">Seuil minimal obligatoire (0.2 A I)</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }

    /**
     * Génère une image PNG (ByteArray) haute résolution du spectre de calcul.
     */
    fun renderSpectrumToPngByteArray(params: RpaSpectrumParams, width: Int = 1200, height: Int = 750): ByteArray {
        val points = spectrumGenerator.generateDesignSpectrumPoints(params, maxPeriodSeconds = 4.0, numPoints = 120)
        val minLimit = spectrumGenerator.calculateMinSpectrumThreshold(params)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        val marginLeft = 100
        val marginRight = 60
        val marginTop = 80
        val marginBottom = 80
        val plotW = width - marginLeft - marginRight
        val plotH = height - marginTop - marginBottom

        val maxT = 4.0
        val maxSa = max(0.50, (points.maxOfOrNull { it.saOverG } ?: 0.5) * 1.15)

        fun px(t: Double): Int = marginLeft + ((t / maxT) * plotW).toInt()
        fun py(sa: Double): Int = marginTop + plotH - ((sa / maxSa) * plotH).toInt()

        // Titre
        g.color = Color(15, 23, 42)
        g.font = Font("Arial", Font.BOLD, 22)
        g.drawString("Spectre de Calcul RPA 2024 (§ 3.3.3)", marginLeft, 40)

        // Limite minimale 0.2*A*I
        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(8f, 5f), 0f)
        val yMin = py(minLimit)
        g.drawLine(marginLeft, yMin, marginLeft + plotW, yMin)

        // Courbe du spectre
        g.color = Color(37, 99, 235)
        g.stroke = BasicStroke(3.5f)
        for (i in 0 until points.size - 1) {
            g.drawLine(px(points[i].periodT), py(points[i].saOverG), px(points[i + 1].periodT), py(points[i + 1].saOverG))
        }

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }
}
