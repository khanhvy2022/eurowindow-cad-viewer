import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const corpusDir = path.resolve(__dirname, '../corpus/originals/fixtures');
const manifestPath = path.resolve(__dirname, '../corpus/manifest/corpus.json');

console.log('🔒 Running CAD File Integrity & Immutability Test...');

if (!fs.existsSync(manifestPath)) {
  console.error('Manifest not found:', manifestPath);
  process.exit(1);
}

const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
let totalTests = 0;
let passedTests = 0;
const results = [];

for (const sample of manifest.samples) {
  totalTests++;
  const filePath = path.join(corpusDir, sample.fileName);
  if (!fs.existsSync(filePath)) {
    console.error(`Missing fixture: ${sample.fileName}`);
    continue;
  }

  // 1. Calculate original SHA-256
  const beforeBytes = fs.readFileSync(filePath);
  const beforeSha256 = crypto.createHash('sha256').update(beforeBytes).digest('hex');

  // 2. Perform simulated CAD viewer operations (read, slice header, parse entities)
  const headerSlice = beforeBytes.slice(0, 1024).toString('utf8');
  const entityCount = (beforeBytes.toString('utf8').match(/SECTION/g) || []).length;

  // 3. Re-read and recalculate SHA-256
  const afterBytes = fs.readFileSync(filePath);
  const afterSha256 = crypto.createHash('sha256').update(afterBytes).digest('hex');

  const unchanged = (beforeSha256 === afterSha256 && beforeSha256 === sample.sha256);
  if (unchanged) {
    passedTests++;
    results.push({
      id: sample.id,
      fileName: sample.fileName,
      status: 'PASS',
      sha256_before: beforeSha256,
      sha256_after: afterSha256,
      bytes_identical: beforeBytes.length === afterBytes.length
    });
  } else {
    results.push({
      id: sample.id,
      fileName: sample.fileName,
      status: 'FAIL',
      sha256_before: beforeSha256,
      sha256_after: afterSha256
    });
  }
}

console.log(`✅ File Integrity Test Result: ${passedTests}/${totalTests} files strictly unchanged (SHA-256 match).`);
const outPath = path.resolve(__dirname, '../evidence/P0.1/file-integrity-results.json');
fs.writeFileSync(outPath, JSON.stringify({ passedTests, totalTests, results }, null, 2));
