package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.engine.frames.BaelRectangularResult
import com.archilab.rpa2024.engine.geometry.Checks
import java.nio.file.Path
import java.util.Locale
import kotlin.math.min

/**
 * Rendu SVG autonome des sections de fondation : section rectangulaire (BaelR)
 * et section en Te (BaelT), avec zone comprimee hachuree, cotes et nappes
 * d'armatures. Aucune bibliotheque graphique n'est requise.
 */
class FoundationSchematicRenderer {

    fun renderRectangular(result: BaelRectangularResult): String = document(
        width = 980,
        height = 560,
        title = "Semelle - section rectangulaire BAELR",
        body = rectangularBody(
            name = "Semelle",
            bMeters = result.section.bMeters,
            hMeters = result.section.hMeters,
            neutralAxisMeters = result.neutralAxisMeters,
            topCoverMeters = result.section.dPrimeMeters,
            bottomCoverMeters = result.section.dMeters.let { result.section.hMeters - it },
            topSteelCm2 = result.topSteelCm2,
            bottomSteelCm2 = result.bottomSteelCm2,
            momentKNm = result.momentKNm,
            model = "BaelR",
            notes = "As sup = ${Checks.format(result.topSteelCm2)} cm², " +
                "As inf = ${Checks.format(result.bottomSteelCm2)} cm²"
        )
    )

    fun renderRectangular(design: StripFootingDesign): String = document(
        width = 980,
        height = 560,
        title = "Semelle ${design.family.name} - appui BAELR",
        body = rectangularBody(
            name = design.family.name,
            bMeters = design.family.webWidthMeters,
            hMeters = design.family.totalHeightMeters,
            neutralAxisMeters = design.supportDesign.neutralAxisMeters,
            topCoverMeters = design.family.topCoverMeters,
            bottomCoverMeters = design.family.bottomCoverMeters,
            topSteelCm2 = design.supportDesign.topSteelCm2,
            bottomSteelCm2 = design.supportDesign.bottomSteelCm2,
            momentKNm = design.supportDesign.momentKNm,
            model = "BaelR - ${design.situation.label}",
            notes = "Appui : ${design.supportSteel.label} (${design.supportSteel.face}), " +
                "As requis ${Checks.format(design.supportSteel.requiredAreaCm2)} cm²"
        )
    )

    fun renderTee(result: BaelTResult): String = document(
        width = 980,
        height = 560,
        title = "Semelle - section en Té BAELT",
        body = teeBody(
            name = "Semelle",
            flangeWidthMeters = result.input.bMeters,
            webWidthMeters = result.input.b0Meters,
            totalHeightMeters = result.input.hMeters,
            tableThicknessMeters = result.input.h0Meters,
            neutralAxisMeters = result.neutralAxisY0Meters,
            topCoverMeters = result.input.dPrimeMeters,
            bottomCoverMeters = result.input.cMeters,
            tensionSteelCm2 = result.tensionSteelCm2,
            compressionSteelCm2 = result.compressionSteelCm2,
            momentKNm = result.designMomentKNm,
            model = result.caseLabel,
            notes = "A inf = ${Checks.format(result.aInfCm2)} cm², " +
                "A sup = ${Checks.format(result.aSupCm2)} cm²"
        )
    )

    fun renderTee(design: StripFootingDesign): String {
        val tee = design.spanTeeDesign
        if (tee == null) return renderRectangular(design)
        return document(
            width = 980,
            height = 560,
            title = "Semelle ${design.family.name} - travée BAELT",
            body = teeBody(
                name = design.family.name,
                flangeWidthMeters = design.family.flangeWidthMeters,
                webWidthMeters = design.family.webWidthMeters,
                totalHeightMeters = design.family.totalHeightMeters,
                tableThicknessMeters = design.family.tableThicknessMeters,
                neutralAxisMeters = tee.neutralAxisY0Meters,
                topCoverMeters = design.family.topCoverMeters,
                bottomCoverMeters = design.family.bottomCoverMeters,
                tensionSteelCm2 = tee.tensionSteelCm2,
                compressionSteelCm2 = tee.compressionSteelCm2,
                momentKNm = tee.designMomentKNm,
                model = tee.caseLabel,
                notes = "Travée ${design.spanSteel.label} : ${design.adoptedSpanModel}, " +
                    "As requis ${Checks.format(design.spanSteel.requiredAreaCm2)} cm²"
            )
        )
    }

    fun renderSection(design: StripFootingDesign): String =
        if (design.family.isTee && design.spanTeeDesign != null) {
            renderTee(design)
        } else {
            renderRectangular(design)
        }

