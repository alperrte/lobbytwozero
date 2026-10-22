import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { repositoryRoot, execute, redact, cleanGenerated, freshFile, backendResults, readModules, clearBackendReports, withReportLock, withRunLog } from '../lib/quality-tools.mjs';
import { loadProfileEnv, requireNode } from '../lib/profile-env.mjs';
import { requireMaven25 } from '../pre-push/backend-check.mjs';
import { backendTestEnvironment } from '../pre-push/runtime-checks.mjs';

export const loadSonarEnv = loadProfileEnv;

export function gatePassed(data) {
  return data?.projectStatus?.status === 'OK';
}

export function sonarWayPolicy(conditions = []) {
  const condition = (metric, op, threshold) => conditions.some((item) => item.metric === metric && item.op === op && Number(item.error) === threshold);
  const ratings = ['new_security_rating', 'new_reliability_rating', 'new_maintainability_rating'].every((metric) => condition(metric, 'GT', 1));
  return condition('new_coverage', 'LT', 80) && condition('new_duplicated_lines_density', 'GT', 3) && (condition('new_violations', 'GT', 0) || ratings);
}

export function validateSonarHost(host) {
  const url = new URL(host);
  if (!['http:', 'https:'].includes(url.protocol) || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('SONAR_HOST_URL must be a local HTTP(S) server URL without credentials');
  return url;
}

export async function sonarRequest(host, endpoint, token) {
  const url = validateSonarHost(host);
  let response;
  try {
    response = await fetch(new URL(endpoint, url), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      redirect: 'error',
      signal: AbortSignal.timeout(10000),
    });
  } catch {
    throw new Error(`SonarQube bağlantısı kurulamadı: ${url.origin}. Seçilen profil için sonar.cmd up komutunu çalıştırın ve SONAR_HOST_URL/port ayarını kontrol edin.`);
  }
  if (!response.ok) {
    const error = new Error(`SonarQube API returned HTTP ${response.status}`);
    error.status = response.status;
    throw error;
  }
  return response.json();
}

// Assigned-gate configuration may require permissions that analysis tokens lack.
// The actual analysis gate and measured overall targets remain mandatory.
export async function readAssignedGatePolicy({host, project, token, request=sonarRequest}) {
  let name;
  try {
    const assigned = await request(host, '/api/qualitygates/get_by_project?project=' + encodeURIComponent(project), token);
    name = assigned.qualityGate?.name;
    if (!name) throw new Error('Assigned Quality Gate is unavailable');
    const policy = await request(host, '/api/qualitygates/show?name=' + encodeURIComponent(name), token);
    return {status:'VERIFIED', name, conditions:policy.conditions ?? []};
  } catch (error) {
    if (error.status !== 403) throw error;
    return {status:'NOT VERIFIED', ...(name ? {name} : {}), reason:'Assigned Quality Gate configuration returned HTTP 403'};
  }
}

export const scanRows = ['SonarQube Access', 'Backend Reports', 'Backend Analysis', 'Quality Gate Backend', 'Overall Targets Backend'];
const metricCandidates = ['coverage','new_coverage','duplicated_lines_density','new_duplicated_lines_density','security_rating','reliability_rating','sqale_rating','new_security_rating','new_reliability_rating','new_maintainability_rating','software_quality_security_rating','software_quality_reliability_rating','software_quality_maintainability_rating','new_software_quality_security_rating','new_software_quality_reliability_rating','new_software_quality_maintainability_rating'];

export function overallTargets(measures) {
  const values = Object.fromEntries((measures ?? []).filter(item => item.value !== undefined).map(item => [item.metric, Number(item.value)]));
  const security = values.software_quality_security_rating ?? values.security_rating;
  const reliability = values.software_quality_reliability_rating ?? values.reliability_rating;
  const maintainability = values.software_quality_maintainability_rating ?? values.sqale_rating;
  return security === 1 && reliability === 1 && maintainability === 1 && values.coverage >= 80 && values.duplicated_lines_density <= 3;
}

// Genel hedefler (A notları) kırmızıysa nedenini kural/dosya/satır/mesaj olarak yazdırır; token ve kaynak kod yazdırılmaz.
export async function explainFailingTargets({ host, project, token, request, say }) {
  try {
    const query = '/api/issues/search?componentKeys=' + encodeURIComponent(project)
      + '&types=VULNERABILITY,BUG&resolved=false&ps=100&s=SEVERITY';
    const found = await request(host, query, token);
    const issues = found.issues ?? [];
    say('Hedefleri bozan açık bulgular (' + (found.total ?? issues.length) + '):');
    for (const issue of issues) {
      const file = String(issue.component ?? '').split(':').slice(1).join(':');
      say('  [' + issue.severity + '/' + issue.type + '] ' + issue.rule + ' ' + file + ':' + (issue.line ?? '?') + ' ' + String(issue.message ?? '').slice(0, 160));
    }
    const fresh = await request(host, '/api/issues/search?componentKeys=' + encodeURIComponent(project)
      + '&inNewCodePeriod=true&resolved=false&ps=50&s=SEVERITY', token);
    say('Yeni koddaki açık bulgular (' + (fresh.total ?? (fresh.issues ?? []).length) + '):');
    for (const issue of fresh.issues ?? []) {
      const file = String(issue.component ?? '').split(':').slice(1).join(':');
      say('  [' + issue.severity + '/' + issue.type + '] ' + issue.rule + ' ' + file + ':' + (issue.line ?? '?') + ' ' + String(issue.message ?? '').slice(0, 160));
    }
  } catch (error) { say('[WARN] Bulgu ayrıntısı alınamadı: ' + error.message); }
}

