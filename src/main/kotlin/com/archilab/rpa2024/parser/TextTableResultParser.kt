package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Parseur de résultats d'analyse au format texte (rapports tabulaires ETABS .txt).
 */
class TextTableResultParser {

    fun parseTables(text: String): EtabsAnalysisResults {
        val modalRatios = mutableListOf<ModalRatioRow>()
        var vxMds = 0.0
        var vyMds = 0.0
        val storyShears = mutableListOf<StoryShearRow>()
        val diaphragmDisplacements = mutableListOf<DiaphragmDisplacementRow>()
        val columnEnvelopes = mutableMapOf<String, ColumnForcesEnvelope>()
        val beamEnvelopes = mutableMapOf<String, BeamForcesEnvelope>()
        val pierEnvelopes = mutableMapOf<String, PierForcesEnvelope>()
        var totalW = 0.0

        val lines = text.lines()
        var currentTable = ""
        var headers = listOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("---") || trimmed.startsWith("===")) continue

            // Détection du début d'un tableau
            if (trimmed.contains("TABLE:", ignoreCase = true) ||
                trimmed.contains("Modal Participating Mass Ratios", ignoreCase = true) ||
                trimmed.contains("Response Spectrum Base Reactions", ignoreCase = true) ||
                trimmed.contains("Story Shears", ignoreCase = true) ||
                trimmed.contains("Diaphragm CM Displacements", ignoreCase = true) ||
                trimmed.contains("Column Forces", ignoreCase = true) ||
                trimmed.contains("Beam Forces", ignoreCase = true) ||
                trimmed.contains("Pier Forces", ignoreCase = true)
            ) {
                currentTable = trimmed.uppercase()
                headers = emptyList()
                continue
            }

            // Détection des en-têtes
            if (headers.isEmpty() && (trimmed.contains("Mode", ignoreCase = true) ||
                        trimmed.contains("Story", ignoreCase = true) ||
                        trimmed.contains("Spec", ignoreCase = true) ||
                        trimmed.contains("Column", ignoreCase = true) ||
                        trimmed.contains("Beam", ignoreCase = true) ||
                        trimmed.contains("Pier", ignoreCase = true))
            ) {
                headers = splitRow(trimmed).map { it.trim() }
                continue
            }

