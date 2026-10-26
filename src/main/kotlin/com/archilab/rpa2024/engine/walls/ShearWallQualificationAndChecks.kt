package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.model.WallSlenderness
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.PierForceRow
import kotlin.math.abs
import kotlin.math.ceil

/** Origine des efforts de calcul retenus pour un voile. */
enum class WallForceSource(val label: String) {
    SEISMIC("Combinaisons sismiques ELA"),
    ALL_COMBINATIONS("Toutes combinaisons (aucune ELA disponible)"),
    NONE("Aucun effort disponible")
}

/**
 * Enveloppes d'efforts a la base d'un voile issues de la table ETABS 'Pier Forces'.
 * Une convention positive est retenue : N en compression, V et M en valeur absolue.
 */
data class WallDesignForces(
    val pier: String,
    val axialCompressionKN: Double,
    val axialCombo: String,
    val shearKN: Double,
    val shearCombo: String,
    val momentKNm: Double,
    val momentCombo: String,
    val bottomRows: List<PierForceRow> = emptyList(),
    val source: WallForceSource = WallForceSource.NONE
) {
    val hasForces: Boolean get() = source != WallForceSource.NONE
    val hasSeismicForces: Boolean get() = source == WallForceSource.SEISMIC
}

/**
 * Extraction dynamique des efforts de base d'un voile. Aucun nom de voile ni aucune
 * cote n'est code en dur : la recherche se fait sur le Pier ETABS puis, en repli, sur
 * les identifiants d'elements issus de la topologie.
 */
object WallForceExtractor {

    fun rowsFor(wall: ShearWallFamily, rows: List<PierForceRow>): List<PierForceRow> {
        val direct = rows.filter { it.pier.trim().equals(wall.pierName.trim(), ignoreCase = true) }
        if (direct.isNotEmpty()) return direct
        val byIds = wall.elementIds.toSet()
        return rows.filter { it.pier.trim() in byIds || it.pier in wall.elementIds }
    }

    fun extract(wall: ShearWallFamily, rows: List<PierForceRow>): WallDesignForces {
        val pierRows = rowsFor(wall, rows)
        val empty = WallDesignForces(
            pier = wall.pierName,
            axialCompressionKN = 0.0,
            axialCombo = "-",
            shearKN = 0.0,
            shearCombo = "-",
            momentKNm = 0.0,
            momentCombo = "-",
            source = WallForceSource.NONE
        )
        if (pierRows.isEmpty()) return empty

        val bottom = bottomRows(pierRows)
        if (bottom.isEmpty()) return empty

        val seismic = bottom.filter { it.category == ComboCategory.ELA }
        val pool = seismic.ifEmpty { bottom }
        val source = if (seismic.isNotEmpty()) WallForceSource.SEISMIC else WallForceSource.ALL_COMBINATIONS

        val axialRow = pool.minByOrNull { it.pKN } ?: pool.first()
        val shearRow = pool.maxByOrNull { abs(it.v2KN) } ?: pool.first()
        val momentRow = pool.maxByOrNull { abs(it.m3KNm) } ?: pool.first()

        // Certains modeles orientent le voile selon l'axe 3 : on bascule alors sur V3 / M2.
        val shear = if (pool.any { abs(it.v2KN) > 1e-9 }) {
            abs(shearRow.v2KN)
        } else {
            abs(pool.maxByOrNull { abs(it.v3KN) }?.v3KN ?: 0.0)
        }
        val moment = if (pool.any { abs(it.m3KNm) > 1e-9 }) {
            abs(momentRow.m3KNm)
        } else {
            abs(pool.maxByOrNull { abs(it.m2KNm) }?.m2KNm ?: 0.0)
        }

        return WallDesignForces(
            pier = wall.pierName,
            axialCompressionKN = (-axialRow.pKN).coerceAtLeast(0.0),
            axialCombo = axialRow.loadCase,
            shearKN = shear,
            shearCombo = shearRow.loadCase,
            momentKNm = moment,
            momentCombo = momentRow.loadCase,
            bottomRows = bottom,
            source = source
        )
    }

