import React, { useState } from 'react';
import { 
  HardHat, 
  MapPin, 
  Phone, 
  UserCheck, 
  FileText, 
  Receipt, 
  Scale, 
  DollarSign, 
  Users, 
  Layers, 
  ArrowRight,
  TrendingUp,
  AlertTriangle,
  Calendar,
  CloudSun,
  Plus,
  Pencil,
  Trash2,
  X
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';
import { ActiveTab } from '../components/Sidebar';
import { Chantier, Worker, WorkerTrade } from '../types/btp';

const TRADES: WorkerTrade[] = [
  'Chef de Chantier',
  'Maçon',
  'Coffreur',
  'Ferrailleur',
  'Manœuvre qualifié',
  'Manœuvre ordinaire',
  'Conducteur d\'engin',
  'Électricien',
  'Plombier',
  'Topographe',
  'Peintre',
  'Soudeur',
  'Autre'
];

interface ChantiersViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

export const ChantiersView: React.FC<ChantiersViewProps> = ({ onNavigate }) => {
  const { 
    chantiers, 
    markets, 
    articles, 
    workers, 
    addWorker,
    updateWorker,
    deleteWorker,
    expenses, 
    attachements, 
    avenants, 
    situations, 
    selectedMarketId, 
    setSelectedMarketId 
  } = useBTP();

  const [selectedChantierId, setSelectedChantierId] = useState<string>(
    chantiers[0]?.id || ''
  );

  // 5 Onglets Internes Spécifiés dans Module 2 (02:30 -> 03:12)
  const [internalTab, setInternalTab] = useState<'articles' | 'equipe' | 'achats' | 'attachements' | 'avenants_situations'>('articles');

  // Modal State for Workers in Chantier
  const [isAddingWorker, setIsAddingWorker] = useState(false);
  const [newWorkerName, setNewWorkerName] = useState('');
  const [newWorkerTrade, setNewWorkerTrade] = useState<WorkerTrade>('Maçon');
  const [newWorkerRate, setNewWorkerRate] = useState<number>(2700);
  const [newWorkerCnas, setNewWorkerCnas] = useState('');
  const [newWorkerPhone, setNewWorkerPhone] = useState('');
  const [newWorkerStatus, setNewWorkerStatus] = useState<Worker['status']>('Actif');
  const [newWorkerDays, setNewWorkerDays] = useState<number>(24);
  const [editingWorker, setEditingWorker] = useState<Worker | null>(null);

  const selectedChantier = chantiers.find(c => c.id === selectedChantierId) || chantiers[0];
  const market = markets.find(m => m.id === selectedChantier.marketId);
  const wilaya = ALGERIAN_WILAYAS.find(w => w.code === selectedChantier.wilaya);

  // Articles liés à ce chantier
  const chantierArticles = articles.filter(a => a.marketId === selectedChantier.marketId);
  const chantierWorkers = workers.filter(w => w.chantierId === selectedChantier.id);
  const chantierExpenses = expenses.filter(e => e.chantierId === selectedChantier.id);
  const chantierAttachements = attachements.filter(a => a.chantierId === selectedChantier.id);
  const chantierSituations = situations.filter(s => s.chantierId === selectedChantier.id);

  const totalDepensesChantier = chantierExpenses.reduce((acc, e) => acc + e.amountDA, 0);

  const openAddWorkerModal = () => {
    setNewWorkerName('');
    setNewWorkerTrade('Maçon');
    setNewWorkerRate(2700);
    setNewWorkerCnas('19882200' + Math.floor(1000 + Math.random() * 9000));
    setNewWorkerPhone('0550 ' + Math.floor(10 + Math.random() * 89) + ' ' + Math.floor(10 + Math.random() * 89) + ' ' + Math.floor(10 + Math.random() * 89));
    setNewWorkerStatus('Actif');
    setNewWorkerDays(24);
    setIsAddingWorker(true);
  };

  const handleAddWorkerSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWorkerName.trim()) return;

    const newW: Worker = {
      id: `w-${Date.now()}`,
      chantierId: selectedChantier.id,
      fullName: newWorkerName.trim(),
      trade: newWorkerTrade,
      dailyRateDA: Number(newWorkerRate) || 0,
      cnasNumber: newWorkerCnas.trim() || 'Non renseigné',
      phone: newWorkerPhone.trim() || 'Non renseigné',
      status: newWorkerStatus,
      daysWorkedCurrentMonth: Number(newWorkerDays) || 0
    };

    addWorker(newW);
    setIsAddingWorker(false);
  };

  const handleEditWorkerSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingWorker || !editingWorker.fullName.trim()) return;
    updateWorker(editingWorker);
    setEditingWorker(null);
  };

  const handleDeleteWorker = (w: Worker) => {
    if (window.confirm(`Confirmez-vous le retrait de l'ouvrier "${w.fullName}" (${w.trade}) de ce chantier ?`)) {
      deleteWorker(w.id);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-amber-500/10 text-amber-600">
              <HardHat className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Liaison Marché ↔ Terrain & Supervision
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            {selectedChantier.name}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Rattaché au <strong className="text-slate-800">{market?.number}</strong> • Wilaya : <strong className="text-slate-800">{wilaya?.code} - {wilaya?.name}</strong> • Chef de Chantier : <strong className="text-blue-700">{selectedChantier.chefChantierName}</strong>
          </p>
        </div>

        {/* Chantier Cards Selector */}
        <div className="flex items-center gap-2">
          {chantiers.map(ch => (
            <button
              key={ch.id}
              onClick={() => {
                setSelectedChantierId(ch.id);
                setSelectedMarketId(ch.marketId);
              }}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                ch.id === selectedChantier.id 
                  ? 'bg-slate-900 text-white shadow-sm' 
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {ch.name}
            </button>
          ))}
        </div>
      </div>

      {/* Main Chantier Identity Card & Progress */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/60 space-y-1">
          <span className="text-[11px] font-bold text-slate-500 uppercase">Chef de Chantier Assigné</span>
          <p className="text-sm font-black text-slate-900 flex items-center gap-1.5">
            <UserCheck className="w-4 h-4 text-blue-600" />
            {selectedChantier.chefChantierName}
          </p>
          <span className="text-xs text-slate-500 font-mono flex items-center gap-1">
            <Phone className="w-3 h-3 text-slate-400" />
            {selectedChantier.chefChantierPhone}
          </span>
        </div>

        <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/60 space-y-1">
          <span className="text-[11px] font-bold text-slate-500 uppercase">Avancement Physique Global</span>
          <p className="text-xl font-black text-amber-600 font-mono">
            {selectedChantier.physicalProgressPct} %
          </p>
          <div className="w-full bg-slate-200 rounded-full h-2 overflow-hidden mt-1">
            <div className="bg-amber-500 h-2 rounded-full" style={{ width: `${selectedChantier.physicalProgressPct}%` }} />
          </div>
        </div>

        <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/60 space-y-1">
          <span className="text-[11px] font-bold text-slate-500 uppercase">Dépenses Imputées au Chantier</span>
          <p className="text-lg font-black text-rose-700 font-mono">
            {formatDA(totalDepensesChantier)}
          </p>
          <span className="text-[11px] text-slate-500">Matériaux, caisse et gasoil engins</span>
        </div>

        <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200/60 space-y-1">
          <span className="text-[11px] font-bold text-slate-500 uppercase">Statut & Climat</span>
          <div className="flex items-center gap-2 mt-1">
            <span className="px-2 py-0.5 rounded-md text-[10px] font-bold bg-emerald-100 text-emerald-800">
              {selectedChantier.status}
            </span>
            <span className="text-xs text-amber-700 font-medium flex items-center gap-1">
              <CloudSun className="w-3.5 h-3.5" />
              22°C Ensoleillé
            </span>
          </div>
          <p className="text-[10px] text-slate-400 mt-1">Localisation : {selectedChantier.address}</p>
        </div>
      </div>

      {/* 5 ONGLETS INTERNES DU CHANTIER (02:30 -> 03:12) */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        {/* Tab Headers */}
        <div className="flex items-center border-b border-slate-200 bg-slate-50/70 overflow-x-auto text-xs font-bold">
          <button
            onClick={() => setInternalTab('articles')}
            className={`px-5 py-3.5 border-b-2 flex items-center gap-2 transition-all ${
              internalTab === 'articles'
                ? 'border-blue-600 text-blue-600 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <Layers className="w-4 h-4" />
            <span>1. Aperçu & Avancement des Articles ({chantierArticles.length})</span>
          </button>

          <button
            onClick={() => setInternalTab('equipe')}
            className={`px-5 py-3.5 border-b-2 flex items-center gap-2 transition-all ${
              internalTab === 'equipe'
                ? 'border-blue-600 text-blue-600 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <Users className="w-4 h-4" />
            <span>2. Équipe & Ouvriers ({chantierWorkers.length})</span>
          </button>

          <button
            onClick={() => setInternalTab('achats')}
            className={`px-5 py-3.5 border-b-2 flex items-center gap-2 transition-all ${
              internalTab === 'achats'
                ? 'border-blue-600 text-blue-600 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <DollarSign className="w-4 h-4" />
            <span>3. Achats & Dépenses du Chantier ({chantierExpenses.length})</span>
          </button>

          <button
            onClick={() => setInternalTab('attachements')}
            className={`px-5 py-3.5 border-b-2 flex items-center gap-2 transition-all ${
              internalTab === 'attachements'
                ? 'border-blue-600 text-blue-600 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <FileText className="w-4 h-4" />
            <span>4. Attachements & Métrés ({chantierAttachements.length})</span>
          </button>

          <button
            onClick={() => setInternalTab('avenants_situations')}
            className={`px-5 py-3.5 border-b-2 flex items-center gap-2 transition-all ${
              internalTab === 'avenants_situations'
                ? 'border-blue-600 text-blue-600 bg-white'
                : 'border-transparent text-slate-600 hover:text-slate-900'
            }`}
          >
            <Receipt className="w-4 h-4" />
            <span>5. Avenants & Situations ({chantierSituations.length})</span>
          </button>
        </div>

        {/* Tab Body */}
        <div className="p-5">
          {/* 1. ARTICLES & AVANCEMENT DU LOT */}
          {internalTab === 'articles' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between text-xs text-slate-500">
                <span>Articles hérités du Marché pour ce Chantier :</span>
                <span className="font-mono font-bold text-slate-700">{chantierArticles.length} Articles</span>
              </div>

              <div className="overflow-x-auto rounded-xl border border-slate-200">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-slate-50 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                      <th className="py-2.5 px-3">N° Art</th>
                      <th className="py-2.5 px-3">Désignation</th>
                      <th className="py-2.5 px-2 text-center">Unité</th>
                      <th className="py-2.5 px-3 text-right">Qté Prévue</th>
                      <th className="py-2.5 px-3 text-right">Qté Réalisée</th>
                      <th className="py-2.5 px-3 text-right">Reste à Faire</th>
                      <th className="py-2.5 px-3 text-center">Avancement %</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {chantierArticles.map(art => {
                      const totalExec = art.previousExecutedQty;
                      const reste = Math.max(0, art.currentContractQuantity - totalExec);
                      const pct = art.currentContractQuantity > 0 ? Math.min(100, Math.round((totalExec / art.currentContractQuantity) * 100)) : 0;

                      return (
                        <tr key={art.id} className="hover:bg-slate-50">
                          <td className="py-2.5 px-3 font-mono font-bold text-blue-700">{art.code}</td>
                          <td className="py-2.5 px-3 font-medium text-slate-900">{art.designation}</td>
                          <td className="py-2.5 px-2 text-center font-mono font-semibold text-slate-600">{art.unit}</td>
                          <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-800">{formatNumberDZ(art.currentContractQuantity)}</td>
                          <td className="py-2.5 px-3 text-right font-mono font-bold text-emerald-700">{formatNumberDZ(totalExec)}</td>
                          <td className="py-2.5 px-3 text-right font-mono text-slate-500">{formatNumberDZ(reste)}</td>
                          <td className="py-2.5 px-3 text-center">
                            <div className="flex items-center justify-center gap-1.5">
                              <div className="w-16 bg-slate-200 rounded-full h-1.5 overflow-hidden">
                                <div className={`h-1.5 rounded-full ${pct >= 100 ? 'bg-emerald-500' : 'bg-blue-600'}`} style={{ width: `${pct}%` }} />
                              </div>
                              <span className="font-mono text-[11px] font-bold text-slate-700">{pct}%</span>
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

          {/* 2. ÉQUIPE DU CHANTIER */}
          {internalTab === 'equipe' && (
            <div className="space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs">
                <div>
                  <span className="font-bold text-slate-800">Personnel affecté au chantier</span>
                  <span className="text-slate-500 ml-2 font-mono">({chantierWorkers.length} ouvriers)</span>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={openAddWorkerModal}
                    className="px-3 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold flex items-center gap-1.5 transition-colors shadow-xs"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Ajouter un Ouvrier</span>
                  </button>
                  <button
                    onClick={() => onNavigate('hr')}
                    className="px-3 py-1.5 bg-blue-50 text-blue-700 rounded-xl font-bold hover:bg-blue-100 transition-colors"
                  >
                    Module Paie & Pointages (CNAS)
                  </button>
                </div>
              </div>

              {chantierWorkers.length === 0 ? (
                <div className="p-8 text-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/50">
                  <p className="text-xs text-slate-500 font-medium">Aucun ouvrier n'est actuellement affecté à ce chantier.</p>
                  <button
                    onClick={openAddWorkerModal}
                    className="mt-3 px-3 py-1.5 bg-blue-600 text-white text-xs font-bold rounded-lg inline-flex items-center gap-1.5"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Affecter un premier ouvrier</span>
                  </button>
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                  {chantierWorkers.map(w => (
                    <div key={w.id} className="p-3.5 rounded-xl border border-slate-200 bg-slate-50/50 space-y-2 hover:border-blue-200 transition-all">
                      <div className="flex justify-between items-start">
                        <div>
                          <span className="font-bold text-xs text-slate-900 block">{w.fullName}</span>
                          <div className="flex items-center gap-1 mt-0.5">
                            <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-blue-100 text-blue-800">{w.trade}</span>
                            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                              w.status === 'Actif' ? 'bg-emerald-100 text-emerald-800' :
                              w.status === 'Congé' ? 'bg-blue-100 text-blue-800' : 'bg-amber-100 text-amber-800'
                            }`}>
                              {w.status}
                            </span>
                          </div>
                        </div>
                        <div className="flex items-center gap-1">
                          <button
                            onClick={() => setEditingWorker(w)}
                            title="Modifier l'ouvrier"
                            className="p-1 rounded-md text-slate-400 hover:text-blue-600 hover:bg-blue-50 transition-colors"
                          >
                            <Pencil className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleDeleteWorker(w)}
                            title="Supprimer l'ouvrier"
                            className="p-1 rounded-md text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                      <p className="text-[11px] text-slate-500 font-mono">Tél : {w.phone} • CNAS : {w.cnasNumber}</p>
                      <div className="flex justify-between text-xs pt-1 border-t border-slate-200/60 font-mono">
                        <span className="text-slate-500">Tarif jour : {formatDA(w.dailyRateDA)}</span>
                        <strong className="text-emerald-700">{w.daysWorkedCurrentMonth}j pointés</strong>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* 3. ACHATS & DÉPENSES */}
          {internalTab === 'achats' && (
            <div className="space-y-4">
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">Dépenses imputées à ce chantier pour le calcul du coût de revient</span>
                <button
                  onClick={() => onNavigate('finance')}
                  className="px-3 py-1 bg-blue-50 text-blue-700 rounded-lg font-bold hover:bg-blue-100 transition-colors"
                >
                  + Ajouter une Dépense
                </button>
              </div>

              <div className="overflow-x-auto rounded-xl border border-slate-200">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-slate-50 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                      <th className="p-2.5">Date</th>
                      <th className="p-2.5">Catégorie</th>
                      <th className="p-2.5">Désignation</th>
                      <th className="p-2.5">Mode</th>
                      <th className="p-2.5 text-right">Montant (DA)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {chantierExpenses.map(e => (
                      <tr key={e.id} className="hover:bg-slate-50">
                        <td className="p-2.5 font-mono text-slate-500">{e.date}</td>
                        <td className="p-2.5 font-semibold text-slate-800">{e.category}</td>
                        <td className="p-2.5 text-slate-600">{e.description}</td>
                        <td className="p-2.5 text-slate-500">{e.paymentMode}</td>
                        <td className="p-2.5 text-right font-mono font-bold text-rose-700">-{formatDA(e.amountDA)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* 4. ATTACHEMENTS & MÉTRÉS */}
          {internalTab === 'attachements' && (
            <div className="space-y-4">
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">Feuilles d'attachement rattachées à ce chantier</span>
                <button
                  onClick={() => onNavigate('attachements')}
                  className="px-4 py-1.5 bg-blue-600 text-white rounded-xl text-xs font-bold hover:bg-blue-700 transition-colors"
                >
                  Accéder à la Saisie Attachements
                </button>
              </div>

              <div className="space-y-2">
                {chantierAttachements.map(att => (
                  <div key={att.id} className="p-3 rounded-xl border border-slate-200 flex items-center justify-between">
                    <div>
                      <strong className="text-xs font-bold text-slate-900 block">Attachement N° {att.number.toString().padStart(2, '0')}</strong>
                      <span className="text-[11px] text-slate-500">Zone : {att.zone} • {att.items.length} articles saisis</span>
                    </div>
                    <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full ${
                      att.status === 'Validé' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                    }`}>
                      {att.status}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* 5. AVENANTS & SITUATIONS */}
          {internalTab === 'avenants_situations' && (
            <div className="space-y-4">
              <div className="flex justify-between items-center text-xs">
                <span className="text-slate-500">Situations et décomptes émis sur ce chantier</span>
                <button
                  onClick={() => onNavigate('situations')}
                  className="px-4 py-1.5 bg-slate-900 text-white rounded-xl text-xs font-bold hover:bg-slate-800 transition-colors"
                >
                  Ouvrir Situations
                </button>
              </div>

              <div className="space-y-2">
                {chantierSituations.map(sit => (
                  <div key={sit.id} className="p-3 rounded-xl border border-slate-200 flex items-center justify-between">
                    <div>
                      <strong className="text-xs font-bold text-slate-900 block">Situation de Travaux N° {sit.number.toString().padStart(2, '0')}</strong>
                      <span className="text-[11px] text-slate-500">{sit.period}</span>
                    </div>
                    <div className="text-right">
                      <strong className="text-xs font-mono font-bold text-blue-700 block">{formatDA(sit.netToPayTTC)}</strong>
                      <span className="text-[10px] text-emerald-700 font-bold">{sit.status}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Add Worker to Chantier Modal */}
      {isAddingWorker && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2">
                <span className="p-2 rounded-lg bg-blue-100 text-blue-700">
                  <Plus className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Ajouter un Ouvrier sur ce Chantier</h3>
                  <p className="text-[11px] text-slate-500">Affectation à : {selectedChantier.name}</p>
                </div>
              </div>
              <button 
                type="button" 
                onClick={() => setIsAddingWorker(false)}
                className="p-1 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddWorkerSubmit} className="p-5 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Nom & Prénom *</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Rachid Belhadj"
                    value={newWorkerName}
                    onChange={(e) => setNewWorkerName(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Corps d'État (Métier) *</label>
                  <select
                    value={newWorkerTrade}
                    onChange={(e) => setNewWorkerTrade(e.target.value as WorkerTrade)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    {TRADES.map(trade => (
                      <option key={trade} value={trade}>{trade}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Statut *</label>
                  <select
                    value={newWorkerStatus}
                    onChange={(e) => setNewWorkerStatus(e.target.value as Worker['status'])}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    <option value="Actif">Actif</option>
                    <option value="Congé">Congé</option>
                    <option value="Arrêt intempérie">Arrêt intempérie</option>
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Tarif Journalier Brut (DA) *</label>
                  <input
                    type="number"
                    min="500"
                    step="100"
                    required
                    value={newWorkerRate}
                    onChange={(e) => setNewWorkerRate(parseFloat(e.target.value) || 0)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono font-bold focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Jours travaillés (Ce mois)</label>
                  <input
                    type="number"
                    min="0"
                    max="31"
                    value={newWorkerDays}
                    onChange={(e) => setNewWorkerDays(parseInt(e.target.value) || 0)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">N° CNAS</label>
                  <input
                    type="text"
                    placeholder="ex: 198822001234"
                    value={newWorkerCnas}
                    onChange={(e) => setNewWorkerCnas(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div className="sm:col-span-2">
                  <label className="font-bold text-slate-700 block mb-1">Téléphone</label>
                  <input
                    type="text"
                    placeholder="ex: 0550 12 34 56"
                    value={newWorkerPhone}
                    onChange={(e) => setNewWorkerPhone(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingWorker(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition-colors shadow-sm"
                >
                  Affecter au Chantier
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Worker Modal */}
      {editingWorker && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2">
                <span className="p-2 rounded-lg bg-amber-100 text-amber-700">
                  <Pencil className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Modifier l'Ouvrier</h3>
                  <p className="text-[11px] text-slate-500">Mise à jour des coordonnées et pointage</p>
                </div>
              </div>
              <button 
                type="button" 
                onClick={() => setEditingWorker(null)}
                className="p-1 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleEditWorkerSubmit} className="p-5 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Nom & Prénom *</label>
                  <input
                    type="text"
                    required
                    value={editingWorker.fullName}
                    onChange={(e) => setEditingWorker({ ...editingWorker, fullName: e.target.value })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Corps d'État (Métier) *</label>
                  <select
                    value={editingWorker.trade}
                    onChange={(e) => setEditingWorker({ ...editingWorker, trade: e.target.value as WorkerTrade })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    {TRADES.map(trade => (
                      <option key={trade} value={trade}>{trade}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Statut *</label>
                  <select
                    value={editingWorker.status}
                    onChange={(e) => setEditingWorker({ ...editingWorker, status: e.target.value as Worker['status'] })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    <option value="Actif">Actif</option>
                    <option value="Congé">Congé</option>
                    <option value="Arrêt intempérie">Arrêt intempérie</option>
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Tarif Journalier Brut (DA) *</label>
                  <input
                    type="number"
                    min="500"
                    step="100"
                    required
                    value={editingWorker.dailyRateDA}
                    onChange={(e) => setEditingWorker({ ...editingWorker, dailyRateDA: parseFloat(e.target.value) || 0 })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono font-bold focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Jours travaillés (Ce mois)</label>
                  <input
                    type="number"
                    min="0"
                    max="31"
                    value={editingWorker.daysWorkedCurrentMonth}
                    onChange={(e) => setEditingWorker({ ...editingWorker, daysWorkedCurrentMonth: parseInt(e.target.value) || 0 })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">N° CNAS</label>
                  <input
                    type="text"
                    value={editingWorker.cnasNumber}
                    onChange={(e) => setEditingWorker({ ...editingWorker, cnasNumber: e.target.value })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div className="sm:col-span-2">
                  <label className="font-bold text-slate-700 block mb-1">Téléphone</label>
                  <input
                    type="text"
                    value={editingWorker.phone}
                    onChange={(e) => setEditingWorker({ ...editingWorker, phone: e.target.value })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setEditingWorker(null)}
                  className="px-4 py-2 border border-slate-200 text-slate-700 rounded-xl text-xs font-semibold hover:bg-slate-50 transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition-colors shadow-sm"
                >
                  Sauvegarder
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
