package com.archilab.rpa2024.engine.frames

import kotlin.math.max

class AutonomousCommercialRebarSelector(
    private val concreteCoverCm: Double = 3.0
) {
    /**
     * Sélectionne une disposition symétrique et constructible pour les poteaux
     * Respecte l'espacement maximal de 20 cm en Zone III (RPA 2024 § 7.4.2)
     */
    fun selectColumnBars(
        bCm: Double,
        hCm: Double,
        targetAreaCm2: Double
    ): RebarArrangement {
        val availableCornerDia = listOf(14, 16, 20)
        val availableInterDia = listOf(14, 16)

        var bestMatch: RebarArrangement? = null
        var minExcess = Double.MAX_VALUE

        for (cornerDia in availableCornerDia) {
            val cornerBar = CommercialBar(cornerDia)
            val cornerArea = 4 * cornerBar.sectionAreaCm2

            for (sideDia in availableInterDia) {
                val sideBar = CommercialBar(sideDia)

                // nbB et nbH : nombre de barres intermédiaires par face
                for (nbB in 0..4 step 2) {
                    for (nbH in 0..4 step 2) {
                        val totalBars = 4 + 2 * nbB + 2 * nbH
                        val totalArea = cornerArea + (2 * nbB + 2 * nbH) * sideBar.sectionAreaCm2

                        if (totalArea >= targetAreaCm2) {
                            val usefulB = bCm - 2 * concreteCoverCm
                            val spacingB = usefulB / (nbB + 1)
                            val usefulH = hCm - 2 * concreteCoverCm
                            val spacingH = usefulH / (nbH + 1)

                            if (spacingB <= 20.0 && spacingH <= 20.0 && spacingB >= 5.0 && spacingH >= 5.0) {
                                val excess = totalArea - targetAreaCm2
                                if (excess < minExcess) {
                                    minExcess = excess
                                    val desc = if (cornerDia == sideDia) {
                                        "${totalBars}T$cornerDia"
                                    } else {
                                        "4T$cornerDia + ${2 * nbB + 2 * nbH}T$sideDia"
                                    }
                                    val map = mapOf(
                                        "angles" to "4T$cornerDia",
                                        "interB" to "${nbB}T$sideDia",
                                        "interH" to "${nbH}T$sideDia"
                                    )
                                    bestMatch = RebarArrangement(desc, totalArea, totalBars, map)
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestMatch ?: run {
            // Disposition de secours calculée
            RebarArrangement("Armatures spéciales : ${"%.2f".format(targetAreaCm2)} cm²", targetAreaCm2, 8)
        }
    }

    /**
     * Sélectionne les armatures de poutres (3 barres filantes continues + chapeaux)
     */
    fun selectBeamBars(
        targetAreaCm2: Double,
        isSupport: Boolean
    ): RebarArrangement {
        // Priorité aux armatures homogènes (même diamètre en travée et chapeaux) : T14, T12, T16
        for (dia in listOf(14, 12, 16)) {
            val barArea = CommercialBar(dia).sectionAreaCm2
            val baseArea = 3 * barArea
            if (baseArea >= targetAreaCm2) {
                return RebarArrangement("3T$dia filantes", baseArea, 3)
            }
            for (nbChap in listOf(2, 3)) {
                val totalArea = baseArea + nbChap * barArea
                if (totalArea >= targetAreaCm2) {
                    return RebarArrangement(
                        "3T$dia filantes + ${nbChap}T$dia chapeaux",
                        totalArea,
                        3 + nbChap
                    )
                }
            }
        }

        // Panachage secondaire si nécessaire
        for (baseDia in listOf(14, 12, 16)) {
            val baseArea = 3 * CommercialBar(baseDia).sectionAreaCm2
            for (chapDia in listOf(14, 12, 16)) {
                if (chapDia == baseDia) continue
                for (nbChap in listOf(2, 3)) {
                    val totalArea = baseArea + nbChap * CommercialBar(chapDia).sectionAreaCm2
                    if (totalArea >= targetAreaCm2) {
                        return RebarArrangement(
                            "3T$baseDia filantes + ${nbChap}T$chapDia chapeaux",
                            totalArea,
                            3 + nbChap
                        )
                    }
                }
            }
        }

        return RebarArrangement("Panachage d'armatures : ${"%.2f".format(targetAreaCm2)} cm²", targetAreaCm2, 6)
    }
}
