import { locales, type Locale } from "./config";
import { pathnames } from "./routing";

type LocalizedEntry = readonly [internal: string, byLocale: Readonly<Record<Locale, string>>];

const localizedEntries: readonly LocalizedEntry[] = Object.entries(pathnames).flatMap(
  ([internal, value]): LocalizedEntry[] =>
    typeof value === "string" ? [] : [[internal, value as Record<Locale, string>]]
);

function stripTrailingSlash(path: string): string {
  return path.length > 1 && path.endsWith("/") ? path.slice(0, -1) : path;
}

/**
 * Bir adresin o dildeki kanonik karşılığını döner; zaten kanonikse veya bilinmiyorsa `null`.
 *
 * İçeride dahili klasör adları (`/about`) ve diğer dilin slug'ı (`/en/hakkimizda`) da çözülebilir;
 * bunlar aynı sayfanın ikinci adresi olur (yinelenen içerik). Burada tek kanonik adrese yönlendirilir.
 * `locale` verilmemişse (önek yok) çağıran taraf varsayılan veya çerezdeki dili geçirir.
 */
export function canonicalPathFor(pathname: string, locale: Locale): string | null {
  const rest = stripTrailingSlash(pathname) || "/";

  for (const [, byLocale] of localizedEntries) {
    const target = byLocale[locale];
    if (rest === target) {
      return null;
    }
  }

  for (const [internal, byLocale] of localizedEntries) {
    const known = new Set<string>([internal, ...locales.map((code) => byLocale[code])]);
    if (known.has(rest)) {
      return byLocale[locale];
    }
  }

  return null;
}
