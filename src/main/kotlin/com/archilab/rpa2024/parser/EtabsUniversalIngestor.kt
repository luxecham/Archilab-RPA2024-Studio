package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.SeismicConfig
import java.nio.file.Path

/**
 * Point d'entree unique pour l'import d'un joint. Le format est deduit de l'extension :
 * .e2k -> lecteur texte ETABS, .xlsx/.xls -> POI, .mdb/.accdb -> Jackcess,
 * .txt -> e2k ou export tabulaire selon le contenu.
 */
class EtabsUniversalIngestor(
    private val topologyBuilder: TopologyBuilder = TopologyBuilder(),
    private val e2kParser: E2kModelParser = E2kModelParser(),
    private val tabularBuilder: TabularModelBuilder = TabularModelBuilder(topologyBuilder),
    private val resultsReader: EtabsResultsReader = EtabsResultsReader()
) {

    fun importJoint(
        modelFile: Path,
        jointName: String,
        seismicConfig: SeismicConfig,
        resultsFile: Path? = null
    ): EtabsImport {
        val model = parseModel(modelFile, jointName, seismicConfig)
        val results = resultsFile?.let { readResults(it) } ?: EtabsResults()
        val warnings = (model.warnings + results.warnings).distinct()
        return EtabsImport(model, results, warnings)
    }

    fun parseModel(file: Path, jointName: String, seismicConfig: SeismicConfig): ParsedEtabsModel {
        val extension = extensionOf(file)
        return when {
            extension == "e2k" -> {
                val raw = e2kParser.parse(file)
                topologyBuilder.build(jointName, raw, seismicConfig)
            }
            extension in TABULAR_EXTENSIONS -> {
                val tables = TabularSource.read(file)
                tabularBuilder.build(tables, jointName, seismicConfig)
            }
            extension == "txt" -> parseTextModel(file, jointName, seismicConfig)
            else -> throw EtabsParseException(
                "Extension '.$extension' non prise en charge. Formats acceptés : .e2k, .xlsx, .xls, .mdb, .accdb, .txt."
            )
        }
    }

    fun parseRaw(file: Path): RawGeometry {
        val extension = extensionOf(file)
        return when {
            extension == "e2k" -> e2kParser.parse(file)
            extension in TABULAR_EXTENSIONS -> tabularBuilder.buildRaw(TabularSource.read(file))
            extension == "txt" -> {
                val text = E2kModelParser.readText(file)
                if (looksLikeE2k(text)) e2kParser.parseText(text, file.fileName.toString())
                else tabularBuilder.buildRaw(TabularSource.read(file))
            }
            else -> throw EtabsParseException(
                "Extension '.$extension' non prise en charge. Formats acceptés : .e2k, .xlsx, .xls, .mdb, .accdb, .txt."
            )
        }
    }

    fun readResults(file: Path): EtabsResults {
        val extension = extensionOf(file)
        val tables = when {
            extension == "e2k" -> throw EtabsParseException(
                "Un fichier .e2k ne contient pas les tableaux de résultats : fournir un export .xlsx, .mdb ou .txt."
            )
            extension == "txt" -> {
                val text = E2kModelParser.readText(file)
                if (looksLikeE2k(text)) {
                    throw EtabsParseException(
                        "Le fichier .txt est un modèle e2k : les résultats doivent être exportés séparément."
                    )
                }
                TabularSource.read(file)
            }
            else -> TabularSource.read(file)
        }
        return resultsReader.read(tables)
    }

    private fun parseTextModel(file: Path, jointName: String, seismicConfig: SeismicConfig): ParsedEtabsModel {
        val text = E2kModelParser.readText(file)
        return if (looksLikeE2k(text)) {
            topologyBuilder.build(jointName, e2kParser.parseText(text, file.fileName.toString()), seismicConfig)
        } else {
            tabularBuilder.build(TabularSource.read(file), jointName, seismicConfig)
        }
    }

    /**
     * Un export e2k contient des tableaux nommés entre guillemets et des affectations
     * `Clé = valeur`. Un export tabulaire ETABS contient aussi `TABLE:` mais ses lignes
     * sont délimitées par des tabulations : il ne doit jamais être routé vers l'e2k.
     */
    private fun looksLikeE2k(text: String): Boolean {
        val marker = Regex("(?im)^\\s*\\$\\s*(STORIES|POINT COORDINATES|LINE CONNECTIVITIES)")
        if (marker.containsMatchIn(text)) return true
        val quotedTable = Regex("(?im)^\\s*TABLE\\s*:\\s*\"[^\"]+\"\\s*$").containsMatchIn(text)
        val assignment = Regex("(?im)^\\s*[A-Za-z_][A-Za-z0-9_]*\\s*=").containsMatchIn(text)
        return quotedTable && assignment
    }

    private fun extensionOf(file: Path): String =
        file.fileName.toString().substringAfterLast('.', "").lowercase()

    private companion object {
        private val TABULAR_EXTENSIONS = setOf("xlsx", "xls", "xlsm", "mdb", "accdb")
    }
}

/** Resultat complet d'un import : topologie reconstruite et tableaux de resultats associes. */
data class EtabsImport(
    val model: ParsedEtabsModel,
    val results: EtabsResults,
    val warnings: List<String>
)
