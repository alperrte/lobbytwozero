import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { formatEnvLine, parseArgs, validateSiteUrl } from "./site-url.mjs";

const script = join(dirname(fileURLToPath(import.meta.url)), "site-url.mjs");
const source = readFileSync(script, "utf8");

// Güvenlik bekçisi: araç dosya sistemine, ağa veya sürece erişmez; yalnız ekrana yazar.
test("script dosya/ağ/süreç/dinamik kod kullanmaz", () => {
  const imports = [...source.matchAll(/from\s+"([^"]+)"/g)].map((match) => match[1]).sort();
  assert.deepEqual(imports, ["node:readline/promises", "node:url"]);
  for (const forbidden of [
    "node:fs", "child_process", "node:http", "node:https", "node:net", "fetch(", "writeFile", "appendFile",
    "readFile", "process.env", "eval(", "new Function"
  ]) {
    assert.ok(!source.includes(forbidden), `izin verilmeyen kullanım: ${forbidden}`);
  }
});

test("https adresi kök adrese indirgenir", () => {
  const result = validateSiteUrl("https://Ltz.Example/yol/alt?x=1#a");
  assert.equal(result.ok, true);
  assert.equal(result.url, "https://ltz.example");
  assert.ok(result.warnings.length >= 1);
});

test("sondaki eğik çizgi atılır", () => {
  assert.equal(validateSiteUrl("https://ltz.example/").url, "https://ltz.example");
});

test("yerel http kabul edilir ama uyarı verir", () => {
  const result = validateSiteUrl("http://localhost:5001");
  assert.equal(result.ok, true);
  assert.equal(result.url, "http://localhost:5001");
  assert.ok(result.warnings.some((warning) => warning.includes("indekslenmez")));
});

test("yerel olmayan http reddedilir", () => {
  assert.equal(validateSiteUrl("http://ltz.example").ok, false);
});

test("production modu http ve localhost'u reddeder", () => {
  assert.equal(validateSiteUrl("http://localhost:5001", { production: true }).ok, false);
  assert.equal(validateSiteUrl("https://localhost", { production: true }).ok, false);
  assert.equal(validateSiteUrl("https://ltz.example", { production: true }).ok, true);
});

test("boş, geçersiz, ftp ve kimlik bilgili adresler reddedilir", () => {
  for (const bad of ["", "   ", "ltz.example", "ftp://ltz.example", "https://user:pw@ltz.example"]) {
    assert.equal(validateSiteUrl(bad).ok, false, bad);
  }
});

test("ENV satırı biçimi", () => {
  assert.equal(formatEnvLine("https://ltz.example"), "NEXT_PUBLIC_SITE_URL=https://ltz.example");
});

test("parseArgs seçenekleri ve hataları ayırır", () => {
  assert.deepEqual(parseArgs(["--help"]).help, true);
  assert.deepEqual(parseArgs(["https://a.example", "--production"]), {
    help: false,
    production: true,
    value: "https://a.example"
  });
  assert.ok(parseArgs(["--x"]).error);
  assert.ok(parseArgs(["a", "b"]).error);
});

test("CLI: geçerli adreste 0, geçersizde 1 ile çıkar ve satırı yazar", () => {
  const ok = spawnSync(process.execPath, [script, "https://ltz.example/"], { encoding: "utf8" });
  assert.equal(ok.status, 0);
  assert.match(ok.stdout, /^NEXT_PUBLIC_SITE_URL=https:\/\/ltz\.example$/m);

  const bad = spawnSync(process.execPath, [script, "http://ltz.example"], { encoding: "utf8" });
  assert.equal(bad.status, 1);
  assert.match(bad.stderr, /Hata:/);

  const help = spawnSync(process.execPath, [script, "--help"], { encoding: "utf8" });
  assert.equal(help.status, 0);
  assert.match(help.stdout, /Kullanım/);
});
