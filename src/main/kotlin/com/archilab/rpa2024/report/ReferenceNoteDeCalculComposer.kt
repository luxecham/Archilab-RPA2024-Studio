package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingDesignSet
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignSet
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignSet
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicAssessment
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngineOptions
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignSet
import com.archilab.rpa2024.normative.OccupancyCase
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.normative.SpectrumType
import kotlin.math.max

data class ReferenceNoteArtifacts(
    val base: NoteDeCalculArtifacts,
    val document: ReportDocument,
    val capturePlaceholders: List<String>
)

/**
 * Compose la note de calcul CTC de reference : conserve tous les calculs du
 * [NoteDeCalculBundle], renomme les treize chapitres selon le sommaire de reference,
 * ajoute les tableaux DTR/RPA manquants et insere systematiquement les balises de
 * captures ETABS/SAFE avec leur commentaire d'interpretation et la mention CV.
 */
class ReferenceNoteDeCalculComposer(
    private val bundle: NoteDeCalculBundle = NoteDeCalculBundle(),
    private val seismicEngine: Rpa2024SeismicEngine = Rpa2024SeismicEngine(),
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val foundationEngine: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator()
) {

    fun compose(request: NoteDeCalculRequest): ReferenceNoteArtifacts {
        val base = bundle.build(request)
        val joint = request.model.joint
        val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)
        val seismic = seismicEngine.assess(
            joint,
            request.results,
            joint.seismicParams,
            Rpa2024SeismicEngineOptions(manualSeismicWeightKN = request.options.manualSeismicWeightKN)
        )
        val columns = columnEngine.design(joint, request.results)
        val beams = beamEngine.design(joint, request.results)
        val walls = wallEngine.design(joint, request.results)
        val footings = request.foundations?.let { foundationEngine.design(it, joint.materials) }
        val placeholders = mutableListOf<String>()

        val chapters = base.document.chapters.map { chapter ->
            val title = referenceTitle(chapter.number) ?: chapter.title
            val extras = when (chapter.number) {
                1 -> descriptionExtras(request, chapter, placeholders)
                2 -> geometryExtras(request, chapter, placeholders)
                3 -> materialsExtras(request, resolved, chapter, placeholders)
                4 -> combinationExtras(request, resolved, chapter, placeholders)
                5 -> loadExtras(request, chapter)
                6, 7 -> synthesisBlock(chapter, if (chapter.number == 6) "poutres" else "poteaux")
                8 -> slabCaptureExtras(request, chapter, placeholders)
                9 -> seismicExtras(seismic, chapter, placeholders)
                10 -> structuralExtras(columns, beams, walls, chapter, placeholders)
                11 -> conclusionOnly(chapter, "acrotère")
                12 -> staircaseExtras(request, chapter)
                13 -> foundationExtras(footings, request, chapter, placeholders)
                else -> emptyList()
            }
            chapter.copy(
                title = title,
                blocks = chapter.blocks + extras
            )
        }
        val annexes = base.document.annexes.map { annex ->
            if (annex.number != 14) return@map annex
            annex.copy(
                blocks = annex.blocks.map { block ->
                    if (block is ReportBlock.Table && block.caption == "Synthèse générale des vérifications") {
                        block.copy(
                            rows = chapters.map { chapter ->
                                listOf(
                                    "${chapter.number}",
                                    chapter.title,
                                    ReportText.statusLabel(chapter.status),
                                    "${chapter.blocks.count { it is ReportBlock.ChecksTable }} tableau(x)"
                                )
                            }
                        )
                    } else {
                        block
                    }
                }
            )
        }
        return ReferenceNoteArtifacts(
            base = base,
            document = base.document.copy(chapters = chapters, annexes = annexes),
            capturePlaceholders = placeholders.distinct()
        )
    }

    private fun referenceTitle(number: Int): String? = when (number) {
        1 -> "Description du projet"
        2 -> "Caractéristiques géométriques du bâtiment"
        3 -> "Caractéristiques des matériaux"
        4 -> "Combinaisons de calcul"
        5 -> "Descente de charges (DTR-BC 2.2)"
        6 -> "Prédimensionnement des poutres"
        7 -> "Prédimensionnement des poteaux"
        8 -> "Étude des planchers (corps creux et poutrelles)"
        9 -> "Étude sismique (RPA 2024)"
        10 -> "Calcul des éléments structuraux"
        11 -> "Étude de l'acrotère"
        12 -> "Étude des escaliers"
        13 -> "Calcul de fondation (SAFE / ETABS)"
        else -> null
    }

    private fun descriptionExtras(
        request: NoteDeCalculRequest,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val joint = request.model.joint
        return buildList {
            add(ReportBlock.Heading(2, "1.5 Environnement logiciel et traçabilité", "s15"))
            add(
                ReportBlock.Paragraph(
                    "La chaîne de calcul est constituée d'ETABS v9.7.4/v18/v21 pour l'analyse " +
                        "tridimensionnelle et SAFE v12/v20 pour les fondations. La présente note reprend " +
                        "les tables exportées (nœuds, connectivités, sections, masses, modes, déplacements, " +
                        "efforts et réactions) sans ressaisie manuelle des cotes ou des résultats."
                )
            )
            addAll(
                capture(
                    "Vue 3D globale du modèle filaire/extrudé ETABS de la structure",
                    "La vue 3D doit confirmer la trame ${joint.gridSystem.totalLengthLx} m × " +
                        "${joint.gridSystem.totalLengthLy} m, les ${joint.storySystem.numStoriesN} niveaux " +
                        "principaux, les ${request.model.frames.size} barres et ${request.model.areas.size} surfaces " +
                        "importées. " + cv(chapter.status),
                    chapter.status,
                    placeholders
                )
            )
        }
    }

    private fun geometryExtras(
        request: NoteDeCalculRequest,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val joint = request.model.joint
        return buildList {
            add(ReportBlock.Heading(2, "2.5 Vue en plan et implantation", "s25"))
            add(
                ReportBlock.Paragraph(
                    "La trame comporte ${joint.gridSystem.axisCountX} files en X et " +
                        "${joint.gridSystem.axisCountY} files en Y. Les poteaux, voiles, poutres et planchers " +
                        "sont positionnés aux coordonnées lues dans ETABS."
                )
            )
            addAll(
                capture(
                    "Vue en plan des axes et de la disposition des poteaux/voiles dans ETABS",
                    "La capture doit montrer les axes repérés, l'emprise Lx = " +
                        "${fmt(joint.gridSystem.totalLengthLx)} m et Ly = ${fmt(joint.gridSystem.totalLengthLy)} m, " +
                        "ainsi que les ${joint.sectionCatalog.columns.size} familles de poteaux et " +
                        "${joint.sectionCatalog.shearWalls.size} famille(s) de voiles du joint. " + cv(chapter.status),
                    chapter.status,
                    placeholders
                )
            )
        }
    }

    private fun materialsExtras(
        request: NoteDeCalculRequest,
        resolved: com.archilab.rpa2024.normative.ResolvedSeismicConfig,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val materials = request.model.joint.materials
        val spectrum = resolved.spectralParams
        val soil = request.foundations?.geotechnical
        val soilBars = soil?.allowableStressBars ?: materials.soilAllowableStressBars
        val soilStress = soil?.allowableStressKNM2 ?: (soilBars * 100.0)
        return buildList {
            add(ReportBlock.Heading(2, "3.5 Coordonnées sismiques, sol et ancrage", "s35"))
            add(
                ReportBlock.Table(
                    "Hypothèses sismiques et géotechniques",
                    listOf("Paramètre", "Symbole", "Valeur", "Unité / référence"),
                    listOf(
                        listOf("Zone sismique", "Zone", resolved.zone.code, resolved.zone.level),
                        listOf("Classe de site", "Site", resolved.siteClass.code, resolved.siteClass.description),
                        listOf("Groupe d'importance", "Groupe", resolved.importance.code,
                            "I = ${fmt(resolved.importance.coeffI, 2)}"),
                        listOf("Spectre retenu", "Type", spectrumTypeLabel(spectrum?.spectrumType),
                            if (spectrum == null) "Étude spécifique requise" else
                                "S = ${fmt(spectrum.s, 2)} ; T1 = ${fmt(spectrum.t1, 2)} s ; " +
                                    "T2 = ${fmt(spectrum.t2, 2)} s ; T3 = ${fmt(spectrum.t3, 2)} s"),
                        listOf("Coefficient de comportement", "R", fmt(resolved.behaviorFactorR, 2), "RPA 2024"),
                        listOf("Contrainte admissible du sol", "σ̄sol", fmt(soilStress, 1), "kN/m²"),
                        listOf("Module de réaction du sol", "Ks", fmt(soil?.subgradeModulusKNM3 ?: Double.NaN, 0), "kN/m³"),
                        listOf("Profondeur d'assise", "D", fmt(soil?.foundationDepthMeters ?: 0.0, 2), "m"),
                        listOf("Longueur d'ancrage de référence", "la", fmt(materials.anchorageLengthMeters, 2), "m")
                    ),
                    columnWidthsCm = listOf(4.5, 2.0, 3.8, 5.9),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.LEFT, ReportAlign.LEFT)
                )
            )
            add(
                ReportBlock.Paragraph(
                    "Le béton C25/30 présente fc28 = ${fmt(materials.fc28MPa, 1)} MPa, ft28 = " +
                        "${fmt(materials.ft28MPa, 2)} MPa, Eij = ${fmt(materials.eijMPa, 0)} MPa et " +
                        "Evj = ${fmt(materials.evjMPa, 0)} MPa. L'acier FeE500 présente fe = " +
                        "${fmt(materials.feMPa, 0)} MPa. Les armatures de table sont constituées d'un treillis " +
                        "soudé TS 150x150 Ø5."
                )
            )
            addAll(
                capture(
                    "Capture de la fenêtre Material Property Data d'ETABS / SAFE",
                    "Les propriétés affichées doivent reprendre fc28 = ${fmt(materials.fc28MPa, 1)} MPa, " +
                        "fe = ${fmt(materials.feMPa, 0)} MPa, γb = ${fmt(materials.gammaConcreteKNm3, 1)} kN/m³ " +
                        "et Ks = ${fmt(soil?.subgradeModulusKNM3 ?: Double.NaN, 0)} kN/m³ pour le sol. " +
                        cv(chapter.status),
                    chapter.status,
                    placeholders
                )
            )
        }
    }

    private fun combinationExtras(
        request: NoteDeCalculRequest,
        resolved: com.archilab.rpa2024.normative.ResolvedSeismicConfig,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val psi = resolved.psi
        val signs = listOf(
            "EX + 0,30 EY", "-EX - 0,30 EY", "EX - 0,30 EY", "-EX + 0,30 EY",
            "0,30 EX + EY", "-0,30 EX - EY", "0,30 EX - EY", "-0,30 EX + EY"
        )
        return buildList {
            add(ReportBlock.Heading(2, "4.5 Tableau 4.2 — coefficients d'accompagnement ψ", "s45"))
            add(
                ReportBlock.Table(
                    "Coefficients ψ du RPA 2024 (Tableau 4.2)",
                    listOf("Code", "Destination des locaux", "ψ"),
                    OccupancyCase.entries.map { occupancy ->
                        listOf(
                            occupancy.code,
                            occupancy.description,
                            fmt(occupancy.psi, 2) + if (occupancy.code == resolved.occupancy.code) " (cas retenu)" else ""
                        )
                    },
                    columnWidthsCm = listOf(2.5, 10.5, 3.2),
                    alignments = listOf(ReportAlign.CENTER, ReportAlign.LEFT, ReportAlign.CENTER)
                )
            )
            add(
                ReportBlock.Note(
                    "Le poids sismique est calculé par W = WG + ψ WQ avec ψ = ${fmt(psi, 2)} " +
                        "pour le cas d'occupation ${resolved.occupancy.code}.",
                    "Poids sismique"
                )
            )
            add(ReportBlock.Heading(2, "4.6 Définition des huit combinaisons accidentelles", "s46"))
            add(
                ReportBlock.Table(
                    "Combinaisons COMB1 à COMB8",
                    listOf("Combinaison", "Définition"),
                    signs.mapIndexed { index, signsText ->
                        listOf("COMB${index + 1}", "G + ${fmt(psi, 2)} Q + $signsText")
                    },
                    columnWidthsCm = listOf(3.0, 13.2),
                    alignments = listOf(ReportAlign.CENTER, ReportAlign.LEFT)
                )
            )
            add(ReportBlock.Heading(2, "4.7 Source de masse ETABS", "s47"))
            add(
                ReportBlock.KeyValues(
                    "Define Mass Source",
                    listOf(
                        "Source" to "From Loads",
                        "Charge permanente G" to "1,00",
                        "Charge d'exploitation Q" to "ψ = ${fmt(psi, 2)}",
                        "Direction de masse" to "X et Y (masse latérale)",
                        "Localisation" to "Masses concentrées aux niveaux"
                    )
                )
            )
            addAll(
                capture(
                    "Capture de la fenêtre Define Load Combinations et Define Mass Source d'ETABS",
                    "La capture doit montrer les combinaisons ELU, ELS, E1, E2, COMB1 à COMB8 et la source " +
                        "de masse From Loads avec G = 1,00 et Q = ψ = ${fmt(psi, 2)}, limitée aux masses " +
                        "latérales. " + cv(CheckStatus.OK),
                    CheckStatus.OK,
                    placeholders
                )
            )
        }
    }

    private fun loadExtras(request: NoteDeCalculRequest, chapter: ReportChapter): List<ReportBlock> {
        val slab = request.model.joint.sectionCatalog.slabs.maxByOrNull { it.htMeters }
        val gamma = request.model.joint.materials.gammaConcreteKNm3
        val selfWeight = slab?.let { topology ->
            if (topology.isSolidSlab) {
                topology.htMeters * gamma
            } else {
                val b = topology.flangeWidthBCm / 100.0
                val b0 = topology.ribWidthB0Cm / 100.0
                val h0 = topology.compressionSlabH0Cm / 100.0
                if (b > 0.0) (b * h0 + b0 * (topology.htMeters - h0)) * gamma / b else topology.htMeters * gamma
            }
        } ?: 0.0
        val finishing = request.options.finishingLoadKNPerM2
        val live = request.options.liveLoadKNPerM2
        val terraceLayers = listOf(
            Triple("Étanchéité multicouche", "DTU série 43", finishing * 0.25),
            Triple("Forme de pente", "pente 1 à 2 %", finishing * 0.40),
            Triple("Isolation thermique", "DTR BC 2.2", finishing * 0.15),
            Triple("Protection lourde", "gravillons 5 cm", finishing * 0.20)
        )
        val floorLayers = listOf(
            Triple("Revêtement de sol et chape", "DTR BC 2.2", finishing * 0.55),
            Triple("Cloisons légères", "DTR BC 2.2", finishing * 0.30),
            Triple("Enduit sous plafond", "2 cm", finishing * 0.15)
        )
        fun rows(layers: List<Triple<String, String, Double>>, imposedLive: Double): List<List<String>> {
            val total = selfWeight + layers.sumOf { it.third }
            return buildList {
                add(listOf("Poids propre du plancher", slab?.geometryLabel ?: "plancher non identifié", fmt(selfWeight)))
                layers.forEach { add(listOf(it.first, it.second, fmt(it.third))) }
                add(listOf("Total G", "somme", fmt(total)))
                add(listOf("Surcharge Q", "DTR BC 2.2", fmt(imposedLive)))
                add(listOf("ELU = 1,35 G + 1,50 Q", "combinaison fondamentale", fmt(1.35 * total + 1.50 * imposedLive)))
                add(listOf("ELS = G + Q", "combinaison de service", fmt(total + imposedLive)))
            }
        }
        val totalTerrace = selfWeight + terraceLayers.sumOf { it.third }
        val totalFloor = selfWeight + floorLayers.sumOf { it.third }
        return buildList {
            add(ReportBlock.Heading(2, "5.4 Descente de charges par couche", "s54"))
            add(
                ReportBlock.Paragraph(
                    "Les charges sont ramenées à 1,00 m² d'emprise. Le poids propre est calculé avec la géométrie " +
                        "réelle du plancher ${slab?.name ?: "non identifié"} ; les charges de revêtement et " +
                        "d'exploitation reprennent les hypothèses du projet."
                )
            )
            add(
                ReportBlock.Table(
                    "Terrasse inaccessible",
                    listOf("Couche / action", "Base de calcul", "G ou Q (kN/m²)"),
                    rows(terraceLayers, 1.0),
                    columnWidthsCm = listOf(5.5, 7.5, 3.4),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.LEFT, ReportAlign.RIGHT),
                    firstColumnBold = true
                )
            )
            add(
                ReportBlock.Table(
                    "Plancher courant",
                    listOf("Couche / action", "Base de calcul", "G ou Q (kN/m²)"),
                    rows(floorLayers, live),
                    columnWidthsCm = listOf(5.5, 7.5, 3.4),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.LEFT, ReportAlign.RIGHT),
                    firstColumnBold = true
                )
            )
            add(
                ReportBlock.Note(
                    "Les charges de finition sont réparties entre les couches selon des proportions de projet. " +
                        "La surcharge de la terrasse inaccessible est prise à 1,00 kN/m² et celle des planchers " +
                        "courants à ${fmt(live)} kN/m² (DTR BC 2.2).",
                    "Hypothèses de charges"
                )
            )
            add(
                ReportBlock.Paragraph(
                    "Synthèse : Gterrasse = ${fmt(totalTerrace)} kN/m², Qterrasse = 1,00 kN/m² ; " +
                        "Gcourant = ${fmt(totalFloor)} kN/m², Qcourant = ${fmt(live)} kN/m². " +
                        cv(if (selfWeight > 0.0) CheckStatus.OK else chapter.status),
                    italic = true,
                    sizePt = 9
                )
            )
        }
    }

    private fun synthesisBlock(chapter: ReportChapter, element: String): List<ReportBlock> = listOf(
        ReportBlock.Heading(2, "${chapter.number}.6 Synthèse et condition de vérification", "s${chapter.number}6"),
        ReportBlock.Paragraph(
            "Les dispositions géométriques des $element respectent les minima RPA 2024 et les règles " +
                "de prédimensionnement BAEL. " + cv(chapter.status),
            italic = true,
            sizePt = 9
        )
    )

    private fun slabCaptureExtras(
        request: NoteDeCalculRequest,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val slabs = request.model.joint.sectionCatalog.slabs
        return buildList {
            add(ReportBlock.Heading(2, "8.6 Capture Koka / SOCOTEC et validation", "s86"))
            addAll(
                capture(
                    "Capture de l'interface Koka / SOCOTEC montrant la géométrie de la poutrelle et les courbes de moments",
                    "La capture doit montrer la poutrelle continue ${slabs.joinToString(", ") { it.name }.ifBlank { "non identifiée" }} " +
                        "avec ses portées, les courbes de moments et la fiche de flèche. Les résultats du tableau " +
                        "précédent sont issus du même schéma de calcul. " + cv(chapter.status),
                    chapter.status,
                    placeholders
                )
            )
            addAll(conclusionOnly(chapter, "planchers et poutrelles"))
        }
    }

    private fun seismicExtras(
        seismic: Rpa2024SeismicAssessment,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val modal = seismic.modalParticipation
        val baseStatus = CheckStatus.worst(seismic.baseShear.map { it.status })
        val columnStatus = CheckStatus.worst(seismic.columnReducedNormalForce.map { it.status })
        val driftStatus = CheckStatus.worst(seismic.driftChecks.map { it.status })
        val pDeltaStatus = CheckStatus.worst(seismic.pDeltaChecks.map { it.status })
        val maxColumn = seismic.columnReducedNormalForce.maxByOrNull { it.nu }
        val maxDrift = seismic.driftChecks.maxByOrNull { it.utilization }
        val maxPDelta = seismic.pDeltaChecks.maxByOrNull { it.theta }
        val baseText = seismic.baseShear.joinToString(" ; ") { shear ->
            "${shear.directionLabel} : V_MSE = ${shear.staticBaseShearKN?.let { fmt(it, 2) } ?: "n/d"} kN, " +
                "V_MDS = ${shear.dynamicBaseShearKN?.let { fmt(it, 2) } ?: "n/d"} kN, " +
                "ratio = ${shear.dynamicToStaticRatio?.let { fmt(it * 100.0, 1) } ?: "n/d"} %"
        }
        val columnText = maxColumn?.let {
            "Effort normal réduit maximal ν = ${fmt(it.nu, 3)} pour ${it.element} " +
                "(${it.familyName}), limite 0,35."
        } ?: "Aucun effort normal sismique exploitable."
        val driftText = maxDrift?.let {
            "Déplacement inter-étages maximal au niveau ${it.story} : νA·Δ = " +
                "${fmt(it.reducedDriftMeters * 1000.0, 2)} mm pour " +
                "${fmt(it.governingLimitMeters * 1000.0, 2)} mm admissibles."
        } ?: "Déplacements inter-étages indisponibles."
        val pDeltaText = maxPDelta?.let {
            "Indice P-Δ maximal θ = ${fmt(it.theta, 3)} au niveau ${it.story} (limite 0,10)."
        } ?: "Effet P-Delta non calculable."
        return buildList {
            add(ReportBlock.Heading(2, "9.8 Captures ETABS et validation sismique", "s98"))
            addAll(
                capture(
                    "Tableau Modal Participating Mass Ratios issu d'ETABS (modes, périodes, UX, UY, sommes UX/UY/RZ)",
                    "Le tableau doit montrer ${modal.modeCount} mode(s), ΣUX = ${fmt(modal.sumUxPercent, 1)} %, " +
                        "ΣUY = ${fmt(modal.sumUyPercent, 1)} % et la période Tk = " +
                        "${modal.lastModePeriodSeconds?.let { fmt(it, 3) } ?: "n/d"} s. " + cv(modal.status),
                    modal.status,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Tableau Response Spectrum Base Reactions et Story Shears d'ETABS",
                    "La capture doit reprendre les réactions et efforts tranchants d'étage utilisés pour le " +
                        "recalage MSE/MDS : $baseText. " + cv(baseStatus),
                    baseStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Tableau récapitulatif des efforts normaux Poteaux sous combinaisons sismiques",
                    "$columnText " + cv(columnStatus),
                    columnStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Tableau Diaphragm CM Displacements issu d'ETABS",
                    "$driftText " + cv(driftStatus),
                    driftStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Tableau récapitulatif de l'effet P-Delta (Pk, Dt, Vk, hk, theta_k)",
                    "$pDeltaText " + cv(pDeltaStatus),
                    pDeltaStatus,
                    placeholders
                )
            )
            add(
                ReportBlock.Paragraph(
                    "Synthèse sismique : ${seismic.baseShear.size} direction(s), " +
                        "${seismic.driftChecks.size} contrôle(s) de déplacement et " +
                        "${seismic.pDeltaChecks.size} contrôle(s) P-Δ. " + cv(chapter.status),
                    italic = true,
                    sizePt = 9
                )
            )
        }
    }

    private fun structuralExtras(
        columns: ColumnDesignSet,
        beams: BeamDesignSet,
        walls: WallDesignSet,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val worstColumn = columns.columns.maxByOrNull { it.requiredAsCm2 }
        val worstBeam = beams.beams.maxByOrNull { it.family.maxClearSpanMeters }
        val wallList = walls.walls
        val wallText = if (wallList.isEmpty()) {
            "Aucune famille de voile n'est présente dans le modèle ETABS importé : les captures 10.3 " +
                "doivent être produites après ajout des voiles au modèle."
        } else {
            val worst = wallList.maxByOrNull { it.qualification.tauMPa }
            "Voiles : ${wallList.size} famille(s), effort normal réduit maximal νd = " +
                "${fmt(worst?.qualification?.normalizedAxial ?: 0.0, 3)}, contrainte tangente maximale τb = " +
                "${fmt(worst?.qualification?.tauMPa ?: 0.0, 3)} MPa."
        }
        val wallStatus = if (wallList.isEmpty()) CheckStatus.NOT_APPLICABLE else walls.status
        return buildList {
            add(ReportBlock.Heading(2, "10.7 Captures de calcul et plans de ferraillage", "s107"))
            addAll(
                capture(
                    "Fiche de calcul BaelR (Poteau) et tableau des sollicitations critiques d'ETABS",
                    "Le poteau le plus sollicité ${worstColumn?.family?.name ?: "n/d"} présente As,calcul = " +
                        "${worstColumn?.let { fmt(it.requiredAsCm2) } ?: "n/d"} cm² et As,adopté = " +
                        "${worstColumn?.let { fmt(it.adoptedAsCm2) } ?: "n/d"} cm². " + cv(columns.status),
                    columns.status,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Schéma d'élévation de l'armature transversale d'un poteau coté",
                    "Le schéma doit coter lcl, Lcr, Lrec et les espacements t/t' des cadres. " + cv(columns.status),
                    columns.status,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Fiches de calcul BaelR (Poutre travée/appui) et Tableau de vérification à l'effort tranchant",
                    "La poutre dimensionnante ${worstBeam?.family?.name ?: "n/d"} porte " +
                        "${worstBeam?.let { fmt(it.family.maxClearSpanMeters) } ?: "n/d"} m ; " +
                        "As,inf = ${worstBeam?.let { fmt(it.bottomAdoptedAsCm2) } ?: "n/d"} cm² et " +
                        "As,sup = ${worstBeam?.let { fmt(it.topAdoptedAsCm2) } ?: "n/d"} cm². " + cv(beams.status),
                    beams.status,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Schéma d'élévation et coupe de l'armature d'une poutre cotée",
                    "Le schéma doit coter la portée, la hauteur, le premier cadre à 5 cm et la zone nodale l' = 1,5 h. " +
                        cv(beams.status),
                    beams.status,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Tableaux Pier Forces (P, V2, M3) pour les voiles de contreventement",
                    wallText + " " + cv(wallStatus),
                    wallStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Graphiques des enveloppes de calcul pour les moments fléchissants (décalés de hcr) et efforts tranchants (amplifiés)",
                    "Les graphiques doivent être établis pour les voiles réellement présents dans le modèle. " +
                        cv(wallStatus),
                    wallStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Fiche de calcul et ferraillage des voiles en zone critique (Éléments de rive et âme)",
                    wallText + " " + cv(wallStatus),
                    wallStatus,
                    placeholders
                )
            )
            add(
                ReportBlock.Paragraph(
                    "Synthèse du chapitre 10 : ${columns.columns.size} famille(s) de poteaux, " +
                        "${beams.beams.size} famille(s) de poutres et ${wallList.size} famille(s) de voiles. " +
                        cv(chapter.status),
                    italic = true,
                    sizePt = 9
                )
            )
        }
    }

    private fun staircaseExtras(
        request: NoteDeCalculRequest,
        chapter: ReportChapter
    ): List<ReportBlock> {
        val geometry = request.staircaseGeometry
        val details = if (geometry == null) {
            "Aucune dalle inclinée n'a été identifiée dans le modèle ETABS. La géométrie de l'escalier doit " +
                "être fournie au moteur pour compléter l'étude."
        } else {
            "L'escalier ${geometry.name} comporte ${geometry.numberOfRisers} contremarches de " +
                "${fmt(geometry.riserHeightMeters * 100.0, 1)} cm et un giron de " +
                "${fmt(geometry.treadDepthMeters * 100.0, 1)} cm ; la valeur de Blondel vaut " +
                "${fmt(geometry.blondelValueMeters * 100.0, 1)} cm, la portée de calcul " +
                "${fmt(geometry.designSpanMeters)} m et l'épaisseur de paillasse " +
                "${fmt(geometry.waistThicknessMeters * 100.0, 0)} cm."
        }
        return listOf(
            ReportBlock.Heading(2, "12.9 Synthèse de l'étude de l'escalier", "s129"),
            ReportBlock.Paragraph(
                "$details La paillasse est vérifiée en flexion composée et en flèche selon le BAEL 91 et " +
                    "les surcharges du DTR BC 2.2. " + cv(chapter.status),
                italic = true,
                sizePt = 9
            )
        )
    }

    private fun foundationExtras(
        footings: StripFootingDesignSet?,
        request: NoteDeCalculRequest,
        chapter: ReportChapter,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        val geotech = request.foundations?.geotechnical
        val maxStress = footings?.soilChecks?.maxByOrNull { it.result.effectiveStressKNM2 }?.result?.effectiveStressKNM2
        val footingStatus = footings?.status ?: CheckStatus.NOT_APPLICABLE
        val foundationText = if (footings == null || footings.footings.isEmpty()) {
            "Aucune semelle n'est disponible dans le modèle SAFE importé."
        } else {
            "${footings.footings.size} famille(s) de semelle filante, Ks = " +
                "${geotech?.subgradeModulusKNM3?.let { fmt(it, 0) } ?: "n/d"} kN/m³, " +
                "σ̄sol = ${geotech?.allowableStressKNM2?.let { fmt(it, 1) } ?: "n/d"} kN/m², " +
                "contrainte effective maximale = ${maxStress?.let { fmt(it, 2) } ?: "n/d"} kN/m²."
        }
        return buildList {
            add(ReportBlock.Heading(2, "13.6 Captures SAFE et validation des fondations", "s136"))
            addAll(
                capture(
                    "Vue SAFE montrant le modèle de semelles filantes / radier et les propriétés des ressorts K120/K160",
                    "$foundationText " + cv(footingStatus),
                    footingStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Diagramme des moments fléchissants (Beam Major Moment Diagram) des nervures de fondation dans SAFE",
                    "Le diagramme doit confirmer les enveloppes de moment utilisées pour le ferraillage en appui " +
                        "et en travée. " + cv(footingStatus),
                    footingStatus,
                    placeholders
                )
            )
            addAll(
                capture(
                    "Fiches BaelR / BaelT (Section en Té) pour le ferraillage des semelles filantes en appui et en travée, y compris au droit des joints",
                    "Les fiches doivent correspondre aux familles ${footings?.footings?.map { it.family.name }?.joinToString(", ") ?: "n/d"} " +
                        "et aux sections en Té inversé ou en L définies dans SAFE. " + cv(footingStatus),
                    footingStatus,
                    placeholders
                )
            )
            addAll(conclusionOnly(chapter, "fondations"))
        }
    }

    private fun conclusionOnly(chapter: ReportChapter, subject: String): List<ReportBlock> = listOf(
        ReportBlock.Heading(2, "${chapter.number}.9 Synthèse de vérification", "s${chapter.number}9"),
        ReportBlock.Paragraph(
            "Les vérifications de $subject sont synthétisées dans les tableaux et figures du chapitre. " +
                cv(chapter.status),
            italic = true,
            sizePt = 9
        )
    )

    private fun capture(
        tag: String,
        interpretation: String,
        status: CheckStatus,
        placeholders: MutableList<String>
    ): List<ReportBlock> {
        placeholders += tag
        return listOf(
            ReportBlock.Paragraph(
                "[FIGURE_CAPTURE_LOGICIEL : $tag]",
                bold = true,
                align = ReportAlign.CENTER,
                sizePt = 9,
                colorHex = "1F3A5F",
                spaceBeforePt = 140,
                keepWithNext = true
            ),
            ReportBlock.Paragraph(
                "Interprétation : $interpretation",
                italic = true,
                sizePt = 9,
                spaceAfterPt = 130
            )
        )
    }

    private fun cv(status: CheckStatus): String = when (status) {
        CheckStatus.OK -> "CV — Condition Vérifiée."
        CheckStatus.WARNING -> "CV — Condition Vérifiée sous réserve des points signalés."
        CheckStatus.FAILED -> "CV — Condition non vérifiée : correction requise."
        CheckStatus.NOT_APPLICABLE -> "CV — Condition non applicable faute de données ou d'élément."
    }

    private fun spectrumTypeLabel(type: SpectrumType?): String = when (type) {
        SpectrumType.TYPE_1 -> "Type 1"
        SpectrumType.TYPE_2 -> "Type 2"
        null -> "non disponible"
    }

    private fun fmt(value: Double, decimals: Int = 2): String =
        if (value.isFinite()) ReportText.plain(value, decimals) else "n/d"
}
