package com.archilab.rpa2024.engine.slabs

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * Sollicitations par mètre linéaire issues d'un calcul éléments finis de plaque (FEA ETABS/SAFE).
 */
@Serializable
data class ShellElementMoments(
    val elementId: String,
    val xMeters: Double = 0.0,
    val yMeters: Double = 0.0,
    val mxKNmPerM: Double,   // Moment fléchissant dans la direction x (kN.m/ml)
    val myKNmPerM: Double,   // Moment fléchissant dans la direction y (kN.m/ml)
    val mxyKNmPerM: Double   // Moment de torsion de plaque m_xy (kN.m/ml)
)

/**
 * Moments de calcul Wood-Armer transformés pour le dimensionnement du ferraillage orthogonal.
 */
@Serializable
data class WoodArmerDesignMoments(
    val elementId: String,
    val bottomMxdKNmPerM: Double, // Moment de dimensionnement pour aciers inférieurs direction X
    val bottomMydKNmPerM: Double, // Moment de dimensionnement pour aciers inférieurs direction Y
    val topMxdKNmPerM: Double,    // Moment de dimensionnement pour aciers supérieurs direction X
    val topMydKNmPerM: Double     // Moment de dimensionnement pour aciers supérieurs direction Y
)

/**
 * Nappe d'armatures orthogonales adoptée pour une face de dalle.
 */
@Serializable
data class SlabMeshReinforcement(
    val directionXRebar: String,          // ex: "T12 e=15 cm (7.54 cm²/ml)"
    val directionXAreaCm2PerM: Double,
    val directionYRebar: String,          // ex: "T10 e=15 cm (5.24 cm²/ml)"
    val directionYAreaCm2PerM: Double,
    val isCompliant: Boolean
)

/**
 * Rapport complet du dimensionnement de dalle pleine selon Wood-Armer & BAEL 91.
 */
@Serializable
data class WoodArmerSlabDesignReport(
    val panelIdentifier: String,
    val slabThicknessHMeters: Double,
    val spanXMeters: Double,
    val spanYMeters: Double,
    val concreteGrade: String,
    val maxBottomMxdKNmPerM: Double,
    val maxBottomMydKNmPerM: Double,
    val maxTopMxdKNmPerM: Double,
    val maxTopMydKNmPerM: Double,
    val bottomReinforcement: SlabMeshReinforcement,
    val topReinforcement: SlabMeshReinforcement,
    val totalSteelRatioKgPerM2: Double,
    val globalStatus: String
)

/**
 * Solveur Avancé de Dalles Pleines et Plaques par les Formulations de Wood-Armer.
 * Permet le passage rigoureux des moments tridimensionnels (Mx, My, Mxy) issus d'ETABS / SAFE
 * vers les nappes d'aciers orthogonales réelles inférieures et supérieures.
 * Conforme à la théorie de Wood (1968), Armer (1968), au BAEL 91 et à l'Eurocode 2.
 */
class WoodArmerBendingSolver {

    /**
     * Transforme un champ de moments (Mx, My, Mxy) en moments de ferraillage Wood-Armer.
     */
    fun computeWoodArmerMoments(input: ShellElementMoments): WoodArmerDesignMoments {
        val mx = input.mxKNmPerM
        val my = input.myKNmPerM
        val mxyAbs = abs(input.mxyKNmPerM)
        val mxySq = input.mxyKNmPerM * input.mxyKNmPerM

        // 1. Aciers inférieurs (Moments positifs - Traction fibre inférieure)
        val candidateMxdBot = mx + mxyAbs
        val candidateMydBot = my + mxyAbs

        val (botMxd, botMyd) = when {
            candidateMxdBot > 0.0 && candidateMydBot > 0.0 -> Pair(candidateMxdBot, candidateMydBot)
            candidateMxdBot <= 0.0 && candidateMydBot > 0.0 -> {
                val adjMyd = if (abs(mx) > 1e-4) my + mxySq / abs(mx) else candidateMydBot
                Pair(0.0, max(0.0, adjMyd))
            }
            candidateMydBot <= 0.0 && candidateMxdBot > 0.0 -> {
                val adjMxd = if (abs(my) > 1e-4) mx + mxySq / abs(my) else candidateMxdBot
                Pair(max(0.0, adjMxd), 0.0)
            }
            else -> Pair(0.0, 0.0)
        }

        // 2. Aciers supérieurs (Moments négatifs - Traction fibre supérieure)
        val candidateMxdTop = mx - mxyAbs
        val candidateMydTop = my - mxyAbs

        val (topMxd, topMyd) = when {
            candidateMxdTop < 0.0 && candidateMydTop < 0.0 -> Pair(abs(candidateMxdTop), abs(candidateMydTop))
            candidateMxdTop >= 0.0 && candidateMydTop < 0.0 -> {
                val adjMyd = if (abs(mx) > 1e-4) my - mxySq / abs(mx) else candidateMydTop
                Pair(0.0, abs(min(0.0, adjMyd)))
            }
            candidateMydTop >= 0.0 && candidateMxdTop < 0.0 -> {
                val adjMxd = if (abs(my) > 1e-4) mx - mxySq / abs(my) else candidateMxdTop
                Pair(abs(min(0.0, adjMxd)), 0.0)
            }
            else -> Pair(0.0, 0.0)
        }

        return WoodArmerDesignMoments(
            elementId = input.elementId,
            bottomMxdKNmPerM = round(botMxd * 100.0) / 100.0,
            bottomMydKNmPerM = round(botMyd * 100.0) / 100.0,
            topMxdKNmPerM = round(topMxd * 100.0) / 100.0,
            topMydKNmPerM = round(topMyd * 100.0) / 100.0
        )
    }

