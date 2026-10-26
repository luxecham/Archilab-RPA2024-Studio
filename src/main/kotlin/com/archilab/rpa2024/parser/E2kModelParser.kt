package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.GridLine
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/**
 * Lecteur de fichiers texte ETABS .e2k (v9.7.4 a v21) et des exports tabulaires texte.
 * Le format e2k est une succession de tables "TABLE: \"NOM\"" dont les lignes sont des
 * paires cle=valeur. Toute la geometrie est convertie vers le systeme interne (m, kN, MPa).
 */
class E2kModelParser {

    fun parse(file: Path): RawGeometry = parseText(readText(file), file.fileName.toString())

    fun parseText(text: String, sourceName: String = "ETABS"): RawGeometry {
        val warnings = mutableListOf<String>()
        val tables = splitTables(text)
        if (tables.isEmpty()) {
            throw EtabsParseException("$sourceName : aucune table ETABS reconnue (TABLE: \"...\").")
        }

        val units = detectUnits(text, tables)
        val materialTable = tables.rowsOf("MATERIAL")
        val sectionTable = tables.rowsOf("FRAME SECTION", "FRAME PROPERTIES")
        val areaSectionTable = tables.rowsOf(
            "WALL/SLAB/DECK", "AREA SECTION", "WALL PROPERTIES", "SLAB PROPERTIES", "DECK PROPERTIES"
        )

        val points = parsePoints(tables, units, warnings)
        val stories = parseStories(tables, units, warnings)
        val grids = parseGrids(tables, units)
        val materials = parseMaterials(materialTable, units)
        val sections = parseSections(sectionTable, areaSectionTable, units)
        val sectionsByName = sections.associateBy { normalize(it.name) }

        val lineSections = assignmentMap(
            rows = tables.rowsOf("LINE ASSIGN", "FRAME ASSIGN"),
            idKeys = listOf("Line", "Frame", "UniqueName", "Label"),
            valueKeys = listOf("Section", "SectionName", "PropName")
        )
        val lineStories = assignmentMap(
            rows = tables.rowsOf("LINE ASSIGN", "FRAME ASSIGN"),
            idKeys = listOf("Line", "Frame", "UniqueName", "Label"),
            valueKeys = listOf("Story", "StoryName")
        )
        val areaSections = assignmentMap(
            rows = tables.rowsOf("AREA ASSIGN"),
            idKeys = listOf("Area", "UniqueName", "Label"),
            valueKeys = listOf("Section", "SectionName", "PropName")
        )
        val areaStories = assignmentMap(
            rows = tables.rowsOf("AREA ASSIGN"),
            idKeys = listOf("Area", "UniqueName", "Label"),
            valueKeys = listOf("Story", "StoryName")
        )
        val areaPiers = assignmentMap(
            rows = tables.rowsOf("PIER ASSIGN", "WALL PIER ASSIGN", "PIER LABEL"),
            idKeys = listOf("Area", "UniqueName", "Label"),
            valueKeys = listOf("Pier", "PierName", "Label")
        )

        val frames = parseFrames(tables, points, sectionsByName, lineSections, lineStories, warnings)
        val areas = parseAreas(tables, points, areaSections, areaStories, areaPiers, warnings)
        val piers = (tables.rowsOf("PIER NAME", "PIER LABEL").mapNotNull { row ->
            value(row, "Pier", "PierName", "Name", "Label")?.takeIf { it.isNotBlank() }
        } + areaPiers.values).distinct().sorted()

        return RawGeometry(
            points = points,
            frames = frames,
            areas = areas,
            sections = sections,
            materials = materials,
            stories = stories,
            gridsX = grids.first,
            gridsY = grids.second,
            pierNames = piers,
            units = units,
            warnings = warnings
        )
    }

    private fun parsePoints(
        tables: Map<String, List<Row>>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): Map<String, Point3D> {
        val rows = tables.rowsOf("POINT COORDINATE", "JOINT COORDINATE", "POINT OBJECT")
        val points = linkedMapOf<String, Point3D>()
        for (row in rows) {
            val id = value(row, "Point", "Joint", "UniqueName", "Label", "Name") ?: continue
            val x = number(row, "X", "XorR", "GlobalX")
            val y = number(row, "Y", "GlobalY")
            val z = number(row, "Z", "GlobalZ")
            if (x == null || y == null || z == null) {
                warnings += "Nœud '$id' ignoré : coordonnées incomplètes."
                continue
            }
            points[id] = Point3D(id, units.toMeters(x), units.toMeters(y), units.toMeters(z))
        }
        if (points.isEmpty()) {
            warnings += "Aucune coordonnée de nœud lue : la topologie ne pourra pas être reconstruite."
        }
        return points
    }

