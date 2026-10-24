import React, { useState } from 'react';
import { BTPProvider, useBTP } from './context/BTPContext';
import { Sidebar, ActiveTab } from './components/Sidebar';
import { Header } from './components/Header';
import { DashboardView } from './views/DashboardView';
import { ProjectsView } from './views/ProjectsView';
import { MarketsView } from './views/MarketsView';
import { ChantiersView } from './views/ChantiersView';
import { AttachementsView } from './views/AttachementsView';
import { AvenantsView } from './views/AvenantsView';
import { SituationsView } from './views/SituationsView';
import { OfficialDocumentsView } from './views/OfficialDocumentsView';
import { LogisticsView } from './views/LogisticsView';
import { FinanceView } from './views/FinanceView';
import { HRView } from './views/HRView';
import { IAMetreurView } from './views/IAMetreurView';
import { TesterFeedbackWidget } from './components/TesterFeedbackWidget';

const MainLayout: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ActiveTab>('dashboard');
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const { lang } = useBTP();

  return (
    <div className={`min-h-screen bg-slate-100/70 flex flex-col ${lang === 'ar' ? 'font-sans' : 'font-sans'}`} dir={lang === 'ar' ? 'rtl' : 'ltr'}>
      {/* Sidebar Navigation */}
      <Sidebar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        isOpen={isSidebarOpen}
        setIsOpen={setIsSidebarOpen}
      />

      {/* Main Content Area */}
      <div className="lg:pl-72 flex flex-col flex-1 min-h-screen">
        {/* Top Navbar */}
        <Header onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)} />

        {/* View Content */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          {activeTab === 'dashboard' && <DashboardView onNavigate={setActiveTab} />}
          {activeTab === 'projects' && <ProjectsView onNavigate={setActiveTab} />}
          {activeTab === 'markets' && <MarketsView />}
          {activeTab === 'chantiers' && <ChantiersView onNavigate={setActiveTab} />}
          {activeTab === 'attachements' && <AttachementsView onNavigate={setActiveTab} />}
          {activeTab === 'avenants' && <AvenantsView onNavigate={setActiveTab} />}
          {activeTab === 'situations' && <SituationsView onNavigate={setActiveTab} />}
          {activeTab === 'documents' && <OfficialDocumentsView />}
          {activeTab === 'logistics' && <LogisticsView />}
          {activeTab === 'finance' && <FinanceView />}
          {activeTab === 'hr' && <HRView />}
          {activeTab === 'ai-metre' && <IAMetreurView onNavigate={setActiveTab} />}
        </main>

        {/* Floating Tester Feedback Widget */}
        <TesterFeedbackWidget activeTab={activeTab} />
      </div>
    </div>
  );
};

export default function App() {
  return (
    <BTPProvider>
      <MainLayout />
    </BTPProvider>
  );
}
