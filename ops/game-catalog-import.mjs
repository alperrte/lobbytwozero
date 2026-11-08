import fs from 'node:fs/promises';
import { createReadStream, createWriteStream } from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const manifestPath = path.join(root, 'ops/game-catalog.manifest.json');
const migrationDirectory = path.join(root, 'backend/services/game-service/src/main/resources/db/migration');
export const POSTGRES_CONTAINER = 'ltz-dev-postgres-1';
export const GAME_CONTAINER = 'ltz-dev-game-service-1';
export const DATABASE = 'game_db';
export const DUMP_FILE = 'ltz-game-catalog.dump';
const remote = '/tmp/' + DUMP_FILE;
export const MODES = ['export', 'download', 'verify', 'import'];
/** Klasör verilmezse dump burada aranır/yazılır (depo kökü; *.dump Git'e girmez). */
export const DEFAULT_DIRECTORY = path.join(root, 'game-catalog-dump');
export const REPLACE = '--replace';
export const USAGE = 'Kullanım: node ops/game-catalog-import.mjs export|download|verify|import [klasör] [--replace]  (klasör verilmezse game-catalog-dump)';
export const MAX_DOWNLOAD_BYTES = 4 * 1024 ** 3;
export const ALLOWED_HOSTS = ['github.com', 'objects.githubusercontent.com', 'release-assets.githubusercontent.com'];
/** Hesap kimliği taşıyabilecek tablolar: doluysa dışa aktarma reddedilir (katalog dışı, kişisel veri). */
export const ACCOUNT_DATA_TABLES = ['game_developer_grants', 'game_developer_feedback', 'game_admin_audits', 'game_identity_audits',
  'game_account_erasure_context', 'game_developer_contents', 'game_developer_media', 'game_update_notes', 'game_rating_projection'];

/** Docker çıktısından yalnız araç hata/özet satırlarını süzer (ayrıntı/ENV sızdırmaz). */
export function importSummary(output) {
  return output.split(/\r?\n/)
    .filter(line => /^(GAME_CATALOG_[A-Z_]+ |pg_dump: error|pg_restore: error|ERROR: |FATAL: )/.test(line)).join('\n');
}

function docker(args, input) {
  const result = spawnSync('docker', args, { encoding: 'utf8', maxBuffer: 64_000_000, input });
  if (result.status !== 0) {
    const summary = importSummary(result.stdout + '\n' + result.stderr);
    throw new Error('Docker işlemi başarısız: ' + args.slice(0, 2).join(' ') + (summary ? '\n' + summary : ''));
  }
  return result.stdout.trim();
}

/** Konteyner içinde psql çalıştırır; kullanıcı adı konteynerin kendi ortamından gelir (ENV dosyası okunmaz). */
function psql(sql, database = DATABASE) {
  return docker(['exec', '-i', POSTGRES_CONTAINER, 'sh', '-c', `psql -U "$POSTGRES_USER" -d ${database} -At -v ON_ERROR_STOP=1`], sql);
}

/** `<kip> [klasör] [--replace]` argümanlarını doğrular; klasör verilmezse game-catalog-dump; `--replace` yalnız import ile geçerlidir. */
export function parseArguments(argv) {
  const rest = [...argv];
  const mode = rest.shift();
  const replace = rest.at(-1) === REPLACE && mode === 'import' ? Boolean(rest.pop()) : false;
  if (!MODES.includes(mode) || rest.length > 1 || rest[0]?.startsWith('--')) throw new Error(USAGE);
  return { mode, directory: rest.length ? path.resolve(rest[0]) : DEFAULT_DIRECTORY, replace };
}

export async function sha256File(file) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

/** Yalnız ltz-dev Compose yığınındaki beklenen servis konteyneri kullanılabilir (etiketlere bakılır; ENV okunmaz). */
export function assertComposeContainer(labels, service) {
  if (labels['com.docker.compose.project'] !== 'ltz-dev' || labels['com.docker.compose.service'] !== service) {
    throw new Error('Yalnız ltz-dev ' + service + ' konteyneri kullanılabilir');
  }
}

export function assertDownloadUrl(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('İndirme adresi geçersiz'); }
  if (url.protocol !== 'https:' || !ALLOWED_HOSTS.includes(url.hostname)) throw new Error('İndirme adresi izinli GitHub sunucularından biri olmalı (https)');
}

/** Manifestin biçimini doğrular; geçersizse nedenini söyleyen hata fırlatır. */
export function validateManifest(manifest) {
  const fail = reason => { throw new Error('Manifest geçersiz: ' + reason); };
  if (!manifest || manifest.format !== 1) fail('format');
  if (manifest.file !== DUMP_FILE) fail('file');
  if (!/^[0-9a-f]{64}$/.test(manifest.sha256 ?? '')) fail('sha256');
  if (!Number.isInteger(manifest.bytes) || manifest.bytes <= 0 || manifest.bytes > MAX_DOWNLOAD_BYTES) fail('bytes');
  if (!Number.isInteger(manifest.flywayVersion) || manifest.flywayVersion < 31) fail('flywayVersion');
  if (!manifest.tables || !(manifest.tables.games > 0)) fail('tables.games');
  if (manifest.url !== null && manifest.url !== undefined) assertDownloadUrl(manifest.url);
  return manifest;
}

