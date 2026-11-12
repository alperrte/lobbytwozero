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
  maxRateLimitLogLines: 20,
  /** Son bir saatte ITAD'in KENDİ 429'u (günlükte `olay=429`) bunu aşarsa uyarı: kota gerçekten aşılıyor demektir; yerel hız sınırı (PRICE_LOCAL_THROTTLE) sayılmaz. */
  maxItad429PerHour: 3
});

/**
 * Fiyat yenileme kademe aralıkları (dakika), `ltz.game.pricing.ttl` (20 dk) ile eşleşmelidir: sıcak 1x, ılık 6x, soğuk 24x. Kademe tanımı
 * (editör seçimi / çıkış tarihi aralıkları) GamePriceRepository.tierRows ile aynıdır; değişirse buradaki SQL de güncellenmelidir.
 */
export const PRICE_TTL_MINUTES = Object.freeze({ hot: 20, warm: 120, cold: 480 });

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
select 'price_stale_' || t.country || '=' || count(*) from (select country, game_id from game_price_quotes group by country, game_id having max(expires_at) <= now()) t group by t.country;
select 'price_noffer_' || country || '=' || count(*) from game_price_refresh_state where status = 'NO_OFFER' group by country;
select 'price_noffer_old_' || country || '=' || count(*) from game_price_refresh_state where status = 'NO_OFFER' and checked_at < now() - interval '24 hours' group by country;
select 'price_failed_' || country || '=' || count(*) from game_price_refresh_state where status = 'FAILED' group by country;
select 'price_due_' || s.country || '_' || t.tier || '=' || count(*) from game_price_refresh_state s
  join (select g.id, case when g.editor_rank is not null or g.release_date between current_date - 90 and current_date + 30 then 'hot'
                          when g.release_date >= current_date - 730 and g.release_date < current_date - 90 then 'warm' else 'cold' end as tier
        from games g where g.status = 'ACTIVE' and g.game_type in ('MAIN_GAME', 'DLC') and g.delisted_at is null
          and exists (select 1 from game_external_refs r where r.game_id = g.id and r.provider = 'STEAM')) t on t.id = s.game_id
  where s.status in ('AVAILABLE', 'NO_OFFER')
    and s.checked_at < now() - (case t.tier when 'hot' then interval '${PRICE_TTL_MINUTES.hot} minutes' when 'warm' then interval '${PRICE_TTL_MINUTES.warm} minutes' else interval '${PRICE_TTL_MINUTES.cold} minutes' end)
  group by s.country, t.tier;
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

const median = values => {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : Math.round((sorted[middle - 1] + sorted[middle]) / 2);
};

/**
 * Game günlüğünden fiyat yenileme gözlemi (yalnız sayılar; ayrıntı/ENV sızdırmaz): ITAD'in kendi 429'u (`olay=429`), yerel hız sınırı
 * (PRICE_LOCAL_THROTTLE), sağlayıcı bloğu (PROVIDER_BLOCKED), Steam bütçesi (RATE_LIMITED) ve pazar başına tur sonuçları/süreleri.
 * S1 öncesi günlükte `olay=429` yoktur; o zaman ITAD 429'u tur satırındaki `sonuç=PRICE_RATE_LIMITED` sayısıdır.
 */
export function parseLogs(logText) {
  const result = { itad429: 0, localThrottle: 0, providerBlocked: 0, steamRateLimited: 0, windowSamples: [], markets: {} };
  let roundRateLimited = 0;
  for (const line of logText.split(/\r?\n/)) {
    if (/olay=429/.test(line)) {
      result.itad429 += 1;
      const window = /pencere_ici=(\d+)/.exec(line);
      if (window) result.windowSamples.push(Number(window[1]));
      continue;
    }
    const round = /Fiyat pazarı turu: pazar=([A-Z]{2}-[A-Z]{3}) aday=(\d+) yenilenen=(\d+) sonuç=([A-Za-z_]+) süre=(\d+) ms(.*)$/.exec(line);
    if (round) {
      const [, market, , refreshed, outcome, , rest] = round;
      const stats = result.markets[market] ??= { rounds: 0, ok: 0, refreshed: 0, timed: 0, queryMs: 0, itadMs: 0, steamMs: 0, writeMs: 0 };
      stats.rounds += 1;
      stats.refreshed += Number(refreshed);
      if (outcome === 'OK') stats.ok += 1;
      if (outcome === 'PRICE_RATE_LIMITED') roundRateLimited += 1;
      if (outcome === 'PRICE_LOCAL_THROTTLE') result.localThrottle += 1;
      if (outcome === 'PROVIDER_BLOCKED') result.providerBlocked += 1;
      const timing = /sorgu_ms=(\d+) itad_ms=(\d+) steam_ms=(\d+) yazma_ms=(\d+)/.exec(rest);
      if (timing) {
        stats.timed += 1;
        stats.queryMs += Number(timing[1]); stats.itadMs += Number(timing[2]); stats.steamMs += Number(timing[3]); stats.writeMs += Number(timing[4]);
      }
    } else if (/RATE_LIMITED/.test(line) && !/PRICE_RATE_LIMITED/.test(line)) result.steamRateLimited += 1;
  }
  if (result.itad429 === 0) result.itad429 = roundRateLimited;
  return result;
}

