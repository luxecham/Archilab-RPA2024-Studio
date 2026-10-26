package com.archilab.rpa2024.cad

import com.archilab.rpa2024.bridge.SafeFoundationGeneration
import com.archilab.rpa2024.bridge.SafeStripFooting
import com.archilab.rpa2024.engine.foundations.StripFootingKind
import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.model.GridSystem
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.model.StoryLevel
import com.archilab.rpa2024.parser.AreaElement
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.EtabsSection
import com.archilab.rpa2024.parser.FrameElement
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import com.archilab.rpa2024.parser.Point3D
import java.nio.file.Path
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Parametres d'edition des planches de coffrage. */
data class FormworkPlanRequest(
    val model: ParsedEtabsModel,
    val foundationGeneration: SafeFoundationGeneration? = null,
    val project: CadProjectInfo = CadProjectInfo(),
    val selectedStoryNames: List<String> = emptyList(),
    val sheetFormat: CadSheetFormat = CadSheetFormat.A3,
    val includeFoundationPlan: Boolean = true,
    val axisExtensionMeters: Double = 1.5,
    val interSheetGapMm: Double = 120.0
) {
    init {
        require(axisExtensionMeters > 0.0) { "Le depassement des axes doit etre positif." }
        require(interSheetGapMm >= 0.0) { "L'espacement des planches doit etre positif ou nul." }
    }
}

/** Compte rendu de generation des plans de coffrage. */
data class FormworkPlanResult(
    val storySheets: List<String>,
    val foundationSheet: String?,
    val sheetCount: Int,
    val columnCount: Int,
    val wallCount: Int,
    val beamCount: Int,
    val slabCount: Int,
    val foundationCount: Int,
    val widthMm: Double,
    val heightMm: Double,
    val warnings: List<String>
)

/**
 * Genere une planche A0/A1/A3 par niveau. Chaque planche contient la trame complete,
 * les doubles cotations, les poteaux, voiles, poutres et planchers du niveau. Une
 * derniere planche trace les semelles filantes a partir de la geometrie ETABS/SAFE.
 *
 * Aucune file, portee ou section n'est predefinie : la trame et les elements sont lus
 * dans [ParsedEtabsModel] et les semelles dans [SafeFoundationGeneration].
 */
class FormworkPlanDxfExporter {

    fun draw(
        document: DxfCoreEngine,
        request: FormworkPlanRequest,
        originX: Double = 0.0,
        originY: Double = 0.0
    ): FormworkPlanResult {
        val warnings = mutableListOf<String>()
        val storyLevels = selectStories(request)
        if (storyLevels.isEmpty()) {
            warnings += "Aucun niveau principal exploitable dans le modele ETABS."
        }
        val sheetWidth = request.sheetFormat.widthMm + request.interSheetGapMm
        val sheets = mutableListOf<String>()
        var columnCount = 0
        var wallCount = 0
        var beamCount = 0
        var slabCount = 0
        var sheetNumber = 1

        storyLevels.forEach { level ->
            val plan = buildStoryPlan(request.model, level)
            columnCount += plan.columns.size
            wallCount += plan.walls.size
            beamCount += plan.beams.size
            slabCount += plan.slabs.size
            val sheetX = originX + (sheetNumber - 1) * sheetWidth
            val sheetTitle = "PLAN DE COFFRAGE - NIVEAU ${level.storyName}"
            drawStorySheet(document, request, plan, sheetX, originY, sheetNumber, sheetTitle)
            sheets += sheetTitle
            sheetNumber++
        }

        var foundationTitle: String? = null
        var foundationCount = 0
        if (request.includeFoundationPlan) {
            val generation = request.foundationGeneration
            if (generation == null || generation.footings.isEmpty()) {
                warnings += "Aucune geometrie de semelle SAFE fournie : plan de fondations non trace."
            } else {
                val sheetX = originX + (sheetNumber - 1) * sheetWidth
                foundationTitle = "PLAN DE COFFRAGE - FONDATIONS (${generation.footings.size} semelles)"
                drawFoundationSheet(
                    document = document,
                    request = request,
                    generation = generation,
                    originX = sheetX,
                    originY = originY,
                    sheetNumber = sheetNumber
                )
                foundationCount = generation.footings.size
                sheetNumber++
            }
        }

        val totalSheets = sheets.size + if (foundationTitle != null) 1 else 0
        val width = if (totalSheets == 0) 0.0 else {
            totalSheets * request.sheetFormat.widthMm + (totalSheets - 1) * request.interSheetGapMm
        }
        return FormworkPlanResult(
            storySheets = sheets,
            foundationSheet = foundationTitle,
            sheetCount = totalSheets,
            columnCount = columnCount,
            wallCount = wallCount,
            beamCount = beamCount,
            slabCount = slabCount,
            foundationCount = foundationCount,
            widthMm = width,
            heightMm = request.sheetFormat.heightMm,
            warnings = warnings
        )
    }

    fun export(request: FormworkPlanRequest, path: Path): FormworkPlanResult {
        val document = DxfCoreEngine("COFFRAGE_${request.model.jointName}")
        val result = draw(document, request)
        document.write(path)
        return result
    }

    // ------------------------------------------------------------------ donnees de plan

    private data class StoryPlan(
        val level: StoryLevel,
        val columns: List<ColumnPlan>,
        val walls: List<WallPlan>,
        val beams: List<BeamPlan>,
        val slabs: List<SlabPlan>
    )

    private data class ColumnPlan(
        val frameId: String,
        val center: Point3D,
        val widthMeters: Double,
        val depthMeters: Double,
        val circular: Boolean,
        val name: String,
        val label: String
    )

