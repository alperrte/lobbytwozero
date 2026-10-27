#!/usr/bin/env node
// Yalnız geçici kaynaklarla Auth + Gateway gerçek HTTP duman testi; gerçek ENV okunmaz.
// Gerekenler: Docker, JDK 25, güncel auth-service ve api-gateway jar'ları (backend kontrolünden sonra).
//   node ops/auth-smoke.mjs
// Taviz yok: tek bir kontrol başarısız olursa betik hata koduyla biter. Üretilen anahtar ve parolalar loga yazılmaz.
import path from 'node:path';
import { spawn } from 'node:child_process';
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { CookieJar, captureCommand, isolatedEnvironment, requireFreshJar, scrub } from './hardware-auth-e2e.mjs';

export function decodeJwtPart(token, index) {
  return JSON.parse(Buffer.from(token.split('.')[index], 'base64url').toString('utf8'));
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

export async function runAuthSmoke(root, log = console.log) {
  const containers = [], children = [], listeners = [], secrets = [];
  const label = `auth-smoke-${randomUUID()}`;
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
    const args = ['run', '-d', '--label', `ltz.auth-smoke=${label}`];
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
    const adminSecret = randomBytes(32).toString('base64url');
    const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
    const rotatedPublicKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
    const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
    secrets.push(password, dbPassword, adminSecret, privateKey, hmac, totp);
    const authDb = await container('postgres:17-alpine', [5432],
      { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword });
    const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
    await until(async () => {
      try { return await sql(authDb, 'SELECT 1') === '1'; } catch { return false; }
    }, 'disposable PostgreSQL');
    const authPort = await unusedPort(), gatewayPort = await unusedPort();
    const authOrigin = `http://127.0.0.1:${authPort}`, gatewayOrigin = `http://127.0.0.1:${gatewayPort}`;
    const unavailable = await listen((_, response) => { response.writeHead(503); response.end(); });
    listeners.push(unavailable.server);
    const authEnvironment = { POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(authDb.ports[5432]), POSTGRES_USER: 'fixture',
      POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey,
      AUTH_JWT_ADDITIONAL_PUBLIC_KEYS: rotatedPublicKey, AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp,
      AUTH_ADMIN_SERVICE_CLIENT_SECRET: adminSecret, AUTH_PUBLIC_BASE_URL: gatewayOrigin, FRONTEND_PUBLIC_URL: gatewayOrigin,
      LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: String(mail.ports[1025]) };
    for (const service of ['USER', 'MAIL', 'GAME', 'LIBRARY', 'REVIEW', 'SOCIAL', 'MESSAGING', 'LFP', 'COMMUNITY', 'CONTENT', 'EVENT',
      'HARDWARE', 'RECOMMENDATION', 'PERFORMANCE', 'ADMIN', 'NOTIFICATION']) {
      authEnvironment[`${service}_SERVICE_URL`] = unavailable.origin;
    }
    await launch('auth-service', authPort, authEnvironment);
    await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: authOrigin, CORS_ALLOWED_ORIGINS: gatewayOrigin });
    check(await sql(authDb, 'SELECT count(*) FROM flyway_schema_history WHERE success') !== '0'
      && await sql(authDb, 'SELECT count(*) FROM flyway_schema_history WHERE NOT success') === '0',
    'Real Auth Flyway migrations applied cleanly and ddl-auto validation passed');

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
    const postJson = (url, body) => fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(6000) });
    const loginAs = async (identifier, expected) => {
      const jar = new CookieJar();
      await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
      return request(gatewayOrigin, '/api/v1/auth/login', expected, { jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { identifier, password } });
    };
    // Erişim token'ı 60 sn yaşar; her hesap kayıttan hemen sonra kullanılır.
    const register = async () => {
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `smoke_${suffix}@example.test`, username = `sm_${suffix}`;
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
      check(Boolean(token) && result.response.headers.getSetCookie().some(c => c.startsWith('__Host-ltz_access=') && /HttpOnly/i.test(c) && /Secure/i.test(c))
        && result.body.status === 'AUTHENTICATED' && !result.body.accessToken,
      'Registration, SMTP verification and login set a secure HttpOnly cookie; no token in the body');
      await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
      return { jar, token, email, username };
    };

    const account = await register();
    const header = decodeJwtPart(account.token, 0), claims = decodeJwtPart(account.token, 1);
    check(claims.exp - claims.iat === 60 && header.typ === 'at+jwt' && header.alg === 'RS256' && Boolean(header.kid),
      `Access token: TTL ${claims.exp - claims.iat}s, typ ${header.typ}, alg ${header.alg}`);

    const jwks = await request(gatewayOrigin, '/api/v1/auth/.well-known/jwks.json', 200);
    check(Array.isArray(jwks.body.keys) && jwks.body.keys.length === 2
      && new Set(jwks.body.keys.map(k => k.kid)).size === 2 && jwks.body.keys.some(k => k.kid === header.kid)
      && jwks.body.keys.every(k => !('d' in k) && !('p' in k) && !('q' in k) && !('dp' in k) && !('dq' in k) && !('qi' in k)),
    'JWKS publishes the signing key plus the rotated public key, public members only, through the Gateway');

    const weak = randomUUID().replaceAll('-', '').slice(0, 10);
    await request(gatewayOrigin, '/api/v1/auth/register', 400, { method: 'POST',
      body: { email: `weak_${weak}@example.test`, username: `wk_${weak}`, password: 'Abcdefg12345!x' } });
    check(true, 'A 14-character password is rejected at registration (policy minimum 15)');

    const viaGateway = await postJson(gatewayOrigin + '/internal/v1/service-tokens', { clientId: 'admin', clientSecret: adminSecret });
    check(viaGateway.status === 404, `/internal is not routed through the Gateway (status ${viaGateway.status})`);

    const tokenUrl = authOrigin + '/internal/v1/service-tokens';
    const wrong = await postJson(tokenUrl, { clientId: 'admin', clientSecret: 'x'.repeat(40) });
    check(wrong.status === 401, `A wrong client secret is rejected (status ${wrong.status})`);
    const issuedResponse = await postJson(tokenUrl, { clientId: 'admin', clientSecret: adminSecret });
    check(issuedResponse.status === 200 && /no-store/i.test(issuedResponse.headers.get('Cache-Control') || ''),
      'Valid client credentials issue a service token with Cache-Control no-store');
    const issued = await issuedResponse.json();
    const serviceToken = issued.accessToken ?? issued.access_token;
    secrets.push(serviceToken);
    const serviceHeader = decodeJwtPart(serviceToken, 0), service = decodeJwtPart(serviceToken, 1);
    check(serviceHeader.typ === 'svc+jwt' && [].concat(service.aud).includes('auth-service') && service.sub === 'svc:admin'
      && service.exp - service.iat === 60 && !String(service.scope).includes('account:erase'),
    `Service token: typ ${serviceHeader.typ}, aud ${[].concat(service.aud)}, sub ${service.sub}, TTL ${service.exp - service.iat}s, scope ${service.scope}`);
    await request(authOrigin, `/internal/v1/accounts/${claims.sub}`, 200, { headers: { Authorization: `Bearer ${serviceToken}` } });
    const userOnInternal = await fetch(authOrigin + `/internal/v1/accounts/${claims.sub}`, { headers: { Authorization: `Bearer ${account.token}` } });
    check(userOnInternal.status === 401, `The service token reads an account; a user access token cannot (status ${userOnInternal.status})`);

    const newEmail = `chg_${randomUUID().replaceAll('-', '').slice(0, 12)}@example.test`;
    secrets.push(newEmail);
    await request(gatewayOrigin, '/api/v1/auth/email/change', 202, { jar: account.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { newEmail, password } });
    const changeCode = await message(newEmail, /\b(\d{6})\b/);
    await request(gatewayOrigin, '/api/v1/auth/email/change/confirm', 204, { jar: account.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { code: changeCode } });
    await loginAs(account.email, 401);
    await loginAs(newEmail, 200);
    check(true, 'Email change: the code goes to the new address, the old address stops working, the new address signs in');

    const leaving = await register();
    await request(gatewayOrigin, '/api/v1/auth/account/deletion/request', 202, { jar: leaving.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { password } });
    const deletionToken = await message(leaving.email, /#token=([A-Za-z0-9_-]+)/);
    await request(gatewayOrigin, '/api/v1/auth/account/deletion/confirm', 202, { jar: leaving.jar, method: 'POST', csrf: true,
      headers: { Origin: gatewayOrigin }, body: { token: deletionToken, username: leaving.username } });
    await loginAs(leaving.email, 403);
    check(await sql(authDb, "SELECT count(*) FROM accounts WHERE status='DELETION_REQUESTED'") === '1'
      && await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='CONFIRMED'") === '0',
    'Deletion confirmed: the account is closed for sign-in during the grace period and no erasure has run yet');
    await request(gatewayOrigin, '/api/v1/auth/account/deletion/cancel', 200, { method: 'POST',
      body: { identifier: leaving.email, password } });
    await loginAs(leaving.email, 200);
    check(await sql(authDb, "SELECT count(*) FROM accounts WHERE status='DELETION_REQUESTED'") === '0',
      'Deletion cancelled inside the grace period: the account signs in again');

    log(`AUTH SMOKE PASS: ${checks} checks; other-service integration NOT_VERIFIED`);
    return { checks, status: 'PASS' };
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
      const resourceLabel = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.auth-smoke" }}', id]);
      if (resourceLabel !== label) throw new Error('Refusing cleanup of a resource outside this fixture');
      await docker(['rm', '-f', '-v', id]);
    }
    log(`[CLEANUP] ${children.length} fixture processes and ${containers.length} owned containers removed`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runAuthSmoke(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')).catch(error => {
    console.error(error.message); process.exitCode = 1;
  });
}
