// BTP Manager Pro DZ — Types & Interfaces Définition Métier Algérie

export type WilayaCode = string;

export interface Wilaya {
  code: string;
  name: string;
  nameAr: string;
  region: 'Centre' | 'Est' | 'Ouest' | 'Sud';
}

export interface Tender {
  id: string;
  reference: string;
  title: string;
  client: string; // Maître d'ouvrage (ex: OPGI, DLEP, APC, Direction des Équipements...)
  wilaya: string;
  submissionDate: string;
  estimatedAmountHT: number;
  cautionSoumission: number;
  status: 'En préparation' | 'Soumis' | 'Retenu' | 'Infructueux';
  openingDate?: string;
  notes?: string;
}

export type MarketType = 'Marché Public' | 'Convention' | 'Contrat' | 'Bon de Commande';
export type MarketStatus = 'En cours' | 'En arrêt' | 'Réception Provisoire' | 'Réception Définitive' | 'Clôturé' | 'Archivé';

export type GuaranteeModality = 
  | 'caution_bonne_execution' 
  | 'retenue_garantie' 
  | 'both' 
  | 'none';

export interface Market {
  id: string;
  type: MarketType;
  number: string;
  title: string;
  client: string; // Maître d'ouvrage
  bet: string; // Bureau d'Études / Maître d'œuvre
  enterpriseName: string;
  wilaya: string;
  commune: string;
  signatureDate: string;
  odsDate: string; // Ordre de Service de démarrage
  durationMonths: number;
  status: MarketStatus;
  
  // Paramètres financiers réglementaires algériens
  tvaRate: 9 | 19; // 9% pour le logement social/public et travaux spécifiques, 19% régime général
  retenueGarantieRate: 0 | 5 | 10; // 5% standard marchés publics DZ, 0% si caution seule
  guaranteeModality?: GuaranteeModality;
  avanceForfaitaireRate: number; // 15% max
  avanceApproRate: number; // jusqu'à 35%
  
  initialAmountHT: number;
  currentAmountHT: number; // Mis à jour automatiquement après validation des avenants
  bankGuarantees: {
    soumission: number;
    bonneExecution: number;
    restitutionAvance: number;
    bankName: string;
    expiryDate: string;
  };
}

export interface DQEArticle {
  id: string;
  marketId: string;
  lotId: string;
  sectionId: string;
  code: string; // ex: '4.2', '4.5', '1.1'
  designation: string;
  unit: 'm³' | 'm²' | 'ml' | 'U' | 'Kg' | 'T' | 'Fft' | 'Ens';
  initialQuantity: number;
  avenantQuantityDelta: number; // somme algébrique des avenants validés
  currentContractQuantity: number; // initialQuantity + avenantQuantityDelta
  unitPriceHT: number;
  previousExecutedQty: number; // Cumul des situations précédentes
  totalExecutedQty: number; // Cumul total incluant attachements en cours
  isExtraArticle?: boolean; // Travaux nouveaux créés par avenant (hors bordereau initial)
}

export interface Section {
  id: string;
  lotId: string;
  code: string;
  name: string;
}

export interface Lot {
  id: string;
  marketId: string;
  code: string;
  name: string;
}

export interface Chantier {
  id: string;
  lotId?: string;
  marketId: string;
  name: string;
  wilaya: string;
  address: string;
  chefChantierName: string;
  chefChantierPhone: string;
  physicalProgressPct: number;
  financialProgressPct: number;
  status: 'Actif' | 'En attente' | 'Arrêté' | 'Livré';
  startDate: string;
  weatherNote?: string;
}

export interface MetreLine {
  id: string;
  label: string; // Désignation de l'ouvrage élémentaire
  count: number; // Nombre d'éléments similaires (N)
  length: number; // Longueur (L) en m
  width: number; // Largeur (l) en m
  height: number; // Hauteur ou Épaisseur (H) en m
  factor: number; // Multiplicateur (1 par défaut, -1 pour déduction)
  subtotal: number; // N × L × l × H × factor
  observation?: string;
}

export interface AttachementItem {
  id: string;
  articleId: string;
  mode: 'Direct' | 'MetreDetaille';
  metreLines: MetreLine[];
  currentQuantity: number; // Quantité totale réalisée sur cet attachement
  observation?: string;
  hasExceedance?: boolean;
  exceedanceQty?: number;
}

