/** CSRF çifti (çift gönderim): Auth `XSRF-TOKEN` çerezini yazar, durum değiştiren istek aynı değeri başlıkta gönderir. */
export const CSRF_PATH = "/api/v1/auth/csrf";
export const CSRF_HEADER = "X-XSRF-TOKEN";

/** Güvenli bağlamda `__Host-` önekli, yerel http'de öneksiz; ikisi de okunabilir CSRF çerezidir. */
const CSRF_COOKIE_NAMES = ["__Host-XSRF-TOKEN", "XSRF-TOKEN"] as const;

export function readCsrfCookie(): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  for (const name of CSRF_COOKIE_NAMES) {
    for (const part of document.cookie.split(";")) {
      const [key, ...rest] = part.trim().split("=");
      if (key === name && rest.length > 0) {
        return decodeURIComponent(rest.join("="));
      }
    }
  }
  return null;
}
