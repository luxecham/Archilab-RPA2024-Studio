package com.archilab.rpa2024.normative

import com.archilab.rpa2024.model.InfillType
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.model.StructuralJoint
import kotlin.math.pow

/**
 * Parametres sismiques effectivement retenus pour un joint, apres resolution des codes
 * et application eventuelle des valeurs imposees par l'utilisateur.
 */
data class ResolvedSeismicConfig(
    val zone: SeismicZone,
    val siteClass: SiteClass,
    val spectralParams: SpectralParams?,
    val importance: ImportanceGroup,
    val bracingSystem: BracingSystem,
    val behaviorFactorR: Double,
    val qualityFactor: QualityFactorResult,
    val ctCoefficient: Double,
    val empiricalPeriodSeconds: Double,
    val lambda: Double,
    val damping: DampingValue,
    val eta: Double,
    val occupancy: OccupancyCase,
    val psi: Double,
    val heightCompliance: HeightCompliance?,
    val warnings: List<String> = emptyList()
) {
    val zoneAcceleration: Double get() = zone.coeffA
    val siteCoefficient: Double? get() = spectralParams?.s
    val spectrumType: SpectrumType? get() = spectralParams?.spectrumType
    val designSpectrumDenominator: Double get() = behaviorFactorR * qualityFactor.qf

    fun elasticAccelerationOverG(periodSeconds: Double): Double? =
        spectralParams?.elasticAccelerationOverG(periodSeconds, zone.coeffA, importance.coeffI, eta)

    fun designAccelerationOverG(periodSeconds: Double): Double? =
        spectralParams?.designAccelerationOverG(
            periodSeconds = periodSeconds,
            zoneAcceleration = zone.coeffA,
            importanceFactor = importance.coeffI,
            behaviorFactor = behaviorFactorR,
            qualityFactor = qualityFactor.qf
        )
}

/**
 * Base de donnees normative RPA 2024 (DTR BC 2.48). Seules les tables utiles aux chapitres
 * 1 a 7 sont embarquees : classification, spectres elastiques, importance, systemes de
 * contreventement, facteur de qualite, amortissement, psi et coefficient de comportement.
 */
object Rpa2024NormativeDb {

    private val type1Spectra: Map<SiteClass, SpectralParams> = mapOf(
        SiteClass.S1 to SpectralParams(SpectrumType.TYPE_1, SiteClass.S1, 1.00, 0.10, 0.40, 2.00, "Tableau 3.3"),
        SiteClass.S2 to SpectralParams(SpectrumType.TYPE_1, SiteClass.S2, 1.20, 0.10, 0.50, 2.00, "Tableau 3.3"),
        SiteClass.S3 to SpectralParams(SpectrumType.TYPE_1, SiteClass.S3, 1.30, 0.15, 0.60, 2.00, "Tableau 3.3"),
        SiteClass.S4 to SpectralParams(SpectrumType.TYPE_1, SiteClass.S4, 1.35, 0.15, 0.70, 2.00, "Tableau 3.3")
    )

    private val type2Spectra: Map<SiteClass, SpectralParams> = mapOf(
        SiteClass.S1 to SpectralParams(SpectrumType.TYPE_2, SiteClass.S1, 1.00, 0.05, 0.25, 1.20, "Tableau 3.4"),
        SiteClass.S2 to SpectralParams(SpectrumType.TYPE_2, SiteClass.S2, 1.30, 0.05, 0.30, 1.20, "Tableau 3.4"),
        SiteClass.S3 to SpectralParams(SpectrumType.TYPE_2, SiteClass.S3, 1.55, 0.10, 0.40, 1.20, "Tableau 3.4"),
        SiteClass.S4 to SpectralParams(SpectrumType.TYPE_2, SiteClass.S4, 1.80, 0.10, 0.50, 1.20, "Tableau 3.4")
    )

    fun getSpectralParameters(zone: SeismicZone, site: SiteClass): SpectralParams {
        if (!site.hasTabulatedSpectrum) {
            throw NormativeDataException(
                "Site SS : aucune forme spectrale tabulee (Tableaux 3.3 et 3.4). Une etude specifique du site est requise (§ 3.2.2)."
            )
        }
        val table = if (zone.spectrumType == SpectrumType.TYPE_1) type1Spectra else type2Spectra
        return table[site]
            ?: throw NormativeDataException("Aucun spectre tabule pour la zone ${zone.code} et le site ${site.code}.")
    }

    fun siteCoefficient(zone: SeismicZone, site: SiteClass): Double = getSpectralParameters(zone, site).s

