package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import com.healthmarketscience.jackcess.Database
import com.healthmarketscience.jackcess.DatabaseBuilder
import com.healthmarketscience.jackcess.Table
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Parseur de base de données Microsoft Access (.mdb) exportée par ETABS / SAFE.
 * Basé sur la bibliothèque Jackcess.
 */
class AccessMdbResultParser {

    fun parse(file: File): EtabsAnalysisResults {
        val db: Database = DatabaseBuilder.open(file)

        val modalRatios = mutableListOf<ModalRatioRow>()
        var vxMds = 0.0
        var vyMds = 0.0
        val storyShears = mutableListOf<StoryShearRow>()
        val diaphragmDisplacements = mutableListOf<DiaphragmDisplacementRow>()
        val columnEnvelopes = mutableMapOf<String, ColumnForcesEnvelope>()
        val beamEnvelopes = mutableMapOf<String, BeamForcesEnvelope>()
        val pierEnvelopes = mutableMapOf<String, PierForcesEnvelope>()
        var totalW = 0.0

        db.use { database ->
            for (tableName in database.tableNames) {
                val table = database.getTable(tableName) ?: continue
                val nameLower = tableName.lowercase()

                when {
                    nameLower.contains("modal") || nameLower.contains("participat") -> {
                        modalRatios.addAll(parseModalTable(table))
                    }
                    nameLower.contains("base") && nameLower.contains("react") -> {
                        val (vx, vy) = parseBaseReactionsTable(table)
                        vxMds = max(vxMds, vx)
                        vyMds = max(vyMds, vy)
                    }
                    nameLower.contains("story") && nameLower.contains("shear") -> {
                        val shears = parseStoryShearsTable(table)
                        storyShears.addAll(shears)
                        shears.filter { it.loadCombo.contains("WI", ignoreCase = true) && it.loc.equals("Bottom", ignoreCase = true) }
                            .forEach { totalW = max(totalW, it.pKN) }
                    }
                    nameLower.contains("diaphragm") || nameLower.contains("displacement") -> {
                        diaphragmDisplacements.addAll(parseDiaphragmTable(table))
                    }
                    nameLower.contains("column") && nameLower.contains("force") -> {
                        columnEnvelopes.putAll(parseColumnForcesTable(table))
                    }
                    nameLower.contains("beam") && nameLower.contains("force") -> {
                        beamEnvelopes.putAll(parseBeamForcesTable(table))
                    }
                    nameLower.contains("pier") && nameLower.contains("force") -> {
                        pierEnvelopes.putAll(parsePierForcesTable(table))
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

    private fun tableToRowMaps(table: Table): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        for (row in table) {
            val map = mutableMapOf<String, String>()
            for (col in table.columns) {
                val value = row[col.name]
                map[col.name] = value?.toString()?.trim() ?: ""
            }
            list.add(map)
        }
        return list
    }

    private fun parseModalTable(table: Table): List<ModalRatioRow> {
        val rows = tableToRowMaps(table)
        return rows.mapNotNull { rowMap ->
            val mode = ColumnAliasResolver.getInt(rowMap, "mode")
            if (mode <= 0) return@mapNotNull null
            ModalRatioRow(
                mode = mode,
                period = ColumnAliasResolver.getDouble(rowMap, "period"),
                ux = ColumnAliasResolver.getDouble(rowMap, "ux"),
                uy = ColumnAliasResolver.getDouble(rowMap, "uy"),
                uz = ColumnAliasResolver.getDouble(rowMap, "uz"),
                sumUx = ColumnAliasResolver.getDouble(rowMap, "sumux"),
                sumUy = ColumnAliasResolver.getDouble(rowMap, "sumuy"),
                sumUz = ColumnAliasResolver.getDouble(rowMap, "sumuz"),
                rx = ColumnAliasResolver.getDouble(rowMap, "rx"),
                ry = ColumnAliasResolver.getDouble(rowMap, "ry"),
                rz = ColumnAliasResolver.getDouble(rowMap, "rz")
            )
        }
    }

    private fun parseBaseReactionsTable(table: Table): Pair<Double, Double> {
        val rows = tableToRowMaps(table)
        var vx = 0.0
        var vy = 0.0
        for (r in rows) {
            val fx = ColumnAliasResolver.getDouble(r, "vx")
            val fy = ColumnAliasResolver.getDouble(r, "vy")
            val load = ColumnAliasResolver.getValue(r, "load") ?: ""
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

            if (isXSeismic) vx = max(vx, abs(fx))
            if (isYSeismic) vy = max(vy, abs(fy))
        }
        return Pair(vx, vy)
    }

    private fun parseStoryShearsTable(table: Table): List<StoryShearRow> {
        val rows = tableToRowMaps(table)
        return rows.mapNotNull { r ->
            val story = ColumnAliasResolver.getValue(r, "story") ?: return@mapNotNull null
            StoryShearRow(
                story = story,
                loadCombo = ColumnAliasResolver.getValue(r, "load") ?: "",
                loc = ColumnAliasResolver.getValue(r, "loc") ?: "Bottom",
                pKN = ColumnAliasResolver.getDouble(r, "p"),
                vxKN = ColumnAliasResolver.getDouble(r, "vx"),
                vyKN = ColumnAliasResolver.getDouble(r, "vy")
            )
        }
    }

    private fun parseDiaphragmTable(table: Table): List<DiaphragmDisplacementRow> {
        val rows = tableToRowMaps(table)
        return rows.mapNotNull { r ->
            val story = ColumnAliasResolver.getValue(r, "story") ?: return@mapNotNull null
            DiaphragmDisplacementRow(
                story = story,
                diaphragm = ColumnAliasResolver.getValue(r, "diaphragm") ?: "D1",
                loadCombo = ColumnAliasResolver.getValue(r, "load") ?: "",
                ux = ColumnAliasResolver.getDouble(r, "ux"),
                uy = ColumnAliasResolver.getDouble(r, "uy")
            )
        }
    }

    private fun parseColumnForcesTable(table: Table): Map<String, ColumnForcesEnvelope> {
        val rows = tableToRowMaps(table)
        val envelopes = mutableMapOf<String, ColumnForcesEnvelope>()
        for (r in rows) {
            val col = ColumnAliasResolver.getValue(r, "column") ?: continue
            val p = ColumnAliasResolver.getDouble(r, "p")
            val v2 = ColumnAliasResolver.getDouble(r, "v2")
            val v3 = ColumnAliasResolver.getDouble(r, "v3")
            val m2 = ColumnAliasResolver.getDouble(r, "m2")
            val m3 = ColumnAliasResolver.getDouble(r, "m3")
            val story = ColumnAliasResolver.getValue(r, "story") ?: ""
            val load = ColumnAliasResolver.getValue(r, "load") ?: ""

            val existing = envelopes[col]
            val maxComp = max(existing?.maxCompressionNdKN ?: 0.0, abs(p))
            envelopes[col] = ColumnForcesEnvelope(
                columnFamilyName = col,
                maxCompressionNdKN = maxComp,
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
        return envelopes
    }

    private fun parseBeamForcesTable(table: Table): Map<String, BeamForcesEnvelope> {
        val rows = tableToRowMaps(table)
        val envelopes = mutableMapOf<String, BeamForcesEnvelope>()
        for (r in rows) {
            val bm = ColumnAliasResolver.getValue(r, "beam") ?: continue
            val m3 = ColumnAliasResolver.getDouble(r, "m3")
            val v2 = ColumnAliasResolver.getDouble(r, "v2")
            val loc = ColumnAliasResolver.getDouble(r, "loc")

            val isSupport = loc == 0.0 || loc > 3.0
            val existing = envelopes[bm]

            val newMSup = if (isSupport) max(existing?.mSupportMaxKNm ?: 0.0, abs(m3)) else existing?.mSupportMaxKNm ?: 0.0
            val newMSpan = if (!isSupport) max(existing?.mSpanMaxKNm ?: 0.0, abs(m3)) else existing?.mSpanMaxKNm ?: 0.0
            val newV2 = max(existing?.vuMaxKN ?: 0.0, abs(v2))

            envelopes[bm] = BeamForcesEnvelope(
                beamFamilyName = bm,
                mSpanMaxKNm = newMSpan,
                mSupportMaxKNm = newMSup,
                vuMaxKN = newV2
            )
        }
        return envelopes
    }

    private fun parsePierForcesTable(table: Table): Map<String, PierForcesEnvelope> {
        val rows = tableToRowMaps(table)
        val envelopes = mutableMapOf<String, PierForcesEnvelope>()
        for (r in rows) {
            val pier = ColumnAliasResolver.getValue(r, "pier") ?: continue
            val story = ColumnAliasResolver.getValue(r, "story") ?: ""
            val p = ColumnAliasResolver.getDouble(r, "p")
            val v2 = ColumnAliasResolver.getDouble(r, "v2")
            val m3 = ColumnAliasResolver.getDouble(r, "m3")

            val existing = envelopes[pier]
            val newNd = max(existing?.ndMaxBaseKN ?: 0.0, abs(p))
            val newV2 = max(existing?.v2MaxBaseKN ?: 0.0, abs(v2))
            val list = (existing?.verticalProfile ?: emptyList()).toMutableList()
            list.add(PierLevelForce(story, 0.0, abs(p), abs(m3), abs(v2)))

            envelopes[pier] = PierForcesEnvelope(
                pierName = pier,
                ndMaxBaseKN = newNd,
                v2MaxBaseKN = newV2,
                verticalProfile = list
            )
        }
        return envelopes
    }
}
