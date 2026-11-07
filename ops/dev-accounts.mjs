#!/usr/bin/env node
// Yerel geliştirme için her role bir test hesabı oluşturur/günceller (yalnız ltz-dev yığını, yalnız yerel adresler).
//
//   DEV_ACCOUNTS_PASSWORD=...  node ops/dev-accounts.mjs            # hesapları oluştur/güncelle ve girişi doğrula
//   node --env-file=.env.dev ops/dev-accounts.mjs                   # parola .env.dev'deki DEV_ACCOUNTS_PASSWORD'dan
//   node ops/dev-accounts.mjs list                                  # hesapları ve rollerini göster
//   node ops/dev-accounts.mjs totp admin                            # admin/moderator için anlık 2FA kodu
//
// Hesaplar: user (PLAYER), creator (CONTENT_CREATOR), developer (GAME_DEVELOPER), moderator (MODERATOR), admin (ADMIN);
// e-posta `<ad>@${DEV_ACCOUNTS_DOMAIN:-ltz.com}`.
//
// Neden doğrudan veritabanı? Auth parola alt sınırı 15 karakterdir; kısa bir geliştirme parolası API'den geçmez. Betik hesabı
// GERÇEK akışla açar (kayıt → Mailpit kodu → doğrulama → giriş; olay ve rol kayıtları Auth'un kendi kodundan çıkar), sonra
// yalnız parola özetini yazar ve gerekli rolü ekler. ADMIN/MODERATOR için Auth TOTP zorunlu kıldığından, rol verilmeden ÖNCE
// TOTP API ile kurulur; sır ve kurtarma kodları gitignore'daki `.dev-accounts.local.json` dosyasına yazılır.
//
// Güvenlik: yalnız localhost'a ve `ltz-dev-*` Postgres kapsayıcısına çalışır, NODE_ENV=production iken reddeder. Parola
// depoya yazılmaz (DEV_ACCOUNTS_PASSWORD zorunlu, varsayılanı yok) ve ekrana basılmaz. Tekrar çalıştırmak güvenlidir.

import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  DEV_ACCOUNTS,
  assertDevTarget,
  emailFor,
  extractCode,
  hashPassword,
  secondsLeftInStep,
  sqlLiteral,
  temporaryPassword,
  totp,
  validateConfig
} from "./dev-accounts-lib.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const STATE_FILE = resolve(root, ".dev-accounts.local.json");

const config = {
  password: process.env.DEV_ACCOUNTS_PASSWORD,
  domain: process.env.DEV_ACCOUNTS_DOMAIN ?? "ltz.com",
  gatewayUrl: process.env.DEV_GATEWAY_URL ?? "http://localhost:5080",
  mailpitUrl: process.env.DEV_MAILPIT_URL ?? "http://localhost:8025",
  origin: process.env.DEV_FRONTEND_ORIGIN ?? "http://localhost:5001",
  container: process.env.DEV_POSTGRES_CONTAINER ?? "ltz-dev-postgres-1",
  nodeEnv: process.env.NODE_ENV
};

function fail(message) {
  console.error(`✗ ${message}`);
  process.exit(1);
}

// ---------- yerel durum (TOTP sırları) ----------

function readState() {
  if (!existsSync(STATE_FILE)) return {};
  try {
    return JSON.parse(readFileSync(STATE_FILE, "utf8"));
  } catch {
    fail(`${STATE_FILE} okunamadı (bozuk JSON). Silip yeniden çalıştırabilirsin; mevcut 2FA hesapları için DB'de TOTP'yi sıfırlaman gerekir.`);
  }
}

function writeState(state) {
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

  if (account.mfa) {
    response = await session.request("POST", "/api/v1/auth/2fa/totp/setup", {});
    const secret = response.json?.secret;
    if (response.status !== 200 || !secret) fail(`${email} TOTP kurulumu başarısız: ${describe(response)}`);
    await waitForFreshTotpStep();
    response = await session.request("POST", "/api/v1/auth/2fa/totp/activate", { code: totp(secret) });
    if (response.status !== 200) fail(`${email} TOTP etkinleştirme başarısız: ${describe(response)}`);
    state[email] = { totpSecret: secret, recoveryCodes: response.json?.recoveryCodes ?? [] };
    writeState(state);
  }
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
  if (!entry?.totpSecret) fail("Bu hesap için yerel TOTP sırrı yok. Önce `node ops/dev-accounts.mjs` çalıştır.");
  console.log(`${totp(entry.totpSecret)}  (${secondsLeftInStep()} sn geçerli)`);
}

try {
  assertDevTarget(config);
} catch (error) {
  fail(error.message);
}

const [command = "seed", argument] = process.argv.slice(2);
if (command === "seed") await seed();
else if (command === "list") list();
else if (command === "totp") showTotp(argument);
else fail(`Bilinmeyen komut: ${command} (seed | list | totp <ad>)`);