    /**
     * Tableau 3.18 : QF = 1 + somme des Pq des criteres non satisfaits.
     * La categorie (c) impose QF = 1 quelle que soit la regularite.
     */
    fun computeQualityFactor(
        category: PenaltyCategory,
        planRegular: Boolean,
        elevationRegular: Boolean,
        topology: StructuralJoint,
        redundantWalls: Boolean
    ): QualityFactorResult {
        if (category == PenaltyCategory.C) {
            return QualityFactorResult(
                category = category,
                qf = 1.0,
                notes = listOf("Catégorie (c) du Tableau 3.18 : QF = 1 par disposition réglementaire.")
            )
        }

        val stories = topology.storySystem.numStoriesN
        val baysX = topology.gridSystem.numBaysX
        val baysY = topology.gridSystem.numBaysY

        val penalties = if (category == PenaltyCategory.A) {
            listOf(
                PenaltyItem(
                    code = "QA1_PLAN",
                    label = "Régularité en plan",
                    weight = 0.05,
                    applied = !planRegular,
                    justification = if (planRegular) "Structure régulière en plan (§ 3.7.1a)" else "Irrégularité en plan (§ 3.7.1a)"
                ),
                PenaltyItem(
                    code = "QA2_ELEVATION",
                    label = "Régularité en élévation",
                    weight = 0.20,
                    applied = !elevationRegular,
                    justification = if (elevationRegular) "Structure régulière en élévation (§ 3.7.1b)" else "Irrégularité en élévation (§ 3.7.1b)"
                ),
                PenaltyItem(
                    code = "QA3_MIN_STORIES",
                    label = "Condition minimale de 2 niveaux",
                    weight = 0.20,
                    applied = stories < 2,
                    justification = "N = $stories niveau(x) principal(aux)"
                ),
                PenaltyItem(
                    code = "QA4_MIN_BAYS",
                    label = "Condition minimale de 3 travées par direction",
                    weight = 0.10,
                    applied = baysX < 3 || baysY < 3,
                    justification = "Travées détectées : $baysX en X, $baysY en Y"
                )
            )
        } else {
            listOf(
                PenaltyItem(
                    code = "QB1_PLAN",
                    label = "Régularité en plan",
                    weight = 0.05,
                    applied = !planRegular,
                    justification = if (planRegular) "Structure régulière en plan (§ 3.7.1a)" else "Irrégularité en plan (§ 3.7.1a)"
                ),
                PenaltyItem(
                    code = "QB2_ELEVATION",
                    label = "Régularité en élévation",
                    weight = 0.20,
                    applied = !elevationRegular,
                    justification = if (elevationRegular) "Structure régulière en élévation (§ 3.7.1b)" else "Irrégularité en élévation (§ 3.7.1b)"
                ),
                PenaltyItem(
                    code = "QB3_REDUNDANCY",
                    label = "Redondance en plan",
                    weight = 0.05,
                    applied = !redundantWalls,
                    justification = if (redundantWalls) {
                        "Au moins deux files de voiles par direction (§ 3.8)"
                    } else {
                        "Moins de deux files de voiles dans une direction (§ 3.8)"
                    }
                )
            )
        }

        val qf = 1.0 + penalties.filter { it.applied }.sumOf { it.weight }
        return QualityFactorResult(category = category, qf = qf, penalties = penalties)
    }

    /** Tableau 4.3 : CT = 0.075 pour une ossature BA sans remplissage, 0.050 sinon. */
    fun ctCoefficient(system: BracingSystem): Double = system.defaultCt

    /** Eqn. (4.4) : T = CT . Hn^(3/4), Hn en metres. */
    fun empiricalPeriod(ct: Double, totalHeightHnMeters: Double): Double =
        if (totalHeightHnMeters <= 0.0) 0.0 else ct * totalHeightHnMeters.pow(0.75)

    /** Eqn. (4.2) : lambda = 0.85 si T <= 2 T2 et si le batiment a plus de 2 niveaux. */
    fun lambdaFactor(periodSeconds: Double, t2Seconds: Double, mainStories: Int): Double =
        if (mainStories > 2 && periodSeconds <= 2.0 * t2Seconds) 0.85 else 1.0

    fun dampingFor(system: BracingSystem, infill: InfillType): DampingValue = DampingTable.dampingFor(system, infill)

    /** Eqn. (3.9). */
    fun eta(dampingPercent: Double): Double = DampingTable.correctionFactor(dampingPercent)

    fun psiFor(occupancy: OccupancyCase): Double = occupancy.psi

