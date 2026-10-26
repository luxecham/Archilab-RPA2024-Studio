"""Point d'entree d'Archilab RPA2024 NoteDeCalcul Studio.

Usage graphique :
    python main.py

Usage ligne de commande (automatisation / tests) :
    python main.py --etabs modele.txt --safe sol.txt --out sortie --demo-config
    python main.py --demo --out build_demo

Construction de l'executable :
    pyinstaller --noconsole --onefile main.py
"""

from __future__ import annotations

import argparse
import sys
import tempfile
from pathlib import Path

from parsers import (
    BRACING_SYSTEMS,
    IMPORTANCE_GROUPS,
    OCCUPANCY_CASES,
    SEISMIC_ZONES,
    SITE_CLASSES,
    ProjectConfig,
    read_project,
)
from rpa_engine import EngineOptions, Status, assess
from report_generator import APP_NAME, APP_VERSION, generate_reports


def _config_from_args(args) -> ProjectConfig:
    config = ProjectConfig()
    if args.project:
        config.project_name = args.project
    if args.joint:
        config.joint_name = args.joint
    if args.wilaya:
        config.wilaya = args.wilaya
    if args.site:
        label = next((k for k in SITE_CLASSES if k.startswith(args.site.upper())), None)
        config.site_class = SITE_CLASSES[label] if label else args.site.upper()
    if args.importance:
        label = next((k for k in IMPORTANCE_GROUPS if k.startswith(args.importance.upper())), None)
        code, factor, nu_a = IMPORTANCE_GROUPS[label] if label else ("2", 1.0, 0.55)
        config.importance_code = code
        config.importance_factor = factor
        config.nu_a = nu_a
    if args.bracing:
        label = next((k for k in BRACING_SYSTEMS if k.startswith(str(args.bracing))), None)
        bracing_id, r = BRACING_SYSTEMS[label] if label else (1, 5.5)
        config.bracing_id = bracing_id
        config.behavior_factor = r
    if args.zone:
        zone = args.zone.upper().replace("ZONE", "").strip(" _-")
        label = next((k for k in SEISMIC_ZONES if k.upper().startswith(zone)), None)
        if label:
            config.zone_label = label
            config.zone = SEISMIC_ZONES[label]
    if args.occupancy:
        label = next((k for k in OCCUPANCY_CASES if k.startswith(args.occupancy)), None)
        if label:
            config.occupancy = label
            config.psi = OCCUPANCY_CASES[label]
    if args.fc28 is not None:
        config.fc28 = args.fc28
    if args.fe is not None:
        config.fe = args.fe
    if args.qadm is not None:
        config.q_adm = args.qadm
    if args.ks is not None:
        config.subgrade_modulus = args.ks
    if args.joint_width is not None:
        config.seismic_joint_provided = args.joint_width
    if args.demo_config:
        config.zone_label = "II - Faible a moyenne (A=0,10)"
        config.zone = 0.10
        config.site_class = "S3"
        config.bracing_id = 1
        config.behavior_factor = 5.5
        config.q_adm = 200.0
        config.subgrade_modulus = 30000.0
        config.seismic_joint_provided = 250.0
    return config


def _demo_paths() -> tuple[Path, Path]:
    from samples import make_demo_data
    folder = Path(tempfile.gettempdir()) / "ArchilabRPA2024" / "cli_demo"
    folder.mkdir(parents=True, exist_ok=True)
    etabs = folder / "DEMO_ETABS.txt"
    safe = folder / "DEMO_SAFE.txt"
    etabs.write_text(make_demo_data.build_etabs_text(), encoding="utf-8")
    safe.write_text(make_demo_data.build_safe_text(), encoding="utf-8")
    return etabs, safe


