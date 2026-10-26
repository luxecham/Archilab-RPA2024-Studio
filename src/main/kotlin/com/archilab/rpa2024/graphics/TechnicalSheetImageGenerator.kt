package com.archilab.rpa2024.graphics

import com.archilab.rpa2024.engine.foundations.BaelTResult
import com.archilab.rpa2024.engine.frames.BaelRectangularResult
import com.archilab.rpa2024.engine.frames.BeamDesignResult
import com.archilab.rpa2024.engine.frames.ColumnDesignResult
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.secondary.ParapetDesignResult
import com.archilab.rpa2024.engine.slabs.ContinuousSlabResult
import com.archilab.rpa2024.engine.walls.WallDesignResult
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fiches de calcul visuelles haute definition remplacant les interfaces Koka, BaelR, BaelT
 * et les utilitaires Excel. Chaque fiche est disponible en SVG et en PNG 300 DPI pour
 * insertion directe dans la note de calcul Word/PDF.
 */
class TechnicalSheetImageGenerator {

    // ------------------------------------------------------------------ BaelR

    fun renderBaelRSheet(
        result: BaelRectangularResult,
        title: String = "Fiche de calcul BaelR — section rectangulaire",
        dpi: Double = 300.0
    ): GraphicOutput {
        val section = result.section
        val canvas = VectorCanvas(1120.0, 760.0)
        frame(canvas, title, "Flexion ${if (section.isSimpleBending) "simple" else "composée"} · " +
            "situation ${result.coefficients.situation.label} · BAEL 91 révisé 99")

        dataBox(
            canvas, 40.0, 92.0, 320.0,
            "Données saisies",
            listOf(
                "b" to "${fmt(section.bCm)} cm",
                "h" to "${fmt(section.hCm)} cm",
                "d'" to "${fmt(section.compressionCoverCm)} cm",
                "c" to "${fmt(section.tensionCoverCm)} cm",
                "fc28" to "${fmt(result.coefficients.fc28MPa)} MPa",
                "fe" to "${fmt(result.coefficients.feMPa)} MPa",
                "θ" to fmt(result.coefficients.theta),
                "γb" to fmt(result.coefficients.gammaB),
                "γs" to fmt(result.coefficients.gammaS),
                "Nu" to "${fmt(result.axialKN)} kN",
                "Mu" to "${fmt(result.momentKNm)} kN.m"
            )
        )

        dataBox(
            canvas, 780.0, 92.0, 300.0,
            "Résultats ELU/ELS",
            listOf(
                "État" to result.state.label,
                "μbu" to fmt(result.muBu, 4),
                "α" to fmt(result.alpha, 4),
                "y0" to "${fmt(result.neutralAxisMeters * 100.0)} cm",
                "zb" to "${fmt(result.leverArmMeters * 100.0)} cm",
                "As,inf" to "${fmt(result.bottomSteelCm2)} cm²",
                "As,sup" to "${fmt(result.topSteelCm2)} cm²",
                "As,min" to "${fmt(result.minimumSteelCm2)} cm²",
                "As,retenu" to "${fmt(result.requiredSteelCm2)} cm²"
            )
        )

        drawRectangularSection(
            canvas, 400.0, 100.0, 340.0, 520.0,
            widthCm = section.bCm,
            heightCm = section.hCm,
            bottomCoverCm = section.tensionCoverCm,
            topCoverCm = section.compressionCoverCm,
            compressionTop = result.momentKNm >= 0.0 && result.bottomSteelCm2 >= result.topSteelCm2,
            neutralAxisCm = result.neutralAxisMeters * 100.0,
            bottomSteelCm2 = result.bottomSteelCm2,
            topSteelCm2 = result.topSteelCm2
        )

        canvas.text(400.0, 660.0,
            "Axe neutre : ${result.neutralAxisLabel} · bras de levier z = ${fmt(result.leverArmMeters * 100.0)} cm",
            10.0, TextAnchor.START, "#374151")
        canvas.text(400.0, 678.0,
            "Moment réduit μlu = ${fmt(result.muLu, 4)} · acier tendu ${fmt(result.tensionSteelCm2)} cm² · " +
                "acier comprimé ${fmt(result.compressionSteelCm2)} cm²",
            10.0, TextAnchor.START, "#374151")
        result.notes.take(2).forEachIndexed { index, note ->
            canvas.text(40.0, 690.0 + index * 15.0, "• $note", 9.0, TextAnchor.START, "#6b7280")
        }
        canvas.footer("Fiche générée automatiquement — remplace la sortie BaelR de SOCOTEC")
        return output(canvas, dpi, title)
    }

    // ------------------------------------------------------------------ BaelT