    fun bottomRows(rows: List<PierForceRow>): List<PierForceRow> {
        val explicit = rows.filter { it.location.trim().equals("Bottom", ignoreCase = true) }
        if (explicit.isNotEmpty()) return explicit
        val minStation = rows.minOfOrNull { it.stationMeters } ?: return rows
        return rows.filter { abs(it.stationMeters - minStation) <= 1e-6 }
    }
}

/** Ligne du tableau de qualification geometrique § 7.7.1. */
data class WallQualificationRow(
    val pier: String,
    val heMeters: Double,
    val bwMeters: Double,
    val heOver3Meters: Double,
    val fourBwMeters: Double,
    val oneMeter: Double,
    val lwMeters: Double,
    val classification: WallSlenderness
)

/** Synthese des trois verifications preliminaires d'un voile (§ 7.7.1 a § 7.7.3). */
data class WallQualificationResult(
    val family: ShearWallFamily,
    val forces: WallDesignForces,
    val requiredLengthMeters: Double,
    val requiredThicknessMeters: Double,
    val normalizedAxial: Double,
    val concreteAreaM2: Double,
    val shearDesignKN: Double,
    val effectiveDepthMeters: Double,
    val tauMPa: Double,
    val tauLimitMPa: Double,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })
    val classification: WallSlenderness get() = family.classification
    val isGeometryQualified: Boolean
        get() = family.lengthLwMeters + 1e-9 >= requiredLengthMeters &&
            family.thicknessBwMeters + 1e-9 >= requiredThicknessMeters

    val row: WallQualificationRow
        get() = WallQualificationRow(
            pier = family.pierName,
            heMeters = family.clearHeightHeMeters,
            bwMeters = family.thicknessBwMeters,
            heOver3Meters = family.clearHeightHeMeters / 3.0,
            fourBwMeters = 4.0 * family.thicknessBwMeters,
            oneMeter = 1.0,
            lwMeters = family.lengthLwMeters,
            classification = family.classification
        )

    fun toElementReport(): ElementCheckReport = ElementCheckReport(
        elementType = "Voile",
        name = family.pierName,
        geometry = "${family.geometryLabel} ; he = ${Checks.format(family.clearHeightHeMeters, 2)} m ; " +
            "Hw = ${Checks.format(family.totalHeightHwMeters, 2)} m",
        checks = checks
    )
}

/** Synthese du chapitre 10.3 (verifications preliminaires des voiles). */
data class WallQualificationSet(
    val walls: List<WallQualificationResult>,
    val rows: List<WallQualificationRow>,
    val chapter: ChapterReport
) {
    val status: CheckStatus get() = CheckStatus.worst(walls.map { it.status })
}

/**
 * Verifications RPA 2024 § 7.7.1 a § 7.7.3 de tous les voiles extraits d'ETABS :
 * - Eq. (7.9) : lw >= max(he/3 ; 4 bw ; 1,00 m) ;
 * - Eq. (7.10) : bw >= max(0,15 m ; he/20) ;
 * - Eq. (7.11) : nu_d = Nd/(Bc fc28) <= 0,40 ;
 * - Eq. (7.12) et (7.13) : tau_b = 1,40 Vu/(bw . 0,90 lw) <= 0,20 fc28.
 */
class ShearWallQualificationAndChecks {

    fun assess(joint: StructuralJoint, results: EtabsResults): WallQualificationSet =
        assess(
            walls = joint.sectionCatalog.shearWalls,
            results = results,
            materials = joint.materials,
            storyCount = joint.storySystem.numStoriesN
        )

