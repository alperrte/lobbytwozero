#!/usr/bin/env node
// Mail Service e-posta şablonu üreticisi (MAIL-08/09). Mail Service'in kendi dosyalarını okur/üretir; başka servise dokunmaz.
//
// Neden var: 30 HTML gövdesi aynı bileşenlerden (etiket, başlık, kod kutusu, düğme, tarih kutusu, uyarı kutusu, özellik
// satırı) kurulur; elle SQL yazmak tutarsızlık üretir. Kaynak burasıdır, çıktı V84 ve V85 migration'larıdır.
//   - TR metinleri bu dosyada yazılıdır (sitenin "sen" dili); EN konu/düz metin/başlık V82+V83 seed'inden okunur.
//   - Kabuk (logo, altbilgi, açık tema) mail/layout.html ve TemplateRenderer içindedir, burada değil.
//
// KULLANIM
//   node ops/mail-templates.mjs check            V84/V85 dosyaları üreticiyle aynı mı (çıkış 1 = fark var)
//   node ops/mail-templates.mjs preview <klasör> Her şablon için tarayıcıda açılan önizleme HTML'i yazar
//   node ops/mail-templates.mjs write <klasör>   V84/V85 SQL'ini verilen klasöre yazar (migration klasörüne YAZMAZ)
//
// DİKKAT: Flyway migration'ı uygulandıktan sonra değişmez. V84/V85 yayınlandı; tasarımı değiştirmek için üreticiyi
// yeni sürüm (V86+, şablon sürümü 3) üretecek şekilde genişletin, V84/V85'i yeniden üretip ezmeyin. `check` bu
// yüzden "dosyalar üreticiyle aynı mı" diye bakar: üretici bilinçsizce değişirse test kırılır.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
export const RESOURCES = resolve(here, "../backend/services/mail-service/src/main/resources");
const MIGRATIONS = join(RESOURCES, "db/migration");
export const V84_FILE = "V84__redesign_mail_templates_dark.sql";
export const V85_FILE = "V85__add_welcome_mail_type.sql";
export const ALLOWED_VARIABLES = ["code", "link", "validityMinutes", "occurredAt"];

// ---- Kategori: [eyebrow rengi, TR etiket, EN etiket]
const CAT = {
  EMAIL_VERIFICATION: ["accent", "E-POSTA DOĞRULAMA", "EMAIL VERIFICATION"],
  PASSWORD_RESET_LINK: ["accent", "PAROLA SIFIRLAMA", "PASSWORD RESET"],
  ACCOUNT_DELETION_CONFIRMATION: ["danger", "HESAP SİLME", "ACCOUNT DELETION"],
  PASSWORD_CHANGED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  PASSWORD_RESET_COMPLETED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  EMAIL_CHANGED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  TWO_FACTOR_ENABLED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  TWO_FACTOR_DISABLED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  RECOVERY_CODE_USED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  EXTERNAL_IDENTITY_LINKED: ["warn", "GÜVENLİK BİLDİRİMİ", "SECURITY NOTICE"],
  ACCOUNT_SUSPENDED: ["warn", "HESAP DURUMU", "ACCOUNT STATUS"],
  ACCOUNT_BANNED: ["danger", "HESAP DURUMU", "ACCOUNT STATUS"],
  ACCOUNT_DISABLED: ["danger", "HESAP DURUMU", "ACCOUNT STATUS"],
  ACCOUNT_REACTIVATED: ["success", "HESAP DURUMU", "ACCOUNT STATUS"],
  WELCOME: ["accent", "HOŞ GELDİN", "WELCOME"]
};
const NOTE_BORDER = { accent: "#ff2e88", warn: "#f59e0b", danger: "#e11d2e", success: "#22c55e" };
const EB_COLOR = { accent: "#ff2e88", warn: "#f59e0b", danger: "#ff5a68", success: "#22c55e" };
const NOTE_KIND = { PASSWORD_RESET_LINK: "warn", ACCOUNT_DELETION_CONFIRMATION: "danger", EMAIL_VERIFICATION: "accent" };

