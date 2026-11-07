#!/usr/bin/env node
// GAME-11: gerçek Game/Community/Gateway; yalnız geçici DB ve fixture JWKS/kullanıcı kimlikleri.
import path from 'node:path';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { generateKeyPairSync, randomBytes, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { isolatedEnvironment, captureCommand, requireFreshJar, scrub } from './hardware-auth-e2e.mjs';
import { fixtureToken } from './game-review-e2e.mjs';

async function until(action, label) {
  const deadline = Date.now() + 90000;
  while (Date.now() < deadline) {
    if (await action()) return;
    await delay(250);
  }
  throw new Error(`Timeout: ${label}`);
}
async function freePort() {
  const server = createServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}

export async function runGameCommunityE2e(root, log = console.log) {
  for (const service of ['game-service', 'community-service', 'api-gateway']) requireFreshJar(root, service);
  const label = randomUUID(), owned = [], children = [], secrets = [];
  let checks = 0;
  const check = (condition, message) => {
    if (!condition) throw new Error(`FAIL: ${message}`);
    checks++; log(`[PASS] ${message}`);
  };
  const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, {
    cwd: root, env: isolatedEnvironment(extra), windowsHide: true,
  }), binary, secrets);
  const sql = (database, text) => command('docker', ['exec', database.id, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'fixture', '-d', 'fixture', '-tAc', text]);
  const database = async () => {
    const password = randomBytes(32).toString('base64url'); secrets.push(password);
    const id = await command('docker', ['run', '-d', '--label', `ltz.game-community-e2e=${label}`, '-p', '127.0.0.1::5432',
      '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=fixture', '-e', 'POSTGRES_DB=fixture', 'postgres:17-alpine'], { POSTGRES_PASSWORD: password });
    if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
    owned.push(id);
    const address = await command('docker', ['port', id, '5432/tcp']);
    if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Database must be loopback only');
    const db = { id, env: { POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: address.split(':')[1], POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: password, DB_GAME_NAME: 'fixture', DB_COMMUNITY_NAME: 'fixture' } };
    await until(async () => { try { return await sql(db, 'select 1') === '1'; } catch { return false; } }, 'PostgreSQL');
    return db;
  };
  const stop = async child => {
    if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
    const closed = new Promise(resolve => child.once('exit', resolve));
    if (process.platform === 'win32') await command('taskkill', ['/PID', String(child.pid), '/T', '/F']);
    else child.kill();
    await Promise.race([closed, delay(5000)]);
    if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await closed; }
  };
  const launch = async (service, extra) => {
    const port = await freePort();
    const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const child = spawn(java, ['-Xmx384m', '-jar', requireFreshJar(root, service), '--server.address=127.0.0.1', `--server.port=${port}`, '--ltz.game.rating.sync.enabled=false', '--spring.datasource.hikari.connection-timeout=1000'], {
      cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'WARN', ...extra }), windowsHide: true,
    });
    children.push(child);
    let output = '', failed = false;
    child.once('error', () => { failed = true; });
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { output = (output + scrub(data.toString(), secrets)).slice(-6000); });
    const origin = `http://127.0.0.1:${port}`;
    await until(async () => {
      if (failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${output}`);
      try { return (await fetch(origin + '/actuator/health', { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; }
    }, service);
    return { origin, child, diagnostics: () => output };
  };
  const keys = generateKeyPairSync('rsa', { modulusLength: 3072 }), kid = randomUUID();
  const jwks = { keys: [{ ...keys.publicKey.export({ format: 'jwk' }), kid, use: 'sig', alg: 'RS256' }] };
  const identity = createServer((request, response) => {
    if (request.url !== '/api/v1/auth/.well-known/jwks.json') { response.writeHead(404).end(); return; }
    response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(jwks));
  });
  await new Promise(resolve => identity.listen(0, '127.0.0.1', resolve));
  const auth = { AUTH_SERVICE_URL: `http://127.0.0.1:${identity.address().port}` };
  const author = randomUUID(), viewer = randomUUID(), admin = randomUUID();
  const token = (subject, roles = ['PLAYER']) => fixtureToken(keys.privateKey, kid, subject, { roles });
  try {
    const gameDb = await database(), communityDb = await database();
    const game = await launch('game-service', { ...gameDb.env, ...auth });
    const community = await launch('community-service', { ...communityDb.env, ...auth, GAME_SERVICE_URL: game.origin });
    const gateway = await launch('api-gateway', { ...auth, GAME_SERVICE_URL: game.origin, COMMUNITY_SERVICE_URL: community.origin });
    const request = async (route, expected = 200, { body, method = 'GET', user, roles, cookie, bearer } = {}) => {
      const headers = { 'Content-Type': 'application/json' };
      if (user) {
        const jwt = token(user, roles);
        if (cookie) headers.Cookie = `ltz_access=${jwt}`; else headers.Authorization = `Bearer ${jwt}`;
      }
      if (bearer) headers.Authorization = `Bearer ${bearer}`;
      const response = await fetch(gateway.origin + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(7000) });
      if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
      const text = await response.text();
      return { data: text ? JSON.parse(text) : null, headers: response.headers };
    };
    const { data: created } = await request('/api/v1/game/admin/games', 201, { method: 'POST', user: admin, roles: ['ADMIN'], body: { title: 'GAME-11 Fixture', gameType: 'MAIN_GAME' } });
    const gameId = created.gameId;
    check(Boolean(gameId), 'Gateway → Game creates a canonical game with validated fixture identity');
    const base = `/api/v1/community/games/${gameId}/content`;
    const content = [];
    for (const type of ['GUIDES', 'EASTER_EGGS', 'EXPERIENCES']) {
      const { data: descriptor } = await request(`/api/v1/game/games/${gameId}/modules/${type}?contractVersion=2`);
      check(descriptor.state === 'EXTERNAL' && descriptor.contentSource.service === 'COMMUNITY', `${type}: Game provides the versioned Community source`);
      const { data: empty } = await request(descriptor.contentSource.listPath);
      check(empty.totalElements === 0 && empty.items.length === 0, `${type}: empty Community result is a successful collection`);
      const { data: post } = await request('/api/v1/community/posts', 201, { method: 'POST', user: author, body: { body: 'Fixture plain text <b>not HTML</b>', gameId, contentType: type, title: `Fixture ${type}` } });
      content.push(post.id);
      const { data: page, headers } = await request(descriptor.contentSource.listPath, 200, { user: viewer, cookie: true });
      check(page.totalElements === 1 && page.items[0].id === post.id && headers.get('cache-control')?.includes('no-store'), `${type}: Gateway cookie identity, typed content and no-store response`);
    }
    const detail = `${base}/${content[0]}`;
    check((await request(detail)).data.body.includes('<b>not HTML</b>'), 'Community detail preserves the plain text contract');
    await request(`/api/v1/community/games/${randomUUID()}/content/${content[0]}`, 404);
    await request(base + '?type=GUIDES', 401, { bearer: 'invalid-token' });
    check(true, 'Wrong-game detail is hidden; invalid user token is rejected');
    // Social/Admin canlı akışı değildir: yalnız geçici Community DB'sine yerel yansıma fixture'ı hazırlanır.
    await sql(communityDb, `insert into user_blocks(blocker_id,blocked_id,created_at) values ('${viewer}','${author}',now())`);
    const blocked = await request(base + '?type=GUIDES', 200, { user: viewer, cookie: true });
    await request(detail, 404, { user: viewer, cookie: true });
    check(blocked.data.totalElements === 0 && blocked.data.items.length === 0, 'Cookie viewer blocking filters list, count and detail together');
    check((await request(base + '?type=GUIDES')).data.totalElements === 1, 'Viewer-specific blocking is not shared with anonymous readers');
    await sql(communityDb, `update posts set status='HIDDEN' where id='${content[0]}'`);
    await request(detail, 404, { user: author, cookie: true });
    check((await request(base + '?type=GUIDES')).data.totalElements === 0, 'Hidden content leaves the list/count and is hidden from its author');
    await command('docker', ['stop', '--timeout', '3', communityDb.id]);
    const direct = await fetch(community.origin + base + '?type=GUIDES', { signal: AbortSignal.timeout(7000) });
    log(`[OUTAGE] Direct Community HTTP ${direct.status}; expected 503.`);
    if (direct.status !== 503) {
      const diagnostic = community.diagnostics();
      const cause = diagnostic.includes('Unable to rollback against JDBC Connection') ? 'Hibernate rollback failure after connection loss' : 'Inspect Community exception mapping';
      log('[CONTRACT GAP] ' + cause);
    }
    check(direct.status === 503, 'The first direct Community outage response must match the documented 503 contract');
    await request(base + '?type=GUIDES', 503);
    check(true, 'Community database outage is 503 through Gateway, never an empty list');
    log(`[RESULT] ${checks} GAME-11 checks passed; real Auth login, Social/Admin event delivery and production deployment are not covered.`);
    return checks;
  } finally {
    const failures = [];
    for (const child of children.reverse()) { try { await stop(child); } catch (error) { failures.push(error); } }
    await new Promise(resolve => identity.close(resolve));
    for (const id of owned.reverse()) {
      try {
        const actual = await command('docker', ['inspect', '--format', '{{index .Config.Labels "ltz.game-community-e2e"}}', id]);
        if (actual !== label) throw new Error('Refusing to delete an unowned container');
        await command('docker', ['rm', '-f', '-v', id]);
      } catch (error) { failures.push(error); }
    }
    if (failures.length) throw new AggregateError(failures, 'GAME-11 fixture cleanup failed');
    log('[CLEANUP] Fixture JVM trees and owned PostgreSQL containers removed.');
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runGameCommunityE2e(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')).catch(error => { console.error(error.message); process.exitCode = 1; });
}
