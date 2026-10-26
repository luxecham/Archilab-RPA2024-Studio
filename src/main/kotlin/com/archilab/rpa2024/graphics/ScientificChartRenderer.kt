package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.engine.seismic.SpectrumGenerator
import com.archilab.rpa2024.engine.walls.WallEnvelopeResult
import kotlin.math.max

data class ChartPoint(val x: Double, val y: Double, val label: String? = null)

data class ChartSeries(
    val label: String,
    val color: String,
    val points: List<ChartPoint>,
    val dashed: Boolean = false,
    val strokeWidth: Double = 1.9,
    val showMarkers: Boolean = false
)

data class HorizontalMarker(val value: Double, val label: String, val color: String = "#dc2626")

data class VerticalMarker(val value: Double, val label: String, val color: String = "#9ca3af")

/**
 * Courbes reglementaires RPA 2024 : spectre de reponse avec limite minimale 0,2 A I et
 * enveloppes de calcul des voiles (moments decales de h_cr, efforts tranchants amplifies).
 * Le trace est produit a la fois en SVG et en PNG 300 DPI.
 */
class ScientificChartRenderer {

    fun renderSpectrum(
        generator: SpectrumGenerator,
        maxPeriodSeconds: Double = 4.0,
        stepSeconds: Double = 0.02,
        dpi: Double = 300.0
    ): GraphicOutput {
        val design = generator.sample(
            kind = com.archilab.rpa2024.engine.seismic.SpectrumKind.HORIZONTAL_DESIGN,
            step = stepSeconds,
            maxPeriodSeconds = maxPeriodSeconds
        )
        val elastic = generator.sample(
            kind = com.archilab.rpa2024.engine.seismic.SpectrumKind.HORIZONTAL_ELASTIC,
            step = stepSeconds,
            maxPeriodSeconds = maxPeriodSeconds
        )
        val floor = 0.2 * generator.zoneAcceleration * generator.importanceFactor
        val series = listOf(
            ChartSeries(
                label = "Spectre de calcul (RPA 2024 Éq. 3.15)",
                color = "#1d4ed8",
                points = design.points.map { ChartPoint(it.periodSeconds, it.accelerationOverG) },
                strokeWidth = 2.2
            ),
            ChartSeries(
                label = "Spectre élastique (Éq. 3.8)",
                color = "#059669",
                points = elastic.points.map { ChartPoint(it.periodSeconds, it.accelerationOverG) },
                dashed = true,
                strokeWidth = 1.4
            )
        )
        val verticalMarkers = listOfNotNull(
            design.t1Seconds.takeIf { it > 0.0 }?.let { VerticalMarker(it, "T1 = ${fmt(it, 2)} s") },
            design.t2Seconds.takeIf { it > 0.0 }?.let { VerticalMarker(it, "T2 = ${fmt(it, 2)} s") },
            design.t3Seconds.takeIf { it > 0.0 }?.let { VerticalMarker(it, "T3 = ${fmt(it, 2)} s") }
        )
        val subtitle = "A = ${fmt(generator.zoneAcceleration, 2)} · I = ${fmt(generator.importanceFactor, 2)} · " +
            "R = ${fmt(generator.behaviorFactor, 2)} · QF = ${fmt(generator.qualityFactor, 2)} · " +
            "η = ${fmt(generator.eta, 3)} · S = ${fmt(generator.spectralParams.s, 2)}"
        return renderCartesian(
            title = "Spectre de réponse RPA 2024 — direction horizontale",
            subtitle = subtitle,
            xLabel = "Période T (s)",
            yLabel = "Sa / g",
            series = series,
            xRange = 0.0..maxPeriodSeconds,
            yRange = 0.0..max(
                ChartSeriesDefaults.MINIMUM_TOP,
                max(design.points.maxOfOrNull { it.accelerationOverG } ?: 0.0,
                    elastic.points.maxOfOrNull { it.accelerationOverG } ?: 0.0) * 1.12
            ),
            horizontalMarkers = listOf(
                HorizontalMarker(floor, "0,2 · A · I = ${fmt(floor, 3)}")
            ),
            verticalMarkers = verticalMarkers,
            yDecimals = 3,
            dpi = dpi
        )
    }