    /**
     * Resout la configuration saisie contre les tables RPA 2024 et evalue les criteres
     * dependant de la topologie (QF, lambda, limites de hauteur).
     */
    fun resolveSeismic(config: SeismicConfig, joint: StructuralJoint): ResolvedSeismicConfig {
        val warnings = mutableListOf<String>()

        val zone = SeismicZone.fromCode(config.zoneCode)
        val declaredSite = if (config.specificStudySite) SiteClass.SS else SiteClass.fromCode(config.siteClassCode)
        val siteClass = config.siteParameters
            ?.let { parameters ->
                val classified = SiteClassifier.classify(parameters)
                if (classified != declaredSite) {
                    warnings += "Site reclassé automatiquement de ${declaredSite.code} à ${classified.code} par le Tableau 3.1 " +
                        "(données géotechniques fournies)."
                }
                classified
            }
            ?: declaredSite

        val spectralParams = if (siteClass.hasTabulatedSpectrum) {
            getSpectralParameters(zone, siteClass)
        } else {
            warnings += "Site SS : étude spécifique requise, aucun spectre tabulé (Tableaux 3.3 et 3.4)."
            null
        }

        if (zone == SeismicZone.ZONE_0) {
            warnings += "Zone sismique 0 : aucune action sismique de calcul (A = 0). Les vérifications sismiques ne s'appliquent pas."
        }

        val importance = ImportanceGroup.fromCode(config.importanceGroupCode)
        val system = BracingSystem.fromId(config.bracingSystemId)
        val behaviorFactorR = config.behaviorFactorOverride ?: system.coeffR

        val qualityFactor = config.qualityFactorOverride?.let { imposed ->
            QualityFactorResult(
                category = system.penaltyCategory,
                qf = imposed,
                notes = listOf("QF imposé manuellement ($imposed) au lieu du calcul automatique.")
            )
        } ?: computeQualityFactor(
            category = system.penaltyCategory,
            planRegular = config.planRegular,
            elevationRegular = config.elevationRegular,
            topology = joint,
            redundantWalls = config.redundantWallsInPlan
        )

        val ct = config.ctCoefficientOverride ?: ctCoefficient(system)
        val empiricalPeriod = empiricalPeriod(ct, joint.storySystem.totalHeightHn)

        val damping = config.dampingPercentOverride
            ?.let { DampingValue(it, "Amortissement imposé manuellement", "Saisie") }
            ?: dampingFor(system, config.infillType)
        val eta = eta(damping.percent)

        val occupancy = OccupancyCase.fromCode(config.occupancyCaseCode)
        val psi = config.psiOverride ?: psiFor(occupancy)

        val lambda = config.lambdaOverride ?: spectralParams
            ?.let { lambdaFactor(empiricalPeriod, it.t2, joint.storySystem.numStoriesN) }
            ?: 1.0

        val heightCompliance = system.heightCompliance(
            zone = zone,
            storiesIncludingPenthouse = joint.storySystem.numStoriesIncludingPenthouse,
            heightIncludingPenthouseMeters = joint.storySystem.totalHeightIncludingPenthouse,
            mainStories = joint.storySystem.numStoriesN,
            mainHeightMeters = joint.storySystem.totalHeightHn
        )

        if (heightCompliance != null && !heightCompliance.isSatisfied) {
            val limit = heightCompliance.limit
            warnings += if (heightCompliance.mainStructureSatisfied) {
                "Limite de hauteur ${limit.reference} dépassée uniquement par les édicules/cage : " +
                    "${format(heightCompliance.heightConsideredMeters)} m au lieu de ${limit.maxHeightMeters} m. À justifier."
            } else {
                "Limite de hauteur ${limit.reference} non satisfaite : " +
                    "${heightCompliance.storiesConsidered} niveaux / ${format(heightCompliance.heightConsideredMeters)} m " +
                    "(max ${limit.maxStories ?: "-"} niveaux / ${limit.maxHeightMeters ?: "-"} m)."
            }
        }

        if (config.usesOverrides) {
            warnings += "Des valeurs ont été imposées manuellement (CT, R, QF, ξ, ψ ou λ) : elles prévalent sur la base RPA 2024."
        }

        return ResolvedSeismicConfig(
            zone = zone,
            siteClass = siteClass,
            spectralParams = spectralParams,
            importance = importance,
            bracingSystem = system,
            behaviorFactorR = behaviorFactorR,
            qualityFactor = qualityFactor,
            ctCoefficient = ct,
            empiricalPeriodSeconds = empiricalPeriod,
            lambda = lambda,
            damping = damping,
            eta = eta,
            occupancy = occupancy,
            psi = psi,
            heightCompliance = heightCompliance,
            warnings = warnings
        )
    }

    private fun format(value: Double): String = String.format("%.2f", value)
}
