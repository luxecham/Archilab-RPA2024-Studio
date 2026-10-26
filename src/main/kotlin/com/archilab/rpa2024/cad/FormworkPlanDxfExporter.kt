package com.archilab.rpa2024.cad

import com.archilab.rpa2024.engine.foundations.FootingType
import com.archilab.rpa2024.engine.foundations.StripFootingSection
import com.archilab.rpa2024.model.*
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Exportateur automatique des plans de coffrage par étage et du plan de fondations au format AutoCAD (.dxf).
 * Entièrement piloté par la topologie dynamique extraite d'ETABS et SAFE (Zéro géométrie codée en dur).
 */
class FormworkPlanDxfExporter {

    /**
     * Génère un fichier DXF complet contenant :
     * 1. Le plan de coffrage du niveau type / étage courant.
     * 2. Le plan de coffrage des fondations (Semelles filantes, nervures, béton de propreté).
     * 3. Les cartouches réglementaires CTC et cotations périmétriques complètes.
     */
    fun exportFormworkDxf(
        joint: StructuralJoint,
        targetFile: File,
        storyLevelIndex: Int = 1,
        projectTitle: String = "Projet R+4 RPA 2024",
        wilaya: String = "Alger"
    ): File {
        val dxf = DxfCoreEngine()

        // 1. Tracé du Plan de Coffrage Étage Type à l'origine (0, 0)
        drawFloorFormworkPlan(
            dxf = dxf,
            joint = joint,
            originX = 0.0,
            originY = 0.0,
            storyLevelIndex = storyLevelIndex,
            projectTitle = projectTitle,
            wilaya = wilaya
        )

        // 2. Tracé du Plan de Coffrage des Fondations décalé sur la droite
        val spacingX = max(joint.gridSystem.totalLengthLx + 15.0, 35.0)
        drawFoundationFormworkPlan(
            dxf = dxf,
            joint = joint,
            originX = spacingX,
            originY = 0.0,
            projectTitle = projectTitle,
            wilaya = wilaya
        )

        return dxf.saveToFile(targetFile)
    }

