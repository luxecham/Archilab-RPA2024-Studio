import React, { useState } from 'react';
import { 
  Printer, 
  FileText, 
  Receipt, 
  Scale, 
  Building2, 
  ShieldCheck, 
  ArrowLeft,
  CheckCircle2
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';

export const OfficialDocumentsView: React.FC = () => {
  const { 
    markets, 
    situations, 
    attachements, 
    avenants, 
    articles, 
    selectedMarketId 
  } = useBTP();

  const [documentType, setDocumentType] = useState<'attachement' | 'situation' | 'avenant'>('situation');

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const marketSituations = situations.filter(s => s.marketId === currentMarket?.id);
  const marketAttachements = attachements.filter(a => a.marketId === currentMarket?.id);
  const marketAvenants = avenants.filter(a => a.marketId === currentMarket?.id);
  const marketArticles = articles.filter(a => a.marketId === currentMarket?.id);

  // Focus on Attachement N° 2 and Situation N° 2 from video
  const activeAttachement = marketAttachements.find(a => a.number === 2) || marketAttachements[0];
  const activeSituation = marketSituations.find(s => s.number === 2) || marketSituations[marketSituations.length - 1] || marketSituations[0];
  const activeAvenant = marketAvenants[0];

  const currentWilaya = ALGERIAN_WILAYAS.find(w => w.code === currentMarket?.wilaya);

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6">
      {/* Top Header & The 2 Official Buttons Specified in 06:48 -> 07:28 */}
      <div className="print:hidden bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-blue-500/10 text-blue-600">
              <Printer className="w-4 h-4" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Documents Officiels BTP Algérie (Anti-Rejet Administratif)
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Édition des États & Décomptes Réglementaires
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Format A4 certifié conforme au Code des Marchés Publics Algérien avec les 3 cadres de signature
          </p>
        </div>

        {/* The 2 Official Print Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => {
              setDocumentType('attachement');
              setTimeout(handlePrint, 100);
            }}
            className="px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-black flex items-center gap-1.5 shadow-md shadow-blue-600/20 transition-all cursor-pointer"
          >
            <FileText className="w-4 h-4" />
            <span>Document 1 : Imprimer la Feuille d'Attachement N° 2</span>
          </button>

          <button
            onClick={() => {
              setDocumentType('situation');
              setTimeout(handlePrint, 100);
            }}
            className="px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-black flex items-center gap-1.5 shadow-md shadow-slate-900/20 transition-all cursor-pointer"
          >
            <Receipt className="w-4 h-4" />
            <span>Document 2 : Imprimer la Situation de Travaux N° 2</span>
          </button>
        </div>
      </div>

      {/* Mode Selector Tabs (Preview) */}
      <div className="print:hidden flex items-center gap-2 overflow-x-auto pb-2">
        <button
          onClick={() => setDocumentType('situation')}
          className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
            documentType === 'situation' ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 border border-slate-200'
          }`}
        >
          <Receipt className="w-4 h-4" />
          <span>Aperçu : Situation de Travaux N° 0{activeSituation?.number || 2}</span>
        </button>

        <button
          onClick={() => setDocumentType('attachement')}
          className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
            documentType === 'attachement' ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 border border-slate-200'
          }`}
        >
          <FileText className="w-4 h-4" />
          <span>Aperçu : Feuille d'Attachement N° 0{activeAttachement?.number || 2}</span>
        </button>

        <button
          onClick={() => setDocumentType('avenant')}
          className={`px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-2 transition-all ${
            documentType === 'avenant' ? 'bg-slate-900 text-white' : 'bg-white text-slate-700 border border-slate-200'
          }`}
        >
          <Scale className="w-4 h-4" />
          <span>Aperçu : Tableau Récapitulatif d'Avenant N° 01</span>
        </button>
      </div>

      {/* DOCUMENT SHEET — FORMAT A4 OFFICIEL */}
      <div className="bg-white p-8 sm:p-12 rounded-2xl border border-slate-200 shadow-lg max-w-5xl mx-auto print:p-0 print:border-none print:shadow-none print:max-w-none text-slate-900 font-sans">
        
        {/* EN-TÊTE RÉGLEMENTAIRE OFFICIEL ALGÉRIE */}
        <div className="text-center space-y-1 pb-4 border-b-2 border-slate-900">
          <p className="font-serif uppercase font-bold text-xs tracking-wider text-slate-800">
            الجمهورية الجزائرية الديمقراطية الشعبية
          </p>
          <p className="font-serif uppercase font-black text-xs tracking-wider text-slate-900">
            RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE
          </p>
          <p className="text-[11px] font-semibold text-slate-600">
            Ministère de l'Habitat, de l'Urbanisme et de la Ville • Wilaya de {currentWilaya ? currentWilaya.name : currentMarket.wilaya}
          </p>
        </div>

        {/* CARTOUCHE ADMINISTRATIF & IDENTITÉ DE L'ENTREPRISE */}
        <div className="grid grid-cols-2 gap-4 my-4 p-3.5 rounded-lg border border-slate-400 text-xs bg-slate-50/50 print:bg-white">
          <div className="space-y-1">
            <p><strong>Maître de l'Ouvrage :</strong> {currentMarket.client}</p>
            <p><strong>Maître d'Œuvre (BET) :</strong> {currentMarket.bet}</p>
            <p><strong>Entreprise de Réalisation :</strong> {currentMarket.enterpriseName}</p>
            <p className="text-[10px] text-slate-500 font-mono">
              RC : 22/00-1458921 • NIF : 000522019485123 • NIS : 0005221400234
            </p>
          </div>
          <div className="space-y-1 text-right">
            <p><strong>Marché N° :</strong> <span className="font-mono font-black">{currentMarket.number}</span></p>
            <p><strong>Avenant :</strong> {activeAvenant ? `Avenant N° 0${activeAvenant.number} inclus` : 'Sans avenant'}</p>
            <p><strong>Date ODS :</strong> {currentMarket.odsDate} ({currentMarket.durationMonths} Mois)</p>
            <p className="text-[10px] text-slate-500 font-mono">
              Domiciliation : BNA Agence Sidi Bel Abbès • RIB : 001 00412 0300054892 41
            </p>
          </div>
          <div className="col-span-2 pt-2 border-t border-slate-300 text-center font-black text-xs uppercase text-slate-900">
            {currentMarket.title}
          </div>
        </div>

        {/* ---------------- DOCUMENT 1 : FEUILLE D'ATTACHEMENT N° 2 (VIDÉO 06:50) ---------------- */}
        {documentType === 'attachement' && activeAttachement && (
          <div className="space-y-5">
            <div className="text-center my-3">
              <h2 className="text-sm font-black uppercase tracking-wider border-y-2 border-slate-900 py-1.5 inline-block px-8 bg-slate-100 print:bg-white">
                FEUILLE D'ATTACHEMENT DES TRAVAUX N° 0{activeAttachement.number}
              </h2>
              <p className="text-xs text-slate-600 mt-1">
                Zone / Localisation : <strong>{activeAttachement.zone}</strong> • Période du {activeAttachement.periodStart} au {activeAttachement.periodEnd}
              </p>
            </div>

            <table className="w-full text-left border-collapse border border-slate-400 text-[10px]">
              <thead>
                <tr className="bg-slate-200 border-b border-slate-400 font-black uppercase text-slate-900">
                  <th className="border border-slate-400 p-2 w-16 text-center">N° Art</th>
                  <th className="border border-slate-400 p-2">Désignation des Ouvrages</th>
                  <th className="border border-slate-400 p-2 text-center w-12">U</th>
                  <th className="border border-slate-400 p-2 text-right w-24">Quantité Prise</th>
                  <th className="border border-slate-400 p-2">Détail des Métrés (N × L × l × H) / Observations</th>
                </tr>
              </thead>
              <tbody>
                {activeAttachement.items.map((it, idx) => {
                  const art = marketArticles.find(a => a.id === it.articleId);
                  return (
                    <tr key={idx} className="border-b border-slate-300">
                      <td className="border border-slate-400 p-2 font-mono text-center font-bold text-blue-800">{art?.code}</td>
                      <td className="border border-slate-400 p-2 font-medium">{art?.designation}</td>
                      <td className="border border-slate-400 p-2 text-center font-mono font-bold">{art?.unit}</td>
                      <td className="border border-slate-400 p-2 text-right font-mono font-black text-slate-900">{formatNumberDZ(it.currentQuantity)}</td>
                      <td className="border border-slate-400 p-2 text-[9px] text-slate-700">
                        {it.metreLines && it.metreLines.length > 0 ? (
                          <div className="space-y-0.5 font-mono">
                            {it.metreLines.map((ml, i) => (
                              <div key={i}>
                                • {ml.label} : {ml.count} × {ml.length}m × {ml.width}m × {ml.height}m = {formatNumberDZ(ml.subtotal)} ({ml.observation || ''})
                              </div>
                            ))}
                          </div>
                        ) : (
                          it.observation || 'Métré contradictoirement constaté sur chantier'
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* ---------------- DOCUMENT 2 : SITUATION DE TRAVAUX N° 2 (VIDÉO 07:10) ---------------- */}
        {documentType === 'situation' && activeSituation && (
          <div className="space-y-5">
            <div className="text-center my-3">
              <h2 className="text-sm font-black uppercase tracking-wider border-y-2 border-slate-900 py-1.5 inline-block px-8 bg-slate-100 print:bg-white">
                SITUATION DE TRAVAUX N° 0{activeSituation.number} (DÉCOMPTE PROVISOIRE)
              </h2>
              <p className="text-xs text-slate-600 mt-1">
                Période : {activeSituation.period} • Rattachée à l'Attachement N° 0{activeAttachement?.number || 2}
              </p>
            </div>

            {/* Grand Tableau Réglementaire */}
            <table className="w-full text-left border-collapse border border-slate-400 text-[10px] print:text-[9px]">
              <thead>
                <tr className="bg-slate-200 border-b border-slate-400 font-black uppercase text-slate-900">
                  <th className="border border-slate-400 p-1.5 w-12 text-center">N° Prix</th>
                  <th className="border border-slate-400 p-1.5">Désignation des Travaux</th>
                  <th className="border border-slate-400 p-1 text-center w-10">U</th>
                  <th className="border border-slate-400 p-1.5 text-right">P.U HT</th>
                  <th className="border border-slate-400 p-1.5 text-right">Qté Marché</th>
                  <th className="border border-slate-400 p-1.5 text-right">Qté Préc.</th>
                  <th className="border border-slate-400 p-1.5 text-right bg-slate-100 print:bg-white font-black">Qté Mois</th>
                  <th className="border border-slate-400 p-1.5 text-right">Qté Cumul</th>
                  <th className="border border-slate-400 p-1.5 text-right">Montant Préc.</th>
                  <th className="border border-slate-400 p-1.5 text-right bg-slate-100 print:bg-white font-black">Montant Mois HT</th>
                  <th className="border border-slate-400 p-1.5 text-right font-black">Montant Cumulé HT</th>
                </tr>
              </thead>
              <tbody>
                {activeSituation.items.map((it, idx) => (
                  <tr key={idx} className="border-b border-slate-300">
                    <td className="border border-slate-400 p-1 font-mono text-center font-bold">{it.code}</td>
                    <td className="border border-slate-400 p-1 font-medium">{it.designation}</td>
                    <td className="border border-slate-400 p-1 text-center font-mono">{it.unit}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono">{formatNumberDZ(it.unitPriceHT)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono font-bold">{formatNumberDZ(it.contractQty)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono text-slate-500">{formatNumberDZ(it.prevQty)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono font-black bg-slate-50 print:bg-white">{formatNumberDZ(it.monthQty)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono font-bold">{formatNumberDZ(it.totalQty)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono text-slate-500">{formatDA(it.prevAmountHT)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono font-black bg-slate-50 print:bg-white">{formatDA(it.monthAmountHT)}</td>
                    <td className="border border-slate-400 p-1 text-right font-mono font-bold">{formatDA(it.totalAmountHT)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Récapitulatif Financier Réglementaire */}
            <div className="flex justify-end pt-2">
              <div className="w-84 border border-slate-400 rounded p-3 text-xs space-y-1.5 bg-slate-50 print:bg-white">
                <div className="flex justify-between border-b border-slate-300 pb-1">
                  <span>1. Total Travaux Réalisés Cumulés (HT) :</span>
                  <strong className="font-mono">{formatDA(activeSituation.grossAmountCumulativeHT)}</strong>
                </div>
                <div className="flex justify-between border-b border-slate-300 pb-1">
                  <span>2. Moins : Situations Précédentes (HT) :</span>
                  <strong className="font-mono text-slate-600">-{formatDA(activeSituation.grossAmountPreviousHT)}</strong>
                </div>
                <div className="flex justify-between border-b border-slate-300 pb-1 font-bold">
                  <span>3. Montant Brut de la Situation (HT) :</span>
                  <strong className="font-mono">{formatDA(activeSituation.grossAmountCurrentHT)}</strong>
                </div>
                <div className="flex justify-between border-b border-slate-300 pb-1 text-slate-700">
                  <span>4. Moins : Retenue de Garantie ({currentMarket.retenueGarantieRate}%) :</span>
                  <strong className="font-mono">-{formatDA(activeSituation.retenueGarantieHT)}</strong>
                </div>
                <div className="flex justify-between border-b border-slate-300 pb-1 font-bold">
                  <span>5. Montant Net HT :</span>
                  <strong className="font-mono">{formatDA(activeSituation.netHT)}</strong>
                </div>
                <div className="flex justify-between border-b border-slate-300 pb-1">
                  <span>6. Plus : TVA Algérie ({activeSituation.tvaRate}%) :</span>
                  <strong className="font-mono">+{formatDA(activeSituation.tvaAmount)}</strong>
                </div>
                <div className="flex justify-between pt-1 font-black text-sm border-t-2 border-slate-900">
                  <span>MONTANT NET À PAYER TTC :</span>
                  <strong className="font-mono text-blue-900">{formatDA(activeSituation.netToPayTTC)}</strong>
                </div>
              </div>
            </div>

            {/* Arrêté en toutes lettres */}
            <div className="p-3 border border-slate-400 rounded bg-slate-50 print:bg-white text-xs">
              <p className="font-bold">
                Arrêtée la présente situation N° 0{activeSituation.number} à la somme TTC de :
              </p>
              <p className="italic font-serif text-slate-900 mt-1 uppercase font-bold">
                « {activeSituation.netToPayWordsFR} »
              </p>
            </div>
          </div>
        )}

        {/* ---------------- DOCUMENT 3 : TABLEAU RÉCAPITULATIF D'AVENANT ---------------- */}
        {documentType === 'avenant' && activeAvenant && (
          <div className="space-y-5">
            <div className="text-center my-3">
              <h2 className="text-sm font-black uppercase tracking-wider border-y-2 border-slate-900 py-1.5 inline-block px-8 bg-slate-100 print:bg-white">
                TABLEAU RÉCAPITULATIF D'AVENANT N° 0{activeAvenant.number}
              </h2>
              <p className="text-xs text-slate-600 mt-1">
                Incidence Financière & Approbation Administrative
              </p>
            </div>

            <div className="grid grid-cols-3 gap-3 text-center text-xs border border-slate-400 p-3 rounded bg-slate-50 print:bg-white">
              <div>
                <span className="text-slate-500 block">Montant Initial Marché HT</span>
                <strong className="font-mono">{formatDA(activeAvenant.initialMarketHT)}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Montant Avenant HT (+{activeAvenant.incidencePct.toFixed(2)}%)</span>
                <strong className="font-mono text-blue-800">+{formatDA(activeAvenant.deltaAmountHT)}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Nouveau Montant Final Marché HT</span>
                <strong className="font-mono">{formatDA(activeAvenant.newMarketHT)}</strong>
              </div>
            </div>

            <table className="w-full text-left border-collapse border border-slate-400 text-[10px]">
              <thead>
                <tr className="bg-slate-200 border-b border-slate-400 font-bold uppercase text-slate-800">
                  <th className="border border-slate-400 p-2 w-16 text-center">N° Prix</th>
                  <th className="border border-slate-400 p-2">Désignation</th>
                  <th className="border border-slate-400 p-2 text-center w-12">Unité</th>
                  <th className="border border-slate-400 p-2 text-right">Ancienne Qté</th>
                  <th className="border border-slate-400 p-2 text-right">Nouvelle Qté</th>
                  <th className="border border-slate-400 p-2 text-right">P.U HT</th>
                  <th className="border border-slate-400 p-2 text-right">Incidence HT (DA)</th>
                </tr>
              </thead>
              <tbody>
                {activeAvenant.changes.map((ch, idx) => (
                  <tr key={idx} className="border-b border-slate-300">
                    <td className="border border-slate-400 p-2 font-mono text-center font-bold">{ch.code}</td>
                    <td className="border border-slate-400 p-2 font-medium">{ch.designation}</td>
                    <td className="border border-slate-400 p-2 text-center font-mono">{ch.unit}</td>
                    <td className="border border-slate-400 p-2 text-right font-mono">{formatNumberDZ(ch.oldQty)}</td>
                    <td className="border border-slate-400 p-2 text-right font-mono font-black">{formatNumberDZ(ch.newQty)}</td>
                    <td className="border border-slate-400 p-2 text-right font-mono">{formatDA(ch.unitPriceHT)}</td>
                    <td className="border border-slate-400 p-2 text-right font-mono font-black text-emerald-800">+{formatDA(ch.deltaAmountHT)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* LES 3 CADRES DE SIGNATURE ET CACHET OFFICIELS (OBLIGATION LÉGALE ALGÉRIE) */}
        <div className="grid grid-cols-3 gap-4 pt-8 mt-8 border-t-2 border-slate-900 text-center text-xs">
          {/* Cadre 1: Entreprise */}
          <div className="border border-slate-400 rounded-lg p-3 h-36 flex flex-col justify-between">
            <div>
              <p className="font-bold text-[11px] uppercase tracking-wider text-slate-900">
                1. L'Entreprise de Réalisation
              </p>
              <p className="text-[10px] text-slate-500 italic mt-0.5">
                (Lu et accepté sans réserve)
              </p>
            </div>
            <div className="text-[9px] text-slate-400">
              Signature & Cachet de l'Entreprise
            </div>
          </div>

          {/* Cadre 2: Bureau d'études BET */}
          <div className="border border-slate-400 rounded-lg p-3 h-36 flex flex-col justify-between">
            <div>
              <p className="font-bold text-[11px] uppercase tracking-wider text-slate-900">
                2. Le Bureau d'Études (BET)
              </p>
              <p className="text-[10px] text-slate-500 italic mt-0.5">
                (Vérifié et certifié exact)
              </p>
            </div>
            <div className="text-[9px] text-slate-400">
              Signature & Cachet de l'Ingénieur / Architecte
            </div>
          </div>

          {/* Cadre 3: Maître d'ouvrage / Client */}
          <div className="border border-slate-400 rounded-lg p-3 h-36 flex flex-col justify-between">
            <div>
              <p className="font-bold text-[11px] uppercase tracking-wider text-slate-900">
                3. Le Maître d'Ouvrage
              </p>
              <p className="text-[10px] text-slate-500 italic mt-0.5">
                (Approuvé pour mandatement)
              </p>
            </div>
            <div className="text-[9px] text-slate-400">
              Fait à {currentMarket.commune}, le ....................<br/>
              Signature & Griffe du Responsable
            </div>
          </div>
        </div>

      </div>
    </div>
  );
};
