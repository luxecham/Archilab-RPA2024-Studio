# Guide Complet & Prompts Antigravity — Recréer BTP Manager Pro (Algérie)

Ce document contient l'analyse technique de la vidéo de démonstration **BTP Manager Pro** (Dinateck) ainsi que la **stratégie d'exécution par étapes (Processus séquentiel)** et les **Prompts prêts à copier-coller dans Google Antigravity** (ou tout autre environnement d'IA agentique comme Cursor / Lovable / Bolt) pour régénérer l'application complète de A à Z.

---

## 1. Analyse Technique & Fonctionnelle de la Vidéo

L'application analysée est un **ERP / SaaS de gestion de chantiers et de marchés publics BTP 100 % adapté à la réglementation algérienne** (Code des Marchés Publics Algérien, Loi de Finances, CNAS, CACOBATPH, G50).

### A. Architecture & Expérience Utilisateur (UI/UX)
* **Support Multi-plateforme & Offline-First** : Application Web Progressive (PWA) fonctionnant sur PC (bureau d'études / direction), Tablette et Mobile (mode tactile terrain pour le chef de chantier), avec synchronisation hors-ligne.
* **Bilingue & Localisé Algérie** : Interface Français / Arabe (RTL), gestion native des **58 Wilayas**, devise en **Dinar Algérien (DZD / DA)** formatée au centime (`1 250 000,00 DA`), et conversion automatique des montants en toutes lettres sur les documents officiels.

### B. Le Cœur Métier (Le Workflow démontré dans la vidéo)
1. **Appels d'Offres & IA Métreur** :
   * Importation de bordereaux de prix / DQE (Devis Quantitatif et Estimatif) depuis un fichier Excel, PDF ou image scannée.
   * Estimation assistée par IA des prix unitaires selon la mercuriale algérienne.
2. **Gestion des Marchés Publics, Conventions & Contrats** :
   * Paramétrage du dossier : Type (Marché public, Convention, Bon de commande / Contrat), Numéro, Intitulé (ex. *Construction de 40 logements publics locatifs R+5*), Maître d'ouvrage (OPGI, DLEP, APC, Direction...), Wilaya, Délai d'exécution, ODS (Ordre de Service de démarrage).
   * Paramètres financiers algériens : **TVA (9 % pour le logement / travaux spécifiques ou 19 % régime général)**, **Retenue de garantie (5 % ou 10 %)**, **Avance forfaitaire (15 %)** et **Avance sur approvisionnement (jusqu'à 35 %)** avec calcul automatique du remboursement sur situations, **Cautions bancaires** (soumission, bonne exécution) et **Révision des prix**.
   * Structure hiérarchique du DQE : **Marché → Lots → Sections / Chapitres → Articles** (N° d'article, Désignation, Unité : `m²`, `m³`, `ml`, `U`, `Kg`, `Fft`, Quantité initiale prévue au cahier des charges, Prix Unitaire HT, Montant Total HT).
3. **Gestion des Chantiers & Espace Chef de Chantier** :
   * Affectation d'un chantier à un **Chef de Chantier** dédié avec localisation par Wilaya / Commune.
   * Suivi de l'avancement physique par lot et par article, gestion des équipes, pointage journalier, réceptions de matériaux, bons de livraison et dépenses de caisse chantier.
4. **Saisie des Attachements & Feuilles de Métré (Le point fort de la vidéo)** :
   * Le chef de chantier (ou l'administrateur) crée un **Attachement N° X** relié à une période et une **Zone / Localisation** précise (ex. *Bloc A - RDC*, *Fondations*).
   * Lors de la sélection d'un article du DQE (ex. *Article 04.02*), le système affiche instantanément : la **Quantité Marché**, la **Quantité Cumulée Précédente**, et la **Quantité Restante**.
   * **Double mode de saisie** :
     * *Mode Métré détaillé (Avant-métré)* : Saisie des lignes de dimensions `Nombre d'éléments (N) × Longueur (L) × Largeur (l) × Hauteur/Épaisseur (H)` avec calcul automatique du volume/surface et zone d'observations.
     * *Mode Quantité directe* : Saisie directe de la quantité exécutée.
   * **Calcul en temps réel du taux d'avancement (%)** de l'article.
5. **Détection Automatique des Dépassements & Workflow d'Avenant** :
   * *Démonstration clé à 04:05 dans la vidéo* : Si la quantité prévue au marché est de `6,00` et que l'utilisateur saisit `622,00`, le logiciel déclenche une **alerte visuelle immédiate de dépassement contractuel ("Dépassement de quantité / Hors Marché")**.
   * Le système bloque ou signale l'écart et propose de générer automatiquement un **Avenant (Avenant N° 1, 2...)** incluant :
     * Les travaux supplémentaires (quantités en dépassement sur articles existants).
     * Les travaux modificatifs (en plus ou en moins).
     * Les **Travaux Nouveaux (Articles hors bordereau initial)** avec création de nouveaux prix unitaires.
   * Tableau comparatif financier en temps réel : **Montant Initial du Marché vs Montant de l'Avenant (+ / - en DA et en % d'incidence financière) vs Nouveau Montant Final du Marché**.
   * **Déblocage synchronisé** : Dès que l'Avenant passe au statut **« Validé / Approuvé »**, la nouvelle quantité contractuelle (ex. `622`) est automatiquement injectée dans l'attachement sans ressaisie !
6. **Génération Automatique des Situations de Travaux (Décomptes Provisoires & DGD)** :
   * Une fois l'attachement validé (Statut : *Brouillon → Soumis → Validé*), un clic génère la **Situation de Travaux N° X** (ou le Décompte Général et Définitif - DGD).
   * Reprise automatique des cumuls : *Quantité Précédente*, *Quantité du Mois (Attachement)*, *Quantité Cumulée*, *Montant HT Précédent*, *Montant HT du Mois*, *Montant HT Cumulé*.
   * Déduction automatique de la **Retenue de Garantie (5 %)**, remboursement des avances, application de la **TVA (9 % ou 19 %)**, et calcul du **Net à Payer TTC**.
7. **Impression & Export aux Formats Officiels Algériens (Anti-Rejet Trésorerie / Maître d'Ouvrage)** :
   * Génération de PDF prêts à signer et cacheter (sans erreur d'arrondi qui cause le rejet des situations au niveau de la banque, du maître d'ouvrage ou du contrôleur financier) :
     * **Feuille d'Attachement contradictoire** (avec cartouche Entreprise / Bureau d'Études BET / Maître d'Ouvrage).
     * **Situation Mensuelle de Travaux / Décompte Provisoire** au format réglementaire.
     * **Récapitulatif Général & Avenant**.
8. **Modules Complémentaires de l'ERP** :
   * **Logistique, Achats & Stock** : Fournisseurs, Bons de commande, Bons de réception chantier, Gestion du dépôt et parc matériel/engins.
   * **Finance, Trésorerie & Fiscalité DZ** : Suivi des encaissements, gestion des **Chèques / Ordres de virement**, Dépenses par chantier, **État 104**, déclarations **G50**, et Paie BTP (**CNAS / CACOBATPH**).

---

## 2. Le Processus Recommandé dans Antigravity (Méthode en 5 Étapes)

> **Conseil d'architecture** : Ne demande pas à Antigravity de coder les 15 modules en une seule requête. Les agents IA produisent un code 10x plus propre et fonctionnel lorsque tu procèdes avec **1 Prompt Maître d'Initialisation**, suivi de **4 Prompts d'Itération Ciblés**.

1. **Étape 1 (Prompt Maître)** : Initialisation de l'architecture, de la base de données complète, du Design System BTP Algérie, du Tableau de bord, et du module **Marchés + DQE + Chantiers**.
2. **Étape 2 (Prompt Cœur Métier)** : Développement du moteur **Attachements + Métré Détaillé (L×l×H) + Détection de Dépassement + Workflow Avenants synchronisé**.
3. **Étape 3 (Prompt Facturation Officielle)** : Moteur de **Situations de Travaux (N°1, N°2... DGD)** + Calculs financiers algériens (TVA 9%/19%, Retenue de garantie 5%) + **Générateur PDF officiel imprimable**.
4. **Étape 4 (Prompt Logistique & Finance)** : Modules **Stock, Achats, Chèques/Trésorerie, Paie CACOBATPH/CNAS & Assistant IA Métreur (Import Excel DQE)**.

---

## 3. PROMPT MAÎTRE (Étape 1 — À coller en premier dans Antigravity)

Copie et colle le bloc ci-dessous dans **Antigravity** pour lancer la création du projet :

```markdown
# RÔLE & OBJECTIF
Agis en tant qu'Architecte Logiciel Senior et Développeur Full-Stack Expert spécialisé dans les ERP pour le secteur du BTP (Bâtiment et Travaux Publics) en Algérie.
Nous allons construire **"BTP Manager Pro DZ"**, une application web progressive (PWA) complète, moderne, rapide et responsive (PC, Tablette et Mode Terrain Mobile pour Chef de Chantier) dédiée à la gestion des Marchés Publics et Privés en Algérie (suivi des appels d'offres, DQE, chantiers, feuilles de métré, attachements, détection automatique des dépassements, avenants, situations de travaux, logistique, trésorerie/chèques et documents officiels conformes aux normes algériennes).

# STACK TECHNIQUE IMPOSÉE
- **Framework** : React 18+ avec TypeScript et Vite (ou Next.js App Router)
- **Styling & UI** : Tailwind CSS + Lucide Icons + composants style shadcn/ui (design industriel premium : Slate/Charcoal profond, Bleu Chantier `#0F172A` / `#2563EB`, Ambre BTP `#F59E0B`, Émeraude validation `#10B981`, Rouge alerte dépassement `#EF4444`).
- **Gestion d'état & Persistance** : Zustand (ou React Context) avec persistance locale complète (`IndexedDB` / `localStorage`) et couche d'abstraction prête pour Supabase/PostgreSQL. L'application DOIT être 100% fonctionnelle immédiatement avec un jeu de données de démonstration réaliste pré-chargé (aucun bouton mort, aucun placeholder vide).
- **Internationalisation & Formatage Algérie** :
  - Devise : Dinar Algérien (`DA` / `DZD`) avec formatage monétaire strict (`1 250 400,00 DA`).
  - Fonction utilitaire de conversion des montants chiffres en toutes lettres en français (ex: *"Un million deux cent cinquante mille quatre cents Dinars Algériens"* pour le bas des situations et décomptes).
  - Liste complète des **58 Wilayas d'Algérie** (01 Adrar à 58 El Meniaa, incluant 22 Sidi Bel Abbès, 16 Alger, 31 Oran, etc.).
  - Support de bascule de langue FR / AR (avec support RTL).

# MODÈLE DE DONNÉES COMPLET (SCHÉMA TYPESCRIPT)
Crée dans `src/types/btp.ts` les entités relationnelles suivantes :
1. **Tender (Appel d'Offres)** : `id, reference, title, client (Maître d'ouvrage: OPGI, DLEP, APC, Direction...), wilaya, submissionDate, estimatedAmountHT, cautionSoumission, status ('En préparation' | 'Soumis' | 'Retenu' | 'Infructueux')`.
2. **Market (Marché / Convention / Contrat)** :
   - `id, type ('Marché Public' | 'Convention' | 'Contrat' | 'Bon de Commande'), number, title, client, bet (Bureau d'études), enterpriseName, wilaya, commune`
   - `signatureDate, odsDate (Ordre de Service de démarrage), durationMonths, status ('En cours' | 'En arrêt' | 'Réception Provisoire' | 'Réception Définitive' | 'Clôturé')`
   - Paramètres financiers DZ : `tvaRate (9 | 19), retenueGarantieRate (5 | 10), avanceForfaitaireRate (0 | 15), avanceApproRate (0 | 35), initialAmountHT, currentAmountHT (mis à jour après avenants)`.
3. **Lot & Section & DQEArticle (Bordereau des Prix / DQE)** :
   - Hiérarchie : `Market` -> `Lot[]` (ex: Lot 01 Gros Œuvres, Lot 02 Corps d'état secondaires) -> `Section[]` (ex: Terrassement, Infrastructure, Superstructure) -> `DQEArticle[]`.
   - `DQEArticle` : `id, marketId, lotId, sectionId, code (ex: '04.02'), designation, unit ('m³' | 'm²' | 'ml' | 'U' | 'Kg' | 'T' | 'Fft' | 'Ens'), initialQuantity, avenantQuantityDelta (somme des avenants validés), currentContractQuantity (initial + delta), unitPriceHT, previousExecutedQty, totalExecutedQty, isExtraArticle (boolean pour travaux hors bordereau)`.
4. **Chantier (Site)** : `id, marketId, name, wilaya, address, chefChantierName, chefChantierPhone, physicalProgressPct, financialProgressPct, status`.
5. **Attachement & MetreLine (Feuille d'Attachement & Avant-Métré)** :
   - `Attachement` : `id, marketId, chantierId, number (ex: 1, 2), periodStart, periodEnd, zone (ex: 'Bloc A - RDC'), status ('Brouillon' | 'Soumis Chef Chantier' | 'Validé'), createdBy, validatedAt, items: AttachementItem[]`.
   - `AttachementItem` : `id, articleId, mode ('Direct' | 'MetreDetaille'), metreLines: { id, label, count, length, width, height, factor, subtotal }[], currentQuantity, observation, hasExceedance (boolean), exceedanceQty (number)`.
6. **Avenant (Amendment)** :
   - `id, marketId, number (ex: 1), title, motif, date, status ('Brouillon' | 'En cours d'approbation' | 'Validé' | 'Rejeté'), initialMarketHT, deltaAmountHT, newMarketHT, incidencePct, changes: { articleId, code, designation, unit, oldQty, newQty, deltaQty, unitPriceHT, isNewArticle: boolean }[]`.
7. **Situation (Situation de Travaux / Décompte)** :
   - `id, marketId, number, date, period, attachedAttachementIds: string[], status ('Brouillon' | 'Soumise' | 'Validée / Payée'), isDGD (boolean)`
   - Calculs : `grossAmountPreviousHT, grossAmountCurrentHT, grossAmountCumulativeHT, retenueGarantieHT, remboursementAvanceHT, netHT, tvaAmount, netToPayTTC`.
8. **Logistics & Finance** :
   - `StockItem`, `Supplier`, `PurchaseOrder`, `Expense` (Dépenses chantier), `ChequePayment` (Suivi des chèques, encaissements, banque, échéance, statut), `Worker` (Pointage & Paie CNAS/CACOBATPH).

# JEUX DE DONNÉES DE DÉMONSTRATION PRÉ-CHARGÉS (OBLIGATOIRE)
Initialise l'application avec le cas concret exact :
- **Marché N° 2026/04** : *"Réalisation de 40 Logements Publics Locatifs (LPL) R+5 avec locaux commerciaux"* à Sidi Bel Abbès (Maître d'ouvrage : OPGI Sidi Bel Abbès, BET : Architecture & Ingénierie DZ, TVA : **9%**, Retenue de garantie : **5%**).
- **Lots & Articles DQE réalistes** :
  - *Section 1 : Terrassement & Infrastructure* (01.01 Fouilles en puits et rigoles `m³`, 01.02 Béton de propreté dosé à 150 kg/m³ `m³`, 01.03 Béton armé en fondation dosé à 350 kg/m³ `m³`, 04.02 Regards de visite en béton armé 80x80 `U` avec quantité prévue = `3` et un autre article `04.05` avec quantité prévue = `6`).
  - *Section 2 : Superstructure R+5* (Poteaux et voiles BA, Planchers corps creux 16+4, Maçonnerie double paroi en briques).
- Deux autres marchés actifs (ex: *Aménagement Urbain & Voirie APC Oran* à TVA 19%, *Réhabilitation Groupe Scolaire*).

# LIVRABLE DE CETTE ÉTAPE 1
Construis :
1. Le **Layout Principal** avec Sidebar professionnelle regroupant : Tableau de Bord, Appels d'Offres & IA DQE, Marchés Publics, Chantiers (Mode Chef de Chantier), Attachements & Métrés, Avenants, Situations & DGD, Documents Officiels, Logistique & Stock, Finance & Chèques, Paramètres.
2. Le **Tableau de Bord Exécutif** : KPIs globaux (Montant total des marchés, Encaissements, Reste à réaliser, Alertes de dépassement d'attachements, Alertes cautions bancaires), graphiques d'avancement physique vs financier.
3. Le **Module Marchés Publics & DQE** complet : Création/édition d'un marché (choix TVA 9%/19%, retenue 5%), explorateur hiérarchique des Lots > Sections > Articles du DQE, ajout/modification d'articles, import rapide de DQE, et fiche synthèse financière du marché.
4. Le **Module Chantiers** : Vue par chantier avec wilaya, chef de chantier, taux d'avancement, et accès direct aux saisies de terrain.
```

---

## 4. PROMPT ÉTAPE 2 — Attachements, Métré (L×l×H), Détection de Dépassement & Avenants

Une fois l'Étape 1 générée par Antigravity, envoie ce deuxième prompt pour implémenter la mécanique centrale démontrée entre `03:10` et `06:15` dans la vidéo :

```markdown
# OBJECTIF ÉTAPE 2 : MOTEUR D'ATTACHEMENTS, MÉTRÉ DÉTAILLÉ & WORKFLOW D'AVENANT AUTOMATIQUE

Implémente maintenant de façon 100% interactive les modules **"Attachements & Métrés"** et **"Avenants"** en respectant exactement le workflow métier BTP algérien suivant :

1. **Création & Saisie d'un Attachement (Chef de Chantier ou Admin)** :
   - Sélection du Marché et du Chantier, définition du Numéro d'attachement (ex: Attachement N° 01, N° 02), de la période et de la **Zone / Localisation** (ex: *"Bloc A - RDC"*, *"Fondations Axe 1 à 5"*).
   - Sélection d'un article du DQE via un sélecteur recherchable (par code ex: `04.02` ou désignation).
   - Dès qu'un article est sélectionné, affiche un bandeau d'état temps réel :
     - **Quantité Marché (Contractuelle)** (ex: `3,00` ou `6,00`)
     - **Quantité Cumulée Précédente**
     - **Quantité Restante Disponible**
     - **Barre de progression du taux d'avancement (%)** de l'article.
   - **Double mode de saisie des quantités** :
     - **Mode "Feuille de Métré (L × l × H)"** : Permet d'ajouter plusieurs lignes de détail (Désignation de la partie d'ouvrage, Nombre d'éléments similaires `N`, Longueur `L`, Largeur `l`, Hauteur/Épaisseur `H`, Coefficient/Déduction) qui calculent automatiquement la quantité partielle et totale, avec champ d'observation.
     - **Mode "Quantité Directe"** : Saisie directe de la quantité réalisée.

2. **Détection Automatique des Dépassements Contractuels (Alerte Critique)** :
   - Si la quantité saisie + cumul précédent dépasse la quantité contractuelle du marché (exemple : quantité prévue = `6`, quantité saisie = `622`), affiche immédiatement :
     - Un badge et une alerte rouge vif : **"DÉassement Contractuel Détecté (+616,00 unités hors marché)"**.
     - Le calcul de l'impact financier HT du dépassement.
     - Un bouton d'action rapide : **"Générer / Ajouter à un Avenant pour régulariser"**.

3. **Module Avenants & Synchronisation Automatique** :
   - Permet de créer un **Avenant N° X** rattaché au marché (en cliquant depuis l'alerte de dépassement de l'attachement ou depuis le menu Avenants).
   - L'avenant permet :
     - D'ajuster les quantités des articles existants en dépassement (ex: passer la quantité de `6` à `622`) ou en diminution.
     - D'ajouter des **Travaux Supplémentaires / Articles Hors Bordereau (Nouveaux Prix)** avec code, désignation, unité, quantité et prix unitaire HT.
   - Affiche en haut de l'avenant le comparatif financier réglementaire :
     - **Montant Initial du Marché HT & TTC**
     - **Montant de l'Avenant (+ / - HT & TTC)**
     - **Taux d'incidence financière (%)** (avec alerte si l'avenant dépasse les seuils réglementaires du Code des Marchés Publics algérien).
     - **Nouveau Montant Final du Marché HT & TTC**.
   - **Workflow de Validation Synchronisé** :
     - Statuts de l'avenant : `Brouillon` -> `En attente de validation` -> `Validé`.
     - **Règle d'or** : Dès que l'utilisateur clique sur **"Valider l'Avenant"**, les nouvelles quantités contractuelles (ex: `622`) et les nouveaux articles sont automatiquement mis à jour dans le DQE du marché, et l'alerte de dépassement dans l'attachement disparaît instantanément (le taux d'avancement passe à 100% Valide), débloquant la validation de l'attachement !
```

---

## 5. PROMPT ÉTAPE 3 — Situations de Travaux, DGD & Impression Officielle Algérienne

Une fois l'étape 2 terminée, envoie ce troisième prompt pour générer les situations et les états PDF officiels (vus à partir de `06:18` dans la vidéo) :

```markdown
# OBJECTIF ÉTAPE 3 : SITUATIONS DE TRAVAUX, DÉCOMPTES & IMPRESSION FORMAT OFFICIEL ALGÉRIEN

Implémente maintenant le module **"Situations de Travaux & DGD"** ainsi que le **Générateur de Documents Officiels Imprimables (PDF / Print View)** :

1. **Génération Automatique de la Situation à partir des Attachements Validés** :
   - Lorsqu'un ou plusieurs attachements sont au statut `Validé`, un bouton **"Générer la Situation N° X"** compile automatiquement toutes les quantités exécutées sur la période sans aucune ressaisie.
   - Le tableau de la Situation affiche pour chaque section et chaque article du marché (incluant les avenants validés) :
     - `N° Prix` | `Désignation` | `Unité` | `Qté Marché/Avenant` | `P.U HT` | `Qté Précédente` | `Qté du Mois` | `Qté Cumulée` | `Montant Précédent HT` | `Montant du Mois HT` | `Montant Cumulé HT` | `% Avancement`.

2. **Pied de Situation & Calculs Financiers Réglementaires Algériens** :
   - Calcule avec une précision comptable stricte (au centime près, sans erreur d'arrondi pour éviter tout rejet au niveau du Maître d'Ouvrage, du CF ou de la Banque) :
     - **Montant Brut des Travaux Cumulés (HT)**
     - **À déduire : Montant des Situations Précédentes (HT)**
     - **Montant Brut de la Situation du Mois (HT)**
     - **Retenue de Garantie (5% ou 10%)**
     - **Remboursement éventuel de l'Avance Forfaitaire / Approvisionnement**
     - **Montant Net HT du Mois**
     - **TVA Algérie (9% ou 19% selon le paramétrage du marché)**
     - **MONTANT NET À PAYER (TTC) en chiffres ET automatiquement arrêté en toutes lettres en Dinars Algériens** (ex: *"Arrêtée la présente situation N° 02 à la somme TTC de : ..."*).

3. **Aperçu & Impression aux Formats Officiels Algériens (3 Modèles Prêts à Imprimer / Exporter en PDF)** :
   Crée une vue d'impression professionnelle (`@media print` + export PDF propre sur fond blanc A4 portrait/paysage) avec en-tête officiel (République Algérienne Démocratique et Populaire / Cartouche Maître d'Ouvrage, Entreprise, BET, Références du Marché et de l'Avenant) pour :
   - **Modèle 1 : Feuille d'Attachement des Travaux N° X** (avec détail des métrés L×l×H et quantités prises en attachement).
   - **Modèle 2 : Situation Mensuelle de Travaux N° X & Décompte Provisoire** (tableau réglementaire complet + récapitulatif financier TTC + arrêté en toutes lettres).
   - **Modèle 3 : Tableau Récapitulatif d'Avenant** (comparatif Marché Initial / Avenant / Nouveau Marché).
   - Chaque document doit comporter en bas de page les **3 cadres de signature et cachet officiels** :
     1. *L'Entreprise de Réalisation (Lu et accepté)*
     2. *Le Bureau d'Études / Maître d'Œuvre (BET - Vérifié et certifié exact)*
     3. *Le Maître d'Ouvrage / Administration (Approuvé)*
```

---

## 6. PROMPT ÉTAPE 4 — IA Métreur (Import DQE), Logistique, Chèques & Paie BTP

Enfin, utilise ce quatrième prompt pour boucler les modules de gestion d'entreprise mentionnés au début de la vidéo (`00:25 - 01:10`) :

```markdown
# OBJECTIF ÉTAPE 4 : IA MÉTREUR (IMPORT DQE), LOGISTIQUE CHANTIER, TRÉSORERIE/CHÈQUES & PAIE BTP DZ

Complète l'application avec les 4 derniers modules opérationnels :

1. **Module "Appels d'Offres & Assistant IA Métreur"** :
   - Interface d'import de DQE (drag-and-drop d'un fichier CSV/Excel ou collage d'un bordereau brut) qui parse automatiquement les sections et articles.
   - Simulateur d'estimation de prix unitaires par IA basé sur les prix de référence du marché BTP algérien (béton, acier, maçonnerie, terrassement, étanchéité) avec calcul de marge, sous-détail de prix (matériaux + main-d'œuvre + matériel + frais généraux) et génération de l'offre financière.

2. **Module "Logistique, Achats & Stock Chantier"** :
   - Suivi des stocks de matériaux par chantier (Ciment CPJ 42.5, Rond à béton FeE500, Gravier, Sable, Briques 8 et 12 trous).
   - Bons de commande fournisseurs, Bons de réception sur chantier, et affectation du matériel / engins (Bétonnière, Grue, Camion benne, Coffrage) par chantier.

3. **Module "Finance, Dépenses & Suivi des Chèques"** :
   - Journal de trésorerie et dépenses par chantier (achats matériaux, location engins, sous-traitance, caisse chef de chantier).
   - **Registre des Chèques & Encaissements** : Suivi des chèques reçus et émis (Numéro de chèque, Banque : BNA, BEA, CPA, BADR, CNEP, Montant DA, Date d'émission, Date d'encaissement, Statut : `En portefeuille`, `Déposé à l'encaissement`, `Encaissé`, `Rejeté`).
   - Calcul automatique de la rentabilité nette par marché (Situations encaissées - Dépenses réelles du chantier).

4. **Module "Équipes & Paie BTP Algérie (CNAS / CACOBATPH)"** :
   - Liste des ouvriers et chefs de chantier par site (Maçon, Coffreur, Ferrailleur, Manœuvre, Chef de chantier).
   - Feuille de pointage mensuelle et calcul simplifié de la paie avec cotisations algériennes (CNAS 9% salarié / 26% employeur, cotisation CACOBATPH congés payés et chômage-intempéries BTP).
```
