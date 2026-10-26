package com.archilab.rpa2024

import com.archilab.rpa2024.engine.foundations.AutonomousFoundationOptimizer
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AutonomousFoundationOptimizerTest {

    private val optimizer = AutonomousFoundationOptimizer()

    @Test
    fun testAutonomousFoundationOptimizationWorkflow() {
        val joint = createDefaultJointA()
        val result = optimizer.optimizeFoundations(joint)

        assertNotNull(result)
        assertTrue(result.isGloballyCompliant, "L'ensemble du système de fondations optimisé doit être conforme")
        assertTrue(result.optimizedSections.containsKey("SFJ200"))
        assertTrue(result.optimizedSections.containsKey("SF120"))

        // Vérification des ressorts de Winkler
        val sfjSpring = result.springStiffnessesKNmPerM["SFJ200"]
        assertNotNull(sfjSpring)
        assertTrue(sfjSpring!! > 0.0, "La raideur linéique de Winkler doit être strictement positive")

        // Vérification du poinçonnement
        val sfjPunch = result.punchingReports["SFJ200"]
        assertNotNull(sfjPunch, "Le contrôle de poinçonnement sur SFJ200 doit être calculé")
        assertTrue(sfjPunch!!.isCompliant, "Le poinçonnement sous poteau doit être vérifié sans armatures transversales")

        // Vérification de la contrainte de sol
        assertTrue(result.finalMaxEffectiveStressKNm2 <= result.allowableStressKNm2)
    }
}
