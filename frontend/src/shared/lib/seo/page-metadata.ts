import type { Metadata } from "next";
import { getLocale, getTranslations } from "next-intl/server";
import { findPageById, type PageId } from "@/shared/config/pages";
import { formatPageTitle, getSiteUrl } from "@/shared/config/site";
import type { Locale } from "@/shared/lib/i18n/config";
import { getPathname } from "@/shared/lib/i18n/navigation";
import { routing } from "@/shared/lib/i18n/routing";

/**
 * Sayfa başına metadata. Başlık/açıklama `Pages.<id>.*` mesajlarından gelir.
 * - Canonical ve hreflang yalnız doğrulanmış bir site kökü varsa yazılır (domain uydurulmaz).
 *   Adresler dile göre üretilir: `/tr/hakkimizda` ↔ `/en/about`; `x-default` varsayılan dile (tr) gider.
 * - `indexable: false` sayfalar (iletişim, yasal metinler, ayarlar) `noindex` yayınlar.
 */
export async function buildPageMetadata(id: PageId): Promise<Metadata> {
  const t = await getTranslations("Pages");
  const locale = (await getLocale()) as Locale;
  const page = findPageById(id);
  const title = t(`${id}.title`);
  const description = t(`${id}.description`);
  const pathFor = (target: Locale) => getPathname({ href: page.href, locale: target });

  return {
    title,
    description,
    ...(getSiteUrl()
      ? {
          alternates: {
            canonical: pathFor(locale),
            languages: {
              ...Object.fromEntries(routing.locales.map((target) => [target, pathFor(target)])),
              "x-default": pathFor(routing.defaultLocale)
            }
          }
        }
      : {}),
    ...(page.indexable ? {} : { robots: { index: false, follow: true } }),
    // Sekme başlığı kök layout şablonundan ("LTZ | %s") gelir; paylaşım kartları aynı biçimi kullanır.
    openGraph: { title: formatPageTitle(title), description },
    twitter: { title: formatPageTitle(title), description }
  };
}