def run_cli(args) -> int:
    if args.demo:
        etabs, safe = _demo_paths()
    elif args.etabs:
        etabs = Path(args.etabs)
        safe = Path(args.safe) if args.safe else None
    else:
        print("Aucun fichier ETABS fourni. Utilisez --etabs ou --demo.", file=sys.stderr)
        return 2
    if not etabs.exists():
        print(f"Fichier introuvable : {etabs}", file=sys.stderr)
        return 2
    config = _config_from_args(args)
    try:
        project = read_project(etabs, config, safe)
    except Exception as exc:  # noqa: BLE001
        print(f"Erreur d'import : {exc}", file=sys.stderr)
        return 2
    try:
        assessment = assess(project, EngineOptions(include_dqe=True, include_plans=True))
    except Exception as exc:  # noqa: BLE001
        print(f"Erreur de calcul : {exc}", file=sys.stderr)
        return 3
    print(f"{APP_NAME} v{APP_VERSION}")
    print(f"Projet : {assessment.project_name} – {assessment.joint_name}")
    print(f"Modèle : {assessment.model.num_stories} niveaux, "
          f"{len(assessment.model.frames)} éléments, {len(assessment.model.areas)} surfaces")
    print(f"Statut : {assessment.status.value} | "
          f"{assessment.failed_count} échec(s) | {assessment.warning_count} réserve(s)")
    for chapter in assessment.chapters:
        print(f"  {chapter.number:02d}. {chapter.title:<46} {chapter.status.value}")
    if assessment.warnings:
        print("Avertissements :")
        for warning in assessment.warnings:
            print(f"  - {warning}")
    if args.out:
        output = Path(args.out)
        try:
            result = generate_reports(
                assessment, output, args.basename,
                include_plans=not args.no_plans,
            )
        except Exception as exc:  # noqa: BLE001
            print(f"Erreur de génération : {exc}", file=sys.stderr)
            return 4
        print("Fichiers générés :")
        for path in result.files:
            print(f"  {path}")
    return 0 if assessment.status != Status.FAILED else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="ArchilabRPA2024",
        description="Archilab RPA2024 NoteDeCalcul Studio – RPA 2024 / BAEL 91",
    )
    parser.add_argument("--version", action="version", version=f"{APP_NAME} {APP_VERSION}")
    parser.add_argument("--etabs", help="Fichier ETABS (.txt, .xlsx, .mdb)")
    parser.add_argument("--safe", help="Fichier SAFE optionnel")
    parser.add_argument("--demo", action="store_true", help="Utiliser le modèle de démonstration")
    parser.add_argument("--demo-config", action="store_true",
                        help="Appliquer la configuration de démonstration")
    parser.add_argument("--out", help="Dossier de sortie Word/PDF/plans")
    parser.add_argument("--basename", default="NOTE_CALCUL", help="Nom de base des fichiers")
    parser.add_argument("--project", help="Nom du projet")
    parser.add_argument("--joint", help="Nom du joint / bloc")
    parser.add_argument("--wilaya", help="Wilaya")
    parser.add_argument("--zone", help="Zone sismique : 0, I, II, III, IV, V ou VI")
    parser.add_argument("--site", help="Classe de site : S1, S2, S3, S4 ou SS")
    parser.add_argument("--importance", help="Groupe d'importance : 1A, 1B, 2 ou 3")
    parser.add_argument("--bracing", help="Système de contreventement : 1 à 9")
    parser.add_argument("--occupancy", help="Cas d'occupation : 1, 2a, 2b, 3, 4, 5")
    parser.add_argument("--fc28", type=float, help="Résistance béton fc28 (MPa)")
    parser.add_argument("--fe", type=float, help="Limite élastique acier (MPa)")
    parser.add_argument("--qadm", type=float, help="Portance admissible (kPa)")
    parser.add_argument("--ks", type=float, help="Module de Winkler (kN/m³)")
    parser.add_argument("--joint-width", type=float, dest="joint_width",
                        help="Largeur de joint sismique prévue (mm)")
    parser.add_argument("--no-plans", action="store_true", help="Ne pas exporter les plans")
    return parser


def main() -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:
            pass
    parser = build_parser()
    args = parser.parse_args()
    headless = bool(args.etabs or args.demo)
    if not headless:
        try:
            from ui_main import launch
        except Exception as exc:  # noqa: BLE001
            print(f"Impossible de charger l'interface : {exc}", file=sys.stderr)
            return 5
        try:
            launch()
        except Exception as exc:  # noqa: BLE001
            print(f"Erreur de l'interface : {exc}", file=sys.stderr)
            try:
                from tkinter import messagebox
                messagebox.showerror(APP_NAME, str(exc))
            except Exception:
                pass
            return 6
        return 0
    return run_cli(args)


if __name__ == "__main__":
    raise SystemExit(main())
