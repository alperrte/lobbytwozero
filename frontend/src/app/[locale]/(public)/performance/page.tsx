import { getTranslations } from "next-intl/server";
import { DeviceList } from "@/features/performance/components/device-list";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("performance");

export default async function PerformancePage() {
  const t = await getTranslations("Performance");
  return <PageContainer>
    <PageHeader title={t("title")} description={t("intro")} />
    <div className="space-y-8">
      <section aria-labelledby="performance-available" className="border-s-2 border-primary ps-5">
        <h2 id="performance-available" className="font-display text-2xl font-semibold">{t("available")}</h2>
        <p className="mt-3 max-w-3xl text-text-secondary">{t("availableDescription")}</p>
      </section>
      <section aria-labelledby="performance-download" className="rounded-panel border border-border bg-surface p-6">
        <h2 id="performance-download" className="font-display text-2xl font-semibold">{t("desktop")}</h2>
        <p id="download-description" className="mt-3 max-w-3xl text-text-secondary">{t("desktopDescription")}</p>
        <button disabled aria-describedby="download-description" className="mt-5 min-h-11 rounded-control border border-control-border px-5 py-2 opacity-60">{t("downloadUnavailable")}</button>
      </section>
      <section aria-labelledby="performance-devices">
        <h2 id="performance-devices" className="mb-4 font-display text-2xl font-semibold">{t("devices")}</h2>
        <DeviceList />
      </section>
    </div>
  </PageContainer>;
}
