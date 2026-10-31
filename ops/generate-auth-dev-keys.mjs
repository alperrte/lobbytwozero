#!/usr/bin/env node
// Auth Service için yerel geliştirme anahtarlarını üretir ve eksik olanları .env dosyalarına yazar.
//
//   node ops/generate-auth-dev-keys.mjs            # .env ve .env.dev (varsa)
//   node ops/generate-auth-dev-keys.mjs .env.local # belirli dosya(lar)
//
// - Yalnız YOK veya BOŞ anahtarları doldurur; dolu bir anahtarın üzerine asla yazmaz. Tek istisna staleDefaults:
//   değeri tam olarak ESKİ varsayılana eşit olan ayar (ör. JWT_ACCESS_TTL_SECONDS=300) yeni varsayılana çekilir.
// - Aynı anahtar tüm dosyalarda aynı olur (.env, .env.dev'in kopyasıdır; ops/env durumu bunu karşılaştırır).
//   Bir dosyada dolu değer varsa diğerlerine o kopyalanır; hiçbirinde yoksa bir kez üretilir.
// - Üretilen değerleri ekrana YAZMAZ.
// - Bu anahtarlar yerel geliştirme içindir; production için ayrı üretilir ve sır yöneticisinde tutulur.

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { generateKeyPairSync, randomBytes } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

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

const requested = process.argv.slice(2);
const targets = (requested.length ? requested : [".env", ".env.dev"])
  .map((file) => resolve(root, file))
  .filter((file) => existsSync(file));

if (targets.length === 0) {
  console.error("Dosya bulunamadı. Önce .env.example dosyasını .env.dev olarak kopyalayın.");
  process.exit(1);
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

for (const file of targets) {
  let text = readFileSync(file, "utf8");
  const eol = text.includes("\r\n") ? "\r\n" : "\n";
  const filled = [];

  for (const [name, generate] of Object.entries(generators)) {
    const match = text.match(lineOf(name));
    if (match && match[1].trim() !== "") continue;
    values[name] ??= generate();
    const value = values[name];
    if (match) {
      text = text.replace(lineOf(name), () => `${name}=${value}`);
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

  const targets = (text.match(lineOf("AUTH_EVENT_TARGETS"))?.[1] ?? "").split(",").map((name) => name.trim());
  const missingTargets = targets.includes("user") ? [] : ["AUTH_EVENT_TARGETS ('user' yok; dolu liste korunur, elle ekleyin)"];

  if (filled.length || refreshed.length || added.length) writeFileSync(file, text);
  const shown = file.startsWith(root) ? file.slice(root.length + 1) : file;
  const report = [];
  if (filled.length) report.push(`dolduruldu -> ${filled.join(", ")}`);
  if (added.length) report.push(`boş eklendi -> ${added.join(", ")}`);
  if (refreshed.length) report.push(`güncellendi -> ${refreshed.join(", ")}`);
  if (missingTargets.length) report.push(`uyarı -> ${missingTargets.join(", ")}`);
  console.log(`${shown}: ${report.length ? report.join("; ") : "eksik anahtar yok"}`);
}
