package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.BeamRole
import com.archilab.rpa2024.model.BoundingBox
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.GridSystem
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.SectionCatalog
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.model.StoryLevel
import com.archilab.rpa2024.model.StorySystem
import com.archilab.rpa2024.model.StructuralJoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Reconstruit la topologie complete d'un joint a partir des seuls noeuds, connectivites,
 * sections et niveaux extraits d'ETABS. Aucune dimension n'est supposee : les portees, hauteurs
 * libres, familles et planchers resultent du calcul geometrique.
 */
class TopologyBuilder {

    fun build(
        jointName: String,
        raw: RawGeometry,
        seismicConfig: SeismicConfig,
        units: UnitSystem? = raw.units
    ): ParsedEtabsModel {
        val warnings = raw.warnings
        if (raw.points.isEmpty()) {
            throw EtabsParseException("Le modele ne contient aucun noeud (POINT COORDINATES / Point Coordinates).")
        }

        val storySystem = buildStorySystem(raw, warnings)
        val gridSystem = buildGridSystem(raw)
        val materials = buildMaterials(raw, warnings)

        val sectionsByName = raw.sections.associateBy { normalize(it.name) }
        val sectionOf: (FrameElement) -> EtabsSection? = { sectionsByName[normalize(it.sectionName)] }

        val frames = raw.frames.map { frame ->
            if (frame.kind != FrameKind.OTHER) frame else frame.copy(kind = inferFrameKind(frame, raw.points))
        }
        val columnFrames = frames.filter { it.kind == FrameKind.COLUMN }
        val beamFrames = frames.filter { it.kind == FrameKind.BEAM }

        val beamDepthAtNode = mutableMapOf<String, Double>()
        for (beam in beamFrames) {
            val depth = sectionOf(beam)?.frameDepthCm ?: parseRectangularDimensions(beam.sectionName)?.second ?: 0.0
            if (depth > 0.0) {
                beamDepthAtNode.merge(beam.startNodeId, depth, ::max)
                beamDepthAtNode.merge(beam.endNodeId, depth, ::max)
            }
        }

        val columnDimensionAtNode = mutableMapOf<String, Double>()
        for (column in columnFrames) {
            val section = sectionOf(column)
            val dimension = section?.maxFrameDimensionCm
                ?: parseRectangularDimensions(column.sectionName)?.let { max(it.first, it.second) }
                ?: section?.diameterCm
                ?: 0.0
            if (dimension > 0.0) {
                columnDimensionAtNode.merge(column.startNodeId, dimension, ::max)
                columnDimensionAtNode.merge(column.endNodeId, dimension, ::max)
            }
        }

        val wallNodeIds = raw.areas
            .filter { it.kind == AreaKind.WALL }
            .flatMap { it.nodeIds }
            .toSet()

        val columns = buildColumnFamilies(
            columnFrames = columnFrames,
            raw = raw,
            sectionsByName = sectionsByName,
            beamDepthAtNode = beamDepthAtNode,
            wallNodeIds = wallNodeIds,
            storySystem = storySystem,
            warnings = warnings
        )

        val beams = buildBeamFamilies(
            beamFrames = beamFrames,
            raw = raw,
            sectionsByName = sectionsByName,
            columnDimensionAtNode = columnDimensionAtNode,
            storySystem = storySystem,
            warnings = warnings
        )

        val walls = buildWallFamilies(
            raw = raw,
            sectionsByName = sectionsByName,
            beamDepthAtNode = beamDepthAtNode,
            storySystem = storySystem,
            warnings = warnings
        )

        val slabs = buildSlabFamilies(
            raw = raw,
            sectionsByName = sectionsByName,
            gridSystem = gridSystem,
            warnings = warnings
        )

        if (columns.isEmpty() && beams.isEmpty() && walls.isEmpty()) {
            warnings += "Aucune famille d'éléments verticaux ou horizontaux n'a pu être reconstruite : vérifier les affectations de sections."
        }

        val joint = StructuralJoint(
            jointName = jointName,
            gridSystem = gridSystem,
            storySystem = storySystem,
            sectionCatalog = SectionCatalog(columns = columns, beams = beams, shearWalls = walls, slabs = slabs),
            materials = materials,
            seismicParams = seismicConfig
        )

        return ParsedEtabsModel(
            joint = joint,
            points = raw.points,
            frames = frames,
            areas = raw.areas,
            sections = raw.sections,
            pierNames = raw.pierNames,
            units = units,
            warnings = warnings.toList()
        )
    }

