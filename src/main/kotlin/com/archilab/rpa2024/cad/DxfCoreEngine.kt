package com.archilab.rpa2024.cad

import java.io.File
import java.io.StringWriter
import java.util.Locale
import kotlin.math.*

/**
 * Standard de calques pour le dessin CAO génie civil et parasismique RPA 2024.
 */
enum class CadLayer(
    val layerName: String,
    val colorAci: Int,
    val linetype: String,
    val lineweightHundredthMm: Int,
    val description: String
) {
    AXES("01_AXES", 8, "CENTER", 13, "Lignes d'axes de trames et files"),
    BULLES_AXES("02_BULLES_AXES", 2, "CONTINUOUS", 25, "Bulles de repérage des axes"),
    COTATIONS("03_COTATIONS", 3, "CONTINUOUS", 18, "Lignes de cote et cotations"),
    COFFRAGE_POTEAUX("04_COFFRAGE_POTEAUX", 4, "CONTINUOUS", 35, "Contour de coffrage des poteaux"),
    COFFRAGE_VOILES("05_COFFRAGE_VOILES", 6, "CONTINUOUS", 40, "Contour de coffrage des voiles"),
    COFFRAGE_POUTRES("06_COFFRAGE_POUTRES", 3, "CONTINUOUS", 30, "Contour de coffrage des poutres et chaînages"),
    COFFRAGE_SEMELLES("07_COFFRAGE_SEMELLES", 5, "CONTINUOUS", 30, "Contour de coffrage des semelles et fondations"),
    ACIER_LONGITUDINAL("08_ACIER_LONGITUDINAL", 1, "CONTINUOUS", 50, "Armatures longitudinales principales"),
    ACIER_TRANSVERSAL("09_ACIER_TRANSVERSAL", 30, "CONTINUOUS", 25, "Cadres, étriers et épingles à 135°"),
    TEXTES_ET_REPERES("10_TEXTES_ET_REPERES", 7, "CONTINUOUS", 20, "Textes, annotations et repères"),
    CARTOUCHE_CTC("11_CARTOUCHE_CTC", 7, "CONTINUOUS", 25, "Cadre de planche et cartouche CTC")
}

/**
 * Point 2D pour le dessin géométrique.
 */
data class CadPoint(val x: Double, val y: Double) {
    fun offset(dx: Double, dy: Double) = CadPoint(x + dx, y + dy)
}

/**
 * Alignement horizontal du texte DXF.
 */
enum class CadHAlign(val code72: Int) {
    LEFT(0),
    CENTER(1),
    RIGHT(2)
}

/**
 * Alignement vertical du texte DXF.
 */
enum class CadVAlign(val code73: Int) {
    BASELINE(0),
    BOTTOM(1),
    MIDDLE(2),
    TOP(3)
}

/**
 * Moteur d'écriture CAO natif AutoCAD DXF (Standard R2010+ / AC1024) en pur Kotlin.
 * Sans aucune dépendance C++ externe, garantit une portabilité 100% multi-plateforme.
 */
class DxfCoreEngine {

    private val entitiesBuffer = StringBuilder()
    private var entityHandleCounter = 0x100

    private fun nextHandle(): String {
        entityHandleCounter++
        return entityHandleCounter.toString(16).uppercase()
    }

    private fun formatDouble(value: Double): String {
        return String.format(Locale.US, "%.4f", value)
    }

