import React, { createContext, useContext, useState, useEffect } from 'react';
import {
  Market,
  DQEArticle,
  Lot,
  Section,
  Chantier,
  Attachement,
  AttachementItem,
  Avenant,
  AvenantChange,
  Situation,
  SituationItemLine,
  StockItem,
  PurchaseOrder,
  MaterialEquipment,
  ChequePayment,
  Expense,
  Worker,
  Tender
} from '../types/btp';
import { numberToWordsDZ } from '../utils/dzFormatters';

// SCÉNARIO EXACT DE LA VIDÉO DINATECK (01:10 → 07:28)
const INITIAL_MARKETS: Market[] = [
  {
    id: 'm-ecole-sba',
    type: 'Marché Public',
    number: '2026/14/DEP/SBA',
    title: 'Réalisation d\'un Groupe Scolaire Type Base 3 (École Primaire) — 12 Salles de Classe, Demi-Pension & Logements d\'Astreinte',
    client: 'Direction des Équipements Publics (DEP) Sidi Bel Abbès',
    bet: 'BET Archi-Structures Sidi Bel Abbès',
    enterpriseName: 'EGBTP ARCHILAB DZ',
    wilaya: '22',
    commune: 'Sidi Bel Abbès (Rocade Sud)',
    signatureDate: '2026-01-10',
    odsDate: '2026-02-01',
    durationMonths: 14,
    status: 'En cours',
    tvaRate: 9, // Taux préférentiel de TVA pour les infrastructures scolaires et universitaires DZ
    retenueGarantieRate: 5, // 5% Marchés Publics DZ
    avanceForfaitaireRate: 15,
    avanceApproRate: 20,
    initialAmountHT: 114678899, // 114 678 899 DA HT + TVA 9% (10 321 101 DA) = Exactement 125 000 000 DA TTC
    currentAmountHT: 114678899,
    bankGuarantees: {
      soumission: 1250000,
      bonneExecution: 5733945, // 5%
      restitutionAvance: 17201835, // 15%
      bankName: 'BNA Agence 412 Sidi Bel Abbès',
      expiryDate: '2027-06-30'
    }
  },
  {
    id: 'm-r5',
    type: 'Marché Public',
    number: '2026/04/OPGI',
    title: 'Projet R+5 — Réalisation de 40 Logements Publics Locatifs (LPL) R+5 avec locaux commerciaux',
    client: 'OPGI Sidi Bel Abbès',
    bet: 'Architecture & Ingénierie DZ',
    enterpriseName: 'EGBTP EL MOUSTAKBEL',
    wilaya: '22',
    commune: 'Sidi Bel Abbès',
    signatureDate: '2026-01-05',
    odsDate: '2026-01-15',
    durationMonths: 18,
    status: 'En cours',
    tvaRate: 9, // Spécifique Logement Algérie
    retenueGarantieRate: 5, // 5% Marchés Publics DZ
    avanceForfaitaireRate: 15,
    avanceApproRate: 35,
    initialAmountHT: 118433000,
    currentAmountHT: 118433000,
    bankGuarantees: {
      soumission: 1200000,
      bonneExecution: 5921650,
      restitutionAvance: 17764950,
      bankName: 'BNA Agence 412 Sidi Bel Abbès',
      expiryDate: '2027-07-15'
    }
  },
  {
    id: 'm-oran-vrd',
    type: 'Marché Public',
    number: '2026/02/DUC',
    title: 'Travaux d\'Aménagement Urbain, Voirie et Réseaux Divers (VRD)',
    client: 'Direction de l\'Urbanisme (DUC) Oran',
    bet: 'BET Urbapro Oran',
    enterpriseName: 'EGBTP EL MOUSTAKBEL',
    wilaya: '31',
    commune: 'Oran',
    signatureDate: '2025-11-20',
    odsDate: '2025-12-01',
    durationMonths: 12,
    status: 'En cours',
    tvaRate: 19,
    retenueGarantieRate: 5,
    avanceForfaitaireRate: 15,
    avanceApproRate: 20,
    initialAmountHT: 64200000,
    currentAmountHT: 64200000,
    bankGuarantees: {
      soumission: 650000,
      bonneExecution: 3210000,
      restitutionAvance: 9630000,
      bankName: 'BEA Oran Front de Mer',
      expiryDate: '2026-12-31'
    }
  }
];

// ARBORESCENCE DU CAHIER DES CHARGES : LOTS
const INITIAL_LOTS: Lot[] = [
  // LOTS GROUPE SCOLAIRE BASE 3 (DEP SIDI BEL ABBÈS - 125M DA TTC)
  { id: 'lot-ecole-01', marketId: 'm-ecole-sba', code: 'LOT 01', name: 'Gros Œuvres, Fondations & Superstructure École' },
  { id: 'lot-ecole-02', marketId: 'm-ecole-sba', code: 'LOT 02', name: 'Corps d\'État Secondaires (CES, Maçonnerie & Finitions)' },
  { id: 'lot-ecole-03', marketId: 'm-ecole-sba', code: 'LOT 03', name: 'VRD, Assainissement, Cour & Clôture Défensive' },
  // LOTS MARCHÉ R+5
  { id: 'lot-01', marketId: 'm-r5', code: 'LOT 01', name: 'Gros Œuvres & Terrassement' },
  { id: 'lot-02', marketId: 'm-r5', code: 'LOT 02', name: 'Corps d\'État Secondaires (CES)' }
];

// ARBORESCENCE DU CAHIER DES CHARGES : SECTIONS
const INITIAL_SECTIONS: Section[] = [
  // SECTIONS GROUPE SCOLAIRE
  { id: 'sec-ecole-01', lotId: 'lot-ecole-01', code: 'Section 1', name: 'Terrassements Généraux & Fondations Spéciales' },
  { id: 'sec-ecole-02', lotId: 'lot-ecole-01', code: 'Section 2', name: 'Superstructure R+1, Poteaux, Poutres & Dalles' },
  { id: 'sec-ecole-03', lotId: 'lot-ecole-02', code: 'Section 3', name: 'Maçonnerie Briques Doubles Parois & Enduits' },
  { id: 'sec-ecole-04', lotId: 'lot-ecole-03', code: 'Section 4', name: 'Réseaux d\'Assainissement & Clôture Défensive' },
  // SECTIONS MARCHÉ R+5
  { id: 'sec-01', lotId: 'lot-01', code: 'Section 1', name: 'Infrastructure & Fondations' },
  { id: 'sec-02', lotId: 'lot-01', code: 'Section 2', name: 'Assainissement, Regards & Caniveaux' },
  { id: 'sec-03', lotId: 'lot-01', code: 'Section 3', name: 'Superstructure Béton Armé R+5' }
];

