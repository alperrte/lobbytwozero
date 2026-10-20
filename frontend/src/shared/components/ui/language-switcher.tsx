"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import { setLocale } from "@/shared/lib/i18n/actions";
import type { Locale } from "@/shared/lib/i18n/config";

export function LanguageSwitcher() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  function changeLocale(locale: Locale) {
    startTransition(async () => {
      await setLocale(locale);
      router.refresh();
    });
  }

  return (
    <nav aria-label="Language" className="flex gap-4">
      <button
        type="button"
        disabled={pending}
        onClick={() => changeLocale("tr")}
      >
        TR
      </button>
      <button
        type="button"
        disabled={pending}
        onClick={() => changeLocale("en")}
      >
        EN
      </button>
    </nav>
  );
}