    fun renderBaelTSheet(
        result: BaelTResult,
        title: String = "Fiche de calcul BaelT — section en Té",
        dpi: Double = 300.0
    ): GraphicOutput {
        val input = result.input
        val canvas = VectorCanvas(1120.0, 780.0)
        frame(canvas, title, "Nervure et table comprimée · ${result.caseLabel} · BAEL 91 révisé 99")

        dataBox(
            canvas, 40.0, 92.0, 320.0,
            "Données saisies",
            listOf(
                "B (table)" to "${fmt(input.bMeters * 100.0)} cm",
                "b0 (nervure)" to "${fmt(input.b0Meters * 100.0)} cm",
                "h (total)" to "${fmt(input.hMeters * 100.0)} cm",
                "h0 (table)" to "${fmt(input.h0Meters * 100.0)} cm",
                "d'" to "${fmt(input.dPrimeMeters * 100.0)} cm",
                "c" to "${fmt(input.cMeters * 100.0)} cm",
                "fc28" to "${fmt(input.fc28MPa)} MPa",
                "fe" to "${fmt(input.feMPa)} MPa",
                "Nu" to "${fmt(input.nuKN)} kN",
                "Mu" to "${fmt(result.designMomentKNm)} kN.m"
            )
        )

        dataBox(
            canvas, 780.0, 92.0, 300.0,
            "Résultats ELU",
            listOf(
                "Cas" to result.caseLabel,
                "Mtu (table)" to "${fmt(result.momentTableKNm)} kN.m",
                "μbu" to fmt(result.muBu, 4),
                "μlu" to fmt(result.muLu, 4),
                "α" to fmt(result.alpha, 4),
                "y0" to "${fmt(result.neutralAxisY0Meters * 100.0)} cm",
                "zb" to "${fmt(result.leverArmMeters * 100.0)} cm",
                "As,inf" to "${fmt(result.aInfCm2)} cm²",
                "As,sup" to "${fmt(result.aSupCm2)} cm²",
                "As,min" to "${fmt(result.minimumSteelCm2)} cm²",
                "As,retenu" to "${fmt(result.requiredSteelCm2)} cm²"
            )
        )

        drawTeeSection(
            canvas, 390.0, 100.0, 360.0, 540.0,
            flangeWidthCm = input.bMeters * 100.0,
            webWidthCm = input.b0Meters * 100.0,
            totalHeightCm = input.hMeters * 100.0,
            flangeThicknessCm = input.h0Meters * 100.0,
            neutralAxisCm = result.neutralAxisY0Meters * 100.0,
            bottomSteelCm2 = result.aInfCm2,
            topSteelCm2 = result.aSupCm2
        )

        canvas.text(390.0, 672.0,
            "Axe neutre y0 = ${fmt(result.neutralAxisY0Meters * 100.0)} cm · " +
                "table ${if (result.neutralAxisY0Meters <= input.h0Meters) "partiellement comprimée" else "et nervure comprimées"}",
            10.0, TextAnchor.START, "#374151")
        canvas.text(390.0, 690.0,
            "Moment équilibré par la table Mtu = ${fmt(result.momentTableKNm)} kN.m · " +
                "Mu = ${fmt(result.designMomentKNm)} kN.m", 10.0, TextAnchor.START, "#374151")
        result.notes.take(2).forEachIndexed { index, note ->
            canvas.text(40.0, 706.0 + index * 15.0, "• $note", 9.0, TextAnchor.START, "#6b7280")
        }
        canvas.footer("Fiche générée automatiquement — remplace la sortie BaelT de SOCOTEC")
        return output(canvas, dpi, title)
    }

    // ------------------------------------------------------------------ Koka

