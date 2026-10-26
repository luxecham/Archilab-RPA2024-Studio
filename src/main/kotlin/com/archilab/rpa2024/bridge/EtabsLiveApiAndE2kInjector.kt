package com.archilab.rpa2024.bridge

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.seismic.SpectrumExportResult
import com.archilab.rpa2024.engine.seismic.SpectrumGenerator
import com.archilab.rpa2024.graphics.ChartPoint
import com.archilab.rpa2024.graphics.ChartSeries
import com.archilab.rpa2024.graphics.ScientificChartRenderer
import com.archilab.rpa2024.parser.E2kModelParser
import com.archilab.rpa2024.parser.UnitSystem
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.report.ReportChapter
import com.archilab.rpa2024.report.ReportDocument
import com.archilab.rpa2024.report.ReportText
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.math.cbrt

data class EtabsMaterialProperties(
    val name: String,
    val type: String,
    val massDensityTonsM3: Double,
    val weightDensityKNM3: Double,
    val elasticModulusKNM2: Double,
    val shearModulusKNM2: Double,
    val poisson: Double,
    val thermalCoefficient: Double,
    val compressiveStrengthKNM2: Double? = null,
    val yieldStrengthKNM2: Double? = null,
    val tensileStrengthKNM2: Double? = null
)

data class EtabsMassSource(
    val name: String,
    val permanentPattern: String,
    val livePattern: String,
    val permanentMultiplier: Double,
    val liveMultiplier: Double,
    val lateralMassOnly: Boolean = true,
    val lumpAtStories: Boolean = true
)

data class EtabsSpectrumFunction(val name: String, val file: Path, val vertical: Boolean)
data class EtabsSpectrumCase(
    val name: String,
    val functionName: String,
    val direction: String,
    val eccentricity: Double,
    val modalCombination: String = "CQC"
)
data class EtabsLoadTerm(val load: String, val factor: Double)
data class EtabsLoadCombination(val name: String, val terms: List<EtabsLoadTerm>, val type: String = "ADD")

data class EtabsInjectionConfig(
    val spectrumGenerator: SpectrumGenerator,
    val psi: Double,
    val concreteName: String = "BA_C25",
    val steelName: String = "FE500",
    val massSourceName: String = "RPA2024_FROM_LOADS",
    val permanentPattern: String = "G",
    val livePattern: String = "Q",
    val fc28KNM2: Double = 25_000.0,
    val fykKNM2: Double = 500_000.0,
    val massDensityTonsM3: Double = 2.548,
    val weightDensityKNM3: Double = 25.0,
    val poisson: Double = 0.20,
    val thermalCoefficient: Double = 1.0e-5,
    val accidentalEccentricity: Double = 0.05,
    val spectrumStepSeconds: Double = 0.02
) {
    init {
        require(psi in 0.0..1.0) { "Le coefficient ψ doit être compris entre 0 et 1." }
        require(fc28KNM2 > 0.0 && fykKNM2 > 0.0) { "Les résistances des matériaux doivent être positives." }
        require(massDensityTonsM3 > 0.0 && weightDensityKNM3 > 0.0) { "Les densités doivent être positives." }
        require(poisson > -1.0 && poisson < 0.5) { "Le coefficient de Poisson doit appartenir à ]-1 ; 0,5[." }
        require(accidentalEccentricity >= 0.0) { "L'excentricité accidentelle doit être positive ou nulle." }
    }
}

data class EtabsInjectionResult(
    val text: String,
    val materials: List<EtabsMaterialProperties>,
    val massSource: EtabsMassSource,
    val functions: List<EtabsSpectrumFunction>,
    val cases: List<EtabsSpectrumCase>,
    val combinations: List<EtabsLoadCombination>,
    val spectrumExport: SpectrumExportResult,
    val reportChapters: List<ReportChapter>
) {
    fun integrateInto(document: ReportDocument): ReportDocument {
        val additions = reportChapters.associateBy { it.number }
        fun sameManagedBlock(first: ReportBlock, second: ReportBlock): Boolean = when {
            first is ReportBlock.Figure && second is ReportBlock.Figure -> first.key == second.key
            first is ReportBlock.KeyValues && second is ReportBlock.KeyValues -> first.caption == second.caption
            first is ReportBlock.Table && second is ReportBlock.Table -> first.caption == second.caption
            else -> false
        }
        return document.copy(chapters = document.chapters.map { chapter ->
            val addition = additions[chapter.number] ?: return@map chapter
            chapter.copy(blocks = chapter.blocks.filterNot { existing ->
                addition.blocks.any { sameManagedBlock(existing, it) }
            } + addition.blocks)
        })
    }
}

