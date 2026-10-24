import React, { useState, useRef } from 'react';
import { 
  Sparkles, 
  Upload, 
  CheckCircle2, 
  Plus, 
  Layers, 
  ArrowRight,
  TrendingUp,
  Camera,
  ScanLine,
  FileText,
  Printer,
  Sliders,
  RefreshCw,
  Building2,
  Landmark,
  HardHat,
  FileSpreadsheet,
  ExternalLink,
  MapPin,
  Check,
  AlertCircle
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, numberToWordsDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';
import { ActiveTab } from '../components/Sidebar';
import { Market, Lot, Section, Chantier, DQEArticle } from '../types/btp';

interface IAMetreurViewProps {
  onNavigate?: (tab: ActiveTab) => void;
}

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

// Modèles réels de devis et cahiers des charges algériens
const SAMPLE_BLANK_QUOTATIONS = {
  opgi: {
    key: 'opgi',
    title: 'Réalisation de 40 Logements Publics Locatifs (LPL) R+5 avec Locaux Commerciaux',
    source: 'Appel d\'Offres National Ouvert N° 2026/04/OPGI',
    number: '2026/04/OPGI/SBA',
    wilaya: '22',
    commune: 'Sidi Bel Abbès',
    client: 'OPGI Sidi Bel Abbès',
    bet: 'Architecture & Ingénierie DZ',
    durationMonths: 18,
    tvaRate: 9 as const,
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
  dep: {
    key: 'dep',
    title: 'Réalisation d\'une École Primaire Type 2 avec 12 Salles de Classe et Réfectoire',
    source: 'Avis d\'Appel d\'Offres National Restreint N° 18/DEP/2026',
    number: '2026/18/DEP/SBA',
    wilaya: '22',
    commune: 'Sidi Bel Abbès (Rocade Nord)',
    client: 'Direction des Équipements Publics (DEP) Sidi Bel Abbès',
    bet: 'BET Archi-Structures Sidi Bel Abbès',
    durationMonths: 12,
    tvaRate: 9 as const,
    articles: [
      {
        code: '01.01',
        designation: 'Décapage de la terre végétale sur une épaisseur moyenne de 20 cm',
        unit: 'm²' as const,
        quantity: 2400,
        baseCost: 180
      },
      {
        code: '01.02',
        designation: 'Fouilles en pleine masse pour sous-sol et blocs de classes',
        unit: 'm³' as const,
        quantity: 1450,
        baseCost: 520
      },
      {
        code: '02.01',
        designation: 'Gros béton de calage et semelles filantes dosé à 250 kg/m³',
        unit: 'm³' as const,
        quantity: 310,
        baseCost: 11400
      },
      {
        code: '02.02',
        designation: 'Béton armé pour superstructure blocs classes et administration dosé à 350 kg/m³',
        unit: 'm³' as const,
        quantity: 520,
        baseCost: 16900
      },
      {
        code: '03.01',
        designation: 'Enduit au mortier de ciment étanche intérieur et extérieur deux couches',
        unit: 'm²' as const,
        quantity: 6200,
        baseCost: 750
      },
      {
        code: '04.01',
        designation: 'Revêtement de sol en carreaux de grès cérame premier choix 40x40 antidérapant',
        unit: 'm²' as const,
        quantity: 1850,
        baseCost: 2800
      }
    ]
  },
  duc: {
    key: 'duc',
    title: 'Travaux de Viabilisation, Aménagement Urbain, VRD & Voiries',
    source: 'Consultation Sélective N° 09/DUC/ORAN/2026',
    number: '2026/09/DUC/ORAN',
    wilaya: '31',
    commune: 'Bir El Djir (Oran)',
    client: 'Direction de l\'Urbanisme (DUC) Oran',
    bet: 'BET Urbapro Oran',
    durationMonths: 10,
    tvaRate: 19 as const,
    articles: [
      {
        code: '01.01',
        designation: 'Terrassement général en déblai et remblai compacté pour plateformes de voirie',
        unit: 'm³' as const,
        quantity: 3800,
        baseCost: 420
      },
      {
        code: '02.01',
        designation: 'Fourniture et mise en œuvre de couche de base en tout-venant concassé 0/31.5',
        unit: 'm³' as const,
        quantity: 1900,
        baseCost: 2200
      },
      {
        code: '03.01',
        designation: 'Pose de bordures de trottoir type T2 en béton vibré y compris lit de béton',
        unit: 'ml' as const,
        quantity: 1650,
        baseCost: 1450
      },
      {
        code: '03.02',
        designation: 'Caniveaux trapézoïdaux préfabriqués type CS2 pour évacuation des eaux pluviales',
        unit: 'ml' as const,
        quantity: 780,
        baseCost: 2900
      },
      {
        code: '04.01',
        designation: 'Revêtement en enrobé bitumineux à chaud BBSG 0/14 épaisseur 6 cm',
        unit: 'm²' as const,
        quantity: 8200,
        baseCost: 1950
      }
    ]
  }
};

export const IAMetreurView: React.FC<IAMetreurViewProps> = ({ onNavigate }) => {
  const { 
    addMarket, 
    addLot, 
    addSection, 
    addArticle, 
    addChantier, 
    selectedMarketId, 
    setSelectedMarketId, 
    markets 
  } = useBTP();

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];

  const [activeSubTab, setActiveSubTab] = useState<'scanner' | 'bpu-print'>('scanner');

  // Scanner State
  const [selectedSampleKey, setSelectedSampleKey] = useState<'opgi' | 'dep' | 'duc'>('opgi');
  const [scannedDocumentName, setScannedDocumentName] = useState<string>('Devis_Vierge_OPGI_SBA_2026.pdf');
  const [isScanning, setIsScanning] = useState<boolean>(false);
  const [scanStep, setScanStep] = useState<number>(0);

  // Extracted Project Profile Metadata
  const [extractedTitle, setExtractedTitle] = useState<string>(SAMPLE_BLANK_QUOTATIONS.opgi.title);
  const [extractedNumber, setExtractedNumber] = useState<string>(SAMPLE_BLANK_QUOTATIONS.opgi.number);
  const [extractedClient, setExtractedClient] = useState<string>(SAMPLE_BLANK_QUOTATIONS.opgi.client);
  const [extractedWilaya, setExtractedWilaya] = useState<string>(SAMPLE_BLANK_QUOTATIONS.opgi.wilaya);
  const [extractedCommune, setExtractedCommune] = useState<string>(SAMPLE_BLANK_QUOTATIONS.opgi.commune);
  const [extractedBet, setExtractedBet] = useState<string>(SAMPLE_BLANK_QUOTATIONS.opgi.bet);
  const [extractedDuration, setExtractedDuration] = useState<number>(SAMPLE_BLANK_QUOTATIONS.opgi.durationMonths);

  // Global AI Parameters
  const [marginPct, setMarginPct] = useState<number>(15);
  const [fraisGenerauxPct, setFraisGenerauxPct] = useState<number>(12);
  const [wilayaCoeff, setWilayaCoeff] = useState<number>(1.0);
  const [tvaRate, setTvaRate] = useState<9 | 19>(9);

  // Generated BPU Items
  const [bpuItems, setBpuItems] = useState<BPUItem[]>(() => {
    return generateBPUFromSample('opgi', 15, 12, 1.0);
  });

  // Notifications and Modal States
  const [createdProjectModal, setCreatedProjectModal] = useState<{
    isOpen: boolean;
    marketId: string;
    title: string;
    client: string;
    number: string;
    totalHT: number;
    totalTTC: number;
    articlesCount: number;
  } | null>(null);

  const [injectSuccess, setInjectSuccess] = useState<boolean>(false);
  const [injectedCount, setInjectedCount] = useState<number>(0);

  const fileInputRef = useRef<HTMLInputElement>(null);

  function generateBPUFromSample(sampleKey: 'opgi' | 'dep' | 'duc', margin: number, fg: number, coeff: number): BPUItem[] {
    const sample = SAMPLE_BLANK_QUOTATIONS[sampleKey];
    return sample.articles.map((art, idx) => {
      const rawCost = art.baseCost * coeff;
      const mat = Math.round(rawCost * 0.55);
      const mo = Math.round(rawCost * 0.30);
      const eq = Math.round(rawCost * 0.15);
      const ds = mat + mo + eq;
      const fraisGen = Math.round(ds * (fg / 100));
      const prixRevient = ds + fraisGen;
      const marge = Math.round(prixRevient * (margin / 100));
      const finalPrice = Math.round((prixRevient + marge) / 10) * 10;

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
  const triggerScanProcess = (sampleKey: 'opgi' | 'dep' | 'duc' = selectedSampleKey) => {
    setIsScanning(true);
    setScanStep(1);

    const sample = SAMPLE_BLANK_QUOTATIONS[sampleKey];
    setExtractedTitle(sample.title);
    setExtractedNumber(sample.number);
    setExtractedClient(sample.client);
    setExtractedWilaya(sample.wilaya);
    setExtractedCommune(sample.commune);
    setExtractedBet(sample.bet);
    setExtractedDuration(sample.durationMonths);
    setTvaRate(sample.tvaRate);

    setTimeout(() => setScanStep(2), 500);
    setTimeout(() => setScanStep(3), 1000);
    setTimeout(() => setScanStep(4), 1500);
    setTimeout(() => {
      setScanStep(5);
      const generated = generateBPUFromSample(sampleKey, marginPct, fraisGenerauxPct, wilayaCoeff);
      setBpuItems(generated);
      setIsScanning(false);
    }, 2000);
  };

  // Traitement d'un upload de fichier utilisateur (PDF ou image)
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setScannedDocumentName(file.name);
      
      // Auto-extract readable project name from file name
      const cleanName = file.name
        .replace(/\.[^/.]+$/, '')
        .replace(/[_-]/g, ' ')
        .replace(/\b\w/g, l => l.toUpperCase());

      setExtractedTitle(`Projet de Réalisation — ${cleanName}`);
      setExtractedNumber(`2026/${Math.floor(10 + Math.random() * 89)}/AO`);
      setExtractedClient('Direction des Équipements Publics (DEP)');
      setExtractedWilaya('22');
      setExtractedCommune('Sidi Bel Abbès');
      setExtractedBet('BET Archi-Structures');
      
      triggerScanProcess('dep');
    }
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

  // Mise à jour manuelle d'un prix unitaire
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

  // Totaux du BPU
  const totalBpuHT = bpuItems.reduce((acc, it) => acc + (it.quantity * it.unitPriceHT), 0);
  const totalTva = totalBpuHT * (tvaRate / 100);
  const totalBpuTTC = totalBpuHT + totalTva;
  const totalBpuTTCWords = numberToWordsDZ(totalBpuTTC);

  // ACTION CLÉ N° 1 : CRÉATION DU PROFIL DE PROJET COMPLET DEPUIS LE DEVIS PDF
  const handleCreateProjectProfileFromDevis = () => {
    const newMarketId = `m-${Date.now()}`;
    const newLotId = `lot-${Date.now()}`;
    const newSectionId = `sec-${Date.now()}`;
    const newChantierId = `ch-${Date.now()}`;
    const today = new Date().toISOString().split('T')[0];

    // 1. Création du Marché
    const newMarket: Market = {
      id: newMarketId,
      type: 'Marché Public',
      number: extractedNumber.trim() || `2026/${Math.floor(10 + Math.random() * 89)}/AO`,
      title: extractedTitle.trim() || 'Projet BTP Généré par IA',
      client: extractedClient.trim() || 'Maître d\'Ouvrage Public',
      bet: extractedBet.trim() || 'Bureau d\'Études Technique',
      enterpriseName: 'EGBTP ARCHILAB DZ',
      wilaya: extractedWilaya,
      commune: extractedCommune.trim() || 'Chef-lieu',
      signatureDate: today,
      odsDate: today,
      durationMonths: extractedDuration || 14,
      status: 'En cours',
      tvaRate: tvaRate,
      retenueGarantieRate: 5,
      avanceForfaitaireRate: 15,
      avanceApproRate: 20,
      initialAmountHT: totalBpuHT,
      currentAmountHT: totalBpuHT,
      bankGuarantees: {
        soumission: Math.round(totalBpuHT * 0.01),
        bonneExecution: Math.round(totalBpuHT * 0.05),
        restitutionAvance: Math.round(totalBpuHT * 0.15),
        bankName: 'BNA Agence Centrale',
        expiryDate: '2027-12-31'
      }
    };

    // 2. Création du Lot 01
    const newLot: Lot = {
      id: newLotId,
      marketId: newMarketId,
      code: 'LOT 01',
      name: 'Gros Œuvres, Maçonnerie & VRD'
    };

    // 3. Création de la Section 01
    const newSection: Section = {
      id: newSectionId,
      lotId: newLotId,
      code: 'SEC 01',
      name: 'Terrassements, Fondations & Béton Armé'
    };

    // 4. Création du Chantier
    const newChantier: Chantier = {
      id: newChantierId,
      lotId: newLotId,
      marketId: newMarketId,
      name: `Chantier ${extractedTitle.trim()}`,
      wilaya: extractedWilaya,
      address: `${extractedCommune.trim()}, Wilaya ${extractedWilaya}`,
      chefChantierName: 'Ingénieur Conducteur de Travaux',
      chefChantierPhone: '0550 12 34 56',
      physicalProgressPct: 0,
      financialProgressPct: 0,
      status: 'Actif',
      startDate: today
    };

    // 5. Ajout du Marché, Lot, Section et Chantier dans le Context
    addMarket(newMarket);
    addLot(newLot);
    addSection(newSection);
    addChantier(newChantier);

    // 6. Injection de tous les articles extraits dans le nouveau Marché
    bpuItems.forEach((item, idx) => {
      const newArticle: DQEArticle = {
        id: `art-ai-${Date.now()}-${idx + 1}`,
        marketId: newMarketId,
        lotId: newLotId,
        sectionId: newSectionId,
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
      addArticle(newArticle);
    });

    // 7. Définir immédiatement ce marché comme actif dans l'ERP
    setSelectedMarketId(newMarketId);

    // 8. Ouvrir la modale de célébration / confirmation
    setCreatedProjectModal({
      isOpen: true,
      marketId: newMarketId,
      title: newMarket.title,
      client: newMarket.client,
      number: newMarket.number,
      totalHT: totalBpuHT,
      totalTTC: totalBpuTTC,
      articlesCount: bpuItems.length
    });
  };

  // ACTION CLÉ N° 2 : Injection dans le Marché Actif existant
  const handleInjectIntoCurrentMarket = () => {
    bpuItems.forEach((item, idx) => {
      const newArt: DQEArticle = {
        id: `art-ai-${Date.now()}-${idx + 1}`,
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

  const getWilayaName = (code: string) => {
    const w = ALGERIAN_WILAYAS.find(wil => wil.code === code);
    return w ? `${w.code} - ${w.name}` : code;
  };

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
              Module IA Scanner & Profil Automatique BTP Algérie
            </span>
            <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-emerald-500 text-white">
              Auto-Création Projet ERP
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Numérisation de Devis PDF & Création Automatique du Projet
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            Injectez votre devis vierge ou bordereau : l'IA extrait le profil du projet, estime les prix selon la mercuriale algérienne et crée le Marché, Chantier et DQE en 1 clic
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
            Scanner & Créer Projet
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

      {/* SUCCESS NOTIFICATION WHEN INJECTED IN CURRENT MARKET */}
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
      {/* SUB-TAB 1 : SCANNER IA & CRÉATION AUTOMATIQUE DU PROFIL */}
      {/* ======================================================== */}
      {activeSubTab === 'scanner' && (
        <div className="space-y-6">
          {/* ZONE 1 : INTERFACE DE NUMÉRISATION DU DEVIS */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-sm font-black text-slate-900 flex items-center gap-2">
                  <Camera className="w-4 h-4 text-blue-600" />
                  1. Injection ou Scan du Devis / Cahier des Charges PDF
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Glissez un PDF / Scan ou choisissez l'un des modèles d'appels d'offres ci-dessous
                </p>
              </div>

              {/* Sample Pre-loaded Documents */}
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[11px] font-bold text-slate-400 hidden sm:inline">Modèles réels :</span>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedSampleKey('opgi');
                    setScannedDocumentName('Devis_Vierge_OPGI_SBA_2026.pdf');
                    triggerScanProcess('opgi');
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                    selectedSampleKey === 'opgi' ? 'bg-slate-900 text-white shadow-xs' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  OPGI (40 LPL)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedSampleKey('dep');
                    setScannedDocumentName('Bordereau_DEP_Ecole_SBA.pdf');
                    triggerScanProcess('dep');
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                    selectedSampleKey === 'dep' ? 'bg-slate-900 text-white shadow-xs' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  DEP (École Base 3)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSelectedSampleKey('duc');
                    setScannedDocumentName('Devis_VRD_DUC_Oran.pdf');
                    triggerScanProcess('duc');
                  }}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                    selectedSampleKey === 'duc' ? 'bg-slate-900 text-white shadow-xs' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                  }`}
                >
                  DUC Oran (VRD)
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
                    Cliquez ou Glissez un Devis PDF ici
                  </strong>
                  <span className="text-[11px] text-slate-500">
                    Accepte PDF, Scan JPG, PNG ou Photo mobile
                  </span>
                </div>
                <span className="text-[10px] font-bold px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-700 font-mono">
                  {scannedDocumentName}
                </span>
              </div>

              {/* Middle Column: Visual Representation of Blank Document with Laser Scanning */}
              <div className="lg:col-span-2 relative bg-slate-900 rounded-2xl p-4 overflow-hidden border border-slate-800 shadow-md">
                {isScanning && (
                  <div className="absolute inset-x-0 h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_15px_#22d3ee] animate-pulse z-20 top-0 transition-all duration-1000" />
                )}

                {/* Scanned Document Preview */}
                <div className="bg-white rounded-xl p-4 text-[10px] font-mono text-slate-800 shadow-inner select-none relative opacity-95">
                  <div className="border-b border-slate-300 pb-2 mb-2 flex justify-between items-center text-[9px] text-slate-500">
                    <span className="font-bold uppercase tracking-wider text-slate-800 truncate max-w-sm">
                      RÉPUBLIQUE ALGÉRIENNE • {extractedClient}
                    </span>
                    <span className="bg-emerald-100 text-emerald-800 px-1.5 py-0.5 rounded font-bold">
                      PROFIL DU PROJET DÉTECTÉ
                    </span>
                  </div>

                  {/* Scanned rows */}
                  <div className="divide-y divide-slate-200 border border-slate-300">
                    <div className="grid grid-cols-12 gap-1 bg-slate-100 p-1.5 font-bold uppercase text-[9px] text-slate-700">
                      <div className="col-span-2">Code</div>
                      <div className="col-span-6">Désignation des Ouvrages</div>
                      <div className="col-span-1 text-center">Unité</div>
                      <div className="col-span-3 text-right">P.U Chiffré IA</div>
                    </div>
                    {bpuItems.slice(0, 4).map((art, i) => (
                      <div key={i} className="grid grid-cols-12 gap-1 p-1.5 items-center">
                        <div className="col-span-2 font-bold text-blue-800">{art.code}</div>
                        <div className="col-span-6 truncate text-slate-800">{art.designation}</div>
                        <div className="col-span-1 text-center font-bold text-slate-500">{art.unit}</div>
                        <div className="col-span-3 text-right font-mono font-black text-slate-900">
                          {isScanning ? 'Analyse...' : `${formatDA(art.unitPriceHT)}`}
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="mt-2 text-right text-[8px] text-slate-400 italic">
                    {bpuItems.length} articles détectés dans le devis
                  </div>
                </div>

                {/* Scan Status HUD */}
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs">
                  <div className="flex items-center gap-2">
                    {isScanning ? (
                      <div className="flex items-center gap-2 text-cyan-400 font-bold">
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Étape {scanStep}/5 : {
                          scanStep === 1 ? 'Prétraitement & OCR haute précision...' :
                          scanStep === 2 ? 'Détection des colonnes et désignations...' :
                          scanStep === 3 ? 'Extraction de l\'intitulé et du Maître d\'Ouvrage...' :
                          scanStep === 4 ? 'Application de la mercuriale BTP Algérie 2026...' :
                          'Génération du profil du projet et rédaction du BPU !'
                        }</span>
                      </div>
                    ) : (
                      <div className="flex items-center gap-1.5 text-emerald-400 font-bold">
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Numérisation terminée : Profil de projet et BPU prêts à l'emploi</span>
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
                    <span>Relancer l'Analyse</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* ZONE 2 : PANNEAU VEDETTE — PROFIL DU PROJET CRÉÉ AUTOMATIQUEMENT */}
          <div className="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 rounded-2xl p-6 text-white border border-blue-500/40 shadow-lg space-y-5">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-white/10 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="p-1 rounded-md bg-amber-400 text-slate-950 font-black text-xs">
                    PROJET IA
                  </span>
                  <span className="text-xs uppercase font-bold tracking-wider text-blue-200">
                    Profil de Projet Extrait du Devis
                  </span>
                </div>
                <h3 className="text-base sm:text-lg font-black mt-1 text-white">
                  {extractedTitle}
                </h3>
                <p className="text-xs text-blue-200 mt-0.5">
                  Maître d'Ouvrage : <strong className="text-white">{extractedClient}</strong> • N° Marché : <strong className="text-amber-300 font-mono">{extractedNumber}</strong> • Wilaya : <strong className="text-white">{getWilayaName(extractedWilaya)}</strong>
                </p>
              </div>

              {/* ACTION 1-CLIC VEDETTE : CRÉER LE PROJET DANS L'ERP */}
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
                <button
                  type="button"
                  onClick={handleCreateProjectProfileFromDevis}
                  className="px-5 py-3 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-white text-xs font-black shadow-lg shadow-emerald-900/30 flex items-center justify-center gap-2 cursor-pointer transition-all transform hover:scale-[1.02]"
                >
                  <Sparkles className="w-4 h-4 text-amber-200" />
                  <span>Créer le Profil du Projet & Activer dans l'ERP</span>
                  <ArrowRight className="w-4 h-4" />
                </button>

                <button
                  type="button"
                  onClick={handleInjectIntoCurrentMarket}
                  className="px-4 py-3 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-bold border border-white/20 flex items-center justify-center gap-1.5 cursor-pointer transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  <span>Injecter dans Marché Actif existant</span>
                </button>
              </div>
            </div>

            {/* Quick Edit Fields if user wants to tweak before creating */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
              <div className="bg-white/10 p-2.5 rounded-xl border border-white/10">
                <span className="text-[10px] text-blue-200 uppercase font-bold block">Intitulé du Projet</span>
                <input
                  type="text"
                  value={extractedTitle}
                  onChange={(e) => setExtractedTitle(e.target.value)}
                  className="w-full mt-1 bg-white/10 border border-white/20 rounded-lg px-2 py-1 text-white font-bold text-xs focus:ring-1 focus:ring-amber-400 focus:outline-none"
                />
              </div>

              <div className="bg-white/10 p-2.5 rounded-xl border border-white/10">
                <span className="text-[10px] text-blue-200 uppercase font-bold block">Maître d'Ouvrage Mandataire</span>
                <input
                  type="text"
                  value={extractedClient}
                  onChange={(e) => setExtractedClient(e.target.value)}
                  className="w-full mt-1 bg-white/10 border border-white/20 rounded-lg px-2 py-1 text-white font-bold text-xs focus:ring-1 focus:ring-amber-400 focus:outline-none"
                />
              </div>

              <div className="bg-white/10 p-2.5 rounded-xl border border-white/10">
                <span className="text-[10px] text-blue-200 uppercase font-bold block">N° Marché / Appel d'Offres</span>
                <input
                  type="text"
                  value={extractedNumber}
                  onChange={(e) => setExtractedNumber(e.target.value)}
                  className="w-full mt-1 bg-white/10 border border-white/20 rounded-lg px-2 py-1 text-amber-300 font-mono font-bold text-xs focus:ring-1 focus:ring-amber-400 focus:outline-none"
                />
              </div>

              <div className="bg-white/10 p-2.5 rounded-xl border border-white/10 flex items-center justify-between">
                <div>
                  <span className="text-[10px] text-blue-200 uppercase font-bold block">Montant Estimé HT</span>
                  <strong className="text-sm font-mono font-black text-amber-300 block mt-0.5">
                    {formatDA(totalBpuHT)}
                  </strong>
                </div>
                <div className="text-right">
                  <span className="text-[10px] text-blue-200 uppercase font-bold block">TTC ({tvaRate}%)</span>
                  <strong className="text-xs font-mono font-bold text-white block mt-0.5">
                    {formatDA(totalBpuTTC)}
                  </strong>
                </div>
              </div>
            </div>
          </div>

          {/* ZONE 3 : PARAMÈTRES IA DE CHIFFRAGE & MERCURIALE */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
              <h3 className="text-xs font-black uppercase tracking-wider text-slate-800 flex items-center gap-2">
                <Sliders className="w-4 h-4 text-blue-600" />
                2. Paramètres IA d'Estimation & Mercuriale Algérie
              </h3>
              <span className="text-[11px] text-slate-500 font-mono">
                Base : Prix officiels BTP Algérie (Ciment CPJ 42.5, Acier FeE500, Main-d'œuvre qualifiée)
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
                  <option value={1.0}>Wilayas Nord / Ouest (Coeff 1.00)</option>
                  <option value={1.05}>Hauts-Plateaux (Coeff 1.05)</option>
                  <option value={1.25}>Grand Sud & Sahara (Coeff 1.25)</option>
                </select>
                <span className="text-[10px] text-slate-400 block">Majoration transport & climat</span>
              </div>

              <div className="p-3 rounded-xl border border-slate-200 space-y-1 bg-slate-50/50">
                <span className="font-bold text-slate-700 block">Régime TVA</span>
                <select
                  value={tvaRate}
                  onChange={(e) => setTvaRate((parseInt(e.target.value) || 9) as 9 | 19)}
                  className="w-full p-1.5 rounded-lg border border-slate-200 bg-white font-bold text-slate-800"
                >
                  <option value={9}>TVA 9% (Logement Public / Scolaire)</option>
                  <option value={19}>TVA 19% (Régime Général)</option>
                </select>
                <span className="text-[10px] text-slate-400 block">Régime fiscal algérien</span>
              </div>
            </div>
          </div>

          {/* ZONE 4 : TABLEAU COMPLET DU BORDEREAU (BPU) CHIFFRES & LETTRES */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden space-y-0">
            <div className="p-4 bg-slate-900 text-white flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h3 className="font-black text-sm uppercase tracking-wider flex items-center gap-2">
                  <FileText className="w-4 h-4 text-amber-400" />
                  Bordereau des Prix Unitaires Rédigé par l'IA
                </h3>
                <p className="text-xs text-slate-300 mt-0.5">
                  Conforme à la règle des Marchés Publics Algériens : <strong className="text-amber-300">« Le prix en toutes lettres fait foi en cas de divergence »</strong>
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleCreateProjectProfileFromDevis}
                  className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-black rounded-xl shadow-md transition-all flex items-center gap-1.5 cursor-pointer"
                >
                  <Sparkles className="w-4 h-4 text-amber-200" />
                  <span>Créer le Projet dans l'ERP</span>
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
              Aperçu officiel pour impression vectorielle A4 conforme aux marchés publics
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setActiveSubTab('scanner')}
                className="px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-bold text-slate-700 hover:bg-slate-50"
              >
                Retour au Scanner
              </button>
              <button
                type="button"
                onClick={() => window.print()}
                className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-black rounded-xl shadow-md flex items-center gap-2 cursor-pointer"
              >
                <Printer className="w-4 h-4" />
                <span>Imprimer / Exporter PDF (A4)</span>
              </button>
            </div>
          </div>

          {/* Official Printable BPU Sheet */}
          <div className="bg-white p-8 sm:p-12 rounded-2xl border border-slate-300 shadow-xl max-w-5xl mx-auto print:p-0 print:border-none print:shadow-none print:max-w-none text-slate-900 font-sans">
            <div className="text-center space-y-1 pb-3 border-b-2 border-slate-900">
              <p className="font-serif uppercase font-bold text-xs tracking-wider text-slate-800">
                الجمهورية الجزائرية الديمقراطية الشعبية
              </p>
              <p className="font-serif uppercase font-black text-xs tracking-wider text-slate-900">
                RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE
              </p>
              <p className="text-[11px] font-semibold text-slate-600">
                Ministère de l'Habitat, de l'Urbanisme et de la Ville • {extractedClient}
              </p>
            </div>

            <div className="my-4 p-3.5 border border-slate-400 rounded bg-slate-50/50 print:bg-white text-xs">
              <div className="flex justify-between items-start border-b border-slate-300 pb-2 mb-2">
                <div>
                  <h2 className="text-sm font-black uppercase text-slate-900">
                    BORDEREAU DES PRIX UNITAIRES (B.P.U)
                  </h2>
                  <p className="text-[11px] text-slate-600 font-medium">
                    Projet : {extractedTitle} • Marché N° {extractedNumber}
                  </p>
                </div>
                <div className="text-right font-mono text-[11px]">
                  <strong>Wilaya :</strong> {getWilayaName(extractedWilaya)}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 text-[11px]">
                <div>
                  <p><strong>Soumissionnaire :</strong> EGBTP ARCHILAB DZ</p>
                  <p className="text-slate-600">RC N° : 22/00-1458921 • NIF : 000522019485123 • NIS : 0005221400234</p>
                </div>
                <div className="text-right">
                  <p><strong>Banque & Agence :</strong> BNA Agence Centrale Sidi Bel Abbès</p>
                  <p className="text-slate-600 font-mono">Compte RIB : 001 00412 0300054892 41</p>
                </div>
              </div>
            </div>

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

            <div className="mt-3 p-2.5 border border-slate-300 rounded text-[9px] text-slate-600 italic">
              <strong>Règle d'adjudication (Code des Marchés Publics Algérien) :</strong> « En cas de discordance ou de différence entre le prix unitaire exprimé en chiffres et celui exprimé en toutes lettres, c'est le montant écrit en toutes lettres qui prévaut et fait foi pour l'évaluation et l'attribution de l'offre. »
            </div>

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

      {/* MODAL DE CÉLÉBRATION : PROFIL DE PROJET CRÉÉ AVEC SUCCÈS */}
      {createdProjectModal?.isOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl border border-slate-200 shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            {/* Modal Header */}
            <div className="bg-gradient-to-r from-emerald-600 to-teal-600 p-6 text-white text-center relative">
              <div className="w-14 h-14 rounded-2xl bg-white/20 text-white flex items-center justify-center mx-auto mb-3 shadow-inner">
                <CheckCircle2 className="w-8 h-8 text-white" />
              </div>
              <span className="px-3 py-1 rounded-full bg-white/20 text-emerald-100 text-[11px] font-black uppercase tracking-wider">
                Profil Créé & Activé avec Succès
              </span>
              <h3 className="text-lg font-black mt-2">
                Nouveau Projet Initialisé dans l'ERP
              </h3>
              <p className="text-xs text-emerald-100 mt-1">
                Le marché, le chantier, le Lot 01 et tous les articles du DQE ont été configurés automatiquement
              </p>
            </div>

            {/* Modal Body: Project Summary */}
            <div className="p-6 space-y-4 text-xs">
              <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200 space-y-2">
                <div className="flex justify-between items-start gap-2">
                  <div>
                    <span className="font-mono text-[11px] font-bold px-2 py-0.5 rounded bg-slate-900 text-white">
                      {createdProjectModal.number}
                    </span>
                    <h4 className="font-bold text-sm text-slate-900 mt-1">
                      {createdProjectModal.title}
                    </h4>
                  </div>
                  <span className="text-emerald-700 font-bold bg-emerald-100 px-2 py-0.5 rounded-full text-[10px]">
                    Projet Actif
                  </span>
                </div>

                <div className="pt-2 border-t border-slate-200 grid grid-cols-2 gap-2 text-slate-600">
                  <div>
                    <span className="text-[10px] text-slate-400 block">Maître d'Ouvrage</span>
                    <strong className="text-slate-800">{createdProjectModal.client}</strong>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Articles DQE Importés</span>
                    <strong className="text-blue-700 font-mono">{createdProjectModal.articlesCount} Articles</strong>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Montant Initial HT</span>
                    <strong className="text-slate-900 font-mono">{formatDA(createdProjectModal.totalHT)}</strong>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Montant Total TTC</span>
                    <strong className="text-emerald-700 font-mono font-black">{formatDA(createdProjectModal.totalTTC)}</strong>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    if (onNavigate) onNavigate('dashboard');
                  }}
                  className="px-4 py-3 bg-blue-600 hover:bg-blue-700 text-white font-black rounded-xl shadow-md flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <Building2 className="w-4 h-4" />
                  <span>Aller au Tableau de Bord</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    if (onNavigate) onNavigate('markets');
                  }}
                  className="px-4 py-3 bg-slate-900 hover:bg-slate-800 text-white font-black rounded-xl shadow-md flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <FileSpreadsheet className="w-4 h-4" />
                  <span>Consulter le DQE</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    if (onNavigate) onNavigate('chantiers');
                  }}
                  className="px-4 py-2.5 border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold rounded-xl flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <HardHat className="w-4 h-4 text-amber-500" />
                  <span>Voir la Fiche Chantier</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    setActiveSubTab('bpu-print');
                  }}
                  className="px-4 py-2.5 border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold rounded-xl flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <Printer className="w-4 h-4 text-blue-600" />
                  <span>Imprimer le BPU Rédigé</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
