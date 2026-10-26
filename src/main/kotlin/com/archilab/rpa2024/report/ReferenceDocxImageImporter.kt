package com.archilab.rpa2024.report

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Résultat de l'extraction des médias d'un DOCX de référence. */
data class ReferenceImageExtraction(
    val directory: Path,
    val images: List<Path>,
    val skipped: Int
) {
    val count: Int get() = images.size
}

/**
 * Extrait les images d'un DOCX de référence (`word/media/`) vers un dossier de travail.
 * L'utilisateur peut ensuite les associer aux repères de la note avec l'import par lot.
 *
 * Aucune image n'est injectée automatiquement dans la note : les captures de projet
 * doivent rester choisies et contrôlées par l'ingénieur.
 */
object ReferenceDocxImageImporter {

    private val supported = setOf("png", "jpg", "jpeg", "bmp", "gif")

    fun extract(document: Path, destination: Path): ReferenceImageExtraction {
        require(Files.isRegularFile(document)) { "DOCX introuvable : $document" }
        require(document.fileName.toString().lowercase().endsWith(".docx")) {
            "Le fichier de référence doit être un .docx : ${document.fileName}"
        }
        Files.createDirectories(destination)
        val images = mutableListOf<Path>()
        var skipped = 0
        ZipFile(document.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            for (entry in entries) {
                val name = entry.name
                if (!name.startsWith("word/media/", ignoreCase = true)) continue
                val extension = name.substringAfterLast('.', "").lowercase()
                if (extension !in supported) {
                    skipped++
                    continue
                }
                val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (safeName.isBlank()) {
                    skipped++
                    continue
                }
                var target = destination.resolve(safeName)
                var suffix = 1
                while (Files.exists(target)) {
                    target = destination.resolve("${safeName.substringBeforeLast('.')}_${suffix++}." +
                        safeName.substringAfterLast('.'))
                }
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(target).use { output -> input.copyTo(output) }
                }
                images.add(target)
            }
        }
        return ReferenceImageExtraction(destination, images, skipped)
    }
}
