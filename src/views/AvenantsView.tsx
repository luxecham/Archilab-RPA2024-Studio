import React, { useState } from 'react';
import { 
  Scale, 
  Plus, 
  CheckCircle2, 
  AlertTriangle, 
  FileText, 
  ArrowRight, 
  ShieldCheck, 
  Printer, 
  Clock,
  Sparkles
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ } from '../utils/dzFormatters';
import { Avenant } from '../types/btp';
import { ActiveTab } from '../components/Sidebar';

interface AvenantsViewProps {
  onNavigate: (tab: ActiveTab) => void;
}

export const AvenantsView: React.FC<AvenantsViewProps> = ({ onNavigate }) => {
  const { 
    markets, 
    avenants, 
    selectedMarketId, 
    validateAvenant 
  } = useBTP();

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const marketAvenants = avenants.filter(av => av.marketId === currentMarket?.id);
  const [selectedAvenantId, setSelectedAvenantId] = useState<string>(
    marketAvenants[0]?.id || ''
  );

  const selectedAvenant = marketAvenants.find(a => a.id === selectedAvenantId) || marketAvenants[0];
  const [showSyncSuccess, setShowSyncSuccess] = useState(false);

  const handleValidate = (avenantId: string) => {
    validateAvenant(avenantId);
    setShowSyncSuccess(true);
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-amber-500/10 text-amber-600">
              <Scale className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Régularisation Contractuelle des Travaux
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Avenants du Marché R+5 ({currentMarket?.number})
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Validation synchronisée : injection des nouvelles quantités et déblocage de l'Attachement
          </p>
        </div>

        <button
          onClick={() => onNavigate('documents')}
          className="px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-bold flex items-center gap-1.5 transition-colors"
        >
          <Printer className="w-4 h-4" />
          <span>Imprimer Tableau Avenant</span>
        </button>
      </div>

      {/* SYNCHRONIZATION SUCCESS BANNER (VIDÉO 05:25 -> 05:55) */}
      {showSyncSuccess && (
        <div className="p-4 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-700 text-white shadow-lg flex flex-col sm:flex-row sm:items-center justify-between gap-4 animate-fade-in">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-white/20 rounded-xl shrink-0">
              <CheckCircle2 className="w-6 h-6" />
            </div>
            <div>
              <strong className="text-sm font-black block">
                Validation Synchronisée Réussie ! (Moment clé de la vidéo)
              </strong>
              <p className="text-xs text-emerald-100 mt-0.5">
                La quantité de l'Article 4.5 est passée à 622 ml dans le DQE. L'alerte rouge de dépassement dans l'Attachement N° 2 a disparu et sa validation est débloquée !
              </p>
            </div>
          </div>

          <button
            onClick={() => onNavigate('attachements')}
            className="px-4 py-2 bg-white text-emerald-900 font-black text-xs rounded-xl shadow-md hover:bg-emerald-50 transition-all flex items-center justify-center gap-1.5 shrink-0"
          >
            <span>Ouvrir l'Attachement N° 2 Débloqué</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Avenants List */}
      {selectedAvenant ? (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-mono font-bold px-2.5 py-0.5 rounded-md bg-amber-100 text-amber-800">
                  AVENANT N° 0{selectedAvenant.number}
                </span>
                <span className={`text-xs font-black px-2.5 py-0.5 rounded-full ${
                  selectedAvenant.status === 'Validé'
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-amber-100 text-amber-800'
                }`}>
                  Statut : {selectedAvenant.status}
                </span>
              </div>
              <h2 className="text-base font-black text-slate-900 mt-1">
                {selectedAvenant.title}
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Motif : <strong className="text-slate-700">{selectedAvenant.motif}</strong>
              </p>
            </div>

            {/* LE BOUTON DÉCLENCHEUR DE LA VALIDATION SYNCHRONISÉE */}
            {selectedAvenant.status !== 'Validé' ? (
              <button
                onClick={() => handleValidate(selectedAvenant.id)}
                className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-black flex items-center gap-2 shadow-lg shadow-emerald-600/30 transition-all shrink-0 cursor-pointer"
              >
                <CheckCircle2 className="w-4 h-4" />
                <span>Valider l'Avenant N° 0{selectedAvenant.number}</span>
              </button>
            ) : (
              <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-50 text-emerald-700 text-xs font-bold border border-emerald-200">
                <ShieldCheck className="w-4 h-4 text-emerald-600" />
                <span>Approuvé & Injecté dans le Marché</span>
              </div>
            )}
          </div>

          {/* Tableau de Bord Financier de l'Avenant (04:48 -> 05:25) */}
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/60">
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                Ancien Montant Marché HT
              </span>
              <p className="text-base font-black text-slate-900 font-mono mt-1">
                {formatDA(selectedAvenant.initialMarketHT)}
              </p>
              <span className="text-[10px] text-slate-400 mt-0.5 block">
                TTC (9%) : {formatDA(selectedAvenant.initialMarketHT * 1.09)}
              </span>
            </div>

            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200">
              <span className="text-[11px] font-bold uppercase tracking-wider text-amber-700">
                Montant de l'Avenant (+ / - HT)
              </span>
              <p className="text-base font-black text-amber-800 font-mono mt-1">
                +{formatDA(selectedAvenant.deltaAmountHT)}
              </p>
              <span className="text-[10px] text-amber-600 mt-0.5 block font-mono">
                +616 ml × 4 000 DA = {formatDA(selectedAvenant.deltaAmountHT)}
              </span>
            </div>

            <div className="p-4 rounded-xl bg-blue-50 border border-blue-200">
              <span className="text-[11px] font-bold uppercase tracking-wider text-blue-700">
                Incidence Financière (%)
              </span>
              <p className="text-base font-black text-blue-800 font-mono mt-1">
                +{selectedAvenant.incidencePct.toFixed(2)} %
              </p>
              <span className="text-[10px] text-blue-600 mt-0.5 block">
                Seuil réglementaire conforme (&lt; 10%)
              </span>
            </div>

            <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200">
              <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-700">
                Nouveau Montant Final HT
              </span>
              <p className="text-base font-black text-emerald-800 font-mono mt-1">
                {formatDA(selectedAvenant.newMarketHT)}
              </p>
              <span className="text-[10px] text-emerald-600 mt-0.5 block">
                TTC (9%) : {formatDA(selectedAvenant.newMarketHT * 1.09)}
              </span>
            </div>
          </div>

          {/* Table of Changes */}
          <div className="space-y-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Articles Modifiés par cet Avenant
            </h3>

            <div className="overflow-x-auto rounded-xl border border-slate-200">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200 text-[10px] font-bold uppercase text-slate-600">
                    <th className="py-2.5 px-3">N° Art</th>
                    <th className="py-2.5 px-3">Désignation</th>
                    <th className="py-2.5 px-2 text-center">Unité</th>
                    <th className="py-2.5 px-3 text-right">Ancienne Qté</th>
                    <th className="py-2.5 px-3 text-right font-bold text-slate-900 bg-emerald-50/50">Nouvelle Qté</th>
                    <th className="py-2.5 px-3 text-right">Écart</th>
                    <th className="py-2.5 px-3 text-right">P.U HT</th>
                    <th className="py-2.5 px-3 text-right">Incidence HT</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {selectedAvenant.changes.map((ch, idx) => (
                    <tr key={idx} className="hover:bg-slate-50">
                      <td className="py-2.5 px-3 font-mono font-bold text-blue-700">{ch.code}</td>
                      <td className="py-2.5 px-3 font-medium text-slate-900">{ch.designation}</td>
                      <td className="py-2.5 px-2 text-center font-mono font-semibold text-slate-600">{ch.unit}</td>
                      <td className="py-2.5 px-3 text-right font-mono text-slate-500">{formatNumberDZ(ch.oldQty)}</td>
                      <td className="py-2.5 px-3 text-right font-mono font-black text-slate-900 bg-emerald-50/40">{formatNumberDZ(ch.newQty)}</td>
                      <td className="py-2.5 px-3 text-right font-mono font-bold text-emerald-600">+{formatNumberDZ(ch.deltaQty)}</td>
                      <td className="py-2.5 px-3 text-right font-mono text-slate-700">{formatDA(ch.unitPriceHT)}</td>
                      <td className="py-2.5 px-3 text-right font-mono font-bold text-slate-900">+{formatDA(ch.deltaAmountHT)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};