    private fun buildStorySystem(raw: RawGeometry, warnings: MutableList<String>): StorySystem {
        if (raw.stories.isNotEmpty()) {
            val hasElevations = raw.stories.all { it.elevationMeters != null }
            val levels = mutableListOf<StoryLevel>()
            if (hasElevations) {
                val sorted = raw.stories.sortedBy { it.elevationMeters ?: 0.0 }
                for ((index, story) in sorted.withIndex()) {
                    val elevation = story.elevationMeters ?: continue
                    val height = when {
                        story.heightMeters > 0.0 -> story.heightMeters
                        index < sorted.lastIndex -> (sorted[index + 1].elevationMeters ?: elevation) - elevation
                        else -> 0.0
                    }
                    if (height <= 0.0) {
                        warnings += "Niveau '${story.name}' ignoré : hauteur nulle ou négative."
                        continue
                    }
                    levels += StoryLevel(story.name, height, elevation, isPenthouseOrCage(story.name))
                }
            } else {
                var cursor = 0.0
                for (story in raw.stories) {
                    if (story.heightMeters <= 0.0) {
                        warnings += "Niveau '${story.name}' ignoré : hauteur nulle."
                        continue
                    }
                    levels += StoryLevel(story.name, story.heightMeters, cursor, isPenthouseOrCage(story.name))
                    cursor += story.heightMeters
                }
            }
            if (levels.isNotEmpty()) return StorySystem(levels)
            warnings += "Définitions de niveaux inutilisables : niveaux reconstruits depuis les nœuds."
        } else {
            warnings += "Aucune définition de niveaux trouvée : niveaux reconstruits depuis les altitudes des nœuds."
        }

        val zValues = distinctSorted(raw.points.values.map { it.zMeters })
        val derived = mutableListOf<StoryLevel>()
        for (index in 0 until zValues.lastIndex) {
            val height = zValues[index + 1] - zValues[index]
            if (height > 1e-3) {
                derived += StoryLevel("Niveau ${index + 1}", height, zValues[index], false)
            }
        }
        if (derived.isEmpty()) {
            throw EtabsParseException("Impossible de déduire les niveaux : altitudes de nœuds insuffisantes.")
        }
        return StorySystem(derived)
    }

    private fun buildGridSystem(raw: RawGeometry): GridSystem {
        val xs = raw.points.values.map { it.xMeters }
        val ys = raw.points.values.map { it.yMeters }
        val bounds = if (xs.isEmpty() || ys.isEmpty()) {
            null
        } else {
            BoundingBox(xs.min(), xs.max(), ys.min(), ys.max())
        }
        return if (raw.gridsX.isEmpty() || raw.gridsY.isEmpty()) {
            GridSystem.fromNodeCoordinates(xs, ys).copy(nodesBounds = bounds)
        } else {
            GridSystem(xGrids = raw.gridsX, yGrids = raw.gridsY, nodesBounds = bounds)
        }
    }

    private fun buildMaterials(raw: RawGeometry, warnings: MutableList<String>): MaterialConfig {
        val concrete = raw.materials.firstOrNull { it.isConcrete && (it.fc28MPa ?: 0.0) > 0.0 }
        val steel = raw.materials.firstOrNull { it.isSteel && (it.fyMPa ?: 0.0) > 0.0 }
        if (concrete == null) {
            warnings += "Aucune résistance béton fc28 lue dans le modèle : valeur par défaut 25 MPa retenue."
        }
        if (steel == null) {
            warnings += "Aucune limite élastique d'acier lue dans le modèle : valeur par défaut 500 MPa retenue."
        }
        val fc28 = concrete?.fc28MPa ?: 25.0
        if (fc28 < 15.0 || fc28 > 80.0) {
            warnings += "fc28 lue = $fc28 MPa hors plage usuelle : vérifier les unités du fichier ETABS."
        }
        return MaterialConfig(
            concreteClass = concrete?.name ?: "C25/30",
            fc28MPa = fc28,
            steelGrade = steel?.name ?: "FeE500",
            feMPa = steel?.fyMPa ?: 500.0
        )
    }

