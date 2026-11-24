import { OverviewScreen, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYou");
export default function ForYouPage() {
  return <RecommendationHubFrame section="overview"><OverviewScreen /></RecommendationHubFrame>;
}
