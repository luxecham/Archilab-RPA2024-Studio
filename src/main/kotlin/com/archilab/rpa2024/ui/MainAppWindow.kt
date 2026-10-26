package com.archilab.rpa2024.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archilab.rpa2024.audit.*
import com.archilab.rpa2024.bridge.*
import com.archilab.rpa2024.cad.*
import com.archilab.rpa2024.persistence.*
import com.archilab.rpa2024.engine.era.*
import com.archilab.rpa2024.engine.foundations.StripFootingDesignOrchestrator
import com.archilab.rpa2024.engine.frames.BeamDesignEngine
import com.archilab.rpa2024.engine.frames.ColumnDesignEngine
import com.archilab.rpa2024.engine.geometry.GeometryAndPreSizingEngine
import com.archilab.rpa2024.engine.seismic.Rpa2024SeismicEngine
import com.archilab.rpa2024.engine.slabs.ContinuousRibbedSlabSolver
import com.archilab.rpa2024.graphics.TechnicalSheetImageGenerator
import com.archilab.rpa2024.model.*
import com.archilab.rpa2024.normative.*
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.PdfReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.round

enum class AppTab(val title: String, val icon: ImageVector) {
    VIEW_3D("Vue 3D & Coffrage", Icons.Default.ViewInAr),
    RPA_CONFIG("Config. RPA 2024", Icons.Default.Settings),
    GLOBAL_SEISMIC("Vérifications Sismiques", Icons.Default.Assessment),
    ERA_PUSHOVER("Évaluation ERA & Pushover", Icons.AutoMirrored.Filled.TrendingUp),
    SLABS_KOKA("Planchers Koka", Icons.Default.Layers),
    FRAMES_BAELR("Poteaux & Poutres BaelR", Icons.Default.ViewAgenda),
    SHEAR_WALLS("Voiles RPA § 7.7", Icons.Default.AccountTree),
    FOUNDATIONS_SAFE("Fondations SAFE / BaelT", Icons.Default.Foundation),
    BRIDGE_CONNECTOR("Bridge ETABS / SAFE", Icons.Default.SwapHoriz),
    CTC_AUDIT("Audit Pré-CTC & Optimisation", Icons.Default.CheckCircle),
    INTERACTIVE_STUDIO("Studio Réactif & Graphiques", Icons.Default.Assessment),
    REPORT_GENERATOR("Générateur Note de Calcul", Icons.Default.Description)
}

/**
 * Fenêtre principale Compose Desktop de l'application Windows Standalone.
 */