    fun renderKokaSheet(
        result: ContinuousSlabResult,
        title: String = "Fiche poutrelle continue Koka — ${result.topology.name}",
        dpi: Double = 300.0
    ): GraphicOutput {
        val canvas = VectorCanvas(1180.0, 820.0)
        frame(canvas, title, "${result.spanCount} travée(s) · ${result.topology.geometryLabel} · " +
            "méthode des trois moments")

        val spans = result.spans
        val totalLength = spans.sumOf { it.lengthMeters }.coerceAtLeast(1.0)
        val left = 90.0
        val right = 1090.0
        val beamY = 250.0
        val scale = (right - left) / totalLength
        var cursor = left
        val supportX = mutableListOf(left)

        // Charges réparties.
        spans.forEach { span ->
            val width = span.lengthMeters * scale
            for (index in 0 until 6) {
                val x = cursor + width * (index + 0.5) / 6.0
                canvas.arrow(x, beamY - 62.0, x, beamY - 10.0, "#dc2626", 1.0, 5.0)
            }
            canvas.text(cursor + width / 2.0, beamY - 70.0,
                "g = ${fmt(result.loads.permanentLineLoadKNPerM)} kN/m · " +
                    "q = ${fmt(result.loads.variableLineLoadKNPerM)} kN/m", 9.0, TextAnchor.MIDDLE, "#dc2626")
            canvas.text(cursor + width / 2.0, beamY + 32.0,
                "Travée ${span.index + 1} · L = ${fmt(span.lengthMeters)} m", 9.5, TextAnchor.MIDDLE, "#111827")
            cursor += width
            supportX += cursor
        }
        canvas.line(left, beamY, right, beamY, "#111827", 4.0)
        supportX.forEach { x ->
            canvas.polygon(listOf(CanvasPoint(x - 14.0, beamY + 6.0), CanvasPoint(x + 14.0, beamY + 6.0),
                CanvasPoint(x, beamY + 26.0)), fill = "#1d4ed8", stroke = "#1e3a8a", strokeWidth = 0.8)
        }

        // Diagramme des moments (allure).
        val diagramTop = 330.0
        val diagramBase = 470.0
        val maxMoment = max(
            spans.maxOfOrNull { max(it.maxSpanMomentUltimateKNm, abs(it.leftMomentUltimateKNm)) } ?: 1.0,
            1.0
        )
        val points = mutableListOf<CanvasPoint>()
        points += CanvasPoint(left, diagramBase)
        var x = left
        spans.forEach { span ->
            val width = span.lengthMeters * scale
            points += CanvasPoint(x, diagramBase + 26.0 * span.leftMomentUltimateKNm / maxMoment)
            points += CanvasPoint(x + width / 2.0, diagramBase - 40.0 * span.maxSpanMomentUltimateKNm / maxMoment)
            points += CanvasPoint(x + width, diagramBase + 26.0 * span.rightMomentUltimateKNm / maxMoment)
            x += width
        }
        canvas.polyline(points, "#7c3aed", 1.8)
        canvas.line(left, diagramBase, right, diagramBase, "#9ca3af", 0.8, doubleArrayOf(4.0, 4.0))
        canvas.text(left, diagramTop - 8.0,
            "Diagramme des moments ultimes (kN.m) — M0,max = ${fmt(maxMoment)} kN.m", 10.0,
            TextAnchor.START, "#4b5563")

        // Tableau des résultats.
        val tableTop = 500.0
        val columns = listOf(90.0, 300.0, 460.0, 620.0, 800.0, 980.0)
        canvas.rect(left, tableTop, right - left, 26.0, "#1f3a5f", "#1f3a5f", 0.8)
        listOf("Travée", "L (m)", "Mu,max (kN.m)", "As (cm²)", "Barres", "Mr (kN.m)").forEachIndexed { index, header ->
            canvas.text(columns[index] + 8.0, tableTop + 17.0, header, 9.5, TextAnchor.START, "#ffffff", bold = true)
        }
        var rowY = tableTop + 26.0
        spans.forEach { span ->
            canvas.line(left, rowY, right, rowY, "#e5e7eb", 0.7)
            val cells = listOf(
                "T${span.index + 1}",
                fmt(span.lengthMeters),
                fmt(span.maxSpanMomentUltimateKNm),
                fmt(span.reinforcement.providedAsCm2),
                span.reinforcement.bars.label,
                fmt(span.reinforcement.resistingMomentKNm)
            )
            cells.forEachIndexed { index, cell ->
                canvas.text(columns[index] + 8.0, rowY + 17.0, cell, 9.0, TextAnchor.START, "#111827")
            }
            rowY += 24.0
        }
        canvas.line(left, rowY, right, rowY, "#9ca3af", 0.8)

        // Section en Té.
        drawTeeSection(
            canvas, 820.0, 600.0, 280.0, 170.0,
            flangeWidthCm = result.topology.flangeWidthBCm,
            webWidthCm = result.topology.ribWidthB0Cm,
            totalHeightCm = result.topology.totalThicknessHtCm,
            flangeThicknessCm = result.topology.compressionSlabH0Cm,
            neutralAxisCm = result.deflection.crackedSection.neutralAxisMeters * 100.0,
            bottomSteelCm2 = spans.maxOfOrNull { it.reinforcement.providedAsCm2 } ?: 0.0,
            topSteelCm2 = result.supports.maxOfOrNull { it.reinforcement.providedAsCm2 } ?: 0.0
        )
        canvas.text(90.0, 730.0,
            "Flèche nuisible Δft = ${fmt(result.deflection.deltaFtMeters * 1000.0)} mm pour " +
                "${fmt(result.deflection.allowableMeters * 1000.0)} mm admissibles · " +
                "φt = ${fmt(result.deflection.muG * 100.0)} %", 10.0, TextAnchor.START, "#374151")
        canvas.text(90.0, 748.0,
            "Contraintes ELS : σbc = ${fmt(spans.maxOfOrNull { it.serviceStress.sigmaConcreteMPa } ?: 0.0)} MPa · " +
                "σst = ${fmt(spans.maxOfOrNull { it.serviceStress.sigmaSteelMPa } ?: 0.0)} MPa",
            10.0, TextAnchor.START, "#374151")
        canvas.footer("Fiche Koka native — poutrelles continues et section en Té")
        return output(canvas, dpi, title)
    }

    // ------------------------------------------------------------------ schémas d'exécution