    /**
     * Ajoute un segment de droite.
     */
    fun addLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        layer: CadLayer,
        colorOverride: Int? = null,
        linetypeOverride: String? = null
    ) {
        entitiesBuffer.appendLine("  0\nLINE")
        entitiesBuffer.appendLine("  5\n${nextHandle()}")
        entitiesBuffer.appendLine("  8\n${layer.layerName}")
        colorOverride?.let { entitiesBuffer.appendLine(" 62\n$it") }
        linetypeOverride?.let { entitiesBuffer.appendLine("  6\n$it") }
        entitiesBuffer.appendLine(" 10\n${formatDouble(x1)}")
        entitiesBuffer.appendLine(" 20\n${formatDouble(y1)}")
        entitiesBuffer.appendLine(" 30\n0.0")
        entitiesBuffer.appendLine(" 11\n${formatDouble(x2)}")
        entitiesBuffer.appendLine(" 21\n${formatDouble(y2)}")
        entitiesBuffer.appendLine(" 31\n0.0")
    }

    /**
     * Ajoute un rectangle délimité par ses deux coins opposés.
     */
    fun addRectangle(
        xMin: Double,
        yMin: Double,
        xMax: Double,
        yMax: Double,
        layer: CadLayer,
        constantWidth: Double = 0.0
    ) {
        val pts = listOf(
            CadPoint(xMin, yMin),
            CadPoint(xMax, yMin),
            CadPoint(xMax, yMax),
            CadPoint(xMin, yMax)
        )
        addLwPolyline(pts, isClosed = true, layer = layer, constantWidth = constantWidth)
    }

    /**
     * Ajoute une polyligne 2D légère (LWPOLYLINE).
     */
    fun addLwPolyline(
        points: List<CadPoint>,
        isClosed: Boolean,
        layer: CadLayer,
        constantWidth: Double = 0.0,
        colorOverride: Int? = null
    ) {
        if (points.isEmpty()) return
        entitiesBuffer.appendLine("  0\nLWPOLYLINE")
        entitiesBuffer.appendLine("  5\n${nextHandle()}")
        entitiesBuffer.appendLine("  8\n${layer.layerName}")
        colorOverride?.let { entitiesBuffer.appendLine(" 62\n$it") }
        entitiesBuffer.appendLine(" 90\n${points.size}")
        entitiesBuffer.appendLine(" 70\n${if (isClosed) 1 else 0}")
        if (constantWidth > 0.0) {
            entitiesBuffer.appendLine(" 43\n${formatDouble(constantWidth)}")
        }
        for (pt in points) {
            entitiesBuffer.appendLine(" 10\n${formatDouble(pt.x)}")
            entitiesBuffer.appendLine(" 20\n${formatDouble(pt.y)}")
        }
    }

    /**
     * Ajoute un cercle.
     */
    fun addCircle(
        cx: Double,
        cy: Double,
        radius: Double,
        layer: CadLayer,
        colorOverride: Int? = null
    ) {
        entitiesBuffer.appendLine("  0\nCIRCLE")
        entitiesBuffer.appendLine("  5\n${nextHandle()}")
        entitiesBuffer.appendLine("  8\n${layer.layerName}")
        colorOverride?.let { entitiesBuffer.appendLine(" 62\n$it") }
        entitiesBuffer.appendLine(" 10\n${formatDouble(cx)}")
        entitiesBuffer.appendLine(" 20\n${formatDouble(cy)}")
        entitiesBuffer.appendLine(" 30\n0.0")
        entitiesBuffer.appendLine(" 40\n${formatDouble(radius)}")
    }

    /**
     * Ajoute un arc de cercle.
     */
    fun addArc(
        cx: Double,
        cy: Double,
        radius: Double,
        startAngleDeg: Double,
        endAngleDeg: Double,
        layer: CadLayer
    ) {
        entitiesBuffer.appendLine("  0\nARC")
        entitiesBuffer.appendLine("  5\n${nextHandle()}")
        entitiesBuffer.appendLine("  8\n${layer.layerName}")
        entitiesBuffer.appendLine(" 10\n${formatDouble(cx)}")
        entitiesBuffer.appendLine(" 20\n${formatDouble(cy)}")
        entitiesBuffer.appendLine(" 30\n0.0")
        entitiesBuffer.appendLine(" 40\n${formatDouble(radius)}")
        entitiesBuffer.appendLine(" 50\n${formatDouble(startAngleDeg)}")
        entitiesBuffer.appendLine(" 51\n${formatDouble(endAngleDeg)}")
    }

    /**
     * Ajoute un texte simple (TEXT).
     */
    fun addText(
        x: Double,
        y: Double,
        height: Double,
        text: String,
        layer: CadLayer,
        rotationDeg: Double = 0.0,
        hAlign: CadHAlign = CadHAlign.LEFT,
        vAlign: CadVAlign = CadVAlign.BASELINE,
        colorOverride: Int? = null
    ) {
        entitiesBuffer.appendLine("  0\nTEXT")
        entitiesBuffer.appendLine("  5\n${nextHandle()}")
        entitiesBuffer.appendLine("  8\n${layer.layerName}")
        colorOverride?.let { entitiesBuffer.appendLine(" 62\n$it") }
        entitiesBuffer.appendLine(" 10\n${formatDouble(x)}")
        entitiesBuffer.appendLine(" 20\n${formatDouble(y)}")
        entitiesBuffer.appendLine(" 30\n0.0")
        entitiesBuffer.appendLine(" 40\n${formatDouble(height)}")
        entitiesBuffer.appendLine("  1\n$text")
        if (rotationDeg != 0.0) {
            entitiesBuffer.appendLine(" 50\n${formatDouble(rotationDeg)}")
        }
        if (hAlign != CadHAlign.LEFT || vAlign != CadVAlign.BASELINE) {
            entitiesBuffer.appendLine(" 72\n${hAlign.code72}")
            entitiesBuffer.appendLine(" 11\n${formatDouble(x)}")
            entitiesBuffer.appendLine(" 21\n${formatDouble(y)}")
            entitiesBuffer.appendLine(" 31\n0.0")
            entitiesBuffer.appendLine(" 73\n${vAlign.code73}")
        }
    }

    /**
     * Ajoute une hachure SOLID sur un contour polygonal fermé.
     */
    fun addSolidHatch(
        boundaryPoints: List<CadPoint>,
        layer: CadLayer,
        colorOverride: Int? = null
    ) {
        if (boundaryPoints.size < 3) return
        entitiesBuffer.appendLine("  0\nHATCH")
        entitiesBuffer.appendLine("  5\n${nextHandle()}")
        entitiesBuffer.appendLine("  8\n${layer.layerName}")
        colorOverride?.let { entitiesBuffer.appendLine(" 62\n$it") }
        entitiesBuffer.appendLine(" 10\n0.0\n 20\n0.0\n 30\n0.0")
        entitiesBuffer.appendLine("210\n0.0\n220\n0.0\n230\n1.0")
        entitiesBuffer.appendLine("  2\nSOLID")
        entitiesBuffer.appendLine(" 70\n1") // 1 = Solid fill
        entitiesBuffer.appendLine(" 71\n0")
        entitiesBuffer.appendLine(" 91\n1") // Nombre de contours
        entitiesBuffer.appendLine(" 92\n2") // 2 = Polyligne
        entitiesBuffer.appendLine(" 72\n0") // Bulge absent
        entitiesBuffer.appendLine(" 73\n1") // Fermé
        entitiesBuffer.appendLine(" 93\n${boundaryPoints.size}")
        for (pt in boundaryPoints) {
            entitiesBuffer.appendLine(" 10\n${formatDouble(pt.x)}")
            entitiesBuffer.appendLine(" 20\n${formatDouble(pt.y)}")
        }
        entitiesBuffer.appendLine(" 97\n0")
    }

    /**
     * Trace une cotation linéaire avec lignes de rappel, ligne de cote, tics d'extrémités et texte.
     */
    fun addLinearDimension(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        offset: Double,
        textOverride: String? = null,
        layer: CadLayer = CadLayer.COTATIONS,
        textHeight: Double = 0.20
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = hypot(dx, dy)
        if (length < 1e-4) return

        // Vecteur unitaire parallèle et perpendiculaire
        val ux = dx / length
        val uy = dy / length
        val nx = -uy
        val ny = ux

        // Ligne de cote décalée
        val dimX1 = x1 + nx * offset
        val dimY1 = y1 + ny * offset
        val dimX2 = x2 + nx * offset
        val dimY2 = y2 + ny * offset

        // Lignes de rappel
        val extensionOvershoot = if (offset >= 0) 0.15 else -0.15
        addLine(x1, y1, dimX1 + nx * extensionOvershoot, dimY1 + ny * extensionOvershoot, layer)
        addLine(x2, y2, dimX2 + nx * extensionOvershoot, dimY2 + ny * extensionOvershoot, layer)

        // Ligne de cote principale
        addLine(dimX1, dimY1, dimX2, dimY2, layer)

        // Tics d'architecture obliques à 45°
        val tickSize = textHeight * 0.50
        val tickDx = (ux + nx) * tickSize * 0.707
        val tickDy = (uy + ny) * tickSize * 0.707
        addLine(dimX1 - tickDx, dimY1 - tickDy, dimX1 + tickDx, dimY1 + tickDy, layer)
        addLine(dimX2 - tickDx, dimY2 - tickDy, dimX2 + tickDx, dimY2 + tickDy, layer)

        // Texte de cote au milieu
        val midX = (dimX1 + dimX2) / 2.0 + nx * (textHeight * 0.70)
        val midY = (dimY1 + dimY2) / 2.0 + ny * (textHeight * 0.70)
        val dimText = textOverride ?: String.format(Locale.US, "%.2f", length)

        var angleDeg = atan2(dy, dx) * 180.0 / PI
        if (angleDeg < -90.0 || angleDeg > 90.0) {
            angleDeg += 180.0
        }

        addText(
            x = midX,
            y = midY,
            height = textHeight,
            text = dimText,
            layer = layer,
            rotationDeg = angleDeg,
            hAlign = CadHAlign.CENTER,
            vAlign = CadVAlign.MIDDLE
        )
    }

    /**
     * Bulle de repère d'armature circulaire avec texte au centre et flèche de renvoi.
     */
    fun addLeaderWithBubble(
        targetX: Double,
        targetY: Double,
        bubbleX: Double,
        bubbleY: Double,
        bubbleRadius: Double,
        bubbleText: String,
        descriptionText: String? = null,
        layer: CadLayer = CadLayer.TEXTES_ET_REPERES
    ) {
        // Ligne de renvoi
        addLine(targetX, targetY, bubbleX, bubbleY, layer)
        // Cercle de bulle
        addCircle(bubbleX, bubbleY, bubbleRadius, CadLayer.BULLES_AXES)
        // Numéro de position au centre
        addText(
            x = bubbleX,
            y = bubbleY,
            height = bubbleRadius * 1.10,
            text = bubbleText,
            layer = layer,
            hAlign = CadHAlign.CENTER,
            vAlign = CadVAlign.MIDDLE
        )

        // Description éventuelle (ex: "4T14 L=3.40m")
        descriptionText?.let {
            val textX = bubbleX + bubbleRadius * 1.40
            val textY = bubbleY
            addText(
                x = textX,
                y = textY,
                height = bubbleRadius * 0.85,
                text = it,
                layer = layer,
                hAlign = CadHAlign.LEFT,
                vAlign = CadVAlign.MIDDLE
            )
        }
    }

    /**
     * Dessine un cadre de planche standard (A1/A0/A3) avec le cartouche réglementaire algérien CTC.
     */
    fun drawTitleBlockCTC(
        originX: Double,
        originY: Double,
        width: Double,
        height: Double,
        projectTitle: String,
        jointName: String,
        wilaya: String,
        drawingTitle: String,
        scaleText: String = "1/50 - 1/20",
        dateText: String = "2026",
        bureauEtudes: String = "ARCHILAB ENGINEERING DZ"
    ) {
        val layer = CadLayer.CARTOUCHE_CTC

        // Cadre extérieur
        addRectangle(originX, originY, originX + width, originY + height, layer, constantWidth = 0.05)
        // Marge intérieure (20mm)
        val m = min(width, height) * 0.015
        addRectangle(originX + m, originY + m, originX + width - m, originY + height - m, layer, constantWidth = 0.02)

        // Cartouche CTC dans le coin inférieur droit (largeur 180mm x hauteur 90mm en échelle relative)
        val tbW = width * 0.28
        val tbH = height * 0.18
        val tbX0 = originX + width - m - tbW
        val tbY0 = originY + m

        addRectangle(tbX0, tbY0, tbX0 + tbW, tbY0 + tbH, layer, constantWidth = 0.03)

        // Lignes de séparation internes
        val lineY1 = tbY0 + tbH * 0.70
        val lineY2 = tbY0 + tbH * 0.40
        val lineY3 = tbY0 + tbH * 0.20
        addLine(tbX0, lineY1, tbX0 + tbW, lineY1, layer)
        addLine(tbX0, lineY2, tbX0 + tbW, lineY2, layer)
        addLine(tbX0, lineY3, tbX0 + tbW, lineY3, layer)

        val colX1 = tbX0 + tbW * 0.50
        addLine(colX1, tbY0, colX1, lineY3, layer)

        // Remplissage des textes du cartouche
        val thTitle = tbH * 0.09
        val thNormal = tbH * 0.06

        addText(tbX0 + tbW / 2.0, tbY0 + tbH * 0.85, thTitle, bureauEtudes, layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)
        addText(tbX0 + tbW / 2.0, tbY0 + tbH * 0.74, thNormal * 0.9, "BUREAU D'ÉTUDES TECHNIQUES - AGRÉÉ CTC ALGÉRIE", layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE)

        addText(tbX0 + tbW * 0.04, tbY0 + tbH * 0.58, thNormal, "PROJET : $projectTitle - $wilaya", layer, hAlign = CadHAlign.LEFT, vAlign = CadVAlign.MIDDLE)
        addText(tbX0 + tbW * 0.04, tbY0 + tbH * 0.47, thNormal, "SOUS-STRUCTURE : $jointName (RPA 2024 / CBA 93)", layer, hAlign = CadHAlign.LEFT, vAlign = CadVAlign.MIDDLE)

        addText(tbX0 + tbW / 2.0, tbY0 + tbH * 0.30, thTitle * 1.1, drawingTitle, layer, hAlign = CadHAlign.CENTER, vAlign = CadVAlign.MIDDLE, colorOverride = 1)

        addText(tbX0 + tbW * 0.04, tbY0 + tbH * 0.10, thNormal, "ÉCHELLE : $scaleText", layer, hAlign = CadHAlign.LEFT, vAlign = CadVAlign.MIDDLE)
        addText(tbX0 + tbW * 0.54, tbY0 + tbH * 0.10, thNormal, "DATE : $dateText", layer, hAlign = CadHAlign.LEFT, vAlign = CadVAlign.MIDDLE)
    }

    /**
     * Génère la chaîne complète DXF standard avec toutes les sections obligatoires.
     */
    fun buildDxfString(): String {
        val sw = StringWriter()

        // 1. HEADER SECTION
        sw.write("  0\nSECTION\n  2\nHEADER\n")
        sw.write("  9\n\$ACADVER\n  1\nAC1024\n") // AutoCAD R2010
        sw.write("  9\n\$INSUNITS\n 70\n6\n")   // Mètres
        sw.write("  9\n\$MEASUREMENT\n 70\n1\n") // Système Métrique
        sw.write("  0\nENDSEC\n")

        // 2. TABLES SECTION (LTYPE, LAYER, STYLE)
        sw.write("  0\nSECTION\n  2\nTABLES\n")

        // Table des Linetypes
        sw.write("  0\nTABLE\n  2\nLTYPE\n 70\n3\n")
        // CONTINUOUS
        sw.write("  0\nLTYPE\n  2\nCONTINUOUS\n 70\n0\n  3\nSolid line\n 72\n65\n 73\n0\n 40\n0.0\n")
        // CENTER
        sw.write("  0\nLTYPE\n  2\nCENTER\n 70\n0\n  3\nCenter ____ _ ____\n 72\n65\n 73\n4\n 40\n2.0\n 49\n1.25\n 49\n-0.25\n 49\n0.25\n 49\n-0.25\n")
        // HIDDEN
        sw.write("  0\nLTYPE\n  2\nHIDDEN\n 70\n0\n  3\nHidden __ __ __\n 72\n65\n 73\n2\n 40\n1.0\n 49\n0.75\n 49\n-0.25\n")
        sw.write("  0\nENDTAB\n")

        // Table des Layers
        sw.write("  0\nTABLE\n  2\nLAYER\n 70\n${CadLayer.entries.size}\n")
        for (layer in CadLayer.entries) {
            sw.write("  0\nLAYER\n  5\n${nextHandle()}\n")
            sw.write("  2\n${layer.layerName}\n")
            sw.write(" 70\n0\n")
            sw.write(" 62\n${layer.colorAci}\n")
            sw.write("  6\n${layer.linetype}\n")
            sw.write("370\n${layer.lineweightHundredthMm}\n")
        }
        sw.write("  0\nENDTAB\n")

        // Table des Styles de texte
        sw.write("  0\nTABLE\n  2\nSTYLE\n 70\n1\n")
        sw.write("  0\nSTYLE\n  5\n${nextHandle()}\n  2\nSTANDARD\n 70\n0\n 40\n0.0\n 41\n1.0\n 50\n0.0\n 71\n0\n 42\n2.5\n  3\narial.ttf\n  4\n\n")
        sw.write("  0\nENDTAB\n")

        sw.write("  0\nENDSEC\n")

        // 3. BLOCKS SECTION
        sw.write("  0\nSECTION\n  2\nBLOCKS\n")
        sw.write("  0\nENDSEC\n")

        // 4. ENTITIES SECTION
        sw.write("  0\nSECTION\n  2\nENTITIES\n")
        sw.write(entitiesBuffer.toString())
        sw.write("  0\nENDSEC\n")

        // 5. EOF
        sw.write("  0\nEOF\n")

        return sw.toString()
    }

    /**
     * Sauvegarde le fichier DXF sur le disque.
     */
    fun saveToFile(file: File): File {
        file.parentFile?.mkdirs()
        file.writeText(buildDxfString(), Charsets.UTF_8)
        return file
    }
}
