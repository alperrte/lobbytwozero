import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const script = join(dirname(fileURLToPath(import.meta.url)), "generate-auth-dev-keys.mjs");
const source = readFileSync(script, "utf8");

// Güvenlik bekçisi: bu script yalnız yerel dosya okuyup yazar ve anahtar üretir. Ağ, süreç başlatma veya
// dinamik kod yürütme eklenirse bu test kırılır ve değişiklik gözden geçirmede açıkça görünür.
test("script yalnız izinli modülleri içe aktarır, ağ/süreç/dinamik kod kullanmaz", () => {
  const imports = [...source.matchAll(/from\s+"([^"]+)"/g)].map((match) => match[1]).sort();
  assert.deepEqual(imports, ["node:crypto", "node:fs", "node:path", "node:url"]);
  for (const forbidden of [
    "child_process", "node:http", "node:https", "node:net", "node:dns", "node:tls", "node:worker_threads",
    "fetch(", "XMLHttpRequest", "WebSocket", "eval(", "new Function", "process.env", "import(",
  ]) {
    assert.ok(!source.includes(forbidden), `izin verilmeyen kullanım: ${forbidden}`);
  }
});

test("script anahtar değerlerini ekrana yazmaz", () => {
  const logged = [...source.matchAll(/console\.(log|error|warn|info)\(([^;]*)\);/g)].map((match) => match[2]).join("\n");
  assert.ok(!/\b(value|values|text|match)\b/.test(logged), "console çıktısı değer/metin içeriyor");
});

test("eksik anahtarları üretir, dolu olanı ezmez, dosyalarda aynı değeri kullanır ve çıktıda göstermez", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-keys-"));
  try {
    const a = join(dir, "a.env");
    const b = join(dir, "b.env");
    writeFileSync(a, "KEEP=1\r\nAUTH_OTP_HMAC_KEY=dolu-deger-degismemeli\r\nAUTH_JWT_PRIVATE_KEY=\r\n");
    writeFileSync(b, "OTHER=2\r\n");

    const first = spawnSync(process.execPath, [script, a, b], { encoding: "utf8" });
    assert.equal(first.status, 0, first.stderr);
    const textA = readFileSync(a, "utf8");
    const textB = readFileSync(b, "utf8");
    const get = (text, name) => text.match(new RegExp(`^${name}=(.*?)\\r?$`, "m"))?.[1];

    assert.equal(get(textA, "AUTH_OTP_HMAC_KEY"), "dolu-deger-degismemeli");
    assert.equal(get(textB, "AUTH_OTP_HMAC_KEY"), "dolu-deger-degismemeli");
    for (const name of ["AUTH_JWT_PRIVATE_KEY", "AUTH_TOTP_ENCRYPTION_KEY"]) {
      assert.ok(get(textA, name) && get(textA, name) === get(textB, name), `${name} iki dosyada aynı olmalı`);
      assert.ok(!first.stdout.includes(get(textA, name)), `${name} değeri çıktıya sızdı`);
    }
    assert.equal(Buffer.from(get(textA, "AUTH_TOTP_ENCRYPTION_KEY"), "base64").length, 32);
    assert.ok(textA.startsWith("KEEP=1\r\n"), "mevcut satırlar ve satır sonları korunmalı");

    const second = spawnSync(process.execPath, [script, a, b], { encoding: "utf8" });
    assert.equal(second.status, 0);
    assert.equal(readFileSync(a, "utf8"), textA);
    assert.equal(readFileSync(b, "utf8"), textB);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("eski varsayılan JWT_ACCESS_TTL_SECONDS=300 değeri 60'a çekilir; özelleştirilmiş değere ve eksik satıra dokunulmaz", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-ttl-"));
  try {
    const stale = join(dir, "stale.env");
    const custom = join(dir, "custom.env");
    const missing = join(dir, "missing.env");
    writeFileSync(stale, "JWT_ACCESS_TTL_SECONDS=300\r\nJWT_ISSUER=ltz-auth\r\n");
    writeFileSync(custom, "JWT_ACCESS_TTL_SECONDS=120\n");
    writeFileSync(missing, "OTHER=1\n");

    const run = spawnSync(process.execPath, [script, stale, custom, missing], { encoding: "utf8" });
    assert.equal(run.status, 0, run.stderr);
    assert.ok(readFileSync(stale, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=60\r\nJWT_ISSUER=ltz-auth\r\n"));
    assert.match(run.stdout, /güncellendi -> JWT_ACCESS_TTL_SECONDS \(300 -> 60\)/);
    assert.ok(readFileSync(custom, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=120\n"));
    assert.ok(!/JWT_ACCESS_TTL_SECONDS/.test(readFileSync(missing, "utf8")), "eksik satır eklenmemeli (Compose varsayılanı 60)");

    const again = spawnSync(process.execPath, [script, stale], { encoding: "utf8" });
    assert.ok(readFileSync(stale, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=60\r\n"));
    assert.ok(!/güncellendi/.test(again.stdout), "ikinci çalıştırmada değişiklik olmamalı");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
