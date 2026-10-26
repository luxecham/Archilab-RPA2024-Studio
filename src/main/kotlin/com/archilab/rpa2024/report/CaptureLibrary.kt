package com.archilab.rpa2024.report

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Resultat de l'injection des captures dans le document de note. */
data class CaptureApplication(
    val document: ReportDocument,
    val appliedTags: List<String> = emptyList(),
    val missingTags: List<String> = emptyList(),
    val invalidFiles: List<String> = emptyList()
) {
    val appliedCount: Int get() = appliedTags.size
    val isComplete: Boolean get() = missingTags.isEmpty() && invalidFiles.isEmpty()
}

/**
 * Gestion des captures logicielles de la note de calcul.
 *
 * Le composeur de reference insere des blocs `[FIGURE_CAPTURE_LOGICIEL : ...]`.
 * L'utilisateur associe ensuite un PNG/JPG à chaque repère depuis l'interface ; la
 * note Word/PDF reçoit alors l'image réelle à la place du texte de repère.
 *
 * Le mapping est volontairement indépendant de Word et PDF : il transforme le modèle
 * de document commun, ce qui garantit le même rendu dans les deux exports.
 */
object CaptureLibrary {

    const val PREFIX = "[FIGURE_CAPTURE_LOGICIEL :"

    private val supportedExtensions = setOf("png", "jpg", "jpeg", "bmp", "gif")

    /** Extrait le libellé exact d'un repère de capture, ou null si le bloc n'en est pas un. */
    fun placeholderTag(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return null
        val end = trimmed.lastIndexOf(']')
        if (end < PREFIX.length) return null
        return trimmed.substring(PREFIX.length, end).trim()
    }

    /** Liste ordonnée des repères présents dans le document. */
    fun placeholderTags(document: ReportDocument): List<String> =
        document.allChapters.flatMap { chapter ->
            chapter.blocks.mapNotNull { block ->
                (block as? ReportBlock.Paragraph)?.let { placeholderTag(it.text) }
            }
        }.distinct()

    fun isSupportedImage(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        if (path.fileName.toString().substringAfterLast('.', "").lowercase() !in supportedExtensions) {
            return false
        }
        return runCatching { ImageIO.read(path.toFile()) != null }.getOrDefault(false)
    }

    /**
     * Remplace les repères associés par des figures réelles. Les repères sans image
     * restent visibles tels quels : aucune capture n'est inventée ni silencieusement
     * supprimée.
     */
    fun apply(document: ReportDocument, captures: Map<String, Path>): CaptureApplication {
        val tags = captures.entries
            .filter { it.value.fileName.toString().isNotBlank() }
            .associate { it.key to it.value }
        val applied = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val pngByTag = mutableMapOf<String, ByteArray>()

        fun rewrite(chapter: ReportChapter): ReportChapter = chapter.copy(
            blocks = chapter.blocks.map { block ->
                if (block !is ReportBlock.Paragraph) return@map block
                val tag = placeholderTag(block.text) ?: return@map block
                val path = tags[tag]
                if (path == null) {
                    missing += tag
                    return@map block
                }
                if (!isSupportedImage(path)) {
                    invalid += "${path.fileName} ($tag)"
                    missing += tag
                    return@map block
                }
                val image = runCatching { ImageIO.read(path.toFile()) }.getOrNull()
                if (image == null) {
                    invalid += "${path.fileName} ($tag)"
                    missing += tag
                    return@map block
                }
                // Normalisation en PNG : le generateur Word embarque les figures sous
                // le type PNG ; les JPEG/BMP/GIF sont donc convertis avant insertion.
                val bytes = java.io.ByteArrayOutputStream().use { output ->
                    ImageIO.write(image, "png", output)
                    output.toByteArray()
                }
                applied += tag
                pngByTag[tag] = bytes
                ReportBlock.Figure(
                    png = bytes,
                    caption = "Figure — $tag",
                    widthCm = 16.0,
                    key = "capture-${tag.hashCode()}-${bytes.size}"
                )
            }
        )

        // La capture « Vue 3D globale » sert aussi de visuel de couverture, comme dans
        // la note de référence : l'utilisateur ne saisit pas deux fois la même image.
        val coverBytes = pngByTag.entries.firstOrNull { it.key.contains("Vue 3D", ignoreCase = true) }?.value
        val rewrittenCover = if (coverBytes != null) document.cover.copy(view3d = coverBytes) else document.cover
        return CaptureApplication(
            document = document.copy(
                cover = rewrittenCover,
                chapters = document.chapters.map(::rewrite),
                annexes = document.annexes.map(::rewrite)
            ),
            appliedTags = applied.distinct(),
            missingTags = missing.distinct(),
            invalidFiles = invalid.distinct()
        )
    }

    /**
     * Association par lot : les images sont triées par nom puis affectées dans l'ordre
     * des repères. L'utilisateur peut ensuite remplacer une association individuellement.
     */
    fun applyByOrder(document: ReportDocument, imageFiles: List<Path>): Pair<CaptureApplication, Map<String, Path>> {
        val tags = placeholderTags(document)
        val images = imageFiles.filter(::isSupportedImage).sortedBy { it.fileName.toString().lowercase() }
        val mapping = tags.zip(images).toMap()
        return apply(document, mapping) to mapping
    }
}
