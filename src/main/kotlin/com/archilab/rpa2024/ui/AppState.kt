package com.archilab.rpa2024.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.archilab.rpa2024.engine.foundations.SafeFoundationExtractor
import com.archilab.rpa2024.engine.foundations.SafeFoundationModel
import com.archilab.rpa2024.model.InfillType
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.parser.EtabsImport
import com.archilab.rpa2024.parser.EtabsUniversalIngestor
import com.archilab.rpa2024.report.CaptureLibrary
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.NoteDeCalculArtifacts
import com.archilab.rpa2024.report.NoteDeCalculBundle
import com.archilab.rpa2024.report.NoteDeCalculOptions
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.report.PdfReportExporter
import com.archilab.rpa2024.report.ReferenceDocxImageImporter
import com.archilab.rpa2024.report.ReferenceNoteDeCalculComposer
import com.archilab.rpa2024.report.ReportDocument
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Etat de calcul d'un joint du projet. */
enum class JointStatus { VIDE, EN_COURS, PRET, ERREUR }

/** Formats d'export proposes par l'onglet de generation CTC. */
enum class ExportFormat { WORD, PDF }

/**
 * Espace de travail d'un joint : configuration sismique, fichiers sources, resultats
 * d'analyse et document de note de calcul. Chaque joint est independant : le projet
 * peut contenir autant de joints que l'etude en comporte.
 */
class JointWorkspace(
    val id: Int,
    name: String = SampleJointA.JOINT_NAME
) {
    var name by mutableStateOf(name)
    var projectTitle by mutableStateOf("1500 LLV BOSQUET")
    var wilaya by mutableStateOf("Sidi Bel Abbès")
    var client by mutableStateOf("Direction du Logement — Sidi Bel Abbès")
    var engineer by mutableStateOf("Archilab — Bureau d'études structures")
    var reference by mutableStateOf("NDC-JOINT-A-RPA2024")
    var revision by mutableStateOf("A")

    var zoneCode by mutableStateOf("III")
    var siteCode by mutableStateOf("S3")
    var importanceCode by mutableStateOf("1A")
    var bracingId by mutableStateOf(3)
    var occupancyCode by mutableStateOf("HABITATION_BUREAUX")
    var infill by mutableStateOf(InfillType.DENSE)
    var planRegular by mutableStateOf(true)
    var elevationRegular by mutableStateOf(true)
    var redundantWalls by mutableStateOf(false)
    var manualWeightText by mutableStateOf("")

    var sample by mutableStateOf(true)
    var modelPath by mutableStateOf<Path?>(null)
    var resultsPath by mutableStateOf<Path?>(null)
    var safePath by mutableStateOf<Path?>(null)

    var options by mutableStateOf(NoteDeCalculOptions())
    var status by mutableStateOf(JointStatus.VIDE)
    var imported by mutableStateOf<EtabsImport?>(null)
    var foundations by mutableStateOf<SafeFoundationModel?>(null)
    var artifacts by mutableStateOf<NoteDeCalculArtifacts?>(null)
    var referenceBase by mutableStateOf<NoteDeCalculArtifacts?>(null)
    var referenceDocument by mutableStateOf<ReportDocument?>(null)
    var capturePlaceholders by mutableStateOf<List<String>>(emptyList())
    val captureFiles = mutableStateMapOf<String, Path>()
    var captureMessage by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var warnings by mutableStateOf<List<String>>(emptyList())
    var analysisInfo by mutableStateOf<String?>(null)

    var exportBusy by mutableStateOf(false)
    var exportMessage by mutableStateOf<String?>(null)
    var exportError by mutableStateOf<String?>(null)
    var lastExport by mutableStateOf<Path?>(null)

    fun config(): SeismicConfig = SeismicConfig(
        zoneCode = zoneCode,
        siteClassCode = siteCode,
        importanceGroupCode = importanceCode,
        occupancyCaseCode = occupancyCode,
        bracingSystemId = bracingId,
        infillType = infill,
        planRegular = planRegular,
        elevationRegular = elevationRegular,
        redundantWallsInPlan = redundantWalls
    )

    fun manualWeightKN(): Double? {
        val text = manualWeightText.replace(',', '.').trim()
        if (text.isEmpty()) return null
        return text.toDoubleOrNull()
    }

    val displayTitle: String
        get() = projectTitle.ifBlank { name }

    val exportBaseName: String
        get() = "NDC_${name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')}_RPA2024"
}

/** Projet multi-joints et navigation entre les huit onglets de l'application. */
class ProjectState {
    val joints = mutableStateListOf(JointWorkspace(1))
    var selectedIndex by mutableStateOf(0)
    var selectedTab by mutableStateOf(0)
    var globalMessage by mutableStateOf<String?>(null)

