package com.archilab.rpa2024

import com.archilab.rpa2024.engine.geometry.CheckStatus
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.NoteDeCalculBundle
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.report.PdfReportExporter
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.apache.pdfbox.Loader
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Test de bout en bout de la partie 6 : composition des treize chapitres CTC depuis
 * l'export ETABS/SAFE du JOINT A, generation du DOCX (equations OMML natives, sommaire
 * automatique, images 300 DPI) puis de l'export PDF pagine.
 */
class FullNoteDeCalculGenerationTest {

    private val outputDirectory: Path = Path.of("build", "generated-ndc")

    @Test
    fun `genere la note de calcul CTC complete en DOCX et PDF`() {
        Files.createDirectories(outputDirectory)
        val imported = SampleJointA.load()
        val foundations = SampleSafeFoundation.load()

        val artifacts = NoteDeCalculBundle().build(
            NoteDeCalculRequest(
                model = imported.model,
                results = imported.results,
                foundations = foundations,
                projectTitle = "1500 LLV BOSQUET",
                wilaya = "Sidi Bel Abbès",
                client = "Direction du Logement — Sidi Bel Abbès",
                engineer = "Archilab — Bureau d'études structures",
                reference = "NDC-JOINT-A-RPA2024",
                revision = "A"
            )
        )

        val document = artifacts.document
        assertEquals(13, document.chapters.size, "La note de calcul doit comporter treize chapitres réglementaires.")
        assertEquals((1..13).toList(), document.chapters.map { it.number })
        assertTrue(document.chapters.all { it.blocks.isNotEmpty() }, "Aucun chapitre ne doit être vide.")
        assertNotNull(document.cover.view3d, "La page de garde doit porter la vue 3D.")
        assertTrue(artifacts.figures.size >= 8, "Les rendus 300 DPI doivent alimenter les chapitres.")
        assertTrue(document.status != CheckStatus.NOT_APPLICABLE)

        val equations = document.allChapters.sumOf { chapter ->
            chapter.blocks.count { it is ReportBlock.Equation }
        }
        assertTrue(equations >= 8, "Les équations OMML doivent être présentes ($equations trouvées).")

        val docx = CtcWordReportGenerator().write(
            document,
            outputDirectory.resolve("NDC_JOINT_A_RPA2024_GENERATED.docx")
        )
        val pdf = PdfReportExporter().write(
            document,
            outputDirectory.resolve("NDC_JOINT_A_RPA2024_GENERATED.pdf")
        )

        assertTrue(Files.size(docx) > 200_000, "Le DOCX doit contenir les images haute résolution.")
        assertTrue(Files.size(pdf) > 100_000, "Le PDF doit contenir les chapitres et les schémas.")

        XWPFDocument(FileInputStream(docx.toFile())).use { word ->
            val xml = word.document.xmlText()
            val headings = word.paragraphs.count { it.styleID == "Heading1" }
            assertTrue(headings >= 13, "Le sommaire doit indexer les titres de niveau 1 ($headings).")
            assertTrue(xml.contains("TOC \\o"), "Le champ TOC automatique doit être présent.")
            assertTrue(xml.contains("m:oMath"), "Les équations doivent être écrites en OMML natif.")
            assertTrue(xml.contains("<w:bookmarkStart"), "Chaque titre doit porter un signet unique.")
            assertTrue(word.allPictures.size >= 8, "Les figures doivent être incorporées dans le DOCX.")
            assertTrue(word.headerList.isNotEmpty(), "L'en-tête courant CTC doit être défini.")
            assertTrue(word.footerList.isNotEmpty(), "Le pied de page doit porter la pagination.")
            val footerXml = word.footerList.joinToString(" ") { it._getHdrFtr().xmlText() }
            assertTrue(footerXml.contains("PAGE") && footerXml.contains("NUMPAGES"), "Pagination X / Y attendue.")
            val settingsXml = java.util.zip.ZipFile(docx.toFile()).use { zip ->
                zip.getInputStream(zip.getEntry("word/settings.xml")).readBytes().toString(Charsets.UTF_8)
            }
            assertTrue(
                settingsXml.contains("updateFields"),
                "Word doit recalculer le sommaire et la pagination à l'ouverture."
            )
        }

        Loader.loadPDF(pdf.toFile()).use { loaded ->
            assertTrue(loaded.numberOfPages >= 20, "Le PDF paginé doit couvrir l'ensemble de la note.")
            assertTrue(loaded.pages.all { it.mediaBox.width > 500f }, "Toutes les pages doivent être en A4.")
        }

        println("DOCX : ${docx.toAbsolutePath()} (${Files.size(docx) / 1024} ko)")
        println("PDF  : ${pdf.toAbsolutePath()} (${Files.size(pdf) / 1024} ko)")
        println("Chapitres : ${document.chapters.size} · équations : $equations · figures : ${artifacts.figures.size}")
    }
}
