import React, { useState, useRef, useEffect } from 'react';
import { 
  Sparkles, 
  Upload, 
  FileSpreadsheet, 
  Calculator, 
  CheckCircle2, 
  Plus, 
  Layers, 
  ArrowRight,
  TrendingUp,
  Cpu,
  Camera,
  ScanLine,
  FileText,
  Printer,
  Sliders,
  RefreshCw,
  Eye,
  Check,
  AlertCircle
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, numberToWordsDZ } from '../utils/dzFormatters';
import { DQEArticle } from '../types/btp';

interface BPUItem {
  id: string;
  code: string;
  designation: string;
  unit: DQEArticle['unit'];
  quantity: number;
  unitPriceHT: number;
  unitPriceWords: string;
  breakdown: {
    materiaux: number;
    mainOeuvre: number;
    materiel: number;
    fraisGeneraux: number;
    marge: number;
  };
}

// Échantillons de devis vierges scannés prêts pour la démonstration
const SAMPLE_BLANK_QUOTATIONS = {
  opgi: {
    title: 'Cahier des Charges Vierge — OPGI Sidi Bel Abbès (40 LPL)',
    source: 'Appel d\'Offres N° 2026/04/OPGI',
    wilaya: '22 - Sidi Bel Abbès',
    client: 'Office de Promotion et de Gestion Immobilière (OPGI)',
    articles: [
      {
        code: '01.01',
        designation: 'Fouilles en puits et en rigoles dans terrain de toute nature sauf rocher',
        unit: 'm³' as const,
        quantity: 850,
        baseCost: 480
      },
      {
        code: '01.02',
        designation: 'Béton de propreté sous semelles dosé à 150 kg/m³ de ciment CPJ 42.5',
        unit: 'm³' as const,
        quantity: 120,
        baseCost: 6500
      },
      {
        code: '01.03',
        designation: 'Béton armé en fondations (semelles isolées, filantes et longrines) dosé à 350 kg/m³',
        unit: 'm³' as const,
        quantity: 420,
        baseCost: 12800
      },
      {
        code: '02.01',
        designation: 'Béton armé en élévation pour poteaux, voiles et poutres R+5 dosé à 350 kg/m³',
        unit: 'm³' as const,
        quantity: 580,
        baseCost: 17200
      },
      {
        code: '02.02',
        designation: 'Planchers à corps creux 16+4 y compris poutrelles et dalle de compression armée',
        unit: 'm²' as const,
        quantity: 3200,
        baseCost: 2950
      },
      {
        code: '03.01',
        designation: 'Maçonnerie en briques creuses terre cuite en double paroi avec lame d\'air',
        unit: 'm²' as const,
        quantity: 4500,
        baseCost: 1650
      },
      {
        code: '04.02',
        designation: 'Regards de visite en béton armé 80x80 cm sous chaussée avec tampon fonte série lourde',
        unit: 'U' as const,
        quantity: 3,
        baseCost: 21500
      },
      {
        code: '04.05',
        designation: 'Caniveaux préfabriqués d\'évacuation des eaux pluviales avec grille galvanisée',
        unit: 'ml' as const,
        quantity: 6,
        baseCost: 3100
      }
    ]
  },
  dlep: {
    title: 'Bordereau Vierge — DLEP (Groupe Scolaire Type B)',
    source: 'Avis d\'Appel d\'Offres National Restreint N° 08/DLEP/2026',
    wilaya: '16 - Alger',
    client: 'Direction du Logement et des Équipements Publics',
    articles: [
      {
        code: '01.01',
        designation: 'Décapage de la terre végétale sur une épaisseur de 20 cm',
        unit: 'm²' as const,
        quantity: 1800,
        baseCost: 180
      },
      {
        code: '01.02',
        designation: 'Fouilles en pleine masse pour sous-sol et blocs classes',
        unit: 'm³' as const,
        quantity: 1200,
        baseCost: 520
      },
      {
        code: '02.01',
        designation: 'Gros béton de calage et semelles filantes dosé à 250 kg/m³',
        unit: 'm³' as const,
        quantity: 260,
        baseCost: 11400
      },
      {
        code: '03.01',
        designation: 'Enduit au mortier de ciment étanche intérieur et extérieur',
        unit: 'm²' as const,
        quantity: 5600,
        baseCost: 750
      },
      {
        code: '04.01',
        designation: 'Revêtement de sol en carreaux de grès cérame premier choix 40x40',
        unit: 'm²' as const,
        quantity: 1400,
        baseCost: 2800
      }
    ]
  }
};

