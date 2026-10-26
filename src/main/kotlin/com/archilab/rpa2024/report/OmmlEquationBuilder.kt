package com.archilab.rpa2024.report

import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.xmlbeans.XmlObject
import org.openxmlformats.schemas.officeDocument.x2006.math.CTOMath
import org.openxmlformats.schemas.officeDocument.x2006.math.CTOMathPara

private const val MATH_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"
private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

/**
 * Noeud OMML (Office Math Markup Language) minimal. Chaque noeud sait s'ecrire en XML
 * Office Math et se restituer en texte brut, ce qui permet d'alimenter a la fois le
 * document Word natif et l'export PDF sans dupliquer les formules.
 */
sealed class OmmlNode {

    internal abstract fun xml(): String

    /** Restitution lineaire utilisee par l'export PDF et les tests. */
    abstract fun plain(): String

    protected fun escape(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }
}

/** Exposant / indice / texte simple. */
class OmmlRun(
    val text: String,
    val upright: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = !upright
) : OmmlNode() {

    override fun xml(): String {
        val style = when {
            upright && bold -> "b"
            upright -> "p"
            bold && italic -> "bi"
            bold -> "b"
            italic -> "i"
            else -> "p"
        }
        return buildString {
            append("<m:r><m:rPr><m:scr m:val=\"roman\"/><m:sty m:val=\"")
            append(style)
            append("\"/></m:rPr><m:t xml:space=\"preserve\">")
            append(escape(text))
            append("</m:t></m:r>")
        }
    }

    override fun plain(): String = text
}

/** Fraction avec barre horizontale. */
class OmmlFraction(
    val numerator: List<OmmlNode>,
    val denominator: List<OmmlNode>
) : OmmlNode() {
    override fun xml(): String =
        "<m:f><m:fPr><m:type m:val=\"bar\"/></m:fPr><m:num>${numerator.joinToString("") { it.xml() }}</m:num>" +
            "<m:den>${denominator.joinToString("") { it.xml() }}</m:den></m:f>"

    override fun plain(): String =
        "(${numerator.joinToString("") { it.plain() }})/(${denominator.joinToString("") { it.plain() }})"
}

/** Racine carree ou racine n-ieme (degre optionnel). */
class OmmlRadical(
    val body: List<OmmlNode>,
    val degree: List<OmmlNode>? = null
) : OmmlNode() {
    override fun xml(): String = buildString {
        append("<m:rad><m:radPr>")
        if (degree == null) append("<m:degHide m:val=\"1\"/>")
        append("</m:radPr><m:deg>")
        degree?.forEach { append(it.xml()) }
        append("</m:deg><m:e>")
        body.forEach { append(it.xml()) }
        append("</m:e></m:rad>")
    }

    override fun plain(): String {
        val radicand = body.joinToString("") { it.plain() }
        return if (degree == null) {
            "sqrt($radicand)"
        } else {
            "root(${degree.joinToString("") { it.plain() }}, $radicand)"
        }
    }
}

/** Base affectee d'un indice inferieur. */
class OmmlSubscript(val base: List<OmmlNode>, val sub: List<OmmlNode>) : OmmlNode() {
    override fun xml(): String =
        "<m:sSub><m:e>${base.joinToString("") { it.xml() }}</m:e>" +
            "<m:sub>${sub.joinToString("") { it.xml() }}</m:sub></m:sSub>"

    override fun plain(): String = base.joinToString("") { it.plain() } + "_" + sub.joinToString("") { it.plain() }
}

/** Base affectee d'un exposant. */
class OmmlSuperscript(val base: List<OmmlNode>, val sup: List<OmmlNode>) : OmmlNode() {
    override fun xml(): String =
        "<m:sSup><m:e>${base.joinToString("") { it.xml() }}</m:e>" +
            "<m:sup>${sup.joinToString("") { it.xml() }}</m:sup></m:sSup>"

    override fun plain(): String = base.joinToString("") { it.plain() } + "^" + sup.joinToString("") { it.plain() }
}

/** Base affectee d'un indice et d'un exposant. */
class OmmlSubSuperscript(
    val base: List<OmmlNode>,
    val sub: List<OmmlNode>,
    val sup: List<OmmlNode>
) : OmmlNode() {
    override fun xml(): String =
        "<m:sSubSup><m:e>${base.joinToString("") { it.xml() }}</m:e>" +
            "<m:sub>${sub.joinToString("") { it.xml() }}</m:sub>" +
            "<m:sup>${sup.joinToString("") { it.xml() }}</m:sup></m:sSubSup>"

    override fun plain(): String =
        base.joinToString("") { it.plain() } + "_" + sub.joinToString("") { it.plain() } +
            "^" + sup.joinToString("") { it.plain() }
}

