import React from 'react';
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
  ShieldAlert
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';

export type ActiveTab = 
  | 'dashboard'
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
      label: 'Stock & Logistique',
      labelAr: 'المخزون واللوجستيك',
      icon: Truck,
      badge: null
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
      {/* Mobile overlay */}
      {isOpen && (
        <div 
          className="fixed inset-0 bg-slate-950/60 backdrop-blur-sm z-40 lg:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}

      <aside className={`
        fixed top-0 bottom-0 left-0 z-50 w-72 bg-slate-900 text-slate-200 border-r border-slate-800
        flex flex-col transition-transform duration-300 ease-in-out
        ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
      `}>
        {/* Brand header */}
        <div className="h-18 px-5 border-b border-slate-800/80 flex items-center justify-between bg-slate-950/40">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-600 via-indigo-600 to-amber-500 flex items-center justify-center shadow-lg shadow-blue-500/20 text-white font-extrabold text-base tracking-wider">
              AM
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-extrabold text-white text-base tracking-tight">ARCHILAB</span>
                <span className="text-[10px] font-bold uppercase bg-blue-500/20 text-blue-400 px-1.5 py-0.5 rounded border border-blue-500/30">MANAGER</span>
              </div>
              <p className="text-[11px] text-slate-400 font-medium">BTP & Marchés Publics Algérie</p>
            </div>
          </div>
        </div>

        {/* Exceedance Banner Notification if any */}
        {exceedanceCount > 0 && (
          <div className="mx-3 mt-3 p-2.5 rounded-lg bg-rose-500/10 border border-rose-500/30 flex items-start gap-2.5 text-xs text-rose-300">
            <ShieldAlert className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-rose-200">Dépassement détecté</p>
              <p className="text-[11px] text-rose-300/80">Un attachement dépasse la quantité marché contractuelle.</p>
            </div>
          </div>
        )}

        {/* Navigation list */}
        <div className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
          <div className="px-3 pb-2 text-[10px] font-bold uppercase tracking-wider text-slate-400">
            {lang === 'ar' ? 'العمليات الرئيسية' : 'Gestion & Chantier'}
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
                }}
                className={`
                  w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl font-medium text-xs transition-all duration-150 group
                  ${isActive 
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30 font-semibold' 
                    : 'text-slate-300 hover:bg-slate-800/70 hover:text-white'
                  }
                `}
              >
                <div className="flex items-center gap-3">
                  <Icon className={`w-4 h-4 transition-colors ${isActive ? 'text-white' : 'text-slate-400 group-hover:text-blue-400'}`} />
                  <span className="truncate">{lang === 'ar' ? item.labelAr : item.label}</span>
                </div>

                <div className="flex items-center gap-1.5">
                  {item.badge && (
                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${item.badgeColor || 'bg-slate-800 text-slate-300 border border-slate-700'}`}>
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
        <div className="p-4 border-t border-slate-800/80 bg-slate-950/60">
          <div className="p-2.5 rounded-xl bg-slate-800/50 border border-slate-700/50">
            <div className="flex items-center justify-between text-[11px] font-semibold text-slate-300">
              <span>Code Marchés Publics DZ</span>
              <span className="text-emerald-400 font-mono text-[10px]">Décret 15-247</span>
            </div>
            <p className="text-[10px] text-slate-400 mt-1">
              Conformité TVA 9%/19% • Retenue 5% • Cautions bancaires
            </p>
          </div>
        </div>
      </aside>
    </>
  );
};