    fun renderColumnExecution(
        design: ColumnDesignResult,
        dpi: Double = 300.0
    ): GraphicOutput {
        val title = "Schéma d'exécution — poteau ${design.family.name}"
        val canvas = VectorCanvas(1000.0, 780.0)
        frame(canvas, title, "Armatures longitudinales et transversales — BAEL 91 + RPA 2024 § 7.4")

        val groundY = 640.0
        val topY = 150.0
        val centerX = 360.0
        val halfWidth = max(24.0, min(60.0, design.sectionWidthCm * 1.2))
        val columnHeight = groundY - topY
        val nodalHeight = min(140.0, design.transverse.nodalZoneLengthCm * 3.0)
        val lapHeight = min(90.0, design.transverse.lapLengthCm * 3.0)

        canvas.rect(centerX - halfWidth, topY, 2 * halfWidth, columnHeight, "#f3f4f6", "#111827", 1.6)
        canvas.hatch(centerX - halfWidth, groundY - nodalHeight, 2 * halfWidth, nodalHeight,
            8.0, "#dc262655", 45.0)
        canvas.hatch(centerX - halfWidth, topY, 2 * halfWidth, nodalHeight, 8.0, "#dc262655", 45.0)
        canvas.text(centerX + halfWidth + 14.0, groundY - nodalHeight / 2.0,
            "zone nodale Lcr = ${fmt(design.transverse.nodalZoneLengthCm, 0)} cm", 9.0,
            TextAnchor.START, "#b91c1c")

        // Cadres.
        var y = topY + 24.0
        while (y < groundY - 12.0) {
            canvas.line(centerX - halfWidth + 8.0, y, centerX + halfWidth - 8.0, y, "#1d4ed8", 1.2)
            y += 26.0
        }
        // Barres longitudinales.
        listOf(-halfWidth + 12.0, halfWidth - 12.0).forEach { offset ->
            canvas.line(centerX + offset, topY + 10.0, centerX + offset, groundY - 10.0, "#b91c1c", 2.6)
        }

        canvas.dimensionV(groundY, topY, centerX - halfWidth - 44.0,
            "Lcl = ${fmt(design.transverse.clearHeightMeters)} m")
        canvas.dimensionH(centerX - halfWidth, centerX + halfWidth, groundY + 30.0,
            "t = ${fmt(design.sectionWidthCm, 0)} cm")
        canvas.dimensionV(groundY - nodalHeight, groundY, centerX - halfWidth - 76.0,
            "Lcr = ${fmt(design.transverse.nodalZoneLengthCm, 0)} cm", "#b91c1c")
        canvas.dimensionV(topY + nodalHeight, topY, centerX + halfWidth + 108.0,
            "Lrec = ${fmt(design.transverse.lapLengthCm, 0)} cm", "#b91c1c")
        canvas.line(centerX - halfWidth, groundY, centerX + halfWidth, groundY, "#111827", 3.0)

        // Coupe.
        drawRectangularSection(
            canvas, 650.0, 170.0, 280.0, 330.0,
            widthCm = design.sectionWidthCm,
            heightCm = design.sectionDepthCm,
            bottomCoverCm = 3.0,
            topCoverCm = 3.0,
            compressionTop = true,
            neutralAxisCm = null,
            bottomSteelCm2 = design.adoptedAsCm2 / 2.0,
            topSteelCm2 = design.adoptedAsCm2 / 2.0,
            labels = false
        )
        canvas.text(650.0, 540.0,
            "Section ${fmt(design.sectionWidthCm, 0)} × ${fmt(design.sectionDepthCm, 0)} cm", 10.0,
            TextAnchor.START, "#111827", bold = true)
        canvas.text(650.0, 558.0, "As = ${fmt(design.adoptedAsCm2)} cm²", 9.5, TextAnchor.START, "#374151")
        canvas.text(650.0, 576.0, "Barres : ${design.layout?.label ?: "minimales RPA"}", 9.5,
            TextAnchor.START, "#374151")
        canvas.text(650.0, 594.0,
            "Cadres : ${design.transverse.stirrupLabel} e = ${fmt(design.transverse.nodalSpacingCm, 0)} cm " +
                "(nodale) / ${fmt(design.transverse.currentSpacingCm, 0)} cm", 9.5, TextAnchor.START, "#374151")
        canvas.text(650.0, 612.0,
            "ν = ${fmt(design.normalizedAxial, 3)} ≤ ${fmt(design.normalizedAxialLimit, 2)}", 9.5,
            TextAnchor.START, if (design.normalizedAxial <= design.normalizedAxialLimit) "#065f46" else "#b91c1c")
        canvas.footer("Cotes en centimètres · zones nodales hachurées · recouvrement en tête")
        return output(canvas, dpi, title)
    }

    fun renderBeamExecution(
        design: BeamDesignResult,
        dpi: Double = 300.0
    ): GraphicOutput {
        val title = "Schéma d'exécution — poutre ${design.family.name}"
        val canvas = VectorCanvas(1040.0, 760.0)
        frame(canvas, title, "Armatures longitudinales, chapeaux et cadres — BAEL 91 + RPA 2024 § 7.5")

        val left = 90.0
        val right = 950.0
        val beamY = 250.0
        val beamHeight = 34.0
        canvas.rect(left, beamY - beamHeight / 2.0, right - left, beamHeight, "#f3f4f6", "#111827", 1.4)
        canvas.rect(left - 18.0, beamY - 52.0, 18.0, 104.0, "#e5e7eb", "#111827", 1.0)
        canvas.rect(right, beamY - 52.0, 18.0, 104.0, "#e5e7eb", "#111827", 1.0)

        val nodalWidth = min(110.0, design.transverse.nodalZoneLengthCm * 2.4)
        canvas.hatch(left, beamY - beamHeight / 2.0, nodalWidth, beamHeight, 8.0, "#dc262655", 45.0)
        canvas.hatch(right - nodalWidth, beamY - beamHeight / 2.0, nodalWidth, beamHeight, 8.0, "#dc262655", 45.0)

        // Barres filantes basses et chapeaux.
        canvas.line(left + 6.0, beamY + 8.0, right - 6.0, beamY + 8.0, "#b91c1c", 2.4)
        canvas.line(left + 6.0, beamY - 8.0, right - 6.0, beamY - 8.0, "#1d4ed8", 1.6)
        canvas.line(left + 20.0, beamY - 14.0, left + nodalWidth + 110.0, beamY - 14.0, "#1d4ed8", 2.4)
        canvas.line(right - nodalWidth - 110.0, beamY - 14.0, right - 20.0, beamY - 14.0, "#1d4ed8", 2.4)

        // Cadres.
        var x = left + 22.0
        while (x < right - 22.0) {
            canvas.line(x, beamY - beamHeight / 2.0 + 3.0, x, beamY + beamHeight / 2.0 - 3.0, "#059669", 1.0)
            x += 26.0
        }

        val spanCm = design.family.maxClearSpanMeters * 100.0
        canvas.dimensionH(left, right, beamY + 80.0,
            "l' = ${fmt(design.family.maxClearSpanMeters)} m")
        canvas.dimensionH(left, left + nodalWidth, beamY + 110.0,
            "S = ${fmt(design.transverse.nodalZoneLengthCm, 0)} cm", "#b91c1c")
        canvas.dimensionH(left + nodalWidth, left + nodalWidth + 110.0, beamY + 140.0,
            "S' = ${fmt(design.transverse.lapLengthCm, 0)} cm", "#1d4ed8")
        canvas.dimensionV(beamY - beamHeight / 2.0, beamY + beamHeight / 2.0, left - 34.0,
            "h = ${fmt(design.heightCm, 0)} cm")
        canvas.text(left, beamY - 62.0,
            "Chapeaux ${design.supportLayout?.label ?: "selon plan"} · filantes ${design.spanLayout?.label ?: "-"}",
            9.5, TextAnchor.START, "#1d4ed8")
        canvas.text(left, beamY - 46.0,
            "Cadres ${design.transverse.stirrupLabel} e = ${fmt(design.transverse.nodalSpacingCm, 0)} cm " +
                "(nodale) / ${fmt(design.transverse.currentSpacingCm, 0)} cm (courante)", 9.5,
            TextAnchor.START, "#065f46")

        drawRectangularSection(
            canvas, 700.0, 470.0, 240.0, 210.0,
            widthCm = design.widthCm,
            heightCm = design.heightCm,
            bottomCoverCm = 3.0,
            topCoverCm = 3.0,
            compressionTop = true,
            neutralAxisCm = design.governingSection?.bael?.neutralAxisMeters?.times(100.0),
            bottomSteelCm2 = design.bottomAdoptedAsCm2,
            topSteelCm2 = design.topAdoptedAsCm2,
            labels = false
        )
        canvas.text(700.0, 706.0, "As,inf = ${fmt(design.bottomAdoptedAsCm2)} cm² · " +
            "As,sup = ${fmt(design.topAdoptedAsCm2)} cm²", 9.5, TextAnchor.START, "#111827")
        canvas.text(700.0, 724.0, "Portée max = ${fmt(design.family.maxClearSpanMeters)} m",
            9.0, TextAnchor.START, "#4b5563")
        canvas.text(90.0, 700.0,
            "Vérifications : ${design.checks.count { it.isSatisfied }}/${design.checks.size} satisfaites · " +
                "τ = ${fmt(design.transverse.shearVerifications.maxOfOrNull { it.tauMPa } ?: 0.0)} MPa " +
                "≤ ${fmt(design.transverse.shearVerifications.maxOfOrNull { it.tauLimitMPa } ?: 0.0)} MPa",
            9.5, TextAnchor.START, "#374151")
        canvas.footer("Cotes en centimètres · ${fmt(spanCm, 0)} cm de portée libre")
        return output(canvas, dpi, title)
    }

