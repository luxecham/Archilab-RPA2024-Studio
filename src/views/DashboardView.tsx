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
  Clock, 
  ShieldAlert, 
  Calendar,
  Layers,
  Plus,
  Search,
  Filter,
  Landmark,
  HardHat,
  MapPin,
  X,
  Check,
  ChevronRight,
  Briefcase,
  SlidersHorizontal,
  FolderKanban
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';
import { ActiveTab } from '../components/Sidebar';
import { Market, MarketType, MarketStatus, Lot, Chantier } from '../types/btp';

interface DashboardViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

const COMMON_CLIENT_SUGGESTIONS = [
  'OPGI (Office de Promotion et de Gestion Immobilière)',
  'Direction de l\'Urbanisme (DUC)',
  'Direction des Équipements Publics (DEP)',
  'Direction des Travaux Publics (DTP)',
  'Direction du Logement (DLEP)',
  'Direction des Ressources en Eau (DRE)',
  'Direction de la Jeunesse et des Sports (DJS)',
  'APC (Assemblée Populaire Communale)',
  'Sonatrach / Groupe Public',
  'Promoteur Immobilier Privé'
];

export const DashboardView: React.FC<DashboardViewProps> = ({ onNavigate }) => {
  const { 
    markets, 
    attachements, 
    avenants, 
    situations, 
    chantiers, 
    articles,
    selectedMarketId,
    setSelectedMarketId,
    addMarket,
    addLot,
    addChantier
  } = useBTP();

  // Search, filter and view mode for projects
  const [projectSearch, setProjectSearch] = useState('');
  const [selectedClientFilter, setSelectedClientFilter] = useState<string>('all');
  const [portfolioViewMode, setPortfolioViewMode] = useState<'by_client' | 'grid'>('by_client');

  // Modal State for New Project / Market
  const [isAddingProject, setIsAddingProject] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newNumber, setNewNumber] = useState('');
  const [newType, setNewType] = useState<MarketType>('Marché Public');
  const [newClient, setNewClient] = useState('OPGI Sidi Bel Abbès');
  const [newBet, setNewBet] = useState('BET Urbapro Oran');
  const [newEnterprise, setNewEnterprise] = useState('SARL TRAVAUX PUBLICS DU MAGHREB');
  const [newWilaya, setNewWilaya] = useState('22');
  const [newCommune, setNewCommune] = useState('Sidi Bel Abbès');
  const [newInitialAmountHT, setNewInitialAmountHT] = useState<number>(75000000);
  const [newTvaRate, setNewTvaRate] = useState<9 | 19>(9);
  const [newRetenueGarantieRate, setNewRetenueGarantieRate] = useState<5 | 10>(5);
  const [newOdsDate, setNewOdsDate] = useState('2026-04-01');
  const [newSignatureDate, setNewSignatureDate] = useState('2026-03-15');
  const [newDurationMonths, setNewDurationMonths] = useState<number>(14);
  const [newChefChantier, setNewChefChantier] = useState('Farid Mansouri');
  const [newChefPhone, setNewChefPhone] = useState('0661 78 90 12');

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];

  // Calculs financiers globaux
  const totalMarketsAmount = markets.reduce((acc, m) => acc + m.currentAmountHT, 0);
  const totalBilledHT = situations.reduce((acc, s) => acc + s.grossAmountCurrentHT, 0);
  const totalNetPaidTTC = situations.reduce((acc, s) => acc + s.netToPayTTC, 0);
  const totalRetenueGarantie = situations.reduce((acc, s) => acc + s.retenueGarantieHT, 0);
  const resteARealiser = Math.max(0, totalMarketsAmount - totalBilledHT);

  // Alertes de dépassement d'attachements
  const exceedanceAttachements = attachements.filter(att =>
    att.items.some(item => item.hasExceedance)
  );

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

  // Filtrage des marchés
  const filteredMarkets = markets.filter(m => {
    const matchesSearch = 
      m.title.toLowerCase().includes(projectSearch.toLowerCase()) ||
      m.number.toLowerCase().includes(projectSearch.toLowerCase()) ||
      m.client.toLowerCase().includes(projectSearch.toLowerCase()) ||
      m.commune.toLowerCase().includes(projectSearch.toLowerCase());

    const matchesClient = selectedClientFilter === 'all' || m.client.trim() === selectedClientFilter.trim();

    return matchesSearch && matchesClient;
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

  const openAddProjectModal = () => {
    const randomNum = Math.floor(10 + Math.random() * 89);
    setNewNumber(`2026/${randomNum}/DEP`);
    setNewTitle('Projet de Réalisation — Complexe Sportif et Voiries');
    setNewClient('Direction des Équipements Publics (DEP)');
    setNewBet('BET Architecture & Urbanisme DZ');
    setNewEnterprise('EGBTP EL MOUSTAKBEL');
    setNewWilaya('22');
    setNewCommune('Sidi Bel Abbès');
    setNewInitialAmountHT(54000000);
    setNewTvaRate(9);
    setNewRetenueGarantieRate(5);
    setNewOdsDate('2026-04-15');
    setNewSignatureDate('2026-03-25');
    setNewDurationMonths(12);
    setNewChefChantier('Abdelkader Zenati');
    setNewChefPhone('0555 33 22 11');
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
      signatureDate: newSignatureDate,
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

    // Création automatique du Lot 01 initial
    const initialLot: Lot = {
      id: `lot-${Date.now()}`,
      marketId: newMarketId,
      code: 'LOT 01',
      name: 'Gros Œuvres, VRD & Aménagements'
    };

    // Création automatique du Chantier initial
    const initialChantier: Chantier = {
      id: `ch-${Date.now()}`,
      lotId: initialLot.id,
      marketId: newMarketId,
      name: `Chantier ${newTitle.trim()}`,
      wilaya: newWilaya,
      address: `${newCommune.trim()}, Wilaya ${newWilaya}`,
      chefChantierName: newChefChantier.trim() || 'Non assigné',
      chefChantierPhone: newChefPhone.trim() || 'Non renseigné',
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
  };

  const getWilayaName = (code: string) => {
    const w = ALGERIAN_WILAYAS.find(wil => wil.code === code);
    return w ? `${w.code} - ${w.name}` : code;
  };

  return (
    <div className="space-y-6">
      {/* BANDEAU SCÉNARIO VIDÉO (01:10 → 07:28) */}
      <div className="bg-gradient-to-r from-slate-950 via-blue-950 to-slate-900 rounded-2xl p-5 text-white border border-blue-500/30 shadow-md space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-500 text-slate-950 font-black flex items-center justify-center text-lg shadow-md shrink-0">
              ▶
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-black text-sm uppercase tracking-wide">
                  Workflow Vidéo BTP Manager Pro (01:10 → 07:28)
                </span>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-500 text-white">
                  Prêt à tester
                </span>
              </div>
              <p className="text-xs text-blue-200 mt-0.5">
                Déroulez le cas réel complet : Projet R+5, saisie Art 4.2, blocage sur 622 ml (Art 4.5), validation Avenant et Situations
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              onClick={openAddProjectModal}
              className="px-4 py-2 bg-emerald-500 hover:bg-emerald-600 text-white text-xs font-black rounded-xl shadow-md transition-all flex items-center justify-center gap-1.5 shrink-0 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Nouveau Projet</span>
            </button>

            <button
              onClick={() => onNavigate('ai-metre')}
              className="px-4 py-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white text-xs font-black rounded-xl shadow-md transition-all flex items-center justify-center gap-1.5 shrink-0 cursor-pointer"
            >
              <span>Scanner Devis & BPU</span>
              <ArrowUpRight className="w-4 h-4" />
            </button>

            <button
              onClick={() => onNavigate('attachements')}
              className="px-4 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-black rounded-xl shadow-md transition-all flex items-center justify-center gap-1.5 shrink-0 cursor-pointer"
            >
              <span>Tester Attachement N° 2</span>
              <ArrowUpRight className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Sequential Step Badges */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-2 border-t border-white/10 text-xs">
          <button
            onClick={() => onNavigate('markets')}
            className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-left transition-colors cursor-pointer"
          >
            <span className="text-[10px] text-blue-300 font-mono block">01:10 - 02:30</span>
            <strong className="text-white text-xs block mt-0.5">1. Marché R+5 & DQE</strong>
            <span className="text-[10px] text-slate-400">Lots & Articles 4.2 / 4.5</span>
          </button>

          <button
            onClick={() => onNavigate('chantiers')}
            className="p-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-left transition-colors cursor-pointer"
          >
            <span className="text-[10px] text-blue-300 font-mono block">02:30 - 03:12</span>
            <strong className="text-white text-xs block mt-0.5">2. Chantier R+5</strong>
            <span className="text-[10px] text-slate-400">5 Onglets Terrain</span>
          </button>

          <button
            onClick={() => onNavigate('attachements')}
            className="p-2.5 rounded-xl bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 text-left transition-colors cursor-pointer"
          >
            <span className="text-[10px] text-rose-300 font-mono block">03:12 - 04:48</span>
            <strong className="text-rose-200 text-xs block mt-0.5">3. Attachement N° 2</strong>
            <span className="text-[10px] text-rose-300">Blocage 622 ml</span>
          </button>

          <button
            onClick={() => onNavigate('avenants')}
            className="p-2.5 rounded-xl bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/40 text-left transition-colors cursor-pointer"
          >
            <span className="text-[10px] text-amber-300 font-mono block">04:48 - 05:55</span>
            <strong className="text-amber-200 text-xs block mt-0.5">4. Avenant N° 01</strong>
            <span className="text-[10px] text-amber-300">Déblocage Synchro</span>
          </button>

          <button
            onClick={() => onNavigate('situations')}
            className="p-2.5 rounded-xl bg-emerald-500/20 hover:bg-emerald-500/30 border border-emerald-500/40 text-left transition-colors cursor-pointer col-span-2 sm:col-span-1"
          >
            <span className="text-[10px] text-emerald-300 font-mono block">05:55 - 07:28</span>
            <strong className="text-emerald-200 text-xs block mt-0.5">5. Situation N° 2</strong>
            <span className="text-[10px] text-emerald-300">Impression PDF</span>
          </button>
        </div>
      </div>

      {/* Top Banner Alert if Exceedance is detected */}
      {exceedanceAttachements.length > 0 && (
        <div className="p-4 rounded-2xl bg-gradient-to-r from-rose-500/15 via-rose-500/10 to-transparent border border-rose-500/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-sm">
          <div className="flex items-start gap-3.5">
            <div className="p-2.5 rounded-xl bg-rose-600 text-white shrink-0 mt-0.5 shadow-md shadow-rose-600/30">
              <ShieldAlert className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-sm text-rose-900">Alerte Métier : Dépassement Contractuel Actif</span>
                <span className="text-[11px] font-extrabold px-2 py-0.5 rounded-full bg-rose-600 text-white">
                  Action Requise
                </span>
              </div>
              <p className="text-xs text-rose-700 mt-0.5">
                Une quantité saisie dans les feuilles d'attachement dépasse le bordereau contractuel. Régularisez la situation par la génération d'un Avenant.
              </p>
            </div>
          </div>
          <button
            onClick={() => onNavigate('attachements')}
            className="w-full sm:w-auto px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white text-xs font-bold rounded-xl transition-all shadow-md shadow-rose-600/20 flex items-center justify-center gap-1.5 shrink-0"
          >
            <span>Voir l'Attachement & Régulariser</span>
            <ArrowUpRight className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Marchés */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-blue-400 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Portefeuille Marchés HT</span>
            <div className="p-2 rounded-xl bg-blue-50 text-blue-600 group-hover:scale-110 transition-transform">
              <Building2 className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalMarketsAmount)}
            </div>
            <div className="flex items-center gap-2 mt-1.5 text-xs text-slate-500">
              <span className="font-semibold text-blue-600">{markets.length} Projets enregistrés</span>
              <span>•</span>
              <span className="text-slate-400">{distinctClients.length} Maîtres d'Ouvrage</span>
            </div>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-600 to-indigo-600" />
        </div>

        {/* Situations Émises & Encaissées */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-emerald-400 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Facturation Situations TTC</span>
            <div className="p-2 rounded-xl bg-emerald-50 text-emerald-600 group-hover:scale-110 transition-transform">
              <Receipt className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalNetPaidTTC)}
            </div>
            <div className="flex items-center gap-2 mt-1.5 text-xs text-slate-500">
              <span className="font-semibold text-emerald-600 flex items-center gap-0.5">
                <CheckCircle2 className="w-3.5 h-3.5" />
                {situations.filter(s => s.status === 'Validée / Payée').length} payées
              </span>
              <span>•</span>
              <span className="text-slate-400">{situations.length} décomptes</span>
            </div>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-emerald-500" />
        </div>

        {/* Retenue de Garantie (5%) */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-amber-400 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Retenues de Garantie (5%)</span>
            <div className="p-2 rounded-xl bg-amber-50 text-amber-600 group-hover:scale-110 transition-transform">
              <Scale className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalRetenueGarantie)}
            </div>
            <p className="text-xs text-slate-500 mt-1.5">
              Consignées jusqu'à la Réception Définitive (1 an)
            </p>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-amber-500" />
        </div>

        {/* Reste à Réaliser */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs relative overflow-hidden group hover:border-indigo-400 transition-all">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Reste à Réaliser HT</span>
            <div className="p-2 rounded-xl bg-indigo-50 text-indigo-600 group-hover:scale-110 transition-transform">
              <TrendingUp className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(resteARealiser)}
            </div>
            <p className="text-xs text-slate-500 mt-1.5">
              Budget résiduel des marchés en cours
            </p>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-indigo-600" />
        </div>
      </div>

      {/* SECTION PRINCIPALE : PORTEFEUILLE PROJETS & CLASSEMENT PAR MAÎTRE D'OUVRAGE */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden space-y-4 p-5">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-slate-100">
          <div>
            <div className="flex items-center gap-2">
              <span className="p-1.5 rounded-lg bg-blue-50 text-blue-700">
                <FolderKanban className="w-4 h-4" />
              </span>
              <h2 className="text-base font-bold text-slate-900">
                Portefeuille des Projets BTP & Classification par Maître d'Ouvrage
              </h2>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              Visualisez, ajoutez et gérez vos marchés publics et privés classés selon le client mandataire
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {/* Toggle view mode */}
            <div className="bg-slate-100 p-0.5 rounded-xl flex items-center text-xs font-bold">
              <button
                onClick={() => setPortfolioViewMode('by_client')}
                className={`px-3 py-1.5 rounded-lg transition-all ${
                  portfolioViewMode === 'by_client' 
                    ? 'bg-white text-blue-700 shadow-xs' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Par Maître d'Ouvrage
              </button>
              <button
                onClick={() => setPortfolioViewMode('grid')}
                className={`px-3 py-1.5 rounded-lg transition-all ${
                  portfolioViewMode === 'grid' 
                    ? 'bg-white text-blue-700 shadow-xs' 
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Tous les Projets ({filteredMarkets.length})
              </button>
            </div>

            {/* Bouton Ajouter un Projet */}
            <button
              onClick={openAddProjectModal}
              className="px-3.5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-1.5 transition-colors shadow-sm cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Ajouter un Projet</span>
            </button>
          </div>
        </div>

        {/* Barre de Recherche et Filtres par Maître d'Ouvrage */}
        <div className="flex flex-col sm:flex-row gap-3 items-center justify-between pt-1">
          <div className="relative flex-1 w-full">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Rechercher un projet par titre, N° de marché, maître d'ouvrage, commune..."
              value={projectSearch}
              onChange={(e) => setProjectSearch(e.target.value)}
              className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none"
            />
          </div>

          <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto pb-1 sm:pb-0">
            <button
              onClick={() => setSelectedClientFilter('all')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all ${
                selectedClientFilter === 'all'
                  ? 'bg-slate-900 text-white shadow-xs'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              Tous ({markets.length})
            </button>
            {distinctClients.map(client => {
              const count = markets.filter(m => m.client.trim() === client).length;
              return (
                <button
                  key={client}
                  onClick={() => setSelectedClientFilter(client)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-medium whitespace-nowrap transition-all flex items-center gap-1 ${
                    selectedClientFilter === client
                      ? 'bg-blue-600 text-white shadow-xs font-bold'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  <Landmark className="w-3 h-3 opacity-70" />
                  <span className="truncate max-w-[140px]">{client}</span>
                  <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-white/20 font-bold">{count}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* AFFICHAGE 1 : GROUPÉ PAR MAÎTRE D'OUVRAGE */}
        {portfolioViewMode === 'by_client' && (
          <div className="space-y-5 pt-2">
            {clientsWithMarkets.length === 0 ? (
              <div className="p-8 text-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/50">
                <p className="text-xs text-slate-500 font-medium">Aucun projet ne correspond à vos critères de recherche.</p>
                <button
                  onClick={openAddProjectModal}
                  className="mt-3 px-3 py-1.5 bg-blue-600 text-white text-xs font-bold rounded-lg inline-flex items-center gap-1.5"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Créer un nouveau projet</span>
                </button>
              </div>
            ) : (
              clientsWithMarkets.map(group => (
                <div key={group.client} className="rounded-2xl border border-slate-200 bg-slate-50/30 overflow-hidden shadow-2xs">
                  {/* Client Group Header */}
                  <div className="bg-slate-100/90 px-4 py-3 border-b border-slate-200 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                      <div className="p-2 rounded-xl bg-white text-blue-700 shadow-xs border border-slate-200/80">
                        <Landmark className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="font-bold text-xs sm:text-sm text-slate-900">{group.client}</h3>
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-100 text-blue-800">
                            {group.count} Marché{group.count > 1 ? 's' : ''}
                          </span>
                        </div>
                        <span className="text-[11px] text-slate-500">
                          Maître d'Ouvrage Mandataire
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-3 self-end sm:self-auto">
                      <div className="text-right">
                        <span className="text-[10px] uppercase font-bold text-slate-400 block">Total Engagements HT</span>
                        <strong className="text-xs sm:text-sm font-mono font-black text-slate-900">
                          {formatDA(group.totalHT)}
                        </strong>
                      </div>
                    </div>
                  </div>

                  {/* Projects Cards List for this Client */}
                  <div className="p-3.5 grid grid-cols-1 md:grid-cols-2 gap-3">
                    {group.markets.map(m => {
                      const isSelected = m.id === selectedMarketId;
                      const chantier = chantiers.find(c => c.marketId === m.id);
                      const mArticles = articles.filter(a => a.marketId === m.id);
                      const execAmount = mArticles.reduce((acc, a) => acc + (a.previousExecutedQty * a.unitPriceHT), 0);
                      const finProgress = m.currentAmountHT > 0 ? Math.round((execAmount / m.currentAmountHT) * 100) : 0;
                      const physProgress = chantier ? chantier.physicalProgressPct : 25;

                      return (
                        <div 
                          key={m.id}
                          className={`p-4 rounded-xl border transition-all space-y-3 relative ${
                            isSelected 
                              ? 'bg-blue-50/50 border-blue-400 shadow-sm ring-1 ring-blue-400/50' 
                              : 'bg-white border-slate-200 hover:border-slate-300'
                          }`}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <span className="font-mono font-bold text-[11px] px-2 py-0.5 rounded-md bg-slate-900 text-white">
                                  {m.number}
                                </span>
                                <span className="text-[10px] font-semibold px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800">
                                  {m.status}
                                </span>
                                <span className="text-[10px] font-medium px-2 py-0.5 rounded-md bg-slate-100 text-slate-600">
                                  {m.type}
                                </span>
                                {isSelected && (
                                  <span className="text-[10px] font-black px-2 py-0.5 rounded-md bg-blue-600 text-white flex items-center gap-1">
                                    <Check className="w-3 h-3" />
                                    Projet Actif
                                  </span>
                                )}
                              </div>
                              <h4 className="font-bold text-xs sm:text-sm text-slate-900 mt-2 line-clamp-2">
                                {m.title}
                              </h4>
                            </div>
                          </div>

                          <div className="grid grid-cols-2 gap-2 text-xs pt-1 border-t border-slate-100">
                            <div>
                              <span className="text-[10px] text-slate-400 block">Localisation & Wilaya</span>
                              <span className="font-medium text-slate-700 flex items-center gap-1 mt-0.5 text-[11px]">
                                <MapPin className="w-3 h-3 text-slate-400" />
                                {m.commune} ({getWilayaName(m.wilaya)})
                              </span>
                            </div>

                            <div className="text-right">
                              <span className="text-[10px] text-slate-400 block">Montant Actuel HT</span>
                              <strong className="font-mono font-bold text-blue-700 text-xs block mt-0.5">
                                {formatDA(m.currentAmountHT)}
                              </strong>
                            </div>
                          </div>

                          {/* Progress Dual Indicators */}
                          <div className="space-y-1 pt-1">
                            <div className="flex justify-between text-[11px]">
                              <span className="text-slate-500">Physique : <strong className="text-amber-600 font-mono">{physProgress}%</strong></span>
                              <span className="text-slate-500">Financier : <strong className="text-blue-700 font-mono">{finProgress}%</strong></span>
                            </div>
                            <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden flex">
                              <div className="bg-amber-500 h-1.5" style={{ width: `${physProgress}%` }} />
                            </div>
                          </div>

                          {/* Action Buttons */}
                          <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-100 text-xs">
                            <button
                              onClick={() => setSelectedMarketId(m.id)}
                              className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                                isSelected
                                  ? 'bg-blue-600 text-white cursor-default'
                                  : 'bg-slate-100 hover:bg-blue-50 hover:text-blue-700 text-slate-700'
                              }`}
                            >
                              {isSelected ? '✓ Sélectionné' : 'Définir comme Actif'}
                            </button>

                            <div className="flex items-center gap-1.5">
                              <button
                                onClick={() => {
                                  setSelectedMarketId(m.id);
                                  onNavigate('markets');
                                }}
                                className="px-2.5 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 text-[11px] font-semibold transition-colors"
                              >
                                DQE / Lots
                              </button>
                              <button
                                onClick={() => {
                                  setSelectedMarketId(m.id);
                                  onNavigate('chantiers');
                                }}
                                className="px-2.5 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-slate-700 text-[11px] font-semibold transition-colors"
                              >
                                Chantier
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
        )}

        {/* AFFICHAGE 2 : GRILLE DE TOUS LES PROJETS */}
        {portfolioViewMode === 'grid' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3.5 pt-2">
            {filteredMarkets.length === 0 ? (
              <div className="col-span-full p-8 text-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/50">
                <p className="text-xs text-slate-500 font-medium">Aucun projet ne correspond à votre recherche.</p>
              </div>
            ) : (
              filteredMarkets.map(m => {
                const isSelected = m.id === selectedMarketId;
                const chantier = chantiers.find(c => c.marketId === m.id);
                const physProgress = chantier ? chantier.physicalProgressPct : 30;

                return (
                  <div
                    key={m.id}
                    className={`p-4 rounded-xl border transition-all space-y-3 ${
                      isSelected
                        ? 'bg-blue-50/40 border-blue-400 shadow-sm ring-1 ring-blue-400/50'
                        : 'bg-white border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono font-bold text-[11px] px-2 py-0.5 rounded-md bg-slate-900 text-white">
                        {m.number}
                      </span>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800">
                        {m.status}
                      </span>
                    </div>

                    <div>
                      <h4 className="font-bold text-xs sm:text-sm text-slate-900 line-clamp-2">
                        {m.title}
                      </h4>
                      <p className="text-[11px] text-blue-700 font-medium flex items-center gap-1 mt-1">
                        <Landmark className="w-3 h-3 text-blue-600 shrink-0" />
                        <span className="truncate">{m.client}</span>
                      </p>
                    </div>

                    <div className="flex justify-between items-center text-xs pt-1 border-t border-slate-100 font-mono">
                      <span className="text-slate-400 text-[10px]">Montant HT</span>
                      <strong className="text-slate-900">{formatDA(m.currentAmountHT)}</strong>
                    </div>

                    <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-100">
                      <button
                        onClick={() => setSelectedMarketId(m.id)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                          isSelected
                            ? 'bg-blue-600 text-white'
                            : 'bg-slate-100 hover:bg-blue-50 hover:text-blue-700 text-slate-700'
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
                        Voir DQE
                      </button>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}
      </div>

      {/* Main Focus: Focus Marché Actif & Avancement Physique / Financier */}
      {currentMarket && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-slate-100">
            <div>
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-0.5 text-xs font-extrabold rounded-md bg-blue-100 text-blue-800 font-mono">
                  MARCHÉ ACTIF : {currentMarket.number}
                </span>
                <span className="text-xs px-2 py-0.5 rounded-md font-semibold bg-emerald-100 text-emerald-800">
                  {currentMarket.status}
                </span>
                <span className="text-xs text-slate-500 hidden sm:inline">
                  ODS : {currentMarket.odsDate} ({currentMarket.durationMonths} mois)
                </span>
              </div>
              <h2 className="text-lg font-bold text-slate-900 mt-1">
                {currentMarket.title}
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Maître d'Ouvrage : <strong className="text-slate-700">{currentMarket.client}</strong> • BET : <strong className="text-slate-700">{currentMarket.bet}</strong> • Wilaya : <strong className="text-slate-700">{getWilayaName(currentMarket.wilaya)}</strong>
              </p>
            </div>

            {/* Quick Actions */}
            <div className="flex items-center gap-2">
              <button
                onClick={() => onNavigate('attachements')}
                className="px-3.5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-1.5 transition-colors shadow-sm"
              >
                <FileText className="w-4 h-4" />
                <span>Saisie Attachement</span>
              </button>
              <button
                onClick={() => onNavigate('situations')}
                className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold flex items-center gap-1.5 transition-colors shadow-sm"
              >
                <Receipt className="w-4 h-4" />
                <span>Générer Situation</span>
              </button>
            </div>
          </div>

          {/* Dual Progress Gauges: Physique vs Financier */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/60 space-y-3">
              <div className="flex items-center justify-between text-xs font-bold text-slate-700">
                <span className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-amber-500" />
                  Avancement Physique Constaté (Chantier)
                </span>
                <span className="text-base font-extrabold text-amber-600 font-mono">
                  {currentPhysicalProgress} %
                </span>
              </div>
              <div className="w-full bg-slate-200 rounded-full h-3 overflow-hidden">
                <div 
                  className="bg-gradient-to-r from-amber-500 to-amber-600 h-3 rounded-full transition-all duration-500"
                  style={{ width: `${currentPhysicalProgress}%` }}
                />
              </div>
              <p className="text-[11px] text-slate-500">
                Validé par le chef de chantier sur feuilles de métré contradictoires.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/60 space-y-3">
              <div className="flex items-center justify-between text-xs font-bold text-slate-700">
                <span className="flex items-center gap-1.5">
                  <div className="w-2.5 h-2.5 rounded-full bg-blue-600" />
                  Avancement Financier Certifié (Situations)
                </span>
                <span className="text-base font-extrabold text-blue-600 font-mono">
                  {currentFinancialProgress} %
                </span>
              </div>
              <div className="w-full bg-slate-200 rounded-full h-3 overflow-hidden">
                <div 
                  className="bg-gradient-to-r from-blue-600 to-indigo-600 h-3 rounded-full transition-all duration-500"
                  style={{ width: `${currentFinancialProgress}%` }}
                />
              </div>
              <p className="text-[11px] text-slate-500">
                Montant certifié : {formatDA(marketExecutedAmount)} sur {formatDA(currentMarket.currentAmountHT)} HT
              </p>
            </div>
          </div>

          {/* Financial Breakdown Table for the market */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
            <div className="p-3 rounded-xl bg-slate-50/80 border border-slate-200/50">
              <span className="text-[11px] font-semibold text-slate-500">Montant Initial HT</span>
              <p className="text-xs sm:text-sm font-bold text-slate-800 font-mono mt-1">
                {formatDA(currentMarket.initialAmountHT)}
              </p>
            </div>
            <div className="p-3 rounded-xl bg-slate-50/80 border border-slate-200/50">
              <span className="text-[11px] font-semibold text-slate-500">Avenants Approuvés</span>
              <p className="text-xs sm:text-sm font-bold text-emerald-600 font-mono mt-1">
                {formatDA(currentMarket.currentAmountHT - currentMarket.initialAmountHT)}
              </p>
            </div>
            <div className="p-3 rounded-xl bg-slate-50/80 border border-slate-200/50">
              <span className="text-[11px] font-semibold text-slate-500">Taux TVA Appliqué</span>
              <p className="text-xs sm:text-sm font-bold text-slate-800 font-mono mt-1">
                {currentMarket.tvaRate} % (Réglementaire)
              </p>
            </div>
            <div className="p-3 rounded-xl bg-slate-50/80 border border-slate-200/50">
              <span className="text-[11px] font-semibold text-slate-500">Caution de Bonne Exécution</span>
              <p className="text-xs sm:text-sm font-bold text-blue-700 font-mono mt-1">
                {formatDA(currentMarket.bankGuarantees.bonneExecution)}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Two columns: Recent Attachements & Official Situations */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Attachements */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-blue-600" />
              <h3 className="font-bold text-sm text-slate-900">Derniers Attachements Chantier</h3>
            </div>
            <button
              onClick={() => onNavigate('attachements')}
              className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-0.5"
            >
              <span>Tous ({attachements.length})</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-2.5">
            {attachements.slice(0, 3).map(att => {
              const hasExceedance = att.items.some(i => i.hasExceedance);
              return (
                <div 
                  key={att.id}
                  className={`
                    p-3.5 rounded-xl border transition-all flex items-center justify-between gap-3
                    ${hasExceedance 
                      ? 'bg-rose-50/60 border-rose-200 hover:border-rose-300' 
                      : 'bg-slate-50/60 border-slate-200/60 hover:bg-slate-50'
                    }
                  `}
                >
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-xs text-slate-900">
                        Attachement N° {att.number.toString().padStart(2, '0')}
                      </span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                        att.status === 'Validé' 
                          ? 'bg-emerald-100 text-emerald-800' 
                          : att.status === 'Soumis Chef Chantier'
                          ? 'bg-blue-100 text-blue-800'
                          : 'bg-slate-200 text-slate-700'
                      }`}>
                        {att.status}
                      </span>
                      {hasExceedance && (
                        <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-rose-600 text-white animate-pulse">
                          Dépassement !
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-500">
                      Zone : <span className="font-medium text-slate-700">{att.zone}</span> • {att.items.length} articles
                    </p>
                  </div>

                  <button
                    onClick={() => onNavigate('attachements')}
                    className="text-xs font-semibold px-2.5 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 transition-colors shrink-0"
                  >
                    Ouvrir
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Recent Situations */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Receipt className="w-4 h-4 text-emerald-600" />
              <h3 className="font-bold text-sm text-slate-900">Situations & Décomptes Émis</h3>
            </div>
            <button
              onClick={() => onNavigate('situations')}
              className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-0.5"
            >
              <span>Toutes ({situations.length})</span>
              <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="space-y-2.5">
            {situations.map(sit => (
              <div 
                key={sit.id}
                className="p-3.5 rounded-xl border border-slate-200/60 bg-slate-50/60 hover:bg-slate-50 transition-all flex items-center justify-between gap-3"
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-xs text-slate-900">
                      Situation N° {sit.number.toString().padStart(2, '0')}
                    </span>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800">
                      {sit.status}
                    </span>
                    <span className="text-xs text-slate-400 font-mono">
                      {sit.date}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500">
                    Net à payer TTC : <strong className="text-slate-900 font-mono">{formatDA(sit.netToPayTTC)}</strong>
                  </p>
                </div>

                <button
                  onClick={() => onNavigate('documents')}
                  className="text-xs font-semibold px-2.5 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-700 hover:bg-slate-100 transition-colors shrink-0"
                >
                  Imprimer PDF
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* MODAL : AJOUTER UN NOUVEAU PROJET / MARCHÉ BTP */}
      {isAddingProject && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-2xl max-h-[92vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50 shrink-0">
              <div className="flex items-center gap-2">
                <span className="p-2 rounded-lg bg-blue-100 text-blue-700">
                  <Building2 className="w-5 h-5" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Créer un Nouveau Projet / Marché BTP</h3>
                  <p className="text-[11px] text-slate-500">
                    Enregistrement du marché, affiliation du Maître d'Ouvrage et initialisation du chantier
                  </p>
                </div>
              </div>
              <button 
                type="button" 
                onClick={() => setIsAddingProject(false)}
                className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleCreateProjectSubmit} className="p-5 space-y-4 overflow-y-auto text-xs">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
                <div className="sm:col-span-2">
                  <label className="font-bold text-slate-700 block mb-1">
                    Intitulé des Travaux / Objet du Projet *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Travaux de Réalisation d'un Complexe Sportif de Proximité (CSP)"
                    value={newTitle}
                    onChange={(e) => setNewTitle(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Numéro Officiel du Marché / Réf. *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="ex: 2026/04/DEP ou 2026/08/OPGI"
                    value={newNumber}
                    onChange={(e) => setNewNumber(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Type de Contrat *
                  </label>
                  <select
                    value={newType}
                    onChange={(e) => setNewType(e.target.value as MarketType)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  >
                    <option value="Marché Public">Marché Public</option>
                    <option value="Convention">Convention</option>
                    <option value="Contrat">Contrat Privé / Sous-traitance</option>
                    <option value="Bon de Commande">Bon de Commande BTP</option>
                  </select>
                </div>

                <div className="sm:col-span-2">
                  <div className="flex items-center justify-between mb-1">
                    <label className="font-bold text-slate-700">
                      Maître d'Ouvrage (Client Mandataire) *
                    </label>
                    <span className="text-[10px] text-slate-400">Classera le projet dans le tableau de bord</span>
                  </div>
                  <input
                    type="text"
                    required
                    list="client-suggestions"
                    placeholder="ex: OPGI Sidi Bel Abbès, Direction de l'Urbanisme DUC Oran, Sonatrach..."
                    value={newClient}
                    onChange={(e) => setNewClient(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                  <datalist id="client-suggestions">
                    {COMMON_CLIENT_SUGGESTIONS.map(s => (
                      <option key={s} value={s} />
                    ))}
                  </datalist>

                  <div className="flex flex-wrap gap-1 mt-1.5">
                    {['OPGI', 'DUC', 'DEP', 'DTP', 'APC', 'Sonatrach'].map(tag => (
                      <button
                        type="button"
                        key={tag}
                        onClick={() => setNewClient(`${tag} ${newCommune || 'Wilaya'}`)}
                        className="text-[10px] px-2 py-0.5 rounded bg-slate-100 hover:bg-blue-100 hover:text-blue-800 text-slate-600 transition-colors"
                      >
                        + {tag}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Bureau d'Études (BET / Suivi)
                  </label>
                  <input
                    type="text"
                    placeholder="ex: BET Urbapro Oran"
                    value={newBet}
                    onChange={(e) => setNewBet(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Entreprise Titulaire (Votre Société)
                  </label>
                  <input
                    type="text"
                    placeholder="ex: SARL EGBTP EL MOUSTAKBEL"
                    value={newEnterprise}
                    onChange={(e) => setNewEnterprise(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Wilaya d'Implantation *
                  </label>
                  <select
                    value={newWilaya}
                    onChange={(e) => setNewWilaya(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  >
                    {ALGERIAN_WILAYAS.map(w => (
                      <option key={w.code} value={w.code}>
                        {w.code} - {w.name} ({w.nameAr})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Commune / Lieu-dit *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Sidi Bel Abbès, Bir El Djir..."
                    value={newCommune}
                    onChange={(e) => setNewCommune(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Montant Initial HT du Marché (DA) *
                  </label>
                  <input
                    type="number"
                    min="100000"
                    step="50000"
                    required
                    value={newInitialAmountHT}
                    onChange={(e) => setNewInitialAmountHT(parseFloat(e.target.value) || 0)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono font-bold focus:ring-2 focus:ring-blue-500 focus:outline-none text-xs"
                  />
                  <span className="text-[10px] text-blue-700 font-mono mt-0.5 block">
                    {formatDA(newInitialAmountHT)}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="font-bold text-slate-700 block mb-1">Taux TVA</label>
                    <select
                      value={newTvaRate}
                      onChange={(e) => setNewTvaRate(Number(e.target.value) as 9 | 19)}
                      className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 text-xs"
                    >
                      <option value={9}>9% (Logement)</option>
                      <option value={19}>19% (Général)</option>
                    </select>
                  </div>

                  <div>
                    <label className="font-bold text-slate-700 block mb-1">Retenue (RG)</label>
                    <select
                      value={newRetenueGarantieRate}
                      onChange={(e) => setNewRetenueGarantieRate(Number(e.target.value) as 5 | 10)}
                      className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 text-xs"
                    >
                      <option value={5}>5% (Standard)</option>
                      <option value={10}>10%</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Date Ordre de Service (ODS) *
                  </label>
                  <input
                    type="date"
                    required
                    value={newOdsDate}
                    onChange={(e) => setNewOdsDate(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Délai Contractuel (Mois)
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="60"
                    value={newDurationMonths}
                    onChange={(e) => setNewDurationMonths(parseInt(e.target.value) || 12)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Chef de Chantier Assigné
                  </label>
                  <input
                    type="text"
                    placeholder="ex: Farid Mansouri"
                    value={newChefChantier}
                    onChange={(e) => setNewChefChantier(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 text-xs"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">
                    Tél. Chef de Chantier
                  </label>
                  <input
                    type="text"
                    placeholder="ex: 0661 78 90 12"
                    value={newChefPhone}
                    onChange={(e) => setNewChefPhone(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 text-xs"
                  />
                </div>
              </div>

              <div className="p-3 bg-blue-50/70 rounded-xl border border-blue-100 flex items-start gap-2 text-[11px] text-blue-800">
                <CheckCircle2 className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
                <span>
                  La création du projet initialise automatiquement la structure arborescente du marché (Lot 01) ainsi que la fiche de suivi de chantier correspondante.
                </span>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingProject(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition-colors shadow-sm"
                >
                  Enregistrer et Activer le Projet
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};