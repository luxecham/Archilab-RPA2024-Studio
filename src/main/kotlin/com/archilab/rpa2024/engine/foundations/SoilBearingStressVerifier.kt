package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.engine.geometry.ChapterReport
import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.engine.geometry.Checks
import com.archilab.rpa2024.engine.geometry.DesignCheck
import com.archilab.rpa2024.engine.geometry.ElementCheckReport
import kotlin.math.abs
import kotlin.math.max

/**
 * Donnees de la verification de portance d'une semelle filante.
 * Le tassement est pris en valeur positive ; il est converti en contrainte
 * effective par le modele de Winkler : sigma_eff = delta . Ks.
 */
data class SoilBearingVerificationInput(
    val settlementMeters: Double,
    val subgradeModulusKNM3: Double,
    val allowableStressKNM2: Double,
    val allowableStressBars: Double = allowableStressKNM2 / 100.0,
    val axialServiceKN: Double = 0.0,
    val footingLengthMeters: Double = 1.0,
    val providedWidthMeters: Double? = null,
    val upliftMeters: Double = 0.0,
    val foundationDepthMeters: Double = 0.0
) {
    init {
        require(settlementMeters >= 0.0) { "Le tassement doit être positif." }
        require(subgradeModulusKNM3 > 0.0) { "Le module de réaction du sol doit être positif." }
        require(allowableStressKNM2 > 0.0) { "La contrainte admissible doit être positive." }
        require(footingLengthMeters > 0.0) { "La longueur de semelle doit être positive." }
        require(upliftMeters >= 0.0) { "Le soulèvement doit être positif." }
    }
}

/** Resultat de la verification de portance et de la largeur minimale. */
data class SoilBearingVerificationResult(
    val input: SoilBearingVerificationInput,
    val effectiveStressKNM2: Double,
    val effectiveStressBars: Double,
    val utilization: Double,
    val minimumWidthMeters: Double,
    val checks: List<DesignCheck>,
    val warnings: List<String> = emptyList()
) {
    val allowableStressKNM2: Double get() = input.allowableStressKNM2
    val allowableStressBars: Double get() = input.allowableStressBars
    val status: CheckStatus get() = CheckStatus.worst(checks.map { it.status })
    val isBearingSatisfied: Boolean get() = effectiveStressKNM2 <= allowableStressKNM2 + 1e-9

    fun toChapterReport(): ChapterReport {
        val element = ElementCheckReport(
            elementType = "Fondation",
            name = "Portance du sol",
            geometry = "Ks = ${Checks.format(input.subgradeModulusKNM3, 0)} kN/m3, " +
                "σ̄ = ${Checks.format(allowableStressKNM2, 1)} kN/m2 " +
                "(${Checks.format(allowableStressBars, 2)} bars)",
            checks = checks
        )
        return ChapterReport(
            number = 13,
            title = "13.2 Vérification de la portance du sol",
            summary = if (isBearingSatisfied) {
                "Contrainte effective ${Checks.format(effectiveStressKNM2, 2)} kN/m2 " +
                    "≤ ${Checks.format(allowableStressKNM2, 2)} kN/m2."
            } else {
                "Portance dépassée : ${Checks.format(effectiveStressKNM2, 2)} kN/m2 > " +
                    "${Checks.format(allowableStressKNM2, 2)} kN/m2."
            },
            checks = checks,
            elements = listOf(element),
            lines = buildList {
                add(
                    "Tassement de calcul Δ = ${Checks.format(input.settlementMeters * 1000.0, 2)} mm, " +
                        "σ_eff = Δ . Ks = ${Checks.format(effectiveStressKNM2, 2)} kN/m2."
                )
                add(
                    "Largeur minimale B_min = N_ser / (L . σ̄) = " +
                        "${Checks.format(minimumWidthMeters, 3)} m " +
                        "(N_ser = ${Checks.format(input.axialServiceKN, 1)} kN, " +
                        "L = ${Checks.format(input.footingLengthMeters, 2)} m)."
                )
                if (input.upliftMeters > 1e-6) {
                    add(
                        "Soulèvement local détecté : ${Checks.format(input.upliftMeters * 1000.0, 2)} mm. " +
                            "Vérifier le poids propre et le ferraillage de traction."
                    )
                }
            }
        )
    }
}

/**
 * Verification native de la portance d'une semelle filante a partir du
 * deplacement vertical SAFE et du module de reaction du sol.
 */
class SoilBearingStressVerifier {

