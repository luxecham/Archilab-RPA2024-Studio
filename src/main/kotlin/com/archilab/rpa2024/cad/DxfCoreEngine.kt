package com.archilab.rpa2024.cad

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Calque normalise de la charte graphique des plans de structure. */
data class CadLayerDefinition(
    val name: String,
    val aciColor: Int,
    val lineType: String,
    val lineweightHundredthsMm: Int,
    val hatchPattern: String? = null
)

/** Charte des calques de l'agence, conforme aux conventions de dessin BA. */
object CadLayerStandard {
    val AXES = CadLayerDefinition("01_AXES", 8, "CENTER", 13)
    val AXIS_BUBBLES = CadLayerDefinition("02_BULLES_AXES", 2, "CONTINUOUS", 25)
    val DIMENSIONS = CadLayerDefinition("03_COTATIONS", 3, "CONTINUOUS", 18)
    val COLUMN_FORMWORK = CadLayerDefinition("04_COFFRAGE_POTEAUX", 4, "CONTINUOUS", 35, "SOLID")
    val WALL_FORMWORK = CadLayerDefinition("05_COFFRAGE_VOILES", 6, "CONTINUOUS", 40, "ANSI31")
    val BEAM_FORMWORK = CadLayerDefinition("06_COFFRAGE_POUTRES", 3, "CONTINUOUS", 30)
    val FOOTING_FORMWORK = CadLayerDefinition("07_COFFRAGE_SEMELLES", 5, "CONTINUOUS", 30)
    val LONGITUDINAL_STEEL = CadLayerDefinition("08_ACIER_LONGITUDINAL", 1, "CONTINUOUS", 50)
    val TRANSVERSE_STEEL = CadLayerDefinition("09_ACIER_TRANSVERSAL", 30, "CONTINUOUS", 25)
    val TEXT = CadLayerDefinition("10_TEXTES_ET_REPERES", 7, "CONTINUOUS", 20)
    val TITLE_BLOCK = CadLayerDefinition("11_CARTOUCHE_CTC", 7, "CONTINUOUS", 20)

    val all: List<CadLayerDefinition> = listOf(
        AXES, AXIS_BUBBLES, DIMENSIONS, COLUMN_FORMWORK, WALL_FORMWORK, BEAM_FORMWORK,
        FOOTING_FORMWORK, LONGITUDINAL_STEEL, TRANSVERSE_STEEL, TEXT, TITLE_BLOCK
    )

