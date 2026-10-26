package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.RawMaterial
import com.archilab.rpa2024.parser.TabularRow
import com.archilab.rpa2024.parser.TabularSource
import com.archilab.rpa2024.parser.TabularTable
import com.archilab.rpa2024.parser.UnitSystem
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Nature de la semelle filante lue dans SAFE. */
enum class StripFootingKind(val label: String) {
    CURRENT("Semelle courante"),
    SEISMIC_JOINT("Semelle sous joint sismique");

    companion object {
        fun fromName(name: String): StripFootingKind {
            val normalized = name.uppercase().replace(" ", "")
            return if (
                normalized.contains("SFJ") ||
                normalized.contains("JOINT") ||
                normalized.contains("SISMIQUE") ||
                normalized.contains("SEISMIC")
            ) {
                SEISMIC_JOINT
            } else {
                CURRENT
            }
        }
    }
}

/**
 * Famille de semelle filante issue de la table « Beam Properties » de SAFE.
 * Les sections en Te utilisent B (largeur de table), h0 (epaisseur de table),
 * b0 (largeur d'ame) et h (hauteur totale) ; les sections rectangulaires ont B = b0
 * et h0 = 0. Aucune dimension n'est imposee : tout provient de l'export.
 */
data class StripFootingFamily(
    val name: String,
    val kind: StripFootingKind,
    val flangeWidthMeters: Double,
    val tableThicknessMeters: Double,
    val webWidthMeters: Double,
    val totalHeightMeters: Double,
    val topCoverMeters: Double = 0.05,
    val bottomCoverMeters: Double = 0.05,
    val footingLengthMeters: Double? = null,
    val materialName: String? = null,
    val elementIds: List<String> = emptyList(),
    val sourceTable: String = ""
) {
    init {
        require(name.isNotBlank()) { "Nom de semelle vide." }
        require(flangeWidthMeters > 0.0) { "Largeur de semelle invalide." }
        require(webWidthMeters > 0.0) { "Largeur d'ame invalide." }
        require(totalHeightMeters > 0.0) { "Hauteur de semelle invalide." }
        require(tableThicknessMeters >= 0.0 && tableThicknessMeters < totalHeightMeters) {
            "Epaisseur de table incompatible avec la hauteur."
        }
    }

    val isTee: Boolean
        get() = tableThicknessMeters > 1e-9 && flangeWidthMeters > webWidthMeters + 1e-6

    val webHeightMeters: Double get() = totalHeightMeters - tableThicknessMeters

    /** Hauteur utile de la nervure pour la flexion longitudinale. */
    val effectiveDepthMeters: Double get() = totalHeightMeters - bottomCoverMeters

    /** Hauteur utile de la table pour la flexion transversale. */
    val tableEffectiveDepthMeters: Double get() = tableThicknessMeters - bottomCoverMeters

    fun withElementIds(ids: List<String>): StripFootingFamily =
        if (ids.isEmpty() || ids == elementIds) this else copy(elementIds = ids)

    val sectionLabel: String
        get() = if (isTee) {
            "T ${format(flangeWidthMeters)} x ${format(totalHeightMeters)} " +
                "(table ${format(flangeWidthMeters)} x ${format(tableThicknessMeters)}, " +
                "ame ${format(webWidthMeters)})"
        } else {
            "${format(webWidthMeters)} x ${format(totalHeightMeters)}"
        }

    private fun format(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f", value)
}

/**
 * Ressort de sol lineaire lu dans « Line Spring Properties ».
 * K_line = Ks . B ; si SAFE ne fournit que K_line, Ks est deduit de la largeur.
 */
data class LineSpringFamily(
    val name: String,
    val widthMeters: Double,
    val subgradeModulusKNM3: Double,
    val lineStiffnessKNM2: Double,
    val sourceTable: String = "",
    val notes: List<String> = emptyList()
) {
    init {
        require(name.isNotBlank()) { "Nom de ressort vide." }
        require(widthMeters > 0.0) { "Largeur de ressort invalide." }
        require(subgradeModulusKNM3 > 0.0) { "Module de reaction du sol invalide." }
    }

    companion object {
        /** Largeur deduite d'un nom de ressort lorsque la table ne la fournit pas (K120 -> 1,20 m). */
        fun widthFromName(name: String): Double? {
            val token = Regex("\\d+(?:[.,]\\d+)?").find(name)?.value
                ?.replace(',', '.')
                ?.toDoubleOrNull()
                ?: return null
            return if (token >= 10.0) token / 100.0 else token
        }
    }
}

/** Parametres geotechniques et reglementaires des fondations. */
data class FoundationGeotechnicalParameters(
    val allowableStressBars: Double = 1.2,
    val allowableStressKNM2: Double = allowableStressBars * 100.0,
    val foundationDepthMeters: Double = 1.5,
    val subgradeModulusKNM3: Double = 21_848.0,
    val concreteUnitWeightKNM3: Double = 25.0
) {
    init {
        require(allowableStressBars > 0.0) { "Contrainte admissible du sol invalide." }
        require(allowableStressKNM2 > 0.0) { "Contrainte admissible en kN/m2 invalide." }
        require(foundationDepthMeters >= 0.0) { "Profondeur d'assise invalide." }
        require(subgradeModulusKNM3 > 0.0) { "Module de reaction du sol invalide." }
    }

    /** 1 bar = 100 kN/m2. */
    val allowableStressBarsFromKNM2: Double get() = allowableStressKNM2 / 100.0
}

/** Deplacement vertical d'un noeud de la semelle, rattache a un cas ELS. */
data class VerticalDisplacementPoint(
    val joint: String,
    val xMeters: Double,
    val yMeters: Double,
    val loadCase: String,
    val verticalMeters: Double
) {
    val isUplift: Boolean get() = verticalMeters > 1e-6
}

/**
 * Enveloppe des deplacements verticaux. La convention SAFE est retenue :
 * U3 negatif = tassement, U3 positif = soulevement. Le tassement de calcul
 * reste positif quelle que soit la convention d'axe de l'export.
 */
data class VerticalDisplacementEnvelope(
    val points: List<VerticalDisplacementPoint>,
    val maxPoint: VerticalDisplacementPoint,
    val minPoint: VerticalDisplacementPoint,
    val governingPoint: VerticalDisplacementPoint
) {
    val maxValueMeters: Double get() = maxPoint.verticalMeters
    val minValueMeters: Double get() = minPoint.verticalMeters
    val maxAbsoluteMeters: Double get() = abs(governingPoint.verticalMeters)
    val settlementMeters: Double
        get() = if (minValueMeters < 0.0) abs(minValueMeters) else abs(maxValueMeters)
    val upliftMeters: Double get() = max(0.0, maxValueMeters)
    val governingLoadCase: String get() = governingPoint.loadCase
}

/** Efforts d'une famille de semelle pour une categorie de combinaison. */
data class StripFootingCombinationForces(
    val familyName: String,
    val category: ComboCategory,
    val combo: String,
    val axialKN: Double,
    val axialCompressionKN: Double,
    val axialTensionKN: Double,
    val supportMomentKNm: Double,
    val spanMomentKNm: Double,
    val shearKN: Double,
    val stationLengthMeters: Double,
    val elementIds: List<String> = emptyList()
)

/**
 * Enveloppe de dimensionnement d'une famille : ELU, ELS et ELA sont conserves
 * separement pour le rapport, les valeurs de calcul etant l'enveloppe des
 * situations ELU et ELA.
 */
data class StripFootingForceEnvelope(
    val familyName: String,
    val ultimate: StripFootingCombinationForces? = null,
    val service: StripFootingCombinationForces? = null,
    val accidental: StripFootingCombinationForces? = null,
    val designSupportMomentKNm: Double = 0.0,
    val designSpanMomentKNm: Double = 0.0,
    val designShearKN: Double = 0.0,
    val designAxialKN: Double = 0.0,
    val serviceAxialKN: Double = 0.0,
    val governingCategory: ComboCategory? = null,
    val elementIds: List<String> = emptyList(),
    val notes: List<String> = emptyList()
) {
    val hasDesignForces: Boolean
        get() = designSupportMomentKNm + designSpanMomentKNm + designShearKN > 1e-9
}

/** Modele SAFE homogene, pret pour le dimensionnement et les rendus. */
data class SafeFoundationModel(
    val families: List<StripFootingFamily>,
    val springs: List<LineSpringFamily>,
    val displacements: List<VerticalDisplacementPoint>,
    val displacementEnvelope: VerticalDisplacementEnvelope?,
    val forceEnvelopes: List<StripFootingForceEnvelope>,
    val materials: List<RawMaterial>,
    val geotechnical: FoundationGeotechnicalParameters,
    val units: UnitSystem,
    val sourceName: String = "SAFE",
    val warnings: List<String> = emptyList()
) {
    fun family(name: String): StripFootingFamily? =
        families.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    fun forcesFor(familyName: String): StripFootingForceEnvelope? =
        forceEnvelopes.firstOrNull { it.familyName.equals(familyName, ignoreCase = true) }

    fun springFor(family: StripFootingFamily): LineSpringFamily? {
        springs.firstOrNull { it.name.equals(family.name, ignoreCase = true) }?.let { return it }
        val familyDigits = digitsOnly(family.name)
        if (familyDigits.isNotEmpty()) {
            springs.firstOrNull { digitsOnly(it.name) == familyDigits }?.let { return it }
        }
        return springs.firstOrNull { abs(it.widthMeters - family.flangeWidthMeters) <= 1e-6 }
    }

    fun materialFor(family: StripFootingFamily, fallback: MaterialConfig = MaterialConfig()): MaterialConfig {
        val concrete = materials.firstOrNull {
            it.isConcrete && family.materialName != null && it.name.equals(family.materialName, true)
        } ?: materials.firstOrNull { it.isConcrete }
        val steel = materials.firstOrNull { it.isSteel }
        return fallback.copy(
            concreteClass = concrete?.name ?: fallback.concreteClass,
            fc28MPa = concrete?.fc28MPa?.takeIf { it > 0.0 } ?: fallback.fc28MPa,
            steelGrade = steel?.name ?: fallback.steelGrade,
            feMPa = steel?.fyMPa?.takeIf { it > 0.0 } ?: fallback.feMPa
        )
    }

    private fun digitsOnly(value: String): String = value.filter { it.isDigit() }
}

/**
 * Extracteur des donnees SAFE (Excel, Access ou texte tabule). Les tables sont
 * identifiees par leurs noms et les colonnes par alias ; aucune liste de
 * semelles, de ressorts ou d'elements n'est codee en dur.
 */
class SafeFoundationExtractor {

    fun read(
        path: Path,
        geotechnical: FoundationGeotechnicalParameters = FoundationGeotechnicalParameters(),
        units: UnitSystem? = null
    ): SafeFoundationModel {
        val tables = TabularSource.read(path)
        return extract(
            tables = tables,
            geotechnical = geotechnical,
            units = units ?: detectUnits(tables),
            sourceName = path.fileName.toString()
        )
    }

    fun extract(
        tables: List<TabularTable>,
        geotechnical: FoundationGeotechnicalParameters = FoundationGeotechnicalParameters(),
        units: UnitSystem = detectUnits(tables),
        sourceName: String = "SAFE"
    ): SafeFoundationModel {
        val warnings = mutableListOf<String>()
        val families = parseFamilies(tables, units, warnings)
        val springs = parseSprings(tables, units, geotechnical, warnings)
        val coordinates = parseJointCoordinates(tables, units)
        val displacements = parseDisplacements(tables, units, coordinates, warnings)
        val displacementEnvelope = buildDisplacementEnvelope(displacements, warnings)
        val materials = parseMaterials(tables, units, warnings)
        val forceEnvelopes = parseForces(tables, families, warnings)

        if (families.isEmpty()) {
            warnings += "Aucune famille de semelle lue : vérifier la table « Beam Properties » de l'export SAFE."
        }
        if (springs.isEmpty()) {
            warnings += "Aucun ressort de sol lu : vérifier la table « Line Spring Properties »."
        }
        if (displacements.isEmpty()) {
            warnings += "Aucun déplacement vertical U3 lu : vérifier la table « Joint Displacements »."
        }
        if (forceEnvelopes.isEmpty()) {
            warnings += "Aucun effort de semelle lu : vérifier la table « Beam Forces »."
        }

        val enrichedFamilies = families.map { family ->
            val ids = forceEnvelopes.firstOrNull { it.familyName == family.name }?.elementIds.orEmpty()
            family.withElementIds(ids)
        }

        return SafeFoundationModel(
            families = enrichedFamilies,
            springs = springs,
            displacements = displacements,
            displacementEnvelope = displacementEnvelope,
            forceEnvelopes = forceEnvelopes,
            materials = materials,
            geotechnical = geotechnical,
            units = units,
            sourceName = sourceName,
            warnings = warnings
        )
    }

    // ------------------------------------------------------------- familles

    private fun parseFamilies(
        tables: List<TabularTable>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): List<StripFootingFamily> {
        val table = findTable(tables, "Beam Propert")
            ?: findTable(tables, "Frame Section Propert")
            ?: findTable(tables, "Section Propert", exclude = listOf("Spring", "Material"))
            ?: findTable(tables, "Beam Def")
            ?: tables.firstOrNull {
                val name = normalize(it.name)
                name.contains("BEAM") && (
                    name.contains("SECTION") || name.contains("PROPERTY") || name.contains("DEFINITION")
                    )
            }
            ?: return emptyList()

        val lengthFactor = tableLengthFactor(table, units)
        val families = mutableListOf<StripFootingFamily>()
        val seen = mutableSetOf<String>()

        table.forEachRow { row ->
            val rawName = text(row, "Name", "SectionName", "Section", "Property", "Label", "Frame", "Beam")
            if (rawName == null) return@forEachRow
            val name = rawName.trim()
            if (name.isBlank() || name.equals("None", true) || name.equals("Default", true)) return@forEachRow
            if (!seen.add(name)) return@forEachRow

            val width = number(
                row,
                "B", "FlangeWidth", "Flange Width", "TableWidth", "LargeurTable",
                "Width", "t2"
            )
            val webWidth = number(
                row,
                "b0", "WebWidth", "Web Width", "NervureWidth", "StemWidth", "bw"
            )
            val totalHeight = number(
                row,
                "h", "Height", "Depth", "TotalDepth", "Total Depth", "t3"
            )
            val tableThickness = number(
                row,
                "h0", "SlabThickness", "FlangeThickness", "Flange Thickness",
                "TableThickness", "tf", "TopThickness"
            )

            if (width == null && webWidth == null && totalHeight == null) {
                warnings += "Section '$name' ignorée : aucune dimension lisible (B, b0, h)."
                return@forEachRow
            }
            if (totalHeight == null || totalHeight <= 0.0) {
                warnings += "Section '$name' ignorée : hauteur h absente ou nulle."
                return@forEachRow
            }

            val bFlange = (width ?: webWidth ?: 0.0) * lengthFactor
            val bWeb = (webWidth ?: width ?: 0.0) * lengthFactor
            val h = totalHeight * lengthFactor
            var h0 = (tableThickness ?: 0.0) * lengthFactor
            if (bFlange <= 0.0 || bWeb <= 0.0) {
                warnings += "Section '$name' ignorée : largeur nulle."
                return@forEachRow
            }
            if (h0 >= h) {
                warnings += "Section '$name' : h0 = ${format(h0, 3)} m ≥ h = ${format(h, 3)} m, " +
                    "valeur ramenée à 0,90 h."
                h0 = 0.9 * h
            }
            val flange = max(bFlange, bWeb)
            val web = min(bFlange, bWeb)
            if (abs(flange - web) < 1e-9) h0 = 0.0

            val topCover = number(
                row,
                "d'", "dPrime", "TopCover", "Top Cover", "EnrobageSup", "Enrobage supérieur"
            )?.times(lengthFactor) ?: 0.05
            val bottomCover = number(
                row,
                "c", "BottomCover", "Bottom Cover", "EnrobageInf", "Enrobage inférieur"
            )?.times(lengthFactor) ?: 0.05
            if (topCover <= 0.0 || topCover >= h || bottomCover <= 0.0 || bottomCover >= h) {
                warnings += "Section '$name' : enrobages incohérents, valeurs 0,05 m retenues."
            }
            val safeTopCover = if (topCover <= 0.0 || topCover >= h) 0.05 else topCover
            val safeBottomCover = if (bottomCover <= 0.0 || bottomCover >= h) 0.05 else bottomCover

            val length = number(
                row,
                "Length", "Longueur", "FootingLength", "Footing Length", "L"
            )?.times(lengthFactor)?.takeIf { it > 0.0 }
            val material = text(row, "Material", "MaterialName", "Material Name", "Mat")
            val elements = text(row, "Elements", "ElementIDs", "Element IDs", "Assigned", "Beams")
                ?.split(Regex("[,;\\s]+"))
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

            families += StripFootingFamily(
                name = name,
                kind = StripFootingKind.fromName(name),
                flangeWidthMeters = flange,
                tableThicknessMeters = h0,
                webWidthMeters = web,
                totalHeightMeters = h,
                topCoverMeters = safeTopCover,
                bottomCoverMeters = safeBottomCover,
                footingLengthMeters = length,
                materialName = material,
                elementIds = elements,
                sourceTable = table.name
            )
        }

        if (families.isEmpty()) {
            warnings += "La table « ${table.name} » ne contient aucune section de semelle exploitable."
        }
        return families
    }

    // ------------------------------------------------------------- ressorts

    private fun parseSprings(
        tables: List<TabularTable>,
        units: UnitSystem,
        geotechnical: FoundationGeotechnicalParameters,
        warnings: MutableList<String>
    ): List<LineSpringFamily> {
        val table = findTable(tables, "Line Spring Propert")
            ?: findTable(tables, "Spring Propert", exclude = listOf("Material"))
            ?: tables.firstOrNull {
                val name = normalize(it.name)
                name.contains("SPRING") && name.contains("PROPERTY")
            }
            ?: return emptyList()

        val lengthFactor = tableLengthFactor(table, units)
        val springs = mutableListOf<LineSpringFamily>()

        table.forEachRow { row ->
            val name = text(row, "Name", "SpringName", "Spring Name", "Spring", "Label", "Property")
                ?: return@forEachRow
            val notes = mutableListOf<String>()

            var width = number(row, "Width", "B", "Largeur", "WidthMeters")?.times(lengthFactor)
            if (width == null || width <= 0.0) {
                width = LineSpringFamily.widthFromName(name)
                if (width != null) {
                    notes += "Largeur absente de la table : ${format(width, 2)} m déduits du nom '$name'."
                }
            }
            if (width == null || width <= 0.0) {
                width = 1.0
                notes += "Largeur absente et non déductible du nom : 1,00 m retenu pour le calcul de K_line."
            }

            val ks = number(row, "Ks", "SubgradeModulus", "Subgrade Modulus", "ModulusOfSubgradeReaction", "Kv")
            val lineStiffness = number(
                row,
                "Kline", "K_line", "LineStiffness", "Line Stiffness", "Stiffness", "K"
            )

            val resolvedKs = when {
                ks != null && ks > 0.0 -> ks
                lineStiffness != null && lineStiffness > 0.0 -> lineStiffness / width
                else -> {
                    notes += "Ks et K_line absents : module géotechnique " +
                        "${format(geotechnical.subgradeModulusKNM3, 0)} kN/m3 retenu."
                    geotechnical.subgradeModulusKNM3
                }
            }
            if (ks != null && ks <= 0.0) {
                notes += "Ks non positif dans l'export : valeur remplacée par le module géotechnique."
            }
            val resolvedLine = lineStiffness?.takeIf { it > 0.0 } ?: resolvedKs * width
            if (lineStiffness == null) {
                notes += "K_line recalculé : Ks . B = ${format(resolvedLine, 0)} kN/m2."
            }

            springs += LineSpringFamily(
                name = name,
                widthMeters = width,
                subgradeModulusKNM3 = resolvedKs,
                lineStiffnessKNM2 = resolvedLine,
                sourceTable = table.name,
                notes = notes
            )
            if (notes.isNotEmpty()) {
                warnings += "Ressort '$name' : ${notes.joinToString(" ")}"
            }
        }
        return springs
    }

    // --------------------------------------------------------- deplacements

    private fun parseJointCoordinates(
        tables: List<TabularTable>,
        units: UnitSystem
    ): Map<String, Pair<Double, Double>> {
        val table = findTable(tables, "Joint Coord")
            ?: findTable(tables, "Point Coord")
            ?: findTable(tables, "Joint Coordinates")
            ?: tables.firstOrNull {
                val name = normalize(it.name)
                (name.contains("JOINT") || name.contains("POINT")) && name.contains("COORD")
            }
            ?: return emptyMap()

        val lengthFactor = tableLengthFactor(table, units)
        val coordinates = mutableMapOf<String, Pair<Double, Double>>()
        table.forEachRow { row ->
            val id = text(row, "Joint", "UniqueName", "Unique Name", "Point", "Node", "Label", "Name")
                ?: return@forEachRow
            val x = number(row, "X", "GlobalX", "Global X", "XorR", "XCoordinate", "X Coordinate")
            val y = number(row, "Y", "GlobalY", "Global Y", "YorZ", "YCoordinate", "Y Coordinate")
            if (x != null && y != null) {
                coordinates[id] = x * lengthFactor to y * lengthFactor
            }
        }
        return coordinates
    }

    private fun parseDisplacements(
        tables: List<TabularTable>,
        units: UnitSystem,
        coordinates: Map<String, Pair<Double, Double>>,
        warnings: MutableList<String>
    ): List<VerticalDisplacementPoint> {
        val table = findTable(tables, "Joint Displacement")
            ?: findTable(tables, "Joint Displ")
            ?: tables.firstOrNull {
                val name = normalize(it.name)
                name.contains("DISPLACEMENT") && !name.contains("STORY")
            }
            ?: return emptyList()

        val lengthFactor = tableLengthFactor(table, units)
        val points = mutableListOf<VerticalDisplacementPoint>()
        table.forEachRow { row ->
            val vertical = number(
                row,
                "U3", "UZ", "Uz", "Vertical", "VerticalDisplacement", "Vertical Displacement",
                "TranslationZ", "Translation Z", "Z"
            ) ?: return@forEachRow
            val joint = text(row, "Joint", "UniqueName", "Unique Name", "Point", "Node", "Label")
                ?: "Nœud ${points.size + 1}"
            val x = number(row, "X", "GlobalX", "Global X", "XorR", "XCoordinate")?.times(lengthFactor)
                ?: coordinates[joint]?.first
                ?: 0.0
            val y = number(row, "Y", "GlobalY", "Global Y", "YorZ", "YCoordinate")?.times(lengthFactor)
                ?: coordinates[joint]?.second
                ?: 0.0
            val loadCase = text(row, "OutputCase", "Output Case", "LoadCase", "Load Case", "Case", "Combo")
                .orEmpty()
            points += VerticalDisplacementPoint(
                joint = joint,
                xMeters = x,
                yMeters = y,
                loadCase = loadCase,
                verticalMeters = vertical * lengthFactor
            )
        }

        if (points.isEmpty()) {
            warnings += "La table « ${table.name} » ne contient aucun déplacement vertical U3 exploitable."
        }
        return points
    }

    private fun buildDisplacementEnvelope(
        points: List<VerticalDisplacementPoint>,
        warnings: MutableList<String>
    ): VerticalDisplacementEnvelope? {
        if (points.isEmpty()) return null
        val servicePoints = points.filter {
            ComboCategory.classify(it.loadCase) == ComboCategory.ELS
        }
        val selected = servicePoints.ifEmpty {
            warnings += "Aucun cas ELS identifié dans les déplacements : enveloppe établie sur tous les cas."
            points
        }
        val maxPoint = selected.maxBy { it.verticalMeters }
        val minPoint = selected.minBy { it.verticalMeters }
        val governingPoint = selected.maxBy { abs(it.verticalMeters) }
        return VerticalDisplacementEnvelope(
            points = selected,
            maxPoint = maxPoint,
            minPoint = minPoint,
            governingPoint = governingPoint
        )
    }

    // ------------------------------------------------------------- materiaux

    private fun parseMaterials(
        tables: List<TabularTable>,
        units: UnitSystem,
        warnings: MutableList<String>
    ): List<RawMaterial> {
        val table = findTable(tables, "Material Propert")
            ?: tables.firstOrNull { normalize(it.name) == "MATERIALS" }
            ?: return emptyList()

        val stressFactor = tableStressFactor(table, units)
        val materials = mutableListOf<RawMaterial>()
        table.forEachRow { row ->
            val name = text(row, "Name", "Material", "MaterialName", "Material Name")
                ?: return@forEachRow
            val type = text(row, "Type", "MaterialType", "Material Type", "DesignType").orEmpty()
            val resolvedType = type.ifBlank { "CONCRETE" }
            val fc = number(
                row,
                "Fc", "Fcu", "fc28", "CompressiveStrength", "Compressive Strength",
                "ConcreteStrength", "f'c"
            )?.times(stressFactor)
            val fy = number(row, "Fy", "fy", "YieldStrength", "Yield Strength", "SteelYield")
                ?.times(stressFactor)
            val e = number(row, "E", "Modulus", "ElasticModulus", "Elastic Modulus", "E1")
                ?.times(stressFactor)
            val fcFromName = Regex("C(\\d{2})", RegexOption.IGNORE_CASE)
                .find(name)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
            materials += RawMaterial(
                name = name,
                type = resolvedType,
                fc28MPa = fc ?: fcFromName,
                fyMPa = fy,
                eMPa = e
            )
            if (fc == null && fcFromName != null) {
                warnings += "Matériau '$name' : fc28 déduit du nom (${format(fcFromName, 0)} MPa)."
            }
        }
        return materials
    }

    // ---------------------------------------------------------------- efforts

    private fun parseForces(
        tables: List<TabularTable>,
        families: List<StripFootingFamily>,
        warnings: MutableList<String>
    ): List<StripFootingForceEnvelope> {
        if (families.isEmpty()) return emptyList()
        val table = findTable(tables, "Beam Forces")
            ?: findTable(tables, "Beam Force")
            ?: findTable(tables, "Beam Result")
            ?: tables.firstOrNull {
                val name = normalize(it.name)
                name.contains("BEAM") && (name.contains("FORCE") || name.contains("RESULT"))
            }
            ?: return emptyList()

        val unresolved = mutableSetOf<String>()
        val raw = mutableListOf<RawBeamForce>()

        table.forEachRow { row ->
            val element = text(row, "Beam", "Element", "Frame", "Label", "UniqueName", "Unique Name")
                ?: return@forEachRow
            val section = text(row, "Section", "SectionName", "Section Name", "Property", "Prop")
            val family = resolveFamily(element, section, families)
            if (family == null) {
                unresolved += element
                return@forEachRow
            }
            val station = number(row, "Station", "Dist", "Distance", "Location", "x") ?: 0.0
            val loadCase = text(row, "OutputCase", "Output Case", "LoadCase", "Load Case", "Case", "Combo")
                .orEmpty()
            val axial = number(row, "P", "Axial", "AxialForce", "Axial Force", "N") ?: 0.0
            val shear = number(row, "V2", "V3", "Shear", "Shear2", "Shear3", "V") ?: 0.0
            val moment = number(
                row,
                "M3", "Moment3", "Moment", "M", "M2", "Moment2"
            ) ?: 0.0
            val length = number(
                row,
                "Length", "Longueur", "ElementLength", "Element Length", "L"
            )
            raw += RawBeamForce(
                familyName = family.name,
                element = element,
                stationMeters = abs(station),
                lengthMeters = length?.takeIf { it > 0.0 },
                loadCase = loadCase,
                axialKN = axial,
                shearKN = shear,
                momentKNm = moment
            )
        }

        if (unresolved.isNotEmpty()) {
            warnings += "Efforts non rattachés à une famille de semelle pour : " +
                unresolved.take(12).joinToString(", ") +
                if (unresolved.size > 12) " (${unresolved.size} éléments)" else ""
        }
        if (raw.isEmpty()) return emptyList()

        val rawById = raw.groupBy { it.familyName }.mapValues { (_, rows) ->
            rows.map { it.element }.distinct()
        }

        val combinations = raw.groupBy { Triple(it.familyName, ComboCategory.classify(it.loadCase), it.loadCase) }
            .map { (key, rows) -> aggregateCombination(key.first, key.second, key.third, rows, families) }

        return combinations.groupBy { it.familyName }.map { (familyName, combos) ->
            val byCategory = combos.groupBy { it.category }
            val ultimate = aggregateCategory(familyName, ComboCategory.ELU, byCategory[ComboCategory.ELU].orEmpty())
            val service = aggregateCategory(familyName, ComboCategory.ELS, byCategory[ComboCategory.ELS].orEmpty())
            val accidental = aggregateCategory(familyName, ComboCategory.ELA, byCategory[ComboCategory.ELA].orEmpty())
            val designCandidates = listOfNotNull(ultimate, accidental)
            val governing = designCandidates.maxByOrNull {
                abs(it.supportMomentKNm) + abs(it.spanMomentKNm) + abs(it.shearKN)
            }
            val notes = mutableListOf<String>()
            if (ultimate == null && accidental == null) {
                notes += "Aucune combinaison ELU ou ELA : la semelle ne peut être dimensionnée qu'aux ELS."
            }
            if (service == null) {
                notes += "Aucune combinaison ELS : la portance du sol sera vérifiée avec N_ser = N_u / 1,35."
            }
            if (ultimate != null && accidental != null) {
                notes += "Enveloppe ELU/ELA retenue pour le ferraillage."
            }
            val designSupport = max(
                abs(ultimate?.supportMomentKNm ?: 0.0),
                abs(accidental?.supportMomentKNm ?: 0.0)
            )
            val designSpan = max(
                abs(ultimate?.spanMomentKNm ?: 0.0),
                abs(accidental?.spanMomentKNm ?: 0.0)
            )
            val designShear = max(
                abs(ultimate?.shearKN ?: 0.0),
                abs(accidental?.shearKN ?: 0.0)
            )
            val designAxial = governing?.axialKN ?: 0.0
            val serviceAxial = service?.let { abs(it.axialCompressionKN - it.axialTensionKN) }
                ?: (abs(designAxial) / 1.35)

            StripFootingForceEnvelope(
                familyName = familyName,
                ultimate = ultimate,
                service = service,
                accidental = accidental,
                designSupportMomentKNm = designSupport,
                designSpanMomentKNm = designSpan,
                designShearKN = designShear,
                designAxialKN = designAxial,
                serviceAxialKN = serviceAxial,
                governingCategory = governing?.category,
                elementIds = rawById[familyName].orEmpty(),
                notes = notes
            )
        }.sortedBy { it.familyName }
    }

    private fun aggregateCombination(
        familyName: String,
        category: ComboCategory,
        combo: String,
        rows: List<RawBeamForce>,
        families: List<StripFootingFamily>
    ): StripFootingCombinationForces {
        val family = families.first { it.name == familyName }
        val observedLength = rows.maxOf { it.stationMeters }.takeIf { it > 0.0 }
        val length = rows.mapNotNull { it.lengthMeters }.maxOrNull()
            ?: observedLength
            ?: family.footingLengthMeters
            ?: 1.0
        val supports = rows.filter {
            it.stationMeters <= 0.05 * length || it.stationMeters >= 0.95 * length
        }
        val span = rows.filter { it.stationMeters in 0.15 * length..0.85 * length }
        val supportRows = supports.ifEmpty { rows }
        val spanRows = span.ifEmpty { rows }
        val support = supportRows.maxBy { abs(it.momentKNm) }
        val spanMoment = spanRows.maxBy { abs(it.momentKNm) }
        val shear = rows.maxBy { abs(it.shearKN) }
        val maxP = rows.maxOf { it.axialKN }
        val minP = rows.minOf { it.axialKN }
        val axial = if (abs(minP) > abs(maxP)) minP else maxP
        return StripFootingCombinationForces(
            familyName = familyName,
            category = category,
            combo = combo,
            axialKN = axial,
            axialCompressionKN = maxP.coerceAtLeast(0.0),
            axialTensionKN = (-minP).coerceAtLeast(0.0),
            supportMomentKNm = support.momentKNm,
            spanMomentKNm = spanMoment.momentKNm,
            shearKN = abs(shear.shearKN),
            stationLengthMeters = length,
            elementIds = rows.map { it.element }.distinct()
        )
    }

    private fun aggregateCategory(
        familyName: String,
        category: ComboCategory,
        combos: List<StripFootingCombinationForces>
    ): StripFootingCombinationForces? {
        if (combos.isEmpty()) return null
        val support = combos.maxBy { abs(it.supportMomentKNm) }
        val span = combos.maxBy { abs(it.spanMomentKNm) }
        val shear = combos.maxBy { it.shearKN }
        val axial = combos.maxBy { abs(it.axialKN) }
        return StripFootingCombinationForces(
            familyName = familyName,
            category = category,
            combo = "appui ${support.combo} / travée ${span.combo}",
            axialKN = axial.axialKN,
            axialCompressionKN = axial.axialCompressionKN,
            axialTensionKN = axial.axialTensionKN,
            supportMomentKNm = support.supportMomentKNm,
            spanMomentKNm = span.spanMomentKNm,
            shearKN = shear.shearKN,
            stationLengthMeters = combos.maxOf { it.stationLengthMeters },
            elementIds = combos.flatMap { it.elementIds }.distinct()
        )
    }

    private fun resolveFamily(
        element: String,
        section: String?,
        families: List<StripFootingFamily>
    ): StripFootingFamily? {
        val candidates = listOfNotNull(element, section)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (candidate in candidates) {
            families.firstOrNull { it.name.equals(candidate, ignoreCase = true) }?.let { return it }
            families.firstOrNull { family ->
                family.elementIds.any { it.equals(candidate, ignoreCase = true) }
            }?.let { return it }
            families.firstOrNull { candidate.contains(it.name, ignoreCase = true) }?.let { return it }
            val candidateDigits = candidate.filter { it.isDigit() }
            if (candidateDigits.isNotEmpty()) {
                families.firstOrNull { family ->
                    family.name.filter { it.isDigit() } == candidateDigits
                }?.let { return it }
            }
        }
        return null
    }

    private data class RawBeamForce(
        val familyName: String,
        val element: String,
        val stationMeters: Double,
        val lengthMeters: Double?,
        val loadCase: String,
        val axialKN: Double,
        val shearKN: Double,
        val momentKNm: Double
    )

    // ------------------------------------------------------------- utilitaires

    private fun findTable(
        tables: List<TabularTable>,
        nameFragment: String,
        exclude: List<String> = emptyList()
    ): TabularTable? {
        val target = normalize(nameFragment)
        val excluded = exclude.map { normalize(it) }
        return tables.firstOrNull { table ->
            val name = normalize(table.name)
            name.contains(target) && excluded.none { name.contains(it) }
        }
    }

    private fun tableLengthFactor(table: TabularTable, units: UnitSystem): Double {
        for (header in table.headers) {
            val lower = header.lowercase()
            when {
                lower.contains("(mm)") || lower.contains("[mm]") -> return 0.001
                lower.contains("(cm)") || lower.contains("[cm]") -> return 0.01
                lower.contains("(ft)") || lower.contains("[ft]") -> return 0.3048
                lower.contains("(in)") || lower.contains("[in]") -> return 0.0254
                lower.contains("(m)") || lower.contains("[m]") -> return 1.0
            }
        }
        return units.lengthFactorToMeters
    }

    private fun tableStressFactor(table: TabularTable, units: UnitSystem): Double {
        for (header in table.headers) {
            val lower = header.lowercase()
            when {
                lower.contains("(mpa)") || lower.contains("[mpa]") -> return 1.0
                lower.contains("(ksi)") || lower.contains("[ksi]") -> return 6.894757
                lower.contains("(psi)") || lower.contains("[psi]") -> return 0.00689476
                lower.contains("kgf/cm") -> return 0.0980665
                lower.contains("(kn/m2)") || lower.contains("kn/m²") -> return 0.001
            }
        }
        return when (units.code) {
            "KN-M", "KN-MM", "N-MM" -> 1.0
            else -> units.stressFactorToMPa
        }
    }

    private fun text(row: TabularRow, vararg aliases: String): String? {
        for (alias in aliases) {
            val raw = row.raw(alias)?.trim()
            if (!raw.isNullOrBlank() && !raw.equals("N/A", ignoreCase = true)) return raw
        }
        val longAliases = aliases.filter { it.length >= 3 }.toTypedArray()
        return if (longAliases.isEmpty()) null else row.value(*longAliases)
    }

    private fun number(row: TabularRow, vararg aliases: String): Double? {
        for (alias in aliases) {
            parseNumber(row.raw(alias))?.let { return it }
        }
        val longAliases = aliases.filter { it.length >= 3 }.toTypedArray()
        if (longAliases.isEmpty()) return null
        return longAliases.firstNotNullOfOrNull { alias -> parseNumber(row.value(alias)) }
    }

    private fun parseNumber(text: String?): Double? {
        val cleaned = text?.trim()?.replace(" ", "")?.replace(',', '.') ?: return null
        cleaned.toDoubleOrNull()?.let { return it }
        return Regex("-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")
            .find(cleaned)
            ?.value
            ?.toDoubleOrNull()
    }

    private fun normalize(value: String): String =
        value.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private fun format(value: Double, decimals: Int): String =
        String.format(java.util.Locale.ROOT, "%.${decimals}f", value)

    companion object {
        /** Detection du systeme d'unites dans les tables SAFE, KN-M par defaut. */
        fun detectUnits(tables: List<TabularTable>): UnitSystem {
            val unitTables = tables.filter { table ->
                val name = table.name.uppercase().replace(Regex("[^A-Z0-9]"), "")
                name.contains("UNIT") || name.contains("PROGRAMCONTROL") || name.contains("MODELSETTING")
            }.ifEmpty { tables }

            val token = Regex(
                "(?i)^(KN|KGF|TONF?|N|KIP|LB)[\\s,;/\\-]*(M|MM|CM|FT|IN)$"
            )
            for (table in unitTables) {
                for (header in table.headers) {
                    token.find(header.trim())?.let { return UnitSystem.fromEtabsUnits(it.value) }
                }
                for (row in table.rows) {
                    for (cell in row) {
                        token.find(cell.trim())?.let { return UnitSystem.fromEtabsUnits(it.value) }
                    }
                }
            }
            return UnitSystem.KN_M
        }
    }
}
