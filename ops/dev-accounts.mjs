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
// !!! BİLİNÇLİ KURAL İHLALİ — YALNIZ YEREL GELİŞTİRME !!!
// Hesaplar kayıt akışından (e-posta doğrulaması, parola kuralları) GEÇİRİLMEDEN doğrudan VERİTABANINA yazılır:
//   - auth_db:  `accounts` (ACTIVE, e-posta doğrulanmış, kısa parolanın Argon2 özeti; Auth alt sınırı 15 karakterdir),
//               PLAYER rolü ve durum geçmişi; ADMIN/MODERATOR/CONTENT_CREATOR/GAME_DEVELOPER rolü `account_roles`'a doğrudan
//               eklenir (normal yolda ADMIN/MODERATOR yalnız TOTP'si etkin hesaba verilir; bu kural dev için atlanır).
//   - user_db:  her hesap için `user_profiles` satırı (kullanıcı adı kopyasıyla), User servisinin olay almasını beklemeden.
// Bu yüzden e-posta, SMTP, Mailpit ya da Mail Service ÇALIŞMAK ZORUNDA DEĞİLDİR; gerçek SMTP açıkken de çalışır ve hiçbir yere
// e-posta göndermez. 2FA: Auth ADMIN/MODERATOR'a giriş sırasında TOTP ister (AUTH_2FA_REQUIRED_ROLES). EN KOLAY DEV YOLU: .env.dev'e
// `AUTH_2FA_REQUIRED_ROLES=` (boş; Auth bunu yalnız dev/test profilinde kabul eder) ekleyip Auth'u yeniden başlatmak: betik o zaman
// hiç TOTP kurmaz, admin/moderator yalnız parolayla girer ve hiçbir e-posta çıkmaz. Boş değilse betik hesabı önce rolsüz açar, API ile
// giriş yapıp TOTP'yi kurar (Auth "2FA etkinleştirildi" bildirimi e-postası gönderir; gerçek SMTP + gerçek alan adı varsa betik
// BUNU YAPMAZ ve durur), SONRA rolü doğrudan verir. Sır ve kurtarma kodları makine düzeyindeki `~/.ltz/dev-accounts.json`
// dosyasındadır (depoya girmez; aynı bilgisayardaki tüm klasörler aynı veritabanını paylaştığı için aynı dosyayı kullanır).
//
// Güvenlik: yalnız localhost'a, `ltz-dev-*` Postgres kapsayıcısına ve dev/test profilindeki Auth'a çalışır, NODE_ENV=production
// iken reddeder. Parola depoya yazılmaz (DEV_ACCOUNTS_PASSWORD zorunlu, varsayılanı yok) ve ekrana basılmaz. Tekrar
// çalıştırmak güvenlidir.

import { spawnSync } from "node:child_process";
import { chmodSync, existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  DEV_ACCOUNTS,
  assertDevProfile,
  assertNodeSupported,
  assertDevTarget,
  assertUuid,
  emailFor,
  hashPassword,
  isUndeliverableDomain,
  parseDevEnv,
  parseRequiredRoles,
  secondsLeftInStep,
  sqlLiteral,
  totp,
  validateConfig
} from "./dev-accounts-lib.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");

try {
  assertNodeSupported();
} catch (error) {
  console.error(`✗ ${error.message}`);
  process.exit(1);
}
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
  origin: process.env.DEV_FRONTEND_ORIGIN ?? "http://localhost:5001",
  container: process.env.DEV_POSTGRES_CONTAINER ?? "ltz-dev-postgres-1",
  authContainer: process.env.DEV_AUTH_CONTAINER ?? "ltz-dev-auth-service-1",
  mailContainer: process.env.DEV_MAIL_CONTAINER ?? "ltz-dev-mail-service-1",
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

