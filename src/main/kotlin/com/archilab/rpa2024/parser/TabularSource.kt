package com.archilab.rpa2024.parser

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.nio.file.Path

/** Table homogene lue depuis Excel, Access ou un export texte ETABS. */
class TabularTable(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>
) {
    val rowCount: Int get() = rows.size

    fun row(index: Int): TabularRow = TabularRow(this, rows[index])

    fun forEachRow(action: (TabularRow) -> Unit) {
        rows.forEachIndexed { index, _ -> action(row(index)) }
    }
}

class TabularRow(private val table: TabularTable, private val values: List<String>) {

    private val resolver: ColumnAliasResolver by lazy { ColumnAliasResolver(table.headers) }

    fun raw(column: String): String? {
        val index = table.headers.indexOfFirst { it.equals(column, ignoreCase = true) }
        return if (index in values.indices) values[index] else null
    }

    fun value(vararg aliases: String): String? {
        for (alias in aliases) {
            val index = resolver.resolve(alias)
            if (index != null) {
                val raw = values.getOrNull(index)?.trim()
                if (!raw.isNullOrBlank() && !raw.equals("N/A", ignoreCase = true)) return raw
            }
        }
        return null
    }

    fun number(vararg aliases: String): Double? =
        value(*aliases)?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull()

    fun percent(vararg aliases: String): Double? {
        val text = value(*aliases) ?: return null
        val numeric = text.replace(" ", "").replace(',', '.').removeSuffix("%").toDoubleOrNull() ?: return null
        return numeric
    }
}

/**
 * Unifie les noms de colonnes entre ETABS v9.7.4 et v18/v21 : les alias sont resolus par
 * correspondance exacte puis par inclusion normalisee (majuscules, sans separateurs).
 */
class ColumnAliasResolver(headers: List<String>) {

    private val exact: Map<String, Int> = headers.mapIndexed { index, header -> header to index }.toMap()
    private val normalized: List<Pair<String, Int>> =
        headers.mapIndexed { index, header -> normalize(header) to index }

    fun resolve(alias: String): Int? {
        exact[alias]?.let { return it }
        exact.entries.firstOrNull { it.key.equals(alias, ignoreCase = true) }?.let { return it.value }
        val target = normalize(alias)
        if (target.isEmpty()) return null
        normalized.firstOrNull { it.first == target }?.let { return it.second }
        normalized.firstOrNull { it.first.startsWith(target) }?.let { return it.second }
        return normalized.firstOrNull { it.first.contains(target) }?.second
    }

    private fun normalize(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9%]"), "")
}

/** Lecture des tables ETABS depuis .xlsx/.xls (POI), .mdb/.accdb (Jackcess) ou texte delimite. */
object TabularSource {

    fun read(file: Path): List<TabularTable> = when (file.fileName.toString().substringAfterLast('.', "").lowercase()) {
        "xlsx", "xls", "xlsm" -> readExcel(file)
        "mdb", "accdb" -> readAccess(file)
        else -> readDelimitedText(file)
    }

    private val UNIT_TOKENS = setOf(
        "KN", "KGF", "TONF", "TON", "N", "LB", "LBF", "KIP", "KIPS",
        "M", "CM", "MM", "FT", "IN", "C", "F", "K", "SEC", "S"
    )

