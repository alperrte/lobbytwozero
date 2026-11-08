import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { MyPostsScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("mineWords.title"), description: t("mineWords.description"), robots: { index: false, follow: false } };
}

export default function MyWordsPage() {
  return (
    <Suspense>
      <MyPostsScreen view="words" />
    </Suspense>
  );
}
