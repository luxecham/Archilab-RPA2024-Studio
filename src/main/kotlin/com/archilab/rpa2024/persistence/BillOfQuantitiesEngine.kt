package com.archilab.rpa2024.persistence

import com.archilab.rpa2024.model.StructuralJoint
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.round

/**
 * Ligne de détail d'un poste du Métré et Devis Quantitatif et Estimatif (DQE).
 */
data class BoqItem(
    val itemNumber: String,
    val designation: String,
    val unit: String,
    val concreteVolumeM3: Double = 0.0,
    val formworkAreaM2: Double = 0.0,
    val rebarWeightKg: Double = 0.0,
    val steelRatioKgPerM3: Double = 0.0,
    val unitPriceDzd: Double,
    val quantity: Double,
    val totalPriceDzd: Double = quantity * unitPriceDzd,
    val rebarBreakdownByDiameterKg: Map<Int, Double> = emptyMap()
)

/**
 * Rapport de synthèse global du DQE pour le Joint structural étudié.
 */
data class BoqSummaryReport(
    val jointName: String,
    val projectTitle: String,
    val wilaya: String,
    val items: List<BoqItem>,
    val totalLeanConcreteM3: Double,
    val totalStructuralConcreteM3: Double,
    val totalFormworkAreaM2: Double,
    val totalRebarWeightKg: Double,
    val globalSteelRatioKgPerM3: Double,
    val totalDevelopedFloorAreaM2: Double,
    val steelRatioPerM2Floor: Double,
    val concreteRatioPerM2Floor: Double,
    val totalEstimatedCostDzd: Double,
    val rebarTotalByDiameterKg: Map<Int, Double>
)

/**
 * Moteur de calcul du Métré et du Devis Quantitatif et Estimatif (DQE) par Joint/Bloc.
 * Calcule automatiquement les cubatures de béton, surfaces de coffrage et tonnages d'acier
 * selon les sections découvertes dans ETABS et les ratios parasismiques du RPA 2024.
 */
class BillOfQuantitiesEngine {

