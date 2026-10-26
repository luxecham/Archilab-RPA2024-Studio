package com.archilab.rpa2024.engine.frames

import java.io.File
import kotlin.math.*

/**
 * Générateur vectoriel SVG et schématique haute définition pour les armatures des poteaux et des poutres.
 * Reproduit fidèlement les fiches SOCOTEC BaelR et les schémas d'exécution du RPA 2024 (pages 28 à 34).
 */
class FrameSchematicRenderer {

    /**
     * Génère le schéma SVG d'une coupe transversale style BaelR.
     */
    fun renderBaelRCrossSectionSvg(
        widthBCm: Double,
        heightHCm: Double,
        y0Meters: Double,
        aSupDescription: String,
        aInfDescription: String,
        viewWidth: Int = 400,
        viewHeight: Int = 450
    ): String {
        val y0Cm = y0Meters * 100.0
        val scale = min(250.0 / widthBCm, 300.0 / heightHCm)
        val rectW = widthBCm * scale
        val rectH = heightHCm * scale
        val startX = (viewWidth - rectW) / 2.0
        val startY = 60.0

        val y0Scaled = min(rectH, y0Cm * scale)
        val centerX = viewWidth / 2.0
        val endX = startX + rectW
        val neutralAxisY = startY + y0Scaled
        val textY0 = startY + y0Scaled / 2.0 + 4.0

        val cX = startX + rectW / 2.0
        val cY = startY + rectH / 2.0

        val coverScaled = 3.0 * scale
        val barYTop = startY + coverScaled
        val barYBot = startY + rectH - coverScaled
        val barXLeft = startX + coverScaled
        val barXRight = startX + rectW - coverScaled
        val barXMid = (barXLeft + barXRight) / 2.0

        val topLabelY = barYTop - 10.0
        val botLabelY = barYBot + 20.0
        val dimBLabelY = startY + rectH + 45.0
        val dimHLabelX = startX - 15.0

        val svg = StringBuilder()
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $viewWidth $viewHeight" width="$viewWidth" height="$viewHeight">""")
        svg.append("""<rect width="100%" height="100%" fill="#ffffff"/>""")

        // Titre
        svg.append("""<text x="$centerX" y="30" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="#1e293b">Section BaelR : ${widthBCm.toInt()} x ${heightHCm.toInt()} cm</text>""")

        // Contour béton
        svg.append("""<rect x="$startX" y="$startY" width="$rectW" height="$rectH" fill="#f8fafc" stroke="#334155" stroke-width="2.5"/>""")

        // Zone comprimée hachurée (de 0 à y0)
        if (y0Scaled > 0.5) {
            svg.append("""<rect x="$startX" y="$startY" width="$rectW" height="$y0Scaled" fill="#cbd5e1" opacity="0.6"/>""")
            svg.append("""<line x1="$startX" y1="$neutralAxisY" x2="$endX" y2="$neutralAxisY" stroke="#dc2626" stroke-width="1.5" stroke-dasharray="4 2"/>""")
            svg.append("""<text x="${endX + 10}" y="$textY0" font-family="Arial, sans-serif" font-size="11" fill="#dc2626">Axe neutre y0 = ${y0Cm.toInt()} cm</text>""")
        }

        // Centre de gravité G
        svg.append("""<text x="$cX" y="${cY + 4}" text-anchor="middle" font-family="Arial, sans-serif" font-size="14" font-weight="bold" fill="#0284c7">*G</text>""")

        // Armatures supérieures (Asup)
        svg.append("""<circle cx="$barXLeft" cy="$barYTop" r="5" fill="#2563eb"/>""")
        svg.append("""<circle cx="$barXRight" cy="$barYTop" r="5" fill="#2563eb"/>""")
        svg.append("""<circle cx="$barXMid" cy="$barYTop" r="5" fill="#2563eb"/>""")
        svg.append("""<text x="$centerX" y="$topLabelY" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#2563eb">Asup = $aSupDescription</text>""")

