import { SimilarPlayersScreen, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouPlayers");
export default function ForYouPlayersPage() {
  return <RecommendationHubFrame section="players"><SimilarPlayersScreen /></RecommendationHubFrame>;
}
