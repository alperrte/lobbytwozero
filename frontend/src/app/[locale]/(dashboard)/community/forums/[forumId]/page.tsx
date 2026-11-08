import { getTranslations } from "next-intl/server";
import { ForumTopicsScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("forum.title"), description: t("forum.description"), robots: { index: false, follow: false } };
}

export default async function ForumPage({ params }: { params: Promise<{ forumId: string }> }) {
  const { forumId } = await params;
  return <ForumTopicsScreen forumId={forumId} />;
}
