import assert from "node:assert/strict";
import test from "node:test";
import {
  DEV_ACCOUNTS,
  assertDevProfile,
  assertNodeSupported,
  assertDevTarget,
  assertUuid,
  base32Decode,
  emailFor,
  hashPassword,
  isUndeliverableDomain,
  parseDevEnv,
  parseRequiredRoles,
  sqlLiteral,
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

test("SQL değeri tek tırnağı kaçırır", () => {
  assert.equal(sqlLiteral("o'brien"), "'o''brien'");
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

test("UUID doğrulaması: geçerli biçim geçer, SQL enjeksiyonu ve boş değer reddedilir", () => {
  assert.equal(assertUuid("6ed1a2d9-dc5d-4c22-bd20-4ef821264d9b"), "6ed1a2d9-dc5d-4c22-bd20-4ef821264d9b");
  assert.throws(() => assertUuid("'; drop table accounts; --"), /UUID/);
  assert.throws(() => assertUuid(""), /UUID/);
  assert.throws(() => assertUuid(undefined), /UUID/);
  assert.throws(() => assertUuid("6ED1A2D9-DC5D-4C22-BD20-4EF821264D9B"), /UUID/);
});

test("teslim edilemeyen alan adı: ayrılmış uzantılar ve example.* evet, gerçek alan adı hayır", () => {
  for (const domain of ["ltz.test", "x.example", "a.invalid", "b.localhost", "c.local", "example.com", "EXAMPLE.org", " ltz.TEST "]) {
    assert.equal(isUndeliverableDomain(domain), true, domain);
  }
  for (const domain of ["ltz.com", "gmail.com", "testltz.com", "ltz.test.com", "", undefined]) {
    assert.equal(isUndeliverableDomain(domain), false, String(domain));
  }
});

test("2FA zorunlu roller: tanımsız = varsayılan, boş = hiçbiri, liste ayrıştırılır", () => {
  assert.deepEqual(parseRequiredRoles(undefined), ["ADMIN", "MODERATOR"]);
  assert.deepEqual(parseRequiredRoles(""), []);
  assert.deepEqual(parseRequiredRoles("\n"), []);
  assert.deepEqual(parseRequiredRoles(" admin , moderator\n"), ["ADMIN", "MODERATOR"]);
  assert.deepEqual(parseRequiredRoles("ADMIN"), ["ADMIN"]);
});

test("Node sürüm kapısı: 24.7 ve üstü geçer, eskisi anlaşılır mesajla reddedilir", () => {
  for (const ok of ["24.7.0", "24.22.2", "26.13.0", "25.0.0"]) assert.doesNotThrow(() => assertNodeSupported(ok), ok);
  for (const bad of ["24.6.9", "22.12.0", "20.0.0", "18.19.1"]) assert.throws(() => assertNodeSupported(bad), /Node 24\.7 veya üstü/, bad);
});
