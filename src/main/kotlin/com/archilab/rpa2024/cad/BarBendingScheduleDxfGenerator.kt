package com.archilab.rpa2024.cad

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Forme de façonnage dessinee dans la nomenclature. */
enum class BarShapeType(val label: String) {
    STRAIGHT("Barre droite"),
    HOOKED("Barre a crosses 90 deg"),
    STIRRUP("Cadre ferme 135 deg"),
    HAIRPIN("Epingle"),
    HAT("Chapeau / barre bateau")
}

/**
 * Position de nomenclature. Une position regroupe toutes les barres identiques
 * (meme forme, meme diametre, meme longueur) de tous les elements semblables.
 */
data class RebarScheduleItem(
    val position: Int,
    val elementType: String,
    val elementName: String,
    val mark: String,
    val shape: BarShapeType,
    val diameterMm: Int,
    val countPerElement: Int,
    val elementCount: Int,
    val developedLengthMeters: Double,
    val spacingCm: Double? = null,
    val note: String = ""
) {
    init {
        require(position >= 1) { "La position de ferraillage doit etre positive." }
        require(diameterMm >= 6) { "Le diametre de barre doit etre au moins egal a 6 mm." }
        require(countPerElement >= 0) { "Le nombre de barres par element doit etre positif ou nul." }
        require(elementCount >= 1) { "Le nombre d'elements identiques doit etre au moins egal a 1." }
        require(developedLengthMeters > 0.0) { "La longueur developpee doit etre positive." }
    }

    val totalCount: Int get() = countPerElement * elementCount
    val totalLengthMeters: Double get() = totalCount * developedLengthMeters
    val unitWeightKgPerMeter: Double get() = BarDevelopedLengthCalculator.unitWeightKgPerMeter(diameterMm)
    val totalWeightKg: Double get() = totalLengthMeters * unitWeightKgPerMeter
    val positionLabel: String get() = position.toString()
    val diameterLabel: String get() = "HA$diameterMm"
}

/** Volume de beton d'un element ou d'une famille, pour le ratio d'armatures. */
data class ConcreteVolumeItem(
    val elementType: String,
    val elementName: String,
    val volumeM3: Double
) {
    init {
        require(volumeM3 >= 0.0) { "Le volume de beton doit etre positif ou nul." }
    }
}

data class BbsDiameterSummary(
    val diameterMm: Int,
    val totalCount: Int,
    val totalLengthMeters: Double,
    val totalWeightKg: Double
)

/**
 * Nomenclature complete d'un jeu de planches. Les totaux sont recalcules a partir des
 * positions : une position n'est jamais saisie deux fois.
 */
data class BarBendingSchedule(
    val projectTitle: String,
    val jointName: String,
    val items: List<RebarScheduleItem>,
    val concreteElements: List<ConcreteVolumeItem>,
    val warnings: List<String> = emptyList()
) {
    val totalSteelWeightKg: Double get() = items.sumOf { it.totalWeightKg }
    val totalSteelLengthMeters: Double get() = items.sumOf { it.totalLengthMeters }
    val concreteVolumeM3: Double get() = concreteElements.sumOf { it.volumeM3 }

    val steelRatioKgM3: Double
        get() = if (concreteVolumeM3 > 0.0) totalSteelWeightKg / concreteVolumeM3 else 0.0

    fun byDiameter(): List<BbsDiameterSummary> = items.groupBy { it.diameterMm }
        .toSortedMap()
        .map { (diameter, group) ->
            BbsDiameterSummary(
                diameterMm = diameter,
                totalCount = group.sumOf { it.totalCount },
                totalLengthMeters = group.sumOf { it.totalLengthMeters },
                totalWeightKg = group.sumOf { it.totalWeightKg }
            )
        }

    fun weightForElement(elementName: String): Double =
        items.filter { it.elementName == elementName }.sumOf { it.totalWeightKg }

    fun rebarRatioFor(element: ConcreteVolumeItem): Double =
        if (element.volumeM3 > 0.0) weightForElement(element.elementName) / element.volumeM3 else 0.0
}

