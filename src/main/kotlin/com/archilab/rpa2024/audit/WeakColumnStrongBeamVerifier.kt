package com.archilab.rpa2024.audit

import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.StructuralJoint
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

/**
 * Type de nœud poteaux-poutres.
 */
enum class NodeType(val description: String, val beamCount: Int) {
    INTERIOR_NODE("Nœud Intérieur (2 Poteaux + 2 Poutres)", 2),
    EDGE_NODE("Nœud de Rive (2 Poteaux + 1 Poutre)", 1),
    CORNER_NODE("Nœud d'Angle (2 Poteaux + 1 Poutre)", 1)
}

/**
 * Détail de vérification de la hiérarchie des résistances à un nœud.
 */
data class NodeHierarchyCheck(
    val nodeName: String,
    val nodeType: NodeType,
    val sumMRcKNm: Double,
    val sumMRbKNm: Double,
    val ratioLambda: Double, // sum(MRc) / sum(MRb)
    val isCompliant: Boolean, // ratioLambda >= 1.25
    val observation: String
)

/**
 * Rapport d'audit global de la condition Poteau Fort / Poutre Faible.
 */
data class WeakColumnStrongBeamReport(
    val jointName: String,
    val nodesEvaluated: List<NodeHierarchyCheck>,
    val minRatioLambda: Double,
    val isGloballyCompliant: Boolean,
    val summaryRecommendation: String
)

/**
 * Vérificateur de la condition de rotule plastique dans les poutres (RPA 2024 § 7.6).
 * Justifie la hiérarchie des résistances aux nœuds d'ossatures :
 * sum(M_Rc) >= 1.25 * sum(M_Rb) pour éviter la formation de mécanismes d'étage souple.
 */
class WeakColumnStrongBeamVerifier {

