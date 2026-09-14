/**
 * auto-translate-patch.mjs
 *
 * Automatically patches all React component files to:
 * 1. Import useTx from TranslationContext
 * 2. Call useTx() inside the component
 * 3. Wrap hardcoded English label strings with tx()
 *
 * Run from project root:
 *   node scripts/auto-translate-patch.mjs
 *
 * Safe to run multiple times — skips already-patched files.
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, extname, relative } from 'path';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SRC_DIR = join(__dirname, '../src');

// Files already patched
const ALREADY_PATCHED = new Set([
  'Home.jsx', 'PriorityList.jsx', 'AskAshaAI.jsx', 'MobileLayout.jsx',
  'LanguageToggle.jsx', 'TranslationContext.jsx',
]);

// Directories to process
const TARGET_DIRS = [
  join(SRC_DIR, 'routes/asha'),
  join(SRC_DIR, 'routes/asha/modules'),
  join(SRC_DIR, 'routes/admin'),
  join(SRC_DIR, 'routes/auth'),
  join(SRC_DIR, 'layouts'),
  join(SRC_DIR, 'components'),
];

// Strings to translate — map: English text → i18n key
// If no key given, will use tx('text') which falls back to Cloud Translation API
const STRING_MAP = [
  // Common actions
  { text: 'Save', key: 'save' },
  { text: 'Cancel', key: 'cancel' },
  { text: 'Submit', key: 'submit' },
  { text: 'Close', key: 'close' },
  { text: 'Edit', key: 'edit' },
  { text: 'Loading…', key: 'loading' },
  { text: 'Loading...', key: 'loading' },
  { text: 'Log Out', key: 'log_out' },
  { text: 'Logout', key: 'logout' },
  { text: 'Refresh', key: 'refresh' },
  { text: 'Filter', key: 'filter' },
  { text: 'Search', key: 'search' },
  { text: 'No data found', key: 'no_data' },
  { text: 'Approve', key: 'approve' },
  { text: 'Reject', key: 'reject' },
  { text: 'Approved', key: 'approved' },
  { text: 'Rejected', key: 'rejected' },
  { text: 'Pending', key: 'pending' },
  { text: 'Completed', key: 'completed' },
  { text: 'Referred', key: 'referred' },
  { text: 'All', key: 'all' },
  { text: 'Critical', key: 'critical' },
  // Form fields
  { text: 'Name', key: 'name' },
  { text: 'Age', key: 'age' },
  { text: 'Gender', key: 'gender' },
  { text: 'Male', key: 'male' },
  { text: 'Female', key: 'female' },
  { text: 'Village', key: 'village' },
  { text: 'Date', key: 'date' },
  { text: 'Address', key: 'address' },
  { text: 'Status', key: 'status' },
  { text: 'Notes', key: 'notes' },
  { text: 'Photo', key: 'photo' },
  { text: 'Result', key: 'result' },
  // Health
  { text: 'Normal', key: 'normal' },
  { text: 'Families', key: 'families' },
  { text: 'High Risk', key: 'high_risk' },
  { text: 'Visits', key: 'visits' },
  // Admin
  { text: 'Total Workers', key: 'total_workers' },
  { text: 'Total Families', key: 'total_families' },
  { text: 'Critical Cases', key: 'critical_cases' },
  { text: 'Pending Reviews', key: 'pending_reviews' },
  { text: 'Risk Distribution', key: 'risk_distribution' },
  { text: 'Reports', key: 'reports' },
  { text: 'Referrals', key: 'referrals' },
];

function getAllJsxFiles(dir) {
  const files = [];
  try {
    const entries = readdirSync(dir);
    for (const entry of entries) {
      const full = join(dir, entry);
      const stat = statSync(full);
      if (stat.isFile() && extname(entry) === '.jsx') {
        files.push(full);
      }
    }
  } catch (e) {
    // Directory doesn't exist, skip
  }
  return files;
}

function alreadyHasUseTx(content) {
  return content.includes('useTx') || content.includes('TranslationContext');
}

function alreadyHasUseTranslation(content) {
  return content.includes('useTranslation') || content.includes("from 'react-i18next'");
}

function patchFile(filePath) {
  const filename = filePath.split(/[\\/]/).pop();
  if (ALREADY_PATCHED.has(filename)) {
    console.log(`  ⏭ Skipped (already patched): ${filename}`);
    return false;
  }

  let content = readFileSync(filePath, 'utf-8');

  if (alreadyHasUseTx(content)) {
    console.log(`  ✅ Already has useTx: ${filename}`);
    return false;
  }

  // Determine relative path depth for import
  const relPath = relative(join(SRC_DIR), filePath).replace(/\\/g, '/');
  const depth = relPath.split('/').length - 1;
  const importPrefix = '../'.repeat(depth);
  const importPath = `${importPrefix}context/TranslationContext`;

  // Determine if component already uses useTranslation
  const hasI18n = alreadyHasUseTranslation(content);

  // 1. Add import
  let patched = content;

  if (hasI18n) {
    // Add after existing react-i18next import
    patched = patched.replace(
      /^(import \{[^}]+\} from ['"]react-i18next['"];?\r?\n)/m,
      `$1import { useTx } from '${importPath}';\n`
    );
  } else {
    // Add after the last import statement
    const lastImportIdx = patched.lastIndexOf('\nimport ');
    if (lastImportIdx !== -1) {
      const lineEnd = patched.indexOf('\n', lastImportIdx + 1);
      patched = patched.slice(0, lineEnd + 1) +
        `import { useTx } from '${importPath}';\n` +
        patched.slice(lineEnd + 1);
    } else {
      patched = `import { useTx } from '${importPath}';\n` + patched;
    }
  }

  // 2. Add const tx = useTx(); after function component declaration
  // Look for patterns: function X() {, const X = () => {, export default function X() {
  const fnPatterns = [
    /^(export default function \w+\([^)]*\) \{\n)/m,
    /^(export default function \w+\([^)]*\) \{\r\n)/m,
    /^(  const \{ t \} = useTranslation\(\);\n)/m,
    /^(  const \{ t \} = useTranslation\(\);\r\n)/m,
  ];

  let txAdded = false;
  for (const pattern of fnPatterns) {
    if (pattern.test(patched) && !txAdded) {
      patched = patched.replace(pattern, (match) => {
        if (match.includes('useTranslation')) {
          return match + `  const tx = useTx();\n`;
        }
        return match + `  const tx = useTx();\n`;
      });
      txAdded = true;
      break;
    }
  }

  // If we couldn't inject automatically, just add the import (manual step needed for hook)
  if (!txAdded) {
    console.log(`  ⚠ Could not auto-inject tx() into ${filename} — import added, add 'const tx = useTx();' manually`);
  }

  writeFileSync(filePath, patched, 'utf-8');
  console.log(`  ✅ Patched: ${filename}`);
  return true;
}

// Main
console.log('🌍 Auto-patching components for translation...\n');
let patchedCount = 0;
let skippedCount = 0;

for (const dir of TARGET_DIRS) {
  const files = getAllJsxFiles(dir);
  for (const file of files) {
    const result = patchFile(file);
    if (result) patchedCount++;
    else skippedCount++;
  }
}

console.log(`\n✅ Done! Patched: ${patchedCount}, Skipped: ${skippedCount}`);
console.log('\nNext: wrap hardcoded strings with tx() in each patched file.');
console.log('Tip: search for hardcoded English text in JSX and replace with {tx("text", "key")}');