    fun renderWallMomentEnvelope(
        envelope: WallEnvelopeResult,
        title: String? = null,
        dpi: Double = 300.0
    ): GraphicOutput {
        val moment = envelope.moment
        val series = listOf(
            ChartSeries(
                "Diagramme issu de l'analyse",
                "#dc2626",
                moment.analysis.map { ChartPoint(it.value, it.elevationMeters) },
                strokeWidth = 2.0,
                showMarkers = true
            ),
            ChartSeries(
                "Enveloppe linéaire",
                "#f59e0b",
                moment.linear.map { ChartPoint(it.value, it.elevationMeters) },
                dashed = true,
                strokeWidth = 1.4
            ),
            ChartSeries(
                "Enveloppe décalée de h_cr = ${fmt(moment.criticalHeightMeters, 2)} m",
                "#1d4ed8",
                moment.shifted.map { ChartPoint(it.value, it.elevationMeters) },
                strokeWidth = 2.2
            )
        )
        val annotations = listOfNotNull(
            moment.analysis.lastOrNull()?.let { ChartPoint(it.value, it.elevationMeters, "M(z) analyse") },
            moment.shifted.lastOrNull()?.let { ChartPoint(it.value, it.elevationMeters, "M calcul") }
        )
        return renderCartesian(
            title = title ?: "Enveloppe des moments — voile ${envelope.family.pierName}",
            subtitle = "Hw = ${fmt(envelope.heightMeters, 2)} m · h_cr = ${fmt(moment.criticalHeightMeters, 2)} m · " +
                "M base = ${fmt(moment.baseMomentKNm, 2)} kN.m",
            xLabel = "Moment M3 (kN.m)",
            yLabel = "Élévation z (m)",
            series = series,
            xRange = 0.0..max(1.0, (moment.shifted.maxOfOrNull { it.value } ?: 0.0) * 1.15),
            yRange = 0.0..max(1.0, envelope.heightMeters * 1.05),
            annotations = annotations,
            dpi = dpi
        )
    }

    fun renderWallShearEnvelope(
        envelope: WallEnvelopeResult,
        title: String? = null,
        dpi: Double = 300.0
    ): GraphicOutput {
        val shear = envelope.shear
        val series = listOf(
            ChartSeries(
                "Effort tranchant d'analyse",
                "#dc2626",
                shear.analysis.map { ChartPoint(it.value, it.elevationMeters) },
                strokeWidth = 2.0,
                showMarkers = true
            ),
            ChartSeries(
                "Diagramme amplifié 1,4 Vu",
                "#059669",
                shear.amplified.map { ChartPoint(it.value, it.elevationMeters) },
                strokeWidth = 1.6
            ),
            ChartSeries(
                "Enveloppe de calcul",
                "#1d4ed8",
                shear.design.map { ChartPoint(it.value, it.elevationMeters) },
                strokeWidth = 2.2
            )
        )
        return renderCartesian(
            title = title ?: "Enveloppe des efforts tranchants — voile ${envelope.family.pierName}",
            subtitle = "V base = ${fmt(shear.baseShearKN, 2)} kN · 1,4 Vu = ${fmt(shear.amplifiedBaseKN, 2)} kN · " +
                "palier haut = ${fmt(shear.topPlateauKN, 2)} kN",
            xLabel = "Effort tranchant T (kN)",
            yLabel = "Élévation z (m)",
            series = series,
            xRange = 0.0..max(1.0, (shear.design.maxOfOrNull { it.value } ?: 0.0) * 1.15),
            yRange = 0.0..max(1.0, envelope.heightMeters * 1.05),
            dpi = dpi
        )
    }

