#!/usr/bin/env node
// Yerel geliştirme yığınını kaldırır ve her role bir test hesabı oluşturur/günceller (yalnız ltz-dev yığını, yalnız yerel adresler).
//
//   node ops/dev-accounts.mjs up                 # derle + başlat + sağlığı bekle + hesapları oluştur (ekibin tek komutu)
//   node ops/dev-accounts.mjs up --no-seed       # yalnız yığını kaldır      (ayrıca: --no-build, --dry-run, servis adları)
//   node ops/dev-accounts.mjs                    # (seed) yığın zaten ayaktaysa hesapları oluştur/güncelle ve girişi doğrula
//   node ops/dev-accounts.mjs list               # hesapları ve rollerini göster
//   node ops/dev-accounts.mjs totp admin         # admin/moderator için anlık 2FA kodu
//
// Parola ve alan adı: ortamdan (DEV_ACCOUNTS_PASSWORD / DEV_ACCOUNTS_DOMAIN) ya da .env.dev'den. .env.dev'den YALNIZ bu iki
// anahtar okunur (ops/dev-accounts-lib.mjs parseDevEnv); satırlar yoksa `node ops/generate-auth-dev-keys.mjs` ekler.
//
// Hesaplar: user (PLAYER), creator (CONTENT_CREATOR), developer (GAME_DEVELOPER), moderator (MODERATOR), admin (ADMIN);
// e-posta `<ad>@${DEV_ACCOUNTS_DOMAIN:-ltz.com}`.
//
// Neden doğrudan veritabanı? Auth parola alt sınırı 15 karakterdir; kısa bir geliştirme parolası API'den geçmez. Betik hesabı
// GERÇEK akışla açar (kayıt → Mailpit kodu → doğrulama → giriş; olay ve rol kayıtları Auth'un kendi kodundan çıkar), sonra
// yalnız parola özetini yazar ve gerekli rolü ekler. ADMIN/MODERATOR için Auth TOTP zorunlu kıldığından, rol verilmeden ÖNCE
// TOTP API ile kurulur; sır ve kurtarma kodları makine düzeyindeki `~/.ltz/dev-accounts.json` dosyasına yazılır (depoya girmez;
// aynı bilgisayardaki tüm klasörler aynı veritabanını paylaştığı için aynı dosyayı kullanır).
//
// Güvenlik: yalnız localhost'a ve `ltz-dev-*` Postgres kapsayıcısına çalışır, NODE_ENV=production iken reddeder. Parola
// depoya yazılmaz (DEV_ACCOUNTS_PASSWORD zorunlu, varsayılanı yok) ve ekrana basılmaz. Tekrar çalıştırmak güvenlidir.

import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  DEV_ACCOUNTS,
  assertDevProfile,
  assertDevTarget,
  emailFor,
  extractCode,
  hashPassword,
  parseDevEnv,
  secondsLeftInStep,
  sqlLiteral,
  temporaryPassword,
  totp,
  validateConfig
} from "./dev-accounts-lib.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
/**
 * 2FA sırları MAKİNE düzeyinde tutulur: aynı bilgisayardaki tüm klasörler (ör. farklı git worktree'leri) aynı Docker
 * veritabanını (`ltz-dev`) paylaşır, dolayısıyla aynı sırrı görmelidir. Depoya girmez (home dizini). `DEV_ACCOUNTS_STATE` ile
 * değiştirilebilir. Eski klasör içi dosya (`.dev-accounts.local.json`) bulunursa içeriği buraya taşınır.
 */
const STATE_FILE = process.env.DEV_ACCOUNTS_STATE ?? resolve(homedir(), ".ltz", "dev-accounts.json");
const LEGACY_STATE_FILE = resolve(root, ".dev-accounts.local.json");

/** Ortam değişkeni önceliklidir; yoksa .env.dev'deki DEV_ACCOUNTS_* satırları kullanılır (başka hiçbir satır okunmaz). */
function devEnv() {
  const file = resolve(root, ".env.dev");
  try {
    return existsSync(file) ? parseDevEnv(readFileSync(file, "utf8")) : {};
  } catch {
    return {};
  }
}
const fromFile = devEnv();

