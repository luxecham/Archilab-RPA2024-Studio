package com.archilab.rpa2024.engine.secondary

import com.archilab.rpa2024.engine.geometry.Checks
import java.nio.file.Path
import java.util.Locale

/**
 * Rendu SVG autonome de l'acrotere : coupe geometrique cotee avec l'effort horizontal
 * en tete et la goutte d'eau, puis schema de ferraillage des deux nappes.
 */
class ParapetSchematicRenderer {

    fun renderGeometry(result: ParapetDesignResult): String = document(
        width = 980,
        height = 640,
        title = "Acrotère - coupe géométrique",
        body = geometryBody(result)
    )

    fun renderReinforcement(result: ParapetDesignResult): String = document(
        width = 980,
        height = 640,
        title = "Acrotère - ferraillage",
        body = reinforcementBody(result)
    )

    fun render(result: ParapetDesignResult): String {
        val body = geometryBody(result) +
            "<g transform=\"translate(0.0, 660.0)\">" + reinforcementBody(result) + "</g>"
        return document(
            width = 980,
            height = 1320,
            title = "Acrotère - note graphique complète",
            body = body
        )
    }

    fun writeGeometrySvg(result: ParapetDesignResult, path: Path): Path {
        path.toFile().writeText(renderGeometry(result), Charsets.UTF_8)
        return path
    }

    fun writeReinforcementSvg(result: ParapetDesignResult, path: Path): Path {
        path.toFile().writeText(renderReinforcement(result), Charsets.UTF_8)
        return path
    }

    fun writeSvg(result: ParapetDesignResult, path: Path): Path {
        path.toFile().writeText(render(result), Charsets.UTF_8)
        return path
    }

    private fun geometryBody(result: ParapetDesignResult): String {
        val geometry = result.geometry
        val scale = 430.0
        val baseX = 330.0
        val baseY = 520.0
        fun x(value: Double): Double = baseX + value * scale
        fun y(value: Double): Double = baseY - value * scale

        val body = StringBuilder()
        body.append(title(
            "Coupe géométrique de l'acrotère",
            "H = ${Checks.format(geometry.heightMeters)} m - e = ${Checks.format(geometry.stemThicknessMeters)} m - " +
                "alpha = ${Checks.format(geometry.slopeAngleDegrees, 2)}°"
        ))

        val points = listOf(
            0.0 to 0.0,
            geometry.stemThicknessMeters to 0.0,
            geometry.stemThicknessMeters to geometry.heightMeters,
            -geometry.corniceProjectionMeters to geometry.heightMeters,
            -geometry.corniceProjectionMeters to geometry.heightMeters - geometry.corniceStraightHeightMeters,
            0.0 to geometry.heightMeters - geometry.corniceStraightHeightMeters - geometry.corniceSlopedHeightMeters
        )
        val pointList = points.joinToString(" ") { (px, py) ->
            String.format(Locale.ROOT, "%.2f,%.2f", x(px), y(py))
        }
        body.append(
            "<polygon points=\"$pointList\" fill=\"#e5e7eb\" stroke=\"#111827\" stroke-width=\"2.2\"/>"
        )

        // Cotes principales.
        body.append(dimension(x(geometry.stemThicknessMeters) + 46.0, y(0.0), x(geometry.stemThicknessMeters) + 46.0, y(geometry.heightMeters),
            "H = ${Checks.format(geometry.heightMeters * 100.0, 0)} cm", vertical = true))
        body.append(dimension(x(0.0), y(0.0) + 34.0, x(geometry.stemThicknessMeters), y(0.0) + 34.0,
            "e = ${Checks.format(geometry.stemThicknessMeters * 100.0, 0)} cm"))
        body.append(dimension(x(-geometry.corniceProjectionMeters), y(geometry.heightMeters) - 26.0,
            x(0.0), y(geometry.heightMeters) - 26.0,
            "b_bec = ${Checks.format(geometry.corniceProjectionMeters * 100.0, 0)} cm"))
        body.append(dimension(x(-geometry.corniceProjectionMeters) - 36.0,
            y(geometry.heightMeters), x(-geometry.corniceProjectionMeters) - 36.0,
            y(geometry.heightMeters - geometry.corniceStraightHeightMeters),
            "h_b1", vertical = true))
        body.append(text(
            x(-geometry.corniceProjectionMeters / 2.0) + 4.0,
            y(geometry.heightMeters - geometry.corniceSlopedHeightMeters / 2.0) + 22.0,
            "h_b2 = ${Checks.format(geometry.corniceSlopedHeightMeters * 100.0, 0)} cm", 10, "middle", "#374151"
        ))

        // Effort horizontal en tete.
        val arrowY = y(geometry.heightMeters) - 14.0
        val arrowStart = x(-geometry.corniceProjectionMeters) - 120.0
        val arrowEnd = x(-geometry.corniceProjectionMeters) - 12.0
        body.append(line(arrowStart, arrowY, arrowEnd, arrowY, "#dc2626", 2.8))
        body.append("<polygon points=\"" +
            String.format(Locale.ROOT, "%.2f,%.2f %.2f,%.2f %.2f,%.2f", arrowEnd, arrowY, arrowEnd - 12.0, arrowY - 6.0, arrowEnd - 12.0, arrowY + 6.0) +
            "\" fill=\"#dc2626\"/>")
        body.append(text(arrowStart - 8.0, arrowY - 12.0,
            "Q = ${Checks.format(result.loads.designHorizontalQKNPerM)} kN/ml", 11, "start", "#dc2626"))

        // Goutte d'eau sous le becquet.
        val dropX = x(-geometry.corniceProjectionMeters + 0.03)
        val dropY = y(geometry.heightMeters - geometry.corniceStraightHeightMeters - geometry.corniceSlopedHeightMeters) + 18.0
        body.append("<path d=\"M %.2f %.2f C %.2f %.2f, %.2f %.2f, %.2f %.2f C %.2f %.2f, %.2f %.2f, %.2f %.2f Z\" fill=\"#93c5fd\" stroke=\"#1d4ed8\" stroke-width=\"1\"/>"
            .format(Locale.ROOT, dropX, dropY, dropX - 6.0, dropY + 12.0, dropX - 5.0, dropY + 20.0, dropX, dropY + 20.0,
                dropX + 5.0, dropY + 20.0, dropX + 6.0, dropY + 12.0, dropX, dropY))
        body.append(text(dropX + 12.0, dropY + 18.0, "goutte d'eau", 9, "start", "#1d4ed8"))

        // Lignes de sol et d'encastrement.
        body.append(line(x(-0.42), y(0.0), x(0.36), y(0.0), "#111827", 2.0))
        body.append(text(x(0.34), y(0.0) + 16.0, "plancher terrasse", 10, "end", "#111827"))

        body.append(text(60.0, 600.0,
            "S = ${Checks.format(geometry.sectionAreaM2, 4)} m² - P = " +
                "${Checks.format(geometry.developedPerimeterMeters)} m - Wp = " +
                "${Checks.format(result.loads.totalWeightWpKNPerM)} kN/ml", 11, "start", "#374151"))
        body.append(text(60.0, 618.0,
            "Fp = 4 A Cp Wp = ${Checks.format(result.loads.seismicForceFpKNPerM)} kN/ml - " +
                "Q retenu = ${Checks.format(result.loads.designHorizontalQKNPerM)} kN/ml", 11, "start", "#374151"))
        return body.toString()
    }

