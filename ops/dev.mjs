#!/usr/bin/env node
// LTZ yerel geliştirme ve operasyon aracı: TEK DOSYA. Yeni klondan çalışır hale getirir; anahtar üretimi, ENV denetimi,
// e-posta, test hesapları, site adresi ve tüm gerçek-HTTP kabul (e2e) betikleri alt komut olarak buradadır.
// Dosya bölümleri `// #region` / `// #endregion` ile ayrılmıştır; testler (ops/dev.test.mjs) bölüm bazlı çalışır.
//
// Kurulum ve günlük kullanım
//   node ops/dev.mjs                 # her şey: ön koşullar → anahtarlar → e-posta → servisler → hesaplar → frontend (5001)
//   node ops/dev.mjs --no-frontend   # frontend'i başlatma        (ayrıca: --no-mail, --no-build, --no-services, --skip-keys, --dry-run)
//   node ops/dev.mjs --with=game-service,library-service   # çekirdeğe ek servisler (virgülle)
//   node ops/dev.mjs --all           # compose'taki TÜM servisler (20): önce bellek denetlenir, yetmiyorsa durur (--force ile geçilir)
//                                    # Hardware dahilse PC parça kataloğu, Hardware başlamadan önce içe aktarılır (--no-catalog ile atlanır)
//   node ops/dev.mjs stop            # servisleri durdur (veri kalır)
//   node ops/dev.mjs reset           # servisleri VE tüm yerel veriyi sil (onay ister)
//   node ops/dev.mjs status          # servis ve e-posta durumu
//   node ops/dev.mjs mail on|off|status   # gerçek e-posta (Gmail) aç/kapat
//
// Alt komutlar
//   node ops/dev.mjs keys [dosya…] [--rotate-totp] [--rotate-hmac] [--force]   # Auth sırlarını üretir/tamamlar (.env.dev)
//   node ops/dev.mjs env-check [dosya…]                                        # Auth ENV kural denetimi (.env ve .env.dev)
//   node ops/dev.mjs accounts up|seed|list|totp <ad>                           # yığın + rol başına test hesapları
//   node ops/dev.mjs site-url <adres> [--production]                           # NEXT_PUBLIC_SITE_URL doğrulama ve satır üretimi
//   node ops/dev.mjs e2e <ad>                                                  # gerçek-HTTP kabul testleri (bkz. `help`)
//   node ops/dev.mjs help
//
// Başlatma adımları (her biri tekrar çalıştırmaya güvenlidir):
//   1. Ön koşullar: Node 24.7+, Docker çalışıyor mu.
//   2. Anahtarlar: `keys` alt komutu (.env.dev yoksa oluşturur, eksikleri tamamlar, dolu satıra dokunmaz).
//   3. E-posta: gerçek SMTP kapalıysa YALNIZ Google uygulama şifresini SORAR (görünmez; Gmail adresi sabit). Boş geçersen
//      Mailpit'te kalır (gerçek mail gitmez, kimse takılmaz). Etkileşimsiz kurulum için DEV_MAIL_PASSWORD ortam değişkeni.
//      Şifre depoda/betikte YOKTUR; yalnız kendi .env.dev dosyana yazılır.
//   4. Servisler ve hesaplar: `accounts up` (Postgres, Mailpit, Mail, Auth, User, Gateway + her role test hesabı).
//      `--with=` / `--all` ile istediğin diğer servisler de eklenir; hepsi ayağa kalkana kadar beklenir ve her biri ✓/✗ raporlanır.
//      Her Spring servisi ~550 MB tutar; Docker belleği (varsayılan RAM'in yarısı) yetmezse betik başlamadan durur.
//   5. Frontend: node_modules eksik/eskiyse `npm ci`, sonra http://localhost:5001 (port 5001 şart: Gateway CORS varsayılanı).
// Yalnız yerel geliştirme içindir; production dağıtımı bu betiği kullanmaz. Hardware kataloğu içe aktarma ayrı kalır:
// ops/hardware-catalog-import.mjs.

import fs from "node:fs";
import path from "node:path";
import * as nodeCrypto from "node:crypto";
import { spawn, spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, readFileSync, renameSync, statSync, writeFileSync } from "node:fs";
import { generateKeyPairSync, randomBytes, randomUUID, sign } from "node:crypto";
import { createServer } from "node:http";
import { homedir, tmpdir } from "node:os";
import { dirname, resolve } from "node:path";
import { createInterface } from "node:readline/promises";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

function fail(message) {
  console.error(`✗ ${message}`);
  process.exit(1);
}

/** Dosyayı 0600 izniyle atomik yazar (önce .tmp, sonra yeniden adlandırma). */
function writeAtomic(path, text) {
  const temp = `${path}.tmp`;
  writeFileSync(temp, text, { mode: 0o600 });
  renameSync(temp, path);
}

// #region prompt
// Terminal soru-cevap yardımcıları (başlatma, mail ve site-url akışları ortak kullanır).
/** Bir satır okur. */
function ask(question) {
  process.stdout.write(question);
  return new Promise((done) => {
    process.stdin.once("data", (chunk) => {
      process.stdin.pause();
      done(String(chunk).trim());
    });
  });
}

/** Terminalde yazılanı göstermeden okur (karakterler ekrana yansımaz). İptal (Ctrl+C) hata fırlatır. */
function askHidden(question) {
  process.stdout.write(question);
  return new Promise((done, reject) => {
    const stdin = process.stdin;
    stdin.setRawMode(true);
    stdin.resume();
    stdin.setEncoding("utf8");
    let value = "";
    const finish = () => {
      stdin.setRawMode(false);
      stdin.pause();
      stdin.off("data", onData);
    };
    const onData = (chunk) => {
      for (const char of chunk) {
        if (char === "\u0003") {
          finish();
          reject(new Error("İptal edildi."));
          return;
        }
        if (char === "\r" || char === "\n") {
          finish();
          process.stdout.write("\n");
          done(value);
          return;
        }
        if (char === "\u007f" || char === "\b") value = value.slice(0, -1);
        else value += char;
      }
    };
    stdin.on("data", onData);
  });
}
// #endregion prompt

// #region accounts-lib
// accounts komutu için saf yardımcılar (ağ/veritabanı yok; test edilebilir).
// `argon2Sync` Node 24.7'de eklendi. Adlandırılmış içe aktarma eski Node'da modül yüklenirken anlaşılmaz bir hata verirdi; bu yüzden
// ad alanı ile içe aktarılır ve sürüm `assertNodeSupported` ile önceden, anlaşılır bir mesajla denetlenir.
const accountsLib = (() => {
  const { createHmac, randomBytes } = nodeCrypto;

  /** Betik Node 24.7+ ister (parola özeti için `crypto.argon2Sync`). Eski sürümde açık bir mesajla durur. */
  function assertNodeSupported(version = process.versions.node) {
    const [major = 0, minor = 0] = String(version).split(".").map(Number);
    if (major < 24 || (major === 24 && minor < 7)) {
      throw new Error(`Node 24.7 veya üstü gerekli (bu makinede ${version}): parola özeti için crypto.argon2Sync kullanılır. https://nodejs.org`);
    }
  }

  /** Rol → hesap eşlemesi. Kullanıcı adı = e-postanın yerel kısmı (Auth: 3-20, yalnız a-z0-9_). `mfa`: Auth bu roller için TOTP ister. */
  const DEV_ACCOUNTS = Object.freeze([
    { local: "user", role: "PLAYER", mfa: false },
    { local: "creator", role: "CONTENT_CREATOR", mfa: false },
    { local: "developer", role: "GAME_DEVELOPER", mfa: false },
    { local: "moderator", role: "MODERATOR", mfa: true },
    { local: "admin", role: "ADMIN", mfa: true }
  ]);

  const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);
  const DOMAIN_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;

  /** Yalnız yerel geliştirme: hedef adresler yerel olmalı, kapsayıcı `ltz-dev-` ile başlamalı, ortam production olmamalı. */
  function assertDevTarget({ gatewayUrl, mailpitUrl, container, nodeEnv }) {
    const problems = [];
    for (const [label, value] of [["GATEWAY_URL", gatewayUrl], ["MAILPIT_URL", mailpitUrl]].filter(([, value]) => value !== undefined)) {
      let host = "";
      try {
        host = new URL(value).hostname;
      } catch {
        problems.push(`${label} geçerli bir adres değil`);
        continue;
      }
      if (!LOCAL_HOSTS.has(host)) problems.push(`${label} yerel olmalı (localhost/127.0.0.1), verilen: ${host}`);
    }
    if (!/^ltz-dev-[a-z0-9_-]+$/.test(container ?? "")) problems.push("Postgres kapsayıcısı ltz-dev- ile başlamalı");
    if (nodeEnv === "production") problems.push("NODE_ENV=production iken çalışmaz");
    if (problems.length > 0) throw new Error(`Güvenlik kapısı: ${problems.join("; ")}`);
  }

  /**
 * Auth'un çalıştığı Spring profili kapısı. Auth zaten gevşek ayarları yalnız dev/test profilinde kabul eder; aynı beyaz liste
 * burada da uygulanır. Profil okunamazsa (boş/bilinmeyen) KAPALI davranılır: emin olunamayan ortamda zayıf parola yazılmaz.
 */
  function assertDevProfile(profile) {
    const active = String(profile ?? "").split(",").map((item) => item.trim().toLowerCase()).filter(Boolean);
    if (active.length === 0) throw new Error("Güvenlik kapısı: Auth profili okunamadı (SPRING_PROFILES_ACTIVE); dev olduğundan emin olunamıyor.");
    const outsiders = active.filter((item) => item !== "dev" && item !== "test");
    if (outsiders.length > 0) throw new Error(`Güvenlik kapısı: Auth profili dev/test değil (${outsiders.join(",")}); zayıf parola yazılmaz.`);
  }

  function validateConfig({ password, domain }) {
    if (typeof password !== "string" || password.length === 0) {
      throw new Error("DEV_ACCOUNTS_PASSWORD tanımlı değil (parola depoya yazılmaz; .env.dev'e ekle ya da ortamdan ver).");
    }
    if (password.length > 128) throw new Error("DEV_ACCOUNTS_PASSWORD en fazla 128 karakter olabilir.");
    if (!DOMAIN_PATTERN.test(domain ?? "")) throw new Error("DEV_ACCOUNTS_DOMAIN geçerli bir alan adı olmalı.");
  }

  function emailFor(account, domain) {
    return `${account.local}@${domain}`;
  }

  /** Spring `DelegatingPasswordEncoder` biçimi: `{argon2}$argon2id$v=19$m=…,t=…,p=…$salt$hash` (Base64, dolgusuz). */
  function hashPassword(password) {
    const memory = 19456;
    const passes = 2;
    const parallelism = 1;
    const nonce = randomBytes(16);
    const tag = nodeCrypto.argon2Sync("argon2id", { message: password, nonce, parallelism, tagLength: 32, memory, passes });
    const b64 = (buffer) => buffer.toString("base64").replace(/=+$/, "");
    return `{argon2}$argon2id$v=19$m=${memory},t=${passes},p=${parallelism}$${b64(nonce)}$${b64(tag)}`;
  }

  const BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  function base32Decode(text) {
    const clean = text.replace(/=+$/, "").replace(/\s+/g, "").toUpperCase();
    let bits = 0;
    let value = 0;
    const bytes = [];
    for (const char of clean) {
      const index = BASE32.indexOf(char);
      if (index < 0) throw new Error("Geçersiz Base32 karakteri");
      value = (value << 5) | index;
      bits += 5;
      if (bits >= 8) {
        bytes.push((value >>> (bits - 8)) & 0xff);
        bits -= 8;
      }
    }
    return Buffer.from(bytes);
  }

  /** RFC 6238 (HMAC-SHA1, 30 sn adım). */
  function totp(secretBase32, nowMs = Date.now(), digits = 6) {
    const counter = Math.floor(nowMs / 1000 / 30);
    const message = Buffer.alloc(8);
    message.writeBigUInt64BE(BigInt(counter));
    const mac = createHmac("sha1", base32Decode(secretBase32)).update(message).digest();
    const offset = mac[mac.length - 1] & 0x0f;
    const binary = mac.readUInt32BE(offset) & 0x7fffffff;
    return String(binary % 10 ** digits).padStart(digits, "0");
  }

  function secondsLeftInStep(nowMs = Date.now()) {
    return 30 - (Math.floor(nowMs / 1000) % 30);
  }

  /** SQL dizge değeri için tek tırnak kaçışı. Girdiler zaten sabit/biçimli; ek savunma. */
  function sqlLiteral(value) {
    return `'${String(value).replaceAll("'", "''")}'`;
  }

  const DEV_ENV_KEYS = new Set(["DEV_ACCOUNTS_PASSWORD", "DEV_ACCOUNTS_DOMAIN"]);

  /**
 * `.env.dev` metninden YALNIZ DEV_ACCOUNTS_* anahtarlarını okur; diğer satırlar (anahtarlar, sırlar) hiç işlenmez ya da
 * döndürülmez. Yorum satırları ve tırnaklı değerler desteklenir.
 */
  function parseDevEnv(text) {
    const found = {};
    for (const rawLine of String(text ?? "").split(/\r?\n/)) {
      const line = rawLine.trim();
      if (line === "" || line.startsWith("#")) continue;
      const index = line.indexOf("=");
      if (index < 0) continue;
      const key = line.slice(0, index).trim();
      if (!DEV_ENV_KEYS.has(key)) continue;
      let value = line.slice(index + 1).trim();
      if (/^(".*"|'.*')$/.test(value)) value = value.slice(1, -1);
      if (value !== "") found[key] = value;
    }
    return found;
  }

  const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

  /** SQL'e gömülecek kimlik değerinin UUID olduğunu doğrular (başka bir veritabanından okunan değer SQL'e karışmasın). */
  function assertUuid(value) {
    if (typeof value !== "string" || !UUID.test(value)) throw new Error("Beklenen UUID biçiminde değil");
    return value;
  }

  const RESERVED_SUFFIX = /\.(test|example|invalid|localhost|local)$/;
  const EXAMPLE_DOMAINS = new Set(["example.com", "example.net", "example.org"]);

  /** E-posta teslim edilemeyen (ayrılmış) alan adı mı? `.test`, `.example`, `.invalid`, `.localhost`, `.local`, example.* */
  function isUndeliverableDomain(domain) {
    const value = String(domain ?? "").trim().toLowerCase();
    return value !== "" && (RESERVED_SUFFIX.test(value) || EXAMPLE_DOMAINS.has(value));
  }

  /**
 * `AUTH_2FA_REQUIRED_ROLES` ham değerinden 2FA zorunlu roller. Tanımsız (undefined) = Auth varsayılanı ADMIN,MODERATOR;
 * boş değer = hiçbir rol zorunlu değil (Auth bunu yalnız dev/test profilinde kabul eder).
 */
  function parseRequiredRoles(raw) {
    if (raw === undefined) return ["ADMIN", "MODERATOR"];
    return String(raw).split(",").map((role) => role.trim().toUpperCase()).filter(Boolean);
  }

  return { assertNodeSupported, DEV_ACCOUNTS, assertDevTarget, assertDevProfile, validateConfig, emailFor, hashPassword, base32Decode, totp, secondsLeftInStep, sqlLiteral, parseDevEnv, assertUuid, isUndeliverableDomain, parseRequiredRoles };
})();
// #endregion accounts-lib

// #region mail-lib
// mail komutu için saf yardımcılar (dosya/terminal yok; test edilebilir).
const mailLib = (() => {
  /**
 * Ekibin ortak test Gmail hesabı (SIR DEĞİL: yalnız adres; hesaba girmek için uygulama şifresi gerekir ve o depoda yoktur).
 * Başka bir hesap denemek için `DEV_MAIL_USER` ortam değişkeni.
 */
  const DEFAULT_MAIL_USER = "lobbytwozerotest@gmail.com";

  /** Gmail SMTP'nin SIR OLMAYAN sabit ayarları: herkes için aynıdır; yalnız uygulama şifresi kişiye özeldir. */
  function smtpOnSettings(address, appPassword) {
    return {
      MAIL_SMTP_HOST: "smtp.gmail.com",
      MAIL_SMTP_PORT: "587",
      MAIL_SMTP_SECURITY: "STARTTLS",
      MAIL_SMTP_USERNAME: address,
      MAIL_SMTP_PASSWORD: appPassword,
      MAIL_FROM_ADDRESS: address,
      MAIL_ALLOWED_LINK_HOSTS: "localhost",
      AUTH_MAIL_ADAPTER: "mail-service"
    };
  }

  /** Gerçek SMTP'yi kapatır: boş satırlar e-postayı yeniden Mailpit'e (http://localhost:8025) düşürür. */
  function smtpOffSettings() {
    return {
      MAIL_SMTP_HOST: "",
      MAIL_SMTP_PORT: "",
      MAIL_SMTP_SECURITY: "",
      MAIL_SMTP_USERNAME: "",
      MAIL_SMTP_PASSWORD: "",
      MAIL_FROM_ADDRESS: "",
      MAIL_ALLOWED_LINK_HOSTS: "",
      AUTH_MAIL_ADAPTER: "dev-smtp"
    };
  }

  const GMAIL = /^[a-z0-9][a-z0-9._%+-]{0,62}@gmail\.com$/i;

  function validateGmailAddress(address) {
    if (!GMAIL.test(String(address ?? "").trim())) {
      throw new Error("Gmail adresi (…@gmail.com) bekleniyor.");
    }
    return String(address).trim().toLowerCase();
  }

  /**
 * Google "uygulama şifresi" 16 harftir; Google onu 4'lü gruplar halinde boşluklu gösterir. Boşluklar atılır. Hata mesajı
 * girilen değeri ASLA içermez.
 */
  function normalizeAppPassword(raw) {
    const value = String(raw ?? "").replace(/\s+/g, "");
    if (!/^[a-zA-Z]{16}$/.test(value)) {
      throw new Error("Uygulama şifresi 16 harf olmalı (Google 4'lü gruplar halinde gösterir; boşluklar önemsiz).");
    }
    return value.toLowerCase();
  }

  /**
 * `.env` metninde verilen anahtarları günceller: var olan AKTİF `KEY=` satırının değeri değişir (yorum satırlarına dokunulmaz),
 * olmayan anahtar sona eklenir. Diğer satırlar, sıra ve satır sonu (LF/CRLF) aynen korunur.
 */
  function applyEnvChanges(text, changes) {
    const eol = text.includes("\r\n") ? "\r\n" : "\n";
    const lines = text.length === 0 ? [] : text.replace(/\r\n/g, "\n").split("\n");
    if (lines.length > 0 && lines[lines.length - 1] === "") lines.pop();
    for (const [key, value] of Object.entries(changes)) {
      if (/[\r\n]/.test(value)) throw new Error(`${key} değeri satır sonu içeremez.`);
      let index = -1;
      lines.forEach((line, i) => {
        if (line.startsWith(`${key}=`)) index = i;
      });
      if (index >= 0) lines[index] = `${key}=${value}`;
      else lines.push(`${key}=${value}`);
    }
    return lines.join(eol) + eol;
  }

  /** Durum özeti: değer göstermez; yalnız host, adaptör ve şifrenin dolu olup olmadığı. */
  function describeSmtp(text) {
    const read = (key) => {
      const line = text.replace(/\r\n/g, "\n").split("\n").reverse().find((item) => item.startsWith(`${key}=`));
      return line === undefined ? "" : line.slice(key.length + 1).trim();
    };
    const host = read("MAIL_SMTP_HOST");
    return {
      real: host !== "",
      host,
      adapter: read("AUTH_MAIL_ADAPTER") || "dev-smtp (varsayılan)",
      passwordSet: read("MAIL_SMTP_PASSWORD") !== ""
    };
  }

  return { DEFAULT_MAIL_USER, smtpOnSettings, smtpOffSettings, validateGmailAddress, normalizeAppPassword, applyEnvChanges, describeSmtp };
})();
// #endregion mail-lib

// #region dev-lib
// dev.mjs için saf yardımcılar (terminal, dosya ve Docker yok; test edilebilir).
const devLib = (() => {
  const KNOWN_FLAGS = new Set(["--no-mail", "--no-frontend", "--no-build", "--no-services", "--skip-keys", "--dry-run", "--all", "--force", "--no-catalog"]);
  /** Varsayılan (çekirdek) servisler: giriş, kayıt, profil ve e-posta için yeterli olan en küçük küme. */
  const CORE_SERVICES = ["postgres", "mailpit", "mail-service", "auth-service", "user-service", "api-gateway"];
  /** Java olmayan servisler (bellek tahmininde ayrı sayılır). */
  const LIGHT_MB = { postgres: 150, mailpit: 30 };
  /** Bir Spring Boot servisi boştayken yaklaşık bu kadar bellek tutar (ölçüm: 6 servis ≈ 1,9 GB). */
  const MB_PER_JVM_SERVICE = 550;
  const COMMANDS = new Set(["start", "stop", "reset", "status", "mail"]);
  /** Ayrı alt komutlar (ana akıştan önce yönlendirilir): hata iletisinde birlikte gösterilir. */
  const SUBCOMMANDS = ["accounts", "keys", "env-check", "site-url", "e2e", "help"];

  /**
 * `node ops/dev.mjs [komut] [--bayrak …] [--with=servis1,servis2]`. Komut verilmezse `start`. `mail` komutunun kalan argümanları
 * aynen iletilir. `--with=` çekirdeğe ek servisler ister (virgülle).
 */
  function parseArgs(argv) {
    const flags = new Set();
    const positional = [];
    const withServices = [];
    for (const arg of argv) {
      if (arg.startsWith("--with=")) {
        withServices.push(...arg.slice("--with=".length).split(",").map((name) => name.trim()).filter(Boolean));
      } else if (arg.startsWith("--")) {
        flags.add(arg);
      } else {
        positional.push(arg);
      }
    }
    const command = positional[0] ?? "start";
    return { command, rest: positional.slice(1), flags, withServices };
  }

  function validateArgs({ command, flags, withServices = [] }) {
    const badNames = withServices.filter((name) => !/^[a-z][a-z0-9-]{1,40}$/.test(name));
    if (badNames.length > 0) throw new Error(`Geçersiz servis adı: ${badNames.join(", ")}`);
    const unknownFlags = [...flags].filter((flag) => !KNOWN_FLAGS.has(flag));
    if (unknownFlags.length > 0) throw new Error(`Bilinmeyen seçenek: ${unknownFlags.join(", ")}`);
    if (!COMMANDS.has(command)) throw new Error(`Bilinmeyen komut: ${command} (${[...COMMANDS, ...SUBCOMMANDS].join(" | ")})`);
  }

  /**
 * Gerçek e-posta (Gmail) adımı kararı:
 *  - "skip"     : --no-mail verilmiş
 *  - "already"  : gerçek SMTP zaten açık ve şifre dolu, dokunulmaz
 *  - "env"      : DEV_MAIL_PASSWORD verilmiş (etkileşimsiz kurulum; adres sabit, DEV_MAIL_USER ile değiştirilebilir)
 *  - "ask"      : etkileşimli terminalde YALNIZ uygulama şifresi sorulur (boş bırakılırsa Mailpit)
 *  - "mailpit"  : sorulamaz (etkileşimsiz) ve değişken yok: Mailpit'te kalınır, kurulum durmaz
 */
  function decideMailStep({ skip, real, passwordSet, interactive, envPassword }) {
    if (skip) return "skip";
    if (real && passwordSet) return "already";
    if (envPassword !== undefined) return "env";
    if (interactive) return "ask";
    return "mailpit";
  }

  /** Bellek tahmini (MB): Java servisleri × ortalama + Postgres/Mailpit. */
  function estimateMemoryMb(services) {
    return [...new Set(services)].reduce((sum, name) => sum + (LIGHT_MB[name] ?? MB_PER_JVM_SERVICE), 0);
  }

  /** Docker belleği bu servis kümesine yeter mi? İşletim ve geçici yük için %15 pay bırakılır. */
  function memoryVerdict({ services, dockerMemBytes }) {
    const needMb = estimateMemoryMb(services);
    const haveMb = Math.floor(dockerMemBytes / 1024 / 1024);
    return { needMb, haveMb, ok: needMb <= haveMb * 0.85 };
  }

  /** `npm ci` gerekir mi? node_modules yoksa ya da kilit dosyası ondan yeniyse. Zamanlar ms (yoksa `null`). */
  function needsInstall({ modulesTime, lockTime }) {
    if (modulesTime === null) return true;
    return lockTime !== null && lockTime > modulesTime;
  }

  /** Kurulum sonu özeti (hesap listesi parolayı göstermez). */
  function summaryLines({ domain, realMail, frontendRunning }) {
    const accounts = ["user", "creator", "developer", "moderator", "admin"].map((name) => `${name}@${domain}`).join(", ");
    return [
      "✓ Hazır",
      `  Giriş:     http://localhost:5001/tr/giris${frontendRunning ? "" : "   (frontend: cd frontend && npx next dev -H localhost -p 5001)"}`,
      `  Hesaplar:  ${accounts}`,
      "  Parola:    .env.dev içindeki DEV_ACCOUNTS_PASSWORD",
      realMail ? "  E-posta:   GERÇEK (Gmail SMTP). Kapatmak için: node ops/dev.mjs mail off" : "  E-posta:   Mailpit http://localhost:8025 (gerçek mail gitmez). Gerçek için: node ops/dev.mjs mail on",
      "  Durdurmak: Ctrl+C (frontend), servisler için: node ops/dev.mjs stop"
    ];
  }

  return { KNOWN_FLAGS, CORE_SERVICES, MB_PER_JVM_SERVICE, COMMANDS, SUBCOMMANDS, parseArgs, validateArgs, decideMailStep, estimateMemoryMb, memoryVerdict, needsInstall, summaryLines };
})();
// #endregion dev-lib

