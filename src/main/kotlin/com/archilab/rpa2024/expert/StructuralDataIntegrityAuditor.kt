package com.archilab.rpa2024.expert

import com.archilab.rpa2024.audit.RegularityAnalyzerRpa2024
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngineOptions
import com.archilab.rpa2024.parser.BaseReactionRow
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.AreaKind
import kotlin.math.abs
import kotlin.math.max

/** Sévérité d'un constat d'intégrité. */
enum class IntegritySeverity {
    INFORMATION,
    WARNING,
    BLOCKING;

    val label: String
        get() = when (this) {
            INFORMATION -> "Information"
            WARNING -> "Avertissement"
            BLOCKING -> "Bloquant"
        }
}

enum class IntegrityVerdict(val label: String) {
    VERIFIABLE("Données vérifiables"),
    VERIFIABLE_WITH_RESERVES("Vérifiable avec réserves"),
    NOT_VERIFIABLE("Non vérifiable en l'état")
}

/** Constat atomique de l'audit de vérifiabilité. */
data class IntegrityFinding(
    val code: String,
    val title: String,
    val severity: IntegritySeverity,
    val reference: String,
    val observation: String,
    val remediation: String = ""
)

/** Rapport complet de vérifiabilité avant dimensionnement. */
data class StructuralIntegrityReport(
    val findings: List<IntegrityFinding>,
    val weightEquilibriumRatio: Double? = null,
    val modalSumUxPercent: Double? = null,
    val modalSumUyPercent: Double? = null,
    val mseMdsRatios: Map<String, Double> = emptyMap(),
    val driftUtilization: Double? = null,
    val pDeltaTheta: Double? = null,
    val softStoryDetected: Boolean? = null,
    val sourceCount: Int = 0,
    val generatedAt: java.time.Instant = java.time.Instant.now()
) {
    val blocking: List<IntegrityFinding> get() = findings.filter { it.severity == IntegritySeverity.BLOCKING }
    val warnings: List<IntegrityFinding> get() = findings.filter { it.severity == IntegritySeverity.WARNING }
    val information: List<IntegrityFinding> get() = findings.filter { it.severity == IntegritySeverity.INFORMATION }

    val verdict: IntegrityVerdict
        get() = when {
            blocking.isNotEmpty() -> IntegrityVerdict.NOT_VERIFIABLE
            warnings.isNotEmpty() -> IntegrityVerdict.VERIFIABLE_WITH_RESERVES
            else -> IntegrityVerdict.VERIFIABLE
        }

    /** Indice de vérifiabilité, purement documentaire : 100 - pénalités. */
    val score: Int
        get() = (100 - blocking.size * 20 - warnings.size * 5).coerceIn(0, 100)
}

/**
 * Auditeur de cohérence physique et réglementaire avant dimensionnement.
 *
 * Les contrôles ne remplacent pas les moteurs de calcul : ils vérifient que les
 * données nécessaires sont présentes, cohérentes et exploitables, puis signalent
 * les anomalies de calage ou de cinématique.
 */
