"""Interface graphique CustomTkinter d'Archilab RPA2024 NoteDeCalcul Studio."""

from __future__ import annotations

import os
import queue
import tempfile
import threading
import traceback
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Callable

import customtkinter as ctk

from parsers import (
    BRACING_SYSTEMS,
    IMPORTANCE_GROUPS,
    OCCUPANCY_CASES,
    SEISMIC_ZONES,
    SITE_CLASSES,
    WILAYAS,
    ParsedProject,
    ProjectConfig,
    read_project,
)
from rpa_engine import Assessment, EngineOptions, Status, assess, fmt
from report_generator import APP_NAME, APP_VERSION, ReportResult, generate_reports
from samples import make_demo_data

__all__ = ["ArchilabApp", "launch"]

NAVY = "#17365D"
BLUE = "#1F4E79"
ACCENT = "#2F6F9F"
BG = "#EEF2F6"
CARD = "#FFFFFF"
INK = "#1F2933"
MUTED = "#5F6B7A"
BORDER = "#D5DEE7"
OK = "#1E7B4B"
WARNING = "#B26A00"
FAILED = "#B00020"
NA = "#5F6B7A"
STATUS_COLORS = {
    Status.OK: OK,
    Status.WARNING: WARNING,
    Status.FAILED: FAILED,
    Status.NA: NA,
}

TITLE_FONT = ("Segoe UI", 22, "bold")
H1_FONT = ("Segoe UI", 18, "bold")
H2_FONT = ("Segoe UI", 14, "bold")
BODY_FONT = ("Segoe UI", 11)
SMALL_FONT = ("Segoe UI", 9)


def _status_color(status: Status) -> str:
    return STATUS_COLORS.get(status, NA)


def _resource_dir() -> Path:
    return Path(__file__).resolve().parent


class Card(ctk.CTkFrame):
    def __init__(self, master, title: str = "", subtitle: str = "", **kwargs):
        super().__init__(master, fg_color=CARD, corner_radius=10, border_width=1,
                         border_color=BORDER, **kwargs)
        self.grid_columnconfigure(0, weight=1)
        row = 0
        if title:
            ctk.CTkLabel(self, text=title, font=H2_FONT, text_color=INK,
                         anchor="w").grid(row=row, column=0, sticky="ew", padx=16, pady=(14, 2))
            row += 1
        if subtitle:
            ctk.CTkLabel(self, text=subtitle, font=SMALL_FONT, text_color=MUTED,
                         anchor="w", wraplength=780, justify="left").grid(
                row=row, column=0, sticky="ew", padx=16, pady=(0, 6))
            row += 1
        self.body = ctk.CTkFrame(self, fg_color="transparent")
        self.body.grid(row=row, column=0, sticky="nsew", padx=12, pady=(4, 12))
        self.body.grid_columnconfigure(1, weight=1)


class StatCard(ctk.CTkFrame):
    def __init__(self, master, label: str, value: str = "—", color: str = NAVY):
        super().__init__(master, fg_color=CARD, corner_radius=9, border_width=1,
                         border_color=BORDER)
        self.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(self, text=label, font=SMALL_FONT, text_color=MUTED,
                     anchor="w").grid(row=0, column=0, sticky="ew", padx=12, pady=(10, 0))
        self.value = ctk.CTkLabel(self, text=value, font=("Segoe UI", 20, "bold"),
                                  text_color=color, anchor="w")
        self.value.grid(row=1, column=0, sticky="ew", padx=12, pady=(0, 10))

    def set(self, value: str, color: str | None = None) -> None:
        self.value.configure(text=value)
        if color:
            self.value.configure(text_color=color)


class ArchilabApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        ctk.set_appearance_mode("light")
        ctk.set_default_color_theme("blue")
        self.title(f"{APP_NAME} v{APP_VERSION}")
        self.geometry("1420x900")
        self.minsize(1180, 760)
        self.configure(fg_color=BG)
        self.protocol("WM_DELETE_WINDOW", self._on_close)

        self.parsed: ParsedProject | None = None
        self.assessment: Assessment | None = None
        self.etabs_path: str = ""
        self.safe_path: str = ""
        self.current_page = "import"
        self.last_report: ReportResult | None = None
        self._demo_loaded = False
        self._busy = False
        self._log_queue: queue.Queue[str] = queue.Queue()
        self._result_queue: queue.Queue[tuple[Callable, object, bool]] = queue.Queue()

        self._build_header()
        self._build_body()
        self._build_statusbar()
        self._show_page("import")
        self._poll_log()
        self._poll_results()

    # ------------------------------------------------------------------
    # Structure
    # ------------------------------------------------------------------

    def _build_header(self) -> None:
        header = ctk.CTkFrame(self, fg_color=NAVY, corner_radius=0, height=72)
        header.pack(side="top", fill="x")
        header.pack_propagate(False)
        header.grid_columnconfigure(2, weight=1)

        logo = ctk.CTkFrame(header, fg_color="transparent")
        logo.grid(row=0, column=0, padx=(22, 12), pady=12, sticky="w")
        ctk.CTkLabel(logo, text="ARCHILAB", font=("Segoe UI", 20, "bold"),
                     text_color="#FFFFFF").pack(anchor="w")
        ctk.CTkLabel(logo, text="RPA2024 NoteDeCalcul Studio",
                     font=("Segoe UI", 9), text_color="#C9D8E8").pack(anchor="w")

        self.header_project = ctk.CTkLabel(
            header, text="Aucun projet importé", font=("Segoe UI", 12),
            text_color="#E8EFF6", anchor="w")
        self.header_project.grid(row=0, column=1, padx=(20, 10), sticky="w")

        right = ctk.CTkFrame(header, fg_color="transparent")
        right.grid(row=0, column=3, padx=(10, 20), pady=10, sticky="e")
        self.header_status = ctk.CTkLabel(
            right, text="EN ATTENTE", font=("Segoe UI", 10, "bold"),
            text_color="#FFFFFF", fg_color="#5F6B7A", corner_radius=6,
            padx=10, pady=4)
        self.header_status.pack(side="left", padx=(0, 10))
        self.theme_switch = ctk.CTkSegmentedButton(
            right, values=["Clair", "Sombre"], command=self._toggle_theme,
            width=130, height=28)
        self.theme_switch.set("Clair")
        self.theme_switch.pack(side="left")

    def _build_body(self) -> None:
        body = ctk.CTkFrame(self, fg_color="transparent")
        body.pack(side="top", fill="both", expand=True)
        body.grid_columnconfigure(1, weight=1)
        body.grid_rowconfigure(0, weight=1)

        sidebar = ctk.CTkFrame(body, fg_color="#FFFFFF", corner_radius=0, width=224,
                               border_width=0)
        sidebar.grid(row=0, column=0, sticky="nsw")
        sidebar.grid_propagate(False)
        sidebar.grid_rowconfigure(8, weight=1)

        ctk.CTkLabel(sidebar, text="NAVIGATION", font=("Segoe UI", 8, "bold"),
                     text_color="#8A96A3", anchor="w").grid(
            row=0, column=0, sticky="ew", padx=18, pady=(18, 8))

        self.nav_buttons: dict[str, ctk.CTkButton] = {}
        nav_items = [
            ("import", "1. Importer ETABS / SAFE"),
            ("config", "2. Configuration"),
            ("checks", "3. Vérifications"),
            ("report", "4. Note et plans"),
            ("journal", "Journal d'exécution"),
            ("help", "Aide et réglementation"),
        ]
        for index, (key, label) in enumerate(nav_items, start=1):
            button = ctk.CTkButton(
                sidebar, text=label, anchor="w", height=38, corner_radius=6,
                fg_color="transparent", text_color="#33414E", hover_color="#E7EEF5",
                font=("Segoe UI", 11), command=lambda k=key: self._show_page(k))
            button.grid(row=index, column=0, sticky="ew", padx=12, pady=2)
            self.nav_buttons[key] = button

        footer = ctk.CTkFrame(sidebar, fg_color="transparent")
        footer.grid(row=9, column=0, sticky="sew", padx=16, pady=14)
        ctk.CTkLabel(footer, text="Moteur RPA 2024 + BAEL 91\nLecture ETABS / SAFE",
                     font=("Segoe UI", 8), text_color="#8A96A3", justify="left").pack(anchor="w")

        self.content = ctk.CTkFrame(body, fg_color=BG, corner_radius=0)
        self.content.grid(row=0, column=1, sticky="nsew")
        self.content.grid_rowconfigure(0, weight=1)
        self.content.grid_columnconfigure(0, weight=1)

        self.pages: dict[str, ctk.CTkFrame] = {}
        self.pages["import"] = self._build_import_page()
        self.pages["config"] = self._build_config_page()
        self.pages["checks"] = self._build_checks_page()
        self.pages["report"] = self._build_report_page()
        self.pages["journal"] = self._build_journal_page()
        self.pages["help"] = self._build_help_page()
        for page in self.pages.values():
            page.grid(row=0, column=0, sticky="nsew", padx=18, pady=14)

    def _build_statusbar(self) -> None:
        bar = ctk.CTkFrame(self, fg_color="#FFFFFF", corner_radius=0, height=30,
                           border_width=1, border_color=BORDER)
        bar.pack(side="bottom", fill="x")
        bar.pack_propagate(False)
        self.status_label = ctk.CTkLabel(bar, text="Prêt.", font=("Segoe UI", 9),
                                         text_color=MUTED, anchor="w")
        self.status_label.pack(side="left", padx=16)
        self.busy_bar = ctk.CTkProgressBar(bar, width=160, height=6, mode="indeterminate")
        self.busy_bar.pack(side="right", padx=16)
        self.busy_bar.set(0)

    # ------------------------------------------------------------------
    # Page Import
    # ------------------------------------------------------------------

    def _build_import_page(self) -> ctk.CTkFrame:
        page = ctk.CTkScrollableFrame(self.content, fg_color=BG, corner_radius=0)
        page.grid_columnconfigure(0, weight=1)

        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", pady=(2, 10))
        ctk.CTkLabel(header, text="Importer le modèle ETABS / SAFE", font=TITLE_FONT,
                     text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(header, text=(
            "Chargez un export de tableaux ETABS (texte tabulé, Excel ou Access) "
            "et, si disponible, un export SAFE pour les pressions de sol."
        ), font=BODY_FONT, text_color=MUTED, anchor="w", justify="left",
            wraplength=900).pack(anchor="w", pady=(2, 0))

        files = Card(page, "Fichiers sources",
                     "Les tableaux reconnus : Story Data, Point Coordinates, Connectivity, "
                     "sections, résultats modaux, efforts, déplacements et centres de masse.")
        files.grid(row=1, column=0, sticky="ew", pady=(0, 10))
        self.etabs_entry = self._file_row(files.body, 0, "Modèle ETABS *",
                                          "Export texte, .xlsx/.xlsm ou .mdb/.accdb",
                                          self._browse_etabs)
        self.safe_entry = self._file_row(files.body, 1, "Résultats SAFE",
                                         "Optionnel : pressions de sol, semelles, poinçonnement",
                                         self._browse_safe)
        self.joint_entry = self._labeled_entry(files.body, 2, "Nom du joint / bloc", "Joint A")

        actions = ctk.CTkFrame(page, fg_color="transparent")
        actions.grid(row=2, column=0, sticky="ew", pady=(0, 12))
        self.import_button = ctk.CTkButton(
            actions, text="Importer et calculer", height=42, width=220,
            fg_color=ACCENT, hover_color=BLUE, font=("Segoe UI", 12, "bold"),
            command=self._do_import)
        self.import_button.pack(side="left")
        ctk.CTkButton(actions, text="Charger la démonstration", height=42, width=200,
                      fg_color="#FFFFFF", text_color=NAVY, border_width=1,
                      border_color=BORDER, hover_color="#F0F5FA",
                      command=self._load_demo).pack(side="left", padx=(10, 0))
        ctk.CTkButton(actions, text="Vider", height=42, width=100,
                      fg_color="#FFFFFF", text_color=MUTED, border_width=1,
                      border_color=BORDER, hover_color="#F0F5FA",
                      command=self._clear_import).pack(side="left", padx=(10, 0))

        stats = ctk.CTkFrame(page, fg_color="transparent")
        stats.grid(row=3, column=0, sticky="ew", pady=(0, 10))
        for index in range(4):
            stats.grid_columnconfigure(index, weight=1)
        self.stat_import_state = StatCard(stats, "État de l'import")
        self.stat_stories = StatCard(stats, "Niveaux")
        self.stat_frames = StatCard(stats, "Éléments linéiques")
        self.stat_surfaces = StatCard(stats, "Surfaces")
        for index, card in enumerate((self.stat_import_state, self.stat_stories,
                                      self.stat_frames, self.stat_surfaces)):
            card.grid(row=0, column=index, sticky="ew", padx=(0 if index == 0 else 8, 0))

        summary = Card(page, "Contenu détecté",
                       "Les valeurs ci-dessous proviennent exclusivement des tableaux importés.")
        summary.grid(row=4, column=0, sticky="nsew")
        summary.body.grid_columnconfigure(0, weight=1)
        self.import_summary = ctk.CTkTextbox(summary.body, height=220, font=("Consolas", 10),
                                             fg_color="#F8FAFC", text_color=INK,
                                             border_width=0, wrap="word")
        self.import_summary.grid(row=0, column=0, sticky="nsew", padx=4, pady=4)
        self.import_summary.insert("1.0", "Aucun fichier importé.\n\n"
                                           "Utilisez « Charger la démonstration » pour tester "
                                           "le logiciel sans disposer d'un modèle ETABS.\n")
        self.import_summary.configure(state="disabled")
        return page

    def _file_row(self, parent, row: int, label: str, hint: str, command: Callable) -> ctk.CTkEntry:
        ctk.CTkLabel(parent, text=label, font=("Segoe UI", 11, "bold"),
                     text_color=INK, anchor="w").grid(row=row, column=0, sticky="w",
                                                      padx=(4, 12), pady=6)
        entry = ctk.CTkEntry(parent, height=34, font=("Segoe UI", 10),
                             placeholder_text=hint)
        entry.grid(row=row, column=1, sticky="ew", padx=(0, 8), pady=6)
        ctk.CTkButton(parent, text="Parcourir…", width=110, height=32,
                      fg_color="#E7EEF5", text_color=NAVY, hover_color="#D9E5F0",
                      command=command).grid(row=row, column=2, padx=4, pady=6)
        return entry

    def _labeled_entry(self, parent, row: int, label: str, value: str = "") -> ctk.CTkEntry:
        ctk.CTkLabel(parent, text=label, font=("Segoe UI", 11, "bold"),
                     text_color=INK, anchor="w").grid(row=row, column=0, sticky="w",
                                                      padx=(4, 12), pady=6)
        entry = ctk.CTkEntry(parent, height=34, font=("Segoe UI", 10))
        entry.grid(row=row, column=1, columnspan=2, sticky="ew", padx=(0, 8), pady=6)
        entry.insert(0, value)
        return entry

    def _browse_etabs(self) -> None:
        path = filedialog.askopenfilename(
            title="Sélectionner l'export ETABS",
            filetypes=[("ETABS / tableaux", "*.txt *.tsv *.csv *.xlsx *.xlsm *.mdb *.accdb"),
                       ("Tous les fichiers", "*.*")])
        if path:
            self.etabs_entry.delete(0, "end")
            self.etabs_entry.insert(0, path)

    def _browse_safe(self) -> None:
        path = filedialog.askopenfilename(
            title="Sélectionner l'export SAFE",
            filetypes=[("SAFE / tableaux", "*.txt *.tsv *.csv *.xlsx *.xlsm *.mdb *.accdb"),
                       ("Tous les fichiers", "*.*")])
        if path:
            self.safe_entry.delete(0, "end")
            self.safe_entry.insert(0, path)

    def _clear_import(self) -> None:
        self.etabs_entry.delete(0, "end")
        self.safe_entry.delete(0, "end")
        self.joint_entry.delete(0, "end")
        self.joint_entry.insert(0, "Joint A")
        self.parsed = None
        self.assessment = None
        self.header_project.configure(text="Aucun projet importé")
        self.header_status.configure(text="EN ATTENTE", fg_color="#5F6B7A")
        self._set_import_summary("Aucun fichier importé.")
        self.stat_import_state.set("Aucun", MUTED)
        self.stat_stories.set("—")
        self.stat_frames.set("—")
        self.stat_surfaces.set("—")
        self._log("Import réinitialisé.")

    def _load_demo(self) -> None:
        folder = Path(tempfile.gettempdir()) / "ArchilabRPA2024" / "demo"
        folder.mkdir(parents=True, exist_ok=True)
        etabs = folder / "DEMO_ETABS.txt"
        safe = folder / "DEMO_SAFE.txt"
        etabs.write_text(make_demo_data.build_etabs_text(), encoding="utf-8")
        safe.write_text(make_demo_data.build_safe_text(), encoding="utf-8")
        self.etabs_entry.delete(0, "end")
        self.etabs_entry.insert(0, str(etabs))
        self.safe_entry.delete(0, "end")
        self.safe_entry.insert(0, str(safe))
        self.joint_entry.delete(0, "end")
        self.joint_entry.insert(0, "Joint A")
        self._demo_loaded = True
        self._apply_demo_config()
        self._log(f"Démonstration écrite dans {folder}.")
        self._do_import()

    def _apply_demo_config(self) -> None:
        self.config_values["project_name"].delete(0, "end")
        self.config_values["project_name"].insert(0, "Résidence El Feth – Démonstration")
        self._set_combo("zone", "II - Faible a moyenne (A=0,10)")
        self._set_combo("site", "S3 - Meuble")
        self._set_combo("importance", "2 - Importance moyenne (I=1,00)")
        self._set_combo("bracing", "1 - Ossature BA (R=5,5)")
        self._set_combo("occupancy", "1 - Habitation / bureaux")
        self._set_entry("q_adm", "200")
        self._set_entry("ks", "30000")
        self._set_entry("joint_width", "250")
        self._set_entry("fc28", "25")
        self._set_entry("fe", "500")

    # ------------------------------------------------------------------
    # Page Configuration
    # ------------------------------------------------------------------

    def _build_config_page(self) -> ctk.CTkFrame:
        page = ctk.CTkScrollableFrame(self.content, fg_color=BG, corner_radius=0)
        page.grid_columnconfigure(0, weight=1)

        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", pady=(2, 10))
        ctk.CTkLabel(header, text="Configuration du projet", font=TITLE_FONT,
                     text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(header, text=(
            "Les paramètres saisis prévalent sur les valeurs par défaut. "
            "La zone sismique doit correspondre au découpage RPA 2024 de la wilaya ; "
            "elle n'est pas déduite automatiquement."
        ), font=BODY_FONT, text_color=MUTED, anchor="w", wraplength=950,
            justify="left").pack(anchor="w", pady=(2, 0))

        self.config_values: dict[str, ctk.CTkEntry | ctk.CTkComboBox | ctk.CTkCheckBox] = {}

        project = Card(page, "Projet et localisation")
        project.grid(row=1, column=0, sticky="ew", pady=(0, 10))
        self._combo_field(project.body, 0, 0, "wilaya", "Wilaya",
                          list(WILAYAS), "16 - Alger")
        self._entry_field(project.body, 0, 2, "project_name", "Nom du projet",
                          "Résidence El Feth")
        self._entry_field(project.body, 1, 0, "joint_name", "Joint / bloc", "Joint A")
        self._combo_field(project.body, 1, 2, "zone", "Zone sismique",
                          list(SEISMIC_ZONES), "II - Faible a moyenne (A=0,10)")
        self._combo_field(project.body, 2, 0, "site", "Classe de site",
                          list(SITE_CLASSES), "S3 - Meuble")
        self._combo_field(project.body, 2, 2, "importance", "Groupe d'importance",
                          list(IMPORTANCE_GROUPS), "2 - Importance moyenne (I=1,00)")

        seismic = Card(page, "Système sismique et régularité",
                       "Le choix du système fixe R, la catégorie de pénalité et le coefficient CT.")
        seismic.grid(row=2, column=0, sticky="ew", pady=(0, 10))
        self._combo_field(seismic.body, 0, 0, "bracing", "Système de contreventement",
                          list(BRACING_SYSTEMS), "1 - Ossature BA (R=5,5)")
        self._entry_field(seismic.body, 0, 2, "r_override", "R imposé (vide = Tableau 3.17)", "")
        self._entry_field(seismic.body, 1, 0, "qf_override", "QF imposé (vide = calcul)", "")
        self._entry_field(seismic.body, 1, 2, "ct_override", "CT imposé (vide = Tableau 4.3)", "")
        self._entry_field(seismic.body, 2, 0, "damping_override", "Amortissement ξ (%) (vide = Tableau 3.5)", "")
        self._entry_field(seismic.body, 2, 2, "lambda_override", "λ imposé (vide = Éq. 4.2)", "")
        self._combo_field(seismic.body, 3, 0, "occupancy", "Cas d'occupation (ψ)",
                          list(OCCUPANCY_CASES), "1 - Habitation / bureaux")
        checks = ctk.CTkFrame(seismic.body, fg_color="transparent")
        checks.grid(row=3, column=2, sticky="w", padx=4, pady=4)
        self.config_values["plan_regular"] = ctk.CTkCheckBox(
            checks, text="Régulier en plan", font=("Segoe UI", 10))
        self.config_values["plan_regular"].pack(side="left", padx=(0, 12))
        self.config_values["elevation_regular"] = ctk.CTkCheckBox(
            checks, text="Régulier en élévation", font=("Segoe UI", 10))
        self.config_values["elevation_regular"].pack(side="left", padx=(0, 12))
        self.config_values["redundant_walls"] = ctk.CTkCheckBox(
            checks, text="Redondance en plan", font=("Segoe UI", 10))
        self.config_values["redundant_walls"].pack(side="left")

        materials = Card(page, "Matériaux et enrobage")
        materials.grid(row=3, column=0, sticky="ew", pady=(0, 10))
        self._entry_field(materials.body, 0, 0, "fc28", "fc28 (MPa)", "25")
        self._entry_field(materials.body, 0, 2, "fe", "fe (MPa)", "500")
        self._entry_field(materials.body, 1, 0, "gamma_b", "γb", "1.5")
        self._entry_field(materials.body, 1, 2, "gamma_s", "γs", "1.15")
        self._entry_field(materials.body, 2, 0, "cover", "Enrobage (cm)", "4")
        self._entry_field(materials.body, 2, 2, "density", "Densité béton (kN/m³)", "25")

        soil = Card(page, "Sol, fondations et joint sismique",
                    "Le module de Winkler Ks et la portance q_adm proviennent de l'étude géotechnique.")
        soil.grid(row=4, column=0, sticky="ew", pady=(0, 10))
        self._entry_field(soil.body, 0, 0, "q_adm", "Portance admissible q_adm (kPa)", "200")
        self._entry_field(soil.body, 0, 2, "ks", "Module de Winkler Ks (kN/m³)", "30000")
        self._entry_field(soil.body, 1, 0, "joint_width", "Largeur de joint prévue (mm)", "")
        ctk.CTkLabel(soil.body, text="Tassement admissible : 25 mm (option)", font=("Segoe UI", 9),
                     text_color=MUTED, anchor="w").grid(row=1, column=2, columnspan=2,
                                                         sticky="w", padx=(4, 10), pady=5)

        options = Card(page, "Options de vérification")
        options.grid(row=5, column=0, sticky="ew", pady=(0, 10))
        self._entry_field(options.body, 0, 0, "drift_fragile", "Limite de dérive fragile", "0.005")
        self._entry_field(options.body, 0, 2, "drift_ductile", "Limite de dérive ductile", "0.0075")
        self._entry_field(options.body, 1, 0, "pdelta_ok", "Seuil P-Delta négligeable", "0.10")
        self._entry_field(options.body, 1, 2, "pdelta_max", "Seuil P-Delta instable", "0.20")
        self._entry_field(options.body, 2, 0, "manual_weight", "Poids sismique imposé (kN, vide = ETABS)", "")
        self._entry_field(options.body, 2, 2, "settlement", "Tassement admissible (mm)", "25")

        actions = ctk.CTkFrame(page, fg_color="transparent")
        actions.grid(row=6, column=0, sticky="ew", pady=(0, 10))
        ctk.CTkButton(actions, text="Enregistrer et recalculer", height=42, width=230,
                      fg_color=ACCENT, hover_color=BLUE,
                      font=("Segoe UI", 12, "bold"),
                      command=self._recalculate).pack(side="left")
        ctk.CTkButton(actions, text="Réinitialiser", height=42, width=130,
                      fg_color="#FFFFFF", text_color=NAVY, border_width=1,
                      border_color=BORDER, hover_color="#F0F5FA",
                      command=self._reset_config).pack(side="left", padx=(10, 0))
        return page

    def _entry_field(self, parent, row: int, column: int, key: str, label: str,
                     placeholder: str = "") -> ctk.CTkEntry:
        ctk.CTkLabel(parent, text=label, font=("Segoe UI", 10, "bold"),
                     text_color=INK, anchor="w").grid(
            row=row, column=column, sticky="w", padx=(4, 10), pady=5)
        entry = ctk.CTkEntry(parent, height=32, font=("Segoe UI", 10))
        entry.grid(row=row, column=column + 1, sticky="ew", padx=(0, 18), pady=5)
        if placeholder:
            entry.insert(0, placeholder)
        self.config_values[key] = entry
        return entry

    def _combo_field(self, parent, row: int, column: int, key: str, label: str,
                     values: list[str], default: str) -> ctk.CTkComboBox:
        ctk.CTkLabel(parent, text=label, font=("Segoe UI", 10, "bold"),
                     text_color=INK, anchor="w").grid(
            row=row, column=column, sticky="w", padx=(4, 10), pady=5)
        combo = ctk.CTkComboBox(parent, values=values, height=32,
                                font=("Segoe UI", 9.5), dropdown_font=("Segoe UI", 9.5))
        combo.grid(row=row, column=column + 1, sticky="ew", padx=(0, 18), pady=5)
        combo.set(default)
        self.config_values[key] = combo
        return combo

    def _set_entry(self, key: str, value: str) -> None:
        widget = self.config_values[key]
        widget.delete(0, "end")
        widget.insert(0, value)

    def _set_combo(self, key: str, value: str) -> None:
        self.config_values[key].set(value)

    def _get_entry(self, key: str) -> str:
        return str(self.config_values[key].get()).strip()

    def _get_combo(self, key: str) -> str:
        return str(self.config_values[key].get()).strip()

    def _reset_config(self) -> None:
        defaults = ProjectConfig()
        self._set_combo("zone", "II - Faible a moyenne (A=0,10)")
        self._set_combo("site", "S3 - Meuble")
        self._set_combo("importance", "2 - Importance moyenne (I=1,00)")
        self._set_combo("bracing", "1 - Ossature BA (R=5,5)")
        self._set_combo("occupancy", "1 - Habitation / bureaux")
        for key, value in {
            "project_name": "Projet", "joint_name": "Joint A",
            "r_override": "", "qf_override": "", "ct_override": "",
            "damping_override": "", "lambda_override": "",
            "fc28": "25", "fe": "500", "gamma_b": "1.5", "gamma_s": "1.15",
            "cover": "4", "density": "25", "q_adm": "200", "ks": "30000",
            "joint_width": "", "drift_fragile": "0.005", "drift_ductile": "0.0075",
            "pdelta_ok": "0.10", "pdelta_max": "0.20", "manual_weight": "",
            "settlement": "25",
        }.items():
            self._set_entry(key, value)
        self.config_values["plan_regular"].select()
        self.config_values["elevation_regular"].select()
        self.config_values["redundant_walls"].select()
        self._log("Configuration réinitialisée.")

    # ------------------------------------------------------------------
    # Page Vérifications
    # ------------------------------------------------------------------

    def _build_checks_page(self) -> ctk.CTkFrame:
        page = ctk.CTkFrame(self.content, fg_color=BG, corner_radius=0)
        page.grid_columnconfigure(0, weight=1)
        page.grid_rowconfigure(2, weight=1)

        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", pady=(2, 8))
        ctk.CTkLabel(header, text="Vérifications réglementaires", font=TITLE_FONT,
                     text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(header, text=(
            "Synthèse par chapitre de la note. Sélectionnez une ligne pour afficher "
            "le détail des vérifications et des éléments."
        ), font=BODY_FONT, text_color=MUTED, anchor="w").pack(anchor="w", pady=(2, 0))

        stats = ctk.CTkFrame(page, fg_color="transparent")
        stats.grid(row=1, column=0, sticky="ew", pady=(0, 8))
        for index in range(4):
            stats.grid_columnconfigure(index, weight=1)
        self.stat_status = StatCard(stats, "Statut global")
        self.stat_failed = StatCard(stats, "Vérifications en échec")
        self.stat_warnings = StatCard(stats, "Réserves")
        self.stat_checked = StatCard(stats, "Éléments contrôlés")
        for index, card in enumerate((self.stat_status, self.stat_failed,
                                      self.stat_warnings, self.stat_checked)):
            card.grid(row=0, column=index, sticky="ew", padx=(0 if index == 0 else 8, 0))

        split = ctk.CTkFrame(page, fg_color="transparent")
        split.grid(row=2, column=0, sticky="nsew")
        split.grid_columnconfigure(0, weight=2)
        split.grid_columnconfigure(1, weight=3)
        split.grid_rowconfigure(0, weight=1)

        table_card = Card(split, "Chapitres")
        table_card.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        table_card.body.grid_rowconfigure(0, weight=1)
        table_card.body.grid_columnconfigure(0, weight=1)
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Checks.Treeview", background="#FFFFFF", fieldbackground="#FFFFFF",
                        foreground=INK, rowheight=27, font=("Segoe UI", 9),
                        borderwidth=0)
        style.configure("Checks.Treeview.Heading", background="#E7EEF5",
                        foreground=NAVY, font=("Segoe UI", 9, "bold"), relief="flat")
        style.map("Checks.Treeview", background=[("selected", "#D9E7F3")],
                  foreground=[("selected", NAVY)])
        columns = ("num", "title", "status", "failed", "warn")
        self.tree = ttk.Treeview(table_card.body, columns=columns, show="headings",
                                 style="Checks.Treeview", selectmode="browse")
        for key, label, width, anchor in (
            ("num", "N°", 40, "center"),
            ("title", "Chapitre", 280, "w"),
            ("status", "Statut", 90, "center"),
            ("failed", "Échecs", 55, "center"),
            ("warn", "Réserves", 65, "center"),
        ):
            self.tree.heading(key, text=label)
            self.tree.column(key, width=width, anchor=anchor, stretch=(key == "title"))
        self.tree.grid(row=0, column=0, sticky="nsew")
        scroll = ttk.Scrollbar(table_card.body, orient="vertical", command=self.tree.yview)
        scroll.grid(row=0, column=1, sticky="ns")
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.bind("<<TreeviewSelect>>", self._on_tree_select)
        for status, color in STATUS_COLORS.items():
            self.tree.tag_configure(str(status.severity), foreground=color)

        detail_card = Card(split, "Détail")
        detail_card.grid(row=0, column=1, sticky="nsew")
        self.detail_text = ctk.CTkTextbox(detail_card.body, font=("Consolas", 10),
                                          fg_color="#F8FAFC", text_color=INK,
                                          border_width=0, wrap="word")
        self.detail_text.grid(row=0, column=0, sticky="nsew", padx=4, pady=4)
        self.detail_text.insert("1.0", "Importez un modèle puis lancez le calcul.")
        self.detail_text.configure(state="disabled")

        actions = ctk.CTkFrame(page, fg_color="transparent")
        actions.grid(row=3, column=0, sticky="ew", pady=(8, 0))
        ctk.CTkButton(actions, text="Exporter la synthèse CSV", height=34, width=210,
                      fg_color="#FFFFFF", text_color=NAVY, border_width=1,
                      border_color=BORDER, hover_color="#F0F5FA",
                      command=self._export_checks_csv).pack(side="left")
        ctk.CTkButton(actions, text="Générer la note et les plans", height=34, width=240,
                      fg_color=ACCENT, hover_color=BLUE,
                      command=lambda: self._show_page("report")).pack(side="right")
        return page

    def _populate_checks(self) -> None:
        for item in self.tree.get_children():
            self.tree.delete(item)
        if not self.assessment:
            self.stat_status.set("—")
            self.stat_failed.set("—")
            self.stat_warnings.set("—")
            self.stat_checked.set("—")
            return
        a = self.assessment
        self.stat_status.set(a.status.value, _status_color(a.status))
        self.stat_failed.set(str(a.failed_count), FAILED if a.failed_count else OK)
        self.stat_warnings.set(str(a.warning_count), WARNING if a.warning_count else OK)
        self.stat_checked.set(str(a.checked_elements))
        for chapter in a.chapters:
            self.tree.insert("", "end", iid=str(chapter.number), values=(
                f"{chapter.number:02d}", chapter.title, chapter.status.value,
                chapter.failed_count, chapter.warning_count,
            ), tags=(str(chapter.status.severity),))
        self.header_status.configure(text=a.status.value.upper(),
                                     fg_color=_status_color(a.status))
        if a.chapters:
            self.tree.selection_set(str(a.chapters[0].number))
            self._show_chapter_detail(a.chapters[0])

    def _on_tree_select(self, _event=None) -> None:
        selection = self.tree.selection()
        if not selection or not self.assessment:
            return
        number = int(selection[0])
        chapter = next((c for c in self.assessment.chapters if c.number == number), None)
        if chapter:
            self._show_chapter_detail(chapter)

    def _show_chapter_detail(self, chapter) -> None:
        lines: list[str] = []
        lines.append(f"{chapter.number}. {chapter.title}")
        lines.append("=" * 78)
        lines.append(f"Statut : {chapter.status.value}   |   "
                     f"Échecs : {chapter.failed_count}   |   Réserves : {chapter.warning_count}")
        lines.append("")
        lines.append(chapter.summary)
        lines.append("")
        if chapter.checks:
            lines.append("VÉRIFICATIONS")
            lines.append("-" * 78)
            for check in chapter.checks:
                mark = "OK" if check.status == Status.OK else (
                    "!!" if check.status == Status.FAILED else
                    "?" if check.status == Status.WARNING else "--")
                lines.append(f"[{mark}] {check.code}  {check.label}")
                lines.append(f"      Réf. : {check.reference}")
                if check.required or check.provided:
                    lines.append(f"      Requis : {check.required or '-'}   |   Fourni : {check.provided or '-'}")
                if check.message:
                    lines.append(f"      {check.message}")
                lines.append("")
        if chapter.elements:
            lines.append("ÉLÉMENTS CONTRÔLÉS")
            lines.append("-" * 78)
            for element in chapter.elements:
                lines.append(f"{element.element_type} – {element.name}  [{element.status.value}]")
                lines.append(f"  {element.geometry}")
                for check in element.checks:
                    if check.status != Status.OK:
                        lines.append(f"  - {check.code} : {check.status.value} – "
                                     f"{check.required or ''} / {check.provided or ''}")
                        if check.message:
                            lines.append(f"    {check.message}")
                for note in element.notes:
                    lines.append(f"  . {note}")
                lines.append("")
        if chapter.lines:
            lines.append("OBSERVATIONS")
            lines.append("-" * 78)
            for line in chapter.lines:
                lines.append(f"• {line}")
        self.detail_text.configure(state="normal")
        self.detail_text.delete("1.0", "end")
        self.detail_text.insert("1.0", "\n".join(lines))
        self.detail_text.configure(state="disabled")

    def _export_checks_csv(self) -> None:
        if not self.assessment:
            messagebox.showinfo(APP_NAME, "Aucune vérification à exporter.")
            return
        path = filedialog.asksaveasfilename(
            title="Exporter la synthèse",
            defaultextension=".csv",
            initialfile=f"SYNTHESE_{self.assessment.joint_name}.csv",
            filetypes=[("CSV", "*.csv")])
        if not path:
            return
        import csv
        with open(path, "w", newline="", encoding="utf-8-sig") as handle:
            writer = csv.writer(handle, delimiter=";")
            writer.writerow(["Chapitre", "Section", "Élément", "Code", "Vérification",
                             "Référence", "Requis", "Fourni", "Statut", "Message"])
            for chapter in self.assessment.chapters:
                for check in chapter.checks:
                    writer.writerow([chapter.number, chapter.title, "", check.code,
                                     check.label, check.reference, check.required,
                                     check.provided, check.status.value, check.message])
                for element in chapter.elements:
                    for check in element.checks:
                        writer.writerow([chapter.number, chapter.title, element.name,
                                         check.code, check.label, check.reference,
                                         check.required, check.provided,
                                         check.status.value, check.message])
        self._log(f"Synthèse CSV exportée : {path}")
        messagebox.showinfo(APP_NAME, f"Synthèse exportée :\n{path}")

    # ------------------------------------------------------------------
    # Page Rapport
    # ------------------------------------------------------------------

    def _build_report_page(self) -> ctk.CTkFrame:
        page = ctk.CTkScrollableFrame(self.content, fg_color=BG, corner_radius=0)
        page.grid_columnconfigure(0, weight=1)
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", pady=(2, 10))
        ctk.CTkLabel(header, text="Note de calcul et plans", font=TITLE_FONT,
                     text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(header, text=(
            "Génération de la note Word/PDF (13 chapitres, captures à insérer, "
            "commentaires CV) et des plans vectoriels DXF/SVG."
        ), font=BODY_FONT, text_color=MUTED, anchor="w").pack(anchor="w", pady=(2, 0))

        options = Card(page, "Paramètres de génération")
        options.grid(row=1, column=0, sticky="ew", pady=(0, 10))
        ctk.CTkLabel(options.body, text="Dossier de sortie", font=("Segoe UI", 10, "bold"),
                     text_color=INK, anchor="w").grid(row=0, column=0, sticky="w", padx=4, pady=5)
        self.output_entry = ctk.CTkEntry(options.body, height=32, font=("Segoe UI", 10))
        self.output_entry.grid(row=0, column=1, sticky="ew", padx=(0, 8), pady=5)
        self.output_entry.insert(0, str(Path.home() / "Documents" / "ArchilabRPA2024"))
        ctk.CTkButton(options.body, text="Parcourir…", width=110, height=32,
                      fg_color="#E7EEF5", text_color=NAVY, hover_color="#D9E5F0",
                      command=self._browse_output).grid(row=0, column=2, padx=4, pady=5)
        ctk.CTkLabel(options.body, text="Nom de base", font=("Segoe UI", 10, "bold"),
                     text_color=INK, anchor="w").grid(row=1, column=0, sticky="w", padx=4, pady=5)
        self.basename_entry = ctk.CTkEntry(options.body, height=32, font=("Segoe UI", 10))
        self.basename_entry.grid(row=1, column=1, sticky="ew", padx=(0, 8), pady=5)
        self.basename_entry.insert(0, "NOTE_CALCUL")
        self.story_combo = ctk.CTkComboBox(options.body, values=["Dernier niveau"],
                                           height=32, font=("Segoe UI", 10))
        self.story_combo.set("Dernier niveau")
        self.story_combo.grid(row=1, column=2, sticky="ew", padx=4, pady=5)
        self.plans_check = ctk.CTkCheckBox(options.body, text="Exporter les plans DXF/SVG",
                                           font=("Segoe UI", 10))
        self.plans_check.grid(row=2, column=0, columnspan=2, sticky="w", padx=4, pady=5)
        self.plans_check.select()

        actions = ctk.CTkFrame(page, fg_color="transparent")
        actions.grid(row=2, column=0, sticky="ew", pady=(0, 10))
        self.generate_button = ctk.CTkButton(
            actions, text="Générer la note Word + PDF + plans", height=44, width=310,
            fg_color=ACCENT, hover_color=BLUE, font=("Segoe UI", 12, "bold"),
            command=self._generate_reports)
        self.generate_button.pack(side="left")
        self.open_folder_button = ctk.CTkButton(
            actions, text="Ouvrir le dossier", height=44, width=160,
            fg_color="#FFFFFF", text_color=NAVY, border_width=1, border_color=BORDER,
            hover_color="#F0F5FA", command=self._open_output_folder)
        self.open_folder_button.pack(side="left", padx=(10, 0))
        self.progress = ctk.CTkProgressBar(actions, width=220, height=8)
        self.progress.pack(side="right", padx=8)
        self.progress.set(0)

        files_card = Card(page, "Fichiers générés")
        files_card.grid(row=3, column=0, sticky="ew", pady=(0, 10))
        files_card.body.grid_columnconfigure(0, weight=1)
        self.files_text = ctk.CTkTextbox(files_card.body, height=180, font=("Consolas", 10),
                                         fg_color="#F8FAFC", text_color=INK, border_width=0,
                                         wrap="word")
        self.files_text.grid(row=0, column=0, sticky="ew", padx=4, pady=4)
        self.files_text.insert("1.0", "Aucune génération effectuée.")
        self.files_text.configure(state="disabled")

        log_card = Card(page, "Journal de génération")
        log_card.grid(row=4, column=0, sticky="ew")
        log_card.body.grid_columnconfigure(0, weight=1)
        self.report_log = ctk.CTkTextbox(log_card.body, height=170, font=("Consolas", 9),
                                         fg_color="#0F1720", text_color="#D7E2EC",
                                         border_width=0, wrap="word")
        self.report_log.grid(row=0, column=0, sticky="ew", padx=4, pady=4)
        self.report_log.insert("1.0", "En attente d'une génération.\n")
        self.report_log.configure(state="disabled")
        return page

    def _browse_output(self) -> None:
        folder = filedialog.askdirectory(title="Dossier de sortie")
        if folder:
            self.output_entry.delete(0, "end")
            self.output_entry.insert(0, folder)

    def _open_output_folder(self) -> None:
        folder = Path(self.output_entry.get().strip() or ".")
        folder.mkdir(parents=True, exist_ok=True)
        try:
            os.startfile(str(folder))  # type: ignore[attr-defined]
        except Exception:
            messagebox.showinfo(APP_NAME, f"Dossier : {folder}")

    def _update_story_combo(self) -> None:
        values = ["Dernier niveau"] + [s.name for s in (self.parsed.model.stories if self.parsed else [])]
        self.story_combo.configure(values=values)
        self.story_combo.set("Dernier niveau")

    # ------------------------------------------------------------------
    # Page Journal et Aide
    # ------------------------------------------------------------------

    def _build_journal_page(self) -> ctk.CTkFrame:
        page = ctk.CTkFrame(self.content, fg_color=BG, corner_radius=0)
        page.grid_columnconfigure(0, weight=1)
        page.grid_rowconfigure(1, weight=1)
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", pady=(2, 10))
        ctk.CTkLabel(header, text="Journal d'exécution", font=TITLE_FONT,
                     text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(header, text="Import, calcul, avertissements et génération.",
                     font=BODY_FONT, text_color=MUTED, anchor="w").pack(anchor="w")
        card = Card(page)
        card.grid(row=1, column=0, sticky="nsew")
        card.body.grid_rowconfigure(0, weight=1)
        card.body.grid_columnconfigure(0, weight=1)
        self.journal_text = ctk.CTkTextbox(card.body, font=("Consolas", 9),
                                           fg_color="#0F1720", text_color="#D7E2EC",
                                           border_width=0, wrap="word")
        self.journal_text.grid(row=0, column=0, sticky="nsew", padx=4, pady=4)
        self.journal_text.insert("1.0", f"{APP_NAME} v{APP_VERSION} – prêt.\n")
        self.journal_text.configure(state="disabled")
        return page

    def _build_help_page(self) -> ctk.CTkFrame:
        page = ctk.CTkScrollableFrame(self.content, fg_color=BG, corner_radius=0)
        page.grid_columnconfigure(0, weight=1)
        header = ctk.CTkFrame(page, fg_color="transparent")
        header.grid(row=0, column=0, sticky="ew", pady=(2, 10))
        ctk.CTkLabel(header, text="Aide et cadre réglementaire", font=TITLE_FONT,
                     text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(header, text="Mode d'emploi, formats acceptés et limites du logiciel.",
                     font=BODY_FONT, text_color=MUTED, anchor="w").pack(anchor="w")

        workflow = Card(page, "Mode d'emploi")
        workflow.grid(row=1, column=0, sticky="ew", pady=(0, 10))
        steps = [
            "1. Exporter depuis ETABS : Story Data, Point Coordinates, propriétés de sections, "
            "connectivités, assignments, Modal Participating Mass Ratios, Story Shears, "
            "Diaphragm Displacements, Column/Beam/Pier Forces, Centers Of Mass And Rigidity.",
            "2. Exporter depuis SAFE : Soil Pressure et, si disponible, Punching Shear.",
            "3. Importer le fichier ETABS puis, en option, le fichier SAFE.",
            "4. Renseigner la wilaya, la zone sismique, le site, l'importance, le système de "
            "contreventement, les matériaux et les paramètres de sol.",
            "5. Lancer « Enregistrer et recalculer », contrôler les chapitres en échec.",
            "6. Générer la note Word/PDF et les plans DXF/SVG, puis insérer les captures d'écran "
            "repérées [FIGURE_CAPTURE_LOGICIEL : ...].",
        ]
        for index, step in enumerate(steps):
            ctk.CTkLabel(workflow.body, text=step, font=("Segoe UI", 10), text_color=INK,
                         anchor="w", justify="left", wraplength=940).grid(
                row=index, column=0, sticky="ew", padx=4, pady=3)

        formats = Card(page, "Formats et dépendances")
        formats.grid(row=2, column=0, sticky="ew", pady=(0, 10))
        text = (
            "• Texte tabulé : blocs séparés par une ligne vide, titre puis en-têtes.\n"
            "• Excel : un tableau par feuille (en-têtes sur la première ligne non vide).\n"
            "• Access : access_parser ou pilote ODBC Microsoft Access.\n"
            "• PDF : reportlab avec Arial (accents français).\n"
            "• Word : python-docx, champ de table des matières mis à jour par Word.\n"
            "• Plans : DXF AC1024 et SVG, calques normalisés, unité millimètre.\n\n"
            "Installation :\n"
            "    py -m pip install -r requirements.txt\n"
            "Exécution :\n"
            "    py main.py\n"
            "Exécutable Windows :\n"
            "    pyinstaller --noconsole --onefile main.py\n"
            "Sortie : dist\\main.exe (renommer en ArchilabRPA2024.exe)."
        )
        ctk.CTkLabel(formats.body, text=text, font=("Consolas", 9.5), text_color=INK,
                     anchor="w", justify="left").grid(row=0, column=0, sticky="ew", padx=4, pady=4)

        limits = Card(page, "Limites et responsabilité")
        limits.grid(row=3, column=0, sticky="ew")
        ctk.CTkLabel(limits.body, text=(
            "Le logiciel lit des résultats déjà calculés par ETABS/SAFE et applique les "
            "vérifications RPA 2024 / BAEL 91. Il ne remplace pas la modélisation, l'analyse "
            "des charges, la géotechnique ni le jugement de l'ingénieur. Les valeurs par défaut "
            "doivent être confirmées pour chaque projet. Une vérification non satisfaite est "
            "signalée sans être masquée ; le visa CTC reste subordonné à la revue du dossier."
        ), font=("Segoe UI", 10), text_color=INK, anchor="w", justify="left",
            wraplength=940).grid(row=0, column=0, sticky="ew", padx=4, pady=4)
        return page

    # ------------------------------------------------------------------
    # Navigation et thème
    # ------------------------------------------------------------------

    def _show_page(self, key: str) -> None:
        self.current_page = key
        for page_key, page in self.pages.items():
            if page_key == key:
                page.grid(row=0, column=0, sticky="nsew", padx=18, pady=14)
                page.tkraise()
            else:
                page.grid_forget()
        for nav_key, button in self.nav_buttons.items():
            if nav_key == key:
                button.configure(fg_color="#E7EEF5", text_color=NAVY,
                                 font=("Segoe UI", 11, "bold"))
            else:
                button.configure(fg_color="transparent", text_color="#33414E",
                                 font=("Segoe UI", 11))

    def _toggle_theme(self, value: str) -> None:
        ctk.set_appearance_mode("Dark" if value == "Sombre" else "Light")
        self.configure(fg_color="#101820" if value == "Sombre" else BG)
        self._log(f"Thème : {value}.")

    # ------------------------------------------------------------------
    # Import et calcul
    # ------------------------------------------------------------------

    def _do_import(self) -> None:
        etabs = self.etabs_entry.get().strip()
        safe = self.safe_entry.get().strip() or None
        if not etabs:
            messagebox.showwarning(APP_NAME, "Sélectionnez d'abord un fichier ETABS.")
            return
        if not Path(etabs).exists():
            messagebox.showerror(APP_NAME, f"Fichier introuvable :\n{etabs}")
            return
        if safe and not Path(safe).exists():
            messagebox.showerror(APP_NAME, f"Fichier SAFE introuvable :\n{safe}")
            return
        self.etabs_path = etabs
        self.safe_path = safe or ""
        joint = self.joint_entry.get().strip()
        if joint:
            self._set_entry("joint_name", joint)
        try:
            config = self._build_config()
        except ValueError as exc:
            messagebox.showerror(APP_NAME, str(exc))
            return
        self._run_background(
            lambda: self._import_and_assess(etabs, safe, config),
            self._on_import_done,
            self._on_error,
            "Import et calcul en cours…",
        )

    def _import_and_assess(self, etabs: str, safe: str | None,
                           config: ProjectConfig) -> tuple[ParsedProject, Assessment]:
        parsed = read_project(etabs, config, safe)
        assessment = assess(parsed, EngineOptions(include_dqe=True, include_plans=True))
        return parsed, assessment

    def _on_import_done(self, result: tuple[ParsedProject, Assessment]) -> None:
        parsed, assessment = result
        self.parsed = parsed
        self.assessment = assessment
        self.header_project.configure(
            text=f"{assessment.project_name}  |  {assessment.joint_name}")
        self._set_import_summary(self._describe_model(parsed))
        self.stat_import_state.set("Importé", OK)
        self.stat_stories.set(str(parsed.model.num_stories))
        self.stat_frames.set(str(len(parsed.model.frames)))
        self.stat_surfaces.set(str(len(parsed.model.areas)))
        self._populate_checks()
        self._update_story_combo()
        self._log(f"Import réussi : {parsed.model.num_stories} niveaux, "
                  f"{len(parsed.model.frames)} éléments, {len(parsed.model.areas)} surfaces.")
        if parsed.warnings:
            for warning in parsed.warnings:
                self._log(f"AVERTISSEMENT : {warning}")
        self._log(f"Calcul terminé : {assessment.status.value}, "
                  f"{assessment.failed_count} échec(s), {assessment.warning_count} réserve(s).")
        self._status(f"Calcul terminé – {assessment.status.value}.")
        self._show_page("checks")

    def _recalculate(self) -> None:
        if not self.parsed:
            messagebox.showinfo(APP_NAME, "Importez d'abord un modèle ETABS.")
            return
        try:
            config = self._build_config()
        except ValueError as exc:
            messagebox.showerror(APP_NAME, str(exc))
            return
        self._run_background(
            lambda: self._reassess(config),
            self._on_reassess_done,
            self._on_error,
            "Recalcul en cours…",
        )

    def _reassess(self, config: ProjectConfig) -> Assessment:
        assert self.parsed is not None
        self.parsed.config = config
        return assess(self.parsed, EngineOptions(include_dqe=True, include_plans=True))

    def _on_reassess_done(self, assessment: Assessment) -> None:
        self.assessment = assessment
        self._populate_checks()
        self.header_project.configure(
            text=f"{assessment.project_name}  |  {assessment.joint_name}")
        self._log(f"Recalcul terminé : {assessment.status.value}.")
        self._status(f"Recalcul terminé – {assessment.status.value}.")
        self._show_page("checks")

    def _describe_model(self, parsed: ParsedProject) -> str:
        model = parsed.model
        lines = [
            f"Fichier ETABS : {self.etabs_path}",
            f"Fichier SAFE : {self.safe_path or 'non fourni'}",
            f"Joint : {parsed.config.joint_name}",
            f"Unités déclarées : {model.units.label}",
            "",
            f"Niveaux : {model.num_stories}",
            f"Hauteur Hn : {fmt(model.total_height, 2)} m",
            f"Travées X × Y : {model.num_bays_x} × {model.num_bays_y}",
            f"Poteaux : {len(model.columns)}",
            f"Poutres : {len(model.beams)}",
            f"Voiles : {len(model.walls)}",
            f"Planchers : {len(model.slabs)}",
            "",
            "Sections détectées :",
        ]
        for section in model.sections.values():
            lines.append(f"  - {section.name} : {section.kind} {section.label}")
        if parsed.results.modal:
            lines.append("")
            lines.append(f"Résultats modaux : {len(parsed.results.modal)} modes")
        if parsed.results.base_shear("X") is not None:
            lines.append(f"V_X ETABS : {fmt(parsed.results.base_shear('X'), 1)} kN")
        if parsed.safe and parsed.safe.soil_pressures:
            lines.append(f"Pressions SAFE : {len(parsed.safe.soil_pressures)}")
        if parsed.warnings:
            lines.append("")
            lines.append(f"Avertissements : {len(parsed.warnings)}")
            for warning in parsed.warnings[:8]:
                lines.append(f"  ! {warning}")
        return "\n".join(lines)

    def _set_import_summary(self, text: str) -> None:
        self.import_summary.configure(state="normal")
        self.import_summary.delete("1.0", "end")
        self.import_summary.insert("1.0", text)
        self.import_summary.configure(state="disabled")

    def _build_config(self) -> ProjectConfig:
        config = ProjectConfig()
        config.project_name = self._get_entry("project_name") or "Projet"
        config.joint_name = self._get_entry("joint_name") or "Joint A"
        config.wilaya = self._get_combo("wilaya") if "wilaya" in self.config_values else "16 - Alger"
        config.zone_label = self._get_combo("zone")
        config.zone = next((v for label, v in SEISMIC_ZONES.items() if label == config.zone_label),
                           0.15)
        config.site_class = SITE_CLASSES.get(self._get_combo("site"), "S3")
        importance_label = self._get_combo("importance")
        code, factor, nu_a = IMPORTANCE_GROUPS.get(importance_label, ("2", 1.0, 0.55))
        config.importance_code = code
        config.importance_factor = factor
        config.nu_a = nu_a
        bracing_label = self._get_combo("bracing")
        bracing_id, r_default = BRACING_SYSTEMS.get(bracing_label, (1, 5.5))
        config.bracing_id = bracing_id
        config.behavior_factor = _float_or(self._get_entry("r_override"), r_default)
        occupancy = self._get_combo("occupancy")
        config.occupancy = occupancy
        config.psi = OCCUPANCY_CASES.get(occupancy, 0.20)
        config.plan_regular = bool(self.config_values["plan_regular"].get())
        config.elevation_regular = bool(self.config_values["elevation_regular"].get())
        config.redundant_walls = bool(self.config_values["redundant_walls"].get())
        config.qf_override = _optional_float(self._get_entry("qf_override"))
        config.ct_override = _optional_float(self._get_entry("ct_override"))
        config.damping_override = _optional_float(self._get_entry("damping_override"))
        config.lambda_override = _optional_float(self._get_entry("lambda_override"))
        config.fc28 = _float_or(self._get_entry("fc28"), 25.0)
        config.fe = _float_or(self._get_entry("fe"), 500.0)
        config.gamma_b = _float_or(self._get_entry("gamma_b"), 1.5)
        config.gamma_s = _float_or(self._get_entry("gamma_s"), 1.15)
        config.steel_cover = _float_or(self._get_entry("cover"), 4.0)
        config.concrete_density = _float_or(self._get_entry("density"), 25.0)
        config.q_adm = _float_or(self._get_entry("q_adm"), 200.0)
        config.subgrade_modulus = _float_or(self._get_entry("ks"), 30000.0)
        config.seismic_joint_provided = _optional_float(self._get_entry("joint_width"))
        config.settlement_limit_mm = _float_or(self._get_entry("settlement"), 25.0)
        config.drift_limit_fragile = _float_or(self._get_entry("drift_fragile"), 0.005)
        config.drift_limit_ductile = _float_or(self._get_entry("drift_ductile"), 0.0075)
        config.pdelta_negative = _float_or(self._get_entry("pdelta_ok"), 0.10)
        config.pdelta_unstable = _float_or(self._get_entry("pdelta_max"), 0.20)
        config.manual_seismic_weight = _optional_float(self._get_entry("manual_weight"))
        if config.fc28 <= 0 or config.fe <= 0:
            raise ValueError("fc28 et fe doivent être positifs.")
        if config.q_adm <= 0 or config.subgrade_modulus <= 0:
            raise ValueError("La portance et le module de Winkler doivent être positifs.")
        if config.behavior_factor <= 0:
            raise ValueError("Le coefficient R doit être positif.")
        return config

    # ------------------------------------------------------------------
    # Génération
    # ------------------------------------------------------------------

    def _generate_reports(self) -> None:
        if not self.assessment:
            messagebox.showinfo(APP_NAME, "Importez et calculez un modèle avant de générer la note.")
            return
        output = Path(self.output_entry.get().strip() or ".")
        basename = self.basename_entry.get().strip() or "NOTE_CALCUL"
        story = self.story_combo.get()
        story_name = None if story in ("", "Dernier niveau") else story
        include_plans = bool(self.plans_check.get())
        assessment = self.assessment
        self.generate_button.configure(state="disabled", text="Génération en cours…")
        self.progress.configure(mode="indeterminate")
        self.progress.start()
        self.report_log.configure(state="normal")
        self.report_log.delete("1.0", "end")
        self.report_log.insert("1.0", "Génération démarrée…\n")
        self.report_log.configure(state="disabled")
        self._run_background(
            lambda: generate_reports(assessment, output, basename, include_plans, story_name),
            self._on_reports_done,
            self._on_error,
            "Génération de la note et des plans…",
            restore_button=True,
        )

    def _on_reports_done(self, result: ReportResult) -> None:
        self.last_report = result
        self.generate_button.configure(state="normal", text="Générer la note Word + PDF + plans")
        self.progress.stop()
        self.progress.configure(mode="determinate")
        self.progress.set(1)
        self.files_text.configure(state="normal")
        self.files_text.delete("1.0", "end")
        lines = []
        for path in result.files:
            try:
                size = f"{path.stat().st_size / 1024:.0f} Ko"
            except OSError:
                size = "-"
            lines.append(f"{path.name}    {size}\n{path}\n")
        self.files_text.insert("1.0", "\n".join(lines))
        self.files_text.configure(state="disabled")
        for entry in result.log:
            self._append_report_log(entry)
        self._append_report_log("Génération terminée.")
        self._log(f"Note générée : {result.docx}; PDF : {result.pdf}.")
        self._status("Note et plans générés.")
        self._show_page("report")

    def _append_report_log(self, text: str) -> None:
        self.report_log.configure(state="normal")
        self.report_log.insert("end", text + "\n")
        self.report_log.see("end")
        self.report_log.configure(state="disabled")

    # ------------------------------------------------------------------
    # Threads et journal
    # ------------------------------------------------------------------

    def _run_background(self, work: Callable, on_done: Callable, on_error: Callable,
                        label: str, restore_button: bool = False) -> None:
        if self._busy:
            messagebox.showinfo(APP_NAME, "Une opération est déjà en cours.")
            return
        self._busy = True
        self._status(label)
        self.busy_bar.start()
        self.import_button.configure(state="disabled")
        if restore_button:
            self.progress.start()

        def runner() -> None:
            try:
                result = work()
            except Exception as exc:  # noqa: BLE001
                traceback.print_exc()
                self._result_queue.put((on_error, exc, True))
            else:
                self._result_queue.put((on_done, result, False))

        threading.Thread(target=runner, daemon=True).start()

    def _poll_results(self) -> None:
        try:
            while True:
                callback, payload, is_error = self._result_queue.get_nowait()
                self._busy = False
                self.busy_bar.stop()
                self.import_button.configure(state="normal")
                self.progress.stop()
                try:
                    callback(payload)
                except Exception as exc:  # noqa: BLE001
                    self._on_error(exc)
        except queue.Empty:
            pass
        self.after(120, self._poll_results)

    def _on_error(self, error: Exception) -> None:
        self.generate_button.configure(state="normal", text="Générer la note Word + PDF + plans")
        self.progress.stop()
        self._status("Erreur.")
        self._log(f"ERREUR : {error}")
        self._append_report_log(f"ERREUR : {error}")
        messagebox.showerror(APP_NAME, str(error))

    def _log(self, text: str) -> None:
        self._log_queue.put(f"[{datetime_now()}] {text}")

    def _status(self, text: str) -> None:
        self.status_label.configure(text=text)

    def _poll_log(self) -> None:
        try:
            while True:
                line = self._log_queue.get_nowait()
                self.journal_text.configure(state="normal")
                self.journal_text.insert("end", line + "\n")
                self.journal_text.see("end")
                self.journal_text.configure(state="disabled")
        except queue.Empty:
            pass
        self.after(250, self._poll_log)

    def _on_close(self) -> None:
        if self._busy:
            if not messagebox.askyesno(APP_NAME, "Une opération est en cours. Quitter quand même ?"):
                return
        self.destroy()


def datetime_now() -> str:
    from datetime import datetime
    return datetime.now().strftime("%H:%M:%S")


def _float_or(text: str, default: float) -> float:
    text = (text or "").strip().replace(",", ".")
    if not text:
        return default
    try:
        return float(text)
    except ValueError as exc:
        raise ValueError(f"Valeur numérique invalide : « {text} ».") from exc


def _optional_float(text: str) -> float | None:
    text = (text or "").strip().replace(",", ".")
    if not text:
        return None
    try:
        return float(text)
    except ValueError as exc:
        raise ValueError(f"Valeur numérique invalide : « {text} ».") from exc


def launch() -> None:
    try:
        import ctypes
        ctypes.windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass
    app = ArchilabApp()
    app.mainloop()