    private fun reinforcementBody(result: ParapetDesignResult): String {
        val geometry = result.geometry
        val scale = 430.0
        val baseX = 330.0
        val baseY = 520.0
        fun x(value: Double): Double = baseX + value * scale
        fun y(value: Double): Double = baseY - value * scale

        val body = StringBuilder()
        body.append(title(
            "Ferraillage de l'acrotère",
            "Nappe principale ${result.mainSteel.label} - répartition ${result.distributionSteel.label}"
        ))

        val points = listOf(
            0.0 to 0.0,
            geometry.stemThicknessMeters to 0.0,
            geometry.stemThicknessMeters to geometry.heightMeters,
            -geometry.corniceProjectionMeters to geometry.heightMeters,
            -geometry.corniceProjectionMeters to geometry.heightMeters - geometry.corniceStraightHeightMeters,
            0.0 to geometry.heightMeters - geometry.corniceStraightHeightMeters - geometry.corniceSlopedHeightMeters
        )
        val pointList = points.joinToString(" ") { (px, py) ->
            String.format(Locale.ROOT, "%.2f,%.2f", x(px), y(py))
        }
        body.append(
            "<polygon points=\"$pointList\" fill=\"#f9fafb\" stroke=\"#111827\" stroke-width=\"2.2\"/>"
        )

        // Nappe verticale : deux files dans l'epaisseur.
        val cover = 0.025
        val leftX = geometry.stemThicknessMeters * 0.30
        val rightX = geometry.stemThicknessMeters * 0.70
        body.append(line(x(leftX), y(cover), x(leftX), y(geometry.heightMeters - cover), "#1d4ed8", 2.4))
        body.append(line(x(rightX), y(cover), x(rightX), y(geometry.heightMeters - cover), "#1d4ed8", 2.4))
        // Nappe horizontale : traits repartis sur la hauteur.
        var height = cover + 0.05
        while (height < geometry.heightMeters - cover) {
            body.append(line(x(leftX - 0.012), y(height), x(rightX + 0.012), y(height), "#059669", 1.4))
            height += 0.07
        }

        // Barres symboliques dans le becquet.
        body.append(line(x(-geometry.corniceProjectionMeters + 0.025), y(geometry.heightMeters - 0.02),
            x(geometry.stemThicknessMeters - 0.02), y(geometry.heightMeters - 0.02), "#dc2626", 2.0))
        body.append(line(x(-geometry.corniceProjectionMeters + 0.02), y(geometry.heightMeters - geometry.corniceStraightHeightMeters),
            x(-geometry.corniceProjectionMeters + 0.02), y(geometry.heightMeters - 0.01), "#dc2626", 2.0))

        body.append(text(x(geometry.stemThicknessMeters) + 90.0, y(geometry.heightMeters * 0.62),
            "Nappe principale", 12, "start", "#1d4ed8"))
        body.append(text(x(geometry.stemThicknessMeters) + 90.0, y(geometry.heightMeters * 0.62) + 18.0,
            "${result.mainSteel.label} - ${Checks.format(result.mainSteel.providedAsCm2PerM)} cm²/ml", 11, "start", "#1d4ed8"))
        body.append(text(x(geometry.stemThicknessMeters) + 90.0, y(geometry.heightMeters * 0.42),
            "Nappe de répartition", 12, "start", "#059669"))
        body.append(text(x(geometry.stemThicknessMeters) + 90.0, y(geometry.heightMeters * 0.42) + 18.0,
            "${result.distributionSteel.label} - ${Checks.format(result.distributionSteel.providedAsCm2PerM)} cm²/ml", 11, "start", "#059669"))
        body.append(text(x(geometry.stemThicknessMeters) + 90.0, y(geometry.heightMeters * 0.22),
            "Recouvrement et ancrage dans le plancher terrasse", 10, "start", "#374151"))

        body.append(line(x(-0.42), y(0.0), x(0.36), y(0.0), "#111827", 2.0))
        body.append(text(60.0, 610.0,
            "Section requise ${Checks.format(result.mainSteel.requiredAsCm2PerM)} cm²/ml - " +
                "minimum de non-fragilité ${Checks.format(result.mainSteel.minimumAsCm2PerM)} cm²/ml", 11, "start", "#374151"))
        return body.toString()
    }