    fun named(name: String): CadLayerDefinition? =
        all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

/** Dimensions normalisees des planches DXF (largeur x hauteur en mm). */
enum class CadSheetFormat(val label: String, val widthMm: Double, val heightMm: Double) {
    A0("A0", 1189.0, 841.0),
    A1("A1", 841.0, 594.0),
    A3("A3", 420.0, 297.0)
}

data class DxfPoint(val x: Double, val y: Double) {
    operator fun plus(other: DxfPoint): DxfPoint = DxfPoint(x + other.x, y + other.y)
    operator fun minus(other: DxfPoint): DxfPoint = DxfPoint(x - other.x, y - other.y)
    operator fun times(factor: Double): DxfPoint = DxfPoint(x * factor, y * factor)
}

/** En-tete commun a toutes les planches de coffrage, ferraillage et mette. */
data class CadProjectInfo(
    val projectTitle: String = "Projet",
    val jointName: String = "",
    val wilaya: String = "",
    val client: String = "",
    val engineer: String = "",
    val reference: String = "",
    val revision: String = "A",
    val date: LocalDate = LocalDate.now()
) {
    val dateLabel: String get() = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

data class CadTitleBlock(
    val project: CadProjectInfo,
    val sheetTitle: String,
    val scaleLabel: String,
    val sheetNumber: Int
)

/** Zone utile d'une planche apres tracage du cadre et du cartouche. */
data class CadSheetLayout(
    val originX: Double,
    val originY: Double,
    val format: CadSheetFormat,
    val drawingLeft: Double,
    val drawingBottom: Double,
    val drawingWidth: Double,
    val drawingHeight: Double
) {
    val drawingRight: Double get() = drawingLeft + drawingWidth
    val drawingTop: Double get() = drawingBottom + drawingHeight
}

enum class CadTextAlignment(val horizontalCode: Int, val verticalCode: Int) {
    LEFT_BOTTOM(0, 1),
    CENTER_MIDDLE(1, 2),
    RIGHT_MIDDLE(2, 2),
    CENTER_TOP(1, 3),
    LEFT_MIDDLE(0, 2)
}

/**
 * Ecriture DXF ASCII native en AC1024 (AutoCAD 2010). Toutes les coordonnees de la
 * charte graphique sont exprimees en millimetres, comme dans un espace papier metrique.
 *
 * Le document conserve les entites dans l'ordre de creation puis produit les sections
 * HEADER, TABLES, BLOCKS, ENTITIES et EOF demandees par AutoCAD.
 */
class DxfCoreEngine(
    private val drawingName: String = "PLANS_EXECUTION_RPA2024"
) {
    private val entities = mutableListOf<String>()
    private val dimensionBlocks = mutableListOf<DimensionBlock>()
    private val entityCounts = linkedMapOf<String, Int>()
    private var nextHandleValue: Long = 0x100L

    private val modelSpaceBlockRecordHandle = nextHandle()
    private val paperSpaceBlockRecordHandle = nextHandle()
    private val modelSpaceBlockHandle = nextHandle()
    private val paperSpaceBlockHandle = nextHandle()

    private var minimumX = Double.POSITIVE_INFINITY
    private var minimumY = Double.POSITIVE_INFINITY
    private var maximumX = Double.NEGATIVE_INFINITY
    private var maximumY = Double.NEGATIVE_INFINITY

    val layerNames: List<String> get() = CadLayerStandard.all.map { it.name }
    val entityCount: Int get() = entityCounts.values.sum()
    val entityTypeCounts: Map<String, Int> get() = entityCounts.toMap()

    private data class DimensionBlock(
        val name: String,
        val recordHandle: String,
        val blockHandle: String,
        val content: String
    )

    // ------------------------------------------------------------------ primitives

    fun addLine(
        layer: CadLayerDefinition,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double
    ) {
        val record = DxfRecord(this)
        record.entityHeader("LINE", layer)
        record.doubleCode(10, startX)
        record.doubleCode(20, startY)
        record.doubleCode(30, 0.0)
        record.doubleCode(11, endX)
        record.doubleCode(21, endY)
        record.doubleCode(31, 0.0)
        entities += record.text()
        registerEntity("LINE")
        includePoint(startX, startY)
        includePoint(endX, endY)
    }

    fun addLwPolyline(
        layer: CadLayerDefinition,
        points: List<DxfPoint>,
        closed: Boolean = false
    ) {
        if (points.isEmpty()) return
        val record = DxfRecord(this)
        record.entityHeader("LWPOLYLINE", layer, "AcDbPolyline")
        record.intCode(90, points.size)
        record.intCode(70, if (closed) 1 else 0)
        record.doubleCode(43, 0.0)
        points.forEach { point ->
            record.doubleCode(10, point.x)
            record.doubleCode(20, point.y)
            includePoint(point.x, point.y)
        }
        entities += record.text()
        registerEntity("LWPOLYLINE")
    }

    fun addCircle(layer: CadLayerDefinition, centerX: Double, centerY: Double, radius: Double) {
        if (radius <= 0.0) return
        val record = DxfRecord(this)
        record.entityHeader("CIRCLE", layer)
        record.doubleCode(10, centerX)
        record.doubleCode(20, centerY)
        record.doubleCode(30, 0.0)
        record.doubleCode(40, radius)
        entities += record.text()
        registerEntity("CIRCLE")
        includePoint(centerX - radius, centerY - radius)
        includePoint(centerX + radius, centerY + radius)
    }

    fun addArc(
        layer: CadLayerDefinition,
        centerX: Double,
        centerY: Double,
        radius: Double,
        startAngleDegrees: Double,
        endAngleDegrees: Double
    ) {
        if (radius <= 0.0) return
        val record = DxfRecord(this)
        record.entityHeader("ARC", layer)
        record.doubleCode(10, centerX)
        record.doubleCode(20, centerY)
        record.doubleCode(30, 0.0)
        record.doubleCode(40, radius)
        record.doubleCode(50, normalizeAngle(startAngleDegrees))
        record.doubleCode(51, normalizeAngle(endAngleDegrees))
        entities += record.text()
        registerEntity("ARC")
        includePoint(centerX - radius, centerY - radius)
        includePoint(centerX + radius, centerY + radius)
    }

    fun addHatch(
        layer: CadLayerDefinition,
        outline: List<DxfPoint>,
        pattern: String = layer.hatchPattern ?: "SOLID",
        patternScale: Double = 1.0,
        patternAngleDegrees: Double = 45.0
    ) {
        if (outline.size < 3) return
        val solid = pattern.equals("SOLID", ignoreCase = true)
        val record = DxfRecord(this)
        record.entityHeader("HATCH", layer, "AcDbHatch")
        record.doubleCode(10, 0.0)
        record.doubleCode(20, 0.0)
        record.doubleCode(30, 0.0)
        record.doubleCode(210, 0.0)
        record.doubleCode(220, 0.0)
        record.doubleCode(230, 1.0)
        record.stringCode(2, pattern)
        record.intCode(70, if (solid) 1 else 0)
        record.intCode(71, 0)
        record.intCode(91, 1)
        record.intCode(92, 3)
        record.intCode(72, 0)
        record.intCode(73, 1)
        record.intCode(93, outline.size)
        outline.forEach { point ->
            record.doubleCode(10, point.x)
            record.doubleCode(20, point.y)
            record.doubleCode(42, 0.0)
            includePoint(point.x, point.y)
        }
        record.intCode(97, 0)
        record.intCode(75, 0)
        record.intCode(76, 1)
        if (solid) {
            record.doubleCode(52, 0.0)
            record.doubleCode(41, 1.0)
            record.intCode(77, 0)
            record.intCode(78, 0)
        } else {
            record.doubleCode(52, normalizeAngle(patternAngleDegrees))
            record.doubleCode(41, patternScale)
            record.intCode(77, 0)
            record.intCode(78, 1)
            record.doubleCode(53, normalizeAngle(patternAngleDegrees))
            record.doubleCode(43, 0.0)
            record.doubleCode(44, 0.0)
            record.doubleCode(45, 3.0 * patternScale)
            record.doubleCode(46, 3.0 * patternScale)
            record.intCode(79, 2)
            record.doubleCode(49, 12.0 * patternScale)
            record.doubleCode(49, -3.0 * patternScale)
        }
        record.doubleCode(47, 1.0)
        record.intCode(98, 0)
        entities += record.text()
        registerEntity("HATCH")
    }

    fun addText(
        layer: CadLayerDefinition,
        x: Double,
        y: Double,
        text: String,
        heightMm: Double = 220.0,
        rotationDegrees: Double = 0.0,
        alignment: CadTextAlignment = CadTextAlignment.LEFT_BOTTOM
    ) {
        if (text.isBlank() || heightMm <= 0.0) return
        val record = DxfRecord(this)
        record.entityHeader("TEXT", layer, "AcDbText")
        record.doubleCode(10, x)
        record.doubleCode(20, y)
        record.doubleCode(30, 0.0)
        record.doubleCode(40, heightMm)
        record.stringCode(1, sanitizeSingleLine(text))
        record.doubleCode(50, normalizeAngle(rotationDegrees))
        record.stringCode(7, "STANDARD")
        record.intCode(72, alignment.horizontalCode)
        record.doubleCode(11, x)
        record.doubleCode(21, y)
        record.doubleCode(31, 0.0)
        record.intCode(73, alignment.verticalCode)
        entities += record.text()
        registerEntity("TEXT")
        includePoint(x, y)
    }

    fun addMText(
        layer: CadLayerDefinition,
        x: Double,
        y: Double,
        text: String,
        heightMm: Double = 200.0,
        widthMm: Double = 0.0,
        rotationDegrees: Double = 0.0
    ) {
        if (text.isBlank() || heightMm <= 0.0) return
        val record = DxfRecord(this)
        record.entityHeader("MTEXT", layer, "AcDbMText")
        record.doubleCode(10, x)
        record.doubleCode(20, y)
        record.doubleCode(30, 0.0)
        record.doubleCode(40, heightMm)
        record.doubleCode(41, widthMm)
        record.intCode(71, 5)
        record.intCode(72, 1)
        record.stringCode(1, sanitizeMultiLine(text))
        record.intCode(73, 1)
        record.doubleCode(44, 1.0)
        record.doubleCode(50, normalizeAngle(rotationDegrees))
        entities += record.text()
        registerEntity("MTEXT")
        includePoint(x, y)
    }

    /**
     * Cote lineaire associee a un bloc anonyme : lignes d'attache, ligne de cote,
     * flèches pleines et texte. [dimensionLinePoint] fixe l'orientation et le decalage.
     */
    fun addLinearDimension(
        layer: CadLayerDefinition,
        start: DxfPoint,
        end: DxfPoint,
        dimensionLinePoint: DxfPoint,
        textOverride: String? = null,
        textHeightMm: Double = 180.0
    ) {
        if (hypot(end.x - start.x, end.y - start.y) < 1e-6) return
        val blockName = "*D${dimensionBlocks.size + 1}"
        val recordHandle = nextHandle()
        val blockHandle = nextHandle()
        val direction = end - start
        val length = hypot(direction.x, direction.y)
        val unit = DxfPoint(direction.x / length, direction.y / length)
        val normal = DxfPoint(-unit.y, unit.x)
        val offset = (dimensionLinePoint - start).dot(normal)
        val first = start + normal * offset
        val second = end + normal * offset
        val middle = DxfPoint((first.x + second.x) / 2.0, (first.y + second.y) / 2.0)
        val textPoint = middle + normal * (textHeightMm * 0.65)
        val dimensionText = textOverride ?: DxfNumber.format(length)
        val angle = Math.toDegrees(atan2(direction.y, direction.x))
        val extensionOvershoot = textHeightMm * 0.8
        val arrowLength = textHeightMm * 0.75
        val arrowWidth = arrowLength * 0.34

        val block = DxfRecord(this)
        block.stringCode(0, "BLOCK")
        block.stringCode(5, blockHandle)
        block.stringCode(330, recordHandle)
        block.stringCode(100, "AcDbEntity")
        block.stringCode(8, layer.name)
        block.stringCode(100, "AcDbBlockBegin")
        block.stringCode(2, blockName)
        block.intCode(70, 1)
        block.doubleCode(10, 0.0)
        block.doubleCode(20, 0.0)
        block.doubleCode(30, 0.0)
        block.stringCode(3, blockName)
        block.stringCode(1, "")

        block.line(layer, start, start + normal * (offset + sign(offset) * extensionOvershoot))
        block.line(layer, end, end + normal * (offset + sign(offset) * extensionOvershoot))
        block.line(layer, first, second)
        block.solidTriangle(
            layer,
            first,
            first + unit * arrowLength,
            first + unit * (arrowLength * 0.5) + normal * (arrowWidth / 2.0),
            first + unit * (arrowLength * 0.5) - normal * (arrowWidth / 2.0)
        )
        block.solidTriangle(
            layer,
            second,
            second - unit * arrowLength,
            second - unit * (arrowLength * 0.5) + normal * (arrowWidth / 2.0),
            second - unit * (arrowLength * 0.5) - normal * (arrowWidth / 2.0)
        )
        block.text(layer, textPoint, dimensionText, textHeightMm, angle, CadTextAlignment.CENTER_MIDDLE)
        block.stringCode(0, "ENDBLK")
        block.stringCode(5, nextHandle())
        block.stringCode(330, recordHandle)
        block.stringCode(100, "AcDbEntity")
        block.stringCode(8, layer.name)
        block.stringCode(100, "AcDbBlockEnd")
        dimensionBlocks += DimensionBlock(blockName, recordHandle, blockHandle, block.text())

        val entity = DxfRecord(this)
        entity.entityHeader("DIMENSION", layer)
        entity.stringCode(100, "AcDbDimension")
        entity.stringCode(2, blockName)
        entity.doubleCode(10, middle.x)
        entity.doubleCode(20, middle.y)
        entity.doubleCode(30, 0.0)
        entity.doubleCode(11, textPoint.x)
        entity.doubleCode(21, textPoint.y)
        entity.doubleCode(31, 0.0)
        entity.intCode(70, 0)
        entity.intCode(71, 0)
        entity.doubleCode(42, length)
        entity.stringCode(1, textOverride.orEmpty())
        entity.stringCode(3, "RPA2024")
        entity.stringCode(100, "AcDbAlignedDimension")
        entity.doubleCode(13, start.x)
        entity.doubleCode(23, start.y)
        entity.doubleCode(33, 0.0)
        entity.doubleCode(14, end.x)
        entity.doubleCode(24, end.y)
        entity.doubleCode(34, 0.0)
        entity.doubleCode(50, normalizeAngle(angle))
        entity.stringCode(100, "AcDbRotatedDimension")
        entities += entity.text()
        registerEntity("DIMENSION")
        includePoint(start.x, start.y)
        includePoint(end.x, end.y)
        includePoint(first.x, first.y)
        includePoint(second.x, second.y)
    }

    /**
     * Repère d'armature complet : flèche sur la barre, ligne de rappel, bulle numerotee
     * et texte multi-lignes (nombre, diametre, espacement, longueur).
     */
    fun addLeaderWithBubble(
        layer: CadLayerDefinition,
        target: DxfPoint,
        bubbleCenter: DxfPoint,
        position: Int,
        label: String,
        bubbleRadiusMm: Double = 320.0,
        textHeightMm: Double = 190.0
    ) {
        val direction = bubbleCenter - target
        val length = hypot(direction.x, direction.y)
        if (length < 1e-6) return
        val unit = DxfPoint(direction.x / length, direction.y / length)
        val horizontal = DxfPoint(1.0, 0.0)
        val landing = bubbleCenter - horizontal * bubbleRadiusMm - horizontal * (bubbleRadiusMm * 0.35)
        val elbow = DxfPoint(landing.x, bubbleCenter.y)
        addLwPolyline(layer, listOf(target, elbow, landing))
        val arrowLength = textHeightMm * 0.75
        val arrowWidth = arrowLength * 0.32
        val normal = DxfPoint(-unit.y, unit.x)
        addSolidTriangle(
            layer,
            target,
            target + unit * arrowLength,
            target + unit * (arrowLength * 0.5) + normal * (arrowWidth / 2.0),
            target + unit * (arrowLength * 0.5) - normal * (arrowWidth / 2.0)
        )
        addCircle(layer, bubbleCenter.x, bubbleCenter.y, bubbleRadiusMm)
        addText(
            layer,
            bubbleCenter.x,
            bubbleCenter.y - textHeightMm * 0.38,
            position.toString(),
            heightMm = textHeightMm * 1.05,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )
        addMText(
            layer,
            landing.x + textHeightMm * 0.3,
            landing.y + textHeightMm * 1.1,
            label,
            heightMm = textHeightMm,
            widthMm = max(bubbleRadiusMm * 8.0, label.length * textHeightMm * 0.58)
        )
    }

    /** Cadre, zone de dessin et cartouche CTC normalise. */
    fun addSheet(
        originX: Double,
        originY: Double,
        format: CadSheetFormat,
        titleBlock: CadTitleBlock
    ): CadSheetLayout {
        val outer = listOf(
            DxfPoint(originX, originY),
            DxfPoint(originX + format.widthMm, originY),
            DxfPoint(originX + format.widthMm, originY + format.heightMm),
            DxfPoint(originX, originY + format.heightMm)
        )
        addLwPolyline(CadLayerStandard.TITLE_BLOCK, outer, closed = true)
        val inset = 6.0
        val inner = outer.map { point ->
            val x = if (point.x == originX) originX + inset else originX + format.widthMm - inset
            val y = if (point.y == originY) originY + inset else originY + format.heightMm - inset
            DxfPoint(x, y)
        }
        addLwPolyline(CadLayerStandard.TITLE_BLOCK, inner, closed = true)

        val titleWidth = min(270.0, format.widthMm * 0.64)
        val titleHeight = 42.0
        val titleLeft = originX + format.widthMm - inset - titleWidth
        val titleBottom = originY + inset
        val titleRight = originX + format.widthMm - inset
        val titleTop = titleBottom + titleHeight
        addLwPolyline(
            CadLayerStandard.TITLE_BLOCK,
            listOf(
                DxfPoint(titleLeft, titleBottom),
                DxfPoint(titleRight, titleBottom),
                DxfPoint(titleRight, titleTop),
                DxfPoint(titleLeft, titleTop)
            ),
            closed = true
        )
        addLine(CadLayerStandard.TITLE_BLOCK, titleLeft, titleBottom + 13.0, titleRight, titleBottom + 13.0)
        addLine(CadLayerStandard.TITLE_BLOCK, titleLeft, titleBottom + 27.0, titleRight, titleBottom + 27.0)
        addLine(
            CadLayerStandard.TITLE_BLOCK,
            titleLeft + titleWidth * 0.54,
            titleBottom,
            titleLeft + titleWidth * 0.54,
            titleTop
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + 3.0,
            titleTop - 5.5,
            titleBlock.project.projectTitle,
            2.5,
            alignment = CadTextAlignment.LEFT_BOTTOM
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + 3.0,
            titleTop - 11.0,
            "JOINT : ${titleBlock.project.jointName}",
            1.9
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + 3.0,
            titleBottom + 22.0,
            "MAITRE D'OUVRAGE : ${titleBlock.project.client.ifBlank { "-" }}",
            1.7
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + 3.0,
            titleBottom + 16.0,
            "BUREAU D'ETUDES : ${titleBlock.project.engineer.ifBlank { "-" }}",
            1.7
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + titleWidth * 0.54 + 3.0,
            titleBottom + 22.0,
            "WILAYA : ${titleBlock.project.wilaya.ifBlank { "-" }}",
            1.7
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + titleWidth * 0.54 + 3.0,
            titleBottom + 16.0,
            "DATE : ${titleBlock.project.dateLabel}",
            1.7
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + 3.0,
            titleBottom + 8.0,
            "REFERENCE : ${titleBlock.project.reference.ifBlank { "-" }}",
            1.7
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + 3.0,
            titleBottom + 3.0,
            "REV. ${titleBlock.project.revision}",
            1.7
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + titleWidth * 0.54 + 3.0,
            titleBottom + 8.0,
            "ECHELLE : ${titleBlock.scaleLabel}",
            1.8
        )
        addText(
            CadLayerStandard.TEXT,
            titleLeft + titleWidth * 0.54 + 3.0,
            titleBottom + 3.0,
            "PLANCHE ${titleBlock.sheetNumber}",
            1.8
        )
        addText(
            CadLayerStandard.TEXT,
            originX + format.widthMm / 2.0,
            originY + format.heightMm - 14.0,
            titleBlock.sheetTitle,
            35.0 / 10.0,
            alignment = CadTextAlignment.CENTER_MIDDLE
        )

        val drawingLeft = originX + 18.0
        val drawingBottom = originY + titleHeight + 16.0
        val drawingWidth = format.widthMm - 36.0
        val drawingHeight = format.heightMm - titleHeight - 38.0
        return CadSheetLayout(
            originX = originX,
            originY = originY,
            format = format,
            drawingLeft = drawingLeft,
            drawingBottom = drawingBottom,
            drawingWidth = drawingWidth,
            drawingHeight = drawingHeight
        )
    }

    fun entityTypes(): Map<String, Int> = entityCounts.toMap()

    fun toDxf(): String {
        val tables = StringBuilder(32 * 1024)
        appendTables(tables)
        val blocks = StringBuilder(32 * 1024)
        appendBlocks(blocks)
        val entitiesSection = StringBuilder(96 * 1024)
        appendEntities(entitiesSection)
        val header = StringBuilder(2 * 1024)
        appendHeader(header)
        return buildString(tables.length + blocks.length + entitiesSection.length + header.length + 16) {
            append(header)
            append(tables)
            append(blocks)
            append(entitiesSection)
            append("0\nEOF\n")
        }
    }

    fun write(path: Path): Path {
        path.parent?.let(Files::createDirectories)
        Files.writeString(path, toDxf(), Charsets.UTF_8)
        return path
    }

    // ------------------------------------------------------------------ sections DXF

    private fun appendHeader(builder: StringBuilder) {
        val minX = if (minimumX.isFinite()) minimumX else 0.0
        val minY = if (minimumY.isFinite()) minimumY else 0.0
        val maxX = if (maximumX.isFinite()) maximumX else 0.0
        val maxY = if (maximumY.isFinite()) maximumY else 0.0
        append(builder, 0, "SECTION")
        append(builder, 2, "HEADER")
        headerVariable(builder, "\$ACADVER", 1, "AC1024")
        headerVariable(builder, "\$DWGCODEPAGE", 3, "ANSI_1252")
        headerVariable(builder, "\$INSUNITS", 70, 4)
        headerVariable(builder, "\$MEASUREMENT", 70, 1)
        headerVariable(builder, "\$LUNITS", 70, 2)
        headerVariable(builder, "\$LUPREC", 70, 3)
        headerVariable(builder, "\$LTSCALE", 40, 1.0)
        headerVariable(builder, "\$TEXTSTYLE", 7, "STANDARD")
        headerVariable(builder, "\$DIMSTYLE", 2, "RPA2024")
        headerVariable(builder, "\$CLAYER", 8, CadLayerStandard.TEXT.name)
        headerVariable(builder, "\$HANDSEED", 5, nextHandle())
        headerVariable(builder, "\$EXTMIN", 10, minX)
        append(builder, 20, minY)
        append(builder, 30, 0.0)
        headerVariable(builder, "\$EXTMAX", 10, maxX)
        append(builder, 20, maxY)
        append(builder, 30, 0.0)
        append(builder, 0, "ENDSEC")
    }

    private fun appendTables(builder: StringBuilder) {
        append(builder, 0, "SECTION")
        append(builder, 2, "TABLES")

        appendTableStart(builder, "LTYPE", 6)
        appendLineType(builder, "CONTINUOUS", "Solid line", 0, emptyList())
        appendLineType(builder, "CENTER", "Center ____ _ ____ _", 4, listOf(12.5, -3.0, 2.5, -3.0))
        appendLineType(builder, "HIDDEN", "Hidden __ __ __", 2, listOf(6.0, -2.5))
        appendLineType(builder, "DASHED", "Dashed __ __", 2, listOf(9.0, -4.5))
        appendLineType(builder, "PHANTOM", "Phantom ___ _ _", 4, listOf(20.0, -3.0, 2.5, -3.0))
        appendTableEnd(builder)

        appendTableStart(builder, "LAYER", CadLayerStandard.all.size)
        CadLayerStandard.all.forEach { layer -> appendLayer(builder, layer) }
        appendTableEnd(builder)

        appendTableStart(builder, "STYLE", 1)
        appendTextStyle(builder, "STANDARD")
        appendTableEnd(builder)

        appendTableStart(builder, "DIMSTYLE", 1)
        appendDimStyle(builder, "RPA2024")
        appendTableEnd(builder)

        appendTableStart(builder, "APPID", 1)
        appendAppId(builder, "ACAD")
        appendTableEnd(builder)

        appendTableStart(builder, "BLOCK_RECORD", dimensionBlocks.size + 2)
        appendBlockRecord(builder, "*Model_Space", modelSpaceBlockRecordHandle)
        appendBlockRecord(builder, "*Paper_Space", paperSpaceBlockRecordHandle)
        dimensionBlocks.forEach { block -> appendBlockRecord(builder, block.name, block.recordHandle) }
        appendTableEnd(builder)

        append(builder, 0, "ENDSEC")
    }

    private fun appendBlocks(builder: StringBuilder) {
        append(builder, 0, "SECTION")
        append(builder, 2, "BLOCKS")
        appendEmptyBlock(
            builder,
            "*Model_Space",
            modelSpaceBlockRecordHandle,
            modelSpaceBlockHandle
        )
        appendEmptyBlock(
            builder,
            "*Paper_Space",
            paperSpaceBlockRecordHandle,
            paperSpaceBlockHandle
        )
        dimensionBlocks.forEach { builder.append(it.content) }
        append(builder, 0, "ENDSEC")
    }

    private fun appendEntities(builder: StringBuilder) {
        append(builder, 0, "SECTION")
        append(builder, 2, "ENTITIES")
        entities.forEach(builder::append)
        append(builder, 0, "ENDSEC")
    }

    private fun appendTableStart(builder: StringBuilder, name: String, count: Int) {
        append(builder, 0, "TABLE")
        append(builder, 2, name)
        append(builder, 5, nextHandle())
        append(builder, 100, "AcDbSymbolTable")
        append(builder, 70, count)
    }

    private fun appendTableEnd(builder: StringBuilder) {
        append(builder, 0, "ENDTAB")
    }

    private fun appendLineType(
        builder: StringBuilder,
        name: String,
        description: String,
        dashCount: Int,
        dashes: List<Double>
    ) {
        append(builder, 0, "LTYPE")
        append(builder, 5, nextHandle())
        append(builder, 100, "AcDbSymbolTableRecord")
        append(builder, 100, "AcDbLinetypeTableRecord")
        append(builder, 2, name)
        append(builder, 70, 0)
        append(builder, 3, description)
        append(builder, 72, 65)
        append(builder, 73, dashCount)
        append(builder, 40, dashes.sumOf { abs(it) })
        dashes.forEach { dash ->
            append(builder, 49, dash)
            append(builder, 74, 0)
        }
    }

    private fun appendLayer(builder: StringBuilder, layer: CadLayerDefinition) {
        append(builder, 0, "LAYER")
        append(builder, 5, nextHandle())
        append(builder, 100, "AcDbSymbolTableRecord")
        append(builder, 100, "AcDbLayerTableRecord")
        append(builder, 2, layer.name)
        append(builder, 70, 0)
        append(builder, 62, layer.aciColor)
        append(builder, 6, layer.lineType)
        append(builder, 370, layer.lineweightHundredthsMm)
    }

    private fun appendTextStyle(builder: StringBuilder, name: String) {
        append(builder, 0, "STYLE")
        append(builder, 5, nextHandle())
        append(builder, 100, "AcDbSymbolTableRecord")
        append(builder, 100, "AcDbTextStyleTableRecord")
        append(builder, 2, name)
        append(builder, 70, 0)
        append(builder, 40, 0.0)
        append(builder, 41, 1.0)
        append(builder, 50, 0.0)
        append(builder, 71, 0)
        append(builder, 42, 2.5)
        append(builder, 3, "isocp.shx")
        append(builder, 4, "")
    }

    private fun appendDimStyle(builder: StringBuilder, name: String) {
        append(builder, 0, "DIMSTYLE")
        append(builder, 105, nextHandle())
        append(builder, 100, "AcDbSymbolTableRecord")
        append(builder, 100, "AcDbDimStyleTableRecord")
        append(builder, 2, name)
        append(builder, 70, 0)
        append(builder, 40, 1.0)
        append(builder, 41, 2.5)
        append(builder, 42, 1.8)
        append(builder, 43, 1.0)
        append(builder, 44, 1.8)
        append(builder, 45, 0.0)
        append(builder, 46, 0.0)
        append(builder, 47, 0.0)
        append(builder, 48, 0.0)
        append(builder, 140, 2.5)
        append(builder, 141, 1.8)
        append(builder, 144, 1.0)
        append(builder, 146, 1.8)
        append(builder, 147, 0.625)
        append(builder, 171, 3)
        append(builder, 172, 2)
        append(builder, 271, 2)
        append(builder, 272, 2)
        append(builder, 274, 2)
        append(builder, 275, 0)
        append(builder, 276, 0)
        append(builder, 277, 2)
        append(builder, 278, 24)
        append(builder, 279, 0)
        append(builder, 280, 0)
        append(builder, 281, 0)
        append(builder, 282, 0)
        append(builder, 283, 0)
        append(builder, 284, 8)
    }

    private fun appendAppId(builder: StringBuilder, name: String) {
        append(builder, 0, "APPID")
        append(builder, 5, nextHandle())
        append(builder, 100, "AcDbSymbolTableRecord")
        append(builder, 100, "AcDbRegAppTableRecord")
        append(builder, 2, name)
        append(builder, 70, 0)
    }

    private fun appendBlockRecord(builder: StringBuilder, name: String, handle: String) {
        append(builder, 0, "BLOCK_RECORD")
        append(builder, 5, handle)
        append(builder, 100, "AcDbSymbolTableRecord")
        append(builder, 100, "AcDbBlockTableRecord")
        append(builder, 2, name)
        append(builder, 70, 0)
    }

    private fun appendEmptyBlock(
        builder: StringBuilder,
        name: String,
        blockRecordHandle: String,
        blockHandle: String
    ) {
        append(builder, 0, "BLOCK")
        append(builder, 5, blockHandle)
        append(builder, 330, blockRecordHandle)
        append(builder, 100, "AcDbEntity")
        append(builder, 8, "0")
        append(builder, 100, "AcDbBlockBegin")
        append(builder, 2, name)
        append(builder, 70, 0)
        append(builder, 10, 0.0)
        append(builder, 20, 0.0)
        append(builder, 30, 0.0)
        append(builder, 3, name)
        append(builder, 1, "")
        append(builder, 0, "ENDBLK")
        append(builder, 5, nextHandle())
        append(builder, 330, blockRecordHandle)
        append(builder, 100, "AcDbEntity")
        append(builder, 8, "0")
        append(builder, 100, "AcDbBlockEnd")
    }

    // ------------------------------------------------------------------ utilitaires

    private fun headerVariable(builder: StringBuilder, name: String, code: Int, value: Any) {
        append(builder, 9, name)
        append(builder, code, value)
    }

    private fun append(builder: StringBuilder, code: Int, value: Any) {
        builder.append(code).append('\n')
        when (value) {
            is Double -> builder.append(DxfNumber.format(value))
            is Float -> builder.append(DxfNumber.format(value.toDouble()))
            is Boolean -> builder.append(if (value) 1 else 0)
            else -> builder.append(value.toString())
        }
        builder.append('\n')
    }

    internal fun nextHandle(): String {
        nextHandleValue += 1
        return nextHandleValue.toString(16).uppercase(Locale.ROOT)
    }

    private fun registerEntity(type: String) {
        entityCounts[type] = (entityCounts[type] ?: 0) + 1
    }

    private fun includePoint(x: Double, y: Double) {
        if (!x.isFinite() || !y.isFinite()) return
        minimumX = min(minimumX, x)
        minimumY = min(minimumY, y)
        maximumX = max(maximumX, x)
        maximumY = max(maximumY, y)
    }

    private fun addSolidTriangle(
        layer: CadLayerDefinition,
        first: DxfPoint,
        second: DxfPoint,
        third: DxfPoint,
        fourth: DxfPoint
    ) {
        val record = DxfRecord(this)
        record.entityHeader("SOLID", layer)
        record.doubleCode(10, first.x)
        record.doubleCode(20, first.y)
        record.doubleCode(30, 0.0)
        record.doubleCode(11, second.x)
        record.doubleCode(21, second.y)
        record.doubleCode(31, 0.0)
        record.doubleCode(12, third.x)
        record.doubleCode(22, third.y)
        record.doubleCode(32, 0.0)
        record.doubleCode(13, fourth.x)
        record.doubleCode(23, fourth.y)
        record.doubleCode(33, 0.0)
        entities += record.text()
        registerEntity("SOLID")
        listOf(first, second, third, fourth).forEach { includePoint(it.x, it.y) }
    }

    private fun sanitizeSingleLine(text: String): String =
        text.replace("\r", " ").replace("\n", " ")

    private fun sanitizeMultiLine(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\P")

    private fun normalizeAngle(value: Double): Double {
        var normalized = value % 360.0
        if (normalized < 0.0) normalized += 360.0
        return normalized
    }

    private fun sign(value: Double): Double = if (value >= 0.0) 1.0 else -1.0

    private fun DxfPoint.dot(other: DxfPoint): Double = x * other.x + y * other.y

    private class DxfRecord(private val engine: DxfCoreEngine) {
        private val builder = StringBuilder(320)

        fun stringCode(code: Int, value: String): DxfRecord {
            builder.append(code).append('\n').append(value).append('\n')
            return this
        }

        fun intCode(code: Int, value: Int): DxfRecord {
            builder.append(code).append('\n').append(value).append('\n')
            return this
        }

        fun doubleCode(code: Int, value: Double): DxfRecord {
            builder.append(code).append('\n').append(DxfNumber.format(value)).append('\n')
            return this
        }

        fun entityHeader(type: String, layer: CadLayerDefinition, subclass: String? = null) {
            stringCode(0, type)
            stringCode(5, engine.nextHandle())
            stringCode(100, "AcDbEntity")
            stringCode(8, layer.name)
            if (subclass != null) stringCode(100, subclass)
        }

        fun line(layer: CadLayerDefinition, start: DxfPoint, end: DxfPoint) {
            entityHeader("LINE", layer)
            doubleCode(10, start.x)
            doubleCode(20, start.y)
            doubleCode(30, 0.0)
            doubleCode(11, end.x)
            doubleCode(21, end.y)
            doubleCode(31, 0.0)
        }

        fun solidTriangle(
            layer: CadLayerDefinition,
            first: DxfPoint,
            second: DxfPoint,
            third: DxfPoint,
            fourth: DxfPoint
        ) {
            entityHeader("SOLID", layer)
            doubleCode(10, first.x)
            doubleCode(20, first.y)
            doubleCode(30, 0.0)
            doubleCode(11, second.x)
            doubleCode(21, second.y)
            doubleCode(31, 0.0)
            doubleCode(12, third.x)
            doubleCode(22, third.y)
            doubleCode(32, 0.0)
            doubleCode(13, fourth.x)
            doubleCode(23, fourth.y)
            doubleCode(33, 0.0)
        }

        fun text(
            layer: CadLayerDefinition,
            point: DxfPoint,
            value: String,
            heightMm: Double,
            rotationDegrees: Double,
            alignment: CadTextAlignment
        ) {
            entityHeader("TEXT", layer, "AcDbText")
            doubleCode(10, point.x)
            doubleCode(20, point.y)
            doubleCode(30, 0.0)
            doubleCode(40, heightMm)
            stringCode(1, value.replace("\n", " "))
            doubleCode(50, rotationDegrees)
            stringCode(7, "STANDARD")
            intCode(72, alignment.horizontalCode)
            doubleCode(11, point.x)
            doubleCode(21, point.y)
            doubleCode(31, 0.0)
            intCode(73, alignment.verticalCode)
        }

        fun text(): String = builder.toString()
    }
}

internal object DxfNumber {
    fun format(value: Double): String {
        if (abs(value) < 1e-9) return "0.0"
        val text = String.format(Locale.ROOT, "%.6f", value)
            .trimEnd('0')
            .trimEnd('.')
        return if (text.contains('.')) text else "$text.0"
    }
}

/** Operations geometriques communes aux planches (coordonnees en mm). */
internal object CadGeometry {
    fun rectangle(centerX: Double, centerY: Double, width: Double, height: Double): List<DxfPoint> =
        listOf(
            DxfPoint(centerX - width / 2.0, centerY - height / 2.0),
            DxfPoint(centerX + width / 2.0, centerY - height / 2.0),
            DxfPoint(centerX + width / 2.0, centerY + height / 2.0),
            DxfPoint(centerX - width / 2.0, centerY + height / 2.0)
        )

    fun rectangleAlongSegment(
        start: DxfPoint,
        end: DxfPoint,
        width: Double,
        shift: Double = 0.0
    ): List<DxfPoint> {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = hypot(dx, dy)
        if (length < 1e-9) return emptyList()
        val normalX = -dy / length
        val normalY = dx / length
        val half = width / 2.0
        val p1 = DxfPoint(start.x + normalX * (shift - half), start.y + normalY * (shift - half))
        val p2 = DxfPoint(start.x + normalX * (shift + half), start.y + normalY * (shift + half))
        val p3 = DxfPoint(end.x + normalX * (shift + half), end.y + normalY * (shift + half))
        val p4 = DxfPoint(end.x + normalX * (shift - half), end.y + normalY * (shift - half))
        return listOf(p1, p2, p3, p4)
    }

    fun polygonArea(points: List<DxfPoint>): Double {
        if (points.size < 3) return 0.0
        var twiceArea = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            twiceArea += current.x * next.y - next.x * current.y
        }
        return abs(twiceArea) / 2.0
    }

    fun centroid(points: List<DxfPoint>): DxfPoint {
        if (points.isEmpty()) return DxfPoint(0.0, 0.0)
        var area = 0.0
        var cx = 0.0
        var cy = 0.0
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val cross = current.x * next.y - next.x * current.y
            area += cross
            cx += (current.x + next.x) * cross
            cy += (current.y + next.y) * cross
        }
        area /= 2.0
        if (abs(area) < 1e-9) {
            return DxfPoint(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
        }
        return DxfPoint(cx / (6.0 * area), cy / (6.0 * area))
    }

    fun angleDegrees(start: DxfPoint, end: DxfPoint): Double = Math.toDegrees(atan2(end.y - start.y, end.x - start.x))

    fun labelAngle(start: DxfPoint, end: DxfPoint): Double {
        val angle = angleDegrees(start, end)
        return if (angle > 90.0 || angle < -90.0) angle + 180.0 else angle
    }

    fun dashedLine(
        document: DxfCoreEngine,
        layer: CadLayerDefinition,
        start: DxfPoint,
        end: DxfPoint,
        dashMm: Double = 120.0,
        gapMm: Double = 60.0
    ) {
        val length = hypot(end.x - start.x, end.y - start.y)
        if (length < 1e-6) return
        val ux = (end.x - start.x) / length
        val uy = (end.y - start.y) / length
        var covered = 0.0
        while (covered < length) {
            val segment = min(dashMm, length - covered)
            document.addLine(
                layer,
                start.x + ux * covered,
                start.y + uy * covered,
                start.x + ux * (covered + segment),
                start.y + uy * (covered + segment)
            )
            covered += segment + gapMm
        }
    }

    fun chooseScale(
        planWidthMeters: Double,
        planHeightMeters: Double,
        availableWidthMm: Double,
        availableHeightMm: Double
    ): Int {
        val candidates = listOf(20, 25, 50, 75, 100, 125, 150, 200, 250, 300, 400, 500, 750, 1000)
        candidates.firstOrNull { denominator ->
            planWidthMeters * 1000.0 / denominator <= availableWidthMm &&
                planHeightMeters * 1000.0 / denominator <= availableHeightMm
        }?.let { return it }
        val denominator = max(
            planWidthMeters * 1000.0 / max(availableWidthMm, 1.0),
            planHeightMeters * 1000.0 / max(availableHeightMm, 1.0)
        )
        return max(1, ceil(denominator / 50.0).toInt() * 50)
    }

    fun polylineFromCoordinates(coordinates: List<Pair<Double, Double>>): List<DxfPoint> =
        coordinates.map { DxfPoint(it.first, it.second) }

    fun polar(centerX: Double, centerY: Double, radius: Double, angleDegrees: Double): DxfPoint {
        val radians = angleDegrees * PI / 180.0
        return DxfPoint(centerX + radius * cos(radians), centerY + radius * sin(radians))
    }
}