    /**
     * Calcule le métré complet et le DQE d'un Joint structural.
     */
    fun computeBoq(
        joint: StructuralJoint,
        projectTitle: String = "Projet R+4 Bosquet",
        wilaya: String = "Alger"
    ): BoqSummaryReport {
        val items = mutableListOf<BoqItem>()

        val lx = joint.gridSystem.totalLengthLx.takeIf { it > 0 } ?: 15.0
        val ly = joint.gridSystem.totalLengthLy.takeIf { it > 0 } ?: 20.65
        val nStories = joint.storySystem.stories.count { !it.isPenthouseOrCage }.takeIf { it > 0 } ?: 4
        val totalFloorAreaM2 = lx * ly * nStories
        val perimeterRoofM = 2.0 * (lx + ly)

        val totalColCountPerStory = (joint.gridSystem.xGrids.size * joint.gridSystem.yGrids.size).coerceAtLeast(16)
        val nColumnsTotal = totalColCountPerStory * nStories

        // 1. TERRASSEMENTS & BÉTON DE PROPRETÉ
        val stripWidthB = 1.30 // Largeur moyenne semelle filante
        val totalFootingLengthM = (lx * joint.gridSystem.yGrids.size + ly * joint.gridSystem.xGrids.size).coerceAtLeast(80.0)
        val leanConcVol = totalFootingLengthM * (stripWidthB + 0.20) * 0.10

        items.add(
            BoqItem(
                itemNumber = "01.01",
                designation = "Béton de propreté dosé à 150 kg/m³ sous semelles (ép. 10 cm)",
                unit = "m³",
                concreteVolumeM3 = leanConcVol,
                unitPriceDzd = 8500.0,
                quantity = leanConcVol
            )
        )

        // 2. FONDATIONS (SEMELLES FILANTES & LONGRINES)
        val footingConcVol = totalFootingLengthM * (stripWidthB * 0.35 + 0.40 * 0.45) // Patin + nervure
        val footingFormArea = totalFootingLengthM * (2.0 * 0.35 + 2.0 * 0.45)
        val footingSteelRatio = 95.0 // kg/m³
        val footingSteelKg = footingConcVol * footingSteelRatio
        val footingRebarMap = mapOf(
            14 to footingSteelKg * 0.50,
            12 to footingSteelKg * 0.30,
            8 to footingSteelKg * 0.20
        )

        items.add(
            BoqItem(
                itemNumber = "02.01",
                designation = "Béton armé dosé à 350 kg/m³ pour semelles filantes et longrines",
                unit = "m³",
                concreteVolumeM3 = footingConcVol,
                formworkAreaM2 = footingFormArea,
                rebarWeightKg = footingSteelKg,
                steelRatioKgPerM3 = footingSteelRatio,
                unitPriceDzd = 16500.0,
                quantity = footingConcVol,
                rebarBreakdownByDiameterKg = footingRebarMap
            )
        )

        // 3. POTEAUX EN ÉLÉVATION
        val col = joint.sectionCatalog.columns.firstOrNull()
        val bCol = (col?.b1Cm ?: 35.0) / 100.0
        val hCol = (col?.h1Cm ?: 35.0) / 100.0
        val lcl = col?.clearHeightLclMeters ?: 2.65

        val colConcVol = (bCol * hCol * lcl) * nColumnsTotal
        val colFormArea = 2.0 * (bCol + hCol) * lcl * nColumnsTotal
        val colSteelRatio = 125.0 // kg/m³ pour poteaux parasismiques RPA 2024
        val colSteelKg = colConcVol * colSteelRatio
        val colRebarMap = mapOf(
            16 to colSteelKg * 0.45,
            14 to colSteelKg * 0.35,
            8 to colSteelKg * 0.20 // Cadres et étriers HA8
        )

        items.add(
            BoqItem(
                itemNumber = "03.01",
                designation = "Béton armé en élévation pour Poteaux (section ${col?.b1Cm?.toInt() ?: 35}x${col?.h1Cm?.toInt() ?: 35} cm)",
                unit = "m³",
                concreteVolumeM3 = colConcVol,
                formworkAreaM2 = colFormArea,
                rebarWeightKg = colSteelKg,
                steelRatioKgPerM3 = colSteelRatio,
                unitPriceDzd = 18500.0,
                quantity = colConcVol,
                rebarBreakdownByDiameterKg = colRebarMap
            )
        )

        // 4. POUTRES ET CHAÎNAGES
        val beam = joint.sectionCatalog.beams.firstOrNull()
        val bBeam = (beam?.bCm ?: 30.0) / 100.0
        val hBeam = (beam?.hCm ?: 50.0) / 100.0
        val totalBeamLengthPerStory = (lx * joint.gridSystem.yGrids.size + ly * joint.gridSystem.xGrids.size).coerceAtLeast(80.0)
        val totalBeamLengthM = totalBeamLengthPerStory * nStories

        val beamConcVol = bBeam * hBeam * totalBeamLengthM
        val beamFormArea = (bBeam + 2.0 * hBeam) * totalBeamLengthM
        val beamSteelRatio = 115.0 // kg/m³
        val beamSteelKg = beamConcVol * beamSteelRatio
        val beamRebarMap = mapOf(
            16 to beamSteelKg * 0.40,
            14 to beamSteelKg * 0.35,
            8 to beamSteelKg * 0.25 // Cadres HA8
        )

        items.add(
            BoqItem(
                itemNumber = "04.01",
                designation = "Béton armé pour Poutres principales et Chaînages (${(bBeam * 100).toInt()}x${(hBeam * 100).toInt()} cm)",
                unit = "m³",
                concreteVolumeM3 = beamConcVol,
                formworkAreaM2 = beamFormArea,
                rebarWeightKg = beamSteelKg,
                steelRatioKgPerM3 = beamSteelRatio,
                unitPriceDzd = 18000.0,
                quantity = beamConcVol,
                rebarBreakdownByDiameterKg = beamRebarMap
            )
        )

        // 5. VOILES DE CONTREVENTEMENT
        var wallConcVol = 0.0
        var wallFormArea = 0.0
        var wallSteelKg = 0.0
        val wallRebarMap = mutableMapOf<Int, Double>()

        val walls = joint.sectionCatalog.shearWalls
        if (walls.isNotEmpty()) {
            for (w in walls) {
                val volW = w.lengthLwMeters * w.thicknessBwMeters * w.totalHeightHwMeters
                val formW = 2.0 * (w.lengthLwMeters + w.thicknessBwMeters) * w.totalHeightHwMeters
                val steelW = volW * 105.0 // 105 kg/m³
                wallConcVol += volW
                wallFormArea += formW
                wallSteelKg += steelW
            }
        } else {
            // Voiles types de contreventement par défaut
            val volW = 2.0 * (3.0 * 0.20 * (lcl * nStories))
            val formW = 2.0 * (2.0 * 3.20) * (lcl * nStories)
            val steelW = volW * 105.0
            wallConcVol = volW
            wallFormArea = formW
            wallSteelKg = steelW
        }

        wallRebarMap[12] = wallSteelKg * 0.45
        wallRebarMap[10] = wallSteelKg * 0.40
        wallRebarMap[8] = wallSteelKg * 0.15

        items.add(
            BoqItem(
                itemNumber = "05.01",
                designation = "Béton armé pour Voiles de contreventement parasismiques (RPA 2024 § 7.7)",
                unit = "m³",
                concreteVolumeM3 = wallConcVol,
                formworkAreaM2 = wallFormArea,
                rebarWeightKg = wallSteelKg,
                steelRatioKgPerM3 = 105.0,
                unitPriceDzd = 19000.0,
                quantity = wallConcVol,
                rebarBreakdownByDiameterKg = wallRebarMap
            )
        )

        // 6. PLANCHERS À CORPS CREUX (16+5 CM)
        // Volume béton coulé en place : nervures + table de compression 5 cm (~0.085 m³/m²)
        val slabConcVol = totalFloorAreaM2 * 0.085
        val slabFormArea = totalFloorAreaM2
        val slabSteelRatio = 14.0 // kg/m² de plancher
        val slabSteelKg = totalFloorAreaM2 * slabSteelRatio
        val slabRebarMap = mapOf(
            12 to slabSteelKg * 0.60, // Aciers de nervures (3T12)
            6 to slabSteelKg * 0.40  // Treillis soudé TS 150x150 diam 4/5
        )

        items.add(
            BoqItem(
                itemNumber = "06.01",
                designation = "Plancher à corps creux (16+5 cm) complet fourni et posé, y compris poutrelles et TS",
                unit = "m²",
                concreteVolumeM3 = slabConcVol,
                formworkAreaM2 = slabFormArea,
                rebarWeightKg = slabSteelKg,
                steelRatioKgPerM3 = slabSteelKg / slabConcVol,
                unitPriceDzd = 3400.0,
                quantity = totalFloorAreaM2,
                rebarBreakdownByDiameterKg = slabRebarMap
            )
        )

        // 7. ÉLÉMENTS SECONDAIRES (ACROTÈRE & ESCALIERS)
        // Acrotère
        val acroVol = perimeterRoofM * (0.10 * 0.60)
        val acroForm = perimeterRoofM * (2.0 * 0.60)
        val acroSteelKg = acroVol * 80.0

        // Escaliers (1 volée par étage)
        val stairVol = nStories * (2.0 * (1.20 * 0.15 * 3.50))
        val stairForm = nStories * (1.20 * 3.50 * 2.0)
        val stairSteelKg = stairVol * 100.0

        val secConcVol = acroVol + stairVol
        val secFormArea = acroForm + stairForm
        val secSteelKg = acroSteelKg + stairSteelKg
        val secRebarMap = mapOf(
            12 to secSteelKg * 0.50,
            10 to secSteelKg * 0.30,
            8 to secSteelKg * 0.20
        )

        items.add(
            BoqItem(
                itemNumber = "07.01",
                designation = "Béton armé pour Éléments secondaires (Acrotère toiture & Escaliers coulés en place)",
                unit = "m³",
                concreteVolumeM3 = secConcVol,
                formworkAreaM2 = secFormArea,
                rebarWeightKg = secSteelKg,
                steelRatioKgPerM3 = secSteelKg / secConcVol,
                unitPriceDzd = 21000.0,
                quantity = secConcVol,
                rebarBreakdownByDiameterKg = secRebarMap
            )
        )

        // 8. FOURNITURE, FAÇONNAGE ET POSE DES ACIERS HAUTE ADHÉRENCE (BORDEREAU DÉTAILLÉ)
        val totalStructuralConcM3 = footingConcVol + colConcVol + beamConcVol + wallConcVol + slabConcVol + secConcVol
        val totalFormM2 = footingFormArea + colFormArea + beamFormArea + wallFormArea + slabFormArea + secFormArea
        val totalSteelKg = footingSteelKg + colSteelKg + beamSteelKg + wallSteelKg + slabSteelKg + secSteelKg
        val globalRatio = if (totalStructuralConcM3 > 0) totalSteelKg / totalStructuralConcM3 else 115.0

        // Agrégation par diamètre
        val totalRebarByDiam = mutableMapOf<Int, Double>()
        listOf(footingRebarMap, colRebarMap, beamRebarMap, wallRebarMap, slabRebarMap, secRebarMap).forEach { map ->
            map.forEach { (d, w) ->
                totalRebarByDiam[d] = (totalRebarByDiam[d] ?: 0.0) + w
            }
        }

        val totalCostDzd = items.sumOf { it.totalPriceDzd }

        return BoqSummaryReport(
            jointName = joint.jointName,
            projectTitle = projectTitle,
            wilaya = wilaya,
            items = items,
            totalLeanConcreteM3 = leanConcVol,
            totalStructuralConcreteM3 = totalStructuralConcM3,
            totalFormworkAreaM2 = totalFormM2,
            totalRebarWeightKg = totalSteelKg,
            globalSteelRatioKgPerM3 = globalRatio,
            totalDevelopedFloorAreaM2 = totalFloorAreaM2,
            steelRatioPerM2Floor = totalSteelKg / totalFloorAreaM2,
            concreteRatioPerM2Floor = totalStructuralConcM3 / totalFloorAreaM2,
            totalEstimatedCostDzd = totalCostDzd,
            rebarTotalByDiameterKg = totalRebarByDiam
        )
    }

