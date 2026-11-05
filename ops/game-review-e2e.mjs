#!/usr/bin/env node
// Gerçek Auth servis token'ı → gerçek Review → Game projeksiyonu; yalnız geçici kaynaklar.
import path from 'node:path';
import { spawn } from 'node:child_process';
import { generateKeyPairSync, randomBytes, randomUUID, sign } from 'node:crypto';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { isolatedEnvironment, scrub, captureCommand, requireFreshJar } from './hardware-auth-e2e.mjs';

export function fixtureToken(privateKey, kid, subject, extra = {}, type = 'at+jwt') {
  const now = Math.floor(Date.now() / 1000);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const unsigned = encode({ alg: 'RS256', typ: type, kid }) + '.' + encode({
    iss: 'ltz-auth', aud: ['ltz-api'], sub: subject, iat: now, nbf: now, exp: now + 60, jti: randomUUID(), ...extra,
  });
  return unsigned + '.' + sign('RSA-SHA256', Buffer.from(unsigned), privateKey).toString('base64url');
}

async function waitFor(action, label, timeout = 90000) {
  const deadline = Date.now() + timeout;
  do {
    const result = await action();
    if (result) return result;
    await delay(250);
  } while (Date.now() < deadline);
  throw new Error(`Timeout: ${label}`);
}

async function freePort() {
  const listener = createServer();
  await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
  const port = listener.address().port;
  await new Promise(resolve => listener.close(resolve));
  return port;
}