    private data class WallPlan(
        val areaId: String,
        val startMeters: Point3D,
        val endMeters: Point3D,
        val polygonMeters: List<Point3D>,
        val thicknessMeters: Double,
        val lengthMeters: Double,
        val name: String,
        val label: String
    )

    private data class BeamPlan(
        val frameId: String,
        val start: Point3D,
        val end: Point3D,
        val widthMeters: Double,
        val heightMeters: Double,
        val supportStartMeters: Double,
        val supportEndMeters: Double,
        val label: String
    )

    private data class SlabPlan(
        val areaId: String,
        val polygonMeters: List<Point3D>,
        val name: String,
        val label: String,
        val ribDirectionX: Boolean,
        val topology: SlabStripTopology?
    )

    private data class AxisLine(val label: String, val coordinateMeters: Double)

    private data class AxisFrame(
        val x: List<AxisLine>,
        val y: List<AxisLine>,
        val minX: Double,
        val maxX: Double,
        val minY: Double,
        val maxY: Double
    )

    private data class PlanViewport(
        val layout: CadSheetLayout,
        val frame: AxisFrame,
        val denominator: Int,
        val drawingOriginX: Double,
        val drawingOriginY: Double,
        val scaleFactor: Double
    ) {
        fun pagePoint(xMeters: Double, yMeters: Double): DxfPoint =
            DxfPoint(
                drawingOriginX + (xMeters - frame.minX) * scaleFactor,
                drawingOriginY + (yMeters - frame.minY) * scaleFactor
            )

        fun pageLength(meters: Double): Double = meters * scaleFactor
    }

    private fun selectStories(request: FormworkPlanRequest): List<StoryLevel> {
        val all = request.model.joint.storySystem.orderedStories
        if (request.selectedStoryNames.isEmpty()) return all
        val selected = request.selectedStoryNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return all.filter { it.storyName in selected }
    }

    private fun buildStoryPlan(model: ParsedEtabsModel, level: StoryLevel): StoryPlan {
        val columns = model.frames
            .filter { it.kind == FrameKind.COLUMN }
            .filter { frame -> belongsToStory(frame, model, level) }
            .mapNotNull { frame -> columnPlan(model, frame, level) }
            .distinctBy { it.frameId }
        val walls = model.areas
            .filter { it.kind == AreaKind.WALL }
            .filter { area -> belongsToStory(area, model, level) }
            .mapNotNull { area -> wallPlan(model, area) }
        val beams = model.frames
            .filter { it.kind == FrameKind.BEAM }
            .filter { frame -> belongsToStory(frame, model, level) }
            .mapNotNull { frame -> beamPlan(model, frame, level) }
        val slabs = model.areas
            .filter { it.kind == AreaKind.SLAB }
            .filter { area -> belongsToStory(area, model, level) }
            .mapNotNull { area -> slabPlan(model, area) }
        return StoryPlan(level, columns, walls, beams, slabs)
    }

    private fun belongsToStory(frame: FrameElement, model: ParsedEtabsModel, level: StoryLevel): Boolean {
        if (frame.storyName?.equals(level.storyName, ignoreCase = true) == true) return true
        if (frame.storyName != null) return false
        val start = model.points[frame.startNodeId] ?: return false
        return abs(start.zMeters - level.elevationZk) <= STORY_TOLERANCE
    }

    private fun belongsToStory(area: AreaElement, model: ParsedEtabsModel, level: StoryLevel): Boolean {
        if (area.storyName?.equals(level.storyName, ignoreCase = true) == true) return true
        if (area.storyName != null) return false
        val z = area.nodeIds.mapNotNull { model.points[it]?.zMeters }.minOrNull() ?: return false
        return abs(z - level.elevationZk) <= STORY_TOLERANCE
    }

    private fun columnPlan(
        model: ParsedEtabsModel,
        frame: FrameElement,
        level: StoryLevel
    ): ColumnPlan? {
        val center = model.points[frame.startNodeId] ?: return null
        if (abs(center.zMeters - level.elevationZk) > STORY_TOLERANCE) return null
        val family = model.joint.sectionCatalog.columns.firstOrNull { frame.id in it.elementIds }
        val section = model.section(frame.sectionName)
        val widthMeters = when {
            family != null && family.shape == ColumnShape.CIRCULAR -> family.effectiveDiameterCm / 100.0
            family != null -> family.b1Cm / 100.0
            section?.widthCm != null -> section.widthCm / 100.0
            else -> return null
        }
        val depthMeters = when {
            family != null && family.shape == ColumnShape.CIRCULAR -> family.effectiveDiameterCm / 100.0
            family != null -> family.h1Cm / 100.0
            section?.depthCm != null -> section.depthCm / 100.0
            else -> return null
        }
        if (widthMeters <= 0.0 || depthMeters <= 0.0) return null
        val circular = family?.shape == ColumnShape.CIRCULAR || section?.kind == com.archilab.rpa2024.parser.EtabsSectionKind.FRAME_CIRCULAR
        val name = family?.name ?: frame.sectionName
        val dimensions = if (circular) {
            "D=${formatCentimeters(widthMeters * 100.0)}"
        } else {
            "${formatCentimeters(widthMeters * 100.0)}x${formatCentimeters(depthMeters * 100.0)}"
        }
        return ColumnPlan(
            frameId = frame.id,
            center = center,
            widthMeters = widthMeters,
            depthMeters = depthMeters,
            circular = circular,
            name = name,
            label = "$name ($dimensions)"
        )
    }

