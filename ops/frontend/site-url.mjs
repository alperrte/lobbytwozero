#!/usr/bin/env node
// NEXT_PUBLIC_SITE_URL üretici/doğrulayıcı.
//
// Verilen (ya da sorulan) site adresini doğrular, normalleştirir ve eklenecek ENV satırını EKRANA yazar.
// Hiçbir dosyayı okumaz, oluşturmaz veya değiştirmez; gerçek .env / .env.local dosyalarına dokunmaz.
// Satırı hangi dosyaya ekleyeceğine sen karar verirsin.
import { createInterface } from "node:readline/promises";
import { fileURLToPath } from "node:url";

export const ENV_KEY = "NEXT_PUBLIC_SITE_URL";
const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

export const HELP = `Kullanım:
  node ops/frontend/site-url.mjs <adres> [--production]
  node ops/frontend/site-url.mjs            (adres sorulur)

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
export function validateSiteUrl(raw, options = {}) {
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
export function formatEnvLine(url) {
  return `${ENV_KEY}=${url}`;
}

/**
 * @param {string[]} argv
 * @returns {{ help: boolean, production: boolean, value: string | undefined, error?: string }}
 */
export function parseArgs(argv) {
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

async function main() {
  const args = parseArgs(process.argv.slice(2));
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

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.exitCode = await main();
}
