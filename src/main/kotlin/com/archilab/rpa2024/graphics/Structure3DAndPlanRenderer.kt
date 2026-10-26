package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.engine.foundations.VerticalDisplacementEnvelope
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.FrameElement
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import com.archilab.rpa2024.parser.Point3D
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Image vectorielle et matricielle produite par le moteur graphique. */
data class GraphicOutput(
    val svg: String,
    val png: ByteArray,
    val width: Int,
    val height: Int,
    val caption: String
)

private data class PlaneTransform(
    val scaleX: Double,
    val scaleY: Double,
    val offsetX: Double,
    val offsetY: Double,
    val flipY: Boolean
) {
    fun x(value: Double): Double = offsetX + value * scaleX
    fun y(value: Double): Double = if (flipY) offsetY - value * scaleY else offsetY + value * scaleY
}

/**
 * Reconstruction visuelle automatique de la structure a partir de la topologie ETABS/SAFE :
 * vue 3D isometrique, plan de coffrage par niveau et plan des fondations avec carte de
 * deplacements et diagramme des moments. Aucune coordonnee n'est codee en dur : tout est lu
 * dans les points, lignes, surfaces et files du modele importe.
 */
class Structure3DAndPlanRenderer {

    private val columnColor = "#1d4ed8"
    private val beamColor = "#059669"
    private val wallColor = "#dc2626"
    private val slabColor = "#d1d5db"

    // ------------------------------------------------------------------ vue 3D

    fun renderIsometric(
        model: ParsedEtabsModel,
        title: String = "Vue 3D isométrique — ${model.joint.jointName}",
        dpi: Double = 300.0
    ): GraphicOutput {
        val canvas = VectorCanvas(1180.0, 860.0)
        canvas.title(title, "Reconstruction automatique depuis les nœuds ETABS — ${model.frames.size} barres, " +
            "${model.areas.size} surfaces")
        if (model.points.isEmpty()) {
            canvas.text(590.0, 430.0, "Aucun nœud exploitable dans le modèle.",
                13.0, TextAnchor.MIDDLE, "#9b1c1c")
            return output(canvas, dpi, title)
        }

        val points = model.points.values
        val angle = Math.toRadians(30.0)
        fun project(point: Point3D): CanvasPoint {
            val sx = (point.xMeters - point.yMeters) * cos(angle)
            val sy = (point.xMeters + point.yMeters) * sin(angle) - point.zMeters
            return CanvasPoint(sx, sy)
        }

        val projected = points.associateWith { project(it) }
        val transform = fit(projected.values, 70.0, 90.0, 1110.0, 760.0, flipY = false)

        // Ombre de sol et grille.
        val groundPoints = points.filter { abs(it.zMeters - points.minOf { p -> p.zMeters }) < 0.01 }
        drawGroundGrid(canvas, groundPoints, transform)

        // Dalles semi-transparentes, de bas en haut.
        model.areas.filter { it.kind == AreaKind.SLAB }.sortedBy { area ->
            area.nodeIds.mapNotNull { model.points[it]?.zMeters }.maxOrNull() ?: 0.0
        }.forEach { area ->
            val polygon = area.nodeIds.mapNotNull { model.points[it] }.map { projected.getValue(it) }
                .map { CanvasPoint(transform.x(it.x), transform.y(it.y)) }
            if (polygon.size >= 3) {
                canvas.polygon(polygon, fill = "#e5e7ebcc", stroke = "#9ca3af", strokeWidth = 0.8)
            }
        }

        // Voiles.
        model.areas.filter { it.kind == AreaKind.WALL }.forEach { area ->
            val polygon = area.nodeIds.mapNotNull { model.points[it] }.map { projected.getValue(it) }
                .map { CanvasPoint(transform.x(it.x), transform.y(it.y)) }
            if (polygon.size >= 3) {
                canvas.polygon(polygon, fill = "#dc262680", stroke = wallColor, strokeWidth = 1.1)
            }
        }

        // Barres : poteaux en premier, poutres ensuite.
        model.frames.sortedBy { it.kind.ordinal }.forEach { frame ->
            val start = model.points[frame.startNodeId] ?: return@forEach
            val end = model.points[frame.endNodeId] ?: return@forEach
            val a = projected.getValue(start)
            val b = projected.getValue(end)
            when (frame.kind) {
                FrameKind.COLUMN -> canvas.line(
                    transform.x(a.x), transform.y(a.y), transform.x(b.x), transform.y(b.y),
                    columnColor, 2.4
                )
                FrameKind.BEAM -> canvas.line(
                    transform.x(a.x), transform.y(a.y), transform.x(b.x), transform.y(b.y),
                    beamColor, 1.7
                )
                FrameKind.BRACE -> canvas.line(
                    transform.x(a.x), transform.y(a.y), transform.x(b.x), transform.y(b.y),
                    "#7c3aed", 1.4
                )
                FrameKind.OTHER -> canvas.line(
                    transform.x(a.x), transform.y(a.y), transform.x(b.x), transform.y(b.y),
                    "#6b7280", 1.0
                )
            }
        }

        drawBaseBubbles(canvas, model, transform)

        // Légende.
        val legendX = 70.0
        var legendY = 800.0
        listOf(
            "Poteaux" to columnColor,
            "Poutres / chaînages" to beamColor,
            "Voiles" to wallColor,
            "Planchers" to "#9ca3af"
        ).forEach { (label, color) ->
            canvas.line(legendX, legendY - 4.0, legendX + 26.0, legendY - 4.0, color, 3.0)
            canvas.text(legendX + 34.0, legendY, label, 9.5, TextAnchor.START, "#374151")
            legendY += 16.0
        }
        canvas.text(1110.0, 800.0,
            "Lx = ${Checks.format(model.joint.gridSystem.totalLengthLx)} m · " +
                "Ly = ${Checks.format(model.joint.gridSystem.totalLengthLy)} m · " +
                "Hn = ${Checks.format(model.joint.storySystem.totalHeightHn)} m",
            9.5, TextAnchor.END, "#374151")
        canvas.footer("Emprise ${model.frames.size} barres · ${model.areas.size} surfaces · " +
            "${model.sections.size} sections · ${model.joint.storySystem.numStoriesIncludingPenthouse} niveaux")
        return output(canvas, dpi, title)
    }