class StructuralDataIntegrityAuditor(
    private val seismicEngine: Rpa2024SeismicEngine = Rpa2024SeismicEngine(),
    private val regularityAnalyzer: RegularityAnalyzerRpa2024 = RegularityAnalyzerRpa2024()
) {

    fun audit(snapshot: CrossModelSnapshot): StructuralIntegrityReport {
        val findings = mutableListOf<IntegrityFinding>()
        val model = snapshot.model
        val results = snapshot.results
        val joint = model.joint

        fun add(
            code: String,
            title: String,
            severity: IntegritySeverity,
            reference: String,
            observation: String,
            remediation: String = ""
        ) {
            findings += IntegrityFinding(code, title, severity, reference, observation, remediation)
        }

        // ---------------------------------------------------------------- géométrie
        if (model.points.isEmpty()) {
            add("GEO-01", "Nœuds absents", IntegritySeverity.BLOCKING, "ETABS", "La table Point Coordinates est vide.")
        }
        if (model.frames.isEmpty()) {
            add("GEO-02", "Barres absentes", IntegritySeverity.BLOCKING, "ETABS", "Aucune connectivité de barres exploitable.")
        }
        if (model.areas.isEmpty()) {
            add("GEO-03", "Surfaces absentes", IntegritySeverity.WARNING, "ETABS",
                "Aucun voile ni plancher n'a été importé ; les vérifications associées seront non applicables.")
        }
        if (joint.storySystem.mainStories.isEmpty()) {
            add("GEO-04", "Niveaux absents", IntegritySeverity.BLOCKING, "ETABS", "Aucun niveau principal n'a pu être reconstruit.")
        }
        val duplicateFrames = model.frames.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateFrames.isNotEmpty()) {
            add("GEO-05", "Identifiants de barres dupliqués", IntegritySeverity.WARNING, "ETABS",
                "${duplicateFrames.size} identifiant(s) dupliqué(s) : ${duplicateFrames.take(8).joinToString()}.")
        }
        val duplicateAreas = model.areas.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateAreas.isNotEmpty()) {
            add("GEO-06", "Identifiants de surfaces dupliqués", IntegritySeverity.WARNING, "ETABS",
                "${duplicateAreas.size} identifiant(s) dupliqué(s) : ${duplicateAreas.take(8).joinToString()}.")
        }
        val zeroSections = model.sections.filter { section ->
            if (section.kind == com.archilab.rpa2024.parser.EtabsSectionKind.FRAME_CIRCULAR) {
                (section.diameterCm ?: 0.0) <= 0.0
            } else if (section.isFrame) {
                (section.widthCm ?: 0.0) <= 0.0 || (section.depthCm ?: 0.0) <= 0.0
            } else {
                (section.thicknessCm ?: 0.0) <= 0.0
            }
        }
        if (zeroSections.isNotEmpty()) {
            add("GEO-07", "Sections à dimension nulle", IntegritySeverity.BLOCKING, "ETABS",
                "Sections invalides : ${zeroSections.map { it.name }.take(8).joinToString()}.")
        }
        if (joint.storySystem.mainStories.any { it.heightHk <= 0.0 }) {
            add("GEO-08", "Hauteur de niveau invalide", IntegritySeverity.BLOCKING, "ETABS",
                "Au moins un niveau possède une hauteur nulle ou négative.")
        }

        // ---------------------------------------------------------------- résultats
        if (results.modalRatios.isEmpty()) {
            add("RES-01", "Participation modale absente", IntegritySeverity.BLOCKING,
                "RPA 2024 § 4.3.3", "La table Modal Participating Mass Ratios est absente.",
                "Réexporter les résultats modaux ETABS avant de composer la note.")
        }
        if (results.storyShears.isEmpty()) {
            add("RES-02", "Efforts tranchants d'étage absents", IntegritySeverity.BLOCKING,
                "RPA 2024 § 4.3.5", "La table Story Shears est absente.",
                "Réexporter Story Shears avec les cas EX/EY et le cas de poids WI.")
        }
        if (results.diaphragmDisplacements.isEmpty()) {
            add("RES-03", "Déplacements de diaphragme absents", IntegritySeverity.WARNING,
                "RPA 2024 § 5.10", "Les déplacements inter-étages ne peuvent pas être vérifiés.",
                "Réexporter Diaphragm CM Displacements.")
        }
        if (results.columnForces.isEmpty()) {
            add("RES-04", "Efforts de poteaux absents", IntegritySeverity.WARNING,
                "RPA 2024 § 7.4.3", "Les efforts normaux des poteaux sont indisponibles.")
        }
        if (results.beamForces.isEmpty()) {
            add("RES-05", "Efforts de poutres absents", IntegritySeverity.WARNING,
                "BAEL 91 / RPA 2024", "Le ferraillage des poutres sera réduit au minimum réglementaire.")
        }

        // ---------------------------------------------------------------- équilibre global
        val seismicWeight = results.seismicWeightKN
        val baseVertical = weightCaseBaseReaction(results)
        val weightRatio = if (seismicWeight != null && seismicWeight > 1e-9 && baseVertical != null) {
            baseVertical / seismicWeight
        } else {
            null
        }
        if (weightRatio == null) {
            add("EQ-01", "Équilibre global non vérifiable", IntegritySeverity.WARNING,
                "ETABS — cas de masse WI", "Poids WI ou réaction verticale de base indisponible.",
                "Exporter les réactions de base ou fournir le poids sismique manuellement.")
        } else {
            val deviation = abs(weightRatio - 1.0)
            val severity = when {
                deviation <= 0.05 -> IntegritySeverity.INFORMATION
                deviation <= 0.10 -> IntegritySeverity.WARNING
                else -> IntegritySeverity.BLOCKING
            }
            add("EQ-01", "Équilibre masse / réaction verticale", severity,
                "ETABS — cas de masse WI",
                "ΣFz,base / W = ${format(weightRatio)} (W = ${format(seismicWeight ?: 0.0)} kN, " +
                    "ΣFz = ${format(baseVertical ?: 0.0)} kN).",
                if (deviation > 0.10) "Vérifier les masses, les cas de charge et les réactions de base." else "")
        }

        // ---------------------------------------------------------------- participation modale
        val modal = seismicEngine.evaluateModalParticipation(
            results.modalRatios,
            joint.storySystem.numStoriesN
        )
        add(
            "MOD-01",
            "Complétude modale",
            when (modal.status) {
                CheckStatus.OK -> IntegritySeverity.INFORMATION
                CheckStatus.FAILED -> IntegritySeverity.BLOCKING
                else -> IntegritySeverity.WARNING
            },
            "RPA 2024 § 4.3.3",
            modal.message,
            if (modal.status == CheckStatus.FAILED) "Augmenter le nombre de modes ou vérifier le modèle." else ""
        )

        // ---------------------------------------------------------------- calage et cinématique
        var driftUtilization: Double? = null
        var pDeltaTheta: Double? = null
        val mseMds = linkedMapOf<String, Double>()
        var softStory: Boolean? = null
        try {
            val seismic = seismicEngine.assess(
                joint = joint,
                results = results,
                config = snapshot.seismicConfig,
                options = Rpa2024SeismicEngineOptions(
                    includePenthouse = false,
                    manualSeismicWeightKN = snapshot.manualSeismicWeightKN
                )
            )
            seismic.baseShear.forEach { shear ->
                val ratio = shear.dynamicToStaticRatio
                if (ratio != null) mseMds[shear.direction.name] = ratio
                val severity = when {
                    shear.scaleFactor != null -> IntegritySeverity.BLOCKING
                    shear.status == CheckStatus.WARNING -> IntegritySeverity.WARNING
                    shear.status == CheckStatus.NOT_APPLICABLE -> IntegritySeverity.WARNING
                    else -> IntegritySeverity.INFORMATION
                }
                add(
                    "MSE-MDS-${shear.direction.name}",
                    "Calage sismique ${shear.directionLabel}",
                    severity,
                    "RPA 2024 § 4.3.5",
                    shear.message,
                    shear.scaleFactor?.let {
                        "Appliquer un facteur d'échelle de ${format(it)} à la réponse dynamique."
                    } ?: ""
                )
            }
            seismic.driftChecks.filter { it.status != CheckStatus.NOT_APPLICABLE }
                .maxByOrNull { it.utilization }
                ?.let { drift ->
                    driftUtilization = drift.utilization
                    add(
                        "DRIFT-01",
                        "Déplacements inter-étages",
                        if (drift.status == CheckStatus.FAILED) IntegritySeverity.BLOCKING else IntegritySeverity.INFORMATION,
                        "RPA 2024 § 5.10",
                        "Niveau ${drift.story} : νA.|Δ| = ${format(drift.reducedDriftMeters * 1000.0)} mm / " +
                            "${format(drift.governingLimitMeters * 1000.0)} mm."
                    )
                }
            seismic.pDeltaChecks.filter { it.status != CheckStatus.NOT_APPLICABLE }
                .maxByOrNull { it.theta }
                ?.let { pDelta ->
                    pDeltaTheta = pDelta.theta
                    add(
                        "PDELTA-01",
                        "Effet P-Delta",
                        when {
                            pDelta.status == CheckStatus.FAILED -> IntegritySeverity.BLOCKING
                            pDelta.status == CheckStatus.WARNING -> IntegritySeverity.WARNING
                            else -> IntegritySeverity.INFORMATION
                        },
                        "RPA 2024 § 5.9",
                        "Niveau ${pDelta.story} : θ = ${format(pDelta.theta)}."
                    )
                }
        } catch (error: Throwable) {
            add("SIS-01", "Analyse sismique non exécutable", IntegritySeverity.BLOCKING,
                "RPA 2024", error.message ?: error.toString())
        }

        try {
            val regularity = regularityAnalyzer.analyze(model, results)
            val b2 = regularity.elevationCriteria.firstOrNull { it.code == "b2" }
            softStory = b2?.satisfied?.not()
            if (b2 != null) {
                add(
                    "B2-01",
                    "Variation de masse et de rigidité",
                    if (b2.satisfied) IntegritySeverity.INFORMATION else IntegritySeverity.WARNING,
                    "RPA 2024 § 3.7.1b",
                    b2.observed
                )
            }
        } catch (error: Throwable) {
            add("B2-00", "Régularité en élévation non calculable", IntegritySeverity.WARNING,
                "RPA 2024 § 3.7.1b", error.message ?: error.toString())
        }

        // ---------------------------------------------------------------- fondations SAFE
        val safe = snapshot.foundations
        if (safe == null) {
            add("SAFE-01", "Modèle SAFE absent", IntegritySeverity.WARNING,
                "DTR BC 2.33.1", "Les fondations ne seront pas vérifiées par le modèle croisé.",
                "Fournir l'export SAFE des semelles et ressorts.")
        } else {
            if (safe.families.isEmpty()) {
                add("SAFE-02", "Aucune semelle lue", IntegritySeverity.BLOCKING,
                    "SAFE — Beam Properties", "La géométrie des semelles est absente.")
            }
            if (safe.springs.isEmpty()) {
                add("SAFE-03", "Ressorts de sol absents", IntegritySeverity.BLOCKING,
                    "SAFE — Line Spring Properties", "Aucun module de réaction Ks n'est exploitable.")
            }
            if (safe.displacementEnvelope == null) {
                add("SAFE-04", "Déplacements verticaux absents", IntegritySeverity.BLOCKING,
                    "SAFE — Joint Displacements", "La vérification de portance Winkler est impossible.")
            }
            if (safe.forceEnvelopes.isEmpty()) {
                add("SAFE-05", "Efforts de semelles absents", IntegritySeverity.BLOCKING,
                    "SAFE — Beam Forces", "Les nervures ne peuvent pas être ferraillées.")
            }
            if (safe.geotechnical.allowableStressKNM2 <= 0.0 || safe.geotechnical.subgradeModulusKNM3 <= 0.0) {
                add("SAFE-06", "Paramètres géotechniques invalides", IntegritySeverity.BLOCKING,
                    "DTR BC 2.33.1", "σsol ou Ks est nul/négatif.")
            }
            val missingForces = safe.families.filter { safe.forcesFor(it.name) == null }
            if (missingForces.isNotEmpty()) {
                add("SAFE-07", "Semelles sans efforts", IntegritySeverity.WARNING,
                    "SAFE — Beam Forces",
                    "${missingForces.size} famille(s) sans enveloppe : ${missingForces.take(6).map { it.name }.joinToString()}.")
            }
            val inconsistentKs = safe.springs.filter {
                abs(it.subgradeModulusKNM3 - safe.geotechnical.subgradeModulusKNM3) /
                    max(safe.geotechnical.subgradeModulusKNM3, 1.0) > 0.20
            }
            if (inconsistentKs.isNotEmpty()) {
                add("SAFE-08", "Modules Ks hétérogènes", IntegritySeverity.WARNING,
                    "SAFE — Line Spring Properties",
                    "${inconsistentKs.size} ressort(s) s'écartent de plus de 20 % du Ks géotechnique.")
            }
            val etabsMaterials = model.joint.materials
            val safeMaterials = safe.materials
            if (safeMaterials.isNotEmpty()) {
                val safeConcrete = safeMaterials.firstOrNull { it.isConcrete }?.fc28MPa
                if (safeConcrete != null && abs(safeConcrete - etabsMaterials.fc28MPa) > 2.0) {
                    add("XMOD-01", "Béton incohérent ETABS/SAFE", IntegritySeverity.WARNING,
                        "ETABS + SAFE",
                        "fc28 ETABS = ${format(etabsMaterials.fc28MPa)} MPa ; " +
                            "fc28 SAFE = ${format(safeConcrete)} MPa.")
                }
            }
        }

        // ---------------------------------------------------------------- synthèse
        if (findings.none { it.severity == IntegritySeverity.BLOCKING }) {
            add("SYN-01", "Contrôle de vérifiabilité terminé", IntegritySeverity.INFORMATION,
                "Audit expert", "Aucun blocage rédhibitoire détecté.")
        }
        return StructuralIntegrityReport(
            findings = findings,
            weightEquilibriumRatio = weightRatio,
            modalSumUxPercent = modal.sumUxPercent,
            modalSumUyPercent = modal.sumUyPercent,
            mseMdsRatios = mseMds,
            driftUtilization = driftUtilization,
            pDeltaTheta = pDeltaTheta,
            softStoryDetected = softStory,
            sourceCount = snapshot.sources.count { it.available }
        )
    }

    private fun weightCaseBaseReaction(results: EtabsResults): Double? {
        val rows: List<BaseReactionRow> = results.baseReactions.filter { row ->
            val normalized = row.loadCase.uppercase().replace(" ", "")
            normalized == "WI" || normalized.startsWith("WI") ||
                normalized.contains("WEIGHT") || normalized.contains("POIDS")
        }
        if (rows.isEmpty()) return null
        return rows.sumOf { abs(it.fzKN) }
    }

    private fun format(value: Double): String =
        String.format(java.util.Locale.ROOT, "%.3f", value)
}
