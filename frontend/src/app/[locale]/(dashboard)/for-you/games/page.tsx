import { GamesScreen, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouGames");
export default function ForYouGamesPage() {
  return <RecommendationHubFrame section="games"><GamesScreen /></RecommendationHubFrame>;
}
