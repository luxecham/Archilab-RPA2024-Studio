package com.archilab.rpa2024.graphics

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Ancrage horizontal d'un texte vectoriel. */
enum class TextAnchor(val svg: String) {
    START("start"),
    MIDDLE("middle"),
    END("end")
}

data class CanvasPoint(val x: Double, val y: Double)

/**
 * Canevas vectoriel hors-ecran. La meme scene est restituee soit en SVG, soit en PNG
 * par Java2D a la resolution demandee (300 DPI par defaut). Aucune dependance graphique
 * externe : le rendu est identique en test headless et dans l'application Windows.
 */
class VectorCanvas(val width: Double, val height: Double) {

    private sealed interface Shape {
        data class Line(
            val x1: Double, val y1: Double, val x2: Double, val y2: Double,
            val color: String, val strokeWidth: Double, val dash: DoubleArray?
        ) : Shape

        data class Rect(
            val x: Double, val y: Double, val w: Double, val h: Double,
            val fill: String?, val stroke: String?, val strokeWidth: Double
        ) : Shape

        data class Poly(
            val points: List<CanvasPoint>, val closed: Boolean,
            val fill: String?, val stroke: String?, val strokeWidth: Double, val dash: DoubleArray?
        ) : Shape

        data class Ellipse(
            val cx: Double, val cy: Double, val rx: Double, val ry: Double,
            val fill: String?, val stroke: String?, val strokeWidth: Double
        ) : Shape

        data class Text(
            val x: Double, val y: Double, val value: String, val size: Double,
            val anchor: TextAnchor, val color: String, val bold: Boolean, val italic: Boolean
        ) : Shape

        data class Hatch(
            val x: Double, val y: Double, val w: Double, val h: Double,
            val spacing: Double, val color: String, val angleDegrees: Double
        ) : Shape
    }

    private val shapes = mutableListOf<Shape>()
    private val defs = StringBuilder()
    private var clipCounter = 0

    // ------------------------------------------------------------------ primitives

    fun line(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String = "#111827",
        strokeWidth: Double = 1.0,
        dash: DoubleArray? = null
    ) {
        shapes += Shape.Line(x1, y1, x2, y2, color, strokeWidth, dash)
    }

    fun rect(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        fill: String? = null,
        stroke: String? = "#111827",
        strokeWidth: Double = 1.0
    ) {
        shapes += Shape.Rect(x, y, w, h, fill, stroke, strokeWidth)
    }

    fun polygon(
        points: List<CanvasPoint>,
        fill: String? = null,
        stroke: String? = "#111827",
        strokeWidth: Double = 1.0
    ) {
        if (points.size >= 3) shapes += Shape.Poly(points, true, fill, stroke, strokeWidth, null)
    }

    fun polyline(
        points: List<CanvasPoint>,
        color: String = "#111827",
        strokeWidth: Double = 1.2,
        dash: DoubleArray? = null
    ) {
        if (points.size >= 2) shapes += Shape.Poly(points, false, null, color, strokeWidth, dash)
    }

    fun circle(
        cx: Double,
        cy: Double,
        radius: Double,
        fill: String? = null,
        stroke: String? = "#111827",
        strokeWidth: Double = 1.0
    ) {
        shapes += Shape.Ellipse(cx, cy, radius, radius, fill, stroke, strokeWidth)
    }

    fun ellipse(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        fill: String? = null,
        stroke: String? = "#111827",
        strokeWidth: Double = 1.0
    ) {
        shapes += Shape.Ellipse(cx, cy, rx, ry, fill, stroke, strokeWidth)
    }

    fun text(
        x: Double,
        y: Double,
        value: String,
        size: Double = 11.0,
        anchor: TextAnchor = TextAnchor.START,
        color: String = "#111827",
        bold: Boolean = false,
        italic: Boolean = false
    ) {
        if (value.isEmpty()) return
        shapes += Shape.Text(x, y, value, size, anchor, color, bold, italic)
    }

    fun hatch(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        spacing: Double = 7.0,
        color: String = "#6b7280",
        angleDegrees: Double = 45.0
    ) {
        if (w <= 0.0 || h <= 0.0) return
        shapes += Shape.Hatch(x, y, w, h, spacing.coerceAtLeast(2.0), color, angleDegrees)
    }

    // ------------------------------------------------------------------ composites

