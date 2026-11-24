import { RunHistoryScreen, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouHistory");
export default function ForYouHistoryPage() {
  return <RecommendationHubFrame section="history"><RunHistoryScreen /></RecommendationHubFrame>;
}
