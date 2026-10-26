package com.archilab.rpa2024.bridge

import com.archilab.rpa2024.engine.foundations.LineSpringFamily
import com.archilab.rpa2024.engine.foundations.StripFootingFamily
import com.archilab.rpa2024.engine.foundations.StripFootingKind
import com.archilab.rpa2024.model.GridLine
import com.archilab.rpa2024.parser.E2kModelParser
import com.archilab.rpa2024.parser.Point3D
import com.archilab.rpa2024.parser.RawGeometry
import com.archilab.rpa2024.parser.UnitSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cbrt
import kotlin.math.max

data class SupportReaction(
    val nodeId: String,
    val loadCase: String,
    val fxKN: Double = 0.0,
    val fyKN: Double = 0.0,
    val fzKN: Double = 0.0,
    val mxKNm: Double = 0.0,
    val myKNm: Double = 0.0,
    val mzKNm: Double = 0.0
)

data class FoundationSoilInput(
    val allowableStressKNM2: Double,
    val directSubgradeModulusKNM3: Double? = null,
    val safetyFactor: Double = 3.0,
    val allowableSettlementMeters: Double? = null
) {
    init {
        require(allowableStressKNM2 > 0.0) { "La contrainte admissible du sol doit être positive." }
        require(directSubgradeModulusKNM3 == null || directSubgradeModulusKNM3 > 0.0) {
            "Le module Ks direct doit être positif."
        }
        require(safetyFactor > 0.0) { "Le coefficient FS de Bowles doit être positif." }
        require(allowableSettlementMeters == null || allowableSettlementMeters > 0.0) {
            "Le tassement admissible doit être positif."
        }
    }

    fun subgradeModulusKNM3(): Double = directSubgradeModulusKNM3
        ?: allowableSettlementMeters?.let { allowableStressKNM2 / it }
        ?: 40.0 * safetyFactor * allowableStressKNM2
}

data class SafeBridgeInput(
    val geometry: RawGeometry,
    val reactions: List<SupportReaction> = emptyList(),
    val soil: FoundationSoilInput,
    val outputUnits: UnitSystem = geometry.units ?: UnitSystem.KN_M,
    val baseStory: String = "BASE",
    val concreteName: String = "BA_FOND_C25",
    val fc28KNM2: Double = 25_000.0,
    val initialWidthMeters: Double = 0.80,
    val widthTiersMeters: List<Double> = listOf(0.40, 0.60, 0.80, 1.00, 1.20, 1.50, 1.80, 2.00, 2.50, 3.00),
    val footingThicknessMeters: Double = 0.40,
    val supportedWallWidthMeters: Double = 0.30,
    val compressionOnly: Boolean = true,
    val seismicJointGridLabels: Set<String> = emptySet(),
    val seismicJointNodeIds: Set<String> = emptySet(),
    val serviceLoadCase: String = "ELS"
) {
    init {
        require(initialWidthMeters > 0.0 && footingThicknessMeters > 0.0 && supportedWallWidthMeters > 0.0) {
            "Les dimensions des semelles doivent être positives."
        }
        require(widthTiersMeters.isNotEmpty() && widthTiersMeters.all { it > 0.0 }) {
            "La série de largeurs doit contenir des valeurs positives."
        }
        require(fc28KNM2 > 0.0) { "La résistance du béton de fondation doit être positive." }
    }
}

data class SafeStripFooting(
    val family: StripFootingFamily,
    val spring: LineSpringFamily,
    val axis: String,
    val gridLabel: String,
    val coordinateMeters: Double,
    val start: Point3D,
    val end: Point3D,
    val exterior: Boolean,
    val seismicJoint: Boolean,
    val eccentricityMeters: Double,
    val inwardSign: Int,
    val supportNodeIds: List<String>
)

data class FootingOptimizationResult(
    val footingName: String,
    val previousWidthMeters: Double,
    val proposedWidthMeters: Double,
    val previousSettlementMeters: Double,
    val proposedSettlementMeters: Double,
    val previousStressKNM2: Double,
    val proposedStressKNM2: Double,
    val previousLineStiffnessKNM2: Double,
    val proposedLineStiffnessKNM2: Double,
    val converged: Boolean
)

data class SafeFoundationGeneration(
    val text: String,
    val footings: List<SafeStripFooting>,
    val materials: List<EtabsMaterialProperties>,
    val subgradeModulusKNM3: Double,
    val basePoints: List<Point3D>,
    val reactions: List<SupportReaction>,
    val optimizations: List<FootingOptimizationResult> = emptyList(),
    val warnings: List<String> = emptyList()
)