    private fun wallPlan(model: ParsedEtabsModel, area: AreaElement): WallPlan? {
        val points = area.nodeIds.mapNotNull { model.points[it] }.distinctBy {
            "${round(it.xMeters)}:${round(it.yMeters)}"
        }
        if (points.size < 2) return null
        val family = model.joint.sectionCatalog.shearWalls.firstOrNull { area.id in it.elementIds }
            ?: area.pierName?.let { name -> model.joint.sectionCatalog.shearWalls.firstOrNull { it.pierName == name } }
        val section = model.section(area.propertyName)
        val thickness = family?.thicknessBwMeters ?: section?.thicknessCm?.div(100.0) ?: return null
        if (thickness <= 0.0) return null
        val (start, end) = farthestPair(points)
        val length = hypot(end.xMeters - start.xMeters, end.yMeters - start.yMeters)
        if (length < 1e-6) return null
        val name = family?.pierName ?: area.pierName ?: area.propertyName
        return WallPlan(
            areaId = area.id,
            startMeters = start,
            endMeters = end,
            polygonMeters = if (points.size >= 3) orientedPolygon(points) else emptyList(),
            thicknessMeters = thickness,
            lengthMeters = length,
            name = name,
            label = "$name (L=${formatMeters(length)} m, ep=${formatCentimeters(thickness * 100.0)} cm)"
        )
    }

    private fun beamPlan(
        model: ParsedEtabsModel,
        frame: FrameElement,
        level: StoryLevel
    ): BeamPlan? {
        val start = model.points[frame.startNodeId] ?: return null
        val end = model.points[frame.endNodeId] ?: return null
        if (abs(start.zMeters - level.elevationZk) > STORY_TOLERANCE &&
            abs(end.zMeters - level.elevationZk) > STORY_TOLERANCE
        ) {
            return null
        }
        val family = model.joint.sectionCatalog.beams.firstOrNull { frame.id in it.elementIds }
        val section = model.section(frame.sectionName)
        val width = family?.bCm?.div(100.0) ?: section?.widthCm?.div(100.0) ?: return null
        val height = family?.hCm?.div(100.0) ?: section?.depthCm?.div(100.0) ?: return null
        if (width <= 0.0 || height <= 0.0) return null
        val directionLength = max(hypot(end.xMeters - start.xMeters, end.yMeters - start.yMeters), 1e-9)
        val startSupport = supportProjection(model, frame.startNodeId, level)
        val endSupport = supportProjection(model, frame.endNodeId, level)
        val insetStart = min(startSupport, directionLength * 0.20)
        val insetEnd = min(endSupport, directionLength * 0.20)
        val name = family?.name ?: frame.sectionName
        return BeamPlan(
            frameId = frame.id,
            start = start,
            end = end,
            widthMeters = width,
            heightMeters = height,
            supportStartMeters = insetStart,
            supportEndMeters = insetEnd,
            label = "$name ${formatCentimeters(width * 100.0)}x${formatCentimeters(height * 100.0)}"
        )
    }

    private fun slabPlan(model: ParsedEtabsModel, area: AreaElement): SlabPlan? {
        val points = area.nodeIds.mapNotNull { model.points[it] }.distinctBy {
            "${round(it.xMeters)}:${round(it.yMeters)}"
        }
        if (points.size < 3) return null
        val ordered = orientedPolygon(points)
        val topology = model.joint.sectionCatalog.slabs.firstOrNull { area.id in it.elementIds }
            ?: model.joint.sectionCatalog.slabs.firstOrNull {
                it.name.equals(area.propertyName, ignoreCase = true)
            }
        val section = model.section(area.propertyName)
        val label = when {
            topology != null && topology.isSolidSlab ->
                "DALLE PLEINE ${formatCentimeters(topology.totalThicknessHtCm)} cm"
            topology != null ->
                "CC ${formatCentimeters(topology.hollowBlockHeightCm)}+" +
                    "${formatCentimeters(topology.compressionSlabH0Cm)} - poutrelles / " +
                    "${formatCentimeters(topology.ribSpacingCm)} cm"
            section?.totalThicknessCm != null ->
                "${area.propertyName} - ${formatCentimeters(section.totalThicknessCm)} cm"
            else -> area.propertyName
        }
        val minX = ordered.minOf { it.xMeters }
        val maxX = ordered.maxOf { it.xMeters }
        val minY = ordered.minOf { it.yMeters }
        val maxY = ordered.maxOf { it.yMeters }
        return SlabPlan(
            areaId = area.id,
            polygonMeters = ordered,
            name = topology?.name ?: area.propertyName,
            label = label,
            ribDirectionX = (maxX - minX) <= (maxY - minY),
            topology = topology
        )
    }

    private fun supportProjection(
        model: ParsedEtabsModel,
        nodeId: String,
        level: StoryLevel
    ): Double {
        val columnFrames = model.frames.filter {
            it.kind == FrameKind.COLUMN &&
                (it.startNodeId == nodeId || it.endNodeId == nodeId) &&
                belongsToStory(it, model, level)
        }
        val widths = columnFrames.mapNotNull { frame ->
            val family = model.joint.sectionCatalog.columns.firstOrNull { frame.id in it.elementIds }
            val section = model.section(frame.sectionName)
            val b = family?.b1Cm?.div(100.0) ?: section?.widthCm?.div(100.0)
            val h = family?.h1Cm?.div(100.0) ?: section?.depthCm?.div(100.0)
            if (b != null && h != null) max(b, h) / 2.0 else null
        }
        val wallThickness = model.areas
            .filter { it.kind == AreaKind.WALL && nodeId in it.nodeIds }
            .mapNotNull { area ->
                val family = model.joint.sectionCatalog.shearWalls.firstOrNull { area.id in it.elementIds }
                family?.thicknessBwMeters ?: model.section(area.propertyName)?.thicknessCm?.div(100.0)
            }
        return (widths + wallThickness).maxOrNull() ?: 0.0
    }

    // ------------------------------------------------------------------ planches

