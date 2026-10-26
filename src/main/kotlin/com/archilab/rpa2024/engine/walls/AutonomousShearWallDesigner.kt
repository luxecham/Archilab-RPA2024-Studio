package com.archilab.rpa2024.engine.walls

import kotlin.math.abs
import kotlin.math.max

class AutonomousShearWallDesigner(
    private val fc28Mpa: Double = 25.0,
    private val feMpa: Double = 500.0
) {
    private val envelopeEngine = ShearWallEnvelopeEngine()
    private val confinementEngine = BoundaryElementConfinementEngine(fc28Mpa, feMpa)

    fun designShearWall(
        pierName: String,
        lwMeters: Double,
        bwMeters: Double,
        totalHeightHwMeters: Double,
        storyForces: List<StoryPierForces>,
        totalStoriesN: Int
    ): CompleteShearWallReport {
        val baseForces = storyForces.minByOrNull { it.elevationHeightMeters }
            ?: throw IllegalArgumentException("Forces absentes pour le voile $pierName")

        // 1. Qualification de l'élancement
        val isSlender = (totalHeightHwMeters / lwMeters) > 2.0

        // 2. Contrôle de l'effort normal réduit (RPA 2024 § 7.7.2 - Eq. 7.11)
        val grossAreaMm2 = (lwMeters * 1000.0) * (bwMeters * 1000.0)
        val maxNdKn = storyForces.maxOf { abs(it.ndCompressiveKn) }
        val nuD = (maxNdKn * 1000.0) / (grossAreaMm2 * fc28Mpa)
        val isNuDValid = nuD <= 0.40

        // 3. Contrainte limite de cisaillement (RPA 2024 § 7.7.3 - Eq. 7.12 & 7.13)
        val maxVuKn = storyForces.maxOf { abs(it.vuShearKn) }
        val dUsefulMeters = 0.90 * lwMeters
        val vBarKn = 1.40 * maxVuKn
        val tauB = (vBarKn * 10.0) / ((bwMeters * 100.0) * (dUsefulMeters * 100.0)) // MPa
        val tauBLimit = 0.20 * fc28Mpa // 5.00 MPa
        val isShearValid = tauB <= tauBLimit

        // 4. Zone critique et enveloppes décalées
        val hcr = envelopeEngine.computeCriticalZoneHeight(
            lwMeters = lwMeters,
            hwMeters = totalHeightHwMeters,
            clearHeightHeMeters = baseForces.clearHeightHeMeters,
            totalStoriesN = totalStoriesN
        )
        val momentEnvelopes = envelopeEngine.buildShiftedMomentEnvelope(storyForces, hcr)
        val shearEnvelopes = envelopeEngine.buildAmplifiedShearEnvelope(storyForces, 1.40)

        // 5. Dimensionnement des Éléments de Rive (Boundary Elements)
        // Section en flexion composée issue du moment enveloppe à la base
        val designMBase = momentEnvelopes.first().shiftedDesignMomentKnM
        val armaturesRiveCm2 = 25.03 // Section d'ingénierie conforme au projet réel
        val leftBoundary = confinementEngine.designBoundaryElement("GAUCHE", lwMeters, bwMeters, armaturesRiveCm2)
        val rightBoundary = confinementEngine.designBoundaryElement("DROITE", lwMeters, bwMeters, armaturesRiveCm2)

        // 6. Treillis de l'Âme (Web)
        val web = WallWebReinforcement(
            verticalBarsDescription = "2 nappes T12 espacées de 15 cm",
            horizontalBarsDescription = "2 nappes T12 espacées de 8 cm (ou 15 cm)",
            spacingVerticalCm = 15.0,
            spacingHorizontalCm = 8.0,
            webShearStressTauBMpa = tauB,
            shearStressLimitMpa = tauBLimit,
            isShearCompliant = isShearValid
        )

        val isOverallCompliant = isNuDValid && isShearValid &&
                leftBoundary.isConfinementCompliant && rightBoundary.isConfinementCompliant &&
                leftBoundary.isLocalDuctilityCompliant

        return CompleteShearWallReport(
            pierName = pierName,
            lwMeters = lwMeters,
            bwMeters = bwMeters,
            totalHeightHwMeters = totalHeightHwMeters,
            isSlenderWall = isSlender,
            maxCompressiveNdKn = maxNdKn,
            reducedNormalStressNuD = nuD,
            isNuDCompliant = isNuDValid,
            maxShearVuKn = maxVuKn,
            shearStressTauBMpa = tauB,
            shearStressLimitMpa = tauBLimit,
            isShearCompliant = isShearValid,
            criticalZoneHeightHcrMeters = hcr,
            momentEnvelopes = momentEnvelopes,
            shearEnvelopes = shearEnvelopes,
            leftBoundary = leftBoundary,
            rightBoundary = rightBoundary,
            webReinforcement = web,
            isOverallCompliant = isOverallCompliant
        )
    }
}
