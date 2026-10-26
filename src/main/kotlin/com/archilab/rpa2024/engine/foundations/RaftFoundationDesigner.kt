package com.archilab.rpa2024.engine.foundations

import com.archilab.rpa2024.engine.frames.BaelRInput
import com.archilab.rpa2024.engine.frames.BaelRectangularSolver
import com.archilab.rpa2024.engine.frames.DesignSituation
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Données d'entrée pour le dimensionnement d'un radier général selon RPA 2024 / CBA 93 / BAEL 91.
 */
@Serializable
data class RaftInput(
    val totalBuildingLoadNserKN: Double,
    val totalBuildingLoadNuKN: Double,
    val footprintAreaM2: Double,
    val maxSpanLxMeters: Double,
    val maxSpanLyMeters: Double,
    val maxColumnLoadNuKN: Double,
    val criticalColumnB1Cm: Double = 40.0,
    val criticalColumnH1Cm: Double = 50.0,
    val soilAllowableCapacityBars: Double = 2.0,
    val subgradeModulusKsKNM3: Double = 40000.0,
    val fc28MPa: Double = 25.0,
    val feMPa: Double = 500.0
)

/**
 * Caractéristiques géométriques et de rigidité du radier.
 */
@Serializable
data class RaftGeometry(
    val overhangMeters: Double,
    val totalRaftAreaM2: Double,
    val slabThicknessHMeters: Double,
    val ribHeightHMeters: Double,
    val elasticLengthLeMeters: Double,
    val isRigidityVerified: Boolean,
    val rigidityObservation: String
)

/**
 * Rapport de vérification du poinçonnement du radier sous le poteau le plus chargé.
 */
@Serializable
data class RaftPunchingShearCheck(
    val columnLoadNuKN: Double,
    val controlPerimeterUcMeters: Double,
    val punchingLimitKN: Double,
    val ratioNuOverLimit: Double,
    val isPunchingCompliant: Boolean,
    val observation: String
)

/**
 * Rapport de vérification géotechnique des pressions transmises au sol sous radier.
 */
@Serializable
data class RaftSoilPressureCheck(
    val raftSelfWeightKN: Double,
    val totalLoadAtBaseSerKN: Double,
    val meanSoilStressKNM2: Double,
    val meanSoilStressBars: Double,
    val allowableCapacityBars: Double,
    val safetyMarginPercent: Double,
    val isSoilPressureCompliant: Boolean,
    val observation: String
)

/**
 * Ferraillage par mètre linéaire en nappes inférieure et supérieure du radier.
 */
@Serializable
data class RaftReinforcementDesign(
    val momentSpanMxKNmPerM: Double,
    val momentSpanMyKNmPerM: Double,
    val momentSupportMxKNmPerM: Double,
    val momentSupportMyKNmPerM: Double,
    val bottomRebarXDesc: String,
    val bottomRebarXAreaCm2PerM: Double,
    val bottomRebarYDesc: String,
    val bottomRebarYAreaCm2PerM: Double,
    val topRebarXDesc: String,
    val topRebarXAreaCm2PerM: Double,
    val topRebarYDesc: String,
    val topRebarYAreaCm2PerM: Double,
    val minFragilityAreaCm2PerM: Double
)

/**
 * Bilan complet d'homologation d'un radier général.
 */
@Serializable
data class RaftFullDesignReport(
    val geometry: RaftGeometry,
    val punchingCheck: RaftPunchingShearCheck,
    val soilCheck: RaftSoilPressureCheck,
    val reinforcement: RaftReinforcementDesign,
    val isGloballyCompliant: Boolean,
    val summaryRecommendation: String
)

/**
 * Concepteur expert de fondations superficielles par radier général selon RPA 2024, CBA 93 et BAEL 91 mod. 99.
 * S'active automatiquement lorsque l'emprise des semelles filantes dépasse 50% de la surface du bâtiment.
 */