    private fun parseStories(
        tables: Map<String, List<Row>>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): List<RawStory> {
        val rows = tables.rowsOf("STORY", "STORIE")
        val stories = mutableListOf<RawStory>()
        for (row in rows) {
            val name = value(row, "Story", "StoryName", "Name", "Level") ?: continue
            if (name.equals("Base", ignoreCase = true) || name.equals("Niveau 0", ignoreCase = true)) continue
            val height = number(row, "Height", "StoryHeight", "HK")
            val elevation = number(row, "Elevation", "Elev", "Z")
            if (height == null && elevation == null) {
                warnings += "Niveau '$name' ignoré : ni hauteur ni altitude."
                continue
            }
            stories += RawStory(
                name = name,
                heightMeters = height?.let { units.toMeters(it) } ?: 0.0,
                elevationMeters = elevation?.let { units.toMeters(it) }
            )
        }
        return stories
    }

    private fun parseGrids(tables: Map<String, List<Row>>, units: UnitSystem): Pair<List<GridLine>, List<GridLine>> {
        val rows = tables.rowsOf("GRID")
        val x = mutableListOf<GridLine>()
        val y = mutableListOf<GridLine>()
        for (row in rows) {
            val label = value(row, "Label", "GridLine", "Name", "GridID") ?: continue
            val direction = value(row, "Dir", "Direction", "Axis")?.trim()?.uppercase()
            val ordinate = number(row, "Ordinate", "Coordinate", "Coord", "X", "Y") ?: continue
            val coordinate = units.toMeters(ordinate)
            val isX = when (direction) {
                "X", "1", "X-X", "GLOBAL X" -> true
                "Y", "2", "Y-Y", "GLOBAL Y" -> false
                else -> null
            }
            when (isX) {
                true -> x += GridLine(label, coordinate)
                false -> y += GridLine(label, coordinate)
                null -> {
                    val combined = value(row, "GridSys", "GridSysName", "GridSystem")?.uppercase() ?: ""
                    if (combined.contains("Y")) y += GridLine(label, coordinate) else x += GridLine(label, coordinate)
                }
            }
        }
        return distinctGrids(x) to distinctGrids(y)
    }

    private fun distinctGrids(lines: List<GridLine>): List<GridLine> =
        lines.groupBy { String.format("%.4f", it.coordinateMeters) }
            .map { (_, group) -> group.first() }
            .sortedBy { it.coordinateMeters }

    private fun parseMaterials(rows: List<Row>, units: UnitSystem): List<RawMaterial> {
        val grouped = linkedMapOf<String, MutableMap<String, String>>()
        for (row in rows) {
            val name = value(row, "Material", "Name", "MaterialName") ?: continue
            grouped.getOrPut(name) { linkedMapOf() }.putAll(row.fields)
        }
        return grouped.map { (name, fields) ->
            val row = Row(fields)
            val type = value(row, "Type", "MaterialType", "DesignType") ?: "Concrete"
            val fc = number(row, "Fc", "FC", "Fc28", "Fcu", "ConcreteStrength", "CompStrength")
            val fy = number(row, "Fy", "FY", "Fys", "SteelStrength", "YieldStrength", "FyMain")
            RawMaterial(
                name = name,
                type = type,
                fc28MPa = fc?.let { stressToMPa(it, units) },
                fyMPa = fy?.let { stressToMPa(it, units) }
            )
        }
    }

    private fun stressToMPa(value: Double, units: UnitSystem): Double =
        if (value in 10.0..1_500.0) value else units.toMPa(value)

