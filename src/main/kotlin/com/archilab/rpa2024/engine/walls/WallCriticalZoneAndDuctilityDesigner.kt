package com.archilab.rpa2024.engine.walls

import com.archilab.rpa2024.engine.frames.BaelRectangularResult
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.BaelSituation
import com.archilab.rpa2024.engine.frames.BaelSectionInput
import com.archilab.rpa2024.engine.frames.RebarBar
import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import com.archilab.rpa2024.model.MaterialConfig
import com.archilab.rpa2024.model.ShearWallFamily
import com.archilab.rpa2024.model.StructuralJoint
import com.archilab.rpa2024.model.StorySystem
import com.archilab.rpa2024.parser.ComboCategory
import com.archilab.rpa2024.parser.EtabsResults
import com.archilab.rpa2024.parser.PierForceRow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Parametres de ferraillage des voiles. */
data class WallDesignSettings(
    val tensionCoverCm: Double = 3.0,
    val coreCoverCm: Double = 2.5,
    val boundaryMinimumRatio: Double = 0.005,
    val webMinimumRatio: Double = 0.002,
    val boundaryMinDiameterMm: Int = 12,
    val boundaryMaxBars: Int = 32,
    val webBarDiameterMm: Int = 12,
    val stirrupMinDiameterMm: Int = 8,
    val behaviorFactor: Double = 3.5
)

/** Element de rive confine (gauche ou droit) avec son ferraillage. */
data class WallBoundaryDesign(
    val side: String,
    val lengthLcCm: Double,
    val thicknessBcCm: Double,
    val coreWidthB0Cm: Double,
    val coreHeightH0Cm: Double,
    val areaAcCm2: Double,
    val requiredAsCm2: Double,
    val minimumAsCm2: Double,
    val adoptedAsCm2: Double,
    val bars: RebarBar,
    val stirrupSpacingCm: Double,
    val stirrupSpacingLimitCm: Double,
    val requiredTransverseCm2: Double,
    val providedTransverseCm2: Double,
    val stirrupLegs: Int,
    val stirrupDiameterMm: Int
) {
    val barLabel: String get() = "${bars.count}T${bars.diameterMm}"
    val stirrupLabel: String get() = "$stirrupLegs brins phi$stirrupDiameterMm"
}

/** Nappes d'armatures de l'ame du voile. */
data class WallWebDesign(
    val lengthCm: Double,
    val layers: Int,
    val barDiameterMm: Int,
    val verticalSpacingCm: Double,
    val horizontalSpacingCm: Double,
    val verticalAreaPerMeterCm2: Double,
    val horizontalAreaPerMeterCm2: Double,
    val verticalRatio: Double,
    val horizontalRatio: Double,
    val minimumRatio: Double,
    val maximumSpacingCm: Double,
    val tauMPa: Double,
    val tauLimitMPa: Double,
    val shearRatioRequired: Double
) {
    val barLabel: String get() = "phi$barDiameterMm"
}

/** Indicateurs de ductilite locale du voile. */
data class WallDuctilityIndicators(
    val inertiaM4: Double,
    val normalizedAxial: Double,
    val requiredCurvatureDuctility: Double,
    val availableCurvatureDuctility: Double,
    val confinementMechanicalRatio: Double,
    val ultimateCompressedHeightMeters: Double,
    val admissibleConfinementLengthMeters: Double,
    val providedConfinementLengthMeters: Double
) {
    val isConfinementLengthSatisfied: Boolean
        get() = providedConfinementLengthMeters + 1e-9 >= admissibleConfinementLengthMeters
}

