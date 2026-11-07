#!/usr/bin/env node
// LTZ yerel geliştirme: TEK KOMUT. Yeni klondan çalışır hale getirir.
//
//   node ops/dev.mjs                 # her şey: ön koşullar → anahtarlar → e-posta → servisler → hesaplar → frontend (5001)
//   node ops/dev.mjs --no-frontend   # frontend'i başlatma        (ayrıca: --no-mail, --no-build, --no-services, --skip-keys, --dry-run)
//   node ops/dev.mjs --with=game-service,library-service   # çekirdeğe ek servisler (virgülle)
//   node ops/dev.mjs --all           # compose'taki TÜM servisler (20): önce bellek denetlenir, yetmiyorsa durur (--force ile geçilir)
//   node ops/dev.mjs stop            # servisleri durdur (veri kalır)
//   node ops/dev.mjs reset           # servisleri VE tüm yerel veriyi sil (onay ister)
//   node ops/dev.mjs status          # servis ve e-posta durumu
//   node ops/dev.mjs mail on|off|status   # gerçek e-posta (Gmail) aç/kapat
//
// Adımlar (her biri tekrar çalıştırmaya güvenlidir):
//   1. Ön koşullar: Node 24.7+, Docker çalışıyor mu.
//   2. Anahtarlar: ops/generate-auth-dev-keys.mjs (.env.dev yoksa oluşturur, eksikleri tamamlar, dolu satıra dokunmaz).
//   3. E-posta: gerçek SMTP kapalıysa YALNIZ Google uygulama şifresini SORAR (görünmez; Gmail adresi sabit). Boş geçersen
//      Mailpit'te kalır (gerçek mail gitmez, kimse takılmaz). Etkileşimsiz kurulum için DEV_MAIL_PASSWORD ortam değişkeni.
//      Şifre depoda/betikte YOKTUR; yalnız kendi .env.dev dosyana yazılır.
//   4. Servisler ve hesaplar: ops/dev-accounts.mjs up (Postgres, Mailpit, Mail, Auth, User, Gateway + her role test hesabı).
//      `--with=` / `--all` ile istediğin diğer servisler de eklenir; hepsi ayağa kalkana kadar beklenir ve her biri ✓/✗ raporlanır.
//      Her Spring servisi ~550 MB tutar; Docker belleği (varsayılan RAM'in yarısı) yetmezse betik başlamadan durur.
//   5. Frontend: node_modules eksik/eskiyse `npm ci`, sonra http://localhost:5001 (port 5001 şart: Gateway CORS varsayılanı).
// Yalnız yerel geliştirme içindir; production dağıtımı bu betiği kullanmaz.

