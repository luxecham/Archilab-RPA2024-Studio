package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class SafeUniversalParser {

    fun parseSafeStream(inputStream: InputStream, model: StructuralJointModel) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var currentTable = ""

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(";")) continue

                if (trimmed.startsWith("$")) {
                    currentTable = trimmed.uppercase()
                    continue
                }

                when {
                    currentTable.contains("LINE SPRING") || currentTable.contains("SPRING PROPERTIES") -> {
                        parseLineSpring(trimmed, model)
                    }
                    currentTable.contains("BEAM PROPERTIES") -> {
                        parseBeamProperty(trimmed, model)
                    }
                }
            }
        }
    }

    private fun parseLineSpring(line: String, model: StructuralJointModel) {
        val tokens = line.split("\\s+".toRegex())
        if (tokens.size >= 2) {
            val springName = tokens[0].uppercase()
            val ksLine = tokens[1].toDoubleOrNull() ?: (model.geo.subgradeReactionKsKnM3 * 1.60)
            // Enregistrement de la raideur de ressort
        }
    }

    private fun parseBeamProperty(line: String, model: StructuralJointModel) {
        val tokens = line.split("\\s+".toRegex())
        if (tokens.size >= 5) {
            val beamType = tokens[0].uppercase() // SF120, SF160, SFJ200
            val b = tokens[1].toDoubleOrNull() ?: 1.60
            val b0 = tokens[2].toDoubleOrNull() ?: 0.50
            val h = tokens[3].toDoubleOrNull() ?: 0.90
            val h0 = tokens[4].toDoubleOrNull() ?: 0.40
            val isJoint = beamType.contains("SFJ")

            val springKLine = model.geo.subgradeReactionKsKnM3 * b
            model.foundations.add(
                FoundationStrip(
                    lineId = "Line_$beamType",
                    typeName = beamType,
                    isJoint = isJoint,
                    widthBMeters = b,
                    webWidthB0Meters = b0,
                    totalHeightHMeters = h,
                    slabHeightH0Meters = h0,
                    springKLineKnM = springKLine
                )
            )
        }
    }
}
