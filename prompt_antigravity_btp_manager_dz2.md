# Guide Complet & Prompts Antigravity — Recréer BTP Manager Pro (Algérie)

Ce document contient le **Prompt Unique Ultra-Détaillé (Spécification Seconde par Seconde de `01:10` à `07:28`)** ainsi que le découpage étape par étape pour régénérer dans **Antigravity** l'exacte mécanique de **BTP Manager Pro** (Dinateck).

---

## 1. Analyse Chronologique Exacte de la Vidéo après `01:10` (`01:10` → `07:28`)

Dans la vidéo, entre `00:00` et `01:10`, le présentateur montre uniquement les menus de la barre latérale. **À partir de `01:10`, il entre dans le cœur du logiciel et déroule un cas réel complet de bout en bout** :

* **`01:10` – `02:00` | Création & Paramétrage du Marché (`R+5`)** :
  * Ouverture de la fiche d'un marché intitulé **projet R+5**.
  * Saisie des informations administratives : **Numéro du marché**, **Type de contrat** (`Marché`, `Convention`, ou `Contrat`), **Montant initial / final du marché**, **Taux de TVA à 9 %** (spécifique au logement en Algérie, ou 19 %), **Retenue de garantie (5 %)**, dates et délais.
* **`02:00` – `02:30` | Structuration du Cahier des Charges (DQE : Lots → Sections → Articles)** :
  * À l'intérieur du marché, création des **Lots** (ex. *Gros œuvres*, *Corps d'état secondaires*).
  * À l'intérieur de chaque Lot, création des **Sections** (chapitres du bordereau).
  * À l'intérieur de chaque Section, liste des **Articles du cahier des charges** avec : **Numéro d'article** (ex. `4.2`), **Désignation**, **Unité** (`m³`, `m²`, `ml`, `U`, `Kg`), **Quantité prévue au cahier des charges**, **Prix Unitaire HT**, et possibilité d'ajouter ou modifier des articles à la volée.
* **`02:30` – `03:12` | Le Module Chantier (Liaison Marché ↔ Terrain)** :
  * Chaque Lot du marché devient automatiquement un **Chantier** dans l'onglet Chantiers.
  * La carte du chantier affiche : le **Chef de Chantier** responsable, la **Wilaya**, le **Taux d'avancement (%)** global.
  * En entrant dans le chantier, on retrouve des onglets dédiés :
    1. **Aperçu & Avancement des articles** (exactement les articles hérités du Lot).
    2. **Équipe & Ouvriers** (les personnes qui travaillent sur ce chantier).
    3. **Achats & Dépenses du chantier** (suivi financier propre à ce chantier).
    4. **Avenants, Attachements & Situations** rattachés à ce chantier.
* **`03:12` – `04:04` | Saisie d'un Attachement Normal & Calculateur de Métré (Cas n°1 : Sans dépassement)** :
  * Le Chef de Chantier (depuis son accès mobile/tablette) ou l'Administrateur crée un **Attachement** (ex. *Attachement N° 2*).
  * Il crée ou sélectionne une **Zone / Localisation** sur le chantier (ex. *Bloc A*, *Étage 1*).
  * Il sélectionne l'**Article `4.2`** du cahier des charges :
    * Le système affiche immédiatement : **Quantité prévue = `3`**, **Quantité déjà réalisée = `0`**, **Reste à réaliser = `3`**.
  * Il choisit le mode de saisie :
    * Soit **Mode Métré détaillé** (`Nombre × Longueur × Largeur × Hauteur`) qui calcule tout seul la quantité.
    * Soit **Quantité directe** + **Observation**.
  * Il saisit une quantité exécutée de **`2`** (sur `3` demandés) et clique sur Ajouter : le logiciel affiche immédiatement le **taux d'avancement de l'article (`66,67 %`)** en vert.
* **`04:04` – `04:48` | Simulation Volontaire d'un Dépassement de Quantité (Cas n°2 : `622` au lieu de `6`)** :
  * Le présentateur sélectionne un deuxième article dont la **quantité prévue au cahier des charges est de `6`**.
  * Il saisit volontairement une quantité énorme de **`622`** (`622 × 1 × 1`) pour montrer la sécurité du logiciel.
  * Dès qu'il valide la ligne, le logiciel **bloque / alerte en rouge vif** : **« Dépassement de quantité ! »** (622 saisis pour 6 prévus, soit +616 en dépassement) et empêche de valider la situation tant que ce dépassement n'est pas régularisé par un **Avenant**.