// #region env-rules
// Auth ENV kuralları (saf fonksiyonlar; dosya/ağ/süreç yok). İki betik kullanır:
//   node ops/dev.mjs keys      : eksikleri tamamlar ve sonunda bu kuralları çalıştırıp raporlar
//   node ops/dev.mjs env-check : yalnız okuyup raporlar (CI veya el ile)
// Kurallar, Auth'un AÇILIŞTA yaptığı doğrulamaların aynısıdır; anahtar/parola DEĞERLERİ hiçbir çıktıya girmez.
const envRules = (() => {
  const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;
  const MAX_KEY_VERSION = 32767;

  /** Satırları ad -> değer sözlüğüne çevirir (tırnaklar atılır). */
  function parse(body) {
    const out = {};
    for (const line of body.split(/\r?\n/)) {
      const found = line.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
      if (found) out[found[1]] = found[2].trim().replace(/^(["'])(.*)\1$/, "$2");
    }
    return out;
  }

  /** Base64 değerin bayt uzunluğu; geçersiz biçimde -1. */
  function byteLength(candidate) {
    if (!candidate || candidate.length % 4 !== 0 || !BASE64.test(candidate)) return -1;
    return Buffer.from(candidate, "base64").length;
  }

  const list = (text) => (text ?? "").split(",").map((part) => part.trim()).filter(Boolean);

  /** Tek dosyanın bulguları: { level: FAIL|WARN|INFO|OK, name, problem, fix? }. */
  function check(settings) {
    const findings = [];
    const add = (level, name, problem, fix) => findings.push({ level, name, problem, fix });
    const regenerate = "node ops/dev.mjs keys ile üretin";

    const hmac = settings.AUTH_OTP_HMAC_KEY ?? "";
    if (hmac === "") add("FAIL", "AUTH_OTP_HMAC_KEY", "boş; Auth açılmaz", regenerate);
    else if (byteLength(hmac) < 32) add("FAIL", "AUTH_OTP_HMAC_KEY", "Base64 değil veya 32 bayttan kısa; Auth açılmaz", regenerate);
    else add("OK", "AUTH_OTP_HMAC_KEY", "geçerli");

    const totp = settings.AUTH_TOTP_ENCRYPTION_KEY ?? "";
    if (totp === "") add("FAIL", "AUTH_TOTP_ENCRYPTION_KEY", "boş; Auth açılmaz", regenerate);
    else if (byteLength(totp) !== 32) add("FAIL", "AUTH_TOTP_ENCRYPTION_KEY", "Base64 değil veya tam 32 bayt değil; Auth açılmaz", regenerate);
    else add("OK", "AUTH_TOTP_ENCRYPTION_KEY", "geçerli");

    const jwt = settings.AUTH_JWT_PRIVATE_KEY ?? "";
    if (jwt === "") add("FAIL", "AUTH_JWT_PRIVATE_KEY", "boş; Auth açılmaz", regenerate);
    else if (!BASE64.test(jwt)) add("FAIL", "AUTH_JWT_PRIVATE_KEY", "Base64 (PKCS#8 DER) biçiminde değil; Auth açılmaz", regenerate);
    else add("OK", "AUTH_JWT_PRIVATE_KEY", "dolu");

    // Mail Service: kuyruktaki kodu şifreleyen AES-256-GCM anahtarı; boş ya da geçersizse Mail Service açılmaz (Auth etkilenmez).
    const mailKey = settings.MAIL_PAYLOAD_ENCRYPTION_KEY ?? "";
    if (mailKey === "") add("FAIL", "MAIL_PAYLOAD_ENCRYPTION_KEY", "boş; Mail Service açılmaz", regenerate);
    else if (byteLength(mailKey) !== 32) add("FAIL", "MAIL_PAYLOAD_ENCRYPTION_KEY", "Base64 değil veya tam 32 bayt değil; Mail Service açılmaz", regenerate);
    else add("OK", "MAIL_PAYLOAD_ENCRYPTION_KEY", "geçerli");

    // Rotasyon (S9) — hepsi isteğe bağlı; ayarlıysa Auth açılışta biçimini doğrular.
    const versionText = settings.AUTH_TOTP_ENCRYPTION_KEY_VERSION ?? "";
    let version = 1;
    if (versionText !== "") {
      version = Number(versionText);
      if (!Number.isInteger(version) || version < 1 || version > MAX_KEY_VERSION) {
        add("FAIL", "AUTH_TOTP_ENCRYPTION_KEY_VERSION", `1 ile ${MAX_KEY_VERSION} arasında tam sayı olmalı; Auth açılmaz`,
          "boş bırakın (varsayılan 1) ya da düzeltin");
        version = 1;
      }
    }
    const previous = settings.AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS ?? "";
    if (previous !== "") {
      const seen = new Set();
      let malformed = false;
      let highest = 0;
      for (const entry of list(previous)) {
        const cut = entry.indexOf("=");
        const entryVersion = cut > 0 ? Number(entry.slice(0, cut).trim()) : Number.NaN;
        const entryKey = cut > 0 ? entry.slice(cut + 1).trim() : "";
        if (!Number.isInteger(entryVersion) || entryVersion < 1 || entryVersion > MAX_KEY_VERSION
            || seen.has(entryVersion) || entryVersion === version || byteLength(entryKey) !== 32) {
          malformed = true;
          break;
        }
        seen.add(entryVersion);
        highest = Math.max(highest, entryVersion);
      }
      if (malformed) {
        add("FAIL", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS",
          "'sürüm=Base64,...' biçiminde değil; sürümler tekil, güncel sürümden farklı ve anahtarlar 32 bayt olmalı; Auth açılmaz",
          "node ops/dev.mjs keys --rotate-totp kullanın ya da listeyi düzeltin");
      } else {
        add("INFO", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS", `rotasyon açık (${seen.size} eski sürüm)`);
        if (highest > version) {
          add("WARN", "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "güncel sürüm eski sürümlerden küçük; yeni bir anahtar sürümü beklenir",
            "sürümü artırın (--rotate-totp bunu kendisi yapar)");
        }
      }
    } else if (version !== 1) {
      add("WARN", "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "1'den farklı ama eski anahtar listesi boş; eski sürümdeki TOTP kayıtları çözülemez",
        "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS listesini ekleyin");
    }
    const hmacPrevious = settings.AUTH_OTP_HMAC_PREVIOUS_KEY ?? "";
    if (hmacPrevious !== "") {
      if (byteLength(hmacPrevious) < 32) {
        add("FAIL", "AUTH_OTP_HMAC_PREVIOUS_KEY", "Base64 değil veya 32 bayttan kısa; Auth açılmaz", "düzeltin ya da boş bırakın");
      } else if (hmacPrevious === hmac) {
        add("WARN", "AUTH_OTP_HMAC_PREVIOUS_KEY", "güncel anahtarla aynı; rotasyon etkisiz", "node ops/dev.mjs keys --rotate-hmac");
      } else {
        add("INFO", "AUTH_OTP_HMAC_PREVIOUS_KEY", "rotasyon açık");
      }
    }

    // Ortama göre kolaylıklar ve tuzaklar.
    const isProd = list(settings.SPRING_PROFILES_ACTIVE).includes("prod");
    const adapter = settings.AUTH_MAIL_ADAPTER ?? "";
    if (isProd && adapter !== "" && adapter !== "mail-service") {
      add("FAIL", "AUTH_MAIL_ADAPTER", "prod profili yalnız 'mail-service' adaptörüyle açılır; Auth açılmaz", "prod'da mail-service seçin");
    }
    if (!isProd) {
      if (!list(settings.COMPOSE_PROFILES).includes("dev-mail")) {
        add("WARN", "COMPOSE_PROFILES", "dev-mail yok; doğrulama/parola kodu e-postaları Mailpit'e düşmez", "COMPOSE_PROFILES=dev-mail ekleyin");
      }
      if (!list(settings.AUTH_EVENT_TARGETS).includes("user")) {
        add("WARN", "AUTH_EVENT_TARGETS", "'user' yok; Auth olayları User Service'e gitmez", "değeri 'user' yapın veya listeye ekleyin");
      }
    }
    return findings;
  }

  /** Dosyalar arası tutarlılık: aynı anahtar .env ve .env.dev'de aynı olmalı (ops/env durumu da bunu bekler). */
  function consistency(parsedList) {
    if (parsedList.length < 2) return [];
    const names = ["AUTH_OTP_HMAC_KEY", "AUTH_TOTP_ENCRYPTION_KEY", "AUTH_JWT_PRIVATE_KEY", "AUTH_OTP_HMAC_PREVIOUS_KEY",
      "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS", "MAIL_PAYLOAD_ENCRYPTION_KEY"];
    return names.filter((name) => new Set(parsedList.map((entry) => entry[name] ?? "")).size > 1);
  }

  const rank = { FAIL: 0, WARN: 1, INFO: 2, OK: 3 };

  /**
 * entries: [{ shown: "görünen dosya adı", body: "dosya metni" }] -> { lines: ["..."], failures: sayı }.
 * Dönen satırlar yalnız ayar adı ve sabit açıklama içerir; hiçbir ayar değeri yoktur.
 */
  function buildReport(entries) {
    const lines = [];
    let failures = 0;
    let authFailures = 0;
    const parsedList = entries.map((entry) => parse(entry.body));
    entries.forEach((entry, index) => {
      lines.push(`== ${entry.shown}`);
      for (const item of check(parsedList[index]).sort((a, b) => rank[a.level] - rank[b.level])) {
        if (item.level === "FAIL") {
          failures++;
          if (!item.name.startsWith("MAIL_")) authFailures++;
        }
        lines.push(`  [${item.level.padEnd(4)}] ${item.name}: ${item.problem}${item.fix ? `  -> ${item.fix}` : ""}`);
      }
    });
    const differing = consistency(parsedList);
    if (differing.length) {
      lines.push("== dosyalar arası");
      for (const name of differing) {
        lines.push(`  [WARN] ${name}: dosyalarda farklı; ops/env durumu uyuşmazlık gösterir  -> aynı değeri kullanın`);
      }
    }
    // Yalnız Mail ayarı hatalıysa Auth açılır; mesaj hangi servisin açılmayacağını doğru söylemeli.
    const subject = authFailures ? "Auth" : "Mail Service";
    lines.push(failures ? `\n${failures} hata: ${subject} bu ayarlarla AÇILMAZ.` : "\nHata yok: Auth ayarları açılış doğrulamasından geçer.");
    return { lines, failures };
  }

  return { parseEnvText: parse, byteLength, checkSettings: check, checkConsistency: consistency, buildReport };
})();
// #endregion env-rules

// #region site-url
// NEXT_PUBLIC_SITE_URL üretici/doğrulayıcı.
//
// Verilen (ya da sorulan) site adresini doğrular, normalleştirir ve eklenecek ENV satırını EKRANA yazar.
// Hiçbir dosyayı okumaz, oluşturmaz veya değiştirmez; gerçek .env / .env.local dosyalarına dokunmaz.
// Satırı hangi dosyaya ekleyeceğine sen karar verirsin.
const siteUrlTool = (() => {
  const ENV_KEY = "NEXT_PUBLIC_SITE_URL";
  const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

  const HELP = `Kullanım:
  node ops/dev.mjs site-url <adres> [--production]
  node ops/dev.mjs site-url            (adres sorulur)

Seçenekler:
  --production   Production ortamı için doğrula: yalnız https ve yerel olmayan bir alan adı kabul edilir.
  --help, -h     Bu yardımı göster.

Ne yapar:
  Adresi doğrular, kök adrese (origin) indirger (yol, sorgu, parça, sondaki "/" atılır) ve
  ${ENV_KEY}=<adres> satırını ekrana yazar. Dosya YAZMAZ ve ENV dosyalarını OKUMAZ.

Kurallar:
  - http ve https kabul edilir; başka protokoller reddedilir.
  - http yalnız yerel geliştirme (localhost, 127.0.0.1, [::1]) için kabul edilir.
  - --production ile http ve localhost reddedilir.
  - Kullanıcı adı/parola içeren adresler reddedilir.

Not: NEXT_PUBLIC_* değerleri 'next build' sırasında koda gömülür. Değeri değiştirdikten sonra
frontend yeniden build edilmelidir. Değer boş bırakılırsa site canonical/sitemap üretmez ve indekslenmez.
`;

  /**
 * @param {string} raw
 * @param {{ production?: boolean }} [options]
 * @returns {{ ok: true, url: string, warnings: string[] } | { ok: false, error: string }}
 */
  function validateSiteUrl(raw, options = {}) {
    const value = (raw ?? "").trim();
    if (!value) {
      return { ok: false, error: "Adres boş olamaz." };
    }

    let url;
    try {
      url = new URL(value);
    } catch {
      return { ok: false, error: `Geçerli bir adres değil: ${value} (örnek: https://alanadi.com)` };
    }

    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return { ok: false, error: `Yalnız http veya https kabul edilir (gelen: ${url.protocol}).` };
    }
    if (url.username || url.password) {
      return { ok: false, error: "Adres kullanıcı adı veya parola içeremez." };
    }

    const isLocal = LOCAL_HOSTS.has(url.hostname);
    if (url.protocol === "http:" && !isLocal) {
      return { ok: false, error: "http yalnız yerel geliştirme içindir; yayın adresi https olmalıdır." };
    }
    if (options.production && (url.protocol !== "https:" || isLocal)) {
      return {
        ok: false,
        error: "Production için https ve yerel olmayan bir alan adı gerekir (localhost kullanılamaz)."
      };
    }

    const warnings = [];
    if (url.pathname !== "/" || url.search || url.hash) {
      warnings.push("Yol, sorgu ve parça atıldı; yalnız kök adres kullanılır.");
    }
    if (isLocal) {
      warnings.push("Yerel adres: bu değerle site arama motorlarına açılmaz (indekslenmez).");
    }

    return { ok: true, url: url.origin, warnings };
  }

  /** Ekrana yazılacak güvenli ENV satırı. */
  function formatEnvLine(url) {
    return `${ENV_KEY}=${url}`;
  }

  /**
 * @param {string[]} argv
 * @returns {{ help: boolean, production: boolean, value: string | undefined, error?: string }}
 */
  function parseArgs(argv) {
    const parsed = { help: false, production: false, value: undefined };
    for (const arg of argv) {
      if (arg === "--help" || arg === "-h") {
        parsed.help = true;
      } else if (arg === "--production") {
        parsed.production = true;
      } else if (arg.startsWith("-")) {
        return { ...parsed, error: `Bilinmeyen seçenek: ${arg}` };
      } else if (parsed.value === undefined) {
        parsed.value = arg;
      } else {
        return { ...parsed, error: "Yalnız tek bir adres verilebilir." };
      }
    }
    return parsed;
  }

  async function ask() {
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    try {
      return await rl.question("Site adresi (örn. https://alanadi.com): ");
    } finally {
      rl.close();
    }
  }

  async function main(argv) {
    const args = parseArgs(argv);
    if (args.error) {
      console.error(`${args.error}\n\n${HELP}`);
      return 2;
    }
    if (args.help) {
      console.log(HELP);
      return 0;
    }

    const raw = args.value ?? (await ask());
    const result = validateSiteUrl(raw, { production: args.production });
    if (!result.ok) {
      console.error(`Hata: ${result.error}`);
      return 1;
    }

    for (const warning of result.warnings) {
      console.warn(`Uyarı: ${warning}`);
    }
    console.log("\nAşağıdaki satırı ilgili ENV dosyasına KENDİN ekle (bu araç dosya değiştirmez):\n");
    console.log(formatEnvLine(result.url));
    console.log("\nDeğeri değiştirdikten sonra frontend'i yeniden build et (NEXT_PUBLIC_* build anında gömülür).");
    return 0;
  }

  return { ENV_KEY, SITE_URL_HELP: HELP, validateSiteUrl, formatEnvLine, parseSiteUrlArgs: parseArgs, siteUrlMain: main };
})();
// #endregion site-url

// Lib ad alanlarının açık dışa aktarımı (testler ve komutlar için).
export const { assertNodeSupported, DEV_ACCOUNTS, assertDevTarget, assertDevProfile, validateConfig, emailFor, hashPassword, base32Decode, totp, secondsLeftInStep, sqlLiteral, parseDevEnv, assertUuid, isUndeliverableDomain, parseRequiredRoles } = accountsLib;
export const { DEFAULT_MAIL_USER, smtpOnSettings, smtpOffSettings, validateGmailAddress, normalizeAppPassword, applyEnvChanges, describeSmtp } = mailLib;
export const { KNOWN_FLAGS, CORE_SERVICES, MB_PER_JVM_SERVICE, COMMANDS, SUBCOMMANDS, parseArgs, validateArgs, decideMailStep, estimateMemoryMb, memoryVerdict, needsInstall, summaryLines } = devLib;
export const { parseEnvText, byteLength, checkSettings, checkConsistency, buildReport } = envRules;
export const { ENV_KEY, SITE_URL_HELP, validateSiteUrl, formatEnvLine, parseSiteUrlArgs, siteUrlMain } = siteUrlTool;

// #region e2e-hardware
// Yalnız geçici kaynaklarla Hardware–Auth gerçek HTTP kabul testi; gerçek ENV okunmaz.
const hardwareE2e = (() => {
  function isolatedEnvironment(extra = {}, source = process.env) {
    const safe = {};
    for (const name of ['PATH', 'Path', 'SystemRoot', 'WINDIR', 'ComSpec', 'TEMP', 'TMP', 'JAVA_HOME', 'USERPROFILE', 'DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_CONFIG']) {
      if (source[name]) safe[name] = source[name];
    }
    return { ...safe, ...extra };
  }

  class CookieJar {
    cookies = new Map();
    accept(headers) {
      for (const line of headers.getSetCookie()) {
        const [pair, ...attributes] = line.split(';');
        const split = pair.indexOf('=');
        const name = pair.slice(0, split), value = pair.slice(split + 1);
        const cookiePath = attributes.find(a => /^\s*path=/i.test(a))?.trim().slice(5) || '/';
        if (attributes.some(a => /^\s*max-age=0\s*$/i.test(a))) this.cookies.delete(name);
        else this.cookies.set(name, { value, path: cookiePath });
      }
    }
    get(name) { return this.cookies.get(name)?.value; }
    header(requestPath) {
      return [...this.cookies].filter(([, c]) => requestPath === c.path || requestPath.startsWith(c.path.endsWith('/') ? c.path : c.path + '/'))
        .map(([name, c]) => `${name}=${c.value}`).join('; ');
    }
  }

  function scrub(text, secrets) {
    let safe = String(text);
    for (const value of secrets.filter(Boolean)) safe = safe.split(value).join('[REDACTED]');
    return safe.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[JWT REDACTED]');
  }

  function requireFreshJar(root, service) {
    const base = path.join(root, 'backend/services', service);
    const jar = path.join(base, 'target', `${service}-0.0.1-SNAPSHOT.jar`);
    if (!fs.existsSync(jar)) throw new Error(`Build ${service} with the full backend check first`);
    const sources = [path.join(root, 'backend/pom.xml'), path.join(base, 'pom.xml')];
    const sourceRoot = path.join(base, 'src/main');
    for (const entry of fs.readdirSync(sourceRoot, { recursive: true, withFileTypes: true })) {
      if (entry.isFile()) sources.push(path.join(entry.parentPath, entry.name));
    }
    const built = fs.statSync(jar).mtimeMs;
    if (sources.some(file => fs.statSync(file).mtimeMs > built)) throw new Error(`Rebuild stale ${service} runtime artifact`);
    return jar;
  }

  function deletionFixtureEnvironment() {
    // Yalnız bu geçici test süreci; gerçek DEV/prod ortam dosyası değiştirilmez.
    return { AUTH_DELETION_GRACE_PERIOD: 'PT0S', JWT_ACCESS_TTL_SECONDS: '60' };
  }

  function deletionFixturePending(total, confirmed, pending) {
    // Bu fixture D4 için yalnız Hardware'a yönlendirir; diğer hedeflerin ACK'i atlanmaz.
    return total === 16 && confirmed === 1 && pending === 15;
  }

  function captureCommand(child, name, secrets = []) {
    return new Promise((resolve, reject) => {
      let output = '', errors = '';
      child.stdout.on('data', data => { output = (output + data).slice(-48000); });
      child.stderr.on('data', data => { errors = (errors + data).slice(-48000); });
      child.once('error', () => reject(new Error(`Cannot start ${name}`)));
      child.once('close', code => code === 0 ? resolve(output.trim())
        : reject(new Error(`${name} failed: ${scrub((errors + output).slice(-2000), secrets)}`)));
    });
  }

  async function until(action, label, timeout = 90000) {
    const deadline = Date.now() + timeout;
    do {
      const result = await action();
      if (result) return result;
      await delay(300);
    } while (Date.now() < deadline);
    throw new Error(`Timeout: ${label}`);
  }

  async function listen(handler) {
    const server = createServer(handler);
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    return { server, port: server.address().port, origin: `http://127.0.0.1:${server.address().port}` };
  }

  async function unusedPort() {
    const listener = await listen((_, response) => response.end());
    await new Promise(resolve => listener.server.close(resolve));
    return listener.port;
  }

  async function runHardwareAuthE2e(root, log = console.log, { withUser = false } = {}) {
    // Kaynak eskiyse geçici kaynak oluşturmadan açık hata ver.
    for (const service of ['auth-service', 'hardware-service', 'api-gateway']) requireFreshJar(root, service);
    if (withUser) requireFreshJar(root, 'user-service');
    const containers = [], children = [], listeners = [], secrets = [];
    const label = `hardware-auth-e2e-${randomUUID()}`;
    let userMediaRoot;
    let checks = 0;
    const check = (condition, name) => {
      if (!condition) throw new Error(`FAIL: ${name}`);
      checks++; log(`[PASS] ${name}`);
    };
    const command = (binary, args, extra = {}) => {
      const child = spawn(binary, args, { cwd: root, env: isolatedEnvironment(extra), windowsHide: true });
      return captureCommand(child, binary, secrets);
    };
    const docker = (args, extra) => command('docker', args, extra);
    const container = async (image, ports, extra = {}) => {
      const args = ['run', '-d', '--label', `ltz.hardware-auth-e2e=${label}`];
      for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
      for (const name of Object.keys(extra)) args.push('-e', name);
      args.push(image);
      const id = await docker(args, extra);
      if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
      containers.push(id);
      const mapped = {};
      for (const port of ports) {
        const address = await docker(['port', id, `${port}/tcp`]);
        if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Disposable port must be loopback only');
        mapped[port] = Number(address.split(':')[1]);
      }
      return { id, ports: mapped };
    };
    const sql = (db, query) => {
      if (!/^SELECT\s/i.test(query)) throw new Error('Only read-only fixture assertions are allowed');
      return docker(['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', query]);
    };
    const launch = async (service, port, extra, args = []) => {
      const jar = requireFreshJar(root, service);
      const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      const child = spawn(java, ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${port}`, ...args], {
        cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'INFO', ...extra }), windowsHide: true,
      });
      const running = { child, output: '', service };
      children.push(running);
      for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
        running.output = (running.output + scrub(data.toString(), secrets)).slice(-48000);
      });
      child.once('error', () => { running.failed = true; });
      await until(async () => {
        if (running.failed || child.exitCode !== null) throw new Error(`${service} did not start: ${running.output.slice(-5000)}`);
        try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).status === 200; }
        catch { return false; }
      }, `${service} readiness`);
      return `http://127.0.0.1:${port}`;
    };
    const request = async (origin, route, expected, { jar, method = 'GET', body, headers = {}, csrf = false } = {}) => {
      const outgoing = { ...headers };
      if (jar) outgoing.Cookie = jar.header(route);
      if (csrf && jar) outgoing['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN');
      if (body !== undefined) outgoing['Content-Type'] = 'application/json';
      const response = await fetch(origin + route, { method, headers: outgoing,
        body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(6000) });
      jar?.accept(response.headers);
      if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
      const text = await response.text();
      return { response, body: text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : text || undefined };
    };
    try {
      const password = randomBytes(32).toString('base64url');
      const dbPassword = randomBytes(32).toString('base64url');
      const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
      const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
      secrets.push(password, dbPassword, privateKey, hmac, totp);
      const dbEnvironment = { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword };
      const authDb = await container('postgres:17-alpine', [5432], dbEnvironment);
      const hardwareDb = await container('postgres:17-alpine', [5432], dbEnvironment);
      const userDb = withUser ? await container('postgres:17-alpine', [5432], dbEnvironment) : undefined;
      const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
      for (const db of [authDb, hardwareDb, ...(userDb ? [userDb] : [])]) await until(async () => {
        try { return await sql(db, 'SELECT 1') === '1'; } catch { return false; }
      }, 'disposable PostgreSQL');
      const authPort = await unusedPort(), hardwarePort = await unusedPort(), gatewayPort = await unusedPort();
      const authOrigin = `http://127.0.0.1:${authPort}`, hardwareOrigin = `http://127.0.0.1:${hardwarePort}`;
      const gatewayOrigin = `http://127.0.0.1:${gatewayPort}`;
      const userPort = withUser ? await unusedPort() : undefined;
      const userOrigin = withUser ? `http://127.0.0.1:${userPort}` : undefined;
      const hardwareSecret = randomBytes(32).toString('base64url'), socialSecret = randomBytes(32).toString('base64url');
      secrets.push(hardwareSecret, socialSecret);
      let erasureToken;
      const erasureResponses = [];
      const relay = await listen(async (incoming, outgoing) => {
        try {
          erasureToken = incoming.headers.authorization;
          secrets.push(erasureToken);
          const response = await fetch(hardwareOrigin + incoming.url, { method: incoming.method,
            headers: { Authorization: erasureToken }, redirect: 'manual', signal: AbortSignal.timeout(5000) });
          erasureResponses.push(response.status);
          outgoing.writeHead(response.status); outgoing.end();
        } catch { outgoing.writeHead(503); outgoing.end(); }
      });
      const unavailable = await listen((_, response) => { response.writeHead(503); response.end(); });
      listeners.push(relay.server, unavailable.server);
      const dbProperties = db => ({ POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(db.ports[5432]),
        POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', DB_HARDWARE_NAME: 'fixture',
        DB_USER_NAME: 'fixture' });
      const authEnvironment = { ...dbProperties(authDb), ...deletionFixtureEnvironment(),
        SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey,
        AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gatewayOrigin,
        FRONTEND_PUBLIC_URL: gatewayOrigin, AUTH_DELETION_POLL_INTERVAL: 'PT1S', AUTH_DELETION_INITIAL_BACKOFF: 'PT1S',
        AUTH_DELETION_MAX_BACKOFF: 'PT2S', AUTH_DELETION_HTTP_TIMEOUT: 'PT1S',
        LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: String(mail.ports[1025]),
        HARDWARE_SERVICE_URL: relay.origin };
      if (withUser) Object.assign(authEnvironment, {
        AUTH_HARDWARE_SERVICE_CLIENT_SECRET: hardwareSecret, AUTH_SOCIAL_SERVICE_CLIENT_SECRET: socialSecret,
      });
      for (const service of ['USER', 'MAIL', 'GAME', 'LIBRARY', 'REVIEW', 'SOCIAL', 'MESSAGING', 'LFP', 'COMMUNITY', 'CONTENT', 'EVENT', 'RECOMMENDATION', 'PERFORMANCE', 'ADMIN', 'NOTIFICATION']) {
        authEnvironment[`${service}_SERVICE_URL`] = unavailable.origin;
      }
      await launch('auth-service', authPort, authEnvironment);
      const launchUser = async trusted => {
        userMediaRoot ??= path.resolve(root, '.cache', `hardware-user-media-${randomUUID()}`);
        await launch('user-service', userPort, { ...dbProperties(userDb), AUTH_SERVICE_URL: authOrigin },
          [`--ltz.user.social.projection-enabled=${trusted}`, `--ltz.user.media.root=${userMediaRoot}`]);
      };
      if (withUser) await launchUser(false);
      await launch('hardware-service', hardwarePort, { ...dbProperties(hardwareDb), AUTH_SERVICE_URL: authOrigin,
        ...(withUser ? { USER_SERVICE_URL: userOrigin, AUTH_HARDWARE_SERVICE_CLIENT_SECRET: hardwareSecret } : {}) });
      await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: authOrigin, HARDWARE_SERVICE_URL: hardwareOrigin,
        ...(withUser ? { USER_SERVICE_URL: userOrigin } : {}),
        CORS_ALLOWED_ORIGINS: gatewayOrigin });
      check(await sql(hardwareDb, 'SELECT count(*) FROM flyway_schema_history WHERE success') === '8', 'Real Hardware Flyway V1-V8 and ddl-auto validation');
      const mailOrigin = `http://127.0.0.1:${mail.ports[8025]}`;
      const message = async (email, pattern) => until(async () => {
        const result = await (await fetch(mailOrigin + '/api/v1/search?query=' + encodeURIComponent('to:' + email))).json();
        for (const summary of result.messages || []) {
          const detail = await (await fetch(mailOrigin + '/api/v1/message/' + summary.ID)).json();
          const match = String(detail.Text).match(pattern);
          if (match) { secrets.push(match[1]); return match[1]; }
        }
        return false;
      }, 'Mailpit delivery', 20000);
      const register = async () => {
        const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `hardware_${suffix}@example.test`, username = `hw_${suffix}`;
        secrets.push(email);
        await request(gatewayOrigin, '/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
        const code = await message(email, /\b(\d{6})\b/);
        await request(gatewayOrigin, '/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
        const jar = new CookieJar();
        await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
        const result = await request(gatewayOrigin, '/api/v1/auth/login', 200, { jar, method: 'POST', csrf: true,
          headers: { Origin: gatewayOrigin }, body: { identifier: email, password } });
        const token = jar.get('__Host-ltz_access');
        secrets.push(token);
        check(Boolean(token) && result.response.headers.getSetCookie().some(c => c.startsWith('__Host-ltz_access=') && /HttpOnly/i.test(c) && /Secure/i.test(c)), 'Real Auth registration, SMTP verification, login and secure HttpOnly access cookie');
        check(result.body.status === 'AUTHENTICATED' && !result.body.accessToken, 'Auth token is absent from the response body');
        await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
        return { jar, token, email, username };
      };
      const owner = await register(), other = await register();
      const profiles = '/api/v1/hardware/profiles';
      await request(gatewayOrigin, profiles, 401);
      check(true, 'Anonymous Hardware access is 401 through the real Gateway');
      const create = (account, body, expected = 201) => request(gatewayOrigin, profiles, expected, { jar: account.jar,
        method: 'POST', csrf: true, headers: { Origin: gatewayOrigin }, body });
      const first = (await create(owner, { name: 'Runtime owner fixture', deviceType: 'DESKTOP' })).body;
      const second = (await create(other, { name: 'Runtime other fixture', deviceType: 'LAPTOP' })).body;
      if (withUser) {
        const ownerId = JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).sub;
        const otherId = JSON.parse(Buffer.from(other.token.split('.')[1], 'base64url')).sub;
        const productId = randomUUID();
        // Yalnız bu koşunun etiketli, yeni Hardware DB'sinde sentetik katalog fixture'ı.
        const fixtureLabel = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.hardware-auth-e2e" }}', hardwareDb.id]);
        if (fixtureLabel !== label) throw new Error('Refusing catalog seed outside this fixture');
        await docker(['exec', hardwareDb.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-v', 'ON_ERROR_STOP=1', '-c',
          `INSERT INTO hardware_products(id,category,model,normalized_model) VALUES ('${productId}','CPU','User review fixture','user review fixture')`]);
        const reviewRoute = `/api/v1/hardware/catalog/products/${productId}/reviews`;
        const ownerReview = (await request(gatewayOrigin, reviewRoute, 201, { jar: owner.jar, method: 'POST', csrf: true,
          headers: { Origin: gatewayOrigin }, body: { body: 'Owner runtime review', rating: 1 } })).body;
        await request(gatewayOrigin, reviewRoute, 201, { jar: other.jar, method: 'POST', csrf: true,
          headers: { Origin: gatewayOrigin }, body: { body: 'Viewer runtime review', rating: 5 } });
        const closed = (await request(gatewayOrigin, reviewRoute + '?size=1', 200, { jar: other.jar })).body;
        check(closed.totalElements === 1 && closed.summary.averageRating === 5,
          'Actual User untrusted projection keeps only own review, count and rating');
        await request(gatewayOrigin, '/api/v1/user/me/privacy', 200, { jar: owner.jar, method: 'PUT', csrf: true,
          headers: { Origin: gatewayOrigin }, body: { version: 0, friendsList: 'PRIVATE', library: 'PRIVATE',
            hardware: 'PRIVATE', performance: 'PRIVATE', guestProfileAccess: 'BASIC' } });
        const initialUser = children.find(running => running.service === 'user-service').child;
        initialUser.kill();
        await until(async () => initialUser.exitCode !== null || initialUser.signalCode !== null,
          'fixture User shutdown', 15000);
        // Yalnız sentetik fixture: gerçek User/Social yapılandırması değişmez.
        await launchUser(true);
        const open = (await request(gatewayOrigin, reviewRoute + '?size=1', 200, { jar: other.jar })).body;
        check(open.items.length === 1 && open.totalElements === 2 && open.totalPages === 2 && open.summary.averageRating === 3,
          'Actual REVIEW_AUTHOR ignores PRIVATE Hardware setting and filters before page/count/rating');
        await request(gatewayOrigin, `/api/v1/hardware/reviews/${ownerReview.id}`, 200, { jar: other.jar });
        const social = (await request(authOrigin, '/internal/v1/service-tokens', 200, { method: 'POST',
          body: { clientId: 'social-service', clientSecret: socialSecret, audience: 'user-service' } })).body;
        secrets.push(social.accessToken);
        const block = async (eventType, blockerId, blockedId) => request(userOrigin, '/internal/events', 200,
          { method: 'POST', headers: { Authorization: `Bearer ${social.accessToken}` }, body: { eventId: randomUUID(),
            eventType, eventVersion: 1, occurredAt: new Date().toISOString(), producer: 'social-service',
            aggregateId: randomUUID(), payload: { blockerId, blockedId } } });
        for (const [blockerId, blockedId] of [[ownerId, otherId], [otherId, ownerId]]) {
          await block('UserBlocked', blockerId, blockedId);
          const blocked = (await request(gatewayOrigin, reviewRoute + '?size=1', 200, { jar: other.jar })).body;
          await request(gatewayOrigin, `/api/v1/hardware/reviews/${ownerReview.id}`, 404, { jar: other.jar });
          check(blocked.totalElements === 1 && blocked.summary.averageRating === 5,
            'Actual User block event excludes author before Hardware pagination and summary in either direction');
          await block('UserUnblocked', blockerId, blockedId);
        }
        const restored = (await request(gatewayOrigin, reviewRoute, 200, { jar: other.jar })).body;
        check(restored.totalElements === 2 && restored.summary.averageRating === 3,
          'User unblock is observed immediately without caching an earlier audience decision');
      }
      check(Boolean(first.id) && Boolean(second.id), 'Real Gateway cookie-to-Bearer conversion creates owner profiles');
      await request(gatewayOrigin, `${profiles}/${first.id}`, 404, { jar: other.jar });
      await create(owner, { name: 'Rejected impersonation', deviceType: 'DESKTOP', userId: randomUUID() }, 400);
      check(true, 'Hardware IDOR and client-supplied owner rejection');
      await request(gatewayOrigin, profiles, 403, { jar: owner.jar, method: 'POST', body: { name: 'Missing CSRF', deviceType: 'DESKTOP' } });
      await request(gatewayOrigin, profiles, 403, { jar: owner.jar, method: 'POST', csrf: true,
        headers: { Origin: 'https://untrusted.example' }, body: { name: 'Wrong origin', deviceType: 'DESKTOP' } });
      check(true, 'Real Gateway rejects missing CSRF and an untrusted Origin');
      await request(hardwareOrigin, profiles, 401, { headers: { Cookie: `__Host-ltz_access=${owner.token}` } });
      await request(gatewayOrigin, '/api/v1/hardware/catalog/products', 403, { jar: owner.jar, method: 'POST', csrf: true, body: {} });
      check(true, 'Hardware accepts no direct cookie identity and PLAYER gains no catalog write permission');
      const headers = { Authorization: `Bearer ${owner.token}` };
      const accountId = JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).sub;
      check(JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).exp
        - JSON.parse(Buffer.from(owner.token.split('.')[1], 'base64url')).iat === 60,
      'Real user token lifetime is 60 seconds; local acceptance has at most 30 seconds skew');
      const deviceRoute = '/api/v1/auth/devices';
      const device = (await request(gatewayOrigin, deviceRoute, 201, { jar: owner.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { label: 'Hardware fixture', password } })).body;
      secrets.push(device.deviceSecret);
      const deviceCredentials = { deviceId: device.deviceId, deviceSecret: device.deviceSecret };
      const issued = (await request(gatewayOrigin, deviceRoute + '/token', 200,
        { method: 'POST', body: deviceCredentials })).body;
      secrets.push(issued.accessToken);
      check(issued.expiresIn === 60 && issued.scope === 'hardware:sync:write', 'Real Auth issues a narrow 60-second device token');
      const deviceHeaders = { Authorization: `Bearer ${issued.accessToken}` };
      const snapshot = { profileVersion: first.version, consentGeneration: randomUUID(), sequence: 1,
        scan: { schemaVersion: 1, capturedAt: new Date().toISOString(), components: [
          { componentKey: randomUUID(), category: 'CPU', modelName: 'Fixture CPU', quantity: 1 }] } };
      await request(gatewayOrigin, `${profiles}/${first.id}/sync/snapshots`, 404, { method: 'POST',
        headers: { ...deviceHeaders, 'Idempotency-Key': randomUUID() }, body: snapshot });
      await request(gatewayOrigin, profiles, 401, { headers: deviceHeaders });
      await request(hardwareOrigin, `/internal/v1/accounts/${accountId}`, 401, { method: 'DELETE', headers: deviceHeaders });
      check(true, 'Real device identity reaches snapshot policy; unavailable Performance fixture denies apply and user/internal access');
      await request(gatewayOrigin, `${deviceRoute}/${device.deviceId}`, 204, { jar: owner.jar, method: 'DELETE', csrf: true,
        headers: { Origin: gatewayOrigin } });
      await request(gatewayOrigin, deviceRoute + '/token', 401, { method: 'POST', body: deviceCredentials });
      check(true, 'Fixture device is revoked after use and cannot obtain another token');
      await request(hardwareOrigin, `/internal/v1/accounts/${accountId}`, 401, { method: 'DELETE', headers });
      check(true, 'A real user access token cannot invoke internal D4');
      await request(gatewayOrigin, '/api/v1/auth/account/deletion/request', 202, { jar: owner.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { password } });
      const deletionToken = await message(owner.email, /#token=([A-Za-z0-9_-]+)/);
      await request(gatewayOrigin, '/api/v1/auth/account/deletion/confirm', 202, { jar: owner.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { token: deletionToken, username: owner.username } });
      await until(async () => await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE service='hardware' AND status='CONFIRMED'") === '1', 'Auth persisted Hardware ACK', 30000);
      check(erasureResponses.includes(204), 'Real Auth worker-issued D4 token receives Hardware 204 and persists CONFIRMED');
      check(await sql(hardwareDb, `SELECT count(*) FROM hardware_profiles WHERE id='${first.id}'`) === '0', 'D4 removes the owner profile from the disposable Hardware database');
      await request(hardwareOrigin, `/internal/v1/accounts/${accountId}`, 204, { method: 'DELETE', headers: { Authorization: erasureToken } });
      await request(hardwareOrigin, `/internal/v1/accounts/${randomUUID()}`, 403, { method: 'DELETE', headers: { Authorization: erasureToken } });
      check(true, 'The actual worker token supports idempotent retry and rejects another account');
      await request(hardwareOrigin, profiles, 401, { headers });
      await request(gatewayOrigin, profiles, 401, { method: 'POST', headers, body: { name: 'Rejected resurrection', deviceType: 'DESKTOP' } });
      await request(gatewayOrigin, `${profiles}/${second.id}`, 200, { jar: other.jar });
      check(true, 'Old user JWT cannot read or recreate data; another account remains accessible');
      check(deletionFixturePending(Number(await sql(authDb, 'SELECT count(*) FROM account_deletion_tasks')),
        Number(await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='CONFIRMED'")),
        Number(await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='PENDING'")))
        && await sql(authDb, "SELECT count(*) FROM accounts WHERE status='DELETION_REQUESTED'") === '1',
      'All 16 Auth targets remain configured; only Hardware D4 is routed, leaving 15 ACKs pending');
      log(`HARDWARE AUTH RUNTIME E2E PASS: ${checks} checks; other-service Saga completion NOT_VERIFIED`);
      return { checks, status: 'PASS', globalSaga: 'NOT_VERIFIED' };
    } catch (error) {
      throw new Error(scrub(error.message, secrets));
    } finally {
      for (const { child } of children.reverse()) {
        if (child.exitCode === null && child.pid) {
          child.kill();
          await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
          if (child.exitCode === null) child.kill('SIGKILL');
        }
      }
      for (const server of listeners) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
      for (const id of containers.reverse()) {
        const resourceLabel = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.hardware-auth-e2e" }}', id]);
        if (resourceLabel !== label) throw new Error('Refusing cleanup of a resource outside this fixture');
        await docker(['rm', '-f', '-v', id]);
      }
      if (userMediaRoot && fs.existsSync(userMediaRoot)) {
        const expectedParent = path.resolve(root, '.cache');
        if (path.dirname(userMediaRoot) !== expectedParent || !path.basename(userMediaRoot).startsWith('hardware-user-media-')) {
          throw new Error('Refusing fixture media cleanup outside the assigned directory');
        }
        fs.rmSync(userMediaRoot, { recursive: true });
      }
      log(`[CLEANUP] ${children.length} fixture processes and ${containers.length} owned containers removed`);
    }
  }

  return { isolatedEnvironment, CookieJar, scrub, requireFreshJar, deletionFixtureEnvironment, deletionFixturePending, captureCommand, runHardwareAuthE2e };
})();
// #endregion e2e-hardware

// #region e2e-performance
// Gerçek Auth + Hardware + Performance + Gateway HTTP kabulü. Yalnız geçici kaynaklar kullanılır.
const performanceE2e = (() => {
  const { isolatedEnvironment, CookieJar, scrub, requireFreshJar, captureCommand } = hardwareE2e;

  async function until(action, name, timeout = 90000) {
    const end = Date.now() + timeout;
    do { const result = await action(); if (result) return result; await delay(300); } while (Date.now() < end);
    throw new Error(`Timeout: ${name}`);
  }

  async function listen(handler) {
    const server = createServer(handler);
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    return { server, port: server.address().port, origin: `http://127.0.0.1:${server.address().port}` };
  }

  async function unusedPort() {
    const listener = await listen((_, response) => response.end());
    await new Promise(resolve => listener.server.close(resolve));
    return listener.port;
  }

  async function runPerformanceAuthE2e(root, log = console.log) {
    const services = ['auth-service', 'hardware-service', 'performance-service', 'api-gateway'];
    for (const service of services) requireFreshJar(root, service);
    const children = [], containers = [], listeners = [], secrets = [];
    const label = `performance-auth-e2e-${randomUUID()}`;
    let checks = 0;
    const check = (value, name) => {
      if (!value) throw new Error(`FAIL: ${name}`);
      checks++; log(`[PASS] ${name}`);
    };
    const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, {
      cwd: root, env: isolatedEnvironment(extra), windowsHide: true,
    }), binary, secrets);
    const docker = (args, extra) => command('docker', args, extra);
    const container = async (image, ports, env = {}) => {
      const args = ['run', '-d', '--label', `ltz.performance-auth-e2e=${label}`];
      for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
      for (const name of Object.keys(env)) args.push('-e', name);
      args.push(image);
      const id = await docker(args, env);
      if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
      containers.push(id);
      const mapped = {};
      for (const port of ports) {
        const address = await docker(['port', id, `${port}/tcp`]);
        if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Non-loopback fixture port');
        mapped[port] = Number(address.split(':')[1]);
      }
      return { id, ports: mapped };
    };
    const sql = (db, query) => {
      if (!/^SELECT\s/i.test(query)) throw new Error('Only read-only fixture assertions are allowed');
      return docker(['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', query]);
    };
    const launch = async (service, port, env) => {
      const java = process.env.JAVA_HOME
        ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      const child = spawn(java, ['-jar', requireFreshJar(root, service), '--server.address=127.0.0.1', `--server.port=${port}`], {
        cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'INFO', ...env }), windowsHide: true,
      });
      const running = { child, output: '' }; children.push(running);
      for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
        running.output = scrub(running.output + data, secrets).slice(-16000);
      });
      child.once('error', () => { running.failed = true; });
      await until(async () => {
        if (running.failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${running.output.slice(-4000)}`);
        try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok; }
        catch { return false; }
      }, `${service} readiness`);
    };
    const request = async (origin, route, expected, { jar, method = 'GET', body, headers = {} } = {}) => {
      const outgoing = { ...headers };
      if (jar) {
        outgoing.Cookie = jar.header(route);
        outgoing.Origin = origin;
        outgoing['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN') || '';
      }
      if (body !== undefined) outgoing['Content-Type'] = 'application/json';
      const response = await fetch(origin + route, { method, headers: outgoing, redirect: 'manual',
        body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(6000) });
      jar?.accept(response.headers);
      if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
      const text = await response.text();
      return text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : undefined;
    };
    try {
      const password = randomBytes(32).toString('base64url'), dbPassword = randomBytes(32).toString('base64url');
      const clientSecret = randomBytes(32).toString('base64url');
      const referenceClientSecret = randomBytes(32).toString('base64url');
      const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey
        .export({ format: 'der', type: 'pkcs8' }).toString('base64');
      const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
      secrets.push(password, dbPassword, clientSecret, referenceClientSecret, privateKey, hmac, totp);
      const dbEnv = { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword };
      const authDb = await container('postgres:17-alpine', [5432], dbEnv);
      const performanceDb = await container('postgres:17-alpine', [5432], dbEnv);
      const hardwareDb = await container('postgres:17-alpine', [5432], dbEnv);
      const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
      for (const db of [authDb, performanceDb, hardwareDb]) await until(async () => {
        try { return await sql(db, 'SELECT 1') === '1'; } catch { return false; }
      }, 'PostgreSQL readiness');
      const authPort = await unusedPort(), perfPort = await unusedPort(), gatewayPort = await unusedPort();
      const hardwarePort = await unusedPort();
      const auth = `http://127.0.0.1:${authPort}`, perf = `http://127.0.0.1:${perfPort}`, gateway = `http://127.0.0.1:${gatewayPort}`;
      const hardware = `http://127.0.0.1:${hardwarePort}`;
      let erasureToken;
      const relay = await listen(async (incoming, outgoing) => {
        try {
          erasureToken = incoming.headers.authorization; secrets.push(erasureToken);
          const response = await fetch(perf + incoming.url, { method: incoming.method,
            headers: { Authorization: erasureToken }, redirect: 'manual', signal: AbortSignal.timeout(5000) });
          outgoing.writeHead(response.status); outgoing.end();
        } catch { outgoing.writeHead(503); outgoing.end(); }
      });
      const unavailable = await listen((_, response) => { response.writeHead(503); response.end(); });
      listeners.push(relay.server, unavailable.server);
      const dbProperties = db => ({ POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(db.ports[5432]),
        POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', DB_PERFORMANCE_NAME: 'fixture',
        DB_HARDWARE_NAME: 'fixture' });
      const authEnv = { ...dbProperties(authDb), SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey,
        AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gateway,
        FRONTEND_PUBLIC_URL: gateway, JWT_ACCESS_TTL_SECONDS: '60', AUTH_DELETION_GRACE_PERIOD: 'PT0S',
        AUTH_DELETION_POLL_INTERVAL: 'PT1S', AUTH_DELETION_INITIAL_BACKOFF: 'PT1S', AUTH_DELETION_MAX_BACKOFF: 'PT2S',
        AUTH_DELETION_HTTP_TIMEOUT: 'PT1S', AUTH_HARDWARE_SERVICE_CLIENT_SECRET: clientSecret,
        AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET: referenceClientSecret,
        LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: String(mail.ports[1025]), PERFORMANCE_SERVICE_URL: relay.origin };
      for (const name of ['USER', 'MAIL', 'GAME', 'LIBRARY', 'REVIEW', 'SOCIAL', 'MESSAGING', 'LFP', 'COMMUNITY', 'CONTENT', 'EVENT', 'HARDWARE', 'RECOMMENDATION', 'ADMIN', 'NOTIFICATION']) {
        authEnv[`${name}_SERVICE_URL`] = unavailable.origin;
      }
      await launch('auth-service', authPort, authEnv);
      await launch('performance-service', perfPort, { ...dbProperties(performanceDb), AUTH_SERVICE_URL: auth,
        GAME_SERVICE_URL: unavailable.origin, HARDWARE_SERVICE_URL: hardware });
      await launch('hardware-service', hardwarePort, { ...dbProperties(hardwareDb), AUTH_SERVICE_URL: auth,
        USER_SERVICE_URL: unavailable.origin, PERFORMANCE_SERVICE_URL: perf, AUTH_HARDWARE_SERVICE_CLIENT_SECRET: clientSecret });
      await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: auth, PERFORMANCE_SERVICE_URL: perf,
        HARDWARE_SERVICE_URL: hardware, CORS_ALLOWED_ORIGINS: gateway });
      check(await sql(performanceDb, 'SELECT count(*) FROM flyway_schema_history WHERE success') === '3', 'Performance Flyway V21-V23 and JPA validation');
      const mailOrigin = `http://127.0.0.1:${mail.ports[8025]}`;
      const message = (email, pattern) => until(async () => {
        const result = await (await fetch(mailOrigin + '/api/v1/search?query=' + encodeURIComponent('to:' + email))).json();
        for (const summary of result.messages || []) {
          const detail = await (await fetch(mailOrigin + '/api/v1/message/' + summary.ID)).json();
          const match = String(detail.Text).match(pattern);
          if (match) { secrets.push(match[1]); return match[1]; }
        }
        return false;
      }, 'SMTP delivery', 20000);
      const register = async () => {
        const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `perf_${suffix}@example.test`, username = `p_${suffix}`;
        secrets.push(email);
        await request(gateway, '/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
        const code = await message(email, /\b(\d{6})\b/);
        await request(gateway, '/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
        const jar = new CookieJar();
        await request(gateway, '/api/v1/auth/csrf', 204, { jar });
        await request(gateway, '/api/v1/auth/login', 200, { jar, method: 'POST', body: { identifier: email, password } });
        await request(gateway, '/api/v1/auth/csrf', 204, { jar });
        const token = jar.get('__Host-ltz_access'); secrets.push(token);
        return { jar, email, username, token, id: JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).sub };
      };
      const owner = await register(), other = await register();
      const devices = '/api/v1/performance/devices', consent = '/api/v1/performance/sync-consent';
      const batches = '/api/v1/performance/sync/batches', authDevices = '/api/v1/auth/devices';
      await request(gateway, devices, 401);
      await request(gateway, consent, 200, { jar: owner.jar, method: 'PUT' });
      await request(gateway, consent, 200, { jar: other.jar, method: 'PUT' });
      check(true, 'Real SMTP registration, login, Gateway cookie conversion and owner consent');
      const device = await request(gateway, authDevices, 201, { jar: owner.jar, method: 'POST', body: { label: 'Performance fixture', password } });
      secrets.push(device.deviceSecret);
      const credentials = { deviceId: device.deviceId, deviceSecret: device.deviceSecret };
      const profile = await request(gateway, '/api/v1/hardware/profiles', 201,
        { jar: owner.jar, method: 'POST', body: { name: 'Hardware integration fixture', deviceType: 'DESKTOP' } });
      const hardwareConsent = `/api/v1/hardware/profiles/${profile.id}/sync-consent`;
      const grant = { profileVersion: profile.version, deviceId: device.deviceId, accepted: true };
      await request(gateway, hardwareConsent, 404, { jar: owner.jar, method: 'PUT', body: grant });
      check(await sql(hardwareDb, 'SELECT count(*) FROM hardware_sync_consents') === '0',
        'Hardware obtains an account-bound Auth token and denies a device not linked in Performance');
      const link = await request(gateway, devices, 200, { jar: owner.jar, method: 'POST', body: { deviceId: device.deviceId } });
      await request(gateway, devices, 404, { jar: other.jar, method: 'POST', body: { deviceId: device.deviceId } });
      check(link.status === 'ACTIVE', 'Explicit link checks actual Auth device ownership; foreign account gets 404');
      const granted = await request(gateway, hardwareConsent, 200, { jar: owner.jar, method: 'PUT', body: grant });
      await request(gateway, hardwareConsent, 404, { jar: other.jar, method: 'PUT', body: grant });
      check(granted.status === 'GRANTED', 'Real Hardware consumer accepts the Performance active-device decision for its owner');
      const issued = await request(gateway, authDevices + '/token', 200, { method: 'POST', body: { ...credentials, target: 'performance' } });
      secrets.push(issued.accessToken);
      check(issued.scope === 'performance:sync:write' && issued.expiresIn === 60, 'Auth target=performance issues the agreed device token');
      const tokenHeaders = { Authorization: `Bearer ${issued.accessToken}`, 'Idempotency-Key': randomUUID() };
      const batch = { schemaVersion: 1, clientSentAt: new Date().toISOString(), sampleCount: 1 };
      const first = await request(gateway, batches, 200, { method: 'POST', headers: tokenHeaders, body: batch });
      const replay = await request(gateway, batches, 200, { method: 'POST', headers: tokenHeaders, body: batch });
      check(!first.replayed && replay.replayed && first.batchId === replay.batchId, 'Actual device token accepts sync and idempotent replay');
      const hardwareToken = await request(gateway, authDevices + '/token', 200, { method: 'POST', body: credentials });
      secrets.push(hardwareToken.accessToken);
      const snapshots = `/api/v1/hardware/profiles/${profile.id}/sync/snapshots`;
      const snapshotHeaders = { Authorization: `Bearer ${hardwareToken.accessToken}`, 'Idempotency-Key': randomUUID() };
      const snapshot = { profileVersion: granted.profileVersion, consentGeneration: granted.generation, sequence: 1,
        scan: { schemaVersion: 1, capturedAt: new Date().toISOString(),
          components: [{ componentKey: randomUUID(), category: 'CPU', modelName: 'Integration CPU', quantity: 1 }] } };
      const applied = await request(gateway, snapshots, 200, { method: 'POST', headers: snapshotHeaders, body: snapshot });
      const repeated = await request(gateway, snapshots, 200, { method: 'POST', headers: snapshotHeaders, body: snapshot });
      await request(gateway, snapshots, 403, { jar: owner.jar, method: 'POST', body: snapshot,
        headers: { 'Idempotency-Key': randomUUID() } });
      check(applied.created === 1 && applied.sequence === 1 && repeated.profileVersion === applied.profileVersion,
        'Real Auth device identity and Hardware-to-Performance checks enable consent, snapshot and idempotent retry');
      await request(gateway, batches, 401, { method: 'POST', body: batch, headers: { ...tokenHeaders, Authorization: `Bearer ${hardwareToken.accessToken}` } });
      await request(gateway, devices, 401, { headers: { Authorization: `Bearer ${issued.accessToken}` } });
      check(true, 'Hardware device audience and device identity on user routes are rejected');
      const serviceRequest = { clientId: 'hardware-service', clientSecret, audience: 'performance-service', accountId: owner.id };
      await request(auth, '/internal/v1/service-tokens', 400, { method: 'POST', body: { ...serviceRequest, accountId: undefined } });
      const service = await request(auth, '/internal/v1/service-tokens', 200, { method: 'POST', body: serviceRequest });
      secrets.push(service.accessToken);
      const serviceHeaders = { Authorization: `Bearer ${service.accessToken}` };
      const verifyPath = `/internal/v1/accounts/${owner.id}/devices/${device.deviceId}`;
      await request(perf, verifyPath, 204, { headers: serviceHeaders });
      await request(perf, `/internal/v1/accounts/${other.id}/devices/${device.deviceId}`, 403, { headers: serviceHeaders });
      const summaryPath = `/internal/v1/accounts/${owner.id}/performance-summary?hardwareProfileId=${randomUUID()}`;
      const referencePath = `/internal/v1/accounts/${owner.id}/games/${randomUUID()}/performance-reference`;
      const summary = await request(perf, summaryPath, 200, { headers: serviceHeaders });
      await request(perf, referencePath, 403, { headers: serviceHeaders });
      await request(perf, `/internal/v1/accounts/${owner.id}`, 403, { method: 'DELETE', headers: serviceHeaders });
      check(service.scope === 'devices:verify summary:read' && summary.sessionCount === 0,
        'Actual Hardware token verifies devices and reads its account summary; reference and erasure remain forbidden');
      const referenceRequest = { ...serviceRequest, clientId: 'recommendation-service', clientSecret: referenceClientSecret };
      await request(auth, '/internal/v1/service-tokens', 400, { method: 'POST', body: { ...referenceRequest, accountId: undefined } });
      const referenceToken = await request(auth, '/internal/v1/service-tokens', 200, { method: 'POST', body: referenceRequest });
      secrets.push(referenceToken.accessToken);
      const referenceHeaders = { Authorization: `Bearer ${referenceToken.accessToken}` };
      const reference = await request(perf, referencePath, 200, { headers: referenceHeaders });
      check(referenceToken.scope === 'reference:read' && reference.fpsSampleCount === 0 && reference.reliable === false,
        'Actual Recommendation token reads an empty performance reference without inventing FPS data');
      await request(perf, summaryPath, 403, { headers: referenceHeaders });
      await request(perf, verifyPath, 403, { headers: referenceHeaders });
      await request(perf, referencePath.replace(owner.id, other.id), 403, { headers: referenceHeaders });
      await request(perf, `/internal/v1/accounts/${owner.id}`, 403, { method: 'DELETE', headers: referenceHeaders });
      check(true, 'Recommendation cannot read Hardware summaries, verify devices, cross accounts or erase data');
      await request(gateway, `${authDevices}/${device.deviceId}`, 204, { jar: owner.jar, method: 'DELETE' });
      await request(gateway, authDevices + '/token', 401, { method: 'POST', body: { ...credentials, target: 'performance' } });
      const reconciled = await request(gateway, devices, 200, { jar: owner.jar });
      await request(gateway, batches, 403, { method: 'POST', headers: { ...tokenHeaders, 'Idempotency-Key': randomUUID() }, body: batch });
      await request(perf, verifyPath, 404, { headers: serviceHeaders });
      check(reconciled[0].status === 'DISABLED', 'Auth revocation prevents issuance; list refresh disables link and blocks an unexpired token');
      await request(gateway, snapshots, 404, { method: 'POST', headers: snapshotHeaders, body: snapshot });
      check(true, 'Hardware rechecks Performance before replay and rejects the now-disabled device without a cached allow');
      await request(gateway, '/api/v1/auth/account/deletion/request', 202, { jar: owner.jar, method: 'POST', body: { password } });
      const deletionToken = await message(owner.email, /#token=([A-Za-z0-9_-]+)/);
      await request(gateway, '/api/v1/auth/account/deletion/confirm', 202, { jar: owner.jar, method: 'POST', body: { token: deletionToken, username: owner.username } });
      await until(async () => await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE service='performance' AND status='CONFIRMED'") === '1', 'Auth Performance deletion ACK', 30000);
      for (const table of ['sync_consents', 'device_links', 'sync_batches']) {
        check(await sql(performanceDb, `SELECT count(*) FROM ${table} WHERE user_id='${owner.id}'`) === '0', `D4 removes ${table} for the target account`);
      }
      await request(perf, `/internal/v1/accounts/${owner.id}`, 204, { method: 'DELETE', headers: { Authorization: erasureToken } });
      await request(perf, `/internal/v1/accounts/${other.id}`, 403, { method: 'DELETE', headers: { Authorization: erasureToken } });
      await request(gateway, consent, 200, { jar: other.jar });
      await request(gateway, consent, 401, { method: 'PUT', headers: { Authorization: `Bearer ${owner.token}` } });
      check(true, 'Actual worker D4 is idempotent, account-bound, preserves another account and blocks resurrection');
      check(await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='PENDING'") === '15', 'Other 15 deletion targets stay pending without fake ACKs');
      // Bu fixture D4 için yalnız Performance'ı hedefler; Auth'un Hardware silme adresi unavailable kalır.
      log(`PERFORMANCE AUTH RUNTIME E2E PASS: ${checks} checks; Hardware sync consumer VERIFIED; global Saga/User/Game NOT_VERIFIED`);
      return { checks, status: 'PASS' };
    } catch (error) { throw new Error(scrub(error.message, secrets)); }
    finally {
      for (const { child } of children.reverse()) {
        if (child.exitCode === null && child.pid) {
          child.kill(); await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
          if (child.exitCode === null) child.kill('SIGKILL');
        }
      }
      for (const server of listeners) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
      for (const id of containers.reverse()) {
        const actual = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.performance-auth-e2e" }}', id]);
        if (actual !== label) throw new Error('Refusing cleanup outside this fixture');
        await docker(['rm', '-f', '-v', id]);
      }
      log(`[CLEANUP] ${children.length} fixture processes and ${containers.length} owned containers removed`);
    }
  }

  return { runPerformanceAuthE2e };
})();
// #endregion e2e-performance

// #region e2e-game-review
// Gerçek Auth servis token'ı → gerçek Review → Game projeksiyonu; yalnız geçici kaynaklar.
const gameReviewE2e = (() => {
  const { isolatedEnvironment, scrub, captureCommand, requireFreshJar } = hardwareE2e;

  function fixtureToken(privateKey, kid, subject, extra = {}, type = 'at+jwt') {
    const now = Math.floor(Date.now() / 1000);
    const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
    const unsigned = encode({ alg: 'RS256', typ: type, kid }) + '.' + encode({
      iss: 'ltz-auth', aud: ['ltz-api'], sub: subject, iat: now, nbf: now, exp: now + 60, jti: randomUUID(), ...extra,
    });
    return unsigned + '.' + sign('RSA-SHA256', Buffer.from(unsigned), privateKey).toString('base64url');
  }

  async function waitFor(action, label, timeout = 90000) {
    const deadline = Date.now() + timeout;
    do {
      const result = await action();
      if (result) return result;
      await delay(250);
    } while (Date.now() < deadline);
    throw new Error(`Timeout: ${label}`);
  }

  async function freePort() {
    const listener = createServer();
    await new Promise((resolve, reject) => { listener.once('error', reject); listener.listen(0, '127.0.0.1', resolve); });
    const port = listener.address().port;
    await new Promise(resolve => listener.close(resolve));
    return port;
  }

  async function runGameReviewE2e(root, log = console.log) {
    for (const service of ['auth-service', 'game-service', 'review-service']) requireFreshJar(root, service);
    const owned = [], processes = [], secrets = [];
    const label = randomUUID();
    let checks = 0;
    const check = (condition, name) => {
      if (!condition) throw new Error(`FAIL: ${name}`);
      checks++; log(`[PASS] ${name}`);
    };
    const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, {
      cwd: root, env: isolatedEnvironment(extra), windowsHide: true,
    }), binary, secrets);
    const docker = (args, extra) => command('docker', args, extra);
    const database = async () => {
      const password = randomBytes(32).toString('base64url');
      secrets.push(password);
      const id = await docker(['run', '-d', '--label', `ltz.game-review-e2e=${label}`, '-p', '127.0.0.1::5432',
        '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=fixture', '-e', 'POSTGRES_DB=fixture', 'postgres:17-alpine'],
      { POSTGRES_PASSWORD: password });
      if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
      owned.push(id);
      const address = await docker(['port', id, '5432/tcp']);
      if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Disposable database must be loopback only');
      await waitFor(async () => {
        try { return await docker(['exec', id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', 'SELECT 1']) === '1'; }
        catch { return false; }
      }, 'disposable PostgreSQL readiness');
      return { POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: address.split(':')[1], POSTGRES_USER: 'fixture',
        POSTGRES_PASSWORD: password, DB_AUTH_NAME: 'fixture', DB_GAME_NAME: 'fixture', DB_REVIEW_NAME: 'fixture' };
    };
    const launch = async (service, port, env, args = []) => {
      const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      const child = spawn(java, ['-Xmx384m', '-jar', requireFreshJar(root, service), '--server.address=127.0.0.1',
        `--server.port=${port}`, '--management.health.mail.enabled=false', ...args], {
        cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'WARN', ...env }), windowsHide: true,
      });
      const running = { child, output: '', failed: false };
      processes.push(running);
      child.once('error', () => { running.failed = true; });
      for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
        running.output = (running.output + scrub(data.toString(), secrets)).slice(-6000);
      });
      const origin = `http://127.0.0.1:${port}`;
      await waitFor(async () => {
        if (running.failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${running.output}`);
        try { return (await fetch(origin + '/actuator/health', { signal: AbortSignal.timeout(2000) })).status === 200; }
        catch { return false; }
      }, `${service} readiness`);
      return { origin, child };
    };
    const stop = async child => {
      if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
      const closed = new Promise(resolve => child.once('exit', resolve));
      if (process.platform === 'win32') {
        // Windows JDK başlatıcısı ikinci bir JVM açabilir; yalnız parent'ı öldürmek uygulamayı açık bırakır.
        await command('taskkill', ['/PID', String(child.pid), '/T', '/F']);
        await closed;
        return;
      }
      child.kill();
      await Promise.race([closed, delay(5000)]);
      if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await closed; }
    };
    const request = async (origin, route, expected, { method = 'GET', body, token } = {}) => {
      const headers = { 'Content-Type': 'application/json' };
      if (token) headers.Authorization = `Bearer ${token}`;
      const response = await fetch(origin + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        redirect: 'manual', signal: AbortSignal.timeout(6000) });
      if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
      const text = await response.text();
      return text ? JSON.parse(text) : undefined;
    };
    try {
      const authDb = await database(), gameDb = await database(), reviewDb = await database();
      const keys = generateKeyPairSync('rsa', { modulusLength: 3072 });
      const encodedKey = keys.privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
      const clientSecret = randomBytes(32).toString('base64url');
      const otpKey = randomBytes(32).toString('base64'), totpKey = randomBytes(32).toString('base64');
      secrets.push(encodedKey, clientSecret, otpKey, totpKey);
      const authPort = await freePort(), gamePort = await freePort(), reviewPort = await freePort();
      const gameOrigin = `http://127.0.0.1:${gamePort}`, reviewOrigin = `http://127.0.0.1:${reviewPort}`;
      const auth = await launch('auth-service', authPort, { ...authDb, SPRING_PROFILES_ACTIVE: 'dev',
        AUTH_JWT_PRIVATE_KEY: encodedKey, AUTH_OTP_HMAC_KEY: otpKey,
        AUTH_TOTP_ENCRYPTION_KEY: totpKey, AUTH_GAME_SERVICE_CLIENT_SECRET: clientSecret,
        GAME_SERVICE_URL: gameOrigin, REVIEW_SERVICE_URL: reviewOrigin });
      const review = await launch('review-service', reviewPort, { ...reviewDb, AUTH_SERVICE_URL: auth.origin, GAME_SERVICE_URL: gameOrigin });
      const game = await launch('game-service', gamePort, { ...gameDb, AUTH_SERVICE_URL: auth.origin,
        REVIEW_SERVICE_URL: reviewOrigin, AUTH_GAME_SERVICE_CLIENT_SECRET: clientSecret },
      ['--ltz.game.rating.sync.interval=PT1S', '--ltz.game.rating.sync.initial-delay=PT1S', '--ltz.game.rating.page-size=2']);

      const jwks = await request(auth.origin, '/api/v1/auth/.well-known/jwks.json', 200);
      const kid = jwks.keys.find(key => key.kty === 'RSA').kid;
      // Kullanıcı/admin kimlikleri yalnız bu fixture'ın geçici anahtarıyla imzalanır; gerçek login kanıtı değildir.
      // Servis token'ı ise aşağıda ve Game'in kendi istemcisinde GERÇEK Auth HTTP üreticisinden alınır.
      const user1 = randomUUID(), user2 = randomUUID(), admin = randomUUID();
      const userToken = user => fixtureToken(keys.privateKey, kid, user, { roles: ['PLAYER'] });
      const adminToken = () => fixtureToken(keys.privateKey, kid, admin, { roles: ['ADMIN'] });
      const service = await request(auth.origin, '/internal/v1/service-tokens', 200, { method: 'POST',
        body: { clientId: 'game-service', clientSecret, audience: 'review-service' } });
      secrets.push(service.accessToken);
      check(service.scope.split(' ').includes('rating-summaries:read') && service.scope.split(' ').includes('game-references:read'),
        'Real Auth issues the registered two-scope Game → Review token');
      await request(review.origin, '/internal/v1/games/rating-summaries?limit=2', 200, { token: service.accessToken });
      await request(review.origin, '/internal/v1/games/rating-summaries', 401, { token: userToken(user1) });
      check(true, 'Real Review accepts the Auth service token and rejects a user token');

      const ids = [];
      for (const title of ['Fixture Alpha', 'Fixture Bravo', 'Fixture Charlie', 'Fixture Delta']) {
        const created = await request(game.origin, '/api/v1/game/admin/games', 201, { method: 'POST', token: adminToken(),
          body: { title, gameType: 'MAIN_GAME' } });
        ids.push(created.gameId);
      }
      const rating = (user, id, value) => request(review.origin, `/api/v1/review/games/${id}/rating`, 200,
        { method: 'PUT', token: userToken(user), body: { rating: value } });
      await rating(user1, ids[0], 9); await rating(user2, ids[0], 7);
      await rating(user1, ids[1], 3); await rating(user1, ids[2], 7);
      const list = query => request(game.origin, '/api/v1/game/games?size=10' + (query ? '&' + query : ''), 200);
      const item = (page, id) => page.items.find(value => value.game.gameId === id);
      await waitFor(async () => {
        const page = await list();
        return item(page, ids[0])?.rating.averageRating === 8 && item(page, ids[2])?.rating.averageRating === 7;
      }, 'rated multi-page projection', 30000);
      const current = await list('sort=RATING&direction=DESC');
      check(current.ratingsSyncedAt && current.items.map(value => value.game.gameId).join() === [ids[0], ids[2], ids[1], ids[3]].join(),
        'Real Review ratings reach Game with stable descending order and unrated last');
      check(item(current, ids[0]).rating.ratingCount === 2 && item(current, ids[3]).rating.status === 'UNRATED'
        && item(current, ids[3]).rating.averageRating === null, 'Average/count and verified unrated semantics');
      const filtered = await list('minRating=7&sort=RATING&direction=DESC');
      check(filtered.totalElements === 2 && filtered.items.length === 2, 'minRating filters the entire local catalog');
      const seen = [];
      for (let page = 0; page < 4; page++) {
        const result = await request(game.origin, `/api/v1/game/games?size=1&page=${page}&sort=RATING&direction=ASC`, 200);
        seen.push(result.items[0].game.gameId);
      }
      check(seen.join() === [ids[1], ids[2], ids[0], ids[3]].join(), 'ASC pagination has no duplication and keeps unrated last');

      await rating(user1, ids[0], 5);
      await waitFor(async () => item(await list(), ids[0]).rating.averageRating === 6, 'updated average', 30000);
      check(item(await list(), ids[0]).rating.ratingCount === 2, 'Updating a rating changes the average without increasing count');
      const erase = user => request(review.origin, `/internal/v1/accounts/${user}`, 204, { method: 'DELETE',
        token: fixtureToken(keys.privateKey, kid, 'svc:auth-service', { aud: ['review-service'], scope: 'account:erase', account_id: user }, 'svc+jwt') });
      await erase(user1);
      await waitFor(async () => {
        const page = await list();
        return item(page, ids[0]).rating.averageRating === 7 && item(page, ids[1]).rating.status === 'UNRATED'
          && item(page, ids[2]).rating.status === 'UNRATED';
      }, 'account erasure reflected by complete scan', 30000);
      check(item(await list(), ids[0]).rating.ratingCount === 1, 'Review erasure updates counts and clears games missing from the next complete scan');
      await rating(user1, ids[0], 8).then(() => { throw new Error('Erased account unexpectedly wrote a rating'); }, error => {
        if (!error.message.endsWith('got 403')) throw error;
      });
      check(true, 'Review prevents erased account data from being recreated');

      await stop(review.child);
      await delay(4500); // Bir tam başarısız tarama ve sınırlı retry için süre.
      const stale = await list('minRating=7&sort=RATING&direction=DESC');
      check(stale.totalElements === 1 && item(stale, ids[0]).rating.averageRating === 7,
        'Review outage preserves the last usable projection and local filtering');
      const batch = await request(game.origin, '/api/v1/game/games/batch', 200, { method: 'POST', body: { gameIds: ids } });
      check(batch.items.length === 4, 'Batch cards remain available during Review outage');
      log(`GAME REVIEW RUNTIME E2E PASS: ${checks} checks; user login and Auth deletion orchestration are outside this fixture`);
      return { status: 'PASS', checks };
    } catch (error) {
      throw new Error(scrub(error.message, secrets));
    } finally {
      const cleanupFailures = [];
      for (const { child } of processes.reverse()) {
        try { await stop(child); } catch { cleanupFailures.push('fixture Java process'); }
      }
      for (const id of owned.reverse()) {
        try {
          const actual = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.game-review-e2e" }}', id]);
          if (actual !== label) throw new Error('Resource ownership mismatch');
          await docker(['rm', '-f', '-v', id]);
        } catch { cleanupFailures.push('owned fixture container'); }
      }
      if (cleanupFailures.length) throw new Error(`Cleanup failed: ${cleanupFailures.join(', ')}`);
      log(`[CLEANUP] ${processes.length} fixture processes and ${owned.length} owned containers removed`);
    }
  }

  return { fixtureToken, runGameReviewE2e };
})();
// #endregion e2e-game-review

// #region e2e-game-auth
// Gerçek Auth/Gateway/Game HTTP sözleşmesi; yalnız etiketli geçici DB, Mailpit ve sentetik hesaplar.
const gameAuthE2e = (() => {
  const { CookieJar, isolatedEnvironment, captureCommand, requireFreshJar, scrub } = hardwareE2e;

  async function until(action, name, timeout = 90000) {
    const deadline = Date.now() + timeout;
    do { const value = await action(); if (value) return value; await delay(250); } while (Date.now() < deadline);
    throw new Error(`Timeout: ${name}`);
  }
  async function freePort() {
    const server = createServer();
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const port = server.address().port;
    await new Promise(resolve => server.close(resolve));
    return port;
  }

  async function runGameAuthE2e(root, log = console.log) {
    for (const service of ['auth-service', 'game-service', 'api-gateway']) requireFreshJar(root, service);
    const label = randomUUID(), containers = [], children = [], secrets = [];
    let checks = 0;
    const check = (value, name) => { if (!value) throw new Error(`FAIL: ${name}`); checks++; log(`[PASS] ${name}`); };
    const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, { cwd: root, env: isolatedEnvironment(extra), windowsHide: true }), binary, secrets);
    const container = async (image, ports, env = {}) => {
      const args = ['run', '-d', '--label', `ltz.game-auth-e2e=${label}`];
      for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
      for (const name of Object.keys(env)) args.push('-e', name);
      const id = await command('docker', [...args, image], env);
      if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container');
      containers.push(id);
      const mapped = {};
      for (const port of ports) {
        const address = await command('docker', ['port', id, `${port}/tcp`]);
        if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Fixture must be loopback only');
        mapped[port] = address.split(':')[1];
      }
      return { id, ports: mapped };
    };
    const sql = async (db, query) => {
      const actual = await command('docker', ['inspect', '--format', '{{ index .Config.Labels "ltz.game-auth-e2e" }}', db.id]);
      if (actual !== label) throw new Error('Refusing SQL outside owned disposable fixture');
      return command('docker', ['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-v', 'ON_ERROR_STOP=1', '-tAc', query]);
    };
    const launch = async (service, port, extra) => {
      const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      const child = spawn(java, ['-Xmx384m', '-jar', requireFreshJar(root, service), '--server.address=127.0.0.1', `--server.port=${port}`, '--ltz.game.rating.sync.enabled=false'], { cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'WARN', ...extra }), windowsHide: true });
      children.push(child);
      let output = '', failed = false;
      child.once('error', () => { failed = true; });
      for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { output = (output + scrub(data.toString(), secrets)).slice(-6000); });
      await until(async () => {
        if (failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${output}`);
        try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; }
      }, service);
    };
    try {
      const password = randomBytes(32).toString('base64url'), dbPassword = randomBytes(32).toString('base64url');
      const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
      const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
      secrets.push(password, dbPassword, privateKey, hmac, totp);
      const dbEnv = { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword };
      const authDb = await container('postgres:17-alpine', [5432], dbEnv), gameDb = await container('postgres:17-alpine', [5432], dbEnv);
      const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
      for (const db of [authDb, gameDb]) await until(async () => { try { return await sql(db, 'SELECT 1') === '1'; } catch { return false; } }, 'PostgreSQL');
      const authPort = await freePort(), gamePort = await freePort(), gatewayPort = await freePort();
      const authOrigin = `http://127.0.0.1:${authPort}`, gameOrigin = `http://127.0.0.1:${gamePort}`, gatewayOrigin = `http://127.0.0.1:${gatewayPort}`;
      const properties = db => ({ POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: db.ports[5432], POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', DB_GAME_NAME: 'fixture' });
      await launch('auth-service', authPort, { ...properties(authDb), SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey, AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp, AUTH_PUBLIC_BASE_URL: gatewayOrigin, FRONTEND_PUBLIC_URL: gatewayOrigin, LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: mail.ports[1025] });
      await launch('game-service', gamePort, { ...properties(gameDb), AUTH_SERVICE_URL: authOrigin, GAME_CATALOG_SYNC_ENABLED: 'false' });
      await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: authOrigin, GAME_SERVICE_URL: gameOrigin, CORS_ALLOWED_ORIGINS: gatewayOrigin });
      const request = async (route, expected, { jar, method = 'GET', body, csrf = false } = {}) => {
        const headers = { Origin: gatewayOrigin };
        if (jar) headers.Cookie = jar.header(route);
        if (csrf && jar) headers['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN');
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const response = await fetch(gatewayOrigin + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(7000) });
        jar?.accept(response.headers);
        if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
        const text = await response.text();
        return { response, body: text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : undefined };
      };
      const register = async () => {
        const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `game_${suffix}@example.test`, username = `game_${suffix}`;
        secrets.push(email);
        await request('/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
        const code = await until(async () => {
          const messages = await (await fetch(`http://127.0.0.1:${mail.ports[8025]}/api/v1/search?query=${encodeURIComponent('to:' + email)}`)).json();
          for (const summary of messages.messages || []) {
            const detail = await (await fetch(`http://127.0.0.1:${mail.ports[8025]}/api/v1/message/${summary.ID}`)).json();
            const match = String(detail.Text).match(/\b(\d{6})\b/);
            if (match) { secrets.push(match[1]); return match[1]; }
          }
        }, 'Mailpit verification', 20000);
        await request('/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
        const jar = new CookieJar();
        await request('/api/v1/auth/csrf', 204, { jar });
        const login = await request('/api/v1/auth/login', 200, { jar, method: 'POST', csrf: true, body: { identifier: email, password } });
        secrets.push(jar.get('__Host-ltz_access'));
        check(login.body.status === 'AUTHENTICATED' && !login.body.accessToken && login.response.headers.getSetCookie().some(value => value.startsWith('__Host-ltz_access=') && /HttpOnly/i.test(value) && /Secure/i.test(value)), 'Gerçek kayıt, SMTP doğrulaması ve güvenli HttpOnly login çerezi');
        await request('/api/v1/auth/csrf', 204, { jar });
        const me = (await request('/api/v1/auth/me', 200, { jar })).body;
        check(typeof me.accountId === 'string' && me.roles.includes('PLAYER'), 'Game ekranının tükettiği gerçek /auth/me sözleşmesi');
        return jar;
      };
      const owner = await register(), other = await register(), gameId = randomUUID();
      await sql(gameDb, `INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES ('${gameId}','Game Auth fixture','game auth fixture','INTERNAL','MAIN_GAME','PARTIAL',now(),now())`);
      await request('/api/v1/game/feedback', 401);
      const body = { gameId, type: 'GENERAL', title: 'Fixture feedback', body: 'Only fixture owner should see this feedback.' };
      await request('/api/v1/game/feedback', 403, { jar: owner, method: 'POST', body });
      check(true, 'Anonim erişim 401; çerezli yazmada eksik CSRF 403');
      const created = (await request('/api/v1/game/feedback', 201, { jar: owner, method: 'POST', csrf: true, body })).body;
      check(Boolean(created.id), 'Gerçek Auth çerezi ve CSRF ile Gateway üzerinden Game yazması');
      const mine = (await request(`/api/v1/game/feedback?gameId=${gameId}`, 200, { jar: owner })).body;
      const theirs = (await request(`/api/v1/game/feedback?gameId=${gameId}`, 200, { jar: other })).body;
      check(mine.items.some(item => item.id === created.id) && theirs.totalElements === 0 && !theirs.items.length, 'İki gerçek oturum arasında kişisel geri bildirim sahipliği');
      await request('/api/v1/game/admin/games', 403, { jar: owner, method: 'POST', csrf: true, body: { title: 'Forbidden fixture', gameType: 'MAIN_GAME' } });
      await request('/api/v1/game/developer/games', 403, { jar: owner });
      check(true, 'Gerçek PLAYER oturumu admin ve geliştirici kaynaklarına erişemez');
      log(`[RESULT] ${checks} gerçek Auth/Gateway/Game kontrolü PASS; HTTP cookie jar kullanılır, tarayıcı HTTPS dağıtım kabulü değildir.`);
      return checks;
    } finally {
      const failures = [];
      for (const child of children.reverse()) {
        if (!child.pid || child.exitCode !== null || child.signalCode !== null) continue;
        try {
          const closed = new Promise(resolve => child.once('exit', resolve));
          if (process.platform === 'win32') await command('taskkill', ['/PID', String(child.pid), '/T', '/F']); else child.kill();
          await Promise.race([closed, delay(5000)]);
          if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await closed; }
        } catch (error) { failures.push(error); }
      }
      for (const id of containers.reverse()) {
        try {
          const actual = await command('docker', ['inspect', '--format', '{{ index .Config.Labels "ltz.game-auth-e2e" }}', id]);
          if (actual !== label) throw new Error('Refusing foreign container cleanup');
          await command('docker', ['rm', '-f', '-v', id]);
        } catch (error) { failures.push(error); }
      }
      if (failures.length) throw new AggregateError(failures, 'Game Auth fixture cleanup failed');
      log('[CLEANUP] Bu koşunun süreç ağaçları, PostgreSQL ve Mailpit konteynerleri temizlendi.');
    }
  }

  return { runGameAuthE2e };
})();
// #endregion e2e-game-auth

// #region e2e-game-community
// GAME-11: gerçek Game/Community/Gateway; yalnız geçici DB ve fixture JWKS/kullanıcı kimlikleri.
const gameCommunityE2e = (() => {
  const { isolatedEnvironment, captureCommand, requireFreshJar, scrub } = hardwareE2e;
  const { fixtureToken } = gameReviewE2e;

  async function until(action, label) {
    const deadline = Date.now() + 90000;
    while (Date.now() < deadline) {
      if (await action()) return;
      await delay(250);
    }
    throw new Error(`Timeout: ${label}`);
  }
  async function freePort() {
    const server = createServer();
    await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
    const port = server.address().port;
    await new Promise(resolve => server.close(resolve));
    return port;
  }

  async function runGameCommunityE2e(root, log = console.log) {
    for (const service of ['game-service', 'community-service', 'api-gateway']) requireFreshJar(root, service);
    const label = randomUUID(), owned = [], children = [], secrets = [];
    let checks = 0;
    const check = (condition, message) => {
      if (!condition) throw new Error(`FAIL: ${message}`);
      checks++; log(`[PASS] ${message}`);
    };
    const command = (binary, args, extra = {}) => captureCommand(spawn(binary, args, {
      cwd: root, env: isolatedEnvironment(extra), windowsHide: true,
    }), binary, secrets);
    const sql = (database, text) => command('docker', ['exec', database.id, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'fixture', '-d', 'fixture', '-tAc', text]);
    const database = async () => {
      const password = randomBytes(32).toString('base64url'); secrets.push(password);
      const id = await command('docker', ['run', '-d', '--label', `ltz.game-community-e2e=${label}`, '-p', '127.0.0.1::5432',
        '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=fixture', '-e', 'POSTGRES_DB=fixture', 'postgres:17-alpine'], { POSTGRES_PASSWORD: password });
      if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
      owned.push(id);
      const address = await command('docker', ['port', id, '5432/tcp']);
      if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Database must be loopback only');
      const db = { id, env: { POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: address.split(':')[1], POSTGRES_USER: 'fixture', POSTGRES_PASSWORD: password, DB_GAME_NAME: 'fixture', DB_COMMUNITY_NAME: 'fixture' } };
      await until(async () => { try { return await sql(db, 'select 1') === '1'; } catch { return false; } }, 'PostgreSQL');
      return db;
    };
    const stop = async child => {
      if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
      const closed = new Promise(resolve => child.once('exit', resolve));
      if (process.platform === 'win32') await command('taskkill', ['/PID', String(child.pid), '/T', '/F']);
      else child.kill();
      await Promise.race([closed, delay(5000)]);
      if (child.exitCode === null && child.signalCode === null) { child.kill('SIGKILL'); await closed; }
    };
    const launch = async (service, extra) => {
      const port = await freePort();
      const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      const child = spawn(java, ['-Xmx384m', '-jar', requireFreshJar(root, service), '--server.address=127.0.0.1', `--server.port=${port}`, '--ltz.game.rating.sync.enabled=false', '--spring.datasource.hikari.connection-timeout=1000'], {
        cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'WARN', ...extra }), windowsHide: true,
      });
      children.push(child);
      let output = '', failed = false;
      child.once('error', () => { failed = true; });
      for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { output = (output + scrub(data.toString(), secrets)).slice(-6000); });
      const origin = `http://127.0.0.1:${port}`;
      await until(async () => {
        if (failed || child.exitCode !== null) throw new Error(`${service} startup failed: ${output}`);
        try { return (await fetch(origin + '/actuator/health', { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; }
      }, service);
      return { origin, child, diagnostics: () => output };
    };
    const keys = generateKeyPairSync('rsa', { modulusLength: 3072 }), kid = randomUUID();
    const jwks = { keys: [{ ...keys.publicKey.export({ format: 'jwk' }), kid, use: 'sig', alg: 'RS256' }] };
    const identity = createServer((request, response) => {
      if (request.url !== '/api/v1/auth/.well-known/jwks.json') { response.writeHead(404).end(); return; }
      response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(jwks));
    });
    await new Promise(resolve => identity.listen(0, '127.0.0.1', resolve));
    const auth = { AUTH_SERVICE_URL: `http://127.0.0.1:${identity.address().port}` };
    const author = randomUUID(), viewer = randomUUID(), admin = randomUUID();
    const token = (subject, roles = ['PLAYER']) => fixtureToken(keys.privateKey, kid, subject, { roles });
    try {
      const gameDb = await database(), communityDb = await database();
      const game = await launch('game-service', { ...gameDb.env, ...auth });
      const community = await launch('community-service', { ...communityDb.env, ...auth, GAME_SERVICE_URL: game.origin });
      const gateway = await launch('api-gateway', { ...auth, GAME_SERVICE_URL: game.origin, COMMUNITY_SERVICE_URL: community.origin });
      const request = async (route, expected = 200, { body, method = 'GET', user, roles, cookie, bearer } = {}) => {
        const headers = { 'Content-Type': 'application/json' };
        if (user) {
          const jwt = token(user, roles);
          if (cookie) headers.Cookie = `ltz_access=${jwt}`; else headers.Authorization = `Bearer ${jwt}`;
        }
        if (bearer) headers.Authorization = `Bearer ${bearer}`;
        const response = await fetch(gateway.origin + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(7000) });
        if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
        const text = await response.text();
        return { data: text ? JSON.parse(text) : null, headers: response.headers };
      };
      const { data: created } = await request('/api/v1/game/admin/games', 201, { method: 'POST', user: admin, roles: ['ADMIN'], body: { title: 'GAME-11 Fixture', gameType: 'MAIN_GAME' } });
      const gameId = created.gameId;
      check(Boolean(gameId), 'Gateway → Game creates a canonical game with validated fixture identity');
      const base = `/api/v1/community/games/${gameId}/content`;
      const content = [];
      for (const type of ['GUIDES', 'EASTER_EGGS', 'EXPERIENCES']) {
        const { data: descriptor } = await request(`/api/v1/game/games/${gameId}/modules/${type}?contractVersion=2`);
        check(descriptor.state === 'EXTERNAL' && descriptor.contentSource.service === 'COMMUNITY', `${type}: Game provides the versioned Community source`);
        const { data: empty } = await request(descriptor.contentSource.listPath);
        check(empty.totalElements === 0 && empty.items.length === 0, `${type}: empty Community result is a successful collection`);
        const { data: post } = await request('/api/v1/community/posts', 201, { method: 'POST', user: author, body: { body: 'Fixture plain text <b>not HTML</b>', gameId, contentType: type, title: `Fixture ${type}` } });
        content.push(post.id);
        const { data: page, headers } = await request(descriptor.contentSource.listPath, 200, { user: viewer, cookie: true });
        check(page.totalElements === 1 && page.items[0].id === post.id && headers.get('cache-control')?.includes('no-store'), `${type}: Gateway cookie identity, typed content and no-store response`);
      }
      const detail = `${base}/${content[0]}`;
      check((await request(detail)).data.body.includes('<b>not HTML</b>'), 'Community detail preserves the plain text contract');
      await request(`/api/v1/community/games/${randomUUID()}/content/${content[0]}`, 404);
      await request(base + '?type=GUIDES', 401, { bearer: 'invalid-token' });
      check(true, 'Wrong-game detail is hidden; invalid user token is rejected');
      // Social/Admin canlı akışı değildir: yalnız geçici Community DB'sine yerel yansıma fixture'ı hazırlanır.
      await sql(communityDb, `insert into user_blocks(blocker_id,blocked_id,created_at) values ('${viewer}','${author}',now())`);
      const blocked = await request(base + '?type=GUIDES', 200, { user: viewer, cookie: true });
      await request(detail, 404, { user: viewer, cookie: true });
      check(blocked.data.totalElements === 0 && blocked.data.items.length === 0, 'Cookie viewer blocking filters list, count and detail together');
      check((await request(base + '?type=GUIDES')).data.totalElements === 1, 'Viewer-specific blocking is not shared with anonymous readers');
      await sql(communityDb, `update posts set status='HIDDEN' where id='${content[0]}'`);
      await request(detail, 404, { user: author, cookie: true });
      check((await request(base + '?type=GUIDES')).data.totalElements === 0, 'Hidden content leaves the list/count and is hidden from its author');
      await command('docker', ['stop', '--timeout', '3', communityDb.id]);
      const direct = await fetch(community.origin + base + '?type=GUIDES', { signal: AbortSignal.timeout(7000) });
      log(`[OUTAGE] Direct Community HTTP ${direct.status}; expected 503.`);
      if (direct.status !== 503) {
        const diagnostic = community.diagnostics();
        const cause = diagnostic.includes('Unable to rollback against JDBC Connection') ? 'Hibernate rollback failure after connection loss' : 'Inspect Community exception mapping';
        log('[CONTRACT GAP] ' + cause);
      }
      check(direct.status === 503, 'The first direct Community outage response must match the documented 503 contract');
      await request(base + '?type=GUIDES', 503);
      check(true, 'Community database outage is 503 through Gateway, never an empty list');
      log(`[RESULT] ${checks} GAME-11 checks passed; real Auth login, Social/Admin event delivery and production deployment are not covered.`);
      return checks;
    } finally {
      const failures = [];
      for (const child of children.reverse()) { try { await stop(child); } catch (error) { failures.push(error); } }
      await new Promise(resolve => identity.close(resolve));
      for (const id of owned.reverse()) {
        try {
          const actual = await command('docker', ['inspect', '--format', '{{index .Config.Labels "ltz.game-community-e2e"}}', id]);
          if (actual !== label) throw new Error('Refusing to delete an unowned container');
          await command('docker', ['rm', '-f', '-v', id]);
        } catch (error) { failures.push(error); }
      }
      if (failures.length) throw new AggregateError(failures, 'GAME-11 fixture cleanup failed');
      log('[CLEANUP] Fixture JVM trees and owned PostgreSQL containers removed.');
    }
  }

  return { runGameCommunityE2e };
})();
// #endregion e2e-game-community

// #region e2e-game-provider-pilot
// Opt-in gerçek sağlayıcı pilotu; DEV DB yok, yalnız seçili sağlayıcının anahtarları JVM'e aktarılır.
const pilotE2e = (() => {
  const { isolatedEnvironment, captureCommand } = hardwareE2e;

  function providerValues(text) {
    const result = {};
    for (const raw of text.replace(/^\uFEFF/, '').split(/\r?\n/)) {
      const match = raw.match(/^\s*(IGDB_CLIENT_ID|IGDB_CLIENT_SECRET|ITAD_API_KEY)\s*=\s*(.*?)\s*$/);
      if (!match) continue;
      let value = match[2];
      if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
      else value = value.replace(/\s+#.*$/, '').trim();
      result[match[1]] = value;
    }
    return result;
  }

  function pilotConfiguration(values, provider = 'igdb') {
    if (!['igdb', 'itad'].includes(provider)) throw new Error('Bilinmeyen pilot sağlayıcısı.');
    if (provider === 'igdb' && (!values.IGDB_CLIENT_ID || !values.IGDB_CLIENT_SECRET)) throw new Error('IGDB kimlik bilgileri eksik; pilot çalıştırılmadı.');
    if (provider === 'itad' && !values.ITAD_API_KEY) throw new Error('ITAD anahtarı eksik; pilot çalıştırılmadı.');
    return { test: provider === 'itad' ? 'ITGamePriceLivePilot' : 'ITGameProviderLivePilot',
      environment: { IGDB_CLIENT_ID: provider === 'igdb' ? values.IGDB_CLIENT_ID : '',
        IGDB_CLIENT_SECRET: provider === 'igdb' ? values.IGDB_CLIENT_SECRET : '',
        ITAD_API_KEY: provider === 'itad' ? values.ITAD_API_KEY : '',
        GAME_CATALOG_SYNC_ENABLED: 'false', MAVEN_SKIP_RC: 'true' } };
  }

  async function runProviderPilot(root, values, log = console.log, provider = 'igdb') {
    const configuration = pilotConfiguration(values, provider);
    const env = isolatedEnvironment(configuration.environment);
    const args = ['-o', '-B', '-f', 'backend/pom.xml', '-pl', 'services/game-service', `-Dtest=${configuration.test}`, '-Dltz.game.live-pilot=true', 'test'];
    const child = process.platform === 'win32'
      ? spawn(env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'mvn.cmd', ...args], { cwd: root, env, windowsHide: true })
      : spawn('mvn', args, { cwd: root, env });
    await captureCommand(child, `Game ${provider.toUpperCase()} pilot`, [values.IGDB_CLIENT_ID, values.IGDB_CLIENT_SECRET, values.ITAD_API_KEY]);
    log(`[PASS] Gerçek ${provider.toUpperCase()} → geçici PostgreSQL → Game public API; TTL tekrar okuması doğrulandı.`);
  }

  return { providerValues, pilotConfiguration, runProviderPilot };
})();
// #endregion e2e-game-provider-pilot

// #region e2e-auth
// Yalnız geçici kaynaklarla Auth + Gateway gerçek HTTP duman testi; gerçek ENV okunmaz.
// Gerekenler: Docker, JDK 25, güncel auth-service ve api-gateway jar'ları (backend kontrolünden sonra).
//   node ops/dev.mjs e2e auth
// Taviz yok: tek bir kontrol başarısız olursa betik hata koduyla biter. Üretilen anahtar ve parolalar loga yazılmaz.
const smokeE2e = (() => {
  const { CookieJar, captureCommand, isolatedEnvironment, requireFreshJar, scrub } = hardwareE2e;

  function decodeJwtPart(token, index) {
    return JSON.parse(Buffer.from(token.split('.')[index], 'base64url').toString('utf8'));
  }

  async function until(action, label, timeout = 90000) {
    const deadline = Date.now() + timeout;
    do {
      const result = await action();
      if (result) return result;
      await delay(300);
    } while (Date.now() < deadline);
    throw new Error(`Timeout: ${label}`);
  }

  async function listen(handler) {
    const server = createServer(handler);
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    return { server, port: server.address().port, origin: `http://127.0.0.1:${server.address().port}` };
  }

  async function unusedPort() {
    const listener = await listen((_, response) => response.end());
    await new Promise(resolve => listener.server.close(resolve));
    return listener.port;
  }

  async function runAuthSmoke(root, log = console.log) {
    const containers = [], children = [], listeners = [], secrets = [];
    const label = `auth-smoke-${randomUUID()}`;
    let checks = 0;
    const check = (condition, name) => {
      if (!condition) throw new Error(`FAIL: ${name}`);
      checks++; log(`[PASS] ${name}`);
    };
    const command = (binary, args, extra = {}) => {
      const child = spawn(binary, args, { cwd: root, env: isolatedEnvironment(extra), windowsHide: true });
      return captureCommand(child, binary, secrets);
    };
    const docker = (args, extra) => command('docker', args, extra);
    const container = async (image, ports, extra = {}) => {
      const args = ['run', '-d', '--label', `ltz.auth-smoke=${label}`];
      for (const port of ports) args.push('-p', `127.0.0.1::${port}`);
      for (const name of Object.keys(extra)) args.push('-e', name);
      args.push(image);
      const id = await docker(args, extra);
      if (!/^[a-f0-9]{64}$/.test(id)) throw new Error('Invalid disposable container ID');
      containers.push(id);
      const mapped = {};
      for (const port of ports) {
        const address = await docker(['port', id, `${port}/tcp`]);
        if (!/^127\.0\.0\.1:\d+$/.test(address)) throw new Error('Disposable port must be loopback only');
        mapped[port] = Number(address.split(':')[1]);
      }
      return { id, ports: mapped };
    };
    const sql = (db, query) => {
      if (!/^SELECT\s/i.test(query)) throw new Error('Only read-only fixture assertions are allowed');
      return docker(['exec', db.id, 'psql', '-U', 'fixture', '-d', 'fixture', '-tAc', query]);
    };
    const launch = async (service, port, extra) => {
      const jar = requireFreshJar(root, service);
      const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
      const child = spawn(java, ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${port}`], {
        cwd: root, env: isolatedEnvironment({ LOG_LEVEL: 'INFO', ...extra }), windowsHide: true,
      });
      const running = { child, output: '', service };
      children.push(running);
      for (const stream of [child.stdout, child.stderr]) stream.on('data', data => {
        running.output = (running.output + scrub(data.toString(), secrets)).slice(-48000);
      });
      child.once('error', () => { running.failed = true; });
      await until(async () => {
        if (running.failed || child.exitCode !== null) throw new Error(`${service} did not start: ${running.output.slice(-5000)}`);
        try { return (await fetch(`http://127.0.0.1:${port}/actuator/health`, { signal: AbortSignal.timeout(2000) })).status === 200; }
        catch { return false; }
      }, `${service} readiness`);
      return `http://127.0.0.1:${port}`;
    };
    const request = async (origin, route, expected, { jar, method = 'GET', body, headers = {}, csrf = false } = {}) => {
      const outgoing = { ...headers };
      if (jar) outgoing.Cookie = jar.header(route);
      if (csrf && jar) outgoing['X-XSRF-TOKEN'] = jar.get('__Host-XSRF-TOKEN');
      if (body !== undefined) outgoing['Content-Type'] = 'application/json';
      const response = await fetch(origin + route, { method, headers: outgoing,
        body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(6000) });
      jar?.accept(response.headers);
      if (response.status !== expected) throw new Error(`${method} ${route}: expected ${expected}, got ${response.status}`);
      const text = await response.text();
      return { response, body: text && response.headers.get('Content-Type')?.includes('json') ? JSON.parse(text) : text || undefined };
    };
    try {
      const password = randomBytes(32).toString('base64url');
      const dbPassword = randomBytes(32).toString('base64url');
      const adminSecret = randomBytes(32).toString('base64url'), socialSecret = randomBytes(32).toString('base64url');
      const messagingSecret = randomBytes(32).toString('base64url'), lfpSecret = randomBytes(32).toString('base64url');
      const librarySecret = randomBytes(32).toString('base64url'), hardwareSecret = randomBytes(32).toString('base64url');
      const reviewSecret = randomBytes(32).toString('base64url'), recommendationSecret = randomBytes(32).toString('base64url');
      const gameSecret = randomBytes(32).toString('base64url');
      const privateKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).privateKey.export({ format: 'der', type: 'pkcs8' }).toString('base64');
      const rotatedPublicKey = generateKeyPairSync('rsa', { modulusLength: 3072 }).publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
      const hmac = randomBytes(32).toString('base64'), totp = randomBytes(32).toString('base64');
      secrets.push(password, dbPassword, adminSecret, socialSecret, messagingSecret, lfpSecret, librarySecret, hardwareSecret,
        reviewSecret, recommendationSecret, gameSecret, privateKey, hmac, totp);
      const authDb = await container('postgres:17-alpine', [5432],
        { POSTGRES_USER: 'fixture', POSTGRES_DB: 'fixture', POSTGRES_PASSWORD: dbPassword });
      const mail = await container('axllent/mailpit:v1.31.0', [1025, 8025]);
      await until(async () => {
        try { return await sql(authDb, 'SELECT 1') === '1'; } catch { return false; }
      }, 'disposable PostgreSQL');
      const authPort = await unusedPort(), gatewayPort = await unusedPort();
      const authOrigin = `http://127.0.0.1:${authPort}`, gatewayOrigin = `http://127.0.0.1:${gatewayPort}`;
      const unavailable = await listen((_, response) => { response.writeHead(503); response.end(); });
      listeners.push(unavailable.server);
      const authEnvironment = { POSTGRES_HOST: '127.0.0.1', POSTGRES_PORT: String(authDb.ports[5432]), POSTGRES_USER: 'fixture',
        POSTGRES_PASSWORD: dbPassword, DB_AUTH_NAME: 'fixture', SPRING_PROFILES_ACTIVE: 'dev', AUTH_JWT_PRIVATE_KEY: privateKey,
        AUTH_JWT_ADDITIONAL_PUBLIC_KEYS: rotatedPublicKey, AUTH_OTP_HMAC_KEY: hmac, AUTH_TOTP_ENCRYPTION_KEY: totp,
        AUTH_ADMIN_SERVICE_CLIENT_SECRET: adminSecret, AUTH_SOCIAL_SERVICE_CLIENT_SECRET: socialSecret,
        AUTH_MESSAGING_SERVICE_CLIENT_SECRET: messagingSecret, AUTH_LFP_SERVICE_CLIENT_SECRET: lfpSecret,
        AUTH_LIBRARY_SERVICE_CLIENT_SECRET: librarySecret, AUTH_HARDWARE_SERVICE_CLIENT_SECRET: hardwareSecret,
        AUTH_REVIEW_SERVICE_CLIENT_SECRET: reviewSecret, AUTH_GAME_SERVICE_CLIENT_SECRET: gameSecret, AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET: recommendationSecret, AUTH_PUBLIC_BASE_URL: gatewayOrigin, FRONTEND_PUBLIC_URL: gatewayOrigin,
        LTZ_AUTH_MAIL_DEVSMTP_HOST: '127.0.0.1', LTZ_AUTH_MAIL_DEVSMTP_PORT: String(mail.ports[1025]) };
      for (const service of ['USER', 'MAIL', 'GAME', 'LIBRARY', 'REVIEW', 'SOCIAL', 'MESSAGING', 'LFP', 'COMMUNITY', 'CONTENT', 'EVENT',
        'HARDWARE', 'RECOMMENDATION', 'PERFORMANCE', 'ADMIN', 'NOTIFICATION']) {
        authEnvironment[`${service}_SERVICE_URL`] = unavailable.origin;
      }
      await launch('auth-service', authPort, authEnvironment);
      await launch('api-gateway', gatewayPort, { AUTH_SERVICE_URL: authOrigin, CORS_ALLOWED_ORIGINS: gatewayOrigin });
      check(await sql(authDb, 'SELECT count(*) FROM flyway_schema_history WHERE success') !== '0'
        && await sql(authDb, 'SELECT count(*) FROM flyway_schema_history WHERE NOT success') === '0',
      'Real Auth Flyway migrations applied cleanly and ddl-auto validation passed');

      const mailOrigin = `http://127.0.0.1:${mail.ports[8025]}`;
      const message = async (email, pattern) => until(async () => {
        const result = await (await fetch(mailOrigin + '/api/v1/search?query=' + encodeURIComponent('to:' + email))).json();
        for (const summary of result.messages || []) {
          const detail = await (await fetch(mailOrigin + '/api/v1/message/' + summary.ID)).json();
          const match = String(detail.Text).match(pattern);
          if (match) { secrets.push(match[1]); return match[1]; }
        }
        return false;
      }, 'Mailpit delivery', 20000);
      const postJson = (url, body) => fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body), redirect: 'manual', signal: AbortSignal.timeout(6000) });
      const loginAs = async (identifier, expected) => {
        const jar = new CookieJar();
        await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
        return request(gatewayOrigin, '/api/v1/auth/login', expected, { jar, method: 'POST', csrf: true,
          headers: { Origin: gatewayOrigin }, body: { identifier, password } });
      };
      // Erişim token'ı 60 sn yaşar; her hesap kayıttan hemen sonra kullanılır.
      const register = async () => {
        const suffix = randomUUID().replaceAll('-', '').slice(0, 12), email = `smoke_${suffix}@example.test`, username = `sm_${suffix}`;
        secrets.push(email);
        await request(gatewayOrigin, '/api/v1/auth/register', 202, { method: 'POST', body: { email, username, password } });
        const code = await message(email, /\b(\d{6})\b/);
        await request(gatewayOrigin, '/api/v1/auth/email-verification/confirm', 204, { method: 'POST', body: { email, code, password } });
        const jar = new CookieJar();
        await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
        const result = await request(gatewayOrigin, '/api/v1/auth/login', 200, { jar, method: 'POST', csrf: true,
          headers: { Origin: gatewayOrigin }, body: { identifier: email, password } });
        const token = jar.get('__Host-ltz_access');
        secrets.push(token);
        check(Boolean(token) && result.response.headers.getSetCookie().some(c => c.startsWith('__Host-ltz_access=') && /HttpOnly/i.test(c) && /Secure/i.test(c))
          && result.body.status === 'AUTHENTICATED' && !result.body.accessToken,
        'Registration, SMTP verification and login set a secure HttpOnly cookie; no token in the body');
        await request(gatewayOrigin, '/api/v1/auth/csrf', 204, { jar });
        return { jar, token, email, username };
      };

      const account = await register();
      const header = decodeJwtPart(account.token, 0), claims = decodeJwtPart(account.token, 1);
      check(claims.exp - claims.iat === 60 && header.typ === 'at+jwt' && header.alg === 'RS256' && Boolean(header.kid),
        `Access token: TTL ${claims.exp - claims.iat}s, typ ${header.typ}, alg ${header.alg}`);

      const jwks = await request(gatewayOrigin, '/api/v1/auth/.well-known/jwks.json', 200);
      check(Array.isArray(jwks.body.keys) && jwks.body.keys.length === 2
        && new Set(jwks.body.keys.map(k => k.kid)).size === 2 && jwks.body.keys.some(k => k.kid === header.kid)
        && jwks.body.keys.every(k => !('d' in k) && !('p' in k) && !('q' in k) && !('dp' in k) && !('dq' in k) && !('qi' in k)),
      'JWKS publishes the signing key plus the rotated public key, public members only, through the Gateway');

      const weak = randomUUID().replaceAll('-', '').slice(0, 10);
      await request(gatewayOrigin, '/api/v1/auth/register', 400, { method: 'POST',
        body: { email: `weak_${weak}@example.test`, username: `wk_${weak}`, password: 'Abcdefg12345!x' } });
      check(true, 'A 14-character password is rejected at registration (policy minimum 15)');

      const viaGateway = await postJson(gatewayOrigin + '/internal/v1/service-tokens', { clientId: 'admin-service', clientSecret: adminSecret });
      check(viaGateway.status === 404, `/internal is not routed through the Gateway (status ${viaGateway.status})`);

      const tokenUrl = authOrigin + '/internal/v1/service-tokens';
      const wrong = await postJson(tokenUrl, { clientId: 'admin-service', clientSecret: 'x'.repeat(40) });
      check(wrong.status === 401, `A wrong client secret is rejected (status ${wrong.status})`);
      const issuedResponse = await postJson(tokenUrl, { clientId: 'admin-service', clientSecret: adminSecret });
      check(issuedResponse.status === 200 && /no-store/i.test(issuedResponse.headers.get('Cache-Control') || ''),
        'Valid client credentials issue a service token with Cache-Control no-store');
      const issued = await issuedResponse.json();
      const serviceToken = issued.accessToken ?? issued.access_token;
      secrets.push(serviceToken);
      const serviceHeader = decodeJwtPart(serviceToken, 0), service = decodeJwtPart(serviceToken, 1);
      check(serviceHeader.typ === 'svc+jwt' && [].concat(service.aud).includes('auth-service') && service.sub === 'svc:admin-service'
        && service.exp - service.iat === 60 && !String(service.scope).includes('account:erase'),
      `Service token: typ ${serviceHeader.typ}, aud ${[].concat(service.aud)}, sub ${service.sub}, TTL ${service.exp - service.iat}s, scope ${service.scope}`);
      await request(authOrigin, `/internal/v1/accounts/${claims.sub}`, 200, { headers: { Authorization: `Bearer ${serviceToken}` } });
      const userOnInternal = await fetch(authOrigin + `/internal/v1/accounts/${claims.sub}`, { headers: { Authorization: `Bearer ${account.token}` } });
      check(userOnInternal.status === 401, `The service token reads an account; a user access token cannot (status ${userOnInternal.status})`);

      // Servisler arası olay girişi: yalnız izinli (hedef, kapsam) çifti; hedefe bağlı token Auth'ta geçmez.
      const ingestResponse = await postJson(tokenUrl, { clientId: 'social-service', clientSecret: socialSecret, audience: 'community-service' });
      const ingestBody = await ingestResponse.json();
      const ingestToken = ingestBody.accessToken ?? ingestBody.access_token;
      secrets.push(ingestToken);
      const ingest = decodeJwtPart(ingestToken, 1);
      check(ingestResponse.status === 200 && [].concat(ingest.aud).join() === 'community-service' && ingest.sub === 'svc:social-service'
        && ingest.scope === 'events:ingest' && ingest.exp - ingest.iat === 60,
      `Event-ingest token: aud ${[].concat(ingest.aud)}, sub ${ingest.sub}, scope ${ingest.scope}, TTL ${ingest.exp - ingest.iat}s`);
      const notGranted = await postJson(tokenUrl, { clientId: 'social-service', clientSecret: socialSecret, audience: 'notification-service' });
      const authForSocial = await postJson(tokenUrl, { clientId: 'social-service', clientSecret: socialSecret });
      check(notGranted.status === 403 && authForSocial.status === 403,
        `A client gets no token outside its allow-list (other target ${notGranted.status}, Auth ${authForSocial.status})`);
      const ingestOnAuth = await fetch(authOrigin + `/internal/v1/accounts/${claims.sub}`, { headers: { Authorization: `Bearer ${ingestToken}` } });
      check(ingestOnAuth.status === 401, `A token bound to another service is rejected by Auth (status ${ingestOnAuth.status})`);

      // Engel sorgusu istemcileri: yalnız social-service hedefi ve blocks:check kapsamı.
      for (const [clientId, clientSecret] of [['messaging-service', messagingSecret], ['lfp-service', lfpSecret]]) {
        const blocksResponse = await postJson(tokenUrl, { clientId, clientSecret, audience: 'social-service' });
        const blocksBody = await blocksResponse.json();
        const blocksToken = blocksBody.accessToken ?? blocksBody.access_token;
        secrets.push(blocksToken);
        const blocks = decodeJwtPart(blocksToken, 1);
        check(blocksResponse.status === 200 && [].concat(blocks.aud).join() === 'social-service' && blocks.sub === `svc:${clientId}`
          && blocks.scope === 'blocks:check' && blocks.exp - blocks.iat === 60,
        `${clientId}: block-check token aud ${[].concat(blocks.aud)}, scope ${blocks.scope}, TTL ${blocks.exp - blocks.iat}s`);
        const other = await postJson(tokenUrl, { clientId, clientSecret, audience: 'community-service' });
        const noAudience = await postJson(tokenUrl, { clientId, clientSecret });
        check(other.status === 403 && noAudience.status === 403, `${clientId} gets nothing outside social-service (other ${other.status}, no audience ${noAudience.status})`);
        const internalCall = await fetch(authOrigin + `/internal/v1/accounts/${claims.sub}`, { headers: { Authorization: `Bearer ${blocksToken}` } });
        check(internalCall.status === 401, `${clientId} block-check token is rejected by Auth (status ${internalCall.status})`);
      }

      // User Service: gizlilik kararı ve özet olayı token'ları (kullanıcı bağı yok); recommendation -> library tek hesaba bağlı.
      const targetToken = async (body) => {
        const response = await postJson(tokenUrl, body);
        const json = await response.json();
        const value = json.accessToken ?? json.access_token;
        if (value) secrets.push(value);
        return { status: response.status, json, claims: value ? decodeJwtPart(value, 1) : {} };
      };
      for (const [clientId, clientSecret, audience, scope] of [['social-service', socialSecret, 'user-service', 'events:ingest privacy:read'],
        ['library-service', librarySecret, 'user-service', 'events:ingest privacy:read'],
        ['hardware-service', hardwareSecret, 'user-service', 'privacy:read'],
        ['review-service', reviewSecret, 'user-service', 'events:ingest']]) {
        const issuedTarget = await targetToken({ clientId, clientSecret, audience });
        check(issuedTarget.status === 200 && [].concat(issuedTarget.claims.aud).join() === audience && issuedTarget.claims.sub === `svc:${clientId}`
          && issuedTarget.claims.scope === scope && !('account_id' in issuedTarget.claims) && issuedTarget.claims.exp - issuedTarget.claims.iat === 60,
        `${clientId}: ${audience} token scope ${issuedTarget.claims.scope}, no account binding`);
      }
      const signals = await targetToken({ clientId: 'recommendation-service', clientSecret: recommendationSecret, audience: 'library-service', accountId: claims.sub });
      check(signals.status === 200 && [].concat(signals.claims.aud).join() === 'library-service' && signals.claims.scope === 'signals:read'
        && signals.claims.account_id === claims.sub && signals.claims.exp - signals.claims.iat === 60,
      `recommendation-service: signals:read token bound to one active account (TTL ${signals.claims.exp - signals.claims.iat}s)`);
      const unbound = await targetToken({ clientId: 'recommendation-service', clientSecret: recommendationSecret, audience: 'library-service' });
      const unknownAccount = await targetToken({ clientId: 'recommendation-service', clientSecret: recommendationSecret, audience: 'library-service', accountId: randomUUID() });
      check(unbound.status === 400 && unbound.json.code === 'SERVICE_ACCOUNT_BINDING_INVALID'
        && unknownAccount.status === 403 && unknownAccount.json.code === 'SERVICE_TARGET_ACCOUNT_UNAVAILABLE',
      `Account-bound scope needs an active account (missing ${unbound.status}, unknown ${unknownAccount.status})`);

      // Review ve Performance entegrasyonu: game -> review (özet okuma), hardware -> performance (cihaz doğrulama, hesaba bağlı).
      const rating = await targetToken({ clientId: 'game-service', clientSecret: gameSecret, audience: 'review-service' });
      check(rating.status === 200 && [].concat(rating.claims.aud).join() === 'review-service' && rating.claims.sub === 'svc:game-service'
        && rating.claims.scope === 'rating-summaries:read' && !('account_id' in rating.claims),
      `game-service: review-service token scope ${rating.claims.scope}, no account binding`);
      const deviceVerify = await targetToken({ clientId: 'hardware-service', clientSecret: hardwareSecret, audience: 'performance-service', accountId: claims.sub });
      const deviceVerifyUnbound = await targetToken({ clientId: 'hardware-service', clientSecret: hardwareSecret, audience: 'performance-service' });
      check(deviceVerify.status === 200 && [].concat(deviceVerify.claims.aud).join() === 'performance-service'
        && deviceVerify.claims.sub === 'svc:hardware-service' && deviceVerify.claims.scope === 'devices:verify'
        && deviceVerify.claims.account_id === claims.sub && deviceVerifyUnbound.status === 400,
      `hardware-service: devices:verify token bound to one active account (without account ${deviceVerifyUnbound.status})`);

      // Steam bağlama: oturum + yeniden doğrulama gerekir; Steam'e gerçek istek gitmez (yalnız yönlendirme adresi üretilir).
      const steamLinkWrong = await request(gatewayOrigin, '/api/v1/auth/oauth/steam/link/start', 400, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { password: 'x'.repeat(20) } });
      const steamLink = await request(gatewayOrigin, '/api/v1/auth/oauth/steam/link/start', 200, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { password } });
      const steamLinkUrl = new URL(steamLink.body.redirectUrl);
      check(steamLinkWrong.body.code === 'CURRENT_PASSWORD_INVALID' && steamLinkUrl.hostname === 'steamcommunity.com'
        && /\/api\/v1\/auth\/oauth\/steam\/link\/callback\?state=/.test(steamLinkUrl.searchParams.get('openid.return_to') || '')
        && /no-store/i.test(steamLink.response.headers.get('Cache-Control') || '')
        && steamLink.response.headers.getSetCookie().some(c => /ltz_oauth=/.test(c) && /HttpOnly/i.test(c)),
      'Steam link start needs the password, redirects to Steam with a link-specific return_to and an HttpOnly state cookie');
      // Kullanıcı tarafı Steam/Google kaldırma ucu yoktur (2026-11-05 kararı): bağlanan kimlik kalıcıdır.

      // Masaüstü cihaz kimliği: bağlama (yeniden doğrulama), cihaz token'ı, dar kapsam, iptal.
      await request(gatewayOrigin, '/api/v1/auth/devices', 400, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { label: 'Bad <name>', password } });
      await request(gatewayOrigin, '/api/v1/auth/devices', 400, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { label: 'Desk PC', password: 'x'.repeat(20) } });
      const bound = await request(gatewayOrigin, '/api/v1/auth/devices', 201, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { label: 'Desk PC', password } });
      const deviceSecret = bound.body.deviceSecret, deviceId = bound.body.deviceId;
      secrets.push(deviceSecret);
      check(/no-store/i.test(bound.response.headers.get('Cache-Control') || '') && Boolean(deviceId) && String(deviceSecret).length >= 43,
        'Device bind needs the password, returns the 256-bit secret once with Cache-Control no-store');
      const listed = await request(gatewayOrigin, '/api/v1/auth/devices', 200, { jar: account.jar });
      check(listed.body.length === 1 && listed.body[0].deviceId === deviceId && !JSON.stringify(listed.body).includes(deviceSecret),
        'Device list shows the device and never the secret');
      const wrongDevice = await postJson(authOrigin + '/api/v1/auth/devices/token', { deviceId, deviceSecret: 'x'.repeat(43) });
      const unknownDevice = await postJson(authOrigin + '/api/v1/auth/devices/token', { deviceId: randomUUID(), deviceSecret });
      check(wrongDevice.status === 401 && unknownDevice.status === 401
        && (await wrongDevice.text()) === (await unknownDevice.text()),
      `Wrong secret and unknown device give the same 401 (${wrongDevice.status}/${unknownDevice.status})`);
      const deviceResponse = await postJson(authOrigin + '/api/v1/auth/devices/token', { deviceId, deviceSecret });
      const deviceBody = await deviceResponse.json();
      const deviceToken = deviceBody.accessToken ?? deviceBody.access_token;
      secrets.push(deviceToken);
      const deviceHeader = decodeJwtPart(deviceToken, 0), deviceClaims = decodeJwtPart(deviceToken, 1);
      check(deviceResponse.status === 200 && /no-store/i.test(deviceResponse.headers.get('Cache-Control') || '')
        && deviceHeader.typ === 'dev+jwt' && [].concat(deviceClaims.aud).join() === 'hardware-service' && deviceClaims.sub === deviceId
        && deviceClaims.account_id === claims.sub && deviceClaims.scope === 'hardware:sync:write' && deviceClaims.exp - deviceClaims.iat === 60
        && !deviceClaims.roles,
      `Device token: typ ${deviceHeader.typ}, aud ${[].concat(deviceClaims.aud)}, scope ${deviceClaims.scope}, TTL ${deviceClaims.exp - deviceClaims.iat}s, no roles`);
      // Aynı cihaz, Performance için ayrı token alır: tek aud, tek kapsam; tanımsız hedef 400.
      const performanceResponse = await postJson(authOrigin + '/api/v1/auth/devices/token', { deviceId, deviceSecret, target: 'performance' });
      const performanceBody = await performanceResponse.json();
      const performanceToken = performanceBody.accessToken ?? performanceBody.access_token;
      if (performanceToken) secrets.push(performanceToken);
      const performanceClaims = performanceToken ? decodeJwtPart(performanceToken, 1) : {};
      const unknownTarget = await postJson(authOrigin + '/api/v1/auth/devices/token', { deviceId, deviceSecret, target: 'game' });
      check(performanceResponse.status === 200 && [].concat(performanceClaims.aud).join() === 'performance-service'
        && performanceClaims.scope === 'performance:sync:write' && performanceClaims.sub === deviceId
        && performanceClaims.account_id === claims.sub && performanceClaims.exp - performanceClaims.iat === 60 && unknownTarget.status === 400,
      `Device token for performance: aud ${[].concat(performanceClaims.aud)}, scope ${performanceClaims.scope}; unknown target ${unknownTarget.status}`);
      const deviceOnInternal = await fetch(authOrigin + `/internal/v1/accounts/${claims.sub}`, { headers: { Authorization: `Bearer ${deviceToken}` } });
      const deviceOnUserApi = await fetch(authOrigin + '/api/v1/auth/devices', { headers: { Authorization: `Bearer ${deviceToken}` } });
      check(deviceOnInternal.status === 401 && deviceOnUserApi.status === 401,
        `The device token opens nothing in Auth (internal ${deviceOnInternal.status}, user API ${deviceOnUserApi.status})`);
      await request(gatewayOrigin, `/api/v1/auth/devices/${deviceId}`, 204, { jar: account.jar, method: 'DELETE', csrf: true,
        headers: { Origin: gatewayOrigin } });
      const revoked = await postJson(authOrigin + '/api/v1/auth/devices/token', { deviceId, deviceSecret });
      check(revoked.status === 401, `A revoked device cannot get a new token (status ${revoked.status})`);
      const rebound = await request(gatewayOrigin, '/api/v1/auth/devices', 201, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { label: 'Laptop', password } });
      secrets.push(rebound.body.deviceSecret);

      const newEmail = `chg_${randomUUID().replaceAll('-', '').slice(0, 12)}@example.test`;
      secrets.push(newEmail);
      await request(gatewayOrigin, '/api/v1/auth/email/change', 202, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { newEmail, password } });
      const changeCode = await message(newEmail, /\b(\d{6})\b/);
      await request(gatewayOrigin, '/api/v1/auth/email/change/confirm', 204, { jar: account.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { code: changeCode } });
      await loginAs(account.email, 401);
      await loginAs(newEmail, 200);
      check(true, 'Email change: the code goes to the new address, the old address stops working, the new address signs in');
      const afterEmailChange = await postJson(authOrigin + '/api/v1/auth/devices/token',
        { deviceId: rebound.body.deviceId, deviceSecret: rebound.body.deviceSecret });
      check(afterEmailChange.status === 401, `Email change revokes bound devices (status ${afterEmailChange.status})`);
      const notice = await message(account.email, /(Bu işlemi siz yapmadıysanız)/);
      check(Boolean(notice), 'The old address receives the e-mail-changed security notification');

      const leaving = await register();
      await request(gatewayOrigin, '/api/v1/auth/account/deletion/request', 202, { jar: leaving.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { password } });
      const deletionToken = await message(leaving.email, /#token=([A-Za-z0-9_-]+)/);
      await request(gatewayOrigin, '/api/v1/auth/account/deletion/confirm', 202, { jar: leaving.jar, method: 'POST', csrf: true,
        headers: { Origin: gatewayOrigin }, body: { token: deletionToken, username: leaving.username } });
      await loginAs(leaving.email, 403);
      check(await sql(authDb, "SELECT count(*) FROM accounts WHERE status='DELETION_REQUESTED'") === '1'
        && await sql(authDb, "SELECT count(*) FROM account_deletion_tasks WHERE status='CONFIRMED'") === '0',
      'Deletion confirmed: the account is closed for sign-in during the grace period and no erasure has run yet');
      await request(gatewayOrigin, '/api/v1/auth/account/deletion/cancel', 200, { method: 'POST',
        body: { identifier: leaving.email, password } });
      await loginAs(leaving.email, 200);
      check(await sql(authDb, "SELECT count(*) FROM accounts WHERE status='DELETION_REQUESTED'") === '0',
        'Deletion cancelled inside the grace period: the account signs in again');

      log(`AUTH SMOKE PASS: ${checks} checks; other-service integration NOT_VERIFIED`);
      return { checks, status: 'PASS' };
    } catch (error) {
      throw new Error(scrub(error.message, secrets));
    } finally {
      for (const { child } of children.reverse()) {
        if (child.exitCode === null && child.pid) {
          child.kill();
          await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
          if (child.exitCode === null) child.kill('SIGKILL');
        }
      }
      for (const server of listeners) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
      for (const id of containers.reverse()) {
        const resourceLabel = await docker(['inspect', '--format', '{{ index .Config.Labels "ltz.auth-smoke" }}', id]);
        if (resourceLabel !== label) throw new Error('Refusing cleanup of a resource outside this fixture');
        await docker(['rm', '-f', '-v', id]);
      }
      log(`[CLEANUP] ${children.length} fixture processes and ${containers.length} owned containers removed`);
    }
  }

  return { decodeJwtPart, runAuthSmoke };
})();
// #endregion e2e-auth

