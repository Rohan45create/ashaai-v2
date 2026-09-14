import jsQR from 'jsqr';
import Tesseract from 'tesseract.js';

// ─── Verhoeff Checksum Algorithm (Client-side, 0 network calls) ───────────────
// Multiplication table in D5
const D = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
  [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
  [4, 0, 1, 2, 3, 9, 5, 6, 7, 8],
  [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2],
  [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
  [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0]
];

// Permutation table P
const P = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
  [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
  [9, 4, 5, 3, 1, 2, 6, 8, 7, 0],
  [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5],
  [7, 0, 4, 6, 9, 1, 3, 2, 5, 8]
];

/**
 * Validates 12-digit Aadhaar using Verhoeff Checksum Algorithm
 */
export function validateVerhoeff(numStr) {
  if (!numStr || typeof numStr !== 'string') return false;
  const cleaned = numStr.replace(/\D/g, '');
  if (cleaned.length !== 12) return false;

  let c = 0;
  const digits = cleaned.split('').map(Number).reverse();

  for (let i = 0; i < digits.length; i++) {
    c = D[c][P[i % 8][digits[i]]];
  }

  return c === 0;
}

/**
 * Preprocesses image on a canvas to optimize OCR text extraction
 */
export function preprocessImage(imgElement) {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  
  // Scale down if image is massive to save browser memory while preserving readability
  const maxDim = 1600;
  let width = imgElement.width || imgElement.videoWidth || imgElement.naturalWidth || 800;
  let height = imgElement.height || imgElement.videoHeight || imgElement.naturalHeight || 600;
  
  if (width > maxDim || height > maxDim) {
    if (width > height) {
      height = Math.round((height * maxDim) / width);
      width = maxDim;
    } else {
      width = Math.round((width * maxDim) / height);
      height = maxDim;
    }
  }
  
  canvas.width = width;
  canvas.height = height;
  ctx.drawImage(imgElement, 0, 0, width, height);

  const imgData = ctx.getImageData(0, 0, width, height);
  const data = imgData.data;

  // Grayscale & mild contrast enhancement
  for (let i = 0; i < data.length; i += 4) {
    const gray = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
    // Contrast stretch
    const contrast = 1.2;
    const adjusted = Math.min(255, Math.max(0, (gray - 128) * contrast + 128));
    data[i] = adjusted;
    data[i + 1] = adjusted;
    data[i + 2] = adjusted;
  }
  
  ctx.putImageData(imgData, 0, 0);
  return canvas;
}

/**
 * Parses raw OCR text into structured Aadhaar fields
 */
export function parseAadhaarText(text) {
  if (!text || typeof text !== 'string') {
    return { aadhaar_raw: '', name: '', dob: '', gender: '', pincode: '', confidence: 0 };
  }

  const lines = text.split('\n').map(l => l.trim()).filter(Boolean);
  let aadhaarNumber = '';
  let dob = '';
  let gender = '';
  let name = '';
  let pincode = '';

  // 1. Extract Aadhaar Number (12 digits, often 4 4 4)
  // Pattern: 4 digits, optional space, 4 digits, optional space, 4 digits
  const aadhaarMatches = text.match(/\b([2-9][0-9]{3}\s?[0-9]{4}\s?[0-9]{4})\b/g) || [];
  for (const match of aadhaarMatches) {
    const cleaned = match.replace(/\s+/g, '');
    if (cleaned.length === 12) {
      if (validateVerhoeff(cleaned)) {
        aadhaarNumber = cleaned;
        break; // Verhoeff checksum matched! Guaranteed valid Aadhaar format
      } else if (!aadhaarNumber) {
        aadhaarNumber = cleaned; // Fallback candidate if checksum fails due to minor OCR character blur
      }
    }
  }

  // 2. Extract DOB / Year of Birth
  // DD/MM/YYYY or DD-MM-YYYY
  const dobMatch = text.match(/(?:DOB|D\.O\.B|Date\s+of\s+Birth|Birth)[\s:]*([0-3]?[0-9][\/\-\.][0-1]?[0-9][\/\-\.][12][90][0-9]{2})/i);
  if (dobMatch && dobMatch[1]) {
    const rawDate = dobMatch[1].replace(/[\-\.]/g, '/');
    const [d, m, y] = rawDate.split('/');
    if (y && m && d) {
      dob = `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`;
    }
  } else {
    // Search standalone DD/MM/YYYY
    const standaloneMatch = text.match(/\b([0-3][0-9]\/[0-1][0-9]\/[12][90][0-9]{2})\b/);
    if (standaloneMatch) {
      const [d, m, y] = standaloneMatch[1].split('/');
      dob = `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`;
    } else {
      // Check for Year of Birth: YYYY
      const yobMatch = text.match(/(?:Year\s+of\s+Birth|YOB)[\s:]*([12][90][0-9]{2})/i);
      if (yobMatch && yobMatch[1]) {
        dob = `${yobMatch[1]}-01-01`;
      }
    }
  }

  // 3. Extract Gender
  if (/\b(FEMALE|Female|महिला)\b/i.test(text)) {
    gender = 'Female';
  } else if (/\b(MALE|Male|पुरुष)\b/i.test(text)) {
    gender = 'Male';
  } else if (/\b(TRANSGENDER|Transgender)\b/i.test(text)) {
    gender = 'Other';
  }

  // 4. Extract Name
  // Typically appears above the DOB line and below Government header
  const ignorePatterns = [
    /government/i, /india/i, /bharat/i, /sarkar/i, /authority/i, /unique/i,
    /identification/i, /uidai/i, /help/i, /1947/i, /aadhaar/i, /enrollment/i,
    /download/i, /dob/i, /male/i, /female/i, /transgender/i, /father/i, /husband/i,
    /www\./i, /year/i, /birth/i, /address/i, /to/i
  ];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    // If line has DOB or gender, candidate name is usually the preceding non-header line
    if (line.match(/(?:DOB|D\.O\.B|Date\s+of\s+Birth|Male|Female|MALE|FEMALE)/i)) {
      if (i > 0) {
        for (let j = i - 1; j >= 0; j--) {
          const prev = lines[j].replace(/[^a-zA-Z\s]/g, '').trim();
          const isHeader = ignorePatterns.some(p => p.test(prev));
          if (!isHeader && prev.length >= 3 && prev.split(' ').length >= 1 && prev.split(' ').length <= 4) {
            name = prev;
            break;
          }
        }
      }
      break;
    }
  }

  // 5. Extract Pincode (6 digits, especially if scanning back side)
  const pinMatch = text.match(/\b([1-9][0-9]{5})\b/);
  if (pinMatch) {
    pincode = pinMatch[1];
  }

  return {
    aadhaar_raw: aadhaarNumber,
    name,
    dob,
    gender,
    pincode,
    rawText: text
  };
}

