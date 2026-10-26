# Moteur expert ETABS + SAFE — architecture

Quatre composants Kotlin, package `com.archilab.rpa2024.expert`, orchestrés autour
des moteurs RPA 2024 / BAEL 91 existants.

```
ETABS (.e2k/.xlsx/.mdb/.txt) ─┐
                             ├─> CrossModelIngestor ─> CrossModelSnapshot
SAFE (.xlsx/.mdb/.txt) ──────┘                              │
                                                            ├─> StructuralDataIntegrityAuditor
                                                            ├─> AutonomousReinforcementDesigner
                                                            └─> AutomatedCalculationNoteBuilder
                                                                     │
                                                       Word / PDF / captures
```

## 1. CrossModelIngestor

Fichier : `src/main/kotlin/com/archilab/rpa2024/expert/CrossModelIngestor.kt`

- `ingest(CrossModelRequest)` : importe le modèle ETABS, ses résultats et l'export SAFE.
- `link(EtabsImport, SafeFoundationModel?, ...)` : relie des modèles déjà importés.
- `CrossModelSnapshot` : superstructure, fondations, configuration sismique,
  géotechnique, sources et avertissements, dans un objet immuable et traçable.
- Aucune géométrie ni liste d'éléments n'est codée en dur.

## 2. StructuralDataIntegrityAuditor

Fichier : `.../expert/StructuralDataIntegrityAuditor.kt`

Contrôles :

- complétude géométrique et résultats ETABS ;
- équilibre global `ΣFz,base / W` sous cas de masse WI ;
- participation modale § 4.3.3 ;
- calage MSE/MDS § 4.3.5 et facteur d'échelle ;
- déplacements inter-étages, P-Delta, variation de masse/raideur b2 ;
- familles SAFE, ressorts Ks, déplacements verticaux, efforts et cohérence
  des matériaux ETABS/SAFE.

Sorties : `StructuralIntegrityReport`, `IntegrityFinding`, verdict
`VERIFIABLE / VERIFIABLE_WITH_RESERVES / NOT_VERIFIABLE` et indice 0–100.

## 3. AutonomousReinforcementDesigner

Fichier : `.../expert/AutonomousReinforcementDesigner.kt`

Produit des `ReinforcementProposal` pour :

- poteaux : flexion composée, ratio RPA de zone, barres commerciales, cadres,
  zones nodales/recouvrements, optimisation de section sur échec ;
- poutres : travée/appui, barres filantes + chapeaux, étriers et 4 vérifications
  d'effort tranchant ;
- voiles : rives, nappes d'âme, zones critiques, ductilité ;
- semelles filantes : appui/travée, section en Té, bielles, portance Winkler,
  optimisation de largeur.

Les minima RPA poteaux utilisés sont : 0,8 % (zones I/II), 0,9 % (zone III),
1,0 % (zones IV/V/VI).

## 3 ter. AutonomousRebarSelector

Fichier : `.../expert/AutonomousRebarSelector.kt`

Sélectionne les barres commerciales au moindre poids d'acier :

- poteaux : 4 coins + barres de face, espacement maximal, cadres et recouvrement ;
- poutres : filantes + chapeaux/renforts réellement distincts, étriers S/S' ;
- alternatives classées par aire d'acier ; section impossible → statut FAILED ;
- aucune section BAEL n'est recalculée : le sélecteur reçoit les As requis.

## 3 bis. AutonomousResizeEngine

Fichier : `.../expert/AutonomousResizeEngine.kt`

- détecte les familles en échec (poteaux, poutres, voiles, semelles) ;
- augmente les dimensions par pas de 5 cm ;
- met à jour le catalogue et les sections du snapshot ;
- relance les solveurs BAEL/RPA sur les efforts importés ;
- retourne une `ResizeDecision` avec section avant/après, itérations et statut ;
- signale explicitement qu'une réanalyse ETABS/SAFE reste nécessaire pour redistribuer
  les efforts.

## 4. AutomatedCalculationNoteBuilder

Fichier : `.../expert/AutomatedCalculationNoteBuilder.kt`

- Compose la note CTC de 13 chapitres avec `ReferenceNoteDeCalculComposer`.
- Ajoute **10.8 Propositions autonomes de ferraillage**.
- Ajoute **13.10 Contrôle de vérifiabilité des données**.
- Conserve les statuts VÉRIFIÉ / À VÉRIFIER / NON VÉRIFIÉ et les actions correctives.
- Injecte les captures associées via `CaptureLibrary`.
- Exporte Word et PDF.

## Exemple d'utilisation

```kotlin
val snapshot = CrossModelIngestor().ingest(
    CrossModelRequest(
        etabsModel = Path.of("JOINT_A.e2k"),
        jointName = "JOINT A",
        seismicConfig = config,
        etabsResults = Path.of("RESULTATS_ETABS.xlsx"),
        safeModel = Path.of("FONDATIONS.saf")
    )
)
val integrity = StructuralDataIntegrityAuditor().audit(snapshot)
val proposals = AutonomousReinforcementDesigner().design(snapshot)
val note = AutomatedCalculationNoteBuilder().build(snapshot, integrity = integrity, proposals = proposals)
note.writeWord(note, Path.of("NOTE_EXPERT.docx"))
note.writePdf(note, Path.of("NOTE_EXPERT.pdf"))
```

## Scénario d'intégration

`src/test/kotlin/com/archilab/rpa2024/expert/ExpertEngineIntegrationTest.kt` :

- import e2k et snapshot croisé ;
- audit d'un modèle sain ;
- participation modale absente → blocage ;
- calage MSE/MDS à 10 kN → facteur d'échelle proposé ;
- poteau surchargé à 8 000 kN → proposition d'optimisation ;
- note de 13 chapitres avec sections 10.8/13.10 et exports Word/PDF.

Dernier résultat : **106/106 tests**, 0 échec.


## Cycles 5 à 8 (v1.7.0)

- `AutonomousFoundationOptimizer` : portance Winkler, semelles filantes, BaelT/BaelR,
  armatures transversales et élargissement automatique.
- `AutonomousCadDxfEngine` : plans de coffrage, ferraillage, nomenclature DXF, DQE XLSX.
- `AutonomousCtcReportEngine` : Word/PDF 13 chapitres avec sections expertes.
- `ArchilabOneClickStudio` : orchestration complète et dashboard de livrables.
- Test d'intégration : `Cycles5To8Test`, livrables de démonstration dans `build/oneclick-demo`.