export type AttachementStatus = 'Brouillon' | 'Soumis Chef Chantier' | 'Validé';

export interface Attachement {
  id: string;
  marketId: string;
  chantierId: string;
  number: number;
  periodStart: string;
  periodEnd: string;
  zone: string; // ex: "Zone 1 : Bloc A - RDC", "Zone 2 : Regards & Réseaux"
  status: AttachementStatus;
  createdBy: string;
  createdAt: string;
  validatedAt?: string;
  items: AttachementItem[];
  notes?: string;
}

export type AvenantStatus = 'Brouillon' | 'En cours d\'approbation' | 'Validé' | 'Rejeté';

export interface AvenantChange {
  articleId: string;
  code: string;
  designation: string;
  unit: string;
  oldQty: number;
  newQty: number;
  deltaQty: number;
  unitPriceHT: number;
  deltaAmountHT: number;
  isNewArticle: boolean;
  reason?: string;
}

export interface Avenant {
  id: string;
  marketId: string;
  number: number;
  title: string;
  motif: string;
  date: string;
  status: AvenantStatus;
  initialMarketHT: number;
  deltaAmountHT: number;
  newMarketHT: number;
  incidencePct: number; // (deltaAmountHT / initialMarketHT) * 100
  changes: AvenantChange[];
  approvedByClientDate?: string;
  approvedByBetDate?: string;
}

export type SituationStatus = 'Brouillon' | 'Soumise' | 'Validée / Payée';

export interface SituationItemLine {
  articleId: string;
  code: string;
  designation: string;
  unit: string;
  contractQty: number;
  unitPriceHT: number;
  prevQty: number;
  monthQty: number;
  totalQty: number;
  prevAmountHT: number;
  monthAmountHT: number;
  totalAmountHT: number;
  progressPct: number;
}

export interface Situation {
  id: string;
  marketId: string;
  chantierId: string;
  number: number;
  date: string;
  period: string;
  attachedAttachementIds: string[];
  status: SituationStatus;
  isDGD: boolean; // Décompte Général et Définitif
  
  // Lignes détaillées
  items: SituationItemLine[];

  // Montants financiers certifiés
  grossAmountPreviousHT: number;
  grossAmountCurrentHT: number; // Montant brut du mois HT
  grossAmountCumulativeHT: number;
  
  retenueGarantieHT: number; // 5% du montant brut du mois
  remboursementAvanceHT: number; // Déduction éventuelle
  netHT: number; // grossAmountCurrentHT - retenueGarantieHT - remboursementAvanceHT
  tvaRate: number;
  tvaAmount: number;
  netToPayTTC: number; // netHT + tvaAmount
  netToPayWordsFR: string; // Montant en toutes lettres
}

// LOGISTIQUE, ACHATS, STOCKS & ENGINS BTP DZ
export type StockCategory = 
  | 'Bois'
  | 'Ciment'
  | 'Granulat'
  | 'Sable'
  | 'Plomberie'
  | 'Électricité'
  | 'Brique et bloc'
  | 'Revêtement sol'
  | 'Acier'
  | 'Autre'
  | 'Granulats'
  | 'Briques & Maçonnerie'
  | 'Plomberie & Élec'
  | 'Bois & Coffrage';

export type SteelDiameter = 6 | 8 | 10 | 12 | 16 | 20;

export type StockOperationStatus = 'Livré au projet' | 'Consommé' | 'Référence Catalogue';

export interface StockItem {
  id: string;
  chantierId: string;
  marketId?: string;
  name: string;
  category: StockCategory;
  unit: string; // Bois: plaque | Ciment: sac | Granulat/Sable: m3 | Plomberie: ml/Unité | Élec: m.lineaire/Unité | Brique: unité/m2 | Sol: m3/carton/m2 | Acier: barre/tonne
  steelDiameter?: SteelDiameter;
  quantityInStock: number; // Reste disponible sur chantier = Total Livré - Total Consommé
  totalDelivered?: number; // Quantité totale livrée au projet (BL)
  totalConsumed?: number; // Quantité totale consommée sur le projet (BS)
  minThreshold: number;
  unitPriceCost: number;
  lastRestockedDate: string;
  storageLocation?: string;
  supplier?: string;
  operationStatus?: StockOperationStatus;
  targetLotName?: string;
  targetArticleId?: string;
  referenceDoc?: string;
}

