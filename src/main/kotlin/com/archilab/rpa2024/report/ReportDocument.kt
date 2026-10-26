package com.archilab.rpa2024.report

import com.archilab.rpa2024.engine.geometry.CheckStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Alignement horizontal d'un contenu de rapport. */
enum class ReportAlign { LEFT, CENTER, RIGHT, JUSTIFY }

/**
 * Bloc elementaire d'une note de calcul. Le modele est volontairement independant de
 * Word et de PDF : le generateur DOCX et l'export PDF consomment la meme liste de blocs.
 */
sealed class ReportBlock {

    data class Heading(
        val level: Int,
        val text: String,
        val bookmark: String
    ) : ReportBlock()

    data class Paragraph(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val align: ReportAlign = ReportAlign.JUSTIFY,
        val sizePt: Int = 11,
        val colorHex: String? = null,
        val spaceBeforePt: Int = 0,
        val spaceAfterPt: Int = 4,
        val keepWithNext: Boolean = false
    ) : ReportBlock()

    data class Bullets(val items: List<String>) : ReportBlock()

    data class Numbered(val items: List<String>) : ReportBlock()

    data class Equation(
        val nodes: List<OmmlNode>,
        val caption: String? = null
    ) : ReportBlock()

    data class Table(
        val caption: String?,
        val headers: List<String>,
        val rows: List<List<String>>,
        val columnWidthsCm: List<Double>? = null,
        val alignments: List<ReportAlign> = emptyList(),
        val headerFillHex: String = "1F3A5F",
        val headerTextHex: String = "FFFFFF",
        val zebra: Boolean = true,
        val fontSizePt: Int = 9,
        val firstColumnBold: Boolean = false
    ) : ReportBlock()

    class Figure(
        val png: ByteArray,
        val caption: String,
        val widthCm: Double = 16.5,
        val key: String
    ) : ReportBlock()

    data class Note(
        val text: String,
        val title: String = "Remarque"
    ) : ReportBlock()

    data class Verdict(
        val text: String,
        val status: CheckStatus
    ) : ReportBlock()

    data class ChecksTable(
        val caption: String?,
        val checks: List<com.archilab.rpa2024.engine.geometry.DesignCheck>
    ) : ReportBlock()

    data class KeyValues(
        val caption: String?,
        val entries: List<Pair<String, String>>
    ) : ReportBlock()

    data object PageBreak : ReportBlock()
}

/** Chapitre numerote de la note de calcul. */
data class ReportChapter(
    val number: Int,
    val title: String,
    val summary: String,
    val status: CheckStatus,
    val blocks: List<ReportBlock>,
    val numbered: Boolean = true
) {
    val bookmark: String get() = "chap$number"
    val headingText: String get() = if (numbered) "$number. $title" else title
}

/** Page de garde encadree du document CTC. */
data class ReportCover(
    val republicLine: String = "RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE",
    val ministryLine: String = "MINISTÈRE DE L'HABITAT, DE L'URBANISME ET DE LA VILLE",
    val wilaya: String,
    val ctcLine: String = "CONTROL TECHNIQUE DE LA CONSTRUCTION — CTC",
    val documentTitle: String = "NOTE DE CALCUL STRUCTURELLE",
    val subtitle: String = "Étude et vérification selon le RPA 2024 (DTR BC 2.48), le BAEL 91 révisé 99 et le CBA 93",
    val projectTitle: String,
    val jointName: String,
    val client: String,
    val engineer: String,
    val reference: String,
    val revision: String,
    val dateLabel: String,
    val view3d: ByteArray?,
    val summary: List<Pair<String, String>>
)

/** Document complet pret a etre ecrit en DOCX ou PDF. */
data class ReportDocument(
    val cover: ReportCover,
    val chapters: List<ReportChapter>,
    val annexes: List<ReportChapter> = emptyList(),
    val warnings: List<String> = emptyList(),
    val generatedOn: LocalDate = LocalDate.now()
) {
    val allChapters: List<ReportChapter> get() = chapters + annexes

    val status: CheckStatus
        get() = CheckStatus.worst(chapters.map { it.status })

    val failedCount: Int get() = chapters.count { it.status == CheckStatus.FAILED }
}

