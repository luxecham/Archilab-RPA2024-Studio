package com.archilab.rpa2024.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archilab.rpa2024.engine.geometry.CheckStatus

val NAVY = Color(0xFF12304F)
val NAVY_DARK = Color(0xFF0A1E33)
val ACCENT = Color(0xFF1F6FB2)
val ACCENT_SOFT = Color(0xFFDCE8F4)
val CANVAS = Color(0xFFF3F5F8)
val CARD = Color(0xFFFFFFFF)
val BORDER = Color(0xFFDCE2EA)
val TEXT = Color(0xFF1F2937)
val MUTED = Color(0xFF5B6573)
val OK_GREEN = Color(0xFF15803D)
val OK_BG = Color(0xFFE8F5EC)
val WARN_AMBER = Color(0xFFB45309)
val WARN_BG = Color(0xFFFFF4E0)
val FAIL_RED = Color(0xFFB91C1C)
val FAIL_BG = Color(0xFFFDECEC)
val NA_GREY = Color(0xFF4B5563)
val NA_BG = Color(0xFFEFF1F4)

fun statusColor(status: CheckStatus): Color = when (status) {
    CheckStatus.OK -> OK_GREEN
    CheckStatus.WARNING -> WARN_AMBER
    CheckStatus.FAILED -> FAIL_RED
    CheckStatus.NOT_APPLICABLE -> NA_GREY
}

fun statusBackground(status: CheckStatus): Color = when (status) {
    CheckStatus.OK -> OK_BG
    CheckStatus.WARNING -> WARN_BG
    CheckStatus.FAILED -> FAIL_BG
    CheckStatus.NOT_APPLICABLE -> NA_BG
}

fun statusLabel(status: CheckStatus): String = when (status) {
    CheckStatus.OK -> "Vérifié"
    CheckStatus.WARNING -> "À vérifier"
    CheckStatus.FAILED -> "Non vérifié"
    CheckStatus.NOT_APPLICABLE -> "Non applicable"
}

fun jointStatusColor(status: JointStatus): Color = when (status) {
    JointStatus.VIDE -> MUTED
    JointStatus.EN_COURS -> ACCENT
    JointStatus.PRET -> OK_GREEN
    JointStatus.ERREUR -> FAIL_RED
}

fun hexColor(hex: String?): Color? = hex?.let {
    runCatching { Color(("FF" + it.trim().removePrefix("#")).toLong(16)) }.getOrNull()
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScopeLike.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CARD,
        border = BorderStroke(1.dp, BORDER)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NAVY)
                    subtitle?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, fontSize = 12.sp, color = MUTED)
                    }
                }
                actions()
            }
            Spacer(Modifier.height(14.dp))
            ColumnScopeLike.content()
        }
    }
}

/** Petit receveur de contenu pour conserver une syntaxe de carte uniforme. */
object ColumnScopeLike

@Composable
fun StatusPill(label: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            label,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CheckStatusPill(status: CheckStatus, modifier: Modifier = Modifier) {
    StatusPill(statusLabel(status), statusColor(status), statusBackground(status), modifier)
}

@Composable
fun JointStatusPill(joint: JointWorkspace, modifier: Modifier = Modifier) {
    StatusPill(
        UiText.statusLabel(joint.status),
        jointStatusColor(joint.status),
        when (joint.status) {
            JointStatus.PRET -> OK_BG
            JointStatus.ERREUR -> FAIL_BG
            JointStatus.EN_COURS -> Color(0xFFE7F0FA)
            JointStatus.VIDE -> NA_BG
        },
        modifier
    )
}

@Composable
fun MetricTile(label: String, value: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFFF7F9FC), RoundedCornerShape(10.dp))
            .border(1.dp, BORDER, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 10.5.sp, color = MUTED, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = NAVY)
        hint?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 10.sp, color = MUTED, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FileRow(
    label: String,
    path: java.nio.file.Path?,
    placeholder: String,
    onBrowse: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(ACCENT_SOFT, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("▤", fontSize = 15.sp, color = ACCENT, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = MUTED)
            Text(
                path?.fileName?.toString() ?: placeholder,
                fontSize = 13.sp,
                color = if (path == null) MUTED else TEXT,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (path != null && onClear != null) {
            TextButton(onClick = onClear) { Text("Retirer", fontSize = 11.sp, color = MUTED) }
        }
        OutlinedButton(onClick = onBrowse, shape = RoundedCornerShape(8.dp)) {
            Text("Parcourir…", fontSize = 11.5.sp)
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    numeric: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.5.sp) },
        placeholder = placeholder?.let { { Text(it, fontSize = 12.sp) } },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TEXT),
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        modifier = modifier
    )
}