/**
 * Calculateur des longueurs developpees BAEL 91 revise 99 et RPA 2024. Le rayon de
 * courbure est pris au mandrin minimal de 6 phi (axe neutre a 3,5 phi du centre), et
 * les crochets parasismiques comportent une partie droite de 10 phi.
 */
object BarDevelopedLengthCalculator {
    fun unitWeightKgPerMeter(diameterMm: Int): Double =
        diameterMm.toDouble() * diameterMm.toDouble() / 162.0

    fun bendArcLengthMeters(diameterMm: Int, angleDegrees: Double): Double {
        val diameterMeters = diameterMm / 1000.0
        val neutralRadius = 3.5 * diameterMeters
        return neutralRadius * Math.toRadians(angleDegrees)
    }

    fun hookLengthMeters(diameterMm: Int, angleDegrees: Double = 135.0, straightFactor: Double = 10.0): Double {
        val diameterMeters = diameterMm / 1000.0
        return bendArcLengthMeters(diameterMm, angleDegrees) + straightFactor * diameterMeters
    }

    fun straightBarLengthMeters(straightLengthMeters: Double): Double = straightLengthMeters

    fun barWithEndHooks(
        straightLengthMeters: Double,
        diameterMm: Int,
        endCount: Int = 2,
        angleDegrees: Double = 90.0,
        straightFactor: Double = 10.0
    ): Double = straightLengthMeters +
        endCount * hookLengthMeters(diameterMm, angleDegrees, straightFactor)

    fun closedStirrupLengthMeters(
        centerlineWidthMeters: Double,
        centerlineHeightMeters: Double,
        diameterMm: Int,
        hookAngleDegrees: Double = 135.0
    ): Double = 2.0 * (centerlineWidthMeters + centerlineHeightMeters) +
        2.0 * hookLengthMeters(diameterMm, hookAngleDegrees)

    fun hatBarLengthMeters(
        totalStraightLengthMeters: Double,
        diameterMm: Int,
        endCount: Int = 2
    ): Double = totalStraightLengthMeters +
        endCount * hookLengthMeters(diameterMm, 90.0)

    fun centeredStirrupDimensions(
        sectionWidthMeters: Double,
        sectionHeightMeters: Double,
        coverMeters: Double,
        diameterMm: Int
    ): Pair<Double, Double> {
        val barRadius = diameterMm / 2000.0
        return Pair(
            max(sectionWidthMeters - 2.0 * coverMeters - 2.0 * barRadius, 0.0),
            max(sectionHeightMeters - 2.0 * coverMeters - 2.0 * barRadius, 0.0)
        )
    }

    fun stirrupCount(
        totalLengthMeters: Double,
        firstStirrupFromSupportMeters: Double,
        nodalZoneLengthMeters: Double,
        nodalSpacingMeters: Double,
        currentSpacingMeters: Double
    ): Int {
        if (totalLengthMeters <= 0.0 || nodalSpacingMeters <= 0.0 || currentSpacingMeters <= 0.0) return 0
        val effective = max(0.0, totalLengthMeters - 2.0 * firstStirrupFromSupportMeters)
        val nodalEachSide = min(nodalZoneLengthMeters, effective / 2.0)
        val nodalCount = 2.0 * ceil(nodalEachSide / nodalSpacingMeters)
        val currentLength = max(0.0, effective - 2.0 * nodalEachSide)
        val currentCount = ceil(currentLength / currentSpacingMeters)
        return (nodalCount + currentCount).toInt().coerceAtLeast(1)
    }
}

/** Resultat de la planche de nomenclature. */
data class BbsDrawingResult(
    val sheetCount: Int,
    val rowCount: Int,
    val widthMm: Double,
    val heightMm: Double
)

/**
 * Dessine la nomenclature en DXF et produit le classeur Excel de mette. Les vignettes
 * de façonnage sont vectorielles et les totaux sont issus de [BarBendingSchedule].
 */
