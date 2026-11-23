import { getTranslations } from "next-intl/server";
import { SentRequestsView } from "@/features/social";

export async function generateMetadata() {
  const t = await getTranslations("Social.meta");
  return { title: t("sent"), robots: { index: false, follow: false } };
}

export default function SentFriendRequestsPage() {
  return <SentRequestsView />;
}
