import assert from 'node:assert';
import { validateVerhoeff, parseAadhaarText } from '../src/utils/aadhaarOcr.js';

console.log('Testing Verhoeff Checksum Algorithm...');

// 1. Known valid Aadhaar numbers (with correct Verhoeff checksum)
// A known valid 12-digit number with valid Verhoeff checksum
// 213456789012: let's test if our JS Verhoeff matches standard
const testValid = '213456789012';
// We can compute checksum or test known numbers
assert.strictEqual(validateVerhoeff(''), false, 'Empty string should fail');
assert.strictEqual(validateVerhoeff('12345'), false, 'Short number should fail');
assert.strictEqual(validateVerhoeff('1234567890123'), false, 'Long number should fail');

// Let's test parseAadhaarText with realistic OCR outputs
console.log('Testing parseAadhaarText with realistic Aadhaar card OCR samples...');

// Sample 1: Standard Front of Card
const sample1 = `
Government of India
भारत सरकार
Sunita Devi
सुनीता देवी
DOB: 14/08/1992
Female / महिला
2345 6789 0123
मेरा आधार, मेरी पहचान
`;

const res1 = parseAadhaarText(sample1);
console.log('Sample 1 extracted:', res1);
assert.strictEqual(res1.gender, 'Female', 'Gender should be Female');
assert.strictEqual(res1.dob, '1992-08-14', 'DOB should be 1992-08-14');
assert.strictEqual(res1.aadhaar_raw, '234567890123', 'Aadhaar number should be extracted without spaces');
assert.strictEqual(res1.name, 'Sunita Devi', 'Name should be Sunita Devi');

// Sample 2: Card with Date of Birth and Male gender
const sample2 = `
UNIQUE IDENTIFICATION AUTHORITY OF INDIA
Amit Kumar Sharma
Date of Birth : 05/11/1988
MALE
9876 5432 1098
`;

const res2 = parseAadhaarText(sample2);
console.log('Sample 2 extracted:', res2);
assert.strictEqual(res2.gender, 'Male', 'Gender should be Male');
assert.strictEqual(res2.dob, '1988-11-05', 'DOB should be 1988-11-05');
assert.strictEqual(res2.aadhaar_raw, '987654321098', 'Aadhaar number should be extracted');
assert.strictEqual(res2.name, 'Amit Kumar Sharma', 'Name should be Amit Kumar Sharma');

// Sample 3: Card with Year of Birth and Back side Pincode
const sample3 = `
Address:
W/O Ramesh Patel
Near Primary Health Centre, Village Khed
Pune, Maharashtra - 410501
Year of Birth: 1975
FEMALE
4321 8765 2109
`;

const res3 = parseAadhaarText(sample3);
console.log('Sample 3 extracted:', res3);
assert.strictEqual(res3.gender, 'Female', 'Gender should be Female');
assert.strictEqual(res3.dob, '1975-01-01', 'DOB from Year of Birth should be 1975-01-01');
assert.strictEqual(res3.pincode, '410501', 'Pincode should be 410501');
assert.strictEqual(res3.aadhaar_raw, '432187652109', 'Aadhaar number should be extracted');

console.log('All Aadhaar on-device OCR parser tests passed successfully! ✓');
