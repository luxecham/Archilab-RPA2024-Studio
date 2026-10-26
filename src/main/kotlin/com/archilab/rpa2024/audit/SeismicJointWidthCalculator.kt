package com.archilab.rpa2024.audit

import com.archilab.rpa2024.model.StructuralJoint
import java.util.Locale
import kotlin.math.*

/**
 * Rapport de calcul et de vérification de la largeur minimale du joint sismique.
 */
data class SeismicJointReport(
    val block1Name: String,
    val block2Name: String?,
    val deltaMax1Mm: Double,
    val deltaMax2Mm: Double,
    val dMinLinearMm: Double,         // d_min = 15mm + delta1 + delta2
    val dMinQuadraticMm: Double,      // d_min = 15mm + sqrt(delta1^2 + delta2^2)
    val adoptedJointWidthMm: Double,  // Largeur adoptée (>= 40mm)
    val adoptedJointWidthCm: Double,  // En centimètres (ex: 5.0 cm)
    val isCompliantWith40mmMin: Boolean,
    val calculationSummary: String,
    val executionRecommendations: List<String>
)

/**
 * Calculateur de la largeur minimale réglementaire du Joint Sismique (RPA 2024 § 5.11).
 * Évite l'entrechoquement entre blocs adjacents (Pounding Effect) à partir de leurs déplacements inélastiques maximaux.
 */
class SeismicJointWidthCalculator {

    /**
     * Calcule la largeur du joint sismique entre deux blocs adjacents d'un même projet
     * (ou pour un bloc isolé avec hypothèse symétrique sur le bloc voisin).
     */
    fun calculateSeismicJointWidth(
        jointA: StructuralJoint,
        jointB: StructuralJoint? = null,
        deltaMaxAInelasticMm: Double? = null,
        deltaMaxBInelasticMm: Double? = null
    ): SeismicJointReport {
        // 1. Déplacements inélastiques maximaux au sommet des blocs
        // delta_k = (R / Qf) * delta_ek
        val deltaA = deltaMaxAInelasticMm ?: estimateInelasticDisplacementMm(jointA)
        val deltaB = deltaMaxBInelasticMm ?: (jointB?.let { estimateInelasticDisplacementMm(it) } ?: deltaA)

        // 2. Formulations réglementaires RPA 2024 § 5.11
        // Formule linéaire : d_min = 15mm + delta_1 + delta_2 >= 40mm
        val dMinLinear = max(40.0, 15.0 + deltaA + deltaB)

        // Formule quadratique (combinaison SRSS) : d_min = 15mm + sqrt(delta_1^2 + delta_2^2) >= 40mm
        val dMinQuadratic = max(40.0, 15.0 + sqrt(deltaA * deltaA + deltaB * deltaB))

        // Largeur recommandée adoptée : arrondie au centimètre supérieur (multiple de 10mm)
        val adoptedMm = ceil(dMinLinear / 10.0) * 10.0
        val adoptedCm = adoptedMm / 10.0

        val block2Label = jointB?.jointName ?: "${jointA.jointName} (Bloc Voisin Miroir)"

        val summary = String.format(
            Locale.US,
            "Joint sismique entre %s (delta = %.1f mm) et %s (delta = %.1f mm) : " +
                "d_min calculé = %.1f mm -> Largeur adoptée d = %.1f cm (>= 40 mm requis par RPA 2024 § 5.11).",
            jointA.jointName, deltaA, block2Label, deltaB, dMinLinear, adoptedCm
        )

        val recommendations = listOf(
            "Largeur libre du joint sismique : $adoptedCm cm (ou $adoptedMm mm) continue sur toute la hauteur de la superstructure.",
            "Matériau de réservation : Polystyrène expansé haute densité (>= 20 kg/m³) compressible sans résistance résiduelle.",
            "Traitement d'étanchéité : Couvre-joint métallique à soufflet ou profilé élastomère souple en toiture et façades.",
            "Canalisations & réseaux : Prévoir des raccords flexibles ou boucles de dilatation au franchissement du joint.",
            "Fondations : Les semelles de part et d'autre du joint sismique peuvent être solidaires si le sol d'assise est homogène."
        )

        return SeismicJointReport(
            block1Name = jointA.jointName,
            block2Name = jointB?.jointName,
            deltaMax1Mm = round(deltaA * 10.0) / 10.0,
            deltaMax2Mm = round(deltaB * 10.0) / 10.0,
            dMinLinearMm = round(dMinLinear * 10.0) / 10.0,
            dMinQuadraticMm = round(dMinQuadratic * 10.0) / 10.0,
            adoptedJointWidthMm = adoptedMm,
            adoptedJointWidthCm = adoptedCm,
            isCompliantWith40mmMin = adoptedMm >= 40.0,
            calculationSummary = summary,
            executionRecommendations = recommendations
        )
    }

    /**
     * Estime le déplacement inélastique au sommet en mm à partir de la hauteur totale
     * et du coefficient de comportement R si le déplacement exact n'est pas fourni.
     */
    private fun estimateInelasticDisplacementMm(joint: StructuralJoint): Double {
        val hn = joint.storySystem.totalHeightHn
        val r = joint.seismicParams.bracingSystem.coeffR
        // En moyenne, le déplacement élastique sommet delta_e ~ Hn / 800 à Hn / 600
        val deltaElastMm = (hn / 750.0) * 1000.0
        // delta inélastique = (R / Qf) * delta_e (avec Qf ~ 1.0)
        return deltaElastMm * (r / 1.0) * 0.40 // ~12 à 20 mm pour un R+4
    }
}
