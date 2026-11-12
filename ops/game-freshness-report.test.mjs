import { describe, test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { countRateLimitLines, evaluate, format, METRICS_SQL, parseLogs, parseMetrics, priceMarkets, PRICE_TTL_MINUTES, THRESHOLDS } from "./game-freshness-report.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "game-freshness-report.mjs"), "utf8");
const healthy = () => ({
  unknown: 9000, main: 150000, dlc: 40000, listed: 140000, adult: 9000, delisted: 40, status_filled: 148000, sync_age_minutes: 20,
  release_never: 100, price_fresh_US: 120000, rate_limit_lines: 0
});

describe("game-freshness-report", () => {
  test("ölçüm satırları ayrıştırılır; tanınmayan satırlar ve metin yok sayılır", () => {
    assert.deepEqual(parseMetrics("unknown=12\nmain=345\nprice_fresh_US=7\nsync_age_minutes=-1\nbozuk satır\nname=abc\n"),
      { unknown: 12, main: 345, price_fresh_US: 7, sync_age_minutes: -1 });
  });

  test("sağlıklı sistem uyarı üretmez ve yeşil özet verir", () => {
    const report = evaluate(healthy());
    assert.deepEqual(report.alerts, []);
    assert.match(format(report), /TÜM KONTROLLER YEŞİL/);
    assert.equal(report.rows.listed, 140000);
  });

  test("eşik aşımları ayrı ayrı uyarı üretir", () => {
    const sync = evaluate({ ...healthy(), sync_age_minutes: THRESHOLDS.maxSyncAgeMinutes + 1 });
    assert.equal(sync.alerts.length, 1); assert.match(sync.alerts[0], /Steam turu/);
    assert.match(evaluate({ ...healthy(), sync_age_minutes: -1 }).alerts[0], /hiç başarıyla/);
    assert.match(evaluate({ ...healthy(), unknown: 100000 }).alerts[0], /Sınıflanmamış/);
    assert.match(evaluate({ ...healthy(), delisted: 5000 }).alerts[0], /yanlış delist/);
    assert.match(evaluate({ ...healthy(), status_filled: 1000 }).alerts[0], /\+18/);
    assert.match(evaluate({ ...healthy(), price_fresh_US: 1000 }).alerts[0], /güncel fiyatlı/);
    assert.match(evaluate({ ...healthy(), rate_limit_lines: 21 }).alerts[0], /hız sınırı/);
    assert.equal(evaluate({ ...healthy(), rate_limit_lines: 20 }).alerts.length, 0, "eşiğin kendisi uyarı değildir");
  });

  test("veri hiç yokken bölme hatası olmaz", () => {
    const report = evaluate({});
    assert.equal(report.rows.unknownShare, 0);
    assert.equal(report.alerts.length, 1, "yalnız 'tur hiç tamamlanmadı' uyarısı");
  });

  test("günlük satırları sayılır; yalnız hız sınırı satırları", () => {
    assert.equal(countRateLimitLines("a\nSteam RATE_LIMITED kod\nb\nPRICE_RATE_LIMITED x\nHTTP 429 hata\n"), 3);
    assert.equal(countRateLimitLines(""), 0);
  });

  test("fiyat ölçümleri pazar başına gruplanır: taze, bayat, kademe gecikmesi, NO_OFFER, FAILED", () => {
    const metrics = parseMetrics("price_fresh_DE=100\nprice_stale_DE=40\nprice_due_DE_hot=5\nprice_due_DE_warm=6\nprice_due_DE_cold=7\nprice_noffer_DE=30\nprice_noffer_old_DE=10\nprice_failed_DE=2\nprice_fresh_TR=9\nprice_stale_TR=1\n");
    assert.deepEqual(priceMarkets(metrics), {
      DE: { fresh: 100, stale: 40, noOffer: 30, noOfferOld: 10, failed: 2, due: { hot: 5, warm: 6, cold: 7 } },
      TR: { fresh: 9, stale: 1, noOffer: 0, noOfferOld: 0, failed: 0, due: { hot: 0, warm: 0, cold: 0 } }
    });
    const text = format(evaluate({ ...healthy(), ...metrics }));
    assert.match(text, /Fiyat DE: taze=100 bayat\(STALE\)=40 kontrolü gelmiş sıcak\/ılık\/soğuk=5\/6\/7 NO_OFFER=30 \(24 sa\+ eski 10\) FAILED=2/);
    assert.match(text, /Fiyat TR: taze=9 bayat\(STALE\)=1/);
  });

  test("günlük ayrıştırma: ITAD 429, yerel hız sınırı, Steam bütçesi ve pazar turları ayrı sayılır", () => {
    const log = [
      "x INFO : Fiyat pazarı turu: pazar=DE-EUR aday=2000 yenilenen=2000 sonuç=OK süre=30000 ms sorgu_ms=300 itad_ms=1500 steam_ms=900 yazma_ms=100 parti=10",
      "x INFO : Fiyat pazarı turu: pazar=DE-EUR aday=2000 yenilenen=0 sonuç=PRICE_LOCAL_THROTTLE süre=500 ms sorgu_ms=500 itad_ms=0 steam_ms=0 yazma_ms=0 parti=0",
      "x INFO : Fiyat pazarı turu: pazar=TR-TRY aday=100 yenilenen=0 sonuç=PROVIDER_BLOCKED süre=5 ms",
      "x WARN : ITAD kotasi: olay=429 yol=prices pazar=DE-EUR oncelik=BACKGROUND pencere_ici=412 son60sn=88",
      "x WARN : ITAD kotasi: olay=429 yol=lookup pazar=DE-EUR oncelik=INTERACTIVE pencere_ici=100 son60sn=10",
      "x INFO : Fiyat pazarı turu: pazar=DE-EUR aday=2000 yenilenen=0 sonuç=PRICE_RATE_LIMITED süre=700 ms",
      "x WARN : Steam bütçe RATE_LIMITED"
    ].join("\n");
    const logs = parseLogs(log);
    assert.equal(logs.itad429, 2, "olay=429 satırları sayılır; aynı olayın tur satırı çift sayılmaz");
    assert.equal(logs.localThrottle, 1);
    assert.equal(logs.providerBlocked, 1);
    assert.equal(logs.steamRateLimited, 1, "PRICE_RATE_LIMITED Steam sayılmaz");
    assert.deepEqual(logs.windowSamples, [412, 100]);
    assert.deepEqual(logs.markets["DE-EUR"], { rounds: 3, ok: 1, refreshed: 2000, timed: 2, queryMs: 800, itadMs: 1500, steamMs: 900, writeMs: 100 });
    const report = evaluate({ ...healthy(), logs });
    assert.equal(report.rows.windowMedianAt429, 256);
    assert.equal(report.rows.windowMinAt429, 100);
    assert.equal(report.alerts.length, 0, "eşik 3, burada 2");
    assert.match(format(report), /Günlük \(1 sa\): ITAD 429=2 yerel hız sınırı turu=1 sağlayıcı bloğu turu=1 Steam bütçe=1; 429'da pencere içi istek medyan\/en az=256\/100/);
    assert.match(format(report), /Tur DE-EUR: tur=3 başarılı=1 yenilenen=2000 ort sorgu\/itad\/steam\/yazma ms=400\/750\/450\/50/);
  });

  test("S1 öncesi günlükte olay=429 yoksa ITAD 429'u tur satırındaki PRICE_RATE_LIMITED sayısıdır; eşik aşımı uyarı verir", () => {
    const old = "Fiyat pazarı turu: pazar=DE-EUR aday=2000 yenilenen=0 sonuç=PRICE_RATE_LIMITED süre=700 ms\n".repeat(4);
    const logs = parseLogs(old);
    assert.equal(logs.itad429, 4);
    const report = evaluate({ ...healthy(), logs });
    assert.equal(report.alerts.length, 1);
    assert.match(report.alerts[0], /ITAD'in kendi 429'u 4 kez \(eşik 3\)/);
    assert.equal(evaluate({ ...healthy(), logs: parseLogs(old.split("\n").slice(0, 3).join("\n")) }).alerts.length, 0, "eşiğin kendisi uyarı değildir");
  });

  test("kademe aralıkları ve SQL tutarlıdır: sıcak 20 dk, ılık 6x, soğuk 24x", () => {
    assert.deepEqual(PRICE_TTL_MINUTES, { hot: 20, warm: 120, cold: 480 });
    assert.match(METRICS_SQL, /interval '20 minutes'/);
    assert.match(METRICS_SQL, /interval '120 minutes'/);
    assert.match(METRICS_SQL, /interval '480 minutes'/);
  });

  test("rapor salt okunurdur: yalnız SELECT, salt okunur oturum, ENV dosyası okunmaz", () => {
    const statements = METRICS_SQL.split(";").map(statement => statement.trim()).filter(Boolean);
    assert.ok(statements.every(statement => /^select\b/i.test(statement)), "tüm ifadeler SELECT");
    assert.ok(!/\b(insert|update|delete|drop|alter|truncate|create)\b/i.test(METRICS_SQL), "yazma ifadesi yok");
    assert.match(source, /default_transaction_read_only=on/);
    assert.ok(!/readFile\([^)]*\.env/.test(source), "ENV dosyası okunmamalı");
    assert.ok(!/Config\.Env/.test(source.replace(/\/\/.*$/gm, "")), "Config.Env incelenmemeli");
  });

  test("CLI: geçersiz kullanımda 1 koduyla çıkar ve Docker'a dokunmaz", () => {
    const result = spawnSync(process.execPath, [join(here, "game-freshness-report.mjs"), "--bilinmeyen"], { encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Kullanım/);
  });
});
