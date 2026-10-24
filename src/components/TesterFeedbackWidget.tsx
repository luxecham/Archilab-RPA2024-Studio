import React, { useState, useEffect } from 'react';
import { 
  MessageSquarePlus, 
  X, 
  Send, 
  Bug, 
  Lightbulb, 
  HelpCircle, 
  Check, 
  Copy, 
  Download, 
  PhoneCall, 
  Share2,
  Trash2
} from 'lucide-react';
import { ActiveTab } from './Sidebar';

interface FeedbackItem {
  id: string;
  date: string;
  userName: string;
  userRole: string;
  type: 'bug' | 'idea' | 'question';
  module: string;
  message: string;
  currentUrl?: string;
}

interface TesterFeedbackWidgetProps {
  activeTab: ActiveTab;
}

const MODULE_NAMES: Record<ActiveTab, string> = {
  'dashboard': 'Tableau de bord',
  'markets': 'Marchés & DQE',
  'chantiers': 'Chantiers & Équipes',
  'attachements': 'Attachements & Métrés',
  'avenants': 'Avenants & Dépassements',
  'situations': 'Situations & Facturation',
  'documents': 'Documents Officiels (PV, Ordres)',
  'logistics': 'Logistique & Stocks',
  'finance': 'Finance & Chèques DZ',
  'hr': 'Ressources Humaines & Paie',
  'ai-metre': 'IA Métreur & Scanner Devis'
};

const STORAGE_KEY = 'btp_manager_user_feedbacks';

