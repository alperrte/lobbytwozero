import { getTranslations } from "next-intl/server";
import { BlockedView } from "@/features/social";

export async function generateMetadata() {
  const t = await getTranslations("Social.meta");
  return { title: t("blocked"), robots: { index: false, follow: false } };
}

export default function BlockedUsersPage() {
  return <BlockedView />;
}
