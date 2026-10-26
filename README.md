# Archilab RPA2024 NoteDeCalcul Studio — Kotlin / Compose Desktop

Application de calcul et de note de calcul pour l'Algérie : **RPA 2024 (DTR BC 2.48)**,
**BAEL 91 révisé 99**, **CBA 93**, **DTR BC 2.2** et **DTR BC 2.33.1**.

Version courante : **1.7.0** — voir [CHANGELOG.md](CHANGELOG.md) et
[AUDIT_LOOP.md](AUDIT_LOOP.md).

## Lancer

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