// ---- TR metinleri (site dili: "sen"). Blok: [tür, metin]. Düz metin: text.
const WARN_SEC = "Bu işlemi sen yapmadıysan hemen parolanı sıfırla ve destek ile iletişime geç.";
const sec = (subject, h1, p, extra = {}) => ({
  subject, h1, blocks: [["p", p], ["info", "Tarih:"], ["note", extra.note ?? WARN_SEC]],
  text: `${p}\nTarih: {{occurredAt}}\n${extra.note ?? WARN_SEC}`
});
const status = (subject, h1, p) => ({ subject, h1, blocks: [["p", p], ["info", "Tarih:"]], text: `${p}\nTarih: {{occurredAt}}` });
const TR = {
  EMAIL_VERIFICATION: {
    subject: "LTZ e-posta doğrulama kodun", h1: "E-posta adresini doğrula",
    blocks: [["p", "LTZ hesabını doğrulamak için aşağıdaki kodu gir. Kod {{validityMinutes}} dakika geçerli."], ["code", "DOĞRULAMA KODU"],
      ["note", "Bu isteği sen yapmadıysan bu e-postayı yok sayabilirsin. Kodu kimseyle paylaşma."]],
    text: "LTZ doğrulama kodun: {{code}}\nKod {{validityMinutes}} dakika geçerli.\nBu isteği sen yapmadıysan bu e-postayı yok say. Kodu kimseyle paylaşma."
  },
  PASSWORD_RESET_LINK: {
    subject: "LTZ parola sıfırlama", h1: "Parolanı sıfırla",
    blocks: [["p", "LTZ parolanı sıfırlamak için aşağıdaki düğmeyi kullan. Bağlantı {{validityMinutes}} dakika geçerli ve yalnız bir kez kullanılabilir."],
      ["button", "Parolayı sıfırla"], ["fallback", "Düğme çalışmazsa bu adresi tarayıcına yapıştır:"],
      ["note", "Bu isteği sen yapmadıysan bu e-postayı yok say; parolan değişmez."]],
    text: "LTZ parolanı sıfırlamak için bağlantıyı aç ({{validityMinutes}} dakika geçerli, tek kullanımlık):\n{{link}}\n\nBu isteği sen yapmadıysan bu e-postayı yok say; parolan değişmez."
  },
  ACCOUNT_DELETION_CONFIRMATION: {
    subject: "LTZ hesap silme onayı", h1: "Hesabını silmeyi onayla",
    blocks: [["p", "LTZ hesabını kalıcı olarak silmek için bağlantıyı aç ve kullanıcı adını yazarak onayla. Bağlantı {{validityMinutes}} dakika geçerli, tek kullanımlık ve bu işlem geri alınamaz."],
      ["button", "Silme işlemini onayla"], ["fallback", "Düğme çalışmazsa bu adresi tarayıcına yapıştır:"],
      ["note", "Bu isteği sen yapmadıysan bu e-postayı yok say ve parolanı değiştir."]],
    text: "LTZ hesabını kalıcı olarak silmek için bağlantıyı aç ve kullanıcı adını yazarak onayla ({{validityMinutes}} dakika geçerli, tek kullanımlık). Bu işlem geri alınamaz:\n{{link}}\n\nBu isteği sen yapmadıysan bu e-postayı yok say ve parolanı değiştir."
  },
  PASSWORD_CHANGED: sec("LTZ parolan değiştirildi", "Parolan değiştirildi", "LTZ hesabının parolası değiştirildi.",
    { note: "Bu değişikliği sen yapmadıysan hemen \"Parolamı unuttum\" ile parolanı sıfırla." }),
  PASSWORD_RESET_COMPLETED: sec("LTZ parolan sıfırlandı", "Parolan sıfırlandı", "LTZ hesabının parolası sıfırlama bağlantısıyla yenilendi.",
    { note: "Bu işlemi sen yapmadıysan parolanı yeniden sıfırla ve hesabının e-postasını kontrol et." }),
  EMAIL_CHANGED: sec("LTZ hesabının e-posta adresi değişti", "E-posta adresin değiştirildi",
    "LTZ hesabının e-posta adresi değiştirildi. Bu bildirim önceki adresine gönderildi.",
    { note: "Bu değişikliği sen yapmadıysan hesabın ele geçirilmiş olabilir; parolanı sıfırla." }),
  TWO_FACTOR_ENABLED: sec("LTZ iki adımlı doğrulama açıldı", "İki adımlı doğrulama açıldı", "LTZ hesabında iki adımlı doğrulama açıldı."),
  TWO_FACTOR_DISABLED: sec("LTZ iki adımlı doğrulama kapatıldı", "İki adımlı doğrulama kapatıldı", "LTZ hesabında iki adımlı doğrulama kapatıldı."),
  RECOVERY_CODE_USED: sec("LTZ kurtarma kodu kullanıldı", "Kurtarma kodu kullanıldı", "LTZ hesabında bir kurtarma kodu kullanıldı."),
  EXTERNAL_IDENTITY_LINKED: sec("LTZ hesabına harici hesap bağlandı", "Harici hesap bağlandı", "LTZ hesabına bir Google ya da Steam hesabı bağlandı."),
  ACCOUNT_SUSPENDED: status("LTZ hesabın askıya alındı", "Hesabın askıya alındı", "LTZ hesabın askıya alındı. Bu süre boyunca hesabınla bazı işlemleri yapamazsın."),
  ACCOUNT_BANNED: status("LTZ hesabın yasaklandı", "Hesabın yasaklandı", "LTZ hesabın platform kuralları nedeniyle yasaklandı ve artık kullanılamaz."),
  ACCOUNT_DISABLED: status("LTZ hesabın devre dışı bırakıldı", "Hesabın devre dışı bırakıldı", "LTZ hesabın devre dışı bırakıldı ve şu anda kullanılamaz."),
  ACCOUNT_REACTIVATED: status("LTZ hesabın yeniden etkinleştirildi", "Hesabın yeniden etkin", "LTZ hesabın yeniden etkinleştirildi. Tekrar giriş yapabilirsin."),
  // WELCOME (V85): özellik satırları sitenin footer grupları (Keşfet, Topluluk, LTZ Performance).
  WELCOME: {
    subject: "LTZ'ye hoş geldin", h1: "LTZ'ye hoş geldin",
    blocks: [["p", "Hesabın hazır. LobbyTwoZero, oyun dünyasını tek bir toplulukta buluşturur."],
      ["features", [["KEŞFET", "Oyunları, haberleri ve incelemeleri keşfet."], ["TOPLULUK", "Forumlara katıl, takım bul, etkinlikleri takip et."],
        ["LTZ PERFORMANCE", "Donanımını ekle, oyunların bilgisayarında nasıl çalışacağını gör."]]],
      ["p", "İyi oyunlar, LTZ ekibi."]],
    text: "LTZ'ye hoş geldin! Hesabın hazır.\nKeşfet: oyunları, haberleri ve incelemeleri keşfet.\nTopluluk: forumlara katıl, takım bul, etkinlikleri takip et.\nLTZ Performance: donanımını ekle, oyunların bilgisayarında nasıl çalışacağını gör.\nİyi oyunlar, LTZ ekibi."
  }
};
const EN_EXTRA = {
  WELCOME: {
    subject: "Welcome to LTZ", h1: "Welcome to LTZ",
    blocks: [["p", "Your account is ready. LobbyTwoZero brings the gaming world together in one community."],
      ["features", [["DISCOVER", "Explore games, news and reviews."], ["COMMUNITY", "Join forums, find a team, follow events."],
        ["LTZ PERFORMANCE", "Add your hardware and see how games will run on your PC."]]],
      ["p", "Have fun, the LTZ team."]],
    text: "Welcome to LTZ! Your account is ready.\nDiscover: explore games, news and reviews.\nCommunity: join forums, find a team, follow events.\nLTZ Performance: add your hardware and see how games will run on your PC.\nHave fun, the LTZ team."
  }
};

