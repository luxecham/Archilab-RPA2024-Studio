package com.archilab.rpa2024.expert

import com.archilab.rpa2024.engine.foundations.FoundationGeotechnicalParameters
import com.archilab.rpa2024.engine.foundations.SafeFoundationExtractor
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.parser.EtabsImport
import com.archilab.rpa2024.parser.EtabsParseException
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Source physique ayant servi à construire un modèle croisé. */
data class CrossModelSource(
    val role: String,
    val file: Path?,
    val sizeBytes: Long? = null,
    val modifiedAt: Instant? = null
) {
    val available: Boolean get() = file != null && file.toFile().isFile
    val displayName: String get() = file?.fileName?.toString() ?: "non fourni"
}

/** Entrée explicite du moteur d'ingestion croisée. */
data class CrossModelRequest(
    val etabsModel: Path,
    val jointName: String,
    val seismicConfig: SeismicConfig,
    val etabsResults: Path? = null,
    val safeModel: Path? = null,
    val geotechnical: FoundationGeotechnicalParameters = FoundationGeotechnicalParameters(),
    val safeUnitsOverride: com.archilab.rpa2024.parser.UnitSystem? = null,
    val manualSeismicWeightKN: Double? = null
)

/**
 * Snapshot homogène du modèle de superstructure ETABS et du modèle de fondations SAFE.
 * Le snapshot est immuable : les calculs experts consomment toujours la même version
 * des données et les sources sont tracées.
 */
data class CrossModelSnapshot(
    val etabs: EtabsImport,
    val foundations: SafeFoundationModel?,
    val seismicConfig: SeismicConfig,
    val geotechnical: FoundationGeotechnicalParameters,
    val sources: List<CrossModelSource>,
    val warnings: List<String>,
    val manualSeismicWeightKN: Double? = null,
    val importedAt: Instant = Instant.now()
) {
    val model get() = etabs.model
    val results get() = etabs.results
    val isCrossModel: Boolean get() = foundations != null
    val jointName get() = model.joint.jointName
    val sourceSummary: String get() = sources.joinToString(" | ") { "${it.role}: ${it.displayName}" }
}

/**
 * Point d'entrée unique de l'ingestion croisée ETABS + SAFE.
 *
 * Aucune géométrie, aucun nom de niveau et aucun effort ne sont codés en dur :
 * la superstructure est lue par `EtabsUniversalIngestor` (e2k, Excel, Access, texte)
 * et les fondations par `SafeFoundationExtractor`.
 */
class CrossModelIngestor(
    private val etabsIngestor: EtabsUniversalIngestor = EtabsUniversalIngestor(),
    private val safeExtractor: SafeFoundationExtractor = SafeFoundationExtractor()
) {

    fun ingest(request: CrossModelRequest): CrossModelSnapshot {
        require(request.jointName.isNotBlank()) { "Le nom du joint est obligatoire." }
        validateFile(request.etabsModel, "ETABS")
        request.etabsResults?.let { validateFile(it, "résultats ETABS") }
        request.safeModel?.let { validateFile(it, "SAFE") }

        val etabs = etabsIngestor.importJoint(
            modelFile = request.etabsModel,
            jointName = request.jointName,
            seismicConfig = request.seismicConfig,
            resultsFile = request.etabsResults
        )
        val foundations = request.safeModel?.let { path ->
            safeExtractor.read(
                path = path,
                geotechnical = request.geotechnical,
                units = request.safeUnitsOverride
            )
        }
        val warnings = (etabs.warnings + foundations?.warnings.orEmpty()).distinct()
        val sources = listOf(
            source("ETABS modèle", request.etabsModel),
            source("ETABS résultats", request.etabsResults),
            source("SAFE fondations", request.safeModel)
        )
        return CrossModelSnapshot(
            etabs = etabs,
            foundations = foundations,
            seismicConfig = request.seismicConfig,
            geotechnical = foundations?.geotechnical ?: request.geotechnical,
            sources = sources,
            warnings = warnings,
            manualSeismicWeightKN = request.manualSeismicWeightKN
        )
    }

    /** Relie deux modèles déjà importés, utile pour les tests et l'interface Compose. */
    fun link(
        etabs: EtabsImport,
        foundations: SafeFoundationModel?,
        seismicConfig: SeismicConfig,
        geotechnical: FoundationGeotechnicalParameters = FoundationGeotechnicalParameters(),
        sources: List<CrossModelSource> = emptyList(),
        manualSeismicWeightKN: Double? = null
    ): CrossModelSnapshot = CrossModelSnapshot(
        etabs = etabs,
        foundations = foundations,
        seismicConfig = seismicConfig,
        geotechnical = foundations?.geotechnical ?: geotechnical,
        sources = sources,
        warnings = (etabs.warnings + foundations?.warnings.orEmpty()).distinct(),
        manualSeismicWeightKN = manualSeismicWeightKN
    )

    private fun validateFile(path: Path, role: String) {
        if (!Files.isRegularFile(path)) {
            throw EtabsParseException("Fichier $role introuvable : $path")
        }
    }

    private fun source(role: String, path: Path?): CrossModelSource {
        if (path == null) return CrossModelSource(role, null)
        return CrossModelSource(
            role = role,
            file = path,
            sizeBytes = Files.size(path),
            modifiedAt = Files.getLastModifiedTime(path).toInstant()
        )
    }
}
