"use client";

import { useTranslations } from "next-intl";
import { useRequireSession } from "@/features/auth";
import { HardwareSnapshot } from "@/features/hardware";
import { PageContainer } from "@/shared/components/layout/page-container";
import { PageHeader } from "@/shared/components/layout/page-header";
import { PerformanceLoading } from "./performance-ui";
import { SessionHistory } from "./session-history";

export function PerformanceDashboard() {
  const t = useTranslations("Performance");
  const session = useRequireSession();
  if (session.status !== "authenticated") return <PerformanceLoading />;
  return <PageContainer>
    <PageHeader title={t("dashboard")} description={t("dashboardDescription")} />
    <div key={session.me.accountId} className="space-y-10">
      <HardwareSnapshot accountId={session.me.accountId} />
      <SessionHistory accountId={session.me.accountId} />
    </div>
  </PageContainer>;
}
