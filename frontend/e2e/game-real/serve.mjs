#!/usr/bin/env node
// ÇALIŞAN bir arayüzü (ör. `next dev` localhost:3000) denemek için izole, geçici bir Auth/Game/Community/Gateway yığını ayakta tutar.
// DEV/prod veritabanına, ENV dosyalarına ve mevcut konteynerlere dokunmaz; yalnız kendi etiketli kaynaklarını oluşturur ve siler.
// Kullanım (frontend/ dizininden):
//   GAME_REAL_GATEWAY_PORT=5080 GAME_REAL_FRONTEND_ORIGIN=http://localhost:3000 GAME_LIVE_DIR=<dizin> node e2e/game-real/serve.mjs
// Durdurma: <GAME_LIVE_DIR>/stop dosyasını oluşturun (süreç kaynakları temizleyip çıkar). Durum: <GAME_LIVE_DIR>/state.json.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { startStack } from "./stack.mjs";

const dir = process.env.GAME_LIVE_DIR ?? path.join(os.tmpdir(), "ltz-game-live");
fs.mkdirSync(dir, { recursive: true });
fs.rmSync(path.join(dir, "stop"), { force: true });
process.env.GAME_REAL_GATEWAY_PORT ??= "5080";
process.env.GAME_REAL_FRONTEND_ORIGIN ??= "http://localhost:3000";
const stack = await startStack();
let code = 0;
try {
  const users = {};
  for (const [name, roles] of [["admin", ["ADMIN"]], ["moderator", ["MODERATOR"]], ["developer", ["GAME_DEVELOPER"]], ["player", []], ["other", []]]) {
    const user = await stack.register(name);
    if (roles.length) { user.totpSecret = await stack.enrollTotp(user); user.totpStep = Math.floor(Date.now() / 30000); await stack.grantRoles(user.email, roles); }
    users[name] = user;
  }
  for (let index = 1; index <= 24; index++) {
    const title = `Seed Oyun ${String(index).padStart(2, "0")}`;
    await stack.sql(stack.gameDb, `INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES (gen_random_uuid(),'${title}','${title.toLowerCase()}','INTERNAL','MAIN_GAME','PARTIAL',now(),now())`);
  }
  // Steam indeksi (GAME-16/21): Steam referanslı, türü belirsiz (UNKNOWN) ve yalnız indeks kapsamlı kayıtlar. Herkese açık katalog (MAIN_GAME) bunları listelemez; /catalog uçları includeUnclassified ile döndürür.
  for (const [index, title] of ["Dead Index Alpha", "Dead Index Beta", "Dead Index Gamma"].entries()) {
    await stack.sql(stack.gameDb, `WITH g AS (INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES (gen_random_uuid(),'${title}','${title.toLowerCase()}','EXTERNAL','UNKNOWN','INDEX_ONLY',now(),now()) RETURNING id) INSERT INTO game_external_refs(id,game_id,provider,external_id,last_synced_at) SELECT gen_random_uuid(), id, 'STEAM', '${900001 + index}', now() FROM g`);
  }
  const externalGameId = (await stack.sql(stack.gameDb, "INSERT INTO games(id,title,normalized_title,origin,game_type,metadata_completeness,created_at,updated_at) VALUES (gen_random_uuid(),'Dış Kaynaklı Oyun','dış kaynaklı oyun','EXTERNAL','MAIN_GAME','PARTIAL',now(),now()) RETURNING id")).split(/\s+/)[0].trim();
  fs.writeFileSync(path.join(dir, "state.json"), JSON.stringify({ gateway: stack.gatewayOrigin, frontend: stack.frontendOrigin, users, seeded: 24, externalGameId }), { mode: 0o600 });
  console.log(`[serve] hazır: Gateway ${stack.gatewayOrigin}, arayüz ${stack.frontendOrigin}; durum ${path.join(dir, "state.json")}`);
  while (!fs.existsSync(path.join(dir, "stop"))) await delay(2000);
  console.log("[serve] durdurma isteği alındı");
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  code = 1;
} finally {
  await stack.stop().catch((error) => { console.error("Temizlik hatası:", error.message); code ||= 2; });
}
process.exit(code);
