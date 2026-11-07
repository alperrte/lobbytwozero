#!/usr/bin/env node
// Yerel geliştirme yığınını tek komutla kaldırır ve her role bir test hesabı açar.
//
//   node ops/dev-up.mjs                 # derle, başlat, sağlığı bekle, test hesaplarını oluştur
//   node ops/dev-up.mjs --no-seed       # hesap oluşturmadan
//   node ops/dev-up.mjs --no-build      # imajları yeniden derlemeden
//   node ops/dev-up.mjs --dry-run       # ne yapacağını yazar, hiçbir şey çalıştırmaz
//   node ops/dev-up.mjs auth-service    # yalnız verilen servisler (varsayılan: aşağıdaki liste)
//
// Önkoşul: `node ops/generate-auth-dev-keys.mjs` en az bir kez çalıştırılmış olmalı (.env.dev ve anahtarlar). Aynı komut
// .env.example'daki DEV_ACCOUNTS_* satırlarını da .env.dev'e ekler. Hesaplar ve güvenlik kapıları: ops/dev-accounts.mjs.
// Yalnız yerel geliştirme içindir; production dağıtımı bu betiği kullanmaz.

import { spawn, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const PROJECT = "ltz-dev";
const DEFAULT_SERVICES = ["postgres", "mailpit", "mail-service", "auth-service", "user-service", "api-gateway"];
/** Hesap oluşturmanın dayandığı servisler: bunlar sağlıklı olmadan kayıt akışı çalışmaz. */
const MUST_BE_HEALTHY = ["auth-service", "api-gateway", "mailpit"];

const args = process.argv.slice(2);
const flags = new Set(args.filter((arg) => arg.startsWith("--")));
const services = args.filter((arg) => !arg.startsWith("--"));
const targets = services.length > 0 ? services : DEFAULT_SERVICES;
const dryRun = flags.has("--dry-run");

for (const flag of flags) {
  if (!["--no-seed", "--no-build", "--dry-run"].includes(flag)) {
    console.error(`✗ Bilinmeyen seçenek: ${flag}`);
    process.exit(2);
  }
}

if (!existsSync(resolve(root, ".env.dev"))) {
  console.error("✗ .env.dev yok. Önce şunu çalıştır: node ops/generate-auth-dev-keys.mjs");
  process.exit(1);
}

const composeArgs = [
  "compose", "-p", PROJECT, "--env-file", ".env.dev", "--profile", "dev-mail",
  "up", "-d", ...(flags.has("--no-build") ? [] : ["--build"]), ...targets
];

function step(title) {
  console.log(`\n▶ ${title}`);
}

if (dryRun) {
  console.log(`docker ${composeArgs.join(" ")}`);
  console.log(`sağlık beklenir: ${MUST_BE_HEALTHY.filter((name) => targets.includes(name)).join(", ") || "(yok)"}`);
  console.log(flags.has("--no-seed") ? "hesap oluşturma: atlanır" : "node --env-file=.env.dev ops/dev-accounts.mjs");
  process.exit(0);
}

step("Servisler başlatılıyor");
const compose = spawnSync("docker", composeArgs, { cwd: root, stdio: "inherit" });
if (compose.status !== 0) {
  console.error("✗ docker compose başarısız oldu (yukarıdaki çıktıya bak).");
  process.exit(compose.status ?? 1);
}

function health(service) {
  const result = spawnSync(
    "docker",
    ["inspect", "-f", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}", `${PROJECT}-${service}-1`],
    { encoding: "utf8" }
  );
  return result.status === 0 ? result.stdout.trim() : "yok";
}

step("Sağlık bekleniyor");
const waitFor = MUST_BE_HEALTHY.filter((name) => targets.includes(name));
const deadline = Date.now() + 240_000;
for (const service of waitFor) {
  while (health(service) !== "healthy") {
    if (Date.now() > deadline) {
      console.error(`✗ ${service} 4 dakikada sağlıklı olmadı. İncele: docker compose -p ${PROJECT} logs ${service} --tail 50`);
      process.exit(1);
    }
    await new Promise((done) => setTimeout(done, 2000));
  }
  console.log(`  ✓ ${service}`);
}

if (flags.has("--no-seed") || !waitFor.includes("auth-service") || !waitFor.includes("api-gateway")) {
  console.log("\nHesap oluşturma atlandı.");
  process.exit(0);
}

step("Test hesapları");
const seed = spawn(process.execPath, ["--env-file=.env.dev", "ops/dev-accounts.mjs"], { cwd: root, stdio: "inherit" });
seed.on("exit", (code) => {
  if (code !== 0) {
    console.error("\n✗ Yığın ayakta ama test hesapları oluşturulamadı. DEV_ACCOUNTS_PASSWORD .env.dev'de yoksa `node ops/generate-auth-dev-keys.mjs` ekler.");
  }
  process.exit(code ?? 1);
});
