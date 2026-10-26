package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max

/**
 * Parseur de classeurs Excel (.xlsx / .xls) contenant les tables de résultats ETABS / SAFE.
 * Basé sur Apache POI.
 */
class ExcelResultParser {

    private val formatter = DataFormatter()

    fun parse(file: File): EtabsAnalysisResults {
        return file.inputStream().use { parse(it) }
    }

    fun parse(inputStream: InputStream): EtabsAnalysisResults {
        val workbook = WorkbookFactory.create(inputStream)

        val modalRatios = mutableListOf<ModalRatioRow>()
        var vxMds = 0.0
        var vyMds = 0.0
        val storyShears = mutableListOf<StoryShearRow>()
        val diaphragmDisplacements = mutableListOf<DiaphragmDisplacementRow>()
        val columnEnvelopes = mutableMapOf<String, ColumnForcesEnvelope>()
        val beamEnvelopes = mutableMapOf<String, BeamForcesEnvelope>()
        val pierEnvelopes = mutableMapOf<String, PierForcesEnvelope>()
        var totalW = 0.0

        for (sheetIndex in 0 until workbook.numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            val sheetName = sheet.sheetName.lowercase()

            when {
                sheetName.contains("modal") || sheetName.contains("participat") -> {
                    modalRatios.addAll(parseModalSheet(sheet))
                }
                sheetName.contains("base") && sheetName.contains("react") -> {
                    val (vx, vy) = parseBaseReactionsSheet(sheet)
                    vxMds = max(vxMds, vx)
                    vyMds = max(vyMds, vy)
                }
                sheetName.contains("story") && sheetName.contains("shear") -> {
                    val shears = parseStoryShearsSheet(sheet)
                    storyShears.addAll(shears)
                    shears.filter { it.loadCombo.contains("WI", ignoreCase = true) && it.loc.equals("Bottom", ignoreCase = true) }
                        .forEach { totalW = max(totalW, it.pKN) }
                }
                sheetName.contains("diaphragm") || sheetName.contains("displacement") -> {
                    diaphragmDisplacements.addAll(parseDiaphragmSheet(sheet))
                }
                sheetName.contains("column") && sheetName.contains("force") -> {
                    val cols = parseColumnForcesSheet(sheet)
                    columnEnvelopes.putAll(cols)
                }
                sheetName.contains("beam") && sheetName.contains("force") -> {
                    val bms = parseBeamForcesSheet(sheet)
                    beamEnvelopes.putAll(bms)
                }
                sheetName.contains("pier") && sheetName.contains("force") -> {
                    val piers = parsePierForcesSheet(sheet)
                    pierEnvelopes.putAll(piers)
                }
            }
        }

        workbook.close()

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

    private fun extractTableRows(sheet: Sheet): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        if (sheet.physicalNumberOfRows < 2) return rows

        val headerRow = sheet.getRow(sheet.firstRowNum) ?: return rows
        val headers = mutableListOf<String>()
        for (c in 0 until headerRow.lastCellNum) {
            val cell = headerRow.getCell(c)
            headers.add(cell?.let { formatter.formatCellValue(it).trim() } ?: "Col_$c")
        }

        for (r in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            val rowMap = mutableMapOf<String, String>()
            for (c in 0 until headers.size) {
                val cell = row.getCell(c)
                val text = cell?.let { formatter.formatCellValue(it).trim() } ?: ""
                rowMap[headers[c]] = text
            }
            if (rowMap.values.any { it.isNotEmpty() }) {
                rows.add(rowMap)
            }
        }
        return rows
    }

    private fun parseModalSheet(sheet: Sheet): List<ModalRatioRow> {
        val rows = extractTableRows(sheet)
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

    private fun parseBaseReactionsSheet(sheet: Sheet): Pair<Double, Double> {
        val rows = extractTableRows(sheet)
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

    private fun parseStoryShearsSheet(sheet: Sheet): List<StoryShearRow> {
        val rows = extractTableRows(sheet)
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

    private fun parseDiaphragmSheet(sheet: Sheet): List<DiaphragmDisplacementRow> {
        val rows = extractTableRows(sheet)
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

    private fun parseColumnForcesSheet(sheet: Sheet): Map<String, ColumnForcesEnvelope> {
        val rows = extractTableRows(sheet)
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

    private fun parseBeamForcesSheet(sheet: Sheet): Map<String, BeamForcesEnvelope> {
        val rows = extractTableRows(sheet)
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

    private fun parsePierForcesSheet(sheet: Sheet): Map<String, PierForcesEnvelope> {
        val rows = extractTableRows(sheet)
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
