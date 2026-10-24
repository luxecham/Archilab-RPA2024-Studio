import React, { useState, useMemo } from 'react';
import { 
  Building2, 
  Plus, 
  Search, 
  Filter, 
  CheckCircle2, 
  AlertTriangle, 
  TrendingUp, 
  Receipt, 
  Scale, 
  Sparkles, 
  MapPin, 
  FileSpreadsheet, 
  Archive, 
  ArchiveRestore, 
  Trash2, 
  Edit3, 
  Check, 
  X, 
  Landmark, 
  Calendar, 
  Clock, 
  ShieldCheck, 
  FolderKanban,
  ExternalLink,
  Layers,
  ArrowRight,
  Eye,
  Star
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, ALGERIAN_WILAYAS, ALGERIAN_BANKS } from '../utils/dzFormatters';
import { ActiveTab } from '../components/Sidebar';
import { Market, MarketType, MarketStatus, GuaranteeModality, AlgerianBank } from '../types/btp';

interface ProjectsViewProps {
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

export const ProjectsView: React.FC<ProjectsViewProps> = ({ onNavigate }) => {
  const { 
    markets, 
    situations, 
    articles,
    selectedMarketId, 
    setSelectedMarketId,
    addMarket,
    updateMarket,
    deleteMarket,
    archiveMarket,
    unarchiveMarket
  } = useBTP();

  // Search & Filter state
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'archived' | 'en_cours' | 'arret' | 'reception'>('all');
  const [wilayaFilter, setWilayaFilter] = useState<string>('all');
  const [viewMode, setViewMode] = useState<'grid' | 'table'>('grid');

  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [targetMarket, setTargetMarket] = useState<Market | null>(null);

  // Success notification toast
  const [notification, setNotification] = useState<string | null>(null);

  // Form state (used for both Add and Edit)
  const [formNumber, setFormNumber] = useState('');
  const [formTitle, setFormTitle] = useState('');
  const [formType, setFormType] = useState<MarketType>('Marché Public');
  const [formClient, setFormClient] = useState('');
  const [formBet, setFormBet] = useState('');
  const [formEnterprise, setFormEnterprise] = useState('EGBTP ARCHILAB DZ');
  const [formWilaya, setFormWilaya] = useState('22');
  const [formCommune, setFormCommune] = useState('Sidi Bel Abbès');
  const [formDuration, setFormDuration] = useState(14);
  const [formSignatureDate, setFormSignatureDate] = useState('2026-01-15');
  const [formOdsDate, setFormOdsDate] = useState('2026-02-01');
  const [formAmountHT, setFormAmountHT] = useState(125000000);
  const [formTvaRate, setFormTvaRate] = useState<9 | 19>(9);
  const [formStatus, setFormStatus] = useState<MarketStatus>('En cours');
  const [formGuaranteeModality, setFormGuaranteeModality] = useState<GuaranteeModality>('caution_bonne_execution');
  const [formBankName, setFormBankName] = useState<AlgerianBank>('BNA');

  const showNotification = (msg: string) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 3500);
  };

  const getWilayaName = (code: string) => {
    const w = ALGERIAN_WILAYAS.find(item => item.code === code);
    return w ? `${w.code} - ${w.name}` : code;
  };

  // Open Edit Modal
  const handleOpenEdit = (m: Market) => {
    setTargetMarket(m);
    setFormNumber(m.number);
    setFormTitle(m.title);
    setFormType(m.type);
    setFormClient(m.client);
    setFormBet(m.bet);
    setFormEnterprise(m.enterpriseName);
    setFormWilaya(m.wilaya);
    setFormCommune(m.commune);
    setFormDuration(m.durationMonths);
    setFormSignatureDate(m.signatureDate);
    setFormOdsDate(m.odsDate);
    setFormAmountHT(m.initialAmountHT);
    setFormTvaRate(m.tvaRate);
    setFormStatus(m.status);
    setFormGuaranteeModality(m.guaranteeModality || (m.retenueGarantieRate === 0 ? 'caution_bonne_execution' : 'retenue_garantie'));
    setFormBankName((m.bankGuarantees?.bankName as AlgerianBank) || 'BNA');
    setIsEditModalOpen(true);
  };

  // Open Delete Confirmation
  const handleOpenDelete = (m: Market) => {
    setTargetMarket(m);
    setIsDeleteModalOpen(true);
  };

  // Submit Edit
  const handleSaveEdit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetMarket) return;

    const retenueRate = (formGuaranteeModality === 'retenue_garantie' || formGuaranteeModality === 'both') ? 5 : 0;
    const cautionAmount = (formGuaranteeModality === 'caution_bonne_execution' || formGuaranteeModality === 'both') 
      ? formAmountHT * 0.05 
      : 0;

    const updated: Market = {
      ...targetMarket,
      number: formNumber,
      title: formTitle,
      type: formType,
      client: formClient,
      bet: formBet,
      enterpriseName: formEnterprise,
      wilaya: formWilaya,
      commune: formCommune,
      durationMonths: Number(formDuration),
      signatureDate: formSignatureDate,
      odsDate: formOdsDate,
      initialAmountHT: Number(formAmountHT),
      currentAmountHT: Number(formAmountHT),
      tvaRate: formTvaRate,
      status: formStatus,
      retenueGarantieRate: retenueRate as 0 | 5 | 10,
      guaranteeModality: formGuaranteeModality,
      bankGuarantees: {
        soumission: targetMarket.bankGuarantees?.soumission || 0,
        bonneExecution: cautionAmount,
        restitutionAvance: targetMarket.bankGuarantees?.restitutionAvance || 0,
        bankName: formBankName,
        expiryDate: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
      }
    };

    updateMarket(updated);
    setIsEditModalOpen(false);
    showNotification(`Projet "${updated.number}" mis à jour avec succès.`);
  };

  // Submit Add New Project
  const handleSaveAdd = (e: React.FormEvent) => {
    e.preventDefault();

    const retenueRate = (formGuaranteeModality === 'retenue_garantie' || formGuaranteeModality === 'both') ? 5 : 0;
    const cautionAmount = (formGuaranteeModality === 'caution_bonne_execution' || formGuaranteeModality === 'both') 
      ? formAmountHT * 0.05 
      : 0;

    const newMarketId = `m-${Date.now()}`;
    const newMarket: Market = {
      id: newMarketId,
      number: formNumber || `MARCHE-${Date.now().toString().slice(-4)}`,
      title: formTitle || 'Nouveau Projet BTP',
      type: formType,
      client: formClient || 'Direction des Équipements Publics',
      bet: formBet || 'Bureau d\'Études Technique Agréé',
      enterpriseName: formEnterprise,
      wilaya: formWilaya,
      commune: formCommune,
      durationMonths: Number(formDuration),
      signatureDate: formSignatureDate,
      odsDate: formOdsDate,
      initialAmountHT: Number(formAmountHT),
      currentAmountHT: Number(formAmountHT),
      tvaRate: formTvaRate,
      status: formStatus,
      retenueGarantieRate: retenueRate as 0 | 5 | 10,
      guaranteeModality: formGuaranteeModality,
      avanceForfaitaireRate: 15,
      avanceApproRate: 0,
      bankGuarantees: {
        soumission: 0,
        bonneExecution: cautionAmount,
        restitutionAvance: 0,
        bankName: formBankName,
        expiryDate: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]
      }
    };

    addMarket(newMarket);
    setSelectedMarketId(newMarketId);
    setIsAddModalOpen(false);
    showNotification(`Nouveau projet "${newMarket.title}" créé et défini comme projet actif !`);
  };

  // Confirm Delete
  const handleConfirmDelete = () => {
    if (!targetMarket) return;
    const deletedNum = targetMarket.number;
    deleteMarket(targetMarket.id);
    setIsDeleteModalOpen(false);
    setTargetMarket(null);
    showNotification(`Projet "${deletedNum}" supprimé avec succès.`);
  };

  // Handle Archive / Restore toggle
  const handleToggleArchive = (m: Market) => {
    if (m.status === 'Archivé') {
      unarchiveMarket(m.id);
      showNotification(`Projet "${m.number}" restauré avec succès.`);
    } else {
      archiveMarket(m.id);
      showNotification(`Projet "${m.number}" archivé.`);
    }
  };

  // Select / Keep as Active Project
  const handleSelectActiveProject = (marketId: string) => {
    setSelectedMarketId(marketId);
    const m = markets.find(item => item.id === marketId);
    showNotification(`Projet actif sélectionné : "${m?.title || marketId}"`);
  };

  // Filtered Markets
  const filteredMarkets = useMemo(() => {
    return markets.filter(m => {
      // Search text
      const q = searchTerm.toLowerCase();
      const matchesSearch = 
        m.title.toLowerCase().includes(q) ||
        m.number.toLowerCase().includes(q) ||
        m.client.toLowerCase().includes(q) ||
        m.bet.toLowerCase().includes(q) ||
        m.commune.toLowerCase().includes(q) ||
        getWilayaName(m.wilaya).toLowerCase().includes(q);

      // Wilaya filter
      const matchesWilaya = wilayaFilter === 'all' || m.wilaya === wilayaFilter;

      // Status filter
      let matchesStatus = true;
      if (statusFilter === 'active') matchesStatus = m.status !== 'Archivé';
      else if (statusFilter === 'archived') matchesStatus = m.status === 'Archivé';
      else if (statusFilter === 'en_cours') matchesStatus = m.status === 'En cours';
      else if (statusFilter === 'arret') matchesStatus = m.status === 'En arrêt';
      else if (statusFilter === 'reception') matchesStatus = m.status.startsWith('Réception');

      return matchesSearch && matchesWilaya && matchesStatus;
    });
  }, [markets, searchTerm, statusFilter, wilayaFilter]);

  // Portefeuille Calculations
  const activeMarketsCount = markets.filter(m => m.status !== 'Archivé').length;
  const archivedMarketsCount = markets.filter(m => m.status === 'Archivé').length;
  const totalPortefeuilleHT = markets.reduce((acc, m) => acc + (m.status !== 'Archivé' ? m.currentAmountHT : 0), 0);
  const totalPaidTTC = situations.filter(s => s.status === 'Validée / Payée').reduce((acc, s) => acc + s.netToPayTTC, 0);

  return (
    <div className="space-y-6">
      {/* TOAST NOTIFICATION */}
      {notification && (
        <div className="fixed top-5 right-5 z-50 bg-slate-900 text-white px-4 py-3 rounded-2xl shadow-xl flex items-center gap-2 text-xs font-bold border border-slate-700 animate-in fade-in slide-in-from-top-4">
          <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          <span>{notification}</span>
        </div>
      )}

      {/* TOP HEADER */}
      <div className="bg-white p-5 rounded-3xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-2 rounded-xl bg-blue-600/10 text-blue-700">
              <FolderKanban className="w-5 h-5" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Contrôle & Portefeuille des Marchés BTP
            </span>
          </div>
          <h1 className="text-xl sm:text-2xl font-black text-slate-900 mt-1">
            Gestion & Contrôle des Projets
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Garder un projet actif, modifier ses paramètres, l'archiver ou le supprimer en toute sécurité
          </p>
        </div>

        {/* Global Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => {
              setFormNumber(`MARCHE-${Date.now().toString().slice(-4)}`);
              setFormTitle('');
              setFormClient('Direction des Équipements Publics (DEP)');
              setFormBet('BET Agréé');
              setFormAmountHT(85000000);
              setIsAddModalOpen(true);
            }}
            className="px-4 py-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-xs font-black rounded-xl shadow-xs flex items-center gap-2 transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Nouveau Projet</span>
          </button>

          <button
            onClick={() => onNavigate('ai-metre')}
            className="px-4 py-2 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white text-xs font-black rounded-xl shadow-xs flex items-center gap-2 transition-all cursor-pointer"
          >
            <Sparkles className="w-4 h-4 text-amber-200" />
            <span>Scanner Devis PDF</span>
          </button>
        </div>
      </div>

      {/* KPI METRIC CARDS */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {/* KPI 1 : Projets Actifs */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Projets Actifs</span>
            <span className="p-1.5 rounded-lg bg-blue-50 text-blue-600">
              <FolderKanban className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {activeMarketsCount} <span className="text-xs font-bold text-slate-500">en cours</span>
            </span>
            <span className="text-[11px] text-slate-400 block mt-0.5">
              {archivedMarketsCount} projets archivés
            </span>
          </div>
        </div>

        {/* KPI 2 : Portefeuille Marchés HT */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Montant Portefeuille HT</span>
            <span className="p-1.5 rounded-lg bg-emerald-50 text-emerald-600">
              <Building2 className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-lg sm:text-xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalPortefeuilleHT)}
            </span>
            <span className="text-[11px] text-emerald-600 font-bold block mt-0.5">
              Budget total contractuel actif
            </span>
          </div>
        </div>

        {/* KPI 3 : Facturé & Réglé */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Facturation Encaissée TTC</span>
            <span className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600">
              <Receipt className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-lg sm:text-xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalPaidTTC)}
            </span>
            <span className="text-[11px] text-slate-400 block mt-0.5">
              Situations validées & décomptes payés
            </span>
          </div>
        </div>

        {/* KPI 4 : Projet Actif en Cours */}
        <div className="bg-gradient-to-br from-blue-900 to-indigo-950 p-4 rounded-2xl text-white shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-blue-300 uppercase tracking-wider flex items-center gap-1">
              <Star className="w-3.5 h-3.5 text-amber-300 fill-amber-300" />
              Projet Sélectionné
            </span>
            <span className="px-2 py-0.5 rounded-md bg-blue-700/60 text-[10px] font-mono font-bold">
              ID Actif
            </span>
          </div>
          <div className="mt-2">
            {(() => {
              const active = markets.find(m => m.id === selectedMarketId);
              return (
                <>
                  <span className="text-xs font-black text-white line-clamp-1 block">
                    {active ? active.title : 'Aucun projet sélectionné'}
                  </span>
                  <span className="text-[11px] text-blue-300 font-mono block mt-0.5">
                    {active ? `${active.number} • ${formatDA(active.currentAmountHT)}` : '—'}
                  </span>
                </>
              );
            })()}
          </div>
        </div>
      </div>

      {/* SEARCH & FILTERS BAR */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
          {/* Search Input */}
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
            <input
              type="text"
              placeholder="Rechercher projet (titre, N° marché, client, BET, commune...)"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-xs rounded-xl border border-slate-200 focus:outline-hidden focus:ring-2 focus:ring-blue-500/20"
            />
          </div>

          {/* Wilaya Filter & View Mode Switch */}
          <div className="flex items-center gap-2">
            <select
              value={wilayaFilter}
              onChange={(e) => setWilayaFilter(e.target.value)}
              className="text-xs py-2 px-3 rounded-xl border border-slate-200 bg-white font-medium"
            >
              <option value="all">Toutes les Wilayas ({ALGERIAN_WILAYAS.length})</option>
              {ALGERIAN_WILAYAS.map(w => (
                <option key={w.code} value={w.code}>{w.code} - {w.name}</option>
              ))}
            </select>

            <div className="flex items-center border border-slate-200 rounded-xl p-0.5 bg-slate-50">
              <button
                onClick={() => setViewMode('grid')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  viewMode === 'grid' ? 'bg-white shadow-xs text-blue-700' : 'text-slate-500'
                }`}
              >
                Grille
              </button>
              <button
                onClick={() => setViewMode('table')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                  viewMode === 'table' ? 'bg-white shadow-xs text-blue-700' : 'text-slate-500'
                }`}
              >
                Tableau
              </button>
            </div>
          </div>
        </div>

        {/* Filter Chips */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 text-xs">
          <button
            onClick={() => setStatusFilter('all')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              statusFilter === 'all'
                ? 'bg-slate-900 text-white'
                : 'bg-slate-100 hover:bg-slate-200 text-slate-600'
            }`}
          >
            Tous les Projets ({markets.length})
          </button>
          <button
            onClick={() => setStatusFilter('active')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              statusFilter === 'active'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'bg-blue-50 hover:bg-blue-100 text-blue-700'
            }`}
          >
            Projets Actifs ({activeMarketsCount})
          </button>
          <button
            onClick={() => setStatusFilter('en_cours')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              statusFilter === 'en_cours'
                ? 'bg-emerald-600 text-white shadow-xs'
                : 'bg-emerald-50 hover:bg-emerald-100 text-emerald-800'
            }`}
          >
            En cours ({markets.filter(m => m.status === 'En cours').length})
          </button>
          <button
            onClick={() => setStatusFilter('reception')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              statusFilter === 'reception'
                ? 'bg-indigo-600 text-white shadow-xs'
                : 'bg-indigo-50 hover:bg-indigo-100 text-indigo-800'
            }`}
          >
            Réception ({markets.filter(m => m.status.startsWith('Réception')).length})
          </button>
          <button
            onClick={() => setStatusFilter('arret')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              statusFilter === 'arret'
                ? 'bg-rose-600 text-white shadow-xs'
                : 'bg-rose-50 hover:bg-rose-100 text-rose-800'
            }`}
          >
            En arrêt ({markets.filter(m => m.status === 'En arrêt').length})
          </button>
          <button
            onClick={() => setStatusFilter('archived')}
            className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
              statusFilter === 'archived'
                ? 'bg-amber-600 text-white shadow-xs'
                : 'bg-amber-50 hover:bg-amber-100 text-amber-800'
            }`}
          >
            Archivés ({archivedMarketsCount})
          </button>
        </div>
      </div>

      {/* ============================================================== */}
      {/* VUE 1 : GRILLE DE CARTES DE PROJETS                             */}
      {/* ============================================================== */}
      {viewMode === 'grid' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {filteredMarkets.map(m => {
            const isCurrent = m.id === selectedMarketId;
            const isArchived = m.status === 'Archivé';
            
            // Financial calculations for this project
            const projectArticles = articles.filter(a => a.marketId === m.id);
            const projectSituations = situations.filter(s => s.marketId === m.id);
            const totalPaidForProject = projectSituations
              .filter(s => s.status === 'Validée / Payée')
              .reduce((sum, s) => sum + s.netToPayTTC, 0);

            const progressPct = m.currentAmountHT > 0 
              ? Math.min(100, Math.round((totalPaidForProject / (m.currentAmountHT * (1 + m.tvaRate / 100))) * 100))
              : 0;

            const isCaution = m.guaranteeModality === 'caution_bonne_execution' || (m.guaranteeModality !== 'retenue_garantie' && m.retenueGarantieRate === 0);

            return (
              <div
                key={m.id}
                className={`bg-white rounded-3xl border transition-all shadow-xs flex flex-col justify-between overflow-hidden ${
                  isCurrent 
                    ? 'border-blue-600 ring-2 ring-blue-500/20 shadow-md' 
                    : isArchived 
                    ? 'border-amber-300/80 bg-amber-50/15 opacity-90' 
                    : 'border-slate-200/80 hover:border-slate-300'
                }`}
              >
                {/* Active Project Banner */}
                {isCurrent && (
                  <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-4 py-1.5 text-white text-xs font-black flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <Star className="w-3.5 h-3.5 fill-amber-300 text-amber-300" />
                      <span>PROJET ACTIF EN COURS D'UTILISATION</span>
                    </span>
                    <span className="text-[10px] bg-white/20 px-2 py-0.5 rounded-md font-mono">
                      Sélectionné
                    </span>
                  </div>
                )}

                {isArchived && !isCurrent && (
                  <div className="bg-amber-100 text-amber-900 px-4 py-1.5 text-xs font-bold flex items-center gap-1.5 border-b border-amber-200">
                    <Archive className="w-3.5 h-3.5 text-amber-700" />
                    <span>PROJET ARCHIVÉ (Inactif)</span>
                  </div>
                )}

                <div className="p-5 space-y-4">
                  {/* Top Line: Market Number, Status, Guarantee Badge */}
                  <div className="flex items-center justify-between gap-2 flex-wrap">
                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-1 rounded-lg bg-slate-900 text-white font-mono font-bold text-xs">
                        {m.number}
                      </span>
                      <span className={`text-[11px] font-bold px-2 py-0.5 rounded-md border ${
                        m.status === 'En cours'
                          ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                          : m.status === 'Archivé'
                          ? 'bg-amber-100 text-amber-900 border-amber-300'
                          : m.status === 'En arrêt'
                          ? 'bg-rose-50 text-rose-800 border-rose-200'
                          : 'bg-indigo-50 text-indigo-800 border-indigo-200'
                      }`}>
                        {m.status}
                      </span>
                    </div>

                    {/* Guarantee Modality Badge */}
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md border flex items-center gap-1 ${
                      isCaution 
                        ? 'bg-blue-50 text-blue-800 border-blue-200' 
                        : 'bg-amber-50 text-amber-800 border-amber-200'
                    }`}>
                      {isCaution ? (
                        <>
                          <ShieldCheck className="w-3 h-3 text-blue-600" />
                          <span>Caution 5% ({m.bankGuarantees?.bankName || 'Banque'})</span>
                        </>
                      ) : (
                        <>
                          <Scale className="w-3 h-3 text-amber-600" />
                          <span>Retenue 5% (Trésor)</span>
                        </>
                      )}
                    </span>
                  </div>

                  {/* Title & Client */}
                  <div>
                    <h3 className="font-black text-base text-slate-900 leading-snug">
                      {m.title}
                    </h3>
                    <div className="flex items-center gap-3 text-xs text-slate-500 mt-2 flex-wrap">
                      <span className="flex items-center gap-1 font-semibold text-slate-700">
                        <Landmark className="w-3.5 h-3.5 text-blue-600 shrink-0" />
                        {m.client}
                      </span>
                      <span>•</span>
                      <span className="flex items-center gap-1">
                        <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                        {m.commune} ({getWilayaName(m.wilaya)})
                      </span>
                    </div>
                  </div>

                  {/* Financial Metrics Box */}
                  <div className="grid grid-cols-2 gap-3 p-3.5 rounded-2xl bg-slate-50 border border-slate-100">
                    <div>
                      <span className="text-[10px] text-slate-500 font-bold block uppercase tracking-wider">
                        Montant Marché HT
                      </span>
                      <span className="text-base font-black text-slate-900 font-mono tracking-tight block mt-0.5">
                        {formatDA(m.currentAmountHT)}
                      </span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">
                        TVA {m.tvaRate}% : {formatDA(m.currentAmountHT * (1 + m.tvaRate / 100))} TTC
                      </span>
                    </div>

                    <div>
                      <span className="text-[10px] text-slate-500 font-bold block uppercase tracking-wider">
                        Facturé & Encaissé
                      </span>
                      <span className="text-base font-black text-emerald-700 font-mono tracking-tight block mt-0.5">
                        {formatDA(totalPaidForProject)}
                      </span>
                      <span className="text-[10px] text-slate-400 block mt-0.5">
                        {projectSituations.length} décomptes émis
                      </span>
                    </div>
                  </div>

                  {/* Progress bar */}
                  <div>
                    <div className="flex items-center justify-between text-xs mb-1">
                      <span className="text-slate-500 text-[11px] font-medium">Avancement financier</span>
                      <span className="font-mono font-bold text-slate-900">{progressPct}%</span>
                    </div>
                    <div className="w-full bg-slate-100 rounded-full h-2 overflow-hidden">
                      <div 
                        className={`h-full rounded-full transition-all duration-500 ${
                          progressPct >= 80 ? 'bg-emerald-500' : progressPct >= 40 ? 'bg-blue-600' : 'bg-amber-500'
                        }`}
                        style={{ width: `${progressPct}%` }}
                      />
                    </div>
                  </div>

                  {/* Details Pills: ODS, BET, Articles */}
                  <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1 border-t border-slate-100">
                    <span className="flex items-center gap-1">
                      <Calendar className="w-3 h-3 text-slate-400" />
                      ODS: <strong>{m.odsDate}</strong> ({m.durationMonths} mois)
                    </span>
                    <span>
                      BET: <strong>{m.bet}</strong>
                    </span>
                  </div>
                </div>

                {/* PROJECT CONTROLS FOOTER */}
                <div className="p-4 bg-slate-50/80 border-t border-slate-100 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2">
                  {/* Select / Keep as Active Project */}
                  <div>
                    {isCurrent ? (
                      <span className="px-3 py-1.5 rounded-xl bg-emerald-100 text-emerald-800 text-xs font-black flex items-center gap-1.5">
                        <Check className="w-3.5 h-3.5" />
                        <span>Projet Actif en Cours</span>
                      </span>
                    ) : (
                      <button
                        onClick={() => handleSelectActiveProject(m.id)}
                        className="px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-black flex items-center gap-1.5 transition-all cursor-pointer shadow-xs"
                        title="Activer ce projet comme projet de travail dans toute l'application"
                      >
                        <Star className="w-3.5 h-3.5" />
                        <span>Garder comme Projet Actif</span>
                      </button>
                    )}
                  </div>

                  {/* Action Buttons: Edit, Archive, Delete */}
                  <div className="flex items-center gap-1 justify-end">
                    <button
                      onClick={() => handleOpenEdit(m)}
                      className="px-2.5 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-100 text-slate-700 text-xs font-bold flex items-center gap-1 transition-colors"
                      title="Modifier les paramètres du projet"
                    >
                      <Edit3 className="w-3.5 h-3.5 text-blue-600" />
                      <span>Modifier</span>
                    </button>

                    <button
                      onClick={() => handleToggleArchive(m)}
                      className="px-2.5 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-100 text-slate-700 text-xs font-bold flex items-center gap-1 transition-colors"
                      title={isArchived ? "Désarchiver le projet" : "Archiver le projet"}
                    >
                      {isArchived ? (
                        <>
                          <ArchiveRestore className="w-3.5 h-3.5 text-amber-600" />
                          <span>Désarchiver</span>
                        </>
                      ) : (
                        <>
                          <Archive className="w-3.5 h-3.5 text-slate-500" />
                          <span>Archiver</span>
                        </>
                      )}
                    </button>

                    <button
                      onClick={() => handleOpenDelete(m)}
                      className="p-2 rounded-xl border border-slate-200 bg-white hover:bg-rose-50 text-slate-400 hover:text-rose-600 text-xs font-bold transition-colors"
                      title="Supprimer ce projet"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* ============================================================== */}
      {/* VUE 2 : TABLEAU DÉTAILLÉ ERP                                    */}
      {/* ============================================================== */}
      {viewMode === 'table' && (
        <div className="bg-white rounded-3xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                  <th className="py-3 px-4">Projet Actif</th>
                  <th className="py-3 px-4">N° Marché</th>
                  <th className="py-3 px-4">Intitulé du Projet</th>
                  <th className="py-3 px-4">Maître d'Ouvrage</th>
                  <th className="py-3 px-4 text-right">Montant HT</th>
                  <th className="py-3 px-4 text-center">Garantie</th>
                  <th className="py-3 px-4 text-center">Statut</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredMarkets.map(m => {
                  const isCurrent = m.id === selectedMarketId;
                  const isArchived = m.status === 'Archivé';
                  const isCaution = m.guaranteeModality === 'caution_bonne_execution' || (m.guaranteeModality !== 'retenue_garantie' && m.retenueGarantieRate === 0);

                  return (
                    <tr 
                      key={m.id} 
                      className={`hover:bg-slate-50 transition-colors ${isCurrent ? 'bg-blue-50/40' : ''}`}
                    >
                      <td className="py-3 px-4 whitespace-nowrap">
                        {isCurrent ? (
                          <span className="px-2 py-1 rounded-md bg-emerald-100 text-emerald-800 text-[10px] font-black inline-flex items-center gap-1">
                            <Star className="w-3 h-3 fill-emerald-600 text-emerald-600" />
                            Actif
                          </span>
                        ) : (
                          <button
                            onClick={() => handleSelectActiveProject(m.id)}
                            className="px-2 py-1 rounded-md border border-slate-200 hover:bg-blue-600 hover:text-white text-slate-600 text-[10px] font-bold transition-all"
                          >
                            Activer
                          </button>
                        )}
                      </td>

                      <td className="py-3 px-4 font-mono font-bold text-slate-900 whitespace-nowrap">
                        {m.number}
                      </td>

                      <td className="py-3 px-4 font-bold text-slate-900 max-w-xs">
                        <span className="line-clamp-1">{m.title}</span>
                        <span className="text-[10px] text-slate-400 font-normal block">{m.commune} ({getWilayaName(m.wilaya)})</span>
                      </td>

                      <td className="py-3 px-4 text-slate-700 font-medium">
                        {m.client}
                      </td>

                      <td className="py-3 px-4 text-right font-mono font-black text-slate-900 whitespace-nowrap">
                        {formatDA(m.currentAmountHT)}
                      </td>

                      <td className="py-3 px-4 text-center whitespace-nowrap">
                        <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold ${
                          isCaution ? 'bg-blue-50 text-blue-800' : 'bg-amber-50 text-amber-800'
                        }`}>
                          {isCaution ? 'Caution 5%' : 'Retenue 5%'}
                        </span>
                      </td>

                      <td className="py-3 px-4 text-center whitespace-nowrap">
                        <span className={`px-2 py-0.5 rounded-md text-[10px] font-bold ${
                          m.status === 'En cours' ? 'bg-emerald-100 text-emerald-800' :
                          m.status === 'Archivé' ? 'bg-amber-100 text-amber-900' :
                          'bg-slate-100 text-slate-700'
                        }`}>
                          {m.status}
                        </span>
                      </td>

                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <div className="flex items-center gap-1 justify-end">
                          <button
                            onClick={() => handleOpenEdit(m)}
                            className="p-1.5 rounded-lg border border-slate-200 hover:bg-slate-100 text-blue-600"
                            title="Modifier"
                          >
                            <Edit3 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleToggleArchive(m)}
                            className="p-1.5 rounded-lg border border-slate-200 hover:bg-slate-100 text-amber-600"
                            title={isArchived ? "Désarchiver" : "Archiver"}
                          >
                            {isArchived ? <ArchiveRestore className="w-3.5 h-3.5" /> : <Archive className="w-3.5 h-3.5" />}
                          </button>
                          <button
                            onClick={() => handleOpenDelete(m)}
                            className="p-1.5 rounded-lg border border-slate-200 hover:bg-rose-50 text-rose-600"
                            title="Supprimer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 1 : MODIFIER LE PROJET                                   */}
      {/* ============================================================== */}
      {isEditModalOpen && targetMarket && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-2xl w-full p-6 shadow-2xl border border-slate-100 space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-blue-100 text-blue-800">
                  <Edit3 className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="font-black text-base text-slate-900">
                    Modifier les Paramètres du Projet
                  </h3>
                  <span className="text-xs text-slate-400 font-mono">{targetMarket.number}</span>
                </div>
              </div>
              <button 
                onClick={() => setIsEditModalOpen(false)} 
                className="text-slate-400 hover:text-slate-600 text-xs font-bold"
              >
                ✕ Fermer
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className="space-y-4">
              {/* N° et Titre */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">N° de Marché / Contrat</label>
                  <input
                    type="text"
                    required
                    value={formNumber}
                    onChange={(e) => setFormNumber(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono font-bold"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="text-xs font-bold text-slate-700 block mb-1">Intitulé du Projet</label>
                  <input
                    type="text"
                    required
                    value={formTitle}
                    onChange={(e) => setFormTitle(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-bold"
                  />
                </div>
              </div>

              {/* Maître d'ouvrage & BET */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Maître d'Ouvrage (Client)</label>
                  <input
                    type="text"
                    required
                    value={formClient}
                    onChange={(e) => setFormClient(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Bureau d'Études (BET)</label>
                  <input
                    type="text"
                    required
                    value={formBet}
                    onChange={(e) => setFormBet(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
              </div>

              {/* Wilaya, Commune & Statut */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Wilaya</label>
                  <select
                    value={formWilaya}
                    onChange={(e) => setFormWilaya(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white"
                  >
                    {ALGERIAN_WILAYAS.map(w => (
                      <option key={w.code} value={w.code}>{w.code} - {w.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Commune</label>
                  <input
                    type="text"
                    required
                    value={formCommune}
                    onChange={(e) => setFormCommune(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Statut du Projet</label>
                  <select
                    value={formStatus}
                    onChange={(e) => setFormStatus(e.target.value as MarketStatus)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white font-bold"
                  >
                    <option value="En cours">🟢 En cours</option>
                    <option value="En arrêt">🔴 En arrêt</option>
                    <option value="Réception Provisoire">🔵 Réception Provisoire</option>
                    <option value="Réception Définitive">🟣 Réception Définitive</option>
                    <option value="Clôturé">⚪ Clôturé</option>
                    <option value="Archivé">📦 Archivé</option>
                  </select>
                </div>
              </div>

              {/* Montant HT & Taux TVA */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3 bg-slate-50 rounded-2xl border border-slate-100">
                <div className="sm:col-span-2">
                  <label className="text-xs font-bold text-slate-700 block mb-1">Montant Initial HT (DA)</label>
                  <input
                    type="number"
                    min="1"
                    step="any"
                    required
                    value={formAmountHT}
                    onChange={(e) => setFormAmountHT(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-sm rounded-xl border border-slate-200 font-mono font-black"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Taux TVA DZ</label>
                  <select
                    value={formTvaRate}
                    onChange={(e) => setFormTvaRate(Number(e.target.value) as 9 | 19)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white font-bold"
                  >
                    <option value={9}>9% (Logement / BTP)</option>
                    <option value={19}>19% (Régime Général)</option>
                  </select>
                </div>
              </div>

              {/* Modalité de Garantie Contractuelle */}
              <div className="space-y-2 p-3 bg-blue-50/60 rounded-2xl border border-blue-200">
                <label className="text-xs font-bold text-blue-900 block">
                  Modalité de Garantie Contractuelle (Code des Marchés Publics DZ)
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                  <button
                    type="button"
                    onClick={() => setFormGuaranteeModality('caution_bonne_execution')}
                    className={`p-2.5 rounded-xl text-left border font-bold transition-all ${
                      formGuaranteeModality === 'caution_bonne_execution'
                        ? 'border-blue-600 bg-white text-blue-950 ring-2 ring-blue-500/20'
                        : 'border-slate-200 bg-white/70 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <ShieldCheck className="w-4 h-4 text-blue-600" />
                      <span>Caution Bancaire (5%)</span>
                    </div>
                    <span className="text-[10px] text-slate-500 block mt-1">
                      0% retenue sur décomptes (trésorerie protégée)
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setFormGuaranteeModality('retenue_garantie')}
                    className={`p-2.5 rounded-xl text-left border font-bold transition-all ${
                      formGuaranteeModality === 'retenue_garantie'
                        ? 'border-blue-600 bg-white text-blue-950 ring-2 ring-blue-500/20'
                        : 'border-slate-200 bg-white/70 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <Scale className="w-4 h-4 text-amber-600" />
                      <span>Retenue de Garantie (5%)</span>
                    </div>
                    <span className="text-[10px] text-slate-500 block mt-1">
                      Prélèvement mensuel consigné au Trésor
                    </span>
                  </button>
                </div>

                {formGuaranteeModality === 'caution_bonne_execution' && (
                  <div className="pt-2">
                    <label className="text-[11px] font-bold text-slate-700 block mb-1">Banque Émettrice de la Caution</label>
                    <select
                      value={formBankName}
                      onChange={(e) => setFormBankName(e.target.value as AlgerianBank)}
                      className="w-full px-3 py-1.5 text-xs rounded-xl border border-slate-200 bg-white font-bold"
                    >
                      {ALGERIAN_BANKS.map(b => (
                        <option key={b} value={b}>{b}</option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              {/* Dates & Délais */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Délai (Mois)</label>
                  <input
                    type="number"
                    min="1"
                    value={formDuration}
                    onChange={(e) => setFormDuration(parseInt(e.target.value) || 1)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Date Signature</label>
                  <input
                    type="date"
                    value={formSignatureDate}
                    onChange={(e) => setFormSignatureDate(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Date ODS Démarrage</label>
                  <input
                    type="date"
                    value={formOdsDate}
                    onChange={(e) => setFormOdsDate(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
              </div>

              {/* Buttons */}
              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsEditModalOpen(false)}
                  className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-black rounded-xl shadow-xs"
                >
                  Enregistrer les Modifications
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 2 : NOUVEAU PROJET                                       */}
      {/* ============================================================== */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-2xl w-full p-6 shadow-2xl border border-slate-100 space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-blue-100 text-blue-800">
                  <Plus className="w-4 h-4" />
                </span>
                <h3 className="font-black text-base text-slate-900">
                  Créer un Nouveau Projet de Marché
                </h3>
              </div>
              <button 
                onClick={() => setIsAddModalOpen(false)} 
                className="text-slate-400 hover:text-slate-600 text-xs font-bold"
              >
                ✕ Fermer
              </button>
            </div>

            <form onSubmit={handleSaveAdd} className="space-y-4">
              {/* N° et Titre */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">N° de Marché</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: 2026/18/DEP"
                    value={formNumber}
                    onChange={(e) => setFormNumber(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono font-bold"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="text-xs font-bold text-slate-700 block mb-1">Intitulé du Projet</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Réalisation d'une école primaire 12 classes..."
                    value={formTitle}
                    onChange={(e) => setFormTitle(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-bold"
                  />
                </div>
              </div>

              {/* Maître d'ouvrage & BET */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Maître d'Ouvrage (Client)</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Direction des Équipements Publics"
                    value={formClient}
                    onChange={(e) => setFormClient(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Bureau d'Études (BET)</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: BET Archi-Design SBA"
                    value={formBet}
                    onChange={(e) => setFormBet(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
              </div>

              {/* Wilaya, Commune & Statut */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Wilaya</label>
                  <select
                    value={formWilaya}
                    onChange={(e) => setFormWilaya(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white"
                  >
                    {ALGERIAN_WILAYAS.map(w => (
                      <option key={w.code} value={w.code}>{w.code} - {w.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Commune</label>
                  <input
                    type="text"
                    required
                    value={formCommune}
                    onChange={(e) => setFormCommune(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Statut Initial</label>
                  <select
                    value={formStatus}
                    onChange={(e) => setFormStatus(e.target.value as MarketStatus)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white font-bold"
                  >
                    <option value="En cours">🟢 En cours</option>
                    <option value="En arrêt">🔴 En arrêt</option>
                  </select>
                </div>
              </div>

              {/* Montant HT & Taux TVA */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3 bg-slate-50 rounded-2xl border border-slate-100">
                <div className="sm:col-span-2">
                  <label className="text-xs font-bold text-slate-700 block mb-1">Montant Initial HT (DA)</label>
                  <input
                    type="number"
                    min="1"
                    step="any"
                    required
                    value={formAmountHT}
                    onChange={(e) => setFormAmountHT(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-sm rounded-xl border border-slate-200 font-mono font-black"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Taux TVA DZ</label>
                  <select
                    value={formTvaRate}
                    onChange={(e) => setFormTvaRate(Number(e.target.value) as 9 | 19)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white font-bold"
                  >
                    <option value={9}>9% (Logement / BTP)</option>
                    <option value={19}>19% (Régime Général)</option>
                  </select>
                </div>
              </div>

              {/* Modalité de Garantie */}
              <div className="space-y-2 p-3 bg-blue-50/60 rounded-2xl border border-blue-200">
                <label className="text-xs font-bold text-blue-900 block">
                  Modalité de Garantie Contractuelle (Code des Marchés Publics DZ)
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                  <button
                    type="button"
                    onClick={() => setFormGuaranteeModality('caution_bonne_execution')}
                    className={`p-2.5 rounded-xl text-left border font-bold transition-all ${
                      formGuaranteeModality === 'caution_bonne_execution'
                        ? 'border-blue-600 bg-white text-blue-950 ring-2 ring-blue-500/20'
                        : 'border-slate-200 bg-white/70 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <ShieldCheck className="w-4 h-4 text-blue-600" />
                      <span>Caution Bancaire (5%)</span>
                    </div>
                    <span className="text-[10px] text-slate-500 block mt-1">
                      0% retenue sur décomptes (trésorerie protégée)
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setFormGuaranteeModality('retenue_garantie')}
                    className={`p-2.5 rounded-xl text-left border font-bold transition-all ${
                      formGuaranteeModality === 'retenue_garantie'
                        ? 'border-blue-600 bg-white text-blue-950 ring-2 ring-blue-500/20'
                        : 'border-slate-200 bg-white/70 text-slate-700'
                    }`}
                  >
                    <div className="flex items-center gap-1.5">
                      <Scale className="w-4 h-4 text-amber-600" />
                      <span>Retenue de Garantie (5%)</span>
                    </div>
                    <span className="text-[10px] text-slate-500 block mt-1">
                      Prélèvement mensuel consigné au Trésor
                    </span>
                  </button>
                </div>
              </div>

              {/* Buttons */}
              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-black rounded-xl shadow-xs"
                >
                  Créer le Projet
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 3 : CONFIRMATION SUPPRESSION PROJET                      */}
      {/* ============================================================== */}
      {isDeleteModalOpen && targetMarket && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl border border-rose-100 space-y-4">
            <div className="flex items-center gap-3 text-rose-600">
              <span className="p-2 rounded-2xl bg-rose-100">
                <Trash2 className="w-5 h-5" />
              </span>
              <div>
                <h3 className="font-black text-base text-slate-900">
                  Supprimer ce Projet ?
                </h3>
                <span className="text-xs text-slate-500">{targetMarket.number}</span>
              </div>
            </div>

            <div className="p-3 bg-rose-50 border border-rose-200 rounded-2xl text-xs text-rose-900 space-y-2">
              <p className="font-bold">
                Êtes-vous sûr de vouloir supprimer définitivement ce projet ?
              </p>
              <p className="text-[11px] text-rose-800">
                <strong>{targetMarket.title}</strong>
                <br />
                Montant : {formatDA(targetMarket.currentAmountHT)} HT
              </p>
              <p className="text-[11px] text-slate-500">
                💡 Astuce : Si vous souhaitez simplement masquer ce projet sans perdre ses données, vous pouvez plutôt l'<strong>Archiver</strong>.
              </p>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsDeleteModalOpen(false)}
                className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-xl"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                className="px-5 py-2 bg-rose-600 hover:bg-rose-700 text-white text-xs font-black rounded-xl shadow-xs"
              >
                Supprimer Définitivement
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
