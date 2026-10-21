#!/usr/bin/env node
// Auth Service için yerel geliştirme anahtarlarını üretir ve eksik olanları .env dosyalarına yazar.
//
//   node ops/generate-auth-dev-keys.mjs            # .env ve .env.dev (varsa)
//   node ops/generate-auth-dev-keys.mjs .env.local # belirli dosya(lar)
//
// - Yalnız YOK veya BOŞ anahtarları doldurur; dolu bir anahtarın üzerine asla yazmaz.
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
};

const requested = process.argv.slice(2);
const targets = (requested.length ? requested : [".env", ".env.dev"])
  .map((file) => resolve(root, file))
  .filter((file) => existsSync(file));

if (targets.length === 0) {
  console.error("Dosya bulunamadı. Önce .env.example dosyasını .env olarak kopyalayın.");
  process.exit(1);
}

for (const file of targets) {
  let text = readFileSync(file, "utf8");
  const eol = text.includes("\r\n") ? "\r\n" : "\n";
  const filled = [];

  for (const [name, generate] of Object.entries(generators)) {
    const line = new RegExp(`^${name}=(.*?)\\r?$`, "m");
    const match = text.match(line);
    if (match && match[1].trim() !== "") continue;
    const value = generate();
    if (match) {
      text = text.replace(line, () => `${name}=${value}`);
    } else {
      if (!text.endsWith("\n")) text += eol;
      text += `${name}=${value}${eol}`;
    }
    filled.push(name);
  }

  if (filled.length) writeFileSync(file, text);
  const shown = file.startsWith(root) ? file.slice(root.length + 1) : file;
  console.log(filled.length ? `${shown}: dolduruldu -> ${filled.join(", ")}` : `${shown}: eksik anahtar yok`);
}