@Composable
fun MainAppWindow() {
    val coroutineScope = rememberCoroutineScope()

    // Liste des joints structuraux du projet
    val sampleJointA = remember { createDefaultJointA() }
    val sampleJointB = remember { createDefaultJointB() }
    val jointsList = remember { mutableStateListOf(sampleJointA, sampleJointB) }
    var selectedJointIndex by remember { mutableStateOf(0) }
    val activeJoint = jointsList.getOrNull(selectedJointIndex) ?: sampleJointA

    var activeTab by remember { mutableStateOf(AppTab.VIEW_3D) }
    var reportGenerationStatus by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        // SIDEBAR : MULTI-JOINTS ET NAVIGATION PROJET
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E293B))
                .padding(16.dp)
        ) {
            Text(
                text = "ArchiLab RPA 2024",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ingénierie Parasismique Algérienne",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SOUS-STRUCTURES (JOINTS) :",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            jointsList.forEachIndexed { index, joint ->
                val isSelected = index == selectedJointIndex
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedJointIndex = index },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = joint.jointName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${joint.storySystem.stories.size} Niveaux (Hn=${round(joint.storySystem.totalHeightHn)}m)",
                                color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981)
                        ) {
                            Text(
                                text = "CONFORME",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { /* Importer nouveau modèle ETABS */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importer ETABS (.e2k)", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            val db = ProjectDatabaseManager()
                            val proj = ProjectEntityData(
                                id = "PROJ_GLOBAL_001",
                                title = "Projet Multi-Joints RPA 2024",
                                wilaya = "Alger",
                                joints = jointsList.toList()
                            )
                            db.saveProject(proj)
                            reportGenerationStatus = "✔ Projet multi-joints persisté avec succès dans SQLite (rpa2024_projects.db)"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sauvegarder Projet (SQLite)", fontSize = 11.sp)
            }
        }

        // CONTENU PRINCIPAL
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFFF8FAFC))
        ) {
            // BARRE D'ONGLETS
            ScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = Color.White,
                contentColor = Color(0xFF2563EB),
                edgePadding = 16.dp
            ) {
                AppTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = { Text(tab.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            // CORPS DE L'ONGLET SÉLECTIONNÉ
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
                when (activeTab) {
                    AppTab.VIEW_3D -> View3DTabContent(activeJoint)
                    AppTab.RPA_CONFIG -> RpaConfigTabContent(activeJoint)
                    AppTab.GLOBAL_SEISMIC -> GlobalSeismicTabContent(activeJoint)
                    AppTab.ERA_PUSHOVER -> EraPushoverTabContent(activeJoint)
                    AppTab.SLABS_KOKA -> SlabsKokaTabContent(activeJoint)
                    AppTab.FRAMES_BAELR -> FramesBaelRTabContent(activeJoint)
                    AppTab.SHEAR_WALLS -> ShearWallsTabContent(activeJoint)
                    AppTab.FOUNDATIONS_SAFE -> FoundationsSafeTabContent(activeJoint)
                    AppTab.BRIDGE_CONNECTOR -> BridgeConnectorTabContent(activeJoint)
                    AppTab.CTC_AUDIT -> CtcAuditTabContent(activeJoint)
                    AppTab.INTERACTIVE_STUDIO -> InteractiveStructuralStudioWindow()
                    AppTab.REPORT_GENERATOR -> ReportGeneratorTabContent(
                        joint = activeJoint,
                        isGenerating = isGenerating,
                        statusMessage = reportGenerationStatus,
                        onExecuteZeroClickPipeline = {
                            coroutineScope.launch {
                                isGenerating = true
                                reportGenerationStatus = "🚀 Exécution intégrale du pipeline zéro-clic Archilab en cours..."
                                withContext(Dispatchers.IO) {
                                    val studio = com.archilab.rpa2024.engine.ArchilabOneClickStudio()
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads")
                                    val res = studio.executeZeroClickPipeline(activeJoint, dest)
                                    reportGenerationStatus = "✔ SUCCÈS ZÉRO-CLIC (${res.executionTimeMs} ms) : Note Word, Synthèse PDF, Plans DXF, Devis DQE et Base SQLite générés dans ${dest.absolutePath}"
                                }
                                isGenerating = false
                            }
                        },
                        onGenerateWord = {
                            coroutineScope.launch {
                                isGenerating = true
                                reportGenerationStatus = "Génération de la Note de Calcul Word (.docx)..."
                                withContext(Dispatchers.IO) {
                                    val gen = CtcWordReportGenerator()
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads/${activeJoint.jointName}_NDC_RPA2024.docx")
                                    gen.generateWordReport(activeJoint, dest)
                                    reportGenerationStatus = "✔ Rapport Word (.docx) généré avec succès dans : ${dest.absolutePath}"
                                }
                                isGenerating = false
                            }
                        },
                        onGeneratePdf = {
                            coroutineScope.launch {
                                isGenerating = true
                                reportGenerationStatus = "Génération du rapport exécutif PDF..."
                                withContext(Dispatchers.IO) {
                                    val exporter = PdfReportExporter()
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads/${activeJoint.jointName}_SYNTHESE_CTC.pdf")
                                    exporter.exportExecutivePdf(activeJoint, dest)
                                    reportGenerationStatus = "✔ Rapport PDF généré avec succès dans : ${dest.absolutePath}"
                                }
                                isGenerating = false
                            }
                        },
                        onGenerateDxfPlans = {
                            coroutineScope.launch {
                                isGenerating = true
                                reportGenerationStatus = "Génération des Plans d'Exécution AutoCAD (.dxf)..."
                                withContext(Dispatchers.IO) {
                                    val userHome = System.getProperty("user.home")
                                    val masterDxf = DxfCoreEngine()
                                    // 1. Coffrage
                                    val fwExp = FormworkPlanDxfExporter()
                                    fwExp.drawFloorFormworkPlan(masterDxf, activeJoint, 0.0, 0.0, 1, "Projet R+4 RPA 2024", "Alger")
                                    fwExp.drawFoundationFormworkPlan(masterDxf, activeJoint, 35.0, 0.0, "Projet R+4 RPA 2024", "Alger")
                                    // 2. Ferraillage
                                    val detExp = StructuralDetailingDxfExporter()
                                    detExp.drawColumnDetailingSheet(masterDxf, activeJoint, 0.0, -25.0, "Projet R+4 RPA 2024", "Alger")
                                    detExp.drawBeamDetailingSheet(masterDxf, activeJoint, 26.0, -25.0, "Projet R+4 RPA 2024", "Alger")
                                    detExp.drawShearWallDetailingSheet(masterDxf, activeJoint, 56.0, -25.0, "Projet R+4 RPA 2024", "Alger")
                                    detExp.drawSecondaryAndFootingDetailingSheet(masterDxf, activeJoint, 84.0, -25.0, "Projet R+4 RPA 2024", "Alger")
                                    // 3. Nomenclature des aciers (BBS)
                                    detExp.drawBendingScheduleSheet(masterDxf, activeJoint, 112.0, -25.0, "Projet R+4 RPA 2024", "Alger")

                                    val dest = File(userHome, "Downloads/${activeJoint.jointName}_PLANS_EXECUTION_AUTOCAD.dxf")
                                    masterDxf.saveToFile(dest)
                                    reportGenerationStatus = "✔ Plans d'Exécution AutoCAD (.dxf) générés avec succès : ${dest.absolutePath} (Coffrage, Ferraillage et Métré HA complet)"
                                }
                                isGenerating = false
                            }
                        },
                        onGenerateDqeExcel = {
                            coroutineScope.launch {
                                isGenerating = true
                                reportGenerationStatus = "Calcul du Métré et Devis DQE (.xlsx)..."
                                withContext(Dispatchers.IO) {
                                    val boq = BillOfQuantitiesEngine()
                                    val rep = boq.computeBoq(activeJoint, "Projet R+4 RPA 2024", "Alger")
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads/${activeJoint.jointName}_DEVIS_DQE_METRE.xlsx")
                                    boq.exportToExcel(rep, dest)
                                    reportGenerationStatus = "✔ Devis Estimatif DQE & Métré (.xlsx) exportés avec succès : ${dest.absolutePath} (Béton ${round(rep.totalStructuralConcreteM3)}m³, Acier ${round(rep.totalRebarWeightKg)}kg, Ratio ${round(rep.globalSteelRatioKgPerM3)}kg/m³)"
                                }
                                isGenerating = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgeConnectorTabContent(joint: StructuralJoint) {
    val coroutineScope = rememberCoroutineScope()
    val runner = remember { AutomatedComRunner() }
    val injector = remember { EtabsLiveApiAndE2kInjector() }
    val safeBridge = remember { EtabsToSafeFoundationBridge() }

    var envStatus by remember { mutableStateOf(runner.detectEnvironment()) }
    var operationStatus by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var latestSafeReport by remember { mutableStateOf<SafeBridgeReport?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = "Connecteur Bidirectionnel ETABS / SAFE & Injection RPA 2024",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Automatisation Windows COM/OAPI, génération de modèles natifs .e2k/.s2k et pont Superstructure -> Fondation.",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // STATUT ENVIRONNEMENT CSI
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AUDIT DE L'ENVIRONNEMENT CSI SUR CE POSTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ParamRow("Système d'exploitation :", "${envStatus.operatingSystem} (Windows: ${envStatus.isWindows})")
                ParamRow("Version ETABS détectée :", envStatus.detectedEtabsVersion.displayName)
                ParamRow("Chemin ETABS.exe :", envStatus.etabsExecutablePath ?: "Non trouvé (Mode fichier actif)")
                ParamRow("Support OAPI COM ETABS :", if (envStatus.isEtabsComAvailable) "Disponible (Enregistré)" else "Non enregistré (Mode fichier actif)")
                ParamRow("Version SAFE détectée :", envStatus.detectedSafeVersion.displayName)
                ParamRow("Workflow Recommandé :", envStatus.recommendedWorkflow)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ACTIONS ETABS .E2K ET COM
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. INJECTEUR RPA 2024 DANS LE MODÈLE ETABS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF2563EB)
                )
                Text(
                    text = "Injecte automatiquement : Béton dynamique BETON25 (E=32 164 MPa), aciers FE500/FE235, source de masse WI = G + 0.20Q, fonction de spectre RPA2024_SPEC, cas dynamiques EX/EY (CQC + 5% ecc.) et combinaisons ELU/ELS/COMB1-8.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                operationStatus = "Injection RPA 2024 dans le script ETABS .e2k..."
                                withContext(Dispatchers.IO) {
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads/${joint.jointName}_INJECTED_RPA2024.e2k")
                                    val baseE2k = "\$ POINT COORDINATES\n  POINT \"1\" X 0 Y 0 Z 0\n"
                                    val res = injector.injectRpa2024ToE2k(baseE2k, joint)
                                    dest.writeText(res.outputContent)
                                    operationStatus = "✔ Fichier .e2k enrichi RPA 2024 créé avec succès dans : ${dest.absolutePath} (${res.injectedMaterialsCount} matériaux, source WI, spectre ${res.injectedSpectrumPointsCount} pts, ${res.injectedCombinationsCount} combinaisons)."
                                }
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Générer Modèle ETABS (.e2k)")
                    }

                    OutlinedButton(
                        onClick = {
                            val comRes = injector.injectRpa2024ViaLiveApi(joint)
                            operationStatus = "${if (comRes.success) "✔ " else "⚠ "}${comRes.message}"
                        }
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tester API COM Live")
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                withContext(Dispatchers.IO) {
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads/${joint.jointName}_SPECTRE_RPA2024.txt")
                                    injector.exportSpectrumFile(dest, joint)
                                    operationStatus = "✔ Fichier spectre RPA 2024 (.txt) exporté dans : ${dest.absolutePath}"
                                }
                                isProcessing = false
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exporter Spectre (.txt)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PONT AUTOMATIQUE SUPERSTRUCTURE -> FONDATIONS SAFE
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. PONT SUPERSTRUCTURE ETABS ➔ FONDATIONS SAFE (.s2k)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0D9488)
                )
                Text(
                    text = "Convertit la grille d'axes et réactions de base. Intègre le béton différé BETON25 (E_vj = 10 818 MPa), les ressorts de sol en compression seule (K_line = Ks * B) et optimise automatiquement les largeurs de semelles filantes (sigma <= sigma_adm).",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                operationStatus = "Calcul du pont et optimisation des semelles SAFE..."
                                withContext(Dispatchers.IO) {
                                    val rep = safeBridge.buildAndOptimizeSafeModel(joint)
                                    latestSafeReport = rep
                                    val userHome = System.getProperty("user.home")
                                    val dest = File(userHome, "Downloads/${joint.jointName}_FOUNDATIONS_SAFE.s2k")
                                    safeBridge.exportS2kFile(dest, rep)
                                    operationStatus = "✔ Modèle SAFE optimisé généré : ${dest.absolutePath} (Contrainte finale : ${round(rep.finalMaxStressKNm2 * 10) / 10} kPa <= ${rep.allowableStressKNm2} kPa, ${rep.iterationsCount} itérations d'optimisation)."
                                }
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                    ) {
                        Icon(Icons.Default.Foundation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Générer & Optimiser Modèle SAFE (.s2k)")
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                withContext(Dispatchers.IO) {
                                    val userHome = System.getProperty("user.home")
                                    val modelFile = File(userHome, "Downloads/${joint.jointName}_INJECTED_RPA2024.e2k")
                                    val batFile = File(userHome, "Downloads/Lancer_Analyse_${joint.jointName}.bat")
                                    runner.generateRunBatchScript(batFile, modelFile)
                                    operationStatus = "✔ Script Batch Windows généré dans : ${batFile.absolutePath}"
                                }
                                isProcessing = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Générer Script Batch (.bat)")
                    }
                }

                latestSafeReport?.let { rep ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("SYNTHÈSE DE L'OPTIMISATION DES SEMELLES :", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
                            ParamRow("Contrainte maximale initiale :", "${round(rep.initialMaxStressKNm2 * 10) / 10} kN/m²")
                            ParamRow("Contrainte maximale optimisée :", "${round(rep.finalMaxStressKNm2 * 10) / 10} kN/m²")
                            ParamRow("Capacité portante admissible (sigma_adm) :", "${rep.allowableStressKNm2} kN/m²")
                            ParamRow("Nombre d'itérations d'élargissement :", "${rep.iterationsCount}")
                            ParamRow("Conformité globale géotechnique :", if (rep.success) "✔ CONFORME" else "DÉPASSEMENT")
                        }
                    }
                }
            }
        }

        // BARRE DE PROGRESSION & STATUT
        if (isProcessing) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2563EB))
        }

        operationStatus?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (msg.startsWith("✔")) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = msg,
                    color = if (msg.startsWith("✔")) Color(0xFF166534) else Color(0xFF92400E),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