* **`04:48` – `05:55` | Création, Calcul Financier & Validation de l'Avenant (Déblocage Automatique)** :
  * L'utilisateur va dans l'onglet **Avenants** et crée un **Avenant** sur ce marché.
  * Il sélectionne l'article en dépassement (ou ajoute un nouvel article hors bordereau) et porte sa quantité contractuelle à **`622`** (avec un prix unitaire ex. `4 000 DA`).
  * Le logiciel affiche instantanément le **bilan financier de l'avenant** :
    * *Ancien montant du marché* vs *Nouveau montant du marché après avenant*.
    * *Montant de l'avenant (+ / -)* et *Pourcentage de dépassement / incidence financière (%)*.
  * **Le moment clé (`05:25` – `05:55`)** : Tant que l'avenant est en statut « Brouillon / En attente », l'attachement reste bloqué en dépassement. Dès que l'utilisateur clique sur le bouton **« Valider l'Avenant »**, la quantité du cahier des charges passe automatiquement à **`622`**, et lorsqu'il retourne dans l'**Attachement**, l'alerte de dépassement a disparu toute seule !
* **`05:55` – `06:48` | Validation de l'Attachement & Génération Automatique de la Situation N° 2** :
  * L'utilisateur clique sur **« Valider l'Attachement »** (possibilité d'imprimer la feuille d'attachement immédiatement).
  * Il va ensuite dans l'onglet **Situations**, clique sur **« Nouvelle Situation »** :
    * Le logiciel reconnaît automatiquement toutes les informations du marché.
    * Il coche / sélectionne l'**Attachement N° 2** (qui est validé) : le système aspire automatiquement toutes les quantités et tous les montants sans rien ressaisir.
