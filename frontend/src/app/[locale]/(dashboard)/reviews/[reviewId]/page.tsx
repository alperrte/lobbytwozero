import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { isReviewUuid, ReviewDetailPage, ReviewLoading } from "@/features/review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Review");
  // İnceleme metni ve sahiplik bilgisi metadata'ya veya paylaşım önizlemesine taşınmaz.
  return { title: t("meta.detail"), robots: { index: false, follow: false } };
}

export default async function ReviewDetailRoute({ params }: { params: Promise<{ reviewId: string }> }) {
  const { reviewId } = await params;
  if (!isReviewUuid(reviewId)) notFound();
  return <Suspense fallback={<ReviewLoading />}><ReviewDetailPage reviewId={reviewId} /></Suspense>;
}
