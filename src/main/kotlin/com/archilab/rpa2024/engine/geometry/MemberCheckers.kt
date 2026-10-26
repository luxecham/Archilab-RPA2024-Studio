package com.archilab.rpa2024.engine.geometry

import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.ColumnFamily
import com.archilab.rpa2024.model.ColumnShape
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.SlabStripTopology
import com.archilab.rpa2024.normative.BracingSystem
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.parser.ColumnForceEnvelope
import kotlin.math.max
import kotlin.math.min

/**
 * Verifications de coffrage et de predimensionnement des poutres (RPA 2024 § 7.5.1, Fig. 7.4).
 */
class BeamChecker {

    fun check(beam: BeamFamily, zone: SeismicZone, bracing: BracingSystem): ElementCheckReport {
        val checks = mutableListOf<DesignCheck>()
        val highZone = zone == SeismicZone.ZONE_IV || zone == SeismicZone.ZONE_V || zone == SeismicZone.ZONE_VI
        val minimumWidth = if (highZone) 25.0 else 20.0
        val minimumHeight = if (bracing.hasWallBracing) 20.0 else 30.0
        val span = beam.maxClearSpanMeters

        checks += Checks.min(
            code = "PB1_LARGEUR",
            label = "Largeur minimale de la poutre",
            reference = "RPA 2024 § 7.5.1",
            provided = beam.bCm,
            minimum = minimumWidth,
            unit = "cm",
            failureMessage = "Largeur b = ${Checks.format(beam.bCm)} cm < ${Checks.format(minimumWidth)} cm requis " +
                "en zone ${zone.code} (RPA 2024 § 7.5.1)."
        )

        checks += Checks.min(
            code = "PB2_HAUTEUR",
            label = "Hauteur minimale de la poutre",
            reference = "RPA 2024 § 7.5.1",
            provided = beam.hCm,
            minimum = minimumHeight,
            unit = "cm",
            failureMessage = if (bracing.hasWallBracing) {
                "Hauteur h = ${Checks.format(beam.hCm)} cm < 20 cm. La réduction à 20 cm n'est admise que pour les " +
                    "ouvrages contreventés par voiles, avec justification."
            } else {
                "Hauteur h = ${Checks.format(beam.hCm)} cm < 30 cm requis (RPA 2024 § 7.5.1)."
            }
        )

        checks += Checks.max(
            code = "PB3_ELANCEMENT",
            label = "Élancement de la poutre h/b",
            reference = "RPA 2024 § 7.5.1",
            provided = beam.aspectRatioHeightOverWidth,
            maximum = 4.0,
            unit = "",
            decimals = 2
        )

        if (span > 0.05) {
            val minHeight = span * 100.0 / 15.0
            val maxHeight = span * 100.0 / 10.0
            checks += Checks.between(
                code = "PB4_RIGIDITE",
                label = "Prédimensionnement h selon la portée libre",
                reference = "Règle de prédimensionnement L/15 ≤ h ≤ L/10",
                provided = beam.hCm,
                minimum = minHeight,
                maximum = maxHeight,
                unit = "cm",
                outOfRangeStatus = CheckStatus.WARNING,
                message = "Hauteur h = ${Checks.format(beam.hCm)} cm en dehors de la plage L/15–L/10 " +
                    "(${Checks.format(minHeight)}–${Checks.format(maxHeight)} cm) pour L = ${Checks.format(span)} m."
            )
        } else {
            checks += Checks.notApplicable(
                "PB4_RIGIDITE",
                "Prédimensionnement h selon la portée",
                "Règle L/15 ≤ h ≤ L/10",
                "Portée libre non identifiable (poutre sur voile ou appuis non poteaux)."
            )
        }

        if (beam.maxSupportWidthCm > 0.0) {
            val maximumWidth = 1.5 * beam.hCm + beam.maxSupportWidthCm
            checks += Checks.max(
                code = "PB5_DEBORDEMENT",
                label = "Débordement de la poutre par rapport à l'appui",
                reference = "RPA 2024 § 7.5.1 (bmax ≤ 1.5h + b1)",
                provided = beam.bCm,
                maximum = maximumWidth,
                unit = "cm",
                failureMessage = "Largeur b = ${Checks.format(beam.bCm)} cm > 1.5h + b1 = ${Checks.format(maximumWidth)} cm."
            )
        } else {
            checks += Checks.notApplicable(
                "PB5_DEBORDEMENT",
                "Débordement par rapport à l'appui",
                "RPA 2024 § 7.5.1",
                "Largeur d'appui b1 non extraite du modèle."
            )
        }

        if (beam.maxAxisEccentricityCm > 0.0) {
            val allowed = max(beam.maxSupportWidthCm / 2.0, beam.supportDepthCm / 2.0)
            checks += Checks.max(
                code = "PB6_EXCENTREMENT",
                label = "Excentrement axe poutre / axe poteau",
                reference = "RPA 2024 Fig. 7.4",
                provided = beam.maxAxisEccentricityCm,
                maximum = allowed,
                unit = "cm",
                failureMessage = "Excentrement de ${Checks.format(beam.maxAxisEccentricityCm)} cm > " +
                    "max(b1/2, h1/2) = ${Checks.format(allowed)} cm."
            )
        }

        if (beam.role == com.archilab.rpa2024.model.BeamRole.SECONDARY_TIE) {
            checks += Checks.info(
                "PB7_CHAINAGE",
                "Rôle détecté : chaînage / poutre secondaire",
                "RPA 2024 § 7.5",
                "Vérifications de coffrage appliquées ; le ferraillage de chaînage reste à justifier."
            )
        }

        return ElementCheckReport(
            elementType = "Poutre",
            name = beam.name,
            geometry = beam.geometryLabel,
            checks = checks
        )
    }
}