* **`06:48` – `07:28` | Calculs de Pied de Situation & Impression des PDF Officiels (Situation + Attachement)** :
  * Affichage du récapitulatif financier complet de la situation : Montant brut, **Retenue de garantie (5 %)**, **TVA (9 %)**, Montant Net à Payer TTC.
  * Génération et prévisualisation des **2 documents officiels prêts à imprimer** (au format réglementaire algérien pour éviter tout rejet administratif) :
    1. **La Feuille d'Attachement N° 2** (avec les métrés détaillés `L × l × H` et les quantités).
    2. **La Situation de Travaux N° 2** (format officiel avec colonnes Précédent / Mois / Cumulé, déductions, TVA 9 %, retenue de garantie 5 %, et cadres de signatures Entreprise / BET / Maître d'Ouvrage).

---

## 2. LE PROMPT COMPLET "APRES 01:10" (À copier-coller directement dans Antigravity)

Si tu veux qu'Antigravity te génère **exactement tout ce workflow de `01:10` à `07:28` d'un seul bloc**, copie et colle ce prompt complet :

```markdown
# RÔLE & OBJECTIF
Tu es un Architecte Logiciel Senior et Développeur Full-Stack Expert en ERP BTP pour l'Algérie.
Construis l'application web complète **"BTP Manager Pro DZ"** (React + TypeScript + Tailwind CSS + Lucide Icons + Zustand avec persistance locale).
L'application doit reproduire **à l'identique et sans aucun placeholder** le workflow métier exact démontré dans la vidéo de démonstration (de 01:10 à 07:28) : de la création du Marché R+5 jusqu'à l'impression officielle de la Feuille d'Attachement N°2 et de la Situation de Travaux N°2, en passant par le blocage sur dépassement de quantité (622 au lieu de 6) et sa régularisation automatique par validation d'Avenant.

---

# SPÉCIFICATIONS FONCTIONNELLES EXACTES (WORKFLOW DE 01:10 À 07:28)

## MODULE 1 : CRÉATION DU MARCHÉ & STRUCTURE HIÉRARCHIQUE DU CAHIER DES CHARGES (01:10 -> 02:30)
1. **Formulaire & Fiche Marché** :
   - Permet de créer et éditer un marché avec :
     - **Nature du contrat** : Sélecteur (`Marché Public`, `Convention`, `Contrat / Bon de commande`).
     - **Numéro du Marché** (ex: `N° 2026/04/OPGI`), **Intitulé du projet** (ex: `Réalisation de 40 Logements R+5`), **Maître d'Ouvrage** (Client), **Bureau d'Études (BET)**, **Entreprise de réalisation**, **Wilaya** (liste des 58 wilayas d'Algérie, par défaut `22 - Sidi Bel Abbès`).
     - **Paramètres Financiers Algériens** :
       - **TVA (%)** : Sélecteur `9%` (Logement / Hydr.) ou `19%` (Régime général) — par défaut **`9%`**.
       - **Retenue de Garantie (%)** : Par défaut **`5%`** (modulable à `10%` ou `0%`).
       - **Avance Forfaitaire (%)** et **Avance sur Approvisionnement (%)**.
     - **Synthèse Financière en temps réel sur la fiche** : Affiche côte à côte le *Montant Initial du Marché (HT & TTC)*, le *Montant des Avenants Validés*, et le *Montant Final Actuel du Marché (HT & TTC)*.

2. **Arborescence du Cahier des Charges / DQE (Lots -> Sections -> Articles)** :
   - Dans la fiche du Marché, intègre un éditeur hiérarchique complet à 3 niveaux :
     - **Niveau 1 : Les Lots** (ex: `Lot 01 : Gros Œuvres & Terrassement`, `Lot 02 : Corps d'État Secondaires`).
     - **Niveau 2 : Les Sections (Chapitres)** à l'intérieur de chaque Lot (ex: `Section 1 : Infrastructure & Fondations`, `Section 2 : Assainissement & Regards`, `Section 3 : Superstructure R+5`).
     - **Niveau 3 : Les Articles du Cahier des Charges** à l'intérieur de chaque Section, avec :
       - `N° d'Article` (ex: `4.2`, `4.5`, etc.)
       - `Désignation complète de l'ouvrage`
       - `Unité` (`m³`, `m²`, `ml`, `U`, `Kg`, `T`, `Fft`)
       - `Quantité Initiale Cahier des Charges`
       - `Quantité Avenant (+ / -)` (calculée automatiquement depuis les avenants validés)
       - `Quantité Contractuelle Actuelle` (`Initiale + Avenant`)
       - `Prix Unitaire HT (en DA)`
       - `Montant Total HT (en DA)`
   - Boutons permettant d'**Ajouter / Modifier / Supprimer** à la volée des Lots, des Sections et des Articles.

---

## MODULE 2 : GESTION DES CHANTIERS & ESPACE CHEF DE CHANTIER (02:30 -> 03:12)
1. **Synchronisation Automatique Lot <-> Chantier** :
   - Chaque Lot d'un marché génère ou est rattaché à un **Chantier** actif.
   - La vue liste des Chantiers affiche des cartes avec : Nom du chantier, Marché parent, **Chef de Chantier assigné**, **Wilaya**, et une **Barre de progression du Taux d'Avancement global (%)**.
2. **Vue Détaillée d'un Chantier (5 Onglets internes)** :
   - **Onglet 1 — Articles & Avancement** : Liste tous les articles du Lot avec Quantité prévue, Quantité réalisée cumulée, Quantité restante, et Barre de progression `%` par article.
   - **Onglet 2 — Équipe du Chantier** : Liste des ouvriers et techniciens affectés au chantier (Nom, Fonction : Chef de chantier, Maçon, Coffreur, Ferrailleur, Téléphone, Salaire journalier, Pointage).
   - **Onglet 3 — Achats & Dépenses du Chantier** : Saisie des dépenses (Ciment, Rond à béton, Sable, Carburant, Location engins) imputées directement à ce chantier pour calculer le coût de revient réel.
   - **Onglet 4 — Attachements & Métrés du Chantier** : Accès direct à la saisie des attachements par le Chef de Chantier ou l'Administrateur.
   - **Onglet 5 — Avenants & Situations du Chantier**.

---

## MODULE 3 : SAISIE DES ATTACHEMENTS, FEUILLE DE MÉTRÉ & DÉTECTION DE DÉPASSEMENT (03:12 -> 04:48)
C'est la fonctionnalité centrale démontrée dans la vidéo. Crée une interface ultra-fluide pour saisir un **Attachement (ex: Attachement N° 1, Attachement N° 2)** :

1. **En-tête de l'Attachement** :
   - Numéro de l'attachement (ex: `Attachement N° 02`), Date, Période (Du ... Au ...), Statut (`Brouillon` | `Validé`).
   - **Gestion des Zones / Localisations** : Permet de créer une localisation précise sur le chantier (ex: `Zone 1 : Bloc A - RDC`, `Zone 2 : Regards & Réseaux`) pour regrouper les lignes de métré.

2. **Sélection d'un Article & Bandeau de Contrôle en Temps Réel** :
   - Lorsqu'on choisit un article du cahier des charges (ex: **Article `4.2`**) dans la liste déroulante :
   - Le système affiche **immédiatement** un bandeau d'information avec 4 compteurs :
     1. **Qté Cahier des Charges (Contractuelle)** : ex. `3,00`
     2. **Qté Saisie Précédemment (Cumul antérieur)** : ex. `0,00`
     3. **Qté Restante Disponible** : ex. `3,00`
     4. **Taux d'Avancement en direct (%)** : Se met à jour instantanément pendant la frappe (ex: si on tape `2` sur `3` prévus -> affiche `66,67 %`).

3. **Double Mode de Saisie des Quantités (Bascule Métré / Direct)** :
   - **Mode A — Feuille de Métré Détaillé (Avant-métré `N × L × l × H`)** :
     - Champs : `Désignation de la ligne`, `Nombre / Semblables (N)`, `Longueur (L)`, `Largeur (l)`, `Hauteur / Épaisseur (H)`, et `Observation`.
     - Le produit `N × L × l × H` calcule automatiquement la quantité totale de la ligne (si une dimension est laissée vide ou à 1, elle est neutre dans la multiplication).
   - **Mode B — Quantité Directe (Forfait / Unité)** :
     - Saisie directe du chiffre de la quantité réalisée + champ `Observation`.

4. **CONTRÔLE CRITIQUE : Alerte & Blocage en cas de Dépassement de Quantité (Le cas `622` vs `6` à 04:05)** :
   - Si pour un article dont la quantité contractuelle est de **`6`**, l'utilisateur saisit **`622`** (soit `Cumul + Qté Saisie > Qté Contractuelle`) :
     - L'article passe immédiatement en état **"DÉPASSEMENT CONTRACTUEL"** (bordure et badge rouge vif clignotant : `⚠️ Dépassement : 622,00 saisi pour 6,00 prévu au marché (+616,00 hors marché)`).
     - Le bouton **"Valider l'Attachement"** est verrouillé tant qu'il existe un dépassement non couvert par un Avenant validé, avec un bouton d'action directe : **"Créer / Régulariser via un Avenant"**.

---

## MODULE 4 : GESTION DES AVENANTS & DÉBLOCAGE AUTOMATIQUE DE L'ATTACHEMENT (04:48 -> 05:55)
Implémente le module **Avenants** avec la synchronisation exacte montrée entre `04:48` et `05:55` :

1. **Création d'un Avenant (ex: Avenant N° 01)** :
   - Permet de modifier à la hausse ou à la baisse la quantité d'articles existants du marché (ex: prendre l'article qui avait une quantité de `6` et passer sa nouvelle quantité à **`622`**, au prix unitaire de `4 000,00 DA`), OU d'ajouter des **Travaux Supplémentaires / Nouveaux Articles hors bordereau**.
