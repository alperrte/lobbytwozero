"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";

/**
 * Sosyal sayfalar yalnız oturum açmış kullanıcıya çizilir. Oturum doğrulanana kadar hiçbir sosyal istek atılmaz
 * (misafirde 401 seli olmaz); misafir `/giris?next=<bu sayfa>` adresine yönlenir ve girişten sonra geri döner.
 * Bu yalnız kullanıcı deneyimidir; yetki kararı her zaman social-service'tedir.
 */
export function SocialSessionGate({ children }: { children: ReactNode }) {
  const session = useRequireSession();
  const t = useTranslations("Social.states");
  if (session.status === "authenticated") {
    return children;
  }
  return (
    <div role="status" aria-live="polite" className="space-y-5">
      <span className="sr-only">{t("loading")}</span>
      <div aria-hidden="true" className="h-40 animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
      <div aria-hidden="true" className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] xl:grid-cols-[minmax(0,1fr)_23rem]">
        <div className="h-96 animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
        <div className="h-64 animate-pulse rounded-panel border border-border bg-surface motion-reduce:animate-none" />
      </div>
    </div>
  );
}