/** Groupe encadre par des delimiteurs (parentheses, crochets, accolades). */
class OmmlDelimiter(
    val body: List<OmmlNode>,
    val open: String = "(",
    val close: String = ")"
) : OmmlNode() {
    override fun xml(): String =
        "<m:d><m:dPr><m:begChr m:val=\"${escape(open)}\"/><m:endChr m:val=\"${escape(close)}\"/></m:dPr>" +
            "<m:e>${body.joinToString("") { it.xml() }}</m:e></m:d>"

    override fun plain(): String = open + body.joinToString("") { it.plain() } + close
}

/** Operateur n-aire (somme, produit, integrale). */
class OmmlNary(
    val operator: String,
    val sub: List<OmmlNode>? = null,
    val sup: List<OmmlNode>? = null,
    val body: List<OmmlNode>
) : OmmlNode() {
    override fun xml(): String = buildString {
        append("<m:nary><m:naryPr><m:chr m:val=\"")
        append(escape(operator))
        append("\"/><m:limLoc m:val=\"undOvr\"/>")
        if (sub == null) append("<m:subHide m:val=\"1\"/>")
        if (sup == null) append("<m:supHide m:val=\"1\"/>")
        append("</m:naryPr><m:sub>")
        sub?.forEach { append(it.xml()) }
        append("</m:sub><m:sup>")
        sup?.forEach { append(it.xml()) }
        append("</m:sup><m:e>")
        body.forEach { append(it.xml()) }
        append("</m:e></m:nary>")
    }

    override fun plain(): String = buildString {
        append(operator)
        if (sub != null) append("_").append(sub.joinToString("") { it.plain() })
        if (sup != null) append("^").append(sup.joinToString("") { it.plain() })
        append(" ")
        append(body.joinToString("") { it.plain() })
    }
}

/** Fonction mathematique appliquee a un argument : sin, cos, max... */
class OmmlFunction(val name: String, val argument: List<OmmlNode>) : OmmlNode() {
    override fun xml(): String =
        "<m:func><m:fName><m:r><m:rPr><m:sty m:val=\"p\"/></m:rPr>" +
            "<m:t xml:space=\"preserve\">${escape(name)}</m:t></m:r></m:fName>" +
            "<m:e>${argument.joinToString("") { it.xml() }}</m:e></m:func>"

    override fun plain(): String = "$name(${argument.joinToString("") { it.plain() }})"
}

/** Accent (chapeau, point, barre) porte par un corps. */
class OmmlAccent(val mark: String, val body: List<OmmlNode>) : OmmlNode() {
    override fun xml(): String =
        "<m:acc><m:accPr><m:chr m:val=\"${escape(mark)}\"/></m:accPr>" +
            "<m:e>${body.joinToString("") { it.xml() }}</m:e></m:acc>"

    override fun plain(): String = body.joinToString("") { it.plain() } + mark
}

/** Matrice rectangulaire (lignes de cellules). */
class OmmlMatrix(val rows: List<List<List<OmmlNode>>>) : OmmlNode() {
    override fun xml(): String = buildString {
        val columns = rows.maxOfOrNull { it.size } ?: 1
        append("<m:m><m:mPr><m:mcs><m:mc><m:mcPr><m:count m:val=\"")
        append(columns)
        append("\"/><m:mcJc m:val=\"center\"/></m:mcPr></m:mc></m:mcs></m:mPr>")
        rows.forEach { row ->
            append("<m:mr>")
            row.forEach { cell ->
                append("<m:e>")
                cell.forEach { append(it.xml()) }
                append("</m:e>")
            }
            repeat(columns - row.size) { append("<m:e/>") }
            append("</m:mr>")
        }
        append("</m:m>")
    }

    override fun plain(): String = rows.joinToString(" ; ") { row ->
        row.joinToString(" , ") { cell -> cell.joinToString("") { it.plain() } }
    }
}

/** Suite de noeuds traites comme un seul corps OMML. */
class OmmlGroup(val children: List<OmmlNode>) : OmmlNode() {
    override fun xml(): String = children.joinToString("") { it.xml() }
    override fun plain(): String = children.joinToString("") { it.plain() }
}

// ---------------------------------------------------------------------------
// Fabriques concises
// ---------------------------------------------------------------------------

/** Variable ou symbole mathematique (italique par defaut). */
fun mv(text: String): OmmlNode = OmmlRun(text)

/** Texte litteral en style romain (unites, indices textuels). */
fun mt(text: String): OmmlNode = OmmlRun(text, upright = true)

/** Texte romain gras (titres d'indices, mots-cles). */
fun mb(text: String): OmmlNode = OmmlRun(text, upright = true, bold = true)

