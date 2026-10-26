import React, { useState, useEffect, useRef } from 'react';
import { 
  Building2, 
  Activity, 
  ShieldCheck, 
  Layers, 
  Table, 
  FileText, 
  CheckCircle2, 
  Download, 
  Sliders, 
  Eye, 
  RefreshCw, 
  Columns, 
  Maximize2,
  AlertTriangle,
  Play
} from 'lucide-react';

// Normative parameters
const ZONES = [
  { id: 'ZONE_0', name: 'Zone 0 (Très faible)', coeffA: 0.00 },
  { id: 'ZONE_I', name: 'Zone I (Faible)', coeffA: 0.07 },
  { id: 'ZONE_II', name: 'Zone II (Moyenne)', coeffA: 0.10 },
  { id: 'ZONE_III', name: 'Zone III (Élevée - Sidi Bel Abbès)', coeffA: 0.15 },
  { id: 'ZONE_IV', name: 'Zone IV (Forte)', coeffA: 0.20 },
  { id: 'ZONE_V', name: 'Zone V (Très forte)', coeffA: 0.25 },
];

const SITES = [
  { id: 'S1', name: 'S1 - Rocher (S = 1.00, T2 = 0.20s)', S: 1.00, T1: 0.05, T2: 0.20, T3: 1.20 },
  { id: 'S2', name: 'S2 - Ferme (S = 1.25, T2 = 0.30s)', S: 1.25, T1: 0.08, T2: 0.30, T3: 1.20 },
  { id: 'S3', name: 'S3 - Meuble (S = 1.55, T2 = 0.40s - Bosquet)', S: 1.55, T1: 0.10, T2: 0.40, T3: 1.20 },
  { id: 'S4', name: 'S4 - Très meuble (S = 1.80, T2 = 0.50s)', S: 1.80, T1: 0.12, T2: 0.50, T3: 1.20 },
  { id: 'S5', name: 'S5 - Spécial (S = 2.00, T2 = 0.60s)', S: 2.00, T1: 0.15, T2: 0.60, T3: 1.20 },
];

const GROUPS = [
  { id: '1A', name: 'Groupe 1A : Bâtiment vital (I = 1.40 - Sûreté Urbaine)', coeffI: 1.40, nuA: 0.46 },
  { id: '1B', name: 'Groupe 1B : Grande importance (I = 1.20)', coeffI: 1.20, nuA: 0.46 },
  { id: '2', name: 'Groupe 2 : Usage courant (I = 1.00)', coeffI: 1.00, nuA: 0.55 },
  { id: '3', name: 'Groupe 3 : Faible importance (I = 0.80)', coeffI: 0.80, nuA: 0.69 },
];

