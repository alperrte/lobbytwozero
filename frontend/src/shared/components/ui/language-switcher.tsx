"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { setLocale } from "@/shared/lib/i18n/actions";
import { locales, type Locale } from "@/shared/lib/i18n/config";

const segment =
  "inline-flex h-9 min-w-9 items-center justify-center rounded-control px-2 font-display text-xs font-semibold tracking-wide text-text-secondary transition-colors hover:text-text-primary disabled:cursor-wait aria-pressed:bg-surface aria-pressed:text-text-primary aria-pressed:shadow-[inset_0_-2px_0_0_var(--ltz-primary)]";

export function LanguageSwitcher() {
  const router = useRouter();
  const activeLocale = useLocale();
  const t = useTranslations("Locale");
  const [pending, startTransition] = useTransition();

  function changeLocale(locale: Locale) {
    if (locale === activeLocale) {
      return;
    }
    startTransition(async () => {
      await setLocale(locale);
      router.refresh();
    });
  }

  return (
    <div
      role="group"
      aria-label={t("label")}
      className="inline-flex rounded-control border border-border bg-surface-elevated p-0.5"
    >
      {locales.map((locale) => (
        <button
          key={locale}
          type="button"
          lang={locale}
          disabled={pending}
          aria-pressed={locale === activeLocale}
          aria-label={`${t(locale)} (${locale.toUpperCase()})`}
          onClick={() => changeLocale(locale)}
          className={segment}
        >
          {locale.toUpperCase()}
        </button>
      ))}
    </div>
  );
}
