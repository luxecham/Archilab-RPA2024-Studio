package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.engine.geometry.Checks
import java.nio.file.Path
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Generation des schemas de ferraillage (sections et elevations) en SVG autonome.
 * Le SVG est volontairement sans dependance graphique : il est testable sans interface
 * et directement injectable dans la note de calcul ou ouvert dans un navigateur.
 */
class FrameSchematicRenderer {

    fun renderColumn(design: ColumnDesignResult): String {
        val body = StringBuilder()
        body.append(sectionDrawing(
            widthCm = design.sectionWidthCm,
            heightCm = design.sectionDepthCm,
            effectiveDepthCm = design.effectiveDepthCm,
            coverCm = (design.sectionDepthCm - design.effectiveDepthCm).coerceAtLeast(1.0),
            compressedHeightCm = compressedHeightCm(design),
            compressionAtTop = true,
            barsPerLongFace = design.layout?.barsPerLongFace ?: 2,
            barsPerShortFace = design.layout?.barsPerShortFace ?: 2,
            barDiameterMm = design.layout?.maxDiameterMm ?: 12,
            title = "Section ${design.family.name}",
            xOffset = 40.0,
            yOffset = 70.0,
            maxWidthPx = 320.0,
            maxHeightPx = 380.0
        ))
        body.append(columnElevation(design))
        return document(
            width = 1040,
            height = 700,
            title = "Poteau ${design.family.name} - ferraillage",
            body = body.toString()
        )
    }

    fun renderBeam(design: BeamDesignResult): String {
        val governing = design.governingSection
        val compressionAtTop = governing?.position != BeamSectionPosition.SUPPORT
        val body = StringBuilder()
        body.append(sectionDrawing(
            widthCm = design.widthCm,
            heightCm = design.heightCm,
            effectiveDepthCm = design.effectiveDepthCm,
            coverCm = (design.heightCm - design.effectiveDepthCm).coerceAtLeast(1.0),
            compressedHeightCm = compressedHeightCm(design),
            compressionAtTop = compressionAtTop,
            barsPerLongFace = max(
                design.spanLayout?.totalBars ?: 2,
                design.supportLayout?.totalBars ?: 2
            ),
            barsPerShortFace = 2,
            barDiameterMm = max(
                design.spanLayout?.maxDiameterMm ?: 12,
                design.supportLayout?.maxDiameterMm ?: 12
            ),
            title = "Section ${design.family.name}",
            xOffset = 40.0,
            yOffset = 70.0,
            maxWidthPx = 300.0,
            maxHeightPx = 330.0
        ))
        body.append(beamElevation(design))
        return document(
            width = 1040,
            height = 700,
            title = "Poutre ${design.family.name} - ferraillage",
            body = body.toString()
        )
    }

    fun writeColumnSvg(design: ColumnDesignResult, path: Path): Path {
        path.toFile().writeText(renderColumn(design), Charsets.UTF_8)
        return path
    }

    fun writeBeamSvg(design: BeamDesignResult, path: Path): Path {
        path.toFile().writeText(renderBeam(design), Charsets.UTF_8)
        return path
    }

    private fun compressedHeightCm(design: ColumnDesignResult): Double {
        val bael = design.governingCase?.bael ?: return 0.0
        return when (bael.state) {
            BaelSectionState.ENTIEREMENT_TENDUE -> 0.0
            BaelSectionState.ENTIEREMENT_COMPRIMEE -> design.sectionDepthCm
            else -> (bael.neutralAxisMeters * 100.0).coerceIn(0.0, design.sectionDepthCm)
        }
    }

    private fun compressedHeightCm(design: BeamDesignResult): Double {
        val bael = design.governingSection?.bael ?: return 0.0
        return when (bael.state) {
            BaelSectionState.ENTIEREMENT_TENDUE -> 0.0
            BaelSectionState.ENTIEREMENT_COMPRIMEE -> design.heightCm
            else -> (bael.neutralAxisMeters * 100.0).coerceIn(0.0, design.heightCm)
        }
    }

