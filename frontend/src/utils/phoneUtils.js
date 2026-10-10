/**
 * Normalizes an Indian mobile phone number to E.164 (+91XXXXXXXXXX).
 * Accepts:
 *  - 10 digits: "9876543210" -> "+919876543210"
 *  - 12 digits starting with 91: "919876543210" -> "+919876543210"
 *  - E.164 with +91: "+919876543210" -> "+919876543210"
 *
 * Returns null if the input is not a valid 10-digit Indian mobile number.
 */
export const normalizeIndianPhone = (phone) => {
  if (!phone) return null;
  const trimmed = String(phone).trim();
  const digits = trimmed.replace(/\D/g, '');

  if (digits.length === 10 && /^[6-9]\d{9}$/.test(digits)) {
    return `+91${digits}`;
  }

  if (digits.length === 12 && digits.startsWith('91') && /^91[6-9]\d{9}$/.test(digits)) {
    return `+${digits}`;
  }

  return null;
};

/**
 * Masks a phone number showing only the last 4 digits.
 * Example: "+919876543211" -> "+91******3211"
 */
export const maskPhone = (phone) => {
  if (!phone) return '****';
  const str = String(phone).trim();
  if (str.length <= 4) return '****';
  const lastFour = str.slice(-4);
  const prefix = str.startsWith('+91') ? '+91' : '';
  return `${prefix}******${lastFour}`;
};
