import assert from "node:assert/strict";
import test from "node:test";
import {
  applyEnvChanges,
  describeSmtp,
  normalizeAppPassword,
  smtpOffSettings,
  smtpOnSettings,
  validateGmailAddress
} from "./dev-mail-lib.mjs";

test("açma ayarları: Gmail sabitleri otomatik, yalnız adres ve şifre kişiye özel", () => {
  const settings = smtpOnSettings("ada@gmail.com", "abcdefghijklmnop");
  assert.equal(settings.MAIL_SMTP_HOST, "smtp.gmail.com");
  assert.equal(settings.MAIL_SMTP_PORT, "587");
  assert.equal(settings.MAIL_SMTP_SECURITY, "STARTTLS");
  assert.equal(settings.MAIL_SMTP_USERNAME, "ada@gmail.com");
  assert.equal(settings.MAIL_FROM_ADDRESS, "ada@gmail.com");
  assert.equal(settings.MAIL_SMTP_PASSWORD, "abcdefghijklmnop");
  assert.equal(settings.AUTH_MAIL_ADAPTER, "mail-service");
});

test("kapatma ayarları: SMTP boşalır, adaptör Mailpit'e döner", () => {
  const settings = smtpOffSettings();
  for (const [key, value] of Object.entries(settings)) {
    if (key !== "AUTH_MAIL_ADAPTER") assert.equal(value, "", key);
  }
  assert.equal(settings.AUTH_MAIL_ADAPTER, "dev-smtp");
});

test("Gmail adresi doğrulaması", () => {
  assert.equal(validateGmailAddress(" Ada.Test+x@Gmail.com "), "ada.test+x@gmail.com");
  for (const bad of ["", "ada@example.com", "ada@gmail.com.evil.test", "@gmail.com", "a b@gmail.com", undefined]) {
    assert.throws(() => validateGmailAddress(bad), /Gmail adresi/, String(bad));
  }
});

test("uygulama şifresi: boşluklar atılır, 16 harf şart; hata değeri sızdırmaz", () => {
  assert.equal(normalizeAppPassword("abcd efgh ijkl mnop"), "abcdefghijklmnop");
  assert.equal(normalizeAppPassword(" ABCD EFGH IJKL MNOP "), "abcdefghijklmnop");
  for (const bad of ["", "kisa", "abcd efgh ijkl mnop q", "abcd-efgh-ijkl-mnop", "1234567890123456"]) {
    assert.throws(
      () => normalizeAppPassword(bad),
      (error) => /16 harf/.test(error.message) && !error.message.includes(bad.trim() || "\u0000")
    );
  }
});

test("env güncelleme: var olan aktif satır değişir, yorum satırına dokunulmaz, eksik anahtar eklenir, diğer satırlar korunur", () => {
  const input = ["KEEP=1", "# MAIL_SMTP_HOST=eski", "MAIL_SMTP_HOST=", "DB_PASSWORD=benim", "MAIL_SMTP_PORT=25"].join("\n") + "\n";
  const output = applyEnvChanges(input, { MAIL_SMTP_HOST: "smtp.gmail.com", MAIL_SMTP_PORT: "587", AUTH_MAIL_ADAPTER: "mail-service" });
  assert.equal(
    output,
    ["KEEP=1", "# MAIL_SMTP_HOST=eski", "MAIL_SMTP_HOST=smtp.gmail.com", "DB_PASSWORD=benim", "MAIL_SMTP_PORT=587", "AUTH_MAIL_ADAPTER=mail-service"].join("\n") + "\n"
  );
});

test("env güncelleme: CRLF korunur, boş dosya ve sonu satır sonsuz dosya çalışır", () => {
  assert.equal(applyEnvChanges("A=1\r\nB=2\r\n", { B: "3" }), "A=1\r\nB=3\r\n");
  assert.equal(applyEnvChanges("", { A: "1" }), "A=1\n");
  assert.equal(applyEnvChanges("A=1", { A: "2" }), "A=2\n");
});

test("env güncelleme: değer satır sonu içeremez (satır enjeksiyonu engeli)", () => {
  assert.throws(() => applyEnvChanges("A=1\n", { A: "x\nEVIL=1" }), /satır sonu/);
});

test("aç sonra kapat: ilk haline anlamlı olarak döner ve tekrar çalıştırmak aynı sonucu verir", () => {
  const start = "KEEP=1\nMAIL_SMTP_HOST=\n";
  const on = applyEnvChanges(start, smtpOnSettings("ada@gmail.com", "abcdefghijklmnop"));
  assert.equal(applyEnvChanges(on, smtpOnSettings("ada@gmail.com", "abcdefghijklmnop")), on);
  const off = applyEnvChanges(on, smtpOffSettings());
  assert.match(off, /^MAIL_SMTP_HOST=$/m);
  assert.match(off, /^MAIL_SMTP_PASSWORD=$/m);
  assert.match(off, /^AUTH_MAIL_ADAPTER=dev-smtp$/m);
  assert.match(off, /^KEEP=1$/m);
});

test("durum özeti şifre ve adres göstermez", () => {
  const on = applyEnvChanges("", smtpOnSettings("ada@gmail.com", "abcdefghijklmnop"));
  const summary = describeSmtp(on);
  assert.deepEqual(summary, { real: true, host: "smtp.gmail.com", adapter: "mail-service", passwordSet: true });
  assert.ok(!JSON.stringify(summary).includes("abcdefghijklmnop"));
  assert.deepEqual(describeSmtp("MAIL_SMTP_HOST=\n"), { real: false, host: "", adapter: "dev-smtp (varsayılan)", passwordSet: false });
});
