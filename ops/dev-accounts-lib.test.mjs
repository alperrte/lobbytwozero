import assert from "node:assert/strict";
import test from "node:test";
import {
  DEV_ACCOUNTS,
  assertDevProfile,
  assertDevTarget,
  base32Decode,
  emailFor,
  extractCode,
  hashPassword,
  parseDevEnv,
  sqlLiteral,
  temporaryPassword,
  totp,
  validateConfig
} from "./dev-accounts-lib.mjs";

const okTarget = {
  gatewayUrl: "http://localhost:5080",
  mailpitUrl: "http://localhost:8025",
  container: "ltz-dev-postgres-1",
  nodeEnv: undefined
};

test("hesaplar: kullanıcı adı Auth kuralına uyar, roller benzersiz", () => {
  for (const account of DEV_ACCOUNTS) assert.match(account.local, /^[a-z0-9_]{3,20}$/);
  assert.equal(new Set(DEV_ACCOUNTS.map((a) => a.role)).size, DEV_ACCOUNTS.length);
  assert.deepEqual(
    DEV_ACCOUNTS.filter((a) => a.mfa).map((a) => a.role).sort(),
    ["ADMIN", "MODERATOR"]
  );
  assert.equal(emailFor(DEV_ACCOUNTS[0], "ltz.com"), "user@ltz.com");
});

test("güvenlik kapısı: yerel hedefe izin verir", () => {
  assert.doesNotThrow(() => assertDevTarget(okTarget));
  assert.doesNotThrow(() => assertDevTarget({ ...okTarget, gatewayUrl: "http://127.0.0.1:5080" }));
});

test("güvenlik kapısı: uzak adres, yanlış kapsayıcı ve production reddedilir", () => {
  assert.throws(() => assertDevTarget({ ...okTarget, gatewayUrl: "https://api.ltz.example" }), /yerel olmalı/);
  assert.throws(() => assertDevTarget({ ...okTarget, mailpitUrl: "http://10.0.0.5:8025" }), /yerel olmalı/);
  assert.throws(() => assertDevTarget({ ...okTarget, container: "prod-postgres-1" }), /ltz-dev-/);
  assert.throws(() => assertDevTarget({ ...okTarget, nodeEnv: "production" }), /production/);
  assert.throws(() => assertDevTarget({ ...okTarget, gatewayUrl: "bozuk" }), /geçerli bir adres değil/);
  assert.throws(() => assertDevTarget({ ...okTarget, gatewayUrl: "http://localhost.evil.example" }), /yerel olmalı/);
});

test("profil kapısı: yalnız dev/test; prod, karışık ve okunamayan profil reddedilir", () => {
  assert.doesNotThrow(() => assertDevProfile("dev"));
  assert.doesNotThrow(() => assertDevProfile("dev,test"));
  assert.doesNotThrow(() => assertDevProfile(" DEV "));
  assert.throws(() => assertDevProfile("prod"), /dev\/test değil/);
  assert.throws(() => assertDevProfile("dev,prod"), /prod/);
  assert.throws(() => assertDevProfile("staging"), /staging/);
  assert.throws(() => assertDevProfile(""), /okunamadı/);
  assert.throws(() => assertDevProfile(undefined), /okunamadı/);
});

test("yapılandırma: parola zorunlu, alan adı geçerli olmalı", () => {
  assert.doesNotThrow(() => validateConfig({ password: "ornek-parola", domain: "ltz.com" }));
  assert.throws(() => validateConfig({ password: "", domain: "ltz.com" }), /DEV_ACCOUNTS_PASSWORD/);
  assert.throws(() => validateConfig({ password: undefined, domain: "ltz.com" }), /DEV_ACCOUNTS_PASSWORD/);
  assert.throws(() => validateConfig({ password: "x", domain: "ltz.com'; drop table" }), /alan adı/);
  assert.throws(() => validateConfig({ password: "x".repeat(129), domain: "ltz.com" }), /128/);
});

test("TOTP: RFC 6238 test vektörü (SHA1, t=59 → 287082)", () => {
  const secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // "12345678901234567890"
  assert.equal(totp(secret, 59_000), "287082");
  assert.equal(totp(secret, 1_111_111_109_000), "081804");
  assert.equal(totp(secret, 1_234_567_890_000), "005924");
});

test("Base32 çözümü ve geçersiz karakter", () => {
  assert.equal(base32Decode("MZXW6===").toString(), "foo");
  assert.throws(() => base32Decode("MZ!W6"), /Base32/);
});

test("parola özeti: Spring biçimi, her seferinde farklı tuz, düz parola içermez", () => {
  const first = hashPassword("ornek-parola");
  const second = hashPassword("ornek-parola");
  assert.match(first, /^\{argon2\}\$argon2id\$v=19\$m=19456,t=2,p=1\$[A-Za-z0-9+/]{22}\$[A-Za-z0-9+/]{43}$/);
  assert.notEqual(first, second);
  assert.ok(!first.includes("ornek-parola"));
});

test("geçici parola: en az 15 karakter ve her seferinde farklı", () => {
  const a = temporaryPassword();
  assert.ok(a.length >= 15);
  assert.notEqual(a, temporaryPassword());
});

test("SQL değeri tek tırnağı kaçırır", () => {
  assert.equal(sqlLiteral("o'brien"), "'o''brien'");
});

test("doğrulama kodu çıkarımı", () => {
  assert.equal(extractCode("Kodun: 483920 (10 dk)"), "483920");
  assert.equal(extractCode("sipariş 12345678 numarası"), null);
  assert.equal(extractCode(undefined), null);
});

test("dev env ayrıştırıcı: yalnız DEV_ACCOUNTS_* okunur, sırlar döndürülmez", () => {
  const text = [
    "# yorum",
    "AUTH_JWT_PRIVATE_KEY=cok-gizli",
    "DEV_ACCOUNTS_PASSWORD=parola-1",
    'DEV_ACCOUNTS_DOMAIN="ltz.test"',
    "DEV_ACCOUNTS_OTHER=yok-sayilir",
    "#DEV_ACCOUNTS_PASSWORD=yorumda",
    "BOS="
  ].join("\r\n");
  assert.deepEqual(parseDevEnv(text), { DEV_ACCOUNTS_PASSWORD: "parola-1", DEV_ACCOUNTS_DOMAIN: "ltz.test" });
  assert.deepEqual(parseDevEnv("DEV_ACCOUNTS_PASSWORD="), {});
  assert.deepEqual(parseDevEnv(undefined), {});
});