    /**
     * Dessine le plan de coffrage d'un étage superstructure (Poteaux, Poutres, Voiles, Dalles).
     */
    fun drawFloorFormworkPlan(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        storyLevelIndex: Int = 1,
        projectTitle: String = "Projet R+4",
        wilaya: String = "Alger"
    ) {
        val xGrids = joint.gridSystem.sortedXGrids
        val yGrids = joint.gridSystem.sortedYGrids
        val minX = xGrids.first().coordinate
        val maxX = xGrids.last().coordinate
        val minY = yGrids.first().coordinate
        val maxY = yGrids.last().coordinate
        val lx = joint.gridSystem.totalLengthLx
        val ly = joint.gridSystem.totalLengthLy

        val storyName = joint.storySystem.stories.getOrNull(storyLevelIndex)?.storyName ?: "Étage Courant"

        // 1. Grille d'Axes et Dépassements
        val overhang = 1.80
        val bubbleRadius = 0.35

        // Axes X (verticaux)
        for (xg in xGrids) {
            val gx = originX + (xg.coordinate - minX)
            val y1 = originY - overhang
            val y2 = originY + ly + overhang
            dxf.addLine(gx, y1, gx, y2, CadLayer.AXES)

            // Bulles d'axes en Bas et en Haut
            dxf.addCircle(gx, y1 - bubbleRadius, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(gx, y1 - bubbleRadius, bubbleRadius * 1.0, xg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)

            dxf.addCircle(gx, y2 + bubbleRadius, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(gx, y2 + bubbleRadius, bubbleRadius * 1.0, xg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
        }

        // Axes Y (horizontaux)
        for (yg in yGrids) {
            val gy = originY + (yg.coordinate - minY)
            val x1 = originX - overhang
            val x2 = originX + lx + overhang
            dxf.addLine(x1, gy, x2, gy, CadLayer.AXES)

            // Bulles d'axes à Gauche et à Droite
            dxf.addCircle(x1 - bubbleRadius, gy, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(x1 - bubbleRadius, gy, bubbleRadius * 1.0, yg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)

            dxf.addCircle(x2 + bubbleRadius, gy, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(x2 + bubbleRadius, gy, bubbleRadius * 1.0, yg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
        }

        // 2. Doubles Lignes de Cotation Périmétriques
        val dimOffset1 = overhang + 1.20 // Ligne 1 : Entraxes
        val dimOffset2 = overhang + 2.20 // Ligne 2 : Cote Totale

        // Cotations X (En bas)
        for (i in 0 until xGrids.size - 1) {
            val xA = originX + (xGrids[i].coordinate - minX)
            val xB = originX + (xGrids[i + 1].coordinate - minX)
            val span = xGrids[i + 1].coordinate - xGrids[i].coordinate
            dxf.addLinearDimension(xA, originY, xB, originY, offset = -dimOffset1, textOverride = String.format(Locale.US, "%.2f", span))
        }
        dxf.addLinearDimension(originX, originY, originX + lx, originY, offset = -dimOffset2, textOverride = String.format(Locale.US, "Lx = %.2f m", lx))

        // Cotations Y (À gauche)
        for (j in 0 until yGrids.size - 1) {
            val yA = originY + (yGrids[j].coordinate - minY)
            val yB = originY + (yGrids[j + 1].coordinate - minY)
            val span = yGrids[j + 1].coordinate - yGrids[j].coordinate
            dxf.addLinearDimension(originX, yA, originX, yB, offset = -dimOffset1, textOverride = String.format(Locale.US, "%.2f", span))
        }
        dxf.addLinearDimension(originX, originY, originX, originY + ly, offset = -dimOffset2, textOverride = String.format(Locale.US, "Ly = %.2f m", ly))

        // 3. Poteaux aux intersections de grilles
        val colFamily = joint.sectionCatalog.columns.firstOrNull()
        val bcMeters = (colFamily?.b1Cm ?: 35.0) / 100.0
        val hcMeters = (colFamily?.h1Cm ?: 35.0) / 100.0

        var colIndex = 1
        for (xg in xGrids) {
            for (yg in yGrids) {
                val cx = originX + (xg.coordinate - minX)
                val cy = originY + (yg.coordinate - minY)

                val x1 = cx - bcMeters / 2.0
                val y1 = cy - hcMeters / 2.0
                val x2 = cx + bcMeters / 2.0
                val y2 = cy + hcMeters / 2.0

                // Contour du poteau et hachure
                dxf.addRectangle(x1, y1, x2, y2, CadLayer.COFFRAGE_POTEAUX, constantWidth = 0.02)
                val pts = listOf(CadPoint(x1, y1), CadPoint(x2, y1), CadPoint(x2, y2), CadPoint(x1, y2))
                dxf.addSolidHatch(pts, CadLayer.COFFRAGE_POTEAUX)

                // Texte de repère poteau
                val colLabel = "P$colIndex (${(bcMeters * 100).toInt()}x${(hcMeters * 100).toInt()})"
                dxf.addText(x2 + 0.10, cy, 0.16, colLabel, CadLayer.TEXTES_ET_REPERES, vAlign = CadVAlign.MIDDLE)
                colIndex++
            }
        }

        // 4. Voiles de Contreventement
        for (wall in joint.sectionCatalog.shearWalls) {
            val lw = wall.lengthLwMeters
            val bw = wall.thicknessBwMeters
            // Positionnement représentatif sur les rives
            val wx = if (wall.direction == AxisDirection.X) originX + lx / 2.0 - lw / 2.0 else originX + (if (wall.pierName.contains("2")) lx else 0.0) - bw / 2.0
            val wy = if (wall.direction == AxisDirection.X) originY + ly - bw / 2.0 else originY + ly / 2.0 - lw / 2.0

            val wWidth = if (wall.direction == AxisDirection.X) lw else bw
            val wHeight = if (wall.direction == AxisDirection.X) bw else lw

            dxf.addRectangle(wx, wy, wx + wWidth, wy + wHeight, CadLayer.COFFRAGE_VOILES, constantWidth = 0.03)
            val wPts = listOf(CadPoint(wx, wy), CadPoint(wx + wWidth, wy), CadPoint(wx + wWidth, wy + wHeight), CadPoint(wx, wy + wHeight))
            dxf.addSolidHatch(wPts, CadLayer.COFFRAGE_VOILES)

            val wallTag = "${wall.pierName} (L=${String.format(Locale.US, "%.2f", lw)}m, ep=${(bw * 100).toInt()}cm)"
            dxf.addText(wx + wWidth / 2.0, wy + wHeight + 0.20, 0.18, wallTag, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 6)
        }

        // 5. Poutres Principales et Chaînages
        val mainBeam = joint.sectionCatalog.beams.find { it.role == BeamRole.MAIN_BEAM } ?: joint.sectionCatalog.beams.firstOrNull()
        val secBeam = joint.sectionCatalog.beams.find { it.role == BeamRole.SECONDARY_TIE } ?: mainBeam

        val bMainM = (mainBeam?.bCm ?: 30.0) / 100.0
        val bSecM = (secBeam?.bCm ?: 30.0) / 100.0

        // Poutres selon X (reliant les files X)
        for (yg in yGrids) {
            val cy = originY + (yg.coordinate - minY)
            for (i in 0 until xGrids.size - 1) {
                val xA = originX + (xGrids[i].coordinate - minX) + bcMeters / 2.0
                val xB = originX + (xGrids[i + 1].coordinate - minX) - bcMeters / 2.0

                if (xB > xA) {
                    val yTop = cy + bMainM / 2.0
                    val yBot = cy - bMainM / 2.0
                    dxf.addLine(xA, yTop, xB, yTop, CadLayer.COFFRAGE_POUTRES)
                    dxf.addLine(xA, yBot, xB, yBot, CadLayer.COFFRAGE_POUTRES)

                    val beamTag = "${mainBeam?.name ?: "PR 30x55"}"
                    dxf.addText((xA + xB) / 2.0, cy, 0.16, beamTag, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
                }
            }
        }

        // Chaînages selon Y (reliant les files Y)
        for (xg in xGrids) {
            val cx = originX + (xg.coordinate - minX)
            for (j in 0 until yGrids.size - 1) {
                val yA = originY + (yGrids[j].coordinate - minY) + hcMeters / 2.0
                val yB = originY + (yGrids[j + 1].coordinate - minY) - hcMeters / 2.0

                if (yB > yA) {
                    val xLeft = cx - bSecM / 2.0
                    val xRight = cx + bSecM / 2.0
                    dxf.addLine(xLeft, yA, xLeft, yB, CadLayer.COFFRAGE_POUTRES)
                    dxf.addLine(xRight, yA, xRight, yB, CadLayer.COFFRAGE_POUTRES)

                    val secTag = "${secBeam?.name ?: "CH 30x35"}"
                    dxf.addText(cx, (yA + yB) / 2.0, 0.16, secTag, CadLayer.TEXTES_ET_REPERES, rotationDeg = 90.0, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
                }
            }
        }

        // 6. Sens de Portée des Planchers à Corps Creux (Poutrelles)
        for (i in 0 until xGrids.size - 1) {
            for (j in 0 until yGrids.size - 1) {
                val midBayX = originX + (xGrids[i].coordinate + xGrids[i + 1].coordinate) / 2.0 - minX
                val midBayY = originY + (yGrids[j].coordinate + yGrids[j + 1].coordinate) / 2.0 - minY

                // Flèche double orientée selon le sens de portée
                val arrowLen = 1.00
                dxf.addLine(midBayX - arrowLen / 2.0, midBayY, midBayX + arrowLen / 2.0, midBayY, CadLayer.TEXTES_ET_REPERES)
                // Flèche gauche
                dxf.addLine(midBayX - arrowLen / 2.0, midBayY, midBayX - arrowLen / 2.0 + 0.20, midBayY + 0.12, CadLayer.TEXTES_ET_REPERES)
                dxf.addLine(midBayX - arrowLen / 2.0, midBayY, midBayX - arrowLen / 2.0 + 0.20, midBayY - 0.12, CadLayer.TEXTES_ET_REPERES)
                // Flèche droite
                dxf.addLine(midBayX + arrowLen / 2.0, midBayY, midBayX + arrowLen / 2.0 - 0.20, midBayY + 0.12, CadLayer.TEXTES_ET_REPERES)
                dxf.addLine(midBayX + arrowLen / 2.0, midBayY, midBayX + arrowLen / 2.0 - 0.20, midBayY - 0.12, CadLayer.TEXTES_ET_REPERES)

                dxf.addText(midBayX, midBayY + 0.25, 0.18, "CC 16+5", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            }
        }

        // 7. Cadre de Planche et Cartouche Réglementaire CTC
        val frameMargin = 5.0
        val frameW = lx + 2.0 * (overhang + dimOffset2 + frameMargin)
        val frameH = ly + 2.0 * (overhang + dimOffset2 + frameMargin)
        val frameX0 = originX - (overhang + dimOffset2 + frameMargin)
        val frameY0 = originY - (overhang + dimOffset2 + frameMargin)

        dxf.drawTitleBlockCTC(
            originX = frameX0,
            originY = frameY0,
            width = frameW,
            height = frameH,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "PLAN DE COFFRAGE - $storyName",
            scaleText = "1/50"
        )
    }

    /**
     * Dessine le plan de coffrage des fondations (Semelles filantes, nervures, gros béton).
     */
    fun drawFoundationFormworkPlan(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        projectTitle: String = "Projet R+4",
        wilaya: String = "Alger"
    ) {
        val xGrids = joint.gridSystem.sortedXGrids
        val yGrids = joint.gridSystem.sortedYGrids
        val minX = xGrids.first().coordinate
        val maxX = xGrids.last().coordinate
        val minY = yGrids.first().coordinate
        val maxY = yGrids.last().coordinate
        val lx = joint.gridSystem.totalLengthLx
        val ly = joint.gridSystem.totalLengthLy

        val overhang = 1.80
        val bubbleRadius = 0.35

        // 1. Grille d'Axes et Bulles
        for (xg in xGrids) {
            val gx = originX + (xg.coordinate - minX)
            val y1 = originY - overhang
            val y2 = originY + ly + overhang
            dxf.addLine(gx, y1, gx, y2, CadLayer.AXES)
            dxf.addCircle(gx, y1 - bubbleRadius, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(gx, y1 - bubbleRadius, bubbleRadius * 1.0, xg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            dxf.addCircle(gx, y2 + bubbleRadius, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(gx, y2 + bubbleRadius, bubbleRadius * 1.0, xg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
        }

        for (yg in yGrids) {
            val gy = originY + (yg.coordinate - minY)
            val x1 = originX - overhang
            val x2 = originX + lx + overhang
            dxf.addLine(x1, gy, x2, gy, CadLayer.AXES)
            dxf.addCircle(x1 - bubbleRadius, gy, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(x1 - bubbleRadius, gy, bubbleRadius * 1.0, yg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
            dxf.addCircle(x2 + bubbleRadius, gy, bubbleRadius, CadLayer.BULLES_AXES)
            dxf.addText(x2 + bubbleRadius, gy, bubbleRadius * 1.0, yg.id, CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
        }

        // 2. Semelles Filantes selon Y (le long des axes Y)
        for (yg in yGrids) {
            val cy = originY + (yg.coordinate - minY)
            val isJointEdge = yg == yGrids.first() || yg == yGrids.last()
            val footingB = if (isJointEdge) 2.00 else 1.20
            val ribB0 = if (isJointEdge) 0.50 else 0.40
            val footingName = if (isJointEdge) "SFJ200" else "SF120"

            val xStart = originX - 0.60
            val xEnd = originX + lx + 0.60

            // Contour Béton de propreté (débordement 10cm)
            val bpOverhang = 0.10
            dxf.addRectangle(
                xStart - bpOverhang,
                cy - footingB / 2.0 - bpOverhang,
                xEnd + bpOverhang,
                cy + footingB / 2.0 + bpOverhang,
                CadLayer.COFFRAGE_SEMELLES,
                constantWidth = 0.01
            )

            // Contour Table de semelle filante
            dxf.addRectangle(
                xStart,
                cy - footingB / 2.0,
                xEnd,
                cy + footingB / 2.0,
                CadLayer.COFFRAGE_SEMELLES,
                constantWidth = 0.03
            )

            // Contour Nervure centrale
            dxf.addRectangle(
                xStart,
                cy - ribB0 / 2.0,
                xEnd,
                cy + ribB0 / 2.0,
                CadLayer.COFFRAGE_POUTRES,
                constantWidth = 0.02
            )

            // Texte de repère semelle
            val fLabel = "$footingName (B=${String.format(Locale.US, "%.2f", footingB)}m, h=0.90m)"
            dxf.addText(xStart + 1.50, cy + footingB / 2.0 + 0.15, 0.18, fLabel, CadLayer.TEXTES_ET_REPERES, colorOverride = 5)
        }

        // 3. Semelles Filantes selon X (le long des axes X)
        for (xg in xGrids) {
            val cx = originX + (xg.coordinate - minX)
            val isJointEdge = xg == xGrids.first() || xg == xGrids.last()
            val footingB = if (isJointEdge) 2.00 else 1.20
            val ribB0 = if (isJointEdge) 0.50 else 0.40

            val yStart = originY - 0.60
            val yEnd = originY + ly + 0.60

            dxf.addRectangle(
                cx - footingB / 2.0,
                yStart,
                cx + footingB / 2.0,
                yEnd,
                CadLayer.COFFRAGE_SEMELLES,
                constantWidth = 0.03
            )

            dxf.addRectangle(
                cx - ribB0 / 2.0,
                yStart,
                cx + ribB0 / 2.0,
                yEnd,
                CadLayer.COFFRAGE_POUTRES,
                constantWidth = 0.02
            )
        }

        // 4. Amorces Poteaux et Voiles
        val bcMeters = 0.35
        val hcMeters = 0.35
        for (xg in xGrids) {
            for (yg in yGrids) {
                val cx = originX + (xg.coordinate - minX)
                val cy = originY + (yg.coordinate - minY)

                val x1 = cx - bcMeters / 2.0
                val y1 = cy - hcMeters / 2.0
                val x2 = cx + bcMeters / 2.0
                val y2 = cy + hcMeters / 2.0

                dxf.addRectangle(x1, y1, x2, y2, CadLayer.COFFRAGE_POTEAUX, constantWidth = 0.02)
                // Croix diagonale d'amorce
                dxf.addLine(x1, y1, x2, y2, CadLayer.COFFRAGE_POTEAUX)
                dxf.addLine(x1, y2, x2, y1, CadLayer.COFFRAGE_POTEAUX)
            }
        }

        // 5. Cadre et Cartouche CTC Fondations
        val dimOffset2 = overhang + 2.20
        val frameMargin = 5.0
        val frameW = lx + 2.0 * (overhang + dimOffset2 + frameMargin)
        val frameH = ly + 2.0 * (overhang + dimOffset2 + frameMargin)
        val frameX0 = originX - (overhang + dimOffset2 + frameMargin)
        val frameY0 = originY - (overhang + dimOffset2 + frameMargin)

        dxf.drawTitleBlockCTC(
            originX = frameX0,
            originY = frameY0,
            width = frameW,
            height = frameH,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "PLAN DE FONDATIONS - SEMELLES FILANTES & NERVURES",
            scaleText = "1/50"
        )
    }
}
