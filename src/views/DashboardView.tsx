import React, { useState } from 'react';
import { 
  Building2, 
  Receipt, 
  AlertTriangle, 
  CheckCircle2, 
  ArrowUpRight, 
  TrendingUp, 
  FileText, 
  Scale, 
  Plus, 
  Search, 
  Landmark, 
  HardHat, 
  MapPin, 
  X, 
  Check, 
  Sparkles,
  FolderKanban,
  FileSpreadsheet,
  Archive,
  ArchiveRestore,
  Trash2,
  AlertOctagon
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';
import { ActiveTab } from '../components/Sidebar';
import { Market, MarketType, Lot, Chantier } from '../types/btp';

interface DashboardViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

const COMMON_CLIENT_SUGGESTIONS = [
  'Direction des Équipements Publics (DEP)',
  'OPGI (Office de Promotion et de Gestion Immobilière)',
  'Direction de l\'Urbanisme (DUC)',
  'Direction des Travaux Publics (DTP)',
  'Direction du Logement (DLEP)',
  'Direction des Ressources en Eau (DRE)',
  'APC (Assemblée Populaire Communale)',
  'Sonatrach / Groupe Public',
  'Promoteur Immobilier Privé'
];

export const DashboardView: React.FC<DashboardViewProps> = ({ onNavigate }) => {
  const { 
    markets, 
    attachements, 
    situations, 
    chantiers, 
    articles,
    selectedMarketId, 
    setSelectedMarketId, 
    addMarket, 
    addLot, 
    addChantier,
    deleteMarket,
    archiveMarket,
    unarchiveMarket
  } = useBTP();

  // Search & Client filter
  const [projectSearch, setProjectSearch] = useState('');
  const [selectedClientFilter, setSelectedClientFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'archived'>('active');

  // Modal State for New Project
  const [isAddingProject, setIsAddingProject] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newNumber, setNewNumber] = useState('');
  const [newType, setNewType] = useState<MarketType>('Marché Public');
  const [newClient, setNewClient] = useState('Direction des Équipements Publics (DEP)');
  const [newBet, setNewBet] = useState('BET Architecture & Urbanisme DZ');
  const [newEnterprise, setNewEnterprise] = useState('EGBTP ARCHILAB DZ');
  const [newWilaya, setNewWilaya] = useState('22');
  const [newCommune, setNewCommune] = useState('Sidi Bel Abbès');
  const [newInitialAmountHT, setNewInitialAmountHT] = useState<number>(65000000);
  const [newTvaRate, setNewTvaRate] = useState<9 | 19>(9);
  const [newRetenueGarantieRate, setNewRetenueGarantieRate] = useState<5 | 10>(5);
  const [newOdsDate, setNewOdsDate] = useState(new Date().toISOString().split('T')[0]);
  const [newDurationMonths, setNewDurationMonths] = useState<number>(12);

  // Modal State for Project Deletion Confirmation
  const [projectToDelete, setProjectToDelete] = useState<Market | null>(null);

  // Transient Toast Notification
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  const triggerNotice = (msg: string) => {
    setActionNotice(msg);
    setTimeout(() => setActionNotice(null), 4000);
  };

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];

  // Calculs financiers globaux (uniquement sur les marchés non-archivés par défaut pour la comptabilité active)
  const activeMarkets = markets.filter(m => m.status !== 'Archivé');
  const totalMarketsAmount = activeMarkets.reduce((acc, m) => acc + m.currentAmountHT, 0);
  const totalBilledHT = situations.reduce((acc, s) => acc + s.grossAmountCurrentHT, 0);
  const totalNetPaidTTC = situations.reduce((acc, s) => acc + s.netToPayTTC, 0);
  const totalRetenueGarantie = situations.reduce((acc, s) => acc + s.retenueGarantieHT, 0);
  const resteARealiser = Math.max(0, totalMarketsAmount - totalBilledHT);

  // Marché courant stats
  const currentMarketArticles = articles.filter(a => a.marketId === currentMarket?.id);
  const currentMarketChantier = chantiers.find(c => c.marketId === currentMarket?.id);

  const marketExecutedAmount = currentMarketArticles.reduce(
    (acc, a) => acc + (a.previousExecutedQty * a.unitPriceHT), 
    0
  );
  const currentPhysicalProgress = currentMarketChantier ? currentMarketChantier.physicalProgressPct : 35;
  const currentFinancialProgress = currentMarket && currentMarket.currentAmountHT > 0 
    ? Math.round((marketExecutedAmount / currentMarket.currentAmountHT) * 100) 
    : 0;

  // Liste unique des Maîtres d'Ouvrage
  const distinctClients = Array.from(new Set(markets.map(m => m.client.trim())));

  // Filtrage des marchés avec Statut (Actifs / Archivés / Tous)
  const filteredMarkets = markets.filter(m => {
    const matchesSearch = 
      m.title.toLowerCase().includes(projectSearch.toLowerCase()) ||
      m.number.toLowerCase().includes(projectSearch.toLowerCase()) ||
      m.client.toLowerCase().includes(projectSearch.toLowerCase()) ||
      m.commune.toLowerCase().includes(projectSearch.toLowerCase());

    const matchesClient = selectedClientFilter === 'all' || m.client.trim() === selectedClientFilter.trim();

    const matchesStatus = 
      statusFilter === 'all' ? true :
      statusFilter === 'active' ? m.status !== 'Archivé' :
      m.status === 'Archivé';

    return matchesSearch && matchesClient && matchesStatus;
  });

  // Groupement des marchés par Maître d'Ouvrage
  const clientsWithMarkets = distinctClients.map(client => {
    const clientMarkets = filteredMarkets.filter(m => m.client.trim() === client);
    const totalHT = clientMarkets.reduce((acc, m) => acc + m.currentAmountHT, 0);
    return {
      client,
      markets: clientMarkets,
      totalHT,
      count: clientMarkets.length
    };
  }).filter(group => group.count > 0);

  const activeCount = markets.filter(m => m.status !== 'Archivé').length;
  const archivedCount = markets.filter(m => m.status === 'Archivé').length;

  const handleToggleArchive = (market: Market) => {
    if (market.status === 'Archivé') {
      unarchiveMarket(market.id);
      triggerNotice(`Projet « ${market.title} » restauré avec succès.`);
    } else {
      archiveMarket(market.id);
      triggerNotice(`Projet « ${market.title} » archivé.`);
    }
  };

  const confirmDeleteProject = () => {
    if (!projectToDelete) return;
    const deletedTitle = projectToDelete.title;
    deleteMarket(projectToDelete.id);
    setProjectToDelete(null);
    triggerNotice(`Projet « ${deletedTitle} » supprimé définitivement.`);
  };

  const openAddProjectModal = () => {
    const randomNum = Math.floor(10 + Math.random() * 89);
    setNewNumber(`2026/${randomNum}/DEP/SBA`);
    setNewTitle('Projet de Réalisation — Complexe Scolaire & Sportif');
    setNewClient('Direction des Équipements Publics (DEP)');
    setNewBet('BET Architecture & Ingénierie DZ');
    setNewEnterprise('EGBTP ARCHILAB DZ');
    setNewWilaya('22');
    setNewCommune('Sidi Bel Abbès');
    setNewInitialAmountHT(75000000);
    setNewTvaRate(9);
    setNewRetenueGarantieRate(5);
    setNewOdsDate(new Date().toISOString().split('T')[0]);
    setNewDurationMonths(14);
    setIsAddingProject(true);
  };

  const handleCreateProjectSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newNumber.trim() || !newClient.trim()) return;

    const newMarketId = `m-${Date.now()}`;
    const initialAmount = Number(newInitialAmountHT) || 0;

    const newM: Market = {
      id: newMarketId,
      type: newType,
      number: newNumber.trim(),
      title: newTitle.trim(),
      client: newClient.trim(),
      bet: newBet.trim(),
      enterpriseName: newEnterprise.trim(),
      wilaya: newWilaya,
      commune: newCommune.trim(),
      signatureDate: newOdsDate,
      odsDate: newOdsDate,
      durationMonths: Number(newDurationMonths) || 12,
      status: 'En cours',
      tvaRate: newTvaRate,
      retenueGarantieRate: newRetenueGarantieRate,
      avanceForfaitaireRate: 15,
      avanceApproRate: 20,
      initialAmountHT: initialAmount,
      currentAmountHT: initialAmount,
      bankGuarantees: {
        soumission: Math.round(initialAmount * 0.01),
        bonneExecution: Math.round(initialAmount * 0.05),
        restitutionAvance: Math.round(initialAmount * 0.15),
        bankName: 'BNA Agence Centrale',
        expiryDate: '2027-12-31'
      }
    };

    const initialLot: Lot = {
      id: `lot-${Date.now()}`,
      marketId: newMarketId,
      code: 'LOT 01',
      name: 'Gros Œuvres, VRD & Aménagements'
    };

    const initialChantier: Chantier = {
      id: `ch-${Date.now()}`,
      lotId: initialLot.id,
      marketId: newMarketId,
      name: `Chantier ${newTitle.trim()}`,
      wilaya: newWilaya,
      address: `${newCommune.trim()}, Wilaya ${newWilaya}`,
      chefChantierName: 'Ingénieur Conducteur de Travaux',
      chefChantierPhone: '0550 12 34 56',
      physicalProgressPct: 5,
      financialProgressPct: 0,
      status: 'Actif',
      startDate: newOdsDate
    };

    addMarket(newM);
    addLot(initialLot);
    addChantier(initialChantier);
    setSelectedMarketId(newMarketId);
    setIsAddingProject(false);
    triggerNotice(`Nouveau projet « ${newTitle.trim()} » créé et activé.`);
  };

  const getWilayaName = (code: string) => {
    const w = ALGERIAN_WILAYAS.find(wil => wil.code === code);
    return w ? `${w.code} - ${w.name}` : code;
  };

  return (
    <div className="space-y-6">
      {/* NOTIFICATION TOAST FLOTTANTE */}
      {actionNotice && (
        <div className="p-3.5 rounded-2xl bg-slate-900 text-white text-xs font-bold flex items-center justify-between shadow-xl border border-slate-700 animate-in fade-in slide-in-from-top-2 duration-200">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>{actionNotice}</span>
          </div>
          <button 
            onClick={() => setActionNotice(null)} 
            className="text-slate-400 hover:text-white p-1"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* 1. TOP HEADER : PROPRIÉTÉS DU TABLEAU DE BORD & ACTIONS RAPIDES */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-blue-600 text-white shadow-xs">
              <Building2 className="w-4 h-4" />
            </span>
            <span className="text-xs font-black uppercase tracking-wider text-blue-700">
              ARCHILAB MANAGER • ERP BTP ALGÉRIE
            </span>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
              En Ligne
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Tableau de Bord Exécutif
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Supervision du portefeuille de projets, suivi d'avancement des chantiers et pilotage financier conforme au Code des Marchés Publics
          </p>
        </div>

        {/* Quick Actions */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={openAddProjectModal}
            className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-black flex items-center gap-1.5 shadow-sm transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Nouveau Projet</span>
          </button>

          <button
            onClick={() => onNavigate('ai-metre')}
            className="px-4 py-2 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white text-xs font-black flex items-center gap-1.5 shadow-sm transition-all cursor-pointer"
          >
            <Sparkles className="w-4 h-4 text-amber-200" />
            <span>Scanner Devis PDF</span>
          </button>
        </div>
      </div>

      {/* 2. LES 4 CARTES KPI ESSENTIELLES (ÉLÉGANTES ET ÉPURÉES) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Marchés */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-2 hover:border-blue-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Portefeuille Marchés HT</span>
            <span className="p-2 rounded-xl bg-blue-50 text-blue-600">
              <Building2 className="w-4 h-4" />
            </span>
          </div>
          <div>
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalMarketsAmount)}
            </div>
            <div className="flex items-center gap-2 mt-1 text-xs text-slate-500">
              <span className="font-bold text-blue-600">{activeCount} Projets Actifs</span>
              <span>•</span>
              <span>{distinctClients.length} Maîtres d'Ouvrage</span>
            </div>
          </div>
        </div>

        {/* Facturation Situations */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-2 hover:border-emerald-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Facturations Situations TTC</span>
            <span className="p-2 rounded-xl bg-emerald-50 text-emerald-600">
              <Receipt className="w-4 h-4" />
            </span>
          </div>
          <div>
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalNetPaidTTC)}
            </div>
            <div className="flex items-center gap-2 mt-1 text-xs text-slate-500">
              <span className="font-bold text-emerald-600 flex items-center gap-1">
                <CheckCircle2 className="w-3.5 h-3.5" />
                {situations.filter(s => s.status === 'Validée / Payée').length} Payées
              </span>
              <span>•</span>
              <span>{situations.length} Décomptes émis</span>
            </div>
          </div>
        </div>

        {/* Retenue de Garantie */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-2 hover:border-amber-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Retenues de Garantie (5%)</span>
            <span className="p-2 rounded-xl bg-amber-50 text-amber-600">
              <Scale className="w-4 h-4" />
            </span>
          </div>
          <div>
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalRetenueGarantie)}
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Consignées jusqu'à la Réception Définitive (1 an)
            </p>
          </div>
        </div>

        {/* Reste à Réaliser */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-2 hover:border-indigo-300 transition-colors">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Reste à Réaliser HT</span>
            <span className="p-2 rounded-xl bg-indigo-50 text-indigo-600">
              <TrendingUp className="w-4 h-4" />
            </span>
          </div>
          <div>
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(resteARealiser)}
            </div>
            <p className="text-xs text-slate-500 mt-1">
              Budget résiduel des marchés en cours
            </p>
          </div>
        </div>
      </div>

      {/* 3. PROJET ACTIF EN VEDETTE (AVEC BOUTONS ARCHIVER & SUPPRIMER) */}
      {currentMarket ? (
        <div className={`bg-white rounded-2xl border shadow-sm p-6 space-y-5 transition-all ${
          currentMarket.status === 'Archivé' ? 'border-amber-300 bg-amber-50/20' : 'border-blue-200'
        }`}>
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-slate-100">
            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="px-2.5 py-0.5 rounded-md bg-blue-600 text-white font-mono font-bold text-xs">
                  {currentMarket.number}
                </span>
                <span className={`text-xs font-bold px-2 py-0.5 rounded-md ${
                  currentMarket.status === 'Archivé' 
                    ? 'bg-amber-100 text-amber-900 border border-amber-300' 
                    : 'bg-emerald-100 text-emerald-800'
                }`}>
                  {currentMarket.status}
                </span>
                <span className="text-xs font-medium text-slate-600 flex items-center gap-1">
                  <Landmark className="w-3.5 h-3.5 text-blue-600" />
                  {currentMarket.client}
                </span>
                <span className="text-xs font-medium text-slate-500 flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-slate-400" />
                  {currentMarket.commune} ({getWilayaName(currentMarket.wilaya)})
                </span>
              </div>
              <h2 className="text-base sm:text-lg font-black text-slate-900 mt-2">
                {currentMarket.title}
              </h2>
            </div>

            {/* Quick action buttons for the current market */}
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => onNavigate('markets')}
                className="px-3 py-1.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold flex items-center gap-1.5 transition-colors"
              >
                <FileSpreadsheet className="w-4 h-4 text-blue-600" />
                <span>Marché & DQE</span>
              </button>

              <button
                onClick={() => onNavigate('chantiers')}
                className="px-3 py-1.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold flex items-center gap-1.5 transition-colors"
              >
                <HardHat className="w-4 h-4 text-amber-500" />
                <span>Chantier</span>
              </button>

              <button
                onClick={() => onNavigate('attachements')}
                className="px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-black flex items-center gap-1.5 shadow-sm transition-colors"
              >
                <FileText className="w-4 h-4" />
                <span>Attachements</span>
              </button>

              {/* BOUTON ARCHIVER / DÉSARCHIVER PROJET ACTIF */}
              <button
                onClick={() => handleToggleArchive(currentMarket)}
                title={currentMarket.status === 'Archivé' ? 'Désarchiver le projet' : 'Archiver le projet'}
                className={`px-3 py-1.5 rounded-xl border text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer ${
                  currentMarket.status === 'Archivé'
                    ? 'bg-amber-100 hover:bg-amber-200 text-amber-900 border-amber-300'
                    : 'border-slate-200 hover:bg-amber-50 hover:border-amber-200 text-slate-600 hover:text-amber-800'
                }`}
              >
                {currentMarket.status === 'Archivé' ? (
                  <>
                    <ArchiveRestore className="w-4 h-4 text-amber-700" />
                    <span>Désarchiver</span>
                  </>
                ) : (
                  <>
                    <Archive className="w-4 h-4 text-amber-600" />
                    <span>Archiver</span>
                  </>
                )}
              </button>

              {/* BOUTON SUPPRIMER PROJET ACTIF */}
              <button
                onClick={() => setProjectToDelete(currentMarket)}
                title="Supprimer définitivement le projet"
                className="px-3 py-1.5 rounded-xl border border-rose-200 hover:bg-rose-50 text-rose-600 text-xs font-bold flex items-center gap-1.5 transition-colors cursor-pointer"
              >
                <Trash2 className="w-4 h-4 text-rose-600" />
                <span>Supprimer</span>
              </button>
            </div>
          </div>

          {/* Dual Progress Gauges: Physique & Financier */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/60 space-y-2">
              <div className="flex items-center justify-between text-xs font-bold text-slate-700">
                <span className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                  Avancement Physique Constaté (Chantier)
                </span>
                <span className="text-sm font-black text-amber-600 font-mono">
                  {currentPhysicalProgress} %
                </span>
              </div>
              <div className="w-full bg-slate-200 rounded-full h-2.5 overflow-hidden">
                <div 
                  className="bg-amber-500 h-2.5 rounded-full transition-all duration-500"
                  style={{ width: `${currentPhysicalProgress}%` }}
                />
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/60 space-y-2">
              <div className="flex items-center justify-between text-xs font-bold text-slate-700">
                <span className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-blue-600" />
                  Avancement Financier Certifié (Situations)
                </span>
                <span className="text-sm font-black text-blue-600 font-mono">
                  {currentFinancialProgress} %
                </span>
              </div>
              <div className="w-full bg-slate-200 rounded-full h-2.5 overflow-hidden">
                <div 
                  className="bg-blue-600 h-2.5 rounded-full transition-all duration-500"
                  style={{ width: `${currentFinancialProgress}%` }}
                />
              </div>
            </div>
          </div>

          {/* Quick Metrics Bar */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center text-xs">
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/50">
              <span className="text-[10px] text-slate-400 uppercase font-bold block">Montant Initial HT</span>
              <strong className="text-xs sm:text-sm font-mono font-bold text-slate-900 mt-0.5 block">
                {formatDA(currentMarket.initialAmountHT)}
              </strong>
            </div>
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/50">
              <span className="text-[10px] text-slate-400 uppercase font-bold block">Montant Actuel HT</span>
              <strong className="text-xs sm:text-sm font-mono font-black text-blue-700 mt-0.5 block">
                {formatDA(currentMarket.currentAmountHT)}
              </strong>
            </div>
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/50">
              <span className="text-[10px] text-slate-400 uppercase font-bold block">Taux TVA / Retenue</span>
              <strong className="text-xs sm:text-sm font-bold text-slate-800 mt-0.5 block">
                TVA {currentMarket.tvaRate}% • RG {currentMarket.retenueGarantieRate}%
              </strong>
            </div>
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/50">
              <span className="text-[10px] text-slate-400 uppercase font-bold block">Caution Bonne Exécution (5%)</span>
              <strong className="text-xs sm:text-sm font-mono font-bold text-emerald-700 mt-0.5 block">
                {formatDA(currentMarket.bankGuarantees.bonneExecution)}
              </strong>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-dashed border-slate-300 p-8 text-center space-y-3">
          <p className="text-xs text-slate-500 font-medium">Aucun projet sélectionné ou disponible.</p>
          <button
            onClick={openAddProjectModal}
            className="px-4 py-2 bg-blue-600 text-white rounded-xl text-xs font-bold inline-flex items-center gap-1.5"
          >
            <Plus className="w-4 h-4" />
            <span>Créer un Nouveau Projet</span>
          </button>
        </div>
      )}

      {/* 4. PORTEFEUILLE PROJETS CLASSÉ PAR MAÎTRE D'OUVRAGE (AVEC ACTIONS ARCHIVER & SUPPRIMER) */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
          <div>
            <h2 className="text-sm font-black text-slate-900 flex items-center gap-2">
              <FolderKanban className="w-4 h-4 text-blue-600" />
              Portefeuille des Projets classé par Maître d'Ouvrage
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Sélectionnez, archivez ou supprimez vos projets en toute simplicité
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {/* Status Filter Toggle */}
            <div className="bg-slate-100 p-0.5 rounded-xl flex items-center text-xs font-bold">
              <button
                onClick={() => setStatusFilter('active')}
                className={`px-3 py-1 rounded-lg transition-all ${
                  statusFilter === 'active' 
                    ? 'bg-white text-blue-700 shadow-xs font-black' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Actifs ({activeCount})
              </button>
              <button
                onClick={() => setStatusFilter('archived')}
                className={`px-3 py-1 rounded-lg transition-all flex items-center gap-1 ${
                  statusFilter === 'archived' 
                    ? 'bg-white text-amber-800 shadow-xs font-black' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Archive className="w-3.5 h-3.5 text-amber-600" />
                <span>Archives ({archivedCount})</span>
              </button>
              <button
                onClick={() => setStatusFilter('all')}
                className={`px-3 py-1 rounded-lg transition-all ${
                  statusFilter === 'all' 
                    ? 'bg-white text-slate-900 shadow-xs font-black' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Tous ({markets.length})
              </button>
            </div>

            {/* Quick Search */}
            <div className="relative w-full sm:w-56">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Rechercher par titre, N°..."
                value={projectSearch}
                onChange={(e) => setProjectSearch(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 text-xs border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none"
              />
            </div>
          </div>
        </div>

        {/* Client Filter Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
          <button
            onClick={() => setSelectedClientFilter('all')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              selectedClientFilter === 'all'
                ? 'bg-slate-900 text-white shadow-xs'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            Tous les Maîtres d'Ouvrage ({filteredMarkets.length})
          </button>
          {distinctClients.map(client => {
            const count = filteredMarkets.filter(m => m.client.trim() === client).length;
            if (count === 0 && selectedClientFilter !== client) return null;
            return (
              <button
                key={client}
                onClick={() => setSelectedClientFilter(client)}
                className={`px-3 py-1.5 rounded-xl font-medium whitespace-nowrap transition-all flex items-center gap-1 ${
                  selectedClientFilter === client
                    ? 'bg-blue-600 text-white font-bold shadow-xs'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                <Landmark className="w-3 h-3 opacity-70" />
                <span className="truncate max-w-[150px]">{client}</span>
                <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-black/10 font-bold">{count}</span>
              </button>
            );
          })}
        </div>

        {/* Grouped Projects View */}
        <div className="space-y-4 pt-1">
          {clientsWithMarkets.length === 0 ? (
            <div className="p-8 text-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/50 space-y-2">
              <p className="text-xs text-slate-500 font-medium">
                Aucun projet ne correspond à vos filtres actuels.
              </p>
              {statusFilter === 'archived' && (
                <p className="text-[11px] text-slate-400">
                  Vous n'avez aucun projet dans les archives.
                </p>
              )}
            </div>
          ) : (
            clientsWithMarkets.map(group => (
              <div key={group.client} className="rounded-xl border border-slate-200 overflow-hidden bg-slate-50/20">
                <div className="bg-slate-100/80 px-4 py-2.5 border-b border-slate-200 flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <Landmark className="w-3.5 h-3.5 text-blue-700" />
                    <strong className="text-slate-900">{group.client}</strong>
                    <span className="px-2 py-0.2 rounded-full bg-blue-100 text-blue-800 text-[10px] font-bold">
                      {group.count} Marché{group.count > 1 ? 's' : ''}
                    </span>
                  </div>
                  <span className="font-mono font-bold text-slate-700 text-xs">
                    {formatDA(group.totalHT)}
                  </span>
                </div>

                <div className="divide-y divide-slate-100">
                  {group.markets.map(m => {
                    const isSelected = m.id === selectedMarketId;
                    const isArchived = m.status === 'Archivé';
                    const chantier = chantiers.find(c => c.marketId === m.id);
                    const physProgress = chantier ? chantier.physicalProgressPct : 20;

                    return (
                      <div 
                        key={m.id} 
                        className={`p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-colors ${
                          isArchived 
                            ? 'bg-amber-50/30 opacity-90' 
                            : isSelected 
                            ? 'bg-blue-50/60 font-semibold' 
                            : 'hover:bg-slate-50'
                        }`}
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-mono font-bold text-[11px] px-2 py-0.5 rounded bg-slate-900 text-white">
                              {m.number}
                            </span>
                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded ${
                              isArchived 
                                ? 'bg-amber-100 text-amber-900 border border-amber-300' 
                                : 'bg-emerald-100 text-emerald-800'
                            }`}>
                              {m.status}
                            </span>
                            {isSelected && (
                              <span className="text-[10px] font-black px-2 py-0.5 rounded bg-blue-600 text-white flex items-center gap-0.5">
                                <Check className="w-3 h-3" />
                                Actif
                              </span>
                            )}
                          </div>
                          <h4 className="text-xs sm:text-sm font-bold text-slate-900">
                            {m.title}
                          </h4>
                          <p className="text-[11px] text-slate-500 flex items-center gap-1">
                            <MapPin className="w-3 h-3 text-slate-400" />
                            {m.commune} ({getWilayaName(m.wilaya)})
                          </p>
                        </div>

                        <div className="flex items-center gap-4 self-end sm:self-auto shrink-0">
                          <div className="text-right">
                            <span className="text-[10px] text-slate-400 uppercase font-bold block">Montant HT</span>
                            <strong className="text-xs sm:text-sm font-mono font-bold text-slate-900 block">
                              {formatDA(m.currentAmountHT)}
                            </strong>
                            <span className="text-[10px] text-amber-600 font-mono font-bold block">
                              Avancement : {physProgress}%
                            </span>
                          </div>

                          {/* ACTION BUTTONS GROUP */}
                          <div className="flex items-center gap-1.5">
                            <button
                              onClick={() => setSelectedMarketId(m.id)}
                              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                                isSelected
                                  ? 'bg-blue-600 text-white cursor-default'
                                  : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                              }`}
                            >
                              {isSelected ? '✓ Actif' : 'Sélectionner'}
                            </button>

                            <button
                              onClick={() => {
                                setSelectedMarketId(m.id);
                                onNavigate('markets');
                              }}
                              className="px-2.5 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold"
                            >
                              DQE
                            </button>

                            {/* BOUTON ARCHIVER / DÉSARCHIVER */}
                            <button
                              onClick={() => handleToggleArchive(m)}
                              title={isArchived ? 'Désarchiver le projet' : 'Archiver le projet'}
                              className={`p-1.5 rounded-lg border transition-colors cursor-pointer ${
                                isArchived 
                                  ? 'bg-amber-100 hover:bg-amber-200 text-amber-800 border-amber-300' 
                                  : 'border-slate-200 hover:bg-amber-50 text-slate-500 hover:text-amber-700'
                              }`}
                            >
                              {isArchived ? (
                                <ArchiveRestore className="w-4 h-4 text-amber-700" />
                              ) : (
                                <Archive className="w-4 h-4" />
                              )}
                            </button>

                            {/* BOUTON SUPPRIMER */}
                            <button
                              onClick={() => setProjectToDelete(m)}
                              title="Supprimer définitivement le projet"
                              className="p-1.5 rounded-lg border border-slate-200 hover:border-rose-300 hover:bg-rose-50 text-slate-400 hover:text-rose-600 transition-colors cursor-pointer"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* 5. ACTIVITÉ RÉCENTE : ATTACHEMENTS & SITUATIONS */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Derniers Attachements */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
              <FileText className="w-4 h-4 text-blue-600" />
              Derniers Attachements Chantier
            </h3>
            <button
              onClick={() => onNavigate('attachements')}
              className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-0.5"
            >
              <span>Voir tout ({attachements.length})</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-2">
            {attachements.slice(0, 3).map(att => {
              const hasExceedance = att.items.some(i => i.hasExceedance);
              return (
                <div 
                  key={att.id}
                  className={`p-3 rounded-xl border flex items-center justify-between gap-3 text-xs transition-colors ${
                    hasExceedance ? 'bg-rose-50/60 border-rose-200' : 'bg-slate-50/60 border-slate-200/60'
                  }`}
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <strong className="text-slate-900">
                        Attachement N° {att.number.toString().padStart(2, '0')}
                      </strong>
                      <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-emerald-100 text-emerald-800">
                        {att.status}
                      </span>
                      {hasExceedance && (
                        <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-rose-600 text-white animate-pulse">
                          Dépassement !
                        </span>
                      )}
                    </div>
                    <span className="text-slate-500 text-[11px] block mt-0.5">
                      Zone : {att.zone} • {att.items.length} articles métrés
                    </span>
                  </div>

                  <button
                    onClick={() => onNavigate('attachements')}
                    className="px-2.5 py-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-700 font-bold shrink-0"
                  >
                    Ouvrir
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Dernières Situations */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
              <Receipt className="w-4 h-4 text-emerald-600" />
              Dernières Situations & Décomptes
            </h3>
            <button
              onClick={() => onNavigate('situations')}
              className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-0.5"
            >
              <span>Voir tout ({situations.length})</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-2">
            {situations.slice(0, 3).map(sit => (
              <div 
                key={sit.id}
                className="p-3 rounded-xl border border-slate-200/60 bg-slate-50/60 flex items-center justify-between gap-3 text-xs"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <strong className="text-slate-900">
                      Situation N° {sit.number.toString().padStart(2, '0')}
                    </strong>
                    <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-emerald-100 text-emerald-800">
                      {sit.status}
                    </span>
                  </div>
                  <span className="text-slate-500 text-[11px] block mt-0.5">
                    Net TTC : <strong className="text-slate-900 font-mono">{formatDA(sit.netToPayTTC)}</strong>
                  </span>
                </div>

                <button
                  onClick={() => onNavigate('situations')}
                  className="px-2.5 py-1 rounded-lg border border-slate-200 hover:bg-slate-100 text-slate-700 font-bold shrink-0"
                >
                  Consulter
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 6. MODAL DE CONFIRMATION DE SUPPRESSION D'UN PROJET */}
      {projectToDelete && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl border border-rose-200 shadow-2xl w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-5 bg-rose-50 border-b border-rose-100 flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-rose-600 text-white flex items-center justify-center shrink-0 shadow-md">
                <AlertOctagon className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-sm font-black text-rose-950">Supprimer Définitivement le Projet ?</h3>
                <p className="text-[11px] text-rose-700">Cette action est irréversible</p>
              </div>
            </div>

            <div className="p-5 space-y-3 text-xs">
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                <span className="font-mono text-[10px] font-bold px-2 py-0.5 rounded bg-slate-900 text-white">
                  {projectToDelete.number}
                </span>
                <h4 className="font-bold text-slate-900 text-xs sm:text-sm">
                  {projectToDelete.title}
                </h4>
                <p className="text-slate-500 text-[11px]">
                  Maître d'Ouvrage : <strong className="text-slate-700">{projectToDelete.client}</strong>
                </p>
                <p className="text-slate-500 text-[11px]">
                  Montant Contractuel : <strong className="text-slate-900 font-mono">{formatDA(projectToDelete.currentAmountHT)}</strong>
                </p>
              </div>

              <p className="text-slate-600 text-[11px] leading-relaxed">
                La suppression effacera l'ensemble du DQE, les lots, les fiches chantier et les attachements associés à ce projet.
              </p>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setProjectToDelete(null)}
                  className="px-4 py-2 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold transition-colors cursor-pointer"
                >
                  Annuler
                </button>
                <button
                  type="button"
                  onClick={confirmDeleteProject}
                  className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-black shadow-md transition-colors cursor-pointer flex items-center gap-1.5"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>Confirmer la Suppression</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 7. MODAL ÉPURÉE POUR CRÉER UN NOUVEAU PROJET */}
      {isAddingProject && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl border border-slate-200 shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-5 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2.5">
                <span className="p-2 rounded-xl bg-blue-100 text-blue-700">
                  <Building2 className="w-5 h-5" />
                </span>
                <div>
                  <h3 className="text-sm font-black text-slate-900">Nouveau Projet / Marché BTP</h3>
                  <p className="text-[11px] text-slate-500">Initialisation du marché et du chantier</p>
                </div>
              </div>
              <button 
                onClick={() => setIsAddingProject(false)}
                className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleCreateProjectSubmit} className="p-5 space-y-4 text-xs">
              <div className="space-y-1">
                <label className="font-bold text-slate-700">Intitulé du Projet</label>
                <input
                  type="text"
                  required
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="ex: Réalisation d'un Lycée 1000/300 Places"
                  className="w-full px-3 py-2 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none font-bold"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="font-bold text-slate-700">N° de Marché</label>
                  <input
                    type="text"
                    required
                    value={newNumber}
                    onChange={(e) => setNewNumber(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-mono font-bold"
                  />
                </div>

                <div className="space-y-1">
                  <label className="font-bold text-slate-700">Maître d'Ouvrage</label>
                  <input
                    type="text"
                    required
                    value={newClient}
                    onChange={(e) => setNewClient(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-medium"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="font-bold text-slate-700">Wilaya</label>
                  <select
                    value={newWilaya}
                    onChange={(e) => setNewWilaya(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-bold bg-white"
                  >
                    {ALGERIAN_WILAYAS.map(w => (
                      <option key={w.code} value={w.code}>{w.code} - {w.name}</option>
                    ))}
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="font-bold text-slate-700">Commune</label>
                  <input
                    type="text"
                    required
                    value={newCommune}
                    onChange={(e) => setNewCommune(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-medium"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="space-y-1">
                  <label className="font-bold text-slate-700">Montant HT (DA)</label>
                  <input
                    type="number"
                    required
                    value={newInitialAmountHT}
                    onChange={(e) => setNewInitialAmountHT(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-mono font-black"
                  />
                </div>

                <div className="space-y-1">
                  <label className="font-bold text-slate-700">Taux TVA</label>
                  <select
                    value={newTvaRate}
                    onChange={(e) => setNewTvaRate(parseInt(e.target.value) as 9 | 19)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-bold bg-white"
                  >
                    <option value={9}>9% (Social / Scolaire)</option>
                    <option value={19}>19% (Régime Général)</option>
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="font-bold text-slate-700">Durée (Mois)</label>
                  <input
                    type="number"
                    value={newDurationMonths}
                    onChange={(e) => setNewDurationMonths(parseInt(e.target.value) || 12)}
                    className="w-full px-3 py-2 border border-slate-200 rounded-xl font-bold"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingProject(false)}
                  className="px-4 py-2 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold transition-colors cursor-pointer"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-black shadow-md transition-colors cursor-pointer"
                >
                  Créer et Activer le Projet
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
