"use client";

import type { ReactNode } from "react";
import { useRequireSession } from "@/features/auth";
import { PageContainer } from "@/shared/components/layout/page-container";
import { ReviewLoading } from "./review-ui";

/**
 * Review sayfalarının ortak kapısı. Review kullanıcı API'leri oturum ister; oturumsuz ziyaretçi mevcut giriş akışına
 * (`/login?next=<bu sayfa>`) yönlenir. Bu yalnız KULLANICI DENEYİMİDİR — yetki kararı Review/Gateway'dedir. İçerik hesap
 * kimliğiyle anahtarlanır: hesap değişince önceki kullanıcının verisi (puan, tavsiye, sahiplik) bellekte kalmaz.
 */
export function ReviewBoundary({ children }: { children: ReactNode }) {
  const session = useRequireSession();
  if (session.status !== "authenticated") {
    return (
      <PageContainer className="py-3 sm:py-5">
        <ReviewLoading />
      </PageContainer>
    );
  }
  return (
    <PageContainer key={session.me.accountId} className="space-y-6 py-3 sm:space-y-8 sm:py-5">
      {children}
    </PageContainer>
  );
}
