package com.archilab.rpa2024.cad

import com.archilab.rpa2024.engine.foundations.StripFootingDesign
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingDesignSet
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignResult
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignResult
import com.archilab.rpa2024.engine.frames.ColumnRebarLayout
import com.archilab.rpa2024.engine.frames.RebarBar
import com.archilab.rpa2024.engine.secondary.ParapetDesignInput
import com.archilab.rpa2024.engine.secondary.ParapetDesignResult
import com.archilab.rpa2024.engine.secondary.ParapetGeometry
import com.archilab.rpa2024.engine.secondary.ParapetWallDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseDesignInput
import com.archilab.rpa2024.engine.secondary.StaircaseDesignResult
import com.archilab.rpa2024.engine.secondary.StaircaseDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.ContinuousSlabInput
import com.archilab.rpa2024.engine.slabs.ContinuousSlabResult
import com.archilab.rpa2024.engine.walls.WallBoundaryDesign
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignResult
import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.ParsedEtabsModel
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Parametres de composition des planches d'execution. */
data class DetailingPlanRequest(
    val model: ParsedEtabsModel,
    val results: EtabsResults = EtabsResults(),
    val foundationModel: SafeFoundationModel? = null,
    val parapetGeometry: ParapetGeometry = ParapetGeometry(),
    val staircaseGeometries: List<StaircaseGeometry> = emptyList(),
    val project: CadProjectInfo = CadProjectInfo(),
    val sheetFormat: CadSheetFormat = CadSheetFormat.A1,
    val slabLiveLoadKNM2: Double = 1.5,
    val slabFinishingLoadKNM2: Double = 1.2,
    val slabPermanentSuperLoadKNM2: Double = 0.0,
    val parapetLengthMeters: Double? = null,
    val interSheetGapMm: Double = 120.0,
    val startingSheetNumber: Int = 1
) {
    init {
        require(slabLiveLoadKNM2 >= 0.0) { "La surcharge du plancher doit etre positive ou nulle." }
        require(slabFinishingLoadKNM2 >= 0.0) { "La charge de revetement doit etre positive ou nulle." }
        require(slabPermanentSuperLoadKNM2 >= 0.0) { "La surcharge permanente doit etre positive ou nulle." }
        require(parapetLengthMeters == null || parapetLengthMeters > 0.0) {
            "La longueur d'acrotere doit etre positive."
        }
        require(interSheetGapMm >= 0.0) { "L'espacement des planches doit etre positif ou nul." }
    }
}

/** Compte rendu des planches de ferraillage et de la nomenclature associee. */
data class DetailingPlanResult(
    val sheetTitles: List<String>,
    val sheetCount: Int,
    val columnCount: Int,
    val beamCount: Int,
    val wallCount: Int,
    val slabCount: Int,
    val staircaseCount: Int,
    val footingCount: Int,
    val schedule: BarBendingSchedule,
    val widthMm: Double,
    val heightMm: Double,
    val warnings: List<String>
)

/**
 * Genere les planches d'execution de ferraillage a partir des moteurs BAEL/RPA 2024.
 * Chaque famille ou element produit une planche cotee et une ou plusieurs positions de
 * nomenclature. Les coupes sont dessinees a 1/20 (ou 1/10 pour les petits elements),
 * les elevations a 1/50 ou 1/100 selon leur hauteur.
 */