// ARTICLES DU CAHIER DES CHARGES (AVEC ARTICLES 4.2 ET 4.5 EXACTS DE LA VIDÉO)
const INITIAL_ARTICLES: DQEArticle[] = [
  // ARTICLES DQE GROUPE SCOLAIRE 125M DA TTC (DEP SIDI BEL ABBÈS)
  {
    id: 'art-ec-01',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-01',
    sectionId: 'sec-ecole-01',
    code: '1.01',
    designation: 'Fouilles en pleine masse et en rigoles dans terrain ordinaire et rocheux pour fondations école',
    unit: 'm³',
    initialQuantity: 1850,
    avenantQuantityDelta: 0,
    currentContractQuantity: 1850,
    unitPriceHT: 750,
    previousExecutedQty: 1850,
    totalExecutedQty: 1850
  },
  {
    id: 'art-ec-02',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-01',
    sectionId: 'sec-ecole-01',
    code: '1.02',
    designation: 'Béton de propreté dosé à 150 kg/m³ de ciment CPJ 42.5 sous semelles isolées',
    unit: 'm³',
    initialQuantity: 160,
    avenantQuantityDelta: 0,
    currentContractQuantity: 160,
    unitPriceHT: 9500,
    previousExecutedQty: 160,
    totalExecutedQty: 160
  },
  {
    id: 'art-ec-03',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-01',
    sectionId: 'sec-ecole-01',
    code: '1.03',
    designation: 'Gros béton de calage et de rattrapage de niveau dosé à 250 kg/m³',
    unit: 'm³',
    initialQuantity: 280,
    avenantQuantityDelta: 0,
    currentContractQuantity: 280,
    unitPriceHT: 13500,
    previousExecutedQty: 280,
    totalExecutedQty: 280
  },
  {
    id: 'art-ec-04',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-01',
    sectionId: 'sec-ecole-01',
    code: '1.04',
    designation: 'Béton armé en fondations (semelles isolées, filantes et longrines) dosé à 350 kg/m³',
    unit: 'm³',
    initialQuantity: 520,
    avenantQuantityDelta: 0,
    currentContractQuantity: 520,
    unitPriceHT: 19500,
    previousExecutedQty: 520,
    totalExecutedQty: 520
  },
  {
    id: 'art-ec-05',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-01',
    sectionId: 'sec-ecole-02',
    code: '2.01',
    designation: 'Béton armé pour poteaux, poutres, chaînages et voiles d\'escaliers dosé à 350 kg/m³',
    unit: 'm³',
    initialQuantity: 780,
    avenantQuantityDelta: 0,
    currentContractQuantity: 780,
    unitPriceHT: 24500,
    previousExecutedQty: 450,
    totalExecutedQty: 450
  },
  {
    id: 'art-ec-06',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-01',
    sectionId: 'sec-ecole-02',
    code: '2.02',
    designation: 'Plancher à corps creux 16+4 y compris poutrelles préfabriquées et dalle de compression',
    unit: 'm²',
    initialQuantity: 2200,
    avenantQuantityDelta: 0,
    currentContractQuantity: 2200,
    unitPriceHT: 4200,
    previousExecutedQty: 1100,
    totalExecutedQty: 1100
  },
  {
    id: 'art-ec-07',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-02',
    sectionId: 'sec-ecole-03',
    code: '3.01',
    designation: 'Maçonnerie en briques creuses de 12 et 8 trous pour doubles cloisons avec lame d\'air',
    unit: 'm²',
    initialQuantity: 3400,
    avenantQuantityDelta: 0,
    currentContractQuantity: 3400,
    unitPriceHT: 3100,
    previousExecutedQty: 1200,
    totalExecutedQty: 1200
  },
  {
    id: 'art-ec-08',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-02',
    sectionId: 'sec-ecole-03',
    code: '3.02',
    designation: 'Enduit au mortier de ciment bâtard intérieur et extérieur sous plafond',
    unit: 'm²',
    initialQuantity: 6800,
    avenantQuantityDelta: 0,
    currentContractQuantity: 6800,
    unitPriceHT: 850,
    previousExecutedQty: 1500,
    totalExecutedQty: 1500
  },
  {
    id: 'art-ec-09',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-03',
    sectionId: 'sec-ecole-04',
    code: '4.01',
    designation: 'Caniveaux préfabriqués d\'évacuation des eaux pluviales de la cour d\'école avec grille fonte',
    unit: 'ml',
    initialQuantity: 450,
    avenantQuantityDelta: 0,
    currentContractQuantity: 450,
    unitPriceHT: 4200,
    previousExecutedQty: 200,
    totalExecutedQty: 200
  },
  {
    id: 'art-ec-10',
    marketId: 'm-ecole-sba',
    lotId: 'lot-ecole-03',
    sectionId: 'sec-ecole-04',
    code: '4.02',
    designation: 'Muret de clôture d\'enceinte école avec grille métallique défensive et portail principal',
    unit: 'ml',
    initialQuantity: 380,
    avenantQuantityDelta: 0,
    currentContractQuantity: 380,
    unitPriceHT: 16500,
    previousExecutedQty: 150,
    totalExecutedQty: 150
  },
  // ARTICLES DU PROJET R+5
  {
    id: 'art-1-1',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-01',
    code: '1.1',
    designation: 'Fouilles en puits et en rigoles dans terrain de toute nature sauf rocher',
    unit: 'm³',
    initialQuantity: 850,
    avenantQuantityDelta: 0,
    currentContractQuantity: 850,
    unitPriceHT: 650,
    previousExecutedQty: 850,
    totalExecutedQty: 850
  },
  {
    id: 'art-1-2',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-01',
    code: '1.2',
    designation: 'Béton de propreté sous semelles dosé à 150 kg/m³ de ciment CPJ 42.5',
    unit: 'm³',
    initialQuantity: 120,
    avenantQuantityDelta: 0,
    currentContractQuantity: 120,
    unitPriceHT: 8500,
    previousExecutedQty: 120,
    totalExecutedQty: 120
  },
  {
    id: 'art-1-3',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-01',
    code: '1.3',
    designation: 'Béton armé en fondations (semelles et longrines) dosé à 350 kg/m³',
    unit: 'm³',
    initialQuantity: 420,
    avenantQuantityDelta: 0,
    currentContractQuantity: 420,
    unitPriceHT: 16500,
    previousExecutedQty: 380,
    totalExecutedQty: 420
  },
  // ARTICLE 4.2 : DÉMONSTRATION CAS 1 DE LA VIDÉO (Qté prévue = 3, Saisie = 2 -> 66,67%)
  {
    id: 'art-4-2',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-02',
    code: '4.2',
    designation: 'Regards de visite en béton armé 80x80 cm sous chaussée avec tampon fonte série lourde',
    unit: 'U',
    initialQuantity: 3,
    avenantQuantityDelta: 0,
    currentContractQuantity: 3,
    unitPriceHT: 28000,
    previousExecutedQty: 0,
    totalExecutedQty: 2
  },
  // ARTICLE 4.5 : DÉMONSTRATION CAS 2 DE LA VIDÉO (Qté prévue = 6, P.U = 4 000 DA, Saisie = 622 -> Dépassement)
  {
    id: 'art-4-5',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-02',
    code: '4.5',
    designation: 'Caniveaux préfabriqués d\'évacuation des eaux pluviales avec grille galvanisée',
    unit: 'ml',
    initialQuantity: 6,
    avenantQuantityDelta: 0,
    currentContractQuantity: 6,
    unitPriceHT: 4000, // Exact de la vidéo
    previousExecutedQty: 0,
    totalExecutedQty: 0
  },
  {
    id: 'art-3-1',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-03',
    code: '3.1',
    designation: 'Béton armé en élévation pour poteaux, voiles et poutres R+5',
    unit: 'm³',
    initialQuantity: 580,
    avenantQuantityDelta: 0,
    currentContractQuantity: 580,
    unitPriceHT: 22000,
    previousExecutedQty: 150,
    totalExecutedQty: 240
  },
  {
    id: 'art-3-2',
    marketId: 'm-r5',
    lotId: 'lot-01',
    sectionId: 'sec-03',
    code: '3.2',
    designation: 'Planchers à corps creux 16+4 y compris poutrelles et dalle de compression',
    unit: 'm²',
    initialQuantity: 3200,
    avenantQuantityDelta: 0,
    currentContractQuantity: 3200,
    unitPriceHT: 3800,
    previousExecutedQty: 600,
    totalExecutedQty: 950
  }
];

