import { getTranslations } from "next-intl/server";
import { ForumsScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("forums.title"), description: t("forums.description"), robots: { index: false, follow: false } };
}

export default function ForumsPage() {
  return <ForumsScreen />;
}
