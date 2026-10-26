package com.archilab.rpa2024.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archilab.rpa2024.model.SoilSiteClass
import com.archilab.rpa2024.normative.ImportanceGroup
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.report.CtcNoteDeCalculWordGenerator
import com.archilab.rpa2024.report.CtcReportDataPayload
import com.archilab.rpa2024.state.InteractiveProjectState
import com.archilab.rpa2024.state.ProjectStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import kotlin.math.round

enum class GraphicTab(val title: String) {
    SPECTRUM("1. Spectre RPA 2024 (§ 3.3)"),
    COLUMN_SECTION("2. Poteau Coupe (BAELR)"),
    COLUMN_ELEVATION("3. Poteau Élévation Lcr"),
    BEAM_SECTION("4. Poutre Coupe"),
    WALL_MOMENT("5. Voile Mu Décalé hcr"),
    WALL_SHEAR("6. Voile 1.40 Vu"),
    SAFE_SOIL("7. Fondations SAFE Heatmap")
}

@Composable
fun InteractiveStructuralStudioWindow(
    stateManager: ProjectStateManager = remember { ProjectStateManager() },
    onGenerateReportRequested: ((InteractiveProjectState) -> Unit)? = null
) {
    val projectState by stateManager.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(GraphicTab.SPECTRUM) }
    var generationStatusMessage by remember { mutableStateOf<String?>(null) }
    var isGeneratingDocx by remember { mutableStateOf(false) }

    // Rendu en cache des images lors des changements d'état
    val currentImageBitmap = remember(selectedTab, projectState) {
        try {
            val bytes = when (selectedTab) {
                GraphicTab.SPECTRUM -> stateManager.spectrumRenderer.renderToPng(
                    zone = projectState.seismicZone,
                    site = projectState.siteClass,
                    importance = projectState.importanceGroup,
                    r = projectState.behaviorFactorR,
                    q = projectState.qualityFactorQ,
                    t0Operating = projectState.fundamentalPeriodT0
                )
                GraphicTab.COLUMN_SECTION -> stateManager.rebarRenderer.renderColumnSectionPng(
                    widthCm = projectState.columnWidthBCm,
                    heightCm = projectState.columnHeightHCm
                )
                GraphicTab.COLUMN_ELEVATION -> stateManager.rebarRenderer.renderColumnElevationPng(
                    heightTotalM = projectState.columnStoryHeightM,
                    sectionDimCm = projectState.columnWidthBCm,
                    lcrM = projectState.columnOutput.lcrM,
                    spacingLcrCm = projectState.columnOutput.tieSpacingLcrCm,
                    spacingCouranteCm = projectState.columnOutput.tieSpacingCouranteCm
                )
                GraphicTab.BEAM_SECTION -> stateManager.rebarRenderer.renderBeamSectionPng(
                    widthBCm = projectState.beamWidthBCm,
                    heightHCm = projectState.beamHeightHCm
                )
                GraphicTab.WALL_MOMENT -> stateManager.wallRenderer.renderMomentEnvelopePng(
                    wallLengthM = projectState.wallLengthLwM,
                    wallThicknessM = projectState.wallThicknessEM,
                    totalHeightM = projectState.wallTotalHeightHM,
                    hcrM = projectState.wallOutput.hcrM
                )
                GraphicTab.WALL_SHEAR -> stateManager.wallRenderer.renderShearEnvelopePng(
                    wallLengthM = projectState.wallLengthLwM,
                    wallThicknessM = projectState.wallThicknessEM
                )
                GraphicTab.SAFE_SOIL -> stateManager.safeRenderer.renderSoilPressureHeatmapPng(
                    ksKNm3 = projectState.soilKsKNm3,
                    sigmaSolAdmissibleBars = projectState.soilSigmaSolAdmBars,
                    peakStressBars = projectState.foundationOutput.calculatedMaxPressureBars
                )
            }
            loadImageBitmap(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B132B))) {
        // BARRE D'EN-TÊTE SUPÉRIEURE
        StudioTopAppBar(projectState, onResetDefaults = { stateManager.resetToDefaults() })

        Row(modifier = Modifier.fillMaxWidth().weight(1.0f)) {
            // 1. PANNEAU GAUCHE : CONTRÔLES RÉACTIFS & SLIDERS (380 DP)
            LeftControlsPanel(
                state = projectState,
                manager = stateManager,
                modifier = Modifier.width(380.dp).fillMaxHeight()
            )

            // 2. PANNEAU CENTRAL : ONGLETS & CANVAS GRAPHIQUE HAUTE DÉFINITION
            CentralGraphicCanvasPanel(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                imageBitmap = currentImageBitmap,
                modifier = Modifier.weight(1.0f).fillMaxHeight()
            )
        }

        // 3. PANNEAU INFÉRIEUR : DASHBOARD CTC & BOUTON D'EXPORT WORD OFFICIEL
        LowerCtcDashboard(
            state = projectState,
            isGenerating = isGeneratingDocx,
            statusMessage = generationStatusMessage,
            onGenerateDocx = {
                coroutineScope.launch {
                    isGeneratingDocx = true
                    generationStatusMessage = "Génération du rapport Word (.docx) avec graphiques haute résolution..."
                    try {
                        withContext(Dispatchers.IO) {
                            val exportDir = File("./exports").apply { mkdirs() }
                            val outFile = File(exportDir, "NOTE_DE_CALCUL_OFFICIELLE_CTC_JOINT_A.docx")
                            val generator = CtcNoteDeCalculWordGenerator()
                            val payload = CtcReportDataPayload.fromInteractiveState(projectState)
                            generator.generateOfficialCtcWordReport(outFile, payload)
                        }
                        generationStatusMessage = "✓ Rapport officiel généré avec succès dans ./exports/"
                    } catch (e: Exception) {
                        generationStatusMessage = "Erreur lors de la génération : ${e.message}"
                    } finally {
                        isGeneratingDocx = false
                    }
                }
            }
        )
    }
}