    val selected: JointWorkspace?
        get() = joints.getOrNull(selectedIndex)

    fun addJoint(): JointWorkspace {
        val template = selected
        val joint = JointWorkspace(
            id = (joints.maxOfOrNull { it.id } ?: 0) + 1,
            name = "JOINT ${('A' + joints.size)}"
        )
        template?.let {
            joint.projectTitle = it.projectTitle
            joint.wilaya = it.wilaya
            joint.client = it.client
            joint.engineer = it.engineer
            joint.zoneCode = it.zoneCode
            joint.siteCode = it.siteCode
            joint.importanceCode = it.importanceCode
            joint.bracingId = it.bracingId
            joint.occupancyCode = it.occupancyCode
            joint.infill = it.infill
            joint.planRegular = it.planRegular
            joint.elevationRegular = it.elevationRegular
            joint.sample = false
            joint.reference = it.reference.replace(it.name.replace(" ", "-"), joint.name.replace(" ", "-"))
        }
        joints += joint
        selectedIndex = joints.lastIndex
        selectedTab = 0
        return joint
    }

    fun removeJoint(joint: JointWorkspace): Boolean {
        if (joints.size <= 1) return false
        val index = joints.indexOf(joint)
        if (index < 0) return false
        joints.removeAt(index)
        selectedIndex = selectedIndex.coerceIn(0, joints.lastIndex)
        return true
    }
}

/**
 * Orchestrateur de l'interface : import ETABS/SAFE, composition de la note de calcul,
 * exports Word et PDF. Tous les calculs lourds sont executes hors du fil graphique.
 */
class ProjectController(private val scope: CoroutineScope) {

    private val ingestor = EtabsUniversalIngestor()
    private val bundle = NoteDeCalculBundle()
    private val referenceComposer = ReferenceNoteDeCalculComposer()
    private val foundationsExtractor = SafeFoundationExtractor()
    private val wordExporter = CtcWordReportGenerator()
    private val pdfExporter = PdfReportExporter()

