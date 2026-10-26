package com.archilab.rpa2024.parser

import com.archilab.rpa2024.model.*
import java.io.File

/**
 * Extracteur et ingestor universel pour modèles et résultats ETABS (v9.7.4 à v21) et SAFE (v12+).
 * Point d'entrée unique pour l'ingestion géométrique et des sollicitations internes.
 */
class EtabsUniversalIngestor(
    private val e2kParser: E2kModelParser = E2kModelParser(),
    private val textResultParser: TextTableResultParser = TextTableResultParser(),
    private val excelResultParser: ExcelResultParser = ExcelResultParser(),
    private val mdbResultParser: AccessMdbResultParser = AccessMdbResultParser()
) {

    /**
     * Parse un modèle texte ETABS (.e2k / .$et) et reconstruit automatiquement
     * toute la topologie géométrique du Joint.
     */
    fun parseModelE2k(content: String, jointName: String = "Joint A"): StructuralJoint {
        return e2kParser.parse(content, jointName)
    }

    /**
     * Parse un fichier modèle texte .e2k depuis le système de fichiers.
     */
    fun parseModelE2k(file: File, jointName: String = "Joint A"): StructuralJoint {
        require(file.exists()) { "Le fichier modèle ETABS n'existe pas : ${file.absolutePath}" }
        val content = file.readText()
        return parseModelE2k(content, jointName)
    }

    /**
     * Parse les tables de résultats au format texte (.txt / rapport ETABS).
     */
    fun parseResultsFromText(tablesText: String): EtabsAnalysisResults {
        return textResultParser.parseTables(tablesText)
    }

    /**
     * Parse les tables de résultats depuis un classeur Excel (.xlsx / .xls).
     */
    fun parseResultsFromExcel(file: File): EtabsAnalysisResults {
        require(file.exists()) { "Le fichier Excel de résultats n'existe pas : ${file.absolutePath}" }
        return excelResultParser.parse(file)
    }

    /**
     * Parse les tables de résultats depuis une base de données Access (.mdb).
     */
    fun parseResultsFromMdb(file: File): EtabsAnalysisResults {
        require(file.exists()) { "Le fichier Access .mdb de résultats n'existe pas : ${file.absolutePath}" }
        return mdbResultParser.parse(file)
    }

    /**
     * Auto-découverte et ingestion complète d'un répertoire de projet ETABS.
     * Recherche automatiquement le modèle (.e2k) et les fichiers de résultats (.xlsx, .mdb, .txt).
     */
    fun autoIngestJointDirectory(
        directory: File,
        jointName: String = "Joint A"
    ): Pair<StructuralJoint, EtabsAnalysisResults> {
        require(directory.isDirectory) { "Le chemin spécifié n'est pas un répertoire valide : ${directory.absolutePath}" }

        val e2kFile = directory.listFiles { _, name -> name.endsWith(".e2k", ignoreCase = true) || name.endsWith(".\$et", ignoreCase = true) }?.firstOrNull()
            ?: throw IllegalArgumentException("Aucun fichier modèle ETABS (.e2k) trouvé dans ${directory.absolutePath}")

        val joint = parseModelE2k(e2kFile, jointName)

        val xlsxFile = directory.listFiles { _, name -> name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".xls", ignoreCase = true) }?.firstOrNull()
        val mdbFile = directory.listFiles { _, name -> name.endsWith(".mdb", ignoreCase = true) }?.firstOrNull()
        val txtFile = directory.listFiles { _, name -> name.endsWith(".txt", ignoreCase = true) || name.endsWith(".out", ignoreCase = true) }?.firstOrNull()

        val results = when {
            xlsxFile != null -> parseResultsFromExcel(xlsxFile)
            mdbFile != null -> parseResultsFromMdb(mdbFile)
            txtFile != null -> parseResultsFromText(txtFile.readText())
            else -> EtabsAnalysisResults()
        }

        return Pair(joint, results)
    }

    /**
     * Crée programmatiquement un StructuralJoint à partir de composants topologiques découverts.
     */
    fun createJointFromTopology(
        jointName: String,
        gridSystem: GridSystem,
        storySystem: StorySystem,
        sectionCatalog: SectionCatalog,
        materials: MaterialConfig = MaterialConfig(),
        seismicParams: SeismicConfig = SeismicConfig()
    ): StructuralJoint {
        return StructuralJoint(
            jointName = jointName,
            gridSystem = gridSystem,
            storySystem = storySystem,
            sectionCatalog = sectionCatalog,
            materials = materials,
            seismicParams = seismicParams
        )
    }
}
