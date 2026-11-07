// dev-accounts.mjs için saf yardımcılar (ağ/veritabanı yok; test edilebilir).
import { argon2Sync, createHmac, randomBytes } from "node:crypto";

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
  for (const [label, value] of [["GATEWAY_URL", gatewayUrl], ["MAILPIT_URL", mailpitUrl]]) {
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
  const tag = argon2Sync("argon2id", { message: password, nonce, parallelism, tagLength: 32, memory, passes });
  const b64 = (buffer) => buffer.toString("base64").replace(/=+$/, "");
  return `{argon2}$argon2id$v=19$m=${memory},t=${passes},p=${parallelism}$${b64(nonce)}$${b64(tag)}`;
}

/** Geçici (yalnız kayıt sırasında kullanılan) politika uyumlu parola; ilk hash'ten hemen sonra değiştirilir. */
export function temporaryPassword() {
  return `Tmp-${randomBytes(18).toString("base64url")}`;
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

/** Mailpit ileti metninden 6 haneli doğrulama kodunu çıkarır. */
export function extractCode(text) {
  const match = /(?<![0-9])([0-9]{6})(?![0-9])/.exec(text ?? "");
  return match ? match[1] : null;
}
