package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.BeamFamily
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.normative.SeismicZone
import com.archilab.rpa2024.parser.BeamEnvelopeBuilder
import com.archilab.rpa2024.parser.BeamForceEnvelope
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Position d'une section critique de poutre. */
enum class BeamSectionPosition(val label: String) {
    SPAN("Travée"),
    SUPPORT("Appui")
}

/** Couple moment / effort tranchant dimensionnant d'une poutre. */
data class BeamCriticalSection(
    val position: BeamSectionPosition,
    val combo: String,
    val category: ComboCategory,
    val momentKNm: Double,
    val shearKN: Double,
    val bael: BaelRectangularResult
) {
    val label: String get() = position.label
}

/**
 * Verification de cisaillement d'une poutre : contrainte tangente, bielles, acier
 * longitudinal tendu et contrainte de compression des bielles.
 */
data class BeamShearVerification(
    val category: ComboCategory,
    val shearKN: Double,
    val widthCm: Double,
    val effectiveDepthCm: Double,
    val strutWidthCm: Double,
    val tauMPa: Double,
    val tauLimitMPa: Double,
    val strutForceKN: Double,
    val strutLimitKN: Double,
    val requiredLongitudinalCm2: Double,
    val providedLongitudinalCm2: Double,
    val sigmaBcMPa: Double,
    val sigmaBcLimitMPa: Double,
    val status: CheckStatus
)

/** Armatures transversales d'une poutre : zone nodale, zone courante et recouvrement. */
data class BeamTransverseDesign(
    val nodalZoneLengthCm: Double,
    val criticalZoneLengthCm: Double,
    val firstStirrupCm: Double,
    val nodalSpacingLimitCm: Double,
    val currentSpacingLimitCm: Double,
    val nodalSpacingCm: Double,
    val currentSpacingCm: Double,
    val stirrupLegs: Int,
    val stirrupDiameterMm: Int,
    val stirrupAreaCm2: Double,
    val requiredStirrupAreaCm2: Double,
    val minimumTransverseRatio: Double,
    val lapLengthCm: Double,
    val shearVerifications: List<BeamShearVerification>
) {
    val stirrupLabel: String get() = "$stirrupLegs brins φ$stirrupDiameterMm"
}

/** Resultat complet du ferraillage d'une famille de poutres. */
data class BeamDesignResult(
    val family: BeamFamily,
    val widthCm: Double,
    val heightCm: Double,
    val effectiveDepthCm: Double,
    val criticalSections: List<BeamCriticalSection>,
    val governingSection: BeamCriticalSection?,
    val requiredBottomAsCm2: Double,
    val requiredTopAsCm2: Double,
    val minimumAsCm2: Double,
    val maximumAsCm2: Double,
    val spanLayout: BeamRebarLayout?,
    val supportLayout: BeamRebarLayout?,
    val bottomAdoptedAsCm2: Double,
    val topAdoptedAsCm2: Double,
    val transverse: BeamTransverseDesign,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })

    fun toElementReport(): ElementCheckReport = ElementCheckReport(
        elementType = "Poutre",
        name = family.name,
        geometry = "${Checks.format(widthCm)}x${Checks.format(heightCm)} cm ; portée max = " +
            "${Checks.format(family.maxClearSpanMeters, 2)} m",
        checks = checks
    )
}

/** Synthese du chapitre 10.2 (poutres). */
data class BeamDesignSet(
    val beams: List<BeamDesignResult>,
    val chapter: ChapterReport
) {
    val status: CheckStatus get() = CheckStatus.worst(beams.map { it.status })
    val failedCount: Int get() = beams.count { it.status == CheckStatus.FAILED }
    val warningCount: Int get() = beams.count { it.status == CheckStatus.WARNING }
}

/**
 * Moteur de ferraillage des poutres (chapitre 10.2) : enveloppes ETABS ELU/ELS/ELA,
 * flexion simple BAEL 91, pourcentage minimal RPA 2024 § 7.5.2.1, repartition en barres
 * filantes et chapeaux, zones nodales et quatre verifications de cisaillement § 7.5.2.2.
 */