/** Resultat complet du calcul et du ferraillage d'un voile. */
data class WallDesignResult(
    val qualification: WallQualificationResult,
    val envelope: WallEnvelopeResult,
    val bael: BaelRectangularResult,
    val designAxialKN: Double,
    val designMomentKNm: Double,
    val designCombo: String,
    val boundaryLeft: WallBoundaryDesign,
    val boundaryRight: WallBoundaryDesign,
    val web: WallWebDesign,
    val ductility: WallDuctilityIndicators,
    val checks: List<DesignCheck>,
    val notes: List<String> = emptyList()
) {
    val family: ShearWallFamily get() = qualification.family
    val forces: WallDesignForces get() = qualification.forces
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })

    fun toElementReport(): ElementCheckReport = ElementCheckReport(
        elementType = "Voile",
        name = family.pierName,
        geometry = "${family.geometryLabel} ; he = ${Checks.format(family.clearHeightHeMeters, 2)} m ; " +
            "Hw = ${Checks.format(family.totalHeightHwMeters, 2)} m",
        checks = checks
    )
}

/** Synthese du chapitre 10.3 (calcul et ferraillage des voiles). */
data class WallDesignSet(
    val walls: List<WallDesignResult>,
    val chapter: ChapterReport
) {
    val status: CheckStatus get() = CheckStatus.worst(walls.map { it.status })
    val failedCount: Int get() = walls.count { it.status == CheckStatus.FAILED }
    val warningCount: Int get() = walls.count { it.status == CheckStatus.WARNING }
}

/**
 * Moteur de calcul et de ferraillage des voiles de contreventement (chapitre 10.3) :
 * flexion composee BAEL 91 en situation accidentelle sur la section rectangulaire
 * bw x lw, elements de rive confines § 7.7.4.1, ductilite locale et nappes d'ame.
 */
