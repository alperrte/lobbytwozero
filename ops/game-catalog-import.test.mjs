import { describe, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  ACCOUNT_DATA_TABLES,
  assertCompatible,
  assertComposeContainer,
  assertDownloadUrl,
  assertNoAccountData,
  compareCounts,
  download,
  DUMP_FILE,
  importSummary,
  latestMigration,
  parseArguments,
  parseCounts,
  readManifest,
  sha256File,
  USAGE,
  validateManifest,
  verifyDumpFile
} from "./game-catalog-import.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const script = join(here, "game-catalog-import.mjs");
const source = readFileSync(script, "utf8");
const validManifest = () => ({
  format: 1, createdAt: "2026-11-08T00:00:00Z", flywayVersion: 175, postgresMajor: 17, file: DUMP_FILE,
  bytes: 3, sha256: "a".repeat(64), url: null, tables: { games: 5, game_terms: 7 }
});

describe("game-catalog-import", () => {
  test("argümanlar: kip ve klasör zorunlu, --replace yalnız import ile", () => {
    assert.deepEqual(parseArguments(["import", "veri"]), { mode: "import", directory: resolve("veri"), replace: false });
    assert.equal(parseArguments(["import", "veri", "--replace"]).replace, true);
    for (const bad of [[], ["import"], ["sil", "veri"], ["export", "veri", "--replace"], ["import", "veri", "--baska"], ["import", "veri", "--replace", "fazla"]]) {
      assert.throws(() => parseArguments(bad), { message: USAGE }, bad.join(" "));
    }
  });

  test("yalnız ltz-dev yığınındaki beklenen servis konteyneri kabul edilir", () => {
    assertComposeContainer({ "com.docker.compose.project": "ltz-dev", "com.docker.compose.service": "postgres" }, "postgres");
    for (const labels of [{}, { "com.docker.compose.project": "baska", "com.docker.compose.service": "postgres" },
      { "com.docker.compose.project": "ltz-dev", "com.docker.compose.service": "auth-service" }]) {
      assert.throws(() => assertComposeContainer(labels, "postgres"), /Yalnız ltz-dev postgres konteyneri/);
    }
  });

  test("manifest: geçerli biçim kabul edilir, bozuk alanlar reddedilir", () => {
    assert.doesNotThrow(() => validateManifest(validManifest()));
    assert.doesNotThrow(() => validateManifest({ ...validManifest(), url: "https://github.com/o/r/releases/download/v1/ltz-game-catalog.dump" }));
    const cases = [{ format: 2 }, { file: "baska.dump" }, { sha256: "xyz" }, { bytes: 0 }, { bytes: 1.5 }, { flywayVersion: 5 },
      { tables: {} }, { tables: { games: 0 } }, { url: "http://github.com/x" }, { url: "https://example.com/x" }];
    for (const change of cases) assert.throws(() => validateManifest({ ...validManifest(), ...change }), /Manifest geçersiz|İndirme adresi/, JSON.stringify(change));
  });

  test("indirme adresi yalnız izinli GitHub sunucularına https ile gidebilir", () => {
    assertDownloadUrl("https://objects.githubusercontent.com/a/b");
    for (const bad of ["http://github.com/a", "https://evil.example/a", "https://github.com.evil.example/a", "dosya:///c:/x", "değil"]) {
      assert.throws(() => assertDownloadUrl(bad), /İndirme adresi/, bad);
    }
  });

  test("depodaki en yeni migration sürümü dosya adlarından bulunur; daha yeni dump reddedilir", () => {
    assert.equal(latestMigration(["V31__a.sql", "V175__b.sql", "V40__c.sql", "README.md"]), 175);
    assert.equal(latestMigration([]), 0);
    assert.doesNotThrow(() => assertCompatible({ flywayVersion: 175 }, 175));
    assert.doesNotThrow(() => assertCompatible({ flywayVersion: 174 }, 175));
    assert.throws(() => assertCompatible({ flywayVersion: 176 }, 175), /V176.*V175/);
  });

  test("hesap verisi içerebilecek tablo doluysa dışa aktarma reddedilir", () => {
    assert.doesNotThrow(() => assertNoAccountData({ games: 10, game_developer_feedback: 0 }));
    assert.throws(() => assertNoAccountData({ games: 10, game_developer_grants: 2 }), /game_developer_grants/);
    assert.ok(ACCOUNT_DATA_TABLES.includes("game_admin_audits") && ACCOUNT_DATA_TABLES.includes("game_developer_feedback"));
  });

  test("satır sayıları ayrıştırılır ve karşılaştırılır", () => {
    const counts = parseCounts(["games=5", "game_terms=7", "flyway=175", "pg=17", "gürültü", "x=y"].join("\n"));
    assert.deepEqual(counts, { tables: { games: 5, game_terms: 7 }, flyway: 175, postgresMajor: 17 });
    assert.deepEqual(compareCounts({ games: 5, game_terms: 7 }, { games: 5, game_terms: 7 }), []);
    assert.deepEqual(compareCounts({ games: 5, game_terms: 7 }, { games: 5, game_terms: 6 }), ["game_terms"]);
    assert.deepEqual(compareCounts({ games: 5 }, {}), ["games"]);
  });

  test("özet süzgeci yalnız bilinen satırları geçirir", () => {
    const out = ["POSTGRES_PASSWORD=gizli", "GAME_CATALOG_IMPORT_RESULT {}", "gürültü", "pg_restore: error: boom"].join("\n");
    assert.equal(importSummary(out), ["GAME_CATALOG_IMPORT_RESULT {}", "pg_restore: error: boom"].join("\n"));
  });

  test("dump doğrulaması: boyut ve SHA-256 eşleşmezse reddedilir; indirme adresiz manifest anlamlı hata verir", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ltz-game-import-"));
    try {
      const file = join(dir, DUMP_FILE);
      writeFileSync(file, "abc");
      const manifest = { ...validManifest(), bytes: 3, sha256: await sha256File(file) };
      await verifyDumpFile(file, manifest);
      await assert.rejects(() => verifyDumpFile(file, { ...manifest, sha256: "0".repeat(64) }), /Dump bütünlüğü doğrulanamadı/);
      await assert.rejects(() => verifyDumpFile(file, { ...manifest, bytes: 4 }), /Dump bütünlüğü doğrulanamadı/);
      await assert.rejects(() => verifyDumpFile(join(dir, "yok.dump"), manifest), /Dump dosyası bulunamadı/);
      await assert.rejects(() => download(dir, manifest), /indirme adresi yok/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  test("indirme: yönlendirmeyi izler, izinli olmayan adrese gitmez, bozuk içeriği siler", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ltz-game-download-"));
    try {
      const body = Buffer.from("abc");
      const manifest = { ...validManifest(), url: "https://github.com/o/r/releases/download/v1/x.dump", bytes: 3, sha256: await (async () => {
        const file = join(dir, "ornek"); writeFileSync(file, body); return sha256File(file);
      })() };
      const calls = [];
      const fetchOk = async (url) => {
        calls.push(url);
        if (url.startsWith("https://github.com/")) return { status: 302, ok: false, headers: new Headers({ location: "https://objects.githubusercontent.com/blob" }) };
        return { status: 200, ok: true, headers: new Headers(), body: new Response(body).body };
      };
      await download(dir, manifest, fetchOk);
      assert.equal(readFileSync(join(dir, DUMP_FILE), "utf8"), "abc");
      assert.equal(calls.length, 2);
      const evil = async () => ({ status: 302, ok: false, headers: new Headers({ location: "https://evil.example/x" }) });
      await assert.rejects(() => download(dir, manifest, evil), /İndirme adresi/);
      const wrong = async () => ({ status: 200, ok: true, headers: new Headers(), body: new Response("xyz").body });
      await assert.rejects(() => download(dir, manifest, wrong), /Dump bütünlüğü doğrulanamadı/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  test("depodaki manifest geçerlidir", async () => {
    const manifest = await readManifest();
    assert.ok(manifest.tables.games > 0 && manifest.flywayVersion >= 175);
  });

  test("CLI: geçersiz kullanımda 1 koduyla çıkar, Docker'a hiç dokunmaz", () => {
    const result = spawnSync(process.execPath, [script, "sil", "veri"], { encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Kullanım: node ops\/game-catalog-import\.mjs/);
  });

  test("gerçek ENV dosyası okunmaz ve konteynerin Config.Env değerine bakılmaz", () => {
    assert.ok(!/readFile\([^)]*\.env/.test(source), "ENV dosyası okunmamalı");
    assert.ok(!/Config\.Env/.test(source.replace(/\/\/.*$/gm, "")), "Config.Env incelenmemeli");
    assert.match(source, /\.Config\.Labels/);
  });
});