export const { isolatedEnvironment, CookieJar, scrub, requireFreshJar, deletionFixtureEnvironment, deletionFixturePending, captureCommand, runHardwareAuthE2e } = hardwareE2e;
export const { runPerformanceAuthE2e } = performanceE2e;
export const { fixtureToken, runGameReviewE2e } = gameReviewE2e;
export const { runGameAuthE2e } = gameAuthE2e;
export const { runGameCommunityE2e } = gameCommunityE2e;
export const { providerValues, pilotConfiguration, runProviderPilot } = pilotE2e;
export const { decodeJwtPart, runAuthSmoke } = smokeE2e;

// #region e2e-library
// Yalnız mevcut ltz-dev: gerçek Auth/Gateway/Library/Game HTTP kabul testi.
// Parola/token/OTP yalnız bellektedir. ENV dosyaları okunmaz, Auth DB'sine SQL gönderilmez.
async function libraryPhase1E2e() {
  const { CookieJar } = hardwareE2e;
  const gateway = 'http://127.0.0.1:5080';
  const mailpit = 'http://127.0.0.1:8025';
  const origin = 'http://localhost:5001';
  const runId = randomUUID().replaceAll('-', '');
  const gameId = randomUUID();
  const invalidGame = randomUUID();
  const title = 'LTZ LIB phase1 test ' + runId;
  const accounts = [], rows = [], cleanup = [];
  let fixtureCreated = false, gameStopped = false, failure;

  function command(args, input) {
    const result = spawnSync('docker', args, { input, encoding: 'utf8', windowsHide: true, timeout: 60000 });
    if (result.status !== 0 || result.error) throw new Error('Docker operation failed: ' + args[0]);
    return result.stdout.trim();
  }

  function sql(database, query) {
    if (!['library_db', 'game_db'].includes(database)) throw new Error('Database scope refused');
    return command(['exec', '-i', 'ltz-dev-postgres-1', 'sh', '-c',
      'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d ' + database], query + '\n');
  }

  function check(name, expected, actual) {
    const pass = expected === actual;
    rows.push({ name, expected, actual, pass });
    console.log((pass ? 'PASS ' : 'FAIL ') + name + ': expected=' + expected + ', actual=' + actual);
    if (!pass) throw new Error('Assertion failed: ' + name);
  }

  async function request(name, route, expected, { account, method = 'GET', body, headers = {}, csrf = false, base = gateway } = {}) {
    const outgoing = { ...headers };
    if (account) outgoing.Cookie = account.jar.header(route);
    if (account && csrf) outgoing['X-XSRF-TOKEN'] = account.jar.get('__Host-XSRF-TOKEN') || account.jar.get('XSRF-TOKEN');
    if (body !== undefined) outgoing['Content-Type'] = 'application/json';
    const response = await fetch(base + route, { method, headers: outgoing, redirect: 'manual',
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
    account?.jar.accept(response.headers);
    check(name + ' HTTP', expected, response.status);
    const text = await response.text();
    return text && response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : undefined;
  }

  async function mailbox(account) {
    const response = await fetch(mailpit + '/api/v1/search?query=' + encodeURIComponent('to:' + account.email),
      { signal: AbortSignal.timeout(5000) });
    if (!response.ok) throw new Error('Local mailbox unavailable');
    return (await response.json()).messages || [];
  }

  async function mailValue(account, pattern) {
    for (let i = 0; i < 40; i++) {
      for (const summary of await mailbox(account)) {
        const response = await fetch(mailpit + '/api/v1/message/' + encodeURIComponent(summary.ID), { signal: AbortSignal.timeout(5000) });
        if (!response.ok) throw new Error('Test message unavailable');
        const detail = await response.json();
        const match = String(detail.Text).match(pattern);
        if (match) return match[1];
      }
      await delay(250);
    }
    throw new Error('Local test email was not delivered');
  }

  async function login(account) {
    account.jar = new CookieJar();
    await request(account.label + ' CSRF', '/api/v1/auth/csrf', 204, { account });
    await request(account.label + ' login', '/api/v1/auth/login', 200, { account, method: 'POST', csrf: true,
      headers: { Origin: origin }, body: { identifier: account.email, password: account.password } });
    const token = account.jar.get('__Host-ltz_access') || account.jar.get('ltz_access');
    if (!token) throw new Error('Missing test session cookie');
    account.token = token;
    account.id = JSON.parse(Buffer.from(token.split('.')[1], 'base64url')).sub;
    if (!/^[a-f0-9-]{36}$/i.test(account.id)) throw new Error('Invalid test account identity');
    await request(account.label + ' session CSRF', '/api/v1/auth/csrf', 204, { account });
  }

  async function register(label) {
    const suffix = randomUUID().replaceAll('-', '').slice(0, 12);
    const account = { label, email: 'lib_' + suffix + '@example.test', username: 'lib_' + suffix,
      password: randomBytes(36).toString('base64url'), jar: new CookieJar() };
    accounts.push(account);
    await request(label + ' register', '/api/v1/auth/register', 202, { method: 'POST',
      body: { email: account.email, username: account.username, password: account.password } });
    const code = await mailValue(account, /\b(\d{6})\b/);
    await request(label + ' email verification', '/api/v1/auth/email-verification/confirm', 204,
      { method: 'POST', body: { email: account.email, code, password: account.password } });
    await login(account);
    return account;
  }

  function state(account) {
    return JSON.parse(sql('library_db', `SELECT json_build_object('count',count(*),'favorite',bool_or(favorite),
    'completed',bool_or(completed)) FROM library_entries WHERE user_id='${account.id}' AND game_id='${gameId}';`));
  }

  async function mutate(account, method, suffix, expected = 200, body) {
    return request(account.label + ' ' + method + ' ' + (suffix || 'collect'), '/api/v1/library/entries' + suffix,
      expected, { account, method, body, csrf: true, headers: { Origin: origin } });
  }

  async function restoreGame() {
    if (gameStopped) { command(['start', 'ltz-dev-game-service-1']); gameStopped = false; }
    for (let i = 0; i < 90; i++) {
      try { if ((await fetch('http://127.0.0.1:5084/actuator/health', { signal: AbortSignal.timeout(1500) })).status === 200) return; }
      catch { /* yalnız readiness yeniden denemesi */ }
      await delay(500);
    }
    throw new Error('Game health did not recover');
  }

  async function cleanAccount(account) {
    if (!account.id) { cleanup.push(account.label + ': registration incomplete; manual account cleanup may be needed'); return; }
    await login(account);
    await request(account.label + ' cleanup request', '/api/v1/auth/account/deletion/request', 202,
      { account, method: 'POST', csrf: true, headers: { Origin: origin }, body: { password: account.password } });
    const token = await mailValue(account, /#token=([A-Za-z0-9_-]+)/);
    await request(account.label + ' cleanup confirm', '/api/v1/auth/account/deletion/confirm', 202,
      { account, method: 'POST', csrf: true, headers: { Origin: origin }, body: { token, username: account.username } });
    cleanup.push(account.label + ': Auth deletion requested through API; existing 14-day grace period retained');
    const ids = [...new Set((await mailbox(account)).map(message => message.ID))];
    if (ids.length) {
      const response = await fetch(mailpit + '/api/v1/messages', { method: 'DELETE',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ IDs: ids }), signal: AbortSignal.timeout(5000) });
      if (!response.ok) throw new Error('Scoped test mailbox cleanup failed');
    }
    check(account.label + ' test mailbox empty', 0, (await mailbox(account)).length);
  }

  try {
    for (const name of ['postgres', 'auth-service', 'game-service', 'api-gateway', 'library-service']) {
      const actual = command(['inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', 'ltz-dev-' + name + '-1']);
      if (actual !== 'ltz-dev') throw new Error('Refusing a non-ltz-dev container');
    }
    fixtureCreated = true;
    sql('game_db', `INSERT INTO games(id,title,normalized_title,created_at,updated_at,origin,game_type,status,metadata_completeness)
    VALUES('${gameId}','${title}','${title.toLowerCase()}',clock_timestamp(),clock_timestamp(),'INTERNAL','MAIN_GAME','ACTIVE','INDEX_ONLY');`);
    const catalog = await request('Real Game batch fixture', '/api/v1/game/games/batch', 200,
      { method: 'POST', body: { gameIds: [gameId] } });
    check('Canonical test game available', gameId, catalog.items?.[0]?.gameId);
    const a = await register('A'), b = await register('B');
    const created = await mutate(a, 'POST', '', 200, { gameId });
    check('New favorite false', false, created.favorite);
    check('New completed false', false, created.completed);
    const again = await mutate(a, 'POST', '', 200, { gameId });
    check('Repeated add stable entry', created.id, again.id);
    check('Repeated add one DB row', 1, state(a).count);
    await mutate(a, 'PUT', '/' + gameId + '/favorite');
    check('Favorite persisted', true, state(a).favorite);
    await mutate(a, 'PUT', '/' + gameId + '/completed');
    check('Completed persisted', true, state(a).completed);
    check('Completed preserves favorite', true, state(a).favorite);
    await mutate(b, 'POST', '', 400, { gameId, userId: a.id });
    await request('B cannot select A via path', '/api/v1/library/users/' + a.id + '/entries/' + gameId, 403,
      { account: b, method: 'DELETE', csrf: true });
    await mutate(b, 'DELETE', '/' + gameId, 204);
    check('B delete preserves A entry', 1, state(a).count);
    await mutate(b, 'PUT', '/' + gameId + '/favorite');
    check('B auto-collects own row', 1, state(b).count);
    check('B auto favorite only', false, state(b).completed);
    check('A flags unaffected by B', true, state(a).completed);
    await mutate(b, 'DELETE', '/' + gameId, 204);
    await mutate(a, 'DELETE', '/' + gameId, 204);
    check('Delete removes collection and flags', 0, state(a).count);
    await mutate(a, 'DELETE', '/' + gameId, 204);
    await mutate(a, 'PUT', '/' + gameId + '/completed');
    check('Auto complete only', false, state(a).favorite);
    await mutate(a, 'DELETE', '/' + gameId, 204);
    await mutate(a, 'POST', '', 404, { gameId: invalidGame });
    await mutate(a, 'PUT', '/' + invalidGame + '/favorite', 404);
    await request('Invalid JWT', '/api/v1/library/entries', 401,
      { method: 'POST', body: { gameId }, headers: { Authorization: 'Bearer invalid-test-token' } });
    await request('Cookie without CSRF', '/api/v1/library/entries', 403, { account: a, method: 'POST', body: { gameId } });
    await request('Valid JWT Bearer through Gateway', '/api/v1/library/entries', 200,
      { method: 'POST', body: { gameId }, headers: { Authorization: 'Bearer ' + a.token } });
    await mutate(a, 'DELETE', '/' + gameId, 204);
    gameStopped = true;
    command(['stop', '--time', '10', 'ltz-dev-game-service-1']);
    try {
      await mutate(a, 'POST', '', 503, { gameId });
      await mutate(a, 'PUT', '/' + gameId + '/favorite', 503);
      check('Outage writes no row', 0, state(a).count);
    } finally { await restoreGame(); }
    await mutate(a, 'POST', '', 200, { gameId });
    await mutate(a, 'DELETE', '/' + gameId, 204);
  } catch (error) {
    failure = error.message.startsWith('Assertion failed:') ? error.message : 'E2E infrastructure or HTTP operation failed (credentials redacted)';
    console.log('FAIL ' + failure);
  } finally {
    try { await restoreGame(); cleanup.push('Game restored: health 200'); }
    catch { cleanup.push('ERROR: Game restoration failed'); failure ||= 'Cleanup failed'; }
    for (const account of accounts) {
      try { await cleanAccount(account); }
      catch { cleanup.push(account.label + ': ERROR cleanup incomplete; credentials not logged'); failure ||= 'Account cleanup incomplete'; }
    }
    try {
      const ids = accounts.filter(account => account.id).map(account => "'" + account.id + "'");
      if (ids.length) sql('library_db', `DELETE FROM library_entries WHERE user_id IN (${ids.join(',')}) AND game_id IN ('${gameId}','${invalidGame}');`);
      if (fixtureCreated) sql('game_db', `DELETE FROM games WHERE id='${gameId}' AND title='${title}';`);
      check('Only test game removed', '0', sql('game_db', `SELECT count(*) FROM games WHERE id='${gameId}';`));
      cleanup.push('Scoped Library test rows and Game fixture removed; existing databases/volumes retained');
    } catch { cleanup.push('ERROR: Scoped fixture cleanup failed'); failure ||= 'Fixture cleanup failed'; }
    const phases = [
      ['Faz 1 — Koleksiyon Ekle/Kaldır ve Veri Bütünlüğü', ['LIB-01','LIB-02','LIB-12','LIB-13','LIB-15']],
      ['Faz 2 — Favori ve Bitirildi İşlemleri', ['LIB-03','LIB-04']],
      ['Faz 3 — Kütüphane Listeleme, Filtreleme ve İstatistik', ['LIB-05','LIB-06','LIB-07','LIB-08','LIB-09','LIB-16']],
      ['Faz 4 — Gizlilik ve Servis Entegrasyonları', ['LIB-10','LIB-11','LIB-14','LIB-18']],
      ['Faz 5 — Arşivlenen Oyunlarda Dayanıklılık', ['LIB-17']]
    ];
    const output = ['# Library Faz 1 — Gerçek ltz-dev E2E kanıtı', '', 'UTC: ' + new Date().toISOString(), '',
      'Sonuç: ' + (failure ? 'FAIL / INCOMPLETE' : 'PASS'), 'Kontroller: ' + rows.filter(row => row.pass).length + '/' + rows.length,
      '', 'Pre-push ve SonarQube çalıştırılmadı; kullanıcı tarafından manuel yapılacaktır.', '', '## Beklenen ve gerçekleşen sonuçlar', '',
      ...rows.map(row => '- [' + (row.pass ? 'x' : ' ') + '] ' + row.name + ': beklenen=' + row.expected + ', gerçekleşen=' + row.actual),
      '', '## Temizlik', '', ...cleanup.map(line => '- ' + line), '',
      'Parola, OTP, JWT, cookie ve silme token değerleri bu rapora veya loglara yazılmadı.',
      'E2E geçerli Auth oturumlarını kullandı; Auth DB üzerinde SQL veya süre/politika değişikliği yapılmadı.',
      'Faz 1 otomatik kabul testleri geçerlidir; E2E eksikse ayrıntı ana sonuç raporunda belirtilir.',
      'Faz 1: ' + (failure ? '5/5 geliştirme; E2E son kontrol eksik' : '5/5') + ' · Genel: 5/18', ''];
    for (const [index, [name, tasks]] of phases.entries()) output.push('## ' + name, '', ...tasks.map(task => '- [' + (index === 0 ? 'x' : ' ') + '] ' + task), '');
    writeFileSync('docs-library/LTZ-HAM-LIB-01-E2E-Sonuc.md', output.join('\n'));
  }
  process.exitCode = failure ? 1 : 0;
}
// #endregion e2e-library

// #region mail
// Yerel geliştirmede GERÇEK e-posta (Gmail SMTP) açar/kapatır. Varsayılan Mailpit'tir (http://localhost:8025, gerçek mail gitmez).
// Normalde `node ops/dev.mjs` kurulum sırasında bunu zaten sorar; bu betik sonradan açıp kapatmak içindir (`node ops/dev.mjs mail on`).
//
//   node ops/dev.mjs mail on        # YALNIZ uygulama şifresini sorar (Gmail adresi sabit); Gmail sabitlerini .env.dev'e kendisi yazar
//   node ops/dev.mjs mail off       # SMTP ayarlarını boşaltır, e-posta yeniden Mailpit'e düşer
//   node ops/dev.mjs mail status    # açık mı kapalı mı (adres ve şifre gösterilmez)
//   (ayrıca: --file <yol>  başka bir env dosyası için; varsayılan .env.dev)
//
// Otomatik gelenler: smtp.gmail.com, 587, STARTTLS, izinli bağlantı sunucusu (localhost) ve AUTH_MAIL_ADAPTER=mail-service.
// Sana özel olan tek şey Google "uygulama şifresi" (16 harf). ŞİFRE DEPOYA, BETİĞE VE SOHBETE YAZILMAZ: ekranda görünmeden girilir,
// yalnız kendi .env.dev dosyana yazılır. Gmail adresi ekibin ortak test hesabıdır (mail-lib bölümü); başka hesap için DEV_MAIL_USER.
// Etkileşimsiz ortamda DEV_MAIL_PASSWORD ortam değişkeni kullanılır.
// Gerçek SMTP açıkken `ltz.com` gibi gerçek alan adlı test hesaplarına gönderilen e-postalar gerçek adrese gider; bkz.
// docs-auth/ekip-hizli-baslangic.md §1.0.
async function mailCommand(argv) {
  const { DEFAULT_MAIL_USER, applyEnvChanges, describeSmtp, normalizeAppPassword, smtpOffSettings, smtpOnSettings, validateGmailAddress } = mailLib;
  const RESTART = "node ops/dev.mjs accounts up --no-seed postgres mailpit mail-service auth-service";

  const args = argv;
  const fileFlag = args.indexOf("--file");
  const target = resolve(root, fileFlag >= 0 ? (args[fileFlag + 1] ?? "") : ".env.dev");
  const command = args.find((arg, index) => !arg.startsWith("--") && args[index - 1] !== "--file") ?? "status";

  if (!existsSync(target)) {
    fail(`${target} yok. Önce şunu çalıştır: node ops/dev.mjs keys`);
  }

  async function turnOn() {
    const user = process.env.DEV_MAIL_USER ?? DEFAULT_MAIL_USER;
    let password = process.env.DEV_MAIL_PASSWORD;
    if (password === undefined) {
      if (!process.stdin.isTTY) {
        fail("Etkileşimli bir terminal gerekli (ya da DEV_MAIL_PASSWORD ortam değişkenini ver).");
      }
      password = await askHidden(`Uygulama şifresi (yazarken görünmez; hesap: ${user}): `);
    }
    let settings;
    try {
      settings = smtpOnSettings(validateGmailAddress(user), normalizeAppPassword(password));
    } catch (error) {
      fail(error.message);
    }
    writeAtomic(target, applyEnvChanges(readFileSync(target, "utf8"), settings));
    console.log(`✓ Gerçek e-posta (Gmail SMTP) ${target} dosyasına yazıldı. Şifre ekrana basılmadı.`);
    console.log(`  Servisleri yeniden başlat: ${RESTART}`);
  }

  function turnOff() {
    writeAtomic(target, applyEnvChanges(readFileSync(target, "utf8"), smtpOffSettings()));
    console.log("✓ Gerçek e-posta kapatıldı: e-postalar Mailpit'e düşer (http://localhost:8025).");
    console.log(`  Servisleri yeniden başlat: ${RESTART}`);
  }

  if (command === "on") await turnOn();
  else if (command === "off") turnOff();
  else if (command === "status") {
    const state = describeSmtp(readFileSync(target, "utf8"));
    console.log(
      state.real
        ? `GERÇEK e-posta açık: ${state.host}, şifre ${state.passwordSet ? "dolu" : "BOŞ (gönderim başarısız olur)"}, adaptör ${state.adapter}`
        : "Mailpit (gerçek e-posta kapalı). Açmak için: node ops/dev.mjs mail on"
    );
  } else fail(`Bilinmeyen komut: ${command} (on | off | status)`);
}
// #endregion mail

// #region accounts
// Yerel geliştirme yığınını kaldırır ve her role bir test hesabı oluşturur/günceller (yalnız ltz-dev yığını, yalnız yerel adresler).
//
//   node ops/dev.mjs accounts up                 # derle + başlat + sağlığı bekle + hesapları oluştur (ekibin tek komutu)
//   node ops/dev.mjs accounts up --no-seed       # yalnız yığını kaldır      (ayrıca: --no-build, --dry-run, servis adları)
//   node ops/dev.mjs accounts                    # (seed) yığın zaten ayaktaysa hesapları oluştur/güncelle ve girişi doğrula
//   node ops/dev.mjs accounts list               # hesapları ve rollerini göster
//   node ops/dev.mjs accounts totp admin         # admin/moderator için anlık 2FA kodu
//
// Parola ve alan adı: ortamdan (DEV_ACCOUNTS_PASSWORD / DEV_ACCOUNTS_DOMAIN) ya da .env.dev'den. .env.dev'den YALNIZ bu iki
// anahtar okunur (accounts-lib bölümündeki parseDevEnv); satırlar yoksa `node ops/dev.mjs keys` ekler.
//
// Hesaplar: user (PLAYER), creator (CONTENT_CREATOR), developer (GAME_DEVELOPER), moderator (MODERATOR), admin (ADMIN);
// e-posta `<ad>@${DEV_ACCOUNTS_DOMAIN:-ltz.com}`.
//
// !!! BİLİNÇLİ KURAL İHLALİ — YALNIZ YEREL GELİŞTİRME !!!
// Hesaplar kayıt akışından (e-posta doğrulaması, parola kuralları) GEÇİRİLMEDEN doğrudan VERİTABANINA yazılır:
//   - auth_db:  `accounts` (ACTIVE, e-posta doğrulanmış, kısa parolanın Argon2 özeti; Auth alt sınırı 15 karakterdir),
//               PLAYER rolü ve durum geçmişi; ADMIN/MODERATOR/CONTENT_CREATOR/GAME_DEVELOPER rolü `account_roles`'a doğrudan
//               eklenir (normal yolda ADMIN/MODERATOR yalnız TOTP'si etkin hesaba verilir; bu kural dev için atlanır).
//   - user_db:  her hesap için `user_profiles` satırı (kullanıcı adı kopyasıyla), User servisinin olay almasını beklemeden.
// Bu yüzden e-posta, SMTP, Mailpit ya da Mail Service ÇALIŞMAK ZORUNDA DEĞİLDİR; gerçek SMTP açıkken de çalışır ve hiçbir yere
// e-posta göndermez. 2FA: Auth ADMIN/MODERATOR'a giriş sırasında TOTP ister (AUTH_2FA_REQUIRED_ROLES). EN KOLAY DEV YOLU: .env.dev'e
// `AUTH_2FA_REQUIRED_ROLES=` (boş; Auth bunu yalnız dev/test profilinde kabul eder) ekleyip Auth'u yeniden başlatmak: betik o zaman
// hiç TOTP kurmaz, admin/moderator yalnız parolayla girer ve hiçbir e-posta çıkmaz. Boş değilse betik hesabı önce rolsüz açar, API ile
// giriş yapıp TOTP'yi kurar (Auth "2FA etkinleştirildi" bildirimi e-postası gönderir; gerçek SMTP + gerçek alan adı varsa betik
// BUNU YAPMAZ ve durur), SONRA rolü doğrudan verir. Sır ve kurtarma kodları makine düzeyindeki `~/.ltz/dev-accounts.json`
// dosyasındadır (depoya girmez; aynı bilgisayardaki tüm klasörler aynı veritabanını paylaştığı için aynı dosyayı kullanır).
//
// Güvenlik: yalnız localhost'a, `ltz-dev-*` Postgres kapsayıcısına ve dev/test profilindeki Auth'a çalışır, NODE_ENV=production
// iken reddeder. Parola depoya yazılmaz (DEV_ACCOUNTS_PASSWORD zorunlu, varsayılanı yok) ve ekrana basılmaz. Tekrar
// çalıştırmak güvenlidir.
async function accountsCommand(argv) {
  const { DEV_ACCOUNTS, assertDevProfile, assertNodeSupported, assertDevTarget, assertUuid, emailFor, hashPassword, isUndeliverableDomain, parseDevEnv, parseRequiredRoles, secondsLeftInStep, sqlLiteral, totp, validateConfig } = accountsLib;
  try {
    assertNodeSupported();
  } catch (error) {
    console.error(`✗ ${error.message}`);
    process.exit(1);
  }
  /**
 * 2FA sırları MAKİNE düzeyinde tutulur: aynı bilgisayardaki tüm klasörler (ör. farklı git worktree'leri) aynı Docker
 * veritabanını (`ltz-dev`) paylaşır, dolayısıyla aynı sırrı görmelidir. Depoya girmez (home dizini). `DEV_ACCOUNTS_STATE` ile
 * değiştirilebilir. Eski klasör içi dosya (`.dev-accounts.local.json`) bulunursa içeriği buraya taşınır.
 */
  const STATE_FILE = process.env.DEV_ACCOUNTS_STATE ?? resolve(homedir(), ".ltz", "dev-accounts.json");
  const LEGACY_STATE_FILE = resolve(root, ".dev-accounts.local.json");

  /** Ortam değişkeni önceliklidir; yoksa .env.dev'deki DEV_ACCOUNTS_* satırları kullanılır (başka hiçbir satır okunmaz). */
  function devEnv() {
    const file = resolve(root, ".env.dev");
    try {
      return existsSync(file) ? parseDevEnv(readFileSync(file, "utf8")) : {};
    } catch {
      return {};
    }
  }
  const fromFile = devEnv();

  const config = {
    password: process.env.DEV_ACCOUNTS_PASSWORD ?? fromFile.DEV_ACCOUNTS_PASSWORD,
    domain: process.env.DEV_ACCOUNTS_DOMAIN ?? fromFile.DEV_ACCOUNTS_DOMAIN ?? "ltz.com",
    gatewayUrl: process.env.DEV_GATEWAY_URL ?? "http://localhost:5080",
    origin: process.env.DEV_FRONTEND_ORIGIN ?? "http://localhost:5001",
    container: process.env.DEV_POSTGRES_CONTAINER ?? "ltz-dev-postgres-1",
    authContainer: process.env.DEV_AUTH_CONTAINER ?? "ltz-dev-auth-service-1",
    mailContainer: process.env.DEV_MAIL_CONTAINER ?? "ltz-dev-mail-service-1",
    nodeEnv: process.env.NODE_ENV
  };

  function fail(message) {
    console.error(`✗ ${message}`);
    process.exit(1);
  }

  // ---------- yerel durum (TOTP sırları) ----------

  function readJson(file) {
    try {
      return JSON.parse(readFileSync(file, "utf8"));
    } catch {
      fail(`${file} okunamadı (bozuk JSON). Silip yeniden çalıştırabilirsin; betik eksik 2FA sırlarını yeniden kurar.`);
    }
  }

  function readState() {
    const machine = existsSync(STATE_FILE) ? readJson(STATE_FILE) : {};
    if (!existsSync(LEGACY_STATE_FILE)) return machine;
    // Eski klasör içi dosyadaki kayıtlardan makine dosyasında olmayanlar taşınır (makine dosyası önceliklidir).
    const merged = { ...readJson(LEGACY_STATE_FILE), ...machine };
    if (Object.keys(merged).length !== Object.keys(machine).length) {
      writeState(merged);
      console.log(`(2FA sırları ${STATE_FILE} dosyasına taşındı)`);
    }
    return merged;
  }

  function writeState(state) {
    mkdirSync(dirname(STATE_FILE), { recursive: true, mode: 0o700 });
    writeFileSync(STATE_FILE, `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
    try {
      chmodSync(STATE_FILE, 0o600);
    } catch {
      // Windows'ta izin biti anlamlı değil.
    }
  }

  // ---------- veritabanı ----------

  function psql(sql, database = "auth_db") {
    const result = spawnSync(
      "docker",
      ["exec", "-i", config.container, "sh", "-c", `psql -U "$POSTGRES_USER" -d ${database} -v ON_ERROR_STOP=1 -tA`],
      { input: sql, encoding: "utf8" }
    );
    if (result.status !== 0) {
      fail(`Veritabanı komutu başarısız (${config.container}): ${(result.stderr || "").trim().split("\n")[0]}`);
    }
    return result.stdout.trim();
  }

  // ---------- HTTP (çerez kavanozu + CSRF) ----------

  class Session {
    jar = new Map();

    async request(method, path, body) {
      const headers = { Accept: "application/json", Origin: config.origin };
      if (this.jar.size > 0) headers.Cookie = [...this.jar].map(([k, v]) => `${k}=${v}`).join("; ");
      const xsrf = this.jar.get("XSRF-TOKEN") ?? this.jar.get("__Host-XSRF-TOKEN");
      if (xsrf && method !== "GET") headers["X-XSRF-TOKEN"] = xsrf;
      if (body !== undefined) headers["Content-Type"] = "application/json";
      const response = await fetch(`${config.gatewayUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        redirect: "manual"
      });
      for (const line of response.headers.getSetCookie()) {
        const [pair] = line.split(";");
        const index = pair.indexOf("=");
        const name = pair.slice(0, index).trim();
        const value = pair.slice(index + 1).trim();
        if (value === "" || /max-age=0/i.test(line)) this.jar.delete(name);
        else this.jar.set(name, value);
      }
      const text = await response.text();
      let json = null;
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        json = null;
      }
      return { status: response.status, json };
    }

    async csrf() {
      await this.request("GET", "/api/v1/auth/csrf");
    }
  }

  function describe(response) {
    return `HTTP ${response.status}${response.json?.code ? ` ${response.json.code}` : ""}`;
  }

  // ---------- akışlar ----------

  async function waitForFreshTotpStep() {
    if (secondsLeftInStep() < 4) await new Promise((done) => setTimeout(done, 5000));
  }

  /** Hesabı rolsüz (yalnız PLAYER) ve doğrulanmış olarak doğrudan veritabanına yazar; kayıt akışı ve e-posta kullanılmaz. */
  function createInDatabase(account, email, passwordHash) {
    const e = sqlLiteral(email.toLowerCase());
    const u = sqlLiteral(account.local);
    psql(`
    begin;
    insert into accounts (id, email, email_normalized, username, username_normalized, password_hash, status,
                          email_verified_at, token_version, created_at, updated_at, version)
    values (gen_random_uuid(), ${sqlLiteral(email)}, ${e}, ${u}, ${u}, ${sqlLiteral(passwordHash)}, 'ACTIVE',
            now(), 0, now(), now(), 0);
    insert into account_roles (account_id, role_id)
      select a.id, r.id from accounts a, roles r where a.email_normalized = ${e} and r.code = 'PLAYER';
    insert into account_role_history (id, account_id, role_code, action, reason, actor_id, created_at)
      select gen_random_uuid(), a.id, 'PLAYER', 'GRANT', 'dev-accounts seed', a.id, now()
        from accounts a where a.email_normalized = ${e};
    insert into account_status_history (id, account_id, from_status, to_status, reason, actor_id, created_at)
      select gen_random_uuid(), a.id, 'PENDING_VERIFICATION', 'ACTIVE', 'dev-accounts seed', a.id, now()
        from accounts a where a.email_normalized = ${e};
    commit;
  `);
  }

  /**
 * User servisinde profil satırını doğrudan yazar (olayı beklemeden). Satır varsa kullanıcı adı kopyası ve durum güncellenir.
 * Hesap kimliği Auth veritabanından okunur ve SQL'e gömülmeden önce UUID olduğu doğrulanır.
 */
  function ensureUserProfile(account, email) {
    const id = psql(`select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())};`);
    try {
      assertUuid(id);
    } catch {
      fail(`${email} için hesap kimliği okunamadı`);
    }
    const u = sqlLiteral(account.local);
    psql(
      `
    insert into user_profiles (user_id, username_snapshot, username_normalized, account_status, created_at, updated_at, version)
    values ('${id}', ${u}, ${u}, 'ACTIVE', now(), now(), 0)
    on conflict (user_id) do update
      set username_snapshot = excluded.username_snapshot, username_normalized = excluded.username_normalized,
          account_status = 'ACTIVE', updated_at = now();
  `,
      "user_db"
    );
  }

  /** Auth konteynerindeki 2FA zorunlu rol listesi (yalnız bu değişken okunur). Tanımsızsa Auth varsayılanı geçerlidir. */
  function requiredTwoFactorRoles() {
    const result = spawnSync("docker", ["exec", config.authContainer, "printenv", "AUTH_2FA_REQUIRED_ROLES"], { encoding: "utf8" });
    return parseRequiredRoles(result.status === 0 ? result.stdout : undefined);
  }

  /** Mail Service'in SMTP sunucusu (yalnız host; boş = Mailpit/geliştirme gönderimi). Konteyner yoksa boş sayılır: mail gitmez. */
  function smtpHost() {
    const result = spawnSync("docker", ["exec", config.mailContainer, "printenv", "MAIL_SMTP_HOST"], { encoding: "utf8" });
    return result.status === 0 ? result.stdout.trim() : "";
  }

  /**
 * TOTP'yi API ile kurmak Auth'a "iki aşamalı doğrulama etkinleştirildi" bildirim e-postası gönderttirir. Gerçek SMTP açıkken ve
 * adres teslim edilebilir bir alan adındaysa (ör. ltz.com) bu e-posta GERÇEK bir adrese gider; bu yüzden o durumda durulur.
 */
  function assertMailSafe(email) {
    const host = smtpHost();
    const domain = email.split("@")[1] ?? "";
    if (host !== "" && !isUndeliverableDomain(domain)) {
      fail(
        `${email} için 2FA kurulumu bildirim e-postası gönderir, ama gerçek SMTP açık (${host}) ve ${domain} gerçek bir alan adı: ` +
          "e-posta başkasına gider. Çözüm (biri yeter): .env.dev'e AUTH_2FA_REQUIRED_ROLES= (boş, yalnız dev) ekleyip Auth'u yeniden " +
          "başlat (admin/moderator 2FA'sız girer, hiç mail çıkmaz); ya da DEV_ACCOUNTS_DOMAIN=ltz.test kullan; ya da geçici olarak " +
          "MAIL_SMTP_HOST'u boşaltıp mail-service'i yeniden başlat."
      );
    }
  }

  /** 2FA zorunlu değilken kalmış TOTP kaydını siler (yerel sır eşleşmiyorsa girişi tıkamasın). Rol ve hesap korunur. */
  function disableTotp(email, state) {
    const owner = `(select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())})`;
    psql(`
    begin;
    delete from recovery_codes where account_id = ${owner};
    delete from totp_credentials where account_id = ${owner};
    update accounts set token_version = token_version + 1 where id = ${owner};
    commit;
  `);
    delete state[email];
    writeState(state);
  }

  /** Yeni (henüz rolsüz) 2FA'lı hesap: parolayla giriş yapıp TOTP kurulur; rol `finalizeInDatabase` ile sonra verilir. */
  async function enrollNewAccount(email, state) {
    const session = new Session();
    await session.csrf();
    const response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
    if (response.json?.status !== "AUTHENTICATED") fail(`${email} ilk giriş başarısız: ${describe(response)}`);
    await enrollTotp(session, email, state);
  }

  /** Giriş yapmış oturumda TOTP kurar ve etkinleştirir; sır ve kurtarma kodları makine dosyasına yazılır. */
  async function enrollTotp(session, email, state) {
    assertMailSafe(email);
    let response = await session.request("POST", "/api/v1/auth/2fa/totp/setup", {});
    const secret = response.json?.secret;
    if (response.status !== 200 || !secret) fail(`${email} TOTP kurulumu başarısız: ${describe(response)}`);
    await waitForFreshTotpStep();
    response = await session.request("POST", "/api/v1/auth/2fa/totp/activate", { code: totp(secret) });
    if (response.status !== 200) fail(`${email} TOTP etkinleştirme başarısız: ${describe(response)}`);
    state[email] = { totpSecret: secret, recoveryCodes: response.json?.recoveryCodes ?? [] };
    writeState(state);
  }

  /**
 * Hesabın TOTP'si var ama sırrı bu makinede yok (veritabanı başka makineden gelmiş, dosya silinmiş): yalnız DEV hesabı için
 * 2FA sıfırlanıp yeniden kurulur. Auth 2FA'sız ADMIN/MODERATOR'a oturum vermediğinden, kurulum süresince rol geçici alınır ve
 * `finalizeInDatabase` ile geri verilir.
 */
  async function repairTotp(account, email, state, reason = "2FA sırrı yok") {
    assertMailSafe(email); // veritabanına dokunmadan ÖNCE: yarım kalmış (rolsüz/2FA'sız) hesap bırakılmasın
    const e = sqlLiteral(email.toLowerCase());
    const owner = `(select id from accounts where email_normalized = ${e})`;
    process.stdout.write(`${reason}, yeniden kuruluyor … `);
    psql(`
    begin;
    update accounts
       set password_hash = ${sqlLiteral(hashPassword(config.password))}, status = 'ACTIVE',
           email_verified_at = coalesce(email_verified_at, now()), token_version = token_version + 1,
           updated_at = now(), version = version + 1
     where email_normalized = ${e};
    insert into account_role_history (id, account_id, role_code, action, reason, actor_id, created_at)
      select gen_random_uuid(), a.id, ${sqlLiteral(account.role)}, 'REVOKE', 'dev-accounts 2fa repair', a.id, now()
        from accounts a join account_roles ar on ar.account_id = a.id join roles r on r.id = ar.role_id
       where a.email_normalized = ${e} and r.code = ${sqlLiteral(account.role)};
    delete from account_roles where account_id = ${owner} and role_id = (select id from roles where code = ${sqlLiteral(account.role)});
    delete from recovery_codes where account_id = ${owner};
    delete from totp_credentials where account_id = ${owner};
    commit;
  `);
    const session = new Session();
    await session.csrf();
    const response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
    if (response.json?.status !== "AUTHENTICATED") fail(`${email} 2FA onarımı sırasında giriş başarısız: ${describe(response)}`);
    await enrollTotp(session, email, state);
  }

  function finalizeInDatabase(account, email, passwordHash) {
    const e = sqlLiteral(email.toLowerCase());
    const roleGrant =
      account.role === "PLAYER"
        ? ""
        : `
    with acc as (select id from accounts where email_normalized = ${e}),
         role as (select id, code from roles where code = ${sqlLiteral(account.role)}),
         ins as (
           insert into account_roles (account_id, role_id, granted_by)
           select acc.id, role.id, acc.id from acc, role
           on conflict do nothing
           returning account_id
         )
    insert into account_role_history (id, account_id, role_code, action, reason, actor_id, created_at)
    select gen_random_uuid(), ins.account_id, ${sqlLiteral(account.role)}, 'GRANT', 'dev-accounts seed', ins.account_id, now() from ins;`;
    psql(`
    begin;
    update accounts
       set password_hash = ${sqlLiteral(passwordHash)},
           status = 'ACTIVE',
           email_verified_at = coalesce(email_verified_at, now()),
           token_version = token_version + 1,
           updated_at = now(),
           version = version + 1
     where email_normalized = ${e};${roleGrant}
    commit;
  `);
  }

  /**
 * Auth, aynı 30 sn dilimindeki TOTP kodunu ikinci kez kabul etmez (tekrar kullanım koruması). Önceki bir çalıştırma ya da TOTP
 * etkinleştirmesi bu dilimi tüketmiş olabilir; bu yüzden 2FA hatasında bir sonraki dilimi bekleyip bir kez yeniden denenir.
 */
  async function verifyLogin(account, email, state) {
    const first = await verifyLoginOnce(account, email, state);
    if (first === null || !first.includes("MFA_FAILED")) return first;
    await new Promise((done) => setTimeout(done, (secondsLeftInStep() + 1) * 1000));
    return verifyLoginOnce(account, email, state);
  }

  async function verifyLoginOnce(account, email, state) {
    const session = new Session();
    await session.csrf();
    let response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
    if (response.json?.status === "MFA_REQUIRED") {
      const secret = state[email]?.totpSecret;
      if (!secret) return "TOTP sırrı bu makinede yok (2FA bu hesapta etkin)";
      await waitForFreshTotpStep();
      response = await session.request("POST", "/api/v1/auth/login/2fa", {
        challengeId: response.json.challengeId,
        code: totp(secret)
      });
    }
    if (response.json?.status !== "AUTHENTICATED") return `giriş başarısız: ${describe(response)}`;
    const me = await session.request("GET", "/api/v1/auth/me");
    const roles = me.json?.roles ?? [];
    if (!roles.includes(account.role)) return `rol eksik: beklenen ${account.role}, gelen [${roles.join(",")}]`;
    const profile = await session.request("GET", "/api/v1/user/me");
    if (profile.status !== 200) return `profil okunamadı: ${describe(profile)}`;
    if (String(profile.json?.username ?? "").toLowerCase() !== account.local) return "profildeki kullanıcı adı beklenenle uyuşmuyor";
    return null;
  }

  async function seed() {
    try {
      validateConfig(config);
    } catch (error) {
      fail(error.message);
    }
    const state = readState();
    const requiredRoles = requiredTwoFactorRoles();
    if (requiredRoles.length === 0) console.log("(AUTH_2FA_REQUIRED_ROLES boş: admin/moderator 2FA'sız girer, TOTP kurulmaz, e-posta gitmez)");
    const results = [];
    for (const account of DEV_ACCOUNTS) {
      const email = emailFor(account, config.domain);
      const needsTotp = requiredRoles.includes(account.role);
      process.stdout.write(`• ${email} (${account.role}) … `);
      const existing = psql(`select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())};`);
      const passwordHash = hashPassword(config.password);
      if (needsTotp && (!existing || !state[email]?.totpSecret)) assertMailSafe(email); // 2FA kurulacaksa mail güvenliği önce
      if (!existing) {
        createInDatabase(account, email, passwordHash);
        if (needsTotp) await enrollNewAccount(email, state);
      } else if (needsTotp && !state[email]?.totpSecret) {
        await repairTotp(account, email, state);
      }
      finalizeInDatabase(account, email, passwordHash);
      ensureUserProfile(account, email);
      let problem = await verifyLogin(account, email, state);
      // Aynı veritabanını başka bir klasör/makine yeniden kurmuş olabilir: yerel sır artık eşleşmiyor. Dev hesabı olduğundan
      // 2FA bir kez sıfırlanıp (zorunluysa yeniden kurulup) tekrar denenir; aynı 30 sn dilimi tekrarı verifyLogin içinde zaten denendi.
      if (problem && existing && (problem.includes("MFA_FAILED") || problem.includes("TOTP sırrı"))) {
        if (needsTotp) {
          await repairTotp(account, email, state, "2FA sırrı eşleşmiyor");
          finalizeInDatabase(account, email, passwordHash);
        } else {
          disableTotp(email, state);
        }
        problem = await verifyLogin(account, email, state);
      }
      console.log(problem ? `✗ ${problem}` : "✓ giriş doğrulandı");
      results.push({ email, role: account.role, mfa: needsTotp, ok: !problem });
    }
    console.log("\nHesaplar (parola: DEV_ACCOUNTS_PASSWORD):");
    for (const r of results) console.log(`  ${r.ok ? "✓" : "✗"} ${r.email.padEnd(22)} ${r.role}${r.mfa ? "  (2FA: node ops/dev.mjs accounts totp " + r.email.split("@")[0] + ")" : ""}`);
    if (results.some((r) => !r.ok)) process.exit(1);
  }

  function list() {
    const rows = psql(`
    select a.email || ' | ' || a.status || ' | ' || coalesce((select string_agg(r.code, ',' order by r.code)
      from account_roles ar join roles r on r.id = ar.role_id where ar.account_id = a.id), '-')
    from accounts a where a.email_normalized like ${sqlLiteral(`%@${config.domain}`)} order by a.created_at;`);
    console.log(rows || "(hesap yok)");
  }

  function showTotp(name) {
    const account = DEV_ACCOUNTS.find((item) => item.local === name);
    if (!account?.mfa) fail(`2FA yalnız şu hesaplarda var: ${DEV_ACCOUNTS.filter((a) => a.mfa).map((a) => a.local).join(", ")}`);
    const entry = readState()[emailFor(account, config.domain)];
    if (!entry?.totpSecret) fail("Bu hesap için bu makinede TOTP sırrı yok. `node ops/dev.mjs accounts` çalıştır: sırrı olmayan 2FA hesabını yeniden kurar.");
    console.log(`${totp(entry.totpSecret)}  (${secondsLeftInStep()} sn geçerli)`);
  }

  /** Auth konteynerinin gerçek profilini okur (yalnız bu tek değişken; başka ortam değeri okunmaz/basılmaz). */
  function readAuthProfile() {
    const result = spawnSync("docker", ["exec", config.authContainer, "printenv", "SPRING_PROFILES_ACTIVE"], { encoding: "utf8" });
    return result.status === 0 ? result.stdout.trim() : "";
  }

  function guardTarget() {
    try {
      assertDevTarget(config);
    } catch (error) {
      fail(error.message);
    }
  }

  /** Yığın ayaktayken: hedef + Auth profili kapıları. */
  function guardRunningStack() {
    guardTarget();
    try {
      assertDevProfile(readAuthProfile());
    } catch (error) {
      fail(error.message);
    }
  }

  const PROJECT = "ltz-dev";
  const DEFAULT_SERVICES = ["postgres", "mailpit", "mail-service", "auth-service", "user-service", "api-gateway"];
  /** Hesap doğrulamasının dayandığı servisler (giriş Auth'tan, profil User'dan, ikisi de Gateway üzerinden). Mail gerekmez. */
  const MUST_BE_HEALTHY = ["auth-service", "api-gateway", "user-service"];
  const UP_FLAGS = new Set(["--no-seed", "--no-build", "--dry-run"]);

  function containerHealth(service) {
    const result = spawnSync(
      "docker",
      ["inspect", "-f", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}", `${PROJECT}-${service}-1`],
      { encoding: "utf8" }
    );
    return result.status === 0 ? result.stdout.trim() : "yok";
  }

  /**
 * Servis hazır mı? Sağlık kontrolü tanımlıysa `healthy` beklenir. Tanımsızsa (ör. user-service) kapsayıcının çalışıyor olması ve
 * Spring'in "Started ...Application" satırını yazmış olması beklenir; aksi halde bu servisler hiç "healthy" olmaz ve kurulum takılır.
 */
  function containerReady(service) {
    const state = containerHealth(service);
    if (state === "healthy") return true;
    if (state !== "running") return false;
    const logs = spawnSync("docker", ["logs", "--tail", "200", `${PROJECT}-${service}-1`], { encoding: "utf8" });
    return /Started \w+Application/.test(`${logs.stdout ?? ""}${logs.stderr ?? ""}`);
  }

  async function up(args) {
    const flags = new Set(args.filter((arg) => arg.startsWith("--")));
    for (const flag of flags) if (!UP_FLAGS.has(flag)) fail(`Bilinmeyen seçenek: ${flag}`);
    const named = args.filter((arg) => !arg.startsWith("--"));
    const targets = named.length > 0 ? named : DEFAULT_SERVICES;
    const composeArgs = ["compose", "-p", PROJECT, "--env-file", ".env.dev", "--profile", "dev-mail", "up", "-d", ...(flags.has("--no-build") ? [] : ["--build"]), ...targets];
    const waitFor = MUST_BE_HEALTHY.filter((name) => targets.includes(name));
    const seeding = !flags.has("--no-seed") && MUST_BE_HEALTHY.every((name) => waitFor.includes(name));

    if (flags.has("--dry-run")) {
      console.log(`docker ${composeArgs.join(" ")}`);
      console.log(`sağlık beklenir: ${waitFor.join(", ") || "(yok)"}`);
      console.log(seeding ? "ardından: hesap oluşturma (seed)" : "hesap oluşturma: atlanır");
      return;
    }
    if (!existsSync(resolve(root, ".env.dev"))) fail(".env.dev yok. Önce şunu çalıştır: node ops/dev.mjs keys");
    guardTarget();

    console.log("\n▶ Servisler başlatılıyor");
    const compose = spawnSync("docker", composeArgs, { cwd: root, stdio: "inherit" });
    if (compose.status !== 0) fail("docker compose başarısız oldu (yukarıdaki çıktıya bak).");

    console.log("\n▶ Sağlık bekleniyor");
    const deadline = Date.now() + 240_000;
    for (const service of waitFor) {
      while (!containerReady(service)) {
        if (Date.now() > deadline) fail(`${service} 4 dakikada sağlıklı olmadı. İncele: docker compose -p ${PROJECT} logs ${service} --tail 50`);
        await new Promise((done) => setTimeout(done, 2000));
      }
      console.log(`  ✓ ${service}`);
    }

    if (!seeding) {
      console.log("\nHesap oluşturma atlandı.");
      return;
    }
    console.log("\n▶ Test hesapları");
    guardRunningStack();
    await seed();
  }

  const [command = "seed", ...rest] = argv;
  if (command === "up") {
    await up(rest);
  } else if (command === "seed") {
    guardRunningStack();
    await seed();
  } else if (command === "list") {
    guardRunningStack();
    list();
  } else if (command === "totp") {
    guardRunningStack();
    showTotp(rest[0]);
  } else {
    fail(`Bilinmeyen komut: ${command} (up | seed | list | totp <ad>)`);
  }
}
// #endregion accounts

