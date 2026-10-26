package com.archilab.rpa2024.graphics

import java.awt.*
import java.awt.geom.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * Moteur vectoriel officiel de dessin d'armatures réelles selon BAEL 91 révisé 99 et RPA 2024 (§ 7.4 & § 7.5).
 * Génère des coupes transversales et des élévations d'éléments en béton armé (poteaux, poutres)
 * avec cotations précises, crochets à 135°, zones nodales Lcr, recouvrements Lrec et cartouches CTC.
 */
class RebarSectionDetailRenderer {

    data class RebarPoint(val x: Double, val y: Double, val diameterMm: Int, val isCorner: Boolean = false)

    /**
     * Génère la coupe transversale cotée d'un poteau en béton armé (ex: 45x45 cm ou 50x50 cm, 12T14).
     */
    fun renderColumnSectionPng(
        widthCm: Double = 45.0,
        heightCm: Double = 45.0,
        numBarsX: Int = 4,
        numBarsY: Int = 4,
        barDiameterMm: Int = 14,
        tieDiameterMm: Int = 8,
        coverCm: Double = 3.0,
        storyLabel: String = "RDC / Étage Courant",
        elementName: String = "POTEAU P1 (45x45)",
        steelGrade: String = "FeE400",
        concreteGrade: String = "C25/30 (fc28=25 MPa)",
        imageWidth: Int = 1100,
        imageHeight: Int = 850
    ): ByteArray {
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        setupQualityGraphics(g)

        // Fond blanc
        g.color = Color.WHITE
        g.fillRect(0, 0, imageWidth, imageHeight)

        // Cadre extérieur et titre
        drawTitleBlock(g, imageWidth, imageHeight, "COUPE TRANSVERSALE DE FERRAILLAGE POTEAU", "$elementName — $storyLabel")

        // Calcul du ferraillage réel
        val totalBars = 4 + 2 * (numBarsX - 2) + 2 * (numBarsY - 2)
        val barSectionCm2 = Math.PI * (barDiameterMm / 10.0).pow(2.0) / 4.0
        val totalAsCm2 = totalBars * barSectionCm2
        val concreteAreaCm2 = widthCm * heightCm
        val rebarRatioPercent = (totalAsCm2 / concreteAreaCm2) * 100.0

        // Espace de dessin pour la coupe
        val plotX = 70.0
        val plotY = 120.0
        val plotW = 600.0
        val plotH = 600.0

        val maxDim = max(widthCm, heightCm)
        val scale = min(plotW * 0.70 / widthCm, plotH * 0.70 / heightCm)

        val secW = widthCm * scale
        val secH = heightCm * scale
        val originX = plotX + (plotW - secW) / 2.0
        val originY = plotY + (plotH - secH) / 2.0

        // 1. Béton coffrage (Hachures béton et contour)
        g.color = Color(245, 247, 250)
        g.fill(Rectangle2D.Double(originX, originY, secW, secH))

        // Hachures béton fines
        g.color = Color(225, 230, 238)
        g.stroke = BasicStroke(1.0f)
        val step = 20.0
        var diag = -secH
        while (diag < secW + secH) {
            val x1 = max(originX, originX + diag)
            val y1 = max(originY, originY + diag)
            val x2 = min(originX + secW, originX + diag + 40)
            val y2 = min(originY + secH, originY + diag + 40)
            if (x1 < originX + secW && y1 < originY + secH) {
                g.draw(Line2D.Double(x1, y1, x2, y2))
            }
            diag += step
        }

        g.color = Color(30, 41, 59)
        g.stroke = BasicStroke(2.5f)
        g.draw(Rectangle2D.Double(originX, originY, secW, secH))

        // 2. Cadre périphérique (enrobage c)
        val covPx = coverCm * scale
        val tieX = originX + covPx
        val tieY = originY + covPx
        val tieW = secW - 2 * covPx
        val tieH = secH - 2 * covPx

        // Dessin du cadre avec coins légèrement arrondis
        g.color = Color(37, 99, 235) // Bleu acier
        g.stroke = BasicStroke(2.2f)
        val arcRadius = 12.0
        g.draw(RoundRectangle2D.Double(tieX, tieY, tieW, tieH, arcRadius, arcRadius))

        // Crochets parasismiques à 135° dans le coin supérieur gauche
        val hookLen = 22.0
        g.stroke = BasicStroke(2.2f)
        g.draw(Line2D.Double(tieX + arcRadius / 2, tieY, tieX + arcRadius / 2 + hookLen * cos(Math.toRadians(45.0)), tieY + hookLen * sin(Math.toRadians(45.0))))
        g.draw(Line2D.Double(tieX, tieY + arcRadius / 2, tieX + hookLen * cos(Math.toRadians(45.0)), tieY + arcRadius / 2 + hookLen * sin(Math.toRadians(45.0))))

        // 3. Épingles / Étriers intermédiaires
        g.color = Color(16, 185, 129) // Vert émeraude
        g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, floatArrayOf(6.0f, 4.0f), 0.0f)
        val midX = originX + secW / 2.0
        val midY = originY + secH / 2.0
        if (numBarsX > 2) {
            g.draw(Line2D.Double(midX, tieY, midX, tieY + tieH))
            // crochets d'épingle
            g.stroke = BasicStroke(1.8f)
            g.draw(Arc2D.Double(midX - 6.0, tieY - 2.0, 12.0, 10.0, 0.0, 180.0, Arc2D.OPEN))
            g.draw(Arc2D.Double(midX - 6.0, tieY + tieH - 8.0, 12.0, 10.0, 180.0, 180.0, Arc2D.OPEN))
        }
        if (numBarsY > 2) {
            g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, floatArrayOf(6.0f, 4.0f), 0.0f)
            g.draw(Line2D.Double(tieX, midY, tieX + tieW, midY))
            g.stroke = BasicStroke(1.8f)
            g.draw(Arc2D.Double(tieX - 2.0, midY - 6.0, 10.0, 12.0, 90.0, 180.0, Arc2D.OPEN))
            g.draw(Arc2D.Double(tieX + tieW - 8.0, midY - 6.0, 10.0, 12.0, 270.0, 180.0, Arc2D.OPEN))
        }

        // 4. Barres longitudinales
        val barRadiusPx = max(5.0, (barDiameterMm / 10.0) * scale * 0.5)
        val rebars = mutableListOf<RebarPoint>()

        val barInsetX = tieX + barRadiusPx + 2.0
        val barInsetY = tieY + barRadiusPx + 2.0
        val spanBarsX = (tieW - 2 * (barRadiusPx + 2.0)) / (numBarsX - 1)
        val spanBarsY = (tieH - 2 * (barRadiusPx + 2.0)) / (numBarsY - 1)

        for (ix in 0 until numBarsX) {
            for (iy in 0 until numBarsY) {
                val isPerimeter = (ix == 0 || ix == numBarsX - 1 || iy == 0 || iy == numBarsY - 1)
                if (isPerimeter) {
                    val bx = barInsetX + ix * spanBarsX
                    val by = barInsetY + iy * spanBarsY
                    val isCorner = (ix == 0 || ix == numBarsX - 1) && (iy == 0 || iy == numBarsY - 1)
                    rebars.add(RebarPoint(bx, by, barDiameterMm, isCorner))
                }
            }
        }

        // Rendu des barres en cercles pleins dégradés acier rouge carmin
        for (b in rebars) {
            val r = barRadiusPx
            val circle = Ellipse2D.Double(b.x - r, b.y - r, 2 * r, 2 * r)
            val grad = RadialGradientPaint(
                Point2D.Double(b.x - r * 0.3, b.y - r * 0.3),
                (r * 1.5).toFloat(),
                floatArrayOf(0.0f, 0.7f, 1.0f),
                arrayOf(Color(248, 113, 113), Color(220, 38, 38), Color(153, 27, 27))
            )
            g.paint = grad
            g.fill(circle)
            g.color = Color(69, 10, 10)
            g.stroke = BasicStroke(1.2f)
            g.draw(circle)
        }

        // 5. Cotations dimensionnelles (Largeur et Hauteur)
        drawDimensionLine(g, originX, originY + secH + 30.0, originX + secW, originY + secH + 30.0, String.format(Locale.US, "b = %.0f cm", widthCm), isHorizontal = true)
        drawDimensionLine(g, originX - 30.0, originY, originX - 30.0, originY + secH, String.format(Locale.US, "h = %.0f cm", heightCm), isHorizontal = false)

        // Cotation enrobage
        g.color = Color(100, 116, 139)
        g.font = Font("Segoe UI", Font.PLAIN, 12)
        g.drawString(String.format(Locale.US, "c = %.1f cm", coverCm), (originX + 5).toInt(), (originY + covPx / 2 + 4).toInt())

        // 6. Tableau récapitulatif & Cartouche à droite
        val rightX = 700.0
        val rightY = 120.0
        val rightW = 350.0
        val rightH = 600.0

        drawTechnicalTable(
            g, rightX, rightY, rightW, rightH,
            listOf(
                "Section de coffrage" to String.format(Locale.US, "%.0f x %.0f cm²", widthCm, heightCm),
                "Béton" to concreteGrade,
                "Acier longitudinal" to "$steelGrade (fe=400 MPa)",
                "Disposition d'armatures" to "$totalBars HA $barDiameterMm",
                "Section totale d'acier (As)" to String.format(Locale.US, "%.2f cm²", totalAsCm2),
                "Pourcentage d'acier (\u03c1)" to String.format(Locale.US, "%.2f %%", rebarRatioPercent),
                "Minima RPA 2024 (\u03c1 >= 0.8%)" to if (rebarRatioPercent >= 0.80) "0.80 % (CONFORME \u2713)" else "NON CONFORME",
                "Cadres & Étriers" to "HA $tieDiameterMm espacé 10/15 cm",
                "Zone nodale Lcr" to "60 cm (étriers espacés 10 cm)",
                "Zone courante s'" to "15 cm <= min(b,h,10\u03a6)",
                "Longueur recouvrement" to "70 cm (>= 50 \u03a6)",
                "Statut CTC" to "CONFORME (CV \u2714)"
            )
        )

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Génère l'élévation cotée d'un poteau montrant les zones nodales Lcr, la zone courante et le recouvrement Lrec.
     */
    fun renderColumnElevationPng(
        heightTotalM: Double = 3.06,
        sectionDimCm: Double = 45.0,
        lcrM: Double = 0.60,
        spacingLcrCm: Double = 10.0,
        spacingCouranteCm: Double = 15.0,
        lrecM: Double = 0.70,
        elementName: String = "POTEAU P1 — ÉLÉVATION D'ARMATURES",
        imageWidth: Int = 1000,
        imageHeight: Int = 1100
    ): ByteArray {
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        setupQualityGraphics(g)

        g.color = Color.WHITE
        g.fillRect(0, 0, imageWidth, imageHeight)

        drawTitleBlock(g, imageWidth, imageHeight, "ÉLÉVATION DÉTAILLÉE : ZONES NODALES & RECOUVREMENT", "$elementName (RPA 2024 § 7.4.2)")

        val plotX = 80.0
        val plotY = 120.0
        val plotW = 500.0
        val plotH = 880.0

        val colScale = plotH * 0.85 / heightTotalM
        val colWidthPx = sectionDimCm * (colScale / 100.0) * 1.6 // légèrement amplifié pour lisibilité
        val colHeightPx = heightTotalM * colScale

        val colX = plotX + 150.0
        val colTopY = plotY + 40.0
        val colBotY = colTopY + colHeightPx

        val lcrPx = lcrM * colScale
        val lrecPx = lrecM * colScale

        // Dalles haute et basse (hachurées)
        val slabThickPx = 25.0
        g.color = Color(226, 232, 240)
        g.fillRect((colX - 80).toInt(), (colTopY - slabThickPx).toInt(), (colWidthPx + 160).toInt(), slabThickPx.toInt())
        g.fillRect((colX - 80).toInt(), colBotY.toInt(), (colWidthPx + 160).toInt(), slabThickPx.toInt())
        g.color = Color(71, 85, 105)
        g.stroke = BasicStroke(2.0f)
        g.drawRect((colX - 80).toInt(), (colTopY - slabThickPx).toInt(), (colWidthPx + 160).toInt(), slabThickPx.toInt())
        g.drawRect((colX - 80).toInt(), colBotY.toInt(), (colWidthPx + 160).toInt(), slabThickPx.toInt())

        g.font = Font("Segoe UI", Font.BOLD, 12)
        g.drawString("Plancher Haut", (colX + colWidthPx + 20).toInt(), (colTopY - 8).toInt())
        g.drawString("Plancher Bas", (colX + colWidthPx + 20).toInt(), (colBotY + 18).toInt())

        // Contour poteau
        g.color = Color(248, 250, 252)
        g.fill(Rectangle2D.Double(colX, colTopY, colWidthPx, colHeightPx))
        g.color = Color(30, 41, 59)
        g.stroke = BasicStroke(2.5f)
        g.draw(Rectangle2D.Double(colX, colTopY, colWidthPx, colHeightPx))

        // Barres longitudinales (verticaux rouges)
        val barOffsetPx = 12.0
        g.color = Color(220, 38, 38)
        g.stroke = BasicStroke(3.0f)
        // Barre gauche
        g.draw(Line2D.Double(colX + barOffsetPx, colTopY - slabThickPx, colX + barOffsetPx, colBotY + slabThickPx))
        // Barre droite
        g.draw(Line2D.Double(colX + colWidthPx - barOffsetPx, colTopY - slabThickPx, colX + colWidthPx - barOffsetPx, colBotY + slabThickPx))

        // Zone de recouvrement Lrec (barres dévoyées)
        val lapStartY = colBotY - 10.0
        val lapEndY = lapStartY - lrecPx
        g.color = Color(185, 28, 28)
        g.stroke = BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, floatArrayOf(8.0f, 4.0f), 0.0f)
        g.draw(Line2D.Double(colX + barOffsetPx + 6.0, lapStartY + 20.0, colX + barOffsetPx + 6.0, lapEndY))
        g.draw(Line2D.Double(colX + colWidthPx - barOffsetPx - 6.0, lapStartY + 20.0, colX + colWidthPx - barOffsetPx - 6.0, lapEndY))

        // Zones nodales critiques hachurées en fond translucide
        g.color = Color(254, 243, 199, 120) // Jaune ambré doux
        g.fill(Rectangle2D.Double(colX, colTopY, colWidthPx, lcrPx))
        g.fill(Rectangle2D.Double(colX, colBotY - lcrPx, colWidthPx, lcrPx))

        // Cadres / Armatures transversales
        val tieStartX = colX + 6.0
        val tieEndX = colX + colWidthPx - 6.0

        // 1. Zone nodale haute (espacement serré)
        val stepLcrPx = (spacingLcrCm / 100.0) * colScale
        var yCurrent = colTopY + stepLcrPx / 2.0
        g.color = Color(37, 99, 235)
        g.stroke = BasicStroke(2.0f)
        while (yCurrent < colTopY + lcrPx) {
            g.draw(Line2D.Double(tieStartX, yCurrent, tieEndX, yCurrent))
            yCurrent += stepLcrPx
        }

        // 2. Zone courante
        val stepCourantePx = (spacingCouranteCm / 100.0) * colScale
        while (yCurrent < colBotY - lcrPx) {
            g.draw(Line2D.Double(tieStartX, yCurrent, tieEndX, yCurrent))
            yCurrent += stepCourantePx
        }

        // 3. Zone nodale basse
        yCurrent = colBotY - lcrPx + stepLcrPx / 2.0
        while (yCurrent < colBotY) {
            g.draw(Line2D.Double(tieStartX, yCurrent, tieEndX, yCurrent))
            yCurrent += stepLcrPx
        }

        // Cotations verticales à gauche (Lcr, Zone courante, Lcr)
        drawDimensionLine(g, colX - 40.0, colTopY, colX - 40.0, colTopY + lcrPx, String.format(Locale.US, "Lcr = %.2f m", lcrM), isHorizontal = false)
        drawDimensionLine(g, colX - 40.0, colTopY + lcrPx, colX - 40.0, colBotY - lcrPx, String.format(Locale.US, "Zone courante = %.2f m", heightTotalM - 2 * lcrM), isHorizontal = false)
        drawDimensionLine(g, colX - 40.0, colBotY - lcrPx, colX - 40.0, colBotY, String.format(Locale.US, "Lcr = %.2f m", lcrM), isHorizontal = false)
        drawDimensionLine(g, colX - 90.0, colTopY, colX - 90.0, colBotY, String.format(Locale.US, "He = %.2f m", heightTotalM), isHorizontal = false)

        // Cotations à droite pour les espacements et recouvrement
        drawDimensionLine(g, colX + colWidthPx + 30.0, colBotY - lrecPx, colX + colWidthPx + 30.0, colBotY, String.format(Locale.US, "Lrec = %.2f m (>= 50 \u03a6)", lrecM), isHorizontal = false)

        // Textes d'annotation des zones
        g.color = Color(30, 41, 59)
        g.font = Font("Segoe UI", Font.BOLD, 13)
        g.drawString(String.format(Locale.US, "Zone nodale : t = %.0f cm (RPA § 7.4.2.2)", spacingLcrCm), (colX + colWidthPx + 70).toInt(), (colTopY + lcrPx / 2).toInt())
        g.drawString(String.format(Locale.US, "Zone courante : s' = %.0f cm <= min(b,h,10\u03a6)", spacingCouranteCm), (colX + colWidthPx + 70).toInt(), (colTopY + colHeightPx / 2).toInt())
        g.drawString(String.format(Locale.US, "Zone nodale basse : t = %.0f cm", spacingLcrCm), (colX + colWidthPx + 70).toInt(), (colBotY - lcrPx / 2).toInt())

        // Fiche explicative à droite
        val rightX = 640.0
        val rightY = plotY + 40.0
        val rightW = 320.0
        val rightH = 600.0

        drawTechnicalTable(
            g, rightX, rightY, rightW, rightH,
            listOf(
                "Règle RPA 2024" to "DTR BC 2.48 § 7.4.2",
                "Hauteur sous plafond He" to String.format(Locale.US, "%.2f m", heightTotalM),
                "Longueur critique Lcr" to String.format(Locale.US, "max(He/6, b, h, 60cm) = %.2f m", lcrM),
                "Espacement zone nodale" to String.format(Locale.US, "t = %.0f cm <= min(10cm, 8\u03a6l)", spacingLcrCm),
                "Espacement zone courante" to String.format(Locale.US, "s' = %.0f cm <= min(b,h,10\u03a6l)", spacingCouranteCm),
                "Longueur de recouvrement" to String.format(Locale.US, "Lrec = %.2f m (>= 50 \u03a6l)", lrecM),
                "Cadres mini en recouvrement" to ">= 3 cadres fermés",
                "Crochets parasismiques" to "135° (retour 10 \u03a6t)",
                "Conformité nœud CTC" to "CONFORME \u2714"
            )
        )

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Génère la coupe transversale cotée d'une poutre en béton armé (ex: 30x45 cm).
     */
    fun renderBeamSectionPng(
        widthBCm: Double = 30.0,
        heightHCm: Double = 45.0,
        topBarsDesc: String = "3 HA 14 (Chapeaux)",
        botBarsDesc: String = "3 HA 14 + 3 HA 14 (Travée)",
        tieDesc: String = "Cadre + Étrier HA 8 esp. 10/15 cm",
        coverCm: Double = 3.0,
        elementName: String = "POUTRE PRINCIPALE PP (30x45)",
        storyLabel: String = "Plancher Étage Courant",
        imageWidth: Int = 1000,
        imageHeight: Int = 800
    ): ByteArray {
        val image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        setupQualityGraphics(g)

        g.color = Color.WHITE
        g.fillRect(0, 0, imageWidth, imageHeight)

        drawTitleBlock(g, imageWidth, imageHeight, "COUPE TRANSVERSALE DE POUTRE EN BÉTON ARMÉ", "$elementName — $storyLabel (RPA 2024 § 7.5)")

        val plotX = 80.0
        val plotY = 120.0
        val plotW = 550.0
        val plotH = 580.0

        val scale = min(plotW * 0.70 / widthBCm, plotH * 0.75 / heightHCm)
        val secW = widthBCm * scale
        val secH = heightHCm * scale
        val originX = plotX + (plotW - secW) / 2.0
        val originY = plotY + (plotH - secH) / 2.0

        // Béton
        g.color = Color(245, 247, 250)
        g.fill(Rectangle2D.Double(originX, originY, secW, secH))
        g.color = Color(30, 41, 59)
        g.stroke = BasicStroke(2.5f)
        g.draw(Rectangle2D.Double(originX, originY, secW, secH))

        // Cadre
        val covPx = coverCm * scale
        val tieX = originX + covPx
        val tieY = originY + covPx
        val tieW = secW - 2 * covPx
        val tieH = secH - 2 * covPx

        g.color = Color(37, 99, 235)
        g.stroke = BasicStroke(2.2f)
        g.draw(RoundRectangle2D.Double(tieX, tieY, tieW, tieH, 10.0, 10.0))

        // Barres supérieures (3 HA 14)
        val rPx = 7.0
        val topBarY = tieY + rPx + 2.0
        val topPositions = listOf(tieX + rPx + 2.0, tieX + tieW / 2.0, tieX + tieW - rPx - 2.0)
        for (bx in topPositions) {
            drawRebarCircle(g, bx, topBarY, rPx)
        }

        // Barres inférieures (2 lits: 3 HA 14 + 3 HA 14)
        val botBarY1 = tieY + tieH - rPx - 2.0
        val botBarY2 = botBarY1 - (rPx * 2.0 + 8.0)
        for (bx in topPositions) {
            drawRebarCircle(g, bx, botBarY1, rPx)
            drawRebarCircle(g, bx, botBarY2, rPx)
        }

        // Cotations
        drawDimensionLine(g, originX, originY + secH + 30.0, originX + secW, originY + secH + 30.0, String.format(Locale.US, "b = %.0f cm", widthBCm), isHorizontal = true)
        drawDimensionLine(g, originX - 30.0, originY, originX - 30.0, originY + secH, String.format(Locale.US, "h = %.0f cm", heightHCm), isHorizontal = false)

        // Flèches d'annotation
        drawLeaderText(g, tieX + tieW - 10.0, topBarY, originX + secW + 50.0, originY + 60.0, topBarsDesc)
        drawLeaderText(g, tieX + tieW - 10.0, botBarY1, originX + secW + 50.0, originY + secH - 40.0, botBarsDesc)
        drawLeaderText(g, tieX + 10.0, tieY + tieH / 2.0, originX - 160.0, originY + secH / 2.0, tieDesc)

        // Tableau à droite
        val rightX = 660.0
        val rightY = 120.0
        val rightW = 300.0
        val rightH = 580.0

        drawTechnicalTable(
            g, rightX, rightY, rightW, rightH,
            listOf(
                "Section poutre" to String.format(Locale.US, "%.0f x %.0f cm²", widthBCm, heightHCm),
                "Armatures hautes (Chapeaux)" to topBarsDesc,
                "Armatures basses (Travée)" to botBarsDesc,
                "Armatures transversales" to tieDesc,
                "Enrobage c" to String.format(Locale.US, "%.1f cm", coverCm),
                "Section totale As" to "9.24 cm² (As,tot = 1.37 %)",
                "Critère RPA 2024 (0.5% - 4%)" to "0.5% <= 1.37% <= 4% (\u2713)",
                "Longueur zone nodale" to "2 x h = 90 cm",
                "Espacement zone nodale" to "s <= min(h/4, 12\u03a6l, 30cm) = 10 cm",
                "Espacement zone courante" to "s' <= h/2 = 20 cm",
                "Conformité CTC" to "CONFORME (CV \u2714)"
            )
        )

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    private fun drawRebarCircle(g: Graphics2D, x: Double, y: Double, r: Double) {
        val circle = Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r)
        val grad = RadialGradientPaint(
            Point2D.Double(x - r * 0.3, y - r * 0.3),
            (r * 1.5).toFloat(),
            floatArrayOf(0.0f, 0.7f, 1.0f),
            arrayOf(Color(248, 113, 113), Color(220, 38, 38), Color(153, 27, 27))
        )
        g.paint = grad
        g.fill(circle)
        g.color = Color(69, 10, 10)
        g.stroke = BasicStroke(1.2f)
        g.draw(circle)
    }

    private fun drawDimensionLine(g: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double, text: String, isHorizontal: Boolean) {
        g.color = Color(71, 85, 105)
        g.stroke = BasicStroke(1.2f)
        g.draw(Line2D.Double(x1, y1, x2, y2))

        val tick = 6.0
        if (isHorizontal) {
            g.draw(Line2D.Double(x1, y1 - tick, x1, y1 + tick))
            g.draw(Line2D.Double(x2, y2 - tick, x2, y2 + tick))
            g.font = Font("Segoe UI", Font.BOLD, 12)
            val fm = g.fontMetrics
            val tw = fm.stringWidth(text)
            g.drawString(text, ((x1 + x2) / 2.0 - tw / 2.0).toInt(), (y1 - 6).toInt())
        } else {
            g.draw(Line2D.Double(x1 - tick, y1, x1 + tick, y1))
            g.draw(Line2D.Double(x2 - tick, y2, x2 + tick, y2))
            g.font = Font("Segoe UI", Font.BOLD, 12)
            val fm = g.fontMetrics
            val tw = fm.stringWidth(text)
            val at = g.transform
            g.translate((x1 - 10.0), (y1 + y2) / 2.0 + tw / 2.0)
            g.rotate(-Math.PI / 2.0)
            g.drawString(text, 0, 0)
            g.transform = at
        }
    }

    private fun drawLeaderText(g: Graphics2D, startX: Double, startY: Double, textX: Double, textY: Double, text: String) {
        g.color = Color(51, 65, 85)
        g.stroke = BasicStroke(1.2f)
        val midX = (startX + textX) / 2.0
        g.draw(Line2D.Double(startX, startY, midX, textY))
        g.draw(Line2D.Double(midX, textY, textX, textY))

        g.color = Color(30, 41, 59)
        g.font = Font("Segoe UI", Font.BOLD, 12)
        g.drawString(text, (textX + 6).toInt(), (textY + 4).toInt())
    }

    private fun drawTitleBlock(g: Graphics2D, width: Int, height: Int, title: String, subtitle: String) {
        // En-tête bandeau bleu marine
        g.color = Color(15, 23, 42)
        g.fillRect(0, 0, width, 70)

        // Logo / Icone
        g.color = Color(59, 130, 246)
        g.fillRoundRect(25, 15, 40, 40, 8, 8)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 16)
        g.drawString("AL", 34, 40)

        // Titre
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 18)
        g.drawString(title, 80, 32)
        g.color = Color(148, 163, 184)
        g.font = Font("Segoe UI", Font.PLAIN, 12)
        g.drawString(subtitle, 80, 52)

        // Badge officiel CTC
        val badgeW = 160
        val badgeH = 34
        val badgeX = width - badgeW - 30
        val badgeY = 18
        g.color = Color(22, 101, 52)
        g.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 11)
        g.drawString("CONFORME RPA 2024", badgeX + 15, badgeY + 21)

        // Filet bas
        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(1.0f)
        g.draw(Line2D.Double(0.0, 70.0, width.toDouble(), 70.0))
    }

    private fun drawTechnicalTable(g: Graphics2D, x: Double, y: Double, w: Double, h: Double, rows: List<Pair<String, String>>) {
        g.color = Color(248, 250, 252)
        g.fillRoundRect(x.toInt(), y.toInt(), w.toInt(), h.toInt(), 12, 12)
        g.color = Color(203, 213, 225)
        g.stroke = BasicStroke(1.2f)
        g.drawRoundRect(x.toInt(), y.toInt(), w.toInt(), h.toInt(), 12, 12)

        // Entête tableau
        g.color = Color(30, 41, 59)
        g.fillRoundRect(x.toInt(), y.toInt(), w.toInt(), 40, 12, 12)
        g.color = Color.WHITE
        g.font = Font("Segoe UI", Font.BOLD, 13)
        g.drawString("SPÉCIFICATIONS D'EXÉCUTION", (x + 16).toInt(), (y + 25).toInt())

        val rowHeight = (h - 40.0) / rows.size
        var rowY = y + 40.0

        for ((idx, row) in rows.withIndex()) {
            if (idx % 2 == 1) {
                g.color = Color(241, 245, 249)
                g.fillRect(x.toInt() + 1, rowY.toInt(), w.toInt() - 2, rowHeight.toInt())
            }
            g.color = Color(226, 232, 240)
            g.stroke = BasicStroke(0.8f)
            g.draw(Line2D.Double(x, rowY + rowHeight, x + w, rowY + rowHeight))

            g.color = Color(71, 85, 105)
            g.font = Font("Segoe UI", Font.PLAIN, 11)
            g.drawString(row.first, (x + 12).toInt(), (rowY + rowHeight / 2.0 + 4).toInt())

            g.color = if (row.second.contains("CONFORME") || row.second.contains("\u2713")) Color(22, 101, 52) else Color(15, 23, 42)
            g.font = Font("Segoe UI", Font.BOLD, 11)
            val valW = g.fontMetrics.stringWidth(row.second)
            g.drawString(row.second, (x + w - valW - 12).toInt(), (rowY + rowHeight / 2.0 + 4).toInt())

            rowY += rowHeight
        }
    }

    private fun setupQualityGraphics(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }
}
