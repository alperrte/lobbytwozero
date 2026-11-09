"use client";

import { useTranslations } from "next-intl";
import { Icon } from "@/shared/components/ui/icons";
import { Link } from "@/shared/lib/i18n/navigation";
import { buttonClass } from "@/shared/components/ui/button-styles";

export function LibraryEmptyState({ filtered = false, readOnly = false, onReset }: { filtered?: boolean; readOnly?: boolean; onReset?: () => void }) {
  const t = useTranslations("Library");
  return <div className="space-y-3 rounded-panel border border-dashed border-control-border px-5 py-12 text-center">
    <Icon name="library" className="mx-auto size-8 text-text-secondary" />
    <h2 className="font-display text-xl font-semibold">{t(filtered ? "emptyFiltered" : "empty")}</h2><p className="mx-auto max-w-prose text-sm leading-6 text-text-secondary">{t(readOnly ? "emptyProfile" : filtered ? "emptyFilteredHint" : "emptyHint")}</p>
    {filtered && onReset && <button type="button" className={buttonClass("secondary")} onClick={onReset}>{t("clearFilters")}</button>}
    {!readOnly && <Link href="/library/add" className={buttonClass("primary")}>{t("addGame")}</Link>}
  </div>;
}
