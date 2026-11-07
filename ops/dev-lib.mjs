// dev.mjs için saf yardımcılar (terminal, dosya ve Docker yok; test edilebilir).

export const KNOWN_FLAGS = new Set(["--no-mail", "--no-frontend", "--no-build", "--no-services", "--skip-keys", "--dry-run", "--all", "--force"]);
/** Varsayılan (çekirdek) servisler: giriş, kayıt, profil ve e-posta için yeterli olan en küçük küme. */
export const CORE_SERVICES = ["postgres", "mailpit", "mail-service", "auth-service", "user-service", "api-gateway"];
/** Java olmayan servisler (bellek tahmininde ayrı sayılır). */
const LIGHT_MB = { postgres: 150, mailpit: 30 };
/** Bir Spring Boot servisi boştayken yaklaşık bu kadar bellek tutar (ölçüm: 6 servis ≈ 1,9 GB). */
export const MB_PER_JVM_SERVICE = 550;
export const COMMANDS = new Set(["start", "stop", "reset", "status", "mail"]);

/**
 * `node ops/dev.mjs [komut] [--bayrak …] [--with=servis1,servis2]`. Komut verilmezse `start`. `mail` komutunun kalan argümanları
 * aynen iletilir. `--with=` çekirdeğe ek servisler ister (virgülle).
 */
export function parseArgs(argv) {
  const flags = new Set();
  const positional = [];
  const withServices = [];
  for (const arg of argv) {
    if (arg.startsWith("--with=")) {
      withServices.push(...arg.slice("--with=".length).split(",").map((name) => name.trim()).filter(Boolean));
    } else if (arg.startsWith("--")) {
      flags.add(arg);
    } else {
      positional.push(arg);
    }
  }
  const command = positional[0] ?? "start";
  return { command, rest: positional.slice(1), flags, withServices };
}

export function validateArgs({ command, flags, withServices = [] }) {
  const badNames = withServices.filter((name) => !/^[a-z][a-z0-9-]{1,40}$/.test(name));
  if (badNames.length > 0) throw new Error(`Geçersiz servis adı: ${badNames.join(", ")}`);
  const unknownFlags = [...flags].filter((flag) => !KNOWN_FLAGS.has(flag));
  if (unknownFlags.length > 0) throw new Error(`Bilinmeyen seçenek: ${unknownFlags.join(", ")}`);
  if (!COMMANDS.has(command)) throw new Error(`Bilinmeyen komut: ${command} (start | stop | reset | status | mail)`);
}

/**
 * Gerçek e-posta (Gmail) adımı kararı:
 *  - "skip"     : --no-mail verilmiş
 *  - "already"  : gerçek SMTP zaten açık ve şifre dolu, dokunulmaz
 *  - "env"      : DEV_MAIL_PASSWORD verilmiş (etkileşimsiz kurulum; adres sabit, DEV_MAIL_USER ile değiştirilebilir)
 *  - "ask"      : etkileşimli terminalde YALNIZ uygulama şifresi sorulur (boş bırakılırsa Mailpit)
 *  - "mailpit"  : sorulamaz (etkileşimsiz) ve değişken yok: Mailpit'te kalınır, kurulum durmaz
 */
export function decideMailStep({ skip, real, passwordSet, interactive, envPassword }) {
  if (skip) return "skip";
  if (real && passwordSet) return "already";
  if (envPassword !== undefined) return "env";
  if (interactive) return "ask";
  return "mailpit";
}

/** Bellek tahmini (MB): Java servisleri × ortalama + Postgres/Mailpit. */
export function estimateMemoryMb(services) {
  return [...new Set(services)].reduce((sum, name) => sum + (LIGHT_MB[name] ?? MB_PER_JVM_SERVICE), 0);
}

/** Docker belleği bu servis kümesine yeter mi? İşletim ve geçici yük için %15 pay bırakılır. */
export function memoryVerdict({ services, dockerMemBytes }) {
  const needMb = estimateMemoryMb(services);
  const haveMb = Math.floor(dockerMemBytes / 1024 / 1024);
  return { needMb, haveMb, ok: needMb <= haveMb * 0.85 };
}

/** `npm ci` gerekir mi? node_modules yoksa ya da kilit dosyası ondan yeniyse. Zamanlar ms (yoksa `null`). */
export function needsInstall({ modulesTime, lockTime }) {
  if (modulesTime === null) return true;
  return lockTime !== null && lockTime > modulesTime;
}

/** Kurulum sonu özeti (hesap listesi parolayı göstermez). */
export function summaryLines({ domain, realMail, frontendRunning }) {
  const accounts = ["user", "creator", "developer", "moderator", "admin"].map((name) => `${name}@${domain}`).join(", ");
  return [
    "✓ Hazır",
    `  Giriş:     http://localhost:5001/tr/giris${frontendRunning ? "" : "   (frontend: cd frontend && npx next dev -H localhost -p 5001)"}`,
    `  Hesaplar:  ${accounts}`,
    "  Parola:    .env.dev içindeki DEV_ACCOUNTS_PASSWORD",
    realMail ? "  E-posta:   GERÇEK (Gmail SMTP). Kapatmak için: node ops/dev.mjs mail off" : "  E-posta:   Mailpit http://localhost:8025 (gerçek mail gitmez). Gerçek için: node ops/dev.mjs mail on",
    "  Durdurmak: Ctrl+C (frontend), servisler için: node ops/dev.mjs stop"
  ];
}