    fun arrow(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: String = "#dc2626",
        strokeWidth: Double = 2.0,
        headSize: Double = 9.0
    ) {
        line(x1, y1, x2, y2, color, strokeWidth)
        val angle = kotlin.math.atan2(y2 - y1, x2 - x1)
        val left = CanvasPoint(
            x2 - headSize * cos(angle - Math.PI / 7.0),
            y2 - headSize * sin(angle - Math.PI / 7.0)
        )
        val right = CanvasPoint(
            x2 - headSize * cos(angle + Math.PI / 7.0),
            y2 - headSize * sin(angle + Math.PI / 7.0)
        )
        polygon(listOf(CanvasPoint(x2, y2), left, right), fill = color, stroke = color, strokeWidth = 0.5)
    }

    fun dimensionH(
        x1: Double,
        x2: Double,
        y: Double,
        label: String,
        color: String = "#4b5563",
        tick: Double = 5.0
    ) {
        line(x1, y, x2, y, color, 0.9)
        line(x1, y - tick, x1, y + tick, color, 0.9)
        line(x2, y - tick, x2, y + tick, color, 0.9)
        text((x1 + x2) / 2.0, y - 6.0, label, 9.5, TextAnchor.MIDDLE, color)
    }

    fun dimensionV(
        y1: Double,
        y2: Double,
        x: Double,
        label: String,
        color: String = "#4b5563",
        tick: Double = 5.0
    ) {
        line(x, y1, x, y2, color, 0.9)
        line(x - tick, y1, x + tick, y1, color, 0.9)
        line(x - tick, y2, x + tick, y2, color, 0.9)
        text(x - 7.0, (y1 + y2) / 2.0, label, 9.5, TextAnchor.END, color)
    }

    fun title(main: String, subtitle: String, margin: Double = 36.0) {
        text(margin, 40.0, main, 19.0, TextAnchor.START, "#111827", bold = true)
        text(margin, 61.0, subtitle, 11.0, TextAnchor.START, "#4b5563")
    }

    fun footer(value: String, margin: Double = 36.0) {
        text(margin, height - 18.0, value, 9.5, TextAnchor.START, "#6b7280")
    }

    fun note(x: Double, y: Double, value: String, color: String = "#374151", size: Double = 10.0) {
        text(x, y, value, size, TextAnchor.START, color)
    }

    fun dataRow(x: Double, y: Double, label: String, value: String, width: Double, size: Double = 10.5) {
        text(x, y, label, size, TextAnchor.START, "#4b5563")
        text(x + width, y, value, size, TextAnchor.END, "#111827", bold = true)
    }

    // ------------------------------------------------------------------ SVG