class EtabsToSafeFoundationBridge {
    fun materials(input: SafeBridgeInput): List<EtabsMaterialProperties> {
        val elastic = 3_700_000.0 * cbrt(input.fc28KNM2 / 1_000.0)
        val poisson = 0.20
        return listOf(
            EtabsMaterialProperties(
                input.concreteName, "CONCRETE", 2.548, 25.0,
                elastic, elastic / (2.0 * (1.0 + poisson)), poisson, 1.0e-5,
                compressiveStrengthKNM2 = input.fc28KNM2
            ),
            EtabsMaterialProperties(
                "FE500", "REBAR", 7.85, 76.972, 210_000_000.0,
                210_000_000.0 / 2.6, 0.30, 1.2e-5,
                yieldStrengthKNM2 = 500_000.0, tensileStrengthKNM2 = 550_000.0
            ),
            EtabsMaterialProperties(
                "FE235", "REBAR", 7.85, 76.972, 210_000_000.0,
                210_000_000.0 / 2.6, 0.30, 1.2e-5,
                yieldStrengthKNM2 = 235_000.0, tensileStrengthKNM2 = 410_000.0
            )
        )
    }

    fun build(input: SafeBridgeInput, existingText: String? = null): SafeFoundationGeneration =
        generate(input, existingText, emptyMap(), emptyList())