// #region keys
// Auth Service için yerel geliştirme ENV dosyalarını TEK KOMUTLA tamamlar:
//
//   node ops/dev.mjs keys            # .env ve .env.dev (hiçbiri yoksa .env.example'dan .env.dev oluşturur)
//   node ops/dev.mjs keys .env.local # belirli dosya(lar)
//
// Her çalıştırmada sırayla:
//   1. .env.example'da olup dosyada HİÇ bulunmayan ayarlar (açıklamalarıyla) dosyanın sonuna eklenir.
//   2. Boş/eksik anahtarlar (JWT, HMAC, TOTP, servis sırları) üretilip doldurulur.
//   3. Sonunda Auth'un açılış kuralları kontrol edilir; ayrıca başka bir betik çalıştırmaya gerek yoktur.
// Kurallar:
// - Var olan satıra DOKUNMAZ: dolu bir değerin üzerine asla yazmaz, sırasını bozmaz. Tek istisna staleDefaults:
//   değeri tam olarak ESKİ varsayılana eşit olan ayar (ör. JWT_ACCESS_TTL_SECONDS=300) yeni varsayılana çekilir.
// - Aynı anahtar tüm dosyalarda aynı olur (.env, .env.dev'in kopyasıdır; ops/env durumu bunu karşılaştırır).
//   Bir dosyada dolu değer varsa diğerlerine o kopyalanır; hiçbirinde yoksa bir kez üretilir.
// - Tekrar çalıştırmak güvenlidir: eksik yoksa hiçbir şeyi değiştirmez.
// - Üretilen değerleri ekrana YAZMAZ.
// - Bu anahtarlar yerel geliştirme içindir; production için ayrı üretilir ve sır yöneticisinde tutulur.
// Anahtar döndürme: --rotate-totp, --rotate-hmac (aşağıda).
async function keysCommand(argv) {
  const { buildReport } = envRules;
  const generators = {
    // Java'nın okuyabildiği PKCS#8 DER (Base64, tek satır), 3072 bit RSA.
    AUTH_JWT_PRIVATE_KEY: () =>
      generateKeyPairSync("rsa", { modulusLength: 3072 })
        .privateKey.export({ type: "pkcs8", format: "der" })
        .toString("base64"),
    // E-posta kodu, deneme sayacı, kurtarma kodu ve OAuth türetmesi için 32 bayt HMAC anahtarı.
    AUTH_OTP_HMAC_KEY: () => randomBytes(32).toString("base64"),
    // TOTP sırlarının AES-256-GCM anahtarı: tam 32 bayt.
    AUTH_TOTP_ENCRYPTION_KEY: () => randomBytes(32).toString("base64"),
    // Admin servisinin Auth'a servis kimligiyle (client credentials) baglanma gizli anahtari (B4): 256 bit, URL-guvenli.
    AUTH_ADMIN_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    // Servisler arası olay girişi (events:ingest) istemcileri: her biri ayrı 256 bit gizli anahtar.
    AUTH_SOCIAL_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_COMMUNITY_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    // Engel sorgusu (blocks:check) istemcileri: messaging ve lfp -> social.
    AUTH_MESSAGING_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_LFP_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    // User Service ve Library entegrasyonu: library/hardware/review -> user, recommendation -> library.
    AUTH_LIBRARY_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_HARDWARE_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_REVIEW_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_GAME_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_RECOMMENDATION_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_CONTENT_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    AUTH_PERFORMANCE_SERVICE_CLIENT_SECRET: () => randomBytes(32).toString("base64url"),
    // Sır değil, dev varsayılanı: Auth olaylarının (UserRegistered, AccountStatusChanged, AccountEmailChanged) gideceği servisler.
    // Satır yoksa veya boşsa "user" yazılır; dolu listeye dokunulmaz (aşağıdaki uyarı yalnız hatırlatır). Hedef erken açılırsa
    // Auth üstel geri çekilmeyle yeniden dener, olay kaybolmaz; User ayağa kalkınca birikenler teslim edilir.
    AUTH_EVENT_TARGETS: () => "user",
    // Mail Service: kuyruktaki doğrulama kodu/bağlantısını şifreleyen AES-256-GCM anahtarı (tam 32 bayt).
    MAIL_PAYLOAD_ENCRYPTION_KEY: () => randomBytes(32).toString("base64"),
  };

  // Mail Service SMTP ayarları: sır üretilmez. Satır yoksa dosyanın sonuna BOŞ eklenir; var olan satıra (dolu ya da boş)
  // asla dokunulmaz. Sağlayıcı seçilince değerler elle doldurulur. Boşken dev'de Mailpit varsayılanları geçerlidir.
  const blankDefaults = [
    "MAIL_SMTP_HOST",
    "MAIL_SMTP_PORT",
    "MAIL_SMTP_USERNAME",
    "MAIL_SMTP_PASSWORD",
    "MAIL_SMTP_SECURITY",
    "MAIL_FROM_ADDRESS",
    "MAIL_ALLOWED_LINK_HOSTS",
  ];

  // Eski varsayılanlar: değer TAM OLARAK eskiyse yeni varsayılana çekilir; özelleştirilmiş değere dokunulmaz.
  // JWT_ACCESS_TTL_SECONDS: access token ömrü 300 sn'den 60 sn'ye indi (askıya alınan hesabın diğer servislerde görünürlüğü).
  const staleDefaults = {
    JWT_ACCESS_TTL_SECONDS: { from: "300", to: "60" },
  };

  const lineOf = (name) => new RegExp(`^${name}=(.*?)\\r?$`, "m");

  const flags = new Set(argv.filter((arg) => arg.startsWith("--")));
  const requested = argv.filter((arg) => !arg.startsWith("--"));
  const examplePath = resolve(root, ".env.example");
  const rotating = flags.has("--rotate-totp") || flags.has("--rotate-hmac");
  let targets = (requested.length ? requested : [".env", ".env.dev"])
    .map((file) => resolve(root, file))
    .filter((file) => existsSync(file));

  // Hiç ENV dosyası yoksa (yeni klon): .env.dev, .env.example'dan oluşturulur ve aşağıda tamamlanır.
  if (targets.length === 0 && requested.length === 0 && !rotating && existsSync(examplePath)) {
    const created = resolve(root, ".env.dev");
    writeFileSync(created, readFileSync(examplePath, "utf8"));
    targets = [created];
    console.log(".env.dev oluşturuldu (.env.example'dan).");
  }

  if (targets.length === 0) {
    console.error("Dosya bulunamadı. Önce .env.example dosyasını .env.dev olarak kopyalayın.");
    process.exit(1);
  }

  /** .env.example'daki KEY=değer satırları ve hemen üstlerindeki açıklama blokları. */
  function exampleEntries() {
    if (!existsSync(examplePath)) return [];
    const entries = [];
    let comments = [];
    for (const rawLine of readFileSync(examplePath, "utf8").split(/\r?\n/)) {
      if (rawLine.startsWith("#")) {
        comments.push(rawLine);
        continue;
      }
      const found = rawLine.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
      if (found && !entries.some((entry) => entry.name === found[1])) {
        entries.push({ name: found[1], line: rawLine, comments });
      }
      comments = [];
    }
    return entries;
  }

  // Anahtar rotasyonu (S9). Normal üretim yapılmaz; yalnız istenen anahtar döndürülür ve çıkılır.
  //   --rotate-totp : AUTH_TOTP_ENCRYPTION_KEY güncel anahtarı AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS listesine ("<sürüm>=<anahtar>")
  //                   taşınır, AUTH_TOTP_ENCRYPTION_KEY_VERSION bir artar, yeni anahtar üretilir. Eski sürümdeki TOTP sırları
  //                   kullanıcı TOTP ile girdikçe yeni anahtarla yeniden şifrelenir; liste tüm kayıtlar geçince temizlenebilir.
  //   --rotate-hmac : AUTH_OTP_HMAC_KEY güncel anahtarı AUTH_OTP_HMAC_PREVIOUS_KEY'e taşınır, yeni anahtar üretilir. Önceki anahtar
  //                   zaten doluysa üzerine yazmak kullanılmamış kurtarma kodlarını geçersiz kılar; yalnız --force ile yapılır.
  // Aynı yeni anahtarlar tüm hedef dosyalara yazılır (başvuru: ilk dolu dosya). Çıktıda anahtar değeri yoktur.
  if (flags.has("--rotate-totp") || flags.has("--rotate-hmac")) {
    const setLine = (body, name, newContent, eol) => {
      // Eşleşme satır sonundaki CR karakterini de kapsar; CRLF dosyalarda korunur.
      if (lineOf(name).test(body)) {
        return body.replace(lineOf(name), (line) => `${name}=${newContent}${line.endsWith("\r") ? "\r" : ""}`);
      }
      return `${body}${body.endsWith("\n") || body === "" ? "" : eol}${name}=${newContent}${eol}`;
    };
    const read = (name) => {
      for (const file of targets) {
        const found = readFileSync(file, "utf8").match(lineOf(name));
        if (found && found[1].trim() !== "") return found[1].trim();
      }
      return "";
    };
    const changes = {};
    if (flags.has("--rotate-totp")) {
      const current = read("AUTH_TOTP_ENCRYPTION_KEY");
      if (current === "") {
        console.error("AUTH_TOTP_ENCRYPTION_KEY boş: önce node ops/dev.mjs keys ile üretin.");
        process.exit(1);
      }
      const oldVersion = Number.parseInt(read("AUTH_TOTP_ENCRYPTION_KEY_VERSION") || "1", 10);
      if (!Number.isInteger(oldVersion) || oldVersion < 1 || oldVersion >= 32767) {
        console.error("AUTH_TOTP_ENCRYPTION_KEY_VERSION geçerli bir sürüm numarası değil.");
        process.exit(1);
      }
      const previous = read("AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS");
      changes.AUTH_TOTP_ENCRYPTION_KEY = generators.AUTH_TOTP_ENCRYPTION_KEY();
      changes.AUTH_TOTP_ENCRYPTION_KEY_VERSION = String(oldVersion + 1);
      changes.AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS = `${previous ? `${previous},` : ""}${oldVersion}=${current}`;
    }
    if (flags.has("--rotate-hmac")) {
      const current = read("AUTH_OTP_HMAC_KEY");
      if (current === "") {
        console.error("AUTH_OTP_HMAC_KEY boş: önce node ops/dev.mjs keys ile üretin.");
        process.exit(1);
      }
      if (read("AUTH_OTP_HMAC_PREVIOUS_KEY") !== "" && !flags.has("--force")) {
        console.error("AUTH_OTP_HMAC_PREVIOUS_KEY zaten dolu: üzerine yazmak kullanılmamış kurtarma kodlarını geçersiz kılar. "
          + "Eski kodlar yenilendikten sonra --force ile tekrarlayın.");
        process.exit(1);
      }
      changes.AUTH_OTP_HMAC_PREVIOUS_KEY = current;
      changes.AUTH_OTP_HMAC_KEY = generators.AUTH_OTP_HMAC_KEY();
    }
    for (const file of targets) {
      let body = readFileSync(file, "utf8");
      const eol = body.includes("\r\n") ? "\r\n" : "\n";
      for (const [name, newContent] of Object.entries(changes)) body = setLine(body, name, newContent, eol);
      writeFileSync(file, body);
      const shown = file.startsWith(root) ? file.slice(root.length + 1) : file;
      console.log(`${shown}: döndürüldü -> ${Object.keys(changes).join(", ")}`);
    }
    console.log("Auth Service'i yeniden başlatın. Eski anahtar listeleri, eski kayıtlar yenilenene kadar kalmalıdır.");
    process.exit(0);
  }

  // Herhangi bir dosyada zaten dolu olan değeri bul; diğer dosyalara onu yaz.
  const values = {};
  for (const name of Object.keys(generators)) {
    for (const file of targets) {
      const match = readFileSync(file, "utf8").match(lineOf(name));
      if (match && match[1].trim() !== "") {
        values[name] = match[1].trim();
        break;
      }
    }
  }

  const example = exampleEntries();

  for (const file of targets) {
    let text = readFileSync(file, "utf8");
    const eol = text.includes("\r\n") ? "\r\n" : "\n";
    const filled = [];

    // 1) .env.example'da olup dosyada hiç bulunmayan ayarları açıklamalarıyla sona ekle (var olan satıra dokunulmaz).
    const synced = [];
    for (const entry of example) {
      if (lineOf(entry.name).test(text)) continue;
      if (synced.length === 0) {
        if (!text.endsWith("\n")) text += eol;
        text += `${eol}# --- .env.example ile eşitlendi: eksik ayarlar eklendi ---${eol}`;
      }
      for (const comment of entry.comments) text += `${comment}${eol}`;
      text += `${entry.line}${eol}`;
      synced.push(entry.name);
    }

    for (const [name, generate] of Object.entries(generators)) {
      const match = text.match(lineOf(name));
      if (match && match[1].trim() !== "") continue;
      values[name] ??= generate();
      const value = values[name];
      if (match) {
        // Eşleşme satır sonundaki CR karakterini de kapsar; CRLF dosyalarda korunur.
        text = text.replace(lineOf(name), (line) => `${name}=${value}${line.endsWith("\r") ? "\r" : ""}`);
      } else {
        if (!text.endsWith("\n")) text += eol;
        text += `${name}=${value}${eol}`;
      }
      filled.push(name);
    }

    const added = [];
    for (const name of blankDefaults) {
      if (text.match(lineOf(name))) continue;
      if (!text.endsWith("\n")) text += eol;
      text += `${name}=${eol}`;
      added.push(name);
    }

    const refreshed = [];
    for (const [name, change] of Object.entries(staleDefaults)) {
      const found = text.match(lineOf(name));
      if (found && found[1].trim() === change.from) {
        // Eşleşme satır sonundaki CR karakterini de kapsar; CRLF dosyalarda korunur.
        text = text.replace(lineOf(name), (line) => `${name}=${change.to}${line.endsWith("\r") ? "\r" : ""}`);
        refreshed.push(`${name} (${change.from} -> ${change.to})`);
      }
    }

    const eventTargets = (text.match(lineOf("AUTH_EVENT_TARGETS"))?.[1] ?? "").split(",").map((name) => name.trim());
    const missingTargets = eventTargets.includes("user") ? [] : ["AUTH_EVENT_TARGETS ('user' yok; dolu liste korunur, elle ekleyin)"];

    if (synced.length || filled.length || refreshed.length || added.length) writeFileSync(file, text);
    const shown = file.startsWith(root) ? file.slice(root.length + 1) : file;
    const report = [];
    if (synced.length) {
      const names = synced.length > 8 ? `${synced.slice(0, 8).join(", ")} ve ${synced.length - 8} ayar daha` : synced.join(", ");
      report.push(`.env.example'dan eklendi (${synced.length}) -> ${names}`);
    }
    if (filled.length) report.push(`dolduruldu -> ${filled.join(", ")}`);
    if (added.length) report.push(`boş eklendi -> ${added.join(", ")}`);
    if (refreshed.length) report.push(`güncellendi -> ${refreshed.join(", ")}`);
    if (missingTargets.length) report.push(`uyarı -> ${missingTargets.join(", ")}`);
    console.log(`${shown}: ${report.length ? report.join("; ") : "eksik ayar yok"}`);
  }

  // 3) Auth'un açılış kuralları: ayrı bir betik çalıştırmaya gerek kalmadan sonuç burada görünür.
  const finalReport = buildReport(targets.map((file) => ({
    shown: file.startsWith(root) ? file.slice(root.length + 1) : file,
    body: readFileSync(file, "utf8"),
  })));
  console.log("\nKontrol:");
  for (const line of finalReport.lines) console.log(line);
  process.exit(finalReport.failures ? 1 : 0);
}
// #endregion keys