    /**
     * Analyse et vérifie les nœuds types représentatifs du bâtiment.
     */
    fun verifyHierarchy(
        joint: StructuralJoint,
        columnRebarAreaCm2: Double? = null,
        beamRebarAreaCm2: Double? = null,
        columnNdKN: Double? = null
    ): WeakColumnStrongBeamReport {
        val col = joint.sectionCatalog.columns.firstOrNull()
        val beam = joint.sectionCatalog.beams.firstOrNull()

        val bc = (col?.b1Cm ?: 35.0) / 100.0
        val hc = (col?.h1Cm ?: 35.0) / 100.0
        val bb = (beam?.bCm ?: 30.0) / 100.0
        val hb = (beam?.hCm ?: 50.0) / 100.0

        val fc28 = joint.materials.fc28MPa
        val fe = joint.materials.feMPa
        val fsu = fe / 1.15 // ~434.8 MPa

        // Armatures par défaut si non injectées (ex: 8T14 = 12.32 cm² pour poteau, 6T14 = 9.24 cm² pour poutre)
        val asColCm2 = columnRebarAreaCm2 ?: 12.32
        val asBeamCm2 = beamRebarAreaCm2 ?: 9.24
        val ndKN = columnNdKN ?: 850.0

        // 1. Moment résistant ultime de la poutre M_Rb (kNm)
        // M_Rb = As * fsu * z avec z ~ 0.9 * d = 0.9 * (h - 0.04)
        val dB = hb - 0.04
        val zB = 0.90 * dB
        val asBeamM2 = asBeamCm2 * 1e-4
        val mRb = asBeamM2 * (fsu * 1000.0) * zB // en kNm

        // 2. Moment résistant ultime du poteau M_Rc (kNm) sous effort normal Nd
        // M_0 (flexion simple) + delta_M(Nd)
        val dC = hc - 0.04
        val asColHalfM2 = (asColCm2 / 2.0) * 1e-4
        val m0Col = asColHalfM2 * (fsu * 1000.0) * (dC - 0.04)
        // Effet favorable de la compression concomitante Nd
        val mNdCol = 0.30 * ndKN * (hc - 0.06)
        val mRcSingle = m0Col + mNdCol

        // Évaluation des nœuds types
        val nodeChecks = mutableListOf<NodeHierarchyCheck>()

        // A. Nœud Intérieur (2 poteaux sup/inf + 2 poutres gauche/droite)
        val sumMRcInt = 2.0 * mRcSingle
        val sumMRbInt = 2.0 * mRb
        val lambdaInt = if (sumMRbInt > 0) sumMRcInt / sumMRbInt else 2.0
        val compInt = lambdaInt >= 1.25
        nodeChecks.add(
            NodeHierarchyCheck(
                nodeName = "Nœud Intérieur Courant",
                nodeType = NodeType.INTERIOR_NODE,
                sumMRcKNm = round(sumMRcInt * 10.0) / 10.0,
                sumMRbKNm = round(sumMRbInt * 10.0) / 10.0,
                ratioLambda = round(lambdaInt * 100.0) / 100.0,
                isCompliant = compInt,
                observation = String.format(
                    Locale.US,
                    "sum(MRc) = %.1f kNm >= 1.25 * sum(MRb) = %.1f kNm (lambda = %.2f >= 1.25) -> %s",
                    sumMRcInt, 1.25 * sumMRbInt, lambdaInt, if (compInt) "CONFORME" else "NON CONFORME"
                )
            )
        )

        // B. Nœud de Rive (2 poteaux sup/inf + 1 poutre)
        val sumMRcEdge = 2.0 * mRcSingle
        val sumMRbEdge = 1.0 * mRb
        val lambdaEdge = if (sumMRbEdge > 0) sumMRcEdge / sumMRbEdge else 2.0
        val compEdge = lambdaEdge >= 1.25
        nodeChecks.add(
            NodeHierarchyCheck(
                nodeName = "Nœud de Rive Façade",
                nodeType = NodeType.EDGE_NODE,
                sumMRcKNm = round(sumMRcEdge * 10.0) / 10.0,
                sumMRbKNm = round(sumMRbEdge * 10.0) / 10.0,
                ratioLambda = round(lambdaEdge * 100.0) / 100.0,
                isCompliant = compEdge,
                observation = String.format(
                    Locale.US,
                    "sum(MRc) = %.1f kNm >= 1.25 * sum(MRb) = %.1f kNm (lambda = %.2f >= 1.25) -> %s",
                    sumMRcEdge, 1.25 * sumMRbEdge, lambdaEdge, if (compEdge) "CONFORME" else "NON CONFORME"
                )
            )
        )

        // C. Nœud d'Angle (2 poteaux sup/inf + 1 poutre principale)
        val sumMRcCorner = 2.0 * mRcSingle
        val sumMRbCorner = 1.0 * mRb
        val lambdaCorner = if (sumMRbCorner > 0) sumMRcCorner / sumMRbCorner else 2.0
        val compCorner = lambdaCorner >= 1.25
        nodeChecks.add(
            NodeHierarchyCheck(
                nodeName = "Nœud d'Angle",
                nodeType = NodeType.CORNER_NODE,
                sumMRcKNm = round(sumMRcCorner * 10.0) / 10.0,
                sumMRbKNm = round(sumMRbCorner * 10.0) / 10.0,
                ratioLambda = round(lambdaCorner * 100.0) / 100.0,
                isCompliant = compCorner,
                observation = String.format(
                    Locale.US,
                    "sum(MRc) = %.1f kNm >= 1.25 * sum(MRb) = %.1f kNm (lambda = %.2f >= 1.25) -> %s",
                    sumMRcCorner, 1.25 * sumMRbCorner, lambdaCorner, if (compCorner) "CONFORME" else "NON CONFORME"
                )
            )
        )

        val minLambda = nodeChecks.minOfOrNull { it.ratioLambda } ?: 1.0
        val isAllCompliant = nodeChecks.all { it.isCompliant }

        val summary = if (isAllCompliant) {
            "Condition de rotule plastique dans les poutres vérifiée (RPA 2024 § 7.6) : ratio minimal lambda = $minLambda >= 1.25 sur tous les nœuds."
        } else {
            "Déficit de capacité poteaux aux nœuds. Risque de formation de rotules plastiques dans les poteaux. Augmenter la section ou le ferraillage des poteaux."
        }

        return WeakColumnStrongBeamReport(
            jointName = joint.jointName,
            nodesEvaluated = nodeChecks,
            minRatioLambda = minLambda,
            isGloballyCompliant = isAllCompliant,
            summaryRecommendation = summary
        )
    }
}
