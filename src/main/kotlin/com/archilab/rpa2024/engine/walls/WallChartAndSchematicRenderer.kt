package com.archilab.rpa2024.engine.walls

import kotlin.math.*

/**
 * Générateur graphique vectoriel SVG des diagrammes d'enveloppes et des coupes de voiles de contreventement.
 */
class WallChartAndSchematicRenderer {

    /**
     * Génère le graphique vectoriel SVG de l'enveloppe des moments fléchissants M(z).
     */
    fun renderMomentEnvelopeSvg(
        momentEnvelope: WallMomentEnvelope,
        svgWidth: Int = 800,
        svgHeight: Int = 500
    ): String {
        val marginLeft = 90.0
        val marginRight = 50.0
        val marginTop = 60.0
        val marginBottom = 70.0

        val plotWidth = svgWidth - marginLeft - marginRight
        val plotHeight = svgHeight - marginTop - marginBottom

        val maxZ = max(1.0, momentEnvelope.totalHeightHwMeters)
        val allMoments = momentEnvelope.shiftedDesignEnvelopeCurve.map { it.value } +
                momentEnvelope.linearEnvelopeCurve.map { it.value } +
                momentEnvelope.analysisCurve.map { it.value }
        val maxM = max(100.0, (allMoments.maxOrNull() ?: 1000.0) * 1.15)
        val minM = min(0.0, (allMoments.minOrNull() ?: 0.0) * 1.10)
        val rangeM = max(1.0, maxM - minM)

        fun xForM(m: Double): Double = marginLeft + ((m - minM) / rangeM) * plotWidth
        fun yForZ(z: Double): Double = marginTop + plotHeight - (z / maxZ) * plotHeight

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <defs>")
        sb.appendLine("    <filter id=\"drop-shadow\" x=\"-5%\" y=\"-5%\" width=\"110%\" height=\"110%\">")
        sb.appendLine("      <feDropShadow dx=\"2\" dy=\"2\" stdDeviation=\"3\" flood-opacity=\"0.15\"/>")
        sb.appendLine("    </filter>")
        sb.appendLine("  </defs>")

        // Fond blanc avec bordure
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"30\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#1e293b\">")
        sb.appendLine("    Enveloppe des Moments Fléchissants M(z) - Voile ${momentEnvelope.pierName} (RPA 2024 § 7.7.4)")
        sb.appendLine("  </text>")

        // Sous-titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"48\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Décalage de traction hcr = ${momentEnvelope.criticalZoneHeightHcrMeters} m | Mbase = ${momentEnvelope.baseMomentKNm} kN.m | Hw = ${momentEnvelope.totalHeightHwMeters} m")
        sb.appendLine("  </text>")

        // Grille horizontale (altitudes Z)
        val numZSteps = 6
        for (i in 0..numZSteps) {
            val z = (i.toDouble() / numZSteps) * maxZ
            val y = yForZ(z)
            sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$y\" x2=\"${marginLeft + plotWidth}\" y2=\"$y\" stroke=\"#f1f5f9\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"${marginLeft - 10}\" y=\"${y + 4}\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">${round(z * 10) / 10} m</text>")
        }

        // Grille verticale (moments M)
        val numMSteps = 5
        for (i in 0..numMSteps) {
            val m = minM + (i.toDouble() / numMSteps) * rangeM
            val x = xForM(m)
            sb.appendLine("  <line x1=\"$x\" y1=\"$marginTop\" x2=\"$x\" y2=\"${marginTop + plotHeight}\" stroke=\"#f1f5f9\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"$x\" y=\"${marginTop + plotHeight + 20}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">${m.toInt()}</text>")
        }

        // Axes principaux
        val yAxisX = xForM(0.0)
        sb.appendLine("  <line x1=\"$yAxisX\" y1=\"$marginTop\" x2=\"$yAxisX\" y2=\"${marginTop + plotHeight}\" stroke=\"#94a3b8\" stroke-width=\"1.5\"/>")
        val xAxisY = yForZ(0.0)
        sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$xAxisY\" x2=\"${marginLeft + plotWidth}\" y2=\"$xAxisY\" stroke=\"#94a3b8\" stroke-width=\"1.5\"/>")

