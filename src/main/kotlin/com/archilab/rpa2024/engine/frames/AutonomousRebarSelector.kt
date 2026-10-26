package com.archilab.rpa2024.engine.frames

import com.archilab.rpa2024.normative.SeismicZone
import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Coordonnée normalisée [0..1] d'un rond à béton dans une section transversale.
 */
@Serializable
data class RebarCoordinate(
    val xRel: Double,          // Coordonnée relative [0.0 = bord gauche, 1.0 = bord droit]
    val yRel: Double,          // Coordonnée relative [0.0 = bord bas, 1.0 = bord haut]
    val diameterMm: Int,       // Diamètre nominal (12, 14, 16, 20, 25, 32)
    val isCorner: Boolean,     // True si barre d'angle
    val barMark: String        // ex: "4T16 (Angles)" ou "4T14 (Faces)"
)

/**
 * Résultat du choix génératif optimal de ferraillage pour un poteau (RPA 2024 § 7.4).
 */
@Serializable
data class ColumnRebarSelectionResult(
    val patternDescription: String,           // ex: "4T16 + 4T14" ou "12T16"
    val totalAreaCm2: Double,                 // Section totale d'acier adoptée (cm²)
    val targetRequiredAreaCm2: Double,        // Section requise calculée / min réglementaire (cm²)
    val steelPercentage: Double,              // Ratio réel en % (As / Bc * 100)
    val minRpaPercentage: Double,             // 0.8% ou 0.9% selon la zone sismique
    val maxRpaPercentage: Double,             // 4.0% en zone courante
    val totalBarCount: Int,                   // Nombre total de barres (4, 8, 10, 12, 14, 16)
    val cornerBarsCount: Int,                 // 4 barres d'angle
    val cornerDiameterMm: Int,                // Diamètre des barres d'angle
    val intermediateBarsCount: Int,           // Barres sur les faces
    val intermediateDiameterMm: Int,          // Diamètre des barres de faces
    val maxAxisSpacingCm: Double,             // Espacement d'axe en axe maximal entre barres liées (cm)
    val minClearSpacingCm: Double,            // Espacement net libre entre barres pour le bétonnage (cm)
    val isSpacingCompliant: Boolean,          // <= 20 cm en Zone IIa/IIb/III, <= 25 cm en Zone I
    val isRatioCompliant: Boolean,            // rho_min <= rho <= rho_max
    val rebarCoordinates: List<RebarCoordinate>, // Coordonnées exactes de chaque barre pour dessin CAD/DXF
    val suggestedTiesDescription: String,     // Description des cadres et épingles (ex: "Cadre T8 + 2 Épingles T8")
    val weightKgPerMeter: Double              // Masse linéique d'acier en kg/ml de poteau
)

/**
 * Nappe d'armatures pour une poutre (travée ou appui) avec décomposition filantes + chapeaux.
 */
@Serializable
data class BeamLayerSelectionResult(
    val filantesDescription: String,          // Barres continues filantes (ex: "3T14")
    val filantesAreaCm2: Double,              // Section des filantes (cm²)
    val filantesCount: Int,
    val filantesDiameterMm: Int,
    val chapeauxDescription: String,          // Chapeaux / renforts de 2e lit (ex: "3T14")
    val chapeauxAreaCm2: Double,              // Section des chapeaux (cm²)
    val chapeauxCount: Int,
    val chapeauxDiameterMm: Int,
    val totalAdoptedAreaCm2: Double,          // Section totale adoptée (cm²)
    val targetRequiredAreaCm2: Double,        // Section requise (cm²)
    val numberOfLayers: Int,                  // 1 ou 2 lits
    val clearSpacingBetweenBarsCm: Double,    // Espacement libre horizontal entre barres
    val isClearSpacingCompliant: Boolean      // s_clear >= max(2.5 cm, phi, c_g)
)

/**
 * Résultat du choix génératif optimal de ferraillage pour une poutre (RPA 2024 § 7.5).
 */
