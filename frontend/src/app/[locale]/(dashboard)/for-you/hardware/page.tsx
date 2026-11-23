import { HardwareScreen, RecommendationHubFrame } from "@/features/recommendation";
import { buildPageMetadata } from "@/shared/lib/seo/page-metadata";

export const generateMetadata = () => buildPageMetadata("forYouHardware");
export default function ForYouHardwarePage() {
  return <RecommendationHubFrame section="hardware"><HardwareScreen /></RecommendationHubFrame>;
}