    // ------------------------------------------------------------------ plan de coffrage

    fun renderPlan(
        model: ParsedEtabsModel,
        storyName: String? = null,
        title: String? = null,
        dpi: Double = 300.0
    ): GraphicOutput {
        val stories = model.joint.storySystem
        val level = storyName?.let { stories.level(it) }
            ?: stories.mainStories.lastOrNull()
            ?: stories.orderedStories.lastOrNull()
        val levelName = level?.storyName ?: storyName ?: "Niveau courant"
        val caption = title ?: "Plan de coffrage — $levelName"
        val canvas = VectorCanvas(1180.0, 900.0)
        canvas.title(caption, "Files, portées, poteaux, poutres et voiles lus dans le modèle — " +
            "cote NGF ${Checks.format(level?.elevationZk ?: 0.0)} m")

        val frames = framesAtLevel(model, levelName)
        val areas = model.areas.filter { it.storyName == null || it.storyName == levelName }
        val grid = model.joint.gridSystem

        val xs = if (grid.xGrids.isNotEmpty()) grid.xGrids.map { it.coordinateMeters }
        else model.points.values.map { it.xMeters }.distinct().sorted()
        val ys = if (grid.yGrids.isNotEmpty()) grid.yGrids.map { it.coordinateMeters }
        else model.points.values.map { it.yMeters }.distinct().sorted()
        if (xs.size < 2 || ys.size < 2) {
            canvas.text(590.0, 440.0, "Grille d'axes incomplète : plan non traçable.", 13.0,
                TextAnchor.MIDDLE, "#9b1c1c")
            return output(canvas, dpi, caption)
        }

        val transform = fit(
            xs.flatMap { x -> ys.map { y -> CanvasPoint(x, y) } },
            140.0, 110.0, 1110.0, 780.0, flipY = true
        )

        // Dalles.
        areas.filter { it.kind == AreaKind.SLAB }.forEach { area ->
            val polygon = area.nodeIds.mapNotNull { model.points[it] }
                .map { CanvasPoint(transform.x(it.xMeters), transform.y(it.yMeters)) }
            if (polygon.size >= 3) {
                canvas.polygon(polygon, fill = "#f3f4f6", stroke = "#9ca3af", strokeWidth = 0.9)
                val firstSpan = model.joint.sectionCatalog.slabs.firstOrNull { it.name == area.propertyName }
                if (firstSpan != null && polygon.size >= 4) {
                    val cx = polygon.map { it.x }.average()
                    val cy = polygon.map { it.y }.average()
                    val half = if (firstSpan.maxSpanMeters > 0.0) firstSpan.maxSpanMeters else 1.0
                    canvas.arrow(cx - 28.0, cy, cx + 28.0, cy, "#2563eb", 1.4, 6.0)
                    canvas.text(cx, cy - 7.0, "sens de portée", 8.5, TextAnchor.MIDDLE, "#2563eb")
                    canvas.text(cx, cy + 16.0,
                        "${firstSpan.name} ${Checks.format(firstSpan.totalThicknessHtCm, 0)} cm · " +
                            "L=${Checks.format(half)} m", 8.0, TextAnchor.MIDDLE, "#4b5563")
                }
            }
        }

        // Voiles.
        areas.filter { it.kind == AreaKind.WALL }.forEach { area ->
            val polygon = area.nodeIds.mapNotNull { model.points[it] }
                .map { CanvasPoint(transform.x(it.xMeters), transform.y(it.yMeters)) }
            if (polygon.size >= 3) {
                canvas.polygon(polygon, fill = "#dc262666", stroke = wallColor, strokeWidth = 2.0)
                val cx = polygon.map { it.x }.average()
                val cy = polygon.map { it.y }.average()
                val length = polygon.zipWithNext().maxOfOrNull { (a, b) ->
                    kotlin.math.hypot(b.x - a.x, b.y - a.y)
                } ?: 0.0
                if (area.pierName != null) {
                    canvas.text(cx, cy - 4.0, area.pierName, 9.0, TextAnchor.MIDDLE, wallColor, bold = true)
                }
                canvas.text(cx, cy + 10.0, "${Checks.format(length / transform.scaleX)} m", 8.0,
                    TextAnchor.MIDDLE, wallColor)
            }
        }

        // Files et cotes.
        val bottomY = 820.0
        xs.forEach { x ->
            canvas.line(transform.x(x), 110.0, transform.x(x), 780.0, "#cbd5e1", 0.6, doubleArrayOf(5.0, 4.0))
        }
        ys.forEach { y ->
            canvas.line(140.0, transform.y(y), 1110.0, transform.y(y), "#cbd5e1", 0.6, doubleArrayOf(5.0, 4.0))
        }
        model.joint.gridSystem.sortedXGrids.forEachIndexed { index, line ->
            drawBubbleX(canvas, transform.x(line.coordinateMeters), 800.0, line.label)
            if (index < xs.lastIndex) {
                canvas.dimensionH(
                    transform.x(xs[index]), transform.x(xs[index + 1]), bottomY,
                    "${Checks.format(xs[index + 1] - xs[index])} m"
                )
            }
        }
        if (xs.size >= 2) {
            canvas.dimensionH(transform.x(xs.first()), transform.x(xs.last()), bottomY + 26.0,
                "Lx = ${Checks.format(xs.last() - xs.first())} m", "#111827")
        }
        model.joint.gridSystem.sortedYGrids.forEachIndexed { index, line ->
            drawBubbleY(canvas, 105.0, transform.y(line.coordinateMeters), line.label)
            if (index < ys.lastIndex) {
                canvas.dimensionV(
                    transform.y(ys[index]), transform.y(ys[index + 1]), 78.0,
                    "${Checks.format(ys[index + 1] - ys[index])} m"
                )
            }
        }
        if (ys.size >= 2) {
            canvas.dimensionV(transform.y(ys.first()), transform.y(ys.last()), 52.0,
                "Ly = ${Checks.format(ys.last() - ys.first())} m", "#111827")
        }

        // Poteaux et poutres.
        frames.filter { it.kind == FrameKind.COLUMN }.forEach { frame ->
            val point = model.points[frame.startNodeId] ?: return@forEach
            val section = model.section(frame.sectionName)
            val bCm = section?.widthCm ?: 30.0
            val hCm = section?.depthCm ?: 30.0
            val halfX = max(4.0, bCm * transform.scaleX / 200.0)
            val halfY = max(4.0, hCm * transform.scaleY / 200.0)
            val cx = transform.x(point.xMeters)
            val cy = transform.y(point.yMeters)
            val diameter = section?.diameterCm
            if (diameter != null) {
                canvas.circle(cx, cy, max(4.0, diameter * transform.scaleX / 200.0),
                    "#1d4ed8", "#1e3a8a", 1.0)
            } else {
                canvas.rect(cx - halfX, cy - halfY, 2 * halfX, 2 * halfY, "#1d4ed8", "#1e3a8a", 1.0)
            }
            canvas.text(cx, cy - halfY - 3.0, section?.name ?: frame.sectionName, 7.5,
                TextAnchor.MIDDLE, "#1e3a8a")
        }
        frames.filter { it.kind == FrameKind.BEAM }.forEach { frame ->
            val start = model.points[frame.startNodeId] ?: return@forEach
            val end = model.points[frame.endNodeId] ?: return@forEach
            canvas.line(
                transform.x(start.xMeters), transform.y(start.yMeters),
                transform.x(end.xMeters), transform.y(end.yMeters), beamColor, 2.0
            )
            val cx = (transform.x(start.xMeters) + transform.x(end.xMeters)) / 2.0
            val cy = (transform.y(start.yMeters) + transform.y(end.yMeters)) / 2.0
            val section = model.section(frame.sectionName)
            val label = section?.let {
                "${it.name} (${Checks.format(it.widthCm ?: 0.0, 0)}x${Checks.format(it.depthCm ?: 0.0, 0)})"
            } ?: frame.sectionName
            canvas.text(cx, cy - 4.0, label, 7.5, TextAnchor.MIDDLE, "#065f46")
        }

        canvas.text(140.0, 862.0,
            "Niveau $levelName · ${frames.count { it.kind == FrameKind.COLUMN }} poteaux · " +
                "${frames.count { it.kind == FrameKind.BEAM }} poutres · " +
                "${areas.count { it.kind == AreaKind.WALL }} voiles", 9.5, TextAnchor.START, "#374151")
        canvas.footer("Cotes en mètres · bulles d'axes conformes au plan de coffrage")
        return output(canvas, dpi, caption)
    }