@Composable
private fun StudioTopAppBar(state: InteractiveProjectState, onResetDefaults: () -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp).background(Color(0xFF2563EB), RoundedCornerShape(6.dp))
            ) {
                Text("AL", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ARCHILAB STRUCTURAL STUDIO — MOTEUR RÉACTIF RPA 2024",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Projet : ${state.projectName} • Wilaya : ${state.wilaya}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.weight(1.0f))

            // Badge temps de calcul
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF22C55E), RoundedCornerShape(4.dp)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Calcul réactif : ${state.computationTimeMs} ms",
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedButton(
                onClick = onResetDefaults,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Défauts Joint A", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LeftControlsPanel(
    state: InteractiveProjectState,
    manager: ProjectStateManager,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(Color(0xFF111827))
            .border(width = 1.dp, color = Color(0xFF1F2937))
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        Text("CONTRÔLES PARAMÉTRIQUES", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(10.dp))

        // SECTION 1 : RPA 2024 SISMIQUE
        ParameterGroupCard(title = "1. Paramètres Sismiques RPA 2024") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Zone : ${state.seismicZone.name}", color = Color.White, fontSize = 12.sp)
                Text("A = ${state.seismicZone.coeffA}", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Site : ${state.siteClass.name}", color = Color.White, fontSize = 12.sp)
                Text("S = ${state.siteClass.s}", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            SliderParamRow(
                label = "Période fondamentale T0",
                value = state.fundamentalPeriodT0,
                unit = "s",
                valueRange = 0.10f..1.50f,
                onValueChange = { manager.updateSeismicParams(t0 = round(it.toDouble() * 1000.0) / 1000.0) }
            )
            SliderParamRow(
                label = "Comportement R",
                value = state.behaviorFactorR,
                unit = "",
                valueRange = 2.0f..5.0f,
                onValueChange = { manager.updateSeismicParams(r = round(it.toDouble() * 10.0) / 10.0) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION 2 : POTEAU BAELR / RPA
        ParameterGroupCard(title = "2. Poteau Rectangulaire (BaelR)") {
            SliderParamRow(
                label = "Largeur b",
                value = state.columnWidthBCm,
                unit = "cm",
                valueRange = 30f..80f,
                onValueChange = { manager.updateColumnParams(widthCm = round(it.toDouble())) }
            )
            SliderParamRow(
                label = "Hauteur h",
                value = state.columnHeightHCm,
                unit = "cm",
                valueRange = 30f..80f,
                onValueChange = { manager.updateColumnParams(heightCm = round(it.toDouble())) }
            )
            SliderParamRow(
                label = "Effort axial Ned",
                value = state.columnNedKN,
                unit = "kN",
                valueRange = 400f..2500f,
                onValueChange = { manager.updateColumnParams(nedKN = round(it.toDouble())) }
            )
            SliderParamRow(
                label = "Moment fléchissant Med",
                value = state.columnMedKNm,
                unit = "kN·m",
                valueRange = 20f..300f,
                onValueChange = { manager.updateColumnParams(medKNm = round(it.toDouble())) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION 3 : POUTRES & VOILES
        ParameterGroupCard(title = "3. Poutres & Voiles RPA (§ 7.7)") {
            SliderParamRow(
                label = "Voile Longueur Lw",
                value = state.wallLengthLwM,
                unit = "m",
                valueRange = 2.0f..6.0f,
                onValueChange = { manager.updateWallParams(lengthM = round(it.toDouble() * 10.0) / 10.0) }
            )
            SliderParamRow(
                label = "Voile Épaisseur e",
                value = state.wallThicknessEM * 100.0,
                unit = "cm",
                valueRange = 15f..35f,
                onValueChange = { manager.updateWallParams(thicknessM = round(it.toDouble()) / 100.0) }
            )
            SliderParamRow(
                label = "Effort Tranchant Vu",
                value = state.wallVedKN,
                unit = "kN",
                valueRange = 100f..800f,
                onValueChange = { manager.updateWallParams(vedKN = round(it.toDouble())) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION 4 : FONDATIONS SAFE
        ParameterGroupCard(title = "4. Interaction Sol SAFE (DTR BC 2.33.1)") {
            SliderParamRow(
                label = "Module réaction Ks",
                value = state.soilKsKNm3,
                unit = "kN/m³",
                valueRange = 15000f..60000f,
                onValueChange = { manager.updateFoundationParams(ksKNm3 = round(it.toDouble())) }
            )
            SliderParamRow(
                label = "Contrainte adm. σsol",
                value = state.soilSigmaSolAdmBars,
                unit = "bars",
                valueRange = 1.0f..3.5f,
                onValueChange = { manager.updateFoundationParams(sigmaSolAdmBars = round(it.toDouble() * 100.0) / 100.0) }
            )
        }
    }
}

@Composable
private fun ParameterGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun SliderParamRow(
    label: String,
    value: Double,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFCBD5E1), fontSize = 11.sp)
            Text(String.format(Locale.US, "%.2f %s", value, unit), color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF2563EB),
                inactiveTrackColor = Color(0xFF475569)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun CentralGraphicCanvasPanel(
    selectedTab: GraphicTab,
    onTabSelected: (GraphicTab) -> Unit,
    imageBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color(0xFF0F172A)).padding(12.dp)) {
        // BARRE D'ONGLETS GRAPHIQUES
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            edgePadding = 8.dp,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            GraphicTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == tab) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ZONE D'AFFICHAGE DE L'IMAGE VECTORIELLE
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = selectedTab.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Color(0xFF2563EB))
                }
            }
        }
    }
}

@Composable
private fun LowerCtcDashboard(
    state: InteractiveProjectState,
    isGenerating: Boolean,
    statusMessage: String?,
    onGenerateDocx: () -> Unit
) {
    Surface(
        color = Color(0xFF0B132B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth().height(85.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            // Badges de conformité CTC en temps réel
            ComplianceBadge(label = "Poteau \u03bd \u2264 0.30", isOk = state.columnOutput.isNuValid, value = String.format(Locale.US, "%.3f", state.columnOutput.reducedAxialStressNu))
            Spacer(modifier = Modifier.width(10.dp))
            ComplianceBadge(label = "Poteau \u03c1 \u2265 0.8%", isOk = state.columnOutput.isAsConforme, value = String.format(Locale.US, "%.2f%%", state.columnOutput.rebarRatioPercent))
            Spacer(modifier = Modifier.width(10.dp))
            ComplianceBadge(label = "Poutre BaelR", isOk = state.beamOutput.isBeamConforme, value = state.beamOutput.selectedRebarTravee.substringBefore("("))
            Spacer(modifier = Modifier.width(10.dp))
            ComplianceBadge(label = "Voile \u03c4b \u2264 5 MPa", isOk = state.wallOutput.isShearConforme, value = String.format(Locale.US, "%.2f MPa", state.wallOutput.tauBMPa))
            Spacer(modifier = Modifier.width(10.dp))
            ComplianceBadge(label = "Sol \u03c3 \u2264 2.0 bar", isOk = state.foundationOutput.isBearingConforme, value = String.format(Locale.US, "%.2f bar", state.foundationOutput.calculatedMaxPressureBars))

            Spacer(modifier = Modifier.weight(1.0f))

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = if (statusMessage.startsWith("✓")) Color(0xFF22C55E) else Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            // Bouton Génération Word avec graphiques vectoriels
            Button(
                onClick = onGenerateDocx,
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Génération en cours...", fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Description, contentDescription = "Word", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Générer Note de Calcul Word (.docx)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ComplianceBadge(label: String, isOk: Boolean, value: String) {
    Surface(
        color = if (isOk) Color(0xFF064E3B) else Color(0xFF7F1D1D),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOk) Color(0xFF059669) else Color(0xFFDC2626))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, color = if (isOk) Color(0xFFA7F3D0) else Color(0xFFFECACA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(if (isOk) "✓ $value" else "✗ $value", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
