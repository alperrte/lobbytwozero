import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const script = join(here, "auth-env-check.mjs");
const generator = join(here, "generate-auth-dev-keys.mjs");
const source = readFileSync(script, "utf8");

const key32 = (seed) => Buffer.alloc(32, seed).toString("base64");
const jwt = Buffer.alloc(1200, 9).toString("base64");

function run(body, extra = []) {
  const dir = mkdtempSync(join(tmpdir(), "ltz-envcheck-"));
  try {
    const file = join(dir, "a.env");
    writeFileSync(file, body);
    const result = spawnSync(process.execPath, [script, file, ...extra], { encoding: "utf8" });
    return { ...result, output: `${result.stdout}${result.stderr}` };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

const good = (more = "") =>
  `AUTH_OTP_HMAC_KEY=${key32(1)}\nAUTH_TOTP_ENCRYPTION_KEY=${key32(2)}\nAUTH_JWT_PRIVATE_KEY=${jwt}\n`
  + `COMPOSE_PROFILES=dev-mail\nAUTH_EVENT_TARGETS=user\n${more}`;

// Güvenlik bekçisi: araç yalnız yerel dosya okur; ağ/süreç/dinamik kod yok ve dosyaya yazmaz.
test("script yalnız izinli modülleri içe aktarır, yazma/ağ/süreç/dinamik kod kullanmaz", () => {
  const imports = [...source.matchAll(/from\s+"([^"]+)"/g)].map((match) => match[1]).sort();
  assert.deepEqual(imports, ["./auth-env-rules.mjs", "node:fs", "node:path", "node:url"]);
  for (const forbidden of [
    "writeFileSync", "appendFileSync", "child_process", "node:http", "node:https", "node:net", "node:crypto",
    "fetch(", "eval(", "new Function", "process.env", "import(",
  ]) {
    assert.ok(!source.includes(forbidden), `izin verilmeyen kullanım: ${forbidden}`);
  }
});

test("çıktıya değer taşıyan değişkenler girmez", () => {
  const logged = [...source.matchAll(/console\.(log|error|warn|info)\(([^;]*)\);/g)].map((match) => match[2]).join("\n");
  assert.ok(!/\b(settings|hmac|totp|jwt|hmacPrevious|previous|entryKey|parsed|body)\b/.test(logged), "console çıktısı değer içeriyor");
});

test("geçerli ayarlar hata vermez ve anahtar değerleri çıktıya sızmaz", () => {
  const result = run(good());
  assert.equal(result.status, 0, result.output);
  for (const name of ["AUTH_OTP_HMAC_KEY", "AUTH_TOTP_ENCRYPTION_KEY", "AUTH_JWT_PRIVATE_KEY"]) {
    assert.match(result.output, new RegExp(`\\[OK  \\] ${name}`));
  }
  for (const secret of [key32(1), key32(2), jwt]) assert.ok(!result.output.includes(secret), "anahtar çıktıya sızdı");
});

test("boş, kısa veya Base64 olmayan anahtarlar hata verir (Auth açılmaz) ve kısa değer yazılmaz", () => {
  const shortKey = Buffer.alloc(16, 5).toString("base64");
  const result = run(`AUTH_OTP_HMAC_KEY=${shortKey}\nAUTH_TOTP_ENCRYPTION_KEY=\nAUTH_JWT_PRIVATE_KEY=@@@@\n`);
  assert.equal(result.status, 1);
  for (const name of ["AUTH_OTP_HMAC_KEY", "AUTH_TOTP_ENCRYPTION_KEY", "AUTH_JWT_PRIVATE_KEY"]) {
    assert.match(result.output, new RegExp(`\\[FAIL\\] ${name}`));
  }
  assert.ok(!result.output.includes(shortKey), "kısa anahtar çıktıya sızdı");
  assert.match(result.output, /Auth bu ayarlarla AÇILMAZ/);
});

test("TOTP anahtarı tam 32 bayt olmalı; 48 bayt reddedilir", () => {
  const result = run(good().replace(key32(2), Buffer.alloc(48, 3).toString("base64")));
  assert.equal(result.status, 1);
  assert.match(result.output, /\[FAIL\] AUTH_TOTP_ENCRYPTION_KEY/);
});

test("rotasyon: geçerli eski anahtar listesi INFO verir; üreticinin ürettiği çıktı kontrolden geçer", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-envcheck-"));
  try {
    const file = join(dir, "a.env");
    writeFileSync(file, good());
    assert.equal(spawnSync(process.execPath, [generator, "--rotate-totp", "--rotate-hmac", file], { encoding: "utf8" }).status, 0);
    const result = spawnSync(process.execPath, [script, file], { encoding: "utf8" });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.match(result.stdout, /\[INFO\] AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS: rotasyon açık \(1 eski sürüm\)/);
    assert.match(result.stdout, /\[INFO\] AUTH_OTP_HMAC_PREVIOUS_KEY: rotasyon açık/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("bozuk eski anahtar listeleri Auth'un açılış doğrulamasıyla aynı şekilde hata verir", () => {
  const bad = [
    `AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=1=${Buffer.alloc(16, 4).toString("base64")}`, // kısa anahtar
    `AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=x=${key32(4)}`, // sürüm sayı değil
    `AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=1=${key32(4)},1=${key32(5)}`, // yinelenen sürüm
    `AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=1=${key32(4)}\nAUTH_TOTP_ENCRYPTION_KEY_VERSION=1`, // güncel sürümle çakışıyor
    `AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=${key32(4)}`, // sürüm yok
    "AUTH_TOTP_ENCRYPTION_KEY_VERSION=0",
    "AUTH_OTP_HMAC_PREVIOUS_KEY=kisa",
  ];
  for (const more of bad) {
    const result = run(good(more));
    assert.equal(result.status, 1, `hata beklenirdi: ${more.split("\n")[0].slice(0, 40)}`);
    assert.ok(!result.output.includes(key32(4)) && !result.output.includes(key32(5)), "anahtar çıktıya sızdı");
  }
});

test("uyarılar: sürüm eski sürümden küçük, eski liste olmadan sürüm 1'den farklı, dosyalar arası fark", () => {
  const lower = run(good(`AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=2=${key32(4)}\nAUTH_TOTP_ENCRYPTION_KEY_VERSION=1`));
  assert.equal(lower.status, 0, lower.output);
  assert.match(lower.output, /\[WARN\] AUTH_TOTP_ENCRYPTION_KEY_VERSION: güncel sürüm eski sürümlerden küçük/);

  const orphan = run(good("AUTH_TOTP_ENCRYPTION_KEY_VERSION=2"));
  assert.equal(orphan.status, 0, orphan.output);
  assert.match(orphan.output, /\[WARN\] AUTH_TOTP_ENCRYPTION_KEY_VERSION: 1'den farklı ama eski anahtar listesi boş/);

  const dir = mkdtempSync(join(tmpdir(), "ltz-envcheck-"));
  try {
    const a = join(dir, "a.env");
    const b = join(dir, "b.env");
    writeFileSync(a, good());
    writeFileSync(b, good().replace(key32(1), key32(8)));
    const result = spawnSync(process.execPath, [script, a, b], { encoding: "utf8" });
    assert.equal(result.status, 0);
    assert.match(result.stdout, /\[WARN\] AUTH_OTP_HMAC_KEY: dosyalarda farklı/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("prod profili yalnız mail-service adaptörüyle açılır; dev'de Mailpit ve olay hedefi uyarısı verilir", () => {
  const prod = run(good("SPRING_PROFILES_ACTIVE=prod\nAUTH_MAIL_ADAPTER=dev-smtp"));
  assert.equal(prod.status, 1);
  assert.match(prod.output, /\[FAIL\] AUTH_MAIL_ADAPTER/);

  const prodOk = run(good("SPRING_PROFILES_ACTIVE=prod\nAUTH_MAIL_ADAPTER=mail-service"));
  assert.equal(prodOk.status, 0, prodOk.output);

  const dev = run(good().replace("COMPOSE_PROFILES=dev-mail\n", "").replace("AUTH_EVENT_TARGETS=user\n", ""));
  assert.equal(dev.status, 0, dev.output);
  assert.match(dev.output, /\[WARN\] COMPOSE_PROFILES: dev-mail yok/);
  assert.match(dev.output, /\[WARN\] AUTH_EVENT_TARGETS/);
});

test("dosya yoksa hata verir", () => {
  const result = spawnSync(process.execPath, [script, join(tmpdir(), "yok-boyle-dosya.env")], { encoding: "utf8" });
  assert.equal(result.status, 1);
});

test("kural modülü saftır: içe aktarma, dosya/ağ/süreç erişimi ve ortam okuması yok", () => {
  const rules = readFileSync(join(here, "auth-env-rules.mjs"), "utf8");
  assert.ok(!/^\s*import\s/m.test(rules), "kural modülü içe aktarma yapmamalı");
  for (const forbidden of ["node:", "require(", "fetch(", "process.", "console."]) {
    assert.ok(!rules.includes(forbidden), `izin verilmeyen kullanım: ${forbidden}`);
  }
});