class WallCriticalZoneAndDuctilityDesigner(
    private val solver: BaelRectangularSolver = BaelRectangularSolver(),
    private val qualificationEngine: ShearWallQualificationAndChecks = ShearWallQualificationAndChecks(),
    private val envelopeGenerator: WallEnvelopeGenerator = WallEnvelopeGenerator(),
    private val settings: WallDesignSettings = WallDesignSettings()
) {

    fun design(joint: StructuralJoint, results: EtabsResults): WallDesignSet =
        design(
            walls = joint.sectionCatalog.shearWalls,
            results = results,
            materials = joint.materials,
            storySystem = joint.storySystem
        )

    fun design(
        walls: List<ShearWallFamily>,
        results: EtabsResults,
        materials: MaterialConfig,
        storySystem: StorySystem? = null
    ): WallDesignSet {
        val qualification = qualificationEngine.assess(
            walls = walls,
            results = results,
            materials = materials,
            storyCount = storySystem?.numStoriesN
        )
        val designs = qualification.walls.map { qualified ->
            val envelope = envelopeGenerator.generate(
                wall = qualified.family,
                forces = qualified.forces,
                rows = results.pierForces,
                storyCount = storySystem?.numStoriesN,
                storySystem = storySystem
            )
            designWall(qualified, envelope, materials)
        }

        val checks = mutableListOf<DesignCheck>()
        checks += Checks.boolean(
            code = "PSW10.1_FAMILLES",
            label = "Familles de voiles ferraillees",
            reference = "RPA 2024 § 7.7 + BAEL 91",
            condition = walls.isNotEmpty(),
            required = "Au moins un voile",
            provided = "${walls.size} voile(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Aucun voile a calculer : verifier les affectations de sections coque dans ETABS."
        )
        checks += Checks.boolean(
            code = "PSW10.1_EFFORTS",
            label = "Efforts de flexion composee disponibles",
            reference = "ETABS - Pier Forces",
            condition = designs.any { it.forces.hasForces },
            required = "Au moins un voile avec efforts",
            provided = "${designs.count { it.forces.hasForces }} voile(s)",
            failureStatus = CheckStatus.WARNING,
            message = "Ferraillage minimal applique : aucun effort de base exploitable."
        )
        checks += Checks.boolean(
            code = "PSW10.1_CONFORMITE",
            label = "Conformite du ferraillage des voiles",
            reference = "RPA 2024 § 7.7.4 + BAEL 91",
            condition = designs.none { it.status == CheckStatus.FAILED },
            required = "Aucun voile en defaut",
            provided = "${designs.count { it.status == CheckStatus.FAILED }} voile(s) en defaut",
            failureStatus = CheckStatus.FAILED,
            message = "Au moins un voile ne satisfait pas les conditions de ferraillage ou de confinement."
        )

        val lines = designs.flatMap { design ->
            buildList {
                val family = design.family
                add(
                    "Voile ${family.pierName} (${family.geometryLabel}) : h_cr = " +
                        "${Checks.format(design.envelope.criticalHeight.retainedMeters, 2)} m ; " +
                        "zone critique = ${design.envelope.criticalHeight.coveredStories.joinToString(" ; ").ifBlank { "n/d" }}."
                )
                add(
                    "  Flexion composee accidentelle : N = ${Checks.format(design.designAxialKN)} kN ; " +
                        "M = ${Checks.format(design.designMomentKNm)} kN.m (${design.designCombo}) ; " +
                        "etat = ${design.bael.state.label} ; y0 = " +
                        "${Checks.format(design.bael.neutralAxisMeters * 100.0)} cm ; A_v,calcul = " +
                        "${Checks.format(design.bael.tensionSteelCm2)} cm2."
                )
                add(
                    "  Rives : lc = ${Checks.format(design.boundaryLeft.lengthLcCm)} cm ; " +
                        "bc = ${Checks.format(design.boundaryLeft.thicknessBcCm)} cm ; " +
                        "A_v,min = ${Checks.format(design.boundaryLeft.minimumAsCm2)} cm2 ; " +
                        "adopte = ${Checks.format(design.boundaryLeft.adoptedAsCm2)} cm2 " +
                        "(${design.boundaryLeft.barLabel}) ; cadres ${design.boundaryLeft.stirrupLabel} " +
                        "tous les ${Checks.format(design.boundaryLeft.stirrupSpacingCm)} cm."
                )
                add(
                    "  Ame : L = ${Checks.format(design.web.lengthCm)} cm ; ${design.web.layers} nappes " +
                        "${design.web.barLabel} ; Sv = ${Checks.format(design.web.verticalSpacingCm)} cm ; " +
                        "Sh = ${Checks.format(design.web.horizontalSpacingCm)} cm ; " +
                        "rho_v = ${Checks.format(design.web.verticalRatio, 4)} ; " +
                        "rho_h = ${Checks.format(design.web.horizontalRatio, 4)}."
                )
                add(
                    "  Ductilite locale : nu_d = ${Checks.format(design.ductility.normalizedAxial, 3)} ; " +
                        "mu_phi,requis = ${Checks.format(design.ductility.requiredCurvatureDuctility, 2)} ; " +
                        "mu_phi,disponible = ${Checks.format(design.ductility.availableCurvatureDuctility, 2)} ; " +
                        "omega_wd = ${Checks.format(design.ductility.confinementMechanicalRatio, 3)} ; " +
                        "xu = ${Checks.format(design.ductility.ultimateCompressedHeightMeters, 3)} m ; " +
                        "lc,adm = ${Checks.format(design.ductility.admissibleConfinementLengthMeters, 3)} m."
                )
            }
        }

        val summary = "${designs.size} voile(s) ferraille(s) : " +
            "${designs.count { it.status == CheckStatus.OK }} conforme(s), " +
            "${designs.count { it.status == CheckStatus.WARNING }} a verifier, " +
            "${designs.count { it.status == CheckStatus.FAILED }} non conforme(s)."

        return WallDesignSet(
            walls = designs,
            chapter = ChapterReport(
                number = 10,
                title = "10.3 Calcul et ferraillage des voiles de contreventement",
                summary = summary,
                checks = checks,
                elements = designs.map { it.toElementReport() },
                lines = lines
            )
        )
    }

    private fun designWall(
        qualified: WallQualificationResult,
        envelope: WallEnvelopeResult,
        materials: MaterialConfig
    ): WallDesignResult {
        val family = qualified.family
        val forces = qualified.forces
        val notes = mutableListOf<String>()
        val widthCm = family.thicknessBwMeters * 100.0
        val depthCm = family.lengthLwMeters * 100.0

        val governing = governingCombinedCase(forces, widthCm, depthCm, materials)
        val bael = governing.result

        val boundaryLeft = designBoundary("Rive gauche", family, bael, materials)
        val boundaryRight = boundaryLeft.copy(side = "Rive droite")
        val web = designWeb(family, qualified, materials)
        val ductility = ductilityIndicators(family, qualified, boundaryLeft, materials)

        val checks = mutableListOf<DesignCheck>()
        checks += qualified.checks

        checks += Checks.info(
            code = "PSW8_FLEXION_COMPOSEE",
            label = "Flexion composee a la base",
            reference = "BAEL 91 mod. 99 - situation accidentelle",
            value = "b = ${Checks.format(widthCm)} cm ; h = ${Checks.format(depthCm)} cm ; c = " +
                "${Checks.format(settings.tensionCoverCm)} cm ; N = ${Checks.format(governing.axialKN)} kN ; " +
                "M = ${Checks.format(governing.momentKNm)} kN.m ; etat = ${bael.state.label} ; " +
                "y0 = ${Checks.format(bael.neutralAxisMeters * 100.0)} cm ; A_v,calcul = " +
                "${Checks.format(bael.tensionSteelCm2)} cm2."
        )
        checks += Checks.min(
            code = "PSW9_RIVE_LONGUEUR",
            label = "Longueur minimale de l'element de rive lc",
            reference = "RPA 2024 § 7.7.4.1",
            provided = boundaryLeft.lengthLcCm,
            minimum = maxOf(0.15 * depthCm, 1.5 * widthCm, 45.0),
            unit = "cm",
            decimals = 1,
            failureMessage = "lc = ${Checks.format(boundaryLeft.lengthLcCm)} cm < max(0,15 lw ; 1,5 bw ; 45 cm)."
        )
        checks += Checks.min(
            code = "PSW10_RIVE_ARMATURES",
            label = "Section d'armatures verticales de rive",
            reference = "RPA 2024 § 7.7.4.1 (A_v >= 0,50 % bc lc)",
            provided = boundaryLeft.adoptedAsCm2,
            minimum = boundaryLeft.requiredAsCm2,
            unit = "cm2",
            decimals = 2,
            failureMessage = "A_v,adopte = ${Checks.format(boundaryLeft.adoptedAsCm2)} cm2 < " +
                "${Checks.format(boundaryLeft.requiredAsCm2)} cm2 requis."
        )
        checks += Checks.boolean(
            code = "PSW11_RIVE_DIAMETRE",
            label = "Diametre minimal des barres de rive",
            reference = "RPA 2024 § 7.7.4.1",
            condition = boundaryLeft.bars.diameterMm >= settings.boundaryMinDiameterMm &&
                boundaryLeft.bars.count % 2 == 0,
            required = "phi >= ${settings.boundaryMinDiameterMm} mm, nombre pair",
            provided = "${boundaryLeft.barLabel}",
            failureStatus = CheckStatus.FAILED,
            message = "Les barres de rive doivent etre en nombre pair avec phi >= " +
                "${settings.boundaryMinDiameterMm} mm."
        )
        checks += Checks.max(
            code = "PSW12_RIVE_ESPACEMENT",
            label = "Espacement vertical des cadres de rive St",
            reference = "RPA 2024 § 7.7.4.1",
            provided = boundaryLeft.stirrupSpacingCm,
            maximum = boundaryLeft.stirrupSpacingLimitCm,
            unit = "cm",
            decimals = 1,
            failureMessage = "St = ${Checks.format(boundaryLeft.stirrupSpacingCm)} cm > " +
                "min(b0/3 ; 12,5 cm ; 6 phi_l)."
        )
        checks += Checks.min(
            code = "PSW13_RIVE_CONFINEMENT",
            label = "Section transversale de confinement de rive At",
            reference = "RPA 2024 § 7.7.4.1 (At >= 0,09 St b0 fc28/fe)",
            provided = boundaryLeft.providedTransverseCm2,
            minimum = boundaryLeft.requiredTransverseCm2,
            unit = "cm2",
            decimals = 2,
            failureMessage = "At = ${Checks.format(boundaryLeft.providedTransverseCm2)} cm2 < " +
                "${Checks.format(boundaryLeft.requiredTransverseCm2)} cm2 requis."
        )
        checks += Checks.min(
            code = "PSW14_LONGUEUR_CONFINEMENT",
            label = "Longueur de confinement disponible",
            reference = "RPA 2024 § 7.7.4.1 (lc >= lc,adm)",
            provided = ductility.providedConfinementLengthMeters,
            minimum = ductility.admissibleConfinementLengthMeters,
            unit = "m",
            decimals = 3,
            failureMessage = "lc = ${Checks.format(ductility.providedConfinementLengthMeters, 3)} m < " +
                "lc,adm = ${Checks.format(ductility.admissibleConfinementLengthMeters, 3)} m."
        )
        checks += Checks.boolean(
            code = "PSW15_AME_PRESENCE",
            label = "Presence d'une ame entre les deux rives",
            reference = "RPA 2024 § 7.7.4.1",
            condition = web.lengthCm > 1.0,
            required = "L_ame = lw - 2 lc > 0",
            provided = "L_ame = ${Checks.format(web.lengthCm)} cm",
            failureStatus = CheckStatus.WARNING,
            message = "Les deux rives se chevauchent : verifier lw et lc."
        )
        checks += Checks.min(
            code = "PSW16_AME_VERTICAL",
            label = "Taux d'armatures verticales d'ame",
            reference = "RPA 2024 § 7.7.4.1 (rho_v >= 0,20 %)",
            provided = web.verticalRatio,
            minimum = web.minimumRatio,
            unit = "",
            decimals = 4,
            failureMessage = "rho_v = ${Checks.format(web.verticalRatio, 4)} < 0,0020."
        )
        checks += Checks.min(
            code = "PSW17_AME_HORIZONTAL",
            label = "Taux d'armatures horizontales d'ame",
            reference = "RPA 2024 § 7.7.4.1 (rho_h >= 0,20 %)",
            provided = web.horizontalRatio,
            minimum = web.minimumRatio,
            unit = "",
            decimals = 4,
            failureMessage = "rho_h = ${Checks.format(web.horizontalRatio, 4)} < 0,0020."
        )
        checks += Checks.max(
            code = "PSW18_AME_ESPACEMENT",
            label = "Espacement maximal des nappes d'ame",
            reference = "RPA 2024 § 7.7.4.1",
            provided = maxOf(web.verticalSpacingCm, web.horizontalSpacingCm),
            maximum = web.maximumSpacingCm,
            unit = "cm",
            decimals = 1,
            failureMessage = "Sv ou Sh > min(1,5 bw ; 30 cm)."
        )
        checks += Checks.max(
            code = "PSW19_AME_CISAILLEMENT",
            label = "Cisaillement de l'ame",
            reference = "RPA 2024 § 7.7.3 Eq. (7.12)",
            provided = web.tauMPa,
            maximum = web.tauLimitMPa,
            unit = "MPa",
            decimals = 3,
            failureMessage = "tau_b = ${Checks.format(web.tauMPa, 3)} MPa > tau_adm = " +
                "${Checks.format(web.tauLimitMPa, 3)} MPa."
        )
        checks += Checks.info(
            code = "PSW20_DUCTILITE",
            label = "Ductilite locale et confinement",
            reference = "RPA 2024 § 7.7.4.1",
            value = "Ic = ${Checks.format(ductility.inertiaM4, 4)} m4 ; nu_d = " +
                "${Checks.format(ductility.normalizedAxial, 3)} ; mu_phi,requis = " +
                "${Checks.format(ductility.requiredCurvatureDuctility, 2)} ; mu_phi,disponible = " +
                "${Checks.format(ductility.availableCurvatureDuctility, 2)} ; omega_wd = " +
                "${Checks.format(ductility.confinementMechanicalRatio, 3)} ; xu = " +
                "${Checks.format(ductility.ultimateCompressedHeightMeters, 3)} m."
        )

        if (family.classification != com.archilab.rpa2024.model.WallSlenderness.SLENDER_WALL) {
            notes += "Voile non elance (hw/lw = ${Checks.format(family.slendernessHwOverLw, 2)}) : " +
                "les regles de zone critique restent appliquees par securite."
        }
        if (!forces.hasForces) {
            notes += "Aucun effort de base : seul le ferraillage minimal RPA est dimensionne."
        }
        if (web.shearRatioRequired > web.minimumRatio) {
            notes += "Taux horizontal theorique de reprise de l'effort tranchant : " +
                "${Checks.format(web.shearRatioRequired, 4)} ; la nappe minimale RPA " +
                "(${Checks.format(web.minimumRatio, 4)}) est retenue et la contrainte est verifiee separement."
        }

        return WallDesignResult(
            qualification = qualified,
            envelope = envelope,
            bael = bael,
            designAxialKN = governing.axialKN,
            designMomentKNm = governing.momentKNm,
            designCombo = governing.combo,
            boundaryLeft = boundaryLeft,
            boundaryRight = boundaryRight,
            web = web,
            ductility = ductility,
            checks = checks,
            notes = notes
        )
    }

    private data class CombinedCase(
        val result: BaelRectangularResult,
        val axialKN: Double,
        val momentKNm: Double,
        val combo: String
    )

    private fun governingCombinedCase(
        forces: WallDesignForces,
        widthCm: Double,
        depthCm: Double,
        materials: MaterialConfig
    ): CombinedCase {
        val rows = forces.bottomRows.filter {
            !forces.hasSeismicForces || it.category == ComboCategory.ELA
        }.ifEmpty { forces.bottomRows }
        val safeWidthCm = widthCm.coerceAtLeast(1.0)
        val safeDepthCm = depthCm.coerceAtLeast(settings.tensionCoverCm + 1.0)

        if (rows.isEmpty()) {
            val result = solver.solve(
                BaelSectionInput(
                    bCm = safeWidthCm,
                    hCm = safeDepthCm,
                    tensionCoverCm = settings.tensionCoverCm,
                    compressionCoverCm = settings.tensionCoverCm,
                    nuKN = forces.axialCompressionKN,
                    muGKNm = forces.momentKNm,
                    situation = BaelSituation.ACCIDENTAL
                ),
                materials
            )
            return CombinedCase(
                result = result,
                axialKN = forces.axialCompressionKN,
                momentKNm = forces.momentKNm,
                combo = forces.axialCombo
            )
        }

        fun momentOf(row: PierForceRow): Double =
            abs(row.m3KNm).takeIf { it > 1e-9 } ?: abs(row.m2KNm)

        return rows.map { row ->
            val axial = (-row.pKN).coerceAtLeast(0.0)
            val moment = momentOf(row)
            CombinedCase(
                result = solver.solve(
                    BaelSectionInput(
                        bCm = safeWidthCm,
                        hCm = safeDepthCm,
                        tensionCoverCm = settings.tensionCoverCm,
                        compressionCoverCm = settings.tensionCoverCm,
                        nuKN = axial,
                        muGKNm = moment,
                        situation = BaelSituation.ACCIDENTAL
                    ),
                    materials
                ),
                axialKN = axial,
                momentKNm = moment,
                combo = row.loadCase
            )
        }.maxBy { it.result.tensionSteelCm2 + it.result.topSteelCm2 }
    }

    private fun designBoundary(
        side: String,
        family: ShearWallFamily,
        bael: BaelRectangularResult,
        materials: MaterialConfig
    ): WallBoundaryDesign {
        val lwCm = family.lengthLwMeters * 100.0
        val bwCm = family.thicknessBwMeters * 100.0
        val lc = maxOf(0.15 * lwCm, 1.5 * bwCm, 45.0)
        val bc = bwCm
        val b0 = (bc - 2.0 * settings.coreCoverCm).coerceAtLeast(1.0)
        val h0 = (lc - 2.0 * settings.coreCoverCm).coerceAtLeast(1.0)
        val area = bc * lc
        val minimum = settings.boundaryMinimumRatio * area
        val required = max(bael.tensionSteelCm2, minimum)
        val bars = selectBoundaryBars(required)
        val barDiameterCm = bars.diameterMm / 10.0
        val spacingLimit = minOf(b0 / 3.0, 12.5, 6.0 * barDiameterCm)
        val spacing = floor(spacingLimit).coerceAtLeast(5.0)
        val requiredAt = 0.09 * spacing * b0 * materials.fc28MPa / materials.feMPa
        val stirrup = selectStirrup(requiredAt)

        return WallBoundaryDesign(
            side = side,
            lengthLcCm = lc,
            thicknessBcCm = bc,
            coreWidthB0Cm = b0,
            coreHeightH0Cm = h0,
            areaAcCm2 = area,
            requiredAsCm2 = required,
            minimumAsCm2 = minimum,
            adoptedAsCm2 = bars.areaCm2,
            bars = bars,
            stirrupSpacingCm = spacing,
            stirrupSpacingLimitCm = spacingLimit,
            requiredTransverseCm2 = requiredAt,
            providedTransverseCm2 = stirrup.areaCm2,
            stirrupLegs = stirrup.legs,
            stirrupDiameterMm = stirrup.diameterMm
        )
    }

    private data class StirrupChoice(
        val legs: Int,
        val diameterMm: Int,
        val areaCm2: Double
    )

    private fun selectStirrup(requiredAtCm2: Double): StirrupChoice {
        val options = listOf(2 to 8, 2 to 10, 4 to 8, 2 to 12, 4 to 10, 4 to 12, 4 to 14)
        val choices = options.map { (legs, diameter) ->
            val unit = PI * (diameter / 10.0).pow(2) / 4.0
            StirrupChoice(legs, diameter, legs * unit)
        }
        return choices.firstOrNull { it.areaCm2 + 1e-9 >= requiredAtCm2 }
            ?: StirrupChoice(4, 14, 4 * PI * 1.96 / 4.0)
    }

    private fun selectBoundaryBars(requiredCm2: Double): RebarBar {
        val diameters = listOf(12, 14, 16, 20, 25, 32)
            .filter { it >= settings.boundaryMinDiameterMm }
        data class Candidate(val bar: RebarBar, val area: Double)

        val candidates = mutableListOf<Candidate>()
        diameters.forEach { diameter ->
            val unit = PI * (diameter / 10.0).pow(2) / 4.0
            if (unit <= 0.0) return@forEach
            val rawCount = ceil(requiredCm2 / unit).toInt().coerceAtLeast(4)
            val count = if (rawCount % 2 == 0) rawCount else rawCount + 1
            if (count <= settings.boundaryMaxBars) {
                candidates += Candidate(RebarBar(count, diameter), count * unit)
            }
        }

        val above = candidates.filter { it.area + 1e-9 >= requiredCm2 }
        if (above.isEmpty()) {
            val diameter = diameters.maxOrNull() ?: 32
            val unit = PI * (diameter / 10.0).pow(2) / 4.0
            val count = ceil(requiredCm2 / unit).toInt().coerceAtLeast(4)
            val even = if (count % 2 == 0) count else count + 1
            return RebarBar(even, diameter)
        }
        // Parmi les sections proches du minimum, on privilegie le moins de barres.
        val near = above.filter { it.area <= requiredCm2 * 1.15 + 1e-9 }
        val pool = near.ifEmpty { above }
        return pool.minWithOrNull(
            compareBy<Candidate> { it.bar.count }
                .thenBy { it.area }
                .thenBy { it.bar.diameterMm }
        )?.bar ?: above.minBy { it.area }.bar
    }

    private fun designWeb(
        family: ShearWallFamily,
        qualified: WallQualificationResult,
        materials: MaterialConfig
    ): WallWebDesign {
        val bwCm = family.thicknessBwMeters * 100.0
        val lcTotal = 2.0 * maxOf(0.15 * family.lengthLwMeters * 100.0, 1.5 * bwCm, 45.0)
        val length = (family.lengthLwMeters * 100.0 - lcTotal).coerceAtLeast(0.0)
        val layers = 2
        val diameter = settings.webBarDiameterMm
        val unitArea = PI * (diameter / 10.0).pow(2) / 4.0
        val maxSpacing = min(1.5 * bwCm, 30.0)
        val minimumRatio = settings.webMinimumRatio

        fun ratioAt(spacingCm: Double): Double =
            if (bwCm <= 0.0 || spacingCm <= 0.0) 0.0 else layers * unitArea / (bwCm * spacingCm)

        fun spacingForRatio(): Double {
            var spacing = floor(maxSpacing / 5.0) * 5.0
            if (spacing < 5.0) spacing = 5.0
            while (spacing > 5.0 && ratioAt(spacing) < minimumRatio) {
                spacing -= 5.0
            }
            return spacing
        }

        val spacing = spacingForRatio()
        val ratio = ratioAt(spacing)
        val areaPerMeter = layers * unitArea * 100.0 / spacing
        val gammaS = materials.gammaSAccidental.coerceAtLeast(1e-9)
        val shearRatio = qualified.tauMPa / (materials.feMPa / gammaS)

        return WallWebDesign(
            lengthCm = length,
            layers = layers,
            barDiameterMm = diameter,
            verticalSpacingCm = spacing,
            horizontalSpacingCm = spacing,
            verticalAreaPerMeterCm2 = areaPerMeter,
            horizontalAreaPerMeterCm2 = areaPerMeter,
            verticalRatio = ratio,
            horizontalRatio = ratio,
            minimumRatio = minimumRatio,
            maximumSpacingCm = maxSpacing,
            tauMPa = qualified.tauMPa,
            tauLimitMPa = qualified.tauLimitMPa,
            shearRatioRequired = shearRatio
        )
    }

    private fun ductilityIndicators(
        family: ShearWallFamily,
        qualified: WallQualificationResult,
        boundary: WallBoundaryDesign,
        materials: MaterialConfig
    ): WallDuctilityIndicators {
        val lw = family.lengthLwMeters
        val bw = family.thicknessBwMeters
        val inertia = if (lw > 0.0 && bw > 0.0) bw * lw.pow(3) / 12.0 else 0.0
        val nu = qualified.normalizedAxial
        val xu = if (lw > 0.0) (nu * lw / 0.8).coerceIn(0.0, lw) else 0.0
        val muPhi = max(1.0, 2.0 * settings.behaviorFactor - 1.0)
        val epsilonCu = 0.0035
        val epsilonSy = materials.feMPa / 200_000.0
        val effectiveDepth = (family.lengthLwMeters - settings.tensionCoverCm / 100.0).coerceAtLeast(0.0)
        val availableMuPhi = if (xu > 0.0 && epsilonSy > 0.0) {
            epsilonCu * effectiveDepth / (xu * epsilonSy)
        } else {
            0.0
        }
        val omegaWd = if (boundary.coreWidthB0Cm > 0.0 && boundary.stirrupSpacingCm > 0.0) {
            boundary.providedTransverseCm2 / (boundary.coreWidthB0Cm * boundary.stirrupSpacingCm) *
                materials.feMPa / materials.fc28MPa
        } else {
            0.0
        }
        val lcAdm = if (muPhi * epsilonSy > 0.0) {
            (xu * (1.0 - epsilonCu / (muPhi * epsilonSy))).coerceAtLeast(0.0)
        } else {
            xu
        }

        return WallDuctilityIndicators(
            inertiaM4 = inertia,
            normalizedAxial = nu,
            requiredCurvatureDuctility = muPhi,
            availableCurvatureDuctility = availableMuPhi,
            confinementMechanicalRatio = omegaWd,
            ultimateCompressedHeightMeters = xu,
            admissibleConfinementLengthMeters = lcAdm,
            providedConfinementLengthMeters = boundary.lengthLcCm / 100.0
        )
    }
}