    // ------------------------------------------------------------------ plan des fondations

    fun renderFoundationPlan(
        model: SafeFoundationModel,
        envelope: VerticalDisplacementEnvelope? = model.displacementEnvelope,
        title: String = "Plan des fondations SAFE",
        dpi: Double = 300.0
    ): GraphicOutput {
        val canvas = VectorCanvas(1180.0, 880.0)
        canvas.title(title, "Semelles filantes, ressorts de sol, carte des déplacements ELS et enveloppe M3")
        if (model.families.isEmpty()) {
            canvas.text(590.0, 430.0, "Aucune famille de semelles détectée dans l'export SAFE.",
                13.0, TextAnchor.MIDDLE, "#9b1c1c")
            return output(canvas, dpi, title)
        }

        val allX = model.displacements.map { it.xMeters }
        val minX = allX.minOrNull() ?: 0.0
        val maxX = allX.maxOrNull() ?: 10.0
        val xSpan = max(1.0, maxX - minX)
        val rows = model.families.size
        val rowSpacing = 1.8
        val totalWidth = xSpan

        val familiesSorted = model.families.sortedBy { it.name }
        val transform = PlaneTransform(
            scaleX = 860.0 / totalWidth,
            scaleY = 500.0 / (rowSpacing * max(1, rows)),
            offsetX = 180.0 - minX * (860.0 / totalWidth),
            offsetY = 680.0,
            flipY = true
        )

        fun stripY(index: Int): Double = 120.0 + index * (500.0 / max(1, rows)) + 30.0

        // Grille des axes issus des points de déplacement.
        val distinctX = allX.distinct().sorted()
        if (distinctX.size >= 2) {
            distinctX.forEachIndexed { index, x ->
                val px = transform.x(x)
                canvas.line(px, 90.0, px, 760.0, "#cbd5e1", 0.7, doubleArrayOf(6.0, 4.0))
                drawBubbleX(canvas, px, 782.0, (65 + index).toChar().toString())
                if (index < distinctX.lastIndex) {
                    canvas.dimensionH(px, transform.x(distinctX[index + 1]), 812.0,
                        "${Checks.format(distinctX[index + 1] - distinctX[index])} m")
                }
            }
        }

        val settlement = envelope?.settlementMeters ?: 0.0
        val stress = model.geotechnical.subgradeModulusKNM3 * abs(settlement)

        familiesSorted.forEachIndexed { index, family ->
            val y = stripY(index)
            val spring = model.springFor(family)
            val forces = model.forcesFor(family.name)
            val widthWorld = min(totalWidth, family.flangeWidthMeters.coerceAtLeast(0.4))
            val bodyWidthPx = widthWorld * transform.scaleX
            val bodyHeightPx = 18.0
            val x0 = 180.0
            val color = settlementColor(settlement, model.geotechnical)
            canvas.rect(x0, y - bodyHeightPx / 2.0, bodyWidthPx, bodyHeightPx, color, "#111827", 1.0)
            canvas.hatch(x0, y - bodyHeightPx / 2.0, bodyWidthPx, bodyHeightPx, 9.0, "#374151aa", 0.45)
            canvas.text(x0 + bodyWidthPx / 2.0, y + 4.0,
                "${family.sectionLabel} · ${Checks.format(family.flangeWidthMeters)} m",
                9.0, TextAnchor.MIDDLE, "#111827", bold = true)
            canvas.text(x0 - 12.0, y - 2.0, family.name, 10.0, TextAnchor.END, "#111827", bold = true)
            canvas.text(x0 - 12.0, y + 12.0, family.kind.label, 8.0, TextAnchor.END, "#6b7280")
            if (spring != null) {
                canvas.text(x0 + bodyWidthPx + 12.0, y - 2.0,
                    "${spring.name} : K = ${Checks.format(spring.subgradeModulusKNM3, 0)} kN/m³", 8.5,
                    TextAnchor.START, "#1d4ed8")
                canvas.text(x0 + bodyWidthPx + 12.0, y + 12.0,
                    "K_line = ${Checks.format(spring.lineStiffnessKNM2, 0)} kN/m²", 8.5,
                    TextAnchor.START, "#1d4ed8")
            }
            if (forces != null) {
                canvas.text(x0, y + bodyHeightPx / 2.0 + 16.0,
                    "Mappui = ${Checks.format(forces.designSupportMomentKNm)} kN.m · " +
                        "Mtravée = ${Checks.format(forces.designSpanMomentKNm)} kN.m · " +
                        "V = ${Checks.format(forces.designShearKN)} kN", 8.5, TextAnchor.START, "#374151")
                drawMomentDiagram(canvas, x0, y + bodyHeightPx / 2.0 + 26.0, bodyWidthPx,
                    forces.designSupportMomentKNm, forces.designSpanMomentKNm)
            }
        }

        // Légende de la carte de déplacements.
        val legendX = 900.0
        canvas.text(legendX, 120.0, "Déplacements ELS", 11.0, TextAnchor.START, "#111827", bold = true)
        var legendY = 140.0
        listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { ratio ->
            val color = settlementColor(ratio * 0.02, model.geotechnical)
            canvas.rect(legendX, legendY, 22.0, 12.0, color, "#9ca3af", 0.6)
            canvas.text(legendX + 28.0, legendY + 10.0, "${Checks.format(ratio * 20.0, 1)} mm", 8.0,
                TextAnchor.START, "#374151")
            legendY += 16.0
        }
        canvas.text(legendX, legendY + 16.0, "Δmax = ${Checks.format(settlement * 1000.0, 2)} mm", 9.5,
            TextAnchor.START, "#111827", bold = true)
        canvas.text(legendX, legendY + 32.0, "σsol = Δ·Ks = ${Checks.format(stress)} kN/m²", 9.5,
            TextAnchor.START, "#111827", bold = true)
        canvas.text(legendX, legendY + 48.0,
            "σ̄sol = ${Checks.format(model.geotechnical.allowableStressKNM2)} kN/m²", 9.5,
            TextAnchor.START, "#6b7280")
        if (envelope?.governingLoadCase != null) {
            canvas.text(legendX, legendY + 64.0, "Cas : ${envelope.governingLoadCase}", 9.0,
                TextAnchor.START, "#6b7280")
        }

        canvas.footer("${model.families.size} familles · ${model.springs.size} ressorts · " +
            "${model.displacements.size} points de déplacement · source ${model.sourceName}")
        return output(canvas, dpi, title)
    }

