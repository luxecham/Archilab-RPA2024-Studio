package com.archilab.rpa2024.sample

import com.archilab.rpa2024.model.InfillType
import com.archilab.rpa2024.model.SeismicConfig
import com.archilab.rpa2024.parser.BaseReactionRow
import com.archilab.rpa2024.parser.ColumnForceRow
import com.archilab.rpa2024.parser.DiaphragmDisplacementRow
import com.archilab.rpa2024.parser.E2kModelParser
import com.archilab.rpa2024.parser.EtabsImport
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.ModalRatioRow
import com.archilab.rpa2024.parser.StoryShearRow
import com.archilab.rpa2024.parser.TopologyBuilder
import java.nio.file.Path

/**
 * Cas de validation reconstruit a partir de la note de calcul du JOINT A
 * (1500 LLV BOSQUET, Sidi Bel Abbes), etablie sous RPA 2024.
 *
 * Le fichier e2k est genere a partir de la trame et des niveaux de la NDC, puis relu par
 * le meme parseur que n'importe quel modele ETABS : la topologie n'est jamais injectee
 * directement dans le modele metier.
 */
object SampleJointA {

    const val JOINT_NAME = "JOINT A"

    private const val STORY_HEIGHT = 3.74
    private const val TOP_ELEVATION = 14.96

    private val XS = doubleArrayOf(0.0, 7.5, 15.0)
    private val YS = doubleArrayOf(0.0, 4.65, 9.65, 15.15, 20.65)
    private val X_LABELS = listOf("A", "B", "C")
    private val Y_LABELS = listOf("1", "2", "3", "4", "5")

    private data class StorySpec(val name: String, val elevation: Double, val cage: Boolean)

    private val STORIES = listOf(
        StorySpec("N1", 0.0, false),
        StorySpec("N2", STORY_HEIGHT, false),
        StorySpec("N3", 2.0 * STORY_HEIGHT, false),
        StorySpec("CAGE", 3.0 * STORY_HEIGHT, true)
    )

    /** Altitudes de noeuds : un niveau par etage, plus le couronnement de la cage. */
    private val Z_LEVELS: List<Double> = STORIES.map { it.elevation } + TOP_ELEVATION

    val defaultConfig = SeismicConfig(
        zoneCode = "III",
        siteClassCode = "S3",
        importanceGroupCode = "1A",
        occupancyCaseCode = "HABITATION_BUREAUX",
        bracingSystemId = 3,
        infillType = InfillType.DENSE,
        planRegular = true,
        elevationRegular = true
    )

    fun load(config: SeismicConfig = defaultConfig, jointName: String = JOINT_NAME): EtabsImport {
        val raw = E2kModelParser().parseText(e2kText(), "$jointName.e2k")
        val model = TopologyBuilder().build(jointName, raw, config)
        val results = results()
        return EtabsImport(
            model = model,
            results = results,
            warnings = (model.warnings + results.warnings).distinct()
        )
    }

    fun writeE2k(path: Path): Path {
        path.toFile().writeText(e2kText(), Charsets.UTF_8)
        return path
    }

