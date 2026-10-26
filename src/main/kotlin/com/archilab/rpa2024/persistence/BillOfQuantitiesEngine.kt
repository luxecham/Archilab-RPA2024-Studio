package com.archilab.rpa2024.persistence

import com.archilab.rpa2024.cad.BarDevelopedLengthCalculator
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.engine.foundations.StripFootingDesign
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignResult
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignResult
import com.archilab.rpa2024.engine.secondary.ParapetDesignInput
import com.archilab.rpa2024.engine.secondary.ParapetGeometry
import com.archilab.rpa2024.engine.secondary.ParapetWallDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseDesignInput
import com.archilab.rpa2024.engine.secondary.StaircaseDesignResult
import com.archilab.rpa2024.engine.secondary.StaircaseDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseExtractor
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.ContinuousSlabInput
import com.archilab.rpa2024.engine.slabs.ContinuousSlabResult
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignResult
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import com.archilab.rpa2024.report.ReportAlign
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.report.ReportChapter
import com.archilab.rpa2024.report.ReportDocument
import com.archilab.rpa2024.report.ReportText
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class QuantityElementType(val label: String, val codePrefix: String) {
    COLUMN("Poteaux", "POT"),
    BEAM("Poutres et chaînages", "POU"),
    WALL("Voiles de contreventement", "VOI"),
    SLAB("Planchers", "PLA"),
    PARAPET("Acrotères", "ACR"),
    STAIRCASE("Escaliers", "ESC"),
    FOUNDATION("Semelles filantes", "SEM"),
    LEAN_CONCRETE("Béton de propreté", "BP")
}

data class QuantityLine(
    val code: String,
    val elementType: QuantityElementType,
    val designation: String,
    val concreteVolumeM3: Double,
    val formworkAreaM2: Double,
    val steelByDiameterKg: Map<Int, Double> = emptyMap(),
    val meshSteelKg: Double = 0.0,
    val notes: String = ""
) {
    val steelWeightKg: Double
        get() = steelByDiameterKg.values.sum() + meshSteelKg

    val steelRatioKgM3: Double
        get() = if (concreteVolumeM3 > 0.0) steelWeightKg / concreteVolumeM3 else 0.0
}

data class QuantityTotals(
    val concreteVolumeM3: Double,
    val formworkAreaM2: Double,
    val steelWeightKg: Double,
    val meshSteelKg: Double,
    val steelByDiameterKg: Map<Int, Double>
) {
    operator fun plus(other: QuantityTotals): QuantityTotals = QuantityTotals(
        concreteVolumeM3 = concreteVolumeM3 + other.concreteVolumeM3,
        formworkAreaM2 = formworkAreaM2 + other.formworkAreaM2,
        steelWeightKg = steelWeightKg + other.steelWeightKg,
        meshSteelKg = meshSteelKg + other.meshSteelKg,
        steelByDiameterKg = (steelByDiameterKg.keys + other.steelByDiameterKg.keys)
            .associateWith { diameter ->
                (steelByDiameterKg[diameter] ?: 0.0) + (other.steelByDiameterKg[diameter] ?: 0.0)
            }
    )

    companion object {
        val ZERO = QuantityTotals(0.0, 0.0, 0.0, 0.0, emptyMap())

        fun from(lines: List<QuantityLine>): QuantityTotals {
            var totals = ZERO
            lines.forEach { line ->
                totals = totals + QuantityTotals(
                    concreteVolumeM3 = line.concreteVolumeM3,
                    formworkAreaM2 = line.formworkAreaM2,
                    steelWeightKg = line.steelWeightKg,
                    meshSteelKg = line.meshSteelKg,
                    steelByDiameterKg = line.steelByDiameterKg
                )
            }
            return totals
        }
    }
}

data class BillOfQuantitiesRequest(
    val joint: StructuralJoint,
    val projectTitle: String = joint.jointName,
    val results: EtabsResults = EtabsResults(),
    val model: ParsedEtabsModel? = null,
    val foundationModel: SafeFoundationModel? = null,
    val parapetGeometry: ParapetGeometry? = ParapetGeometry(),
    val staircaseGeometries: List<StaircaseGeometry> = emptyList(),
    val parapetLengthMeters: Double? = null,
    val slabLiveLoadKNM2: Double = 1.5,
    val slabFinishingLoadKNM2: Double = 1.2,
    val slabPermanentSuperLoadKNM2: Double = 0.0
) {
    init {
        require(slabLiveLoadKNM2 >= 0.0) { "La surcharge de plancher doit être positive ou nulle." }
        require(slabFinishingLoadKNM2 >= 0.0) { "La charge de revêtement doit être positive ou nulle." }
        require(slabPermanentSuperLoadKNM2 >= 0.0) { "La charge permanente additionnelle doit être positive ou nulle." }
        require(parapetLengthMeters == null || parapetLengthMeters > 0.0) {
            "La longueur d'acrotère doit être positive."
        }
    }
}

/**
 * Metre detaille d'un joint. Chaque ligne correspond a une famille ou a un ouvrage et
 * contient le beton, le coffrage et les aciers ventiles par diametre.
 */