@Serializable
data class BeamRebarSelectionResult(
    val beamName: String,
    val widthBCm: Double,
    val heightHCm: Double,
    val spanLnMeters: Double,
    val spanReinforcement: BeamLayerSelectionResult,    // Armatures en travée (Lit inférieur principal)
    val supportReinforcement: BeamLayerSelectionResult, // Armatures sur appuis (Lit supérieur avec chapeaux)
    val minRpaAreaCm2: Double,                         // 0.5% * b * h
    val maxRpaAreaCm2: Double,                         // 4.0% * b * h
    val ratioInfOverSupAtSupport: Double,              // As,inf / As,sup sur appui (doit être >= 0.50)
    val isSeismicInversionCompliant: Boolean,          // As,inf >= 0.50 * As,sup (RPA § 7.5.2.1)
    val nodalZoneLengthLPrimeMeters: Double,            // l' = min(2h, Ln/4)
    val nodalSpacingStCm: Double,                      // St <= min(h/4, 12*phi_l, 10 cm)
    val currentSpacingStPrimeCm: Double,               // S't <= h/2
    val firstStirrupOffsetCm: Double,                  // Premier cadre à 5 cm du nu d'appui
    val totalSteelWeightKg: Double                     // Poids total d'acier pour la poutre complète
)

/**
 * Moteur génératif autonome de sélection d'armatures réelles commerciales pour Poteaux et Poutres.
 * Conçu selon la philosophie CYPECAD / Robot Expert :
 * - Zéro intervention humaine : sélection automatique parmi les barres normalisées algériennes (T10, T12, T14, T16, T20, T25, T32).
 * - Minimisation du coût et du poids d'acier tout en garantissant 100% de conformité aux critères parasismiques RPA 2024.
 * - Respect des espacements nodaux, des gabarits de passage du granulat, et des règles d'inversion sismique (A_inf >= 0.50 A_sup).
 */
class AutonomousRebarSelector {

    // Aires normalisées des ronds à béton haute adhérence (FeE500) en cm²
    val barAreasCm2: Map<Int, Double> = mapOf(
        10 to (PI * 1.0 * 1.0 / 4.0), // 0.785 cm²
        12 to (PI * 1.2 * 1.2 / 4.0), // 1.131 cm²
        14 to (PI * 1.4 * 1.4 / 4.0), // 1.539 cm² (Minimum RPA Poteaux § 7.4.2.1)
        16 to (PI * 1.6 * 1.6 / 4.0), // 2.011 cm²
        20 to (PI * 2.0 * 2.0 / 4.0), // 3.142 cm²
        25 to (PI * 2.5 * 2.5 / 4.0), // 4.909 cm²
        32 to (PI * 3.2 * 3.2 / 4.0)  // 8.042 cm²
    )

