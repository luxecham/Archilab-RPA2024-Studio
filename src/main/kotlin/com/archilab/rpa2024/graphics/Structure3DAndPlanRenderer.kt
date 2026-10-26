package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.StructuralJoint
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * Moteur de rendu automatique des vues 3D isométriques, plans de coffrage d'étage et plans de fondation SAFE.
 */
class Structure3DAndPlanRenderer {

    /**
     * Génère la vue 3D isométrique vectorielle (SVG) du bâtiment complet depuis la topologie du Joint.
     */
    fun render3DIsometricSvg(
        joint: StructuralJoint,
        svgWidth: Int = 900,
        svgHeight: Int = 650
    ): String {
        val lx = max(1.0, joint.gridSystem.totalLengthLx)
        val ly = max(1.0, joint.gridSystem.totalLengthLy)
        val hn = max(1.0, joint.storySystem.totalHeightHn)

        // Projection isométrique : u = (X - Y) * cos(30°), v = - (Z - (X + Y) * sin(30°))
        val cos30 = cos(Math.toRadians(30.0))
        val sin30 = sin(Math.toRadians(30.0))

        val uMin = -ly * cos30
        val uMax = lx * cos30
        val uRange = max(1.0, uMax - uMin)

        val vMin = -(hn + 0.0)
        val vMax = (lx + ly) * sin30
        val vRange = max(1.0, vMax - vMin)

        val margin = 70.0
        val plotW = svgWidth - 2.0 * margin
        val plotH = svgHeight - 2.0 * margin
        val scale = min(plotW / uRange, plotH / vRange) * 0.90

        val cx = svgWidth / 2.0 - ((uMax + uMin) / 2.0) * scale
        val cy = svgHeight / 2.0 + 30.0

        fun projX(x: Double, y: Double): Double = cx + (x - y) * cos30 * scale
        fun projY(x: Double, y: Double, z: Double): Double = cy - (z * 1.15 - (x + y) * sin30) * scale

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"32\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#0f172a\">")
        sb.appendLine("    Vue 3D Isométrique du Modèle Structural : ${joint.jointName}")
        sb.appendLine("  </text>")
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"52\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Dimensions : Lx = ${round(lx * 100) / 100} m × Ly = ${round(ly * 100) / 100} m | Hn = ${round(hn * 100) / 100} m (${joint.storySystem.stories.size} Niveaux)")
        sb.appendLine("  </text>")

        // 1. Grille d'axes de base (z = 0)
        for (xg in joint.gridSystem.xGrids) {
            val p1X = projX(xg.coordinate, 0.0)
            val p1Y = projY(xg.coordinate, 0.0, 0.0)
            val p2X = projX(xg.coordinate, ly)
            val p2Y = projY(xg.coordinate, ly, 0.0)
            sb.appendLine("  <line x1=\"$p1X\" y1=\"$p1Y\" x2=\"$p2X\" y2=\"$p2Y\" stroke=\"#cbd5e1\" stroke-width=\"1.2\" stroke-dasharray=\"4,3\"/>")
            // Bulle d'axe
            sb.appendLine("  <circle cx=\"$p1X\" cy=\"${p1Y + 12}\" r=\"8\" fill=\"#f8fafc\" stroke=\"#64748b\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"$p1X\" y=\"${p1Y + 15}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"8\" font-weight=\"bold\" fill=\"#334155\">${xg.id}</text>")
        }
        for (yg in joint.gridSystem.yGrids) {
            val p1X = projX(0.0, yg.coordinate)
            val p1Y = projY(0.0, yg.coordinate, 0.0)
            val p2X = projX(lx, yg.coordinate)
            val p2Y = projY(lx, yg.coordinate, 0.0)
            sb.appendLine("  <line x1=\"$p1X\" y1=\"$p1Y\" x2=\"$p2X\" y2=\"$p2Y\" stroke=\"#cbd5e1\" stroke-width=\"1.2\" stroke-dasharray=\"4,3\"/>")
            sb.appendLine("  <circle cx=\"${p1X - 12}\" cy=\"$p1Y\" r=\"8\" fill=\"#f8fafc\" stroke=\"#64748b\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"${p1X - 12}\" y=\"${p1Y + 3}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"8\" font-weight=\"bold\" fill=\"#334155\">${yg.id}</text>")
        }

        // 2. Dalles d'étages horizontales
        for (story in joint.storySystem.stories) {
            val z = story.elevationZk
            val c1X = projX(0.0, 0.0)
            val c1Y = projY(0.0, 0.0, z)
            val c2X = projX(lx, 0.0)
            val c2Y = projY(lx, 0.0, z)
            val c3X = projX(lx, ly)
            val c3Y = projY(lx, ly, z)
            val c4X = projX(0.0, ly)
            val c4Y = projY(0.0, ly, z)

            val polyPath = "M $c1X $c1Y L $c2X $c2Y L $c3X $c3Y L $c4X $c4Y Z"
            sb.appendLine("  <path d=\"$polyPath\" fill=\"#94a3b8\" fill-opacity=\"0.10\" stroke=\"#64748b\" stroke-width=\"1\"/>")
            sb.appendLine("  <text x=\"${c1X - 10}\" y=\"$c1Y\" text-anchor=\"end\" font-family=\"Arial, sans-serif\" font-size=\"9\" fill=\"#475569\">${story.storyName} (z=${round(z * 10) / 10}m)</text>")
        }

        // 3. Poteaux verticaux (aux intersections des grilles)
        for (xg in joint.gridSystem.xGrids) {
            for (yg in joint.gridSystem.yGrids) {
                val bx = projX(xg.coordinate, yg.coordinate)
                val by = projY(xg.coordinate, yg.coordinate, 0.0)
                val tx = projX(xg.coordinate, yg.coordinate)
                val ty = projY(xg.coordinate, yg.coordinate, hn)
                sb.appendLine("  <line x1=\"$bx\" y1=\"$by\" x2=\"$tx\" y2=\"$ty\" stroke=\"#2563eb\" stroke-width=\"2.5\" stroke-linecap=\"round\"/>")
            }
        }

        // 4. Poutres longitudinales et transversales à chaque niveau
        for (story in joint.storySystem.stories) {
            val z = story.elevationZk
            for (xg in joint.gridSystem.xGrids) {
                val p1X = projX(xg.coordinate, 0.0)
                val p1Y = projY(xg.coordinate, 0.0, z)
                val p2X = projX(xg.coordinate, ly)
                val p2Y = projY(xg.coordinate, ly, z)
                sb.appendLine("  <line x1=\"$p1X\" y1=\"$p1Y\" x2=\"$p2X\" y2=\"$p2Y\" stroke=\"#10b981\" stroke-width=\"1.8\"/>")
            }
            for (yg in joint.gridSystem.yGrids) {
                val p1X = projX(0.0, yg.coordinate)
                val p1Y = projY(0.0, yg.coordinate, z)
                val p2X = projX(lx, yg.coordinate)
                val p2Y = projY(lx, yg.coordinate, z)
                sb.appendLine("  <line x1=\"$p1X\" y1=\"$p1Y\" x2=\"$p2X\" y2=\"$p2Y\" stroke=\"#10b981\" stroke-width=\"1.8\"/>")
            }
        }

        // 5. Voiles de contreventement (Surfaces rouges verticales)
        for (wall in joint.sectionCatalog.shearWalls) {
            val wx = if (wall.direction == AxisDirection.X) 0.0 else lx / 2.0
            val wy = if (wall.direction == AxisDirection.Y) 0.0 else ly / 2.0
            val wl = wall.lengthLwMeters

            val w1X = projX(wx, wy)
            val w1Y = projY(wx, wy, 0.0)
            val w2X = projX(if (wall.direction == AxisDirection.X) wx + wl else wx, if (wall.direction == AxisDirection.Y) wy + wl else wy)
            val w2Y = projY(if (wall.direction == AxisDirection.X) wx + wl else wx, if (wall.direction == AxisDirection.Y) wy + wl else wy, 0.0)
            val w3X = projX(if (wall.direction == AxisDirection.X) wx + wl else wx, if (wall.direction == AxisDirection.Y) wy + wl else wy)
            val w3Y = projY(if (wall.direction == AxisDirection.X) wx + wl else wx, if (wall.direction == AxisDirection.Y) wy + wl else wy, hn)
            val w4X = projX(wx, wy)
            val w4Y = projY(wx, wy, hn)

            val wallPoly = "M $w1X $w1Y L $w2X $w2Y L $w3X $w3Y L $w4X $w4Y Z"
            sb.appendLine("  <path d=\"$wallPoly\" fill=\"#dc2626\" fill-opacity=\"0.65\" stroke=\"#991b1b\" stroke-width=\"2\"/>")
        }

        // Légende
        val legY = svgHeight - 20
        sb.appendLine("  <line x1=\"50\" y1=\"$legY\" x2=\"75\" y2=\"$legY\" stroke=\"#2563eb\" stroke-width=\"3\"/>")
        sb.appendLine("  <text x=\"82\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">Poteaux</text>")

        sb.appendLine("  <line x1=\"160\" y1=\"$legY\" x2=\"185\" y2=\"$legY\" stroke=\"#10b981\" stroke-width=\"2\"/>")
        sb.appendLine("  <text x=\"192\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">Poutres</text>")

        sb.appendLine("  <rect x=\"270\" y=\"${legY - 6}\" width=\"18\" height=\"12\" fill=\"#dc2626\" fill-opacity=\"0.7\" stroke=\"#991b1b\" stroke-width=\"1\"/>")
        sb.appendLine("  <text x=\"295\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">Voiles de Contreventement</text>")

        sb.appendLine("  <rect x=\"470\" y=\"${legY - 6}\" width=\"18\" height=\"12\" fill=\"#94a3b8\" fill-opacity=\"0.3\" stroke=\"#64748b\" stroke-width=\"1\"/>")
        sb.appendLine("  <text x=\"495\" y=\"${legY + 4}\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#334155\">Planchers Dalles</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }

    /**
     * Génère la vue en plan de coffrage 2D complète d'un étage.
     */
    fun renderFloorFramingPlanSvg(
        joint: StructuralJoint,
        storyName: String = "H RDC",
        svgWidth: Int = 800,
        svgHeight: Int = 600
    ): String {
        val lx = max(1.0, joint.gridSystem.totalLengthLx)
        val ly = max(1.0, joint.gridSystem.totalLengthLy)

        val marginLeft = 90.0
        val marginRight = 60.0
        val marginTop = 80.0
        val marginBottom = 70.0

        val plotW = svgWidth - marginLeft - marginRight
        val plotH = svgHeight - marginTop - marginBottom

        val scale = min(plotW / lx, plotH / ly) * 0.90
        val originX = marginLeft + (plotW - lx * scale) / 2.0
        val originY = marginTop + (plotH - ly * scale) / 2.0

        fun xPix(x: Double): Double = originX + x * scale
        fun yPix(y: Double): Double = originY + (ly - y) * scale // Y orienté vers le haut

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#e2e8f0\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"32\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"16\" font-weight=\"bold\" fill=\"#0f172a\">")
        sb.appendLine("    Plan de Coffrage du Niveau : $storyName (${joint.jointName})")
        sb.appendLine("  </text>")
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"52\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#64748b\">")
        sb.appendLine("    Dimensions globales : Lx = ${round(lx * 100) / 100} m × Ly = ${round(ly * 100) / 100} m")
        sb.appendLine("  </text>")

        // 1. Files d'axes X
        for (xg in joint.gridSystem.xGrids) {
            val xp = xPix(xg.coordinate)
            val yTop = originY - 20.0
            val yBottom = originY + ly * scale + 20.0
            sb.appendLine("  <line x1=\"$xp\" y1=\"$yTop\" x2=\"$xp\" y2=\"$yBottom\" stroke=\"#94a3b8\" stroke-width=\"1.2\" stroke-dasharray=\"6,3\"/>")
            // Bulles en haut et en bas
            sb.appendLine("  <circle cx=\"$xp\" cy=\"${yTop - 12}\" r=\"10\" fill=\"#ffffff\" stroke=\"#475569\" stroke-width=\"1.5\"/>")
            sb.appendLine("  <text x=\"$xp\" y=\"${yTop - 8}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#0f172a\">${xg.id}</text>")
        }

        // 2. Files d'axes Y
        for (yg in joint.gridSystem.yGrids) {
            val yp = yPix(yg.coordinate)
            val xLeft = originX - 20.0
            val xRight = originX + lx * scale + 20.0
            sb.appendLine("  <line x1=\"$xLeft\" y1=\"$yp\" x2=\"$xRight\" y2=\"$yp\" stroke=\"#94a3b8\" stroke-width=\"1.2\" stroke-dasharray=\"6,3\"/>")
            // Bulles à gauche et à droite
            sb.appendLine("  <circle cx=\"${xLeft - 12}\" cy=\"$yp\" r=\"10\" fill=\"#ffffff\" stroke=\"#475569\" stroke-width=\"1.5\"/>")
            sb.appendLine("  <text x=\"${xLeft - 12}\" y=\"${yp + 4}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#0f172a\">${yg.id}</text>")
        }

        // 3. Poutres
        for (i in 0 until joint.gridSystem.xGrids.size - 1) {
            val x1 = joint.gridSystem.xGrids[i].coordinate
            val x2 = joint.gridSystem.xGrids[i + 1].coordinate
            for (yg in joint.gridSystem.yGrids) {
                val yp = yPix(yg.coordinate)
                sb.appendLine("  <line x1=\"${xPix(x1)}\" y1=\"$yp\" x2=\"${xPix(x2)}\" y2=\"$yp\" stroke=\"#059669\" stroke-width=\"4\" stroke-linecap=\"round\"/>")
                val midX = (xPix(x1) + xPix(x2)) / 2.0
                sb.appendLine("  <text x=\"$midX\" y=\"${yp - 5}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"8\" fill=\"#065f46\">PR30X55</text>")
            }
        }
        for (j in 0 until joint.gridSystem.yGrids.size - 1) {
            val y1 = joint.gridSystem.yGrids[j].coordinate
            val y2 = joint.gridSystem.yGrids[j + 1].coordinate
            for (xg in joint.gridSystem.xGrids) {
                val xp = xPix(xg.coordinate)
                sb.appendLine("  <line x1=\"$xp\" y1=\"${yPix(y1)}\" x2=\"$xp\" y2=\"${yPix(y2)}\" stroke=\"#059669\" stroke-width=\"4\" stroke-linecap=\"round\"/>")
                val midY = (yPix(y1) + yPix(y2)) / 2.0
                sb.appendLine("  <text x=\"${xp + 6}\" y=\"$midY\" text-anchor=\"start\" font-family=\"Arial, sans-serif\" font-size=\"8\" fill=\"#065f46\">CH30X35</text>")
            }
        }

        // 4. Poteaux aux nœuds
        val colW = 12.0
        val colH = 16.0
        for (xg in joint.gridSystem.xGrids) {
            for (yg in joint.gridSystem.yGrids) {
                val xp = xPix(xg.coordinate)
                val yp = yPix(yg.coordinate)
                sb.appendLine("  <rect x=\"${xp - colW / 2}\" y=\"${yp - colH / 2}\" width=\"$colW\" height=\"$colH\" fill=\"#1e40af\" stroke=\"#0f172a\" stroke-width=\"1.5\" rx=\"1\"/>")
            }
        }

        // 5. Voiles
        for (wall in joint.sectionCatalog.shearWalls) {
            val wx = if (wall.direction == AxisDirection.X) 0.0 else lx / 2.0
            val wy = if (wall.direction == AxisDirection.Y) 0.0 else ly / 2.0
            val wl = wall.lengthLwMeters

            val px = xPix(wx)
            val py = yPix(wy)
            if (wall.direction == AxisDirection.X) {
                val wWidth = wl * scale
                sb.appendLine("  <rect x=\"$px\" y=\"${py - 5}\" width=\"$wWidth\" height=\"10\" fill=\"#dc2626\" stroke=\"#991b1b\" stroke-width=\"1.5\"/>")
                sb.appendLine("  <text x=\"${px + wWidth / 2}\" y=\"${py + 16}\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"9\" font-weight=\"bold\" fill=\"#dc2626\">${wall.pierName} (L=${wall.lengthLwMeters}m)</text>")
            } else {
                val wHeight = wl * scale
                sb.appendLine("  <rect x=\"${px - 5}\" y=\"${py - wHeight}\" width=\"10\" height=\"$wHeight\" fill=\"#dc2626\" stroke=\"#991b1b\" stroke-width=\"1.5\"/>")
                sb.appendLine("  <text x=\"${px + 10}\" y=\"${py - wHeight / 2}\" font-family=\"Arial, sans-serif\" font-size=\"9\" font-weight=\"bold\" fill=\"#dc2626\">${wall.pierName}</text>")
            }
        }

        sb.appendLine("</svg>")
        return sb.toString()
    }

    /**
     * Génère une image PNG (ByteArray) haute résolution à partir d'un dessin vectoriel ou Canvas.
     */
    fun renderPlanToPngByteArray(joint: StructuralJoint, width: Int = 1200, height: Int = 900): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        g.color = Color(15, 23, 42)
        g.font = Font("Arial", Font.BOLD, 22)
        g.drawString("Plan de Coffrage : ${joint.jointName}", 50, 45)

        g.color = Color(100, 116, 139)
        g.font = Font("Arial", Font.PLAIN, 14)
        g.drawString("Dimensions : Lx = ${round(joint.gridSystem.totalLengthLx * 100) / 100} m x Ly = ${round(joint.gridSystem.totalLengthLy * 100) / 100} m", 50, 70)

        // Cadre extérieur
        g.color = Color(203, 213, 225)
        g.drawRect(50, 90, width - 100, height - 140)

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Génère une image matricielle PNG de la vue 3D isométrique.
     */
    fun render3dIsometricPngBytes(joint: StructuralJoint, width: Int = 1200, height: Int = 800): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        g.color = Color(15, 23, 42)
        g.font = Font("Arial", Font.BOLD, 22)
        g.drawString("Modèle 3D Éléments Finis : ${joint.jointName}", 50, 45)

        g.color = Color(100, 116, 139)
        g.font = Font("Arial", Font.PLAIN, 14)
        g.drawString("Hn = ${round(joint.storySystem.totalHeightHn * 100) / 100} m | ${joint.storySystem.stories.size} Niveaux", 50, 70)

        // Cadre extérieur
        g.color = Color(203, 213, 225)
        g.drawRect(50, 90, width - 100, height - 140)

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "PNG", baos)
        return baos.toByteArray()
    }

    /**
     * Rendu PNG du plan de coffrage.
     */
    fun renderPlanPngBytes(joint: StructuralJoint, storyName: String = "", width: Int = 1200, height: Int = 900): ByteArray {
        return renderPlanToPngByteArray(joint, width, height)
    }
}
