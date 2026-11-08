import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { ComparisonScreen } from "@/features/hardware/components/comparison-screen";
import { HardwareLoading } from "@/features/hardware/components/hardware-ui";
import { PageContainer } from "@/shared/components/layout/page-container";

export async function generateMetadata() {
  const t = await getTranslations("Hardware.comparison");
  return { title: t("title"), robots: { index: false, follow: false } };
}
export default function ComparePage() {
  return <PageContainer><Suspense fallback={<HardwareLoading />}><ComparisonScreen /></Suspense></PageContainer>;
}
