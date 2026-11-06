import { getTranslations } from "next-intl/server";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { SettingsForm } from "@/shared/components/layout/settings-form";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("settings");

/**
 * Ayarlar: açılır panel değil, gerçek bir sayfa. Tercihler yalnız bu cihazda saklanır;
 * hesap/güvenlik ayarı yoktur. Sayfa `noindex` yayınlanır (pages.ts: indexable=false).
 */
export default async function SettingsPage() {
  const t = await getTranslations("Settings");

  return (
    <PageContainer>
      <PageHeader title={t("title")} description={t("description")} />
      <SettingsForm />
    </PageContainer>
  );
}