/** Variable grecque ou lettre italique. */
fun mg(text: String): OmmlNode = OmmlRun(text, italic = true)

fun mfrac(numerator: OmmlNode, denominator: OmmlNode): OmmlNode =
    OmmlFraction(listOf(numerator), listOf(denominator))

fun mfrac(numerator: List<OmmlNode>, denominator: List<OmmlNode>): OmmlNode =
    OmmlFraction(numerator, denominator)

fun msub(base: OmmlNode, sub: OmmlNode): OmmlNode = OmmlSubscript(listOf(base), listOf(sub))
fun msub(base: List<OmmlNode>, sub: List<OmmlNode>): OmmlNode = OmmlSubscript(base, sub)
fun msup(base: OmmlNode, sup: OmmlNode): OmmlNode = OmmlSuperscript(listOf(base), listOf(sup))
fun msup(base: List<OmmlNode>, sup: List<OmmlNode>): OmmlNode = OmmlSuperscript(base, sup)
fun msubsup(base: OmmlNode, sub: OmmlNode, sup: OmmlNode): OmmlNode =
    OmmlSubSuperscript(listOf(base), listOf(sub), listOf(sup))

fun msqrt(body: OmmlNode): OmmlNode = OmmlRadical(listOf(body))
fun msqrt(body: List<OmmlNode>): OmmlNode = OmmlRadical(body)
fun mroot(degree: OmmlNode, body: OmmlNode): OmmlNode = OmmlRadical(listOf(body), listOf(degree))
fun mdelim(body: List<OmmlNode>, open: String = "(", close: String = ")"): OmmlNode =
    OmmlDelimiter(body, open, close)
fun mdelim(vararg body: OmmlNode): OmmlNode = OmmlDelimiter(body.toList())
fun msum(sub: OmmlNode?, sup: OmmlNode?, vararg body: OmmlNode): OmmlNode =
    OmmlNary("∑", sub?.let { listOf(it) }, sup?.let { listOf(it) }, body.toList())
fun mfunc(name: String, vararg argument: OmmlNode): OmmlNode = OmmlFunction(name, argument.toList())

/** Ecrit une suite de noeuds comme une seule chaine OMML terminee par un saut de ligne. */
fun eq(vararg nodes: OmmlNode): List<OmmlNode> = nodes.toList()

/** Concatene deux listes de noeuds (utile pour composer des formules longues). */
fun List<OmmlNode>.then(vararg next: OmmlNode): List<OmmlNode> = this + next

// ---------------------------------------------------------------------------
// Insertion dans un document Word
// ---------------------------------------------------------------------------

/**
 * Construction et insertion des equations OMML natives dans un document Word.
 *
 * Le document produit reste entierement editable : Word reconnait les balises
 * `m:oMath` comme des equations natives, sans image ni champ OLE.
 */
object OmmlEquationBuilder {

    fun mathXml(nodes: List<OmmlNode>): String = buildString {
        append("<m:oMath xmlns:m=\"")
        append(MATH_NS)
        append("\" xmlns:w=\"")
        append(WORD_NS)
        append("\">")
        nodes.forEach { append(it.xml()) }
        append("</m:oMath>")
    }

    fun mathParaXml(nodes: List<OmmlNode>): String = buildString {
        append("<m:oMathPara xmlns:m=\"")
        append(MATH_NS)
        append("\" xmlns:w=\"")
        append(WORD_NS)
        append("\"><m:oMathParaPr><m:jc m:val=\"center\"/></m:oMathParaPr><m:oMath>")
        nodes.forEach { append(it.xml()) }
        append("</m:oMath></m:oMathPara>")
    }

    /** Insere une equation en ligne dans un paragraphe existant. */
    fun appendInline(paragraph: XWPFParagraph, nodes: List<OmmlNode>) {
        if (nodes.isEmpty()) return
        val math = CTOMath.Factory.parse(mathXml(nodes))
        copyAtEnd(paragraph, math)
    }

    /** Insere une equation hors texte centree dans un paragraphe dedie. */
    fun appendDisplay(paragraph: XWPFParagraph, nodes: List<OmmlNode>) {
        if (nodes.isEmpty()) return
        val math = CTOMathPara.Factory.parse(mathParaXml(nodes))
        copyAtEnd(paragraph, math)
    }

    fun plainText(nodes: List<OmmlNode>): String = nodes.joinToString("") { it.plain() }

    private fun copyAtEnd(paragraph: XWPFParagraph, element: XmlObject) {
        val destination = paragraph.ctp.newCursor()
        destination.toEndToken()
        val source = element.newCursor()
        if (source.isStartdoc) source.toFirstChild()
        source.copyXml(destination)
        source.dispose()
        destination.dispose()
    }
}