    private fun sectionDrawing(
        widthCm: Double,
        heightCm: Double,
        effectiveDepthCm: Double,
        coverCm: Double,
        compressedHeightCm: Double,
        compressionAtTop: Boolean,
        barsPerLongFace: Int,
        barsPerShortFace: Int,
        barDiameterMm: Int,
        title: String,
        xOffset: Double,
        yOffset: Double,
        maxWidthPx: Double,
        maxHeightPx: Double
    ): String {
        val safeWidth = max(widthCm, 1.0)
        val safeHeight = max(heightCm, 1.0)
        val scale = min(maxWidthPx / safeWidth, maxHeightPx / safeHeight)
        val drawingWidth = safeWidth * scale
        val drawingHeight = safeHeight * scale
        val x0 = xOffset + (maxWidthPx - drawingWidth) / 2.0
        val y0 = yOffset + (maxHeightPx - drawingHeight) / 2.0
        val x1 = x0 + drawingWidth
        val y1 = y0 + drawingHeight

        val svg = StringBuilder()
        svg.append(text(xOffset, yOffset - 28.0, title, 15, weight = "bold"))
        svg.append(rect(x0, y0, drawingWidth, drawingHeight, "#ffffff", "#111827", 2.0))

        if (compressedHeightCm > 0.01) {
            val hatchHeight = min(compressedHeightCm, safeHeight) * scale
            val hatchY = if (compressionAtTop) y0 else y1 - hatchHeight
            svg.append(rect(hatchY = hatchY, x = x0, width = drawingWidth, height = hatchHeight))
        }

        // Barres : couronne peripherique symetrique.
        val radius = max(3.5, barDiameterMm / 10.0 * scale / 2.0)
        val positions = LinkedHashSet<Pair<Double, Double>>()
        val longCount = barsPerLongFace.coerceAtLeast(2)
        val shortCount = barsPerShortFace.coerceAtLeast(2)
        fun distributed(count: Int, from: Double, to: Double, fixed: Double, horizontal: Boolean) {
            for (index in 0 until count) {
                val ratio = if (count == 1) 0.5 else index.toDouble() / (count - 1)
                val position = from + (to - from) * ratio
                positions += if (horizontal) position to fixed else fixed to position
            }
        }
        distributed(longCount, x0 + coverCm * scale, x1 - coverCm * scale, y0 + coverCm * scale, true)
        distributed(longCount, x0 + coverCm * scale, x1 - coverCm * scale, y1 - coverCm * scale, true)
        val innerShort = (shortCount - 2).coerceAtLeast(0)
        if (innerShort > 0) {
            for (index in 1..innerShort) {
                val ratio = index.toDouble() / (innerShort + 1)
                val position = y0 + coverCm * scale + (drawingHeight - 2.0 * coverCm * scale) * ratio
                positions += (x0 + coverCm * scale) to position
                positions += (x1 - coverCm * scale) to position
            }
        }
        positions.forEach { (barX, barY) ->
            svg.append(circle(barX, barY, radius, "#1d4ed8"))
        }

        // Axe neutre / hauteur comprimee.
        if (compressedHeightCm > 0.01) {
            val hatchHeight = min(compressedHeightCm, safeHeight) * scale
            val hatchY = if (compressionAtTop) y0 else y1 - hatchHeight
            svg.append(line(x0 - 18.0, hatchY, x1 + 18.0, hatchY, "#b91c1c", 1.6, "6 3"))
            svg.append(text(
                x = x1 + 22.0,
                y = hatchY + 4.0,
                value = "y0 = ${Checks.format(compressedHeightCm)} cm",
                size = 12,
                color = "#b91c1c"
            ))
        }

        // Centre de gravite G.
        val gx = x0 + drawingWidth / 2.0
        val gy = y0 + drawingHeight / 2.0
        svg.append(line(gx - 9.0, gy, gx + 9.0, gy, "#059669", 1.4))
        svg.append(line(gx, gy - 9.0, gx, gy + 9.0, "#059669", 1.4))
        svg.append(text(gx + 8.0, gy - 8.0, "G", 12, color = "#059669", weight = "bold"))

        // Cotes b, h, d' et c.
        svg.append(line(x0, y1 + 22.0, x1, y1 + 22.0, "#374151", 1.0))
        svg.append(text((x0 + x1) / 2.0, y1 + 38.0, "b = ${Checks.format(widthCm)} cm", 12, anchor = "middle"))
        svg.append(line(x0 - 22.0, y0, x0 - 22.0, y1, "#374151", 1.0))
        svg.append(text(
            x = x0 - 40.0,
            y = (y0 + y1) / 2.0,
            value = "h = ${Checks.format(heightCm)} cm",
            size = 12,
            anchor = "middle",
            rotate = -90.0
        ))
        val topCover = coverCm * scale
        val bottomCover = (safeHeight - effectiveDepthCm.coerceIn(0.0, safeHeight)) * scale
        svg.append(text(x1 + 10.0, y0 + topCover + 4.0, "d' = ${Checks.format(coverCm)} cm", 11, color = "#6b7280"))
        svg.append(text(x1 + 10.0, y1 - bottomCover - 4.0, "c = ${Checks.format(safeHeight - effectiveDepthCm)} cm", 11, color = "#6b7280"))

        return svg.toString()
    }

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