data class BillOfQuantities(
    val projectTitle: String,
    val jointName: String,
    val lines: List<QuantityLine>,
    val floorAreaM2: Double,
    val generatedOn: LocalDate = LocalDate.now(),
    val warnings: List<String> = emptyList()
) {
    val totals: QuantityTotals get() = QuantityTotals.from(lines)
    val concreteTotalM3: Double get() = totals.concreteVolumeM3
    val formworkTotalM2: Double get() = totals.formworkAreaM2
    val steelTotalKg: Double get() = totals.steelWeightKg
    val leanConcreteM3: Double
        get() = lines.filter { it.elementType == QuantityElementType.LEAN_CONCRETE }.sumOf { it.concreteVolumeM3 }
    val structuralConcreteM3: Double get() = (concreteTotalM3 - leanConcreteM3).coerceAtLeast(0.0)
    val steelByDiameterKg: Map<Int, Double>
        get() {
            val diameters = (standardDiameters() + totals.steelByDiameterKg.keys).distinct().sorted()
            return diameters.associateWith { diameter -> totals.steelByDiameterKg[diameter] ?: 0.0 }
        }

    val steelRatioKgM3: Double
        get() = if (structuralConcreteM3 > 0.0) steelTotalKg / structuralConcreteM3 else 0.0

    val steelRateKgM2: Double
        get() = if (floorAreaM2 > 0.0) steelTotalKg / floorAreaM2 else 0.0

    val concreteRateM3M2: Double
        get() = if (floorAreaM2 > 0.0) concreteTotalM3 / floorAreaM2 else 0.0

    val formworkRateM2M3: Double
        get() = if (structuralConcreteM3 > 0.0) formworkTotalM2 / structuralConcreteM3 else 0.0

    fun totalsByType(): Map<QuantityElementType, QuantityTotals> =
        QuantityElementType.entries.associateWith { type ->
            QuantityTotals.from(lines.filter { it.elementType == type })
        }.filterValues { it.concreteVolumeM3 > 0.0 || it.steelWeightKg > 0.0 || it.formworkAreaM2 > 0.0 }

    fun toChapterReport(number: Int = 15): ReportChapter {
        val status = when {
            lines.isEmpty() -> CheckStatus.FAILED
            warnings.isNotEmpty() -> CheckStatus.WARNING
            else -> CheckStatus.OK
        }
        val checks = listOf(
            Checks.boolean(
                code = "DQE_COMPLETUDE",
                label = "Complétude du métré",
                reference = "Métré quantitatif - dossier d'exécution",
                condition = lines.isNotEmpty(),
                required = "Au moins un poste quantifié",
                provided = "${lines.size} poste(s), ${steelByDiameterKg.size} diamètre(s) d'acier",
                failureStatus = CheckStatus.FAILED,
                message = "Aucune quantité n'a pu être calculée : vérifier le modèle et les familles."
            )
        )
        val diameterHeaders = standardDiameters().joinToString(" | ") { "HA$it" }
        val quantityRows = lines.map { line ->
            listOf(
                line.code,
                line.elementType.label,
                line.designation,
                ReportText.plain(line.concreteVolumeM3, 3),
                ReportText.plain(line.formworkAreaM2, 3),
                ReportText.plain(line.steelWeightKg, 2),
                ReportText.plain(line.steelRatioKgM3, 1),
                line.notes
            )
        } + listOf(
            listOf(
                "TOTAL",
                "Tous postes",
                "Métré général",
                ReportText.plain(concreteTotalM3, 3),
                ReportText.plain(formworkTotalM2, 3),
                ReportText.plain(steelTotalKg, 2),
                ReportText.plain(steelRatioKgM3, 1),
                ""
            )
        )
        val repartitionRows = totalsByType().map { (type, totals) ->
            listOf(
                type.label,
                ReportText.plain(totals.concreteVolumeM3, 3),
                ReportText.plain(totals.formworkAreaM2, 3),
                ReportText.plain(totals.steelWeightKg, 2)
            )
        }
        val blocks = buildList {
            add(
                ReportBlock.Paragraph(
                    "Métré calculé pour le joint $jointName sur une surface cumulée de plancher de " +
                        "${ReportText.plain(floorAreaM2, 2)} m². Ventilation des aciers : $diameterHeaders, " +
                        "plus treillis soudé de table.",
                    sizePt = 10
                )
            )
            add(
                ReportBlock.Table(
                    caption = "Détail quantitatif par poste",
                    headers = listOf("Code", "Poste", "Désignation", "Béton m³", "Coffrage m²", "Acier kg", "kg/m³", "Observation"),
                    rows = quantityRows,
                    alignments = listOf(
                        ReportAlign.LEFT, ReportAlign.LEFT, ReportAlign.LEFT,
                        ReportAlign.RIGHT, ReportAlign.RIGHT, ReportAlign.RIGHT,
                        ReportAlign.RIGHT, ReportAlign.LEFT
                    ),
                    fontSizePt = 8,
                    firstColumnBold = true
                )
            )
            add(
                ReportBlock.Table(
                    caption = "Récapitulatif par poste",
                    headers = listOf("Poste", "Béton m³", "Coffrage m²", "Acier kg"),
                    rows = repartitionRows,
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.RIGHT, ReportAlign.RIGHT, ReportAlign.RIGHT),
                    firstColumnBold = true
                )
            )
            add(
                ReportBlock.KeyValues(
                    caption = "Ratios globaux",
                    entries = listOf(
                        "Béton total" to "${ReportText.num(concreteTotalM3, 3)} m³",
                        "dont béton de propreté" to "${ReportText.num(leanConcreteM3, 3)} m³",
                        "Coffrage total" to "${ReportText.num(formworkTotalM2, 2)} m²",
                        "Acier total" to "${ReportText.num(steelTotalKg, 2)} kg",
                        "Ratio acier / béton structurel" to "${ReportText.num(steelRatioKgM3, 1)} kg/m³",
                        "Acier par m² de plancher" to "${ReportText.num(steelRateKgM2, 2)} kg/m²",
                        "Béton par m² de plancher" to "${ReportText.num(concreteRateM3M2, 3)} m³/m²",
                        "Coffrage par m³ de béton" to "${ReportText.num(formworkRateM2M3, 2)} m²/m³",
                        "Aciers par diamètre" to steelByDiameterKg.entries.joinToString(" ; ") {
                            "HA${it.key} = ${ReportText.num(it.value, 2)} kg"
                        }
                    )
                )
            )
            if (warnings.isNotEmpty()) {
                add(ReportBlock.Note("Avertissements de métré : ${warnings.joinToString(" ; ")}", "Métré - réserves"))
            }
        }
        return ReportChapter(
            number = number,
            title = "Métré et devis quantitatif des ouvrages",
            summary = "Béton ${ReportText.num(concreteTotalM3, 2)} m³, coffrage " +
                "${ReportText.num(formworkTotalM2, 1)} m², acier ${ReportText.num(steelTotalKg, 1)} kg, " +
                "ratio ${ReportText.num(steelRatioKgM3, 1)} kg/m³.",
            status = status,
            blocks = blocks
        )
    }

    fun appendTo(document: ReportDocument, number: Int = 15): ReportDocument =
        document.copy(
            annexes = document.annexes.filterNot { it.number == number } + toChapterReport(number)
        )

    fun writeXlsx(path: Path): Path {
        path.parent?.let(Files::createDirectories)
        val workbook = XSSFWorkbook()
        val titleStyle = workbook.createCellStyle().apply {
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
        val diameters = steelByDiameterKg.keys.sorted()
        val dqe = workbook.createSheet("DQE")
        dqe.createRow(0).createCell(0).setCellValue("DEVIS QUANTITATIF ET ESTIMATIF - MÉTRÉ")
        dqe.getRow(0).getCell(0).cellStyle = titleStyle
        dqe.createRow(1).createCell(0).setCellValue("Projet")
        dqe.getRow(1).createCell(1).setCellValue(projectTitle)
        dqe.createRow(2).createCell(0).setCellValue("Joint")
        dqe.getRow(2).createCell(1).setCellValue(jointName)
        dqe.createRow(3).createCell(0).setCellValue("Date")
        dqe.getRow(3).createCell(1).setCellValue(generatedOn.toString())
        val headers = listOf(
            "N°", "Code", "Poste", "Désignation", "Béton (m³)", "Coffrage (m²)"
        ) + diameters.map { "HA$it (kg)" } + listOf("TS Ø5 (kg)", "Acier total (kg)", "Ratio (kg/m³)", "Observation")
        val headerRow = dqe.createRow(5)
        headers.forEachIndexed { index, header ->
            val cell = headerRow.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = titleStyle
        }
        lines.forEachIndexed { index, line ->
            val row = dqe.createRow(6 + index)
            var column = 0
            fun text(value: String) {
                row.createCell(column++).setCellValue(value)
            }
            fun number(value: Double, style: XSSFCellStyle = numberStyle) {
                val cell = row.createCell(column++)
                cell.setCellValue(value)
                cell.cellStyle = style
            }
            number((index + 1).toDouble(), integerStyle)
            text(line.code)
            text(line.elementType.label)
            text(line.designation)
            number(line.concreteVolumeM3)
            number(line.formworkAreaM2)
            diameters.forEach { diameter -> number(line.steelByDiameterKg[diameter] ?: 0.0) }
            number(line.meshSteelKg)
            number(line.steelWeightKg)
            number(line.steelRatioKgM3)
            text(line.notes)
        }
        val totalsRow = dqe.createRow(6 + lines.size)
        totalsRow.createCell(0).setCellValue("TOTAL")
        totalsRow.getCell(0).cellStyle = titleStyle
        totalsRow.createCell(4).setCellValue(concreteTotalM3)
        totalsRow.createCell(5).setCellValue(formworkTotalM2)
        var totalColumn = 6
        diameters.forEach { diameter ->
            totalsRow.createCell(totalColumn++).setCellValue(steelByDiameterKg[diameter] ?: 0.0)
        }
        totalsRow.createCell(totalColumn++).setCellValue(totals.meshSteelKg)
        totalsRow.createCell(totalColumn++).setCellValue(steelTotalKg)
        totalsRow.createCell(totalColumn).setCellValue(steelRatioKgM3)
        (0 until headers.size).forEach { column ->
            dqe.autoSizeColumn(column)
            dqe.setColumnWidth(column, min(dqe.getColumnWidth(column) + 512, 9000))
        }

        val summary = workbook.createSheet("Récapitulatif")
        val summaryHeader = summary.createRow(0)
        listOf("Poste", "Béton (m³)", "Coffrage (m²)", "Acier (kg)", "Ratio acier (kg/m³)").forEachIndexed { index, header ->
            val cell = summaryHeader.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = titleStyle
        }
        totalsByType().entries.forEachIndexed { index, entry ->
            val row = summary.createRow(index + 1)
            row.createCell(0).setCellValue(entry.key.label)
            row.createCell(1).setCellValue(entry.value.concreteVolumeM3)
            row.createCell(2).setCellValue(entry.value.formworkAreaM2)
            row.createCell(3).setCellValue(entry.value.steelWeightKg)
            row.createCell(4).setCellValue(
                if (entry.value.concreteVolumeM3 > 0.0) entry.value.steelWeightKg / entry.value.concreteVolumeM3 else 0.0
            )
        }
        val ratioStart = totalsByType().size + 3
        listOf(
            "Surface cumulée de plancher (m²)" to floorAreaM2,
            "Béton total (m³)" to concreteTotalM3,
            "Coffrage total (m²)" to formworkTotalM2,
            "Acier total (kg)" to steelTotalKg,
            "Ratio acier / béton (kg/m³)" to steelRatioKgM3,
            "Acier par m² de plancher (kg/m²)" to steelRateKgM2,
            "Béton par m² de plancher (m³/m²)" to concreteRateM3M2,
            "Coffrage par m³ de béton (m²/m³)" to formworkRateM2M3
        ).forEachIndexed { index, (label, value) ->
            val row = summary.createRow(ratioStart + index)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
        }
        (0 until 5).forEach { summary.autoSizeColumn(it) }

        val steel = workbook.createSheet("Aciers par diamètre")
        val steelHeader = steel.createRow(0)
        listOf("Diamètre", "Poids (kg)", "Part (%)").forEachIndexed { index, header ->
            val cell = steelHeader.createCell(index)
            cell.setCellValue(header)
            cell.cellStyle = titleStyle
        }
        steelByDiameterKg.entries.forEachIndexed { index, entry ->
            val row = steel.createRow(index + 1)
            row.createCell(0).setCellValue("HA${entry.key}")
            row.createCell(1).setCellValue(entry.value)
            row.createCell(2).setCellValue(if (steelTotalKg > 0.0) entry.value / steelTotalKg * 100.0 else 0.0)
        }
        val meshRow = steel.createRow(steelByDiameterKg.size + 1)
        meshRow.createCell(0).setCellValue("TS Ø5")
        meshRow.createCell(1).setCellValue(totals.meshSteelKg)
        meshRow.createCell(2).setCellValue(if (steelTotalKg > 0.0) totals.meshSteelKg / steelTotalKg * 100.0 else 0.0)
        (0 until 3).forEach { steel.autoSizeColumn(it) }

        Files.newOutputStream(path).use { output ->
            workbook.use { it.write(output) }
        }
        return path
    }

    companion object {
        fun standardDiameters(): List<Int> = listOf(6, 8, 10, 12, 14, 16, 20)
    }
}