export async function readManifest(file = manifestPath) {
  let text;
  try { text = await fs.readFile(file, 'utf8'); } catch { throw new Error('Manifest yok: ops/game-catalog.manifest.json (önce export çalıştırın veya depoyu güncelleyin)'); }
  return validateManifest(JSON.parse(text));
}

/** Dump dosyasının boyutunu ve SHA-256'sını manifestle karşılaştırır. */
export async function verifyDumpFile(file, manifest) {
  let stat;
  try { stat = await fs.stat(file); } catch { throw new Error('Dump dosyası bulunamadı: ' + file + ' (dosyayı bu klasöre koyun veya manifesttaki adresten indirin)'); }
  if (stat.size !== manifest.bytes || await sha256File(file) !== manifest.sha256) throw new Error('Dump bütünlüğü doğrulanamadı: ' + path.basename(file));
}

/** Depodaki en yeni Game migration sürümü (dosya adlarından). */
export function latestMigration(fileNames) {
  const versions = fileNames.map(name => /^V(\d+)__/.exec(name)).filter(Boolean).map(match => Number(match[1]));
  return versions.length ? Math.max(...versions) : 0;
}

/** Dump, bu kaynak kodunun bildiğinden daha yeni bir şemayla alındıysa Flyway doğrulaması servisi açmaz; baştan reddedilir. */
export function assertCompatible(manifest, latest) {
  if (manifest.flywayVersion > latest) {
    throw new Error(`Dump V${manifest.flywayVersion} şemasıyla alınmış, bu depodaki en yeni Game migration V${latest}; önce dalı güncelleyin`);
  }
}

export function assertNoAccountData(tables) {
  const filled = ACCOUNT_DATA_TABLES.filter(name => (tables[name] ?? 0) > 0);
  if (filled.length) throw new Error('Hesap verisi içerebilecek tablolar dolu, dışa aktarılmaz: ' + filled.join(', '));
}

/** `ad=sayı` satırlarını nesneye çevirir (özel `flyway=` ve `pg=` anahtarlarını ayırır). */
export function parseCounts(output) {
  const tables = {};
  const info = {};
  for (const line of output.split(/\r?\n/)) {
    const match = /^([a-z_]+)=(\d+)$/.exec(line.trim());
    if (!match) continue;
    (match[1] === 'flyway' || match[1] === 'pg' ? info : tables)[match[1]] = Number(match[2]);
  }
  return { tables, flyway: info.flyway, postgresMajor: info.pg };
}

export const COUNT_SQL = `
select relname || '=' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I', relname), false, true, '')))[1]::text
  from pg_stat_user_tables where schemaname = 'public' order by relname;
select 'flyway=' || max(version::int) from flyway_schema_history where success;
select 'pg=' || (current_setting('server_version_num')::int / 10000);
`;

export function tableCounts() {
  return parseCounts(psql(COUNT_SQL));
}

/** Manifestteki satır sayıları ile hedef veritabanını karşılaştırır; fark varsa tablo adlarını verir. */
export function compareCounts(expected, actual) {
  return Object.keys(expected).filter(name => actual[name] !== expected[name]);
}

function assertPostgres() {
  assertComposeContainer(JSON.parse(docker(['inspect', '--format', '{{json .Config.Labels}}', POSTGRES_CONTAINER])), 'postgres');
}

export async function exportCatalog(directory) {
  assertPostgres();
  const before = tableCounts();
  assertNoAccountData(before.tables);
  await fs.mkdir(directory, { recursive: true });
  docker(['exec', POSTGRES_CONTAINER, 'sh', '-c',
    `pg_dump -U "$POSTGRES_USER" -d ${DATABASE} -Fc -Z 6 --no-owner --no-privileges -f ${remote}`]);
  const target = path.join(directory, DUMP_FILE);
  try {
    docker(['cp', `${POSTGRES_CONTAINER}:${remote}`, target]);
  } finally {
    docker(['exec', POSTGRES_CONTAINER, 'rm', '-f', remote]);
  }
  const previous = await fs.readFile(manifestPath, 'utf8').then(JSON.parse, () => null);
  const stat = await fs.stat(target);
  const manifest = validateManifest({
    format: 1, createdAt: new Date().toISOString(), flywayVersion: before.flyway, postgresMajor: before.postgresMajor,
    file: DUMP_FILE, bytes: stat.size, sha256: await sha256File(target), url: previous?.url ?? null, tables: before.tables
  });
  await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
  console.log(`GAME_CATALOG_EXPORT_RESULT ${JSON.stringify({ file: target, bytes: manifest.bytes, sha256: manifest.sha256, games: manifest.tables.games, flyway: manifest.flywayVersion })}`);
  if (!manifest.url) console.log('GAME_CATALOG_EXPORT_NOTE dosyayı paylaşın ve manifestteki "url" alanına indirme adresini yazın (GitHub Release varlığı)');
}