    // ------------------------------------------------------------------ helpers

    private fun framesAtLevel(model: ParsedEtabsModel, levelName: String): List<FrameElement> {
        val byStory = model.frames.filter { it.storyName == levelName }
        if (byStory.isNotEmpty()) return byStory
        val level = model.joint.storySystem.level(levelName)
        val z = level?.elevationZk ?: return model.frames
        val tolerance = 0.05
        return model.frames.filter { frame ->
            val a = model.points[frame.startNodeId]
            val b = model.points[frame.endNodeId]
            a != null && b != null &&
                (abs(a.zMeters - z) <= tolerance || abs(b.zMeters - z) <= tolerance)
        }
    }

    private fun drawGroundGrid(canvas: VectorCanvas, points: List<Point3D>, transform: PlaneTransform) {
        if (points.isEmpty()) return
        val xs = points.map { it.xMeters }.distinct().sorted()
        val ys = points.map { it.yMeters }.distinct().sorted()
        val angle = Math.toRadians(30.0)
        fun project(x: Double, y: Double): CanvasPoint =
            CanvasPoint((x - y) * cos(angle), (x + y) * sin(angle) - points.first().zMeters)

        xs.forEach { x ->
            val a = project(x, ys.first())
            val b = project(x, ys.last())
            canvas.line(transform.x(a.x), transform.y(a.y), transform.x(b.x), transform.y(b.y),
                "#e5e7eb", 0.8, doubleArrayOf(4.0, 4.0))
        }
        ys.forEach { y ->
            val a = project(xs.first(), y)
            val b = project(xs.last(), y)
            canvas.line(transform.x(a.x), transform.y(a.y), transform.x(b.x), transform.y(b.y),
                "#e5e7eb", 0.8, doubleArrayOf(4.0, 4.0))
        }
    }