    private fun buildColumnFamilies(
        columnFrames: List<FrameElement>,
        raw: RawGeometry,
        sectionsByName: Map<String, EtabsSection>,
        beamDepthAtNode: Map<String, Double>,
        wallNodeIds: Set<String>,
        storySystem: StorySystem,
        warnings: MutableList<String>
    ): List<ColumnFamily> {
        val candidates = mutableListOf<ColumnCandidate>()

        for (frame in columnFrames) {
            val start = raw.points[frame.startNodeId]
            val end = raw.points[frame.endNodeId]
            if (start == null || end == null) {
                warnings += "Poteau '${frame.id}' ignoré : nœuds introuvables."
                continue
            }
            val section = sectionsByName[normalize(frame.sectionName)]
            val dims = frameDimensions(section, frame.sectionName)
            if (dims == null) {
                warnings += "Poteau '${frame.id}' ignoré : dimensions introuvables pour la section '${frame.sectionName}'."
                continue
            }

            val bottomZ = min(start.zMeters, end.zMeters)
            val topZ = max(start.zMeters, end.zMeters)
            val level = storySystem.levelAtElevation(bottomZ, tolerance = 0.10)
            val storyHeight = level?.heightHk ?: (topZ - bottomZ)
            val beamDepthCm = max(
                beamDepthAtNode[frame.startNodeId] ?: 0.0,
                beamDepthAtNode[frame.endNodeId] ?: 0.0
            )
            val clearHeight = (storyHeight - beamDepthCm / 100.0).coerceAtLeast(0.0)

            candidates += ColumnCandidate(
                name = frame.sectionName,
                dims = dims,
                clearHeightMeters = clearHeight,
                beamHeightCm = beamDepthCm,
                isWallBoundary = frame.startNodeId in wallNodeIds || frame.endNodeId in wallNodeIds,
                storyName = level?.storyName ?: frame.storyName ?: "",
                elementId = frame.id
            )
        }

        return candidates.groupBy { it.name }.map { (name, group) ->
            val first = group.first()
            ColumnFamily(
                name = name,
                shape = first.dims.shape,
                b1Cm = first.dims.widthCm,
                h1Cm = first.dims.depthCm,
                clearHeightLclMeters = group.maxOf { it.clearHeightMeters },
                diameterCm = first.dims.diameterCm,
                isWallBoundary = group.any { it.isWallBoundary },
                supportingBeamHeightCm = group.maxOf { it.beamHeightCm }.takeIf { it > 0.0 },
                stories = group.map { it.storyName }.filter { it.isNotBlank() }.distinct().sorted(),
                elementIds = group.map { it.elementId }
            )
        }.sortedBy { it.name }
    }

