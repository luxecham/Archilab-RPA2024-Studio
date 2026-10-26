package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.engine.geometry.Checks
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max

/**
 * Rendu graphique des voiles en SVG autonome : diagrammes M(z) et T(z) avec leurs
 * enveloppes reglementaires, puis coupe horizontale du voile avec rives confinees et
 * nappes d'ame. Le SVG ne depend d'aucune bibliotheque graphique et reste testable.
 */
class WallChartAndSchematicRenderer {

    fun renderMomentChart(result: WallDesignResult): String = document(
        width = 980,
        height = 520,
        title = "Voile ${result.family.pierName} - moments flechissants M(z)",
        body = momentBody(result)
    )

    fun renderShearChart(result: WallDesignResult): String = document(
        width = 980,
        height = 520,
        title = "Voile ${result.family.pierName} - efforts tranchants T(z)",
        body = shearBody(result)
    )

    fun renderSection(result: WallDesignResult): String = document(
        width = 980,
        height = 560,
        title = "Voile ${result.family.pierName} - coupe horizontale et ferraillage",
        body = sectionBody(result)
    )

    fun render(result: WallDesignResult): String {
        val body = buildString {
            append(momentBody(result))
            append(String.format(Locale.ROOT, "<g transform=\"translate(0.0, 540.0)\">"))
            append(shearBody(result))
            append("</g>")
            append(String.format(Locale.ROOT, "<g transform=\"translate(0.0, 1080.0)\">"))
            append(sectionBody(result))
            append("</g>")
        }
        return document(
            width = 980,
            height = 1660,
            title = "Voile ${result.family.pierName} - note graphique complete",
            body = body
        )
    }

    fun writeMomentSvg(result: WallDesignResult, path: Path): Path {
        path.toFile().writeText(renderMomentChart(result), Charsets.UTF_8)
        return path
    }

    fun writeShearSvg(result: WallDesignResult, path: Path): Path {
        path.toFile().writeText(renderShearChart(result), Charsets.UTF_8)
        return path
    }

    fun writeSectionSvg(result: WallDesignResult, path: Path): Path {
        path.toFile().writeText(renderSection(result), Charsets.UTF_8)
        return path
    }

    fun writeSvg(result: WallDesignResult, path: Path): Path {
        path.toFile().writeText(render(result), Charsets.UTF_8)
        return path
    }

    // ------------------------------------------------------------------ M(z)

    private fun momentBody(result: WallDesignResult): String {
        val envelope = result.envelope
        val height = max(envelope.heightMeters, 0.1)
        val shifted = envelope.moment.shifted
        val maxValue = maxOf(
            envelope.moment.linear.maxOfOrNull { it.value } ?: 0.0,
            shifted.maxOfOrNull { it.value } ?: 0.0,
            envelope.moment.analysis.maxOfOrNull { it.value } ?: 0.0,
            1.0
        )
        val body = StringBuilder()
        body.append(titleBlock(
            "Moments fléchissants M(z)",
            "Voile ${result.family.pierName} - h_cr = " +
                "${Checks.format(envelope.criticalHeight.retainedMeters, 2)} m - " +
                "M_base = ${Checks.format(envelope.moment.baseMomentKNm)} kN.m"
        ))
        body.append(chartAxes(
            height = height,
            maxValue = maxValue,
            xLabel = "M (kN.m)",
            yLabel = "Hw (m)"
        ))

        val xOf: (Double) -> Double = { value -> xAxis(maxValue, value) }
        val yOf: (Double) -> Double = { elevation -> yAxis(height, elevation) }

        // Courbe 1 : analyse ETABS.
        body.append(polyline(
            envelope.moment.analysis.map { it.elevationMeters to it.value },
            xOf, yOf, "#6b7280", 1.6, "6 4"
        ))
        // Courbe 2 : enveloppe lineaire.
        body.append(polyline(
            envelope.moment.linear.map { it.elevationMeters to it.value },
            xOf, yOf, "#dc2626", 2.2, null
        ))
        // Courbe 3 : enveloppe decalee de h_cr.
        body.append(polyline(
            shifted.map { it.elevationMeters to it.value },
            xOf, yOf, "#1d4ed8", 2.8, null
        ))

        // Palier de la zone critique.
        val yHcr = yOf(envelope.criticalHeight.retainedMeters)
        body.append(line(plotLeft, yHcr, plotRight, yHcr, "#059669", 1.2, "4 4"))
        body.append(text(plotRight - 6.0, yHcr - 6.0, "h_cr = " +
            "${Checks.format(envelope.criticalHeight.retainedMeters, 2)} m", 11, "end", "#059669"))

        // Valeurs remarquables.
        body.append(text(xOf(envelope.moment.baseMomentKNm) + 6.0, yOf(0.0) - 8.0,
            "M_base = ${Checks.format(envelope.moment.baseMomentKNm)}", 11, color = "#1d4ed8"))
        if (envelope.moment.topMomentKNm > 1e-6) {
            body.append(text(xOf(envelope.moment.topMomentKNm) + 6.0, yOf(height) + 14.0,
                "M_sommet = ${Checks.format(envelope.moment.topMomentKNm)}", 11, color = "#dc2626"))
        }

        body.append(legend(
            listOf(
                LegendItem("#6b7280", "1 - Diagramme ETABS", "6 4"),
                LegendItem("#dc2626", "2 - Enveloppe linéaire", null),
                LegendItem("#1d4ed8", "3 - Enveloppe décalée de h_cr", null)
            )
        ))
        return body.toString()
    }