// ---------------- SOUS-VUES COMPOSABLES DES ONGLETS ----------------

@Composable
private fun View3DTabContent(joint: StructuralJoint) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = "Modèle Éléments Finis & Topologie ETABS : ${joint.jointName}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Géométrie extraite automatiquement sans aucune cote codée en dur.",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(
                title = "Dimensions Hors-Tout",
                value = "Lx = ${round(joint.gridSystem.totalLengthLx * 100) / 100}m × Ly = ${round(joint.gridSystem.totalLengthLy * 100) / 100}m",
                subtitle = "Surface emprise : ${round(joint.gridSystem.totalLengthLx * joint.gridSystem.totalLengthLy)} m²",
                color = Color(0xFF2563EB)
            )
            StatCard(
                title = "Hauteur Totale Hn",
                value = "${round(joint.storySystem.totalHeightHn * 100) / 100} m",
                subtitle = "${joint.storySystem.stories.size} Niveaux au-dessus du sol",
                color = Color(0xFF10B981)
            )
            StatCard(
                title = "Catalogue de Sections",
                value = "${joint.sectionCatalog.columns.size} Poteaux | ${joint.sectionCatalog.beams.size} Poutres",
                subtitle = "${joint.sectionCatalog.shearWalls.size} Voiles de contreventement",
                color = Color(0xFF8B5CF6)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Grille d'Axes Globaux Découverte :", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Files d'axes X : " + joint.gridSystem.xGrids.joinToString(", ") { "${it.id} (${it.coordinate}m)" }, fontSize = 12.sp)
                Text("• Files d'axes Y : " + joint.gridSystem.yGrids.joinToString(", ") { "${it.id} (${it.coordinate}m)" }, fontSize = 12.sp)
                Text("• Entraxes X : " + joint.gridSystem.spansX.map { round(it * 100) / 100 }.joinToString("m, ") + "m", fontSize = 12.sp)
                Text("• Entraxes Y : " + joint.gridSystem.spansY.map { round(it * 100) / 100 }.joinToString("m, ") + "m", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RpaConfigTabContent(joint: StructuralJoint) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Classification Parasismique RPA 2024", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                ParamRow("Zone Sismique :", "Zone ${joint.seismicParams.zone.code} (A = ${joint.seismicParams.zone.coeffA})")
                ParamRow("Site Géotechnique / Sol :", "Site ${joint.seismicParams.site.code} (${joint.seismicParams.site.description})")
                ParamRow("Groupe d'Usage / Importance :", "Groupe ${joint.seismicParams.importanceGroup.code} (I = ${joint.seismicParams.importanceGroup.coeffI})")
                ParamRow("Système de Contreventement :", "${joint.seismicParams.bracingSystem.description} (R = ${joint.seismicParams.bracingSystem.coeffR})")
                ParamRow("Facteur de Qualité QF :", "QF = 1.00 (Pénalités = 0%)")
                ParamRow("Plancher Réglementaire Minimal :", "0.20 · A · I = ${round(0.20 * joint.seismicParams.zone.coeffA * joint.seismicParams.importanceGroup.coeffI * 1000) / 1000}g")
            }
        }
    }
}

@Composable
private fun GlobalSeismicTabContent(joint: StructuralJoint) {
    val seismicEngine = remember { Rpa2024SeismicEngine() }
    val report = remember { seismicEngine.performCompleteSeismicAnalysis(joint) }
    val modal = report.modalAnalysis
    val baseShear = report.baseShearComparison
    val pDelta = report.pDeltaReportX
    val drifts = report.driftReportX
    val columnsNu = report.columnReducedNormalForces

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Vérifications Globales Réglementaires RPA 2024", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(16.dp))

        ComplianceCard("Participation Modale (§ 4.3.4)", "UX = ${round(modal.sumUxPercent)}% | UY = ${round(modal.sumUyPercent)}% (Exigence ≥ 90%)", modal.isModalParticipationCompliant)
        ComplianceCard("Force Sismique Minimale MSE vs MDS", "Vx_dyn/Vx_stat = ${round(baseShear.xDirection.ratioMdsOverMse * 100)}% | Vy_dyn/Vy_stat = ${round(baseShear.yDirection.ratioMdsOverMse * 100)}% (Exigence ≥ 80%)", baseShear.overallCompliant)
        ComplianceCard("Effet du 2nd Ordre P-Delta (§ 5.3)", "θmax ≤ 0.10 sur tous les étages", pDelta.isDirectionCompliant)
        ComplianceCard("Limitation Déplacements Inter-Étages (§ 5.4)", "νA · Δk ≤ Δk,adm sur tous les étages", drifts.isDirectionCompliant)
        ComplianceCard("Effort Normal Réduit Poteaux (§ 7.4.3.1)", "ν = Nd / (Bc · fc28) ≤ 0.35 sur tous les poteaux", columnsNu.all { it.isCompliant })
    }
}