    private fun parseSections(
        frameRows: List<Row>,
        areaRows: List<Row>,
        units: UnitSystem
    ): List<EtabsSection> {
        val sections = mutableListOf<EtabsSection>()
        for (row in frameRows) {
            val name = value(row, "Section", "SectionName", "Name", "PropName") ?: continue
            val shape = value(row, "Shape", "SectionShape", "Type")?.uppercase() ?: "RECTANGULAR"
            val circular = shape.contains("CIRC") || shape.contains("ROUND") || value(row, "D", "Diameter") != null
            val depthRaw = number(row, "t3", "Depth", "H", "D", "Diameter")
            val widthRaw = number(row, "t2", "Width", "B", "D", "Diameter")
            if (circular) {
                val diameter = depthRaw ?: widthRaw ?: continue
                sections += EtabsSection(
                    name = name,
                    kind = EtabsSectionKind.FRAME_CIRCULAR,
                    materialName = value(row, "Material", "MatProp"),
                    diameterCm = units.toCentimeters(diameter),
                    depthCm = units.toCentimeters(diameter),
                    widthCm = units.toCentimeters(diameter)
                )
            } else {
                val depth = depthRaw ?: continue
                val width = widthRaw ?: depth
                val kind = if (shape.contains("RECT") || shape.contains("CONCRETE") || shape.contains("BA")) {
                    EtabsSectionKind.FRAME_RECTANGULAR
                } else {
                    EtabsSectionKind.FRAME_OTHER
                }
                sections += EtabsSection(
                    name = name,
                    kind = kind,
                    materialName = value(row, "Material", "MatProp"),
                    widthCm = units.toCentimeters(width),
                    depthCm = units.toCentimeters(depth)
                )
            }
        }
        for (row in areaRows) {
            val name = value(row, "Section", "SectionName", "Name", "PropName") ?: continue
            val type = value(row, "Type", "AreaType", "PropType", "ShellType")?.uppercase() ?: ""
            val thicknessRaw = number(row, "Thickness", "Th", "t3", "SlabThickness", "TotalThickness")
            val totalRaw = number(row, "TotalThickness", "TotalThk", "Thickness")
            val compressionRaw = number(row, "TopThickness", "CompressionThickness", "H0", "FlangeThickness")
            val hollowRaw = number(row, "HollowBlockHeight", "BlockHeight", "Hb")
            val thickness = thicknessRaw ?: totalRaw
            if (thickness == null && type.isBlank()) continue
            val kind = when {
                type.contains("WALL") -> EtabsSectionKind.WALL
                type.contains("DECK") -> EtabsSectionKind.DECK
                else -> EtabsSectionKind.SLAB
            }
            sections += EtabsSection(
                name = name,
                kind = kind,
                materialName = value(row, "Material", "MatProp"),
                thicknessCm = thickness?.let { units.toCentimeters(it) },
                totalThicknessCm = (totalRaw ?: thickness)?.let { units.toCentimeters(it) },
                compressionSlabThicknessCm = compressionRaw?.let { units.toCentimeters(it) },
                hollowBlockHeightCm = hollowRaw?.let { units.toCentimeters(it) },
                ribWidthCm = number(row, "RibWidth", "StemWidth", "b0")?.let { units.toCentimeters(it) },
                ribSpacingCm = number(row, "RibSpacing", "Spacing", "b")?.let { units.toCentimeters(it) }
            )
        }
        return sections.distinctBy { it.name.uppercase() }
    }

    private fun parseFrames(
        tables: Map<String, List<Row>>,
        points: Map<String, Point3D>,
        sectionsByName: Map<String, EtabsSection>,
        lineSections: Map<String, String>,
        lineStories: Map<String, String>,
        warnings: MutableList<String>
    ): List<FrameElement> {
        val rows = tables.rowsOf("LINE CONNECTIVIT", "FRAME CONNECTIVIT", "CONNECTIVITY - FRAME")
        val frames = mutableListOf<FrameElement>()
        for (row in rows) {
            val lineId = value(row, "Line", "Frame", "UniqueName", "Label", "Name") ?: continue
            val start = value(row, "Point1", "Joint1", "PointI", "StartJoint", "i")
            val end = value(row, "Point2", "Joint2", "PointJ", "EndJoint", "j")
            if (start == null || end == null) {
                warnings += "Élément '$lineId' ignoré : nœuds de connectivité manquants."
                continue
            }
            val uniqueName = value(row, "UniqueName") ?: lineId
            val section = lineSections[lineId]
                ?: lineSections[uniqueName]
                ?: value(row, "Section", "SectionName", "PropName")
            if (section == null) {
                warnings += "Élément '$lineId' ignoré : aucune section affectée."
                continue
            }
            val declaredKind = value(row, "LineType", "FrameType", "Type")?.uppercase()
            val kind = when {
                declaredKind == null -> inferFrameKind(start, end, points)
                declaredKind.contains("COLUMN") -> FrameKind.COLUMN
                declaredKind.contains("BEAM") -> FrameKind.BEAM
                declaredKind.contains("BRACE") -> FrameKind.BRACE
                else -> inferFrameKind(start, end, points)
            }
            val resolvedSection = sectionsByName[normalize(section)]?.name ?: section
            frames += FrameElement(
                id = uniqueName,
                kind = kind,
                startNodeId = start,
                endNodeId = end,
                sectionName = resolvedSection,
                storyName = lineStories[lineId] ?: lineStories[uniqueName] ?: value(row, "Story", "StoryName")
            )
        }
        return frames
    }

