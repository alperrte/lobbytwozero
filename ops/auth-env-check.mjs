#!/usr/bin/env node
// Auth Service ENV kontrolü: Auth'un AÇILIŞTA reddedeceği ayarları Docker'ı başlatmadan önce yakalar.
//
//   node ops/auth-env-check.mjs                 # .env ve .env.dev (varsa)
//   node ops/auth-env-check.mjs .env.local      # belirli dosya(lar)
//
// - Yalnız OKUR; hiçbir dosyayı değiştirmez, ağa çıkmaz, süreç başlatmaz.
// - Anahtar/parola DEĞERLERİNİ ekrana yazmaz; yalnız ayar adı ve sorunun türünü söyler.
// - Çıkış kodu 1: Auth'un açılmayacağı bir hata var. Uyarılar (WARN) çıkış kodunu etkilemez.
// - Eksik/hatalı anahtarı üretmek için: node ops/generate-auth-dev-keys.mjs

import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const requested = process.argv.slice(2);
const targets = (requested.length ? requested : [".env", ".env.dev"])
  .map((file) => resolve(root, file))
  .filter((file) => existsSync(file));

if (targets.length === 0) {
  console.error("Dosya bulunamadı. Önce .env.example dosyasını .env.dev olarak kopyalayın.");
  process.exit(1);
}

const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;
const MAX_KEY_VERSION = 32767;

/** Satırları ad -> değer sözlüğüne çevirir (tırnaklar atılır). Değerler yalnız bu işlemde tutulur, asla yazdırılmaz. */
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

/** Java'daki SecretCipher/VerificationCodeHasher açılış doğrulamalarının aynısı. Sorun metinleri sabittir. */
function check(file, settings) {
  const findings = [];
  const add = (level, name, problem, fix) => findings.push({ level, name, problem, fix });
  const regenerate = "node ops/generate-auth-dev-keys.mjs ile üretin";

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
    for (const entry of previous.split(",").map((part) => part.trim()).filter(Boolean)) {
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
        "node ops/generate-auth-dev-keys.mjs --rotate-totp kullanın ya da listeyi düzeltin");
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
      add("WARN", "AUTH_OTP_HMAC_PREVIOUS_KEY", "güncel anahtarla aynı; rotasyon etkisiz", "node ops/generate-auth-dev-keys.mjs --rotate-hmac");
    } else {
      add("INFO", "AUTH_OTP_HMAC_PREVIOUS_KEY", "rotasyon açık");
    }
  }

  // Ortama göre kolaylıklar ve tuzaklar.
  const profile = settings.SPRING_PROFILES_ACTIVE ?? "";
  const isProd = profile.split(",").map((part) => part.trim()).includes("prod");
  const adapter = settings.AUTH_MAIL_ADAPTER ?? "";
  if (isProd && adapter !== "" && adapter !== "mail-service") {
    add("FAIL", "AUTH_MAIL_ADAPTER", "prod profili yalnız 'mail-service' adaptörüyle açılır; Auth açılmaz", "prod'da mail-service seçin");
  }
  if (!isProd) {
    const profiles = (settings.COMPOSE_PROFILES ?? "").split(",").map((part) => part.trim());
    if (!profiles.includes("dev-mail")) {
      add("WARN", "COMPOSE_PROFILES", "dev-mail yok; doğrulama/parola kodu e-postaları Mailpit'e düşmez", "COMPOSE_PROFILES=dev-mail ekleyin");
    }
    if (!(settings.AUTH_EVENT_TARGETS ?? "").split(",").map((part) => part.trim()).includes("user")) {
      add("WARN", "AUTH_EVENT_TARGETS", "'user' yok; Auth olayları User Service'e gitmez", "node ops/generate-auth-dev-keys.mjs ile varsayılanı yazdırın");
    }
  }
  return { file, findings };
}

const results = targets.map((file) => check(file, parse(readFileSync(file, "utf8"))));

// Dosyalar arası tutarlılık: aynı anahtar .env ve .env.dev'de aynı olmalı (ops/env durumu da bunu bekler).
const parsed = targets.map((file) => parse(readFileSync(file, "utf8")));
const consistency = [];
if (parsed.length > 1) {
  for (const name of ["AUTH_OTP_HMAC_KEY", "AUTH_TOTP_ENCRYPTION_KEY", "AUTH_JWT_PRIVATE_KEY", "AUTH_OTP_HMAC_PREVIOUS_KEY",
    "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS"]) {
    const distinct = new Set(parsed.map((entry) => entry[name] ?? ""));
    if (distinct.size > 1) consistency.push(name);
  }
}

const rank = { FAIL: 0, WARN: 1, INFO: 2, OK: 3 };
let failures = 0;
for (const { file, findings } of results) {
  const shown = file.startsWith(root) ? file.slice(root.length + 1) : file;
  console.log(`== ${shown}`);
  for (const item of [...findings].sort((a, b) => rank[a.level] - rank[b.level])) {
    if (item.level === "FAIL") failures++;
    const hint = item.fix ? `  -> ${item.fix}` : "";
    console.log(`  [${item.level.padEnd(4)}] ${item.name}: ${item.problem}${hint}`);
  }
}
if (consistency.length) {
  console.log("== dosyalar arası");
  for (const name of consistency) {
    console.log(`  [WARN] ${name}: dosyalarda farklı; ops/env durumu uyuşmazlık gösterir  -> aynı değeri kullanın`);
  }
}
console.log(failures ? `\n${failures} hata: Auth bu ayarlarla AÇILMAZ.` : "\nHata yok: Auth ayarları açılış doğrulamasından geçer.");
process.exit(failures ? 1 : 0);
