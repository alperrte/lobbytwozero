import { useTranslations } from "next-intl";
import { panel } from "./hardware-ui";

export function PsuResource() {
  const t = useTranslations("Hardware.psuResource");
  return <aside className={`${panel} space-y-2 p-4 text-sm`}>
    <h3 className="font-display font-semibold">{t("title")}</h3>
    <p className="leading-6 text-text-secondary">{t("description")}</p>
    <p className="text-xs text-text-secondary">{t("source")}</p>
    <a className="inline-flex min-h-11 items-center text-brand-text underline underline-offset-4" href="https://zttbuilds.com/pages/psu-tier-list" target="_blank" rel="noopener noreferrer">{t("open")}</a>
  </aside>;
}
