// Yalnız mevcut ltz-dev: gerçek Auth/Gateway/Library/Game HTTP kabul testi.
// Parola/token/OTP yalnız bellektedir. ENV dosyaları okunmaz, Auth DB'sine SQL gönderilmez.
import { randomBytes, randomUUID } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { setTimeout as delay } from 'node:timers/promises';
import { CookieJar } from './hardware-auth-e2e.mjs';

const gateway = 'http://127.0.0.1:5080';
const mailpit = 'http://127.0.0.1:8025';
const origin = 'http://localhost:5001';
const runId = randomUUID().replaceAll('-', '');
const gameId = randomUUID();
const invalidGame = randomUUID();
const title = 'LTZ LIB phase1 test ' + runId;
const accounts = [], rows = [], cleanup = [];
let fixtureCreated = false, gameStopped = false, failure;

function command(args, input) {
  const result = spawnSync('docker', args, { input, encoding: 'utf8', windowsHide: true, timeout: 60000 });
  if (result.status !== 0 || result.error) throw new Error('Docker operation failed: ' + args[0]);
  return result.stdout.trim();
}

function sql(database, query) {
  if (!['library_db', 'game_db'].includes(database)) throw new Error('Database scope refused');
  return command(['exec', '-i', 'ltz-dev-postgres-1', 'sh', '-c',
    'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d ' + database], query + '\n');
}

function check(name, expected, actual) {
  const pass = expected === actual;
  rows.push({ name, expected, actual, pass });
  console.log((pass ? 'PASS ' : 'FAIL ') + name + ': expected=' + expected + ', actual=' + actual);
  if (!pass) throw new Error('Assertion failed: ' + name);
}

async function request(name, route, expected, { account, method = 'GET', body, headers = {}, csrf = false, base = gateway } = {}) {
  const outgoing = { ...headers };
  if (account) outgoing.Cookie = account.jar.header(route);
  if (account && csrf) outgoing['X-XSRF-TOKEN'] = account.jar.get('__Host-XSRF-TOKEN') || account.jar.get('XSRF-TOKEN');
  if (body !== undefined) outgoing['Content-Type'] = 'application/json';
  const response = await fetch(base + route, { method, headers: outgoing, redirect: 'manual',
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  account?.jar.accept(response.headers);
  check(name + ' HTTP', expected, response.status);
  const text = await response.text();
  return text && response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : undefined;
}

async function mailbox(account) {
  const response = await fetch(mailpit + '/api/v1/search?query=' + encodeURIComponent('to:' + account.email),
    { signal: AbortSignal.timeout(5000) });
  if (!response.ok) throw new Error('Local mailbox unavailable');
  return (await response.json()).messages || [];
}

async function mailValue(account, pattern) {
  for (let i = 0; i < 40; i++) {
    for (const summary of await mailbox(account)) {
      const response = await fetch(mailpit + '/api/v1/message/' + encodeURIComponent(summary.ID), { signal: AbortSignal.timeout(5000) });
      if (!response.ok) throw new Error('Test message unavailable');
      const detail = await response.json();
      const match = String(detail.Text).match(pattern);
      if (match) return match[1];
    }
    await delay(250);
  }
  throw new Error('Local test email was not delivered');
}

async function login(account) {
  account.jar = new CookieJar();
  await request(account.label + ' CSRF', '/api/v1/auth/csrf', 204, { account });
  await request(account.label + ' login', '/api/v1/auth/login', 200, { account, method: 'POST', csrf: true,
    headers: { Origin: origin }, body: { identifier: account.email, password: account.password } });
  const token = account.jar.get('__Host-ltz_access') || account.jar.get('ltz_access');
  if (!token) throw new Error('Missing test session cookie');
  account.token = token;
  account.id = JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).sub;
  if (!/^[a-f0-9-]{36}$/i.test(account.id)) throw new Error('Invalid test account identity');
  await request(account.label + ' session CSRF', '/api/v1/auth/csrf', 204, { account });
}

async function register(label) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
  const account = { label, email: 'lib_' + suffix + '@example.test', username: 'lib_' + suffix,
    password: randomBytes(36).toString('base64url'), jar: new CookieJar() };
  accounts.push(account);
  await request(label + ' register', '/api/v1/auth/register', 202, { method: 'POST',
    body: { email: account.email, username: account.username, password: account.password } });
  const code = await mailValue(account, /\b(\d{6})\b/);
  await request(label + ' email verification', '/api/v1/auth/email-verification/confirm', 204,
    { method: 'POST', body: { email: account.email, code, password: account.password } });
  await login(account);
  return account;
}

function state(account) {
  return JSON.parse(sql('library_db', `SELECT json_build_object('count',count(*),'favorite',bool_or(favorite),
    'completed',bool_or(completed)) FROM library_entries WHERE user_id='${account.id}' AND game_id='${gameId}';`));
}

