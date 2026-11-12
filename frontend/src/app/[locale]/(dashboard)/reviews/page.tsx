import type { Metadata } from "next";
import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { ReviewHub, ReviewLoading } from "@/features/review";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("Review");
  return { title: t("meta.hub"), description: t("meta.hubDescription"), robots: { index: false, follow: false } };
}

export default function ReviewsPage() {
  return <Suspense fallback={<ReviewLoading />}><ReviewHub /></Suspense>;
}