class BarBendingScheduleDxfGenerator(
    private val rowsPerSheet: Int = 18,
    private val interSheetGapMm: Double = 120.0
) {
    init {
        require(rowsPerSheet >= 5) { "Une planche doit contenir au moins cinq positions." }
    }

    fun draw(
        document: DxfCoreEngine,
        schedule: BarBendingSchedule,
        project: CadProjectInfo,
        originX: Double = 0.0,
        originY: Double = 0.0,
        format: CadSheetFormat = CadSheetFormat.A0
    ): BbsDrawingResult {
        val pages = if (schedule.items.isEmpty()) listOf(emptyList()) else schedule.items.chunked(rowsPerSheet)
        pages.forEachIndexed { pageIndex, items ->
            val sheetX = originX + pageIndex * (format.widthMm + interSheetGapMm)
            drawPage(
                document = document,
                schedule = schedule,
                pageItems = items,
                project = project,
                originX = sheetX,
                originY = originY,
                format = format,
                sheetNumber = pageIndex + 1,
                pageCount = pages.size
            )
        }
        val width = pages.size * format.widthMm + (pages.size - 1) * interSheetGapMm
        return BbsDrawingResult(
            sheetCount = pages.size,
            rowCount = schedule.items.size,
            widthMm = width,
            heightMm = format.heightMm
        )
    }

    private fun drawPage(
        document: DxfCoreEngine,
        schedule: BarBendingSchedule,
        pageItems: List<RebarScheduleItem>,
        project: CadProjectInfo,
        originX: Double,
        originY: Double,
        format: CadSheetFormat,
        sheetNumber: Int,
        pageCount: Int
    ) {
        val title = "NOMENCLATURE ET FAÇONNAGE DES ACIERS - ${project.jointName}"
        val layout = document.addSheet(
            originX,
            originY,
            format,
            CadTitleBlock(
                project = project,
                sheetTitle = title,
                scaleLabel = "SANS ECHELLE",
                sheetNumber = sheetNumber
            )
        )
        val left = layout.drawingLeft + 4.0
        val right = layout.drawingLeft + layout.drawingWidth - 4.0
        val top = layout.drawingTop - 8.0
        val bottom = layout.drawingBottom + 8.0
        val totalWidth = right - left
        val columnWidths = listOf(
            totalWidth * 0.035,
            totalWidth * 0.155,
            totalWidth * 0.135,
            totalWidth * 0.095,
            totalWidth * 0.045,
            totalWidth * 0.055,
            totalWidth * 0.055,
            totalWidth * 0.075,
            totalWidth * 0.080,
            totalWidth * 0.075,
            totalWidth * 0.095
        )
        val headers = listOf(
            "POS", "ELEMENT", "TYPE", "SCHEMA", "Ø", "NB/EL.", "NB TOTAL",
            "L. DEV. (m)", "L. TOT. (m)", "POIDS (kg)", "OBSERVATIONS"
        )
        val columnX = mutableListOf(left)
        columnWidths.dropLast(1).forEach { width -> columnX += columnX.last() + width }
        val headerHeight = 11.0
        val footerHeight = 38.0
        val tableBottom = bottom + footerHeight + 8.0
        val availableRowsHeight = top - headerHeight - tableBottom
        val rowHeight = min(27.0, availableRowsHeight / max(pageItems.size + 1, 1))
        val effectiveRowHeight = max(rowHeight, 12.0)

        document.addLwPolyline(
            CadLayerStandard.TITLE_BLOCK,
            listOf(
                DxfPoint(left, tableBottom),
                DxfPoint(right, tableBottom),
                DxfPoint(right, top),
                DxfPoint(left, top)
            ),
            closed = true
        )
        document.addLine(CadLayerStandard.TITLE_BLOCK, left, top - headerHeight, right, top - headerHeight)
        headers.forEachIndexed { index, header ->
            document.addText(
                CadLayerStandard.TEXT,
                columnX[index] + 2.0,
                top - headerHeight + 2.2,
                header,
                2.25
            )
        }
        columnX.drop(1).forEach { x ->
            document.addLine(CadLayerStandard.TITLE_BLOCK, x, tableBottom, x, top)
        }

        pageItems.forEachIndexed { rowIndex, item ->
            val rowTop = top - headerHeight - rowIndex * effectiveRowHeight
            val rowBottom = rowTop - effectiveRowHeight
            if (rowIndex > 0) {
                document.addLine(CadLayerStandard.TITLE_BLOCK, left, rowTop, right, rowTop)
            }
            val baseline = rowBottom + effectiveRowHeight * 0.36
            val centerY = (rowTop + rowBottom) / 2.0
            val bubbleCenter = DxfPoint(columnX[0] + columnWidths[0] / 2.0, centerY)
            document.addCircle(
                CadLayerStandard.AXIS_BUBBLES,
                bubbleCenter.x,
                bubbleCenter.y,
                min(4.5, effectiveRowHeight * 0.30)
            )
            document.addText(
                CadLayerStandard.TEXT,
                bubbleCenter.x,
                bubbleCenter.y - 1.25,
                item.position.toString(),
                2.4,
                alignment = CadTextAlignment.CENTER_MIDDLE
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[1] + 2.0,
                baseline,
                fitText("${item.elementType} ${item.elementName}", columnWidths[1], 2.0),
                2.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[2] + 2.0,
                baseline,
                fitText(item.shape.label, columnWidths[2], 1.9),
                1.9
            )
            drawShapeVignette(
                document,
                item.shape,
                DxfPoint(columnX[3] + 2.0, rowBottom + 2.0),
                DxfPoint(columnX[3] + columnWidths[3] - 2.0, rowTop - 2.0),
                item.diameterMm,
                item.developedLengthMeters
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[4] + 2.0,
                baseline,
                "HA${item.diameterMm}",
                2.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[5] + 2.0,
                baseline,
                item.countPerElement.toString(),
                2.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[6] + 2.0,
                baseline,
                item.totalCount.toString(),
                2.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[7] + 2.0,
                baseline,
                formatNumber(item.developedLengthMeters, 2),
                2.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[8] + 2.0,
                baseline,
                formatNumber(item.totalLengthMeters, 2),
                2.0
            )
            document.addText(
                CadLayerStandard.TEXT,
                columnX[9] + 2.0,
                baseline,
                formatNumber(item.totalWeightKg, 2),
                2.0
            )
            val observation = buildString {
                append(item.mark)
                item.spacingCm?.let { append(" - e=").append(formatNumber(it, 1)).append(" cm") }
                if (item.note.isNotBlank()) append(" - ").append(item.note)
            }
            document.addText(
                CadLayerStandard.TEXT,
                columnX[10] + 2.0,
                baseline,
                fitText(observation, columnWidths[10], 1.8),
                1.8
            )
            item.spacingCm?.let { spacing ->
                val bubble = DxfPoint(columnX[10] + columnWidths[10] - 8.0, centerY)
                document.addText(
                    CadLayerStandard.TEXT,
                    bubble.x,
                    bubble.y - 1.2,
                    "e=${formatNumber(spacing, 0)}",
                    1.7,
                    alignment = CadTextAlignment.CENTER_MIDDLE
                )
            }
        }

        drawFooter(document, schedule, left, right, bottom + 2.0, pageNumber = sheetNumber, pageCount = pageCount)
    }

    private fun drawFooter(
        document: DxfCoreEngine,
        schedule: BarBendingSchedule,
        left: Double,
        right: Double,
        bottom: Double,
        pageNumber: Int,
        pageCount: Int
    ) {
        document.addLine(CadLayerStandard.TITLE_BLOCK, left, bottom + 34.0, right, bottom + 34.0)
        document.addText(
            CadLayerStandard.TEXT,
            left,
            bottom + 25.0,
            "POIDS TOTAL ACIER = ${formatNumber(schedule.totalSteelWeightKg, 2)} kg   |   " +
                "VOLUME BETON = ${formatNumber(schedule.concreteVolumeM3, 3)} m3   |   " +
                "RATIO = ${formatNumber(schedule.steelRatioKgM3, 1)} kg/m3",
            2.5
        )
        val diameters = schedule.byDiameter().joinToString("   ") { summary ->
            "HA${summary.diameterMm} : ${formatNumber(summary.totalWeightKg, 1)} kg " +
                "(${formatNumber(summary.totalLengthMeters, 1)} m)"
        }
        document.addText(
            CadLayerStandard.TEXT,
            left,
            bottom + 15.0,
            "RECAPITULATIF PAR DIAMETRE : ${diameters.ifBlank { "-" }}",
            2.1
        )
        document.addText(
            CadLayerStandard.TEXT,
            left,
            bottom + 4.0,
            "Longueurs developpees calculees avec rayons de courbure BAEL et crochets RPA 2024 " +
                "(10 phi droits, cadres a 135 deg). Feuille $pageNumber / $pageCount.",
            1.8
        )
    }

    private fun drawShapeVignette(
        document: DxfCoreEngine,
        shape: BarShapeType,
        lowerLeft: DxfPoint,
        upperRight: DxfPoint,
        diameterMm: Int,
        developedLengthMeters: Double
    ) {
        val width = upperRight.x - lowerLeft.x
        val height = upperRight.y - lowerLeft.y
        val centerY = (lowerLeft.y + upperRight.y) / 2.0 + 1.5
        val margin = 3.0
        val left = lowerLeft.x + margin
        val right = upperRight.x - margin
        when (shape) {
            BarShapeType.STRAIGHT -> {
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, left, centerY, right, centerY)
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, left, centerY - 2.0, left, centerY + 2.0)
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, right, centerY - 2.0, right, centerY + 2.0)
            }
            BarShapeType.HOOKED -> {
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, left + 4.0, centerY - 1.0, right - 4.0, centerY - 1.0)
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, left + 4.0, centerY - 1.0, left + 4.0, centerY + 5.0)
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, right - 4.0, centerY - 1.0, right - 4.0, centerY - 7.0)
            }
            BarShapeType.STIRRUP -> {
                val inset = 4.0
                val rectangle = listOf(
                    DxfPoint(left + inset, centerY - height * 0.28),
                    DxfPoint(right - inset, centerY - height * 0.28),
                    DxfPoint(right - inset, centerY + height * 0.28),
                    DxfPoint(left + inset, centerY + height * 0.28)
                )
                document.addLwPolyline(CadLayerStandard.TRANSVERSE_STEEL, rectangle, closed = true)
                document.addLine(
                    CadLayerStandard.TRANSVERSE_STEEL,
                    left + inset,
                    centerY + height * 0.28,
                    left + inset + 5.0,
                    centerY + height * 0.28 - 3.0
                )
                document.addLine(
                    CadLayerStandard.TRANSVERSE_STEEL,
                    left + inset,
                    centerY - height * 0.28,
                    left + inset + 5.0,
                    centerY - height * 0.28 + 3.0
                )
            }
            BarShapeType.HAIRPIN -> {
                val bottomY = centerY - height * 0.28
                val topY = centerY + height * 0.30
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, left + 4.0, topY, left + 4.0, bottomY)
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, left + 4.0, bottomY, right - 4.0, bottomY)
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, right - 4.0, bottomY, right - 4.0, topY)
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, left + 4.0, topY, left + 8.0, topY - 2.5)
                document.addLine(CadLayerStandard.TRANSVERSE_STEEL, right - 4.0, topY, right - 8.0, topY - 2.5)
            }
            BarShapeType.HAT -> {
                val topY = centerY + height * 0.22
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, left + 3.0, topY, right - 3.0, topY)
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, left + 3.0, topY, left + 9.0, topY - 7.0)
                document.addLine(CadLayerStandard.LONGITUDINAL_STEEL, right - 3.0, topY, right - 9.0, topY - 7.0)
            }
        }
        document.addText(
            CadLayerStandard.TEXT,
            lowerLeft.x + 2.0,
            lowerLeft.y + 1.0,
            "Ø$diameterMm",
            1.35
        )
        val dimensionY = lowerLeft.y + 3.0
        document.addLine(CadLayerStandard.DIMENSIONS, left, dimensionY, right, dimensionY)
        document.addLine(CadLayerStandard.DIMENSIONS, left, dimensionY - 1.0, left, dimensionY + 1.0)
        document.addLine(CadLayerStandard.DIMENSIONS, right, dimensionY - 1.0, right, dimensionY + 1.0)
        document.addText(
            CadLayerStandard.TEXT,
            right,
            dimensionY + 1.2,
            "L=${formatNumber(developedLengthMeters, 2)} m",
            1.35,
            alignment = CadTextAlignment.RIGHT_MIDDLE
        )
    }

    /**
     * Exporte la nomenclature en classeur Excel : une feuille de positions, une synthese
     * par diametre et une synthese beton / ratio.
     */
    fun writeXlsx(schedule: BarBendingSchedule, project: CadProjectInfo, path: Path): Path {
        val workbook = XSSFWorkbook()
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
        val numberStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("0.000")
        }
        val integerStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("0")
        }

        val positions = workbook.createSheet("Nomenclature")
        positions.createRow(0).createCell(0).setCellValue("Nomenclature et façonnage des aciers")
        positions.getRow(0).getCell(0).cellStyle = headerStyle
        positions.createRow(1).createCell(0).setCellValue("Projet")
        positions.getRow(1).createCell(1).setCellValue(project.projectTitle)
        positions.createRow(2).createCell(0).setCellValue("Joint")
        positions.getRow(2).createCell(1).setCellValue(project.jointName)
        positions.createRow(3).createCell(0).setCellValue("Référence")
        positions.getRow(3).createCell(1).setCellValue(project.reference)
        val headers = listOf(
            "Position", "Élément", "Type", "Forme", "Diamètre (mm)", "Barres/élément",
            "Éléments identiques", "Total barres", "Longueur développée (m)",
            "Longueur totale (m)", "Poids unitaire (kg/m)", "Poids total (kg)",
            "Espacement (cm)", "Repère", "Observations"
        )
        val headerRow = positions.createRow(5)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = headerStyle
        }
        schedule.items.forEachIndexed { rowIndex, item ->
            val row = positions.createRow(6 + rowIndex)
            row.createCell(0).setCellValue(item.position.toDouble())
            row.createCell(1).setCellValue("${item.elementType} ${item.elementName}")
            row.createCell(2).setCellValue(item.mark)
            row.createCell(3).setCellValue(item.shape.label)
            row.createCell(4).setCellValue(item.diameterMm.toDouble())
            row.createCell(5).setCellValue(item.countPerElement.toDouble())
            row.createCell(6).setCellValue(item.elementCount.toDouble())
            row.createCell(7).setCellValue(item.totalCount.toDouble())
            row.createCell(8).setCellValue(item.developedLengthMeters)
            row.createCell(9).setCellValue(item.totalLengthMeters)
            row.createCell(10).setCellValue(item.unitWeightKgPerMeter)
            row.createCell(11).setCellValue(item.totalWeightKg)
            item.spacingCm?.let { row.createCell(12).setCellValue(it) }
            row.createCell(13).setCellValue(item.mark)
            row.createCell(14).setCellValue(item.note)
            listOf(7, 4, 5, 6).forEach { index -> row.getCell(index).cellStyle = integerStyle }
            listOf(8, 9, 10, 11).forEach { index -> row.getCell(index).cellStyle = numberStyle }
            item.spacingCm?.let { row.getCell(12).cellStyle = numberStyle }
        }
        val totalRow = positions.createRow(7 + schedule.items.size)
        totalRow.createCell(0).setCellValue("TOTAL")
        totalRow.createCell(9).setCellValue(schedule.totalSteelLengthMeters)
        totalRow.createCell(11).setCellValue(schedule.totalSteelWeightKg)
        (0 until headers.size).forEach { column ->
            positions.autoSizeColumn(column)
            positions.setColumnWidth(column, min(positions.getColumnWidth(column) + 512, 9000))
        }

        val diameters = workbook.createSheet("Aciers par diamètre")
        val diameterHeader = diameters.createRow(0)
        listOf("Diamètre", "Nombre total de barres", "Longueur totale (m)", "Poids total (kg)").forEachIndexed { index, value ->
            val cell = diameterHeader.createCell(index)
            cell.setCellValue(value)
            cell.cellStyle = headerStyle
        }
        schedule.byDiameter().forEachIndexed { index, summary ->
            val row = diameters.createRow(index + 1)
            row.createCell(0).setCellValue("HA${summary.diameterMm}")
            row.createCell(1).setCellValue(summary.totalCount.toDouble())
            row.createCell(2).setCellValue(summary.totalLengthMeters)
            row.createCell(3).setCellValue(summary.totalWeightKg)
        }
        (0 until 4).forEach { diameters.autoSizeColumn(it) }

        val concrete = workbook.createSheet("Béton et ratios")
        val concreteHeader = concrete.createRow(0)
        listOf("Élément", "Type", "Volume (m3)", "Poids acier (kg)", "Ratio (kg/m3)").forEachIndexed { index, value ->
            val cell = concreteHeader.createCell(index)
            cell.setCellValue(value)
            cell.cellStyle = headerStyle
        }
        schedule.concreteElements.forEachIndexed { index, element ->
            val row = concrete.createRow(index + 1)
            row.createCell(0).setCellValue(element.elementName)
            row.createCell(1).setCellValue(element.elementType)
            row.createCell(2).setCellValue(element.volumeM3)
            row.createCell(3).setCellValue(schedule.weightForElement(element.elementName))
            row.createCell(4).setCellValue(schedule.rebarRatioFor(element))
        }
        val summaryRow = concrete.createRow(schedule.concreteElements.size + 2)
        summaryRow.createCell(0).setCellValue("TOTAL")
        summaryRow.createCell(2).setCellValue(schedule.concreteVolumeM3)
        summaryRow.createCell(3).setCellValue(schedule.totalSteelWeightKg)
        summaryRow.createCell(4).setCellValue(schedule.steelRatioKgM3)
        (0 until 5).forEach { concrete.autoSizeColumn(it) }

        path.parent?.let(Files::createDirectories)
        Files.newOutputStream(path).use { stream ->
            workbook.use { it.write(stream) }
        }
        return path
    }

    private fun fitText(text: String, widthMm: Double, heightMm: Double): String {
        val maxCharacters = max(4, (widthMm / (heightMm * 0.58)).toInt())
        return if (text.length <= maxCharacters) text else text.take(max(1, maxCharacters - 1)) + "…"
    }

    private fun formatNumber(value: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", value)
}

