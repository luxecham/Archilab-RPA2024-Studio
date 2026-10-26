package com.archilab.rpa2024.testing

import com.archilab.rpa2024.engine.secondary.StaircaseGeometry
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.NoteDeCalculOptions
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.report.PdfReportExporter
import com.archilab.rpa2024.report.ReferenceNoteDeCalculComposer
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Génère et contrôle la note de calcul CTC de référence du Joint A, avec les treize
 * chapitres demandés, les tableaux DTR/RPA complémentaires et les vingt captures
 * logicielles à insérer.
 */
class ReferenceNoteDeCalculGenerationTest {

    @Test
    fun `la note de calcul de reference contient chapitres captures et CV`() {
        val outputDirectory = Path.of("build", "note-joint-a")
        Files.createDirectories(outputDirectory)
        val imported = SampleJointA.load()
        val foundations = SampleSafeFoundation.load()
        val staircase = StaircaseGeometry(
            name = "Escalier principal - deux volées",
            riserHeightMeters = 0.17,
            treadDepthMeters = 0.28,
            numberOfRisers = 22,
            numberOfFlights = 2,
            flightWidthMeters = 1.20,
            landingLengthMeters = 1.20,
            waistThicknessMeters = 0.15,
            landingThicknessMeters = 0.15
        )
        val request = NoteDeCalculRequest(
            model = imported.model,
            results = imported.results,
            foundations = foundations,
            staircaseGeometry = staircase,
            projectTitle = "1500 LLV BOSQUET",
            wilaya = "Sidi Bel Abbès",
            client = "Direction du Logement — Sidi Bel Abbès",
            engineer = "Archilab — Bureau d'études structures",
            reference = "NDC-JOINT-A-RPA2024",
            revision = "A",
            options = NoteDeCalculOptions()
        )

        val result = ReferenceNoteDeCalculComposer().compose(request)
        val document = result.document
        assertEquals(13, document.chapters.size)
        val expectedTitles = listOf(
            "Description du projet",
            "Caractéristiques géométriques du bâtiment",
            "Caractéristiques des matériaux",
            "Combinaisons de calcul",
            "Descente de charges (DTR-BC 2.2)",
            "Prédimensionnement des poutres",
            "Prédimensionnement des poteaux",
            "Étude des planchers (corps creux et poutrelles)",
            "Étude sismique (RPA 2024)",
            "Calcul des éléments structuraux",
            "Étude de l'acrotère",
            "Étude des escaliers",
            "Calcul de fondation (SAFE / ETABS)"
        )
        assertEquals(expectedTitles, document.chapters.map { it.title })
        assertEquals((1..13).toList(), document.chapters.map { it.number })

        val expectedCaptures = listOf(
            "Vue 3D globale du modèle filaire/extrudé ETABS de la structure",
            "Vue en plan des axes et de la disposition des poteaux/voiles dans ETABS",
            "Capture de la fenêtre Material Property Data d'ETABS / SAFE",
            "Capture de la fenêtre Define Load Combinations et Define Mass Source d'ETABS",
            "Capture de l'interface Koka / SOCOTEC montrant la géométrie de la poutrelle et les courbes de moments",
            "Tableau Modal Participating Mass Ratios issu d'ETABS (modes, périodes, UX, UY, sommes UX/UY/RZ)",
            "Tableau Response Spectrum Base Reactions et Story Shears d'ETABS",
            "Tableau récapitulatif des efforts normaux Poteaux sous combinaisons sismiques",
            "Tableau Diaphragm CM Displacements issu d'ETABS",
            "Tableau récapitulatif de l'effet P-Delta (Pk, Dt, Vk, hk, theta_k)",
            "Fiche de calcul BaelR (Poteau) et tableau des sollicitations critiques d'ETABS",
            "Schéma d'élévation de l'armature transversale d'un poteau coté",
            "Fiches de calcul BaelR (Poutre travée/appui) et Tableau de vérification à l'effort tranchant",
            "Schéma d'élévation et coupe de l'armature d'une poutre cotée",
            "Tableaux Pier Forces (P, V2, M3) pour les voiles de contreventement",
            "Graphiques des enveloppes de calcul pour les moments fléchissants (décalés de hcr) et efforts tranchants (amplifiés)",
            "Fiche de calcul et ferraillage des voiles en zone critique (Éléments de rive et âme)",
            "Vue SAFE montrant le modèle de semelles filantes / radier et les propriétés des ressorts K120/K160",
            "Diagramme des moments fléchissants (Beam Major Moment Diagram) des nervures de fondation dans SAFE",
            "Fiches BaelR / BaelT (Section en Té) pour le ferraillage des semelles filantes en appui et en travée, y compris au droit des joints"
        )
        assertEquals(expectedCaptures.size, result.capturePlaceholders.size)
        assertEquals(expectedCaptures, result.capturePlaceholders)
        val paragraphs = document.chapters.flatMap { chapter ->
            chapter.blocks.filterIsInstance<ReportBlock.Paragraph>().map { it.text }
        }
        expectedCaptures.forEach { capture ->
            assertTrue(
                paragraphs.any { it == "[FIGURE_CAPTURE_LOGICIEL : $capture]" },
                "Balise de capture absente : $capture"
            )
        }
        assertTrue(paragraphs.count { it.contains("CV") } >= expectedCaptures.size)
        assertTrue(
            document.chapters.flatMap { it.blocks }.any { it is ReportBlock.Table },
            "Les tableaux de calcul doivent être présents."
        )
        assertTrue(
            document.chapters.sumOf { chapter -> chapter.blocks.count { it is ReportBlock.Figure } } > 0,
            "Les figures techniques générées doivent rester dans la note."
        )
        assertEquals(13, document.chapters.size, "Le sommaire doit comporter exactement treize chapitres.")

        val docx = CtcWordReportGenerator().write(
            document,
            outputDirectory.resolve("NOTE_CALCUL_JOINT_A_RPA2024.docx")
        )
        val pdf = PdfReportExporter().write(
            document,
            outputDirectory.resolve("NOTE_CALCUL_JOINT_A_RPA2024.pdf")
        )
        assertTrue(Files.exists(docx))
        assertTrue(Files.exists(pdf))
        assertTrue(Files.size(docx) > 200_000L)
        assertTrue(Files.size(pdf) > 100_000L)

        XWPFDocument(FileInputStream(docx.toFile())).use { word ->
            val text = word.paragraphs.joinToString("\n") { it.text }
            expectedCaptures.forEach { capture ->
                assertTrue(text.contains(capture), "Capture Word absente : $capture")
            }
            assertTrue(word.document.xmlText().contains("m:oMath"), "Les équations OMML doivent être présentes.")
            assertTrue(word.document.xmlText().contains("TOC \\o"), "Le sommaire Word doit être présent.")
            assertTrue(word.paragraphs.count { it.styleID == "Heading1" } >= 13)
        }

        Loader.loadPDF(pdf.toFile()).use { loaded ->
            assertTrue(loaded.numberOfPages >= 20, "La note PDF doit être complète.")
            val text = PDFTextStripper().getText(loaded)
            assertNotNull(text)
            val normalized = text.replace(Regex("\\s+"), " ")
            expectedCaptures.forEach { capture ->
                assertTrue(
                    normalized.contains(capture.replace(Regex("\\s+"), " ")),
                    "Capture PDF absente : $capture"
                )
            }
            assertTrue(normalized.contains("Description du projet"))
            assertTrue(normalized.contains("Calcul de fondation (SAFE / ETABS)"))
        }

        println("NOTE : ${docx.toAbsolutePath()}")
        println("NOTE PDF : ${pdf.toAbsolutePath()}")
        println("Captures logicielles : ${result.capturePlaceholders.size}")
    }
}