/**
 * Main On-Device Browser Extraction Function
 * 
 * Photographs front and/or back side of an Aadhaar card.
 * 1. Checks for QR code on canvas using jsQR (fastest & most accurate).
 * 2. If no QR or partial fields, runs browser-based Tesseract.js OCR.
 * 3. NEVER uploads the raw card image anywhere.
 */
export async function extractAadhaarFromCardImage(imageSource, onProgress) {
  let imgElement;

  if (imageSource instanceof HTMLVideoElement || imageSource instanceof HTMLImageElement || imageSource instanceof HTMLCanvasElement) {
    imgElement = imageSource;
  } else if (imageSource instanceof Blob || imageSource instanceof File) {
    imgElement = await new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = URL.createObjectURL(imageSource);
    });
  } else if (typeof imageSource === 'string') {
    imgElement = await new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = imageSource;
    });
  } else {
    throw new Error('Unsupported image source');
  }

  const canvas = preprocessImage(imgElement);
  const ctx = canvas.getContext('2d');
  const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);

  // ── Step 1: Quick QR Code Detection on the photograph ──
  onProgress?.({ status: 'checking_qr', progress: 0.1 });
  const qrCode = jsQR(imgData.data, imgData.width, imgData.height);
  if (qrCode && qrCode.data) {
    try {
      const parser = new DOMParser();
      const xml = parser.parseFromString(qrCode.data, 'text/xml');
      const root = xml.documentElement;

      if (root && root.nodeName !== 'parsererror') {
        const uid = root.getAttribute('uid') || '';
        const name = root.getAttribute('name') || '';
        const dobRaw = root.getAttribute('dob') || '';
        const gRaw = root.getAttribute('gender') || '';

        const gender = (gRaw === 'M' || gRaw === 'Male') ? 'Male' : (gRaw === 'F' || gRaw === 'Female') ? 'Female' : gRaw;
        let formattedDob = '';
        if (dobRaw) {
          const parts = dobRaw.includes('-') ? dobRaw.split('-') : [];
          if (parts.length === 3) {
            formattedDob = parts[0].length === 4
              ? dobRaw
              : `${parts[2]}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}`;
          }
        }

        onProgress?.({ status: 'completed', progress: 1.0 });
        console.info('[AadhaarOCR] QR code recognized on-device from card photograph.');
        return {
          aadhaar_raw: uid,
          name,
          dob: formattedDob,
          gender,
          pincode: root.getAttribute('pc') || '',
          source: 'qr_on_card',
          confidence: 100
        };
      }
    } catch {
      // Fall through to browser OCR
    }
  }

  // ── Step 2: On-Device Browser OCR via Tesseract.js ──
  onProgress?.({ status: 'running_browser_ocr', progress: 0.3 });
  console.info('[AadhaarOCR] Running on-device browser OCR. Image does not leave device.');

  try {
    const result = await Tesseract.recognize(canvas, 'eng', {
      logger: m => {
        if (m.status === 'recognizing text') {
          onProgress?.({ status: 'recognizing text', progress: 0.3 + (m.progress * 0.6) });
        }
      }
    });

    const parsed = parseAadhaarText(result.data.text);
    onProgress?.({ status: 'completed', progress: 1.0 });

    return {
      aadhaar_raw: parsed.aadhaar_raw,
      name: parsed.name,
      dob: parsed.dob,
      gender: parsed.gender,
      pincode: parsed.pincode,
      source: 'browser_ocr',
      confidence: Math.round(result.data.confidence || 75),
      rawText: result.data.text
    };
  } finally {
    // Revoke object URL if created
    if (imageSource instanceof Blob || imageSource instanceof File) {
      URL.revokeObjectURL(imgElement.src);
    }
  }
}