    // ------------------------------------------------------------------ T(z)

    private fun shearBody(result: WallDesignResult): String {
        val envelope = result.envelope
        val height = max(envelope.heightMeters, 0.1)
        val maxValue = maxOf(
            envelope.shear.analysis.maxOfOrNull { it.value } ?: 0.0,
            envelope.shear.amplified.maxOfOrNull { it.value } ?: 0.0,
            envelope.shear.design.maxOfOrNull { it.value } ?: 0.0,
            1.0
        )
        val body = StringBuilder()
        body.append(titleBlock(
            "Efforts tranchants T(z)",
            "Voile ${result.family.pierName} - V_base = " +
                "${Checks.format(envelope.shear.baseShearKN)} kN - 1,40 V_base = " +
                "${Checks.format(envelope.shear.amplifiedBaseKN)} kN"
        ))
        body.append(chartAxes(
            height = height,
            maxValue = maxValue,
            xLabel = "T (kN)",
            yLabel = "Hw (m)"
        ))

        val xOf: (Double) -> Double = { value -> xAxis(maxValue, value) }
        val yOf: (Double) -> Double = { elevation -> yAxis(height, elevation) }

        body.append(polyline(
            envelope.shear.analysis.map { it.elevationMeters to it.value },
            xOf, yOf, "#dc2626", 1.8, "6 4"
        ))
        body.append(polyline(
            envelope.shear.amplified.map { it.elevationMeters to it.value },
            xOf, yOf, "#059669", 2.2, null
        ))
        body.append(polyline(
            envelope.shear.design.map { it.elevationMeters to it.value },
            xOf, yOf, "#1d4ed8", 2.8, null
        ))

        body.append(text(xOf(envelope.shear.amplifiedBaseKN) + 6.0, yOf(0.0) - 8.0,
            "1,40 Vu = ${Checks.format(envelope.shear.amplifiedBaseKN)}", 11, color = "#1d4ed8"))
        body.append(text(xOf(envelope.shear.topPlateauKN) + 6.0, yOf(height) + 14.0,
            "V_barre/2 = ${Checks.format(envelope.shear.topPlateauKN)}", 11, color = "#1d4ed8"))

        body.append(legend(
            listOf(
                LegendItem("#dc2626", "1 - Diagramme ETABS", "6 4"),
                LegendItem("#059669", "2 - Diagramme amplifié 1,40 Vu", null),
                LegendItem("#1d4ed8", "3 - Enveloppe de calcul", null)
            )
        ))
        return body.toString()
    }

    // ------------------------------------------------------------ Coupe

