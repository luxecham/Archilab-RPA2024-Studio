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
  FolderTree,
  MapPin,
  Check,
  ChevronDown,
  ChevronRight,
  PieChart,
  Table,
  ShieldCheck,
  Scale,
  ShieldAlert,
  FolderKanban
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ, numberToWordsDZ, ALGERIAN_WILAYAS } from '../utils/dzFormatters';
import { ActiveTab } from '../components/Sidebar';
import { Market, Lot, Section, Chantier, DQEArticle, GuaranteeModality } from '../types/btp';

interface IAMetreurViewProps {
  onNavigate?: (tab: ActiveTab) => void;
}

// Structure de données hiérarchique : LOT -> SOUS-LOT (SECTION) -> ARTICLES
interface DevisArticleDef {
  code: string;
  designation: string;
  unit: DQEArticle['unit'];
  quantity: number;
  baseCost: number;
}

interface DevisSectionDef {
  code: string;
  name: string;
  articles: DevisArticleDef[];
}

interface DevisLotDef {
  code: string;
  name: string;
  sections: DevisSectionDef[];
}

interface DevisQuotationDef {
  key: string;
  title: string;
  source: string;
  number: string;
  wilaya: string;
  commune: string;
  client: string;
  bet: string;
  durationMonths: number;
  tvaRate: 9 | 19;
  lots: DevisLotDef[];
}

interface ProcessedArticle {
  id: string;
  lotCode: string;
  lotName: string;
  sectionCode: string;
  sectionName: string;
  code: string;
  designation: string;
  unit: DQEArticle['unit'];
  quantity: number;
  unitPriceHT: number;
  unitPriceWords: string;
  totalLineHT: number;
  breakdown: {
    materiaux: number;
    mainOeuvre: number;
    materiel: number;
    fraisGeneraux: number;
    marge: number;
  };
}

interface ProcessedSection {
  code: string;
  name: string;
  articles: ProcessedArticle[];
  subtotalHT: number;
}

interface ProcessedLot {
  code: string;
  name: string;
  sections: ProcessedSection[];
  subtotalHT: number;
  pctOfTotal: number;
}

