import { getTranslations } from "next-intl/server";
import { Notice } from "@/shared/components/ui/notice";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("about");

const aims = ["discover", "social", "community", "performance"] as const;

export default async function AboutPage() {
  const pages = await getTranslations("Pages");
  const t = await getTranslations("About");

  return (
    <div className="w-full">
      <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
        {pages("about.title")}
      </h1>
      <p className="mt-4 max-w-prose text-lg text-text-secondary">{t("intro")}</p>

      <div className="mt-8">
        <Notice title={t("statusTitle")}>
          <p>{t("statusBody")}</p>
        </Notice>
      </div>

      <section aria-labelledby="about-aims" className="mt-12">
        <h2
          id="about-aims"
          className="font-display text-xl font-semibold text-text-primary"
        >
          {t("aimsTitle")}
        </h2>
        <dl className="mt-5 grid gap-x-10 gap-y-6 sm:grid-cols-2">
          {aims.map((id) => (
            <div key={id} className="border-s-2 border-border ps-4">
              <dt className="font-display text-base font-semibold text-text-primary">
                {t(`aims.${id}.title`)}
              </dt>
              <dd className="mt-1 text-text-secondary">{t(`aims.${id}.body`)}</dd>
            </div>
          ))}
        </dl>
      </section>
    </div>
  );
}
