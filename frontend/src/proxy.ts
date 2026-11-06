import createMiddleware from "next-intl/middleware";
import { NextResponse, type NextRequest } from "next/server";
import { canonicalPathFor } from "@/shared/lib/i18n/canonical-path";
import { defaultLocale, localeCookieName, locales, type Locale } from "@/shared/lib/i18n/config";
import { routing } from "@/shared/lib/i18n/routing";

const handleI18n = createMiddleware(routing);

function isLocale(value: string | undefined): value is Locale {
  return locales.some((locale) => locale === value);
}

/**
 * Tek i18n katmanı: dil öneki yoksa çerez / Accept-Language / varsayılan (tr) ile yönlendirir,
 * `/tr/hakkimizda` gibi yerelleştirilmiş adresleri dahili rotaya (`/tr/about`) yeniden yazar.
 * Ek olarak, aynı sayfanın ikinci adreslerini (`/tr/about`, `/en/hakkimizda`, öneksiz `/about`)
 * tek kanonik adrese 308 ile yönlendirir; böylece Türkçe sitede İngilizce slug yaşamaz.
 * `/api`, `/_next`, `/_vercel` ve uzantılı dosyalar (robots.txt, sitemap.xml, llms.txt, ikonlar) kapsam dışıdır.
 */
export default function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const [, first, ...tail] = pathname.split("/");

  let locale: Locale;
  let rest: string;
  if (isLocale(first)) {
    locale = first;
    rest = `/${tail.join("/")}`;
  } else {
    const cookie = request.cookies.get(localeCookieName)?.value;
    locale = isLocale(cookie) ? cookie : defaultLocale;
    rest = pathname;
  }

  const canonical = canonicalPathFor(rest, locale);
  if (canonical) {
    const url = request.nextUrl.clone();
    url.pathname = `/${locale}${canonical}`;
    url.search = search;
    return NextResponse.redirect(url, 308);
  }

  return handleI18n(request);
}

export const config = {
  matcher: ["/((?!api|_next|_vercel|.*\\..*).*)"]
};