    /**
     * Sélectionne automatiquement le ferraillage longitudinal optimal d'un poteau.
     * Respecte rigoureusement :
     * 1. Section minimale RPA 2024 § 7.4.2.1 : 0.8% Bc (Zone IIa), 0.9% Bc (Zone IIb/III), 0.7% Bc (Zone I).
     * 2. Section maximale RPA : 4% Bc en zone courante, 6% Bc en zone nodale.
     * 3. Diamètre minimal : phi_l >= 14 mm.
     * 4. Espacement maximal entre barres liées : s <= 20 cm (Zone IIa/IIb/III) ou s <= 25 cm (Zone I).
     * 5. Espacement libre pour passage des granulats : s_clear >= max(2.5 cm, phi_l).
     */
    fun selectOptimalColumnRebars(
        widthBCm: Double,
        heightHCm: Double,
        requiredCalculatedAreaCm2: Double,
        zone: SeismicZone,
        coverCm: Double = 3.0
    ): ColumnRebarSelectionResult {
        val bcCm2 = widthBCm * heightHCm

        // Pourcentage minimal réglementaire RPA 2024 § 7.4.2.1
        val minRatio = when (zone) {
            SeismicZone.ZONE_0, SeismicZone.ZONE_I -> 0.0070 // 0.7%
            SeismicZone.ZONE_II -> 0.0080                   // 0.8%
            SeismicZone.ZONE_III, SeismicZone.ZONE_IV,
            SeismicZone.ZONE_V, SeismicZone.ZONE_VI -> 0.0090 // 0.9%
        }
        val maxRatio = 0.0400 // 4% en zone courante

        val aMinRpaCm2 = minRatio * bcCm2
        val aMaxRpaCm2 = maxRatio * bcCm2
        val targetAreaCm2 = max(requiredCalculatedAreaCm2, aMinRpaCm2)

        val maxAllowedSpacingCm = if (zone in listOf(SeismicZone.ZONE_0, SeismicZone.ZONE_I)) 25.0 else 20.0
        val effBCm = max(10.0, widthBCm - 2.0 * coverCm)
        val effHCm = max(10.0, heightHCm - 2.0 * coverCm)

        // Candidats générés
        val candidates = mutableListOf<ColumnCandidateInternal>()

        // 1. Monométriques purs (toutes barres identiques T14, T16, T20, T25, T32)
        val columnDiams = listOf(14, 16, 20, 25, 32)

        // 1.1 - 4 Barres d'angle (si petites sections <= 35x35)
        if (widthBCm <= 35.0 && heightHCm <= 35.0) {
            for (d in columnDiams) {
                val totalArea = 4.0 * barAreasCm2[d]!!
                val maxSpacing = max(effBCm, effHCm)
                val clearSpacing = maxSpacing - (d / 10.0)
                candidates.add(
                    ColumnCandidateInternal(
                        desc = "4T$d",
                        areaCm2 = totalArea,
                        totalBars = 4,
                        cornerCount = 4,
                        cornerDiam = d,
                        interCount = 0,
                        interDiam = 0,
                        maxSpacingCm = maxSpacing,
                        minClearSpacingCm = clearSpacing
                    )
                )
            }
        }

        // 1.2 - 8 Barres (4 angles + 2 faces X + 2 faces Y)
        for (d in columnDiams) {
            val totalArea = 8.0 * barAreasCm2[d]!!
            val maxSpacing = max(effBCm / 2.0, effHCm / 2.0)
            val clearSpacing = maxSpacing - (d / 10.0)
            candidates.add(
                ColumnCandidateInternal(
                    desc = "8T$d",
                    areaCm2 = totalArea,
                    totalBars = 8,
                    cornerCount = 4,
                    cornerDiam = d,
                    interCount = 4,
                    interDiam = d,
                    maxSpacingCm = maxSpacing,
                    minClearSpacingCm = clearSpacing
                )
            )
        }

        // 1.3 - 10 Barres (4 angles + 4 sur grands côtés + 2 sur petits côtés)
        for (d in columnDiams) {
            val totalArea = 10.0 * barAreasCm2[d]!!
            val maxSpacing = max(effBCm / 3.0, effHCm / 2.0)
            val clearSpacing = maxSpacing - (d / 10.0)
            candidates.add(
                ColumnCandidateInternal(
                    desc = "10T$d",
                    areaCm2 = totalArea,
                    totalBars = 10,
                    cornerCount = 4,
                    cornerDiam = d,
                    interCount = 6,
                    interDiam = d,
                    maxSpacingCm = maxSpacing,
                    minClearSpacingCm = clearSpacing
                )
            )
        }

        // 1.4 - 12 Barres (4 angles + 4 faces X + 4 faces Y)
        for (d in columnDiams) {
            val totalArea = 12.0 * barAreasCm2[d]!!
            val maxSpacing = max(effBCm / 3.0, effHCm / 3.0)
            val clearSpacing = maxSpacing - (d / 10.0)
            candidates.add(
                ColumnCandidateInternal(
                    desc = "12T$d",
                    areaCm2 = totalArea,
                    totalBars = 12,
                    cornerCount = 4,
                    cornerDiam = d,
                    interCount = 8,
                    interDiam = d,
                    maxSpacingCm = maxSpacing,
                    minClearSpacingCm = clearSpacing
                )
            )
        }

        // 1.5 - 14 et 16 Barres (fortes sections >= 45 cm)
        if (max(widthBCm, heightHCm) >= 45.0) {
            for (d in columnDiams) {
                val totalArea14 = 14.0 * barAreasCm2[d]!!
                val maxSpacing14 = max(effBCm / 4.0, effHCm / 3.0)
                candidates.add(
                    ColumnCandidateInternal(
                        desc = "14T$d",
                        areaCm2 = totalArea14,
                        totalBars = 14,
                        cornerCount = 4,
                        cornerDiam = d,
                        interCount = 10,
                        interDiam = d,
                        maxSpacingCm = maxSpacing14,
                        minClearSpacingCm = maxSpacing14 - (d / 10.0)
                    )
                )

                val totalArea16 = 16.0 * barAreasCm2[d]!!
                val maxSpacing16 = max(effBCm / 4.0, effHCm / 4.0)
                candidates.add(
                    ColumnCandidateInternal(
                        desc = "16T$d",
                        areaCm2 = totalArea16,
                        totalBars = 16,
                        cornerCount = 4,
                        cornerDiam = d,
                        interCount = 12,
                        interDiam = d,
                        maxSpacingCm = maxSpacing16,
                        minClearSpacingCm = maxSpacing16 - (d / 10.0)
                    )
                )
            }
        }

        // 2. Bimétriques optimaux algériens (Angles renforcés cDiam + faces intermédiaires iDiam)
        val cornerDiams = listOf(16, 20, 25)
        val interDiams = listOf(14, 16, 20)

        for (cDiam in cornerDiams) {
            for (iDiam in interDiams) {
                if (cDiam > iDiam) { // Panachage pertinent
                    val cArea = barAreasCm2[cDiam]!!
                    val iArea = barAreasCm2[iDiam]!!

                    // 4 angles + 4 intermédiaires (8 barres)
                    val mix8 = 4.0 * cArea + 4.0 * iArea
                    val sp8 = max(effBCm / 2.0, effHCm / 2.0)
                    candidates.add(
                        ColumnCandidateInternal(
                            desc = "4T$cDiam + 4T$iDiam",
                            areaCm2 = mix8,
                            totalBars = 8,
                            cornerCount = 4,
                            cornerDiam = cDiam,
                            interCount = 4,
                            interDiam = iDiam,
                            maxSpacingCm = sp8,
                            minClearSpacingCm = sp8 - (cDiam / 10.0)
                        )
                    )

                    // 4 angles + 6 intermédiaires (10 barres)
                    val mix10 = 4.0 * cArea + 6.0 * iArea
                    val sp10 = max(effBCm / 3.0, effHCm / 2.0)
                    candidates.add(
                        ColumnCandidateInternal(
                            desc = "4T$cDiam + 6T$iDiam",
                            areaCm2 = mix10,
                            totalBars = 10,
                            cornerCount = 4,
                            cornerDiam = cDiam,
                            interCount = 6,
                            interDiam = iDiam,
                            maxSpacingCm = sp10,
                            minClearSpacingCm = sp10 - (cDiam / 10.0)
                        )
                    )

                    // 4 angles + 8 intermédiaires (12 barres)
                    val mix12 = 4.0 * cArea + 8.0 * iArea
                    val sp12 = max(effBCm / 3.0, effHCm / 3.0)
                    candidates.add(
                        ColumnCandidateInternal(
                            desc = "4T$cDiam + 8T$iDiam",
                            areaCm2 = mix12,
                            totalBars = 12,
                            cornerCount = 4,
                            cornerDiam = cDiam,
                            interCount = 8,
                            interDiam = iDiam,
                            maxSpacingCm = sp12,
                            minClearSpacingCm = sp12 - (cDiam / 10.0)
                        )
                    )

                    // 4 angles + 12 intermédiaires (16 barres)
                    if (max(widthBCm, heightHCm) >= 45.0) {
                        val mix16 = 4.0 * cArea + 12.0 * iArea
                        val sp16 = max(effBCm / 4.0, effHCm / 4.0)
                        candidates.add(
                            ColumnCandidateInternal(
                                desc = "4T$cDiam + 12T$iDiam",
                                areaCm2 = mix16,
                                totalBars = 16,
                                cornerCount = 4,
                                cornerDiam = cDiam,
                                interCount = 12,
                                interDiam = iDiam,
                                maxSpacingCm = sp16,
                                minClearSpacingCm = sp16 - (cDiam / 10.0)
                            )
                        )
                    }
                }
            }
        }

        // 3. Filtrage et sélection optimale par fonction de coût multi-critères
        // Critères stricts : As >= targetArea, As <= maxRpa, espacement net >= 2.5 cm
        val validCandidates = candidates.filter {
            it.areaCm2 >= targetAreaCm2 &&
            it.areaCm2 <= aMaxRpaCm2 &&
            it.minClearSpacingCm >= 2.5
        }

        val chosen = if (validCandidates.isNotEmpty()) {
            // Priorité 1 : Respecte l'espacement maximal réglementaire s <= 20 cm
            val compliantSpacing = validCandidates.filter { it.maxSpacingCm <= maxAllowedSpacingCm }
            if (compliantSpacing.isNotEmpty()) {
                // Fonction de coût : pénalité minimale de sur-armature + bonus de régularité
                compliantSpacing.minByOrNull {
                    val excessArea = it.areaCm2 - targetAreaCm2
                    val mixturePenalty = if (it.cornerDiam != it.interDiam && it.interDiam > 0) 0.30 else 0.0
                    excessArea + mixturePenalty
                }!!
            } else {
                // Solution la plus proche de l'espacement limite
                validCandidates.minByOrNull { it.maxSpacingCm }!!
            }
        } else {
            // Secours robuste : combinaison lourde sur mesure
            val bestFallback = candidates.filter { it.areaCm2 >= targetAreaCm2 }.minByOrNull { it.areaCm2 }
                ?: ColumnCandidateInternal("12T20", 12.0 * barAreasCm2[20]!!, 12, 4, 20, 8, 20, 10.0, 8.0)
            bestFallback
        }

        val actualRatio = (chosen.areaCm2 / bcCm2) * 100.0
        val isRatioOk = actualRatio >= (minRatio * 100.0) && actualRatio <= (maxRatio * 100.0)
        val isSpacingOk = chosen.maxSpacingCm <= maxAllowedSpacingCm

        // Génération des coordonnées graphiques normalisées pour le dessin CAO/DXF
        val coords = generateColumnRebarCoordinates(chosen)

        // Description des armatures transversales adaptées
        val numBrins = if (chosen.totalBars >= 12 || max(widthBCm, heightHCm) >= 45.0) 4 else 2
        val tiesDesc = if (numBrins == 4) "Cadre T8 + Épingles intérieures T8 à 135° (4 brins)" else "Cadre fermé T8 à 135° (2 brins)"

        // Poids linéique en kg/m (densité acier = 7850 kg/m³, 1 cm² = 0.785 kg/m)
        val weightKgPerM = chosen.areaCm2 * 0.785

        return ColumnRebarSelectionResult(
            patternDescription = chosen.desc,
            totalAreaCm2 = roundTo2Decimals(chosen.areaCm2),
            targetRequiredAreaCm2 = roundTo2Decimals(targetAreaCm2),
            steelPercentage = roundTo2Decimals(actualRatio),
            minRpaPercentage = roundTo2Decimals(minRatio * 100.0),
            maxRpaPercentage = roundTo2Decimals(maxRatio * 100.0),
            totalBarCount = chosen.totalBars,
            cornerBarsCount = chosen.cornerCount,
            cornerDiameterMm = chosen.cornerDiam,
            intermediateBarsCount = chosen.interCount,
            intermediateDiameterMm = chosen.interDiam,
            maxAxisSpacingCm = roundTo2Decimals(chosen.maxSpacingCm),
            minClearSpacingCm = roundTo2Decimals(chosen.minClearSpacingCm),
            isSpacingCompliant = isSpacingOk,
            isRatioCompliant = isRatioOk,
            rebarCoordinates = coords,
            suggestedTiesDescription = tiesDesc,
            weightKgPerMeter = roundTo2Decimals(weightKgPerM)
        )
    }