/**
 * Verifications de coffrage et d'effort normal reduit des poteaux (RPA 2024 § 7.4.1 et Eq. 7.5).
 */
class ColumnChecker {

    fun check(column: ColumnFamily, zone: SeismicZone, fc28MPa: Double): ElementCheckReport {
        val checks = mutableListOf<DesignCheck>()
        val circular = column.shape == ColumnShape.CIRCULAR

        val minimumDimension = if (circular) {
            when (zone) {
                SeismicZone.ZONE_I, SeismicZone.ZONE_II -> 25.0
                SeismicZone.ZONE_III -> 30.0
                SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 35.0
                SeismicZone.ZONE_0 -> 25.0
            }
        } else {
            if (zone == SeismicZone.ZONE_IV || zone == SeismicZone.ZONE_V || zone == SeismicZone.ZONE_VI) 30.0 else 25.0
        }

        checks += Checks.min(
            code = if (circular) "PP1_DIAMETRE" else "PP1_DIMENSION",
            label = if (circular) "Diamètre minimal du poteau circulaire" else "Dimension minimale de la section",
            reference = "RPA 2024 § 7.4.1",
            provided = column.minDimensionCm,
            minimum = minimumDimension,
            unit = "cm",
            failureMessage = if (circular) {
                "Diamètre D = ${Checks.format(column.effectiveDiameterCm)} cm < ${Checks.format(minimumDimension)} cm " +
                    "requis en zone ${zone.code} pour un poteau circulaire."
            } else {
                "Min(b1, h1) = ${Checks.format(column.minDimensionCm)} cm < ${Checks.format(minimumDimension)} cm " +
                    "requis en zone ${zone.code}."
            }
        )

        if (column.clearHeightLclMeters > 0.0) {
            val slendernessLimit = if (circular) column.clearHeightLclMeters * 100.0 / 15.0 else column.clearHeightLclMeters * 100.0 / 20.0
            checks += Checks.min(
                code = if (circular) "PP2_ELANCEMENT_CIRC" else "PP2_ELANCEMENT",
                label = "Élancement géométrique lcl / dimension",
                reference = if (circular) "RPA 2024 § 7.4.1 (D ≥ lcl/15)" else "RPA 2024 § 7.4.1 (min(b1,h1) ≥ lcl/20)",
                provided = column.minDimensionCm,
                minimum = slendernessLimit,
                unit = "cm",
                failureMessage = "Dimension ${Checks.format(column.minDimensionCm)} cm insuffisante pour " +
                    "lcl = ${Checks.format(column.clearHeightLclMeters)} m (minimum ${Checks.format(slendernessLimit)} cm)."
            )
        } else {
            checks += Checks.notApplicable(
                "PP2_ELANCEMENT",
                "Élancement géométrique lcl / dimension",
                "RPA 2024 § 7.4.1",
                "Hauteur libre lcl non déterminée (poutres connectées non identifiées)."
            )
        }

        if (!circular) {
            checks += Checks.between(
                code = "PP3_RAPPORT",
                label = "Rapport des dimensions 1/4 < b1/h1 < 4",
                reference = "RPA 2024 § 7.4.1",
                provided = column.aspectRatioB1OverH1,
                minimum = 0.25,
                maximum = 4.0,
                unit = "",
                decimals = 2,
                outOfRangeStatus = CheckStatus.FAILED,
                message = "Rapport b1/h1 = ${Checks.format(column.aspectRatioB1OverH1, 2)} hors de l'intervalle ]1/4 ; 4[."
            )
        } else {
            checks += Checks.notApplicable(
                "PP3_RAPPORT",
                "Rapport des dimensions",
                "RPA 2024 § 7.4.1",
                "Poteau circulaire : critère de rapport b1/h1 sans objet."
            )
        }

        if (column.isWallBoundary) {
            checks += Checks.info(
                "PP4_VOILE",
                "Poteau solidaire d'un voile",
                "RPA 2024 § 7.4.3",
                "Poteau frontière de voile : exclu de la vérification ν ≤ 0,35 du poteau isolé ; ferraillage de voile à appliquer."
            )
        }

        return ElementCheckReport(
            elementType = "Poteau",
            name = column.name,
            geometry = column.geometryLabel,
            checks = checks
        )
    }