    fun e2kText(): String {
        val table = StringBuilder()
        table.appendLine("TABLE: \"Program Control\"")
        table.appendLine("CurrUnits=\"KN, m\"")

        table.appendLine("TABLE: \"Stories\"")
        STORIES.forEach { story ->
            table.appendLine("Story=\"${story.name}\" Height=$STORY_HEIGHT Elevation=${story.elevation}")
        }

        table.appendLine("TABLE: \"Grid Lines\"")
        X_LABELS.forEachIndexed { index, label ->
            table.appendLine("Label=\"$label\" Dir=\"X\" Ordinate=${XS[index]}")
        }
        Y_LABELS.forEachIndexed { index, label ->
            table.appendLine("Label=\"$label\" Dir=\"Y\" Ordinate=${YS[index]}")
        }

        table.appendLine("TABLE: \"Point Coordinates\"")
        for (zi in Z_LEVELS.indices) {
            for (yi in YS.indices) {
                for (xi in XS.indices) {
                    table.appendLine(
                        "Point=\"${pointId(xi, yi, zi)}\" X=${XS[xi]} Y=${YS[yi]} Z=${Z_LEVELS[zi]}"
                    )
                }
            }
        }

        table.appendLine("TABLE: \"Material Properties\"")
        table.appendLine("Material=\"B25\" Type=\"Concrete\" Fc=25")
        table.appendLine("Material=\"A500\" Type=\"Rebar\" Fy=500")

        table.appendLine("TABLE: \"Frame Sections\"")
        table.appendLine("Section=\"POT35x55\" Material=\"B25\" Shape=\"Rectangular\" t3=0.55 t2=0.35")
        table.appendLine("Section=\"POT40x50\" Material=\"B25\" Shape=\"Rectangular\" t3=0.50 t2=0.40")
        table.appendLine("Section=\"POT35x35\" Material=\"B25\" Shape=\"Rectangular\" t3=0.35 t2=0.35")
        table.appendLine("Section=\"B30x40\" Material=\"B25\" Shape=\"Rectangular\" t3=0.40 t2=0.30")
        table.appendLine("Section=\"B30x35\" Material=\"B25\" Shape=\"Rectangular\" t3=0.35 t2=0.30")

        table.appendLine("TABLE: \"Wall/Slab/Deck Properties\"")
        table.appendLine(
            "Section=\"16+5\" Material=\"B25\" Type=\"Slab\" Thickness=0.05 TotalThickness=0.21 " +
                "H0=0.05 HollowBlockHeight=0.16 RibWidth=0.10 RibSpacing=0.60"
        )

        table.appendLine("TABLE: \"Line Connectivities\"")
        for (storyIndex in STORIES.indices) {
            val story = STORIES[storyIndex]
            for (yi in YS.indices) {
                for (xi in XS.indices) {
                    table.appendLine(
                        "Line=\"${columnId(xi, yi, story.name)}\" " +
                            "Point1=\"${pointId(xi, yi, storyIndex)}\" " +
                            "Point2=\"${pointId(xi, yi, storyIndex + 1)}\" " +
                            "Section=\"${columnSection(xi, yi)}\" Story=\"${story.name}\" LineType=\"Column\""
                    )
                }
            }
            for (yi in YS.indices) {
                for (xi in 0 until XS.lastIndex) {
                    table.appendLine(
                        "Line=\"BX_${xi}_${yi}_${story.name}\" " +
                            "Point1=\"${pointId(xi, yi, storyIndex)}\" " +
                            "Point2=\"${pointId(xi + 1, yi, storyIndex)}\" " +
                            "Section=\"B30x40\" Story=\"${story.name}\" LineType=\"Beam\""
                    )
                }
            }
            for (xi in XS.indices) {
                for (yi in 0 until YS.lastIndex) {
                    table.appendLine(
                        "Line=\"BY_${xi}_${yi}_${story.name}\" " +
                            "Point1=\"${pointId(xi, yi, storyIndex)}\" " +
                            "Point2=\"${pointId(xi, yi + 1, storyIndex)}\" " +
                            "Section=\"B30x35\" Story=\"${story.name}\" LineType=\"Beam\""
                    )
                }
            }
        }

        table.appendLine("TABLE: \"Area Connectivities\"")
        for (storyIndex in STORIES.indices) {
            table.appendLine(
                "Area=\"S_${STORIES[storyIndex].name}\" " +
                    "Point1=\"${pointId(0, 0, storyIndex)}\" " +
                    "Point2=\"${pointId(XS.lastIndex, 0, storyIndex)}\" " +
                    "Point3=\"${pointId(XS.lastIndex, YS.lastIndex, storyIndex)}\" " +
                    "Point4=\"${pointId(0, YS.lastIndex, storyIndex)}\" " +
                    "Section=\"16+5\" Story=\"${STORIES[storyIndex].name}\" AreaType=\"Floor\""
            )
        }
        return table.toString()
    }

