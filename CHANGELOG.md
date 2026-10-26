# Journal des itérations — Archilab RPA2024 NoteDeCalcul Studio

## v1.7.0 — Cycles 5 à 8 : fondations, CAO, rapport, one-click (2026-10-26)

- `AutonomousFoundationOptimizer` : semelles SAFE, Winkler, BaelR/BaelT, bielles et auto-élargissement.
- `AutonomousCadDxfEngine` : coffrage, ferraillage, nomenclature DXF et DQE acier XLSX.
- `AutonomousCtcReportEngine` : note 13 chapitres Word/PDF avec sections expertes.
- `ArchilabOneClickStudio` : ingestion -> audit -> fondations -> ferraillage -> resize -> note -> DXF,
  tableau de bord et livrables persistants dans `build/oneclick-demo`.
- **106/106 tests**, 0 échec.

## v1.6.0 — Sélecteur commercial autonome (2026-10-26)

- Ajout de `AutonomousRebarSelector` : sélection du panachage commercial au moindre
  poids d'acier, avec espacements, ratios RPA, enrobages et largeur de coffrage.
- Poteaux : disposition symétrique 4 coins + barres de face, cadres, recouvrements.
- Poutres : barres filantes + chapeaux/renforts distincts, étriers S/S' et At/s.
- Alternatives classées par aire d'acier ; section impossible signalée FAILED.
- 4 tests dédiés ; **102/102 tests**, 0 échec.

## v1.5.0 — Copilot auto-resize (2026-10-26)

- Ajout de `AutonomousResizeEngine` : redimensionnement par pas de 5 cm des poteaux,
  poutres, voiles et semelles en échec, avec réexécution des solveurs BAEL/RPA.
- Mise à jour cohérente des familles, du catalogue de sections et des sections
  EtabsSection dans le snapshot ; les gros poteaux circulaires sont également traités.
- `AutonomousReinforcementDesigner.designWithAutoResize` enchaîne resize puis
  proposition de barres commerciales.
- Tests de stress : poteau à 10 000 kN, poutre à 500 kN.m ; modèle sain inchangé.
- **98/98 tests**, 0 échec.

## v1.4.0 — Moteur expert ETABS + SAFE (2026-10-26)

- Ajout de `CrossModelIngestor` : ingestion croisée et snapshot immuable.
- Ajout de `StructuralDataIntegrityAuditor` : équilibre global, modal, MSE/MDS,
  déplacements, P-Delta, b2 et vérifiabilité SAFE.
- Ajout de `AutonomousReinforcementDesigner` : propositions commerciales pour
  poteaux, poutres, voiles et semelles, avec optimisation de section.
- Ajout de `AutomatedCalculationNoteBuilder` : note 13 chapitres enrichie des
  sections 10.8 et 13.10, captures et exports Word/PDF.
- Scénario d'intégration : import e2k, anomalies MSE/MDS et modal, poteau surchargé,
  note experte.
- **95/95 tests**, 0 échec.

## v1.3.0 — Itération 3 (2026-10-26)

### Capacités poteau fort / poutre faible
- Correction de l'orientation des sections dans `WeakColumnStrongBeamVerifier` :
  la hauteur de section est désormais la dimension parallèle au moment pour X et Y.
- Ajout de tests sur l'orientation et sur la croissance de capacité avec la hauteur.
- Diagnostic du modèle de démonstration : 120 directions de nœuds contrôlées,
  8 en échec et 10 avertissements SCWB ; le verdict CTC reste donc défavorable
  pour ce modèle, ce qui est un résultat structurel réel.

### Captures de référence
- `ReferenceDocxImageImporter` extrait les médias `word/media/` d'un DOCX de
  référence vers `build/captures/<joint>` sans traversée de chemin.
- Bouton **Extraire d'un DOCX de référence** dans l'écran des captures ; les
  images sont associées provisoirement dans l'ordre et doivent être contrôlées.
- La capture 3D alimente aussi la page de garde.

### Validation
- **89/89 tests**, 0 échec, 0 erreur.
- Les 15 contrôles CTC restent exécutés ; 10 conformes, 5 avertissements,
  0 réserve bloquante CTC, verdict global non conforme à cause des 8 nœuds SCWB.

## v1.2.0 — Itération 2 (2026-10-26)

### Captures logicielles réelles
- Ajout de `CaptureLibrary` : association d'une image PNG/JPG/BMP/GIF à chaque repère
  `[FIGURE_CAPTURE_LOGICIEL : ...]`.
- Insertion effective de l'image dans les exports Word et PDF, avec normalisation PNG.
- Écran « Captures ETABS / SAFE » dans l'onglet de génération : choix image par repère,
  remplacement, retrait, import d'un dossier complet par ordre de repères.
- Le composeur de référence est désormais utilisé par l'interface pour produire la note
  (titres CTC, chapitres et repères), avec repli documenté vers la note standard en cas
  d'indisponibilité.
- Test : 4 cas `CaptureLibraryTest` (insertion, médias DOCX/PDF, image invalide, import par lot).

### Régularité b2
- Détection des **augmentations** aussi bien que des diminutions de masse/raideur entre
  niveaux consécutifs, selon une variation relative absolue.
- Test `RegularityB2VariationTest` sur une augmentation de masse de 50 %.
- Le poids sismique saisi manuellement prévaut désormais sur le poids ETABS dans les chapitres 5 et 9.

### Comparaison au DOCX de référence
- Le DOCX fourni contient 113 médias et 13 sections principales ; la note générée
  comporte 13 chapitres détaillés et 20 repères de captures contrôlés.
- Les images de projet ne sont pas inventées : elles sont associées manuellement
  (ou par lot) dans le logiciel, conformément à la demande.

## v1.1.0 — Itération 1 (2026-10-26)

- Classification correcte des combinaisons `COMB1` (ELU) et `COMB2..COMB8` (ELA),
  utilisée par le ferraillage BAEL, l'analyse sismique et les joints.
- Import des exports texte ETABS préfixés `TABLE:` : lecture tabulaire au lieu du rejet
  comme fichier e2k, avec détection stricte des affectations e2k.
- Normalisation des participations modales lorsque les colonnes `SumUX/SumUY` sont
  absentes (fraction vs pourcentage).
- 6 tests de régression `RegressionAuditIteration1Test`.
- Tableau de bord CTC : 15/15 contrôles exécutés, 10 conformes, 5 avertissements,
  0 réserve bloquante ; verdict non conforme lié à la hiérarchie poteaux-poutres du
  modèle de démonstration (finding structurel, pas un défaut de calcul).

## Base

- v1.0.0 — Parties 1 à 10 : ingestion, moteurs RPA/BAEL, UI, Word/PDF, DXF, audit CTC,
  persistance SQLite, DQE, note de référence.
