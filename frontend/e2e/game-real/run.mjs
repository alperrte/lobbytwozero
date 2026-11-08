#!/usr/bin/env node
// Gerçek Auth/Gateway/Game/Community yığınına karşı Game arayüzü tarayıcı testlerini çalıştırır (fixture DEĞİL).
// Kullanım: node e2e/game-real/run.mjs [playwright argümanları]   (frontend/ dizininden)
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { startStack } from "./stack.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const frontend = path.resolve(here, "../..");
process.env.GAME_REAL_GATEWAY_PORT ??= "5180";
const stack = await startStack();
let exitCode = 1;
try {
  const users = {};
  for (const [name, roles] of [["admin", ["ADMIN"]], ["moderator", ["MODERATOR"]], ["developer", ["GAME_DEVELOPER"]], ["player", []], ["other", []]]) {
    const user = await stack.register(name);
    if (roles.length) { user.totpSecret = await stack.enrollTotp(user); user.totpStep = Math.floor(Date.now() / 30000); await stack.grantRoles(user.email, roles); }
    users[name] = user;
  }
  // Sayfalama ve sıralama için sentetik filler oyunlar (yalnız bu geçici Game veritabanı).
  for (let index = 1; index <= 24; index++) {
    const title = `Seed Oyun ${String(index).padStart(2, "0")}`;
    await stack.sql(stack.gameDb, `INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES (gen_random_uuid(),'${title}','${title.toLowerCase()}','INTERNAL','MAIN_GAME','PARTIAL',now(),now())`);
  }
  // Manuel alan kilidi yalnız EXTERNAL kökenli oyunlarda oluşur (Game kuralı): geçici DB'ye harici kökenli bir kayıt eklenir.
  // Steam indeksi (GAME-16/21): Steam referanslı, türü belirsiz (UNKNOWN) ve yalnız indeks kapsamlı kayıtlar. Herkese açık katalog (MAIN_GAME) bunları listelemez; /catalog uçları includeUnclassified ile döndürür.
  for (const [index, title] of ["Dead Index Alpha", "Dead Index Beta", "Dead Index Gamma"].entries()) {
    await stack.sql(stack.gameDb, `WITH g AS (INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES (gen_random_uuid(),'${title}','${title.toLowerCase()}','EXTERNAL','UNKNOWN','INDEX_ONLY',now(),now()) RETURNING id) INSERT INTO game_external_refs(id,game_id,provider,external_id,last_synced_at) SELECT gen_random_uuid(), id, 'STEAM', '${900001 + index}', now() FROM g`);
  }
  const externalGameId = (await stack.sql(stack.gameDb, "INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES (gen_random_uuid(),'Dış Kaynaklı Oyun','dış kaynaklı oyun','EXTERNAL','MAIN_GAME','PARTIAL',now(),now()) RETURNING id")).split(/\s+/)[0].trim();
  const state = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "ltz-game-real-")), "state.json");
  fs.writeFileSync(state, JSON.stringify({ gateway: stack.gatewayOrigin, frontend: stack.frontendOrigin, users, seeded: 24, externalGameId }), { mode: 0o600 });
  console.log("[run] yığın hazır; Playwright başlıyor");
  const env = { ...process.env, GAME_REAL_STATE: state, NEXT_PUBLIC_API_BASE_URL: stack.gatewayOrigin };
  // Kabuk kullanılmaz: argümanlar (ör. -g "a|b") olduğu gibi aktarılır.
  const nextCli = path.join(frontend, "node_modules/next/dist/bin/next");
  const playwrightCli = path.join(frontend, "node_modules/@playwright/test/cli.js");
  // NEXT_PUBLIC_* derleme anında gömülür. Gateway portu sabit (varsayılan 5180) olduğundan, kod değişmediyse
  // GAME_REAL_SKIP_BUILD=1 ile önceki derleme yeniden kullanılabilir.
  if (process.env.GAME_REAL_SKIP_BUILD === "1") {
    console.log("[run] GAME_REAL_SKIP_BUILD=1: önceki üretim derlemesi kullanılır");
  } else {
    console.log("[run] üretim derlemesi (NEXT_PUBLIC_API_BASE_URL = geçici Gateway)");
    const build = spawn(process.execPath, [nextCli, "build"], { cwd: frontend, env, stdio: "inherit" });
    const built = await new Promise((resolve) => build.once("exit", (code) => resolve(code ?? 1)));
    if (built !== 0) throw new Error("next build başarısız");
  }
  const child = spawn(process.execPath, [playwrightCli, "test", "-c", "e2e/game-real/playwright.config.ts", ...process.argv.slice(2)], { cwd: frontend, env, stdio: "inherit" });
  exitCode = await new Promise((resolve) => child.once("exit", (code) => resolve(code ?? 1)));
  fs.rmSync(path.dirname(state), { recursive: true, force: true });
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
} finally {
  await stack.stop().catch((error) => { console.error("Temizlik hatası:", error.message); exitCode ||= 2; });
}
process.exit(exitCode);
