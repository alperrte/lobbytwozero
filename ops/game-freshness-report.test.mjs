import { describe, test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { countRateLimitLines, evaluate, format, METRICS_SQL, parseMetrics, THRESHOLDS } from "./game-freshness-report.mjs";

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
