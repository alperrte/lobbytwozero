import { Suspense } from "react";
import { getTranslations } from "next-intl/server";
import { MyPostsScreen } from "@/features/community";

export async function generateMetadata() {
  const t = await getTranslations("Community.meta");
  return { title: t("mine.title"), description: t("mine.description"), robots: { index: false, follow: false } };
}

export default function MyPostsPage() {
  return (
    <Suspense>
      <MyPostsScreen view="posts" />
    </Suspense>
  );
}