    fun analyze(project: ProjectState, joint: JointWorkspace) {
        if (joint.status == JointStatus.EN_COURS) return
        val weight = joint.manualWeightText.trim()
        if (weight.isNotEmpty() && joint.manualWeightKN() == null) {
            joint.status = JointStatus.ERREUR
            joint.error = "Poids sismique manuel invalide : « $weight »."
            return
        }
        if (!joint.sample && joint.modelPath == null) {
            joint.status = JointStatus.ERREUR
            joint.error = "Sélectionnez un fichier modèle ETABS (.e2k, .xlsx, .mdb ou .txt) ou chargez l'exemple NDC."
            return
        }

        joint.status = JointStatus.EN_COURS
        joint.error = null
        joint.warnings = emptyList()
        project.globalMessage = "Analyse du joint ${joint.name} en cours…"

        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { prepare(joint) }
                joint.imported = prepared.imported
                joint.foundations = prepared.foundations
                val artifacts = withContext(Dispatchers.IO) { composeArtifacts(joint, prepared.request) }
                joint.artifacts = artifacts
                joint.analysisInfo = buildString {
                    append("${prepared.request.model.frames.size} barres · ")
                    append("${prepared.request.model.areas.size} surfaces · ")
                    append("${prepared.request.model.points.size} nœuds · ")
                    append("${artifacts.document.chapters.size} chapitres")
                    prepared.foundations?.let { append(" · ${it.families.size} familles de semelles SAFE") }
                }
                joint.warnings = prepared.warnings
                joint.status = JointStatus.PRET
                project.globalMessage = "Joint ${joint.name} analysé : ${artifacts.document.chapters.size} chapitres générés."
            } catch (error: Throwable) {
                joint.status = JointStatus.ERREUR
                joint.error = error.message ?: error.toString()
                project.globalMessage = "Échec de l'analyse du joint ${joint.name}."
            }
        }
    }

    private data class Prepared(
        val request: NoteDeCalculRequest,
        val imported: EtabsImport,
        val foundations: SafeFoundationModel?,
        val warnings: List<String>
    )

    private fun prepare(joint: JointWorkspace): Prepared {
        val config = joint.config()
        val imported = if (joint.sample) {
            SampleJointA.load(config, joint.name.ifBlank { SampleJointA.JOINT_NAME })
        } else {
            ingestor.importJoint(joint.modelPath!!, joint.name.ifBlank { "Joint" }, config, joint.resultsPath)
        }
        val foundations = if (joint.sample) {
            SampleSafeFoundation.load()
        } else {
            joint.safePath?.let { foundationsExtractor.read(it) }
        }
        val request = NoteDeCalculRequest(
            model = imported.model,
            results = imported.results,
            foundations = foundations,
            projectTitle = joint.projectTitle,
            wilaya = joint.wilaya,
            client = joint.client,
            engineer = joint.engineer,
            reference = joint.reference,
            revision = joint.revision,
            options = joint.options.copy(manualSeismicWeightKN = joint.manualWeightKN())
        )
        return Prepared(
            request = request,
            imported = imported,
            foundations = foundations,
            warnings = (imported.model.warnings + imported.results.warnings + (foundations?.warnings.orEmpty())).distinct()
        )
    }

    fun loadSample(project: ProjectState, joint: JointWorkspace) {
        joint.sample = true
        joint.modelPath = null
        joint.resultsPath = null
        joint.safePath = null
        joint.name = SampleJointA.JOINT_NAME
        joint.zoneCode = "III"
        joint.siteCode = "S3"
        joint.importanceCode = "1A"
        joint.bracingId = 3
        joint.occupancyCode = "HABITATION_BUREAUX"
        joint.infill = InfillType.DENSE
        joint.planRegular = true
        joint.elevationRegular = true
        joint.redundantWalls = false
        joint.manualWeightText = ""
        analyze(project, joint)
    }

    fun importFile(joint: JointWorkspace, path: Path) {
        val name = path.fileName.toString()
        val lower = name.lowercase(Locale.FRANCE)
        when {
            lower.endsWith(".e2k") -> {
                joint.modelPath = path
                joint.sample = false
            }
            lower.contains("safe") || lower.contains("fondation") -> joint.safePath = path
            joint.modelPath == null || joint.sample -> {
                joint.modelPath = path
                joint.sample = false
            }
            else -> joint.resultsPath = path
        }
        joint.status = JointStatus.VIDE
        joint.error = null
    }

    fun export(project: ProjectState, joint: JointWorkspace, format: ExportFormat) {
        if (joint.exportBusy) return
        if (joint.imported == null) {
            joint.exportError = "Lancez d'abord l'analyse du joint avant de générer la note de calcul."
            return
        }
        val target = chooseTarget(joint, format) ?: return
        joint.exportBusy = true
        joint.exportError = null
        joint.exportMessage = null
        project.globalMessage = "Génération de la note ${if (format == ExportFormat.WORD) "Word" else "PDF"}…"

        scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) { prepare(joint) }
                val artifacts = withContext(Dispatchers.IO) { composeArtifacts(joint, prepared.request) }
                joint.artifacts = artifacts
                withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.WORD -> wordExporter.write(artifacts.document, target)
                        ExportFormat.PDF -> pdfExporter.write(artifacts.document, target)
                    }
                }
                joint.imported = prepared.imported
                joint.foundations = prepared.foundations
                joint.lastExport = target
                joint.exportMessage = "Note de calcul exportée : ${target.toAbsolutePath()}"
                project.globalMessage = "Export ${format.name.lowercase(Locale.FRANCE)} terminé."
            } catch (error: Throwable) {
                joint.exportError = error.message ?: error.toString()
                project.globalMessage = "Échec de l'export de la note de calcul."
            } finally {
                joint.exportBusy = false
            }
        }
    }

    /**
     * Compose la note de reference (treize chapitres CTC, captures logicielles) puis
     * injecte les images associees dans l'espace de travail. En cas d'indisponibilite
     * du composeur de reference, la note standard reste produite avec un avertissement.
     */
    private fun composeArtifacts(joint: JointWorkspace, request: NoteDeCalculRequest): NoteDeCalculArtifacts {
        return try {
            val reference = referenceComposer.compose(request)
            joint.referenceBase = reference.base
            joint.referenceDocument = reference.document
            joint.capturePlaceholders = reference.capturePlaceholders
            val base = reference.base.copy(document = reference.document)
            applyCaptures(joint, base)
        } catch (error: Throwable) {
            joint.capturePlaceholders = emptyList()
            joint.referenceBase = null
            joint.referenceDocument = null
            joint.captureMessage = "Composeur de reference indisponible : ${error.message}. Note standard utilisee."
            bundle.build(request)
        }
    }

    fun setCapture(joint: JointWorkspace, tag: String, path: Path?) {
        if (path == null) {
            joint.captureFiles.remove(tag)
            joint.captureMessage = "Capture retiree : ${shortTag(tag)}."
        } else {
            if (!CaptureLibrary.isSupportedImage(path)) {
                joint.captureMessage = "Image non supportee : ${path.fileName}. Utiliser PNG, JPG, BMP ou GIF."
                return
            }
            joint.captureFiles[tag] = path
            joint.captureMessage = "Capture associee : ${path.fileName}."
        }
        refreshCaptures(joint)
    }

    fun importCaptureFolder(joint: JointWorkspace, directory: Path): Int {
        val document = joint.referenceDocument ?: return 0
        val base = joint.referenceBase ?: return 0
        val images = Files.list(directory).use { stream ->
            stream.filter { CaptureLibrary.isSupportedImage(it) }
                .sorted(compareBy { it.fileName.toString().lowercase(Locale.FRANCE) })
                .toList()
        }
        if (images.isEmpty()) {
            joint.captureMessage = "Aucune image PNG/JPG valide dans ${directory.fileName}."
            return 0
        }
        val (application, mapping) = CaptureLibrary.applyByOrder(document, images)
        joint.captureFiles.clear()
        joint.captureFiles.putAll(mapping)
        joint.artifacts = base.copy(document = application.document)
        joint.captureMessage = "${mapping.size} capture(s) associee(s) dans l'ordre des reperes."
        return mapping.size
    }

    /**
     * Extrait les images d'un DOCX de reference puis les affecte dans l'ordre des
     * reperes. L'utilisateur doit ensuite controler/remplacer les associations :
     * une capture de projet ne doit jamais etre validee sans revue.
     */
    fun importReferenceDocx(joint: JointWorkspace, document: Path): Int {
        val base = joint.referenceBase ?: return 0
        val reference = joint.referenceDocument ?: return 0
        val target = Path.of("build", "captures", joint.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            .ifBlank { "joint" })
        return try {
            val extraction = ReferenceDocxImageImporter.extract(document, target)
            if (extraction.images.isEmpty()) {
                joint.captureMessage = "Aucune image exploitable dans ${document.fileName}."
                return 0
            }
            val (application, mapping) = CaptureLibrary.applyByOrder(reference, extraction.images)
            joint.captureFiles.clear()
            joint.captureFiles.putAll(mapping)
            joint.artifacts = base.copy(document = application.document)
            joint.captureMessage = "${extraction.count} image(s) extraite(s) du DOCX ; " +
                "${mapping.size} capture(s) associee(s) dans l'ordre des reperes. " +
                "Verifier et remplacer si necessaire."
            mapping.size
        } catch (error: Throwable) {
            joint.captureMessage = "Extraction DOCX impossible : ${error.message}"
            0
        }
    }

    fun clearCaptures(joint: JointWorkspace) {
        joint.captureFiles.clear()
        refreshCaptures(joint)
        joint.captureMessage = "Toutes les captures ont ete retirees du document."
    }

    fun refreshCaptures(joint: JointWorkspace) {
        val base = joint.referenceBase ?: return
        val document = joint.referenceDocument ?: return
        joint.artifacts = applyCaptures(joint, base.copy(document = document))
    }

    private fun applyCaptures(joint: JointWorkspace, base: NoteDeCalculArtifacts): NoteDeCalculArtifacts {
        val application = CaptureLibrary.apply(base.document, joint.captureFiles.toMap())
        val total = joint.capturePlaceholders.size
        joint.captureMessage = when {
            total == 0 -> null
            application.invalidFiles.isNotEmpty() ->
                "${application.invalidFiles.size} image(s) invalide(s) ; ${application.appliedCount}/$total capture(s) associee(s)."
            else -> "${application.appliedCount}/$total capture(s) associee(s) au document."
        }
        return base.copy(document = application.document)
    }

    private fun shortTag(tag: String): String = tag.take(70).let { if (tag.length > 70) "$it..." else it }

    private fun chooseTarget(joint: JointWorkspace, format: ExportFormat): Path? {
        val extension = if (format == ExportFormat.WORD) ".docx" else ".pdf"
        val dialog = FileDialog(null as Frame?, "Enregistrer la note de calcul CTC", FileDialog.SAVE)
        dialog.file = joint.exportBaseName + extension
        dialog.isVisible = true
        val directory = dialog.directory ?: return null
        val file = dialog.file ?: return null
        val path = File(directory, file).toPath()
        return if (path.fileName.toString().lowercase(Locale.FRANCE).endsWith(extension)) {
            path
        } else {
            path.resolveSibling(path.fileName.toString() + extension)
        }
    }
}

/** Formatage francais commun a l'interface. */
object UiText {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH)

    fun number(value: Double?, decimals: Int = 2): String {
        if (value == null) return "n/d"
        return String.format(Locale.FRANCE, "%,.${decimals}f", value)
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
    }

    fun today(): String = dateFormatter.format(LocalDate.now()).replaceFirstChar { it.uppercase(Locale.FRENCH) }

    fun statusLabel(status: JointStatus): String = when (status) {
        JointStatus.VIDE -> "À analyser"
        JointStatus.EN_COURS -> "Analyse en cours"
        JointStatus.PRET -> "Note générée"
        JointStatus.ERREUR -> "Erreur"
    }
}
