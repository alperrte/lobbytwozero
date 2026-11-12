import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { isReviewUuid, ReviewLoading, ReviewWritePage } from "@/features/review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Review");
  return { title: t("meta.write"), robots: { index: false, follow: false } };
}

export default async function ReviewWriteRoute({ params }: { params: Promise<{ gameId: string }> }) {
  const { gameId } = await params;
  if (!isReviewUuid(gameId)) notFound();
  return <Suspense fallback={<ReviewLoading />}><ReviewWritePage gameId={gameId} /></Suspense>;
}