class StructuralDetailingDxfExporter(
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val slabSolver: ContinuousRibbedSlabSolver = ContinuousRibbedSlabSolver(),
    private val parapetDesigner: ParapetWallDesigner = ParapetWallDesigner(),
    private val staircaseDesigner: StaircaseDesigner = StaircaseDesigner(),
    private val footingOrchestrator: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator()
) {

    fun draw(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        originX: Double = 0.0,
        originY: Double = 0.0
    ): DetailingPlanResult {
        val joint = request.model.joint
        val warnings = mutableListOf<String>()
        val collector = RebarScheduleCollector()
        var sheetNumber = request.startingSheetNumber
        var sheetIndex = 0
        val sheetTitles = mutableListOf<String>()

        val columns = columnEngine.design(joint, request.results)
        val beams = beamEngine.design(joint, request.results)
        val walls = wallEngine.design(joint, request.results)
        val slabs = solveSlabs(request, warnings)
        val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)
        val parapet = parapetDesigner.design(
            ParapetDesignInput(geometry = request.parapetGeometry, materials = joint.materials, zoneAccelerationA = resolved.zone.coeffA)
        )
        val staircases = request.staircaseGeometries.map {
            staircaseDesigner.design(StaircaseDesignInput(geometry = it, materials = joint.materials))
        }
        val footings = request.foundationModel?.let { footingOrchestrator.design(it, joint.materials) }

        fun nextSheetX(): Double = originX + sheetIndex * (request.sheetFormat.widthMm + request.interSheetGapMm)

        columns.columns.forEach { design ->
            val title = "FERRAILLAGE POTEAU ${design.family.name} - ${design.family.geometryLabel}"
            drawColumnSheet(document, request, design, collector, nextSheetX(), originY, sheetNumber, title)
            sheetTitles += title
            sheetIndex++
            sheetNumber++
        }
        beams.beams.forEach { design ->
            val title = "FERRAILLAGE POUTRE ${design.family.name} - ${design.family.geometryLabel}"
            drawBeamSheet(document, request, design, collector, nextSheetX(), originY, sheetNumber, title)
            sheetTitles += title
            sheetIndex++
            sheetNumber++
        }
        walls.walls.forEach { design ->
            val title = "FERRAILLAGE VOILE ${design.family.pierName} - ${design.family.geometryLabel}"
            drawWallSheet(document, request, design, collector, nextSheetX(), originY, sheetNumber, title)
            sheetTitles += title
            sheetIndex++
            sheetNumber++
        }
        slabs.forEach { slab ->
            val title = "FERRAILLAGE POUTRELLES - ${slab.topology.name}"
            drawRibSheet(document, request, slab, collector, nextSheetX(), originY, sheetNumber, title)
            sheetTitles += title
            sheetIndex++
            sheetNumber++
        }
        val parapetTitle = "FERRAILLAGE ACROTERE - ${parapet.geometry.geometryLabel}"
        drawParapetSheet(
            document,
            request,
            joint,
            parapet,
            collector,
            nextSheetX(),
            originY,
            sheetNumber,
            parapetTitle
        )
        sheetTitles += parapetTitle
        sheetIndex++
        sheetNumber++

        staircases.forEach { staircase ->
            val title = "FERRAILLAGE ESCALIER - ${staircase.geometry.name}"
            drawStaircaseSheet(document, request, staircase, collector, nextSheetX(), originY, sheetNumber, title)
            sheetTitles += title
            sheetIndex++
            sheetNumber++
        }
        footings?.footings?.forEach { footing ->
            val title = "FERRAILLAGE SEMELLE ${footing.family.name} - ${footing.family.sectionLabel}"
            drawFootingSheet(document, request, joint, footing, collector, nextSheetX(), originY, sheetNumber, title)
            sheetTitles += title
            sheetIndex++
            sheetNumber++
        }

        warnings += columns.chapter.lines.filter { it.startsWith("Attention") }
        warnings += beams.chapter.lines.filter { it.startsWith("Attention") }
        warnings += footings?.warnings.orEmpty()
        warnings += parapet.notes.filter { it.contains("Attention") }
        val schedule = collector.toSchedule(request.project.projectTitle, joint.jointName, warnings.distinct())
        val totalSheets = sheetTitles.size
        val width = if (totalSheets == 0) 0.0 else {
            totalSheets * request.sheetFormat.widthMm + (totalSheets - 1) * request.interSheetGapMm
        }
        return DetailingPlanResult(
            sheetTitles = sheetTitles,
            sheetCount = totalSheets,
            columnCount = columns.columns.size,
            beamCount = beams.beams.size,
            wallCount = walls.walls.size,
            slabCount = slabs.size,
            staircaseCount = staircases.size,
            footingCount = footings?.footings?.size ?: 0,
            schedule = schedule,
            widthMm = width,
            heightMm = request.sheetFormat.heightMm,
            warnings = warnings.distinct()
        )
    }

    fun export(request: DetailingPlanRequest, path: Path): DetailingPlanResult {
        val document = DxfCoreEngine("FERRAILLAGE_${request.model.jointName}")
        val result = draw(document, request)
        document.write(path)
        return result
    }

    // ------------------------------------------------------------------ poteaux

    private fun drawColumnSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        design: ColumnDesignResult,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val family = design.family
        val transverse = design.transverse
        val layout = design.layout
        val elementCount = max(1, family.elementIds.size)
        val lcl = transverse.clearHeightMeters
        if (lcl <= 0.0) {
            return
        }
        val topStory = request.model.joint.storySystem.orderedStories.lastOrNull()?.storyName
        val isTopLevel = family.stories.any { it.equals(topStory, ignoreCase = true) }
        val lap = transverse.lapLengthCm / 100.0
        val anchorage = transverse.anchorageLengthCm / 100.0
        val hook = if (isTopLevel) anchorage else 0.0
        val longLength = lcl + max(lap, hook)
        val cornerBar = layout?.let { shape ->
            collector.add(
                elementType = "Poteau",
                elementName = family.name,
                shape = if (isTopLevel) BarShapeType.HOOKED else BarShapeType.STRAIGHT,
                diameterMm = shape.cornerBar.diameterMm,
                countPerElement = shape.cornerBar.count,
                elementCount = elementCount,
                developedLengthMeters = longLength,
                note = if (isTopLevel) "barres de coin, ancrage en tete ${fmt(anchorage)} m"
                else "barres de coin, recouvrement ${fmt(lap)} m"
            )
        }
        val faceBar = layout?.faceBar?.takeIf { it.count > 0 }?.let { shape ->
            collector.add(
                elementType = "Poteau",
                elementName = family.name,
                shape = if (isTopLevel) BarShapeType.HOOKED else BarShapeType.STRAIGHT,
                diameterMm = shape.diameterMm,
                countPerElement = shape.count,
                elementCount = elementCount,
                developedLengthMeters = longLength,
                note = if (isTopLevel) "barres de face, ancrage en tete ${fmt(anchorage)} m"
                else "barres de face, recouvrement ${fmt(lap)} m"
            )
        }
        val primaryLongitudinalPosition = cornerBar?.position ?: faceBar?.position ?: 0
        val stirrupCount = columnStirrupCount(design)
        val (stirrupWidth, stirrupHeight) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
            design.sectionWidthCm / 100.0,
            design.sectionDepthCm / 100.0,
            3.0 / 100.0,
            transverse.stirrupDiameterMm
        )
        val stirrupLength = BarDevelopedLengthCalculator.closedStirrupLengthMeters(
            stirrupWidth, stirrupHeight, transverse.stirrupDiameterMm
        )
        val stirrupItem = collector.add(
            elementType = "Poteau",
            elementName = family.name,
            shape = BarShapeType.STIRRUP,
            diameterMm = transverse.stirrupDiameterMm,
            countPerElement = stirrupCount,
            elementCount = elementCount,
            developedLengthMeters = stirrupLength,
            spacingCm = transverse.nodalSpacingCm,
            note = "cadres ${transverse.stirrupLegs} brins, zones nodales"
        )
        collector.addConcrete(
            "Poteau",
            family.name,
            design.sectionWidthCm / 100.0 * design.sectionDepthCm / 100.0 * lcl * elementCount
        )

        val layoutSheet = addSheet(document, request, originX, originY, sheetNumber, title, "1/50 - 1/20")
        val left = layoutSheet.drawingLeft
        val bottom = layoutSheet.drawingBottom
        val elevationScale = preferredLinearScale(lcl, layoutSheet.drawingHeight * 0.62, preferred = 50)
        val elevationHeight = lcl * 1000.0 / elevationScale
        val baseY = bottom + (layoutSheet.drawingHeight - elevationHeight) * 0.45
        val centerX = left + layoutSheet.drawingWidth * 0.18
        val columnWidth = design.sectionWidthCm / 100.0 * 1000.0 / elevationScale
        addViewTitle(
            document,
            DxfPoint(centerX, baseY + elevationHeight + 20.0),
            "ELEVATION ${family.name} (1/$elevationScale)"
        )
        drawColumnElevation(
            document,
            design,
            DxfPoint(centerX, baseY),
            columnWidth,
            elevationHeight,
            elevationScale,
            primaryLongitudinalPosition,
            stirrupItem?.position ?: 0
        )

        val sectionScale = 20
        val sectionCenter = DxfPoint(
            left + layoutSheet.drawingWidth * 0.52,
            bottom + layoutSheet.drawingHeight * 0.55
        )
        addViewTitle(
            document,
            DxfPoint(sectionCenter.x, sectionCenter.y + design.sectionDepthCm / 100.0 * 1000.0 / sectionScale / 2.0 + 18.0),
            "COUPE A-A ${fmt(design.sectionWidthCm, 0)}x${fmt(design.sectionDepthCm, 0)} cm (1/$sectionScale)"
        )
        drawColumnSection(
            document,
            design,
            sectionCenter,
            sectionScale,
            primaryLongitudinalPosition,
            stirrupItem?.position ?: 0
        )
        drawColumnNotes(document, layoutSheet, design, isTopLevel)
    }

    private fun drawColumnElevation(
        document: DxfCoreEngine,
        design: ColumnDesignResult,
        base: DxfPoint,
        width: Double,
        height: Double,
        scale: Int,
        longitudinalPosition: Int,
        stirrupPosition: Int
    ) {
        val transverse = design.transverse
        val outline = listOf(
            DxfPoint(base.x - width / 2.0, base.y),
            DxfPoint(base.x + width / 2.0, base.y),
            DxfPoint(base.x + width / 2.0, base.y + height),
            DxfPoint(base.x - width / 2.0, base.y + height)
        )
        document.addLwPolyline(CadLayerStandard.COLUMN_FORMWORK, outline, closed = true)
        document.addHatch(CadLayerStandard.COLUMN_FORMWORK, outline, pattern = "ANSI31", patternScale = 0.5)
        // Nœuds inferieur et superieur.
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x - width, base.y, base.x + width, base.y)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x - width, base.y, base.x - width, base.y - 13.0)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x + width, base.y, base.x + width, base.y - 13.0)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x - width, base.y + height, base.x + width, base.y + height)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x - width, base.y + height, base.x - width, base.y + height + 10.0)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x + width, base.y + height, base.x + width, base.y + height + 10.0)
        document.addLine(CadLayerStandard.BEAM_FORMWORK, base.x - width, base.y + height + 10.0, base.x + width, base.y + height + 10.0)

        // Barres longitudinales visibles, recouvrement et ancrage.
        val barOffset = width * 0.34
        listOf(-barOffset, barOffset).forEachIndexed { index, offset ->
            document.addLine(
                CadLayerStandard.LONGITUDINAL_STEEL,
                base.x + offset,
                base.y,
                base.x + offset,
                base.y + height
            )
            if (index == 0) {
                val lapHeight = transverse.lapLengthCm / 100.0 * 1000.0 / scale
                document.addLine(
                    CadLayerStandard.TRANSVERSE_STEEL,
                    base.x + offset - 2.0,
                    base.y,
                    base.x + offset - 2.0,
                    base.y + lapHeight
                )
                document.addText(
                    CadLayerStandard.TEXT,
                    base.x + offset + 3.0,
                    base.y + lapHeight / 2.0,
                    "Lrec=${fmt(transverse.lapLengthCm / 100.0)} m",
                    1.8
                )
            }
        }
        // Cadres dresses dans les deux zones nodales et la zone courante.
        val nodalHeight = transverse.nodalZoneLengthCm / 100.0 * 1000.0 / scale
        val nodalSpacing = transverse.nodalSpacingCm / 100.0 * 1000.0 / scale
        val currentSpacing = transverse.currentSpacingCm / 100.0 * 1000.0 / scale
        drawStirrupLines(document, base, width, height, nodalHeight, nodalSpacing, currentSpacing)
        val lineLeft = base.x - width / 2.0
        val lineRight = base.x + width / 2.0
        CadGeometry.dashedLine(
            document,
            CadLayerStandard.DIMENSIONS,
            DxfPoint(lineLeft, base.y + nodalHeight),
            DxfPoint(lineRight, base.y + nodalHeight),
            dashMm = 3.0,
            gapMm = 1.8
        )
        CadGeometry.dashedLine(
            document,
            CadLayerStandard.DIMENSIONS,
            DxfPoint(lineLeft, base.y + height - nodalHeight),
            DxfPoint(lineRight, base.y + height - nodalHeight),
            dashMm = 3.0,
            gapMm = 1.8
        )
        document.addText(
            CadLayerStandard.TEXT,
            base.x + width / 2.0 + 2.0,
            base.y + nodalHeight + 1.5,
            "Lcr=${fmt(transverse.nodalZoneLengthCm / 100.0)} m",
            1.8
        )
        document.addText(
            CadLayerStandard.TEXT,
            base.x + width / 2.0 + 2.0,
            base.y + height - nodalHeight + 1.5,
            "Lcr=${fmt(transverse.nodalZoneLengthCm / 100.0)} m",
            1.8
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(base.x + barOffset, base.y + height * 0.65),
            DxfPoint(base.x + width + 30.0, base.y + height * 0.65),
            longitudinalPosition,
            "Barres longitudinales\n${design.layout?.label ?: "-"}\nRecouvrement ${fmt(transverse.lapLengthCm)} cm",
            bubbleRadiusMm = 3.3,
            textHeightMm = 1.7
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(base.x, base.y + nodalHeight * 0.55),
            DxfPoint(base.x + width + 30.0, base.y + nodalHeight * 0.55),
            stirrupPosition,
            "Cadres ${transverse.stirrupLabel}\n"
                + "t=${fmt(transverse.nodalSpacingCm)} cm (nodal)\n"
                + "t'=${fmt(transverse.currentSpacingCm)} cm (courant)",
            bubbleRadiusMm = 3.3,
            textHeightMm = 1.7
        )
        horizontalDimension(
            document,
            base.x - width / 2.0,
            base.x + width / 2.0,
            base.y + height + 16.0,
            base.y + height + 25.0,
            "${fmt(design.sectionWidthCm, 0)} cm"
        )
        verticalDimension(
            document,
            base.y,
            base.y + height,
            base.x - width / 2.0 - 15.0,
            "lcl = ${fmt(transverse.clearHeightMeters)} m"
        )
        verticalDimension(
            document,
            base.y + nodalHeight,
            base.y + height,
            base.x - width / 2.0 - 25.0,
            "zone courante"
        )
    }

    private fun drawColumnSection(
        document: DxfCoreEngine,
        design: ColumnDesignResult,
        center: DxfPoint,
        scale: Int,
        longitudinalPosition: Int,
        stirrupPosition: Int
    ) {
        val width = design.sectionWidthCm / 100.0 * 1000.0 / scale
        val height = design.sectionDepthCm / 100.0 * 1000.0 / scale
        val coverPage = 3.0 / 100.0 * 1000.0 / scale
        val outline = CadGeometry.rectangle(center.x, center.y, width, height)
        document.addLwPolyline(CadLayerStandard.COLUMN_FORMWORK, outline, closed = true)
        document.addHatch(CadLayerStandard.COLUMN_FORMWORK, outline, pattern = "ANSI31", patternScale = 0.45)
        val stirrup = CadGeometry.rectangle(
            center.x,
            center.y,
            width - 2.0 * coverPage,
            height - 2.0 * coverPage
        )
        document.addLwPolyline(CadLayerStandard.TRANSVERSE_STEEL, stirrup, closed = true)
        // Crochets a 135 deg aux deux angles superieurs.
        document.addLine(
            CadLayerStandard.TRANSVERSE_STEEL,
            center.x - width / 2.0 + coverPage,
            center.y + height / 2.0 - coverPage,
            center.x - width / 2.0 + coverPage + 4.0,
            center.y + height / 2.0 - coverPage - 3.0
        )
        document.addLine(
            CadLayerStandard.TRANSVERSE_STEEL,
            center.x + width / 2.0 - coverPage,
            center.y - height / 2.0 + coverPage,
            center.x + width / 2.0 - coverPage - 4.0,
            center.y - height / 2.0 + coverPage + 3.0
        )
        val layout = design.layout
        if (layout != null) {
            barPositions(layout, center, width, height, coverPage).forEach { point ->
                document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, point.x, point.y, 1.35)
            }
        }
        horizontalDimension(
            document,
            center.x - width / 2.0,
            center.x + width / 2.0,
            center.y - height / 2.0 - 8.0,
            center.y - height / 2.0 - 16.0,
            "${fmt(design.sectionWidthCm, 0)} cm"
        )
        verticalDimension(
            document,
            center.y - height / 2.0,
            center.y + height / 2.0,
            center.x + width / 2.0 + 12.0,
            "${fmt(design.sectionDepthCm, 0)} cm"
        )
        document.addText(
            CadLayerStandard.TEXT,
            center.x,
            center.y - height / 2.0 - 21.0,
            "Enrobage 3 cm - cadres ${design.transverse.stirrupLabel}",
            1.75,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x + width * 0.25, center.y + height * 0.28),
            DxfPoint(center.x + width / 2.0 + 28.0, center.y + height / 2.0 + 25.0),
            longitudinalPosition,
            "Longitudinal\n${layout?.label ?: "-"}\n${fmt(design.adoptedAsCm2)} cm2",
            bubbleRadiusMm = 3.1,
            textHeightMm = 1.6
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x - width * 0.30, center.y - height * 0.25),
            DxfPoint(center.x - width / 2.0 - 28.0, center.y - height / 2.0 - 28.0),
            stirrupPosition,
            "Cadre ferme 135 deg\n${design.transverse.stirrupLegs} brins " +
                "phi${design.transverse.stirrupDiameterMm}\n${fmt(design.transverse.stirrupAreaCm2)} cm2",
            bubbleRadiusMm = 3.1,
            textHeightMm = 1.6
        )
    }

    private fun drawColumnNotes(
        document: DxfCoreEngine,
        layout: CadSheetLayout,
        design: ColumnDesignResult,
        topLevel: Boolean
    ) {
        val lines = listOf(
            "As calcule = ${fmt(design.requiredAsCm2)} cm2 ; As,min RPA = ${fmt(design.minimumAsCm2)} cm2 ; " +
                "As adopte = ${fmt(design.adoptedAsCm2)} cm2.",
            "Zone critique Lcr = ${fmt(design.transverse.nodalZoneLengthCm)} cm a chaque extremite ; " +
                "recouvrement Lrec = ${fmt(design.transverse.lapLengthCm)} cm.",
            "Effort normal reduit nu = ${fmt(design.normalizedAxial, 3)} <= ${fmt(design.normalizedAxialLimit, 2)}.",
            if (topLevel) {
                "Ancrage de tete par retour d'equerre >= ${fmt(design.transverse.anchorageLengthCm)} cm."
            } else {
                "Recouvrement en pied ${fmt(design.transverse.lapLengthCm)} cm dans le noeud inferieur."
            },
            "Nu = ${fmt(design.axialCompressionKN)} kN ; section ${fmt(design.sectionWidthCm, 0)} x " +
                "${fmt(design.sectionDepthCm, 0)} cm."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + 2.0,
            layout.drawingBottom + 3.0,
            lines.joinToString("\n"),
            heightMm = 1.8,
            widthMm = layout.drawingWidth - 4.0
        )
    }

    private fun columnStirrupCount(design: ColumnDesignResult): Int {
        val transverse = design.transverse
        val lclMeters = transverse.clearHeightMeters
        if (lclMeters <= 0.0) return 0
        val nodal = transverse.nodalZoneLengthCm / 100.0
        val nodalSpacing = transverse.nodalSpacingCm / 100.0
        val currentSpacing = transverse.currentSpacingCm / 100.0
        val effectiveNodal = min(nodal, lclMeters / 2.0)
        val nodalCount = 2.0 * ceil(effectiveNodal / nodalSpacing)
        val currentLength = max(0.0, lclMeters - 2.0 * effectiveNodal)
        val currentCount = ceil(currentLength / currentSpacing)
        return (nodalCount + currentCount).toInt().coerceAtLeast(1)
    }

    private fun barPositions(
        layout: ColumnRebarLayout,
        center: DxfPoint,
        width: Double,
        height: Double,
        cover: Double
    ): List<DxfPoint> {
        val points = mutableListOf<DxfPoint>()
        val left = center.x - width / 2.0 + cover
        val right = center.x + width / 2.0 - cover
        val bottom = center.y - height / 2.0 + cover
        val top = center.y + height / 2.0 - cover
        val horizontalCount = layout.barsPerLongFace.coerceAtLeast(2)
        val verticalCount = layout.barsPerShortFace.coerceAtLeast(2)
        for (index in 0 until horizontalCount) {
            val fraction = index.toDouble() / (horizontalCount - 1)
            points += DxfPoint(left + (right - left) * fraction, bottom)
            points += DxfPoint(left + (right - left) * fraction, top)
        }
        for (index in 1 until verticalCount - 1) {
            val fraction = index.toDouble() / (verticalCount - 1)
            points += DxfPoint(left, bottom + (top - bottom) * fraction)
            points += DxfPoint(right, bottom + (top - bottom) * fraction)
        }
        return points.distinctBy { "${round(it.x)}:${round(it.y)}" }
    }

    private fun drawStirrupLines(
        document: DxfCoreEngine,
        base: DxfPoint,
        width: Double,
        height: Double,
        nodalHeight: Double,
        nodalSpacing: Double,
        currentSpacing: Double
    ) {
        val left = base.x - width / 2.0
        val right = base.x + width / 2.0
        fun drawSpaced(start: Double, end: Double, spacing: Double) {
            if (spacing <= 0.0 || end <= start) return
            var current = start
            while (current <= end + 1e-6) {
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, left, current, right, current)
                current += spacing
            }
        }
        drawSpaced(base.y, base.y + min(nodalHeight, height), nodalSpacing)
        drawSpaced(base.y + height - min(nodalHeight, height), base.y + height, nodalSpacing)
        drawSpaced(base.y + min(nodalHeight, height), base.y + height - min(nodalHeight, height), currentSpacing)
    }

    // ------------------------------------------------------------------ poutres

    private fun drawBeamSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        design: BeamDesignResult,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val family = design.family
        val elementCount = max(1, family.elementIds.size)
        val span = max(family.maxClearSpanMeters, 1e-3)
        val transverse = design.transverse
        val holePosition = addBeamBbsItems(design, collector, elementCount)
        collector.addConcrete(
            "Poutre",
            family.name,
            design.widthCm / 100.0 * design.heightCm / 100.0 * span * elementCount
        )
        val layoutSheet = addSheet(document, request, originX, originY, sheetNumber, title, "1/50 - 1/20")
        val scale = preferredLinearScale(span, layoutSheet.drawingWidth * 0.62, preferred = 50)
        val elevationWidth = span * 1000.0 / scale
        val elevationHeight = design.heightCm / 100.0 * 1000.0 / scale
        val start = DxfPoint(
            layoutSheet.drawingLeft + layoutSheet.drawingWidth * 0.68 - elevationWidth / 2.0,
            layoutSheet.drawingBottom + layoutSheet.drawingHeight * 0.68
        )
        val end = DxfPoint(start.x + elevationWidth, start.y)
        addViewTitle(
            document,
            DxfPoint(start.x, start.y + elevationHeight + 38.0),
            "ELEVATION LONGITUDINALE ${family.name} (1/$scale)"
        )
        drawBeamElevation(
            document,
            design,
            start,
            end,
            elevationHeight,
            scale,
            holePosition
        )
        val sectionScale = 20
        val supportCenter = DxfPoint(
            layoutSheet.drawingLeft + layoutSheet.drawingWidth * 0.14,
            layoutSheet.drawingBottom + layoutSheet.drawingHeight * 0.20
        )
        val spanCenter = DxfPoint(
            layoutSheet.drawingLeft + layoutSheet.drawingWidth * 0.36,
            layoutSheet.drawingBottom + layoutSheet.drawingHeight * 0.20
        )
        addViewTitle(
            document,
            DxfPoint(supportCenter.x, supportCenter.y + design.heightCm / 100.0 * 1000.0 / sectionScale / 2.0 + 12.0),
            "COUPE APPUI (1/$sectionScale)"
        )
        drawBeamSection(document, design, supportCenter, sectionScale, support = true, position = holePosition.support)
        addViewTitle(
            document,
            DxfPoint(spanCenter.x, spanCenter.y + design.heightCm / 100.0 * 1000.0 / sectionScale / 2.0 + 12.0),
            "COUPE TRAVEE (1/$sectionScale)"
        )
        drawBeamSection(document, design, spanCenter, sectionScale, support = false, position = holePosition.span)
        drawBeamNotes(document, layoutSheet, design)
    }

    private data class BeamBbsPositions(
        val bottom: Int,
        val top: Int,
        val stirrup: Int,
        val support: Int,
        val span: Int
    )

    private fun addBeamBbsItems(
        design: BeamDesignResult,
        collector: RebarScheduleCollector,
        elementCount: Int
    ): BeamBbsPositions {
        val family = design.family
        val span = family.maxClearSpanMeters
        val transverse = design.transverse
        val anchorage = transverse.lapLengthCm / 100.0
        val chapeauLength = min(span, 2.0 * transverse.criticalZoneLengthCm / 100.0 + 2.0 * anchorage)
        val bottomLayout = design.spanLayout
        val topLayout = design.supportLayout
        var bottomPosition = 0
        if (bottomLayout != null) {
            val continuous = collector.add(
                elementType = "Poutre",
                elementName = family.name,
                shape = BarShapeType.HOOKED,
                diameterMm = bottomLayout.continuousBar.diameterMm,
                countPerElement = bottomLayout.continuousBar.count,
                elementCount = elementCount,
                developedLengthMeters = span + 2.0 * anchorage,
                note = "nappe inferieure filante ${bottomLayout.continuousBar.label}"
            )
            bottomPosition = continuous?.position ?: 0
            bottomLayout.additionalBar?.let { additional ->
                collector.add(
                    elementType = "Poutre",
                    elementName = family.name,
                    shape = BarShapeType.HOOKED,
                    diameterMm = additional.diameterMm,
                    countPerElement = additional.count,
                    elementCount = elementCount,
                    developedLengthMeters = span + 2.0 * anchorage,
                    note = "renforts inferieurs de travée ${additional.label}"
                )
            }
        }
        var topPosition = 0
        if (topLayout != null) {
            val continuous = collector.add(
                elementType = "Poutre",
                elementName = family.name,
                shape = BarShapeType.HAT,
                diameterMm = topLayout.continuousBar.diameterMm,
                countPerElement = topLayout.continuousBar.count,
                elementCount = elementCount,
                developedLengthMeters = span + 2.0 * anchorage,
                note = "nappe superieure filante ${topLayout.continuousBar.label}"
            )
            topPosition = continuous?.position ?: 0
            topLayout.additionalBar?.let { additional ->
                collector.add(
                    elementType = "Poutre",
                    elementName = family.name,
                    shape = BarShapeType.HAT,
                    diameterMm = additional.diameterMm,
                    countPerElement = additional.count * 2,
                    elementCount = elementCount,
                    developedLengthMeters = chapeauLength,
                    note = "chapeaux aux deux appuis ${additional.label}"
                )
            }
        }
        val (stirrupWidth, stirrupHeight) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
            design.widthCm / 100.0,
            design.heightCm / 100.0,
            0.025,
            transverse.stirrupDiameterMm
        )
        val stirrupLength = BarDevelopedLengthCalculator.closedStirrupLengthMeters(
            stirrupWidth, stirrupHeight, transverse.stirrupDiameterMm
        )
        val stirrupCount = BarDevelopedLengthCalculator.stirrupCount(
            totalLengthMeters = span,
            firstStirrupFromSupportMeters = transverse.firstStirrupCm / 100.0,
            nodalZoneLengthMeters = transverse.nodalZoneLengthCm / 100.0,
            nodalSpacingMeters = transverse.nodalSpacingCm / 100.0,
            currentSpacingMeters = transverse.currentSpacingCm / 100.0
        )
        val stirrupItem = collector.add(
            elementType = "Poutre",
            elementName = family.name,
            shape = BarShapeType.STIRRUP,
            diameterMm = transverse.stirrupDiameterMm,
            countPerElement = stirrupCount,
            elementCount = elementCount,
            developedLengthMeters = stirrupLength,
            spacingCm = transverse.nodalSpacingCm,
            note = "premier cadre a ${fmt(transverse.firstStirrupCm)} cm du nu"
        )
        return BeamBbsPositions(
            bottom = bottomPosition,
            top = topPosition,
            stirrup = stirrupItem?.position ?: 0,
            support = topPosition,
            span = bottomPosition
        )
    }

    private fun drawBeamElevation(
        document: DxfCoreEngine,
        design: BeamDesignResult,
        start: DxfPoint,
        end: DxfPoint,
        height: Double,
        scale: Int,
        positions: BeamBbsPositions
    ) {
        val top = start.y + height
        val outline = listOf(
            DxfPoint(start.x, start.y),
            DxfPoint(end.x, end.y),
            DxfPoint(end.x, end.y + height),
            DxfPoint(start.x, start.y + height)
        )
        document.addLwPolyline(CadLayerStandard.BEAM_FORMWORK, outline, closed = true)
        // Poteaux d'appui schematises.
        val supportWidth = 22.0
        document.addLwPolyline(
            CadLayerStandard.COLUMN_FORMWORK,
            CadGeometry.rectangle(start.x, start.y - 18.0, supportWidth, 38.0),
            closed = true
        )
        document.addLwPolyline(
            CadLayerStandard.COLUMN_FORMWORK,
            CadGeometry.rectangle(end.x, end.y - 18.0, supportWidth, 38.0),
            closed = true
        )
        val cover = 5.0
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            start.x + cover,
            start.y + 4.0,
            end.x - cover,
            end.y + 4.0
        )
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            start.x + cover,
            top - 4.0,
            end.x - cover,
            top - 4.0
        )
        // Ancrages courbes aux extremites.
        listOf(start to 1.0, end to -1.0).forEach { (point, sign) ->
            document.addArc(
                CadLayerStandard.LONGITUDINAL_STEEL,
                point.x + sign * 8.0,
                point.y + 4.0,
                8.0,
                if (sign > 0) 180.0 else 0.0,
                if (sign > 0) 270.0 else 90.0
            )
            document.addArc(
                CadLayerStandard.LONGITUDINAL_STEEL,
                point.x + sign * 8.0,
                top - 4.0,
                8.0,
                if (sign > 0) 90.0 else 270.0,
                if (sign > 0) 180.0 else 360.0
            )
        }
        // Chapeaux aux appuis.
        val critical = design.transverse.criticalZoneLengthCm / 100.0 * 1000.0 / scale
        listOf(start, end).forEach { support ->
            val direction = if (support == start) 1.0 else -1.0
            document.addLine(
                CadLayerStandard.LONGITUDINAL_STEEL,
                support.x,
                top - 5.0,
                support.x + direction * critical,
                top - 5.0
            )
        }
        val firstStirrup = design.transverse.firstStirrupCm / 100.0 * 1000.0 / scale
        val nodal = design.transverse.nodalZoneLengthCm / 100.0 * 1000.0 / scale
        val nodalSpacing = design.transverse.nodalSpacingCm / 100.0 * 1000.0 / scale
        val currentSpacing = design.transverse.currentSpacingCm / 100.0 * 1000.0 / scale
        drawBeamStirrups(
            document,
            start,
            end,
            height,
            firstStirrup,
            nodal,
            nodalSpacing,
            currentSpacing
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint((start.x + end.x) / 2.0, start.y + 4.0),
            DxfPoint((start.x + end.x) / 2.0 + 18.0, start.y - 20.0),
            positions.bottom,
            "Nappe inferieure\n${design.spanLayout?.label ?: "-"}\n${fmt(design.bottomAdoptedAsCm2)} cm2",
            bubbleRadiusMm = 3.2,
            textHeightMm = 1.65
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint((start.x + end.x) / 2.0, top - 5.0),
            DxfPoint((start.x + end.x) / 2.0 + 18.0, top + 18.0),
            positions.top,
            "Nappe superieure\n${design.supportLayout?.label ?: "-"}\n${fmt(design.topAdoptedAsCm2)} cm2",
            bubbleRadiusMm = 3.2,
            textHeightMm = 1.65
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(start.x + firstStirrup, start.y + height * 0.45),
            DxfPoint(start.x + firstStirrup + 16.0, start.y + height * 0.45 + 17.0),
            positions.stirrup,
            "Cadres ${design.transverse.stirrupLabel}\n"
                + "S=${fmt(design.transverse.nodalSpacingCm)} cm\n"
                + "S'=${fmt(design.transverse.currentSpacingCm)} cm",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
        horizontalDimension(
            document,
            start.x,
            end.x,
            start.y - 30.0,
            start.y - 40.0,
            "L = ${fmt(design.family.maxClearSpanMeters)} m"
        )
        verticalDimension(
            document,
            start.y,
            top,
            end.x + 14.0,
            "h = ${fmt(design.heightCm, 0)} cm"
        )
        horizontalDimension(
            document,
            start.x,
            start.x + nodal,
            start.y - 54.0,
            start.y - 64.0,
            "l' = ${fmt(design.transverse.nodalZoneLengthCm / 100.0)} m"
        )
    }

    private fun drawBeamStirrups(
        document: DxfCoreEngine,
        start: DxfPoint,
        end: DxfPoint,
        height: Double,
        firstStirrup: Double,
        nodal: Double,
        nodalSpacing: Double,
        currentSpacing: Double
    ) {
        val top = start.y + height
        val left = start.x + firstStirrup
        val right = end.x - firstStirrup
        fun drawAt(x: Double) {
            if (x !in start.x..end.x) return
            document.addLine(CadLayerStandard.TRANSVERSE_STEEL, x, start.y + 2.0, x, top - 2.0)
        }
        var cursor = left
        while (cursor <= min(left + nodal, right)) {
            drawAt(cursor)
            cursor += nodalSpacing
        }
        cursor = max(right - nodal, left)
        while (cursor <= right) {
            drawAt(cursor)
            cursor += nodalSpacing
        }
        cursor = left + nodal + currentSpacing
        while (cursor < right - nodal) {
            drawAt(cursor)
            cursor += currentSpacing
        }
        document.addText(
            CadLayerStandard.TEXT,
            start.x + nodal / 2.0,
            top + 4.0,
            "1er cadre a ${fmt(firstStirrup)}",
            1.55,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun drawBeamSection(
        document: DxfCoreEngine,
        design: BeamDesignResult,
        center: DxfPoint,
        scale: Int,
        support: Boolean,
        position: Int
    ) {
        val width = design.widthCm / 100.0 * 1000.0 / scale
        val height = design.heightCm / 100.0 * 1000.0 / scale
        val outline = CadGeometry.rectangle(center.x, center.y, width, height)
        document.addLwPolyline(CadLayerStandard.BEAM_FORMWORK, outline, closed = true)
        if (support) {
            document.addHatch(CadLayerStandard.BEAM_FORMWORK, outline, pattern = "ANSI31", patternScale = 0.8)
        }
        val cover = 2.5 / 100.0 * 1000.0 / scale
        document.addLwPolyline(
            CadLayerStandard.TRANSVERSE_STEEL,
            CadGeometry.rectangle(center.x, center.y, width - 2.0 * cover, height - 2.0 * cover),
            closed = true
        )
        val layout = if (support) design.supportLayout else design.spanLayout
        val barRadius = max(0.9, (layout?.maxDiameterMm ?: 12) / (2.0 * scale))
        val count = layout?.totalBars ?: 0
        if (count > 0) {
            val totalWidth = width - 2.0 * (cover + 2.0)
            val spacing = if (count > 1) totalWidth / (count - 1) else 0.0
            for (index in 0 until count) {
                val x = center.x - totalWidth / 2.0 + index * spacing
                val y = if (support) center.y + height / 2.0 - cover - 2.0 else center.y - height / 2.0 + cover + 2.0
                document.addCircle(
                    CadLayerStandard.LONGITUDINAL_STEEL,
                    x,
                    y,
                    barRadius
                )
            }
            if (support) {
                // Chapeau inferieur de continuite visible.
                document.addCircle(
                    CadLayerStandard.LONGITUDINAL_STEEL,
                    center.x - width * 0.25,
                    center.y - height / 2.0 + cover + 2.0,
                    barRadius
                )
                document.addCircle(
                    CadLayerStandard.LONGITUDINAL_STEEL,
                    center.x + width * 0.25,
                    center.y - height / 2.0 + cover + 2.0,
                    barRadius
                )
            }
        }
        document.addText(
            CadLayerStandard.TEXT,
            center.x,
            center.y + height / 2.0 + 5.0,
            "${fmt(design.widthCm, 0)}x${fmt(design.heightCm, 0)} cm",
            1.8,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        horizontalDimension(
            document,
            center.x - width / 2.0,
            center.x + width / 2.0,
            center.y - height / 2.0 - 5.0,
            center.y - height / 2.0 - 11.0,
            "${fmt(design.widthCm, 0)} cm"
        )
        verticalDimension(
            document,
            center.y - height / 2.0,
            center.y + height / 2.0,
            center.x + width / 2.0 + 10.0,
            "${fmt(design.heightCm, 0)} cm"
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x + width * 0.25, center.y),
            DxfPoint(center.x + width / 2.0 + 30.0, center.y + 12.0),
            position,
            if (support) "Coupe appui\n${design.supportLayout?.label ?: "-"}"
            else "Coupe travée\n${design.spanLayout?.label ?: "-"}",
            bubbleRadiusMm = 2.9,
            textHeightMm = 1.55
        )
    }

    private fun drawBeamNotes(document: DxfCoreEngine, layout: CadSheetLayout, design: BeamDesignResult) {
        val lines = listOf(
            "As,inf = ${fmt(design.bottomAdoptedAsCm2)} cm2 ; As,sup = ${fmt(design.topAdoptedAsCm2)} cm2 ; " +
                "As,min RPA = ${fmt(design.minimumAsCm2)} cm2.",
            "Zone nodale l' = ${fmt(design.transverse.nodalZoneLengthCm)} cm ; zone critique " +
                "${fmt(design.transverse.criticalZoneLengthCm)} cm ; Lrec = ${fmt(design.transverse.lapLengthCm)} cm.",
            "Cadres ${design.transverse.stirrupLabel} : S = ${fmt(design.transverse.nodalSpacingCm)} cm en zone " +
                "critique, S' = ${fmt(design.transverse.currentSpacingCm)} cm en zone courante.",
            "Premier cadre a ${fmt(design.transverse.firstStirrupCm)} cm du nu de l'appui."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + 2.0,
            layout.drawingBottom + 3.0,
            lines.joinToString("\n"),
            heightMm = 1.75,
            widthMm = layout.drawingWidth - 4.0
        )
    }

    // ------------------------------------------------------------------ voiles

    private fun drawWallSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        design: WallDesignResult,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val family = design.family
        val elementCount = max(1, family.elementIds.size)
        val hcr = design.envelope.criticalHeight.retainedMeters
        val positions = addWallBbsItems(design, collector, elementCount)
        collector.addConcrete(
            "Voile",
            family.pierName,
            family.lengthLwMeters * family.thicknessBwMeters * family.totalHeightHwMeters * elementCount
        )
        val layoutSheet = addSheet(document, request, originX, originY, sheetNumber, title, "1/50 - 1/100")
        val sectionScale = chooseLinearScale(family.lengthLwMeters, layoutSheet.drawingWidth * 0.55)
        val sectionCenter = DxfPoint(
            layoutSheet.drawingLeft + layoutSheet.drawingWidth * 0.25,
            layoutSheet.drawingTop - 70.0
        )
        addViewTitle(
            document,
            DxfPoint(
                sectionCenter.x,
                sectionCenter.y + family.thicknessBwMeters * 1000.0 / sectionScale / 2.0 + 22.0
            ),
            "COUPE HORIZONTALE EN ZONE CRITIQUE (1/$sectionScale)"
        )
        drawWallSection(document, design, sectionCenter, sectionScale, positions)
        val elevationScale = preferredLinearScale(
            family.totalHeightHwMeters,
            layoutSheet.drawingHeight * 0.76,
            preferred = 50,
            maxDenominator = 150
        )
        val elevationWidth = family.lengthLwMeters * 1000.0 / elevationScale
        val elevationHeight = family.totalHeightHwMeters * 1000.0 / elevationScale
        val elevationOrigin = DxfPoint(
            layoutSheet.drawingLeft + layoutSheet.drawingWidth * 0.60,
            layoutSheet.drawingBottom + 28.0
        )
        addViewTitle(
            document,
            DxfPoint(elevationOrigin.x, elevationOrigin.y + elevationHeight + 14.0),
            "ELEVATION ${family.pierName} (1/$elevationScale)"
        )
        drawWallElevation(
            document,
            design,
            elevationOrigin,
            elevationWidth,
            elevationHeight,
            elevationScale,
            hcr,
            positions
        )
        drawWallNotes(document, layoutSheet, design, hcr)
    }

    private data class WallBbsPositions(
        val boundary: Int,
        val stirrup: Int,
        val verticalMesh: Int,
        val horizontalMesh: Int
    )

    private fun addWallBbsItems(
        design: WallDesignResult,
        collector: RebarScheduleCollector,
        elementCount: Int
    ): WallBbsPositions {
        val family = design.family
        val hcr = design.envelope.criticalHeight.retainedMeters
        val left = design.boundaryLeft
        val right = design.boundaryRight

        fun addBoundaryVertical(side: String, boundary: WallBoundaryDesign): Int? {
            val lapLength = 50.0 * boundary.bars.diameterMm / 1000.0
            return collector.add(
                elementType = "Voile",
                elementName = family.pierName,
                shape = BarShapeType.HOOKED,
                diameterMm = boundary.bars.diameterMm,
                countPerElement = boundary.bars.count,
                elementCount = elementCount,
                developedLengthMeters = family.totalHeightHwMeters + lapLength,
                note = "rive $side ${boundary.barLabel}, recouvrement ${fmt(lapLength)} m"
            )?.position
        }

        fun addBoundaryStirrups(side: String, boundary: WallBoundaryDesign): Int? {
            val spacing = boundary.stirrupSpacingCm.coerceAtLeast(1.0) / 100.0
            val count = ceil(family.totalHeightHwMeters / spacing).toInt().coerceAtLeast(1)
            val (width, height) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
                boundary.lengthLcCm / 100.0,
                family.thicknessBwMeters,
                0.025,
                boundary.stirrupDiameterMm
            )
            return collector.add(
                elementType = "Voile",
                elementName = family.pierName,
                shape = BarShapeType.STIRRUP,
                diameterMm = boundary.stirrupDiameterMm,
                countPerElement = count,
                elementCount = elementCount,
                developedLengthMeters = BarDevelopedLengthCalculator.closedStirrupLengthMeters(
                    width, height, boundary.stirrupDiameterMm
                ),
                spacingCm = boundary.stirrupSpacingCm,
                note = "cadres de confinement rive $side, zone critique hcr=${fmt(hcr)} m"
            )?.position
        }

        val leftVertical = addBoundaryVertical("gauche", left)
        addBoundaryVertical("droite", right)
        val leftStirrup = addBoundaryStirrups("gauche", left)
        addBoundaryStirrups("droite", right)

        val webLap = 50.0 * design.web.barDiameterMm / 1000.0
        val verticalSpacing = design.web.verticalSpacingCm / 100.0
        val verticalCount = (2.0 * ceil(family.lengthLwMeters / verticalSpacing.coerceAtLeast(0.01))).toInt()
            .coerceAtLeast(2)
        val verticalItem = collector.add(
            elementType = "Voile",
            elementName = family.pierName,
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.web.barDiameterMm,
            countPerElement = verticalCount,
            elementCount = elementCount,
            developedLengthMeters = family.totalHeightHwMeters + webLap,
            spacingCm = design.web.verticalSpacingCm,
            note = "double nappe verticale Sv=${fmt(design.web.verticalSpacingCm)} cm"
        )
        val horizontalSpacing = design.web.horizontalSpacingCm / 100.0
        val horizontalCount = (2.0 * ceil(family.totalHeightHwMeters / horizontalSpacing.coerceAtLeast(0.01))).toInt()
            .coerceAtLeast(2)
        val horizontalItem = collector.add(
            elementType = "Voile",
            elementName = family.pierName,
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.web.barDiameterMm,
            countPerElement = horizontalCount,
            elementCount = elementCount,
            developedLengthMeters = family.lengthLwMeters,
            spacingCm = design.web.horizontalSpacingCm,
            note = "double nappe horizontale Sh=${fmt(design.web.horizontalSpacingCm)} cm"
        )
        return WallBbsPositions(
            boundary = leftVertical ?: 0,
            stirrup = leftStirrup ?: 0,
            verticalMesh = verticalItem?.position ?: 0,
            horizontalMesh = horizontalItem?.position ?: 0
        )
    }

    private fun drawWallSection(
        document: DxfCoreEngine,
        design: WallDesignResult,
        center: DxfPoint,
        scale: Int,
        positions: WallBbsPositions
    ) {
        val family = design.family
        val length = family.lengthLwMeters * 1000.0 / scale
        val thickness = family.thicknessBwMeters * 1000.0 / scale
        val outline = CadGeometry.rectangle(center.x, center.y, length, thickness)
        document.addLwPolyline(CadLayerStandard.WALL_FORMWORK, outline, closed = true)
        document.addHatch(CadLayerStandard.WALL_FORMWORK, outline, pattern = "ANSI31", patternScale = 1.0)
        val leftLc = design.boundaryLeft.lengthLcCm / 100.0 * 1000.0 / scale
        val rightLc = design.boundaryRight.lengthLcCm / 100.0 * 1000.0 / scale
        val leftBoundary = CadGeometry.rectangle(
            center.x - length / 2.0 + leftLc / 2.0,
            center.y,
            leftLc,
            thickness
        )
        val rightBoundary = CadGeometry.rectangle(
            center.x + length / 2.0 - rightLc / 2.0,
            center.y,
            rightLc,
            thickness
        )
        document.addLwPolyline(CadLayerStandard.COLUMN_FORMWORK, leftBoundary, closed = true)
        document.addLwPolyline(CadLayerStandard.COLUMN_FORMWORK, rightBoundary, closed = true)
        drawBoundaryBars(document, leftBoundary, design.boundaryLeft)
        drawBoundaryBars(document, rightBoundary, design.boundaryRight)
        // Nappes verticales de l'ame : deux lignes de points.
        val webSpacing = design.web.verticalSpacingCm / 100.0 * 1000.0 / scale
        val webStart = center.x - length / 2.0 + leftLc
        val webEnd = center.x + length / 2.0 - rightLc
        var x = webStart + webSpacing
        while (x < webEnd) {
            document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, x, center.y + thickness * 0.30, 0.85)
            document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, x, center.y - thickness * 0.30, 0.85)
            x += webSpacing
        }
        // Nappes horizontales : lignes dans l'epaisseur.
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            webStart,
            center.y + thickness * 0.30,
            webEnd,
            center.y + thickness * 0.30
        )
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            webStart,
            center.y - thickness * 0.30,
            webEnd,
            center.y - thickness * 0.30
        )
        horizontalDimension(
            document,
            center.x - length / 2.0,
            center.x + length / 2.0,
            center.y + thickness / 2.0 + 8.0,
            center.y + thickness / 2.0 + 15.0,
            "lw = ${fmt(family.lengthLwMeters)} m"
        )
        horizontalDimension(
            document,
            center.x - length / 2.0,
            center.x - length / 2.0 + leftLc,
            center.y - thickness / 2.0 - 6.0,
            center.y - thickness / 2.0 - 12.0,
            "lc = ${fmt(design.boundaryLeft.lengthLcCm)} cm"
        )
        verticalDimension(
            document,
            center.y - thickness / 2.0,
            center.y + thickness / 2.0,
            center.x + length / 2.0 + 12.0,
            "bw = ${fmt(family.thicknessBwMeters)} m"
        )
        val labels = listOf(
            "V1 : ${design.web.layers} nappes ${design.web.barLabel} " +
                "Sv=${fmt(design.web.verticalSpacingCm)} cm Sh=${fmt(design.web.horizontalSpacingCm)} cm",
            "Rive G : ${design.boundaryLeft.barLabel} - cadres ${design.boundaryLeft.stirrupLabel}",
            "Rive D : ${design.boundaryRight.barLabel} - cadres ${design.boundaryRight.stirrupLabel}"
        )
        document.addMText(
            CadLayerStandard.TEXT,
            center.x - length / 2.0,
            center.y - thickness / 2.0 - 24.0,
            labels.joinToString("\n"),
            heightMm = 1.7,
            widthMm = max(length, 100.0)
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x - length / 2.0 + leftLc / 2.0, center.y + thickness / 4.0),
            DxfPoint(center.x - length / 2.0 - 24.0, center.y + 22.0),
            positions.boundary,
            "Armatures de rive\nG ${design.boundaryLeft.barLabel}\nD ${design.boundaryRight.barLabel}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x, center.y + thickness * 0.30),
            DxfPoint(center.x + 22.0, center.y + thickness / 2.0 + 25.0),
            positions.verticalMesh,
            "Nappes verticales\n${design.web.barLabel} e=${fmt(design.web.verticalSpacingCm)} cm",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
    }

    private fun drawBoundaryBars(
        document: DxfCoreEngine,
        rectangle: List<DxfPoint>,
        boundary: WallBoundaryDesign
    ) {
        val left = rectangle.minOf { it.x }
        val right = rectangle.maxOf { it.x }
        val bottom = rectangle.minOf { it.y }
        val top = rectangle.maxOf { it.y }
        val count = boundary.bars.count.coerceAtLeast(2)
        for (index in 0 until count) {
            val fraction = index.toDouble() / (count - 1)
            val x = left + (right - left) * fraction
            document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, x, bottom + 2.0, 1.05)
            document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, x, top - 2.0, 1.05)
        }
    }

    private fun drawWallElevation(
        document: DxfCoreEngine,
        design: WallDesignResult,
        origin: DxfPoint,
        width: Double,
        height: Double,
        scale: Int,
        hcr: Double,
        positions: WallBbsPositions
    ) {
        val family = design.family
        val outline = listOf(
            origin,
            DxfPoint(origin.x + width, origin.y),
            DxfPoint(origin.x + width, origin.y + height),
            DxfPoint(origin.x, origin.y + height)
        )
        document.addLwPolyline(CadLayerStandard.WALL_FORMWORK, outline, closed = true)
        val criticalHeight = hcr * 1000.0 / scale
        val critical = listOf(
            origin,
            DxfPoint(origin.x + width, origin.y),
            DxfPoint(origin.x + width, origin.y + criticalHeight),
            DxfPoint(origin.x, origin.y + criticalHeight)
        )
        document.addHatch(CadLayerStandard.WALL_FORMWORK, critical, pattern = "ANSI31", patternScale = 2.0)
        val verticalSpacing = design.web.verticalSpacingCm / 100.0 * 1000.0 / scale
        var x = origin.x + verticalSpacing
        while (x < origin.x + width) {
            document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, x, origin.y, x, origin.y + height)
            x += verticalSpacing
        }
        val horizontalSpacing = design.web.horizontalSpacingCm / 100.0 * 1000.0 / scale
        var y = origin.y + horizontalSpacing
        while (y < origin.y + height) {
            document.addLine(CadLayerStandard.TRANSVERSE_STEEL, origin.x, y, origin.x + width, y)
            y += horizontalSpacing
        }
        CadGeometry.dashedLine(
            document,
            CadLayerStandard.DIMENSIONS,
            DxfPoint(origin.x, origin.y + criticalHeight),
            DxfPoint(origin.x + width, origin.y + criticalHeight),
            dashMm = 3.0,
            gapMm = 1.8
        )
        document.addText(
            CadLayerStandard.TEXT,
            origin.x + width + 3.0,
            origin.y + criticalHeight + 1.6,
            "hcr = ${fmt(hcr)} m",
            1.7
        )
        document.addText(
            CadLayerStandard.TEXT,
            origin.x + 2.0,
            origin.y + criticalHeight + 4.0,
            "ZONE CRITIQUE CONFINEE",
            1.8
        )
        verticalDimension(
            document,
            origin.y,
            origin.y + height,
            origin.x - 12.0,
            "Hw = ${fmt(family.totalHeightHwMeters)} m"
        )
        horizontalDimension(
            document,
            origin.x,
            origin.x + width,
            origin.y - 8.0,
            origin.y - 15.0,
            "lw = ${fmt(family.lengthLwMeters)} m"
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(origin.x + width * 0.5, origin.y + height * 0.55),
            DxfPoint(origin.x + width + 28.0, origin.y + height * 0.55),
            positions.horizontalMesh,
            "Nappe horizontale\n${design.web.barLabel} e=${fmt(design.web.horizontalSpacingCm)} cm",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
    }

    private fun drawWallNotes(document: DxfCoreEngine, layout: CadSheetLayout, design: WallDesignResult, hcr: Double) {
        val lines = listOf(
            "Voile ${design.family.pierName} : lw = ${fmt(design.family.lengthLwMeters)} m ; " +
                "bw = ${fmt(design.family.thicknessBwMeters)} m ; Hw = ${fmt(design.family.totalHeightHwMeters)} m.",
            "Zone critique hcr = ${fmt(hcr)} m ; confinement mecanique = " +
                "${fmt(design.ductility.confinementMechanicalRatio, 3)}.",
            "Rives : ${design.boundaryLeft.barLabel} + ${design.boundaryRight.barLabel} ; " +
                "cadres ${design.boundaryLeft.stirrupLabel} e=${fmt(design.boundaryLeft.stirrupSpacingCm)} cm.",
            "Ame : double nappe ${design.web.barLabel} - Sv=${fmt(design.web.verticalSpacingCm)} cm, " +
                "Sh=${fmt(design.web.horizontalSpacingCm)} cm ; épingles de maintien.",
            "Efforts : Nd = ${fmt(design.designAxialKN)} kN, Vu = ${fmt(design.forces.shearKN)} kN, " +
                "Mu = ${fmt(design.designMomentKNm)} kN.m (${design.designCombo})."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + 2.0,
            layout.drawingBottom + 3.0,
            lines.joinToString("\n"),
            heightMm = 1.68,
            widthMm = layout.drawingWidth - 4.0
        )
    }

    // ------------------------------------------------------------------ poutrelles

    private fun solveSlabs(request: DetailingPlanRequest, warnings: MutableList<String>): List<ContinuousSlabResult> {
        val materials = request.model.joint.materials
        return request.model.joint.sectionCatalog.slabs
            .filter { it.continuousSpansMeters.isNotEmpty() }
            .mapNotNull { topology ->
                val selfWeight = topology.htMeters * materials.gammaConcreteKNm3
                runCatching {
                    slabSolver.solve(
                        ContinuousSlabInput(
                            topology = topology,
                            permanentLoadKNPerM2 = selfWeight + request.slabFinishingLoadKNM2 +
                                request.slabPermanentSuperLoadKNM2,
                            variableLoadKNPerM2 = request.slabLiveLoadKNM2,
                            materials = materials,
                            permanentBeforeFinishesKNPerM2 = selfWeight,
                            leftCantileverMeters = topology.leftCantileverMeters,
                            rightCantileverMeters = topology.rightCantileverMeters
                        )
                    )
                }.onFailure {
                    warnings += "Plancher '${topology.name}' non ferraille : ${it.message ?: "erreur de calcul"}."
                }.getOrNull()
            }
    }

    private fun drawRibSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        slab: ContinuousSlabResult,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val topology = slab.topology
        val ribCount = ribCount(request, topology)
        val positions = addRibBbsItems(slab, collector, ribCount)
        val planArea = slabPlanArea(request, topology)
        val equivalentThickness = if (topology.isSolidSlab) {
            topology.htMeters
        } else {
            val b = topology.flangeWidthBCm / 100.0
            val b0 = topology.ribWidthB0Cm / 100.0
            val h0 = topology.compressionSlabH0Cm / 100.0
            val ht = topology.htMeters
            if (b > 0.0) (b * h0 + b0 * (ht - h0)) / b else ht
        }
        collector.addConcrete("Poutrelles", topology.name, planArea * equivalentThickness)
        val layout = addSheet(document, request, originX, originY, sheetNumber, title, "1/10 - COUPE")
        val scale = chooseLinearScale(
            topology.flangeWidthBCm / 100.0,
            90.0,
            maxDenominator = 10
        )
        val center = DxfPoint(layout.drawingLeft + 120.0, layout.drawingBottom + 105.0)
        addViewTitle(
            document,
            DxfPoint(center.x, center.y + topology.htMeters * 1000.0 / scale / 2.0 + 18.0),
            "COUPE TRANSVERSALE DE POUTRELLE (1/$scale)"
        )
        drawRibSection(document, slab, center, scale, positions)
        val rows = listOf(
            "Plancher ${topology.name} : ${topology.geometryLabel}.",
            "Portées lues dans ETABS : ${topology.continuousSpansMeters.joinToString(" + ") { fmt(it) }} m ; " +
                "poutrelles estimées : $ribCount.",
            "Travée dimensionnante : ${slab.spans.maxByOrNull { it.lengthMeters }?.reinforcement?.bars?.label ?: "-"} ; " +
                "appui : ${slab.supports.maxByOrNull { abs(it.momentUltimateKNm) }?.reinforcement?.bars?.label ?: "-"}.",
            "Flèche nuisible ${fmt(slab.deflection.deltaFtMeters * 1000.0)} mm <= " +
                "${fmt(slab.deflection.allowableMeters * 1000.0)} mm.",
            "Treillis soude de table : TS 150x150 - fil 5 mm, pose sur le hourdis."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + layout.drawingWidth * 0.52,
            layout.drawingBottom + 120.0,
            rows.joinToString("\n"),
            heightMm = 1.85,
            widthMm = layout.drawingWidth * 0.44
        )
    }

    private data class RibBbsPositions(
        val span: Int,
        val support: Int,
        val stirrup: Int
    )

    private fun addRibBbsItems(
        slab: ContinuousSlabResult,
        collector: RebarScheduleCollector,
        ribCount: Int
    ): RibBbsPositions {
        val topology = slab.topology
        val cover = 0.02
        val governingSpan = slab.spans.maxByOrNull { it.lengthMeters }
            ?: return RibBbsPositions(0, 0, 0)
        val governingSupport = slab.supports.maxByOrNull { abs(it.momentUltimateKNm) }
        val governingShear = slab.shearDesigns.maxByOrNull { it.shearUltimateKN }
        var spanPosition = 0
        var supportPosition = 0
        var stirrupPosition = 0

        slab.spans.forEach { span ->
            val item = collector.add(
                elementType = "Poutrelle",
                elementName = topology.name,
                shape = BarShapeType.HOOKED,
                diameterMm = span.reinforcement.bars.diameterMm,
                countPerElement = span.reinforcement.bars.count,
                elementCount = ribCount,
                developedLengthMeters = span.lengthMeters +
                    2.0 * span.reinforcement.anchorageLengthMeters,
                note = "travée ${span.index + 1} de ${fmt(span.lengthMeters)} m, " +
                    span.reinforcement.bars.label
            )
            if (span === governingSpan) spanPosition = item?.position ?: 0
        }
        slab.supports.forEach { support ->
            val item = collector.add(
                elementType = "Poutrelle",
                elementName = topology.name,
                shape = BarShapeType.HAT,
                diameterMm = support.reinforcement.bars.diameterMm,
                countPerElement = support.reinforcement.bars.count,
                elementCount = ribCount,
                developedLengthMeters = support.reinforcement.anchorageLengthMeters * 2.0 +
                    min(2.0 * topology.htMeters, support.reinforcement.anchorageLengthMeters * 4.0),
                note = "appui ${support.index}, ${support.reinforcement.bars.label}"
            )
            if (support === governingSupport) supportPosition = item?.position ?: 0
        }
        slab.shearDesigns.forEach { shearDesign ->
            val relatedSpan = slab.spans.firstOrNull { it.index == shearDesign.spanIndex }
            val spanLength = relatedSpan?.lengthMeters ?: governingSpan.lengthMeters
            val diameter = shearDesign.stirrupDiameterMm
            val width = topology.ribWidthB0Cm / 100.0
            val height = topology.htMeters - topology.compressionSlabH0Cm / 100.0
            val (centerWidth, centerHeight) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
                width, height.coerceAtLeast(0.05), cover, diameter
            )
            val count = ceil(spanLength / shearDesign.spacingMeters.coerceAtLeast(0.01))
                .toInt().coerceAtLeast(1)
            val item = collector.add(
                elementType = "Poutrelle",
                elementName = topology.name,
                shape = BarShapeType.STIRRUP,
                diameterMm = diameter,
                countPerElement = count,
                elementCount = ribCount,
                developedLengthMeters = BarDevelopedLengthCalculator.closedStirrupLengthMeters(
                    centerWidth, centerHeight, diameter
                ),
                spacingCm = shearDesign.spacingMeters * 100.0,
                note = "etriers de nervure, travée ${shearDesign.spanIndex + 1}, " +
                    shearDesign.status.label
            )
            if (shearDesign === governingShear) stirrupPosition = item?.position ?: 0
        }
        return RibBbsPositions(
            span = spanPosition,
            support = supportPosition,
            stirrup = stirrupPosition
        )
    }

    private fun drawRibSection(
        document: DxfCoreEngine,
        slab: ContinuousSlabResult,
        center: DxfPoint,
        scale: Int,
        positions: RibBbsPositions
    ) {
        val topology = slab.topology
        val b = topology.flangeWidthBCm / 100.0 * 1000.0 / scale
        val b0 = topology.ribWidthB0Cm / 100.0 * 1000.0 / scale
        val ht = topology.htMeters * 1000.0 / scale
        val h0 = topology.compressionSlabH0Cm / 100.0 * 1000.0 / scale
        val baseY = center.y - ht / 2.0
        val topY = baseY + ht
        val flangeBottom = topY - h0
        val outline = listOf(
            DxfPoint(center.x - b / 2.0, flangeBottom),
            DxfPoint(center.x + b / 2.0, flangeBottom),
            DxfPoint(center.x + b / 2.0, topY),
            DxfPoint(center.x - b / 2.0, topY)
        )
        document.addLwPolyline(CadLayerStandard.WALL_FORMWORK, outline, closed = true)
        val web = listOf(
            DxfPoint(center.x - b0 / 2.0, baseY),
            DxfPoint(center.x + b0 / 2.0, baseY),
            DxfPoint(center.x + b0 / 2.0, flangeBottom),
            DxfPoint(center.x - b0 / 2.0, flangeBottom)
        )
        document.addLwPolyline(CadLayerStandard.BEAM_FORMWORK, web, closed = true)
        document.addHatch(CadLayerStandard.BEAM_FORMWORK, web, pattern = "ANSI31", patternScale = 0.35)
        val span = slab.spans.maxByOrNull { it.lengthMeters }
        if (span != null) {
            val count = span.reinforcement.bars.count.coerceAtLeast(2)
            val usable = b0 - 8.0
            for (index in 0 until count) {
                val x = center.x - usable / 2.0 + if (count > 1) usable * index / (count - 1) else usable / 2.0
                document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, x, baseY + 5.0, 1.1)
            }
            document.addText(
                CadLayerStandard.TEXT,
                center.x,
                baseY - 5.0,
                "Inf. ${span.reinforcement.bars.label}",
                1.8,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
        }
        val support = slab.supports.maxByOrNull { abs(it.momentUltimateKNm) }
        if (support != null) {
            document.addLine(
                CadLayerStandard.LONGITUDINAL_STEEL,
                center.x - b * 0.32,
                topY - 3.0,
                center.x + b * 0.32,
                topY - 3.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                center.x,
                topY + 3.0,
                "Sup. ${support.reinforcement.bars.label}",
                1.8,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
        }
        // Treillis de table conventionnel.
        document.addLine(
            CadLayerStandard.TRANSVERSE_STEEL,
            center.x - b / 2.0 + 2.0,
            topY - 2.0,
            center.x + b / 2.0 - 2.0,
            topY - 2.0
        )
        document.addText(
            CadLayerStandard.TEXT,
            center.x,
            flangeBottom - 3.0,
            "Table ${fmt(topology.flangeWidthBCm, 0)} cm - hourdis ${fmt(topology.compressionSlabH0Cm, 0)} cm",
            1.7,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        horizontalDimension(
            document,
            center.x - b / 2.0,
            center.x + b / 2.0,
            topY + 9.0,
            topY + 17.0,
            "b = ${fmt(topology.flangeWidthBCm, 0)} cm"
        )
        horizontalDimension(
            document,
            center.x - b0 / 2.0,
            center.x + b0 / 2.0,
            baseY - 12.0,
            baseY - 20.0,
            "b0 = ${fmt(topology.ribWidthB0Cm, 0)} cm"
        )
        verticalDimension(
            document,
            baseY,
            topY,
            center.x + b / 2.0 + 11.0,
            "ht = ${fmt(topology.totalThicknessHtCm, 0)} cm"
        )
        verticalDimension(
            document,
            flangeBottom,
            topY,
            center.x - b / 2.0 - 11.0,
            "h0 = ${fmt(topology.compressionSlabH0Cm, 0)} cm"
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x, baseY + 5.0),
            DxfPoint(center.x + b / 2.0 + 34.0, baseY + 18.0),
            positions.span,
            "Armatures inferieures\n${span?.reinforcement?.bars?.label ?: "-"}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(center.x, topY - 3.0),
            DxfPoint(center.x + b / 2.0 + 34.0, topY + 24.0),
            positions.support,
            "Chapeaux d'appui\n${support?.reinforcement?.bars?.label ?: "-"}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
    }

    // ------------------------------------------------------------------ acrotere

    private fun drawParapetSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        joint: StructuralJoint,
        design: ParapetDesignResult,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val geometry = design.geometry
        val length = request.parapetLengthMeters ?: roofPerimeterMeters(request)
        val mainSpacing = design.mainSteel.spacingMeters
        val mainCount = ceil(length / mainSpacing.coerceAtLeast(0.01)).toInt().coerceAtLeast(1)
        val mainHook = BarDevelopedLengthCalculator.hookLengthMeters(design.mainSteel.barDiameterMm, 90.0)
        val mainItem = collector.add(
            elementType = "Acrotère",
            elementName = "Acrotère ${request.project.jointName.ifBlank { joint.jointName }}",
            shape = BarShapeType.HOOKED,
            diameterMm = design.mainSteel.barDiameterMm,
            countPerElement = mainCount,
            elementCount = 1,
            developedLengthMeters = geometry.heightMeters + 2.0 * mainHook,
            spacingCm = mainSpacing * 100.0,
            note = "nappe principale, retour dans le plancher"
        )
        val distributionSpacing = design.distributionSteel.spacingMeters
        val distributionCount = ceil(geometry.heightMeters / distributionSpacing.coerceAtLeast(0.01)).toInt()
            .coerceAtLeast(1)
        val distributionItem = collector.add(
            elementType = "Acrotère",
            elementName = "Acrotère ${request.project.jointName.ifBlank { joint.jointName }}",
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.distributionSteel.barDiameterMm,
            countPerElement = distributionCount,
            elementCount = 1,
            developedLengthMeters = length,
            spacingCm = distributionSpacing * 100.0,
            note = "nappe de repartition sur la longueur de la terrasse"
        )
        collector.addConcrete("Acrotère", "Acrotère", geometry.sectionAreaM2 * length)
        val layout = addSheet(document, request, originX, originY, sheetNumber, title, "1/10 - COUPE")
        val scale = chooseLinearScale(geometry.heightMeters, 90.0, maxDenominator = 10)
        val center = DxfPoint(layout.drawingLeft + 95.0, layout.drawingBottom + 85.0)
        addViewTitle(
            document,
            DxfPoint(center.x, center.y + geometry.heightMeters * 1000.0 / scale / 2.0 + 18.0),
            "COUPE VERTICALE DE L'ACROTERE (1/$scale)"
        )
        drawParapetSection(document, design, center, scale, mainItem?.position ?: 0, distributionItem?.position ?: 0)
        val notes = listOf(
            "Acrotère ${geometry.geometryLabel}.",
            "Longueur de terrasse retenue : ${fmt(length)} m.",
            "Armature principale ${design.mainSteel.label} = ${fmt(design.mainSteel.providedAsCm2PerM)} cm2/ml ; " +
                "répartition ${design.distributionSteel.label} = ${fmt(design.distributionSteel.providedAsCm2PerM)} cm2/ml.",
            "Force sismique Fp = ${fmt(design.loads.seismicForceFpKNPerM)} kN/ml ; " +
                "charge de garde-corps ${fmt(design.loads.handrailLoadKNPerM)} kN/ml.",
            "Moment d'encastrement Mu = ${fmt(design.durableResult.momentKNm)} kN.m ; " +
                "situation ${design.governingSituation.label}."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + layout.drawingWidth * 0.50,
            layout.drawingBottom + 85.0,
            notes.joinToString("\n"),
            heightMm = 1.85,
            widthMm = layout.drawingWidth * 0.46
        )
    }

    private fun drawParapetSection(
        document: DxfCoreEngine,
        design: ParapetDesignResult,
        base: DxfPoint,
        scale: Int,
        mainPosition: Int,
        distributionPosition: Int
    ) {
        val geometry = design.geometry
        val height = geometry.heightMeters * 1000.0 / scale
        val thickness = geometry.stemThicknessMeters * 1000.0 / scale
        val projection = geometry.corniceProjectionMeters * 1000.0 / scale
        val straight = geometry.corniceStraightHeightMeters * 1000.0 / scale
        val sloped = geometry.corniceSlopedHeightMeters * 1000.0 / scale
        val stemTop = base.y + height - straight - sloped
        val outline = listOf(
            DxfPoint(base.x, base.y),
            DxfPoint(base.x + thickness, base.y),
            DxfPoint(base.x + thickness, stemTop),
            DxfPoint(base.x + thickness + projection, stemTop),
            DxfPoint(base.x + thickness + projection, stemTop + straight),
            DxfPoint(base.x + thickness, base.y + height),
            DxfPoint(base.x, base.y + height)
        )
        document.addLwPolyline(CadLayerStandard.WALL_FORMWORK, outline, closed = true)
        document.addHatch(CadLayerStandard.WALL_FORMWORK, outline, pattern = "ANSI31", patternScale = 0.45)
        // Nappe principale en epingle continue.
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            base.x + thickness * 0.45,
            base.y + 3.0,
            base.x + thickness * 0.45,
            base.y + height - 4.0
        )
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            base.x + thickness * 0.45,
            base.y + height - 4.0,
            base.x + thickness + projection - 2.0,
            base.y + height - 4.0
        )
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            base.x + thickness * 0.45,
            base.y + 3.0,
            base.x - 5.0,
            base.y + 3.0
        )
        // Aciers de repartition.
        val distributionStep = geometry.heightMeters / 4.0 * 1000.0 / scale
        var y = base.y + distributionStep
        while (y < base.y + height) {
            document.addCircle(CadLayerStandard.TRANSVERSE_STEEL, base.x + thickness / 2.0, y, 0.85)
            y += distributionStep
        }
        horizontalDimension(
            document,
            base.x,
            base.x + thickness + projection,
            base.y + height + 7.0,
            base.y + height + 14.0,
            "e + becquet = ${fmt(geometry.topWidthMeters)} m"
        )
        verticalDimension(
            document,
            base.y,
            base.y + height,
            base.x + thickness + projection + 10.0,
            "H = ${fmt(geometry.heightMeters)} m"
        )
        verticalDimension(
            document,
            base.y,
            base.y + height - straight - sloped,
            base.x - 9.0,
            "fût ${fmt(geometry.stemInnerHeightMeters)} m"
        )
        document.addText(
            CadLayerStandard.TEXT,
            base.x - 6.0,
            base.y + height / 2.0,
            "e=${fmt(geometry.stemThicknessMeters)} m",
            1.75,
            rotationDegrees = 90.0,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(base.x + thickness * 0.45, base.y + height * 0.55),
            DxfPoint(base.x + thickness + projection + 24.0, base.y + height * 0.55),
            mainPosition,
            "Nappe principale\n${design.mainSteel.label}\n${fmt(design.mainSteel.providedAsCm2PerM)} cm2/ml",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(base.x + thickness / 2.0, base.y + height * 0.30),
            DxfPoint(base.x - 28.0, base.y + height * 0.30),
            distributionPosition,
            "Repartition\n${design.distributionSteel.label}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
    }

    // ------------------------------------------------------------------ escaliers

    private fun drawStaircaseSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        design: StaircaseDesignResult,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val geometry = design.geometry
        val positions = addStaircaseBbsItems(design, collector)
        val flightCount = geometry.numberOfFlights.coerceAtLeast(1)
        collector.addConcrete(
            "Escalier",
            geometry.name,
            geometry.waistThicknessMeters * geometry.developedFlightLengthMeters * geometry.flightWidthMeters * flightCount +
                geometry.landingThicknessMeters * geometry.landingLengthMeters * geometry.flightWidthMeters * flightCount
        )
        val layout = addSheet(document, request, originX, originY, sheetNumber, title, "1/25")
        val totalRun = geometry.flightRunMeters + geometry.landingLengthMeters
        val scale = chooseLinearScale(totalRun, layout.drawingWidth * 0.58)
        val start = DxfPoint(layout.drawingLeft + 40.0, layout.drawingBottom + 65.0)
        addViewTitle(
            document,
            DxfPoint(start.x, start.y + geometry.flightRiseMeters * 1000.0 / scale + 24.0),
            "COUPE LONGITUDINALE DE LA VOLÉE (1/$scale)"
        )
        drawStaircaseSection(document, design, start, scale, positions)
        val notes = listOf(
            "${geometry.name} : ${geometry.geometryLabel}.",
            "Blondel 2h+g = ${fmt(geometry.blondelValueMeters * 100.0)} cm ; " +
                "inclinaison ${fmt(geometry.slopeAngleDegrees)} deg.",
            "Portée ${fmt(geometry.designSpanMeters)} m ; paillasse ${fmt(geometry.waistThicknessMeters * 100.0)} cm.",
            "Travée ${design.spanReinforcement.label} ; appui ${design.supportReinforcement.label} ; " +
                "répartition ${design.distributionReinforcement.label}.",
            "Flèche ${fmt(design.deflection.deltaFtMeters * 1000.0)} mm <= " +
                "${fmt(design.deflection.allowableMeters * 1000.0)} mm."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + layout.drawingWidth * 0.55,
            layout.drawingBottom + 70.0,
            notes.joinToString("\n"),
            heightMm = 1.85,
            widthMm = layout.drawingWidth * 0.42
        )
    }

    private data class StaircaseBbsPositions(val span: Int, val support: Int, val distribution: Int)

    private fun addStaircaseBbsItems(
        design: StaircaseDesignResult,
        collector: RebarScheduleCollector
    ): StaircaseBbsPositions {
        val geometry = design.geometry
        val flights = geometry.numberOfFlights.coerceAtLeast(1)
        val spanAnchorage = 50.0 * design.spanReinforcement.barDiameterMm / 1000.0
        val supportAnchorage = 50.0 * design.supportReinforcement.barDiameterMm / 1000.0
        val spanCount = ceil(geometry.flightWidthMeters / design.spanReinforcement.spacingMeters.coerceAtLeast(0.01))
            .toInt().coerceAtLeast(1)
        val spanItem = collector.add(
            elementType = "Escalier",
            elementName = geometry.name,
            shape = BarShapeType.HOOKED,
            diameterMm = design.spanReinforcement.barDiameterMm,
            countPerElement = spanCount,
            elementCount = flights,
            developedLengthMeters = geometry.developedFlightLengthMeters + geometry.landingLengthMeters +
                2.0 * spanAnchorage,
            spacingCm = design.spanReinforcement.spacingMeters * 100.0,
            note = "nappe inferieure de volée, ${design.spanReinforcement.label}"
        )
        val supportCount = ceil(geometry.flightWidthMeters / design.supportReinforcement.spacingMeters.coerceAtLeast(0.01))
            .toInt().coerceAtLeast(1)
        val supportItem = collector.add(
            elementType = "Escalier",
            elementName = geometry.name,
            shape = BarShapeType.HAT,
            diameterMm = design.supportReinforcement.barDiameterMm,
            countPerElement = supportCount,
            elementCount = flights,
            developedLengthMeters = geometry.landingLengthMeters + 2.0 * supportAnchorage,
            spacingCm = design.supportReinforcement.spacingMeters * 100.0,
            note = "nappe d'appui au palier, ${design.supportReinforcement.label}"
        )
        val distributionCount = ceil(
            geometry.developedFlightLengthMeters / design.distributionReinforcement.spacingMeters.coerceAtLeast(0.01)
        ).toInt().coerceAtLeast(1)
        val distributionItem = collector.add(
            elementType = "Escalier",
            elementName = geometry.name,
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.distributionReinforcement.barDiameterMm,
            countPerElement = distributionCount,
            elementCount = flights,
            developedLengthMeters = geometry.flightWidthMeters,
            spacingCm = design.distributionReinforcement.spacingMeters * 100.0,
            note = "armatures de repartition, ${design.distributionReinforcement.label}"
        )
        return StaircaseBbsPositions(
            span = spanItem?.position ?: 0,
            support = supportItem?.position ?: 0,
            distribution = distributionItem?.position ?: 0
        )
    }

    private fun drawStaircaseSection(
        document: DxfCoreEngine,
        design: StaircaseDesignResult,
        origin: DxfPoint,
        scale: Int,
        positions: StaircaseBbsPositions
    ) {
        val geometry = design.geometry
        val run = geometry.flightRunMeters * 1000.0 / scale
        val rise = geometry.flightRiseMeters * 1000.0 / scale
        val riser = geometry.riserHeightMeters * 1000.0 / scale
        val tread = geometry.treadDepthMeters * 1000.0 / scale
        val waist = geometry.waistThicknessMeters * 1000.0 / scale
        val steps = geometry.risersPerFlight.coerceAtLeast(2)
        for (index in 0 until steps) {
            val x = origin.x + index * tread
            val yBase = origin.y + index * riser
            document.addLine(CadLayerStandard.BEAM_FORMWORK, x, yBase, x + tread, yBase)
            document.addLine(CadLayerStandard.BEAM_FORMWORK, x + tread, yBase, x + tread, yBase + riser)
        }
        val slopeStart = DxfPoint(origin.x, origin.y + waist * 0.45)
        val slopeEnd = DxfPoint(origin.x + run, origin.y + rise + waist * 0.45)
        document.addLine(CadLayerStandard.WALL_FORMWORK, slopeStart.x, slopeStart.y, slopeEnd.x, slopeEnd.y)
        document.addLine(
            CadLayerStandard.WALL_FORMWORK,
            slopeStart.x,
            slopeStart.y - waist,
            slopeEnd.x,
            slopeEnd.y - waist
        )
        document.addLine(CadLayerStandard.WALL_FORMWORK, slopeStart.x, slopeStart.y, slopeStart.x, slopeStart.y - waist)
        document.addLine(CadLayerStandard.WALL_FORMWORK, slopeEnd.x, slopeEnd.y, slopeEnd.x, slopeEnd.y - waist)
        // Nappe inferieure.
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            slopeStart.x,
            slopeStart.y - waist + 2.5,
            slopeEnd.x,
            slopeEnd.y - waist + 2.5
        )
        // Nappe d'appui au palier.
        val landing = geometry.landingLengthMeters * 1000.0 / scale
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            slopeEnd.x,
            slopeEnd.y - 2.0,
            slopeEnd.x + landing,
            slopeEnd.y - 2.0
        )
        document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, slopeEnd.x + landing, slopeEnd.y - 2.0, slopeEnd.x + landing, slopeEnd.y - 6.0)
        horizontalDimension(
            document,
            origin.x,
            origin.x + run,
            origin.y - 2.0,
            origin.y - 10.0,
            "L volée = ${fmt(geometry.flightRunMeters)} m"
        )
        verticalDimension(
            document,
            origin.y,
            origin.y + rise,
            origin.x + run + landing + 9.0,
            "H volée = ${fmt(geometry.flightRiseMeters)} m"
        )
        document.addText(
            CadLayerStandard.TEXT,
            origin.x + run / 2.0,
            origin.y + rise / 2.0 + 5.0,
            "${geometry.risersPerFlight} marches x ${fmt(geometry.riserHeightMeters * 100.0)} cm",
            1.75,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        document.addText(
            CadLayerStandard.TEXT,
            origin.x + run / 2.0,
            origin.y + rise / 2.0 - waist / 2.0 - 4.0,
            "Paillasse ${fmt(geometry.waistThicknessMeters * 100.0)} cm",
            1.7,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(slopeStart.x + run * 0.5, slopeStart.y - waist / 2.0),
            DxfPoint(slopeStart.x + run * 0.5, slopeStart.y - waist - 20.0),
            positions.span,
            "Nappe inferieure\n${design.spanReinforcement.label}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(slopeEnd.x, slopeEnd.y - 2.0),
            DxfPoint(slopeEnd.x + 20.0, slopeEnd.y + 18.0),
            positions.support,
            "Nappe d'appui\n${design.supportReinforcement.label}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
    }

    // ------------------------------------------------------------------ semelles

    private fun drawFootingSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        joint: StructuralJoint,
        design: StripFootingDesign,
        collector: RebarScheduleCollector,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String
    ) {
        val family = design.family
        val length = footingLength(design)
        val elementCount = max(1, family.elementIds.size)
        val positions = addFootingBbsItems(design, collector, length, elementCount)
        collector.addConcrete(
            "Semelle",
            family.name,
            footingSectionArea(family) * length * elementCount
        )
        val layout = addSheet(document, request, originX, originY, sheetNumber, title, "1/20 - COUPE")
        val scale = 20
        val widthPage = family.flangeWidthMeters * 1000.0 / scale
        val base = DxfPoint(layout.drawingLeft + 20.0 + widthPage / 2.0, layout.drawingBottom + 58.0)
        addViewTitle(
            document,
            DxfPoint(base.x, base.y + family.totalHeightMeters * 1000.0 / scale + 30.0),
            "COUPE TRANSVERSALE ${if (family.isTee) "EN TE RENVERSE" else "RECTANGULAIRE"} (1/$scale)"
        )
        drawFootingSection(document, design, base, scale, positions)
        val notes = listOf(
            "${family.name} (${family.kind.label}) : ${family.sectionLabel}.",
            "Longueur ${fmt(length)} m ; ${elementCount} element(s) identique(s).",
            "Nappe transversale ${design.transverseSteel.label} ; appui ${design.supportSteel.label} ; " +
                "travée ${design.spanSteel.label}.",
            "Béton de propreté 10 cm sous semelle ; enrobage ${fmt(family.bottomCoverMeters * 100.0)} cm.",
            "Portance : ${design.soil?.let { "sigma = ${fmt(it.effectiveStressKNM2)} kN/m2" } ?: "non verifiee"}."
        )
        document.addMText(
            CadLayerStandard.TEXT,
            layout.drawingLeft + layout.drawingWidth * 0.54,
            layout.drawingBottom + 70.0,
            notes.joinToString("\n"),
            heightMm = 1.8,
            widthMm = layout.drawingWidth * 0.43
        )
    }

    private data class FootingBbsPositions(val transverse: Int, val support: Int, val span: Int)

    private fun addFootingBbsItems(
        design: StripFootingDesign,
        collector: RebarScheduleCollector,
        length: Double,
        elementCount: Int
    ): FootingBbsPositions {
        val family = design.family
        val cover = max(family.bottomCoverMeters, family.topCoverMeters)
        val transverseCount = ceil(length / design.transverseSteel.spacingMeters.coerceAtLeast(0.01))
            .toInt().coerceAtLeast(1)
        val transverseItem = collector.add(
            elementType = "Semelle",
            elementName = family.name,
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.transverseSteel.barDiameterMm,
            countPerElement = transverseCount,
            elementCount = elementCount,
            developedLengthMeters = max(family.flangeWidthMeters - 2.0 * cover, 0.1),
            spacingCm = design.transverseSteel.spacingMeters * 100.0,
            note = "nappe transversale ${design.transverseSteel.label}"
        )
        val supportItem = collector.add(
            elementType = "Semelle",
            elementName = family.name,
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.supportSteel.barDiameterMm,
            countPerElement = design.supportSteel.barCount,
            elementCount = elementCount,
            developedLengthMeters = max(length - 2.0 * cover, 0.1),
            spacingCm = design.supportSteel.spacingMeters * 100.0,
            note = "appui ${design.supportSteel.label}"
        )
        val spanItem = collector.add(
            elementType = "Semelle",
            elementName = family.name,
            shape = BarShapeType.STRAIGHT,
            diameterMm = design.spanSteel.barDiameterMm,
            countPerElement = design.spanSteel.barCount,
            elementCount = elementCount,
            developedLengthMeters = max(length - 2.0 * cover, 0.1),
            spacingCm = design.spanSteel.spacingMeters * 100.0,
            note = "travée ${design.spanSteel.label}"
        )
        return FootingBbsPositions(
            transverse = transverseItem?.position ?: 0,
            support = supportItem?.position ?: 0,
            span = spanItem?.position ?: 0
        )
    }

    private fun drawFootingSection(
        document: DxfCoreEngine,
        design: StripFootingDesign,
        base: DxfPoint,
        scale: Int,
        positions: FootingBbsPositions
    ) {
        val family = design.family
        val width = family.flangeWidthMeters * 1000.0 / scale
        val table = family.tableThicknessMeters * 1000.0 / scale
        val webWidth = family.webWidthMeters * 1000.0 / scale
        val totalHeight = family.totalHeightMeters * 1000.0 / scale
        val leanHeight = 10.0
        val isJoint = family.kind == com.archilab.rpa2024.engine.foundations.StripFootingKind.SEISMIC_JOINT
        val webOffset = if (isJoint) -width / 2.0 + webWidth / 2.0 else 0.0
        val lean = listOf(
            DxfPoint(base.x - width / 2.0 - 5.0, base.y - leanHeight),
            DxfPoint(base.x + width / 2.0 + 5.0, base.y - leanHeight),
            DxfPoint(base.x + width / 2.0 + 5.0, base.y),
            DxfPoint(base.x - width / 2.0 - 5.0, base.y)
        )
        document.addLwPolyline(CadLayerStandard.FOOTING_FORMWORK, lean, closed = true)
        document.addHatch(
            CadLayerStandard.FOOTING_FORMWORK,
            lean,
            pattern = "ANSI31",
            patternScale = 0.4
        )
        if (family.tableThicknessMeters > 1e-9) {
            val flange = listOf(
                DxfPoint(base.x - width / 2.0, base.y),
                DxfPoint(base.x + width / 2.0, base.y),
                DxfPoint(base.x + width / 2.0, base.y + table),
                DxfPoint(base.x - width / 2.0, base.y + table)
            )
            document.addLwPolyline(CadLayerStandard.FOOTING_FORMWORK, flange, closed = true)
            document.addHatch(CadLayerStandard.FOOTING_FORMWORK, flange, pattern = "ANSI31", patternScale = 0.5)
        }
        val web = listOf(
            DxfPoint(base.x + webOffset - webWidth / 2.0, base.y + table),
            DxfPoint(base.x + webOffset + webWidth / 2.0, base.y + table),
            DxfPoint(base.x + webOffset + webWidth / 2.0, base.y + totalHeight),
            DxfPoint(base.x + webOffset - webWidth / 2.0, base.y + totalHeight)
        )
        document.addLwPolyline(CadLayerStandard.FOOTING_FORMWORK, web, closed = true)
        document.addHatch(CadLayerStandard.FOOTING_FORMWORK, web, pattern = "ANSI31", patternScale = 0.5)
        // Nappe inferieure transversale.
        val bottomCover = family.bottomCoverMeters * 1000.0 / scale
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            base.x - width / 2.0 + 4.0,
            base.y + bottomCover,
            base.x + width / 2.0 - 4.0,
            base.y + bottomCover
        )
        document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, base.x - width / 4.0, base.y + bottomCover, 1.05)
        document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, base.x, base.y + bottomCover, 1.05)
        document.addCircle(CadLayerStandard.LONGITUDINAL_STEEL, base.x + width / 4.0, base.y + bottomCover, 1.05)
        // Armatures longitudinales d'ame.
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            base.x + webOffset - webWidth * 0.30,
            base.y + table + 3.0,
            base.x + webOffset - webWidth * 0.30,
            base.y + totalHeight - 3.0
        )
        document.addLine(
            CadLayerStandard.LONGITUDINAL_STEEL,
            base.x + webOffset + webWidth * 0.30,
            base.y + table + 3.0,
            base.x + webOffset + webWidth * 0.30,
            base.y + totalHeight - 3.0
        )
        // Cadres de nervure schematiques.
        var y = base.y + table + 8.0
        while (y < base.y + totalHeight - 3.0) {
            document.addLine(
                CadLayerStandard.TRANSVERSE_STEEL,
                base.x + webOffset - webWidth / 2.0 + 2.0,
                y,
                base.x + webOffset + webWidth / 2.0 - 2.0,
                y
            )
            y += 9.0
        }
        document.addText(
            CadLayerStandard.TEXT,
            base.x,
            base.y + totalHeight + 4.0,
            "Nervure ${fmt(family.webWidthMeters * 100.0)} x ${fmt(family.totalHeightMeters * 100.0)} cm",
            1.75,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        horizontalDimension(
            document,
            base.x - width / 2.0,
            base.x + width / 2.0,
            base.y - leanHeight - 9.0,
            base.y - leanHeight - 17.0,
            "B = ${fmt(family.flangeWidthMeters)} m"
        )
        verticalDimension(
            document,
            base.y,
            base.y + totalHeight,
            base.x + width / 2.0 + 12.0,
            "h = ${fmt(family.totalHeightMeters)} m"
        )
        horizontalDimension(
            document,
            base.x + webOffset - webWidth / 2.0,
            base.x + webOffset + webWidth / 2.0,
            base.y + totalHeight + 14.0,
            base.y + totalHeight + 21.0,
            "b0 = ${fmt(family.webWidthMeters)} m"
        )
        verticalDimension(
            document,
            base.y,
            base.y + table,
            base.x - width / 2.0 - 10.0,
            "h0 = ${fmt(family.tableThicknessMeters)} m"
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(base.x, base.y + bottomCover),
            DxfPoint(base.x + width / 2.0 + 30.0, base.y + 28.0),
            positions.transverse,
            "Nappe transversale\n${design.transverseSteel.label}\n" +
                "${fmt(design.transverseSteel.providedAreaCm2)} cm2/ml",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
        document.addLeaderWithBubble(
            CadLayerStandard.TEXT,
            DxfPoint(base.x + webOffset, base.y + table + 3.0),
            DxfPoint(base.x + webOffset + webWidth + 36.0, base.y + table + 25.0),
            positions.support,
            "Appui / travée\n${design.supportSteel.label} + ${design.spanSteel.label}",
            bubbleRadiusMm = 3.0,
            textHeightMm = 1.55
        )
    }

    // ------------------------------------------------------------------ utilitaires communs

    private fun addSheet(
        document: DxfCoreEngine,
        request: DetailingPlanRequest,
        originX: Double,
        originY: Double,
        sheetNumber: Int,
        title: String,
        scaleLabel: String
    ): CadSheetLayout = document.addSheet(
        originX,
        originY,
        request.sheetFormat,
        CadTitleBlock(
            project = request.project.copy(
                jointName = request.project.jointName.ifBlank { request.model.jointName }
            ),
            sheetTitle = title,
            scaleLabel = scaleLabel,
            sheetNumber = sheetNumber
        )
    )

    private fun addViewTitle(document: DxfCoreEngine, point: DxfPoint, title: String) {
        document.addText(
            CadLayerStandard.TEXT,
            point.x,
            point.y,
            title,
            heightMm = 2.5,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
    }

    private fun horizontalDimension(
        document: DxfCoreEngine,
        startX: Double,
        endX: Double,
        featureY: Double,
        dimensionY: Double,
        text: String
    ) {
        document.addLinearDimension(
            CadLayerStandard.DIMENSIONS,
            DxfPoint(startX, featureY),
            DxfPoint(endX, featureY),
            DxfPoint((startX + endX) / 2.0, dimensionY),
            text,
            textHeightMm = 1.8
        )
    }

    private fun verticalDimension(
        document: DxfCoreEngine,
        startY: Double,
        endY: Double,
        dimensionX: Double,
        text: String
    ) {
        document.addLinearDimension(
            CadLayerStandard.DIMENSIONS,
            DxfPoint(dimensionX, startY),
            DxfPoint(dimensionX, endY),
            DxfPoint(dimensionX, (startY + endY) / 2.0),
            text,
            textHeightMm = 1.8
        )
    }

    private fun preferredLinearScale(
        sizeMeters: Double,
        availableMm: Double,
        preferred: Int = 50,
        maxDenominator: Int = 200
    ): Int {
        if (sizeMeters <= 0.0 || availableMm <= 0.0) return preferred
        if (preferred <= maxDenominator && sizeMeters * 1000.0 / preferred <= availableMm) return preferred
        return chooseLinearScale(sizeMeters, availableMm, maxDenominator)
    }

    private fun chooseLinearScale(
        sizeMeters: Double,
        availableMm: Double,
        maxDenominator: Int = 200
    ): Int {
        if (sizeMeters <= 0.0 || availableMm <= 0.0) return 50
        val candidates = listOf(5, 10, 20, 25, 50, 75, 100, 125, 150, 200)
            .filter { it <= maxDenominator }
        return candidates.firstOrNull { sizeMeters * 1000.0 / it <= availableMm } ?: maxDenominator
    }

    private fun ribCount(request: DetailingPlanRequest, topology: SlabStripTopology): Int {
        val areas = request.model.areas.filter { area ->
            topology.elementIds.isEmpty() || area.id in topology.elementIds ||
                area.propertyName.equals(topology.name, ignoreCase = true)
        }
        val spacing = topology.ribSpacingCm / 100.0
        if (spacing <= 0.0) return 1
        val crossSizes = areas.mapNotNull { area ->
            val points = area.nodeIds.mapNotNull { request.model.points[it] }
                .distinctBy { "${round(it.xMeters)}:${round(it.yMeters)}" }
            if (points.size < 3) return@mapNotNull null
            val width = points.maxOf { it.xMeters } - points.minOf { it.xMeters }
            val height = points.maxOf { it.yMeters } - points.minOf { it.yMeters }
            min(width, height)
        }
        val representative = crossSizes.maxOrNull() ?: return 1
        return max(1, floor(representative / spacing).toInt() + 1)
    }

    private fun slabPlanArea(request: DetailingPlanRequest, topology: SlabStripTopology): Double {
        val areas = request.model.areas.filter { area ->
            topology.elementIds.isEmpty() || area.id in topology.elementIds ||
                area.propertyName.equals(topology.name, ignoreCase = true)
        }
        val area = areas.sumOf { area ->
            val points = area.nodeIds.mapNotNull { request.model.points[it] }
                .distinctBy { "${round(it.xMeters)}:${round(it.yMeters)}" }
                .map { DxfPoint(it.xMeters * 1000.0, it.yMeters * 1000.0) }
            if (points.size < 3) 0.0 else CadGeometry.polygonArea(points) / 1_000_000.0
        }
        return if (area > 0.0) area else topology.continuousSpansMeters.sum() *
            max(topology.flangeWidthBCm / 100.0, 0.1)
    }

    private fun roofPerimeterMeters(request: DetailingPlanRequest): Double {
        val grid = request.model.joint.gridSystem
        val width = grid.totalLengthLx
        val length = grid.totalLengthLy
        return if (width > 0.0 && length > 0.0) 2.0 * (width + length) else 0.0
    }

    private fun footingLength(design: StripFootingDesign): Double =
        design.forces?.ultimate?.stationLengthMeters
            ?: design.forces?.accidental?.stationLengthMeters
            ?: design.forces?.service?.stationLengthMeters
            ?: design.family.footingLengthMeters
            ?: 1.0

    private fun footingSectionArea(family: com.archilab.rpa2024.engine.foundations.StripFootingFamily): Double =
        family.flangeWidthMeters * family.tableThicknessMeters +
            family.webWidthMeters * max(0.0, family.totalHeightMeters - family.tableThicknessMeters)

    private fun fmt(value: Double, decimals: Int = 2): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)

    private fun round(value: Double): Long = Math.round(value * 1000.0)
}
