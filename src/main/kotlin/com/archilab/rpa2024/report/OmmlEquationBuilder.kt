package com.archilab.rpa2024.report

import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.xmlbeans.XmlCursor

/**
 * Utilitare de génération d'équations mathématiques natives Microsoft Word (OMML : Office Math Markup Language).
 * Permet d'insérer des formules éditables dans le document Word (.docx) sans passer par des images basse résolution.
 */
object OmmlEquationBuilder {

    private const val OMML_NS = "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\""

    /**
     * Insère un bloc d'équation OMML dans un paragraphe Apache POI XWPFParagraph.
     */
    fun insertOmmlEquation(paragraph: XWPFParagraph, ommlXmlContent: String) {
        val mathXml = "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">$ommlXmlContent</m:oMath>"
        try {
            val cursor: XmlCursor = paragraph.getCTP().newCursor()
            cursor.toEndToken()
            val mathObj = org.apache.xmlbeans.XmlObject.Factory.parse(mathXml)
            val mathCursor = mathObj.newCursor()
            mathCursor.copyXml(cursor)
            mathCursor.dispose()
            cursor.dispose()
        } catch (_: Exception) {
            // Fallback textuel si XMLBeans est en mode restreint
            val run = paragraph.createRun()
            run.isItalic = true
            run.setText(extractReadableFormula(ommlXmlContent))
        }
    }

    /**
     * Équation OMML : Période fondamentale empirique T_emp = C_T * h_N^(3/4)
     */
    fun buildEmpiricalPeriodOmml(): String =
        "<m:r><m:t>T</m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>empirique</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> = </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>C</m:t></m:r></m:e><m:sub><m:r><m:t>T</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> · </m:t></m:r>" +
        "<m:sSup><m:e><m:r><m:t>h</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>N</m:t></m:r></m:sub></m:sSub></m:e><m:sup><m:f><m:num><m:r><m:t>3</m:t></m:r></m:num><m:den><m:r><m:t>4</m:t></m:r></m:den></m:f></m:sup></m:sSup>"

    /**
     * Équation OMML : Force sismique totale V = lambda * (Sad / g) * W
     */
    fun buildSeismicForceOmml(): String =
        "<m:r><m:t>V = λ · </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>S</m:t></m:r></m:e><m:sub><m:r><m:t>ad</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:r><m:t>g</m:t></m:r></m:den></m:f>" +
        "<m:r><m:t> · W</m:t></m:r>"

    /**
     * Équation OMML : Effort normal réduit poteaux nu = Nd / (Bc * fc28) <= 0.35
     */
    fun buildReducedAxialForceColumnOmml(): String =
        "<m:r><m:t>ν = </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>N</m:t></m:r></m:e><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>B</m:t></m:r></m:e><m:sub><m:r><m:t>c</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>f</m:t></m:r></m:e><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.35</m:t></m:r>"

    /**
     * Équation OMML : Effort normal réduit voiles nu_d = Nd / (Bc * fc28) <= 0.40
     */
    fun buildReducedAxialForceWallOmml(): String =
        "<m:r><m:t>ν</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub><m:r><m:t> = </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>N</m:t></m:r></m:e><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>B</m:t></m:r></m:e><m:sub><m:r><m:t>c</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>f</m:t></m:r></m:e><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.40</m:t></m:r>"

    /**
     * Équation OMML : Effet P-Delta theta_k = (Pk * Delta_k) / (Vk * hk) <= 0.10
     */
    fun buildPDeltaOmml(): String =
        "<m:sSub><m:e><m:r><m:t>θ</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> = </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>P</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> · Δ</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>V</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>h</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.10</m:t></m:r>"

    /**
     * Équation OMML : Limitation des déplacements inter-étages nu_A * Delta_k <= Delta_bar_k
     */
    fun buildInterstoryDriftOmml(): String =
        "<m:sSub><m:e><m:r><m:t>ν</m:t></m:r></m:e><m:sub><m:r><m:t>A</m:t></m:r></m:sub></m:sSub><m:r><m:t> · Δ</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> ≤ </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>Δ</m:t></m:r></m:e><m:sub><m:r><m:t>k,adm</m:t></m:r></m:sub></m:sSub>"

    /**
     * Équation OMML : Cisaillement conventionnel voile tau_b = (1.4 * Vu) / (bw * d) <= 0.20 * fc28
     */
    fun buildShearStressWallOmml(): String =
        "<m:sSub><m:e><m:r><m:t>τ</m:t></m:r></m:e><m:sub><m:r><m:t>b</m:t></m:r></m:sub></m:sSub><m:r><m:t> = </m:t></m:r>" +
        "<m:f><m:num><m:r><m:t>1.40 · </m:t></m:r><m:sSub><m:e><m:r><m:t>V</m:t></m:r></m:e><m:sub><m:r><m:t>u</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>b</m:t></m:r></m:e><m:sub><m:r><m:t>w</m:t></m:r></m:sub></m:sSub><m:r><m:t> · d</m:t></m:r></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.20 · </m:t></m:r><m:sSub><m:e><m:r><m:t>f</m:t></m:r></m:e><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub>"

    /**
     * Équation OMML : Contrainte du sol sigma = Delta_max * Ks <= sigma_adm
     */
    fun buildSoilStressOmml(): String =
        "<m:sSub><m:e><m:r><m:t>σ</m:t></m:r></m:e><m:sub><m:r><m:t>eff</m:t></m:r></m:sub></m:sSub><m:r><m:t> = Δ</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>max</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>K</m:t></m:r></m:e><m:sub><m:r><m:t>s</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> ≤ </m:t></m:r><m:sSub><m:e><m:r><m:t>σ</m:t></m:r></m:e><m:sub><m:r><m:t>sol,adm</m:t></m:r></m:sub></m:sSub>"

    /**
     * Équation OMML : Force sismique sur acrotère Fp = 4.0 * A * Cp * Wp
     */
    fun buildParapetForceOmml(): String =
        "<m:sSub><m:e><m:r><m:t>F</m:t></m:r></m:e><m:sub><m:r><m:t>p</m:t></m:r></m:sub></m:sSub><m:r><m:t> = 4.0 · A · </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>C</m:t></m:r></m:e><m:sub><m:r><m:t>p</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>W</m:t></m:r></m:e><m:sub><m:r><m:t>p</m:t></m:r></m:sub></m:sSub>"

    private fun extractReadableFormula(omml: String): String {
        return omml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
