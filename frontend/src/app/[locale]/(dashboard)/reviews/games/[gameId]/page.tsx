import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { isReviewUuid, ReviewGamePage, ReviewLoading } from "@/features/review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Review");
  // Oyun adı, puan veya inceleme içeriği metadata'ya taşınmaz.
  return { title: t("meta.game"), robots: { index: false, follow: false } };
}

export default async function ReviewGameRoute({ params }: { params: Promise<{ gameId: string }> }) {
  const { gameId } = await params;
  if (!isReviewUuid(gameId)) notFound();
  return <Suspense fallback={<ReviewLoading />}><ReviewGamePage gameId={gameId} /></Suspense>;
}