// ---- EN: V82+V83 seed'inden bloklara ayrıştır (metin değişmez).
const ROW_PATTERN = /\('([A-Z_]+)', '(tr|en)',\s*\$s\$([\s\S]*?)\$s\$,\s*\$h\$([\s\S]*?)\$h\$,\s*\$x\$([\s\S]*?)\$x\$\)/g;
export function readSeedRows() {
  const text = ["V82__seed_account_mail_templates.sql", "V83__add_account_takeover_notification_types.sql"]
    .map((file) => readFileSync(join(MIGRATIONS, file), "utf8")).join("\n");
  return [...text.matchAll(ROW_PATTERN)].map((m) => ({ code: m[1], locale: m[2], subject: m[3], html: m[4], text: m[5] }));
}

function parseEnglish(row) {
  const h1 = row.html.match(/<h1[^>]*>(.*?)<\/h1>/)[1];
  const paragraphs = [...row.html.matchAll(/<p style="([^"]*)">(.*?)<\/p>/g)].map((m) => ({ style: m[1], html: m[2] }));
  const blocks = [];
  paragraphs.forEach((para, index) => {
    const isLast = index === paragraphs.length - 1;
    if (para.style.includes("letter-spacing")) blocks.push(["code", "VERIFICATION CODE"]);
    else if (para.html.includes('<a href="{{link}}"')) blocks.push(["button", para.html.match(/>([^<]+)<\/a>/)[1]]);
    else if (para.html.includes("word-break")) blocks.push(["fallback", para.html.split("<br>")[0]]);
    else if (/^Date:/.test(para.html)) blocks.push(["info", "Date:"]);
    else if (isLast && (row.code in NOTE_KIND || /^If you did not/.test(para.html))) blocks.push(["note", para.html]);
    else blocks.push(["p", para.html]);
  });
  return { subject: row.subject, h1, blocks, text: row.text };
}

