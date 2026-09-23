/**
 * Utility functions for parsing and normalizing form values extracted from voice dictation.
 * HTML5 <input type="date"> strictly requires ISO 8601 YYYY-MM-DD.
 */

const MONTHS = {
  january: 1, jan: 1,
  february: 2, feb: 2,
  march: 3, mar: 3,
  april: 4, apr: 4,
  may: 5,
  june: 6, jun: 6,
  july: 7, jul: 7,
  august: 8, aug: 8,
  september: 9, sep: 9, sept: 9,
  october: 10, oct: 10,
  november: 11, nov: 11,
  december: 12, dec: 12,
  // Marathi month names
  'जानेवारी': 1, 'फेब्रुवारी': 2, 'मार्च': 3, 'एप्रिल': 4,
  'मे': 5, 'जून': 6, 'जुलै': 7, 'ऑगस्ट': 8,
  'सप्टेंबर': 9, 'ऑक्टोबर': 10, 'नोव्हेंबर': 11, 'डिसेंबर': 12,
  // Hindi month names
  'जनवरी': 1, 'फरवरी': 2, 'अप्रैल': 4, 'मई': 5,
  'जुलाई': 7, 'अगस्त': 8, 'सितंबर': 9, 'अक्टूबर': 10,
  'नवंबर': 11, 'दिसंबर': 12,
};

const DEVANAGARI_DIGITS = {
  '०': '0', '१': '1', '२': '2', '३': '3', '४': '4',
  '५': '5', '६': '6', '७': '7', '८': '8', '९': '9',
};

export const normalizeDevanagariDigits = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str.replace(/[०-९]/g, (d) => DEVANAGARI_DIGITS[d] || d);
};

/**
 * Normalizes any spoken or written date string to ISO format YYYY-MM-DD.
 * Returns empty string or original string if unparseable.
 */
