#!/usr/bin/env node
// Yalnız geçici kaynaklarla Hardware–Auth gerçek HTTP kabul testi; gerçek ENV okunmaz.
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

export function isolatedEnvironment(extra = {}, source = process.env) {
  const safe = {};
  for (const name of ['PATH', 'Path', 'SystemRoot', 'WINDIR', 'ComSpec', 'TEMP', 'TMP', 'JAVA_HOME', 'USERPROFILE', 'DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_CONFIG']) {
    if (source[name]) safe[name] = source[name];
  }
  return { ...safe, ...extra };
}

export class CookieJar {
  cookies = new Map();
  accept(headers) {
    for (const line of headers.getSetCookie()) {
      const [pair, ...attributes] = line.split(';');
      const split = pair.indexOf('=');
      const name = pair.slice(0, split), value = pair.slice(split + 1);
      const cookiePath = attributes.find(a => /^\s*path=/i.test(a))?.trim().slice(5) || '/';
      if (attributes.some(a => /^\s*max-age=0\s*$/i.test(a))) this.cookies.delete(name);
      else this.cookies.set(name, { value, path: cookiePath });
    }
  }
  get(name) { return this.cookies.get(name)?.value; }
  header(requestPath) {
    return [...this.cookies].filter(([, c]) => requestPath === c.path || requestPath.startsWith(c.path.endsWith('/') ? c.path : c.path + '/'))
      .map(([name, c]) => `${name}=${c.value}`).join('; ');
  }
}

export function scrub(text, secrets) {
  let safe = String(text);
  for (const value of secrets.filter(Boolean)) safe = safe.split(value).join('[REDACTED]');
  return safe.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[JWT REDACTED]');
}

export function requireFreshJar(root, service) {
  const base = path.join(root, 'backend/services', service);
  const jar = path.join(base, 'target', `${service}-0.0.1-SNAPSHOT.jar`);
  if (!fs.existsSync(jar)) throw new Error(`Build ${service} with the full backend check first`);
  const sources = [path.join(root, 'backend/pom.xml'), path.join(base, 'pom.xml')];
  const sourceRoot = path.join(base, 'src/main');
  for (const entry of fs.readdirSync(sourceRoot, { recursive: true, withFileTypes: true })) {
    if (entry.isFile()) sources.push(path.join(entry.parentPath, entry.name));
  }
  const built = fs.statSync(jar).mtimeMs;
  if (sources.some(file => fs.statSync(file).mtimeMs > built)) throw new Error(`Rebuild stale ${service} runtime artifact`);
  return jar;
}

export function deletionFixtureEnvironment() {
  // Yalnız bu geçici test süreci; gerçek DEV/prod ortam dosyası değiştirilmez.
  return { AUTH_DELETION_GRACE_PERIOD: 'PT0S', JWT_ACCESS_TTL_SECONDS: '60' };
}

export function deletionFixturePending(total, confirmed, pending) {
  // Repo genelinde 5 uç hazır, 11 uç eksik. Bu fixture yalnız Hardware'ı başlatır.
  return total === 16 && confirmed === 1 && pending === 15;
}

export function captureCommand(child, name, secrets = []) {
  return new Promise((resolve, reject) => {
    let output = '', errors = '';
    child.stdout.on('data', data => { output = (output + data).slice(-48000); });
    child.stderr.on('data', data => { errors = (errors + data).slice(-48000); });
    child.once('error', () => reject(new Error(`Cannot start ${name}`)));
    child.once('close', code => code === 0 ? resolve(output.trim())
      : reject(new Error(`${name} failed: ${scrub((errors + output).slice(-2000), secrets)}`)));
  });
}

