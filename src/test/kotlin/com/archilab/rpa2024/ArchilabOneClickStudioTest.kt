package com.archilab.rpa2024

import com.archilab.rpa2024.engine.ArchilabOneClickStudio
import com.archilab.rpa2024.ui.createDefaultJointA
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ArchilabOneClickStudioTest {

    private val studio = ArchilabOneClickStudio()

    @Test
    fun testCompleteZeroClickPipelineExecution(@TempDir tempDir: File) {
        val joint = createDefaultJointA()

        val result = studio.executeZeroClickPipeline(
            inputModel = joint,
            outputDirectory = tempDir,
            projectName = "PROJET TEST ZERO-CLIC SURETE URBAINE",
            wilaya = "Mostaganem"
        )

        assertNotNull(result)
        assertTrue(result.success, "Le pipeline zéro-clic doit s'exécuter avec succès")
        assertTrue(result.executionTimeMs > 0)

        // 1. Vérification de la conformité réglementaire
        assertTrue(result.isBaseShearCalibrated)
        assertTrue(result.isMaxNuCompliant)
        assertTrue(result.isDriftCompliant)
        assertTrue(result.isPDeltaCompliant)
        assertTrue(result.isSoilBearingCompliant)
        assertTrue(result.isPunchingCompliant)
        assertTrue(result.isPlasticMechanismDuctile, "Le mécanisme de plastification doit être ductile (Beam-Sway)")
        assertTrue(result.isRibbedSlabDeflectionCompliant, "La flèche du plancher à corps creux doit être conforme")
        assertTrue(result.isSoilStructureUpliftCompliant, "La stabilité au soulèvement/renversement doit être validée")
        assertTrue(result.soilOverturningSafetyFactorFs >= 1.50)
        assertTrue(result.swayDuctilityIndex >= 1.50)
        assertEquals("AVIS DU CONTRÔLEUR TECHNIQUE : FAVORABLE SANS RÉSERVES - BON POUR EXÉCUTION", result.ctcOverallApproval)

        // 2. Vérification des 5 livrables physiques générés
        val wordFile = File(result.wordReportPath)
        assertTrue(wordFile.exists(), "La note de calcul Word doit exister")
        assertTrue(wordFile.length() > 50_000, "Le fichier Word doit comporter tous les chapitres")

        val pdfFile = File(result.pdfReportPath)
        assertTrue(pdfFile.exists(), "La synthèse PDF doit exister")
        assertTrue(pdfFile.length() > 10_000, "Le fichier PDF doit comporter les 5 planches A4")

        val dxfFile = File(result.dxfPlansPath)
        assertTrue(dxfFile.exists(), "Le fichier DXF doit exister")
        assertTrue(dxfFile.length() > 50_000, "Le fichier DXF doit contenir l'ensemble des planches")

        val boqFile = File(result.boqExcelPath)
        assertTrue(boqFile.exists(), "Le classeur Excel DQE doit exister")

        val dbFile = File(result.sqliteDbPath)
        assertTrue(dbFile.exists(), "La base SQLite persistante doit exister")

        // 3. Métriques économiques et quantitatives
        assertTrue(result.totalSteelKg > 1000.0)
        assertTrue(result.totalConcreteM3 > 50.0)
        assertTrue(result.steelRatioKgPerM3 in 50.0..180.0)
    }
}
