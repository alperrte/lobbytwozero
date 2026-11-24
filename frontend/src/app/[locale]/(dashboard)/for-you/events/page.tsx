import { EventsScreen, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouEvents");
export default function ForYouEventsPage() {
  return <RecommendationHubFrame section="events"><EventsScreen /></RecommendationHubFrame>;
}
