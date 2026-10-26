package com.archilab.rpa2024.report

import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.xmlbeans.XmlCursor

/**
 * Générateur d'équations mathématiques au standard natif Microsoft Word OMML (Office Math Markup Language).
 * Permet l'insertion directe de formules mathématiques vectorielles et éditables dans le document .docx.
 */
object OmmlEquationBuilder {

    private const val OMML_NS = "xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\""

    fun buildFraction(numerator: String, denominator: String): String {
        return "<m:f $OMML_NS><m:num><m:r><m:t>$numerator</m:t></m:r></m:num><m:den><m:r><m:t>$denominator</m:t></m:r></m:den></m:f>"
    }

    /**
     * Formule de l'effort normal réduit des poteaux : ν = Nd / (Bc · fc28) ≤ 0.35
     */
    fun buildReducedNormalStressEquation(): String {
        return """
            <m:oMathPara $OMML_NS>
              <m:oMath>
                <m:r><m:t>ν = </m:t></m:r>
                <m:f>
                  <m:num><m:r><m:t>N</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub></m:num>
                  <m:den><m:r><m:t>B</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>c</m:t></m:r></m:sub></m:sSub><m:r><m:t> · f</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub></m:den>
                </m:f>
                <m:r><m:t> ≤ 0.35</m:t></m:r>
              </m:oMath>
            </m:oMathPara>
        """.trimIndent()
    }

    /**
     * Formule des déplacements inter-étages : νA · Δk ≤ Δk_adm
     */
    fun buildStoryDriftEquation(): String {
        return """
            <m:oMathPara $OMML_NS>
              <m:oMath>
                <m:r><m:t>ν</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>A</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> · Δ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> ≤ </m:t></m:r>
                <m:acc><m:accPr><m:chr m:val="¯"/></m:accPr><m:e><m:r><m:t>Δ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:e></m:acc>
                <m:r><m:t> = 0.0075 · h</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub>
              </m:oMath>
            </m:oMathPara>
        """.trimIndent()
    }

    /**
     * Formule de la stabilité au 2nd ordre : θk = (Pk · Δk) / (Vk · hk) ≤ 0.10
     */
    fun buildPDeltaEquation(): String {
        return """
            <m:oMathPara $OMML_NS>
              <m:oMath>
                <m:r><m:t>θ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> = </m:t></m:r>
                <m:f>
                  <m:num><m:r><m:t>P</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> · Δ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:num>
                  <m:den><m:r><m:t>V</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> · h</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:den>
                </m:f>
                <m:r><m:t> ≤ 0.10</m:t></m:r>
              </m:oMath>
            </m:oMathPara>
        """.trimIndent()
    }

    /**
     * Formule de la force sismique totale statique : VMSE = λ · (Sad/g) · W
     */
    fun buildBaseShearEquation(): String {
        return """
            <m:oMathPara $OMML_NS>
              <m:oMath>
                <m:r><m:t>V</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>MSE</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> = λ · </m:t></m:r>
                <m:f>
                  <m:num><m:r><m:t>S</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>ad</m:t></m:r></m:sub></m:sSub></m:num>
                  <m:den><m:r><m:t>g</m:t></m:r></m:den>
                </m:f>
                <m:r><m:t>(T</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>0</m:t></m:r></m:sub></m:sSub><m:r><m:t>) · W</m:t></m:r>
              </m:oMath>
            </m:oMathPara>
        """.trimIndent()
    }

    /**
     * Formule de contrainte de cisaillement des voiles : τb = 1.4 · Vu / (bw · d) ≤ 0.20 · fc28
     */
    fun buildWallShearStressEquation(): String {
        return """
            <m:oMathPara $OMML_NS>
              <m:oMath>
                <m:r><m:t>τ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>b</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> = </m:t></m:r>
                <m:f>
                  <m:num><m:r><m:t>1.40 · V</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>u</m:t></m:r></m:sub></m:sSub></m:num>
                  <m:den><m:r><m:t>b</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>w</m:t></m:r></m:sub></m:sSub><m:r><m:t> · d</m:t></m:r></m:den>
                </m:f>
                <m:r><m:t> ≤ 0.20 · f</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub>
              </m:oMath>
            </m:oMathPara>
        """.trimIndent()
    }

    /**
     * Formule de contrainte de sol SAFE : σeff = Δmax · Ks ≤ σsol_adm
     */
    fun buildSoilStressEquation(): String {
        return """
            <m:oMathPara $OMML_NS>
              <m:oMath>
                <m:r><m:t>σ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>eff</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> = Δ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>max</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> · K</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>s</m:t></m:r></m:sub></m:sSub>
                <m:r><m:t> ≤ </m:t></m:r>
                <m:acc><m:accPr><m:chr m:val="¯"/></m:accPr><m:e><m:r><m:t>σ</m:t></m:r><m:sSub><m:sSubPr/><m:e/><m:sub><m:r><m:t>sol</m:t></m:r></m:sub></m:sSub></m:e></m:acc>
              </m:oMath>
            </m:oMathPara>
        """.trimIndent()
    }