    private fun drawBaseBubbles(canvas: VectorCanvas, model: ParsedEtabsModel, transform: PlaneTransform) {
        val angle = Math.toRadians(30.0)
        val zBase = model.points.values.minOfOrNull { it.zMeters } ?: 0.0
        val yBase = model.points.values.minOfOrNull { it.yMeters } ?: 0.0
        val xBase = model.points.values.minOfOrNull { it.xMeters } ?: 0.0
        val xGrids = model.joint.gridSystem.sortedXGrids.takeIf { it.isNotEmpty() }
            ?: model.points.values.map { it.xMeters }.distinct().sorted().mapIndexed { index, x ->
                GridLine((65 + index).toChar().toString(), x)
            }
        xGrids.forEach { line ->
            val p = CanvasPoint((line.coordinateMeters - yBase) * cos(angle),
                (line.coordinateMeters + yBase) * sin(angle) - zBase)
            drawBubbleX(canvas, transform.x(p.x), transform.y(p.y) + 26.0, line.label)
        }
        val yGrids = model.joint.gridSystem.sortedYGrids.takeIf { it.isNotEmpty() }
            ?: model.points.values.map { it.yMeters }.distinct().sorted().mapIndexed { index, y ->
                GridLine((index + 1).toString(), y)
            }
        yGrids.forEach { line ->
            val p = CanvasPoint((xBase - line.coordinateMeters) * cos(angle),
                (xBase + line.coordinateMeters) * sin(angle) - zBase)
            drawBubbleY(canvas, transform.x(p.x) - 24.0, transform.y(p.y), line.label)
        }
    }

