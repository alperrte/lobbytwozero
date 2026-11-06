import { getTranslations } from "next-intl/server";
import { Notice } from "@/shared/components/ui/notice";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("contact");

/**
 * İletişim sayfası iskeleti. Resmî destek e-postası henüz belirlenmediği için adres, mailto
 * bağlantısı ya da form yoktur; durum dürüstçe yazılır. Sayfa `noindex` yayınlanır.
 */
export default async function ContactPage() {
  const pages = await getTranslations("Pages");
  const t = await getTranslations("Contact");

  return (
    <div className="mx-auto w-full max-w-5xl">
      <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
        {pages("contact.title")}
      </h1>
      <p className="mt-4 max-w-prose text-lg text-text-secondary">{t("intro")}</p>

      <div className="mt-8">
        <Notice title={t("bodyTitle")}>
          <p>{t("body")}</p>
        </Notice>
      </div>
    </div>
  );
}
