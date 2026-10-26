package com.archilab.rpa2024.parser

import kotlin.math.abs

/**
 * Lecteur des tableaux de resultats ETABS (v9.7.4, v18, v21) : participation modale,
 * reactions de base, efforts tranchants d'etage, deplacements, efforts poteaux/poutres/voiles.
 * Les noms de tables et de colonnes sont unifies par alias et toutes les valeurs sont converties
 * vers kN, m et kN.m.
 */
class EtabsResultsReader {

    fun read(tables: List<TabularTable>, declaredUnits: UnitSystem? = null): EtabsResults {
        val warnings = mutableListOf<String>()
        val units = declaredUnits ?: detectUnits(tables)

        val modalRatios = readModalRatios(tables, units, warnings)
        val baseReactions = readBaseReactions(tables, units)
        val storyShears = readStoryShears(tables, units)
        val displacements = readDisplacements(tables, units)
        val columnForces = readColumnForces(tables, units)
        val beamForces = readBeamForces(tables, units)
        val pierForces = readPierForces(tables, units)
        val massRigidityCenters = readMassRigidityCenters(tables, units)

        if (modalRatios.isEmpty()) warnings += "Table 'Modal Participating Mass Ratios' absente ou vide : périodes issues du calcul analytique uniquement."
        if (storyShears.isEmpty()) warnings += "Table 'Story Shears' absente ou vide : poids sismique non extrait d'ETABS."
        if (displacements.isEmpty()) warnings += "Table des déplacements absente ou vide : vérification des déplacements non automatisée."
        if (columnForces.isEmpty()) warnings += "Table 'Column Forces' absente ou vide : vérification de l'effort normal réduit non automatisée."
        if (massRigidityCenters.isEmpty()) warnings += "Table 'Centers Of Mass And Rigidity' absente ou vide : régularité en plan non automatisée."

        return EtabsResults(
            modalRatios = modalRatios,
            baseReactions = baseReactions,
            storyShears = storyShears,
            diaphragmDisplacements = displacements,
            columnForces = columnForces,
            beamForces = beamForces,
            pierForces = pierForces,
            massRigidityCenters = massRigidityCenters,
            warnings = warnings
        )
    }

    private fun readModalRatios(
        tables: List<TabularTable>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): List<ModalRatioRow> {
        val matching = findAll(tables, "Modal Participating Mass Ratios", "Modal Participation Mass Ratios", "Modal Participating Mass")
        if (matching.isEmpty()) return emptyList()

        val rows = mutableListOf<List<Double?>>()
        val periods = mutableListOf<Double>()
        for (table in matching) {
            table.forEachRow { row ->
                val modeNumber = row.number("Mode", "ModeNumber", "Mode No")?.toInt() ?: return@forEachRow
                val period = row.number("Period", "T") ?: 0.0
                val ux = row.percent("UX", "Ux")
                val uy = row.percent("Uy", "UY")
                val uz = row.percent("Uz", "UZ")
                val sumUx = row.percent("SumUX", "Sum Ux")
                val sumUy = row.percent("SumUY", "Sum Uy")
                val sumUz = row.percent("SumUZ", "Sum Uz")
                val rx = row.percent("RX", "Rx")
                val ry = row.percent("RY", "Ry")
                val rz = row.percent("RZ", "Rz")
                rows += listOf(modeNumber.toDouble(), period, ux, uy, uz, sumUx, sumUy, sumUz, rx, ry, rz)
                periods += period
            }
        }
        if (rows.isEmpty()) return emptyList()

        // ETABS peut exporter les participations en fraction (0,60) ou en pourcentage (60)
        // et omettre les colonnes SumUX/SumUY. La normale doit porter sur toutes les
        // colonnes de participation, pas uniquement sur les sommes absentes.
        val participationValues = rows.flatMap { values ->
            listOfNotNull(values[2], values[3], values[4], values[5], values[6], values[7])
        }
        val maxCumulative = rows.mapNotNull { it[5] ?: it[6] }.maxOrNull() ?: 0.0
        val maxParticipation = participationValues.maxOrNull() ?: 0.0
        val scale = when {
            maxCumulative > 1.5 -> 1.0
            maxCumulative > 1e-9 -> 100.0
            maxParticipation in 1e-9..1.5 -> 100.0
            else -> 1.0
        }
        if (scale > 1.0) warnings += "Participations massiques exprimées en fraction : conversion automatique en pourcentage."

        return rows.map { values ->
            ModalRatioRow(
                mode = values[0]!!.toInt(),
                periodSeconds = values[1] ?: 0.0,
                uxPercent = (values[2] ?: 0.0) * scale,
                uyPercent = (values[3] ?: 0.0) * scale,
                uzPercent = (values[4] ?: 0.0) * scale,
                sumUxPercent = (values[5] ?: 0.0) * scale,
                sumUyPercent = (values[6] ?: 0.0) * scale,
                sumUzPercent = (values[7] ?: 0.0) * scale,
                rxPercent = (values[8] ?: 0.0) * scale,
                ryPercent = (values[9] ?: 0.0) * scale,
                rzPercent = (values[10] ?: 0.0) * scale
            )
        }.sortedBy { it.mode }
    }

