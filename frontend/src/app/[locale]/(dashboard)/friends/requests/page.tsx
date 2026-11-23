import { getTranslations } from "next-intl/server";
import { RequestsView } from "@/features/social";

export async function generateMetadata() {
  const t = await getTranslations("Social.meta");
  return { title: t("requests"), robots: { index: false, follow: false } };
}

export default function FriendRequestsPage() {
  return <RequestsView />;
}
