import type { Metadata } from "next";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { MyReviewsPage, ReviewLoading } from "@/features/review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Review");
  // Kişisel inceleme listesi: metin ve oyun adları metadata'ya taşınmaz.
  return { title: t("meta.mine"), robots: { index: false, follow: false } };
}

export default function MyReviewsRoute() {
  return <Suspense fallback={<ReviewLoading />}><MyReviewsPage /></Suspense>;
}
