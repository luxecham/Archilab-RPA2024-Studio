package com.archilab.rpa2024

import com.archilab.rpa2024.report.AutonomousCtcReportEngine
import com.archilab.rpa2024.ui.createDefaultJointA
import org.apache.pdfbox.Loader
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream

class AutonomousCtcReportEngineTest {

    private val reportEngine = AutonomousCtcReportEngine()

    @Test
    fun testGenerateFullDocumentationPackage(@TempDir tempDir: File) {
        val joint = createDefaultJointA()

        val result = reportEngine.generateFullDocumentationPackage(
            joint = joint,
            outputDir = tempDir,
            projectName = "Test Projet CTC",
            filePrefix = "TEST_NOTE_DE_CALCUL"
        )

        assertNotNull(result)
        assertTrue(result.isGloballyCompliant)
        assertEquals(5, result.pdfPagesCount)
        assertEquals(13, result.wordChaptersCount)

        // 1. Vérification du fichier Word
        val wordFile = File(result.wordFilePath)
        assertTrue(wordFile.exists())
        assertTrue(wordFile.length() > 50_000, "Le fichier Word doit dépasser 50 Ko")
        FileInputStream(wordFile).use { fis ->
            val doc = XWPFDocument(fis)
            assertTrue(doc.paragraphs.size > 100, "Le rapport Word doit comporter plus de 100 paragraphes")
            assertTrue(doc.tables.size > 10, "Le rapport Word doit contenir plus de 10 tableaux réglementaires")
        }

        // 2. Vérification du fichier PDF
        val pdfFile = File(result.pdfFilePath)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 10_000, "Le fichier PDF doit dépasser 10 Ko")
        val pdDoc = Loader.loadPDF(pdfFile)
        assertEquals(5, pdDoc.numberOfPages, "La synthèse PDF exécutive doit comporter exactement 5 pages A4")
        pdDoc.close()
    }
}