class RaftFoundationDesigner(
    private val baelSolver: BaelRectangularSolver = BaelRectangularSolver()
) {

    /**
     * Dimensionne le radier complet (géométrie, poinçonnement, contrainte au sol et ferraillage des dalles).
     */
    fun designRaft(input: RaftInput): RaftFullDesignReport {
        val lx = min(input.maxSpanLxMeters, input.maxSpanLyMeters)
        val ly = max(input.maxSpanLxMeters, input.maxSpanLyMeters)

        // 1. Pré-dimensionnement de l'épaisseur du radier
        // Condition de coffrage : h >= Lmax / 10 pour radier nervuré, h >= 40 cm
        val rawH = max(0.40, ly / 10.0)
        val slabH = ceil(rawH * 20.0) / 20.0 // arrondi aux 5 cm supérieurs (ex: 0.50m)
        val ribH = ceil((ly / 8.0) * 20.0) / 20.0 // hauteur des nervures (ex: 0.70m)

        // Débord réglementaire : Ld >= max(0.50m, h/2)
        val overhang = max(0.50, slabH / 2.0)
        val perimeterFootprintEstimate = 2.0 * (sqrt(input.footprintAreaM2) * 2.0)
        val extraAreaFromOverhang = perimeterFootprintEstimate * overhang + PI * overhang * overhang
        val totalRaftArea = input.footprintAreaM2 + extraAreaFromOverhang

        // 2. Condition de rigidité du radier (longueur élastique Le)
        // Le = ((4 * E * I) / (Ks * b))^(1/4)
        val fc28 = input.fc28MPa
        val eConcrete = 11000.0 * fc28.pow(1.0 / 3.0) * 1000.0 // kPa
        val bUnit = 1.0 // m
        val inertiaI = (bUnit * slabH.pow(3.0)) / 12.0
        val ks = input.subgradeModulusKsKNM3
        val elasticLengthLe = ((4.0 * eConcrete * inertiaI) / (ks * bUnit)).pow(0.25)

        val maxAllowedSpanForRigid = (PI / 2.0) * elasticLengthLe
        val isRigid = ly <= maxAllowedSpanForRigid
        val rigidityObs = if (isRigid) {
            "Radier rigide vérifié : Lmax = ${roundTo2(ly)}m <= pi/2 * Le (${roundTo2(maxAllowedSpanForRigid)}m)"
        } else {
            "Radier semi-souple : Lmax (${roundTo2(ly)}m) > pi/2 * Le (${roundTo2(maxAllowedSpanForRigid)}m) - Présence de nervures requise"
        }

        val geometry = RaftGeometry(
            overhangMeters = roundTo2(overhang),
            totalRaftAreaM2 = roundTo2(totalRaftArea),
            slabThicknessHMeters = roundTo2(slabH),
            ribHeightHMeters = roundTo2(ribH),
            elasticLengthLeMeters = roundTo2(elasticLengthLe),
            isRigidityVerified = isRigid,
            rigidityObservation = rigidityObs
        )

        // 3. Vérification du non-poinçonnement (BAEL 91 Art. A.5.2.4 & CBA 93)
        // Nu <= 0.045 * uc * h * fc28 / gamma_b
        val aCol = input.criticalColumnB1Cm / 100.0
        val bCol = input.criticalColumnH1Cm / 100.0
        val dSlab = slabH - 0.05 // enrobage 5 cm
        val uc = 2.0 * ((aCol + slabH) + (bCol + slabH)) // contour de cisaillement à h/2
        val gammaB = 1.50
        val punchingLimitKN = (0.045 * uc * slabH * (fc28 * 1000.0)) / gammaB
        val nuCol = input.maxColumnLoadNuKN
        val isPunchingOk = nuCol <= punchingLimitKN
        val ratioPunching = if (punchingLimitKN > 0.0) nuCol / punchingLimitKN else 0.0

        val punchingCheck = RaftPunchingShearCheck(
            columnLoadNuKN = roundTo2(nuCol),
            controlPerimeterUcMeters = roundTo2(uc),
            punchingLimitKN = roundTo2(punchingLimitKN),
            ratioNuOverLimit = roundTo2(ratioPunching),
            isPunchingCompliant = isPunchingOk,
            observation = if (isPunchingOk) {
                "Non-poinçonnement vérifié : Nu = ${roundTo2(nuCol)} kN <= Nu,lim = ${roundTo2(punchingLimitKN)} kN"
            } else {
                "Risque de poinçonnement : prévoir un chapiteau ou augmenter l'épaisseur du radier"
            }
        )

        // 4. Vérification géotechnique des contraintes du sol
        // Poids propre du radier : G_radier = S_radier * h * 25 kN/m³
        val raftWeightKN = totalRaftArea * slabH * 25.0
        val totalSerAtBase = input.totalBuildingLoadNserKN + raftWeightKN
        val meanStressKNM2 = totalSerAtBase / totalRaftArea
        val meanStressBars = meanStressKNM2 / 100.0
        val allowableBars = input.soilAllowableCapacityBars
        val isSoilOk = meanStressBars <= allowableBars
        val margin = if (allowableBars > 0) ((allowableBars - meanStressBars) / allowableBars) * 100.0 else 0.0

        val soilCheck = RaftSoilPressureCheck(
            raftSelfWeightKN = roundTo2(raftWeightKN),
            totalLoadAtBaseSerKN = roundTo2(totalSerAtBase),
            meanSoilStressKNM2 = roundTo2(meanStressKNM2),
            meanSoilStressBars = roundTo2(meanStressBars),
            allowableCapacityBars = allowableBars,
            safetyMarginPercent = roundTo1(margin),
            isSoilPressureCompliant = isSoilOk,
            observation = if (isSoilOk) {
                "Contrainte admissible du sol respectée : sigma = ${roundTo2(meanStressBars)} bars <= sigma_adm = ${roundTo2(allowableBars)} bars (Marge ${roundTo1(margin)}%)"
            } else {
                "Dépassement de la contrainte admissible du sol sous radier : augmenter le débord du radier"
            }
        )

        // 5. Calcul des moments et ferraillage des panneaux de dalle
        // Pression ultime de réaction sous radier : qu = Nu_total / S_radier
        val totalNuAtBase = input.totalBuildingLoadNuKN + (raftWeightKN * 1.35)
        val quKNM2 = totalNuAtBase / totalRaftArea

        val rho = lx / ly
        val muX = 1.0 / (8.0 * (1.0 + 2.4 * rho.pow(3.0)))
        val muY = rho.pow(3.0) * muX

        val m0x = muX * quKNM2 * lx.pow(2.0)
        val m0y = muY * m0x

        // Moment de travée (nappe inférieure) et moment sur appuis/poteaux (nappe supérieure)
        val mSpanX = 0.75 * m0x
        val mSpanY = 0.75 * m0y
        val mSupportX = 0.50 * m0x
        val mSupportY = 0.50 * m0y

        // Calcul des sections d'acier par mètre linéaire
        val ft28 = 0.6 + 0.06 * fc28
        val minFragilityCm2PerM = 0.23 * 1.0 * dSlab * (ft28 / input.feMPa) * 10000.0

        val resSpanX = baelSolver.solve(
            BaelRInput(
                widthBMeters = 1.0,
                heightHMeters = slabH,
                dPrimeMeters = 0.05,
                cMeters = 0.05,
                fc28MPa = fc28,
                feMPa = input.feMPa,
                axialForceNKN = 0.0,
                momentMKNm = mSpanX,
                situation = DesignSituation.DURABLE_ELU
            )
        )
        val asSpanX = max(minFragilityCm2PerM, resSpanX.aInfCm2)

        val resSpanY = baelSolver.solve(
            BaelRInput(
                widthBMeters = 1.0,
                heightHMeters = slabH,
                dPrimeMeters = 0.05,
                cMeters = 0.05,
                fc28MPa = fc28,
                feMPa = input.feMPa,
                axialForceNKN = 0.0,
                momentMKNm = mSpanY,
                situation = DesignSituation.DURABLE_ELU
            )
        )
        val asSpanY = max(minFragilityCm2PerM, resSpanY.aInfCm2)

        val resSuppX = baelSolver.solve(
            BaelRInput(
                widthBMeters = 1.0,
                heightHMeters = slabH,
                dPrimeMeters = 0.05,
                cMeters = 0.05,
                fc28MPa = fc28,
                feMPa = input.feMPa,
                axialForceNKN = 0.0,
                momentMKNm = mSupportX,
                situation = DesignSituation.DURABLE_ELU
            )
        )
        val asSuppX = max(minFragilityCm2PerM, resSuppX.aInfCm2)

        val resSuppY = baelSolver.solve(
            BaelRInput(
                widthBMeters = 1.0,
                heightHMeters = slabH,
                dPrimeMeters = 0.05,
                cMeters = 0.05,
                fc28MPa = fc28,
                feMPa = input.feMPa,
                axialForceNKN = 0.0,
                momentMKNm = mSupportY,
                situation = DesignSituation.DURABLE_ELU
            )
        )
        val asSuppY = max(minFragilityCm2PerM, resSuppY.aInfCm2)

        val reinforcement = RaftReinforcementDesign(
            momentSpanMxKNmPerM = roundTo2(mSpanX),
            momentSpanMyKNmPerM = roundTo2(mSpanY),
            momentSupportMxKNmPerM = roundTo2(mSupportX),
            momentSupportMyKNmPerM = roundTo2(mSupportY),
            bottomRebarXDesc = chooseSlabRebars(asSpanX),
            bottomRebarXAreaCm2PerM = roundTo2(asSpanX),
            bottomRebarYDesc = chooseSlabRebars(asSpanY),
            bottomRebarYAreaCm2PerM = roundTo2(asSpanY),
            topRebarXDesc = chooseSlabRebars(asSuppX),
            topRebarXAreaCm2PerM = roundTo2(asSuppX),
            topRebarYDesc = chooseSlabRebars(asSuppY),
            topRebarYAreaCm2PerM = roundTo2(asSuppY),
            minFragilityAreaCm2PerM = roundTo2(minFragilityCm2PerM)
        )

        val isGlobalOk = isPunchingOk && isSoilOk

        val recommendation = if (isGlobalOk) {
            "Radier général conforme au RPA 2024 et CBA 93. Épaisseur adoptée h = ${(slabH * 100).toInt()} cm, débord = ${(overhang * 100).toInt()} cm."
        } else {
            "Ajustements de coffrage nécessaires pour satisfaire toutes les vérifications réglementaires du radier."
        }

        return RaftFullDesignReport(
            geometry = geometry,
            punchingCheck = punchingCheck,
            soilCheck = soilCheck,
            reinforcement = reinforcement,
            isGloballyCompliant = isGlobalOk,
            summaryRecommendation = recommendation
        )
    }

    /**
     * Rendu graphique vectoriel SVG de la coupe d'exécution d'un radier général.
     */
    fun renderRaftCrossSectionSvg(
        report: RaftFullDesignReport,
        svgWidth: Int = 700,
        svgHeight: Int = 380
    ): String {
        val h = report.geometry.slabThicknessHMeters
        val overhang = report.geometry.overhangMeters
        val asBot = report.reinforcement.bottomRebarXDesc
        val asTop = report.reinforcement.topRebarXDesc
        val sigma = report.soilCheck.meanSoilStressBars

        val sb = StringBuilder()
        sb.appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"100%\" height=\"100%\">")
        sb.appendLine("  <rect width=\"$svgWidth\" height=\"$svgHeight\" fill=\"#ffffff\" rx=\"8\" stroke=\"#cbd5e1\" stroke-width=\"1.5\"/>")

        // Titre
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"30\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"15\" font-weight=\"bold\" fill=\"#0f172a\">")
        sb.appendLine("    Coupe Transversale d'Exécution : Radier Général en Béton Armé (RPA 2024 / CBA 93)")
        sb.appendLine("  </text>")
        sb.appendLine("  <text x=\"${svgWidth / 2}\" y=\"50\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"12\" fill=\"#475569\">")
        sb.appendLine("    Épaisseur h = ${(h * 100).toInt()} cm | Débord = ${(overhang * 100).toInt()} cm | Contrainte au sol = $sigma bars")
        sb.appendLine("  </text>")

        // Sol sous radier (hachures terre)
        sb.appendLine("  <rect x=\"50\" y=\"240\" width=\"600\" height=\"80\" fill=\"#f8fafc\" stroke=\"#94a3b8\" stroke-dasharray=\"4,4\"/>")
        sb.appendLine("  <text x=\"350\" y=\"285\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" fill=\"#64748b\">Réaction du Sol de Fondation (Winkler Ks = 40 000 kN/m³)</text>")

        // Dalle du radier
        sb.appendLine("  <rect x=\"80\" y=\"140\" width=\"540\" height=\"100\" fill=\"#e2e8f0\" stroke=\"#334155\" stroke-width=\"2\" rx=\"2\"/>")

        // Poteaux reposant sur le radier
        sb.appendLine("  <rect x=\"170\" y=\"60\" width=\"50\" height=\"80\" fill=\"#cbd5e1\" stroke=\"#1e293b\" stroke-width=\"2\"/>")
        sb.appendLine("  <rect x=\"480\" y=\"60\" width=\"50\" height=\"80\" fill=\"#cbd5e1\" stroke=\"#1e293b\" stroke-width=\"2\"/>")
        sb.appendLine("  <text x=\"195\" y=\"105\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#0f172a\">POT 1</text>")
        sb.appendLine("  <text x=\"505\" y=\"105\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#0f172a\">POT 2</text>")

        // Nappe supérieure d'armatures (rouge)
        sb.appendLine("  <line x1=\"95\" y1=\"155\" x2=\"605\" y2=\"155\" stroke=\"#dc2626\" stroke-width=\"2.5\"/>")
        sb.appendLine("  <text x=\"350\" y=\"150\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#dc2626\">Nappe Supérieure : $asTop</text>")

        // Nappe inférieure d'armatures (bleu)
        sb.appendLine("  <line x1=\"95\" y1=\"225\" x2=\"605\" y2=\"225\" stroke=\"#2563eb\" stroke-width=\"2.5\"/>")
        sb.appendLine("  <text x=\"350\" y=\"220\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" font-weight=\"bold\" fill=\"#2563eb\">Nappe Inférieure : $asBot</text>")

        // Débord
        sb.appendLine("  <line x1=\"80\" y1=\"130\" x2=\"170\" y2=\"130\" stroke=\"#047857\" stroke-width=\"1.5\" marker-start=\"url(#arrow)\" marker-end=\"url(#arrow)\"/>")
        sb.appendLine("  <text x=\"125\" y=\"125\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"10\" fill=\"#047857\">Débord ${(overhang * 100).toInt()}cm</text>")

        // Badge de conformité
        val badgeColor = if (report.isGloballyCompliant) "#16a34a" else "#dc2626"
        val badgeText = if (report.isGloballyCompliant) "RADIER CONFORME CTC" else "RESERVE CTC"
        sb.appendLine("  <rect x=\"270\" y=\"335\" width=\"160\" height=\"28\" rx=\"14\" fill=\"$badgeColor\"/>")
        sb.appendLine("  <text x=\"350\" y=\"353\" text-anchor=\"middle\" font-family=\"Arial, sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#ffffff\">$badgeText</text>")

        sb.appendLine("</svg>")
        return sb.toString()
    }

    private fun chooseSlabRebars(areaCm2PerM: Double): String {
        val options = listOf(
            Pair("T12 e=15cm", 7.54),
            Pair("T14 e=15cm", 10.26),
            Pair("T14 e=12.5cm", 12.32),
            Pair("T16 e=15cm", 13.40),
            Pair("T16 e=12.5cm", 16.08),
            Pair("T20 e=15cm", 20.94),
            Pair("T20 e=12.5cm", 25.13)
        )
        val match = options.find { it.second >= areaCm2PerM } ?: options.last()
        return "${match.first} (${match.second} cm²/ml)"
    }

    private fun roundTo1(v: Double): Double = round(v * 10.0) / 10.0
    private fun roundTo2(v: Double): Double = round(v * 100.0) / 100.0
}