const config = {
  password: process.env.DEV_ACCOUNTS_PASSWORD ?? fromFile.DEV_ACCOUNTS_PASSWORD,
  domain: process.env.DEV_ACCOUNTS_DOMAIN ?? fromFile.DEV_ACCOUNTS_DOMAIN ?? "ltz.com",
  gatewayUrl: process.env.DEV_GATEWAY_URL ?? "http://localhost:5080",
  mailpitUrl: process.env.DEV_MAILPIT_URL ?? "http://localhost:8025",
  origin: process.env.DEV_FRONTEND_ORIGIN ?? "http://localhost:5001",
  container: process.env.DEV_POSTGRES_CONTAINER ?? "ltz-dev-postgres-1",
  authContainer: process.env.DEV_AUTH_CONTAINER ?? "ltz-dev-auth-service-1",
  nodeEnv: process.env.NODE_ENV
};

function fail(message) {
  console.error(`✗ ${message}`);
  process.exit(1);
}

// ---------- yerel durum (TOTP sırları) ----------

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    fail(`${file} okunamadı (bozuk JSON). Silip yeniden çalıştırabilirsin; betik eksik 2FA sırlarını yeniden kurar.`);
  }
}

function readState() {
  const machine = existsSync(STATE_FILE) ? readJson(STATE_FILE) : {};
  if (!existsSync(LEGACY_STATE_FILE)) return machine;
  // Eski klasör içi dosyadaki kayıtlardan makine dosyasında olmayanlar taşınır (makine dosyası önceliklidir).
  const merged = { ...readJson(LEGACY_STATE_FILE), ...machine };
  if (Object.keys(merged).length !== Object.keys(machine).length) {
    writeState(merged);
    console.log(`(2FA sırları ${STATE_FILE} dosyasına taşındı)`);
  }
  return merged;
}

function writeState(state) {
  mkdirSync(dirname(STATE_FILE), { recursive: true, mode: 0o700 });
  writeFileSync(STATE_FILE, `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
  try {
    chmodSync(STATE_FILE, 0o600);
  } catch {
    // Windows'ta izin biti anlamlı değil.
  }
}

// ---------- veritabanı ----------

function psql(sql) {
  const result = spawnSync(
    "docker",
    ["exec", "-i", config.container, "sh", "-c", 'psql -U "$POSTGRES_USER" -d auth_db -v ON_ERROR_STOP=1 -tA'],
    { input: sql, encoding: "utf8" }
  );
  if (result.status !== 0) {
    fail(`Veritabanı komutu başarısız (${config.container}): ${(result.stderr || "").trim().split("\n")[0]}`);
  }
  return result.stdout.trim();
}

// ---------- HTTP (çerez kavanozu + CSRF) ----------

class Session {
  jar = new Map();

  async request(method, path, body) {
    const headers = { Accept: "application/json", Origin: config.origin };
    if (this.jar.size > 0) headers.Cookie = [...this.jar].map(([k, v]) => `${k}=${v}`).join("; ");
    const xsrf = this.jar.get("XSRF-TOKEN") ?? this.jar.get("__Host-XSRF-TOKEN");
    if (xsrf && method !== "GET") headers["X-XSRF-TOKEN"] = xsrf;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(`${config.gatewayUrl}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: "manual"
    });
    for (const line of response.headers.getSetCookie()) {
      const [pair] = line.split(";");
      const index = pair.indexOf("=");
      const name = pair.slice(0, index).trim();
      const value = pair.slice(index + 1).trim();
      if (value === "" || /max-age=0/i.test(line)) this.jar.delete(name);
      else this.jar.set(name, value);
    }
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    return { status: response.status, json };
  }

  async csrf() {
    await this.request("GET", "/api/v1/auth/csrf");
  }
}

function describe(response) {
  return `HTTP ${response.status}${response.json?.code ? ` ${response.json.code}` : ""}`;
}

// ---------- Mailpit ----------

async function mailIds(email) {
  const response = await fetch(`${config.mailpitUrl}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
  if (!response.ok) fail(`Mailpit'e ulaşılamadı (${config.mailpitUrl}): HTTP ${response.status}`);
  const body = await response.json();
  return (body.messages ?? []).map((message) => message.ID);
}

async function waitForCode(email, knownIds) {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const fresh = (await mailIds(email)).filter((id) => !knownIds.has(id));
    for (const id of fresh) {
      const response = await fetch(`${config.mailpitUrl}/api/v1/message/${id}`);
      const message = await response.json();
      const code = extractCode(message.Text);
      if (code) return code;
    }
    await new Promise((done) => setTimeout(done, 1000));
  }
  fail(`${email} için doğrulama e-postası 30 sn içinde gelmedi (Mail Service/Mailpit çalışıyor mu?).`);
}