/** Pazar başına fiyat ölçümlerini `price_<tür>_<ÜLKE>` anahtarlarından toplar. */
export function priceMarkets(metrics) {
  const markets = {};
  const entry = country => markets[country] ??= { fresh: 0, stale: 0, noOffer: 0, noOfferOld: 0, failed: 0, due: { hot: 0, warm: 0, cold: 0 } };
  for (const [key, value] of Object.entries(metrics)) {
    let match;
    if ((match = /^price_fresh_([A-Z]{2})$/.exec(key))) entry(match[1]).fresh = value;
    else if ((match = /^price_stale_([A-Z]{2})$/.exec(key))) entry(match[1]).stale = value;
    else if ((match = /^price_noffer_old_([A-Z]{2})$/.exec(key))) entry(match[1]).noOfferOld = value;
    else if ((match = /^price_noffer_([A-Z]{2})$/.exec(key))) entry(match[1]).noOffer = value;
    else if ((match = /^price_failed_([A-Z]{2})$/.exec(key))) entry(match[1]).failed = value;
    else if ((match = /^price_due_([A-Z]{2})_(hot|warm|cold)$/.exec(key))) entry(match[1]).due[match[2]] = value;
  }
  return markets;
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
    rateLimitLogLines: metrics.rate_limit_lines ?? 0,
    priceMarkets: priceMarkets(metrics),
    itad429PerHour: metrics.logs?.itad429 ?? 0,
    localThrottleRounds: metrics.logs?.localThrottle ?? 0,
    providerBlockedRounds: metrics.logs?.providerBlocked ?? 0,
    steamRateLimited: metrics.logs?.steamRateLimited ?? 0,
    windowMedianAt429: median(metrics.logs?.windowSamples ?? []),
    windowMinAt429: (metrics.logs?.windowSamples ?? []).length ? Math.min(...metrics.logs.windowSamples) : null,
    logMarkets: metrics.logs?.markets ?? {}
  };
  if (rows.syncAgeMinutes < 0) alerts.push('Steam artımlı turu henüz hiç başarıyla tamamlanmadı');
  else if (rows.syncAgeMinutes > thresholds.maxSyncAgeMinutes) alerts.push(`Son başarılı Steam turu ${rows.syncAgeMinutes} dakika önce (eşik ${thresholds.maxSyncAgeMinutes})`);
  if (rows.unknownShare > thresholds.maxUnknownShare) alerts.push(`Sınıflanmamış kayıt payı %${(rows.unknownShare * 100).toFixed(1)} (eşik %${thresholds.maxUnknownShare * 100})`);
  if (rows.delistedShare > thresholds.maxDelistedShare) alerts.push(`Mağazadan kaldırılmış oyun payı %${(rows.delistedShare * 100).toFixed(1)} (eşik %${thresholds.maxDelistedShare * 100}); toplu yanlış delist olabilir`);
  if (main > 0 && rows.statusFilledShare < thresholds.minStatusFilledShare) alerts.push(`+18/ücretsiz/yakında bilgisi dolu oyun payı %${(rows.statusFilledShare * 100).toFixed(1)} (eşik %${thresholds.minStatusFilledShare * 100})`);
  if (main > 0 && rows.freshPriceShareUS < thresholds.minFreshPriceShare) alerts.push(`ABD pazarında güncel fiyatlı oyun payı %${(rows.freshPriceShareUS * 100).toFixed(1)} (eşik %${thresholds.minFreshPriceShare * 100})`);
  if (rows.itad429PerHour > thresholds.maxItad429PerHour) alerts.push(`Son 1 saatte ITAD'in kendi 429'u ${rows.itad429PerHour} kez (eşik ${thresholds.maxItad429PerHour}); pencere içi istek medyanı ${rows.windowMedianAt429 ?? 'bilinmiyor'}`);
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
  for (const [country, market] of Object.entries(rows.priceMarkets).sort(([a], [b]) => a.localeCompare(b))) {
    lines.push(`Fiyat ${country}: taze=${market.fresh} bayat(STALE)=${market.stale} kontrolü gelmiş sıcak/ılık/soğuk=${market.due.hot}/${market.due.warm}/${market.due.cold} NO_OFFER=${market.noOffer} (24 sa+ eski ${market.noOfferOld}) FAILED=${market.failed}`);
  }
  lines.push(`Günlük (1 sa): ITAD 429=${rows.itad429PerHour} yerel hız sınırı turu=${rows.localThrottleRounds} sağlayıcı bloğu turu=${rows.providerBlockedRounds} Steam bütçe=${rows.steamRateLimited}; 429'da pencere içi istek medyan/en az=${rows.windowMedianAt429 ?? '-'}/${rows.windowMinAt429 ?? '-'}`);
  for (const [market, stats] of Object.entries(rows.logMarkets).sort(([a], [b]) => a.localeCompare(b))) {
    const avg = value => (stats.timed ? Math.round(value / stats.timed) : '-');
    lines.push(`Tur ${market}: tur=${stats.rounds} başarılı=${stats.ok} yenilenen=${stats.refreshed} ort sorgu/itad/steam/yazma ms=${avg(stats.queryMs)}/${avg(stats.itadMs)}/${avg(stats.steamMs)}/${avg(stats.writeMs)}`);
  }
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
    const text = (logs.stdout ?? '') + '\n' + (logs.stderr ?? '');
    metrics.rate_limit_lines = countRateLimitLines(text);
    metrics.logs = parseLogs(text);
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
