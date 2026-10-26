package com.archilab.rpa2024

import com.archilab.rpa2024.cad.AutonomousCadDxfEngine
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AutonomousCadDxfEngineTest {

    private val cadEngine = AutonomousCadDxfEngine()

    @Test
    fun testAutonomousCadPackageExport(@TempDir tempDir: File) {
        val joint = createDefaultJointA()
        val targetDxf = File(tempDir, "TEST_PACKAGE_EXECUTION.dxf")

        val result = cadEngine.exportFullCadPackage(
            joint = joint,
            targetDxfFile = targetDxf,
            projectName = "Projet Test Cad",
            wilaya = "Alger"
        )

        assertNotNull(result)
        assertTrue(targetDxf.exists(), "Le fichier DXF doit exister sur le disque")
        assertTrue(result.fileSizeBytes > 50_000, "Le fichier DXF doit contenir les entités complètes (> 50 Ko)")
        assertEquals(7, result.totalSheetsCount, "Le dossier CAO doit comporter 7 planches d'exécution et de coffrage")
        assertTrue(result.entitiesCount > 200, "Le dessin DXF doit comporter au moins 200 entités vectorielles")
        assertTrue(result.totalSteelWeightKg > 0.0, "Le métré d'acier total doit être strictement positif")
        assertTrue(result.totalConcreteVolumeM3 > 0.0, "Le volume de béton doit être calculé")
        assertTrue(result.steelRatioKgPerM3 in 50.0..180.0, "Le ratio d'acier doit être compris dans une fourchette technique réaliste")
    }
}