    /**
     * Tableaux de resultats NDC : poids sismique, reactions de base, periodes modales,
     * deplacements EX/EY et efforts normaux des trois familles de poteaux.
     */
    fun results(): EtabsResults {
        val storyShears = mutableListOf<StoryShearRow>()
        CUMULATIVE_WEIGHTS.forEach { (story, weight) ->
            storyShears += StoryShearRow(story, "Bottom", "WI", 0.0, 0.0, weight)
        }
        EX_STORY_SHEAR.forEach { (story, shear) ->
            storyShears += StoryShearRow(story, "Bottom", "EX", shear, 0.0)
        }
        EY_STORY_SHEAR.forEach { (story, shear) ->
            storyShears += StoryShearRow(story, "Bottom", "EY", 0.0, shear)
        }

        val displacements = mutableListOf<DiaphragmDisplacementRow>()
        EX_DISPLACEMENTS.forEach { (story, value) ->
            displacements += DiaphragmDisplacementRow(story, "EX", value, 0.0)
        }
        EY_DISPLACEMENTS.forEach { (story, value) ->
            displacements += DiaphragmDisplacementRow(story, "EY", 0.0, value)
        }

        val columnForces = COLUMN_AXIAL_KN.map { (section, axial) ->
            ColumnForceRow(
                story = "N1",
                element = representativeColumn(section),
                loadCase = "ELA",
                stationMeters = 0.0,
                pKN = -axial,
                v2KN = 0.0,
                v3KN = 0.0,
                m2KNm = 120.0,
                m3KNm = 150.0
            )
        }

        return EtabsResults(
            modalRatios = listOf(
                ModalRatioRow(
                    mode = 1, periodSeconds = 0.399,
                    uxPercent = 68.0, uyPercent = 0.5, uzPercent = 0.0,
                    sumUxPercent = 68.0, sumUyPercent = 0.5, sumUzPercent = 0.0
                ),
                ModalRatioRow(
                    mode = 2, periodSeconds = 0.294,
                    uxPercent = 2.0, uyPercent = 70.0, uzPercent = 0.0,
                    sumUxPercent = 70.0, sumUyPercent = 70.5, sumUzPercent = 0.0
                ),
                ModalRatioRow(
                    mode = 3, periodSeconds = 0.213,
                    uxPercent = 20.0, uyPercent = 19.5, uzPercent = 0.0,
                    sumUxPercent = 90.0, sumUyPercent = 90.0, sumUzPercent = 0.0
                )
            ),
            baseReactions = listOf(
                BaseReactionRow("EX", fxKN = 1884.51, fyKN = 0.0, fzKN = 0.0),
                BaseReactionRow("EY", fxKN = 0.0, fyKN = 1819.47, fzKN = 0.0)
            ),
            storyShears = storyShears,
            diaphragmDisplacements = displacements,
            columnForces = columnForces
        )
    }

    private fun columnSection(xi: Int, yi: Int): String = when {
        (xi == 0 || xi == XS.lastIndex) && (yi == 0 || yi == YS.lastIndex) -> "POT35x55"
        (xi == 1 && (yi == 0 || yi == YS.lastIndex)) || yi == 2 -> "POT40x50"
        else -> "POT35x35"
    }

    private fun representativeColumn(section: String): String {
        for (yi in YS.indices) {
            for (xi in XS.indices) {
                if (columnSection(xi, yi) == section) return columnId(xi, yi, "N1")
            }
        }
        error("Aucun poteau de section $section dans le modele d'exemple.")
    }

    private fun pointId(xi: Int, yi: Int, zi: Int): String = "N$xi$yi$zi"

    private fun columnId(xi: Int, yi: Int, story: String): String = "C_${xi}_${yi}_$story"

    private val CUMULATIVE_WEIGHTS = linkedMapOf(
        "N1" to 10357.95,
        "N2" to 6854.80,
        "N3" to 3351.60,
        "CAGE" to 290.00
    )

    private val EX_STORY_SHEAR = linkedMapOf(
        "N1" to 1884.51,
        "N2" to 1420.00,
        "N3" to 860.00,
        "CAGE" to 210.00
    )

    private val EY_STORY_SHEAR = linkedMapOf(
        "N1" to 1819.47,
        "N2" to 1360.00,
        "N3" to 820.00,
        "CAGE" to 200.00
    )

    private val EX_DISPLACEMENTS = linkedMapOf(
        "N1" to 0.005,
        "N2" to 0.009,
        "N3" to 0.012,
        "CAGE" to 0.014
    )

    private val EY_DISPLACEMENTS = linkedMapOf(
        "N1" to 0.004,
        "N2" to 0.007,
        "N3" to 0.010,
        "CAGE" to 0.0115
    )

    private val COLUMN_AXIAL_KN = linkedMapOf(
        "POT35x55" to 673.0,
        "POT40x50" to 1496.0,
        "POT35x35" to 446.0
    )
}