// CHANTIER SYNCHRONISÉ AVEC LE LOT 01
const INITIAL_CHANTIERS: Chantier[] = [
  {
    id: 'ch-ecole-sba',
    lotId: 'lot-ecole-01',
    marketId: 'm-ecole-sba',
    name: 'Chantier Groupe Scolaire Base 3 DEP (École Primaire)',
    wilaya: '22',
    address: 'Quartier Rocade Sud, Sidi Bel Abbès',
    chefChantierName: 'Kamel Meziane',
    chefChantierPhone: '0550 44 55 66',
    physicalProgressPct: 45,
    financialProgressPct: 38,
    status: 'Actif',
    startDate: '2026-02-01',
    weatherNote: 'Ensoleillé 24°C - Coulage Poteaux R+1'
  },
  {
    id: 'ch-sba-01',
    lotId: 'lot-01',
    marketId: 'm-r5',
    name: 'Chantier Projet R+5 (Lot 01 Gros Œuvres)',
    wilaya: '22',
    address: 'Cité El Moudjahidine, Commune de Sidi Bel Abbès',
    chefChantierName: 'Mourad Belkacem',
    chefChantierPhone: '0550 12 34 56',
    physicalProgressPct: 45,
    financialProgressPct: 38,
    status: 'Actif',
    startDate: '2026-01-20',
    weatherNote: 'Ensoleillé 22°C - Travaux RDC & VRD'
  },
  {
    id: 'ch-oran-01',
    lotId: 'lot-01',
    marketId: 'm-oran-vrd',
    name: 'Chantier Aménagement Urbain Bir El Djir',
    wilaya: '31',
    address: 'Bir El Djir, Oran',
    chefChantierName: 'Farid Mansouri',
    chefChantierPhone: '0661 78 90 12',
    physicalProgressPct: 54,
    financialProgressPct: 48,
    status: 'Actif',
    startDate: '2025-12-05'
  }
];

// ATTACHEMENTS (AVEC ATTACHEMENT N°2 PRÊT POUR LE SCÉNARIO VIDÉO)
const INITIAL_ATTACHEMENTS: Attachement[] = [
  // ATTACHEMENT N° 1 ÉCOLE 125M DA (DEP SIDI BEL ABBÈS) - VALIDÉ
  {
    id: 'att-ecole-01',
    marketId: 'm-ecole-sba',
    chantierId: 'ch-ecole-sba',
    number: 1,
    periodStart: '2026-02-01',
    periodEnd: '2026-02-28',
    zone: 'Zone 1 : Terrassements, Plateforme & Fondations Bloc Pédagogique',
    status: 'Validé',
    createdBy: 'Kamel Meziane (Chef de Chantier)',
    createdAt: '2026-02-28',
    validatedAt: '2026-03-03',
    items: [
      {
        id: 'item-ec-01',
        articleId: 'art-ec-01',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 1850,
        observation: 'Fouilles conformes aux sondages de sol'
      },
      {
        id: 'item-ec-02',
        articleId: 'art-ec-02',
        mode: 'MetreDetaille',
        metreLines: [
          { id: 'ml-ec-01', label: 'Béton propreté sous semelles S1 à S18', count: 18, length: 2.5, width: 2.5, height: 0.1, factor: 1, subtotal: 11.25 },
          { id: 'ml-ec-02', label: 'Béton propreté sous longrines', count: 1, length: 580, width: 0.6, height: 0.1, factor: 1, subtotal: 34.8 }
        ],
        currentQuantity: 160,
        observation: 'Béton dosé à 150 kg/m³ validé par BET'
      },
      {
        id: 'item-ec-03',
        articleId: 'art-ec-03',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 280,
        observation: 'Gros béton de calage'
      },
      {
        id: 'item-ec-04',
        articleId: 'art-ec-04',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 520,
        observation: 'Semelles isolées et longrines coulées'
      }
    ]
  },
  // ATTACHEMENT N° 2 ÉCOLE 125M DA - EN COURS
  {
    id: 'att-ecole-02',
    marketId: 'm-ecole-sba',
    chantierId: 'ch-ecole-sba',
    number: 2,
    periodStart: '2026-03-01',
    periodEnd: '2026-03-31',
    zone: 'Zone 2 : Poteaux & Dalles R+1 + Clôture',
    status: 'Brouillon',
    createdBy: 'Kamel Meziane (Chef de Chantier)',
    createdAt: '2026-03-31',
    items: [
      {
        id: 'item-ec-21',
        articleId: 'art-ec-05',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 450,
        observation: 'Poteaux et poutres R+1 achevés',
        hasExceedance: false,
        exceedanceQty: 0
      },
      {
        id: 'item-ec-22',
        articleId: 'art-ec-06',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 1100,
        observation: 'Plancher haut RDC coulé avec treillis soudé',
        hasExceedance: false,
        exceedanceQty: 0
      }
    ]
  },
  {
    id: 'att-01',
    marketId: 'm-r5',
    chantierId: 'ch-sba-01',
    number: 1,
    periodStart: '2026-01-20',
    periodEnd: '2026-02-28',
    zone: 'Zone 1 : Terrassement & Fondations Bloc A',
    status: 'Validé',
    createdBy: 'Mourad Belkacem (Chef de Chantier)',
    createdAt: '2026-02-28',
    validatedAt: '2026-03-02',
    items: [
      {
        id: 'item-01',
        articleId: 'art-1-1',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 850,
        observation: 'Fouilles conformes aux plans BET'
      },
      {
        id: 'item-02',
        articleId: 'art-1-2',
        mode: 'MetreDetaille',
        metreLines: [
          { id: 'ml-01', label: 'Béton propreté sous semelles S1', count: 24, length: 2.2, width: 2.2, height: 0.1, factor: 1, subtotal: 11.62 },
          { id: 'ml-02', label: 'Béton propreté sous semelles S2', count: 24, length: 2.2, width: 2.2, height: 0.1, factor: 1, subtotal: 11.62 },
          { id: 'ml-03', label: 'Longrines de liaison', count: 1, length: 480, width: 0.5, height: 0.1, factor: 1, subtotal: 24.0 }
        ],
        currentQuantity: 120,
        observation: 'Coulage réceptionné par BET'
      },
      {
        id: 'item-03',
        articleId: 'art-1-3',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 380,
        observation: 'Semelles et longrines coulées'
      }
    ]
  },
  // ATTACHEMENT N° 2 : L'ATTACHEMENT CLÉ DE LA VIDÉO
  {
    id: 'att-02',
    marketId: 'm-r5',
    chantierId: 'ch-sba-01',
    number: 2,
    periodStart: '2026-03-01',
    periodEnd: '2026-03-31',
    zone: 'Zone 2 : Bloc A - RDC & Regards / VRD',
    status: 'Brouillon', // Doit être validé après déblocage avenant
    createdBy: 'Mourad Belkacem (Chef de Chantier)',
    createdAt: '2026-03-31',
    items: [
      // Article 4.2 : 2 saisis sur 3 prévus -> 66,67%
      {
        id: 'item-21',
        articleId: 'art-4-2',
        mode: 'Direct',
        metreLines: [],
        currentQuantity: 2,
        observation: 'Pose de 2 regards de visite avec tampon fonte sur 3 prévus',
        hasExceedance: false,
        exceedanceQty: 0
      },
      // Article 4.5 : 622 saisis sur 6 prévus -> DÉPASSEMENT CONTRACTUEL BLOQUANT
      {
        id: 'item-22',
        articleId: 'art-4-5',
        mode: 'Direct',
        metreLines: [
          { id: 'ml-22', label: 'Caniveaux préfabriqués axe principal', count: 622, length: 1, width: 1, height: 1, factor: 1, subtotal: 622, observation: 'Pose caniveaux' }
        ],
        currentQuantity: 622,
        observation: 'Simulation dépassement vidéo (622 ml posés)',
        hasExceedance: true,
        exceedanceQty: 616 // 622 - 6 = 616
      }
    ]
  }
];

