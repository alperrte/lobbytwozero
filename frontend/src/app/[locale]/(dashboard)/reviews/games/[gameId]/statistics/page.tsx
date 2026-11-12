import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { isReviewUuid, ReviewLoading, ReviewStatisticsPage } from "@/features/review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Review");
  return { title: t("meta.statistics"), robots: { index: false, follow: false } };
}

export default async function ReviewStatisticsRoute({ params }: { params: Promise<{ gameId: string }> }) {
  const { gameId } = await params;
  if (!isReviewUuid(gameId)) notFound();
  return <Suspense fallback={<ReviewLoading />}><ReviewStatisticsPage gameId={gameId} /></Suspense>;
}