    private fun sectionBody(result: WallDesignResult): String {
        val family = result.family
        val boundary = result.boundaryLeft
        val web = result.web
        val lwCm = family.lengthLwMeters * 100.0
        val bwCm = family.thicknessBwMeters * 100.0
        val drawingWidth = 820.0
        val scale = if (lwCm > 0.0) drawingWidth / lwCm else 1.0
        val thicknessPx = (bwCm * scale).coerceIn(24.0, 220.0)
        val x0 = 80.0
        val x1 = x0 + max(lwCm * scale, 40.0)
        val centerY = 300.0
        val y0 = centerY - thicknessPx / 2.0
        val y1 = centerY + thicknessPx / 2.0
        val lcPx = (boundary.lengthLcCm * scale).coerceIn(10.0, (x1 - x0) / 2.0)
        val webLeft = x0 + lcPx
        val webRight = x1 - lcPx

        val body = StringBuilder()
        body.append(titleBlock(
            "Coupe horizontale du voile",
            "lw = ${Checks.format(lwCm)} cm - bw = ${Checks.format(bwCm)} cm - " +
                "lc = ${Checks.format(boundary.lengthLcCm)} cm - Hw = " +
                "${Checks.format(family.totalHeightHwMeters, 2)} m"
        ))

        // Beton.
        body.append(rect(x0, y0, x1 - x0, y1 - y0, "#f8fafc", "#111827", 2.0))

        // Rives confinees.
        body.append(rect(x0, y0, lcPx, y1 - y0, "url(#wallHatch)", "#b91c1c", 1.8))
        body.append(rect(x1 - lcPx, y0, lcPx, y1 - y0, "url(#wallHatch)", "#b91c1c", 1.8))

        // Cadres serres dans les rives.
        drawStirrups(body, x0 + 6.0, x0 + lcPx - 6.0, y0 + 6.0, y1 - 6.0, boundary.stirrupSpacingCm * scale)
        drawStirrups(body, x1 - lcPx + 6.0, x1 - 6.0, y0 + 6.0, y1 - 6.0, boundary.stirrupSpacingCm * scale)

        // Barres longitudinales symboliques des rives (8 par rive).
        drawBoundaryBars(body, x0 + 9.0, y0 + 9.0, lcPx - 18.0, y1 - y0 - 18.0)
        drawBoundaryBars(body, x1 - lcPx + 9.0, y0 + 9.0, lcPx - 18.0, y1 - y0 - 18.0)

        // Nappes verticales de l'ame (deux lits).
        val layerYs = listOf(y0 + thicknessPx / 3.0, y1 - thicknessPx / 3.0)
        val svPx = (web.verticalSpacingCm * scale).coerceAtLeast(4.0)
        layerYs.forEach { layerY ->
            body.append(line(webLeft + 4.0, layerY, webRight - 4.0, layerY, "#1d4ed8", 1.6))
            var x = webLeft + 4.0
            while (x <= webRight - 4.0 + 0.1) {
                body.append(circle(x, layerY, 2.6, "#1d4ed8"))
                x += svPx
            }
        }
        // Nappes horizontales : lignes transversales espacees de Sh.
        val shPx = (web.horizontalSpacingCm * scale).coerceAtLeast(6.0)
        var xh = webLeft + 6.0
        while (xh <= webRight - 6.0 + 0.1) {
            body.append(line(xh, y0 + 6.0, xh, y1 - 6.0, "#059669", 0.8, "3 3"))
            xh += shPx
        }

        // Cotes horizontales.
        body.append(line(x0, y1 + 26.0, x1, y1 + 26.0, "#374151", 1.0))
        body.append(text((x0 + x1) / 2.0, y1 + 42.0,
            "lw = ${Checks.format(lwCm)} cm", 12, "middle"))
        body.append(dimension(x0, y0 - 22.0, x0 + lcPx, y0 - 22.0,
            "lc = ${Checks.format(boundary.lengthLcCm)} cm"))
        body.append(dimension(webLeft, y0 - 22.0, webRight, y0 - 22.0,
            "L_ame = ${Checks.format(web.lengthCm)} cm"))
        body.append(dimension(x1 - lcPx, y0 - 22.0, x1, y0 - 22.0,
            "lc = ${Checks.format(boundary.lengthLcCm)} cm"))

        // Cote verticale bw.
        body.append(line(x1 + 26.0, y0, x1 + 26.0, y1, "#374151", 1.0))
        body.append(text(x1 + 40.0, centerY + 4.0, "bw = ${Checks.format(bwCm)} cm", 12,
            anchor = "middle", rotate = 90.0))

        // Annotations.
        body.append(text(x0, y1 + 66.0,
            "Rive gauche : ${boundary.barLabel} (${Checks.format(boundary.adoptedAsCm2)} cm²) ; " +
                "cadres ${boundary.stirrupLabel} tous les ${Checks.format(boundary.stirrupSpacingCm)} cm",
            12, color = "#b91c1c"))
        body.append(text(x0, y1 + 86.0,
            "Ame : ${web.layers} nappes ${web.barLabel} ; Sv = ${Checks.format(web.verticalSpacingCm)} cm ; " +
                "Sh = ${Checks.format(web.horizontalSpacingCm)} cm ; rho_v = " +
                "${Checks.format(web.verticalRatio, 4)} ; rho_h = ${Checks.format(web.horizontalRatio, 4)}",
            12, color = "#1d4ed8"))
        body.append(text(x0, y1 + 106.0,
            "Rive droite : ${boundary.barLabel} ; noyau confine b0 x h0 = " +
                "${Checks.format(boundary.coreWidthB0Cm)} x ${Checks.format(boundary.coreHeightH0Cm)} cm ; " +
                "At = ${Checks.format(boundary.providedTransverseCm2)} cm² ≥ " +
                "${Checks.format(boundary.requiredTransverseCm2)} cm²",
            12, color = "#b91c1c"))
        body.append(text(x0, y1 + 126.0,
            "Zone critique h_cr = ${Checks.format(result.envelope.criticalHeight.retainedMeters, 2)} m ; " +
                "N = ${Checks.format(result.designAxialKN)} kN ; M = " +
                "${Checks.format(result.designMomentKNm)} kN.m ; y0 = " +
                "${Checks.format(result.bael.neutralAxisMeters * 100.0)} cm",
            12))
        body.append(text(x0, y1 + 146.0,
            "τb = ${Checks.format(web.tauMPa, 3)} MPa ≤ τadm = ${Checks.format(web.tauLimitMPa, 3)} MPa ; " +
                "lc,adm = ${Checks.format(result.ductility.admissibleConfinementLengthMeters, 3)} m",
            12))
        return body.toString()
    }