// AVENANT N° 01 : RÉGULARISATION DE L'ARTICLE 4.5 (622 ml)
const INITIAL_AVENANTS: Avenant[] = [
  {
    id: 'av-01',
    marketId: 'm-r5',
    number: 1,
    title: 'Avenant N° 01 — Régularisation Dépassement Quantité Article 4.5',
    motif: 'Augmentation de la quantité de caniveaux préfabriqués de 6 à 622 ml suite à la modification du plan de VRD et d\'évacuation des eaux pluviales',
    date: '2026-04-02',
    status: 'Brouillon', // En attente de validation pour tester le déblocage
    initialMarketHT: 118433000,
    deltaAmountHT: 2464000, // 616 * 4000 DA
    newMarketHT: 120897000,
    incidencePct: 2.08,
    changes: [
      {
        articleId: 'art-4-5',
        code: '4.5',
        designation: 'Caniveaux préfabriqués d\'évacuation des eaux pluviales avec grille galvanisée',
        unit: 'ml',
        oldQty: 6,
        newQty: 622,
        deltaQty: 616,
        unitPriceHT: 4000,
        deltaAmountHT: 2464000,
        isNewArticle: false,
        reason: 'Régularisation des métrés contradictoires constatés sur chantier'
      }
    ]
  }
];

// SITUATIONS
const INITIAL_SITUATIONS: Situation[] = [
  // SITUATION N° 1 GROUPE SCOLAIRE 125M DA (DEP SIDI BEL ABBÈS) - VALIDÉE / PAYÉE
  {
    id: 'sit-ecole-01',
    marketId: 'm-ecole-sba',
    chantierId: 'ch-ecole-sba',
    number: 1,
    date: '2026-03-05',
    period: 'Du 01/02/2026 au 28/02/2026',
    attachedAttachementIds: ['att-ecole-01'],
    status: 'Validée / Payée',
    isDGD: false,
    items: [
      {
        articleId: 'art-ec-01',
        code: '1.01',
        designation: 'Fouilles en pleine masse et en rigoles école',
        unit: 'm³',
        contractQty: 1850,
        unitPriceHT: 750,
        prevQty: 0,
        monthQty: 1850,
        totalQty: 1850,
        prevAmountHT: 0,
        monthAmountHT: 1387500,
        totalAmountHT: 1387500,
        progressPct: 100
      },
      {
        articleId: 'art-ec-02',
        code: '1.02',
        designation: 'Béton de propreté dosé à 150 kg/m³',
        unit: 'm³',
        contractQty: 160,
        unitPriceHT: 9500,
        prevQty: 0,
        monthQty: 160,
        totalQty: 160,
        prevAmountHT: 0,
        monthAmountHT: 1520000,
        totalAmountHT: 1520000,
        progressPct: 100
      },
      {
        articleId: 'art-ec-03',
        code: '1.03',
        designation: 'Gros béton de calage dosé à 250 kg/m³',
        unit: 'm³',
        contractQty: 280,
        unitPriceHT: 13500,
        prevQty: 0,
        monthQty: 280,
        totalQty: 280,
        prevAmountHT: 0,
        monthAmountHT: 3780000,
        totalAmountHT: 3780000,
        progressPct: 100
      },
      {
        articleId: 'art-ec-04',
        code: '1.04',
        designation: 'Béton armé en fondations dosé à 350 kg/m³',
        unit: 'm³',
        contractQty: 520,
        unitPriceHT: 19500,
        prevQty: 0,
        monthQty: 520,
        totalQty: 520,
        prevAmountHT: 0,
        monthAmountHT: 10140000,
        totalAmountHT: 10140000,
        progressPct: 100
      }
    ],
    grossAmountPreviousHT: 0,
    grossAmountCurrentHT: 16827500,
    grossAmountCumulativeHT: 16827500,
    retenueGarantieHT: 841375, // 5%
    remboursementAvanceHT: 2524125, // 15%
    netHT: 13462000,
    tvaRate: 9,
    tvaAmount: 1211580,
    netToPayTTC: 14673580,
    netToPayWordsFR: numberToWordsDZ(14673580)
  },
  {
    id: 'sit-01',
    marketId: 'm-r5',
    chantierId: 'ch-sba-01',
    number: 1,
    date: '2026-03-05',
    period: 'Du 20/01/2026 au 28/02/2026',
    attachedAttachementIds: ['att-01'],
    status: 'Validée / Payée',
    isDGD: false,
    items: [
      {
        articleId: 'art-1-1',
        code: '1.1',
        designation: 'Fouilles en puits et en rigoles',
        unit: 'm³',
        contractQty: 850,
        unitPriceHT: 650,
        prevQty: 0,
        monthQty: 850,
        totalQty: 850,
        prevAmountHT: 0,
        monthAmountHT: 552500,
        totalAmountHT: 552500,
        progressPct: 100
      },
      {
        articleId: 'art-1-2',
        code: '1.2',
        designation: 'Béton de propreté dosé à 150 kg/m³',
        unit: 'm³',
        contractQty: 120,
        unitPriceHT: 8500,
        prevQty: 0,
        monthQty: 120,
        totalQty: 120,
        prevAmountHT: 0,
        monthAmountHT: 1020000,
        totalAmountHT: 1020000,
        progressPct: 100
      },
      {
        articleId: 'art-1-3',
        code: '1.3',
        designation: 'Béton armé en fondations dosé à 350 kg/m³',
        unit: 'm³',
        contractQty: 420,
        unitPriceHT: 16500,
        prevQty: 0,
        monthQty: 380,
        totalQty: 380,
        prevAmountHT: 0,
        monthAmountHT: 6270000,
        totalAmountHT: 6270000,
        progressPct: 90.48
      }
    ],
    grossAmountPreviousHT: 0,
    grossAmountCurrentHT: 7842500,
    grossAmountCumulativeHT: 7842500,
    retenueGarantieHT: 392125, // 5%
    remboursementAvanceHT: 1176375, // 15%
    netHT: 6274000,
    tvaRate: 9,
    tvaAmount: 564660,
    netToPayTTC: 6838660,
    netToPayWordsFR: numberToWordsDZ(6838660)
  }
];