// ---------- akışlar ----------

async function waitForFreshTotpStep() {
  if (secondsLeftInStep() < 4) await new Promise((done) => setTimeout(done, 5000));
}

async function createViaApi(account, email, temp, state) {
  const session = new Session();
  await session.csrf();
  const known = new Set(await mailIds(email));

  let response = await session.request("POST", "/api/v1/auth/register", { email, username: account.local, password: temp });
  if (response.status !== 202) fail(`${email} kayıt başarısız: ${describe(response)}`);
  const code = await waitForCode(email, known);

  response = await session.request("POST", "/api/v1/auth/email-verification/confirm", { email, code, password: temp });
  if (response.status !== 204) fail(`${email} e-posta doğrulaması başarısız: ${describe(response)}`);

  response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: temp });
  if (response.json?.status !== "AUTHENTICATED") fail(`${email} ilk giriş başarısız: ${describe(response)}`);

  if (account.mfa) await enrollTotp(session, email, state);
}

/** Giriş yapmış oturumda TOTP kurar ve etkinleştirir; sır ve kurtarma kodları makine dosyasına yazılır. */
async function enrollTotp(session, email, state) {
  let response = await session.request("POST", "/api/v1/auth/2fa/totp/setup", {});
  const secret = response.json?.secret;
  if (response.status !== 200 || !secret) fail(`${email} TOTP kurulumu başarısız: ${describe(response)}`);
  await waitForFreshTotpStep();
  response = await session.request("POST", "/api/v1/auth/2fa/totp/activate", { code: totp(secret) });
  if (response.status !== 200) fail(`${email} TOTP etkinleştirme başarısız: ${describe(response)}`);
  state[email] = { totpSecret: secret, recoveryCodes: response.json?.recoveryCodes ?? [] };
  writeState(state);
}

/**
 * Hesabın TOTP'si var ama sırrı bu makinede yok (veritabanı başka makineden gelmiş, dosya silinmiş): yalnız DEV hesabı için
 * 2FA sıfırlanıp yeniden kurulur. Auth 2FA'sız ADMIN/MODERATOR'a oturum vermediğinden, kurulum süresince rol geçici alınır ve
 * `finalizeInDatabase` ile geri verilir.
 */
async function repairTotp(account, email, state) {
  const e = sqlLiteral(email.toLowerCase());
  const owner = `(select id from accounts where email_normalized = ${e})`;
  process.stdout.write("2FA sırrı yok, yeniden kuruluyor … ");
  psql(`
    begin;
    update accounts
       set password_hash = ${sqlLiteral(hashPassword(config.password))}, status = 'ACTIVE',
           email_verified_at = coalesce(email_verified_at, now()), token_version = token_version + 1,
           updated_at = now(), version = version + 1
     where email_normalized = ${e};
    insert into account_role_history (id, account_id, role_code, action, reason, actor_id, created_at)
      select gen_random_uuid(), a.id, ${sqlLiteral(account.role)}, 'REVOKE', 'dev-accounts 2fa repair', a.id, now()
        from accounts a join account_roles ar on ar.account_id = a.id join roles r on r.id = ar.role_id
       where a.email_normalized = ${e} and r.code = ${sqlLiteral(account.role)};
    delete from account_roles where account_id = ${owner} and role_id = (select id from roles where code = ${sqlLiteral(account.role)});
    delete from recovery_codes where account_id = ${owner};
    delete from totp_credentials where account_id = ${owner};
    commit;
  `);
  const session = new Session();
  await session.csrf();
  const response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
  if (response.json?.status !== "AUTHENTICATED") fail(`${email} 2FA onarımı sırasında giriş başarısız: ${describe(response)}`);
  await enrollTotp(session, email, state);
}