    private fun drawStorySheet(
        document: DxfCoreEngine,
        request: FormworkPlanRequest,
        plan: StoryPlan,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        sheetTitle: String
    ) {
        drawSlabs(document, request, plan, originX, originY, sheetNumber, sheetTitle)
    }

    private fun drawSlabs(
        document: DxfCoreEngine,
        request: FormworkPlanRequest,
        plan: StoryPlan,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        sheetTitle: String
    ) {
        val frame = buildAxes(request.model.joint.gridSystem, planPoints(plan))
        val envelopeWidth = frame.maxX - frame.minX
        val envelopeHeight = frame.maxY - frame.minY
        val denominator = CadGeometry.chooseScale(
            envelopeWidth + 2.0 * request.axisExtensionMeters,
            envelopeHeight + 2.0 * request.axisExtensionMeters,
            request.sheetFormat.widthMm - 70.0,
            request.sheetFormat.heightMm - 115.0
        )
        val titleBlock = CadTitleBlock(
            project = request.project.copy(jointName = request.project.jointName.ifBlank { request.model.jointName }),
            sheetTitle = sheetTitle,
            scaleLabel = "1/$denominator",
            sheetNumber = sheetNumber
        )
        val layout = document.addSheet(originX, originY, request.sheetFormat, titleBlock)
        val scaleFactor = 1000.0 / denominator
        val contentWidth = (envelopeWidth + 2.0 * request.axisExtensionMeters) * scaleFactor
        val contentHeight = (envelopeHeight + 2.0 * request.axisExtensionMeters) * scaleFactor
        val drawingOriginX = layout.drawingLeft + (layout.drawingWidth - contentWidth) / 2.0 +
            request.axisExtensionMeters * scaleFactor
        val drawingOriginY = layout.drawingBottom + (layout.drawingHeight - contentHeight) / 2.0 +
            request.axisExtensionMeters * scaleFactor
        val viewport = PlanViewport(
            layout = layout,
            frame = frame,
            denominator = denominator,
            drawingOriginX = drawingOriginX,
            drawingOriginY = drawingOriginY,
            scaleFactor = scaleFactor
        )

        drawGrid(document, viewport, request.axisExtensionMeters)

        plan.slabs.forEach { slab -> drawSlab(document, viewport, slab) }
        plan.beams.forEach { beam -> drawBeam(document, viewport, beam) }
        plan.walls.forEach { wall -> drawWall(document, viewport, wall) }
        plan.columns.forEach { column -> drawColumn(document, viewport, column) }

        document.addText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + 2.0,
            originY + 54.0,
            "Niveau ${plan.level.storyName} - altitude ${formatMeters(plan.level.elevationZk)} m - " +
                "${plan.columns.size} poteaux, ${plan.walls.size} voiles, ${plan.beams.size} poutres, " +
                "${plan.slabs.size} planchers",
            2.0
        )
    }

    private fun drawFoundationSheet(
        document: DxfCoreEngine,
        request: FormworkPlanRequest,
        generation: SafeFoundationGeneration,
        originX: Double,
        originY: Double,
        sheetNumber: Int
    ) {
        val baseColumns = generation.basePoints.associateBy { it.id }
        val baseAreaPoints = request.model.areas
            .filter { it.kind == AreaKind.WALL }
            .flatMap { area -> area.nodeIds.mapNotNull { request.model.points[it] } }
            .filter { point -> generation.basePoints.any { abs(it.zMeters - point.zMeters) <= STORY_TOLERANCE } }
            .distinctBy { "${round(it.xMeters)}:${round(it.yMeters)}" }
        val points = buildList {
            generation.footings.forEach {
                add(it.start)
                add(it.end)
            }
            addAll(generation.basePoints)
            addAll(baseAreaPoints)
        }
        val frame = buildAxes(request.model.joint.gridSystem, points)
        val envelopeWidth = frame.maxX - frame.minX
        val envelopeHeight = frame.maxY - frame.minY
        val denominator = CadGeometry.chooseScale(
            envelopeWidth + 2.0 * request.axisExtensionMeters,
            envelopeHeight + 2.0 * request.axisExtensionMeters,
            request.sheetFormat.widthMm - 70.0,
            request.sheetFormat.heightMm - 115.0
        )
        val titleBlock = CadTitleBlock(
            project = request.project.copy(jointName = request.project.jointName.ifBlank { request.model.jointName }),
            sheetTitle = "PLAN DE COFFRAGE - FONDATIONS",
            scaleLabel = "1/$denominator",
            sheetNumber = sheetNumber
        )
        val layout = document.addSheet(originX, originY, request.sheetFormat, titleBlock)
        val scaleFactor = 1000.0 / denominator
        val contentWidth = (envelopeWidth + 2.0 * request.axisExtensionMeters) * scaleFactor
        val contentHeight = (envelopeHeight + 2.0 * request.axisExtensionMeters) * scaleFactor
        val viewport = PlanViewport(
            layout = layout,
            frame = frame,
            denominator = denominator,
            drawingOriginX = layout.drawingLeft + (layout.drawingWidth - contentWidth) / 2.0 +
                request.axisExtensionMeters * scaleFactor,
            drawingOriginY = layout.drawingBottom + (layout.drawingHeight - contentHeight) / 2.0 +
                request.axisExtensionMeters * scaleFactor,
            scaleFactor = scaleFactor
        )
        drawGrid(document, viewport, request.axisExtensionMeters)

        // Voiles d'amorce au niveau de base.
        val baseStory = request.model.joint.storySystem.orderedStories.firstOrNull()
        if (baseStory != null) {
            request.model.areas
                .filter { it.kind == AreaKind.WALL }
                .filter { belongsToStory(it, request.model, baseStory) }
                .mapNotNull { wallPlan(request.model, it) }
                .forEach { wall ->
                    val start = viewport.pagePoint(wall.startMeters.xMeters, wall.startMeters.yMeters)
                    val end = viewport.pagePoint(wall.endMeters.xMeters, wall.endMeters.yMeters)
                    val outline = CadGeometry.rectangleAlongSegment(
                        start,
                        end,
                        viewport.pageLength(wall.thicknessMeters)
                    )
                    document.addLwPolyline(CadLayerStandard.WALL_FORMWORK, outline, closed = true)
                    document.addHatch(
                        CadLayerStandard.WALL_FORMWORK,
                        outline,
                        pattern = "ANSI31",
                        patternScale = 0.4
                    )
                }
        }

        // Amorces de poteaux et de voiles au niveau de base.
        baseColumns.values.forEach { point ->
            val columns = request.model.frames
                .filter { frame ->
                    frame.kind == FrameKind.COLUMN &&
                        (frame.startNodeId == point.id || frame.endNodeId == point.id)
                }
            columns.forEach { frame ->
                val family = request.model.joint.sectionCatalog.columns.firstOrNull { frame.id in it.elementIds }
                val section = request.model.section(frame.sectionName)
                val width = family?.b1Cm?.div(100.0) ?: section?.widthCm?.div(100.0)
                val depth = family?.h1Cm?.div(100.0) ?: section?.depthCm?.div(100.0)
                if (width != null && depth != null) {
                    drawAmorceColumn(
                        document,
                        viewport,
                        point,
                        width,
                        depth,
                        family?.name ?: frame.sectionName
                    )
                }
            }
        }

        generation.footings.forEachIndexed { index, footing ->
            drawFoundation(document, viewport, footing, index + 1)
        }

        document.addText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + 2.0,
            originY + 54.0,
            "${generation.footings.size} semelle(s) filante(s) - Ks = " +
                "${formatNumber(generation.subgradeModulusKNM3, 0)} kN/m3",
            2.0
        )
    }

    private fun drawGrid(
        document: DxfCoreEngine,
        viewport: PlanViewport,
        extensionMeters: Double
    ) {
        val frame = viewport.frame
        val extension = viewport.pageLength(extensionMeters)
        val xMin = viewport.pagePoint(frame.minX, frame.minY).x
        val xMax = viewport.pagePoint(frame.maxX, frame.maxY).x
        val yMin = viewport.pagePoint(frame.minX, frame.minY).y
        val yMax = viewport.pagePoint(frame.maxX, frame.maxY).y

        frame.x.forEach { line ->
            val x = viewport.pagePoint(line.coordinateMeters, frame.minY).x
            document.addLine(CadLayerStandard.AXES, x, yMin - extension, x, yMax + extension)
            drawAxisBubble(document, x, yMin - extension, line.label)
            drawAxisBubble(document, x, yMax + extension, line.label)
        }
        frame.y.forEach { line ->
            val y = viewport.pagePoint(frame.minX, line.coordinateMeters).y
            document.addLine(CadLayerStandard.AXES, xMin - extension, y, xMax + extension, y)
            drawAxisBubble(document, xMin - extension, y, line.label)
            drawAxisBubble(document, xMax + extension, y, line.label)
        }

        val xCoordinates = frame.x.map { it.coordinateMeters }
        val yCoordinates = frame.y.map { it.coordinateMeters }
        if (xCoordinates.size >= 2) {
            drawHorizontalDimensions(
                document, viewport, xCoordinates, frame.maxY, yMax + extension + 10.0,
                frame.minX, frame.maxX, outwardSign = 1.0
            )
            drawHorizontalDimensions(
                document, viewport, xCoordinates, frame.minY, yMin - extension - 10.0,
                frame.minX, frame.maxX, outwardSign = -1.0
            )
        }
        if (yCoordinates.size >= 2) {
            drawVerticalDimensions(
                document, viewport, yCoordinates, frame.maxX, xMax + extension + 10.0,
                frame.minY, frame.maxY, outwardSign = 1.0
            )
            drawVerticalDimensions(
                document, viewport, yCoordinates, frame.minX, xMin - extension - 10.0,
                frame.minY, frame.maxY, outwardSign = -1.0
            )
        }
    }

    private fun drawHorizontalDimensions(
        document: DxfCoreEngine,
        viewport: PlanViewport,
        coordinates: List<Double>,
        baseY: Double,
        dimensionY: Double,
        minX: Double,
        maxX: Double,
        outwardSign: Double
    ) {
        val sorted = coordinates.distinct().sorted()
        sorted.zipWithNext().forEach { (left, right) ->
            val first = viewport.pagePoint(left, baseY)
            val second = viewport.pagePoint(right, baseY)
            document.addLinearDimension(
                CadLayerStandard.DIMENSIONS,
                DxfPoint(first.x, first.y),
                DxfPoint(second.x, second.y),
                DxfPoint((first.x + second.x) / 2.0, dimensionY),
                formatDimensionMeters(right - left),
                textHeightMm = 2.1
            )
        }
        val start = viewport.pagePoint(sorted.first(), baseY)
        val end = viewport.pagePoint(sorted.last(), baseY)
        val overallY = dimensionY + outwardSign * 9.0
        document.addLinearDimension(
            CadLayerStandard.DIMENSIONS,
            DxfPoint(start.x, start.y),
            DxfPoint(end.x, end.y),
            DxfPoint((start.x + end.x) / 2.0, overallY),
            "L = ${formatDimensionMeters(maxX - minX)}",
            textHeightMm = 2.2
        )
    }

    private fun drawVerticalDimensions(
        document: DxfCoreEngine,
        viewport: PlanViewport,
        coordinates: List<Double>,
        baseX: Double,
        dimensionX: Double,
        minY: Double,
        maxY: Double,
        outwardSign: Double
    ) {
        val sorted = coordinates.distinct().sorted()
        sorted.zipWithNext().forEach { (bottom, top) ->
            val first = viewport.pagePoint(baseX, bottom)
            val second = viewport.pagePoint(baseX, top)
            document.addLinearDimension(
                CadLayerStandard.DIMENSIONS,
                DxfPoint(first.x, first.y),
                DxfPoint(second.x, second.y),
                DxfPoint(dimensionX, (first.y + second.y) / 2.0),
                formatDimensionMeters(top - bottom),
                textHeightMm = 2.1
            )
        }
        val start = viewport.pagePoint(baseX, sorted.first())
        val end = viewport.pagePoint(baseX, sorted.last())
        val overallX = dimensionX + outwardSign * 9.0
        document.addLinearDimension(
            CadLayerStandard.DIMENSIONS,
            DxfPoint(start.x, start.y),
            DxfPoint(end.x, end.y),
            DxfPoint(overallX, (start.y + end.y) / 2.0),
            "L = ${formatDimensionMeters(maxY - minY)}",
            textHeightMm = 2.2
        )
    }

    private fun drawAxisBubble(document: DxfCoreEngine, x: Double, y: Double, label: String) {
        document.addCircle(CadLayerStandard.AXIS_BUBBLES, x, y, 4.6)
        document.addText(
            CadLayerStandard.AXIS_BUBBLES,
            x,
            y - 1.35,
            label,
            heightMm = 3.4,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun drawSlab(document: DxfCoreEngine, viewport: PlanViewport, slab: SlabPlan) {
        val polygon = slab.polygonMeters.map { viewport.pagePoint(it.xMeters, it.yMeters) }
        if (polygon.size < 3) return
        document.addLwPolyline(CadLayerStandard.BEAM_FORMWORK, polygon, closed = true)
        val center = viewport.pagePoint(
            slab.polygonMeters.map { it.xMeters }.average(),
            slab.polygonMeters.map { it.yMeters }.average()
        )
        val minX = slab.polygonMeters.minOf { it.xMeters }
        val maxX = slab.polygonMeters.maxOf { it.xMeters }
        val minY = slab.polygonMeters.minOf { it.yMeters }
        val maxY = slab.polygonMeters.maxOf { it.yMeters }
        val half = viewport.pageLength(
            0.16 * if (slab.ribDirectionX) (maxX - minX) else (maxY - minY)
        )
        val labelCenter = DxfPoint(center.x + 24.0, center.y + 12.0)
        if (slab.ribDirectionX) {
            drawDoubleArrow(document, DxfPoint(center.x - half, center.y), DxfPoint(center.x + half, center.y))
            document.addText(
                CadLayerStandard.TEXT,
                labelCenter.x,
                labelCenter.y,
                slab.label,
                2.15,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
            document.addText(
                CadLayerStandard.TEXT,
                labelCenter.x,
                labelCenter.y - 4.5,
                slab.name,
                1.8,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
        } else {
            drawDoubleArrow(document, DxfPoint(center.x, center.y - half), DxfPoint(center.x, center.y + half))
            document.addText(
                CadLayerStandard.TEXT,
                labelCenter.x,
                labelCenter.y,
                slab.label,
                2.15,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
            document.addText(
                CadLayerStandard.TEXT,
                labelCenter.x,
                labelCenter.y - 4.5,
                slab.name,
                1.8,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
        }
    }

    private fun drawDoubleArrow(document: DxfCoreEngine, start: DxfPoint, end: DxfPoint) {
        document.addLine(CadLayerStandard.BEAM_FORMWORK, start.x, start.y, end.x, end.y)
        val angle = CadGeometry.angleDegrees(start, end)
        drawArrowHead(document, end, angle, CadLayerStandard.BEAM_FORMWORK)
        drawArrowHead(document, start, angle + 180.0, CadLayerStandard.BEAM_FORMWORK)
    }

    private fun drawArrowHead(
        document: DxfCoreEngine,
        tip: DxfPoint,
        directionDegrees: Double,
        layer: CadLayerDefinition
    ) {
        val length = 3.2
        val halfWidth = 1.2
        val base = CadGeometry.polar(tip.x, tip.y, length, directionDegrees + 180.0)
        val left = CadGeometry.polar(base.x, base.y, halfWidth, directionDegrees + 90.0)
        val right = CadGeometry.polar(base.x, base.y, halfWidth, directionDegrees - 90.0)
        val triangle = listOf(tip, left, right)
        document.addLwPolyline(layer, triangle, closed = true)
        document.addHatch(layer, triangle, pattern = "SOLID")
    }

    private fun drawBeam(document: DxfCoreEngine, viewport: PlanViewport, beam: BeamPlan) {
        val start = viewport.pagePoint(beam.start.xMeters, beam.start.yMeters)
        val end = viewport.pagePoint(beam.end.xMeters, beam.end.yMeters)
        val length = hypot(end.x - start.x, end.y - start.y)
        if (length < 1e-6) return
        val ux = (end.x - start.x) / length
        val uy = (end.y - start.y) / length
        val nx = -uy
        val ny = ux
        val insetStart = min(viewport.pageLength(beam.supportStartMeters), length * 0.2)
        val insetEnd = min(viewport.pageLength(beam.supportEndMeters), length * 0.2)
        val from = DxfPoint(start.x + ux * insetStart, start.y + uy * insetStart)
        val to = DxfPoint(end.x - ux * insetEnd, end.y - uy * insetEnd)
        val halfWidth = viewport.pageLength(beam.widthMeters) / 2.0
        val first = DxfPoint(from.x + nx * halfWidth, from.y + ny * halfWidth)
        val second = DxfPoint(to.x + nx * halfWidth, to.y + ny * halfWidth)
        val third = DxfPoint(to.x - nx * halfWidth, to.y - ny * halfWidth)
        val fourth = DxfPoint(from.x - nx * halfWidth, from.y - ny * halfWidth)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, first.x, first.y, second.x, second.y)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, fourth.x, fourth.y, third.x, third.y)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, first.x, first.y, fourth.x, fourth.y)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, second.x, second.y, third.x, third.y)
        val middle = DxfPoint((from.x + to.x) / 2.0, (from.y + to.y) / 2.0)
        document.addText(
            CadLayerStandard.TEXT,
            middle.x,
            middle.y + halfWidth + 2.0,
            beam.label,
            2.0,
            rotationDegrees = CadGeometry.labelAngle(from, to),
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun drawWall(document: DxfCoreEngine, viewport: PlanViewport, wall: WallPlan) {
        val start = viewport.pagePoint(wall.startMeters.xMeters, wall.startMeters.yMeters)
        val end = viewport.pagePoint(wall.endMeters.xMeters, wall.endMeters.yMeters)
        val polygon = if (wall.polygonMeters.size >= 3) {
            wall.polygonMeters.map { viewport.pagePoint(it.xMeters, it.yMeters) }
        } else {
            CadGeometry.rectangleAlongSegment(start, end, viewport.pageLength(wall.thicknessMeters))
        }
        if (polygon.size < 3) return
        document.addLwPolyline(CadLayerStandard.WALL_FORMWORK, polygon, closed = true)
        document.addHatch(CadLayerStandard.WALL_FORMWORK, polygon, pattern = "ANSI31", patternScale = 0.55)
        val midX = wall.startMeters.xMeters +
            (wall.endMeters.xMeters - wall.startMeters.xMeters) * 0.28
        val midY = wall.startMeters.yMeters +
            (wall.endMeters.yMeters - wall.startMeters.yMeters) * 0.28
        val center = viewport.pagePoint(midX, midY)
        val angle = CadGeometry.labelAngle(start, end)
        document.addText(
            CadLayerStandard.TEXT,
            center.x,
            center.y + 3.0,
            wall.label,
            1.85,
            rotationDegrees = angle,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun drawColumn(document: DxfCoreEngine, viewport: PlanViewport, column: ColumnPlan) {
        val center = viewport.pagePoint(column.center.xMeters, column.center.yMeters)
        val width = viewport.pageLength(column.widthMeters)
        val depth = viewport.pageLength(column.depthMeters)
        if (column.circular) {
            val radius = viewport.pageLength(column.widthMeters) / 2.0
            document.addCircle(CadLayerStandard.COLUMN_FORMWORK, center.x, center.y, radius)
            document.addHatch(
                CadLayerStandard.COLUMN_FORMWORK,
                polygonOfCircle(center, radius, 20),
                pattern = "SOLID"
            )
        } else {
            val outline = CadGeometry.rectangle(center.x, center.y, width, depth)
            document.addLwPolyline(CadLayerStandard.COLUMN_FORMWORK, outline, closed = true)
            document.addHatch(CadLayerStandard.COLUMN_FORMWORK, outline, pattern = "ANSI31", patternScale = 0.35)
        }
        document.addText(
            CadLayerStandard.TEXT,
            center.x,
            center.y,
            column.label,
            1.55,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun drawAmorceColumn(
        document: DxfCoreEngine,
        viewport: PlanViewport,
        point: Point3D,
        widthMeters: Double,
        depthMeters: Double,
        name: String
    ) {
        val center = viewport.pagePoint(point.xMeters, point.yMeters)
        val outline = CadGeometry.rectangle(
            center.x,
            center.y,
            viewport.pageLength(widthMeters),
            viewport.pageLength(depthMeters)
        )
        document.addLwPolyline(CadLayerStandard.COLUMN_FORMWORK, outline, closed = true)
        document.addHatch(CadLayerStandard.COLUMN_FORMWORK, outline, pattern = "ANSI31", patternScale = 0.35)
        document.addText(
            CadLayerStandard.TEXT,
            center.x,
            center.y,
            name,
            1.35,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun drawFoundation(
        document: DxfCoreEngine,
        viewport: PlanViewport,
        footing: SafeStripFooting,
        index: Int
    ) {
        val start = viewport.pagePoint(footing.start.xMeters, footing.start.yMeters)
        val end = viewport.pagePoint(footing.end.xMeters, footing.end.yMeters)
        val family = footing.family
        val currentKind = family.kind == StripFootingKind.CURRENT
        val shift = if (currentKind) 0.0 else footing.inwardSign * family.flangeWidthMeters / 2.0
        val mainOutline = CadGeometry.rectangleAlongSegment(
            start,
            end,
            viewport.pageLength(family.flangeWidthMeters),
            viewport.pageLength(shift)
        )
        val leanOutline = CadGeometry.rectangleAlongSegment(
            start,
            end,
            viewport.pageLength(family.flangeWidthMeters + 0.20),
            viewport.pageLength(shift)
        )
        if (leanOutline.isNotEmpty()) {
            document.addLwPolyline(CadLayerStandard.FOOTING_FORMWORK, leanOutline, closed = true)
        }
        if (mainOutline.isNotEmpty()) {
            document.addLwPolyline(CadLayerStandard.FOOTING_FORMWORK, mainOutline, closed = true)
            document.addHatch(
                CadLayerStandard.FOOTING_FORMWORK,
                mainOutline,
                pattern = "ANSI31",
                patternScale = 0.65
            )
        }
        val webOutline = CadGeometry.rectangleAlongSegment(
            start,
            end,
            viewport.pageLength(family.webWidthMeters),
            viewport.pageLength(if (currentKind) 0.0 else shift)
        )
        if (webOutline.isNotEmpty()) {
            document.addLwPolyline(CadLayerStandard.FOOTING_FORMWORK, webOutline, closed = true)
        }
        val middle = DxfPoint((start.x + end.x) / 2.0, (start.y + end.y) / 2.0)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = max(hypot(dx, dy), 1e-6)
        val nx = -dy / length
        val ny = dx / length
        val labelPoint = DxfPoint(
            middle.x + nx * (viewport.pageLength(family.flangeWidthMeters) / 2.0 + 4.0),
            middle.y + ny * (viewport.pageLength(family.flangeWidthMeters) / 2.0 + 4.0)
        )
        val angle = CadGeometry.labelAngle(start, end)
        document.addText(
            CadLayerStandard.TEXT,
            labelPoint.x,
            labelPoint.y,
            "${family.name} (B=${formatMeters(family.flangeWidthMeters)} m, h=" +
                "${formatMeters(family.totalHeightMeters)} m)",
            1.8,
            rotationDegrees = angle,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        document.addLeaderWithBubble(
            layer = CadLayerStandard.TEXT,
            target = middle,
            bubbleCenter = DxfPoint(labelPoint.x, labelPoint.y + 6.0),
            position = index,
            label = "${family.name}\n" +
                "${if (currentKind) "SF centree" else "SFJ excentree"}\n" +
                "b0=${formatMeters(family.webWidthMeters)} m\n" +
                "h0=${formatMeters(family.tableThicknessMeters)} m",
            bubbleRadiusMm = 2.8,
            textHeightMm = 1.55
        )
        if (!currentKind && abs(footing.eccentricityMeters) > 1e-9) {
            document.addText(
                CadLayerStandard.TEXT,
                middle.x,
                middle.y - viewport.pageLength(family.flangeWidthMeters) / 2.0 - 3.0,
                "e = ${formatMeters(footing.eccentricityMeters)} m",
                1.55,
                rotationDegrees = angle,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
        }
    }

    // ------------------------------------------------------------------ geometrie

    private fun buildAxes(
        grids: GridSystem,
        points: List<Point3D>
    ): AxisFrame {
        val xAxes = grids.sortedXGrids.map { AxisLine(it.label, it.coordinateMeters) }
            .ifEmpty { deriveAxes(points.map { it.xMeters }, true) }
        val yAxes = grids.sortedYGrids.map { AxisLine(it.label, it.coordinateMeters) }
            .ifEmpty { deriveAxes(points.map { it.yMeters }, false) }
        val xs = points.map { it.xMeters } + xAxes.map { it.coordinateMeters }
        val ys = points.map { it.yMeters } + yAxes.map { it.coordinateMeters }
        val rawMinX = xs.minOrNull() ?: 0.0
        val rawMaxX = xs.maxOrNull() ?: 0.0
        val rawMinY = ys.minOrNull() ?: 0.0
        val rawMaxY = ys.maxOrNull() ?: 0.0
        val actualMinX = minOf(rawMinX, rawMaxX)
        val actualMaxX = maxOf(rawMaxX, actualMinX + 0.1)
        val actualMinY = minOf(rawMinY, rawMaxY)
        val actualMaxY = maxOf(rawMaxY, actualMinY + 0.1)
        return AxisFrame(
            x = xAxes,
            y = yAxes,
            minX = actualMinX,
            maxX = actualMaxX,
            minY = actualMinY,
            maxY = actualMaxY
        )
    }

    private fun deriveAxes(coordinates: List<Double>, horizontal: Boolean): List<AxisLine> {
        val sorted = coordinates.sorted().distinctBy { round(it) }
        return sorted.mapIndexed { index, value ->
            val label = if (horizontal) {
                if (index < 26) ('A'.code + index).toChar().toString() else "X${index + 1}"
            } else {
                if (index < 26) ('A'.code + index).toChar().toString() else "Y${index + 1}"
            }
            AxisLine(label, value)
        }
    }

    private fun planPoints(plan: StoryPlan): List<Point3D> = buildList {
        plan.columns.forEach { add(it.center) }
        plan.walls.forEach {
            add(it.startMeters)
            add(it.endMeters)
            addAll(it.polygonMeters)
        }
        plan.beams.forEach {
            add(it.start)
            add(it.end)
        }
        plan.slabs.forEach { addAll(it.polygonMeters) }
    }

    private fun orientedPolygon(points: List<Point3D>): List<Point3D> {
        val centerX = points.map { it.xMeters }.average()
        val centerY = points.map { it.yMeters }.average()
        return points.sortedBy { atan2(it.yMeters - centerY, it.xMeters - centerX) }
    }

    private fun farthestPair(points: List<Point3D>): Pair<Point3D, Point3D> {
        var best = points[0] to points[1]
        var bestDistance = -1.0
        for (firstIndex in points.indices) {
            for (secondIndex in firstIndex + 1 until points.size) {
                val distance = points[firstIndex].planDistanceTo(points[secondIndex])
                if (distance > bestDistance) {
                    bestDistance = distance
                    best = points[firstIndex] to points[secondIndex]
                }
            }
        }
        return best
    }

    private fun polygonOfCircle(center: DxfPoint, radius: Double, segments: Int): List<DxfPoint> =
        (0 until segments).map { index ->
            CadGeometry.polar(center.x, center.y, radius, index * 360.0 / segments)
        }

    private fun formatMeters(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
    private fun formatCentimeters(value: Double): String =
        if (abs(value - value.toInt()) < 1e-6) value.toInt().toString()
        else String.format(Locale.ROOT, "%.1f", value)
    private fun formatDimensionMeters(value: Double): String =
        String.format(Locale.ROOT, "%.2f m", value)
    private fun formatNumber(value: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)
    private fun round(value: Double): Long = Math.round(value * 1000.0)

    private companion object {
        const val STORY_TOLERANCE = 0.02
    }
}