export const parseToIsoDate = (raw) => {
  if (!raw || typeof raw !== 'string') return '';
  const s = normalizeDevanagariDigits(raw.trim());
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;

  // Pattern: "25 july 2006", "25th July 2006", "25th July, 2006", "25 जुलै 2006"
  const dmyMatch = s.match(/^(\d{1,2})(?:st|nd|rd|th)?\s+([a-zA-Z\u0900-\u097F]+)(?:,)?\s+(\d{4})$/);
  if (dmyMatch) {
    const day = parseInt(dmyMatch[1], 10);
    const mon = MONTHS[dmyMatch[2].toLowerCase()];
    const year = parseInt(dmyMatch[3], 10);
    if (mon && day >= 1 && day <= 31 && year >= 1900 && year <= 2100) {
      return `${year}-${String(mon).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
  }

  // Pattern: "July 25, 2006", "July 25th 2006", "July 25 2006"
  const mdyMatch = s.match(/^([a-zA-Z\u0900-\u097F]+)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,)?\s+(\d{4})$/);
  if (mdyMatch) {
    const mon = MONTHS[mdyMatch[1].toLowerCase()];
    const day = parseInt(mdyMatch[2], 10);
    const year = parseInt(mdyMatch[3], 10);
    if (mon && day >= 1 && day <= 31 && year >= 1900 && year <= 2100) {
      return `${year}-${String(mon).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
  }

  // Pattern: DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY, DD MM YYYY
  const numMatch = s.match(/^(\d{1,2})[/\-.\s](\d{1,2})[/\-.\s](\d{4})$/);
  if (numMatch) {
    const day = parseInt(numMatch[1], 10);
    const mon = parseInt(numMatch[2], 10);
    const year = parseInt(numMatch[3], 10);
    if (mon >= 1 && mon <= 12 && day >= 1 && day <= 31 && year >= 1900 && year <= 2100) {
      return `${year}-${String(mon).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
  }

  // Pattern: YYYY/MM/DD, YYYY-M-D, YYYY.MM.DD
  const ymdMatch = s.match(/^(\d{4})[/\-.\s](\d{1,2})[/\-.\s](\d{1,2})$/);
  if (ymdMatch) {
    const year = parseInt(ymdMatch[1], 10);
    const mon = parseInt(ymdMatch[2], 10);
    const day = parseInt(ymdMatch[3], 10);
    if (mon >= 1 && mon <= 12 && day >= 1 && day <= 31) {
      return `${year}-${String(mon).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    }
  }

  // Native Date parsing fallback
  const d = new Date(s);
  if (!isNaN(d.getTime())) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    if (y >= 1900 && y <= 2100) {
      return `${y}-${m}-${day}`;
    }
  }

  return raw;
};

/**
 * Scans text or transcript for spoken date of birth in English, Marathi, or Hindi.
 */
export const extractDobFromText = (raw) => {
  if (!raw || typeof raw !== 'string') return null;
  const text = normalizeDevanagariDigits(raw);
  const patterns = [
    /(?:date\s+of\s+birth|birth\s*date|dob|born\s+on|born|जन्मतारीख|जन्म\s*तारीख)\s*(?:is|:|-)?\s*(\d{1,2}(?:st|nd|rd|th)?\s+[a-zA-Z\u0900-\u097F]+(?:,)?\s+\d{4})/i,
    /(?:date\s+of\s+birth|birth\s*date|dob|born\s+on|born|जन्मतारीख|जन्म\s*तारीख)\s*(?:is|:|-)?\s*([a-zA-Z\u0900-\u097F]+\s+\d{1,2}(?:st|nd|rd|th)?(?:,)?\s+\d{4})/i,
    /(?:date\s+of\s+birth|birth\s*date|dob|born\s+on|born|जन्मतारीख|जन्म\s*तारीख)\s*(?:is|:|-)?\s*(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{4})/i,
    /(\d{1,2}(?:st|nd|rd|th)?\s+(?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|जुलै|जुलाई|जानेवारी|फेब्रुवारी|मार्च|एप्रिल|मे|जून|ऑगस्ट|सप्टेंबर|ऑक्टोबर|नोव्हेंबर|डिसेंबर|जनवरी|फरवरी|मई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर)\s*,?\s*\d{4})/i,
    /((?:january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec|जुलै|जुलाई|जानेवारी|फेब्रुवारी|मार्च|एप्रिल|मे|जून|ऑगस्ट|सप्टेंबर|ऑक्टोबर|नोव्हेंबर|डिसेंबर|जनवरी|फरवरी|मई|अगस्त|सितंबर|अक्टूबर|नवंबर|दिसंबर)\s+\d{1,2}(?:st|nd|rd|th)?\s*,?\s*\d{4})/i,
    /(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{4})/,
  ];
  for (const pat of patterns) {
    const m = text.match(pat);
    if (m && m[1]) return m[1].trim();
  }
  return null;
};

/**
 * Calculates approximate age in whole years from an ISO YYYY-MM-DD date string.
 */
export const calculateAgeFromDob = (dobIso) => {
  if (!dobIso || !/^\d{4}-\d{2}-\d{2}$/.test(dobIso)) return null;
  try {
    const [y, m, d] = dobIso.split('-').map(Number);
    const birthDate = new Date(y, m - 1, d);
    const today = new Date();
    let age = today.getFullYear() - birthDate.getFullYear();
    const monthDiff = today.getMonth() - birthDate.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
      age--;
    }
    return age >= 0 ? age : null;
  } catch {
    return null;
  }
};

/**
 * Normalizes gender input from English/Hindi/Marathi.
 */
export const normalizeGender = (val) => {
  if (!val) return '';
  const s = String(val).trim().toLowerCase();
  if (s === 'male' || s === 'm' || s === 'पुरुष' || s === 'मुलगा' || s === 'boy' || s === 'man') {
    return 'Male';
  }
  if (s === 'female' || s === 'f' || s === 'महिला' || s === 'स्त्री' || s === 'मुलगी' || s === 'girl' || s === 'woman') {
    return 'Female';
  }
  if (s === 'other' || s === 'इतर' || s === 'transgender') {
    return 'Other';
  }
  return s.charAt(0).toUpperCase() + s.slice(1);
};

/**
 * Normalizes relationship to head of family.
 */
export const normalizeRelationship = (val) => {
  if (!val) return '';
  const s = String(val).trim().toLowerCase();
  if (s.includes('self') || s.includes('स्वतः') || s.includes('head') || s.includes('प्रमुख')) return 'Self';
  if (s.includes('wife') || s.includes('husband') || s.includes('spouse') || s.includes('पती') || s.includes('पत्नी') || s.includes('बायको') || s.includes('नवरा')) return 'Spouse';
  if (s.includes('son') || s.includes('मुलगा') || s.includes('बेटा')) return 'Son';
  if (s.includes('daughter') || s.includes('मुलगी') || s.includes('बेटी')) return 'Daughter';
  if (s.includes('father') || s.includes('वडील') || s.includes('पिता')) return 'Father';
  if (s.includes('mother') || s.includes('आई') || s.includes('माता')) return 'Mother';
  return 'Other';
};

/**
 * Normalizes marital status.
 */
export const normalizeMaritalStatus = (val) => {
  if (!val) return '';
  const s = String(val).trim().toLowerCase();
  if (s.includes('unmarried') || s.includes('single') || s.includes('अविवाहित')) return 'Unmarried';
  if (s.includes('married') || s.includes('विवाहित')) return 'Married';
  if (s.includes('widow') || s.includes('विधवा')) return 'Widow';
  if (s.includes('separated') || s.includes('घटस्फोटित')) return 'Separated';
  return '';
};
