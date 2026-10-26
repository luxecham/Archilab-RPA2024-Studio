"""Generation de la note de calcul Word et PDF.

Le meme modele de document alimente les deux sorties. Chaque chapitre comporte :
  - une synthese chiffree ;
  - les tableaux de verifications (requis / fourni / statut) ;
  - les commentaires d'ingenieur termines par « CV. » ;
  - une capture logicielle placee sous chaque figure analytique.

Les plans vectoriels (DXF/SVG) et la nomenclature sont produits par
:mod:`vector_export` et references en annexe.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Sequence

from rpa_engine import Assessment, ChapterReport, Check, ElementReport, Status, fmt
from vector_export import PlanExportResult, export_plans

__all__ = ["ReportResult", "generate_reports", "generate_docx", "generate_pdf", "build_document_blocks"]


APP_NAME = "Archilab RPA2024 NoteDeCalcul Studio"
APP_VERSION = "1.0.0"
STATUS_COLORS = {
    Status.OK: "1E7B4B",
    Status.WARNING: "B26A00",
    Status.FAILED: "B00020",
    Status.NA: "5F6B7A",
}


# ---------------------------------------------------------------------------
# Modele de document
# ---------------------------------------------------------------------------

@dataclass
class Block:
    kind: str
    text: str = ""
    level: int = 1
    headers: list[str] = field(default_factory=list)
    rows: list[list[str]] = field(default_factory=list)
    widths: list[float] = field(default_factory=list)
    statuses: list[Status] = field(default_factory=list)
    title: str = ""
    tag: str = ""


@dataclass
class ReportResult:
    docx: Path | None = None
    pdf: Path | None = None
    plans: PlanExportResult | None = None
    log: list[str] = field(default_factory=list)

    @property
    def files(self) -> list[Path]:
        files = [p for p in (self.docx, self.pdf) if p]
        if self.plans:
            files.extend(self.plans.files)
        return files


def build_document_blocks(assessment: Assessment, plans: PlanExportResult | None = None) -> list[Block]:
    blocks: list[Block] = []
    blocks.append(Block("summary", title="Synthèse générale"))
    blocks.extend(_summary_blocks(assessment))
    for chapter in assessment.chapters:
        blocks.append(Block("pagebreak"))
        blocks.extend(_chapter_blocks(assessment, chapter))
    blocks.append(Block("pagebreak"))
    blocks.extend(_annex_dqe(assessment))
    blocks.append(Block("pagebreak"))
    blocks.extend(_annex_plans(assessment, plans))
    blocks.append(Block("pagebreak"))
    blocks.extend(_annex_limites(assessment))
    return blocks


def _summary_blocks(assessment: Assessment) -> list[Block]:
    blocks: list[Block] = []
    status = assessment.status
    blocks.append(Block("callout", text=(
        f"Avis de synthèse : {status.value}. "
        f"{assessment.failed_count} vérification(s) en échec et "
        f"{assessment.warning_count} réserve(s) à examiner. "
        "La note compile les résultats calculés sans masquer les dépassements."
    ), level=status.severity))
    rows = [
        ["Projet", assessment.project_name],
        ["Joint / bloc", assessment.joint_name],
        ["Zone sismique", f"{assessment.seismic.resolved.zone_code} (A = {fmt(assessment.seismic.resolved.a, 2)})"],
        ["Site", assessment.seismic.resolved.site],
        ["Système / R", f"Système {assessment.seismic.resolved.bracing_id} / R = {fmt(assessment.seismic.resolved.r, 1)}"],
        ["Facteur de qualité", fmt(assessment.regularity.qf, 2)],
        ["Niveaux / hauteur", f"{assessment.model.num_stories} / {fmt(assessment.model.total_height, 2)} m"],
        ["Éléments contrôlés", str(assessment.checked_elements)],
        ["Statut global", status.value],
        ["Généré le", assessment.generated_at],
    ]
    blocks.append(Block("table", title="Fiche du projet", headers=["Donnée", "Valeur"],
                        rows=rows, widths=[5.0, 10.5]))
    summary_rows = [
        [c.number, c.title, c.status.value, str(c.failed_count), str(c.warning_count)]
        for c in assessment.chapters
    ]
    blocks.append(Block("table", title="Synthèse par chapitre",
                        headers=["N°", "Chapitre", "Statut", "Échecs", "Réserves"],
                        rows=summary_rows, widths=[1.2, 8.0, 3.2, 1.6, 1.6],
                        statuses=[c.status for c in assessment.chapters]))
    if assessment.warnings:
        blocks.append(Block("h2", text="Points de vigilance issus de l'import"))
        for warning in assessment.warnings[:20]:
            blocks.append(Block("bullet", text=warning))
    blocks.append(Block("p", text=_global_cv(assessment)))
    return blocks


def _global_cv(assessment: Assessment) -> str:
    if assessment.status == Status.OK:
        verdict = ("L'ensemble des vérifications automatisées est satisfait pour les hypothèses saisies. "
                   "La conformité définitive reste subordonnée à la revue du modèle, des charges et des plans "
                   "par l'ingénieur et au visa du CTC.")
    elif assessment.status == Status.WARNING:
        verdict = ("Les vérifications principales sont satisfaites, mais des réserves subsistent. "
                   "Elles doivent être levées par une justification écrite ou une modification du modèle "
                   "avant diffusion au CTC.")
    elif assessment.status == Status.FAILED:
        verdict = ("Au moins une vérification réglementaire n'est pas satisfaite. "
                   "Le modèle ne peut pas être diffusé en l'état : reprendre les éléments signalés, "
                   "puis régénérer la note.")
    else:
        verdict = "Les données disponibles ne permettent pas de conclure sur la conformité."
    return verdict + " CV."


def _chapter_blocks(assessment: Assessment, chapter: ChapterReport) -> list[Block]:
    blocks: list[Block] = [
        Block("h1", text=f"{chapter.number}. {chapter.title}"),
        Block("lead", text=chapter.summary),
        Block("status", text=chapter.status.value, level=chapter.status.severity,
              title=f"{chapter.failed_count} échec(s) – {chapter.warning_count} réserve(s)"),
    ]
    if chapter.checks:
        blocks.append(_checks_table(chapter.checks, f"Vérifications du chapitre {chapter.number}"))
    for table in chapter.tables:
        blocks.append(Block("table", title=table.title, headers=table.headers,
                            rows=table.rows, widths=table.widths or []))
    if chapter.elements:
        blocks.append(Block("h2", text="Détail des éléments contrôlés"))
        for element in chapter.elements:
            blocks.append(Block("h3", text=f"{element.element_type} – {element.name}"))
            blocks.append(Block("small", text=element.geometry))
            blocks.append(_checks_table(element.checks, ""))
            for note in element.notes:
                blocks.append(Block("bullet", text=note))
    for line in chapter.lines:
        blocks.append(Block("bullet", text=line))
    tag = chapter.figure or _default_figure(chapter.number)
    blocks.append(Block("figure", tag=tag))
    blocks.append(Block("cv", text=_chapter_cv(assessment, chapter)))
    for extra_tag, extra_cv in _extra_figures(chapter.number, assessment):
        blocks.append(Block("figure", tag=extra_tag))
        blocks.append(Block("cv", text=extra_cv))
    return blocks


def _extra_figures(number: int, assessment: Assessment) -> list[tuple[str, str]]:
    """Captures complementaires pour couvrir les figures analytiques de la note."""
    if number == 4:
        return [
            ("[FIGURE_CAPTURE_LOGICIEL : ETABS – tableaux des charges permanentes et d'exploitation]",
             "Les charges permanentes et d'exploitation sont vérifiées avant combinaison ; "
             "toute charge absente de l'export doit être ajoutée dans ETABS puis réimportée. CV."),
            ("[FIGURE_CAPTURE_LOGICIEL : ETABS – combinaisons de charges sismiques et enveloppes]",
             "Les combinaisons COMB1 à COMB8 couvrent les situations durables et accidentelles ; "
             "les enveloppes de moments et d'efforts normaux en sont déduites. CV."),
        ]
    if number == 6:
        return [
            ("[FIGURE_CAPTURE_LOGICIEL : ETABS – plan de coffrage et sections des poteaux et poutres]",
             "Les sections affichées sont celles réellement importées ; le prédimensionnement "
             "est recontrôlé famille par famille, sans valeur forfaitaire. CV."),
        ]
    if number == 9:
        return [
            ("[FIGURE_CAPTURE_LOGICIEL : ETABS – courbe du spectre RPA et déplacements modaux]",
             f"Le spectre de calcul est lu à T = {fmt(assessment.seismic.resolved.empirical_period, 3)} s "
             "après plafonnement de la période analytique ; les déplacements sont ensuite amplifiés "
             "par R/QF avant le contrôle νA.|Δ|. CV."),
        ]
    if number == 10:
        return [
            ("[FIGURE_CAPTURE_LOGICIEL : ETABS – tableau de ferraillage des poteaux et poutres]",
             "Les sections d'acier calculées sont converties en barres commerciales ; les minima "
             "RPA et les espacements de cadres sont vérifiés pour chaque famille. CV."),
        ]
    if number == 12:
        return [
            ("[FIGURE_CAPTURE_LOGICIEL : SAFE – carte des tassements et modèle de Winkler]",
             f"La contrainte de Winkler est calculée avec Ks = "
             f"{fmt(assessment.config.subgrade_modulus, 0)} kN/m³ ; le tassement reste limité à "
             f"{fmt(assessment.config.settlement_limit_mm, 0)} mm. CV."),
        ]
    return []


def _checks_table(checks: Sequence[Check], title: str) -> Block:
    return Block(
        "table",
        title=title,
        headers=["Code", "Vérification", "Référence", "Requis", "Fourni", "Statut"],
        rows=[[c.code, c.label, c.reference, c.required or "-", c.provided or "-",
               "Information" if c.informational else c.status.value]
              for c in checks],
        widths=[1.9, 4.1, 3.3, 2.7, 2.4, 2.2],
        statuses=[c.status for c in checks],
    )


def _default_figure(number: int) -> str:
    tags = {
        1: "[FIGURE_CAPTURE_LOGICIEL : ETABS – écran d'accueil, unités et nom du modèle]",
        2: "[FIGURE_CAPTURE_LOGICIEL : ETABS – définition des matériaux et spectre RPA 2024]",
        3: "[FIGURE_CAPTURE_LOGICIEL : ETABS – vue 3D et plan du niveau courant]",
        4: "[FIGURE_CAPTURE_LOGICIEL : ETABS – tableaux des charges et combinaisons]",
        5: "[FIGURE_CAPTURE_LOGICIEL : ETABS – matériaux béton C25/30 et acier FeE500]",
    }
    return tags.get(number, "[FIGURE_CAPTURE_LOGICIEL : ETABS – capture du modèle]")


def _chapter_cv(assessment: Assessment, chapter: ChapterReport) -> str:
    number = chapter.number
    seismic = assessment.seismic
    if number == 1:
        text = (f"Le modèle importé comporte {assessment.model.num_stories} niveaux, "
                f"{len(assessment.model.columns)} poteaux, {len(assessment.model.beams)} poutres, "
                f"{len(assessment.model.walls)} voiles et {len(assessment.model.slabs)} planchers. "
                f"La hauteur retenue est Hn = {fmt(assessment.model.total_height, 2)} m. "
                "Les données de configuration et les fichiers sources sont tracés dans cette fiche.")
    elif number == 2:
        text = (f"La vérification s'appuie sur le RPA 2024 et le BAEL 91 révisé 99. "
                f"Le facteur de qualité vaut QF = {fmt(assessment.regularity.qf, 2)} et le coefficient "
                f"de comportement R = {fmt(seismic.resolved.r, 1)}. Le spectre retenu est de type "
                f"{seismic.resolved.spectrum_type} pour le site {seismic.resolved.site}. "
                "Ces paramètres gouvernent directement la force sismique et les déplacements.")
    elif number == 3:
        text = (f"La structure est régulière en plan : {assessment.regularity.plan_regular}. "
                f"La trame comporte {assessment.model.num_bays_x} travées en X et "
                f"{assessment.model.num_bays_y} travées en Y. Le système porteur associe "
                f"{len(assessment.model.columns)} poteaux, {len(assessment.model.beams)} poutres et "
                f"{len(assessment.model.walls)} voiles, ce qui permet de mobiliser les dispositions "
                "RPA relatives aux ossatures contreventées.")
    elif number == 4:
        text = ("Les combinaisons ELU et accidentelles sont appliquées aux efforts importés. "
                "Le poids sismique et les cas EX/EY conditionnent le calage MSE/MDS. "
                "Toute charge non exportée depuis ETABS doit être ajoutée au modèle puis réimportée.")
    elif number == 5:
        text = (f"Le béton C25/30 (fc28 = {fmt(assessment.config.fc28, 1)} MPa) et l'acier "
                f"FeE500 (fe = {fmt(assessment.config.fe, 0)} MPa) respectent les minima usuels. "
                f"Les coefficients partiels γb = {fmt(assessment.config.gamma_b, 2)} et "
                f"γs = {fmt(assessment.config.gamma_s, 2)} sont appliqués dans le noyau BAEL.")
    elif number == 6:
        failed = [e for e in chapter.elements if e.status == Status.FAILED]
        warn = [e for e in chapter.elements if e.status == Status.WARNING]
        text = (f"{len(chapter.elements)} famille(s) ont été contrôlées ; "
                f"{len(failed)} présente(nt) un dépassement et {len(warn)} une réserve de prédimensionnement. "
                "Les règles L/15–L/10 pour les poutres et L/22,5–L/25 pour les planchers sont appliquées "
                "élément par élément, sans valeur forfaitaire.")
    elif number == 7:
        text = (f"La régularité en plan est {'satisfaite' if assessment.regularity.plan_regular else 'non satisfaite'}, "
                f"celle en élévation {'satisfaite' if assessment.regularity.elevation_regular else 'non satisfaite'}. "
                f"Le QF résultant est {fmt(assessment.regularity.qf, 2)}. "
                "Une irrégularité non corrigée réduit la force sismique mais aggrave les déplacements locaux ; "
                "elle doit être justifiée, pas seulement constatée.")
    elif number == 8:
        text = (f"La période empirique vaut T = {fmt(seismic.resolved.empirical_period, 3)} s. "
                f"λ = {fmt(seismic.resolved.lambda_factor, 2)} et l'amortissement est de "
                f"{fmt(seismic.resolved.damping, 1)} %, soit η = {fmt(seismic.resolved.eta, 3)}. "
                "La période analytique est plafonnée à 1,30.T avant lecture du spectre.")
    elif number == 9:
        modal = seismic.modal
        drifts = [d for d in seismic.all_drifts if d.status != Status.NA]
        pdelta = [p for p in seismic.all_pdelta if p.status != Status.NA]
        worst_drift = max(drifts, key=lambda d: d.utilization) if drifts else None
        worst_theta = max(pdelta, key=lambda p: p.theta) if pdelta else None
        nu = [c for c in seismic.columns if c.status != Status.NA]
        worst_nu = max(nu, key=lambda c: c.nu) if nu else None
        text = (f"La participation modale atteint ΣUX = {fmt(modal.sum_ux, 1)} % et "
                f"ΣUY = {fmt(modal.sum_uy, 1)} % en {modal.modes_for_90 or modal.mode_count} modes "
                f"(sur {modal.mode_count} modes calculés). "
                f"Les rapports V_MDS/V_MSE sont de "
                f"{', '.join(f'{s.direction} : {fmt((s.ratio or 0) * 100, 1)} %' for s in seismic.base_shear)}. ")
        if worst_nu:
            text += f"L'effort normal réduit maximal vaut ν = {fmt(worst_nu.nu, 3)}. "
        if worst_drift:
            text += (f"La dérive la plus défavorable est obtenue au niveau {worst_drift.story} avec "
                     f"νA.|Δ| = {fmt(worst_drift.reduced * 1000, 2)} mm pour "
                     f"{fmt(worst_drift.governing * 1000, 2)} mm admissibles. ")
        if worst_theta:
            text += f"L'effet P-Delta maximal vaut θ = {fmt(worst_theta.theta, 3)}. "
    elif number == 10:
        text = ("Les poteaux sont vérifiés en flexion composée et les poutres en flexion simple, "
                "avec enveloppe des moments de travée et d'appui. "
                "Les sections d'acier sont converties en barres commerciales et confrontées aux minima RPA. "
                f"Synthèse : {chapter.status.value.lower()}.")
    elif number == 11:
        walls = assessment.reinforcement.walls
        if walls:
            text = (f"{len(walls)} famille(s) de voiles sont contrôlées. Les longueurs minimales "
                    "lw ≥ max(he/3 ; 4bw ; 1 m) et les épaisseurs bw ≥ max(15 cm ; he/20) sont vérifiées. "
                    "Les zones critiques exigent une longueur au moins égale à max(1,5.bw ; 0,15.lw) "
                    "et un ferraillage vertical minimal de 0,20 %.")
        else:
            text = ("Aucun voile n'a été identifié dans le modèle importé. Le chapitre est sans objet ; "
                    "si le système de contreventement déclaré repose sur des voiles, l'export ETABS "
                    "des surfaces verticales doit être complété avant validation.")
    elif number == 12:
        footings = assessment.foundations.footings
        if footings:
            governing = max(footings, key=lambda f: f.q_max)
            text = (f"{len(footings)} famille(s) de semelles sont dimensionnées. "
                    f"La contrainte maximale vaut {fmt(governing.q_max, 1)} kN/m² pour "
                    f"q_adm = {fmt(governing.q_adm, 1)} kN/m², soit une utilisation de "
                    f"{fmt(governing.q_max / governing.q_adm * 100, 1)} %. "
                    f"Le tassement estimé par Winkler (Ks = {fmt(assessment.config.subgrade_modulus, 0)} kN/m³) "
                    f"est de {fmt(governing.settlement * 1000, 1)} mm. Les pressions SAFE sont confrontées "
                    "à la portance admissible lorsqu'elles sont disponibles.")
        else:
            text = ("Aucune semelle n'a pu être dimensionnée, faute d'efforts de fondation exploitables. "
                    "Importer les réactions de base ETABS ou les efforts SAFE avant production de la note.")
    elif number == 13:
        text = (f"La synthèse générale conclut : {assessment.status.value}. "
                f"{assessment.failed_count} vérification(s) sont en échec et "
                f"{assessment.warning_count} font l'objet de réserves. "
                "Les chapitres concernés doivent être repris avant tout visa CTC.")
    else:
        text = "Le chapitre est renseigné par les résultats du moteur de calcul."
    return text + " CV."


def _annex_dqe(assessment: Assessment) -> list[Block]:
    blocks = [Block("h1", text="Annexe A – Devis quantitatif estimatif")]
    if not assessment.dqe:
        blocks.append(Block("p", text="Le DQE n'a pas été calculé pour cette exécution."))
        return blocks
    dqe = assessment.dqe
    blocks.append(Block("lead", text=(
        "Métré estimatif des volumes de béton, surfaces de coffrage et masses d'acier, "
        "calculé à partir des sections et longueurs du modèle importé."
    )))
    blocks.append(Block("table", title="DQE détaillé", headers=dqe.table.headers,
                        rows=dqe.table.rows, widths=dqe.table.widths or [2.0, 6.8, 1.8, 2.0, 2.2, 2.4]))
    totals_rows = [[label, fmt(value, 2)] for label, value in dqe.totals.items()]
    blocks.append(Block("table", title="Totaux", headers=["Indicateur", "Valeur"],
                        rows=totals_rows, widths=[7.0, 4.0]))
    blocks.append(Block("cv", text=(
        f"Le ratio acier global est de {fmt(dqe.totals.get('Ratio acier (kg/m³)', 0), 1)} kg/m³ "
        "pour le volume de béton calculé. Les quantités restent estimatives : elles ne remplacent pas "
        "un métré d'exécution validé sur plans. CV."
    )))
    return blocks


def _annex_plans(assessment: Assessment, plans: PlanExportResult | None) -> list[Block]:
    blocks = [Block("h1", text="Annexe B – Plans vectoriels et nomenclature")]
    if not plans or not plans.files:
        blocks.append(Block("p", text="Aucun plan vectoriel n'a été exporté."))
        return blocks
    blocks.append(Block("lead", text=(
        "Les plans de coffrage et de ferraillage sont exportés en DXF (AC1024) et SVG. "
        "La nomenclature des armatures est fournie en CSV séparé."
    )))
    rows = []
    for path in plans.files:
        rows.append([path.name, path.suffix.upper().lstrip("."), f"{path.stat().st_size / 1024:.0f} Ko"])
    blocks.append(Block("table", title="Fichiers générés", headers=["Fichier", "Format", "Taille"],
                        rows=rows, widths=[9.0, 2.4, 2.4]))
    blocks.append(Block("figure", tag="[FIGURE_CAPTURE_LOGICIEL : AutoCAD – plan de coffrage exporté et calques RPA]"))
    blocks.append(Block("p", text=(
        "Les calques AXES, COLONNES, POUTRES, VOILES, PLANCHERS, COTES, TEXTE, CADRES, BARRES, "
        "HACHURES et CARTOUCHE sont normalisés. Les entités sont tracées dans l'unité millimètre "
        "et les cotes sont issues des coordonnées réelles du modèle. CV."
    )))
    blocks.append(Block("figure", tag="[FIGURE_CAPTURE_LOGICIEL : AutoCAD – plan de ferraillage et nomenclature des barres]"))
    if assessment.reinforcement.column_designs:
        rows = []
        for section, design in assessment.reinforcement.column_designs.items():
            rows.append(["Poteau", section, design.get("label", "-"), fmt(design.get("as_total", 0), 2)])
        for section, design in assessment.reinforcement.beam_designs.items():
            rows.append(["Poutre", section,
                         f"{design.get('bottom', '-')} / {design.get('top', '-')}",
                         fmt(design.get("as_bottom", 0) + design.get("as_top", 0), 2)])
        for family in assessment.model.wall_families():
            rows.append(["Voile", family.key, "T12 e=20 + 4T16 rive",
                         fmt(0.002 * family.section.thickness * family.length_lw * 100, 2)])
        for footing in assessment.foundations.footings:
            rows.append(["Semelle", footing.family, footing.message.split(";")[0].strip(), "-"])
        blocks.append(Block("table", title="Nomenclature synthétique des armatures",
                            headers=["Type", "Section", "Ferraillage", "As (cm²)"],
                            rows=rows, widths=[2.6, 4.4, 5.0, 2.4]))
    return blocks


def _annex_limites(assessment: Assessment) -> list[Block]:
    blocks = [Block("h1", text="Annexe C – Limites d'emploi et traçabilité")]
    blocks.append(Block("lead", text=(
        "Cette note est produite automatiquement par un logiciel d'aide au calcul. "
        "Elle ne dispense ni de la vérification des hypothèses, ni du contrôle des plans, "
        "ni du visa d'un ingénieur habilité."
    )))
    for text in (
        "Les résultats dépendent intégralement des tableaux ETABS/SAFE importés : unités, sections, "
        "connectivités, cas de charge et combinaisons doivent être contrôlés avant calcul.",
        "Les captures d'écran référencées [FIGURE_CAPTURE_LOGICIEL : ...] doivent être remplacées par "
        "les impressions réelles du modèle avant diffusion.",
        "Les dispositions constructives non automatisées (recouvrements, ancrages, poussée au vide, "
        "joints de dilatation) restent à vérifier sur plans.",
        "Les vérifications de sol supposent une portance et un module de Winkler homogènes ; "
        "une campagne géotechnique reste nécessaire.",
        "Le logiciel n'effectue aucune modification du modèle ETABS/SAFE : il lit, vérifie et documente.",
    ):
        blocks.append(Block("bullet", text=text))
    blocks.append(Block("table", title="Traçabilité de l'exécution", headers=["Élément", "Valeur"], rows=[
        ["Logiciel", f"{APP_NAME} v{APP_VERSION}"],
        ["Date de génération", assessment.generated_at],
        ["Projet", assessment.project_name],
        ["Joint", assessment.joint_name],
        ["Statut", assessment.status.value],
        ["Nombre de chapitres", str(len(assessment.chapters))],
        ["Nombre d'éléments contrôlés", str(assessment.checked_elements)],
    ], widths=[6.0, 9.0]))
    blocks.append(Block("cv", text=(
        "La note est traçable et reproductible à partir des fichiers sources et de la configuration "
        "enregistrée. Toute modification du modèle impose un nouvel import et la régénération complète "
        "du document. CV."
    )))
    return blocks


# ---------------------------------------------------------------------------
# Word
# ---------------------------------------------------------------------------

def generate_docx(assessment: Assessment, path: str | Path,
                  plans: PlanExportResult | None = None) -> Path:
    try:
        from docx import Document
        from docx.enum.section import WD_SECTION
        from docx.enum.table import WD_TABLE_ALIGNMENT
        from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
        from docx.oxml import OxmlElement
        from docx.oxml.ns import qn
        from docx.shared import Cm, Pt, RGBColor
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("python-docx requis : pip install python-docx") from exc

    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    document = Document()
    section = document.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.left_margin = Cm(2.0)
    section.right_margin = Cm(2.0)
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(1.8)
    section.header_distance = Cm(1.0)
    section.footer_distance = Cm(0.8)

    styles = document.styles
    _set_font(styles["Normal"], "Arial", 9.5)
    styles["Normal"].paragraph_format.space_after = Pt(3)
    styles["Normal"].paragraph_format.line_spacing = 1.12
    for name, size, color in (("Heading 1", 16, "17365D"), ("Heading 2", 12.5, "1F4E79"),
                              ("Heading 3", 10.5, "2F5597")):
        style = styles[name]
        _set_font(style, "Arial", size, bold=True, color=color)
        style.paragraph_format.space_before = Pt(10)
        style.paragraph_format.space_after = Pt(4)
        style.paragraph_format.keep_with_next = True

    _docx_header_footer(document, assessment)
    _docx_cover(document, assessment)
    document.add_page_break()

    # Champ TOC mis a jour par Word a l'ouverture.
    heading = document.add_paragraph("Sommaire", style="Heading 1")
    _docx_toc(document)
    document.add_page_break()

    blocks = build_document_blocks(assessment, plans)
    for block in blocks:
        if block.kind == "pagebreak":
            document.add_page_break()
        elif block.kind == "h1":
            _docx_heading(document, str(block.text), 1)
        elif block.kind == "h2":
            _docx_heading(document, str(block.text), 2)
        elif block.kind == "h3":
            _docx_heading(document, str(block.text), 3)
        elif block.kind == "lead":
            paragraph = document.add_paragraph(str(block.text))
            paragraph_run = paragraph.runs[0]
            paragraph_run.italic = True
            paragraph_run.font.color.rgb = RGBColor(0x44, 0x4F, 0x5A)
        elif block.kind == "small":
            paragraph = document.add_paragraph(str(block.text))
            for run in paragraph.runs:
                run.font.size = Pt(8.5)
                run.font.color.rgb = RGBColor(0x55, 0x60, 0x6B)
        elif block.kind == "bullet":
            document.add_paragraph(str(block.text), style="List Bullet")
        elif block.kind == "status":
            _docx_status_badge(document, block.text, block.title, int(block.level))
        elif block.kind == "callout":
            _docx_callout(document, str(block.text), int(block.level))
        elif block.kind == "figure":
            _docx_figure(document, block.tag)
        elif block.kind == "cv":
            _docx_cv(document, str(block.text))
        elif block.kind == "table":
            _docx_table(document, block)
        elif block.kind == "summary":
            _docx_heading(document, block.title or "Synthèse", 1)

    document.core_properties.title = f"Note de calcul – {assessment.project_name}"
    document.core_properties.subject = "RPA 2024 / BAEL 91"
    document.core_properties.author = APP_NAME
    document.core_properties.keywords = "RPA 2024, BAEL, note de calcul, CTC"
    document.save(path)
    return path


def _set_font(style, name: str, size: float, bold: bool = False, color: str | None = None) -> None:
    from docx.oxml.ns import qn
    from docx.shared import Pt, RGBColor

    style.font.name = name
    style.font.size = Pt(size)
    style.font.bold = bold
    if color:
        style.font.color.rgb = RGBColor.from_string(color)
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        from docx.oxml import OxmlElement
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    for attribute in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        rfonts.set(qn(attribute), name)


def _docx_header_footer(document, assessment: Assessment) -> None:
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.shared import Cm, Pt, RGBColor

    header = document.sections[0].header
    table = header.add_table(rows=1, cols=2, width=Cm(17.0))
    table.autofit = True
    left = table.cell(0, 0).paragraphs[0]
    run = left.add_run(f"{APP_NAME}  |  Note de calcul RPA 2024 / BAEL 91")
    _run_style(run, 8, color="44546A")
    right = table.cell(0, 1).paragraphs[0]
    right.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = right.add_run(f"{assessment.project_name} – {assessment.joint_name}")
    _run_style(run, 8, color="44546A")

    footer = document.sections[0].footer
    paragraph = footer.paragraphs[0]
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run(f"{APP_NAME} v{APP_VERSION} – page ")
    _run_style(run, 8, color="6B7280")
    _docx_page_field(paragraph)
    run = paragraph.add_run(f" – {assessment.generated_at}")
    _run_style(run, 8, color="6B7280")


def _run_style(run, size: float, bold: bool = False, color: str | None = None) -> None:
    from docx.shared import Pt, RGBColor

    run.font.name = "Arial"
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = RGBColor.from_string(color)


def _docx_page_field(paragraph) -> None:
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn

    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = " PAGE "
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    text = OxmlElement("w:t")
    text.text = "1"
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instruction)
    run._r.append(separate)
    run._r.append(text)
    run._r.append(end)


def _docx_toc(document) -> None:
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn

    paragraph = document.add_paragraph()
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    instruction = OxmlElement("w:instrText")
    instruction.set(qn("xml:space"), "preserve")
    instruction.text = r'TOC \o "1-2" \h \z \u'
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    placeholder = OxmlElement("w:t")
    placeholder.text = "Mettre à jour le champ (Ctrl+A puis F9) pour afficher la pagination."
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(begin)
    run._r.append(instruction)
    run._r.append(separate)
    run._r.append(placeholder)
    run._r.append(end)

    settings = document.settings.element
    update = settings.find(qn("w:updateFields"))
    if update is None:
        update = OxmlElement("w:updateFields")
        settings.append(update)
    update.set(qn("w:val"), "true")


def _docx_cover(document, assessment: Assessment) -> None:
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.shared import Cm, Pt, RGBColor

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.space_before = Pt(40)
    run = paragraph.add_run("ARCHILAB")
    run.font.name = "Arial"
    run.font.size = Pt(28)
    run.bold = True
    run.font.color.rgb = RGBColor(0x17, 0x36, 0x5D)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run("RPA2024 NoteDeCalcul Studio")
    run.font.name = "Arial"
    run.font.size = Pt(13)
    run.font.color.rgb = RGBColor(0x44, 0x54, 0x6A)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.space_before = Pt(90)
    run = paragraph.add_run("NOTE DE CALCUL")
    run.font.name = "Arial"
    run.font.size = Pt(30)
    run.bold = True
    run.font.color.rgb = RGBColor(0x1F, 0x29, 0x33)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run("RPA 2024 (DTR BC 2.48)  •  BAEL 91 révisé 99  •  CBA 93")
    run.font.name = "Arial"
    run.font.size = Pt(11)
    run.font.color.rgb = RGBColor(0x5F, 0x6B, 0x7A)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.space_before = Pt(70)
    run = paragraph.add_run(assessment.project_name)
    run.font.name = "Arial"
    run.font.size = Pt(20)
    run.bold = True
    run.font.color.rgb = RGBColor(0x17, 0x36, 0x5D)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run(f"Joint / bloc : {assessment.joint_name}")
    run.font.name = "Arial"
    run.font.size = Pt(13)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run(f"Wilaya : {assessment.config.wilaya}  •  Zone {assessment.seismic.resolved.zone_code}  •  Site {assessment.seismic.resolved.site}")
    run.font.name = "Arial"
    run.font.size = Pt(10.5)
    run.font.color.rgb = RGBColor(0x44, 0x54, 0x6A)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.space_before = Pt(90)
    run = paragraph.add_run(assessment.status.value.upper())
    run.font.name = "Arial"
    run.font.size = Pt(15)
    run.bold = True
    run.font.color.rgb = RGBColor.from_string(STATUS_COLORS[assessment.status])

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = paragraph.add_run(f"Édition du {assessment.generated_at}  •  {APP_NAME} v{APP_VERSION}")
    run.font.name = "Arial"
    run.font.size = Pt(9)
    run.font.color.rgb = RGBColor(0x6B, 0x72, 0x80)

    paragraph = document.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.space_before = Pt(24)
    run = paragraph.add_run(
        "Document généré automatiquement. Vérification des hypothèses, des plans et visa "
        "d'un ingénieur habilité requis avant diffusion au CTC."
    )
    run.font.name = "Arial"
    run.font.size = Pt(8)
    run.italic = True
    run.font.color.rgb = RGBColor(0x6B, 0x72, 0x80)


def _docx_heading(document, text: str, level: int) -> None:
    document.add_paragraph(text, style=f"Heading {level}")


def _docx_status_badge(document, text: str, detail: str, severity: int) -> None:
    from docx.shared import Pt, RGBColor

    status = next((s for s in Status if s.value == text), Status.NA)
    paragraph = document.add_paragraph()
    run = paragraph.add_run(f"Statut : {text}")
    run.bold = True
    run.font.size = Pt(9.5)
    run.font.color.rgb = RGBColor.from_string(STATUS_COLORS[status])
    if detail:
        run = paragraph.add_run(f"   ({detail})")
        run.font.size = Pt(8.5)
        run.font.color.rgb = RGBColor(0x6B, 0x72, 0x80)


def _docx_callout(document, text: str, severity: int) -> None:
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.shared import Cm, Pt, RGBColor

    status = Status.FAILED if severity >= 2 else Status.WARNING if severity == 1 else Status.OK
    table = document.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = table.cell(0, 0)
    _shade(cell, {"1E7B4B": "E8F5EE", "B26A00": "FFF4E0", "B00020": "FDE8EA"}.get(
        STATUS_COLORS[status], "EEF2F7"))
    paragraph = cell.paragraphs[0]
    run = paragraph.add_run(text)
    run.font.name = "Arial"
    run.font.size = Pt(9.5)
    run.font.color.rgb = RGBColor(0x1F, 0x29, 0x33)


def _docx_figure(document, tag: str) -> None:
    from docx.shared import Cm, Pt, RGBColor

    table = document.add_table(rows=1, cols=1)
    cell = table.cell(0, 0)
    _shade(cell, "FFF7D6")
    paragraph = cell.paragraphs[0]
    paragraph.paragraph_format.space_before = Pt(6)
    paragraph.paragraph_format.space_after = Pt(6)
    run = paragraph.add_run(tag)
    run.font.name = "Arial"
    run.font.size = Pt(9)
    run.bold = True
    run.font.color.rgb = RGBColor(0x7A, 0x4F, 0x00)


def _docx_cv(document, text: str) -> None:
    from docx.shared import Pt, RGBColor

    paragraph = document.add_paragraph()
    paragraph.paragraph_format.space_before = Pt(4)
    run = paragraph.add_run(text)
    run.font.name = "Arial"
    run.font.size = Pt(9.2)
    run.italic = True
    run.font.color.rgb = RGBColor(0x2F, 0x4F, 0x6F)


def _shade(cell, hex_color: str) -> None:
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn

    tc_pr = cell._tc.get_or_add_tcPr()
    shading = OxmlElement("w:shd")
    shading.set(qn("w:fill"), hex_color)
    shading.set(qn("w:val"), "clear")
    tc_pr.append(shading)


def _docx_table(document, block: Block) -> None:
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.shared import Cm, Pt, RGBColor

    if block.title:
        paragraph = document.add_paragraph()
        run = paragraph.add_run(block.title)
        run.font.name = "Arial"
        run.font.size = Pt(9)
        run.bold = True
        run.font.color.rgb = RGBColor(0x1F, 0x4E, 0x79)
        paragraph.paragraph_format.space_before = Pt(6)
        paragraph.paragraph_format.space_after = Pt(2)
    if not block.headers:
        return
    table = document.add_table(rows=1, cols=len(block.headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    widths = block.widths or [17.0 / len(block.headers)] * len(block.headers)
    if len(widths) != len(block.headers):
        widths = [17.0 / len(block.headers)] * len(block.headers)
    for index, header in enumerate(block.headers):
        cell = table.rows[0].cells[index]
        cell.width = Cm(widths[index])
        _shade(cell, "17365D")
        paragraph = cell.paragraphs[0]
        run = paragraph.add_run(str(header))
        run.font.name = "Arial"
        run.font.size = Pt(8)
        run.bold = True
        run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    for row_index, row in enumerate(block.rows):
        cells = table.add_row().cells
        status = block.statuses[row_index] if row_index < len(block.statuses) else None
        for index, value in enumerate(row[:len(block.headers)]):
            cell = cells[index]
            cell.width = Cm(widths[index])
            if row_index % 2 == 1:
                _shade(cell, "F4F6F9")
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            run = paragraph.add_run(str(value))
            run.font.name = "Arial"
            run.font.size = Pt(7.8)
            if index == len(row) - 1 and status is not None:
                run.bold = True
                run.font.color.rgb = RGBColor.from_string(STATUS_COLORS[status])
            if index >= len(row) - 3 and block.headers[index].lower().startswith(("requis", "fourni")):
                run.font.size = Pt(7.4)
    document.add_paragraph().paragraph_format.space_after = Pt(0)


# ---------------------------------------------------------------------------
# PDF
# ---------------------------------------------------------------------------

def generate_pdf(assessment: Assessment, path: str | Path,
                 plans: PlanExportResult | None = None) -> Path:
    try:
        from reportlab.lib import colors
        from reportlab.lib.enums import TA_CENTER, TA_LEFT
        from reportlab.lib.pagesizes import A4
        from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
        from reportlab.lib.units import cm
        from reportlab.pdfbase import pdfmetrics
        from reportlab.pdfbase.ttfonts import TTFont
        from reportlab.platypus import (
            BaseDocTemplate, Frame, KeepTogether, PageBreak, PageTemplate, Paragraph,
            Spacer, Table, TableStyle,
        )
        from reportlab.platypus.tableofcontents import TableOfContents
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("reportlab requis : pip install reportlab") from exc

    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    base_font, bold_font = _register_pdf_fonts(pdfmetrics, TTFont)
    safe = (lambda text: str(text)) if base_font != "Helvetica" else _latin_safe

    colors_map = {
        "navy": colors.HexColor("#17365D"),
        "blue": colors.HexColor("#1F4E79"),
        "ink": colors.HexColor("#1F2933"),
        "muted": colors.HexColor("#5F6B7A"),
        "ok": colors.HexColor("#1E7B4B"),
        "warning": colors.HexColor("#B26A00"),
        "failed": colors.HexColor("#B00020"),
        "na": colors.HexColor("#5F6B7A"),
        "row": colors.HexColor("#F4F6F9"),
        "figure": colors.HexColor("#FFF7D6"),
    }
    style_sheet = getSampleStyleSheet()
    styles = {
        "cover_title": ParagraphStyle("cover_title", parent=style_sheet["Title"],
                                      fontName=bold_font, fontSize=26, leading=31,
                                      textColor=colors_map["navy"], alignment=TA_CENTER,
                                      spaceAfter=8),
        "cover_sub": ParagraphStyle("cover_sub", parent=style_sheet["Normal"],
                                    fontName=base_font, fontSize=10.5, leading=15,
                                    textColor=colors_map["muted"], alignment=TA_CENTER),
        "cover_project": ParagraphStyle("cover_project", parent=style_sheet["Normal"],
                                        fontName=bold_font, fontSize=18, leading=23,
                                        textColor=colors_map["navy"], alignment=TA_CENTER),
        "h1": ParagraphStyle("H1", parent=style_sheet["Heading1"], fontName=bold_font,
                             fontSize=15, leading=19, textColor=colors_map["navy"],
                             spaceBefore=8, spaceAfter=6),
        "h2": ParagraphStyle("H2", parent=style_sheet["Heading2"], fontName=bold_font,
                             fontSize=11.5, leading=15, textColor=colors_map["blue"],
                             spaceBefore=8, spaceAfter=4),
        "h3": ParagraphStyle("H3", parent=style_sheet["Heading3"], fontName=bold_font,
                             fontSize=10, leading=13, textColor=colors_map["blue"],
                             spaceBefore=6, spaceAfter=3),
        "body": ParagraphStyle("Body", parent=style_sheet["BodyText"], fontName=base_font,
                               fontSize=8.8, leading=12.2, textColor=colors_map["ink"],
                               spaceAfter=4, alignment=TA_LEFT),
        "lead": ParagraphStyle("Lead", parent=style_sheet["BodyText"], fontName=base_font,
                               fontSize=9.2, leading=13, textColor=colors_map["muted"],
                               spaceAfter=6),
        "small": ParagraphStyle("Small", parent=style_sheet["BodyText"], fontName=base_font,
                                fontSize=7.6, leading=10, textColor=colors_map["muted"],
                                spaceAfter=2),
        "bullet": ParagraphStyle("Bullet", parent=style_sheet["BodyText"], fontName=base_font,
                                 fontSize=8.6, leading=11.6, leftIndent=10, bulletIndent=2,
                                 spaceAfter=2),
        "figure": ParagraphStyle("Figure", parent=style_sheet["BodyText"], fontName=bold_font,
                                 fontSize=8.2, leading=11, textColor=colors.HexColor("#7A4F00")),
        "cv": ParagraphStyle("CV", parent=style_sheet["BodyText"], fontName=base_font,
                             fontSize=8.6, leading=11.8, textColor=colors.HexColor("#2F4F6F"),
                             leftIndent=8, rightIndent=8, spaceBefore=4, spaceAfter=6),
        "status": ParagraphStyle("Status", parent=style_sheet["BodyText"], fontName=bold_font,
                                 fontSize=9.2, leading=12, spaceAfter=4),
    }

    class NumberedDocTemplate(BaseDocTemplate):
        def __init__(self, filename, **kwargs):
            BaseDocTemplate.__init__(self, filename, **kwargs)
            frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id="normal")
            self.addPageTemplates([PageTemplate(id="main", frames=[frame], onPage=self._decorate)])

        def _decorate(self, canvas, doc):
            canvas.saveState()
            canvas.setStrokeColor(colors.HexColor("#C9D2DC"))
            canvas.setLineWidth(0.4)
            canvas.line(doc.leftMargin, doc.height + doc.bottomMargin + 8,
                        doc.leftMargin + doc.width, doc.height + doc.bottomMargin + 8)
            canvas.setFont(base_font, 7)
            canvas.setFillColor(colors_map["muted"])
            canvas.drawString(doc.leftMargin, A4[1] - 1.35 * cm,
                              f"{APP_NAME}  |  Note de calcul RPA 2024 / BAEL 91")
            canvas.drawRightString(A4[0] - doc.rightMargin, A4[1] - 1.35 * cm,
                                   f"{assessment.project_name} – {assessment.joint_name}")
            canvas.line(doc.leftMargin, doc.bottomMargin - 8,
                        doc.leftMargin + doc.width, doc.bottomMargin - 8)
            canvas.drawString(doc.leftMargin, doc.bottomMargin - 20,
                              f"{APP_NAME} v{APP_VERSION} – {assessment.generated_at}")
            canvas.drawRightString(A4[0] - doc.rightMargin, doc.bottomMargin - 20,
                                   f"Page {doc.page}")
            canvas.restoreState()

        def afterFlowable(self, flowable):
            if isinstance(flowable, Paragraph):
                style = flowable.style.name
                if style in ("H1", "H2"):
                    text = flowable.getPlainText()
                    if text.strip() == "Sommaire":
                        return
                    level = 0 if style == "H1" else 1
                    self.notify("TOCEntry", (level, text, self.page))

    doc = NumberedDocTemplate(
        str(path), pagesize=A4,
        leftMargin=1.75 * cm, rightMargin=1.75 * cm,
        topMargin=2.0 * cm, bottomMargin=2.0 * cm,
        title=f"Note de calcul – {assessment.project_name}",
        author=APP_NAME, subject="RPA 2024 / BAEL 91",
    )

    story = []
    story.extend(_pdf_cover(assessment, styles, colors_map, base_font, bold_font, Paragraph, Spacer, PageBreak, Table, TableStyle, colors))
    story.append(PageBreak())
    story.append(Paragraph("Sommaire", styles["h1"]))
    story.append(Spacer(1, 4))
    toc = TableOfContents()
    toc.levelStyles = [
        ParagraphStyle("TOC1", fontName=base_font, fontSize=9.5, leading=15,
                       textColor=colors_map["ink"]),
        ParagraphStyle("TOC2", fontName=base_font, fontSize=8.2, leading=12,
                       leftIndent=14, textColor=colors_map["muted"]),
    ]
    toc.dotsMinLevel = 0
    story.append(toc)
    story.append(PageBreak())

    blocks = build_document_blocks(assessment, plans)
    for block in blocks:
        if block.kind == "pagebreak":
            story.append(PageBreak())
        elif block.kind in ("h1", "h2", "h3"):
            story.append(Paragraph(safe(block.text), styles[block.kind]))
        elif block.kind == "lead":
            story.append(Paragraph(safe(block.text), styles["lead"]))
        elif block.kind == "small":
            story.append(Paragraph(safe(block.text), styles["small"]))
        elif block.kind == "bullet":
            story.append(Paragraph("• " + safe(block.text), styles["bullet"]))
        elif block.kind == "status":
            status = next((s for s in Status if s.value == block.text), Status.NA)
            story.append(Paragraph(
                f'<font color="{_hex(STATUS_COLORS[status])}">Statut : {safe(block.text)}</font>'
                + (f'  <font size="7.5" color="#6B7280">({safe(block.title)})</font>' if block.title else ""),
                styles["status"],
            ))
        elif block.kind == "callout":
            story.append(_pdf_callout(block.text, int(block.level), styles, colors_map,
                                      Table, TableStyle, Paragraph, Spacer))
        elif block.kind == "figure":
            story.append(_pdf_figure(block.tag, styles, colors_map, Table, TableStyle, Paragraph, Spacer, safe))
        elif block.kind == "cv":
            story.append(_pdf_cv(block.text, styles, Paragraph, safe))
        elif block.kind == "table":
            flow = _pdf_table(block, styles, colors_map, Table, TableStyle,
                              Paragraph, Spacer, safe, base_font)
            if isinstance(flow, list):
                story.extend(flow)
            else:
                story.append(flow)
        elif block.kind == "summary":
            story.append(Paragraph(safe(block.title or "Synthèse"), styles["h1"]))

    doc.multiBuild(story)
    return path


def _latin_safe(text: str) -> str:
    return (str(text).replace("≤", "<=").replace("≥", ">=").replace("µ", "u")
            .replace("–", "-").replace("’", "'").replace("×", "x"))


def _hex(color: str) -> str:
    return "#" + color


def _register_pdf_fonts(pdfmetrics, TTFont) -> tuple[str, str]:
    candidates = [
        ("Arial", Path("C:/Windows/Fonts/arial.ttf"), Path("C:/Windows/Fonts/arialbd.ttf")),
        ("DejaVuSans", Path("C:/Windows/Fonts/DejaVuSans.ttf"), Path("C:/Windows/Fonts/DejaVuSans-Bold.ttf")),
        ("LiberationSans", Path("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
         Path("/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf")),
    ]
    for name, regular, bold in candidates:
        if regular.exists() and bold.exists():
            try:
                pdfmetrics.registerFont(TTFont(name, str(regular)))
                pdfmetrics.registerFont(TTFont(name + "-Bold", str(bold)))
                return name, name + "-Bold"
            except Exception:
                continue
    return "Helvetica", "Helvetica-Bold"


def _pdf_cover(assessment, styles, colors_map, base_font, bold_font, Paragraph, Spacer,
               PageBreak, Table, TableStyle, colors):
    story = []
    story.append(Spacer(1, 2.0 * __import__("reportlab").lib.units.cm))
    story.append(Paragraph("ARCHILAB", styles["cover_title"]))
    story.append(Paragraph("RPA2024 NoteDeCalcul Studio", styles["cover_sub"]))
    story.append(Spacer(1, 3.0 * __import__("reportlab").lib.units.cm))
    story.append(Paragraph("NOTE DE CALCUL", styles["cover_title"]))
    story.append(Paragraph("RPA 2024 (DTR BC 2.48) • BAEL 91 révisé 99 • CBA 93", styles["cover_sub"]))
    story.append(Spacer(1, 3.2 * __import__("reportlab").lib.units.cm))
    story.append(Paragraph(_pdf_escape(assessment.project_name), styles["cover_project"]))
    story.append(Spacer(1, 0.3 * __import__("reportlab").lib.units.cm))
    story.append(Paragraph(f"Joint / bloc : {_pdf_escape(assessment.joint_name)}", styles["cover_sub"]))
    story.append(Paragraph(
        f"Wilaya : {_pdf_escape(assessment.config.wilaya)} • Zone {assessment.seismic.resolved.zone_code} "
        f"• Site {assessment.seismic.resolved.site}", styles["cover_sub"]))
    story.append(Spacer(1, 3.0 * __import__("reportlab").lib.units.cm))
    status_color = colors.HexColor(_hex(STATUS_COLORS[assessment.status]))
    story.append(Paragraph(
        f'<font color="{_hex(STATUS_COLORS[assessment.status])}" size="14"><b>{_pdf_escape(assessment.status.value.upper())}</b></font>',
        styles["cover_sub"]))
    story.append(Spacer(1, 0.5 * __import__("reportlab").lib.units.cm))
    story.append(Paragraph(f"Édition du {assessment.generated_at} • {APP_NAME} v{APP_VERSION}",
                           styles["cover_sub"]))
    story.append(Spacer(1, 2.0 * __import__("reportlab").lib.units.cm))
    story.append(Paragraph(
        "Document généré automatiquement. Vérification des hypothèses, des plans et visa "
        "d'un ingénieur habilité requis avant diffusion au CTC.", styles["cover_sub"]))
    return story


def _pdf_escape(text: str) -> str:
    return str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _pdf_callout(text, severity, styles, colors_map, Table, TableStyle, Paragraph, Spacer):
    from reportlab.lib.units import cm
    color = colors_map["failed"] if severity >= 2 else colors_map["warning"] if severity == 1 else colors_map["ok"]
    background = {2: colors_map["failed"].clone(alpha=0.08), 1: colors_map["warning"].clone(alpha=0.08)}.get(
        severity, colors_map["ok"].clone(alpha=0.08))
    table = Table([[Paragraph(_pdf_escape(text), styles["body"])]], colWidths=[17.0 * cm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), background),
        ("BOX", (0, 0), (-1, -1), 0.8, color),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    return table


def _pdf_figure(tag, styles, colors_map, Table, TableStyle, Paragraph, Spacer, safe):
    from reportlab.lib.units import cm
    from reportlab.platypus import KeepTogether
    table = Table([[Paragraph(safe(tag), styles["figure"])]], colWidths=[17.0 * cm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), colors_map["figure"]),
        ("BOX", (0, 0), (-1, -1), 0.8, colors_map["warning"]),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return KeepTogether([Spacer(1, 4), table, Spacer(1, 3)])


def _pdf_cv(text, styles, Paragraph, safe):
    from reportlab.lib.units import cm
    from reportlab.lib import colors
    from reportlab.platypus import Table, TableStyle
    value = safe(text)
    table = Table([[Paragraph(value, styles["cv"])]], colWidths=[17.0 * cm])
    table.setStyle(TableStyle([
        ("LINEBEFORE", (0, 0), (0, -1), 2.2, colors.HexColor("#2F4F6F")),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    return table


def _pdf_table(block, styles, colors_map, Table, TableStyle, Paragraph, Spacer, safe, base_font):
    from reportlab.lib.units import cm
    from reportlab.lib import colors
    from reportlab.platypus import KeepTogether

    elements = []
    if block.title:
        title = Paragraph(f"<b>{_pdf_escape(block.title)}</b>", styles["h3"])
        elements.append(title)
    if not block.headers:
        return KeepTogether(elements) if elements else Spacer(1, 2)
    headers = [Paragraph(f'<b><font color="#FFFFFF">{_pdf_escape(h)}</font></b>',
                         styles["small"]) for h in block.headers]
    widths = block.widths or [17.0 / len(block.headers)] * len(block.headers)
    total = 17.0
    scale = total / sum(widths) if sum(widths) > 0 else 1.0
    col_widths = [max(0.9, w * scale) * cm for w in widths]
    data = [headers]
    for row in block.rows:
        data.append([Paragraph(safe(str(v)), styles["small"]) for v in row[:len(block.headers)]])
    table = Table(data, colWidths=col_widths, repeatRows=1)
    commands = [
        ("BACKGROUND", (0, 0), (-1, 0), colors_map["navy"]),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.3, colors_map["na"].clone(alpha=0.35)),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
        ("TOPPADDING", (0, 0), (-1, -1), 2.5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2.5),
    ]
    for index in range(1, len(data)):
        if index % 2 == 0:
            commands.append(("BACKGROUND", (0, index), (-1, index), colors_map["row"]))
        if index - 1 < len(block.statuses):
            status = block.statuses[index - 1]
            commands.append(("TEXTCOLOR", (-1, index), (-1, index),
                             colors.HexColor(_hex(STATUS_COLORS[status]))))
    table.setStyle(TableStyle(commands))
    elements.append(table)
    elements.append(Spacer(1, 5))
    # Les tableaux courts restent groupes ; les tableaux longs se coupent
    # proprement entre deux pages avec repetition de l'en-tete.
    if len(data) > 30:
        return elements
    return KeepTogether(elements)


def generate_reports(assessment: Assessment, out_dir: str | Path,
                     basename: str = "NOTE_CALCUL", include_plans: bool = True,
                     story_name: str | None = None) -> ReportResult:
    output = Path(out_dir)
    output.mkdir(parents=True, exist_ok=True)
    root = re.sub(r"[^0-9A-Za-z_\-]+", "_", basename).strip("_") or "NOTE_CALCUL"
    result = ReportResult()
    plans = export_plans(assessment, output / "plans", story_name) if include_plans else None
    result.plans = plans
    if plans and plans.files:
        result.log.append(f"{len(plans.files)} fichier(s) de plans exporté(s) pour le niveau {plans.story}.")
    result.docx = generate_docx(assessment, output / f"{root}.docx", plans)
    result.log.append(f"Word généré : {result.docx.name}")
    result.pdf = generate_pdf(assessment, output / f"{root}.pdf", plans)
    result.log.append(f"PDF généré : {result.pdf.name}")
    return result

__all__ += ["APP_NAME", "APP_VERSION"]
