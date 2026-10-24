// BTP Manager Pro DZ — Utilitaires Réglementaires et Financiers Algérie

import { Wilaya, AlgerianBank } from '../types/btp';

export const ALGERIAN_WILAYAS: Wilaya[] = [
  { code: '01', name: 'Adrar', nameAr: 'أدرار', region: 'Sud' },
  { code: '02', name: 'Chlef', nameAr: 'الشلف', region: 'Centre' },
  { code: '03', name: 'Laghouat', nameAr: 'الأغواط', region: 'Sud' },
  { code: '04', name: 'Oum El Bouaghi', nameAr: 'أم البواقي', region: 'Est' },
  { code: '05', name: 'Batna', nameAr: 'باتنة', region: 'Est' },
  { code: '06', name: 'Béjaïa', nameAr: 'بجاية', region: 'Est' },
  { code: '07', name: 'Biskra', nameAr: 'بسكرة', region: 'Sud' },
  { code: '08', name: 'Béchar', nameAr: 'بشار', region: 'Sud' },
  { code: '09', name: 'Blida', nameAr: 'البليدة', region: 'Centre' },
  { code: '10', name: 'Bouira', nameAr: 'البويرة', region: 'Centre' },
  { code: '11', name: 'Tamanrasset', nameAr: 'تمنراست', region: 'Sud' },
  { code: '12', name: 'Tébessa', nameAr: 'تبسة', region: 'Est' },
  { code: '13', name: 'Tlemcen', nameAr: 'تلمسان', region: 'Ouest' },
  { code: '14', name: 'Tiaret', nameAr: 'تيارت', region: 'Ouest' },
  { code: '15', name: 'Tizi Ouzou', nameAr: 'تيزي وزو', region: 'Centre' },
  { code: '16', name: 'Alger', nameAr: 'الجزائر', region: 'Centre' },
  { code: '17', name: 'Djelfa', nameAr: 'الجلفة', region: 'Centre' },
  { code: '18', name: 'Jijel', nameAr: 'جيجل', region: 'Est' },
  { code: '19', name: 'Sétif', nameAr: 'سطيف', region: 'Est' },
  { code: '20', name: 'Saïda', nameAr: 'سعيدة', region: 'Ouest' },
  { code: '21', name: 'Skikda', nameAr: 'سكيكدة', region: 'Est' },
  { code: '22', name: 'Sidi Bel Abbès', nameAr: 'سيدي بلعباس', region: 'Ouest' },
  { code: '23', name: 'Annaba', nameAr: 'عنابة', region: 'Est' },
  { code: '24', name: 'Guelma', nameAr: 'قالمة', region: 'Est' },
  { code: '25', name: 'Constantine', nameAr: 'قسنطينة', region: 'Est' },
  { code: '26', name: 'Médéa', nameAr: 'المدية', region: 'Centre' },
  { code: '27', name: 'Mostaganem', nameAr: 'مستغانم', region: 'Ouest' },
  { code: '28', name: "M'Sila", nameAr: 'المسيلة', region: 'Est' },
  { code: '29', name: 'Mascara', nameAr: 'معسكر', region: 'Ouest' },
  { code: '30', name: 'Ouargla', nameAr: 'ورقلة', region: 'Sud' },
  { code: '31', name: 'Oran', nameAr: 'وهران', region: 'Ouest' },
  { code: '32', name: 'El Bayadh', nameAr: 'البيض', region: 'Ouest' },
  { code: '33', name: 'Illizi', nameAr: 'إليزي', region: 'Sud' },
  { code: '34', name: 'Bordj Bou Arreridj', nameAr: 'برج بوعريريج', region: 'Est' },
  { code: '35', name: 'Boumerdès', nameAr: 'بومرداس', region: 'Centre' },
  { code: '36', name: 'El Tarf', nameAr: 'الطارف', region: 'Est' },
  { code: '37', name: 'Tindouf', nameAr: 'تندوف', region: 'Sud' },
  { code: '38', name: 'Tissemsilt', nameAr: 'تيسمسيلت', region: 'Ouest' },
  { code: '39', name: 'El Oued', nameAr: 'الوادي', region: 'Sud' },
  { code: '40', name: 'Khenchela', nameAr: 'خنشلة', region: 'Est' },
  { code: '41', name: 'Souk Ahras', nameAr: 'سوق أهراس', region: 'Est' },
  { code: '42', name: 'Tipaza', nameAr: 'تيبازة', region: 'Centre' },
  { code: '43', name: 'Mila', nameAr: 'ميلة', region: 'Est' },
  { code: '44', name: 'Aïn Defla', nameAr: 'عين الدفلى', region: 'Centre' },
  { code: '45', name: 'Naâma', nameAr: 'النعامة', region: 'Ouest' },
  { code: '46', name: 'Aïn Témouchent', nameAr: 'عين تموشنت', region: 'Ouest' },
  { code: '47', name: 'Ghardaïa', nameAr: 'غرداية', region: 'Sud' },
  { code: '48', name: 'Relizane', nameAr: 'غليزان', region: 'Ouest' },
  { code: '49', name: 'Timimoun', nameAr: 'تيميمون', region: 'Sud' },
  { code: '50', name: 'Bordj Badji Mokhtar', nameAr: 'برج باجي مختار', region: 'Sud' },
  { code: '51', name: 'Ouled Djellal', nameAr: 'أولاد جلال', region: 'Sud' },
  { code: '52', name: 'Béni Abbès', nameAr: 'بني عباس', region: 'Sud' },
  { code: '53', name: 'In Salah', nameAr: 'عين صالح', region: 'Sud' },
  { code: '54', name: 'In Guezzam', nameAr: 'عين قزام', region: 'Sud' },
  { code: '55', name: 'Touggourt', nameAr: 'تقرت', region: 'Sud' },
  { code: '56', name: 'Djanet', nameAr: 'جانت', region: 'Sud' },
  { code: '57', name: "El M'Ghair", nameAr: 'المغير', region: 'Sud' },
  { code: '58', name: 'El Meniaa', nameAr: 'المنيعة', region: 'Sud' },
];

