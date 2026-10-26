package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.foundations.BaelTSectionSolver
import com.archilab.rpa2024.engine.foundations.BaelTInput
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.foundations.StripFootingDesignSet
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.BeamDesignSet
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignSet
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.GeometryAndPreSizingEngine
import com.archilab.rpa2024.engine.geometry.JointAssessment
import com.archilab.rpa2024.engine.geometry.ColumnForceInput
import com.archilab.rpa2024.engine.secondary.ParapetDesignInput
import com.archilab.rpa2024.engine.secondary.ParapetDesignResult
import com.archilab.rpa2024.engine.secondary.ParapetGeometry
import com.archilab.rpa2024.engine.secondary.ParapetWallDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseDesignInput
import com.archilab.rpa2024.engine.secondary.StaircaseDesignResult
import com.archilab.rpa2024.engine.secondary.StaircaseDesigner
import com.archilab.rpa2024.engine.secondary.StaircaseExtractor
import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicAssessment
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngineOptions
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.engine.slabs.ContinuousSlabInput
import com.archilab.rpa2024.engine.slabs.ContinuousSlabResult
import com.archilab.rpa2024.engine.walls.WallCriticalZoneAndDuctilityDesigner
import com.archilab.rpa2024.engine.walls.WallDesignSet
import com.archilab.rpa2024.engine.walls.labelFr
import com.archilab.rpa2024.graphics.GraphicOutput
import com.archilab.rpa2024.graphics.ScientificChartRenderer
import com.archilab.rpa2024.graphics.Structure3DAndPlanRenderer
import com.archilab.rpa2024.graphics.TechnicalSheetImageGenerator
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.ResolvedSeismicConfig
import com.archilab.rpa2024.normative.Rpa2024NormativeDb
import com.archilab.rpa2024.normative.SpectrumType
import com.archilab.rpa2024.parser.AreaKind
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.FrameKind
import com.archilab.rpa2024.parser.ParsedEtabsModel
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

/** Parametres de composition de la note de calcul. */
data class NoteDeCalculOptions(
    val liveLoadKNPerM2: Double = 1.5,
    val finishingLoadKNPerM2: Double = 1.2,
    val permanentSuperLoadKNPerM2: Double = 0.0,
    val renderDpi: Double = 300.0,
    val maxPlanFigures: Int = 6,
    val maxModeRows: Int = 12,
    val maxCheckRows: Int = 80,
    val manualSeismicWeightKN: Double? = null
)

/** Demande de generation d'une note de calcul pour un joint importe. */
data class NoteDeCalculRequest(
    val model: ParsedEtabsModel,
    val results: EtabsResults = EtabsResults(),
    val foundations: SafeFoundationModel? = null,
    val projectTitle: String = "Projet",
    val wilaya: String = "",
    val client: String = "",
    val engineer: String = "",
    val reference: String = "",
    val revision: String = "A",
    val parapetGeometry: ParapetGeometry = ParapetGeometry(),
    val staircaseGeometry: StaircaseGeometry? = null,
    val options: NoteDeCalculOptions = NoteDeCalculOptions()
)

/** Resultat de la composition : document structure + images PNG 300 DPI indexees. */
data class NoteDeCalculArtifacts(
    val document: ReportDocument,
    val figures: Map<String, ByteArray>
)

/**
 * Composition de la note de calcul CTC en treize chapitres. Tous les chapitres sont
 * alimentes par les moteurs de calcul a partir du modele ETABS/SAFE importe : aucune
 * dimension, aucun nom d'element et aucun effort ne sont codes en dur.
 */
