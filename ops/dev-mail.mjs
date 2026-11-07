#!/usr/bin/env node
// Yerel geliştirmede GERÇEK e-posta (Gmail SMTP) açar/kapatır. Varsayılan Mailpit'tir (http://localhost:8025, gerçek mail gitmez).
// Normalde `node ops/dev.mjs` kurulum sırasında bunu zaten sorar; bu betik sonradan açıp kapatmak içindir (`node ops/dev.mjs mail on`).
//
//   node ops/dev-mail.mjs on        # YALNIZ uygulama şifresini sorar (Gmail adresi sabit); Gmail sabitlerini .env.dev'e kendisi yazar
//   node ops/dev-mail.mjs off       # SMTP ayarlarını boşaltır, e-posta yeniden Mailpit'e düşer
//   node ops/dev-mail.mjs status    # açık mı kapalı mı (adres ve şifre gösterilmez)
//   (ayrıca: --file <yol>  başka bir env dosyası için; varsayılan .env.dev)
//
// Otomatik gelenler: smtp.gmail.com, 587, STARTTLS, izinli bağlantı sunucusu (localhost) ve AUTH_MAIL_ADAPTER=mail-service.
// Sana özel olan tek şey Google "uygulama şifresi" (16 harf). ŞİFRE DEPOYA, BETİĞE VE SOHBETE YAZILMAZ: ekranda görünmeden girilir,
// yalnız kendi .env.dev dosyana yazılır. Gmail adresi ekibin ortak test hesabıdır (dev-mail-lib.mjs); başka hesap için DEV_MAIL_USER.
// Etkileşimsiz ortamda DEV_MAIL_PASSWORD ortam değişkeni kullanılır.
// Gerçek SMTP açıkken `ltz.com` gibi gerçek alan adlı test hesaplarına gönderilen e-postalar gerçek adrese gider; bkz.
// docs-auth/ekip-hizli-baslangic.md §1.0.

import { existsSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  DEFAULT_MAIL_USER,
  applyEnvChanges,
  describeSmtp,
  normalizeAppPassword,
  smtpOffSettings,
  smtpOnSettings,
  validateGmailAddress
} from "./dev-mail-lib.mjs";
import { askHidden } from "./dev-prompt.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const RESTART = "node ops/dev-accounts.mjs up --no-seed postgres mailpit mail-service auth-service";

function fail(message) {
  console.error(`✗ ${message}`);
  process.exit(1);
}

const args = process.argv.slice(2);
const fileFlag = args.indexOf("--file");
const target = resolve(root, fileFlag >= 0 ? (args[fileFlag + 1] ?? "") : ".env.dev");
const command = args.find((arg, index) => !arg.startsWith("--") && args[index - 1] !== "--file") ?? "status";

if (!existsSync(target)) {
  fail(`${target} yok. Önce şunu çalıştır: node ops/generate-auth-dev-keys.mjs`);
}

function writeAtomic(path, text) {
  const temp = `${path}.tmp`;
  writeFileSync(temp, text, { mode: 0o600 });
  renameSync(temp, path);
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
      : "Mailpit (gerçek e-posta kapalı). Açmak için: node ops/dev-mail.mjs on"
  );
} else fail(`Bilinmeyen komut: ${command} (on | off | status)`);