export const CivilWorkAGView: React.FC = () => {
  const [activeSubTab, setActiveSubTab] = useState<'3d' | 'spectrum' | 'frames' | 'walls' | 'foundations' | 'ctc'>('spectrum');

  // Seismic parameters state
  const [selectedZone, setSelectedZone] = useState('ZONE_III');
  const [selectedSite, setSelectedSite] = useState('S3');
  const [selectedGroup, setSelectedGroup] = useState('1A');
  const [behaviorR, setBehaviorR] = useState(3.5);
  const [qualityQ, setQualityQ] = useState(1.10);
  const [dampingPercent, setDampingPercent] = useState(7.0);

  // Canvas ref for spectrum
  const canvasRef = useRef<HTMLCanvasElement>(null);
  // Canvas ref for 3D model
  const canvas3DRef = useRef<HTMLCanvasElement>(null);

  const zone = ZONES.find(z => z.id === selectedZone) || ZONES[3];
  const site = SITES.find(s => s.id === selectedSite) || SITES[2];
  const group = GROUPS.find(g => g.id === selectedGroup) || GROUPS[0];

  // Damping correction eta
  const eta = Math.sqrt(7.0 / (2.0 + dampingPercent));
  const minThreshold = 0.20 * zone.coeffA * group.coeffI;

  // Spectrum calculation function
  const calculateSadOverG = (T: number): number => {
    if (T <= 0) return 0;
    const { T1, T2, T3, S } = site;
    const A = zone.coeffA;
    const I = group.coeffI;
    const R = behaviorR;
    const Q = qualityQ;

    let SaeOverG: number;
    if (T < T1) {
      SaeOverG = A * I * (1.0 + (T / T1) * (2.5 * eta * S - 1.0));
    } else if (T <= T2) {
      SaeOverG = 2.5 * A * I * eta * S;
    } else if (T <= T3) {
      SaeOverG = 2.5 * A * I * eta * S * (T2 / T);
    } else {
      SaeOverG = 2.5 * A * I * eta * S * (T2 / T3) * Math.pow(T3 / T, 2.0);
    }

    const SadOverG = (SaeOverG * (Q / R));
    return Math.max(SadOverG, minThreshold);
  };

  // Draw Spectrum Chart
  useEffect(() => {
    if (activeSubTab !== 'spectrum') return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;
    ctx.clearRect(0, 0, width, height);

    // Padding
    const padL = 60, padR = 30, padT = 30, padB = 40;
    const plotW = width - padL - padR;
    const plotH = height - padT - padB;

    const maxT = 3.0;
    const maxY = 0.40;

    // Grid lines
    ctx.strokeStyle = '#e2e8f0';
    ctx.lineWidth = 1;
    for (let t = 0.5; t <= maxT; t += 0.5) {
      const x = padL + (t / maxT) * plotW;
      ctx.beginPath();
      ctx.moveTo(x, padT);
      ctx.lineTo(x, padT + plotH);
      ctx.stroke();

      ctx.fillStyle = '#64748b';
      ctx.font = '10px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(`${t}s`, x, padT + plotH + 18);
    }

    for (let y = 0.05; y <= maxY; y += 0.05) {
      const py = padT + plotH - (y / maxY) * plotH;
      ctx.beginPath();
      ctx.moveTo(padL, py);
      ctx.lineTo(padL + plotW, py);
      ctx.stroke();

      ctx.fillStyle = '#64748b';
      ctx.font = '10px sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(y.toFixed(2), padL - 8, py + 3);
    }

    // Axes
    ctx.strokeStyle = '#334155';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(padL, padT);
    ctx.lineTo(padL, padT + plotH);
    ctx.lineTo(padL + plotW, padT + plotH);
    ctx.stroke();

    // Min Threshold Line (0.20 A I)
    const thresholdY = padT + plotH - (minThreshold / maxY) * plotH;
    ctx.strokeStyle = '#ef4444';
    ctx.setLineDash([4, 4]);
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(padL, thresholdY);
    ctx.lineTo(padL + plotW, thresholdY);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.fillStyle = '#ef4444';
    ctx.font = 'bold 10px sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(`0.20·A·I = ${minThreshold.toFixed(3)}g (Seuil plancher)`, padL + 10, thresholdY - 6);

    // Spectrum Curve Sad/g
    ctx.strokeStyle = '#2563eb';
    ctx.lineWidth = 3;
    ctx.beginPath();
    const steps = 150;
    for (let i = 0; i <= steps; i++) {
      const t = (i / steps) * maxT;
      const sad = calculateSadOverG(t);
      const x = padL + (t / maxT) * plotW;
      const y = padT + plotH - Math.min(sad / maxY, 1.0) * plotH;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();

    // Fill under curve
    ctx.lineTo(padL + plotW, padT + plotH);
    ctx.lineTo(padL, padT + plotH);
    ctx.fillStyle = 'rgba(37, 99, 235, 0.08)';
    ctx.fill();

    // Operating point for Sidi Bel Abbès (T0 = 0.399s)
    const opT = 0.399;
    const opSad = calculateSadOverG(opT);
    const opX = padL + (opT / maxT) * plotW;
    const opY = padT + plotH - (opSad / maxY) * plotH;

    ctx.fillStyle = '#10b981';
    ctx.beginPath();
    ctx.arc(opX, opY, 6, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2;
    ctx.stroke();

    ctx.fillStyle = '#0f172a';
    ctx.font = 'bold 11px sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(`Mode 1 : T = ${opT}s, Sad/g = ${opSad.toFixed(3)}`, opX + 10, opY - 6);

  }, [activeSubTab, selectedZone, selectedSite, selectedGroup, behaviorR, qualityQ, dampingPercent]);

  // Draw 3D Isometric Structure Wireframe
  useEffect(() => {
    if (activeSubTab !== '3d') return;
    const canvas = canvas3DRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    const cx = w / 2;
    const cy = h / 2 + 30;

    const iso = (x: number, y: number, z: number) => {
      const scale = 11;
      const isoX = cx + (x - y) * Math.cos(Math.PI / 6) * scale;
      const isoY = cy + (x + y) * Math.sin(Math.PI / 6) * scale - z * scale * 1.5;
      return { x: isoX, y: isoY };
    };

    // Draw Ground Grid / Soil Foundation
    ctx.strokeStyle = '#e2e8f0';
    ctx.lineWidth = 1;
    for (let x = -8; x <= 8; x += 4) {
      const p1 = iso(x, -10, 0);
      const p2 = iso(x, 10, 0);
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.stroke();
    }
    for (let y = -10; y <= 10; y += 4) {
      const p1 = iso(-8, y, 0);
      const p2 = iso(8, y, 0);
      ctx.beginPath();
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
      ctx.stroke();
    }

    // Story levels: 0.0, 3.74, 7.48, 11.22
    const heights = [0.0, 3.74, 7.48, 11.22];
    const colCoords = [
      [-6, -8], [-6, 0], [-6, 8],
      [0, -8], [0, 0], [0, 8],
      [6, -8], [6, 0], [6, 8]
    ];

    // Foundation strip beams (SAFE SFJ200)
    ctx.strokeStyle = '#059669';
    ctx.lineWidth = 4;
    colCoords.forEach(([x, y]) => {
      const p = iso(x, y, 0);
      ctx.fillStyle = '#10b981';
      ctx.fillRect(p.x - 6, p.y - 4, 12, 8);
    });

    // Draw Columns (RDC to 2nd)
    colCoords.forEach(([x, y]) => {
      for (let s = 0; s < 3; s++) {
        const pBot = iso(x, y, heights[s]);
        const pTop = iso(x, y, heights[s + 1]);
        ctx.strokeStyle = s === 0 ? '#1e3a8a' : '#3b82f6';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.moveTo(pBot.x, pBot.y);
        ctx.lineTo(pTop.x, pTop.y);
        ctx.stroke();
      }
    });

    // Draw Beams at each floor level
    heights.slice(1).forEach((z, sIdx) => {
      ctx.strokeStyle = sIdx === 2 ? '#9333ea' : '#475569';
      ctx.lineWidth = 2;

      // X beams
      [-8, 0, 8].forEach(y => {
        const p1 = iso(-6, y, z);
        const p2 = iso(6, y, z);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
      });

      // Y beams
      [-6, 0, 6].forEach(x => {
        const p1 = iso(x, -8, z);
        const p2 = iso(x, 8, z);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();
      });
    });

    // Draw Shear Walls V1, V5, V6 (Filled translucent polygons)
    const drawWall = (x1: number, y1: number, x2: number, y2: number, h: number, name: string) => {
      const b1 = iso(x1, y1, 0);
      const b2 = iso(x2, y2, 0);
      const t2 = iso(x2, y2, h);
      const t1 = iso(x1, y1, h);

      ctx.fillStyle = 'rgba(239, 68, 68, 0.35)';
      ctx.strokeStyle = '#dc2626';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(b1.x, b1.y);
      ctx.lineTo(b2.x, b2.y);
      ctx.lineTo(t2.x, t2.y);
      ctx.lineTo(t1.x, t1.y);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();

      ctx.fillStyle = '#991b1b';
      ctx.font = 'bold 11px sans-serif';
      ctx.fillText(name, (b1.x + t2.x) / 2 - 10, (b1.y + t2.y) / 2);
    };

    drawWall(-6, -4, -6, 4, 11.22, 'V1 (lw=1.40m)');
    drawWall(6, -4, 6, 4, 11.22, 'V6 (lw=1.40m)');
    drawWall(-3, 8, 3, 8, 11.22, 'V5 (lw=1.40m)');

  }, [activeSubTab]);

  return (
    <div className="space-y-6">
      {/* Top Banner Official CTC Algeria */}
      <div className="rounded-3xl bg-gradient-to-r from-slate-900 via-blue-950 to-indigo-950 text-white p-6 sm:p-8 shadow-2xl border border-blue-500/30 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-96 h-96 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-blue-500/20 text-blue-300 border border-blue-400/30">
                RÉPUBLIQUE ALGÉRIENNE DÉMOCRATIQUE ET POPULAIRE
              </span>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-black uppercase tracking-wider bg-emerald-500/20 text-emerald-300 border border-emerald-400/30">
                CONFORME DTR BC 2.48 (RPA 2024)
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-white flex items-center gap-3">
              🏛️ ARCHILAB CIVILWORK AG
            </h1>
            <p className="text-slate-300 text-sm mt-1 max-w-2xl">
              Plateforme cloud d'analyse parasismique, dimensionnement béton armé (BAEL 91 / RPA 2024) et génération officielle de Notes de Calcul certifiées CTC Algérie.
            </p>
            <div className="flex flex-wrap items-center gap-4 mt-4 text-xs text-slate-300">
              <span className="flex items-center gap-1.5 font-bold text-white">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" /> Projet : Sûreté Urbaine 1500 LLV Bosquet
              </span>
              <span className="text-slate-400">|</span>
              <span>Wilaya : Sidi Bel Abbès (Zone III)</span>
              <span className="text-slate-400">|</span>
              <span className="text-emerald-400 font-bold">Avis CTC : Favorable Sans Réserves (CV)</span>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <button
              onClick={() => setActiveSubTab('ctc')}
              className="px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold text-xs shadow-lg shadow-blue-500/30 flex items-center gap-2 transition-all"
            >
              <FileText className="w-4 h-4" />
              Note de Calcul CTC (.docx)
            </button>
            <button
              onClick={() => setActiveSubTab('3d')}
              className="px-4 py-2.5 rounded-xl bg-white/10 hover:bg-white/20 text-white font-bold text-xs border border-white/20 flex items-center gap-2 transition-all backdrop-blur-md"
            >
              <Eye className="w-4 h-4" />
              Modèle 3D Topologique
            </button>
          </div>
        </div>
      </div>

      {/* Sub-Navigation Tabs */}
      <div className="flex overflow-x-auto gap-2 p-1.5 bg-white/80 dark:bg-slate-900/80 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm backdrop-blur-md">
        {[
          { id: 'spectrum', label: '1. Spectre RPA 2024', icon: Activity },
          { id: '3d', label: '2. Géométrie & Modèle 3D', icon: Layers },
          { id: 'frames', label: '3. Poteaux & Poutres (BAEL)', icon: Columns },
          { id: 'walls', label: '4. Voiles § 7.7 RPA', icon: Building2 },
          { id: 'foundations', label: '5. Fondations SAFE', icon: Maximize2 },
          { id: 'ctc', label: '6. Rapport CTC (13 Ch.)', icon: FileText },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveSubTab(tab.id as any)}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold whitespace-nowrap transition-all ${
              activeSubTab === tab.id
                ? 'bg-blue-600 text-white shadow-md shadow-blue-500/25'
                : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            <tab.icon className="w-4 h-4" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* ======================================================== */}
      {/* MODULE 1 : SPECTRE PARASISMIQUE RPA 2024                */}
      {/* ======================================================== */}
      {activeSubTab === 'spectrum' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Controls Panel */}
          <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <h2 className="text-base font-black text-slate-900 dark:text-white flex items-center gap-2">
              <Sliders className="w-5 h-5 text-blue-600" />
              Paramètres RPA 2024 (§ 3.3)
            </h2>

            <div>
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Zone Sismique</label>
              <select
                value={selectedZone}
                onChange={e => setSelectedZone(e.target.value)}
                className="w-full mt-1 px-3 py-2 rounded-xl text-xs bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-900 dark:text-white focus:ring-2 focus:ring-blue-500"
              >
                {ZONES.map(z => (
                  <option key={z.id} value={z.id}>{z.name} (A = {z.coeffA})</option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Classification du Sol</label>
              <select
                value={selectedSite}
                onChange={e => setSelectedSite(e.target.value)}
                className="w-full mt-1 px-3 py-2 rounded-xl text-xs bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-900 dark:text-white focus:ring-2 focus:ring-blue-500"
              >
                {SITES.map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Groupe d'Usage</label>
              <select
                value={selectedGroup}
                onChange={e => setSelectedGroup(e.target.value)}
                className="w-full mt-1 px-3 py-2 rounded-xl text-xs bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-900 dark:text-white focus:ring-2 focus:ring-blue-500"
              >
                {GROUPS.map(g => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-3 pt-2">
              <div>
                <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Facteur R</label>
                <input
                  type="number"
                  step="0.1"
                  value={behaviorR}
                  onChange={e => setBehaviorR(parseFloat(e.target.value) || 3.5)}
                  className="w-full mt-1 px-3 py-2 rounded-xl text-xs bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-900 dark:text-white"
                />
              </div>
              <div>
                <label className="text-xs font-bold text-slate-700 dark:text-slate-300">Qualité Q</label>
                <input
                  type="number"
                  step="0.05"
                  value={qualityQ}
                  onChange={e => setQualityQ(parseFloat(e.target.value) || 1.10)}
                  className="w-full mt-1 px-3 py-2 rounded-xl text-xs bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 text-slate-900 dark:text-white"
                />
              </div>
            </div>

            <div className="p-3 rounded-2xl bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-900 text-xs space-y-1">
              <div className="flex justify-between font-bold text-blue-900 dark:text-blue-300">
                <span>Amortissement η :</span>
                <span>{eta.toFixed(3)} (ξ = {dampingPercent}%)</span>
              </div>
              <div className="flex justify-between text-slate-600 dark:text-slate-400">
                <span>Plancher Minimal 0.20·A·I :</span>
                <span className="font-bold text-rose-600 dark:text-rose-400">{minThreshold.toFixed(3)}g</span>
              </div>
              <div className="flex justify-between text-slate-600 dark:text-slate-400">
                <span>Périodes caractéristiques :</span>
                <span>T1={site.T1}s, T2={site.T2}s, T3={site.T3}s</span>
              </div>
            </div>
          </div>

          {/* Interactive Chart & Modal Table */}
          <div className="lg:col-span-2 space-y-6">
            <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-black text-slate-900 dark:text-white flex items-center gap-2">
                  <Activity className="w-4 h-4 text-blue-600" />
                  Spectre de Calcul Sad/g (Équation 3.15 RPA 2024)
                </h3>
                <span className="text-[11px] font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/40 px-2.5 py-1 rounded-full border border-emerald-500/20">
                  Calibré : T0 = 0.399s → Sad/g = {calculateSadOverG(0.399).toFixed(3)}
                </span>
              </div>

              <div className="w-full overflow-x-auto">
                <canvas
                  ref={canvasRef}
                  width={650}
                  height={260}
                  className="w-full max-w-full h-auto bg-slate-50 dark:bg-slate-950 rounded-2xl border border-slate-200 dark:border-slate-800"
                />
              </div>
            </div>

            {/* Modal Participation Table */}
            <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl overflow-hidden">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-black text-slate-900 dark:text-white">
                  Complétude Modale ETABS (8 modes analysés - RPA § 4.3.3)
                </h3>
                <span className="text-xs font-bold text-emerald-600 dark:text-emerald-400 flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5" /> ΣUx = 93.92% & ΣUy = 93.88% ≥ 90% (CV)
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 font-bold uppercase text-[10px]">
                    <tr>
                      <th className="p-2.5">Mode</th>
                      <th className="p-2.5">Période (s)</th>
                      <th className="p-2.5">Ux (%)</th>
                      <th className="p-2.5">Uy (%)</th>
                      <th className="p-2.5">Rz (%)</th>
                      <th className="p-2.5">ΣUx (%)</th>
                      <th className="p-2.5">ΣUy (%)</th>
                      <th className="p-2.5 text-center">Conformité</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800 text-slate-700 dark:text-slate-300">
                    <tr className="hover:bg-blue-50/50 dark:hover:bg-blue-950/20 font-bold">
                      <td className="p-2.5">1</td>
                      <td className="p-2.5 text-blue-600">0.399</td>
                      <td className="p-2.5">73.17</td>
                      <td className="p-2.5">0.68</td>
                      <td className="p-2.5">1.06</td>
                      <td className="p-2.5">73.17</td>
                      <td className="p-2.5">0.68</td>
                      <td className="p-2.5 text-center text-blue-600 font-black">Translation X</td>
                    </tr>
                    <tr className="hover:bg-blue-50/50 dark:hover:bg-blue-950/20 font-bold">
                      <td className="p-2.5">2</td>
                      <td className="p-2.5 text-blue-600">0.295</td>
                      <td className="p-2.5">1.12</td>
                      <td className="p-2.5">70.32</td>
                      <td className="p-2.5">3.40</td>
                      <td className="p-2.5">74.29</td>
                      <td className="p-2.5">71.00</td>
                      <td className="p-2.5 text-center text-indigo-600 font-black">Translation Y</td>
                    </tr>
                    <tr className="hover:bg-blue-50/50 dark:hover:bg-blue-950/20">
                      <td className="p-2.5">3</td>
                      <td className="p-2.5">0.266</td>
                      <td className="p-2.5">0.10</td>
                      <td className="p-2.5">3.19</td>
                      <td className="p-2.5">61.00</td>
                      <td className="p-2.5">74.38</td>
                      <td className="p-2.5">74.18</td>
                      <td className="p-2.5 text-center text-amber-600">Torsion</td>
                    </tr>
                    <tr className="bg-emerald-50/60 dark:bg-emerald-950/30 font-bold text-emerald-900 dark:text-emerald-300">
                      <td className="p-2.5">8</td>
                      <td className="p-2.5">0.073</td>
                      <td className="p-2.5">0.71</td>
                      <td className="p-2.5">14.30</td>
                      <td className="p-2.5">0.59</td>
                      <td className="p-2.5">93.92</td>
                      <td className="p-2.5">93.88</td>
                      <td className="p-2.5 text-center text-emerald-600 font-black">≥ 90% (CV)</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODULE 2 : GÉOMÉTRIE ET MODÈLE 3D                        */}
      {/* ======================================================== */}
      {activeSubTab === '3d' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-black text-slate-900 dark:text-white flex items-center gap-2">
                <Eye className="w-5 h-5 text-blue-600" />
                Vérification Topologique 3D (ETABS → Archilab)
              </h2>
              <span className="text-xs text-slate-500 font-semibold">Joint A (Lx = 15.00m, Ly = 20.65m, Hn = 11.22m)</span>
            </div>

            <div className="w-full overflow-hidden bg-slate-950 rounded-2xl border border-slate-800 p-2 flex justify-center">
              <canvas
                ref={canvas3DRef}
                width={700}
                height={380}
                className="w-full max-w-full h-auto"
              />
            </div>
          </div>

          <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <h3 className="text-sm font-black text-slate-900 dark:text-white flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              Contrôles de Coffrage (Figures 7.1 & 7.4 RPA)
            </h3>

            <div className="space-y-3 text-xs">
              <div className="p-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-500/20">
                <p className="font-bold text-emerald-800 dark:text-emerald-300">Poteau C111 (POT 40x50)</p>
                <p className="text-slate-600 dark:text-slate-400">Min(b1, h1) = 40 cm ≥ 25 cm | lcl/20 = 16.2 cm (CV)</p>
                <span className="text-[10px] font-black text-emerald-600 bg-emerald-100 dark:bg-emerald-900 px-2 py-0.5 rounded-full mt-1 inline-block">
                  ν = 0.299 ≤ 0.35 (CV)
                </span>
              </div>

              <div className="p-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-500/20">
                <p className="font-bold text-emerald-800 dark:text-emerald-300">Poutre Principale B1 (PR 30x55)</p>
                <p className="text-slate-600 dark:text-slate-400">b = 30 cm ≥ 20 cm | h = 55 cm ≥ 30 cm | h/b = 1.83 ≤ 4.0</p>
                <span className="text-[10px] font-black text-emerald-600 bg-emerald-100 dark:bg-emerald-900 px-2 py-0.5 rounded-full mt-1 inline-block">
                  Rigidité L/15 ≤ hp ≤ L/10 (CV)
                </span>
              </div>

              <div className="p-3 rounded-2xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-500/20">
                <p className="font-bold text-emerald-800 dark:text-emerald-300">Trumeau V6 (1.40 x 0.30 m)</p>
                <p className="text-slate-600 dark:text-slate-400">lw = 1.40 m ≥ 4·bw (1.20 m) | he/20 = 17 cm ≤ 30 cm</p>
                <span className="text-[10px] font-black text-emerald-600 bg-emerald-100 dark:bg-emerald-900 px-2 py-0.5 rounded-full mt-1 inline-block">
                  Qualification Voile Validée (CV)
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODULE 3 : FERRAILLAGE POTEAUX & POUTRES                */}
      {/* ======================================================== */}
      {activeSubTab === 'frames' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Poteaux */}
          <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <h3 className="text-sm font-black text-slate-900 dark:text-white flex items-center justify-between">
              <span>Ferraillage Poteaux (Flexion Composée BAEL 91)</span>
              <span className="text-xs text-blue-600 font-bold">12T14 Adopté</span>
            </h3>

            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Poteau C111 (40x50 cm) :</span>
                <span className="font-bold">Nd = 1495.43 kN, Mcorr = 52.0 kNm</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Section d'acier réelle :</span>
                <span className="font-bold text-emerald-600">12T14 (18.48 cm²) ≥ Amin RPA (18.00 cm²)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Zone nodale confinée :</span>
                <span className="font-bold">Lcr = 60 cm, t = 10 cm, t' = 15 cm</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Longueur de recouvrement :</span>
                <span className="font-bold">Lrec = 70 cm (50·Φ)</span>
              </div>
            </div>

            <div className="w-full h-32 bg-slate-100 dark:bg-slate-950 rounded-2xl flex items-center justify-center p-4 border border-dashed border-slate-300 dark:border-slate-700">
              <div className="w-28 h-20 border-4 border-slate-800 dark:border-slate-400 relative rounded-md flex items-center justify-center">
                <span className="text-[10px] font-black text-slate-500">40 x 50</span>
                <div className="absolute top-1 left-1 w-2.5 h-2.5 rounded-full bg-blue-600" />
                <div className="absolute top-1 right-1 w-2.5 h-2.5 rounded-full bg-blue-600" />
                <div className="absolute bottom-1 left-1 w-2.5 h-2.5 rounded-full bg-blue-600" />
                <div className="absolute bottom-1 right-1 w-2.5 h-2.5 rounded-full bg-blue-600" />
                <div className="absolute top-1 left-1/3 w-2 h-2 rounded-full bg-blue-600" />
                <div className="absolute top-1 right-1/3 w-2 h-2 rounded-full bg-blue-600" />
                <div className="absolute bottom-1 left-1/3 w-2 h-2 rounded-full bg-blue-600" />
                <div className="absolute bottom-1 right-1/3 w-2 h-2 rounded-full bg-blue-600" />
                <div className="absolute left-1 top-1/2 -translate-y-1/2 w-2 h-2 rounded-full bg-blue-600" />
                <div className="absolute right-1 top-1/2 -translate-y-1/2 w-2 h-2 rounded-full bg-blue-600" />
              </div>
            </div>
          </div>

          {/* Poutres */}
          <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
            <h3 className="text-sm font-black text-slate-900 dark:text-white flex items-center justify-between">
              <span>Poutres Principales (4 Contrôles Effort Tranchant)</span>
              <span className="text-xs text-blue-600 font-bold">PR 30x55 (3T14 + 3T14)</span>
            </h3>

            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">Sollicitations d'appui :</span>
                <span className="font-bold">Mu = 161.70 kNm, Vu = 178.0 kN</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">1. Contrainte de cisaillement :</span>
                <span className="font-bold text-emerald-600">τu = 1.14 MPa ≤ 3.33 MPa (CV)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">2. Compression bielle béton :</span>
                <span className="font-bold text-emerald-600">Vu ≤ 911.14 kN (CV)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">3. Ancrage acier aux appuis :</span>
                <span className="font-bold text-emerald-600">As_fournie (9.24 cm²) ≥ 4.09 cm² (CV)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-600 dark:text-slate-400">4. Pression diamétrale coude :</span>
                <span className="font-bold text-emerald-600">σmb = 2.61 MPa ≤ 21.67 MPa (CV)</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODULE 4 : VOILES SELON RPA 2024 § 7.7                   */}
      {/* ======================================================== */}
      {activeSubTab === 'walls' && (
        <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-6">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-black text-slate-900 dark:text-white">
              Conception Parasismique des Voiles de Contreventement (RPA 2024 § 7.7)
            </h3>
            <span className="text-xs font-bold px-3 py-1 bg-emerald-50 text-emerald-600 rounded-full border border-emerald-500/20">
              Trumeaux V1, V5, V6 Validés
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 space-y-1">
              <p className="text-slate-500 font-semibold">Effort Normal Réduit</p>
              <p className="text-lg font-black text-emerald-600">νd = 0.255 ≤ 0.40</p>
              <p className="text-[11px] text-slate-500">Nd_max = 2675.81 kN, Bc = 4200 cm²</p>
            </div>
            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 space-y-1">
              <p className="text-slate-500 font-semibold">Cisaillement Conventionnel</p>
              <p className="text-lg font-black text-emerald-600">τb = 2.78 MPa ≤ 5.00 MPa</p>
              <p className="text-[11px] text-slate-500">1.40·Vu = 1050.52 kN, d = 0.90·lw</p>
            </div>
            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 space-y-1">
              <p className="text-slate-500 font-semibold">Ductilité Locale Rives</p>
              <p className="text-lg font-black text-emerald-600">ωwd = 0.4169 ≥ 0.1969</p>
              <p className="text-[11px] text-slate-500">Rives lc = 60 cm (16T14, St = 8 cm)</p>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODULE 5 : FONDATIONS SAFE                              */}
      {/* ======================================================== */}
      {activeSubTab === 'foundations' && (
        <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 border border-slate-200 dark:border-slate-800 shadow-xl space-y-4">
          <h3 className="text-sm font-black text-slate-900 dark:text-white">
            Vérification Sol-Structure SAFE v12 (Modèle de Winkler)
          </h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 space-y-2">
              <p className="font-bold text-slate-800 dark:text-slate-200">Semelle de Joint Sismique SFJ200 (b = 2.00 m)</p>
              <p className="text-emerald-600 font-black">σeff = 109.24 kPa ≤ σsol_adm = 120.00 kPa (CV)</p>
              <p className="text-slate-600 dark:text-slate-400">Appui BaelR : Mu = 1135 kNm → 27.97 cm² (12T16 + 6T14)</p>
              <p className="text-slate-600 dark:text-slate-400">Travée BaelT : Mu = 126 kNm → 2.97 cm² (3T12)</p>
            </div>
            <div className="p-4 rounded-2xl bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 space-y-2">
              <p className="font-bold text-slate-800 dark:text-slate-200">Semelle Courante SF120 (b = 1.20 m)</p>
              <p className="text-emerald-600 font-black">σeff = 87.40 kPa ≤ σsol_adm = 120.00 kPa (CV)</p>
              <p className="text-slate-600 dark:text-slate-400">Appui BaelR : Mu = 313 kNm → 7.57 cm² (6T14)</p>
              <p className="text-slate-600 dark:text-slate-400">Patin transversal : Méthode des bielles → T12 e=15 cm</p>
            </div>
          </div>
        </div>
      )}

      {/* ======================================================== */}
      {/* MODULE 6 : NOTE DE CALCUL CTC OFFICIELLE                 */}
      {/* ======================================================== */}
      {activeSubTab === 'ctc' && (
        <div className="bg-white dark:bg-slate-900 rounded-3xl p-6 sm:p-8 border border-slate-200 dark:border-slate-800 shadow-xl space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 dark:border-slate-800 pb-6">
            <div>
              <span className="text-[10px] font-black uppercase text-blue-600 tracking-wider">
                DOCX NATIF AVEC FORMULES OMML VECTORIELLES
              </span>
              <h2 className="text-lg font-black text-slate-900 dark:text-white mt-1">
                Note de Calcul Structurale Officielle (13 Chapitres CTC)
              </h2>
              <p className="text-xs text-slate-500 mt-1">
                Réf : BET ARCHILAB / JOINT A / SÛRETÉ URBAINE 1500 LLV BOSQUET (SIDI BEL ABBÈS)
              </p>
            </div>

            <a
              href="https://github.com/luxecham/Archilab-RPA2024-Studio"
              target="_blank"
              rel="noreferrer"
              className="px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs flex items-center gap-2 shadow-lg shadow-emerald-500/25 transition-all self-start sm:self-auto"
            >
              <Download className="w-4 h-4" />
              Télécharger Dossier Complet (.docx / .pdf)
            </a>
          </div>

          {/* 13 Chapters List Preview */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            {[
              "1. Description du projet et normes appliquées (RPA 2024, CBA 93, BAEL 91)",
              "2. Caractéristiques géométriques du bâtiment (Niveaux, portées libres, axes)",
              "3. Caractéristiques mécaniques des matériaux (Béton C25/30, Acier Fe E500, Sol)",
              "4. Combinaisons de charges (ELU, ELS, Sismiques W = G + 0.3Q, COMB1 à 8)",
              "5. Descente de charges DTR BC 2.2 (Terrasse G=6.75 kN/m², Q=1.00 kN/m²)",
              "6. Prédimensionnement des poutres selon Figure 7.4 RPA 2024 (PR 30x55, PR 30x35)",
              "7. Prédimensionnement des poteaux selon Figure 7.1 RPA 2024 (POT 40x50, POT 35x55)",
              "8. Planchers corps creux (16+5 cm), poutrelles continues en Té KOKA et flèche",
              "9. Étude sismique RPA 2024 (Spectre Sad/g, 8 modes, calage VMDS ≥ 0.80VMSE, ν ≤ 0.35, P-Delta)",
              "10. Calcul des éléments structuraux (Poteaux BaelR, Poutres 4 vérif., Voiles § 7.7)",
              "11. Étude de l'acrotère console en flexion composée (Fp = 4·A·Cp·Wp, fissuration préjudiciable)",
              "12. Étude des escaliers droits et paliers selon Blondel (2h + g = 64 cm)",
              "13. Calcul des fondations SAFE v12 sous modèle de Winkler (σeff ≤ 120 kPa, BaelR & BaelT)",
            ].map((ch, idx) => (
              <div key={idx} className="p-3 rounded-2xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700/60 flex items-center justify-between">
                <span className="font-semibold text-slate-800 dark:text-slate-200">{ch}</span>
                <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 ml-2" />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