    private fun rect(hatchY: Double, x: Double, width: Double, height: Double): String = String.format(
        Locale.ROOT,
        "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"url(#hatch)\" opacity=\"0.65\"/>",
        x, hatchY, width, height
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
        "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"%.1f\" fill=\"%s\" stroke=\"#0f172a\" stroke-width=\"0.8\"/>",
        cx, cy, radius, fill
    )

    private fun text(
        x: Double,
        y: Double,
        value: String,
        size: Int = 12,
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

    private fun columnElevation(design: ColumnDesignResult): String {
        val svg = StringBuilder()
        val transverse = design.transverse
        val xLeft = 560.0
        val columnWidth = 110.0
        val top = 120.0
        val bottom = 590.0
        val available = bottom - top
        val lclCm = (transverse.clearHeightMeters * 100.0).coerceAtLeast(1.0)
        val scale = available / lclCm
        val nodalHeight = min(transverse.nodalZoneLengthCm * scale, available / 2.0)
        val xRight = xLeft + columnWidth

        svg.append(text(xLeft, 78.0, "Élévation ${design.family.name}", 15, weight = "bold"))
        svg.append(rect(xLeft, top, columnWidth, available, "#ffffff", "#111827", 2.0))
        svg.append(String.format(
            Locale.ROOT,
            "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"url(#hatch)\" opacity=\"0.45\"/>",
            xLeft, top, columnWidth, nodalHeight
        ))
        svg.append(String.format(
            Locale.ROOT,
            "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" fill=\"url(#hatch)\" opacity=\"0.45\"/>",
            xLeft, bottom - nodalHeight, columnWidth, nodalHeight
        ))

        // Noeuds d'appui.
        svg.append(rect(xLeft - 40.0, top - 22.0, columnWidth + 80.0, 22.0, "#d1d5db", "#111827", 1.2))
        svg.append(rect(xLeft - 40.0, bottom, columnWidth + 80.0, 22.0, "#d1d5db", "#111827", 1.2))

        // Barres longitudinales.
        svg.append(line(xLeft + 16.0, top + 8.0, xLeft + 16.0, bottom - 8.0, "#1d4ed8", 3.0))
        svg.append(line(xRight - 16.0, top + 8.0, xRight - 16.0, bottom - 8.0, "#1d4ed8", 3.0))

        // Cadres : zone nodale puis zone courante.
        fun drawStirrups(from: Double, to: Double, spacingCm: Double) {
            val spacing = max(spacingCm * scale, 4.0)
            var y = from
            while (y <= to + 0.1) {
                svg.append(line(xLeft + 8.0, y, xRight - 8.0, y, "#374151", 1.0))
                y += spacing
            }
        }
        drawStirrups(top + 6.0, top + nodalHeight, transverse.nodalSpacingCm)
        drawStirrups(top + nodalHeight, bottom - nodalHeight, transverse.currentSpacingCm)
        drawStirrups(bottom - nodalHeight, bottom - 6.0, transverse.nodalSpacingCm)

        // Crochets d'ancrage a 90 degres.
        svg.append(String.format(
            Locale.ROOT,
            "<path d=\"M %.1f %.1f L %.1f %.1f L %.1f %.1f\" fill=\"none\" stroke=\"#1d4ed8\" stroke-width=\"2.4\"/>",
            xLeft + 16.0, top + 8.0, xLeft + 16.0, top + 24.0, xLeft + 16.0 - 22.0, top + 24.0
        ))
        svg.append(String.format(
            Locale.ROOT,
            "<path d=\"M %.1f %.1f L %.1f %.1f L %.1f %.1f\" fill=\"none\" stroke=\"#1d4ed8\" stroke-width=\"2.4\"/>",
            xRight - 16.0, bottom - 8.0, xRight - 16.0, bottom - 24.0, xRight - 16.0 + 22.0, bottom - 24.0
        ))

        // Cotes.
        svg.append(line(xLeft - 55.0, top, xLeft - 55.0, bottom, "#374151", 1.0))
        svg.append(text(
            x = xLeft - 68.0,
            y = (top + bottom) / 2.0,
            value = "lcl = ${Checks.format(transverse.clearHeightMeters, 2)} m",
            size = 12,
            anchor = "middle",
            rotate = -90.0
        ))
        svg.append(line(xRight + 40.0, top, xRight + 40.0, top + nodalHeight, "#b91c1c", 1.4))
        svg.append(text(
            x = xRight + 48.0,
            y = top + nodalHeight / 2.0 + 4.0,
            value = "Lcr = ${Checks.format(transverse.nodalZoneLengthCm)} cm",
            size = 11,
            color = "#b91c1c"
        ))
        svg.append(line(xRight + 40.0, bottom - nodalHeight, xRight + 40.0, bottom, "#b91c1c", 1.4))
        svg.append(text(
            x = xRight + 48.0,
            y = bottom - nodalHeight / 2.0 + 4.0,
            value = "Lcr = ${Checks.format(transverse.nodalZoneLengthCm)} cm",
            size = 11,
            color = "#b91c1c"
        ))
        svg.append(text(
            x = xLeft,
            y = bottom + 52.0,
            value = "Cadres ${transverse.stirrupLabel} : t = ${Checks.format(transverse.nodalSpacingCm)} cm " +
                "(nodale), t' = ${Checks.format(transverse.currentSpacingCm)} cm (courante)",
            size = 12
        ))
        svg.append(text(
            x = xLeft,
            y = bottom + 72.0,
            value = "Lrec = ${Checks.format(transverse.lapLengthCm)} cm ; ancrage 90° ≥ " +
                "${Checks.format(transverse.anchorageLengthCm)} cm",
            size = 12
        ))
        svg.append(text(
            x = xLeft,
            y = bottom + 92.0,
            value = "Cadres fermés par crochets à 135° (10 φt ≥ 10 cm)",
            size = 12
        ))
        return svg.toString()
    }

    private fun beamElevation(design: BeamDesignResult): String {
        val svg = StringBuilder()
        val transverse = design.transverse
        val supportLeft = 560.0
        val supportWidth = 70.0
        val beamLeft = supportLeft + supportWidth
        val beamRight = 980.0
        val beamTop = 300.0
        val beamBottom = 400.0
        val spanMeters = max(design.family.maxClearSpanMeters, 0.5)
        val scale = (beamRight - beamLeft) / (spanMeters * 100.0)
        val nodalLengthPx = min(transverse.nodalZoneLengthCm * scale, beamRight - beamLeft)

        svg.append(text(supportLeft, 78.0, "Élévation ${design.family.name}", 15, weight = "bold"))
        svg.append(rect(supportLeft, 140.0, supportWidth, 420.0, "#e5e7eb", "#111827", 2.0))
        svg.append(text(supportLeft + supportWidth / 2.0, 130.0, "Poteau", 11, anchor = "middle"))
        svg.append(rect(beamLeft, beamTop, beamRight - beamLeft, beamBottom - beamTop, "#ffffff", "#111827", 2.0))
        svg.append(rect(beamLeft, beamTop, nodalLengthPx, beamBottom - beamTop, "#fee2e2", "#b91c1c", 1.0))
        svg.append(rect(beamRight - nodalLengthPx, beamTop, nodalLengthPx, beamBottom - beamTop, "#fee2e2", "#b91c1c", 1.0))

        // Premier cadre a 5 cm du nu de l'appui.
        val firstStirrupX = beamLeft + transverse.firstStirrupCm * scale
        svg.append(line(firstStirrupX, beamTop + 4.0, firstStirrupX, beamBottom - 4.0, "#b91c1c", 2.0))

        // Cadres schematiques dans les zones nodales et courante.
        fun drawStirrups(fromX: Double, toX: Double, spacingCm: Double) {
            val spacing = max(spacingCm * scale, 5.0)
            var x = fromX
            while (x <= toX + 0.1) {
                svg.append(line(x, beamTop + 4.0, x, beamBottom - 4.0, "#6b7280", 0.8))
                x += spacing
            }
        }
        drawStirrups(firstStirrupX, beamLeft + nodalLengthPx, transverse.nodalSpacingCm)
        drawStirrups(beamLeft + nodalLengthPx, beamRight - nodalLengthPx, transverse.currentSpacingCm)
        drawStirrups(beamRight - nodalLengthPx, beamRight - transverse.firstStirrupCm * scale, transverse.nodalSpacingCm)

        // Barres longitudinales.
        val barY = beamTop + 14.0
        val bottomBarY = beamBottom - 14.0
        svg.append(line(beamLeft + 8.0, barY, beamRight - 8.0, barY, "#1d4ed8", 2.4))
        svg.append(line(beamLeft + 8.0, bottomBarY, beamRight - 8.0, bottomBarY, "#1d4ed8", 2.4))

        // Cote de la zone nodale l'.
        svg.append(line(beamLeft, beamTop - 22.0, beamLeft + nodalLengthPx, beamTop - 22.0, "#b91c1c", 1.4))
        svg.append(line(beamLeft, beamTop - 28.0, beamLeft, beamTop - 16.0, "#b91c1c", 1.4))
        svg.append(line(beamLeft + nodalLengthPx, beamTop - 28.0, beamLeft + nodalLengthPx, beamTop - 16.0, "#b91c1c", 1.4))
        svg.append(text(
            x = beamLeft + nodalLengthPx / 2.0,
            y = beamTop - 30.0,
            value = "l' = ${Checks.format(transverse.nodalZoneLengthCm)} cm",
            size = 11,
            anchor = "middle",
            color = "#b91c1c"
        ))

        svg.append(text(
            x = supportLeft,
            y = beamBottom + 52.0,
            value = "Premier cadre à ${Checks.format(transverse.firstStirrupCm)} cm ; " +
                "S = ${Checks.format(transverse.nodalSpacingCm)} cm (nodale) ; " +
                "S' = ${Checks.format(transverse.currentSpacingCm)} cm (courante)",
            size = 12
        ))
        svg.append(text(
            x = supportLeft,
            y = beamBottom + 72.0,
            value = "Cadres ${transverse.stirrupLabel} ; At = ${Checks.format(transverse.stirrupAreaCm2)} cm² ; " +
                "Lrec = ${Checks.format(transverse.lapLengthCm)} cm",
            size = 12
        ))
        svg.append(text(
            x = supportLeft,
            y = beamBottom + 92.0,
            value = "Portée libre max = ${Checks.format(spanMeters, 2)} m ; " +
                "As,inf = ${Checks.format(design.bottomAdoptedAsCm2)} cm² ; " +
                "As,sup = ${Checks.format(design.topAdoptedAsCm2)} cm²",
            size = 12
        ))
        svg.append(text(
            x = supportLeft,
            y = beamBottom + 112.0,
            value = "Zone critique lcr = 1,5 h = ${Checks.format(transverse.criticalZoneLengthCm)} cm ; " +
                "zone nodale l' = 2 h = ${Checks.format(transverse.nodalZoneLengthCm)} cm",
            size = 12
        ))
        return svg.toString()
    }

    private fun document(width: Int, height: Int, title: String, body: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" ")
        append("viewBox=\"0 0 $width $height\" role=\"img\" aria-label=\"${escape(title)}\">\n")
        append("  <title>${escape(title)}</title>\n")
        append("  <defs>\n")
        append("    <pattern id=\"hatch\" width=\"9\" height=\"9\" patternUnits=\"userSpaceOnUse\" ")
        append("patternTransform=\"rotate(45)\">\n")
        append("      <rect width=\"9\" height=\"9\" fill=\"#fff1f2\"/>\n")
        append("      <line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"9\" stroke=\"#dc2626\" stroke-width=\"2\"/>\n")
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
}
