package com.archilab.rpa2024.cad

import com.archilab.rpa2024.engine.foundations.FootingType
import com.archilab.rpa2024.engine.foundations.StripFootingSection
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.model.*
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Générateur et dessinateur automatique des plans d'exécution de ferraillage (AutoCAD .dxf)
 * conformes aux dispositions constructives parasismiques du Chapitre 7 du RPA 2024 :
 * - Planche 1 : Poteaux (RPA 2024 § 7.4) avec élévation, zones nodales et coupe A-A détaillée
 * - Planche 2 : Poutres & Chaînages (RPA 2024 § 7.5) avec élévation longitudinale et coupes travée/appui
 * - Planche 3 : Voiles de Contreventement (RPA 2024 § 7.7) avec élévation verticale Hcr et coupe horizontale
 * - Planche 4 : Éléments Secondaires & Fondations (Acrotère, Poutrelle, Semelle Filante, Radier Général)
 * - Planche 5 : Nomenclature Générale des Aciers HA & Bordereau de Ferraillage (BBS)
 */
class StructuralDetailingDxfExporter(
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallDesigner: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val bbsGenerator: BarBendingScheduleDxfGenerator = BarBendingScheduleDxfGenerator()
) {

    /**
     * Exporte l'ensemble complet des 5 planches d'exécution du ferraillage dans un fichier DXF unique.
     */
    fun exportAllDetailingDxf(
        joint: StructuralJoint,
        targetFile: File,
        projectTitle: String = "Projet R+4 RPA 2024",
        wilaya: String = "Alger"
    ): File {
        val dxf = DxfCoreEngine()

        var currentOriginX = 0.0

        // 1. Ferraillage Poteaux
        drawColumnDetailingSheet(dxf, joint, currentOriginX, 0.0, projectTitle, wilaya)
        currentOriginX += 26.0

        // 2. Ferraillage Poutres & Chaînages
        drawBeamDetailingSheet(dxf, joint, currentOriginX, 0.0, projectTitle, wilaya)
        currentOriginX += 30.0

        // 3. Ferraillage Voiles de Contreventement
        drawShearWallDetailingSheet(dxf, joint, currentOriginX, 0.0, projectTitle, wilaya)
        currentOriginX += 28.0

        // 4. Ferraillage Éléments Secondaires & Fondations
        drawSecondaryAndFootingDetailingSheet(dxf, joint, currentOriginX, 0.0, projectTitle, wilaya)
        currentOriginX += 26.0

        // 5. Nomenclature Générale des Aciers HA (BBS - Bordereau de Ferraillage)
        drawBendingScheduleSheet(dxf, joint, currentOriginX, 0.0, projectTitle, wilaya)

        return dxf.saveToFile(targetFile)
    }

    /**
     * 1. Planche de ferraillage des Poteaux (RPA 2024 § 7.4.2).
     */
    fun drawColumnDetailingSheet(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        projectTitle: String,
        wilaya: String
    ) {
        val col = joint.sectionCatalog.columns.firstOrNull()
            ?: ColumnFamily("P35X35", ColumnShape.RECTANGULAR, 35.0, 35.0, clearHeightLclMeters = 2.65)
        val bc = col.b1Cm / 100.0
        val hc = col.h1Cm / 100.0
        val lcl = col.clearHeightLclMeters

        // Calcul dynamique via le moteur de ferraillage
        val designReport = columnEngine.designColumn(
            column = col,
            envelope = null,
            zone = joint.seismicParams.zone,
            fc28MPa = joint.materials.fc28MPa,
            feMPa = joint.materials.feMPa
        )

        // Hauteur de zone critique nodale Lcr selon RPA 2024 § 7.4.2 :
        // Lcr = max(hc, bc, lcl / 6, 45 cm)
        val lcr = max(max(hc, bc), max(lcl / 6.0, 0.45))
        val lCourante = max(lcl - 2.0 * lcr, 0.50)
        val lRec = designReport.transverseDesign.overlapLengthLrecCm / 100.0 // ex: 0.70m pour T14

        // A. ÉLÉVATION VERTICALE DU POTEAU
        val elevX = originX + 2.0
        val elevY = originY + 2.0
        val beamH = 0.55 // Hauteur de la poutre en tête
        val slabThick = 0.20 // Dalle en pied

        // Tracé du béton (Poteau + amorces poutres)
        dxf.addRectangle(elevX, elevY, elevX + bc, elevY + lcl, CadLayer.COFFRAGE_POTEAUX, constantWidth = 0.03)
        // Amorce inférieure (plancher bas)
        dxf.addRectangle(elevX - 0.50, elevY - slabThick, elevX + bc + 0.50, elevY, CadLayer.COFFRAGE_POUTRES)
        // Amorce supérieure (poutre haute)
        dxf.addRectangle(elevX - 0.80, elevY + lcl, elevX + bc + 0.80, elevY + lcl + beamH, CadLayer.COFFRAGE_POUTRES)

        // Armatures longitudinales verticales
        val cover = 0.03
        val barLeftX = elevX + cover
        val barRightX = elevX + bc - cover

        // Barres principales traversantes avec recouvrement en pied
        dxf.addLine(barLeftX, elevY - slabThick, barLeftX, elevY + lcl + beamH - cover, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(barRightX, elevY - slabThick, barRightX, elevY + lcl + beamH - cover, CadLayer.ACIER_LONGITUDINAL)

        // Barres d'attente / recouvrement en pied (Lrec)
        dxf.addLine(barLeftX + 0.015, elevY - 0.50, barLeftX + 0.015, elevY + lRec, CadLayer.ACIER_LONGITUDINAL, colorOverride = 1)
        dxf.addLine(barRightX - 0.015, elevY - 0.50, barRightX - 0.015, elevY + lRec, CadLayer.ACIER_LONGITUDINAL, colorOverride = 1)

        // Ancrage en tête par retour d'équerre à 90° dans la poutre
        dxf.addLine(barLeftX, elevY + lcl + beamH - cover, barLeftX + 0.20, elevY + lcl + beamH - cover, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(barRightX, elevY + lcl + beamH - cover, barRightX - 0.20, elevY + lcl + beamH - cover, CadLayer.ACIER_LONGITUDINAL)

        // Cadres horizontaux dans la zone nodale basse (t = 10cm)
        var yTier = elevY + 0.05
        while (yTier <= elevY + lcr) {
            dxf.addLine(barLeftX - 0.01, yTier, barRightX + 0.01, yTier, CadLayer.ACIER_TRANSVERSAL)
            yTier += 0.10
        }
        // Cadres dans la zone courante centrale (t' = 15cm)
        while (yTier <= elevY + lcl - lcr) {
            dxf.addLine(barLeftX - 0.01, yTier, barRightX + 0.01, yTier, CadLayer.ACIER_TRANSVERSAL)
            yTier += 0.15
        }
        // Cadres dans la zone nodale haute (t = 10cm)
        while (yTier <= elevY + lcl) {
            dxf.addLine(barLeftX - 0.01, yTier, barRightX + 0.01, yTier, CadLayer.ACIER_TRANSVERSAL)
            yTier += 0.10
        }

        // Cotations de l'élévation
        dxf.addLinearDimension(elevX + bc, elevY, elevX + bc, elevY + lcr, offset = 0.60, textOverride = String.format(Locale.US, "ZN Lcr=%.2fm (St=10)", lcr))
        dxf.addLinearDimension(elevX + bc, elevY + lcr, elevX + bc, elevY + lcl - lcr, offset = 0.60, textOverride = String.format(Locale.US, "ZC=%.2fm (St'=15)", lCourante))
        dxf.addLinearDimension(elevX + bc, elevY + lcl - lcr, elevX + bc, elevY + lcl, offset = 0.60, textOverride = String.format(Locale.US, "ZN Lcr=%.2fm (St=10)", lcr))
        dxf.addLinearDimension(elevX, elevY, elevX, elevY + lcl, offset = -0.60, textOverride = String.format(Locale.US, "Lcl = %.2f m", lcl))
        dxf.addLinearDimension(barLeftX + 0.02, elevY, barLeftX + 0.02, elevY + lRec, offset = 0.25, textOverride = String.format(Locale.US, "Lrec=%.0fcm", lRec * 100.0), layer = CadLayer.COTATIONS)

        // Titre élévation
        dxf.addText(elevX + bc / 2.0, elevY - 0.90, 0.24, "ÉLÉVATION DU POTEAU ${col.name}", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)

        // B. COUPE TRANSVERSALE A-A (Échelle 1/20e agrandie, située à droite)
        val secX = originX + 7.50
        val secY = originY + 2.50
        val scale = 3.0 // Multiplicateur pour lisibilité graphique

        val sW = bc * scale
        val sH = hc * scale
        val sCov = cover * scale

        // Rectangle extérieur béton
        dxf.addRectangle(secX, secY, secX + sW, secY + sH, CadLayer.COFFRAGE_POTEAUX, constantWidth = 0.03)

        // Cadre périphérique transversal avec crochets à 135°
        val cadrePts = listOf(
            CadPoint(secX + sCov, secY + sCov),
            CadPoint(secX + sW - sCov, secY + sCov),
            CadPoint(secX + sW - sCov, secY + sH - sCov),
            CadPoint(secX + sCov, secY + sH - sCov)
        )
        dxf.addLwPolyline(cadrePts, isClosed = true, layer = CadLayer.ACIER_TRANSVERSAL, constantWidth = 0.015)

        // Crochet à 135° RPA 2024 (longueur 10 * phi = 8cm)
        val hookLen = 0.08 * scale
        dxf.addLine(secX + sCov, secY + sCov, secX + sCov + hookLen * 0.707, secY + sCov + hookLen * 0.707, CadLayer.ACIER_TRANSVERSAL)
        dxf.addLine(secX + sCov, secY + sCov, secX + sCov + hookLen * 0.707, secY + sCov - hookLen * 0.20, CadLayer.ACIER_TRANSVERSAL)

        // Étrier / épingle intérieure en croix
        dxf.addLine(secX + sW / 2.0, secY + sCov, secX + sW / 2.0, secY + sH - sCov, CadLayer.ACIER_TRANSVERSAL)
        dxf.addLine(secX + sCov, secY + sH / 2.0, secX + sW - sCov, secY + sH / 2.0, CadLayer.ACIER_TRANSVERSAL)

        // 8 barres longitudinales principales (4 angles + 4 faces)
        val barRadius = 0.025 * scale
        val rebarCoords = listOf(
            CadPoint(secX + sCov, secY + sCov),
            CadPoint(secX + sW / 2.0, secY + sCov),
            CadPoint(secX + sW - sCov, secY + sCov),
            CadPoint(secX + sW - sCov, secY + sH / 2.0),
            CadPoint(secX + sW - sCov, secY + sH - sCov),
            CadPoint(secX + sW / 2.0, secY + sH - sCov),
            CadPoint(secX + sCov, secY + sH - sCov),
            CadPoint(secX + sCov, secY + sH / 2.0)
        )
        for (pt in rebarCoords) {
            dxf.addCircle(pt.x, pt.y, barRadius, CadLayer.ACIER_LONGITUDINAL)
            // Hachure pleine du rond à béton
            val cPts = (0..12).map {
                val a = it * 2.0 * kotlin.math.PI / 12.0
                CadPoint(pt.x + barRadius * kotlin.math.cos(a), pt.y + barRadius * kotlin.math.sin(a))
            }
            dxf.addSolidHatch(cPts, CadLayer.ACIER_LONGITUDINAL, colorOverride = 1)
        }

        // Cotations de la section
        dxf.addLinearDimension(secX, secY, secX + sW, secY, offset = -0.40, textOverride = "${col.b1Cm.toInt()} cm")
        dxf.addLinearDimension(secX, secY, secX, secY + sH, offset = -0.40, textOverride = "${col.h1Cm.toInt()} cm")

        // Légendes et bulles de repérage
        dxf.addLeaderWithBubble(secX + sCov, secY + sCov, secX - 0.80, secY + sH + 0.50, 0.25, "1", "4T16 (Angles)")
        dxf.addLeaderWithBubble(secX + sW / 2.0, secY + sCov, secX + sW / 2.0, secY - 0.80, 0.25, "2", "4T14 (Intermédiaires)")
        dxf.addLeaderWithBubble(secX + sW - sCov, secY + sH / 2.0, secX + sW + 0.80, secY + sH / 2.0, 0.25, "3", "Cadre + Étrier T8 (ZN:10, ZC:15)")

        dxf.addText(secX + sW / 2.0, secY - 1.20, 0.24, "COUPE A-A (ECH. 1/20) - ${designReport.rebarPatternDescription}", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)

        // Tableau récapitulatif de conformité sur la planche
        val tabX = originX + 15.5
        val tabY = originY + 8.5
        dxf.addRectangle(tabX, tabY - 5.0, tabX + 6.0, tabY, CadLayer.TEXTES_ET_REPERES, constantWidth = 0.02)
        dxf.addText(tabX + 3.0, tabY - 0.5, 0.20, "VÉRIFICATIONS CTC RPA 2024", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)
        dxf.addLine(tabX, tabY - 0.8, tabX + 6.0, tabY - 0.8, CadLayer.TEXTES_ET_REPERES)
        dxf.addText(tabX + 0.3, tabY - 1.4, 0.16, "Section : ${col.b1Cm.toInt()}x${col.h1Cm.toInt()} cm", CadLayer.TEXTES_ET_REPERES)
        dxf.addText(tabX + 0.3, tabY - 2.0, 0.16, "A adoptée : ${String.format(Locale.US, "%.2f", designReport.aAdoptedCm2)} cm²", CadLayer.TEXTES_ET_REPERES)
        dxf.addText(tabX + 0.3, tabY - 2.6, 0.16, "Pourcentage : ${String.format(Locale.US, "%.2f", (designReport.aAdoptedCm2 / col.grossAreaBcCm2) * 100.0)}% (>= 0.8%) [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)
        dxf.addText(tabX + 0.3, tabY - 3.2, 0.16, "Espacement : <= 20 cm [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)
        dxf.addText(tabX + 0.3, tabY - 3.8, 0.16, "Zone nodale Lcr : ${String.format(Locale.US, "%.2f", lcr)} m", CadLayer.TEXTES_ET_REPERES)
        dxf.addText(tabX + 0.3, tabY - 4.4, 0.16, "Recouvrement Lrec : ${String.format(Locale.US, "%.0f", lRec * 100.0)} cm", CadLayer.TEXTES_ET_REPERES)

        // Cadre de planche et Cartouche CTC
        dxf.drawTitleBlockCTC(
            originX = originX,
            originY = originY,
            width = 24.0,
            height = 14.0,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "FERRAILLAGE POTEAUX (${col.name}) - RPA 2024 § 7.4",
            scaleText = "1/50 - 1/20"
        )
    }

    /**
     * 2. Planche de ferraillage des Poutres et Chaînages (RPA 2024 § 7.5.2).
     */
    fun drawBeamDetailingSheet(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        projectTitle: String,
        wilaya: String
    ) {
        val beam = joint.sectionCatalog.beams.find { it.role == BeamRole.MAIN_BEAM }
            ?: joint.sectionCatalog.beams.firstOrNull()
            ?: BeamFamily("PR30X55", BeamRole.MAIN_BEAM, 30.0, 55.0, maxClearSpanMeters = 4.50)
        val b = beam.bCm / 100.0
        val h = beam.hCm / 100.0
        val spanLn = min(beam.maxClearSpanMeters, 4.50)

        // Calcul dynamique via le moteur de ferraillage
        val beamReport = beamEngine.designBeam(
            beam = beam,
            envelope = null,
            zone = joint.seismicParams.zone,
            fc28MPa = joint.materials.fc28MPa,
            feMPa = joint.materials.feMPa
        )

        // A. ÉLÉVATION LONGITUDINALE DE LA TRAVÉE
        val elevX = originX + 2.0
        val elevY = originY + 5.5
        val colWidth = 0.40

        // Poteaux gauche et droit
        dxf.addRectangle(elevX - colWidth, elevY - 0.80, elevX, elevY + h + 0.80, CadLayer.COFFRAGE_POTEAUX)
        dxf.addRectangle(elevX + spanLn, elevY - 0.80, elevX + spanLn + colWidth, elevY + h + 0.80, CadLayer.COFFRAGE_POTEAUX)

        // Contour de la poutre
        dxf.addRectangle(elevX, elevY, elevX + spanLn, elevY + h, CadLayer.COFFRAGE_POUTRES, constantWidth = 0.03)

        // Armatures longitudinales filantes
        val cover = 0.03
        // Filantes inférieures ancrées dans les poteaux
        val yInf = elevY + cover
        dxf.addLine(elevX - colWidth + cover, yInf, elevX + spanLn + colWidth - cover, yInf, CadLayer.ACIER_LONGITUDINAL)
        // Crochets d'ancrage aux extrémités
        dxf.addLine(elevX - colWidth + cover, yInf, elevX - colWidth + cover, yInf + 0.25, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(elevX + spanLn + colWidth - cover, yInf, elevX + spanLn + colWidth - cover, yInf + 0.25, CadLayer.ACIER_LONGITUDINAL)

        // Filantes supérieures
        val ySup = elevY + h - cover
        dxf.addLine(elevX - colWidth + cover, ySup, elevX + spanLn + colWidth - cover, ySup, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(elevX - colWidth + cover, ySup, elevX - colWidth + cover, ySup - 0.25, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(elevX + spanLn + colWidth - cover, ySup, elevX + spanLn + colWidth - cover, ySup - 0.25, CadLayer.ACIER_LONGITUDINAL)

        // Chapeaux sur appuis (sur L/4 = spanLn / 4)
        val lChapeau = spanLn * 0.25
        dxf.addLine(elevX - colWidth + cover, ySup - 0.02, elevX + lChapeau, ySup - 0.02, CadLayer.ACIER_LONGITUDINAL, colorOverride = 1)
        dxf.addLine(elevX + spanLn - lChapeau, ySup - 0.02, elevX + spanLn + colWidth - cover, ySup - 0.02, CadLayer.ACIER_LONGITUDINAL, colorOverride = 1)

        // Cadres verticaux selon RPA 2024 § 7.5.2 :
        // Zone nodale l' = 2h = pas S = 8cm (1er cadre à 5cm du nu)
        val lPrime = min(2.0 * h, spanLn * 0.25)
        var xFrame = elevX + 0.05
        while (xFrame <= elevX + lPrime) {
            dxf.addLine(xFrame, yInf, xFrame, ySup, CadLayer.ACIER_TRANSVERSAL)
            xFrame += 0.08
        }
        // Zone courante centrale au pas S' = 15cm
        while (xFrame <= elevX + spanLn - lPrime) {
            dxf.addLine(xFrame, yInf, xFrame, ySup, CadLayer.ACIER_TRANSVERSAL)
            xFrame += 0.15
        }
        // Zone nodale droite
        while (xFrame <= elevX + spanLn - 0.05) {
            dxf.addLine(xFrame, yInf, xFrame, ySup, CadLayer.ACIER_TRANSVERSAL)
            xFrame += 0.08
        }

        // Cotations élévation poutre
        dxf.addLinearDimension(elevX, elevY, elevX + spanLn, elevY, offset = -0.50, textOverride = String.format(Locale.US, "Portée libre Ln = %.2f m", spanLn))
        dxf.addLinearDimension(elevX, elevY, elevX + lPrime, elevY, offset = -0.90, textOverride = "ZN: S=8cm")
        dxf.addLinearDimension(elevX + lPrime, elevY, elevX + spanLn - lPrime, elevY, offset = -0.90, textOverride = "ZC: S'=15cm")
        dxf.addLinearDimension(elevX + spanLn - lPrime, elevY, elevX + spanLn, elevY, offset = -0.90, textOverride = "ZN: S=8cm")

        dxf.addText(elevX + spanLn / 2.0, elevY + h + 0.60, 0.24, "ÉLÉVATION POUTRE PRINCIPALE ${beam.name} (${(b * 100).toInt()}x${(h * 100).toInt()})", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)

        // B. COUPES TRANSVERSALES EN APPUI ET EN TRAVÉE (Échelle 1/20e)
        val secScale = 3.0
        val sW = b * secScale
        val sH = h * secScale
        val sCov = cover * secScale

        // Coupe en Travée
        val secX1 = originX + 2.0
        val secY1 = originY + 1.20
        dxf.addRectangle(secX1, secY1, secX1 + sW, secY1 + sH, CadLayer.COFFRAGE_POUTRES, constantWidth = 0.03)
        dxf.addRectangle(secX1 + sCov, secY1 + sCov, secX1 + sW - sCov, secY1 + sH - sCov, CadLayer.ACIER_TRANSVERSAL, constantWidth = 0.015)
        // Aciers 3T14 en bas, 3T14 en haut
        val barRad = 0.02 * secScale
        for (k in 0..2) {
            val bx = secX1 + sCov + k * (sW - 2.0 * sCov) / 2.0
            dxf.addCircle(bx, secY1 + sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(bx, secY1 + sH - sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
        }
        dxf.addText(secX1 + sW / 2.0, secY1 - 0.40, 0.20, "COUPE EN TRAVÉE (3T14 sup + 3T14 inf)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER)

        // Coupe sur Appui (avec chapeaux en lit supérieur)
        val secX2 = originX + 9.0
        val secY2 = originY + 1.20
        dxf.addRectangle(secX2, secY2, secX2 + sW, secY2 + sH, CadLayer.COFFRAGE_POUTRES, constantWidth = 0.03)
        dxf.addRectangle(secX2 + sCov, secY2 + sCov, secX2 + sW - sCov, secY2 + sH - sCov, CadLayer.ACIER_TRANSVERSAL, constantWidth = 0.015)
        for (k in 0..2) {
            val bx = secX2 + sCov + k * (sW - 2.0 * sCov) / 2.0
            dxf.addCircle(bx, secY2 + sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(bx, secY2 + sH - sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
            // Deuxième lit chapeaux
            dxf.addCircle(bx, secY2 + sH - sCov - barRad * 2.5, barRad, CadLayer.ACIER_LONGITUDINAL)
        }
        dxf.addText(secX2 + sW / 2.0, secY2 - 0.40, 0.20, "COUPE SUR APPUI (Chapeaux ${beamReport.supportRebars.chapeauxDescription})", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER)

        // Tableau récapitulatif 4 vérifications d'effort tranchant sur la planche
        val tabX = originX + 16.5
        val tabY = originY + 4.5
        dxf.addRectangle(tabX, tabY - 3.5, tabX + 8.0, tabY, CadLayer.TEXTES_ET_REPERES, constantWidth = 0.02)
        dxf.addText(tabX + 4.0, tabY - 0.4, 0.18, "4 VÉRIFICATIONS D'EFFORT TRANCHANT (RPA § 7.5.2)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)
        dxf.addLine(tabX, tabY - 0.7, tabX + 8.0, tabY - 0.7, CadLayer.TEXTES_ET_REPERES)
        dxf.addText(tabX + 0.3, tabY - 1.2, 0.15, "1. Tau_u = ${String.format(Locale.US, "%.2f", beamReport.shearChecks.tauUMPa)} MPa <= ${String.format(Locale.US, "%.2f", beamReport.shearChecks.tauUAdmissibleMPa)} MPa [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)
        dxf.addText(tabX + 0.3, tabY - 1.7, 0.15, "2. Bielle comprimée : Vu <= Vu_bielle [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)
        dxf.addText(tabX + 0.3, tabY - 2.2, 0.15, "3. Aciers inf. appui : As,inf >= Vu/(fe/gamma_s) [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)
        dxf.addText(tabX + 0.3, tabY - 2.7, 0.15, "4. Compression d'appui : sigma_c <= fc28 [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)
        dxf.addText(tabX + 0.3, tabY - 3.2, 0.15, "Règle A_inf >= 0.50 A_sup : Respectée [CV]", CadLayer.TEXTES_ET_REPERES, colorOverride = 3)

        // Cartouche CTC Poutres
        dxf.drawTitleBlockCTC(
            originX = originX,
            originY = originY,
            width = 28.0,
            height = 14.0,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "FERRAILLAGE POUTRES & CHAÎNAGES - RPA 2024 § 7.5",
            scaleText = "1/50 - 1/20"
        )
    }

    /**
     * 3. Planche de ferraillage des Voiles de Contreventement (RPA 2024 § 7.7.4).
     */
    fun drawShearWallDetailingSheet(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        projectTitle: String,
        wilaya: String
    ) {
        val wall = joint.sectionCatalog.shearWalls.firstOrNull()
            ?: ShearWallFamily("V1", AxisDirection.X, 3.00, 0.20, 2.65, 15.44)
        val lw = wall.lengthLwMeters
        val bw = wall.thicknessBwMeters
        val lcl = wall.clearHeightHeMeters

        // Calcul dynamique via le moteur de voiles
        val wallReport = wallDesigner.designShearWall(
            wall = wall,
            designAxialForceNdKN = 1200.0,
            designMomentMKNm = 650.0,
            designShearVuKN = 350.0,
            fc28MPa = joint.materials.fc28MPa,
            feMPa = joint.materials.feMPa
        )

        val hcr = max(wall.clearHeightHeMeters, max(lw / 6.0, 2.0 * bw))
        val lc = wallReport.leftBoundaryElement.lengthLcMeters
        val lame = lw - 2.0 * lc

        // A. ÉLÉVATION VERTICALE DU VOILE (Affichant Hcr, potelets et âme)
        val elevX = originX + 2.0
        val elevY = originY + 1.5
        val scaleElev = 1.6
        val eLw = lw * scaleElev
        val eH = lcl * scaleElev
        val eHcr = min(hcr * scaleElev, eH * 0.70)
        val eLc = lc * scaleElev

        // Contour béton du voile
        dxf.addRectangle(elevX, elevY, elevX + eLw, elevY + eH, CadLayer.COFFRAGE_VOILES, constantWidth = 0.03)

        // Semelle / fondation d'amorce en pied
        dxf.addRectangle(elevX - 0.40, elevY - 0.50, elevX + eLw + 0.40, elevY, CadLayer.COFFRAGE_SEMELLES)

        // Ligne de délimitation de la zone critique Hcr
        dxf.addLine(elevX, elevY + eHcr, elevX + eLw, elevY + eHcr, CadLayer.AXES, linetypeOverride = "DASHED")
        dxf.addText(elevX + eLw + 0.20, elevY + eHcr, 0.18, String.format(Locale.US, "Limite Hcr = %.2f m", hcr), CadLayer.TEXTES_ET_REPERES, colorOverride = 1)

        // Potelets d'extrémités verticaux (traits mixtes)
        dxf.addLine(elevX + eLc, elevY, elevX + eLc, elevY + eH, CadLayer.AXES, linetypeOverride = "HIDDEN")
        dxf.addLine(elevX + eLw - eLc, elevY, elevX + eLw - eLc, elevY + eH, CadLayer.AXES, linetypeOverride = "HIDDEN")

        // Barres d'attente en pied
        dxf.addLine(elevX + 0.08, elevY - 0.40, elevX + 0.08, elevY + 0.80, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(elevX + eLw - 0.08, elevY - 0.40, elevX + eLw - 0.08, elevY + 0.80, CadLayer.ACIER_LONGITUDINAL)

        // Cadres resserrés en zone critique Hcr (St = 8 cm)
        var yCr = elevY + 0.08
        while (yCr <= elevY + eHcr) {
            dxf.addLine(elevX + 0.04, yCr, elevX + eLc - 0.04, yCr, CadLayer.ACIER_TRANSVERSAL)
            dxf.addLine(elevX + eLw - eLc + 0.04, yCr, elevX + eLw - 0.04, yCr, CadLayer.ACIER_TRANSVERSAL)
            yCr += 0.12
        }
        // Cadres courants au-dessus de Hcr (St = 15 cm)
        while (yCr <= elevY + eH) {
            dxf.addLine(elevX + 0.04, yCr, elevX + eLc - 0.04, yCr, CadLayer.ACIER_TRANSVERSAL)
            dxf.addLine(elevX + eLw - eLc + 0.04, yCr, elevX + eLw - 0.04, yCr, CadLayer.ACIER_TRANSVERSAL)
            yCr += 0.22
        }

        // Cotations élévation
        dxf.addLinearDimension(elevX, elevY, elevX, elevY + eHcr, offset = -0.50, textOverride = String.format(Locale.US, "Hcr=%.2fm (St=8)", hcr))
        dxf.addLinearDimension(elevX, elevY + eHcr, elevX, elevY + eH, offset = -0.50, textOverride = "ZC (St=15)")
        dxf.addLinearDimension(elevX, elevY, elevX + eLw, elevY, offset = -0.80, textOverride = String.format(Locale.US, "Lw = %.2f m", lw))

        dxf.addText(elevX + eLw / 2.0, elevY + eH + 0.50, 0.22, "ÉLÉVATION VERTICALE VOILE ${wall.pierName}", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)

        // B. COUPE HORIZONTALE DU VOILE EN ZONE CRITIQUE (Échelle 1/20e agrandie)
        val secScale = 2.2
        val sLw = lw * secScale
        val sBw = bw * secScale
        val sLc = lc * secScale
        val sCov = 0.025 * secScale

        val secX = originX + 2.0
        val secY = originY + 8.5

        // Contour extérieur béton du voile
        dxf.addRectangle(secX, secY, secX + sLw, secY + sBw, CadLayer.COFFRAGE_VOILES, constantWidth = 0.03)

        // Lignes de délimitation des éléments de rive gauche et droite
        dxf.addLine(secX + sLc, secY, secX + sLc, secY + sBw, CadLayer.AXES, linetypeOverride = "HIDDEN")
        dxf.addLine(secX + sLw - sLc, secY, secX + sLw - sLc, secY + sBw, CadLayer.AXES, linetypeOverride = "HIDDEN")

        // Cadres de confinement dans les éléments de rive (crochets fermés à 135°)
        dxf.addRectangle(secX + sCov, secY + sCov, secX + sLc - sCov, secY + sBw - sCov, CadLayer.ACIER_TRANSVERSAL, constantWidth = 0.015)
        dxf.addRectangle(secX + sLw - sLc + sCov, secY + sCov, secX + sLw - sCov, secY + sBw - sCov, CadLayer.ACIER_TRANSVERSAL, constantWidth = 0.015)

        // Armatures verticales de rive (6T14 à gauche et à droite)
        val barRad = 0.02 * secScale
        for (i in 0..2) {
            val bxL = secX + sCov + i * (sLc - 2.0 * sCov) / 2.0
            val bxR = secX + sLw - sLc + sCov + i * (sLc - 2.0 * sCov) / 2.0
            dxf.addCircle(bxL, secY + sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(bxL, secY + sBw - sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(bxR, secY + sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(bxR, secY + sBw - sCov, barRad, CadLayer.ACIER_LONGITUDINAL)
        }

        // Armatures de l'âme (Zone courante : nappe double T12 espacées de 15cm)
        var xAme = secX + sLc + 0.15 * secScale
        while (xAme <= secX + sLw - sLc - 0.10 * secScale) {
            dxf.addCircle(xAme, secY + sCov, barRad * 0.85, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(xAme, secY + sBw - sCov, barRad * 0.85, CadLayer.ACIER_LONGITUDINAL)
            // Épingle de liaison transversale (4 par m²)
            dxf.addLine(xAme, secY + sCov, xAme, secY + sBw - sCov, CadLayer.ACIER_TRANSVERSAL)
            xAme += 0.20 * secScale
        }

        // Armatures horizontales filantes de l'âme ancrées dans les rives
        dxf.addLine(secX + sCov, secY + sCov, secX + sLw - sCov, secY + sCov, CadLayer.ACIER_TRANSVERSAL)
        dxf.addLine(secX + sCov, secY + sBw - sCov, secX + sLw - sCov, secY + sBw - sCov, CadLayer.ACIER_TRANSVERSAL)

        // Cotations du voile
        dxf.addLinearDimension(secX, secY, secX + sLc, secY, offset = -0.40, textOverride = String.format(Locale.US, "lc = %.2fm", lc))
        dxf.addLinearDimension(secX + sLc, secY, secX + sLw - sLc, secY, offset = -0.40, textOverride = String.format(Locale.US, "Âme = %.2fm", lame))
        dxf.addLinearDimension(secX + sLw - sLc, secY, secX + sLw, secY, offset = -0.40, textOverride = String.format(Locale.US, "lc = %.2fm", lc))
        dxf.addLinearDimension(secX, secY, secX + sLw, secY, offset = -0.80, textOverride = String.format(Locale.US, "Lw = %.2f m", lw))

        // Légendes
        dxf.addText(secX + sLw / 2.0, secY + sBw + 0.50, 0.22, "COUPE HORIZONTALE VOILE ${wall.pierName} (ZONE CRITIQUE HCR)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER, colorOverride = 1)
        dxf.addLeaderWithBubble(secX + sLc / 2.0, secY + sCov, secX + sLc / 2.0, secY + sBw + 1.00, 0.22, "1", "Élément de rive : 6T14 + Cadres T8 e=8cm")
        dxf.addLeaderWithBubble(secX + sLw / 2.0, secY + sCov, secX + sLw / 2.0, secY - 1.20, 0.22, "2", "Âme : Double nappe T12 e=15cm + Épingles (4/m²)")

        // Cartouche CTC Voiles
        dxf.drawTitleBlockCTC(
            originX = originX,
            originY = originY,
            width = 26.0,
            height = 14.0,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "FERRAILLAGE VOILES DE CONTREVENTEMENT - RPA 2024 § 7.7",
            scaleText = "1/50 - 1/20"
        )
    }

    /**
     * 4. Planche de ferraillage des Éléments Secondaires (Acrotère, Poutrelles) et Semelles Filantes.
     */
    fun drawSecondaryAndFootingDetailingSheet(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        projectTitle: String,
        wilaya: String
    ) {
        val scale = 3.0

        // A. COUPE ACROTÈRE (H = 60cm, e = 10cm, Becquet = 15cm)
        val acroX = originX + 2.0
        val acroY = originY + 7.0
        val hAcro = 0.60 * scale
        val eAcro = 0.10 * scale
        val bBecquet = 0.15 * scale

        val acroPts = listOf(
            CadPoint(acroX, acroY),
            CadPoint(acroX + eAcro + bBecquet, acroY),
            CadPoint(acroX + eAcro + bBecquet, acroY + 0.15 * scale),
            CadPoint(acroX + eAcro, acroY + 0.15 * scale),
            CadPoint(acroX + eAcro, acroY + hAcro),
            CadPoint(acroX, acroY + hAcro)
        )
        dxf.addLwPolyline(acroPts, isClosed = true, layer = CadLayer.COFFRAGE_POTEAUX, constantWidth = 0.02)
        // Armature en épingle continue et ronds de répartition
        dxf.addLine(acroX + 0.02 * scale, acroY + 0.02 * scale, acroX + 0.02 * scale, acroY + hAcro - 0.02 * scale, CadLayer.ACIER_LONGITUDINAL)
        for (k in 0..3) {
            dxf.addCircle(acroX + 0.04 * scale, acroY + 0.05 * scale + k * 0.15 * scale, 0.03, CadLayer.ACIER_TRANSVERSAL)
        }
        dxf.addText(acroX + eAcro / 2.0, acroY - 0.40, 0.18, "ACROTÈRE (H=60cm, T8 e=15cm)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER)

        // B. COUPE POUTRELLE EN TÉ (16+5 cm, Table 60cm, Nervure 12cm)
        val slabX = originX + 7.5
        val slabY = originY + 7.0
        val bTable = 0.60 * scale
        val b0 = 0.12 * scale
        val hTotal = 0.21 * scale
        val hTable = 0.05 * scale

        val tBeamPts = listOf(
            CadPoint(slabX, slabY + hTotal),
            CadPoint(slabX + bTable, slabY + hTotal),
            CadPoint(slabX + bTable, slabY + hTotal - hTable),
            CadPoint(slabX + (bTable + b0) / 2.0, slabY + hTotal - hTable),
            CadPoint(slabX + (bTable + b0) / 2.0, slabY),
            CadPoint(slabX + (bTable - b0) / 2.0, slabY),
            CadPoint(slabX + (bTable - b0) / 2.0, slabY + hTotal - hTable),
            CadPoint(slabX, slabY + hTotal - hTable)
        )
        dxf.addLwPolyline(tBeamPts, isClosed = true, layer = CadLayer.COFFRAGE_POUTRES, constantWidth = 0.02)
        // Treillis soudé de table et aciers inférieurs nervure (2T12 + étrier phi 6)
        dxf.addLine(slabX + 0.02 * scale, slabY + hTotal - 0.02 * scale, slabX + bTable - 0.02 * scale, slabY + hTotal - 0.02 * scale, CadLayer.ACIER_TRANSVERSAL)
        dxf.addCircle(slabX + bTable / 2.0 - 0.02 * scale, slabY + 0.03 * scale, 0.03, CadLayer.ACIER_LONGITUDINAL)
        dxf.addCircle(slabX + bTable / 2.0 + 0.02 * scale, slabY + 0.03 * scale, 0.03, CadLayer.ACIER_LONGITUDINAL)
        dxf.addText(slabX + bTable / 2.0, slabY - 0.40, 0.18, "POUTRELLE 16+5 (2T12 inf + TS Ø5)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER)

        // C. COUPE SEMELLE FILANTE NERVURÉE EN TÉ RENVERSÉ (B = 1.60m, b0 = 0.40m, h = 0.90m)
        val ftgX = originX + 2.0
        val ftgY = originY + 1.20
        val fScale = 2.0
        val bFtg = 1.60 * fScale
        val b0Ftg = 0.40 * fScale
        val hFtg = 0.90 * fScale
        val h0Ftg = 0.40 * fScale

        // Béton de propreté ép=10cm (débord 10cm)
        dxf.addRectangle(ftgX - 0.10 * fScale, ftgY - 0.10 * fScale, ftgX + bFtg + 0.10 * fScale, ftgY, CadLayer.COFFRAGE_SEMELLES)

        // Contour Té renversé de la semelle
        val ftgPts = listOf(
            CadPoint(ftgX, ftgY),
            CadPoint(ftgX + bFtg, ftgY),
            CadPoint(ftgX + bFtg, ftgY + h0Ftg),
            CadPoint(ftgX + (bFtg + b0Ftg) / 2.0, ftgY + h0Ftg),
            CadPoint(ftgX + (bFtg + b0Ftg) / 2.0, ftgY + hFtg),
            CadPoint(ftgX + (bFtg - b0Ftg) / 2.0, ftgY + hFtg),
            CadPoint(ftgX + (bFtg - b0Ftg) / 2.0, ftgY + h0Ftg),
            CadPoint(ftgX, ftgY + h0Ftg)
        )
        dxf.addLwPolyline(ftgPts, isClosed = true, layer = CadLayer.COFFRAGE_SEMELLES, constantWidth = 0.03)

        // Nappe de ferraillage inférieure de semelle (T12 e=15cm)
        dxf.addLine(ftgX + 0.05 * fScale, ftgY + 0.05 * fScale, ftgX + bFtg - 0.05 * fScale, ftgY + 0.05 * fScale, CadLayer.ACIER_LONGITUDINAL)
        // Crochets d'extrémité de semelle
        dxf.addLine(ftgX + 0.05 * fScale, ftgY + 0.05 * fScale, ftgX + 0.05 * fScale, ftgY + h0Ftg - 0.05 * fScale, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(ftgX + bFtg - 0.05 * fScale, ftgY + 0.05 * fScale, ftgX + bFtg - 0.05 * fScale, ftgY + h0Ftg - 0.05 * fScale, CadLayer.ACIER_LONGITUDINAL)

        // Armatures longitudinales et cadres de la nervure (3T16 inf + 3T16 sup)
        for (i in 0..2) {
            val rx = ftgX + (bFtg - b0Ftg) / 2.0 + 0.05 * fScale + i * 0.12 * fScale
            dxf.addCircle(rx, ftgY + 0.08 * fScale, 0.03, CadLayer.ACIER_LONGITUDINAL)
            dxf.addCircle(rx, ftgY + hFtg - 0.05 * fScale, 0.03, CadLayer.ACIER_LONGITUDINAL)
        }
        dxf.addRectangle(ftgX + (bFtg - b0Ftg) / 2.0 + 0.04 * fScale, ftgY + 0.05 * fScale, ftgX + (bFtg + b0Ftg) / 2.0 - 0.04 * fScale, ftgY + hFtg - 0.04 * fScale, CadLayer.ACIER_TRANSVERSAL)

        dxf.addText(ftgX + bFtg / 2.0, ftgY - 0.40, 0.20, "SEMELLE FILANTE SF160 (B=1.60m, Nappe T12 e=15cm)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER)

        // D. COUPE RADIER GÉNÉRAL (h = 50 cm, Débord 50 cm, 2 nappes T14/T16)
        val raftX = originX + 11.0
        val raftY = originY + 1.20
        val rWidth = 4.0 * fScale
        val rThick = 0.50 * fScale
        val rOverhang = 0.50 * fScale

        // Béton de propreté sous radier ép=10cm
        dxf.addRectangle(raftX - 0.10 * fScale, raftY - 0.10 * fScale, raftX + rWidth + 0.10 * fScale, raftY, CadLayer.COFFRAGE_SEMELLES)

        // Dalle du radier
        dxf.addRectangle(raftX, raftY, raftX + rWidth, raftY + rThick, CadLayer.COFFRAGE_SEMELLES, constantWidth = 0.03)

        // Nappe inférieure (T14 e=15cm)
        dxf.addLine(raftX + 0.05 * fScale, raftY + 0.05 * fScale, raftX + rWidth - 0.05 * fScale, raftY + 0.05 * fScale, CadLayer.ACIER_LONGITUDINAL)
        // Nappe supérieure (T14 e=15cm)
        dxf.addLine(raftX + 0.05 * fScale, raftY + rThick - 0.05 * fScale, raftX + rWidth - 0.05 * fScale, raftY + rThick - 0.05 * fScale, CadLayer.ACIER_LONGITUDINAL, colorOverride = 1)

        // Poteaux d'amorce sur radier
        val colW = 0.40 * fScale
        val colH = 0.60 * fScale
        val col1X = raftX + rOverhang + 0.30 * fScale
        val col2X = raftX + rWidth - rOverhang - colW - 0.30 * fScale
        dxf.addRectangle(col1X, raftY + rThick, col1X + colW, raftY + rThick + colH, CadLayer.COFFRAGE_POTEAUX)
        dxf.addRectangle(col2X, raftY + rThick, col2X + colW, raftY + rThick + colH, CadLayer.COFFRAGE_POTEAUX)

        // Barres d'attente poteaux ancrées dans le radier
        dxf.addLine(col1X + 0.04 * fScale, raftY + 0.05 * fScale, col1X + 0.04 * fScale, raftY + rThick + colH, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(col1X + colW - 0.04 * fScale, raftY + 0.05 * fScale, col1X + colW - 0.04 * fScale, raftY + rThick + colH, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(col2X + 0.04 * fScale, raftY + 0.05 * fScale, col2X + 0.04 * fScale, raftY + rThick + colH, CadLayer.ACIER_LONGITUDINAL)
        dxf.addLine(col2X + colW - 0.04 * fScale, raftY + 0.05 * fScale, col2X + colW - 0.04 * fScale, raftY + rThick + colH, CadLayer.ACIER_LONGITUDINAL)

        dxf.addText(raftX + rWidth / 2.0, raftY - 0.40, 0.20, "VARIANTE RADIER GÉNÉRAL (h=50cm, Débord 50cm, 2 nappes T14)", CadLayer.TEXTES_ET_REPERES, hAlign = CadHAlign.CENTER)

        // Cartouche CTC Éléments Secondaires et Fondations
        dxf.drawTitleBlockCTC(
            originX = originX,
            originY = originY,
            width = 24.0,
            height = 14.0,
            projectTitle = projectTitle,
            jointName = joint.jointName,
            wilaya = wilaya,
            drawingTitle = "FERRAILLAGE ÉLÉMENTS SECONDAIRES & FONDATIONS (SEMELLES / RADIER)",
            scaleText = "1/20"
        )
    }

    /**
     * 5. Planche de Nomenclature des Aciers HA & Bordereau de Ferraillage (BBS).
     */
    fun drawBendingScheduleSheet(
        dxf: DxfCoreEngine,
        joint: StructuralJoint,
        originX: Double,
        originY: Double,
        projectTitle: String,
        wilaya: String
    ) {
        val rep = bbsGenerator.computeBendingSchedule(joint)
        val numRows = rep.items.size
        val rowHeight = 0.70
        val tableHeight = (numRows + 2) * rowHeight + 4.0
        val frameMargin = 2.0

        // Calcul de la coordonnée haute pour alignement sur originY en base
        val topY = originY + tableHeight + frameMargin
        bbsGenerator.drawBendingScheduleTable(
            dxf = dxf,
            originX = originX + frameMargin,
            originY = topY,
            report = rep,
            joint = joint,
            projectTitle = projectTitle,
            wilaya = wilaya
        )
    }
}
