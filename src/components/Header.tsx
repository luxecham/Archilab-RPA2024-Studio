import React, { useRef } from 'react';
import { Menu, Globe, Building, ShieldCheck, RefreshCw, Sun, Moon, Download, Upload } from 'lucide-react';
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
    resetData,
    theme,
    toggleTheme,
    exportDatabaseJSON,
    importDatabaseJSON
  } = useBTP();

  const fileInputRef = useRef<HTMLInputElement>(null);

  const currentMarket = markets.find(m => m.id === selectedMarketId) || markets[0];
  const currentWilaya = currentMarket ? ALGERIAN_WILAYAS.find(w => w.code === currentMarket.wilaya) : null;

  const handleFileRestore = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      if (content) {
        const success = importDatabaseJSON(content);
        if (success) {
          alert('Base de données restaurée avec succès !');
        } else {
          alert('Erreur : fichier JSON de sauvegarde invalide.');
        }
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  return (
    <header className="sticky top-0 z-30 h-16 liquid-glass border-b border-white/50 dark:border-white/10 px-4 sm:px-6 flex items-center justify-between transition-colors">
      {/* Left side: Hamburger + Market Selector */}
      <div className="flex items-center gap-3">
        <button
          onClick={onToggleSidebar}
          className="p-2 -ml-2 rounded-xl text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-white/40 dark:hover:bg-slate-800/40 lg:hidden transition-colors"
          aria-label="Menu"
        >
          <Menu className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400 hidden sm:flex items-center justify-center border border-blue-500/20">
            <Building className="w-4 h-4" />
          </div>
          <div className="flex flex-col">
            <span className="text-[10px] uppercase font-black tracking-wider text-slate-400 dark:text-slate-400">Marché Actif</span>
            <select
              value={currentMarket ? currentMarket.id : ''}
              onChange={(e) => setSelectedMarketId(e.target.value)}
              className="font-extrabold text-xs sm:text-sm text-slate-800 dark:text-slate-100 bg-transparent border-none p-0 pr-6 focus:ring-0 cursor-pointer truncate max-w-[190px] sm:max-w-xs md:max-w-md"
            >
              {markets.length === 0 ? (
                <option value="" className="text-slate-900 dark:text-slate-100 dark:bg-slate-900">Aucun projet actif</option>
              ) : (
                markets.map(m => (
                  <option key={m.id} value={m.id} className="text-slate-900 dark:text-slate-100 dark:bg-slate-900">
                    {m.status === 'Archivé' ? '📦 [Archivé] ' : ''}N° {m.number} — {m.title}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>

        {currentMarket && (
          <div className="hidden lg:flex items-center gap-1.5 pl-3 border-l border-slate-200/60 dark:border-slate-700/60 text-xs">
            <span className="px-2.5 py-1 rounded-full font-bold bg-blue-500/10 text-blue-700 dark:text-blue-300 border border-blue-500/20 backdrop-blur-xs">
              {currentWilaya ? `Wilaya ${currentWilaya.code} - ${currentWilaya.name}` : currentMarket.wilaya}
            </span>
            <span className="px-2.5 py-1 rounded-full font-bold bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20 backdrop-blur-xs">
              TVA {currentMarket.tvaRate}%
            </span>
            <span className="px-2.5 py-1 rounded-full font-bold bg-amber-500/10 text-amber-700 dark:text-amber-300 border border-amber-500/20 backdrop-blur-xs">
              Retenue {currentMarket.retenueGarantieRate}%
            </span>
          </div>
        )}
      </div>

      {/* Right side: Database Backup, Theme Switcher, Certification Badge, Language Switcher, Reset Demo */}
      <div className="flex items-center gap-1.5 sm:gap-2">
        {/* Anti-Rejet Certification Badge */}
        <div className="hidden xl:flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 text-[11px] font-bold border border-emerald-500/20 shadow-xs">
          <ShieldCheck className="w-3.5 h-3.5" />
          <span>Certifié Marchés Publics DZ (Décret 15-247)</span>
        </div>

        {/* Export / Backup Database JSON */}
        <button
          onClick={exportDatabaseJSON}
          title="Sauvegarder la base de données (.json)"
          className="p-2 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white hover:bg-white/50 dark:hover:bg-slate-800/50 border border-transparent hover:border-white/40 transition-all flex items-center gap-1.5 text-xs font-bold"
        >
          <Download className="w-4 h-4 text-blue-600 dark:text-blue-400" />
          <span className="hidden md:inline">Sauvegarder</span>
        </button>

        {/* Import / Restore Database JSON */}
        <input 
          type="file" 
          ref={fileInputRef} 
          accept=".json" 
          onChange={handleFileRestore} 
          className="hidden" 
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          title="Restaurer une sauvegarde (.json)"
          className="p-2 rounded-xl text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white hover:bg-white/50 dark:hover:bg-slate-800/50 border border-transparent hover:border-white/40 transition-all flex items-center gap-1.5 text-xs font-bold"
        >
          <Upload className="w-4 h-4 text-emerald-600 dark:text-emerald-400" />
          <span className="hidden md:inline">Restaurer</span>
        </button>

        {/* Theme Switcher (Clair / Sombre) */}
        <button
          onClick={toggleTheme}
          title={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
          className="p-2 rounded-xl text-slate-500 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white hover:bg-white/50 dark:hover:bg-slate-800/50 border border-transparent hover:border-white/40 transition-all"
        >
          {theme === 'dark' ? (
            <Sun className="w-4 h-4 text-amber-400" />
          ) : (
            <Moon className="w-4 h-4 text-indigo-600" />
          )}
        </button>

        {/* Reset Demo button */}
        <button
          onClick={() => {
            if (confirm("Réinitialiser toutes les données de démonstration d'origine ?")) {
              resetData();
            }
          }}
          title="Réinitialiser les données de démo"
          className="p-2 rounded-xl text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 hover:bg-white/50 dark:hover:bg-slate-800/50 transition-colors"
        >
          <RefreshCw className="w-4 h-4" />
        </button>

        {/* Language switch */}
        <button
          onClick={() => setLang(lang === 'fr' ? 'ar' : 'fr')}
          className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl border border-white/60 dark:border-white/10 text-xs font-bold text-slate-700 dark:text-slate-200 hover:bg-white/40 dark:hover:bg-slate-800/40 transition-colors backdrop-blur-xs"
        >
          <Globe className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
          <span>{lang === 'fr' ? 'العربية' : 'Français'}</span>
        </button>
      </div>
    </header>
  );
};
