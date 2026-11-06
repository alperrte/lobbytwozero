import type { MetadataRoute } from "next";
import { publicRoutes } from "@/shared/config/public-routes";
import { getSiteUrl, isIndexable } from "@/shared/config/site";
import { getPathname } from "@/shared/lib/i18n/navigation";
import { routing } from "@/shared/lib/i18n/routing";

export default function sitemap(): MetadataRoute.Sitemap {
  const siteUrl = getSiteUrl();

  // Mutlak URL gerekir; doğrulanmış site kökü yoksa domain uydurmak yerine boş döner.
  if (!isIndexable(siteUrl) || !siteUrl) {
    return [];
  }

  const absolute = (path: string) => new URL(path, siteUrl).toString();

  // Her dil için ayrı kayıt; hepsi karşılıklı hreflang alternatifleri taşır (x-default: varsayılan dil).
  return publicRoutes.flatMap((route) => {
    const languages = Object.fromEntries(
      routing.locales.map((locale) => [
        locale,
        absolute(getPathname({ href: route.path, locale }))
      ])
    );
    const alternates = {
      languages: {
        ...languages,
        "x-default": languages[routing.defaultLocale]
      }
    };

    return routing.locales.map((locale) => ({
      url: languages[locale],
      alternates,
      changeFrequency: route.changeFrequency,
      priority: route.priority
    }));
  });
}