async function mutate(account, method, suffix, expected = 200, body) {
  return request(account.label + ' ' + method + ' ' + (suffix || 'collect'), '/api/v1/library/entries' + suffix,
    expected, { account, method, body, csrf: true, headers: { Origin: origin } });
}

async function restoreGame() {
  if (gameStopped) { command(['start', 'ltz-dev-game-service-1']); gameStopped = false; }
  for (let i = 0; i < 90; i++) {
    try { if ((await fetch('http://127.0.0.1:5084/actuator/health', { signal: AbortSignal.timeout(1500) })).status === 200) return; }
    catch { /* yalnız readiness yeniden denemesi */ }
    await delay(500);
  }
  throw new Error('Game health did not recover');
}

async function cleanAccount(account) {
  if (!account.id) { cleanup.push(account.label + ': registration incomplete; manual account cleanup may be needed'); return; }
  await login(account);
  await request(account.label + ' cleanup request', '/api/v1/auth/account/deletion/request', 202,
    { account, method: 'POST', csrf: true, headers: { Origin: origin }, body: { password: account.password } });
  const token = await mailValue(account, /#token=([A-Za-z0-9_-]+)/);
  await request(account.label + ' cleanup confirm', '/api/v1/auth/account/deletion/confirm', 202,
    { account, method: 'POST', csrf: true, headers: { Origin: origin }, body: { token, username: account.username } });
  cleanup.push(account.label + ': Auth deletion requested through API; existing 14-day grace period retained');
  const ids = [...new Set((await mailbox(account)).map(message => message.ID))];
  if (ids.length) {
    const response = await fetch(mailpit + '/api/v1/messages', { method: 'DELETE',
      headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ IDs: ids }), signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new Error('Scoped test mailbox cleanup failed');
  }
  check(account.label + ' test mailbox empty', 0, (await mailbox(account)).length);
}

try {
  for (const name of ['postgres', 'auth-service', 'game-service', 'api-gateway', 'library-service']) {
    const actual = command(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', 'ltz-dev-' + name + '-1']);
    if (actual !== 'ltz-dev') throw new Error('Refusing a non-ltz-dev container');
  }
  fixtureCreated = true;
  sql('game_db', `INSERT INTO games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness)
    VALUES('${gameId}','${title}','${title.toLowerCase()}',clock_timestamp(),clock_timestamp(),'INTERNAL','MAIN_GAME','ACTIVE','INDEX_ONLY');`);
  const catalog = await request('Real Game batch fixture', '/api/v1/game/games/batch', 200,
    { method: 'POST', body: { gameIds: [gameId] } });
  check('Canonical test game available', gameId, catalog.items?.[0]?.gameId);
  const a = await register('A'), b = await register('B');
  const created = await mutate(a, 'POST', '', 200, { gameId });
  check('New favorite false', false, created.favorite);
  check('New completed false', false, created.completed);
  const again = await mutate(a, 'POST', '', 200, { gameId });
  check('Repeated add stable entry', created.id, again.id);
  check('Repeated add one DB row', 1, state(a).count);
  await mutate(a, 'PUT', '/' + gameId + '/favorite');
  check('Favorite persisted', true, state(a).favorite);
  await mutate(a, 'PUT', '/' + gameId + '/completed');
  check('Completed persisted', true, state(a).completed);
  check('Completed preserves favorite', true, state(a).favorite);
  await mutate(b, 'POST', '', 400, { gameId, userId: a.id });
  await request('B cannot select A via path', '/api/v1/library/users/' + a.id + '/entries/' + gameId, 403,
    { account: b, method: 'DELETE', csrf: true });
  await mutate(b, 'DELETE', '/' + gameId, 204);
  check('B delete preserves A entry', 1, state(a).count);
  await mutate(b, 'PUT', '/' + gameId + '/favorite');
  check('B auto-collects own row', 1, state(b).count);
  check('B auto favorite only', false, state(b).completed);
  check('A flags unaffected by B', true, state(a).completed);
  await mutate(b, 'DELETE', '/' + gameId, 204);
  await mutate(a, 'DELETE', '/' + gameId, 204);
  check('Delete removes collection and flags', 0, state(a).count);
  await mutate(a, 'DELETE', '/' + gameId, 204);
  await mutate(a, 'PUT', '/' + gameId + '/completed');
  check('Auto complete only', false, state(a).favorite);
  await mutate(a, 'DELETE', '/' + gameId, 204);
  await mutate(a, 'POST', '', 404, { gameId: invalidGame });
  await mutate(a, 'PUT', '/' + invalidGame + '/favorite', 404);
  await request('Invalid JWT', '/api/v1/library/entries', 401,
    { method: 'POST', body: { gameId }, headers: { Authorization: 'Bearer invalid-test-token' } });
  await request('Cookie without CSRF', '/api/v1/library/entries', 403, { account: a, method: 'POST', body: { gameId } });
  await request('Valid JWT Bearer through Gateway', '/api/v1/library/entries', 200,
    { method: 'POST', body: { gameId }, headers: { Authorization: 'Bearer ' + a.token } });
  await mutate(a, 'DELETE', '/' + gameId, 204);
  gameStopped = true;
  command(['stop', '--time', '10', 'ltz-dev-game-service-1']);
  try {
    await mutate(a, 'POST', '', 503, { gameId });
    await mutate(a, 'PUT', '/' + gameId + '/favorite', 503);
    check('Outage writes no row', 0, state(a).count);
  } finally { await restoreGame(); }
  await mutate(a, 'POST', '', 200, { gameId });
  await mutate(a, 'DELETE', '/' + gameId, 204);
} catch (error) {
  failure = error.message.startsWith('Assertion failed:') ? error.message : 'E2E infrastructure or HTTP operation failed (credentials redacted)';
  console.log('FAIL ' + failure);
} finally {
  try { await restoreGame(); cleanup.push('Game restored: health 200'); }
  catch { cleanup.push('ERROR: Game restoration failed'); failure ||= 'Cleanup failed'; }
  for (const account of accounts) {
    try { await cleanAccount(account); }
    catch { cleanup.push(account.label + ': ERROR cleanup incomplete; credentials not logged'); failure ||= 'Account cleanup incomplete'; }
  }
  try {
    const ids = accounts.filter(account => account.id).map(account => "'" + account.id + "'");
    if (ids.length) sql('library_db', `DELETE FROM library_entries WHERE user_id IN (${ids.join(',')}) AND game_id IN ('${gameId}','${invalidGame}');`);
    if (fixtureCreated) sql('game_db', `DELETE FROM games WHERE id='${gameId}' AND title='${title}';`);
    check('Only test game removed', '0', sql('game_db', `SELECT count(*) FROM games WHERE id='${gameId}';`));
    cleanup.push('Scoped Library test rows and Game fixture removed; existing databases/volumes retained');
  } catch { cleanup.push('ERROR: Scoped fixture cleanup failed'); failure ||= 'Fixture cleanup failed'; }
  const phases = [
    ['Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü', ['LIB-01','LIB-02','LIB-12','LIB-13','LIB-15']],
    ['Faz 2 — Favori ve Bitirildi İşlemleri', ['LIB-03','LIB-04']],
    ['Faz 3 — Kütüphane Listeleme, Filtreleme ve İstatistik', ['LIB-05','LIB-06','LIB-07','LIB-08','LIB-09','LIB-16']],
    ['Faz 4 — Gizlilik ve Servis Entegrasyonları', ['LIB-10','LIB-11','LIB-14','LIB-18']],
    ['Faz 5 — Arşivlenen Oyunlarda Dayanıklılık', ['LIB-17']]
  ];
  const output = ['# Library Faz 1 — Gerçek ltz-dev E2E kanıtı', '', 'UTC: ' + new Date().toISOString(), '',
    'Sonuç: ' + (failure ? 'FAIL / INCOMPLETE' : 'PASS'), 'Kontroller: ' + rows.filter(row => row.pass).length + '/' + rows.length,
    '', 'Pre-push ve SonarQube çalıştırılmadı; kullanıcı tarafından manuel yapılacaktır.', '', '## Beklenen ve gerçekleşen sonuçlar', '',
    ...rows.map(row => '- [' + (row.pass ? 'x' : ' ') + '] ' + row.name + ': beklenen=' + row.expected + ', gerçekleşen=' + row.actual),
    '', '## Temizlik', '', ...cleanup.map(line => '- ' + line), '',
    'Parola, OTP, JWT, cookie ve silme token değerleri bu rapora veya loglara yazılmadı.',
    'E2E geçerli Auth oturumlarını kullandı; Auth DB üzerinde SQL veya süre/politika değişikliği yapılmadı.',
    'Faz 1 otomatik kabul testleri geçerlidir; E2E eksikse ayrıntı ana sonuç raporunda belirtilir.',
    'Faz 1: ' + (failure ? '5/5 geliştirme; E2E son kontrol eksik' : '5/5') + ' · Genel: 5/18', ''];
  for (const [index, [name, tasks]] of phases.entries()) output.push('## ' + name, '', ...tasks.map(task => '- [' + (index === 0 ? 'x' : ' ') + '] ' + task), '');
  writeFileSync('docs-library/LTZ-HAM-LIB-01-E2E-Sonuc.md', output.join('\n'));
}
process.exitCode = failure ? 1 : 0;
