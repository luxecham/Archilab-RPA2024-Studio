package com.archilab.rpa2024.normative

import com.archilab.rpa2024.model.PenaltyCategory
import com.archilab.rpa2024.model.StructuralJoint
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Base de données et moteur normatif officiel DTR BC 2.48 (RPA 2024) et DTR BC 2.2.
 */
object Rpa2024NormativeDb {

    /**
     * Retourne les paramètres spectraux de calcul selon la zone sismique et la classe de site
     * (Tableau 3.3 pour Spectre Type 1, Tableau 3.4 pour Spectre Type 2).
     */
    fun getSpectralParameters(zone: SeismicZone, site: SiteClass): SpectralParams {
        return when (zone) {
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> {
                // Spectre Type 1 (Tableau 3.3)
                when (site) {
                    SiteClass.S1 -> SpectralParams(s = 1.00, t1 = 0.10, t2 = 0.40, t3 = 2.00, spectrumType = 1)
                    SiteClass.S2 -> SpectralParams(s = 1.20, t1 = 0.10, t2 = 0.50, t3 = 2.00, spectrumType = 1)
                    SiteClass.S3 -> SpectralParams(s = 1.30, t1 = 0.15, t2 = 0.60, t3 = 2.00, spectrumType = 1)
                    SiteClass.S4 -> SpectralParams(s = 1.35, t1 = 0.15, t2 = 0.70, t3 = 2.00, spectrumType = 1)
                    SiteClass.S5 -> SpectralParams(s = 1.40, t1 = 0.15, t2 = 0.75, t3 = 2.00, spectrumType = 1)
                }
            }
            SeismicZone.ZONE_0, SeismicZone.ZONE_I, SeismicZone.ZONE_II, SeismicZone.ZONE_III -> {
                // Spectre Type 2 (Tableau 3.4)
                when (site) {
                    SiteClass.S1 -> SpectralParams(s = 1.00, t1 = 0.05, t2 = 0.25, t3 = 1.20, spectrumType = 2)
                    SiteClass.S2 -> SpectralParams(s = 1.30, t1 = 0.05, t2 = 0.30, t3 = 1.20, spectrumType = 2)
                    SiteClass.S3 -> SpectralParams(s = 1.55, t1 = 0.10, t2 = 0.40, t3 = 1.20, spectrumType = 2)
                    SiteClass.S4 -> SpectralParams(s = 1.80, t1 = 0.10, t2 = 0.50, t3 = 1.20, spectrumType = 2)
                    SiteClass.S5 -> SpectralParams(s = 1.90, t1 = 0.10, t2 = 0.55, t3 = 1.20, spectrumType = 2)
                }
            }
        }
    }

