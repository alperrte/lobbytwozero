import { describe, test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import {
  assertHardwareContainer,
  buildImportCommand,
  buildStandaloneCommand,
  importSummary,
  parseArguments,
  sha256Of,
  USAGE,
  verifyDatasetFiles
} from "./hardware-catalog-import.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const script = join(here, "hardware-catalog-import.mjs");
const source = readFileSync(script, "utf8");

describe("hardware-catalog-import", () => {
  test("argümanlar: varsayılan kip dry-run, kip ve klasör doğrulanır", () => {
    assert.deepEqual(parseArguments(["dry-run", "veri"]), { mode: "dry-run", directory: resolve("veri"), standalone: false });
    assert.equal(parseArguments(["import", "veri"]).mode, "import");
    assert.equal(parseArguments(["download", "veri"]).mode, "download");
    for (const bad of [[], ["import"], ["sil", "veri"], ["import", "veri", "fazla"]]) {
      assert.throws(() => parseArguments(bad), { message: USAGE }, bad.join(" "));
    }
  });

  test("içe aktarma komutu yalnız import kipinde --execute taşır", () => {
    assert.ok(buildImportCommand("import").includes("--execute"));
    assert.ok(!buildImportCommand("dry-run").includes("--execute"));
    assert.ok(buildImportCommand("dry-run").includes("ltz-dev-hardware-service-1"));
  });

  test("--standalone: yalnız bu bayrak kabul edilir ve çalışan konteyner gerektirmeyen komut kurulur", () => {
    assert.equal(parseArguments(["import", "veri", "--standalone"]).standalone, true);
    assert.equal(parseArguments(["import", "veri"]).standalone, false);
    assert.throws(() => parseArguments(["import", "veri", "--baska"]), { message: USAGE });
    assert.throws(() => parseArguments(["import", "veri", "--standalone", "fazla"]), { message: USAGE });
    const dry = buildStandaloneCommand("dry-run", "/veri");
    assert.deepEqual(dry.slice(0, 3), ["compose", "-p", "ltz-dev"]);
    assert.ok(dry.includes("--rm") && dry.includes("--no-deps"), "tek seferlik, bağımlılık başlatmaz");
    assert.ok(dry.includes("/veri:/dataset:ro"), "veri seti salt okunur bağlanır");
    assert.ok(!dry.includes("--execute"));
    assert.ok(buildStandaloneCommand("import", "/veri").includes("--execute"));
  });

  test("özet süzgeci yalnız bilinen satırları geçirir", () => {
    const lf = String.fromCharCode(10);
    const out = ["SECRET=abc", "PC_PART_IMPORT_RESULT {}", "gürültü", "PC_PART_IMPORT_FAILED: X"].join(lf);
    assert.equal(importSummary(out), ["PC_PART_IMPORT_RESULT {}", "PC_PART_IMPORT_FAILED: X"].join(lf));
  });

  test("yalnız ltz-dev hardware-service konteyneri kabul edilir", () => {
    assertHardwareContainer({ "com.docker.compose.project": "ltz-dev", "com.docker.compose.service": "hardware-service" });
    for (const labels of [
      {},
      { "com.docker.compose.project": "baska", "com.docker.compose.service": "hardware-service" },
      { "com.docker.compose.project": "ltz-dev", "com.docker.compose.service": "auth-service" }
    ]) {
      assert.throws(() => assertHardwareContainer(labels), /Yalnız ltz-dev Hardware konteyneri/);
    }
  });

  test("dataset dosyaları manifestteki SHA-256 ile eşleşmezse reddedilir", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ltz-hw-import-"));
    try {
      writeFileSync(join(dir, "cpu.json"), "[1]");
      const good = { files: [{ file: "cpu.json", sha256: sha256Of(Buffer.from("[1]")) }] };
      await verifyDatasetFiles(dir, good);
      await assert.rejects(() => verifyDatasetFiles(dir, { files: [{ file: "cpu.json", sha256: "0".repeat(64) }] }), /Dosya hash uyuşmazlığı: cpu\.json/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  test("CLI: geçersiz kullanımda 1 koduyla çıkar, Docker'a hiç dokunmaz", () => {
    const result = spawnSync(process.execPath, [script, "sil", "veri"], { encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Kullanım: node ops\/hardware-catalog-import\.mjs/);
  });

  test("gerçek ENV dosyası okunmaz ve konteynerin Config.Env değerine bakılmaz", () => {
    assert.ok(!/readFile\([^)]*\.env/.test(source), "ENV dosyası okunmamalı");
    assert.ok(!/Config\.Env/.test(source.replace(/\/\/.*$/gm, "")), "Config.Env incelenmemeli");
    assert.match(source, /\.Config\.Labels/);
  });

  test("manifest her dosya için SHA-256 içerir", () => {
    const manifest = JSON.parse(readFileSync(join(here, "..", "backend/services/hardware-service/src/main/resources/catalog/pc-part-dataset.json"), "utf8"));
    assert.ok(manifest.files.length > 0);
    for (const file of manifest.files) assert.match(file.sha256, /^[0-9a-f]{64}$/, file.file);
  });
});
