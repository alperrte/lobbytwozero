import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { getSiteUrl } from "@/shared/config/site";

export async function generateMetadata(): Promise<Metadata> {
  // Canonical yalnız doğrulanmış bir site kökü varsa yazılır; domain uydurulmaz.
  return getSiteUrl() ? { alternates: { canonical: "/" } } : {};
}

export default async function HomePage() {
  const t = await getTranslations("Home");

  return (
    <div className="mx-auto w-full max-w-5xl">
      <h1 className="font-display text-4xl font-bold tracking-tight text-text-primary sm:text-5xl">
        {t("title")}
      </h1>
      <p className="mt-4 max-w-prose text-lg text-text-secondary">
        {t("description")}
      </p>

      <section
        aria-labelledby="status-title"
        className="mt-10 max-w-prose rounded-panel border border-border bg-surface p-5 sm:p-6"
      >
        <h2
          id="status-title"
          className="font-display text-lg font-semibold text-text-primary"
        >
          {t("statusTitle")}
        </h2>
        <p className="mt-2 text-text-secondary">{t("statusBody")}</p>
      </section>
    </div>
  );
}
