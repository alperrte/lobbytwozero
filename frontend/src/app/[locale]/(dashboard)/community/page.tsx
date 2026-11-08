import { getTranslations } from "next-intl/server";
import { FeedScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("feed.title"), description: t("feed.description"), robots: { index: false, follow: false } };
}

export default function CommunityPage() {
  return <FeedScreen />;
}
