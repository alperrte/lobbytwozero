"use client";

import { useTranslations } from "next-intl";

export default function Error({
  reset
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const t = useTranslations("Errors");

  return (
    <main
      role="alert"
      className="mx-auto flex min-h-dvh max-w-xl flex-col items-start justify-center gap-4 px-6"
    >
      <h1 className="font-display text-3xl font-bold text-text-primary">
        {t("title")}
      </h1>
      <p className="text-text-secondary">{t("description")}</p>
      <button
        type="button"
        onClick={reset}
        className="inline-flex h-11 items-center rounded-control bg-primary px-5 font-display text-sm font-semibold text-primary-foreground hover:bg-secondary"
      >
        {t("retry")}
      </button>
    </main>
  );
}
