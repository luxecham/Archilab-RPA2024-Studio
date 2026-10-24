import React, { useState } from 'react';
import { 
  Receipt, 
  FileText, 
  CheckCircle2, 
  Printer, 
  Plus, 
  DollarSign, 
  Percent, 
  Building2, 
  ArrowRight,
  ShieldCheck,
  AlertCircle,
  Eye
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, numberToWordsDZ } from '../utils/dzFormatters';
import { Situation, SituationItemLine } from '../types/btp';
import { ActiveTab } from '../components/Sidebar';

interface SituationsViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

export const SituationsView: React.FC<SituationsViewProps> = ({ onNavigate }) => {
  const { 
    markets, 
    situations, 
    attachements, 
    selectedMarketId, 
    generateSituationFromAttachements,
    validateSituation
  } = useBTP();

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const marketSituations = situations.filter(s => s.marketId === currentMarket?.id);
  const validatedAttachements = attachements.filter(
    a => a.marketId === currentMarket?.id && a.status === 'Validé'
  );

  const [selectedSituationId, setSelectedSituationId] = useState<string>(
    marketSituations[marketSituations.length - 1]?.id || marketSituations[0]?.id || ''
  );

  const selectedSituation = marketSituations.find(s => s.id === selectedSituationId) || marketSituations[0];

  // Modal Nouvelle Situation (choix attachement)
  const [isGeneratingModalOpen, setIsGeneratingModalOpen] = useState(false);
  const [selectedAttachementForSit, setSelectedAttachementForSit] = useState<string>(
    validatedAttachements[validatedAttachements.length - 1]?.id || ''
  );

  const handleConfirmGenerate = () => {
    if (!selectedAttachementForSit) {
      alert("Veuillez sélectionner un attachement validé.");
      return;
    }
    const newSitId = generateSituationFromAttachements(currentMarket.id, [selectedAttachementForSit]);
    setSelectedSituationId(newSitId);
    setIsGeneratingModalOpen(false);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-emerald-500/10 text-emerald-600">
              <Receipt className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Décomptes Provisoires & Situations Mensuelles de Travaux
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Situations de Travaux — {currentMarket?.title}
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Calculs financiers réglementaires : TVA {currentMarket?.tvaRate}% (Logement Algérie) • Retenue de Garantie {currentMarket?.retenueGarantieRate}%
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setIsGeneratingModalOpen(true)}
            className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-black flex items-center gap-1.5 transition-colors shadow-sm cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>Nouvelle Situation</span>
          </button>

          <button
            onClick={() => onNavigate('documents')}
            className="px-3.5 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold flex items-center gap-1.5 transition-colors shadow-sm"
          >
            <Printer className="w-4 h-4" />
            <span>Imprimer Format Officiel</span>
          </button>
        </div>
      </div>

      {/* MODAL / DRAWER NOUVELLE SITUATION (05:55 -> 06:48) */}
      {isGeneratingModalOpen && (
        <div className="p-5 rounded-2xl bg-white border-2 border-blue-500 shadow-xl space-y-4 animate-fade-in">
          <div className="flex justify-between items-center border-b border-slate-100 pb-3">
            <div>
              <h3 className="text-sm font-black text-slate-900 flex items-center gap-2">
                <Receipt className="w-4 h-4 text-blue-600" />
                Génération Automatique d'une Situation de Travaux
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                Sélectionnez l'attachement contradictoire validé à compiler (Zéro ressaisie)
              </p>
            </div>
            <button
              onClick={() => setIsGeneratingModalOpen(false)}
              className="text-xs text-slate-400 hover:text-slate-600"
            >
              Fermer
            </button>
          </div>

          <div className="space-y-3">
            <label className="text-xs font-bold text-slate-700 block">
              Attachement Validé à compiler pour cette situation :
            </label>
            {validatedAttachements.length > 0 ? (
              <div className="space-y-2">
                {validatedAttachements.map(att => (
                  <label
                    key={att.id}
                    className={`flex items-center justify-between p-3.5 rounded-xl border cursor-pointer transition-all ${
                      selectedAttachementForSit === att.id
                        ? 'border-blue-600 bg-blue-50/50 ring-2 ring-blue-600/20'
                        : 'border-slate-200 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <input
                        type="radio"
                        name="attSelect"
                        checked={selectedAttachementForSit === att.id}
                        onChange={() => setSelectedAttachementForSit(att.id)}
                        className="accent-blue-600"
                      />
                      <div>
                        <strong className="text-xs font-bold text-slate-900 block">
                          Attachement N° 0{att.number} — {att.zone}
                        </strong>
                        <span className="text-[11px] text-slate-500">
                          Période : {att.periodStart} au {att.periodEnd} • {att.items.length} articles
                        </span>
                      </div>
                    </div>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                      Validé BET & Client
                    </span>
                  </label>
                ))}
              </div>
            ) : (
              <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-800">
                Aucun attachement validé disponible. Veuillez d'abord valider l'Attachement N° 2 dans l'onglet Attachements.
              </div>
            )}
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-slate-100">
            <button
              onClick={() => setIsGeneratingModalOpen(false)}
              className="px-4 py-2 rounded-xl text-xs font-bold text-slate-600 hover:bg-slate-100"
            >
              Annuler
            </button>
            <button
              disabled={validatedAttachements.length === 0}
              onClick={handleConfirmGenerate}
              className="px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:bg-slate-300 text-white text-xs font-black shadow-md shadow-blue-600/20 cursor-pointer"
            >
              Générer la Situation Mensuelle
            </button>
          </div>
        </div>
      )}

      {/* Situations Selector Tabs */}
      {marketSituations.length > 0 ? (
        <div className="space-y-6">
          <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
            {marketSituations.map(sit => {
              const isSelected = sit.id === selectedSituation?.id;
              return (
                <button
                  key={sit.id}
                  onClick={() => setSelectedSituationId(sit.id)}
                  className={`
                    px-4 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2.5 whitespace-nowrap transition-all border shrink-0
                    ${isSelected 
                      ? 'bg-slate-900 text-white border-slate-900 shadow-md' 
                      : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50'
                    }
                  `}
                >
                  <span>Situation N° 0{sit.number}</span>
                  <span className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    sit.status === 'Validée / Payée' 
                      ? 'bg-emerald-500/20 text-emerald-400' 
                      : 'bg-blue-500/20 text-blue-300'
                  }`}>
                    {sit.status}
                  </span>
                  <span className="font-mono text-[11px] text-slate-400">
                    {formatDA(sit.netToPayTTC)}
                  </span>
                </button>
              );
            })}
          </div>

          {selectedSituation && (
            <div className="space-y-6">
              {/* Situation Header Meta & Validation Toolbar */}
              <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <span className="text-base font-black text-slate-900">
                      Décompte Provisoire N° 0{selectedSituation.number}
                    </span>
                    <span className={`text-xs px-2.5 py-0.5 rounded-full font-bold ${
                      selectedSituation.status === 'Validée / Payée'
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-blue-100 text-blue-800'
                    }`}>
                      {selectedSituation.status}
                    </span>
                    <span className="text-xs text-slate-400 font-mono">
                      Date : {selectedSituation.date}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500">
                    Période d'exécution : <strong className="text-slate-800">{selectedSituation.period}</strong> • Attachement rattaché : <strong className="text-blue-700">{selectedSituation.attachedAttachementIds.join(', ')}</strong>
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  {selectedSituation.status !== 'Validée / Payée' && (
                    <button
                      onClick={() => validateSituation(selectedSituation.id)}
                      className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-black flex items-center gap-1.5 transition-colors shadow-sm cursor-pointer"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Valider & Certifier pour Paiement</span>
                    </button>
                  )}
                  <button
                    onClick={() => onNavigate('documents')}
                    className="px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold transition-colors"
                  >
                    Imprimer Situation PDF
                  </button>
                </div>
              </div>

              {/* Main Regulatory Works Breakdown Table */}
              <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
                <div className="p-4 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
                  <h3 className="font-bold text-xs uppercase tracking-wider text-slate-700">
                    Tableau Réglementaire par Section & Article (Décompte Provisoire N° 0{selectedSituation.number})
                  </h3>
                  <span className="text-xs text-slate-500 font-mono">
                    Devise : Dinar Algérien (DA)
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-slate-100 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                        <th className="py-2.5 px-3">N° Prix</th>
                        <th className="py-2.5 px-3">Désignation des Travaux</th>
                        <th className="py-2.5 px-2 text-center">Unité</th>
                        <th className="py-2.5 px-2 text-right">Qté Marché</th>
                        <th className="py-2.5 px-3 text-right">P.U HT (DA)</th>
                        <th className="py-2.5 px-2 text-right">Qté Préc.</th>
                        <th className="py-2.5 px-2 text-right bg-blue-50 font-bold text-blue-900">Qté Mois</th>
                        <th className="py-2.5 px-2 text-right">Qté Cumul</th>
                        <th className="py-2.5 px-3 text-right">Montant Préc.</th>
                        <th className="py-2.5 px-3 text-right bg-blue-50 font-bold text-blue-900">Montant Mois HT</th>
                        <th className="py-2.5 px-3 text-right font-bold text-slate-900">Montant Cumulé HT</th>
                        <th className="py-2.5 px-2 text-center">%</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {selectedSituation.items.map((line, idx) => (
                        <tr key={idx} className="hover:bg-slate-50">
                          <td className="py-2.5 px-3 font-mono font-bold text-blue-700">
                            {line.code}
                          </td>
                          <td className="py-2.5 px-3 font-medium text-slate-900 max-w-xs truncate">
                            {line.designation}
                          </td>
                          <td className="py-2.5 px-2 text-center font-mono font-semibold text-slate-600">
                            {line.unit}
                          </td>
                          <td className="py-2.5 px-2 text-right font-mono text-slate-600">
                            {formatNumberDZ(line.contractQty)}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono text-slate-700">
                            {formatNumberDZ(line.unitPriceHT)}
                          </td>
                          <td className="py-2.5 px-2 text-right font-mono text-slate-500">
                            {formatNumberDZ(line.prevQty)}
                          </td>
                          <td className="py-2.5 px-2 text-right font-mono font-black text-blue-700 bg-blue-50/40">
                            {formatNumberDZ(line.monthQty)}
                          </td>
                          <td className="py-2.5 px-2 text-right font-mono font-bold text-slate-900">
                            {formatNumberDZ(line.totalQty)}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono text-slate-500">
                            {formatDA(line.prevAmountHT)}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono font-black text-blue-700 bg-blue-50/40">
                            {formatDA(line.monthAmountHT)}
                          </td>
                          <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">
                            {formatDA(line.totalAmountHT)}
                          </td>
                          <td className="py-2.5 px-2 text-center font-mono font-bold text-slate-600">
                            {line.progressPct}%
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Totaux du Tableau */}
                <div className="bg-slate-50 p-4 border-t border-slate-200 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs font-mono">
                  <div className="text-slate-500">
                    Articles facturés : <strong className="text-slate-900">{selectedSituation.items.length}</strong>
                  </div>
                  <div className="flex items-center gap-6">
                    <div>
                      <span className="text-slate-500">Total Mois Brut HT : </span>
                      <strong className="text-blue-700 text-sm font-black">{formatDA(selectedSituation.grossAmountCurrentHT)}</strong>
                    </div>
                    <div>
                      <span className="text-slate-500">Total Cumulé Brut HT : </span>
                      <strong className="text-slate-900 text-sm font-black">{formatDA(selectedSituation.grossAmountCumulativeHT)}</strong>
                    </div>
                  </div>
                </div>
              </div>

              {/* PIED DE SITUATION & CALCULS FINANCIERS RÉGLEMENTAIRES DZ (06:48 -> 07:28) */}
              <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
                <div className="border-b border-slate-100 pb-3 flex items-center justify-between">
                  <h3 className="font-bold text-sm uppercase tracking-wider text-slate-900 flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4 text-emerald-600" />
                    Liquidation Financière Réglementaire (Décompte Provisoire N° 0{selectedSituation.number})
                  </h3>
                  <span className="text-xs font-bold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                    Conforme Trésorerie DZ
                  </span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                  {/* Left: Summary lines */}
                  <div className="space-y-2 text-xs">
                    <div className="flex justify-between py-1.5 border-b border-slate-100">
                      <span className="text-slate-600">1. Total Travaux Réalisés Cumulés (HT) :</span>
                      <strong className="font-mono text-slate-900">{formatDA(selectedSituation.grossAmountCumulativeHT)}</strong>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100">
                      <span className="text-slate-600">2. Moins : Montant des Situations Précédentes (HT) :</span>
                      <strong className="font-mono text-slate-500">-{formatDA(selectedSituation.grossAmountPreviousHT)}</strong>
                    </div>
                    <div className="flex justify-between py-2 border-b-2 border-slate-200 bg-slate-50/50 px-2 rounded-lg font-bold">
                      <span className="text-slate-800">= 3. Montant Brut de la Situation N° 0{selectedSituation.number} (HT) :</span>
                      <strong className="font-mono text-blue-700 text-sm">{formatDA(selectedSituation.grossAmountCurrentHT)}</strong>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 text-amber-800">
                      <span>4. Moins : Retenue de Garantie (5%) :</span>
                      <strong className="font-mono">-{formatDA(selectedSituation.retenueGarantieHT)}</strong>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 font-bold">
                      <span className="text-slate-700">= 5. Montant Net HT de la Situation :</span>
                      <strong className="font-mono text-slate-900 font-black">{formatDA(selectedSituation.netHT)}</strong>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 text-slate-700">
                      <span>6. Plus : TVA Algérie (9%) :</span>
                      <strong className="font-mono">+{formatDA(selectedSituation.tvaAmount)}</strong>
                    </div>
                  </div>

                  {/* Right: Net to pay in words & total badge */}
                  <div className="flex flex-col justify-between p-5 rounded-2xl bg-gradient-to-br from-slate-900 to-blue-950 text-white space-y-4 shadow-md">
                    <div>
                      <span className="text-xs uppercase font-bold tracking-wider text-blue-300">
                        MONTANT NET À PAYER (TTC) EN DINARS ALGÉRIENS
                      </span>
                      <div className="text-2xl sm:text-3xl font-black font-mono tracking-tight text-amber-400 mt-2">
                        {formatDA(selectedSituation.netToPayTTC)}
                      </div>
                    </div>

                    <div className="p-3.5 rounded-xl bg-white/10 border border-white/15 backdrop-blur-xs space-y-1">
                      <span className="text-[10px] font-bold uppercase tracking-wider text-slate-300 block">
                        Arrêté de la présente situation en toutes lettres :
                      </span>
                      <p className="text-xs font-serif italic text-white leading-relaxed">
                        « Arrêtée la présente situation N° 0{selectedSituation.number} à la somme TTC de : <strong className="text-amber-300 font-sans not-italic font-black">{selectedSituation.netToPayWordsFR}</strong> »
                      </p>
                    </div>

                    <p className="text-[10px] text-slate-400 text-center">
                      Certifié exact par le BET et soumis au Maître d'Ouvrage pour mandatement
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
};
