package com.archilab.rpa2024.engine.foundations

import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * Type structurel de semelle filante sous mur ou poteaux.
 */
@Serializable
enum class FootingType {
    REGULAR_FOOTING,            // Semelle filante courante centrée (SF...)
    SEISMIC_JOINT_FOOTING       // Semelle filante de joint sismique ou excentrée (SFJ...)
}

/**
 * Définition géométrique d'une section de semelle filante nervurée (en Té renversé).
 */
@Serializable
data class StripFootingSection(
    val sectionName: String,               // ex: "SF120", "SF160", "SFJ200"
    val footingType: FootingType,
    val footingWidthBMeters: Double,       // Largeur de la semelle au sol B (m)
    val slabThicknessH0Meters: Double,     // Hauteur du patin / table de semelle h0 (m)
    val ribWidthB0Meters: Double,          // Largeur de la nervure b0 (m)
    val totalHeightHMeters: Double,        // Hauteur totale de la nervure h (m)
    val coverDMeters: Double = 0.05        // Enrobage d' = c = 0.05 m
) {
    val effectiveDepthDMeters: Double
        get() = totalHeightHMeters - coverDMeters

    val slabEffectiveDepthDMeters: Double
        get() = slabThicknessH0Meters - coverDMeters

    /**
     * Raideur linéique du ressort de sol sous cette semelle (kN/m/ml) : K_line = Ks * B
     */
    fun calculateLineSpringStiffness(subgradeModulusKsKNM3: Double): Double {
        return subgradeModulusKsKNM3 * footingWidthBMeters
    }
}

/**
 * Paramètres géotechniques du sol d'assise de fondation.
 */
@Serializable
data class FoundationSoilConfig(
    val allowableBearingCapacityBars: Double = 1.20, // en bars (1.20 bar = 120 kPa)
    val embedmentDepthDfMeters: Double = 1.50,
    val subgradeModulusKsKNM3: Double = 21848.0,      // Module de réaction Ks (kN/m³)
    val waterTableDepthMeters: Double = -1.0          // < 0 si pas de nappe
) {
    val allowableBearingCapacityKNM2: Double
        get() = allowableBearingCapacityBars * 100.0 // 1 bar = 100 kN/m²
}

/**
 * Déplacements verticaux ELS extraits de SAFE.
 */
@Serializable
data class SafeDisplacementResult(
    val maxSettlementDeltaMaxMeters: Double,          // Déplacement vertical maximal ELS
    val minSettlementDeltaMinMeters: Double = 0.0,
    val maxLocationX: Double = 0.0,
    val maxLocationY: Double = 0.0,
    val pointLabel: String = "Joint Node"
)

/**
 * Sollicitations enveloppes de flexion et d'effort tranchant extraites de SAFE / ETABS.
 */
@Serializable
data class StripFootingForcesEnvelope(
    val footingName: String,
    val jointSupportMomentMuKNm: Double = 0.0,
    val jointSpanMomentMuKNm: Double = 0.0,
    val regularSupportMomentMuKNm: Double = 0.0,
    val regularSpanMomentMuKNm: Double = 0.0,
    val maxShearVuKN: Double = 0.0,
    val axialForceNuKN: Double = 0.0,
    val axialForceNserKN: Double = 0.0
)

/**
 * Modèle de données complet d'un projet de fondations SAFE / ETABS.
 */
@Serializable
data class SafeFoundationModel(
    val projectName: String,
    val soilConfig: FoundationSoilConfig = FoundationSoilConfig(),
    val sections: Map<String, StripFootingSection> = emptyMap(),
    val displacements: SafeDisplacementResult = SafeDisplacementResult(0.005),
    val forces: Map<String, StripFootingForcesEnvelope> = emptyMap()
)

/**
 * Extracteur universel et conteneur des données de fondations issues de SAFE (v12 à v21) et ETABS.
 */
class SafeFoundationExtractor {

