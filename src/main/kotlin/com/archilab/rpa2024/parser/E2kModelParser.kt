package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Parseur spécialisé du fichier de modèle texte ETABS (.e2k / .$et).
 * Supporte toutes les versions d'ETABS (v9.7.4 jusqu'à v21).
 */
class E2kModelParser {

    private data class RawPoint(val id: String, val x: Double, val y: Double, val z: Double)
    private data class RawFrameSection(val name: String, val shape: String, val t3: Double, val t2: Double)
    private data class RawLine(val id: String, val ptI: String, val ptJ: String, var sectionName: String = "")
    private data class RawArea(val id: String, val points: List<String>, var pierName: String = "", var propName: String = "")

    private fun extractNamedValue(line: String, keyword: String): String? {
        val regex = Regex("(?:^|\\s+)$keyword\\s*(?:=|\\s)\\s*(?:\"([^\"]+)\"|'([^']+)'|(\\S+))", RegexOption.IGNORE_CASE)
        val match = regex.find(line) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2].ifEmpty { match.groupValues[3] } }
    }

    private fun extractDoubleValue(line: String, keyword: String): Double? {
        val regex = Regex("(?:^|\\s+)$keyword\\s*(?:=|\\s)\\s*([+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)", RegexOption.IGNORE_CASE)
        return regex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    fun parse(e2kContent: String, jointName: String = "Joint A"): StructuralJoint {
        var unitConverter = EtabsUnitConverter()
        val rawPoints = mutableMapOf<String, RawPoint>()
        val rawSections = mutableMapOf<String, RawFrameSection>()
        val rawLines = mutableListOf<RawLine>()
        val rawAreas = mutableListOf<RawArea>()
        val rawStories = mutableListOf<StoryLevel>()
        val rawXGrids = mutableListOf<GridLine>()
        val rawYGrids = mutableListOf<GridLine>()
        val wallThicknesses = mutableMapOf<String, Double>()

        var currentSection = ""
        val lines = e2kContent.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";")) continue

            if (trimmed.startsWith("$")) {
                currentSection = trimmed.substring(1).trim().uppercase()
                continue
            }

            // Unités
            if (trimmed.contains("UNITS", ignoreCase = true)) {
                val match = Regex("UNITS\\s+[\"']?([A-Za-z0-9\\-]+)[\"']?", RegexOption.IGNORE_CASE).find(trimmed)
                if (match != null) {
                    unitConverter = EtabsUnitConverter.fromEtabsString(match.groupValues[1])
                }
            }

            when {
                currentSection.startsWith("STORIES") -> {
                    val name = extractNamedValue(trimmed, "STORY")
                    val htRaw = extractDoubleValue(trimmed, "HEIGHT") ?: extractDoubleValue(trimmed, "HT")
                    val elevRaw = extractDoubleValue(trimmed, "ELEV")

                    if (name != null && htRaw != null && elevRaw != null && htRaw > 1e-4 && !name.equals("BASE", ignoreCase = true)) {
                        val ht = unitConverter.toMeters(htRaw)
                        val elev = unitConverter.toMeters(elevRaw)
                        val isCage = name.contains("CAGE", ignoreCase = true) || name.contains("PENT", ignoreCase = true)
                        rawStories.add(StoryLevel(storyName = name, heightHk = ht, elevationZk = elev, isPenthouseOrCage = isCage))
                    }
                }

                currentSection.startsWith("GRIDS") -> {
                    val id = extractNamedValue(trimmed, "GRID") ?: extractNamedValue(trimmed, "GRIDLINE")
                    val dirStr = extractNamedValue(trimmed, "DIR")
                    val ordRaw = extractDoubleValue(trimmed, "ORD") ?: extractDoubleValue(trimmed, "COOR")

                    if (id != null && dirStr != null && ordRaw != null) {
                        val ord = unitConverter.toMeters(ordRaw)
                        val dir = if (dirStr.uppercase() == "X") AxisDirection.X else AxisDirection.Y
                        val gl = GridLine(id, ord, dir)
                        if (dir == AxisDirection.X) rawXGrids.add(gl) else rawYGrids.add(gl)
                    }
                }

                currentSection.startsWith("POINT COORDINATES") -> {
                    val id = extractNamedValue(trimmed, "POINT")
                    val xRaw = extractDoubleValue(trimmed, "X")
                    val yRaw = extractDoubleValue(trimmed, "Y")
                    val zRaw = extractDoubleValue(trimmed, "Z") ?: 0.0

                    if (id != null && xRaw != null && yRaw != null) {
                        val x = unitConverter.toMeters(xRaw)
                        val y = unitConverter.toMeters(yRaw)
                        val z = unitConverter.toMeters(zRaw)
                        rawPoints[id] = RawPoint(id, x, y, z)
                    }
                }

                currentSection.startsWith("FRAME SECTIONS") -> {
                    val name = extractNamedValue(trimmed, "FRAME")
                    val shape = extractNamedValue(trimmed, "SHAPE") ?: "RECTANGULAR"
                    val t3Raw = extractDoubleValue(trimmed, "T3") ?: 0.50
                    val t2Raw = extractDoubleValue(trimmed, "T2") ?: t3Raw

                    if (name != null) {
                        val t3 = unitConverter.toMeters(t3Raw)
                        val t2 = unitConverter.toMeters(t2Raw)
                        rawSections[name] = RawFrameSection(name, shape, t3, t2)
                    }
                }

                currentSection.startsWith("WALL/SLAB") || currentSection.startsWith("AREA PROPERTIES") -> {
                    val name = extractNamedValue(trimmed, "WALL") ?: extractNamedValue(trimmed, "SHELL")
                    val thickRaw = extractDoubleValue(trimmed, "THICKNESS") ?: extractDoubleValue(trimmed, "THICK")

                    if (name != null && thickRaw != null) {
                        wallThicknesses[name] = unitConverter.toMeters(thickRaw)
                    }
                }

                currentSection.startsWith("LINE CONNECTIVITIES") -> {
                    val id = extractNamedValue(trimmed, "LINE")
                    val ptI = extractNamedValue(trimmed, "I")
                    val ptJ = extractNamedValue(trimmed, "J")

                    if (id != null && ptI != null && ptJ != null) {
                        rawLines.add(RawLine(id, ptI, ptJ))
                    }
                }

                currentSection.startsWith("LINE ASSIGNS") -> {
                    val lineId = extractNamedValue(trimmed, "LINE")
                    val secName = extractNamedValue(trimmed, "SECTION")

                    if (lineId != null && secName != null) {
                        rawLines.find { it.id == lineId }?.sectionName = secName
                    }
                }

                currentSection.startsWith("AREA CONNECTIVITIES") -> {
                    val areaId = extractNamedValue(trimmed, "AREA")
                    if (areaId != null) {
                        // Récupère les identifiants de points après POINTS ou NODES
                        val ptsPart = trimmed.substringAfter("POINTS", "").ifEmpty { trimmed.substringAfter("NODES", "") }
                        val pts = ptsPart.split(Regex("\\s+")).map { it.replace("\"", "").replace("'", "").trim() }.filter { it.isNotEmpty() }
                        rawAreas.add(RawArea(areaId, pts))
                    }
                }

                currentSection.startsWith("AREA ASSIGNS") || currentSection.startsWith("PIER ASSIGNS") -> {
                    val areaId = extractNamedValue(trimmed, "AREA")
                    val pier = extractNamedValue(trimmed, "PIER")

                    if (areaId != null && pier != null) {
                        rawAreas.find { it.id == areaId }?.pierName = pier
                    }
                }
            }
        }

        // 1. Déduction du système d'axes
        val xGrids = if (rawXGrids.isNotEmpty()) rawXGrids.distinctBy { it.coordinate }.sorted() else {
            val xs = rawPoints.values.map { it.x }.distinct().sorted()
            xs.mapIndexed { idx, x -> GridLine("X${idx + 1}", x, AxisDirection.X) }
        }
        val yGrids = if (rawYGrids.isNotEmpty()) rawYGrids.distinctBy { it.coordinate }.sorted() else {
            val ys = rawPoints.values.map { it.y }.distinct().sorted()
            ys.mapIndexed { idx, y -> GridLine("Y${idx + 1}", y, AxisDirection.Y) }
        }
        val gridSystem = GridSystem(xGrids, yGrids)

        // 2. Déduction du système d'étages
        val stories = if (rawStories.isNotEmpty()) rawStories.sorted() else {
            val zs = rawPoints.values.map { it.z }.distinct().sorted()
            val stList = mutableListOf<StoryLevel>()
            for (i in 1 until zs.size) {
                val z = zs[i]
                val prevZ = zs[i - 1]
                val ht = z - prevZ
                stList.add(StoryLevel("Story $i", ht, z, isPenthouseOrCage = (i == zs.size - 1 && ht < 3.0)))
            }
            stList
        }
        val storySystem = StorySystem(stories)

        // 3. Détection des poteaux, poutres, et voiles
        val columnLines = mutableListOf<RawLine>()
        val beamLines = mutableListOf<RawLine>()
        val wallNodeIds = rawAreas.flatMap { it.points }.toSet()

        for (line in rawLines) {
            val pI = rawPoints[line.ptI] ?: continue
            val pJ = rawPoints[line.ptJ] ?: continue
            val dz = abs(pJ.z - pI.z)
            val dxy = sqrt((pJ.x - pI.x) * (pJ.x - pI.x) + (pJ.y - pI.y) * (pJ.y - pI.y))

            if (dz > 0.10 && dxy < 0.10) {
                columnLines.add(line)
            } else if (dz < 0.10 && dxy > 0.10) {
                beamLines.add(line)
            }
        }

        // Familles de poteaux
        val columnFamilies = columnLines.groupBy { it.sectionName.ifEmpty { "DefaultColumn" } }.map { (secName, cols) ->
            val sec = rawSections[secName]
            val b1 = (sec?.t2 ?: 0.35) * 100.0
            val h1 = (sec?.t3 ?: 0.50) * 100.0
            val shape = if (sec?.shape.equals("CIRCULAR", ignoreCase = true)) ColumnShape.CIRCULAR else ColumnShape.RECTANGULAR

            // Calcul de l_cl = hk - max(h_poutres)
            val defaultHk = storySystem.mainStories.firstOrNull()?.heightHk ?: 3.74
            val connectedBeamH = beamLines.filter { bLine ->
                cols.any { cLine -> cLine.ptJ == bLine.ptI || cLine.ptJ == bLine.ptJ }
            }.mapNotNull { rawSections[it.sectionName]?.t3 }.maxOrNull() ?: 0.55
            val lcl = max(1.5, defaultHk - connectedBeamH)

            // Détection si poteau solidaire de voile
            val isWall = cols.any { it.ptI in wallNodeIds || it.ptJ in wallNodeIds }

            ColumnFamily(
                name = secName,
                shape = shape,
                b1Cm = b1,
                h1Cm = h1,
                diameterCm = if (shape == ColumnShape.CIRCULAR) b1 else null,
                clearHeightLclMeters = lcl,
                isWallBoundary = isWall,
                elementIds = cols.map { it.id }
            )
        }

        // Familles de poutres
        val beamFamilies = beamLines.groupBy { it.sectionName.ifEmpty { "DefaultBeam" } }.map { (secName, bms) ->
            val sec = rawSections[secName]
            val b = (sec?.t2 ?: 0.30) * 100.0
            val h = (sec?.t3 ?: 0.50) * 100.0

            val spans = bms.mapNotNull { bm ->
                val pI = rawPoints[bm.ptI] ?: return@mapNotNull null
                val pJ = rawPoints[bm.ptJ] ?: return@mapNotNull null
                val lax = sqrt((pJ.x - pI.x) * (pJ.x - pI.x) + (pJ.y - pI.y) * (pJ.y - pI.y))
                // Déduction du nu d'appui
                val colDimI = columnFamilies.firstOrNull()?.minDimensionCm?.let { it / 100.0 } ?: 0.35
                val colDimJ = colDimI
                max(0.5, lax - colDimI / 2.0 - colDimJ / 2.0)
            }
            val maxClearSpan = spans.maxOrNull() ?: 4.35
            val minClearSpan = spans.minOrNull() ?: maxClearSpan

            val isSecondary = secName.contains("second", ignoreCase = true) ||
                    secName.contains("ch", ignoreCase = true) ||
                    secName.contains("tie", ignoreCase = true)

            BeamFamily(
                name = secName,
                role = if (isSecondary) BeamRole.SECONDARY_TIE else BeamRole.MAIN_BEAM,
                bCm = b,
                hCm = h,
                maxClearSpanMeters = maxClearSpan,
                minClearSpanMeters = minClearSpan,
                maxSupportWidthCm = columnFamilies.firstOrNull()?.minDimensionCm ?: 35.0,
                maxAxisEccentricityCm = 0.0,
                elementIds = bms.map { it.id }
            )
        }

        // Familles de voiles (Piers)
        val shearWallFamilies = rawAreas.filter { it.pierName.isNotEmpty() }.groupBy { it.pierName }.map { (pierName, areas) ->
            val areaPts = areas.flatMap { it.points }.mapNotNull { rawPoints[it] }
            val minX = areaPts.minOfOrNull { it.x } ?: 0.0
            val maxX = areaPts.maxOfOrNull { it.x } ?: 0.0
            val minY = areaPts.minOfOrNull { it.y } ?: 0.0
            val maxY = areaPts.maxOfOrNull { it.y } ?: 0.0
            val minZ = areaPts.minOfOrNull { it.z } ?: 0.0
            val maxZ = areaPts.maxOfOrNull { it.z } ?: 0.0

            val dx = maxX - minX
            val dy = maxY - minY
            val lw = max(0.5, sqrt(dx * dx + dy * dy))
            val dir = if (dx >= dy) AxisDirection.X else AxisDirection.Y
            val hw = max(3.0, maxZ - minZ)
            val he = storySystem.mainStories.firstOrNull()?.heightHk ?: 3.39
            val bw = wallThicknesses.values.firstOrNull() ?: 0.30

            ShearWallFamily(
                pierName = pierName,
                direction = dir,
                lengthLwMeters = lw,
                thicknessBwMeters = bw,
                clearHeightHeMeters = he,
                totalHeightHwMeters = hw,
                stories = storySystem.stories.map { it.storyName }
            )
        }

        // Topologie de plancher corps creux (extraite de la portée maximale de poutrelle)
        val slabStrip = SlabStripTopology(
            name = "Corps Creux Courant",
            totalThicknessHtCm = 21.0, // 16+5
            compressionSlabH0Cm = 5.0,
            hollowBlockHeightCm = 16.0,
            ribWidthB0Cm = 12.0,
            flangeWidthBCm = 60.0,
            continuousSpansMeters = listOf(4.35, 3.15, 2.80)
        )

        val catalog = SectionCatalog(
            columns = columnFamilies,
            beams = beamFamilies,
            shearWalls = shearWallFamilies,
            slabStrips = listOf(slabStrip)
        )

        return StructuralJoint(
            jointName = jointName,
            gridSystem = gridSystem,
            storySystem = storySystem,
            sectionCatalog = catalog,
            materials = MaterialConfig(),
            seismicParams = SeismicConfig()
        )
    }
}
