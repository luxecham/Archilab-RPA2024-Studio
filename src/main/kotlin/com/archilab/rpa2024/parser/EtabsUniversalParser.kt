package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.*

class EtabsUniversalParser {

    fun parseE2kStream(inputStream: InputStream, model: StructuralJointModel) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var currentBlock = ""

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(";")) continue

                if (trimmed.startsWith("$")) {
                    currentBlock = trimmed.uppercase()
                    continue
                }

                when (currentBlock) {
                    "$ STORIES - STORY DATA", "$ STORIES" -> parseStoryLine(trimmed, model)
                    "$ POINT COORDINATES" -> parsePointCoordinate(trimmed, model)
                    "$ FRAME ASSIGNMENTS", "$ LINE INFORMATION" -> parseFrameAssignment(trimmed, model)
                    "$ WALL/PIER INFORMATION", "$ PIER ASSIGNMENTS" -> parsePierAssignment(trimmed, model)
                }
            }
        }
        recomputeTopologyDerivations(model)
    }

    private fun parseStoryLine(line: String, model: StructuralJointModel) {
        val tokens = tokenizeE2kLine(line)
        if (tokens.size >= 3) {
            val storyName = tokens[0]
            val height = tokens[1].toDoubleOrNull() ?: 3.74
            val elev = tokens[2].toDoubleOrNull() ?: 0.0
            model.stories.add(StoryLevel(storyName, height, elev))
        }
    }

    private fun parsePointCoordinate(line: String, model: StructuralJointModel) {
        val tokens = tokenizeE2kLine(line)
        if (tokens.size >= 4) {
            val id = tokens[0]
            val x = tokens[1].toDoubleOrNull() ?: 0.0
            val y = tokens[2].toDoubleOrNull() ?: 0.0
            val z = tokens[3].toDoubleOrNull() ?: 0.0
            model.points[id] = Point3D(id, x, y, z)
        }
    }

    private fun parseFrameAssignment(line: String, model: StructuralJointModel) {
        val tokens = tokenizeE2kLine(line)
        if (tokens.size >= 5) {
            val frameId = tokens[0]
            val nodeI = model.points[tokens[1]] ?: Point3D(tokens[1], 0.0, 0.0, 0.0)
            val nodeJ = model.points[tokens[2]] ?: Point3D(tokens[2], 0.0, 0.0, 3.74)
            val sectionName = tokens[3].uppercase()
            val storyName = tokens[4]

            val dx = nodeJ.x - nodeI.x
            val dy = nodeJ.y - nodeI.y
            val dz = nodeJ.z - nodeI.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)

            val isVertical = abs(dz) > 0.8 * length
            if (isVertical) {
                val (bCm, hCm) = extractDimensionsFromSectionName(sectionName, defaultB = 35.0, defaultH = 50.0)
                model.columns.add(
                    ColumnElement(
                        id = frameId,
                        story = storyName,
                        sectionName = sectionName,
                        bCm = bCm,
                        hCm = hCm,
                        heightMeters = length,
                        clearHeightMeters = length - 0.50, // Calcul initial
                        nodeBase = nodeI,
                        nodeTop = nodeJ
                    )
                )
            } else {
                val (bCm, hCm) = extractDimensionsFromSectionName(sectionName, defaultB = 30.0, defaultH = 55.0)
                val isPrincipal = sectionName.contains("PR") || sectionName.contains("PRINCIPALE")
                model.beams.add(
                    BeamElement(
                        id = frameId,
                        story = storyName,
                        sectionName = sectionName,
                        isPrimary = isPrincipal,
                        bCm = bCm,
                        hCm = hCm,
                        lengthAxisMeters = length,
                        lengthClearMeters = max(0.5, length - 0.40),
                        nodeStart = nodeI,
                        nodeEnd = nodeJ
                    )
                )
            }
        }
    }

    private fun parsePierAssignment(line: String, model: StructuralJointModel) {
        val tokens = tokenizeE2kLine(line)
        if (tokens.size >= 5) {
            val pierName = tokens[0]
            val story = tokens[1]
            val lw = tokens[2].toDoubleOrNull() ?: 1.40
            val bw = tokens[3].toDoubleOrNull() ?: 0.30
            val he = tokens[4].toDoubleOrNull() ?: 3.39
            model.shearWalls.add(
                ShearWallPier(
                    pierName = pierName,
                    story = story,
                    lengthLwMeters = lw,
                    thicknessBwMeters = bw,
                    clearHeightHeMeters = he,
                    elevationZkMeters = 0.0
                )
            )
        }
    }

    private fun recomputeTopologyDerivations(model: StructuralJointModel) {
        // Déduction exacte des hauteurs libres de poteaux l_cl = hk - h_poutre_max
        for (i in model.columns.indices) {
            val col = model.columns[i]
            val adjacentBeams = model.beams.filter { it.story == col.story }
            val maxBeamH = adjacentBeams.maxOfOrNull { it.hCm / 100.0 } ?: 0.55
            val refinedClearH = max(1.0, col.heightMeters - maxBeamH)
            model.columns[i] = col.copy(clearHeightMeters = refinedClearH)
        }
    }

    private fun extractDimensionsFromSectionName(name: String, defaultB: Double, defaultH: Double): Pair<Double, Double> {
        val clean = name.replace("POT", "").replace("PR", "").replace("CH", "").trim()
        val parts = clean.split("X", "*", " ", "_").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            val d1 = parts[0].toDoubleOrNull()
            val d2 = parts[1].toDoubleOrNull()
            if (d1 != null && d2 != null) {
                return Pair(d1, d2)
            }
        }
        return Pair(defaultB, defaultH)
    }

    private fun tokenizeE2kLine(line: String): List<String> {
        val regex = """"([^"]*)"|(\S+)""".toRegex()
        return regex.findAll(line.trim()).map { matchResult ->
            matchResult.groups[1]?.value ?: matchResult.groups[2]?.value ?: ""
        }.filter { it.isNotEmpty() }.toList()
    }
}