    fun render(set: StripFootingDesignSet): String {
        if (set.footings.isEmpty()) {
            return document(980, 320, "Semelles filantes", "")
        }
        val step = 580.0
        val body = buildString {
            set.footings.forEachIndexed { index, design ->
                append(String.format(Locale.ROOT, "<g transform=\"translate(0.0, %.1f)\">", index * step))
                append(sectionBody(design))
                append("</g>")
            }
        }
        return document(
            width = 980,
            height = (100 + set.footings.size * step).toInt(),
            title = "Semelles filantes - note graphique complète",
            body = body
        )
    }

    fun writeRectangularSvg(result: BaelRectangularResult, path: Path): Path {
        path.toFile().writeText(renderRectangular(result), Charsets.UTF_8)
        return path
    }

    fun writeRectangularSvg(design: StripFootingDesign, path: Path): Path {
        path.toFile().writeText(renderRectangular(design), Charsets.UTF_8)
        return path
    }

    fun writeTeeSvg(result: BaelTResult, path: Path): Path {
        path.toFile().writeText(renderTee(result), Charsets.UTF_8)
        return path
    }

    fun writeTeeSvg(design: StripFootingDesign, path: Path): Path {
        path.toFile().writeText(renderTee(design), Charsets.UTF_8)
        return path
    }

    fun writeSectionSvg(design: StripFootingDesign, path: Path): Path {
        path.toFile().writeText(renderSection(design), Charsets.UTF_8)
        return path
    }

    fun writeSvg(set: StripFootingDesignSet, path: Path): Path {
        path.toFile().writeText(render(set), Charsets.UTF_8)
        return path
    }

    // ------------------------------------------------------------------ corps

    private fun sectionBody(design: StripFootingDesign): String =
        if (design.family.isTee && design.spanTeeDesign != null) {
            teeBody(
                name = design.family.name,
                flangeWidthMeters = design.family.flangeWidthMeters,
                webWidthMeters = design.family.webWidthMeters,
                totalHeightMeters = design.family.totalHeightMeters,
                tableThicknessMeters = design.family.tableThicknessMeters,
                neutralAxisMeters = design.spanTeeDesign.neutralAxisY0Meters,
                topCoverMeters = design.family.topCoverMeters,
                bottomCoverMeters = design.family.bottomCoverMeters,
                tensionSteelCm2 = design.spanTeeDesign.tensionSteelCm2,
                compressionSteelCm2 = design.spanTeeDesign.compressionSteelCm2,
                momentKNm = design.spanTeeDesign.designMomentKNm,
                model = "BaelT - ${design.situation.label}",
                notes = "Travée ${design.spanSteel.label} - As requis " +
                    "${Checks.format(design.spanSteel.requiredAreaCm2)} cm² ; " +
                    "transversal ${design.transverseSteel.label}"
            )
        } else {
            rectangularBody(
                name = design.family.name,
                bMeters = design.family.webWidthMeters,
                hMeters = design.family.totalHeightMeters,
                neutralAxisMeters = design.supportDesign.neutralAxisMeters,
                topCoverMeters = design.family.topCoverMeters,
                bottomCoverMeters = design.family.bottomCoverMeters,
                topSteelCm2 = design.supportDesign.topSteelCm2,
                bottomSteelCm2 = design.supportDesign.bottomSteelCm2,
                momentKNm = design.supportDesign.momentKNm,
                model = "BaelR - ${design.situation.label}",
                notes = "Appui ${design.supportSteel.label} - As requis " +
                    "${Checks.format(design.supportSteel.requiredAreaCm2)} cm² ; " +
                    "transversal ${design.transverseSteel.label}"
            )
        }