    private fun buildBeamFamilies(
        beamFrames: List<FrameElement>,
        raw: RawGeometry,
        sectionsByName: Map<String, EtabsSection>,
        columnDimensionAtNode: Map<String, Double>,
        storySystem: StorySystem,
        warnings: MutableList<String>
    ): List<BeamFamily> {
        val candidates = mutableListOf<BeamCandidate>()

        for (frame in beamFrames) {
            val start = raw.points[frame.startNodeId]
            val end = raw.points[frame.endNodeId]
            if (start == null || end == null) {
                warnings += "Poutre '${frame.id}' ignorée : nœuds introuvables."
                continue
            }
            val section = sectionsByName[normalize(frame.sectionName)]
            val dims = frameDimensions(section, frame.sectionName)
            if (dims == null) {
                warnings += "Poutre '${frame.id}' ignorée : dimensions introuvables pour la section '${frame.sectionName}'."
                continue
            }

            val axisLength = start.planDistanceTo(end)
            val supportStart = columnDimensionAtNode[frame.startNodeId] ?: 0.0
            val supportEnd = columnDimensionAtNode[frame.endNodeId] ?: 0.0
            val clearSpan = (axisLength - (supportStart / 2.0 + supportEnd / 2.0) / 100.0).coerceAtLeast(0.05)
            val level = storySystem.levelAtElevation((start.zMeters + end.zMeters) / 2.0, tolerance = 0.10)

            candidates += BeamCandidate(
                name = frame.sectionName,
                widthCm = dims.widthCm,
                heightCm = dims.depthCm,
                clearSpanMeters = clearSpan,
                supportWidthCm = max(supportStart, supportEnd),
                supportDepthCm = max(supportStart, supportEnd),
                role = detectBeamRole(frame.sectionName, frame.id),
                storyName = level?.storyName ?: frame.storyName ?: "",
                elementId = frame.id
            )
        }

        return candidates.groupBy { it.name }.map { (name, group) ->
            val first = group.first()
            BeamFamily(
                name = name,
                role = if (group.any { it.role == BeamRole.MAIN_BEAM }) BeamRole.MAIN_BEAM else BeamRole.SECONDARY_TIE,
                bCm = first.widthCm,
                hCm = first.heightCm,
                maxClearSpanMeters = group.maxOf { it.clearSpanMeters },
                minClearSpanMeters = group.minOf { it.clearSpanMeters },
                maxSupportWidthCm = group.maxOf { it.supportWidthCm },
                maxSupportDepthCm = group.maxOf { it.supportDepthCm }.takeIf { it > 0.0 },
                maxAxisEccentricityCm = 0.0,
                continuousSpansMeters = group.map { it.clearSpanMeters }.distinct().sortedDescending(),
                elementIds = group.map { it.elementId }
            )
        }.sortedBy { it.name }
    }

    private fun buildWallFamilies(
        raw: RawGeometry,
        sectionsByName: Map<String, EtabsSection>,
        beamDepthAtNode: Map<String, Double>,
        storySystem: StorySystem,
        warnings: MutableList<String>
    ): List<ShearWallFamily> {
        val candidates = mutableListOf<WallCandidate>()

        for (area in raw.areas.filter { it.kind == AreaKind.WALL }) {
            val nodes = area.nodeIds.mapNotNull { raw.points[it] }
            if (nodes.size < 3) {
                warnings += "Voile '${area.id}' ignoré : moins de trois nœuds valides."
                continue
            }
            val section = sectionsByName[normalize(area.propertyName)]
            val dx = (nodes.maxOf { it.xMeters } - nodes.minOf { it.xMeters })
            val dy = (nodes.maxOf { it.yMeters } - nodes.minOf { it.yMeters })
            val length = max(dx, dy)
            val thickness = section?.thicknessCm?.div(100.0)?.takeIf { it > 0.0 }
                ?: min(dx, dy).takeIf { it in 0.05..1.5 }
            if (length <= 0.1 || thickness == null) {
                warnings += "Voile '${area.id}' ignoré : longueur ou épaisseur non identifiable."
                continue
            }

            val bottomZ = nodes.minOf { it.zMeters }
            val topZ = nodes.maxOf { it.zMeters }
            val level = storySystem.levelAtElevation(bottomZ, tolerance = 0.10)
            val storyHeight = level?.heightHk ?: (topZ - bottomZ)
            val beamDepthCm = nodes.maxOf { beamDepthAtNode[it.id] ?: 0.0 }
            val clearHeight = (storyHeight - beamDepthCm / 100.0).coerceAtLeast(0.05)

            val pier = area.pierName?.takeIf { it.isNotBlank() } ?: area.propertyName
            candidates += WallCandidate(
                name = pier,
                direction = if (dx >= dy) AxisDirection.X else AxisDirection.Y,
                lengthMeters = length,
                thicknessMeters = thickness,
                clearHeightMeters = clearHeight,
                bottomZ = bottomZ,
                topZ = topZ,
                storyName = level?.storyName ?: area.storyName ?: "",
                elementId = area.id
            )
        }

        return candidates.groupBy { it.name }.map { (name, group) ->
            val first = group.first()
            ShearWallFamily(
                pierName = name,
                direction = first.direction,
                lengthLwMeters = group.maxOf { it.lengthMeters },
                thicknessBwMeters = group.maxOf { it.thicknessMeters },
                clearHeightHeMeters = group.maxOf { it.clearHeightMeters },
                totalHeightHwMeters = (group.maxOf { it.topZ } - group.minOf { it.bottomZ }).coerceAtLeast(first.clearHeightMeters),
                stories = group.map { it.storyName }.filter { it.isNotBlank() }.distinct().sorted(),
                elementIds = group.map { it.elementId }
            )
        }.sortedBy { it.pierName }
    }