export const ALGERIAN_BANKS: AlgerianBank[] = [
  'BNA',
  'BEA',
  'CPA',
  'BADR',
  'BDL',
  'CNEP-Banque',
  'Al Baraka',
  'AGB',
  'Housing Bank',
  'Société Générale',
  'Autre'
];

/**
 * Formatage strict en Dinar Algérien (DA)
 * Ex: 1250000.5 -> "1 250 000,50 DA"
 */
export function formatDA(amount: number | null | undefined): string {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return '0,00 DA';
  }
  
  const rounded = Math.round(amount * 100) / 100;
  const parts = rounded.toFixed(2).split('.');
  const integerPart = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  const decimalPart = parts[1];
  
  return `${integerPart},${decimalPart} DA`;
}

export function formatNumberDZ(value: number | null | undefined, decimals: number = 2): string {
  if (value === null || value === undefined || isNaN(value)) {
    return '0,00';
  }
  const parts = value.toFixed(decimals).split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return parts.join(',');
}

/**
 * Conversion d'un montant numérique en toutes lettres en Français
 * Adapté aux normes des Marchés Publics Algériens (Dinars et centimes)
 */
export function numberToWordsDZ(amount: number): string {
  if (isNaN(amount) || amount === 0) {
    return 'Zéro Dinar Algérien';
  }

  const units = ['', 'un', 'deux', 'trois', 'quatre', 'cinq', 'six', 'sept', 'huit', 'neuf'];
  const teens = ['dix', 'onze', 'douze', 'treize', 'quatorze', 'quinze', 'seize', 'dix-sept', 'dix-huit', 'dix-neuf'];
  const tens = ['', 'dix', 'vingt', 'trente', 'quarante', 'cinquante', 'soixante', 'soixante-dix', 'quatre-vingts', 'quatre-vingt-dix'];

  function convertGroup(n: number): string {
    let result = '';
    const h = Math.floor(n / 100);
    const remainder = n % 100;

    if (h > 0) {
      if (h === 1) {
        result += 'cent';
      } else {
        result += units[h] + ' cents';
      }
      if (remainder > 0) {
        result = result.replace(/cents$/, 'cent');
        result += ' ';
      }
    }

    if (remainder >= 10 && remainder <= 19) {
      result += teens[remainder - 10];
    } else if (remainder >= 70 && remainder <= 79) {
      if (remainder === 71) {
        result += 'soixante et onze';
      } else {
        result += 'soixante-' + teens[remainder - 70];
      }
    } else if (remainder >= 90 && remainder <= 99) {
      result += 'quatre-vingt-' + teens[remainder - 90];
    } else {
      const t = Math.floor(remainder / 10);
      const u = remainder % 10;

      if (t > 0) {
        if (t === 8 && u === 0) {
          result += 'quatre-vingts';
        } else {
          result += tens[t].replace(/s$/, '');
        }
        if (u === 1 && t !== 8) {
          result += ' et un';
        } else if (u > 0) {
          result += '-' + units[u];
        }
      } else if (u > 0) {
        result += units[u];
      }
    }

    return result;
  }

  const absAmount = Math.abs(amount);
  const dinars = Math.floor(absAmount);
  const centimes = Math.round((absAmount - dinars) * 100);

  let dinarText = '';

  const milliards = Math.floor(dinars / 1000000000);
  const millions = Math.floor((dinars % 1000000000) / 1000000);
  const milliers = Math.floor((dinars % 1000000) / 1000);
  const unites = dinars % 1000;

  if (milliards > 0) {
    dinarText += convertGroup(milliards) + (milliards > 1 ? ' milliards ' : ' milliard ');
  }
  if (millions > 0) {
    dinarText += convertGroup(millions) + (millions > 1 ? ' millions ' : ' million ');
  }
  if (milliers > 0) {
    if (milliers === 1) {
      dinarText += 'mille ';
    } else {
      dinarText += convertGroup(milliers) + ' mille ';
    }
  }
  if (unites > 0 || dinarText === '') {
    dinarText += convertGroup(unites);
  }

  dinarText = dinarText.trim();
  dinarText = dinarText.charAt(0).toUpperCase() + dinarText.slice(1);

  let fullText = `${dinarText} Dinar${dinars > 1 ? 's' : ''} Algérien${dinars > 1 ? 's' : ''}`;

  if (centimes > 0) {
    const centimeWord = convertGroup(centimes);
    fullText += ` et ${centimeWord} centime${centimes > 1 ? 's' : ''}`;
  }

  return fullText;
}