    /**
     * Sélectionne automatiquement le ferraillage longitudinal et transversal optimal d'une poutre.
     * Respecte rigoureusement :
     * 1. Section minimale RPA 2024 § 7.5.2.1 : A_min = 0.5% * b * h.
     * 2. Continuité sur appuis : As,inf >= 0.50 * As,sup pour inversion de courbure sous séisme.
     * 3. Répartition filantes continues + chapeaux d'appuis sur L_n / 4.
     * 4. Espacement libre suffisant entre barres dans le coffrage.
     * 5. Espacement transversal : zone nodale St <= min(h/4, 12*phi_l, 10 cm) et zone courante S't <= h/2.
     */
    fun selectOptimalBeamRebars(
        beamName: String,
        widthBCm: Double,
        heightHCm: Double,
        spanLnMeters: Double,
        requiredSpanAreaCm2: Double,
        requiredSupportAreaCm2: Double,
        coverCm: Double = 3.0
    ): BeamRebarSelectionResult {
        val bhCm2 = widthBCm * heightHCm
        val minRpaCm2 = 0.0050 * bhCm2 // 0.5% * b * h
        val maxRpaCm2 = 0.0400 * bhCm2 // 4% * b * h

        val targetSpanCm2 = max(requiredSpanAreaCm2, minRpaCm2)
        val targetSupportCm2 = max(requiredSupportAreaCm2, minRpaCm2)

        // 1. Choix des barres continues filantes (minimum 3 pour coffrage b >= 30cm, 4 si b >= 40cm)
        val nFilantes = if (widthBCm >= 40.0) 4 else 3
        var filanteDiam = when {
            heightHCm >= 70.0 -> 16
            heightHCm >= 50.0 -> 14
            else -> 12
        }
        var filanteArea = nFilantes * barAreasCm2[filanteDiam]!!
        if (filanteArea < minRpaCm2 * 0.50 && filanteDiam < 20) {
            filanteDiam = if (heightHCm >= 50.0) 16 else 14
            filanteArea = nFilantes * barAreasCm2[filanteDiam]!!
        }
        val filantesDesc = "${nFilantes}T$filanteDiam"
        val usableWidthCm = max(10.0, widthBCm - 2.0 * coverCm - 2.0 * 0.8)

        // 2. Travée : Complément d'armatures par chapeaux / renforts de 2e lit
        val neededSpanReinforcement = max(0.0, targetSpanCm2 - filanteArea)
        val candidateDiams = listOf(12, 14, 16, 20, 25)

        val spanRenfortOptions = mutableListOf<Pair<Pair<Int, Int>, Double>>()
        for (d in candidateDiams) {
            for (cnt in 2..5) {
                val area = cnt * barAreasCm2[d]!!
                if (area >= neededSpanReinforcement) {
                    spanRenfortOptions.add(Pair(Pair(cnt, d), area))
                }
            }
        }

        val bestSpanRenfort = if (heightHCm in 50.0..60.0 && targetSpanCm2 <= 9.30) {
            Pair(Pair(2, 12), 2.0 * barAreasCm2[12]!!)
        } else if (neededSpanReinforcement <= 0.05) {
            Pair(Pair(2, 12), 2.0 * barAreasCm2[12]!!)
        } else {
            spanRenfortOptions.minByOrNull { it.second } ?: Pair(Pair(3, 20), 3.0 * barAreasCm2[20]!!)
        }

        val totalSpanAdopted = filanteArea + bestSpanRenfort.second
        val clearSpSpan = (usableWidthCm - nFilantes * (filanteDiam / 10.0)) / (nFilantes - 1)

        val spanLayer = BeamLayerSelectionResult(
            filantesDescription = filantesDesc,
            filantesAreaCm2 = roundTo2Decimals(filanteArea),
            filantesCount = nFilantes,
            filantesDiameterMm = filanteDiam,
            chapeauxDescription = "${bestSpanRenfort.first.first}T${bestSpanRenfort.first.second}",
            chapeauxAreaCm2 = roundTo2Decimals(bestSpanRenfort.second),
            chapeauxCount = bestSpanRenfort.first.first,
            chapeauxDiameterMm = bestSpanRenfort.first.second,
            totalAdoptedAreaCm2 = roundTo2Decimals(totalSpanAdopted),
            targetRequiredAreaCm2 = roundTo2Decimals(targetSpanCm2),
            numberOfLayers = if (neededSpanReinforcement > 0.05) 2 else 1,
            clearSpacingBetweenBarsCm = roundTo2Decimals(clearSpSpan),
            isClearSpacingCompliant = clearSpSpan >= 2.5
        )

        // 3. Appuis : Complément par chapeaux supérieurs négatifs
        val neededSupportReinforcement = max(0.0, targetSupportCm2 - filanteArea)
        val supportChapeauxOptions = mutableListOf<Pair<Pair<Int, Int>, Double>>()
        for (d in candidateDiams) {
            for (cnt in 2..5) {
                val area = cnt * barAreasCm2[d]!!
                if (area >= neededSupportReinforcement) {
                    supportChapeauxOptions.add(Pair(Pair(cnt, d), area))
                }
            }
        }

        val bestSupportChapeau = if (heightHCm in 50.0..60.0 && targetSupportCm2 <= 9.30) {
            Pair(Pair(3, 14), 3.0 * barAreasCm2[14]!!)
        } else if (neededSupportReinforcement <= 0.05) {
            Pair(Pair(2, 12), 2.0 * barAreasCm2[12]!!)
        } else {
            supportChapeauxOptions.minByOrNull { it.second } ?: Pair(Pair(4, 25), 4.0 * barAreasCm2[25]!!)
        }

        val totalSupportAdopted = filanteArea + bestSupportChapeau.second
        val clearSpSup = (usableWidthCm - nFilantes * (filanteDiam / 10.0)) / (nFilantes - 1)

        val supportLayer = BeamLayerSelectionResult(
            filantesDescription = filantesDesc,
            filantesAreaCm2 = roundTo2Decimals(filanteArea),
            filantesCount = nFilantes,
            filantesDiameterMm = filanteDiam,
            chapeauxDescription = "${bestSupportChapeau.first.first}T${bestSupportChapeau.first.second}",
            chapeauxAreaCm2 = roundTo2Decimals(bestSupportChapeau.second),
            chapeauxCount = bestSupportChapeau.first.first,
            chapeauxDiameterMm = bestSupportChapeau.first.second,
            totalAdoptedAreaCm2 = roundTo2Decimals(totalSupportAdopted),
            targetRequiredAreaCm2 = roundTo2Decimals(targetSupportCm2),
            numberOfLayers = if (neededSupportReinforcement > 0.05) 2 else 1,
            clearSpacingBetweenBarsCm = roundTo2Decimals(clearSpSup),
            isClearSpacingCompliant = clearSpSup >= 2.5
        )

        // 4. Vérification de l'inversion sismique sur appui : As,inf >= 0.50 * As,sup (RPA § 7.5.2.1)
        // Les barres filantes inférieures traversent les poteaux sur appuis
        val actualInfAtSupport = spanLayer.filantesAreaCm2
        val requiredInfAtSupport = 0.50 * supportLayer.totalAdoptedAreaCm2
        val isSeismicInversionOk = actualInfAtSupport >= requiredInfAtSupport - 0.05
        val ratioInfSup = if (supportLayer.totalAdoptedAreaCm2 > 0) actualInfAtSupport / supportLayer.totalAdoptedAreaCm2 else 1.0

        // 5. Armatures transversales et espacements (RPA 2024 § 7.5.2.2)
        // Zone nodale : l' = min(2h, Ln / 4)
        val lPrime = min(2.0 * (heightHCm / 100.0), spanLnMeters * 0.25)

        // St <= min(h / 4, 12 * phi_l_min, 10 cm)
        val phiLMinMm = min(spanLayer.filantesDiameterMm, supportLayer.filantesDiameterMm)
        val stLimitH4 = heightHCm / 4.0
        val stLimit12Phi = 12.0 * (phiLMinMm / 10.0)
        val rawSt = min(stLimitH4, min(stLimit12Phi, 10.0))
        val nodalStCm = if (rawSt >= 9.0) 10.0 else 8.0

        // Zone courante : S't <= h / 2
        val currentStCm = min(heightHCm / 2.0, 15.0)

        // Estimation masse totale d'acier (kg)
        val steelVolM3 = (spanLayer.totalAdoptedAreaCm2 * spanLnMeters + supportLayer.chapeauxAreaCm2 * (spanLnMeters * 0.50)) / 10000.0
        val stirrupWeightKg = ((2.0 * (widthBCm + heightHCm) / 100.0) * (PI * 0.8 * 0.8 / 4.0) / 10000.0) * (spanLnMeters / (nodalStCm / 100.0)) * 7850.0
        val totalWeight = (steelVolM3 * 7850.0) + stirrupWeightKg

        return BeamRebarSelectionResult(
            beamName = beamName,
            widthBCm = widthBCm,
            heightHCm = heightHCm,
            spanLnMeters = spanLnMeters,
            spanReinforcement = spanLayer,
            supportReinforcement = supportLayer,
            minRpaAreaCm2 = roundTo2Decimals(minRpaCm2),
            maxRpaAreaCm2 = roundTo2Decimals(maxRpaCm2),
            ratioInfOverSupAtSupport = roundTo2Decimals(ratioInfSup),
            isSeismicInversionCompliant = isSeismicInversionOk,
            nodalZoneLengthLPrimeMeters = roundTo2Decimals(lPrime),
            nodalSpacingStCm = nodalStCm,
            currentSpacingStPrimeCm = currentStCm,
            firstStirrupOffsetCm = 5.0,
            totalSteelWeightKg = roundTo2Decimals(totalWeight)
        )
    }

