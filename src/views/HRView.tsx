import React, { useState } from 'react';
import { 
  Users, 
  UserCheck, 
  HardHat, 
  Calendar, 
  Plus, 
  ShieldCheck, 
  DollarSign, 
  Phone,
  Umbrella,
  CheckCircle2,
  Pencil,
  Trash2,
  X,
  Search,
  Filter,
  Building2,
  Briefcase
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ } from '../utils/dzFormatters';
import { Worker, WorkerTrade } from '../types/btp';

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

export const HRView: React.FC = () => {
  const { workers, addWorker, updateWorker, deleteWorker, updateWorkerDays, chantiers } = useBTP();

  // Search & filter states
  const [searchTerm, setSearchTerm] = useState('');
  const [filterChantier, setFilterChantier] = useState('all');
  const [filterTrade, setFilterTrade] = useState('all');

  // Add Worker Modal / Drawer State
  const [isAddingWorker, setIsAddingWorker] = useState(false);
  const [newName, setNewName] = useState('');
  const [newTrade, setNewTrade] = useState<WorkerTrade>('Maçon');
  const [newChantierId, setNewChantierId] = useState(chantiers[0]?.id || 'ch-sba-01');
  const [newRate, setNewRate] = useState<number>(2700);
  const [newCnas, setNewCnas] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [newStatus, setNewStatus] = useState<Worker['status']>('Actif');
  const [newDays, setNewDays] = useState<number>(24);

  // Edit Worker Modal State
  const [editingWorker, setEditingWorker] = useState<Worker | null>(null);

  // Taux de cotisations sociales BTP Algérie
  const CNAS_SALARIAL = 0.09; // 9% retenue à la source
  const CNAS_PATRONAL = 0.26; // 26% charges patronales
  const CACOBATPH_RATE = 0.1221; // 12.21% Congés Payés & Chômage-Intempéries BTP

  // Filtrage des ouvriers
  const filteredWorkers = workers.filter(w => {
    const matchesSearch = 
      w.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      w.phone.includes(searchTerm) ||
      w.cnasNumber.includes(searchTerm) ||
      w.trade.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesChantier = filterChantier === 'all' || w.chantierId === filterChantier;
    const matchesTrade = filterTrade === 'all' || w.trade === filterTrade;

    return matchesSearch && matchesChantier && matchesTrade;
  });

  const totalSalairesBruts = workers.reduce((acc, w) => acc + (w.dailyRateDA * w.daysWorkedCurrentMonth), 0);
  const totalCotisationsCNAS = totalSalairesBruts * CNAS_PATRONAL;
  const totalCacobatph = totalSalairesBruts * CACOBATPH_RATE;
  const totalNetAPayer = totalSalairesBruts * (1 - CNAS_SALARIAL);

  const openAddModal = () => {
    setNewName('');
    setNewTrade('Maçon');
    setNewChantierId(chantiers[0]?.id || 'ch-sba-01');
    setNewRate(2700);
    setNewCnas('19882200' + Math.floor(1000 + Math.random() * 9000));
    setNewPhone('0550 ' + Math.floor(10 + Math.random() * 89) + ' ' + Math.floor(10 + Math.random() * 89) + ' ' + Math.floor(10 + Math.random() * 89));
    setNewStatus('Actif');
    setNewDays(24);
    setIsAddingWorker(true);
  };

  const handleAddWorker = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) return;

    const newW: Worker = {
      id: `w-${Date.now()}`,
      chantierId: newChantierId,
      fullName: newName.trim(),
      trade: newTrade,
      dailyRateDA: Number(newRate) || 0,
      cnasNumber: newCnas.trim() || 'Non renseigné',
      phone: newPhone.trim() || 'Non renseigné',
      status: newStatus,
      daysWorkedCurrentMonth: Number(newDays) || 0
    };

    addWorker(newW);
    setIsAddingWorker(false);
  };

  const handleEditSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingWorker || !editingWorker.fullName.trim()) return;

    updateWorker(editingWorker);
    setEditingWorker(null);
  };

  const handleDeleteWorker = (w: Worker) => {
    if (window.confirm(`Confirmez-vous la suppression définitive de l'ouvrier "${w.fullName}" (${w.trade}) ?`)) {
      deleteWorker(w.id);
    }
  };

  const getChantierName = (chantierId: string) => {
    const ch = chantiers.find(c => c.id === chantierId);
    return ch ? `${ch.name} (W.${ch.wilaya})` : chantierId;
  };

  const getStatusBadge = (status: Worker['status']) => {
    switch (status) {
      case 'Actif':
        return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">Actif</span>;
      case 'Congé':
        return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-100 text-blue-800 border border-blue-200">Congé</span>;
      case 'Arrêt intempérie':
        return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">Arrêt Intempérie</span>;
      default:
        return <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-100 text-slate-800">{status}</span>;
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-blue-500/10 text-blue-600">
              <Users className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Ressources Humaines, Pointage & Réglementation Sociale DZ
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-bold text-slate-900 mt-1">
            Équipes BTP & Paie (CNAS / CACOBATPH Algérie)
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Gestion complète des ouvriers, affectation par chantier, salaires journaliers et cotisations légales
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-2 transition-colors shadow-sm shrink-0"
        >
          <Plus className="w-4 h-4" />
          <span>Ajouter un Ouvrier</span>
        </button>
      </div>

      {/* KPI Cards: Social Security and Payroll */}
      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Masse Salariale Brute
          </span>
          <div className="text-xl font-black text-slate-900 font-mono mt-1">
            {formatDA(totalSalairesBruts)}
          </div>
          <p className="text-[11px] text-slate-400 mt-1">Sur la base des pointages du mois</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Cotisation CNAS (26% + 9%)
          </span>
          <div className="text-xl font-black text-blue-700 font-mono mt-1">
            {formatDA(totalCotisationsCNAS + (totalSalairesBruts * CNAS_SALARIAL))}
          </div>
          <p className="text-[11px] text-blue-600 mt-1">Déclaration G50 / CNAS mensuelle</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-amber-700 flex items-center gap-1">
            <Umbrella className="w-3.5 h-3.5" />
            CACOBATPH BTP (12.21%)
          </span>
          <div className="text-xl font-black text-amber-700 font-mono mt-1">
            {formatDA(totalCacobatph)}
          </div>
          <p className="text-[11px] text-amber-600 mt-1">Congés payés & intempéries BTP</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-emerald-700">
            Net Total à Verser
          </span>
          <div className="text-xl font-black text-emerald-700 font-mono mt-1">
            {formatDA(totalNetAPayer)}
          </div>
          <p className="text-[11px] text-emerald-600 mt-1">{workers.length} ouvriers enregistrés</p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row gap-3 items-center justify-between">
        <div className="relative flex-1 w-full">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Rechercher par nom, téléphone, N° CNAS, métier..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2 w-full md:w-auto">
          <div className="flex items-center gap-1 text-xs text-slate-600">
            <Building2 className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={filterChantier}
              onChange={(e) => setFilterChantier(e.target.value)}
              className="py-1.5 px-2.5 text-xs border border-slate-200 rounded-xl bg-slate-50 font-medium focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">Tous les chantiers ({chantiers.length})</option>
              {chantiers.map(c => (
                <option key={c.id} value={c.id}>{c.name} (W.{c.wilaya})</option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-1 text-xs text-slate-600">
            <Briefcase className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={filterTrade}
              onChange={(e) => setFilterTrade(e.target.value)}
              className="py-1.5 px-2.5 text-xs border border-slate-200 rounded-xl bg-slate-50 font-medium focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">Tous les corps d'état</option>
              {TRADES.map(t => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Add Worker Modal */}
      {isAddingWorker && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2">
                <span className="p-2 rounded-lg bg-blue-100 text-blue-700">
                  <Plus className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Ajouter un Nouvel Ouvrier BTP</h3>
                  <p className="text-[11px] text-slate-500">Enregistrement dans le registre du personnel et affiliation CNAS</p>
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

            <form onSubmit={handleAddWorker} className="p-5 space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                <div>
                  <label className="font-bold text-slate-700 block mb-1">Nom & Prénom *</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Rachid Belhadj"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Corps d'État (Métier) *</label>
                  <select
                    value={newTrade}
                    onChange={(e) => setNewTrade(e.target.value as WorkerTrade)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    {TRADES.map(trade => (
                      <option key={trade} value={trade}>{trade}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Chantier d'affectation *</label>
                  <select
                    value={newChantierId}
                    onChange={(e) => setNewChantierId(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    {chantiers.map(ch => (
                      <option key={ch.id} value={ch.id}>{ch.name} (W.{ch.wilaya})</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Statut *</label>
                  <select
                    value={newStatus}
                    onChange={(e) => setNewStatus(e.target.value as Worker['status'])}
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
                    value={newRate}
                    onChange={(e) => setNewRate(parseFloat(e.target.value) || 0)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono font-bold focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                  <span className="text-[10px] text-slate-400 mt-0.5 block">Ex: 2700 DA/jour pour ouvrier qualifié</span>
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Jours travaillés (Mois en cours)</label>
                  <input
                    type="number"
                    min="0"
                    max="31"
                    value={newDays}
                    onChange={(e) => setNewDays(parseInt(e.target.value) || 0)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Numéro d'immatriculation CNAS</label>
                  <input
                    type="text"
                    placeholder="12 chiffres (ex: 198822001234)"
                    value={newCnas}
                    onChange={(e) => setNewCnas(e.target.value)}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Numéro de Téléphone</label>
                  <input
                    type="text"
                    placeholder="ex: 0550 12 34 56"
                    value={newPhone}
                    onChange={(e) => setNewPhone(e.target.value)}
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
                  Enregistrer l'Ouvrier
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Worker Modal */}
      {editingWorker && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-2xl overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2">
                <span className="p-2 rounded-lg bg-amber-100 text-amber-700">
                  <Pencil className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">Modifier l'Ouvrier</h3>
                  <p className="text-[11px] text-slate-500">Mise à jour des informations personnelles, chantier et salaire</p>
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

            <form onSubmit={handleEditSubmit} className="p-5 space-y-4">
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
                  <label className="font-bold text-slate-700 block mb-1">Chantier d'affectation *</label>
                  <select
                    value={editingWorker.chantierId}
                    onChange={(e) => setEditingWorker({ ...editingWorker, chantierId: e.target.value })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    {chantiers.map(ch => (
                      <option key={ch.id} value={ch.id}>{ch.name} (W.{ch.wilaya})</option>
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
                  <label className="font-bold text-slate-700 block mb-1">Jours travaillés (Mois en cours)</label>
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
                  <label className="font-bold text-slate-700 block mb-1">Numéro d'immatriculation CNAS</label>
                  <input
                    type="text"
                    value={editingWorker.cnasNumber}
                    onChange={(e) => setEditingWorker({ ...editingWorker, cnasNumber: e.target.value })}
                    className="w-full p-2.5 rounded-xl border border-slate-200 font-mono focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  />
                </div>

                <div>
                  <label className="font-bold text-slate-700 block mb-1">Numéro de Téléphone</label>
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
                  Sauvegarder les Modifications
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Workers & Pointage Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        <div className="p-4 border-b border-slate-100 bg-slate-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div>
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-700">
              Pointage Mensuel & Décompte des Salaires avec Cotisations Algérie
            </h3>
            <p className="text-[11px] text-slate-400 mt-0.5">
              Affichage de {filteredWorkers.length} ouvrier(s) sur {workers.length} au total
            </p>
          </div>
          <span className="text-xs text-blue-700 font-mono font-semibold bg-blue-50 px-2.5 py-1 rounded-lg">
            Régime Spécifique BTP CACOBATPH Actif
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-100/80 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                <th className="p-3">Ouvrier & Métier</th>
                <th className="p-3">Chantier</th>
                <th className="p-3">Statut</th>
                <th className="p-3">N° CNAS</th>
                <th className="p-3 text-right">Tarif / Jour</th>
                <th className="p-3 text-center w-28">Jours Pointés</th>
                <th className="p-3 text-right">Brut Mois</th>
                <th className="p-3 text-right">CNAS 9% (Sal.)</th>
                <th className="p-3 text-right">CACOBATPH (12.21%)</th>
                <th className="p-3 text-right bg-emerald-50/50 font-bold">Net à Payer</th>
                <th className="p-3 text-center">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredWorkers.length === 0 ? (
                <tr>
                  <td colSpan={11} className="p-8 text-center text-slate-400">
                    Aucun ouvrier ne correspond à votre recherche ou filtre.
                  </td>
                </tr>
              ) : (
                filteredWorkers.map(w => {
                  const brut = w.dailyRateDA * w.daysWorkedCurrentMonth;
                  const cnasSal = brut * CNAS_SALARIAL;
                  const cacobatph = brut * CACOBATPH_RATE;
                  const net = brut - cnasSal;

                  return (
                    <tr key={w.id} className="hover:bg-slate-50 transition-colors">
                      <td className="p-3">
                        <div className="font-bold text-slate-900">{w.fullName}</div>
                        <span className="text-[10px] text-slate-500 font-medium">{w.trade} • {w.phone}</span>
                      </td>
                      <td className="p-3">
                        <span className="inline-block px-2 py-0.5 rounded text-[10px] font-medium bg-slate-100 text-slate-700 max-w-[150px] truncate" title={getChantierName(w.chantierId)}>
                          {getChantierName(w.chantierId)}
                        </span>
                      </td>
                      <td className="p-3">
                        {getStatusBadge(w.status)}
                      </td>
                      <td className="p-3 font-mono text-slate-600">{w.cnasNumber}</td>
                      <td className="p-3 text-right font-mono text-slate-700">{formatDA(w.dailyRateDA)}</td>
                      <td className="p-3 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <input
                            type="number"
                            min="0"
                            max="31"
                            value={w.daysWorkedCurrentMonth}
                            onChange={(e) => updateWorkerDays(w.id, parseInt(e.target.value) || 0)}
                            className="w-14 px-1 py-1 rounded border border-slate-200 text-center font-mono font-bold hover:border-blue-400 focus:border-blue-500 focus:outline-none"
                          />
                          <span className="text-[10px] text-slate-400">j</span>
                        </div>
                      </td>
                      <td className="p-3 text-right font-mono font-bold text-slate-900">{formatDA(brut)}</td>
                      <td className="p-3 text-right font-mono text-rose-600">-{formatDA(cnasSal)}</td>
                      <td className="p-3 text-right font-mono text-amber-700">{formatDA(cacobatph)}</td>
                      <td className="p-3 text-right font-mono font-bold text-emerald-700 bg-emerald-50/20">{formatDA(net)}</td>
                      <td className="p-3 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            onClick={() => setEditingWorker(w)}
                            title="Modifier l'ouvrier"
                            className="p-1.5 rounded-lg text-slate-500 hover:text-blue-600 hover:bg-blue-50 transition-colors"
                          >
                            <Pencil className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleDeleteWorker(w)}
                            title="Supprimer l'ouvrier"
                            className="p-1.5 rounded-lg text-slate-500 hover:text-rose-600 hover:bg-rose-50 transition-colors"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};