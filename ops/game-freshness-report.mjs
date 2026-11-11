// Game servisi veri tazeliği raporu (SALT OKUNUR): dev veritabanına yalnız SELECT atar, hiçbir şey yazmaz, ENV dosyası okumaz.
// Kullanım: node ops/game-freshness-report.mjs [--json]   (eşik aşılırsa uyarıları yazar ve 1 koduyla çıkar)
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const POSTGRES_CONTAINER = 'ltz-dev-postgres-1';
export const GAME_CONTAINER = 'ltz-dev-game-service-1';
export const DATABASE = 'game_db';
export const USAGE = 'Kullanım: node ops/game-freshness-report.mjs [--json]';

/** Uyarı eşikleri; değerler "hedef sağlıklı sistem" için seçilmiştir (bkz. ops/game-catalog.md). */
export const THRESHOLDS = Object.freeze({
  /** Son başarılı Steam artımlı turundan bu kadar dakika geçtiyse uyarı (dev aralığı 60 dk, canlı 15 dk). */
  maxSyncAgeMinutes: 180,
  /** Sınıflanmamış (UNKNOWN) kayıtların toplam içindeki payı bunu aşarsa uyarı. */
  maxUnknownShare: 0.10,
  /** Mağazadan kaldırılmış (delist) oyunların ana oyunlar içindeki payı bunu aşarsa uyarı (toplu yanlış delist belirtisi). */
  maxDelistedShare: 0.02,
  /** Ana oyunlarda +18/ücretsiz/yakında bilgisinin dolu olma payı bunun altındaysa uyarı. */
  minStatusFilledShare: 0.90,
  /** ABD pazarında güncel (süresi dolmamış) fiyatı olan oyun payı bunun altındaysa uyarı. */
  minFreshPriceShare: 0.50,
  /** Son bir saatte game-service günlüğünde hız sınırı (429/RATE_LIMITED) satırı sayısı bunu aşarsa uyarı. */
  maxRateLimitLogLines: 20
});

/** Salt okunur sayım sorgusu: `ad=değer` satırları. */
export const METRICS_SQL = `
select 'unknown=' || count(*) from games where status = 'ACTIVE' and origin = 'EXTERNAL' and game_type = 'UNKNOWN';
select 'main=' || count(*) from games where status = 'ACTIVE' and game_type = 'MAIN_GAME';
select 'dlc=' || count(*) from games where status = 'ACTIVE' and game_type = 'DLC';
select 'listed=' || count(*) from games where status = 'ACTIVE' and game_type = 'MAIN_GAME' and delisted_at is null and adult is not true;
select 'delisted=' || count(*) from games where status = 'ACTIVE' and game_type = 'MAIN_GAME' and delisted_at is not null;
select 'adult=' || count(*) from games where status = 'ACTIVE' and game_type = 'MAIN_GAME' and adult is true;
select 'status_filled=' || count(*) from games where status = 'ACTIVE' and game_type = 'MAIN_GAME' and adult is not null and free is not null and coming_soon is not null;
select 'sync_age_minutes=' || coalesce(floor(extract(epoch from now() - last_successful_at) / 60)::bigint, -1) from game_provider_sync_state where provider = 'STEAM';
select 'release_never=' || count(*) from games g where g.status = 'ACTIVE' and g.game_type = 'MAIN_GAME'
  and not exists (select 1 from game_metadata_provenance p where p.game_id = g.id and p.field = 'RELEASE_STATUS');
select 'price_fresh_' || q.country || '=' || count(distinct q.game_id) from game_price_quotes q where q.expires_at > now() group by q.country;
`;

/** `ad=sayı` satırlarını nesneye çevirir; tanınmayan satırlar yok sayılır. */
export function parseMetrics(output) {
  const metrics = {};
  for (const line of output.split(/\r?\n/)) {
    const match = /^([a-z_A-Z]+)=(-?\d+)$/.exec(line.trim());
    if (match) metrics[match[1]] = Number(match[2]);
  }
  return metrics;
}

/** Günlük metninde hız sınırı satırı sayısı (ayrıntı/ENV sızdırmaz: yalnız sayı). */
export function countRateLimitLines(logText) {
  return logText.split(/\r?\n/).filter(line => /RATE_LIMITED|PRICE_RATE_LIMITED| 429 /.test(line)).length;
}

const share = (part, whole) => (whole > 0 ? part / whole : 0);