    private fun readBaseReactions(tables: List<TabularTable>, units: UnitSystem): List<BaseReactionRow> {
        val matching = findAll(tables, "Response Spectrum Base Reactions", "Base Reactions", "Reactions")
        val rows = mutableListOf<BaseReactionRow>()
        for (table in matching) {
            val alias = ColumnAliasResolver(table.headers)
            val hasSpectrumColumns = alias.resolve("FX") != null || alias.resolve("F1") != null
            if (!hasSpectrumColumns) continue
            table.forEachRow { row ->
                val loadCase = row.value("LoadCase", "Load Case", "Load Pattern", "Case", "Output Case")
                    ?: return@forEachRow
                val fx = row.number("FX", "F1", "GlobalFX") ?: 0.0
                val fy = row.number("FY", "F2", "GlobalFY") ?: 0.0
                val fz = row.number("FZ", "F3", "GlobalFZ") ?: 0.0
                rows += BaseReactionRow(
                    loadCase = loadCase,
                    fxKN = units.toKN(fx),
                    fyKN = units.toKN(fy),
                    fzKN = units.toKN(fz),
                    story = row.value("Story", "Level")
                )
            }
        }
        return rows.distinct()
    }

    private fun readStoryShears(tables: List<TabularTable>, units: UnitSystem): List<StoryShearRow> {
        val matching = findAll(tables, "Story Shears", "Story Forces")
        val rows = mutableListOf<StoryShearRow>()
        for (table in matching) {
            val alias = ColumnAliasResolver(table.headers)
            if (alias.resolve("VX") == null && alias.resolve("Vx") == null) continue
            table.forEachRow { row ->
                val story = row.value("Story", "Level") ?: return@forEachRow
                val loadCase = row.value("LoadCase", "Load Case", "Load Case/Combo", "Case", "Output Case")
                    ?: return@forEachRow
                rows += StoryShearRow(
                    story = story,
                    location = row.value("Location", "Loc") ?: "",
                    loadCase = loadCase,
                    vxKN = units.toKN(row.number("VX", "Vx", "F1") ?: 0.0),
                    vyKN = units.toKN(row.number("VY", "Vy", "F2") ?: 0.0),
                    pKN = units.toKN(row.number("P", "PX", "FZ") ?: 0.0)
                )
            }
        }
        return rows.distinct()
    }