    private fun parseAreas(
        tables: Map<String, List<Row>>,
        points: Map<String, Point3D>,
        areaSections: Map<String, String>,
        areaStories: Map<String, String>,
        areaPiers: Map<String, String>,
        warnings: MutableList<String>
    ): List<AreaElement> {
        val rows = tables.rowsOf("AREA CONNECTIVIT", "CONNECTIVITY - AREA")
        val areas = mutableListOf<AreaElement>()
        for (row in rows) {
            val areaId = value(row, "Area", "UniqueName", "Label", "Name") ?: continue
            val nodeIds = (1..8).mapNotNull { index ->
                value(row, "Point$index", "Joint$index", "Point${index}I")
            }
            if (nodeIds.size < 3) {
                warnings += "Surface '$areaId' ignorée : moins de trois nœuds."
                continue
            }
            val uniqueName = value(row, "UniqueName") ?: areaId
            val property = areaSections[areaId]
                ?: areaSections[uniqueName]
                ?: value(row, "Section", "SectionName", "PropName", "Property")
            if (property == null) {
                warnings += "Surface '$areaId' ignorée : aucune propriété affectée."
                continue
            }
            val declared = value(row, "AreaType", "Type", "ShellType")?.uppercase()
            val kind = when {
                declared == null -> inferAreaKind(nodeIds, points)
                declared.contains("WALL") -> AreaKind.WALL
                declared.contains("FLOOR") || declared.contains("ROOF") ||
                    declared.contains("SLAB") || declared.contains("DECK") -> AreaKind.SLAB
                else -> inferAreaKind(nodeIds, points)
            }
            areas += AreaElement(
                id = uniqueName,
                kind = kind,
                nodeIds = nodeIds,
                propertyName = property,
                pierName = areaPiers[areaId] ?: areaPiers[uniqueName] ?: value(row, "Pier", "PierName"),
                storyName = areaStories[areaId] ?: areaStories[uniqueName] ?: value(row, "Story", "StoryName")
            )
        }
        return areas
    }

    private fun inferFrameKind(startId: String, endId: String, points: Map<String, Point3D>): FrameKind {
        val start = points[startId] ?: return FrameKind.OTHER
        val end = points[endId] ?: return FrameKind.OTHER
        val dz = abs(end.zMeters - start.zMeters)
        val plan = start.planDistanceTo(end)
        return when {
            dz > 0.30 && plan < 0.10 -> FrameKind.COLUMN
            dz < 0.10 && plan > 0.10 -> FrameKind.BEAM
            else -> FrameKind.BRACE
        }
    }

    private fun inferAreaKind(nodeIds: List<String>, points: Map<String, Point3D>): AreaKind {
        val nodes = nodeIds.mapNotNull { points[it] }
        if (nodes.size < 3) return AreaKind.OTHER
        val dz = nodes.maxOf { it.zMeters } - nodes.minOf { it.zMeters }
        val dx = nodes.maxOf { it.xMeters } - nodes.minOf { it.xMeters }
        val dy = nodes.maxOf { it.yMeters } - nodes.minOf { it.yMeters }
        return if (dz > 0.30 && minOf(dx, dy) < 1.0) AreaKind.WALL else AreaKind.SLAB
    }