// #region env-check
// Auth Service ENV kontrolü (yalnız okur). Normalde AYRICA çalıştırmanız gerekmez:
// node ops/dev.mjs keys eksikleri tamamlar ve sonunda bu kontrolü kendisi yapar.
// Bu betik, dosyaya dokunmadan yalnız raporu görmek (ör. CI) içindir.
//
//   node ops/dev.mjs env-check                 # .env ve .env.dev (varsa)
//   node ops/dev.mjs env-check .env.local      # belirli dosya(lar)
//
// - Hiçbir dosyayı değiştirmez, ağa çıkmaz, süreç başlatmaz; anahtar/parola DEĞERLERİNİ ekrana yazmaz.
// - Çıkış kodu 1: Auth'un açılmayacağı bir hata var. Uyarılar (WARN) çıkış kodunu etkilemez.
async function envCheckCommand(argv) {
  const { buildReport } = envRules;
  const requested = argv;
  const targets = (requested.length ? requested : [".env", ".env.dev"])
    .map((file) => resolve(root, file))
    .filter((file) => existsSync(file));

  if (targets.length === 0) {
    console.error("Dosya bulunamadı. node ops/dev.mjs keys dosyayı .env.example'dan oluşturur.");
    process.exit(1);
  }

  const entries = targets.map((file) => ({
    shown: file.startsWith(root) ? file.slice(root.length + 1) : file,
    body: readFileSync(file, "utf8"),
  }));
  const report = buildReport(entries);
  for (const line of report.lines) console.log(line);
  process.exit(report.failures ? 1 : 0);
}
// #endregion env-check