    fun renderWallExecution(
        design: WallDesignResult,
        dpi: Double = 300.0
    ): GraphicOutput {
        val title = "Schéma d'exécution — voile ${design.family.pierName}"
        val canvas = VectorCanvas(1100.0, 720.0)
        frame(canvas, title, "Éléments de rive, âme et ductilité locale — RPA 2024 § 7.7.4")

        val left = 100.0
        val wallWidth = 760.0
        val wallY = 200.0
        val wallHeight = 26.0
        val thicknessPx = max(18.0, min(46.0, design.family.thicknessBwMeters * 200.0))
        canvas.rect(left, wallY, wallWidth, wallHeight, "#f3f4f6", "#111827", 1.4)

        val leftLength = min(wallWidth * 0.35, design.boundaryLeft.lengthLcCm * 2.0)
        val rightLength = min(wallWidth * 0.35, design.boundaryRight.lengthLcCm * 2.0)
        canvas.rect(left, wallY, leftLength, wallHeight, "#dc262644", "#b91c1c", 1.2)
        canvas.rect(left + wallWidth - rightLength, wallY, rightLength, wallHeight, "#dc262644", "#b91c1c", 1.2)
        canvas.text(left + leftLength / 2.0, wallY - 10.0,
            "rive gauche ${design.boundaryLeft.barLabel}", 9.0, TextAnchor.MIDDLE, "#b91c1c")
        canvas.text(left + wallWidth - rightLength / 2.0, wallY - 10.0,
            "rive droite ${design.boundaryRight.barLabel}", 9.0, TextAnchor.MIDDLE, "#b91c1c")

        // Nappes verticales et horizontales.
        var x = left + leftLength + 16.0
        while (x < left + wallWidth - rightLength - 8.0) {
            canvas.line(x, wallY + 2.0, x, wallY + wallHeight - 2.0, "#1d4ed8", 1.0)
            x += 22.0
        }
        var y = wallY + 2.0
        while (y < wallY + wallHeight) {
            canvas.line(left + 8.0, y, left + wallWidth - 8.0, y, "#059669", 1.0)
            y += 7.0
        }

        canvas.dimensionH(left, left + wallWidth, wallY + 70.0,
            "lw = ${fmt(design.family.lengthLwMeters)} m")
        canvas.dimensionV(wallY, wallY + wallHeight, left - 30.0,
            "bw = ${fmt(design.family.thicknessBwMeters * 100.0, 0)} cm")
        canvas.dimensionH(left, left + leftLength, wallY + 100.0,
            "lc = ${fmt(design.boundaryLeft.lengthLcCm, 0)} cm", "#b91c1c")
        canvas.text(left, wallY + 130.0,
            "Âme : ${design.web.layers} nappe(s) ${design.web.barLabel} · " +
                "Sv = ${fmt(design.web.verticalSpacingCm, 0)} cm · Sh = ${fmt(design.web.horizontalSpacingCm, 0)} cm",
            9.5, TextAnchor.START, "#065f46")

        dataBox(
            canvas, 100.0, 380.0, 430.0,
            "Ductilité et cisaillement",
            listOf(
                "νd" to fmt(design.qualification.normalizedAxial, 3),
                "τb" to "${fmt(design.qualification.tauMPa)} MPa",
                "0,2 fc28" to "${fmt(design.qualification.tauLimitMPa)} MPa",
                "h_cr" to "${fmt(design.envelope.criticalHeight.retainedMeters)} m",
                "μφ requis" to fmt(design.ductility.requiredCurvatureDuctility, 3),
                "μφ disponible" to fmt(design.ductility.availableCurvatureDuctility, 3),
                "Confinement" to "${fmt(design.ductility.providedConfinementLengthMeters)} m",
                "Admissible" to "${fmt(design.ductility.admissibleConfinementLengthMeters)} m"
            )
        )
        dataBox(
            canvas, 580.0, 380.0, 420.0,
            "Base du voile (BaelR)",
            listOf(
                "Section" to "${fmt(design.family.lengthLwMeters * 100.0, 0)} × ${fmt(design.family.thicknessBwMeters * 100.0, 0)} cm",
                "N" to "${fmt(design.designAxialKN)} kN",
                "M" to "${fmt(design.designMomentKNm)} kN.m",
                "y0" to "${fmt(design.bael.neutralAxisMeters * 100.0)} cm",
                "As calculée" to "${fmt(design.bael.requiredSteelCm2)} cm²",
                "Combo" to design.designCombo
            )
        )
        canvas.footer("Ferraillage de la zone critique, des rives et de l'âme")
        return output(canvas, dpi, title)
    }