function finalizeInDatabase(account, email, passwordHash) {
  const e = sqlLiteral(email.toLowerCase());
  const roleGrant =
    account.role === "PLAYER"
      ? ""
      : `
    with acc as (select id from accounts where email_normalized = ${e}),
         role as (select id, code from roles where code = ${sqlLiteral(account.role)}),
         ins as (
           insert into account_roles (account_id, role_id, granted_by)
           select acc.id, role.id, acc.id from acc, role
           on conflict do nothing
           returning account_id
         )
    insert into account_role_history (id, account_id, role_code, action, reason, actor_id, created_at)
    select gen_random_uuid(), ins.account_id, ${sqlLiteral(account.role)}, 'GRANT', 'dev-accounts seed', ins.account_id, now() from ins;`;
  psql(`
    begin;
    update accounts
       set password_hash = ${sqlLiteral(passwordHash)},
           status = 'ACTIVE',
           email_verified_at = coalesce(email_verified_at, now()),
           token_version = token_version + 1,
           updated_at = now(),
           version = version + 1
     where email_normalized = ${e};${roleGrant}
    commit;
  `);
}

/**
 * Auth, aynı 30 sn dilimindeki TOTP kodunu ikinci kez kabul etmez (tekrar kullanım koruması). Önceki bir çalıştırma ya da TOTP
 * etkinleştirmesi bu dilimi tüketmiş olabilir; bu yüzden 2FA hatasında bir sonraki dilimi bekleyip bir kez yeniden denenir.
 */
async function verifyLogin(account, email, state) {
  const first = await verifyLoginOnce(account, email, state);
  if (first === null || !account.mfa || !first.includes("401")) return first;
  await new Promise((done) => setTimeout(done, (secondsLeftInStep() + 1) * 1000));
  return verifyLoginOnce(account, email, state);
}

async function verifyLoginOnce(account, email, state) {
  const session = new Session();
  await session.csrf();
  let response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
  if (account.mfa) {
    if (response.json?.status !== "MFA_REQUIRED") return `beklenen MFA_REQUIRED, gelen ${describe(response)}`;
    const secret = state[email]?.totpSecret;
    if (!secret) return "TOTP sırrı yerel dosyada yok (hesap önceden TOTP ile açılmış olabilir)";
    await waitForFreshTotpStep();
    response = await session.request("POST", "/api/v1/auth/login/2fa", {
      challengeId: response.json.challengeId,
      code: totp(secret)
    });
  }
  if (response.json?.status !== "AUTHENTICATED") return `giriş başarısız: ${describe(response)}`;
  const me = await session.request("GET", "/api/v1/auth/me");
  const roles = me.json?.roles ?? [];
  if (!roles.includes(account.role)) return `rol eksik: beklenen ${account.role}, gelen [${roles.join(",")}]`;
  return null;
}

async function seed() {
  try {
    validateConfig(config);
  } catch (error) {
    fail(error.message);
  }
  const state = readState();
  const results = [];
  for (const account of DEV_ACCOUNTS) {
    const email = emailFor(account, config.domain);
    process.stdout.write(`• ${email} (${account.role}) … `);
    const existing = psql(`select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())};`);
    if (!existing) await createViaApi(account, email, temporaryPassword(), state);
    else if (account.mfa && !state[email]?.totpSecret) await repairTotp(account, email, state);
    finalizeInDatabase(account, email, hashPassword(config.password));
    const problem = await verifyLogin(account, email, state);
    console.log(problem ? `✗ ${problem}` : "✓ giriş doğrulandı");
    results.push({ email, role: account.role, mfa: account.mfa, ok: !problem });
  }
  console.log("\nHesaplar (parola: DEV_ACCOUNTS_PASSWORD):");
  for (const r of results) console.log(`  ${r.ok ? "✓" : "✗"} ${r.email.padEnd(22)} ${r.role}${r.mfa ? "  (2FA: node ops/dev-accounts.mjs totp " + r.email.split("@")[0] + ")" : ""}`);
  if (results.some((r) => !r.ok)) process.exit(1);
}

function list() {
  const rows = psql(`
    select a.email || ' | ' || a.status || ' | ' || coalesce((select string_agg(r.code, ',' order by r.code)
      from account_roles ar join roles r on r.id = ar.role_id where ar.account_id = a.id), '-')
    from accounts a where a.email_normalized like ${sqlLiteral(`%@${config.domain}`)} order by a.created_at;`);
  console.log(rows || "(hesap yok)");
}

function showTotp(name) {
  const account = DEV_ACCOUNTS.find((item) => item.local === name);
  if (!account?.mfa) fail(`2FA yalnız şu hesaplarda var: ${DEV_ACCOUNTS.filter((a) => a.mfa).map((a) => a.local).join(", ")}`);
  const entry = readState()[emailFor(account, config.domain)];
  if (!entry?.totpSecret) fail("Bu hesap için bu makinede TOTP sırrı yok. `node ops/dev-accounts.mjs` çalıştır: sırrı olmayan 2FA hesabını yeniden kurar.");
  console.log(`${totp(entry.totpSecret)}  (${secondsLeftInStep()} sn geçerli)`);
}