    fun assess(
        walls: List<ShearWallFamily>,
        results: EtabsResults,
        materials: MaterialConfig,
        storyCount: Int? = null
    ): WallQualificationSet {
        val qualified = walls.map { qualify(it, WallForceExtractor.extract(it, results.pierForces), materials) }
        val rows = qualified.map { it.row }
        val chapterChecks = mutableListOf<DesignCheck>()

        chapterChecks += Checks.boolean(
            code = "PSW10.0_FAMILLES",
            label = "Voiles de contreventement traites",
            reference = "RPA 2024 § 7.7",
            condition = walls.isNotEmpty(),
            required = "Au moins un voile",
            provided = "${walls.size} voile(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucun voile extrait d'ETABS : verifier les affectations de sections coque."
        )
        chapterChecks += Checks.boolean(
            code = "PSW10.0_EFFORTS",
            label = "Efforts de voiles importes",
            reference = "ETABS - Pier Forces",
            condition = qualified.any { it.forces.hasForces },
            required = "Au moins une ligne a la base",
            provided = "${qualified.count { it.forces.hasForces }} voile(s) avec efforts",
            failureStatus = CheckStatus.WARNING,
            message = "Table 'Pier Forces' absente ou vide : seules les verifications geometriques sont exploitables."
        )
        val reclass = qualified.count { it.family.lengthLwMeters + 1e-9 < it.requiredLengthMeters }
        chapterChecks += Checks.boolean(
            code = "PSW10.0_RECLASSEMENT",
            label = "Voiles satisfaisant l'elongation minimale",
            reference = "RPA 2024 § 7.7.1 Eq. (7.9)",
            condition = reclass == 0,
            required = "Aucun element a reclasser",
            provided = "$reclass voile(s) a reclasser en poteau",
            failureStatus = CheckStatus.WARNING,
            message = "Un ou plusieurs elements ne satisfont pas lw >= max(he/3 ; 4 bw ; 1 m) : " +
                "les traiter comme des poteaux dans le chapitre 10.1."
        )
        chapterChecks += Checks.boolean(
            code = "PSW10.0_CONFORMITE",
            label = "Conformite des verifications preliminaires des voiles",
            reference = "RPA 2024 § 7.7.1 a § 7.7.3",
            condition = qualified.none { it.status == CheckStatus.FAILED },
            required = "Aucun voile en defaut",
            provided = "${qualified.count { it.status == CheckStatus.FAILED }} voile(s) en defaut",
            failureStatus = CheckStatus.FAILED,
            message = "Au moins un voile ne satisfait pas les conditions de coffrage, d'effort normal ou de cisaillement."
        )

        val lines = mutableListOf<String>()
        if (storyCount != null) {
            lines += "Nombre de niveaux retenu pour le plafond h_cr (Eq. 7.23) : N = $storyCount."
        }
        qualified.forEach { wall ->
            lines += buildList {
                add(
                    "Voile ${wall.family.pierName} : he = ${Checks.format(wall.family.clearHeightHeMeters, 2)} m ; " +
                        "bw = ${Checks.format(wall.family.thicknessBwMeters, 2)} m ; he/3 = " +
                        "${Checks.format(wall.family.clearHeightHeMeters / 3.0, 2)} m ; 4 bw = " +
                        "${Checks.format(4.0 * wall.family.thicknessBwMeters, 2)} m ; 1,00 m ; lw adopte = " +
                        "${Checks.format(wall.family.lengthLwMeters, 2)} m ; classification = " +
                        "${wall.classification.labelFr()}."
                )
                if (wall.forces.hasForces) {
                    add(
                        "  Efforts de base (${wall.forces.source.label}) : Nd = " +
                            "${Checks.format(wall.forces.axialCompressionKN)} kN sous ${wall.forces.axialCombo} ; " +
                            "Vu = ${Checks.format(wall.forces.shearKN)} kN sous ${wall.forces.shearCombo} ; " +
                            "Mu = ${Checks.format(wall.forces.momentKNm)} kN.m sous ${wall.forces.momentCombo}."
                    )
                    add(
                        "  Verifications : nu_d = ${Checks.format(wall.normalizedAxial, 3)} <= 0,40 ; " +
                            "tau_b = ${Checks.format(wall.tauMPa, 3)} MPa <= 0,20 fc28 = " +
                            "${Checks.format(wall.tauLimitMPa, 3)} MPa."
                    )
                } else {
                    add("  Aucun effort de base exploitable dans 'Pier Forces'.")
                }
            }
        }

        val summary = "${qualified.size} voile(s) qualifie(s) : " +
            "${qualified.count { it.status == CheckStatus.OK }} conforme(s), " +
            "${qualified.count { it.status == CheckStatus.WARNING }} a verifier, " +
            "${qualified.count { it.status == CheckStatus.FAILED }} non conforme(s)."

        return WallQualificationSet(
            walls = qualified,
            rows = rows,
            chapter = ChapterReport(
                number = 10,
                title = "10.3 Calcul des voiles de contreventement - verifications preliminaires",
                summary = summary,
                checks = chapterChecks,
                elements = qualified.map { it.toElementReport() },
                lines = lines
            )
        )
    }

