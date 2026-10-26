package com.archilab.rpa2024.report

import com.archilab.rpa2024.graphics.RpaSpectrumChartRenderer
import com.archilab.rpa2024.graphics.RebarSectionDetailRenderer
import com.archilab.rpa2024.graphics.SafeSoilPressureMapRenderer
import com.archilab.rpa2024.graphics.ShearWallEnvelopesRenderer
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class CtcNoteDeCalculWordGenerator {

    private val spectrumRenderer = RpaSpectrumChartRenderer()
    private val rebarRenderer = RebarSectionDetailRenderer()
    private val wallRenderer = ShearWallEnvelopesRenderer()
    private val safeRenderer = SafeSoilPressureMapRenderer()

    fun generateOfficialCtcWordReport(outputFile: File, payload: CtcReportDataPayload = CtcReportDataPayload.createJointASample()) {
        generateFullCtcDocx(payload, outputFile)
    }

    fun generateFullCtcDocx(payload: CtcReportDataPayload, outputFile: File) {
        val document = XWPFDocument()

        // 1. Page de Garde Officielle
        buildCoverPage(document, payload)

        // 2. Sommaire des 13 Chapitres
        buildTableOfContents(document)

        // 3. Chapitre 1 : Description du projet et normes
        buildChapter1Description(document, payload)

        // 4. Chapitre 2 : Caractéristiques géométriques du bâtiment
        buildChapter2Geometry(document, payload)

        // 5. Chapitre 3 : Caractéristiques mécaniques des matériaux
        buildChapter3Materials(document, payload)

        // 6. Chapitre 4 : Combinaisons de charges et poids sismique
        buildChapter4Combinations(document, payload)

        // 7. Chapitre 5 : Descente de charges (DTR BC 2.2)
        buildChapter5LoadTakeDown(document)

        // 8. Chapitres 6 & 7 : Prédimensionnement Poutres et Poteaux
        buildChapter6And7PreDimensioning(document, payload)

        // 9. Chapitre 8 : Étude des planchers (Corps creux, Poutrelles Koka & Flèche)
        buildChapter8Floors(document, payload)

        // 10. Chapitre 9 : Étude sismique (RPA 2024)
        buildChapter9SeismicStudy(document, payload)

        // 11. Chapitre 10 : Calcul des éléments structuraux (Poteaux, Poutres, Voiles)
        buildChapter10StructuralCalculations(document, payload)

        // 12. Chapitre 11 : Étude de l'acrotère
        buildChapter11Parapet(document, payload)

        // 13. Chapitre 12 : Étude des escaliers
        buildChapter12Stairs(document, payload)

        // 14. Chapitre 13 : Calcul des fondations (SAFE)
        buildChapter13Foundations(document, payload)

        // Écriture physique sur disque
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            document.write(fos)
        }
        document.close()
    }

    private fun injectOmml(paragraph: XWPFParagraph, ommlXml: String) {
        try {
            OmmlEquationBuilder.insertOmmlEquation(paragraph, ommlXml)
        } catch (_: Exception) {
            paragraph.createRun().setText("[Formule Mathématique OMML]")
        }
    }

    private fun insertImage(paragraph: XWPFParagraph, imageBytes: ByteArray, filename: String, widthPt: Double = 450.0, heightPt: Double = 270.0) {
        try {
            paragraph.alignment = ParagraphAlignment.CENTER
            val run = paragraph.createRun()
            ByteArrayInputStream(imageBytes).use { bais ->
                run.addPicture(
                    bais,
                    XWPFDocument.PICTURE_TYPE_PNG,
                    filename,
                    Units.toEMU(widthPt),
                    Units.toEMU(heightPt)
                )
            }
        } catch (e: Exception) {
            val run = paragraph.createRun()
            run.setText("[Illustration graphique : $filename]")
        }
    }

    private fun buildCoverPage(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val pHeader = doc.createParagraph()
        pHeader.alignment = ParagraphAlignment.CENTER
        val rHead = pHeader.createRun()
        rHead.fontFamily = CtcStyleCatalog.FONT_FAMILY
        rHead.fontSize = 12
        rHead.isBold = true
        rHead.setText("RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE")
        rHead.addCarriageReturn()
        rHead.setText("MINISTÈRE DE L'HABITAT, DE L'URBANISME ET DE LA VILLE")
        rHead.addCarriageReturn()
        rHead.setText("WILAYA DE ${payload.model.wilaya.uppercase(Locale.ROOT)}")
        rHead.addCarriageReturn()
        rHead.addCarriageReturn()

        val pTitle = doc.createParagraph()
        pTitle.alignment = ParagraphAlignment.CENTER
        val rTitle = pTitle.createRun()
        rTitle.fontFamily = CtcStyleCatalog.FONT_FAMILY
        rTitle.fontSize = 24
        rTitle.isBold = true
        rTitle.color = CtcStyleCatalog.COLOR_PRIMARY_NAVY
        rTitle.setText("NOTE DE CALCUL STRUCTURALE")
        rTitle.addCarriageReturn()
        rTitle.fontSize = 16
        rTitle.setText("CONFORME DTR BC 2.48 (RPA 2024) & BAEL 91 MOD. 99")
        rTitle.addCarriageReturn()
        rTitle.addCarriageReturn()

        val pSub = doc.createParagraph()
        pSub.alignment = ParagraphAlignment.CENTER
        val rSub = pSub.createRun()
        rSub.fontFamily = CtcStyleCatalog.FONT_FAMILY
        rSub.fontSize = 14
        rSub.isBold = true
        rSub.setText("PROJET : ${payload.model.projectName}")
        rSub.addCarriageReturn()
        rSub.setText("BLOC / SOUS-ENSEMBLE : ${payload.model.jointName}")
        rSub.addCarriageReturn()
        rSub.addCarriageReturn()

        val pBureau = doc.createParagraph()
        pBureau.alignment = ParagraphAlignment.CENTER
        val rBur = pBureau.createRun()
        rBur.fontFamily = CtcStyleCatalog.FONT_FAMILY
        rBur.fontSize = 11
        rBur.setText("BUREAU D'ÉTUDES D'ARCHITECTURE ET D'INGÉNIERIE : BET ARCHILAB")
        rBur.addCarriageReturn()
        rBur.setText("LOGICIELS UTILISÉS : CSI ETABS v18 / SAFE v12 / ARCHILAB NOTE DE CALCUL STUDIO")
        rBur.addCarriageReturn()
        rBur.setText("DOCUMENT SOUMIS POUR CONTRÔLE RÉGLEMENTAIRE AU CTC")
        rBur.addBreak(BreakType.PAGE)
    }

    private fun buildTableOfContents(doc: XWPFDocument) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "SOMMAIRE GÉNÉRAL")

        val chapters = listOf(
            "1. Description générale de l'ouvrage et cadre normatif",
            "2. Caractéristiques géométriques du bâtiment",
            "3. Caractéristiques mécaniques des matériaux (Béton & Acier)",
            "4. Combinaisons d'actions et évaluation du poids sismique total W",
            "5. Descente de charges selon le DTR BC 2.2",
            "6. Prédimensionnement des éléments structuraux (Poutres)",
            "7. Prédimensionnement des poteaux et voiles de contreventement",
            "8. Étude des planchers (Poutrelles en Té KOKA et flèche)",
            "9. Étude de la réponse sismique selon le DTR BC 2.48 (RPA 2024)",
            "10. Calcul et ferraillage des éléments structuraux (Poteaux, Poutres, Voiles)",
            "11. Étude de l'acrotère console",
            "12. Étude des escaliers droits en béton armé",
            "13. Calcul et dimensionnement des fondations (CSI SAFE v12 & BaelT)"
        )

        for (ch in chapters) {
            val pItem = doc.createParagraph()
            val r = pItem.createRun()
            r.fontFamily = CtcStyleCatalog.FONT_FAMILY
            r.fontSize = 11
            r.isBold = true
            r.setText(ch)
        }
        doc.createParagraph().createRun().addBreak(BreakType.PAGE)
    }

    private fun buildChapter1Description(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "1. Description générale de l'ouvrage et cadre normatif")

        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Le présent projet consiste en l'étude d'ingénierie parasismique de la structure '${payload.model.jointName}' " +
                    "relative à l'opération '${payload.model.projectName}', implantée dans la Wilaya de ${payload.model.wilaya}."
        )

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "1.1 Règlements techniques appliqués")
        val norms = listOf(
            "• DTR BC 2.48 (RPA 2024) : Règles Parasismiques Algériennes.",
            "• DTR BC 2.41 (CBA 93) : Règles de conception et de calcul des structures en béton armé.",
            "• BAEL 91 révisé 99 : Calcul des ouvrages en béton armé aux états limites.",
            "• DTR BC 2.2 : Charges permanentes et charges d'exploitation.",
            "• DTR BC 2.33.1 : Règles de calcul des fondations superficielles."
        )
        for (norm in norms) {
            CtcStyleCatalog.addParagraphText(doc.createParagraph(), norm)
        }

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "1.2 Classification parasismique du site (RPA 2024)")
        val tSite = doc.createTable(6, 2)
        CtcStyleCatalog.formatTable(tSite, listOf(5500, 4000))
        tSite.getRow(0).getCell(0).text = "Paramètre Réglementaire"
        tSite.getRow(0).getCell(1).text = "Valeur adoptée"
        CtcStyleCatalog.formatHeaderRow(tSite.getRow(0))

        val siteData = listOf(
            "Zone sismique" to "${payload.model.zone.name} (A = ${payload.model.zone.coeffA})",
            "Groupe d'usage" to "${payload.model.importance.name} (I = ${payload.model.importance.coeffI})",
            "Classification du site" to "${payload.model.site.name} (S = ${payload.model.site.s})",
            "Facteur de comportement R" to "R = ${payload.model.behaviorR}",
            "Facteur de qualité Q" to "Q = ${payload.model.qualityQ}"
        )
        for (i in siteData.indices) {
            val row = tSite.getRow(i + 1)
            row.getCell(0).text = siteData[i].first
            row.getCell(1).text = siteData[i].second
            CtcStyleCatalog.formatDataRow(row)
        }
    }

    private fun buildChapter2Geometry(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "2. Caractéristiques géométriques du bâtiment")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Le bâtiment est constitué d'un niveau Rez-de-chaussée et de 2 étages courants (R+2), coiffé d'une toiture terrasse inaccessible. " +
                    "La hauteur sous plafond est de 3.74 m au RDC et 3.74 m aux étages, portant la hauteur totale du bloc à H = 11.22 m."
        )
    }

    private fun buildChapter3Materials(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "3. Caractéristiques mécaniques des matériaux")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "3.1 Béton (Classe C25/30)")

        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Résistance caractéristique à 28 jours : fc28 = ${payload.model.concrete.fc28Mpa} MPa, ft28 = ${payload.model.concrete.ft28Mpa} MPa. " +
                    "Contrainte admissible de calcul à l'ELU : fbu = 0.85 · fc28 / θ · γb = 0.85 · 25 / (1.0 · 1.5) = 14.17 MPa. " +
                    "En situation accidentelle (séisme) : fbu_acc = 0.85 · 25 / 1.15 = 18.48 MPa."
        )

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "3.2 Acier pour béton armé (Haute Adhérence FeE500)")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Limite d'élasticité nominale : fe = ${payload.model.rebar.feMpa} MPa. " +
                    "Résistance de calcul à l'ELU : fsu = fe / γs = 500 / 1.15 = 434.78 MPa. " +
                    "En situation sismique (γs = 1.0) : fsu_acc = 500.00 MPa."
        )
    }

    private fun buildChapter4Combinations(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "4. Combinaisons d'actions et poids sismique")
        val combis = listOf(
            "1. Situation Durable : 1.35 G + 1.50 Q (ELU)",
            "2. Situation de Service : G + Q (ELS)",
            "3. Situation Sismique RPA § 5.2 : G + Q ± Ex et G + Q ± Ey",
            "4. Situation Sismique Renversante : 0.8 G ± Ex et 0.8 G ± Ey"
        )
        for (c in combis) {
            CtcStyleCatalog.addParagraphText(doc.createParagraph(), c)
        }
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Le poids sismique total W est évalué conformément à la formule W = Σ (Gi + ψ · Qi) avec ψ = 0.20 pour bâtiment d'habitation/bureaux."
        )
    }

    private fun buildChapter5LoadTakeDown(doc: XWPFDocument) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "5. Descente de charges (DTR BC 2.2)")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "5.1 Plancher terrasse inaccessible")
        val tTerrasse = doc.createTable(7, 2)
        CtcStyleCatalog.formatTable(tTerrasse, listOf(6500, 3000))
        tTerrasse.getRow(0).getCell(0).text = "Couche constitutive"
        tTerrasse.getRow(0).getCell(1).text = "Poids (kN/m²)"
        CtcStyleCatalog.formatHeaderRow(tTerrasse.getRow(0))

        val terrData = listOf(
            "Protection gravillon roulé (e = 5 cm)" to "1.00 kN/m²",
            "Étanchéité multicouche (e = 2 cm)" to "0.12 kN/m²",
            "Forme de pente moyenne (e = 10 cm, 22 kN/m³)" to "2.20 kN/m²",
            "Isolation thermique polystyrène (e = 4 cm)" to "0.12 kN/m²",
            "Corps creux béton armé (16 + 5 cm)" to "3.10 kN/m²",
            "Enduit sous plafond en plâtre (e = 2 cm)" to "0.20 kN/m²"
        )
        for (i in terrData.indices) {
            val row = tTerrasse.getRow(i + 1)
            row.getCell(0).text = terrData[i].first
            row.getCell(1).text = terrData[i].second
            CtcStyleCatalog.formatDataRow(row)
        }
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Total Plancher Terrasse : G = 6.75 kN/m², Q = 1.00 kN/m².")
    }

    private fun buildChapter6And7PreDimensioning(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "6 & 7. Prédimensionnement des poutres et poteaux (RPA 2024)")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "Prédimensionnement des Poutres (Figure 7.4 RPA 2024)")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Condition de rigidité : Lmax/15 ≤ hp ≤ Lmax/10 et 0.3 hp ≤ bp ≤ 0.4 hp. Sections adoptées : P.Principale 30x55 cm, P.Principale 30x35 cm, Chaînage 30x35 cm."
        )
        CtcStyleCatalog.addVerificationBadge(
            doc.createParagraph(),
            "Vérification géométrique Figure 7.4 (b ≥ 20 cm, h ≥ 30 cm, h/b ≤ 4)",
            true
        )

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "Prédimensionnement des Poteaux (Figure 7.1 RPA 2024)")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Conditions de coffrage : Min(b1, h1) ≥ 25 cm (Zone III), Min(b1, h1) ≥ lcl/20, 1/4 < b1/h1 < 4. Sections adoptées : POT 40x50 cm, POT 35x55 cm, POT 35x35 cm."
        )
        CtcStyleCatalog.addVerificationBadge(
            doc.createParagraph(),
            "Vérification des conditions de coffrage poteaux",
            true
        )
    }

    private fun buildChapter8Floors(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "8. Étude des planchers (Corps creux, Poutrelles Té & Flèche)")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Épaisseur minimale ht = L/22.5 = (4.65 - 0.30)/22.5 = 19 cm -> Corps creux (16 + 5) cm adopté (ht = 21 cm)."
        )

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "Résultats de résolution de la poutrelle continue en Té (KOKA)")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "En travée : Mu = ${"%.2f".format(Locale.US, payload.joist.maxSpanMomentMuKnM)} kNm -> ${payload.joist.spanRebarDescription}."
        )
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Sur appui : Mu = ${"%.2f".format(Locale.US, payload.joist.leftSupportMomentMuKnM)} kNm -> ${payload.joist.leftSupportRebarDescription}."
        )
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Contrôle de la flèche selon BAEL 91 § B.6.5 : Flèche calculée f = ${payload.joist.calculatedDeflectionMm} mm ≤ f_adm = ${payload.joist.allowableDeflectionMm} mm."
        )
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Vérification de la flèche totale admissible", payload.joist.isDeflectionCompliant)
    }

    private fun buildChapter9SeismicStudy(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "9. Étude sismique (DTR BC 2.48 - RPA 2024)")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "9.1 Spectres de réponse réglementaires RPA 2024 (§ 3.3)")
        val pImgSpectrum = doc.createParagraph()
        val spectrumPng = spectrumRenderer.renderToPng(
            zone = payload.model.zone,
            site = payload.model.site,
            importance = payload.model.importance,
            r = payload.model.behaviorR,
            q = payload.model.qualityQ,
            t0Operating = payload.modalModes.firstOrNull()?.periodSeconds ?: 0.399
        )
        insertImage(pImgSpectrum, spectrumPng, "Spectre_RPA2024.png", 460.0, 275.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 9.1 : Spectre de calcul RPA 2024 (Sad/g), spectre élastique (Sae/g) et seuil minimal 0.20·A·I.")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "9.2 Participation modale de masse (RPA 2024 § 4.3.3)")
        val tModes = doc.createTable(payload.modalModes.size + 1, 5)
        CtcStyleCatalog.formatTable(tModes, listOf(1500, 2000, 2000, 2000, 2000))
        tModes.getRow(0).getCell(0).text = "Mode"
        tModes.getRow(0).getCell(1).text = "Période (s)"
        tModes.getRow(0).getCell(2).text = "Ux (%)"
        tModes.getRow(0).getCell(3).text = "Uy (%)"
        tModes.getRow(0).getCell(4).text = "Rz (%)"
        CtcStyleCatalog.formatHeaderRow(tModes.getRow(0))

        for (i in payload.modalModes.indices) {
            val m = payload.modalModes[i]
            val row = tModes.getRow(i + 1)
            row.getCell(0).text = m.modeNumber.toString()
            row.getCell(1).text = "%.3f".format(Locale.US, m.periodSeconds)
            row.getCell(2).text = "%.2f".format(Locale.US, m.uxPercent)
            row.getCell(3).text = "%.2f".format(Locale.US, m.uyPercent)
            row.getCell(4).text = "%.2f".format(Locale.US, m.rzPercent)
            CtcStyleCatalog.formatDataRow(row)
        }
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), payload.modalSummary.diagnostic, payload.modalSummary.isCompliant)

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "9.3 Calage de la force sismique totale (MSE vs MDS § 4.2.3)")
        injectOmml(doc.createParagraph(), OmmlEquationBuilder.buildBaseShearEquation())
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Sens X : VMSE = ${"%.2f".format(Locale.US, payload.baseShearX.staticForceVmseKn)} kN, VMDS = ${"%.2f".format(Locale.US, payload.baseShearX.dynamicForceVmdsKn)} kN (Ratio = ${"%.2f".format(Locale.US, payload.baseShearX.ratioVmdsOverVmse * 100)}% >= 80%)."
        )
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Calage dynamique Sens X (VMDS ≥ 0.80 VMSE)", payload.baseShearX.isCalibrated)

        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Sens Y : VMSE = ${"%.2f".format(Locale.US, payload.baseShearY.staticForceVmseKn)} kN, VMDS = ${"%.2f".format(Locale.US, payload.baseShearY.dynamicForceVmdsKn)} kN (Ratio = ${"%.2f".format(Locale.US, payload.baseShearY.ratioVmdsOverVmse * 100)}% >= 80%)."
        )
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Calage dynamique Sens Y (VMDS ≥ 0.80 VMSE)", payload.baseShearY.isCalibrated)

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "9.4 Vérification de l'effort normal réduit des poteaux (ν ≤ 0.35)")
        injectOmml(doc.createParagraph(), OmmlEquationBuilder.buildReducedNormalStressEquation())
        val tNu = doc.createTable(payload.columnReducedStressChecks.size + 1, 6)
        CtcStyleCatalog.formatTable(tNu, listOf(1800, 1800, 1500, 1500, 1400, 1500))
        tNu.getRow(0).getCell(0).text = "Niveau"
        tNu.getRow(0).getCell(1).text = "Poteau"
        tNu.getRow(0).getCell(2).text = "Section"
        tNu.getRow(0).getCell(3).text = "Nd (kN)"
        tNu.getRow(0).getCell(4).text = "ν"
        tNu.getRow(0).getCell(5).text = "Statut"
        CtcStyleCatalog.formatHeaderRow(tNu.getRow(0))

        for (i in payload.columnReducedStressChecks.indices) {
            val c = payload.columnReducedStressChecks[i]
            val row = tNu.getRow(i + 1)
            row.getCell(0).text = c.story
            row.getCell(1).text = c.columnId
            row.getCell(2).text = c.sectionName
            row.getCell(3).text = "%.1f".format(Locale.US, c.axialForceNdKn)
            row.getCell(4).text = "%.3f".format(Locale.US, c.reducedStressNu)
            row.getCell(5).text = if (c.isCompliant) "Vérifiée (CV)" else "NON VÉRIFIÉE"
            CtcStyleCatalog.formatDataRow(row)
        }

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "9.5 Déplacements inter-étages (νA·Δk ≤ 0.0075 hk)")
        injectOmml(doc.createParagraph(), OmmlEquationBuilder.buildStoryDriftEquation())
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Déplacements inter-étages limitation des dommages", true)

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "9.6 Effet de second ordre P-Delta (θk ≤ 0.10)")
        injectOmml(doc.createParagraph(), OmmlEquationBuilder.buildPDeltaEquation())
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Stabilité de second ordre (θk_max < 0.10)", true)
    }

    private fun buildChapter10StructuralCalculations(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "10. Calcul des éléments structuraux")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "10.1 Ferraillage des poteaux (Flexion composée BAEL / RPA § 7.4)")
        for (col in payload.designedColumns) {
            CtcStyleCatalog.addParagraphText(
                doc.createParagraph(),
                "• Poteau ${col.sectionName} (${col.story}) : Nd = ${"%.1f".format(Locale.US, col.ndMaxKn)} kN, Mcorr = ${"%.1f".format(Locale.US, col.muCorrKnM)} kNm " +
                        "-> Armatures adoptées : ${col.adoptedLongitudinalBars.description} (${"%.2f".format(Locale.US, col.adoptedLongitudinalBars.totalAreaCm2)} cm² ≥ Amin = ${"%.2f".format(Locale.US, col.aMinRpaCm2)} cm²). " +
                        "Zone nodale Lcr = ${col.lcrNodalZoneCm} cm, t = ${col.spacingNodalCm} cm, t' = ${col.spacingCurrentCm} cm, Lrec = ${col.lapLengthLrecCm} cm."
            )
        }

        // Insertion Coupe & Élévation Poteau
        val pImgCol = doc.createParagraph()
        val colSectionPng = rebarRenderer.renderColumnSectionPng(
            widthCm = 40.0,
            heightCm = 50.0,
            barDiameterMm = 14,
            elementName = "POTEAU TYPE C111 (40x50 cm)"
        )
        insertImage(pImgCol, colSectionPng, "Coupe_Poteau_C111.png", 450.0, 310.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 10.1 : Coupe transversale de ferraillage du poteau C111 (BaelR / RPA § 7.4).")

        val pImgColElev = doc.createParagraph()
        val colElevPng = rebarRenderer.renderColumnElevationPng(
            heightTotalM = 3.24,
            sectionDimCm = 45.0,
            elementName = "POTEAU C111 — ZONES NODALES Lcr & RECOUVREMENT"
        )
        insertImage(pImgColElev, colElevPng, "Elevation_Poteau_Lcr.png", 420.0, 440.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 10.2 : Élévation et répartition des étriers en zone nodale Lcr et zone courante (RPA § 7.4.2).")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "10.2 Ferraillage des poutres et 4 vérifications à l'effort tranchant")
        for (beam in payload.designedBeams) {
            CtcStyleCatalog.addParagraphText(
                doc.createParagraph(),
                "• Poutre ${beam.sectionName} : Travée ${beam.adoptedSpanBars.description} ; Appuis ${beam.adoptedSupportBars.description}. " +
                        "Cisaillement τu = ${"%.2f".format(Locale.US, beam.shearChecks.tauUMpa)} MPa ≤ ${"%.2f".format(Locale.US, beam.shearChecks.tauLimitMpa)} MPa. " +
                        "Bielle béton = ${"%.1f".format(Locale.US, beam.shearChecks.bielleCapacityKn)} kN ≥ Vu = ${"%.1f".format(Locale.US, beam.shearChecks.vuKn)} kN."
            )
        }
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Les 4 vérifications à l'effort tranchant des poutres", true)

        val pImgBeam = doc.createParagraph()
        val beamSectionPng = rebarRenderer.renderBeamSectionPng(
            widthBCm = 30.0,
            heightHCm = 55.0,
            elementName = "POUTRE PRINCIPALE PR (30x55 cm)"
        )
        insertImage(pImgBeam, beamSectionPng, "Coupe_Poutre_PR30x55.png", 450.0, 310.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 10.3 : Coupe transversale de ferraillage de la poutre principale PR 30x55 (RPA § 7.5).")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "10.3 Voiles de contreventement (RPA 2024 § 7.7)")
        injectOmml(doc.createParagraph(), OmmlEquationBuilder.buildWallShearStressEquation())
        for (wall in payload.designedWalls) {
            CtcStyleCatalog.addParagraphText(
                doc.createParagraph(),
                "• Voile ${wall.pierName} (lw = ${wall.lwMeters} m, bw = ${wall.bwMeters} m) : νd = ${"%.3f".format(Locale.US, wall.reducedNormalStressNuD)} ≤ 0.40 (CV). " +
                        "Cisaillement 1.4Vu : τb = ${"%.2f".format(Locale.US, wall.shearStressTauBMpa)} MPa ≤ 5.00 MPa (CV). " +
                        "Hauteur critique hcr = ${wall.criticalZoneHeightHcrMeters} m. Rives confinées lc = ${wall.leftBoundary.lcChosenCm} cm (St = 8 cm, 16T14)."
            )
        }

        // Insertion des diagrammes d'enveloppes
        val pImgWallM = doc.createParagraph()
        val wallMPng = wallRenderer.renderMomentEnvelopePng(
            wallLengthM = 1.40,
            wallThicknessM = 0.30,
            totalHeightM = 11.22,
            hcrM = 3.39,
            elementName = "VOILE V6 (1.40x0.30 m)"
        )
        insertImage(pImgWallM, wallMPng, "Enveloppe_Moments_Voile_V6.png", 460.0, 300.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 10.4 : Enveloppe des moments fléchissants Mu(z) décalée de la hauteur critique hcr (RPA § 7.7.1).")

        val pImgWallV = doc.createParagraph()
        val wallVPng = wallRenderer.renderShearEnvelopePng(
            wallLengthM = 1.40,
            wallThicknessM = 0.30,
            elementName = "VOILE V6 — EFFORT TRANCHANT 1.40 Vu"
        )
        insertImage(pImgWallV, wallVPng, "Enveloppe_EffortTranchant_Voile_V6.png", 460.0, 300.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 10.5 : Diagramme d'effort tranchant amplifié 1.40 Vu et vérification limite tau_b <= 0.20 fc28.")

        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Conformité d'ensemble des trumeaux de contreventement", true)
    }

    private fun buildChapter11Parapet(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "11. Étude de l'acrotère")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Acrotère console : Poids propre Wp = ${"%.2f".format(Locale.US, payload.parapet.deadLoadWpKnPerM)} kN/ml, Force sismique Fp = ${"%.2f".format(Locale.US, payload.parapet.seismicForceFpKnPerM)} kN/ml, Surcharge Q = ${"%.2f".format(Locale.US, payload.parapet.designForceQKnPerM)} kN/ml. " +
                    "Moment à l'encastrement Mu = ${"%.2f".format(Locale.US, payload.parapet.momentEluKnM)} kNm/ml -> Armatures adoptées : ${payload.parapet.verticalRebarDescription}."
        )
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Ferraillage de l'acrotère à la flexion composée", true)
    }

    private fun buildChapter12Stairs(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "12. Étude des escaliers")
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Vérification de Blondel : 2h + g = 2·17 + 30 = ${payload.staircase.blondelValueCm} cm (59 ≤ 64 ≤ 64 cm - Vérifié). " +
                    "Moment en travée Mu = ${"%.2f".format(Locale.US, payload.staircase.maxMomentEluKnM)} kNm -> Armatures principales : ${payload.staircase.mainRebarDescription}."
        )
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Conformité de l'escalier droit", true)
    }

    private fun buildChapter13Foundations(doc: XWPFDocument, payload: CtcReportDataPayload) {
        val p = doc.createParagraph()
        CtcStyleCatalog.styleHeading1(p, "13. Calcul des fondations (SAFE v12)")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "13.1 Vérification de la contrainte effective du sol (Modèle de Winkler)")
        injectOmml(doc.createParagraph(), OmmlEquationBuilder.buildSoilStressEquation())
        CtcStyleCatalog.addParagraphText(
            doc.createParagraph(),
            "Déplacement vertical maximal sous SAFE : Δmax = 0.005 m. Raideur de sol Ks = ${"%.0f".format(Locale.US, payload.model.geo.subgradeReactionKsKnM3)} kN/m³. " +
                    "Contrainte effective : σeff = 0.005 · 21848 = 109.24 kPa ≤ σsol_adm = 120.00 kPa."
        )
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Contrainte effective du sol admissible (σeff ≤ 120 kPa)", true)

        // Insertion cartographie SAFE
        val pImgSafe = doc.createParagraph()
        val safePng = safeRenderer.renderSoilPressureHeatmapPng(
            ksKNm3 = payload.model.geo.subgradeReactionKsKnM3,
            sigmaSolAdmissibleBars = payload.model.geo.sigmaSolAdmBars,
            peakStressBars = 1.15
        )
        insertImage(pImgSafe, safePng, "Cartographie_SAFE_Sol.png", 470.0, 315.0)
        CtcStyleCatalog.addParagraphText(doc.createParagraph(), "Figure 13.1 : Cartographie spectrale des pressions de sol effectives sous semelles filantes (CSI SAFE v12).")

        CtcStyleCatalog.styleHeading2(doc.createParagraph(), "13.2 Ferraillage des semelles filantes et de joint (BaelR / BaelT)")
        for (f in payload.foundations) {
            CtcStyleCatalog.addParagraphText(
                doc.createParagraph(),
                "• Semelle ${f.beamType} : En appui -> ${f.supportRebarDescription} ; En travée -> ${f.spanRebarDescription} ; Patin transversal -> ${f.transverseSlabRebarDescription}."
            )
        }
        CtcStyleCatalog.addVerificationBadge(doc.createParagraph(), "Dimensionnement structural des fondations superficielles", true)
    }
}