// ---- Bileşenler: satır içi renkler koyu tema; sınıflar layout.html içindeki açık tema iyileştirmesi içindir.
const DISPLAY = "'Chakra Petch','Bahnschrift','Segoe UI',Arial,sans-serif";
const eyebrow = (kind, text) =>
  `<p class="eb-${kind}" style="margin:0 0 10px 0;font-family:${DISPLAY};font-size:12px;font-weight:700;letter-spacing:2.5px;color:${EB_COLOR[kind]}">${text}</p>`;
const heading = (text) =>
  `<h1 class="h1" style="margin:0 0 16px 0;font-family:${DISPLAY};font-size:28px;line-height:1.2;font-weight:700;color:#f1f1f1">${text}</h1>`;
const paragraph = (html) => `<p style="margin:0 0 18px 0">${html}</p>`;
const codeBox = (label) =>
  `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 20px 0"><tr><td class="box" align="center" style="padding:20px 16px;background:#1e1f26;border:1px solid #34343c;border-radius:12px">` +
  `<p class="muted" style="margin:0 0 8px 0;font-family:${DISPLAY};font-size:11px;font-weight:700;letter-spacing:2.5px;color:#a0a7b4">${label}</p>` +
  `<p class="code" style="margin:0;font-size:36px;line-height:1.2;letter-spacing:10px;font-weight:700;font-family:Consolas,Menlo,'Courier New',monospace;color:#ff2e88">{{code}}</p></td></tr></table>`;
const button = (label) =>
  `<table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 18px 0"><tr><td bgcolor="#e11d2e" style="background:#e11d2e;border-radius:10px"><a href="{{link}}" style="display:inline-block;padding:14px 32px;font-family:${DISPLAY};font-size:15px;font-weight:700;letter-spacing:0.5px;line-height:1.2;color:#ffffff;text-decoration:none;border-radius:10px;background:#e11d2e">${label}</a></td></tr></table>`;
const fallback = (intro) =>
  `<p class="muted" style="margin:0 0 22px 0;font-size:13px;line-height:1.5;color:#a0a7b4">${intro}<br><span style="word-break:break-all">{{link}}</span></p>`;
const info = (label) =>
  `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 22px 0"><tr><td class="box" style="padding:13px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px"><span class="muted" style="color:#a0a7b4">${label}</span>&nbsp;&nbsp;<strong>{{occurredAt}}</strong></td></tr></table>`;
const note = (kind, html) =>
  `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:4px 0 0 0"><tr><td class="note" style="padding:14px 18px;background:#1e1f26;border-left:4px solid ${NOTE_BORDER[kind]};border-radius:6px;font-size:14px;line-height:1.55">${html}</td></tr></table>`;
const features = (items) =>
  `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin:0 0 12px 0">` +
  items.map(([title, desc]) =>
    `<tr><td class="box" style="padding:14px 18px;background:#1e1f26;border:1px solid #34343c;border-radius:10px;font-size:14px;line-height:1.5"><p class="eb-accent" style="margin:0 0 4px 0;font-family:${DISPLAY};font-size:11px;font-weight:700;letter-spacing:2.5px;color:#ff2e88">${title}</p><p style="margin:0">${desc}</p></td></tr><tr><td height="8" style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr>`).join("") + `</table>`;

