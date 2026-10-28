import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { repositoryRoot } from '../../lib/quality-tools.mjs';

// Only first-party tooling. Never traverse node_modules, generated reports or ENV files.
export function toolingTests(root = repositoryRoot) {
  const visit = relative => fs.readdirSync(path.join(root, relative), { withFileTypes: true }).flatMap(entry => {
    if (entry.isDirectory() && ['node_modules', '.cache', 'target', 'coverage', 'test-results'].includes(entry.name)) return [];
    if (entry.isSymbolicLink()) throw new Error('Tooling test link refused');
    const name = relative + '/' + entry.name;
    return entry.isDirectory() ? visit(name) : entry.isFile() && entry.name.endsWith('.test.mjs') ? [name] : [];
  });
  const tests = ['ops', 'pre-push', 'sonarqube', 'lib'].flatMap(visit).sort();
  if (!tests.length) throw new Error('Tooling test inventory is empty');
  return tests;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const tests = toolingTests();
    console.log('First-party tooling test files: ' + tests.length);
    const result = spawnSync(process.execPath, ['--test', ...tests], { cwd: repositoryRoot, stdio: 'inherit', windowsHide: true });
    process.exitCode = result.error || result.signal ? 1 : result.status ?? 1;
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
