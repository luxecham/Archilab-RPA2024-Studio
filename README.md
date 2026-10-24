# BTP Manager Pro DZ 🇩🇿 — ERP BTP & Marchés Publics Algérie

Application web progressive (PWA) complète, moderne et responsive (PC, Tablette et Mode Terrain Mobile Chef de Chantier) dédiée à la gestion intégrale des **Marchés Publics et Privés de BTP en Algérie**, conforme au **Code des Marchés Publics Algérien (Décret présidentiel n° 15-247)** et aux règles de la Trésorerie et des Maîtres d'Ouvrage (OPGI, DLEP, DUC, DEP, APC...).

---

## 🌐 Comment Mettre en Ligne le Projet pour Tests Utilisateurs

Vous pouvez déployer cette application gratuitement pour que des ingénieurs, chefs de chantier, métreurs et directeurs de travaux puissent l'essayer directement sur leur smartphone, tablette ou ordinateur sans rien installer.

### Solution 1 : Déploiement Automatique via GitHub Pages (100% Gratuit)
1. Créez un dépôt sur [GitHub.com](https://github.com/new) nommé par exemple `btp-manager-dz`.
2. Liez votre dossier local et poussez le code :
   ```bash
   git remote add origin https://github.com/VOTRE_NOM_UTILISATEUR/btp-manager-dz.git
   git push -u origin main
   ```
3. Dans les paramètres de votre dépôt GitHub (**Settings > Pages**) :
   - Sous **Source**, sélectionnez **GitHub Actions**.
   - Le workflow automatisé déjà inclus (`.github/workflows/deploy.yml`) construira et publiera automatiquement le site en ligne sur :  
     `https://VOTRE_NOM_UTILISATEUR.github.io/btp-manager-dz/`

---

### Solution 2 : Déploiement en 1 Clic sur Vercel (Recommandé)
1. Rendez-vous sur [vercel.com](https://vercel.com) (connexion gratuite avec compte GitHub).
2. Cliquez sur **« Add New Project »** et sélectionnez votre dépôt GitHub `btp-manager-dz`.
3. Cliquez sur **« Deploy »** sans toucher aux options :
   - Votre application sera immédiatement disponible en HTTPS sur une URL du type `https://btp-manager-dz.vercel.app`.
   - Vercel mettra à jour l'application automatiquement à chaque nouveau `git push`.

---

### Solution 3 : Déploiement en 1 Clic sur Netlify
1. Rendez-vous sur [netlify.com](https://www.netlify.com).
2. Importez votre dépôt GitHub et cliquez sur **Deploy**.
3. La configuration `netlify.toml` déjà prête gère la redirection et le routage SPA.

---

## 💬 Système de Collecte des Retours Testeurs Intégré

L'application intègre un **widget flottant permanent** en bas à droite de l'écran :  
👉 **« 💬 Donner un avis / Signaler un problème »**

Ce widget permet à chaque utilisateur testeur en situation réelle :
- De signaler un **bug bloquant**, une **suggestion d'amélioration** ou une remarque.
- D'indiquer son métier (*Conducteur de travaux, Chef de chantier, Métreur BTP, Comptable...*).
- De qualifier le module concerné (*Attachements, Situations, DQE, Scanner IA...*).
- D'**envoyer son rapport directement via WhatsApp** au développeur en 1 clic.
- D'**exporter la liste complète des retours en JSON** ou de la copier pour l'ouvrir dans les issues GitHub.

---

## 🚀 Démarrage Local

Le serveur de développement local :
```bash
npm run dev
```
👉 Accès sur **[http://localhost:3000](http://localhost:3000)**

Pour compiler pour la production :
```bash
npm run build
```

---

## 🏗️ Fonctionnalités & Workflow Métier Algérien

### 1. Gestion des Marchés Publics & DQE
- **Paramétrage financier DZ strict** :
  - Taux TVA : **9 %** (logement social/public, travaux spécifiques) ou **19 %** (régime général).
  - Retenue de garantie : **5 %** ou **10 %**.
  - Avance forfaitaire (**15 %**) et remboursement automatique sur décomptes.
  - Cautions bancaires (Soumission, Bonne Exécution, Restitution d'avances).
- **Arborescence DQE hiérarchique** : Marché → Lots → Sections → Articles DQE.
- Suivi des 58 Wilayas d'Algérie avec support bilingue (Français / Arabe RTL).
- **Ajout de projet et classement par Maître d'Ouvrage** (OPGI, DUC, DEP, DTP, APC...).

### 2. Le Cœur Métier : Attachements & Détection de Dépassement
- Saisie des attachements contradictoires rattachés à une zone précise (*Bloc A - RDC*, *Fondations*...).
- **Double mode de saisie** : Quantité directe ou Avant-Métré Détaillé ($N \times L \times l \times H$).
- **Détection Automatique des Dépassements Contractuels (Alerte Rouge)** :
  - Blocage visuel en cas de quantité supérieure au bordereau contractuel.
  - Calcul instantané de l'incidence financière.
  - Déclenchement automatique d'un avenant de régularisation.

### 3. Avenants & Validation Synchronisée
- Création d'Avenants régularisant les dépassements ou introduisant des travaux nouveaux.
- Dès validation de l'avenant, les nouvelles quantités contractuelles sont réinjectées dans le DQE du marché et l'alerte sur l'attachement est levée automatiquement.

### 4. Situations Mensuelles de Travaux (Décomptes Provisoires & DGD)
- Génération en 1 clic à partir des attachements contradictoires validés.
- Liquidation comptable : Déduction situations antérieures, déduction de la Retenue de Garantie (5%), remboursement des avances, calcul TVA (9% ou 19%), Net à payer TTC.
- **Conversion automatique du montant en toutes lettres en Dinars Algériens** :
  - *« Arrêtée la présente situation N° 02 à la somme TTC de : Six millions huit cent trente-huit mille six cent soixante Dinars Algériens »*.

### 5. Générateur d'États Officiels & PDF (Anti-Rejet Trésorerie)
- Mise en page vectorielle A4 avec en-tête officiel : **République Algérienne Démocratique et Populaire**.
- Les **3 cadres de signature et cachet obligatoires** :
  1. *L'Entreprise de Réalisation (Lu et accepté)*
  2. *Le Bureau d'Études / Maître d'Œuvre (BET - Vérifié et certifié exact)*
  3. *Le Maître d'Ouvrage / Administration (Approuvé pour mandatement)*

### 6. Assistant IA Métreur & Scanner de Devis Vierge (BPU Chiffres & Lettres)
- **Numérisation / Scan de Devis Vierge** :
  - Upload de photos/scans (JPG, PNG, PDF) ou démo prête en 1 clic avec les modèles officiels.
  - Faisceau laser de scan avec détection OCR des grilles et colonnes.
- **Rédaction & Chiffrage Automatique par l'IA** :
  - Décomposition du déboursé sec selon la mercuriale BTP Algérie.
  - **Rédaction obligatoire en chiffres ET en toutes lettres** en Dinars Algériens.
  - Injection directe dans le marché et impression du BPU officiel.

### 7. Gestion Complète des Ouvriers & Paie BTP
- Ajout, modification et suppression des ouvriers par chantier.
- Pointage mensuel des journées travaillées.
- Calcul automatique des cotisations sociales **CNAS (9% salarial / 26% patronal)** et **CACOBATPH (12,21% Congés Payés & Chômage-Intempéries BTP)**.