export const IAMetreurView: React.FC = () => {
  const { addArticle, selectedMarketId, markets } = useBTP();
  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];

  const [activeSubTab, setActiveSubTab] = useState<'scanner' | 'bpu-print' | 'subdetail'>('scanner');

  // Scanner State
  const [selectedSampleKey, setSelectedSampleKey] = useState<'opgi' | 'dlep'>('opgi');
  const [scannedDocumentName, setScannedDocumentName] = useState<string>('Devis_Vierge_OPGI_SBA_2026.pdf');
  const [uploadedImagePreview, setUploadedImagePreview] = useState<string | null>(null);
  const [isScanning, setIsScanning] = useState<boolean>(false);
  const [scanStep, setScanStep] = useState<number>(0);
  const [scanCompleted, setScanCompleted] = useState<boolean>(true); // Pre-completed on start for instant exploration!

  // Global AI Parameters
  const [marginPct, setMarginPct] = useState<number>(15); // 15% bénéfice
  const [fraisGenerauxPct, setFraisGenerauxPct] = useState<number>(12); // 12% FG
  const [wilayaCoeff, setWilayaCoeff] = useState<number>(1.0); // 1.0 Ouest/Centre, 1.05 Hauts plateaux, 1.25 Sud
  const [tvaRate, setTvaRate] = useState<number>(9); // 9% ou 19%

  // Generated BPU Items
  const [bpuItems, setBpuItems] = useState<BPUItem[]>(() => {
    return generateBPUFromSample('opgi', 15, 12, 1.0);
  });

  const [injectSuccess, setInjectSuccess] = useState<boolean>(false);
  const [injectedCount, setInjectedCount] = useState<number>(0);

  const fileInputRef = useRef<HTMLInputElement>(null);

  function generateBPUFromSample(sampleKey: 'opgi' | 'dlep', margin: number, fg: number, coeff: number): BPUItem[] {
    const sample = SAMPLE_BLANK_QUOTATIONS[sampleKey];
    return sample.articles.map((art, idx) => {
      // Déboursé sec calculé selon mercuriale
      const rawCost = art.baseCost * coeff;
      const mat = Math.round(rawCost * 0.55);
      const mo = Math.round(rawCost * 0.30);
      const eq = Math.round(rawCost * 0.15);
      const ds = mat + mo + eq;
      const fraisGen = Math.round(ds * (fg / 100));
      const prixRevient = ds + fraisGen;
      const marge = Math.round(prixRevient * (margin / 100));
      const finalPrice = Math.round((prixRevient + marge) / 10) * 10; // Arrondi commercial dizaine

      return {
        id: `bpu-${idx + 1}`,
        code: art.code,
        designation: art.designation,
        unit: art.unit,
        quantity: art.quantity,
        unitPriceHT: finalPrice,
        unitPriceWords: numberToWordsDZ(finalPrice),
        breakdown: {
          materiaux: mat,
          mainOeuvre: mo,
          materiel: eq,
          fraisGeneraux: fraisGen,
          marge: marge
        }
      };
    });
  }

  // Lancement du scan IA simulé
  const triggerScanProcess = (sampleKey: 'opgi' | 'dlep' = selectedSampleKey) => {
    setIsScanning(true);
    setScanStep(1);
    setScanCompleted(false);

    // Simulation séquentielle des étapes d'analyse OCR & Vision IA
    setTimeout(() => setScanStep(2), 600);
    setTimeout(() => setScanStep(3), 1200);
    setTimeout(() => setScanStep(4), 1800);
    setTimeout(() => {
      setScanStep(5);
      const generated = generateBPUFromSample(sampleKey, marginPct, fraisGenerauxPct, wilayaCoeff);
      setBpuItems(generated);
      setIsScanning(false);
      setScanCompleted(true);
    }, 2400);
  };

  // Mise à jour manuelle d'un prix unitaire (recalcul instantané des lettres)
  const handlePriceChange = (id: string, newPrice: number) => {
    setBpuItems(prev =>
      prev.map(item => {
        if (item.id === id) {
          const validPrice = Math.max(0, newPrice);
          return {
            ...item,
            unitPriceHT: validPrice,
            unitPriceWords: numberToWordsDZ(validPrice)
          };
        }
        return item;
      })
    );
  };

  // Recalcul en masse lors du changement des curseurs
  const handleRecalculateAll = (newMargin: number, newFg: number, newCoeff: number) => {
    setBpuItems(prev =>
      prev.map(item => {
        const ds = item.breakdown.materiaux + item.breakdown.mainOeuvre + item.breakdown.materiel;
        const fraisGen = Math.round(ds * (newFg / 100));
        const prixRevient = ds + fraisGen;
        const marge = Math.round(prixRevient * (newMargin / 100));
        const finalPrice = Math.round(((prixRevient + marge) * (newCoeff / wilayaCoeff)) / 10) * 10;

        return {
          ...item,
          unitPriceHT: finalPrice,
          unitPriceWords: numberToWordsDZ(finalPrice)
        };
      })
    );
  };

  // Traitement d'un upload de fichier utilisateur
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setScannedDocumentName(file.name);
      if (file.type.startsWith('image/')) {
        const reader = new FileReader();
        reader.onload = () => setUploadedImagePreview(reader.result as string);
        reader.readAsDataURL(file);
      }
      triggerScanProcess('opgi');
    }
  };

  // Injection dans le DQE du Marché sélectionné
  const handleInjectIntoCurrentMarket = () => {
    bpuItems.forEach(item => {
      const newArt: DQEArticle = {
        id: `art-ai-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
        marketId: currentMarket.id,
        lotId: 'lot-01',
        sectionId: 'sec-01',
        code: item.code,
        designation: item.designation,
        unit: item.unit,
        initialQuantity: item.quantity,
        avenantQuantityDelta: 0,
        currentContractQuantity: item.quantity,
        unitPriceHT: item.unitPriceHT,
        previousExecutedQty: 0,
        totalExecutedQty: 0
      };
      addArticle(newArt);
    });

    setInjectedCount(bpuItems.length);
    setInjectSuccess(true);
    setTimeout(() => setInjectSuccess(false), 5000);
  };

  // Totaux du BPU
  const totalBpuHT = bpuItems.reduce((acc, it) => acc + (it.quantity * it.unitPriceHT), 0);
  const totalTva = totalBpuHT * (tvaRate / 100);
  const totalBpuTTC = totalBpuHT + totalTva;
  const totalBpuTTCWords = numberToWordsDZ(totalBpuTTC);

  return (
    <div className="space-y-6">
      {/* Top Banner Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-gradient-to-r from-blue-600 via-indigo-600 to-amber-500 text-white shadow-sm">
              <ScanLine className="w-4 h-4" />
            </span>
            <span className="text-xs font-black uppercase tracking-wider text-blue-700">
              Module IA Scanner & Rédaction BPU Algérie
            </span>
            <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-amber-400 text-slate-950">
              Chiffres & Toutes Lettres
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Scanner de Devis Vierge & Rédaction Automatique du BPU
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            L'IA numérise le bordereau vierge du cahier des charges, estime les prix selon la mercuriale algérienne et rédige le BPU en chiffres et en lettres conforme au Code des Marchés Publics
          </p>
        </div>

        {/* Action Switcher */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveSubTab('scanner')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeSubTab === 'scanner' ? 'bg-blue-600 text-white shadow-sm' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            Scanner & BPU Rédigé
          </button>
          <button
            onClick={() => setActiveSubTab('bpu-print')}
            className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
              activeSubTab === 'bpu-print' ? 'bg-blue-600 text-white shadow-sm' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            Aperçu Impression BPU A4
          </button>
        </div>
      </div>

      {/* SUCCESS NOTIFICATION WHEN INJECTED */}
      {injectSuccess && (
        <div className="p-4 rounded-2xl bg-emerald-50 border border-emerald-200 text-emerald-900 text-xs flex items-center justify-between shadow-sm animate-fade-in">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
            <div>
              <strong className="font-black block">Injection Réussie dans le Marché Actif !</strong>
              <span>
                {injectedCount} articles avec leurs prix unitaires en chiffres et en toutes lettres ont été ajoutés au DQE du {currentMarket.title}.
              </span>
            </div>
          </div>
          <span className="text-[11px] font-mono font-bold px-2.5 py-1 bg-emerald-600 text-white rounded-lg">
            DQE Synchronisé
          </span>
        </div>
      )}

      {/* ======================================================== */}
      {/* SUB-TAB 1 : SCANNER IA & BPU GÉNÉRÉ                      */}
      {/* ======================================================== */}
      {activeSubTab === 'scanner' && (
        <div className="space-y-6">
          {/* ZONE 1 : INTERFACE DE NUMÉRISATION DU DEVIS VIERGE */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-sm font-black text-slate-900 flex items-center gap-2">
                  <Camera className="w-4 h-4 text-blue-600" />
                  1. Numérisation / Scan du Devis Vierge (DAO / Cahier des Charges)
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Téléversez une photo/scan de bordereau vierge ou choisissez un modèle officiel d'appel d'offres
                </p>
              </div>

              {/* Sample Pre-loaded Tender Documents */}
              <div className="flex items-center gap-2">
                <span className="text-[11px] font-bold text-slate-400 hidden sm:inline">Exemples prêts :</span>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedSampleKey('opgi');
                    setScannedDocumentName('Devis_Vierge_OPGI_SBA_2026.pdf');
                    setUploadedImagePreview(null);
                    triggerScanProcess('opgi');
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                    selectedSampleKey === 'opgi' ? 'bg-slate-900 text-white shadow-xs' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  Devis Vierge OPGI (R+5)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedSampleKey('dlep');
                    setScannedDocumentName('Bordereau_Vierge_DLEP_Ecole.pdf');
                    setUploadedImagePreview(null);
                    triggerScanProcess('dlep');
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                    selectedSampleKey === 'dlep' ? 'bg-slate-900 text-white shadow-xs' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  Devis Vierge DLEP (École)
                </button>
              </div>
            </div>

            {/* Visual Scan / Upload Container */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-center">
              {/* Left Column: Drag & Drop / File Input */}
              <div 
                onClick={() => fileInputRef.current?.click()}
                className="border-2 border-dashed border-blue-300 hover:border-blue-500 rounded-2xl p-6 text-center cursor-pointer bg-blue-50/20 hover:bg-blue-50/50 transition-all flex flex-col items-center justify-center space-y-3 group"
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/*,.pdf"
                  onChange={handleFileUpload}
                  className="hidden"
                />
                <div className="w-12 h-12 rounded-2xl bg-blue-100 text-blue-600 flex items-center justify-center group-hover:scale-110 transition-transform shadow-sm">
                  <Upload className="w-6 h-6" />
                </div>
                <div>
                  <strong className="text-xs font-black text-slate-900 block">
                    Cliquez ou Glissez le Devis Vierge ici
                  </strong>
                  <span className="text-[11px] text-slate-500">
                    Accepte Scan JPG, PNG, PDF ou Photo Mobile
                  </span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-blue-100 text-blue-700">
                  Document actuel : {scannedDocumentName}
                </span>
              </div>

              {/* Middle Column: Visual Representation of Blank Document with Laser Scanning Beam */}
              <div className="lg:col-span-2 relative bg-slate-900 rounded-2xl p-4 overflow-hidden border border-slate-800 shadow-md">
                {/* Laser scan line animation */}
                {isScanning && (
                  <div className="absolute inset-x-0 h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_15px_#22d3ee] animate-pulse z-20 top-0 transition-all duration-1000"
                    style={{ animation: 'bounce 2s infinite' }}
                  />
                )}

                {/* Simulated Blank Tender Document */}
                <div className="bg-white rounded-xl p-4 text-[10px] font-mono text-slate-800 shadow-inner select-none relative opacity-95">
                  <div className="border-b border-slate-300 pb-2 mb-2 flex justify-between items-center text-[9px] text-slate-500">
                    <span className="font-bold uppercase tracking-wider text-slate-800">
                      RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE — {SAMPLE_BLANK_QUOTATIONS[selectedSampleKey].client}
                    </span>
                    <span className="bg-amber-100 text-amber-800 px-1.5 py-0.5 rounded font-bold">
                      DOCUMENT VIERGE À REMPLIR
                    </span>
                  </div>

                  {/* Table headers in scanned doc */}
                  <div className="grid grid-cols-12 gap-1 bg-slate-100 p-1.5 font-bold uppercase text-[9px] text-slate-700 border border-slate-300">
                    <div className="col-span-2">N° Prix</div>
                    <div className="col-span-5">Désignation des Ouvrages</div>
                    <div className="col-span-1 text-center">Unité</div>
                    <div className="col-span-2 text-right">P.U Chiffres</div>
                    <div className="col-span-2 text-center text-amber-700 font-bold">P.U Lettres</div>
                  </div>

                  {/* Scanned rows with blank price placeholders */}
                  <div className="divide-y divide-slate-200 border-x border-b border-slate-300">
                    {SAMPLE_BLANK_QUOTATIONS[selectedSampleKey].articles.slice(0, 4).map((art, i) => (
                      <div key={i} className="grid grid-cols-12 gap-1 p-1.5 items-center relative">
                        <div className="col-span-2 font-bold text-blue-800">{art.code}</div>
                        <div className="col-span-5 truncate text-slate-800">{art.designation}</div>
                        <div className="col-span-1 text-center font-bold text-slate-500">{art.unit}</div>
                        <div className="col-span-2 text-right text-slate-400 italic">
                          {isScanning ? 'Analyse...' : '[ Vierge à chiffrer ]'}
                        </div>
                        <div className="col-span-2 text-center text-slate-400 italic text-[8px]">
                          {isScanning ? 'Rédaction...' : '[ Vierge en lettres ]'}
                        </div>

                        {/* Visual AI detection box */}
                        {isScanning && (
                          <div className="absolute inset-0 border border-cyan-400/80 bg-cyan-400/10 pointer-events-none" />
                        )}
                      </div>
                    ))}
                  </div>

                  <div className="mt-2 text-right text-[8px] text-slate-400 italic">
                    {SAMPLE_BLANK_QUOTATIONS[selectedSampleKey].articles.length} articles détectés dans le cahier des charges
                  </div>
                </div>

                {/* Scan Status HUD */}
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs">
                  <div className="flex items-center gap-2">
                    {isScanning ? (
                      <div className="flex items-center gap-2 text-cyan-400 font-bold">
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Étape {scanStep}/5 : {
                          scanStep === 1 ? 'Prétraitement de l\'image & OCR haute précision...' :
                          scanStep === 2 ? 'Reconnaissance de la grille & colonnes du devis...' :
                          scanStep === 3 ? 'Analyse sémantique & Mercuriale BTP Algérie 2026...' :
                          scanStep === 4 ? 'Calcul des déboursés secs (Ciment, Acier, MO)...' :
                          'Rédaction automatique des prix en chiffres et en toutes lettres !'
                        }</span>
                      </div>
                    ) : (
                      <div className="flex items-center gap-1.5 text-emerald-400 font-bold">
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Numérisation terminée : Devis vierge analysé et BPU rédigé avec succès</span>
                      </div>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => triggerScanProcess()}
                    disabled={isScanning}
                    className="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 text-white text-xs font-black flex items-center gap-1.5 shadow-md transition-all cursor-pointer"
                  >
                    <Sparkles className="w-3.5 h-3.5" />
                    <span>Relancer le Scan IA</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* ZONE 2 : PARAMÈTRES IA DE CHIFFRAGE & MERCURIALE ALGÉRIE */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
              <h3 className="text-xs font-black uppercase tracking-wider text-slate-800 flex items-center gap-2">
                <Sliders className="w-4 h-4 text-blue-600" />
                2. Paramètres IA d'Estimation & Mercuriale Algérie
              </h3>
              <span className="text-[11px] text-slate-500 font-mono">
                Base : Prix officiels BTP Algérie (Ciment CPJ 42.5, FeE500, Sable lavé, Main-d'œuvre qualifiée)
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 text-xs">
              <div className="p-3 rounded-xl border border-slate-200 space-y-1 bg-slate-50/50">
                <div className="flex justify-between items-center">
                  <span className="font-bold text-slate-700">Marge Bénéficiaire</span>
                  <strong className="font-mono text-emerald-700 font-black">{marginPct}%</strong>
                </div>
                <input
                  type="range"
                  min="5"
                  max="30"
                  value={marginPct}
                  onChange={(e) => {
                    const val = parseInt(e.target.value) || 15;
                    setMarginPct(val);
                    handleRecalculateAll(val, fraisGenerauxPct, wilayaCoeff);
                  }}
                  className="w-full accent-emerald-600 cursor-pointer"
                />
                <span className="text-[10px] text-slate-400 block">Bénéfice net & aléas</span>
              </div>

              <div className="p-3 rounded-xl border border-slate-200 space-y-1 bg-slate-50/50">
                <div className="flex justify-between items-center">
                  <span className="font-bold text-slate-700">Frais Généraux (FG)</span>
                  <strong className="font-mono text-blue-700 font-black">{fraisGenerauxPct}%</strong>
                </div>
                <input
                  type="range"
                  min="5"
                  max="20"
                  value={fraisGenerauxPct}
                  onChange={(e) => {
                    const val = parseInt(e.target.value) || 12;
                    setFraisGenerauxPct(val);
                    handleRecalculateAll(marginPct, val, wilayaCoeff);
                  }}
                  className="w-full accent-blue-600 cursor-pointer"
                />
                <span className="text-[10px] text-slate-400 block">Encadrement et conduite</span>
              </div>

              <div className="p-3 rounded-xl border border-slate-200 space-y-1 bg-slate-50/50">
                <span className="font-bold text-slate-700 block">Zone Géographique / Wilaya</span>
                <select
                  value={wilayaCoeff}
                  onChange={(e) => {
                    const val = parseFloat(e.target.value) || 1.0;
                    setWilayaCoeff(val);
                    handleRecalculateAll(marginPct, fraisGenerauxPct, val);
                  }}
                  className="w-full p-1.5 rounded-lg border border-slate-200 bg-white font-bold text-slate-800"
                >
                  <option value={1.0}>Wilayas Nord / Centre (Coeff 1.00)</option>
                  <option value={1.05}>Hauts-Plateaux (Coeff 1.05)</option>
                  <option value={1.25}>Grand Sud & Sahara (Coeff 1.25)</option>
                </select>
                <span className="text-[10px] text-slate-400 block">Majoration transport & climat</span>
              </div>

              <div className="p-3 rounded-xl border border-slate-200 space-y-1 bg-slate-50/50">
                <span className="font-bold text-slate-700 block">Régime TVA</span>
                <select
                  value={tvaRate}
                  onChange={(e) => setTvaRate(parseInt(e.target.value) || 9)}
                  className="w-full p-1.5 rounded-lg border border-slate-200 bg-white font-bold text-slate-800"
                >
                  <option value={9}>TVA 9% (Logement Public / LPL)</option>
                  <option value={19}>TVA 19% (Régime Général)</option>
                </select>
                <span className="text-[10px] text-slate-400 block">Régime fiscal algérien</span>
              </div>
            </div>
          </div>

          {/* ZONE 3 : TABLEAU COMPLET DU BORDEREAU DES PRIX UNITAIRES (BPU) EN CHIFFRES & LETTRES */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden space-y-0">
            {/* Table Header toolbar */}
            <div className="p-4 bg-slate-900 text-white flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h3 className="font-black text-sm uppercase tracking-wider flex items-center gap-2">
                  <FileText className="w-4 h-4 text-amber-400" />
                  Bordereau des Prix Unitaires Rédigé & Rempli par l'IA
                </h3>
                <p className="text-xs text-slate-300 mt-0.5">
                  Conforme à la règle des Marchés Publics Algériens : <strong className="text-amber-300">« Le prix en toutes lettres fait foi en cas de divergence »</strong>
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleInjectIntoCurrentMarket}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-black rounded-xl shadow-md transition-all flex items-center gap-1.5 cursor-pointer"
                >
                  <Plus className="w-4 h-4" />
                  <span>Injecter dans le Marché Actif (DQE)</span>
                </button>

                <button
                  type="button"
                  onClick={() => setActiveSubTab('bpu-print')}
                  className="px-4 py-2 bg-white/10 hover:bg-white/20 text-white text-xs font-black rounded-xl border border-white/20 transition-all flex items-center gap-1.5 cursor-pointer"
                >
                  <Printer className="w-4 h-4" />
                  <span>Imprimer BPU</span>
                </button>
              </div>
            </div>

            {/* Interactive BPU Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-100 border-b border-slate-200 text-[10px] font-black uppercase text-slate-700">
                    <th className="py-3 px-3 w-16">N° Prix</th>
                    <th className="py-3 px-3">Désignation des Ouvrages</th>
                    <th className="py-3 px-2 text-center w-12">U</th>
                    <th className="py-3 px-3 text-right w-20">Quantité</th>
                    <th className="py-3 px-3 text-right w-36 bg-blue-50/50">Prix Unitaire en Chiffres</th>
                    <th className="py-3 px-4 bg-amber-50/60 text-amber-950">Prix Unitaire en Toutes Lettres (Dinars Algériens)</th>
                    <th className="py-3 px-3 text-right w-32 font-black">Montant Total HT</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {bpuItems.map(item => {
                    const totalLineHT = item.quantity * item.unitPriceHT;

                    return (
                      <tr key={item.id} className="hover:bg-slate-50/80 transition-colors">
                        <td className="py-3 px-3 font-mono font-black text-blue-700 text-sm">
                          {item.code}
                        </td>
                        <td className="py-3 px-3 font-medium text-slate-900 max-w-xs">
                          <div>
                            <span className="font-bold">{item.designation}</span>
                            <div className="mt-1 flex items-center gap-2 text-[10px] text-slate-400 font-mono">
                              <span>Mat : {formatDA(item.breakdown.materiaux)}</span>
                              <span>•</span>
                              <span>MO : {formatDA(item.breakdown.mainOeuvre)}</span>
                              <span>•</span>
                              <span>Marge : +{formatDA(item.breakdown.marge)}</span>
                            </div>
                          </div>
                        </td>
                        <td className="py-3 px-2 text-center font-mono font-bold text-slate-700 bg-slate-50/50">
                          {item.unit}
                        </td>
                        <td className="py-3 px-3 text-right font-mono font-semibold text-slate-700">
                          {formatNumberDZ(item.quantity)}
                        </td>
                        <td className="py-3 px-3 text-right bg-blue-50/30">
                          <div className="flex items-center justify-end gap-1">
                            <input
                              type="number"
                              step="10"
                              value={item.unitPriceHT}
                              onChange={(e) => handlePriceChange(item.id, parseFloat(e.target.value) || 0)}
                              className="w-28 px-2 py-1 text-right font-mono font-black text-slate-900 text-xs bg-white rounded-lg border border-blue-300 focus:outline-hidden focus:ring-2 focus:ring-blue-500"
                            />
                            <span className="text-[10px] font-bold text-slate-400">DA</span>
                          </div>
                        </td>
                        <td className="py-3 px-4 bg-amber-50/30">
                          <div className="p-2 rounded-lg bg-white/80 border border-amber-200/80">
                            <span className="text-[11px] font-serif italic font-bold text-amber-950 uppercase tracking-wide block">
                              « {item.unitPriceWords} »
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-3 text-right font-mono font-black text-slate-900 text-sm">
                          {formatDA(totalLineHT)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Total Footer Summary */}
            <div className="bg-slate-900 text-white p-5 flex flex-col md:flex-row items-center justify-between gap-4">
              <div className="space-y-1 text-center md:text-left">
                <span className="text-xs text-slate-400 font-bold uppercase tracking-wider block">
                  Arrêté Total de l'Offre Financière (BPU / DQE) :
                </span>
                <p className="text-xs font-serif italic text-amber-300 font-bold uppercase">
                  « {totalBpuTTCWords} »
                </p>
              </div>

              <div className="flex items-center gap-6 font-mono text-right">
                <div>
                  <span className="text-[11px] text-slate-400 block">Total HT</span>
                  <strong className="text-base font-black text-white">{formatDA(totalBpuHT)}</strong>
                </div>
                <div>
                  <span className="text-[11px] text-slate-400 block">TVA ({tvaRate}%)</span>
                  <strong className="text-base font-black text-blue-300">+{formatDA(totalTva)}</strong>
                </div>
                <div className="pl-4 border-l border-slate-700">
                  <span className="text-[11px] text-amber-400 font-bold uppercase tracking-wider block">Total TTC</span>
                  <strong className="text-xl font-black text-amber-400">{formatDA(totalBpuTTC)}</strong>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* SUB-TAB 2 : APERÇU IMPRESSION BPU A4 OFFICIEL ALGÉRIE   */}
      {/* ======================================================== */}
      {activeSubTab === 'bpu-print' && (
        <div className="space-y-6">
          <div className="print:hidden bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex items-center justify-between gap-4">
            <span className="text-xs font-bold text-slate-700">
              Aperçu prêt pour impression A4 vectorielle conforme aux soumissions de marchés publics
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setActiveSubTab('scanner')}
                className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-bold text-slate-700 hover:bg-slate-50"
              >
                Retour à l'éditeur
              </button>
              <button
                type="button"
                onClick={() => window.print()}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-black rounded-xl shadow-md flex items-center gap-2 cursor-pointer"
              >
                <Printer className="w-4 h-4" />
                <span>Imprimer / Sauvegarder en PDF (A4)</span>
              </button>
            </div>
          </div>

          {/* Official Printable BPU Sheet */}
          <div className="bg-white p-8 sm:p-12 rounded-2xl border border-slate-300 shadow-xl max-w-5xl mx-auto print:p-0 print:border-none print:shadow-none print:max-w-none text-slate-900 font-sans">
            {/* Header officiel */}
            <div className="text-center space-y-1 pb-3 border-b-2 border-slate-900">
              <p className="font-serif uppercase font-bold text-xs tracking-wider text-slate-800">
                الجمهورية الجزائرية الديمقراطية الشعبية
              </p>
              <p className="font-serif uppercase font-black text-xs tracking-wider text-slate-900">
                RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE
              </p>
              <p className="text-[11px] font-semibold text-slate-600">
                Ministère de l'Habitat, de l'Urbanisme et de la Ville • {SAMPLE_BLANK_QUOTATIONS[selectedSampleKey].client}
              </p>
            </div>

            {/* Titre et cartouche de soumission */}
            <div className="my-4 p-3.5 border border-slate-400 rounded bg-slate-50/50 print:bg-white text-xs">
              <div className="flex justify-between items-start border-b border-slate-300 pb-2 mb-2">
                <div>
                  <h2 className="text-sm font-black uppercase text-slate-900">
                    BORDEREAU DES PRIX UNITAIRES (B.P.U)
                  </h2>
                  <p className="text-[11px] text-slate-600 font-medium">
                    Projet : {currentMarket.title} • {SAMPLE_BLANK_QUOTATIONS[selectedSampleKey].source}
                  </p>
                </div>
                <div className="text-right font-mono text-[11px]">
                  <strong>Wilaya :</strong> {SAMPLE_BLANK_QUOTATIONS[selectedSampleKey].wilaya}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 text-[11px]">
                <div>
                  <p><strong>Soumissionnaire :</strong> {currentMarket.enterpriseName}</p>
                  <p className="text-slate-600">RC N° : 22/00-1458921 • NIF : 000522019485123 • NIS : 0005221400234</p>
                </div>
                <div className="text-right">
                  <p><strong>Banque & Agence :</strong> BNA Agence Centrale Sidi Bel Abbès</p>
                  <p className="text-slate-600 font-mono">Compte RIB : 001 00412 0300054892 41</p>
                </div>
              </div>
            </div>

            {/* Tableau BPU avec colonnes officielles Chiffres & Toutes Lettres */}
            <table className="w-full text-left border-collapse border border-slate-400 text-[10px] print:text-[9px]">
              <thead>
                <tr className="bg-slate-200 border-b border-slate-400 font-black uppercase text-slate-900">
                  <th className="border border-slate-400 p-2 w-16 text-center">N° Prix</th>
                  <th className="border border-slate-400 p-2">Désignation des Ouvrages</th>
                  <th className="border border-slate-400 p-2 text-center w-12">Unité</th>
                  <th className="border border-slate-400 p-2 text-right w-24">Prix Unitaire en Chiffres (DA HT)</th>
                  <th className="border border-slate-400 p-2">Prix Unitaire en Toutes Lettres (En Dinars Algériens)</th>
                </tr>
              </thead>
              <tbody>
                {bpuItems.map((item, idx) => (
                  <tr key={idx} className="border-b border-slate-300">
                    <td className="border border-slate-400 p-2 font-mono text-center font-bold text-blue-900">
                      {item.code}
                    </td>
                    <td className="border border-slate-400 p-2 font-medium">
                      {item.designation}
                    </td>
                    <td className="border border-slate-400 p-2 text-center font-mono font-bold">
                      {item.unit}
                    </td>
                    <td className="border border-slate-400 p-2 text-right font-mono font-black text-slate-900 whitespace-nowrap">
                      {formatDA(item.unitPriceHT)}
                    </td>
                    <td className="border border-slate-400 p-2 font-serif italic text-slate-900 font-bold uppercase">
                      « {item.unitPriceWords} »
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Note réglementaire légale */}
            <div className="mt-3 p-2.5 border border-slate-300 rounded text-[9px] text-slate-600 italic">
              <strong>Règle d'adjudication (Code des Marchés Publics Algérien) :</strong> « En cas de discordance ou de différence entre le prix unitaire exprimé en chiffres et celui exprimé en toutes lettres, c'est le montant écrit en toutes lettres qui prévaut et fait foi pour l'évaluation et l'attribution de l'offre. »
            </div>

            {/* Cadre de signature & cachet officiel du soumissionnaire */}
            <div className="grid grid-cols-2 gap-8 pt-8 mt-6 border-t-2 border-slate-900 text-xs">
              <div className="p-3 border border-slate-300 rounded space-y-1">
                <span className="font-bold block">Montant Total de la Soumission :</span>
                <p className="font-mono font-black text-sm text-slate-900">
                  Total HT : {formatDA(totalBpuHT)} • TTC ({tvaRate}%) : {formatDA(totalBpuTTC)}
                </p>
                <p className="font-serif italic text-[10px] text-slate-700 uppercase pt-1">
                  Arrêté à : « {totalBpuTTCWords} »
                </p>
              </div>

              <div className="p-3 border border-slate-400 rounded h-36 flex flex-col justify-between text-center">
                <div>
                  <strong className="text-[11px] uppercase tracking-wider block">
                    Pour l'Entreprise Soumissionnaire
                  </strong>
                  <span className="text-[10px] text-slate-500 italic">
                    (Lu et accepté le présent Bordereau des Prix Unitaires)
                  </span>
                </div>
                <div className="text-[9px] text-slate-400">
                  Date, Signature et Cachet humide de l'Entreprise
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
