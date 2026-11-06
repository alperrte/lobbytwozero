export const SITE_NAME = "LobbyTwoZero";

/**
 * Meta theme-color değerleri globals.css içindeki `--ltz-surface` tokenlarını yansıtır
 * (Dark ve Light). site.test.ts iki dosyanın eşitliğini doğrular.
 */
export const THEME_COLORS = {
  dark: "#141419",
  light: "#ffffff"
} as const;

const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1", "[::1]"]);

/**
 * Doğrulanmış site kökü (canonical, sitemap, robots için).
 * `NEXT_PUBLIC_SITE_URL` tanımlı değilse veya geçersizse `undefined` döner; domain uydurulmaz.
 * Yalnız https (veya yerel geliştirme için localhost) kabul edilir.
 */
export function getSiteUrl(
  raw: string | undefined = process.env.NEXT_PUBLIC_SITE_URL
): URL | undefined {
  const value = raw?.trim();
  if (!value) {
    return undefined;
  }

  try {
    const url = new URL(value);
    const isLocal = LOCAL_HOSTS.has(url.hostname);
    if (url.protocol !== "https:" && !(isLocal && url.protocol === "http:")) {
      return undefined;
    }
    return new URL(url.origin);
  } catch {
    return undefined;
  }
}

/**
 * Arama motorlarına açılma kararı. Yalnız production + doğrulanmış, yerel olmayan
 * bir site kökü varsa true; aksi halde dev/preview ortamları yanlışlıkla indekslenmez.
 */
export function isIndexable(
  siteUrl: URL | undefined = getSiteUrl(),
  nodeEnv: string | undefined = process.env.NODE_ENV
): boolean {
  return (
    nodeEnv === "production" &&
    siteUrl !== undefined &&
    !LOCAL_HOSTS.has(siteUrl.hostname)
  );
}
