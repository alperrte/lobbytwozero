import { getTranslations } from "next-intl/server";
import { ContactForm } from "@/shared/components/layout/contact-form";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("contact");

/**
 * İletişim: yalnız frontend DEMO formu. Backend, e-posta altyapısı veya uç nokta yoktur; resmî e-posta,
 * telefon ya da adres uydurulmaz. Gönderim mantığı `shared/lib/contact/contact-submit.ts` adaptörünün arkasındadır.
 * Sayfa `noindex` yayınlanır.
 */
export default async function ContactPage() {
  const t = await getTranslations("Contact");

  return (
    <PageContainer>
      <PageHeader title={t("title")} description={t("description")} />

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] xl:grid-cols-[minmax(0,1fr)_22rem]">
        <ContactForm />

        <aside
          aria-labelledby="contact-side-title"
          className="rounded-panel border border-border bg-surface p-5 sm:p-6"
        >
          <h2 id="contact-side-title" className="font-display text-lg font-semibold text-text-primary">
            {t("side.title")}
          </h2>
          <p className="mt-3 text-sm leading-relaxed text-text-secondary">{t("side.body")}</p>
        </aside>
      </div>
    </PageContainer>
  );
}
