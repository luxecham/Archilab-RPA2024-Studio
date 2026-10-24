import React, { useState } from 'react';
import { 
  Building2, 
  Plus, 
  FolderTree, 
  Layers, 
  FileText, 
  Trash2, 
  Edit3, 
  Search, 
  CheckCircle2, 
  Percent, 
  Scale, 
  Calendar,
  ChevronDown,
  ChevronRight,
  Sparkles
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';
import { Market, DQEArticle, Section, Lot } from '../types/btp';

export const MarketsView: React.FC = () => {
  const { 
    markets, 
    lots, 
    sections, 
    articles, 
    selectedMarketId, 
    setSelectedMarketId,
    addMarket,
    addLot,
    addSection,
    addArticle,
    deleteArticle
  } = useBTP();

  const [activeTab, setActiveTab] = useState<'dqe' | 'overview' | 'new-market'>('dqe');
  const [searchTerm, setSearchTerm] = useState('');

  // Modals / forms state
  const [isAddingLot, setIsAddingLot] = useState(false);
  const [newLotName, setNewLotName] = useState('');
  const [newLotCode, setNewLotCode] = useState('LOT 03');

  const [isAddingSection, setIsAddingSection] = useState(false);
  const [selectedLotForSection, setSelectedLotForSection] = useState('lot-01');
  const [newSectionName, setNewSectionName] = useState('');

  const [isAddingArticle, setIsAddingArticle] = useState(false);
  const [articleSectionId, setArticleSectionId] = useState('sec-02');
  const [articleCode, setArticleCode] = useState('');
  const [articleDesignation, setArticleDesignation] = useState('');
  const [articleUnit, setArticleUnit] = useState<DQEArticle['unit']>('U');
  const [articleQty, setArticleQty] = useState<number>(10);
  const [articlePU, setArticlePU] = useState<number>(4000);

  // New Market Form State
  const [newMarketNumber, setNewMarketNumber] = useState('2026/08/OPGI');
  const [newMarketTitle, setNewMarketTitle] = useState('Projet R+5 — Construction de 30 Logements Promotionnels');
  const [newMarketClient, setNewMarketClient] = useState('OPGI Sidi Bel Abbès');
  const [newMarketBet, setNewMarketBet] = useState('Architecture & Ingénierie DZ');
  const [newMarketWilaya, setNewMarketWilaya] = useState('22');
  const [newMarketTva, setNewMarketTva] = useState<9 | 19>(9);
  const [newMarketRG, setNewMarketRG] = useState<5 | 10>(5);
  const [newMarketDuration, setNewMarketDuration] = useState<number>(18);
  const [newMarketInitialAmount, setNewMarketInitialAmount] = useState<number>(85000000);

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const marketLots = lots.filter(l => l.marketId === currentMarket?.id);
  const marketArticles = articles.filter(a => a.marketId === currentMarket?.id);

  // Financial synthesis
  const initialHT = currentMarket.initialAmountHT;
  const initialTTC = initialHT * (1 + currentMarket.tvaRate / 100);
  const currentHT = currentMarket.currentAmountHT;
  const currentTTC = currentHT * (1 + currentMarket.tvaRate / 100);
  const deltaAvenantsHT = currentHT - initialHT;

  const handleCreateLot = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newLotName.trim()) return;
    const newL: Lot = {
      id: `lot-${Date.now()}`,
      marketId: currentMarket.id,
      code: newLotCode,
      name: newLotName
    };
    addLot(newL);
    setIsAddingLot(false);
    setNewLotName('');
  };

  const handleCreateSection = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSectionName.trim()) return;
    const newS: Section = {
      id: `sec-${Date.now()}`,
      lotId: selectedLotForSection,
      code: `Section ${sections.filter(s => s.lotId === selectedLotForSection).length + 1}`,
      name: newSectionName
    };
    addSection(newS);
    setIsAddingSection(false);
    setNewSectionName('');
  };

  const handleCreateArticle = (e: React.FormEvent) => {
    e.preventDefault();
    if (!articleCode.trim() || !articleDesignation.trim()) return;
    const newArt: DQEArticle = {
      id: `art-${Date.now()}`,
      marketId: currentMarket.id,
      lotId: 'lot-01',
      sectionId: articleSectionId,
      code: articleCode,
      designation: articleDesignation,
      unit: articleUnit,
      initialQuantity: Number(articleQty),
      avenantQuantityDelta: 0,
      currentContractQuantity: Number(articleQty),
      unitPriceHT: Number(articlePU),
      previousExecutedQty: 0,
      totalExecutedQty: 0
    };
    addArticle(newArt);
    setIsAddingArticle(false);
    setArticleCode('');
    setArticleDesignation('');
  };

  return (
    <div className="space-y-6">
      {/* Top Header & Market Synthesis Side-by-Side (01:10 -> 02:00) */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="px-2.5 py-0.5 rounded-md bg-blue-100 text-blue-800 text-xs font-mono font-bold">
              {currentMarket?.number}
            </span>
            <span className="text-xs px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 font-bold">
              TVA {currentMarket?.tvaRate}% (Logement)
            </span>
            <span className="text-xs px-2 py-0.5 rounded-md bg-amber-100 text-amber-800 font-bold">
              Retenue {currentMarket?.retenueGarantieRate}%
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            {currentMarket?.title}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Client : <strong className="text-slate-800">{currentMarket?.client}</strong> • BET : <strong className="text-slate-800">{currentMarket?.bet}</strong> • Wilaya : <strong className="text-slate-800">{currentMarket?.wilaya} - Sidi Bel Abbès</strong>
          </p>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab('dqe')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'dqe' ? 'bg-blue-600 text-white shadow-sm' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Cahier des Charges (DQE)
          </button>
          <button
            onClick={() => setActiveTab('overview')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeTab === 'overview' ? 'bg-blue-600 text-white shadow-sm' : 'bg-slate-100 text-slate-600'
            }`}
          >
            Fiche Administrative
          </button>
          <button
            onClick={() => setActiveTab('new-market')}
            className="px-3.5 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Nouveau Marché</span>
          </button>
        </div>
      </div>

      {/* SYNTHÈSE FINANCIÈRE EN TEMPS RÉEL (CÔTE À CÔTE HT & TTC) */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
            Montant Initial du Marché
          </span>
          <div className="text-lg sm:text-xl font-black text-slate-900 font-mono mt-1">
            {formatDA(initialHT)}
          </div>
          <span className="text-[11px] text-slate-400 font-mono block mt-0.5">
            TTC (TVA {currentMarket.tvaRate}%) : {formatDA(initialTTC)}
          </span>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-amber-200/80 bg-amber-50/20 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-amber-700">
            Montant des Avenants Validés
          </span>
          <div className="text-lg sm:text-xl font-black text-amber-700 font-mono mt-1">
            +{formatDA(deltaAvenantsHT)}
          </div>
          <span className="text-[11px] text-amber-600 font-mono block mt-0.5">
            Incidence financière : +{((deltaAvenantsHT / initialHT) * 100).toFixed(2)} %
          </span>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-blue-200/80 bg-blue-50/20 shadow-xs">
          <span className="text-xs font-bold uppercase tracking-wider text-blue-700">
            Montant Final Actuel du Marché
          </span>
          <div className="text-lg sm:text-xl font-black text-blue-900 font-mono mt-1">
            {formatDA(currentHT)}
          </div>
          <span className="text-[11px] text-blue-700 font-mono block mt-0.5">
            TTC : {formatDA(currentTTC)}
          </span>
        </div>
      </div>

      {/* TAB 1: ARBORESCENCE DQE (LOTS -> SECTIONS -> ARTICLES) */}
      {activeTab === 'dqe' && (
        <div className="space-y-6">
          {/* Action Bar for Adding Lots, Sections, Articles */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="relative flex-1 max-w-sm">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                placeholder="Rechercher par article (ex: 4.2, 4.5, béton...)"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl border border-slate-200 focus:outline-hidden"
              />
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setIsAddingLot(!isAddingLot)}
                className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold flex items-center gap-1 transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>+ Lot</span>
              </button>
              <button
                onClick={() => setIsAddingSection(!isAddingSection)}
                className="px-3 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold flex items-center gap-1 transition-colors"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>+ Section</span>
              </button>
              <button
                onClick={() => setIsAddingArticle(!isAddingArticle)}
                className="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-1 transition-colors shadow-xs"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>+ Article</span>
              </button>
            </div>
          </div>

          {/* Inline Form: Add Lot */}
          {isAddingLot && (
            <form onSubmit={handleCreateLot} className="bg-slate-50 p-4 rounded-2xl border border-slate-200 space-y-3">
              <h3 className="text-xs font-bold text-slate-800">Ajouter un nouveau Lot au Marché</h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <input
                  type="text"
                  required
                  placeholder="Code Lot (ex: LOT 03)"
                  value={newLotCode}
                  onChange={(e) => setNewLotCode(e.target.value)}
                  className="px-3 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                />
                <input
                  type="text"
                  required
                  placeholder="Intitulé du Lot (ex: VRD & Voirie)"
                  value={newLotName}
                  onChange={(e) => setNewLotName(e.target.value)}
                  className="sm:col-span-2 px-3 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                />
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setIsAddingLot(false)} className="text-xs text-slate-400">Annuler</button>
                <button type="submit" className="px-3 py-1 bg-blue-600 text-white text-xs font-bold rounded-lg">Créer le Lot</button>
              </div>
            </form>
          )}

          {/* Inline Form: Add Section */}
          {isAddingSection && (
            <form onSubmit={handleCreateSection} className="bg-slate-50 p-4 rounded-2xl border border-slate-200 space-y-3">
              <h3 className="text-xs font-bold text-slate-800">Ajouter une Section (Chapitre) à un Lot</h3>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <select
                  value={selectedLotForSection}
                  onChange={(e) => setSelectedLotForSection(e.target.value)}
                  className="px-3 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                >
                  {marketLots.map(l => (
                    <option key={l.id} value={l.id}>{l.code} - {l.name}</option>
                  ))}
                </select>
                <input
                  type="text"
                  required
                  placeholder="Nom de la section (ex: Assainissement & Regards)"
                  value={newSectionName}
                  onChange={(e) => setNewSectionName(e.target.value)}
                  className="sm:col-span-2 px-3 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                />
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setIsAddingSection(false)} className="text-xs text-slate-400">Annuler</button>
                <button type="submit" className="px-3 py-1 bg-blue-600 text-white text-xs font-bold rounded-lg">Créer la Section</button>
              </div>
            </form>
          )}

          {/* Inline Form: Add Article */}
          {isAddingArticle && (
            <form onSubmit={handleCreateArticle} className="bg-blue-50/50 p-4 rounded-2xl border border-blue-200 space-y-3">
              <h3 className="text-xs font-bold text-blue-900">Ajouter un Article au Cahier des Charges</h3>
              <div className="grid grid-cols-1 sm:grid-cols-6 gap-3">
                <div>
                  <label className="text-[10px] font-bold text-slate-600 block mb-1">Section</label>
                  <select
                    value={articleSectionId}
                    onChange={(e) => setArticleSectionId(e.target.value)}
                    className="w-full px-2 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                  >
                    {sections.map(s => (
                      <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="text-[10px] font-bold text-slate-600 block mb-1">N° Article</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: 4.6"
                    value={articleCode}
                    onChange={(e) => setArticleCode(e.target.value)}
                    className="w-full px-2 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="text-[10px] font-bold text-slate-600 block mb-1">Désignation</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Bordures de trottoir T2..."
                    value={articleDesignation}
                    onChange={(e) => setArticleDesignation(e.target.value)}
                    className="w-full px-2 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                  />
                </div>
                <div>
                  <label className="text-[10px] font-bold text-slate-600 block mb-1">Unité</label>
                  <select
                    value={articleUnit}
                    onChange={(e) => setArticleUnit(e.target.value as any)}
                    className="w-full px-2 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                  >
                    <option value="m³">m³</option>
                    <option value="m²">m²</option>
                    <option value="ml">ml</option>
                    <option value="U">U</option>
                    <option value="Kg">Kg</option>
                    <option value="T">T</option>
                  </select>
                </div>
                <div>
                  <label className="text-[10px] font-bold text-slate-600 block mb-1">P.U HT (DA)</label>
                  <input
                    type="number"
                    required
                    value={articlePU}
                    onChange={(e) => setArticlePU(parseFloat(e.target.value) || 0)}
                    className="w-full px-2 py-1.5 text-xs bg-white rounded-lg border border-slate-200"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setIsAddingArticle(false)} className="text-xs text-slate-400">Annuler</button>
                <button type="submit" className="px-3 py-1 bg-blue-600 text-white text-xs font-bold rounded-lg">Enregistrer</button>
              </div>
            </form>
          )}

          {/* Hierarchical Tree Render: Lots -> Sections -> Articles */}
          <div className="space-y-4">
            {marketLots.map(lot => {
              const lotSections = sections.filter(s => s.lotId === lot.id);

              return (
                <div key={lot.id} className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
                  {/* Lot Header */}
                  <div className="bg-slate-900 text-white px-5 py-3 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <FolderTree className="w-4 h-4 text-blue-400" />
                      <span className="font-mono font-bold text-xs bg-blue-600 px-2 py-0.5 rounded text-white">
                        {lot.code}
                      </span>
                      <h2 className="text-sm font-bold">{lot.name}</h2>
                    </div>
                    <span className="text-xs text-slate-400">
                      Chantier assigné : {lot.code === 'LOT 01' ? 'Chantier Projet R+5' : 'En préparation'}
                    </span>
                  </div>

                  {/* Sections inside Lot */}
                  <div className="p-4 space-y-4">
                    {lotSections.map(sec => {
                      const secArticles = marketArticles.filter(a => a.sectionId === sec.id && (
                        a.code.toLowerCase().includes(searchTerm.toLowerCase()) ||
                        a.designation.toLowerCase().includes(searchTerm.toLowerCase())
                      ));

                      if (secArticles.length === 0 && searchTerm) return null;

                      return (
                        <div key={sec.id} className="rounded-xl border border-slate-200 overflow-hidden">
                          {/* Section Header */}
                          <div className="bg-slate-50 px-4 py-2 border-b border-slate-200 flex items-center justify-between">
                            <span className="text-xs font-bold text-slate-800 flex items-center gap-2">
                              <Layers className="w-3.5 h-3.5 text-blue-600" />
                              {sec.name}
                            </span>
                            <span className="text-[11px] font-mono font-semibold text-slate-500">
                              {secArticles.length} Article{secArticles.length > 1 ? 's' : ''}
                            </span>
                          </div>

                          {/* Articles Table */}
                          <div className="overflow-x-auto">
                            <table className="w-full text-left border-collapse text-xs">
                              <thead>
                                <tr className="bg-white border-b border-slate-100 text-[10px] font-bold uppercase text-slate-500">
                                  <th className="py-2.5 px-3 w-16">N° Art</th>
                                  <th className="py-2.5 px-3">Désignation des Travaux</th>
                                  <th className="py-2.5 px-2 text-center w-12">U</th>
                                  <th className="py-2.5 px-3 text-right">Qté Initiale</th>
                                  <th className="py-2.5 px-3 text-right">Avenant</th>
                                  <th className="py-2.5 px-3 text-right font-bold text-slate-900 bg-blue-50/30">Qté Contractuelle</th>
                                  <th className="py-2.5 px-3 text-right">P.U HT (DA)</th>
                                  <th className="py-2.5 px-3 text-right">Montant HT</th>
                                  <th className="py-2.5 px-2 text-center w-10"></th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-slate-100">
                                {secArticles.map(art => {
                                  const totalAmount = art.currentContractQuantity * art.unitPriceHT;
                                  const isKeyVideoArticle = art.code === '4.2' || art.code === '4.5';

                                  return (
                                    <tr 
                                      key={art.id} 
                                      className={`hover:bg-slate-50 transition-colors ${
                                        isKeyVideoArticle ? 'bg-amber-50/20' : ''
                                      }`}
                                    >
                                      <td className="py-2.5 px-3 font-mono font-bold text-blue-700">
                                        {art.code}
                                        {isKeyVideoArticle && (
                                          <span className="ml-1 text-[9px] px-1 py-0.2 rounded bg-amber-400 text-slate-900 font-sans font-bold">
                                            Vidéo
                                          </span>
                                        )}
                                      </td>
                                      <td className="py-2.5 px-3 font-medium text-slate-900">
                                        {art.designation}
                                      </td>
                                      <td className="py-2.5 px-2 text-center font-mono font-semibold text-slate-600">
                                        {art.unit}
                                      </td>
                                      <td className="py-2.5 px-3 text-right font-mono text-slate-600">
                                        {formatNumberDZ(art.initialQuantity)}
                                      </td>
                                      <td className="py-2.5 px-3 text-right font-mono font-bold text-emerald-600">
                                        {art.avenantQuantityDelta > 0 ? `+${formatNumberDZ(art.avenantQuantityDelta)}` : '-'}
                                      </td>
                                      <td className="py-2.5 px-3 text-right font-mono font-black text-slate-900 bg-blue-50/30">
                                        {formatNumberDZ(art.currentContractQuantity)}
                                      </td>
                                      <td className="py-2.5 px-3 text-right font-mono text-slate-700">
                                        {formatDA(art.unitPriceHT)}
                                      </td>
                                      <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">
                                        {formatDA(totalAmount)}
                                      </td>
                                      <td className="py-2.5 px-2 text-center">
                                        <button
                                          onClick={() => deleteArticle(art.id)}
                                          className="text-slate-300 hover:text-rose-600 transition-colors"
                                          title="Supprimer l'article"
                                        >
                                          <Trash2 className="w-3.5 h-3.5" />
                                        </button>
                                      </td>
                                    </tr>
                                  );
                                })}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 2: OVERVIEW */}
      {activeTab === 'overview' && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 text-xs">
          <h2 className="text-sm font-bold text-slate-900">Fiche Administrative du Marché R+5</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-slate-500">Nature du Contrat</p>
              <strong className="text-slate-900">{currentMarket.type}</strong>
            </div>
            <div>
              <p className="text-slate-500">Numéro Officiel</p>
              <strong className="font-mono text-slate-900">{currentMarket.number}</strong>
            </div>
            <div>
              <p className="text-slate-500">Maître d'Ouvrage (Client)</p>
              <strong className="text-slate-900">{currentMarket.client}</strong>
            </div>
            <div>
              <p className="text-slate-500">Bureau d'Études (BET)</p>
              <strong className="text-slate-900">{currentMarket.bet}</strong>
            </div>
            <div>
              <p className="text-slate-500">Taux de TVA Réglementaire</p>
              <strong className="text-slate-900">{currentMarket.tvaRate}% (Spécifique Logement Algérie)</strong>
            </div>
            <div>
              <p className="text-slate-500">Retenue de Garantie</p>
              <strong className="text-slate-900">{currentMarket.retenueGarantieRate}% sur les décomptes provisoires</strong>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