/** Formatage francais commun aux tableaux et paragraphes. */
object ReportText {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH)

    fun num(value: Double, decimals: Int = 2): String =
        String.format(Locale.FRANCE, "%,.${decimals}f", value)
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .trim()

    fun plain(value: Double, decimals: Int = 2): String =
        String.format(Locale.FRANCE, "%.${decimals}f", value)

    fun pct(value: Double, decimals: Int = 1): String = plain(value, decimals) + " %"

    fun date(value: LocalDate): String {
        val formatted = dateFormatter.format(value)
        return formatted.replaceFirstChar { it.uppercase(Locale.FRENCH) }
    }

    fun dateFr(value: LocalDate): String = dateFormatter.format(value)

    fun statusLabel(status: CheckStatus): String = when (status) {
        CheckStatus.OK -> "Vérifié"
        CheckStatus.WARNING -> "À vérifier"
        CheckStatus.FAILED -> "Non vérifié"
        CheckStatus.NOT_APPLICABLE -> "Non applicable"
    }

    fun statusColor(status: CheckStatus): String = when (status) {
        CheckStatus.OK -> "15803D"
        CheckStatus.WARNING -> "B45309"
        CheckStatus.FAILED -> "B91C1C"
        CheckStatus.NOT_APPLICABLE -> "4B5563"
    }

    fun list(items: List<String>, separator: String = ", "): String =
        items.filter { it.isNotBlank() }.joinToString(separator)

    fun plural(count: Int, singular: String, pluralForm: String = "${singular}s"): String =
        "$count ${if (count > 1) pluralForm else singular}"
}

/** Bibliotheque des formules natives inserees dans la note de calcul. */
object ReportEquations {

    /** Moyenne ponderee d'une combinaison ELU : 1,35 G + 1,50 Q. */
    fun ultimateCombination(): List<OmmlNode> = eq(
        mv("S"), msub(mv("u"), mt("ELU")), mt(" = 1,35 "), mv("G"), mt(" + 1,50 "), mv("Q")
    )

    /** Combinaison de service : G + Q. */
    fun serviceCombination(): List<OmmlNode> = eq(
        mv("S"), msub(mv("s"), mt("ELS")), mt(" = "), mv("G"), mt(" + "), mv("Q")
    )

    /** Combinaison accidentelle sismique : G + Q +/- E. */
    fun seismicCombination(): List<OmmlNode> = eq(
        mv("S"), msub(mv("a"), mt("ELA")), mt(" = "), mv("G"), mt(" + "), mv("Q"), mt(" ± "),
        mv("E"), mt("   avec  "), mv("ψ"), mt(" · "), mv("Q")
    )

    /** Poids sismique : W = G + psi . Q. */
    fun seismicWeight(): List<OmmlNode> = eq(
        mv("W"), mt(" = "), mv("G"), mt(" + "), mv("ψ"), mv("Q")
    )

    /** Pseudo-acceleration horizontale du spectre de calcul. */
    fun spectralAcceleration(): List<OmmlNode> = listOf(
        OmmlFraction(
            listOf(mv("S"), msub(mv("a"), mt("d")), mt("("), mv("T"), mt(")")),
            listOf(mv("g"))
        ),
        mt(" = "),
        mv("A"), mt(" · "), mv("I"), mt(" · "), mv("η"), mt(" · "),
        mfrac(
            listOf(mt("2,5 "), mv("Q"), mt(" · "), mv("S")),
            listOf(mv("R"))
        ),
        mt(" · "), mv("B"), mt("("), mv("T"), mt(")")
    )

    /** Plancher minimal du spectre de calcul : 0,2 A I. */
    fun spectrumFloor(): List<OmmlNode> = eq(
        msub(
            mdelim(
                listOf(
                    mfrac(
                        listOf(mv("S"), msub(mv("a"), mt("d"))),
                        listOf(mv("g"))
                    )
                )
            ),
            mt("min")
        ),
        mt(" = 0,2 "), mv("A"), mv("I")
    )