export async function runScan({ root = repositoryRoot, profile = 'dev', ambient = process.env, runCommand = execute, request = sonarRequest, log = console.log } = {}) {
  const rows = Object.fromEntries(scanRows.map(name => [name, 'SKIPPED']));
  const details = {};
  let secrets = [ambient.SONAR_TOKEN];
  let env = ambient;
  const say = value => log(redact(value, secrets));
  const run = (command, args, cwd = root, capture = false) => runCommand(command, args, { cwd, env, capture, secrets, log: say });
  const mvn = process.platform === 'win32' ? 'mvn.cmd' : 'mvn';
  const started = Date.now();

  async function step(name, action) {
    say('\n[CHECK] ' + name);
    try { rows[name] = await action() ? 'PASS' : 'FAIL'; }
    catch (error) { rows[name] = 'FAIL'; details[name] = redact(error.message, secrets); say('[ERROR] ' + details[name]); }
  }

  try {
    requireNode();
    env = loadSonarEnv(root, profile, ambient);
    env.SONAR_HOST_URL ||= 'http://localhost:5199';
    secrets = [env.SONAR_TOKEN, env.SONAR_DB_PASSWORD];
    const host = env.SONAR_HOST_URL;
    const project = env.SONAR_BACKEND_PROJECT_KEY || 'ltz-backend';
    if (!/^[A-Za-z0-9_.:-]+$/.test(project)) throw new Error('Invalid backend project key');

    // Validate before the Maven scanner could receive the token.
    validateSonarHost(host);
    let metricKeys = [];

    await step('SonarQube Access', async () => {
      const server = await request(host, '/api/system/status');
      if (server.status !== 'UP') throw new Error('SonarQube is not ready: ' + server.status);
      if (!env.SONAR_TOKEN?.trim()) throw new Error('SONAR_TOKEN missing; set it in ignored .env.' + profile + ' or your shell');
      if (!(await request(host, '/api/authentication/validate', env.SONAR_TOKEN)).valid) {
        throw new Error(`SONAR_TOKEN geçersiz. ${validateSonarHost(host).origin} üzerindeki bu SonarQube sunucusunda analiz yetkili bir token üretip seçilen profilin SONAR_TOKEN alanını güncelleyin.`);
      }
      const gate = await request(host, '/api/qualitygates/show?name=Sonar%20way', env.SONAR_TOKEN);
      if (!sonarWayPolicy(gate.conditions)) throw new Error('Sonar way does not match the required new-code targets');
      const catalog = await request(host, '/api/metrics/search?ps=500', env.SONAR_TOKEN);
      const available = new Set((catalog.metrics ?? []).map(item => item.key));
      metricKeys = metricCandidates.filter(key => available.has(key));
      if (!metricKeys.includes('coverage') || !metricKeys.includes('duplicated_lines_density')) throw new Error('Required server metrics are unavailable');
      return true;
    });

    if (rows['SonarQube Access'] !== 'PASS') throw new Error('Scan stopped before building: SonarQube access failed');
    const modules = readModules(root);
    clearBackendReports(root, modules);

    await step('Backend Reports', async () => {
      const config = await run('docker', ['compose', '--env-file', `.env.${profile}`, '-f', 'docker-compose.yml', 'config', '--format', 'json'], root, true);
      if (config.code !== 0) throw new Error('Cannot resolve the selected application Compose profile for backend tests.');

      let model;
      try { model = JSON.parse(config.output); }
      catch { throw new Error('Cannot parse the selected application Compose profile for backend tests.'); }

      const testEnv = backendTestEnvironment(model, env);
      secrets = [...secrets, testEnv.POSTGRES_PASSWORD];

      const testRun = (command, args, cwd = root, capture = false) => runCommand(command, args,
        { cwd, env: testEnv, capture, secrets, log: say });

      await requireMaven25(testRun, mvn, root);
      const result = await testRun(mvn, ['-B', '-ntp', '-f', 'backend/pom.xml', '--fail-at-end', 'clean', 'verify']);

      if (result.code !== 0) throw new Error('Backend build/tests failed');
      details.backend = backendResults(root, modules, started);
      say(`Fresh reports: ${details.backend.tests} tests; local line coverage ${details.backend.lineCoverage.toFixed(2)}%`);
      return true;
    });

    await step('Backend Analysis', async () => {
      if (rows['Backend Reports'] !== 'PASS') throw new Error('Fresh successful tests and measurable coverage are required');
      cleanGenerated(root, 'backend/target/sonar');
      const result = await run(mvn, ['-B', '-ntp', '-f', 'backend/pom.xml', 'sonar:sonar',
        '-Dsonar.projectKey=' + project, '-Dsonar.qualitygate.wait=true', '-Dsonar.qualitygate.timeout=300']);
      const metadata = freshFile(root, 'backend/target/sonar/report-task.txt', started);
      const taskId = metadata.match(/^ceTaskId=(.+)$/m)?.[1]?.trim();
      if (!taskId) throw new Error('Scanner did not submit a current analysis');

      const deadline = Date.now() + 300000;
      let task;
      do {
        task = (await request(host, '/api/ce/task?id=' + encodeURIComponent(taskId), env.SONAR_TOKEN)).task;
        if (['SUCCESS', 'FAILED', 'CANCELED'].includes(task?.status)) break;
        await new Promise(resolve => setTimeout(resolve, 2000));
      } while (Date.now() < deadline);

      if (task?.status !== 'SUCCESS' || !task.analysisId) throw new Error('Analysis processing failed or timed out');
      const gate = await request(host, '/api/qualitygates/project_status?analysisId=' + encodeURIComponent(task.analysisId), env.SONAR_TOKEN);
      details.gate = gate.projectStatus;
      rows['Quality Gate Backend'] = gatePassed(gate) ? 'PASS' : 'FAIL';

      const metrics = await request(host, '/api/measures/component?component=' + encodeURIComponent(project) + '&metricKeys=' + metricKeys.join(','), env.SONAR_TOKEN);
      details.measures = metrics.component?.measures ?? [];
      rows['Overall Targets Backend'] = overallTargets(details.measures) ? 'PASS' : 'FAIL';
      say('Backend metrics (rating 1=A): ' + JSON.stringify(details.measures));

      if (rows['Overall Targets Backend'] === 'FAIL' || rows['Quality Gate Backend'] === 'FAIL') await explainFailingTargets({ host, project, token: env.SONAR_TOKEN, request, say });
      details.gatePolicy = await readAssignedGatePolicy({ host, project, token: env.SONAR_TOKEN, request });
      say('Assigned gate policy: ' + JSON.stringify(details.gatePolicy));

      if (result.code !== 0) say('[ERROR] Maven scanner exited with code ' + result.code);
      return result.code === 0;
    });
  } catch (error) { say('[ERROR] ' + error.message); }

  const passed = scanRows.every(name => rows[name] === 'PASS');
  say('\nLTZ SONARQUBE SCAN');
  for (const [name, status] of Object.entries(rows)) say(name.padEnd(26) + ' ' + status);
  say(passed ? 'SONAR SCAN PASSED' : 'SONAR SCAN FAILED');
  return { exitCode: passed ? 0 : 1, rows, details };
}

