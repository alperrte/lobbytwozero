// dev-accounts.mjs için saf yardımcılar (ağ/veritabanı yok; test edilebilir).
// `argon2Sync` Node 24.7'de eklendi. Adlandırılmış içe aktarma eski Node'da modül yüklenirken anlaşılmaz bir hata verirdi; bu yüzden
// ad alanı ile içe aktarılır ve sürüm `assertNodeSupported` ile önceden, anlaşılır bir mesajla denetlenir.
import * as nodeCrypto from "node:crypto";

const { createHmac, randomBytes } = nodeCrypto;

/** Betik Node 24.7+ ister (parola özeti için `crypto.argon2Sync`). Eski sürümde açık bir mesajla durur. */
export function assertNodeSupported(version = process.versions.node) {
  const [major = 0, minor = 0] = String(version).split(".").map(Number);
  if (major < 24 || (major === 24 && minor < 7)) {
    throw new Error(`Node 24.7 veya üstü gerekli (bu makinede ${version}): parola özeti için crypto.argon2Sync kullanılır. https://nodejs.org`);
  }
}

/** Rol → hesap eşlemesi. Kullanıcı adı = e-postanın yerel kısmı (Auth: 3-20, yalnız a-z0-9_). `mfa`: Auth bu roller için TOTP ister. */
export const DEV_ACCOUNTS = Object.freeze([
  { local: "user", role: "PLAYER", mfa: false },
  { local: "creator", role: "CONTENT_CREATOR", mfa: false },
  { local: "developer", role: "GAME_DEVELOPER", mfa: false },
  { local: "moderator", role: "MODERATOR", mfa: true },
  { local: "admin", role: "ADMIN", mfa: true }
]);

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]", "::1"]);
const DOMAIN_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/;

/** Yalnız yerel geliştirme: hedef adresler yerel olmalı, kapsayıcı `ltz-dev-` ile başlamalı, ortam production olmamalı. */
export function assertDevTarget({ gatewayUrl, mailpitUrl, container, nodeEnv }) {
  const problems = [];
  for (const [label, value] of [["GATEWAY_URL", gatewayUrl], ["MAILPIT_URL", mailpitUrl]].filter(([, value]) => value !== undefined)) {
    let host = "";
    try {
      host = new URL(value).hostname;
    } catch {
      problems.push(`${label} geçerli bir adres değil`);
      continue;
    }
    if (!LOCAL_HOSTS.has(host)) problems.push(`${label} yerel olmalı (localhost/127.0.0.1), verilen: ${host}`);
  }
  if (!/^ltz-dev-[a-z0-9_-]+$/.test(container ?? "")) problems.push("Postgres kapsayıcısı ltz-dev- ile başlamalı");
  if (nodeEnv === "production") problems.push("NODE_ENV=production iken çalışmaz");
  if (problems.length > 0) throw new Error(`Güvenlik kapısı: ${problems.join("; ")}`);
}

/**
 * Auth'un çalıştığı Spring profili kapısı. Auth zaten gevşek ayarları yalnız dev/test profilinde kabul eder; aynı beyaz liste
 * burada da uygulanır. Profil okunamazsa (boş/bilinmeyen) KAPALI davranılır: emin olunamayan ortamda zayıf parola yazılmaz.
 */
export function assertDevProfile(profile) {
  const active = String(profile ?? "").split(",").map((item) => item.trim().toLowerCase()).filter(Boolean);
  if (active.length === 0) throw new Error("Güvenlik kapısı: Auth profili okunamadı (SPRING_PROFILES_ACTIVE); dev olduğundan emin olunamıyor.");
  const outsiders = active.filter((item) => item !== "dev" && item !== "test");
  if (outsiders.length > 0) throw new Error(`Güvenlik kapısı: Auth profili dev/test değil (${outsiders.join(",")}); zayıf parola yazılmaz.`);
}

export function validateConfig({ password, domain }) {
  if (typeof password !== "string" || password.length === 0) {
    throw new Error("DEV_ACCOUNTS_PASSWORD tanımlı değil (parola depoya yazılmaz; .env.dev'e ekle ya da ortamdan ver).");
  }
  if (password.length > 128) throw new Error("DEV_ACCOUNTS_PASSWORD en fazla 128 karakter olabilir.");
  if (!DOMAIN_PATTERN.test(domain ?? "")) throw new Error("DEV_ACCOUNTS_DOMAIN geçerli bir alan adı olmalı.");
}

export function emailFor(account, domain) {
  return `${account.local}@${domain}`;
}