/** Regroupe et numerote les positions dans l'ordre de creation des planches. */
internal class RebarScheduleCollector {
    private val items = mutableListOf<RebarScheduleItem>()
    private val concreteElements = mutableListOf<ConcreteVolumeItem>()
    private var nextPosition = 1

    fun add(
        elementType: String,
        elementName: String,
        shape: BarShapeType,
        diameterMm: Int,
        countPerElement: Int,
        elementCount: Int,
        developedLengthMeters: Double,
        spacingCm: Double? = null,
        note: String = ""
    ): RebarScheduleItem? {
        if (countPerElement <= 0 || developedLengthMeters <= 0.0) return null
        val position = nextPosition++
        val item = RebarScheduleItem(
            position = position,
            elementType = elementType,
            elementName = elementName,
            mark = "HA$diameterMm-P$position",
            shape = shape,
            diameterMm = diameterMm,
            countPerElement = countPerElement,
            elementCount = max(1, elementCount),
            developedLengthMeters = developedLengthMeters,
            spacingCm = spacingCm,
            note = note
        )
        items += item
        return item
    }

    fun addConcrete(elementType: String, elementName: String, volumeM3: Double) {
        if (volumeM3 > 0.0) {
            concreteElements += ConcreteVolumeItem(elementType, elementName, volumeM3)
        }
    }

    fun toSchedule(projectTitle: String, jointName: String, warnings: List<String> = emptyList()): BarBendingSchedule =
        BarBendingSchedule(
            projectTitle = projectTitle,
            jointName = jointName,
            items = items.toList(),
            concreteElements = concreteElements.toList(),
            warnings = warnings
        )

    val positionCount: Int get() = items.size
}