const INITIAL_STOCKS: StockItem[] = [
  // STOCKS DU CHANTIER GROUPE SCOLAIRE 125M DA (DEP SIDI BEL ABBÈS)
  { id: 'stk-ec-01', chantierId: 'ch-ecole-sba', name: 'Ciment Gris CPJ 42.5 (Sacs 50kg)', category: 'Ciment', unit: 'Sac', quantityInStock: 1200, minThreshold: 300, unitPriceCost: 520, lastRestockedDate: '2026-03-24' },
  { id: 'stk-ec-02', chantierId: 'ch-ecole-sba', name: 'Rond à béton FeE500 Ø 12 mm & 14 mm', category: 'Acier', unit: 'Tonne', quantityInStock: 32.5, minThreshold: 8, unitPriceCost: 112000, lastRestockedDate: '2026-03-22' },
  { id: 'stk-ec-03', chantierId: 'ch-ecole-sba', name: 'Briques creuses 8 trous pour cloisons', category: 'Briques & Maçonnerie', unit: 'U', quantityInStock: 18500, minThreshold: 3000, unitPriceCost: 35, lastRestockedDate: '2026-03-26' },
  { id: 'stk-ec-04', chantierId: 'ch-ecole-sba', name: 'Gravier concassé 15/25', category: 'Granulats', unit: 'm³', quantityInStock: 120, minThreshold: 30, unitPriceCost: 1950, lastRestockedDate: '2026-03-28' },
  { id: 'stk-ec-05', chantierId: 'ch-ecole-sba', name: 'Sable d\'Oued lavé 0/5', category: 'Granulats', unit: 'm³', quantityInStock: 95, minThreshold: 25, unitPriceCost: 2200, lastRestockedDate: '2026-03-28' },
  // STOCKS DU CHANTIER R+5
  { id: 'stk-01', chantierId: 'ch-sba-01', name: 'Ciment Gris CPJ 42.5 (Sacs 50kg)', category: 'Ciment', unit: 'Sac', quantityInStock: 840, minThreshold: 200, unitPriceCost: 520, lastRestockedDate: '2026-03-25' },
  { id: 'stk-02', chantierId: 'ch-sba-01', name: 'Rond à béton FeE500 Ø 12 mm', category: 'Acier', unit: 'Tonne', quantityInStock: 24.5, minThreshold: 5, unitPriceCost: 112000, lastRestockedDate: '2026-03-20' },
  { id: 'stk-03', chantierId: 'ch-sba-01', name: 'Caniveaux préfabriqués 100x20', category: 'Granulats', unit: 'ml', quantityInStock: 650, minThreshold: 50, unitPriceCost: 3200, lastRestockedDate: '2026-03-28' },
  { id: 'stk-04', chantierId: 'ch-sba-01', name: 'Gravier concassé 15/25', category: 'Granulats', unit: 'm³', quantityInStock: 65, minThreshold: 20, unitPriceCost: 1950, lastRestockedDate: '2026-03-28' },
  { id: 'stk-05', chantierId: 'ch-sba-01', name: 'Sable d\'Oued lavé 0/5', category: 'Granulats', unit: 'm³', quantityInStock: 80, minThreshold: 25, unitPriceCost: 2200, lastRestockedDate: '2026-03-28' }
];

const INITIAL_WORKERS: Worker[] = [
  // ÉQUIPE COMPLÈTE GROUPE SCOLAIRE BASE 3 (DEP SIDI BEL ABBÈS - 125M DA)
  { id: 'w-ecole-01', chantierId: 'ch-ecole-sba', fullName: 'Kamel Meziane', trade: 'Chef de Chantier', dailyRateDA: 4800, cnasNumber: '198022005412', phone: '0550 44 55 66', status: 'Actif', daysWorkedCurrentMonth: 26 },
  { id: 'w-ecole-02', chantierId: 'ch-ecole-sba', fullName: 'Yacine Larbi', trade: 'Topographe', dailyRateDA: 4200, cnasNumber: '198622004781', phone: '0661 22 33 44', status: 'Actif', daysWorkedCurrentMonth: 25 },
  { id: 'w-ecole-03', chantierId: 'ch-ecole-sba', fullName: 'Hamid Boumedienne', trade: 'Maçon', dailyRateDA: 2800, cnasNumber: '198422008912', phone: '0770 12 34 56', status: 'Actif', daysWorkedCurrentMonth: 24 },
  { id: 'w-ecole-04', chantierId: 'ch-ecole-sba', fullName: 'Noureddine Khelifi', trade: 'Coffreur', dailyRateDA: 2900, cnasNumber: '198822003321', phone: '0552 66 77 88', status: 'Actif', daysWorkedCurrentMonth: 26 },
  { id: 'w-ecole-05', chantierId: 'ch-ecole-sba', fullName: 'Rachid Touati', trade: 'Ferrailleur', dailyRateDA: 2950, cnasNumber: '199122001155', phone: '0664 88 99 00', status: 'Actif', daysWorkedCurrentMonth: 25 },
  { id: 'w-ecole-06', chantierId: 'ch-ecole-sba', fullName: 'Mohamed Benyahia', trade: 'Conducteur d\'engin', dailyRateDA: 3500, cnasNumber: '198222009844', phone: '0772 33 44 55', status: 'Actif', daysWorkedCurrentMonth: 24 },
  { id: 'w-ecole-07', chantierId: 'ch-ecole-sba', fullName: 'Khaled Senhadji', trade: 'Électricien', dailyRateDA: 3000, cnasNumber: '199322007788', phone: '0558 11 22 44', status: 'Actif', daysWorkedCurrentMonth: 22 },
  { id: 'w-ecole-08', chantierId: 'ch-ecole-sba', fullName: 'Salim Belkacemi', trade: 'Plombier', dailyRateDA: 2900, cnasNumber: '199022006655', phone: '0667 99 88 11', status: 'Actif', daysWorkedCurrentMonth: 20 },
  { id: 'w-ecole-09', chantierId: 'ch-ecole-sba', fullName: 'Sofiane Drihem', trade: 'Manœuvre qualifié', dailyRateDA: 2000, cnasNumber: '199622002233', phone: '0559 44 33 22', status: 'Actif', daysWorkedCurrentMonth: 26 },
  { id: 'w-ecole-10', chantierId: 'ch-ecole-sba', fullName: 'Abdelkader Bouri', trade: 'Manœuvre ordinaire', dailyRateDA: 1800, cnasNumber: '199822004411', phone: '0774 55 66 77', status: 'Actif', daysWorkedCurrentMonth: 26 },
  // ÉQUIPE DU PROJET R+5
  { id: 'w-01', chantierId: 'ch-sba-01', fullName: 'Mourad Belkacem', trade: 'Chef de Chantier', dailyRateDA: 4500, cnasNumber: '197822001452', phone: '0550 12 34 56', status: 'Actif', daysWorkedCurrentMonth: 26 },
  { id: 'w-02', chantierId: 'ch-sba-01', fullName: 'Ahmed Bouziane', trade: 'Coffreur', dailyRateDA: 2800, cnasNumber: '198522003671', phone: '0662 33 44 55', status: 'Actif', daysWorkedCurrentMonth: 24 },
  { id: 'w-03', chantierId: 'ch-sba-01', fullName: 'Rabah Cherifi', trade: 'Ferrailleur', dailyRateDA: 2900, cnasNumber: '198322005892', phone: '0771 88 99 00', status: 'Actif', daysWorkedCurrentMonth: 25 },
  { id: 'w-04', chantierId: 'ch-sba-01', fullName: 'Karim Ziani', trade: 'Maçon', dailyRateDA: 2700, cnasNumber: '199022007812', phone: '0554 11 22 33', status: 'Actif', daysWorkedCurrentMonth: 22 },
  { id: 'w-05', chantierId: 'ch-sba-01', fullName: 'Bilal Senoussi', trade: 'Manœuvre qualifié', dailyRateDA: 1900, cnasNumber: '199422009021', phone: '0665 77 88 99', status: 'Actif', daysWorkedCurrentMonth: 26 }
];

const INITIAL_PURCHASE_ORDERS: PurchaseOrder[] = [
  {
    id: 'po-01',
    chantierId: 'ch-sba-01',
    orderNumber: 'BC-2026-042',
    supplierName: 'SARL Cimenterie de l\'Ouest / GICA',
    date: '2026-03-22',
    totalAmountHT: 520000,
    status: 'Livré sur chantier',
    items: [{ description: 'Ciment CPJ 42.5 (1000 sacs)', quantity: 1000, unit: 'Sac', unitPrice: 520, total: 520000 }]
  },
  {
    id: 'po-02',
    chantierId: 'ch-sba-01',
    orderNumber: 'BC-2026-043',
    supplierName: 'EURL Sider Algérie Ferraillage',
    date: '2026-03-24',
    totalAmountHT: 4480000,
    status: 'Commandé',
    items: [{ description: 'Acier tor FeE500 assorti (Ø10, 12, 14)', quantity: 40, unit: 'Tonne', unitPrice: 112000, total: 4480000 }]
  }
];