    fun renderParapetSheet(
        result: ParapetDesignResult,
        dpi: Double = 300.0
    ): GraphicOutput {
        val title = "Fiche acrotère — géométrie et ferraillage"
        val canvas = VectorCanvas(1000.0, 700.0)
        frame(canvas, title, "H = ${fmt(result.geometry.heightMeters)} m · e = " +
            "${fmt(result.geometry.stemThicknessMeters)} m · α = ${fmt(result.geometry.slopeAngleDegrees)}°")

        val baseX = 330.0
        val baseY = 520.0
        val scale = 430.0
        fun x(value: Double): Double = baseX + value * scale
        fun y(value: Double): Double = baseY - value * scale

        val points = listOf(
            CanvasPoint(0.0, 0.0),
            CanvasPoint(result.geometry.stemThicknessMeters, 0.0),
            CanvasPoint(result.geometry.stemThicknessMeters, result.geometry.heightMeters),
            CanvasPoint(-result.geometry.corniceProjectionMeters, result.geometry.heightMeters),
            CanvasPoint(-result.geometry.corniceProjectionMeters,
                result.geometry.heightMeters - result.geometry.corniceStraightHeightMeters),
            CanvasPoint(0.0, result.geometry.heightMeters - result.geometry.corniceStraightHeightMeters -
                result.geometry.corniceSlopedHeightMeters)
        ).map { CanvasPoint(x(it.x), y(it.y)) }
        canvas.polygon(points, "#e5e7eb", "#111827", 2.0)

        canvas.dimensionV(y(0.0), y(result.geometry.heightMeters),
            x(result.geometry.stemThicknessMeters) + 40.0, "H = ${fmt(result.geometry.heightMeters * 100.0, 0)} cm")
        canvas.dimensionH(x(0.0), x(result.geometry.stemThicknessMeters), y(0.0) + 34.0,
            "e = ${fmt(result.geometry.stemThicknessMeters * 100.0, 0)} cm")
        canvas.dimensionH(x(-result.geometry.corniceProjectionMeters), x(0.0),
            y(result.geometry.heightMeters) - 24.0,
            "b = ${fmt(result.geometry.corniceProjectionMeters * 100.0, 0)} cm")

        canvas.arrow(x(-result.geometry.corniceProjectionMeters) - 110.0,
            y(result.geometry.heightMeters) - 12.0,
            x(-result.geometry.corniceProjectionMeters) - 10.0,
            y(result.geometry.heightMeters) - 12.0)
        canvas.text(x(-result.geometry.corniceProjectionMeters) - 118.0,
            y(result.geometry.heightMeters) - 26.0,
            "Q = ${fmt(result.loads.designHorizontalQKNPerM)} kN/ml", 10.0, TextAnchor.END, "#dc2626")

        // Nappes.
        canvas.line(x(result.geometry.stemThicknessMeters * 0.3), y(0.03),
            x(result.geometry.stemThicknessMeters * 0.3), y(result.geometry.heightMeters - 0.03),
            "#1d4ed8", 2.2)
        canvas.line(x(result.geometry.stemThicknessMeters * 0.7), y(0.03),
            x(result.geometry.stemThicknessMeters * 0.7), y(result.geometry.heightMeters - 0.03),
            "#1d4ed8", 2.2)
        var height = 0.08
        while (height < result.geometry.heightMeters - 0.03) {
            canvas.line(x(result.geometry.stemThicknessMeters * 0.28), y(height),
                x(result.geometry.stemThicknessMeters * 0.72), y(height), "#059669", 1.0)
            height += 0.07
        }
        canvas.text(x(result.geometry.stemThicknessMeters) + 90.0,
            y(result.geometry.heightMeters * 0.6),
            "Nappe principale ${result.mainSteel.label}", 10.0, TextAnchor.START, "#1d4ed8", bold = true)
        canvas.text(x(result.geometry.stemThicknessMeters) + 90.0,
            y(result.geometry.heightMeters * 0.6) + 17.0,
            "${fmt(result.mainSteel.providedAsCm2PerM)} cm²/ml", 9.5, TextAnchor.START, "#1d4ed8")
        canvas.text(x(result.geometry.stemThicknessMeters) + 90.0,
            y(result.geometry.heightMeters * 0.36),
            "Répartition ${result.distributionSteel.label}", 10.0, TextAnchor.START, "#059669", bold = true)
        canvas.text(x(result.geometry.stemThicknessMeters) + 90.0,
            y(result.geometry.heightMeters * 0.36) + 17.0,
            "${fmt(result.distributionSteel.providedAsCm2PerM)} cm²/ml", 9.5, TextAnchor.START, "#059669")

        canvas.text(60.0, 610.0,
            "Wp = ${fmt(result.loads.totalWeightWpKNPerM)} kN/ml · " +
                "Fp = ${fmt(result.loads.seismicForceFpKNPerM)} kN/ml · Q retenu = " +
                "${fmt(result.loads.designHorizontalQKNPerM)} kN/ml", 10.0, TextAnchor.START, "#374151")
        canvas.text(60.0, 628.0,
            "S = ${fmt(result.geometry.sectionAreaM2, 4)} m² · P = " +
                "${fmt(result.geometry.developedPerimeterMeters, 3)} m · " +
                "situation ${result.governingSituation.label}", 10.0, TextAnchor.START, "#374151")
        canvas.footer("T8 e = ${fmt(result.mainSteel.spacingMeters * 100.0, 0)} cm — schéma coté de l'acrotère")
        return output(canvas, dpi, title)
    }