    /**
     * Auto-classification de la catégorie de site géotechnique selon les paramètres du sol (Tableau 3.1).
     */
    fun classifySite(
        vs30: Double? = null,
        n30: Double? = null,
        qc30MPa: Double? = null,
        cu30KPa: Double? = null
    ): SiteClass {
        if (vs30 != null) {
            return when {
                vs30 > 800.0 -> SiteClass.S1
                vs30 > 360.0 -> SiteClass.S2
                vs30 >= 180.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        if (n30 != null) {
            return when {
                n30 > 50.0 -> SiteClass.S2
                n30 >= 15.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        if (qc30MPa != null) {
            return when {
                qc30MPa > 15.0 -> SiteClass.S2
                qc30MPa >= 1.5 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        if (cu30KPa != null) {
            return when {
                cu30KPa > 100.0 -> SiteClass.S2
                cu30KPa >= 50.0 -> SiteClass.S3
                else -> SiteClass.S4
            }
        }
        return SiteClass.S3 // Par défaut meuble (valeur sécuritaire standard)
    }

    /**
     * Calcul automatique du facteur de qualité Q_F = 1 + sum(P_q) selon le Tableau 3.18 du RPA 2024.
     * Les conditions de travées (numBays >= 3) et d'étages (numStories >= 2) sont extraites directement
     * de la topologie géométrique découverte du modèle ETABS.
     */
    fun computeQualityFactor(
        category: PenaltyCategory,
        planRegular: Boolean,
        elevRegular: Boolean,
        topology: StructuralJoint,
        redundantWalls: Boolean = false
    ): QualityFactorResult {
        val penalties = mutableMapOf<String, Double>()

        val hasMinStories = topology.storySystem.numStoriesN >= 2
        val hasMinBays = topology.gridSystem.satisfiesMinBaysCondition

        when (category) {
            PenaltyCategory.CATEGORY_A -> {
                // 1. Régularité en plan
                penalties["1. Régularité en plan"] = if (planRegular) 0.0 else 0.05
                // 2. Régularité en élévation
                penalties["2. Régularité en élévation"] = if (elevRegular) 0.0 else 0.20
                // 3. Conditions minimales sur le nombre d'étages (N >= 2)
                penalties["3. Conditions minimales sur le nombre d'étages"] = if (hasMinStories) 0.0 else 0.20
                // 4. Conditions minimales sur les travées (>= 3 travées dans chaque direction)
                penalties["4. Conditions minimales sur les travées"] = if (hasMinBays) 0.0 else 0.10
            }
            PenaltyCategory.CATEGORY_B -> {
                // 1. Régularité en plan
                penalties["1. Régularité en plan"] = if (planRegular) 0.0 else 0.05
                // 2. Régularité en élévation
                penalties["2. Régularité en élévation"] = if (elevRegular) 0.0 else 0.20
                // 3. Redondance en plan (au moins 2 files de voiles par direction)
                penalties["3. Redondance en plan"] = if (redundantWalls) 0.0 else 0.05
            }
        }

        val sumPq = penalties.values.sum()
        val qf = 1.0 + sumPq

        return QualityFactorResult(
            qf = qf,
            category = category,
            penalties = penalties,
            isPlanRegular = planRegular,
            isElevRegular = elevRegular,
            hasMinStories = hasMinStories,
            hasMinBays = hasMinBays,
            hasRedundantWalls = redundantWalls
        )
    }

    /**
     * Facteur de correction d'amortissement eta = sqrt(7 / (2 + xi)) >= 0.70 (Formule 4.3 RPA 2024).
     */
    fun computeDampingCorrectionEta(xiPercent: Double): Double {
        val raw = sqrt(7.0 / (2.0 + xiPercent))
        return max(0.70, raw)
    }

    /**
     * Période empirique fondamentale T_empirique = C_T * H_n^(3/4) (Formule 4.4 RPA 2024).
     */
    fun computeEmpiricalPeriod(ct: Double, totalHeightHn: Double): Double {
        require(totalHeightHn > 0.0) { "La hauteur totale Hn doit être strictement positive." }
        return ct * totalHeightHn.pow(0.75)
    }

    /**
     * Spectre de réponse élastique horizontal Sae(T)/g (Eq 3.8 RPA 2024).
     */
    fun computeElasticResponseSpectrumSaOverG(
        periodT: Double,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        dampingXiPercent: Double = 7.0
    ): Double {
        val params = getSpectralParameters(zone, site)
        val eta = computeDampingCorrectionEta(dampingXiPercent)
        val a = zone.coeffA
        val i = importance.coeffI
        val s = params.s
        val t1 = params.t1
        val t2 = params.t2
        val t3 = params.t3

        val baseCoeff = a * i * s
        return when {
            periodT <= 0.0 -> baseCoeff
            periodT < t1 -> baseCoeff * (1.0 + (periodT / t1) * (2.5 * eta - 1.0))
            periodT <= t2 -> baseCoeff * (2.5 * eta)
            periodT <= t3 -> baseCoeff * (2.5 * eta) * (t2 / periodT)
            else -> baseCoeff * (2.5 * eta) * ((t2 * t3) / (periodT * periodT))
        }
    }

    /**
     * Spectre de dimensionnement Sad(T)/g (Eq 3.15 RPA 2024) tenant compte du coefficient
     * de comportement R et du facteur de qualité Q_F.
     */
    fun computeDesignResponseSpectrumSadOverG(
        periodT: Double,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        bracing: BracingSystem,
        qualityFactorQf: Double,
        dampingXiPercent: Double = 7.0
    ): Double {
        val params = getSpectralParameters(zone, site)
        val eta = computeDampingCorrectionEta(dampingXiPercent)
        val a = zone.coeffA
        val i = importance.coeffI
        val s = params.s
        val t1 = params.t1
        val t2 = params.t2
        val t3 = params.t3
        val r = bracing.coeffR

        val minAllowed = 0.20 * a * i
        val plateau = (a * i * s) * (2.5 * eta / r) * qualityFactorQf

        val calculated = when {
            periodT <= 0.0 -> (a * i * s) * (2.0 / 3.0)
            periodT < t1 -> (a * i * s) * ((2.0 / 3.0) + (periodT / t1) * ((2.5 * eta / r) * qualityFactorQf - (2.0 / 3.0)))
            periodT <= t2 -> plateau
            periodT <= t3 -> plateau * (t2 / periodT)
            else -> plateau * ((t2 * t3) / (periodT * periodT))
        }

        return max(minAllowed, calculated)
    }

    /**
     * Coefficient correcteur lambda (Eq 4.2 RPA 2024) :
     * lambda = 0.85 si T0 <= 2*T2 et si le bâtiment a plus de 2 niveaux (N > 2), sinon 1.00.
     */
    fun computeLambda(periodT0: Double, t2: Double, numStoriesN: Int): Double {
        return if (periodT0 <= (2.0 * t2) && numStoriesN > 2) 0.85 else 1.00
    }

    /**
     * Force sismique totale à la base par la méthode statique équivalente (Eq 4.1 RPA 2024) :
     * V = lambda * (Sad / g) * W.
     */
    fun computeEquivalentStaticBaseShear(
        totalWeightWKN: Double,
        periodT0: Double,
        zone: SeismicZone,
        site: SiteClass,
        importance: ImportanceGroup,
        bracing: BracingSystem,
        qualityFactorQf: Double,
        numStoriesN: Int,
        dampingXiPercent: Double = 7.0
    ): Double {
        val params = getSpectralParameters(zone, site)
        val lambda = computeLambda(periodT0, params.t2, numStoriesN)
        val sadOverG = computeDesignResponseSpectrumSadOverG(
            periodT = periodT0,
            zone = zone,
            site = site,
            importance = importance,
            bracing = bracing,
            qualityFactorQf = qualityFactorQf,
            dampingXiPercent = dampingXiPercent
        )
        return lambda * sadOverG * totalWeightWKN
    }

    /**
     * Facteur de réduction nu_A pour les déplacements relatifs inter-étages (§ 1.2 Commentaire 6 & § 5.10).
     */
    fun getEffectiveReductionFactorNuA(
        importance: ImportanceGroup,
        useCustom: Boolean = false,
        customValue: Double = 0.50
    ): Double {
        return if (useCustom) customValue else importance.nuATabulated
    }
}
