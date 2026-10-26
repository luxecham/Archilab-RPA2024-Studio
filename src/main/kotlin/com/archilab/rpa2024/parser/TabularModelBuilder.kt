package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.model.SeismicConfig
import kotlin.math.abs

/**
 * Reconstruit la geometrie brute a partir des tables ETABS exportees (Excel, Access, texte).
 * Les tables sont reconnues par alias afin de couvrir ETABS v9.7.4, v18, v21 et les exports SAFE.
 */
class TabularModelBuilder(private val topologyBuilder: TopologyBuilder = TopologyBuilder()) {

    fun build(
        tables: List<TabularTable>,
        jointName: String,
        seismicConfig: SeismicConfig
    ): ParsedEtabsModel = topologyBuilder.build(jointName, buildRaw(tables), seismicConfig)

    fun buildRaw(tables: List<TabularTable>): RawGeometry {
        if (tables.isEmpty()) {
            throw EtabsParseException("Le fichier ne contient aucune table exploitable (Excel/Access/Texte).")
        }
        val warnings = mutableListOf<String>()
        val units = detectUnits(tables, warnings)

        val points = readPoints(tables, units, warnings)
        val stories = readStories(tables, units, warnings)
        val grids = readGrids(tables, units)
        val materials = readMaterials(tables, units)
        val sections = readSections(tables, units)
        val sectionsByName = sections.associateBy { normalize(it.name) }

        val frameSections = readAssignments(
            tables,
            tableAliases = arrayOf("Frame Section Assignments", "Line Assigns", "Frame Assignments", "Frame Assigns"),
            idAliases = arrayOf("Frame", "UniqueName", "Line", "Label")
        )
        val frameStories = readAssignments(
            tables,
            tableAliases = arrayOf("Frame Section Assignments", "Line Assigns", "Frame Assignments", "Frame Assigns"),
            idAliases = arrayOf("Frame", "UniqueName", "Line", "Label"),
            valueAliases = arrayOf("Story", "StoryName", "Level")
        )
        val areaSections = readAssignments(
            tables,
            tableAliases = arrayOf("Area Section Assignments", "Area Assigns", "Shell Assignments", "Wall Assignments"),
            idAliases = arrayOf("Area", "UniqueName", "Label", "Wall")
        )
        val areaStories = readAssignments(
            tables,
            tableAliases = arrayOf("Area Section Assignments", "Area Assigns", "Shell Assignments", "Wall Assignments"),
            idAliases = arrayOf("Area", "UniqueName", "Label", "Wall"),
            valueAliases = arrayOf("Story", "StoryName", "Level")
        )
        val areaPiers = readAssignments(
            tables,
            tableAliases = arrayOf("Wall Pier Assignments", "Pier Assignments", "Pier Labels", "Pier Names"),
            idAliases = arrayOf("Area", "UniqueName", "Label", "Wall"),
            valueAliases = arrayOf("Pier", "PierName", "Pier Label")
        )

        val frames = readFrames(tables, points, sectionsByName, frameSections, frameStories, warnings)
        val areas = readAreas(tables, points, areaSections, areaStories, areaPiers, warnings)

        val piers = (readPierNames(tables) + areaPiers.values).distinct().sorted()

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

    private fun readPoints(
        tables: List<TabularTable>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): Map<String, Point3D> {
        val table = findTable(tables, "Point Coordinates", "Joint Coordinates", "Point Object Connectivity")
            ?: run {
                warnings += "Table 'Point Coordinates' absente : topologie impossible."
                return emptyMap()
            }
        val points = linkedMapOf<String, Point3D>()
        table.forEachRow { row ->
            val id = row.value("UniqueName", "Point", "Joint", "Label", "Name") ?: return@forEachRow
            val x = row.number("X", "GlobalX")
            val y = row.number("Y", "GlobalY")
            val z = row.number("Z", "GlobalZ")
            if (x == null || y == null || z == null) {
                warnings += "Nœud '$id' ignoré : coordonnées incomplètes."
                return@forEachRow
            }
            points[id] = Point3D(id, units.toMeters(x), units.toMeters(y), units.toMeters(z))
        }
        if (points.isEmpty()) warnings += "Aucune coordonnée de nœud exploitable dans la table 'Point Coordinates'."
        return points
    }

    private fun readStories(
        tables: List<TabularTable>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): List<RawStory> {
        val table = findTable(tables, "Story Data", "Stories", "Story Definitions", "Story Names") ?: return emptyList()
        val stories = mutableListOf<RawStory>()
        table.forEachRow { row ->
            val name = row.value("Story", "StoryName", "Name", "Level") ?: return@forEachRow
            if (name.equals("Base", ignoreCase = true)) return@forEachRow
            val height = row.number("Height", "StoryHeight", "HK")
            val elevation = row.number("Elevation", "Elev", "Z")
            if (height == null && elevation == null) {
                warnings += "Niveau '$name' ignoré : ni hauteur ni altitude."
                return@forEachRow
            }
            stories += RawStory(
                name = name,
                heightMeters = height?.let { units.toMeters(it) } ?: 0.0,
                elevationMeters = elevation?.let { units.toMeters(it) }
            )
        }
        return stories
    }

    private fun readGrids(tables: List<TabularTable>, units: UnitSystem): Pair<List<GridLine>, List<GridLine>> {
        val table = findTable(tables, "Grid Definitions - Lines", "Grid Lines", "Grid Data", "Grid Definitions")
            ?: return emptyList<GridLine>() to emptyList()
        val x = mutableListOf<GridLine>()
        val y = mutableListOf<GridLine>()
        table.forEachRow { row ->
            val label = row.value("LineName", "Label", "GridLine", "Name", "GridID") ?: return@forEachRow
            val direction = row.value("Direction", "Dir", "Axis")?.trim()?.uppercase()
            val ordinate = row.number("Ordinate", "Coordinate", "Coord", "X", "Y") ?: return@forEachRow
            val coordinate = units.toMeters(ordinate)
            val isX = when {
                direction == null -> {
                    val system = row.value("GridName", "GridSystem", "GridSys")?.uppercase() ?: ""
                    if (system.contains("Y")) false else true
                }
                direction.startsWith("X") || direction == "1" -> true
                direction.startsWith("Y") || direction == "2" -> false
                else -> true
            }
            if (isX) x += GridLine(label, coordinate) else y += GridLine(label, coordinate)
        }
        return distinctGrids(x) to distinctGrids(y)
    }

    private fun distinctGrids(lines: List<GridLine>): List<GridLine> =
        lines.groupBy { String.format("%.4f", it.coordinateMeters) }
            .map { (_, group) -> group.first() }
            .sortedBy { it.coordinateMeters }

    private fun readMaterials(tables: List<TabularTable>, units: UnitSystem): List<RawMaterial> {
        val relevant = tables.filter { normalize(it.name).contains("MATERIALPROPERTIE") }
        if (relevant.isEmpty()) return emptyList()
        val merged = linkedMapOf<String, MutableMap<String, String>>()
        for (table in relevant) {
            table.forEachRow { row ->
                val name = row.value("Material", "MaterialName", "Name") ?: return@forEachRow
                merged.getOrPut(name) { linkedMapOf() }.putAll(rowFields(table, row))
            }
        }
        return merged.map { (name, fields) ->
            val row = MapRow(fields)
            val type = row.value("Type", "MaterialType", "DesignType") ?: "Concrete"
            val fc = row.number("Fc", "Fc28", "Fcu", "ConcreteStrength", "CompStrength", "F'c")
            val fy = row.number("Fy", "Fys", "SteelStrength", "YieldStrength", "FyMain")
            RawMaterial(
                name = name,
                type = type,
                fc28MPa = fc?.let { stressToMPa(it, units) },
                fyMPa = fy?.let { stressToMPa(it, units) }
            )
        }
    }

    private fun readSections(tables: List<TabularTable>, units: UnitSystem): List<EtabsSection> {
        val sections = mutableListOf<EtabsSection>()

        val frameTables = tables.filter {
            val name = normalize(it.name)
            name.contains("FRAMESECTIONPROPERTIE") || name.contains("FRAMEPROPERTIE") ||
                name.contains("BEAMPROPERTIE") || name.contains("COLUMNPROPERTIE")
        }
        for (table in frameTables) {
            table.forEachRow { row ->
                val name = row.value("SectionName", "Section", "Name", "PropName") ?: return@forEachRow
                val shape = row.value("Shape", "SectionShape", "Type")?.uppercase() ?: "RECTANGULAR"
                val depthRaw = row.number("t3", "Depth", "H", "D", "Diameter")
                val widthRaw = row.number("t2", "Width", "B", "D", "Diameter")
                val circular = shape.contains("CIRC") || shape.contains("ROUND") ||
                    row.value("Shape")?.contains("Circle", ignoreCase = true) == true
                if (circular) {
                    val diameter = depthRaw ?: widthRaw ?: return@forEachRow
                    sections += EtabsSection(
                        name = name,
                        kind = EtabsSectionKind.FRAME_CIRCULAR,
                        materialName = row.value("Material", "MatProp"),
                        diameterCm = units.toCentimeters(diameter),
                        depthCm = units.toCentimeters(diameter),
                        widthCm = units.toCentimeters(diameter)
                    )
                } else {
                    val depth = depthRaw ?: return@forEachRow
                    val width = widthRaw ?: depth
                    sections += EtabsSection(
                        name = name,
                        kind = EtabsSectionKind.FRAME_RECTANGULAR,
                        materialName = row.value("Material", "MatProp"),
                        widthCm = units.toCentimeters(width),
                        depthCm = units.toCentimeters(depth)
                    )
                }
            }
        }

        val areaTables = tables.filter {
            val name = normalize(it.name)
            (name.contains("WALLPROPERTIE") || name.contains("SLABPROPERTIE") ||
                name.contains("DECKPROPERTIE") || name.contains("AREASECTIONPROPERTIE") ||
                name.contains("SHELLPROPERTIE")) && !name.contains("ASSIGN")
        }
        for (table in areaTables) {
            val tableName = normalize(table.name)
            table.forEachRow { row ->
                val name = row.value("Name", "SectionName", "Section", "PropName", "Property") ?: return@forEachRow
                val type = row.value("Type", "AreaType", "PropType", "ShellType", "SlabType")?.uppercase() ?: ""
                val thickness = row.number("Thickness", "Th", "t3", "SlabThickness")
                    ?: row.number("TotalThickness", "TotalDepth", "Depth")
                    ?: return@forEachRow
                val total = row.number("TotalThickness", "TotalDepth", "Depth") ?: thickness
                val kind = when {
                    tableName.contains("WALL") || type.contains("WALL") -> EtabsSectionKind.WALL
                    tableName.contains("DECK") || type.contains("DECK") -> EtabsSectionKind.DECK
                    else -> EtabsSectionKind.SLAB
                }
                sections += EtabsSection(
                    name = name,
                    kind = kind,
                    materialName = row.value("Material", "MatProp"),
                    thicknessCm = units.toCentimeters(thickness),
                    totalThicknessCm = units.toCentimeters(total),
                    compressionSlabThicknessCm = row.number("TopThickness", "SlabThickness", "CompressionThickness", "H0")
                        ?.let { units.toCentimeters(it) },
                    hollowBlockHeightCm = row.number("HollowBlockHeight", "BlockHeight", "Hb")
                        ?.let { units.toCentimeters(it) },
                    ribWidthCm = row.number("RibWidth", "StemWidth", "b0")?.let { units.toCentimeters(it) },
                    ribSpacingCm = row.number("RibSpacing", "Spacing", "Rib")?.let { units.toCentimeters(it) }
                )
            }
        }
        val merged = linkedMapOf<String, EtabsSection>()
        for (section in sections) {
            val key = section.name.uppercase()
            val existing = merged[key]
            merged[key] = if (existing == null) section else existing.copy(
                widthCm = existing.widthCm ?: section.widthCm,
                depthCm = existing.depthCm ?: section.depthCm,
                diameterCm = existing.diameterCm ?: section.diameterCm,
                thicknessCm = existing.thicknessCm ?: section.thicknessCm,
                totalThicknessCm = existing.totalThicknessCm ?: section.totalThicknessCm,
                compressionSlabThicknessCm = existing.compressionSlabThicknessCm ?: section.compressionSlabThicknessCm,
                hollowBlockHeightCm = existing.hollowBlockHeightCm ?: section.hollowBlockHeightCm,
                ribWidthCm = existing.ribWidthCm ?: section.ribWidthCm,
                ribSpacingCm = existing.ribSpacingCm ?: section.ribSpacingCm
            )
        }
        return merged.values.toList()
    }

    private fun readFrames(
        tables: List<TabularTable>,
        points: Map<String, Point3D>,
        sectionsByName: Map<String, EtabsSection>,
        assignments: Map<String, String>,
        storyAssignments: Map<String, String>,
        warnings: MutableList<String>
    ): List<FrameElement> {
        val table = findTable(tables, "Connectivity - Frame", "Frame Connectivity", "Line Connectivities", "Frame Objects")
            ?: run {
                warnings += "Table 'Connectivity - Frame' absente : aucune poutre ni poteau."
                return emptyList()
            }
        val frames = mutableListOf<FrameElement>()
        table.forEachRow { row ->
            val id = row.value("Frame", "UniqueName", "Line", "Label") ?: return@forEachRow
            val start = row.value("Joint", "Joint1", "Joint 1", "Point1", "Point 1", "JointI", "i")
            val end = row.value("Joint2", "Joint 2", "Point2", "Point 2", "JointJ", "j")
            if (start == null || end == null) {
                warnings += "Élément '$id' ignoré : nœuds de connectivité manquants."
                return@forEachRow
            }
            val section = assignments[id]
                ?: row.value("Section", "SectionName", "PropName", "Property")
                ?: run {
                    warnings += "Élément '$id' ignoré : aucune section affectée."
                    return@forEachRow
                }
            val resolved = sectionsByName[normalize(section)]?.name ?: section
            val declared = row.value("Type", "FrameType", "LineType")?.uppercase()
            val kind = when {
                declared == null -> inferFrameKind(start, end, points)
                declared.contains("COLUMN") -> FrameKind.COLUMN
                declared.contains("BEAM") -> FrameKind.BEAM
                declared.contains("BRACE") -> FrameKind.BRACE
                else -> inferFrameKind(start, end, points)
            }
            frames += FrameElement(
                id = id,
                kind = kind,
                startNodeId = start,
                endNodeId = end,
                sectionName = resolved,
                storyName = storyAssignments[id] ?: row.value("Story", "StoryName")
            )
        }
        return frames
    }

    private fun readAreas(
        tables: List<TabularTable>,
        points: Map<String, Point3D>,
        assignments: Map<String, String>,
        storyAssignments: Map<String, String>,
        pierAssignments: Map<String, String>,
        warnings: MutableList<String>
    ): List<AreaElement> {
        val table = findTable(tables, "Connectivity - Area", "Area Connectivity", "Area Connectivities", "Area Objects")
            ?: run {
                warnings += "Table 'Connectivity - Area' absente : aucun voile ni plancher."
                return emptyList()
            }
        val areas = mutableListOf<AreaElement>()
        table.forEachRow { row ->
            val id = row.value("Area", "UniqueName", "Label") ?: return@forEachRow
            val nodeIds = (1..8).mapNotNull { index ->
                row.value("Point$index", "Point $index", "Joint$index", "Joint $index")
            }
            if (nodeIds.size < 3) {
                warnings += "Surface '$id' ignorée : moins de trois nœuds."
                return@forEachRow
            }
            val property = assignments[id]
                ?: row.value("Section", "SectionName", "PropName", "Property")
                ?: run {
                    warnings += "Surface '$id' ignorée : aucune propriété affectée."
                    return@forEachRow
                }
            val declared = row.value("AreaType", "Area Type", "Type", "ShellType")?.uppercase()
            val kind = when {
                declared == null -> inferAreaKind(nodeIds, points)
                declared.contains("WALL") -> AreaKind.WALL
                declared.contains("FLOOR") || declared.contains("ROOF") ||
                    declared.contains("SLAB") || declared.contains("DECK") -> AreaKind.SLAB
                else -> inferAreaKind(nodeIds, points)
            }
            areas += AreaElement(
                id = id,
                kind = kind,
                nodeIds = nodeIds,
                propertyName = property,
                pierName = pierAssignments[id] ?: row.value("Pier", "PierName", "PierLabel"),
                storyName = storyAssignments[id] ?: row.value("Story", "StoryName")
            )
        }
        return areas
    }

    private fun readPierNames(tables: List<TabularTable>): List<String> {
        val table = findTable(tables, "Pier Names", "Pier Labels", "Wall Pier Assignments") ?: return emptyList()
        val names = mutableListOf<String>()
        table.forEachRow { row ->
            row.value("Pier", "PierName", "PierLabel", "Name", "Label")?.takeIf { it.isNotBlank() }?.let { names += it }
        }
        return names
    }

    private fun readAssignments(
        tables: List<TabularTable>,
        tableAliases: Array<String>,
        idAliases: Array<String>,
        valueAliases: Array<String> = arrayOf("Section", "SectionName", "PropName", "Property")
    ): Map<String, String> {
        val table = findTable(tables, *tableAliases) ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        table.forEachRow { row ->
            val id = row.value(*idAliases) ?: return@forEachRow
            val value = row.value(*valueAliases) ?: return@forEachRow
            if (value.isNotBlank()) result[id] = value
        }
        return result
    }

    private fun findTable(tables: List<TabularTable>, vararg aliases: String): TabularTable? {
        for (alias in aliases) {
            val target = normalize(alias)
            tables.firstOrNull { normalize(it.name) == target }?.let { return it }
        }
        for (alias in aliases) {
            val target = normalize(alias)
            tables.firstOrNull { normalize(it.name).startsWith(target) }?.let { return it }
        }
        for (alias in aliases) {
            val target = normalize(alias)
            tables.firstOrNull { normalize(it.name).contains(target) }?.let { return it }
        }
        return null
    }

    private fun detectUnits(tables: List<TabularTable>, warnings: MutableList<String>): UnitSystem {
        val program = findTable(tables, "Program Control", "Units")
        if (program != null) {
            val resolver = ColumnAliasResolver(program.headers)
            val showUnits = resolver.resolve("CurrUnits") ?: resolver.resolve("Units") ?: resolver.resolve("Current Units")
            if (showUnits != null) {
                if (program.rowCount > 0) {
                    val value = program.row(0).raw(program.headers[showUnits])
                    if (!value.isNullOrBlank()) {
                        warnings += "Unités ETABS détectées : $value."
                        return UnitSystem.fromEtabsUnits(value)
                    }
                }
            }
        }
        warnings += "Unités non déclarées : kN et m retenus par défaut."
        return UnitSystem.KN_M
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

    private fun stressToMPa(value: Double, units: UnitSystem): Double =
        if (value in 10.0..1_500.0) value else units.toMPa(value)

    private fun rowFields(table: TabularTable, row: TabularRow): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        table.headers.forEach { header -> row.raw(header)?.let { fields[header] = it } }
        return fields
    }

    private fun normalize(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private class MapRow(private val fields: Map<String, String>) {

        fun value(vararg aliases: String): String? {
            for (alias in aliases) {
                fields.entries.firstOrNull { normalize(it.key) == normalize(alias) }?.value
                    ?.takeIf { it.isNotBlank() }?.let { return it }
            }
            for (alias in aliases) {
                val target = normalize(alias)
                fields.entries.firstOrNull { normalize(it.key).startsWith(target) }?.value
                    ?.takeIf { it.isNotBlank() }?.let { return it }
            }
            return null
        }

        fun number(vararg aliases: String): Double? =
            value(*aliases)?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull()

        private fun normalize(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]"), "")
    }
}