    fun verify(input: SoilBearingVerificationInput): SoilBearingVerificationResult {
        val stress = abs(input.settlementMeters) * input.subgradeModulusKNM3
        val utilization = if (input.allowableStressKNM2 <= 0.0) 0.0 else stress / input.allowableStressKNM2
        val minimumWidth = requiredWidthMeters(
            axialServiceKN = input.axialServiceKN,
            footingLengthMeters = input.footingLengthMeters,
            allowableStressKNM2 = input.allowableStressKNM2
        )
        val warnings = mutableListOf<String>()
        if (input.upliftMeters > 1e-6) {
            warnings += "Soulèvement de ${Checks.format(input.upliftMeters * 1000.0, 2)} mm : " +
                "la semelle n'est pas entièrement comprimée."
        }
        if (minimumWidth > 0.0 && input.providedWidthMeters != null &&
            input.providedWidthMeters + 1e-6 < minimumWidth
        ) {
            warnings += "Largeur fournie ${Checks.format(input.providedWidthMeters, 2)} m " +
                "inférieure à B_min = ${Checks.format(minimumWidth, 2)} m."
        }

        val checks = mutableListOf<DesignCheck>()
        checks += Checks.max(
            code = "SOL13.1_PORTANCE",
            label = "Contrainte effective du sol",
            reference = "DTR BC 2.33.1, modèle de Winkler",
            provided = stress,
            maximum = input.allowableStressKNM2,
            unit = "kN/m2",
            decimals = 2,
            failureMessage = "σ_eff = ${Checks.format(stress, 2)} kN/m2 > " +
                "σ̄ = ${Checks.format(input.allowableStressKNM2, 2)} kN/m2 : augmenter la largeur de semelle."
        )
        checks += Checks.boolean(
            code = "SOL13.2_SOULEVEMENT",
            label = "Absence de soulèvement",
            reference = "DTR BC 2.33.1",
            condition = input.upliftMeters <= 1e-6,
            required = "Δ_soulèvement = 0 mm",
            provided = "${Checks.format(input.upliftMeters * 1000.0, 2)} mm",
            failureStatus = CheckStatus.WARNING,
            message = "Soulèvement local : vérifier la stabilité au renversement et les armatures de traction."
        )
        if (input.providedWidthMeters != null) {
            checks += Checks.min(
                code = "SOL13.3_LARGEUR",
                label = "Largeur de semelle",
                reference = "DTR BC 2.33.1, portance",
                provided = input.providedWidthMeters,
                minimum = minimumWidth,
                unit = "m",
                decimals = 3,
                failureMessage = "B = ${Checks.format(input.providedWidthMeters, 2)} m < " +
                    "B_min = ${Checks.format(minimumWidth, 2)} m."
            )
        } else {
            checks += Checks.info(
                code = "SOL13.3_LARGEUR",
                label = "Largeur minimale requise",
                reference = "DTR BC 2.33.1, portance",
                value = "B_min = ${Checks.format(minimumWidth, 3)} m"
            )
        }
        checks += Checks.info(
            code = "SOL13.4_KS",
            label = "Module de réaction du sol",
            reference = "Essais géotechniques / SAFE",
            value = "Ks = ${Checks.format(input.subgradeModulusKNM3, 0)} kN/m3, " +
                "Df = ${Checks.format(input.foundationDepthMeters, 2)} m"
        )

        return SoilBearingVerificationResult(
            input = input,
            effectiveStressKNM2 = stress,
            effectiveStressBars = stress / 100.0,
            utilization = utilization,
            minimumWidthMeters = minimumWidth,
            checks = checks,
            warnings = warnings
        )
    }

    fun verify(
        envelope: VerticalDisplacementEnvelope,
        spring: LineSpringFamily,
        geotechnical: FoundationGeotechnicalParameters,
        axialServiceKN: Double,
        footingLengthMeters: Double,
        providedWidthMeters: Double? = null
    ): SoilBearingVerificationResult = verify(
        SoilBearingVerificationInput(
            settlementMeters = envelope.settlementMeters,
            subgradeModulusKNM3 = spring.subgradeModulusKNM3,
            allowableStressKNM2 = geotechnical.allowableStressKNM2,
            allowableStressBars = geotechnical.allowableStressBars,
            axialServiceKN = axialServiceKN,
            footingLengthMeters = footingLengthMeters,
            providedWidthMeters = providedWidthMeters,
            upliftMeters = envelope.upliftMeters,
            foundationDepthMeters = geotechnical.foundationDepthMeters
        )
    )

    /** B_min = N_ser,total / (L . sigma_admissible), borne a zero sans effort vertical. */
    fun requiredWidthMeters(
        axialServiceKN: Double,
        footingLengthMeters: Double,
        allowableStressKNM2: Double
    ): Double {
        if (axialServiceKN <= 0.0 || footingLengthMeters <= 0.0 || allowableStressKNM2 <= 0.0) return 0.0
        return max(0.0, axialServiceKN / (footingLengthMeters * allowableStressKNM2))
    }
}