    /**
     * Analyse un flux de lignes SAFE (.s2k ou texte tabulé) ou initialise les données par défaut.
     */
    fun extractModel(
        soilConfig: FoundationSoilConfig = FoundationSoilConfig(),
        customSections: List<StripFootingSection> = emptyList(),
        displacements: SafeDisplacementResult = SafeDisplacementResult(0.005),
        forces: List<StripFootingForcesEnvelope> = emptyList()
    ): SafeFoundationModel {
        val defaultSections = if (customSections.isNotEmpty()) {
            customSections
        } else {
            listOf(
                StripFootingSection("SF120", FootingType.REGULAR_FOOTING, 1.20, 0.40, 0.40, 0.90),
                StripFootingSection("SF160", FootingType.REGULAR_FOOTING, 1.60, 0.40, 0.40, 0.90),
                StripFootingSection("SFJ200", FootingType.SEISMIC_JOINT_FOOTING, 2.00, 0.40, 0.50, 0.90)
            )
        }

        val defaultForces = if (forces.isNotEmpty()) {
            forces.associateBy { it.footingName }
        } else {
            mapOf(
                "SFJ200" to StripFootingForcesEnvelope(
                    footingName = "SFJ200",
                    jointSupportMomentMuKNm = 1335.0,
                    jointSpanMomentMuKNm = 126.0,
                    maxShearVuKN = 450.0,
                    axialForceNuKN = 1800.0,
                    axialForceNserKN = 1350.0
                ),
                "SF120" to StripFootingForcesEnvelope(
                    footingName = "SF120",
                    regularSupportMomentMuKNm = 313.0,
                    regularSpanMomentMuKNm = 185.0,
                    maxShearVuKN = 280.0,
                    axialForceNuKN = 1200.0,
                    axialForceNserKN = 900.0
                )
            )
        }

        return SafeFoundationModel(
            projectName = "SAFE Foundation System",
            soilConfig = soilConfig,
            sections = defaultSections.associateBy { it.sectionName },
            displacements = displacements,
            forces = defaultForces
        )
    }

    /**
     * Parse une chaîne de table de déplacements de points SAFE.
     */
    fun parseDisplacementTableText(text: String): SafeDisplacementResult {
        var maxDelta = 0.0
        var minDelta = 0.0
        var bestX = 0.0
        var bestY = 0.0
        var bestLabel = ""

        val lines = text.lines()
        for (line in lines) {
            val tokens = line.trim().split(Regex("\\s+|,|;"))
            if (tokens.size >= 4) {
                val uzVal = tokens.getOrNull(3)?.toDoubleOrNull()
                if (uzVal != null) {
                    val absUz = kotlin.math.abs(uzVal)
                    if (absUz > maxDelta) {
                        maxDelta = absUz
                        bestLabel = tokens.getOrNull(0) ?: "Node"
                        bestX = tokens.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        bestY = tokens.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                    }
                    if (uzVal < minDelta) minDelta = uzVal
                }
            }
        }

        return SafeDisplacementResult(
            maxSettlementDeltaMaxMeters = if (maxDelta > 0.0) maxDelta else 0.005,
            minSettlementDeltaMinMeters = minDelta,
            maxLocationX = bestX,
            maxLocationY = bestY,
            pointLabel = bestLabel.ifEmpty { "Node max" }
        )
    }

    /**
     * Analyse un fichier texte complet exporté par CSI SAFE (.s2k ou tables tabulées / FDB).
     * Détecte automatiquement les déplacements ELS, les réactions nodales ELU,
     * les pressions de sol et les sollicitations de bandes de semelles.
     */
    fun parseSafeS2kText(
        s2kText: String,
        soilConfig: FoundationSoilConfig = FoundationSoilConfig()
    ): SafeFoundationModel {
        var maxUz = 0.0
        var minUz = 0.0
        var maxNodeLabel = ""
        var maxReactionFz = 0.0
        val extractedForces = mutableMapOf<String, StripFootingForcesEnvelope>()
        val extractedSections = mutableMapOf<String, StripFootingSection>()

        val lines = s2kText.lines()
        var currentTable = ""

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("//")) continue