export async function runGameReviewE2e(root, log = console.log) {
  for (const service of ['auth-service', 'game-service', 'review-service']) requireFreshJar(root, service);
  const owned = [], processes = [], secrets = [];
  const label = randomUUID();
  let checks = 0;
  const check = (condition, name) => {
    if (!condition) throw new Error(`FAIL: ${name}`);
    checks++; log(`[PASS] ${name}`);
  };
  const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, {
    cwd: root, env: isolatedEnvironment(extra), windowsHide: true,
  }), binary, secrets);
  const docker = (args, extra) => command('docker', args, extra);
  const database = async () => {
    const password = randomBytes(32).toString('base64url');
    secrets.push(password);
    const id = await docker(['run', '-d', '--label', `ltz.game-review-e2e=${label}`, '-p', '127.0.0.1::5432',
      '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=fixture', '-e', 'POSTGRES_DB=fixture', 'postgres:17-alpine'],
    { POSTGRES_PASSWORD: password });
    if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
    owned.push(id);
    const address = await docker(['port', id, '5432/tcp']);
    if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Disposable database must be loopback only');
    await waitFor(async () => {
      try { return await docker(['exec', id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', 'SELECT 1']) === '1'; }
      catch { return false; }
    }, 'disposable PostgreSQL readiness');
    return { POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: address.split(':')[1], POSTGRES_USER: 'fixture',
      POSTGRES_PASSWORD: password, DB_AUTH_NAME: 'fixture', DB_GAME_NAME: 'fixture', DB_REVIEW_NAME: 'fixture' };
  };
  const launch = async (service, port, env, args = []) => {
    const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    const child = spawn(java, ['-Xmx384m', '-jar', requireFreshJar(root, service), '--server.address=127.0.0.1',
      `--server.port=${port}`, '--management.health.mail.enabled=false', ...args], {
      cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'WARN', ...env }), windowsHide: true,
    });
    const running = { child, output: '', failed: false };
    processes.push(running);
    child.once('error', () => { running.failed = true; });
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
      running.output = (running.output + scrub(data.toString(), secrets)).slice(-6000);
    });
    const origin = `http://127.0.0.1:${port}`;
    await waitFor(async () => {
      if (running.failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${running.output}`);
      try { return (await fetch(origin + '/actuator/health', { signal: AbortSignal.timeout(2000) })).status === 200; }
      catch { return false; }
    }, `${service} readiness`);
    return { origin, child };
  };
  const stop = async child => {
    if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
    const closed = new Promise(resolve => child.once('exit', resolve));
    if (process.platform === 'win32') {
      // Windows JDK başlatıcısı ikinci bir JVM açabilir; yalnız parent'ı öldürmek uygulamayı açık bırakır.
      await command('taskkill', ['/PID', String(child.pid), '/T', '/F']);
      await closed;
      return;
    }
    child.kill();
    await Promise.race([closed, delay(5000)]);
    if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await closed; }
  };
  const request = async (origin, route, expected, { method = 'GET', body, token } = {}) => {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(origin + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'manual', signal: AbortSignal.timeout(6000) });
    if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
    const text = await response.text();
    return text ? JSON.parse(text) : undefined;
  };
  try {
    const authDb = await database(), gameDb = await database(), reviewDb = await database();
    const keys = generateKeyPairSync('rsa', { modulusLength: 3072 });
    const encodedKey = keys.privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
    const clientSecret = randomBytes(32).toString('base64url');
    const otpKey = randomBytes(32).toString('base64'), totpKey = randomBytes(32).toString('base64');
    secrets.push(encodedKey, clientSecret, otpKey, totpKey);
    const authPort = await freePort(), gamePort = await freePort(), reviewPort = await freePort();
    const gameOrigin = `http://127.0.0.1:${gamePort}`, reviewOrigin = `http://127.0.0.1:${reviewPort}`;
    const auth = await launch('auth-service', authPort, { ...authDb, SPRING_PROFILES_ACTIVE: 'dev',
      AUTH_JWT_PRIVATE_KEY: encodedKey, AUTH_OTP_HMAC_KEY: otpKey,
      AUTH_TOTP_ENCRYPTION_KEY: totpKey, AUTH_GAME_SERVICE_CLIENT_SECRET: clientSecret,
      GAME_SERVICE_URL: gameOrigin, REVIEW_SERVICE_URL: reviewOrigin });
    const review = await launch('review-service', reviewPort, { ...reviewDb, AUTH_SERVICE_URL: auth.origin, GAME_SERVICE_URL: gameOrigin });
    const game = await launch('game-service', gamePort, { ...gameDb, AUTH_SERVICE_URL: auth.origin,
      REVIEW_SERVICE_URL: reviewOrigin, AUTH_GAME_SERVICE_CLIENT_SECRET: clientSecret },
    ['--ltz.game.rating.sync.interval=PT1S', '--ltz.game.rating.sync.initial-delay=PT1S', '--ltz.game.rating.page-size=2']);

    const jwks = await request(auth.origin, '/api/v1/auth/.well-known/jwks.json', 200);
    const kid = jwks.keys.find(key => key.kty === 'RSA').kid;
    // Kullanıcı/admin kimlikleri yalnız bu fixture'ın geçici anahtarıyla imzalanır; gerçek login kanıtı değildir.
    // Servis token'ı ise aşağıda ve Game'in kendi istemcisinde GERÇEK Auth HTTP üreticisinden alınır.
    const user1 = randomUUID(), user2 = randomUUID(), admin = randomUUID();
    const userToken = user => fixtureToken(keys.privateKey, kid, user, { roles: ['PLAYER'] });
    const adminToken = () => fixtureToken(keys.privateKey, kid, admin, { roles: ['ADMIN'] });
    const service = await request(auth.origin, '/internal/v1/service-tokens', 200, { method: 'POST',
      body: { clientId: 'game-service', clientSecret, audience: 'review-service' } });
    secrets.push(service.accessToken);
    check(service.scope.split(' ').includes('rating-summaries:read') && service.scope.split(' ').includes('game-references:read'),
      'Real Auth issues the registered two-scope Game → Review token');
    await request(review.origin, '/internal/v1/games/rating-summaries?limit=2', 200, { token: service.accessToken });
    await request(review.origin, '/internal/v1/games/rating-summaries', 401, { token: userToken(user1) });
    check(true, 'Real Review accepts the Auth service token and rejects a user token');

    const ids = [];
    for (const title of ['Fixture Alpha', 'Fixture Bravo', 'Fixture Charlie', 'Fixture Delta']) {
      const created = await request(game.origin, '/api/v1/game/admin/games', 201, { method: 'POST', token: adminToken(),
        body: { title, gameType: 'MAIN_GAME' } });
      ids.push(created.gameId);
    }
    const rating = (user, id, value) => request(review.origin, `/api/v1/review/games/${id}/rating`, 200,
      { method: 'PUT', token: userToken(user), body: { rating: value } });
    await rating(user1, ids[0], 9); await rating(user2, ids[0], 7);
    await rating(user1, ids[1], 3); await rating(user1, ids[2], 7);
    const list = query => request(game.origin, '/api/v1/game/games?size=10' + (query ? '&' + query : ''), 200);
    const item = (page, id) => page.items.find(value => value.game.gameId === id);
    await waitFor(async () => {
      const page = await list();
      return item(page, ids[0])?.rating.averageRating === 8 && item(page, ids[2])?.rating.averageRating === 7;
    }, 'rated multi-page projection', 30000);
    const current = await list('sort=RATING&direction=DESC');
    check(current.ratingsSyncedAt && current.items.map(value => value.game.gameId).join() === [ids[0], ids[2], ids[1], ids[3]].join(),
      'Real Review ratings reach Game with stable descending order and unrated last');
    check(item(current, ids[0]).rating.ratingCount === 2 && item(current, ids[3]).rating.status === 'UNRATED'
      && item(current, ids[3]).rating.averageRating === null, 'Average/count and verified unrated semantics');
    const filtered = await list('minRating=7&sort=RATING&direction=DESC');
    check(filtered.totalElements === 2 && filtered.items.length === 2, 'minRating filters the entire local catalog');
    const seen = [];
    for (let page = 0; page < 4; page++) {
      const result = await request(game.origin, `/api/v1/game/games?size=1&page=${page}&sort=RATING&direction=ASC`, 200);
      seen.push(result.items[0].game.gameId);
    }
    check(seen.join() === [ids[1], ids[2], ids[0], ids[3]].join(), 'ASC pagination has no duplication and keeps unrated last');

    await rating(user1, ids[0], 5);
    await waitFor(async () => item(await list(), ids[0]).rating.averageRating === 6, 'updated average', 30000);
    check(item(await list(), ids[0]).rating.ratingCount === 2, 'Updating a rating changes the average without increasing count');
    const erase = user => request(review.origin, `/internal/v1/accounts/${user}`, 204, { method: 'DELETE',
      token: fixtureToken(keys.privateKey, kid, 'svc:auth-service', { aud: ['review-service'], scope: 'account:erase', account_id: user }, 'svc+jwt') });
    await erase(user1);
    await waitFor(async () => {
      const page = await list();
      return item(page, ids[0]).rating.averageRating === 7 && item(page, ids[1]).rating.status === 'UNRATED'
        && item(page, ids[2]).rating.status === 'UNRATED';
    }, 'account erasure reflected by complete scan', 30000);
    check(item(await list(), ids[0]).rating.ratingCount === 1, 'Review erasure updates counts and clears games missing from the next complete scan');
    await rating(user1, ids[0], 8).then(() => { throw new Error('Erased account unexpectedly wrote a rating'); }, error => {
      if (!error.message.endsWith('got 403')) throw error;
    });
    check(true, 'Review prevents erased account data from being recreated');

    await stop(review.child);
    await delay(4500); // Bir tam başarısız tarama ve sınırlı retry için süre.
    const stale = await list('minRating=7&sort=RATING&direction=DESC');
    check(stale.totalElements === 1 && item(stale, ids[0]).rating.averageRating === 7,
      'Review outage preserves the last usable projection and local filtering');
    const batch = await request(game.origin, '/api/v1/game/games/batch', 200, { method: 'POST', body: { gameIds: ids } });
    check(batch.items.length === 4, 'Batch cards remain available during Review outage');
    log(`GAME REVIEW RUNTIME E2E PASS: ${checks} checks; user login and Auth deletion orchestration are outside this fixture`);
    return { status: 'PASS', checks };
  } catch (error) {
    throw new Error(scrub(error.message, secrets));
  } finally {
    const cleanupFailures = [];
    for (const { child } of processes.reverse()) {
      try { await stop(child); } catch { cleanupFailures.push('fixture Java process'); }
    }
    for (const id of owned.reverse()) {
      try {
        const actual = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.game-review-e2e" }}', id]);
        if (actual !== label) throw new Error('Resource ownership mismatch');
        await docker(['rm', '-f', '-v', id]);
      } catch { cleanupFailures.push('owned fixture container'); }
    }
    if (cleanupFailures.length) throw new Error(`Cleanup failed: ${cleanupFailures.join(', ')}`);
    log(`[CLEANUP] ${processes.length} fixture processes and ${owned.length} owned containers removed`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runGameReviewE2e(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')).catch(error => {
    console.error(error.message); process.exitCode = 1;
  });
}
