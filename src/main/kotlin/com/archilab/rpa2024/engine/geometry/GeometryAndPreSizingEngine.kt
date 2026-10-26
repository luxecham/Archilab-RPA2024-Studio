package com.archilab.rpa2024.engine.geometry

import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.normative.ResolvedSeismicConfig
import com.archilab.rpa2024.parser.ColumnForceEnvelope
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.ParsedEtabsModel

/** Charges et coefficients retenus pour la descente de charges et le poids sismique. */
data class LoadDefinitions(
    val permanentKN: Double? = null,
    val liveKN: Double? = null,
    val psi: Double,
    val seismicWeightKN: Double? = null
) {
    val seismicCombinationKN: Double?
        get() = when {
            permanentKN != null && liveKN != null -> permanentKN + psi * liveKN
            permanentKN != null -> permanentKN
            else -> seismicWeightKN
        }
}

/** Effort normal de calcul retenu pour un poteau, issu des enveloppes ETABS. */
data class ColumnForceInput(
    val story: String,
    val element: String,
    val familyName: String,
    val axialKN: Double,
    val combo: String
) {
    companion object {
        fun fromEnvelopes(
            families: List<ColumnFamily>,
            envelopes: List<ColumnForceEnvelope>
        ): List<ColumnForceInput> = families.flatMap { family ->
            val ids = family.elementIds.toSet()
            envelopes.filter { it.element in ids }.map { envelope ->
                ColumnForceInput(
                    story = envelope.story,
                    element = envelope.element,
                    familyName = family.name,
                    axialKN = envelope.maxCompressionKN,
                    combo = envelope.maxCompressionCombo
                )
            }
        }
    }
}

/**
 * Moteur des chapitres 1 a 7 : description, geometrie deduite, materiaux, combinaisons,
 * descente de charges, predimensionnement/coffrage et etude sismique.
 */
