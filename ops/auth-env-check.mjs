#!/usr/bin/env node
// Auth Service ENV kontrolü (yalnız okur). Normalde AYRICA çalıştırmanız gerekmez:
// node ops/generate-auth-dev-keys.mjs eksikleri tamamlar ve sonunda bu kontrolü kendisi yapar.
// Bu betik, dosyaya dokunmadan yalnız raporu görmek (ör. CI) içindir.
//
//   node ops/auth-env-check.mjs                 # .env ve .env.dev (varsa)
//   node ops/auth-env-check.mjs .env.local      # belirli dosya(lar)
//
// - Hiçbir dosyayı değiştirmez, ağa çıkmaz, süreç başlatmaz; anahtar/parola DEĞERLERİNİ ekrana yazmaz.
// - Çıkış kodu 1: Auth'un açılmayacağı bir hata var. Uyarılar (WARN) çıkış kodunu etkilemez.

import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { buildReport } from "./auth-env-rules.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const requested = process.argv.slice(2);
const targets = (requested.length ? requested : [".env", ".env.dev"])
  .map((file) => resolve(root, file))
  .filter((file) => existsSync(file));

if (targets.length === 0) {
  console.error("Dosya bulunamadı. node ops/generate-auth-dev-keys.mjs dosyayı .env.example'dan oluşturur.");
  process.exit(1);
}

const entries = targets.map((file) => ({
  shown: file.startsWith(root) ? file.slice(root.length + 1) : file,
  body: readFileSync(file, "utf8"),
}));
const report = buildReport(entries);
for (const line of report.lines) console.log(line);
process.exit(report.failures ? 1 : 0);