    /**
     * Verification de l'effort normal reduit ν = Nd / (Bc . fc28) ≤ 0,35 (Eq. 7.5).
     * L'enveloppe est prise sous combinaisons accidentelles (ELA) et, a defaut, toutes combinaisons.
     */
    fun axialCheck(
        column: ColumnFamily,
        fc28MPa: Double,
        envelopes: List<ColumnForceEnvelope>
    ): DesignCheck {
        if (column.isWallBoundary) {
            return Checks.notApplicable(
                "PP5_NU",
                "Effort normal réduit ν ≤ 0,35",
                "RPA 2024 § 7.4.3 Eq. (7.5)",
                "Poteau solidaire d'un voile : hors champ de la vérification ν du poteau isolé."
            )
        }
        val elementIds = column.elementIds.toSet()
        val relevant = envelopes.filter { it.element in elementIds }
        if (relevant.isEmpty()) {
            return Checks.notApplicable(
                "PP5_NU",
                "Effort normal réduit ν ≤ 0,35",
                "RPA 2024 § 7.4.3 Eq. (7.5)",
                "Aucun effort de poteau extrait d'ETABS pour cette famille."
            )
        }
        val governing = relevant.maxBy { it.maxCompressionKN }
        val axialKN = max(0.0, governing.maxCompressionKN)
        // Bc en cm2 vaut 100 fois sa valeur en mm2 : nd (N) = Nd (kN) x 1000.
        val nu = axialKN * 1000.0 / (column.areaCm2 * 100.0 * fc28MPa)
        val status = when {
            nu > 0.35 + 1e-9 -> CheckStatus.FAILED
            nu > 0.30 -> CheckStatus.WARNING
            else -> CheckStatus.OK
        }
        return DesignCheck(
            code = "PP5_NU",
            label = "Effort normal réduit ν ≤ 0,35",
            reference = "RPA 2024 § 7.4.3 Eq. (7.5)",
            status = status,
            required = "≤ 0,35",
            provided = Checks.format(nu, 3),
            message = when (status) {
                CheckStatus.FAILED -> "ν = ${Checks.format(nu, 3)} > 0,35 sous ${governing.maxCompressionCombo} " +
                    "(Nd = ${Checks.format(axialKN)} kN, Bc = ${Checks.format(column.areaCm2)} cm²)."
                CheckStatus.WARNING -> "ν = ${Checks.format(nu, 3)} proche de la limite 0,35 " +
                    "(Nd = ${Checks.format(axialKN)} kN sous ${governing.maxCompressionCombo})."
                else -> ""
            }
        )
    }
}