import { spawn, spawnSync } from "node:child_process";
import { existsSync, readFileSync, renameSync, statSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { assertNodeSupported, parseDevEnv } from "./dev-accounts-lib.mjs";
import { CORE_SERVICES, decideMailStep, memoryVerdict, needsInstall, parseArgs, summaryLines, validateArgs } from "./dev-lib.mjs";
import { DEFAULT_MAIL_USER, applyEnvChanges, describeSmtp, normalizeAppPassword, smtpOnSettings, validateGmailAddress } from "./dev-mail-lib.mjs";
import { ask, askHidden } from "./dev-prompt.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const ENV_DEV = resolve(root, ".env.dev");
const FRONTEND = resolve(root, "frontend");
const PROJECT = "ltz-dev";
const isWindows = process.platform === "win32";

function fail(message) {
  console.error(`✗ ${message}`);
  process.exit(1);
}

function step(index, total, title) {
  console.log(`\n▶ ${index}/${total} ${title}`);
}

const parsed = parseArgs(process.argv.slice(2));
try {
  validateArgs(parsed);
} catch (error) {
  fail(error.message);
}
const { command, rest, flags, withServices } = parsed;
const dryRun = flags.has("--dry-run");

/** Alt betiği çalıştırır; çıktı doğrudan terminale akar. Başarısızlıkta kurulum durur. */
function runNode(script, args = []) {
  const label = `node ops/${script} ${args.join(" ")}`.trim();
  if (dryRun) {
    console.log(`  (kuru çalıştırma) ${label}`);
    return;
  }
  const result = spawnSync(process.execPath, [resolve(root, "ops", script), ...args], { cwd: root, stdio: "inherit" });
  if (result.status !== 0) fail(`${label} başarısız oldu (yukarıdaki çıktıya bak).`);
}

function compose(args, options = {}) {
  return spawnSync("docker", ["compose", "-p", PROJECT, "--env-file", ".env.dev", "--profile", "dev-mail", ...args], {
    cwd: root,
    stdio: "inherit",
    ...options
  });
}

function dockerRunning() {
  return spawnSync("docker", ["info"], { stdio: "ignore" }).status === 0;
}

function writeAtomic(path, text) {
  const temp = `${path}.tmp`;
  writeFileSync(temp, text, { mode: 0o600 });
  renameSync(temp, path);
}

async function portOpen(port) {
  try {
    const response = await fetch(`http://localhost:${port}/`, { signal: AbortSignal.timeout(1500), redirect: "manual" });
    return response.status > 0;
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------- yardımcı komutlar
if (command === "mail") {
  runNode("dev-mail.mjs", rest);
  process.exit(0);
}

if (command === "stop") {
  if (!dockerRunning()) fail("Docker çalışmıyor.");
  const result = compose(["down", "--remove-orphans"]);
  process.exit(result.status ?? 1);
}

if (command === "reset") {
  if (!dockerRunning()) fail("Docker çalışmıyor.");
  console.log("Bu komut servisleri durdurur ve TÜM yerel veriyi siler (veritabanı, yüklenen medya, hesaplar). Geri alınamaz.");
  const answer = process.stdin.isTTY ? await ask("Devam etmek için 'evet' yaz: ") : "";
  if (answer.toLowerCase() !== "evet") fail("Vazgeçildi.");
  const result = compose(["down", "-v", "--remove-orphans"]);
  if (result.status === 0) console.log("✓ Sıfırlandı. Yeniden kurmak için: node ops/dev.mjs");
  process.exit(result.status ?? 1);
}

if (command === "status") {
  if (!existsSync(ENV_DEV)) fail(".env.dev yok. Kurmak için: node ops/dev.mjs");
  const state = describeSmtp(readFileSync(ENV_DEV, "utf8"));
  console.log(state.real ? `E-posta: GERÇEK (${state.host}), şifre ${state.passwordSet ? "dolu" : "BOŞ"}` : "E-posta: Mailpit (gerçek mail gitmez)");
  console.log(`Frontend (5001): ${(await portOpen(5001)) ? "çalışıyor" : "kapalı"}`);
  if (dockerRunning()) {
    const containers = spawnSync("docker", ["ps", "--format", "{{.Names}}\t{{.Status}}", "--filter", `name=${PROJECT}-`], { encoding: "utf8" });
    console.log(containers.stdout.trim() || "Servis konteyneri yok.");
  } else console.log("Docker çalışmıyor.");
  process.exit(0);
}

// ---------------------------------------------------------------- start (varsayılan)
const TOTAL = 5;

step(1, TOTAL, "Ön koşullar");
try {
  assertNodeSupported();
} catch (error) {
  fail(error.message);
}
if (!dryRun && !dockerRunning()) fail("Docker çalışmıyor. Docker Desktop'ı başlat ve tekrar dene.");
console.log(`  ✓ Node ${process.versions.node}${dryRun ? "" : ", Docker çalışıyor"}`);

step(2, TOTAL, "Anahtarlar ve .env.dev");
if (flags.has("--skip-keys")) console.log("  (atlandı: --skip-keys)");
else runNode("generate-auth-dev-keys.mjs");

step(3, TOTAL, "E-posta");
await configureMail();

step(4, TOTAL, "Servisler ve test hesapları");
const extraServices = await resolveExtraServices();
if (flags.has("--no-services")) console.log("  (atlandı: --no-services; yalnız ayarlar yapıldı)");
else {
  runNode("dev-accounts.mjs", ["up", ...(flags.has("--no-build") ? ["--no-build"] : []), ...(extraServices.length > 0 ? [...CORE_SERVICES, ...extraServices] : [])]);
  if (extraServices.length > 0 && !dryRun) await reportServices(extraServices);
}

step(5, TOTAL, "Frontend");
await startFrontend();

// ---------------------------------------------------------------- adımlar
/** Compose'taki tüm servis adları (profil dahil). */
function composeServices() {
  const result = spawnSync("docker", ["compose", "-p", PROJECT, "--env-file", existsSync(ENV_DEV) ? ".env.dev" : ".env.example", "--profile", "dev-mail", "config", "--services"], { cwd: root, encoding: "utf8" });
  return result.status === 0 ? result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean) : [];
}

/** `--with=` / `--all` ile istenen ek servisler: doğrulanır ve Docker belleği yeter mi diye BAŞLAMADAN denetlenir. */
async function resolveExtraServices() {
  if (!flags.has("--all") && withServices.length === 0) return [];
  if (dryRun) {
    console.log(`  (kuru çalıştırma) ek servisler: ${flags.has("--all") ? "tümü" : withServices.join(", ")}`);
    return [];
  }
  const available = composeServices();
  if (available.length === 0) fail("Compose servis listesi okunamadı (docker-compose.yml ve .env.dev geçerli mi?).");
  const wanted = flags.has("--all") ? available : withServices;
  const unknown = wanted.filter((name) => !available.includes(name));
  if (unknown.length > 0) fail(`Bilinmeyen servis: ${unknown.join(", ")}. Olanlar: ${available.join(", ")}`);
  const extra = [...new Set(wanted)].filter((name) => !CORE_SERVICES.includes(name));
  const info = spawnSync("docker", ["info", "--format", "{{.MemTotal}}"], { encoding: "utf8" });
  const dockerMemBytes = Number.parseInt(info.stdout.trim(), 10);
  if (Number.isFinite(dockerMemBytes)) {
    const verdict = memoryVerdict({ services: [...CORE_SERVICES, ...extra], dockerMemBytes });
    const need = (verdict.needMb / 1024).toFixed(1);
    const have = (verdict.haveMb / 1024).toFixed(1);
    if (!verdict.ok && !flags.has("--force")) {
      fail(`${extra.length + CORE_SERVICES.length} servis yaklaşık ${need} GB ister, Docker'a ${have} GB ayrılmış. Servisler bellek yetmediği için ölür. ` +
        "Çözüm (biri yeter): daha az servis seç (--with=a,b), ya da Docker Desktop > Settings > Resources bölümünden belleği artır (ya da ~/.wslconfig içine memory=12GB yaz ve `wsl --shutdown`). Yine de denemek için --force.");
    }
    console.log(`  Bellek: ~${need} GB gerekir, Docker'da ${have} GB var ✓`);
  }
  return extra;
}

function serviceState(name) {
  const inspect = spawnSync("docker", ["inspect", "-f", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}|{{.State.ExitCode}}|{{.State.OOMKilled}}", `${PROJECT}-${name}-1`], { encoding: "utf8" });
  if (inspect.status !== 0) return { status: "yok", exit: 0, oom: false, started: false };
  const [status = "", exit = "0", oom = "false"] = inspect.stdout.trim().split("|");
  const logs = spawnSync("docker", ["logs", "--tail", "200", `${PROJECT}-${name}-1`], { encoding: "utf8" });
  return { status, exit: Number(exit), oom: oom === "true", started: /Started \w+Application/.test(`${logs.stdout ?? ""}${logs.stderr ?? ""}`) };
}

/** Ek servislerin hazır olmasını bekler (en çok 4 dk) ve her birini raporlar; hata kurulumu durdurmaz, çekirdek çalışmaya devam eder. */
async function reportServices(names) {
  console.log(`  Ek servisler bekleniyor (${names.length}) …`);
  const deadline = Date.now() + 240_000;
  const pending = new Set(names);
  const results = new Map();
  while (pending.size > 0 && Date.now() < deadline) {
    for (const name of [...pending]) {
      const state = serviceState(name);
      if (state.status === "healthy" || (state.status === "running" && state.started)) {
        results.set(name, "ok");
        pending.delete(name);
      } else if (state.status === "exited" || state.status === "dead" || state.status === "yok") {
        results.set(name, state.oom ? "bellek yetmedi (OOM)" : `kapandı (çıkış kodu ${state.exit})`);
        pending.delete(name);
      }
    }
    if (pending.size > 0) await new Promise((resolveWait) => setTimeout(resolveWait, 4000));
  }
  for (const name of pending) results.set(name, "4 dakikada hazır olmadı");
  let broken = 0;
  for (const name of names) {
    const result = results.get(name);
    if (result === "ok") console.log(`  ✓ ${name}`);
    else {
      broken += 1;
      console.log(`  ✗ ${name}: ${result}. İncele: docker compose -p ${PROJECT} logs ${name} --tail 50`);
    }
  }
  if (broken > 0) console.log(`  ! ${broken} servis ayağa kalkmadı; çekirdek (giriş, kayıt, profil) çalışmaya devam eder.`);
}

async function configureMail() {
  if (dryRun || !existsSync(ENV_DEV)) {
    console.log(dryRun ? "  (kuru çalıştırma) uygulama şifresi sorulur; boş geçilirse Mailpit" : "  (.env.dev henüz yok; adım atlandı)");
    return;
  }
  const state = describeSmtp(readFileSync(ENV_DEV, "utf8"));
  const decision = decideMailStep({
    skip: flags.has("--no-mail"),
    real: state.real,
    passwordSet: state.passwordSet,
    interactive: Boolean(process.stdin.isTTY),
    envPassword: process.env.DEV_MAIL_PASSWORD
  });

  if (decision === "skip") return console.log("  (atlandı: --no-mail)");
  if (decision === "already") return console.log(`  ✓ Gerçek e-posta açık (${state.host})`);
  if (decision === "mailpit") return console.log("  Mailpit (gerçek mail gitmez). Gerçek için: node ops/dev.mjs mail on");

  const user = process.env.DEV_MAIL_USER ?? DEFAULT_MAIL_USER;
  let password = process.env.DEV_MAIL_PASSWORD;
  if (decision === "ask") {
    console.log(`  Gerçek e-posta için Google uygulama şifresi gerekir (hesap: ${user}). Boş bırakırsan Mailpit kullanılır.`);
    password = await askHidden("  Uygulama şifresi (yazarken görünmez; boş = Mailpit): ");
    if (password.trim() === "") return console.log("  ✓ Mailpit (şifre girilmedi). Sonra açmak için: node ops/dev.mjs mail on");
  }
  try {
    const settings = smtpOnSettings(validateGmailAddress(user), normalizeAppPassword(password));
    writeAtomic(ENV_DEV, applyEnvChanges(readFileSync(ENV_DEV, "utf8"), settings));
    console.log("  ✓ Gerçek e-posta (Gmail SMTP) .env.dev dosyasına yazıldı. Şifre ekrana basılmadı.");
  } catch (error) {
    console.log(`  ! ${error.message} Mailpit ile devam ediliyor. Sonra: node ops/dev.mjs mail on`);
  }
}

async function startFrontend() {
  const env = existsSync(ENV_DEV) ? parseDevEnv(readFileSync(ENV_DEV, "utf8")) : {};
  const domain = process.env.DEV_ACCOUNTS_DOMAIN ?? env.DEV_ACCOUNTS_DOMAIN ?? "ltz.com";
  const realMail = existsSync(ENV_DEV) && describeSmtp(readFileSync(ENV_DEV, "utf8")).real;

  if (flags.has("--no-frontend")) {
    console.log("  (atlandı: --no-frontend)");
    return printSummary({ domain, realMail, frontendRunning: false });
  }
  const running = dryRun ? false : await portOpen(5001);
  if (running) {
    console.log("  ✓ Frontend zaten çalışıyor: http://localhost:5001");
    return printSummary({ domain, realMail, frontendRunning: true });
  }

  const modules = resolve(FRONTEND, "node_modules");
  const lock = resolve(FRONTEND, "package-lock.json");
  const install = needsInstall({
    modulesTime: existsSync(modules) ? statSync(resolve(modules, ".package-lock.json"), { throwIfNoEntry: false })?.mtimeMs ?? statSync(modules).mtimeMs : null,
    lockTime: existsSync(lock) ? statSync(lock).mtimeMs : null
  });
  if (dryRun) {
    console.log(`  (kuru çalıştırma) ${install ? "npm ci; " : ""}npx next dev -H localhost -p 5001`);
    return printSummary({ domain, realMail, frontendRunning: true });
  }
  if (install) {
    console.log("  Bağımlılıklar kuruluyor (npm ci) …");
    const result = spawnSync("npm", ["ci", "--no-audit", "--no-fund"], { cwd: FRONTEND, stdio: "inherit", shell: isWindows });
    if (result.status !== 0) fail("npm ci başarısız oldu.");
  }
  printSummary({ domain, realMail, frontendRunning: true });
  console.log("\n  Frontend başlıyor (durdurmak için Ctrl+C). Servisler arka planda çalışmaya devam eder.\n");
  const server = spawn("npx", ["next", "dev", "-H", "localhost", "-p", "5001"], { cwd: FRONTEND, stdio: "inherit", shell: isWindows });
  server.on("exit", (code) => process.exit(code ?? 0));
}

function printSummary(input) {
  console.log("");
  for (const line of summaryLines(input)) console.log(line);
}
