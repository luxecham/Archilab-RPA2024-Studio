package com.archilab.rpa2024.engine.foundations

import kotlin.math.*

/**
 * Générateur vectoriel SVG des fiches de résultats BaelR et BaelT pour les fondations.
 */
class FoundationSchematicRenderer {

    /**
     * Génère la fiche graphique BaelT de section en Té (reproduisant fidèlement l'interface SOCOTEC BaelT).
     */
    fun renderBaelTCrossSectionSvg(
        flangeWidthBMeters: Double,
        webWidthB0Meters: Double,
        totalHeightHMeters: Double,
        flangeThicknessH0Meters: Double,
        neutralAxisY0Meters: Double,
        aInfCm2: Double,
        aSupCm2: Double = 0.0,
        momentMUKNm: Double = 0.0,
        svgWidth: Int = 600,
        svgHeight: Int = 420
    ): String {
        val marginLeft = 60.0
        val marginRight = 60.0
        val marginTop = 70.0
        val plotWidth = svgWidth - marginLeft - marginRight
        val plotHeight = svgHeight - marginTop - 70.0

        val scale = min(plotWidth / flangeWidthBMeters, plotHeight / totalHeightHMeters)

        val bPix = flangeWidthBMeters * scale
        val b0Pix = webWidthB0Meters * scale
        val hPix = totalHeightHMeters * scale
        val h0Pix = flangeThicknessH0Meters * scale
        val y0Pix = neutralAxisY0Meters * scale

        val startX = marginLeft + (plotWidth - bPix) / 2.0
        val startY = marginTop
        val overhangPix = (bPix - b0Pix) / 2.0

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre style BaelT
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"32\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#1e293b\">")
        sb.appendLine("    Section en Té (SOCOTEC BaelT) : B = ${flangeWidthBMeters}m × h = ${totalHeightHMeters}m")
        sb.appendLine("  </text>")
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"52\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Mu = ${momentMUKNm} kN.m | Ainf = ${aInfCm2} cm² | Asup = ${aSupCm2} cm² | y0 = ${neutralAxisY0Meters} m")
        sb.appendLine("  </text>")

        // Contour de la section en Té
        val tPath = "M $startX $startY " +
                "L ${startX + bPix} $startY " +
                "L ${startX + bPix} ${startY + h0Pix} " +
                "L ${startX + bPix - overhangPix} ${startY + h0Pix} " +
                "L ${startX + bPix - overhangPix} ${startY + hPix} " +
                "L ${startX + overhangPix} ${startY + hPix} " +
                "L ${startX + overhangPix} ${startY + h0Pix} " +
                "L $startX ${startY + h0Pix} Z"

        sb.appendLine("  <path d=\"$tPath\" fill=\"#f8fafc\" stroke=\"#1e293b\" stroke-width=\"2.5\"/>")

        // Hachurage de la zone comprimée (hauteur y0 depuis la table supérieure)
        if (y0Pix > 0.0) {
            val compH = min(y0Pix, hPix)
            if (compH <= h0Pix) {
                // Comprimé uniquement dans la table
                sb.appendLine("  <rect x=\"$startX\" y=\"$startY\" width=\"$bPix\" height=\"$compH\" fill=\"#fee2e2\" fill-opacity=\"0.6\" stroke=\"#ef4444\" stroke-width=\"1.5\"/>")
            } else {
                // Comprimé dans la table + partie haute de la nervure
                sb.appendLine("  <rect x=\"$startX\" y=\"$startY\" width=\"$bPix\" height=\"$h0Pix\" fill=\"#fee2e2\" fill-opacity=\"0.6\" stroke=\"#ef4444\" stroke-width=\"1.5\"/>")
                val extraH = compH - h0Pix
                sb.appendLine("  <rect x=\"${startX + overhangPix}\" y=\"${startY + h0Pix}\" width=\"$b0Pix\" height=\"$extraH\" fill=\"#fee2e2\" fill-opacity=\"0.6\" stroke=\"#ef4444\" stroke-width=\"1.5\"/>")
            }
            // Ligne de l'axe neutre y0
            val lineY = startY + compH
            val lineX1 = if (compH <= h0Pix) startX else startX + overhangPix
            val lineX2 = if (compH <= h0Pix) startX + bPix else startX + bPix - overhangPix
            sb.appendLine("  <line x1=\"$lineX1\" y1=\"$lineY\" x2=\"$lineX2\" y2=\"$lineY\" stroke=\"#dc2626\" stroke-width=\"2\" stroke-dasharray=\"4,3\"/>")
            sb.appendLine("  <text x=\"${lineX2 + 8}\" y=\"${lineY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#dc2626\">Axe neutre y0 = ${neutralAxisY0Meters}m</text>")
        }

        // Armatures inférieures tendues (en bas de la nervure)
        val rebarY = startY + hPix - 15.0
        val numBars = 4
        val stepBar = (b0Pix - 20.0) / (numBars - 1)
        for (i in 0 until numBars) {
            val rx = startX + overhangPix + 10.0 + i * stepBar
            sb.appendLine("  <circle cx=\"$rx\" cy=\"$rebarY\" r=\"5\" fill=\"#2563eb\"/>")
        }

        // Cotations
        // 1. Largeur de table b en haut
        val dimYTop = startY - 12.0
        sb.appendLine("  <line x1=\"$startX\" y1=\"$dimYTop\" x2=\"${startX + bPix}\" y2=\"$dimYTop\" stroke=\"#0f172a\" stroke-width=\"1.5\"/>")
        sb.appendLine("  <text x=\"${startX + bPix / 2}\" y=\"${dimYTop - 4}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#0f172a\">b = ${flangeWidthBMeters} m</text>")

        // 2. Largeur de nervure b0 en bas
        val dimYBottom = startY + hPix + 18.0
        val webX1 = startX + overhangPix
        val webX2 = webX1 + b0Pix
        sb.appendLine("  <line x1=\"$webX1\" y1=\"$dimYBottom\" x2=\"$webX2\" y2=\"$dimYBottom\" stroke=\"#0f172a\" stroke-width=\"1.5\"/>")
        sb.appendLine("  <text x=\"${webX1 + b0Pix / 2}\" y=\"${dimYBottom + 14}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#0f172a\">b0 = ${webWidthB0Meters} m</text>")

        // 3. Hauteur totale h à gauche
        val dimXLeft = startX - 16.0
        sb.appendLine("  <line x1=\"$dimXLeft\" y1=\"$startY\" x2=\"$dimXLeft\" y2=\"${startY + hPix}\" stroke=\"#0f172a\" stroke-width=\"1.5\"/>")
        sb.appendLine("  <text x=\"${dimXLeft - 4}\" y=\"${startY + hPix / 2}\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#0f172a\">h = ${totalHeightHMeters} m</text>")

        // Légende
        val legY = svgHeight - 18
        sb.appendLine("  <circle cx=\"${marginLeft + 20}\" cy=\"$legY\" r=\"5\" fill=\"#2563eb\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 32}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#334155\">Aciers tendus Ainf = ${aInfCm2} cm²</text>")

        sb.appendLine("  <rect x=\"${marginLeft + 240}\" y=\"${legY - 6}\" width=\"14\" height=\"12\" fill=\"#fee2e2\" stroke=\"#ef4444\" stroke-width=\"1\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 262}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">Zone comprimée du béton</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }
}
