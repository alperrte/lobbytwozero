import { useTranslations } from "next-intl";
import { cn } from "@/shared/utils/cn";

/**
 * "Önizleme" işareti: yanındaki içerik tasarım örneğidir, gerçek kullanıcı verisi değildir. Ekran okuyucuya da
 * açıkça söylenir (görsel işaret tek başına yeterli sayılmaz).
 */
export function PreviewMark({ className }: { className?: string }) {
  const t = useTranslations("Profile.preview");
  return (
    <span
      title={t("markHint")}
      className={cn(
        "inline-flex items-center rounded-full border border-dashed border-border px-2 py-0.5 text-xs font-medium leading-none text-text-secondary",
        className
      )}
    >
      {t("mark")}
      <span className="sr-only">. {t("markHint")}</span>
    </span>
  );
}