export interface StockMovement {
  id: string;
  stockItemId: string;
  stockItemName: string;
  chantierId: string;
  marketId?: string;
  type: 'Entrée (Réception BL)' | 'Sortie (Consommation)' | 'Inventaire & Ajustement';
  quantity: number;
  unit: string;
  date: string;
  referenceDoc?: string; // ex: BL N° 4512 / BS N° 821 / Bon de pesée / Bon de sortie coulage
  destinationOrSource?: string; // ex: Coulage Poteaux R+1 / GICA Cimenterie
  notes?: string;
  lotName?: string;
  articleId?: string;
  unitCostDA?: number;
  totalCostDA?: number;
}

export interface PurchaseOrder {
  id: string;
  chantierId: string;
  orderNumber: string;
  supplierName: string;
  date: string;
  totalAmountHT: number;
  status: 'Commandé' | 'Partiellement reçu' | 'Livré sur chantier' | 'Facturé';
  items: { description: string; quantity: number; unit: string; unitPrice: number; total: number }[];
}

export type EquipmentType = 
  | 'Rétrocaveuse'
  | 'Pelleteuse'
  | 'Grue à tour'
  | 'Bétonnière mobile'
  | 'Camion malaxeur (Toupie)'
  | 'Camion benne'
  | 'Compacteur'
  | 'Dumper'
  | 'Groupe électrogène'
  | 'Chariot télescopique'
  | 'Petit matériel'
  | 'Engin lourd'
  | 'Grue'
  | 'Bétonnière';

export type EquipmentStatus = 
  | 'Opérationnel'
  | 'En mission / Sur chantier'
  | 'En maintenance'
  | 'En panne'
  | 'À l\'arrêt'
  | 'Au dépôt central';

export interface MaterialEquipment {
  id: string;
  chantierId: string;
  name: string;
  brandModel?: string;
  type: EquipmentType;
  matricule: string;
  status: EquipmentStatus;
  driverName: string;
  driverPhone?: string;
  horametreHours?: number; // Heures moteur
  fuelLitersPerDay?: number; // Consommation gasoil L/j
  nextMaintenanceHours?: number; // Seuil vidange / révision
  dailyRentalCostDA?: number; // Coût de revient journalier en DA
}

// FINANCE & CHÈQUES ALGÉRIE
export type AlgerianBank = 'BNA' | 'BEA' | 'CPA' | 'BADR' | 'BDL' | 'CNEP-Banque' | 'Al Baraka' | 'AGB' | 'Housing Bank' | 'Société Générale' | 'Autre';

export interface ChequePayment {
  id: string;
  marketId?: string;
  chantierId?: string;
  type: 'Reçu' | 'Émis';
  chequeNumber: string;
  bank: AlgerianBank;
  amountDA: number;
  issuerOrBeneficiary: string;
  issueDate: string;
  depositDate?: string;
  cashingDate?: string;
  status: 'En portefeuille' | 'Déposé à l\'encaissement' | 'Encaissé' | 'Impayé / Rejeté';
  notes?: string;
}

export interface Expense {
  id: string;
  chantierId: string;
  marketId: string;
  date: string;
  category: 'Matériaux' | 'Main-d\'œuvre' | 'Carburant & Engins' | 'Frais Généraux' | 'Sous-traitance' | 'Caisse Chantier';
  description: string;
  amountDA: number;
  paymentMode: 'Chèque' | 'Virement BTP' | 'Espèces (Caisse)';
  referenceDoc?: string;
}

// PAIE & ÉQUIPES BTP DZ (CNAS / CACOBATPH)
export type WorkerTrade = 
  | 'Chef de Chantier' 
  | 'Maçon' 
  | 'Coffreur' 
  | 'Ferrailleur' 
  | 'Manœuvre qualifié' 
  | 'Manœuvre ordinaire' 
  | 'Conducteur d\'engin'
  | 'Électricien'
  | 'Plombier'
  | 'Topographe'
  | 'Peintre'
  | 'Soudeur'
  | 'Autre';

export interface Worker {
  id: string;
  chantierId: string;
  fullName: string;
  trade: WorkerTrade;
  dailyRateDA: number; // Salaire journalier de base
  cnasNumber: string;
  phone: string;
  status: 'Actif' | 'Congé' | 'Arrêt intempérie';
  daysWorkedCurrentMonth: number;
}