    private fun rectangularBody(
        name: String,
        bMeters: Double,
        hMeters: Double,
        neutralAxisMeters: Double,
        topCoverMeters: Double,
        bottomCoverMeters: Double,
        topSteelCm2: Double,
        bottomSteelCm2: Double,
        momentKNm: Double,
        model: String,
        notes: String
    ): String {
        val scale = min(420.0 / bMeters.coerceAtLeast(0.1), 320.0 / hMeters.coerceAtLeast(0.1))
        val baseX = 330.0
        val baseY = 470.0
        fun x(value: Double): Double = baseX + value * scale
        fun y(value: Double): Double = baseY - value * scale

        val body = StringBuilder()
        body.append(title(
            "Section rectangulaire - $name",
            "$model - b = ${Checks.format(bMeters, 2)} m, h = ${Checks.format(hMeters, 2)} m, " +
                "M = ${Checks.format(momentKNm, 1)} kN.m"
        ))
        body.append(
            "<rect x=\"${fmt(x(0.0))}\" y=\"${fmt(y(hMeters))}\" width=\"${fmt(bMeters * scale)}\" " +
                "height=\"${fmt(hMeters * scale)}\" fill=\"#f3f4f6\" stroke=\"#111827\" stroke-width=\"2.2\"/>"
        )

        val compressionAtTop = momentKNm >= 0.0
        val y0 = neutralAxisMeters.coerceIn(0.0, hMeters)
        val hatchY = if (compressionAtTop) y(hMeters) else baseY - y0 * scale
        body.append(hatchedRect(x(0.0), hatchY, bMeters * scale, y0 * scale))
        body.append(line(x(0.0) - 12.0, y(y0), x(bMeters) + 12.0, y(y0), "#2563eb", 1.4, "6 4"))
        body.append(text(x(bMeters) + 16.0, y(y0) + 4.0,
            "y0 = ${Checks.format(y0 * 100.0, 1)} cm", 10, "start", "#2563eb"))

        // Cotes.
        body.append(dimension(x(0.0), y(0.0) + 34.0, x(bMeters), y(0.0) + 34.0,
            "b = ${Checks.format(bMeters * 100.0, 0)} cm"))
        body.append(dimension(x(bMeters) + 54.0, y(0.0), x(bMeters) + 54.0, y(hMeters),
            "h = ${Checks.format(hMeters * 100.0, 0)} cm", vertical = true))
        body.append(dimension(x(0.0) - 34.0, y(0.0), x(0.0) - 34.0, y(bottomCoverMeters),
            "c", vertical = true))
        body.append(dimension(x(0.0) - 34.0, y(hMeters), x(0.0) - 34.0, y(hMeters - topCoverMeters),
            "d'", vertical = true))

        // Armatures.
        val coverY = y(bottomCoverMeters)
        val topSteelY = y(hMeters - topCoverMeters)
        body.append(steelLine(x(0.0) + 18.0, coverY, x(bMeters) - 18.0, coverY, "#1d4ed8"))
        body.append(steelLine(x(0.0) + 18.0, topSteelY, x(bMeters) - 18.0, topSteelY, "#059669"))
        body.append(text(x(bMeters) + 12.0, coverY + 16.0,
            "A inf = ${Checks.format(bottomSteelCm2)} cm²", 10, "start", "#1d4ed8"))
        body.append(text(x(bMeters) + 12.0, topSteelY - 8.0,
            "A sup = ${Checks.format(topSteelCm2)} cm²", 10, "start", "#059669"))

        body.append(text(48.0, 506.0,
            "Zone comprimée : ${if (compressionAtTop) "fibre supérieure" else "fibre inférieure"} " +
                "(hachures) - y0 = ${Checks.format(y0, 3)} m", 10, "start", "#4b5563"))
        body.append(text(48.0, 526.0, notes, 11, "start", "#374151"))
        return body.toString()
    }

