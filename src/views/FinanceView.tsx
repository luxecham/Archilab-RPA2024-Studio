import React, { useState } from 'react';
import { 
  Landmark, 
  Receipt, 
  Plus, 
  TrendingUp, 
  CreditCard, 
  CheckCircle2, 
  Clock, 
  AlertCircle, 
  DollarSign, 
  Scale, 
  Layers
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, ALGERIAN_BANKS } from '../utils/dzFormatters';
import { ChequePayment, Expense, AlgerianBank } from '../types/btp';

export const FinanceView: React.FC = () => {
  const { 
    cheques, 
    expenses, 
    markets, 
    situations, 
    selectedMarketId, 
    addCheque, 
    updateChequeStatus,
    addExpense 
  } = useBTP();

  const [activeTab, setActiveTab] = useState<'cheques' | 'expenses' | 'profitability'>('cheques');
  const [filterType, setFilterType] = useState<'Tous' | 'Reçu' | 'Émis'>('Tous');

  // New Cheque Form
  const [isAddingCheque, setIsAddingCheque] = useState(false);
  const [chequeType, setChequeType] = useState<'Reçu' | 'Émis'>('Reçu');
  const [chequeNumber, setChequeNumber] = useState('');
  const [chequeBank, setChequeBank] = useState<AlgerianBank>('BNA');
  const [chequeAmount, setChequeAmount] = useState<number>(1000000);
  const [chequeParty, setChequeParty] = useState('');

  // New Expense Form
  const [isAddingExpense, setIsAddingExpense] = useState(false);
  const [expenseDesc, setExpenseDesc] = useState('');
  const [expenseCategory, setExpenseCategory] = useState<Expense['category']>('Matériaux');
  const [expenseAmount, setExpenseAmount] = useState<number>(50000);
  const [expenseMode, setExpenseMode] = useState<Expense['paymentMode']>('Espèces (Caisse)');

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];

  // Cheque calculations
  const totalChequesRecus = cheques.filter(c => c.type === 'Reçu').reduce((acc, c) => acc + c.amountDA, 0);
  const totalChequesEmis = cheques.filter(c => c.type === 'Émis').reduce((acc, c) => acc + c.amountDA, 0);
  const totalEncaisse = cheques.filter(c => c.type === 'Reçu' && c.status === 'Encaissé').reduce((acc, c) => acc + c.amountDA, 0);

  // Profitability per market
  const marketSituations = situations.filter(s => s.marketId === currentMarket?.id && s.status === 'Validée / Payée');
  const marketIncome = marketSituations.reduce((acc, s) => acc + s.netToPayTTC, 0);
  const marketExpenses = expenses.filter(e => e.marketId === currentMarket?.id).reduce((acc, e) => acc + e.amountDA, 0);
  const netMarginDA = marketIncome - marketExpenses;
  const marginPct = marketIncome > 0 ? (netMarginDA / marketIncome) * 100 : 0;

  const filteredCheques = cheques.filter(c => filterType === 'Tous' || c.type === filterType);

  const handleCreateCheque = (e: React.FormEvent) => {
    e.preventDefault();
    if (!chequeNumber.trim() || !chequeParty.trim()) return;

    const newC: ChequePayment = {
      id: `chq-${Date.now()}`,
      marketId: currentMarket.id,
      type: chequeType,
      chequeNumber,
      bank: chequeBank,
      amountDA: Number(chequeAmount),
      issuerOrBeneficiary: chequeParty,
      issueDate: new Date().toISOString().split('T')[0],
      status: 'En portefeuille'
    };

    addCheque(newC);
    setIsAddingCheque(false);
    setChequeNumber('');
    setChequeParty('');
  };

  const handleCreateExpense = (e: React.FormEvent) => {
    e.preventDefault();
    if (!expenseDesc.trim()) return;

    const newExp: Expense = {
      id: `exp-${Date.now()}`,
      marketId: currentMarket.id,
      chantierId: 'ch-sba-01',
      date: new Date().toISOString().split('T')[0],
      category: expenseCategory,
      description: expenseDesc,
      amountDA: Number(expenseAmount),
      paymentMode: expenseMode
    };

    addExpense(newExp);
    setIsAddingExpense(false);
    setExpenseDesc('');
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-600">
              <Landmark className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Trésorerie & Système Bancaire Algérien
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-bold text-slate-900 mt-1">
            Registre des Chèques, Dépenses & Rentabilité Chantier
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Suivi des encaissements Trésor Public / Banques DZ (BNA, BEA, CPA, BADR, CNEP) et dépenses réelles
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab('cheques')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'cheques' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Chèques ({cheques.length})
          </button>
          <button
            onClick={() => setActiveTab('expenses')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'expenses' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Dépenses ({expenses.length})
          </button>
          <button
            onClick={() => setActiveTab('profitability')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'profitability' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Rentabilité Marché
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Chèques Reçus (Clients / Trésor)
          </span>
          <div className="text-xl font-black text-emerald-600 font-mono mt-2">
            {formatDA(totalChequesRecus)}
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Encaissé : <strong className="text-slate-800 font-mono">{formatDA(totalEncaisse)}</strong>
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Chèques Émis (Fournisseurs BTP)
          </span>
          <div className="text-xl font-black text-rose-600 font-mono mt-2">
            {formatDA(totalChequesEmis)}
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Règlements cimenteries, ferraillage & engins
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Solde Net de Trésorerie
          </span>
          <div className="text-xl font-black text-blue-700 font-mono mt-2">
            {formatDA(totalChequesRecus - totalChequesEmis)}
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Position de trésorerie disponible
          </p>
        </div>
      </div>

      {/* TAB 1: REGISTRE DES CHÈQUES ALGÉRIE */}
      {activeTab === 'cheques' && (
        <div className="space-y-4">
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              {(['Tous', 'Reçu', 'Émis'] as const).map(t => (
                <button
                  key={t}
                  onClick={() => setFilterType(t)}
                  className={`px-3 py-1 rounded-lg text-xs font-bold transition-all ${
                    filterType === t ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {t}
                </button>
              ))}
            </div>

            <button
              onClick={() => setIsAddingCheque(!isAddingCheque)}
              className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl flex items-center gap-1.5 transition-colors shrink-0"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Enregistrer un Chèque</span>
            </button>
          </div>

          {/* Add Cheque Form Drawer */}
          {isAddingCheque && (
            <form onSubmit={handleCreateCheque} className="bg-white p-5 rounded-2xl border border-blue-200 shadow-md space-y-4">
              <div className="flex justify-between items-center">
                <h3 className="text-xs font-bold text-slate-900">Enregistrer un mouvement de chèque</h3>
                <button type="button" onClick={() => setIsAddingCheque(false)} className="text-xs text-slate-400">Fermer</button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-5 gap-3">
                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Type</label>
                  <select
                    value={chequeType}
                    onChange={(e) => setChequeType(e.target.value as any)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 bg-white"
                  >
                    <option value="Reçu">Reçu (Encaissement)</option>
                    <option value="Émis">Émis (Fournisseur)</option>
                  </select>
                </div>

                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Banque Algérienne</label>
                  <select
                    value={chequeBank}
                    onChange={(e) => setChequeBank(e.target.value as any)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 bg-white font-bold"
                  >
                    {ALGERIAN_BANKS.map(b => (
                      <option key={b} value={b}>{b}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Numéro du Chèque</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: CHQ-098234"
                    value={chequeNumber}
                    onChange={(e) => setChequeNumber(e.target.value)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 font-mono"
                  />
                </div>

                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Émetteur / Bénéficiaire</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: OPGI / Cimenterie..."
                    value={chequeParty}
                    onChange={(e) => setChequeParty(e.target.value)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200"
                  />
                </div>

                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Montant (DA)</label>
                  <input
                    type="number"
                    step="100"
                    required
                    value={chequeAmount}
                    onChange={(e) => setChequeAmount(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 font-mono font-bold"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2">
                <button type="submit" className="px-4 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-bold">
                  Enregistrer
                </button>
              </div>
            </form>
          )}

          {/* Table of Cheques */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                    <th className="py-3 px-4">Sens</th>
                    <th className="py-3 px-4">N° Chèque</th>
                    <th className="py-3 px-4">Banque DZ</th>
                    <th className="py-3 px-4">Émetteur / Bénéficiaire</th>
                    <th className="py-3 px-4">Date Émission</th>
                    <th className="py-3 px-4 text-right">Montant (DA)</th>
                    <th className="py-3 px-4 text-center">Statut Encaissement</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredCheques.map(c => (
                    <tr key={c.id} className="hover:bg-slate-50">
                      <td className="py-3 px-4">
                        <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                          c.type === 'Reçu' ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'
                        }`}>
                          {c.type}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono font-bold text-slate-900">{c.chequeNumber}</td>
                      <td className="py-3 px-4 font-bold text-blue-700">{c.bank}</td>
                      <td className="py-3 px-4 font-medium text-slate-800">{c.issuerOrBeneficiary}</td>
                      <td className="py-3 px-4 font-mono text-slate-500">{c.issueDate}</td>
                      <td className="py-3 px-4 text-right font-mono font-bold text-slate-900">{formatDA(c.amountDA)}</td>
                      <td className="py-3 px-4 text-center">
                        <select
                          value={c.status}
                          onChange={(e) => updateChequeStatus(c.id, e.target.value as any)}
                          className={`text-[10px] font-bold px-2 py-1 rounded-lg border-none focus:ring-0 cursor-pointer ${
                            c.status === 'Encaissé'
                              ? 'bg-emerald-100 text-emerald-800'
                              : c.status === 'Déposé à l\'encaissement'
                              ? 'bg-blue-100 text-blue-800'
                              : 'bg-amber-100 text-amber-800'
                          }`}
                        >
                          <option value="En portefeuille">En portefeuille</option>
                          <option value="Déposé à l'encaissement">Déposé à l'encaissement</option>
                          <option value="Encaissé">Encaissé</option>
                          <option value="Impayé / Rejeté">Impayé / Rejeté</option>
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: DÉPENSES DE CHANTIER */}
      {activeTab === 'expenses' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center bg-white p-4 rounded-2xl border border-slate-200 shadow-xs">
            <span className="text-xs font-bold text-slate-700">Dépenses réelles enregistrées sur le marché</span>
            <button
              onClick={() => setIsAddingExpense(!isAddingExpense)}
              className="px-3 py-1.5 bg-blue-600 text-white rounded-xl text-xs font-bold flex items-center gap-1.5"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Ajouter une Dépense</span>
            </button>
          </div>

          {isAddingExpense && (
            <form onSubmit={handleCreateExpense} className="bg-white p-4 rounded-2xl border border-blue-200 shadow-md space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-xs">
                <div>
                  <label className="font-bold text-slate-600 block mb-1">Catégorie</label>
                  <select
                    value={expenseCategory}
                    onChange={(e) => setExpenseCategory(e.target.value as any)}
                    className="w-full p-2 rounded-lg border border-slate-200 bg-white"
                  >
                    <option value="Matériaux">Matériaux</option>
                    <option value="Main-d'œuvre">Main-d'œuvre</option>
                    <option value="Carburant & Engins">Carburant & Engins</option>
                    <option value="Frais Généraux">Frais Généraux</option>
                    <option value="Caisse Chantier">Caisse Chantier</option>
                  </select>
                </div>
                <div className="sm:col-span-2">
                  <label className="font-bold text-slate-600 block mb-1">Description</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Achat gasoil pour pelle 200L..."
                    value={expenseDesc}
                    onChange={(e) => setExpenseDesc(e.target.value)}
                    className="w-full p-2 rounded-lg border border-slate-200"
                  />
                </div>
                <div>
                  <label className="font-bold text-slate-600 block mb-1">Montant (DA)</label>
                  <input
                    type="number"
                    required
                    value={expenseAmount}
                    onChange={(e) => setExpenseAmount(parseFloat(e.target.value) || 0)}
                    className="w-full p-2 rounded-lg border border-slate-200 font-mono font-bold"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button type="submit" className="px-4 py-1.5 bg-blue-600 text-white rounded-lg text-xs font-bold">
                  Enregistrer
                </button>
              </div>
            </form>
          )}

          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-xs">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                  <th className="p-3">Date</th>
                  <th className="p-3">Catégorie</th>
                  <th className="p-3">Description</th>
                  <th className="p-3">Mode</th>
                  <th className="p-3 text-right">Montant (DA)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {expenses.map(e => (
                  <tr key={e.id} className="hover:bg-slate-50">
                    <td className="p-3 font-mono text-slate-500">{e.date}</td>
                    <td className="p-3 font-semibold text-slate-800">{e.category}</td>
                    <td className="p-3 text-slate-600">{e.description}</td>
                    <td className="p-3 text-slate-500">{e.paymentMode}</td>
                    <td className="p-3 text-right font-mono font-bold text-rose-700">-{formatDA(e.amountDA)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: RENTABILITÉ NETTE PAR MARCHÉ */}
      {activeTab === 'profitability' && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
          <div className="border-b border-slate-100 pb-3">
            <h3 className="font-bold text-base text-slate-900">
              Analyse de Rentabilité — Marché N° {currentMarket.number}
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Différentiel entre encaissements réels (Situations payées) et coûts réels du chantier
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200">
              <span className="text-xs font-bold text-emerald-800 uppercase block">Situations Encaissées</span>
              <p className="text-xl font-black text-emerald-700 font-mono mt-1">{formatDA(marketIncome)}</p>
            </div>
            <div className="p-4 rounded-xl bg-rose-50 border border-rose-200">
              <span className="text-xs font-bold text-rose-800 uppercase block">Coûts et Dépenses Réels</span>
              <p className="text-xl font-black text-rose-700 font-mono mt-1">{formatDA(marketExpenses)}</p>
            </div>
            <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
              <span className="text-xs font-bold text-blue-800 uppercase block">Marge Brute Réalisée</span>
              <p className="text-xl font-black text-blue-800 font-mono mt-1">
                {formatDA(netMarginDA)} ({marginPct.toFixed(1)}%)
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