    /** Limite de branche : T1 = 0,10 s, T2 = 0,30 s, T3 = 3,00 s (site S3, type 2). */
    fun branchLimits(t1: Double, t2: Double, t3: Double): List<OmmlNode> = eq(
        msub(mv("T"), mt("1")), mt(" = "), mt(ReportText.plain(t1, 2)), mt(" s ; "),
        msub(mv("T"), mt("2")), mt(" = "), mt(ReportText.plain(t2, 2)), mt(" s ; "),
        msub(mv("T"), mt("3")), mt(" = "), mt(ReportText.plain(t3, 2)), mt(" s")
    )

    /** Effort tranchant statique equivalent : V = A D Q W / R. */
    fun staticBaseShear(): List<OmmlNode> = eq(
        mv("V"), mt(" = "),
        mfrac(
            listOf(mv("A"), mt(" · "), mv("D"), mt(" · "), mv("Q"), mt(" · "), mv("W")),
            listOf(mv("R"))
        ),
        mt("  avec  "), mv("D"), mt(" = "),
        mv("S"), mt("("), mv("T"), mt(")"), mt(" · "), mv("T")
    )

    /** Condition de la methode dynamique : Vdyn >= 0,8 Vstat. */
    fun dynamicBaseShear(): List<OmmlNode> = eq(
        msub(mv("V"), mt("dyn")), mt(" ≥ 0,80 "), msub(mv("V"), mt("stat")),
        mt("  ⇒  "), mv("λ"), mt(" = "), mfrac(
            listOf(mt("0,80 "), msub(mv("V"), mt("stat"))),
            listOf(msub(mv("V"), mt("dyn")))
        )
    )

    /** Deplacement relatif inter-etages et limite reglementaire. */
    fun interStoryDrift(): List<OmmlNode> = eq(
        msub(mv("δ"), mv("k")), mt(" = "), mv("ν"), mt(" · "),
        mdelim(
            listOf(
                msub(mv("d"), mv("k")), mt(" − "), msub(mv("d"), mt("k−1"))
            )
        ),
        mt(" ≤ "), msub(mv("δ"), mt("lim"))
    )

    /** Coefficient de sensibilite aux effets du second ordre. */
    fun pDelta(): List<OmmlNode> = eq(
        mv("θ"), mt(" = "),
        mfrac(
            listOf(
                msub(mv("P"), mv("k")), mt(" · "), msub(mv("Δ"), mv("k"))
            ),
            listOf(
                msub(mv("V"), mv("k")), mt(" · "), msub(mv("h"), mv("k"))
            )
        )
    )

    /** Moment reduit de flexion simple BAEL. */
    fun reducedMoment(): List<OmmlNode> = eq(
        msub(mv("μ"), mt("bu")), mt(" = "),
        mfrac(
            listOf(msub(mv("M"), mt("u"))),
            listOf(msub(mv("b"), mt("0")), mt(" · "), msup(mv("d"), mt("2")), mt(" · "), msub(mv("f"), mt("bu")))
        )
    )

    /** Bras de levier et section d'acier tendu. */
    fun steelArea(): List<OmmlNode> = eq(
        msub(mv("A"), mt("s")), mt(" = "),
        mfrac(
            listOf(msub(mv("M"), mt("u"))),
            listOf(msub(mv("z"), mt("b")), mt(" · "), msub(mv("f"), mt("su")))
        ),
        mt("   avec  "), mv("z"), mt(" = "),
        mv("d"), mt(" · "), mdelim(
            listOf(mt("1 − 0,4 "), mv("α"))
        )
    )

    /** Pourcentage minimal d'armatures des poteaux (RPA 2024 § 7.4.2.1). */
    fun columnMinimumSteel(): List<OmmlNode> = eq(
        msub(mv("A"), mt("s,min")), mt(" = 0,8 % · "), msub(mv("B"), mt("c")),
        mt("  et  "), mv("A"), msub(mt("s"), mt("max")), mt(" = 4,0 % · "),
        msub(mv("B"), mt("c"))
    )

