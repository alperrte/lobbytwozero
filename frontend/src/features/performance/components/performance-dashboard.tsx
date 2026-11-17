"use client";

import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { HardwareSnapshot } from "@/features/hardware";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { PerformanceLoading } from "./performance-ui";
import { SessionHistory } from "./session-history";
import { useState } from "react";
import { PerformancePrivacy } from "./performance-privacy";

export function PerformanceDashboard() {
  const t = useTranslations("Performance");
  const session = useRequireSession();
  const [revision, setRevision] = useState(0);
  if (session.status !== "authenticated") return <PerformanceLoading />;
  return <PageContainer>
    <PageHeader title={t("dashboard")} description={t("dashboardDescription")} />
    <div key={session.me.accountId} className="space-y-10">
      <HardwareSnapshot accountId={session.me.accountId} />
      <SessionHistory key={revision} accountId={session.me.accountId} />
      <section aria-labelledby="performance-compatibility" className="space-y-3 rounded-panel border border-border bg-surface p-5 sm:p-6">
        <h2 id="performance-compatibility" className="font-display text-2xl font-semibold">{t("compatibilityAndRecommendations")}</h2>
        <p className="max-w-3xl text-sm text-text-secondary">{t("compatibilityUnavailable")}</p>
        <p className="max-w-3xl text-sm text-text-secondary">{t("recommendationsUnavailable")}</p>
      </section>
      <PerformancePrivacy accountId={session.me.accountId} onDataChanged={() => setRevision((value) => value + 1)} />
    </div>
  </PageContainer>;
}