    /**
     * Exporte le Devis Quantitatif et Estimatif (DQE) au format Microsoft Excel (.xlsx) natif via Apache POI.
     */
    fun exportToExcel(report: BoqSummaryReport, outputFile: File) {
        val workbook = XSSFWorkbook()

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
                fontHeightInPoints = 11
            }
            setFont(font)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00 \"DZD\"")
            alignment = HorizontalAlignment.RIGHT
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        val numberStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
            alignment = HorizontalAlignment.RIGHT
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        val textStyle = workbook.createCellStyle().apply {
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        val boldSummaryStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 11
            }
            setFont(font)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00 \"DZD\"")
            alignment = HorizontalAlignment.RIGHT
            borderBottom = BorderStyle.MEDIUM
            borderTop = BorderStyle.MEDIUM
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        // ---------------- FEUILLE 1 : DEVIS ESTIMATIF (DQE) ----------------
        val sheet1 = workbook.createSheet("DEVIS ESTIMATIF DQE")
        sheet1.isDisplayGridlines = true

        // Titre du document
        val titleRow = sheet1.createRow(1)
        val titleCell = titleRow.createCell(1)
        titleCell.setCellValue("DEVIS QUANTITATIF & ESTIMATIF (DQE) - GROS-ŒUVRE RPA 2024")
        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
            color = IndexedColors.DARK_BLUE.index
        }
        val titleStyle = workbook.createCellStyle().apply { setFont(titleFont) }
        titleCell.cellStyle = titleStyle

        val subRow = sheet1.createRow(2)
        subRow.createCell(1).setCellValue("Projet : ${report.projectTitle}  |  Joint : ${report.jointName}  |  Wilaya : ${report.wilaya}")

        // En-têtes du tableau
        val headerRow = sheet1.createRow(4)
        val headers = listOf("N° Poste", "Désignation des Travaux", "Unité", "Quantité", "P.U. Estimatif (DZD)", "Montant Total HT (DZD)")
        for (i in headers.indices) {
            val cell = headerRow.createCell(i + 1)
            cell.setCellValue(headers[i])
            cell.cellStyle = headerStyle
        }

        var rowIndex = 5
        for (item in report.items) {
            val row = sheet1.createRow(rowIndex++)
            val c0 = row.createCell(1).apply { setCellValue(item.itemNumber); cellStyle = textStyle }
            val c1 = row.createCell(2).apply { setCellValue(item.designation); cellStyle = textStyle }
            val c2 = row.createCell(3).apply { setCellValue(item.unit); cellStyle = textStyle }
            val c3 = row.createCell(4).apply { setCellValue(round(item.quantity * 100) / 100); cellStyle = numberStyle }
            val c4 = row.createCell(5).apply { setCellValue(item.unitPriceDzd); cellStyle = currencyStyle }
            val c5 = row.createCell(6).apply { setCellValue(round(item.totalPriceDzd * 100) / 100); cellStyle = currencyStyle }
        }

        // Ligne Total
        val totalRow = sheet1.createRow(rowIndex)
        val lblCell = totalRow.createCell(2).apply {
            setCellValue("TOTAL GÉNÉRAL ESTIMATIF GROS-ŒUVRE (HORS TAXES)")
            val f = workbook.createFont().apply { bold = true }
            cellStyle = workbook.createCellStyle().apply { setFont(f); borderTop = BorderStyle.MEDIUM; borderBottom = BorderStyle.MEDIUM }
        }
        val sumCell = totalRow.createCell(6).apply {
            setCellValue(report.totalEstimatedCostDzd)
            cellStyle = boldSummaryStyle
        }

        for (c in 1..6) {
            sheet1.autoSizeColumn(c)
        }

        // ---------------- FEUILLE 2 : MÉTRÉ MATÉRIAUX & RATIOS ----------------
        val sheet2 = workbook.createSheet("MÉTRÉ & RATIOS MATÉRIAUX")
        sheet2.isDisplayGridlines = true

        val s2TitleRow = sheet2.createRow(1)
        s2TitleRow.createCell(1).setCellValue("RÉCAPITULATIF QUANTITATIF DU BÉTON, COFFRAGE ET ACIER HA")
        s2TitleRow.getCell(1).cellStyle = titleStyle

        val s2HeaderRow = sheet2.createRow(3)
        val s2Headers = listOf("Composant Structurel", "Béton (m³)", "Coffrage (m²)", "Acier HA (kg)", "Ratio Acier (kg/m³)")
        for (i in s2Headers.indices) {
            val cell = s2HeaderRow.createCell(i + 1)
            cell.setCellValue(s2Headers[i])
            cell.cellStyle = headerStyle
        }

        var r2 = 4
        for (item in report.items) {
            if (item.concreteVolumeM3 > 0 || item.rebarWeightKg > 0) {
                val row = sheet2.createRow(r2++)
                row.createCell(1).apply { setCellValue(item.designation); cellStyle = textStyle }
                row.createCell(2).apply { setCellValue(round(item.concreteVolumeM3 * 100) / 100); cellStyle = numberStyle }
                row.createCell(3).apply { setCellValue(round(item.formworkAreaM2 * 100) / 100); cellStyle = numberStyle }
                row.createCell(4).apply { setCellValue(round(item.rebarWeightKg * 10) / 10); cellStyle = numberStyle }
                row.createCell(5).apply { setCellValue(round(item.steelRatioKgPerM3 * 10) / 10); cellStyle = numberStyle }
            }
        }

        // Total
        val sumRow2 = sheet2.createRow(r2)
        sumRow2.createCell(1).apply {
            setCellValue("TOTAUX GÉNÉRAUX")
            val f = workbook.createFont().apply { bold = true }
            cellStyle = workbook.createCellStyle().apply { setFont(f); borderTop = BorderStyle.MEDIUM; borderBottom = BorderStyle.MEDIUM }
        }
        sumRow2.createCell(2).apply { setCellValue(round(report.totalStructuralConcreteM3 * 100) / 100); cellStyle = boldSummaryStyle }
        sumRow2.createCell(3).apply { setCellValue(round(report.totalFormworkAreaM2 * 100) / 100); cellStyle = boldSummaryStyle }
        sumRow2.createCell(4).apply { setCellValue(round(report.totalRebarWeightKg * 10) / 10); cellStyle = boldSummaryStyle }
        sumRow2.createCell(5).apply { setCellValue(round(report.globalSteelRatioKgPerM3 * 10) / 10); cellStyle = boldSummaryStyle }

        // Tableau de ventilation des aciers par diamètre
        r2 += 3
        val diamHeaderRow = sheet2.createRow(r2++)
        diamHeaderRow.createCell(1).setCellValue("VENTILATION DES ACIERS HAUTE ADHÉRENCE PAR DIAMÈTRE (kg)")
        diamHeaderRow.getCell(1).cellStyle = titleStyle

        val diamSubRow = sheet2.createRow(r2++)
        diamSubRow.createCell(1).apply { setCellValue("Diamètre Nominal"); cellStyle = headerStyle }
        diamSubRow.createCell(2).apply { setCellValue("Poids Total (kg)"); cellStyle = headerStyle }
        diamSubRow.createCell(3).apply { setCellValue("Pourcentage (%)"); cellStyle = headerStyle }

        for ((diam, weight) in report.rebarTotalByDiameterKg.toSortedMap()) {
            val row = sheet2.createRow(r2++)
            val pct = if (report.totalRebarWeightKg > 0) (weight / report.totalRebarWeightKg) * 100.0 else 0.0
            row.createCell(1).apply { setCellValue("HA $diam"); cellStyle = textStyle }
            row.createCell(2).apply { setCellValue(round(weight * 10) / 10); cellStyle = numberStyle }
            row.createCell(3).apply { setCellValue(String.format(Locale.US, "%.1f %%", pct)); cellStyle = textStyle }
        }

        for (c in 1..5) {
            sheet2.autoSizeColumn(c)
        }

        FileOutputStream(outputFile).use { fos ->
            workbook.write(fos)
        }
        workbook.close()
    }
}