interface EtabsLiveAdapter {
    fun setMaterial(material: EtabsMaterialProperties, units: UnitSystem)
    fun setMassSource(source: EtabsMassSource)
    fun setResponseSpectrumFunction(function: EtabsSpectrumFunction)
    fun setResponseSpectrumCase(case: EtabsSpectrumCase)
    fun setLoadCombination(combination: EtabsLoadCombination)
}

class EtabsLiveApiAndE2kInjector(
    private val chartRenderer: ScientificChartRenderer = ScientificChartRenderer()
) {
    fun materials(config: EtabsInjectionConfig): List<EtabsMaterialProperties> {
        val elastic = 11_000_000.0 * cbrt(config.fc28KNM2 / 1_000.0)
        val shear = elastic / (2.0 * (1.0 + config.poisson))
        return listOf(
            EtabsMaterialProperties(
                name = config.concreteName,
                type = "CONCRETE",
                massDensityTonsM3 = config.massDensityTonsM3,
                weightDensityKNM3 = config.weightDensityKNM3,
                elasticModulusKNM2 = elastic,
                shearModulusKNM2 = shear,
                poisson = config.poisson,
                thermalCoefficient = config.thermalCoefficient,
                compressiveStrengthKNM2 = config.fc28KNM2
            ),
            EtabsMaterialProperties(
                name = config.steelName,
                type = "REBAR",
                massDensityTonsM3 = 7.850,
                weightDensityKNM3 = 76.972,
                elasticModulusKNM2 = 210_000_000.0,
                shearModulusKNM2 = 210_000_000.0 / (2.0 * 1.3),
                poisson = 0.30,
                thermalCoefficient = 1.2e-5,
                yieldStrengthKNM2 = config.fykKNM2,
                tensileStrengthKNM2 = config.fykKNM2
            )
        )
    }

    fun combinations(config: EtabsInjectionConfig): List<EtabsLoadCombination> {
        val g = config.permanentPattern
        val q = config.livePattern
        val wi = listOf(EtabsLoadTerm(g, 1.0), EtabsLoadTerm(q, config.psi))
        val combinations = mutableListOf(
            EtabsLoadCombination("ELU", listOf(EtabsLoadTerm(g, 1.35), EtabsLoadTerm(q, 1.50))),
            EtabsLoadCombination("ELS", listOf(EtabsLoadTerm(g, 1.0), EtabsLoadTerm(q, 1.0))),
            EtabsLoadCombination("WI", wi),
            EtabsLoadCombination("E1", listOf(EtabsLoadTerm("EX", 1.0), EtabsLoadTerm("EY", 0.30))),
            EtabsLoadCombination("E2", listOf(EtabsLoadTerm("EX", 0.30), EtabsLoadTerm("EY", 1.0)))
        )
        val seismicSigns = listOf(
            1.0 to 0.30, -1.0 to -0.30, 1.0 to -0.30, -1.0 to 0.30,
            0.30 to 1.0, -0.30 to -1.0, 0.30 to -1.0, -0.30 to 1.0
        )
        seismicSigns.forEachIndexed { index, (ex, ey) ->
            combinations += EtabsLoadCombination(
                "COMB${index + 1}",
                wi + EtabsLoadTerm("EX", ex) + EtabsLoadTerm("EY", ey)
            )
        }
        listOf("E1", "E2").forEach { direction ->
            listOf(1.0, -1.0).forEach { sign ->
                val suffix = if (sign > 0.0) "P" else "N"
                combinations += EtabsLoadCombination(
                    "PORT_${direction}_$suffix",
                    listOf(EtabsLoadTerm(g, 0.80), EtabsLoadTerm(direction, sign))
                )
            }
        }
        return combinations
    }

    fun injectText(
        sourceText: String,
        config: EtabsInjectionConfig,
        spectrumDirectory: Path,
        declaredUnits: UnitSystem? = null
    ): EtabsInjectionResult {
        val units = declaredUnits ?: detectUnits(sourceText)
        val export = config.spectrumGenerator.exportSpectrumToEtabsFile(
            spectrumDirectory,
            step = config.spectrumStepSeconds
        )
        val materials = materials(config)
        val massSource = EtabsMassSource(
            name = config.massSourceName,
            permanentPattern = config.permanentPattern,
            livePattern = config.livePattern,
            permanentMultiplier = 1.0,
            liveMultiplier = config.psi
        )
        val functions = listOf(
            EtabsSpectrumFunction("RPA2024_H", export.horizontalPath.toAbsolutePath().normalize(), false),
            EtabsSpectrumFunction("RPA2024_V", export.verticalPath.toAbsolutePath().normalize(), true)
        )
        val cases = listOf(
            EtabsSpectrumCase("EX", "RPA2024_H", "U1", config.accidentalEccentricity),
            EtabsSpectrumCase("EY", "RPA2024_H", "U2", config.accidentalEccentricity)
        )
        val combinations = combinations(config)
        val block = buildString {
            table("MATERIAL PROPERTIES 01 - GENERAL", materials.map {
                mapOf("Material" to it.name, "Type" to it.type, "DesignType" to it.type)
            })
            table("MATERIAL PROPERTIES 02 - MECHANICAL", materials.map { material ->
                buildMap {
                    put("Material", material.name)
                    put("MassPerVol", CsiText.number(CsiUnits.massDensity(material.massDensityTonsM3, units)))
                    put("WeightPerVol", CsiText.number(CsiUnits.weightDensity(material.weightDensityKNM3, units)))
                    put("E", CsiText.number(CsiUnits.stress(material.elasticModulusKNM2, units)))
                    put("G", CsiText.number(CsiUnits.stress(material.shearModulusKNM2, units)))
                    put("U", CsiText.number(material.poisson))
                    put("A", CsiText.number(material.thermalCoefficient))
                    material.compressiveStrengthKNM2?.let { put("Fc", CsiText.number(CsiUnits.stress(it, units))) }
                    material.yieldStrengthKNM2?.let { put("Fy", CsiText.number(CsiUnits.stress(it, units))) }
                    material.tensileStrengthKNM2?.let { put("Fu", CsiText.number(CsiUnits.stress(it, units))) }
                }
            })
            table("MASS SOURCE 01 - GENERAL", listOf(mapOf(
                "MassSource" to massSource.name,
                "Source" to "LOADS",
                "LateralMassOnly" to if (massSource.lateralMassOnly) "YES" else "NO",
                "LumpAtStories" to if (massSource.lumpAtStories) "YES" else "NO"
            )))
            table("MASS SOURCE 02 - LOAD PATTERNS", listOf(
                mapOf("MassSource" to massSource.name, "LoadPat" to massSource.permanentPattern,
                    "Multiplier" to CsiText.number(massSource.permanentMultiplier)),
                mapOf("MassSource" to massSource.name, "LoadPat" to massSource.livePattern,
                    "Multiplier" to CsiText.number(massSource.liveMultiplier))
            ))
            table("FUNCTION - RESPONSE SPECTRUM - FROM FILE", functions.map {
                mapOf("Name" to it.name, "FileName" to it.file.toString(), "HeaderLines" to "0",
                    "FunctionType" to if (it.vertical) "VERTICAL" else "HORIZONTAL")
            })
            table("LOAD CASES - RESPONSE SPECTRUM", cases.map {
                mapOf("Name" to it.name, "Function" to it.functionName, "Dir" to it.direction,
                    "Eccen" to CsiText.number(it.eccentricity), "ModalComb" to it.modalCombination,
                    "MassSource" to massSource.name)
            })
            table("LOAD COMBINATIONS 01 - GENERAL", combinations.map {
                mapOf("Name" to it.name, "Type" to it.type)
            })
            table("LOAD COMBINATIONS 02 - ADDITIVE", combinations.flatMap { combination ->
                combination.terms.map { term ->
                    mapOf("Name" to combination.name, "LoadCase" to term.load, "SF" to CsiText.number(term.factor))
                }
            })
        }
        val text = CsiManagedText.upsert(sourceText, "ETABS", block)
        return EtabsInjectionResult(
            text = text,
            materials = materials,
            massSource = massSource,
            functions = functions,
            cases = cases,
            combinations = combinations,
            spectrumExport = export,
            reportChapters = reportChapters(config, materials, massSource, cases, combinations)
        )
    }

    fun injectFile(
        source: Path,
        config: EtabsInjectionConfig,
        spectrumDirectory: Path = source.parent,
        output: Path = source
    ): EtabsInjectionResult {
        val result = injectText(E2kModelParser.readText(source), config, spectrumDirectory)
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, result.text, Charsets.UTF_8)
        return result
    }

    fun applyLive(
        adapter: EtabsLiveAdapter,
        units: UnitSystem,
        config: EtabsInjectionConfig,
        spectrumDirectory: Path
    ): EtabsInjectionResult {
        val result = injectText("TABLE: \"PROGRAM CONTROL\"\r\nCurrUnits=\"${units.code}\"", config, spectrumDirectory, units)
        result.materials.forEach { adapter.setMaterial(it, units) }
        adapter.setMassSource(result.massSource)
        result.functions.forEach(adapter::setResponseSpectrumFunction)
        result.cases.forEach(adapter::setResponseSpectrumCase)
        result.combinations.forEach(adapter::setLoadCombination)
        return result
    }

    fun reportChapters(
        config: EtabsInjectionConfig,
        materials: List<EtabsMaterialProperties> = materials(config),
        massSource: EtabsMassSource = EtabsMassSource(config.massSourceName, config.permanentPattern,
            config.livePattern, 1.0, config.psi),
        cases: List<EtabsSpectrumCase> = listOf(
            EtabsSpectrumCase("EX", "RPA2024_H", "U1", config.accidentalEccentricity),
            EtabsSpectrumCase("EY", "RPA2024_H", "U2", config.accidentalEccentricity)
        ),
        combinations: List<EtabsLoadCombination> = combinations(config)
    ): List<ReportChapter> {
        val concrete = materials.first { it.type == "CONCRETE" }
        val materialChart = chartRenderer.renderCartesian(
            title = "Configuration des matériaux ETABS",
            subtitle = "Béton BAEL 91 révisé 99 et acier — propriétés mécaniques injectées",
            xLabel = "Matériau",
            yLabel = "Module d'élasticité E (millions kN/m²)",
            series = listOf(ChartSeries("E", "#1d4ed8", materials.mapIndexed { index, material ->
                ChartPoint(index.toDouble(), material.elasticModulusKNM2 / 1_000_000.0, material.name)
            })),
            xRange = 0.0..(materials.size - 1).coerceAtLeast(1).toDouble(),
            yRange = 0.0..(materials.maxOf { it.elasticModulusKNM2 } / 1_000_000.0 * 1.15),
            annotations = materials.mapIndexed { index, material ->
                ChartPoint(index.toDouble(), material.elasticModulusKNM2 / 1_000_000.0, material.name)
            }
        )
        val combinationChart = chartRenderer.renderCartesian(
            title = "Enveloppe des combinaisons sismiques",
            subtitle = "Directions EX/EY et excentricité accidentelle de ${ReportText.plain(config.accidentalEccentricity, 2)}",
            xLabel = "Combinaison",
            yLabel = "Facteur directionnel",
            series = listOf(
                ChartSeries("EX", "#dc2626", combinations.filter { it.name.startsWith("COMB") }.mapIndexed { index, combo ->
                    ChartPoint(index + 1.0, combo.terms.firstOrNull { it.load == "EX" }?.factor ?: 0.0)
                }),
                ChartSeries("EY", "#1d4ed8", combinations.filter { it.name.startsWith("COMB") }.mapIndexed { index, combo ->
                    ChartPoint(index + 1.0, combo.terms.firstOrNull { it.load == "EY" }?.factor ?: 0.0)
                })
            ),
            xRange = 1.0..8.0,
            yRange = -1.2..1.2,
            yDecimals = 1
        )
        val spectrumChart = chartRenderer.renderSpectrum(config.spectrumGenerator, stepSeconds = config.spectrumStepSeconds)
        return listOf(
            ReportChapter(3, "Matériaux et configuration ETABS", "Propriétés injectées dans les unités actives du modèle.",
                CheckStatus.OK, listOf(
                    ReportBlock.KeyValues("Béton ${concrete.name}", listOf(
                        "Masse volumique" to "${ReportText.num(concrete.massDensityTonsM3, 3)} t/m³",
                        "Poids volumique" to "${ReportText.num(concrete.weightDensityKNM3)} kN/m³",
                        "Eij = 11000 ∛fc28" to "${ReportText.num(concrete.elasticModulusKNM2, 0)} kN/m²",
                        "G = E / [2(1+ν)]" to "${ReportText.num(concrete.shearModulusKNM2, 0)} kN/m²",
                        "ν / α" to "${ReportText.plain(concrete.poisson, 2)} / ${concrete.thermalCoefficient}"
                    )),
                    ReportBlock.Figure(materialChart.png, materialChart.caption, key = "bridge-materials")
                )),
            ReportChapter(4, "Masses, cas de charge et combinaisons", "Source From Loads et enveloppes ELU, ELS et sismiques.",
                CheckStatus.OK, listOf(
                    ReportBlock.KeyValues("Source de masse ${massSource.name}", listOf(
                        "G" to ReportText.plain(massSource.permanentMultiplier, 2),
                        "Q" to "ψ = ${ReportText.plain(massSource.liveMultiplier, 2)}",
                        "Masse latérale uniquement" to if (massSource.lateralMassOnly) "Oui" else "Non",
                        "Masse concentrée aux niveaux" to if (massSource.lumpAtStories) "Oui" else "Non"
                    )),
                    ReportBlock.Table("Combinaisons injectées", listOf("Nom", "Définition"), combinations.map { combo ->
                        listOf(combo.name, combo.terms.joinToString(" + ") {
                            "${ReportText.plain(it.factor, 2)} ${it.load}"
                        }.replace("+ -", "− "))
                    }),
                    ReportBlock.Figure(combinationChart.png, combinationChart.caption, key = "bridge-combinations")
                )),
            ReportChapter(9, "Spectres et analyse modale directionnelle", "Spectres RPA 2024 de 0 à 4 s et combinaison CQC.",
                CheckStatus.OK, listOf(
                    ReportBlock.Table("Cas response-spectrum", listOf("Cas", "Fonction", "Direction", "Excentricité", "Modes"),
                        cases.map { listOf(it.name, it.functionName, it.direction, ReportText.plain(it.eccentricity, 2), it.modalCombination) }),
                    ReportBlock.Figure(spectrumChart.png, spectrumChart.caption, key = "bridge-spectrum")
                ))
        )
    }

    private fun detectUnits(text: String): UnitSystem {
        val declared = Regex("(?i)(?:CurrUnits|CurrentUnits|Units)\\s*=\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: Regex("(?im)^\\s*\\$?\\s*UNITS?\\s*[:=]\\s*\"?([A-Za-z,\\s\\-]+)\"?").find(text)?.groupValues?.get(1)
        return declared?.let(UnitSystem::fromEtabsUnits) ?: UnitSystem.KN_M
    }
}

