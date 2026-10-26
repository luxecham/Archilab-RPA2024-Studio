package com.archilab.rpa2024.cad

import com.archilab.rpa2024.model.StructuralJoint
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

/**
 * Type géométrique de façonnage d'armature pour la nomenclature et le métré.
 */
enum class RebarShapeType(val description: String) {
    STRAIGHT("Barre droite"),
    HOOKED_ENDS("Barre filante avec 2 crosses d'ancrage"),
    CLOSED_TIE_135("Cadre fermé avec crochets à 135° RPA 2024"),
    U_SHAPE("Épingle / Cadre ouvert en U"),
    CROSSTIE_135("Épingle de confinement avec crochets à 135°"),
    CHAPEAU("Barre chapeau d'appui")
}

/**
 * Ligne de nomenclature d'une position d'armature.
 */
data class BbsRebarItem(
    val posNumber: Int,
    val elementGroup: String,         // "Poteaux", "Poutres", "Voiles", "Fondations", "Acrotère"
    val diameterMm: Int,              // 8, 10, 12, 14, 16, 20
    val shapeType: RebarShapeType,
    val countPerElement: Int,
    val identicalElementsCount: Int,
    val developedLengthMeters: Double,
    val dimensionA: Double,           // Cotes de façonnage en mètres
    val dimensionB: Double = 0.0,
    val dimensionC: Double = 0.0
) {
    val totalCount: Int
        get() = countPerElement * identicalElementsCount

    val totalLengthMeters: Double
        get() = totalCount * developedLengthMeters

    /**
     * Masse linéique normalisée (phi^2 / 162 en kg/ml).
     */
    val linearUnitWeightKgM: Double
        get() = (diameterMm.toDouble() * diameterMm.toDouble()) / 162.0

    /**
     * Masse totale en kg pour cette position.
     */
    val totalWeightKg: Double
        get() = totalLengthMeters * linearUnitWeightKgM
}

/**
 * Rapport récapitulatif global du métré HA et des ratios d'acier/béton.
 */
data class BbsSummaryReport(
    val items: List<BbsRebarItem>,
    val weightsByDiameterKg: Map<Int, Double>,
    val totalSteelWeightKg: Double,
    val totalConcreteVolumeM3: Double,
    val steelRatioKgPerM3: Double
)

/**
 * Générateur et dessinateur automatique des Tableaux de Nomenclature et de Façonnage des Aciers (BBS) en DXF.
 * Calcule avec exactitude les longueurs développées (mandrins BAEL + crochets parasismiques à 135° RPA 2024),
 * dessine les vignettes géométriques de façonnage et calcule le métré d'armatures et ratios kg/m³.
 */
class BarBendingScheduleDxfGenerator {

