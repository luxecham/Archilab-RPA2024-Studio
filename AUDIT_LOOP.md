# Boucle d'audit — Archilab RPA2024 NoteDeCalcul Studio

## Itération 1 — v1.1.0

**Modules audités :** `parser`, `engine/seismic`, `engine/frames`, `audit`.

| Statut | Valeur |
|---|---|
| Tests JUnit 5 | 78/78 au moment de l'itération 1 (95/95 après le moteur expert v1.4) |
| Conformité CTC du modèle de référence | 10 contrôles conformes / 15 (66,7 %) ; 5 avertissements, 0 réserve bloquante CTC |
| Verdict CTC | Avis défavorable lié à la hiérarchie poteaux-poutres du modèle de démonstration |
| Anomalies critiques corrigées | 3 |

### Anomalies critiques fermées

1. **Combinaisons `COMB1…COMB8` non classées** : les moteurs BAEL et sismiques ignoraient les efforts nommés COMB2…COMB8 et prenaient le ferraillage minimal. `COMB1` est désormais ELU, `COMB2…COMB8` ELA ; les filtres ne confondent plus COMB1 avec un cas sismique.
2. **Export texte ETABS avec `TABLE:` rejeté ou routé vers e2k** : un export tabulaire valide était refusé. Le parseur reconnaît maintenant les blocs `TABLE:` avec en-têtes tabulés et saute la ligne d'unités ; la détection e2k exige les affectations `Clé = valeur`.
3. **Participations modales mal normalisées sans `SumUX/SumUY`** : les valeurs en pourcentage étaient multipliées par 100. La normalisation porte désormais sur toutes les colonnes de participation.

### Preuves
- `RegressionAuditIteration1Test` : 6 tests (classification, design COMB2, import TABLE:, e2k préservé, deux modes de participation).
- `AuditDashboardIteration1Test` : 15 contrôles CTC exécutés, verdict calculé.

## Itération 2 — v1.2.0

**Modules audités :** `report`, `ui`, `audit/RegularityAnalyzerRpa2024`, `engine/seismic`.

| Action | Résultat |
|---|---|
| Captures logicielles | Association image par image et import par lot ; insertion PNG réelle dans Word/PDF |
| Composeur de note | L'interface utilise le composeur de référence CTC avec 13 chapitres et 20 repères |
| b2 | Une augmentation de masse/raideur > 30 % est désormais détectée, pas seulement une baisse |
| Poids sismique manuel | La valeur saisie prévaut désormais sur le poids ETABS et alimente les chapitres 5 et 9 |
| Tests | **84/84**, 0 échec, 0 erreur |

### Points bloquants restants
- Le modèle de démonstration présente des nœuds poteau-poutre non conformes (SCWB) : c'est un **résultat structurel à corriger dans le modèle**, pas un défaut de calcul.
- 20 repères de captures doivent être renseignés pour une note visuellement complète ; le DOCX de référence fourni contient 113 médias, dont logos, équations et captures de projet.
- Validation live ETABS/SAFE non automatisée dans cet environnement ; les parseurs sont couverts par fixtures TXT/XLSX/MDB/e2k.

## Itération 3 — v1.3.0

**Modules audités :** `audit/WeakColumnStrongBeamVerifier`, `report`, `ui`.

| Action | Résultat |
|---|---|
| Orientation des poteaux | La hauteur de section suit le sens du moment (X/Y) ; correction de la capacité SCWB |
| Diagnostic SCWB | 120 directions contrôlées, 8 échecs, 10 avertissements sur le modèle de démonstration |
| Images DOCX de référence | Extraction sécurisée des médias et association provisoire dans l'ordre |
| Captures | La vue 3D associée alimente aussi la couverture |
| Tests | **89/89**, 0 échec, 0 erreur |

### Prochaine cible (itération 4)
- Revoir les capacités SCWB : enrobage des poutres, contribution des dalles, moment biaxial et hiérarchie aux nœuds de rive.
- Aligner les 13 sections de la note générée sur les 13 sections du DOCX de référence (ordre et sous-titres).
- Ajouter une validation de rendu page à page (captures manquantes, tableaux coupés, pagination).

## Moteur expert v1.4.0 — ETABS + SAFE

- `CrossModelIngestor` : ingestion croisée et snapshot traçable.
- `StructuralDataIntegrityAuditor` : équilibre masse/réaction, modal, MSE/MDS,
  dérives, P-Delta, b2 et vérifiabilité SAFE.
- `AutonomousReinforcementDesigner` : barres commerciales et optimisations pour
  poteaux, poutres, voiles et semelles.
- `AutomatedCalculationNoteBuilder` : note 13 chapitres + sections 10.8 et 13.10.
- Scénario d'intégration `ExpertEngineIntegrationTest` : 6 cas, 95/95 tests.
- Détails : [EXPERT_ENGINE.md](EXPERT_ENGINE.md).


## Copilot v1.5.0 — auto-resize poteaux/poutres/voiles/semelles

- Module audité : `AutonomousReinforcementDesigner` et nouveau `AutonomousResizeEngine`.
- Anomalie traitée : les éléments en échec étaient seulement signalés, sans
  re-dimensionnement réévalué.
- Correction : sections augmentées par pas de 5 cm, catalogue et `EtabsSection`
  synchronisés, solveurs relancés ; poteaux circulaires inclus.
- Stress-tests : poteau 10 000 kN, poutre 500 kN.m, modèle sain sans modification.
- Limite explicite : les efforts importés sont conservés ; une réanalyse
  ETABS/SAFE reste nécessaire pour la redistribution.
- Tests : **98/98**, 0 échec.


## Copilot cycle 1 — v1.6.0

- Module audité : `AutonomousRebarSelector`.
- Optimisation : choix du panachage commercial au moindre poids, filantes et
  chapeaux distincts, espacements et ratios RPA contrôlés.
- Tests : 4 cas dédiés (poteau, alternatives, poutre, section impossible).
- Total : **102/102**, 0 échec.


## Copilot cycles 5 à 8 — v1.7.0

- Fondations SAFE/BaelT, CAO DXF/DQE, rapport CTC expert et orchestrateur one-click.
- Livrables persistants : `build/oneclick-demo/NOTE_CALCUL_CTC.docx`, `.pdf`, plans DXF et DQE.
- Tests : **106/106**, 0 échec.