// MODÈLES OFFICIELS DE CAHIERS DES CHARGES ALGÉRIENS STRUCTURES PAR LOT & SOUS-LOT
const SAMPLE_QUOTATIONS: Record<string, DevisQuotationDef> = {
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
    tvaRate: 9,
    lots: [
      {
        code: 'LOT 01',
        name: 'TERRASSEMENTS & GROS ŒUVRES',
        sections: [
          {
            code: 'Sous-Lot 1.1',
            name: 'Fouilles & Terrassements',
            articles: [
              {
                code: '01.01',
                designation: 'Fouilles en puits et en rigoles dans terrain de toute nature sauf rocher pour fondations',
                unit: 'm³',
                quantity: 850,
                baseCost: 480
              },
              {
                code: '01.02',
                designation: 'Remblaiement des fouilles en tuf sélectionné compacté par couches successives de 20 cm',
                unit: 'm³',
                quantity: 320,
                baseCost: 650
              }
            ]
          },
          {
            code: 'Sous-Lot 1.2',
            name: 'Fondations & Infrastructure',
            articles: [
              {
                code: '01.03',
                designation: 'Béton de propreté sous semelles filantes et isolées dosé à 150 kg/m³ de ciment CPJ 42.5',
                unit: 'm³',
                quantity: 120,
                baseCost: 6500
              },
              {
                code: '01.04',
                designation: 'Béton armé en fondations pour semelles et longrines de liaison dosé à 350 kg/m³ de ciment',
                unit: 'm³',
                quantity: 420,
                baseCost: 12800
              }
            ]
          },
          {
            code: 'Sous-Lot 1.3',
            name: 'Superstructure & Dalles R+5',
            articles: [
              {
                code: '01.05',
                designation: 'Béton armé en élévation pour poteaux, voiles parasismiques et poutres dosé à 350 kg/m³',
                unit: 'm³',
                quantity: 580,
                baseCost: 17200
              },
              {
                code: '01.06',
                designation: 'Planchers à corps creux 16+4 y compris poutrelles préfabriquées et dalle de compression armée',
                unit: 'm²',
                quantity: 3200,
                baseCost: 2950
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 02',
        name: 'MAÇONNERIE & ENDUITS',
        sections: [
          {
            code: 'Sous-Lot 2.1',
            name: 'Maçonnerie & Cloisonnement',
            articles: [
              {
                code: '02.01',
                designation: 'Maçonnerie en briques creuses terre cuite 8 trous (10 cm) pour cloisons de distribution',
                unit: 'm²',
                quantity: 2800,
                baseCost: 1250
              },
              {
                code: '02.02',
                designation: 'Double cloison extérieure en briques creuses (15 cm + 10 cm) avec lame d\'air ventilée de 5 cm',
                unit: 'm²',
                quantity: 4500,
                baseCost: 1850
              }
            ]
          },
          {
            code: 'Sous-Lot 2.2',
            name: 'Enduits Intérieurs & Façades',
            articles: [
              {
                code: '02.03',
                designation: 'Enduit au mortier de ciment étanche intérieur lissé en deux couches (gobetis et corps d\'enduit)',
                unit: 'm²',
                quantity: 5400,
                baseCost: 780
              },
              {
                code: '02.04',
                designation: 'Enduit extérieur tyrolien teinté projeté mécaniquement avec baguettes d\'arrêt d\'angles',
                unit: 'm²',
                quantity: 4200,
                baseCost: 1100
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 03',
        name: 'REVÊTEMENTS SOLS & MURS',
        sections: [
          {
            code: 'Sous-Lot 3.1',
            name: 'Sols & Plinthes',
            articles: [
              {
                code: '03.01',
                designation: 'Revêtement de sol en carreaux de grès cérame émaillé premier choix 40x40 antidérapant',
                unit: 'm²',
                quantity: 3100,
                baseCost: 2850
              },
              {
                code: '03.02',
                designation: 'Plinthes assorties en grès cérame 7x40 posées au mortier de ciment dosé à 300 kg/m³',
                unit: 'ml',
                quantity: 1850,
                baseCost: 650
              }
            ]
          },
          {
            code: 'Sous-Lot 3.2',
            name: 'Faïence Murale',
            articles: [
              {
                code: '03.03',
                designation: 'Revêtement mural en faïence premier choix 20x30 pour cuisines, salles d\'eau et WC',
                unit: 'm²',
                quantity: 1600,
                baseCost: 2400
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 04',
        name: 'VRD & AMÉNAGEMENTS EXTÉRIEURS',
        sections: [
          {
            code: 'Sous-Lot 4.1',
            name: 'Assainissement & Eaux Pluviales',
            articles: [
              {
                code: '04.01',
                designation: 'Canalisations d\'assainissement en buses PVC compact série 1 classe CR8 Ø 315 mm',
                unit: 'ml',
                quantity: 340,
                baseCost: 4200
              },
              {
                code: '04.02',
                designation: 'Regards de visite en béton armé 80x80 cm sous chaussée avec tampon fonte série lourde 40T',
                unit: 'U',
                quantity: 14,
                baseCost: 21500
              },
              {
                code: '04.03',
                designation: 'Caniveaux préfabriqués d\'évacuation des eaux pluviales avec grille galvanisée caillebotis',
                unit: 'ml',
                quantity: 180,
                baseCost: 3100
              }
            ]
          }
        ]
      }
    ]
  },
  dep: {
    key: 'dep',
    title: 'Réalisation d\'une École Primaire Type 2 avec 12 Salles de Classe et Demi-Pension',
    source: 'Avis d\'Appel d\'Offres National Restreint N° 18/DEP/2026',
    number: '2026/18/DEP/SBA',
    wilaya: '22',
    commune: 'Sidi Bel Abbès (Rocade Nord)',
    client: 'Direction des Équipements Publics (DEP) Sidi Bel Abbès',
    bet: 'BET Archi-Structures Sidi Bel Abbès',
    durationMonths: 12,
    tvaRate: 9,
    lots: [
      {
        code: 'LOT 01',
        name: 'TERRASSEMENTS & INFRASTRUCTURE',
        sections: [
          {
            code: 'Sous-Lot 1.1',
            name: 'Terrassements Généraux',
            articles: [
              {
                code: '01.01',
                designation: 'Décapage de la terre végétale sur une épaisseur moyenne de 20 cm et mise en dépôt',
                unit: 'm²',
                quantity: 2400,
                baseCost: 180
              },
              {
                code: '01.02',
                designation: 'Fouilles en pleine masse pour sous-sol, blocs de classes et administration',
                unit: 'm³',
                quantity: 1450,
                baseCost: 520
              }
            ]
          },
          {
            code: 'Sous-Lot 1.2',
            name: 'Fondations & Gros Béton',
            articles: [
              {
                code: '01.03',
                designation: 'Gros béton de calage et semelles filantes dosé à 250 kg/m³ de ciment',
                unit: 'm³',
                quantity: 310,
                baseCost: 11400
              },
              {
                code: '01.04',
                designation: 'Béton armé pour semelles isolées, longrines et amorces poteaux dosé à 350 kg/m³',
                unit: 'm³',
                quantity: 480,
                baseCost: 14500
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 02',
        name: 'SUPERSTRUCTURE & MAÇONNERIE',
        sections: [
          {
            code: 'Sous-Lot 2.1',
            name: 'Ossature & Planchers Blocs Classes',
            articles: [
              {
                code: '02.01',
                designation: 'Béton armé en élévation pour poteaux, poutres et chaînages dosé à 350 kg/m³',
                unit: 'm³',
                quantity: 520,
                baseCost: 16900
              },
              {
                code: '02.02',
                designation: 'Planchers nervurés 16+4 pour 12 salles de classe et réfectoire y compris chape armée',
                unit: 'm²',
                quantity: 2100,
                baseCost: 2900
              }
            ]
          },
          {
            code: 'Sous-Lot 2.2',
            name: 'Maçonnerie & Enduits Spécifiques',
            articles: [
              {
                code: '02.03',
                designation: 'Murs extérieurs en double paroi isolée avec laine minérale pour confort thermique scolaire',
                unit: 'm²',
                quantity: 2600,
                baseCost: 2200
              },
              {
                code: '02.04',
                designation: 'Enduits au mortier bâtard ciment-chaux intérieur lissé et extérieur hydrofugé',
                unit: 'm²',
                quantity: 5800,
                baseCost: 820
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 03',
        name: 'ÉTANCHÉITÉ & REVÊTEMENTS',
        sections: [
          {
            code: 'Sous-Lot 3.1',
            name: 'Étanchéité Toiture Terrasse',
            articles: [
              {
                code: '03.01',
                designation: 'Forme de pente en béton maigre épaisseur moyenne 8 cm avec gorge de raccordement',
                unit: 'm²',
                quantity: 1650,
                baseCost: 950
              },
              {
                code: '03.02',
                designation: 'Étanchéité multicouche type Paxalu autoprotégée avec équerres et relevés d\'étanchéité',
                unit: 'm²',
                quantity: 1650,
                baseCost: 2600
              }
            ]
          },
          {
            code: 'Sous-Lot 3.2',
            name: 'Carrelage Salles de Classe',
            articles: [
              {
                code: '03.03',
                designation: 'Carrelage grès cérame pleine masse 40x40 haute résistance trafic scolaire intensif',
                unit: 'm²',
                quantity: 1850,
                baseCost: 2800
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 04',
        name: 'COUR DE RÉCRÉATION & CLÔTURE',
        sections: [
          {
            code: 'Sous-Lot 4.1',
            name: 'Clôture & Aménagements Extérieurs',
            articles: [
              {
                code: '04.01',
                designation: 'Mur de clôture d\'enceinte en maçonnerie avec chaperon béton et grille métallique décorative',
                unit: 'ml',
                quantity: 320,
                baseCost: 8500
              },
              {
                code: '04.02',
                designation: 'Aménagement de la cour de récréation en enrobé bitumineux 0/10 épaisseur 5 cm',
                unit: 'm²',
                quantity: 1800,
                baseCost: 1750
              }
            ]
          }
        ]
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
    tvaRate: 19,
    lots: [
      {
        code: 'LOT 01',
        name: 'TERRASSEMENTS GÉNÉRAUX & VOIRIE',
        sections: [
          {
            code: 'Sous-Lot 1.1',
            name: 'Corps de Chaussée',
            articles: [
              {
                code: '01.01',
                designation: 'Terrassement général en déblai et remblai compacté à 95% OPM pour plateformes de voirie',
                unit: 'm³',
                quantity: 3800,
                baseCost: 420
              },
              {
                code: '01.02',
                designation: 'Fourniture et mise en œuvre de couche de fondation en tout-venant d\'oued 0/40 épaisseur 20 cm',
                unit: 'm³',
                quantity: 2600,
                baseCost: 1800
              },
              {
                code: '01.03',
                designation: 'Couche de base en concassé 0/31.5 d\'épaisseur 15 cm compactée',
                unit: 'm³',
                quantity: 1900,
                baseCost: 2200
              }
            ]
          },
          {
            code: 'Sous-Lot 1.2',
            name: 'Revêtement Bitumineux',
            articles: [
              {
                code: '01.04',
                designation: 'Imprégnation au bitume fluidifié 0/1 dosé à 1.2 kg/m² avec sablage de protection',
                unit: 'm²',
                quantity: 8200,
                baseCost: 280
              },
              {
                code: '01.05',
                designation: 'Revêtement en enrobé bitumineux à chaud BBSG 0/14 épaisseur 6 cm cylindré au rouleau vibrant',
                unit: 'm²',
                quantity: 8200,
                baseCost: 1950
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 02',
        name: 'TROTTOIRS & BORDURES',
        sections: [
          {
            code: 'Sous-Lot 2.1',
            name: 'Bordures & Pavage Piéton',
            articles: [
              {
                code: '02.01',
                designation: 'Pose de bordures de trottoir type T2 en béton vibré y compris semelle béton de calage',
                unit: 'ml',
                quantity: 1650,
                baseCost: 1450
              },
              {
                code: '02.02',
                designation: 'Bordures d\'accotement et îlots directionnels type I2 chanfreinées',
                unit: 'ml',
                quantity: 750,
                baseCost: 1200
              },
              {
                code: '02.03',
                designation: 'Revêtement de trottoir en pavés autobloquants préfabriqués bicolores épaisseur 6 cm sur lit de sable',
                unit: 'm²',
                quantity: 3400,
                baseCost: 2100
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 03',
        name: 'ASSAINISSEMENT & EAUX PLUVIALES',
        sections: [
          {
            code: 'Sous-Lot 3.1',
            name: 'Collecteurs & Ouvrages de Visite',
            articles: [
              {
                code: '03.01',
                designation: 'Caniveaux trapézoïdaux préfabriqués type CS2 pour évacuation superficielle des eaux pluviales',
                unit: 'ml',
                quantity: 780,
                baseCost: 2900
              },
              {
                code: '03.02',
                designation: 'Collecteur principal en buses béton armé série 135A Ø 600 mm posées sur lit de sable',
                unit: 'ml',
                quantity: 450,
                baseCost: 7800
              },
              {
                code: '03.03',
                designation: 'Regards de visite avec têtes d\'avaloirs et grilles fonte série lourde 40T',
                unit: 'U',
                quantity: 28,
                baseCost: 28000
              }
            ]
          }
        ]
      },
      {
        code: 'LOT 04',
        name: 'ÉCLAIRAGE PUBLIC SOLAIRE',
        sections: [
          {
            code: 'Sous-Lot 4.1',
            name: 'Candélabres & Réseau',
            articles: [
              {
                code: '04.01',
                designation: 'Massifs béton de scellement pour candélabres avec tiges d\'ancrage galvanisées',
                unit: 'U',
                quantity: 45,
                baseCost: 14500
              },
              {
                code: '04.02',
                designation: 'Candélabres solaires autonomes hauteur 8m avec luminaire LED 120W et panneau photovoltaïque',
                unit: 'U',
                quantity: 45,
                baseCost: 85000
              }
            ]
          }
        ]
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
  const [selectedSampleKey, setSelectedSampleKey] = useState<string>('opgi');
  const [scannedDocumentName, setScannedDocumentName] = useState<string>('Devis_Vierge_OPGI_SBA_2026.pdf');
  const [isScanning, setIsScanning] = useState<boolean>(false);
  const [scanStep, setScanStep] = useState<number>(0);

  // Extracted Project Profile Metadata
  const [extractedTitle, setExtractedTitle] = useState<string>(SAMPLE_QUOTATIONS.opgi.title);
  const [extractedNumber, setExtractedNumber] = useState<string>(SAMPLE_QUOTATIONS.opgi.number);
  const [extractedClient, setExtractedClient] = useState<string>(SAMPLE_QUOTATIONS.opgi.client);
  const [extractedWilaya, setExtractedWilaya] = useState<string>(SAMPLE_QUOTATIONS.opgi.wilaya);
  const [extractedCommune, setExtractedCommune] = useState<string>(SAMPLE_QUOTATIONS.opgi.commune);
  const [extractedBet, setExtractedBet] = useState<string>(SAMPLE_QUOTATIONS.opgi.bet);
  const [extractedDuration, setExtractedDuration] = useState<number>(SAMPLE_QUOTATIONS.opgi.durationMonths);

  // Global AI Parameters
  const [marginPct, setMarginPct] = useState<number>(15);
  const [fraisGenerauxPct, setFraisGenerauxPct] = useState<number>(12);
  const [wilayaCoeff, setWilayaCoeff] = useState<number>(1.0);
  const [tvaRate, setTvaRate] = useState<9 | 19>(9);

  // Guarantee Modality selection (Caution de Bonne Exécution vs Retenue de Garantie)
  const [guaranteeModality, setGuaranteeModality] = useState<GuaranteeModality>('caution_bonne_execution');
  const [customBankName, setCustomBankName] = useState<string>('BNA Agence 412 Sidi Bel Abbès');

  // Collapsible Lot Sections in UI
  const [expandedLots, setExpandedLots] = useState<Record<string, boolean>>({
    'LOT 01': true,
    'LOT 02': true,
    'LOT 03': true,
    'LOT 04': true
  });

  const toggleLot = (lotCode: string) => {
    setExpandedLots(prev => ({ ...prev, [lotCode]: !prev[lotCode] }));
  };

  // State of structured processed lots
  const [currentQuotationDef, setCurrentQuotationDef] = useState<DevisQuotationDef>(SAMPLE_QUOTATIONS.opgi);

  // Compute processed lots with unit prices in numbers and words, subtotals, and breakdowns
  function computeProcessedLots(
    quotation: DevisQuotationDef, 
    margin: number, 
    fg: number, 
    coeff: number
  ): ProcessedLot[] {
    let rawTotalHT = 0;

    // First pass to compute articles and sections
    const preLots = quotation.lots.map(lotDef => {
      let lotSubtotal = 0;

      const processedSections: ProcessedSection[] = lotDef.sections.map(secDef => {
        let secSubtotal = 0;

        const processedArticles: ProcessedArticle[] = secDef.articles.map((art, idx) => {
          const rawCost = art.baseCost * coeff;
          const mat = Math.round(rawCost * 0.55);
          const mo = Math.round(rawCost * 0.30);
          const eq = Math.round(rawCost * 0.15);
          const ds = mat + mo + eq;
          const fraisGen = Math.round(ds * (fg / 100));
          const prixRevient = ds + fraisGen;
          const marge = Math.round(prixRevient * (margin / 100));
          const finalPrice = Math.round((prixRevient + marge) / 10) * 10;
          const totalLineHT = art.quantity * finalPrice;

          secSubtotal += totalLineHT;

          return {
            id: `art-${lotDef.code}-${secDef.code}-${idx + 1}`,
            lotCode: lotDef.code,
            lotName: lotDef.name,
            sectionCode: secDef.code,
            sectionName: secDef.name,
            code: art.code,
            designation: art.designation,
            unit: art.unit,
            quantity: art.quantity,
            unitPriceHT: finalPrice,
            unitPriceWords: numberToWordsDZ(finalPrice),
            totalLineHT,
            breakdown: {
              materiaux: mat,
              mainOeuvre: mo,
              materiel: eq,
              fraisGeneraux: fraisGen,
              marge
            }
          };
        });

        lotSubtotal += secSubtotal;

        return {
          code: secDef.code,
          name: secDef.name,
          articles: processedArticles,
          subtotalHT: secSubtotal
        };
      });

      rawTotalHT += lotSubtotal;

      return {
        code: lotDef.code,
        name: lotDef.name,
        sections: processedSections,
        subtotalHT: lotSubtotal,
        pctOfTotal: 0
      };
    });

    // Second pass to calculate percentage of total
    return preLots.map(l => ({
      ...l,
      pctOfTotal: rawTotalHT > 0 ? Math.round((l.subtotalHT / rawTotalHT) * 100) : 0
    }));
  }

  const processedLots = computeProcessedLots(currentQuotationDef, marginPct, fraisGenerauxPct, wilayaCoeff);

  // Financial Grand Totals
  const totalBpuHT = processedLots.reduce((acc, lot) => acc + lot.subtotalHT, 0);
  const totalTva = totalBpuHT * (tvaRate / 100);
  const totalBpuTTC = totalBpuHT + totalTva;
  const totalBpuTTCWords = numberToWordsDZ(totalBpuTTC);

  // Flat list of all articles for easy counting or injection
  const allArticles: ProcessedArticle[] = processedLots.flatMap(l => l.sections.flatMap(s => s.articles));

  // Modal and feedback states
  const [createdProjectModal, setCreatedProjectModal] = useState<{
    isOpen: boolean;
    marketId: string;
    title: string;
    client: string;
    number: string;
    totalHT: number;
    totalTTC: number;
    lotsCount: number;
    sectionsCount: number;
    articlesCount: number;
    guaranteeModality: GuaranteeModality;
    cautionAmount: number;
    retenueRate: number;
  } | null>(null);

  const [injectSuccess, setInjectSuccess] = useState<boolean>(false);
  const [injectedCount, setInjectedCount] = useState<number>(0);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Scan simulation
  const triggerScanProcess = (quotationKey: string = selectedSampleKey) => {
    setIsScanning(true);
    setScanStep(1);

    const qDef = SAMPLE_QUOTATIONS[quotationKey] || SAMPLE_QUOTATIONS.opgi;
    setCurrentQuotationDef(qDef);
    setExtractedTitle(qDef.title);
    setExtractedNumber(qDef.number);
    setExtractedClient(qDef.client);
    setExtractedWilaya(qDef.wilaya);
    setExtractedCommune(qDef.commune);
    setExtractedBet(qDef.bet);
    setExtractedDuration(qDef.durationMonths);
    setTvaRate(qDef.tvaRate);

    setTimeout(() => setScanStep(2), 500);
    setTimeout(() => setScanStep(3), 1000);
    setTimeout(() => setScanStep(4), 1500);
    setTimeout(() => {
      setScanStep(5);
      setIsScanning(false);
    }, 2000);
  };

  // Traitement d'un upload personnalisé (PDF / Scan)
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setScannedDocumentName(file.name);
      
      const cleanName = file.name
        .replace(/\.[^/.]+$/, '')
        .replace(/[_-]/g, ' ')
        .replace(/\b\w/g, l => l.toUpperCase());

      // On crée dynamiquement une quotation structurée complète avec le nom du fichier
      const customDef: DevisQuotationDef = {
        key: 'custom',
        title: `Projet de Réalisation — ${cleanName}`,
        source: `Appel d'Offres National N° ${Math.floor(10 + Math.random() * 89)}/2026`,
        number: `2026/${Math.floor(10 + Math.random() * 89)}/DEP`,
        wilaya: '22',
        commune: 'Sidi Bel Abbès',
        client: 'Direction des Équipements Publics (DEP)',
        bet: 'BET Architecture & Ingénierie DZ',
        durationMonths: 14,
        tvaRate: 9,
        lots: SAMPLE_QUOTATIONS.dep.lots
      };

      setCurrentQuotationDef(customDef);
      setExtractedTitle(customDef.title);
      setExtractedNumber(customDef.number);
      setExtractedClient(customDef.client);
      setExtractedWilaya(customDef.wilaya);
      setExtractedCommune(customDef.commune);
      setExtractedBet(customDef.bet);
      setExtractedDuration(customDef.durationMonths);
      setTvaRate(customDef.tvaRate);

      triggerScanProcess('dep');
    }
  };

  // ACTION CLÉ : CRÉER LE NOUVEAU PROJET AVEC SES LOTS, SOUS-LOTS ET ARTICLES STRUCTURÉS
  const handleCreateProjectProfileFromDevis = () => {
    const newMarketId = `m-${Date.now()}`;
    const today = new Date().toISOString().split('T')[0];

    const chosenRetenueRate: 0 | 5 | 10 = 
      (guaranteeModality === 'retenue_garantie' || guaranteeModality === 'both') ? 5 : 0;
    const chosenCautionAmount = 
      (guaranteeModality === 'caution_bonne_execution' || guaranteeModality === 'both') 
        ? Math.round(totalBpuHT * 0.05) 
        : 0;

    // 1. Création de l'Entité Marché
    const newMarket: Market = {
      id: newMarketId,
      type: 'Marché Public',
      number: extractedNumber.trim() || `2026/${Math.floor(10 + Math.random() * 89)}/AO`,
      title: extractedTitle.trim() || 'Projet BTP Structuré par IA',
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
      retenueGarantieRate: chosenRetenueRate,
      guaranteeModality: guaranteeModality,
      avanceForfaitaireRate: 15,
      avanceApproRate: 20,
      initialAmountHT: totalBpuHT,
      currentAmountHT: totalBpuHT,
      bankGuarantees: {
        soumission: Math.round(totalBpuHT * 0.01),
        bonneExecution: chosenCautionAmount,
        restitutionAvance: Math.round(totalBpuHT * 0.15),
        bankName: customBankName.trim() || 'BNA Agence Centrale',
        expiryDate: '2027-12-31'
      }
    };

    addMarket(newMarket);

    let createdLotsCount = 0;
    let createdSectionsCount = 0;
    let createdArticlesCount = 0;

    // 2. Création de chaque LOT, de chaque SOUS-LOT (SECTION) et de chaque ARTICLE
    processedLots.forEach((lotItem, lotIdx) => {
      const newLotId = `lot-${Date.now()}-${lotIdx + 1}`;
      createdLotsCount++;

      const newLot: Lot = {
        id: newLotId,
        marketId: newMarketId,
        code: lotItem.code,
        name: lotItem.name
      };

      addLot(newLot);

      // Création du chantier rattaché au Lot 01
      if (lotIdx === 0) {
        const newChantier: Chantier = {
          id: `ch-${Date.now()}`,
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
        addChantier(newChantier);
      }

      // Parcourir chaque sous-lot (section)
      lotItem.sections.forEach((secItem, secIdx) => {
        const newSectionId = `sec-${Date.now()}-${lotIdx + 1}-${secIdx + 1}`;
        createdSectionsCount++;

        const newSection: Section = {
          id: newSectionId,
          lotId: newLotId,
          code: secItem.code,
          name: secItem.name
        };

        addSection(newSection);

        // Parcourir chaque article de ce sous-lot
        secItem.articles.forEach((artItem, artIdx) => {
          createdArticlesCount++;
          const newArticle: DQEArticle = {
            id: `art-ai-${Date.now()}-${lotIdx + 1}-${secIdx + 1}-${artIdx + 1}`,
            marketId: newMarketId,
            lotId: newLotId,
            sectionId: newSectionId,
            code: artItem.code,
            designation: artItem.designation,
            unit: artItem.unit,
            initialQuantity: artItem.quantity,
            avenantQuantityDelta: 0,
            currentContractQuantity: artItem.quantity,
            unitPriceHT: artItem.unitPriceHT,
            previousExecutedQty: 0,
            totalExecutedQty: 0
          };

          addArticle(newArticle);
        });
      });
    });

    // 3. Activer immédiatement le projet dans tout l'ERP
    setSelectedMarketId(newMarketId);

    // 4. Ouvrir la modale récapitulative
    setCreatedProjectModal({
      isOpen: true,
      marketId: newMarketId,
      title: newMarket.title,
      client: newMarket.client,
      number: newMarket.number,
      totalHT: totalBpuHT,
      totalTTC: totalBpuTTC,
      lotsCount: createdLotsCount,
      sectionsCount: createdSectionsCount,
      articlesCount: createdArticlesCount,
      guaranteeModality: guaranteeModality,
      cautionAmount: chosenCautionAmount,
      retenueRate: chosenRetenueRate
    });
  };

  // ACTION SECONDAIRE : Injection dans le Marché Actif existant
  const handleInjectIntoCurrentMarket = () => {
    allArticles.forEach((item, idx) => {
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

    setInjectedCount(allArticles.length);
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
              <FolderTree className="w-4 h-4" />
            </span>
            <span className="text-xs font-black uppercase tracking-wider text-blue-700">
              Module IA Métreur & Structuration DQE (Lot / Sous-Lot / Articles)
            </span>
            <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-emerald-500 text-white">
              Arborescence BTP
            </span>
          </div>
          <h1 className="text-lg sm:text-xl font-black text-slate-900 mt-1">
            Analyse IA de Devis & Génération de Projet Classé par Lots
          </h1>
          <p className="text-xs text-slate-500 mt-0.5">
            L'IA numérise le devis, rédige les désignations, estime les prix selon la mercuriale algérienne et structure l'offre par Lot, Sous-Lot avec sous-total de chaque lot
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
            Arborescence Lots & Devis
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
              <strong className="font-black block">Articles injectés avec succès !</strong>
              <span>
                {injectedCount} articles ont été ajoutés au DQE du marché {currentMarket.title}.
              </span>
            </div>
          </div>
          <span className="text-[11px] font-mono font-bold px-2.5 py-1 bg-emerald-600 text-white rounded-lg">
            DQE Synchronisé
          </span>
        </div>
      )}

      {/* ======================================================== */}
      {/* SUB-TAB 1 : SCANNER IA & ARBORESCENCE PAR LOT / SOUS-LOT */}
      {/* ======================================================== */}
      {activeSubTab === 'scanner' && (
        <div className="space-y-6">
          {/* ZONE 1 : INTERFACE DE NUMÉRISATION DU DEVIS */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
              <div>
                <h2 className="text-sm font-black text-slate-900 flex items-center gap-2">
                  <Camera className="w-4 h-4 text-blue-600" />
                  1. Téléversement du Devis ou Choix d'un Modèle Officiel
                </h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Glissez votre devis PDF/scan ou testez l'un des cahiers des charges prédéfinis
                </p>
              </div>

              {/* Sample Pre-loaded Documents */}
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-[11px] font-bold text-slate-400 hidden sm:inline">Exemples :</span>
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
                  DEP (École Primaire)
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
              {/* Drag & Drop */}
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
                    Détection automatique des lots, sous-lots & désignations
                  </span>
                </div>
                <span className="text-[10px] font-bold px-2.5 py-0.5 rounded-full bg-blue-100 text-blue-700 font-mono">
                  {scannedDocumentName}
                </span>
              </div>

              {/* HUD Scan Box */}
              <div className="lg:col-span-2 relative bg-slate-900 rounded-2xl p-4 overflow-hidden border border-slate-800 shadow-md">
                {isScanning && (
                  <div className="absolute inset-x-0 h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_15px_#22d3ee] animate-pulse z-20 top-0 transition-all duration-1000" />
                )}

                <div className="bg-white rounded-xl p-4 text-[10px] font-mono text-slate-800 shadow-inner select-none relative opacity-95 space-y-2">
                  <div className="border-b border-slate-300 pb-2 flex justify-between items-center text-[9px] text-slate-500">
                    <span className="font-bold uppercase tracking-wider text-slate-800 truncate max-w-sm">
                      DÉCOMPOSITION DU BORDEREAU DQE • {extractedClient}
                    </span>
                    <span className="bg-emerald-100 text-emerald-800 px-1.5 py-0.5 rounded font-bold">
                      {processedLots.length} LOTS DÉTECTÉS
                    </span>
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                    {processedLots.map(lot => (
                      <div key={lot.code} className="p-2 rounded-lg bg-slate-50 border border-slate-200">
                        <span className="font-bold text-blue-800 block text-[9px]">{lot.code}</span>
                        <strong className="text-slate-900 truncate block text-[10px]">{lot.name}</strong>
                        <span className="text-[9px] font-mono text-emerald-700 font-bold block mt-0.5">
                          {formatDA(lot.subtotalHT)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Scan Status HUD */}
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs">
                  <div className="flex items-center gap-2">
                    {isScanning ? (
                      <div className="flex items-center gap-2 text-cyan-400 font-bold">
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Étape {scanStep}/5 : {
                          scanStep === 1 ? 'Prétraitement du devis & OCR...' :
                          scanStep === 2 ? 'Détection des Lots & Chapitres...' :
                          scanStep === 3 ? 'Extraction des sous-lots et articles...' :
                          scanStep === 4 ? 'Calcul des prix selon mercuriale Algérie...' :
                          'Rédaction des désignations et sous-totaux par lot !'
                        }</span>
                      </div>
                    ) : (
                      <div className="flex items-center gap-1.5 text-emerald-400 font-bold">
                        <CheckCircle2 className="w-4 h-4" />
                        <span>Devis numérisé et classé par Lot, Sous-Lot et Articles avec succès</span>
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

          {/* ZONE 2 : PANNEAU VEDETTE — PROPOSITION DU NOUVEAU PROJET */}
          <div className="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 rounded-2xl p-6 text-white border border-blue-500/40 shadow-lg space-y-5">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 border-b border-white/10 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <span className="p-1 rounded-md bg-amber-400 text-slate-950 font-black text-xs">
                    PROJET IA PROPOSÉ
                  </span>
                  <span className="text-xs uppercase font-bold tracking-wider text-blue-200">
                    {processedLots.length} Lots • {processedLots.reduce((acc, l) => acc + l.sections.length, 0)} Sous-Lots • {allArticles.length} Articles
                  </span>
                </div>
                <h3 className="text-base sm:text-lg font-black mt-1 text-white">
                  {extractedTitle}
                </h3>
                <p className="text-xs text-blue-200 mt-0.5">
                  Maître d'Ouvrage : <strong className="text-white">{extractedClient}</strong> • Marché N° : <strong className="text-amber-300 font-mono">{extractedNumber}</strong> • Wilaya : <strong className="text-white">{getWilayaName(extractedWilaya)}</strong>
                </p>
              </div>

              {/* ACTION CLÉ 1-CLIC : CRÉER LE NOUVEAU PROJET */}
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
                <button
                  type="button"
                  onClick={handleCreateProjectProfileFromDevis}
                  className="px-5 py-3 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-white text-xs font-black shadow-lg shadow-emerald-900/30 flex items-center justify-center gap-2 cursor-pointer transition-all transform hover:scale-[1.02]"
                >
                  <Sparkles className="w-4 h-4 text-amber-200" />
                  <span>Créer le Projet & Tous les Lots dans l'ERP</span>
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

            {/* Quick Edit Fields */}
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

          {/* ZONE 2.B : PROPOSITION IA DE GARANTIE FINANCIÈRE (RETENUE OU CAUTION) */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-emerald-50 text-emerald-700">
                  <ShieldCheck className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-black text-slate-900 flex items-center gap-2">
                    Proposition IA : Modalité de Garantie Contractuelle
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                      Code des Marchés Publics DZ
                    </span>
                  </h3>
                  <p className="text-xs text-slate-500">
                    Décret Présidentiel N° 15-247 (Art. 130 à 135) : Choisissez la modalité de garantie à appliquer pour ce projet
                  </p>
                </div>
              </div>

              <div className="text-right">
                <span className="text-[10px] text-slate-400 uppercase font-bold block">Base Garantie (5% HT)</span>
                <strong className="text-xs font-mono font-black text-emerald-700">
                  {formatDA(Math.round(totalBpuHT * 0.05))}
                </strong>
              </div>
            </div>

            {/* 4 Interactive Modality Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
              {/* Option 1: Caution de Bonne Exécution */}
              <div 
                onClick={() => setGuaranteeModality('caution_bonne_execution')}
                className={`p-3.5 rounded-xl border cursor-pointer transition-all space-y-2 relative ${
                  guaranteeModality === 'caution_bonne_execution'
                    ? 'bg-blue-50/60 border-blue-500 shadow-xs ring-2 ring-blue-500/20'
                    : 'bg-white border-slate-200 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="p-1.5 rounded-lg bg-blue-100 text-blue-700">
                    <ShieldCheck className="w-4 h-4" />
                  </span>
                  {guaranteeModality === 'caution_bonne_execution' && (
                    <span className="text-[10px] font-black px-2 py-0.5 rounded bg-blue-600 text-white flex items-center gap-0.5">
                      <Check className="w-3 h-3" /> Recommandé
                    </span>
                  )}
                </div>
                <div>
                  <strong className="text-slate-900 font-bold block">Caution de Bonne Exécution (5%)</strong>
                  <span className="text-[11px] text-slate-500 block mt-0.5">
                    Lettre de garantie bancaire déposée. <strong>Aucune retenue (0%)</strong> sur les décomptes mensuels (trésorerie 100% préservée).
                  </span>
                </div>
                <div className="pt-2 border-t border-slate-100 flex justify-between items-center text-[11px] font-mono">
                  <span className="text-slate-400">Montant Caution :</span>
                  <strong className="text-blue-700 font-bold">{formatDA(Math.round(totalBpuHT * 0.05))}</strong>
                </div>
              </div>

              {/* Option 2: Retenue de Garantie */}
              <div 
                onClick={() => setGuaranteeModality('retenue_garantie')}
                className={`p-3.5 rounded-xl border cursor-pointer transition-all space-y-2 relative ${
                  guaranteeModality === 'retenue_garantie'
                    ? 'bg-amber-50/60 border-amber-500 shadow-xs ring-2 ring-amber-500/20'
                    : 'bg-white border-slate-200 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="p-1.5 rounded-lg bg-amber-100 text-amber-800">
                    <Scale className="w-4 h-4" />
                  </span>
                  {guaranteeModality === 'retenue_garantie' && (
                    <span className="text-[10px] font-black px-2 py-0.5 rounded bg-amber-500 text-slate-950 flex items-center gap-0.5">
                      <Check className="w-3 h-3" /> Choisi
                    </span>
                  )}
                </div>
                <div>
                  <strong className="text-slate-900 font-bold block">Retenue de Garantie (5%)</strong>
                  <span className="text-[11px] text-slate-500 block mt-0.5">
                    Prélèvement automatique de <strong>5% sur chaque décompte mensuel</strong> consigné au Trésor Public jusqu'à la Réception Définitive (1 an).
                  </span>
                </div>
                <div className="pt-2 border-t border-slate-100 flex justify-between items-center text-[11px] font-mono">
                  <span className="text-slate-400">Total consigné :</span>
                  <strong className="text-amber-700 font-bold">{formatDA(Math.round(totalBpuHT * 0.05))}</strong>
                </div>
              </div>

              {/* Option 3: Double Garantie */}
              <div 
                onClick={() => setGuaranteeModality('both')}
                className={`p-3.5 rounded-xl border cursor-pointer transition-all space-y-2 relative ${
                  guaranteeModality === 'both'
                    ? 'bg-indigo-50/60 border-indigo-500 shadow-xs ring-2 ring-indigo-500/20'
                    : 'bg-white border-slate-200 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="p-1.5 rounded-lg bg-indigo-100 text-indigo-700">
                    <Building2 className="w-4 h-4" />
                  </span>
                  {guaranteeModality === 'both' && (
                    <span className="text-[10px] font-black px-2 py-0.5 rounded bg-indigo-600 text-white flex items-center gap-0.5">
                      <Check className="w-3 h-3" /> Choisi
                    </span>
                  )}
                </div>
                <div>
                  <strong className="text-slate-900 font-bold block">Double Garantie (Caution + Retenue)</strong>
                  <span className="text-[11px] text-slate-500 block mt-0.5">
                    Caution bancaire 5% + Retenue de 5% sur chaque décompte pour ouvrages complexes ou sensibles.
                  </span>
                </div>
                <div className="pt-2 border-t border-slate-100 flex justify-between items-center text-[11px] font-mono">
                  <span className="text-slate-400">Total combiné :</span>
                  <strong className="text-indigo-700 font-bold">{formatDA(Math.round(totalBpuHT * 0.10))}</strong>
                </div>
              </div>

              {/* Option 4: Sans retenue ni caution */}
              <div 
                onClick={() => setGuaranteeModality('none')}
                className={`p-3.5 rounded-xl border cursor-pointer transition-all space-y-2 relative ${
                  guaranteeModality === 'none'
                    ? 'bg-slate-100 border-slate-500 shadow-xs ring-2 ring-slate-500/20'
                    : 'bg-white border-slate-200 hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="p-1.5 rounded-lg bg-slate-100 text-slate-700">
                    <ShieldAlert className="w-4 h-4" />
                  </span>
                  {guaranteeModality === 'none' && (
                    <span className="text-[10px] font-black px-2 py-0.5 rounded bg-slate-900 text-white flex items-center gap-0.5">
                      <Check className="w-3 h-3" /> Choisi
                    </span>
                  )}
                </div>
                <div>
                  <strong className="text-slate-900 font-bold block">Sans Retenue ni Caution (0%)</strong>
                  <span className="text-[11px] text-slate-500 block mt-0.5">
                    Conventions simples de gré à gré, bons de commande ou prestations sans consignation.
                  </span>
                </div>
                <div className="pt-2 border-t border-slate-100 flex justify-between items-center text-[11px] font-mono">
                  <span className="text-slate-400">Garantie :</span>
                  <strong className="text-slate-600 font-bold">0 DA</strong>
                </div>
              </div>
            </div>

            {/* Custom Bank input if Caution is chosen */}
            {(guaranteeModality === 'caution_bonne_execution' || guaranteeModality === 'both') && (
              <div className="p-3 bg-blue-50/50 rounded-xl border border-blue-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="flex items-center gap-2">
                  <Landmark className="w-4 h-4 text-blue-700 shrink-0" />
                  <div>
                    <strong className="text-blue-900 block">Banque Émettrice de la Caution de Bonne Exécution</strong>
                    <span className="text-[11px] text-blue-700">Agence bancaire agréée délivrant l'acte de caution</span>
                  </div>
                </div>
                <input
                  type="text"
                  value={customBankName}
                  onChange={(e) => setCustomBankName(e.target.value)}
                  placeholder="ex: BNA Agence 412 Sidi Bel Abbès"
                  className="w-full sm:w-80 px-3 py-1.5 bg-white border border-blue-300 rounded-lg text-slate-900 font-bold text-xs focus:ring-2 focus:ring-blue-500 focus:outline-none"
                />
              </div>
            )}
          </div>

          {/* ZONE 3 : TABLEAU RÉCAPITULATIF PAR LOT (SOUS-TOTAL DE CHAQUE LOT) */}
          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-blue-50 text-blue-700">
                  <Table className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Bordereau Récapitulatif : Sous-Total de Chaque Lot
                  </h3>
                  <p className="text-xs text-slate-500">
                    Synthèse financière contractuelle de l'offre par corps d'état
                  </p>
                </div>
              </div>

              <span className="text-xs font-bold px-3 py-1 bg-slate-100 text-slate-700 rounded-full font-mono">
                {processedLots.length} Lots Contractuels
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-100 text-[10px] font-black uppercase text-slate-700 border-b border-slate-200">
                    <th className="py-2.5 px-3 w-20">Code Lot</th>
                    <th className="py-2.5 px-3">Désignation du Lot (Corps d'État)</th>
                    <th className="py-2.5 px-3 text-center w-24">Sous-Lots</th>
                    <th className="py-2.5 px-3 text-center w-24">Articles</th>
                    <th className="py-2.5 px-3 text-right w-44">Sous-Total HT (DA)</th>
                    <th className="py-2.5 px-3 text-right w-28">Part (%)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {processedLots.map(lot => (
                    <tr key={lot.code} className="hover:bg-slate-50 transition-colors">
                      <td className="py-3 px-3 font-mono font-black text-blue-700">
                        {lot.code}
                      </td>
                      <td className="py-3 px-3 font-bold text-slate-900">
                        {lot.name}
                      </td>
                      <td className="py-3 px-3 text-center font-mono text-slate-600">
                        {lot.sections.length}
                      </td>
                      <td className="py-3 px-3 text-center font-mono text-slate-600">
                        {lot.sections.reduce((acc, s) => acc + s.articles.length, 0)}
                      </td>
                      <td className="py-3 px-3 text-right font-mono font-black text-slate-900 text-sm">
                        {formatDA(lot.subtotalHT)}
                      </td>
                      <td className="py-3 px-3 text-right">
                        <span className="font-mono font-bold px-2 py-0.5 rounded-full bg-blue-100 text-blue-800 text-[11px]">
                          {lot.pctOfTotal} %
                        </span>
                      </td>
                    </tr>
                  ))}
                  <tr className="bg-slate-900 text-white font-black text-sm">
                    <td colSpan={4} className="py-3.5 px-4 uppercase tracking-wider text-xs">
                      Total Général de l'Offre HT
                    </td>
                    <td className="py-3.5 px-3 text-right font-mono text-base text-amber-300">
                      {formatDA(totalBpuHT)}
                    </td>
                    <td className="py-3.5 px-3 text-right font-mono text-xs">
                      100 %
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          {/* ZONE 4 : ARBORESCENCE DÉTAILLÉE : LOT -> SOUS-LOT -> ARTICLES AVEC SOUS-TOTAUX */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-black text-slate-900 flex items-center gap-2">
                <FolderTree className="w-4 h-4 text-blue-600" />
                Détail Complet du Devis (Articles & Sous-Totaux par Lot)
              </h3>
              <span className="text-xs text-slate-500">
                Rédigé et chiffré par l'IA en chiffres et en toutes lettres
              </span>
            </div>

            {processedLots.map(lot => {
              const isExpanded = expandedLots[lot.code] !== false;

              return (
                <div key={lot.code} className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
                  {/* LOT HEADER WITH SUB-TOTAL */}
                  <div 
                    onClick={() => toggleLot(lot.code)}
                    className="bg-slate-900 hover:bg-slate-800 text-white p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 cursor-pointer transition-colors select-none"
                  >
                    <div className="flex items-center gap-3">
                      <button className="text-slate-400 hover:text-white">
                        {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                      </button>
                      <span className="font-mono font-black text-xs px-2.5 py-1 rounded-md bg-blue-600 text-white">
                        {lot.code}
                      </span>
                      <div>
                        <h4 className="font-bold text-sm text-white">{lot.name}</h4>
                        <span className="text-[11px] text-slate-400">
                          {lot.sections.length} sous-lots • {lot.sections.reduce((acc, s) => acc + s.articles.length, 0)} articles
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-4 self-end sm:self-auto font-mono">
                      <div className="text-right">
                        <span className="text-[10px] text-slate-400 uppercase tracking-wider block">
                          Sous-Total HT du {lot.code}
                        </span>
                        <strong className="text-base font-black text-amber-300">
                          {formatDA(lot.subtotalHT)}
                        </strong>
                      </div>
                      <span className="text-xs px-2 py-0.5 rounded-md bg-white/10 text-white font-bold">
                        {lot.pctOfTotal}%
                      </span>
                    </div>
                  </div>

                  {/* SECTIONS & ARTICLES INSIDE THIS LOT */}
                  {isExpanded && (
                    <div className="p-4 space-y-4">
                      {lot.sections.map(section => (
                        <div key={section.code} className="rounded-xl border border-slate-200 overflow-hidden bg-slate-50/20">
                          {/* SOUS-LOT HEADER */}
                          <div className="bg-slate-100/90 px-4 py-2 border-b border-slate-200 flex items-center justify-between text-xs">
                            <span className="font-bold text-slate-800 flex items-center gap-2">
                              <Layers className="w-3.5 h-3.5 text-blue-600" />
                              <strong className="font-mono text-blue-800">{section.code} :</strong> {section.name}
                            </span>
                            <span className="font-mono font-bold text-slate-700 text-[11px]">
                              Sous-total : {formatDA(section.subtotalHT)}
                            </span>
                          </div>

                          {/* ARTICLES TABLE */}
                          <div className="overflow-x-auto bg-white">
                            <table className="w-full text-left border-collapse text-xs">
                              <thead>
                                <tr className="bg-slate-50 border-b border-slate-100 text-[10px] font-bold uppercase text-slate-500">
                                  <th className="py-2.5 px-3 w-16">Code</th>
                                  <th className="py-2.5 px-3">Désignation Rédigée par l'IA</th>
                                  <th className="py-2.5 px-2 text-center w-12">U</th>
                                  <th className="py-2.5 px-3 text-right w-20">Qté</th>
                                  <th className="py-2.5 px-3 text-right w-32 bg-blue-50/30">P.U Chiffres</th>
                                  <th className="py-2.5 px-3 bg-amber-50/40 text-amber-950">Prix en Toutes Lettres</th>
                                  <th className="py-2.5 px-3 text-right w-32 font-bold">Total Ligne HT</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-slate-100">
                                {section.articles.map(art => (
                                  <tr key={art.id} className="hover:bg-slate-50 transition-colors">
                                    <td className="py-2.5 px-3 font-mono font-bold text-blue-700">
                                      {art.code}
                                    </td>
                                    <td className="py-2.5 px-3 font-medium text-slate-900 max-w-sm">
                                      {art.designation}
                                    </td>
                                    <td className="py-2.5 px-2 text-center font-mono font-bold text-slate-600 bg-slate-50/50">
                                      {art.unit}
                                    </td>
                                    <td className="py-2.5 px-3 text-right font-mono font-semibold text-slate-700">
                                      {formatNumberDZ(art.quantity)}
                                    </td>
                                    <td className="py-2.5 px-3 text-right font-mono font-black text-slate-900 bg-blue-50/20">
                                      {formatDA(art.unitPriceHT)}
                                    </td>
                                    <td className="py-2.5 px-3 font-serif italic text-amber-950 text-[11px] bg-amber-50/20 uppercase font-semibold">
                                      « {art.unitPriceWords} »
                                    </td>
                                    <td className="py-2.5 px-3 text-right font-mono font-black text-slate-900">
                                      {formatDA(art.totalLineHT)}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>
                        </div>
                      ))}

                      {/* LOT RECAP BAR AT THE END OF THE LOT */}
                      <div className="bg-slate-100 p-3 rounded-xl flex items-center justify-between text-xs font-mono">
                        <span className="font-bold text-slate-700 uppercase tracking-wide">
                          Sous-Total HT Cumulé du {lot.code} ({lot.name}) :
                        </span>
                        <strong className="text-sm font-black text-slate-900">
                          {formatDA(lot.subtotalHT)}
                        </strong>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* ZONE 5 : TOTAUX GÉNÉRAUX DU MARCHÉ */}
          <div className="bg-slate-900 text-white rounded-2xl p-6 flex flex-col md:flex-row items-center justify-between gap-4 shadow-xl">
            <div className="space-y-1 text-center md:text-left">
              <span className="text-xs text-slate-400 font-bold uppercase tracking-wider block">
                Arrêté Total de l'Offre Financière DQE / BPU :
              </span>
              <p className="text-xs font-serif italic text-amber-300 font-bold uppercase max-w-xl">
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

          {/* BANDEAU D'INJECTION DU PROJET AVEC TOUS LES ARTICLES DANS ARCHILAB MANAGER */}
          <div className="bg-gradient-to-r from-blue-900 via-indigo-950 to-slate-900 p-6 rounded-3xl text-white shadow-xl flex flex-col sm:flex-row items-center justify-between gap-4 border border-blue-800/60">
            <div>
              <span className="text-xs font-bold text-amber-300 uppercase tracking-widest flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-amber-300" />
                Devis & Cahier des Charges Analysé avec Succès
              </span>
              <h3 className="text-base sm:text-lg font-black text-white mt-1">
                Injecter ce Devis ({processedLots.length} Lots • {processedLots.reduce((acc, l) => acc + l.sections.reduce((a, s) => a + s.articles.length, 0), 0)} Articles) comme Nouveau Projet
              </h3>
              <p className="text-xs text-blue-200 mt-0.5">
                Crée instantanément le marché dans l'ERP avec tous ses lots, sous-lots, articles et sous-totaux chiffrés.
              </p>
            </div>
            <button
              type="button"
              onClick={handleCreateProjectProfileFromDevis}
              className="px-6 py-3.5 bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-white font-black text-xs sm:text-sm rounded-2xl shadow-xl flex items-center gap-2 transition-all cursor-pointer hover:scale-105 shrink-0"
            >
              <CheckCircle2 className="w-5 h-5 text-emerald-100" />
              <span>Injecter comme Nouveau Projet</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* SUB-TAB 2 : APERÇU IMPRESSION BPU A4 AVEC SOUS-TOTAUX    */}
      {/* ======================================================== */}
      {activeSubTab === 'bpu-print' && (
        <div className="space-y-6">
          <div className="print:hidden bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex items-center justify-between gap-4">
            <span className="text-xs font-bold text-slate-700">
              Aperçu officiel BPU structuré par Lot & Sous-Lot pour impression A4
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
          <div className="bg-white p-8 sm:p-12 rounded-2xl border border-slate-300 shadow-xl max-w-5xl mx-auto print:p-0 print:border-none print:shadow-none print:max-w-none text-slate-900 font-sans space-y-6">
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

            <div className="p-3.5 border border-slate-400 rounded bg-slate-50/50 print:bg-white text-xs">
              <div className="flex justify-between items-start border-b border-slate-300 pb-2 mb-2">
                <div>
                  <h2 className="text-sm font-black uppercase text-slate-900">
                    BORDEREAU DES PRIX UNITAIRES & DQE PAR LOTS
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

            {/* LOTS PRINT TABLES */}
            {processedLots.map(lot => (
              <div key={lot.code} className="space-y-2">
                <div className="bg-slate-200 border border-slate-400 p-2 font-black uppercase text-xs flex justify-between items-center">
                  <span>{lot.code} — {lot.name}</span>
                  <span className="font-mono">Sous-Total : {formatDA(lot.subtotalHT)}</span>
                </div>

                {lot.sections.map(sec => (
                  <div key={sec.code} className="space-y-1">
                    <div className="bg-slate-100 border-x border-t border-slate-400 px-2 py-1 font-bold text-[10px] text-slate-700">
                      {sec.code} : {sec.name}
                    </div>

                    <table className="w-full text-left border-collapse border border-slate-400 text-[10px] print:text-[9px]">
                      <thead>
                        <tr className="bg-slate-50 border-b border-slate-400 font-bold uppercase text-slate-800">
                          <th className="border border-slate-400 p-1.5 w-14 text-center">N° Prix</th>
                          <th className="border border-slate-400 p-1.5">Désignation des Ouvrages</th>
                          <th className="border border-slate-400 p-1.5 text-center w-10">Unité</th>
                          <th className="border border-slate-400 p-1.5 text-right w-16">Quantité</th>
                          <th className="border border-slate-400 p-1.5 text-right w-24">P.U Chiffres (DA)</th>
                          <th className="border border-slate-400 p-1.5">Prix en Toutes Lettres</th>
                          <th className="border border-slate-400 p-1.5 text-right w-28">Total HT (DA)</th>
                        </tr>
                      </thead>
                      <tbody>
                        {sec.articles.map(item => (
                          <tr key={item.id} className="border-b border-slate-300">
                            <td className="border border-slate-400 p-1.5 font-mono text-center font-bold text-blue-900">
                              {item.code}
                            </td>
                            <td className="border border-slate-400 p-1.5 font-medium">
                              {item.designation}
                            </td>
                            <td className="border border-slate-400 p-1.5 text-center font-mono font-bold">
                              {item.unit}
                            </td>
                            <td className="border border-slate-400 p-1.5 text-right font-mono">
                              {formatNumberDZ(item.quantity)}
                            </td>
                            <td className="border border-slate-400 p-1.5 text-right font-mono font-black whitespace-nowrap">
                              {formatDA(item.unitPriceHT)}
                            </td>
                            <td className="border border-slate-400 p-1.5 font-serif italic text-[9px] uppercase font-bold">
                              « {item.unitPriceWords} »
                            </td>
                            <td className="border border-slate-400 p-1.5 text-right font-mono font-bold whitespace-nowrap">
                              {formatDA(item.totalLineHT)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ))}
              </div>
            ))}

            {/* CADRE RÉCAPITULATIF GÉNÉRAL PAR LOT */}
            <div className="pt-4 space-y-2">
              <h3 className="font-black uppercase text-xs border-b border-slate-900 pb-1">
                BORDEREAU RÉCAPITULATIF GÉNÉRAL (PAR CORPS D'ÉTAT)
              </h3>
              <table className="w-full text-left border-collapse border border-slate-900 text-xs">
                <thead>
                  <tr className="bg-slate-200 font-bold uppercase text-[10px]">
                    <th className="border border-slate-400 p-2 w-20">Code</th>
                    <th className="border border-slate-400 p-2">Intitulé du Lot</th>
                    <th className="border border-slate-400 p-2 text-right w-44">Sous-Total HT (DA)</th>
                  </tr>
                </thead>
                <tbody>
                  {processedLots.map(l => (
                    <tr key={l.code} className="border-b border-slate-300">
                      <td className="border border-slate-400 p-2 font-mono font-bold">{l.code}</td>
                      <td className="border border-slate-400 p-2 font-medium">{l.name}</td>
                      <td className="border border-slate-400 p-2 text-right font-mono font-black">{formatDA(l.subtotalHT)}</td>
                    </tr>
                  ))}
                  <tr className="bg-slate-100 font-black">
                    <td colSpan={2} className="border border-slate-400 p-2 uppercase">Total Général HT</td>
                    <td className="border border-slate-400 p-2 text-right font-mono text-sm">{formatDA(totalBpuHT)}</td>
                  </tr>
                  <tr className="font-bold">
                    <td colSpan={2} className="border border-slate-400 p-2">TVA Réglementaire ({tvaRate}%)</td>
                    <td className="border border-slate-400 p-2 text-right font-mono">+{formatDA(totalTva)}</td>
                  </tr>
                  <tr className="bg-slate-900 text-white font-black text-sm">
                    <td colSpan={2} className="border border-slate-900 p-2.5 uppercase">Total Général de l'Offre TTC</td>
                    <td className="border border-slate-900 p-2.5 text-right font-mono text-amber-300">{formatDA(totalBpuTTC)}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Signatures */}
            <div className="grid grid-cols-2 gap-8 pt-6 border-t-2 border-slate-900 text-xs">
              <div className="p-3 border border-slate-300 rounded space-y-1">
                <span className="font-bold block">Montant Total de la Soumission :</span>
                <p className="font-mono font-black text-sm text-slate-900">
                  Total TTC : {formatDA(totalBpuTTC)}
                </p>
                <p className="font-serif italic text-[10px] text-slate-700 uppercase pt-1">
                  Arrêté à : « {totalBpuTTCWords} »
                </p>
              </div>

              <div className="p-3 border border-slate-400 rounded h-32 flex flex-col justify-between text-center">
                <div>
                  <strong className="text-[11px] uppercase tracking-wider block">
                    Pour l'Entreprise Soumissionnaire
                  </strong>
                  <span className="text-[10px] text-slate-500 italic">
                    (Lu et accepté)
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

      {/* MODAL DE CÉLÉBRATION : NOUVEAU PROJET CRÉÉ AVEC TOUS SES LOTS */}
      {createdProjectModal?.isOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl border border-slate-200 shadow-2xl w-full max-w-xl overflow-hidden animate-in fade-in zoom-in-95 duration-200">
            <div className="bg-gradient-to-r from-emerald-600 to-teal-600 p-6 text-white text-center relative">
              <div className="w-14 h-14 rounded-2xl bg-white/20 text-white flex items-center justify-center mx-auto mb-3 shadow-inner">
                <CheckCircle2 className="w-8 h-8 text-white" />
              </div>
              <span className="px-3 py-1 rounded-full bg-white/20 text-emerald-100 text-[11px] font-black uppercase tracking-wider">
                Nouveau Projet Initialisé par l'IA
              </span>
              <h3 className="text-lg font-black mt-2">
                Marché, Lots, Sous-Lots & DQE Configurés avec Succès
              </h3>
              <p className="text-xs text-emerald-100 mt-1">
                Tous les sous-totaux par lot ont été injectés dans l'ERP
              </p>
            </div>

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

                <div className="pt-2 border-t border-slate-200 grid grid-cols-2 sm:grid-cols-3 gap-2 text-slate-600">
                  <div>
                    <span className="text-[10px] text-slate-400 block">Lots Créés</span>
                    <strong className="text-blue-700 font-mono">{createdProjectModal.lotsCount} Lots</strong>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Sous-Lots (Sections)</span>
                    <strong className="text-indigo-700 font-mono">{createdProjectModal.sectionsCount} Chapitres</strong>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Articles DQE</span>
                    <strong className="text-emerald-700 font-mono">{createdProjectModal.articlesCount} Articles</strong>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block">Montant Initial HT</span>
                    <strong className="text-slate-900 font-mono">{formatDA(createdProjectModal.totalHT)}</strong>
                  </div>
                  <div className="sm:col-span-2">
                    <span className="text-[10px] text-slate-400 block">Montant Total TTC ({tvaRate}%)</span>
                    <strong className="text-emerald-700 font-mono font-black">{formatDA(createdProjectModal.totalTTC)}</strong>
                  </div>
                  <div className="sm:col-span-3 pt-2 border-t border-slate-200">
                    <span className="text-[10px] text-slate-400 block">Modalité de Garantie Financière Choisi</span>
                    <strong className="text-slate-800 flex items-center gap-1.5 mt-0.5">
                      {createdProjectModal.guaranteeModality === 'caution_bonne_execution' ? (
                        <>
                          <ShieldCheck className="w-4 h-4 text-blue-600 shrink-0" />
                          <span>Caution Bancaire (5%) : {formatDA(createdProjectModal.cautionAmount)} ({customBankName}) • Retenue 0% sur décomptes</span>
                        </>
                      ) : createdProjectModal.guaranteeModality === 'retenue_garantie' ? (
                        <>
                          <Scale className="w-4 h-4 text-amber-600 shrink-0" />
                          <span>Retenue de Garantie (5%) : Prélèvement sur situations mensuelles (consigné au Trésor)</span>
                        </>
                      ) : createdProjectModal.guaranteeModality === 'both' ? (
                        <>
                          <Building2 className="w-4 h-4 text-indigo-600 shrink-0" />
                          <span>Double Garantie : Caution 5% ({formatDA(createdProjectModal.cautionAmount)}) + Retenue 5%</span>
                        </>
                      ) : (
                        <span>Sans retenue ni caution (0%)</span>
                      )}
                    </strong>
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    if (onNavigate) onNavigate('projects');
                  }}
                  className="px-4 py-3 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white font-black rounded-xl shadow-md flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <FolderKanban className="w-4 h-4" />
                  <span>Gérer dans l'onglet Projets</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    if (onNavigate) onNavigate('markets');
                  }}
                  className="px-4 py-3 bg-blue-600 hover:bg-blue-700 text-white font-black rounded-xl shadow-md flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <FileSpreadsheet className="w-4 h-4" />
                  <span>Consulter le DQE par Lots</span>
                </button>

                <button
                  type="button"
                  onClick={() => {
                    setCreatedProjectModal(null);
                    if (onNavigate) onNavigate('dashboard');
                  }}
                  className="px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-black rounded-xl shadow-md flex items-center justify-center gap-2 cursor-pointer transition-colors"
                >
                  <Building2 className="w-4 h-4" />
                  <span>Voir le Tableau de Bord</span>
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
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