class GeometryAndPreSizingEngine(
    private val beamChecker: BeamChecker = BeamChecker(),
    private val columnChecker: ColumnChecker = ColumnChecker(),
    private val wallChecker: WallChecker = WallChecker(),
    private val slabChecker: SlabChecker = SlabChecker(),
    private val seismicEngine: SeismicEngine = SeismicEngine()
) {

    fun assess(
        model: ParsedEtabsModel,
        results: EtabsResults = EtabsResults(),
        manualSeismicWeightKN: Double? = null
    ): JointAssessment {
        val joint = model.joint
        val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)
        val seismic = seismicEngine.assess(joint, resolved, results, manualSeismicWeightKN)
        val loads = LoadDefinitions(psi = resolved.psi, seismicWeightKN = seismic.seismicWeightKN)

        val warnings = (model.warnings + results.warnings + resolved.warnings + seismic.notes).distinct()
        val chapters = listOf(
            descriptionChapter(model, resolved),
            geometryChapter(joint),
            materialsChapter(joint.materials),
            combinationsChapter(resolved, loads),
            loadChapter(joint, results, loads),
            preSizingChapter(joint, resolved, results),
            seismicChapter(joint, resolved, seismic, results)
        )
        return JointAssessment(
            jointName = joint.jointName,
            chapters = chapters,
            seismic = seismic,
            warnings = warnings
        )
    }

    private fun descriptionChapter(model: ParsedEtabsModel, resolved: ResolvedSeismicConfig): ChapterReport {
        val joint = model.joint
        val catalog = joint.sectionCatalog
        val lines = mutableListOf(
            "Joint : ${joint.jointName}",
            "Niveaux détectés : ${joint.storySystem.numStoriesIncludingPenthouse} (dont ${joint.storySystem.numStoriesN} principaux)",
            "Familles détectées : ${catalog.columns.size} poteau(x), ${catalog.beams.size} poutre(s), " +
                "${catalog.shearWalls.size} voile(s), ${catalog.slabs.size} plancher(s)",
            "Éléments du modèle : ${catalog.totalElementCount}",
            "Zone sismique ${resolved.zone.code} (${resolved.zone.level}) ; site ${resolved.siteClass.code} ; " +
                "groupe d'importance ${resolved.importance.code} (I = ${resolved.importance.coeffI})",
            "Système de contreventement : ${resolved.bracingSystem.description} (R = ${Checks.format(resolved.behaviorFactorR, 1)}, " +
                "catégorie ${resolved.qualityFactor.categoryLabel})"
        )
        if (model.units != null) {
            lines += "Unités source converties : ${model.units.code} → kN, m, MPa"
        }
        if (model.warnings.isNotEmpty()) {
            model.warnings.forEach { lines += "Avertissement d'import : $it" }
        }

        val checks = mutableListOf<DesignCheck>()
        checks += if (model.warnings.isEmpty()) {
            Checks.boolean(
                "PA1_LECTURE",
                "Lecture du modèle ETABS",
                "Contrôle d'import",
                condition = true,
                required = "Aucune anomalie",
                provided = "Modèle importé sans avertissement"
            )
        } else {
            DesignCheck(
                code = "PA1_LECTURE",
                label = "Lecture du modèle ETABS",
                reference = "Contrôle d'import",
                status = CheckStatus.WARNING,
                required = "Aucune anomalie",
                provided = "${model.warnings.size} avertissement(s)",
                message = "Vérifier la liste des avertissements d'import avant exploitation des résultats."
            )
        }
        checks += Checks.boolean(
            "PA2_FAMILLES",
            "Présence de familles exploitables",
            "Contrôle de topologie",
            condition = !catalog.isEmpty(),
            required = "Au moins une famille",
            provided = "${catalog.columns.size + catalog.beams.size + catalog.shearWalls.size + catalog.slabs.size} famille(s)",
            failureStatus = CheckStatus.FAILED,
            message = "Aucune famille d'éléments n'a pu être reconstruite : vérifier les affectations de sections ETABS."
        )

        return ChapterReport(
            number = 1,
            title = "Description du joint",
            summary = "Identification du bloc, configuration sismique retenue et inventaire des familles extraites d'ETABS.",
            checks = checks,
            lines = lines
        )
    }

    private fun geometryChapter(joint: StructuralJoint): ChapterReport {
        val grid = joint.gridSystem
        val stories = joint.storySystem
        val checks = mutableListOf<DesignCheck>()

        checks += Checks.min(
            "PG1_NIVEAUX",
            "Nombre minimal de deux niveaux",
            "RPA 2024 Tableau 3.18 (critère de qualité)",
            provided = stories.numStoriesN.toDouble(),
            minimum = 2.0,
            unit = "niveaux",
            decimals = 0,
            failureMessage = "Moins de deux niveaux principaux : pénalité Pq = 0,20 applicable (Tableau 3.18)."
        ).let { if (it.status == CheckStatus.FAILED) it.copy(status = CheckStatus.WARNING) else it }

        checks += Checks.min(
            "PG2_TRAVEES_X",
            "Nombre minimal de trois travées en X",
            "RPA 2024 Tableau 3.18 (critère de qualité)",
            provided = grid.numBaysX.toDouble(),
            minimum = 3.0,
            unit = "travées",
            decimals = 0,
            failureMessage = "Moins de trois travées en X : pénalité Pq = 0,10 applicable (Tableau 3.18)."
        ).let { if (it.status == CheckStatus.FAILED) it.copy(status = CheckStatus.WARNING) else it }

        checks += Checks.min(
            "PG3_TRAVEES_Y",
            "Nombre minimal de trois travées en Y",
            "RPA 2024 Tableau 3.18 (critère de qualité)",
            provided = grid.numBaysY.toDouble(),
            minimum = 3.0,
            unit = "travées",
            decimals = 0,
            failureMessage = "Moins de trois travées en Y : pénalité Pq = 0,10 applicable (Tableau 3.18)."
        ).let { if (it.status == CheckStatus.FAILED) it.copy(status = CheckStatus.WARNING) else it }

        val lines = mutableListOf(
            "Longueur totale Lx = ${Checks.format(grid.totalLengthLx)} m ; Ly = ${Checks.format(grid.totalLengthLy)} m",
            "Travées X : ${grid.spansX.joinToString(" ; ") { Checks.format(it) }.ifBlank { "non identifiées" }} m",
            "Travées Y : ${grid.spansY.joinToString(" ; ") { Checks.format(it) }.ifBlank { "non identifiées" }} m",
            "Hauteur totale Hn (hors cage) = ${Checks.format(stories.totalHeightHn)} m ; " +
                "avec édicules = ${Checks.format(stories.totalHeightIncludingPenthouse)} m",
            "Hauteur maximale d'étage = ${Checks.format(stories.maxStoryHeightMeters)} m"
        )
        stories.orderedStories.forEach { story ->
            lines += "Niveau '${story.storyName}' : h = ${Checks.format(story.heightHk)} m, z = " +
                "${Checks.format(story.elevationZk)} m${if (story.isPenthouseOrCage) " (cage/édicule)" else ""}"
        }

        return ChapterReport(
            number = 2,
            title = "Géométrie reconstruite",
            summary = "Trame d'axes, portées, niveaux et hauteurs déduits automatiquement des nœuds ETABS (aucune valeur forfaitaire).",
            checks = checks,
            lines = lines
        )
    }

    private fun materialsChapter(materials: MaterialConfig): ChapterReport {
        val checks = mutableListOf<DesignCheck>()
        val fc28 = materials.fc28MPa
        checks += Checks.boolean(
            code = "PM1_FC28",
            label = "Résistance caractéristique du béton fc28",
            reference = "CBA 93 / RPA 2024",
            condition = fc28 in 20.0..60.0,
            required = "20 ≤ fc28 ≤ 60 MPa (usage courant)",
            provided = "fc28 = ${Checks.format(fc28)} MPa",
            failureStatus = CheckStatus.WARNING,
            message = "fc28 = ${Checks.format(fc28)} MPa hors plage usuelle : vérifier la classe de béton."
        )
        checks += Checks.boolean(
            code = "PM2_ACIER",
            label = "Limite élastique de l'acier",
            reference = "CBA 93",
            condition = materials.feMPa in 400.0..600.0,
            required = "400 ≤ fe ≤ 600 MPa",
            provided = "fe = ${Checks.format(materials.feMPa)} MPa",
            failureStatus = CheckStatus.WARNING,
            message = "Limite élastique fe = ${Checks.format(materials.feMPa)} MPa hors plage usuelle des aciers HA."
        )

        val lines = listOf(
            "Béton : ${materials.concreteClass} ; fc28 = ${Checks.format(fc28)} MPa",
            "Résistance en traction : ft28 = 0,6 + 0,06 fc28 = ${Checks.format(materials.ft28MPa)} MPa",
            "Module de déformation instantané : Eij = 11000 fc28^(1/3) = ${Checks.format(materials.eijMPa, 0)} MPa",
            "Module différé : Evj = 3700 fc28^(1/3) = ${Checks.format(materials.evjMPa, 0)} MPa",
            "Résistance de calcul ELU : fbu = 0,85 fc28/(θ γb) = ${Checks.format(materials.fbuDurableMPa)} MPa (γb = ${Checks.format(materials.gammaBElu, 2)})",
            "Résistance de calcul accidentelle : fbu = ${Checks.format(materials.fbuAccidentalMPa)} MPa (γb = ${Checks.format(materials.gammaBAccidental, 2)})",
            "Acier : ${materials.steelGrade} ; fe = ${Checks.format(materials.feMPa)} MPa ; " +
                "σs ELU = ${Checks.format(materials.sigmaSteelEluMPa)} MPa ; σs accidentel = ${Checks.format(materials.sigmaSteelAccidentalMPa)} MPa",
            "Poids volumique du béton armé : ${Checks.format(materials.gammaConcreteKNm3, 1)} kN/m³"
        )

        return ChapterReport(
            number = 3,
            title = "Matériaux",
            summary = "Caractéristiques mécaniques du béton et de l'acier, déduites du modèle et des règles CBA 93.",
            checks = checks,
            lines = lines
        )
    }

    private fun combinationsChapter(resolved: ResolvedSeismicConfig, loads: LoadDefinitions): ChapterReport {
        val psi = resolved.psi
        val lines = listOf(
            "Combinaisons ELU (CBA 93) : 1,35 G + 1,50 Q",
            "Combinaisons ELS : G + Q",
            "Combinaison sismique RPA 2024 : G + Q + E",
            "Combinaison sismique alternative : 0,80 G ± E",
            "Poids sismique : W = WG + ψ WQ avec ψ = ${Checks.format(psi, 2)} " +
                "(Tableau 4.2, ${resolved.occupancy.description})",
            "Coefficient de comportement R = ${Checks.format(resolved.behaviorFactorR, 2)} ; " +
                "facteur de qualité QF = ${Checks.format(resolved.qualityFactor.qf, 2)}",
            "Coefficient d'amortissement ξ = ${Checks.format(resolved.damping.percent, 0)} % (${resolved.damping.description}) ; " +
                "η = ${Checks.format(resolved.eta, 3)}",
            "Coefficient λ = ${Checks.format(resolved.lambda, 2)} (Eq. 4.2)"
        )
        val checks = mutableListOf<DesignCheck>()
        checks += Checks.info(
            "PC1_COMBINAISONS",
            "Combinaisons réglementaires disponibles",
            "CBA 93 / RPA 2024",
            "ELU, ELS, ELA (G + Q + E) et 0,8G ± E."
        )
        if (loads.seismicCombinationKN == null) {
            checks += Checks.notApplicable(
                "PC2_POIDS",
                "Poids sismique W = WG + ψWQ",
                "RPA 2024 Eq. (4.3)",
                "Charges G et Q non extraites : renseigner le poids sismique manuellement."
            )
        }
        return ChapterReport(
            number = 4,
            title = "Combinaisons d'actions",
            summary = "Combinaisons ELU/ELS/accidentelles et paramètres de calcul sismique retenus.",
            checks = checks,
            lines = lines
        )
    }

    private fun loadChapter(
        joint: StructuralJoint,
        results: EtabsResults,
        loads: LoadDefinitions
    ): ChapterReport {
        val checks = mutableListOf<DesignCheck>()
        val lines = mutableListOf<String>()
        val weight = loads.seismicWeightKN
        if (weight != null) {
            checks += Checks.info(
                "PDC1_POIDS",
                "Poids sismique total W",
                "RPA 2024 Eq. (4.3)",
                "${Checks.format(weight)} kN"
            )
            val cumulative = results.cumulativeWeightByStoryKN()
            if (cumulative.isNotEmpty()) {
                lines += "Poids cumulés lus dans Story Shears (cas WI) :"
                joint.storySystem.orderedStories.forEach { story ->
                    cumulative[story.storyName]?.let {
                        lines += "Niveau '${story.storyName}' : P = ${Checks.format(it)} kN"
                    }
                }
            } else {
                lines += "Poids cumulés par niveau non disponibles : seule la valeur totale W est exploitée."
            }
        } else {
            checks += Checks.notApplicable(
                "PDC1_POIDS",
                "Poids sismique total W",
                "RPA 2024 Eq. (4.3)",
                "Table 'Story Shears' (cas WI) absente et aucune valeur manuelle fournie."
            )
        }
        if (loads.permanentKN != null && loads.liveKN != null) {
            lines += "W = ${Checks.format(loads.permanentKN)} + ${Checks.format(loads.psi, 2)} × " +
                "${Checks.format(loads.liveKN)} = ${Checks.format(loads.seismicCombinationKN ?: 0.0)} kN"
        } else {
            lines += "Descente de charges détaillée G/Q non fournie : le poids sismique global est utilisé pour la répartition des forces."
        }

        return ChapterReport(
            number = 5,
            title = "Descente de charges",
            summary = "Poids sismique et poids cumulés par niveau issus d'ETABS ou fournis manuellement.",
            checks = checks,
            lines = lines
        )
    }

    private fun preSizingChapter(
        joint: StructuralJoint,
        resolved: ResolvedSeismicConfig,
        results: EtabsResults
    ): ChapterReport {
        val catalog = joint.sectionCatalog
        val elements = mutableListOf<ElementCheckReport>()

        catalog.beams.forEach { elements += beamChecker.check(it, resolved.zone, resolved.bracingSystem) }
        catalog.columns.forEach { elements += columnChecker.check(it, resolved.zone, joint.materials.fc28MPa) }
        catalog.shearWalls.forEach { elements += wallChecker.check(it) }
        catalog.slabs.forEach { elements += slabChecker.check(it) }

        val checks = mutableListOf<DesignCheck>()
        checks += Checks.boolean(
            "PPR0_BEAMS",
            "Familles de poutres détectées",
            "RPA 2024 § 7.5",
            condition = catalog.beams.isNotEmpty(),
            required = "Au moins une famille",
            provided = "${catalog.beams.size} famille(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucune poutre détectée : vérifier la classification des éléments horizontaux."
        )
        checks += Checks.boolean(
            "PPR0_COLUMNS",
            "Familles de poteaux détectées",
            "RPA 2024 § 7.4",
            condition = catalog.columns.isNotEmpty(),
            required = "Au moins une famille",
            provided = "${catalog.columns.size} famille(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucun poteau détecté : vérifier la classification des éléments verticaux."
        )
        checks += Checks.boolean(
            "PPR0_SLABS",
            "Familles de planchers détectées",
            "DTR BC 2.2",
            condition = catalog.slabs.isNotEmpty(),
            required = "Au moins une famille",
            provided = "${catalog.slabs.size} famille(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucun plancher détecté : vérifier les surfaces de plancher ETABS."
        )

        val forceInputs = ColumnForceInput.fromEnvelopes(
            catalog.columns,
            results.columnEnvelopes(ComboCategory.ELA).ifEmpty { results.columnEnvelopes() }
        )
        if (forceInputs.isEmpty() && catalog.columns.isNotEmpty()) {
            checks += Checks.notApplicable(
                "PPR0_NU",
                "Efforts normaux des poteaux",
                "RPA 2024 Eq. (7.5)",
                "Aucune table 'Column Forces' importée : vérification ν reportée au chapitre 7."
            )
        }

        val summary = "Vérifications de coffrage RPA 2024 de ${elements.size} famille(s) : " +
            "${elements.count { it.status == CheckStatus.OK }} conformes, " +
            "${elements.count { it.status == CheckStatus.WARNING }} à vérifier, " +
            "${elements.count { it.status == CheckStatus.FAILED }} non conformes."

        return ChapterReport(
            number = 6,
            title = "Prédimensionnement et coffrage",
            summary = summary,
            checks = checks,
            elements = elements
        )
    }

    private fun seismicChapter(
        joint: StructuralJoint,
        resolved: ResolvedSeismicConfig,
        seismic: SeismicAssessment,
        results: EtabsResults
    ): ChapterReport {
        val checks = mutableListOf<DesignCheck>()
        val lines = mutableListOf<String>()
        val spectral = resolved.spectralParams

        if (spectral != null) {
            lines += "Spectre type ${if (spectral.spectrumType.name == "TYPE_1") "1" else "2"} - " +
                "site ${resolved.siteClass.code} : S = ${Checks.format(spectral.s, 2)}, T1 = ${Checks.format(spectral.t1, 2)} s, " +
                "T2 = ${Checks.format(spectral.t2, 2)} s, T3 = ${Checks.format(spectral.t3, 2)} s (${spectral.reference})"
            lines += "A = ${Checks.format(resolved.zone.coeffA, 2)} ; I = ${Checks.format(resolved.importance.coeffI, 2)} ; " +
                "νA = ${Checks.format(resolved.importance.nuA, 2)} ; R = ${Checks.format(resolved.behaviorFactorR, 2)} ; " +
                "QF = ${Checks.format(resolved.qualityFactor.qf, 2)}"
        } else {
            lines += "Site SS : aucune forme spectrale tabulée, étude spécifique requise (RPA 2024 § 3.2.2)."
        }
        lines += "Périodes : Tx = ${seismic.periodXSeconds?.let { Checks.format(it, 3) } ?: "n/d"} s ; " +
            "Ty = ${seismic.periodYSeconds?.let { Checks.format(it, 3) } ?: "n/d"} s ; " +
            "T empirique = ${Checks.format(resolved.empiricalPeriodSeconds, 3)} s"
        lines += "Poids sismique W = ${seismic.seismicWeightKN?.let { "${Checks.format(it)} kN" } ?: "n/d"}"
        lines += "Force statique équivalente : Vx = ${seismic.staticBaseShearXKN?.let { "${Checks.format(it)} kN" } ?: "n/d"} ; " +
            "Vy = ${seismic.staticBaseShearYKN?.let { "${Checks.format(it)} kN" } ?: "n/d"}"
        lines += "Réaction de base ETABS : Vx = ${seismic.dynamicBaseShearXKN?.let { "${Checks.format(it)} kN" } ?: "n/d"} ; " +
            "Vy = ${seismic.dynamicBaseShearYKN?.let { "${Checks.format(it)} kN" } ?: "n/d"}"

        checks += Checks.boolean(
            "PS1_QF",
            "Facteur de qualité QF",
            "RPA 2024 Tableau 3.18",
            condition = resolved.qualityFactor.qf >= 1.0,
            required = "QF ≥ 1,0",
            provided = Checks.format(resolved.qualityFactor.qf, 2),
            message = "QF = ${Checks.format(resolved.qualityFactor.qf, 2)}."
        )

        resolved.qualityFactor.penalties.forEach { penalty ->
            checks += DesignCheck(
                code = "PS_${penalty.code}",
                label = penalty.label,
                reference = "RPA 2024 Tableau 3.18 (${resolved.qualityFactor.categoryLabel})",
                status = if (penalty.applied) CheckStatus.WARNING else CheckStatus.OK,
                required = "Critère satisfait",
                provided = if (penalty.applied) "Pq = +${Checks.format(penalty.weight, 2)} appliquée" else "Pq = 0",
                message = penalty.justification
            )
        }

        val heightCompliance = resolved.heightCompliance
        if (heightCompliance != null) {
            checks += DesignCheck(
                code = "PS2_HAUTEUR",
                label = "Limitation de hauteur du système de contreventement",
                reference = "RPA 2024 ${heightCompliance.limit.reference}",
                status = if (heightCompliance.isSatisfied) CheckStatus.OK else CheckStatus.FAILED,
                required = buildString {
                    append(heightCompliance.limit.maxStories?.let { "≤ $it niveaux" } ?: "sans limite de niveaux")
                    append(" ; ")
                    append(heightCompliance.limit.maxHeightMeters?.let { "≤ ${Checks.format(it)} m" } ?: "sans limite de hauteur")
                },
                provided = "${heightCompliance.storiesConsidered} niveaux ; ${Checks.format(heightCompliance.heightConsideredMeters)} m",
                message = if (heightCompliance.isSatisfied) "" else {
                    "Hauteur ou nombre de niveaux supérieur à la limite du système ${resolved.bracingSystem.id} " +
                        "en zone ${resolved.zone.code}."
                }
            )
        }

        fun shearCheck(direction: String, staticValue: Double?, dynamicValue: Double?): DesignCheck {
            return if (staticValue == null || dynamicValue == null) {
                Checks.notApplicable(
                    "PS3_V$direction",
                    "Comparaison force statique / réaction de base en $direction",
                    "RPA 2024 § 4.3",
                    "Données insuffisantes pour comparer les méthodes statique et dynamique."
                )
            } else {
                val status = if (dynamicValue + 1e-6 >= staticValue) CheckStatus.OK else CheckStatus.WARNING
                DesignCheck(
                    code = "PS3_V$direction",
                    label = "Comparaison force statique / réaction de base en $direction",
                    reference = "RPA 2024 § 4.3.5",
                    status = status,
                    required = "Vdynamique ≥ Vstatique",
                    provided = "Vstat = ${Checks.format(staticValue)} kN ; Vdyn = ${Checks.format(dynamicValue)} kN",
                    message = if (status == CheckStatus.WARNING) {
                        "Base dynamique inférieure à la force statique : correction par le rapport " +
                            "${Checks.format(staticValue / dynamicValue, 3)} requise."
                    } else ""
                )
            }
        }

        checks += shearCheck("X", seismic.staticBaseShearXKN, seismic.dynamicBaseShearXKN)
        checks += shearCheck("Y", seismic.staticBaseShearYKN, seismic.dynamicBaseShearYKN)

        listOf(
            "X" to seismic.driftChecksX,
            "Y" to seismic.driftChecksY
        ).forEach { (direction, driftList) ->
            driftList.forEach { drift ->
                checks += DesignCheck(
                    code = "PS4_DEPL_${direction}_${drift.story}",
                    label = "Déplacement inter-étages en $direction - ${drift.story}",
                    reference = "RPA 2024 § 5.10 Eq. (5.12)",
                    status = drift.status,
                    required = "νA·Δk ≤ 0,0075 hk = ${Checks.format(drift.allowedDriftMeters * 100.0)} cm",
                    provided = "νA·Δk = ${Checks.format(drift.reducedDriftMeters * 100.0)} cm",
                    message = drift.message
                )
            }
        }

        listOf(
            "X" to seismic.pDeltaChecksX,
            "Y" to seismic.pDeltaChecksY
        ).forEach { (direction, pDeltaList) ->
            pDeltaList.forEach { pDelta ->
                checks += DesignCheck(
                    code = "PS5_PDELTA_${direction}_${pDelta.story}",
                    label = "Effet P-Δ en $direction - ${pDelta.story}",
                    reference = "RPA 2024 § 5.9 Eq. (5.10)",
                    status = pDelta.status,
                    required = "θ ≤ 0,10",
                    provided = "θ = ${Checks.format(pDelta.theta, 3)}",
                    message = when (pDelta.status) {
                        CheckStatus.WARNING -> "0,10 < θ ≤ 0,20 : amplifier les effets sismiques par 1/(1-θ)."
                        CheckStatus.FAILED -> "θ > 0,20 : structure potentiellement instable, redimensionnement requis."
                        else -> ""
                    }
                )
            }
        }

        val axialEnvelopes = results.columnEnvelopes(ComboCategory.ELA).ifEmpty { results.columnEnvelopes() }
        val axialElements = joint.sectionCatalog.columns.map { column ->
            ElementCheckReport(
                elementType = "Poteau (effort normal)",
                name = column.name,
                geometry = column.geometryLabel,
                checks = listOf(columnChecker.axialCheck(column, joint.materials.fc28MPa, axialEnvelopes))
            )
        }

        if (seismic.driftChecksX.isEmpty() && seismic.driftChecksY.isEmpty()) {
            checks += Checks.notApplicable(
                "PS4_DEPL",
                "Vérification des déplacements inter-étages",
                "RPA 2024 § 5.10",
                "Table 'Diaphragm CM Displacements' absente : fournir les déplacements EX/EY."
            )
        }

        val summary = buildString {
            append("Vx statique = ")
            append(seismic.staticBaseShearXKN?.let { "${Checks.format(it)} kN" } ?: "n/d")
            append(" ; Vy statique = ")
            append(seismic.staticBaseShearYKN?.let { "${Checks.format(it)} kN" } ?: "n/d")
            append(" ; déplacements : ${seismic.driftStatus.label.lowercase()} ; P-Δ : ${seismic.pDeltaStatus.label.lowercase()}.")
        }

        return ChapterReport(
            number = 7,
            title = "Étude sismique",
            summary = summary,
            checks = checks,
            elements = axialElements,
            lines = lines
        )
    }
}
