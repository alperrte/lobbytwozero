import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { repositoryRoot, execute, backendTests, backendResults, readModules, clearBackendReports, withReportLock, withRunLog } from '../lib/quality-tools.mjs';
import { requireNode } from '../lib/profile-env.mjs';
import { requireMaven25 } from './backend-check.mjs';
import { checkGitSecrets, checkBackendHealth } from './runtime-checks.mjs';
import { checkRepositoryHygiene } from './hygiene-checks.mjs';

export const requiredRows = ['Git / Secret Check', 'Backend Tests', 'Backend Build', 'Backend Coverage', 'Docker Configuration'];

export function formatQualityStatus(value, stream = process.stdout) {
  const text = String(value);
  if (!stream.isTTY || Object.hasOwn(process.env, 'NO_COLOR') || process.env.TERM === 'dumb') return text;
  return text.replace(/^(NOT SAFE TO COMMIT|SAFE TO COMMIT)$/gm, status =>
    `\u001b[${status === 'SAFE TO COMMIT' ? 32 : 31}m${status}\u001b[0m`);
}

export async function runQualityChecks({ root = repositoryRoot, profile = 'dev', ambient = process.env, runCommand = execute, healthProbe, log = console.log } = {}) {
  const rows = Object.fromEntries([...requiredRows, 'Backend HTTP Health'].map(name => [name, 'SKIPPED']));
  const details = {};
  const env = Object.fromEntries(Object.entries(ambient).filter(([name]) => !name.startsWith('SONAR')));
  const run = (command, args, cwd = root, capture = false) => runCommand(command, args, { cwd, env, capture, log });
  const mvn = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
  const started = Date.now();
  let healthRequired = false;
  async function step(name, action) {
    log(`\n[CHECK] ${name}`);
    try { rows[name] = await action() ? 'PASS' : 'FAIL'; }
    catch (error) { rows[name] = 'FAIL'; details[name] = error.message; log(`[ERROR] ${error.message}`); }
  }
  try {
    if (!['dev', 'prod'].includes(profile)) throw new Error('Profile must be dev or prod');
    requireNode();
    const modules = readModules(root);
    clearBackendReports(root, modules);
    await step('Git / Secret Check', async () => (await checkGitSecrets(run, log)) && checkRepositoryHygiene({ run, log, root }));
    await step('Backend Tests', async () => {
      rows['Backend Build'] = 'FAIL';
      await requireMaven25(run, mvn, root);
      const result = await run(mvn, ['-B', '-ntp', '-f', 'backend/pom.xml', '--fail-at-end', 'clean', 'verify']);
      rows['Backend Build'] = result.code === 0 ? 'PASS' : 'FAIL';
      return result.code === 0 && backendTests(root, modules, started) > 0;
    });
    await step('Backend Coverage', async () => {
      if (rows['Backend Tests'] !== 'PASS') throw new Error('Successful fresh backend tests are required');
      const report = backendResults(root, modules, started);
      details.backend = report;
      log(`Backend: ${report.tests} tests; overall line coverage ${report.lineCoverage.toFixed(2)}% (target 80%)`);
      return report.lineCoverage >= 80;
    });
    let deployed = [];
    await step('Docker Configuration', async () => {
      const base = ['compose', '--env-file', `.env.${profile}`, '-f', 'docker-compose.yml'];
      if ((await run('docker', ['compose', 'version'], root, true)).code !== 0) throw new Error('Docker Compose unavailable');
      if ((await run('docker', [...base, 'config', '--quiet'], root, true)).code !== 0) throw new Error('Compose configuration failed; check the selected ENV profile locally');
      const result = await run('docker', [...base, 'config', '--services'], root, true);
      if (result.code !== 0) throw new Error('Cannot list configured Compose services');
      const services = result.output.trim().split(/\r?\n/);
      deployed = modules.filter(module => services.includes(module.split('/')[1]));
      healthRequired = deployed.length > 0;
      return services.includes('postgres');
    });
    if (healthRequired) {
      await step('Backend HTTP Health', async () => {
        const report = await checkBackendHealth({ run, profile, modules: deployed, probe: healthProbe, log });
        details.backendHttp = report;
        return report.passed;
      });
    } else {
      log('[SKIPPED] Backend HTTP Health: no backend services in the current Compose configuration.');
    }
    log('[SCOPE] Current backend and Compose configuration. No frontend exists; SonarQube runs separately.');
  } catch (error) { log(`[ERROR] ${error.message}`); }
  const safe = requiredRows.every(name => rows[name] === 'PASS') && (!healthRequired || rows['Backend HTTP Health'] === 'PASS');
  log('\n========================================\nLTZ LOCAL QUALITY CHECK\n========================================');
  for (const [name, value] of Object.entries(rows)) log(`${name.padEnd(24)} ${value}`);
  log('\n' + (safe ? 'SAFE TO COMMIT' : 'NOT SAFE TO COMMIT'));
  log('Result covers only the checks above; it does not prove application runtime or production readiness.');
  return { exitCode: safe ? 0 : 1, rows, details };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    process.exitCode = (await withRunLog(repositoryRoot, 'pre-push', log =>
      withReportLock(repositoryRoot, () => runQualityChecks({ profile: process.argv[2] ?? 'dev', log })),
    { log: value => console.log(formatQualityStatus(value)) })).exitCode;
  } catch (error) { console.error(error.message + '\nNOT SAFE TO COMMIT'); process.exitCode = 1; }
}
