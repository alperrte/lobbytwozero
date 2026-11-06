// Gerçek Auth + Performance + Gateway HTTP kabulü. Yalnız bu koşunun geçici kaynakları kullanılır.
import path from 'node:path';
import { spawn } from 'node:child_process';
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { isolatedEnvironment, CookieJar, scrub, requireFreshJar, captureCommand } from './hardware-auth-e2e.mjs';

async function until(action, name, timeout = 90000) {
  const end = Date.now() + timeout;
  do { const result = await action(); if (result) return result; await delay(300); } while (Date.now() < end);
  throw new Error(`Timeout: ${name}`);
}

async function listen(handler) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return { server, port: server.address().port, origin: `http://127.0.0.1:${server.address().port}` };
}

async function unusedPort() {
  const listener = await listen((_, response) => response.end());
  await new Promise(resolve => listener.server.close(resolve));
  return listener.port;
}

export async function runPerformanceAuthE2e(root, log = console.log) {
  const services = ['auth-service', 'performance-service', 'api-gateway'];
  for (const service of services) requireFreshJar(root, service);
  const children = [], containers = [], listeners = [], secrets = [];
  const label = `performance-auth-e2e-${randomUUID()}`;
  let checks = 0;
  const check = (value, name) => {
    if (!value) throw new Error(`FAIL: ${name}`);
    checks++; log(`[PASS] ${name}`);
  };
  const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, {
    cwd: root, env: isolatedEnvironment(extra), windowsHide: true,
  }), binary, secrets);
  const docker = (args, extra) => command('docker', args, extra);
  const container = async (image, ports, env = {}) => {
    const args = ['run', '-d', '--label', `ltz.performance-auth-e2e=${label}`];
    for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
    for (const name of Object.keys(env)) args.push('-e', name);
    args.push(image);
    const id = await docker(args, env);
    if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
    containers.push(id);
    const mapped = {};
    for (const port of ports) {
      const address = await docker(['port', id, `${port}/tcp`]);
      if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Non-loopback fixture port');
      mapped[port] = Number(address.split(':')[1]);
    }
    return { id, ports: mapped };
  };
  const sql = (db, query) => {
    if (!/^SELECT\s/i.test(query)) throw new Error('Only read-only fixture assertions are allowed');
    return docker(['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', query]);
  };
  const launch = async (service, port, env) => {
    const java = process.env.JAVA_HOME
      ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const child = spawn(java, ['-jar', requireFreshJar(root, service), '--server.address=127.0.0.1', `--server.port=${port}`], {
      cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'INFO', ...env }), windowsHide: true,
    });
    const running = { child, output: '' }; children.push(running);
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
      running.output = scrub(running.output + data, secrets).slice(-16000);
    });
    child.once('error', () => { running.failed = true; });
    await until(async () => {
      if (running.failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${running.output.slice(-4000)}`);
      try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok; }
      catch { return false; }
    }, `${service} readiness`);
  };
  const request = async (origin, route, expected, { jar, method = 'GET', body, headers = {} } = {}) => {
    const outgoing = { ...headers };
    if (jar) {
      outgoing.Cookie = jar.header(route);
      outgoing.Origin = origin;
      outgoing['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN') || '';
    }
    if (body !== undefined) outgoing['Content-Type'] = 'application/json';
    const response = await fetch(origin + route, { method, headers: outgoing, redirect: 'manual',
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(6000) });
    jar?.accept(response.headers);
    if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
    const text = await response.text();
    return text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : undefined;
  };
  try {
    const password = randomBytes(32).toString('base64url'), dbPassword = randomBytes(32).toString('base64url');
    const clientSecret = randomBytes(32).toString('base64url');
    const referenceClientSecret = randomBytes(32).toString('base64url');
    const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey
      .export({ format: 'der', type: 'pkcs8' }).toString('base64');
    const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
    secrets.push(password, dbPassword, clientSecret, referenceClientSecret, privateKey, hmac, totp);
    const dbEnv = { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword };
    const authDb = await container('postgres:17-alpine', [5432], dbEnv);
    const performanceDb = await container('postgres:17-alpine', [5432], dbEnv);
    const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
    for (const db of [authDb, performanceDb]) await until(async () => {
      try { return await sql(db, 'SELECT 1') === '1'; } catch { return false; }
    }, 'PostgreSQL readiness');
    const authPort = await unusedPort(), perfPort = await unusedPort(), gatewayPort = await unusedPort();
    const auth = `http://127.0.0.1:${authPort}`, perf = `http://127.0.0.1:${perfPort}`, gateway = `http://127.0.0.1:${gatewayPort}`;
    let erasureToken;
    const relay = await listen(async (incoming, outgoing) => {
      try {
        erasureToken = incoming.headers.authorization; secrets.push(erasureToken);
        const response = await fetch(perf + incoming.url, { method: incoming.method,
          headers: { Authorization: erasureToken }, redirect: 'manual', signal: AbortSignal.timeout(5000) });
        outgoing.writeHead(response.status); outgoing.end();
      } catch { outgoing.writeHead(503); outgoing.end(); }
    });
    const unavailable = await listen((_, response) => { response.writeHead(503); response.end(); });
    listeners.push(relay.server, unavailable.server);
    const dbProperties = db => ({ POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(db.ports[5432]),
      POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', DB_PERFORMANCE_NAME: 'fixture' });
    const authEnv = { ...dbProperties(authDb), SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey,
      AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gateway,
      FRONTEND_PUBLIC_URL: gateway, JWT_ACCESS_TTL_SECONDS: '60', AUTH_DELETION_GRACE_PERIOD: 'PT0S',
      AUTH_DELETION_POLL_INTERVAL: 'PT1S', AUTH_DELETION_INITIAL_BACKOFF: 'PT1S', AUTH_DELETION_MAX_BACKOFF: 'PT2S',
      AUTH_DELETION_HTTP_TIMEOUT: 'PT1S', AUTH_HARDWARE_SERVICE_CLIENT_SECRET: clientSecret,
      AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET: referenceClientSecret,
      LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: String(mail.ports[1025]), PERFORMANCE_SERVICE_URL: relay.origin };
    for (const name of ['USER', 'MAIL', 'GAME', 'LIBRARY', 'REVIEW', 'SOCIAL', 'MESSAGING', 'LFP', 'COMMUNITY', 'CONTENT', 'EVENT', 'HARDWARE', 'RECOMMENDATION', 'ADMIN', 'NOTIFICATION']) {
      authEnv[`${name}_SERVICE_URL`] = unavailable.origin;
    }
    await launch('auth-service', authPort, authEnv);
    await launch('performance-service', perfPort, { ...dbProperties(performanceDb), AUTH_SERVICE_URL: auth,
      GAME_SERVICE_URL: unavailable.origin, HARDWARE_SERVICE_URL: unavailable.origin });
    await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: auth, PERFORMANCE_SERVICE_URL: perf, CORS_ALLOWED_ORIGINS: gateway });
    check(await sql(performanceDb, 'SELECT count(*) FROM flyway_schema_history WHERE success') === '3', 'Performance Flyway V21-V23 and JPA validation');
    const mailOrigin = `http://127.0.0.1:${mail.ports[8025]}`;
    const message = (email, pattern) => until(async () => {
      const result = await (await fetch(mailOrigin + '/api/v1/search?query=' + encodeURIComponent('to:' + email))).json();
      for (const summary of result.messages || []) {
        const detail = await (await fetch(mailOrigin + '/api/v1/message/' + summary.ID)).json();
        const match = String(detail.Text).match(pattern);
        if (match) { secrets.push(match[1]); return match[1]; }
      }
      return false;
    }, 'SMTP delivery', 20000);
    const register = async () => {
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `perf_${suffix}@example.test`, username = `p_${suffix}`;
      secrets.push(email);
      await request(gateway, '/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
      const code = await message(email, /\b(\d{6})\b/);
      await request(gateway, '/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
      const jar = new CookieJar();
      await request(gateway, '/api/v1/auth/csrf', 204, { jar });
      await request(gateway, '/api/v1/auth/login', 200, { jar, method: 'POST', body: { identifier: email, password } });
      await request(gateway, '/api/v1/auth/csrf', 204, { jar });
      const token = jar.get('__Host-ltz_access'); secrets.push(token);
      return { jar, email, username, token, id: JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).sub };
    };
    const owner = await register(), other = await register();
    const devices = '/api/v1/performance/devices', consent = '/api/v1/performance/sync-consent';
    const batches = '/api/v1/performance/sync/batches', authDevices = '/api/v1/auth/devices';
    await request(gateway, devices, 401);
    await request(gateway, consent, 200, { jar: owner.jar, method: 'PUT' });
    await request(gateway, consent, 200, { jar: other.jar, method: 'PUT' });
    check(true, 'Real SMTP registration, login, Gateway cookie conversion and owner consent');
    const device = await request(gateway, authDevices, 201, { jar: owner.jar, method: 'POST', body: { label: 'Performance fixture', password } });
    secrets.push(device.deviceSecret);
    const credentials = { deviceId: device.deviceId, deviceSecret: device.deviceSecret };
    const link = await request(gateway, devices, 200, { jar: owner.jar, method: 'POST', body: { deviceId: device.deviceId } });
    await request(gateway, devices, 404, { jar: other.jar, method: 'POST', body: { deviceId: device.deviceId } });
    check(link.status === 'ACTIVE', 'Explicit link checks actual Auth device ownership; foreign account gets 404');
    const issued = await request(gateway, authDevices + '/token', 200, { method: 'POST', body: { ...credentials, target: 'performance' } });
    secrets.push(issued.accessToken);
    check(issued.scope === 'performance:sync:write' && issued.expiresIn === 60, 'Auth target=performance issues the agreed device token');
    const tokenHeaders = { Authorization: `Bearer ${issued.accessToken}`, 'Idempotency-Key': randomUUID() };
    const batch = { schemaVersion: 1, clientSentAt: new Date().toISOString(), sampleCount: 1 };
    const first = await request(gateway, batches, 200, { method: 'POST', headers: tokenHeaders, body: batch });
    const replay = await request(gateway, batches, 200, { method: 'POST', headers: tokenHeaders, body: batch });
    check(!first.replayed && replay.replayed && first.batchId === replay.batchId, 'Actual device token accepts sync and idempotent replay');
    const hardwareToken = await request(gateway, authDevices + '/token', 200, { method: 'POST', body: credentials });
    secrets.push(hardwareToken.accessToken);
    await request(gateway, batches, 401, { method: 'POST', body: batch, headers: { ...tokenHeaders, Authorization: `Bearer ${hardwareToken.accessToken}` } });
    await request(gateway, devices, 401, { headers: { Authorization: `Bearer ${issued.accessToken}` } });
    check(true, 'Hardware device audience and device identity on user routes are rejected');
    const serviceRequest = { clientId: 'hardware-service', clientSecret, audience: 'performance-service', accountId: owner.id };
    await request(auth, '/internal/v1/service-tokens', 400, { method: 'POST', body: { ...serviceRequest, accountId: undefined } });
    const service = await request(auth, '/internal/v1/service-tokens', 200, { method: 'POST', body: serviceRequest });
    secrets.push(service.accessToken);
    const serviceHeaders = { Authorization: `Bearer ${service.accessToken}` };
    const verifyPath = `/internal/v1/accounts/${owner.id}/devices/${device.deviceId}`;
    await request(perf, verifyPath, 204, { headers: serviceHeaders });
    await request(perf, `/internal/v1/accounts/${other.id}/devices/${device.deviceId}`, 403, { headers: serviceHeaders });
    const summaryPath = `/internal/v1/accounts/${owner.id}/performance-summary?hardwareProfileId=${randomUUID()}`;
    const referencePath = `/internal/v1/accounts/${owner.id}/games/${randomUUID()}/performance-reference`;
    const summary = await request(perf, summaryPath, 200, { headers: serviceHeaders });
    await request(perf, referencePath, 403, { headers: serviceHeaders });
    await request(perf, `/internal/v1/accounts/${owner.id}`, 403, { method: 'DELETE', headers: serviceHeaders });
    check(service.scope === 'devices:verify summary:read' && summary.sessionCount === 0,
      'Actual Hardware token verifies devices and reads its account summary; reference and erasure remain forbidden');
    const referenceRequest = { ...serviceRequest, clientId: 'recommendation-service', clientSecret: referenceClientSecret };
    await request(auth, '/internal/v1/service-tokens', 400, { method: 'POST', body: { ...referenceRequest, accountId: undefined } });
    const referenceToken = await request(auth, '/internal/v1/service-tokens', 200, { method: 'POST', body: referenceRequest });
    secrets.push(referenceToken.accessToken);
    const referenceHeaders = { Authorization: `Bearer ${referenceToken.accessToken}` };
    const reference = await request(perf, referencePath, 200, { headers: referenceHeaders });
    check(referenceToken.scope === 'reference:read' && reference.fpsSampleCount === 0 && reference.reliable === false,
      'Actual Recommendation token reads an empty performance reference without inventing FPS data');
    await request(perf, summaryPath, 403, { headers: referenceHeaders });
    await request(perf, verifyPath, 403, { headers: referenceHeaders });
    await request(perf, referencePath.replace(owner.id, other.id), 403, { headers: referenceHeaders });
    await request(perf, `/internal/v1/accounts/${owner.id}`, 403, { method: 'DELETE', headers: referenceHeaders });
    check(true, 'Recommendation cannot read Hardware summaries, verify devices, cross accounts or erase data');
    await request(gateway, `${authDevices}/${device.deviceId}`, 204, { jar: owner.jar, method: 'DELETE' });
    await request(gateway, authDevices + '/token', 401, { method: 'POST', body: { ...credentials, target: 'performance' } });
    const reconciled = await request(gateway, devices, 200, { jar: owner.jar });
    await request(gateway, batches, 403, { method: 'POST', headers: { ...tokenHeaders, 'Idempotency-Key': randomUUID() }, body: batch });
    await request(perf, verifyPath, 404, { headers: serviceHeaders });
    check(reconciled[0].status === 'DISABLED', 'Auth revocation prevents issuance; list refresh disables link and blocks an unexpired token');
    await request(gateway, '/api/v1/auth/account/deletion/request', 202, { jar: owner.jar, method: 'POST', body: { password } });
    const deletionToken = await message(owner.email, /#token=([A-Za-z0-9_-]+)/);
    await request(gateway, '/api/v1/auth/account/deletion/confirm', 202, { jar: owner.jar, method: 'POST', body: { token: deletionToken, username: owner.username } });
    await until(async () => await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE service='performance' AND status='CONFIRMED'") === '1', 'Auth Performance deletion ACK', 30000);
    for (const table of ['sync_consents', 'device_links', 'sync_batches']) {
      check(await sql(performanceDb, `SELECT count(*) FROM ${table} WHERE user_id='${owner.id}'`) === '0', `D4 removes ${table} for the target account`);
    }
    await request(perf, `/internal/v1/accounts/${owner.id}`, 204, { method: 'DELETE', headers: { Authorization: erasureToken } });
    await request(perf, `/internal/v1/accounts/${other.id}`, 403, { method: 'DELETE', headers: { Authorization: erasureToken } });
    await request(gateway, consent, 200, { jar: other.jar });
    await request(gateway, consent, 401, { method: 'PUT', headers: { Authorization: `Bearer ${owner.token}` } });
    check(true, 'Actual worker D4 is idempotent, account-bound, preserves another account and blocks resurrection');
    check(await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='PENDING'") === '15', 'Other 15 deletion targets stay pending without fake ACKs');
    log(`PERFORMANCE AUTH RUNTIME E2E PASS: ${checks} checks; global Saga NOT_VERIFIED; Game/Hardware consumers NOT_TESTED`);
    return { checks, status: 'PASS' };
  } catch (error) { throw new Error(scrub(error.message, secrets)); }
  finally {
    for (const { child } of children.reverse()) {
      if (child.exitCode === null && child.pid) {
        child.kill(); await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
        if (child.exitCode === null) child.kill('SIGKILL');
      }
    }
    for (const server of listeners) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
    for (const id of containers.reverse()) {
      const actual = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.performance-auth-e2e" }}', id]);
      if (actual !== label) throw new Error('Refusing cleanup outside this fixture');
      await docker(['rm', '-f', '-v', id]);
    }
    log(`[CLEANUP] ${children.length} fixture processes and ${containers.length} owned containers removed`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runPerformanceAuthE2e(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')).catch(error => {
    console.error(error.message); process.exitCode = 1;
  });
}
