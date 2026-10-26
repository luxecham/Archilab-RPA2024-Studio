# Archilab RPA2024 NoteDeCalcul Studio

Application Desktop Windows autonome pour la vérification et la note de calcul
d'ouvrages en béton armé selon le **RPA 2024 (DTR BC 2.48)**, le **BAEL 91 révisé 99**,
le **CBA 93** et le **DTR BC 2.33.1** pour les fondations.

L'application lit des tableaux exportés depuis **ETABS** et **SAFE**, exécute les
vérifications réglementaires, produit une note Word/PDF en 13 chapitres avec
commentaires d'ingénieur et captures à insérer, puis exporte les plans de coffrage
et de ferraillage en DXF et SVG.

> Le logiciel ne modifie jamais le modèle ETABS/SAFE. Il lit, vérifie et documente.
> Le visa CTC reste subordonné à la revue des hypothèses et des plans par un
> ingénieur habilité.

---

## 1. Prérequis

- Windows 10/11 64 bits.
- Python **3.10 ou supérieur** (testé avec Python 3.13).
- ETABS 9.7.4 / 18 / 21 et SAFE (exports de tableaux).
- Pour les fichiers `.mdb` / `.accdb` : le paquet `access_parser` (installé par
  `requirements.txt`) ou, à défaut, le pilote ODBC Microsoft Access.

## 2. Installation

Depuis le dossier `python_app` :

```bat
py -3.12 -m venv .venv
.venv\Scripts\activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Dépendances principales : `customtkinter`, `pandas`, `numpy`, `openpyxl`,
`python-docx`, `reportlab`, `access_parser`, `pyinstaller`.

## 3. Lancement

```bat
python main.py
```

L'écran d'accueil propose **Charger la démonstration** pour tester immédiatement
le logiciel sans fichier ETABS.

### Mode ligne de commande (automatisation)

```bat
python main.py --demo --demo-config --out sortie --project "Résidence El Feth"
python main.py --etabs modele.txt --safe sol.txt --out sortie --zone II --site S3
```

Paramètres utiles : `--etabs`, `--safe`, `--out`, `--basename`, `--project`,
`--joint`, `--wilaya`, `--zone`, `--site`, `--importance`, `--bracing`,
`--fc28`, `--fe`, `--qadm`, `--ks`, `--joint-width`, `--no-plans`.
Le code retour vaut `0` si le statut global n'est pas en échec, `1` si une
vérification est en échec.

## 4. Formats d'entrée

| Format | Lecture |
|---|---|
| Texte tabulé `.txt` / `.tsv` / `.csv` | Blocs séparés par une ligne vide : titre du tableau, en-têtes, lignes. Les exports `TABLE: ...` sont aussi reconnus. |
| Excel `.xlsx` / `.xlsm` | Un tableau par feuille ; la première ligne non vide devient l'en-tête. |
| Access `.mdb` / `.accdb` | `access_parser` en priorité, puis pilote ODBC Microsoft Access. |

Tableaux ETABS reconnus (alias v9.7.4, v18, v21) :

- `Story Data`, `Point Coordinates`, `Grid Definitions - Lines` ;
- `Frame Section Properties`, `Area Section Properties` ;
- `Connectivity - Frame`, `Connectivity - Area` ;
- `Frame Section Assignments`, `Area Section Assignments`, `Wall Pier Assignments` ;
- `Modal Participating Mass Ratios`, `Story Shears`, `Diaphragm CM Displacements` ;
- `Column Forces`, `Beam Forces`, `Pier Forces` ;
- `Centers Of Mass And Rigidity`, `Response Spectrum Base Reactions`.

Tableaux SAFE : `Soil Pressure`, `Punching Shear`, `Foundation Reactions` /
`Footing Design`.

Les unités sont lues dans `Program Control` (`CurrUnits`) et converties vers
**kN, m, MPa, kN/m²**. Les unités non déclarées sont supposées `kN, m, C`.

## 5. Vérifications couvertes

| Chapitre | Contenu |
|---|---|
| 1–5 | Projet, base réglementaire, géométrie, charges/combinaisons, matériaux. |
| 6 | Prédimensionnement : poteaux, poutres (b, h, h/b, L/15–L/10), planchers, voiles. |
| 7 | Régularité a1–a5 / b1–b3 et facteur de qualité QF (Tableau 3.18). |
| 8 | Période empirique, spectre, λ, CT, amortissement η. |
| 9 | Participation modale, calage MSE/MDS, ν ≤ 0,35, dérives νA.|Δ|, P-Delta. |
| 10 | Ferraillage BAEL des poteaux (flexion composée) et poutres (flexion simple, effort tranchant, étriers). |
| 11 | Voiles : lw, bw, élancement, zones critiques, ratio d'armatures. |
| 12 | Semelles auto-dimensionnées, q_max/q_min, soulèvement, poinçonnement, tassement Winkler, pressions SAFE. |
| 13 | Synthèse, statut, réserves. |

Contrôles complémentaires : poteau fort / poutre faible (ΣMRc ≥ 1,25 ΣMRb),
largeur de joint sismique (d_min = 15 + δ1 + δ2, minimum 40 mm), DQE
(béton m³, coffrage m², acier kg et ratio kg/m³).

**Aucune géométrie de projet n'est codée en dur** : toutes les boucles portent
sur les familles et éléments réellement importés.

## 6. Sorties

Pour un nom de base `NOTE_CALCUL` et un niveau sélectionné :

```
NOTE_CALCUL.docx
NOTE_CALCUL.pdf
plans/
  PLAN_COFFRAGE_<niveau>.dxf
  PLAN_FERRAILLAGE_<niveau>.dxf
  PLAN_COFFRAGE_<niveau>.svg
  PLAN_FERRAILLAGE_<niveau>.svg
  NOMENCLATURE_<joint>.csv
