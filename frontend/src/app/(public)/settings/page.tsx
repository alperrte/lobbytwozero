import { getTranslations } from "next-intl/server";
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
    <div className="w-full">
      <h1 className="font-display text-3xl font-bold tracking-tight text-text-primary sm:text-4xl">
        {t("title")}
      </h1>
      <p className="mt-3 max-w-prose text-lg text-text-secondary">{t("description")}</p>

      <div className="mt-8">
        <SettingsForm />
      </div>
    </div>
  );
}
