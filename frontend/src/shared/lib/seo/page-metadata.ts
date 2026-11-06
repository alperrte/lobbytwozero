import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { findPageById, type PageId } from "@/shared/config/pages";
import { formatPageTitle, getSiteUrl } from "@/shared/config/site";

/**
 * Sayfa başına metadata. Başlık/açıklama `Pages.<id>.*` mesajlarından gelir.
 * - Canonical yalnız doğrulanmış bir site kökü varsa yazılır (domain uydurulmaz).
 * - `indexable: false` sayfalar (iletişim, yasal metinler) `noindex` yayınlar.
 */
export async function buildPageMetadata(id: PageId): Promise<Metadata> {
  const t = await getTranslations("Pages");
  const page = findPageById(id);
  const title = t(`${id}.title`);
  const description = t(`${id}.description`);

  return {
    title,
    description,
    ...(getSiteUrl() ? { alternates: { canonical: page.href } } : {}),
    ...(page.indexable ? {} : { robots: { index: false, follow: true } }),
    // Sekme başlığı kök layout şablonundan ("LTZ | %s") gelir; paylaşım kartları aynı biçimi kullanır.
    openGraph: { title: formatPageTitle(title), description },
    twitter: { title: formatPageTitle(title), description }
  };
}