    private fun drawBubbleX(canvas: VectorCanvas, x: Double, y: Double, label: String) {
        canvas.circle(x, y, 12.0, "#ffffff", "#111827", 1.0)
        canvas.text(x, y + 4.0, label, 9.0, TextAnchor.MIDDLE, "#111827", bold = true)
    }

    private fun drawBubbleY(canvas: VectorCanvas, x: Double, y: Double, label: String) {
        canvas.circle(x, y, 12.0, "#ffffff", "#111827", 1.0)
        canvas.text(x, y + 4.0, label, 9.0, TextAnchor.MIDDLE, "#111827", bold = true)
    }

    private fun drawMomentDiagram(
        canvas: VectorCanvas,
        x: Double,
        y: Double,
        width: Double,
        support: Double,
        span: Double
    ) {
        val amplitude = 14.0
        val maxValue = max(support, span).coerceAtLeast(1.0)
        val points = buildList {
            add(CanvasPoint(x, y))
            add(CanvasPoint(x, y - amplitude * support / maxValue))
            add(CanvasPoint(x + width / 2.0, y + amplitude * span / maxValue))
            add(CanvasPoint(x + width, y - amplitude * support / maxValue))
            add(CanvasPoint(x + width, y))
        }
        canvas.polyline(points, "#7c3aed", 1.2)
        canvas.line(x, y, x + width, y, "#9ca3af", 0.6, doubleArrayOf(3.0, 3.0))
    }

