# Archilab RPA2024 NoteDeCalcul Studio

[![CI](https://github.com/luxecham/Archilab-RPA2024-Studio/actions/workflows/ci.yml/badge.svg)](https://github.com/luxecham/Archilab-RPA2024-Studio/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/luxecham/Archilab-RPA2024-Studio?label=release)](https://github.com/luxecham/Archilab-RPA2024-Studio/releases/latest)
[![Site](https://img.shields.io/badge/site-t%C3%A9l%C3%A9chargement-f0a33c)](https://luxecham.github.io/Archilab-RPA2024-Studio/)

Application de calcul et de note de calcul pour l'Algérie : **RPA 2024 (DTR BC 2.48)**,
**BAEL 91 révisé 99**, **CBA 93**, **DTR BC 2.2** et **DTR BC 2.33.1**.

Version courante : **1.7.0** — voir [CHANGELOG.md](CHANGELOG.md) et
[AUDIT_LOOP.md](AUDIT_LOOP.md).

## Téléchargement

Aucune compilation n'est nécessaire : les binaires Windows sont publiés dans
[la dernière release](https://github.com/luxecham/Archilab-RPA2024-Studio/releases/latest).
Le site [luxecham.github.io/Archilab-RPA2024-Studio](https://luxecham.github.io/Archilab-RPA2024-Studio/)
présente les deux applications et leurs empreintes SHA-256.

| Application | Fichier | Prérequis |
| --- | --- | --- |
| Kotlin / Compose Desktop | `ArchilabRPA2024-Studio-1.7.0-Windows.exe` | Windows 10/11 x64 |
| Python autonome | `ArchilabRPA2024-Python-1.7.0-Windows.exe` | Windows 10/11 x64 |

Les binaires ne sont pas signés : contrôlez le SHA-256 avant exécution, puis
autorisez le lancement via SmartScreen si nécessaire.

La version Python est documentée dans [python_app/README.md](python_app/README.md).

## Lancer (depuis les sources)

```bat
set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
gradlew.bat run
```

L'écran **Import & Topologie 3D** charge l'exemple NDC intégré ou vos exports
ETABS/SAFE (`.e2k`, `.xlsx`, `.mdb`, `.accdb`, `.txt`, tableaux SAFE).

## Tests

```bat
gradlew.bat test
```

Dernière validation : **106/106 tests**, 0 échec, 0 erreur.

## Construire l'installateur Windows

```bat
gradlew.bat packageExe
```

Sortie : `build\compose\binaries\main\exe\ArchilabRPA2024-1.7.0.exe`.

## Modules experts ETABS + SAFE

Voir [EXPERT_ENGINE.md](EXPERT_ENGINE.md) pour l'ingestion croisée, l'audit de
vérifiabilité, le designer autonome de ferraillage et le composeur de note expert.

## Captures ETABS / SAFE

La note de calcul de référence contient des repères contrôlés du type
`[FIGURE_CAPTURE_LOGICIEL : ...]`. Dans l'onglet **Génération Note de Calcul CTC** :

1. descendre à la section **Captures ETABS / SAFE** ;
2. associer une image à chaque repère avec **Choisir**, importer un dossier
   d'images, ou extraire les médias d'un **DOCX de référence** (association
   provisoire dans l'ordre, à contrôler) ;
3. la capture « Vue 3D globale » est aussi utilisée sur la page de garde ;
4. exporter Word/PDF : les images sont insérées réellement, les repères non
   associés restent visibles pour contrôle.

Les images sont normalisées en PNG avant insertion ; les formats PNG, JPG, BMP
et GIF sont acceptés.

## Note

Le DOCX de référence fourni (`NDC JOINT A RPA2024.docx`) contient 113 médias et
13 sections principales. La note générée produit 13 chapitres détaillés, des
équations OMML natives, des figures techniques et 20 repères de captures. Les
captures de projet ne sont jamais inventées : elles sont associées dans le
logiciel. Voir les écarts et les prochaines cibles dans [AUDIT_LOOP.md](AUDIT_LOOP.md).