    fun svg(): String {
        val body = StringBuilder()
        shapes.forEach { shape -> body.append(svgShape(shape)) }
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${fmt(width)}\" height=\"${fmt(height)}\" " +
                    "viewBox=\"0 0 ${fmt(width)} ${fmt(height)}\" role=\"img\">"
            )
            append("<defs>").append(defs).append("</defs>")
            append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>")
            append("<g font-family=\"Segoe UI, Arial, sans-serif\">")
            append(body)
            append("</g></svg>")
        }
    }

    private fun svgShape(shape: Shape): String = when (shape) {
        is Shape.Line -> "<line x1=\"${fmt(shape.x1)}\" y1=\"${fmt(shape.y1)}\" x2=\"${fmt(shape.x2)}\" " +
            "y2=\"${fmt(shape.y2)}\" stroke=\"${shape.color}\" stroke-width=\"${fmt(shape.strokeWidth)}\"" +
            dashes(shape.dash) + "/>"

        is Shape.Rect -> "<rect x=\"${fmt(shape.x)}\" y=\"${fmt(shape.y)}\" width=\"${fmt(shape.w)}\" " +
            "height=\"${fmt(shape.h)}\"${paint(shape.fill, shape.stroke, shape.strokeWidth)}/>"

        is Shape.Poly -> {
            val points = shape.points.joinToString(" ") { "${fmt(it.x)},${fmt(it.y)}" }
            val tag = if (shape.closed) "polygon" else "polyline"
            "<$tag points=\"$points\"${paint(shape.fill, shape.stroke, shape.strokeWidth)}" +
                dashes(shape.dash) + "/>"
        }

        is Shape.Ellipse -> "<ellipse cx=\"${fmt(shape.cx)}\" cy=\"${fmt(shape.cy)}\" " +
            "rx=\"${fmt(shape.rx)}\" ry=\"${fmt(shape.ry)}\"${paint(shape.fill, shape.stroke, shape.strokeWidth)}/>"

        is Shape.Text -> buildString {
            append("<text x=\"${fmt(shape.x)}\" y=\"${fmt(shape.y)}\" font-size=\"${fmt(shape.size)}\" ")
            append("text-anchor=\"${shape.anchor.svg}\" fill=\"${shape.color}\"")
            if (shape.bold) append(" font-weight=\"700\"")
            if (shape.italic) append(" font-style=\"italic\"")
            append(">").append(escape(shape.value)).append("</text>")
        }

        is Shape.Hatch -> buildString {
            val id = "hatch${clipCounter++}"
            defs.append("<clipPath id=\"$id\"><rect x=\"${fmt(shape.x)}\" y=\"${fmt(shape.y)}\" ")
                .append("width=\"${fmt(shape.w)}\" height=\"${fmt(shape.h)}\"/></clipPath>")
            val diagonal = kotlin.math.hypot(shape.w, shape.h)
            val cx = shape.x + shape.w / 2.0
            val cy = shape.y + shape.h / 2.0
            val angle = Math.toRadians(shape.angleDegrees)
            val dx = cos(angle) * diagonal
            val dy = sin(angle) * diagonal
            val steps = (shape.w + shape.h) / shape.spacing
            append("<g clip-path=\"url(#$id)\">")
            var offset = -diagonal
            var index = 0
            while (index <= steps * 2) {
                append("<line x1=\"${fmt(cx + offset * dx)}\" y1=\"${fmt(cy + offset * dy)}\" ")
                append("x2=\"${fmt(cx + offset * dx + dx)}\" y2=\"${fmt(cy + offset * dy + dy)}\" ")
                append("stroke=\"${shape.color}\" stroke-width=\"0.6\"/>")
                offset += shape.spacing / diagonal
                index++
            }
            append("</g>")
        }
    }

    private fun paint(fill: String?, stroke: String?, strokeWidth: Double): String =
        " fill=\"${fill ?: "none"}\"" +
            (stroke?.let { " stroke=\"$it\" stroke-width=\"${fmt(strokeWidth)}\"" } ?: " stroke=\"none\"")

    private fun dashes(dash: DoubleArray?): String =
        dash?.takeIf { it.isNotEmpty() }?.joinToString(",", prefix = " stroke-dasharray=\"", postfix = "\"")
            ?: ""

    // ------------------------------------------------------------------ PNG (Java2D)

    fun png(dpi: Double = 300.0): ByteArray {
        val scale = dpi / 96.0
        val imageWidth = maxOf(1, Math.round(width * scale).toInt())
        val imageHeight = maxOf(1, Math.round(height * scale).toInt())
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, imageWidth, imageHeight)
            graphics.scale(scale, scale)
            shapes.forEach { shape -> drawShape(graphics, shape) }
        } finally {
            graphics.dispose()
        }
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    private fun drawShape(graphics: java.awt.Graphics2D, shape: Shape) {
        when (shape) {
            is Shape.Line -> {
                graphics.color = color(shape.color)
                graphics.stroke = stroke(shape.strokeWidth, shape.dash)
                graphics.drawLine(px(shape.x1), px(shape.y1), px(shape.x2), px(shape.y2))
            }

            is Shape.Rect -> {
                if (shape.fill != null) {
                    graphics.color = color(shape.fill)
                    graphics.fillRect(px(shape.x), px(shape.y), px(shape.w), px(shape.h))
                }
                if (shape.stroke != null) {
                    graphics.color = color(shape.stroke)
                    graphics.stroke = stroke(shape.strokeWidth, null)
                    graphics.drawRect(px(shape.x), px(shape.y), px(shape.w), px(shape.h))
                }
            }

            is Shape.Poly -> {
                val xs = shape.points.map { px(it.x) }.toIntArray()
                val ys = shape.points.map { px(it.y) }.toIntArray()
                if (shape.fill != null) {
                    graphics.color = color(shape.fill)
                    if (shape.closed) graphics.fillPolygon(xs, ys, xs.size)
                }
                if (shape.stroke != null) {
                    graphics.color = color(shape.stroke)
                    graphics.stroke = stroke(shape.strokeWidth, shape.dash)
                    if (shape.closed) graphics.drawPolygon(xs, ys, xs.size)
                    else graphics.drawPolyline(xs, ys, xs.size)
                }
            }

            is Shape.Ellipse -> {
                val x = px(shape.cx - shape.rx)
                val y = px(shape.cy - shape.ry)
                val w = px(2 * shape.rx)
                val h = px(2 * shape.ry)
                if (shape.fill != null) {
                    graphics.color = color(shape.fill)
                    graphics.fillOval(x, y, w, h)
                }
                if (shape.stroke != null) {
                    graphics.color = color(shape.stroke)
                    graphics.stroke = stroke(shape.strokeWidth, null)
                    graphics.drawOval(x, y, w, h)
                }
            }

            is Shape.Text -> {
                val font = Font(
                    "SansSerif",
                    (if (shape.bold) Font.BOLD else Font.PLAIN) or (if (shape.italic) Font.ITALIC else 0),
                    maxOf(1, Math.round(shape.size).toInt())
                )
                graphics.font = font
                graphics.color = color(shape.color)
                val metrics = graphics.fontMetrics
                val textWidth = metrics.stringWidth(shape.value).toDouble()
                val anchorX = when (shape.anchor) {
                    TextAnchor.START -> shape.x
                    TextAnchor.MIDDLE -> shape.x - textWidth / 2.0
                    TextAnchor.END -> shape.x - textWidth
                }
                graphics.drawString(shape.value, px(anchorX).toFloat(), px(shape.y).toFloat())
            }

            is Shape.Hatch -> {
                val previousClip = graphics.clip
                graphics.clipRect(px(shape.x), px(shape.y), px(shape.w), px(shape.h))
                graphics.color = color(shape.color)
                graphics.stroke = BasicStroke(0.6f)
                val diagonal = kotlin.math.hypot(shape.w, shape.h)
                val cx = shape.x + shape.w / 2.0
                val cy = shape.y + shape.h / 2.0
                val angle = Math.toRadians(shape.angleDegrees)
                val dx = cos(angle) * diagonal
                val dy = sin(angle) * diagonal
                val steps = ((shape.w + shape.h) / shape.spacing).toInt() + 2
                var offset = -diagonal
                var index = 0
                while (index <= steps * 2) {
                    graphics.drawLine(
                        px(cx + offset * dx), px(cy + offset * dy),
                        px(cx + offset * dx + dx), px(cy + offset * dy + dy)
                    )
                    offset += shape.spacing / diagonal
                    index++
                }
                graphics.clip = previousClip
            }
        }
    }

    // ------------------------------------------------------------------ IO

    fun writeSvg(path: Path): Path {
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeText(svg(), Charsets.UTF_8)
        return path
    }

    fun writePng(path: Path, dpi: Double = 300.0): Path {
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeBytes(png(dpi))
        return path
    }

    private fun px(value: Double): Int = Math.round(value).toInt()

    private fun stroke(width: Double, dash: DoubleArray?): BasicStroke =
        if (dash != null && dash.isNotEmpty()) {
            BasicStroke(width.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash.map { it.toFloat() }.toFloatArray(), 0f)
        } else {
            BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        }

    companion object {
        fun fmt(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

        fun color(hex: String): Color {
            val normalized = hex.removePrefix("#")
            val value = normalized.toLongOrNull(16) ?: 0x111827
            return when (normalized.length) {
                6 -> Color((value or 0xFF000000).toInt(), true)
                8 -> Color(value.toInt(), true)
                else -> Color.BLACK
            }
        }

        fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun fr(value: Double, decimals: Int = 2): String =
            String.format(Locale.ROOT, "%.${decimals}f", value).replace('.', ',')

        /** Ramene une serie de valeurs dans un intervalle de dessin. */
        fun scaleRange(value: Double, sourceMin: Double, sourceMax: Double, targetMin: Double, targetMax: Double): Double {
            if (abs(sourceMax - sourceMin) < 1e-12) return (targetMin + targetMax) / 2.0
            val ratio = (value - sourceMin) / (sourceMax - sourceMin)
            return targetMin + ratio * (targetMax - targetMin)
        }

        fun niceUpperBound(value: Double, steps: Int = 5): Double {
            if (value <= 0.0) return 1.0
            val rawStep = value / steps
            val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rawStep)))
            val normalized = rawStep / magnitude
            val multiplier = when {
                normalized <= 1.0 -> 1.0
                normalized <= 2.0 -> 2.0
                normalized <= 2.5 -> 2.5
                normalized <= 5.0 -> 5.0
                else -> 10.0
            }
            return multiplier * magnitude * steps
        }

        fun clip(value: Double, minimum: Double, maximum: Double): Double =
            value.coerceIn(minimum, maximum)
    }
}