function renderBody(code, locale, def) {
  const [kind, trLabel, enLabel] = CAT[code];
  const out = [eyebrow(kind, locale === "tr" ? trLabel : enLabel), heading(def.h1)];
  for (const [type, value] of def.blocks) {
    if (type === "p") out.push(paragraph(value));
    else if (type === "code") out.push(codeBox(value));
    else if (type === "button") out.push(button(value));
    else if (type === "fallback") out.push(fallback(value));
    else if (type === "info") out.push(info(value));
    else if (type === "features") out.push(features(value));
    else if (type === "note") out.push(note(NOTE_KIND[code] ?? "warn", value));
    else throw new Error(`Bilinmeyen blok türü: ${type}`);
  }
  return out.join("");
}

/** 15 tür x 2 dil = 30 satır; `welcome` V85'e, kalanı V84'e gider. */
export function buildRows() {
  const seed = readSeedRows();
  const rows = [];
  for (const code of Object.keys(CAT)) {
    const english = EN_EXTRA[code] ?? parseEnglish(seed.find((row) => row.code === code && row.locale === "en"));
    rows.push({ code, locale: "tr", subject: TR[code].subject, html: renderBody(code, "tr", TR[code]), text: TR[code].text });
    rows.push({ code, locale: "en", subject: english.subject, html: renderBody(code, "en", english), text: english.text });
  }
  return rows;
}

const valuesOf = (rows) => rows.map((r) => `('${r.code}', '${r.locale}',\n $s$${r.subject}$s$,\n $h$${r.html}$h$,\n $x$${r.text}$x$)`).join(",\n");

export function buildMigrations(rows = buildRows()) {
  if (rows.some((r) => /\$/.test(r.html + r.subject + r.text))) throw new Error("$ karakteri dolar-alıntıyı bozar");
  const base = rows.filter((r) => r.code !== "WELCOME");
  const welcome = rows.filter((r) => r.code === "WELCOME");
  const v84 = `-- MAIL-08/09: koyu temalı, marka paletli yeni e-posta tasarımı (şablon sürümü 2) ve sitenin "sen" diliyle TR metinler.
-- Kabuk (wordmark başlığı, altbilgi, imza çizgisi, açık tema iyileştirmesi) mail/layout.html ve TemplateRenderer içindedir;
-- buradaki gövdeler yalnız içerik bileşenleridir (etiket, başlık, kod kutusu, düğme, tarih kutusu, uyarı kutusu).
-- EN konu ve düz metin sürüm 1 ile AYNIDIR; TR metinler "siz" yerine "sen" kipine çevrildi (siteyle aynı ses).
-- V82/V83 değiştirilmez: sürüm 2 satırları eklenir, sürüm 1 pasife alınır (geri dönüş için satırlar kalır).
-- Kullanılabilen değişkenler: code, link, validityMinutes, occurredAt.

INSERT INTO mail_templates (id, code, locale, subject, body_html, body_text, version, active, created_at, updated_at)
SELECT gen_random_uuid(), t.code, t.locale, t.subject, t.body_html, t.body_text, 2, true, now(), now()
FROM (VALUES
${valuesOf(base)}
) AS t(code, locale, subject, body_html, body_text);

UPDATE mail_templates SET active = false, updated_at = now() WHERE version = 1;
`;
  const v85 = `-- MAIL-05: hoş geldin e-postası. Auth, e-posta doğrulaması tamamlanınca security-notifications ucuna type=WELCOME gönderir
-- (Auth SecurityNotificationType ile aynı ad sözleşmesi). Bildirim yalnız occurredAt taşır; şablon değişken kullanmaz.
-- V81..V84 değiştirilmez: tür kısıtı yeniden oluşturulur, şablon V84 tasarım kuşağıyla (sürüm 2) eklenir.

ALTER TABLE mail_messages DROP CONSTRAINT ck_mail_messages_type;
ALTER TABLE mail_messages ADD CONSTRAINT ck_mail_messages_type CHECK (type IN (
    'EMAIL_VERIFICATION', 'PASSWORD_RESET_LINK', 'ACCOUNT_DELETION_CONFIRMATION',
    'PASSWORD_CHANGED', 'PASSWORD_RESET_COMPLETED', 'EMAIL_CHANGED',
    'ACCOUNT_SUSPENDED', 'ACCOUNT_BANNED', 'ACCOUNT_DISABLED', 'ACCOUNT_REACTIVATED',
    'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'RECOVERY_CODE_USED', 'EXTERNAL_IDENTITY_LINKED',
    'WELCOME'));

INSERT INTO mail_templates (id, code, locale, subject, body_html, body_text, version, active, created_at, updated_at)
SELECT gen_random_uuid(), t.code, t.locale, t.subject, t.body_html, t.body_text, 2, true, now(), now()
FROM (VALUES
${valuesOf(welcome)}
) AS t(code, locale, subject, body_html, body_text);
`;
  return { [V84_FILE]: v84, [V85_FILE]: v85 };
}