/**
 * Qualification des voiles de contreventement (RPA 2024 § 7.7.1, Eq. 7.9 et 7.10).
 */
class WallChecker {

    fun check(wall: ShearWallFamily): ElementCheckReport {
        val checks = mutableListOf<DesignCheck>()

        checks += Checks.min(
            code = "PV1_LONGUEUR",
            label = "Longueur minimale du voile lw",
            reference = "RPA 2024 § 7.7.1 Eq. (7.9)",
            provided = wall.lengthLwMeters * 100.0,
            minimum = wall.requiredMinimumLengthMeters * 100.0,
            unit = "cm",
            failureMessage = "lw = ${Checks.format(wall.lengthLwMeters)} m < max(he/3 ; 4bw ; 1 m) = " +
                "${Checks.format(wall.requiredMinimumLengthMeters)} m : l'élément doit être reclassé en poteau."
        )

        checks += Checks.min(
            code = "PV2_EPAISSEUR",
            label = "Épaisseur minimale du voile bw",
            reference = "RPA 2024 § 7.7.1 Eq. (7.10)",
            provided = wall.thicknessBwMeters * 100.0,
            minimum = wall.requiredMinimumThicknessMeters * 100.0,
            unit = "cm",
            failureMessage = "bw = ${Checks.format(wall.thicknessBwMeters * 100.0)} cm < max(15 cm ; he/20) = " +
                "${Checks.format(wall.requiredMinimumThicknessMeters * 100.0)} cm."
        )

        checks += Checks.info(
            code = "PV3_CLASSIFICATION",
            label = "Classification de l'élancement du voile",
            reference = "RPA 2024 § 7.7.4",
            value = when (wall.classification) {
                com.archilab.rpa2024.model.WallSlenderness.SLENDER_WALL ->
                    "Voile élancé (hw/lw = ${Checks.format(wall.slendernessHwOverLw, 2)} > 2,0)"
                com.archilab.rpa2024.model.WallSlenderness.SHORT_WALL ->
                    "Voile court (2/3 < hw/lw = ${Checks.format(wall.slendernessHwOverLw, 2)} ≤ 2,0)"
                com.archilab.rpa2024.model.WallSlenderness.LARGE_DIMENSION_WALL ->
                    "Voile de grande dimension (hw/lw = ${Checks.format(wall.slendernessHwOverLw, 2)} ≤ 2/3)"
            }
        )

        checks += Checks.info(
            "PV4_DIMENSIONS",
            "Dimensions extraites du modèle",
            "ETABS",
            "lw = ${Checks.format(wall.lengthLwMeters)} m ; bw = ${Checks.format(wall.thicknessBwMeters * 100.0)} cm ; " +
                "he = ${Checks.format(wall.clearHeightHeMeters)} m ; hw = ${Checks.format(wall.totalHeightHwMeters)} m."
        )

        return ElementCheckReport(
            elementType = "Voile",
            name = wall.pierName,
            geometry = wall.geometryLabel,
            checks = checks
        )
    }
}

