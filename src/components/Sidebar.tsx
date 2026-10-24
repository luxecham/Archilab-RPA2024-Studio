import React, { useState } from 'react';
import { 
  Building2, 
  HardHat, 
  FileSpreadsheet, 
  FileText, 
  Scale, 
  Receipt, 
  Printer, 
  Truck, 
  Landmark, 
  Users, 
  Sparkles,
  ChevronRight,
  ShieldAlert,
  FolderKanban,
  MoreHorizontal,
  X
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';

export type ActiveTab = 
  | 'dashboard'
  | 'projects'
  | 'markets'
  | 'chantiers'
  | 'attachements'
  | 'avenants'
  | 'situations'
  | 'documents'
  | 'logistics'
  | 'finance'
  | 'hr'
  | 'ai-metre';

interface SidebarProps {
  activeTab: ActiveTab;
  setActiveTab: (tab: ActiveTab) => void;
  isOpen: boolean;
  setIsOpen: (open: boolean) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab, isOpen, setIsOpen }) => {
  const { attachements, markets, lang } = useBTP();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  // Compter les attachements avec dépassement contractuel
  const exceedanceCount = attachements.reduce((acc, att) => {
    return acc + (att.items.some(i => i.hasExceedance) ? 1 : 0);
  }, 0);

  const navItems = [
    {
      id: 'dashboard',
      label: 'Tableau de Bord',
      labelAr: 'لوحة القيادة',
      icon: Building2,
      badge: null
    },
    {
      id: 'projects',
      label: 'Projets',
      labelAr: 'المشاريع',
      icon: FolderKanban,
      badge: `${markets.filter(m => m.status !== 'Archivé').length}`
    },
    {
      id: 'markets',
      label: 'Marchés & DQE',
      labelAr: 'الصفقات وجداول الأسعار',
      icon: FileSpreadsheet,
      badge: `${markets.length}`
    },
    {
      id: 'chantiers',
      label: 'Chantiers (Terrain)',
      labelAr: 'ورشات البناء',
      icon: HardHat,
      badge: null
    },
    {
      id: 'attachements',
      label: 'Attachements & Métré',
      labelAr: 'الكشوفات والقياسات',
      icon: FileText,
      badge: exceedanceCount > 0 ? `${exceedanceCount} alerte!` : null,
      badgeColor: 'bg-rose-500 text-white animate-pulse'
    },
    {
      id: 'avenants',
      label: 'Avenants & Dépassements',
      labelAr: 'الملاحق والتسويات',
      icon: Scale,
      badge: null
    },
    {
      id: 'situations',
      label: 'Situations & DGD',
      labelAr: 'كشوف الحساب المؤقتة',
      icon: Receipt,
      badge: null
    },
    {
      id: 'documents',
      label: 'Documents Officiels DZ',
      labelAr: 'الوثائق الرسمية',
      icon: Printer,
      badge: 'PDF'
    },
    {
      id: 'ai-metre',
      label: 'Scanner IA Devis & BPU',
      labelAr: 'مسح كشوف الأسعار بالذكاء الاصطناعي',
      icon: Sparkles,
      badge: 'IA OCR',
      badgeColor: 'bg-gradient-to-r from-blue-600 to-indigo-600 text-white font-black animate-pulse'
    },
    {
      id: 'logistics',
      label: 'Stocks & Engins BTP',
      labelAr: 'المخزون والعتاد',
      icon: Truck,
      badge: 'Matériaux DZ'
    },
    {
      id: 'finance',
      label: 'Finance & Chèques DZ',
      labelAr: 'المالية والصكوك',
      icon: Landmark,
      badge: null
    },
    {
      id: 'hr',
      label: 'Équipes & CNAS / CACOBATPH',
      labelAr: 'العمال والضمان الاجتماعي',
      icon: Users,
      badge: null
    }
  ];

  return (
    <>
      {/* Mobile Backdrop Overlay for Drawer */}
      {(isOpen || isMobileMenuOpen) && (
        <div 
          className="fixed inset-0 bg-slate-950/60 backdrop-blur-md z-40 lg:hidden"
          onClick={() => {
            setIsOpen(false);
            setIsMobileMenuOpen(false);
          }}
        />
      )}

      {/* ======================================================== */}
      {/* DESKTOP FLOATING LIQUID GLASS SIDEBAR (>= 1024px)        */}
      {/* & MOBILE DRAWER (< 1024px)                              */}
      {/* ======================================================== */}
      <aside className={`
        fixed top-0 bottom-0 left-0 z-50 w-72 
        lg:top-3 lg:bottom-3 lg:left-3 lg:w-68 lg:rounded-3xl lg:border lg:border-white/50 lg:dark:border-white/10
        liquid-glass text-slate-800 dark:text-slate-100 shadow-2xl
        flex flex-col transition-all duration-300 ease-in-out
        ${isOpen || isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
      `}>
        {/* Brand header */}
        <div className="h-18 px-5 border-b border-white/40 dark:border-white/10 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-br from-blue-600 via-indigo-600 to-cyan-500 flex items-center justify-center shadow-lg shadow-blue-500/25 text-white font-black text-base tracking-wider border border-white/30">
              AM
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-black text-slate-900 dark:text-white text-base tracking-tight">ARCHILAB</span>
                <span className="text-[10px] font-black uppercase bg-blue-500/20 text-blue-700 dark:text-blue-300 px-1.5 py-0.5 rounded-full border border-blue-500/30">
                  ERP DZ
                </span>
              </div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 font-semibold">Marchés Publics & BTP</p>
            </div>
          </div>

          <button 
            onClick={() => {
              setIsOpen(false);
              setIsMobileMenuOpen(false);
            }} 
            className="lg:hidden p-1.5 rounded-xl text-slate-400 hover:text-slate-600 dark:hover:text-white"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Exceedance Banner Notification if any */}
        {exceedanceCount > 0 && (
          <div className="mx-3 mt-3 p-2.5 rounded-2xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-2.5 text-xs text-rose-700 dark:text-rose-300 backdrop-blur-xs">
            <ShieldAlert className="w-4 h-4 text-rose-500 shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-rose-800 dark:text-rose-200">Dépassement Détecté</p>
              <p className="text-[11px] opacity-90">Attachement excédant la quantité contractuelle.</p>
            </div>
          </div>
        )}

        {/* Navigation list */}
        <div className="flex-1 overflow-y-auto px-3 py-3 space-y-1">
          <div className="px-3 pb-2 text-[10px] font-black uppercase tracking-wider text-slate-400 dark:text-slate-400">
            {lang === 'ar' ? 'العمليات الرئيسية' : 'Modules Métier'}
          </div>

          {navItems.map(item => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => {
                  setActiveTab(item.id as ActiveTab);
                  setIsOpen(false);
                  setIsMobileMenuOpen(false);
                }}
                className={`
                  w-full flex items-center justify-between px-3.5 py-2.5 rounded-2xl font-bold text-xs transition-all duration-200 group
                  ${isActive 
                    ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/30 font-black' 
                    : 'text-slate-700 dark:text-slate-300 hover:bg-white/60 dark:hover:bg-slate-800/60 hover:text-blue-600 dark:hover:text-white'
                  }
                `}
              >
                <div className="flex items-center gap-2.5">
                  <Icon className={`w-4 h-4 transition-transform group-hover:scale-110 ${isActive ? 'text-white' : 'text-slate-400 group-hover:text-blue-500'}`} />
                  <span className="truncate">{lang === 'ar' ? item.labelAr : item.label}</span>
                </div>

                <div className="flex items-center gap-1.5">
                  {item.badge && (
                    <span className={`text-[10px] font-black px-2 py-0.5 rounded-full ${item.badgeColor || 'bg-slate-200/70 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-300/60 dark:border-slate-700'}`}>
                      {item.badge}
                    </span>
                  )}
                  {isActive && <ChevronRight className="w-3.5 h-3.5 text-blue-200" />}
                </div>
              </button>
            );
          })}
        </div>

        {/* Footer info & standards */}
        <div className="p-3.5 border-t border-white/40 dark:border-white/10">
          <div className="p-2.5 rounded-2xl bg-white/40 dark:bg-slate-800/40 border border-white/60 dark:border-white/10">
            <div className="flex items-center justify-between text-[11px] font-extrabold text-slate-800 dark:text-slate-200">
              <span>Code Marchés Publics</span>
              <span className="text-emerald-600 dark:text-emerald-400 font-mono text-[10px] bg-emerald-500/10 px-1.5 py-0.5 rounded-md">Décret 15-247</span>
            </div>
            <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-1 font-medium">
              TVA 9%/19% • Retenue 5% • Caution 5%
            </p>
          </div>
        </div>
      </aside>

      {/* ======================================================== */}
      {/* MOBILE FLOATING LIQUID GLASS BOTTOM DOCK (< 768px)       */}
      {/* ======================================================== */}
      <nav className="fixed bottom-3 inset-x-3 z-40 md:hidden liquid-glass-dock rounded-3xl p-1.5 flex items-center justify-around shadow-2xl border border-white/60 dark:border-white/15">
        {/* 1. Tableau de bord */}
        <button
          onClick={() => setActiveTab('dashboard')}
          className={`flex flex-col items-center justify-center p-2 rounded-2xl transition-all touch-target ${
            activeTab === 'dashboard' ? 'text-blue-600 dark:text-blue-400 font-black' : 'text-slate-500 dark:text-slate-400'
          }`}
        >
          <Building2 className="w-5 h-5" />
          <span className="text-[10px] mt-0.5 font-bold">Bord</span>
        </button>

        {/* 2. Projets */}
        <button
          onClick={() => setActiveTab('projects')}
          className={`flex flex-col items-center justify-center p-2 rounded-2xl transition-all touch-target ${
            activeTab === 'projects' ? 'text-blue-600 dark:text-blue-400 font-black' : 'text-slate-500 dark:text-slate-400'
          }`}
        >
          <FolderKanban className="w-5 h-5" />
          <span className="text-[10px] mt-0.5 font-bold">Projets</span>
        </button>

        {/* 3. HERO CENTRAL BUTTON: SCANNER IA OCR */}
        <button
          onClick={() => setActiveTab('ai-metre')}
          className={`-top-4 relative flex flex-col items-center justify-center p-3 rounded-full transition-all shadow-xl touch-target ${
            activeTab === 'ai-metre'
              ? 'bg-gradient-to-tr from-blue-600 via-indigo-600 to-cyan-400 text-white ring-4 ring-blue-500/30 scale-105'
              : 'bg-gradient-to-tr from-blue-600 to-indigo-600 text-white shadow-blue-500/40 hover:scale-105'
          }`}
          title="Scanner IA Devis & OCR"
        >
          <Sparkles className="w-6 h-6 animate-pulse" />
          <span className="sr-only">Scanner IA</span>
        </button>

        {/* 4. Stocks & Engins */}
        <button
          onClick={() => setActiveTab('logistics')}
          className={`flex flex-col items-center justify-center p-2 rounded-2xl transition-all touch-target ${
            activeTab === 'logistics' ? 'text-blue-600 dark:text-blue-400 font-black' : 'text-slate-500 dark:text-slate-400'
          }`}
        >
          <Truck className="w-5 h-5" />
          <span className="text-[10px] mt-0.5 font-bold">Stocks</span>
        </button>

        {/* 5. Menu Plus */}
        <button
          onClick={() => setIsMobileMenuOpen(true)}
          className="flex flex-col items-center justify-center p-2 rounded-2xl text-slate-500 dark:text-slate-400 transition-all touch-target hover:text-slate-900 dark:hover:text-white"
        >
          <MoreHorizontal className="w-5 h-5" />
          <span className="text-[10px] mt-0.5 font-bold">Plus</span>
        </button>
      </nav>
    </>
  );
};