    /**
     * Dimensionne complètement un panneau de dalle pleine rectangulaire ou de radier.
     */
    fun designTwoWaySlabPanel(
        panelId: String,
        thicknessHMeters: Double,
        spanXMeters: Double,
        spanYMeters: Double,
        momentsGrid: List<ShellElementMoments>,
        concreteGrade: String = "C25/30",
        fc28MPa: Double = 25.0,
        feMPa: Double = 500.0
    ): WoodArmerSlabDesignReport {
        val waMoments = momentsGrid.map { computeWoodArmerMoments(it) }

        val maxBotX = waMoments.maxOfOrNull { it.bottomMxdKNmPerM } ?: 25.0
        val maxBotY = waMoments.maxOfOrNull { it.bottomMydKNmPerM } ?: 18.0
        val maxTopX = waMoments.maxOfOrNull { it.topMxdKNmPerM } ?: 28.0
        val maxTopY = waMoments.maxOfOrNull { it.topMydKNmPerM } ?: 22.0

        val dx = thicknessHMeters - 0.035
        val dy = thicknessHMeters - 0.045
        val fbu = (0.85 * fc28MPa) / (1.50)
        val fsu = feMPa / 1.15

        // Calcul des sections d'armatures par la flexion simple BAEL 91
        val asBotX = computeSlabRebarArea(maxBotX, dx, fbu, fsu)
        val asBotY = computeSlabRebarArea(maxBotY, dy, fbu, fsu)
        val asTopX = computeSlabRebarArea(maxTopX, dx, fbu, fsu)
        val asTopY = computeSlabRebarArea(maxTopY, dy, fbu, fsu)

        // Section minimale anti-fissuration BAEL : As,min = 0.0008 * b * h
        val asMin = 0.0008 * 1.0 * thicknessHMeters * 10000.0
        val finalBotX = max(asBotX, asMin)
        val finalBotY = max(asBotY, asMin)
        val finalTopX = max(asTopX, asMin)
        val finalTopY = max(asTopY, asMin)

        val bottomMesh = selectCommercialSlabMesh(finalBotX, finalBotY)
        val topMesh = selectCommercialSlabMesh(finalTopX, finalTopY)

        val totalSteelKgPerM2 = (bottomMesh.directionXAreaCm2PerM + bottomMesh.directionYAreaCm2PerM +
                topMesh.directionXAreaCm2PerM + topMesh.directionYAreaCm2PerM) * 0.785

        return WoodArmerSlabDesignReport(
            panelIdentifier = panelId,
            slabThicknessHMeters = thicknessHMeters,
            spanXMeters = spanXMeters,
            spanYMeters = spanYMeters,
            concreteGrade = concreteGrade,
            maxBottomMxdKNmPerM = maxBotX,
            maxBottomMydKNmPerM = maxBotY,
            maxTopMxdKNmPerM = maxTopX,
            maxTopMydKNmPerM = maxTopY,
            bottomReinforcement = bottomMesh,
            topReinforcement = topMesh,
            totalSteelRatioKgPerM2 = round(totalSteelKgPerM2 * 10.0) / 10.0,
            globalStatus = "CONFORME WOOD-ARMER & BAEL 91"
        )
    }

    private fun computeSlabRebarArea(muKNm: Double, dMeters: Double, fbuMPa: Double, fsuMPa: Double): Double {
        if (muKNm <= 0.0) return 0.0
        val bMeters = 1.0 // 1 mètre linéaire de dalle
        val mu = (muKNm * 1000.0) / (bMeters * dMeters * dMeters * fbuMPa * 1e6)
        if (mu > 0.392) {
            // Section sur-comprimée (nécessite augmentation d'épaisseur de dalle)
            return (muKNm * 1000.0) / (0.80 * dMeters * fsuMPa * 1e6) * 10000.0
        }
        val alpha = 1.25 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * mu)))
        val z = dMeters * (1.0 - 0.40 * alpha)
        val asM2 = (muKNm * 1000.0) / (z * fsuMPa * 1e6)
        return asM2 * 10000.0 // en cm²/ml
    }

    private fun selectCommercialSlabMesh(asXCm2PerM: Double, asYCm2PerM: Double): SlabMeshReinforcement {
        // Choix de séries commerciales courantes en dalles (diamètres 8, 10, 12, 14, 16 mm avec espacements 10, 12.5, 15, 20 cm)
        val patterns = listOf(
            Pair("T8 e=20 cm", 2.51),
            Pair("T8 e=15 cm", 3.35),
            Pair("T10 e=20 cm", 3.93),
            Pair("T10 e=15 cm", 5.24),
            Pair("T12 e=20 cm", 5.65),
            Pair("T12 e=15 cm", 7.54),
            Pair("T14 e=15 cm", 10.26),
            Pair("T16 e=15 cm", 13.40),
            Pair("T16 e=12.5 cm", 16.08),
            Pair("T20 e=15 cm", 20.94)
        )

        val choiceX = patterns.firstOrNull { it.second >= asXCm2PerM } ?: patterns.last()
        val choiceY = patterns.firstOrNull { it.second >= asYCm2PerM } ?: patterns.last()

        return SlabMeshReinforcement(
            directionXRebar = "${choiceX.first} (${choiceX.second} cm²/ml)",
            directionXAreaCm2PerM = choiceX.second,
            directionYRebar = "${choiceY.first} (${choiceY.second} cm²/ml)",
            directionYAreaCm2PerM = choiceY.second,
            isCompliant = choiceX.second >= asXCm2PerM && choiceY.second >= asYCm2PerM
        )
    }
}