        // Zone critique hcr hachurée en arrière-plan
        val yHcr = yForZ(momentEnvelope.criticalZoneHeightHcrMeters)
        val hcrBoxHeight = xAxisY - yHcr
        sb.appendLine("  <rect x=\"$marginLeft\" y=\"$yHcr\" width=\"$plotWidth\" height=\"$hcrBoxHeight\" fill=\"#eff6ff\" fill-opacity=\"0.6\"/>")
        sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$yHcr\" x2=\"${marginLeft + plotWidth}\" y2=\"$yHcr\" stroke=\"#3b82f6\" stroke-width=\"1.2\" stroke-dasharray=\"4,4\"/>")
        sb.appendLine("  <text x=\"${marginLeft + plotWidth - 10}\" y=\"${yHcr - 6}\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#2563eb\">Zone Critique hcr = ${momentEnvelope.criticalZoneHeightHcrMeters} m</text>")

        // 1. Courbe d'analyse (Rouge)
        if (momentEnvelope.analysisCurve.size >= 2) {
            val pathData = buildPolylinePath(momentEnvelope.analysisCurve.map { Pair(xForM(it.value), yForZ(it.zMeters)) })
            sb.appendLine("  <path d=\"$pathData\" fill=\"none\" stroke=\"#ef4444\" stroke-width=\"2.5\" stroke-dasharray=\"6,3\"/>")
            for (pt in momentEnvelope.analysisCurve) {
                sb.appendLine("  <circle cx=\"${xForM(pt.value)}\" cy=\"${yForZ(pt.zMeters)}\" r=\"4\" fill=\"#ef4444\"/>")
            }
        }

        // 2. Enveloppe linéaire (Vert)
        if (momentEnvelope.linearEnvelopeCurve.size >= 2) {
            val pathData = buildPolylinePath(momentEnvelope.linearEnvelopeCurve.map { Pair(xForM(it.value), yForZ(it.zMeters)) })
            sb.appendLine("  <path d=\"$pathData\" fill=\"none\" stroke=\"#10b981\" stroke-width=\"2\" stroke-dasharray=\"4,4\"/>")
        }

        // 3. Enveloppe de calcul décalée de hcr (Bleu - Robuste continue)
        if (momentEnvelope.shiftedDesignEnvelopeCurve.size >= 2) {
            val pathData = buildPolylinePath(momentEnvelope.shiftedDesignEnvelopeCurve.map { Pair(xForM(it.value), yForZ(it.zMeters)) })
            sb.appendLine("  <path d=\"$pathData\" fill=\"none\" stroke=\"#1d4ed8\" stroke-width=\"3\"/>")
            for (pt in momentEnvelope.shiftedDesignEnvelopeCurve) {
                sb.appendLine("  <circle cx=\"${xForM(pt.value)}\" cy=\"${yForZ(pt.zMeters)}\" r=\"4\" fill=\"#1d4ed8\"/>")
            }
        }

        // Légende en bas
        val legY = svgHeight - 22
        sb.appendLine("  <circle cx=\"${marginLeft + 30}\" cy=\"$legY\" r=\"5\" fill=\"#ef4444\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 42}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">1. Analyse ETABS</text>")

        sb.appendLine("  <line x1=\"${marginLeft + 180}\" y1=\"$legY\" x2=\"${marginLeft + 205}\" y2=\"$legY\" stroke=\"#10b981\" stroke-width=\"2\" stroke-dasharray=\"4,4\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 215}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">2. Enveloppe Linéaire</text>")

        sb.appendLine("  <line x1=\"${marginLeft + 360}\" y1=\"$legY\" x2=\"${marginLeft + 390}\" y2=\"$legY\" stroke=\"#1d4ed8\" stroke-width=\"3\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 400}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#1d4ed8\">3. Enveloppe Décalée (hcr)</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }

    /**
     * Génère le graphique vectoriel SVG de l'enveloppe des efforts tranchants T(z).
     */
    fun renderShearEnvelopeSvg(
        shearEnvelope: WallShearEnvelope,
        svgWidth: Int = 800,
        svgHeight: Int = 500
    ): String {
        val marginLeft = 90.0
        val marginRight = 50.0
        val marginTop = 60.0
        val marginBottom = 70.0

        val plotWidth = svgWidth - marginLeft - marginRight
        val plotHeight = svgHeight - marginTop - marginBottom

        val maxZ = max(1.0, shearEnvelope.totalHeightHwMeters)
        val allShears = shearEnvelope.piecewiseDesignEnvelopeCurve.map { it.value } +
                shearEnvelope.amplifiedCurve.map { it.value } +
                shearEnvelope.analysisCurve.map { it.value }
        val maxV = max(100.0, (allShears.maxOrNull() ?: 1000.0) * 1.15)
        val minV = min(0.0, (allShears.minOrNull() ?: 0.0) * 1.10)
        val rangeV = max(1.0, maxV - minV)

        fun xForV(v: Double): Double = marginLeft + ((v - minV) / rangeV) * plotWidth
        fun yForZ(z: Double): Double = marginTop + plotHeight - (z / maxZ) * plotHeight

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"30\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#1e293b\">")
        sb.appendLine("    Enveloppe des Efforts Tranchants T(z) - Voile ${shearEnvelope.pierName} (RPA 2024 Fig. 7.15)")
        sb.appendLine("  </text>")

        // Sous-titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"48\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Vu base = ${shearEnvelope.baseShearVuKN} kN | Vbar = ${shearEnvelope.amplifiedBaseShearVBarKN} kN (1.4*Vu) | Palier sommet = ${shearEnvelope.topShearLimitKN} kN")
        sb.appendLine("  </text>")

        // Grille Z
        val numZSteps = 6
        for (i in 0..numZSteps) {
            val z = (i.toDouble() / numZSteps) * maxZ
            val y = yForZ(z)
            sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$y\" x2=\"${marginLeft + plotWidth}\" y2=\"$y\" stroke=\"#f1f5f9\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"${marginLeft - 10}\" y=\"${y + 4}\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">${round(z * 10) / 10} m</text>")
        }

        // Grille V
        val numVSteps = 5
        for (i in 0..numVSteps) {
            val v = minV + (i.toDouble() / numVSteps) * rangeV
            val x = xForV(v)
            sb.appendLine("  <line x1=\"$x\" y1=\"$marginTop\" x2=\"$x\" y2=\"${marginTop + plotHeight}\" stroke=\"#f1f5f9\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"$x\" y=\"${marginTop + plotHeight + 20}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">${v.toInt()}</text>")
        }

        // Ligne hw/3
        val yHThird = yForZ(shearEnvelope.totalHeightHwMeters / 3.0)
        sb.appendLine("  <line x1=\"$marginLeft\" y1=\"$yHThird\" x2=\"${marginLeft + plotWidth}\" y2=\"$yHThird\" stroke=\"#cbd5e1\" stroke-width=\"1.2\" stroke-dasharray=\"3,3\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 10}\" y=\"${yHThird - 6}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#475569\">hw / 3 = ${round(shearEnvelope.totalHeightHwMeters / 3.0 * 10) / 10} m</text>")

        // 1. Analyse (Rouge)
        if (shearEnvelope.analysisCurve.size >= 2) {
            val pathData = buildPolylinePath(shearEnvelope.analysisCurve.map { Pair(xForV(it.value), yForZ(it.zMeters)) })
            sb.appendLine("  <path d=\"$pathData\" fill=\"none\" stroke=\"#ef4444\" stroke-width=\"2\" stroke-dasharray=\"5,3\"/>")
        }

        // 2. Amplifiée 1.4*Vu (Vert)
        if (shearEnvelope.amplifiedCurve.size >= 2) {
            val pathData = buildPolylinePath(shearEnvelope.amplifiedCurve.map { Pair(xForV(it.value), yForZ(it.zMeters)) })
            sb.appendLine("  <path d=\"$pathData\" fill=\"none\" stroke=\"#10b981\" stroke-width=\"2\" stroke-dasharray=\"4,4\"/>")
        }

        // 3. Enveloppe de calcul par morceaux (Bleu)
        if (shearEnvelope.piecewiseDesignEnvelopeCurve.size >= 2) {
            val pathData = buildPolylinePath(shearEnvelope.piecewiseDesignEnvelopeCurve.map { Pair(xForV(it.value), yForZ(it.zMeters)) })
            sb.appendLine("  <path d=\"$pathData\" fill=\"none\" stroke=\"#2563eb\" stroke-width=\"3\"/>")
            for (pt in shearEnvelope.piecewiseDesignEnvelopeCurve) {
                sb.appendLine("  <circle cx=\"${xForV(pt.value)}\" cy=\"${yForZ(pt.zMeters)}\" r=\"4\" fill=\"#2563eb\"/>")
            }
        }

        // Légende
        val legY = svgHeight - 22
        sb.appendLine("  <line x1=\"${marginLeft + 20}\" y1=\"$legY\" x2=\"${marginLeft + 45}\" y2=\"$legY\" stroke=\"#ef4444\" stroke-width=\"2\" stroke-dasharray=\"5,3\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 55}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">1. Analyse ETABS</text>")

        sb.appendLine("  <line x1=\"${marginLeft + 190}\" y1=\"$legY\" x2=\"${marginLeft + 215}\" y2=\"$legY\" stroke=\"#10b981\" stroke-width=\"2\" stroke-dasharray=\"4,4\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 225}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">2. Amplifié 1.4*T(z)</text>")

        sb.appendLine("  <line x1=\"${marginLeft + 370}\" y1=\"$legY\" x2=\"${marginLeft + 400}\" y2=\"$legY\" stroke=\"#2563eb\" stroke-width=\"3\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 410}\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#2563eb\">3. Enveloppe Décalée par Morceaux</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }

    /**
     * Génère la coupe transversale détaillée de ferraillage du voile (Style Page 38/40).
     */
    fun renderWallCrossSectionSvg(
        pierName: String,
        lengthLwMeters: Double,
        thicknessBwMeters: Double,
        boundaryLengthLcMeters: Double,
        boundaryRebarDesc: String,
        webRebarDesc: String,
        tieSpacingStCm: Double,
        svgWidth: Int = 900,
        svgHeight: Int = 340
    ): String {
        val marginLeft = 60.0
        val marginRight = 60.0
        val marginTop = 70.0
        val plotWidth = svgWidth - marginLeft - marginRight
        val wallHeightPixels = 120.0

        val scaleX = plotWidth / lengthLwMeters
        val wallX = marginLeft
        val wallY = marginTop + 40.0
        val wallW = lengthLwMeters * scaleX
        val lcPixels = boundaryLengthLcMeters * scaleX

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"32\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#0f172a\">")
        sb.appendLine("    Coupe Transversale de Ferraillage du Voile : $pierName (RPA 2024 § 7.7.4)")
        sb.appendLine("  </text>")
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"52\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Dimensions : lw = ${lengthLwMeters} m × bw = ${thicknessBwMeters} m | Rives lc = ${boundaryLengthLcMeters} m ($boundaryRebarDesc, St = ${tieSpacingStCm.toInt()} cm)")
        sb.appendLine("  </text>")

        // Béton du voile (Rectangle principal)
        sb.appendLine("  <rect x=\"$wallX\" y=\"$wallY\" width=\"$wallW\" height=\"$wallHeightPixels\" fill=\"#f8fafc\" stroke=\"#1e293b\" stroke-width=\"2.5\" rx=\"2\"/>")

        // 1. Élément de rive GAUCHE (fond teinté)
        sb.appendLine("  <rect x=\"$wallX\" y=\"$wallY\" width=\"$lcPixels\" height=\"$wallHeightPixels\" fill=\"#fee2e2\" fill-opacity=\"0.45\" stroke=\"#dc2626\" stroke-width=\"1.5\" stroke-dasharray=\"4,3\"/>")
        // Cadre de rive gauche
        val tiePad = 8.0
        sb.appendLine("  <rect x=\"${wallX + tiePad}\" y=\"${wallY + tiePad}\" width=\"${lcPixels - 2 * tiePad}\" height=\"${wallHeightPixels - 2 * tiePad}\" fill=\"none\" stroke=\"#dc2626\" stroke-width=\"2\" rx=\"4\"/>")
        // Barres verticales rive gauche (4 coins + barres intermédiaires)
        val numBarsRive = 8
        val stepXRive = (lcPixels - 2 * tiePad) / (numBarsRive / 2 - 1)
        for (i in 0 until (numBarsRive / 2)) {
            val bx = wallX + tiePad + i * stepXRive
            sb.appendLine("  <circle cx=\"$bx\" cy=\"${wallY + tiePad + 2}\" r=\"4.5\" fill=\"#b91c1c\"/>")
            sb.appendLine("  <circle cx=\"$bx\" cy=\"${wallY + wallHeightPixels - tiePad - 2}\" r=\"4.5\" fill=\"#b91c1c\"/>")
        }

        // 2. Élément de rive DROITE (fond teinté)
        val rightX = wallX + wallW - lcPixels
        sb.appendLine("  <rect x=\"$rightX\" y=\"$wallY\" width=\"$lcPixels\" height=\"$wallHeightPixels\" fill=\"#fee2e2\" fill-opacity=\"0.45\" stroke=\"#dc2626\" stroke-width=\"1.5\" stroke-dasharray=\"4,3\"/>")
        // Cadre de rive droite
        sb.appendLine("  <rect x=\"${rightX + tiePad}\" y=\"${wallY + tiePad}\" width=\"${lcPixels - 2 * tiePad}\" height=\"${wallHeightPixels - 2 * tiePad}\" fill=\"none\" stroke=\"#dc2626\" stroke-width=\"2\" rx=\"4\"/>")
        for (i in 0 until (numBarsRive / 2)) {
            val bx = rightX + tiePad + i * stepXRive
            sb.appendLine("  <circle cx=\"$bx\" cy=\"${wallY + tiePad + 2}\" r=\"4.5\" fill=\"#b91c1c\"/>")
            sb.appendLine("  <circle cx=\"$bx\" cy=\"${wallY + wallHeightPixels - tiePad - 2}\" r=\"4.5\" fill=\"#b91c1c\"/>")
        }

        // 3. Zone Centrale (Âme du voile)
        val webStartX = wallX + lcPixels
        val webWidth = wallW - 2 * lcPixels
        if (webWidth > 20.0) {
            val numWebCols = max(3, (webWidth / 55.0).toInt())
            val stepWeb = webWidth / (numWebCols + 1)

            // Lignes longitudinales horizontales de l'âme
            sb.appendLine("  <line x1=\"$webStartX\" y1=\"${wallY + tiePad + 2}\" x2=\"${webStartX + webWidth}\" y2=\"${wallY + tiePad + 2}\" stroke=\"#2563eb\" stroke-width=\"1.8\"/>")
            sb.appendLine("  <line x1=\"$webStartX\" y1=\"${wallY + wallHeightPixels - tiePad - 2}\" x2=\"${webStartX + webWidth}\" y2=\"${wallY + wallHeightPixels - tiePad - 2}\" stroke=\"#2563eb\" stroke-width=\"1.8\"/>")

            for (i in 1..numWebCols) {
                val wx = webStartX + i * stepWeb
                // Épingle reliant les deux nappes
                sb.appendLine("  <line x1=\"$wx\" y1=\"${wallY + tiePad + 2}\" x2=\"$wx\" y2=\"${wallY + wallHeightPixels - tiePad - 2}\" stroke=\"#64748b\" stroke-width=\"1.5\" stroke-dasharray=\"2,2\"/>")
                sb.appendLine("  <circle cx=\"$wx\" cy=\"${wallY + tiePad + 2}\" r=\"4\" fill=\"#1d4ed8\"/>")
                sb.appendLine("  <circle cx=\"$wx\" cy=\"${wallY + wallHeightPixels - tiePad - 2}\" r=\"4\" fill=\"#1d4ed8\"/>")
            }
        }

        // Cotations
        val dimYTop = wallY - 14.0
        // Cotation lc gauche
        sb.appendLine("  <line x1=\"$wallX\" y1=\"$dimYTop\" x2=\"${wallX + lcPixels}\" y2=\"$dimYTop\" stroke=\"#b91c1c\" stroke-width=\"1.5\"/>")
        sb.appendLine("  <text x=\"${wallX + lcPixels / 2}\" y=\"${dimYTop - 4}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#b91c1c\">lc = ${boundaryLengthLcMeters}m</text>")

        // Cotation âme centrale
        sb.appendLine("  <line x1=\"$webStartX\" y1=\"$dimYTop\" x2=\"${webStartX + webWidth}\" y2=\"$dimYTop\" stroke=\"#1d4ed8\" stroke-width=\"1.5\"/>")
        val webLength = max(0.0, lengthLwMeters - 2 * boundaryLengthLcMeters)
        sb.appendLine("  <text x=\"${webStartX + webWidth / 2}\" y=\"${dimYTop - 4}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#1d4ed8\">Âme = ${round(webLength * 100) / 100}m</text>")

        // Cotation lc droite
        sb.appendLine("  <line x1=\"$rightX\" y1=\"$dimYTop\" x2=\"${rightX + lcPixels}\" y2=\"$dimYTop\" stroke=\"#b91c1c\" stroke-width=\"1.5\"/>")
        sb.appendLine("  <text x=\"${rightX + lcPixels / 2}\" y=\"${dimYTop - 4}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#b91c1c\">lc = ${boundaryLengthLcMeters}m</text>")

        // Cotation globale lw en bas
        val dimYBottom = wallY + wallHeightPixels + 25.0
        sb.appendLine("  <line x1=\"$wallX\" y1=\"$dimYBottom\" x2=\"${wallX + wallW}\" y2=\"$dimYBottom\" stroke=\"#0f172a\" stroke-width=\"1.8\"/>")
        sb.appendLine("  <line x1=\"$wallX\" y1=\"${dimYBottom - 6}\" x2=\"$wallX\" y2=\"${dimYBottom + 6}\" stroke=\"#0f172a\" stroke-width=\"1.8\"/>")
        sb.appendLine("  <line x1=\"${wallX + wallW}\" y1=\"${dimYBottom - 6}\" x2=\"${wallX + wallW}\" y2=\"${dimYBottom + 6}\" stroke=\"#0f172a\" stroke-width=\"1.8\"/>")
        sb.appendLine("  <text x=\"${wallX + wallW / 2}\" y=\"${dimYBottom + 16}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"13\" font-weight=\"bold\" fill=\"#0f172a\">Longueur Totale lw = ${lengthLwMeters} m (Epaisseur bw = ${thicknessBwMeters} m)</text>")

        // Légendes de ferraillage
        val noteY = svgHeight - 20
        sb.appendLine("  <circle cx=\"${marginLeft + 20}\" cy=\"$noteY\" r=\"5\" fill=\"#b91c1c\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 32}\" y=\"${noteY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#334155\">Rives : $boundaryRebarDesc (Cadres St = ${tieSpacingStCm.toInt()} cm)</text>")

        sb.appendLine("  <circle cx=\"${marginLeft + 350}\" cy=\"$noteY\" r=\"5\" fill=\"#1d4ed8\"/>")
        sb.appendLine("  <text x=\"${marginLeft + 362}\" y=\"${noteY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#334155\">Âme : $webRebarDesc</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }

    private fun buildPolylinePath(points: List<Pair<Double, Double>>): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("M ${points[0].first} ${points[0].second} ")
        for (i in 1 until points.size) {
            sb.append("L ${points[i].first} ${points[i].second} ")
        }
        return sb.toString().trim()
    }
}
