# Archilab RPA2024 Studio 🇩🇿

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Compose Desktop](https://img.shields.io/badge/Compose_Desktop-1.7.0-2C5E8A?logo=jetpackcompose)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![JDK](https://img.shields.io/badge/JDK-21-ED8B00?logo=openjdk)](https://openjdk.org/)
[![CTC Algérie](https://img.shields.io/badge/Conformit%C3%A9-CTC_Alg%C3%A9rie-16A34A)](https://www.ctc-dz.org/)
[![Tests](https://img.shields.io/badge/Unit_Tests-77%2F77_Pass-brightgreen)](#tests--validation)

> **Studio d'Ingénierie Structurale et de Génération Automatique des Notes de Calcul CTC, Plans d'Exécution CAO (.dxf) et Devis Quantitatifs (DQE).**  
> Conçu pour automatiser 100% de la chaîne de travail de l'ingénieur en génie civil dès l'injection des modèles **CSI ETABS** et **CSI SAFE**.

---

## 🎯 Philosophie « Zero-Click Structural Engine »

L'ingénieur civil réalise uniquement sa modélisation sous **ETABS** (superstructure) et **SAFE** (infrastructure). Dès que les fichiers d'export (`.e2k`, `.mdb`, `.xlsx`, `.s2k`) sont injectés, le logiciel exécute **100% des étapes d'ingénierie en moins de 650 ms** sans aucune saisie manuelle :

```
       [ Modèle ETABS (.e2k, .mdb, .xlsx) / SAFE (.s2k) ]
                                |
                                v
               [ Archilab RPA 2024 Studio ]
  +---------------------------------------------------------+
  |  1. Auto-découverte topologique (Niveaux, Portées, Axes)|
  |  2. Diagnostic RPA 2024 (Modes, Base Shear, P-Delta)    |
  |  3. Ferraillage Génératif (Bresler, Voiles, Nœuds)      |
  |  4. Analyse Non-Linéaire Pushover N2 & Rotules ASCE 41  |
  |  5. Planchers Nervurés Continues (Koka / Caquot)        |
  |  6. Fondations sur Sol Élastique (Winkler & Pasternak)  |
  |  7. Poinçonnement Dalles (Eurocode 2 / Goujons PSB)     |
  |  8. Moments de Plaque Wood-Armer (Dalles & Radiers)     |
  |  9. Poutres Palières & Nœuds d'Escaliers                |
  +---------------------------------------------------------+
                                |
     +--------------------------+--------------------------+
     |                          |                          |
     v                          v                          v
[ Note Word (.docx) ]  [ Synthèse PDF CTC ]   [ Plans CAO (.dxf) ]
  13 Chapitres CTC       5 Planches A4         7 Folios Cotés
  Équations OMML         Visa Homologation     Bordereau BAP
     |                          |
     v                          v
[ Devis DQE (.xlsx) ]  [ Base SQLite (.db) ]
  Cubatures & Aciers     Persistance Globale
```

---

## 🏛️ Réglementations & Normes Intégrées

Le solveur mathématique et les règles de vérification intègrent rigoureusement les prescriptions techniques suivantes :

- **DTR BC 2.48 (RPA 2024) :** Règles Parasismiques Algériennes (zones sismiques 0, I, IIa, IIb, III, IV ; spectres élastiques et de calcul, $V_{MSE}$ vs $V_{MDS}$, $\nu \le 0.35$, dérives $\Delta_k \le \bar{\Delta}$, $\theta_k \le 0.10$, joints sismiques, dispositions constructives des voiles et nœuds).
- **CBA 93 & BAEL 91 révisé 99 :** Règles de conception et de calcul des structures en béton armé (flexion simple, flexion composée, cisaillement, contraintes limites de compression du béton et de traction des aciers).
- **DTR BC 2.33.1 :** Règles de calcul des fondations superficielles (semelles filantes, isolées et radiers généraux sous sollicitations sismiques, excentrement, soulèvement et tassement admissible).
- **DTR BC 2.2 :** Charges permanentes et d'exploitation des bâtiments.
- **Eurocode 2 (NF EN 1992-1-1 § 6.4) :** Vérification au poinçonnement des dalles et radiers au nu et à $2d$, calcul des armatures de poinçonnement (goujons PSB/HDB).
- **Eurocode 8-3 / ASCE 41-13 / FEMA 356 :** Analyse non-linéaire en poussée progressive (Pushover N2) et distribution des rotules plastiques.
- **Wood (1968) & Armer (1968) :** Dimensionnement des armatures orthogonales sous champ de moments de plaque $(M_x, M_y, M_{xy})$.

---

## 📦 Architecture Modulaire des Packages (Kotlin 21)

Le projet est découpé en modules hautement cohésifs :

```
src/main/kotlin/com/archilab/rpa2024/
├── audit/              # Audit de conformité CTC & boucle d'auto-redimensionnement
│   └── CtcComplianceAuditorAndAutoFixer.kt
├── bridge/             # Connecteurs et ponts d'échange CSI ETABS / SAFE
│   ├── AutomatedComRunner.kt
│   ├── EtabsModelInjector.kt
│   └── SafeFoundationBridge.kt
├── cad/                # Moteur d'exportation CAO AutoCAD (.dxf)
│   ├── AutonomousCadDxfEngine.kt
│   ├── BarBendingScheduleCalculator.kt
│   └── DxfCoreEngine.kt
├── engine/             # Moteurs de calcul analytique et dimensionnement
│   ├── ArchilabOneClickStudio.kt       # Super-Orchestrateur Zéro-Clic
│   ├── era/                            # Vulnérabilité ERA, Pushover N2 & Rotules
│   │   ├── PlasticHingeDistributionEngine.kt
│   │   └── StructuralSeismicVulnerabilityAndPushoverEngine.kt
│   ├── foundations/                    # Fondations SAFE, Winkler & Pasternak
│   │   ├── AutonomousFoundationOptimizer.kt
│   │   ├── RaftFoundationDesigner.kt
│   │   ├── SafeFoundationExtractor.kt
│   │   ├── SoilBearingStressVerifier.kt
│   │   ├── SoilStructureInteractionEngine.kt
│   │   └── StripFootingDesignOrchestrator.kt
│   ├── frames/                         # Poteaux et Poutres (BaelR & Bresler)
│   │   ├── BaelRectangularSolver.kt
│   │   ├── BeamDesignEngine.kt
│   │   └── ColumnDesignEngine.kt
│   ├── geometry/                       # Auto-découverte et pré-dimensionnement
│   │   └── GeometryAndPreSizingEngine.kt
│   ├── secondary/                      # Éléments secondaires & Nœuds d'escalier
│   │   ├── LandingBeamAndStaircaseNodeDesigner.kt
│   │   ├── ParapetWallDesigner.kt
│   │   └── StaircaseDesigner.kt
│   ├── seismic/                        # Spectre & Dynamique RPA 2024
│   │   └── Rpa2024SeismicEngine.kt
│   ├── slabs/                          # Planchers Koka, Wood-Armer & Poinçonnement
│   │   ├── AdvancedPunchingShearEngine.kt
│   │   ├── ContinuousRibbedSlabSolver.kt
│   │   ├── MultiSpanRibbedSlabEngine.kt
│   │   ├── TBeamDeflectionCalculator.kt
│   │   └── WoodArmerBendingSolver.kt
│   └── walls/                          # Voiles de contreventement RPA § 7.7
│       └── WallCriticalZoneAndDuctilityDesigner.kt
├── graphics/           # Rendu vectoriel SVG et graphiques scientifiques
├── model/              # Modèle de données topologique et catalogue de sections
├── normative/          # Base de données et formules normatives DTR BC 2.48
├── parser/             # Parseurs universels ETABS (.e2k, .mdb, .xlsx) et SAFE
├── persistence/        # Persistance SQLite et Devis Quantitatif DQE Excel
├── report/             # Générateur Word OMML (13 chapitres) et PDF CTC
└── ui/                 # Interface graphique Desktop Jetpack Compose
```

---

## 🚀 Fonctionnalités Majeures

### 1. Diagnostic RPA 2024 Automatisé
- **Participation modale :** Cumul des masses modales $\ge 90\%$ dans les deux directions horizontales.
- **Calage de l'effort tranchant :** Comparaison automatique entre l'effort tranchant à la base dynamique $V_{\text{MDS}}$ et statique équivalent $V_{\text{MSE}}$ avec majoration automatique si $V_{\text{MDS}} < 0.80 V_{\text{MSE}}$.
- **Effort normal réduit :** Contrôle systématique de $\nu = \frac{N_d}{B_c \cdot f_{c28}} \le 0.35$ sur chaque poteau avec boucle d'auto-élargissement par pas de 5 cm.
- **Déplacements inter-étages & P-Delta :** Calcul des déplacements relatifs avec coefficient de comportement $R$ et vérification de la stabilité au deuxième ordre ($\theta_k \le 0.10$).

### 2. Moteur Génératif de Ferraillage (Style CYPECAD / Robot Expert)
- **Flexion bi-axiale déviée :** Solveur analytique basé sur le contour d'interaction de Bresler :
  $$\frac{1}{N} = \frac{1}{N_x} + \frac{1}{N_y} - \frac{1}{N_0}$$
- **Sélection commerciale d'armatures :** Optimisation automatique des diamètres ($\phi 12, \phi 14, \phi 16, \phi 20$), espacements réglementaires ($s \le \min(10\phi_l, 25\text{ cm})$), et panachage régulier ou bimétrique.
- **Voiles de contreventement :** Qualification géométrique ($l_w / b_w \ge 4$), éléments de rive confinés ($l_c = \max(20\text{ cm}, 1.5 b_w)$) et ductilité locale $\nu_d \le 0.40$.
- **Planchers à corps creux multi-travées :** Résolution des moments par Clapeyron et méthode forfaitaire, vérification de la flèche différée nuisible ($\Delta f_t \le L/500$), et calcul des épures d'arrêt de barres (arrêts à $0.10 L_n$, chapeaux à $0.20-0.25 L_n$).

### 3. Poinçonnement & Moments de Plaque Wood-Armer
- **Poinçonnement Eurocode 2 :** Détermination des périmètres critiques $u_0$ et $u_1$, résistance du béton $v_{Rd,c}$, contrainte maximale admissible $v_{Rd,\max}$, et dimensionnement automatique de rails de goujons PSB/HDB.
- **Transformation de Wood-Armer :** Passage rigoureux des champs de moments éléments finis $(M_x, M_y, M_{xy})$ aux moments de flexion orthogonaux pour les armatures en nappe inférieure et supérieure.

### 4. Interaction Sol-Structure (ISS) & Fondations
- **Modèle de sol biparamétrique de Pasternak :** Raideur de Winkler $k_s$ et paramètre de cisaillement $G_p$.
- **Impédances dynamiques de Gazetas :** Allongement de la période fondamentale $\tilde{T}$ et amortissement radiatif $\xi_{\text{rad}}$.
- **Stabilité au soulèvement :** Respect des exigences CTC : facteur au renversement $F_s \ge 1.50$ et zone comprimée $\ge 75\%$ sous séisme.

---

## 📄 Les 5 Livrables Générés en 1 Clic

| Document | Format | Description |
| :--- | :--- | :--- |
| **Note de Calcul CTC** | `.docx` | Document Word complet en 13 chapitres avec équations OMML officielles, tableaux de descente de charges, ferraillages et justifications normatives. |
| **Synthèse Exécutive** | `.pdf` | Rapport exécutif de 5 pages A4 avec vue 3D filaire, spectres RPA 2024, courbes de capacité et cadre officiel de visa CTC. |
| **Dossier de Plans CAO** | `.dxf` | 7 folios AutoCAD aux échelles normalisées (Coffrage d'étage, Fondations, Poteaux 1/20, Poutres 1/20, Voiles, Semelles, Nomenclature BAP). |
| **Devis Quantitatif DQE** | `.xlsx` | Classeur Excel reprenant les cubatures de béton, tonnages d'acier par diamètre, ratios ($kg/m^3$) et montants estimatifs en DZD. |
| **Base Relationnelle** | `.db` | Base de données SQLite persistante pour archivage et réutilisation multi-projets. |

---

## 🧪 Tests & Validation

La suite de tests unitaires automatisée valide l'ensemble des modules d'ingénierie :

```bash
gradle test
```

Résultats actuels de la suite de tests :
```
BUILD SUCCESSFUL
Total Unit Tests: 77
Passed: 77 (100%)
Failed: 0
Skipped: 0
Execution time: ~21s
```

---

## 💻 Démarrage Rapide

### Prérequis
- **JDK 21** ou supérieur (ex: Microsoft Build of OpenJDK 21).
- **Gradle 8.10+** (inclus ou via wrapper).

### Compilation & Lancement
```bash
# Cloner le dépôt
git clone https://github.com/luxecham/Archilab-RPA2024-Studio.git
cd Archilab-RPA2024-Studio

# Exécuter l'application Compose Desktop
gradle run

# Construire l'installateur Windows autonome (.exe / .msi)
gradle packageExe
```

---

## 👤 Auteur & Maintenance
- **Développeur & Concepteur Principal :** luxecham
- **Licence :** Propriétaire / Usage Professionnel Bureaux d'Études BTP Algérie.
