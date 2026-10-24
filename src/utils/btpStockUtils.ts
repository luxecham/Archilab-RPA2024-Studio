import { SteelDiameter, StockCategory } from '../types/btp';

// Masses linéaires standard Eurocode 2 / Décret BTP Algérie pour l'acier FeE500
export const STEEL_LINEAR_MASS: Record<SteelDiameter, { kgPerM: number; barWeightKg: number; barsPerTonne: number }> = {
  6: { kgPerM: 0.222, barWeightKg: 2.664, barsPerTonne: 375.38 },
  8: { kgPerM: 0.395, barWeightKg: 4.740, barsPerTonne: 210.97 },
  10: { kgPerM: 0.617, barWeightKg: 7.404, barsPerTonne: 135.06 },
  12: { kgPerM: 0.888, barWeightKg: 10.656, barsPerTonne: 93.84 },
  16: { kgPerM: 1.578, barWeightKg: 18.936, barsPerTonne: 52.81 },
  20: { kgPerM: 2.466, barWeightKg: 29.592, barsPerTonne: 33.79 }
};

/**
 * Convertit instantanément une quantité d'acier entre Barres de 12m et Tonnes
 */
export function convertSteel(
  diameter: SteelDiameter, 
  quantity: number, 
  unit: 'barre' | 'tonne'
): { barres: number; tonnes: number } {
  const spec = STEEL_LINEAR_MASS[diameter] || STEEL_LINEAR_MASS[12];
  if (unit === 'barre') {
    const tonnes = (quantity * spec.barWeightKg) / 1000;
    return { barres: quantity, tonnes: Number(tonnes.toFixed(3)) };
  } else {
    const barres = Math.round((quantity * 1000) / spec.barWeightKg);
    return { barres, tonnes: quantity };
  }
}

/**
 * Unités autorisées par catégorie selon les directives chantier BTP DZ :
 * - bois = plaque (ou madrier)
 * - ciment = sac (50 kg)
 * - granulat = m3
 * - sable = m3
 * - plomberie = soit ml soit Unité
 * - electricite = soit m.lineaire soit Unité
 * - brique et bloc = unité ou m2
 * - revetement sol = m3 ou carton (et m2)
 * - acier = barre ou tonne (Ø6, Ø8, Ø10, Ø12, Ø16, Ø20)
 */
export const CATEGORY_ALLOWED_UNITS: Record<string, string[]> = {
  'Bois': ['plaque', 'madrier', 'chevron', 'panneau'],
  'Ciment': ['sac', 'tonne'],
  'Granulat': ['m3', 'tonne'],
  'Sable': ['m3'],
  'Plomberie': ['ml', 'Unité'],
  'Électricité': ['m.lineaire', 'Unité'],
  'Brique et bloc': ['unité', 'm2'],
  'Revêtement sol': ['carton', 'm2', 'm3'],
  'Acier': ['barre', 'tonne'],
  'Autre': ['Unité', 'm2', 'ml', 'm3', 'kg']
};

export const DEFAULT_UNIT_PER_CATEGORY: Record<string, string> = {
  'Bois': 'plaque',
  'Ciment': 'sac',
  'Granulat': 'm3',
  'Sable': 'm3',
  'Plomberie': 'ml',
  'Électricité': 'm.lineaire',
  'Brique et bloc': 'unité',
  'Revêtement sol': 'carton',
  'Acier': 'barre',
  'Autre': 'Unité'
};

export const CATEGORY_ICONS_BADGES: Record<string, { label: string; icon: string; color: string; badge: string }> = {
  'Bois': { label: 'Bois & Coffrage', icon: '🪵', color: 'bg-amber-100 text-amber-900 border-amber-200', badge: 'plaque' },
  'Ciment': { label: 'Ciment', icon: '🧱', color: 'bg-slate-200 text-slate-800 border-slate-300', badge: 'sac' },
  'Granulat': { label: 'Granulat', icon: '⛰️', color: 'bg-stone-200 text-stone-800 border-stone-300', badge: 'm3' },
  'Sable': { label: 'Sable', icon: '🏜️', color: 'bg-yellow-100 text-yellow-800 border-yellow-200', badge: 'm3' },
  'Plomberie': { label: 'Plomberie', icon: '🚿', color: 'bg-sky-100 text-sky-800 border-sky-200', badge: 'ml / Unité' },
  'Électricité': { label: 'Électricité', icon: '⚡', color: 'bg-amber-100 text-amber-800 border-amber-300', badge: 'm.lineaire / Unité' },
  'Brique et bloc': { label: 'Brique & Bloc', icon: '🧱', color: 'bg-orange-100 text-orange-900 border-orange-200', badge: 'unité / m2' },
  'Revêtement sol': { label: 'Revêtement Sol', icon: '✨', color: 'bg-teal-100 text-teal-800 border-teal-200', badge: 'm3 / carton / m2' },
  'Acier': { label: 'Acier & Ferraillage', icon: '🔩', color: 'bg-blue-100 text-blue-900 border-blue-200', badge: 'barre / tonne' },
  'Autre': { label: 'Autre Fourniture', icon: '📦', color: 'bg-gray-100 text-gray-800 border-gray-200', badge: 'divers' }
};