async function until(action, label, timeout = 90000) {
  const deadline = Date.now() + timeout;
  do {
    const result = await action();
    if (result) return result;
    await delay(300);
  } while (Date.now() < deadline);
  throw new Error(`Timeout: ${label}`);
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

export async function runHardwareAuthE2e(root, log = console.log) {
  // Kaynak eskiyse geçici kaynak oluşturmadan açık hata ver.
  for (const service of ['auth-service', 'hardware-service', 'api-gateway']) requireFreshJar(root, service);
  const containers = [], children = [], listeners = [], secrets = [];
  const label = `hardware-auth-e2e-${randomUUID()}`;
  let checks = 0;
  const check = (condition, name) => {
    if (!condition) throw new Error(`FAIL: ${name}`);
    checks++; log(`[PASS] ${name}`);
  };
  const command = (binary, args, extra = {}) => {
    const child = spawn(binary, args, { cwd: root, env: isolatedEnvironment(extra), windowsHide: true });
    return captureCommand(child, binary, secrets);
  };
  const docker = (args, extra) => command('docker', args, extra);
  const container = async (image, ports, extra = {}) => {
    const args = ['run', '-d', '--label', `ltz.hardware-auth-e2e=${label}`];
    for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
    for (const name of Object.keys(extra)) args.push('-e', name);
    args.push(image);
    const id = await docker(args, extra);
    if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
    containers.push(id);
    const mapped = {};
    for (const port of ports) {
      const address = await docker(['port', id, `${port}/tcp`]);
      if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Disposable port must be loopback only');
      mapped[port] = Number(address.split(':')[1]);
    }
    return { id, ports: mapped };
  };
  const sql = (db, query) => {
    if (!/^SELECT\s/i.test(query)) throw new Error('Only read-only fixture assertions are allowed');
    return docker(['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', query]);
  };
  const launch = async (service, port, extra) => {
    const jar = requireFreshJar(root, service);
    const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const child = spawn(java, ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${port}`], {
      cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'INFO', ...extra }), windowsHide: true,
    });
    const running = { child, output: '', service };
    children.push(running);
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
      running.output = (running.output + scrub(data.toString(), secrets)).slice(-48000);
    });
    child.once('error', () => { running.failed = true; });
    await until(async () => {
      if (running.failed || child.exitCode !== null) throw new Error(`${service} did not start: ${running.output.slice(-5000)}`);
      try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).status === 200; }
      catch { return false; }
    }, `${service} readiness`);
    return `http://127.0.0.1:${port}`;
  };
  const request = async (origin, route, expected, { jar, method = 'GET', body, headers = {}, csrf = false } = {}) => {
    const outgoing = { ...headers };
    if (jar) outgoing.Cookie = jar.header(route);
    if (csrf && jar) outgoing['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN');
    if (body !== undefined) outgoing['Content-Type'] = 'application/json';
    const response = await fetch(origin + route, { method, headers: outgoing,
      body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(6000) });
    jar?.accept(response.headers);
    if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
    const text = await response.text();
    return { response, body: text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : text || undefined };
  };
  try {
    const password = randomBytes(32).toString('base64url');
    const dbPassword = randomBytes(32).toString('base64url');
    const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
    const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
    secrets.push(password, dbPassword, privateKey, hmac, totp);
    const dbEnvironment = { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword };
    const authDb = await container('postgres:17-alpine', [5432], dbEnvironment);
    const hardwareDb = await container('postgres:17-alpine', [5432], dbEnvironment);
    const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
    for (const db of [authDb, hardwareDb]) await until(async () => {
      try { return await sql(db, 'SELECT 1') === '1'; } catch { return false; }
    }, 'disposable PostgreSQL');
    const authPort = await unusedPort(), hardwarePort = await unusedPort(), gatewayPort = await unusedPort();
    const authOrigin = `http://127.0.0.1:${authPort}`, hardwareOrigin = `http://127.0.0.1:${hardwarePort}`;
    const gatewayOrigin = `http://127.0.0.1:${gatewayPort}`;
    let erasureToken;
    const erasureResponses = [];
    const relay = await listen(async (incoming, outgoing) => {
      try {
        erasureToken = incoming.headers.authorization;
        secrets.push(erasureToken);
        const response = await fetch(hardwareOrigin + incoming.url, { method: incoming.method,
          headers: { Authorization: erasureToken }, redirect: 'manual', signal: AbortSignal.timeout(5000) });
        erasureResponses.push(response.status);
        outgoing.writeHead(response.status); outgoing.end();
      } catch { outgoing.writeHead(503); outgoing.end(); }
    });
    const unavailable = await listen((_, response) => { response.writeHead(503); response.end(); });
    listeners.push(relay.server, unavailable.server);
    const dbProperties = db => ({ POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(db.ports[5432]),
      POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', DB_HARDWARE_NAME: 'fixture' });
    const authEnvironment = { ...dbProperties(authDb), ...deletionFixtureEnvironment(),
      SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey,
      AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gatewayOrigin,
      FRONTEND_PUBLIC_URL: gatewayOrigin, AUTH_DELETION_POLL_INTERVAL: 'PT1S', AUTH_DELETION_INITIAL_BACKOFF: 'PT1S',
      AUTH_DELETION_MAX_BACKOFF: 'PT2S', AUTH_DELETION_HTTP_TIMEOUT: 'PT1S',
      LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: String(mail.ports[1025]),
      HARDWARE_SERVICE_URL: relay.origin };
    for (const service of ['USER', 'MAIL', 'GAME', 'LIBRARY', 'REVIEW', 'SOCIAL', 'MESSAGING', 'LFP', 'COMMUNITY', 'CONTENT', 'EVENT', 'RECOMMENDATION', 'PERFORMANCE', 'ADMIN', 'NOTIFICATION']) {
      authEnvironment[`${service}_SERVICE_URL`] = unavailable.origin;
    }
    await launch('auth-service', authPort, authEnvironment);
    await launch('hardware-service', hardwarePort, { ...dbProperties(hardwareDb), AUTH_SERVICE_URL: authOrigin });
    await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: authOrigin, HARDWARE_SERVICE_URL: hardwareOrigin,
      CORS_ALLOWED_ORIGINS: gatewayOrigin });
    check(await sql(hardwareDb, 'SELECT count(*) FROM flyway_schema_history WHERE success') === '8', 'Real Hardware Flyway V1-V8 and ddl-auto validation');
    const mailOrigin = `http://127.0.0.1:${mail.ports[8025]}`;
    const message = async (email, pattern) => until(async () => {
      const result = await (await fetch(mailOrigin + '/api/v1/search?query=' + encodeURIComponent('to:' + email))).json();
      for (const summary of result.messages || []) {
        const detail = await (await fetch(mailOrigin + '/api/v1/message/' + summary.ID)).json();
        const match = String(detail.Text).match(pattern);
        if (match) { secrets.push(match[1]); return match[1]; }
      }
      return false;
    }, 'Mailpit delivery', 20000);
    const register = async () => {
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `hardware_${suffix}@example.test`, username = `hw_${suffix}`;
      secrets.push(email);
      await request(gatewayOrigin, '/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
      const code = await message(email, /\b(\d{6})\b/);
      await request(gatewayOrigin, '/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
      const jar = new CookieJar();
      await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
      const result = await request(gatewayOrigin, '/api/v1/auth/login', 200, { jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { identifier: email, password } });
      const token = jar.get('__Host-ltz_access');
      secrets.push(token);
      check(Boolean(token) && result.response.headers.getSetCookie().some(c => c.startsWith('__Host-ltz_access=') && /HttpOnly/i.test(c) && /Secure/i.test(c)), 'Real Auth registration, SMTP verification, login and secure HttpOnly access cookie');
      check(result.body.status === 'AUTHENTICATED' && !result.body.accessToken, 'Auth token is absent from the response body');
      await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
      return { jar, token, email, username };
    };
    const owner = await register(), other = await register();
    const profiles = '/api/v1/hardware/profiles';
    await request(gatewayOrigin, profiles, 401);
    check(true, 'Anonymous Hardware access is 401 through the real Gateway');
    const create = (account, body, expected = 201) => request(gatewayOrigin, profiles, expected, { jar: account.jar,
      method: 'POST', csrf: true, headers: { Origin: gatewayOrigin }, body });
    const first = (await create(owner, { name: 'Runtime owner fixture', deviceType: 'DESKTOP' })).body;
    const second = (await create(other, { name: 'Runtime other fixture', deviceType: 'LAPTOP' })).body;
    check(Boolean(first.id) && Boolean(second.id), 'Real Gateway cookie-to-Bearer conversion creates owner profiles');
    await request(gatewayOrigin, `${profiles}/${first.id}`, 404, { jar: other.jar });
    await create(owner, { name: 'Rejected impersonation', deviceType: 'DESKTOP', userId: randomUUID() }, 400);
    check(true, 'Hardware IDOR and client-supplied owner rejection');
    await request(gatewayOrigin, profiles, 403, { jar: owner.jar, method: 'POST', body: { name: 'Missing CSRF', deviceType: 'DESKTOP' } });
    await request(gatewayOrigin, profiles, 403, { jar: owner.jar, method: 'POST', csrf: true,
      headers: { Origin: 'https://untrusted.example' }, body: { name: 'Wrong origin', deviceType: 'DESKTOP' } });
    check(true, 'Real Gateway rejects missing CSRF and an untrusted Origin');
    await request(hardwareOrigin, profiles, 401, { headers: { Cookie: `__Host-ltz_access=${owner.token}` } });
    await request(gatewayOrigin, '/api/v1/hardware/catalog/products', 403, { jar: owner.jar, method: 'POST', csrf: true, body: {} });
    check(true, 'Hardware accepts no direct cookie identity and PLAYER gains no catalog write permission');
    const headers = { Authorization: `Bearer ${owner.token}` };
    const accountId = JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).sub;
    check(JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).exp
      - JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).iat === 60,
    'Real user token lifetime is 60 seconds; local acceptance has at most 30 seconds skew');
    const deviceRoute = '/api/v1/auth/devices';
    const device = (await request(gatewayOrigin, deviceRoute, 201, { jar: owner.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { label: 'Hardware fixture', password } })).body;
    secrets.push(device.deviceSecret);
    const deviceCredentials = { deviceId: device.deviceId, deviceSecret: device.deviceSecret };
    const issued = (await request(gatewayOrigin, deviceRoute + '/token', 200,
      { method: 'POST', body: deviceCredentials })).body;
    secrets.push(issued.accessToken);
    check(issued.expiresIn === 60 && issued.scope === 'hardware:sync:write', 'Real Auth issues a narrow 60-second device token');
    const deviceHeaders = { Authorization: `Bearer ${issued.accessToken}` };
    const snapshot = { profileVersion: first.version, consentGeneration: randomUUID(), sequence: 1,
      scan: { schemaVersion: 1, capturedAt: new Date().toISOString(), components: [
        { componentKey: randomUUID(), category: 'CPU', modelName: 'Fixture CPU', quantity: 1 }] } };
    await request(gatewayOrigin, `${profiles}/${first.id}/sync/snapshots`, 404, { method: 'POST',
      headers: { ...deviceHeaders, 'Idempotency-Key': randomUUID() }, body: snapshot });
    await request(gatewayOrigin, profiles, 401, { headers: deviceHeaders });
    await request(hardwareOrigin, `/internal/v1/accounts/${accountId}`, 401, { method: 'DELETE', headers: deviceHeaders });
    check(true, 'Real device identity reaches snapshot policy; missing Performance contract denies apply and user/internal access');
    await request(gatewayOrigin, `${deviceRoute}/${device.deviceId}`, 204, { jar: owner.jar, method: 'DELETE', csrf: true,
      headers: { Origin: gatewayOrigin } });
    await request(gatewayOrigin, deviceRoute + '/token', 401, { method: 'POST', body: deviceCredentials });
    check(true, 'Fixture device is revoked after use and cannot obtain another token');
    await request(hardwareOrigin, `/internal/v1/accounts/${accountId}`, 401, { method: 'DELETE', headers });
    check(true, 'A real user access token cannot invoke internal D4');
    await request(gatewayOrigin, '/api/v1/auth/account/deletion/request', 202, { jar: owner.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { password } });
    const deletionToken = await message(owner.email, /#token=([A-Za-z0-9_-]+)/);
    await request(gatewayOrigin, '/api/v1/auth/account/deletion/confirm', 202, { jar: owner.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { token: deletionToken, username: owner.username } });
    await until(async () => await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE service='hardware' AND status='CONFIRMED'") === '1', 'Auth persisted Hardware ACK', 30000);
    check(erasureResponses.includes(204), 'Real Auth worker-issued D4 token receives Hardware 204 and persists CONFIRMED');
    check(await sql(hardwareDb, `SELECT count(*) FROM hardware_profiles WHERE id='${first.id}'`) === '0', 'D4 removes the owner profile from the disposable Hardware database');
    await request(hardwareOrigin, `/internal/v1/accounts/${accountId}`, 204, { method: 'DELETE', headers: { Authorization: erasureToken } });
    await request(hardwareOrigin, `/internal/v1/accounts/${randomUUID()}`, 403, { method: 'DELETE', headers: { Authorization: erasureToken } });
    check(true, 'The actual worker token supports idempotent retry and rejects another account');
    await request(hardwareOrigin, profiles, 401, { headers });
    await request(gatewayOrigin, profiles, 401, { method: 'POST', headers, body: { name: 'Rejected resurrection', deviceType: 'DESKTOP' } });
    await request(gatewayOrigin, `${profiles}/${second.id}`, 200, { jar: other.jar });
    check(true, 'Old user JWT cannot read or recreate data; another account remains accessible');
    check(deletionFixturePending(Number(await sql(authDb, 'SELECT count(*) FROM account_deletion_tasks')),
      Number(await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='CONFIRMED'")),
      Number(await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='PENDING'")))
      && await sql(authDb, "SELECT count(*) FROM accounts WHERE status='DELETION_REQUESTED'") === '1',
    'All 16 Auth targets remain configured; fixture leaves 15 ACKs pending (11 endpoints missing repo-wide, 4 ready services not started)');
    log(`HARDWARE AUTH RUNTIME E2E PASS: ${checks} checks; other-service Saga completion NOT_VERIFIED`);
    return { checks, status: 'PASS', globalSaga: 'NOT_VERIFIED' };
  } catch (error) {
    throw new Error(scrub(error.message, secrets));
  } finally {
    for (const { child } of children.reverse()) {
      if (child.exitCode === null && child.pid) {
        child.kill();
        await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
        if (child.exitCode === null) child.kill('SIGKILL');
      }
    }
    for (const server of listeners) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
    for (const id of containers.reverse()) {
      const resourceLabel = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.hardware-auth-e2e" }}', id]);
      if (resourceLabel !== label) throw new Error('Refusing cleanup of a resource outside this fixture');
      await docker(['rm', '-f', '-v', id]);
    }
    log(`[CLEANUP] ${children.length} fixture processes and ${containers.length} owned containers removed`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runHardwareAuthE2e(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')).catch(error => {
    console.error(error.message); process.exitCode = 1;
  });
}