/**
 * Predimensionnement des planchers a corps creux ou pleins (regle L/22,5 du RPA et coffrage).
 */
class SlabChecker {

    fun check(slab: SlabStripTopology): ElementCheckReport {
        val checks = mutableListOf<DesignCheck>()
        val span = slab.maxSpanMeters

        if (span > 0.05) {
            val divisor = if (slab.isSolidSlab) 25.0 else 22.5
            val minimumThickness = span * 100.0 / divisor
            checks += Checks.min(
                code = "PD1_EPAISSEUR",
                label = "Épaisseur minimale du plancher",
                reference = if (slab.isSolidSlab) "Règle L/25 (dalle pleine)" else "Règle L/22,5 (corps creux)",
                provided = slab.totalThicknessHtCm,
                minimum = minimumThickness,
                unit = "cm",
                failureMessage = "ht = ${Checks.format(slab.totalThicknessHtCm)} cm < L/$divisor = " +
                    "${Checks.format(minimumThickness)} cm pour L = ${Checks.format(span)} m."
            )
        } else {
            checks += Checks.notApplicable(
                "PD1_EPAISSEUR",
                "Épaisseur minimale du plancher",
                "Règle L/22,5",
                "Portée de poutrelle non identifiable depuis la trame d'axes."
            )
        }

        if (slab.isSolidSlab) {
            checks += Checks.notApplicable(
                "PD2_COFFRAGE",
                "Coffrage corps creux (h0 + hourdis)",
                "DTR BC 2.2",
                "Dalle pleine : pas de corps creux."
            )
        } else {
            checks += Checks.boolean(
                code = "PD2_COFFRAGE",
                label = "Cohérence ht = h0 + hauteur du corps creux",
                reference = "DTR BC 2.2",
                condition = slab.isThicknessConsistent,
                required = "ht = h0 + hc",
                provided = "ht = ${Checks.format(slab.totalThicknessHtCm)} cm ; h0 + hc = " +
                    "${Checks.format(slab.compressionSlabH0Cm + slab.hollowBlockHeightCm)} cm",
                failureStatus = CheckStatus.FAILED,
                message = "Incohérence d'épaisseur : ${slab.geometryLabel}."
            )
            checks += Checks.min(
                code = "PD3_TABLE",
                label = "Épaisseur de la table de compression h0",
                reference = "DTR BC 2.2",
                provided = slab.compressionSlabH0Cm,
                minimum = 4.0,
                unit = "cm",
                failureMessage = "h0 = ${Checks.format(slab.compressionSlabH0Cm)} cm < 4 cm."
            )
            checks += Checks.min(
                code = "PD4_NERVURE",
                label = "Largeur minimale de nervure b0",
                reference = "DTR BC 2.2",
                provided = slab.ribWidthB0Cm,
                minimum = 10.0,
                unit = "cm",
                failureMessage = "b0 = ${Checks.format(slab.ribWidthB0Cm)} cm < 10 cm."
            )
            checks += Checks.max(
                code = "PD5_ENTRAXE",
                label = "Entraxe maximal des nervures",
                reference = "DTR BC 2.2",
                provided = slab.flangeWidthBCm,
                maximum = 60.0,
                unit = "cm",
                failureMessage = "Entraxe b = ${Checks.format(slab.flangeWidthBCm)} cm > 60 cm."
            )
        }

        if (slab.continuousSpansMeters.isNotEmpty()) {
            checks += Checks.info(
                "PD6_PORTEES",
                "Travées continues détectées",
                "ETABS",
                slab.continuousSpansMeters.joinToString(" ; ") { "${Checks.format(it)} m" }
            )
        }

        return ElementCheckReport(
            elementType = "Plancher",
            name = slab.name,
            geometry = slab.geometryLabel,
            checks = checks
        )
    }
}
