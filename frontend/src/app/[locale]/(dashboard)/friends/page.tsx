import { getTranslations } from "next-intl/server";
import { FriendsView } from "@/features/social";

export async function generateMetadata() {
  const t = await getTranslations("Social.meta");
  return { title: t("friends"), robots: { index: false, follow: false } };
}

export default function FriendsPage() {
  return <FriendsView />;
}
