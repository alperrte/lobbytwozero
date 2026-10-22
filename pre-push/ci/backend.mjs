import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { execute, repositoryRoot, readModules, backendResults, clearBackendReports } from '../../lib/quality-tools.mjs';
import { requireMaven25 } from '../backend-check.mjs';
import { prepareCoverage, enforceCoverage } from './coverage.mjs';

export async function backendCheck({ root = repositoryRoot, ambient = process.env, modules, external = false, runCommand = execute,
  prepare = prepareCoverage, coverageCheck = enforceCoverage, resetReports = clearBackendReports, log = console.log } = {}) {
  const available = readModules(root);
  const selected = modules ?? available;
  if (!selected.length || new Set(selected).size !== selected.length || selected.some(module => !available.includes(module))) throw new Error('Invalid selected backend modules');
  log('[SCOPE] ' + selected.join(', '));
  const project = 'ltz-test-' + randomUUID().slice(0, 8);
  // Deliberately allowlist the process environment; no developer DB/mail/OAuth secrets.
  const allowed = ['PATH', 'Path', 'SystemRoot', 'WINDIR', 'ComSpec', 'TEMP', 'TMP', 'HOME', 'USERPROFILE', 'JAVA_HOME',
    'GITHUB_ACTIONS', 'COVERAGE_BASE_SHA', 'COVERAGE_HEAD_SHA', 'COVERAGE_PR_HEAD_SHA'];
  const env = Object.fromEntries(allowed.filter(key => ambient[key]).map(key => [key, ambient[key]]));
  env.MAVEN_SKIP_RC = 'true';
  env.POSTGRES_PASSWORD = 'ltz_test_only';
  const run = (cmd, args, cwd = root, capture = false) => runCommand(cmd, args, { cwd, capture, env, log });
  const mvn = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
  const compose = ['compose', '--env-file', process.platform === 'win32' ? 'NUL' : '/dev/null', '-p', project, '-f', 'pre-push/ci/compose.test.yml'];
  let created = false, failure;
  try {
    await requireMaven25(run, mvn, root);
    env.LTZ_COVERAGE_CONTEXT = ambient.LTZ_COVERAGE_CONTEXT || await prepare(root);
    resetReports(root, selected);
    let port = 65432;
    if (external) {
      if (ambient.GITHUB_ACTIONS !== 'true') throw new Error('External test database mode is CI-only');
    } else {
      created = true; // clean up even a partially started disposable project
      if ((await run('docker', [...compose, 'up', '-d', '--wait', '--wait-timeout', '90'], root, true)).code !== 0) throw new Error('Disposable PostgreSQL startup failed');
      const result = await run('docker', [...compose, 'port', 'postgres', '5432'], root, true);
      const match = result.output.trim().match(/^127\.0\.0\.1:(\d+)$/);
      if (result.code !== 0 || !match) throw new Error('Cannot determine isolated PostgreSQL port');
      port = Number(match[1]);
    }
    const started = Date.now();
    const settings = 'pre-push/ci/maven-settings.xml';
    const localRepository = external ? path.join(env.HOME, '.m2/repository') : path.join(root, '.cache/m2');
    const result = await run(mvn, ['-B', '-ntp', '-s', settings, '-gs', settings, '-Dmaven.repo.local=' + localRepository,
      '-f', 'backend/pom.xml', '-pl', selected.join(','), '-Pci', '-Dltz.test.postgres.port=' + port, '--fail-at-end', 'verify']);
    if (result.code !== 0) throw new Error('Maven verify failed; tests/quality/coverage are not proven');
    // Re-evaluate the same central policy even if Maven's exec goal was skipped.
    // Legacy readers additionally retain report freshness/HTML/bytecode checks.
    backendResults(root, selected, started);
    for (const report of coverageCheck(root, selected, env.LTZ_COVERAGE_CONTEXT)) log(JSON.stringify(report));
    return 0;
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    if (created) {
      try {
        if ((await run('docker', [...compose, 'down', '--timeout', '10'], root, true)).code !== 0) {
          throw new Error('Disposable PostgreSQL cleanup failed: ' + project);
        }
      } catch (cleanupError) {
        if (failure) throw new AggregateError([failure, cleanupError], failure.message + '; ' + cleanupError.message);
        throw cleanupError;
      }
    }
  }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const args = process.argv.slice(2);
    let external = false, modules;
    for (let index = 0; index < args.length; index++) {
      if (args[index] === '--ci' && !external) external = true;
      else if (args[index] === '--modules' && modules === undefined && args[index + 1]) modules = args[++index].split(',');
      else throw new Error('Use --ci and/or --modules services/name,services/other');
    }
    process.exitCode = await backendCheck({ external, modules });
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
