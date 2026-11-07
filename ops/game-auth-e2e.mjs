#!/usr/bin/env node
// Gerçek Auth/Gateway/Game HTTP sözleşmesi; yalnız etiketli geçici DB, Mailpit ve sentetik hesaplar.
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { CookieJar, isolatedEnvironment, captureCommand, requireFreshJar, scrub } from './hardware-auth-e2e.mjs';

async function until(action, name, timeout = 90000) {
  const deadline = Date.now() + timeout;
  do { const value = await action(); if (value) return value; await delay(250); } while (Date.now() < deadline);
  throw new Error(`Timeout: ${name}`);
}
async function freePort() {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

export async function runGameAuthE2e(root, log = console.log) {
  for (const service of ['auth-service', 'game-service', 'api-gateway']) requireFreshJar(root, service);
  const label = randomUUID(), containers = [], children = [], secrets = [];
  let checks = 0;
  const check = (value, name) => { if (!value) throw new Error(`FAIL: ${name}`); checks++; log(`[PASS] ${name}`); };
  const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, { cwd: root, env: isolatedEnvironment(extra), windowsHide: true }), binary, secrets);
  const container = async (image, ports, env = {}) => {
    const args = ['run', '-d', '--label', `ltz.game-auth-e2e=${label}`];
    for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
    for (const name of Object.keys(env)) args.push('-e', name);
    const id = await command('docker', [...args, image], env);
    if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container');
    containers.push(id);
    const mapped = {};
    for (const port of ports) {
      const address = await command('docker', ['port', id, `${port}/tcp`]);
      if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Fixture must be loopback only');
      mapped[port] = address.split(':')[1];
    }
    return { id, ports: mapped };
  };
  const sql = async (db, query) => {
    const actual = await command('docker', ['inspect', '--format', '{{ index .Config.Labels "ltz.game-auth-e2e" }}', db.id]);
    if (actual !== label) throw new Error('Refusing SQL outside owned disposable fixture');
    return command('docker', ['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-v', 'ON_ERROR_STOP=1', '-tAc', query]);
  };
  const launch = async (service, port, extra) => {
    const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const child = spawn(java, ['-Xmx384m', '-jar', requireFreshJar(root, service), '--server.address=127.0.0.1', `--server.port=${port}`, '--ltz.game.rating.sync.enabled=false'], { cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'WARN', ...extra }), windowsHide: true });
    children.push(child);
    let output = '', failed = false;
    child.once('error', () => { failed = true; });
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { output = (output + scrub(data.toString(), secrets)).slice(-6000); });
    await until(async () => {
      if (failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${output}`);
      try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; }
    }, service);
  };
  try {
    const password = randomBytes(32).toString('base64url'), dbPassword = randomBytes(32).toString('base64url');
    const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
    const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
    secrets.push(password, dbPassword, privateKey, hmac, totp);
    const dbEnv = { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword };
    const authDb = await container('postgres:17-alpine', [5432], dbEnv), gameDb = await container('postgres:17-alpine', [5432], dbEnv);
    const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
    for (const db of [authDb, gameDb]) await until(async () => { try { return await sql(db, 'SELECT 1') === '1'; } catch { return false; } }, 'PostgreSQL');
    const authPort = await freePort(), gamePort = await freePort(), gatewayPort = await freePort();
    const authOrigin = `http://127.0.0.1:${authPort}`, gameOrigin = `http://127.0.0.1:${gamePort}`, gatewayOrigin = `http://127.0.0.1:${gatewayPort}`;
    const properties = db => ({ POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: db.ports[5432], POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', DB_GAME_NAME: 'fixture' });
    await launch('auth-service', authPort, { ...properties(authDb), SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey, AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gatewayOrigin, FRONTEND_PUBLIC_URL: gatewayOrigin, LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: mail.ports[1025] });
    await launch('game-service', gamePort, { ...properties(gameDb), AUTH_SERVICE_URL: authOrigin, GAME_CATALOG_SYNC_ENABLED: 'false' });
    await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: authOrigin, GAME_SERVICE_URL: gameOrigin, CORS_ALLOWED_ORIGINS: gatewayOrigin });
    const request = async (route, expected, { jar, method = 'GET', body, csrf = false } = {}) => {
      const headers = { Origin: gatewayOrigin };
      if (jar) headers.Cookie = jar.header(route);
      if (csrf && jar) headers['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN');
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      const response = await fetch(gatewayOrigin + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(7000) });
      jar?.accept(response.headers);
      if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
      const text = await response.text();
      return { response, body: text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : undefined };
    };
    const register = async () => {
      const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `game_${suffix}@example.test`, username = `game_${suffix}`;
      secrets.push(email);
      await request('/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
      const code = await until(async () => {
        const messages = await (await fetch(`http://127.0.0.1:${mail.ports[8025]}/api/v1/search?query=${encodeURIComponent('to:' + email)}`)).json();
        for (const summary of messages.messages || []) {
          const detail = await (await fetch(`http://127.0.0.1:${mail.ports[8025]}/api/v1/message/${summary.ID}`)).json();
          const match = String(detail.Text).match(/\b(\d{6})\b/);
          if (match) { secrets.push(match[1]); return match[1]; }
        }
      }, 'Mailpit verification', 20000);
      await request('/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
      const jar = new CookieJar();
      await request('/api/v1/auth/csrf', 204, { jar });
      const login = await request('/api/v1/auth/login', 200, { jar, method: 'POST', csrf: true, body: { identifier: email, password } });
      secrets.push(jar.get('__Host-ltz_access'));
      check(login.body.status === 'AUTHENTICATED' && !login.body.accessToken && login.response.headers.getSetCookie().some(value => value.startsWith('__Host-ltz_access=') && /HttpOnly/i.test(value) && /Secure/i.test(value)), 'Gerçek kayıt, SMTP doğrulaması ve güvenli HttpOnly login çerezi');
      await request('/api/v1/auth/csrf', 204, { jar });
      const me = (await request('/api/v1/auth/me', 200, { jar })).body;
      check(typeof me.accountId === 'string' && me.roles.includes('PLAYER'), 'Game ekranının tükettiği gerçek /auth/me sözleşmesi');
      return jar;
    };
    const owner = await register(), other = await register(), gameId = randomUUID();
    await sql(gameDb, `INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES ('${gameId}','Game Auth fixture','game auth fixture','INTERNAL','MAIN_GAME','PARTIAL',now(),now())`);
    await request('/api/v1/game/feedback', 401);
    const body = { gameId, type: 'GENERAL', title: 'Fixture feedback', body: 'Only fixture owner should see this feedback.' };
    await request('/api/v1/game/feedback', 403, { jar: owner, method: 'POST', body });
    check(true, 'Anonim erişim 401; çerezli yazmada eksik CSRF 403');
    const created = (await request('/api/v1/game/feedback', 201, { jar: owner, method: 'POST', csrf: true, body })).body;
    check(Boolean(created.id), 'Gerçek Auth çerezi ve CSRF ile Gateway üzerinden Game yazması');
    const mine = (await request(`/api/v1/game/feedback?gameId=${gameId}`, 200, { jar: owner })).body;
    const theirs = (await request(`/api/v1/game/feedback?gameId=${gameId}`, 200, { jar: other })).body;
    check(mine.items.some(item => item.id === created.id) && theirs.totalElements === 0 && !theirs.items.length, 'İki gerçek oturum arasında kişisel geri bildirim sahipliği');
    await request('/api/v1/game/admin/games', 403, { jar: owner, method: 'POST', csrf: true, body: { title: 'Forbidden fixture', gameType: 'MAIN_GAME' } });
    await request('/api/v1/game/developer/games', 403, { jar: owner });
    check(true, 'Gerçek PLAYER oturumu admin ve geliştirici kaynaklarına erişemez');
    log(`[RESULT] ${checks} gerçek Auth/Gateway/Game kontrolü PASS; HTTP cookie jar kullanılır, tarayıcı HTTPS dağıtım kabulü değildir.`);
    return checks;
  } finally {
    const failures = [];
    for (const child of children.reverse()) {
      if (!child.pid || child.exitCode !== null || child.signalCode !== null) continue;
      try {
        const closed = new Promise(resolve => child.once('exit', resolve));
        if (process.platform === 'win32') await command('taskkill', ['/PID', String(child.pid), '/T', '/F']); else child.kill();
        await Promise.race([closed, delay(5000)]);
        if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await closed; }
      } catch (error) { failures.push(error); }
    }
    for (const id of containers.reverse()) {
      try {
        const actual = await command('docker', ['inspect', '--format', '{{ index .Config.Labels "ltz.game-auth-e2e" }}', id]);
        if (actual !== label) throw new Error('Refusing foreign container cleanup');
        await command('docker', ['rm', '-f', '-v', id]);
      } catch (error) { failures.push(error); }
    }
    if (failures.length) throw new AggregateError(failures, 'Game Auth fixture cleanup failed');
    log('[CLEANUP] Bu koşunun süreç ağaçları, PostgreSQL ve Mailpit konteynerleri temizlendi.');
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await runGameAuthE2e(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