```

La note comprend 13 chapitres, des commentaires terminés par **CV.**, des
tableaux requis/fourni/statut et des repères exacts :

```
[FIGURE_CAPTURE_LOGICIEL : ETABS – ...]
```

Ces repères doivent être remplacés par les captures réelles avant diffusion.
Les plans DXF utilisent AC1024, l'unité millimètre et les calques
`AXES`, `COLONNES`, `POUTRES`, `VOILES`, `PLANCHERS`, `COTES`, `TEXTE`,
`CADRES`, `BARRES`, `HACHURES`, `CARTOUCHE`.

## 7. Construire l'exécutable Windows

Commande exacte demandée :

```bat
pyinstaller --noconsole --onefile main.py
```

PyInstaller produit `dist\main.exe`. Pour obtenir le nom de livraison :

```bat
copy /Y dist\main.exe dist\ArchilabRPA2024.exe
```

Le script `build_exe.bat` exécute exactement la commande ci-dessus puis réalise
la copie :

```bat
build_exe.bat
```

Résultat : **`dist\ArchilabRPA2024.exe`**.

Remarques de packaging :

- L'exécutable est autonome (`--onefile`) et sans console (`--noconsole`).
- La démonstration est générée à la demande dans `%TEMP%\ArchilabRPA2024\demo`.
- Les fichiers Access nécessitent `access_parser` (inclus par PyInstaller) ou le
  pilote ODBC Access installé sur le poste.
- Si un thème CustomTkinter manquait dans une version future de PyInstaller,
  utiliser le fichier `ArchilabRPA2024.spec` fourni :
  `pyinstaller ArchilabRPA2024.spec`.

## 8. Tests

```bat
python -m unittest discover -s tests -v
```

La suite vérifie :

- conversions d'unités ETABS (kN/m, Tonf/cm, pressions) ;
- lecture du même modèle en TXT, XLSX et MDB ;
- modèle de géométrie différente (nombre de niveaux/travées) pour garantir
  l'absence de valeurs codées en dur ;
- solveur BAEL ;
- statut global, QF, modal, MSE/MDS, ν, dérives, P-Delta, ferraillage, voiles,
  fondations, DQE, poteau fort / poutre faible, joint sismique ;
- cas négatif de portance et avertissement de limite de hauteur ;
- génération DOCX/PDF, présence des repères de capture et des commentaires CV ;
- audit DXF (`ezdxf`) : 0 erreur, calques normalisés.

## 9. Structure du code

| Fichier | Rôle |
|---|---|
| `main.py` | Entrée GUI + CLI, arguments, codes retour. |
| `ui_main.py` | Interface CustomTkinter : import, configuration, vérifications, génération, journal. |
| `parsers.py` | Tables TXT/XLSX/MDB, unités, topologie, résultats ETABS/SAFE, configuration. |
| `rpa_engine.py` | RPA 2024, BAEL 91, régularité, joints, fondations Winkler, DQE. |
| `report_generator.py` | Modèle de document commun, DOCX, PDF, annexes. |
| `vector_export.py` | DXF AC1024, SVG, nomenclature CSV. |
| `samples/make_demo_data.py` | Génération du modèle de démonstration. |
| `tests/test_python_app.py` | Tests automatisés. |
| `tools/` | Fabrication de la fixture Access (Java/Jackcess). |

## 10. Limites et responsabilité

- La zone sismique doit être saisie selon le découpage RPA 2024 en vigueur pour
  la wilaya ; elle n'est **pas** déduite automatiquement.
- Les charges, combinaisons et résultats proviennent d'ETABS/SAFE : leur
  cohérence doit être contrôlée avant calcul.
- Les dispositions non automatisées (recouvrements, ancrages, poussée au vide,
  joints de dilatation, poussée des terres) restent à vérifier sur plans.
- La portance et le module de Winkler supposent une étude géotechnique ; une
  campagne de sol reste nécessaire.
- Les quantités du DQE sont estimatives et ne remplacent pas un métré d'exécution.
- Le logiciel est une aide au calcul ; il ne se substitue ni à l'ingénieur ni au
  contrôle technique de la construction.

## 11. Livraison vérifiée (25/10/2026)

- Exécutable : `dist/ArchilabRPA2024.exe` (environ 50 Mo, one-file, sans console).
- Source : `python_app/` ; exemples : `samples/` ; sortie de démonstration :
  `build_livraison/`.
- Note de démonstration : **27 pages**, 13 chapitres + 3 annexes,
  **21 repères `[FIGURE_CAPTURE_LOGICIEL : ...]`** et **21 commentaires terminés par CV.**
- Plans DXF : audit `ezdxf` **0 erreur**, 185 et 341 entités, calques normalisés.
- Entrées validées sur le même modèle en **TXT**, **XLSX** et **MDB**.
- Tests : **19/19 OK** (`python -m unittest discover -s tests -v`).

Rappel : PyInstaller nomme l'exécutable d'après le script (`main.exe`). La commande
exacte `pyinstaller --noconsole --onefile main.py` est bien celle exécutée ;
`build_exe.bat` copie ensuite `dist\main.exe` vers `dist\ArchilabRPA2024.exe`.