    private fun teeBody(
        name: String,
        flangeWidthMeters: Double,
        webWidthMeters: Double,
        totalHeightMeters: Double,
        tableThicknessMeters: Double,
        neutralAxisMeters: Double,
        topCoverMeters: Double,
        bottomCoverMeters: Double,
        tensionSteelCm2: Double,
        compressionSteelCm2: Double,
        momentKNm: Double,
        model: String,
        notes: String
    ): String {
        val scale = min(420.0 / flangeWidthMeters.coerceAtLeast(0.1), 320.0 / totalHeightMeters.coerceAtLeast(0.1))
        val baseX = 330.0
        val baseY = 470.0
        fun x(value: Double): Double = baseX + value * scale
        fun y(value: Double): Double = baseY - value * scale

        val b = flangeWidthMeters
        val b0 = webWidthMeters
        val h = totalHeightMeters
        val h0 = tableThicknessMeters
        val webLeft = (b - b0) / 2.0
        val webRight = (b + b0) / 2.0

        val body = StringBuilder()
        body.append(title(
            "Section en Té - $name",
            "$model - B = ${Checks.format(b, 2)} m, b0 = ${Checks.format(b0, 2)} m, " +
                "h = ${Checks.format(h, 2)} m, h0 = ${Checks.format(h0, 2)} m, " +
                "M = ${Checks.format(momentKNm, 1)} kN.m"
        ))

        val points = listOf(
            0.0 to h,
            b to h,
            b to h - h0,
            webRight to h - h0,
            webRight to 0.0,
            webLeft to 0.0,
            webLeft to h - h0,
            0.0 to h - h0
        )
        val pointList = points.joinToString(" ") { (px, py) ->
            String.format(Locale.ROOT, "%.2f,%.2f", x(px), y(py))
        }
        body.append(
            "<polygon points=\"$pointList\" fill=\"#f3f4f6\" stroke=\"#111827\" stroke-width=\"2.2\"/>"
        )

        val y0 = neutralAxisMeters.coerceIn(0.0, h)
        if (y0 <= h0) {
            body.append(hatchedRect(x(0.0), y(h), b * scale, y0 * scale))
        } else {
            body.append(hatchedRect(x(0.0), y(h), b * scale, h0 * scale))
            body.append(hatchedRect(x(webLeft), y(h - h0), b0 * scale, (y0 - h0) * scale))
        }
        body.append(line(x(0.0) - 12.0, y(y0), x(b) + 12.0, y(y0), "#2563eb", 1.4, "6 4"))
        body.append(text(x(b) + 16.0, y(y0) + 4.0,
            "y0 = ${Checks.format(y0 * 100.0, 1)} cm", 10, "start", "#2563eb"))

        // Cotes.
        body.append(dimension(x(0.0), y(h) - 30.0, x(b), y(h) - 30.0,
            "B = ${Checks.format(b * 100.0, 0)} cm"))
        body.append(dimension(x(webLeft), y(0.0) + 34.0, x(webRight), y(0.0) + 34.0,
            "b0 = ${Checks.format(b0 * 100.0, 0)} cm"))
        body.append(dimension(x(b) + 54.0, y(0.0), x(b) + 54.0, y(h),
            "h = ${Checks.format(h * 100.0, 0)} cm", vertical = true))
        body.append(dimension(x(b) + 112.0, y(h - h0), x(b) + 112.0, y(h),
            "h0 = ${Checks.format(h0 * 100.0, 0)} cm", vertical = true))
        body.append(dimension(x(0.0) - 34.0, y(0.0), x(0.0) - 34.0, y(bottomCoverMeters),
            "c", vertical = true))
        body.append(dimension(x(0.0) - 34.0, y(h), x(0.0) - 34.0, y(h - topCoverMeters),
            "d'", vertical = true))

        // Armatures.
        val coverY = y(bottomCoverMeters)
        val topSteelY = y(h - topCoverMeters)
        body.append(steelLine(x(webLeft) + 14.0, coverY, x(webRight) - 14.0, coverY, "#1d4ed8"))
        body.append(steelLine(x(0.0) + 16.0, topSteelY, x(b) - 16.0, topSteelY, "#059669"))
        body.append(text(x(b) + 12.0, coverY + 16.0,
            "A inf = ${Checks.format(tensionSteelCm2)} cm²", 10, "start", "#1d4ed8"))
        body.append(text(x(b) + 12.0, topSteelY - 8.0,
            "A sup = ${Checks.format(compressionSteelCm2)} cm²", 10, "start", "#059669"))

        body.append(text(48.0, 506.0,
            "Zone comprimée hachurée : ${if (y0 <= h0) "table seule" else "table + nervure"}" +
                " - y0 = ${Checks.format(y0, 3)} m", 10, "start", "#4b5563"))
        body.append(text(48.0, 526.0, notes, 11, "start", "#374151"))
        return body.toString()
    }

    // ------------------------------------------------------------------ SVG helpers

    private fun document(width: Int, height: Int, title: String, body: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" " +
            "viewBox=\"0 0 $width $height\" role=\"img\" aria-label=\"${escape(title)}\">" +
            "<defs><pattern id=\"hatchCompression\" width=\"9\" height=\"9\" patternUnits=\"userSpaceOnUse\" " +
            "patternTransform=\"rotate(45)\"><line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"9\" stroke=\"#9ca3af\" " +
            "stroke-width=\"2.2\"/></pattern></defs>" +
            "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>" +
            "<g font-family=\"Segoe UI, Arial, sans-serif\">" + body + "</g></svg>"

    private fun title(main: String, subtitle: String): String =
        text(40.0, 42.0, main, 20, "start", "#111827", bold = true) +
            text(40.0, 66.0, subtitle, 12, "start", "#374151")

    private fun hatchedRect(x: Double, y: Double, width: Double, height: Double): String =
        "<rect x=\"${fmt(x)}\" y=\"${fmt(y)}\" width=\"${fmt(width)}\" height=\"${fmt(height)}\" " +
            "fill=\"url(#hatchCompression)\" stroke=\"#6b7280\" stroke-width=\"1\" opacity=\"0.9\"/>"

    private fun steelLine(x1: Double, y1: Double, x2: Double, y2: Double, color: String): String =
        line(x1, y1, x2, y2, color, 3.4) +
            circle(x1, y1, 3.4, color) +
            circle(x2, y2, 3.4, color)

    private fun circle(cx: Double, cy: Double, radius: Double, color: String): String =
        "<circle cx=\"${fmt(cx)}\" cy=\"${fmt(cy)}\" r=\"${fmt(radius)}\" fill=\"$color\"/>"

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

    private fun line(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String,
        width: Double,
        dash: String? = null
    ): String = "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"%s\" stroke-width=\"%.1f\"%s/>"
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

    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