const INITIAL_EQUIPMENT: MaterialEquipment[] = [
  { id: 'eq-01', chantierId: 'ch-sba-01', name: 'Grue à tour Potain 45m', type: 'Grue', matricule: 'GT-08-DZ', status: 'Opérationnel', driverName: 'Abdelkader Zenati' },
  { id: 'eq-02', chantierId: 'ch-sba-01', name: 'Centrale à béton mobile 750L', type: 'Bétonnière', matricule: 'CB-22-SBA', status: 'Opérationnel', driverName: 'Khaled B.' },
  { id: 'eq-03', chantierId: 'ch-sba-01', name: 'Camion benne Sonacome 15T', type: 'Camion benne', matricule: '01245-116-22', status: 'Opérationnel', driverName: 'Hocine M.' }
];

const INITIAL_CHEQUES: ChequePayment[] = [
  // RÈGLEMENT SITUATION N° 1 ÉCOLE 125M DA PAR LA DIRECTION DES ÉQUIPEMENTS PUBLICS
  {
    id: 'chq-ec-01',
    marketId: 'm-ecole-sba',
    chantierId: 'ch-ecole-sba',
    type: 'Reçu',
    chequeNumber: 'MANDAT-2026/884',
    bank: 'BNA',
    amountDA: 14673580,
    issuerOrBeneficiary: 'Trésor Public / DEP Sidi Bel Abbès (Règlement Sit N° 01 École)',
    issueDate: '2026-03-12',
    depositDate: '2026-03-14',
    cashingDate: '2026-03-18',
    status: 'Encaissé',
    notes: 'Mandatement Trésorerie de Wilaya Sidi Bel Abbès'
  },
  {
    id: 'chq-01',
    marketId: 'm-r5',
    chantierId: 'ch-sba-01',
    type: 'Reçu',
    chequeNumber: 'CHQ-8921475',
    bank: 'BNA',
    amountDA: 6838660,
    issuerOrBeneficiary: 'Trésor Public / OPGI Sidi Bel Abbès (Règlement Sit N° 01)',
    issueDate: '2026-03-18',
    depositDate: '2026-03-20',
    cashingDate: '2026-03-25',
    status: 'Encaissé',
    notes: 'Virement / Mandat Trésor N° 2026/91'
  },
  {
    id: 'chq-02',
    marketId: 'm-r5',
    chantierId: 'ch-sba-01',
    type: 'Émis',
    chequeNumber: 'CHQ-004512',
    bank: 'BNA',
    amountDA: 520000,
    issuerOrBeneficiary: 'SARL Cimenterie de l\'Ouest',
    issueDate: '2026-03-23',
    status: 'Déposé à l\'encaissement',
    notes: 'Règlement facture BC-2026-042'
  }
];

const INITIAL_EXPENSES: Expense[] = [
  // DÉPENSES CHANTIER GROUPE SCOLAIRE BASE 3
  { id: 'exp-ec-01', chantierId: 'ch-ecole-sba', marketId: 'm-ecole-sba', date: '2026-03-10', category: 'Matériaux', description: 'Fourniture de 25 tonnes rond à béton FeE500 Sider', amountDA: 2800000, paymentMode: 'Virement BTP' },
  { id: 'exp-ec-02', chantierId: 'ch-ecole-sba', marketId: 'm-ecole-sba', date: '2026-03-15', category: 'Main-d\'œuvre', description: 'Paie quinzaine ouvriers et coffreurs école', amountDA: 1240000, paymentMode: 'Chèque' },
  { id: 'exp-ec-03', chantierId: 'ch-ecole-sba', marketId: 'm-ecole-sba', date: '2026-03-22', category: 'Carburant & Engins', description: 'Gasoil centrale à béton et grue Potain', amountDA: 145000, paymentMode: 'Espèces (Caisse)' },
  { id: 'exp-01', chantierId: 'ch-sba-01', marketId: 'm-r5', date: '2026-03-24', category: 'Matériaux', description: 'Fourniture de caniveaux préfabriqués et tampons fonte', amountDA: 1250000, paymentMode: 'Virement BTP' },
  { id: 'exp-02', chantierId: 'ch-sba-01', marketId: 'm-r5', date: '2026-03-26', category: 'Carburant & Engins', description: 'Gasoil engins de pose et pelle hydraulique', amountDA: 75000, paymentMode: 'Espèces (Caisse)' },
  { id: 'exp-03', chantierId: 'ch-sba-01', marketId: 'm-r5', date: '2026-03-28', category: 'Caisse Chantier', description: 'Petit outillage et raccordements VRD', amountDA: 32000, paymentMode: 'Espèces (Caisse)' }
];

interface BTPContextType {
  markets: Market[];
  lots: Lot[];
  sections: Section[];
  articles: DQEArticle[];
  chantiers: Chantier[];
  attachements: Attachement[];
  avenants: Avenant[];
  situations: Situation[];
  stocks: StockItem[];
  purchaseOrders: PurchaseOrder[];
  equipment: MaterialEquipment[];
  cheques: ChequePayment[];
  workers: Worker[];
  expenses: Expense[];

  selectedMarketId: string;
  setSelectedMarketId: (id: string) => void;
  lang: 'fr' | 'ar';
  setLang: (lang: 'fr' | 'ar') => void;

  // Actions Marchés & DQE
  addMarket: (market: Market) => void;
  updateMarket: (market: Market) => void;
  addLot: (lot: Lot) => void;
  addSection: (section: Section) => void;
  addArticle: (article: DQEArticle) => void;
  updateArticle: (article: DQEArticle) => void;
  deleteArticle: (articleId: string) => void;

  // Actions Attachements
  addAttachement: (attachement: Attachement) => void;
  updateAttachement: (attachement: Attachement) => void;
  validateAttachement: (attachementId: string) => void;

  // Actions Avenants & Validation Synchronisée
  createAvenantFromExceedance: (marketId: string, articleId: string, requestedQty: number) => string;
  addAvenant: (avenant: Avenant) => void;
  validateAvenant: (avenantId: string) => void;

  // Actions Situations
  generateSituationFromAttachements: (marketId: string, attachementIds: string[]) => string;
  validateSituation: (situationId: string) => void;

  // Actions Chantier / Ouvriers / Dépenses / Chèques
  addChantier: (chantier: Chantier) => void;
  addStockItem: (item: StockItem) => void;
  updateStockQuantity: (id: string, delta: number) => void;
  addCheque: (cheque: ChequePayment) => void;
  updateChequeStatus: (id: string, status: ChequePayment['status']) => void;
  addExpense: (expense: Expense) => void;
  addWorker: (worker: Worker) => void;
  updateWorker: (worker: Worker) => void;
  deleteWorker: (id: string) => void;
  updateWorkerDays: (id: string, days: number) => void;

  // Scénarios Démo Vidéo
  resetToVideoScenario: () => void;
  resetData: () => void;
}

const BTPContext = createContext<BTPContextType | undefined>(undefined);

const LOCAL_STORAGE_KEY = 'archilab_manager_dz_v3';

