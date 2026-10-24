import React, { useState, useMemo, useRef } from 'react';
import { 
  Truck, 
  Package, 
  ShoppingCart, 
  HardHat, 
  Plus, 
  Search, 
  AlertTriangle, 
  CheckCircle2, 
  ArrowDownRight, 
  ArrowUpRight,
  Gauge,
  Fuel,
  Phone,
  Calendar,
  RefreshCw,
  Trash2,
  Wrench,
  Layers,
  Filter,
  FileText,
  Check,
  Zap,
  Activity,
  Calculator,
  RotateCcw,
  Download,
  Camera,
  Table as TableIcon,
  LayoutGrid
} from 'lucide-react';
import { useBTP } from '../context/BTPContext';
import { formatDA, formatNumberDZ } from '../utils/dzFormatters';
import { 
  StockItem, 
  StockCategory, 
  SteelDiameter, 
  StockMovement, 
  StockOperationStatus,
  MaterialEquipment, 
  EquipmentType, 
  EquipmentStatus 
} from '../types/btp';
import { 
  CATEGORY_ALLOWED_UNITS, 
  DEFAULT_UNIT_PER_CATEGORY, 
  CATEGORY_ICONS_BADGES, 
  convertSteel, 
  STEEL_LINEAR_MASS 
} from '../utils/btpStockUtils';

