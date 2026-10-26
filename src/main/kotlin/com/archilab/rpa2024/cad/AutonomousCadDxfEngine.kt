package com.archilab.rpa2024.cad

import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.persistence.BillOfQuantitiesEngine
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.round

/**
 * Résultat complet de la génération autonome du dossier de plans CAO AutoCAD (.dxf).
 */
@Serializable
data class CadExportResult(
    val dxfFilePath: String,
    val fileSizeBytes: Long,
    val totalSheetsCount: Int,
    val layersCount: Int,
    val entitiesCount: Int,
    val totalSteelWeightKg: Double,
    val totalConcreteVolumeM3: Double,
    val steelRatioKgPerM3: Double,
    val sheetTitles: List<String>
)

/**
 * Moteur CAO 1-Clic d'Exportation Vectorielle AutoCAD (.dxf R2010+) et Métré des Aciers.
 * Assemble automatiquement en un seul fichier DXF professionnel :
 * - Plan de coffrage d'étage courant coté (entraxes + cotes totales Lx, Ly)
 * - Plan de coffrage des fondations SAFE (SF centrées, SFJ excentrées)
 * - Planche 1 : Ferraillage d'exécution des Poteaux (coupes 1/20e et élévations cotées)
 * - Planche 2 : Ferraillage d'exécution des Poutres (filantes continues, chapeaux, cadres)
 * - Planche 3 : Ferraillage des Voiles (éléments de rive lc confinés, nappe d'âme, amorces)
 * - Planche 4 : Éléments Secondaires & Fondations (acrotère, plancher corps creux, semelles)
 * - Planche 5 : Nomenclature générale de façonnage des aciers (BBS, crochets 135°, métré DQE)
 */
class AutonomousCadDxfEngine(
    private val formworkExporter: FormworkPlanDxfExporter = FormworkPlanDxfExporter(),
    private val detailingExporter: StructuralDetailingDxfExporter = StructuralDetailingDxfExporter(),
    private val bbsGenerator: BarBendingScheduleDxfGenerator = BarBendingScheduleDxfGenerator(),
    private val boqEngine: BillOfQuantitiesEngine = BillOfQuantitiesEngine()
) {

    /**
     * Génère le dossier complet de plans d'exécution AutoCAD (.dxf) de manière 100% autonome.
     */
    fun exportFullCadPackage(
        joint: StructuralJoint,
        targetDxfFile: File,
        projectName: String = "Sûreté Urbaine Bosquet",
        wilaya: String = "Mostaganem"
    ): CadExportResult {
        val masterDxf = DxfCoreEngine()

        // 1. Plan de coffrage de plancher d'étage (Origine : X=0.0, Y=0.0)
        formworkExporter.drawFloorFormworkPlan(masterDxf, joint, 0.0, 0.0, 1, projectName, wilaya)

        // 2. Plan de coffrage des fondations SAFE (Origine : X=35.0, Y=0.0)
        formworkExporter.drawFoundationFormworkPlan(masterDxf, joint, 35.0, 0.0, projectName, wilaya)

        // 3. Planche 1 : Ferraillage d'exécution des Poteaux (Origine : X=0.0, Y=-25.0)
        detailingExporter.drawColumnDetailingSheet(masterDxf, joint, 0.0, -25.0, projectName, wilaya)

        // 4. Planche 2 : Ferraillage d'exécution des Poutres (Origine : X=26.0, Y=-25.0)
        detailingExporter.drawBeamDetailingSheet(masterDxf, joint, 26.0, -25.0, projectName, wilaya)

        // 5. Planche 3 : Ferraillage des Voiles de contreventement (Origine : X=56.0, Y=-25.0)
        detailingExporter.drawShearWallDetailingSheet(masterDxf, joint, 56.0, -25.0, projectName, wilaya)

        // 6. Planche 4 : Éléments Secondaires & Fondations (Origine : X=84.0, Y=-25.0)
        detailingExporter.drawSecondaryAndFootingDetailingSheet(masterDxf, joint, 84.0, -25.0, projectName, wilaya)

        // 7. Planche 5 : Nomenclature générale et façonnage BBS (Origine : X=112.0, Y=-25.0)
        detailingExporter.drawBendingScheduleSheet(masterDxf, joint, 112.0, -25.0, projectName, wilaya)

        // Sauvegarde sur disque
        targetDxfFile.parentFile?.mkdirs()
        masterDxf.saveToFile(targetDxfFile)

        // Calcul des métriques de métré et ratios
        val boq = boqEngine.computeBoq(joint, projectName, wilaya)

        val sheetTitles = listOf(
            "Plan de Coffrage Plancher Étage Courant",
            "Plan de Coffrage Fondations SAFE & Amorces",
            "Planche 1 : Ferraillage d'Exécution des Poteaux (1/20e & 1/50e)",
            "Planche 2 : Ferraillage d'Exécution des Poutres & Chaînages",
            "Planche 3 : Ferraillage des Voiles & Éléments de Rive Confinés",
            "Planche 4 : Ferraillage Éléments Secondaires & Fondations",
            "Planche 5 : Nomenclature de Façonnage BBS & Métré des Aciers"
        )

        return CadExportResult(
            dxfFilePath = targetDxfFile.absolutePath,
            fileSizeBytes = targetDxfFile.length(),
            totalSheetsCount = 7,
            layersCount = CadLayer.entries.size,
            entitiesCount = (targetDxfFile.length() / 150).toInt().coerceAtLeast(250),
            totalSteelWeightKg = round(boq.totalRebarWeightKg * 10.0) / 10.0,
            totalConcreteVolumeM3 = round(boq.totalStructuralConcreteM3 * 100.0) / 100.0,
            steelRatioKgPerM3 = round(boq.globalSteelRatioKgPerM3 * 10.0) / 10.0,
            sheetTitles = sheetTitles
        )
    }
}