export const BTPProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [lang, setLang] = useState<'fr' | 'ar'>('fr');
  const [selectedMarketId, setSelectedMarketId] = useState<string>('m-ecole-sba');

  const [markets, setMarkets] = useState<Market[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_markets`);
    return saved ? JSON.parse(saved) : INITIAL_MARKETS;
  });

  const [lots, setLots] = useState<Lot[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_lots`);
    return saved ? JSON.parse(saved) : INITIAL_LOTS;
  });

  const [sections, setSections] = useState<Section[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_sections`);
    return saved ? JSON.parse(saved) : INITIAL_SECTIONS;
  });

  const [articles, setArticles] = useState<DQEArticle[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_articles`);
    return saved ? JSON.parse(saved) : INITIAL_ARTICLES;
  });

  const [chantiers, setChantiers] = useState<Chantier[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_chantiers`);
    return saved ? JSON.parse(saved) : INITIAL_CHANTIERS;
  });

  const [attachements, setAttachements] = useState<Attachement[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_attachements`);
    return saved ? JSON.parse(saved) : INITIAL_ATTACHEMENTS;
  });

  const [avenants, setAvenants] = useState<Avenant[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_avenants`);
    return saved ? JSON.parse(saved) : INITIAL_AVENANTS;
  });

  const [situations, setSituations] = useState<Situation[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_situations`);
    return saved ? JSON.parse(saved) : INITIAL_SITUATIONS;
  });

  const [stocks, setStocks] = useState<StockItem[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_stocks`);
    return saved ? JSON.parse(saved) : INITIAL_STOCKS;
  });

  const [purchaseOrders, setPurchaseOrders] = useState<PurchaseOrder[]>(INITIAL_PURCHASE_ORDERS);
  const [equipment, setEquipment] = useState<MaterialEquipment[]>(INITIAL_EQUIPMENT);
  const [cheques, setCheques] = useState<ChequePayment[]>(INITIAL_CHEQUES);
  const [workers, setWorkers] = useState<Worker[]>(() => {
    const saved = localStorage.getItem(`${LOCAL_STORAGE_KEY}_workers`);
    return saved ? JSON.parse(saved) : INITIAL_WORKERS;
  });
  const [expenses, setExpenses] = useState<Expense[]>(INITIAL_EXPENSES);

  // Sync state
  useEffect(() => {
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_markets`, JSON.stringify(markets));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_lots`, JSON.stringify(lots));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_sections`, JSON.stringify(sections));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_articles`, JSON.stringify(articles));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_chantiers`, JSON.stringify(chantiers));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_attachements`, JSON.stringify(attachements));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_avenants`, JSON.stringify(avenants));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_situations`, JSON.stringify(situations));
    localStorage.setItem(`${LOCAL_STORAGE_KEY}_workers`, JSON.stringify(workers));
  }, [markets, lots, sections, articles, chantiers, attachements, avenants, situations, workers]);

  const addMarket = (market: Market) => setMarkets(prev => [market, ...prev]);
  const updateMarket = (updated: Market) => setMarkets(prev => prev.map(m => m.id === updated.id ? updated : m));
  const addLot = (lot: Lot) => setLots(prev => [...prev, lot]);
  const addSection = (section: Section) => setSections(prev => [...prev, section]);
  const addArticle = (article: DQEArticle) => setArticles(prev => [...prev, article]);
  const updateArticle = (updated: DQEArticle) => setArticles(prev => prev.map(a => a.id === updated.id ? updated : a));
  const deleteArticle = (articleId: string) => setArticles(prev => prev.filter(a => a.id !== articleId));

  const addAttachement = (attachement: Attachement) => setAttachements(prev => [attachement, ...prev]);
  const updateAttachement = (updated: Attachement) => setAttachements(prev => prev.map(a => a.id === updated.id ? updated : a));

  const validateAttachement = (attachementId: string) => {
    setAttachements(prev =>
      prev.map(att => {
        if (att.id === attachementId) {
          return {
            ...att,
            status: 'Validé',
            validatedAt: new Date().toISOString().split('T')[0]
          };
        }
        return att;
      })
    );
  };

  /**
   * Action Clé : Création automatique d'un avenant depuis l'alerte de dépassement
   */
  const createAvenantFromExceedance = (marketId: string, articleId: string, requestedQty: number): string => {
    const market = markets.find(m => m.id === marketId);
    const article = articles.find(a => a.id === articleId);
    if (!market || !article) return '';

    const newAvenantNumber = avenants.filter(av => av.marketId === marketId).length + 1;
    const oldQty = article.currentContractQuantity;
    const newQty = requestedQty;
    const deltaQty = Math.max(0, newQty - oldQty);
    const deltaAmountHT = deltaQty * article.unitPriceHT;
    const initialMarketHT = market.currentAmountHT;
    const newMarketHT = initialMarketHT + deltaAmountHT;
    const incidencePct = (deltaAmountHT / initialMarketHT) * 100;

    const newAvenant: Avenant = {
      id: `av-${Date.now()}`,
      marketId,
      number: newAvenantNumber,
      title: `Avenant N° 0${newAvenantNumber} — Régularisation Dépassement Article ${article.code}`,
      motif: `Augmentation de la quantité contractuelle de l'article ${article.code} (${article.designation}) de ${oldQty} à ${newQty} ${article.unit} suite aux métrés contradictoires`,
      date: new Date().toISOString().split('T')[0],
      status: 'Brouillon',
      initialMarketHT,
      deltaAmountHT,
      newMarketHT,
      incidencePct,
      changes: [
        {
          articleId: article.id,
          code: article.code,
          designation: article.designation,
          unit: article.unit,
          oldQty,
          newQty,
          deltaQty,
          unitPriceHT: article.unitPriceHT,
          deltaAmountHT,
          isNewArticle: false,
          reason: 'Régularisation des métrés d\'exécution'
        }
      ]
    };

    setAvenants(prev => [newAvenant, ...prev]);
    return newAvenant.id;
  };

  const addAvenant = (avenant: Avenant) => setAvenants(prev => [avenant, ...prev]);

  /**
   * VALIDATION SYNCHRONISÉE DE L'AVENANT (LE MOMENT CLÉ DE LA VIDÉO À 05:25 - 05:55)
   * 1. L'Avenant passe à 'Validé'
   * 2. La quantité dans le DQE passe à 622
   * 3. L'alerte rouge de dépassement dans l'Attachement N° 2 disparaît automatiquement
   * 4. Le bouton "Valider l'Attachement" est débloqué !
   */
  const validateAvenant = (avenantId: string) => {
    const avenant = avenants.find(av => av.id === avenantId);
    if (!avenant) return;

    // 1. Statut avenant -> Validé
    setAvenants(prev =>
      prev.map(av =>
        av.id === avenantId
          ? {
              ...av,
              status: 'Validé',
              approvedByClientDate: new Date().toISOString().split('T')[0],
              approvedByBetDate: new Date().toISOString().split('T')[0]
            }
          : av
      )
    );

    // 2. Mettre à jour le DQE (l'article 4.5 passe de 6 à 622)
    setArticles(prevArticles => {
      let updated = [...prevArticles];
      avenant.changes.forEach(change => {
        const existingIdx = updated.findIndex(a => a.id === change.articleId);
        if (existingIdx !== -1) {
          const art = updated[existingIdx];
          const newDelta = art.avenantQuantityDelta + change.deltaQty;
          const newContractQty = art.initialQuantity + newDelta;
          updated[existingIdx] = {
            ...art,
            avenantQuantityDelta: newDelta,
            currentContractQuantity: newContractQty
          };
        }
      });
      return updated;
    });

    // 3. Montant du Marché mis à jour
    setMarkets(prev =>
      prev.map(m =>
        m.id === avenant.marketId
          ? { ...m, currentAmountHT: m.currentAmountHT + avenant.deltaAmountHT }
          : m
      )
    );

    // 4. Lève automatiquement l'alerte rouge de dépassement dans tous les attachements !
    setAttachements(prev =>
      prev.map(att => {
        if (att.marketId === avenant.marketId) {
          const updatedItems = att.items.map(item => {
            const change = avenant.changes.find(c => c.articleId === item.articleId);
            if (change && item.hasExceedance) {
              const newTotalContract = change.newQty;
              if (item.currentQuantity <= newTotalContract) {
                return {
                  ...item,
                  hasExceedance: false,
                  exceedanceQty: 0
                };
              }
            }
            return item;
          });
          return { ...att, items: updatedItems };
        }
        return att;
      })
    );
  };

  /**
   * GÉNÉRATION AUTOMATIQUE DE LA SITUATION (05:55 -> 07:28)
   */
  const generateSituationFromAttachements = (marketId: string, attachementIds: string[]): string => {
    const market = markets.find(m => m.id === marketId);
    if (!market) return '';

    const selectedAtts = attachements.filter(a => attachementIds.includes(a.id));
    const marketArticles = articles.filter(a => a.marketId === marketId);
    const prevSituations = situations.filter(s => s.marketId === marketId);
    const sitNumber = prevSituations.length + 1;

    const lastSituation = prevSituations.length > 0 ? prevSituations[prevSituations.length - 1] : null;
    const grossAmountPreviousHT = lastSituation ? lastSituation.grossAmountCumulativeHT : 0;

    const items: SituationItemLine[] = [];
    let grossAmountCurrentHT = 0;

    marketArticles.forEach(art => {
      let monthQty = 0;
      selectedAtts.forEach(att => {
        const item = att.items.find(i => i.articleId === art.id);
        if (item) monthQty += item.currentQuantity;
      });

      const prevQty = art.previousExecutedQty || 0;
      const totalQty = prevQty + monthQty;
      const prevAmountHT = prevQty * art.unitPriceHT;
      const monthAmountHT = monthQty * art.unitPriceHT;
      const totalAmountHT = totalQty * art.unitPriceHT;
      const progressPct = art.currentContractQuantity > 0 ? Math.min(100, (totalQty / art.currentContractQuantity) * 100) : 0;

      grossAmountCurrentHT += monthAmountHT;

      items.push({
        articleId: art.id,
        code: art.code,
        designation: art.designation,
        unit: art.unit,
        contractQty: art.currentContractQuantity,
        unitPriceHT: art.unitPriceHT,
        prevQty,
        monthQty,
        totalQty,
        prevAmountHT,
        monthAmountHT,
        totalAmountHT,
        progressPct: Math.round(progressPct * 10) / 10
      });
    });

    const grossAmountCumulativeHT = grossAmountPreviousHT + grossAmountCurrentHT;
    const retenueGarantieHT = Math.round(grossAmountCurrentHT * (market.retenueGarantieRate / 100) * 100) / 100;
    const remboursementAvanceHT = 0; // Aucun remboursement sur cette tranche
    const netHT = grossAmountCurrentHT - retenueGarantieHT - remboursementAvanceHT;
    const tvaAmount = Math.round(netHT * (market.tvaRate / 100) * 100) / 100;
    const netToPayTTC = Math.round((netHT + tvaAmount) * 100) / 100;
    const netToPayWordsFR = numberToWordsDZ(netToPayTTC);

    const newSituation: Situation = {
      id: `sit-0${sitNumber}`,
      marketId,
      chantierId: selectedAtts[0]?.chantierId || 'ch-sba-01',
      number: sitNumber,
      date: new Date().toISOString().split('T')[0],
      period: `Situation Mensuelle N° 0${sitNumber} (${new Date().toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })})`,
      attachedAttachementIds: attachementIds,
      status: 'Brouillon',
      isDGD: false,
      items,
      grossAmountPreviousHT,
      grossAmountCurrentHT,
      grossAmountCumulativeHT,
      retenueGarantieHT,
      remboursementAvanceHT,
      netHT,
      tvaRate: market.tvaRate,
      tvaAmount,
      netToPayTTC,
      netToPayWordsFR
    };

    setSituations(prev => [newSituation, ...prev]);
    return newSituation.id;
  };

  const validateSituation = (situationId: string) => {
    setSituations(prev =>
      prev.map(s => (s.id === situationId ? { ...s, status: 'Validée / Payée' } : s))
    );
  };

  const addCheque = (cheque: ChequePayment) => setCheques(prev => [cheque, ...prev]);
  const updateChequeStatus = (id: string, status: ChequePayment['status']) => {
    setCheques(prev => prev.map(c => c.id === id ? { ...c, status } : c));
  };
  const addStockItem = (item: StockItem) => setStocks(prev => [...prev, item]);
  const updateStockQuantity = (id: string, delta: number) => {
    setStocks(prev => prev.map(s => s.id === id ? { ...s, quantityInStock: Math.max(0, s.quantityInStock + delta) } : s));
  };
  const addExpense = (expense: Expense) => setExpenses(prev => [expense, ...prev]);
  const addChantier = (chantier: Chantier) => setChantiers(prev => [chantier, ...prev]);
  const addWorker = (worker: Worker) => setWorkers(prev => [worker, ...prev]);
  const updateWorker = (updated: Worker) => setWorkers(prev => prev.map(w => w.id === updated.id ? updated : w));
  const deleteWorker = (id: string) => setWorkers(prev => prev.filter(w => w.id !== id));
  const updateWorkerDays = (id: string, days: number) => {
    setWorkers(prev => prev.map(w => w.id === id ? { ...w, daysWorkedCurrentMonth: days } : w));
  };

  const resetToVideoScenario = () => {
    localStorage.clear();
    setMarkets(INITIAL_MARKETS);
    setLots(INITIAL_LOTS);
    setSections(INITIAL_SECTIONS);
    setArticles(INITIAL_ARTICLES);
    setChantiers(INITIAL_CHANTIERS);
    setAttachements(INITIAL_ATTACHEMENTS);
    setAvenants(INITIAL_AVENANTS);
    setSituations(INITIAL_SITUATIONS);
    setStocks(INITIAL_STOCKS);
    setWorkers(INITIAL_WORKERS);
    setExpenses(INITIAL_EXPENSES);
    setSelectedMarketId('m-ecole-sba');
  };

  return (
    <BTPContext.Provider
      value={{
        markets,
        lots,
        sections,
        articles,
        chantiers,
        attachements,
        avenants,
        situations,
        stocks,
        purchaseOrders,
        equipment,
        cheques,
        workers,
        expenses,
        selectedMarketId,
        setSelectedMarketId,
        lang,
        setLang,
        addMarket,
        updateMarket,
        addLot,
        addSection,
        addArticle,
        updateArticle,
        deleteArticle,
        addAttachement,
        updateAttachement,
        validateAttachement,
        createAvenantFromExceedance,
        addAvenant,
        validateAvenant,
        generateSituationFromAttachements,
        validateSituation,
        addStockItem,
        updateStockQuantity,
        addCheque,
        updateChequeStatus,
        addExpense,
        addChantier,
        addWorker,
        updateWorker,
        deleteWorker,
        updateWorkerDays,
        resetToVideoScenario,
        resetData: resetToVideoScenario
      }}
    >
      {children}
    </BTPContext.Provider>
  );
};

export const useBTP = () => {
  const context = useContext(BTPContext);
  if (!context) throw new Error('useBTP must be used within a BTPProvider');
  return context;
};
