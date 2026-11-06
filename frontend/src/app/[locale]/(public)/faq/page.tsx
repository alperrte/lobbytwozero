import { getTranslations } from "next-intl/server";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { Accordion } from "@/shared/components/ui/accordion";
import { faqAnchorId, faqCategories, faqItemsOf } from "@/shared/config/faq";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("faq");

/**
 * SSS: altı kategori, her kategoride kapalı başlayan akordeon. İçerik `shared/config/faq.ts` yapısı
 * ve `Faq` çevirilerinden gelir; backend yoktur. Soru alanı 896px ile sınırlı ve ortalanmıştır.
 */
export default async function FaqPage() {
  const t = await getTranslations("Faq");

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-[56rem]">
        <PageHeader title={t("heading")} description={t("description")}>
          <nav aria-label={t("categoriesLabel")}>
            <ul className="flex flex-wrap gap-2">
              {faqCategories.map((category) => (
                <li key={category.id}>
                  <a
                    href={`#${faqAnchorId(category.id)}`}
                    className="inline-flex items-center rounded-control border border-border bg-surface px-3 py-1.5 text-sm text-text-secondary transition-colors duration-150 hover:bg-surface-elevated hover:text-text-primary"
                  >
                    {t(`categories.${category.id}`)}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
        </PageHeader>

        <div className="space-y-10">
          {faqCategories.map((category) => {
            const headingId = `${faqAnchorId(category.id)}-title`;

            return (
              <section
                key={category.id}
                id={faqAnchorId(category.id)}
                aria-labelledby={headingId}
                className="scroll-mt-24"
              >
                <h2
                  id={headingId}
                  className="mb-3 font-display text-xl font-semibold text-text-primary"
                >
                  {t(`categories.${category.id}`)}
                </h2>
                <Accordion
                  className="overflow-hidden rounded-panel border border-border bg-surface"
                  items={faqItemsOf(category.id).map((item) => ({
                    id: item.id,
                    title: t(`items.${item.id}.title`),
                    content: <p>{t(`items.${item.id}.answer`)}</p>
                  }))}
                />
              </section>
            );
          })}
        </div>
      </div>
    </PageContainer>
  );
}