// #region start
async function devCommand(argv) {
  const { assertNodeSupported, parseDevEnv } = accountsLib;
  const { DEFAULT_MAIL_USER, applyEnvChanges, describeSmtp, normalizeAppPassword, smtpOnSettings, validateGmailAddress } = mailLib;
  const ENV_DEV = resolve(root, ".env.dev");
  const FRONTEND = resolve(root, "frontend");
  const PROJECT = "ltz-dev";
  const isWindows = process.platform === "win32";

  function step(index, total, title) {
    console.log(`\n▶ ${index}/${total} ${title}`);
  }

  const parsed = parseArgs(argv);
  try {
    validateArgs(parsed);
  } catch (error) {
    fail(error.message);
  }
  const { command, rest, flags, withServices } = parsed;
  const dryRun = flags.has("--dry-run");

  /** Alt betiği çalıştırır; çıktı doğrudan terminale akar. Başarısızlıkta kurulum durur. */
  function runNode(subcommand, args = []) {
    const label = `node ops/dev.mjs ${subcommand} ${args.join(" ")}`.trim();
    if (dryRun) {
      console.log(`  (kuru çalıştırma) ${label}`);
      return;
    }
    const result = spawnSync(process.execPath, [resolve(root, "ops", "dev.mjs"), subcommand, ...args], { cwd: root, stdio: "inherit" });
    if (result.status !== 0) fail(`${label} başarısız oldu (yukarıdaki çıktıya bak).`);
  }

  function compose(args, options = {}) {
    return spawnSync("docker", ["compose", "-p", PROJECT, "--env-file", ".env.dev", "--profile", "dev-mail", ...args], {
      cwd: root,
      stdio: "inherit",
      ...options
    });
  }

  function dockerRunning() {
    return spawnSync("docker", ["info"], { stdio: "ignore" }).status === 0;
  }

  async function portOpen(port) {
    try {
      const response = await fetch(`http://localhost:${port}/`, { signal: AbortSignal.timeout(1500), redirect: "manual" });
      return response.status > 0;
    } catch {
      return false;
    }
  }

  // ---------------------------------------------------------------- yardımcı komutlar
  if (command === "stop") {
    if (!dockerRunning()) fail("Docker çalışmıyor.");
    const result = compose(["down", "--remove-orphans"]);
    process.exit(result.status ?? 1);
  }

  if (command === "reset") {
    if (!dockerRunning()) fail("Docker çalışmıyor.");
    console.log("Bu komut servisleri durdurur ve TÜM yerel veriyi siler (veritabanı, yüklenen medya, hesaplar). Geri alınamaz.");
    const answer = process.stdin.isTTY ? await ask("Devam etmek için 'evet' yaz: ") : "";
    if (answer.toLowerCase() !== "evet") fail("Vazgeçildi.");
    const result = compose(["down", "-v", "--remove-orphans"]);
    if (result.status === 0) console.log("✓ Sıfırlandı. Yeniden kurmak için: node ops/dev.mjs");
    process.exit(result.status ?? 1);
  }

  if (command === "status") {
    if (!existsSync(ENV_DEV)) fail(".env.dev yok. Kurmak için: node ops/dev.mjs");
    const state = describeSmtp(readFileSync(ENV_DEV, "utf8"));
    console.log(state.real ? `E-posta: GERÇEK (${state.host}), şifre ${state.passwordSet ? "dolu" : "BOŞ"}` : "E-posta: Mailpit (gerçek mail gitmez)");
    console.log(`Frontend (5001): ${(await portOpen(5001)) ? "çalışıyor" : "kapalı"}`);
    if (dockerRunning()) {
      const containers = spawnSync("docker", ["ps", "--format", "{{.Names}}\t{{.Status}}", "--filter", `name=${PROJECT}-`], { encoding: "utf8" });
      console.log(containers.stdout.trim() || "Servis konteyneri yok.");
    } else console.log("Docker çalışmıyor.");
    process.exit(0);
  }

  // ---------------------------------------------------------------- start (varsayılan)
  const TOTAL = 5;

  step(1, TOTAL, "Ön koşullar");
  try {
    assertNodeSupported();
  } catch (error) {
    fail(error.message);
  }
  if (!dryRun && !dockerRunning()) fail("Docker çalışmıyor. Docker Desktop'ı başlat ve tekrar dene.");
  console.log(`  ✓ Node ${process.versions.node}${dryRun ? "" : ", Docker çalışıyor"}`);

  step(2, TOTAL, "Anahtarlar ve .env.dev");
  if (flags.has("--skip-keys")) console.log("  (atlandı: --skip-keys)");
  else runNode("keys");

  step(3, TOTAL, "E-posta");
  await configureMail();

  step(4, TOTAL, "Servisler ve test hesapları");
  const extraServices = await resolveExtraServices();
  if (flags.has("--no-services")) console.log("  (atlandı: --no-services; yalnız ayarlar yapıldı)");
  else {
    const noBuild = flags.has("--no-build") ? ["--no-build"] : [];
    const targets = extraServices.length > 0 ? [...CORE_SERVICES, ...extraServices] : [];
    // Hardware seçiliyse katalog, Hardware servisi AYAĞA KALKMADAN önce içe aktarılır: önce diğer servisler (Postgres dahil), sonra katalog, en son Hardware.
    const catalogFirst = wantsHardware() && !flags.has("--no-catalog");
    runNode("accounts", ["up", ...noBuild, ...(catalogFirst ? targets.filter((name) => name !== "hardware-service") : targets)]);
    if (catalogFirst) {
      importHardwareCatalog();
      runNode("accounts", ["up", "--no-seed", ...noBuild, "hardware-service"]);
    }
    if (extraServices.length > 0 && !dryRun) await reportServices(extraServices);
  }

  step(5, TOTAL, "Frontend");
  await startFrontend();

  // ---------------------------------------------------------------- adımlar
  /** `--all` veya `--with=hardware-service` ile Hardware servisi isteniyor mu? (kuru çalıştırmada da doğru cevap verir) */
  function wantsHardware() {
    return flags.has("--all") || withServices.includes("hardware-service");
  }

  /**
   * Hardware kataloğunu (PC parça veri seti) içe aktarır: ops/hardware-catalog-import.mjs, `--standalone` ile (çalışan Hardware konteyneri gerekmez).
   * Veri seti geçici klasörde yoksa SHA-256 doğrulamalı olarak indirilir. Flyway göçlerini içe aktarma komutu uygular.
   * Hata kurulumu durdurmaz: uyarı yazılır, Hardware yine başlar (katalog sonradan el ile aktarılabilir).
   */
  function importHardwareCatalog() {
    const importer = resolve(root, "ops", "hardware-catalog-import.mjs");
    const dataset = resolve(tmpdir(), "ltz-pc-part-dataset");
    const manual = `node ops/hardware-catalog-import.mjs import "${dataset}" --standalone`;
    console.log("  Hardware kataloğu, Hardware servisi başlamadan önce içe aktarılıyor …");
    if (dryRun) {
      console.log(`  (kuru çalıştırma) indir (yoksa) → ${manual}`);
      return;
    }
    const importerRun = (args) => spawnSync(process.execPath, [importer, ...args], { cwd: root, stdio: "inherit" });
    if (!existsSync(resolve(dataset, "download.json")) && importerRun(["download", dataset]).status !== 0) {
      console.log(`  ⚠ Katalog indirilemedi; atlandı. Sonra dene: ${manual}`);
      return;
    }
    if (!flags.has("--no-build") && compose(["build", "hardware-service"]).status !== 0) {
      console.log(`  ⚠ Hardware imajı derlenemedi; katalog atlandı. Sonra dene: ${manual}`);
      return;
    }
    if (importerRun(["import", dataset, "--standalone"]).status !== 0) {
      console.log(`  ⚠ Katalog içe aktarılamadı (veri seti eskiyse ${dataset} klasörünü silip tekrar dene). El ile: ${manual}`);
      return;
    }
    console.log("  ✓ Hardware kataloğu hazır");
  }

  /** Compose'taki tüm servis adları (profil dahil). */
  function composeServices() {
    const result = spawnSync("docker", ["compose", "-p", PROJECT, "--env-file", existsSync(ENV_DEV) ? ".env.dev" : ".env.example", "--profile", "dev-mail", "config", "--services"], { cwd: root, encoding: "utf8" });
    return result.status === 0 ? result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean) : [];
  }

  /** `--with=` / `--all` ile istenen ek servisler: doğrulanır ve Docker belleği yeter mi diye BAŞLAMADAN denetlenir. */
  async function resolveExtraServices() {
    if (!flags.has("--all") && withServices.length === 0) return [];
    if (dryRun) {
      console.log(`  (kuru çalıştırma) ek servisler: ${flags.has("--all") ? "tümü" : withServices.join(", ")}`);
      return [];
    }
    const available = composeServices();
    if (available.length === 0) fail("Compose servis listesi okunamadı (docker-compose.yml ve .env.dev geçerli mi?).");
    const wanted = flags.has("--all") ? available : withServices;
    const unknown = wanted.filter((name) => !available.includes(name));
    if (unknown.length > 0) fail(`Bilinmeyen servis: ${unknown.join(", ")}. Olanlar: ${available.join(", ")}`);
    const extra = [...new Set(wanted)].filter((name) => !CORE_SERVICES.includes(name));
    const info = spawnSync("docker", ["info", "--format", "{{.MemTotal}}"], { encoding: "utf8" });
    const dockerMemBytes = Number.parseInt(info.stdout.trim(), 10);
    if (Number.isFinite(dockerMemBytes)) {
      const verdict = memoryVerdict({ services: [...CORE_SERVICES, ...extra], dockerMemBytes });
      const need = (verdict.needMb / 1024).toFixed(1);
      const have = (verdict.haveMb / 1024).toFixed(1);
      if (!verdict.ok && !flags.has("--force")) {
        fail(`${extra.length + CORE_SERVICES.length} servis yaklaşık ${need} GB ister, Docker'a ${have} GB ayrılmış. Servisler bellek yetmediği için ölür. ` +
          "Çözüm (biri yeter): daha az servis seç (--with=a,b), ya da Docker Desktop > Settings > Resources bölümünden belleği artır (ya da ~/.wslconfig içine memory=12GB yaz ve `wsl --shutdown`). Yine de denemek için --force.");
      }
      console.log(`  Bellek: ~${need} GB gerekir, Docker'da ${have} GB var ✓`);
    }
    return extra;
  }

  function serviceState(name) {
    const inspect = spawnSync("docker", ["inspect", "-f", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}|{{.State.ExitCode}}|{{.State.OOMKilled}}", `${PROJECT}-${name}-1`], { encoding: "utf8" });
    if (inspect.status !== 0) return { status: "yok", exit: 0, oom: false, started: false };
    const [status = "", exit = "0", oom = "false"] = inspect.stdout.trim().split("|");
    const logs = spawnSync("docker", ["logs", "--tail", "200", `${PROJECT}-${name}-1`], { encoding: "utf8" });
    return { status, exit: Number(exit), oom: oom === "true", started: /Started \w+Application/.test(`${logs.stdout ?? ""}${logs.stderr ?? ""}`) };
  }

  /** Ek servislerin hazır olmasını bekler (en çok 4 dk) ve her birini raporlar; hata kurulumu durdurmaz, çekirdek çalışmaya devam eder. */
  async function reportServices(names) {
    console.log(`  Ek servisler bekleniyor (${names.length}) …`);
    const deadline = Date.now() + 240_000;
    const pending = new Set(names);
    const results = new Map();
    while (pending.size > 0 && Date.now() < deadline) {
      for (const name of [...pending]) {
        const state = serviceState(name);
        if (state.status === "healthy" || (state.status === "running" && state.started)) {
          results.set(name, "ok");
          pending.delete(name);
        } else if (state.status === "exited" || state.status === "dead" || state.status === "yok") {
          results.set(name, state.oom ? "bellek yetmedi (OOM)" : `kapandı (çıkış kodu ${state.exit})`);
          pending.delete(name);
        }
      }
      if (pending.size > 0) await new Promise((resolveWait) => setTimeout(resolveWait, 4000));
    }
    for (const name of pending) results.set(name, "4 dakikada hazır olmadı");
    let broken = 0;
    for (const name of names) {
      const result = results.get(name);
      if (result === "ok") console.log(`  ✓ ${name}`);
      else {
        broken += 1;
        console.log(`  ✗ ${name}: ${result}. İncele: docker compose -p ${PROJECT} logs ${name} --tail 50`);
      }
    }
    if (broken > 0) console.log(`  ! ${broken} servis ayağa kalkmadı; çekirdek (giriş, kayıt, profil) çalışmaya devam eder.`);
  }

  async function configureMail() {
    if (dryRun || !existsSync(ENV_DEV)) {
      console.log(dryRun ? "  (kuru çalıştırma) uygulama şifresi sorulur; boş geçilirse Mailpit" : "  (.env.dev henüz yok; adım atlandı)");
      return;
    }
    const state = describeSmtp(readFileSync(ENV_DEV, "utf8"));
    const decision = decideMailStep({
      skip: flags.has("--no-mail"),
      real: state.real,
      passwordSet: state.passwordSet,
      interactive: Boolean(process.stdin.isTTY),
      envPassword: process.env.DEV_MAIL_PASSWORD
    });

    if (decision === "skip") return console.log("  (atlandı: --no-mail)");
    if (decision === "already") return console.log(`  ✓ Gerçek e-posta açık (${state.host})`);
    if (decision === "mailpit") return console.log("  Mailpit (gerçek mail gitmez). Gerçek için: node ops/dev.mjs mail on");

    const user = process.env.DEV_MAIL_USER ?? DEFAULT_MAIL_USER;
    let password = process.env.DEV_MAIL_PASSWORD;
    if (decision === "ask") {
      console.log(`  Gerçek e-posta için Google uygulama şifresi gerekir (hesap: ${user}). Boş bırakırsan Mailpit kullanılır.`);
      password = await askHidden("  Uygulama şifresi (yazarken görünmez; boş = Mailpit): ");
      if (password.trim() === "") return console.log("  ✓ Mailpit (şifre girilmedi). Sonra açmak için: node ops/dev.mjs mail on");
    }
    try {
      const settings = smtpOnSettings(validateGmailAddress(user), normalizeAppPassword(password));
      writeAtomic(ENV_DEV, applyEnvChanges(readFileSync(ENV_DEV, "utf8"), settings));
      console.log("  ✓ Gerçek e-posta (Gmail SMTP) .env.dev dosyasına yazıldı. Şifre ekrana basılmadı.");
    } catch (error) {
      console.log(`  ! ${error.message} Mailpit ile devam ediliyor. Sonra: node ops/dev.mjs mail on`);
    }
  }

  async function startFrontend() {
    const env = existsSync(ENV_DEV) ? parseDevEnv(readFileSync(ENV_DEV, "utf8")) : {};
    const domain = process.env.DEV_ACCOUNTS_DOMAIN ?? env.DEV_ACCOUNTS_DOMAIN ?? "ltz.com";
    const realMail = existsSync(ENV_DEV) && describeSmtp(readFileSync(ENV_DEV, "utf8")).real;

    if (flags.has("--no-frontend")) {
      console.log("  (atlandı: --no-frontend)");
      return printSummary({ domain, realMail, frontendRunning: false });
    }
    const running = dryRun ? false : await portOpen(5001);
    if (running) {
      console.log("  ✓ Frontend zaten çalışıyor: http://localhost:5001");
      return printSummary({ domain, realMail, frontendRunning: true });
    }

    const modules = resolve(FRONTEND, "node_modules");
    const lock = resolve(FRONTEND, "package-lock.json");
    const install = needsInstall({
      modulesTime: existsSync(modules) ? statSync(resolve(modules, ".package-lock.json"), { throwIfNoEntry: false })?.mtimeMs ?? statSync(modules).mtimeMs : null,
      lockTime: existsSync(lock) ? statSync(lock).mtimeMs : null
    });
    if (dryRun) {
      console.log(`  (kuru çalıştırma) ${install ? "npm ci; " : ""}npx next dev -H localhost -p 5001`);
      return printSummary({ domain, realMail, frontendRunning: true });
    }
    if (install) {
      console.log("  Bağımlılıklar kuruluyor (npm ci) …");
      const result = spawnSync("npm", ["ci", "--no-audit", "--no-fund"], { cwd: FRONTEND, stdio: "inherit", shell: isWindows });
      if (result.status !== 0) fail("npm ci başarısız oldu.");
    }
    printSummary({ domain, realMail, frontendRunning: true });
    console.log("\n  Frontend başlıyor (durdurmak için Ctrl+C). Servisler arka planda çalışmaya devam eder.\n");
    const server = spawn("npx", ["next", "dev", "-H", "localhost", "-p", "5001"], { cwd: FRONTEND, stdio: "inherit", shell: isWindows });
    server.on("exit", (code) => process.exit(code ?? 0));
  }

  function printSummary(input) {
    console.log("");
    for (const line of summaryLines(input)) console.log(line);
  }
}
// #endregion start