@Composable
fun DropdownField(
    label: String,
    selectedCode: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = MUTED)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    options.firstOrNull { it.first == selectedCode }?.second ?: selectedCode,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start
                )
                Text("▾", fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 340.dp)
            ) {
                options.forEach { (code, text) ->
                    DropdownMenuItem(
                        text = { Text(text, fontSize = 12.sp) },
                        onClick = {
                            onSelect(code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToggleField(label: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.5.sp, color = TEXT)
            description?.let { Text(it, fontSize = 10.5.sp, color = MUTED) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun InfoBanner(text: String, color: Color = ACCENT, background: Color = Color(0xFFEDF4FB)) {
    Surface(color = background, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 12.sp, color = color, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun ErrorBanner(text: String) {
    Surface(color = FAIL_BG, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 12.sp, color = FAIL_RED, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun WarningBanner(warnings: List<String>) {
    if (warnings.isEmpty()) return
    Surface(color = WARN_BG, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Avertissements d'import (${warnings.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WARN_AMBER
            )
            Spacer(Modifier.height(4.dp))
            warnings.take(6).forEach { warning ->
                Text("• $warning", fontSize = 11.sp, color = Color(0xFF7C4A03))
            }
            if (warnings.size > 6) {
                Text("… et ${warnings.size - 6} autre(s).", fontSize = 11.sp, color = Color(0xFF7C4A03))
            }
        }
    }
}

@Composable
fun BusyRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ACCENT)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = ACCENT)
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NAVY, contentColor = Color.White)
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp)
    ) {
        Text(text, fontSize = 12.5.sp, color = NAVY)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    HorizontalDivider(color = BORDER, thickness = 1.dp, modifier = modifier)
}

/** Bandeau d'en-tete d'un chapitre repris du document de note de calcul. */
@Composable
fun ChapterBanner(number: Int?, title: String, summary: String, status: CheckStatus, numbered: Boolean = true) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF7F9FC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BORDER)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (numbered && number != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(NAVY, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$number", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = NAVY)
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 11.5.sp, color = MUTED)
            }
            Spacer(Modifier.width(10.dp))
            CheckStatusPill(status)
        }
    }
}

@Composable
fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFE7EFF8) else Color.Transparent, RoundedCornerShape(9.dp))
            .border(
                1.dp,
                if (selected) Color(0xFFB9D0E6) else Color.Transparent,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun ScrollableTabStrip(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        tabs.chunked(4).forEachIndexed { rowIndex, rowTabs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowTabs.forEachIndexed { columnIndex, label ->
                    val index = rowIndex * 4 + columnIndex
                    val active = index == selected
                    Surface(
                        color = if (active) NAVY else Color(0xFFF7F9FC),
                        border = if (active) null else BorderStroke(1.dp, BORDER),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(index) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 10.sp,
                                color = if (active) Color(0xFFAAC8E8) else MUTED,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label,
                                fontSize = 11.5.sp,
                                color = if (active) Color.White else TEXT,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(4 - rowTabs.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = MUTED)
    }
}

@Composable
fun MonoEquation(text: String) {
    Surface(
        color = Color(0xFFF7F9FC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = NAVY,
            textAlign = TextAlign.Center
        )
    }
}
