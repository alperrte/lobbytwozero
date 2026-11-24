import { notFound } from "next/navigation";
import { RecommendationHubFrame, RunDetailScreen, isUuid } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouHistory");
export default async function ForYouRunDetailPage({ params }: { params: Promise<{ runId: string }> }) {
  const { runId } = await params;
  if (!isUuid(runId)) notFound();
  return <RecommendationHubFrame section="history"><RunDetailScreen runId={runId.toLowerCase()} /></RecommendationHubFrame>;
}