    private fun detectUnits(text: String, tables: Map<String, List<Row>>): UnitSystem {
        val programRow = tables.rowsOf("PROGRAM CONTROL").firstOrNull()
        val declared = programRow?.let { value(it, "CurrUnits", "Units", "CurrentUnits") }
        if (!declared.isNullOrBlank()) return UnitSystem.fromEtabsUnits(declared)

        val header = Regex("(?im)^\\s*\\$?\\s*UNITS?\\s*[:=]\\s*\"?([A-Za-z,\\s\\-]+)\"?").find(text)
        if (header != null) return UnitSystem.fromEtabsUnits(header.groupValues[1])

        val generic = Regex("(?i)CurrUnits\\s*=\\s*\"([^\"]+)\"").find(text)
        if (generic != null) return UnitSystem.fromEtabsUnits(generic.groupValues[1])
        return UnitSystem.KN_M
    }

    private fun splitTables(text: String): Map<String, List<Row>> {
        val result = linkedMapOf<String, MutableList<Row>>()
        var current: MutableList<Row>? = null
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val tableName = tableHeader(line)
            if (tableName != null) {
                current = result.getOrPut(normalize(tableName)) { mutableListOf() }
                continue
            }
            if (line.startsWith("$") || line.startsWith("//")) continue
            val row = current ?: continue
            val fields = parseFields(line)
            if (fields.isNotEmpty()) row += Row(fields)
        }
        return result
    }

    private fun tableHeader(line: String): String? {
        if (line.startsWith("TABLE:", ignoreCase = true)) {
            val rest = line.substringAfter(':').trim().trim('"')
            return rest.ifBlank { null }
        }
        if (line.startsWith("$")) {
            val rest = line.removePrefix("$").trim()
            if (rest.isEmpty()) return null
            val upper = rest.uppercase()
            val known = listOf(
                "STORIES", "STORY DATA", "GRIDS", "GRID DATA", "GRID LINES", "POINT COORDINATES",
                "JOINT COORDINATES", "LINE CONNECTIVITIES", "AREA CONNECTIVITIES", "MATERIAL PROPERTIES",
                "FRAME SECTIONS", "WALL/SLAB/DECK PROPERTIES", "LINE ASSIGNS", "AREA ASSIGNS",
                "PIER NAMES", "PIER LABELS", "PROGRAM CONTROL"
            )
            return if (known.any { upper.startsWith(it) }) rest else null
        }
        return null
    }

    private fun parseFields(line: String): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        PAIR_PATTERN.findAll(line).forEach { match ->
            val key = match.groupValues[1]
            val quoted = match.groupValues[3]
            val plain = match.groupValues[2]
            fields[key] = if (quoted.isNotEmpty()) quoted else plain
        }
        return fields
    }

    private fun assignmentMap(rows: List<Row>, idKeys: List<String>, valueKeys: List<String>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (row in rows) {
            val id = value(row, *idKeys.toTypedArray()) ?: continue
            val assigned = value(row, *valueKeys.toTypedArray()) ?: continue
            if (assigned.isNotBlank()) map[id] = assigned
        }
        return map
    }

    private fun value(row: Row, vararg keys: String): String? {
        for (key in keys) {
            val found = row.fields.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            if (!found.isNullOrBlank() && !found.equals("N/A", ignoreCase = true)) return found
        }
        return null
    }

    private fun number(row: Row, vararg keys: String): Double? =
        value(row, *keys)?.replace(',', '.')?.trim()?.toDoubleOrNull()

    private fun normalize(name: String): String = name.uppercase().replace(Regex("[^A-Z0-9/+-]"), "")

    private data class Row(val fields: Map<String, String>)

    private fun Map<String, List<Row>>.rowsOf(vararg prefixes: String): List<Row> =
        entries.filter { (name, _) -> prefixes.any { name.startsWith(normalize(it)) } }
            .flatMap { it.value }

    companion object {
        private val PAIR_PATTERN = Regex("([A-Za-z0-9_]+)\\s*=\\s*(\"([^\"]*)\"|[^\\s]+)")

        fun readText(file: Path): String {
            val bytes = Files.readAllBytes(file)
            val utf8 = String(bytes, Charsets.UTF_8)
            val replacementCount = utf8.count { it == '\uFFFD' }
            return if (replacementCount > 0) {
                String(bytes, Charset.forName("windows-1252"))
            } else {
                utf8
            }
        }
    }
}