    fun buildOrUpdate(input: SafeBridgeInput, source: Path, output: Path = source): SafeFoundationGeneration {
        val existing = if (Files.exists(source)) E2kModelParser.readText(source) else null
        val result = build(input, existing)
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, result.text, Charsets.UTF_8)
        return result
    }

    fun optimize(
        input: SafeBridgeInput,
        existingText: String? = null,
        measuredSettlementsMeters: Map<String, Double> = emptyMap()
    ): SafeFoundationGeneration {
        val initial = build(input, existingText)
        val ks = initial.subgradeModulusKNM3
        val tiers = input.widthTiersMeters.distinct().sorted()
        val widths = mutableMapOf<String, Double>()
        val results = mutableListOf<FootingOptimizationResult>()
        val warnings = mutableListOf<String>()
        initial.footings.forEach { footing ->
            val current = footing.family.flangeWidthMeters
            val length = footing.start.planDistanceTo(footing.end).coerceAtLeast(1e-6)
            val measured = measuredSettlementsMeters[footing.family.name]?.let(::abs)
            val axial = input.reactions
                .filter { it.loadCase.equals(input.serviceLoadCase, true) && it.nodeId in footing.supportNodeIds }
                .sumOf { abs(it.fzKN) }
            if (measured == null && axial <= 0.0) {
                warnings += "${footing.family.name} : Δmax SAFE et réaction ${input.serviceLoadCase} absents, " +
                    "optimisation non vérifiée."
                results += FootingOptimizationResult(
                    footing.family.name, current, current, 0.0, 0.0, 0.0, 0.0,
                    footing.spring.lineStiffnessKNM2, footing.spring.lineStiffnessKNM2, false
                )
                return@forEach
            }
            val settlement = measured ?: axial / (ks * current * length)
            val stress = settlement * ks
            if (stress <= input.soil.allowableStressKNM2 + 1e-9) {
                results += FootingOptimizationResult(
                    footing.family.name, current, current, settlement, settlement,
                    stress, stress, footing.spring.lineStiffnessKNM2, footing.spring.lineStiffnessKNM2, true
                )
                return@forEach
            }
            val required = max(current * stress / input.soil.allowableStressKNM2, current)
            val proposed = tiers.firstOrNull { it > current + 1e-9 && it + 1e-9 >= required }
            if (proposed == null) {
                warnings += "${footing.family.name} : largeur requise ${format(required)} m ; " +
                    "aucun palier supérieur disponible, relancer une analyse SAFE après adaptation de la série."
                results += FootingOptimizationResult(
                    footing.family.name, current, current, settlement, settlement, stress, stress,
                    footing.spring.lineStiffnessKNM2, footing.spring.lineStiffnessKNM2, false
                )
                return@forEach
            }
            widths[footing.family.name] = proposed
            val proposedSettlement = settlement * current / proposed
            results += FootingOptimizationResult(
                footingName = footing.family.name,
                previousWidthMeters = current,
                proposedWidthMeters = proposed,
                previousSettlementMeters = settlement,
                proposedSettlementMeters = proposedSettlement,
                previousStressKNM2 = stress,
                proposedStressKNM2 = proposedSettlement * ks,
                previousLineStiffnessKNM2 = footing.spring.lineStiffnessKNM2,
                proposedLineStiffnessKNM2 = ks * proposed,
                converged = proposedSettlement * ks <= input.soil.allowableStressKNM2 + 1e-6
            )
        }
        return generate(input, existingText, widths, results, initial.warnings + warnings)
    }

    fun optimizeFile(
        input: SafeBridgeInput,
        source: Path,
        output: Path = source,
        measuredSettlementsMeters: Map<String, Double> = emptyMap()
    ): SafeFoundationGeneration {
        val existing = if (Files.exists(source)) E2kModelParser.readText(source) else null
        val result = optimize(input, existing, measuredSettlementsMeters)
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, result.text, Charsets.UTF_8)
        return result
    }

    private fun generate(
        input: SafeBridgeInput,
        existingText: String?,
        widthOverrides: Map<String, Double>,
        optimizations: List<FootingOptimizationResult>,
        additionalWarnings: List<String> = emptyList()
    ): SafeFoundationGeneration {
        require(input.geometry.points.isNotEmpty()) { "Le modèle ETABS ne contient aucun nœud." }
        val baseElevation = input.geometry.points.values.minOf { it.zMeters }
        val tolerance = max(1e-6, input.geometry.points.values.maxOf { it.zMeters }.minus(baseElevation) * 1e-6)
        val basePoints = input.geometry.points.values
            .filter { abs(it.zMeters - baseElevation) <= tolerance }
            .sortedBy { it.id }
        require(basePoints.isNotEmpty()) { "Aucun nœud du niveau de base n'a été détecté." }
        val knownReactions = input.reactions.filter { reaction ->
            input.geometry.points[reaction.nodeId]?.let { abs(it.zMeters - baseElevation) <= tolerance } == true
        }
        val warnings = additionalWarnings.toMutableList()
        val ignored = input.reactions.size - knownReactions.size
        if (ignored > 0) warnings += "$ignored réaction(s) ignorée(s) : nœud inconnu ou hors niveau de base."

        val xGrids = grids(input.geometry.gridsX, basePoints.map { it.xMeters }, "X")
        val yGrids = grids(input.geometry.gridsY, basePoints.map { it.yMeters }, "Y")
        val minX = basePoints.minOf { it.xMeters }
        val maxX = basePoints.maxOf { it.xMeters }
        val minY = basePoints.minOf { it.yMeters }
        val maxY = basePoints.maxOf { it.yMeters }
        val edgeTolerance = max(1e-6, max(maxX - minX, maxY - minY) * 1e-6)
        require(maxX > minX && maxY > minY) { "Les semelles filantes exigent une emprise de base non nulle en X et Y." }
        val ks = input.soil.subgradeModulusKNM3()
        val footings = buildList {
            xGrids.forEach { grid ->
                val start = Point3D("", grid.coordinateMeters, minY, baseElevation)
                val end = Point3D("", grid.coordinateMeters, maxY, baseElevation)
                val exterior = abs(grid.coordinateMeters - minX) <= edgeTolerance ||
                    abs(grid.coordinateMeters - maxX) <= edgeTolerance
                val supports = basePoints.filter { abs(it.xMeters - grid.coordinateMeters) <= edgeTolerance }.map { it.id }
                val joint = exterior && (input.seismicJointGridLabels.any { it.equals(grid.label, ignoreCase = true) } ||
                    supports.any(input.seismicJointNodeIds::contains))
                val inward = if (abs(grid.coordinateMeters - minX) <= edgeTolerance) 1 else -1
                add(footing(input, grid, "X", start, end, exterior, joint, inward, supports, ks, widthOverrides))
            }
            yGrids.forEach { grid ->
                val start = Point3D("", minX, grid.coordinateMeters, baseElevation)
                val end = Point3D("", maxX, grid.coordinateMeters, baseElevation)
                val exterior = abs(grid.coordinateMeters - minY) <= edgeTolerance ||
                    abs(grid.coordinateMeters - maxY) <= edgeTolerance
                val supports = basePoints.filter { abs(it.yMeters - grid.coordinateMeters) <= edgeTolerance }.map { it.id }
                val joint = exterior && (input.seismicJointGridLabels.any { it.equals(grid.label, ignoreCase = true) } ||
                    supports.any(input.seismicJointNodeIds::contains))
                val inward = if (abs(grid.coordinateMeters - minY) <= edgeTolerance) 1 else -1
                add(footing(input, grid, "Y", start, end, exterior, joint, inward, supports, ks, widthOverrides))
            }
        }
        val materials = materials(input)
        val text = CsiManagedText.upsert(
            existingText ?: "TABLE: \"PROGRAM CONTROL\"\r\nCurrUnits=\"${input.outputUnits.code}\"",
            "SAFE",
            safeTables(input, footings, materials, basePoints, knownReactions, ks)
        )
        return SafeFoundationGeneration(
            text = text,
            footings = footings,
            materials = materials,
            subgradeModulusKNM3 = ks,
            basePoints = basePoints,
            reactions = knownReactions,
            optimizations = optimizations,
            warnings = warnings.distinct()
        )
    }

    private fun footing(
        input: SafeBridgeInput,
        grid: GridLine,
        axis: String,
        start: Point3D,
        end: Point3D,
        exterior: Boolean,
        joint: Boolean,
        inward: Int,
        supports: List<String>,
        ks: Double,
        widthOverrides: Map<String, Double>
    ): SafeStripFooting {
        val prefix = if (joint) "SFJ" else "SF"
        val name = "${prefix}_${axis}_${safeId(grid.label)}"
        val springName = "K_${axis}_${safeId(grid.label)}"
        val width = widthOverrides[name]
            ?: input.widthTiersMeters.filter { it + 1e-9 >= input.initialWidthMeters }.minOrNull()
            ?: input.widthTiersMeters.max()
        require(width + 1e-9 >= input.supportedWallWidthMeters) {
            "$name : largeur ${format(width)} m inférieure à l'élément porteur ${format(input.supportedWallWidthMeters)} m."
        }
        val eccentricity = if (joint) (width - input.supportedWallWidthMeters) / 2.0 else 0.0
        val length = start.planDistanceTo(end)
        val elementId = "RPA_${axis}_${safeId(grid.label)}"
        val family = StripFootingFamily(
            name = name,
            kind = if (joint) StripFootingKind.SEISMIC_JOINT else StripFootingKind.CURRENT,
            flangeWidthMeters = width,
            tableThicknessMeters = 0.0,
            webWidthMeters = width,
            totalHeightMeters = input.footingThicknessMeters,
            footingLengthMeters = length,
            materialName = input.concreteName,
            elementIds = listOf(elementId),
            sourceTable = "BEAM PROPERTIES"
        )
        return SafeStripFooting(
            family = family,
            spring = LineSpringFamily(springName, width, ks, ks * width, "LINE SPRING PROPERTIES"),
            axis = axis,
            gridLabel = grid.label,
            coordinateMeters = grid.coordinateMeters,
            start = start.copy(id = "${elementId}_I"),
            end = end.copy(id = "${elementId}_J"),
            exterior = exterior,
            seismicJoint = joint,
            eccentricityMeters = eccentricity,
            inwardSign = inward,
            supportNodeIds = supports
        )
    }

    private fun safeTables(
        input: SafeBridgeInput,
        footings: List<SafeStripFooting>,
        materials: List<EtabsMaterialProperties>,
        basePoints: List<Point3D>,
        reactions: List<SupportReaction>,
        ks: Double
    ): String = buildString {
        table("MATERIAL PROPERTIES 01 - GENERAL", materials.map {
            mapOf("Material" to it.name, "Type" to it.type, "DesignType" to it.type)
        })
        table("MATERIAL PROPERTIES 02 - MECHANICAL", materials.map { material ->
            buildMap {
                put("Material", material.name)
                put("MassPerVol", CsiText.number(CsiUnits.massDensity(material.massDensityTonsM3, input.outputUnits)))
                put("WeightPerVol", CsiText.number(CsiUnits.weightDensity(material.weightDensityKNM3, input.outputUnits)))
                put("E", CsiText.number(CsiUnits.stress(material.elasticModulusKNM2, input.outputUnits)))
                put("G", CsiText.number(CsiUnits.stress(material.shearModulusKNM2, input.outputUnits)))
                put("U", CsiText.number(material.poisson))
                put("A", CsiText.number(material.thermalCoefficient))
                material.compressiveStrengthKNM2?.let { put("Fc", CsiText.number(CsiUnits.stress(it, input.outputUnits))) }
                material.yieldStrengthKNM2?.let { put("Fy", CsiText.number(CsiUnits.stress(it, input.outputUnits))) }
                material.tensileStrengthKNM2?.let { put("Fu", CsiText.number(CsiUnits.stress(it, input.outputUnits))) }
            }
        })
        table("BEAM PROPERTIES", footings.map { footing ->
            mapOf(
                "Name" to footing.family.name, "Material" to input.concreteName, "Shape" to "RECTANGULAR",
                "B" to length(footing.family.flangeWidthMeters, input.outputUnits),
                "b0" to length(footing.family.webWidthMeters, input.outputUnits),
                "h" to length(footing.family.totalHeightMeters, input.outputUnits),
                "h0" to "0", "Length" to length(footing.family.footingLengthMeters ?: 0.0, input.outputUnits),
                "Eccentricity" to length(footing.eccentricityMeters * footing.inwardSign, input.outputUnits),
                "Elements" to footing.family.elementIds.joinToString(",")
            )
        })
        table("LINE SPRING PROPERTIES", footings.map { footing ->
            mapOf(
                "Name" to footing.spring.name,
                "Width" to length(footing.family.flangeWidthMeters, input.outputUnits),
                "Ks" to CsiText.number(ks / input.outputUnits.forceFactorToKN * input.outputUnits.lengthFactorToMeters.pow3()),
                "Kline" to CsiText.number(CsiUnits.lineStiffness(footing.spring.lineStiffnessKNM2, input.outputUnits)),
                "CompressionOnly" to if (input.compressionOnly) "YES" else "NO"
            )
        })
        val generatedPoints = footings.flatMap { listOf(it.start, it.end) }
        table("POINT COORDINATES", (basePoints + generatedPoints).distinctBy { it.id }.map {
            mapOf("Point" to it.id, "Story" to input.baseStory,
                "X" to length(it.xMeters, input.outputUnits), "Y" to length(it.yMeters, input.outputUnits),
                "Z" to length(it.zMeters, input.outputUnits))
        })
        table("LINE CONNECTIVITIES", footings.map {
            mapOf("Line" to it.family.elementIds.single(), "Point1" to it.start.id, "Point2" to it.end.id,
                "Story" to input.baseStory, "LineType" to "BEAM")
        })
        table("LINE ASSIGNMENTS", footings.map {
            mapOf("Line" to it.family.elementIds.single(), "Story" to input.baseStory,
                "Section" to it.family.name, "Spring" to it.spring.name,
                "GridAxis" to it.axis, "GridLabel" to it.gridLabel)
        })
        table("LOAD PATTERNS", reactions.map { it.loadCase }.distinct().sorted().map { case ->
            mapOf("LoadPat" to case, "Type" to if (case.equals("G", true)) "DEAD" else "OTHER", "SelfWeight" to "0")
        })
        table("JOINT LOADS", reactions.map { reaction ->
            mapOf(
                "Point" to reaction.nodeId, "LoadPat" to reaction.loadCase,
                "FX" to CsiText.number(-reaction.fxKN / input.outputUnits.forceFactorToKN),
                "FY" to CsiText.number(-reaction.fyKN / input.outputUnits.forceFactorToKN),
                "FZ" to CsiText.number(-reaction.fzKN / input.outputUnits.forceFactorToKN),
                "MX" to CsiText.number(-reaction.mxKNm / input.outputUnits.momentFactorToKNm),
                "MY" to CsiText.number(-reaction.myKNm / input.outputUnits.momentFactorToKNm),
                "MZ" to CsiText.number(-reaction.mzKNm / input.outputUnits.momentFactorToKNm)
            )
        })
    }

    private fun grids(declared: List<GridLine>, coordinates: List<Double>, prefix: String): List<GridLine> {
        if (declared.isNotEmpty()) return declared.distinctBy { it.label to round(it.coordinateMeters) }
            .sortedBy { it.coordinateMeters }
        return coordinates.distinct().sorted().mapIndexed { index, coordinate ->
            GridLine("$prefix${index + 1}", coordinate)
        }
    }

    private fun safeId(value: String): String = value.trim().uppercase()
        .replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifBlank { "GRID" }

    private fun length(value: Double, units: UnitSystem): String = CsiText.number(value / units.lengthFactorToMeters)

    private fun round(value: Double): Double = ceil(value * 1e6) / 1e6

    private fun Double.pow3(): Double = this * this * this

    private fun format(value: Double): String = CsiText.number(value)
}

private fun StringBuilder.table(name: String, rows: List<Map<String, String>>) {
    append("TABLE: \"").append(name).append("\"\r\n")
    rows.forEach { append(CsiText.row(it)).append("\r\n") }
    append("\r\n")
}