    // ------------------------------------------------------------------ SVG helpers

    private fun document(width: Int, height: Int, title: String, body: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" " +
            "viewBox=\"0 0 $width $height\" role=\"img\" aria-label=\"${escape(title)}\">" +
            "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>" +
            "<g font-family=\"Segoe UI, Arial, sans-serif\">" + body + "</g></svg>"

    private fun title(main: String, subtitle: String): String =
        text(40.0, 42.0, main, 20, "start", "#111827", bold = true) +
            text(40.0, 64.0, subtitle, 12, "start", "#374151")

    private fun text(
        x: Double,
        y: Double,
        value: String,
        size: Int,
        anchor: String = "start",
        color: String = "#111827",
        bold: Boolean = false
    ): String = "<text x=\"%.2f\" y=\"%.2f\" font-size=\"%d\" text-anchor=\"%s\" fill=\"%s\"%s>%s</text>"
        .format(Locale.ROOT, x, y, size, anchor, color, if (bold) " font-weight=\"700\"" else "", escape(value))

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double, color: String, width: Double, dash: String? = null): String =
        "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"%s\" stroke-width=\"%.1f\"%s/>"
            .format(Locale.ROOT, x1, y1, x2, y2, color, width, if (dash != null) " stroke-dasharray=\"$dash\"" else "")

    private fun dimension(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        label: String,
        vertical: Boolean = false
    ): String {
        val arrow = 5.0
        val body = StringBuilder()
        body.append(line(x1, y1, x2, y2, "#6b7280", 1.0))
        if (vertical) {
            body.append(line(x1 - arrow, y1 + arrow, x1, y1, "#6b7280", 1.0))
            body.append(line(x1 + arrow, y1 + arrow, x1, y1, "#6b7280", 1.0))
            body.append(line(x2 - arrow, y2 - arrow, x2, y2, "#6b7280", 1.0))
            body.append(line(x2 + arrow, y2 - arrow, x2, y2, "#6b7280", 1.0))
            body.append(text(x1 - 8.0, (y1 + y2) / 2.0, label, 10, "end", "#4b5563"))
        } else {
            body.append(line(x1, y1 - arrow, x1, y1, "#6b7280", 1.0))
            body.append(line(x1, y1 + arrow, x1, y1, "#6b7280", 1.0))
            body.append(line(x2, y2 - arrow, x2, y2, "#6b7280", 1.0))
            body.append(line(x2, y2 + arrow, x2, y2, "#6b7280", 1.0))
            body.append(text((x1 + x2) / 2.0, y1 - 7.0, label, 10, "middle", "#4b5563"))
        }
        return body.toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
