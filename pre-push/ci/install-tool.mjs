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
const networkCodes = new Set(['ECONNRESET', 'ECONNREFUSED', 'ETIMEDOUT', 'EAI_AGAIN', 'ENOTFOUND',
  'ENETUNREACH', 'EHOSTUNREACH', 'UND_ERR_CONNECT_TIMEOUT', 'UND_ERR_HEADERS_TIMEOUT', 'UND_ERR_BODY_TIMEOUT', 'UND_ERR_SOCKET']);
const downloadAttempts = 3;
export async function downloadArchive(url, { fetcher = fetch, wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds)), log = console.log } = {}) {
  for (let attempt = 1; attempt <= downloadAttempts; attempt++) {
    let httpStatus, retryAfter, reason, retryable;
    const signal = AbortSignal.timeout(60_000);
    try {
      const response = await fetcher(url, { signal });
      httpStatus = response.status;
      if (response.ok) return Buffer.from(await response.arrayBuffer());
      retryAfter = response.headers.get('retry-after');
      reason = 'HTTP_ERROR';
      retryable = httpStatus === 408 || httpStatus === 429 || (httpStatus >= 500 && httpStatus <= 599)
        || (httpStatus === 403 && (retryAfter !== null || response.headers.get('x-ratelimit-remaining') === '0'));
      await response.body?.cancel().catch(() => {});
    } catch (error) {
      const code = error.cause?.code ?? error.code;
      reason = signal.aborted || error.name === 'TimeoutError' ? 'TIMEOUT' : networkCodes.has(code) ? code : 'NETWORK_ERROR';
      retryable = reason === 'TIMEOUT' || networkCodes.has(code) || (error instanceof TypeError && code === undefined);
    }
    const diagnostic = { category: 'DEPENDENCY_ERROR', stage: 'download', attempts: attempt, reason,
      ...(Number.isInteger(httpStatus) && httpStatus >= 100 && httpStatus <= 599 ? { httpStatus } : {}) };
    if (!retryable || attempt === downloadAttempts) {
      throw Object.assign(new Error('Official tool download failed'), diagnostic);
    }
    const delay = Math.min(5000, Math.max(1000 * 2 ** (attempt - 1), /^\d{1,6}$/.test(retryAfter ?? '') ? Number(retryAfter) * 1000 : 0));
    log('Tool download retry: ' + JSON.stringify({ ...diagnostic, delayMs: delay }));
    await wait(delay);
  }
}
export async function installTool(name, { root = repositoryRoot, fetcher = fetch, wait, log = console.log } = {}) {
  const spec = tools[name], platform = spec?.platforms[process.platform + '-' + process.arch];
  if (!platform) throw new Error('Unsupported tool/platform');
  const target = toolPath(name, root);
  const directory = generatedPath(root, '.cache/tools/' + name + '/' + spec.version);
  // Always verify the pinned archive before extraction, including on repeated installs.
  const bytes = await downloadArchive('https://github.com/' + spec.repository + '/releases/download/v' + spec.version + '/' + platform.asset,
    { fetcher, wait, log });
  verifyArchive(bytes, platform.sha256);
  fs.mkdirSync(directory, { recursive: true });
  const archive = generatedPath(root, '.cache/tools/' + name + '/' + spec.version + '/' + platform.asset);
  fs.writeFileSync(archive, bytes);
  // Only the expected executable is extracted, never upstream installers/scripts.
  extractExecutable(archive, directory, platform.binary);
  if (!fs.existsSync(target) || fs.lstatSync(target).isSymbolicLink()) {
    throw Object.assign(new Error('Verified executable unavailable'), { category: 'CONFIGURATION_ERROR', stage: 'extraction' });
  }
  if (process.platform !== 'win32') fs.chmodSync(target, 0o755);
  log(name + ' ' + spec.version + ': verified official archive; installed ' + path.relative(root, target));
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await installTool(process.argv[2]); }
  catch (error) {
    const category = error.category ?? (['EACCES', 'EPERM', 'ENOENT'].includes(error.code) ? 'ENVIRONMENT_ERROR' : 'UNKNOWN');
    console.error('LTZ_DIAG:' + JSON.stringify({ category, stage: error.stage ?? 'file-access',
      ...(error.stage === 'download' ? { attempts: error.attempts, reason: error.reason, httpStatus: error.httpStatus } : {}) }));
    console.error('Tool install FAILED; raw details withheld; no scanner success claimed.'); process.exitCode = 2;
  }
}
