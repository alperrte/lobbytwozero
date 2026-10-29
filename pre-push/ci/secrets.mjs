import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { repositoryRoot } from '../../lib/quality-tools.mjs';
import { sensitiveFile } from '../runtime-checks.mjs';
import { toolPath, tools } from './install-tool.mjs';

export const sha = value => typeof value === 'string' && /^[a-f0-9]{40}$/.test(value);
export function scanSecrets({ mode = 'staged', base, head, root = repositoryRoot, run = spawnSync, log = console.log } = {}) {
  // Never print scanner output: even an upstream error might include a matched value.
  const git = args => run('git', args, { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true });
  if (!['staged', 'worktree', 'range', 'history'].includes(mode)) throw new Error('Unknown scan mode');
  if (mode === 'range' && (!sha(base) || !sha(head))) throw new Error('Range requires full commit SHAs');
  if (mode === 'range' || mode === 'history') {
    const shallow = git(['rev-parse', '--is-shallow-repository']);
    if (shallow.status !== 0 || shallow.stdout.trim() !== 'false') throw new Error('Full history is required');
  }
  if (mode === 'range') {
    for (const ref of [base, head]) {
      if (git(['cat-file', '-e', ref + '^{commit}']).status !== 0) throw new Error('Commit missing; fetch full history first');
    }
  }
  const names = git(['ls-files', '--cached', '-z']);
  if (names.status !== 0) throw new Error('Cannot inspect tracked filenames');
  const forbidden = names.stdout.split('\0').filter(Boolean).filter(sensitiveFile);
  if (forbidden.length) {
    log('Secret filename gate FAILED; tracked sensitive-file count: ' + forbidden.length);
    return 1; // Do not read actual sensitive files from the working tree.
  }
  const binary = toolPath('gitleaks', root);
  const version = run(binary, ['version'], { encoding: 'utf8', windowsHide: true });
  if (version.status !== 0 || !version.stdout?.includes(tools.gitleaks.version)) throw new Error('Pinned Gitleaks unavailable');
  const common = ['--redact=100', '--no-banner', '--log-level=error', '--exit-code=10', '--ignore-gitleaks-allow',
    '--config', path.join(root, 'pre-push/ci/gitleaks.toml'),
    '--gitleaks-ignore-path', path.join(root, 'pre-push/ci/empty.gitleaksignore')];
  // Use upstream rules; repository config/inline allow comments cannot silently suppress the scan.
  const scannerEnv = { ...process.env };
  delete scannerEnv.GITLEAKS_CONFIG;
  delete scannerEnv.GITLEAKS_CONFIG_TOML;
  let args, input;
  if (mode === 'staged' || mode === 'worktree') {
    const diff = git(['diff', ...(mode === 'staged' ? ['--cached'] : ['HEAD']), '--no-ext-diff', '--no-textconv', '--unified=0', '--diff-filter=ACMR', '--']);
    if (diff.status !== 0) throw new Error('Cannot collect local changes');
    input = diff.stdout;
    args = ['stdin', ...common];
  } else {
    args = ['git', ...common, '--log-opts=' + (mode === 'history' ? '--all --full-history' : '--full-history ' + base + '..' + head), '.'];
  }
  const result = run(binary, args, { cwd: root, env: scannerEnv, input, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, windowsHide: true, timeout: mode === 'history' ? 600_000 : 120_000 });
  if (result.error || result.signal || ![0, 10].includes(result.status)) throw new Error('Scanner execution failed');
  log('Gitleaks ' + mode + ': ' + (result.status === 0 ? 'PASS' : 'FINDING — private triage required; values withheld'));
  return result.status === 0 ? 0 : 1;
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { process.exitCode = scanSecrets({ mode: process.argv[2], base: process.argv[3], head: process.argv[4] }); }
  catch { console.error('Secret scan ERROR — NOT VERIFIED; no raw scanner output retained.'); process.exitCode = 2; }
}
