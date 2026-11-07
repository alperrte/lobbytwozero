// dev-mail.mjs için saf yardımcılar (dosya/terminal yok; test edilebilir).

/**
 * Ekibin ortak test Gmail hesabı (SIR DEĞİL: yalnız adres; hesaba girmek için uygulama şifresi gerekir ve o depoda yoktur).
 * Başka bir hesap denemek için `DEV_MAIL_USER` ortam değişkeni.
 */
export const DEFAULT_MAIL_USER = "lobbytwozerotest@gmail.com";

/** Gmail SMTP'nin SIR OLMAYAN sabit ayarları: herkes için aynıdır; yalnız uygulama şifresi kişiye özeldir. */
export function smtpOnSettings(address, appPassword) {
  return {
    MAIL_SMTP_HOST: "smtp.gmail.com",
    MAIL_SMTP_PORT: "587",
    MAIL_SMTP_SECURITY: "STARTTLS",
    MAIL_SMTP_USERNAME: address,
    MAIL_SMTP_PASSWORD: appPassword,
    MAIL_FROM_ADDRESS: address,
    MAIL_ALLOWED_LINK_HOSTS: "localhost",
    AUTH_MAIL_ADAPTER: "mail-service"
  };
}

/** Gerçek SMTP'yi kapatır: boş satırlar e-postayı yeniden Mailpit'e (http://localhost:8025) düşürür. */
export function smtpOffSettings() {
  return {
    MAIL_SMTP_HOST: "",
    MAIL_SMTP_PORT: "",
    MAIL_SMTP_SECURITY: "",
    MAIL_SMTP_USERNAME: "",
    MAIL_SMTP_PASSWORD: "",
    MAIL_FROM_ADDRESS: "",
    MAIL_ALLOWED_LINK_HOSTS: "",
    AUTH_MAIL_ADAPTER: "dev-smtp"
  };
}

const GMAIL = /^[a-z0-9][a-z0-9._%+-]{0,62}@gmail\.com$/i;

export function validateGmailAddress(address) {
  if (!GMAIL.test(String(address ?? "").trim())) {
    throw new Error("Gmail adresi (…@gmail.com) bekleniyor.");
  }
  return String(address).trim().toLowerCase();
}

/**
 * Google "uygulama şifresi" 16 harftir; Google onu 4'lü gruplar halinde boşluklu gösterir. Boşluklar atılır. Hata mesajı
 * girilen değeri ASLA içermez.
 */
export function normalizeAppPassword(raw) {
  const value = String(raw ?? "").replace(/\s+/g, "");
  if (!/^[a-zA-Z]{16}$/.test(value)) {
    throw new Error("Uygulama şifresi 16 harf olmalı (Google 4'lü gruplar halinde gösterir; boşluklar önemsiz).");
  }
  return value.toLowerCase();
}

/**
 * `.env` metninde verilen anahtarları günceller: var olan AKTİF `KEY=` satırının değeri değişir (yorum satırlarına dokunulmaz),
 * olmayan anahtar sona eklenir. Diğer satırlar, sıra ve satır sonu (LF/CRLF) aynen korunur.
 */
export function applyEnvChanges(text, changes) {
  const eol = text.includes("\r\n") ? "\r\n" : "\n";
  const lines = text.length === 0 ? [] : text.replace(/\r\n/g, "\n").split("\n");
  if (lines.length > 0 && lines[lines.length - 1] === "") lines.pop();
  for (const [key, value] of Object.entries(changes)) {
    if (/[\r\n]/.test(value)) throw new Error(`${key} değeri satır sonu içeremez.`);
    let index = -1;
    lines.forEach((line, i) => {
      if (line.startsWith(`${key}=`)) index = i;
    });
    if (index >= 0) lines[index] = `${key}=${value}`;
    else lines.push(`${key}=${value}`);
  }
  return lines.join(eol) + eol;
}

/** Durum özeti: değer göstermez; yalnız host, adaptör ve şifrenin dolu olup olmadığı. */
export function describeSmtp(text) {
  const read = (key) => {
    const line = text.replace(/\r\n/g, "\n").split("\n").reverse().find((item) => item.startsWith(`${key}=`));
    return line === undefined ? "" : line.slice(key.length + 1).trim();
  };
  const host = read("MAIL_SMTP_HOST");
  return {
    real: host !== "",
    host,
    adapter: read("AUTH_MAIL_ADAPTER") || "dev-smtp (varsayılan)",
    passwordSet: read("MAIL_SMTP_PASSWORD") !== ""
  };
}