    // ------------------------------------------------------------------ dessins de sections

    private fun drawRectangularSection(
        canvas: VectorCanvas,
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        widthCm: Double,
        heightCm: Double,
        bottomCoverCm: Double,
        topCoverCm: Double,
        compressionTop: Boolean,
        neutralAxisCm: Double?,
        bottomSteelCm2: Double,
        topSteelCm2: Double,
        labels: Boolean = true
    ) {
        val scale = min(width / widthCm.coerceAtLeast(1.0), height / heightCm.coerceAtLeast(1.0))
        val drawWidth = widthCm * scale
        val drawHeight = heightCm * scale
        val x = left + (width - drawWidth) / 2.0
        val y = top + (height - drawHeight) / 2.0
        canvas.rect(x, y, drawWidth, drawHeight, "#f9fafb", "#111827", 1.8)

        val hatchHeight = max(8.0, drawHeight * 0.22)
        if (compressionTop) {
            canvas.hatch(x, y, drawWidth, hatchHeight, 7.0, "#6b728055", 45.0)
            canvas.text(x + drawWidth / 2.0, y + hatchHeight / 2.0, "zone comprimée", 8.0,
                TextAnchor.MIDDLE, "#4b5563")
        } else {
            canvas.hatch(x, y + drawHeight - hatchHeight, drawWidth, hatchHeight, 7.0, "#6b728055", 45.0)
            canvas.text(x + drawWidth / 2.0, y + drawHeight - hatchHeight / 2.0, "zone comprimée", 8.0,
                TextAnchor.MIDDLE, "#4b5563")
        }

        if (neutralAxisCm != null && neutralAxisCm > 0.0 && neutralAxisCm < heightCm) {
            val axisY = y + neutralAxisCm * scale
            canvas.line(x - 14.0, axisY, x + drawWidth + 14.0, axisY, "#dc2626", 1.0, doubleArrayOf(6.0, 4.0))
            canvas.text(x + drawWidth + 18.0, axisY + 4.0, "y0 = ${fmt(neutralAxisCm)} cm", 9.0,
                TextAnchor.START, "#dc2626")
        }

        // Barres : quatre files symboliques par nappe.
        val bottomY = y + drawHeight - bottomCoverCm * scale
        val topSteelY = y + topCoverCm * scale
        val barRadius = max(2.5, min(5.0, scale * 1.2))
        val bottomCount = barsFor(bottomSteelCm2)
        val topCount = barsFor(topSteelCm2)
        repeat(bottomCount) { index ->
            val bx = x + drawWidth * (index + 1) / (bottomCount + 1)
            canvas.circle(bx, bottomY, barRadius, "#b91c1c", "#7f1d1d", 0.8)
        }
        repeat(topCount) { index ->
            val bx = x + drawWidth * (index + 1) / (topCount + 1)
            canvas.circle(bx, topSteelY, barRadius, "#1d4ed8", "#1e3a8a", 0.8)
        }

        if (labels) {
            canvas.dimensionH(x, x + drawWidth, y + drawHeight + 30.0,
                "b = ${fmt(widthCm, 0)} cm")
            canvas.dimensionV(y, y + drawHeight, x - 24.0, "h = ${fmt(heightCm, 0)} cm")
            canvas.text(x, y + drawHeight + 52.0,
                "As,inf = ${fmt(bottomSteelCm2)} cm² · As,sup = ${fmt(topSteelCm2)} cm²", 9.0,
                TextAnchor.START, "#374151")
        }
    }