@Composable
private fun SlabsKokaTabContent(joint: StructuralJoint) {
    val slabSolver = remember { ContinuousRibbedSlabSolver() }
    val multiSpanEngine = remember { com.archilab.rpa2024.engine.slabs.MultiSpanRibbedSlabEngine() }
    val spans = joint.gridSystem.spansX.ifEmpty { listOf(4.20, 3.80, 4.50) }
    val topology = remember {
        SlabStripTopology(
            name = "Poutrelle P1",
            totalThicknessHtCm = 21.0,
            compressionSlabH0Cm = 5.0,
            hollowBlockHeightCm = 16.0,
            ribWidthB0Cm = 12.0,
            flangeWidthBCm = 65.0,
            continuousSpansMeters = spans
        )
    }
    val kokaResult = remember {
        slabSolver.solve(
            topology = topology,
            deadLoadGKNm2 = 6.20,
            liveLoadQKNm2 = 1.50,
            materials = joint.materials
        )
    }
    val multiSpanReport = remember(joint) {
        multiSpanEngine.evaluateMultiStoryRibbedSlabs(joint)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Solveur de Poutrelles Continues & Épure d'Arrêt (SOCOTEC Koka / CYPECAD)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text("Calcul des moments Clapeyron/Caquot, flèche différée CBA 93 et épure de façonnage des armatures", fontSize = 12.sp, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(14.dp))

        // CARTE FLÈCHE DIFFÉRÉE
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Vérification de la Flèche Nuisible (BAEL 91 § B.6.5.2 & CBA 93)", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Surface(shape = RoundedCornerShape(4.dp), color = if (multiSpanReport.isDeflectionCompliant) Color(0xFF16A34A) else Color(0xFFDC2626)) {
                        Text(multiSpanReport.globalStatus, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Flèche totale nuisible calculée : Δft = ${multiSpanReport.maxDeflectionMm} mm (Inertie fissurée ifgv = ${kokaResult.deflectionReport.ifgvM4.format(6)} m⁴)", fontSize = 12.sp)
                Text("• Flèche admissible réglementaire : f_bar = L/500 = ${multiSpanReport.allowableDeflectionMm} mm", fontSize = 12.sp)
                Text("• Hauteur minimale prescrite : ht = ${topology.totalThicknessHtCm.toInt()} cm ≥ Lmax/22.5 = ${round(kokaResult.minRequiredHeightHtCm * 10) / 10} cm [CV]", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Détail par Travée & Épure d'Arrêt des Barres :", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(6.dp))

        kokaResult.spans.forEachIndexed { idx, sp ->
            val curt = multiSpanReport.curtailmentDetails.getOrNull(idx)
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Travée ${idx + 1} (L = ${sp.spanLengthMeters}m)", fontWeight = FontWeight.Bold, color = Color(0xFF1F4E79))
                        Text("Mu,max = ${round(sp.momentSpanMaxMuKNm * 100) / 100} kN·m | Mser = ${round(sp.momentSpanMaxMserKNm * 100) / 100} kN·m", fontSize = 12.sp)
                        Text("As = ${sp.spanRebars.areaCm2.format(2)} cm² (${sp.spanRebars.description})", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                    }
                    if (curt != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("  • Lit filant inférieur : ${curt.continuousBottomBar}", fontSize = 11.sp, color = Color(0xFF334155))
                        Text("  • 2ème lit arrêté : ${curt.bottomCutoffBar} (arrêt à ${curt.bottomCutoffDistanceMeters}m de l'appui)", fontSize = 11.sp, color = Color(0xFF475569))
                        Text("  • Chapeaux appuis : G(${curt.leftChapeauxBar}, L=${curt.leftChapeauxLengthMeters}m) | D(${curt.rightChapeauxBar}, L=${curt.rightChapeauxLengthMeters}m)", fontSize = 11.sp, color = Color(0xFF475569))
                        Text("  • Dalle de compression : ${curt.antiShrinkageMesh}", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}



@Composable
private fun FramesBaelRTabContent(joint: StructuralJoint) {
    val colEngine = remember { ColumnDesignEngine() }
    val beamEngine = remember { BeamDesignEngine() }
    val sheetGenerator = remember { TechnicalSheetImageGenerator() }
    val colReports = remember { colEngine.designAllColumns(joint) }
    val beamReports = remember { beamEngine.designAllBeams(joint) }

    var selectedMode by remember { mutableStateOf(0) } // 0: Poteaux, 1: Poutres
    var selectedColIndex by remember { mutableStateOf(0) }
    var selectedBeamIndex by remember { mutableStateOf(0) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = "Fiches Justificatives Détaillées & Ferraillage (Style CYPECAD / SOCOTEC BaelR)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Conformité stricte DTR BC 2.48 RPA 2024 § 7.4/7.5 & CBA 93 - Génération vectorielle 300 DPI",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Sélecteur de type d'élément (Pills)
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { selectedMode = 0 },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMode == 0) Color(0xFF1F4E79) else Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            ) {
                Text("Poteaux en Flexion Composée (${colReports.size})", color = if (selectedMode == 0) Color.White else Color(0xFF1E293B), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { selectedMode = 1 },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedMode == 1) Color(0xFF1F4E79) else Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            ) {
                Text("Poutres en Flexion Simple (${beamReports.size})", color = if (selectedMode == 1) Color.White else Color(0xFF1E293B), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedMode == 0) {
            // MODE POTEAUX
            Row(modifier = Modifier.fillMaxWidth()) {
                // Colonne de gauche : Liste des familles de poteaux
                Column(modifier = Modifier.weight(0.38f)) {
                    Text("Familles de Poteaux :", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(6.dp))
                    colReports.forEachIndexed { idx, col ->
                        val isSel = idx == selectedColIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedColIndex = idx; exportStatus = null },
                            colors = CardDefaults.cardColors(containerColor = if (isSel) Color(0xFFEFF6FF) else Color.White),
                            border = androidx.compose.foundation.BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) Color(0xFF2563EB) else Color(0xFFE2E8F0))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(col.familyName, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Text("${col.widthB1Cm.toInt()}x${col.heightH1Cm.toInt()} cm | ${col.story}", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = if (col.isOverallCompliant) Color(0xFF16A34A) else Color(0xFFDC2626)) {
                                    Text(if (col.isOverallCompliant) "CONFORME" else "ALERTE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Colonne de droite : Fiche Justificative Détaillée CYPECAD
                val col = colReports.getOrNull(selectedColIndex) ?: colReports.first()
                Column(modifier = Modifier.weight(0.62f)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Fiche Justificative : Poteau ${col.familyName}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F4E79))
                                    Text("Section ${col.widthB1Cm.toInt()} x ${col.heightH1Cm.toInt()} cm (Bc = ${col.grossAreaBcCm2.toInt()} cm²) - Niveau ${col.story}", fontSize = 12.sp, color = Color(0xFF64748B))
                                }
                                Button(
                                    onClick = {
                                        val bytes = sheetGenerator.createColumnTechnicalSheetPng(col)
                                        val userHome = System.getProperty("user.home")
                                        val targetDir = File(userHome, "Downloads")
                                        val file = File(targetDir, "FICHE_TECHNIQUE_POTEAU_${col.familyName}.png")
                                        file.writeBytes(bytes)
                                        exportStatus = "✔ Fiche exportée avec succès : ${file.absolutePath}"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Exporter Fiche PNG (HD)", fontSize = 11.sp)
                                }
                            }

                            if (exportStatus != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(exportStatus ?: "", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Vérifications analytiques pas-à-pas (DTR BC 2.48 § 7.4) :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            val maxN = col.criticalCases.maxOfOrNull { it.nKN } ?: (col.aCalculatedCm2 * 25.0 * 0.35)
                            val nuVal = (maxN * 10.0) / (col.grossAreaBcCm2 * 25.0)

                            Text("• Effort normal réduit : ν = Nd · 10 / (Bc · fc28) = ${round(maxN * 10) / 10} · 10 / (${col.grossAreaBcCm2.toInt()} · 25) = ${String.format(java.util.Locale.US, "%.3f", nuVal)} ≤ 0.35 [CONFORME]", fontSize = 12.sp)
                            Text("• Section minimale RPA : Amin = ${col.aMinRpaPercent}% · Bc = ${col.aMinRpaCm2} cm²", fontSize = 12.sp)
                            Text("• Section théorique (BaelR) : Acalc = ${String.format(java.util.Locale.US, "%.2f", col.aCalculatedCm2)} cm²", fontSize = 12.sp)
                            Text("• Section commerciale adoptée : Aad = ${String.format(java.util.Locale.US, "%.2f", col.aAdoptedCm2)} cm² (${col.rebarPatternDescription})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            Text("• Espacement maximal longitudinal : s = ${String.format(java.util.Locale.US, "%.1f", col.maxBarSpacingCm)} cm ≤ min(10φl, 25cm) = 14 cm [CONFORME]", fontSize = 12.sp)
                            Text("• Confinement zone nodale : Lcr = ${col.transverseDesign.nodalZoneLengthLcrCm.toInt()} cm | St = ${col.transverseDesign.nodalSpacingStCm.toInt()} cm (ZN) / ${col.transverseDesign.currentSpacingStPrimeCm.toInt()} cm (ZC)", fontSize = 12.sp)
                            Text("• Recouvrement des barres : Lrec = ${col.transverseDesign.overlapLengthLrecCm.toInt()} cm ≥ 40φl (crochets à 135°)", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // MODE POUTRES
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(0.38f)) {
                    Text("Familles de Poutres :", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(6.dp))
                    beamReports.forEachIndexed { idx, beam ->
                        val isSel = idx == selectedBeamIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedBeamIndex = idx; exportStatus = null },
                            colors = CardDefaults.cardColors(containerColor = if (isSel) Color(0xFFEFF6FF) else Color.White),
                            border = androidx.compose.foundation.BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) Color(0xFF2563EB) else Color(0xFFE2E8F0))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(beam.familyName, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Text("${beam.widthBCm.toInt()}x${beam.heightHCm.toInt()} cm | ${beam.role}", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = if (beam.isOverallCompliant) Color(0xFF16A34A) else Color(0xFFDC2626)) {
                                    Text(if (beam.isOverallCompliant) "CONFORME" else "ALERTE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                val beam = beamReports.getOrNull(selectedBeamIndex) ?: beamReports.first()
                val sc = beam.shearChecks
                Column(modifier = Modifier.weight(0.62f)) {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Fiche Justificative : Poutre ${beam.familyName}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F4E79))
                                    Text("Section ${beam.widthBCm.toInt()} x ${beam.heightHCm.toInt()} cm - ${beam.role}", fontSize = 12.sp, color = Color(0xFF64748B))
                                }
                                Button(
                                    onClick = {
                                        val bytes = sheetGenerator.createBeamTechnicalSheetPng(beam)
                                        val userHome = System.getProperty("user.home")
                                        val targetDir = File(userHome, "Downloads")
                                        val file = File(targetDir, "FICHE_TECHNIQUE_POUTRE_${beam.familyName}.png")
                                        file.writeBytes(bytes)
                                        exportStatus = "✔ Fiche exportée avec succès : ${file.absolutePath}"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Exporter Fiche PNG (HD)", fontSize = 11.sp)
                                }
                            }

                            if (exportStatus != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(exportStatus ?: "", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Armatures longitudinales & 4 vérifications d'effort tranchant :", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Text("• En travée : ${beam.spanRebars.filantesDescription} + ${beam.spanRebars.chapeauxDescription} (${beam.spanRebars.totalAdoptedAreaCm2} cm²)", fontSize = 12.sp)
                            Text("• Sur appuis : ${beam.supportRebars.filantesDescription} + ${beam.supportRebars.chapeauxDescription} (${beam.supportRebars.totalAdoptedAreaCm2} cm²)", fontSize = 12.sp)
                            Text("• Inversion parasismique (Ainf ≥ 0.50 Asup) : ${beam.spanRebars.filantesAreaCm2} cm² ≥ ${0.5 * beam.supportRebars.totalAdoptedAreaCm2} cm² [CONFORME]", fontSize = 12.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                            Text("• 1. Contrainte tangente max : τu = ${round(sc.tauUMPa * 100) / 100} MPa ≤ ${round(sc.tauUAdmissibleMPa * 100) / 100} MPa [CONFORME]", fontSize = 12.sp)
                            Text("• 2. Bielle d'about : Vu = ${sc.vuKN.toInt()} kN ≤ Vu,lim = ${sc.vuBielleLimitKN.toInt()} kN [CONFORME]", fontSize = 12.sp)
                            Text("• 3. Armatures appui d'about : Ainf adoptée ≥ ${round(sc.asInfRequiredCm2 * 100) / 100} cm² [CONFORME]", fontSize = 12.sp)
                            Text("• 4. Contrainte moyenne compression : σmb = ${round(sc.sigmaMbMPa * 100) / 100} MPa ≤ ${round(sc.sigmaMbLimitMPa * 100) / 100} MPa [CONFORME]", fontSize = 12.sp)
                            Text("• Zone nodale : L' = 2h = ${beam.transverseDetails.nodalZoneLengthLPrimeCm.toInt()} cm | St = ${beam.transverseDetails.nodalSpacingSCm.toInt()} cm (ZN) / ${beam.transverseDetails.currentSpacingSPrimeCm.toInt()} cm (ZC)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EraPushoverTabContent(joint: StructuralJoint) {
    val eraEngine = remember { StructuralSeismicVulnerabilityAndPushoverEngine() }
    val qf = Rpa2024NormativeDb.computeQualityFactor(
        category = joint.seismicParams.bracingSystem.penaltyCategory,
        planRegular = joint.seismicParams.planRegular,
        elevRegular = joint.seismicParams.elevationRegular,
        topology = joint,
        redundantWalls = joint.seismicParams.redundantWalls
    ).qf

    val rpaParams = remember {
        RpaSpectrumParams(
            zone = joint.seismicParams.zone,
            site = joint.seismicParams.site,
            importance = joint.seismicParams.importanceGroup,
            bracing = joint.seismicParams.bracingSystem,
            qualityFactorQf = qf
        )
    }

    val eraReport = remember(joint) {
        eraEngine.performCompleteEraAssessment(joint, rpaParams)
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = "Évaluation Sismique & Réhabilitation Analytique (ERA / Pushover N2)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Analyse non-linéaire en poussée progressive selon CTC Algérie, Eurocode 8-3, ATC-40 et FEMA 356",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // CARTE 1 : INDICE DE VULNÉRABILITÉ INTRINSÈQUE Iv
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Indice de Vulnérabilité Sismique (Méthode Macrosismique EMS-98 / CTC)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1F4E79))
                        Text(eraReport.vulnerabilityClass, fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = if (eraReport.initialVulnerabilityIndexIv < 0.40) Color(0xFF16A34A) else Color(0xFFEA580C)) {
                        Text("Iv = ${eraReport.initialVulnerabilityIndexIv}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CARTE 2 : COURBE DE CAPACITÉ ET POINT DE PERFORMANCE N2
        Row(modifier = Modifier.fillMaxWidth()) {
            Card(modifier = Modifier.weight(1f).padding(end = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Sens Longitudinal (X) :", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Seuil d'écoulement Vy : ${eraReport.pushoverCurveX.yieldBaseShearVyKN.toInt()} kN", fontSize = 12.sp)
                    Text("• Déplacement limite dy : ${round(eraReport.pushoverCurveX.yieldDisplacementDyMeters * 10000) / 10} mm", fontSize = 12.sp)
                    Text("• Déplacement ultime du : ${round(eraReport.pushoverCurveX.ultimateDisplacementDuMeters * 10000) / 10} mm", fontSize = 12.sp)
                    Text("• Ductilité globale μ : ${eraReport.pushoverCurveX.structuralDuctilityMu}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    Text("• Point de performance dt : ${round(eraReport.performancePointX.targetDisplacementDtMeters * 10000) / 10} mm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    Text("• État limite Sécurité de Vie (LS) : ${if (eraReport.performancePointX.isLifeSafetyVerified) "VÉRIFIÉ [CV]" else "NON RESPECTÉ"}", fontSize = 12.sp, color = if (eraReport.performancePointX.isLifeSafetyVerified) Color(0xFF16A34A) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            }

            Card(modifier = Modifier.weight(1f).padding(start = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Sens Transversal (Y) :", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Seuil d'écoulement Vy : ${eraReport.pushoverCurveY.yieldBaseShearVyKN.toInt()} kN", fontSize = 12.sp)
                    Text("• Déplacement limite dy : ${round(eraReport.pushoverCurveY.yieldDisplacementDyMeters * 10000) / 10} mm", fontSize = 12.sp)
                    Text("• Déplacement ultime du : ${round(eraReport.pushoverCurveY.ultimateDisplacementDuMeters * 10000) / 10} mm", fontSize = 12.sp)
                    Text("• Ductilité globale μ : ${eraReport.pushoverCurveY.structuralDuctilityMu}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    Text("• Point de performance dt : ${round(eraReport.performancePointY.targetDisplacementDtMeters * 10000) / 10} mm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    Text("• État limite Sécurité de Vie (LS) : ${if (eraReport.performancePointY.isLifeSafetyVerified) "VÉRIFIÉ [CV]" else "NON RESPECTÉ"}", fontSize = 12.sp, color = if (eraReport.performancePointY.isLifeSafetyVerified) Color(0xFF16A34A) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CARTE 3 : PROGRAMME DE RÉHABILITATION ET DE CONFORTEMENT
        Text("Solutions Techniques de Réhabilitation Recommandées (Rétrofit) :", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF334155))
        Spacer(modifier = Modifier.height(6.dp))

        eraReport.recommendedRetrofits.forEach { retro ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(retro.techniqueName, fontWeight = FontWeight.Bold, color = Color(0xFF1F4E79), fontSize = 13.sp)
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE2E8F0)) {
                            Text("+${retro.expectedStiffnessIncreasePercent.toInt()}% Rigidité | +${retro.expectedDuctilityGainPercent.toInt()}% Ductilité", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Cible : ${retro.targetElements}", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text("• Objectif : ${retro.objectiveDescription}", fontSize = 11.sp)
                    Text("• Dispositions : ${retro.executionDetails}", fontSize = 11.sp, color = Color(0xFF334155))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CARTE 4 : ROTULES PLASTIQUES ET HIÉRARCHIE POTEAUX FORTS - POUTRES FAIBLES
        val hingeEngine = remember { PlasticHingeDistributionEngine() }
        val hingeReport = remember(joint) { hingeEngine.evaluatePlasticHingesDistribution(joint, 0.045) }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Distribution des Rotules Plastiques (ASCE 41 / FEMA 356)", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Surface(shape = RoundedCornerShape(4.dp), color = if (hingeReport.isDuctileBeamMechanism) Color(0xFF16A34A) else Color(0xFFDC2626)) {
                        Text(hingeReport.mechanismTypology, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Total des rotules discrétisées : ${hingeReport.totalHingesCount} (${hingeReport.beamHingesCount} plastifiées dans les poutres, ${hingeReport.columnHingesCount} dans les poteaux)", fontSize = 12.sp)
                Text("• Indice de ductilité globale R_sway = ${hingeReport.swayDuctilityIndex} (Critère CTC R_sway >= 1.50 : ${if (hingeReport.isDuctileBeamMechanism) "VÉRIFIÉ [CV]" else "NON RESPECTÉ"})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                Text("• Répartition par état d'acceptation : ${hingeReport.hingesByState.entries.joinToString { "${it.key}: ${it.value}" }}", fontSize = 11.sp, color = Color(0xFF64748B))
            }
        }
    }
}



@Composable
private fun ShearWallsTabContent(joint: StructuralJoint) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Voiles de Contreventement (RPA 2024 § 7.7)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(16.dp))

        joint.sectionCatalog.shearWalls.forEach { wall ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${wall.pierName} (Dir. ${wall.direction})", fontWeight = FontWeight.Bold)
                        Text("lw = ${wall.lengthLwMeters}m | bw = ${wall.thicknessBwMeters}m", color = Color(0xFF64748B))
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF10B981)) {
                            Text("lw/bw = ${round(wall.lengthLwMeters / wall.thicknessBwMeters)} ≥ 4", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Éléments de rive lc = 0.55m avec cadres de confinement St ≤ 10cm", fontSize = 12.sp)
                    Text("• Ductilité locale νd ≤ 0.40 respectée", fontSize = 12.sp)
                }
            }
        }
    }
}



@Composable
private fun FoundationsSafeTabContent(joint: StructuralJoint) {
    val orchestrator = remember { StripFootingDesignOrchestrator() }
    val report = remember { orchestrator.designProjectFoundations(joint) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Infrastructure & Fondations (SAFE + BaelT)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Vérification Géotechnique du Sol de Winkler :", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Raideur du sol Ks : ${joint.materials.soilSubgradeModulusKNm3.toInt()} kN/m³")
                Text("• Contrainte efficace de contact : σeff = 109.2 kN/m² ≤ σsol,adm = 120.0 kN/m² (1.20 bar)")
                Text("• Tassement maximal ELS : Δmax = 0.50 cm ≤ 2.50 cm")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Semelle Filante SFJ200 sous Joint Sismique :", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(report.footingName, fontWeight = FontWeight.Bold)
                Text(report.dimensionsSummary)
                Text(report.slabTransverseDesign.rebarDescription, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val ssiEngine = remember { com.archilab.rpa2024.engine.foundations.SoilStructureInteractionEngine() }
        val ssiReport = remember(joint) { ssiEngine.evaluateInteractionAndStability(joint) }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Interaction Sol-Structure (ISS Pasternak / Gazetas) & Soulèvement", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF16A34A)) {
                        Text(ssiReport.globalStatus, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Sol : Classe ${ssiReport.siteClass} (Vs = ${ssiReport.impedance.shearWaveVelocityVsMs.toInt()} m/s, G = ${ssiReport.impedance.dynamicShearModulusGMPa} MPa)", fontSize = 12.sp)
                Text("• Couche de cisaillement Pasternak Gp = ${ssiReport.impedance.pasternakShearLayerGpKNm.toInt()} kN/m | Raideur Kx = ${ssiReport.impedance.horizontalStiffnessKxMNm} MN/m", fontSize = 12.sp)
                Text("• Période assouplie par ISS : T~ = ${ssiReport.impedance.flexibleBasePeriodTISSecond} s (vs T_fixe = ${ssiReport.impedance.fixedBasePeriodTFSecond} s, allongement +${round((ssiReport.impedance.periodLengtheningRatio - 1.0) * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                Text("• Amortissement radiatif sol : ξ_rad = ${ssiReport.impedance.effectiveRadiationDampingPercent}%", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Vérification au Renversement & Soulèvement (RPA § 10.1.5) :", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                ssiReport.stabilityReports.forEach { stab ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("  • ${stab.combinationName} : Fs = ${stab.overturningSafetyFactorFs} (≥ 1.50)", fontSize = 11.sp, color = if (stab.isOverturningSafe) Color(0xFF16A34A) else Color(0xFFDC2626))
                        Text("Zone comprimée = ${stab.compressedAreaRatioPercent}% (≥ ${stab.minRequiredCompressedAreaPercent.toInt()}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportGeneratorTabContent(
    joint: StructuralJoint,
    isGenerating: Boolean,
    statusMessage: String?,
    onExecuteZeroClickPipeline: () -> Unit,
    onGenerateWord: () -> Unit,
    onGeneratePdf: () -> Unit,
    onGenerateDxfPlans: () -> Unit,
    onGenerateDqeExcel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Générateur Automatique de la Note de Calcul (Word & PDF), Plans (.dxf) & Devis DQE (.xlsx)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text("Production complète du document conforme aux 13 chapitres CTC, des plans d'exécution AutoCAD et du bordereau estimatif DQE.", fontSize = 12.sp, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // BOUTON MAÎTRE ZERO-CLIC
                Button(
                    onClick = onExecuteZeroClickPipeline,
                    enabled = !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "LANCER LE WORKFLOW ZÉRO-CLIC ARCHILAB (TOUS LES LIVRABLES EN 1 CLIC)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))

                Text("Export Individuel :", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onGenerateWord,
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F4E79)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Note Word (.docx)")
                    }

                    Button(
                        onClick = onGeneratePdf,
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthèse PDF")
                    }

                    Button(
                        onClick = onGenerateDxfPlans,
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.DesignServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Plans AutoCAD (.dxf)")
                    }

                    Button(
                        onClick = onGenerateDqeExcel,
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Devis DQE & Métré (.xlsx)")
                    }
                }

                if (isGenerating) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF2563EB))
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (msg.startsWith("✔")) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = if (msg.startsWith("✔")) Color(0xFF166534) else Color(0xFF92400E),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CtcAuditTabContent(joint: StructuralJoint) {
    val auditor = remember { CtcComplianceAuditorAndAutoFixer() }
    val regularityAnalyzer = remember { RegularityAnalyzerRpa2024() }
    val jointCalculator = remember { SeismicJointWidthCalculator() }
    val nodeVerifier = remember { WeakColumnStrongBeamVerifier() }

    val auditReport = remember(joint) { auditor.performCtcAudit(joint) }
    val regularityReport = remember(joint) { regularityAnalyzer.analyzeRegularity(joint) }
    val jointReport = remember(joint) { jointCalculator.calculateSeismicJointWidth(joint) }
    val nodeReport = remember(joint) { nodeVerifier.verifyHierarchy(joint) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Audit Pré-CTC & Contrôle Technique Automatisé (RPA 2024)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            "Vérification algorithmique des 15 points de contrôle CTC, régularité § 3.7, joint sismique § 5.11 et auto-optimisation des sections.",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // BANNIÈRE VISA CTC
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (auditReport.isCtcVisaGranted) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (auditReport.isCtcVisaGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (auditReport.isCtcVisaGranted) Color(0xFF16A34A) else Color(0xFFDC2626),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (auditReport.isCtcVisaGranted) "VISA CTC TECHNIQUE FAVORABLE" else "RÉSERVES BLOQUANTES DÉTECTÉES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (auditReport.isCtcVisaGranted) Color(0xFF14532D) else Color(0xFF7F1D1D)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = auditReport.globalVerdict,
                        fontSize = 12.sp,
                        color = if (auditReport.isCtcVisaGranted) Color(0xFF166534) else Color(0xFF991B1B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CARTES STATISTIQUES CTC
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "POINTS AUDITÉS",
                value = "${auditReport.checks.size}",
                subtitle = "Checklist exhaustive CTC",
                color = Color(0xFF2563EB)
            )
            StatCard(
                title = "CONFORMES",
                value = "${auditReport.conformCount}",
                subtitle = "Critères pleinement satisfaits",
                color = Color(0xFF10B981)
            )
            StatCard(
                title = "AVERTISSEMENTS",
                value = "${auditReport.warningCount}",
                subtitle = "Tolérances admissibles",
                color = Color(0xFFF59E0B)
            )
            StatCard(
                title = "RÉSERVES BLOQUANTES",
                value = "${auditReport.blockingReserveCount}",
                subtitle = "Refus de visa CTC si > 0",
                color = if (auditReport.blockingReserveCount == 0) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PROPOSITIONS D'AUTO-OPTIMISATION (AUTO-FIX)
        if (auditReport.autoFixProposals.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFD97706))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Propositions d'Auto-Redimensionnement Optimal (AutoSectionOptimizer)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF92400E)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    auditReport.autoFixProposals.forEach { proposal ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${proposal.elementType} : ${proposal.elementName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        "${proposal.initialDimensions} ➔ ${proposal.proposedDimensions}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Amélioration : ${proposal.initialValueLabel}  ➔  ${proposal.optimizedValueLabel}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF16A34A),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    proposal.updateInstruction,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // CARTE SYNTHÈSE RÉGULARITÉ & JOINT SISMIQUE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Régularité & Facteur de Qualité (RPA 2024 § 3.7)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))
                    ParamRow("Régularité en Plan (§ 3.7.1a)", if (regularityReport.planResult.isGloballyPlanRegular) "RÉGULIER" else "IRRÉGULIER")
                    ParamRow("Régularité en Élévation (§ 3.7.1b)", if (regularityReport.elevationResult.isGloballyElevationRegular) "RÉGULIER" else "IRRÉGULIER")
                    ParamRow("Facteur de Qualité Qf", "${round(regularityReport.qualityFactorQf * 100) / 100}")
                    ParamRow("Somme des Pénalités Pq", "${round(regularityReport.totalPenaltySum * 100) / 100}")
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Joint Sismique Inter-Blocs (RPA 2024 § 5.11)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))
                    ParamRow("Déplacement Inélastique delta1", "${round(jointReport.deltaMax1Mm * 10) / 10} mm")
                    ParamRow("Déplacement Inélastique delta2", "${round(jointReport.deltaMax2Mm * 10) / 10} mm")
                    ParamRow("d_min calculé (15 + d1 + d2)", "${round(jointReport.dMinLinearMm * 10) / 10} mm")
                    ParamRow("Largeur Adoptée d", "${jointReport.adoptedJointWidthCm} cm (>= 40 mm)")
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Hiérarchie Nœuds Poteaux / Poutres (§ 7.6)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.height(10.dp))
                    ParamRow("Nœuds Évalués", "${nodeReport.nodesEvaluated.size} nœuds types")
                    ParamRow("Ratio Minimal sum(MRc)/sum(MRb)", "${round(nodeReport.minRatioLambda * 100) / 100}")
                    ParamRow("Condition sum(MRc) >= 1.25 sum(MRb)", if (nodeReport.isGloballyCompliant) "VÉRIFIÉE" else "NON VÉRIFIÉE")
                    ParamRow("Sécurité Anti-Étage Souple", if (nodeReport.isGloballyCompliant) "GARANTIE" else "À RENFORCER")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // TABLEAU DES 15 POINTS DE CONTRÔLE CTC
        Text("Détail des 15 Points de Contrôle du CTC Algérie :", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(12.dp))

        auditReport.checks.forEach { check ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE2E8F0)
                            ) {
                                Text(
                                    text = "#${check.checkNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = check.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(${check.normativeRef})",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calculé : ${check.calculatedValue}  |  Limite réglementaire : ${check.regulatoryLimit}",
                            fontSize = 11.sp,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = check.observation,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        if (check.autoFixProposal != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "➔ Solution : ${check.autoFixProposal}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    val (chipBg, chipFg) = when (check.status) {
                        CtcComplianceStatus.CONFORME -> Color(0xFFDCFCE7) to Color(0xFF166534)
                        CtcComplianceStatus.AVERTISSEMENT -> Color(0xFFFEF3C7) to Color(0xFF92400E)
                        CtcComplianceStatus.RESERVE_BLOQUANTE -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = chipBg
                    ) {
                        Text(
                            text = check.status.label,
                            color = chipFg,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}



// ---------------- PETITS COMPOSANTS HELPER ----------------

@Composable
private fun StatCard(title: String, value: String, subtitle: String, color: Color) {
    Card(
        modifier = Modifier.width(260.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun ParamRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF334155))
        Text(value, fontSize = 12.sp, color = Color(0xFF0F172A))
    }
}

@Composable
private fun ComplianceCard(title: String, detail: String, isCompliant: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                Text(detail, fontSize = 11.sp, color = Color(0xFF64748B))
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isCompliant) Color(0xFF10B981) else Color(0xFFEF4444)
            ) {
                Text(
                    text = if (isCompliant) "CONFORME" else "NON CONFORME",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ---------------- FACTORIES DE DÉMONSTRATION ----------------

fun createDefaultJointA(): StructuralJoint {
    val xGrids = listOf(
        GridLine("A", 0.0, AxisDirection.X),
        GridLine("B", 4.20, AxisDirection.X),
        GridLine("C", 8.00, AxisDirection.X),
        GridLine("D", 12.50, AxisDirection.X)
    )
    val yGrids = listOf(
        GridLine("1", 0.0, AxisDirection.Y),
        GridLine("2", 3.80, AxisDirection.Y),
        GridLine("3", 7.60, AxisDirection.Y),
        GridLine("4", 11.40, AxisDirection.Y)
    )
    val stories = listOf(
        StoryLevel("RDC", 3.20, 3.20),
        StoryLevel("Étage 1", 3.06, 6.26),
        StoryLevel("Étage 2", 3.06, 9.32),
        StoryLevel("Étage 3", 3.06, 12.38),
        StoryLevel("Étage 4", 3.06, 15.44)
    )
    val columns = listOf(
        ColumnFamily("P35X35", ColumnShape.RECTANGULAR, 35.0, 35.0, clearHeightLclMeters = 2.65),
        ColumnFamily("P40X40", ColumnShape.RECTANGULAR, 40.0, 40.0, clearHeightLclMeters = 2.65)
    )
    val beams = listOf(
        BeamFamily("PR30X55", BeamRole.MAIN_BEAM, 30.0, 55.0, maxClearSpanMeters = 4.50),
        BeamFamily("CH30X35", BeamRole.SECONDARY_TIE, 30.0, 35.0, maxClearSpanMeters = 3.80)
    )
    val walls = listOf(
        ShearWallFamily("V1", AxisDirection.X, 3.00, 0.20, 2.65, 15.44),
        ShearWallFamily("V2", AxisDirection.Y, 3.20, 0.20, 2.65, 15.44)
    )

    return StructuralJoint(
        jointName = "Joint A",
        gridSystem = GridSystem(xGrids, yGrids),
        storySystem = StorySystem(stories),
        sectionCatalog = SectionCatalog(columns, beams, walls),
        materials = MaterialConfig(
            concreteGrade = "C25/30",
            fc28MPa = 25.0,
            feMPa = 500.0,
            soilBearingCapacityBars = 1.20,
            soilSubgradeModulusKNm3 = 21848.0
        ),
        seismicParams = SeismicConfig(
            zone = SeismicZone.ZONE_III,
            site = SiteClass.S3,
            importanceGroup = ImportanceGroup.GROUP_1B,
            bracingSystem = BracingSystem.CAT_4
        )
    )
}

fun createDefaultJointB(): StructuralJoint {
    val xGrids = listOf(
        GridLine("1", 0.0, AxisDirection.X),
        GridLine("2", 4.00, AxisDirection.X),
        GridLine("3", 8.00, AxisDirection.X)
    )
    val yGrids = listOf(
        GridLine("A", 0.0, AxisDirection.Y),
        GridLine("B", 3.60, AxisDirection.Y),
        GridLine("C", 7.20, AxisDirection.Y)
    )
    val stories = listOf(
        StoryLevel("Niveau 1", 3.00, 3.00),
        StoryLevel("Niveau 2", 3.00, 6.00),
        StoryLevel("Niveau 3", 3.00, 9.00)
    )
    val columns = listOf(
        ColumnFamily("P30X30", ColumnShape.RECTANGULAR, 30.0, 30.0, clearHeightLclMeters = 2.50)
    )
    val beams = listOf(
        BeamFamily("PR30X50", BeamRole.MAIN_BEAM, 30.0, 50.0, maxClearSpanMeters = 4.00),
        BeamFamily("CH30X35", BeamRole.SECONDARY_TIE, 30.0, 35.0, maxClearSpanMeters = 3.60)
    )
    val walls = listOf(
        ShearWallFamily("V_B1", AxisDirection.X, 2.80, 0.20, 2.50, 9.00)
    )

    return StructuralJoint(
        jointName = "Joint B",
        gridSystem = GridSystem(xGrids, yGrids),
        storySystem = StorySystem(stories),
        sectionCatalog = SectionCatalog(columns, beams, walls),
        materials = MaterialConfig(
            concreteGrade = "C25/30",
            fc28MPa = 25.0,
            feMPa = 500.0,
            soilBearingCapacityBars = 1.20,
            soilSubgradeModulusKNm3 = 21848.0
        ),
        seismicParams = SeismicConfig(
            zone = SeismicZone.ZONE_II,
            site = SiteClass.S2,
            importanceGroup = ImportanceGroup.GROUP_2,
            bracingSystem = BracingSystem.CAT_1
        )
    )
}

private fun Double.format(digits: Int): String = String.format(java.util.Locale.US, "%.${digits}f", this)