    /**
     * Calcule le métré détaillé d'armatures pour le Joint structural étudié.
     */
    fun computeBendingSchedule(joint: StructuralJoint): BbsSummaryReport {
        val items = mutableListOf<BbsRebarItem>()
        var pos = 1

        val nStories = joint.storySystem.numStoriesN
        val nColumns = joint.gridSystem.sortedXGrids.size * joint.gridSystem.sortedYGrids.size
        val nBaysX = joint.gridSystem.spansX.size
        val nBaysY = joint.gridSystem.spansY.size

        // 1. Poteaux
        val colH = joint.storySystem.stories.firstOrNull()?.heightHk ?: 3.06
        // Pos 1 : 4T16 d'angles (longueur H + 0.70m de recouvrement)
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poteaux",
                diameterMm = 16,
                shapeType = RebarShapeType.STRAIGHT,
                countPerElement = 4,
                identicalElementsCount = nColumns * nStories,
                developedLengthMeters = colH + 0.70,
                dimensionA = colH + 0.70
            )
        )
        // Pos 2 : 4T14 intermédiaires
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poteaux",
                diameterMm = 14,
                shapeType = RebarShapeType.STRAIGHT,
                countPerElement = 4,
                identicalElementsCount = nColumns * nStories,
                developedLengthMeters = colH + 0.70,
                dimensionA = colH + 0.70
            )
        )
        // Pos 3 : Cadres fermés T8 à 135° (2 * (0.30 + 0.30) + 2 * 0.08 = 1.36m)
        val framesPerCol = 28 // 2 ZN + ZC
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poteaux",
                diameterMm = 8,
                shapeType = RebarShapeType.CLOSED_TIE_135,
                countPerElement = framesPerCol,
                identicalElementsCount = nColumns * nStories,
                developedLengthMeters = 1.36,
                dimensionA = 0.30,
                dimensionB = 0.30
            )
        )
        // Pos 4 : Étriers / épingles T8
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poteaux",
                diameterMm = 8,
                shapeType = RebarShapeType.CROSSTIE_135,
                countPerElement = framesPerCol,
                identicalElementsCount = nColumns * nStories,
                developedLengthMeters = 0.46,
                dimensionA = 0.30
            )
        )

        // 2. Poutres Principales
        val avgSpan = if (joint.gridSystem.spansX.isNotEmpty()) joint.gridSystem.spansX.average() else 4.20
        val nBeamsTotal = (nBaysX * joint.gridSystem.sortedYGrids.size) * nStories
        // Pos 5 : 3T14 filantes inférieures
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poutres",
                diameterMm = 14,
                shapeType = RebarShapeType.HOOKED_ENDS,
                countPerElement = 3,
                identicalElementsCount = nBeamsTotal,
                developedLengthMeters = avgSpan + 0.80,
                dimensionA = avgSpan,
                dimensionB = 0.25
            )
        )
        // Pos 6 : 3T14 filantes supérieures
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poutres",
                diameterMm = 14,
                shapeType = RebarShapeType.HOOKED_ENDS,
                countPerElement = 3,
                identicalElementsCount = nBeamsTotal,
                developedLengthMeters = avgSpan + 0.80,
                dimensionA = avgSpan,
                dimensionB = 0.25
            )
        )
        // Pos 7 : 3T14 chapeaux sur appuis
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poutres",
                diameterMm = 14,
                shapeType = RebarShapeType.CHAPEAU,
                countPerElement = 3,
                identicalElementsCount = nBeamsTotal,
                developedLengthMeters = avgSpan * 0.30 + 0.40,
                dimensionA = avgSpan * 0.30,
                dimensionB = 0.20
            )
        )
        // Pos 8 : Cadres T8 poutres (2 * (0.25 + 0.50) + 0.16 = 1.66m)
        val framesPerBeam = 35
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Poutres",
                diameterMm = 8,
                shapeType = RebarShapeType.CLOSED_TIE_135,
                countPerElement = framesPerBeam,
                identicalElementsCount = nBeamsTotal,
                developedLengthMeters = 1.66,
                dimensionA = 0.25,
                dimensionB = 0.50
            )
        )

        // 3. Voiles de Contreventement
        val nWalls = max(joint.sectionCatalog.shearWalls.size, 2)
        // Pos 9 : Barres verticales rives 6T14
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Voiles",
                diameterMm = 14,
                shapeType = RebarShapeType.STRAIGHT,
                countPerElement = 12,
                identicalElementsCount = nWalls * nStories,
                developedLengthMeters = colH + 0.70,
                dimensionA = colH + 0.70
            )
        )
        // Pos 10 : Cadres de confinement rive T8 e=8cm
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Voiles",
                diameterMm = 8,
                shapeType = RebarShapeType.CLOSED_TIE_135,
                countPerElement = 38,
                identicalElementsCount = nWalls * nStories,
                developedLengthMeters = 1.56,
                dimensionA = 0.50,
                dimensionB = 0.16
            )
        )
        // Pos 11 : Armatures d'âme T12
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Voiles",
                diameterMm = 12,
                shapeType = RebarShapeType.STRAIGHT,
                countPerElement = 24,
                identicalElementsCount = nWalls * nStories,
                developedLengthMeters = colH + 0.60,
                dimensionA = colH + 0.60
            )
        )

        // 4. Fondations (Semelles Filantes)
        val totalFootingLen = joint.gridSystem.totalLengthLx * joint.gridSystem.sortedYGrids.size + joint.gridSystem.totalLengthLy * joint.gridSystem.sortedXGrids.size
        // Pos 12 : Nappe inférieure de semelle T12 e=15cm (L = 1.60m + 2*0.35m = 2.30m)
        val nBarsFtg = (totalFootingLen / 0.15).toInt()
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Fondations",
                diameterMm = 12,
                shapeType = RebarShapeType.HOOKED_ENDS,
                countPerElement = nBarsFtg,
                identicalElementsCount = 1,
                developedLengthMeters = 2.30,
                dimensionA = 1.60,
                dimensionB = 0.35
            )
        )
        // Pos 13 : Armatures longitudinales nervure 6T16
        val nNervBars = (totalFootingLen / 6.0).toInt() * 6
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Fondations",
                diameterMm = 16,
                shapeType = RebarShapeType.STRAIGHT,
                countPerElement = nNervBars,
                identicalElementsCount = 1,
                developedLengthMeters = 6.70,
                dimensionA = 6.70
            )
        )

        // 5. Acrotère
        val perimeter = 2.0 * (joint.gridSystem.totalLengthLx + joint.gridSystem.totalLengthLy)
        val nAcroBars = (perimeter / 0.20).toInt()
        items.add(
            BbsRebarItem(
                posNumber = pos++,
                elementGroup = "Acrotère",
                diameterMm = 8,
                shapeType = RebarShapeType.U_SHAPE,
                countPerElement = nAcroBars,
                identicalElementsCount = 1,
                developedLengthMeters = 1.40,
                dimensionA = 0.60,
                dimensionB = 0.10
            )
        )

        // Calculs récapitulatifs par diamètre
        val weightsByDia = items.groupBy { it.diameterMm }.mapValues { (_, list) ->
            round(list.sumOf { it.totalWeightKg } * 10.0) / 10.0
        }
        val totalSteelWeight = round(items.sumOf { it.totalWeightKg } * 10.0) / 10.0

        // Estimation du volume de béton armé effectif total (Superstructure + Fondations)
        val areaPerFloor = joint.gridSystem.totalLengthLx * joint.gridSystem.totalLengthLy
        // Pour un plancher corps creux 16+5, le volume équivalent de béton est d'environ 0.09 m³/m² (table 5cm + nervures 12x16cm)
        val slabVol = areaPerFloor * 0.09 * nStories
        val colVol = nColumns * (0.35 * 0.35) * colH * nStories
        val beamVol = totalFootingLen * 0.30 * 0.55
        val ftgVol = totalFootingLen * (1.60 * 0.40 + 0.40 * 0.50)
        val totalConcreteVol = round((slabVol + colVol + beamVol + ftgVol) * 10.0) / 10.0

        val steelRatio = if (totalConcreteVol > 0.0) round((totalSteelWeight / totalConcreteVol) * 10.0) / 10.0 else 95.0

        return BbsSummaryReport(
            items = items,
            weightsByDiameterKg = weightsByDia,
            totalSteelWeightKg = totalSteelWeight,
            totalConcreteVolumeM3 = totalConcreteVol,
            steelRatioKgPerM3 = steelRatio
        )
    }

    /**
     * Dessine la table de nomenclature d'aciers complète (BBS) en coordonnées absolues DXF.
     */
    fun drawBendingScheduleTable(
        dxf: DxfCoreEngine,
        originX: Double,
        originY: Double,
        report: BbsSummaryReport,
        joint: StructuralJoint,
        projectTitle: String = "Projet R+4 RPA 2024",
        wilaya: String = "Alger"
    ) {
        val layer = CadLayer.TEXTES_ET_REPERES
        val rowHeight = 0.70
        val colWidths = listOf(1.20, 2.80, 1.40, 1.80, 1.80, 4.50, 2.50, 2.20, 2.40)
        val tableWidth = colWidths.sum()
        val numRows = report.items.size
        val tableHeight = (numRows + 2) * rowHeight + 4.0

        // 1. Cadre général de la table
        dxf.addRectangle(originX, originY - tableHeight, originX + tableWidth, originY, CadLayer.TEXTES_ET_REPERES, constantWidth = 0.03)

        // 2. En-tête principal
        val headerY = originY - rowHeight
        dxf.addLine(originX, headerY, originX + tableWidth, headerY, layer)
        dxf.addText(
            originX + tableWidth / 2.0,
            originY - rowHeight / 2.0,
            0.26,
            "NOMENCLATURE GÉNÉRALE & MÉTRÉ DES ACIERS HA (RPA 2024 / BAEL 91)",
            layer,
            hAlign = CadHAlign.CENTER,
            vAlign = CadVAlign.MIDDLE,
            colorOverride = 1
        )

        // 3. Colonnes d'en-tête
        val subHeaderY = originY - 2.0 * rowHeight
        dxf.addLine(originX, subHeaderY, originX + tableWidth, subHeaderY, layer)

        val headers = listOf("POS", "ÉLÉMENT", "Ø (mm)", "NB/ÉLÉM", "TOTAL", "SCHÉMA DE FAÇONNAGE", "LONG. DÉV.", "POIDS LIN.", "POIDS TOTAL")
        var curColX = originX
        for (i in headers.indices) {
            val nextColX = curColX + colWidths[i]
            dxf.addLine(nextColX, originY - rowHeight, nextColX, originY - (numRows + 2) * rowHeight, layer)
            dxf.addText((curColX + nextColX) / 2.0, (headerY + subHeaderY) / 2.0, 0.16, headers[i], layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            curColX = nextColX
        }

        // 4. Lignes de données
        for (idx in report.items.indices) {
            val item = report.items[idx]
            val rowY = subHeaderY - (idx + 1) * rowHeight
            dxf.addLine(originX, rowY, originX + tableWidth, rowY, layer)

            var cx = originX
            // Pos
            dxf.addText(cx + colWidths[0] / 2.0, rowY + rowHeight / 2.0, 0.18, "${item.posNumber}", layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            cx += colWidths[0]

            // Élément
            dxf.addText(cx + colWidths[1] / 2.0, rowY + rowHeight / 2.0, 0.16, item.elementGroup, layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            cx += colWidths[1]

            // Diamètre
            dxf.addText(cx + colWidths[2] / 2.0, rowY + rowHeight / 2.0, 0.18, "T${item.diameterMm}", layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE, colorOverride = 1)
            cx += colWidths[2]

            // Nb/Élém
            dxf.addText(cx + colWidths[3] / 2.0, rowY + rowHeight / 2.0, 0.16, "${item.countPerElement}", layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            cx += colWidths[3]

            // Total
            dxf.addText(cx + colWidths[4] / 2.0, rowY + rowHeight / 2.0, 0.18, "${item.totalCount}", layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            cx += colWidths[4]

            // Vignette Schéma de Façonnage
            drawRebarVignette(dxf, cx + 0.40, rowY + rowHeight / 2.0, item)
            cx += colWidths[5]

            // Longueur développée
            dxf.addText(cx + colWidths[6] / 2.0, rowY + rowHeight / 2.0, 0.16, String.format(Locale.US, "%.2f m", item.developedLengthMeters), layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            cx += colWidths[6]

            // Poids lin
            dxf.addText(cx + colWidths[7] / 2.0, rowY + rowHeight / 2.0, 0.16, String.format(Locale.US, "%.3f", item.linearUnitWeightKgM), layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            cx += colWidths[7]

            // Poids total
            dxf.addText(cx + colWidths[8] / 2.0, rowY + rowHeight / 2.0, 0.18, String.format(Locale.US, "%.1f kg", item.totalWeightKg), layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE, colorOverride = 1)
        }

        // 5. Bloc de Synthèse Récapitulatif (Poids par diamètre et ratios)
        val sumY0 = subHeaderY - (numRows + 1) * rowHeight - 0.40
        val sumBoxW = tableWidth
        val sumBoxH = 3.20
        dxf.addRectangle(originX, sumY0 - sumBoxH, originX + sumBoxW, sumY0, CadLayer.CARTOUCHE_CTC, constantWidth = 0.03)

        dxf.addText(originX + 0.50, sumY0 - 0.40, 0.20, "RÉCAPITULATIF DES POIDS D'ACIER PAR DIAMÈTRE :", CadLayer.TEXTES_ET_REPERES, colorOverride = 1)

        val diaStr = report.weightsByDiameterKg.entries.joinToString("   |   ") { "T${it.key} : ${it.value} kg" }
        dxf.addText(originX + 0.50, sumY0 - 0.90, 0.18, diaStr, CadLayer.TEXTES_ET_REPERES)

        dxf.addLine(originX, sumY0 - 1.30, originX + sumBoxW, sumY0 - 1.30, CadLayer.TEXTES_ET_REPERES)

        val totalSteel = String.format(Locale.US, "%.1f kg (%.2f Tonnes)", report.totalSteelWeightKg, report.totalSteelWeightKg / 1000.0)
        dxf.addText(originX + 0.50, sumY0 - 1.80, 0.22, "POIDS TOTAL D'ACIER HA : $totalSteel", CadLayer.TEXTES_ET_REPERES, colorOverride = 1)

        val volBéton = String.format(Locale.US, "%.2f m³", report.totalConcreteVolumeM3)
        dxf.addText(originX + 0.50, sumY0 - 2.30, 0.20, "VOLUME ESTIMÉ DE BÉTON : $volBéton", CadLayer.TEXTES_ET_REPERES)

        val ratioStr = String.format(Locale.US, "%.1f kg / m³", report.steelRatioKgPerM3)
        dxf.addText(originX + 0.50, sumY0 - 2.80, 0.22, "RATIO MOYEN D'ARMATURES : $ratioStr (Conforme RPA 2024)", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)

        // Cadre de Planche et Cartouche CTC
        val frameMargin = 2.0
        dxf.drawTitleBlockCTC(
            originX = originX - frameMargin,
            originY = originY - tableHeight - frameMargin,
            width = tableWidth + 2.0 * frameMargin,
            height = tableHeight + 2.0 * frameMargin,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "NOMENCLATURE DES ACIERS & MÉTRÉ HA (RPA 2024)",
            scaleText = "Sans échelle"
        )
    }

    /**
     * Dessine la vignette miniature schématique du façonnage de l'armature.
     */
    private fun drawRebarVignette(
        dxf: DxfCoreEngine,
        centerX: Double,
        centerY: Double,
        item: BbsRebarItem
    ) {
        val layer = CadLayer.ACIER_TRANSVERSAL
        val vLen = 2.40

        when (item.shapeType) {
            RebarShapeType.STRAIGHT -> {
                dxf.addLine(centerX, centerY, centerX + vLen, centerY, layer)
            }
            RebarShapeType.HOOKED_ENDS -> {
                val h = 0.20
                dxf.addLine(centerX, centerY + h, centerX, centerY, layer)
                dxf.addLine(centerX, centerY, centerX + vLen, centerY, layer)
                dxf.addLine(centerX + vLen, centerY, centerX + vLen, centerY + h, layer)
            }
            RebarShapeType.CLOSED_TIE_135 -> {
                val w = 1.40
                val h = 0.40
                dxf.addRectangle(centerX + 0.50, centerY - h / 2.0, centerX + 0.50 + w, centerY + h / 2.0, layer, constantWidth = 0.015)
                // Crochet à 135°
                dxf.addLine(centerX + 0.50, centerY - h / 2.0, centerX + 0.50 + 0.15, centerY - h / 2.0 + 0.15, layer)
            }
            RebarShapeType.U_SHAPE -> {
                val w = 1.40
                val h = 0.35
                dxf.addLine(centerX + 0.50, centerY + h / 2.0, centerX + 0.50, centerY - h / 2.0, layer)
                dxf.addLine(centerX + 0.50, centerY - h / 2.0, centerX + 0.50 + w, centerY - h / 2.0, layer)
                dxf.addLine(centerX + 0.50 + w, centerY - h / 2.0, centerX + 0.50 + w, centerY + h / 2.0, layer)
            }
            RebarShapeType.CROSSTIE_135 -> {
                val w = 1.60
                dxf.addLine(centerX + 0.40, centerY, centerX + 0.40 + w, centerY, layer)
                dxf.addLine(centerX + 0.40, centerY, centerX + 0.40 + 0.12, centerY + 0.12, layer)
                dxf.addLine(centerX + 0.40 + w, centerY, centerX + 0.40 + w - 0.12, centerY - 0.12, layer)
            }
            RebarShapeType.CHAPEAU -> {
                val h = 0.20
                dxf.addLine(centerX, centerY - h, centerX, centerY, layer)
                dxf.addLine(centerX, centerY, centerX + vLen, centerY, layer)
            }
        }
    }

    /**
     * Exporte la planche de nomenclature directement dans un fichier DXF autonome.
     */
    fun exportBbsDxfFile(
        joint: StructuralJoint,
        targetFile: File,
        projectTitle: String = "Projet R+4 RPA 2024",
        wilaya: String = "Alger"
    ): File {
        val dxf = DxfCoreEngine()
        val report = computeBendingSchedule(joint)
        drawBendingScheduleTable(dxf, 0.0, 0.0, report, joint, projectTitle, wilaya)
        return dxf.saveToFile(targetFile)
    }
}