    private fun buildSlabFamilies(
        raw: RawGeometry,
        sectionsByName: Map<String, EtabsSection>,
        gridSystem: GridSystem,
        warnings: MutableList<String>
    ): List<SlabStripTopology> {
        val candidates = mutableListOf<SlabCandidate>()

        for (area in raw.areas.filter { it.kind == AreaKind.SLAB }) {
            val nodes = area.nodeIds.mapNotNull { raw.points[it] }
            if (nodes.size < 3) continue
            val bbox = BoundingBox(
                minX = nodes.minOf { it.xMeters },
                maxX = nodes.maxOf { it.xMeters },
                minY = nodes.minOf { it.yMeters },
                maxY = nodes.maxOf { it.yMeters }
            )
            val section = sectionsByName[normalize(area.propertyName)]
            val thickness = section?.totalThicknessCm
                ?: section?.thicknessCm
                ?: parseSlabDimensions(area.propertyName)?.totalCm
            if (thickness == null || thickness <= 0.0) {
                warnings += "Plancher '${area.id}' ignoré : épaisseur introuvable pour la propriété '${area.propertyName}'."
                continue
            }

            val parsed = section?.let {
                if (it.compressionSlabThicknessCm != null && it.hollowBlockHeightCm != null) {
                    SlabParsed(it.compressionSlabThicknessCm, it.hollowBlockHeightCm, it.totalThicknessCm ?: thickness, false)
                } else null
            } ?: parseSlabDimensions(area.propertyName)

            val isSolid = parsed?.hollow != false || area.propertyName.uppercase().contains("PLEINE")
            val h0 = if (isSolid) thickness else (parsed?.compressionSlabCm ?: 5.0)
            val hollow = if (isSolid) 0.0 else (parsed?.hollowBlockCm ?: (thickness - h0))
            val ribWidth = section?.ribWidthCm ?: 10.0
            val ribSpacing = section?.ribSpacingCm ?: 60.0

            // Les nervures portent sur la direction qui minimise la portee entre files.
            val spansX = ribSpans(bbox, gridSystem, alongX = true)
            val spansY = ribSpans(bbox, gridSystem, alongX = false)
            val spans = if ((spansX.maxOrNull() ?: 0.0) <= (spansY.maxOrNull() ?: 0.0)) spansX else spansY

            candidates += SlabCandidate(
                name = area.propertyName,
                thicknessCm = thickness,
                compressionSlabCm = h0,
                hollowBlockCm = hollow,
                ribWidthCm = ribWidth,
                ribSpacingCm = ribSpacing,
                spans = spans,
                areaSquareMeters = bbox.widthX * bbox.lengthY,
                isSolid = isSolid,
                elementId = area.id
            )
        }

        return candidates.groupBy { it.name }.map { (name, group) ->
            val representative = group.maxBy { it.areaSquareMeters }
            SlabStripTopology(
                name = name,
                totalThicknessHtCm = group.maxOf { it.thicknessCm },
                compressionSlabH0Cm = representative.compressionSlabCm,
                hollowBlockHeightCm = representative.hollowBlockCm,
                ribWidthB0Cm = representative.ribWidthCm,
                flangeWidthBCm = representative.ribSpacingCm,
                continuousSpansMeters = representative.spans,
                ribSpacingCm = representative.ribSpacingCm,
                isSolidSlab = representative.isSolid,
                elementIds = group.map { it.elementId }
            )
        }.sortedBy { it.name }
    }