    private fun readExcel(file: Path): List<TabularTable> {
        val formatter = DataFormatter()
        val tables = mutableListOf<TabularTable>()
        WorkbookFactory.create(file.toFile()).use { workbook ->
            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex)
                val grid = mutableListOf<List<String>>()
                for (row in sheet) {
                    val cells = mutableListOf<String>()
                    val lastCell = row.lastCellNum.toInt().coerceAtLeast(0)
                    for (cellIndex in 0 until lastCell) {
                        val cell = row.getCell(cellIndex)
                        cells += if (cell == null) "" else formatter.formatCellValue(cell).trim()
                    }
                    if (cells.any { it.isNotBlank() }) grid += cells
                }
                val table = buildTable(sheet.sheetName, grid)
                if (table != null) tables += table
            }
        }
        return tables
    }

    private fun readAccess(file: Path): List<TabularTable> {
        val tables = mutableListOf<TabularTable>()
        val database = com.healthmarketscience.jackcess.DatabaseBuilder.open(file.toFile())
        try {
            for (table in database) {
                val headers = table.columns.map { it.name }
                if (headers.isEmpty()) continue
                val rows = mutableListOf<List<String>>()
                for (row in table) {
                    rows += headers.map { header ->
                        val value = row.get(header)
                        when (value) {
                            null -> ""
                            is Double -> formatNumber(value)
                            is Float -> formatNumber(value.toDouble())
                            else -> value.toString().trim()
                        }
                    }
                }
                tables += TabularTable(table.name, headers, rows)
            }
        } finally {
            database.close()
        }
        return tables
    }

    private fun readDelimitedText(file: Path): List<TabularTable> {
        val text = E2kModelParser.readText(file)
        val markedTables = readMarkedTables(text)
        if (markedTables.isNotEmpty()) return markedTables
        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    blocks += current
                    current = mutableListOf()
                }
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) blocks += current

        val tables = mutableListOf<TabularTable>()
        blocks.forEachIndexed { index, block ->
            if (block.size < 2) return@forEachIndexed
            val delimiter = detectDelimiter(block)
            val firstCells = splitLine(block.first(), delimiter)
            val secondCells = splitLine(block[1], delimiter)
            val hasTitle = firstCells.size <= 1 && secondCells.size >= 2
            val name = if (hasTitle) firstCells.firstOrNull().orEmpty().ifBlank { "Table ${index + 1}" } else "Table ${index + 1}"
            val headerLine = if (hasTitle) 1 else 0
            val headers = splitLine(block[headerLine], delimiter)
            if (headers.size < 2) return@forEachIndexed
            val rows = block.drop(headerLine + 1).map { splitLine(it, delimiter) }
                .filter { it.any { cell -> cell.isNotBlank() } }
            if (rows.isNotEmpty()) tables += TabularTable(name, headers, rows)
        }
        return tables
    }

    private fun buildTable(name: String, grid: List<List<String>>): TabularTable? {
        if (grid.size < 2) return null
        val headerIndex = grid.indexOfFirst { it.count { cell -> cell.isNotBlank() } >= 2 }
        if (headerIndex < 0) return null
        val header = grid[headerIndex]
        val width = header.size
        val rows = grid.drop(headerIndex + 1)
            .map { row -> List(width) { index -> row.getOrElse(index) { "" } } }
            .filter { row -> row.any { it.isNotBlank() } }
        return if (rows.isEmpty()) null else TabularTable(name, header, rows)
    }

    /**
     * Lit les exports ETABS « Display Tables » qui commencent chaque tableau par
     * `TABLE:  Nom du tableau`, puis éventuellement une ligne d'unités et les en-têtes.
     * Ces fichiers ne doivent pas être confondus avec un modèle e2k.
     */
    private fun readMarkedTables(text: String): List<TabularTable> {
        if (!Regex("(?im)^\\s*TABLE\\s*:").containsMatchIn(text)) return emptyList()
        val lines = text.lines()
        val tables = mutableListOf<TabularTable>()
        var index = 0
        while (index < lines.size) {
            val title = lines[index].trim()
            if (!title.startsWith("TABLE:", ignoreCase = true)) {
                index++
                continue
            }
            val name = title.substringAfter(':').trim().trim('"').ifBlank { "Table ${tables.size + 1}" }
            index++
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break
            val sample = (index until minOf(index + 6, lines.size)).map { lines[it] }
            val delimiter = detectDelimiter(sample)
            while (index < lines.size && isUnitsLine(lines[index], delimiter)) index++
            if (index >= lines.size) break
            val headers = splitLine(lines[index], delimiter)
            if (headers.size < 2) {
                index++
                continue
            }
            index++
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && !lines[index].trim().startsWith("TABLE:", ignoreCase = true)) {
                val line = lines[index].trim()
                if (line.isNotBlank()) {
                    val cells = splitLine(line, delimiter)
                    if (cells.size >= 2) rows += cells
                }
                index++
            }
            if (rows.isNotEmpty()) tables += TabularTable(name, headers, rows)
        }
        return tables
    }

    private fun isUnitsLine(line: String, delimiter: Regex): Boolean {
        val cells = splitLine(line, delimiter).filter { it.isNotBlank() }
        if (cells.isEmpty() || cells.size > 8) return false
        val tokens = cells.flatMap { cell ->
            cell.uppercase().split(Regex("[^A-Z]+")).filter { it.isNotBlank() }
        }
        return tokens.isNotEmpty() && tokens.all { it in UNIT_TOKENS }
    }

    private fun detectDelimiter(block: List<String>): Regex {
        val sample = block.take(4)
        return when {
            sample.any { it.contains('\t') } -> Regex("\\t+")
            sample.count { line -> line.count { char -> char == ';' } >= 2 } >= 2 -> Regex("\\s*;\\s*")
            sample.count { line -> line.count { char -> char == ',' } >= 2 } >= 2 -> Regex("\\s*,\\s*")
            else -> Regex("\\s{2,}")
        }
    }

    private fun splitLine(line: String, delimiter: Regex): List<String> =
        line.trim().split(delimiter).map { it.trim().trim('"') }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
