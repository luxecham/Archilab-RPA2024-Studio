package com.archilab.rpa2024.sample

import com.archilab.rpa2024.engine.foundations.FoundationGeotechnicalParameters
import com.archilab.rpa2024.engine.foundations.SafeFoundationExtractor
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.parser.TabularTable
import com.archilab.rpa2024.parser.UnitSystem
import java.nio.file.Path

/**
 * Export SAFE de demonstration du JOINT A (semelles filantes sous voiles et portiques).
 * Les tables reproduisent la structure d'un export SAFE reel et sont relues par
 * [SafeFoundationExtractor] : les familles, ressorts, deplacements et efforts ne sont
 * jamais injectes directement dans le modele metier.
 */
object SampleSafeFoundation {

    const val SOURCE_NAME = "JOINT_A_SAFE.xlsx"

    private val GEOTECHNICAL = FoundationGeotechnicalParameters(
        allowableStressBars = 1.2,
        foundationDepthMeters = 1.5,
        subgradeModulusKNM3 = 21_848.0
    )

    private val XS = doubleArrayOf(0.0, 7.5, 15.0)

    fun load(): SafeFoundationModel = SafeFoundationExtractor().extract(
        tables = tables(),
        geotechnical = GEOTECHNICAL,
        units = UnitSystem.KN_M,
        sourceName = SOURCE_NAME
    )

    fun writeExport(path: Path): Path {
        path.toFile().writeText(tabulatedText(), Charsets.UTF_8)
        return path
    }

    fun tabulatedText(): String = buildString {
        tables().forEach { table ->
            appendLine("TABLE: \"${table.name}\"")
            appendLine(table.headers.joinToString("\t"))
            table.rows.forEach { row -> appendLine(row.joinToString("\t")) }
        }
    }

    private fun tables(): List<TabularTable> = listOf(
        TabularTable("Units", listOf("Units"), listOf(listOf("KN-M"))),
        TabularTable(
            name = "Beam Properties",
            headers = listOf("Name", "Material", "B", "h0", "b0", "h", "d'", "c", "Length"),
            rows = listOf(
                listOf("SF120", "C25/30", "1.20", "0.00", "1.20", "0.50", "0.05", "0.05", "1.00"),
                listOf("SF140", "C25/30", "1.40", "0.30", "0.50", "0.60", "0.05", "0.05", "1.00"),
                listOf("SFJ200", "C25/30", "2.00", "0.35", "0.60", "0.80", "0.05", "0.05", "1.00")
            )
        ),
        TabularTable(
            name = "Line Spring Properties",
            headers = listOf("Name", "Width", "Ks"),
            rows = listOf(
                listOf("K120", "1.20", "21848"),
                listOf("K140", "1.40", "21848"),
                listOf("K200", "2.00", "21848")
            )
        ),
        TabularTable(
            name = "Joint Coordinates",
            headers = listOf("Joint", "X", "Y"),
            rows = XS.flatMapIndexed { index, x ->
                listOf(
                    listOf("JS${index + 1}A", fmt(x), "0.00"),
                    listOf("JS${index + 1}B", fmt(x), "10.30"),
                    listOf("JS${index + 1}C", fmt(x), "20.65")
                )
            }
        ),
        TabularTable(
            name = "Joint Displacements",
            headers = listOf("Joint", "OutputCase", "U3"),
            rows = XS.flatMapIndexed { index, _ ->
                listOf(
                    listOf("JS${index + 1}A", "ELS1", "-0.0062"),
                    listOf("JS${index + 1}B", "ELS1", "-0.0044"),
                    listOf("JS${index + 1}C", "ELS1", "-0.0051")
                )
            }
        ),
        TabularTable(
            name = "Beam Forces",
            headers = listOf("Beam", "Section", "Station", "OutputCase", "P", "V2", "M3"),
            rows = forceRows("SF120", axial = Triple(520.0, 385.0, 430.0), support = 305.0, span = 128.0, shear = 205.0) +
                forceRows("SF140", axial = Triple(610.0, 448.0, 505.0), support = 355.0, span = 152.0, shear = 238.0) +
                forceRows("SFJ200", axial = Triple(930.0, 690.0, 760.0), support = 612.0, span = 268.0, shear = 372.0)
        ),
        TabularTable(
            name = "Material Properties",
            headers = listOf("Name", "Type", "Fc", "Fy"),
            rows = listOf(
                listOf("C25/30", "Concrete", "25", "0"),
                listOf("FeE500", "Rebar", "0", "500")
            )
        )
    )

    /**
     * Trois combinaisons par famille (ELU, ELS, ELA) et trois stations, pour que
     * l'orchestrateur lise une enveloppe identique a celle d'un export SAFE.
     */
    private fun forceRows(
        section: String,
        axial: Triple<Double, Double, Double>,
        support: Double,
        span: Double,
        shear: Double
    ): List<List<String>> {
        val ultimate = listOf(
            listOf(section, section, "0.00", "ELU1", fmt(axial.first), fmt(shear), fmt(-support)),
            listOf(section, section, "7.50", "ELU1", fmt(axial.first), "12.0", fmt(span)),
            listOf(section, section, "15.00", "ELU1", fmt(axial.first), fmt(-shear), fmt(-support))
        )
        val service = listOf(
            listOf(section, section, "0.00", "ELS1", fmt(axial.second), fmt(shear * 0.72), fmt(-support * 0.78)),
            listOf(section, section, "7.50", "ELS1", fmt(axial.second), "8.0", fmt(span * 0.78)),
            listOf(section, section, "15.00", "ELS1", fmt(axial.second), fmt(-shear * 0.72), fmt(-support * 0.78))
        )
        val accidental = listOf(
            listOf(section, section, "0.00", "ELA1", fmt(axial.third), fmt(shear * 1.22), fmt(-support * 1.28)),
            listOf(section, section, "7.50", "ELA1", fmt(axial.third), "0.0", fmt(span * 1.28)),
            listOf(section, section, "15.00", "ELA1", fmt(axial.third), fmt(-shear * 1.22), fmt(-support * 1.28))
        )
        return ultimate + service + accidental
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f", value)
}