2. **Tableau de Bord Financier de l'Avenant (Temps réel)** :
   - Affiche clairement :
     - **Montant Initial du Marché (HT & TTC)**
     - **Montant de l'Avenant (+ / - en DA HT & TTC)**
     - **Taux de variation / Dépassement global du marché (%)**
     - **Nouveau Montant Final du Marché (HT & TTC)**.
3. **Mécanisme de Validation Synchronisée (`Brouillon` -> `Validé`)** :
   - Tant que l'avenant est à l'état `Brouillon / En attente`, le cahier des charges garde l'ancienne quantité (`6`) et l'attachement affiche toujours l'erreur de dépassement.
   - **Dès que l'utilisateur clique sur le bouton « Valider l'Avenant »** :
     1. Le statut de l'avenant passe à **`Validé`** (badge vert).
     2. La quantité contractuelle de l'article dans le Cahier des Charges (DQE) passe automatiquement de `6` à **`622`**.
     3. Dans l'**Attachement N° 2**, l'alerte rouge de dépassement sur la quantité `622` **disparaît automatiquement**, le taux d'avancement passe à `100 %` (Valide), et le bouton **« Valider l'Attachement »** est immédiatement débloqué !

---

## MODULE 5 : GÉNÉRATION AUTOMATIQUE DES SITUATIONS & IMPRESSION OFFICIELLE (05:55 -> 07:28)
1. **Validation de l'Attachement & Création de la Situation en 1 Clic** :
   - Une fois l'Attachement N° 2 passé au statut **`Validé`**, l'utilisateur ouvre l'onglet **Situations de Travaux** et clique sur **« Nouvelle Situation »**.
   - Le logiciel pré-remplit automatiquement toutes les informations du Marché (Client, Entreprise, TVA 9%, Retenue de garantie 5%).
   - L'utilisateur sélectionne dans une liste l'**Attachement N° 2 (Validé)** à rattacher à cette situation :
     - **Zéro ressaisie** : Le système récupère automatiquement tous les articles, les quantités précédentes (Situation N° 1), les quantités de l'Attachement N° 2, et calcule les quantités cumulées et les montants HT.

