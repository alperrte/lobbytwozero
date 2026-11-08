import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { MyPostsScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("mineReposts.title"), description: t("mineReposts.description"), robots: { index: false, follow: false } };
}

export default function MyRepostsPage() {
  return (
    <Suspense>
      <MyPostsScreen view="reposts" />
    </Suspense>
  );
}
