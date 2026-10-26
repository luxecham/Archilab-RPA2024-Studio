package com.archilab.rpa2024.testing

import com.archilab.rpa2024.report.ReferenceDocxImageImporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Itération 3 : extraction contrôlée des médias d'un DOCX de référence. */
class ReferenceDocxImageImporterTest {

    private fun fakeDocx(directory: Path, entries: List<Pair<String, ByteArray>>): Path {
        val file = directory.resolve("REFERENCE.docx")
        ZipOutputStream(Files.newOutputStream(file)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `les images png et jpg du DOCX sont extraites et nommees sans traversee`(@TempDir temp: Path) {
        val docx = fakeDocx(temp, listOf(
            "word/media/image1.png" to byteArrayOf(1, 2, 3),
            "word/media/image2.jpg" to byteArrayOf(4, 5),
            "word/media/image3.svg" to byteArrayOf(6),
            "word/media/../evil.png" to byteArrayOf(7)
        ))
        val target = temp.resolve("extracted")
        val result = ReferenceDocxImageImporter.extract(docx, target)
        assertEquals(3, result.count)
        assertTrue(result.images.all { Files.exists(it) && it.parent == target })
        assertTrue(result.images.none { it.fileName.toString().contains("..") })
    }

    @Test
    fun `un fichier non docx est refuse`(@TempDir temp: Path) {
        val txt = temp.resolve("REFERENCE.txt")
        Files.writeString(txt, "pas un docx")
        val failed = runCatching { ReferenceDocxImageImporter.extract(txt, temp.resolve("out")) }.isFailure
        assertTrue(failed)
    }
}