    private fun settlementColor(settlementMeters: Double, geotechnical: com.archilab.rpa2024.engine.foundations.FoundationGeotechnicalParameters): String {
        val allowed = if (geotechnical.subgradeModulusKNM3 > 0.0) {
            geotechnical.allowableStressKNM2 / geotechnical.subgradeModulusKNM3
        } else {
            0.005
        }
        val ratio = (abs(settlementMeters) / allowed.coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        val red = (215 * ratio + 37 * (1 - ratio)).toInt()
        val green = (80 * ratio + 99 * (1 - ratio)).toInt()
        val blue = (60 * ratio + 235 * (1 - ratio)).toInt()
        return String.format("#%02x%02x%02x", red, green, blue)
    }

    private fun fit(
        points: Collection<CanvasPoint>,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        flipY: Boolean
    ): PlaneTransform {
        if (points.isEmpty()) {
            return PlaneTransform(1.0, 1.0, left, if (flipY) bottom else top, flipY)
        }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = (maxX - minX).takeIf { it > 1e-9 } ?: 1.0
        val height = (maxY - minY).takeIf { it > 1e-9 } ?: 1.0
        val scale = min((right - left) / width, (bottom - top) / height)
        val drawWidth = width * scale
        val drawHeight = height * scale
        val offsetX = left + ((right - left) - drawWidth) / 2.0 - minX * scale
        val offsetY = if (flipY) {
            top + ((bottom - top) - drawHeight) / 2.0 + maxY * scale
        } else {
            top + ((bottom - top) - drawHeight) / 2.0 - minY * scale
        }
        return PlaneTransform(scale, scale, offsetX, offsetY, flipY)
    }

    private fun output(canvas: VectorCanvas, dpi: Double, caption: String): GraphicOutput = GraphicOutput(
        svg = canvas.svg(),
        png = canvas.png(dpi),
        width = Math.round(canvas.width).toInt(),
        height = Math.round(canvas.height).toInt(),
        caption = caption
    )

    fun writePng(output: GraphicOutput, path: Path): Path {
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeBytes(output.png)
        return path
    }

    fun writeSvg(output: GraphicOutput, path: Path): Path {
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeText(output.svg, Charsets.UTF_8)
        return path
    }
}
