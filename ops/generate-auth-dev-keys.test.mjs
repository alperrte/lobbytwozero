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
  assert.deepEqual(imports, ["./auth-env-rules.mjs", "node:crypto", "node:fs", "node:path", "node:url"]);
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
    // Var olan HMAC değeri geçersiz bir yer tutucu: üzerine YAZILMAZ ama sondaki kontrol bunu hata olarak bildirir.
    assert.equal(first.status, 1, first.stderr);
    assert.match(first.stdout, /\[FAIL\] AUTH_OTP_HMAC_KEY/);
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
    assert.equal(second.status, 1);
    assert.equal(readFileSync(a, "utf8"), textA);
    assert.equal(readFileSync(b, "utf8"), textB);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("AUTH_EVENT_TARGETS satır yoksa veya boşsa user yazılır; dolu listeye dokunulmaz, user yoksa uyarılır", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-targets-"));
  try {
    const missing = join(dir, "missing.env");
    const blank = join(dir, "blank.env");
    const custom = join(dir, "custom.env");
    const already = join(dir, "already.env");
    writeFileSync(missing, "OTHER=1\n");
    writeFileSync(blank, "AUTH_EVENT_TARGETS=\r\n");
    writeFileSync(custom, "AUTH_EVENT_TARGETS=community\n");
    writeFileSync(already, "AUTH_EVENT_TARGETS=community,user\n");
    const get = (file) => readFileSync(file, "utf8").match(/^AUTH_EVENT_TARGETS=(.*?)\r?$/m)?.[1];

    // Aynı çalıştırmadaki dosyalar aynı değeri paylaşır (betiğin sözleşmesi); bu yüzden gruplar ayrı çalıştırılır.
    const empty = spawnSync(process.execPath, [script, missing, blank], { encoding: "utf8" });
    assert.equal(empty.status, 0, empty.stderr);
    assert.equal(get(missing), "user");
    assert.equal(get(blank), "user");

    const filled = spawnSync(process.execPath, [script, custom, already], { encoding: "utf8" });
    assert.equal(filled.status, 0, filled.stderr);
    assert.equal(get(custom), "community", "dolu liste ezilmemeli");
    assert.equal(get(already), "community,user");
    assert.match(filled.stdout, /custom.env: .*uyarı -> AUTH_EVENT_TARGETS/);
    assert.ok(!/already.env: .*uyarı/.test(filled.stdout), "user içeren listede uyarı olmamalı");

    const again = spawnSync(process.execPath, [script, missing, blank], { encoding: "utf8" });
    assert.equal(get(missing), "user");
    assert.ok(!/dolduruldu -> AUTH_EVENT_TARGETS/.test(again.stdout), "ikinci çalıştırmada değişiklik olmamalı");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("Mail anahtarı üretilir; SMTP satırları yoksa boş eklenir, var olan (dolu/boş) satıra dokunulmaz", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-mail-"));
  try {
    const fresh = join(dir, "fresh.env");
    const custom = join(dir, "custom.env");
    writeFileSync(fresh, "OTHER=1\r\n");
    writeFileSync(custom, "MAIL_SMTP_HOST=smtp.example.test\nMAIL_SMTP_PORT=\nMAIL_PAYLOAD_ENCRYPTION_KEY=dolu-anahtar\n");
    const get = (text, name) => text.match(new RegExp(`^${name}=(.*?)\\r?$`, "m"))?.[1];
    const smtpKeys = ["MAIL_SMTP_HOST", "MAIL_SMTP_PORT", "MAIL_SMTP_USERNAME", "MAIL_SMTP_PASSWORD",
      "MAIL_SMTP_SECURITY", "MAIL_FROM_ADDRESS", "MAIL_ALLOWED_LINK_HOSTS"];

    // Betik, bir dosyada dolu olan anahtarı aynı çalıştırmadaki tüm dosyalara kopyalar; bu yüzden ayrı çalıştırılır.
    const run = spawnSync(process.execPath, [script, fresh], { encoding: "utf8" });
    assert.equal(run.status, 0, run.stderr);
    const customRun = spawnSync(process.execPath, [script, custom], { encoding: "utf8" });
    assert.equal(customRun.status, 0, customRun.stderr);
    const freshText = readFileSync(fresh, "utf8");
    const customText = readFileSync(custom, "utf8");

    assert.equal(Buffer.from(get(freshText, "MAIL_PAYLOAD_ENCRYPTION_KEY"), "base64").length, 32);
    assert.ok(!run.stdout.includes(get(freshText, "MAIL_PAYLOAD_ENCRYPTION_KEY")), "anahtar çıktıya sızdı");
    for (const name of smtpKeys) {
      assert.equal(get(freshText, name), "", `${name} boş eklenmeli`);
    }
    assert.equal(get(customText, "MAIL_SMTP_HOST"), "smtp.example.test", "dolu satır ezilmemeli");
    assert.equal(get(customText, "MAIL_SMTP_PORT"), "", "boş satır doldurulmamalı ve çoğaltmamalı");
    assert.equal(get(customText, "MAIL_PAYLOAD_ENCRYPTION_KEY"), "dolu-anahtar");
    assert.equal(customText.match(/^MAIL_SMTP_PORT=/gm).length, 1);

    const again = spawnSync(process.execPath, [script, fresh], { encoding: "utf8" });
    const againCustom = spawnSync(process.execPath, [script, custom], { encoding: "utf8" });
    assert.equal(readFileSync(fresh, "utf8"), freshText);
    assert.equal(readFileSync(custom, "utf8"), customText);
    assert.ok(!/boş eklendi/.test(again.stdout + againCustom.stdout), "ikinci çalıştırmada değişiklik olmamalı");
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
    assert.match(readFileSync(missing, "utf8"), /^JWT_ACCESS_TTL_SECONDS=60\r?$/m, "eksik satır .env.example'dan (60) eklenir");

    const again = spawnSync(process.execPath, [script, stale], { encoding: "utf8" });
    assert.ok(readFileSync(stale, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=60\r\n"));
    assert.ok(!/güncellendi/.test(again.stdout), "ikinci çalıştırmada değişiklik olmamalı");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// ---- Anahtar rotasyonu (S9) ----

const getLine = (body, name) => body.match(new RegExp(`^${name}=(.*?)\r?$`, "m"))?.[1];

test("--rotate-totp güncel anahtarı önceki listesine taşır, sürümü artırır, yeni anahtar üretir ve çıktıya yazmaz", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-rotate-"));
  try {
    const file = join(dir, "a.env");
    const first = Buffer.alloc(32, 1).toString("base64");
    writeFileSync(file, `KEEP=1\r\nAUTH_TOTP_ENCRYPTION_KEY=${first}\r\n`);

    const run1 = spawnSync(process.execPath, [script, "--rotate-totp", file], { encoding: "utf8" });
    assert.equal(run1.status, 0, run1.stderr);
    const body1 = readFileSync(file, "utf8");
    const second = getLine(body1, "AUTH_TOTP_ENCRYPTION_KEY");
    assert.notEqual(second, first);
    assert.equal(Buffer.from(second, "base64").length, 32);
    assert.equal(getLine(body1, "AUTH_TOTP_ENCRYPTION_KEY_VERSION"), "2");
    assert.equal(getLine(body1, "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS"), `1=${first}`);
    assert.equal(getLine(body1, "KEEP"), "1");
    assert.ok(body1.includes("\r\n") && !/[^\r]\n/.test(body1), "satır sonları korunmalı");
    for (const secret of [first, second]) assert.ok(!run1.stdout.includes(secret), "anahtar çıktıya sızdı");

    const run2 = spawnSync(process.execPath, [script, "--rotate-totp", file], { encoding: "utf8" });
    assert.equal(run2.status, 0, run2.stderr);
    const body2 = readFileSync(file, "utf8");
    assert.equal(getLine(body2, "AUTH_TOTP_ENCRYPTION_KEY_VERSION"), "3");
    assert.equal(getLine(body2, "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS"), `1=${first},2=${second}`);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("--rotate-hmac önceki anahtarı doldurur; dolu önceki anahtarın üzerine yalnız --force ile yazar", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-rotate-"));
  try {
    const file = join(dir, "a.env");
    const first = Buffer.alloc(32, 7).toString("base64");
    writeFileSync(file, `AUTH_OTP_HMAC_KEY=${first}\nAUTH_OTP_HMAC_PREVIOUS_KEY=\n`);

    const run1 = spawnSync(process.execPath, [script, "--rotate-hmac", file], { encoding: "utf8" });
    assert.equal(run1.status, 0, run1.stderr);
    const body1 = readFileSync(file, "utf8");
    const second = getLine(body1, "AUTH_OTP_HMAC_KEY");
    assert.notEqual(second, first);
    assert.equal(getLine(body1, "AUTH_OTP_HMAC_PREVIOUS_KEY"), first);
    for (const secret of [first, second]) assert.ok(!run1.stdout.includes(secret), "anahtar çıktıya sızdı");

    // Önceki anahtar dolu: eski kurtarma kodlarını kaybettirmemek için ikinci döndürme reddedilir.
    const refused = spawnSync(process.execPath, [script, "--rotate-hmac", file], { encoding: "utf8" });
    assert.notEqual(refused.status, 0);
    assert.equal(readFileSync(file, "utf8"), body1, "reddedilen döndürme dosyayı değiştirmemeli");

    const forced = spawnSync(process.execPath, [script, "--rotate-hmac", "--force", file], { encoding: "utf8" });
    assert.equal(forced.status, 0, forced.stderr);
    assert.equal(getLine(readFileSync(file, "utf8"), "AUTH_OTP_HMAC_PREVIOUS_KEY"), second);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("rotasyon, boş anahtarlı dosyada hata verir ve dosyayı değiştirmez", () => {
  const dir = mkdtempSync(join(tmpdir(), "ltz-rotate-"));
  try {
    const file = join(dir, "a.env");
    writeFileSync(file, "AUTH_TOTP_ENCRYPTION_KEY=\n");
    const run = spawnSync(process.execPath, [script, "--rotate-totp", file], { encoding: "utf8" });
    assert.notEqual(run.status, 0);
    assert.equal(readFileSync(file, "utf8"), "AUTH_TOTP_ENCRYPTION_KEY=\n");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// ---- Tek komutla tamamlama: .env.example eşitleme, dosya oluşturma, sondaki kontrol ----

import { copyFileSync, existsSync, mkdirSync } from "node:fs";

/** Geçici bir "depo" kurar (ops/ betikleri + verilen .env.example); gerçek .env dosyalarına asla dokunulmaz. */
function fakeRepo(exampleBody) {
  const dir = mkdtempSync(join(tmpdir(), "ltz-repo-"));
  mkdirSync(join(dir, "ops"));
  const opsDir = dirname(script);
  for (const file of ["generate-auth-dev-keys.mjs", "auth-env-rules.mjs"]) copyFileSync(join(opsDir, file), join(dir, "ops", file));
  writeFileSync(join(dir, ".env.example"), exampleBody);
  const run = (...args) => spawnSync(process.execPath, [join(dir, "ops", "generate-auth-dev-keys.mjs"), ...args], { encoding: "utf8" });
  return { dir, run, cleanup: () => rmSync(dir, { recursive: true, force: true }) };
}

const exampleBody = [
  "# Auth anahtarları",
  "AUTH_JWT_PRIVATE_KEY=",
  "AUTH_OTP_HMAC_KEY=",
  "AUTH_TOTP_ENCRYPTION_KEY=",
  "",
  "# Mailpit kodları gösterir",
  "COMPOSE_PROFILES=dev-mail",
  "AUTH_EVENT_TARGETS=user",
  "",
  "# Yeni ayar: açıklaması da gelir",
  "NEW_SETTING=varsayilan",
  "DB_PASSWORD=ornek-parola",
  "",
].join("\n");

test("hiç ENV dosyası yoksa .env.dev, .env.example'dan oluşturulur; anahtarlar üretilir; sondaki kontrol geçer", () => {
  const repo = fakeRepo(exampleBody);
  try {
    const run = repo.run();
    assert.equal(run.status, 0, run.stdout + run.stderr);
    assert.match(run.stdout, /\.env\.dev oluşturuldu/);
    assert.match(run.stdout, /Hata yok: Auth ayarları açılış doğrulamasından geçer/);
    const body = readFileSync(join(repo.dir, ".env.dev"), "utf8");
    const get = (name) => body.match(new RegExp(`^${name}=(.*?)\r?$`, "m"))?.[1];
    assert.equal(Buffer.from(get("AUTH_TOTP_ENCRYPTION_KEY"), "base64").length, 32);
    assert.equal(get("COMPOSE_PROFILES"), "dev-mail");
    assert.equal(get("DB_PASSWORD"), "ornek-parola");
    assert.ok(body.includes("# Mailpit kodları gösterir"), "açıklamalar korunmalı");
    for (const name of ["AUTH_JWT_PRIVATE_KEY", "AUTH_OTP_HMAC_KEY", "AUTH_TOTP_ENCRYPTION_KEY"]) {
      assert.ok(!run.stdout.includes(get(name)), `${name} değeri çıktıya sızdı`);
    }
    assert.ok(!existsSync(join(repo.dir, ".env")), "yalnız .env.dev oluşturulur");
  } finally {
    repo.cleanup();
  }
});

test("eksik ayarlar açıklamalarıyla eklenir; var olana (örnekten farklı olsa da) dokunulmaz; ikinci çalıştırma hiçbir şeyi değiştirmez", () => {
  const repo = fakeRepo(exampleBody);
  try {
    const file = join(repo.dir, ".env.dev");
    writeFileSync(file, "KEEP=1\r\nDB_PASSWORD=benim-parolam\r\nCOMPOSE_PROFILES=\r\n");

    const first = repo.run();
    assert.equal(first.status, 0, first.stdout + first.stderr);
    const body = readFileSync(file, "utf8");
    assert.ok(body.startsWith("KEEP=1\r\nDB_PASSWORD=benim-parolam\r\nCOMPOSE_PROFILES=\r\n"), "mevcut satırlar ve satır sonları aynen korunmalı");
    assert.match(body, /# --- \.env\.example ile eşitlendi: eksik ayarlar eklendi ---/);
    assert.match(body, /# Yeni ayar: açıklaması da gelir\r\nNEW_SETTING=varsayilan\r\n/);
    assert.match(first.stdout, /\.env\.example'dan eklendi/);
    assert.ok(!/\r?\nDB_PASSWORD=ornek-parola/.test(body), "var olan ayar örnek değerle tekrar eklenmemeli");
    assert.ok(!/[^\r]\n/.test(body), "CRLF dosyada LF satır sonu kalmamalı");

    const second = repo.run();
    assert.equal(second.status, 0, second.stdout + second.stderr);
    assert.equal(readFileSync(file, "utf8"), body, "ikinci çalıştırmada dosya değişmemeli");
    assert.match(second.stdout, /eksik ayar yok/);
  } finally {
    repo.cleanup();
  }
});

test("sondaki kontrol hata bulursa çıkış kodu 1 olur ama dosya yine de tamamlanır", () => {
  const repo = fakeRepo(exampleBody);
  try {
    const file = join(repo.dir, ".env.dev");
    writeFileSync(file, `${exampleBody}\nAUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=bozuk\n`);
    const run = repo.run();
    assert.equal(run.status, 1);
    assert.match(run.stdout, /\[FAIL\] AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS/);
    assert.match(run.stdout, /Auth bu ayarlarla AÇILMAZ/);
    assert.equal(readFileSync(file, "utf8").match(/^AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS=(.*)$/m)[1], "bozuk", "bozuk değer sessizce düzeltilmez");
  } finally {
    repo.cleanup();
  }
});