    /**
     * Insère un bloc d'équation OMML dans un paragraphe Apache POI XWPFParagraph.
     */
    fun insertOmmlEquation(paragraph: XWPFParagraph, ommlXmlContent: String) {
        val mathXml = if (ommlXmlContent.contains("<m:oMathPara") || ommlXmlContent.contains("<m:oMath")) {
            ommlXmlContent
        } else {
            "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">$ommlXmlContent</m:oMath>"
        }
        try {
            val cursor: XmlCursor = paragraph.ctp.newCursor()
            cursor.toEndToken()
            val mathObj = org.apache.xmlbeans.XmlObject.Factory.parse(mathXml)
            val mathCursor = mathObj.newCursor()
            mathCursor.copyXml(cursor)
            mathCursor.dispose()
            cursor.dispose()
        } catch (_: Exception) {
            val run = paragraph.createRun()
            run.isItalic = true
            run.setText(extractReadableFormula(ommlXmlContent))
        }
    }

    fun buildEmpiricalPeriodOmml(): String =
        "<m:r><m:t>T</m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>empirique</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> = </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>C</m:t></m:r></m:e><m:sub><m:r><m:t>T</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> · </m:t></m:r>" +
        "<m:sSup><m:e><m:r><m:t>h</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>N</m:t></m:r></m:sub></m:sSub></m:e><m:sup><m:f><m:num><m:r><m:t>3</m:t></m:r></m:num><m:den><m:r><m:t>4</m:t></m:r></m:den></m:f></m:sup></m:sSup>"

    fun buildSeismicForceOmml(): String =
        "<m:r><m:t>V = λ · </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>S</m:t></m:r></m:e><m:sub><m:r><m:t>ad</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:r><m:t>g</m:t></m:r></m:den></m:f>" +
        "<m:r><m:t> · W</m:t></m:r>"

    fun buildReducedAxialForceColumnOmml(): String =
        "<m:r><m:t>ν = </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>N</m:t></m:r></m:e><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>B</m:t></m:r></m:e><m:sub><m:r><m:t>c</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>f</m:t></m:r></m:e><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.35</m:t></m:r>"

    fun buildReducedAxialForceWallOmml(): String =
        "<m:r><m:t>ν</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub><m:r><m:t> = </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>N</m:t></m:r></m:e><m:sub><m:r><m:t>d</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>B</m:t></m:r></m:e><m:sub><m:r><m:t>c</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>f</m:t></m:r></m:e><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.40</m:t></m:r>"

    fun buildPDeltaOmml(): String =
        "<m:sSub><m:e><m:r><m:t>θ</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> = </m:t></m:r>" +
        "<m:f><m:num><m:sSub><m:e><m:r><m:t>P</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> · Δ</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>V</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>h</m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.10</m:t></m:r>"

    fun buildInterstoryDriftOmml(): String =
        "<m:sSub><m:e><m:r><m:t>ν</m:t></m:r></m:e><m:sub><m:r><m:t>A</m:t></m:r></m:sub></m:sSub><m:r><m:t> · Δ</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>k</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> ≤ </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>Δ</m:t></m:r></m:e><m:sub><m:r><m:t>k,adm</m:t></m:r></m:sub></m:sSub>"

    fun buildShearStressWallOmml(): String =
        "<m:sSub><m:e><m:r><m:t>τ</m:t></m:r></m:e><m:sub><m:r><m:t>b</m:t></m:r></m:sub></m:sSub><m:r><m:t> = </m:t></m:r>" +
        "<m:f><m:num><m:r><m:t>1.40 · </m:t></m:r><m:sSub><m:e><m:r><m:t>V</m:t></m:r></m:e><m:sub><m:r><m:t>u</m:t></m:r></m:sub></m:sSub></m:num><m:den><m:sSub><m:e><m:r><m:t>b</m:t></m:r></m:e><m:sub><m:r><m:t>w</m:t></m:r></m:sub></m:sSub><m:r><m:t> · d</m:t></m:r></m:den></m:f>" +
        "<m:r><m:t> ≤ 0.20 · </m:t></m:r><m:sSub><m:e><m:r><m:t>f</m:t></m:r></m:e><m:sub><m:r><m:t>c28</m:t></m:r></m:sub></m:sSub>"

    fun buildSoilStressOmml(): String =
        "<m:sSub><m:e><m:r><m:t>σ</m:t></m:r></m:e><m:sub><m:r><m:t>eff</m:t></m:r></m:sub></m:sSub><m:r><m:t> = Δ</m:t></m:r><m:sSub><m:e><m:r><m:t></m:t></m:r></m:e><m:sub><m:r><m:t>max</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> · </m:t></m:r><m:sSub><m:e><m:r><m:t>K</m:t></m:r></m:e><m:sub><m:r><m:t>s</m:t></m:r></m:sub></m:sSub>" +
        "<m:r><m:t> ≤ </m:t></m:r><m:sSub><m:e><m:r><m:t>σ</m:t></m:r></m:e><m:sub><m:r><m:t>sol,adm</m:t></m:r></m:sub></m:sSub>"

    fun buildParapetForceOmml(): String =
        "<m:sSub><m:e><m:r><m:t>F</m:t></m:r></m:e><m:sub><m:r><m:t>p</m:t></m:r></m:sub></m:sSub><m:r><m:t> = 4.0 · A · </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>C</m:t></m:r></m:e><m:sub><m:r><m:t>p</m:t></m:r></m:sub></m:sSub><m:r><m:t> · </m:t></m:r>" +
        "<m:sSub><m:e><m:r><m:t>W</m:t></m:r></m:e><m:sub><m:r><m:t>p</m:t></m:r></m:sub></m:sSub>"

    private fun extractReadableFormula(omml: String): String {
        return omml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