    private fun drawStirrups(
        body: StringBuilder,
        left: Double,
        right: Double,
        top: Double,
        bottom: Double,
        spacingPx: Double
    ) {
        val spacing = spacingPx.coerceAtLeast(3.0)
        var x = left
        while (x <= right + 0.1) {
            body.append(line(x, top, x, bottom, "#b91c1c", 1.2))
            x += spacing
        }
        body.append(line(left, top, right, top, "#b91c1c", 1.2))
        body.append(line(left, bottom, right, bottom, "#b91c1c", 1.2))
    }

    private fun drawBoundaryBars(
        body: StringBuilder,
        x: Double,
        y: Double,
        width: Double,
        height: Double
    ) {
        val xs = listOf(x, x + width / 2.0, x + width)
        val ys = listOf(y, y + height / 2.0, y + height)
        xs.forEachIndexed { ix, px ->
            ys.forEachIndexed { iy, py ->
                if (ix == 1 && iy == 1) return@forEachIndexed
                body.append(circle(px, py, 3.4, "#1d4ed8"))
            }
        }
    }

    // ------------------------------------------------------------ Axes

    private fun titleBlock(title: String, subtitle: String): String = buildString {
        append(text(34.0, 34.0, title, 18, weight = "bold"))
        append(text(34.0, 52.0, subtitle, 12, color = "#4b5563"))
    }

    private fun chartAxes(height: Double, maxValue: Double, xLabel: String, yLabel: String): String {
        val body = StringBuilder()
        body.append(rect(plotLeft, plotTop, plotRight - plotLeft, plotBottom - plotTop, "#ffffff", "#111827", 1.2))
        for (index in 0..4) {
            val ratio = index / 4.0
            val x = plotLeft + (plotRight - plotLeft) * ratio
            body.append(line(x, plotTop, x, plotBottom, "#e5e7eb", 0.8))
            body.append(text(x, plotBottom + 20.0, Checks.format(maxValue * ratio, 0), 10, "middle", "#4b5563"))
            val y = plotBottom - (plotBottom - plotTop) * ratio
            body.append(line(plotLeft, y, plotRight, y, "#e5e7eb", 0.8))
            body.append(text(plotLeft - 10.0, y + 4.0, Checks.format(height * ratio, 2), 10, "end", "#4b5563"))
        }
        body.append(line(plotLeft, plotTop, plotLeft, plotBottom, "#111827", 1.2))
        body.append(line(plotLeft, plotBottom, plotRight, plotBottom, "#111827", 1.2))
        body.append(text((plotLeft + plotRight) / 2.0, plotBottom + 44.0, xLabel, 12, "middle"))
        body.append(text(plotLeft - 66.0, (plotTop + plotBottom) / 2.0, yLabel, 12, "middle", rotate = -90.0))
        return body.toString()
    }

    private fun xAxis(maxValue: Double, value: Double): Double {
        val safeMax = max(maxValue, 1e-9)
        return plotLeft + (plotRight - plotLeft) * (value.coerceIn(0.0, safeMax) / safeMax)
    }

    private fun yAxis(height: Double, elevation: Double): Double {
        val safeHeight = max(height, 1e-9)
        return plotBottom - (plotBottom - plotTop) * (elevation.coerceIn(0.0, safeHeight) / safeHeight)
    }