export const TesterFeedbackWidget: React.FC<TesterFeedbackWidgetProps> = ({ activeTab }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [copied, setCopied] = useState(false);

  // Form fields
  const [userName, setUserName] = useState('');
  const [userRole, setUserRole] = useState('Conducteur de Travaux');
  const [feedbackType, setFeedbackType] = useState<'bug' | 'idea' | 'question'>('bug');
  const [feedbackModule, setFeedbackModule] = useState(MODULE_NAMES[activeTab] || 'Tableau de bord');
  const [message, setMessage] = useState('');
  const [isSuccess, setIsSuccess] = useState(false);

  // Saved feedbacks
  const [feedbacks, setFeedbacks] = useState<FeedbackItem[]>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    setFeedbackModule(MODULE_NAMES[activeTab] || 'Général');
  }, [activeTab]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(feedbacks));
  }, [feedbacks]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) return;

    const newItem: FeedbackItem = {
      id: `fb-${Date.now()}`,
      date: new Date().toLocaleString('fr-DZ', { dateStyle: 'short', timeStyle: 'short' }),
      userName: userName.trim() || 'Utilisateur Testeur',
      userRole,
      type: feedbackType,
      module: feedbackModule,
      message: message.trim()
    };

    setFeedbacks(prev => [newItem, ...prev]);
    setIsSuccess(true);
    setTimeout(() => {
      setIsSuccess(false);
      setMessage('');
      setIsOpen(false);
    }, 1800);
  };

  const generateReportText = (item?: FeedbackItem) => {
    if (item) {
      return `[ARCHILAB MANAGER] Retour Testeur :\n- Date : ${item.date}\n- Nom : ${item.userName} (${item.userRole})\n- Type : ${item.type.toUpperCase()}\n- Module : ${item.module}\n- Remarque : ${item.message}`;
    }
    return `[ARCHILAB MANAGER] Rapport de test (${feedbacks.length} retours enregistrés) :\n\n` + 
      feedbacks.map((f, i) => `${i + 1}. [${f.type.toUpperCase()} - ${f.module}] par ${f.userName} (${f.userRole}) : ${f.message}`).join('\n\n');
  };

  const handleCopyClipboard = () => {
    navigator.clipboard.writeText(generateReportText());
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleExportJSON = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(feedbacks, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `btp_manager_feedbacks_${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const handleSendWhatsApp = () => {
    const text = encodeURIComponent(
      `Bonjour, voici mon retour sur l'application ARCHILAB MANAGER :\n\n` +
      `👤 Nom : ${userName || 'Testeur'} (${userRole})\n` +
      `📌 Module : ${feedbackModule}\n` +
      `⚠️ Type : ${feedbackType === 'bug' ? 'Problème / Bug' : feedbackType === 'idea' ? 'Suggestion / Amélioration' : 'Question'}\n` +
      `📝 Message : ${message}`
    );
    window.open(`https://wa.me/?text=${text}`, '_blank');
  };

  return (
    <>
      {/* Floating Trigger Button */}
      <div className="fixed bottom-4 right-4 z-40 flex items-center gap-2">
        <button
          onClick={() => setIsOpen(true)}
          className="px-3.5 py-2.5 rounded-full bg-slate-900 hover:bg-blue-600 text-white text-xs font-bold shadow-xl border border-white/20 flex items-center gap-2 transition-all hover:scale-105 cursor-pointer"
          title="Signaler un problème ou donner votre avis pour améliorer l'application"
        >
          <span className="relative flex h-2.5 w-2.5">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
          </span>
          <MessageSquarePlus className="w-4 h-4 text-amber-300" />
          <span>Donner un avis / Signaler un problème</span>
          {feedbacks.length > 0 && (
            <span className="px-1.5 py-0.2 rounded-full bg-blue-500 text-[10px] font-mono">
              {feedbacks.length}
            </span>
          )}
        </button>
      </div>

      {/* Feedback Modal */}
      {isOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl border border-slate-200 shadow-2xl w-full max-w-lg overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
              <div className="flex items-center gap-2">
                <span className="p-2 rounded-lg bg-blue-100 text-blue-700">
                  <MessageSquarePlus className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Avis & Signalement de Problèmes (Testeurs)
                  </h3>
                  <p className="text-[11px] text-slate-500">
                    Votre avis nous aide à perfectionner l'ERP selon la réalité du terrain BTP en Algérie
                  </p>
                </div>
              </div>
              <button 
                type="button" 
                onClick={() => setIsOpen(false)}
                className="p-1 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Toggle History vs New */}
            <div className="flex border-b border-slate-100 text-xs font-bold bg-slate-50/50">
              <button
                type="button"
                onClick={() => setShowHistory(false)}
                className={`flex-1 py-2 text-center transition-colors ${!showHistory ? 'border-b-2 border-blue-600 text-blue-700 bg-white font-extrabold' : 'text-slate-500 hover:text-slate-800'}`}
              >
                Nouveau Retour / Bug
              </button>
              <button
                type="button"
                onClick={() => setShowHistory(true)}
                className={`flex-1 py-2 text-center transition-colors ${showHistory ? 'border-b-2 border-blue-600 text-blue-700 bg-white font-extrabold' : 'text-slate-500 hover:text-slate-800'}`}
              >
                Retours Enregistrés ({feedbacks.length})
              </button>
            </div>

            {!showHistory ? (
              <form onSubmit={handleSubmit} className="p-5 space-y-4 text-xs">
                {isSuccess ? (
                  <div className="p-6 text-center space-y-2 bg-emerald-50 rounded-xl border border-emerald-200 animate-in fade-in">
                    <Check className="w-10 h-10 text-emerald-600 mx-auto" />
                    <h4 className="font-bold text-sm text-emerald-900">Merci pour votre retour précieux !</h4>
                    <p className="text-xs text-emerald-700">Votre remarque a bien été enregistrée pour correction.</p>
                  </div>
                ) : (
                  <>
                    {/* Feedback Type Selection */}
                    <div>
                      <label className="font-bold text-slate-700 block mb-1.5">Nature de votre message</label>
                      <div className="grid grid-cols-3 gap-2">
                        <button
                          type="button"
                          onClick={() => setFeedbackType('bug')}
                          className={`p-2 rounded-xl border flex flex-col items-center gap-1 transition-all text-center ${
                            feedbackType === 'bug'
                              ? 'bg-rose-50 border-rose-400 text-rose-800 font-bold ring-1 ring-rose-400'
                              : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                          }`}
                        >
                          <Bug className="w-4 h-4 text-rose-600" />
                          <span className="text-[11px]">Problème / Bug</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => setFeedbackType('idea')}
                          className={`p-2 rounded-xl border flex flex-col items-center gap-1 transition-all text-center ${
                            feedbackType === 'idea'
                              ? 'bg-amber-50 border-amber-400 text-amber-800 font-bold ring-1 ring-amber-400'
                              : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                          }`}
                        >
                          <Lightbulb className="w-4 h-4 text-amber-600" />
                          <span className="text-[11px]">Amélioration</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => setFeedbackType('question')}
                          className={`p-2 rounded-xl border flex flex-col items-center gap-1 transition-all text-center ${
                            feedbackType === 'question'
                              ? 'bg-blue-50 border-blue-400 text-blue-800 font-bold ring-1 ring-blue-400'
                              : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                          }`}
                        >
                          <HelpCircle className="w-4 h-4 text-blue-600" />
                          <span className="text-[11px]">Question / Autre</span>
                        </button>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label className="font-bold text-slate-700 block mb-1">Votre Nom & Prénom</label>
                        <input
                          type="text"
                          placeholder="ex: Mourad (ou Anonyme)"
                          value={userName}
                          onChange={(e) => setUserName(e.target.value)}
                          className="w-full p-2 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none"
                        />
                      </div>

                      <div>
                        <label className="font-bold text-slate-700 block mb-1">Votre Rôle BTP</label>
                        <select
                          value={userRole}
                          onChange={(e) => setUserRole(e.target.value)}
                          className="w-full p-2 rounded-xl border border-slate-200 bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                        >
                          <option value="Conducteur de Travaux">Conducteur de Travaux</option>
                          <option value="Chef de Chantier">Chef de Chantier</option>
                          <option value="Ingénieur / Métreur BTP">Ingénieur / Métreur BTP</option>
                          <option value="Directeur d'Entreprise BTP">Directeur d'Entreprise BTP</option>
                          <option value="Comptable / Gestionnaire Paie">Comptable / Gestionnaire Paie</option>
                          <option value="Bureau d'Études (BET)">Bureau d'Études (BET)</option>
                          <option value="Autre">Autre</option>
                        </select>
                      </div>
                    </div>

                    <div>
                      <label className="font-bold text-slate-700 block mb-1">Module concerné</label>
                      <input
                        type="text"
                        value={feedbackModule}
                        onChange={(e) => setFeedbackModule(e.target.value)}
                        className="w-full p-2 rounded-xl border border-slate-200 bg-slate-50 font-medium text-slate-700"
                      />
                    </div>

                    <div>
                      <label className="font-bold text-slate-700 block mb-1">
                        Décrivez le problème rencontré ou votre suggestion *
                      </label>
                      <textarea
                        required
                        rows={4}
                        placeholder="Expliquez ce qui ne fonctionne pas ou ce qui manque selon vos habitudes de travail (ex: calcul TVA, saisie métré, format impression PDF, etc.)..."
                        value={message}
                        onChange={(e) => setMessage(e.target.value)}
                        className="w-full p-2.5 rounded-xl border border-slate-200 focus:ring-2 focus:ring-blue-500 focus:outline-none resize-none"
                      />
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-100">
                      {message.trim() && (
                        <button
                          type="button"
                          onClick={handleSendWhatsApp}
                          className="px-3 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold flex items-center gap-1.5 transition-colors shadow-xs"
                          title="Envoyer directement ce retour par WhatsApp"
                        >
                          <Share2 className="w-3.5 h-3.5" />
                          <span>Envoyer WhatsApp</span>
                        </button>
                      )}

                      <div className="flex items-center gap-2 ml-auto">
                        <button
                          type="button"
                          onClick={() => setIsOpen(false)}
                          className="px-4 py-2 border border-slate-200 text-slate-600 rounded-xl hover:bg-slate-50 transition-colors"
                        >
                          Fermer
                        </button>
                        <button
                          type="submit"
                          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold flex items-center gap-1.5 transition-colors shadow-sm"
                        >
                          <Send className="w-3.5 h-3.5" />
                          <span>Enregistrer l'Avis</span>
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </form>
            ) : (
              <div className="p-5 space-y-4 text-xs max-h-[60vh] overflow-y-auto">
                {feedbacks.length === 0 ? (
                  <p className="text-center text-slate-400 py-8">Aucun retour enregistré pour le moment.</p>
                ) : (
                  <>
                    <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                      <span className="font-bold text-slate-700">{feedbacks.length} avis récolté(s)</span>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={handleCopyClipboard}
                          className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg font-medium flex items-center gap-1 transition-colors"
                        >
                          <Copy className="w-3.5 h-3.5" />
                          <span>{copied ? 'Copié !' : 'Copier tout'}</span>
                        </button>
                        <button
                          onClick={handleExportJSON}
                          className="px-2.5 py-1 bg-blue-50 hover:bg-blue-100 text-blue-700 rounded-lg font-medium flex items-center gap-1 transition-colors"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>Export JSON</span>
                        </button>
                      </div>
                    </div>

                    <div className="space-y-2.5">
                      {feedbacks.map(item => (
                        <div key={item.id} className="p-3 rounded-xl border border-slate-200 bg-slate-50/50 space-y-1.5">
                          <div className="flex items-center justify-between text-[11px]">
                            <div className="flex items-center gap-1.5">
                              <span className={`px-1.5 py-0.2 rounded font-bold uppercase text-[9px] ${
                                item.type === 'bug' ? 'bg-rose-100 text-rose-800' :
                                item.type === 'idea' ? 'bg-amber-100 text-amber-800' : 'bg-blue-100 text-blue-800'
                              }`}>
                                {item.type}
                              </span>
                              <strong className="text-slate-800">{item.userName}</strong>
                              <span className="text-slate-400">({item.userRole})</span>
                            </div>
                            <span className="text-[10px] text-slate-400 font-mono">{item.date}</span>
                          </div>

                          <div className="text-[11px] font-semibold text-blue-700">
                            Module : {item.module}
                          </div>

                          <p className="text-xs text-slate-700 bg-white p-2 rounded-lg border border-slate-100">
                            {item.message}
                          </p>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
};