        // Armatures inférieures (Ainf)
        svg.append("""<circle cx="$barXLeft" cy="$barYBot" r="5" fill="#16a34a"/>""")
        svg.append("""<circle cx="$barXRight" cy="$barYBot" r="5" fill="#16a34a"/>""")
        svg.append("""<circle cx="$barXMid" cy="$barYBot" r="5" fill="#16a34a"/>""")
        svg.append("""<text x="$centerX" y="$botLabelY" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" font-weight="bold" fill="#16a34a">Ainf = $aInfDescription</text>""")

        // Cotations
        svg.append("""<text x="$centerX" y="$dimBLabelY" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" fill="#475569">b = ${widthBCm.toInt()} cm</text>""")
        svg.append("""<text x="$dimHLabelX" y="$cY" text-anchor="middle" transform="rotate(-90 $dimHLabelX $cY)" font-family="Arial, sans-serif" font-size="12" fill="#475569">h = ${heightHCm.toInt()} cm</text>""")

        svg.append("</svg>")
        return svg.toString()
    }

    /**
     * Génère l'élévation technique de ferraillage d'un poteau selon RPA 2024 (style pages 29/30).
     */
    fun renderColumnElevationSvg(
        colName: String,
        bCm: Double,
        hCm: Double,
        lclMeters: Double,
        lcrCm: Double,
        stNodalCm: Double,
        stCurrentCm: Double,
        lrecCm: Double,
        rebarDesc: String,
        viewWidth: Int = 450,
        viewHeight: Int = 650
    ): String {
        val centerX = viewWidth / 2.0
        val colX = 140.0
        val colW = 70.0
        val colTopY = 80.0
        val colH = 480.0
        val colBotY = colTopY + colH

        val lcrScaled = (lcrCm / (lclMeters * 100.0)) * colH
        val colMidY = colTopY + colH / 2.0

        val svg = StringBuilder()
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $viewWidth $viewHeight" width="$viewWidth" height="$viewHeight">""")
        svg.append("""<rect width="100%" height="100%" fill="#ffffff"/>""")

        svg.append("""<text x="$centerX" y="25" text-anchor="middle" font-family="Arial, sans-serif" font-size="15" font-weight="bold" fill="#0f172a">Ferraillage Poteau : $colName (${bCm.toInt()}x${hCm.toInt()})</text>""")

        // Nœud supérieur et inférieur
        svg.append("""<rect x="${colX - 40.0}" y="${colTopY - 30.0}" width="${colW + 80.0}" height="30" fill="#e2e8f0" stroke="#64748b"/>""")
        svg.append("""<rect x="${colX - 40.0}" y="$colBotY" width="${colW + 80.0}" height="30" fill="#e2e8f0" stroke="#64748b"/>""")

        // Poteau béton
        svg.append("""<rect x="$colX" y="$colTopY" width="$colW" height="$colH" fill="#f8fafc" stroke="#334155" stroke-width="2"/>""")

        // Armatures verticales avec crochets 90°
        svg.append("""<line x1="${colX + 10.0}" y1="${colTopY - 20.0}" x2="${colX + 10.0}" y2="${colBotY + 20.0}" stroke="#dc2626" stroke-width="3"/>""")
        svg.append("""<line x1="${colX + colW - 10.0}" y1="${colTopY - 20.0}" x2="${colX + colW - 10.0}" y2="${colBotY + 20.0}" stroke="#dc2626" stroke-width="3"/>""")

        // Zones nodales haute et basse
        svg.append("""<rect x="$colX" y="$colTopY" width="$colW" height="$lcrScaled" fill="#fed7aa" opacity="0.4"/>""")
        svg.append("""<rect x="$colX" y="${colBotY - lcrScaled}" width="$colW" height="$lcrScaled" fill="#fed7aa" opacity="0.4"/>""")

        // Cadres serrés en zone nodale (espacement t)
        var y = colTopY + 10.0
        while (y < colTopY + lcrScaled) {
            svg.append("""<line x1="${colX + 6.0}" y1="$y" x2="${colX + colW - 6.0}" y2="$y" stroke="#2563eb" stroke-width="1.5"/>""")
            y += 12.0
        }

        y = colBotY - lcrScaled + 10.0
        while (y < colBotY) {
            svg.append("""<line x1="${colX + 6.0}" y1="$y" x2="${colX + colW - 6.0}" y2="$y" stroke="#2563eb" stroke-width="1.5"/>""")
            y += 12.0
        }

        // Cadres en zone courante (espacement t')
        y = colTopY + lcrScaled + 15.0
        while (y < colBotY - lcrScaled) {
            svg.append("""<line x1="${colX + 6.0}" y1="$y" x2="${colX + colW - 6.0}" y2="$y" stroke="#2563eb" stroke-width="1.5"/>""")
            y += 24.0
        }

        // Cotations et textes
        svg.append("""<text x="60" y="$colMidY" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#0f172a">lcl = ${String.format(java.util.Locale.US, "%.2f", lclMeters)} m</text>""")
        svg.append("""<text x="${colX + colW + 20.0}" y="${colTopY + lcrScaled / 2.0}" font-family="Arial, sans-serif" font-size="11" fill="#ea580c">Zone nodale Lcr = ${lcrCm.toInt()} cm (t = ${stNodalCm.toInt()} cm)</text>""")
        svg.append("""<text x="${colX + colW + 20.0}" y="$colMidY" font-family="Arial, sans-serif" font-size="11" fill="#2563eb">Zone courante (t' = ${stCurrentCm.toInt()} cm)</text>""")
        svg.append("""<text x="${colX + colW + 20.0}" y="${colBotY - lcrScaled / 2.0}" font-family="Arial, sans-serif" font-size="11" fill="#ea580c">Zone nodale Lcr = ${lcrCm.toInt()} cm (t = ${stNodalCm.toInt()} cm)</text>""")
        svg.append("""<text x="$centerX" y="${colBotY + 50.0}" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#16a34a">Armatures : $rebarDesc | Lrec = ${lrecCm.toInt()} cm</text>""")

        svg.append("</svg>")
        return svg.toString()
    }

    /**
     * Génère l'élévation technique de ferraillage d'une poutre selon RPA 2024 (style pages 33/34).
     */
    fun renderBeamElevationSvg(
        beamName: String,
        bCm: Double,
        hCm: Double,
        lPrimeCm: Double,
        stNodalCm: Double,
        stCurrentCm: Double,
        spanRebarDesc: String,
        supportRebarDesc: String,
        lrecCm: Double,
        viewWidth: Int = 650,
        viewHeight: Int = 380
    ): String {
        val centerX = viewWidth / 2.0
        val supW = 60.0
        val beamStartX = 100.0
        val beamEndX = 550.0
        val beamTopY = 80.0
        val beamH = 120.0
        val beamBotY = beamTopY + beamH
        val lPrimeScaled = 100.0
        val beamMidX = (beamStartX + beamEndX) / 2.0

        val svg = StringBuilder()
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $viewWidth $viewHeight" width="$viewWidth" height="$viewHeight">""")
        svg.append("""<rect width="100%" height="100%" fill="#ffffff"/>""")

        svg.append("""<text x="$centerX" y="25" text-anchor="middle" font-family="Arial, sans-serif" font-size="15" font-weight="bold" fill="#0f172a">Ferraillage Poutre : $beamName (${bCm.toInt()}x${hCm.toInt()}) [BAEL 91 + RPA 2024]</text>""")

        // Poteau gauche et droit
        svg.append("""<rect x="${beamStartX - supW}" y="${beamTopY - 40.0}" width="$supW" height="${beamH + 80.0}" fill="#e2e8f0" stroke="#64748b"/>""")
        svg.append("""<rect x="$beamEndX" y="${beamTopY - 40.0}" width="$supW" height="${beamH + 80.0}" fill="#e2e8f0" stroke="#64748b"/>""")

        // Poutre béton
        svg.append("""<rect x="$beamStartX" y="$beamTopY" width="${beamEndX - beamStartX}" height="$beamH" fill="#f8fafc" stroke="#334155" stroke-width="2"/>""")

        // Zone nodale gauche et droite l'
        svg.append("""<rect x="$beamStartX" y="$beamTopY" width="$lPrimeScaled" height="$beamH" fill="#fed7aa" opacity="0.4"/>""")
        svg.append("""<rect x="${beamEndX - lPrimeScaled}" y="$beamTopY" width="$lPrimeScaled" height="$beamH" fill="#fed7aa" opacity="0.4"/>""")

        // 1er cadre à 5 cm
        svg.append("""<line x1="${beamStartX + 10.0}" y1="${beamTopY + 8.0}" x2="${beamStartX + 10.0}" y2="${beamBotY - 8.0}" stroke="#dc2626" stroke-width="2"/>""")
        svg.append("""<text x="${beamStartX + 10.0}" y="${beamTopY - 8.0}" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#dc2626">5cm</text>""")

        // Cadres zone nodale gauche (espacement S = 8 cm)
        var x = beamStartX + 25.0
        while (x < beamStartX + lPrimeScaled) {
            svg.append("""<line x1="$x" y1="${beamTopY + 8.0}" x2="$x" y2="${beamBotY - 8.0}" stroke="#2563eb" stroke-width="1.5"/>""")
            x += 16.0
        }

        // Cadres zone courante (espacement S' = 15 cm)
        while (x < beamEndX - lPrimeScaled) {
            svg.append("""<line x1="$x" y1="${beamTopY + 8.0}" x2="$x" y2="${beamBotY - 8.0}" stroke="#2563eb" stroke-width="1.5"/>""")
            x += 32.0
        }

        // Cadres zone nodale droite
        x = beamEndX - lPrimeScaled + 10.0
        while (x < beamEndX) {
            svg.append("""<line x1="$x" y1="${beamTopY + 8.0}" x2="$x" y2="${beamBotY - 8.0}" stroke="#2563eb" stroke-width="1.5"/>""")
            x += 16.0
        }

        // Armatures longitudinales supérieures (chapeaux d'appui)
        svg.append("""<line x1="${beamStartX - 40.0}" y1="${beamTopY + 12.0}" x2="${beamStartX + 140.0}" y2="${beamTopY + 12.0}" stroke="#dc2626" stroke-width="3"/>""")
        svg.append("""<line x1="${beamEndX - 140.0}" y1="${beamTopY + 12.0}" x2="${beamEndX + 40.0}" y2="${beamTopY + 12.0}" stroke="#dc2626" stroke-width="3"/>""")

        // Armatures longitudinales filantes inférieures
        svg.append("""<line x1="${beamStartX - 40.0}" y1="${beamBotY - 12.0}" x2="${beamEndX + 40.0}" y2="${beamBotY - 12.0}" stroke="#16a34a" stroke-width="3"/>""")

        // Annotations et cotes
        svg.append("""<text x="${beamStartX + lPrimeScaled / 2.0}" y="${beamBotY + 25.0}" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" fill="#ea580c">Zone nodale l' = ${lPrimeCm.toInt()} cm (S = ${stNodalCm.toInt()} cm)</text>""")
        svg.append("""<text x="$beamMidX" y="${beamBotY + 25.0}" text-anchor="middle" font-family="Arial, sans-serif" font-size="11" fill="#2563eb">Zone courante (S' = ${stCurrentCm.toInt()} cm)</text>""")
        svg.append("""<text x="$centerX" y="${beamBotY + 60.0}" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" font-weight="bold" fill="#0f172a">En travée : $spanRebarDesc | Sur appui : $supportRebarDesc | Lrec = ${lrecCm.toInt()} cm</text>""")

        svg.append("</svg>")
        return svg.toString()
    }

    /**
     * Sauvegarde un SVG généré dans un fichier.
     */
    fun saveSvgToFile(svgContent: String, outputFile: File) {
        outputFile.writeText(svgContent)
    }
}