/** Dump'ı manifestteki adresten indirir (yalnız izinli GitHub sunucuları, en çok 5 yönlendirme, boyut sınırı, SHA-256 doğrulaması). */
export async function download(directory, manifest, fetchImpl = fetch) {
  if (!manifest.url) throw new Error('Manifestte indirme adresi yok. Dump dosyasını (' + DUMP_FILE + ') ' + directory + ' klasörüne elle koyun.');
  await fs.mkdir(directory, { recursive: true });
  const target = path.join(directory, DUMP_FILE);
  const partial = target + '.part';
  let url = manifest.url;
  for (let hop = 0; hop <= 5; hop++) {
    assertDownloadUrl(url);
    const response = await fetchImpl(url, { redirect: 'manual', signal: AbortSignal.timeout(30 * 60_000) });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get('location');
      if (!location) throw new Error('Yönlendirme adresi yok');
      url = new URL(location, url).toString();
      continue;
    }
    if (!response.ok || !response.body) throw new Error('Dump indirilemedi (HTTP ' + response.status + ')');
    let received = 0;
    async function* limited(stream) {
      for await (const chunk of stream) {
        received += chunk.length;
        if (received > MAX_DOWNLOAD_BYTES) throw new Error('Dump boyut sınırını aştı');
        yield chunk;
      }
    }
    try {
      await pipeline(Readable.fromWeb(response.body), limited, createWriteStream(partial));
      await verifyDumpFile(partial, manifest);
    } catch (error) {
      await fs.rm(partial, { force: true });
      throw error;
    }
    await fs.rename(partial, target);
    console.log(`GAME_CATALOG_DOWNLOAD_RESULT ${JSON.stringify({ file: target, bytes: received })}`);
    return;
  }
  throw new Error('Çok fazla yönlendirme');
}

function gameServiceRunning() {
  const result = spawnSync('docker', ['inspect', '--format', '{{.State.Running}}', GAME_CONTAINER], { encoding: 'utf8' });
  return result.status === 0 && result.stdout.trim() === 'true';
}

/** game_db yoksa oluşturur; tablolar var mı ve kaç oyun var, döndürür. */
function targetState() {
  const exists = psql(`select 1 from pg_database where datname = '${DATABASE}'`, 'postgres');
  if (exists !== '1') psql(`create database ${DATABASE}`, 'postgres');
  const hasGames = psql(`select to_regclass('public.games') is not null`) === 't';
  const games = hasGames ? Number(psql('select count(*) from games')) : 0;
  return { hasTables: hasGames, games };
}

export async function importCatalog(directory, replace, manifest) {
  const file = path.join(directory, DUMP_FILE);
  await verifyDumpFile(file, manifest);
  assertCompatible(manifest, latestMigration(await fs.readdir(migrationDirectory)));
  assertPostgres();
  if (gameServiceRunning()) throw new Error('Game servisi çalışıyor; önce durdurun: docker compose -p ltz-dev --env-file .env.dev --profile dev-mail stop game-service');
  const state = targetState();
  if (state.games > 0 && !replace) {
    console.log(`GAME_CATALOG_IMPORT_SKIPPED game_db zaten dolu (${state.games} oyun); üzerine yazmak için --replace`);
    return;
  }
  docker(['cp', file, `${POSTGRES_CONTAINER}:${remote}`]);
  try {
    docker(['exec', POSTGRES_CONTAINER, 'sh', '-c',
      `pg_restore -U "$POSTGRES_USER" -d ${DATABASE} --no-owner --no-privileges --exit-on-error --single-transaction${state.hasTables ? ' --clean --if-exists' : ''} ${remote}`]);
  } finally {
    docker(['exec', POSTGRES_CONTAINER, 'rm', '-f', remote]);
  }
  const actual = tableCounts();
  const different = compareCounts(manifest.tables, actual.tables);
  if (different.length) throw new Error('İçe aktarma sonrası satır sayıları uyuşmuyor: ' + different.join(', '));
  console.log(`GAME_CATALOG_IMPORT_RESULT ${JSON.stringify({ games: actual.tables.games, flyway: actual.flyway, replaced: state.hasTables })}`);
}

export async function run(argv = process.argv.slice(2)) {
  const { mode, directory, replace } = parseArguments(argv);
  if (mode === 'export') return exportCatalog(directory);
  const manifest = await readManifest();
  if (mode === 'download') return download(directory, manifest);
  if (mode === 'verify') {
    await verifyDumpFile(path.join(directory, DUMP_FILE), manifest);
    assertCompatible(manifest, latestMigration(await fs.readdir(migrationDirectory)));
    console.log(`GAME_CATALOG_VERIFY_RESULT ${JSON.stringify({ games: manifest.tables.games, flyway: manifest.flywayVersion })}`);
    return;
  }
  return importCatalog(directory, replace, manifest);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { await run(); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
