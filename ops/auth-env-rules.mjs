// Auth ENV kuralları (saf fonksiyonlar; dosya/ağ/süreç yok). İki betik kullanır:
//   ops/generate-auth-dev-keys.mjs : eksikleri tamamlar ve sonunda bu kuralları çalıştırıp raporlar
//   ops/auth-env-check.mjs         : yalnız okuyup raporlar (CI veya el ile)
// Kurallar, Auth'un AÇILIŞTA yaptığı doğrulamaların aynısıdır; anahtar/parola DEĞERLERİ hiçbir çıktıya girmez.

const BASE64 = /^[A-Za-z0-9+/]+={0,2}$/;
const MAX_KEY_VERSION = 32767;

/** Satırları ad -> değer sözlüğüne çevirir (tırnaklar atılır). */
export function parse(body) {
  const out = {};
  for (const line of body.split(/\r?\n/)) {
    const found = line.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (found) out[found[1]] = found[2].trim().replace(/^(["'])(.*)\1$/, "$2");
  }
  return out;
}

/** Base64 değerin bayt uzunluğu; geçersiz biçimde -1. */
export function byteLength(candidate) {
  if (!candidate || candidate.length % 4 !== 0 || !BASE64.test(candidate)) return -1;
  return Buffer.from(candidate, "base64").length;
}

const list = (text) => (text ?? "").split(",").map((part) => part.trim()).filter(Boolean);

/** Tek dosyanın bulguları: { level: FAIL|WARN|INFO|OK, name, problem, fix? }. */
export function check(settings) {
  const findings = [];
  const add = (level, name, problem, fix) => findings.push({ level, name, problem, fix });
  const regenerate = "node ops/generate-auth-dev-keys.mjs ile üretin";

  const hmac = settings.AUTH_OTP_HMAC_KEY ?? "";
  if (hmac === "") add("FAIL", "AUTH_OTP_HMAC_KEY", "boş; Auth açılmaz", regenerate);
  else if (byteLength(hmac) < 32) add("FAIL", "AUTH_OTP_HMAC_KEY", "Base64 değil veya 32 bayttan kısa; Auth açılmaz", regenerate);
  else add("OK", "AUTH_OTP_HMAC_KEY", "geçerli");

  const totp = settings.AUTH_TOTP_ENCRYPTION_KEY ?? "";
  if (totp === "") add("FAIL", "AUTH_TOTP_ENCRYPTION_KEY", "boş; Auth açılmaz", regenerate);
  else if (byteLength(totp) !== 32) add("FAIL", "AUTH_TOTP_ENCRYPTION_KEY", "Base64 değil veya tam 32 bayt değil; Auth açılmaz", regenerate);
  else add("OK", "AUTH_TOTP_ENCRYPTION_KEY", "geçerli");

  const jwt = settings.AUTH_JWT_PRIVATE_KEY ?? "";
  if (jwt === "") add("FAIL", "AUTH_JWT_PRIVATE_KEY", "boş; Auth açılmaz", regenerate);
  else if (!BASE64.test(jwt)) add("FAIL", "AUTH_JWT_PRIVATE_KEY", "Base64 (PKCS#8 DER) biçiminde değil; Auth açılmaz", regenerate);
  else add("OK", "AUTH_JWT_PRIVATE_KEY", "dolu");

  // Mail Service: kuyruktaki kodu şifreleyen AES-256-GCM anahtarı; boş ya da geçersizse Mail Service açılmaz (Auth etkilenmez).
  const mailKey = settings.MAIL_PAYLOAD_ENCRYPTION_KEY ?? "";
  if (mailKey === "") add("FAIL", "MAIL_PAYLOAD_ENCRYPTION_KEY", "boş; Mail Service açılmaz", regenerate);
  else if (byteLength(mailKey) !== 32) add("FAIL", "MAIL_PAYLOAD_ENCRYPTION_KEY", "Base64 değil veya tam 32 bayt değil; Mail Service açılmaz", regenerate);
  else add("OK", "MAIL_PAYLOAD_ENCRYPTION_KEY", "geçerli");

  // Rotasyon (S9) — hepsi isteğe bağlı; ayarlıysa Auth açılışta biçimini doğrular.
  const versionText = settings.AUTH_TOTP_ENCRYPTION_KEY_VERSION ?? "";
  let version = 1;
  if (versionText !== "") {
    version = Number(versionText);
    if (!Number.isInteger(version) || version < 1 || version > MAX_KEY_VERSION) {
      add("FAIL", "AUTH_TOTP_ENCRYPTION_KEY_VERSION", `1 ile ${MAX_KEY_VERSION} arasında tam sayı olmalı; Auth açılmaz`,
        "boş bırakın (varsayılan 1) ya da düzeltin");
      version = 1;
    }
  }
  const previous = settings.AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS ?? "";
  if (previous !== "") {
    const seen = new Set();
    let malformed = false;
    let highest = 0;
    for (const entry of list(previous)) {
      const cut = entry.indexOf("=");
      const entryVersion = cut > 0 ? Number(entry.slice(0, cut).trim()) : Number.NaN;
      const entryKey = cut > 0 ? entry.slice(cut + 1).trim() : "";
      if (!Number.isInteger(entryVersion) || entryVersion < 1 || entryVersion > MAX_KEY_VERSION
          || seen.has(entryVersion) || entryVersion === version || byteLength(entryKey) !== 32) {
        malformed = true;
        break;
      }
      seen.add(entryVersion);
      highest = Math.max(highest, entryVersion);
    }
    if (malformed) {
      add("FAIL", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS",
        "'sürüm=Base64,...' biçiminde değil; sürümler tekil, güncel sürümden farklı ve anahtarlar 32 bayt olmalı; Auth açılmaz",
        "node ops/generate-auth-dev-keys.mjs --rotate-totp kullanın ya da listeyi düzeltin");
    } else {
      add("INFO", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS", `rotasyon açık (${seen.size} eski sürüm)`);
      if (highest > version) {
        add("WARN", "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "güncel sürüm eski sürümlerden küçük; yeni bir anahtar sürümü beklenir",
          "sürümü artırın (--rotate-totp bunu kendisi yapar)");
      }
    }
  } else if (version !== 1) {
    add("WARN", "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "1'den farklı ama eski anahtar listesi boş; eski sürümdeki TOTP kayıtları çözülemez",
      "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS listesini ekleyin");
  }
  const hmacPrevious = settings.AUTH_OTP_HMAC_PREVIOUS_KEY ?? "";
  if (hmacPrevious !== "") {
    if (byteLength(hmacPrevious) < 32) {
      add("FAIL", "AUTH_OTP_HMAC_PREVIOUS_KEY", "Base64 değil veya 32 bayttan kısa; Auth açılmaz", "düzeltin ya da boş bırakın");
    } else if (hmacPrevious === hmac) {
      add("WARN", "AUTH_OTP_HMAC_PREVIOUS_KEY", "güncel anahtarla aynı; rotasyon etkisiz", "node ops/generate-auth-dev-keys.mjs --rotate-hmac");
    } else {
      add("INFO", "AUTH_OTP_HMAC_PREVIOUS_KEY", "rotasyon açık");
    }
  }

  // Ortama göre kolaylıklar ve tuzaklar.
  const isProd = list(settings.SPRING_PROFILES_ACTIVE).includes("prod");
  const adapter = settings.AUTH_MAIL_ADAPTER ?? "";
  if (isProd && adapter !== "" && adapter !== "mail-service") {
    add("FAIL", "AUTH_MAIL_ADAPTER", "prod profili yalnız 'mail-service' adaptörüyle açılır; Auth açılmaz", "prod'da mail-service seçin");
  }
  if (!isProd) {
    if (!list(settings.COMPOSE_PROFILES).includes("dev-mail")) {
      add("WARN", "COMPOSE_PROFILES", "dev-mail yok; doğrulama/parola kodu e-postaları Mailpit'e düşmez", "COMPOSE_PROFILES=dev-mail ekleyin");
    }
    if (!list(settings.AUTH_EVENT_TARGETS).includes("user")) {
      add("WARN", "AUTH_EVENT_TARGETS", "'user' yok; Auth olayları User Service'e gitmez", "değeri 'user' yapın veya listeye ekleyin");
    }
  }
  return findings;
}

/** Dosyalar arası tutarlılık: aynı anahtar .env ve .env.dev'de aynı olmalı (ops/env durumu da bunu bekler). */
export function consistency(parsedList) {
  if (parsedList.length < 2) return [];
  const names = ["AUTH_OTP_HMAC_KEY", "AUTH_TOTP_ENCRYPTION_KEY", "AUTH_JWT_PRIVATE_KEY", "AUTH_OTP_HMAC_PREVIOUS_KEY",
    "AUTH_TOTP_ENCRYPTION_KEY_VERSION", "AUTH_TOTP_ENCRYPTION_PREVIOUS_KEYS", "MAIL_PAYLOAD_ENCRYPTION_KEY"];
  return names.filter((name) => new Set(parsedList.map((entry) => entry[name] ?? "")).size > 1);
}

const rank = { FAIL: 0, WARN: 1, INFO: 2, OK: 3 };

/**
 * entries: [{ shown: "görünen dosya adı", body: "dosya metni" }] -> { lines: ["..."], failures: sayı }.
 * Dönen satırlar yalnız ayar adı ve sabit açıklama içerir; hiçbir ayar değeri yoktur.
 */
export function buildReport(entries) {
  const lines = [];
  let failures = 0;
  let authFailures = 0;
  const parsedList = entries.map((entry) => parse(entry.body));
  entries.forEach((entry, index) => {
    lines.push(`== ${entry.shown}`);
    for (const item of check(parsedList[index]).sort((a, b) => rank[a.level] - rank[b.level])) {
      if (item.level === "FAIL") {
        failures++;
        if (!item.name.startsWith("MAIL_")) authFailures++;
      }
      lines.push(`  [${item.level.padEnd(4)}] ${item.name}: ${item.problem}${item.fix ? `  -> ${item.fix}` : ""}`);
    }
  });
  const differing = consistency(parsedList);
  if (differing.length) {
    lines.push("== dosyalar arası");
    for (const name of differing) {
      lines.push(`  [WARN] ${name}: dosyalarda farklı; ops/env durumu uyuşmazlık gösterir  -> aynı değeri kullanın`);
    }
  }
  // Yalnız Mail ayarı hatalıysa Auth açılır; mesaj hangi servisin açılmayacağını doğru söylemeli.
  const subject = authFailures ? "Auth" : "Mail Service";
  lines.push(failures ? `\n${failures} hata: ${subject} bu ayarlarla AÇILMAZ.` : "\nHata yok: Auth ayarları açılış doğrulamasından geçer.");
  return { lines, failures };
}
