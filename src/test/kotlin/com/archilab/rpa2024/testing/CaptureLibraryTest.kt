package com.archilab.rpa2024.testing

import com.archilab.rpa2024.report.CaptureLibrary
import com.archilab.rpa2024.report.CtcWordReportGenerator
import com.archilab.rpa2024.report.NoteDeCalculRequest
import com.archilab.rpa2024.report.PdfReportExporter
import com.archilab.rpa2024.report.ReferenceNoteDeCalculComposer
import com.archilab.rpa2024.report.ReportBlock
import com.archilab.rpa2024.sample.SampleJointA
import com.archilab.rpa2024.sample.SampleSafeFoundation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

/**
 * Itération 2 : les captures logicielles ne restent plus des repères textuels.
 * L'utilisateur associe une image et les exports Word/PDF reçoivent réellement le média.
 */
class CaptureLibraryTest {

    private fun referenceArtifacts() = ReferenceNoteDeCalculComposer().compose(
        NoteDeCalculRequest(
            model = SampleJointA.load().model,
            results = SampleJointA.load().results,
            foundations = SampleSafeFoundation.load(),
            projectTitle = "1500 LLV BOSQUET",
            wilaya = "Sidi Bel Abbès",
            client = "Direction du Logement — Sidi Bel Abbès",
            engineer = "Archilab — Bureau d'études structures",
            reference = "NDC-JOINT-A-RPA2024",
            revision = "A"
        )
    )

    private fun writePng(directory: Path, name: String = "capture.png"): Path {
        val image = BufferedImage(80, 50, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color(23, 54, 93)
        graphics.fillRect(0, 0, 80, 50)
        graphics.color = Color.WHITE
        graphics.drawString("ETABS", 18, 28)
        graphics.dispose()
        val path = directory.resolve(name)
        ImageIO.write(image, "png", path.toFile())
        return path
    }

    @Test
    fun `une capture associee remplace le repere dans le document`(@TempDir temp: Path) {
        val reference = referenceArtifacts()
        val tag = reference.capturePlaceholders.first()
        val image = writePng(temp)
        val application = CaptureLibrary.apply(
            reference.document,
            mapOf(tag to image)
        )
        assertEquals(1, application.appliedCount)
        assertTrue(application.appliedTags.contains(tag))
        assertTrue(application.missingTags.size >= reference.capturePlaceholders.size - 1)
        assertTrue(
            application.document.allChapters.any { chapter ->
                chapter.blocks.any { it is ReportBlock.Figure && it.caption.contains(tag) }
            }
        )
        // Le premier repère est la vue 3D : il fournit aussi le visuel de couverture.
        assertTrue(application.document.cover.view3d != null)
    }

    @Test
    fun `les captures associees sont physiquement presentes dans Word et PDF`(@TempDir temp: Path) {
        val reference = referenceArtifacts()
        val tag = reference.capturePlaceholders.first()
        val image = writePng(temp)
        val application = CaptureLibrary.apply(
            reference.document,
            mapOf(tag to image)
        )
        val wordPath = temp.resolve("NOTE_CAPTURES.docx")
        CtcWordReportGenerator().write(application.document, wordPath)
        val media = ZipInputStream(Files.newInputStream(wordPath)).use { zip ->
            generateSequence { zip.nextEntry }.count { it.name.startsWith("word/media/") }
        }
        assertTrue(media > 0, "Le DOCX doit contenir au moins une image réelle.")
        val pdf = PdfReportExporter().bytes(application.document)
        assertTrue(pdf.size > 10_000)
        assertTrue(String(pdf, Charsets.ISO_8859_1).contains("/Subtype /Image"))
    }

    @Test
    fun `un fichier non image laisse le repere et signale l erreur`(@TempDir temp: Path) {
        val reference = referenceArtifacts()
        val tag = reference.capturePlaceholders.first()
        val fake = temp.resolve("capture.txt")
        Files.writeString(fake, "ceci n'est pas une image")
        val application = CaptureLibrary.apply(
            reference.document,
            mapOf(tag to fake)
        )
        assertEquals(0, application.appliedCount)
        assertEquals(1, application.invalidFiles.size)
        assertFalse(application.isComplete)
        assertTrue(application.missingTags.contains(tag))
    }

    @Test
    fun `l import par lot associe les images dans l ordre des reperes`(@TempDir temp: Path) {
        val reference = referenceArtifacts()
        val images = listOf(
            writePng(temp, "01_vue3d.png"),
            writePng(temp, "02_plan.png"),
            writePng(temp, "03_materiaux.png")
        )
        val (application, mapping) = CaptureLibrary.applyByOrder(reference.document, images)
        assertEquals(3, mapping.size)
        assertEquals(3, application.appliedCount)
        assertEquals(reference.capturePlaceholders.take(3).toSet(), mapping.keys)
    }
}
