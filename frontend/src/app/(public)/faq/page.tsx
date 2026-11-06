import { getTranslations } from "next-intl/server";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("faq");

const items = ["what", "ready", "hardware", "data"] as const;

export default async function FaqPage() {
  const pages = await getTranslations("Pages");
  const t = await getTranslations("Faq");

  return (
    <div className="mx-auto w-full max-w-5xl">
      <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
        {pages("faq.title")}
      </h1>
      <p className="mt-4 max-w-prose text-lg text-text-secondary">{t("intro")}</p>

      <dl className="mt-8 max-w-prose divide-y divide-border border-y border-border">
        {items.map((id) => (
          <div key={id} className="py-5">
            <dt className="font-display text-base font-semibold text-text-primary">
              {t(`items.${id}.q`)}
            </dt>
            <dd className="mt-2 text-text-secondary">{t(`items.${id}.a`)}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
