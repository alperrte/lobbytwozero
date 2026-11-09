import { describe, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  ALLOWED_VARIABLES,
  RESOURCES,
  V84_FILE,
  V85_FILE,
  buildMigrations,
  buildRows,
  readSeedRows,
  renderPreview
} from "./mail-templates.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const script = join(here, "mail-templates.mjs");
const migration = (file) => readFileSync(join(RESOURCES, "db/migration", file), "utf8");

describe("mail-templates", () => {
  const rows = buildRows();

  test("15 tür x TR/EN = 30 satır; WELCOME V85'e, kalan 28 satır V84'e gider", () => {
    assert.equal(rows.length, 30);
    assert.equal(new Set(rows.map((row) => row.code)).size, 15);
    const sql = buildMigrations(rows);
    assert.equal((sql[V84_FILE].match(/^\('/gm) ?? []).length, 28);
    assert.equal((sql[V85_FILE].match(/^\('/gm) ?? []).length, 2);
    assert.ok(!sql[V84_FILE].includes("'WELCOME'"));
  });

  test("yalnız izinli değişkenler kullanılır (Mail Service başka değişken çözmez)", () => {
    for (const row of rows) {
      for (const part of [row.subject, row.html, row.text]) {
        for (const [, name] of part.matchAll(/\{\{(\w+)\}\}/g)) {
          assert.ok(ALLOWED_VARIABLES.includes(name), `${row.code}/${row.locale}: bilinmeyen değişken ${name}`);
        }
      }
    }
  });

  test("dolar-alıntıyı bozacak karakter yok; her HTML başlık ve kategori etiketi taşır", () => {
    for (const row of rows) {
      assert.ok(!/\$/.test(row.html + row.subject + row.text), `${row.code}/${row.locale}: $ karakteri`);
      assert.match(row.html, /<h1 class="h1"/);
      assert.match(row.html, /class="eb-(accent|warn|danger|success)"/);
    }
  });

  test("EN konu ve düz metin V82/V83 seed'iyle aynıdır (yalnız HTML yenilendi)", () => {
    for (const seed of readSeedRows().filter((row) => row.locale === "en")) {
      const built = rows.find((row) => row.code === seed.code && row.locale === "en");
      assert.equal(built.subject, seed.subject, seed.code);
      assert.equal(built.text, seed.text, seed.code);
    }
  });

  test("TR metinler sitenin 'sen' dilinde: 'siz' kipi ekleri kalmamıştır", () => {
    for (const row of rows.filter((candidate) => candidate.locale === "tr")) {
      assert.ok(!/(yapmadıysanız|sıfırlayın|geçin\b|kodunuz|adresiniz|hesabınız|parolanız)/.test(row.subject + row.text), row.code);
    }
  });

  test("yayınlanmış V84 ve V85 üreticiyle birebir aynıdır (üretici bilinçsizce değişmesin)", () => {
    const sql = buildMigrations(rows);
    assert.equal(migration(V84_FILE), sql[V84_FILE]);
    assert.equal(migration(V85_FILE), sql[V85_FILE]);
    const check = spawnSync(process.execPath, [script, "check"], { encoding: "utf8" });
    assert.equal(check.status, 0, check.stderr);
  });

  test("önizleme gerçek kabuğu kullanır, değişken bırakmaz ve migration klasörüne yazmaz", () => {
    const layout = readFileSync(join(RESOURCES, "mail/layout.html"), "utf8");
    const html = renderPreview(rows.find((row) => row.code === "EMAIL_VERIFICATION" && row.locale === "tr"), layout);
    assert.match(html, /483920/);
    assert.ok(!html.includes("{{"));

    const directory = mkdtempSync(join(tmpdir(), "mail-templates-"));
    try {
      const run = spawnSync(process.execPath, [script, "preview", directory], { encoding: "utf8" });
      assert.equal(run.status, 0, run.stderr);
      assert.equal(readdirSync(directory).length, 30);
      const write = spawnSync(process.execPath, [script, "write", directory], { encoding: "utf8" });
      assert.equal(write.status, 0, write.stderr);
      assert.ok(readdirSync(directory).includes(V84_FILE));
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  test("bilinmeyen komut kullanım hatası verir (çıkış 2)", () => {
    const run = spawnSync(process.execPath, [script, "yaz"], { encoding: "utf8" });
    assert.equal(run.status, 2);
    assert.match(run.stderr, /Kullanım/);
  });
});
