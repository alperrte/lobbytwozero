/**
 * Frontend güvenlik başlıkları ve API adresi denetimi (docs/SECURITY.md §11). Saf fonksiyonlar: `next.config.ts` çağırır,
 * testler doğrudan sınar. `next.config.ts` bu dosyayı alias olmadan, göreli yolla import eder.
 *
 * Tam CSP bu sürümde YOK: tema başlatma betiği satır içi çalışıyor ve gerçek render ile nonce/hash planı test
 * edilmeden CSP yazmak siteyi bozar. Burada yalnız zararsız ve yüksek değerli başlıklar var; en önemlisi
 * `frame-ancestors 'none'` (giriş sayfasının başka sitede iframe'e konup tıklatılmasını, yani clickjacking'i engeller).
 * HSTS'nin asıl yeri TLS'i sonlandıran ters vekildir; uygulama yalnız üretim derlemesinde ek olarak gönderir.
 */
export type SecurityHeader = { key: string; value: string };

export function securityHeaders(isProduction: boolean): SecurityHeader[] {
  const headers: SecurityHeader[] = [
    { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
    { key: "X-Frame-Options", value: "DENY" },
    { key: "X-Content-Type-Options", value: "nosniff" },
    { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
    {
      key: "Permissions-Policy",
      value: "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()"
    }
  ];
  if (isProduction) {
    // `includeSubDomains`/`preload` bilerek yok: alt alan adlarının TLS durumu bu uygulamanın kararı değildir.
    headers.push({ key: "Strict-Transport-Security", value: "max-age=31536000" });
  }
  return headers;
}

const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

/**
 * `NEXT_PUBLIC_API_BASE_URL` denetimi (derleme zamanı). Boş/tanımsız değer geçerlidir (göreli yol: frontend ve
 * `/api/v1` aynı host altında). Üretimde `http` yalnız yerel adreslerde kabul edilir; başka bir `http` adresi, oturum
 * çerezlerinin ve parolaların düz metin gitmesi demektir. Geçersiz değer derlemeyi durdurur.
 */
export function assertSafeApiBaseUrl(value: string | undefined, isProduction: boolean): void {
  if (value === undefined || value.trim() === "") {
    return;
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error("NEXT_PUBLIC_API_BASE_URL geçerli bir adres değil (boş bırakılırsa göreli yol kullanılır).");
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") {
    throw new Error("NEXT_PUBLIC_API_BASE_URL yalnız http(s) olabilir.");
  }
  if (isProduction && url.protocol === "http:" && !LOOPBACK_HOSTS.has(url.hostname)) {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL üretimde https olmalı (yalnız localhost http kabul edilir). Frontend ve /api/v1 aynı host altındaysa boş bırakın."
    );
  }
}