    private fun ribSpans(bbox: BoundingBox, gridSystem: GridSystem, alongX: Boolean): List<Double> {
        val coordinates = if (alongX) {
            gridSystem.sortedXGrids.map { it.coordinateMeters }
        } else {
            gridSystem.sortedYGrids.map { it.coordinateMeters }
        }
        val minCoordinate = if (alongX) bbox.minX else bbox.minY
        val maxCoordinate = if (alongX) bbox.maxX else bbox.maxY
        val inside = coordinates.filter { it >= minCoordinate - 0.01 && it <= maxCoordinate + 0.01 }
        val spans = inside.zipWithNext { left, right -> right - left }.filter { it > 0.05 }
        return if (spans.isNotEmpty()) spans else listOf(maxCoordinate - minCoordinate)
    }

    private fun inferFrameKind(frame: FrameElement, points: Map<String, Point3D>): FrameKind {
        val start = points[frame.startNodeId] ?: return FrameKind.OTHER
        val end = points[frame.endNodeId] ?: return FrameKind.OTHER
        val dz = abs(end.zMeters - start.zMeters)
        val plan = start.planDistanceTo(end)
        return when {
            dz > 0.30 && plan < 0.10 -> FrameKind.COLUMN
            dz < 0.10 && plan > 0.10 -> FrameKind.BEAM
            else -> FrameKind.BRACE
        }
    }

    private fun detectBeamRole(sectionName: String, elementId: String): BeamRole {
        val normalized = "$sectionName $elementId".uppercase()
        val tieMarkers = listOf("CHAIN", "CHAÎN", "CHAINAGE", "TIE", "LINTEAU", "LINT", "CH-", "CH_")
        val isTie = tieMarkers.any { normalized.contains(it) } ||
            Regex("(^|[^A-Z])CH([^A-Z]|$)").containsMatchIn(normalized)
        return if (isTie) BeamRole.SECONDARY_TIE else BeamRole.MAIN_BEAM
    }

    private fun frameDimensions(section: EtabsSection?, sectionName: String): FrameDimensions? {
        if (section != null) {
            if (section.kind == EtabsSectionKind.FRAME_CIRCULAR) {
                val diameter = section.diameterCm ?: section.widthCm ?: section.depthCm
                if (diameter != null && diameter > 0.0) {
                    return FrameDimensions(diameter, diameter, diameter, ColumnShape.CIRCULAR)
                }
            }
            val width = section.widthCm
            val depth = section.depthCm
            if (width != null && depth != null && width > 0.0 && depth > 0.0) {
                return FrameDimensions(width, depth, null, ColumnShape.RECTANGULAR)
            }
            val diameter = section.diameterCm
            if (diameter != null && diameter > 0.0) {
                return FrameDimensions(diameter, diameter, diameter, ColumnShape.CIRCULAR)
            }
        }

        parseRectangularDimensions(sectionName)?.let { (first, second) ->
            return FrameDimensions(first, second, null, ColumnShape.RECTANGULAR)
        }
        parseDiameter(sectionName)?.let { diameter ->
            return FrameDimensions(diameter, diameter, diameter, ColumnShape.CIRCULAR)
        }
        return null
    }

    private fun parseRectangularDimensions(name: String): Pair<Double, Double>? {
        val match = RECTANGULAR_PATTERN.find(name) ?: return null
        val first = parseLengthToCm(match.groupValues[1]) ?: return null
        val second = parseLengthToCm(match.groupValues[2]) ?: return null
        return if (first > 0.0 && second > 0.0) first to second else null
    }

    private fun parseDiameter(name: String): Double? {
        val match = DIAMETER_PATTERN.find(name) ?: return null
        return parseLengthToCm(match.groupValues[1])
    }