    private fun readDisplacements(tables: List<TabularTable>, units: UnitSystem): List<DiaphragmDisplacementRow> {
        val matching = findAll(
            tables,
            "Diaphragm CM Displacements",
            "Diaphragm Center of Mass Displacements",
            "Story Displacements",
            "Joint Displacements"
        )
        val rows = mutableListOf<DiaphragmDisplacementRow>()
        for (table in matching) {
            table.forEachRow { row ->
                val story = row.value("Story", "Level") ?: return@forEachRow
                val loadCase = row.value("LoadCase", "Load Case", "Load Case/Combo", "Case", "Output Case")
                    ?: return@forEachRow
                rows += DiaphragmDisplacementRow(
                    story = story,
                    loadCase = loadCase,
                    uxMeters = units.toMeters(row.number("UX", "Ux", "U1") ?: 0.0),
                    uyMeters = units.toMeters(row.number("UY", "Uy", "U2") ?: 0.0),
                    uzMeters = units.toMeters(row.number("UZ", "Uz", "U3") ?: 0.0),
                    diaphragm = row.value("Diaphragm", "Diaph")
                )
            }
        }
        return rows.distinct()
    }

    private fun readMassRigidityCenters(
        tables: List<TabularTable>,
        units: UnitSystem
    ): List<MassRigidityCenterRow> {
        val matching = findAll(
            tables,
            "Centers Of Mass And Rigidity",
            "Center of Mass and Rigidity",
            "Center Mass Rigidity",
            "Centres de masse et de rigidite"
        )
        val rows = mutableListOf<MassRigidityCenterRow>()
        for (table in matching) {
            table.forEachRow { row ->
                val story = row.value("Story", "Level", "Niveau") ?: return@forEachRow
                val xcm = row.number("XCM", "X CM", "CenterMassX", "CentreMasseX") ?: return@forEachRow
                val ycm = row.number("YCM", "Y CM", "CenterMassY", "CentreMasseY") ?: return@forEachRow
                val xcr = row.number("XCR", "X CR", "CenterRigidityX", "CentreRigiditeX") ?: xcm
                val ycr = row.number("YCR", "Y CR", "CenterRigidityY", "CentreRigiditeY") ?: ycm
                rows += MassRigidityCenterRow(
                    story = story,
                    diaphragm = row.value("Diaphragm", "Diaph", "Diaphragme") ?: "",
                    massX = row.number("MassX", "Mass X", "MasseX") ?: 0.0,
                    massY = row.number("MassY", "Mass Y", "MasseY") ?: 0.0,
                    xcmMeters = units.toMeters(xcm),
                    ycmMeters = units.toMeters(ycm),
                    xcrMeters = units.toMeters(xcr),
                    ycrMeters = units.toMeters(ycr)
                )
            }
        }
        return rows.distinct()
    }

    private fun readColumnForces(tables: List<TabularTable>, units: UnitSystem): List<ColumnForceRow> {
        val matching = findAll(tables, "Column Forces", "Element Forces - Columns", "Element Forces Columns")
        val rows = mutableListOf<ColumnForceRow>()
        for (table in matching) {
            table.forEachRow { row ->
                val story = row.value("Story", "Level")
                val element = row.value("Column", "UniqueName", "Frame", "Label") ?: return@forEachRow
                val loadCase = row.value("LoadCase", "Load Case", "Load Case/Combo", "Case", "Output Case")
                    ?: return@forEachRow
                rows += ColumnForceRow(
                    story = story ?: "",
                    element = element,
                    loadCase = loadCase,
                    stationMeters = units.toMeters(row.number("Station", "Loc", "Location") ?: 0.0),
                    pKN = units.toKN(row.number("P", "P1") ?: 0.0),
                    v2KN = units.toKN(row.number("V2") ?: 0.0),
                    v3KN = units.toKN(row.number("V3") ?: 0.0),
                    m2KNm = units.toKNm(row.number("M2") ?: 0.0),
                    m3KNm = units.toKNm(row.number("M3") ?: 0.0),
                    lengthMeters = row.number("Length", "L")?.let { units.toMeters(it) }
                )
            }
        }
        return rows.distinct()
    }

