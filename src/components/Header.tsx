import React from 'react';
import { Menu, Globe, Building, ShieldCheck, RefreshCw } from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { ALGERIAN_WILAYAS } from '../utils/dzFormatters';

interface HeaderProps {
  onToggleSidebar: () => void;
  onOpenNewAttachement?: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onToggleSidebar }) => {
  const { 
    markets, 
    selectedMarketId, 
    setSelectedMarketId, 
    lang, 
    setLang,
    resetData
  } = useBTP();

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const currentWilaya = currentMarket ? ALGERIAN_WILAYAS.find(w => w.code === currentMarket.wilaya) : null;

  return (
    <header className="sticky top-0 z-30 h-16 bg-white border-b border-slate-200/80 shadow-xs flex items-center justify-between px-4 sm:px-6">
      {/* Left side: Hamburger + Market Selector */}
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleSidebar}
          className="p-2 -ml-2 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-100 lg:hidden transition-colors"
          aria-label="Menu"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-2">
          <Building className="w-4 h-4 text-blue-600 shrink-0 hidden sm:block" />
          <div className="flex flex-col">
            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-600">Marché Actif</span>
            <select
              value={currentMarket ? currentMarket.id : ''}
              onChange={(e) => setSelectedMarketId(e.target.value)}
              className="font-bold text-xs sm:text-sm text-slate-800 bg-transparent border-none p-0 pr-6 focus:ring-0 cursor-pointer truncate max-w-[200px] sm:max-w-md"
            >
              {markets.length === 0 ? (
                <option value="">Aucun projet actif</option>
              ) : (
                markets.map(m => (
                  <option key={m.id} value={m.id}>
                    {m.status === 'Archivé' ? '📦 [Archivé] ' : ''}N° {m.number} — {m.title}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>

        {currentMarket && (
          <div className="hidden md:flex items-center gap-2 pl-3 border-l border-slate-200 text-xs">
            <span className="px-2 py-0.5 rounded-md font-semibold bg-blue-50 text-blue-700 border border-blue-200/60">
              {currentWilaya ? `Wilaya ${currentWilaya.code} - ${currentWilaya.name}` : currentMarket.wilaya}
            </span>
            <span className="px-2 py-0.5 rounded-md font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/60">
              TVA {currentMarket.tvaRate}%
            </span>
            <span className="px-2 py-0.5 rounded-md font-semibold bg-amber-50 text-amber-800 border border-amber-200/60">
              Retenue {currentMarket.retenueGarantieRate}%
            </span>
          </div>
        )}
      </div>

      {/* Right side: Actions, Language Switcher, Reset Demo */}
      <div className="flex items-center gap-2 sm:gap-3">
        {/* Anti-Rejet Certification Badge */}
        <div className="hidden xl:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-slate-100 text-slate-600 text-[11px] font-medium border border-slate-200">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
          <span>Certifié Conforme Trésorerie DZ</span>
        </div>

        {/* Reset Demo button */}
        <button
          onClick={() => {
            if (confirm("Réinitialiser les données de démonstration d'origine ?")) {
              resetData();
            }
          }}
          title="Réinitialiser les données de démo"
          className="p-2 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
        >
          <RefreshCw className="w-4 h-4" />
        </button>

        {/* Language switch */}
        <button
          onClick={() => setLang(lang === 'fr' ? 'ar' : 'fr')}
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
        >
          <Globe className="w-3.5 h-3.5 text-blue-600" />
          <span>{lang === 'fr' ? 'العربية' : 'Français'}</span>
        </button>
      </div>
    </header>
  );
};
