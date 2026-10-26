package com.archilab.civilwork

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

private val Bg = Color(0xFF080D16)
private val Panel = Color(0xFF0F1726)
private val PanelSoft = Color(0xFF131D30)
private val Line = Color(0xFF1E2C44)
private val TextMain = Color(0xFFE8EEF8)
private val Muted = Color(0xFF93A4BD)
private val Accent = Color(0xFFF0A33C)
private val Teal = Color(0xFF46D3C0)
private val Danger = Color(0xFFF2675F)

private data class Zone(val code: String, val a: Double, val type: Int)
private data class Site(val s: Double, val t1: Double, val t2: Double, val t3: Double)

private val zones = listOf(
    Zone("0", 0.00, 2),
    Zone("I", 0.07, 2),
    Zone("II", 0.10, 2),
    Zone("III", 0.15, 2),
    Zone("IV", 0.20, 1),
    Zone("V", 0.25, 1),
    Zone("VI", 0.30, 1)
)
private val type1 = mapOf(
    "S1" to Site(1.00, 0.10, 0.40, 2.00),
    "S2" to Site(1.20, 0.10, 0.50, 2.00),
    "S3" to Site(1.30, 0.15, 0.60, 2.00),
    "S4" to Site(1.35, 0.15, 0.70, 2.00)
)
private val type2 = mapOf(
    "S1" to Site(1.00, 0.05, 0.25, 1.20),
    "S2" to Site(1.30, 0.05, 0.30, 1.20),
    "S3" to Site(1.55, 0.10, 0.40, 1.20),
    "S4" to Site(1.80, 0.10, 0.50, 1.20)
)
private val importance = mapOf("1A" to 1.40, "1B" to 1.20, "2" to 1.00, "3" to 0.80)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) { CivilworkApp() }
}