// ---- Önizleme: gerçek kabuk + altbilgi (TemplateRenderer.FOOTERS ile aynı metin; orada değişirse burayı da güncelleyin).
const SAMPLE = { code: "483920", link: "https://ltz.example/yasal/sifirla?t=abc123", validityMinutes: "15", occurredAt: "09.11.2026 15:30 UTC" };
const FOOTER = {
  tr: ["Oyunları keşfet, oyuncularla bağlantı kur, deneyimlerini paylaş.", "Bu e-postayı LTZ hesabınla ilgili bir işlem nedeniyle aldın. Otomatik gönderilmiştir; lütfen yanıtlama.", "Gizlilik Politikası, KVKK Aydınlatma Metni ve Kullanım Koşulları LTZ sitesinde yer alır.", "Tüm hakları saklıdır."],
  en: ["Discover games, connect with players, share your experiences.", "You received this email because of an action on your LTZ account. It was sent automatically; please do not reply.", "The Privacy Policy, KVKK Privacy Notice and Terms of Use are available on the LTZ website.", "All rights reserved."]
};

export function renderPreview(row, layout) {
  const fill = (text) => text.replace(/\{\{(\w+)\}\}/g, (_, key) => SAMPLE[key] ?? "");
  const lines = FOOTER[row.locale];
  const footer = `<p class="ft-brand" style="margin:0 0 6px 0;font-family:${DISPLAY};font-size:13px;font-weight:700;letter-spacing:1px;color:#f1f1f1">LobbyTwoZero</p>` +
    lines.slice(0, 3).map((line) => `<p style="margin:0 0 10px 0">${line}</p>`).join("") +
    `<p style="margin:0">© ${new Date().getFullYear()} LobbyTwoZero. ${lines[3]}</p>`;
  return layout.replace("{{lang}}", row.locale).replace("{{title}}", row.subject).replace("{{content}}", fill(row.html))
    .replace("{{footer}}", footer).replace("cid:ltz-logo", `file:///${join(RESOURCES, "mail/ltz-logo.png").replaceAll("\\", "/")}`);
}

function main(argv) {
  const [command, directory] = argv;
  const migrations = buildMigrations();
  if (command === "check") {
    const stale = Object.entries(migrations).filter(([file, sql]) => readFileSync(join(MIGRATIONS, file), "utf8") !== sql);
    if (stale.length) {
      console.error(`Üreticiyle uyuşmayan migration: ${stale.map(([file]) => file).join(", ")}`);
      process.exit(1);
    }
    console.log("V84 ve V85 üreticiyle aynı.");
  } else if (command === "write" && directory) {
    mkdirSync(directory, { recursive: true });
    for (const [file, sql] of Object.entries(migrations)) writeFileSync(join(directory, file), sql, "utf8");
    console.log(`${Object.keys(migrations).length} migration yazıldı -> ${resolve(directory)}`);
  } else if (command === "preview" && directory) {
    const layout = readFileSync(join(RESOURCES, "mail/layout.html"), "utf8");
    mkdirSync(directory, { recursive: true });
    const rows = buildRows();
    for (const row of rows) writeFileSync(join(directory, `${row.code}.${row.locale}.html`), renderPreview(row, layout), "utf8");
    console.log(`${rows.length} önizleme yazıldı -> ${resolve(directory)}`);
  } else {
    console.error("Kullanım: node ops/mail-templates.mjs check | preview <klasör> | write <klasör>");
    process.exit(2);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main(process.argv.slice(2));