/**
 * Moteur de mette d'un joint. Toutes les dimensions proviennent des familles, des
 * connectivites ETABS et des moteurs de dimensionnement ; aucune quantite n'est saisie
 * en dur. L'acier suit les memes longueurs developpees BAEL/RPA que les plans DXF.
 */
class BillOfQuantitiesEngine(
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val slabSolver: ContinuousRibbedSlabSolver = ContinuousRibbedSlabSolver(),
    private val parapetDesigner: ParapetWallDesigner = ParapetWallDesigner(),
    private val staircaseDesigner: StaircaseDesigner = StaircaseDesigner(),
    private val staircaseExtractor: StaircaseExtractor = StaircaseExtractor(),
    private val foundationEngine: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator()
) {

    fun calculate(
        joint: StructuralJoint,
        results: EtabsResults = EtabsResults(),
        model: ParsedEtabsModel? = null,
        foundationModel: SafeFoundationModel? = null
    ): BillOfQuantities = calculate(
        BillOfQuantitiesRequest(
            joint = joint,
            results = results,
            model = model,
            foundationModel = foundationModel
        )
    )

    fun calculate(request: BillOfQuantitiesRequest): BillOfQuantities {
        val builder = TakeoffBuilder(request)
        return builder.build()
    }

    private class SteelTakeoff {
        private val weights = sortedMapOf<Int, Double>()
        var meshKg: Double = 0.0

        fun addBar(diameterMm: Int, count: Int, developedLengthMeters: Double) {
            if (count <= 0 || developedLengthMeters <= 0.0 || diameterMm < 6) return
            addWeight(
                diameterMm,
                count * developedLengthMeters * BarDevelopedLengthCalculator.unitWeightKgPerMeter(diameterMm)
            )
        }

        fun addWeight(diameterMm: Int, weightKg: Double) {
            if (weightKg <= 0.0 || !weightKg.isFinite() || diameterMm < 6) return
            weights[diameterMm] = (weights[diameterMm] ?: 0.0) + weightKg
        }

        fun addMesh(weightKg: Double) {
            if (weightKg > 0.0 && weightKg.isFinite()) meshKg += weightKg
        }

        fun byDiameter(): Map<Int, Double> = weights.toSortedMap()
    }

    private inner class TakeoffBuilder(private val request: BillOfQuantitiesRequest) {
        private val joint = request.joint
        private val model = request.model
        private val materials = joint.materials
        private val lines = mutableListOf<QuantityLine>()
        private val warnings = mutableListOf<String>()
        private val counters = mutableMapOf<QuantityElementType, Int>()
        private var floorArea = 0.0

        fun build(): BillOfQuantities {
            addColumns()
            addBeams()
            addWalls()
            addSlabs()
            addParapet()
            addStaircases()
            addFoundations()
            val footprint = joint.gridSystem.totalLengthLx * joint.gridSystem.totalLengthLy
            val denominatorArea = if (floorArea > 0.0) floorArea else footprint
            return BillOfQuantities(
                projectTitle = request.projectTitle.ifBlank { joint.jointName },
                jointName = joint.jointName,
                lines = lines.toList(),
                floorAreaM2 = denominatorArea,
                warnings = warnings.distinct()
            )
        }

        private fun nextCode(type: QuantityElementType): String {
            val index = (counters[type] ?: 0) + 1
            counters[type] = index
            return "${type.codePrefix}-${index.toString().padStart(2, '0')}"
        }

        private fun addLine(
            type: QuantityElementType,
            designation: String,
            concrete: Double,
            formwork: Double,
            steel: SteelTakeoff,
            notes: String = ""
        ) {
            val byDiameter = steel.byDiameter()
            val line = QuantityLine(
                code = nextCode(type),
                elementType = type,
                designation = designation,
                concreteVolumeM3 = nonNegative(concrete),
                formworkAreaM2 = nonNegative(formwork),
                steelByDiameterKg = byDiameter.mapValues { nonNegative(it.value) }.filterValues { it > 1e-9 },
                meshSteelKg = nonNegative(steel.meshKg),
                notes = notes
            )
            lines += line
        }

        // -------------------------------------------------------------- poteaux

        private fun addColumns() {
            val designs = columnEngine.design(joint, request.results)
            designs.columns.forEach { design -> addColumn(design) }
        }

        private fun addColumn(design: ColumnDesignResult) {
            val family = design.family
            val elementLengths = frameLengths(family.elementIds, FrameKind.COLUMN, vertical = true)
            val fallbackHeight = when {
                family.clearHeightLclMeters > 0.0 -> family.clearHeightLclMeters
                joint.storySystem.orderedStories.isNotEmpty() -> joint.storySystem.orderedStories.first().heightHk
                else -> 0.0
            }
            val declaredCount = max(1, family.elementIds.size)
            val count = max(declaredCount, elementLengths.size)
            val totalLength = when {
                elementLengths.isEmpty() -> fallbackHeight * count
                elementLengths.size == count -> elementLengths.sum()
                else -> elementLengths.average() * count
            }
            if (totalLength <= 0.0) {
                warnings += "Poteau ${family.name} : hauteur libre indisponible, quantité ignorée."
                return
            }
            val averageLength = totalLength / count
            val sectionArea = if (family.shape == ColumnShape.CIRCULAR) {
                PI * (family.effectiveDiameterCm / 100.0).let { it * it } / 4.0
            } else {
                design.sectionWidthCm / 100.0 * design.sectionDepthCm / 100.0
            }
            val perimeter = if (family.shape == ColumnShape.CIRCULAR) {
                PI * family.effectiveDiameterCm / 100.0
            } else {
                2.0 * (design.sectionWidthCm + design.sectionDepthCm) / 100.0
            }
            val concrete = sectionArea * totalLength
            val formwork = perimeter * totalLength
            val steel = SteelTakeoff()
            val layout = design.layout
            if (layout == null) {
                warnings += "Poteau ${family.name} : aucune disposition d'acier adoptée."
            } else {
                val topStory = joint.storySystem.orderedStories.lastOrNull()?.storyName
                val topLevel = family.stories.any { it.equals(topStory, ignoreCase = true) }
                val lap = design.transverse.lapLengthCm / 100.0
                val anchorage = design.transverse.anchorageLengthCm / 100.0
                val barLength = averageLength + if (topLevel) anchorage else lap
                steel.addBar(layout.cornerBar.diameterMm, layout.cornerBar.count * count, barLength)
                layout.faceBar?.let { face ->
                    steel.addBar(face.diameterMm, face.count * count, barLength)
                }
                val stirrupCount = columnStirrupCount(design)
                val (width, depth) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
                    design.sectionWidthCm / 100.0,
                    design.sectionDepthCm / 100.0,
                    0.03,
                    design.transverse.stirrupDiameterMm
                )
                val stirrupLength = BarDevelopedLengthCalculator.closedStirrupLengthMeters(
                    width,
                    depth,
                    design.transverse.stirrupDiameterMm
                )
                steel.addBar(design.transverse.stirrupDiameterMm, stirrupCount * count, stirrupLength)
            }
            addLine(
                QuantityElementType.COLUMN,
                "${family.name} ${family.geometryLabel} - $count élément(s), lcl=${format(averageLength)} m",
                concrete,
                formwork,
                steel,
                "As adopté : ${layout?.label ?: "n/d"} ; cadres ${design.transverse.stirrupLabel}."
            )
        }

        // -------------------------------------------------------------- poutres

        private fun addBeams() {
            val designs = beamEngine.design(joint, request.results)
            designs.beams.forEach { design -> addBeam(design) }
        }

        private fun addBeam(design: BeamDesignResult) {
            val family = design.family
            val elementLengths = frameLengths(family.elementIds, FrameKind.BEAM, vertical = false)
            val declaredCount = max(1, family.elementIds.size)
            val count = max(declaredCount, elementLengths.size)
            val totalLength = when {
                elementLengths.isEmpty() -> family.maxClearSpanMeters * count
                elementLengths.size == count -> elementLengths.sum()
                else -> elementLengths.average() * count
            }
            if (totalLength <= 0.0) {
                warnings += "Poutre ${family.name} : longueur indisponible, quantité ignorée."
                return
            }
            val averageLength = totalLength / count
            val width = design.widthCm / 100.0
            val height = design.heightCm / 100.0
            val concrete = width * height * totalLength
            val formwork = 2.0 * height * totalLength
            val steel = SteelTakeoff()
            val anchorage = design.transverse.lapLengthCm / 100.0
            val bottom = design.spanLayout
            val top = design.supportLayout
            if (bottom != null) {
                steel.addBar(
                    bottom.continuousBar.diameterMm,
                    bottom.continuousBar.count * count,
                    averageLength + 2.0 * anchorage
                )
                bottom.additionalBar?.let {
                    steel.addBar(it.diameterMm, it.count * count, averageLength + 2.0 * anchorage)
                }
            }
            if (top != null) {
                steel.addBar(
                    top.continuousBar.diameterMm,
                    top.continuousBar.count * count,
                    averageLength + 2.0 * anchorage
                )
                top.additionalBar?.let {
                    steel.addBar(
                        it.diameterMm,
                        it.count * 2 * count,
                        min(averageLength, 2.0 * design.transverse.criticalZoneLengthCm / 100.0 + 2.0 * anchorage)
                    )
                }
            }
            val stirrupCount = BarDevelopedLengthCalculator.stirrupCount(
                totalLengthMeters = averageLength,
                firstStirrupFromSupportMeters = design.transverse.firstStirrupCm / 100.0,
                nodalZoneLengthMeters = design.transverse.nodalZoneLengthCm / 100.0,
                nodalSpacingMeters = design.transverse.nodalSpacingCm / 100.0,
                currentSpacingMeters = design.transverse.currentSpacingCm / 100.0
            )
            val (stirrupWidth, stirrupDepth) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
                width,
                height,
                0.025,
                design.transverse.stirrupDiameterMm
            )
            steel.addBar(
                design.transverse.stirrupDiameterMm,
                stirrupCount * count,
                BarDevelopedLengthCalculator.closedStirrupLengthMeters(
                    stirrupWidth,
                    stirrupDepth,
                    design.transverse.stirrupDiameterMm
                )
            )
            addLine(
                QuantityElementType.BEAM,
                "${family.name} ${family.geometryLabel} - $count élément(s), L=${format(averageLength)} m",
                concrete,
                formwork,
                steel,
                "Inf. ${design.spanLayout?.label ?: "n/d"} ; sup. ${design.supportLayout?.label ?: "n/d"}."
            )
        }

        // -------------------------------------------------------------- voiles

        private fun addWalls() {
            val designs = wallEngine.design(joint, request.results)
            designs.walls.forEach { design -> addWall(design) }
        }

        private fun addWall(design: WallDesignResult) {
            val family = design.family
            val count = 1
            val height = family.totalHeightHwMeters
            val concrete = family.lengthLwMeters * family.thicknessBwMeters * height
            val formwork = 2.0 * family.lengthLwMeters * height
            val steel = SteelTakeoff()
            val boundaryLap = 50.0 / 1000.0
            listOf(design.boundaryLeft, design.boundaryRight).forEach { boundary ->
                steel.addBar(
                    boundary.bars.diameterMm,
                    boundary.bars.count * count,
                    height + boundaryLap * boundary.bars.diameterMm
                )
                val spacing = boundary.stirrupSpacingCm.coerceAtLeast(1.0) / 100.0
                val stirrupCount = ceil(height / spacing).toInt().coerceAtLeast(1) * count
                val (stirrupWidth, stirrupDepth) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
                    boundary.lengthLcCm / 100.0,
                    family.thicknessBwMeters,
                    0.025,
                    boundary.stirrupDiameterMm
                )
                steel.addBar(
                    boundary.stirrupDiameterMm,
                    stirrupCount,
                    BarDevelopedLengthCalculator.closedStirrupLengthMeters(
                        stirrupWidth,
                        stirrupDepth,
                        boundary.stirrupDiameterMm
                    )
                )
            }
            val verticalSpacing = design.web.verticalSpacingCm / 100.0
            val verticalCount = (2.0 * ceil(family.lengthLwMeters / verticalSpacing.coerceAtLeast(0.01))).toInt()
                .coerceAtLeast(2) * count
            steel.addBar(
                design.web.barDiameterMm,
                verticalCount,
                height + 50.0 * design.web.barDiameterMm / 1000.0
            )
            val horizontalSpacing = design.web.horizontalSpacingCm / 100.0
            val horizontalCount = (2.0 * ceil(height / horizontalSpacing.coerceAtLeast(0.01))).toInt()
                .coerceAtLeast(2) * count
            steel.addBar(design.web.barDiameterMm, horizontalCount, family.lengthLwMeters)
            addLine(
                QuantityElementType.WALL,
                "${family.pierName} ${family.geometryLabel} - Hw=${format(height)} m",
                concrete,
                formwork,
                steel,
                "Rives ${design.boundaryLeft.barLabel}/${design.boundaryRight.barLabel} ; " +
                    "âme ${design.web.barLabel} e=${format(design.web.verticalSpacingCm)} cm."
            )
        }

        // -------------------------------------------------------------- planchers

        private fun addSlabs() {
            val slabResults = solveSlabs()
            slabResults.forEach { result -> addSlab(result) }
        }

        private fun solveSlabs(): List<ContinuousSlabResult> {
            val slabs = mutableListOf<ContinuousSlabResult>()
            joint.sectionCatalog.slabs
                .filter { it.continuousSpansMeters.isNotEmpty() }
                .forEach { topology ->
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
                        warnings += "Plancher ${topology.name} non métré : ${it.message.orEmpty()}."
                    }.getOrNull()?.let(slabs::add)
                }
            return slabs
        }

        private fun addSlab(result: ContinuousSlabResult) {
            val topology = result.topology
            val planArea = slabPlanArea(topology)
            if (planArea <= 0.0) {
                warnings += "Plancher ${topology.name} : surface en plan indisponible, métré du plancher ignoré."
                return
            }
            floorArea += planArea
            val b = topology.flangeWidthBCm / 100.0
            val b0 = topology.ribWidthB0Cm / 100.0
            val h0 = topology.compressionSlabH0Cm / 100.0
            val ht = topology.htMeters
            val equivalentThickness = if (topology.isSolidSlab) ht else {
                if (b > 0.0) (b * h0 + b0 * (ht - h0)) / b else ht
            }
            val concrete = planArea * equivalentThickness
            val formwork = planArea
            val ribs = ribCount(topology, planArea)
            val steel = SteelTakeoff()
            result.spans.forEach { span ->
                val bars = span.reinforcement.bars
                steel.addBar(
                    bars.diameterMm,
                    bars.count * ribs,
                    span.lengthMeters + 2.0 * span.reinforcement.anchorageLengthMeters
                )
            }
            result.supports.forEach { support ->
                val bars = support.reinforcement.bars
                steel.addBar(
                    bars.diameterMm,
                    bars.count * ribs,
                    support.reinforcement.anchorageLengthMeters * 2.0 +
                        min(2.0 * ht, support.reinforcement.anchorageLengthMeters * 4.0)
                )
            }
            result.shearDesigns.forEach { shear ->
                val span = result.spans.firstOrNull { it.index == shear.spanIndex }
                val length = span?.lengthMeters ?: topology.maxSpanMeters
                val count = ceil(length / shear.spacingMeters.coerceAtLeast(0.01)).toInt().coerceAtLeast(1)
                val (stirrupWidth, stirrupDepth) = BarDevelopedLengthCalculator.centeredStirrupDimensions(
                    b0,
                    (ht - h0).coerceAtLeast(0.05),
                    0.02,
                    shear.stirrupDiameterMm
                )
                steel.addBar(
                    shear.stirrupDiameterMm,
                    count * ribs,
                    BarDevelopedLengthCalculator.closedStirrupLengthMeters(
                        stirrupWidth,
                        stirrupDepth,
                        shear.stirrupDiameterMm
                    )
                )
            }
            if (!topology.isSolidSlab) {
                steel.addMesh(planArea * tableMeshWeightPerM2())
            }
            addLine(
                QuantityElementType.SLAB,
                "${topology.name} ${topology.geometryLabel} - ${format(planArea)} m², $ribs poutrelles",
                concrete,
                formwork,
                steel,
                if (topology.isSolidSlab) "Dalle pleine." else "Treillis soudé TS 150x150 Ø5 inclus."
            )
        }

        // -------------------------------------------------------------- acrotère

        private fun addParapet() {
            val geometry = request.parapetGeometry ?: return
            val length = request.parapetLengthMeters ?: roofPerimeter()
            if (length <= 0.0) {
                warnings += "Acrotère : longueur de terrasse indisponible, quantité ignorée."
                return
            }
            val zoneA = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint).zone.coeffA
            val design = parapetDesigner.design(
                ParapetDesignInput(geometry = geometry, materials = materials, zoneAccelerationA = zoneA)
            )
            val concrete = geometry.sectionAreaM2 * length
            val formwork = 2.0 * geometry.heightMeters * length
            val steel = SteelTakeoff()
            val mainSpacing = design.mainSteel.spacingMeters.coerceAtLeast(0.01)
            val mainCount = ceil(length / mainSpacing).toInt().coerceAtLeast(1)
            val hook = BarDevelopedLengthCalculator.hookLengthMeters(design.mainSteel.barDiameterMm, 90.0)
            steel.addBar(design.mainSteel.barDiameterMm, mainCount, geometry.heightMeters + 2.0 * hook)
            val distributionSpacing = design.distributionSteel.spacingMeters.coerceAtLeast(0.01)
            val distributionCount = ceil(geometry.heightMeters / distributionSpacing).toInt().coerceAtLeast(1)
            steel.addBar(design.distributionSteel.barDiameterMm, distributionCount, length)
            addLine(
                QuantityElementType.PARAPET,
                "Acrotère ${geometry.geometryLabel} - longueur ${format(length)} m",
                concrete,
                formwork,
                steel,
                "Principale ${design.mainSteel.label} ; répartition ${design.distributionSteel.label}."
            )
        }

        // -------------------------------------------------------------- escaliers

        private fun addStaircases() {
            val geometries = request.staircaseGeometries.ifEmpty {
                model?.let { staircaseExtractor.fromModel(it) }.orEmpty()
            }
            geometries.forEach { geometry ->
                val design = staircaseDesigner.design(StaircaseDesignInput(geometry = geometry, materials = materials))
                addStaircase(design)
            }
        }

        private fun addStaircase(design: StaircaseDesignResult) {
            val geometry = design.geometry
            val flights = geometry.numberOfFlights.coerceAtLeast(1)
            val concrete = geometry.waistThicknessMeters * geometry.developedFlightLengthMeters *
                geometry.flightWidthMeters * flights +
                geometry.landingThicknessMeters * geometry.landingLengthMeters * geometry.flightWidthMeters * flights
            val formwork = (geometry.developedFlightLengthMeters + geometry.landingLengthMeters) *
                geometry.flightWidthMeters * flights
            val steel = SteelTakeoff()
            val spanCount = ceil(geometry.flightWidthMeters / design.spanReinforcement.spacingMeters.coerceAtLeast(0.01))
                .toInt().coerceAtLeast(1) * flights
            val spanAnchorage = 50.0 * design.spanReinforcement.barDiameterMm / 1000.0
            steel.addBar(
                design.spanReinforcement.barDiameterMm,
                spanCount,
                geometry.developedFlightLengthMeters + geometry.landingLengthMeters + 2.0 * spanAnchorage
            )
            val supportCount = ceil(geometry.flightWidthMeters / design.supportReinforcement.spacingMeters.coerceAtLeast(0.01))
                .toInt().coerceAtLeast(1) * flights
            val supportAnchorage = 50.0 * design.supportReinforcement.barDiameterMm / 1000.0
            steel.addBar(
                design.supportReinforcement.barDiameterMm,
                supportCount,
                geometry.landingLengthMeters + 2.0 * supportAnchorage
            )
            val distributionCount = ceil(
                geometry.developedFlightLengthMeters / design.distributionReinforcement.spacingMeters.coerceAtLeast(0.01)
            ).toInt().coerceAtLeast(1) * flights
            steel.addBar(
                design.distributionReinforcement.barDiameterMm,
                distributionCount,
                geometry.flightWidthMeters
            )
            addLine(
                QuantityElementType.STAIRCASE,
                "${geometry.name} ${geometry.geometryLabel} - $flights volée(s)",
                concrete,
                formwork,
                steel,
                "Travée ${design.spanReinforcement.label} ; appui ${design.supportReinforcement.label}."
            )
        }

        // -------------------------------------------------------------- fondations

        private fun addFoundations() {
            val foundation = request.foundationModel
            if (foundation == null) {
                warnings += "Fondations SAFE non fournies : semelles et béton de propreté non métrés."
                return
            }
            val designs = foundationEngine.design(foundation, materials)
            designs.footings.forEach { design -> addFooting(design) }
        }

        private fun addFooting(design: StripFootingDesign) {
            val family = design.family
            val count = max(1, family.elementIds.size)
            val length = footingLength(design)
            if (length <= 0.0) {
                warnings += "Semelle ${family.name} : longueur indisponible, quantité ignorée."
                return
            }
            val sectionArea = family.flangeWidthMeters * family.tableThicknessMeters +
                family.webWidthMeters * (family.totalHeightMeters - family.tableThicknessMeters).coerceAtLeast(0.0)
            val concrete = sectionArea * length * count
            val formwork = 2.0 * family.totalHeightMeters * length * count
            val steel = SteelTakeoff()
            val cover = max(family.topCoverMeters, family.bottomCoverMeters)
            listOf(design.supportSteel, design.spanSteel).forEach { layout ->
                steel.addBar(
                    layout.barDiameterMm,
                    layout.barCount * count,
                    max(length - 2.0 * cover, 0.1)
                )
            }
            val transverseSpacing = design.transverseSteel.spacingMeters.coerceAtLeast(0.01)
            val transverseCount = ceil(length / transverseSpacing).toInt().coerceAtLeast(1) * count
            steel.addBar(
                design.transverseSteel.barDiameterMm,
                transverseCount,
                max(family.flangeWidthMeters - 2.0 * cover, 0.1)
            )
            addLine(
                QuantityElementType.FOUNDATION,
                "${family.name} ${family.sectionLabel} - ${format(length)} m × $count",
                concrete,
                formwork,
                steel,
                "Appui ${design.supportSteel.label} ; travée ${design.spanSteel.label} ; " +
                    "transversal ${design.transverseSteel.label}."
            )
            val leanVolume = (family.flangeWidthMeters + 0.20) * (length + 0.20) * 0.10 * count
            addLine(
                QuantityElementType.LEAN_CONCRETE,
                "Béton de propreté sous ${family.name} (10 cm)",
                leanVolume,
                0.0,
                SteelTakeoff(),
                "Emprise élargie de 10 cm de chaque côté."
            )
        }

        // -------------------------------------------------------------- outils

        private fun frameLengths(ids: List<String>, kind: FrameKind, vertical: Boolean): List<Double> {
            val geometry = model ?: return emptyList()
            val target = ids.toSet()
            return geometry.frames
                .filter { it.id in target && it.kind == kind }
                .mapNotNull { frame ->
                    val start = geometry.points[frame.startNodeId] ?: return@mapNotNull null
                    val end = geometry.points[frame.endNodeId] ?: return@mapNotNull null
                    if (vertical) start.distanceTo(end) else start.planDistanceTo(end)
                }
                .filter { it > 1e-9 }
        }

        private fun columnStirrupCount(design: ColumnDesignResult): Int {
            val transverse = design.transverse
            val height = transverse.clearHeightMeters
            if (height <= 0.0) return 0
            val nodalLength = min(transverse.nodalZoneLengthCm / 100.0, height / 2.0)
            val nodal = 2.0 * ceil(nodalLength / (transverse.nodalSpacingCm / 100.0).coerceAtLeast(0.01))
            val current = ceil((height - 2.0 * nodalLength) / (transverse.currentSpacingCm / 100.0).coerceAtLeast(0.01))
            return (nodal + current).toInt().coerceAtLeast(1)
        }

        private fun slabPlanArea(topology: com.archilab.rpa2024.model.SlabStripTopology): Double {
            val geometry = model
            if (geometry == null) {
                val footprint = joint.gridSystem.totalLengthLx * joint.gridSystem.totalLengthLy
                return if (joint.sectionCatalog.slabs.size == 1) footprint else 0.0
            }
            val ids = topology.elementIds.toSet()
            val areas = geometry.areas.filter { area ->
                area.kind == AreaKind.SLAB && (area.id in ids || area.propertyName.equals(topology.name, ignoreCase = true))
            }
            return areas.sumOf { area -> polygonArea(geometry, area.nodeIds) }
        }

        private fun ribCount(topology: com.archilab.rpa2024.model.SlabStripTopology, planArea: Double): Int {
            val spacing = topology.ribSpacingCm / 100.0
            if (spacing <= 0.0) return 1
            val geometry = model
            if (geometry != null) {
                val ids = topology.elementIds.toSet()
                val areas = geometry.areas.filter { area ->
                    area.kind == AreaKind.SLAB && (area.id in ids || area.propertyName.equals(topology.name, ignoreCase = true))
                }
                val count = areas.sumOf { area ->
                    val points = area.nodeIds.mapNotNull { geometry.points[it] }
                    if (points.isEmpty()) 0 else {
                        val width = points.maxOf { it.xMeters } - points.minOf { it.xMeters }
                        val length = points.maxOf { it.yMeters } - points.minOf { it.yMeters }
                        max(1, floor(min(width, length) / spacing).toInt() + 1)
                    }
                }
                if (count > 0) return count
            }
            if (planArea <= 0.0) return 1
            val footprint = joint.gridSystem.totalLengthLx * joint.gridSystem.totalLengthLy
            if (footprint <= 0.0) return 1
            return max(1, floor(min(joint.gridSystem.totalLengthLx, joint.gridSystem.totalLengthLy) / spacing).toInt() + 1)
        }

        private fun polygonArea(geometry: ParsedEtabsModel, nodeIds: List<String>): Double {
            val points = nodeIds.mapNotNull { geometry.points[it] }
                .distinctBy { "${Math.round(it.xMeters * 1000)}:${Math.round(it.yMeters * 1000)}" }
            if (points.size < 3) return 0.0
            var twiceArea = 0.0
            for (index in points.indices) {
                val current = points[index]
                val next = points[(index + 1) % points.size]
                twiceArea += current.xMeters * next.yMeters - next.xMeters * current.yMeters
            }
            return abs(twiceArea) / 2.0
        }

        private fun roofPerimeter(): Double {
            val grid = joint.gridSystem
            return 2.0 * (grid.totalLengthLx + grid.totalLengthLy)
        }

        private fun footingLength(design: StripFootingDesign): Double =
            design.forces?.ultimate?.stationLengthMeters
                ?: design.forces?.accidental?.stationLengthMeters
                ?: design.forces?.service?.stationLengthMeters
                ?: design.family.footingLengthMeters
                ?: 1.0

        private fun tableMeshWeightPerM2(): Double {
            val pitch = 0.15
            val wirePerMeter = 2.0 / pitch
            return wirePerMeter * BarDevelopedLengthCalculator.unitWeightKgPerMeter(5)
        }

        private fun nonNegative(value: Double): Double =
            if (value.isFinite()) max(0.0, value) else 0.0

        private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
    }
}