    private fun drawTeeSection(
        canvas: VectorCanvas,
        left: Double,
        top: Double,
        width: Double,
        height: Double,
        flangeWidthCm: Double,
        webWidthCm: Double,
        totalHeightCm: Double,
        flangeThicknessCm: Double,
        neutralAxisCm: Double,
        bottomSteelCm2: Double,
        topSteelCm2: Double
    ) {
        val scale = min(width / flangeWidthCm.coerceAtLeast(1.0), height / totalHeightCm.coerceAtLeast(1.0))
        val drawWidth = flangeWidthCm * scale
        val drawHeight = totalHeightCm * scale
        val x = left + (width - drawWidth) / 2.0
        val y = top + (height - drawHeight) / 2.0

        val flangeHeight = flangeThicknessCm * scale
        val webWidth = webWidthCm * scale
        val webX = x + (drawWidth - webWidth) / 2.0

        val points = listOf(
            CanvasPoint(x, y),
            CanvasPoint(x + drawWidth, y),
            CanvasPoint(x + drawWidth, y + flangeHeight),
            CanvasPoint(webX + webWidth, y + flangeHeight),
            CanvasPoint(webX + webWidth, y + drawHeight),
            CanvasPoint(webX, y + drawHeight),
            CanvasPoint(webX, y + flangeHeight),
            CanvasPoint(x, y + flangeHeight)
        )
        canvas.polygon(points, "#f9fafb", "#111827", 1.8)

        val compressionInFlangeOnly = neutralAxisCm <= flangeThicknessCm + 1e-9
        val hatchBottom = if (compressionInFlangeOnly) flangeHeight else drawHeight
        canvas.hatch(x, y, drawWidth, hatchBottom, 7.0, "#6b728055", 45.0)
        canvas.text(x + drawWidth / 2.0, y + max(9.0, flangeHeight / 2.0 + 3.0), "table comprimée", 8.0,
            TextAnchor.MIDDLE, "#4b5563")

        if (neutralAxisCm > 0.0 && neutralAxisCm < totalHeightCm) {
            val axisY = y + neutralAxisCm * scale
            canvas.line(x - 14.0, axisY, x + drawWidth + 14.0, axisY, "#dc2626", 1.0, doubleArrayOf(6.0, 4.0))
            canvas.text(x + drawWidth + 18.0, axisY + 4.0, "y0 = ${fmt(neutralAxisCm)} cm", 9.0,
                TextAnchor.START, "#dc2626")
        }

        val barRadius = max(2.5, min(5.0, scale * 1.2))
        val bottomY = y + drawHeight - max(6.0, scale * 4.0)
        val topY = y + max(6.0, scale * 4.0)
        val bottomCount = barsFor(bottomSteelCm2)
        val topCount = barsFor(topSteelCm2)
        repeat(bottomCount) { index ->
            canvas.circle(webX + webWidth * (index + 1) / (bottomCount + 1), bottomY, barRadius,
                "#b91c1c", "#7f1d1d", 0.8)
        }
        repeat(topCount) { index ->
            canvas.circle(x + drawWidth * (index + 1) / (topCount + 1), topY, barRadius,
                "#1d4ed8", "#1e3a8a", 0.8)
        }

        canvas.dimensionH(x, x + drawWidth, y + drawHeight + 30.0, "B = ${fmt(flangeWidthCm, 0)} cm")
        canvas.dimensionH(webX, webX + webWidth, y + drawHeight + 52.0, "b0 = ${fmt(webWidthCm, 0)} cm")
        canvas.dimensionV(y, y + drawHeight, x - 24.0, "h = ${fmt(totalHeightCm, 0)} cm")
        canvas.dimensionV(y, y + flangeHeight, x + drawWidth + 46.0, "h0 = ${fmt(flangeThicknessCm, 0)} cm")
    }

    // ------------------------------------------------------------------ helpers

    private fun frame(canvas: VectorCanvas, title: String, subtitle: String) {
        canvas.rect(20.0, 20.0, canvas.width - 40.0, canvas.height - 40.0, null, "#1f3a5f", 1.4)
        canvas.title(title, subtitle, 40.0)
    }

    private fun dataBox(
        canvas: VectorCanvas,
        x: Double,
        y: Double,
        width: Double,
        title: String,
        rows: List<Pair<String, String>>
    ) {
        val rowHeight = 22.0
        val height = 34.0 + rows.size * rowHeight
        canvas.rect(x, y, width, height, "#f8fafc", "#cbd5e1", 0.9)
        canvas.rect(x, y, width, 26.0, "#1f3a5f", "#1f3a5f", 0.9)
        canvas.text(x + 10.0, y + 18.0, title, 10.0, TextAnchor.START, "#ffffff", bold = true)
        var rowY = y + 44.0
        rows.forEach { (label, value) ->
            canvas.text(x + 10.0, rowY, label, 9.5, TextAnchor.START, "#4b5563")
            canvas.text(x + width - 10.0, rowY, value, 9.5, TextAnchor.END, "#111827", bold = true)
            rowY += rowHeight
        }
    }

    private fun barsFor(areaCm2: Double): Int = when {
        areaCm2 <= 0.0 -> 0
        areaCm2 < 4.0 -> 2
        areaCm2 < 10.0 -> 3
        areaCm2 < 20.0 -> 4
        areaCm2 < 35.0 -> 5
        else -> 6
    }

    private fun output(canvas: VectorCanvas, dpi: Double, caption: String): GraphicOutput = GraphicOutput(
        svg = canvas.svg(),
        png = canvas.png(dpi),
        width = Math.round(canvas.width).toInt(),
        height = Math.round(canvas.height).toInt(),
        caption = caption
    )

    fun writePng(output: GraphicOutput, path: Path): Path {
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeBytes(output.png)
        return path
    }

    fun writeSvg(output: GraphicOutput, path: Path): Path {
        path.parent?.toFile()?.mkdirs()
        path.toFile().writeText(output.svg, Charsets.UTF_8)
        return path
    }

    private fun fmt(value: Double, decimals: Int = 2): String = Checks.format(value, decimals)
}