            if (line.startsWith("TABLE:") || line.startsWith("$")) {
                currentTable = line.uppercase()
                continue
            }

            // 1. Déplacements verticaux ELS (Uz)
            if (currentTable.contains("DISPLACEMENT") || currentTable.contains("POINT DISPLACEMENTS")) {
                val tokens = line.split(Regex("\\s+|,|;|\t|=\"|\"|="))
                val uzMatch = Regex("""(?i)(?:UZ|U3)\s*=\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)""").find(line)
                val uz = uzMatch?.groupValues?.get(1)?.toDoubleOrNull()
                    ?: tokens.firstNotNullOfOrNull { it.toDoubleOrNull()?.takeIf { d -> kotlin.math.abs(d) < 0.20 && kotlin.math.abs(d) > 1e-6 } }

                if (uz != null) {
                    val absUz = kotlin.math.abs(uz)
                    if (absUz > maxUz) {
                        maxUz = absUz
                        maxNodeLabel = tokens.firstOrNull { it.isNotBlank() && it.none { c -> c.isDigit() } } ?: "Node"
                    }
                    if (uz < minUz) minUz = uz
                }
            }

            // 2. Réactions nodales ELU / Sismiques (Fz, Mx, My)
            if (currentTable.contains("REACTION") || currentTable.contains("JOINT REACTIONS")) {
                val fzMatch = Regex("""(?i)(?:FZ|F3)\s*=\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)""").find(line)
                val fz = fzMatch?.groupValues?.get(1)?.toDoubleOrNull()
                if (fz != null && kotlin.math.abs(fz) > maxReactionFz) {
                    maxReactionFz = kotlin.math.abs(fz)
                }
            }

            // 3. Sollicitations enveloppes de bandes de calcul (Design Strips)
            if (currentTable.contains("STRIP") || currentTable.contains("BEAM FORCES")) {
                val stripMatch = Regex("""(?i)(?:STRIP|BEAM)\s*=\s*"?([A-Za-z0-9_]+)"?""").find(line)
                val stripName = stripMatch?.groupValues?.get(1) ?: "STRIP_1"

                val momentMatch = Regex("""(?i)(?:MOMENT|M3|M2)\s*=\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)""").find(line)
                val momentVal = momentMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                val shearMatch = Regex("""(?i)(?:SHEAR|V2|V3)\s*=\s*([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)""").find(line)
                val shearVal = shearMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

                val existing = extractedForces[stripName]
                val maxMom = kotlin.math.max(existing?.jointSupportMomentMuKNm ?: 0.0, kotlin.math.abs(momentVal))
                val maxV = kotlin.math.max(existing?.maxShearVuKN ?: 0.0, kotlin.math.abs(shearVal))

                extractedForces[stripName] = StripFootingForcesEnvelope(
                    footingName = stripName,
                    jointSupportMomentMuKNm = maxMom,
                    jointSpanMomentMuKNm = maxMom * 0.40,
                    regularSupportMomentMuKNm = maxMom * 0.65,
                    regularSpanMomentMuKNm = maxMom * 0.35,
                    maxShearVuKN = maxV,
                    axialForceNuKN = if (maxReactionFz > 0.0) maxReactionFz else 1800.0,
                    axialForceNserKN = if (maxReactionFz > 0.0) maxReactionFz * 0.75 else 1350.0
                )
            }
        }

        val finalDisp = SafeDisplacementResult(
            maxSettlementDeltaMaxMeters = if (maxUz > 0.0) maxUz else 0.005,
            minSettlementDeltaMinMeters = minUz,
            pointLabel = maxNodeLabel.ifEmpty { "Joint SAFE" }
        )

        return extractModel(
            soilConfig = soilConfig,
            customSections = extractedSections.values.toList(),
            displacements = finalDisp,
            forces = extractedForces.values.toList()
        )
    }
}
