// ops/dev.mjs için TEK ortak test dosyası (node --test ops/dev.test.mjs).
// Her bölüm eski ayrı test dosyalarının karşılığıdır: dev-lib, mail-lib, accounts-lib, env-check, keys, site-url,
// e2e-* ve env-profil. Statik güvenlik bekçileri dev.mjs içindeki `// #region` bölümünü okur; böylece bir komuta
// eklenen ağ/süreç/dinamik kod kullanımı yalnız o komutun bölümünde yakalanır.
import { describe, test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";
import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { generateKeyPairSync, verify } from "node:crypto";
import { fileURLToPath } from "node:url";
import * as dev from "./dev.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const devPath = join(here, "dev.mjs");
const devSource = readFileSync(devPath, "utf8");

/** dev.mjs içindeki `// #region ad` … `// #endregion ad` arasını döndürür. */
function region(name) {
  const start = devSource.indexOf(`// #region ${name}\n`);
  const end = devSource.indexOf(`// #endregion ${name}\n`);
  assert.ok(start >= 0 && end > start, `dev.mjs içinde bölüm yok: ${name}`);
  return devSource.slice(start, end);
}

// #region dev-lib
describe("dev-lib", () => {
  const { CORE_SERVICES, decideMailStep, estimateMemoryMb, memoryVerdict, needsInstall, parseArgs, summaryLines, validateArgs } = dev;

  test("argüman ayrıştırma: komut verilmezse start, bayraklar ayrılır, mail argümanları iletilir", () => {
    assert.deepEqual(parseArgs([]), { command: "start", rest: [], flags: new Set(), withServices: [] });
    const parsed = parseArgs(["--no-mail", "start", "--no-frontend"]);
    assert.equal(parsed.command, "start");
    assert.deepEqual([...parsed.flags].sort(), ["--no-frontend", "--no-mail"]);
    assert.deepEqual(parseArgs(["mail", "on"]).rest, ["on"]);
  });

  test("argüman doğrulama: bilinmeyen komut ve bayrak reddedilir", () => {
    assert.doesNotThrow(() => validateArgs(parseArgs(["--dry-run", "--no-build", "--skip-keys", "--no-services"])));
    assert.throws(() => validateArgs(parseArgs(["calistir"])), /Bilinmeyen komut/);
    assert.throws(() => validateArgs(parseArgs(["--hizli"])), /Bilinmeyen seçenek/);
  });

  test("e-posta kararı: öncelik sırası", () => {
    const base = { skip: false, real: false, passwordSet: false, interactive: true, envPassword: undefined };
    assert.equal(decideMailStep({ ...base, skip: true, real: true, passwordSet: true }), "skip");
    assert.equal(decideMailStep({ ...base, real: true, passwordSet: true }), "already");
    assert.equal(decideMailStep({ ...base, envPassword: "x" }), "env");
    assert.equal(decideMailStep(base), "ask");
    assert.equal(decideMailStep({ ...base, interactive: false }), "mailpit");
  });

  test("e-posta kararı: şifresiz yarım kurulum 'already' sayılmaz (yeniden sorulur); etkileşimsizde değişken yoksa Mailpit", () => {
    const base = { skip: false, real: true, passwordSet: false, interactive: true, envPassword: undefined };
    assert.equal(decideMailStep(base), "ask");
    assert.equal(decideMailStep({ ...base, interactive: false }), "mailpit");
    assert.equal(decideMailStep({ ...base, interactive: false, envPassword: "x" }), "env");
  });

  test("npm ci kararı", () => {
    assert.equal(needsInstall({ modulesTime: null, lockTime: 100 }), true);
    assert.equal(needsInstall({ modulesTime: 200, lockTime: 100 }), false);
    assert.equal(needsInstall({ modulesTime: 100, lockTime: 200 }), true);
    assert.equal(needsInstall({ modulesTime: 100, lockTime: null }), false);
  });

  test("özet: beş hesap, parola değeri yok, e-posta kipi doğru", () => {
    const mailpit = summaryLines({ domain: "ltz.com", realMail: false, frontendRunning: true }).join("\n");
    for (const name of ["user", "creator", "developer", "moderator", "admin"]) assert.match(mailpit, new RegExp(`${name}@ltz\\.com`));
    assert.match(mailpit, /Mailpit/);
    assert.doesNotMatch(mailpit, /ltz1234/);
    assert.doesNotMatch(mailpit, /frontend: cd frontend/);
    const real = summaryLines({ domain: "ltz.test", realMail: true, frontendRunning: false }).join("\n");
    assert.match(real, /GERÇEK/);
    assert.match(real, /user@ltz\.test/);
    assert.match(real, /cd frontend && npx next dev/);
  });

  test("--with= servis listesi: virgülle ayrılır, bayrak sayılmaz, adı doğrulanır", () => {
    const parsed = parseArgs(["--with=game-service,library-service", "--no-mail", "--with=review-service"]);
    assert.deepEqual(parsed.withServices, ["game-service", "library-service", "review-service"]);
    assert.deepEqual([...parsed.flags], ["--no-mail"]);
    assert.doesNotThrow(() => validateArgs(parsed));
    assert.throws(() => validateArgs(parseArgs(["--with=Game Service"])), /Geçersiz servis adı/);
    assert.throws(() => validateArgs(parseArgs(["--with=a;rm -rf"])), /Geçersiz servis adı/);
    assert.doesNotThrow(() => validateArgs(parseArgs(["--all", "--force"])));
  });

  test("bellek tahmini ve karar: çekirdek 6 servis yeter, 20 servis 7,4 GB'a sığmaz", () => {
    const gb = 1024 ** 3;
    assert.equal(estimateMemoryMb(CORE_SERVICES), 200 + 30 + 4 * 350);
    assert.equal(estimateMemoryMb([...CORE_SERVICES, "auth-service"]), estimateMemoryMb(CORE_SERVICES));
    assert.equal(memoryVerdict({ services: CORE_SERVICES, dockerMemBytes: 7.4 * gb }).ok, true);
    const all = [...CORE_SERVICES, ...Array.from({ length: 14 }, (_, i) => `servis-${i}-service`)];
    const verdict = memoryVerdict({ services: all, dockerMemBytes: 7.4 * gb });
    assert.equal(verdict.ok, false);
    assert.ok(verdict.needMb > verdict.haveMb * 0.85);
    assert.equal(memoryVerdict({ services: all, dockerMemBytes: 16 * gb }).ok, true);
  });
});
// #endregion dev-lib

// #region mail-lib
describe("mail-lib", () => {
  const { applyEnvChanges, describeSmtp, normalizeAppPassword, smtpOffSettings, smtpOnSettings, validateGmailAddress } = dev;

  test("açma ayarları: Gmail sabitleri otomatik, yalnız adres ve şifre kişiye özel", () => {
    const settings = smtpOnSettings("ada@gmail.com", "abcdefghijklmnop");
    assert.equal(settings.MAIL_SMTP_HOST, "smtp.gmail.com");
    assert.equal(settings.MAIL_SMTP_PORT, "587");
    assert.equal(settings.MAIL_SMTP_SECURITY, "STARTTLS");
    assert.equal(settings.MAIL_SMTP_USERNAME, "ada@gmail.com");
    assert.equal(settings.MAIL_FROM_ADDRESS, "ada@gmail.com");
    assert.equal(settings.MAIL_SMTP_PASSWORD, "abcdefghijklmnop");
    assert.equal(settings.AUTH_MAIL_ADAPTER, "mail-service");
  });

  test("kapatma ayarları: SMTP boşalır, adaptör Mailpit'e döner", () => {
    const settings = smtpOffSettings();
    for (const [key, value] of Object.entries(settings)) {
      if (key !== "AUTH_MAIL_ADAPTER") assert.equal(value, "", key);
    }
    assert.equal(settings.AUTH_MAIL_ADAPTER, "dev-smtp");
  });

  test("Gmail adresi doğrulaması", () => {
    assert.equal(validateGmailAddress(" Ada.Test+x@Gmail.com "), "ada.test+x@gmail.com");
    for (const bad of ["", "ada@example.com", "ada@gmail.com.evil.test", "@gmail.com", "a b@gmail.com", undefined]) {
      assert.throws(() => validateGmailAddress(bad), /Gmail adresi/, String(bad));
    }
  });

  test("uygulama şifresi: boşluklar atılır, 16 harf şart; hata değeri sızdırmaz", () => {
    assert.equal(normalizeAppPassword("abcd efgh ijkl mnop"), "abcdefghijklmnop");
    assert.equal(normalizeAppPassword(" ABCD EFGH IJKL MNOP "), "abcdefghijklmnop");
    for (const bad of ["", "kisa", "abcd efgh ijkl mnop q", "abcd-efgh-ijkl-mnop", "1234567890123456"]) {
      assert.throws(
        () => normalizeAppPassword(bad),
        (error) => /16 harf/.test(error.message) && !error.message.includes(bad.trim() || "\u0000")
      );
    }
  });

  test("env güncelleme: var olan aktif satır değişir, yorum satırına dokunulmaz, eksik anahtar eklenir, diğer satırlar korunur", () => {
    const input = ["KEEP=1", "# MAIL_SMTP_HOST=eski", "MAIL_SMTP_HOST=", "DB_PASSWORD=benim", "MAIL_SMTP_PORT=25"].join("\n") + "\n";
    const output = applyEnvChanges(input, { MAIL_SMTP_HOST: "smtp.gmail.com", MAIL_SMTP_PORT: "587", AUTH_MAIL_ADAPTER: "mail-service" });
    assert.equal(
      output,
      ["KEEP=1", "# MAIL_SMTP_HOST=eski", "MAIL_SMTP_HOST=smtp.gmail.com", "DB_PASSWORD=benim", "MAIL_SMTP_PORT=587", "AUTH_MAIL_ADAPTER=mail-service"].join("\n") + "\n"
    );
  });

  test("env güncelleme: CRLF korunur, boş dosya ve sonu satır sonsuz dosya çalışır", () => {
    assert.equal(applyEnvChanges("A=1\r\nB=2\r\n", { B: "3" }), "A=1\r\nB=3\r\n");
    assert.equal(applyEnvChanges("", { A: "1" }), "A=1\n");
    assert.equal(applyEnvChanges("A=1", { A: "2" }), "A=2\n");
  });

  test("env güncelleme: değer satır sonu içeremez (satır enjeksiyonu engeli)", () => {
    assert.throws(() => applyEnvChanges("A=1\n", { A: "x\nEVIL=1" }), /satır sonu/);
  });

  test("aç sonra kapat: ilk haline anlamlı olarak döner ve tekrar çalıştırmak aynı sonucu verir", () => {
    const start = "KEEP=1\nMAIL_SMTP_HOST=\n";
    const on = applyEnvChanges(start, smtpOnSettings("ada@gmail.com", "abcdefghijklmnop"));
    assert.equal(applyEnvChanges(on, smtpOnSettings("ada@gmail.com", "abcdefghijklmnop")), on);
    const off = applyEnvChanges(on, smtpOffSettings());
    assert.match(off, /^MAIL_SMTP_HOST=$/m);
    assert.match(off, /^MAIL_SMTP_PASSWORD=$/m);
    assert.match(off, /^AUTH_MAIL_ADAPTER=dev-smtp$/m);
    assert.match(off, /^KEEP=1$/m);
  });

  test("durum özeti şifre ve adres göstermez", () => {
    const on = applyEnvChanges("", smtpOnSettings("ada@gmail.com", "abcdefghijklmnop"));
    const summary = describeSmtp(on);
    assert.deepEqual(summary, { real: true, host: "smtp.gmail.com", adapter: "mail-service", passwordSet: true });
    assert.ok(!JSON.stringify(summary).includes("abcdefghijklmnop"));
    assert.deepEqual(describeSmtp("MAIL_SMTP_HOST=\n"), { real: false, host: "", adapter: "dev-smtp (varsayılan)", passwordSet: false });
  });
});
// #endregion mail-lib

// #region accounts-lib
describe("accounts-lib", () => {
  const { DEV_ACCOUNTS, assertDevProfile, assertNodeSupported, assertDevTarget, assertUuid, base32Decode, emailFor, hashPassword, isUndeliverableDomain, parseDevEnv, parseRequiredRoles, sqlLiteral, totp, validateConfig } = dev;

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
});
// #endregion accounts-lib

// #region env-check
describe("env-check", () => {
  const script = devPath;
  const source = region("env-check");

  const key32 = (seed) => Buffer.alloc(32, seed).toString("base64");
  const jwt = Buffer.alloc(1200, 9).toString("base64");

  function run(body, extra = []) {
    const dir = mkdtempSync(join(tmpdir(), "ltz-envcheck-"));
    try {
      const file = join(dir, "a.env");
      writeFileSync(file, body);
      const result = spawnSync(process.execPath, [script, "env-check", file, ...extra], { encoding: "utf8" });
      return { ...result, output: `${result.stdout}${result.stderr}` };
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  }

  const good = (more = "") =>
    `AUTH_OTP_HMAC_KEY=${key32(1)}\nAUTH_TOTP_ENCRYPTION_KEY=${key32(2)}\nAUTH_JWT_PRIVATE_KEY=${jwt}\n`
    + `MAIL_PAYLOAD_ENCRYPTION_KEY=${key32(3)}\n`
    + `COMPOSE_PROFILES=dev-mail\nAUTH_EVENT_TARGETS=user\n${more}`;

  // Güvenlik bekçisi: araç yalnız yerel dosya okur; ağ/süreç/dinamik kod yok ve dosyaya yazmaz.
  test("script yalnız izinli modülleri içe aktarır, yazma/ağ/süreç/dinamik kod kullanmaz", () => {
    const imports = [...source.matchAll(/from\s+"([^"]+)"/g)].map((match) => match[1]).sort();
    assert.deepEqual(imports, [], "bölüm kendi import'unu taşımaz (ortak başlıkta)");
    for (const forbidden of [
      "writeFileSync", "appendFileSync", "child_process", "node:http", "node:https", "node:net", "node:crypto",
      "fetch(", "eval(", "new Function", "process.env", "import(", "spawnSync(", "spawn(", "createServer(", "randomBytes(", "generateKeyPairSync(",
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

  test("Mail anahtarı boş, kısa veya Base64 değilse FAIL; Auth ayarları sağlamsa mesaj Mail Service'i işaret eder", () => {
    const withoutMail = good().replace(/MAIL_PAYLOAD_ENCRYPTION_KEY=.*\n/, "");
    for (const mailLine of ["", "MAIL_PAYLOAD_ENCRYPTION_KEY=\n", `MAIL_PAYLOAD_ENCRYPTION_KEY=${Buffer.alloc(16, 5).toString("base64")}\n`,
      "MAIL_PAYLOAD_ENCRYPTION_KEY=@@@@\n"]) {
      const result = run(withoutMail + mailLine);
      assert.equal(result.status, 1, mailLine);
      assert.match(result.output, /\[FAIL\] MAIL_PAYLOAD_ENCRYPTION_KEY/);
      assert.match(result.output, /Mail Service bu ayarlarla AÇILMAZ/);
      assert.ok(!result.output.includes("Auth bu ayarlarla AÇILMAZ"), "Auth sağlamken Auth açılmaz denmemeli");
      assert.ok(!result.output.includes(Buffer.alloc(16, 5).toString("base64")), "kısa anahtar çıktıya sızdı");
    }
    // Auth ayarı da bozuksa mesaj Auth'u söyler.
    assert.match(run(withoutMail.replace(key32(1), "")).output, /Auth bu ayarlarla AÇILMAZ/);
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
      assert.equal(spawnSync(process.execPath, [script, "keys", "--rotate-totp", "--rotate-hmac", file], { encoding: "utf8" }).status, 0);
      const result = spawnSync(process.execPath, [script, "env-check", file], { encoding: "utf8" });
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
      const result = spawnSync(process.execPath, [script, "env-check", a, b], { encoding: "utf8" });
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
    const result = spawnSync(process.execPath, [script, "env-check", join(tmpdir(), "yok-boyle-dosya.env")], { encoding: "utf8" });
    assert.equal(result.status, 1);
  });

  test("kural modülü saftır: içe aktarma, dosya/ağ/süreç erişimi ve ortam okuması yok", () => {
    const rules = region("env-rules");
    assert.ok(!/^\s*import\s/m.test(rules), "kural modülü içe aktarma yapmamalı");
    for (const forbidden of ["node:", "require(", "fetch(", "process.", "console."]) {
      assert.ok(!rules.includes(forbidden), `izin verilmeyen kullanım: ${forbidden}`);
    }
  });
});
// #endregion env-check

// #region keys
describe("keys", () => {
  const script = devPath;
  const source = region("keys");

  // Güvenlik bekçisi: bu script yalnız yerel dosya okuyup yazar ve anahtar üretir. Ağ, süreç başlatma veya
  // dinamik kod yürütme eklenirse bu test kırılır ve değişiklik gözden geçirmede açıkça görünür.
  test("script yalnız izinli modülleri içe aktarır, ağ/süreç/dinamik kod kullanmaz", () => {
    const imports = [...source.matchAll(/from\s+"([^"]+)"/g)].map((match) => match[1]).sort();
    assert.deepEqual(imports, [], "bölüm kendi import'unu taşımaz (ortak başlıkta)");
    for (const forbidden of [
      "child_process", "node:http", "node:https", "node:net", "node:dns", "node:tls", "node:worker_threads",
      "fetch(", "XMLHttpRequest", "WebSocket", "eval(", "new Function", "process.env", "import(", "spawnSync(", "spawn(", "createServer(",
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

      const first = spawnSync(process.execPath, [script, "keys", a, b], { encoding: "utf8" });
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

      const second = spawnSync(process.execPath, [script, "keys", a, b], { encoding: "utf8" });
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
      const empty = spawnSync(process.execPath, [script, "keys", missing, blank], { encoding: "utf8" });
      assert.equal(empty.status, 0, empty.stderr);
      assert.equal(get(missing), "user");
      assert.equal(get(blank), "user");

      const filled = spawnSync(process.execPath, [script, "keys", custom, already], { encoding: "utf8" });
      assert.equal(filled.status, 0, filled.stderr);
      assert.equal(get(custom), "community", "dolu liste ezilmemeli");
      assert.equal(get(already), "community,user");
      assert.match(filled.stdout, /custom.env: .*uyarı -> AUTH_EVENT_TARGETS/);
      assert.ok(!/already.env: .*uyarı/.test(filled.stdout), "user içeren listede uyarı olmamalı");

      const again = spawnSync(process.execPath, [script, "keys", missing, blank], { encoding: "utf8" });
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
      const run = spawnSync(process.execPath, [script, "keys", fresh], { encoding: "utf8" });
      assert.equal(run.status, 0, run.stderr);
      const customRun = spawnSync(process.execPath, [script, "keys", custom], { encoding: "utf8" });
      // Dolu ama geçersiz Mail anahtarı ezilmez; kontrol bunu FAIL olarak bildirir (çıkış 1) ve Auth'un açılacağını söyler.
      assert.equal(customRun.status, 1, customRun.stderr);
      assert.match(customRun.stdout, /\[FAIL\] MAIL_PAYLOAD_ENCRYPTION_KEY: Base64 değil veya tam 32 bayt değil; Mail Service açılmaz/);
      assert.match(customRun.stdout, /Mail Service bu ayarlarla AÇILMAZ/);
      assert.ok(!customRun.stdout.includes("dolu-anahtar"), "anahtar çıktıya sızdı");
      assert.match(run.stdout, /\[OK {2}\] MAIL_PAYLOAD_ENCRYPTION_KEY: geçerli/);
      const freshText = readFileSync(fresh, "utf8");
      const customText = readFileSync(custom, "utf8");

      assert.equal(Buffer.from(get(freshText, "MAIL_PAYLOAD_ENCRYPTION_KEY"), "base64").length, 32);
      assert.ok(!run.stdout.includes(get(freshText, "MAIL_PAYLOAD_ENCRYPTION_KEY")), "anahtar çıktıya sızdı");
      for (const name of smtpKeys) {
        assert.equal(get(freshText, name), "", `${name} boş eklenmeli`);
      }
      assert.equal(get(freshText, "GIPHY_API_KEY"), "", "dış sağlayıcı anahtarı üretilmez, boş eklenir");
      assert.equal(get(customText, "MAIL_SMTP_HOST"), "smtp.example.test", "dolu satır ezilmemeli");
      assert.equal(get(customText, "MAIL_SMTP_PORT"), "", "boş satır doldurulmamalı ve çoğaltmamalı");
      assert.equal(get(customText, "MAIL_PAYLOAD_ENCRYPTION_KEY"), "dolu-anahtar");
      assert.equal(customText.match(/^MAIL_SMTP_PORT=/gm).length, 1);

      const again = spawnSync(process.execPath, [script, "keys", fresh], { encoding: "utf8" });
      const againCustom = spawnSync(process.execPath, [script, "keys", custom], { encoding: "utf8" });
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

      const run = spawnSync(process.execPath, [script, "keys", stale, custom, missing], { encoding: "utf8" });
      assert.equal(run.status, 0, run.stderr);
      assert.ok(readFileSync(stale, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=60\r\nJWT_ISSUER=ltz-auth\r\n"));
      assert.match(run.stdout, /güncellendi -> JWT_ACCESS_TTL_SECONDS \(300 -> 60\)/);
      assert.ok(readFileSync(custom, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=120\n"));
      assert.match(readFileSync(missing, "utf8"), /^JWT_ACCESS_TTL_SECONDS=60\r?$/m, "eksik satır .env.example'dan (60) eklenir");

      const again = spawnSync(process.execPath, [script, "keys", stale], { encoding: "utf8" });
      assert.ok(readFileSync(stale, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=60\r\n"));
      assert.ok(!/güncellendi/.test(again.stdout), "ikinci çalıştırmada değişiklik olmamalı");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  test("JWT_ACCESS_TTL_SECONDS=900 değeri de 60'a çekilir (Hardware 60 sn'den uzun token'ı reddeder)", () => {
    const dir = mkdtempSync(join(tmpdir(), "ltz-ttl900-"));
    try {
      const file = join(dir, "long.env");
      writeFileSync(file, "JWT_ACCESS_TTL_SECONDS=900\r\nJWT_ISSUER=ltz-auth\r\n");
      const run = spawnSync(process.execPath, [script, "keys", file], { encoding: "utf8" });
      assert.equal(run.status, 0, run.stderr);
      assert.ok(readFileSync(file, "utf8").startsWith("JWT_ACCESS_TTL_SECONDS=60\r\nJWT_ISSUER=ltz-auth\r\n"));
      assert.match(run.stdout, /güncellendi -> JWT_ACCESS_TTL_SECONDS \(900 -> 60\)/);
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

      const run1 = spawnSync(process.execPath, [script, "keys", "--rotate-totp", file], { encoding: "utf8" });
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

      const run2 = spawnSync(process.execPath, [script, "keys", "--rotate-totp", file], { encoding: "utf8" });
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

      const run1 = spawnSync(process.execPath, [script, "keys", "--rotate-hmac", file], { encoding: "utf8" });
      assert.equal(run1.status, 0, run1.stderr);
      const body1 = readFileSync(file, "utf8");
      const second = getLine(body1, "AUTH_OTP_HMAC_KEY");
      assert.notEqual(second, first);
      assert.equal(getLine(body1, "AUTH_OTP_HMAC_PREVIOUS_KEY"), first);
      for (const secret of [first, second]) assert.ok(!run1.stdout.includes(secret), "anahtar çıktıya sızdı");

      // Önceki anahtar dolu: eski kurtarma kodlarını kaybettirmemek için ikinci döndürme reddedilir.
      const refused = spawnSync(process.execPath, [script, "keys", "--rotate-hmac", file], { encoding: "utf8" });
      assert.notEqual(refused.status, 0);
      assert.equal(readFileSync(file, "utf8"), body1, "reddedilen döndürme dosyayı değiştirmemeli");

      const forced = spawnSync(process.execPath, [script, "keys", "--rotate-hmac", "--force", file], { encoding: "utf8" });
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
      const run = spawnSync(process.execPath, [script, "keys", "--rotate-totp", file], { encoding: "utf8" });
      assert.notEqual(run.status, 0);
      assert.equal(readFileSync(file, "utf8"), "AUTH_TOTP_ENCRYPTION_KEY=\n");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  // ---- Tek komutla tamamlama: .env.example eşitleme, dosya oluşturma, sondaki kontrol ----


  /** Geçici bir "depo" kurar (ops/ betikleri + verilen .env.example); gerçek .env dosyalarına asla dokunulmaz. */
  function fakeRepo(exampleBody) {
    const dir = mkdtempSync(join(tmpdir(), "ltz-repo-"));
    mkdirSync(join(dir, "ops"));
    const opsDir = devPath;
    copyFileSync(opsDir, join(dir, "ops", "dev.mjs"));
    writeFileSync(join(dir, ".env.example"), exampleBody);
    const run = (...args) => spawnSync(process.execPath, [join(dir, "ops", "dev.mjs"), "keys", ...args], { encoding: "utf8" });
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
});
// #endregion keys

// #region site-url
describe("site-url", () => {
  const { formatEnvLine, parseSiteUrlArgs: parseArgs, validateSiteUrl } = dev;

  const script = devPath;
  const source = region("site-url");

  // Güvenlik bekçisi: araç dosya sistemine, ağa veya sürece erişmez; yalnız ekrana yazar.
  test("script dosya/ağ/süreç/dinamik kod kullanmaz", () => {
    const imports = [...source.matchAll(/from\s+"([^"]+)"/g)].map((match) => match[1]).sort();
    assert.deepEqual(imports, [], "bölüm kendi import'unu taşımaz (ortak başlıkta)");
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
    const ok = spawnSync(process.execPath, [script, "site-url", "https://ltz.example/"], { encoding: "utf8" });
    assert.equal(ok.status, 0);
    assert.match(ok.stdout, /^NEXT_PUBLIC_SITE_URL=https:\/\/ltz\.example$/m);

    const bad = spawnSync(process.execPath, [script, "site-url", "http://ltz.example"], { encoding: "utf8" });
    assert.equal(bad.status, 1);
    assert.match(bad.stderr, /Hata:/);

    const help = spawnSync(process.execPath, [script, "site-url", "--help"], { encoding: "utf8" });
    assert.equal(help.status, 0);
    assert.match(help.stdout, /Kullanım/);
  });
});
// #endregion site-url

// #region e2e-hardware
describe("e2e-hardware", () => {
  const { isolatedEnvironment, CookieJar, scrub, requireFreshJar, captureCommand, deletionFixtureEnvironment, deletionFixturePending } = dev;

  test('deletion fixture bypasses only its own grace period and pins the agreed short access lifetime', () => {
    assert.deepEqual(deletionFixtureEnvironment(), { AUTH_DELETION_GRACE_PERIOD: 'PT0S', JWT_ACCESS_TTL_SECONDS: '60' });
  });

  test('missing endpoint inventory cannot substitute for actual durable deletion acknowledgements', () => {
    assert.equal(deletionFixturePending(16, 1, 15), true);
    assert.equal(deletionFixturePending(16, 1, 11), false);
    assert.equal(deletionFixturePending(12, 1, 11), false);
    assert.equal(deletionFixturePending(16, 5, 11), false);
  });

  test('runtime fixture never inherits application credentials or ambient JVM/debug configuration', () => {
    const env = isolatedEnvironment({ POSTGRES_PASSWORD: 'disposable-fixture' }, {
      Path: 'trusted-path', JAVA_HOME: 'trusted-java', DEBUG: 'true', JAVA_TOOL_OPTIONS: 'untrusted',
      AUTH_JWT_PRIVATE_KEY: 'real-key-must-not-be-inherited', POSTGRES_PASSWORD: 'real-password', SONAR_TOKEN: 'real-token',
    });
    assert.deepEqual(env, { Path: 'trusted-path', JAVA_HOME: 'trusted-java', POSTGRES_PASSWORD: 'disposable-fixture' });
  });

  test('cookie fixture respects path boundaries so refresh credentials never reach Hardware', () => {
    const jar = new CookieJar(), headers = new Headers();
    headers.append('Set-Cookie', '__Host-ltz_access=fixture-access; Path=/; Secure; HttpOnly');
    headers.append('Set-Cookie', '__Secure-ltz_refresh=fixture-refresh; Path=/api/v1/auth/session; Secure; HttpOnly');
    jar.accept(headers);
    assert.equal(jar.header('/api/v1/hardware/profiles'), '__Host-ltz_access=fixture-access');
    assert.match(jar.header('/api/v1/auth/session/refresh'), /fixture-refresh/);
    assert.doesNotMatch(jar.header('/api/v1/auth/session-other'), /fixture-refresh/);
  });

  test('logout removes cleared cookies while preserving unrelated CSRF state', () => {
    const jar = new CookieJar();
    const first = new Headers();
    first.append('Set-Cookie', '__Host-ltz_access=fixture-access; Path=/');
    first.append('Set-Cookie', '__Host-XSRF-TOKEN=fixture-csrf; Path=/');
    jar.accept(first);
    const clear = new Headers(); clear.append('Set-Cookie', '__Host-ltz_access=; Path=/; Max-Age=0'); jar.accept(clear);
    assert.equal(jar.get('__Host-ltz_access'), undefined);
    assert.equal(jar.header('/api/v1/hardware/profiles'), '__Host-XSRF-TOKEN=fixture-csrf');
  });

  test('failure diagnostics redact generated credentials and dynamically observed JWTs', () => {
    const text = scrub('private-fixture eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ4In0.signature fixture-pass', ['private-fixture', 'fixture-pass']);
    assert.equal(text, '[REDACTED] [JWT REDACTED] [REDACTED]');
  });

  test('runtime evidence rejects an absent or stale production artifact', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-hardware-runtime-'));
    try {
      const base = path.join(root, 'backend/services/hardware-service');
      fs.mkdirSync(path.join(base, 'src/main'), { recursive: true });
      fs.mkdirSync(path.join(base, 'target'));
      const parentPom = path.join(root, 'backend/pom.xml');
      const servicePom = path.join(base, 'pom.xml');
      const source = path.join(base, 'src/main/application.properties');
      for (const file of [parentPom, servicePom, source]) fs.writeFileSync(file, 'fixture');
      assert.throws(() => requireFreshJar(root, 'hardware-service'), /Build hardware-service/);
      const jar = path.join(base, 'target/hardware-service-0.0.1-SNAPSHOT.jar');
      fs.writeFileSync(jar, 'fixture artifact');
      assert.equal(requireFreshJar(root, 'hardware-service'), jar);
      const future = new Date(Date.now() + 10000); fs.utimesSync(source, future, future);
      assert.throws(() => requireFreshJar(root, 'hardware-service'), /Rebuild stale/);
    } finally {
      const target = fs.realpathSync(root), parent = fs.realpathSync(os.tmpdir());
      if (path.dirname(target) !== parent || !path.basename(target).startsWith('ltz-hardware-runtime-')) {
        throw new Error('Refusing cleanup outside the dedicated temporary fixture');
      }
      fs.rmSync(target, { recursive: true });
    }
  });

  function fixtureProcess() {
    const child = new EventEmitter(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
    return child;
  }

  test('Docker image pull progress on stderr cannot corrupt a successful container ID', async () => {
    const child = fixtureProcess(), result = captureCommand(child, 'docker');
    child.stderr.write('Unable to find image locally; pulling disposable image\n');
    child.stdout.write('a'.repeat(64) + '\n'); child.emit('close', 0);
    assert.equal(await result, 'a'.repeat(64));
  });

  test('a failed process exposes only redacted and bounded diagnostics', async () => {
    const child = fixtureProcess(), result = captureCommand(child, 'docker', ['fixture-password']);
    child.stderr.write('fixture-password'); child.emit('close', 1);
    await assert.rejects(result, error => error.message === 'docker failed: [REDACTED]');
  });
});
// #endregion e2e-hardware

// #region e2e-game-provider-pilot
describe("e2e-game-provider-pilot", () => {
  const { pilotConfiguration, providerValues, runProviderPilot } = dev;

  test('pilot parser accepts only provider keys and never executes ENV expressions', () => {
    assert.deepEqual(providerValues('DB_PASSWORD=not-forwarded\nIGDB_CLIENT_ID="fixture-id"\nIGDB_CLIENT_SECRET=\'fixture-secret\'\nITAD_API_KEY=$(not-executed) # comment'), {
      IGDB_CLIENT_ID: 'fixture-id', IGDB_CLIENT_SECRET: 'fixture-secret', ITAD_API_KEY: '$(not-executed)'
    });
  });
  test('missing credentials are a visible failure before launching a process', async () => {
    await assert.rejects(runProviderPilot('.', {}), /kimlik bilgileri eksik/);
  });

  test('ITAD pilot forwards only the selected provider credential', () => {
    const config = pilotConfiguration({ IGDB_CLIENT_ID: 'unused-id', IGDB_CLIENT_SECRET: 'unused-secret', ITAD_API_KEY: 'fixture-key' }, 'itad');
    assert.equal(config.test, 'ITGamePriceLivePilot');
    assert.equal(config.environment.ITAD_API_KEY, 'fixture-key');
    assert.equal(config.environment.IGDB_CLIENT_ID, '');
    assert.equal(config.environment.IGDB_CLIENT_SECRET, '');
    assert.throws(() => pilotConfiguration({}, 'itad'), /ITAD anahtarı eksik/);
    assert.throws(() => pilotConfiguration({}, 'unknown'), /Bilinmeyen/);
  });
});
// #endregion e2e-game-provider-pilot

// #region e2e-game-review
describe("e2e-game-review", () => {
  const { fixtureToken } = dev;

  test('isolated fixture tokens have verifiable RS256 signatures and bounded service identity claims', () => {
    const keys = generateKeyPairSync('rsa', { modulusLength: 3072 });
    const token = fixtureToken(keys.privateKey, 'fixture-key', 'svc:auth-service', {
      aud: ['review-service'], scope: 'account:erase', account_id: '00000000-0000-4000-8000-000000000001',
    }, 'svc+jwt');
    const [header, payload, signature] = token.split('.');
    assert.equal(verify('RSA-SHA256', Buffer.from(header + '.' + payload), keys.publicKey, Buffer.from(signature, 'base64url')), true);
    const claims = JSON.parse(Buffer.from(payload, 'base64url'));
    assert.deepEqual(JSON.parse(Buffer.from(header, 'base64url')), { alg: 'RS256', typ: 'svc+jwt', kid: 'fixture-key' });
    assert.equal(claims.exp - claims.iat, 60);
    assert.equal(claims.sub, 'svc:auth-service');
    assert.deepEqual(claims.aud, ['review-service']);
    assert.equal(claims.scope, 'account:erase');
    assert.equal(claims.account_id, '00000000-0000-4000-8000-000000000001');
  });
});
// #endregion e2e-game-review

// #region e2e-auth
describe("e2e-auth", () => {
  const { decodeJwtPart, runAuthSmoke } = dev;

  test('JWT parts are decoded as base64url JSON without any signature trust', () => {
    const part = value => Buffer.from(JSON.stringify(value)).toString('base64url');
    const token = `${part({ typ: 'at+jwt', alg: 'RS256' })}.${part({ sub: 'fixture', iat: 1, exp: 61 })}.signature`;
    assert.deepEqual(decodeJwtPart(token, 0), { typ: 'at+jwt', alg: 'RS256' });
    assert.equal(decodeJwtPart(token, 1).exp - decodeJwtPart(token, 1).iat, 60);
  });

  test('importing the smoke module never starts containers or services', () => {
    assert.equal(typeof runAuthSmoke, 'function');
  });
});
// #endregion e2e-auth

// #region env-profil
describe("env-profil", () => {
  const source = here;

  test('ENV switching preserves local edits, backs up forced changes and reports profile drift', { skip: process.platform !== 'win32' }, () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ltz-env-'));
    fs.mkdirSync(path.join(root, 'ops'));
    fs.copyFileSync(path.join(source, 'env.ps1'), path.join(root, 'ops/env.ps1'));
    const run = (...args) => spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'ops/env.ps1'), ...args], { encoding: 'utf8', windowsHide: true });
    const active = path.join(root, '.env');
    try {
      fs.writeFileSync(path.join(root, '.env.dev'), 'APP_ENV=dev\n');
      fs.writeFileSync(path.join(root, '.env.prod'), 'APP_ENV=prod\n');
      assert.equal(run('status').status, 0);
      assert.equal(fs.existsSync(active), false);
      assert.equal(run('use', 'dev').status, 0);
      assert.equal(fs.readFileSync(active, 'utf8'), '# LTZ-ACTIVE-PROFILE=dev\nAPP_ENV=dev\n');
      assert.match(run('status').stdout, /matches/);
      fs.appendFileSync(active, 'LOCAL_EDIT=keep\n');
      assert.match(run('status').stdout, /differs/);
      assert.notEqual(run('use', 'prod').status, 0);
      assert.match(fs.readFileSync(active, 'utf8'), /LOCAL_EDIT=keep/);
      assert.equal(run('use', 'prod', '-Force').status, 0);
      assert.match(fs.readFileSync(path.join(root, '.env.bak'), 'utf8'), /LOCAL_EDIT=keep/);
      assert.match(fs.readFileSync(active, 'utf8'), /ACTIVE-PROFILE=prod/);
      assert.equal(run('use', 'dev').status, 0);
      fs.writeFileSync(active, '');
      assert.equal(run('status').status, 0);
      assert.notEqual(run('use', 'prod').status, 0);
    } finally { fs.rmSync(root, { recursive: true, force: true }); }
  });
});
// #endregion env-profil

// #region dispatch
describe("dispatch", () => {
  const run = (args, env = {}) =>
    spawnSync(process.execPath, [devPath, ...args], { encoding: "utf8", env: { ...process.env, ...env } });

  test("dev.mjs içe aktarılınca hiçbir komut çalışmaz, yalnız dışa aktarım olur", () => {
    assert.equal(typeof dev.main, "function");
    assert.equal(typeof dev.parseArgs, "function");
    assert.equal(typeof dev.parseSiteUrlArgs, "function");
    assert.equal(typeof dev.runHardwareAuthE2e, "function");
  });

  test("help tüm alt komutları listeler", () => {
    const result = run(["help"]);
    assert.equal(result.status, 0);
    for (const word of ["keys", "env-check", "accounts", "site-url", "e2e hardware", "e2e game-provider-pilot", "mail on|off|status"]) {
      assert.ok(result.stdout.includes(word), `help çıktısında yok: ${word}`);
    }
  });

  test("bilinmeyen komut tüm alt komutları gösteren hata verir", () => {
    const result = run(["calistir"]);
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Bilinmeyen komut: calistir \(start \| stop \| reset \| status \| mail \| accounts \| keys \| env-check \| site-url \| e2e \| help\)/);
  });

  test("bilinmeyen e2e adı 2 koduyla biter ve geçerli adları söyler", () => {
    const result = run(["e2e", "yok"]);
    assert.equal(result.status, 2);
    assert.match(result.stderr, /hardware \| performance \| auth \| game-auth \| game-community \| game-review \| library \| game-provider-pilot/);
  });

  test("provider pilotu açık izin olmadan ENV dosyası okumaz", () => {
    for (const args of [[], ["--itad"], ["--allow-env-files", "--baska"], ["--allow-env-files", "--itad", "fazla"]]) {
      const result = run(["e2e", "game-provider-pilot", ...args]);
      assert.equal(result.status, 1, args.join(" "));
      assert.match(result.stderr, /ENV okuması için açık izin gerekli/);
    }
  });

  test("ENV dosyası okuyan tek e2e yolu provider pilotudur", () => {
    const reads = [...devSource.matchAll(/readFileSync\(path\.join\(root, "\.env(?:\.dev)?"\)/g)];
    assert.ok(reads.length >= 2, "pilot .env ve .env.dev okur");
    const pilotRegion = region("dispatch");
    const outside = devSource.replace(pilotRegion, "");
    assert.ok(!/path\.join\(root, "\.env(?:\.dev)?"\)/.test(outside), "pilot dışında kök .env okuması olmamalı");
  });

  test("site-url alt komutu çıkış kodunu iletir", () => {
    assert.equal(run(["site-url", "https://ltz.example/"]).status, 0);
    assert.equal(run(["site-url", "http://ltz.example"]).status, 1);
    assert.equal(run(["site-url", "--help"]).status, 0);
  });
});
// #endregion dispatch

// #region mail
describe("mail", () => {
  const run = (file, args, env = {}) =>
    spawnSync(process.execPath, [devPath, "mail", ...args, "--file", file], { encoding: "utf8", env: { ...process.env, ...env } });

  test("on/off/status: şifre dosyaya yazılır, ekrana ve çıktıya sızmaz; off ayarları boşaltır", () => {
    const dir = mkdtempSync(join(tmpdir(), "ltz-mail-"));
    try {
      const file = join(dir, "dev.env");
      writeFileSync(file, "KEEP=1\n");
      assert.match(run(file, ["status"]).stdout, /Mailpit/);
      const on = run(file, ["on"], { DEV_MAIL_PASSWORD: "abcd efgh ijkl mnop", DEV_MAIL_USER: "ada@gmail.com" });
      assert.equal(on.status, 0, on.stderr);
      assert.ok(!`${on.stdout}${on.stderr}`.includes("abcdefghijklmnop"), "şifre çıktıya sızdı");
      const body = readFileSync(file, "utf8");
      assert.match(body, /^KEEP=1$/m);
      assert.match(body, /^MAIL_SMTP_HOST=smtp\.gmail\.com$/m);
      assert.match(run(file, ["status"]).stdout, /GERÇEK e-posta açık: smtp\.gmail\.com, şifre dolu/);
      const off = run(file, ["off"]);
      assert.equal(off.status, 0, off.stderr);
      assert.ok(!readFileSync(file, "utf8").includes("abcdefghijklmnop"));
      assert.match(run(file, ["status"]).stdout, /Mailpit/);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  test("dosya yoksa keys komutunu önerir; geçersiz Gmail adresi dosyayı değiştirmez", () => {
    const dir = mkdtempSync(join(tmpdir(), "ltz-mail-"));
    try {
      const file = join(dir, "yok.env");
      const missing = run(file, ["status"]);
      assert.equal(missing.status, 1);
      assert.match(missing.stderr, /node ops\/dev\.mjs keys/);
      writeFileSync(file, "KEEP=1\n");
      const bad = run(file, ["on"], { DEV_MAIL_PASSWORD: "abcdefghijklmnop", DEV_MAIL_USER: "ada@example.com" });
      assert.equal(bad.status, 1);
      assert.equal(readFileSync(file, "utf8"), "KEEP=1\n");
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  test("mail bölümü eski dosya adlarına ve doğrudan alt betiğe dönmez", () => {
    const source = region("mail");
    assert.ok(!/dev-mail\.mjs|dev-accounts\.mjs|generate-auth-dev-keys\.mjs/.test(source));
    assert.ok(!source.includes("spawnSync("), "mail komutu süreç başlatmaz");
  });
});
// #endregion mail

// #region birleşik-dosya
describe("birleşik dosya", () => {
  test("dev.mjs içinde eski ayrı betik adlarına yönlendirme kalmamıştır", () => {
    const stale = [...devSource.matchAll(/node ops\/(dev-accounts|dev-mail|generate-auth-dev-keys|auth-env-check|auth-smoke|frontend\/site-url)\.mjs/g)];
    assert.deepEqual(stale.map((match) => match[0]), []);
  });

  test("her bölüm açılır ve kapanır", () => {
    const opens = [...devSource.matchAll(/^\/\/ #region (\S+)$/gm)].map((match) => match[1]);
    const closes = [...devSource.matchAll(/^\/\/ #endregion (\S+)$/gm)].map((match) => match[1]);
    assert.deepEqual(opens, closes);
    assert.equal(new Set(opens).size, opens.length, "bölüm adları tekil olmalı");
  });

  test("başlatma akışı alt komutları kendi dev.mjs dosyasını çağırır", () => {
    const start = region("start");
    assert.match(start, /runNode\("keys"\)/);
    assert.match(start, /runNode\("accounts", \["up"/);
    assert.match(start, /resolve\(root, "ops", "dev\.mjs"\), subcommand/);
  });

  test("katalog içe aktarma: Hardware başlamadan önce ve --standalone ile; --no-catalog ile atlanır", () => {
    const start = region("start");
    assert.ok(dev.KNOWN_FLAGS.has("--no-catalog"));
    const body = start.slice(start.indexOf("const catalogFirst"));
    const order = [body.indexOf("importHardwareCatalog();"), body.indexOf('"hardware-service"]);')];
    assert.ok(order.every((index) => index >= 0) && order[0] < order[1], "sıra: katalog → hardware");
    assert.match(start, /targets\.filter\(\(name\) => name !== "hardware-service"\)/);
    assert.match(start, /importerRun\(\["import", dataset, "--standalone"\]\)/);
    assert.match(start, /wantsHardware\(\) && !flags\.has\("--no-catalog"\)/);
    assert.ok(!/\.env(?:\.dev)?"\)/.test(start.slice(start.indexOf("function importHardwareCatalog"))), "katalog adımı ENV dosyası okumaz");
  });

  test("dry-run --all: katalog Hardware'den önce yazdırılır; --no-catalog ve Hardware'siz akışta yazdırılmaz", () => {
    const run = (args) => spawnSync(process.execPath, [devPath, "--dry-run", "--no-frontend", "--no-mail", "--skip-keys", ...args], { encoding: "utf8" });
    const all = run(["--all"]);
    assert.equal(all.status, 0, all.stderr);
    const out = all.stdout;
    const up = out.indexOf("accounts up");
    const catalog = out.indexOf("hardware-catalog-import.mjs import");
    const hardware = out.indexOf("accounts up --no-seed hardware-service");
    assert.ok(up >= 0 && catalog > up && hardware > catalog, "sıra: accounts up → katalog → hardware");
    assert.match(out, /--standalone/);
    assert.ok(!run(["--all", "--no-catalog"]).stdout.includes("hardware-catalog-import"));
    assert.ok(!run(["--with=game-service"]).stdout.includes("hardware-catalog-import"));
    assert.ok(run(["--with=hardware-service"]).stdout.includes("hardware-catalog-import.mjs import"));
  });

  test("Game kataloğu: Game başlamadan önce içe aktarılır; --no-catalog ile atlanır; ENV okunmaz", () => {
    const start = region("start");
    assert.match(start, /wantsGame\(\) && !flags\.has\("--no-catalog"\)/);
    const body = start.slice(start.indexOf("const gameCatalogFirst"));
    const order = [body.indexOf("importGameCatalog();"), body.indexOf('"game-service"]);')];
    assert.ok(order.every((index) => index >= 0) && order[0] < order[1], "sıra: Game kataloğu → game-service");
    assert.match(start, /name !== "game-service"/);
    assert.ok(!/\.env(?:\.dev)?"\)/.test(start.slice(start.indexOf("function importGameCatalog"))), "Game katalog adımı ENV dosyası okumaz");
  });

  test("dry-run: --with=game-service katalogu Game'den önce yazdırır; --no-catalog ve Game'siz akışta yazdırılmaz", () => {
    const run = (args) => spawnSync(process.execPath, [devPath, "--dry-run", "--no-frontend", "--no-mail", "--skip-keys", ...args], { encoding: "utf8" });
    const withGame = run(["--with=game-service"]);
    assert.equal(withGame.status, 0, withGame.stderr);
    const catalog = withGame.stdout.indexOf("game-catalog-import.mjs import");
    const game = withGame.stdout.indexOf("accounts up --no-seed game-service");
    assert.ok(catalog >= 0 && game > catalog, "sıra: katalog → game-service");
    assert.ok(!run(["--with=game-service", "--no-catalog"]).stdout.includes("game-catalog-import"));
    assert.ok(!run(["--with=hardware-service"]).stdout.includes("game-catalog-import"));
    assert.ok(run(["--all"]).stdout.includes("game-catalog-import.mjs import"));
  });
});
// #endregion birleşik-dosya
