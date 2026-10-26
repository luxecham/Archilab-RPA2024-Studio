package com.archilab.rpa2024

import com.archilab.rpa2024.engine.era.PlasticHingeDistributionEngine
import com.archilab.rpa2024.model.AxisDirection
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlasticHingeDistributionEngineTest {

    private val engine = PlasticHingeDistributionEngine()

    @Test
    fun testPlasticHingesDistributionDuctileMechanism() {
        val joint = createDefaultJointA()

        val report = engine.evaluatePlasticHingesDistribution(
            joint = joint,
            topDisplacementMeters = 0.045, // 4.5 cm de déplacement en tête
            direction = AxisDirection.X
        )

        assertNotNull(report)
        assertTrue(report.totalHingesCount > 0, "Des rotules plastiques doivent être positionnées sur les éléments")
        assertTrue(report.hinges.any { it.elementType == "Poutre" })
        assertTrue(report.hinges.any { it.elementType == "Poteau" })

        // Vérification de la hiérarchie poutres faibles / poteaux forts
        assertTrue(report.swayDuctilityIndex > 0.0)
        assertTrue(report.isDuctileBeamMechanism, "La structure de démonstration doit présenter un mécanisme ductile global")
        assertTrue(report.hingesByState.isNotEmpty())
    }
}
