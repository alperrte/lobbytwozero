import { spawnSync } from 'node:child_process';
import { toolPath } from './install-tool.mjs';
const binary = toolPath('prek');
// The mandatory CI secret range scan is separate; no duplicated staged scan here.
const hooks = ['check-merge-conflict', 'trailing-whitespace', 'end-of-file-fixer', 'check-json', 'check-yaml', 'check-xml', 'check-added-large-files'];
let code = 0;
const config = spawnSync(binary, ['validate-config'], { encoding: 'utf8', windowsHide: true });
if (config.status !== 0) {
  console.error('prek validation failed or pinned binary unavailable; NOT VERIFIED.');
  process.exitCode = 1;
}
else {
  for (const hook of hooks) {
    const result = spawnSync(binary, ['run', hook, '--all-files'], { encoding: 'utf8', windowsHide: true, maxBuffer: 8 * 1024 * 1024 });
    console.log('prek ' + hook + ': ' + (result.status === 0 ? 'PASS' : 'FAILED') + '; raw parser output withheld');
    if (result.status !== 0) code = 1;
  }
  process.exitCode = code;
}
