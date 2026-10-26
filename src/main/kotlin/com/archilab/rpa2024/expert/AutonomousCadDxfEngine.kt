package com.archilab.rpa2024.expert

import com.archilab.rpa2024.cad.BarBendingScheduleDxfGenerator
import com.archilab.rpa2024.cad.CadProjectInfo
import com.archilab.rpa2024.cad.CadSheetFormat
import com.archilab.rpa2024.cad.DetailingPlanRequest
import com.archilab.rpa2024.cad.DetailingPlanResult
import com.archilab.rpa2024.cad.DxfCoreEngine
import com.archilab.rpa2024.cad.FormworkPlanDxfExporter
import com.archilab.rpa2024.cad.FormworkPlanRequest
import com.archilab.rpa2024.cad.FormworkPlanResult
import com.archilab.rpa2024.cad.StructuralDetailingDxfExporter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class CadExportOptions(
    val sheetFormat: CadSheetFormat = CadSheetFormat.A1,
    val selectedStoryNames: List<String> = emptyList(),
    val includeFormwork: Boolean = true,
    val includeDetailing: Boolean = true,
    val includeBbsXlsx: Boolean = true,
    val includeBbsDxf: Boolean = true
)

/** Livrables vectoriels du cycle CAO. */
data class CadExportReport(
    val formworkPath: Path? = null,
    val detailingPath: Path? = null,
    val bbsDxfPath: Path? = null,
    val bbsXlsxPath: Path? = null,
    val formworkResult: FormworkPlanResult? = null,
    val detailingResult: DetailingPlanResult? = null,
    val warnings: List<String> = emptyList()
) {
    val files: List<Path> get() = listOfNotNull(formworkPath, detailingPath, bbsDxfPath, bbsXlsxPath)
    val sheetCount: Int get() = (formworkResult?.sheetCount ?: 0) + (detailingResult?.sheetCount ?: 0)
}

/**
 * Cycle 6 — moteur CAO autonome.
 *
 * Réutilise le noyau DXF natif AC1024 : plans de coffrage par niveau, planches de
 * ferraillage poteaux/poutres/voiles/planchers/acrotère/escaliers/semelles, nomenclature
 * façonnée DXF et classeur de métré. Aucune entité n'est codée en dur.
 */
class AutonomousCadDxfEngine(
    private val formworkExporter: FormworkPlanDxfExporter = FormworkPlanDxfExporter(),
    private val detailingExporter: StructuralDetailingDxfExporter = StructuralDetailingDxfExporter(),
    private val bbsGenerator: BarBendingScheduleDxfGenerator = BarBendingScheduleDxfGenerator()
) {

    fun export(
        snapshot: CrossModelSnapshot,
        project: CadProjectInfo,
        outputDirectory: Path,
        options: CadExportOptions = CadExportOptions()
    ): CadExportReport {
        Files.createDirectories(outputDirectory)
        val warnings = mutableListOf<String>()
        val stem = sanitize("${project.projectTitle}_${snapshot.jointName}")
        var formworkPath: Path? = null
        var detailingPath: Path? = null
        var formworkResult: FormworkPlanResult? = null
        var detailingResult: DetailingPlanResult? = null

        if (options.includeFormwork) {
            val request = FormworkPlanRequest(
                model = snapshot.model,
                foundationGeneration = null,
                project = project,
                selectedStoryNames = options.selectedStoryNames,
                sheetFormat = options.sheetFormat,
                includeFoundationPlan = snapshot.foundations != null
            )
            formworkPath = outputDirectory.resolve("COFFRAGE_${stem}.dxf")
            formworkResult = formworkExporter.export(request, formworkPath)
            warnings += formworkResult.warnings
            if (snapshot.foundations == null) {
                warnings += "Plan de coffrage des fondations non tracé : export SAFE absent."
            } else {
                warnings += "Le plan de coffrage des fondations SAFE est détaillé dans les planches de ferraillage."
            }
        }

        if (options.includeDetailing) {
            val request = DetailingPlanRequest(
                model = snapshot.model,
                results = snapshot.results,
                foundationModel = snapshot.foundations,
                project = project,
                sheetFormat = options.sheetFormat,
                startingSheetNumber = (formworkResult?.sheetCount ?: 0) + 1
            )
            detailingPath = outputDirectory.resolve("FERRAILLAGE_${stem}.dxf")
            detailingResult = detailingExporter.export(request, detailingPath)
            warnings += detailingResult.warnings
        }

        var bbsDxfPath: Path? = null
        var bbsXlsxPath: Path? = null
        val schedule = detailingResult?.schedule
        if (schedule != null && options.includeBbsDxf) {
            bbsDxfPath = outputDirectory.resolve("NOMENCLATURE_${stem}.dxf")
            val document = DxfCoreEngine("NOMENCLATURE_${snapshot.jointName}")
            bbsGenerator.draw(document, schedule, project, format = options.sheetFormat)
            document.write(bbsDxfPath)
        }
        if (schedule != null && options.includeBbsXlsx) {
            bbsXlsxPath = outputDirectory.resolve("DQE_ACIERS_${stem}.xlsx")
            bbsGenerator.writeXlsx(schedule, project, bbsXlsxPath)
        }
        return CadExportReport(
            formworkPath = formworkPath,
            detailingPath = detailingPath,
            bbsDxfPath = bbsDxfPath,
            bbsXlsxPath = bbsXlsxPath,
            formworkResult = formworkResult,
            detailingResult = detailingResult,
            warnings = warnings.distinct()
        )
    }

    fun oneClick(
        snapshot: CrossModelSnapshot,
        project: CadProjectInfo,
        outputDirectory: Path
    ): CadExportReport = export(snapshot, project, outputDirectory, CadExportOptions())

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "PROJET" }
}
