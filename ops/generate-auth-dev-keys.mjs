#!/usr/bin/env node
// Auth Service için yerel geliştirme ENV dosyalarını TEK KOMUTLA tamamlar:
//
//   node ops/generate-auth-dev-keys.mjs            # .env ve .env.dev (hiçbiri yoksa .env.example'dan .env.dev oluşturur)
//   node ops/generate-auth-dev-keys.mjs .env.local # belirli dosya(lar)
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

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { generateKeyPairSync, randomBytes } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { buildReport } from "./auth-env-rules.mjs";

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

const flags = new Set(process.argv.slice(2).filter((arg) => arg.startsWith("--")));
const requested = process.argv.slice(2).filter((arg) => !arg.startsWith("--"));
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
      console.error("AUTH_TOTP_ENCRYPTION_KEY boş: önce node ops/generate-auth-dev-keys.mjs ile üretin.");
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
      console.error("AUTH_OTP_HMAC_KEY boş: önce node ops/generate-auth-dev-keys.mjs ile üretin.");
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