/** Auth konteynerinin gerçek profilini okur (yalnız bu tek değişken; başka ortam değeri okunmaz/basılmaz). */
function readAuthProfile() {
  const result = spawnSync("docker", ["exec", config.authContainer, "printenv", "SPRING_PROFILES_ACTIVE"], { encoding: "utf8" });
  return result.status === 0 ? result.stdout.trim() : "";
}

function guardTarget() {
  try {
    assertDevTarget(config);
  } catch (error) {
    fail(error.message);
  }
}

/** Yığın ayaktayken: hedef + Auth profili kapıları. */
function guardRunningStack() {
  guardTarget();
  try {
    assertDevProfile(readAuthProfile());
  } catch (error) {
    fail(error.message);
  }
}

const PROJECT = "ltz-dev";
const DEFAULT_SERVICES = ["postgres", "mailpit", "mail-service", "auth-service", "user-service", "api-gateway"];
/** Hesap oluşturmanın dayandığı servisler: bunlar sağlıklı olmadan kayıt akışı çalışmaz. */
const MUST_BE_HEALTHY = ["auth-service", "api-gateway", "mailpit"];
const UP_FLAGS = new Set(["--no-seed", "--no-build", "--dry-run"]);

function containerHealth(service) {
  const result = spawnSync(
    "docker",
    ["inspect", "-f", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}", `${PROJECT}-${service}-1`],
    { encoding: "utf8" }
  );
  return result.status === 0 ? result.stdout.trim() : "yok";
}

async function up(args) {
  const flags = new Set(args.filter((arg) => arg.startsWith("--")));
  for (const flag of flags) if (!UP_FLAGS.has(flag)) fail(`Bilinmeyen seçenek: ${flag}`);
  const named = args.filter((arg) => !arg.startsWith("--"));
  const targets = named.length > 0 ? named : DEFAULT_SERVICES;
  const composeArgs = ["compose", "-p", PROJECT, "--env-file", ".env.dev", "--profile", "dev-mail", "up", "-d", ...(flags.has("--no-build") ? [] : ["--build"]), ...targets];
  const waitFor = MUST_BE_HEALTHY.filter((name) => targets.includes(name));
  const seeding = !flags.has("--no-seed") && waitFor.includes("auth-service") && waitFor.includes("api-gateway");

  if (flags.has("--dry-run")) {
    console.log(`docker ${composeArgs.join(" ")}`);
    console.log(`sağlık beklenir: ${waitFor.join(", ") || "(yok)"}`);
    console.log(seeding ? "ardından: hesap oluşturma (seed)" : "hesap oluşturma: atlanır");
    return;
  }
  if (!existsSync(resolve(root, ".env.dev"))) fail(".env.dev yok. Önce şunu çalıştır: node ops/generate-auth-dev-keys.mjs");
  guardTarget();

  console.log("\n▶ Servisler başlatılıyor");
  const compose = spawnSync("docker", composeArgs, { cwd: root, stdio: "inherit" });
  if (compose.status !== 0) fail("docker compose başarısız oldu (yukarıdaki çıktıya bak).");

  console.log("\n▶ Sağlık bekleniyor");
  const deadline = Date.now() + 240_000;
  for (const service of waitFor) {
    while (containerHealth(service) !== "healthy") {
      if (Date.now() > deadline) fail(`${service} 4 dakikada sağlıklı olmadı. İncele: docker compose -p ${PROJECT} logs ${service} --tail 50`);
      await new Promise((done) => setTimeout(done, 2000));
    }
    console.log(`  ✓ ${service}`);
  }

  if (!seeding) {
    console.log("\nHesap oluşturma atlandı.");
    return;
  }
  console.log("\n▶ Test hesapları");
  guardRunningStack();
  await seed();
}

const [command = "seed", ...rest] = process.argv.slice(2);
if (command === "up") {
  await up(rest);
} else if (command === "seed") {
  guardRunningStack();
  await seed();
} else if (command === "list") {
  guardRunningStack();
  list();
} else if (command === "totp") {
  guardRunningStack();
  showTotp(rest[0]);
} else {
  fail(`Bilinmeyen komut: ${command} (up | seed | list | totp <ad>)`);
}