    private fun polyline(
        points: List<Pair<Double, Double>>,
        xOf: (Double) -> Double,
        yOf: (Double) -> Double,
        color: String,
        width: Double,
        dash: String?
    ): String {
        if (points.size < 2) return ""
        val path = points.joinToString(" ") { (elevation, value) ->
            String.format(Locale.ROOT, "%.1f,%.1f", xOf(value), yOf(elevation))
        }
        val dashAttribute = dash?.let { String.format(Locale.ROOT, " stroke-dasharray=\"%s\"", it) } ?: ""
        return String.format(
            Locale.ROOT,
            "<polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%.1f\" " +
                "stroke-linecap=\"round\" stroke-linejoin=\"round\"%s/>",
            path, color, width, dashAttribute
        )
    }

    private fun legend(items: List<LegendItem>): String {
        val body = StringBuilder()
        var y = 500.0
        items.forEachIndexed { index, item ->
            val x = 210.0 + index * 250.0
            body.append(line(x, y, x + 26.0, y, item.color, 2.4, item.dash))
            body.append(text(x + 32.0, y + 4.0, item.label, 11))
        }
        return body.toString()
    }

    private data class LegendItem(val color: String, val label: String, val dash: String?)

    // ------------------------------------------------------- SVG helpers

    private fun rect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        fill: String,
        stroke: String,
        strokeWidth: Double
    ): String = String.format(
        Locale.ROOT,
        "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"%s\" stroke=\"%s\" stroke-width=\"%.1f\"/>",
        x, y, width, height, fill, stroke, strokeWidth
    )

    private fun line(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        stroke: String,
        strokeWidth: Double,
        dash: String? = null
    ): String = if (dash == null) {
        String.format(
            Locale.ROOT,
            "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"%.1f\"/>",
            x1, y1, x2, y2, stroke, strokeWidth
        )
    } else {
        String.format(
            Locale.ROOT,
            "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"%.1f\" stroke-dasharray=\"%s\"/>",
            x1, y1, x2, y2, stroke, strokeWidth, dash
        )
    }

    private fun circle(cx: Double, cy: Double, radius: Double, fill: String): String = String.format(
        Locale.ROOT,
        "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\" stroke=\"#0f172a\" stroke-width=\"0.7\"/>",
        cx, cy, radius, fill
    )

    private fun dimension(x1: Double, y: Double, x2: Double, yLabel: Double, label: String): String = buildString {
        append(line(x1, y, x2, y, "#b91c1c", 1.0))
        append(line(x1, y - 5.0, x1, y + 5.0, "#b91c1c", 1.0))
        append(line(x2, y - 5.0, x2, y + 5.0, "#b91c1c", 1.0))
        append(text((x1 + x2) / 2.0, yLabel - 7.0, label, 11, "middle", "#b91c1c"))
    }

    private fun text(
        x: Double,
        y: Double,
        value: String,
        size: Int = 11,
        anchor: String = "start",
        color: String = "#1f2937",
        weight: String = "normal",
        rotate: Double? = null
    ): String {
        val transform = if (rotate != null) {
            String.format(Locale.ROOT, " transform=\"rotate(%.1f %.1f %.1f)\"", rotate, x, y)
        } else {
            ""
        }
        return String.format(
            Locale.ROOT,
            "<text x=\"%.1f\" y=\"%.1f\" font-family=\"Segoe UI, Arial, sans-serif\" font-size=\"%d\" " +
                "font-weight=\"%s\" fill=\"%s\" text-anchor=\"%s\"%s>%s</text>",
            x, y, size, weight, color, anchor, transform, escape(value)
        )
    }

    private fun document(width: Int, height: Int, title: String, body: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" ")
        append("viewBox=\"0 0 $width $height\" role=\"img\" aria-label=\"${escape(title)}\">\n")
        append("  <title>${escape(title)}</title>\n")
        append("  <defs>\n")
        append("    <pattern id=\"wallHatch\" width=\"8\" height=\"8\" patternUnits=\"userSpaceOnUse\" ")
        append("patternTransform=\"rotate(45)\">\n")
        append("      <rect width=\"8\" height=\"8\" fill=\"#fee2e2\"/>\n")
        append("      <line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"8\" stroke=\"#dc2626\" stroke-width=\"1.6\"/>\n")
        append("    </pattern>\n")
        append("  </defs>\n")
        append("  <rect width=\"$width\" height=\"$height\" fill=\"#ffffff\"/>\n")
        append(body)
        append("\n</svg>\n")
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private companion object {
        const val plotLeft = 140.0
        const val plotRight = 920.0
        const val plotTop = 80.0
        const val plotBottom = 450.0
    }
}