@Composable
private fun CivilworkApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Teal,
            background = Bg,
            surface = Panel,
            onPrimary = Color(0xFF171006),
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        var zoneCode by remember { mutableStateOf("III") }
        var siteCode by remember { mutableStateOf("S3") }
        var importanceCode by remember { mutableStateOf("1A") }
        var weight by remember { mutableStateOf("10357.95") }
        var height by remember { mutableStateOf("11.22") }
        var stories by remember { mutableStateOf("3") }
        var behaviorR by remember { mutableStateOf("3.5") }
        var qualityQ by remember { mutableStateOf("1.10") }
        var ct by remember { mutableStateOf("0.050") }
        var columnB by remember { mutableStateOf("40") }
        var columnH by remember { mutableStateOf("50") }
        var columnNd by remember { mutableStateOf("1495.43") }

        val zone = zones.first { it.code == zoneCode }
        val site = (if (zone.type == 1) type1 else type2)[siteCode] ?: type2.getValue("S3")
        val iFactor = importance[importanceCode] ?: 1.0
        val r = behaviorR.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 3.5
        val q = qualityQ.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val w = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        val h = height.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        val n = stories.replace(',', '.').toDoubleOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
        val ctValue = ct.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.05
        val eta = max(0.70, sqrt(7.0 / 9.0))
        val tEmp = ctValue * h.pow(0.75)
        val lambda = if (n > 2 && tEmp <= 2.0 * site.t2 + 1e-9) 0.85 else 1.0
        val sd = designAcceleration(tEmp, zone, site, iFactor, r, q)
        val baseShear = lambda * sd * w
        val b = columnB.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        val hc = columnH.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        val nd = columnNd.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
        val nu = if (b > 0.0 && hc > 0.0) nd * 10.0 / (b * hc * 25.0) else 0.0
        val nuOk = nu <= 0.35 + 1e-9

        BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
            val wide = maxWidth >= 900.dp
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 1180.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    TopBar()
                    Hero()
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            ConfigCard(
                                modifier = Modifier.weight(1.0f),
                                zoneCode = zoneCode,
                                siteCode = siteCode,
                                importanceCode = importanceCode,
                                weight = weight,
                                height = height,
                                stories = stories,
                                behaviorR = behaviorR,
                                qualityQ = qualityQ,
                                ct = ct,
                                columnB = columnB,
                                columnH = columnH,
                                columnNd = columnNd,
                                onZone = { zoneCode = it },
                                onSite = { siteCode = it },
                                onImportance = { importanceCode = it },
                                onWeight = { weight = it },
                                onHeight = { height = it },
                                onStories = { stories = it },
                                onR = { behaviorR = it },
                                onQ = { qualityQ = it },
                                onCt = { ct = it },
                                onB = { columnB = it },
                                onH = { columnH = it },
                                onNd = { columnNd = it }
                            )
                            ResultsColumn(
                                modifier = Modifier.weight(1.15f),
                                zone = zone,
                                site = site,
                                iFactor = iFactor,
                                r = r,
                                q = q,
                                eta = eta,
                                tEmp = tEmp,
                                lambda = lambda,
                                sd = sd,
                                baseShear = baseShear,
                                nu = nu,
                                nuOk = nuOk
                            )
                        }
                    } else {
                        ConfigCard(
                            modifier = Modifier.fillMaxWidth(),
                            zoneCode = zoneCode,
                            siteCode = siteCode,
                            importanceCode = importanceCode,
                            weight = weight,
                            height = height,
                            stories = stories,
                            behaviorR = behaviorR,
                            qualityQ = qualityQ,
                            ct = ct,
                            columnB = columnB,
                            columnH = columnH,
                            columnNd = columnNd,
                            onZone = { zoneCode = it },
                            onSite = { siteCode = it },
                            onImportance = { importanceCode = it },
                            onWeight = { weight = it },
                            onHeight = { height = it },
                            onStories = { stories = it },
                            onR = { behaviorR = it },
                            onQ = { qualityQ = it },
                            onCt = { ct = it },
                            onB = { columnB = it },
                            onH = { columnH = it },
                            onNd = { columnNd = it }
                        )
                        ResultsColumn(
                            modifier = Modifier.fillMaxWidth(),
                            zone = zone,
                            site = site,
                            iFactor = iFactor,
                            r = r,
                            q = q,
                            eta = eta,
                            tEmp = tEmp,
                            lambda = lambda,
                            sd = sd,
                            baseShear = baseShear,
                            nu = nu,
                            nuOk = nuOk
                        )
                    }
                    Footer()
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Panel)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text("A", color = Accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Column {
            Text("ARCHILAB", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("CIVILWORK", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(
            color = Color(0xFF10261F),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, Color(0xFF1F5946))
        ) {
            Text(
                "WASM · HORS LIGNE",
                color = Teal,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Button(
            onClick = { window.open("https://luxecham.github.io/Archilab-RPA2024-Studio/app/", "_blank") },
            colors = ButtonDefaults.buttonColors(containerColor = PanelSoft, contentColor = TextMain),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Application complète", fontSize = 10.sp)
        }
    }
}

@Composable
private fun Hero() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RPA 2024 · BAEL 91 · CBA 93", color = Teal, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            "Le calcul parasismique, directement dans le navigateur.",
            color = TextMain,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 34.sp
        )
        Text(
            "Spectre de calcul, effort tranchant statique et effort normal réduit, exécutés localement " +
                "en WebAssembly. Aucun fichier n'est envoyé à un serveur.",
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun ConfigCard(
    modifier: Modifier,
    zoneCode: String,
    siteCode: String,
    importanceCode: String,
    weight: String,
    height: String,
    stories: String,
    behaviorR: String,
    qualityQ: String,
    ct: String,
    columnB: String,
    columnH: String,
    columnNd: String,
    onZone: (String) -> Unit,
    onSite: (String) -> Unit,
    onImportance: (String) -> Unit,
    onWeight: (String) -> Unit,
    onHeight: (String) -> Unit,
    onStories: (String) -> Unit,
    onR: (String) -> Unit,
    onQ: (String) -> Unit,
    onCt: (String) -> Unit,
    onB: (String) -> Unit,
    onH: (String) -> Unit,
    onNd: (String) -> Unit
) {
    PanelCard(modifier) {
        Text("Données du projet", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("Paramètres RPA 2024 et section de poteau", color = Muted, fontSize = 11.sp)
        ChoiceRow("Zone sismique", zones.map { it.code }, zoneCode, onZone)
        ChoiceRow("Classe de site", listOf("S1", "S2", "S3", "S4"), siteCode, onSite)
        ChoiceRow("Groupe d'importance", listOf("1A", "1B", "2", "3"), importanceCode, onImportance)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Poids sismique W (kN)", weight, onWeight, Modifier.weight(1f))
            NumberField("Hauteur Hn (m)", height, onHeight, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Niveaux N", stories, onStories, Modifier.weight(1f))
            NumberField("Coefficient R", behaviorR, onR, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Facteur Q", qualityQ, onQ, Modifier.weight(1f))
            NumberField("CT", ct, onCt, Modifier.weight(1f))
        }
        HorizontalDivider(color = Line)
        Text("Vérification rapide d'un poteau", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("b (cm)", columnB, onB, Modifier.weight(1f))
            NumberField("h (cm)", columnH, onH, Modifier.weight(1f))
        }
        NumberField("Effort normal Nd (kN)", columnNd, onNd, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ResultsColumn(
    modifier: Modifier,
    zone: Zone,
    site: Site,
    iFactor: Double,
    r: Double,
    q: Double,
    eta: Double,
    tEmp: Double,
    lambda: Double,
    sd: Double,
    baseShear: Double,
    nu: Double,
    nuOk: Boolean
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Période Temp", "${fmt(tEmp, 3)} s", "CT · Hn^0,75", Modifier.weight(1f))
            Kpi("λ", fmt(lambda, 2), if (lambda < 1.0) "T ≤ 2·T2" else "N ≤ 2 ou T > 2·T2", Modifier.weight(1f))
            Kpi("Sd/g", fmt(sd, 4), "Spectre de calcul", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("V_MSE", "${fmt(baseShear, 0)} kN", "λ · Sd/g · W", Modifier.weight(1.2f))
            Kpi("η amortissement", fmt(eta, 4), "ξ = 7 %", Modifier.weight(0.9f))
            Kpi("Borne min.", fmt(0.20 * zone.a * iFactor, 4), "0,20·A·I", Modifier.weight(0.9f))
        }
        PanelCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Spectre de calcul", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("A = ${fmt(zone.a, 2)} · I = ${fmt(iFactor, 2)} · R = ${fmt(r, 1)} · Q = ${fmt(q, 2)}",
                    color = Muted, fontSize = 9.sp)
            }
            SpectrumChart(zone, site, iFactor, r, q)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 s", color = Muted, fontSize = 9.sp)
                Text("1 s", color = Muted, fontSize = 9.sp)
                Text("2 s", color = Muted, fontSize = 9.sp)
                Text("3 s", color = Muted, fontSize = 9.sp)
                Text("4 s", color = Muted, fontSize = 9.sp)
            }
        }
        PanelCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Effort normal réduit", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("ν = Nd / (Bc · fc28), fc28 = 25 MPa", color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (nuOk) "Condition Vérifiée (CV)" else "NON VÉRIFIÉE",
                    color = if (nuOk) Teal else Danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ν = ${fmt(nu, 3)}", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("limite 0,350", color = Muted, fontSize = 10.sp)
            }
        }
        Surface(
            color = PanelSoft,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Périmètre de cette version web", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                Text(
                    "Évaluation en ligne des paramètres sismiques et d'une section de poteau. " +
                        "La note CTC complète, les plans DXF, le DQE et le pipeline ETABS/SAFE restent dans l'application Windows.",
                    color = Muted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { content() }
    }
}

@Composable
private fun ChoiceRow(label: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Muted, fontSize = 10.sp)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            choices.forEach { choice ->
                val active = choice == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Accent else PanelSoft)
                        .clickable { onSelect(choice) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        choice,
                        color = if (active) Color(0xFF171006) else Muted,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 10.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0B1220))
                .then(Modifier)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = TextMain, fontSize = 13.sp)
            )
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, note: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Panel,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label.uppercase(), color = Muted, fontSize = 9.sp)
            Text(value, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(note, color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun SpectrumChart(zone: Zone, site: Site, iFactor: Double, r: Double, q: Double) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(top = 8.dp)
    ) {
        val left = 8f
        val top = 8f
        val right = size.width - 8f
        val bottom = size.height - 8f
        for (step in 0..4) {
            val y = top + (bottom - top) * step / 4f
            drawLine(Color(0xFF16223A), Offset(left, y), Offset(right, y), strokeWidth = 1f)
            val x = left + (right - left) * step / 4f
            drawLine(Color(0xFF16223A), Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
        }
        val maxValue = max(0.05, designAcceleration(2.0, zone, site, iFactor, r, q) * 1.35)
        val path = Path()
        val steps = 220
        for (index in 0..steps) {
            val t = 4.0 * index / steps
            val value = designAcceleration(t, zone, site, iFactor, r, q)
            val x = left + (right - left) * (t / 4.0).toFloat()
            val y = bottom - (bottom - top) * (value / maxValue).toFloat().coerceIn(0f, 1f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Accent, style = Stroke(width = 2.4f))
        drawLine(Color(0xFF2A3954), Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.2f)
    }
}

private fun designAcceleration(
    t: Double,
    zone: Zone,
    site: Site,
    i: Double,
    r: Double,
    q: Double
): Double {
    if (zone.a <= 0.0) return 0.0
    val base = zone.a * i * site.s
    val plateau = 2.5 * q / r
    val value = when {
        t <= 0.0 -> base * 2.0 / 3.0
        t < site.t1 -> base * (2.0 / 3.0 + (t / site.t1) * (plateau - 2.0 / 3.0))
        t < site.t2 -> base * plateau
        t < site.t3 -> base * plateau * (site.t2 / t)
        t <= 4.0 -> base * plateau * (site.t2 * site.t3 / (t * t))
        else -> 0.0
    }
    return max(value, 0.20 * zone.a * i)
}

@Composable
private fun Footer() {
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HorizontalDivider(color = Line)
        Text(
            "ARCHILAB CIVILWORK · WebAssembly · Les calculs restent dans votre navigateur.",
            color = Muted,
            fontSize = 9.sp
        )
        Text(
            "Outil d'aide à la justification. Les résultats doivent être validés par un ingénieur qualifié et le CTC.",
            color = Color(0xFF6F819B),
            fontSize = 9.sp
        )
    }
}

private fun fmt(value: Double, decimals: Int = 2): String {
    if (!value.isFinite()) return "—"
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor) / factor
    var text = rounded.toString()
    if (decimals > 0 && !text.contains('.')) {
        text += "." + "0".repeat(decimals)
    }
    return text.replace('.', ',')
}