function psql(sql, database = "auth_db") {
  const result = spawnSync(
    "docker",
    ["exec", "-i", config.container, "sh", "-c", `psql -U "$POSTGRES_USER" -d ${database} -v ON_ERROR_STOP=1 -tA`],
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

// ---------- akışlar ----------

async function waitForFreshTotpStep() {
  if (secondsLeftInStep() < 4) await new Promise((done) => setTimeout(done, 5000));
}

/** Hesabı rolsüz (yalnız PLAYER) ve doğrulanmış olarak doğrudan veritabanına yazar; kayıt akışı ve e-posta kullanılmaz. */
function createInDatabase(account, email, passwordHash) {
  const e = sqlLiteral(email.toLowerCase());
  const u = sqlLiteral(account.local);
  psql(`
    begin;
    insert into accounts (id, email, email_normalized, username, username_normalized, password_hash, status,
                          email_verified_at, token_version, created_at, updated_at, version)
    values (gen_random_uuid(), ${sqlLiteral(email)}, ${e}, ${u}, ${u}, ${sqlLiteral(passwordHash)}, 'ACTIVE',
            now(), 0, now(), now(), 0);
    insert into account_roles (account_id, role_id)
      select a.id, r.id from accounts a, roles r where a.email_normalized = ${e} and r.code = 'PLAYER';
    insert into account_role_history (id, account_id, role_code, action, reason, actor_id, created_at)
      select gen_random_uuid(), a.id, 'PLAYER', 'GRANT', 'dev-accounts seed', a.id, now()
        from accounts a where a.email_normalized = ${e};
    insert into account_status_history (id, account_id, from_status, to_status, reason, actor_id, created_at)
      select gen_random_uuid(), a.id, 'PENDING_VERIFICATION', 'ACTIVE', 'dev-accounts seed', a.id, now()
        from accounts a where a.email_normalized = ${e};
    commit;
  `);
}

/**
 * User servisinde profil satırını doğrudan yazar (olayı beklemeden). Satır varsa kullanıcı adı kopyası ve durum güncellenir.
 * Hesap kimliği Auth veritabanından okunur ve SQL'e gömülmeden önce UUID olduğu doğrulanır.
 */
function ensureUserProfile(account, email) {
  const id = psql(`select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())};`);
  try {
    assertUuid(id);
  } catch {
    fail(`${email} için hesap kimliği okunamadı`);
  }
  const u = sqlLiteral(account.local);
  psql(
    `
    insert into user_profiles (user_id, username_snapshot, username_normalized, account_status, created_at, updated_at, version)
    values ('${id}', ${u}, ${u}, 'ACTIVE', now(), now(), 0)
    on conflict (user_id) do update
      set username_snapshot = excluded.username_snapshot, username_normalized = excluded.username_normalized,
          account_status = 'ACTIVE', updated_at = now();
  `,
    "user_db"
  );
}

/** Auth konteynerindeki 2FA zorunlu rol listesi (yalnız bu değişken okunur). Tanımsızsa Auth varsayılanı geçerlidir. */
function requiredTwoFactorRoles() {
  const result = spawnSync("docker", ["exec", config.authContainer, "printenv", "AUTH_2FA_REQUIRED_ROLES"], { encoding: "utf8" });
  return parseRequiredRoles(result.status === 0 ? result.stdout : undefined);
}

/** Mail Service'in SMTP sunucusu (yalnız host; boş = Mailpit/geliştirme gönderimi). Konteyner yoksa boş sayılır: mail gitmez. */
function smtpHost() {
  const result = spawnSync("docker", ["exec", config.mailContainer, "printenv", "MAIL_SMTP_HOST"], { encoding: "utf8" });
  return result.status === 0 ? result.stdout.trim() : "";
}

/**
 * TOTP'yi API ile kurmak Auth'a "iki aşamalı doğrulama etkinleştirildi" bildirim e-postası gönderttirir. Gerçek SMTP açıkken ve
 * adres teslim edilebilir bir alan adındaysa (ör. ltz.com) bu e-posta GERÇEK bir adrese gider; bu yüzden o durumda durulur.
 */
function assertMailSafe(email) {
  const host = smtpHost();
  const domain = email.split("@")[1] ?? "";
  if (host !== "" && !isUndeliverableDomain(domain)) {
    fail(
      `${email} için 2FA kurulumu bildirim e-postası gönderir, ama gerçek SMTP açık (${host}) ve ${domain} gerçek bir alan adı: ` +
        "e-posta başkasına gider. Çözüm (biri yeter): .env.dev'e AUTH_2FA_REQUIRED_ROLES= (boş, yalnız dev) ekleyip Auth'u yeniden " +
        "başlat (admin/moderator 2FA'sız girer, hiç mail çıkmaz); ya da DEV_ACCOUNTS_DOMAIN=ltz.test kullan; ya da geçici olarak " +
        "MAIL_SMTP_HOST'u boşaltıp mail-service'i yeniden başlat."
    );
  }
}

/** 2FA zorunlu değilken kalmış TOTP kaydını siler (yerel sır eşleşmiyorsa girişi tıkamasın). Rol ve hesap korunur. */
function disableTotp(email, state) {
  const owner = `(select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())})`;
  psql(`
    begin;
    delete from recovery_codes where account_id = ${owner};
    delete from totp_credentials where account_id = ${owner};
    update accounts set token_version = token_version + 1 where id = ${owner};
    commit;
  `);
  delete state[email];
  writeState(state);
}

/** Yeni (henüz rolsüz) 2FA'lı hesap: parolayla giriş yapıp TOTP kurulur; rol `finalizeInDatabase` ile sonra verilir. */
async function enrollNewAccount(email, state) {
  const session = new Session();
  await session.csrf();
  const response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
  if (response.json?.status !== "AUTHENTICATED") fail(`${email} ilk giriş başarısız: ${describe(response)}`);
  await enrollTotp(session, email, state);
}

/** Giriş yapmış oturumda TOTP kurar ve etkinleştirir; sır ve kurtarma kodları makine dosyasına yazılır. */
async function enrollTotp(session, email, state) {
  assertMailSafe(email);
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
async function repairTotp(account, email, state, reason = "2FA sırrı yok") {
  assertMailSafe(email); // veritabanına dokunmadan ÖNCE: yarım kalmış (rolsüz/2FA'sız) hesap bırakılmasın
  const e = sqlLiteral(email.toLowerCase());
  const owner = `(select id from accounts where email_normalized = ${e})`;
  process.stdout.write(`${reason}, yeniden kuruluyor … `);
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
  if (first === null || !first.includes("MFA_FAILED")) return first;
  await new Promise((done) => setTimeout(done, (secondsLeftInStep() + 1) * 1000));
  return verifyLoginOnce(account, email, state);
}

async function verifyLoginOnce(account, email, state) {
  const session = new Session();
  await session.csrf();
  let response = await session.request("POST", "/api/v1/auth/login", { identifier: email, password: config.password });
  if (response.json?.status === "MFA_REQUIRED") {
    const secret = state[email]?.totpSecret;
    if (!secret) return "TOTP sırrı bu makinede yok (2FA bu hesapta etkin)";
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
  const profile = await session.request("GET", "/api/v1/user/me");
  if (profile.status !== 200) return `profil okunamadı: ${describe(profile)}`;
  if (String(profile.json?.username ?? "").toLowerCase() !== account.local) return "profildeki kullanıcı adı beklenenle uyuşmuyor";
  return null;
}

async function seed() {
  try {
    validateConfig(config);
  } catch (error) {
    fail(error.message);
  }
  const state = readState();
  const requiredRoles = requiredTwoFactorRoles();
  if (requiredRoles.length === 0) console.log("(AUTH_2FA_REQUIRED_ROLES boş: admin/moderator 2FA'sız girer, TOTP kurulmaz, e-posta gitmez)");
  const results = [];
  for (const account of DEV_ACCOUNTS) {
    const email = emailFor(account, config.domain);
    const needsTotp = requiredRoles.includes(account.role);
    process.stdout.write(`• ${email} (${account.role}) … `);
    const existing = psql(`select id from accounts where email_normalized = ${sqlLiteral(email.toLowerCase())};`);
    const passwordHash = hashPassword(config.password);
    if (needsTotp && (!existing || !state[email]?.totpSecret)) assertMailSafe(email); // 2FA kurulacaksa mail güvenliği önce
    if (!existing) {
      createInDatabase(account, email, passwordHash);
      if (needsTotp) await enrollNewAccount(email, state);
    } else if (needsTotp && !state[email]?.totpSecret) {
      await repairTotp(account, email, state);
    }
    finalizeInDatabase(account, email, passwordHash);
    ensureUserProfile(account, email);
    let problem = await verifyLogin(account, email, state);
    // Aynı veritabanını başka bir klasör/makine yeniden kurmuş olabilir: yerel sır artık eşleşmiyor. Dev hesabı olduğundan
    // 2FA bir kez sıfırlanıp (zorunluysa yeniden kurulup) tekrar denenir; aynı 30 sn dilimi tekrarı verifyLogin içinde zaten denendi.
    if (problem && existing && (problem.includes("MFA_FAILED") || problem.includes("TOTP sırrı"))) {
      if (needsTotp) {
        await repairTotp(account, email, state, "2FA sırrı eşleşmiyor");
        finalizeInDatabase(account, email, passwordHash);
      } else {
        disableTotp(email, state);
      }
      problem = await verifyLogin(account, email, state);
    }
    console.log(problem ? `✗ ${problem}` : "✓ giriş doğrulandı");
    results.push({ email, role: account.role, mfa: needsTotp, ok: !problem });
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
/** Hesap doğrulamasının dayandığı servisler (giriş Auth'tan, profil User'dan, ikisi de Gateway üzerinden). Mail gerekmez. */
const MUST_BE_HEALTHY = ["auth-service", "api-gateway", "user-service"];
const UP_FLAGS = new Set(["--no-seed", "--no-build", "--dry-run"]);

function containerHealth(service) {
  const result = spawnSync(
    "docker",
    ["inspect", "-f", "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}", `${PROJECT}-${service}-1`],
    { encoding: "utf8" }
  );
  return result.status === 0 ? result.stdout.trim() : "yok";
}

/**
 * Servis hazır mı? Sağlık kontrolü tanımlıysa `healthy` beklenir. Tanımsızsa (ör. user-service) kapsayıcının çalışıyor olması ve
 * Spring'in "Started ...Application" satırını yazmış olması beklenir; aksi halde bu servisler hiç "healthy" olmaz ve kurulum takılır.
 */
function containerReady(service) {
  const state = containerHealth(service);
  if (state === "healthy") return true;
  if (state !== "running") return false;
  const logs = spawnSync("docker", ["logs", "--tail", "200", `${PROJECT}-${service}-1`], { encoding: "utf8" });
  return /Started \w+Application/.test(`${logs.stdout ?? ""}${logs.stderr ?? ""}`);
}

async function up(args) {
  const flags = new Set(args.filter((arg) => arg.startsWith("--")));
  for (const flag of flags) if (!UP_FLAGS.has(flag)) fail(`Bilinmeyen seçenek: ${flag}`);
  const named = args.filter((arg) => !arg.startsWith("--"));
  const targets = named.length > 0 ? named : DEFAULT_SERVICES;
  const composeArgs = ["compose", "-p", PROJECT, "--env-file", ".env.dev", "--profile", "dev-mail", "up", "-d", ...(flags.has("--no-build") ? [] : ["--build"]), ...targets];
  const waitFor = MUST_BE_HEALTHY.filter((name) => targets.includes(name));
  const seeding = !flags.has("--no-seed") && MUST_BE_HEALTHY.every((name) => waitFor.includes(name));

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
    while (!containerReady(service)) {
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