/** Ölçümleri eşiklerle karşılaştırır: { rows, alerts }. Saf fonksiyondur (test edilebilir). */
export function evaluate(metrics, thresholds = THRESHOLDS) {
  const alerts = [];
  const total = (metrics.unknown ?? 0) + (metrics.main ?? 0) + (metrics.dlc ?? 0);
  const main = metrics.main ?? 0;
  const rows = {
    unknown: metrics.unknown ?? 0, main, dlc: metrics.dlc ?? 0, listed: metrics.listed ?? 0, adult: metrics.adult ?? 0, delisted: metrics.delisted ?? 0,
    unknownShare: share(metrics.unknown ?? 0, total), delistedShare: share(metrics.delisted ?? 0, main),
    statusFilledShare: share(metrics.status_filled ?? 0, main), releaseNeverObserved: metrics.release_never ?? 0,
    syncAgeMinutes: metrics.sync_age_minutes ?? -1, freshPriceShareUS: share(metrics.price_fresh_US ?? 0, main),
    rateLimitLogLines: metrics.rate_limit_lines ?? 0
  };
  if (rows.syncAgeMinutes < 0) alerts.push('Steam artımlı turu henüz hiç başarıyla tamamlanmadı');
  else if (rows.syncAgeMinutes > thresholds.maxSyncAgeMinutes) alerts.push(`Son başarılı Steam turu ${rows.syncAgeMinutes} dakika önce (eşik ${thresholds.maxSyncAgeMinutes})`);
  if (rows.unknownShare > thresholds.maxUnknownShare) alerts.push(`Sınıflanmamış kayıt payı %${(rows.unknownShare * 100).toFixed(1)} (eşik %${thresholds.maxUnknownShare * 100})`);
  if (rows.delistedShare > thresholds.maxDelistedShare) alerts.push(`Mağazadan kaldırılmış oyun payı %${(rows.delistedShare * 100).toFixed(1)} (eşik %${thresholds.maxDelistedShare * 100}); toplu yanlış delist olabilir`);
  if (main > 0 && rows.statusFilledShare < thresholds.minStatusFilledShare) alerts.push(`+18/ücretsiz/yakında bilgisi dolu oyun payı %${(rows.statusFilledShare * 100).toFixed(1)} (eşik %${thresholds.minStatusFilledShare * 100})`);
  if (main > 0 && rows.freshPriceShareUS < thresholds.minFreshPriceShare) alerts.push(`ABD pazarında güncel fiyatlı oyun payı %${(rows.freshPriceShareUS * 100).toFixed(1)} (eşik %${thresholds.minFreshPriceShare * 100})`);
  if (rows.rateLimitLogLines > thresholds.maxRateLimitLogLines) alerts.push(`Son 1 saatte ${rows.rateLimitLogLines} hız sınırı günlük satırı (eşik ${thresholds.maxRateLimitLogLines})`);
  return { rows, alerts };
}

/** İnsan okuyabilir özet. */
export function format({ rows, alerts }) {
  const pct = value => `%${(value * 100).toFixed(1)}`;
  const lines = [
    `Oyun: ana=${rows.main} dlc=${rows.dlc} sınıfsız=${rows.unknown} (${pct(rows.unknownShare)}) listede=${rows.listed} +18=${rows.adult} kaldırılmış=${rows.delisted} (${pct(rows.delistedShare)})`,
    `Durum bilgisi dolu: ${pct(rows.statusFilledShare)}; hiç gözlenmemiş çıkış durumu: ${rows.releaseNeverObserved}`,
    `Son başarılı Steam turu: ${rows.syncAgeMinutes < 0 ? 'yok' : rows.syncAgeMinutes + ' dk önce'}; ABD güncel fiyat: ${pct(rows.freshPriceShareUS)}; hız sınırı günlük satırı (1 sa): ${rows.rateLimitLogLines}`
  ];
  return lines.concat(alerts.length ? alerts.map(alert => 'UYARI: ' + alert) : ['TÜM KONTROLLER YEŞİL']).join('\n');
}

function docker(args, input) {
  const result = spawnSync('docker', args, { encoding: 'utf8', maxBuffer: 64_000_000, input });
  if (result.status !== 0) {
    // Yalnız araç hata satırları (ayrıntı/ENV sızdırmaz); ör. şema henüz güncel değilse eksik kolon hatası burada görünür.
    const errors = ((result.stderr ?? '') + '\n' + (result.stdout ?? '')).split(/\r?\n/).filter(line => /^(ERROR: |FATAL: |psql: error)/.test(line)).slice(0, 3).join('\n');
    throw new Error('Docker işlemi başarısız: ' + args.slice(0, 2).join(' ') + (errors ? '\n' + errors : ''));
  }
  return result.stdout;
}

/** Salt okunur ölçüm: SELECT sorguları + günlük satır sayısı. Kullanıcı adı konteynerin kendi ortamından gelir (ENV dosyası okunmaz). */
export function collect() {
  const output = docker(['exec', '-i', POSTGRES_CONTAINER, 'sh', '-c', `psql -U "$POSTGRES_USER" -d ${DATABASE} -At -v ON_ERROR_STOP=1 --set=default_transaction_read_only=on`], METRICS_SQL);
  const metrics = parseMetrics(output);
  try {
    const logs = spawnSync('docker', ['logs', '--since', '1h', GAME_CONTAINER], { encoding: 'utf8', maxBuffer: 64_000_000 });
    metrics.rate_limit_lines = countRateLimitLines((logs.stdout ?? '') + '\n' + (logs.stderr ?? ''));
  } catch { metrics.rate_limit_lines = 0; }
  return metrics;
}

export function run(argv = process.argv.slice(2)) {
  if (argv.some(argument => argument !== '--json') || argv.length > 1) { console.error(USAGE); return 1; }
  const report = evaluate(collect());
  console.log(argv[0] === '--json' ? JSON.stringify(report, null, 2) : format(report));
  return report.alerts.length ? 1 : 0;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) process.exitCode = run();
