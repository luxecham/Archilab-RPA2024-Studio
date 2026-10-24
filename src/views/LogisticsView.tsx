import React, { useState } from 'react';
import { 
  Truck, 
  Package, 
  ShoppingCart, 
  HardHat, 
  Plus, 
  Search, 
  AlertTriangle, 
  CheckCircle2, 
  ArrowDownRight, 
  ArrowUpRight 
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ } from '../utils/dzFormatters';
import { StockItem, PurchaseOrder, MaterialEquipment } from '../types/btp';

export const LogisticsView: React.FC = () => {
  const { 
    stocks, 
    purchaseOrders, 
    equipment, 
    chantiers, 
    addStockItem, 
    updateStockQuantity 
  } = useBTP();

  const [activeTab, setActiveTab] = useState<'stocks' | 'orders' | 'equipment'>('stocks');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedChantierId, setSelectedChantierId] = useState<string>(chantiers[0]?.id || '');

  // Add stock state
  const [isAddingStock, setIsAddingStock] = useState(false);
  const [newStockName, setNewStockName] = useState('');
  const [newStockCategory, setNewStockCategory] = useState<StockItem['category']>('Ciment');
  const [newStockUnit, setNewStockUnit] = useState('Sac');
  const [newStockQty, setNewStockQty] = useState(100);
  const [newStockThreshold, setNewStockThreshold] = useState(20);
  const [newStockPrice, setNewStockPrice] = useState(550);

  const filteredStocks = stocks.filter(s => {
    const matchesSearch = s.name.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesChantier = selectedChantierId === 'all' || s.chantierId === selectedChantierId;
    return matchesSearch && matchesChantier;
  });

  const handleAddStock = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newStockName.trim()) return;

    const newItem: StockItem = {
      id: `stk-${Date.now()}`,
      chantierId: selectedChantierId === 'all' ? chantiers[0]?.id || 'ch-sba-01' : selectedChantierId,
      name: newStockName,
      category: newStockCategory,
      unit: newStockUnit,
      quantityInStock: Number(newStockQty),
      minThreshold: Number(newStockThreshold),
      unitPriceCost: Number(newStockPrice),
      lastRestockedDate: new Date().toISOString().split('T')[0]
    };

    addStockItem(newItem);
    setIsAddingStock(false);
    setNewStockName('');
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-indigo-500/10 text-indigo-600">
              <Truck className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Logistique, Approvisionnements & Matériel Chantier
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-bold text-slate-900 mt-1">
            Gestion des Stocks de Matériaux & Engins BTP
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Suivi des réceptions chantier (Ciment CPJ, Ronds à béton, Agrégats) et commandes fournisseurs
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab('stocks')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'stocks' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Stocks ({stocks.length})
          </button>
          <button
            onClick={() => setActiveTab('orders')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'orders' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Commandes ({purchaseOrders.length})
          </button>
          <button
            onClick={() => setActiveTab('equipment')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'equipment' ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Engins ({equipment.length})
          </button>
        </div>
      </div>

      {/* TAB 1 : STOCKS DE MATÉRIAUX */}
      {activeTab === 'stocks' && (
        <div className="space-y-4">
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3 flex-1">
              <div className="relative flex-1 max-w-sm">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Rechercher matériau (ciment, fer, sable...)"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl border border-slate-200 focus:outline-hidden"
                />
              </div>

              <select
                value={selectedChantierId}
                onChange={(e) => setSelectedChantierId(e.target.value)}
                className="text-xs py-1.5 px-3 rounded-xl border border-slate-200 bg-white font-medium"
              >
                <option value="all">Tous les chantiers</option>
                {chantiers.map(c => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>

            <button
              onClick={() => setIsAddingStock(!isAddingStock)}
              className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-xl flex items-center gap-1.5 transition-colors shrink-0"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Nouveau Matériau</span>
            </button>
          </div>

          {/* Add Stock Form Drawer */}
          {isAddingStock && (
            <form onSubmit={handleAddStock} className="bg-white p-4 rounded-2xl border border-blue-200 shadow-md space-y-4">
              <div className="flex justify-between items-center">
                <h3 className="text-xs font-bold text-slate-900">Enregistrer une nouvelle référence en stock</h3>
                <button type="button" onClick={() => setIsAddingStock(false)} className="text-xs text-slate-400">Fermer</button>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-6 gap-3">
                <div className="sm:col-span-2">
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Désignation Matériau</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Ciment CPJ 42.5"
                    value={newStockName}
                    onChange={(e) => setNewStockName(e.target.value)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Catégorie</label>
                  <select
                    value={newStockCategory}
                    onChange={(e) => setNewStockCategory(e.target.value as any)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 bg-white"
                  >
                    <option value="Ciment">Ciment</option>
                    <option value="Acier">Acier / Fer</option>
                    <option value="Granulats">Granulats & Sable</option>
                    <option value="Briques & Maçonnerie">Briques & Blocs</option>
                    <option value="Plomberie & Élec">Plomberie & Élec</option>
                    <option value="Bois & Coffrage">Bois & Coffrage</option>
                  </select>
                </div>
                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Unité</label>
                  <input
                    type="text"
                    required
                    value={newStockUnit}
                    onChange={(e) => setNewStockUnit(e.target.value)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Quantité Initiale</label>
                  <input
                    type="number"
                    required
                    value={newStockQty}
                    onChange={(e) => setNewStockQty(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 font-mono"
                  />
                </div>
                <div>
                  <label className="text-[11px] font-bold text-slate-600 block mb-1">Seuil d'Alerte Min</label>
                  <input
                    type="number"
                    required
                    value={newStockThreshold}
                    onChange={(e) => setNewStockThreshold(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 font-mono"
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

          {/* Grid of Stocks */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredStocks.map(stk => {
              const isLow = stk.quantityInStock <= stk.minThreshold;
              return (
                <div 
                  key={stk.id}
                  className={`bg-white p-4 rounded-2xl border transition-all shadow-xs flex flex-col justify-between ${
                    isLow ? 'border-amber-400/80 bg-amber-50/20' : 'border-slate-200/80'
                  }`}
                >
                  <div className="space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700">
                        {stk.category}
                      </span>
                      {isLow && (
                        <span className="text-[10px] font-extrabold px-2 py-0.5 rounded-md bg-amber-100 text-amber-800 flex items-center gap-1">
                          <AlertTriangle className="w-3 h-3" />
                          Stock Bas
                        </span>
                      )}
                    </div>

                    <div>
                      <h4 className="font-bold text-sm text-slate-900">{stk.name}</h4>
                      <p className="text-xs text-slate-400 mt-0.5">Dernier approvisionnement : {stk.lastRestockedDate}</p>
                    </div>

                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between">
                      <div>
                        <span className="text-[10px] text-slate-500 block">En Stock</span>
                        <span className="text-lg font-black text-slate-900 font-mono">
                          {formatNumberDZ(stk.quantityInStock)} {stk.unit}
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="text-[10px] text-slate-500 block">Valeur Unitaire</span>
                        <span className="text-xs font-bold text-slate-700 font-mono">
                          {formatDA(stk.unitPriceCost)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Stock Quick Adjustment */}
                  <div className="pt-3 mt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                    <span className="text-[11px] text-slate-400">Ajustement rapide :</span>
                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => updateStockQuantity(stk.id, -10)}
                        className="w-7 h-7 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold flex items-center justify-center transition-colors"
                        title="Sortie de stock (-10)"
                      >
                        -10
                      </button>
                      <button
                        onClick={() => updateStockQuantity(stk.id, 50)}
                        className="px-2 h-7 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-emerald-700 font-bold flex items-center justify-center transition-colors"
                        title="Réception livraison (+50)"
                      >
                        +50
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 2 : BONS DE COMMANDE */}
      {activeTab === 'orders' && (
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                  <th className="py-3 px-4">N° Commande</th>
                  <th className="py-3 px-4">Fournisseur</th>
                  <th className="py-3 px-4">Désignation</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4 text-right">Montant HT</th>
                  <th className="py-3 px-4 text-center">Statut</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {purchaseOrders.map(po => (
                  <tr key={po.id} className="hover:bg-slate-50">
                    <td className="py-3 px-4 font-mono font-bold text-blue-700">{po.orderNumber}</td>
                    <td className="py-3 px-4 font-semibold text-slate-900">{po.supplierName}</td>
                    <td className="py-3 px-4 text-slate-600">{po.items.map(i => i.description).join(', ')}</td>
                    <td className="py-3 px-4 font-mono text-slate-500">{po.date}</td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-slate-900">{formatDA(po.totalAmountHT)}</td>
                    <td className="py-3 px-4 text-center">
                      <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                        po.status === 'Livré sur chantier'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-blue-100 text-blue-800'
                      }`}>
                        {po.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3 : MATÉRIEL & ENGINS */}
      {activeTab === 'equipment' && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {equipment.map(eq => (
            <div key={eq.id} className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700">
                  {eq.type}
                </span>
                <span className="text-xs font-bold font-mono px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800">
                  {eq.status}
                </span>
              </div>
              <div>
                <h4 className="font-bold text-slate-900">{eq.name}</h4>
                <p className="text-xs text-slate-500 font-mono mt-0.5">Matricule : {eq.matricule}</p>
              </div>
              <div className="p-2.5 rounded-xl bg-slate-50 border border-slate-100 text-xs">
                <span className="text-slate-500 block text-[11px]">Conducteur / Opérateur affecté</span>
                <strong className="text-slate-900">{eq.driverName}</strong>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