export async function runSonar({ action, root = repositoryRoot, profile = 'dev', ambient = process.env, runCommand = execute, request = sonarRequest, log = console.log } = {}) {
  let secrets = [ambient.SONAR_TOKEN];
  const say = value => log(redact(value, secrets));

  try {
    requireNode();
    if (!['up', 'status', 'scan', 'down'].includes(action) || !['dev', 'prod'].includes(profile)) throw new Error('Usage: sonar.cmd [up|status|scan|down] [dev|prod]');
    if (action === 'scan') return await withReportLock(root, () => runScan({ root, profile, ambient, runCommand, request, log: say }));

    const env = loadSonarEnv(root, profile, ambient);
    secrets = [env.SONAR_TOKEN, env.SONAR_DB_PASSWORD];
    const host = env.SONAR_HOST_URL || 'http://localhost:5199';
    validateSonarHost(host);
    const base = ['compose', '-p', 'ltz-sonar', '--env-file', '.env.' + profile, '-f', 'docker-compose.sonar.yml'];

    // The down command intentionally stops services and retains all volumes.
    const args = action === 'up' ? ['up', '-d', '--wait', '--wait-timeout', '360', 'sonar-db', 'sonarqube']
      : action === 'down' ? ['stop', 'sonarqube', 'sonar-db'] : ['ps'];

    const result = await runCommand('docker', [...base, ...args], { cwd: root, env, secrets, log: say });
    if (result.code !== 0) throw new Error('Docker operation failed: ' + result.output);

    if (action === 'status' || action === 'up') {
      const status = await request(host, '/api/system/status');
      say('SonarQube readiness: ' + status.status + '; version: ' + (status.version ?? 'unknown'));
      return { exitCode: status.status === 'UP' ? 0 : 1 };
    }

    say('SonarQube stopped; containers and volumes retained.');
    return { exitCode: 0 };
  } catch (error) { say('[ERROR] ' + error.message); return { exitCode: 1 }; }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length > 4) throw new Error('Too many arguments');
    const action = process.argv[2];
    const label = ['up', 'status', 'scan', 'down'].includes(action) ? 'sonarqube-' + action : 'sonarqube';
    process.exitCode = (await withRunLog(repositoryRoot, label, log => runSonar({ action, profile: process.argv[3] ?? 'dev', log }),
      { secrets: [process.env.SONAR_TOKEN] })).exitCode;
  } catch (error) { console.error('Sonar runner could not start: ' + error.message); process.exitCode = 1; }
}