// #region dispatch
const E2E_NAMES = ["hardware", "performance", "auth", "game-auth", "game-community", "game-review", "library", "game-provider-pilot"];

const HELP_TEXT = `LTZ yerel geliştirme aracı (node ops/dev.mjs)

Kurulum ve günlük kullanım
  node ops/dev.mjs [--no-frontend --no-mail --no-build --no-services --no-catalog --skip-keys --dry-run --with=a,b --all --force]
  node ops/dev.mjs stop | reset | status
  node ops/dev.mjs mail on|off|status [--file <yol>]

Alt komutlar
  node ops/dev.mjs keys [dosya…] [--rotate-totp] [--rotate-hmac] [--force]
  node ops/dev.mjs env-check [dosya…]
  node ops/dev.mjs accounts up|seed|list|totp <ad>
  node ops/dev.mjs site-url <adres> [--production]

Gerçek-HTTP kabul testleri (yalnız geçici kaynaklar; gerçek ENV okunmaz)
  node ops/dev.mjs e2e hardware [--with-user]
  node ops/dev.mjs e2e performance | auth | game-auth | game-community | game-review | library
  node ops/dev.mjs e2e game-provider-pilot --allow-env-files [--itad]   # YALNIZ bu komut .env/.env.dev okur
`;

/** Pilot yalnız açık izinle ENV dosyası okur: `--allow-env-files [--itad]`. */
function pilotArgsAllowed(args) {
  return args[0] === "--allow-env-files" && (args.length === 1 || (args.length === 2 && args[1] === "--itad"));
}

