import { getTranslations } from "next-intl/server";
import { UserPostsScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("user.title"), description: t("user.description"), robots: { index: false, follow: false } };
}

export default async function UserPostsPage({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;
  return <UserPostsScreen userId={userId} />;
}