    private fun readBeamForces(tables: List<TabularTable>, units: UnitSystem): List<BeamForceRow> {
        val matching = findAll(tables, "Beam Forces", "Element Forces - Beams", "Element Forces Beams")
        val rows = mutableListOf<BeamForceRow>()
        for (table in matching) {
            table.forEachRow { row ->
                val story = row.value("Story", "Level")
                val element = row.value("Beam", "UniqueName", "Frame", "Label") ?: return@forEachRow
                val loadCase = row.value("LoadCase", "Load Case", "Load Case/Combo", "Case", "Output Case")
                    ?: return@forEachRow
                rows += BeamForceRow(
                    story = story ?: "",
                    element = element,
                    loadCase = loadCase,
                    stationMeters = units.toMeters(row.number("Station", "Loc", "Location") ?: 0.0),
                    pKN = units.toKN(row.number("P", "P1") ?: 0.0),
                    v2KN = units.toKN(row.number("V2") ?: 0.0),
                    v3KN = units.toKN(row.number("V3") ?: 0.0),
                    m2KNm = units.toKNm(row.number("M2") ?: 0.0),
                    m3KNm = units.toKNm(row.number("M3") ?: 0.0),
                    lengthMeters = row.number("Length", "L")?.let { units.toMeters(it) }
                )
            }
        }
        return rows.distinct()
    }

    private fun readPierForces(tables: List<TabularTable>, units: UnitSystem): List<PierForceRow> {
        val matching = findAll(tables, "Pier Forces", "Wall Forces", "Element Forces - Walls")
        val rows = mutableListOf<PierForceRow>()
        for (table in matching) {
            table.forEachRow { row ->
                val story = row.value("Story", "Level")
                val pier = row.value("Pier", "Wall", "UniqueName", "Label") ?: return@forEachRow
                val loadCase = row.value("LoadCase", "Load Case", "Load Case/Combo", "Case", "Output Case")
                    ?: return@forEachRow
                rows += PierForceRow(
                    story = story ?: "",
                    pier = pier,
                    loadCase = loadCase,
                    location = row.value("Location", "Loc") ?: "",
                    stationMeters = units.toMeters(row.number("Station", "Loc", "Location") ?: 0.0),
                    pKN = units.toKN(row.number("P", "P1") ?: 0.0),
                    v2KN = units.toKN(row.number("V2") ?: 0.0),
                    v3KN = units.toKN(row.number("V3") ?: 0.0),
                    m2KNm = units.toKNm(row.number("M2") ?: 0.0),
                    m3KNm = units.toKNm(row.number("M3") ?: 0.0),
                    lengthMeters = row.number("Length", "L")?.let { units.toMeters(it) }
                )
            }
        }
        return rows.distinct()
    }

    private fun detectUnits(tables: List<TabularTable>): UnitSystem {
        val program = find(tables, "Program Control", "Units")
        if (program != null && program.rowCount > 0) {
            val row = program.row(0)
            val value = row.value("CurrUnits", "Units", "Current Units")
            if (!value.isNullOrBlank()) return UnitSystem.fromEtabsUnits(value)
        }
        return UnitSystem.KN_M
    }

    private fun find(tables: List<TabularTable>, vararg aliases: String): TabularTable? {
        for (alias in aliases) {
            val target = normalize(alias)
            tables.firstOrNull { normalize(it.name) == target }?.let { return it }
        }
        for (alias in aliases) {
            val target = normalize(alias)
            tables.firstOrNull { normalize(it.name).startsWith(target) }?.let { return it }
        }
        for (alias in aliases) {
            val target = normalize(alias)
            tables.firstOrNull { normalize(it.name).contains(target) }?.let { return it }
        }
        return null
    }

    private fun findAll(tables: List<TabularTable>, vararg aliases: String): List<TabularTable> {
        val normalizedAliases = aliases.map { normalize(it) }
        return tables.filter { table ->
            val name = normalize(table.name)
            normalizedAliases.any { name == it || name.startsWith(it) || name.contains(it) }
        }.distinctBy { normalize(it.name) }
    }

    private fun normalize(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]"), "")
}