    fun renderCartesian(
        title: String,
        subtitle: String,
        xLabel: String,
        yLabel: String,
        series: List<ChartSeries>,
        xRange: ClosedFloatingPointRange<Double>,
        yRange: ClosedFloatingPointRange<Double>,
        horizontalMarkers: List<HorizontalMarker> = emptyList(),
        verticalMarkers: List<VerticalMarker> = emptyList(),
        annotations: List<ChartPoint> = emptyList(),
        yDecimals: Int = 2,
        width: Double = 1100.0,
        height: Double = 660.0,
        dpi: Double = 300.0
    ): GraphicOutput {
        val canvas = VectorCanvas(width, height)
        canvas.title(title, subtitle)
        val left = 100.0
        val top = 100.0
        val right = width - 50.0
        val bottom = height - 90.0

        val xMin = xRange.start
        val xMax = xRange.endInclusive.takeIf { it > xRange.start } ?: (xRange.start + 1.0)
        val yMin = yRange.start
        val yMax = yRange.endInclusive.takeIf { it > yRange.start } ?: (yRange.start + 1.0)

        fun px(x: Double): Double = VectorCanvas.scaleRange(x, xMin, xMax, left, right)
        fun py(y: Double): Double = VectorCanvas.scaleRange(y, yMin, yMax, bottom, top)

        // Grille et graduations.
        val xTicks = 10
        val yTicks = 8
        for (index in 0..xTicks) {
            val value = xMin + (xMax - xMin) * index / xTicks
            val x = px(value)
            canvas.line(x, top, x, bottom, if (index == 0) "#6b7280" else "#e5e7eb",
                if (index == 0) 1.1 else 0.7)
            canvas.text(x, bottom + 20.0, fmt(value, 2), 9.5, TextAnchor.MIDDLE, "#4b5563")
        }
        for (index in 0..yTicks) {
            val value = yMin + (yMax - yMin) * index / yTicks
            val y = py(value)
            canvas.line(left, y, right, y, if (index == 0) "#6b7280" else "#e5e7eb",
                if (index == 0) 1.1 else 0.7)
            canvas.text(left - 10.0, y + 3.5, fmt(value, yDecimals), 9.5, TextAnchor.END, "#4b5563")
        }

        canvas.text((left + right) / 2.0, bottom + 48.0, xLabel, 11.0, TextAnchor.MIDDLE, "#111827", bold = true)
        canvas.text(28.0, (top + bottom) / 2.0, yLabel, 11.0, TextAnchor.MIDDLE, "#111827", bold = true, italic = true)

        horizontalMarkers.forEach { marker ->
            if (marker.value in yMin..yMax) {
                val y = py(marker.value)
                canvas.line(left, y, right, y, marker.color, 1.5, doubleArrayOf(8.0, 5.0))
                canvas.text(right - 4.0, y - 6.0, marker.label, 10.0, TextAnchor.END, marker.color, bold = true)
            }
        }
        verticalMarkers.forEach { marker ->
            if (marker.value in xMin..xMax) {
                val x = px(marker.value)
                canvas.line(x, top, x, bottom, marker.color, 1.0, doubleArrayOf(4.0, 4.0))
                canvas.text(x + 4.0, top + 12.0, marker.label, 9.0, TextAnchor.START, "#4b5563")
            }
        }

        series.forEach { serie ->
            val points = serie.points
                .filter { it.x.isFinite() && it.y.isFinite() }
                .map { CanvasPoint(px(it.x), py(it.y)) }
            if (points.size >= 2) {
                canvas.polyline(
                    points, serie.color, serie.strokeWidth,
                    if (serie.dashed) doubleArrayOf(7.0, 4.0) else null
                )
            }
            if (serie.showMarkers) {
                points.forEach { point -> canvas.circle(point.x, point.y, 2.2, "#ffffff", serie.color, 1.2) }
            }
        }

        annotations.forEach { annotation ->
            if (annotation.x in xMin..xMax && annotation.y in yMin..yMax) {
                val x = px(annotation.x)
                val y = py(annotation.y)
                canvas.circle(x, y, 3.0, "#ffffff", "#111827", 1.0)
                canvas.text(x + 6.0, y - 7.0, annotation.label ?: "", 9.5, TextAnchor.START, "#111827")
            }
        }

        // Légende.
        var legendY = top + 4.0
        val legendX = right - 330.0
        series.forEach { serie ->
            canvas.line(legendX, legendY, legendX + 26.0, legendY, serie.color, 2.0,
                if (serie.dashed) doubleArrayOf(7.0, 4.0) else null)
            canvas.text(legendX + 33.0, legendY + 4.0, serie.label, 9.5, TextAnchor.START, "#374151")
            legendY += 17.0
        }

        canvas.footer("RPA 2024 · tracé ${series.size} série(s) · ${fmt(xMin, 2)} ≤ x ≤ ${fmt(xMax, 2)}")
        return GraphicOutput(
            svg = canvas.svg(),
            png = canvas.png(dpi),
            width = Math.round(canvas.width).toInt(),
            height = Math.round(canvas.height).toInt(),
            caption = title
        )
    }

    private fun fmt(value: Double, decimals: Int): String =
        String.format(java.util.Locale.ROOT, "%.${decimals}f", value).replace('.', ',')
}

private object ChartSeriesDefaults {
    const val MINIMUM_TOP = 0.35
}