    private fun parseLengthToCm(raw: String): Double? {
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
        return if (value <= 5.0) value * 100.0 else value
    }

    private fun parseSlabDimensions(name: String): SlabParsed? {
        val hollowMatch = HOLLOW_SLAB_PATTERN.find(name)
        if (hollowMatch != null) {
            val block = hollowMatch.groupValues[1].replace(',', '.').toDoubleOrNull()
            val slab = hollowMatch.groupValues[2].replace(',', '.').toDoubleOrNull()
            if (block != null && slab != null && block > 0.0 && slab > 0.0) {
                return SlabParsed(
                    compressionSlabCm = slab,
                    hollowBlockCm = block,
                    totalCm = block + slab,
                    hollow = true
                )
            }
        }
        val plain = PLAIN_THICKNESS_PATTERN.find(name)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        return plain?.let { SlabParsed(compressionSlabCm = it, hollowBlockCm = 0.0, totalCm = it, hollow = false) }
    }

    private fun isPenthouseOrCage(storyName: String): Boolean {
        val normalized = storyName.uppercase()
        return listOf(
            "CAGE", "ÉDICULE", "EDICULE", "TOIT", "ROOF", "PENTHOUSE",
            "ASCENSEUR", "MACHINE", "LOCAL TECHNIQUE", "TERRASSE TECHNIQUE"
        ).any { normalized.contains(it) }
    }

    private fun normalize(name: String): String = name.uppercase().replace(Regex("[^A-Z0-9+]"), "")

    private fun distinctSorted(values: List<Double>): List<Double> {
        val sorted = values.filter { it.isFinite() }.sorted()
        val result = mutableListOf<Double>()
        for (value in sorted) {
            if (result.isEmpty() || abs(value - result.last()) > 0.01) result += value
        }
        return result
    }

    private data class FrameDimensions(
        val widthCm: Double,
        val depthCm: Double,
        val diameterCm: Double?,
        val shape: ColumnShape
    )

    private data class SlabParsed(
        val compressionSlabCm: Double,
        val hollowBlockCm: Double,
        val totalCm: Double,
        val hollow: Boolean
    )

    private data class ColumnCandidate(
        val name: String,
        val dims: FrameDimensions,
        val clearHeightMeters: Double,
        val beamHeightCm: Double,
        val isWallBoundary: Boolean,
        val storyName: String,
        val elementId: String
    )

    private data class BeamCandidate(
        val name: String,
        val widthCm: Double,
        val heightCm: Double,
        val clearSpanMeters: Double,
        val supportWidthCm: Double,
        val supportDepthCm: Double,
        val role: BeamRole,
        val storyName: String,
        val elementId: String
    )

    private data class WallCandidate(
        val name: String,
        val direction: AxisDirection,
        val lengthMeters: Double,
        val thicknessMeters: Double,
        val clearHeightMeters: Double,
        val bottomZ: Double,
        val topZ: Double,
        val storyName: String,
        val elementId: String
    )

    private data class SlabCandidate(
        val name: String,
        val thicknessCm: Double,
        val compressionSlabCm: Double,
        val hollowBlockCm: Double,
        val ribWidthCm: Double,
        val ribSpacingCm: Double,
        val spans: List<Double>,
        val areaSquareMeters: Double,
        val isSolid: Boolean,
        val elementId: String
    )

    private companion object {
        private val RECTANGULAR_PATTERN = Regex("(\\d+(?:[.,]\\d+)?)\\s*[xX×*]\\s*(\\d+(?:[.,]\\d+)?)")
        private val DIAMETER_PATTERN = Regex("[Dd](?:IA)?\\s*(\\d+(?:[.,]\\d+)?)")
        private val HOLLOW_SLAB_PATTERN = Regex("(\\d{1,3})\\s*\\+\\s*(\\d{1,3})")
        private val PLAIN_THICKNESS_PATTERN = Regex("(\\d{1,3})(?:\\s*CM)?$")
    }
}
