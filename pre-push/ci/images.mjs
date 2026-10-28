import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { repositoryRoot, readModules } from '../../lib/quality-tools.mjs';
import { runTrivy } from './formats.mjs';
export function scanImages({ root = repositoryRoot, scan = runTrivy, log = console.log } = {}) {
  const images = new Set();
  for (const module of readModules(root)) {
    const dockerfile = fs.readFileSync(path.join(root, 'backend', module, 'Dockerfile'), 'utf8');
    for (const match of dockerfile.matchAll(/^FROM\s+(\S+)/gm)) {
      if (!/^[a-z0-9][a-z0-9./_:-]*(?:@sha256:[a-f0-9]{64})?$/.test(match[1])) throw new Error('Unreviewed image reference');
      images.add(match[1]);
    }
  }
  let code = 0;
  if (!images.size) throw new Error('No base images found; scan is NOT VERIFIED');
  for (const image of images) {
    try {
      const metadata = scan(['image', '--scanners', 'vuln', '--severity', 'HIGH,CRITICAL', '--exit-code', '1', '--timeout', '5m', image]);
      log('LTZ_IMAGE_RESULT:' + JSON.stringify(metadata));
    }
    catch (error) {
      log('LTZ_IMAGE_RESULT:' + JSON.stringify(error.imageMetadata ?? { image, status: 'SCANNER_ERROR', high: null, critical: null, total: null, truncated: false, vulnerabilities: [] }));
      log('LTZ_DIAG:' + JSON.stringify({ category: error.category ?? 'UNKNOWN', stage: error.stage ?? 'runtime' }));
      code = 1;
    }
  }
  return code;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { process.exitCode = scanImages(); }
  catch { console.error('Image inventory invalid; scan NOT VERIFIED; raw output withheld'); process.exitCode = 1; }
}
