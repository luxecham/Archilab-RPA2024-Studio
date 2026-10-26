package com.archilab.rpa2024.model

import kotlinx.serialization.Serializable

/**
 * Ligne du tableau modal (Modal Participating Mass Ratios).
 * Valeurs de participation massique normalisées en pourcentage [0, 100]%.
 */
@Serializable
data class ModalRatioRow(
    val mode: Int,
    val period: Double,
    val ux: Double,
    val uy: Double,
    val uz: Double = 0.0,
    val sumUx: Double,
    val sumUy: Double,
    val sumUz: Double = 0.0,
    val rx: Double = 0.0,
    val ry: Double = 0.0,
    val rz: Double = 0.0
)

/**
 * Réactions à la base issues de l'analyse spectrale multi-modale (Response Spectrum Base Reactions).
 */
@Serializable
data class BaseReactionsResult(
    val vxMds: Double,
    val vyMds: Double
)

/**
 * Ligne du tableau d'efforts tranchants d'étage (Story Shears).
 */
@Serializable
data class StoryShearRow(
    val story: String,
    val loadCombo: String,
    val loc: String, // "Top" ou "Bottom"
    val pKN: Double,
    val vxKN: Double,
    val vyKN: Double
)

/**
 * Ligne du tableau des déplacements du centre de masse de diaphragme (Diaphragm CM Displacements).
 */
@Serializable
data class DiaphragmDisplacementRow(
    val story: String,
    val diaphragm: String,
    val loadCombo: String,
    val ux: Double,
    val uy: Double
)

/**
 * Torseur d'efforts internes (N, M2, M3).
 * Convention : N négatif en compression (ETABS) ou positif en compression selon l'enveloppe.
 */
@Serializable
data class ForceTorseur(
    val nKN: Double,
    val m2KNm: Double,
    val m3KNm: Double
)

/**
 * Enveloppe des efforts internes critiques pour une famille de poteaux.
 */
@Serializable
data class ColumnForcesEnvelope(
    val columnFamilyName: String,
    val maxCompressionNdKN: Double,
    val maxCompressionStory: String = "",
    val maxCompressionColumnId: String = "",
    val maxCompressionCombo: String = "",
    val eluTorseur: ForceTorseur = ForceTorseur(0.0, 0.0, 0.0),
    val elsTorseur: ForceTorseur = ForceTorseur(0.0, 0.0, 0.0),
    val elaTorseur: ForceTorseur = ForceTorseur(0.0, 0.0, 0.0),
    val v2MaxKN: Double = 0.0,
    val v3MaxKN: Double = 0.0
)

/**
 * Enveloppe des sollicitations critiques pour une famille de poutres.
 */
@Serializable
data class BeamForcesEnvelope(
    val beamFamilyName: String,
    val mSpanMaxKNm: Double,
    val mSupportMaxKNm: Double,
    val vuMaxKN: Double,
    val eluSpanM: Double = mSpanMaxKNm,
    val eluSupportM: Double = mSupportMaxKNm,
    val elsSpanM: Double = 0.0,
    val elsSupportM: Double = 0.0,
    val elaSpanM: Double = 0.0,
    val elaSupportM: Double = 0.0
)

/**
 * Effort au niveau d'un étage pour un voile de refend (Pier).
 */
@Serializable
data class PierLevelForce(
    val story: String,
    val zkMeters: Double,
    val nKN: Double,
    val m3KNm: Double,
    val v2KN: Double
)

/**
 * Enveloppe globale et profil vertical des efforts d'un voile (Pier).
 */
@Serializable
data class PierForcesEnvelope(
    val pierName: String,
    val ndMaxBaseKN: Double,
    val v2MaxBaseKN: Double,
    val verticalProfile: List<PierLevelForce> = emptyList()
)

/**
 * Conteneur unifié de tous les résultats d'analyse extraits d'ETABS.
 */
@Serializable
data class EtabsAnalysisResults(
    val modalRatios: List<ModalRatioRow> = emptyList(),
    val baseReactions: BaseReactionsResult = BaseReactionsResult(0.0, 0.0),
    val storyShears: List<StoryShearRow> = emptyList(),
    val diaphragmDisplacements: List<DiaphragmDisplacementRow> = emptyList(),
    val columnEnvelopes: Map<String, ColumnForcesEnvelope> = emptyMap(),
    val beamEnvelopes: Map<String, BeamForcesEnvelope> = emptyMap(),
    val pierEnvelopes: Map<String, PierForcesEnvelope> = emptyMap(),
    val totalWeightWKN: Double = 0.0
)