export const LogisticsView: React.FC = () => {
  const { 
    stocks, 
    stockMovements, 
    purchaseOrders, 
    equipment, 
    chantiers, 
    markets,
    articles,
    addStockItem, 
    updateStockQuantity, 
    deleteStockItem, 
    recordStockMovement,
    addEquipment,
    updateEquipment,
    deleteEquipment,
    resetStocksToDZDefaults
  } = useBTP();

  // Filtrage strict : seuls les chantiers dont le marché existe sont conservés
  const validChantiers = useMemo(() => chantiers.filter(c => markets.some(m => m.id === c.marketId)), [chantiers, markets]);
  const validChantierIds = useMemo(() => validChantiers.map(c => c.id), [validChantiers]);

  // Navigation tabs
  const [activeTab, setActiveTab] = useState<'stocks' | 'steel' | 'equipment' | 'movements' | 'orders'>('stocks');
  const [stockViewMode, setStockViewMode] = useState<'cards' | 'table'>('cards');
  
  // Filters
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedChantierId, setSelectedChantierId] = useState<string>('all');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [equipmentStatusFilter, setEquipmentStatusFilter] = useState<string>('all');

  // Modals state
  const [isAddingStock, setIsAddingStock] = useState(false);
  const [isAddingMovement, setIsAddingMovement] = useState(false);
  const [isAddingEquipment, setIsAddingEquipment] = useState(false);
  const [selectedStockForMovement, setSelectedStockForMovement] = useState<StockItem | null>(null);

  // New stock item form state avec statut d'opération segmenté obligatoire
  const [newStockOperationStatus, setNewStockOperationStatus] = useState<StockOperationStatus>('Livré au projet');
  const [newStockMarketId, setNewStockMarketId] = useState<string>('');
  const [newStockChantierId, setNewStockChantierId] = useState<string>('');
  const [newStockName, setNewStockName] = useState('');
  const [newStockCategory, setNewStockCategory] = useState<StockCategory>('Bois');
  const [newStockUnit, setNewStockUnit] = useState('plaque');
  const [newStockDiameter, setNewStockDiameter] = useState<SteelDiameter>(12);
  const [newStockQty, setNewStockQty] = useState(100);
  const [newStockRefDoc, setNewStockRefDoc] = useState('BL-2026/1042');
  const [newStockDate, setNewStockDate] = useState(new Date().toISOString().split('T')[0]);
  const [newStockLotOuvrage, setNewStockLotOuvrage] = useState('LOT 01 - Gros Œuvres / Poteaux R+1');
  const [newStockArticleDQE, setNewStockArticleDQE] = useState('');
  const [newStockThreshold, setNewStockThreshold] = useState(20);
  const [newStockPrice, setNewStockPrice] = useState(850);
  const [newStockSupplier, setNewStockSupplier] = useState('');
  const [newStockLocation, setNewStockLocation] = useState('');

  const blPhotoInputRef = useRef<HTMLInputElement>(null);

  // New movement form state
  const [movementType, setMovementType] = useState<StockMovement['type']>('Sortie (Consommation)');
  const [movementQty, setMovementQty] = useState<number>(10);
  const [movementRefDoc, setMovementRefDoc] = useState('');
  const [movementDestOrSource, setMovementDestOrSource] = useState('');
  const [movementNotes, setMovementNotes] = useState('');

  // New equipment form state
  const [newEqName, setNewEqName] = useState('');
  const [newEqBrand, setNewEqBrand] = useState('');
  const [newEqType, setNewEqType] = useState<EquipmentType>('Rétrocaveuse');
  const [newEqMatricule, setNewEqMatricule] = useState('');
  const [newEqDriver, setNewEqDriver] = useState('');
  const [newEqPhone, setNewEqPhone] = useState('');
  const [newEqHorametre, setNewEqHorametre] = useState<number>(1200);
  const [newEqFuel, setNewEqFuel] = useState<number>(30);
  const [newEqDailyCost, setNewEqDailyCost] = useState<number>(18000);

  // Live steel calculator state
  const [calcDiameter, setCalcDiameter] = useState<SteelDiameter>(12);
  const [calcQty, setCalcQty] = useState<number>(100);
  const [calcUnit, setCalcUnit] = useState<'barre' | 'tonne'>('barre');

  // Handle category change in New Stock Form
  const handleCategoryChange = (category: StockCategory) => {
    setNewStockCategory(category);
    const allowed = CATEGORY_ALLOWED_UNITS[category] || ['Unité'];
    setNewStockUnit(allowed[0]);
  };

  // Filtered stocks (strictement rattachés aux chantiers des marchés existants)
  const filteredStocks = useMemo(() => {
    return stocks.filter(s => {
      if (!validChantierIds.includes(s.chantierId)) return false;
      const matchesSearch = s.name.toLowerCase().includes(searchTerm.toLowerCase()) || 
                            s.category.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            (s.supplier && s.supplier.toLowerCase().includes(searchTerm.toLowerCase()));
      const matchesChantier = selectedChantierId === 'all' || s.chantierId === selectedChantierId;
      const matchesCategory = selectedCategory === 'all' || s.category === selectedCategory;
      return matchesSearch && matchesChantier && matchesCategory;
    });
  }, [stocks, validChantierIds, searchTerm, selectedChantierId, selectedCategory]);

  // Total stock value in DA
  const totalStockValuation = useMemo(() => {
    return stocks
      .filter(s => validChantierIds.includes(s.chantierId))
      .reduce((sum, item) => sum + (item.quantityInStock * item.unitPriceCost), 0);
  }, [stocks, validChantierIds]);

  // Low stock items count
  const lowStockCount = useMemo(() => {
    return stocks.filter(s => validChantierIds.includes(s.chantierId) && s.quantityInStock <= s.minThreshold).length;
  }, [stocks, validChantierIds]);

  // Operational equipment count
  const operationalEqCount = useMemo(() => {
    return equipment.filter(e => validChantierIds.includes(e.chantierId) && (e.status === 'Opérationnel' || e.status === 'En mission / Sur chantier')).length;
  }, [equipment, validChantierIds]);

  // Filtered equipment
  const filteredEquipment = useMemo(() => {
    return equipment.filter(e => {
      if (!validChantierIds.includes(e.chantierId)) return false;
      const matchesSearch = e.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            e.matricule.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            e.driverName.toLowerCase().includes(searchTerm.toLowerCase()) ||
                            e.type.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesChantier = selectedChantierId === 'all' || e.chantierId === selectedChantierId;
      const matchesStatus = equipmentStatusFilter === 'all' || e.status === equipmentStatusFilter;
      return matchesSearch && matchesChantier && matchesStatus;
    });
  }, [equipment, validChantierIds, searchTerm, selectedChantierId, equipmentStatusFilter]);

  // Filtered movements (BL / BS)
  const filteredMovements = useMemo(() => {
    return stockMovements.filter(m => {
      if (!validChantierIds.includes(m.chantierId)) return false;
      return selectedChantierId === 'all' || m.chantierId === selectedChantierId;
    });
  }, [stockMovements, validChantierIds, selectedChantierId]);

  // Filtered purchase orders
  const filteredPurchaseOrders = useMemo(() => {
    return purchaseOrders.filter(po => {
      if (!validChantierIds.includes(po.chantierId)) return false;
      return selectedChantierId === 'all' || po.chantierId === selectedChantierId;
    });
  }, [purchaseOrders, validChantierIds, selectedChantierId]);

  // Handle create new stock avec synchronisation Livré au projet / Consommé
  const handleAddStock = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newStockName.trim()) return;

    const isDelivered = newStockOperationStatus === 'Livré au projet';
    const isConsumed = newStockOperationStatus === 'Consommé';

    const qty = Number(newStockQty) || 0;
    const delivered = isDelivered ? qty : (isConsumed ? qty * 1.5 : qty);
    const consumed = isConsumed ? qty : 0;
    const remaining = isConsumed ? Math.max(0, delivered - consumed) : qty;

    const assignedChantier = newStockChantierId || (selectedChantierId === 'all' ? validChantiers[0]?.id || '' : selectedChantierId);
    const targetChantier = validChantiers.find(c => c.id === assignedChantier);
    const assignedMarket = newStockMarketId || targetChantier?.marketId || markets[0]?.id;

    const newItem: StockItem = {
      id: `stk-${Date.now()}`,
      chantierId: assignedChantier,
      marketId: assignedMarket,
      name: newStockName.trim(),
      category: newStockCategory,
      unit: newStockUnit,
      steelDiameter: newStockCategory === 'Acier' ? newStockDiameter : undefined,
      quantityInStock: remaining,
      totalDelivered: delivered,
      totalConsumed: consumed,
      minThreshold: Number(newStockThreshold) || 10,
      unitPriceCost: Number(newStockPrice) || 0,
      lastRestockedDate: newStockDate || new Date().toISOString().split('T')[0],
      supplier: newStockSupplier || 'Fournisseur Agréé BTP DZ',
      storageLocation: newStockLocation || 'Parc de stockage chantier',
      operationStatus: newStockOperationStatus,
      targetLotName: isConsumed ? newStockLotOuvrage : undefined,
      targetArticleId: isConsumed ? newStockArticleDQE : undefined,
      referenceDoc: newStockRefDoc.trim() || (isDelivered ? `BL-2026/${Math.floor(1000 + Math.random() * 9000)}` : `BS-2026/${Math.floor(100 + Math.random() * 900)}`)
    };

    addStockItem(newItem);
    setIsAddingStock(false);
    setNewStockName('');
    setNewStockSupplier('');
    setNewStockLocation('');
    setNewStockRefDoc('BL-2026/1042');
  };

  // Export CSV de l'état des stocks
  const handleExportStocksCSV = () => {
    let csv = 'Catégorie;Désignation;Unité;Statut Opération;Quantité Livrée au Projet;Quantité Consommée;Reste en Stock sur Chantier;Seuil Minimum;Prix Unitaire Revient (DA);Valeur Globale (DA);Fournisseur;Emplacement\n';
    filteredStocks.forEach(s => {
      const delivered = s.totalDelivered ?? s.quantityInStock;
      const consumed = s.totalConsumed ?? 0;
      csv += `"${s.category}";"${s.name.replace(/"/g, '""')}";"${s.unit}";"${s.operationStatus || 'Livré au projet'}";${delivered};${consumed};${s.quantityInStock};${s.minThreshold};${s.unitPriceCost};${s.quantityInStock * s.unitPriceCost};"${(s.supplier || '').replace(/"/g, '""')}";"${(s.storageLocation || '').replace(/"/g, '""')}"\n`;
    });
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Stocks_Materiaux_ArchiLab_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Quick Open Movement Dialog for a specific item
  const openMovementForStock = (item: StockItem, type: StockMovement['type']) => {
    setSelectedStockForMovement(item);
    setMovementType(type);
    setMovementQty(type.startsWith('Sortie') ? 10 : 50);
    setMovementRefDoc(type.startsWith('Entrée') ? `BL-2026/${Math.floor(1000 + Math.random() * 9000)}` : `BS-2026/${Math.floor(100 + Math.random() * 900)}`);
    setMovementDestOrSource(type.startsWith('Sortie') ? 'Coulage Béton / Chantier' : (item.supplier || 'Livraison Fournisseur'));
    setIsAddingMovement(true);
  };

  // Submit Movement
  const handleSaveMovement = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedStockForMovement || movementQty <= 0) return;

    const movement: StockMovement = {
      id: `mov-${Date.now()}`,
      stockItemId: selectedStockForMovement.id,
      stockItemName: selectedStockForMovement.name,
      chantierId: selectedStockForMovement.chantierId,
      type: movementType,
      quantity: Number(movementQty),
      unit: selectedStockForMovement.unit,
      date: new Date().toISOString().split('T')[0],
      referenceDoc: movementRefDoc || 'Billetage Chantier',
      destinationOrSource: movementDestOrSource || 'Chantier',
      notes: movementNotes
    };

    recordStockMovement(movement);
    setIsAddingMovement(false);
    setSelectedStockForMovement(null);
    setMovementNotes('');
  };

  // Handle Add Equipment
  const handleAddEquipmentSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newEqName.trim() || !newEqMatricule.trim()) return;

    const newMachine: MaterialEquipment = {
      id: `eq-${Date.now()}`,
      chantierId: selectedChantierId === 'all' ? validChantiers[0]?.id || '' : selectedChantierId,
      name: newEqName,
      brandModel: newEqBrand || newEqName,
      type: newEqType,
      matricule: newEqMatricule,
      status: 'Opérationnel',
      driverName: newEqDriver || 'Conducteur Qualifié',
      driverPhone: newEqPhone || '0550 00 00 00',
      horametreHours: Number(newEqHorametre) || 0,
      fuelLitersPerDay: Number(newEqFuel) || 0,
      nextMaintenanceHours: (Number(newEqHorametre) || 0) + 250,
      dailyRentalCostDA: Number(newEqDailyCost) || 15000
    };

    addEquipment(newMachine);
    setIsAddingEquipment(false);
    setNewEqName('');
    setNewEqBrand('');
    setNewEqMatricule('');
    setNewEqDriver('');
    setNewEqPhone('');
  };

  // Quick toggle status for equipment
  const handleCycleEquipmentStatus = (item: MaterialEquipment) => {
    const statuses: EquipmentStatus[] = [
      'Opérationnel', 
      'En mission / Sur chantier', 
      'En maintenance', 
      'En panne', 
      'Au dépôt central'
    ];
    const currentIndex = statuses.indexOf(item.status);
    const nextStatus = statuses[(currentIndex + 1) % statuses.length];
    updateEquipment({ ...item, status: nextStatus });
  };

  // Quick bump horametre
  const handleBumpHorametre = (item: MaterialEquipment, deltaHours: number) => {
    const newHours = (item.horametreHours || 0) + deltaHours;
    updateEquipment({ ...item, horametreHours: newHours });
  };

  // Calculation result for the steel calculator
  const steelCalcResult = useMemo(() => {
    return convertSteel(calcDiameter, calcQty, calcUnit);
  }, [calcDiameter, calcQty, calcUnit]);

  return (
    <div className="space-y-6">
      {/* TOP HEADER */}
      <div className="bg-white p-5 rounded-3xl border border-slate-200/80 shadow-xs flex flex-col lg:flex-row lg:items-center justify-between gap-5">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-2 rounded-xl bg-blue-600/10 text-blue-700">
              <Truck className="w-5 h-5" />
            </span>
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Direction Logistique, Approvisionnements & Matériel BTP
            </span>
          </div>
          <h1 className="text-xl sm:text-2xl font-black text-slate-900 mt-1">
            Gestion des Stocks de Matériaux & Engins BTP
          </h1>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl">
            Suivi conforme aux unités des chantiers algériens : Bois en <strong className="text-slate-800">plaque</strong>, Ciment en <strong className="text-slate-800">sac</strong>, Granulat & Sable en <strong className="text-slate-800">m³</strong>, Plomberie en <strong className="text-slate-800">ml / Unité</strong>, Électricité en <strong className="text-slate-800">m.lineaire / Unité</strong>, Briques en <strong className="text-slate-800">unité / m²</strong>, Revêtement sol en <strong className="text-slate-800">carton / m³ / m²</strong>, Acier Ø6 à Ø20 en <strong className="text-slate-800">barre ou tonne</strong>.
          </p>
        </div>

        {/* Global Action & Reset */}
        <div className="flex items-center flex-wrap gap-2">
          {/* Input Caméra Mobile pour Bon de Livraison (BL) */}
          <input
            type="file"
            ref={blPhotoInputRef}
            accept="application/pdf,image/*"
            capture="environment"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) {
                alert(`Photo du Bon de Livraison (BL) "${file.name}" enregistrée avec succès dans le dossier chantier !`);
              }
            }}
          />

          {/* Bouton Scan / Photo BL direct */}
          <button
            type="button"
            onClick={() => blPhotoInputRef.current?.click()}
            title="Prendre en photo un Bon de Livraison (BL) sur chantier"
            className="px-3 py-2 text-xs font-bold text-emerald-800 dark:text-emerald-200 bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 rounded-xl flex items-center gap-1.5 transition-colors"
          >
            <Camera className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />
            <span className="hidden sm:inline">Photo BL Chantier</span>
          </button>

          {/* Export CSV des Stocks */}
          {activeTab === 'stocks' && (
            <button
              type="button"
              onClick={handleExportStocksCSV}
              title="Exporter l'état complet des stocks au format CSV"
              className="px-3 py-2 text-xs font-bold text-slate-700 dark:text-slate-200 bg-white/70 dark:bg-slate-800 border border-slate-200/80 dark:border-slate-700 hover:bg-white rounded-xl flex items-center gap-1.5 transition-colors"
            >
              <Download className="w-3.5 h-3.5 text-blue-600 dark:text-blue-400" />
              <span className="hidden sm:inline">Export CSV</span>
            </button>
          )}

          <button
            onClick={() => {
              if (confirm("Réinitialiser le référentiel des stocks et du matériel selon les unités standard BTP Algérie (Décret présidentiel N° 15-247) ?")) {
                resetStocksToDZDefaults();
              }
            }}
            title="Restaurer le catalogue complet des matériaux et engins avec les unités DZ"
            className="px-3 py-2 text-xs font-bold text-slate-600 dark:text-slate-300 hover:text-slate-900 bg-slate-100/70 dark:bg-slate-800 hover:bg-slate-200 rounded-xl flex items-center gap-1.5 transition-colors"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Référentiel BTP DZ</span>
          </button>

          {activeTab === 'stocks' && (
            <button
              onClick={() => setIsAddingStock(true)}
              className="px-4 py-2 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-xs font-black rounded-xl shadow-lg shadow-blue-500/25 flex items-center gap-2 transition-all shrink-0 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Nouveau Matériau</span>
            </button>
          )}

          {activeTab === 'equipment' && (
            <button
              onClick={() => setIsAddingEquipment(true)}
              className="px-4 py-2 bg-gradient-to-r from-amber-600 to-orange-600 hover:from-amber-700 hover:to-orange-700 text-white text-xs font-black rounded-xl shadow-lg shadow-amber-500/25 flex items-center gap-2 transition-all shrink-0 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Ajouter un Engin</span>
            </button>
          )}
        </div>
      </div>

      {/* KPI METRIC CARDS */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
        {/* KPI 1: Valeur globale des stocks */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Valeur Stock Chantier</span>
            <span className="p-1.5 rounded-lg bg-emerald-50 text-emerald-600">
              <Package className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-lg sm:text-xl font-black text-slate-900 font-mono tracking-tight">
              {formatDA(totalStockValuation)}
            </span>
            <span className="text-[11px] text-slate-400 block mt-0.5">
              {stocks.length} références actives enregistrées
            </span>
          </div>
        </div>

        {/* KPI 2: Alertes seuil critique */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Stocks Critiques</span>
            <span className={`p-1.5 rounded-lg ${lowStockCount > 0 ? 'bg-amber-100 text-amber-800 animate-pulse' : 'bg-slate-100 text-slate-600'}`}>
              <AlertTriangle className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-lg sm:text-xl font-black text-slate-900 font-mono">
              {lowStockCount} <span className="text-xs font-bold text-slate-500">références</span>
            </span>
            <span className={`text-[11px] font-bold block mt-0.5 ${lowStockCount > 0 ? 'text-amber-600' : 'text-emerald-600'}`}>
              {lowStockCount > 0 ? 'Réapprovisionnement requis' : 'Tous stocks au-dessus du seuil'}
            </span>
          </div>
        </div>

        {/* KPI 3: Parc d'engins opérationnels */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Parc d'Engins BTP</span>
            <span className="p-1.5 rounded-lg bg-blue-50 text-blue-600">
              <Truck className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-lg sm:text-xl font-black text-slate-900 font-mono">
              {operationalEqCount} / {equipment.length} <span className="text-xs font-bold text-slate-500">actifs</span>
            </span>
            <span className="text-[11px] text-emerald-600 font-bold block mt-0.5">
              Taux de disponibilité : {Math.round((operationalEqCount / (equipment.length || 1)) * 100)}%
            </span>
          </div>
        </div>

        {/* KPI 4: Mouvements récents */}
        <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Bons de Mouvements</span>
            <span className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600">
              <FileText className="w-4 h-4" />
            </span>
          </div>
          <div className="mt-2">
            <span className="text-lg sm:text-xl font-black text-slate-900 font-mono">
              {filteredMovements.length} <span className="text-xs font-bold text-slate-500">vouchers</span>
            </span>
            <span className="text-[11px] text-slate-400 block mt-0.5">
              Réceptions BL & Bons de sortie
            </span>
          </div>
        </div>
      </div>

      {/* MAIN NAVIGATION BAR */}
      <div className="flex items-center justify-between gap-3 border-b border-slate-200 pb-3 overflow-x-auto">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab('stocks')}
            className={`px-4 py-2 rounded-xl text-xs font-black transition-all flex items-center gap-2 ${
              activeTab === 'stocks'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            <Package className="w-3.5 h-3.5" />
            <span>Matériaux en Stock</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
              activeTab === 'stocks' ? 'bg-blue-500 text-white' : 'bg-slate-100 text-slate-600'
            }`}>
              {filteredStocks.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('steel')}
            className={`px-4 py-2 rounded-xl text-xs font-black transition-all flex items-center gap-2 ${
              activeTab === 'steel'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            <Calculator className="w-3.5 h-3.5" />
            <span>Convertisseur Acier Ø6-Ø20 (Barres ↔ Tonnes)</span>
          </button>

          <button
            onClick={() => setActiveTab('equipment')}
            className={`px-4 py-2 rounded-xl text-xs font-black transition-all flex items-center gap-2 ${
              activeTab === 'equipment'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            <Truck className="w-3.5 h-3.5" />
            <span>Engins & Matériel BTP</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
              activeTab === 'equipment' ? 'bg-blue-500 text-white' : 'bg-slate-100 text-slate-600'
            }`}>
              {filteredEquipment.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('movements')}
            className={`px-4 py-2 rounded-xl text-xs font-black transition-all flex items-center gap-2 ${
              activeTab === 'movements'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            <FileText className="w-3.5 h-3.5" />
            <span>Journal des Mouvements (BL / BS)</span>
            <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
              activeTab === 'movements' ? 'bg-blue-500 text-white' : 'bg-slate-100 text-slate-600'
            }`}>
              {filteredMovements.length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('orders')}
            className={`px-4 py-2 rounded-xl text-xs font-black transition-all flex items-center gap-2 ${
              activeTab === 'orders'
                ? 'bg-blue-600 text-white shadow-xs'
                : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'
            }`}
          >
            <ShoppingCart className="w-3.5 h-3.5" />
            <span>Bons de Commande ({filteredPurchaseOrders.length})</span>
          </button>
        </div>

        {/* Global Chantier Selector */}
        <div className="shrink-0">
          <select
            value={selectedChantierId}
            onChange={(e) => setSelectedChantierId(e.target.value)}
            className="text-xs font-bold py-1.5 px-3 rounded-xl border border-slate-200 bg-white shadow-xs"
          >
            <option value="all">📍 Tous les Chantiers Réunis</option>
            {validChantiers.map(c => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
      </div>

      {/* ============================================================== */}
      {/* ONGLET 1 : MATÉRIAUX & GESTION DES STOCKS                       */}
      {/* ============================================================== */}
      {activeTab === 'stocks' && (
        <div className="space-y-4">
          {/* SEARCH & CATEGORY CHIPS */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
              <div className="relative flex-1 max-w-md">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Rechercher par matériau, fournisseur, emplacement..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 text-xs rounded-xl border border-slate-200 focus:outline-hidden focus:ring-2 focus:ring-blue-500/20"
                />
              </div>

              <div className="flex items-center gap-3">
                <div className="text-xs text-slate-500 font-medium">
                  Affichage de <strong className="text-slate-900 dark:text-white">{filteredStocks.length}</strong> matériaux sur <strong className="text-slate-900 dark:text-white">{stocks.length}</strong>
                </div>

                {/* Sélecteur de vue : Cartes vs Tableau */}
                <div className="flex items-center gap-1 bg-slate-100/80 dark:bg-slate-800 p-1 rounded-xl border border-slate-200/60 dark:border-slate-700">
                  <button
                    type="button"
                    onClick={() => setStockViewMode('cards')}
                    className={`p-1.5 rounded-lg text-xs font-bold transition-all ${
                      stockViewMode === 'cards'
                        ? 'bg-white dark:bg-slate-700 text-blue-600 dark:text-blue-400 shadow-xs'
                        : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-300'
                    }`}
                    title="Vue en Cartes Tactiles"
                  >
                    <LayoutGrid className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setStockViewMode('table')}
                    className={`p-1.5 rounded-lg text-xs font-bold transition-all ${
                      stockViewMode === 'table'
                        ? 'bg-white dark:bg-slate-700 text-blue-600 dark:text-blue-400 shadow-xs'
                        : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-300'
                    }`}
                    title="Vue en Tableau Détaillé"
                  >
                    <TableIcon className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>

            {/* Quick Category Chips with official units */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 pt-1 text-xs">
              <button
                onClick={() => setSelectedCategory('all')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'all'
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 hover:bg-slate-200 text-slate-600'
                }`}
              >
                Tous les Matériaux ({stocks.length})
              </button>

              <button
                onClick={() => setSelectedCategory('Bois')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Bois'
                    ? 'bg-amber-800 text-white shadow-xs'
                    : 'bg-amber-50 hover:bg-amber-100 text-amber-900'
                }`}
              >
                🪵 Bois (plaque)
              </button>

              <button
                onClick={() => setSelectedCategory('Ciment')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Ciment'
                    ? 'bg-slate-800 text-white shadow-xs'
                    : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                }`}
              >
                🧱 Ciment (sac)
              </button>

              <button
                onClick={() => setSelectedCategory('Granulat')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Granulat'
                    ? 'bg-stone-800 text-white shadow-xs'
                    : 'bg-stone-100 hover:bg-stone-200 text-stone-700'
                }`}
              >
                ⛰️ Granulat (m³)
              </button>

              <button
                onClick={() => setSelectedCategory('Sable')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Sable'
                    ? 'bg-yellow-700 text-white shadow-xs'
                    : 'bg-yellow-50 hover:bg-yellow-100 text-yellow-800'
                }`}
              >
                🏜️ Sable (m³)
              </button>

              <button
                onClick={() => setSelectedCategory('Acier')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Acier'
                    ? 'bg-blue-700 text-white shadow-xs'
                    : 'bg-blue-50 hover:bg-blue-100 text-blue-900'
                }`}
              >
                🔩 Acier Ø6-Ø20 (barre / tonne)
              </button>

              <button
                onClick={() => setSelectedCategory('Plomberie')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Plomberie'
                    ? 'bg-sky-700 text-white shadow-xs'
                    : 'bg-sky-50 hover:bg-sky-100 text-sky-800'
                }`}
              >
                🚿 Plomberie (ml / Unité)
              </button>

              <button
                onClick={() => setSelectedCategory('Électricité')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Électricité'
                    ? 'bg-amber-600 text-white shadow-xs'
                    : 'bg-amber-50 hover:bg-amber-100 text-amber-900'
                }`}
              >
                ⚡ Électricité (m.lineaire / Unité)
              </button>

              <button
                onClick={() => setSelectedCategory('Brique et bloc')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Brique et bloc'
                    ? 'bg-orange-700 text-white shadow-xs'
                    : 'bg-orange-50 hover:bg-orange-100 text-orange-900'
                }`}
              >
                🧱 Briques & Blocs (unité / m²)
              </button>

              <button
                onClick={() => setSelectedCategory('Revêtement sol')}
                className={`px-3 py-1.5 rounded-xl font-bold whitespace-nowrap transition-all ${
                  selectedCategory === 'Revêtement sol'
                    ? 'bg-teal-700 text-white shadow-xs'
                    : 'bg-teal-50 hover:bg-teal-100 text-teal-900'
                }`}
              >
                ✨ Revêtement Sol (carton / m³ / m²)
              </button>
            </div>
          </div>

          {/* VUE 1 : GRILLE DE CARTES TACTILES LIQUID GLASS */}
          {stockViewMode === 'cards' && (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {filteredStocks.map(stk => {
                const isLow = stk.quantityInStock <= stk.minThreshold;
                const meta = CATEGORY_ICONS_BADGES[stk.category] || CATEGORY_ICONS_BADGES['Autre'];
                
                const delivered = stk.totalDelivered ?? stk.quantityInStock;
                const consumed = stk.totalConsumed ?? 0;
                const ratio = delivered > 0 ? Math.min(100, Math.round((consumed / delivered) * 100)) : 0;

                // If steel, compute live equivalence
                let steelEquiv: { barres: number; tonnes: number } | null = null;
                if (stk.category === 'Acier' && stk.steelDiameter) {
                  steelEquiv = convertSteel(stk.steelDiameter, stk.quantityInStock, stk.unit as any);
                }

                return (
                  <div 
                    key={stk.id}
                    className={`liquid-glass-card p-5 border transition-all shadow-md flex flex-col justify-between ${
                      isLow ? 'border-amber-400 bg-amber-500/10 ring-2 ring-amber-400/20' : 'border-white/60 dark:border-white/10 hover:border-blue-400'
                    }`}
                  >
                    <div className="space-y-3">
                      {/* Header line: Category badge & Low stock badge */}
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <span className="text-base">{meta.icon}</span>
                          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-md border ${meta.color}`}>
                            {stk.category} • {stk.unit}
                          </span>

                          {/* Badge de Statut d'Opération */}
                          {stk.operationStatus === 'Consommé' ? (
                            <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-800 dark:text-amber-300 border border-amber-500/30 flex items-center gap-0.5">
                              <ArrowDownRight className="w-3 h-3" />
                              Consommé
                            </span>
                          ) : stk.operationStatus === 'Livré au projet' ? (
                            <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-800 dark:text-emerald-300 border border-emerald-500/30 flex items-center gap-0.5">
                              <ArrowUpRight className="w-3 h-3" />
                              Livré au projet
                            </span>
                          ) : (
                            <span className="text-[10px] font-black px-2 py-0.5 rounded-full bg-blue-500/15 text-blue-800 dark:text-blue-300 border border-blue-500/30">
                              Catalogue
                            </span>
                          )}
                        </div>

                        <div className="flex items-center gap-1">
                          {isLow && (
                            <span className="text-[10px] font-extrabold px-2 py-0.5 rounded-md bg-amber-100 text-amber-800 flex items-center gap-1">
                              <AlertTriangle className="w-3 h-3" />
                              Stock Bas
                            </span>
                          )}
                          <button
                            onClick={() => {
                              if (confirm(`Supprimer la référence "${stk.name}" du stock ?`)) {
                                deleteStockItem(stk.id);
                              }
                            }}
                            className="p-1 text-slate-300 hover:text-rose-600 rounded-md transition-colors"
                            title="Supprimer la référence"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>

                      {/* Designation & Emplacement */}
                      <div>
                        <h4 className="font-extrabold text-sm text-slate-900 dark:text-white leading-snug">{stk.name}</h4>
                        <p className="text-[11px] text-slate-400 mt-1 flex items-center gap-2 flex-wrap">
                          <span>📍 {stk.storageLocation || 'Magasin Chantier'}</span>
                          <span>•</span>
                          <span>🏭 {stk.supplier || 'Fournisseur Agréé'}</span>
                          {stk.referenceDoc && (
                            <>
                              <span>•</span>
                              <span className="font-mono text-blue-600 dark:text-blue-400 font-bold">{stk.referenceDoc}</span>
                            </>
                          )}
                        </p>
                      </div>

                      {/* Acier Specific Badge: Equivalent in Barres or Tonnes */}
                      {steelEquiv && stk.steelDiameter && (
                        <div className="p-2 rounded-xl bg-blue-500/10 border border-blue-500/25 text-[11px] flex items-center justify-between">
                          <span className="font-bold text-blue-900 dark:text-blue-300 flex items-center gap-1">
                            <Zap className="w-3 h-3 text-blue-600" />
                            Acier HA FeE500 Ø{stk.steelDiameter}
                          </span>
                          <span className="font-mono font-black text-blue-800 dark:text-blue-200">
                            {stk.unit === 'barre' ? (
                              <>≈ {formatNumberDZ(steelEquiv.tonnes)} Tonnes</>
                            ) : (
                              <>≈ {formatNumberDZ(steelEquiv.barres)} Barres (12m)</>
                            )}
                          </span>
                        </div>
                      )}

                      {/* Stock Metrics Box Multi-Compteurs : Livré / Consommé / Reste */}
                      <div className="p-3.5 rounded-2xl bg-white/50 dark:bg-slate-800/50 border border-white/60 dark:border-white/10 space-y-2.5">
                        <div className="grid grid-cols-3 gap-1.5 text-center">
                          <div className="p-2 rounded-xl bg-emerald-500/10 border border-emerald-500/20">
                            <span className="text-[9px] uppercase font-bold text-emerald-700 dark:text-emerald-300 block">Livré Projet</span>
                            <span className="font-mono font-black text-xs text-emerald-800 dark:text-emerald-200">
                              {formatNumberDZ(delivered)}
                            </span>
                          </div>

                          <div className="p-2 rounded-xl bg-amber-500/10 border border-amber-500/20">
                            <span className="text-[9px] uppercase font-bold text-amber-700 dark:text-amber-300 block">Consommé</span>
                            <span className="font-mono font-black text-xs text-amber-800 dark:text-amber-200">
                              {formatNumberDZ(consumed)}
                            </span>
                          </div>

                          <div className={`p-2 rounded-xl border ${isLow ? 'bg-rose-500/15 border-rose-500/30 text-rose-800 dark:text-rose-200' : 'bg-blue-500/10 border-blue-500/20 text-blue-900 dark:text-blue-200'}`}>
                            <span className="text-[9px] uppercase font-bold block">Reste Chantier</span>
                            <span className="font-mono font-black text-xs">
                              {formatNumberDZ(stk.quantityInStock)} <span className="text-[9px] font-bold">{stk.unit}</span>
                            </span>
                          </div>
                        </div>

                        {/* Jauge de Progression */}
                        <div>
                          <div className="flex justify-between text-[10px] text-slate-500 dark:text-slate-400 font-bold mb-1">
                            <span>Taux de Consommation Chantier</span>
                            <span>{ratio}% ({formatNumberDZ(consumed)} / {formatNumberDZ(delivered)})</span>
                          </div>
                          <div className="w-full bg-slate-200 dark:bg-slate-700 h-2 rounded-full overflow-hidden">
                            <div 
                              className={`h-full transition-all duration-300 ${ratio > 85 ? 'bg-rose-500' : ratio > 50 ? 'bg-amber-500' : 'bg-emerald-500'}`}
                              style={{ width: `${ratio}%` }}
                            />
                          </div>
                        </div>

                        {/* Valorisation financière */}
                        <div className="flex items-center justify-between pt-1 border-t border-slate-100 dark:border-slate-700/60 text-[11px]">
                          <span className="text-slate-500 font-bold">Valeur en Stock :</span>
                          <span className="font-mono font-black text-slate-900 dark:text-white">
                            {formatDA(stk.quantityInStock * stk.unitPriceCost)}
                          </span>
                        </div>
                      </div>
                    </div>

                    {/* Quick Action Footer: Movement & Adjust */}
                    <div className="pt-3 mt-3 border-t border-white/40 dark:border-white/10 flex items-center justify-between gap-2">
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => openMovementForStock(stk, 'Sortie (Consommation)')}
                          className="px-2.5 py-1.5 rounded-xl bg-amber-500/15 hover:bg-amber-500/25 text-amber-800 dark:text-amber-300 text-xs font-bold flex items-center gap-1 transition-colors"
                          title="Enregistrer une sortie pour consommation chantier"
                        >
                          <ArrowDownRight className="w-3.5 h-3.5" />
                          <span>Sortie (BS)</span>
                        </button>
                        <button
                          onClick={() => openMovementForStock(stk, 'Entrée (Réception BL)')}
                          className="px-2.5 py-1.5 rounded-xl bg-emerald-500/15 hover:bg-emerald-500/25 text-emerald-800 dark:text-emerald-300 text-xs font-bold flex items-center gap-1 transition-colors"
                          title="Enregistrer une livraison / réception fournisseur"
                        >
                          <ArrowUpRight className="w-3.5 h-3.5" />
                          <span>Réception (BL)</span>
                        </button>
                      </div>

                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => updateStockQuantity(stk.id, -10)}
                          className="w-7 h-7 rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 text-slate-700 dark:text-slate-300 text-xs font-bold flex items-center justify-center transition-colors"
                          title="Ajustement rapide -10"
                        >
                          -10
                        </button>
                        <button
                          onClick={() => updateStockQuantity(stk.id, 50)}
                          className="w-7 h-7 rounded-xl bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 text-slate-700 dark:text-slate-300 text-xs font-bold flex items-center justify-center transition-colors"
                          title="Ajustement rapide +50"
                        >
                          +50
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* VUE 2 : TABLEAU COMPTABLE DÉTAILLÉ */}
          {stockViewMode === 'table' && (
            <div className="liquid-glass-card rounded-3xl border border-white/60 dark:border-white/10 overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-xs text-left">
                  <thead className="bg-slate-100/70 dark:bg-slate-800/70 text-slate-700 dark:text-slate-300 font-black uppercase tracking-wider text-[10px] border-b border-slate-200/60 dark:border-slate-700/60">
                    <tr>
                      <th className="py-3 px-3">Statut</th>
                      <th className="py-3 px-3">Catégorie</th>
                      <th className="py-3 px-4">Désignation Matériau</th>
                      <th className="py-3 px-3 text-right">Livré Projet</th>
                      <th className="py-3 px-3 text-right">Consommé</th>
                      <th className="py-3 px-3 text-right">Reste Chantier</th>
                      <th className="py-3 px-3 text-center w-28">Consommation</th>
                      <th className="py-3 px-3 text-right">Valeur Stock (DA)</th>
                      <th className="py-3 px-3 text-center">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/40 dark:divide-white/10">
                    {filteredStocks.map(stk => {
                      const isLow = stk.quantityInStock <= stk.minThreshold;
                      const delivered = stk.totalDelivered ?? stk.quantityInStock;
                      const consumed = stk.totalConsumed ?? 0;
                      const ratio = delivered > 0 ? Math.min(100, Math.round((consumed / delivered) * 100)) : 0;

                      return (
                        <tr key={stk.id} className="hover:bg-white/40 dark:hover:bg-slate-800/40 transition-colors">
                          <td className="py-3 px-3 whitespace-nowrap">
                            {stk.operationStatus === 'Consommé' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-amber-500/15 text-amber-800 dark:text-amber-300 border border-amber-500/30">
                                Consommé
                              </span>
                            ) : stk.operationStatus === 'Livré au projet' ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-emerald-500/15 text-emerald-800 dark:text-emerald-300 border border-emerald-500/30">
                                Livré
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-black bg-blue-500/15 text-blue-800 dark:text-blue-300 border border-blue-500/30">
                                Catalogue
                              </span>
                            )}
                          </td>
                          <td className="py-3 px-3 font-bold text-slate-700 dark:text-slate-300 whitespace-nowrap">
                            {stk.category} ({stk.unit})
                          </td>
                          <td className="py-3 px-4">
                            <span className="font-extrabold text-slate-900 dark:text-white block">{stk.name}</span>
                            <span className="text-[10px] text-slate-400">📍 {stk.storageLocation || 'Magasin'} • 🏭 {stk.supplier || 'Fournisseur'}</span>
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-bold text-emerald-700 dark:text-emerald-300 tabular-nums">
                            {formatNumberDZ(delivered)} {stk.unit}
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-bold text-amber-700 dark:text-amber-300 tabular-nums">
                            {formatNumberDZ(consumed)} {stk.unit}
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-black text-slate-900 dark:text-white tabular-nums">
                            <span className={isLow ? 'text-amber-600 font-black' : ''}>
                              {formatNumberDZ(stk.quantityInStock)} {stk.unit}
                            </span>
                          </td>
                          <td className="py-3 px-3">
                            <div className="w-full bg-slate-200 dark:bg-slate-700 h-2 rounded-full overflow-hidden">
                              <div 
                                className={`h-full ${ratio > 85 ? 'bg-rose-500' : ratio > 50 ? 'bg-amber-500' : 'bg-emerald-500'}`}
                                style={{ width: `${ratio}%` }}
                              />
                            </div>
                            <span className="text-[9px] font-mono font-bold text-slate-400 text-center block mt-0.5">{ratio}%</span>
                          </td>
                          <td className="py-3 px-3 text-right font-mono font-black text-slate-900 dark:text-white tabular-nums">
                            {formatDA(stk.quantityInStock * stk.unitPriceCost)}
                          </td>
                          <td className="py-3 px-3 text-center whitespace-nowrap">
                            <div className="flex items-center justify-center gap-1">
                              <button
                                onClick={() => openMovementForStock(stk, 'Sortie (Consommation)')}
                                className="p-1 rounded-lg bg-amber-500/15 text-amber-700 hover:bg-amber-500/25"
                                title="Sortie Chantier"
                              >
                                <ArrowDownRight className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => openMovementForStock(stk, 'Entrée (Réception BL)')}
                                className="p-1 rounded-lg bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/25"
                                title="Réception BL"
                              >
                                <ArrowUpRight className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => {
                                  if (confirm(`Supprimer la référence "${stk.name}" ?`)) deleteStockItem(stk.id);
                                }}
                                className="p-1 rounded-lg text-slate-300 hover:text-rose-600"
                                title="Supprimer"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {filteredStocks.length === 0 && (
            <div className="bg-white p-12 rounded-2xl border border-dashed border-slate-200 text-center space-y-3">
              <Package className="w-10 h-10 text-slate-300 mx-auto" />
              <h3 className="text-sm font-bold text-slate-700">Aucun matériau trouvé pour cette recherche</h3>
              <p className="text-xs text-slate-400">Modifiez le terme de recherche ou changez la catégorie filtrée.</p>
            </div>
          )}
        </div>
      )}

      {/* ============================================================== */}
      {/* ONGLET 2 : CALCULATEUR & MATRICE ACIER (Ø6 à Ø20)              */}
      {/* ============================================================== */}
      {activeTab === 'steel' && (
        <div className="space-y-6">
          {/* INTERACTIVE STEEL CONVERTER CARD */}
          <div className="bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 p-6 rounded-3xl text-white shadow-lg space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-blue-900/60 pb-4">
              <div>
                <span className="text-xs font-bold text-blue-400 uppercase tracking-widest flex items-center gap-1.5">
                  <Calculator className="w-4 h-4" />
                  Génie Civil & Ferraillage BTP Algérie
                </span>
                <h2 className="text-lg sm:text-xl font-black mt-1">
                  Convertisseur Dynamique Acier : Barres (12m) ↔ Tonnes
                </h2>
                <p className="text-xs text-slate-300 mt-1">
                  Conforme aux normes Eurocode 2 et au Décret BTP Algérie pour l'acier Haute Adhérence FeE500.
                </p>
              </div>

              <div className="bg-blue-900/40 border border-blue-700/50 px-4 py-2 rounded-2xl text-right">
                <span className="text-[10px] text-blue-300 font-bold uppercase block">Poids linéaire Ø{calcDiameter}</span>
                <span className="text-sm font-black font-mono text-white">
                  {STEEL_LINEAR_MASS[calcDiameter].kgPerM} kg / ml
                </span>
                <span className="text-[10px] text-blue-300 block">
                  (1 barre 12m = {STEEL_LINEAR_MASS[calcDiameter].barWeightKg} kg)
                </span>
              </div>
            </div>

            {/* Input Form for live conversion */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 bg-white/5 p-4 rounded-2xl border border-white/10">
              {/* Select Diameter */}
              <div>
                <label className="text-xs font-bold text-blue-300 block mb-1.5">Diamètre Nominal (mm)</label>
                <div className="grid grid-cols-6 gap-1">
                  {([6, 8, 10, 12, 16, 20] as SteelDiameter[]).map(d => (
                    <button
                      key={d}
                      onClick={() => setCalcDiameter(d)}
                      className={`py-2 rounded-xl text-xs font-black transition-all ${
                        calcDiameter === d
                          ? 'bg-blue-500 text-white ring-2 ring-blue-300'
                          : 'bg-white/10 text-slate-300 hover:bg-white/20'
                      }`}
                    >
                      Ø{d}
                    </button>
                  ))}
                </div>
              </div>

              {/* Quantity */}
              <div>
                <label className="text-xs font-bold text-blue-300 block mb-1.5">Quantité</label>
                <input
                  type="number"
                  min="0.01"
                  step="any"
                  value={calcQty}
                  onChange={(e) => setCalcQty(parseFloat(e.target.value) || 0)}
                  className="w-full px-3.5 py-2 text-sm rounded-xl bg-slate-800 border border-blue-700/60 text-white font-mono font-bold focus:outline-hidden focus:ring-2 focus:ring-blue-400"
                />
              </div>

              {/* Unit Switch */}
              <div>
                <label className="text-xs font-bold text-blue-300 block mb-1.5">Unité de Départ</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    onClick={() => setCalcUnit('barre')}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      calcUnit === 'barre'
                        ? 'bg-blue-600 text-white shadow-xs'
                        : 'bg-white/10 text-slate-300 hover:bg-white/20'
                    }`}
                  >
                    Barre (12m)
                  </button>
                  <button
                    onClick={() => setCalcUnit('tonne')}
                    className={`py-2 rounded-xl text-xs font-bold transition-all ${
                      calcUnit === 'tonne'
                        ? 'bg-blue-600 text-white shadow-xs'
                        : 'bg-white/10 text-slate-300 hover:bg-white/20'
                    }`}
                  >
                    Tonne
                  </button>
                </div>
              </div>
            </div>

            {/* Results Display */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="bg-blue-900/30 border border-blue-500/40 p-4 rounded-2xl flex items-center justify-between">
                <div>
                  <span className="text-[11px] font-bold text-blue-300 uppercase tracking-wider block">Équivalence en Barres de 12m</span>
                  <span className="text-2xl font-black font-mono text-white mt-1 block">
                    {formatNumberDZ(steelCalcResult.barres)} <span className="text-sm font-bold text-blue-300">barres</span>
                  </span>
                </div>
                <span className="text-3xl">📏</span>
              </div>

              <div className="bg-indigo-900/30 border border-indigo-500/40 p-4 rounded-2xl flex items-center justify-between">
                <div>
                  <span className="text-[11px] font-bold text-indigo-300 uppercase tracking-wider block">Équivalence en Poids Métrique</span>
                  <span className="text-2xl font-black font-mono text-emerald-400 mt-1 block">
                    {formatNumberDZ(steelCalcResult.tonnes)} <span className="text-sm font-bold text-indigo-300">Tonnes</span>
                  </span>
                </div>
                <span className="text-3xl">⚖️</span>
              </div>
            </div>
          </div>

          {/* TABLE OF STEEL REFERENCE DIAMETERS IN ALGERIAN BTP */}
          <div className="bg-white rounded-3xl border border-slate-200/80 shadow-xs overflow-hidden">
            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
              <div>
                <h3 className="font-black text-sm text-slate-900">
                  Matrice Officielle de Ferraillage BTP Algérie (Aciers FeE500)
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Spécifications de façonnage pour ronds à béton de longueur commerciale 12 mètres
                </p>
              </div>
              <span className="text-xs font-bold px-3 py-1 rounded-full bg-blue-100 text-blue-800">
                Aciers Tor FeE500
              </span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                    <th className="py-3 px-4">Diamètre (mm)</th>
                    <th className="py-3 px-4">Usage Chantier Typique</th>
                    <th className="py-3 px-4 text-right">Masse Linéaire (kg/ml)</th>
                    <th className="py-3 px-4 text-right">Poids 1 Barre (12m)</th>
                    <th className="py-3 px-4 text-right">Nb Barres / Tonne</th>
                    <th className="py-3 px-4 text-center">Unités Réglementaires</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {([6, 8, 10, 12, 16, 20] as SteelDiameter[]).map(d => {
                    const spec = STEEL_LINEAR_MASS[d];
                    const usages: Record<number, string> = {
                      6: 'Cadres, épingles, frettes & étriers pour poteaux secondaires',
                      8: 'Étriers & armatures transversales poteaux et poutres',
                      10: 'Armatures dalles pleines, chaînages horizontaux et verticaux',
                      12: 'Ferraillage principal semelles isolées, filantes et poutres',
                      16: 'Ferraillage principal poteaux porteurs & longrines VRD',
                      20: 'Gros œuvre lourd, amorces poteaux RDC et radiers généraux'
                    };

                    return (
                      <tr key={d} className="hover:bg-blue-50/30 transition-colors">
                        <td className="py-3 px-4 font-mono font-bold text-blue-700 text-sm">
                          Ø{d} mm
                        </td>
                        <td className="py-3 px-4 text-slate-700 font-medium">
                          {usages[d]}
                        </td>
                        <td className="py-3 px-4 text-right font-mono font-bold text-slate-900">
                          {spec.kgPerM} kg/m
                        </td>
                        <td className="py-3 px-4 text-right font-mono text-slate-800">
                          {spec.barWeightKg} kg
                        </td>
                        <td className="py-3 px-4 text-right font-mono font-bold text-indigo-700">
                          ≈ {formatNumberDZ(spec.barsPerTonne)} barres
                        </td>
                        <td className="py-3 px-4 text-center">
                          <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-blue-100 text-blue-800">
                            barre ou tonne
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* ONGLET 3 : GESTION DES ENGINS & MATÉRIEL BTP                   */}
      {/* ============================================================== */}
      {activeTab === 'equipment' && (
        <div className="space-y-4">
          {/* Filter Bar */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="flex items-center gap-3 flex-1">
              <div className="relative flex-1 max-w-sm">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
                <input
                  type="text"
                  placeholder="Rechercher engin, matricule, conducteur..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl border border-slate-200 focus:outline-hidden"
                />
              </div>

              <select
                value={equipmentStatusFilter}
                onChange={(e) => setEquipmentStatusFilter(e.target.value)}
                className="text-xs py-1.5 px-3 rounded-xl border border-slate-200 bg-white font-medium"
              >
                <option value="all">Tous les statuts</option>
                <option value="Opérationnel">Opérationnel</option>
                <option value="En mission / Sur chantier">En mission / Sur chantier</option>
                <option value="En maintenance">En maintenance</option>
                <option value="En panne">En panne</option>
                <option value="Au dépôt central">Au dépôt central</option>
              </select>
            </div>

            <button
              onClick={() => setIsAddingEquipment(true)}
              className="px-4 py-2 bg-gradient-to-r from-amber-600 to-orange-600 hover:from-amber-700 hover:to-orange-700 text-white text-xs font-black rounded-xl shadow-xs flex items-center gap-2 transition-all shrink-0"
            >
              <Plus className="w-4 h-4" />
              <span>Ajouter un Engin BTP</span>
            </button>
          </div>

          {/* Grid of Equipment Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredEquipment.map(eq => {
              const isOper = eq.status === 'Opérationnel' || eq.status === 'En mission / Sur chantier';
              const isMaint = eq.status === 'En maintenance';
              const isBroken = eq.status === 'En panne';

              const statusColor = isOper
                ? 'bg-emerald-100 text-emerald-800 border-emerald-200'
                : isMaint
                ? 'bg-amber-100 text-amber-800 border-amber-200'
                : isBroken
                ? 'bg-rose-100 text-rose-800 border-rose-200'
                : 'bg-slate-100 text-slate-700 border-slate-200';

              return (
                <div 
                  key={eq.id}
                  className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs hover:border-slate-300 transition-all flex flex-col justify-between space-y-4"
                >
                  <div className="space-y-3">
                    {/* Top line: Type & Status toggle */}
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700">
                        🚜 {eq.type}
                      </span>

                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleCycleEquipmentStatus(eq)}
                          className={`text-[10px] font-extrabold px-2.5 py-0.5 rounded-full border transition-transform active:scale-95 flex items-center gap-1 ${statusColor}`}
                          title="Cliquez pour changer de statut"
                        >
                          <Activity className="w-3 h-3" />
                          <span>{eq.status}</span>
                        </button>
                        <button
                          onClick={() => {
                            if (confirm(`Supprimer l'engin "${eq.name}" ?`)) {
                              deleteEquipment(eq.id);
                            }
                          }}
                          className="p-1 text-slate-300 hover:text-rose-600 rounded-md transition-colors"
                          title="Supprimer l'engin"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    {/* Machine Name & Matricule */}
                    <div>
                      <h4 className="font-bold text-sm text-slate-900">{eq.name}</h4>
                      <p className="text-xs text-slate-500 font-mono mt-0.5 flex items-center gap-2">
                        <span className="font-bold text-blue-700 bg-blue-50 px-1.5 py-0.5 rounded-sm">
                          {eq.matricule}
                        </span>
                        {eq.brandModel && <span>{eq.brandModel}</span>}
                      </p>
                    </div>

                    {/* Machine Metrics: Horamètre & Fuel */}
                    <div className="grid grid-cols-2 gap-2 p-3 rounded-xl bg-slate-50 border border-slate-100 text-xs">
                      <div>
                        <span className="text-[10px] text-slate-500 font-bold block uppercase tracking-wider flex items-center gap-1">
                          <Gauge className="w-3 h-3 text-slate-400" />
                          Horamètre
                        </span>
                        <span className="text-sm font-black font-mono text-slate-900 mt-0.5 block">
                          {formatNumberDZ(eq.horametreHours || 0)} h
                        </span>
                        <span className="text-[10px] text-slate-400 block mt-0.5">
                          Rév. à {formatNumberDZ(eq.nextMaintenanceHours || 2500)} h
                        </span>
                      </div>

                      <div>
                        <span className="text-[10px] text-slate-500 font-bold block uppercase tracking-wider flex items-center gap-1">
                          <Fuel className="w-3 h-3 text-slate-400" />
                          Gasoil / Jour
                        </span>
                        <span className="text-sm font-black font-mono text-slate-900 mt-0.5 block">
                          {eq.fuelLitersPerDay ? `${eq.fuelLitersPerDay} L/j` : 'N/A'}
                        </span>
                        <span className="text-[10px] text-slate-400 block mt-0.5">
                          {eq.dailyRentalCostDA ? formatDA(eq.dailyRentalCostDA) : 'Coût N/A'}
                        </span>
                      </div>
                    </div>

                    {/* Assigned Driver Box */}
                    <div className="p-2.5 rounded-xl bg-blue-50/50 border border-blue-100 text-xs flex items-center justify-between">
                      <div>
                        <span className="text-[10px] text-slate-500 block">Conducteur / Opérateur affecté</span>
                        <strong className="text-slate-900 block mt-0.5">{eq.driverName}</strong>
                      </div>
                      {eq.driverPhone && (
                        <a 
                          href={`tel:${eq.driverPhone}`}
                          className="px-2 py-1 rounded-lg bg-blue-100 hover:bg-blue-200 text-blue-800 text-[11px] font-bold flex items-center gap-1 transition-colors"
                        >
                          <Phone className="w-3 h-3" />
                          <span>Appeler</span>
                        </a>
                      )}
                    </div>
                  </div>

                  {/* Actions Bar */}
                  <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                    <span className="text-[11px] text-slate-400">Heures moteur :</span>
                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => handleBumpHorametre(eq, 8)}
                        className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold transition-colors"
                        title="Ajouter une journée de travail (+8h)"
                      >
                        +8 h
                      </button>
                      <button
                        onClick={() => handleBumpHorametre(eq, 40)}
                        className="px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold transition-colors"
                        title="Ajouter une semaine de travail (+40h)"
                      >
                        +40 h
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* ONGLET 4 : JOURNAL DES MOUVEMENTS (BONS DE CHANTIER BL/BS)     */}
      {/* ============================================================== */}
      {activeTab === 'movements' && (
        <div className="space-y-4">
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex items-center justify-between gap-3">
            <div>
              <h3 className="font-black text-sm text-slate-900">
                Journal Chronologique des Mouvements de Stock
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">
                Historique certifié des Bons de Livraison Fournisseurs (BL) et Bons de Sortie Chantier (BS)
              </p>
            </div>
            <span className="text-xs font-bold text-slate-500">
              Total : {filteredMovements.length} mouvements
            </span>
          </div>

          <div className="bg-white rounded-3xl border border-slate-200/80 shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                    <th className="py-3 px-4">Type Mouvement</th>
                    <th className="py-3 px-4">Date</th>
                    <th className="py-3 px-4">Réf. Document</th>
                    <th className="py-3 px-4">Désignation Matériau</th>
                    <th className="py-3 px-4 text-right">Quantité & Unité</th>
                    <th className="py-3 px-4">Destination / Source</th>
                    <th className="py-3 px-4">Observations</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredMovements.map(mov => {
                    const isEntry = mov.type.startsWith('Entrée');
                    const isExit = mov.type.startsWith('Sortie');

                    return (
                      <tr key={mov.id} className="hover:bg-slate-50 transition-colors">
                        <td className="py-3 px-4 whitespace-nowrap">
                          <span className={`px-2.5 py-1 rounded-full text-[10px] font-black inline-flex items-center gap-1 ${
                            isEntry
                              ? 'bg-emerald-100 text-emerald-800'
                              : isExit
                              ? 'bg-rose-100 text-rose-800'
                              : 'bg-blue-100 text-blue-800'
                          }`}>
                            {isEntry && <ArrowUpRight className="w-3 h-3" />}
                            {isExit && <ArrowDownRight className="w-3 h-3" />}
                            <span>{mov.type}</span>
                          </span>
                        </td>
                        <td className="py-3 px-4 font-mono text-slate-500 whitespace-nowrap">{mov.date}</td>
                        <td className="py-3 px-4 font-mono font-bold text-blue-700 whitespace-nowrap">{mov.referenceDoc}</td>
                        <td className="py-3 px-4 font-bold text-slate-900">{mov.stockItemName}</td>
                        <td className="py-3 px-4 text-right font-mono font-black text-sm whitespace-nowrap">
                          <span className={isEntry ? 'text-emerald-700' : 'text-rose-700'}>
                            {isEntry ? '+' : '-'}{formatNumberDZ(mov.quantity)} {mov.unit}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-slate-700 font-medium">{mov.destinationOrSource}</td>
                        <td className="py-3 px-4 text-slate-400 italic text-[11px]">{mov.notes || '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* ONGLET 5 : BONS DE COMMANDE                                    */}
      {/* ============================================================== */}
      {activeTab === 'orders' && (
        <div className="bg-white rounded-3xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="p-4 border-b border-slate-100 flex items-center justify-between">
            <div>
              <h3 className="font-black text-sm text-slate-900">
                Bons de Commande Fournisseurs & Approvisionnements
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">
                Suivi des engagements financiers et livraisons sur site
              </p>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-[11px] font-bold uppercase tracking-wider text-slate-600">
                  <th className="py-3 px-4">N° Commande</th>
                  <th className="py-3 px-4">Fournisseur</th>
                  <th className="py-3 px-4">Désignation</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4 text-right">Montant HT</th>
                  <th className="py-3 px-4 text-center">Statut</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredPurchaseOrders.map(po => (
                  <tr key={po.id} className="hover:bg-slate-50">
                    <td className="py-3 px-4 font-mono font-bold text-blue-700">{po.orderNumber}</td>
                    <td className="py-3 px-4 font-semibold text-slate-900">{po.supplierName}</td>
                    <td className="py-3 px-4 text-slate-600">{po.items.map(i => i.description).join(', ')}</td>
                    <td className="py-3 px-4 font-mono text-slate-500">{po.date}</td>
                    <td className="py-3 px-4 text-right font-mono font-bold text-slate-900">{formatDA(po.totalAmountHT)}</td>
                    <td className="py-3 px-4 text-center">
                      <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold ${
                        po.status === 'Livré sur chantier'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-blue-100 text-blue-800'
                      }`}>
                        {po.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 1 : ENREGISTRER UN MATÉRIAU (LIVRÉ AU PROJET / CONSOMMÉ) */}
      {/* ============================================================== */}
      {isAddingStock && (
        <div className="fixed inset-0 z-50 bg-slate-950/70 backdrop-blur-md flex items-end sm:items-center justify-center p-0 sm:p-4">
          <div className="liquid-glass-modal w-full sm:max-w-2xl sm:rounded-3xl p-5 sm:p-6 shadow-2xl border border-white/60 dark:border-white/10 space-y-4 max-h-[92vh] overflow-y-auto mobile-bottom-sheet">
            <div className="flex items-center justify-between border-b border-white/40 dark:border-white/10 pb-3">
              <div className="flex items-center gap-2.5">
                <span className="p-2 rounded-2xl bg-blue-500/15 text-blue-700 dark:text-blue-300 border border-blue-500/20">
                  <Package className="w-5 h-5" />
                </span>
                <div>
                  <h3 className="font-black text-base text-slate-900 dark:text-white">
                    Enregistrer un Nouveau Matériau au Répertoire
                  </h3>
                  <p className="text-[11px] text-slate-500 dark:text-slate-400">
                    Qualification obligatoire : Réception sur Chantier (BL) ou Sortie Consommation (BS)
                  </p>
                </div>
              </div>
              <button 
                onClick={() => setIsAddingStock(false)} 
                className="p-1.5 rounded-xl text-slate-400 hover:text-slate-600 dark:hover:text-white text-xs font-bold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleAddStock} className="space-y-4">
              {/* SÉLECTEUR DE STATUT D'OPÉRATION LIQUID GLASS (OBLIGATOIRE) */}
              <div className="space-y-1.5">
                <label className="text-xs font-black text-slate-800 dark:text-slate-200 block uppercase tracking-wider">
                  Statut de l'Opération sur Chantier (Obligatoire)
                </label>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setNewStockOperationStatus('Livré au projet');
                      setNewStockRefDoc(`BL-2026/${Math.floor(1000 + Math.random() * 9000)}`);
                    }}
                    className={`p-3 rounded-2xl text-left border text-xs font-bold transition-all flex items-center gap-2.5 touch-target ${
                      newStockOperationStatus === 'Livré au projet'
                        ? 'border-emerald-500 bg-emerald-500/20 text-emerald-900 dark:text-emerald-200 ring-2 ring-emerald-500/30 shadow-sm'
                        : 'border-slate-200/80 dark:border-slate-700 bg-white/40 dark:bg-slate-800/40 text-slate-600 dark:text-slate-300 hover:bg-white/70'
                    }`}
                  >
                    <div className="w-8 h-8 rounded-xl bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 flex items-center justify-center shrink-0">
                      <ArrowUpRight className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="block font-black text-xs">Livré au projet</span>
                      <span className="text-[10px] text-slate-500 dark:text-slate-400 font-medium">Entrée / Réception BL</span>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => {
                      setNewStockOperationStatus('Consommé');
                      setNewStockRefDoc(`BS-2026/${Math.floor(100 + Math.random() * 900)}`);
                    }}
                    className={`p-3 rounded-2xl text-left border text-xs font-bold transition-all flex items-center gap-2.5 touch-target ${
                      newStockOperationStatus === 'Consommé'
                        ? 'border-amber-500 bg-amber-500/20 text-amber-900 dark:text-amber-200 ring-2 ring-amber-500/30 shadow-sm'
                        : 'border-slate-200/80 dark:border-slate-700 bg-white/40 dark:bg-slate-800/40 text-slate-600 dark:text-slate-300 hover:bg-white/70'
                    }`}
                  >
                    <div className="w-8 h-8 rounded-xl bg-amber-500/20 text-amber-700 dark:text-amber-300 flex items-center justify-center shrink-0">
                      <ArrowDownRight className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="block font-black text-xs">Consommé</span>
                      <span className="text-[10px] text-slate-500 dark:text-slate-400 font-medium">Sortie / Ouvrage BS</span>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setNewStockOperationStatus('Référence Catalogue')}
                    className={`p-3 rounded-2xl text-left border text-xs font-bold transition-all flex items-center gap-2.5 touch-target ${
                      newStockOperationStatus === 'Référence Catalogue'
                        ? 'border-blue-500 bg-blue-500/20 text-blue-900 dark:text-blue-200 ring-2 ring-blue-500/30 shadow-sm'
                        : 'border-slate-200/80 dark:border-slate-700 bg-white/40 dark:bg-slate-800/40 text-slate-600 dark:text-slate-300 hover:bg-white/70'
                    }`}
                  >
                    <div className="w-8 h-8 rounded-xl bg-blue-500/20 text-blue-700 dark:text-blue-300 flex items-center justify-center shrink-0">
                      <Package className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="block font-black text-xs">Catalogue Seul</span>
                      <span className="text-[10px] text-slate-500 dark:text-slate-400 font-medium">Stock initial standard</span>
                    </div>
                  </button>
                </div>
              </div>

              {/* PROJET & CHANTIER AFFECTÉ */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-black text-slate-800 dark:text-slate-200 block mb-1">
                    Projet / Marché Public Affecté
                  </label>
                  <select
                    value={newStockMarketId || (markets[0]?.id || '')}
                    onChange={(e) => {
                      setNewStockMarketId(e.target.value);
                      const firstCh = validChantiers.find(c => c.marketId === e.target.value);
                      if (firstCh) setNewStockChantierId(firstCh.id);
                    }}
                    className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input font-bold"
                  >
                    {markets.map(m => (
                      <option key={m.id} value={m.id}>
                        N° {m.number} — {m.title.slice(0, 45)}...
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-xs font-black text-slate-800 dark:text-slate-200 block mb-1">
                    Chantier Actif de Destination
                  </label>
                  <select
                    value={newStockChantierId || (validChantiers[0]?.id || '')}
                    onChange={(e) => setNewStockChantierId(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input font-bold"
                  >
                    {validChantiers.map(c => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.address})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* CHAMPS DYNAMIQUES SELON STATUT D'OPÉRATION */}
              {newStockOperationStatus === 'Livré au projet' && (
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3.5 rounded-2xl bg-emerald-500/10 border border-emerald-500/25">
                  <div>
                    <label className="text-xs font-black text-emerald-900 dark:text-emerald-200 block mb-1">
                      Quantité Livrée au Projet
                    </label>
                    <input
                      type="number"
                      min="0.01"
                      step="any"
                      required
                      value={newStockQty}
                      onChange={(e) => setNewStockQty(parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 text-xs font-mono font-black rounded-xl liquid-glass-input"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-black text-emerald-900 dark:text-emerald-200 block mb-1">
                      N° Bon de Livraison (BL)
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="ex: BL-2026/4102"
                      value={newStockRefDoc}
                      onChange={(e) => setNewStockRefDoc(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-mono font-bold rounded-xl liquid-glass-input"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-black text-emerald-900 dark:text-emerald-200 block mb-1">
                      Date de Réception
                    </label>
                    <input
                      type="date"
                      required
                      value={newStockDate}
                      onChange={(e) => setNewStockDate(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-bold rounded-xl liquid-glass-input"
                    />
                  </div>
                </div>
              )}

              {newStockOperationStatus === 'Consommé' && (
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 p-3.5 rounded-2xl bg-amber-500/10 border border-amber-500/25">
                  <div>
                    <label className="text-xs font-black text-amber-900 dark:text-amber-200 block mb-1">
                      Quantité Consommée sur le Projet
                    </label>
                    <input
                      type="number"
                      min="0.01"
                      step="any"
                      required
                      value={newStockQty}
                      onChange={(e) => setNewStockQty(parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 text-xs font-mono font-black rounded-xl liquid-glass-input"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-black text-amber-900 dark:text-amber-200 block mb-1">
                      N° Bon de Sortie (BS)
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="ex: BS-2026/890"
                      value={newStockRefDoc}
                      onChange={(e) => setNewStockRefDoc(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-mono font-bold rounded-xl liquid-glass-input"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-black text-amber-900 dark:text-amber-200 block mb-1">
                      Date de Consommation
                    </label>
                    <input
                      type="date"
                      required
                      value={newStockDate}
                      onChange={(e) => setNewStockDate(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-bold rounded-xl liquid-glass-input"
                    />
                  </div>

                  <div className="sm:col-span-2">
                    <label className="text-xs font-black text-amber-900 dark:text-amber-200 block mb-1">
                      Lot / Ouvrage d'Affectation
                    </label>
                    <input
                      type="text"
                      placeholder="ex: LOT 01 - Gros Œuvres (Poteaux R+1) / Briques RDC..."
                      value={newStockLotOuvrage}
                      onChange={(e) => setNewStockLotOuvrage(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-bold rounded-xl liquid-glass-input"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-black text-amber-900 dark:text-amber-200 block mb-1">
                      Article DQE Lié (Rentabilité)
                    </label>
                    <select
                      value={newStockArticleDQE}
                      onChange={(e) => setNewStockArticleDQE(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-bold rounded-xl liquid-glass-input truncate"
                    >
                      <option value="">Sélectionner un Article DQE...</option>
                      {articles.map(a => (
                        <option key={a.id} value={a.id}>
                          {a.code} — {a.designation.slice(0, 30)}...
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
              )}

              {newStockOperationStatus === 'Référence Catalogue' && (
                <div className="p-3.5 rounded-2xl bg-blue-500/10 border border-blue-500/25 grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-black text-blue-900 dark:text-blue-200 block mb-1">
                      Quantité Initiale en Stock
                    </label>
                    <input
                      type="number"
                      min="0"
                      step="any"
                      required
                      value={newStockQty}
                      onChange={(e) => setNewStockQty(parseFloat(e.target.value) || 0)}
                      className="w-full px-3 py-2 text-xs font-mono font-bold rounded-xl liquid-glass-input"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-black text-blue-900 dark:text-blue-200 block mb-1">
                      Emplacement de Stockage
                    </label>
                    <input
                      type="text"
                      placeholder="ex: Parc central à fer / Magasin..."
                      value={newStockLocation}
                      onChange={(e) => setNewStockLocation(e.target.value)}
                      className="w-full px-3 py-2 text-xs font-bold rounded-xl liquid-glass-input"
                    />
                  </div>
                </div>
              )}

              {/* Catégorie */}
              <div>
                <label className="text-xs font-bold text-slate-700 dark:text-slate-300 block mb-1">
                  Catégorie du Matériau
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                  {(['Bois', 'Ciment', 'Granulat', 'Sable', 'Acier', 'Plomberie', 'Électricité', 'Brique et bloc', 'Revêtement sol'] as StockCategory[]).map(cat => {
                    const meta = CATEGORY_ICONS_BADGES[cat];
                    const isSelected = newStockCategory === cat;
                    return (
                      <button
                        type="button"
                        key={cat}
                        onClick={() => handleCategoryChange(cat)}
                        className={`p-2 rounded-xl text-left border text-xs font-bold transition-all flex items-center gap-2 ${
                          isSelected
                            ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/40 text-blue-900 dark:text-blue-200 ring-2 ring-blue-500/20'
                            : 'border-slate-200/80 dark:border-slate-700 hover:border-slate-300 text-slate-700 dark:text-slate-300'
                        }`}
                      >
                        <span>{meta.icon}</span>
                        <div className="truncate">
                          <span className="block truncate">{cat}</span>
                          <span className="text-[9px] text-slate-400 font-normal">{meta.badge}</span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Désignation Complète */}
              <div>
                <label className="text-xs font-bold text-slate-700 dark:text-slate-300 block mb-1">
                  Désignation Complète du Matériau
                </label>
                <input
                  type="text"
                  required
                  placeholder="ex: Planches sapin rouge 4m / Ciment CPJ 42.5 / Acier HA Ø12..."
                  value={newStockName}
                  onChange={(e) => setNewStockName(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input focus:outline-hidden focus:ring-2 focus:ring-blue-500 font-bold"
                />
              </div>

              {/* Diamètre si Acier */}
              {newStockCategory === 'Acier' && (
                <div className="p-3 bg-blue-500/10 rounded-2xl border border-blue-500/25 space-y-2">
                  <label className="text-xs font-black text-blue-900 dark:text-blue-200 block">
                    Diamètre Nominal du Fer à Béton (mm)
                  </label>
                  <div className="grid grid-cols-6 gap-1.5">
                    {([6, 8, 10, 12, 16, 20] as SteelDiameter[]).map(dia => (
                      <button
                        type="button"
                        key={dia}
                        onClick={() => setNewStockDiameter(dia)}
                        className={`py-2 text-xs font-black rounded-xl transition-all ${
                          newStockDiameter === dia
                            ? 'bg-blue-600 text-white shadow-xs'
                            : 'bg-white/80 dark:bg-slate-800 border border-blue-200 dark:border-slate-700 text-blue-900 dark:text-blue-200'
                        }`}
                      >
                        Ø{dia}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Unité & Prix Unitaire */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 dark:text-slate-300 block mb-1">
                    Unité Réglementaire DZ
                  </label>
                  <select
                    value={newStockUnit}
                    onChange={(e) => setNewStockUnit(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input font-bold"
                  >
                    {(CATEGORY_ALLOWED_UNITS[newStockCategory] || ['Unité']).map(u => (
                      <option key={u} value={u}>{u}</option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 dark:text-slate-300 block mb-1">
                    Prix Unitaire de Revient (DA)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="any"
                    required
                    value={newStockPrice}
                    onChange={(e) => setNewStockPrice(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input font-mono font-bold"
                  />
                </div>
              </div>

              {/* Seuil Alerte & Fournisseur */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 dark:text-slate-300 block mb-1">
                    Seuil d'Alerte Minimum
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="any"
                    required
                    value={newStockThreshold}
                    onChange={(e) => setNewStockThreshold(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input font-mono font-bold"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 dark:text-slate-300 block mb-1">
                    Fournisseur Principal
                  </label>
                  <input
                    type="text"
                    placeholder="ex: GICA / Tosyali / Lafarge..."
                    value={newStockSupplier}
                    onChange={(e) => setNewStockSupplier(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl liquid-glass-input font-bold"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-white/40 dark:border-white/10">
                <button
                  type="button"
                  onClick={() => setIsAddingStock(false)}
                  className="px-4 py-2 text-xs font-bold text-slate-600 dark:text-slate-300 hover:bg-white/40 rounded-xl"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 text-white text-xs font-black rounded-xl shadow-lg shadow-blue-500/25 transition-all cursor-pointer"
                >
                  Enregistrer la Référence
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 2 : ENREGISTRER UN MOUVEMENT (BL FOURNISSEUR OU BS)      */}
      {/* ============================================================== */}
      {isAddingMovement && selectedStockForMovement && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl border border-slate-100 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <span className={`p-1.5 rounded-lg ${
                  movementType.startsWith('Entrée') ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'
                }`}>
                  {movementType.startsWith('Entrée') ? <ArrowUpRight className="w-4 h-4" /> : <ArrowDownRight className="w-4 h-4" />}
                </span>
                <div>
                  <h3 className="font-black text-sm text-slate-900">
                    {movementType.startsWith('Entrée') ? 'Bon de Réception Fournisseur (BL)' : 'Bon de Sortie Chantier (Consommation)'}
                  </h3>
                  <span className="text-xs text-slate-500 font-medium">{selectedStockForMovement.name}</span>
                </div>
              </div>
              <button 
                onClick={() => setIsAddingMovement(false)} 
                className="text-slate-400 hover:text-slate-600 text-xs font-bold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSaveMovement} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">
                    Quantité ({selectedStockForMovement.unit})
                  </label>
                  <input
                    type="number"
                    min="0.01"
                    step="any"
                    required
                    value={movementQty}
                    onChange={(e) => setMovementQty(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-sm rounded-xl border border-slate-200 font-mono font-bold"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">
                    N° Bon / Référence
                  </label>
                  <input
                    type="text"
                    required
                    value={movementRefDoc}
                    onChange={(e) => setMovementRefDoc(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono font-bold"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  {movementType.startsWith('Entrée') ? 'Fournisseur / Transporteur' : 'Destination / Affectation sur Chantier'}
                </label>
                <input
                  type="text"
                  required
                  placeholder={movementType.startsWith('Entrée') ? "ex: SPA Cimenterie Chlef" : "ex: Coulage Poteaux RDC Bloc B"}
                  value={movementDestOrSource}
                  onChange={(e) => setMovementDestOrSource(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">
                  Observations / Remarques
                </label>
                <textarea
                  rows={2}
                  placeholder="Précisions sur la livraison, pesée, conformité..."
                  value={movementNotes}
                  onChange={(e) => setMovementNotes(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingMovement(false)}
                  className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className={`px-5 py-2 text-white text-xs font-black rounded-xl shadow-xs ${
                    movementType.startsWith('Entrée') ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'
                  }`}
                >
                  Valider le Mouvement
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* MODAL 3 : AJOUTER UN ENGIN BTP                                 */}
      {/* ============================================================== */}
      {isAddingEquipment && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl border border-slate-100 space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2">
                <span className="p-1.5 rounded-lg bg-amber-100 text-amber-800">
                  <Truck className="w-4 h-4" />
                </span>
                <h3 className="font-black text-sm text-slate-900">
                  Enregistrer un Nouvel Engin ou Véhicule BTP
                </h3>
              </div>
              <button 
                onClick={() => setIsAddingEquipment(false)} 
                className="text-slate-400 hover:text-slate-600 text-xs font-bold"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleAddEquipmentSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Type d'Engin</label>
                  <select
                    value={newEqType}
                    onChange={(e) => setNewEqType(e.target.value as any)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 bg-white font-bold"
                  >
                    <option value="Rétrocaveuse">Rétrocaveuse (JCB/CAT)</option>
                    <option value="Pelleteuse">Pelleteuse sur chenilles</option>
                    <option value="Grue à tour">Grue à tour Potain</option>
                    <option value="Camion malaxeur (Toupie)">Camion Malaxeur (Toupie)</option>
                    <option value="Camion benne">Camion Benne 15T/20T</option>
                    <option value="Bétonnière mobile">Bétonnière mobile tractée</option>
                    <option value="Compacteur">Compacteur vibrant</option>
                    <option value="Groupe électrogène">Groupe électrogène</option>
                    <option value="Chariot télescopique">Chariot télescopique</option>
                    <option value="Dumper">Dumper</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Matricule Wilaya</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: 01452-116-22"
                    value={newEqMatricule}
                    onChange={(e) => setNewEqMatricule(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono font-bold"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-700 block mb-1">Désignation & Marque / Modèle</label>
                <input
                  type="text"
                  required
                  placeholder="ex: Rétrocaveuse 4x4 JCB 3CX Eco"
                  value={newEqName}
                  onChange={(e) => setNewEqName(e.target.value)}
                  className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Conducteur / Chauffeur</label>
                  <input
                    type="text"
                    required
                    placeholder="ex: Youcef Benali"
                    value={newEqDriver}
                    onChange={(e) => setNewEqDriver(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Téléphone Conducteur</label>
                  <input
                    type="text"
                    placeholder="ex: 0550 12 34 56"
                    value={newEqPhone}
                    onChange={(e) => setNewEqPhone(e.target.value)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Horamètre (h)</label>
                  <input
                    type="number"
                    min="0"
                    value={newEqHorametre}
                    onChange={(e) => setNewEqHorametre(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Gasoil (L/j)</label>
                  <input
                    type="number"
                    min="0"
                    value={newEqFuel}
                    onChange={(e) => setNewEqFuel(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono"
                  />
                </div>

                <div>
                  <label className="text-xs font-bold text-slate-700 block mb-1">Coût DA / jour</label>
                  <input
                    type="number"
                    min="0"
                    value={newEqDailyCost}
                    onChange={(e) => setNewEqDailyCost(parseFloat(e.target.value) || 0)}
                    className="w-full px-3 py-2 text-xs rounded-xl border border-slate-200 font-mono"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingEquipment(false)}
                  className="px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-100 rounded-xl"
                >
                  Annuler
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 bg-amber-600 hover:bg-amber-700 text-white text-xs font-black rounded-xl shadow-xs"
                >
                  Ajouter l'Engin
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
