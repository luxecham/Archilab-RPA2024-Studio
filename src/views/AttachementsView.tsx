import React, { useState } from 'react';
import { 
  FileText, 
  Plus, 
  AlertTriangle, 
  CheckCircle2, 
  Trash2, 
  Calculator, 
  Scale, 
  Receipt, 
  ArrowRight, 
  ShieldAlert,
  Lock,
  Unlock,
  Play,
  RotateCcw,
  Sparkles
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ } from '../utils/dzFormatters';
import { Attachement, AttachementItem, MetreLine, DQEArticle } from '../types/btp';
import { ActiveTab } from '../components/Sidebar';

interface AttachementsViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

export const AttachementsView: React.FC<AttachementsViewProps> = ({ onNavigate }) => {
  const { 
    markets, 
    articles, 
    chantiers, 
    attachements, 
    selectedMarketId, 
    addAttachement, 
    updateAttachement,
    validateAttachement,
    createAvenantFromExceedance,
    generateSituationFromAttachements,
    resetToVideoScenario
  } = useBTP();

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const marketArticles = articles.filter(a => a.marketId === currentMarket?.id);
  const marketAttachements = attachements.filter(a => a.marketId === currentMarket?.id);
  const currentChantier = chantiers.find(c => c.marketId === currentMarket?.id) || chantiers[0];

  // Active attachement : par défaut Attachement N° 2 (l'attachement de la vidéo)
  const [activeAttachementId, setActiveAttachementId] = useState<string>(
    marketAttachements.find(a => a.number === 2)?.id || marketAttachements[0]?.id || ''
  );

  const selectedAttachement = marketAttachements.find(a => a.id === activeAttachementId) || marketAttachements[0];

  // Article selection & Entry
  // Find article 4.5 by default or 4.2
  const defaultArt = marketArticles.find(a => a.code === '4.5') || marketArticles[0];
  const [selectedArticleId, setSelectedArticleId] = useState<string>(defaultArt?.id || '');
  const [entryMode, setEntryMode] = useState<'Direct' | 'MetreDetaille'>('Direct');
  const [directQty, setDirectQty] = useState<number>(622); // Démonstration de la vidéo : 622 ml
  const [observation, setObservation] = useState('Simulation vidéo : pose caniveaux');

  // Metre Lines for Avant-Métré mode (N × L × l × H)
  const [metreLines, setMetreLines] = useState<MetreLine[]>([
    { id: '1', label: 'Caniveaux préfabriqués axe principal', count: 622, length: 1, width: 1, height: 1, factor: 1, subtotal: 622, observation: 'Pose sur lit de béton' }
  ]);

  const activeArticle = marketArticles.find(a => a.id === selectedArticleId);

  // Quantité en cours de calcul
  const calculatedQty = entryMode === 'Direct' 
    ? directQty 
    : metreLines.reduce((acc, line) => acc + line.subtotal, 0);

  // Détection en direct du dépassement
  const prevCumul = activeArticle?.previousExecutedQty || 0;
  const contractQty = activeArticle?.currentContractQuantity || 0;
  const totalProjected = prevCumul + calculatedQty;
  const isCurrentlyExceeding = totalProjected > contractQty;
  const exceedanceQty = Math.max(0, totalProjected - contractQty);
  const exceedanceFinancialImpactHT = exceedanceQty * (activeArticle?.unitPriceHT || 0);
  const avancementPct = contractQty > 0 ? Math.min(100, Math.round((totalProjected / contractQty) * 10000) / 100) : 0;

  // Vérifier si l'attachement actuel a au moins un article avec dépassement non régularisé
  const attachementHasExceedance = selectedAttachement?.items.some(i => i.hasExceedance) || false;

  // Actions d'aide pour tester la vidéo en 1 clic
  const handleTestCas1Article42 = () => {
    const art42 = marketArticles.find(a => a.code === '4.2');
    if (art42) {
      setSelectedArticleId(art42.id);
      setDirectQty(2);
      setObservation('Pose de 2 regards de visite avec tampon fonte (Cas n°1 vidéo)');
    }
  };

  const handleTestCas2Article45 = () => {
    const art45 = marketArticles.find(a => a.code === '4.5');
    if (art45) {
      setSelectedArticleId(art45.id);
      setDirectQty(622);
      setObservation('Test dépassement vidéo : 622 au lieu de 6');
    }
  };

  const handleAddMetreLine = () => {
    const newLine: MetreLine = {
      id: `ml-${Date.now()}`,
      label: `Ligne de métré ${metreLines.length + 1}`,
      count: 1,
      length: 10,
      width: 1,
      height: 1,
      factor: 1,
      subtotal: 10
    };
    setMetreLines([...metreLines, newLine]);
  };

  const handleUpdateMetreLine = (id: string, field: keyof MetreLine, value: any) => {
    setMetreLines(prev =>
      prev.map(line => {
        if (line.id === id) {
          const updated = { ...line, [field]: value };
          if (['count', 'length', 'width', 'height', 'factor'].includes(field)) {
            const count = updated.count || 1;
            const length = updated.length || 1;
            const width = updated.width || 1;
            const height = updated.height || 1;
            const factor = updated.factor || 1;
            updated.subtotal = Math.round(count * length * width * height * factor * 100) / 100;
          }
          return updated;
        }
        return line;
      })
    );
  };

  const handleSaveItemToAttachement = () => {
    if (!selectedAttachement || !activeArticle) return;

    const newItem: AttachementItem = {
      id: `item-${Date.now()}`,
      articleId: activeArticle.id,
      mode: entryMode,
      metreLines: entryMode === 'MetreDetaille' ? metreLines : [],
      currentQuantity: calculatedQty,
      observation: observation || (isCurrentlyExceeding ? 'Dépassement quantitatif constaté sur chantier' : 'Conforme au cahier des charges'),
      hasExceedance: isCurrentlyExceeding,
      exceedanceQty: isCurrentlyExceeding ? exceedanceQty : 0
    };

    const existingIndex = selectedAttachement.items.findIndex(i => i.articleId === activeArticle.id);
    let updatedItems = [...selectedAttachement.items];
    if (existingIndex !== -1) {
      updatedItems[existingIndex] = newItem;
    } else {
      updatedItems.push(newItem);
    }

    updateAttachement({
      ...selectedAttachement,
      items: updatedItems
    });
  };

  return (
    <div className="space-y-6">
      {/* 🎬 BANDEAU DE DÉMONSTRATION VIDÉO DINATECK (01:10 → 07:28) */}
      <div className="p-4 rounded-2xl bg-gradient-to-r from-slate-900 via-blue-950 to-slate-900 text-white shadow-lg border border-blue-500/30 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-amber-500 text-slate-950 font-black shrink-0">
            <Play className="w-5 h-5 fill-current" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-black text-sm tracking-wide text-white uppercase">
                Démo Vidéo Interactive (01:10 → 07:28)
              </span>
              <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-amber-400 text-slate-950">
                Cas R+5
              </span>
            </div>
            <p className="text-xs text-blue-200 mt-0.5">
              Simulez les deux cas exacts de la vidéo : Cas normal (Art. 4.2) et Cas Dépassement 622 ml (Art. 4.5)
            </p>
          </div>
        </div>

        {/* Quick Test Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={handleTestCas1Article42}
            className="px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold transition-all shadow-sm flex items-center gap-1.5"
          >
            <span>🟢 Cas 1 : Article 4.2 (Saisir 2 sur 3)</span>
          </button>

          <button
            onClick={handleTestCas2Article45}
            className="px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold transition-all shadow-sm flex items-center gap-1.5"
          >
            <span>🔴 Cas 2 : Article 4.5 (Saisir 622 sur 6)</span>
          </button>

          <button
            onClick={resetToVideoScenario}
            className="p-2 rounded-xl bg-white/10 hover:bg-white/20 text-white transition-colors"
            title="Réinitialiser au scénario vidéo initial"
          >
            <RotateCcw className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Attachement Tabs / Selector List */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
        {marketAttachements.map(att => {
          const isSelected = att.id === selectedAttachement?.id;
          const hasExceedance = att.items.some(i => i.hasExceedance);

          return (
            <button
              key={att.id}
              onClick={() => setActiveAttachementId(att.id)}
              className={`
                px-4 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2.5 whitespace-nowrap transition-all border shrink-0
                ${isSelected 
                  ? 'bg-slate-900 text-white border-slate-900 shadow-md' 
                  : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
                }
              `}
            >
              <span>Attachement N° {att.number.toString().padStart(2, '0')}</span>
              <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                att.status === 'Validé' 
                  ? 'bg-emerald-500/20 text-emerald-400' 
                  : 'bg-blue-500/20 text-blue-300'
              }`}>
                {att.status}
              </span>
              {hasExceedance && (
                <span className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse" title="Dépassement contractuel actif" />
              )}
            </button>
          );
        })}
      </div>

      {selectedAttachement && (
        <div className="space-y-6">
          {/* Attachement Meta & Validation Toolbar */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <span className="font-black text-slate-900 text-base">
                  Feuille d'Attachement N° {selectedAttachement.number.toString().padStart(2, '0')}
                </span>
                <span className={`text-xs px-2.5 py-0.5 rounded-full font-bold ${
                  selectedAttachement.status === 'Validé'
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-amber-100 text-amber-800'
                }`}>
                  Statut : {selectedAttachement.status}
                </span>
                {attachementHasExceedance && (
                  <span className="text-xs px-2.5 py-0.5 rounded-full font-black bg-rose-600 text-white animate-pulse">
                    ⚠️ Dépassement à Régulariser
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500">
                Zone : <strong className="text-slate-800">{selectedAttachement.zone}</strong> • Établi par <strong className="text-slate-700">{selectedAttachement.createdBy}</strong>
              </p>
            </div>

            {/* Workflow Action Buttons (Validation Block / Unlock) */}
            <div className="flex items-center gap-2">
              {selectedAttachement.status !== 'Validé' ? (
                attachementHasExceedance ? (
                  // BOUTON VERROUILLÉ / BLOQUÉ TANT QU'IL Y A DÉPASSEMENT NON RÉGULARISÉ (VIDÉO 04:05 -> 04:48)
                  <div className="relative group">
                    <button
                      disabled
                      className="px-4 py-2.5 rounded-xl bg-slate-300 text-slate-500 text-xs font-bold flex items-center gap-2 cursor-not-allowed border border-slate-300"
                    >
                      <Lock className="w-4 h-4 text-rose-500" />
                      <span>Validation Bloquée (Dépassement non régularisé)</span>
                    </button>
                    <div className="absolute right-0 top-full mt-2 w-64 p-2 bg-slate-900 text-white text-[11px] rounded-lg shadow-lg hidden group-hover:block z-20">
                      Régularisez ce dépassement dans l'onglet Avenants pour débloquer la validation de cet attachement.
                    </div>
                  </div>
                ) : (
                  // BOUTON DÉBLOQUÉ DÈS QUE L'AVENANT EST VALIDÉ (VIDÉO 05:55)
                  <button
                    onClick={() => validateAttachement(selectedAttachement.id)}
                    className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-extrabold flex items-center gap-2 shadow-lg shadow-emerald-600/30 transition-all"
                  >
                    <Unlock className="w-4 h-4" />
                    <span>Valider l'Attachement N° {selectedAttachement.number}</span>
                  </button>
                )
              ) : (
                // SI VALIDÉ : BOUTON GÉNÉRER LA SITUATION N° 2 (VIDÉO 06:15)
                <button
                  onClick={() => {
                    generateSituationFromAttachements(currentMarket.id, [selectedAttachement.id]);
                    onNavigate('situations');
                  }}
                  className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-extrabold flex items-center gap-2 shadow-lg shadow-blue-600/30 transition-all"
                >
                  <Receipt className="w-4 h-4" />
                  <span>Générer la Situation N° {selectedAttachement.number}</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              )}

              <button
                onClick={() => onNavigate('documents')}
                className="px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold transition-colors"
              >
                Imprimer Attachement
              </button>
            </div>
          </div>

          {/* Saisie & Moteur de Métré (L × l × H / Direct) */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
            <div className="border-b border-slate-100 pb-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <Calculator className="w-4 h-4 text-blue-600" />
                  Saisie d'un Article & Décompte Métré
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Sélectionnez un article du cahier des charges pour constater les quantités d'exécution
                </p>
              </div>

              {/* Mode switch */}
              <div className="flex items-center p-1 bg-slate-100 rounded-xl text-xs font-bold">
                <button
                  onClick={() => setEntryMode('Direct')}
                  className={`px-3 py-1.5 rounded-lg transition-all ${
                    entryMode === 'Direct' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-700'
                  }`}
                >
                  Quantité Directe
                </button>
                <button
                  onClick={() => setEntryMode('MetreDetaille')}
                  className={`px-3 py-1.5 rounded-lg transition-all ${
                    entryMode === 'MetreDetaille' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-700'
                  }`}
                >
                  Métré Détaillé (N × L × l × H)
                </button>
              </div>
            </div>

            {/* Article Picker */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  Article du Cahier des Charges (Bordereau DQE)
                </label>
                <select
                  value={selectedArticleId}
                  onChange={(e) => {
                    setSelectedArticleId(e.target.value);
                    const art = marketArticles.find(a => a.id === e.target.value);
                    if (art?.code === '4.2') setDirectQty(2);
                    if (art?.code === '4.5') setDirectQty(622);
                  }}
                  className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white font-bold text-slate-900 focus:ring-2 focus:ring-blue-500"
                >
                  {marketArticles.map(art => (
                    <option key={art.id} value={art.id}>
                      Article {art.code} — {art.designation} (Prévu : {art.currentContractQuantity} {art.unit} - {formatDA(art.unitPriceHT)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  Observations / Localisation précise sur chantier
                </label>
                <input
                  type="text"
                  placeholder="ex: Bloc A - RDC, Axe 1 à 4"
                  value={observation}
                  onChange={(e) => setObservation(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                />
              </div>
            </div>

            {/* BANDEAU DE CONTRÔLE EN TEMPS RÉEL (LES 4 COMPTEURS CLÉS À 03:20) */}
            {activeArticle && (
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 grid grid-cols-2 sm:grid-cols-4 gap-4 text-center">
                <div className="p-2 rounded-lg bg-white border border-slate-100">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 block">
                    1. Qté Cahier des Charges
                  </span>
                  <p className="text-base font-black text-slate-900 font-mono mt-0.5">
                    {formatNumberDZ(activeArticle.currentContractQuantity)} {activeArticle.unit}
                  </p>
                </div>

                <div className="p-2 rounded-lg bg-white border border-slate-100">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 block">
                    2. Cumul Antérieur
                  </span>
                  <p className="text-base font-black text-slate-700 font-mono mt-0.5">
                    {formatNumberDZ(prevCumul)} {activeArticle.unit}
                  </p>
                </div>

                <div className="p-2 rounded-lg bg-white border border-slate-100">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 block">
                    3. Reste Disponible
                  </span>
                  <p className={`text-base font-black font-mono mt-0.5 ${
                    isCurrentlyExceeding ? 'text-rose-600' : 'text-blue-700'
                  }`}>
                    {formatNumberDZ(Math.max(0, contractQty - totalProjected))} {activeArticle.unit}
                  </p>
                </div>

                <div className="p-2 rounded-lg bg-white border border-slate-100">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 block">
                    4. Avancement en direct
                  </span>
                  <p className={`text-base font-black font-mono mt-0.5 ${
                    isCurrentlyExceeding ? 'text-rose-600' : 'text-emerald-600'
                  }`}>
                    {avancementPct} %
                  </p>
                </div>
              </div>
            )}

            {/* Saisie Mode Direct */}
            {entryMode === 'Direct' && (
              <div className="p-4 rounded-xl bg-slate-50/50 border border-slate-200/50 space-y-3">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="max-w-xs">
                    <label className="text-xs font-bold text-slate-700 block mb-1">
                      Quantité exécutée sur cet attachement ({activeArticle?.unit})
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={directQty}
                      onChange={(e) => setDirectQty(parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 text-lg font-black font-mono rounded-xl border border-slate-200 focus:outline-hidden focus:border-blue-500"
                    />
                  </div>

                  <div className="text-xs text-slate-500">
                    Total cumulé résultant : <strong className="font-mono text-slate-900 font-bold">{formatNumberDZ(totalProjected)} {activeArticle?.unit}</strong>
                  </div>
                </div>
              </div>
            )}

            {/* Saisie Mode Métré Détaillé (N × L × l × H) */}
            {entryMode === 'MetreDetaille' && (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-slate-700">
                    Lignes d'Avant-Métré (Dimensions N × Longueur × Largeur × Hauteur)
                  </span>
                  <button
                    type="button"
                    onClick={handleAddMetreLine}
                    className="px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs font-bold flex items-center gap-1 transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Ajouter une Ligne</span>
                  </button>
                </div>

                <div className="overflow-x-auto rounded-xl border border-slate-200">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                        <th className="py-2.5 px-3">Partie d'Ouvrage</th>
                        <th className="py-2.5 px-2 text-center w-16">Nb (N)</th>
                        <th className="py-2.5 px-2 text-center w-20">Long. (m)</th>
                        <th className="py-2.5 px-2 text-center w-20">Larg. (m)</th>
                        <th className="py-2.5 px-2 text-center w-20">Haut. (m)</th>
                        <th className="py-2.5 px-3 text-right w-24">Sous-Total</th>
                        <th className="py-2.5 px-2 text-center w-10"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {metreLines.map(line => (
                        <tr key={line.id} className="hover:bg-slate-50/50">
                          <td className="p-2">
                            <input
                              type="text"
                              value={line.label}
                              onChange={(e) => handleUpdateMetreLine(line.id, 'label', e.target.value)}
                              className="w-full px-2 py-1 rounded-md border border-slate-200 text-xs"
                            />
                          </td>
                          <td className="p-2 text-center">
                            <input
                              type="number"
                              value={line.count}
                              onChange={(e) => handleUpdateMetreLine(line.id, 'count', parseFloat(e.target.value) || 1)}
                              className="w-14 px-1 py-1 rounded-md border border-slate-200 text-xs text-center font-mono"
                            />
                          </td>
                          <td className="p-2 text-center">
                            <input
                              type="number"
                              step="0.01"
                              value={line.length}
                              onChange={(e) => handleUpdateMetreLine(line.id, 'length', parseFloat(e.target.value) || 1)}
                              className="w-16 px-1 py-1 rounded-md border border-slate-200 text-xs text-center font-mono"
                            />
                          </td>
                          <td className="p-2 text-center">
                            <input
                              type="number"
                              step="0.01"
                              value={line.width}
                              onChange={(e) => handleUpdateMetreLine(line.id, 'width', parseFloat(e.target.value) || 1)}
                              className="w-16 px-1 py-1 rounded-md border border-slate-200 text-xs text-center font-mono"
                            />
                          </td>
                          <td className="p-2 text-center">
                            <input
                              type="number"
                              step="0.01"
                              value={line.height}
                              onChange={(e) => handleUpdateMetreLine(line.id, 'height', parseFloat(e.target.value) || 1)}
                              className="w-16 px-1 py-1 rounded-md border border-slate-200 text-xs text-center font-mono"
                            />
                          </td>
                          <td className="p-2 text-right font-mono font-bold text-slate-800">
                            {formatNumberDZ(line.subtotal)}
                          </td>
                          <td className="p-2 text-center">
                            <button
                              type="button"
                              onClick={() => setMetreLines(metreLines.filter(l => l.id !== line.id))}
                              className="text-slate-400 hover:text-rose-600"
                            >
                              <Trash2 className="w-3.5 h-3.5" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                <div className="flex justify-end text-xs font-mono font-bold text-slate-900 pt-1">
                  Total Métré Calculé : <span className="ml-2 text-blue-700">{formatNumberDZ(calculatedQty)} {activeArticle?.unit}</span>
                </div>
              </div>
            )}

            {/* DÉTECTION CRITIQUE DU DÉPASSEMENT (VIDÉO 04:05 -> 04:48) */}
            {isCurrentlyExceeding && (
              <div className="p-4 rounded-xl bg-rose-50 border-2 border-rose-500 shadow-md space-y-3 animate-fade-in">
                <div className="flex items-start gap-3">
                  <div className="p-2 rounded-xl bg-rose-600 text-white shrink-0 shadow-md">
                    <ShieldAlert className="w-5 h-5 animate-pulse" />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <h4 className="font-black text-sm text-rose-900">
                        ⚠️ DÉPASSEMENT CONTRACTUEL DÉTECTÉ (+{formatNumberDZ(exceedanceQty)} {activeArticle?.unit} HORS MARCHÉ)
                      </h4>
                      <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-rose-600 text-white">
                        Blocage Actif
                      </span>
                    </div>

                    <p className="text-xs text-rose-800 mt-1">
                      Pour l'article <strong>{activeArticle?.code}</strong>, la quantité saisie (<strong>{formatNumberDZ(totalProjected)} {activeArticle?.unit}</strong>) dépasse la quantité contractuelle prévue (<strong>{formatNumberDZ(contractQty)} {activeArticle?.unit}</strong>).
                    </p>

                    <div className="mt-2 text-xs font-mono text-rose-900 font-bold">
                      Impact Financier HT du Dépassement : +{formatDA(exceedanceFinancialImpactHT)}
                    </div>
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-3 border-t border-rose-200">
                  <span className="text-[11px] text-rose-700 font-medium">
                    La validation de l'attachement est verrouillée tant que cet écart n'est pas couvert par un Avenant validé.
                  </span>

                  <button
                    type="button"
                    onClick={() => {
                      if (!activeArticle) return;
                      handleSaveItemToAttachement();
                      createAvenantFromExceedance(currentMarket.id, activeArticle.id, totalProjected);
                      onNavigate('avenants');
                    }}
                    className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-black flex items-center justify-center gap-1.5 shadow-md shadow-rose-600/30 transition-all shrink-0"
                  >
                    <Scale className="w-4 h-4" />
                    <span>Créer / Régulariser via un Avenant</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            )}

            {/* Save Item Button */}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={handleSaveItemToAttachement}
                className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold transition-all shadow-md shadow-blue-600/20"
              >
                Enregistrer la Ligne dans l'Attachement
              </button>
            </div>

            {/* Articles Table in current Attachement */}
            {selectedAttachement.items.length > 0 && (
              <div className="pt-4 border-t border-slate-100 space-y-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                  Articles Comptabilisés dans l'Attachement N° {selectedAttachement.number} ({selectedAttachement.items.length})
                </h3>

                <div className="overflow-x-auto rounded-xl border border-slate-200">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-slate-50 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                        <th className="py-2.5 px-3">N° Art</th>
                        <th className="py-2.5 px-3">Désignation</th>
                        <th className="py-2.5 px-2 text-center">Unité</th>
                        <th className="py-2.5 px-3 text-right">Qté Prise</th>
                        <th className="py-2.5 px-3 text-right">P.U HT</th>
                        <th className="py-2.5 px-3 text-right">Montant HT</th>
                        <th className="py-2.5 px-3 text-center">Statut Marché</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {selectedAttachement.items.map(item => {
                        const art = marketArticles.find(a => a.id === item.articleId);
                        const itemAmountHT = (art?.unitPriceHT || 0) * item.currentQuantity;

                        return (
                          <tr key={item.id} className={item.hasExceedance ? 'bg-rose-50/70 font-bold' : 'hover:bg-slate-50'}>
                            <td className="py-2.5 px-3 font-mono font-bold text-blue-700">{art?.code}</td>
                            <td className="py-2.5 px-3 font-medium text-slate-900">{art?.designation}</td>
                            <td className="py-2.5 px-2 text-center font-mono font-semibold text-slate-600">{art?.unit}</td>
                            <td className="py-2.5 px-3 text-right font-mono font-black text-slate-900">{formatNumberDZ(item.currentQuantity)}</td>
                            <td className="py-2.5 px-3 text-right font-mono text-slate-700">{formatDA(art?.unitPriceHT)}</td>
                            <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">{formatDA(itemAmountHT)}</td>
                            <td className="py-2.5 px-3 text-center">
                              {item.hasExceedance ? (
                                <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-rose-600 text-white animate-pulse">
                                  Dépassement (+{formatNumberDZ(item.exceedanceQty)})
                                </span>
                              ) : (
                                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
                                  Conforme ({art && art.currentContractQuantity > 0 ? Math.round((item.currentQuantity / art.currentContractQuantity) * 100) : 100}%)
                                </span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