    private fun qualify(
        wall: ShearWallFamily,
        forces: WallDesignForces,
        materials: MaterialConfig
    ): WallQualificationResult {
        val checks = mutableListOf<DesignCheck>()
        val notes = mutableListOf<String>()
        val he = wall.clearHeightHeMeters
        val bw = wall.thicknessBwMeters
        val lw = wall.lengthLwMeters
        val requiredLength = wall.requiredMinimumLengthMeters
        val requiredThickness = wall.requiredMinimumThicknessMeters

        checks += Checks.min(
            code = "PSW1_LONGUEUR",
            label = "Longueur minimale du voile lw",
            reference = "RPA 2024 § 7.7.1 Eq. (7.9)",
            provided = lw,
            minimum = requiredLength,
            unit = "m",
            decimals = 2,
            failureMessage = "lw = ${Checks.format(lw, 2)} m < max(he/3 ; 4 bw ; 1,00 m) = " +
                "${Checks.format(requiredLength, 2)} m : reclasser l'element en poteau (chapitre 10.1)."
        )
        checks += Checks.min(
            code = "PSW2_EPAISSEUR",
            label = "Epaisseur minimale du voile bw",
            reference = "RPA 2024 § 7.7.1 Eq. (7.10)",
            provided = bw,
            minimum = requiredThickness,
            unit = "m",
            decimals = 3,
            failureMessage = "bw = ${Checks.format(bw * 100.0, 1)} cm < max(15 cm ; he/20) = " +
                "${Checks.format(requiredThickness * 100.0, 1)} cm."
        )
        checks += Checks.info(
            code = "PSW3_CLASSIFICATION",
            label = "Classification de l'elancement mecanique",
            reference = "RPA 2024 § 7.7.4",
            value = "${wall.classification.labelFr()} : hw/lw = " +
                "${Checks.format(wall.slendernessHwOverLw, 2)} (hw = ${Checks.format(wall.totalHeightHwMeters, 2)} m ; " +
                "lw = ${Checks.format(lw, 2)} m)."
        )

        val concreteArea = lw * bw
        val normalizedAxial = if (concreteArea > 0.0 && materials.fc28MPa > 0.0) {
            forces.axialCompressionKN / (concreteArea * materials.fc28MPa * 1000.0)
        } else {
            0.0
        }
        val effectiveDepth = 0.90 * lw
        val shearDesign = 1.40 * forces.shearKN
        val tauMPa = if (bw > 0.0 && effectiveDepth > 0.0) {
            shearDesign / (bw * effectiveDepth) / 1000.0
        } else {
            0.0
        }
        val tauLimit = 0.20 * materials.fc28MPa

        if (forces.hasForces) {
            checks += Checks.max(
                code = "PSW4_NU",
                label = "Effort normal reduit nu_d",
                reference = "RPA 2024 § 7.7.2 Eq. (7.11)",
                provided = normalizedAxial,
                maximum = 0.40,
                unit = "",
                decimals = 3,
                warningAtRatio = 0.90,
                failureMessage = "nu_d = ${Checks.format(normalizedAxial, 3)} > 0,40 : risque de rupture fragile, " +
                    "augmenter la section ou reduire l'effort normal."
            )
            checks += Checks.max(
                code = "PSW5_CISAILLEMENT",
                label = "Contrainte limite de cisaillement tau_b",
                reference = "RPA 2024 § 7.7.3 Eq. (7.12) et (7.13)",
                provided = tauMPa,
                maximum = tauLimit,
                unit = "MPa",
                decimals = 3,
                warningAtRatio = 0.90,
                failureMessage = "tau_b = ${Checks.format(tauMPa, 3)} MPa > 0,20 fc28 = " +
                    "${Checks.format(tauLimit, 3)} MPa : augmenter bw, lw ou reduire Vu."
            )
            checks += Checks.info(
                code = "PSW6_EFFORTS_BASE",
                label = "Efforts de base extraits d'ETABS",
                reference = "ETABS - Pier Forces (${forces.source.label})",
                value = "Nd = ${Checks.format(forces.axialCompressionKN)} kN (${forces.axialCombo}) ; " +
                    "Vu = ${Checks.format(forces.shearKN)} kN (${forces.shearCombo}) ; " +
                    "Mu = ${Checks.format(forces.momentKNm)} kN.m (${forces.momentCombo})."
            )
            checks += Checks.info(
                code = "PSW7_CISAILLEMENT_DETAIL",
                label = "Detail du cisaillement de calcul",
                reference = "RPA 2024 § 7.7.3",
                value = "Vbarre = 1,40 Vu = ${Checks.format(shearDesign)} kN ; d = 0,90 lw = " +
                    "${Checks.format(effectiveDepth, 2)} m ; tau_b = Vbarre/(bw d) = " +
                    "${Checks.format(tauMPa, 3)} MPa."
            )
            if (!forces.hasSeismicForces) {
                notes += "Aucune combinaison sismique ELA identifiee : les enveloppes toutes combinaisons sont utilisees."
            }
            if (forces.axialCompressionKN == 0.0) {
                notes += "Aucun effort de compression detecte a la base (P >= 0 sur toutes les combinaisons retenues)."
            }
        } else {
            checks += Checks.notApplicable(
                code = "PSW4_NU",
                label = "Effort normal reduit nu_d",
                reference = "RPA 2024 § 7.7.2 Eq. (7.11)",
                message = "Aucun effort normal a la base dans la table 'Pier Forces'."
            )
            checks += Checks.notApplicable(
                code = "PSW5_CISAILLEMENT",
                label = "Contrainte limite de cisaillement tau_b",
                reference = "RPA 2024 § 7.7.3 Eq. (7.12) et (7.13)",
                message = "Aucun effort tranchant a la base dans la table 'Pier Forces'."
            )
        }

        if (lw + 1e-9 < requiredLength) {
            notes += "Condition Eq. (7.9) non satisfaite : l'element doit etre calcule comme un poteau."
        }
        if (bw + 1e-9 < requiredThickness) {
            notes += "Condition Eq. (7.10) non satisfaite : epaisseur de voile insuffisante."
        }

        return WallQualificationResult(
            family = wall,
            forces = forces,
            requiredLengthMeters = requiredLength,
            requiredThicknessMeters = requiredThickness,
            normalizedAxial = normalizedAxial,
            concreteAreaM2 = concreteArea,
            shearDesignKN = shearDesign,
            effectiveDepthMeters = effectiveDepth,
            tauMPa = tauMPa,
            tauLimitMPa = tauLimit,
            checks = checks,
            notes = notes
        )
    }
}

internal fun WallSlenderness.labelFr(): String = when (this) {
    WallSlenderness.SLENDER_WALL -> "Voile elance (hw/lw > 2,0)"
    WallSlenderness.SHORT_WALL -> "Voile court (2/3 < hw/lw <= 2,0)"
    WallSlenderness.LARGE_DIMENSION_WALL -> "Voile de grande dimension (hw/lw <= 2/3)"
}

internal fun storyCountFor(wall: ShearWallFamily, storyCount: Int?): Int {
    if (storyCount != null && storyCount > 0) return storyCount
    val fromStories = wall.stories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().size
    if (fromStories > 0) return fromStories
    val ratio = if (wall.clearHeightHeMeters > 0.0) {
        wall.totalHeightHwMeters / wall.clearHeightHeMeters
    } else {
        1.0
    }
    return ceil(ratio).toInt().coerceAtLeast(1)
}
