import { FriendSuggestions, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouFriends");
export default function ForYouFriendsPage() {
  return <RecommendationHubFrame section="friends"><FriendSuggestions /></RecommendationHubFrame>;
}