            if (headers.isNotEmpty()) {
                val tokens = splitRow(trimmed)
                if (tokens.size >= headers.size - 2) {
                    val rowMap = headers.zip(tokens).toMap()

                    when {
                        currentTable.contains("MODAL") -> {
                            val mode = ColumnAliasResolver.getInt(rowMap, "mode")
                            val period = ColumnAliasResolver.getDouble(rowMap, "period")
                            val ux = ColumnAliasResolver.getDouble(rowMap, "ux")
                            val uy = ColumnAliasResolver.getDouble(rowMap, "uy")
                            val uz = ColumnAliasResolver.getDouble(rowMap, "uz")
                            val sumUx = ColumnAliasResolver.getDouble(rowMap, "sumux")
                            val sumUy = ColumnAliasResolver.getDouble(rowMap, "sumuy")
                            val sumUz = ColumnAliasResolver.getDouble(rowMap, "sumuz")
                            val rx = ColumnAliasResolver.getDouble(rowMap, "rx")
                            val ry = ColumnAliasResolver.getDouble(rowMap, "ry")
                            val rz = ColumnAliasResolver.getDouble(rowMap, "rz")

                            if (mode > 0) {
                                modalRatios.add(
                                    ModalRatioRow(
                                        mode = mode,
                                        period = period,
                                        ux = ux,
                                        uy = uy,
                                        uz = uz,
                                        sumUx = sumUx,
                                        sumUy = sumUy,
                                        sumUz = sumUz,
                                        rx = rx,
                                        ry = ry,
                                        rz = rz
                                    )
                                )
                            }
                        }

                        currentTable.contains("BASE REACTIONS") -> {
                            val fx = ColumnAliasResolver.getDouble(rowMap, "vx")
                            val fy = ColumnAliasResolver.getDouble(rowMap, "vy")
                            val load = ColumnAliasResolver.getValue(rowMap, "load") ?: ""
                            val isXSeismic = load.contains("EX", ignoreCase = true) ||
                                    load.contains("SPEC_X", ignoreCase = true) ||
                                    load.contains("SPECX", ignoreCase = true) ||
                                    load.contains("RSA_X", ignoreCase = true) ||
                                    load.contains("RSX", ignoreCase = true) ||
                                    load.contains("SX", ignoreCase = true)

                            val isYSeismic = load.contains("EY", ignoreCase = true) ||
                                    load.contains("SPEC_Y", ignoreCase = true) ||
                                    load.contains("SPECY", ignoreCase = true) ||
                                    load.contains("RSA_Y", ignoreCase = true) ||
                                    load.contains("RSY", ignoreCase = true) ||
                                    load.contains("SY", ignoreCase = true)

                            if (isXSeismic) {
                                vxMds = max(vxMds, abs(fx))
                            }
                            if (isYSeismic) {
                                vyMds = max(vyMds, abs(fy))
                            }
                            // Si aucun nom standard explicite mais qu'il s'agit d'une table spectrale dédiée
                            if (!isXSeismic && !isYSeismic && currentTable.contains("SPECTRUM")) {
                                if (abs(fx) > abs(fy)) vxMds = max(vxMds, abs(fx))
                                else vyMds = max(vyMds, abs(fy))
                            }
                        }

                        currentTable.contains("STORY SHEARS") -> {
                            val story = ColumnAliasResolver.getValue(rowMap, "story") ?: ""
                            val load = ColumnAliasResolver.getValue(rowMap, "load") ?: ""
                            val loc = ColumnAliasResolver.getValue(rowMap, "loc") ?: "Bottom"
                            val p = ColumnAliasResolver.getDouble(rowMap, "p")
                            val vx = ColumnAliasResolver.getDouble(rowMap, "vx")
                            val vy = ColumnAliasResolver.getDouble(rowMap, "vy")

                            if (story.isNotEmpty()) {
                                storyShears.add(
                                    StoryShearRow(
                                        story = story,
                                        loadCombo = load,
                                        loc = loc,
                                        pKN = p,
                                        vxKN = vx,
                                        vyKN = vy
                                    )
                                )
                                if (load.contains("WI", ignoreCase = true) && loc.equals("Bottom", ignoreCase = true)) {
                                    totalW = max(totalW, p)
                                }
                            }
                        }

                        currentTable.contains("DIAPHRAGM") -> {
                            val story = ColumnAliasResolver.getValue(rowMap, "story") ?: ""
                            val dia = ColumnAliasResolver.getValue(rowMap, "diaphragm") ?: ""
                            val load = ColumnAliasResolver.getValue(rowMap, "load") ?: ""
                            val ux = ColumnAliasResolver.getDouble(rowMap, "ux")
                            val uy = ColumnAliasResolver.getDouble(rowMap, "uy")

                            if (story.isNotEmpty()) {
                                diaphragmDisplacements.add(
                                    DiaphragmDisplacementRow(
                                        story = story,
                                        diaphragm = dia,
                                        loadCombo = load,
                                        ux = ux,
                                        uy = uy
                                    )
                                )
                            }
                        }

                        currentTable.contains("COLUMN FORCES") -> {
                            val col = ColumnAliasResolver.getValue(rowMap, "column") ?: ""
                            val story = ColumnAliasResolver.getValue(rowMap, "story") ?: ""
                            val load = ColumnAliasResolver.getValue(rowMap, "load") ?: ""
                            val p = ColumnAliasResolver.getDouble(rowMap, "p")
                            val v2 = ColumnAliasResolver.getDouble(rowMap, "v2")
                            val v3 = ColumnAliasResolver.getDouble(rowMap, "v3")
                            val m2 = ColumnAliasResolver.getDouble(rowMap, "m2")
                            val m3 = ColumnAliasResolver.getDouble(rowMap, "m3")

                            val existing = columnEnvelopes[col]
                            val newMaxComp = max(existing?.maxCompressionNdKN ?: 0.0, abs(p))
                            columnEnvelopes[col] = ColumnForcesEnvelope(
                                columnFamilyName = col,
                                maxCompressionNdKN = newMaxComp,
                                maxCompressionStory = story,
                                maxCompressionColumnId = col,
                                maxCompressionCombo = load,
                                eluTorseur = if (load.contains("ELU", ignoreCase = true)) ForceTorseur(p, m2, m3) else existing?.eluTorseur ?: ForceTorseur(p, m2, m3),
                                elsTorseur = if (load.contains("ELS", ignoreCase = true)) ForceTorseur(p, m2, m3) else existing?.elsTorseur ?: ForceTorseur(p, m2, m3),
                                elaTorseur = if (load.contains("COMB", ignoreCase = true) || load.contains("ACC", ignoreCase = true)) ForceTorseur(p, m2, m3) else existing?.elaTorseur ?: ForceTorseur(p, m2, m3),
                                v2MaxKN = max(existing?.v2MaxKN ?: 0.0, abs(v2)),
                                v3MaxKN = max(existing?.v3MaxKN ?: 0.0, abs(v3))
                            )
                        }

                        currentTable.contains("BEAM FORCES") -> {
                            val bm = ColumnAliasResolver.getValue(rowMap, "beam") ?: ""
                            val m3 = ColumnAliasResolver.getDouble(rowMap, "m3")
                            val v2 = ColumnAliasResolver.getDouble(rowMap, "v2")
                            val loc = ColumnAliasResolver.getDouble(rowMap, "loc")

                            val isSupport = loc == 0.0 || loc > 3.0
                            val existing = beamEnvelopes[bm]

                            val newMSup = if (isSupport) max(existing?.mSupportMaxKNm ?: 0.0, abs(m3)) else existing?.mSupportMaxKNm ?: 0.0
                            val newMSpan = if (!isSupport) max(existing?.mSpanMaxKNm ?: 0.0, abs(m3)) else existing?.mSpanMaxKNm ?: 0.0
                            val newV2 = max(existing?.vuMaxKN ?: 0.0, abs(v2))

                            beamEnvelopes[bm] = BeamForcesEnvelope(
                                beamFamilyName = bm,
                                mSpanMaxKNm = newMSpan,
                                mSupportMaxKNm = newMSup,
                                vuMaxKN = newV2
                            )
                        }

                        currentTable.contains("PIER FORCES") -> {
                            val pier = ColumnAliasResolver.getValue(rowMap, "pier") ?: ""
                            val story = ColumnAliasResolver.getValue(rowMap, "story") ?: ""
                            val p = ColumnAliasResolver.getDouble(rowMap, "p")
                            val v2 = ColumnAliasResolver.getDouble(rowMap, "v2")
                            val m3 = ColumnAliasResolver.getDouble(rowMap, "m3")

                            val existing = pierEnvelopes[pier]
                            val newNd = max(existing?.ndMaxBaseKN ?: 0.0, abs(p))
                            val newV2 = max(existing?.v2MaxBaseKN ?: 0.0, abs(v2))
                            val list = (existing?.verticalProfile ?: emptyList()).toMutableList()
                            list.add(PierLevelForce(story, 0.0, abs(p), abs(m3), abs(v2)))

                            pierEnvelopes[pier] = PierForcesEnvelope(
                                pierName = pier,
                                ndMaxBaseKN = newNd,
                                v2MaxBaseKN = newV2,
                                verticalProfile = list
                            )
                        }
                    }
                }
            }
        }

        return EtabsAnalysisResults(
            modalRatios = modalRatios,
            baseReactions = BaseReactionsResult(vxMds, vyMds),
            storyShears = storyShears,
            diaphragmDisplacements = diaphragmDisplacements,
            columnEnvelopes = columnEnvelopes,
            beamEnvelopes = beamEnvelopes,
            pierEnvelopes = pierEnvelopes,
            totalWeightWKN = totalW
        )
    }

    private fun splitRow(row: String): List<String> {
        return if (row.contains("\t")) {
            row.split("\t")
        } else if (row.contains(",")) {
            row.split(",")
        } else {
            row.split(Regex("\\s{2,}"))
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
