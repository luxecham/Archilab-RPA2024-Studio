package com.archilab.rpa2024.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Libelles des huit onglets de navigation, dans l'ordre des chapitres du dossier CTC. */
val APP_TABS = listOf(
    "Import & Topologie 3D",
    "Matériaux & RPA 2024",
    "Vérifications sismiques",
    "Planchers & Poutrelles",
    "Poteaux & Poutres",
    "Voiles",
    "Acrotère / Escaliers / Fondations",
    "Génération Note de Calcul CTC"
)

@Composable
fun MainAppWindow(project: ProjectState, controller: ProjectController) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = NAVY,
            onPrimary = Color.White,
            secondary = ACCENT,
            surface = CARD,
            background = CANVAS,
            onSurface = TEXT
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().background(CANVAS)) {
            TopBar(project, controller)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Sidebar(project, controller)
                Column(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White)) {
                    ScrollableTabStrip(APP_TABS, project.selectedTab) { project.selectedTab = it }
                    HorizontalDivider(color = BORDER, thickness = 1.dp)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val joint = project.selected
                        if (joint == null) {
                            Text(
                                "Aucun joint sélectionné.",
                                fontSize = 13.sp,
                                color = MUTED,
                                modifier = Modifier.padding(24.dp)
                            )
                        } else {
                            when (project.selectedTab.coerceIn(0, APP_TABS.lastIndex)) {
                                0 -> ImportTopologyScreen(project, joint, controller)
                                1 -> MaterialsRpaScreen(project, joint, controller)
                                2 -> SeismicScreen(project, joint, controller)
                                3 -> FloorsScreen(project, joint, controller)
                                4 -> FramesScreen(project, joint, controller)
                                5 -> WallsScreen(project, joint, controller)
                                6 -> SecondaryScreen(project, joint, controller)
                                else -> ReportExportScreen(project, joint, controller)
                            }
                        }
                    }
                }
            }
            StatusBar(project)
        }
    }
}

@Composable
private fun TopBar(project: ProjectState, controller: ProjectController) {
    val joint = project.selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(NAVY_DARK)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(ACCENT, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("RPA", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "ARCHILAB · Note de calcul RPA 2024",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "DTR BC 2.48 · BAEL 91 révisé 99 · CBA 93 · Control Technique de la Construction",
                color = Color(0xFF9FB6CC),
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.weight(1f))
        joint?.let {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
                Text(
                    it.displayTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${it.name} · ${it.wilaya}",
                    color = Color(0xFF9FB6CC),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            JointStatusPill(it)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { controller.analyze(project, it) },
                enabled = it.status != JointStatus.EN_COURS,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ACCENT,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF3C5773),
                    disabledContentColor = Color(0xFFB9C6D3)
                )
            ) {
                Text("Analyser le joint", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Sidebar(project: ProjectState, controller: ProjectController) {
    Column(
        modifier = Modifier
            .width(246.dp)
            .fillMaxHeight()
            .background(Color.White)
            .border(width = 1.dp, color = BORDER)
            .padding(14.dp)
    ) {
        Text(
            "JOINTS DU PROJET",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MUTED
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            project.joints.forEachIndexed { index, joint ->
                SelectableRow(
                    selected = index == project.selectedIndex,
                    onClick = {
                        project.selectedIndex = index
                        project.selectedTab = 0
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(jointStatusColor(joint.status), RoundedCornerShape(999.dp))
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            joint.name,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = TEXT,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            UiText.statusLabel(joint.status) +
                                if (joint.imported != null) " · ${joint.artifacts?.document?.chapters?.size ?: 0} ch." else "",
                            fontSize = 9.5.sp,
                            color = MUTED,
                            maxLines = 1
                        )
                    }
                    if (project.joints.size > 1) {
                        TextButton(
                            onClick = { project.removeJoint(joint) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("✕", fontSize = 11.sp, color = MUTED)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth()) {
            SecondaryButton("Nouveau joint") {
                val joint = project.addJoint()
                joint.sample = true
                controller.analyze(project, joint)
            }
        }
        Spacer(Modifier.height(12.dp))
        DividerLine()
        Spacer(Modifier.height(10.dp))
        Text("PROJET", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MUTED)
        Spacer(Modifier.height(4.dp))
        Text(
            project.selected?.displayTitle ?: "—",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = NAVY,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            project.selected?.wilaya ?: "",
            fontSize = 10.5.sp,
            color = MUTED
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Glissez-déposez un fichier ETABS (.e2k), des résultats ou un export SAFE directement dans la fenêtre.",
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            color = MUTED
        )
    }
}

@Composable
private fun StatusBar(project: ProjectState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Color(0xFFF7F9FC))
            .border(width = 1.dp, color = BORDER)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (project.selected?.status == JointStatus.ERREUR) FAIL_RED else OK_GREEN, RoundedCornerShape(999.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            project.globalMessage ?: "Prêt — sélectionnez un joint, importez les fichiers puis lancez l'analyse.",
            fontSize = 10.5.sp,
            color = TEXT,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "RPA 2024 · ${UiText.today()}",
            fontSize = 10.sp,
            color = MUTED
        )
    }
}
