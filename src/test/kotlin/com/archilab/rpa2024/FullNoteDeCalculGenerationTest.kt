package com.archilab.rpa2024

import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.PdfReportExporter
import com.archilab.rpa2024.ui.createDefaultJointA
import org.apache.pdfbox.Loader
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

class FullNoteDeCalculGenerationTest {

    @Test
    fun testGenerateFullCtcWordReport() {
        val joint = createDefaultJointA()
        val tempDocx = File("build/test-results/NDC_JOINT_A_TEST.docx")
        tempDocx.parentFile?.mkdirs()

        val generator = CtcWordReportGenerator()
        val generatedFile = generator.generateWordReport(
            joint = joint,
            outputFile = tempDocx,
            projectName = "PROJET TEST INTÉGRAL R+4 RPA 2024",
            clientName = "DIRECTION DU LOGEMENT ET DES ÉQUIPEMENTS PUBLICS",
            engineerName = "Ing. Structure ArchiLab"
        )

        // 1. Vérification de l'existence et de la taille du fichier Word
        assertTrue(generatedFile.exists(), "Le fichier Word généré doit exister.")
        assertTrue(generatedFile.length() > 20_000, "Le fichier Word généré doit contenir du contenu substantiel (> 20 Ko).")

        // 2. Relecture et validation de l'intégrité du document DOCX via Apache POI
        FileInputStream(generatedFile).use { fis ->
            val document = XWPFDocument(fis)

            // Vérification de la présence des tableaux (Page de garde, Sommaire, Matériaux, Sismique, etc.)
            assertTrue(document.tables.size >= 8, "Le document doit contenir au minimum 8 tableaux réglementaires formatés.")

            // Vérification des 13 chapitres dans le texte
            val allParagraphsText = document.paragraphs.joinToString("\n") { it.text }
            for (ch in 1..13) {
                assertTrue(
                    allParagraphsText.contains("CHAPITRE $ch"),
                    "Le document Word doit contenir le titre officiel du CHAPITRE $ch."
                )
            }

            // Vérification des images intégrées (3D, Plan, Spectre, Fiches)
            assertTrue(document.allPictures.isNotEmpty(), "Le document Word doit contenir des images intégrées.")
        }
    }

    @Test
    fun testGenerateCtcPdfExecutiveReport() {
        val joint = createDefaultJointA()
        val tempPdf = File("build/test-results/SYNTHESE_JOINT_A_TEST.pdf")
        tempPdf.parentFile?.mkdirs()

        val exporter = PdfReportExporter()
        val generatedPdf = exporter.exportExecutivePdf(
            joint = joint,
            outputPdfFile = tempPdf,
            projectName = "SYNTHÈSE SISMIQUE TEST JOINT A"
        )

        // 1. Vérification de l'existence et taille du PDF
        assertTrue(generatedPdf.exists(), "Le fichier PDF doit exister.")
        assertTrue(generatedPdf.length() > 10_000, "Le fichier PDF doit faire plus de 10 Ko.")

        // 2. Relecture avec PDFBox 3.x
        val pdDoc = Loader.loadPDF(generatedPdf)
        assertEquals(5, pdDoc.numberOfPages, "La synthèse PDF exécutive doit comporter 5 pages A4 complètes.")
        pdDoc.close()
    }
}