internal object CsiUnits {
    fun stress(knM2: Double, units: UnitSystem): Double =
        knM2 / units.forceFactorToKN * units.lengthFactorToMeters * units.lengthFactorToMeters

    fun weightDensity(knM3: Double, units: UnitSystem): Double =
        knM3 / units.forceFactorToKN * units.lengthFactorToMeters.pow(3)

    fun massDensity(tonsM3: Double, units: UnitSystem): Double =
        tonsM3 * 9.80665 / units.forceFactorToKN * units.lengthFactorToMeters.pow(4)

    fun lineStiffness(knM2: Double, units: UnitSystem): Double =
        knM2 / units.forceFactorToKN * units.lengthFactorToMeters * units.lengthFactorToMeters

    private fun Double.pow(exponent: Int): Double = Math.pow(this, exponent.toDouble())
}

internal object CsiText {
    fun number(value: Double): String = String.format(Locale.ROOT, "%.10g", value)

    fun row(fields: Map<String, String>): String = fields.entries.joinToString(" ") { (key, raw) ->
        val value = if (raw.any(Char::isWhitespace)) "\"${raw.replace("\"", "'")}\"" else raw
        "$key=$value"
    }
}

internal object CsiManagedText {
    fun upsert(source: String, marker: String, content: String): String {
        val begin = "$ RPA2024:BEGIN:$marker"
        val end = "$ RPA2024:END:$marker"
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val retained = mutableListOf<String>()
        var skipping = false
        normalized.lineSequence().forEach { line ->
            when (line.trim()) {
                begin -> skipping = true
                end -> skipping = false
                else -> if (!skipping) retained += line
            }
        }
        return buildString {
            append(retained.joinToString("\r\n").trimEnd())
            if (isNotEmpty()) append("\r\n\r\n")
            append(begin).append("\r\n")
            append(content.trimEnd()).append("\r\n")
            append(end).append("\r\n")
        }
    }
}

private fun StringBuilder.table(name: String, rows: List<Map<String, String>>) {
    append("TABLE: \"").append(name).append("\"\r\n")
    rows.forEach { append(CsiText.row(it)).append("\r\n") }
    append("\r\n")
}