class NoteDeCalculBundle(
    private val geometryEngine: GeometryAndPreSizingEngine = GeometryAndPreSizingEngine(),
    private val seismicEngine: Rpa2024SeismicEngine = Rpa2024SeismicEngine(),
    private val slabSolver: ContinuousRibbedSlabSolver = ContinuousRibbedSlabSolver(),
    private val columnEngine: ColumnDesignEngine = ColumnDesignEngine(),
    private val beamEngine: BeamDesignEngine = BeamDesignEngine(),
    private val wallEngine: WallCriticalZoneAndDuctilityDesigner = WallCriticalZoneAndDuctilityDesigner(),
    private val parapetDesigner: ParapetWallDesigner = ParapetWallDesigner(),
    private val staircaseDesigner: StaircaseDesigner = StaircaseDesigner(),
    private val staircaseExtractor: StaircaseExtractor = StaircaseExtractor(),
    private val foundationOrchestrator: StripFootingDesignOrchestrator = StripFootingDesignOrchestrator(),
    private val baelTSolver: BaelTSectionSolver = BaelTSectionSolver(),
    private val renderer: Structure3DAndPlanRenderer = Structure3DAndPlanRenderer(),
    private val charts: ScientificChartRenderer = ScientificChartRenderer(),
    private val sheets: TechnicalSheetImageGenerator = TechnicalSheetImageGenerator()
) {

    fun build(request: NoteDeCalculRequest): NoteDeCalculArtifacts {
        val joint = request.model.joint
        val materials = joint.materials
        val resolved = Rpa2024NormativeDb.resolveSeismic(joint.seismicParams, joint)
        val geometry = geometryEngine.assess(
            request.model,
            request.results,
            request.options.manualSeismicWeightKN
        )
        val seismic = seismicEngine.assess(
            joint,
            request.results,
            joint.seismicParams,
            Rpa2024SeismicEngineOptions(manualSeismicWeightKN = request.options.manualSeismicWeightKN)
        )
        val slabs = solveSlabs(joint, materials, request.options)
        val columns = columnEngine.design(joint, request.results)
        val beams = beamEngine.design(joint, request.results)
        val walls = wallEngine.design(joint, request.results)
        val parapet = parapetDesigner.design(
            ParapetDesignInput(
                geometry = request.parapetGeometry,
                materials = materials,
                zoneAccelerationA = resolved.zone.coeffA
            )
        )
        val stairGeometries = request.staircaseGeometry?.let { listOf(it) }
            ?: staircaseExtractor.fromModel(request.model)
        val stairs = stairGeometries.map { staircaseDesigner.design(StaircaseDesignInput(geometry = it, materials = materials)) }
        val foundations = request.foundations?.let { foundationOrchestrator.design(it, materials) }

        val context = BuildContext(
            request = request,
            joint = joint,
            resolved = resolved,
            geometry = geometry,
            seismic = seismic,
            slabs = slabs,
            columns = columns,
            beams = beams,
            walls = walls,
            parapet = parapet,
            stairs = stairs,
            foundations = foundations
        )

        val chapters = listOf(
            context.descriptionChapter(),
            context.geometryChapter(),
            context.materialsChapter(),
            context.combinationsChapter(),
            context.loadChapter(),
            context.beamPreSizingChapter(),
            context.columnPreSizingChapter(),
            context.slabChapter(),
            context.seismicChapter(),
            context.structuralElementsChapter(),
            context.parapetChapter(),
            context.staircaseChapter(),
            context.foundationChapter()
        )
        val annexes = context.annexes(chapters)

        val document = ReportDocument(
            cover = context.cover(),
            chapters = chapters,
            annexes = annexes,
            warnings = (request.model.warnings + request.results.warnings + geometry.warnings +
                seismic.warnings + foundations?.warnings.orEmpty()).distinct(),
            generatedOn = LocalDate.now()
        )
        return NoteDeCalculArtifacts(document, context.figures.toMap())
    }

    private fun solveSlabs(
        joint: StructuralJoint,
        materials: MaterialConfig,
        options: NoteDeCalculOptions
    ): List<ContinuousSlabResult> = joint.sectionCatalog.slabs
        .filter { it.continuousSpansMeters.isNotEmpty() }
        .mapNotNull { topology ->
            val selfWeight = topology.htMeters * materials.gammaConcreteKNm3
            runCatching {
                slabSolver.solve(
                    ContinuousSlabInput(
                        topology = topology,
                        permanentLoadKNPerM2 = selfWeight + options.finishingLoadKNPerM2 + options.permanentSuperLoadKNPerM2,
                        variableLoadKNPerM2 = options.liveLoadKNPerM2,
                        materials = materials,
                        permanentBeforeFinishesKNPerM2 = selfWeight,
                        leftCantileverMeters = topology.leftCantileverMeters,
                        rightCantileverMeters = topology.rightCantileverMeters
                    )
                )
            }.getOrNull()
        }

    private inner class BuildContext(
        val request: NoteDeCalculRequest,
        val joint: StructuralJoint,
        val resolved: ResolvedSeismicConfig,
        val geometry: JointAssessment,
        val seismic: Rpa2024SeismicAssessment,
        val slabs: List<ContinuousSlabResult>,
        val columns: ColumnDesignSet,
        val beams: BeamDesignSet,
        val walls: WallDesignSet,
        val parapet: ParapetDesignResult,
        val stairs: List<StaircaseDesignResult>,
        val foundations: StripFootingDesignSet?
    ) {

        val figures = linkedMapOf<String, ByteArray>()
        private val materials: MaterialConfig get() = joint.materials
        private val model: ParsedEtabsModel get() = request.model
        private val results: EtabsResults get() = request.results
        private val catalog get() = joint.sectionCatalog
        private val options get() = request.options
        private val dpi get() = options.renderDpi

        // ---------------------------------------------------------------- page de garde

        fun cover(): ReportCover {
            val view3d = runCatching {
                val output = renderer.renderIsometric(model, "Vue 3D — ${joint.jointName}", dpi)
                figures["couverture_3d"] = output.png
                output.png
            }.getOrNull()

            val summary = listOf(
                "Projet" to request.projectTitle,
                "Joint étudié" to joint.jointName,
                "Wilaya" to request.wilaya.ifBlank { "—" },
                "Zone sismique" to "${resolved.zone.code} — A = ${ReportText.plain(resolved.zone.coeffA, 2)} g",
                "Site" to "${resolved.siteClass.code} — ${spectrumTypeLabel(resolved.spectralParams?.spectrumType)}",
                "Groupe d'importance" to "${resolved.importance.code} — I = ${ReportText.plain(resolved.importance.coeffI, 2)}",
                "Système de contreventement" to resolved.bracingSystem.description,
                "Coefficient de comportement" to "R = ${ReportText.plain(resolved.behaviorFactorR, 2)}",
                "Niveaux principaux" to "${joint.storySystem.numStoriesN}",
                "Hauteur Hn" to "${ReportText.plain(joint.storySystem.totalHeightHn, 2)} m",
                "Éléments modélisés" to "${catalog.totalElementCount}",
                "Synthèse générale" to ReportText.statusLabel(globalStatus())
            )

            return ReportCover(
                wilaya = request.wilaya.ifBlank { "—" },
                projectTitle = request.projectTitle,
                jointName = joint.jointName,
                client = request.client.ifBlank { "—" },
                engineer = request.engineer.ifBlank { "—" },
                reference = request.reference.ifBlank { "NDC-${joint.jointName.replace(' ', '-')}-RPA2024" },
                revision = request.revision,
                dateLabel = ReportText.date(LocalDate.now()),
                view3d = view3d,
                summary = summary
            )
        }

        private fun globalStatus(): CheckStatus = CheckStatus.worst(
            listOf(
                geometry.overallStatus,
                seismic.overallStatus,
                CheckStatus.worst(slabs.map { it.status }),
                columns.status,
                beams.status,
                walls.status,
                parapet.status
            ) + foundations?.status.let { listOfNotNull(it) }.orEmpty()
        )

        // ---------------------------------------------------------------- chapitre 1

        fun descriptionChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "1.1 Présentation du projet", "s11")
            blocks += ReportBlock.KeyValues(
                "Identification de l'opération",
                listOf(
                    "Intitulé du projet" to request.projectTitle,
                    "Wilaya" to request.wilaya.ifBlank { "—" },
                    "Joint structural" to joint.jointName,
                    "Maître d'ouvrage" to request.client.ifBlank { "—" },
                    "Bureau d'études" to request.engineer.ifBlank { "—" },
                    "Référence de la note" to request.reference.ifBlank { "NDC-${joint.jointName}" },
                    "Indice de révision" to request.revision,
                    "Date d'établissement" to ReportText.date(LocalDate.now())
                )
            )
            blocks += ReportBlock.Paragraph(
                "La présente note de calcul établit la justification structurelle du joint « ${joint.jointName} ». " +
                    "Le modèle a été reconstruit automatiquement à partir de l'export ETABS : les niveaux, la trame, " +
                    "les sections et les efforts sont lus dans le fichier source, sans reprise manuelle de cotes. " +
                    "Les vérifications suivent le RPA 2024 (DTR BC 2.48), le BAEL 91 révisé 99 et le CBA 93."
            )
            blocks += ReportBlock.Heading(2, "1.2 Consistance du modèle", "s12")
            blocks += ReportBlock.Paragraph(
                "Le joint comporte ${joint.storySystem.numStoriesN} niveaux principaux" +
                    (if (joint.storySystem.penthouseStories.isNotEmpty()) {
                        " et ${joint.storySystem.penthouseStories.size} édicule(s) exclu(s) du calcul sismique"
                    } else "") +
                    ", pour une hauteur Hn de ${ReportText.plain(joint.storySystem.totalHeightHn, 2)} m. " +
                    "La trame est de ${catalog.totalElementCount} éléments structuraux répartis en " +
                    "${catalog.columns.size} famille(s) de poteaux, ${catalog.beams.size} famille(s) de poutres, " +
                    "${catalog.shearWalls.size} voile(s) et ${catalog.slabs.size} plancher(s)."
            )
            blocks += ReportBlock.Table(
                "Consistance du modèle ETABS",
                listOf("Désignation", "Nombre", "Détail"),
                listOf(
                    listOf("Niveaux principaux", "${joint.storySystem.numStoriesN}", "h = ${ReportText.plain(joint.storySystem.totalHeightHn, 2)} m"),
                    listOf("Niveaux annexes", "${joint.storySystem.penthouseStories.size}", "édicules et cages"),
                    listOf("Poteaux", "${model.frames.count { it.kind == FrameKind.COLUMN }}", "${catalog.columns.size} famille(s)"),
                    listOf("Poutres", "${model.frames.count { it.kind == FrameKind.BEAM }}", "${catalog.beams.size} famille(s)"),
                    listOf("Voiles", "${model.areas.count { it.kind == AreaKind.WALL }}", "${catalog.shearWalls.size} famille(s)"),
                    listOf("Planchers", "${model.areas.count { it.kind == AreaKind.SLAB }}", "${catalog.slabs.size} famille(s)"),
                    listOf("Nœuds", "${model.points.size}", "coordonnées lues dans l'export"),
                    listOf("Sections", "${model.sections.size}", "béton et acier")
                ),
                columnWidthsCm = listOf(5.0, 2.6, 8.9),
                alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.LEFT)
            )
            blocks += ReportBlock.Heading(2, "1.3 Références réglementaires", "s13")
            blocks += ReportBlock.Bullets(
                listOf(
                    "RPA 2024 — DTR BC 2.48 : règlement parasismique des constructions, version 2024.",
                    "BAEL 91 révisé 99 : règles de calcul et de conception des ouvrages en béton armé.",
                    "CBA 93 : règles d'emploi du béton armé.",
                    "DTR BC 2.2 : charges permanentes et surcharges d'exploitation.",
                    "Export ETABS/SAFE du modèle et des tables de résultats (forces, déplacements, réactions)."
                )
            )
            blocks += ReportBlock.Heading(2, "1.4 Hypothèses générales", "s14")
            blocks += ReportBlock.Bullets(
                listOf(
                    "Les efforts sont issus des combinaisons ETABS ; les catégories ELU, ELS et ELA sont identifiées par analyse des noms de combinaisons.",
                    "Le béton est de classe ${materials.concreteClass} (fc28 = ${ReportText.plain(materials.fc28MPa, 1)} MPa) et l'acier de nuance ${materials.steelGrade} (fe = ${ReportText.plain(materials.feMPa, 0)} MPa).",
                    "La structure est supposée régulière en plan et en élévation ; en cas d'irrégularité, les majorations du RPA 2024 sont à appliquer au niveau du modèle ETABS.",
                    "Les résultats ETABS sont supposés obtenus avec une analyse tridimensionnelle prenant en compte la torsion accidentelle."
                )
            )
            if (request.model.warnings.isNotEmpty()) {
                blocks += ReportBlock.Note(
                    "L'import a signalé ${request.model.warnings.size} avertissement(s) : " +
                        request.model.warnings.take(6).joinToString(" ; ") +
                        if (request.model.warnings.size > 6) " …" else "",
                    "Avertissements d'import"
                )
            }
            val view = runCatching { renderer.renderIsometric(model, "Vue 3D isométrique — ${joint.jointName}", dpi) }.getOrNull()
            if (view != null) {
                blocks += figure("vue_3d", view, "Figure 1.1 — Vue 3D isométrique du joint ${joint.jointName}", 16.5)
            }
            return ReportChapter(
                1,
                "Description générale",
                "Présentation du projet, consistance du modèle ETABS et références réglementaires.",
                geometry.chapters.firstOrNull { it.number == 1 }?.status ?: CheckStatus.OK,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 2

        fun geometryChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            val stories = joint.storySystem.orderedStories
            blocks += ReportBlock.Heading(2, "2.1 Niveaux et hauteurs", "s21")
            blocks += ReportBlock.Paragraph(
                "Les niveaux sont reconstruits depuis la table Stories d'ETABS. Les édicules et cages d'escalier " +
                    "sont isolés afin d'être exclus de la hauteur Hn et du nombre de niveaux N utilisés par la " +
                    "méthode statique équivalente."
            )
            blocks += ReportBlock.Table(
                "Niveaux du joint",
                listOf("Niveau", "Hauteur hk (m)", "Altitude zk (m)", "Nature"),
                stories.map { level ->
                    listOf(
                        level.storyName,
                        ReportText.plain(level.heightHk, 2),
                        ReportText.plain(level.elevationZk, 2),
                        if (level.isPenthouseOrCage) "Édicule / cage" else "Courant"
                    )
                },
                columnWidthsCm = listOf(4.5, 4.0, 4.0, 4.0),
                alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.CENTER, ReportAlign.CENTER)
            )
            blocks += ReportBlock.KeyValues(
                "Synthèse géométrique",
                listOf(
                    "Nombre de niveaux N" to "${joint.storySystem.numStoriesN}",
                    "Hauteur totale Hn" to "${ReportText.plain(joint.storySystem.totalHeightHn, 2)} m",
                    "Hauteur d'étage maximale" to "${ReportText.plain(joint.storySystem.maxStoryHeightMeters, 2)} m",
                    "Longueur de trame Lx" to "${ReportText.plain(joint.gridSystem.totalLengthLx, 2)} m",
                    "Longueur de trame Ly" to "${ReportText.plain(joint.gridSystem.totalLengthLy, 2)} m",
                    "Travée maximale X" to "${ReportText.plain(joint.gridSystem.maxSpanX, 2)} m",
                    "Travée maximale Y" to "${ReportText.plain(joint.gridSystem.maxSpanY, 2)} m"
                )
            )

            blocks += ReportBlock.Heading(2, "2.2 Trame et files d'axes", "s22")
            val gridRows = mutableListOf<List<String>>()
            joint.gridSystem.sortedXGrids.forEachIndexed { index, line ->
                val next = joint.gridSystem.sortedXGrids.getOrNull(index + 1)
                gridRows += listOf(
                    "X", line.label, ReportText.plain(line.coordinateMeters, 2),
                    next?.let { ReportText.plain(it.coordinateMeters - line.coordinateMeters, 2) } ?: "—"
                )
            }
            joint.gridSystem.sortedYGrids.forEachIndexed { index, line ->
                val next = joint.gridSystem.sortedYGrids.getOrNull(index + 1)
                gridRows += listOf(
                    "Y", line.label, ReportText.plain(line.coordinateMeters, 2),
                    next?.let { ReportText.plain(it.coordinateMeters - line.coordinateMeters, 2) } ?: "—"
                )
            }
            blocks += ReportBlock.Table(
                "Files d'axes et portées",
                listOf("Direction", "Axe", "Abscisse (m)", "Portée suivante (m)"),
                gridRows,
                columnWidthsCm = listOf(3.0, 3.0, 5.0, 5.5),
                alignments = listOf(ReportAlign.CENTER, ReportAlign.CENTER, ReportAlign.CENTER, ReportAlign.CENTER)
            )

            blocks += ReportBlock.Heading(2, "2.3 Familles de sections", "s23")
            if (catalog.columns.isNotEmpty()) {
                blocks += ReportBlock.Paragraph("Poteaux :", bold = true, align = ReportAlign.LEFT, keepWithNext = true)
                blocks += ReportBlock.Table(
                    "Familles de poteaux",
                    listOf("Poteau", "Section (cm)", "Lcl (m)", "Élancement", "Éléments"),
                    catalog.columns.map { family ->
                        listOf(
                            family.name,
                            family.geometryLabel,
                            ReportText.plain(family.clearHeightLclMeters, 2),
                            ReportText.plain(family.slendernessMinDimensionOverLcl, 1),
                            "${family.elementIds.size}"
                        )
                    },
                    columnWidthsCm = listOf(3.8, 4.6, 2.6, 2.8, 2.7)
                )
            }
            if (catalog.beams.isNotEmpty()) {
                blocks += ReportBlock.Paragraph("Poutres :", bold = true, align = ReportAlign.LEFT, keepWithNext = true)
                blocks += ReportBlock.Table(
                    "Familles de poutres",
                    listOf("Poutre", "Section (cm)", "Portée max (m)", "Continue", "Éléments"),
                    catalog.beams.map { family ->
                        listOf(
                            family.name,
                            family.geometryLabel,
                            ReportText.plain(family.maxClearSpanMeters, 2),
                            if (family.isContinuous) "Oui" else "Non",
                            "${family.elementIds.size}"
                        )
                    },
                    columnWidthsCm = listOf(3.8, 4.6, 3.0, 2.4, 2.7)
                )
            }
            if (catalog.shearWalls.isNotEmpty()) {
                blocks += ReportBlock.Paragraph("Voiles :", bold = true, align = ReportAlign.LEFT, keepWithNext = true)
                blocks += ReportBlock.Table(
                    "Familles de voiles",
                    listOf("Voile", "lw (m)", "bw (m)", "he (m)", "Hw (m)", "Classification"),
                    catalog.shearWalls.map { family ->
                        listOf(
                            family.pierName,
                            ReportText.plain(family.lengthLwMeters, 2),
                            ReportText.plain(family.thicknessBwMeters, 2),
                            ReportText.plain(family.clearHeightHeMeters, 2),
                            ReportText.plain(family.totalHeightHwMeters, 2),
                            family.classification.labelFr()
                        )
                    },
                    columnWidthsCm = listOf(3.6, 2.2, 2.2, 2.2, 2.2, 4.1)
                )
            }
            if (catalog.slabs.isNotEmpty()) {
                blocks += ReportBlock.Paragraph("Planchers :", bold = true, align = ReportAlign.LEFT, keepWithNext = true)
                blocks += ReportBlock.Table(
                    "Familles de planchers",
                    listOf("Plancher", "ht (cm)", "h0 (cm)", "b0 (cm)", "B (cm)", "Portée max (m)"),
                    catalog.slabs.map { slab ->
                        listOf(
                            slab.name,
                            ReportText.plain(slab.totalThicknessHtCm, 1),
                            ReportText.plain(slab.compressionSlabH0Cm, 1),
                            ReportText.plain(slab.ribWidthB0Cm, 1),
                            ReportText.plain(slab.flangeWidthBCm, 1),
                            ReportText.plain(slab.maxSpanMeters, 2)
                        )
                    },
                    columnWidthsCm = listOf(3.8, 2.3, 2.3, 2.3, 2.3, 3.5)
                )
            }

            blocks += ReportBlock.Heading(2, "2.4 Plans de coffrage", "s24")
            blocks += ReportBlock.Paragraph(
                "Le plan de coffrage est régénéré pour chaque niveau à partir des files, des poteaux, des poutres " +
                    "et des voiles du modèle. Les bulles d'axes et les portées sont cotées automatiquement."
            )
            stories.take(options.maxPlanFigures).forEach { level ->
                runCatching {
                    renderer.renderPlan(
                        model,
                        level.storyName,
                        "Plan de coffrage — ${level.storyName} (z = ${ReportText.plain(level.elevationZk, 2)} m)",
                        dpi
                    )
                }.getOrNull()?.let { output ->
                    blocks += figure(
                        "plan_${level.storyName}",
                        output,
                        "Figure 2.${stories.indexOf(level) + 1} — Plan de coffrage du niveau ${level.storyName}",
                        16.5
                    )
                }
            }
            return ReportChapter(
                2,
                "Géométrie du joint",
                "Niveaux, trame, familles de sections et plans de coffrage reconstruits depuis ETABS.",
                geometry.chapters.firstOrNull { it.number == 2 }?.status ?: CheckStatus.OK,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 3

        fun materialsChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "3.1 Béton", "s31")
            blocks += ReportBlock.Paragraph(
                "Le béton est défini par sa classe ${materials.concreteClass}. Les résistances caractéristiques " +
                    "et les modules de déformation sont calculés selon le BAEL 91 révisé 99 à partir de fc28."
            )
            blocks += ReportBlock.Table(
                "Caractéristiques du béton",
                listOf("Grandeur", "Symbole", "Valeur", "Unité"),
                listOf(
                    listOf("Résistance caractéristique à 28 jours", "fc28", ReportText.plain(materials.fc28MPa, 1), "MPa"),
                    listOf("Résistance en traction", "ft28", ReportText.plain(materials.ft28MPa, 2), "MPa"),
                    listOf("Module de déformation instantané", "Eij", ReportText.num(materials.eijMPa, 0), "MPa"),
                    listOf("Module de déformation différé", "Evj", ReportText.num(materials.evjMPa, 0), "MPa"),
                    listOf("Rapport Eij / Evj", "—", ReportText.plain(materials.eijOverEvj, 2), "—"),
                    listOf("Résistance de calcul ELU", "fbu", ReportText.plain(materials.fbuDurableMPa, 2), "MPa"),
                    listOf("Résistance de calcul accidentelle", "fbu,acc", ReportText.plain(materials.fbuAccidentalMPa, 2), "MPa"),
                    listOf("Poids volumique", "γb", ReportText.plain(materials.gammaConcreteKNm3, 1), "kN/m³"),
                    listOf("Dosage en ciment", "—", ReportText.plain(materials.cementDosageKgM3, 0), "kg/m³")
                ),
                columnWidthsCm = listOf(7.5, 2.5, 3.5, 3.0),
                alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.RIGHT, ReportAlign.CENTER)
            )
            blocks += ReportBlock.Heading(2, "3.2 Acier", "s32")
            blocks += ReportBlock.Table(
                "Caractéristiques de l'acier",
                listOf("Grandeur", "Symbole", "Valeur", "Unité"),
                listOf(
                    listOf("Nuance", "—", materials.steelGrade, "—"),
                    listOf("Limite d'élasticité", "fe", ReportText.plain(materials.feMPa, 0), "MPa"),
                    listOf("Contrainte de calcul ELU", "fsu", ReportText.plain(materials.sigmaSteelEluMPa, 2), "MPa"),
                    listOf("Contrainte de calcul accidentelle", "fsu,acc", ReportText.plain(materials.sigmaSteelAccidentalMPa, 2), "MPa"),
                    listOf("Coefficient de sécurité ELU", "γs", ReportText.plain(materials.gammaSElu, 2), "—"),
                    listOf("Coefficient de sécurité accidentel", "γs,acc", ReportText.plain(materials.gammaSAccidental, 2), "—")
                ),
                columnWidthsCm = listOf(7.5, 2.5, 3.5, 3.0),
                alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.RIGHT, ReportAlign.CENTER)
            )
            blocks += ReportBlock.Heading(2, "3.3 Formules de calcul", "s33")
            blocks += ReportBlock.Equation(
                listOf(
                    mv("f"), msub(mv("t"), mt("28")), mt(" = 0,6 + 0,06 "), msub(mv("f"), mt("c28")),
                    mt("   ;   "), msub(mv("E"), mt("ij")), mt(" = 11 000 "), msup(msub(mv("f"), mt("c28")), mt("1/3"))
                ),
                "Résistance en traction et module instantané (BAEL 91 Annexe A.2.1)"
            )
            blocks += ReportBlock.Equation(
                listOf(
                    msub(mv("f"), mt("bu")), mt(" = "),
                    mfrac(
                        listOf(mt("0,85 · "), msub(mv("f"), mt("c28"))),
                        listOf(mv("θ"), mt(" · "), msub(mv("γ"), mt("b")))
                    ),
                    mt("   ;   "), msub(mv("f"), mt("su")), mt(" = "),
                    mfrac(
                        listOf(mv("f"), msub(mv("e"), mt(""))),
                        listOf(mv("γ"), msub(mv("s"), mt("")))
                    )
                ),
                "Résistances de calcul du béton et de l'acier (BAEL 91 § A.4.3)"
            )
            blocks += ReportBlock.KeyValues(
                "Coefficients partiels",
                listOf(
                    "γb (ELU durable)" to ReportText.plain(materials.gammaBElu, 2),
                    "γb (accidentel)" to ReportText.plain(materials.gammaBAccidental, 2),
                    "γs (ELU)" to ReportText.plain(materials.gammaSElu, 2),
                    "θ" to ReportText.plain(materials.theta, 2),
                    "Contrainte admissible du sol" to "${ReportText.plain(materials.soilAllowableStressBars, 2)} bars"
                )
            )
            blocks += ReportBlock.Heading(2, "3.4 Exigences de durabilité", "s34")
            blocks += ReportBlock.Paragraph(
                "Les enrobages retenus tiennent compte de la classe d'exposition et du diamètre des armatures. " +
                    "Pour les poteaux et poutres, l'enrobage nominal est de 3,0 cm ; pour les voiles, 2,5 cm sur les " +
                    "nappes et 3,0 cm sur les éléments de rive. Les longueurs d'ancrage sont calculées à partir de " +
                    "la contrainte d'adhérence fbu et du diamètre des barres."
            )
            return ReportChapter(
                3,
                "Matériaux",
                "Caractéristiques du béton et de l'acier, résistances de calcul et exigences de durabilité.",
                geometry.chapters.firstOrNull { it.number == 3 }?.status ?: CheckStatus.OK,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 4

        fun combinationsChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "4.1 Combinaisons d'actions", "s41")
            blocks += ReportBlock.Paragraph(
                "Les combinaisons sont construites selon le BAEL 91 pour les états limites ultimes et de service, " +
                    "et selon le RPA 2024 pour les situations accidentelles sismiques. Les efforts correspondants " +
                    "sont lus directement dans les tables ETABS."
            )
            blocks += ReportBlock.Equation(ReportEquations.ultimateCombination(), "Combinaison ELU fondamentale")
            blocks += ReportBlock.Equation(ReportEquations.serviceCombination(), "Combinaison ELS")
            blocks += ReportBlock.Equation(ReportEquations.seismicCombination(), "Combinaison accidentelle sismique")
            blocks += ReportBlock.Equation(ReportEquations.seismicWeight(), "Poids sismique (RPA 2024 § 4.2.3)")

            blocks += ReportBlock.Heading(2, "4.2 Coefficients retenus", "s42")
            val psi = resolved.psi
            blocks += ReportBlock.Table(
                "Coefficients de combinaison",
                listOf("Combinaison", "G", "Q", "E", "Coefficient partiel"),
                listOf(
                    listOf("ELU", "1,35", "1,50", "—", "γb = ${ReportText.plain(materials.gammaBElu, 2)} ; γs = ${ReportText.plain(materials.gammaSElu, 2)}"),
                    listOf("ELS", "1,00", "1,00", "—", "γs = ${ReportText.plain(materials.gammaSElu, 2)}"),
                    listOf("ELA", "1,00", ReportText.plain(psi, 2), "1,00", "γb = ${ReportText.plain(materials.gammaBAccidental, 2)} ; γs = ${ReportText.plain(materials.gammaSAccidental, 2)}")
                ),
                columnWidthsCm = listOf(3.2, 2.0, 2.0, 2.0, 7.3),
                alignments = listOf(ReportAlign.CENTER, ReportAlign.CENTER, ReportAlign.CENTER, ReportAlign.CENTER, ReportAlign.LEFT)
            )
            blocks += ReportBlock.Note(
                "Le coefficient ψ retenu pour le poids sismique et les combinaisons accidentelles vaut " +
                    "${ReportText.plain(psi, 2)} (RPA 2024, tableau des coefficients de combinaison).",
                "Coefficient ψ"
            )

            blocks += ReportBlock.Heading(2, "4.3 Cas de charge et combinaisons du modèle", "s43")
            val cases = (results.baseReactions.map { it.loadCase } +
                results.storyShears.map { it.loadCase } +
                results.columnForces.map { it.loadCase } +
                results.pierForces.map { it.loadCase })
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            if (cases.isEmpty()) {
                blocks += ReportBlock.Note("Aucun cas de charge n'a été trouvé dans les tables de résultats importées.")
            } else {
                blocks += ReportBlock.Table(
                    "Cas de charge détectés",
                    listOf("Combinaison / cas", "Catégorie"),
                    cases.map { case -> listOf(case, categoryLabel(ComboCategory.classify(case))) },
                    columnWidthsCm = listOf(9.0, 7.5),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER)
                )
            }
            blocks += ReportBlock.Heading(2, "4.4 Principe de retenue des efforts", "s44")
            blocks += ReportBlock.Bullets(
                listOf(
                    "Poteaux et poutres : enveloppes ELU pour le ferraillage courant, enveloppes ELA pour les vérifications sismiques.",
                    "Voiles : enveloppes ELA pour la flexion composée et l'effort tranchant, avec amplification réglementaire de 1,40 Vu.",
                    "Planchers : combinaisons ELU pour le ferraillage et ELS pour la flèche et les contraintes.",
                    "Semelles : enveloppe ELU et ELA pour le dimensionnement, ELS pour la portance du sol."
                )
            )
            return ReportChapter(
                4,
                "Combinaisons d'actions",
                "Combinaisons ELU, ELS et ELA, coefficients partiels et inventaire des cas de charge.",
                geometry.chapters.firstOrNull { it.number == 4 }?.status ?: CheckStatus.OK,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 5

        fun loadChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "5.1 Principe de la descente de charges", "s51")
            blocks += ReportBlock.Paragraph(
                "La descente de charges cumule les charges permanentes (poids propre, revêtements, cloisons) et " +
                    "les surcharges d'exploitation pondérées. Le poids sismique W est obtenu par la combinaison " +
                    "G + ψQ et comparé au poids lu dans la table Story Shears du modèle."
            )
            blocks += ReportBlock.Equation(ReportEquations.seismicWeight(), "Poids sismique par niveau")

            val weightByStory = results.cumulativeWeightByStoryKN()
            val storyShearRows = results.storyShears.filter { it.isSeismicWeightCase }
            blocks += ReportBlock.Heading(2, "5.2 Poids par niveau", "s52")
            if (storyShearRows.isEmpty() && weightByStory.isEmpty()) {
                blocks += ReportBlock.Note(
                    "Aucune ligne de poids sismique (cas WI) n'a été trouvée dans la table Story Shears. " +
                        "Le poids sismique est alors déduit des efforts normaux des poteaux, ou saisi manuellement."
                )
            } else {
                val rows = joint.storySystem.orderedStories.map { level ->
                    val weight = weightByStory[level.storyName]
                        ?: storyShearRows.filter { it.story == level.storyName }.maxOfOrNull { abs(it.pKN) }
                    listOf(
                        level.storyName,
                        ReportText.plain(level.elevationZk, 2),
                        weight?.let { ReportText.num(it, 2) } ?: "—",
                        weight?.let { ReportText.num(it / max(1.0, joint.storySystem.totalHeightHn), 2) } ?: "—"
                    )
                }
                blocks += ReportBlock.Table(
                    "Poids sismique par niveau",
                    listOf("Niveau", "Altitude (m)", "Poids Wi (kN)", "Wi / Hn (kN/m)"),
                    rows,
                    columnWidthsCm = listOf(4.0, 4.0, 4.5, 4.0),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.RIGHT, ReportAlign.RIGHT)
                )
            }
            val totalWeight = request.options.manualSeismicWeightKN
                ?: results.seismicWeightKN
                ?: weightByStory.values.sum().takeIf { it > 0.0 }
            blocks += ReportBlock.KeyValues(
                "Synthèse du poids sismique",
                listOf(
                    "Poids sismique total W" to (totalWeight?.let { "${ReportText.num(it, 2)} kN" } ?: "non disponible"),
                    "Coefficient ψ" to ReportText.plain(resolved.psi, 2),
                    "Nombre de niveaux N" to "${joint.storySystem.numStoriesN}",
                    "Hauteur Hn" to "${ReportText.plain(joint.storySystem.totalHeightHn, 2)} m"
                )
            )

            blocks += ReportBlock.Heading(2, "5.3 Descente de charges par poteau", "s53")
            val columnInputs = ColumnForceInput.fromEnvelopes(catalog.columns, results.columnEnvelopes())
                .sortedByDescending { it.axialKN }
                .take(40)
            if (columnInputs.isEmpty()) {
                blocks += ReportBlock.Note(
                    "Les tables Column Forces ne contiennent pas d'effort normal exploitable pour ce joint."
                )
            } else {
                blocks += ReportBlock.Table(
                    "Efforts normaux descendants par poteau",
                    listOf("Niveau", "Élément", "Famille", "N (kN)", "Combinaison"),
                    columnInputs.map { input ->
                        listOf(
                            input.story,
                            input.element,
                            input.familyName,
                            ReportText.num(input.axialKN, 1),
                            input.combo
                        )
                    },
                    columnWidthsCm = listOf(3.2, 4.4, 3.8, 3.0, 2.1),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.LEFT, ReportAlign.LEFT, ReportAlign.RIGHT, ReportAlign.LEFT)
                )
            }
            blocks += ReportBlock.Paragraph(
                "Le poteau le plus chargé reprend un effort normal de " +
                    (columnInputs.firstOrNull()?.let { "${ReportText.num(it.axialKN, 1)} kN sous la combinaison ${it.combo}" } ?: "valeur non disponible") +
                    ". Ces efforts servent au prédimensionnement (chapitre 7) et au ferraillage (chapitre 10.1)."
            )
            return ReportChapter(
                5,
                "Descente de charges",
                "Poids sismique par niveau et efforts normaux descendants des poteaux.",
                geometry.chapters.firstOrNull { it.number == 5 }?.status ?: CheckStatus.OK,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 6

        fun beamPreSizingChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "6.1 Règles de prédimensionnement", "s61")
            blocks += ReportBlock.Paragraph(
                "Le prédimensionnement des poutres vérifie la hauteur minimale vis-à-vis de la portée, la largeur " +
                    "minimale d'about et la compatibilité des armatures avec les zones nodales. Les valeurs sont " +
                    "comparées aux exigences du RPA 2024 et aux règles de l'art du BAEL 91."
            )
            blocks += ReportBlock.Bullets(
                listOf(
                    "Hauteur utile : h ≥ L / 12 pour une poutre continue, h ≥ L / 10 pour une poutre isostatique.",
                    "Largeur : b ≥ 20 cm et b ≥ h / 3 pour les poutres de rive.",
                    "Zone nodale : la largeur du poteau doit permettre l'ancrage des armatures longitudinales.",
                    "Les poutres forment un chaînage périphérique et intérieur conformément au RPA 2024 § 7.5."
                )
            )
            val checks = mutableListOf<DesignCheck>()
            catalog.beams.forEach { beam ->
                checks += Checks.min(
                    "PB6_H_${beam.name}",
                    "Élancement de la poutre ${beam.name}",
                    "BAEL 91 § B.6.1 / RPA 2024 § 7.5.1",
                    provided = beam.hCm / (beam.maxClearSpanMeters * 100.0),
                    minimum = 1.0 / 12.0,
                    unit = "",
                    decimals = 4
                )
                checks += Checks.min(
                    "PB6_B_${beam.name}",
                    "Largeur de la poutre ${beam.name}",
                    "RPA 2024 § 7.5.1",
                    provided = beam.bCm,
                    minimum = 20.0,
                    unit = "cm",
                    decimals = 1
                )
                checks += Checks.min(
                    "PB6_HMIN_${beam.name}",
                    "Hauteur de la poutre ${beam.name}",
                    "RPA 2024 § 7.5.1",
                    provided = beam.hCm,
                    minimum = 30.0,
                    unit = "cm",
                    decimals = 1
                )
            }
            blocks += ReportBlock.Heading(2, "6.2 Vérification des sections", "s62")
            if (catalog.beams.isEmpty()) {
                blocks += ReportBlock.Note("Aucune famille de poutres n'a été détectée dans le modèle.")
            } else {
                blocks += ReportBlock.Table(
                    "Prédimensionnement des poutres",
                    listOf("Poutre", "b (cm)", "h (cm)", "L max (m)", "h / L", "h/L min", "État"),
                    catalog.beams.map { beam ->
                        val ratio = beam.hCm / (beam.maxClearSpanMeters * 100.0)
                        listOf(
                            beam.name,
                            ReportText.plain(beam.bCm, 1),
                            ReportText.plain(beam.hCm, 1),
                            ReportText.plain(beam.maxClearSpanMeters, 2),
                            ReportText.plain(ratio, 4),
                            ReportText.plain(1.0 / 12.0, 4),
                            if (ratio >= 1.0 / 12.0) "Vérifié" else "À revoir"
                        )
                    },
                    columnWidthsCm = listOf(4.0, 2.0, 2.0, 2.4, 2.2, 2.2, 2.2)
                )
            }
            blocks += ReportBlock.Heading(2, "6.3 Contrôles détaillés", "s63")
            blocks += ReportBlock.ChecksTable("Contrôles de prédimensionnement", checks)
            blocks += ReportBlock.Heading(2, "6.4 Conclusion", "s64")
            blocks += ReportBlock.Paragraph(
                "Les sections de poutres retenues satisfont les ratios de prédimensionnement pour " +
                    "${catalog.beams.count { it.hCm / (it.maxClearSpanMeters * 100.0) >= 1.0 / 12.0 }} famille(s) sur " +
                    "${catalog.beams.size}. Le ferraillage détaillé est présenté au chapitre 10.2."
            )
            return ReportChapter(
                6,
                "Prédimensionnement des poutres",
                "Vérification des hauteurs, largeurs et ratios de portée des poutres.",
                CheckStatus.worst(checks.map { it.status }),
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 7

        fun columnPreSizingChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "7.1 Règles de prédimensionnement", "s71")
            blocks += ReportBlock.Paragraph(
                "Le prédimensionnement des poteaux contrôle la dimension minimale de section, l'élancement " +
                    "géométrique et le pourcentage d'armatures retenu par le ferraillage automatique. Ces vérifications " +
                    "précèdent l'étude détaillée en flexion composée du chapitre 10.1."
            )
            blocks += ReportBlock.Equation(ReportEquations.reducedAxial(), "Effort normal réduit admissible (RPA 2024 § 7.4.3)")
            blocks += ReportBlock.Equation(ReportEquations.columnMinimumSteel(), "Pourcentages d'armatures des poteaux (RPA 2024 § 7.4.2.1)")
            val checks = mutableListOf<DesignCheck>()
            catalog.columns.forEach { column ->
                val design = columns.columns.firstOrNull { it.family.name == column.name }
                checks += Checks.min(
                    "PP7_DIM_${column.name}",
                    "Dimension minimale du poteau ${column.name}",
                    "RPA 2024 § 7.4.1",
                    provided = column.minDimensionCm,
                    minimum = 25.0,
                    unit = "cm",
                    decimals = 1
                )
                checks += Checks.max(
                    "PP7_EL_${column.name}",
                    "Élancement du poteau ${column.name}",
                    "RPA 2024 § 7.4.1",
                    provided = design?.transverse?.slendernessLambda ?: 0.0,
                    maximum = 70.0,
                    unit = "",
                    decimals = 1
                )
                val ratio = design?.let { if (column.areaCm2 > 0.0) it.adoptedAsCm2 / column.areaCm2 * 100.0 else 0.0 } ?: 0.0
                checks += Checks.between(
                    "PP7_AS_${column.name}",
                    "Pourcentage d'armatures du poteau ${column.name}",
                    "RPA 2024 § 7.4.2.1",
                    provided = ratio,
                    minimum = 0.8,
                    maximum = 4.0,
                    unit = "%",
                    decimals = 2
                )
            }
            blocks += ReportBlock.Heading(2, "7.2 Vérification des sections", "s72")
            if (catalog.columns.isEmpty()) {
                blocks += ReportBlock.Note("Aucune famille de poteaux n'a été détectée dans le modèle.")
            } else {
                blocks += ReportBlock.Table(
                    "Prédimensionnement des poteaux",
                    listOf("Poteau", "Section (cm)", "Lcl (m)", "λ", "Bc (cm²)", "As adopté (cm²)", "ρ (%)"),
                    catalog.columns.map { column ->
                        val design = columns.columns.firstOrNull { it.family.name == column.name }
                        listOf(
                            column.name,
                            column.geometryLabel,
                            ReportText.plain(column.clearHeightLclMeters, 2),
                            ReportText.plain(design?.transverse?.slendernessLambda ?: 0.0, 1),
                            ReportText.plain(column.areaCm2, 0),
                            ReportText.plain(design?.adoptedAsCm2 ?: 0.0, 2),
                            ReportText.plain(
                                design?.let { if (column.areaCm2 > 0) it.adoptedAsCm2 / column.areaCm2 * 100.0 else 0.0 } ?: 0.0,
                                2
                            )
                        )
                    },
                    columnWidthsCm = listOf(3.4, 3.6, 2.2, 1.8, 2.4, 2.6, 1.8)
                )
            }
            blocks += ReportBlock.Heading(2, "7.3 Contrôles détaillés", "s73")
            blocks += ReportBlock.ChecksTable("Contrôles de prédimensionnement des poteaux", checks)
            blocks += ReportBlock.Heading(2, "7.4 Conclusion", "s74")
            blocks += ReportBlock.Paragraph(
                "Les sections de poteaux respectent la dimension minimale de 25 cm et l'élancement limite retenu. " +
                    "Le ferraillage détaillé, les zones nodales et les cadres sont étudiés au chapitre 10.1."
            )
            return ReportChapter(
                7,
                "Prédimensionnement des poteaux",
                "Dimensions minimales, élancement et pourcentages d'armatures des poteaux.",
                CheckStatus.worst(checks.map { it.status }),
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 8

        fun slabChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "8.1 Méthode de calcul", "s81")
            blocks += ReportBlock.Paragraph(
                "Les planchers à corps creux sont calculés comme des poutrelles continues reposant sur les poutres. " +
                    "La méthode des trois moments fournit les moments sur appuis, puis les moments en travée sont " +
                    "déduits de l'isostatique. Le ferraillage est dimensionné en section en Té pour les travées et en " +
                    "section rectangulaire pour les appuis."
            )
            blocks += ReportBlock.Equation(ReportEquations.referenceMoment(), "Moment isostatique de référence")
            blocks += ReportBlock.Equation(ReportEquations.spanAndSupportMoments(), "Moments en travée et sur appui")
            blocks += ReportBlock.Equation(ReportEquations.steelArea(), "Section d'acier tendu")
            blocks += ReportBlock.Equation(ReportEquations.deflection(), "Flèche totale nuisible (BAEL 91 Annexe B.6.5)")

            if (slabs.isEmpty()) {
                blocks += ReportBlock.Note(
                    "Aucune famille de planchers exploitable n'a été détectée. Vérifier les affectations de sections " +
                        "coque dans ETABS et la présence des portées continues."
                )
                return ReportChapter(
                    8,
                    "Planchers et poutrelles (Koka)",
                    "Étude des poutrelles continues : moments, ferraillage et flèche.",
                    CheckStatus.NOT_APPLICABLE,
                    blocks
                )
            }

            blocks += ReportBlock.Heading(2, "8.2 Charges et hypothèses", "s82")
            blocks += ReportBlock.Table(
                "Charges des planchers",
                listOf("Plancher", "ht (cm)", "G (kN/m²)", "Q (kN/m²)", "qu (kN/m)", "qs (kN/m)"),
                slabs.map { result ->
                    listOf(
                        result.topology.name,
                        ReportText.plain(result.topology.totalThicknessHtCm, 1),
                        ReportText.plain(result.loads.permanentLoadKNPerM2, 2),
                        ReportText.plain(result.loads.variableLoadKNPerM2, 2),
                        ReportText.plain(result.loads.ultimateLineLoadKNPerM, 2),
                        ReportText.plain(result.loads.serviceLineLoadKNPerM, 2)
                    )
                },
                columnWidthsCm = listOf(4.2, 2.2, 2.6, 2.4, 2.6, 2.5)
            )

            blocks += ReportBlock.Heading(2, "8.3 Résultats par plancher", "s83")
            slabs.forEach { result ->
                blocks += ReportBlock.Paragraph(
                    "Plancher ${result.topology.name} — ${result.spanCount} travée(s), ${result.topology.geometryLabel}.",
                    bold = true,
                    align = ReportAlign.LEFT,
                    keepWithNext = true
                )
                blocks += ReportBlock.Table(
                    "Travées — ${result.topology.name}",
                    listOf("Travée", "L (m)", "M_t,ELU (kN·m)", "M_a,ELU (kN·m)", "As (cm²)", "Choix", "Vérifié"),
                    result.spans.map { span ->
                        listOf(
                            "${span.index + 1}",
                            ReportText.plain(span.lengthMeters, 2),
                            ReportText.plain(span.maxSpanMomentUltimateKNm, 2),
                            ReportText.plain(abs(span.leftMomentUltimateKNm), 2),
                            ReportText.plain(span.reinforcement.requiredAsCm2, 2),
                            span.reinforcement.bars.label,
                            ReportText.statusLabel(span.reinforcement.status)
                        )
                    },
                    columnWidthsCm = listOf(1.8, 2.2, 3.2, 3.2, 2.4, 2.2, 2.5)
                )
                blocks += ReportBlock.Table(
                    "Appuis — ${result.topology.name}",
                    listOf("Appui", "M_a,ELU (kN·m)", "As requise (cm²)", "Choix", "État"),
                    result.supports.map { support ->
                        listOf(
                            "${support.index + 1}",
                            ReportText.plain(abs(support.momentUltimateKNm), 2),
                            ReportText.plain(support.reinforcement.requiredAsCm2, 2),
                            support.reinforcement.bars.label,
                            ReportText.statusLabel(support.reinforcement.status)
                        )
                    },
                    columnWidthsCm = listOf(2.2, 3.4, 3.4, 3.0, 4.5)
                )
                blocks += ReportBlock.KeyValues(
                    "Flèche et contraintes — ${result.topology.name}",
                    listOf(
                        "Flèche totale Δft" to "${ReportText.plain(result.deflection.deltaFtMeters * 1000.0, 2)} mm",
                        "Flèche admissible" to "${ReportText.plain(result.deflection.allowableMeters * 1000.0, 2)} mm",
                        "Taux d'utilisation" to ReportText.pct(result.deflection.utilization * 100.0),
                        "Contrainte béton (ELS)" to "${ReportText.plain(result.spans.firstOrNull()?.serviceStress?.sigmaConcreteMPa ?: 0.0, 2)} MPa",
                        "Contrainte acier (ELS)" to "${ReportText.plain(result.spans.firstOrNull()?.serviceStress?.sigmaSteelMPa ?: 0.0, 2)} MPa"
                    )
                )
                runCatching { sheets.renderKokaSheet(result, "Fiche Koka — ${result.topology.name}", dpi) }
                    .getOrNull()?.let { output ->
                        blocks += figure(
                            "koka_${result.topology.name}",
                            output,
                            "Figure 8.${slabs.indexOf(result) + 1} — Fiche de calcul Koka du plancher ${result.topology.name}",
                            16.5
                        )
                    }
                val baelT = baelTFor(result)
                if (baelT != null) {
                    runCatching { sheets.renderBaelTSheet(baelT, "Fiche BaelT — ${result.topology.name}", dpi) }
                        .getOrNull()?.let { output ->
                            blocks += figure(
                                "baelt_${result.topology.name}",
                                output,
                                "Figure 8.${slabs.indexOf(result) + 1}.b — Fiche BaelT de la travée la plus sollicitée " +
                                    "du plancher ${result.topology.name}",
                                16.5
                            )
                        }
                }
            }
            blocks += ReportBlock.Heading(2, "8.4 Synthèse des vérifications", "s84")
            blocks += ReportBlock.ChecksTable(
                "Contrôles des planchers",
                slabs.flatMap { it.checks })
            return ReportChapter(
                8,
                "Planchers et poutrelles (Koka)",
                "Poutrelles continues : charges, moments, ferraillage, flèche et contraintes ELS.",
                CheckStatus.worst(slabs.map { it.status }),
                blocks
            )
        }

        private fun baelTFor(result: ContinuousSlabResult) = runCatching {
            val topology = result.topology
            if (topology.isSolidSlab) return@runCatching null
            val h = topology.htMeters
            val h0 = topology.compressionSlabH0Cm / 100.0
            val b = topology.flangeWidthBCm / 100.0
            val b0 = topology.ribWidthB0Cm / 100.0
            if (h0 <= 0.0 || h0 >= h || b0 <= 0.0 || b0 > b) return@runCatching null
            baelTSolver.solve(
                BaelTInput(
                    bMeters = b,
                    b0Meters = b0,
                    hMeters = h,
                    h0Meters = h0,
                    dPrimeMeters = 0.03,
                    cMeters = 0.03,
                    nuKN = 0.0,
                    muKNm = result.maxUltimateMomentKNm,
                    fc28MPa = materials.fc28MPa,
                    feMPa = materials.feMPa,
                    theta = materials.theta,
                    gammaB = materials.gammaBElu,
                    gammaS = materials.gammaSElu
                )
            )
        }.getOrNull()

        // ---------------------------------------------------------------- chapitre 9

        fun seismicChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "9.1 Spectre de réponse de calcul", "s91")
            blocks += ReportBlock.Paragraph(
                "Le spectre de calcul est construit selon le RPA 2024 à partir de la zone sismique " +
                    "(${resolved.zone.code}, A = ${ReportText.plain(resolved.zone.coeffA, 2)} g), du site " +
                    "${resolved.siteClass.code}, du groupe d'importance ${resolved.importance.code} " +
                    "(I = ${ReportText.plain(resolved.importance.coeffI, 2)}) et du coefficient de comportement " +
                    "R = ${ReportText.plain(resolved.behaviorFactorR, 2)}."
            )
            blocks += ReportBlock.Equation(ReportEquations.spectralAcceleration(), "Pseudo-accélération horizontale du spectre de calcul")
            blocks += ReportBlock.Equation(ReportEquations.spectrumFloor(), "Plancher minimal du spectre de calcul")
            val spectral = resolved.spectralParams
            if (spectral != null) {
                blocks += ReportBlock.Equation(
                    ReportEquations.branchLimits(spectral.t1, spectral.t2, spectral.t3),
                    "Bornes des branches spectrales du site ${resolved.siteClass.code}"
                )
                blocks += ReportBlock.Table(
                    "Paramètres du spectre",
                    listOf("Paramètre", "Valeur", "Unité"),
                    listOf(
                        listOf("Coefficient d'accélération A", ReportText.plain(resolved.zone.coeffA, 2), "g"),
                        listOf("Coefficient de site S", ReportText.plain(spectral.s, 2), "—"),
                        listOf("Facteur d'importance I", ReportText.plain(resolved.importance.coeffI, 2), "—"),
                        listOf("Coefficient de comportement R", ReportText.plain(resolved.behaviorFactorR, 2), "—"),
                        listOf("Facteur de qualité Q", ReportText.plain(resolved.qualityFactor.qf, 2), "—"),
                        listOf("Amortissement", ReportText.plain(resolved.damping.percent, 1), "%"),
                        listOf("Période T1", ReportText.plain(spectral.t1, 2), "s"),
                        listOf("Période T2", ReportText.plain(spectral.t2, 2), "s"),
                        listOf("Période T3", ReportText.plain(spectral.t3, 2), "s"),
                        listOf("Plancher 0,2 A I", ReportText.plain(0.2 * resolved.zone.coeffA * resolved.importance.coeffI, 4), "g")
                    ),
                    columnWidthsCm = listOf(8.5, 4.0, 4.0),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.RIGHT, ReportAlign.CENTER)
                )
            }
            val generator = seismic.spectrumGenerator
            if (generator != null) {
                runCatching { charts.renderSpectrum(generator, 4.0, 0.02, dpi) }.getOrNull()?.let { output ->
                    blocks += figure(
                        "spectre",
                        output,
                        "Figure 9.1 — Spectre de réponse horizontal de calcul et plancher minimal 0,2 A I",
                        16.5
                    )
                }
            }

            blocks += ReportBlock.Heading(2, "9.2 Analyse modale et participation", "s92")
            val modes = results.modalRatios.sortedBy { it.mode }.take(options.maxModeRows)
            if (modes.isEmpty()) {
                blocks += ReportBlock.Note("La table Modal Participating Mass Ratios n'a pas été importée.")
            } else {
                blocks += ReportBlock.Table(
                    "Participations massiques modales",
                    listOf("Mode", "T (s)", "Ux (%)", "Uy (%)", "ΣUx (%)", "ΣUy (%)"),
                    modes.map { mode ->
                        listOf(
                            "${mode.mode}",
                            ReportText.plain(mode.periodSeconds, 3),
                            ReportText.plain(mode.uxPercent, 2),
                            ReportText.plain(mode.uyPercent, 2),
                            ReportText.plain(mode.sumUxPercent, 2),
                            ReportText.plain(mode.sumUyPercent, 2)
                        )
                    },
                    columnWidthsCm = listOf(2.0, 2.8, 2.9, 2.9, 2.9, 3.0)
                )
            }
            val modal = seismic.modalParticipation
            blocks += ReportBlock.KeyValues(
                "Synthèse de la participation modale",
                listOf(
                    "Nombre de modes" to "${modal.modeCount}",
                    "Modes nécessaires pour 90 %" to (modal.requiredModesFor90Percent?.toString() ?: "—"),
                    "ΣUx" to ReportText.pct(modal.sumUxPercent),
                    "ΣUy" to ReportText.pct(modal.sumUyPercent),
                    "Mode fondamental X" to (modal.fundamentalModeX?.let { "T = ${ReportText.plain(it.periodSeconds, 3)} s" } ?: "—"),
                    "Mode fondamental Y" to (modal.fundamentalModeY?.let { "T = ${ReportText.plain(it.periodSeconds, 3)} s" } ?: "—"),
                    "Mode de torsion" to (modal.torsionMode?.let { "T = ${ReportText.plain(it.periodSeconds, 3)} s" } ?: "—"),
                    "État" to ReportText.statusLabel(modal.status)
                )
            )
            blocks += ReportBlock.Paragraph(
                "Le nombre de modes retenus doit assurer une participation massique cumulée d'au moins 90 % " +
                    "dans chaque direction horizontale (RPA 2024 § 4.3.4). ${modal.message}"
            )

            blocks += ReportBlock.Heading(2, "9.3 Comparaison MSE / MDS", "s93")
            blocks += ReportBlock.Equation(ReportEquations.staticBaseShear(), "Effort tranchant statique équivalent")
            blocks += ReportBlock.Equation(ReportEquations.dynamicBaseShear(), "Condition de recalage de la méthode dynamique")
            blocks += ReportBlock.Table(
                "Comparaison méthode statique / méthode dynamique",
                listOf("Sens", "T analysée (s)", "T empirique (s)", "T retenue (s)", "λ", "Vstat (kN)", "Vdyn (kN)", "Vdyn/Vstat", "λ mini", "État"),
                seismic.baseShear.map { check ->
                    listOf(
                        check.directionLabel,
                        check.periodAnalyticalSeconds?.let { ReportText.plain(it, 3) } ?: "—",
                        ReportText.plain(check.periodEmpiricalSeconds, 3),
                        ReportText.plain(check.periodRetainedSeconds, 3),
                        ReportText.plain(check.lambda, 2),
                        check.staticBaseShearKN?.let { ReportText.num(it, 1) } ?: "—",
                        check.dynamicBaseShearKN?.let { ReportText.num(it, 1) } ?: "—",
                        check.dynamicToStaticRatio?.let { ReportText.plain(it, 3) } ?: "—",
                        check.scaleFactor?.let { ReportText.plain(it, 3) } ?: "—",
                        ReportText.statusLabel(check.status)
                    )
                },
                columnWidthsCm = listOf(2.0, 2.0, 2.0, 2.0, 1.4, 2.2, 2.2, 1.9, 1.6, 2.2),
                fontSizePt = 8
            )

            blocks += ReportBlock.Heading(2, "9.4 Effort normal réduit des poteaux (ν ≤ 0,35)", "s94")
            blocks += ReportBlock.Equation(ReportEquations.reducedAxial(), "Condition RPA 2024 § 7.4.3")
            val normalChecks = seismic.columnReducedNormalForce
            if (normalChecks.isEmpty()) {
                blocks += ReportBlock.Note("Aucun effort normal de poteau n'a pu être vérifié.")
            } else {
                blocks += ReportBlock.Table(
                    "Effort normal réduit des poteaux",
                    listOf("Poteau", "Niveau", "Section", "Nd (kN)", "Bc (cm²)", "ν", "ν lim", "État"),
                    normalChecks.take(options.maxCheckRows).map { check ->
                        listOf(
                            check.element,
                            check.story,
                            check.sectionLabel,
                            ReportText.num(check.ndKN, 1),
                            ReportText.plain(check.areaCm2, 0),
                            ReportText.plain(check.nu, 3),
                            ReportText.plain(check.nuLimit, 2),
                            ReportText.statusLabel(check.status)
                        )
                    },
                    columnWidthsCm = listOf(3.8, 2.2, 2.8, 2.6, 2.4, 1.6, 1.6, 2.5)
                )
            }

            blocks += ReportBlock.Heading(2, "9.5 Déplacements relatifs inter-étages", "s95")
            blocks += ReportBlock.Equation(ReportEquations.interStoryDrift(), "Déplacement relatif et limite (RPA 2024 § 5.10)")
            val driftRows = seismic.driftChecks
            if (driftRows.isEmpty()) {
                blocks += ReportBlock.Note("Les déplacements diaphragme n'ont pas été importés.")
            } else {
                blocks += ReportBlock.Table(
                    "Déplacements relatifs inter-étages",
                    listOf("Sens", "Niveau", "hk (m)", "δe (mm)", "δinel (mm)", "δlim (mm)", "δ/δlim", "État"),
                    driftRows.map { drift ->
                        listOf(
                            if (drift.direction.name == "X") "X-X" else "Y-Y",
                            drift.story,
                            ReportText.plain(drift.storyHeightMeters, 2),
                            ReportText.plain(drift.relativeDriftMeters * 1000.0, 2),
                            ReportText.plain(drift.inelasticDisplacementMeters * 1000.0, 2),
                            ReportText.plain(drift.governingLimitMeters * 1000.0, 2),
                            ReportText.plain(drift.utilization, 3),
                            ReportText.statusLabel(drift.status)
                        )
                    },
                    columnWidthsCm = listOf(1.8, 2.8, 2.0, 2.4, 2.6, 2.4, 2.0, 2.5)
                )
            }

            blocks += ReportBlock.Heading(2, "9.6 Effets du second ordre (P-Δ)", "s96")
            blocks += ReportBlock.Equation(ReportEquations.pDelta(), "Coefficient de sensibilité P-Δ (RPA 2024 § 5.9)")
            val pDeltaRows = seismic.pDeltaChecks
            if (pDeltaRows.isEmpty()) {
                blocks += ReportBlock.Note("Les données nécessaires au calcul P-Δ ne sont pas disponibles.")
            } else {
                blocks += ReportBlock.Table(
                    "Classification P-Δ par niveau",
                    listOf("Sens", "Niveau", "Pk (kN)", "Vk (kN)", "hk (m)", "Δ (mm)", "θ", "Classement"),
                    pDeltaRows.map { check ->
                        listOf(
                            if (check.direction.name == "X") "X-X" else "Y-Y",
                            check.story,
                            ReportText.num(check.cumulativeWeightKN, 1),
                            ReportText.num(check.storyShearKN, 1),
                            ReportText.plain(check.storyHeightMeters, 2),
                            ReportText.plain(check.driftMeters * 1000.0, 2),
                            ReportText.plain(check.theta, 4),
                            check.classification.label
                        )
                    },
                    columnWidthsCm = listOf(1.6, 2.4, 2.6, 2.6, 1.8, 2.0, 1.8, 3.7),
                    fontSizePt = 8
                )
            }

            blocks += ReportBlock.Heading(2, "9.7 Synthèse de l'étude sismique", "s97")
            blocks += ReportBlock.Verdict(
                when (seismic.overallStatus) {
                    CheckStatus.OK -> "L'ensemble des vérifications sismiques globales est satisfait."
                    CheckStatus.WARNING -> "Certaines vérifications sismiques nécessitent une justification complémentaire."
                    CheckStatus.FAILED -> "Des vérifications sismiques ne sont pas satisfaites : le modèle doit être revu."
                    CheckStatus.NOT_APPLICABLE -> "Aucune vérification sismique n'est applicable à cette configuration."
                },
                seismic.overallStatus
            )
            blocks += ReportBlock.ChecksTable("Contrôles sismiques globaux", seismic.checks)
            if (seismic.warnings.isNotEmpty()) {
                blocks += ReportBlock.Note(seismic.warnings.joinToString(" ; "), "Avertissements sismiques")
            }
            return ReportChapter(
                9,
                "Étude sismique",
                "Spectre de calcul, participation modale, recalage MSE/MDS, effort normal réduit, déplacements et P-Δ.",
                seismic.overallStatus,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 10

        fun structuralElementsChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Paragraph(
                "Ce chapitre regroupe le ferraillage détaillé des poteaux (10.1), des poutres (10.2) et des voiles " +
                    "de contreventement (10.3). Chaque famille est traitée en flexion composée ou simple selon sa nature, " +
                    "avec les dispositions constructives du RPA 2024."
            )

            // 10.1 Poteaux
            blocks += ReportBlock.Heading(2, "10.1 Poteaux", "s101")
            blocks += ReportBlock.Paragraph(
                "Les poteaux sont calculés en flexion composée avec les enveloppes ELU et ELA. Les armatures " +
                    "longitudinales, les zones nodales, les cadres de confinement et l'élancement sont vérifiés."
            )
            blocks += ReportBlock.Equation(ReportEquations.columnMinimumSteel(), "Pourcentages d'armatures (RPA 2024 § 7.4.2.1)")
            if (columns.columns.isEmpty()) {
                blocks += ReportBlock.Note("Aucune famille de poteaux n'a été détectée.")
            } else {
                blocks += ReportBlock.Table(
                    "Ferraillage des poteaux",
                    listOf("Poteau", "Section (cm)", "N (kN)", "M (kN·m)", "As,calc (cm²)", "As,min (cm²)", "Choix", "Cadres", "État"),
                    columns.columns.map { design ->
                        listOf(
                            design.family.name,
                            "${ReportText.plain(design.sectionWidthCm, 1)}×${ReportText.plain(design.sectionDepthCm, 1)}",
                            ReportText.num(design.axialCompressionKN, 1),
                            ReportText.num(design.governingCase?.momentKNm ?: 0.0, 1),
                            ReportText.plain(design.requiredAsCm2, 2),
                            ReportText.plain(design.minimumAsCm2, 2),
                            design.layout?.label ?: "—",
                            design.transverse.stirrupLabel,
                            ReportText.statusLabel(design.status)
                        )
                    },
                    columnWidthsCm = listOf(2.8, 2.4, 2.2, 2.2, 2.2, 2.2, 2.0, 1.8, 1.9),
                    fontSizePt = 8
                )
                blocks += ReportBlock.Table(
                    "Dispositions constructives des poteaux",
                    listOf("Poteau", "Lcl (m)", "Zone nodale (cm)", "Recouvrement (cm)", "espacement nodal", "espacement courant"),
                    columns.columns.map { design ->
                        listOf(
                            design.family.name,
                            ReportText.plain(design.transverse.clearHeightMeters, 2),
                            ReportText.plain(design.transverse.nodalZoneLengthCm, 0),
                            ReportText.plain(design.transverse.lapLengthCm, 0),
                            ReportText.plain(design.transverse.nodalSpacingCm, 1),
                            ReportText.plain(design.transverse.currentSpacingCm, 1)
                        )
                    },
                    columnWidthsCm = listOf(3.4, 2.6, 3.2, 3.2, 2.4, 2.4)
                )
                val governing = columns.columns.firstNotNullOfOrNull { it.governingCase?.bael }
                if (governing != null) {
                    runCatching { sheets.renderBaelRSheet(governing, "Fiche BaelR — poteau le plus sollicité", dpi) }
                        .getOrNull()?.let { output ->
                            blocks += figure(
                                "baelr_poteau",
                                output,
                                "Figure 10.1 — Fiche de calcul BaelR du poteau le plus sollicité",
                                16.5
                            )
                        }
                }
                columns.columns.take(4).forEachIndexed { index, design ->
                    runCatching { sheets.renderColumnExecution(design, dpi) }.getOrNull()?.let { output ->
                        blocks += figure(
                            "poteau_exec_${design.family.name}",
                            output,
                            "Figure 10.2.${index + 1} — Schéma d'exécution du poteau ${design.family.name}",
                            15.5
                        )
                    }
                }
                blocks += ReportBlock.ChecksTable("Contrôles des poteaux", columns.columns.flatMap { it.checks })
            }

            // 10.2 Poutres
            blocks += ReportBlock.Heading(2, "10.2 Poutres", "s102")
            blocks += ReportBlock.Paragraph(
                "Les poutres sont calculées en flexion simple à l'ELU, avec les chapeaux sur appuis et les " +
                    "vérifications de cisaillement des bielles et des armatures transversales."
            )
            if (beams.beams.isEmpty()) {
                blocks += ReportBlock.Note("Aucune famille de poutres n'a été détectée.")
            } else {
                blocks += ReportBlock.Table(
                    "Ferraillage des poutres",
                    listOf("Poutre", "Section (cm)", "M+ (kN·m)", "As,inf (cm²)", "As,sup (cm²)", "Choix travée", "Choix appui", "Cadres", "État"),
                    beams.beams.map { design ->
                        listOf(
                            design.family.name,
                            "${ReportText.plain(design.widthCm, 1)}×${ReportText.plain(design.heightCm, 1)}",
                            ReportText.num(design.governingSection?.momentKNm ?: 0.0, 1),
                            ReportText.plain(design.requiredBottomAsCm2, 2),
                            ReportText.plain(design.requiredTopAsCm2, 2),
                            design.spanLayout?.label ?: "—",
                            design.supportLayout?.label ?: "—",
                            design.transverse.stirrupLabel,
                            ReportText.statusLabel(design.status)
                        )
                    },
                    columnWidthsCm = listOf(2.6, 2.4, 2.2, 2.2, 2.2, 2.4, 2.4, 1.6, 1.8),
                    fontSizePt = 8
                )
                blocks += ReportBlock.Table(
                    "Vérifications de cisaillement des poutres",
                    listOf("Poutre", "Combinaison", "Vu (kN)", "τu (MPa)", "τlim (MPa)", "σbc (MPa)", "État"),
                    beams.beams.flatMap { design ->
                        design.transverse.shearVerifications.map { shear ->
                            listOf(
                                design.family.name,
                                shear.category.name,
                                ReportText.num(shear.shearKN, 1),
                                ReportText.plain(shear.tauMPa, 3),
                                ReportText.plain(shear.tauLimitMPa, 3),
                                ReportText.plain(shear.sigmaBcMPa, 2),
                                ReportText.statusLabel(shear.status)
                            )
                        }
                    },
                    columnWidthsCm = listOf(3.2, 2.6, 2.8, 2.4, 2.4, 2.4, 2.6)
                )
                val governing = beams.beams.firstNotNullOfOrNull { it.governingSection?.bael }
                if (governing != null) {
                    runCatching { sheets.renderBaelRSheet(governing, "Fiche BaelR — poutre la plus sollicitée", dpi) }
                        .getOrNull()?.let { output ->
                            blocks += figure(
                                "baelr_poutre",
                                output,
                                "Figure 10.3 — Fiche de calcul BaelR de la poutre la plus sollicitée",
                                16.5
                            )
                        }
                }
                beams.beams.take(4).forEachIndexed { index, design ->
                    runCatching { sheets.renderBeamExecution(design, dpi) }.getOrNull()?.let { output ->
                        blocks += figure(
                            "poutre_exec_${design.family.name}",
                            output,
                            "Figure 10.4.${index + 1} — Schéma d'exécution de la poutre ${design.family.name}",
                            15.5
                        )
                    }
                }
                blocks += ReportBlock.ChecksTable("Contrôles des poutres", beams.beams.flatMap { it.checks })
            }

            // 10.3 Voiles
            blocks += ReportBlock.Heading(2, "10.3 Voiles de contreventement", "s103")
            if (walls.walls.isEmpty()) {
                blocks += ReportBlock.Note(
                    "Aucun voile de contreventement n'a été détecté dans le modèle. Les chapitres 10.3 et les " +
                        "enveloppes M(z)/V(z) ne sont pas applicables à ce joint. Si des voiles existent, vérifier " +
                        "que leurs sections coque sont bien affectées dans ETABS.",
                    "Voiles non applicables"
                )
                blocks += ReportBlock.Paragraph(
                    "À titre indicatif, le RPA 2024 impose pour les voiles une épaisseur minimale de 15 cm, une " +
                        "longueur minimale liée à la hauteur d'étage et un confinement des éléments de rive sur la " +
                        "hauteur critique h_cr."
                )
            } else {
                blocks += ReportBlock.Equation(ReportEquations.wallCriticalHeight(), "Hauteur de la zone critique (RPA 2024 Eq. 7.22 et 7.23)")
                blocks += ReportBlock.Equation(ReportEquations.wallShearAmplification(), "Amplification de l'effort tranchant")
                blocks += ReportBlock.Table(
                    "Qualification géométrique des voiles",
                    listOf("Voile", "he (m)", "bw (m)", "lw (m)", "he/3 (m)", "4bw (m)", "lw,min (m)", "Classification"),
                    walls.walls.map { design ->
                        val row = design.qualification.row
                        listOf(
                            row.pier,
                            ReportText.plain(row.heMeters, 2),
                            ReportText.plain(row.bwMeters, 2),
                            ReportText.plain(row.lwMeters, 2),
                            ReportText.plain(row.heOver3Meters, 2),
                            ReportText.plain(row.fourBwMeters, 2),
                            ReportText.plain(design.qualification.requiredLengthMeters, 2),
                            row.classification.labelFr()
                        )
                    },
                    columnWidthsCm = listOf(3.0, 1.9, 1.9, 1.9, 1.9, 1.9, 2.2, 3.8),
                    fontSizePt = 8
                )
                blocks += ReportBlock.Table(
                    "Ferraillage des voiles",
                    listOf("Voile", "N (kN)", "M (kN·m)", "V (kN)", "Rive gauche", "Rive droite", "Âme verticale", "Âme horizontale", "État"),
                    walls.walls.map { design ->
                        listOf(
                            design.family.pierName,
                            ReportText.num(design.designAxialKN, 1),
                            ReportText.num(design.designMomentKNm, 1),
                            ReportText.num(design.forces.shearKN, 1),
                            "${design.boundaryLeft.barLabel} / ${design.boundaryLeft.stirrupLabel}",
                            "${design.boundaryRight.barLabel} / ${design.boundaryRight.stirrupLabel}",
                            "${design.web.barLabel} e=${ReportText.plain(design.web.verticalSpacingCm, 0)} cm",
                            "${design.web.barLabel} e=${ReportText.plain(design.web.horizontalSpacingCm, 0)} cm",
                            ReportText.statusLabel(design.status)
                        )
                    },
                    columnWidthsCm = listOf(2.4, 2.0, 2.2, 1.9, 2.2, 2.2, 1.8, 1.8, 1.5),
                    fontSizePt = 8
                )
                blocks += ReportBlock.Table(
                    "Ductilité locale des voiles",
                    listOf("Voile", "I (m⁴)", "ν", "μφ requis", "μφ disponible", "l,adm (m)", "l,fourni (m)", "État"),
                    walls.walls.map { design ->
                        val ductility = design.ductility
                        listOf(
                            design.family.pierName,
                            ReportText.plain(ductility.inertiaM4, 3),
                            ReportText.plain(ductility.normalizedAxial, 3),
                            ReportText.plain(ductility.requiredCurvatureDuctility, 2),
                            ReportText.plain(ductility.availableCurvatureDuctility, 2),
                            ReportText.plain(ductility.admissibleConfinementLengthMeters, 2),
                            ReportText.plain(ductility.providedConfinementLengthMeters, 2),
                            if (ductility.isConfinementLengthSatisfied) "Vérifié" else "À revoir"
                        )
                    },
                    columnWidthsCm = listOf(2.8, 2.0, 1.6, 2.2, 2.2, 2.2, 2.2, 2.3)
                )
                walls.walls.forEachIndexed { index, design ->
                    runCatching { charts.renderWallMomentEnvelope(design.envelope, null, dpi) }.getOrNull()?.let { output ->
                        blocks += figure(
                            "voile_${design.family.pierName}_moment",
                            output,
                            "Figure 10.5.${index + 1} — Enveloppe des moments M(z) du voile ${design.family.pierName}",
                            15.0
                        )
                    }
                    runCatching { charts.renderWallShearEnvelope(design.envelope, null, dpi) }.getOrNull()?.let { output ->
                        blocks += figure(
                            "voile_${design.family.pierName}_tranchant",
                            output,
                            "Figure 10.6.${index + 1} — Enveloppe des efforts tranchants V(z) du voile ${design.family.pierName}",
                            15.0
                        )
                    }
                    runCatching { sheets.renderWallExecution(design, dpi) }.getOrNull()?.let { output ->
                        blocks += figure(
                            "voile_exec_${design.family.pierName}",
                            output,
                            "Figure 10.7.${index + 1} — Schéma d'exécution du voile ${design.family.pierName}",
                            16.0
                        )
                    }
                }
                blocks += ReportBlock.ChecksTable("Contrôles des voiles", walls.walls.flatMap { it.checks })
            }

            val statuses = listOf(columns.status, beams.status, walls.status).filter { it != CheckStatus.NOT_APPLICABLE }
            return ReportChapter(
                10,
                "Éléments structuraux",
                "Ferraillage des poteaux, des poutres et des voiles, dispositions constructives et schémas d'exécution.",
                CheckStatus.worst(statuses),
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 11

        fun parapetChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "11.1 Géométrie et charges", "s111")
            blocks += ReportBlock.Paragraph(
                "L'acrotère est modélisé comme une console verticale de 1,00 m de largeur encastrée dans la dalle " +
                    "terrasse. Il supporte son poids propre, l'enduit, la main courante et la force sismique " +
                    "des éléments secondaires."
            )
            blocks += ReportBlock.KeyValues(
                "Géométrie de l'acrotère",
                listOf(
                    "Hauteur H" to "${ReportText.plain(parapet.geometry.heightMeters, 2)} m",
                    "Épaisseur du fût e" to "${ReportText.plain(parapet.geometry.stemThicknessMeters, 2)} m",
                    "Saillie du becquet" to "${ReportText.plain(parapet.geometry.corniceProjectionMeters, 2)} m",
                    "Périmètre développé" to "${ReportText.plain(parapet.geometry.developedPerimeterMeters, 2)} m",
                    "Aire de la section" to "${ReportText.plain(parapet.geometry.sectionAreaM2 * 1000.0, 2)} dm²",
                    "Angle du becquet" to "${ReportText.plain(parapet.geometry.slopeAngleDegrees, 2)}°"
                )
            )
            blocks += ReportBlock.Equation(ReportEquations.secondarySeismicForce(), "Force sismique de l'élément secondaire (RPA 2024)")
            blocks += ReportBlock.Table(
                "Charges de l'acrotère",
                listOf("Charge", "Valeur", "Unité"),
                listOf(
                    listOf("Poids propre du béton", ReportText.plain(parapet.loads.concreteWeightKNPerM, 3), "kN/ml"),
                    listOf("Poids de l'enduit", ReportText.plain(parapet.loads.renderWeightKNPerM, 3), "kN/ml"),
                    listOf("Poids total Wp", ReportText.plain(parapet.loads.totalWeightWpKNPerM, 3), "kN/ml"),
                    listOf("Force sismique Fp", ReportText.plain(parapet.loads.seismicForceFpKNPerM, 3), "kN/ml"),
                    listOf("Charge de main courante", ReportText.plain(parapet.loads.handrailLoadKNPerM, 2), "kN/ml"),
                    listOf("Charge horizontale de calcul", ReportText.plain(parapet.loads.designHorizontalQKNPerM, 3), "kN/ml")
                ),
                columnWidthsCm = listOf(8.0, 4.0, 4.5),
                alignments = listOf(ReportAlign.LEFT, ReportAlign.RIGHT, ReportAlign.CENTER)
            )
            blocks += ReportBlock.Heading(2, "11.2 Ferraillage", "s112")
            blocks += ReportBlock.Table(
                "Armatures de l'acrotère",
                listOf("Nappe", "M (kN·m)", "As calculée (cm²/ml)", "As minimale (cm²/ml)", "Choix", "As fournie (cm²/ml)", "État"),
                listOf(
                    listOf(
                        "Principale",
                        ReportText.plain(parapet.mainSteel.designMomentKNm, 2),
                        ReportText.plain(parapet.mainSteel.calculatedAsCm2PerM, 2),
                        ReportText.plain(parapet.mainSteel.minimumAsCm2PerM, 2),
                        parapet.mainSteel.label,
                        ReportText.plain(parapet.mainSteel.providedAsCm2PerM, 2),
                        ReportText.statusLabel(parapet.mainSteel.status)
                    ),
                    listOf(
                        "Répartition",
                        ReportText.plain(parapet.distributionSteel.designMomentKNm, 2),
                        ReportText.plain(parapet.distributionSteel.calculatedAsCm2PerM, 2),
                        ReportText.plain(parapet.distributionSteel.minimumAsCm2PerM, 2),
                        parapet.distributionSteel.label,
                        ReportText.plain(parapet.distributionSteel.providedAsCm2PerM, 2),
                        ReportText.statusLabel(parapet.distributionSteel.status)
                    )
                ),
                columnWidthsCm = listOf(2.2, 2.0, 2.6, 2.6, 2.6, 2.6, 2.1),
                fontSizePt = 8
            )
            blocks += ReportBlock.Paragraph(
                "Situation dimensionnante retenue : ${parapet.governingSituation.label}. " +
                    "L'acrotère est ferraillé sur toute sa hauteur avec recouvrement des nappes dans la dalle."
            )
            runCatching { sheets.renderParapetSheet(parapet, dpi) }.getOrNull()?.let { output ->
                blocks += figure("acrotere", output, "Figure 11.1 — Fiche de l'acrotère : géométrie et ferraillage", 16.0)
            }
            blocks += ReportBlock.Heading(2, "11.3 Synthèse des vérifications", "s113")
            blocks += ReportBlock.ChecksTable("Contrôles de l'acrotère", parapet.checks)
            return ReportChapter(
                11,
                "Acrotère",
                "Console verticale soumise au poids propre, à la main courante et à la force sismique des éléments secondaires.",
                parapet.status,
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 12

        fun staircaseChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "12.1 Géométrie et méthode", "s121")
            if (stairs.isEmpty()) {
                blocks += ReportBlock.Note(
                    "Aucun escalier incliné n'a été détecté dans le modèle ETABS. Le chapitre est conservé pour " +
                        "mémoire ; renseigner la géométrie de l'escalier dans l'interface pour obtenir l'étude complète.",
                    "Escalier non détecté"
                )
                blocks += ReportBlock.Paragraph(
                    "La méthode retenue pour les escaliers en béton armé assimile la volée à une poutre-dalle " +
                        "inclinée de largeur unitaire, appuyée sur les paliers, et vérifie la flexion simple, " +
                        "l'effort tranchant et la flèche selon le BAEL 91."
                )
                return ReportChapter(
                    12,
                    "Escaliers",
                    "Étude de la volée d'escalier en poutre-dalle équivalente.",
                    CheckStatus.NOT_APPLICABLE,
                    blocks
                )
            }
            stairs.forEach { stair ->
                val geometry = stair.geometry
                blocks += ReportBlock.Paragraph(
                    "Escalier ${geometry.name} — ${geometry.geometryLabel}.",
                    bold = true,
                    align = ReportAlign.LEFT,
                    keepWithNext = true
                )
                blocks += ReportBlock.KeyValues(
                    "Géométrie — ${geometry.name}",
                    listOf(
                        "Hauteur totale" to "${ReportText.plain(geometry.totalRiseMeters, 2)} m",
                        "Nombre de contremarches" to "${geometry.numberOfRisers}",
                        "Nombre de volées" to "${geometry.numberOfFlights}",
                        "Giron" to "${ReportText.plain(geometry.treadDepthMeters * 100.0, 1)} cm",
                        "Emmarchement" to "${ReportText.plain(geometry.flightWidthMeters, 2)} m",
                        "Longueur de calcul" to "${ReportText.plain(geometry.designSpanMeters, 2)} m",
                        "Relation de Blondel (2h + g)" to "${ReportText.plain(geometry.blondelValueMeters, 3)} m",
                        "Épaisseur de paillasse" to "${ReportText.plain(geometry.waistThicknessMeters * 100.0, 1)} cm"
                    )
                )
                blocks += ReportBlock.Table(
                    "Charges — ${geometry.name}",
                    listOf("Charge", "Valeur", "Unité"),
                    listOf(
                        listOf("Charge permanente", ReportText.plain(stair.loads.permanentKNM2, 2), "kN/m²"),
                        listOf("Surcharge d'exploitation", ReportText.plain(stair.loads.variableKNM2, 2), "kN/m²"),
                        listOf("Charge ultime", ReportText.plain(stair.loads.ultimateKNM2, 2), "kN/m²"),
                        listOf("Charge de service", ReportText.plain(stair.loads.serviceKNM2, 2), "kN/m²"),
                        listOf("Charge linéique ELU", ReportText.plain(stair.loads.ultimateLineLoadKNPerM, 2), "kN/m")
                    ),
                    columnWidthsCm = listOf(8.0, 4.0, 4.5),
                    alignments = listOf(ReportAlign.LEFT, ReportAlign.RIGHT, ReportAlign.CENTER)
                )
                blocks += ReportBlock.Table(
                    "Sollicitations et ferraillage — ${geometry.name}",
                    listOf("Zone", "M (kN·m)", "As requise (cm²/ml)", "Choix", "As fournie (cm²/ml)", "État"),
                    listOf(
                        listOf(
                            "Travée",
                            ReportText.plain(stair.spanMomentUltimateKNm, 2),
                            ReportText.plain(stair.spanReinforcement.requiredAsCm2PerM, 2),
                            stair.spanReinforcement.label,
                            ReportText.plain(stair.spanReinforcement.providedAsCm2PerM, 2),
                            ReportText.statusLabel(stair.spanReinforcement.status)
                        ),
                        listOf(
                            "Appui",
                            ReportText.plain(abs(stair.supportMomentUltimateKNm), 2),
                            ReportText.plain(stair.supportReinforcement.requiredAsCm2PerM, 2),
                            stair.supportReinforcement.label,
                            ReportText.plain(stair.supportReinforcement.providedAsCm2PerM, 2),
                            ReportText.statusLabel(stair.supportReinforcement.status)
                        ),
                        listOf(
                            "Répartition",
                            "—",
                            ReportText.plain(stair.distributionReinforcement.requiredAsCm2PerM, 2),
                            stair.distributionReinforcement.label,
                            ReportText.plain(stair.distributionReinforcement.providedAsCm2PerM, 2),
                            ReportText.statusLabel(stair.distributionReinforcement.status)
                        )
                    ),
                    columnWidthsCm = listOf(2.4, 2.4, 3.0, 2.8, 3.0, 2.4)
                )
                blocks += ReportBlock.KeyValues(
                    "Flèche — ${geometry.name}",
                    listOf(
                        "Flèche totale Δft" to "${ReportText.plain(stair.deflection.deltaFtMeters * 1000.0, 2)} mm",
                        "Flèche admissible" to "${ReportText.plain(stair.deflection.allowableMeters * 1000.0, 2)} mm",
                        "Effort tranchant de calcul" to "${ReportText.num(stair.shearUltimateKN, 1)} kN",
                        "État" to ReportText.statusLabel(stair.status)
                    )
                )
                blocks += ReportBlock.ChecksTable("Contrôles de l'escalier ${geometry.name}", stair.checks)
            }
            return ReportChapter(
                12,
                "Escaliers",
                "Poutre-dalle inclinée : charges, moments, ferraillage et flèche.",
                CheckStatus.worst(stairs.map { it.status }),
                blocks
            )
        }

        // ---------------------------------------------------------------- chapitre 13

        fun foundationChapter(): ReportChapter {
            val blocks = mutableListOf<ReportBlock>()
            blocks += ReportBlock.Heading(2, "13.1 Dimensionnement des semelles filantes", "s131")
            val safe = request.foundations
            if (safe == null || foundations == null || foundations.footings.isEmpty()) {
                blocks += ReportBlock.Note(
                    "Aucun export SAFE n'a été fourni pour ce joint. Le chapitre des fondations est conservé pour " +
                        "mémoire ; importer les tables SAFE (familles de semelles, ressorts de sol, déplacements et " +
                        "efforts) pour obtenir le dimensionnement complet.",
                    "Fondations non disponibles"
                )
                blocks += ReportBlock.Paragraph(
                    "La méthode retenue pour les semelles filantes combine la portance du sol par le modèle de " +
                        "Winkler (σeff = Δ · Ks), la flexion simple longitudinale et transversale, et les vérifications " +
                        "de cisaillement et de largeur minimale."
                )
                return ReportChapter(
                    13,
                    "Fondations",
                    "Semelles filantes, portance du sol, ferraillage et plan des fondations.",
                    CheckStatus.NOT_APPLICABLE,
                    blocks
                )
            }
            blocks += ReportBlock.Paragraph(
                "Les semelles sont dimensionnées à partir des familles, ressorts et efforts lus dans l'export SAFE " +
                    "(source : ${safe.sourceName}). Le sol est modélisé par un module de réaction Ks = " +
                    "${ReportText.num(safe.geotechnical.subgradeModulusKNM3, 0)} kN/m³ pour une contrainte admissible " +
                    "de ${ReportText.plain(safe.geotechnical.allowableStressKNM2, 1)} kN/m²."
            )
            blocks += ReportBlock.Equation(ReportEquations.soilStress(), "Contrainte effective du sol (modèle de Winkler)")
            blocks += ReportBlock.Table(
                "Semelles filantes",
                listOf("Semelle", "Type", "B (m)", "H (m)", "L (m)", "M appui (kN·m)", "M travée (kN·m)", "V (kN)", "État"),
                foundations.footings.map { design ->
                    listOf(
                        design.family.name,
                        design.family.kind.label,
                        ReportText.plain(design.family.flangeWidthMeters, 2),
                        ReportText.plain(design.family.totalHeightMeters, 2),
                        ReportText.plain(design.forces?.ultimate?.stationLengthMeters ?: design.family.footingLengthMeters ?: 0.0, 2),
                        ReportText.plain(abs(design.forces?.designSupportMomentKNm ?: 0.0), 1),
                        ReportText.plain(design.forces?.designSpanMomentKNm ?: 0.0, 1),
                        ReportText.plain(design.forces?.designShearKN ?: 0.0, 1),
                        ReportText.statusLabel(design.status)
                    )
                },
                columnWidthsCm = listOf(2.8, 2.8, 1.6, 1.6, 1.6, 2.4, 2.4, 1.9, 2.0),
                fontSizePt = 8
            )
            blocks += ReportBlock.Table(
                "Ferraillage des semelles",
                listOf("Semelle", "Acier appui", "Acier travée", "Acier transversal", "τu (MPa)", "τlim (MPa)", "B min (m)"),
                foundations.footings.map { design ->
                    listOf(
                        design.family.name,
                        design.supportSteel.label,
                        design.spanSteel.label,
                        design.transverseSteel.label,
                        ReportText.plain(design.shearStressMPa, 3),
                        ReportText.plain(design.shearLimitMPa, 3),
                        ReportText.plain(design.minimumWidthMeters, 2)
                    )
                },
                columnWidthsCm = listOf(2.8, 3.0, 3.0, 3.0, 2.0, 2.0, 2.0)
            )
            blocks += ReportBlock.Heading(2, "13.2 Vérification de la portance du sol", "s132")
            if (foundations.soilChecks.isEmpty()) {
                blocks += ReportBlock.Note("Aucun déplacement vertical SAFE n'a permis de vérifier la portance.")
            } else {
                blocks += ReportBlock.Table(
                    "Portance du sol",
                    listOf("Semelle", "Ressort", "Δ (mm)", "σeff (kN/m²)", "σadm (kN/m²)", "Utilisation", "B min (m)", "État"),
                    foundations.soilChecks.map { check ->
                        listOf(
                            check.familyName,
                            check.springName,
                            ReportText.plain(check.result.input.settlementMeters * 1000.0, 2),
                            ReportText.plain(check.result.effectiveStressKNM2, 1),
                            ReportText.plain(check.result.allowableStressKNM2, 1),
                            ReportText.pct(check.result.utilization * 100.0),
                            ReportText.plain(check.result.minimumWidthMeters, 2),
                            ReportText.statusLabel(check.result.status)
                        )
                    },
                    columnWidthsCm = listOf(2.6, 2.6, 2.0, 2.4, 2.4, 2.2, 2.0, 2.2)
                )
            }
            blocks += ReportBlock.Heading(2, "13.3 Plan des fondations", "s133")
            runCatching { renderer.renderFoundationPlan(safe, safe.displacementEnvelope, "Plan des fondations — ${joint.jointName}", dpi) }
                .getOrNull()?.let { output ->
                    blocks += figure(
                        "fondations",
                        output,
                        "Figure 13.1 — Plan des fondations : semelles, ressorts, carte des tassements ELS et enveloppe M3",
                        16.5
                    )
                }
            blocks += ReportBlock.Heading(2, "13.4 Synthèse des vérifications", "s134")
            blocks += ReportBlock.ChecksTable(
                "Contrôles des fondations",
                foundations.footings.flatMap { it.checks } + foundations.soilChecks.flatMap { it.result.checks })
            return ReportChapter(
                13,
                "Fondations",
                "Semelles filantes, portance du sol, ferraillage et plan des fondations issu de SAFE.",
                foundations.status,
                blocks
            )
        }

        // ---------------------------------------------------------------- annexes

        fun annexes(chapters: List<ReportChapter>): List<ReportChapter> {
            val overall = CheckStatus.worst(chapters.map { it.status })
            val summaryTable = ReportBlock.Table(
                "Synthèse générale des vérifications",
                listOf("Chapitre", "Intitulé", "Statut", "Contrôles"),
                chapters.map { chapter ->
                    listOf(
                        "${chapter.number}",
                        chapter.title,
                        ReportText.statusLabel(chapter.status),
                        "${chapter.blocks.count { it is ReportBlock.ChecksTable }} tableau(x)"
                    )
                },
                columnWidthsCm = listOf(2.0, 8.5, 3.0, 3.0),
                alignments = listOf(ReportAlign.CENTER, ReportAlign.LEFT, ReportAlign.CENTER, ReportAlign.CENTER)
            )
            val synthesis = ReportChapter(
                14,
                "Annexe A — Synthèse générale des vérifications",
                "Tableau récapitulatif du statut de chaque chapitre.",
                overall,
                listOf(
                    ReportBlock.Paragraph(
                        "Le tableau ci-dessous récapitule le statut réglementaire de chaque chapitre. Un statut " +
                            "« Non vérifié » impose une reprise du dimensionnement avant visa du CTC."
                    ),
                    summaryTable,
                    ReportBlock.Verdict(
                        when (overall) {
                            CheckStatus.OK -> "Synthèse générale : l'ensemble des chapitres est vérifié."
                            CheckStatus.WARNING -> "Synthèse générale : des vérifications restent à justifier."
                            CheckStatus.FAILED -> "Synthèse générale : au moins un chapitre n'est pas vérifié."
                            CheckStatus.NOT_APPLICABLE -> "Synthèse générale : aucune vérification applicable."
                        },
                        overall
                    )
                ),
                numbered = false
            )

            val warningList = (request.model.warnings + request.results.warnings + geometry.warnings +
                seismic.warnings + foundations?.warnings.orEmpty()).distinct()
            val warnings = ReportChapter(
                15,
                "Annexe B — Avertissements et réserves",
                "${warningList.size} avertissement(s) recensé(s).",
                if (warningList.isEmpty()) CheckStatus.OK else CheckStatus.WARNING,
                listOf(
                    if (warningList.isEmpty()) {
                        ReportBlock.Paragraph("Aucun avertissement n'a été émis lors de l'import et du calcul.")
                    } else {
                        ReportBlock.Numbered(warningList)
                    },
                    ReportBlock.Paragraph(
                        "Les avertissements ci-dessus ne remettent pas nécessairement en cause les résultats, mais " +
                            "doivent être levés par le bureau d'études avant l'émission du visa."
                    )
                ),
                numbered = false
            )

            val nomenclature = ReportChapter(
                16,
                "Annexe C — Nomenclature des armatures",
                "Conventions de désignation utilisées dans la note.",
                CheckStatus.OK,
                listOf(
                    ReportBlock.Table(
                        "Nomenclature",
                        listOf("Désignation", "Signification"),
                        listOf(
                            listOf("nTφ", "n barres de diamètre φ (mm)"),
                            listOf("e = xx cm", "espacement des armatures"),
                            listOf("Lcl", "hauteur libre entre planchers"),
                            listOf("Lcr / Lrec", "longueur de la zone critique / de recouvrement"),
                            listOf("h_cr", "hauteur de la zone critique d'un voile"),
                            listOf("BaelR / BaelT", "fiches de flexion composée rectangulaire / en Té"),
                            listOf("Koka", "fiche de calcul des poutrelles continues"),
                            listOf("MSE / MDS", "méthode statique équivalente / méthode dynamique spectrale")
                        ),
                        columnWidthsCm = listOf(4.0, 12.5)
                    )
                ),
                numbered = false
            )
            return listOf(synthesis, warnings, nomenclature)
        }

        // ---------------------------------------------------------------- helpers

        private fun categoryLabel(category: ComboCategory): String = when (category) {
            ComboCategory.ELU -> "ELU"
            ComboCategory.ELS -> "ELS"
            ComboCategory.ELA -> "ELA (sismique)"
            ComboCategory.OTHER -> "Autre"
        }

        private fun spectrumTypeLabel(type: SpectrumType?): String = when (type) {
            SpectrumType.TYPE_1 -> "spectre type 1"
            SpectrumType.TYPE_2 -> "spectre type 2"
            null -> "spectre non tabulé"
        }

        private fun figure(key: String, output: GraphicOutput, caption: String, widthCm: Double): ReportBlock {
            figures[key] = output.png
            return ReportBlock.Figure(output.png, caption, widthCm, key)
        }
    }
}