2. **Calculs Financiers Officiels en Pied de Situation** :
   - Affiche le décompte financier exact :
     - `Total Travaux Réalisés Cumulés (HT)`
     - `Moins : Montant des Situations Précédentes (HT)`
     - `= Montant Brut de la Situation N° 2 (HT)`
     - `Moins : Retenue de Garantie (5%)`
     - `= Montant Net HT de la Situation`
     - `Plus : TVA (9%)`
     - `= MONTANT NET À PAYER (TTC) en DA`
     - **Arrêté automatique de la situation en toutes lettres en français** (ex: *"Arrêtée la présente situation N° 02 à la somme TTC de : ..."*).

3. **Prévisualisation & Impression PDF aux Formats Officiels Algériens (06:50 -> 07:28)** :
   Intègre une modale d'aperçu avant impression haute fidélité (prête pour `window.print()` / Export PDF A4 sans aucun élément parasite de l'interface) proposant deux boutons d'impression officiels :
   - **Document Officiel 1 : "Imprimer la Feuille d'Attachement N° 2"** :
     - En-tête officiel avec les informations du Marché, du Chantier, de l'Entreprise et du Maître d'Ouvrage.
     - Tableau détaillé des ouvrages pris en attachement par Zone/Localisation avec colonnes : `N° Art`, `Désignation`, `Unité`, `Détail des Métrés (N × L × l × H)`, `Quantité Partielle`, `Quantité Totale`, `Observations`.
     - Bas de page avec cadres de signatures contradictoires : **Le Chef de Chantier / L'Entreprise**, **Le Bureau d'Études (BET)**, **Le Maître d'Ouvrage**.
   - **Document Officiel 2 : "Imprimer la Situation de Travaux N° 2 (Format Officiel)"** :
     - Page de garde / En-tête réglementaire Algérie (Marché, Avenant N°1 inclus, Entreprise, N° RC / NIF / NIS / RIB Bancaire, Maître d'ouvrage).
     - Grand tableau réglementaire par Section et Article : `N° Prix | Désignation des Ouvrages | Unité | Prix Unitaire HT | Qté Marché (Initiale + Avenant) | Qté Précédente | Qté du Mois (Attachement N°2) | Qté Cumulée | Montant Précédent HT | Montant du Mois HT | Montant Cumulé HT | %`.
     - Tableau récapitulatif financier final (Brut HT, Retenue de garantie 5%, TVA 9%, Net à payer TTC en chiffres et en toutes lettres) + les 3 cadres de signature et cachet officiels.

---

# DONNÉES PRÉ-CHARGÉES POUR TESTER LE SCÉNARIO DE LA VIDÉO IMMÉDIATEMENT
Au lancement de l'application, pré-charge exactement le scénario de la vidéo pour que je puisse le tester en 2 clics :
1. Un **Marché R+5** à Sidi Bel Abbès (TVA `9%`, Retenue de garantie `5%`) avec :
   - **Article `4.2`** : Quantité prévue = `3` (prêt à recevoir une saisie de `2` dans l'attachement pour afficher `66,67%` d'avancement).
   - **Article `4.5`** : Quantité prévue = `6` et Prix Unitaire = `4 000 DA` (avec un bouton rapide **"Simuler le test vidéo (Saisir 622)"** ou la saisie déjà prête dans l'Attachement N°2 qui déclenche l'alerte de dépassement `622 > 6`, et un **Avenant N°1** prêt à être validé pour débloquer instantanément l'Attachement N°2 et générer la Situation N°2 !).
```