class BeamDesignEngine(
    private val solver: BaelRectangularSolver = BaelRectangularSolver(),
    private val optimizer: RebarOptimizer = RebarOptimizer(),
    private val settings: FrameDesignSettings = FrameDesignSettings()
) {

    fun design(joint: StructuralJoint, results: EtabsResults): BeamDesignSet =
        design(
            beams = joint.sectionCatalog.beams,
            results = results,
            materials = joint.materials,
            zone = SeismicZone.fromCode(joint.seismicParams.zoneCode)
        )

    fun design(
        beams: List<BeamFamily>,
        results: EtabsResults,
        materials: MaterialConfig,
        zone: SeismicZone
    ): BeamDesignSet {
        val designs = beams.map { designBeam(it, results, materials, zone) }
        val checks = mutableListOf<DesignCheck>()
        checks += Checks.boolean(
            code = "PB10.0_FAMILLES",
            label = "Familles de poutres traitées",
            reference = "RPA 2024 § 7.5.2",
            condition = beams.isNotEmpty(),
            required = "Au moins une famille",
            provided = "${beams.size} famille(s)",
            failureStatus = CheckStatus.FAILED,
            message = "Aucune famille de poutres à ferrailler : vérifier les affectations ETABS."
        )
        checks += Checks.boolean(
            code = "PB10.0_EFFORTS",
            label = "Efforts de poutres importés",
            reference = "ETABS - Beam Forces",
            condition = designs.any { it.criticalSections.isNotEmpty() },
            required = "Au moins une section critique",
            provided = "${designs.sumOf { it.criticalSections.size }} section(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucune table Beam Forces exploitable : le ferraillage minimal RPA est appliqué."
        )
        checks += Checks.boolean(
            code = "PB10.0_CONFORMITE",
            label = "Conformité du ferraillage des poutres",
            reference = "BAEL 91 + RPA 2024 § 7.5",
            condition = designs.none { it.status == CheckStatus.FAILED },
            required = "Aucune famille en défaut",
            provided = "${designs.count { it.status == CheckStatus.FAILED }} famille(s) en défaut",
            failureStatus = CheckStatus.FAILED,
            message = "Au moins une famille de poutres ne satisfait pas les vérifications de ferraillage."
        )

        val summary = "${designs.size} famille(s) de poutres ferraillée(s) : " +
            "${designs.count { it.status == CheckStatus.OK }} conforme(s), " +
            "${designs.count { it.status == CheckStatus.WARNING }} à vérifier, " +
            "${designs.count { it.status == CheckStatus.FAILED }} non conforme(s)."
        val lines = designs.flatMap { design ->
            buildList {
                add(
                    "Poutre ${design.family.name} (${Checks.format(design.widthCm)}x" +
                        "${Checks.format(design.heightCm)} cm) : As,min RPA = " +
                        "${Checks.format(design.minimumAsCm2)} cm², As,inf = " +
                        "${Checks.format(design.bottomAdoptedAsCm2)} cm² " +
                        "(${design.spanLayout?.label ?: "non disponible"}), As,sup,appui = " +
                        "${Checks.format(design.topAdoptedAsCm2)} cm² " +
                        "(${design.supportLayout?.label ?: "non disponible"})."
                )
                design.criticalSections.maxByOrNull { it.bael.requiredSteelCm2 }?.let { section ->
                    add(
                        "  Section dimensionnante : ${section.label} sous ${section.combo} " +
                            "(${section.category.name}), M = ${Checks.format(section.momentKNm)} kN.m, " +
                            "état = ${section.bael.state.label}, y0 = " +
                            "${Checks.format(section.bael.neutralAxisMeters * 100.0)} cm."
                    )
                }
                val transverse = design.transverse
                add(
                    "  Zone nodale l' = ${Checks.format(transverse.nodalZoneLengthCm)} cm ; premier cadre à " +
                        "${Checks.format(transverse.firstStirrupCm)} cm ; cadres ${transverse.stirrupLabel} " +
                        "tous les ${Checks.format(transverse.nodalSpacingCm)} cm (zone nodale) et " +
                        "${Checks.format(transverse.currentSpacingCm)} cm (zone courante) ; " +
                        "recouvrement Lrec = ${Checks.format(transverse.lapLengthCm)} cm."
                )
                transverse.shearVerifications.forEach { shear ->
                    add(
                        "  Cisaillement ${shear.category.name} : Vu = ${Checks.format(shear.shearKN)} kN, " +
                            "tau_u = ${Checks.format(shear.tauMPa, 2)} MPa ≤ " +
                            "${Checks.format(shear.tauLimitMPa, 2)} MPa (${shear.status.label})."
                    )
                }
            }
        }

        return BeamDesignSet(
            beams = designs,
            chapter = ChapterReport(
                number = 10,
                title = "10.2 Ferraillage des poutres (flexion simple)",
                summary = summary,
                checks = checks,
                elements = designs.map { it.toElementReport() },
                lines = lines
            )
        )
    }

    private fun designBeam(
        family: BeamFamily,
        results: EtabsResults,
        materials: MaterialConfig,
        zone: SeismicZone
    ): BeamDesignResult {
        val notes = mutableListOf<String>()
        val widthCm = family.bCm.coerceAtLeast(1.0)
        val heightCm = family.hCm.coerceAtLeast(1.0)
        val effectiveDepthCm = (heightCm - settings.steelCoverCm).coerceAtLeast(1.0)

        val ids = family.elementIds.toSet()
        val rows = results.beamForces.filter { it.element in ids }
        val envelopes = BeamEnvelopeBuilder.build(rows)
        val sections = extractCriticalSections(envelopes, widthCm, heightCm, materials)

        val spanCalc = sections
            .filter { it.position == BeamSectionPosition.SPAN }
            .maxOfOrNull { it.bael.tensionSteelCm2 + it.bael.compressionSteelCm2 } ?: 0.0
        val supportCalc = sections
            .filter { it.position == BeamSectionPosition.SUPPORT }
            .maxOfOrNull { it.bael.tensionSteelCm2 + it.bael.compressionSteelCm2 } ?: 0.0
        val baaelMinimum = sections.maxOfOrNull { it.bael.minimumSteelCm2 } ?: 0.0

        val rpaMinimum = settings.beamMinSteelRatio * widthCm * heightCm
        val minimumAs = max(rpaMinimum, baaelMinimum)
        val maximumAs = settings.beamMaxSteelRatio * widthCm * heightCm
        val maximumLapAs = 0.06 * widthCm * heightCm
        val minimumBarDiameter = if (isHighZone(zone)) 14 else settings.longitudinalMinDiameterMm

        val requiredTop = max(supportCalc, minimumAs)
        var supportLayout = optimizer.selectBeamLayout(
            bCm = widthCm,
            hCm = heightCm,
            steelCoverCm = settings.steelCoverCm,
            requiredAsCm2 = requiredTop,
            minimumContinuousAsCm2 = 0.25 * requiredTop,
            additionalRole = "chapeaux",
            maximumAsCm2 = maximumAs,
            minimumDiameterMm = minimumBarDiameter
        )
        var iterations = 0
        while (iterations < 6) {
            val current = supportLayout ?: break
            val requiredContinuous = 0.25 * current.areaCm2
            if (current.continuousAreaCm2 + 1e-9 >= requiredContinuous) break
            supportLayout = optimizer.selectBeamLayout(
                bCm = widthCm,
                hCm = heightCm,
                steelCoverCm = settings.steelCoverCm,
                requiredAsCm2 = requiredTop,
                minimumContinuousAsCm2 = requiredContinuous,
                additionalRole = "chapeaux",
                maximumAsCm2 = maximumAs,
                minimumDiameterMm = minimumBarDiameter
            )
            iterations++
        }
        val topAdopted = supportLayout?.areaCm2 ?: 0.0

        val requiredBottom = maxOf(spanCalc, 0.5 * topAdopted, minimumAs)
        val spanLayout = optimizer.selectBeamLayout(
            bCm = widthCm,
            hCm = heightCm,
            steelCoverCm = settings.steelCoverCm,
            requiredAsCm2 = requiredBottom,
            minimumContinuousAsCm2 = 0.0,
            additionalRole = "renforts",
            maximumAsCm2 = maximumAs,
            minimumDiameterMm = minimumBarDiameter
        )
        val bottomAdopted = spanLayout?.areaCm2 ?: 0.0

        if (rows.isEmpty()) {
            notes += "Aucun effort ETABS pour la famille : ferraillage minimal RPA appliqué."
        }
        if (supportLayout == null || spanLayout == null) {
            notes += "Aucune disposition de barres n'a pu être générée pour la section."
        }
        if (bottomAdopted > maximumAs + 1e-6 || topAdopted > maximumAs + 1e-6) {
            notes += "Le ferraillage adopté dépasse ${Checks.format(settings.beamMaxSteelRatio * 100.0)} % " +
                "de la section (${Checks.format(maximumAs)} cm²) : vérifier les efforts ou la géométrie."
        }

        val transverse = designTransverse(
            family = family,
            envelopes = envelopes,
            spanLayout = spanLayout,
            supportLayout = supportLayout,
            bottomAdopted = bottomAdopted,
            compressionSteelNeeded = sections.any { it.bael.compressionSteelCm2 > 1e-6 },
            materials = materials,
            zone = zone
        )

        val serviceability = designServiceability(
            widthCm = widthCm,
            heightCm = heightCm,
            envelopes = envelopes,
            spanLayout = spanLayout,
            supportLayout = supportLayout,
            materials = materials
        )

        val checks = buildChecks(
            spanLayout = spanLayout,
            supportLayout = supportLayout,
            bottomAdopted = bottomAdopted,
            topAdopted = topAdopted,
            requiredBottom = requiredBottom,
            requiredTop = requiredTop,
            minimum = minimumAs,
            maximum = maximumAs,
            maximumLap = maximumLapAs,
            minimumBarDiameterMm = minimumBarDiameter,
            transverse = transverse,
            serviceability = serviceability,
            hasForces = rows.isNotEmpty()
        )

        return BeamDesignResult(
            family = family,
            widthCm = widthCm,
            heightCm = heightCm,
            effectiveDepthCm = effectiveDepthCm,
            criticalSections = sections,
            governingSection = sections.maxByOrNull { it.bael.requiredSteelCm2 },
            requiredBottomAsCm2 = requiredBottom,
            requiredTopAsCm2 = requiredTop,
            minimumAsCm2 = minimumAs,
            maximumAsCm2 = maximumAs,
            spanLayout = spanLayout,
            supportLayout = supportLayout,
            bottomAdoptedAsCm2 = bottomAdopted,
            topAdoptedAsCm2 = topAdopted,
            transverse = transverse,
            checks = checks,
            notes = notes
        )
    }

    private fun extractCriticalSections(
        envelopes: List<BeamForceEnvelope>,
        widthCm: Double,
        heightCm: Double,
        materials: MaterialConfig
    ): List<BeamCriticalSection> {
        val sections = mutableListOf<BeamCriticalSection>()
        envelopes
            .filter { it.category == ComboCategory.ELU || it.category == ComboCategory.ELA }
            .forEach { envelope ->
                val situation = if (envelope.category == ComboCategory.ELA) {
                    BaelSituation.ACCIDENTAL
                } else {
                    BaelSituation.DURABLE
                }
                if (envelope.maxSpanMomentKNm > 0.0) {
                    sections += BeamCriticalSection(
                        position = BeamSectionPosition.SPAN,
                        combo = envelope.maxSpanMomentCombo,
                        category = envelope.category,
                        momentKNm = envelope.maxSpanMomentKNm,
                        shearKN = envelope.maxShearKN,
                        bael = solver.solve(
                            BaelSectionInput(
                                bCm = widthCm,
                                hCm = heightCm,
                                tensionCoverCm = settings.steelCoverCm,
                                compressionCoverCm = settings.steelCoverCm,
                                nuKN = 0.0,
                                muGKNm = envelope.maxSpanMomentKNm,
                                situation = situation
                            ),
                            materials
                        )
                    )
                }
                if (envelope.maxSupportMomentKNm > 0.0) {
                    sections += BeamCriticalSection(
                        position = BeamSectionPosition.SUPPORT,
                        combo = envelope.maxSupportMomentCombo,
                        category = envelope.category,
                        momentKNm = -envelope.maxSupportMomentKNm,
                        shearKN = envelope.maxShearKN,
                        bael = solver.solve(
                            BaelSectionInput(
                                bCm = widthCm,
                                hCm = heightCm,
                                tensionCoverCm = settings.steelCoverCm,
                                compressionCoverCm = settings.steelCoverCm,
                                nuKN = 0.0,
                                muGKNm = -envelope.maxSupportMomentKNm,
                                situation = situation
                            ),
                            materials
                        )
                    )
                }
            }
        return sections.sortedByDescending { it.bael.requiredSteelCm2 }
    }

    private fun designTransverse(
        family: BeamFamily,
        envelopes: List<BeamForceEnvelope>,
        spanLayout: BeamRebarLayout?,
        supportLayout: BeamRebarLayout?,
        bottomAdopted: Double,
        compressionSteelNeeded: Boolean,
        materials: MaterialConfig,
        zone: SeismicZone
    ): BeamTransverseDesign {
        val widthCm = family.bCm.coerceAtLeast(1.0)
        val heightCm = family.hCm.coerceAtLeast(1.0)
        val minimumBarDiameterCm = listOfNotNull(spanLayout?.minDiameterMm, supportLayout?.minDiameterMm)
            .minOrNull()?.div(10.0) ?: settings.longitudinalMinDiameterMm / 10.0
        val maximumBarDiameterCm = listOfNotNull(spanLayout?.maxDiameterMm, supportLayout?.maxDiameterMm)
            .maxOrNull()?.div(10.0) ?: settings.longitudinalMinDiameterMm / 10.0

        val nodalZoneLength = 2.0 * heightCm
        val criticalZoneLength = 1.5 * heightCm
        val compressionSpacingLimit = minOf(heightCm / 4.0, 12.0 * minimumBarDiameterCm)
        val currentSpacingLimit = if (compressionSteelNeeded) {
            min(compressionSpacingLimit, heightCm / 2.0)
        } else {
            heightCm / 2.0
        }
        val minimumTransverseRatio = 0.003
        val baseNodalLimit = minOf(heightCm / 4.0, 17.5, 6.0 * minimumBarDiameterCm)

        val stirrupOptions = listOf(
            2 to 8, 2 to 10, 4 to 8, 2 to 12, 4 to 10, 4 to 12, 6 to 10, 4 to 14, 6 to 12, 4 to 16
        ).map { (legs, diameter) -> RebarBar(legs, diameter) }
        val chosen = stirrupOptions.firstOrNull { bar ->
            val nodalLimit = min(baseNodalLimit, 24.0 * bar.diameterMm / 10.0)
            val maximumSpacingByForfait = bar.areaCm2 / (minimumTransverseRatio * widthCm)
            val nodal = floor(min(nodalLimit, maximumSpacingByForfait))
            val current = floor(min(currentSpacingLimit, maximumSpacingByForfait))
            nodal >= 5.0 && current >= 5.0
        } ?: RebarBar(4, 12)

        val nodalSpacingLimit = min(baseNodalLimit, 24.0 * chosen.diameterMm / 10.0)
        val maximumSpacingByForfait = chosen.areaCm2 / (minimumTransverseRatio * widthCm)
        val nodalSpacing = floor(min(nodalSpacingLimit, maximumSpacingByForfait)).coerceAtLeast(5.0)
        val currentSpacing = floor(min(currentSpacingLimit, maximumSpacingByForfait)).coerceAtLeast(5.0)
        val requiredAt = minimumTransverseRatio * max(nodalSpacing, currentSpacing) * widthCm

        val lapFactor = when (zone) {
            SeismicZone.ZONE_IV, SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 60.0
            SeismicZone.ZONE_0 -> 40.0
            else -> 50.0
        }
        val lapLength = lapFactor * maximumBarDiameterCm

        val shearVerifications = envelopes
            .filter { it.category == ComboCategory.ELU || it.category == ComboCategory.ELA }
            .map { envelope ->
                val situation = if (envelope.category == ComboCategory.ELA) {
                    BaelSituation.ACCIDENTAL
                } else {
                    BaelSituation.DURABLE
                }
                shearVerification(
                    category = envelope.category,
                    situation = situation,
                    shear = envelope.maxShearKN,
                    widthCm = widthCm,
                    heightCm = heightCm,
                    supportWidthCm = family.supportWidthCm,
                    providedLongitudinalCm2 = bottomAdopted,
                    materials = materials
                )
            }

        return BeamTransverseDesign(
            nodalZoneLengthCm = nodalZoneLength,
            criticalZoneLengthCm = criticalZoneLength,
            firstStirrupCm = 5.0,
            nodalSpacingLimitCm = nodalSpacingLimit,
            currentSpacingLimitCm = currentSpacingLimit,
            nodalSpacingCm = nodalSpacing,
            currentSpacingCm = currentSpacing,
            stirrupLegs = chosen.count,
            stirrupDiameterMm = chosen.diameterMm,
            stirrupAreaCm2 = chosen.areaCm2,
            requiredStirrupAreaCm2 = requiredAt,
            minimumTransverseRatio = minimumTransverseRatio,
            lapLengthCm = lapLength,
            shearVerifications = shearVerifications
        )
    }

    private fun shearVerification(
        category: ComboCategory,
        situation: BaelSituation,
        shear: Double,
        widthCm: Double,
        heightCm: Double,
        supportWidthCm: Double,
        providedLongitudinalCm2: Double,
        materials: MaterialConfig
    ): BeamShearVerification {
        val coefficients = solver.coefficients(materials, situation)
        val b = widthCm / 100.0
        val d = (heightCm - settings.steelCoverCm).coerceAtLeast(1.0) / 100.0
        val supportWidthMeters = supportWidthCm / 100.0
        val strutWidth = if (supportWidthMeters > 0.0) {
            min(0.9 * d, supportWidthMeters)
        } else {
            0.9 * d
        }
        val tau = shear / (b * d) / 1000.0
        val tauLimit = min(0.20 * coefficients.fc28MPa / coefficients.gammaB, 5.0)
        val strutLimit = 0.267 * b * strutWidth * coefficients.fc28MPa * 1000.0
        val requiredLongitudinal = shear * 10.0 / coefficients.fsuMPa
        val sigmaBc = shear / (b * strutWidth) / 1000.0
        val sigmaBcLimit = 1.3 * coefficients.fc28MPa / coefficients.gammaB
        val status = when {
            tau > tauLimit + 1e-9 || shear > strutLimit + 1e-6 ||
                requiredLongitudinal > providedLongitudinalCm2 + 1e-9 ||
                sigmaBc > sigmaBcLimit + 1e-9 -> CheckStatus.FAILED
            tau > 0.9 * tauLimit || shear > 0.9 * strutLimit ||
                requiredLongitudinal > 0.9 * providedLongitudinalCm2 ||
                sigmaBc > 0.9 * sigmaBcLimit -> CheckStatus.WARNING
            else -> CheckStatus.OK
        }
        return BeamShearVerification(
            category = category,
            shearKN = shear,
            widthCm = widthCm,
            effectiveDepthCm = d * 100.0,
            strutWidthCm = strutWidth * 100.0,
            tauMPa = tau,
            tauLimitMPa = tauLimit,
            strutForceKN = shear,
            strutLimitKN = strutLimit,
            requiredLongitudinalCm2 = requiredLongitudinal,
            providedLongitudinalCm2 = providedLongitudinalCm2,
            sigmaBcMPa = sigmaBc,
            sigmaBcLimitMPa = sigmaBcLimit,
            status = status
        )
    }

    /** Verification ELS simplifiee : section rectangulaire fissuree avec n = 15. */
    private fun designServiceability(
        widthCm: Double,
        heightCm: Double,
        envelopes: List<BeamForceEnvelope>,
        spanLayout: BeamRebarLayout?,
        supportLayout: BeamRebarLayout?,
        materials: MaterialConfig
    ): List<DesignCheck> {
        val service = envelopes.filter { it.category == ComboCategory.ELS }
        if (service.isEmpty()) {
            return listOf(
                Checks.notApplicable(
                    code = "PB10.4_ELS",
                    label = "Vérification ELS des contraintes",
                    reference = "BAEL 91 révisé 99, A.4.5",
                    message = "Aucune combinaison ELS importée pour cette famille : vérification non applicable."
                )
            )
        }
        val checks = mutableListOf<DesignCheck>()
        val b = widthCm / 100.0
        val d = (heightCm - settings.steelCoverCm).coerceAtLeast(1.0) / 100.0
        val concreteLimit = 0.6 * materials.fc28MPa
        val steelLimit = settings.elsSteelStressLimitMPa ?: (0.8 * materials.feMPa)
        val spanMoment = service.maxOf { it.maxSpanMomentKNm }
        val supportMoment = service.maxOf { it.maxSupportMomentKNm }

        fun stress(momentKNm: Double, asCm2: Double?, label: String) {
            if (momentKNm <= 0.0 || asCm2 == null || asCm2 <= 1e-9) return
            val asM2 = asCm2 / 10_000.0
            val cracked = crackedRectangularSection(b, d, asM2)
            val sigmaConcrete = if (cracked.inertiaM4 > 0.0) {
                momentKNm * cracked.neutralAxisM * cracked.neutralAxisM /
                    (2.0 * cracked.inertiaM4) / 1000.0
            } else {
                0.0
            }
            val sigmaSteel = if (cracked.inertiaM4 > 0.0) {
                15.0 * momentKNm * (d - cracked.neutralAxisM) / cracked.inertiaM4 / 1000.0
            } else {
                0.0
            }
            checks += Checks.max(
                code = "PB10.4_SIGMA_BC_$label",
                label = "Contrainte béton ELS ($label)",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                provided = sigmaConcrete,
                maximum = concreteLimit,
                unit = "MPa",
                decimals = 2
            )
            checks += Checks.max(
                code = "PB10.4_SIGMA_ST_$label",
                label = "Contrainte acier ELS ($label)",
                reference = "BAEL 91 révisé 99, A.4.5.2",
                provided = sigmaSteel,
                maximum = steelLimit,
                unit = "MPa",
                decimals = 1
            )
        }

        stress(spanMoment, spanLayout?.areaCm2, "TRAVEE")
        stress(supportMoment, supportLayout?.areaCm2, "APPUI")
        return checks
    }

    private data class CrackedRectangle(val neutralAxisM: Double, val inertiaM4: Double)

    private fun crackedRectangularSection(bMeters: Double, dMeters: Double, asM2: Double): CrackedRectangle {
        if (asM2 <= 0.0 || bMeters <= 0.0 || dMeters <= 0.0) return CrackedRectangle(0.0, 0.0)
        val nAs = 15.0 * asM2
        var low = 0.0
        var high = dMeters
        repeat(100) {
            val mid = (low + high) / 2.0
            val firstMoment = bMeters * mid * mid / 2.0
            val equilibrium = firstMoment - nAs * (dMeters - mid)
            if (equilibrium > 0.0) {
                high = mid
            } else {
                low = mid
            }
        }
        val y = (low + high) / 2.0
        val inertia = bMeters * y * y * y / 3.0 + nAs * (dMeters - y) * (dMeters - y)
        return CrackedRectangle(y, inertia)
    }

    private fun buildChecks(
        spanLayout: BeamRebarLayout?,
        supportLayout: BeamRebarLayout?,
        bottomAdopted: Double,
        topAdopted: Double,
        requiredBottom: Double,
        requiredTop: Double,
        minimum: Double,
        maximum: Double,
        maximumLap: Double,
        minimumBarDiameterMm: Int,
        transverse: BeamTransverseDesign,
        serviceability: List<DesignCheck>,
        hasForces: Boolean
    ): List<DesignCheck> {
        val checks = mutableListOf<DesignCheck>()

        checks += if (spanLayout == null) {
            Checks.boolean(
                code = "PB10.2_AS_TRAVEE",
                label = "Armatures inférieures de travée",
                reference = "BAEL 91 + RPA 2024 § 7.5.2.1",
                condition = false,
                required = "≥ ${Checks.format(requiredBottom)} cm²",
                provided = "aucune disposition",
                failureStatus = CheckStatus.FAILED,
                message = "Impossible de générer une disposition de barres inférieures."
            )
        } else {
            Checks.min(
                code = "PB10.2_AS_TRAVEE",
                label = "Armatures inférieures de travée",
                reference = "BAEL 91 + RPA 2024 § 7.5.2.1",
                provided = bottomAdopted,
                minimum = requiredBottom,
                unit = "cm²",
                failureMessage = "As,inf = ${Checks.format(bottomAdopted)} cm² < " +
                    "${Checks.format(requiredBottom)} cm²."
            )
        }
        checks += if (supportLayout == null) {
            Checks.boolean(
                code = "PB10.2_AS_APPUI",
                label = "Armatures supérieures d'appui (chapeaux)",
                reference = "BAEL 91 + RPA 2024 § 7.5.2.1",
                condition = false,
                required = "≥ ${Checks.format(requiredTop)} cm²",
                provided = "aucune disposition",
                failureStatus = CheckStatus.FAILED,
                message = "Impossible de générer une disposition de chapeaux."
            )
        } else {
            Checks.min(
                code = "PB10.2_AS_APPUI",
                label = "Armatures supérieures d'appui (chapeaux)",
                reference = "BAEL 91 + RPA 2024 § 7.5.2.1",
                provided = topAdopted,
                minimum = requiredTop,
                unit = "cm²",
                failureMessage = "As,sup = ${Checks.format(topAdopted)} cm² < " +
                    "${Checks.format(requiredTop)} cm²."
            )
        }
        checks += Checks.min(
            code = "PB10.2_AS_MIN",
            label = "Pourcentage minimal RPA d'armatures de poutre",
            reference = "RPA 2024 § 7.5.2.1 (0,50 % b h)",
            provided = if (spanLayout == null) 0.0 else min(bottomAdopted, topAdopted),
            minimum = minimum,
            unit = "cm²",
            failureMessage = "Le ferraillage d'une nappe est inférieur à 0,50 % b h = " +
                "${Checks.format(minimum)} cm²."
        )
        checks += Checks.max(
            code = "PB10.2_AS_MAX",
            label = "Pourcentage maximal d'armatures de poutre",
            reference = "RPA 2024 § 7.5.2.1 (4 % en zone courante, 6 % au recouvrement)",
            provided = max(bottomAdopted, topAdopted),
            maximum = maximum,
            unit = "cm²",
            failureMessage = "Le ferraillage adopté dépasse 4 % b h = ${Checks.format(maximum)} cm²."
        )
        checks += Checks.max(
            code = "PB10.2_AS_RECOUVREMENT_MAX",
            label = "Pourcentage maximal d'armatures au recouvrement",
            reference = "RPA 2024 § 7.5.2.1 (6 % en zone de recouvrement)",
            provided = max(bottomAdopted, topAdopted),
            maximum = maximumLap,
            unit = "cm²",
            failureMessage = "Le ferraillage adopté dépasse 6 % b h = ${Checks.format(maximumLap)} cm²."
        )
        checks += Checks.min(
            code = "PB10.2_CHAPEAUX_FILANTS",
            label = "Prolongation des chapeaux sur toute la longueur",
            reference = "RPA 2024 § 7.5.2.1 (≥ 1/4 Asup,max sur toute la longueur)",
            provided = supportLayout?.continuousAreaCm2 ?: 0.0,
            minimum = 0.25 * topAdopted,
            unit = "cm²",
            failureMessage = "Les barres filantes supérieures (${Checks.format(supportLayout?.continuousAreaCm2 ?: 0.0)} cm²) " +
                "sont inférieures au quart de Asup,appui = ${Checks.format(0.25 * topAdopted)} cm²."
        )
        checks += Checks.min(
            code = "PB10.2_AINF_APPUI",
            label = "Armatures inférieures à l'appui",
            reference = "RPA 2024 § 7.5.2.1 (Ainf ≥ 0,50 Asup)",
            provided = bottomAdopted,
            minimum = 0.5 * topAdopted,
            unit = "cm²",
            failureMessage = "Ainf,appui = ${Checks.format(bottomAdopted)} cm² < 0,50 Asup = " +
                "${Checks.format(0.5 * topAdopted)} cm²."
        )
        checks += Checks.min(
            code = "PB10.2_DIAMETRE",
            label = "Diamètre minimal des barres longitudinales",
            reference = if (minimumBarDiameterMm >= 14) {
                "RPA 2024 § 7.5.2.1 (φ ≥ 14 mm en zones IV-VI)"
            } else {
                "RPA 2024 § 7.5.2.1"
            },
            provided = listOfNotNull(spanLayout?.minDiameterMm, supportLayout?.minDiameterMm)
                .minOrNull()?.toDouble() ?: 0.0,
            minimum = minimumBarDiameterMm.toDouble(),
            unit = "mm",
            decimals = 0,
            failureMessage = "Les barres longitudinales doivent avoir un diamètre ≥ " +
                "${minimumBarDiameterMm} mm."
        )
        checks += Checks.info(
            code = "PB10.2_ZONE_NODALE",
            label = "Longueur de la zone nodale",
            reference = "RPA 2024 § 7.5.2.2",
            value = "l' = 2 h = ${Checks.format(transverse.nodalZoneLengthCm)} cm"
        )
        checks += Checks.info(
            code = "PB10.2_ZONE_CRITIQUE",
            label = "Longueur de la zone critique",
            reference = "RPA 2024 § 7.5.2.1",
            value = "lcr = 1,5 h = ${Checks.format(transverse.criticalZoneLengthCm)} cm"
        )
        checks += Checks.info(
            code = "PB10.2_PREMIER_CADRE",
            label = "Position du premier cadre",
            reference = "RPA 2024 § 7.5.2.2",
            value = "à ${Checks.format(transverse.firstStirrupCm)} cm du nu de l'appui"
        )
        checks += Checks.max(
            code = "PB10.2_ESPACEMENT_NODAL",
            label = "Espacement des cadres en zone critique",
            reference = "RPA 2024 § 7.5.2.2 (min(h/4 ; 24 φt ; 17,5 cm ; 6 φl))",
            provided = transverse.nodalSpacingCm,
            maximum = transverse.nodalSpacingLimitCm,
            unit = "cm",
            failureMessage = "Espacement nodal ${Checks.format(transverse.nodalSpacingCm)} cm > " +
                "${Checks.format(transverse.nodalSpacingLimitCm)} cm."
        )
        checks += Checks.max(
            code = "PB10.2_ESPACEMENT_COURANT",
            label = "Espacement des cadres en zone courante",
            reference = "RPA 2024 § 7.5.2.2 (S' ≤ h/2)",
            provided = transverse.currentSpacingCm,
            maximum = transverse.currentSpacingLimitCm,
            unit = "cm",
            failureMessage = "Espacement courant ${Checks.format(transverse.currentSpacingCm)} cm > " +
                "${Checks.format(transverse.currentSpacingLimitCm)} cm."
        )
        checks += Checks.min(
            code = "PB10.2_AT",
            label = "Section des armatures transversales",
            reference = "RPA 2024 § 7.5.2.2 (At ≥ 0,003 S b)",
            provided = transverse.stirrupAreaCm2,
            minimum = transverse.requiredStirrupAreaCm2,
            unit = "cm²",
            failureMessage = "At = ${Checks.format(transverse.stirrupAreaCm2)} cm² < " +
                "${Checks.format(transverse.requiredStirrupAreaCm2)} cm²."
        )
        checks += Checks.info(
            code = "PB10.2_RECOUVREMENT",
            label = "Longueur de recouvrement",
            reference = "RPA 2024 § 7.5.2.1",
            value = "Lrec = ${Checks.format(transverse.lapLengthCm)} cm"
        )
        transverse.shearVerifications.forEach { shear ->
            val suffix = shear.category.name
            checks += Checks.max(
                code = "PB10.3_TAU_$suffix",
                label = "Contrainte tangente conventionnelle ($suffix)",
                reference = "RPA 2024 § 7.5.2.2 (0,20 fc28/γb ≤ 5 MPa)",
                provided = shear.tauMPa,
                maximum = shear.tauLimitMPa,
                unit = "MPa",
                decimals = 2,
                failureMessage = "tau_u = ${Checks.format(shear.tauMPa, 2)} MPa > " +
                    "${Checks.format(shear.tauLimitMPa, 2)} MPa."
            )
            checks += Checks.max(
                code = "PB10.3_BIELLE_$suffix",
                label = "Compression des bielles ($suffix)",
                reference = "RPA 2024 § 7.5.2.2 (Vu ≤ 0,267 b a fc28)",
                provided = shear.strutForceKN,
                maximum = shear.strutLimitKN,
                unit = "kN",
                failureMessage = "Vu = ${Checks.format(shear.strutForceKN)} kN > " +
                    "${Checks.format(shear.strutLimitKN)} kN."
            )
            checks += Checks.min(
                code = "PB10.3_ACIER_LONGITUDINAL_$suffix",
                label = "Acier longitudinal tendu à l'appui ($suffix)",
                reference = "RPA 2024 § 7.5.2.2 (As ≥ Vu/(fe/γs))",
                provided = shear.providedLongitudinalCm2,
                minimum = shear.requiredLongitudinalCm2,
                unit = "cm²",
                failureMessage = "As,inf = ${Checks.format(shear.providedLongitudinalCm2)} cm² < " +
                    "${Checks.format(shear.requiredLongitudinalCm2)} cm²."
            )
            checks += Checks.max(
                code = "PB10.3_SIGMA_BC_$suffix",
                label = "Contrainte de compression des bielles ($suffix)",
                reference = "RPA 2024 § 7.5.2.2 (σbc ≤ 1,3 fc28/γb)",
                provided = shear.sigmaBcMPa,
                maximum = shear.sigmaBcLimitMPa,
                unit = "MPa",
                decimals = 2,
                failureMessage = "sigma_bc = ${Checks.format(shear.sigmaBcMPa, 2)} MPa > " +
                    "${Checks.format(shear.sigmaBcLimitMPa, 2)} MPa."
            )
        }
        checks += serviceability
        if (!hasForces) {
            checks += Checks.notApplicable(
                code = "PB10.0_EFFORTS",
                label = "Sections critiques de poutre",
                reference = "ETABS - Beam Forces",
                message = "Aucun effort importé : seul le ferraillage minimal RPA est vérifié."
            )
        }
        return checks
    }

    private fun isHighZone(zone: SeismicZone): Boolean =
        zone == SeismicZone.ZONE_IV || zone == SeismicZone.ZONE_V || zone == SeismicZone.ZONE_VI
}