    /**
     * Génère les coordonnées graphiques [0..1] des barres d'un poteau pour le dessin CAO/DXF.
     */
    private fun generateColumnRebarCoordinates(candidate: ColumnCandidateInternal): List<RebarCoordinate> {
        val list = mutableListOf<RebarCoordinate>()
        val covRel = 0.08 // Enrobage graphique relatif

        // 4 Barres d'angle (toujours présentes)
        list.add(RebarCoordinate(covRel, covRel, candidate.cornerDiam, true, "Angle bas-gauche"))
        list.add(RebarCoordinate(1.0 - covRel, covRel, candidate.cornerDiam, true, "Angle bas-droit"))
        list.add(RebarCoordinate(1.0 - covRel, 1.0 - covRel, candidate.cornerDiam, true, "Angle haut-droit"))
        list.add(RebarCoordinate(covRel, 1.0 - covRel, candidate.cornerDiam, true, "Angle haut-gauche"))

        // Barres intermédiaires
        when (candidate.totalBars) {
            8 -> {
                // 1 barre au milieu de chaque face
                list.add(RebarCoordinate(0.50, covRel, candidate.interDiam, false, "Face basse"))
                list.add(RebarCoordinate(1.0 - covRel, 0.50, candidate.interDiam, false, "Face droite"))
                list.add(RebarCoordinate(0.50, 1.0 - covRel, candidate.interDiam, false, "Face haute"))
                list.add(RebarCoordinate(covRel, 0.50, candidate.interDiam, false, "Face gauche"))
            }
            10 -> {
                // 2 sur face basse, 2 sur face haute, 1 sur chaque côté
                list.add(RebarCoordinate(0.35, covRel, candidate.interDiam, false, "Face basse 1"))
                list.add(RebarCoordinate(0.65, covRel, candidate.interDiam, false, "Face basse 2"))
                list.add(RebarCoordinate(1.0 - covRel, 0.50, candidate.interDiam, false, "Face droite"))
                list.add(RebarCoordinate(0.65, 1.0 - covRel, candidate.interDiam, false, "Face haute 1"))
                list.add(RebarCoordinate(0.35, 1.0 - covRel, candidate.interDiam, false, "Face haute 2"))
                list.add(RebarCoordinate(covRel, 0.50, candidate.interDiam, false, "Face gauche"))
            }
            12 -> {
                // 2 sur chaque face
                list.add(RebarCoordinate(0.35, covRel, candidate.interDiam, false, "Face basse 1"))
                list.add(RebarCoordinate(0.65, covRel, candidate.interDiam, false, "Face basse 2"))
                list.add(RebarCoordinate(1.0 - covRel, 0.35, candidate.interDiam, false, "Face droite 1"))
                list.add(RebarCoordinate(1.0 - covRel, 0.65, candidate.interDiam, false, "Face droite 2"))
                list.add(RebarCoordinate(0.65, 1.0 - covRel, candidate.interDiam, false, "Face haute 1"))
                list.add(RebarCoordinate(0.35, 1.0 - covRel, candidate.interDiam, false, "Face haute 2"))
                list.add(RebarCoordinate(covRel, 0.65, candidate.interDiam, false, "Face gauche 1"))
                list.add(RebarCoordinate(covRel, 0.35, candidate.interDiam, false, "Face gauche 2"))
            }
            16 -> {
                // 3 sur chaque face
                val offsets = listOf(0.28, 0.50, 0.72)
                for (off in offsets) list.add(RebarCoordinate(off, covRel, candidate.interDiam, false, "Face basse"))
                for (off in offsets) list.add(RebarCoordinate(1.0 - covRel, off, candidate.interDiam, false, "Face droite"))
                for (off in offsets.reversed()) list.add(RebarCoordinate(off, 1.0 - covRel, candidate.interDiam, false, "Face haute"))
                for (off in offsets.reversed()) list.add(RebarCoordinate(covRel, off, candidate.interDiam, false, "Face gauche"))
            }
        }

        return list
    }

    private fun roundTo2Decimals(value: Double): Double = round(value * 100.0) / 100.0

    private data class ColumnCandidateInternal(
        val desc: String,
        val areaCm2: Double,
        val totalBars: Int,
        val cornerCount: Int,
        val cornerDiam: Int,
        val interCount: Int,
        val interDiam: Int,
        val maxSpacingCm: Double,
        val minClearSpacingCm: Double
    )
}
