import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { repositoryRoot, generatedPath } from '../../lib/quality-tools.mjs';

export const tools = JSON.parse(fs.readFileSync(new URL('./toolchain.json', import.meta.url), 'utf8'));
export function toolPath(name, root = repositoryRoot) {
  const spec = tools[name], platform = spec?.platforms[process.platform + '-' + process.arch];
  if (!platform) throw new Error('Unsupported tool/platform; use a reviewed upstream distribution');
  return generatedPath(root, '.cache/tools/' + name + '/' + spec.version + '/' + platform.binary);
}
export function verifyArchive(bytes, expected) {
  if (!/^[a-f0-9]{64}$/.test(expected) || createHash('sha256').update(bytes).digest('hex') !== expected) {
    throw Object.assign(new Error('Tool archive SHA256 mismatch; nothing will be executed'), { category: 'CONFIGURATION_ERROR', stage: 'integrity' });
  }
}
export function extractExecutable(archive, directory, binary, run = spawnSync) {
  const result = run('tar', ['-xf', archive, '-C', directory, binary], { stdio: 'pipe', windowsHide: true, timeout: 60_000 });
  if (result.error || result.status !== 0) {
    throw Object.assign(new Error('Verified archive extraction failed'),
      { category: result.error?.code === 'ETIMEDOUT' ? 'TIMEOUT' : 'CONFIGURATION_ERROR', stage: 'extraction' });
  }
}
export async function installTool(name) {
  const spec = tools[name], platform = spec?.platforms[process.platform + '-' + process.arch];
  if (!platform) throw new Error('Unsupported tool/platform');
  const target = toolPath(name);
  const directory = generatedPath(repositoryRoot, '.cache/tools/' + name + '/' + spec.version);
  // Always verify the pinned archive before extraction, including on repeated installs.
  let bytes;
  try {
    const response = await fetch('https://github.com/' + spec.repository + '/releases/download/v' + spec.version + '/' + platform.asset,
      { signal: AbortSignal.timeout(60_000) });
    if (!response.ok) throw new Error('Download failed');
    bytes = Buffer.from(await response.arrayBuffer());
  } catch {
    throw Object.assign(new Error('Official tool download failed'), { category: 'DEPENDENCY_ERROR', stage: 'download' });
  }
  verifyArchive(bytes, platform.sha256);
  fs.mkdirSync(directory, { recursive: true });
  const archive = generatedPath(repositoryRoot, '.cache/tools/' + name + '/' + spec.version + '/' + platform.asset);
  fs.writeFileSync(archive, bytes);
  // Only the expected executable is extracted, never upstream installers/scripts.
  extractExecutable(archive, directory, platform.binary);
  if (!fs.existsSync(target) || fs.lstatSync(target).isSymbolicLink()) {
    throw Object.assign(new Error('Verified executable unavailable'), { category: 'CONFIGURATION_ERROR', stage: 'extraction' });
  }
  if (process.platform !== 'win32') fs.chmodSync(target, 0o755);
  console.log(name + ' ' + spec.version + ': verified official archive; installed ' + path.relative(repositoryRoot, target));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await installTool(process.argv[2]); }
  catch (error) {
    const category = error.category ?? (['EACCES', 'EPERM', 'ENOENT'].includes(error.code) ? 'ENVIRONMENT_ERROR' : 'UNKNOWN');
    console.error('LTZ_DIAG:' + JSON.stringify({ category, stage: error.stage ?? 'file-access' }));
    console.error('Tool install FAILED; raw details withheld; no scanner success claimed.'); process.exitCode = 2;
  }
}