/** Spring `DelegatingPasswordEncoder` biçimi: `{argon2}$argon2id$v=19$m=…,t=…,p=…$salt$hash` (Base64, dolgusuz). */
export function hashPassword(password) {
  const memory = 19456;
  const passes = 2;
  const parallelism = 1;
  const nonce = randomBytes(16);
  const tag = nodeCrypto.argon2Sync("argon2id", { message: password, nonce, parallelism, tagLength: 32, memory, passes });
  const b64 = (buffer) => buffer.toString("base64").replace(/=+$/, "");
  return `{argon2}$argon2id$v=19$m=${memory},t=${passes},p=${parallelism}$${b64(nonce)}$${b64(tag)}`;
}

const BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export function base32Decode(text) {
  const clean = text.replace(/=+$/, "").replace(/\s+/g, "").toUpperCase();
  let bits = 0;
  let value = 0;
  const bytes = [];
  for (const char of clean) {
    const index = BASE32.indexOf(char);
    if (index < 0) throw new Error("Geçersiz Base32 karakteri");
    value = (value << 5) | index;
    bits += 5;
    if (bits >= 8) {
      bytes.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(bytes);
}

/** RFC 6238 (HMAC-SHA1, 30 sn adım). */
export function totp(secretBase32, nowMs = Date.now(), digits = 6) {
  const counter = Math.floor(nowMs / 1000 / 30);
  const message = Buffer.alloc(8);
  message.writeBigUInt64BE(BigInt(counter));
  const mac = createHmac("sha1", base32Decode(secretBase32)).update(message).digest();
  const offset = mac[mac.length - 1] & 0x0f;
  const binary = mac.readUInt32BE(offset) & 0x7fffffff;
  return String(binary % 10 ** digits).padStart(digits, "0");
}

export function secondsLeftInStep(nowMs = Date.now()) {
  return 30 - (Math.floor(nowMs / 1000) % 30);
}

/** SQL dizge değeri için tek tırnak kaçışı. Girdiler zaten sabit/biçimli; ek savunma. */
export function sqlLiteral(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

const DEV_ENV_KEYS = new Set(["DEV_ACCOUNTS_PASSWORD", "DEV_ACCOUNTS_DOMAIN"]);

/**
 * `.env.dev` metninden YALNIZ DEV_ACCOUNTS_* anahtarlarını okur; diğer satırlar (anahtarlar, sırlar) hiç işlenmez ya da
 * döndürülmez. Yorum satırları ve tırnaklı değerler desteklenir.
 */
export function parseDevEnv(text) {
  const found = {};
  for (const rawLine of String(text ?? "").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line === "" || line.startsWith("#")) continue;
    const index = line.indexOf("=");
    if (index < 0) continue;
    const key = line.slice(0, index).trim();
    if (!DEV_ENV_KEYS.has(key)) continue;
    let value = line.slice(index + 1).trim();
    if (/^(".*"|'.*')$/.test(value)) value = value.slice(1, -1);
    if (value !== "") found[key] = value;
  }
  return found;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** SQL'e gömülecek kimlik değerinin UUID olduğunu doğrular (başka bir veritabanından okunan değer SQL'e karışmasın). */
export function assertUuid(value) {
  if (typeof value !== "string" || !UUID.test(value)) throw new Error("Beklenen UUID biçiminde değil");
  return value;
}

const RESERVED_SUFFIX = /\.(test|example|invalid|localhost|local)$/;
const EXAMPLE_DOMAINS = new Set(["example.com", "example.net", "example.org"]);

/** E-posta teslim edilemeyen (ayrılmış) alan adı mı? `.test`, `.example`, `.invalid`, `.localhost`, `.local`, example.* */
export function isUndeliverableDomain(domain) {
  const value = String(domain ?? "").trim().toLowerCase();
  return value !== "" && (RESERVED_SUFFIX.test(value) || EXAMPLE_DOMAINS.has(value));
}

/**
 * `AUTH_2FA_REQUIRED_ROLES` ham değerinden 2FA zorunlu roller. Tanımsız (undefined) = Auth varsayılanı ADMIN,MODERATOR;
 * boş değer = hiçbir rol zorunlu değil (Auth bunu yalnız dev/test profilinde kabul eder).
 */
export function parseRequiredRoles(raw) {
  if (raw === undefined) return ["ADMIN", "MODERATOR"];
  return String(raw).split(",").map((role) => role.trim().toUpperCase()).filter(Boolean);
}