async function guarded(task) {
  try {
    await task();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

async function e2eCommand(args) {
  const [name, ...rest] = args;
  switch (name) {
    case "hardware":
      return guarded(() => hardwareE2e.runHardwareAuthE2e(root, console.log, { withUser: rest.includes("--with-user") }));
    case "performance":
      return guarded(() => performanceE2e.runPerformanceAuthE2e(root));
    case "auth":
      return guarded(() => smokeE2e.runAuthSmoke(root));
    case "game-auth":
      return guarded(() => gameAuthE2e.runGameAuthE2e(root));
    case "game-community":
      return guarded(() => gameCommunityE2e.runGameCommunityE2e(root));
    case "game-review":
      return guarded(() => gameReviewE2e.runGameReviewE2e(root));
    case "library":
      return libraryPhase1E2e();
    case "game-provider-pilot":
      return guarded(async () => {
        if (!pilotArgsAllowed(rest)) throw new Error("ENV okuması için açık izin gerekli: --allow-env-files [--itad]");
        const values = {
          ...pilotE2e.providerValues(fs.readFileSync(path.join(root, ".env"), "utf8")),
          ...pilotE2e.providerValues(fs.readFileSync(path.join(root, ".env.dev"), "utf8"))
        };
        await pilotE2e.runProviderPilot(root, values, console.log, rest[1] === "--itad" ? "itad" : "igdb");
      });
    default:
      console.error(`Bilinmeyen e2e: ${name ?? "(yok)"} (${E2E_NAMES.join(" | ")})`);
      process.exitCode = 2;
  }
}

/** Komut yönlendirici: ayrı alt komutlar önce; geri kalanı başlatma akışı (devCommand). */
export async function main(argv = process.argv.slice(2)) {
  const [first, ...tail] = argv;
  switch (first) {
    case "accounts":
      return accountsCommand(tail);
    case "keys":
      return keysCommand(tail);
    case "env-check":
      return envCheckCommand(tail);
    case "mail":
      return mailCommand(tail);
    case "site-url":
      process.exitCode = await siteUrlMain(tail);
      return;
    case "e2e":
      return e2eCommand(tail);
    case "help":
    case "--help":
    case "-h":
      console.log(HELP_TEXT);
      return;
    default:
      return devCommand(argv);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await main();
}
// #endregion dispatch