    /** Effort normal reduit admissible d'un poteau (RPA 2024 § 7.4.3). */
    fun reducedAxial(): List<OmmlNode> = eq(
        mv("ν"), mt(" = "),
        mfrac(
            listOf(mv("N"), msub(mv("d"), mt(""))),
            listOf(msub(mv("B"), mt("c")), mt(" · "), msub(mv("f"), mt("c28")))
        ),
        mt(" ≤ 0,35")
    )

    /** Contrainte tangente conventionnelle. */
    fun shearStress(): List<OmmlNode> = eq(
        msub(mv("τ"), mt("u")), mt(" = "),
        mfrac(
            listOf(msub(mv("V"), mt("u"))),
            listOf(msub(mv("b"), mt("0")), mt(" · "), mv("d"))
        ),
        mt(" ≤ "), msub(mv("τ"), mt("lim"))
    )

    /** Moment isostatique de reference d'une poutrelle continue. */
    fun referenceMoment(): List<OmmlNode> = eq(
        msub(mv("M"), mt("0")), mt(" = "),
        mfrac(
            listOf(mv("q"), mt(" · "), msup(mv("L"), mt("2"))),
            listOf(mt("8"))
        )
    )

    /** Moment en travee et sur appui (methode forfaitaire BAEL). */
    fun spanAndSupportMoments(): List<OmmlNode> = eq(
        msub(mv("M"), mt("t")), mt(" ≥ 0,85 "), msub(mv("M"), mt("0")),
        mt("  ;  "), msub(mv("M"), mt("a")), mt(" ≥ 0,50 "), msub(mv("M"), mt("0"))
    )

    /** Contrainte effective du sol (modele de Winkler). */
    fun soilStress(): List<OmmlNode> = eq(
        msub(mv("σ"), mt("eff")), mt(" = "), mv("Δ"), mt(" · "), msub(mv("K"), mt("s")),
        mt(" ≤ "), msub(mv("σ"), mt("adm"))
    )

    /** Hauteur de la zone critique d'un voile (RPA 2024 Eq. 7.22 et 7.23). */
    fun wallCriticalHeight(): List<OmmlNode> = eq(
        msub(mv("h"), mt("cr")), mt(" = max"), mdelim(
            listOf(
                msub(mv("l"), mv("w")),
                mt(" ; "),
                mfrac(listOf(msub(mv("h"), mt("e"))), listOf(mt("6")))
            )
        ),
        mt(" ≤ "),
        mdelim(
            listOf(
                mfrac(listOf(msub(mv("h"), mt("e"))), listOf(mt("3"))),
                mt(" et 2 "), msub(mv("h"), mt("e"))
            ),
            "[", "]"
        )
    )

    /** Effort tranchant de calcul d'un voile amplifie : Ve = 1,4 Vu. */
    fun wallShearAmplification(): List<OmmlNode> = eq(
        msub(mv("V"), mt("e")), mt(" = 1,40 "), msub(mv("V"), mt("u"))
    )

    /** Flexion composee d'un voile : Nu et Mu en situation accidentelle. */
    fun wallDesign(): List<OmmlNode> = eq(
        msub(mv("N"), mt("u")), mt(" ≥ 0  ;  "), msub(mv("M"), mt("u")), mt(" = max"),
        mdelim(
            listOf(
                msub(mv("M"), mt("ELA")),
                mt(" ; "),
                msub(mv("N"), mt("ELA")), mt(" · "),
                mfrac(listOf(msub(mv("l"), mv("w"))), listOf(mt("2")))
            )
        )
    )

    /** Fleche totale nuisible BAEL : Delta ft = fgq - fji. */
    fun deflection(): List<OmmlNode> = eq(
        mv("Δ"), msub(mv("f"), mt("t")), mt(" = "), msub(mv("f"), mt("gq")), mt(" − "),
        msub(mv("f"), mt("ji")), mt(" ≤ "),
        mfrac(listOf(mv("L")), listOf(mt("500")))
    )

    /** Force sismique d'un element secondaire : Fp = 4 A Cp Wp. */
    fun secondarySeismicForce(): List<OmmlNode> = eq(
        msub(mv("F"), mv("p")), mt(" = 4 · "), mv("A"), mt(" · "), msub(mv("C"), mv("p")),
        mt(" · "), msub(mv("W"), mv("p"))
    )
}
